package com.nvu.operacional;

import android.media.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;

/** Real-pixel gate: a jobs list needs complete freight-card context beside Aceitar. */
public final class GtoR332FreightCardSignatureTest {
    private static void require(boolean c, String m) { if (!c) throw new AssertionError(m); }

    public static void main(String[] args) throws Exception {
        GtoFastVisualDetector detector = new GtoFastVisualDetector();
        BufferedImage list = ImageIO.read(new File("scripts/fixtures/r330-real-list-page2.png"));
        require(list != null, "real freight list fixture must load");
        GtoFastVisualDetector.Frame frame = analyze(detector, list, 1L);
        require(frame.hasFreightList(), "real card+Aceitar list must be recognized");
        require(frame.buttons.size() == 5, "real page must retain five visible freights");
        int evidence = 0;
        for (int i = 0; i < frame.buttons.size(); i++) {
            if (frame.cardDarkRatios[i] >= 0.62f
                && frame.cardLightTextRatios[i] >= 0.012f
                && frame.cardGreenInfoRatios[i] >= 0.0025f) evidence++;
        }
        require(evidence >= 3, "real list must contain card body + text + green info evidence");

        String[] neutral = {
            "scripts/fixtures/r331-trip-phantom-list.png",
            "scripts/fixtures/r331-post-trip-screen.png",
            "scripts/fixtures/r329-route-menu.png",
            "scripts/fixtures/r329-gameplay-false-2.png",
            "scripts/fixtures/r329-gameplay-false-1.png"
        };
        long ts = 10L;
        for (String path : neutral) {
            BufferedImage image = ImageIO.read(new File(path));
            require(image != null, path + " must load");
            GtoFastVisualDetector.Frame candidate = analyze(detector, image, ts++);
            require(!candidate.hasFreightList(), path + " must remain neutral without a real freight card");
        }
        System.out.println("GtoR332FreightCardSignatureTest: PASS");
    }

    private static GtoFastVisualDetector.Frame analyze(GtoFastVisualDetector d, BufferedImage image, long ts) {
        return d.analyze(new BufferedImageFrame(image, ts), image.getWidth(), image.getHeight(), ts);
    }
    private static final class BufferedImageFrame extends Image {
        private final Plane[] planes; private final long timestamp;
        BufferedImageFrame(BufferedImage image, long timestamp) {
            this.timestamp = timestamp;
            int w=image.getWidth(), h=image.getHeight(); byte[] rgba=new byte[w*h*4]; int o=0;
            for(int y=0;y<h;y++) for(int x=0;x<w;x++) { int a=image.getRGB(x,y); rgba[o++]=(byte)(a>>16); rgba[o++]=(byte)(a>>8); rgba[o++]=(byte)a; rgba[o++]=(byte)(a>>24); }
            planes = new Plane[] { new BufferedPlane(ByteBuffer.wrap(rgba), w*4) };
        }
        @Override public Plane[] getPlanes(){ return planes; }
        @Override public long getTimestamp(){ return timestamp; }
    }
    private static final class BufferedPlane extends Image.Plane {
        private final ByteBuffer buffer; private final int rowStride;
        BufferedPlane(ByteBuffer b,int r){buffer=b;rowStride=r;}
        @Override public ByteBuffer getBuffer(){return buffer.duplicate();}
        @Override public int getPixelStride(){return 4;}
        @Override public int getRowStride(){return rowStride;}
    }
}
