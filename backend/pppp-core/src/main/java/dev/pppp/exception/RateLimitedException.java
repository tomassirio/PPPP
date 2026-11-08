package dev.pppp.exception;

/** The user is going too fast. HTTP 429. */
public class RateLimitedException extends RuntimeException {
    public RateLimitedException(String message) {
        super(message);
    }
}
