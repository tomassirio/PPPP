package dev.pppp.model;

/** POST /api/v1/inputs response: the stream entry id and the mode it was queued under. */
public record InputAccepted(String id, String mode) {}
