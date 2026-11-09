package dev.pppp.service;

import dev.pppp.exception.ForbiddenException;
import dev.pppp.exception.NotFoundException;
import dev.pppp.exception.RateLimitedException;
import dev.pppp.model.ChatMessage;
import dev.pppp.model.LiveEvent;
import dev.pppp.repository.ChatRepository;
import java.time.Duration;
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

    /** Stores a SYSTEM message in chat:stream and publishes CHAT_MESSAGE. Must never throw. */
    public void postSystem(String text) {
        try {
            store("system", "System", text, "SYSTEM", false);
        } catch (RuntimeException e) {
            log.warn("Could not post system message '{}'", text, e);
        }
    }

    /** Newest first, strictly older than {@code before} when given. */
    public List<ChatMessage> history(String before, int limit) {
        if (before != null && !before.matches("\\d+-\\d+")) throw new IllegalArgumentException("Invalid message id");
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be 1 to 100");
        return repo.newestBefore(before, limit);
    }

    public void delete(String id) {
        if (!repo.delete(id)) throw new NotFoundException("No such message");
        events.publishEvent(new LiveEvent("CHAT_DELETED", Map.of("id", id)));
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
