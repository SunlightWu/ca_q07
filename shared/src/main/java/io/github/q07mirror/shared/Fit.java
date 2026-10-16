package io.github.q07mirror.shared;

/** Single source of truth for letterboxing and touch coordinates. */
public final class Fit {
    public final double left, top, width, height;
    public final int frameWidth, frameHeight;
    public Fit(int viewWidth, int viewHeight, int fw, int fh) {
        if (viewWidth <= 0 || viewHeight <= 0 || fw <= 0 || fh <= 0) throw new IllegalArgumentException("dimensions");
        frameWidth=fw; frameHeight=fh;
        double s=Math.min((double)viewWidth/fw,(double)viewHeight/fh);
        width=fw*s; height=fh*s; left=(viewWidth-width)/2; top=(viewHeight-height)/2;
    }
    public boolean contains(double x,double y) { return x>=left && x<left+width && y>=top && y<top+height; }
    public int x(double x) { return Math.max(0,Math.min(frameWidth-1,(int)((x-left)*frameWidth/width))); }
    public int y(double y) { return Math.max(0,Math.min(frameHeight-1,(int)((y-top)*frameHeight/height))); }
}
