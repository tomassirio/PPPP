package dev.pppp.service.game;

/** A window of the console's memory read in one frame, addressed by bus address. */
public record Ram(int base, byte[] bytes) {
    public int u8(int address) {
        return bytes[address - base] & 0xFF;
    }

    /** Big-endian, as Game Boy games usually store multi-byte stats. */
    public int u16(int address) {
        return u8(address) << 8 | u8(address + 1);
    }

    /** Little-endian, as flag arrays spanning two bytes are laid out. */
    public int u16le(int address) {
        return u8(address) | u8(address + 1) << 8;
    }

    public byte[] slice(int address, int length) {
        return java.util.Arrays.copyOfRange(bytes, address - base, address - base + length);
    }
}
