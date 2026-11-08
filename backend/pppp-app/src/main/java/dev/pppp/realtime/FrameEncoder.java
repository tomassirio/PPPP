package dev.pppp.realtime;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;

/** Encodes a 160x144 0xRRGGBB frame as PNG (a few KB for Game Boy graphics). */
public final class FrameEncoder {
    private FrameEncoder() {}

    public static byte[] png(int[] rgb) {
        BufferedImage image = new BufferedImage(160, 144, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 160, 144, rgb, 0, 160);
        ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
