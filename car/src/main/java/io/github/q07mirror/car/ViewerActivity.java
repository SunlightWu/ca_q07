package io.github.q07mirror.car;

import android.app.Activity;
import android.os.Bundle;

public final class ViewerActivity extends Activity {
    private MirrorPanel panel;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        panel=new MirrorPanel(this,this::finish); panel.title.setText("Q07 手机镜像 · 应用内预览"); setContentView(panel);
    }
    @Override public void onStop() { super.onStop(); panel.stop(); if(!isFinishing()) finish(); }
    @Override public void onDestroy() { if(panel!=null) panel.stop(); super.onDestroy(); }
}
