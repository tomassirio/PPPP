package dev.pppp.model;

import dev.pppp.emulator.Button;
import java.util.Map;

/** The open democracy window n: votes per button (first-vote order) and when it closes (epoch ms). */
public record VoteTally(String window, Map<Button, Long> tally, long endsAt) {}
