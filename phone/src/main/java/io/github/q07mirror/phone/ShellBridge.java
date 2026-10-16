package io.github.q07mirror.phone;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Process;
import io.github.q07mirror.shared.Wire;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipFile;

/** Runs as Shizuku shell UID. Only this process accesses the scrcpy abstract sockets. */
public final class ShellBridge extends IBridge.Stub {
    private volatile ServerSocket listener;
    private volatile Socket authenticating;
    private volatile Session active;
    private File serverFile, workDir;
    private volatile String state = "未启动";
    private final StringBuilder logs = new StringBuilder();
    private String token, codec;
    private int maxSize;
    public ShellBridge() {}

    private synchronized void log(String s) {
        logs.append(s).append('\n');
        if (logs.length() > 10000) logs.delete(0, logs.length()-8000);
    }
    @Override public synchronized String start(String apkPath, String code, int port, String codec, int size) {
        stop(); logs.setLength(0);
        try {
            token=Wire.token(code);
            if (port < 1024 || port > 65535 || !(codec.equals("h264") || codec.equals("h265")) || size < 640 || size > 2560)
                throw new IOException("参数错误");
            this.codec=codec; maxSize=size;
            if (Process.myUid()!=2000 && Process.myUid()!=0) throw new IOException("Shizuku 服务不是 shell/root 身份");
            workDir = new File("/data/local/tmp", "q07mirror-"+UUID.randomUUID());
            if (!workDir.mkdir()) throw new IOException("不能创建 shell 工作目录");
            serverFile = new File(workDir,"server.jar");
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try (ZipFile apk=new ZipFile(apkPath);
                 InputStream in=apk.getInputStream(apk.getEntry("assets/scrcpy-server.jar"));
                 OutputStream out=new FileOutputStream(serverFile)) {
                byte[] b=new byte[32768]; int n;
                while ((n=in.read(b))!=-1) { out.write(b,0,n); digest.update(b,0,n); }
            }
            StringBuilder hex=new StringBuilder();
            for(byte b:digest.digest()) hex.append(String.format(Locale.ROOT,"%02x",b&255));
            if (!hex.toString().equals("764eb6f79811d5211fe9df341120882ba9994c7a61b897d7bf3fb662e53bc536"))
                throw new IOException("scrcpy 文件校验失败");
            if (!serverFile.setReadOnly()) throw new IOException("无法设置服务端只读权限");
            ServerSocket ss=new ServerSocket(); ss.setReuseAddress(true); ss.bind(new InetSocketAddress(port));
            listener=ss; state="等待车机连接 · TCP "+port+" · uid="+Process.myUid();
            new Thread(()->acceptLoop(ss),"q07-accept").start();
            return state;
        } catch(Exception e) { stop(); state="启动失败: "+e; log(state); return state; }
    }
    private void acceptLoop(ServerSocket ss) {
        while(listener==ss) {
            Socket socket=null;
            try {
                socket=ss.accept(); authenticating=socket;
                socket.setTcpNoDelay(true); socket.setKeepAlive(true); socket.setSoTimeout(5000);
                Wire.Hello h=Wire.serverAuth(socket.getInputStream(),socket.getOutputStream(),token);
                if (listener!=ss) throw new IOException("服务已停止");
                socket.setSoTimeout(0);
                if (h.channel==Wire.PROBE) { close(socket); continue; }
                synchronized(this) {
                    if(h.channel==Wire.VIDEO) {
                        if(active!=null) active.close();
                        Session s=new Session(socket,h.session); active=s;
                        state="视频通道已验证，等待控制通道";
                        new Thread(()->{
                            try { Thread.sleep(10000); } catch(InterruptedException ignored) {}
                            if(!s.started.get()) s.close();
                        },"q07-pair-timeout").start();
                    } else {
                        Session s=active;
                        if(s==null || s.closed.get() || s.control!=null || !Arrays.equals(h.session,s.id)
                            || !socket.getInetAddress().equals(s.video.getInetAddress())) throw new IOException("控制会话不匹配");
                        s.control=socket; s.started.set(true);
                        new Thread(s::run,"q07-session").start();
                    }
                }
                socket=null;
            } catch(Exception e) { if(listener==ss) log("连接: "+e.getMessage()); }
            finally { close(socket); authenticating=null; }
        }
    }
    @Override public synchronized String status() { return state+"\n"+logs; }
    @Override public synchronized void stop() {
        ServerSocket old=listener; listener=null; close(old); close(authenticating);
        if(active!=null) { active.close(); active=null; }
        if(serverFile!=null) { serverFile.delete(); serverFile=null; }
        if(workDir!=null) { workDir.delete(); workDir=null; }
        state="已停止";
    }
    @Override public void destroy() { stop(); System.exit(0); }
    private static void close(Closeable c) { if(c!=null) try { c.close(); } catch(IOException ignored) {} }

