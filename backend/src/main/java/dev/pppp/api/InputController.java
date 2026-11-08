package dev.pppp.api;

import dev.pppp.auth.CurrentUser;
import dev.pppp.chat.ChatService;
import dev.pppp.input.InputService;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class InputController {
    public record InputRequest(String command, Integer repeat) {}

    private final InputService inputs;
    private final ChatService chat;

    public InputController(InputService inputs, ChatService chat) {
        this.inputs = inputs;
        this.chat = chat;
    }

    @PostMapping("/api/v1/inputs")
    public ResponseEntity<InputService.Accepted> submit(@RequestBody InputRequest req, Authentication auth) {
        var accepted = inputs.submit(CurrentUser.id(auth), req.command(), req.repeat() == null ? 1 : req.repeat());
        return ResponseEntity.accepted().location(URI.create("/api/v1/inputs/" + accepted.id())).body(accepted);
    }

    @GetMapping("/api/v1/inputs/{id}")
    public InputService.Status status(@PathVariable String id) {
        var status = inputs.status(id);
        if (status == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown or expired input");
        return status;
    }

    @GetMapping("/api/v1/queue")
    public List<InputService.Queued> queue(@RequestParam(defaultValue = "20") int limit) {
        return inputs.upcoming(Math.clamp(limit, 1, 100));
    }

    @DeleteMapping("/api/v1/admin/queue")
    public Map<String, Integer> flush(Authentication auth) {
        int dropped = inputs.flush();
        chat.postSystem(CurrentUser.id(auth) + " flushed the input queue (" + dropped + " dropped)");
        return Map.of("dropped", dropped);
    }
}
