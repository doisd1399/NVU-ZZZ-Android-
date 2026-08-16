package com.nvu.operacional;

import android.graphics.Rect;
import android.media.Image;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;

/** Regression for the exact GTO freight-list screenshot supplied with the HF9 report. */
public final class GtoR334Hf9FreightScreenTest {
    private static final File FIXTURE = new File("scripts/fixtures/gto-real-freight-list-hf9.png");

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        BufferedImage original = ImageIO.read(FIXTURE);
        require(original != null, "HF9 reported screenshot must load");
        require(original.getWidth() == 1536 && original.getHeight() == 691,
            "HF9 screenshot must remain 1536x691");

        GtoFastVisualDetector detector = new GtoFastVisualDetector();
        GtoFastVisualDetector.Frame baseline = analyze(detector, original, 1L);
        require(baseline.hasFreightList(), "HF9 reported freight list must be recognized");
        require(baseline.buttons.size() == 5,
            "HF9 reported list must expose exactly five Aceitar rows, got " + baseline.buttons.size());

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
            require(candidate != null, "HF9 screenshot press must identify row " + (row + 1));
            require(candidate.row == row,
                "HF9 row mismatch: expected " + row + " got " + candidate.row);
        }

        System.out.println("GtoR334Hf9FreightScreenTest: PASS");
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
