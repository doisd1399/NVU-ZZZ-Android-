package com.nvu.operacional;

import android.media.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;

/** User reproduction: real page-2 jobs list is visible while NVU still shows idle/cancelled observer card. */
public final class GtoHf40CancelledListScreenshotTest {
    private static void req(boolean v, String m) { if (!v) throw new AssertionError(m); }

    public static void main(String[] args) throws Exception {
        BufferedImage shot = ImageIO.read(new File("scripts/fixtures/hf40-cancelled-list-bootstrap.png"));
        req(shot != null, "HF40 cancelled-list fixture must load");
        GtoFastVisualDetector detector = new GtoFastVisualDetector();
        GtoFastVisualDetector.Frame frame = detector.analyze(
            new BufferedImageFrame(shot, 1L), shot.getWidth(), shot.getHeight(), 1L
        );
        req(frame.hasFreightList(), "the post-cancel screenshot must be recognized as a real freight-list visual candidate");
        req(frame.buttons.size() == 5, "the post-cancel screenshot must expose all 5 Accept rows");
        System.out.println("GtoHf40CancelledListScreenshotTest: PASS");
    }

    private static final class BufferedImageFrame extends Image {
        private final Plane[] planes;
        private final long timestamp;
        BufferedImageFrame(BufferedImage image, long timestamp) {
            this.timestamp = timestamp;
            int width = image.getWidth(), height = image.getHeight();
            byte[] rgba = new byte[width * height * 4];
            int o = 0;
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y);
                rgba[o++] = (byte)((argb >> 16) & 0xff);
                rgba[o++] = (byte)((argb >> 8) & 0xff);
                rgba[o++] = (byte)(argb & 0xff);
                rgba[o++] = (byte)((argb >> 24) & 0xff);
            }
            planes = new Plane[]{new BufferedPlane(ByteBuffer.wrap(rgba), width * 4)};
        }
        @Override public Plane[] getPlanes() { return planes; }
        @Override public long getTimestamp() { return timestamp; }
    }

    private static final class BufferedPlane extends Image.Plane {
        private final ByteBuffer b; private final int stride;
        BufferedPlane(ByteBuffer b, int stride) { this.b = b; this.stride = stride; }
        @Override public ByteBuffer getBuffer() { return b.duplicate(); }
        @Override public int getPixelStride() { return 4; }
        @Override public int getRowStride() { return stride; }
    }
}
