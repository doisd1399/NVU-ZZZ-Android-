package com.nvu.operacional;

import android.media.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;

/** HF38 physical regression: source list is a real list; post-selection gameplay/review is not. */
public final class GtoHf38UserCargoRegressionScreenshotTest {
    private static void req(boolean v, String m) { if (!v) throw new AssertionError(m); }

    public static void main(String[] args) throws Exception {
        BufferedImage list = ImageIO.read(new File("scripts/fixtures/hf38-mixed-line-regression/list-with-cargo-km-same-band.png"));
        BufferedImage toast = ImageIO.read(new File("scripts/fixtures/hf38-mixed-line-regression/review-toast-after-selection.png"));
        BufferedImage card = ImageIO.read(new File("scripts/fixtures/hf38-mixed-line-regression/review-card-cargo-missing.png"));
        req(list != null && toast != null && card != null, "HF38 fixtures must load");
        GtoFastVisualDetector detector = new GtoFastVisualDetector();

        GtoFastVisualDetector.Frame a = analyze(detector, list, 1L);
        req(a.hasFreightList(), "source screenshot must remain a real freight list");
        req(a.buttons.size() == 4, "source list must expose exactly 4 Accept rows");

        GtoFastVisualDetector.Frame b = analyze(detector, toast, 2L);
        req(!b.hasFreightList(), "gameplay with preserved-freight toast must not be a freight list");

        GtoFastVisualDetector.Frame c = analyze(detector, card, 3L);
        req(!c.hasFreightList(), "gameplay with NVU review card must not be a freight list");

        System.out.println("GtoHf38UserCargoRegressionScreenshotTest: PASS");
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
