package com.nvu.operacional;

import android.media.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;

/** Physical HF45 regressions: list-after-result must remain visibly detectable. */
public final class GtoHf45CriticalScreenshotsTest {
    private static void req(boolean v, String m) { if (!v) throw new AssertionError(m); }

    public static void main(String[] args) throws Exception {
        GtoFastVisualDetector detector = new GtoFastVisualDetector();
        BufferedImage a = ImageIO.read(new File("scripts/fixtures/hf45-critical-flow/list-after-result.png"));
        BufferedImage b = ImageIO.read(new File("scripts/fixtures/hf45-critical-flow/list-after-result-menu.png"));
        req(a != null && b != null, "HF45 list-after-result screenshots must load");
        GtoFastVisualDetector.Frame fa = detector.analyze(new Frame(a, 1L), a.getWidth(), a.getHeight(), 1L);
        GtoFastVisualDetector.Frame fb = detector.analyze(new Frame(b, 2L), b.getWidth(), b.getHeight(), 2L);
        System.out.println("counts=" + fa.buttons.size() + "," + fb.buttons.size());
        req(fa.hasFreightList() && fa.buttons.size() == 5, "visible list after result must expose 5 Accept rows");
        req(fb.hasFreightList() && fb.buttons.size() == 5, "visible list behind NVU menu must expose 5 Accept rows");
        System.out.println("GtoHf45CriticalScreenshotsTest: PASS");
    }

    private static final class Frame extends Image {
        private final Plane[] planes; private final long ts;
        Frame(BufferedImage image, long ts) {
            this.ts = ts;
            int w=image.getWidth(), h=image.getHeight(), o=0;
            byte[] rgba=new byte[w*h*4];
            for (int y=0;y<h;y++) for (int x=0;x<w;x++) {
                int argb=image.getRGB(x,y);
                rgba[o++]=(byte)((argb>>16)&255); rgba[o++]=(byte)((argb>>8)&255);
                rgba[o++]=(byte)(argb&255); rgba[o++]=(byte)((argb>>24)&255);
            }
            planes=new Plane[]{new P(ByteBuffer.wrap(rgba), w*4)};
        }
        @Override public Plane[] getPlanes(){ return planes; }
        @Override public long getTimestamp(){ return ts; }
    }
    private static final class P extends Image.Plane {
        private final ByteBuffer b; private final int stride;
        P(ByteBuffer b,int stride){this.b=b;this.stride=stride;}
        @Override public ByteBuffer getBuffer(){return b.duplicate();}
        @Override public int getPixelStride(){return 4;}
        @Override public int getRowStride(){return stride;}
    }
}
