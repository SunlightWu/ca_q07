package io.github.q07mirror.phone;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import io.github.q07mirror.shared.Wire;
import rikka.shizuku.Shizuku;
import java.net.*;
import java.util.*;

public final class MainActivity extends Activity {
    private final Handler ui=new Handler(Looper.getMainLooper());
    private TextView status,addresses,code;
    private Spinner codec,size;
    private boolean pendingStart;
    private final Shizuku.OnRequestPermissionResultListener permission=(request,result)->{
        if(request==7) {
            if(result==PackageManager.PERMISSION_GRANTED && pendingStart) startMirror();
            else if(result!=PackageManager.PERMISSION_GRANTED) toast("未授予 Shizuku 权限");
            pendingStart=false;
        }
    };
    private final Runnable refresh=new Runnable() {
        @Override public void run() { status.setText(MirrorService.currentStatus); addresses.setText(addresses()); ui.postDelayed(this,1500); }
    };
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        SharedPreferences prefs=getSharedPreferences("mirror",0);
        if(!prefs.contains("token")) prefs.edit().putString("token",Wire.newToken()).apply();
        ScrollView scroll=new ScrollView(this); LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28,24,28,36); scroll.addView(root); setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets s=insets.getInsets(WindowInsets.Type.systemBars()); v.setPadding(s.left,s.top,s.right,s.bottom); return insets;
        });
        label(root,"Q07 · 手机投屏",28);
        label(root,"荣耀 Magic5 / Android 16 测试版\n先连接车机热点，启动 Shizuku，再开始投屏。",16);
        addresses=label(root,"",18);
        label(root,"车机连接码（输入一次后记住）",15);
        code=label(root,formatCode(prefs.getString("token","")),25); code.setTextIsSelectable(true);
        label(root,"视频编码",15);
        codec=new Spinner(this); codec.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"H.264 · 默认","H.265 · 对比测试"})); root.addView(codec);
        codec.setSelection("h265".equals(prefs.getString("codec","h264"))?1:0);
        label(root,"视频长边",15);
        size=new Spinner(this); size.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"1280 · 先验证","1920 · 更清晰"})); root.addView(size);
        size.setSelection(prefs.getInt("size",1280)==1920?1:0);
        button(root,"1. 打开 / 安装 Shizuku",()->{
            Intent i=getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
            startActivity(i!=null?i:new Intent(Intent.ACTION_VIEW,Uri.parse("https://shizuku.rikka.app/download/")));
        });
        button(root,"2. 授权并开始投屏",this::authorize);
        button(root,"停止投屏",()->{ MirrorService.currentStatus="已停止"; stopService(new Intent(this,MirrorService.class)); });
        button(root,"打开触摸测试网格",()->startActivity(new Intent(this,GridActivity.class)));
        button(root,"打开开发者选项",()->{ try { startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)); } catch(Exception e) { toast("请在系统设置中打开开发者选项"); } });
        button(root,"应用设置 / 后台运行",()->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));
        button(root,"更换连接码（先停止投屏）",()->{
            if(MirrorService.running) { toast("请先停止投屏"); return; }
            String c=Wire.newToken(); prefs.edit().putString("token",c).apply(); code.setText(formatCode(c));
        });
        label(root,"运行状态 / 服务端日志",18);
        status=label(root,"",14); status.setTextIsSelectable(true);
        button(root,"复制诊断信息",()->{
            String text="Q07 phone 0.1.0\n"+Build.MANUFACTURER+" "+Build.MODEL+" / Android "+Build.VERSION.RELEASE+"\n"+addresses()+"\n"+MirrorService.currentStatus;
            getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Q07诊断",text)); toast("诊断已复制（不含连接码）");
        });
        label(root,"手机重启后需重新启动 Shizuku。测试期间保持手机解锁；受保护画面可能不显示。更改编码或画质后，停止再开始投屏。",14);
        Shizuku.addRequestPermissionResultListener(permission);
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},8);
    }
    private void authorize() {
        try {
            if(!Shizuku.pingBinder()) { toast("请先在 Shizuku 中通过无线调试启动服务"); return; }
            if(Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED) startMirror();
            else { pendingStart=true; Shizuku.requestPermission(7); }
        } catch(Exception e) { toast(e.toString()); }
    }
    private void startMirror() {
        if(MirrorService.running) { toast("投屏服务已经运行；更改配置需先停止"); return; }
        getSharedPreferences("mirror",0).edit().putString("codec",codec.getSelectedItemPosition()==0?"h264":"h265")
            .putInt("size",size.getSelectedItemPosition()==0?1280:1920).apply();
        startForegroundService(new Intent(this,MirrorService.class));
    }
    private String addresses() {
        StringBuilder s=new StringBuilder("手机局域网 IP（填入车机）：\n"); int count=0;
        try {
            Enumeration<NetworkInterface> all=NetworkInterface.getNetworkInterfaces();
            while(all!=null && all.hasMoreElements()) {
                NetworkInterface n=all.nextElement(); if(!n.isUp() || n.isLoopback()) continue;
                for(InetAddress a:Collections.list(n.getInetAddresses()))
                    if(a instanceof Inet4Address && a.isSiteLocalAddress()) { s.append(n.getName()).append(": ").append(a.getHostAddress()).append('\n'); count++; }
            }
        } catch(Exception e) { s.append(e.getMessage()); }
        if(count==0) s.append("未找到局域网地址，请连接车机热点\n");
        return s.append("端口：").append(Wire.PORT).toString();
    }
    private static String formatCode(String c) { return c.replaceAll("(.{4})(?=.)","$1 "); }
    private TextView label(LinearLayout root,String text,int sp) { TextView v=new TextView(this); v.setText(text); v.setTextSize(sp); v.setPadding(0,12,0,12); root.addView(v); return v; }
    private void button(LinearLayout root,String text,Runnable r) { Button b=new Button(this); b.setText(text); b.setAllCaps(false); b.setOnClickListener(v->r.run()); root.addView(b,new LinearLayout.LayoutParams(-1,-2)); }
    private void toast(String s) { Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
    @Override public void onResume() { super.onResume(); ui.post(refresh); }
    @Override public void onPause() { ui.removeCallbacks(refresh); super.onPause(); }
    @Override public void onDestroy() { Shizuku.removeRequestPermissionResultListener(permission); super.onDestroy(); }
}
