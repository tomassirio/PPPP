package dev.pppp.model;

import java.time.Instant;

/** A save state of one cartridge. The kind is the id's prefix: {manual|auto}-{timestamp}. */
public record SaveState(String id, String cartridgeId, Kind kind, Instant createdAt) {
    public enum Kind { manual, auto }
}
