package dev.pppp.model;

/** Published (Spring ApplicationEvent) when a user is removed from the allowlist or banned. Listeners close their sockets. */
public record UserRevokedEvent(String userId) {}
