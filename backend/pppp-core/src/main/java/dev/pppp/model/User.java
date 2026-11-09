package dev.pppp.model;

/** An allowlisted user. id = lower-cased email. */
/** mutedUntil: chat mute expiry in epoch ms, null when not muted. */
public record User(String id, Role role, boolean banned, Long mutedUntil) {}
