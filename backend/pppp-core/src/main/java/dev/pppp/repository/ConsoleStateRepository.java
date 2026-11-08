package dev.pppp.repository;

import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/** The inserted cartridge and power state in the Redis hash "console" {cartridgeId, power}. */
@Repository
public class ConsoleStateRepository {
    private static final String KEY = "console";

    private final StringRedisTemplate redis;

    public ConsoleStateRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void save(String cartridgeId, String power) {
        redis.opsForHash().putAll(KEY, Map.of("cartridgeId", cartridgeId, "power", power));
    }

    public void clear() {
        redis.delete(KEY);
    }

    /** The remembered cartridge id, or null. */
    public String cartridgeId() {
        Object id = redis.opsForHash().get(KEY, "cartridgeId");
        return id == null ? null : id.toString();
    }

    public boolean poweredOn() {
        return "ON".equals(redis.opsForHash().get(KEY, "power"));
    }
}
