package dev.pppp.model;

import java.util.List;

/**
 * What a game profile shows in the party box (GET /game/state, GAME_STATE): progress counters, the party and the
 * badges in the order they are meant to be earned, and the collection (Pokédex) for the side banners.
 */
public record GameSummary(String game, List<Counter> counters, List<Member> party, List<Badge> badges,
                          Collection collection) {
    public record Counter(String label, int value, int max) {}

    /** status is a short condition tag ("PSN", "FNT") or null; icon is an id for GET /game/icons/{icon}.png, 0 = none. */
    public record Member(String name, String species, int level, int hp, int maxHp, String status, int icon) {}

    /** place is where it is earned ("Pewter Gym"). */
    public record Badge(String name, String place, boolean earned) {}

    /**
     * What there is to collect (the Pokédex), numbered 1..size; each number is also an icon id. names holds the
     * names of seen entries only (null for unseen, so they are not given away).
     */
    public record Collection(int size, List<Integer> seen, List<Integer> owned, List<String> names) {}
}
