package dev.pppp.engine;

import eu.rekawek.coffeegb.core.Gameboy;
import eu.rekawek.coffeegb.core.Gameboy.GameboyConfiguration;
import eu.rekawek.coffeegb.core.events.EventBus;
import eu.rekawek.coffeegb.core.events.EventBusImpl;
import eu.rekawek.coffeegb.core.gpu.Display;
import eu.rekawek.coffeegb.core.ir.InfraredEndpoint;
import eu.rekawek.coffeegb.core.memory.cart.Rom;
import eu.rekawek.coffeegb.core.memory.cart.battery.BatteryPersistenceFailedEvent;
import eu.rekawek.coffeegb.core.memory.cart.battery.BatteryStorage;
import eu.rekawek.coffeegb.core.serial.SerialEndpoint;
import eu.rekawek.coffeegb.core.state.bess.BessCodec;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * The single authoritative emulator (Coffee GB core). One dedicated thread owns the {@link Gameboy}
 * and runs it frame by frame; every other call is handed to that thread and runs between frames.
 * Invalid console transitions throw {@link IllegalStateException}, which the web layer maps to 409.
 */
@Component
public class EmulatorHost {

    public enum Power { ON, OFF }

    /** cartridgeId is null when no cartridge is inserted. */
    public record Status(Power power, String cartridgeId, boolean paused, double speed) {}

    private static final Logger log = LoggerFactory.getLogger(EmulatorHost.class);
    /** One LCD frame: 154 lines x 456 dots. 4194304 / 70224 = 59.73 fps. */
    private static final int TICKS_PER_FRAME = 70224;
    private static final long FRAME_NANOS = 1_000_000_000L * TICKS_PER_FRAME / Gameboy.TICKS_PER_SEC;
    private static final int PIXELS = Display.DISPLAY_WIDTH * Display.DISPLAY_HEIGHT;

    // Lifecycle fields are guarded by `this`; the machine fields belong to the emulator thread.
    private String cartridgeId;
    private Rom rom;
    private Path batterySave;
    private Thread thread;
    private volatile boolean paused;
    private volatile double speed = 1.0;

    private final BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();
    private final List<Consumer<int[]>> frameListeners = new CopyOnWriteArrayList<>();

    private Gameboy gb;
    private EventBusImpl bus;
    private boolean running;
    private long frame;
    private final int[] pixels = new int[PIXELS];
    private Press press;

