package io.github.q07mirror.phone;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import io.github.q07mirror.shared.Wire;
import rikka.shizuku.Shizuku;
import java.util.concurrent.*;

public final class MirrorService extends Service {
    public static volatile String currentStatus="尚未启动投屏服务";
    public static volatile boolean running;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
    private volatile IBridge bridge;
    private volatile boolean destroyed;
    private Shizuku.UserServiceArgs args;
    private PowerManager.WakeLock wake;
    private String token, codec; private int size;
    private final Shizuku.OnBinderDeadListener dead=()->{
        currentStatus="Shizuku 已断开。请重新启动 Shizuku，然后再次开始投屏。"; stopSelf();
    };
    private final ServiceConnection connection=new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n,IBinder b) {
            if(destroyed) return;
            bridge=IBridge.Stub.asInterface(b);
            worker.execute(()->{
                try { currentStatus=bridge.start(getApplicationInfo().sourceDir,token,Wire.PORT,codec,size); }
                catch(Exception e) { currentStatus="服务启动失败: "+e; stopSelf(); }
            });
        }
        @Override public void onServiceDisconnected(ComponentName n) {
            bridge=null;
            if(!destroyed) { currentStatus="shell 服务已断开，请重新开始投屏"; stopSelf(); }
        }
    };
    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("mirror","手机投屏",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,MirrorService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        startForeground(1,new Notification.Builder(this,"mirror").setContentTitle("Q07 手机投屏运行中")
            .setContentText("点此查看连接状态；完成后可停止服务")
            .setSmallIcon(android.R.drawable.ic_menu_camera).setContentIntent(open).setOngoing(true)
            .addAction(new Notification.Action.Builder(null,"停止",stop).build()).build());
        Shizuku.addBinderDeadListener(dead);
        worker.scheduleWithFixedDelay(()->{
            IBridge b=bridge;
            if(b!=null && !destroyed) try { currentStatus=b.status(); } catch(Exception e) {
                currentStatus="shell 服务不可用: "+e.getMessage(); stopSelf();
            }
        },1,1,TimeUnit.SECONDS);
    }
    @Override public int onStartCommand(Intent intent,int flags,int id) {
        if(intent!=null && "STOP".equals(intent.getAction())) { currentStatus="已停止"; stopSelf(); return START_NOT_STICKY; }
        if(running) return START_NOT_STICKY;
        try {
            if(!Shizuku.pingBinder() || Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED)
                throw new IllegalStateException("请先启动并授权 Shizuku");
            android.content.SharedPreferences p=getSharedPreferences("mirror",0);
            token=Wire.token(p.getString("token","")); codec=p.getString("codec","h264"); size=p.getInt("size",1280);
            args=new Shizuku.UserServiceArgs(new ComponentName(this,ShellBridge.class))
                .daemon(false).processNameSuffix("mirror_shell").tag("q07-mirror").version(1);
            running=true; currentStatus="正在启动 shell 投屏服务…";
            wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"q07mirror:phone");
            wake.acquire();
            Shizuku.bindUserService(args,connection);
            worker.schedule(()->{ if(bridge==null && !destroyed) { currentStatus="Shizuku 绑定超时，请检查授权后重新开始"; stopSelf(); } },15,TimeUnit.SECONDS);
        } catch(Exception e) { currentStatus=e.getMessage(); stopSelf(); }
        return START_NOT_STICKY;
    }
    @Override public void onDestroy() {
        destroyed=true; running=false;
        Shizuku.removeBinderDeadListener(dead);
        worker.shutdownNow();
        IBridge b=bridge; bridge=null;
        new Thread(()->{
            try { if(b!=null) b.stop(); } catch(Exception ignored) {}
            try { if(args!=null && Shizuku.pingBinder()) Shizuku.unbindUserService(args,connection,true); } catch(Exception ignored) {}
        },"q07-stop").start();
        if(wake!=null && wake.isHeld()) wake.release();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent i) { return null; }
}
