package io.github.q07mirror.shared;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Q07 transport envelope. The authenticated payload is unmodified scrcpy 5.0.1. */
public final class Wire {
    public static final String SCRCPY_VERSION = "5.0.1";
    public static final int PORT = 27183, MAGIC = 0x5130374d, VERSION = 1;
    public static final int PROBE = 0, VIDEO = 1, CONTROL = 2;
    public static final int MAX_PACKET = 4 * 1024 * 1024;
    private static final SecureRandom RANDOM = new SecureRandom();
    private Wire() {}
    public static byte[] random(int n) { byte[] b = new byte[n]; RANDOM.nextBytes(b); return b; }
    public static String newToken() {
        StringBuilder b = new StringBuilder();
        for (byte v : random(8)) b.append(String.format(Locale.ROOT, "%02X", v & 255));
        return b.toString();
    }
    public static String token(String s) {
        String v = s == null ? "" : s.replace(" ", "").replace("-", "").trim().toUpperCase(Locale.ROOT);
        if (!v.matches("[0-9A-F]{16}")) throw new IllegalArgumentException("连接码应为手机显示的 16 位字符");
        return v;
    }
    public static final class Hello {
        public final int channel; public final byte[] session;
        Hello(int c, byte[] s) { channel = c; session = s; }
    }
    private static byte[] header(int c, byte[] s) throws IOException {
        if (s.length != 16 || c < 0 || c > 2) throw new IOException("Invalid hello");
        return ByteBuffer.allocate(25).putInt(MAGIC).putInt(VERSION).put((byte)c).put(s).array();
    }
    private static byte[] mac(String token, byte[] header, byte[] nonce, int role) throws IOException {
        try {
            Mac m = Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(token(token).getBytes(StandardCharsets.US_ASCII), "HmacSHA256"));
            m.update((byte)role); m.update(header); return m.doFinal(nonce);
        } catch (GeneralSecurityException e) { throw new IOException(e); }
    }
    public static void clientAuth(InputStream in, OutputStream out, String token, int channel, byte[] session) throws IOException {
        byte[] h = header(channel, session);
        DataInputStream d = new DataInputStream(in);
        out.write(h); out.flush();
        byte[] nonce = new byte[32]; d.readFully(nonce);
        out.write(mac(token, h, nonce, 0)); out.flush();
        if (d.readUnsignedByte() != 1) throw new IOException("连接码不匹配或协议版本不匹配");
        byte[] proof = new byte[32]; d.readFully(proof);
        if (!MessageDigest.isEqual(proof, mac(token, h, nonce, 1))) throw new IOException("手机身份验证失败");
    }
    public static Hello serverAuth(InputStream in, OutputStream out, String token) throws IOException {
        DataInputStream d = new DataInputStream(in);
        byte[] h = new byte[25]; d.readFully(h);
        ByteBuffer b = ByteBuffer.wrap(h);
        if (b.getInt() != MAGIC || b.getInt() != VERSION) throw new IOException("协议版本不匹配");
        int c = b.get() & 255;
        if (c > CONTROL) throw new IOException("Invalid channel");
        byte[] session = new byte[16]; b.get(session);
        byte[] nonce = random(32); out.write(nonce); out.flush();
        byte[] proof = new byte[32]; d.readFully(proof);
        if (!MessageDigest.isEqual(proof, mac(token, h, nonce, 0))) {
            out.write(0); out.flush(); throw new IOException("连接码错误");
        }
        out.write(1); out.write(mac(token, h, nonce, 1)); out.flush();
        return new Hello(c, session);
    }
    public static String readDeviceName(DataInputStream in) throws IOException {
        byte[] b = new byte[64]; in.readFully(b); int n = 0;
        while (n < b.length && b[n] != 0) n++;
        return new String(b, 0, n, StandardCharsets.UTF_8);
    }
    public static String readCodec(DataInputStream in) throws IOException {
        int id = in.readInt();
        if (id == 0x68323634) return "video/avc";
        if (id == 0x68323635) return "video/hevc";
        throw new IOException("不支持的视频编码: " + Integer.toHexString(id));
    }
    public static final class Packet {
        public final int width, height; public final long pts;
        public final boolean config, key; public final byte[] data;
        Packet(int w, int h, long p, boolean c, boolean k, byte[] d) { width=w; height=h; pts=p; config=c; key=k; data=d; }
        public boolean session() { return data == null; }
    }
    public static Packet readPacket(DataInputStream in) throws IOException {
        int first = in.readInt();
        if ((first & 0x80000000) != 0) {
            if ((first & 0x7ffffffe) != 0) throw new IOException("Invalid session flags");
            int w = in.readInt(), h = in.readInt();
            if (w < 1 || h < 1 || w > 8192 || h > 8192) throw new IOException("Invalid dimensions");
            return new Packet(w,h,0,false,false,null);
        }
        long meta = ((long) first << 32) | (in.readInt() & 0xffffffffL);
        int size = in.readInt();
        if (size < 1 || size > MAX_PACKET) throw new IOException("Invalid packet size: " + size);
        byte[] data = new byte[size]; in.readFully(data);
        return new Packet(0,0,meta & ((1L << 61)-1),(meta & (1L << 62))!=0,(meta & (1L << 61))!=0,data);
    }
    /** Position is in encoded-frame coordinates; scrcpy maps it back to the display. */
    public static byte[] touch(int action, long pointer, int x, int y, int width, int height) {
        if (width < 1 || height < 1 || width > 65535 || height > 65535) throw new IllegalArgumentException("dimensions");
        return ByteBuffer.allocate(32).put((byte)2).put((byte)action).putLong(pointer)
            .putInt(Math.max(0,Math.min(width-1,x))).putInt(Math.max(0,Math.min(height-1,y)))
            .putShort((short)width).putShort((short)height)
            .putShort((short)((action==1 || action==3) ? 0 : 0xffff)).putInt(0).putInt(0).array();
    }
    public static byte[] key(int action, int keycode) {
        return ByteBuffer.allocate(14).put((byte)0).put((byte)action).putInt(keycode).putInt(0).putInt(0).array();
    }
}
