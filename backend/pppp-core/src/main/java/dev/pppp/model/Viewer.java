package dev.pppp.model;

/** Someone with /play open (an open event stream). id = lower-cased email; picture may be null. */
public record Viewer(String id, String name, String picture) {}
