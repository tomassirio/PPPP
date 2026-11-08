package dev.pppp.model;

/** Published (Spring ApplicationEvent) when a user is removed from the allowlist or banned. Listeners end their sessions and close their sockets. */
public record UserRevokedEvent(String userId) {}
