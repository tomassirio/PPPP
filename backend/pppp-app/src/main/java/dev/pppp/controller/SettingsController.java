package dev.pppp.controller;

import dev.pppp.model.Settings;
import dev.pppp.service.SettingsService;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/settings")
public class SettingsController {
    private final SettingsService settings;

    public SettingsController(SettingsService settings) {
        this.settings = settings;
    }

    @GetMapping("/input")
    public Settings.Input input() { return settings.input(); }

    @PutMapping("/input")
    public Settings.Input input(@Valid @RequestBody Settings.Input s) { settings.save(s); return s; }

    @GetMapping("/rate-limit")
    public Settings.RateLimit rateLimit() { return settings.rateLimit(); }

    @PutMapping("/rate-limit")
    public Settings.RateLimit rateLimit(@Valid @RequestBody Settings.RateLimit s) { settings.save(s); return s; }

    @GetMapping("/autosave")
    public Settings.Autosave autosave() { return settings.autosave(); }

    @PutMapping("/autosave")
    public Settings.Autosave autosave(@Valid @RequestBody Settings.Autosave s) { settings.save(s); return s; }
}