    private final class Session {
        final Socket video; final byte[] id;
        volatile Socket control;
        volatile LocalSocket localVideo, localControl;
        volatile java.lang.Process process;
        final AtomicBoolean closed=new AtomicBoolean(), started=new AtomicBoolean();
        Session(Socket v,byte[] id) { video=v; this.id=id; }
        void run() {
            try {
                int scid=new java.security.SecureRandom().nextInt() & 0x7fffffff;
                String name=String.format(Locale.ROOT,"scrcpy_%08x",scid);
                File jar=serverFile;
                if(closed.get() || jar==null) return;
                ProcessBuilder pb=new ProcessBuilder("/system/bin/app_process","/","com.genymobile.scrcpy.Server",Wire.SCRCPY_VERSION,
                    "scid="+Integer.toHexString(scid),"tunnel_forward=true","audio=false","control=true",
                    "video_codec="+codec,"max_size="+maxSize,"max_fps=30","video_bit_rate=4000000",
                    "send_dummy_byte=false","clipboard_autosync=false","cleanup=false","power_on=false");
                pb.environment().put("CLASSPATH",jar.getAbsolutePath()); pb.redirectErrorStream(true);
                process=pb.start();
                if(closed.get()) { process.destroy(); return; }
                new Thread(()->{
                    try(BufferedReader r=new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line; while((line=r.readLine())!=null) log(line);
                    } catch(IOException ignored) {}
                },"q07-server-log").start();
                localVideo=connectLocal(name);
                localControl=connectLocal(name);
                if(closed.get()) { close(); return; }
                state="正在投屏 · "+codec+" · "+video.getInetAddress().getHostAddress();
                pump(localVideo.getInputStream(),video.getOutputStream(),"q07-video");
                pump(control.getInputStream(),localControl.getOutputStream(),"q07-control-up");
                pump(localControl.getInputStream(),control.getOutputStream(),"q07-control-down");
                // Detect a disconnected viewer even when its screen is static.
                new Thread(()->{ try { while(video.getInputStream().read()!=-1) {} } catch(IOException ignored) {} finally { close(); } },"q07-video-eof").start();
                int exit=process.waitFor();
                if(!closed.get()) log("scrcpy 退出: "+exit);
            } catch(Exception e) { if(!closed.get()) log("投屏失败: "+e); }
            finally { close(); }
        }
        LocalSocket connectLocal(String name) throws Exception {
            long deadline=System.nanoTime()+8_000_000_000L;
            while(!closed.get() && System.nanoTime()<deadline) {
                LocalSocket s=new LocalSocket();
                try { s.connect(new LocalSocketAddress(name,LocalSocketAddress.Namespace.ABSTRACT));
                    if(closed.get()) { s.close(); throw new IOException("会话已停止"); } return s;
                } catch(IOException e) { s.close(); if(process!=null && !process.isAlive()) throw new IOException("scrcpy 已退出，请看手机日志"); Thread.sleep(80); }
            }
            throw new IOException("scrcpy 本地通道启动超时");
        }
        void pump(InputStream in,OutputStream out,String name) {
            new Thread(()->{ try { byte[] b=new byte[65536]; int n; while(!closed.get() && (n=in.read(b))!=-1) out.write(b,0,n); }
                catch(IOException ignored) {} finally { close(); } },name).start();
        }
        void close() {
            boolean first=closed.compareAndSet(false,true);
            ShellBridge.close(video); ShellBridge.close(control); ShellBridge.close(localVideo); ShellBridge.close(localControl);
            java.lang.Process p=process; if(p!=null) p.destroy();
            if(first && active==this && listener!=null) state="车机已断开，等待重新连接";
        }
    }
}
