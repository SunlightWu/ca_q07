package io.github.q07mirror.car;

import android.content.Context;
import android.media.*;
import android.view.Surface;
import io.github.q07mirror.shared.Wire;
import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

final class MirrorClient implements AutoCloseable {
    interface Listener {
        void status(String s);
        void size(int w,int h);
        void ready();
    }
    private final Context ctx;
    private final String host, token;
    private final Surface surface;
    private final Listener listener;
    private final AtomicBoolean stopped=new AtomicBoolean();
    private volatile Connection current;
    private Thread worker;
    MirrorClient(Context ctx,String host,String token,Surface surface,Listener listener) {
        this.ctx=ctx.getApplicationContext(); this.host=host; this.token=token; this.surface=surface; this.listener=listener;
    }
    void start() { worker=new Thread(this::loop,"q07-viewer"); worker.start(); }
    private void loop() {
        while(!stopped.get()) {
            Connection c=new Connection(); current=c;
            try {
                listener.size(0,0); listener.status("正在连接手机 "+host+"…");
                c.video=Net.connect(ctx,host,Wire.PORT);
                if(stopped.get()) break;
                byte[] id=Wire.random(16);
                Wire.clientAuth(c.video.getInputStream(),c.video.getOutputStream(),token,Wire.VIDEO,id);
                c.control=Net.connect(ctx,host,Wire.PORT);
                if(stopped.get()) break;
                Wire.clientAuth(c.control.getInputStream(),c.control.getOutputStream(),token,Wire.CONTROL,id);
                c.control.setSoTimeout(0);
                c.startControl();
                DataInputStream in=new DataInputStream(new BufferedInputStream(c.video.getInputStream(),65536));
                String name=Wire.readDeviceName(in), mime=Wire.readCodec(in);
                listener.status("已连接 "+name+"，等待首帧…");
                // No read deadline after initialization: an unchanged screen may legitimately be quiet.
                c.video.setSoTimeout(0);
                int width=0,height=0;
                while(!stopped.get() && !c.closed.get()) {
                    Wire.Packet p=Wire.readPacket(in);
                    if(p.session()) {
                        c.releaseDecoder(); width=p.width; height=p.height; listener.size(width,height);
                        listener.status("视频尺寸 "+width+" × "+height+"，等待画面…");
                    } else {
                        if(width==0) throw new IOException("视频帧缺少 session 元数据");
                        if(c.decoder==null) {
                            if(!p.config) continue;
                            c.decoder=new Decoder(mime,width,height,c);
                        }
                        c.decoder.feed(p);
                    }
                }
            } catch(Exception e) {
                if(!stopped.get()) listener.status("连接中断："+e.getMessage()+"\n2 秒后重试；手机端可查看 scrcpy 日志。");
            } finally { c.close(); c.releaseDecoder(); if(current==c) current=null; }
            if(!stopped.get()) try { Thread.sleep(2000); } catch(InterruptedException ignored) {}
        }
    }
    void send(byte[] event) {
        Connection c=current;
        if(c!=null && c.control!=null && !c.closed.get() && !c.events.offer(event)) {
            listener.status("控制队列拥堵，重新连接以避免触摸卡住"); c.close();
        }
    }
    void key(int code) { send(Wire.key(0,code)); send(Wire.key(1,code)); }
    @Override public void close() { stopped.set(true); Connection c=current; if(c!=null) c.close(); if(worker!=null) worker.interrupt(); }
    private final class Connection {
        volatile Socket video,control;
        volatile Decoder decoder;
        final AtomicBoolean closed=new AtomicBoolean();
        final BlockingQueue<byte[]> events=new ArrayBlockingQueue<>(128);
        Thread sender;
        void startControl() throws IOException {
            final OutputStream out=control.getOutputStream(); final InputStream in=control.getInputStream();
            sender=new Thread(()->{
                try { while(!closed.get()) { byte[] b=events.take(); out.write(b); out.flush(); } }
                catch(Exception ignored) {} finally { close(); }
            },"q07-touch-writer"); sender.start();
            new Thread(()->{
                try { byte[] b=new byte[4096]; while(!closed.get() && in.read(b)!=-1) {} }
                catch(IOException ignored) {} finally { close(); }
            },"q07-control-reader").start();
        }
        void close() { closed.set(true); closeSocket(video); closeSocket(control); if(sender!=null) sender.interrupt(); }
        void releaseDecoder() { Decoder d=decoder; decoder=null; if(d!=null) d.close(); }
    }
    private static void closeSocket(Socket s) { if(s!=null) try { s.close(); } catch(IOException ignored) {} }

    private final class Decoder {
        final MediaCodec codec;
        final Connection owner;
        final Thread output;
        volatile boolean closed;
        Decoder(String mime,int w,int h,Connection owner) throws IOException {
            this.owner=owner;
            MediaFormat format=MediaFormat.createVideoFormat(mime,w,h);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,Wire.MAX_PACKET);
            format.setInteger(MediaFormat.KEY_PRIORITY,0);
            format.setInteger(MediaFormat.KEY_OPERATING_RATE,30);
            codec=MediaCodec.createDecoderByType(mime);
            try { codec.configure(format,surface,null,0); codec.start(); }
            catch(RuntimeException e) { codec.release(); throw e; }
            output=new Thread(()->drain(w,h),"q07-decoder-output"); output.start();
        }
        void feed(Wire.Packet p) throws IOException {
            long deadline=System.nanoTime()+3_000_000_000L;
            while(!closed && !stopped.get() && !owner.closed.get()) {
                int index=codec.dequeueInputBuffer(10000);
                if(index>=0) {
                    ByteBuffer b=codec.getInputBuffer(index);
                    if(b==null || b.capacity()<p.data.length) throw new IOException("解码输入缓冲不足，请降低分辨率");
                    b.clear(); b.put(p.data);
                    codec.queueInputBuffer(index,0,p.data.length,p.pts,p.config?MediaCodec.BUFFER_FLAG_CODEC_CONFIG:0); return;
                }
                if(System.nanoTime()>deadline) throw new IOException("解码器连续 3 秒未接收数据");
            }
            throw new IOException("解码会话结束");
        }
        void drain(int w,int h) {
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo(); boolean first=true;
            try {
                while(!closed && !owner.closed.get()) {
                    int index=codec.dequeueOutputBuffer(info,10000);
                    if(index>=0) {
                        codec.releaseOutputBuffer(index,true);
                        if(first) { first=false; listener.ready(); listener.status("画面已解码 · "+w+" × "+h+"\n点击画面操作手机；返回 / 主页按钮在上方。"); }
                    }
                }
            } catch(Exception e) { if(!closed) { listener.status("解码失败："+e.getMessage()); owner.close(); } }
        }
        void close() {
            closed=true;
            try { output.join(1500); } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
            try { codec.stop(); } catch(Exception ignored) {}
            codec.release();
        }
    }
}
