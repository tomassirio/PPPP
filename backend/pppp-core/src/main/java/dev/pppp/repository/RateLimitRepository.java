package dev.pppp.repository;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/** ratelimit:{userId}: input count in the current fixed 1 s window (INCR + EXPIRE 1 s). */
@Repository
public class RateLimitRepository {
    private final StringRedisTemplate redis;

    public RateLimitRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Counts one more input for the user and returns the count in this second. */
    public long increment(String userId) {
        String key = "ratelimit:" + userId;
        long n = redis.opsForValue().increment(key);
        if (n == 1) redis.expire(key, Duration.ofSeconds(1));
        return n;
    }

    /** Inputs counted in the current window, without counting a new one. */
    public long count(String userId) {
        String n = redis.opsForValue().get("ratelimit:" + userId);
        return n == null ? 0 : Long.parseLong(n);
    }
}
