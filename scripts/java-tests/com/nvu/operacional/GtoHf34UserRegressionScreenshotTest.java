package com.nvu.operacional;

import android.media.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;

public final class GtoHf34UserRegressionScreenshotTest {
    private static void req(boolean v, String m) { if (!v) throw new AssertionError(m); }

    public static void main(String[] args) throws Exception {
        BufferedImage list = ImageIO.read(new File("scripts/fixtures/hf34-user-list-after-trip.png"));
        BufferedImage gameplay = ImageIO.read(new File("scripts/fixtures/hf34-user-selected-freight-origin.png"));
        BufferedImage listOverlay = ImageIO.read(new File("scripts/fixtures/hf34-user-list-with-review.png"));
        req(list != null && gameplay != null && listOverlay != null, "HF34 user fixtures must load");
        GtoFastVisualDetector detector = new GtoFastVisualDetector();

        GtoFastVisualDetector.Frame a = analyze(detector, list, 1L);
        req(a.hasFreightList(), "reopened page-2 freight list must be recognized");
        req(a.buttons.size() == 5, "reopened page-2 list must expose 5 Accept rows");

        GtoFastVisualDetector.Frame b = analyze(detector, gameplay, 2L);
        req(!b.hasFreightList(), "gameplay after selecting Barras de Ferro must not be classified as a list");

        GtoFastVisualDetector.Frame c = analyze(detector, listOverlay, 3L);
        req(c.hasFreightList(), "freight list must stay detectable with NVU overlay visible");
        req(c.buttons.size() == 5, "overlay freight list must still expose 5 Accept rows");

        System.out.println("GtoHf34UserRegressionScreenshotTest: PASS");
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
