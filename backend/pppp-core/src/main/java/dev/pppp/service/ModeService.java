package dev.pppp.service;

import dev.pppp.model.LiveEvent;
import dev.pppp.model.Settings;
import dev.pppp.repository.VoteRepository;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Current input mode (ANARCHY or DEMOCRACY), active players and mode votes.
 * Voting makes you active; only active players' votes count, against the number of active players.
 * Every mode change, by vote or by admin PUT, goes through SettingsService.save and is announced here.
 */
@Service
public class ModeService {
    public static final String ANARCHY = "ANARCHY";
    public static final String DEMOCRACY = "DEMOCRACY";
    private static final Duration ACTIVE_FOR = Duration.ofMinutes(5);

    private final VoteRepository votes;
    private final SettingsService settings;
    private final ApplicationEventPublisher events;
    private final ChatService chat;
    private volatile String last; // last mode seen, to tell a switch from a re-save

    public ModeService(VoteRepository votes, SettingsService settings, ApplicationEventPublisher events, ChatService chat) {
        this.votes = votes;
        this.settings = settings;
        this.events = events;
        this.chat = chat;
    }

    public String current() {
        String mode = settings.input().mode();
        if (last == null) last = mode;
        return mode;
    }

    public void markActive(String userId) {
        votes.markActive(userId, System.currentTimeMillis());
    }

    /** Players with an input or mode vote in the last 5 min. */
    public Set<String> activePlayers() {
        return votes.activeSince(System.currentTimeMillis() - ACTIVE_FOR.toMillis());
    }

    /** Records the vote and switches when the threshold is reached. Returns the active players' vote counts. */
    public Map<String, Long> vote(String userId, String mode) {
        if (!ANARCHY.equals(mode) && !DEMOCRACY.equals(mode)) {
            throw new IllegalArgumentException("mode must be ANARCHY or DEMOCRACY");
        }
        markActive(userId);
        votes.putModeVote(userId, mode);
        Map<String, Long> counts = counts();
        // ponytail: read-then-save, two racing deciding votes both save the same mode; the listener ignores the repeat.
        Settings.Input s = settings.input();
        long active = activePlayers().size();
        String target = DEMOCRACY.equals(s.mode()) ? ANARCHY : DEMOCRACY;
        int percent = DEMOCRACY.equals(target) ? s.enterDemocracyPercent() : s.leaveDemocracyPercent();
        if (counts.get(target) * 100 >= (long) percent * active) {
            settings.save(new Settings.Input(target, s.democracyWindowSeconds(), s.enterDemocracyPercent(),
                    s.leaveDemocracyPercent(), s.holdFrames(), s.releaseFrames(), s.maxQueueLength(), s.chatCommands()));
            return counts();
        }
        events.publishEvent(new LiveEvent("MODE_VOTES", counts));
        return counts;
    }

    /** Mode votes of currently active players. */
    public Map<String, Long> counts() {
        Set<String> active = activePlayers();
        Map<String, Long> counts = new TreeMap<>(Map.of(ANARCHY, 0L, DEMOCRACY, 0L));
        votes.modeVotes().forEach((user, mode) -> {
            if (active.contains(user)) counts.merge(mode, 1L, Long::sum);
        });
        return counts;
    }

    @EventListener
    public void onInputSettings(Settings.Input s) {
        String prev = last;
        last = s.mode();
        if (prev == null || prev.equals(s.mode())) return;
        votes.clearModeVotes();
        events.publishEvent(new LiveEvent("MODE_CHANGED", Map.of("mode", s.mode())));
        chat.postSystem("Mode is now " + s.mode());
    }
}
