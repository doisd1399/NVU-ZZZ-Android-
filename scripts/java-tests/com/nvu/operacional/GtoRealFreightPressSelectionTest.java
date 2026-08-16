package com.nvu.operacional;

import android.graphics.Rect;
import android.media.Image;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;

/**
 * Runs the production detector against the real 1536x691 GTO freight screenshot and
 * simulates the short visual state produced when each Aceitar button is pressed.
 */
public final class GtoRealFreightPressSelectionTest {
    private static final File FIXTURE = new File("scripts/fixtures/gto-real-freight-list-5.png");

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        BufferedImage original = ImageIO.read(FIXTURE);
        require(original != null, "real freight fixture must load");
        GtoFastVisualDetector detector = new GtoFastVisualDetector();
        GtoFastVisualDetector.Frame baseline = analyze(detector, original, 1L);
        require(baseline.hasFreightList() && baseline.buttons.size() == 5,
            "baseline must expose five real freight rows");

        for (int row = 0; row < baseline.buttons.size(); row++) {
            BufferedImage pressed = copy(original);
            Rect button = baseline.buttons.get(row);
            // The GTO press animation can darken the orange fill enough for the orange
            // mask to lose that one row. Keep surrounding card/text pixels untouched.
            int left = Math.max(0, button.left - 5);
            int right = Math.min(pressed.getWidth(), button.right + 5);
            int top = Math.max(0, button.top - 4);
            int bottom = Math.min(pressed.getHeight(), button.bottom + 4);
            for (int y = top; y < bottom; y++) {
                for (int x = left; x < right; x++) {
                    pressed.setRGB(x, y, 0xFF3A3A3A);
                }
            }
            GtoFastVisualDetector.Frame current = analyze(detector, pressed, 10L + row);
            GtoFastVisualDetector.PressCandidate candidate =
                detector.detectPressedRowAfterTouch(baseline, current, original.getHeight());
            if (candidate == null) {
                candidate = detector.detectTemporarilyMissingPressedRow(
                    baseline, current, original.getHeight()
                );
            }
            require(candidate != null, "real-pixel press must identify row " + (row + 1));
            require(candidate.row == row,
                "real-pixel press row mismatch: expected " + row + " got " + candidate.row);
        }

        // A clean unchanged list must never be interpreted as a selection.
        GtoFastVisualDetector.Frame unchanged = analyze(detector, copy(original), 100L);
        require(detector.detectPressedRowAfterTouch(baseline, unchanged, original.getHeight()) == null,
            "unchanged list cannot become a selected freight");
        require(detector.detectPressedRow(baseline, unchanged, original.getHeight()) == null,
            "passive visual path cannot invent a selected row");

        System.out.println("GtoRealFreightPressSelectionTest: PASS");
    }

    private static GtoFastVisualDetector.Frame analyze(
        GtoFastVisualDetector detector,
        BufferedImage image,
        long timestamp
    ) {
        BufferedImageFrame frame = new BufferedImageFrame(image, timestamp);
        return detector.analyze(frame, image.getWidth(), image.getHeight(), timestamp);
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
