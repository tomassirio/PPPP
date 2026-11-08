package dev.pppp.api;

import dev.pppp.engine.EmulatorHost;
import dev.pppp.input.InputService;
import dev.pppp.input.ModeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StateController {
    /** tally is null until the democracy workstream fills it. */
    public record State(EmulatorHost.Power power, String cartridgeId, boolean paused, String mode, long queueDepth, Object tally) {}

    private final EmulatorHost host;
    private final ModeService modes;
    private final InputService inputs;

    public StateController(EmulatorHost host, ModeService modes, InputService inputs) {
        this.host = host;
        this.modes = modes;
        this.inputs = inputs;
    }

    @GetMapping("/api/v1/state")
    public State state() {
        var s = host.status();
        return new State(s.power(), s.cartridgeId(), s.paused(), modes.current(), inputs.depth(), null);
    }
}
