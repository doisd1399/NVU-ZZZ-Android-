package com.nvu.operacional;

import android.media.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;

public final class GtoHf37UserReplacementScreenshotTest {
    private static void req(boolean v, String m) { if (!v) throw new AssertionError(m); }

    public static void main(String[] args) throws Exception {
        BufferedImage firstList = ImageIO.read(new File("scripts/fixtures/hf37-replacement-list-before.png"));
        BufferedImage currentList = ImageIO.read(new File("scripts/fixtures/hf37-replacement-list-current.png"));
        BufferedImage afterAccept = ImageIO.read(new File("scripts/fixtures/hf37-after-accept-review-regression.png"));
        req(firstList != null && currentList != null && afterAccept != null, "HF37 user fixtures must load");
        GtoFastVisualDetector detector = new GtoFastVisualDetector();

        GtoFastVisualDetector.Frame a = analyze(detector, firstList, 1L);
        req(a.hasFreightList(), "first replacement list must be recognized");
        req(a.buttons.size() == 5, "first replacement list must expose exactly 5 Accept rows");

        GtoFastVisualDetector.Frame b = analyze(detector, currentList, 2L);
        req(b.hasFreightList(), "current replacement list must be recognized");
        req(b.buttons.size() == 4, "current replacement list must expose exactly 4 Accept rows");

        GtoFastVisualDetector.Frame c = analyze(detector, afterAccept, 3L);
        req(!c.hasFreightList(), "gameplay after Accept must not be classified as a freight list");

        System.out.println("GtoHf37UserReplacementScreenshotTest: PASS");
    }

    private static GtoFastVisualDetector.Frame analyze(GtoFastVisualDetector d, BufferedImage image, long ts) {
        return d.analyze(new BufferedImageFrame(image, ts), image.getWidth(), image.getHeight(), ts);
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
        BufferedPlane(ByteBuffer b, int stride) { this.b=b; this.stride=stride; }
        @Override public ByteBuffer getBuffer() { return b.duplicate(); }
        @Override public int getPixelStride() { return 4; }
        @Override public int getRowStride() { return stride; }
    }
}
