package dev.pppp.service.game;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;

/**
 * Decompresses a Generation I Pokémon picture (front/back sprites) from the ROM and renders it as a PNG.
 * Port of the pret/pokemontools pic decompressor: two RLE/bit-group planes, delta-decoded, optionally XORed.
 */
final class GenOnePic {
    private static final int[] TABLE2_0 = {0x0, 0x1, 0x3, 0x2, 0x7, 0x6, 0x4, 0x5, 0xf, 0xe, 0xc, 0xd, 0x8, 0x9, 0xb, 0xa};
    private static final int[] TABLE2_1 = {0xf, 0xe, 0xc, 0xd, 0x8, 0x9, 0xb, 0xa, 0x0, 0x1, 0x3, 0x2, 0x7, 0x6, 0x4, 0x5};
    /** DMG shades: color 0 is transparent (outside the sprite), 1-3 light to dark. */
    private static final int[] SHADES = {0x00000000, 0xFFA8A8A8, 0xFF555555, 0xFF000000};
    /** Colour 0 inside the outline: white body, so sprites read on dark backgrounds too. */
    private static final int WHITE = 0xFFF8F8F8;

    private final byte[] rom;
    private int bit; // absolute bit position in rom
    private int widthTiles, heightTiles, rows, cols; // rows = height in px (tiles*8), cols = width in tiles

    private GenOnePic(byte[] rom, int offset) {
        this.rom = rom;
        this.bit = offset * 8;
    }

    /** PNG of the picture at that ROM offset in grey shades, scaled by an integer factor. */
    static byte[] png(byte[] rom, int offset, int scale) {
        return png(rom, offset, scale, null);
    }

    /** As {@link #png(byte[], int, int)} with colours 0-3 from palette (ARGB), or grey when it is null. */
    static byte[] png(byte[] rom, int offset, int scale, int[] palette) {
        GenOnePic p = new GenOnePic(rom, offset);
        int[][] pixels = p.decompress();
        int h = pixels.length, w = pixels[0].length;
        boolean[][] outside = outside(pixels);
        BufferedImage img = new BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h * scale; y++) {
            for (int x = 0; x < w * scale; x++) {
                int c = pixels[y / scale][x / scale];
                int argb = c == 0 && outside[y / scale][x / scale] ? 0
                        : palette != null ? palette[c] : c == 0 ? WHITE : SHADES[c];
                img.setRGB(x, y, argb);
            }
        }
        var out = new ByteArrayOutputStream();
        try {
            ImageIO.write(img, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /** Colour-0 pixels connected to the border: the background, drawn transparent. */
    private static boolean[][] outside(int[][] px) {
        int h = px.length, w = px[0].length;
        boolean[][] out = new boolean[h][w];
        java.util.ArrayDeque<int[]> todo = new java.util.ArrayDeque<>();
        for (int x = 0; x < w; x++) {
            todo.add(new int[] {x, 0});
            todo.add(new int[] {x, h - 1});
        }
        for (int y = 0; y < h; y++) {
            todo.add(new int[] {0, y});
            todo.add(new int[] {w - 1, y});
        }
        while (!todo.isEmpty()) {
            int[] p = todo.poll();
            int x = p[0], y = p[1];
            if (x < 0 || y < 0 || x >= w || y >= h || out[y][x] || px[y][x] != 0) continue;
            out[y][x] = true;
            todo.add(new int[] {x + 1, y});
            todo.add(new int[] {x - 1, y});
            todo.add(new int[] {x, y + 1});
            todo.add(new int[] {x, y - 1});
        }
        return out;
    }

    private int readBit() {
        int b = (rom[bit >> 3] >> (7 - (bit & 7))) & 1;
        bit++;
        return b;
    }

    private int readInt(int n) {
        int v = 0;
        for (int i = 0; i < n; i++) v = v << 1 | readBit();
        return v;
    }

    /** Pixel colors 0-3, [y][x]. */
    private int[][] decompress() {
        widthTiles = readInt(4);
        heightTiles = readInt(4);
        cols = widthTiles;
        rows = heightTiles * 8;
        int size = cols * rows; // bytes per plane
        int order = readBit();
        byte[][] planes = new byte[2][];
        planes[order] = fill(size);
        int mode = readBit();
        if (mode == 1) mode += readBit();
        planes[order ^ 1] = fill(size);
        int r1 = order, r2 = order ^ 1;
        switch (mode) {
            case 0 -> {
                decode(planes[0]);
                decode(planes[1]);
            }
            case 1 -> {
                decode(planes[r1]);
                xor(planes[r1], planes[r2]);
            }
            default -> {
                decode(planes[r2]);
                decode(planes[r1]);
                xor(planes[r1], planes[r2]);
            }
        }
        // Planes are column-major: column c (8 px wide), row y -> byte c*rows + y. Plane 0 is the low bit.
        int[][] px = new int[rows][cols * 8];
        for (int c = 0; c < cols; c++) {
            for (int y = 0; y < rows; y++) {
                int lo = planes[0][c * rows + y] & 0xFF, hi = planes[1][c * rows + y] & 0xFF;
                for (int b = 0; b < 8; b++) px[y][c * 8 + b] = (lo >> (7 - b) & 1) | (hi >> (7 - b) & 1) << 1;
            }
        }
        return px;
    }

    /** One plane: alternating RLE-of-zero and literal chunks of 2-bit groups, then deinterlaced into bytes. */
    private byte[] fill(int size) {
        int groups = size * 4;
        int[] g = new int[groups];
        int n = 0;
        boolean rle = readBit() == 0;
        while (n < groups) {
            if (rle) {
                int i = 0;
                while (readBit() == 1) i++;
                int count = ((2 << i) - 1) + readInt(i + 1);
                for (int k = 0; k < count && n < groups; k++) g[n++] = 0;
            } else {
                while (n < groups) {
                    int v = readInt(2);
                    if (v == 0) break;
                    g[n++] = v;
                }
            }
            rle = !rle;
        }
        // Groups run down 2-px-wide columns; regroup into bytes of 4 groups per pixel row of an 8-px column.
        int width2 = cols * 4; // number of 2-px columns
        byte[] out = new byte[size];
        int i = 0;
        for (int c2 = 0; c2 < width2; c2++) {
            for (int y = 0; y < rows; y++) {
                int c = c2 / 4, shift = 6 - 2 * (c2 % 4);
                out[c * rows + y] |= (byte) (g[i++] << shift);
            }
        }
        return out;
    }

    /** Undo the per-row delta coding, left to right across each pixel row. */
    private void decode(byte[] plane) {
        for (int y = 0; y < rows; y++) {
            int prev = 0;
            for (int c = 0; c < cols; c++) {
                int i = c * rows + y;
                int a = (plane[i] >> 4) & 0xF, b = plane[i] & 0xF;
                a = (prev == 0 ? TABLE2_0 : TABLE2_1)[a];
                prev = a & 1;
                b = (prev == 0 ? TABLE2_0 : TABLE2_1)[b];
                prev = b & 1;
                plane[i] = (byte) (a << 4 | b);
            }
        }
    }

    private static void xor(byte[] from, byte[] into) {
        for (int i = 0; i < into.length; i++) into[i] ^= from[i];
    }
}
