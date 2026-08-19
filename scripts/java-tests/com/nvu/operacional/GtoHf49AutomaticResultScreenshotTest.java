package com.nvu.operacional;

import android.media.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;

public final class GtoHf49AutomaticResultScreenshotTest {
    private static void req(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        BufferedImage result = ImageIO.read(new File("scripts/fixtures/hf49-auto-result/result-concluido.png"));
        req(result != null, "HF49 physical Concluido screenshot must load");
        GtoResultVisualGate gate = new GtoResultVisualGate();
        req(gate.looksLikeResultDialog(new Frame(result), result.getWidth(), result.getHeight()),
            "physical Concluido screen must wake semantic result OCR");
        req(gate.looksLikeCertifiedResultStillVisible(new Frame(result), result.getWidth(), result.getHeight()),
            "physical Concluido modal must remain a strict certified-result visual");
        System.out.println("PASS GtoHf49AutomaticResultScreenshotTest");
    }

    static final class Frame extends Image {
        final Plane[] planes;
        Frame(BufferedImage image) {
            int w = image.getWidth(), h = image.getHeight(), offset = 0;
            byte[] rgba = new byte[w * h * 4];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int pixel = image.getRGB(x, y);
                    rgba[offset++] = (byte) (pixel >> 16);
                    rgba[offset++] = (byte) (pixel >> 8);
                    rgba[offset++] = (byte) pixel;
                    rgba[offset++] = (byte) (pixel >> 24);
                }
            }
            planes = new Plane[]{new P(ByteBuffer.wrap(rgba), w * 4)};
        }
        public Plane[] getPlanes() { return planes; }
        public long getTimestamp() { return 1L; }
    }

    static final class P extends Image.Plane {
        final ByteBuffer buffer;
        final int rowStride;
        P(ByteBuffer buffer, int rowStride) { this.buffer = buffer; this.rowStride = rowStride; }
        public ByteBuffer getBuffer() { return buffer.duplicate(); }
        public int getPixelStride() { return 4; }
        public int getRowStride() { return rowStride; }
    }
}
