package dev.pppp.input;

import dev.pppp.api.EventPublisher;
import dev.pppp.engine.Button;
import dev.pppp.engine.EmulatorHost;
import dev.pppp.settings.Settings;
import dev.pppp.settings.SettingsService;
import java.time.Duration;
import java.time.Instant;
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
 * group "engine" one entry at a time and presses each entry's steps back to back, so sequences never
 * interleave. Starts by re-reading its own unacked entries (crash recovery), then new ones.
 */
@Component
public class AnarchyScheduler implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(AnarchyScheduler.class);
    private static final Consumer CONSUMER = Consumer.from(InputService.GROUP, "scheduler");

    private final StringRedisTemplate redis;
    private final InputService inputs;
    private final EmulatorHost host;
    private final SettingsService settings;
    private final EventPublisher events;
    private volatile Thread thread;

    public AnarchyScheduler(StringRedisTemplate redis, InputService inputs, EmulatorHost host,
                            SettingsService settings, EventPublisher events) {
        this.redis = redis;
        this.inputs = inputs;
        this.host = host;
        this.settings = settings;
        this.events = events;
    }

    @Override
    public void start() {
        inputs.createGroup();
        thread = Thread.ofPlatform().name("anarchy-scheduler").daemon().start(this::loop);
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
        boolean recovering = true; // "0" = our own pending entries; ">" = new ones
        while (running()) {
            try {
                List<MapRecord<String, Object, Object>> records = recovering
                        ? redis.opsForStream().read(CONSUMER, StreamReadOptions.empty().count(10),
                                StreamOffset.create(InputService.STREAM, ReadOffset.from("0")))
                        : redis.opsForStream().read(CONSUMER, StreamReadOptions.empty().count(1).block(Duration.ofMillis(500)),
                                StreamOffset.create(InputService.STREAM, ReadOffset.lastConsumed()));
                if (records == null || records.isEmpty()) {
                    recovering = false;
                    continue;
                }
                for (var r : records) {
                    if (!running()) return;
                    process(r);
                }
            } catch (Exception e) {
                if (!running()) return;
                log.warn("Input loop error, retrying: {}", e.toString());
                recovering = true; // anything we read but did not ack gets picked up again
                sleep(500);
            }
        }
    }

    private void process(MapRecord<String, Object, Object> r) {
        String id = r.getId().getValue();
        String user = (String) r.getValue().get("user");
        String cmd = (String) r.getValue().get("cmd");
        while (running()) {
            String status = inputs.statusOf(id);
            if ("DROPPED".equals(status) || "EXECUTED".equals(status)) break; // flushed, or already ran
            EmulatorHost.Status console = host.status();
            if (console.power() != EmulatorHost.Power.ON) {
                inputs.setStatus(id, "DROPPED", null); // no stale backlog while the console is off
                break;
            }
            if (console.paused()) {
                sleep(100); // keep the entry; it runs on resume
                continue;
            }
            Settings.Input s = settings.input();
            try {
                for (Button b : CommandParser.deserialize(cmd)) host.press(b, s.holdFrames(), s.releaseFrames());
            } catch (RuntimeException e) {
                log.warn("Press failed for input {}: {}", id, e.toString());
                inputs.setStatus(id, "DROPPED", null);
                break;
            }
            inputs.setStatus(id, "EXECUTED", Instant.now());
            events.publish("INPUT_EXECUTED", Map.of("id", id, "user", user, "command", cmd));
            break;
        }
        if (running()) redis.opsForStream().acknowledge(InputService.STREAM, InputService.GROUP, id);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
