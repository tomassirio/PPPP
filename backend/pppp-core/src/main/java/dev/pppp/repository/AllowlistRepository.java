package dev.pppp.repository;

import dev.pppp.model.Role;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/** Redis hash allowlist (user id -> role), set bans, and the read side of ratelimit:{userId}. Ids arrive normalized. */
@Repository
public class AllowlistRepository {
    private static final String ALLOWLIST = "allowlist";
    private static final String BANS = "bans";

    private final StringRedisTemplate redis;

    public AllowlistRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean isEmpty() {
        return redis.opsForHash().size(ALLOWLIST) == 0;
    }

    /** The role, or null if not allowlisted. */
    public Role role(String id) {
        Object raw = redis.opsForHash().get(ALLOWLIST, id);
        return raw == null ? null : Role.valueOf((String) raw);
    }

    public Map<String, Role> roles() {
        return redis.<String, String>opsForHash().entries(ALLOWLIST).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> Role.valueOf(e.getValue())));
    }

    public void putAll(Map<String, Role> entries) {
        redis.opsForHash().putAll(ALLOWLIST, entries.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().name())));
    }

    public void put(String id, Role role) {
        redis.opsForHash().put(ALLOWLIST, id, role.name());
    }

    /** Returns false if the id was not on the allowlist. */
    public boolean remove(String id) {
        return redis.opsForHash().delete(ALLOWLIST, id) > 0;
    }

    public boolean isBanned(String id) {
        return Boolean.TRUE.equals(redis.opsForSet().isMember(BANS, id));
    }

    public Set<String> banned() {
        return redis.opsForSet().members(BANS);
    }

    public void ban(String id) {
        redis.opsForSet().add(BANS, id);
    }

    /** Inputs counted in the current 1 s rate-limit window (key written by RateLimiter). */
    public int inputsThisSecond(String id) {
        String used = redis.opsForValue().get("ratelimit:" + id);
        return used == null ? 0 : Integer.parseInt(used);
    }
}
