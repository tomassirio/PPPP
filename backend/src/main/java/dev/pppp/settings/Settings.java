package dev.pppp.settings;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public final class Settings {
    private Settings() {}

    public record Input(
            @NotNull @Pattern(regexp = "ANARCHY|DEMOCRACY") String mode,
            @Min(1) @Max(60) int democracyWindowSeconds,
            @Min(1) @Max(100) int enterDemocracyPercent,
            @Min(1) @Max(100) int leaveDemocracyPercent,
            @Min(1) @Max(60) int holdFrames,
            @Min(1) @Max(60) int releaseFrames,
            @Min(1) @Max(500) int maxQueueLength,
            boolean chatCommands) {
        public static final Input DEFAULT = new Input("ANARCHY", 10, 80, 50, 6, 6, 50, false);
    }

    public record RateLimit(@Min(1) @Max(20) int inputsPerSecond) {
        public static final RateLimit DEFAULT = new RateLimit(2);
    }

    public record Autosave(boolean enabled, @NotBlank String cron, @Min(1) @Max(1000) int keep) {
        public static final Autosave DEFAULT = new Autosave(true, "0 0/30 * * * ?", 48);
    }
}
