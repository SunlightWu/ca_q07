package io.github.q07mirror.car;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.widget.*;
import io.github.q07mirror.shared.Wire;
import java.net.Socket;

public final class MainActivity extends Activity {
    private EditText ip,token;
    private TextView status;
    private Button probe;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this); LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(32,20,32,32); scroll.addView(root); setContentView(scroll);
        label(root,"Q07 · 手机镜像",28);
        label(root,"先在手机启动投屏服务，再填写手机显示的 IP 和连接码。\n建议先用应用内预览确认画面和触摸，再打开悬浮窗。",17);
        SharedPreferences p=getSharedPreferences("mirror",0);
        label(root,"手机 IP",16); ip=new EditText(this); ip.setSingleLine(true); ip.setInputType(InputType.TYPE_CLASS_PHONE); ip.setHint("例如 192.168.43.123"); ip.setText(p.getString("host","")); root.addView(ip);
        label(root,"手机连接码",16); token=new EditText(this); token.setSingleLine(true); token.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS); token.setHint("16 位，可带空格"); token.setText(p.getString("token","")); root.addView(token);
        probe=button(root,"1. 测试网络和连接码",this::probe);
        button(root,"2. 应用内预览 / 反控测试",()->{
            if(!save()) return; stopService(new Intent(this,OverlayService.class)); startActivity(new Intent(this,ViewerActivity.class));
        });
        button(root,"3. 授予悬浮窗权限",()->{
            try { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()))); }
            catch(Exception e) { status.setText("车机未提供权限页面。可通过 ADB 执行：\nappops set "+getPackageName()+" SYSTEM_ALERT_WINDOW allow"); }
        });
        button(root,"4. 打开镜像悬浮窗",()->{
            if(!save()) return;
            if(!Settings.canDrawOverlays(this)) { status.setText("请先授予悬浮窗权限；也可先使用应用内预览。"); return; }
            startForegroundService(new Intent(this,OverlayService.class));
        });
        button(root,"关闭悬浮窗",()->stopService(new Intent(this,OverlayService.class)));
        button(root,"复制诊断信息",()->{
            String t="Q07 car 0.1.0\n"+getPackageName()+"\nAndroid "+Build.VERSION.RELEASE+"\n手机 IP: "+ip.getText()+"\n悬浮窗权限: "+Settings.canDrawOverlays(this)+"\n"+status.getText()+"\n"+MirrorPanel.lastStatus;
            getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Q07诊断",t)); Toast.makeText(this,"已复制诊断信息（不含连接码）",Toast.LENGTH_SHORT).show();
        });
        status=label(root,"尚未测试连接",16); status.setTextIsSelectable(true);
        label(root,"窗口操作：拖动标题移动，拖动底部状态栏缩放；画面区域操作手机。\n仅支持单指操作。手机 IP 变化后需重新填写。\n安装包："+getPackageName()+"\nscrcpy 协议："+Wire.SCRCPY_VERSION,14);
    }
    private boolean save() {
        try { getSharedPreferences("mirror",0).edit().putString("host",Net.address(ip.getText().toString())).putString("token",Wire.token(token.getText().toString())).apply(); return true; }
        catch(Exception e) { status.setText(e.getMessage()); return false; }
    }
    private void probe() {
        if(!save()) return;
        String host=ip.getText().toString().trim(),code=token.getText().toString(); probe.setEnabled(false); status.setText("正在测试 TCP 与连接码…");
        new Thread(()->{
            String result;
            try(Socket s=Net.connect(getApplicationContext(),host,Wire.PORT)) {
                Wire.clientAuth(s.getInputStream(),s.getOutputStream(),code,Wire.PROBE,Wire.random(16));
                result="网络与连接码验证通过。下一步点“应用内预览”，验证采集、解码与触摸。";
            } catch(Exception e) { result="连接测试失败："+e.getMessage(); }
            final String text=result;
            runOnUiThread(()->{ if(!isDestroyed()) { status.setText(text); probe.setEnabled(true); } });
        },"q07-probe").start();
    }
    private TextView label(LinearLayout root,String text,int sp) { TextView v=new TextView(this); v.setText(text); v.setTextSize(sp); v.setPadding(0,12,0,12); root.addView(v); return v; }
    private Button button(LinearLayout root,String text,Runnable r) { Button b=new Button(this); b.setText(text); b.setAllCaps(false); b.setOnClickListener(v->r.run()); root.addView(b,new LinearLayout.LayoutParams(-1,-2)); return b; }
}
