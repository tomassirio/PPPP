package dev.pppp.exception;

/** The requested thing does not exist. HTTP 404. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
