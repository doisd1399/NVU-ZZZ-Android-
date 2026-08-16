package com.nvu.operacional;
import android.media.Image; import java.awt.image.BufferedImage; import java.io.File; import java.nio.ByteBuffer; import javax.imageio.ImageIO;
public final class GtoR333RealResultGateTest {
 private static void req(boolean c,String m){if(!c)throw new AssertionError(m);}
 public static void main(String[] a)throws Exception{GtoResultVisualGate g=new GtoResultVisualGate(); BufferedImage r=ImageIO.read(new File("scripts/fixtures/r333-real-result.png")); BufferedImage t=ImageIO.read(new File("scripts/fixtures/r333-trip-overlay.png")); BufferedImage p=ImageIO.read(new File("scripts/fixtures/r333-result-failure-gameplay.png")); req(match(g,r),"real result"); req(!match(g,t),"trip overlay neutral"); req(!match(g,p),"gameplay neutral"); System.out.println("GtoR333RealResultGateTest: PASS");}
 private static boolean match(GtoResultVisualGate g,BufferedImage i){return g.looksLikeResultDialog(new F(i),i.getWidth(),i.getHeight());}
 static final class F extends Image{final Plane[] p;F(BufferedImage i){int w=i.getWidth(),h=i.getHeight(),o=0;byte[] q=new byte[w*h*4];for(int y=0;y<h;y++)for(int x=0;x<w;x++){int z=i.getRGB(x,y);q[o++]=(byte)(z>>16);q[o++]=(byte)(z>>8);q[o++]=(byte)z;q[o++]=(byte)(z>>24);}p=new Plane[]{new P(ByteBuffer.wrap(q),w*4)};}public Plane[] getPlanes(){return p;}public long getTimestamp(){return 1;}}
 static final class P extends Image.Plane{final ByteBuffer b;final int r;P(ByteBuffer b,int r){this.b=b;this.r=r;}public ByteBuffer getBuffer(){return b.duplicate();}public int getPixelStride(){return 4;}public int getRowStride(){return r;}}
}
