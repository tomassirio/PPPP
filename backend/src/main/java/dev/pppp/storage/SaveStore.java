package dev.pppp.storage;

import dev.pppp.PpppProperties;
import dev.pppp.engine.EmulatorHost;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Save states at {dataDir}/states/{cartridgeId}/{id}.state, id = {manual|auto}-{timestamp},
 * indexed in the sorted set savestates:{cartridgeId} (member = id, score = creation millis).
 * The kind is the id's prefix.
 */
@Component
public class SaveStore {
    private static final Pattern STATE_ID = Pattern.compile("(manual|auto)-\\d{8}-\\d{6}-\\d{6}");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSSSSS").withZone(ZoneOffset.UTC);

    public enum Kind { manual, auto }

    public record SaveState(String id, String cartridgeId, Kind kind, Instant createdAt) {}

    private final Path states;
    private final StringRedisTemplate redis;
    private final EmulatorHost host;

    public SaveStore(PpppProperties props, StringRedisTemplate redis, EmulatorHost host) {
        this.states = props.dataDir().resolve("states");
        this.redis = redis;
        this.host = host;
    }

    /** Snapshots the running console. Throws IllegalStateException (409) when it is off or empty. */
    public synchronized SaveState take(Kind kind) {
        String cartridgeId = runningCartridge();
        byte[] data = host.saveState();
        Instant now = Instant.now();
        while (redis.opsForZSet().score(key(cartridgeId), kind + "-" + STAMP.format(now)) != null) now = now.plusNanos(1000);
        String id = kind + "-" + STAMP.format(now);
        try {
            Path file = file(cartridgeId, id);
            Files.createDirectories(file.getParent());
            Files.write(file, data);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        redis.opsForZSet().add(key(cartridgeId), id, now.toEpochMilli());
        return new SaveState(id, cartridgeId, kind, now);
    }

    /** Newest first. */
    public List<SaveState> list(String cartridgeId) {
        var entries = redis.opsForZSet().reverseRangeWithScores(key(cartridgeId), 0, -1);
        if (entries == null) return List.of();
        return entries.stream()
                .map(e -> new SaveState(e.getValue(), cartridgeId, kindOf(e.getValue()),
                        Instant.ofEpochMilli(e.getScore().longValue())))
                .toList();
    }

    /** Loads a state of the inserted cartridge. 404 if unknown, 409 if the console is off. */
    public SaveState load(String id) {
        String cartridgeId = runningCartridge();
        if (!STATE_ID.matcher(id).matches() || redis.opsForZSet().score(key(cartridgeId), id) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No save state " + id + " for " + cartridgeId);
        }
        try {
            host.loadState(Files.readAllBytes(file(cartridgeId, id)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return list(cartridgeId).stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
    }

    /** Deletes the oldest automatic states beyond keep. Manual states are never pruned. */
    public synchronized void prune(String cartridgeId, int keep) {
        List<SaveState> autos = list(cartridgeId).stream().filter(s -> s.kind() == Kind.auto).toList();
        for (SaveState old : autos.subList(Math.min(keep, autos.size()), autos.size())) {
            redis.opsForZSet().remove(key(cartridgeId), old.id());
            try {
                Files.deleteIfExists(file(cartridgeId, old.id()));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private String runningCartridge() {
        EmulatorHost.Status s = host.status();
        if (s.power() != EmulatorHost.Power.ON || s.cartridgeId() == null) throw new IllegalStateException("Console is off");
        return s.cartridgeId();
    }

    private Path file(String cartridgeId, String id) {
        return states.resolve(cartridgeId).resolve(id + ".state");
    }

    private static Kind kindOf(String id) {
        return Kind.valueOf(id.substring(0, id.indexOf('-')));
    }

    private static String key(String cartridgeId) {
        return "savestates:" + cartridgeId;
    }
}
