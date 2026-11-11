package dev.pppp.controller;

import dev.pppp.emulator.EmulatorHost;
import dev.pppp.model.VoteTally;
import dev.pppp.service.InputScheduler;
import dev.pppp.service.InputService;
import dev.pppp.service.ModeService;
import dev.pppp.service.LaunchCountdown;
import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StateController {
    /**
     * tally is the open democracy window, or null. modeVotes counts active players' mode votes. launchAt is when
     * the console powers itself on (the countdown), or null.
     */
    public record State(EmulatorHost.Power power, String cartridgeId, boolean paused, double speed, String mode, long queueDepth,
                        VoteTally tally, Map<String, Long> modeVotes, Instant launchAt) {}

    private final EmulatorHost host;
    private final ModeService modes;
    private final InputService inputs;
    private final InputScheduler scheduler;
    private final LaunchCountdown launch;

    public StateController(EmulatorHost host, ModeService modes, InputService inputs, InputScheduler scheduler,
                           LaunchCountdown launch) {
        this.host = host;
        this.modes = modes;
        this.inputs = inputs;
        this.scheduler = scheduler;
        this.launch = launch;
    }

    @GetMapping("/api/v1/state")
    public State state() {
        var s = host.status();
        return new State(s.power(), s.cartridgeId(), s.paused(), s.speed(), modes.current(), inputs.depth(),
                scheduler.tally(), modes.counts(), launch.startsAt());
    }
}
