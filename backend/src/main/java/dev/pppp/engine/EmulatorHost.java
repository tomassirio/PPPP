package dev.pppp.engine;

import java.nio.file.Path;
import org.springframework.stereotype.Component;

/**
 * CONTRACT for the single authoritative emulator. Signatures are shared by every workstream;
 * the engine workstream replaces the bodies. Invalid console transitions throw
 * {@link IllegalStateException}, which the web layer maps to 409.
 */
@Component
public class EmulatorHost {

    public enum Power { ON, OFF }

    /** cartridgeId is null when no cartridge is inserted. */
    public record Status(Power power, String cartridgeId, boolean paused, double speed) {}

    /** Inserts a ROM. Console must be OFF. batterySave is where the cartridge's .sav lives (may not exist yet). */
    public void insert(String cartridgeId, Path rom, Path batterySave) {
        throw new UnsupportedOperationException("engine workstream");
    }

    /** Removes the cartridge. Console must be OFF. */
    public void eject() {
        throw new UnsupportedOperationException("engine workstream");
    }

    /** Needs a cartridge. */
    public void powerOn() {
        throw new UnsupportedOperationException("engine workstream");
    }

    /** Flushes the battery save, then stops the emulator thread. */
    public void powerOff() {
        throw new UnsupportedOperationException("engine workstream");
    }

    public void reset() {
        throw new UnsupportedOperationException("engine workstream");
    }

    public void pause() {
        throw new UnsupportedOperationException("engine workstream");
    }

    public void resume() {
        throw new UnsupportedOperationException("engine workstream");
    }

    /** 0.25 to 4.0. */
    public void setSpeed(double multiplier) {
        throw new UnsupportedOperationException("engine workstream");
    }

    public Status status() {
        throw new UnsupportedOperationException("engine workstream");
    }

    /** Holds the button for holdFrames, releases for releaseFrames, blocking the caller until done. Console must be ON. */
    public void press(Button button, int holdFrames, int releaseFrames) {
        throw new UnsupportedOperationException("engine workstream");
    }

    /** Snapshot taken on the emulator thread between frames. Console must be ON. */
    public byte[] saveState() {
        throw new UnsupportedOperationException("engine workstream");
    }

    /** Console must be ON with the same cartridge the state was taken from. */
    public void loadState(byte[] state) {
        throw new UnsupportedOperationException("engine workstream");
    }

    /** Writes the cartridge RAM to its .sav file. No-op when off or the cartridge has no battery. */
    public void flushBattery() {
        throw new UnsupportedOperationException("engine workstream");
    }
}
