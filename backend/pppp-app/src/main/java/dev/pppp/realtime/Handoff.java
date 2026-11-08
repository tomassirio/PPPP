package dev.pppp.realtime;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Hands values to a consumer on a virtual thread, keeping at most `capacity` pending: when the
 * consumer falls behind, the oldest pending values are dropped. offer() never blocks.
 */
final class Handoff<T> {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Handoff.class);
    private final ArrayBlockingQueue<T> queue;
    private final AtomicBoolean draining = new AtomicBoolean();
    private final Consumer<T> consumer;

    Handoff(int capacity, Consumer<T> consumer) {
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.consumer = consumer;
    }

    void offer(T value) {
        while (!queue.offer(value)) queue.poll();
        if (draining.compareAndSet(false, true)) Thread.startVirtualThread(this::drain);
    }

    private void drain() {
        do {
            T value;
            while ((value = queue.poll()) != null) {
                try {
                    consumer.accept(value);
                } catch (RuntimeException e) {
                    log.warn("Dropped a value", e);
                }
            }
            draining.set(false);
        } while (!queue.isEmpty() && draining.compareAndSet(false, true));
    }
}
