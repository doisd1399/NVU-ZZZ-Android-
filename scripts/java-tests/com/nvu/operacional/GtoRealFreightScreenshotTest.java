package com.nvu.operacional;

import android.media.Image;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;

public final class GtoRealFreightScreenshotTest {
    private static final File FIXTURE = new File("scripts/fixtures/gto-real-freight-list-5.png");

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        BufferedImage original = ImageIO.read(FIXTURE);
        require(original != null, "real GTO screenshot fixture must load");
        require(original.getWidth() == 1536 && original.getHeight() == 691,
            "fixture dimensions must remain the reported 1536x691 capture");

        GtoFastVisualDetector detector = new GtoFastVisualDetector();
        GtoFastVisualDetector.Frame exact = analyze(detector, original, 1L);
        require(exact.hasFreightList(), "production detector must recognize the exact reported freight list");
        require(exact.buttons.size() == 5, "exact reported screenshot must expose exactly five freights");

        // The GTO can show fewer jobs on the same page. Remove only the lower Aceitar
        // buttons from the real screenshot and ensure every 1..5 cardinality is accepted.
        for (int count = 1; count <= 5; count++) {
            BufferedImage variant = copy(original);
            for (int row = count; row < 5; row++) {
                int top = new int[] { 42, 163, 285, 406, 528 }[row];
                int bottom = new int[] { 95, 216, 338, 460, 581 }[row];
                // Replace the orange button with the same neutral card tone; leave the
                // freight text intact so this tests button-stack detection, not a crop.
                for (int y = Math.max(0, top - 4); y < Math.min(variant.getHeight(), bottom + 4); y++) {
                    for (int x = 1390; x < Math.min(variant.getWidth(), 1525); x++) {
                        variant.setRGB(x, y, 0xFF3A3A3A);
                    }
                }
            }
            GtoFastVisualDetector.Frame frame = analyze(detector, variant, 10L + count);
            require(frame.hasFreightList(), "real-pixel " + count + "-freight page must be valid");
            require(frame.buttons.size() == count,
                "real-pixel page must report " + count + " freights, got " + frame.buttons.size());
        }

        int[][] sizes = new int[][] {
            { 1024, 461 },
            { 1280, 576 },
            { 1536, 691 },
            { 1920, 864 }
        };
        for (int[] size : sizes) {
            BufferedImage scaled = scale(original, size[0], size[1]);
            GtoFastVisualDetector.Frame frame = analyze(detector, scaled, 100L + size[0]);
            require(frame.hasFreightList(),
                "scaled real screenshot must remain detectable at " + size[0] + "x" + size[1]);
            require(frame.buttons.size() == 5,
                "scaled real screenshot must keep five rows at " + size[0] + "x" + size[1]);
        }

        System.out.println("GtoRealFreightScreenshotTest: PASS");
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

    private static BufferedImage scale(BufferedImage input, int width, int height) {
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(input, 0, 0, width, height, null);
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
                    rgba[offset++] = (byte) ((argb >> 16) & 0xFF); // R
                    rgba[offset++] = (byte) ((argb >> 8) & 0xFF);  // G
                    rgba[offset++] = (byte) (argb & 0xFF);         // B
                    rgba[offset++] = (byte) ((argb >> 24) & 0xFF);// A
                }
            }
            this.planes = new Plane[] { new BufferedPlane(ByteBuffer.wrap(rgba), width * 4) };
        }

        @Override
        public Plane[] getPlanes() {
            return planes;
        }

        @Override
        public long getTimestamp() {
            return timestamp;
        }
    }

    private static final class BufferedPlane extends Image.Plane {
        private final ByteBuffer buffer;
        private final int rowStride;

        BufferedPlane(ByteBuffer buffer, int rowStride) {
            this.buffer = buffer;
            this.rowStride = rowStride;
        }

        @Override
        public ByteBuffer getBuffer() {
            return buffer.duplicate();
        }

        @Override
        public int getPixelStride() {
            return 4;
        }

        @Override
        public int getRowStride() {
            return rowStride;
        }
    }
}
