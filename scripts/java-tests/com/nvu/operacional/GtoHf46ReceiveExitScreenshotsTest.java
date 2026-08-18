package com.nvu.operacional;

import android.media.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;

public final class GtoHf46ReceiveExitScreenshotsTest {
    private static void req(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        GtoResultVisualGate gate = new GtoResultVisualGate();
        BufferedImage result = ImageIO.read(new File("scripts/fixtures/hf46-receive-exit/result-before-receive.png"));
        BufferedImage after = ImageIO.read(new File("scripts/fixtures/hf46-receive-exit/gameplay-after-receive.png"));
        req(result != null && after != null, "HF46 physical screenshots must load");
        req(result.getWidth() == after.getWidth() && result.getHeight() == after.getHeight(),
            "physical screenshots must preserve capture geometry");

        req(gate.looksLikeResultDialog(new Frame(result), result.getWidth(), result.getHeight()),
            "real Concluido screen must wake result OCR");
        req(gate.looksLikeCertifiedResultStillVisible(new Frame(result), result.getWidth(), result.getHeight()),
            "real certified result must be considered still visible");

        // Root-cause reproduction: the old permissive wake-up gate can match this dark
        // gameplay frame, so it must never be used as the terminal continuity authority.
        req(gate.looksLikeResultDialog(new Frame(after), after.getWidth(), after.getHeight()),
            "physical post-Receber gameplay reproduces the historical permissive false-positive");
        req(!gate.looksLikeCertifiedResultStillVisible(new Frame(after), after.getWidth(), after.getHeight()),
            "strict certified-result continuity must prove the modal disappeared");

        req(GtoResultCompletionPolicy.shouldInferReceiveFromCertifiedExit(
                "RESULT_DETECTED", true, false, "", false, false,
                Long.MAX_VALUE, GtoResultCompletionPolicy.PASSIVE_EXIT_MIN_ABSENT_FRAMES,
                GtoResultCompletionPolicy.PASSIVE_EXIT_GRACE_MS + 1L),
            "certified modal exit without ADS evidence must recover a dropped Receive callback");

        System.out.println("PASS GtoHf46ReceiveExitScreenshotsTest");
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
