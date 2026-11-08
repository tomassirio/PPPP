package dev.pppp.service;

import dev.pppp.emulator.Button;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamInfo.XInfoGroup;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * The input queue: inputs:stream entries {user, cmd, repeat, mode} consumed by group "engine", plus an
 * inputs:status:{id} hash per entry. "Queued" = delivered-but-unacked (being executed) + not yet delivered.
 * Used by InputController and ChatCommandBridge.
 * Throws ResponseStatusException: 400 bad command, 429 rate limited, 503 queue full (with Retry-After).
 */
@Service
public class InputService {
    public static final String STREAM = "inputs:stream";
    public static final String GROUP = "engine";
    static final long STREAM_MAXLEN = 500;
    private static final Duration STATUS_TTL = Duration.ofMinutes(10);

    public record Accepted(String id, String mode) {}
    public record Status(String id, String status, String executedAt, Long position) {}
    public record Queued(String id, String user, String command) {}

    private final StringRedisTemplate redis;
    private final RateLimiter rateLimiter;
    private final ModeService modes;
    private final SettingsService settings;

    public InputService(StringRedisTemplate redis, RateLimiter rateLimiter, ModeService modes, SettingsService settings) {
        this.redis = redis;
        this.rateLimiter = rateLimiter;
        this.modes = modes;
        this.settings = settings;
    }

    public Accepted submit(String userId, String command, int repeat) {
        rateLimiter.check(userId);
        String mode = modes.current();
        List<Button> steps = CommandParser.parse(command, repeat, mode);
        // ponytail: check-then-add, concurrent submits can overshoot the cap by a few; fine for 18 players.
        if (depth() >= settings.input().maxQueueLength()) throw queueFull();

        boolean single = steps.stream().distinct().count() == 1;
        var id = redis.opsForStream().add(STREAM,
                Map.of("user", userId,
                        "cmd", CommandParser.serialize(steps),
                        "repeat", String.valueOf(single ? steps.size() : 1),
                        "mode", mode),
                XAddOptions.maxlen(STREAM_MAXLEN).approximateTrimming(true)).getValue();
        setStatus(id, "QUEUED", null);
        modes.markActive(userId);
        return new Accepted(id, mode);
    }

    /** Null when unknown or expired. position: 0 = executing now, 1 = next, ... (only while QUEUED). */
    public Status status(String id) {
        Map<Object, Object> h = redis.opsForHash().entries(statusKey(id));
        if (h.isEmpty()) return null;
        String status = (String) h.get("status");
        Long position = null;
        if ("QUEUED".equals(status)) {
            // ponytail: O(queue) XRANGE per call, queue is capped at <= 500.
            position = (long) redis.opsForStream().range(STREAM,
                    Range.of(Range.Bound.exclusive(group().lastDeliveredId()), Range.Bound.inclusive(id)),
                    Limit.limit().count((int) STREAM_MAXLEN)).size();
        }
        return new Status(id, status, (String) h.get("executedAt"), position);
    }

    /** Not yet delivered to the scheduler, oldest first. */
    public List<Queued> upcoming(int limit) {
        return undelivered(group(), limit).stream()
                .map(r -> new Queued(r.getId().getValue(), (String) r.getValue().get("user"), (String) r.getValue().get("cmd")))
                .toList();
    }

    /** Pending (being executed) + not yet delivered. */
    public long depth() {
        // ponytail: XRANGE after last-delivered id, O(queue) <= 500; XINFO GROUPS "lag" is O(1) if this ever matters.
        XInfoGroup g = group();
        return g.pendingCount() + undelivered(g, (int) STREAM_MAXLEN).size();
    }

    /** Marks every queued input DROPPED and moves the group past them. Returns how many were dropped. */
    public int flush() {
        XInfoGroup g = group();
        List<String> ids = new ArrayList<>();
        redis.opsForStream().pending(STREAM, GROUP, Range.unbounded(), STREAM_MAXLEN)
                .forEach(p -> ids.add(p.getIdAsString()));
        var undelivered = undelivered(g, (int) STREAM_MAXLEN);
        undelivered.forEach(r -> ids.add(r.getId().getValue()));
        ids.forEach(id -> setStatus(id, "DROPPED", null));
        if (!undelivered.isEmpty()) {
            String last = undelivered.getLast().getId().getValue();
            redis.execute((RedisCallback<Object>) c -> c.execute("XGROUP",
                    bytes("SETID"), bytes(STREAM), bytes(GROUP), bytes(last)));
        }
        // Pending ones are acked by the scheduler when it sees DROPPED.
        return ids.size();
    }

    void setStatus(String id, String status, Instant executedAt) {
        String key = statusKey(id);
        redis.opsForHash().put(key, "status", status);
        if (executedAt != null) redis.opsForHash().put(key, "executedAt", executedAt.toString());
        redis.expire(key, STATUS_TTL);
    }

    String statusOf(String id) {
        return (String) redis.opsForHash().get(statusKey(id), "status");
    }

    /** XGROUP CREATE inputs:stream engine 0 MKSTREAM; no-op when it already exists. */
    void createGroup() {
        try {
            redis.opsForStream().createGroup(STREAM, ReadOffset.from("0"), GROUP);
        } catch (Exception e) {
            if (!String.valueOf(rootMessage(e)).contains("BUSYGROUP")) throw e;
        }
    }

    private List<MapRecord<String, Object, Object>> undelivered(XInfoGroup g, int limit) {
        return redis.opsForStream().range(STREAM,
                Range.of(Range.Bound.exclusive(g.lastDeliveredId()), Range.Bound.unbounded()),
                Limit.limit().count(limit));
    }

    private XInfoGroup group() {
        return redis.opsForStream().groups(STREAM).stream()
                .filter(g -> GROUP.equals(g.groupName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Consumer group " + GROUP + " missing"));
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

    private static ResponseStatusException queueFull() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Queue is full") {
            @Override
            public HttpHeaders getHeaders() {
                var h = new HttpHeaders();
                h.set(HttpHeaders.RETRY_AFTER, "1"); // ponytail: fixed 1 s; derive from depth x hold frames if clients hammer.
                return h;
            }
        };
    }
}
