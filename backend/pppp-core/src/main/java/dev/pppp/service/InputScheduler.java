package dev.pppp.service;

import dev.pppp.emulator.Button;
import dev.pppp.emulator.EmulatorHost;
import dev.pppp.model.LiveEvent;
import dev.pppp.model.Settings;
import dev.pppp.model.VoteTally;
import dev.pppp.repository.InputQueueRepository;
import dev.pppp.repository.InputQueueRepository.Entry;
import dev.pppp.repository.VoteRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * The single owner of button presses. Runs on its own thread, reads the input queue one entry at a time. Starts by re-reading its own unacked entries (crash recovery), then new ones.
 *
 * <p>Anarchy entries (tagged mode=ANARCHY at submit) run as sent, steps back to back.
 *
 * <p>Democracy entries are votes for their button with their repeat. Window n (n = id of its first vote) opens on
 * the first vote and closes when one button has a strict majority of active players, every active player has
 * voted, or democracyWindowSeconds have passed. Most votes wins, a tie is broken at random; it presses
 * the median of its voters' repeats (lower middle for an even count). Winners EXECUTED, the rest OUTVOTED.
 * A user's second vote in a window is OUTVOTED right away. Votes stay unacked until their window resolves, so a
 * restart re-reads and re-tallies them. An anarchy entry, or the mode switching to ANARCHY, resolves the open
 * window at once with the votes it has.
 */
@Component
public class InputScheduler implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(InputScheduler.class);
    private record Vote(String id, String user, Button button, int repeat) {}
    private record Window(String n, long endsAt, List<Vote> votes) {}

    private final InputQueueRepository queue;
    private final VoteRepository votes;
    private final EmulatorHost host;
    private final SettingsService settings;
    private final ApplicationEventPublisher events;
    private final ModeService modes;
    private volatile Thread thread;
    private Window window; // scheduler thread only
    private volatile VoteTally tally;

    public InputScheduler(InputQueueRepository queue, VoteRepository votes, EmulatorHost host,
                          SettingsService settings, ApplicationEventPublisher events, ModeService modes) {
        this.queue = queue;
        this.votes = votes;
        this.host = host;
        this.settings = settings;
        this.events = events;
        this.modes = modes;
    }

    /** The open democracy window, or null. */
    public VoteTally tally() {
        return tally;
    }

    @Override
    public void start() {
        queue.createGroup();
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
                List<Entry> records = recoverFrom != null
                        ? queue.readOwnPending(recoverFrom, 10)
                        : queue.readNext(Duration.ofMillis(window != null ? 100 : 500));
                if (records.isEmpty()) {
                    recoverFrom = null;
                    continue;
                }
                for (var r : records) {
                    if (!running()) return;
                    if (recoverFrom != null) recoverFrom = r.id();
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

    private void process(Entry r) {
        String id = r.id();
        String status = queue.status(id);
        if (status != null && !"QUEUED".equals(status)) { // flushed, or already ran
            ack(id);
            return;
        }
        if (ModeService.DEMOCRACY.equals(r.mode())) {
            vote(r);
        } else {
            if (window != null) resolve();
            press(r);
        }
    }

    private void press(Entry r) {
        String id = r.id();
        String user = r.user();
        String cmd = r.cmd();
        while (running()) {
            if ("DROPPED".equals(queue.status(id))) break; // flushed while paused
            EmulatorHost.Status console = host.status();
            if (console.power() != EmulatorHost.Power.ON) {
                queue.setStatus(id, "DROPPED", null); // no stale backlog while the console is off
                break;
            }
            if (console.paused()) {
                sleep(100); // keep the entry; it runs on resume
                continue;
            }
            if (pressAll(CommandParser.deserialize(cmd), id)) {
                queue.setStatus(id, "EXECUTED", Instant.now());
                events.publishEvent(new LiveEvent("INPUT_EXECUTED", Map.of("id", id, "user", user, "command", cmd)));
            } else {
                queue.setStatus(id, "DROPPED", null);
            }
            break;
        }
        if (running()) ack(id);
    }

    private void vote(Entry r) {
        String id = r.id();
        if (host.status().power() != EmulatorHost.Power.ON) {
            queue.setStatus(id, "DROPPED", null);
            ack(id);
            return;
        }
        String user = r.user();
        Button button = CommandParser.deserialize(r.cmd()).getFirst();
        int repeat = r.repeat();
        if (window == null) {
            window = new Window(id, System.currentTimeMillis() + settings.input().democracyWindowSeconds() * 1000L,
                    new ArrayList<>());
            votes.deleteWindow(id); // leftovers of a crashed run of this same window
        }
        if (!votes.addVoter(window.n(), user)) { // one vote per user per window
            queue.setStatus(id, "OUTVOTED", null);
            ack(id);
            return;
        }
        window.votes().add(new Vote(id, user, button, repeat));
        votes.countVote(window.n(), button);

        Map<Button, Long> counts = new LinkedHashMap<>();
        window.votes().forEach(v -> counts.merge(v.button(), 1L, Long::sum));
        tally = new VoteTally(window.n(), Collections.unmodifiableMap(counts), window.endsAt());
        events.publishEvent(new LiveEvent("VOTE_TALLY", Map.of("window", tally.window(), "tally", tally.tally(), "endsAt", tally.endsAt())));

        long active = modes.activePlayers().size();
        long top = Collections.max(counts.values());
        if (top * 2 > active || window.votes().size() >= active) resolve();
    }

    private void resolve() {
        Window w = window;
        window = null;
        tally = null;
        // Votes flushed by an admin while the window was open no longer count.
        List<Vote> live = w.votes().stream().filter(v -> !"DROPPED".equals(queue.status(v.id()))).toList();
        Map<Button, List<Vote>> byButton = new LinkedHashMap<>(); // insertion order = first vote order
        live.forEach(v -> byButton.computeIfAbsent(v.button(), b -> new ArrayList<>()).add(v));
        List<Vote> winners = List.of();
        int most = byButton.values().stream().mapToInt(List::size).max().orElse(0);
        List<List<Vote>> tied = byButton.values().stream().filter(v -> v.size() == most).toList();
        if (!tied.isEmpty()) winners = tied.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(tied.size())); // tie: random

        if (!winners.isEmpty()) {
            int[] repeats = winners.stream().mapToInt(Vote::repeat).sorted().toArray();
            int times = repeats[(repeats.length - 1) / 2]; // median, lower middle for an even count
            Vote first = winners.getFirst();
            boolean ok = awaitConsole() && pressAll(Collections.nCopies(times, first.button()), w.n());
            Instant at = Instant.now();
            for (Vote v : live) {
                queue.setStatus(v.id(), !ok ? "DROPPED" : winners.contains(v) ? "EXECUTED" : "OUTVOTED", ok ? at : null);
            }
            if (ok) {
                events.publishEvent(new LiveEvent("INPUT_EXECUTED", Map.of("id", first.id(), "user", first.user(),
                        "command", CommandParser.serialize(Collections.nCopies(times, first.button())), "window", w.n())));
            }
        }
        if (!running()) return; // stopping mid-press: leave them pending, a restart re-tallies
        w.votes().forEach(v -> ack(v.id()));
        votes.deleteWindow(w.n());
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
        queue.ack(id);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
