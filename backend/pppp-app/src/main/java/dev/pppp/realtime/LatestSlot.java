package dev.pppp.realtime;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Hands values to a consumer on a virtual thread, keeping only the newest one: if the consumer is
 * still busy, intermediate values are dropped. offer() never blocks.
 */
final class LatestSlot<T> {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LatestSlot.class);
    private final AtomicReference<T> slot = new AtomicReference<>();
    private final AtomicBoolean draining = new AtomicBoolean();
    private final Consumer<T> consumer;

    LatestSlot(Consumer<T> consumer) {
        this.consumer = consumer;
    }

    void offer(T value) {
        slot.set(value);
        if (draining.compareAndSet(false, true)) Thread.startVirtualThread(this::drain);
    }

    private void drain() {
        do {
            T value;
            while ((value = slot.getAndSet(null)) != null) {
                try {
                    consumer.accept(value);
                } catch (RuntimeException e) {
                    log.warn("Dropped a value", e);
                }
            }
            draining.set(false);
        } while (slot.get() != null && draining.compareAndSet(false, true));
    }
}
