package dev.pppp.emulator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A 32 KB MBC1+RAM+BATTERY ROM. It polls the A button; on every press it increments $C001 and
 * copies the counter to cartridge RAM $A000 (so it lands in the .sav). $C000 mirrors A held.
 * Channel 1 plays a steady 512 Hz square wave on both speakers.
 */
public final class TestRom {
    private TestRom() {}

    private static final int[] PROGRAM = {
            0x3E, 0x80, 0xE0, 0x26, // NR52 = $80     APU on
            0x3E, 0x77, 0xE0, 0x24, // NR50 = $77     full master volume
            0x3E, 0xFF, 0xE0, 0x25, // NR51 = $FF     all channels to both sides
            0x3E, 0x80, 0xE0, 0x11, // NR11 = $80     50% duty, no length
            0x3E, 0xF0, 0xE0, 0x12, // NR12 = $F0     volume 15, no envelope
            0x3E, 0x00, 0xE0, 0x13, // NR13 = $00
            0x3E, 0x87, 0xE0, 0x14, // NR14 = $87     trigger, freq $700 = 512 Hz
            0x3E, 0x0A,             // LD A,$0A
            0xEA, 0x00, 0x00,       // LD ($0000),A   enable cartridge RAM
            0x06, 0x00,             // LD B,0         previous A state
            0x3E, 0x10,             // loop: LD A,$10 select action buttons
            0xE0, 0x00,             // LDH ($00),A
            0xF0, 0x00,             // LDH A,($00)
            0xF0, 0x00,             // LDH A,($00)
            0x2F,                   // CPL
            0xE6, 0x01,             // AND $01        A button
            0xEA, 0x00, 0xC0,       // LD ($C000),A
            0xB8,                   // CP B
            0x28, 0xEF,             // JR Z,loop
            0x47,                   // LD B,A
            0xB7,                   // OR A
            0x28, 0xEB,             // JR Z,loop      released
            0xFA, 0x01, 0xC0,       // LD A,($C001)
            0x3C,                   // INC A
            0xEA, 0x01, 0xC0,       // LD ($C001),A
            0xEA, 0x00, 0xA0,       // LD ($A000),A
            0x18, 0xDF,             // JR loop
    };

    public static Path write(Path dir) throws IOException {
        byte[] rom = new byte[0x8000];
        rom[0x100] = 0x00;                          // NOP
        rom[0x101] = (byte) 0xC3;                   // JP $0150
        rom[0x102] = 0x50;
        rom[0x103] = 0x01;
        byte[] title = "PPPPTEST".getBytes();
        System.arraycopy(title, 0, rom, 0x134, title.length);
        rom[0x147] = 0x03;                          // MBC1+RAM+BATTERY
        rom[0x149] = 0x02;                          // 8 KB RAM
        int sum = 0;
        for (int a = 0x134; a <= 0x14C; a++) sum = sum - rom[a] - 1;
        rom[0x14D] = (byte) sum;
        for (int i = 0; i < PROGRAM.length; i++) rom[0x150 + i] = (byte) PROGRAM[i];
        Path file = dir.resolve("test.gb");
        Files.write(file, rom);
        return file;
    }
}
