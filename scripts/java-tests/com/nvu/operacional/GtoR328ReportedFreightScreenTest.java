package com.nvu.operacional;

import android.graphics.Rect;
import android.media.Image;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;

/** Regression for the real GTO freight-list screenshot reported after R3.27-HF2. */
public final class GtoR328ReportedFreightScreenTest {
    private static final File FIXTURE = new File("scripts/fixtures/gto-real-freight-list-r328.png");

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        BufferedImage original = ImageIO.read(FIXTURE);
        require(original != null, "reported GTO screenshot must load");
        require(original.getWidth() == 1536 && original.getHeight() == 691,
            "reported GTO screenshot must remain 1536x691");

        GtoFastVisualDetector detector = new GtoFastVisualDetector();
        GtoFastVisualDetector.Frame baseline = analyze(detector, original, 1L);
        require(baseline.hasFreightList(), "real reported list must be recognized by production detector");
        require(baseline.buttons.size() == 5,
            "real reported list must contain exactly five Aceitar rows, got " + baseline.buttons.size());

        for (int row = 0; row < baseline.buttons.size(); row++) {
            BufferedImage pressed = copy(original);
            Rect button = baseline.buttons.get(row);
            for (int y = Math.max(0, button.top - 3); y < Math.min(pressed.getHeight(), button.bottom + 3); y++) {
                for (int x = Math.max(0, button.left - 4); x < Math.min(pressed.getWidth(), button.right + 4); x++) {
                    pressed.setRGB(x, y, 0xFF3A3A3A);
                }
            }
            GtoFastVisualDetector.Frame current = analyze(detector, pressed, 10L + row);
            GtoFastVisualDetector.PressCandidate candidate =
                detector.detectPressedRowAfterTouch(baseline, current, original.getHeight());
            if (candidate == null) {
                candidate = detector.detectTemporarilyMissingPressedRow(baseline, current, original.getHeight());
            }
            require(candidate != null, "reported screen press must identify row " + (row + 1));
            require(candidate.row == row,
                "reported screen row mismatch: expected " + row + " got " + candidate.row);
        }

        System.out.println("GtoR328ReportedFreightScreenTest: PASS");
    }

    private static GtoFastVisualDetector.Frame analyze(
        GtoFastVisualDetector detector, BufferedImage image, long timestamp
    ) {
        return detector.analyze(
            new BufferedImageFrame(image, timestamp), image.getWidth(), image.getHeight(), timestamp
        );
    }

    private static BufferedImage copy(BufferedImage input) {
        BufferedImage output = new BufferedImage(input.getWidth(), input.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        graphics.drawImage(input, 0, 0, null);
        graphics.dispose();
        return output;
    }

    private static final class BufferedImageFrame extends Image {
        private final Plane[] planes;
        private final long timestamp;
        BufferedImageFrame(BufferedImage image, long timestamp) {
            this.timestamp = timestamp;
            int width = image.getWidth();
            int height = image.getHeight();
            byte[] rgba = new byte[width * height * 4];
            int offset = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = image.getRGB(x, y);
                    rgba[offset++] = (byte) ((argb >> 16) & 0xFF);
                    rgba[offset++] = (byte) ((argb >> 8) & 0xFF);
                    rgba[offset++] = (byte) (argb & 0xFF);
                    rgba[offset++] = (byte) ((argb >> 24) & 0xFF);
                }
            }
            planes = new Plane[] { new BufferedPlane(ByteBuffer.wrap(rgba), width * 4) };
        }
        @Override public Plane[] getPlanes() { return planes; }
        @Override public long getTimestamp() { return timestamp; }
    }

    private static final class BufferedPlane extends Image.Plane {
        private final ByteBuffer buffer;
        private final int rowStride;
        BufferedPlane(ByteBuffer buffer, int rowStride) {
            this.buffer = buffer;
            this.rowStride = rowStride;
        }
        @Override public ByteBuffer getBuffer() { return buffer.duplicate(); }
        @Override public int getPixelStride() { return 4; }
        @Override public int getRowStride() { return rowStride; }
    }
}
