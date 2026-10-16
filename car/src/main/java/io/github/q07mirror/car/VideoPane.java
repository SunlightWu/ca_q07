package io.github.q07mirror.car;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.FrameLayout;
import io.github.q07mirror.shared.*;

final class VideoPane extends FrameLayout implements TextureView.SurfaceTextureListener {
    interface Host { void surface(Surface s); void lost(); }
    private final TextureView texture;
    private final Host host;
    private Surface surface;
    private int fw,fh;
    private Fit fit;
    private boolean ready,down;
    private int pointer=-1,lastX,lastY;
    MirrorClient client;
    VideoPane(Context c,Host host) {
        super(c); this.host=host; setBackgroundColor(Color.BLACK);
        texture=new TextureView(c); texture.setSurfaceTextureListener(this);
        addView(texture,new LayoutParams(-1,-1,Gravity.CENTER));
        setKeepScreenOn(true);
    }
    void size(int w,int h) { cancel(); ready=false; fw=w; fh=h; fit=null; update(); }
    void ready() { ready=true; }
    private void update() {
        if(fw<=0 || fh<=0 || getWidth()==0 || getHeight()==0) return;
        fit=new Fit(getWidth(),getHeight(),fw,fh);
        LayoutParams lp=new LayoutParams(Math.max(1,(int)Math.round(fit.width)),Math.max(1,(int)Math.round(fit.height)),Gravity.CENTER);
        texture.setLayoutParams(lp);
    }
    @Override protected void onSizeChanged(int w,int h,int ow,int oh) { super.onSizeChanged(w,h,ow,oh); cancel(); update(); }
    private void cancel() {
        if(down && client!=null && fw>0 && fh>0) client.send(Wire.touch(MotionEvent.ACTION_CANCEL,0,lastX,lastY,fw,fh));
        down=false; pointer=-1;
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent e) { return true; }
    @Override public boolean onTouchEvent(MotionEvent e) {
        if(!ready || fit==null || client==null) return true;
        int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN) {
            if(!fit.contains(e.getX(),e.getY())) return true;
            pointer=e.getPointerId(0); down=true;
        }
        if(!down) return true;
        int index=e.findPointerIndex(pointer);
        if(index<0) { cancel(); return true; }
        if(action==MotionEvent.ACTION_POINTER_DOWN) return true; // First version deliberately supports one finger.
        if(action==MotionEvent.ACTION_POINTER_UP) {
            if(e.getPointerId(e.getActionIndex())!=pointer) return true;
            action=MotionEvent.ACTION_UP;
        }
        lastX=fit.x(e.getX(index)); lastY=fit.y(e.getY(index));
        client.send(Wire.touch(action,0,lastX,lastY,fw,fh));
        if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL) { down=false; pointer=-1; }
        return true;
    }
    @Override public void onSurfaceTextureAvailable(SurfaceTexture t,int w,int h) { surface=new Surface(t); host.surface(surface); }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture t) {
        cancel(); host.lost(); if(surface!=null) surface.release(); surface=null; return true;
    }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture t,int w,int h) {}
    @Override public void onSurfaceTextureUpdated(SurfaceTexture t) {}
}