    /** Inserts a ROM. Console must be OFF. batterySave is where the cartridge's .sav lives (may not exist yet). */
    public synchronized void insert(String cartridgeId, Path rom, Path batterySave) {
        requireOff("insert a cartridge");
        try {
            this.rom = new Rom(rom.toFile());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unreadable ROM: " + e.getMessage(), e);
        }
        try {
            // Coffee GB refuses battery paths with symlinked components (e.g. macOS /var), so resolve them.
            Path dir = Files.createDirectories(batterySave.toAbsolutePath().getParent()).toRealPath();
            this.batterySave = dir.resolve(batterySave.getFileName());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        this.cartridgeId = cartridgeId;
    }

    /** Removes the cartridge. Console must be OFF. */
    public synchronized void eject() {
        requireOff("eject the cartridge");
        cartridgeId = null;
        rom = null;
        batterySave = null;
    }

    /** Needs a cartridge. Loads the battery save, if any. */
    public synchronized void powerOn() {
        requireOff("power on");
        if (rom == null) throw new IllegalStateException("No cartridge inserted");
        paused = false;
        frame = 0;
        startMachine();
        running = true;
        thread = Thread.ofPlatform().name("emulator").daemon().start(this::loop);
    }

    /** Flushes the battery save, then stops the emulator thread. */
    public synchronized void powerOff() {
        requireOn();
        try {
            call(() -> {
                stopMachine();
                running = false;
                return null;
            });
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            thread = null;
            Runnable left;
            while ((left = tasks.poll()) != null) {
                if (left instanceof FutureTask<?> f) f.cancel(false);
            }
        }
    }

    /** Hard reset: flushes the battery and boots the cartridge again. */
    public void reset() {
        call(() -> {
            stopMachine();
            startMachine();
            return null;
        });
    }

    public synchronized void pause() {
        requireOn();
        paused = true;
    }

    public synchronized void resume() {
        requireOn();
        paused = false;
    }

    /** 0.25 to 4.0. */
    public void setSpeed(double multiplier) {
        if (!(multiplier >= 0.25 && multiplier <= 4.0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Speed must be between 0.25 and 4.0");
        }
        speed = multiplier;
    }

    public synchronized Status status() {
        return new Status(thread == null ? Power.OFF : Power.ON, cartridgeId, paused, speed);
    }

    /** Holds the button for holdFrames, releases for releaseFrames, blocking the caller until done. Console must be ON. */
    public void press(Button button, int holdFrames, int releaseFrames) {
        if (holdFrames < 0 || releaseFrames < 0) throw new IllegalArgumentException("Negative frame count");
        Press p = new Press(button == Button.WAIT ? null : eu.rekawek.coffeegb.core.joypad.Button.valueOf(button.name()),
                holdFrames, releaseFrames);
        call(() -> {
            if (press != null) throw new IllegalStateException("Another press is in progress");
            press = p;
            return null;
        });
        await(p.done);
    }

    /** Snapshot (BESS format) taken on the emulator thread between frames. Console must be ON. */
    public byte[] saveState() {
        return call(() -> {
            Gameboy copy = bessWorker();
            try {
                copy.restoreState(gb.captureState());
                return BessCodec.write(copy.captureBessState());
            } finally {
                copy.discardUnstarted();
            }
        });
    }

    /** Console must be ON with the same cartridge the state was taken from. */
    public void loadState(byte[] state) {
        call(() -> {
            Gameboy copy = bessWorker();
            try {
                copy.restoreBessState(BessCodec.read(state));
                gb.restoreState(copy.captureState());
            } catch (IOException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Corrupt save state: " + e.getMessage(), e);
            } catch (IllegalArgumentException e) { // e.g. "BESS state belongs to a different ROM"
                throw new IllegalStateException("Save state does not fit this console: " + e.getMessage(), e);
            } finally {
                copy.discardUnstarted();
            }
            return null;
        });
    }

    /** Writes the cartridge RAM to its .sav file. No-op when off or the cartridge has no battery. */
    public void flushBattery() {
        try {
            call(() -> {
                gb.flushCartridge();
                return null;
            });
        } catch (IllegalStateException off) {
            // no-op when off
        }
    }

    /**
     * Reads one byte of the bus (ROM, VRAM, cartridge RAM, WRAM, OAM, HRAM) between frames.
     * I/O registers are refused because some reads have side effects. Console must be ON.
     */
    public int readByte(int address) {
        if (address < 0 || address > 0xFFFE || (address >= 0xFF00 && address < 0xFF80)) {
            throw new IllegalArgumentException("Address not readable: " + Integer.toHexString(address));
        }
        // ponytail: one queue round trip per byte; add a batch read when RAM watchers poll many bytes.
        return call(() -> gb.getAddressSpace().getByte(address) & 0xFF);
    }

    /**
     * Registers a listener for every second emulated frame: 160x144 pixels as 0xRRGGBB, a fresh array
     * each call. Runs on the emulator thread, so it must hand off and return immediately.
     */
    public void onFrame(Consumer<int[]> listener) {
        frameListeners.add(listener);
    }

    @PreDestroy
    public synchronized void shutdown() {
        if (thread != null) powerOff();
    }

    // ---- emulator thread ----

    private void loop() {
        long deadline = System.nanoTime();
        while (running) {
            try {
                Runnable task;
                while (running && (task = tasks.poll()) != null) task.run();
                if (!running) break;
                if (paused) {
                    task = tasks.poll(20, TimeUnit.MILLISECONDS);
                    if (task != null) task.run();
                    deadline = System.nanoTime();
                    continue;
                }
                step();
                deadline += (long) (FRAME_NANOS / speed);
                long wait = deadline - System.nanoTime();
                if (wait > 0) LockSupport.parkNanos(wait);
                else if (wait < -100_000_000L) deadline = System.nanoTime(); // too far behind: don't catch up in a burst
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                log.error("Emulator frame failed", e);
            }
        }
    }

    private void step() {
        Press p = press;
        if (p != null && p.frames == 0 && p.button != null && p.hold > 0) gb.setPressedButtons(Set.of(p.button));
        gb.runTicks(TICKS_PER_FRAME);
        frame++;
        if (frame % 2 == 0 && !frameListeners.isEmpty()) {
            for (Consumer<int[]> l : frameListeners) {
                try {
                    l.accept(pixels.clone());
                } catch (RuntimeException e) {
                    log.warn("Frame listener failed", e);
                }
            }
        }
        if (p != null) {
            p.frames++;
            if (p.frames == p.hold) gb.setPressedButtons(Set.of());
            if (p.frames >= p.hold + p.release) {
                press = null;
                p.done.complete(null);
            }
        }
    }

    private void startMachine() {
        bus = new EventBusImpl(null, "pppp", false);
        bus.register(e -> e.toRgb(pixels, false), Display.DmgFrameReadyEvent.class);
        bus.register(e -> e.toRgb(pixels), Display.GbcFrameReadyEvent.class);
        bus.register(e -> log.error("Battery {} failed for {}: {}",e.operation(), e.fileName(), e.message()),
                BatteryPersistenceFailedEvent.class);
        gb = config().build();
        gb.init(bus, SerialEndpoint.NULL_ENDPOINT, InfraredEndpoint.NULL_ENDPOINT, null);
    }

    /** Flushes the battery and releases the machine; fails a running press so its caller wakes up. */
    private void stopMachine() {
        if (press != null) {
            press.done.completeExceptionally(new IllegalStateException("Console was reset or powered off"));
            press = null;
        }
        gb.close();
        bus.close();
    }

    private GameboyConfiguration config() {
        return new GameboyConfiguration(rom)
                .setExecutionMode(eu.rekawek.coffeegb.core.ExecutionMode.PERFORMANCE)
                .setBatteryStorage(BatteryStorage.direct(batterySave), null);
    }

    /** An isolated machine for BESS conversion; it never touches the battery file. */
    private Gameboy bessWorker() {
        Gameboy copy = config().forBessTransfer(gb).build();
        copy.init(EventBus.NULL_EVENT_BUS, SerialEndpoint.NULL_ENDPOINT, InfraredEndpoint.NULL_ENDPOINT, null);
        return copy;
    }

    // ---- plumbing ----

    private static final class Press {
        final eu.rekawek.coffeegb.core.joypad.Button button;
        final int hold, release;
        final CompletableFuture<Void> done = new CompletableFuture<>();
        int frames;

        Press(eu.rekawek.coffeegb.core.joypad.Button button, int hold, int release) {
            this.button = button;
            this.hold = hold;
            this.release = release;
            if (hold + release == 0) done.complete(null);
        }
    }

    /** Runs the task on the emulator thread between frames and waits for it. Console must be ON. */
    private <T> T call(Callable<T> task) {
        FutureTask<T> f = new FutureTask<>(task);
        synchronized (this) {
            requireOn();
            if (Thread.currentThread() == thread) {
                f.run();
            } else {
                tasks.add(f);
            }
        }
        return await(f);
    }

    private static <T> T await(java.util.concurrent.Future<T> f) {
        try {
            return f.get();
        } catch (CancellationException e) {
            throw new IllegalStateException("Console was powered off");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw new IllegalStateException(e.getCause());
        }
    }

    private void requireOn() {
        if (thread == null) throw new IllegalStateException("Console is off");
    }

    private void requireOff(String action) {
        if (thread != null) throw new IllegalStateException("Power off the console to " + action);
    }
}
