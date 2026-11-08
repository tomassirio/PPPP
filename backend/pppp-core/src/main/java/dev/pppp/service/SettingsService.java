package dev.pppp.service;

import dev.pppp.model.Settings;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Settings live in Redis as JSON under settings:{name}. Every save publishes the new record as a
 * Spring ApplicationEvent, so listeners (e.g. the autosave scheduler) can react with @EventListener.
 */
@Service
public class SettingsService {
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final ApplicationEventPublisher events;

    public SettingsService(StringRedisTemplate redis, ObjectMapper json, ApplicationEventPublisher events) {
        this.redis = redis;
        this.json = json;
        this.events = events;
    }

    public Settings.Input input() { return read("input", Settings.Input.class, Settings.Input.DEFAULT); }
    public Settings.RateLimit rateLimit() { return read("rate-limit", Settings.RateLimit.class, Settings.RateLimit.DEFAULT); }
    public Settings.Autosave autosave() { return read("autosave", Settings.Autosave.class, Settings.Autosave.DEFAULT); }

    public void save(Settings.Input s) { write("input", s); }
    public void save(Settings.RateLimit s) { write("rate-limit", s); }
    public void save(Settings.Autosave s) { write("autosave", s); }

    private <T> T read(String name, Class<T> type, T fallback) {
        String raw = redis.opsForValue().get("settings:" + name);
        if (raw == null) return fallback;
        try {
            return json.readValue(raw, type);
        } catch (JsonProcessingException e) {
            return fallback;
        }
    }

    private void write(String name, Object value) {
        try {
            redis.opsForValue().set("settings:" + name, json.writeValueAsString(value));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        events.publishEvent(value);
    }
}
