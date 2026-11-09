package dev.pppp.service;

import dev.pppp.emulator.Button;
import dev.pppp.exception.NotFoundException;
import dev.pppp.exception.QueueFullException;
import dev.pppp.model.InputAccepted;
import dev.pppp.model.InputStatus;
import dev.pppp.model.QueuedInput;
import dev.pppp.repository.InputQueueRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * The input queue. "Queued" = delivered-but-unacked (being executed) + not yet delivered.
 * Used by InputController and ChatCommandBridge. submit throws IllegalArgumentException (bad command),
 * RateLimitedException and QueueFullException.
 */
@Service
public class InputService {
    private static final java.util.Set<Button> DIRECTIONS = java.util.EnumSet.of(Button.UP, Button.DOWN, Button.LEFT, Button.RIGHT);
    private final InputQueueRepository queue;
    private final RateLimiter rateLimiter;
    private final ModeService modes;
    private final SettingsService settings;
    private final ChatService chat;

    public InputService(InputQueueRepository queue, RateLimiter rateLimiter, ModeService modes, SettingsService settings,
                        ChatService chat) {
        this.queue = queue;
        this.rateLimiter = rateLimiter;
        this.modes = modes;
        this.settings = settings;
        this.chat = chat;
    }

    public InputAccepted submit(String userId, String command, int repeat) {
        return submit(userId, command, repeat, false);
    }

    /**
     * walk = the player is holding a direction (pad or arrow key): pressed long enough to chain steps without a
     * release in between. Only for a single direction in anarchy; ignored in democracy (one vote per window).
     */
    public InputAccepted submit(String userId, String command, int repeat, boolean walk) {
        rateLimiter.check(userId);
        String mode = modes.current();
        List<Button> steps = CommandParser.parse(command, repeat, mode);
        walk = walk && ModeService.ANARCHY.equals(mode);
        if (walk && (steps.size() != 1 || !DIRECTIONS.contains(steps.getFirst()))) {
            throw new IllegalArgumentException("walk is only for a single direction");
        }
        // ponytail: check-then-add, concurrent submits can overshoot the cap by a few; fine for 18 players.
        // ponytail: fixed 1 s Retry-After; derive from depth x hold frames if clients hammer.
        if (depth() >= settings.input().maxQueueLength()) throw new QueueFullException("Queue is full", 1);

        boolean single = steps.stream().distinct().count() == 1;
        String id = queue.add(userId, CommandParser.serialize(steps), single ? steps.size() : 1, mode, walk);
        queue.setStatus(id, "QUEUED", null);
        modes.markActive(userId);
        return new InputAccepted(id, mode);
    }

    /** position: 0 = executing now, 1 = next, ... (only while QUEUED). NotFoundException when unknown or expired. */
    public InputStatus status(String id) {
        var s = queue.storedStatus(id);
        if (s == null) throw new NotFoundException("Unknown or expired input");
        Long position = "QUEUED".equals(s.status()) ? queue.countDeliverableThrough(id) : null;
        return new InputStatus(id, s.status(), s.executedAt(), position);
    }

    /** Not yet delivered to the scheduler, oldest first. limit is clamped to 1..100. */
    public List<QueuedInput> upcoming(int limit) {
        return queue.undelivered(Math.clamp(limit, 1, 100)).stream()
                .map(e -> new QueuedInput(e.id(), e.user(), e.cmd()))
                .toList();
    }

    /** Pending (being executed) + not yet delivered. */
    public long depth() {
        return queue.pendingCount() + queue.undelivered(InputQueueRepository.MAXLEN).size();
    }

    /** Marks every queued input DROPPED, moves the group past them and announces it in chat. Returns how many. */
    public int flush(String adminId) {
        List<String> ids = new ArrayList<>(queue.pendingIds());
        var undelivered = queue.undelivered(InputQueueRepository.MAXLEN);
        undelivered.forEach(e -> ids.add(e.id()));
        ids.forEach(id -> queue.setStatus(id, "DROPPED", null));
        if (!undelivered.isEmpty()) queue.markDeliveredThrough(undelivered.getLast().id());
        // Pending ones are acked by the scheduler when it sees DROPPED.
        chat.postAdmin(adminId + " flushed the input queue (" + ids.size() + " dropped)");
        return ids.size();
    }
}
