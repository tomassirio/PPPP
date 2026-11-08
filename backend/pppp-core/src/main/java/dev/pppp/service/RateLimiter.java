package dev.pppp.service;

import dev.pppp.exception.RateLimitedException;
import dev.pppp.repository.RateLimitRepository;
import org.springframework.stereotype.Component;

/** Fixed 1 s window per user, limit from settings. */
@Component
public class RateLimiter {
    private final RateLimitRepository counts;
    private final SettingsService settings;

    public RateLimiter(RateLimitRepository counts, SettingsService settings) {
        this.counts = counts;
        this.settings = settings;
    }

    /** Throws RateLimitedException when the user is over inputsPerSecond. */
    public void check(String userId) {
        if (counts.increment(userId) > settings.rateLimit().inputsPerSecond()) {
            throw new RateLimitedException("Rate limit exceeded");
        }
    }

    /** Inputs the user may still send in the current 1 s window. */
    public int remaining(String userId) {
        return (int) Math.max(0, settings.rateLimit().inputsPerSecond() - counts.count(userId));
    }
}
