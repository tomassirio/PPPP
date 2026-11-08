package dev.pppp.api;

import dev.pppp.engine.EmulatorHost;
import dev.pppp.input.InputScheduler;
import dev.pppp.input.InputService;
import dev.pppp.input.ModeService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StateController {
    /** tally is the open democracy window, or null. modeVotes counts active players' mode votes. */
    public record State(EmulatorHost.Power power, String cartridgeId, boolean paused, double speed, String mode, long queueDepth,
                        InputScheduler.Tally tally, Map<String, Long> modeVotes) {}

    private final EmulatorHost host;
    private final ModeService modes;
    private final InputService inputs;
    private final InputScheduler scheduler;

    public StateController(EmulatorHost host, ModeService modes, InputService inputs, InputScheduler scheduler) {
        this.host = host;
        this.modes = modes;
        this.inputs = inputs;
        this.scheduler = scheduler;
    }

    @GetMapping("/api/v1/state")
    public State state() {
        var s = host.status();
        return new State(s.power(), s.cartridgeId(), s.paused(), s.speed(), modes.current(), inputs.depth(),
                scheduler.tally(), modes.counts());
    }
}
