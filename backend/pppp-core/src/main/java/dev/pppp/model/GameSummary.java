package dev.pppp.model;

import java.util.List;

/** What a game profile shows in the status panel (GET /game/state, GAME_STATE): progress counters and the party. */
public record GameSummary(String game, List<Counter> counters, List<Member> party) {
    public record Counter(String label, int value, int max) {}

    /** status is a short condition tag ("PSN", "FNT") or null. */
    public record Member(String name, String species, int level, int hp, int maxHp, String status) {}
}
