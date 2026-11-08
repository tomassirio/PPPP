package dev.pppp.model;

/** An input not yet delivered to the scheduler. command is the serialized steps, e.g. "LEFT,LEFT". */
public record QueuedInput(String id, String user, String command) {}
