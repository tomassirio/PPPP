package dev.pppp.controller;

import dev.pppp.emulator.EmulatorHost;
import dev.pppp.model.ConsoleCommand;
import dev.pppp.service.ConsoleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/console")
public class ConsoleController {
    private final ConsoleService console;

    public ConsoleController(ConsoleService console) {
        this.console = console;
    }

    @PutMapping("/cartridge")
    public EmulatorHost.Status insert(@Valid @RequestBody ConsoleCommand.Insert req) { return console.insert(req.cartridgeId()); }

    @PutMapping("/power")
    public EmulatorHost.Status power(@Valid @RequestBody ConsoleCommand.Power req) { return console.power(req.state()); }

    @PostMapping("/reset")
    public EmulatorHost.Status reset() { return console.reset(); }

    @PostMapping("/pause")
    public EmulatorHost.Status pause() { return console.pause(); }

    @PostMapping("/resume")
    public EmulatorHost.Status resume() { return console.resume(); }

    @PutMapping("/speed")
    public EmulatorHost.Status speed(@Valid @RequestBody ConsoleCommand.Speed req) { return console.speed(req.multiplier()); }
}
