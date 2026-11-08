package dev.pppp.service;

import dev.pppp.model.Settings;
import dev.pppp.repository.SettingsRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Settings records with their defaults. Every save publishes the new record as a Spring ApplicationEvent,
 * so listeners (e.g. the autosave scheduler) can react with @EventListener.
 */
@Service
public class SettingsService {
    private final SettingsRepository repo;
    private final ApplicationEventPublisher events;

    public SettingsService(SettingsRepository repo, ApplicationEventPublisher events) {
        this.repo = repo;
        this.events = events;
    }

    public Settings.Input input() { return repo.find("input", Settings.Input.class, Settings.Input.DEFAULT); }
    public Settings.RateLimit rateLimit() { return repo.find("rate-limit", Settings.RateLimit.class, Settings.RateLimit.DEFAULT); }
    public Settings.Autosave autosave() { return repo.find("autosave", Settings.Autosave.class, Settings.Autosave.DEFAULT); }

    public void save(Settings.Input s) { write("input", s); }
    public void save(Settings.RateLimit s) { write("rate-limit", s); }
    public void save(Settings.Autosave s) { write("autosave", s); }

    private void write(String name, Object value) {
        repo.save(name, value);
        events.publishEvent(value);
    }
}
