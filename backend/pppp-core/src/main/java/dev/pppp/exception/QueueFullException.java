package dev.pppp.exception;

/** The input queue is at capacity. HTTP 503 with Retry-After. */
public class QueueFullException extends RuntimeException {
    private final int retryAfterSeconds;

    public QueueFullException(String message, int retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
