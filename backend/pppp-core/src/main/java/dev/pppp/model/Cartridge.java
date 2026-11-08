package dev.pppp.model;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** A ROM on the data volume. The id is the sanitized file name stem, so it is stable and filesystem-safe. */
public record Cartridge(String id, String title, long size, boolean cgb) {
    public static final Pattern ID = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    /** Bytes up to the end of the cartridge header. */
    public static final int HEADER_SIZE = 0x150;

    /** Title and CGB flag from the first {@link #HEADER_SIZE} bytes of a ROM. */
    public static Cartridge fromHeader(String id, byte[] header, long size) {
        boolean cgb = (header[0x143] & 0x80) != 0;
        int end = cgb ? 0x143 : 0x144;
        String title = new String(header, 0x134, end - 0x134, StandardCharsets.US_ASCII).replaceAll("[^\\x20-\\x7e]", "").trim();
        return new Cartridge(id, title, size, cgb);
    }

    /** Header checksum at 0x14D over 0x134..0x14C; the boot ROM refuses carts where it is wrong. */
    public static boolean headerChecksumOk(byte[] rom) {
        if (rom.length < HEADER_SIZE) return false;
        int x = 0;
        for (int i = 0x134; i <= 0x14C; i++) x = x - rom[i] - 1;
        return (x & 0xFF) == (rom[0x14D] & 0xFF);
    }
}
