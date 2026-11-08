package dev.pppp.model;

import dev.pppp.emulator.EmulatorHost;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Request bodies of the admin console endpoints. */
public final class ConsoleCommand {
    private ConsoleCommand() {}

    public record Insert(@NotBlank String cartridgeId) {}
    public record Power(@NotNull EmulatorHost.Power state) {}
    public record Speed(@DecimalMin("0.25") @DecimalMax("4.0") double multiplier) {}
}
