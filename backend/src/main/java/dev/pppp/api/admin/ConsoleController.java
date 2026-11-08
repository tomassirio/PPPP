package dev.pppp.api.admin;

import dev.pppp.api.EventPublisher;
import dev.pppp.chat.ChatService;
import dev.pppp.engine.EmulatorHost;
import dev.pppp.storage.CartridgeStore;
import dev.pppp.storage.ConsoleStore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Every action announces itself in chat, publishes CONSOLE_CHANGED and is remembered across restarts. */
@RestController
@RequestMapping("/api/v1/admin/console")
public class ConsoleController {
    public record Insert(@NotBlank String cartridgeId) {}
    public record Power(@NotNull EmulatorHost.Power state) {}
    public record Speed(@DecimalMin("0.25") @DecimalMax("4.0") double multiplier) {}

    private final EmulatorHost host;
    private final CartridgeStore cartridges;
    private final ConsoleStore console;
    private final ChatService chat;
    private final EventPublisher events;

    public ConsoleController(EmulatorHost host, CartridgeStore cartridges, ConsoleStore console, ChatService chat,
                             EventPublisher events) {
        this.host = host;
        this.cartridges = cartridges;
        this.console = console;
        this.chat = chat;
        this.events = events;
    }

    @PutMapping("/cartridge")
    public EmulatorHost.Status insert(@Valid @RequestBody Insert req) {
        var rom = cartridges.require(req.cartridgeId());
        if (host.status().power() == EmulatorHost.Power.ON) throw new IllegalStateException("Power the console off first");
        host.insert(req.cartridgeId(), rom, cartridges.batterySave(req.cartridgeId()));
        return changed("Cartridge " + req.cartridgeId() + " inserted");
    }

    @PutMapping("/power")
    public EmulatorHost.Status power(@Valid @RequestBody Power req) {
        if (req.state() == EmulatorHost.Power.ON) host.powerOn();
        else host.powerOff();
        return changed("Console powered " + req.state().name().toLowerCase());
    }

    @PostMapping("/reset")
    public EmulatorHost.Status reset() { host.reset(); return changed("Console reset"); }

    @PostMapping("/pause")
    public EmulatorHost.Status pause() { host.pause(); return changed("Game paused"); }

    @PostMapping("/resume")
    public EmulatorHost.Status resume() { host.resume(); return changed("Game resumed"); }

    @PutMapping("/speed")
    public EmulatorHost.Status speed(@Valid @RequestBody Speed req) {
        host.setSpeed(req.multiplier());
        return changed("Speed set to " + req.multiplier() + "x");
    }

    private EmulatorHost.Status changed(String message) {
        EmulatorHost.Status s = host.status();
        console.remember(s);
        chat.postSystem(message);
        events.publish("CONSOLE_CHANGED", s);
        return s;
    }
}
