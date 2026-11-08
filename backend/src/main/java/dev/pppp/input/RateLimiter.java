package dev.pppp.input;

import dev.pppp.settings.SettingsService;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Fixed 1 s window per user: ratelimit:{userId} INCR + EXPIRE 1 s. */
@Component
public class RateLimiter {
    private final StringRedisTemplate redis;
    private final SettingsService settings;

    public RateLimiter(StringRedisTemplate redis, SettingsService settings) {
        this.redis = redis;
        this.settings = settings;
    }

    public void check(String userId) {
        String key = "ratelimit:" + userId;
        long n = redis.opsForValue().increment(key);
        if (n == 1) redis.expire(key, Duration.ofSeconds(1));
        if (n > settings.rateLimit().inputsPerSecond()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded");
        }
    }
}
