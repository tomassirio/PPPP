package dev.pppp.storage;

import dev.pppp.engine.EmulatorHost;
import dev.pppp.settings.SettingsService;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Makes a run survive a restart: the inserted cartridge and power state live in the Redis hash
 * "console" {cartridgeId, power}. On boot they are restored; on shutdown the running game gets an
 * automatic save state. Depends on EmulatorHost, so @PreDestroy runs before the emulator stops.
 */
@Component
public class ConsoleStore {
    private static final Logger log = LoggerFactory.getLogger(ConsoleStore.class);
    private static final String KEY = "console";

    private final StringRedisTemplate redis;
    private final EmulatorHost host;
    private final CartridgeStore cartridges;
    private final SaveStore saves;
    private final SettingsService settings;

    public ConsoleStore(StringRedisTemplate redis, EmulatorHost host, CartridgeStore cartridges, SaveStore saves,
                        SettingsService settings) {
        this.redis = redis;
        this.host = host;
        this.cartridges = cartridges;
        this.saves = saves;
        this.settings = settings;
    }

    /** Call after every successful console action. */
    public void remember(EmulatorHost.Status s) {
        if (s.cartridgeId() == null) redis.delete(KEY);
        else redis.opsForHash().putAll(KEY, Map.of("cartridgeId", s.cartridgeId(), "power", s.power().name()));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void restore() {
        Object id = redis.opsForHash().get(KEY, "cartridgeId");
        if (id == null) return;
        try {
            String cartridgeId = id.toString();
            host.insert(cartridgeId, cartridges.require(cartridgeId), cartridges.batterySave(cartridgeId));
            if ("ON".equals(redis.opsForHash().get(KEY, "power"))) host.powerOn();
            log.info("Restored console: {}", host.status());
        } catch (RuntimeException e) {
            log.warn("Could not restore console from Redis", e);
        }
    }

    @PreDestroy
    public void saveOnShutdown() {
        try {
            EmulatorHost.Status s = host.status();
            if (s.power() != EmulatorHost.Power.ON || s.cartridgeId() == null) return;
            host.flushBattery();
            saves.take(SaveStore.Kind.auto);
            saves.prune(s.cartridgeId(), settings.autosave().keep());
        } catch (RuntimeException e) {
            log.warn("Shutdown save state failed", e);
        }
    }
}
