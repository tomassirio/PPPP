package dev.pppp.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/** Settings records as JSON under settings:{name}. */
@Repository
public class SettingsRepository {
    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public SettingsRepository(StringRedisTemplate redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    /** The stored value, or fallback when missing or unreadable. */
    public <T> T find(String name, Class<T> type, T fallback) {
        String raw = redis.opsForValue().get("settings:" + name);
        if (raw == null) return fallback;
        try {
            return json.readValue(raw, type);
        } catch (JsonProcessingException e) {
            return fallback;
        }
    }

    public void save(String name, Object value) {
        try {
            redis.opsForValue().set("settings:" + name, json.writeValueAsString(value));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
