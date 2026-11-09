package dev.pppp.model;

import java.util.List;

/**
 * What a game profile shows in the party box (GET /game/state, GAME_STATE): progress counters, the party and the
 * badges in the order they are meant to be earned.
 */
public record GameSummary(String game, List<Counter> counters, List<Member> party, List<Badge> badges) {
    public record Counter(String label, int value, int max) {}

    /** status is a short condition tag ("PSN", "FNT") or null; icon is an id for GET /game/icons/{icon}.png, 0 = none. */
    public record Member(String name, String species, int level, int hp, int maxHp, String status, int icon) {}

    /** place is where it is earned ("Pewter Gym"). */
    public record Badge(String name, String place, boolean earned) {}
}
