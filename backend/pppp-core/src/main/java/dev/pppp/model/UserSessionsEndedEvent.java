package dev.pppp.model;

/** Published on a role change: the user's HTTP sessions end so the new role applies on next login. Sockets stay open. */
public record UserSessionsEndedEvent(String userId) {}
