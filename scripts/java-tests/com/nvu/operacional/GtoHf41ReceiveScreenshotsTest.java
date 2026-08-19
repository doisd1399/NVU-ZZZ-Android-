package com.nvu.operacional;

import android.media.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;

public final class GtoHf41ReceiveScreenshotsTest {
    private static void req(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        GtoResultVisualGate gate = new GtoResultVisualGate();
        BufferedImage result = ImageIO.read(new File("scripts/fixtures/hf41-receive-flow/result-screen-receive.png"));
        BufferedImage gameplay = ImageIO.read(new File("scripts/fixtures/hf41-receive-flow/gameplay-after-receive.png"));
        req(result != null && gameplay != null, "HF41 physical screenshots must load");
        req(gate.looksLikeResultDialog(new Frame(result), result.getWidth(), result.getHeight()),
            "the physical Concluido/Receber screen must retain result-dialog visual authority");
        req(!gate.looksLikeResultDialog(new Frame(gameplay), gameplay.getWidth(), gameplay.getHeight()),
            "the physical gameplay screen after Receber must prove the result dialog is gone");
        req(result.getWidth() == gameplay.getWidth() && result.getHeight() == gameplay.getHeight(),
            "result/gameplay fixtures must preserve the same device capture geometry");
        System.out.println("PASS GtoHf41ReceiveScreenshotsTest");
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
