package dev.pppp.service.game;

import dev.pppp.emulator.EmulatorHost;
import dev.pppp.model.GameEvent;
import dev.pppp.model.GameSummary;
import dev.pppp.model.LiveEvent;
import dev.pppp.repository.GameEventRepository;
import dev.pppp.service.CartridgeService;
import dev.pppp.service.ChatService;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Game events from RAM, for any game with a {@link GameProfile}. Every ~10 frames it reads the profile's memory
 * window and parses it. A state counts once it has held for {@link #STABLE_READS} reads (~0.5 s; RAM is noisy
 * during screen transitions). Each confirmed state is published as GAME_STATE; the profile's events between two
 * confirmed states go to game:events, GAME_EVENT and chat.
 *
 * <p>The first confirmed state after the game comes into play (or after any CONSOLE_CHANGED, e.g. a loaded save
 * state) is a baseline and fires nothing, so loading progress never replays it as news.
 */
@Service
public class RamWatcher {
    static final int STABLE_READS = 3;
    private static final Logger log = LoggerFactory.getLogger(RamWatcher.class);

    private final EmulatorHost host;
    private final CartridgeService cartridges;
    private final GameEventRepository repo;
    private final ChatService chat;
    private final ApplicationEventPublisher events;

    private volatile String lastPlayer;
    private volatile boolean rebaseline;
    private volatile GameSummary summary;
    private String cartridgeId; // poll thread only
    private volatile Tracker<?> tracker;

    public RamWatcher(EmulatorHost host, CartridgeService cartridges, GameEventRepository repo, ChatService chat,
                      ApplicationEventPublisher events) {
        this.host = host;
        this.cartridges = cartridges;
        this.repo = repo;
        this.chat = chat;
        this.events = events;
    }

    /** The latest confirmed state of the inserted game (empty before it comes into play), or null when it has no profile. */
    public GameSummary summary() {
        return summary;
    }

    /** PNG for an icon id of the current summary, or null. */
    public byte[] icon(int id) {
        Tracker<?> t = tracker;
        return t == null ? null : t.profile.icon(id);
    }

    public List<GameEvent> history(int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be 1 to 100");
        return repo.newest(limit);
    }

    @EventListener
    public void onLiveEvent(LiveEvent e) {
        if ("CONSOLE_CHANGED".equals(e.type())) rebaseline = true;
        if ("INPUT_EXECUTED".equals(e.type()) && e.payload() instanceof Map<?, ?> m && m.get("user") instanceof String user) {
            lastPlayer = user;
        }
    }

    @Scheduled(fixedDelay = 170)
    public void poll() {
        try {
            EmulatorHost.Status s = host.status();
            if (s.cartridgeId() == null || !s.cartridgeId().equals(cartridgeId)) {
                cartridgeId = s.cartridgeId();
                tracker = cartridgeId == null ? null : load(cartridgeId);
                // An empty box until the game comes into play, so players see it from the title screen on.
                summary = tracker == null ? null : tracker.profile.summary(null);
                if (summary != null) events.publishEvent(new LiveEvent("GAME_STATE", summary));
            }
            if (tracker == null) return;
            if (rebaseline || s.power() != EmulatorHost.Power.ON) {
                rebaseline = false;
                tracker.reset();
                if (s.power() != EmulatorHost.Power.ON) return;
            }
            tracker.offer();
        } catch (IllegalStateException e) {
            // console powered off between status and read
        } catch (RuntimeException e) {
            log.warn("RAM watcher poll failed", e);
        }
    }

    private Tracker<?> load(String id) {
        try {
            GameProfile<?> profile = GameProfile.forRom(Files.readAllBytes(cartridges.require(id)));
            log.info("Cartridge {}: {}", id, profile == null ? "no game profile, no RAM events" : profile.getClass().getSimpleName());
            return profile == null ? null : track(profile);
        } catch (IOException | RuntimeException e) {
            log.warn("Could not load a game profile for {}", id, e);
            return null;
        }
    }

    private <S> Tracker<S> track(GameProfile<S> profile) {
        return new Tracker<>(profile);
    }

    private void announce(GameEvent e) {
        GameEvent event = new GameEvent(null, e.type(), e.text(), e.details(), lastPlayer, System.currentTimeMillis());
        try {
            event = repo.append(event);
        } catch (RuntimeException ex) {
            log.warn("Could not store game event {}", e.type(), ex);
        }
        events.publishEvent(new LiveEvent("GAME_EVENT", event));
        chat.postSystem(e.text());
    }

    /** Debounce and baseline for one profile; poll thread only. */
    private final class Tracker<S> {
        final GameProfile<S> profile;
        S confirmed, pending;
        int stable;

        Tracker(GameProfile<S> profile) {
            this.profile = profile;
        }

        void reset() {
            confirmed = pending = null;
            stable = 0;
        }

        void offer() {
            S current = profile.read(new Ram(profile.ramStart(), host.readBytes(profile.ramStart(), profile.ramLength())));
            if (!Objects.equals(current, pending)) {
                pending = current;
                stable = 1;
                return;
            }
            if (stable < STABLE_READS) stable++;
            if (stable < STABLE_READS || Objects.equals(current, confirmed)) return;
            S before = confirmed;
            confirmed = current;
            if (current == null) return; // out of play: the next state in play is a fresh baseline
            summary = profile.summary(current);
            events.publishEvent(new LiveEvent("GAME_STATE", summary));
            if (before != null) profile.diff(before, current).forEach(RamWatcher.this::announce);
        }
    }
}
