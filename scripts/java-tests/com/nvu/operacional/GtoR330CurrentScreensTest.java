package com.nvu.operacional;

import android.graphics.Rect;
import android.media.Image;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;

/** Regression using the exact screenshots reported with the R3.29 selection failure. */
public final class GtoR330CurrentScreensTest {
    private static void require(boolean c, String m) { if (!c) throw new AssertionError(m); }

    public static void main(String[] args) throws Exception {
        GtoFastVisualDetector detector = new GtoFastVisualDetector();
        long ts = 100L;

        BufferedImage list = ImageIO.read(new File("scripts/fixtures/r330-real-list-page2.png"));
        require(list != null, "current real list must load");
        GtoFastVisualDetector.Frame baseline = analyze(detector, list, ts++);
        require(baseline.hasFreightList(), "current real list must be recognized");
        require(baseline.buttons.size() == 5, "current real list must contain exactly 5 visible jobs");

        String[] neutral = {
            "scripts/fixtures/r330-selection-error-gameplay.png",
            "scripts/fixtures/r330-selection-error-overlay.png",
            "scripts/fixtures/r330-post-failure-gameplay.png",
            "scripts/fixtures/r330-old-false-list-gameplay.png"
        };
        for (String path : neutral) {
            BufferedImage image = ImageIO.read(new File(path));
            require(image != null, path + " must load");
            GtoFastVisualDetector.Frame frame = analyze(detector, image, ts++);
            require(!frame.hasFreightList(), path + " must stay neutral");
        }

        for (int row = 0; row < baseline.buttons.size(); row++) {
            BufferedImage pressed = copy(list);
            Rect button = baseline.buttons.get(row);
            for (int y = Math.max(0, button.top - 2); y < Math.min(pressed.getHeight(), button.bottom + 2); y++) {
                for (int x = Math.max(0, button.left - 3); x < Math.min(pressed.getWidth(), button.right + 3); x++) {
                    pressed.setRGB(x, y, 0xFF353535);
                }
            }
            GtoFastVisualDetector.Frame current = analyze(detector, pressed, ts++);
            GtoFastVisualDetector.PressCandidate candidate = detector.detectPressedRowAfterTouch(baseline, current, list.getHeight());
            if (candidate == null) candidate = detector.detectTemporarilyMissingPressedRow(baseline, current, list.getHeight());
            require(candidate != null, "row " + (row + 1) + " must produce a selection candidate");
            require(candidate.row == row, "row " + (row + 1) + " must remain exact");
        }

        System.out.println("GtoR330CurrentScreensTest: PASS");
    }

    private static GtoFastVisualDetector.Frame analyze(GtoFastVisualDetector d, BufferedImage image, long ts) {
        return d.analyze(new BufferedImageFrame(image, ts), image.getWidth(), image.getHeight(), ts);
    }

    private static BufferedImage copy(BufferedImage input) {
        BufferedImage output = new BufferedImage(input.getWidth(), input.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = output.createGraphics();
        g.drawImage(input, 0, 0, null);
        g.dispose();
        return output;
    }

    private static final class BufferedImageFrame extends Image {
        private final Plane[] planes;
        private final long timestamp;
        BufferedImageFrame(BufferedImage image, long timestamp) {
            this.timestamp = timestamp;
            int w = image.getWidth(), h = image.getHeight();
            byte[] rgba = new byte[w * h * 4];
            int o = 0;
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int a = image.getRGB(x, y);
                rgba[o++] = (byte)(a >> 16);
                rgba[o++] = (byte)(a >> 8);
                rgba[o++] = (byte)a;
                rgba[o++] = (byte)(a >> 24);
            }
            planes = new Plane[] { new BufferedPlane(ByteBuffer.wrap(rgba), w * 4) };
        }
        @Override public Plane[] getPlanes() { return planes; }
        @Override public long getTimestamp() { return timestamp; }
    }

    private static final class BufferedPlane extends Image.Plane {
        private final ByteBuffer buffer;
        private final int rowStride;
        BufferedPlane(ByteBuffer buffer, int rowStride) { this.buffer = buffer; this.rowStride = rowStride; }
        @Override public ByteBuffer getBuffer() { return buffer.duplicate(); }
        @Override public int getPixelStride() { return 4; }
        @Override public int getRowStride() { return rowStride; }
    }
}
