package dev.pppp.service;

import dev.pppp.emulator.EmulatorHost;
import dev.pppp.exception.NotFoundException;
import dev.pppp.model.LiveEvent;
import dev.pppp.model.SaveState;
import dev.pppp.repository.SaveStateRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/** Save states of the inserted cartridge: id = {manual|auto}-{timestamp}; automatic ones are pruned to Settings.Autosave.keep. */
@Service
public class SaveStateService {
    private static final Pattern STATE_ID = Pattern.compile("(manual|auto)-\\d{8}-\\d{6}-\\d{6}");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSSSSS").withZone(ZoneOffset.UTC);

    private final SaveStateRepository states;
    private final EmulatorHost host;
    private final SettingsService settings;
    private final ChatService chat;
    private final ApplicationEventPublisher events;

    public SaveStateService(SaveStateRepository states, EmulatorHost host, SettingsService settings, ChatService chat,
                            ApplicationEventPublisher events) {
        this.states = states;
        this.host = host;
        this.settings = settings;
        this.chat = chat;
        this.events = events;
    }

    /** States of the inserted cartridge, newest first; empty without one. */
    public List<SaveState> list() {
        String cartridgeId = host.status().cartridgeId();
        return cartridgeId == null ? List.of() : states.findAll(cartridgeId);
    }

    /** Manual snapshot, announced in chat. IllegalStateException when the console is off or empty. */
    public SaveState takeManual() {
        SaveState state = take(SaveState.Kind.manual);
        chat.postAdmin("Save state taken (" + state.id() + ")");
        return state;
    }

    /** Automatic snapshot plus pruning, silent. Returns null (skips) when the console is off or empty. */
    public SaveState takeAuto() {
        EmulatorHost.Status s = host.status();
        if (s.power() != EmulatorHost.Power.ON || s.cartridgeId() == null) return null;
        host.flushBattery();
        SaveState state = take(SaveState.Kind.auto);
        prune(s.cartridgeId(), settings.autosave().keep());
        return state;
    }

    /** Loads a state of the inserted cartridge. NotFoundException if unknown, IllegalStateException if the console is off. */
    public SaveState load(String id) {
        String cartridgeId = runningCartridge();
        if (!STATE_ID.matcher(id).matches() || !states.exists(cartridgeId, id)) {
            throw new NotFoundException("No save state " + id + " for " + cartridgeId);
        }
        host.loadState(states.read(cartridgeId, id));
        SaveState state = states.findAll(cartridgeId).stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
        chat.postAdmin("Save state " + id + " loaded");
        events.publishEvent(new LiveEvent("CONSOLE_CHANGED", host.status()));
        return state;
    }

    private synchronized SaveState take(SaveState.Kind kind) {
        String cartridgeId = runningCartridge();
        byte[] data = host.saveState();
        Instant now = Instant.now();
        while (states.exists(cartridgeId, kind + "-" + STAMP.format(now))) now = now.plusNanos(1000);
        SaveState state = new SaveState(kind + "-" + STAMP.format(now), cartridgeId, kind, now);
        states.save(state, data);
        return state;
    }

    /** Deletes the oldest automatic states beyond keep. Manual states are never pruned. */
    private synchronized void prune(String cartridgeId, int keep) {
        List<SaveState> autos = states.findAll(cartridgeId).stream().filter(s -> s.kind() == SaveState.Kind.auto).toList();
        for (SaveState old : autos.subList(Math.min(keep, autos.size()), autos.size())) states.delete(old);
    }

    private String runningCartridge() {
        EmulatorHost.Status s = host.status();
        if (s.power() != EmulatorHost.Power.ON || s.cartridgeId() == null) throw new IllegalStateException("Console is off");
        return s.cartridgeId();
    }
}
