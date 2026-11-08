package dev.pppp.input;

import dev.pppp.settings.SettingsService;
import org.springframework.stereotype.Service;

/** Current input mode, ANARCHY or DEMOCRACY. Seam for the democracy workstream (mode votes). */
@Service
public class ModeService {
    private final SettingsService settings;

    public ModeService(SettingsService settings) {
        this.settings = settings;
    }

    public String current() {
        return settings.input().mode();
    }
}
