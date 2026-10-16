package io.github.q07mirror.car;

import android.content.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;

final class MirrorPanel extends LinearLayout implements VideoPane.Host {
    static volatile String lastStatus="尚未连接";
    final TextView title,status;
    final VideoPane pane;
    private MirrorClient client;
    MirrorPanel(Context ctx,Runnable close) {
        super(ctx); setOrientation(VERTICAL); setBackgroundColor(Color.rgb(22,29,40));
        LinearLayout bar=new LinearLayout(ctx); bar.setGravity(Gravity.CENTER_VERTICAL);
        title=new TextView(ctx); title.setText("Q07 手机镜像 · 拖此处移动"); title.setTextColor(Color.WHITE); title.setTextSize(16); title.setPadding(12,8,6,8);
        bar.addView(title,new LinearLayout.LayoutParams(0,-1,1));
        addButton(bar,"返回",()->{ if(client!=null) client.key(KeyEvent.KEYCODE_BACK); });
        addButton(bar,"主页",()->{ if(client!=null) client.key(KeyEvent.KEYCODE_HOME); });
        addButton(bar,"重连",()->{ if(surface!=null) start(surface); });
        addButton(bar,"关闭",close);
        addView(bar,new LinearLayout.LayoutParams(-1,dp(52)));
        pane=new VideoPane(ctx,this); addView(pane,new LinearLayout.LayoutParams(-1,0,1));
        status=new TextView(ctx); status.setTextColor(Color.LTGRAY); status.setTextSize(13); status.setPadding(12,4,12,4); status.setMaxLines(2);
        addView(status,new LinearLayout.LayoutParams(-1,dp(44)));
    }
    private Surface surface;
    @Override public void surface(Surface s) { surface=s; start(s); }
    @Override public void lost() { surface=null; stop(); }
    private void start(Surface s) {
        stop();
        SharedPreferences prefs=getContext().getSharedPreferences("mirror",0);
        Listener listener=new Listener();
        client=new MirrorClient(getContext(),prefs.getString("host",""),prefs.getString("token",""),s,listener);
        listener.owner=client; pane.client=client; client.start();
    }
    private final class Listener implements MirrorClient.Listener {
        MirrorClient owner;
        @Override public void status(String text) { post(()->{ if(client==owner) { status.setText(text); lastStatus=text; } }); }
        @Override public void size(int w,int h) { post(()->{ if(client==owner) pane.size(w,h); }); }
        @Override public void ready() { post(()->{ if(client==owner) pane.ready(); }); }
    }
    void stop() { MirrorClient old=client; client=null; pane.client=null; if(old!=null) old.close(); }
    private void addButton(LinearLayout bar,String label,Runnable action) {
        Button b=new Button(getContext()); b.setText(label); b.setTextSize(14); b.setMinWidth(0); b.setMinimumWidth(0); b.setPadding(6,0,6,0);
        b.setOnClickListener(v->action.run()); bar.addView(b,new LinearLayout.LayoutParams(dp(64),-1));
    }
    int dp(int n) { return (int)(n*getResources().getDisplayMetrics().density+.5f); }
}
