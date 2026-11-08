package dev.pppp.model;

/** An allowlisted user. id = lower-cased email. */
public record User(String id, Role role, boolean banned) {}
