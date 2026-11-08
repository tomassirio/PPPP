package dev.pppp.input;

import org.springframework.stereotype.Service;

/**
 * CONTRACT used by InputController and ChatCommandBridge. The input workstream replaces the body.
 * Throws ResponseStatusException: 400 bad command, 429 rate limited, 503 queue full (with Retry-After).
 */
@Service
public class InputService {

    public record Accepted(String id, String mode) {}

    public Accepted submit(String userId, String command, int repeat) {
        throw new UnsupportedOperationException("input workstream");
    }
}
