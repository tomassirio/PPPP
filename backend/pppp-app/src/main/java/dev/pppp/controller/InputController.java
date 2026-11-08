package dev.pppp.controller;

import dev.pppp.model.InputAccepted;
import dev.pppp.model.InputStatus;
import dev.pppp.model.QueuedInput;
import dev.pppp.security.CurrentUser;
import dev.pppp.service.InputService;
import dev.pppp.service.ModeService;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InputController {
    public record InputRequest(String command, Integer repeat) {}
    public record ModeVote(String mode) {}

    private final InputService inputs;
    private final ModeService modes;

    public InputController(InputService inputs, ModeService modes) {
        this.inputs = inputs;
        this.modes = modes;
    }

    /** Body: the active players' mode vote counts after this vote. */
    @PostMapping("/api/v1/votes/mode")
    public ResponseEntity<Map<String, Long>> voteMode(@RequestBody ModeVote req, Authentication auth) {
        return ResponseEntity.accepted().body(modes.vote(CurrentUser.id(auth), req.mode()));
    }

    @PostMapping("/api/v1/inputs")
    public ResponseEntity<InputAccepted> submit(@RequestBody InputRequest req, Authentication auth) {
        var accepted = inputs.submit(CurrentUser.id(auth), req.command(), req.repeat() == null ? 1 : req.repeat());
        return ResponseEntity.accepted().location(URI.create("/api/v1/inputs/" + accepted.id())).body(accepted);
    }

    @GetMapping("/api/v1/inputs/{id}")
    public InputStatus status(@PathVariable String id) {
        return inputs.status(id);
    }

    @GetMapping("/api/v1/queue")
    public List<QueuedInput> queue(@RequestParam(defaultValue = "20") int limit) {
        return inputs.upcoming(limit);
    }

    @DeleteMapping("/api/v1/admin/queue")
    public Map<String, Integer> flush(Authentication auth) {
        return Map.of("dropped", inputs.flush(CurrentUser.id(auth)));
    }
}
