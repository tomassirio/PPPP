package dev.pppp.repository;

import dev.pppp.model.ChatMessage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * chat:stream (~5000 messages), chat:mutes (userId -> muted-until epoch ms), ratelimit:chat:{userId},
 * chat:reactions:{messageId} (userId -> reaction, 30-day TTL so lines trimmed off the stream don't leave them behind),
 * ratelimit:reaction:{userId}.
 */
@Repository
public class ChatRepository {
    static final String STREAM = "chat:stream";
    static final String MUTES = "chat:mutes";
    static final String REACTIONS = "chat:reactions:";
    static final Duration REACTIONS_TTL = Duration.ofDays(30);

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
        return new ChatMessage(id.getValue(), userId, name, text, kind, command, id.getTimestamp(), Map.of());
    }

    /** Newest first, strictly older than {@code before} (a stream id) when non-null. */
    public List<ChatMessage> newestBefore(String before, int limit) {
        Range<String> range = before == null
                ? Range.unbounded()
                : Range.of(Range.Bound.unbounded(), Range.Bound.exclusive(before));
        List<MapRecord<String, Object, Object>> records =
                redis.opsForStream().reverseRange(STREAM, range, Limit.limit().count(limit));
        if (records == null || records.isEmpty()) return List.of();
        List<Object> reactions = redis.executePipelined(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public Object execute(RedisOperations ops) {
                for (var r : records) ops.opsForHash().entries(REACTIONS + r.getId().getValue());
                return null;
            }
        });
        List<ChatMessage> out = new ArrayList<>(records.size());
        for (int i = 0; i < records.size(); i++) {
            out.add(toMessage(records.get(i), byReaction((Map<?, ?>) reactions.get(i))));
        }
        return out;
    }

    /** The kind of the line with this stream id, or null if there is none. */
    public String kindOf(String id) {
        List<MapRecord<String, Object, Object>> found = redis.opsForStream().range(STREAM, Range.closed(id, id));
        return found == null || found.isEmpty() ? null : (String) found.getFirst().getValue().get("kind");
    }

    /** Sets (or replaces) the user's reaction to a message. */
    public void react(String id, String userId, String reaction) {
        redis.opsForHash().put(REACTIONS + id, userId, reaction);
        redis.expire(REACTIONS + id, REACTIONS_TTL);
    }

    public void unreact(String id, String userId) {
        redis.opsForHash().delete(REACTIONS + id, userId);
    }

    /** reaction -> user ids, both sorted. */
    public Map<String, List<String>> reactions(String id) {
        return byReaction(redis.opsForHash().entries(REACTIONS + id));
    }

    /** True if a message was removed. */
    public boolean delete(String id) {
        Long removed = redis.opsForStream().delete(STREAM, id);
        redis.delete(REACTIONS + id);
        return removed != null && removed > 0;
    }

    /** Deletes every line and its reactions. Mutes stay. */
    public void clear() {
        redis.delete(STREAM);
        // ponytail: KEYS is O(all keys); fine at this size (reset is rare, one key per reacted-to line).
        redis.delete(redis.keys(REACTIONS + "*"));
    }

    /** Deletes every non-USER line (SYSTEM, ADMIN); returns how many. */
    public int deleteNonUser() {
        // ponytail: one XRANGE over the whole stream; fine while it is capped at ~5000 entries.
        List<MapRecord<String, Object, Object>> all = redis.opsForStream().range(STREAM, Range.unbounded());
        if (all == null) return 0;
        String[] ids = all.stream().filter(r -> !"USER".equals(r.getValue().get("kind")))
                .map(r -> r.getId().getValue()).toArray(String[]::new);
        if (ids.length > 0) {
            redis.opsForStream().delete(STREAM, ids);
            redis.delete(Arrays.stream(ids).map(id -> REACTIONS + id).toList());
        }
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

    /** Claims the user's reaction slot for {@code window}; false if already taken. */
    public boolean tryAcquireReactionSlot(String userId, Duration window) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("ratelimit:reaction:" + userId, "1", window));
    }

    private static Map<String, List<String>> byReaction(Map<?, ?> userToReaction) {
        Map<String, List<String>> out = new TreeMap<>();
        if (userToReaction == null) return out;
        userToReaction.forEach((user, reaction) -> out.computeIfAbsent((String) reaction, r -> new ArrayList<>()).add((String) user));
        out.values().forEach(users -> users.sort(null));
        return out;
    }

    private static ChatMessage toMessage(MapRecord<String, Object, Object> r, Map<String, List<String>> reactions) {
        Map<Object, Object> f = r.getValue();
        return new ChatMessage(r.getId().getValue(), (String) f.get("user"), (String) f.getOrDefault("name", f.get("user")),
                (String) f.get("text"), (String) f.get("kind"), "true".equals(f.get("command")), r.getId().getTimestamp(),
                reactions);
    }
}
