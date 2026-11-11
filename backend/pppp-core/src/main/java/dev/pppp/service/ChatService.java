package dev.pppp.service;

import dev.pppp.exception.ForbiddenException;
import dev.pppp.exception.NotFoundException;
import dev.pppp.exception.RateLimitedException;
import dev.pppp.model.ChatMessage;
import dev.pppp.model.LiveEvent;
import dev.pppp.repository.ChatRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/** Chat rules: length, mutes, one message per second, command bridging; delivered over SSE. Other workstreams call postSystem. */
@Service
public class ChatService {
    static final int MAX_LENGTH = 300;
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatRepository repo;
    private final ApplicationEventPublisher events;
    private final ChatCommandBridge bridge;

    public ChatService(ChatRepository repo, ApplicationEventPublisher events, ChatCommandBridge bridge) {
        this.repo = repo;
        this.events = events;
        this.bridge = bridge;
    }

    public ChatMessage send(String userId, String displayName, String text) {
        text = text == null ? "" : text.strip();
        if (text.isEmpty() || text.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Message must be 1 to " + MAX_LENGTH + " characters");
        }
        if (isMuted(userId)) throw new ForbiddenException("You are muted");
        if (!repo.tryAcquireSendSlot(userId, Duration.ofSeconds(1))) {
            throw new RateLimitedException("One message per second");
        }
        return store(userId, displayName, text, "USER", bridge.trySubmit(userId, text));
    }

    /** A SYSTEM message everyone sees (mode changes, game events). Stored in chat:stream, published as CHAT_MESSAGE. Never throws. */
    public void postSystem(String text) {
        post(text, "SYSTEM");
    }

    /** An ADMIN message: console and moderation notices only admins see, in history and live. Never throws. */
    public void postAdmin(String text) {
        post(text, "ADMIN");
    }

    private void post(String text, String kind) {
        try {
            store("system", "System", text, kind, false);
        } catch (RuntimeException e) {
            log.warn("Could not post {} message '{}'", kind, text, e);
        }
    }

    /** Newest first, strictly older than {@code before} when given. ADMIN messages only for admins. */
    public List<ChatMessage> history(String before, int limit, boolean admin) {
        if (before != null && !before.matches("\\d+-\\d+")) throw new IllegalArgumentException("Invalid message id");
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be 1 to 100");
        if (admin) return repo.newestBefore(before, limit);
        List<ChatMessage> out = new ArrayList<>(limit);
        String cursor = before;
        while (out.size() < limit) { // skip admin lines but still fill the page
            List<ChatMessage> page = repo.newestBefore(cursor, limit);
            for (ChatMessage m : page) {
                if (!"ADMIN".equals(m.kind()) && out.size() < limit) out.add(m);
            }
            if (page.size() < limit) break;
            cursor = page.getLast().id();
        }
        return out;
    }

    public void delete(String id) {
        if (!repo.delete(id)) throw new NotFoundException("No such message");
        events.publishEvent(new LiveEvent("CHAT_DELETED", Map.of("id", id)));
    }

    /** Removes all SYSTEM and ADMIN lines (players' messages stay), tells browsers via CHAT_CLEARED, logs who did it. */
    public int clearSystem(String adminId) {
        int removed = repo.deleteNonUser();
        events.publishEvent(new LiveEvent("CHAT_CLEARED", Map.of("removed", removed)));
        postAdmin(adminId + " cleared " + removed + " system messages");
        return removed;
    }

    /** Deletes every chat line, players' included. Mutes stay. Browsers are told by the caller. */
    public void clearAll() {
        repo.clear();
    }

    public void mute(String userId, int minutes) {
        repo.muteUntil(userId, System.currentTimeMillis() + Duration.ofMinutes(minutes).toMillis());
    }

    public void unmute(String userId) {
        repo.unmute(userId);
    }

    /** Active mute expiry in epoch ms, or null; clears an expired mute. */
    public Long mutedUntil(String userId) {
        Long until = repo.mutedUntil(userId);
        if (until == null || until > System.currentTimeMillis()) return until;
        repo.unmute(userId);
        return null;
    }

    private boolean isMuted(String userId) {
        return mutedUntil(userId) != null;
    }

    private ChatMessage store(String userId, String name, String text, String kind, boolean command) {
        ChatMessage message = repo.append(userId, name, text, kind, command);
        events.publishEvent(new LiveEvent("CHAT_MESSAGE", message));
        return message;
    }
}
