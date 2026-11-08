package dev.pppp.service;

import dev.pppp.model.LiveEvent;
import org.springframework.context.ApplicationEventPublisher;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Chat in Redis: messages in chat:stream (~5000), mutes in chat:mutes, delivered over SSE. Other workstreams call postSystem. */
@Service
public class ChatService {
    static final String STREAM = "chat:stream";
    static final String MUTES = "chat:mutes";
    static final int MAX_LENGTH = 300;
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    public record Message(String id, String user, String name, String text, String kind, boolean command, long at) {}

    private final StringRedisTemplate redis;
    private final ApplicationEventPublisher events;
    private final ChatCommandBridge bridge;

    public ChatService(StringRedisTemplate redis, ApplicationEventPublisher events, ChatCommandBridge bridge) {
        this.redis = redis;
        this.events = events;
        this.bridge = bridge;
    }

    public Message send(String userId, String displayName, String text) {
        text = text == null ? "" : text.strip();
        if (text.isEmpty() || text.length() > MAX_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message must be 1 to " + MAX_LENGTH + " characters");
        }
        if (isMuted(userId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are muted");
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("ratelimit:chat:" + userId, "1", Duration.ofSeconds(1)))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "One message per second");
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
    public List<Message> history(String before, int limit) {
        if (before != null && !before.matches("\\d+-\\d+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid message id");
        }
        if (limit < 1 || limit > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be 1 to 100");
        Range<String> range = before == null
                ? Range.unbounded()
                : Range.of(Range.Bound.unbounded(), Range.Bound.exclusive(before));
        List<MapRecord<String, Object, Object>> records =
                redis.opsForStream().reverseRange(STREAM, range, Limit.limit().count(limit));
        return records == null ? List.of() : records.stream().map(ChatService::toMessage).toList();
    }

    public void delete(String id) {
        Long removed = redis.opsForStream().delete(STREAM, id);
        if (removed == null || removed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        events.publishEvent(new LiveEvent("CHAT_DELETED", Map.of("id", id)));
    }

    public void mute(String userId, int minutes) {
        long until = System.currentTimeMillis() + Duration.ofMinutes(minutes).toMillis();
        redis.opsForHash().put(MUTES, userId, Long.toString(until));
    }

    private boolean isMuted(String userId) {
        Object until = redis.opsForHash().get(MUTES, userId);
        if (until == null) return false;
        if (Long.parseLong(until.toString()) > System.currentTimeMillis()) return true;
        redis.opsForHash().delete(MUTES, userId);
        return false;
    }

    private Message store(String userId, String name, String text, String kind, boolean command) {
        Map<String, String> fields = Map.of(
                "user", userId, "name", name, "text", text, "kind", kind, "command", Boolean.toString(command));
        RecordId id = redis.opsForStream().add(
                StreamRecords.newRecord().in(STREAM).ofMap(fields),
                XAddOptions.maxlen(5000).approximateTrimming(true));
        var message = new Message(id.getValue(), userId, name, text, kind, command, id.getTimestamp());
        events.publishEvent(new LiveEvent("CHAT_MESSAGE", message));
        return message;
    }

    private static Message toMessage(MapRecord<String, Object, Object> r) {
        Map<Object, Object> f = r.getValue();
        return new Message(r.getId().getValue(), (String) f.get("user"), (String) f.getOrDefault("name", f.get("user")),
                (String) f.get("text"), (String) f.get("kind"), "true".equals(f.get("command")), r.getId().getTimestamp());
    }
}
