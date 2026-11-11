package dev.pppp.model;

/** Someone not on the allowlist who tried to sign in. name and picture come from Google and may be null. */
public record AccessRequest(String email, String name, String picture, long requestedAt) {}
