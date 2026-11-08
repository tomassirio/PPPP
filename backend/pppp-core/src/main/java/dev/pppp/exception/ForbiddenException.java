package dev.pppp.exception;

/** The user may not do this right now (e.g. muted in chat). HTTP 403. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
