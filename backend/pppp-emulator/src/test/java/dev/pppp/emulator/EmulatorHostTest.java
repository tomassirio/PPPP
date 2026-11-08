package dev.pppp.emulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EmulatorHostTest {
    @TempDir Path dir;
    final EmulatorHost host = new EmulatorHost();

    @AfterEach
    void off() {
        host.shutdown();
    }

    @Test
    void powerTransitions() throws Exception {
        assertThatThrownBy(host::powerOn).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> host.press(Button.A, 1, 1)).isInstanceOf(IllegalStateException.class);
        host.flushBattery(); // no-op when off

        host.insert("test", TestRom.write(dir), dir.resolve("test.sav"));
        host.powerOn();
        assertThat(host.status()).isEqualTo(new EmulatorHost.Status(EmulatorHost.Power.ON, "test", false, 1.0));
        assertThatThrownBy(host::powerOn).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(host::eject).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> host.insert("x", dir.resolve("test.gb"), null)).isInstanceOf(IllegalStateException.class);

        host.powerOff();
        assertThat(host.status().power()).isEqualTo(EmulatorHost.Power.OFF);
        assertThatThrownBy(host::powerOff).isInstanceOf(IllegalStateException.class);
        host.eject();
        assertThat(host.status().cartridgeId()).isNull();
    }

    @Test
    void pressSaveStateAndBattery() throws Exception {
        Path sav = dir.resolve("test.sav");
        host.insert("test", TestRom.write(dir), sav);
        host.powerOn();
        host.setSpeed(4.0);

        int before = host.readByte(0xC001);
        host.press(Button.A, 6, 6);
        assertThat(host.readByte(0xC001)).isEqualTo((before + 1) & 0xFF);
        host.press(Button.WAIT, 2, 2); // presses nothing
        assertThat(host.readByte(0xC001)).isEqualTo((before + 1) & 0xFF);

        byte[] state = host.saveState();
        host.press(Button.A, 6, 6);
        assertThat(host.readByte(0xC001)).isEqualTo((before + 2) & 0xFF);
        host.loadState(state);
        assertThat(host.readByte(0xC001)).isEqualTo((before + 1) & 0xFF);

        host.press(Button.A, 6, 6);
        int counter = host.readByte(0xA000);
        host.powerOff();
        assertThat(Files.readAllBytes(sav)[0] & 0xFF).isEqualTo(counter);

        host.powerOn(); // battery loaded on power-on
        host.press(Button.WAIT, 1, 0); // let the ROM enable cartridge RAM
        assertThat(host.readByte(0xA000)).isEqualTo(counter);
    }

    @Test
    void producesFramesUnlessPaused() throws Exception {
        var frames = new ArrayBlockingQueue<int[]>(100);
        host.onFrame(frames::offer);
        host.insert("test", TestRom.write(dir), dir.resolve("test.sav"));
        host.powerOn();
        int[] px = frames.poll(2, TimeUnit.SECONDS);
        assertThat(px).hasSize(160 * 144);

        host.pause();
        Thread.sleep(100); // let an in-flight frame land
        frames.clear();
        assertThat(frames.poll(200, TimeUnit.MILLISECONDS)).isNull();
        host.resume();
        assertThat(frames.poll(2, TimeUnit.SECONDS)).isNotNull();
    }
}
