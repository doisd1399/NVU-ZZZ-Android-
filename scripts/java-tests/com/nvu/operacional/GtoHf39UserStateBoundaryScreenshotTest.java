package com.nvu.operacional;

import android.media.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;

/** Physical HF39 regression: a real list underneath stale NVU review is still a list boundary. */
public final class GtoHf39UserStateBoundaryScreenshotTest {
    private static void req(boolean v, String m) { if (!v) throw new AssertionError(m); }

    public static void main(String[] args) throws Exception {
        BufferedImage listWithReview = ImageIO.read(new File("scripts/fixtures/hf39-state-boundary/list-visible-stale-review.png"));
        BufferedImage gameplayReview = ImageIO.read(new File("scripts/fixtures/hf39-state-boundary/gameplay-review.png"));
        BufferedImage gameplayToast = ImageIO.read(new File("scripts/fixtures/hf39-state-boundary/gameplay-toast.png"));
        req(listWithReview != null && gameplayReview != null && gameplayToast != null, "HF39 fixtures must load");
        GtoFastVisualDetector detector = new GtoFastVisualDetector();

        GtoFastVisualDetector.Frame a = analyze(detector, listWithReview, 1L);
        req(a.hasFreightList(), "real list must remain visible even under stale review overlay");
        req(a.buttons.size() == 5, "stale-review screenshot must expose exactly 5 Accept rows");

        GtoFastVisualDetector.Frame b = analyze(detector, gameplayReview, 2L);
        req(!b.hasFreightList(), "gameplay with review overlay must not be a freight list");

        GtoFastVisualDetector.Frame c = analyze(detector, gameplayToast, 3L);
        req(!c.hasFreightList(), "gameplay with toast must not be a freight list");

        System.out.println("GtoHf39UserStateBoundaryScreenshotTest: PASS");
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
