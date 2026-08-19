package com.nvu.operacional;

import android.media.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;

/** Exact physical regression: list visible after leaving and returning to GTO. */
public final class GtoHf48ReturnRecognitionScreenshotTest {
    private static void req(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        BufferedImage image = ImageIO.read(new File(
            "scripts/fixtures/hf48-return-recognition/list-after-app-return.png"
        ));
        req(image != null, "HF48 return screenshot must load");
        GtoFastVisualDetector detector = new GtoFastVisualDetector();
        GtoFastVisualDetector.Frame frame = analyze(detector, image, 1L);
        System.out.println("visibleFreights=" + frame.buttons.size());
        req(frame.hasFreightList(), "returned GTO screenshot must be visually recognized as a freight list");
        req(frame.buttons.size() == 5, "returned GTO screenshot must expose exactly 5 Accept rows");
        System.out.println("GtoHf48ReturnRecognitionScreenshotTest: PASS");
    }

    private static GtoFastVisualDetector.Frame analyze(GtoFastVisualDetector detector, BufferedImage image, long ts) {
        return detector.analyze(new BufferedImageFrame(image, ts), image.getWidth(), image.getHeight(), ts);
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
                    rgba[offset++] = (byte) ((argb >> 16) & 0xff);
                    rgba[offset++] = (byte) ((argb >> 8) & 0xff);
                    rgba[offset++] = (byte) (argb & 0xff);
                    rgba[offset++] = (byte) ((argb >> 24) & 0xff);
                }
            }
            planes = new Plane[]{new BufferedPlane(ByteBuffer.wrap(rgba), width * 4)};
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
