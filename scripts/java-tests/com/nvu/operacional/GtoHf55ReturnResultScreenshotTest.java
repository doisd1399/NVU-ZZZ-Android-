package com.nvu.operacional;

import android.media.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;

public final class GtoHf55ReturnResultScreenshotTest {
    private static void req(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static BufferedImage load(String path) throws Exception {
        BufferedImage image = ImageIO.read(new File(path));
        req(image != null, "physical HF55 screenshot must load: " + path);
        return image;
    }

    private static void requireStrong(GtoResultVisualGate gate, BufferedImage image, String label) {
        req(
            gate.looksLikeStrongReturnResultDialog(new Frame(image), image.getWidth(), image.getHeight()),
            label + " must qualify for the strict paused-return result probe"
        );
    }

    public static void main(String[] args) throws Exception {
        GtoResultVisualGate gate = new GtoResultVisualGate();
        BufferedImage resultAfterCall = load(
            "scripts/fixtures/hf55-return-result/result-after-call.png"
        );
        BufferedImage resultUnderNvuMenu = load(
            "scripts/fixtures/hf55-return-result/result-under-nvu-menu.png"
        );

        requireStrong(gate, resultAfterCall, "physical Concluido screen after interruption");
        requireStrong(gate, resultUnderNvuMenu, "physical Concluido screen partially covered by NVU menu");

        // The strict return bridge is intentionally a visual wake-up only. Existing
        // semantic OCR remains responsible for proving Concluido + payout.
        req(
            gate.looksLikeResultDialog(new Frame(resultAfterCall), resultAfterCall.getWidth(), resultAfterCall.getHeight()),
            "same physical result must still wake ordinary semantic OCR"
        );

        System.out.println("GtoHf55ReturnResultScreenshotTest: PASS");
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
        P(ByteBuffer buffer, int rowStride) {
            this.buffer = buffer;
            this.rowStride = rowStride;
        }
        public ByteBuffer getBuffer() { return buffer.duplicate(); }
        public int getPixelStride() { return 4; }
        public int getRowStride() { return rowStride; }
    }
}
