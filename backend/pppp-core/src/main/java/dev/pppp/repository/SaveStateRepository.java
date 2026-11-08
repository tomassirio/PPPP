package dev.pppp.repository;

import dev.pppp.config.PpppProperties;
import dev.pppp.model.SaveState;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * Save states at {dataDir}/states/{cartridgeId}/{id}.state, indexed in the sorted set
 * savestates:{cartridgeId} (member = id, score = creation millis).
 */
@Repository
public class SaveStateRepository {
    private final Path states;
    private final StringRedisTemplate redis;

    public SaveStateRepository(PpppProperties props, StringRedisTemplate redis) {
        this.states = props.dataDir().resolve("states");
        this.redis = redis;
    }

    public boolean exists(String cartridgeId, String id) {
        return redis.opsForZSet().score(key(cartridgeId), id) != null;
    }

    public void save(SaveState state, byte[] data) {
        try {
            Path file = file(state.cartridgeId(), state.id());
            Files.createDirectories(file.getParent());
            Files.write(file, data);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        redis.opsForZSet().add(key(state.cartridgeId()), state.id(), state.createdAt().toEpochMilli());
    }

    /** Newest first. */
    public List<SaveState> findAll(String cartridgeId) {
        var entries = redis.opsForZSet().reverseRangeWithScores(key(cartridgeId), 0, -1);
        if (entries == null) return List.of();
        return entries.stream()
                .map(e -> new SaveState(e.getValue(), cartridgeId, kindOf(e.getValue()),
                        Instant.ofEpochMilli(e.getScore().longValue())))
                .toList();
    }

    public byte[] read(String cartridgeId, String id) {
        try {
            return Files.readAllBytes(file(cartridgeId, id));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void delete(SaveState state) {
        redis.opsForZSet().remove(key(state.cartridgeId()), state.id());
        try {
            Files.deleteIfExists(file(state.cartridgeId(), state.id()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path file(String cartridgeId, String id) {
        return states.resolve(cartridgeId).resolve(id + ".state");
    }

    private static SaveState.Kind kindOf(String id) {
        return SaveState.Kind.valueOf(id.substring(0, id.indexOf('-')));
    }

    private static String key(String cartridgeId) {
        return "savestates:" + cartridgeId;
    }
}
