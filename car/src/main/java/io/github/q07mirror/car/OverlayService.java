package io.github.q07mirror.car;

import android.app.*;
import android.content.*;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public final class OverlayService extends Service {
    private WindowManager wm;
    private WindowManager.LayoutParams params;
    private MirrorPanel panel;
    @Override public void onCreate() {
        super.onCreate();
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("mirror","手机镜像",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,OverlayService.class).setAction("STOP"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        startForeground(2,new Notification.Builder(this,"mirror").setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("Q07 手机镜像")
            .setContentText("窗口运行中，点此打开设置").setContentIntent(open).setOngoing(true)
            .addAction(new Notification.Action.Builder(null,"关闭",stop).build()).build());
    }
    @Override public int onStartCommand(Intent intent,int flags,int id) {
        if(intent!=null && "STOP".equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if(panel!=null) return START_NOT_STICKY;
        if(!Settings.canDrawOverlays(this)) { MirrorPanel.lastStatus="未获得悬浮窗权限"; stopSelf(); return START_NOT_STICKY; }
        try {
            wm=getSystemService(WindowManager.class);
            android.graphics.Rect bounds=wm.getCurrentWindowMetrics().getBounds();
            SharedPreferences p=getSharedPreferences("mirror",0);
            int defaultW=Math.min(bounds.width()-40,1000),defaultH=Math.min(bounds.height()-60,900);
            int w=Math.min(bounds.width(),Math.max(480,p.getInt("windowW",defaultW)));
            int h=Math.min(bounds.height(),Math.max(360,p.getInt("windowH",defaultH)));
            params=new WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.OPAQUE);
            params.gravity=Gravity.TOP|Gravity.START;
            params.x=Math.max(0,Math.min(bounds.width()-w,p.getInt("windowX",30)));
            params.y=Math.max(0,Math.min(bounds.height()-h,p.getInt("windowY",60)));
            panel=new MirrorPanel(this,this::stopSelf);
            panel.title.setOnTouchListener(new Move(false));
            panel.status.setText("右下方拖动状态栏可调整大小");
            panel.status.setOnTouchListener(new Move(true));
            wm.addView(panel,params);
        } catch(Exception e) { MirrorPanel.lastStatus="悬浮窗启动失败: "+e; stopSelf(); }
        return START_NOT_STICKY;
    }
    private final class Move implements View.OnTouchListener {
        final boolean resize; float x,y; int startX,startY,startW,startH;
        Move(boolean resize) { this.resize=resize; }
        @Override public boolean onTouch(View v,MotionEvent e) {
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN) {
                x=e.getRawX(); y=e.getRawY(); startX=params.x; startY=params.y; startW=params.width; startH=params.height; return true;
            }
            if(e.getActionMasked()==MotionEvent.ACTION_MOVE) {
                android.graphics.Rect b=wm.getCurrentWindowMetrics().getBounds();
                int dx=(int)(e.getRawX()-x),dy=(int)(e.getRawY()-y);
                if(resize) { params.width=Math.max(Math.min(b.width(),panel.dp(420)),Math.min(b.width()-params.x,startW+dx));
                    params.height=Math.max(Math.min(b.height(),panel.dp(260)),Math.min(b.height()-params.y,startH+dy)); }
                else { params.x=Math.max(0,Math.min(b.width()-params.width,startX+dx)); params.y=Math.max(0,Math.min(b.height()-params.height,startY+dy)); }
                wm.updateViewLayout(panel,params); return true;
            }
            if(e.getActionMasked()==MotionEvent.ACTION_UP) save();
            return true;
        }
    }
    private void save() { if(params!=null) getSharedPreferences("mirror",0).edit().putInt("windowX",params.x).putInt("windowY",params.y).putInt("windowW",params.width).putInt("windowH",params.height).apply(); }
    @Override public void onDestroy() {
        if(panel!=null) { panel.stop(); save(); try { wm.removeView(panel); } catch(Exception ignored) {} panel=null; }
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent i) { return null; }
}
