package dev.pppp.repository;

import dev.pppp.model.ChatMessage;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/** chat:stream (~5000 messages), chat:mutes (userId -> muted-until epoch ms), ratelimit:chat:{userId}. */
@Repository
public class ChatRepository {
    static final String STREAM = "chat:stream";
    static final String MUTES = "chat:mutes";

    private final StringRedisTemplate redis;

    public ChatRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public ChatMessage append(String userId, String name, String text, String kind, boolean command) {
        Map<String, String> fields = Map.of(
                "user", userId, "name", name, "text", text, "kind", kind, "command", Boolean.toString(command));
        RecordId id = redis.opsForStream().add(
                StreamRecords.newRecord().in(STREAM).ofMap(fields),
                XAddOptions.maxlen(5000).approximateTrimming(true));
        return new ChatMessage(id.getValue(), userId, name, text, kind, command, id.getTimestamp());
    }

    /** Newest first, strictly older than {@code before} (a stream id) when non-null. */
    public List<ChatMessage> newestBefore(String before, int limit) {
        Range<String> range = before == null
                ? Range.unbounded()
                : Range.of(Range.Bound.unbounded(), Range.Bound.exclusive(before));
        List<MapRecord<String, Object, Object>> records =
                redis.opsForStream().reverseRange(STREAM, range, Limit.limit().count(limit));
        return records == null ? List.of() : records.stream().map(ChatRepository::toMessage).toList();
    }

    /** True if a message was removed. */
    public boolean delete(String id) {
        Long removed = redis.opsForStream().delete(STREAM, id);
        return removed != null && removed > 0;
    }

    /** Deletes every line. Mutes stay. */
    public void clear() {
        redis.delete(STREAM);
    }

    /** Deletes every non-USER line (SYSTEM, ADMIN); returns how many. */
    public int deleteNonUser() {
        // ponytail: one XRANGE over the whole stream; fine while it is capped at ~5000 entries.
        List<MapRecord<String, Object, Object>> all = redis.opsForStream().range(STREAM, Range.unbounded());
        if (all == null) return 0;
        String[] ids = all.stream().filter(r -> !"USER".equals(r.getValue().get("kind")))
                .map(r -> r.getId().getValue()).toArray(String[]::new);
        if (ids.length > 0) redis.opsForStream().delete(STREAM, ids);
        return ids.length;
    }

    public void muteUntil(String userId, long untilEpochMs) {
        redis.opsForHash().put(MUTES, userId, Long.toString(untilEpochMs));
    }

    /** Muted-until epoch ms, or null if never muted. */
    public Long mutedUntil(String userId) {
        Object until = redis.opsForHash().get(MUTES, userId);
        return until == null ? null : Long.parseLong(until.toString());
    }

    public void unmute(String userId) {
        redis.opsForHash().delete(MUTES, userId);
    }

    /** Claims the user's chat send slot for {@code window}; false if already taken. */
    public boolean tryAcquireSendSlot(String userId, Duration window) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("ratelimit:chat:" + userId, "1", window));
    }

    private static ChatMessage toMessage(MapRecord<String, Object, Object> r) {
        Map<Object, Object> f = r.getValue();
        return new ChatMessage(r.getId().getValue(), (String) f.get("user"), (String) f.getOrDefault("name", f.get("user")),
                (String) f.get("text"), (String) f.get("kind"), "true".equals(f.get("command")), r.getId().getTimestamp());
    }
}
