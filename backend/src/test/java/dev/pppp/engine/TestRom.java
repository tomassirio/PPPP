package dev.pppp.engine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A 32 KB MBC1+RAM+BATTERY ROM. It polls the A button; on every press it increments $C001 and
 * copies the counter to cartridge RAM $A000 (so it lands in the .sav). $C000 mirrors A held.
 */
public final class TestRom {
    private TestRom() {}

    private static final int[] PROGRAM = {
            0x3E, 0x0A,             // 0150 LD A,$0A
            0xEA, 0x00, 0x00,       // 0152 LD ($0000),A   enable cartridge RAM
            0x06, 0x00,             // 0155 LD B,0         previous A state
            0x3E, 0x10,             // 0157 loop: LD A,$10 select action buttons
            0xE0, 0x00,             // 0159 LDH ($00),A
            0xF0, 0x00,             // 015B LDH A,($00)
            0xF0, 0x00,             // 015D LDH A,($00)
            0x2F,                   // 015F CPL
            0xE6, 0x01,             // 0160 AND $01        A button
            0xEA, 0x00, 0xC0,       // 0162 LD ($C000),A
            0xB8,                   // 0165 CP B
            0x28, 0xEF,             // 0166 JR Z,loop
            0x47,                   // 0168 LD B,A
            0xB7,                   // 0169 OR A
            0x28, 0xEB,             // 016A JR Z,loop      released
            0xFA, 0x01, 0xC0,       // 016C LD A,($C001)
            0x3C,                   // 016F INC A
            0xEA, 0x01, 0xC0,       // 0170 LD ($C001),A
            0xEA, 0x00, 0xA0,       // 0173 LD ($A000),A
            0x18, 0xDF,             // 0176 JR loop
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
