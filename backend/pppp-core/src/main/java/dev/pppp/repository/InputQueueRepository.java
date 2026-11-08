package dev.pppp.repository;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamInfo.XInfoGroup;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * inputs:stream entries {user, cmd, repeat, mode}, consumed by group "engine" (consumer "scheduler"),
 * plus an inputs:status:{id} hash {status, executedAt} per entry that expires 10 min after its last change.
 */
@Repository
public class InputQueueRepository {
    private static final String STREAM = "inputs:stream";
    private static final String GROUP = "engine";
    public static final int MAXLEN = 500;
    private static final Consumer CONSUMER = Consumer.from(GROUP, "scheduler");
    private static final Duration STATUS_TTL = Duration.ofMinutes(10);

    /** One stream entry. cmd: serialized steps, e.g. "LEFT,LEFT,DOWN". */
    public record Entry(String id, String user, String cmd, int repeat, String mode) {}

    /** The inputs:status:{id} hash. executedAt is null unless executed. */
    public record StoredStatus(String status, String executedAt) {}

    private final StringRedisTemplate redis;

    public InputQueueRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** XADD with approximate MAXLEN trimming. Returns the new entry id. */
    public String add(String user, String cmd, int repeat, String mode) {
        return redis.opsForStream().add(STREAM,
                Map.of("user", user, "cmd", cmd, "repeat", String.valueOf(repeat), "mode", mode),
                XAddOptions.maxlen(MAXLEN).approximateTrimming(true)).getValue();
    }

    public void setStatus(String id, String status, Instant executedAt) {
        String key = statusKey(id);
        redis.opsForHash().put(key, "status", status);
        if (executedAt != null) redis.opsForHash().put(key, "executedAt", executedAt.toString());
        redis.expire(key, STATUS_TTL);
    }

    /** Null when unknown or expired. */
    public String status(String id) {
        return (String) redis.opsForHash().get(statusKey(id), "status");
    }

    /** Null when unknown or expired. */
    public StoredStatus storedStatus(String id) {
        Map<Object, Object> h = redis.opsForHash().entries(statusKey(id));
        return h.isEmpty() ? null : new StoredStatus((String) h.get("status"), (String) h.get("executedAt"));
    }

    /** Entries after the group's last-delivered id, up to and including id. */
    public long countDeliverableThrough(String id) {
        // ponytail: O(queue) XRANGE per call, queue is capped at <= 500.
        return redis.opsForStream().range(STREAM,
                Range.of(Range.Bound.exclusive(group().lastDeliveredId()), Range.Bound.inclusive(id)),
                Limit.limit().count(MAXLEN)).size();
    }

    /** Not yet delivered to the group, oldest first. */
    public List<Entry> undelivered(int limit) {
        // ponytail: XRANGE after last-delivered id, O(queue) <= 500; XINFO GROUPS "lag" is O(1) if this ever matters.
        return redis.opsForStream().range(STREAM,
                        Range.of(Range.Bound.exclusive(group().lastDeliveredId()), Range.Bound.unbounded()),
                        Limit.limit().count(limit))
                .stream().map(InputQueueRepository::entry).toList();
    }

    /** Delivered but not yet acked. */
    public long pendingCount() {
        return group().pendingCount();
    }

    public List<String> pendingIds() {
        return redis.opsForStream().pending(STREAM, GROUP, Range.unbounded(), MAXLEN)
                .stream().map(p -> p.getIdAsString()).toList();
    }

    /** XGROUP SETID: the group skips everything up to and including id. */
    public void markDeliveredThrough(String id) {
        redis.execute((RedisCallback<Object>) c -> c.execute("XGROUP",
                bytes("SETID"), bytes(STREAM), bytes(GROUP), bytes(id)));
    }

    /** XGROUP CREATE inputs:stream engine 0 MKSTREAM; no-op when it already exists. */
    public void createGroup() {
        try {
            redis.opsForStream().createGroup(STREAM, ReadOffset.from("0"), GROUP);
        } catch (Exception e) {
            if (!String.valueOf(rootMessage(e)).contains("BUSYGROUP")) throw e;
        }
    }

    /** The scheduler's own delivered-but-unacked entries with id greater than afterId (crash recovery). */
    public List<Entry> readOwnPending(String afterId, int count) {
        return entries(redis.opsForStream().read(CONSUMER, StreamReadOptions.empty().count(count),
                StreamOffset.create(STREAM, ReadOffset.from(afterId))));
    }

    /** Next new entry for the scheduler, waiting up to block. Empty on timeout. */
    public List<Entry> readNext(Duration block) {
        return entries(redis.opsForStream().read(CONSUMER, StreamReadOptions.empty().count(1).block(block),
                StreamOffset.create(STREAM, ReadOffset.lastConsumed())));
    }

    public void ack(String id) {
        redis.opsForStream().acknowledge(STREAM, GROUP, id);
    }

    private XInfoGroup group() {
        return redis.opsForStream().groups(STREAM).stream()
                .filter(g -> GROUP.equals(g.groupName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Consumer group " + GROUP + " missing"));
    }

    private static List<Entry> entries(List<MapRecord<String, Object, Object>> records) {
        return records == null ? List.of() : records.stream().map(InputQueueRepository::entry).toList();
    }

    private static Entry entry(MapRecord<String, Object, Object> r) {
        Map<Object, Object> v = r.getValue();
        return new Entry(r.getId().getValue(), (String) v.get("user"), (String) v.get("cmd"),
                Integer.parseInt((String) v.getOrDefault("repeat", "1")), (String) v.get("mode"));
    }

    private static String statusKey(String id) {
        return "inputs:status:" + id;
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static String rootMessage(Throwable e) {
        while (e.getCause() != null) e = e.getCause();
        return e.getMessage();
    }
}
