package io.github.q07mirror.phone;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.*;
import android.view.*;

public final class GridActivity extends Activity {
    @Override public void onCreate(Bundle b) { super.onCreate(b); getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); setContentView(new Grid()); }
    private final class Grid extends View {
        final Paint p=new Paint(3); float x=-1,y=-1; String event="在车机点击 / 滑动，检查落点";
        Grid() { super(GridActivity.this); }
        @Override protected void onDraw(Canvas c) {
            c.drawColor(Color.rgb(12,22,34)); p.setStrokeWidth(2); p.setColor(Color.rgb(62,88,110));
            for(int i=0;i<=6;i++) c.drawLine(i*getWidth()/6f,0,i*getWidth()/6f,getHeight(),p);
            for(int i=0;i<=8;i++) c.drawLine(0,i*getHeight()/8f,getWidth(),i*getHeight()/8f,p);
            p.setTextSize(30); p.setColor(Color.WHITE);
            c.drawText(event,20,65,p); c.drawText(getWidth()+" × "+getHeight()+" · 旋转手机测试方向",20,110,p);
            if(x>=0) { p.setColor(Color.CYAN); c.drawCircle(x,y,24,p); c.drawText("("+(int)x+", "+(int)y+")",Math.min(x+28,getWidth()-220),Math.max(150,y-25),p); }
        }
        @Override public boolean onTouchEvent(MotionEvent e) { x=e.getX(); y=e.getY(); event=MotionEvent.actionToString(e.getActionMasked()); invalidate(); return true; }
    }
}
