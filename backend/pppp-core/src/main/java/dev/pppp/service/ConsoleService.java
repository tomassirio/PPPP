package dev.pppp.service;

import dev.pppp.emulator.EmulatorHost;
import dev.pppp.model.LiveEvent;
import dev.pppp.repository.ConsoleStateRepository;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Admin console actions. Each one announces itself in chat, publishes CONSOLE_CHANGED and is remembered
 * in Redis, so a run survives a restart: on shutdown the running game gets an automatic save state; on boot
 * the cartridge and power state are restored and the newest save state is loaded, so play resumes where it
 * stopped.
 */
@Service
public class ConsoleService {
    private static final Logger log = LoggerFactory.getLogger(ConsoleService.class);

    private final EmulatorHost host;
    private final ConsoleStateRepository state;
    private final CartridgeService cartridges;
    private final SaveStateService saves;
    private final ChatService chat;
    private final ApplicationEventPublisher events;

    public ConsoleService(EmulatorHost host, ConsoleStateRepository state, CartridgeService cartridges,
                          SaveStateService saves, ChatService chat, ApplicationEventPublisher events) {
        this.host = host;
        this.state = state;
        this.cartridges = cartridges;
        this.saves = saves;
        this.chat = chat;
        this.events = events;
    }

    public EmulatorHost.Status insert(String cartridgeId) {
        var rom = cartridges.require(cartridgeId);
        if (host.status().power() == EmulatorHost.Power.ON) throw new IllegalStateException("Power the console off first");
        host.insert(cartridgeId, rom, cartridges.batterySave(cartridgeId));
        return changed("Cartridge " + cartridgeId + " inserted");
    }

    public EmulatorHost.Status power(EmulatorHost.Power power) {
        if (power == EmulatorHost.Power.ON) host.powerOn();
        else host.powerOff();
        return changed("Console powered " + power.name().toLowerCase(Locale.ROOT));
    }

    public EmulatorHost.Status reset() { host.reset(); return changed("Console reset"); }

    public EmulatorHost.Status pause() { host.pause(); return changed("Game paused"); }

    public EmulatorHost.Status resume() { host.resume(); return changed("Game resumed"); }

    public EmulatorHost.Status speed(double multiplier) {
        host.setSpeed(multiplier);
        return changed("Speed set to " + multiplier + "x");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void restore() {
        String cartridgeId = state.cartridgeId();
        if (cartridgeId == null) return;
        try {
            host.insert(cartridgeId, cartridges.require(cartridgeId), cartridges.batterySave(cartridgeId));
            if (state.poweredOn()) {
                host.powerOn();
                resume(cartridgeId);
            }
            log.info("Restored console: {}", host.status());
        } catch (RuntimeException e) {
            log.warn("Could not restore console from Redis", e);
        }
    }

    /**
     * On ContextClosedEvent, the first step of shutdown: Redis (a lifecycle bean) is stopped before any
     * @PreDestroy runs, and taking a save state needs it, so a @PreDestroy save always failed.
     */
    /**
     * Loads the cartridge's newest save state, normally the one taken at shutdown. (Not compared with the battery
     * save: the emulator rewrites that file while stopping, after the shutdown state.)
     */
    private void resume(String cartridgeId) {
        var newest = saves.list().stream().findFirst().orElse(null);
        if (newest == null) return;
        try {
            saves.load(newest.id());
        } catch (RuntimeException e) {
            log.warn("Could not resume {} from save state {}", cartridgeId, newest.id(), e); // e.g. other hardware
        }
    }

    @EventListener(ContextClosedEvent.class)
    public void saveOnShutdown() {
        try {
            saves.takeAuto();
        } catch (RuntimeException e) {
            log.warn("Shutdown save state failed", e);
        }
    }

    private EmulatorHost.Status changed(String message) {
        EmulatorHost.Status s = host.status();
        if (s.cartridgeId() == null) state.clear();
        else state.save(s.cartridgeId(), s.power().name());
        chat.postAdmin(message);
        events.publishEvent(new LiveEvent("CONSOLE_CHANGED", s));
        return s;
    }
}
