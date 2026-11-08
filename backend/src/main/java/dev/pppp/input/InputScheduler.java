package dev.pppp.input;

import dev.pppp.api.EventPublisher;
import dev.pppp.engine.Button;
import dev.pppp.engine.EmulatorHost;
import dev.pppp.settings.Settings;
import dev.pppp.settings.SettingsService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * The single owner of button presses. Runs on its own thread, reads inputs:stream through consumer
 * group "engine" one entry at a time. Starts by re-reading its own unacked entries (crash recovery), then new ones.
 *
 * <p>Anarchy entries (tagged mode=ANARCHY at submit) run as sent, steps back to back.
 *
 * <p>Democracy entries are votes for their button with their repeat. Window n (n = id of its first vote) opens on
 * the first vote and closes when one button has a strict majority of active players, every active player has
 * voted, or democracyWindowSeconds have passed. Most votes wins, ties go to the button voted first; it presses
 * the median of its voters' repeats (lower middle for an even count). Winners EXECUTED, the rest OUTVOTED.
 * A user's second vote in a window is OUTVOTED right away. Votes stay unacked until their window resolves, so a
 * restart re-reads and re-tallies them. An anarchy entry, or the mode switching to ANARCHY, resolves the open
 * window at once with the votes it has.
 */
@Component
public class InputScheduler implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(InputScheduler.class);
    private static final Consumer CONSUMER = Consumer.from(InputService.GROUP, "scheduler");

    public record Tally(String window, Map<Button, Long> tally, long endsAt) {}
    private record Vote(String id, String user, Button button, int repeat) {}
    private record Window(String n, long endsAt, List<Vote> votes) {}

    private final StringRedisTemplate redis;
    private final InputService inputs;
    private final EmulatorHost host;
    private final SettingsService settings;
    private final EventPublisher events;
    private final ModeService modes;
    private volatile Thread thread;
    private Window window; // scheduler thread only
    private volatile Tally tally;

    public InputScheduler(StringRedisTemplate redis, InputService inputs, EmulatorHost host,
                          SettingsService settings, EventPublisher events, ModeService modes) {
        this.redis = redis;
        this.inputs = inputs;
        this.host = host;
        this.settings = settings;
        this.events = events;
        this.modes = modes;
    }

    /** The open democracy window, or null. */
    public Tally tally() {
        return tally;
    }

    @Override
    public void start() {
        inputs.createGroup();
        thread = Thread.ofPlatform().name("input-scheduler").daemon().start(this::loop);
    }

    @Override
    public void stop() {
        Thread t = thread;
        thread = null;
        if (t != null) {
            t.interrupt();
            try {
                t.join(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return thread != null;
    }

    private boolean running() {
        return thread == Thread.currentThread();
    }

    private void loop() {
        String recoverFrom = "0"; // our own pending entries from this id on; null = only new ones
        while (running()) {
            try {
                if (window != null && (System.currentTimeMillis() >= window.endsAt()
                        || !ModeService.DEMOCRACY.equals(modes.current()))) {
                    resolve();
                }
                List<MapRecord<String, Object, Object>> records = recoverFrom != null
                        ? redis.opsForStream().read(CONSUMER, StreamReadOptions.empty().count(10),
                                StreamOffset.create(InputService.STREAM, ReadOffset.from(recoverFrom)))
                        : redis.opsForStream().read(CONSUMER,
                                StreamReadOptions.empty().count(1).block(Duration.ofMillis(window != null ? 100 : 500)),
                                StreamOffset.create(InputService.STREAM, ReadOffset.lastConsumed()));
                if (records == null || records.isEmpty()) {
                    recoverFrom = null;
                    continue;
                }
                for (var r : records) {
                    if (!running()) return;
                    if (recoverFrom != null) recoverFrom = r.getId().getValue();
                    process(r);
                }
            } catch (Exception e) {
                if (!running()) return;
                log.warn("Input loop error, retrying: {}", e.toString());
                recoverFrom = "0"; // anything we read but did not ack gets picked up again, votes re-tallied
                window = null;
                tally = null;
                sleep(500);
            }
        }
    }

    private void process(MapRecord<String, Object, Object> r) {
        String id = r.getId().getValue();
        String status = inputs.statusOf(id);
        if (status != null && !"QUEUED".equals(status)) { // flushed, or already ran
            ack(id);
            return;
        }
        if (ModeService.DEMOCRACY.equals(r.getValue().get("mode"))) {
            vote(r);
        } else {
            if (window != null) resolve();
            press(r);
        }
    }

    private void press(MapRecord<String, Object, Object> r) {
        String id = r.getId().getValue();
        String user = (String) r.getValue().get("user");
        String cmd = (String) r.getValue().get("cmd");
        while (running()) {
            if ("DROPPED".equals(inputs.statusOf(id))) break; // flushed while paused
            EmulatorHost.Status console = host.status();
            if (console.power() != EmulatorHost.Power.ON) {
                inputs.setStatus(id, "DROPPED", null); // no stale backlog while the console is off
                break;
            }
            if (console.paused()) {
                sleep(100); // keep the entry; it runs on resume
                continue;
            }
            if (pressAll(CommandParser.deserialize(cmd), id)) {
                inputs.setStatus(id, "EXECUTED", Instant.now());
                events.publish("INPUT_EXECUTED", Map.of("id", id, "user", user, "command", cmd));
            } else {
                inputs.setStatus(id, "DROPPED", null);
            }
            break;
        }
        if (running()) ack(id);
    }

    private void vote(MapRecord<String, Object, Object> r) {
        String id = r.getId().getValue();
        if (host.status().power() != EmulatorHost.Power.ON) {
            inputs.setStatus(id, "DROPPED", null);
            ack(id);
            return;
        }
        String user = (String) r.getValue().get("user");
        Button button = CommandParser.deserialize((String) r.getValue().get("cmd")).getFirst();
        int repeat = Integer.parseInt((String) r.getValue().getOrDefault("repeat", "1"));
        if (window == null) {
            window = new Window(id, System.currentTimeMillis() + settings.input().democracyWindowSeconds() * 1000L,
                    new ArrayList<>());
            redis.delete(List.of(windowKey(), votersKey())); // leftovers of a crashed run of this same window
        }
        if (redis.opsForSet().add(votersKey(), user) == 0) { // one vote per user per window
            inputs.setStatus(id, "OUTVOTED", null);
            ack(id);
            return;
        }
        window.votes().add(new Vote(id, user, button, repeat));
        redis.opsForZSet().incrementScore(windowKey(), button.name(), 1);

        Map<Button, Long> counts = new LinkedHashMap<>();
        window.votes().forEach(v -> counts.merge(v.button(), 1L, Long::sum));
        tally = new Tally(window.n(), Collections.unmodifiableMap(counts), window.endsAt());
        events.publish("VOTE_TALLY", Map.of("window", tally.window(), "tally", tally.tally(), "endsAt", tally.endsAt()));

        long active = modes.activePlayers().size();
        long top = Collections.max(counts.values());
        if (top * 2 > active || window.votes().size() >= active) resolve();
    }

    private void resolve() {
        Window w = window;
        window = null;
        tally = null;
        // Votes flushed by an admin while the window was open no longer count.
        List<Vote> live = w.votes().stream().filter(v -> !"DROPPED".equals(inputs.statusOf(v.id()))).toList();
        Map<Button, List<Vote>> byButton = new LinkedHashMap<>(); // insertion order = first vote order
        live.forEach(v -> byButton.computeIfAbsent(v.button(), b -> new ArrayList<>()).add(v));
        List<Vote> winners = List.of();
        for (var votes : byButton.values()) if (votes.size() > winners.size()) winners = votes; // strict: earliest wins ties

        if (!winners.isEmpty()) {
            int[] repeats = winners.stream().mapToInt(Vote::repeat).sorted().toArray();
            int times = repeats[(repeats.length - 1) / 2]; // median, lower middle for an even count
            Vote first = winners.getFirst();
            boolean ok = awaitConsole() && pressAll(Collections.nCopies(times, first.button()), w.n());
            Instant at = Instant.now();
            for (Vote v : live) {
                inputs.setStatus(v.id(), !ok ? "DROPPED" : winners.contains(v) ? "EXECUTED" : "OUTVOTED", ok ? at : null);
            }
            if (ok) {
                events.publish("INPUT_EXECUTED", Map.of("id", first.id(), "user", first.user(),
                        "command", CommandParser.serialize(Collections.nCopies(times, first.button())), "window", w.n()));
            }
        }
        if (!running()) return; // stopping mid-press: leave them pending, a restart re-tallies
        w.votes().forEach(v -> ack(v.id()));
        redis.delete(List.of(windowKey(w.n()), votersKey(w.n())));
    }

    /** Waits out a pause. False when the console is off (or we are stopping). */
    private boolean awaitConsole() {
        while (running()) {
            EmulatorHost.Status console = host.status();
            if (console.power() != EmulatorHost.Power.ON) return false;
            if (!console.paused()) return true;
            sleep(100);
        }
        return false;
    }

    private boolean pressAll(List<Button> steps, String id) {
        Settings.Input s = settings.input();
        try {
            for (Button b : steps) host.press(b, s.holdFrames(), s.releaseFrames());
            return true;
        } catch (RuntimeException e) {
            log.warn("Press failed for input {}: {}", id, e.toString());
            return false;
        }
    }

    private void ack(String id) {
        redis.opsForStream().acknowledge(InputService.STREAM, InputService.GROUP, id);
    }

    private String windowKey() { return windowKey(window.n()); }
    private String votersKey() { return votersKey(window.n()); }
    private static String windowKey(String n) { return "vote:window:" + n; }
    private static String votersKey(String n) { return "vote:voters:" + n; }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
