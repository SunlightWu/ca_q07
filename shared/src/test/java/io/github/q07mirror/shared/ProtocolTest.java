package io.github.q07mirror.shared;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.concurrent.*;

public class ProtocolTest {
    private DataInputStream stream(byte[] b) { return new DataInputStream(new ByteArrayInputStream(b)); }
    @Test public void readsV5SessionThenConfigAndKeyFrameWithoutLosingAlignment() throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(); DataOutputStream out=new DataOutputStream(bytes);
        out.writeInt(0x80000000); out.writeInt(720); out.writeInt(1280);
        out.writeLong(1L<<62); out.writeInt(4); out.write(new byte[]{0,0,0,1});
        out.writeLong((1L<<61)|123456L); out.writeInt(3); out.write(new byte[]{1,2,3});
        out.writeInt(0x80000001); out.writeInt(1280); out.writeInt(720);
        DataInputStream in=stream(bytes.toByteArray());
        Wire.Packet p=Wire.readPacket(in); assertTrue(p.session()); assertEquals(720,p.width); assertEquals(1280,p.height);
        p=Wire.readPacket(in); assertTrue(p.config); assertFalse(p.session()); assertEquals(0,p.pts);
        p=Wire.readPacket(in); assertTrue(p.key); assertEquals(123456,p.pts); assertArrayEquals(new byte[]{1,2,3},p.data);
        p=Wire.readPacket(in); assertTrue(p.session()); assertEquals(1280,p.width); assertEquals(-1,in.read());
    }
    @Test public void fragmentedReadsAreHandled() throws Exception {
        byte[] data=ByteBuffer.allocate(16).putLong(42).putInt(4).putInt(123).array();
        InputStream fragmented=new FilterInputStream(new ByteArrayInputStream(data)) {
            @Override public int read(byte[] b,int off,int len) throws IOException { return super.read(b,off,Math.min(1,len)); }
        };
        assertEquals(42,Wire.readPacket(new DataInputStream(fragmented)).pts);
    }
    @Test public void rejectsOversizedNegativeAndTruncatedPayloads() {
        for(int size:new int[]{-1,0,Wire.MAX_PACKET+1,5}) {
            byte[] b=ByteBuffer.allocate(13).putLong(1).putInt(size).put((byte)0).array();
            assertThrows(IOException.class,()->Wire.readPacket(stream(b)));
        }
    }
    @Test public void rejectsInvalidDimensionsAndSessionFlags() {
        for(int[] x:new int[][]{{0x80000000,0,720},{0x80000000,9000,720},{0x80000002,1280,720}})
            assertThrows(IOException.class,()->Wire.readPacket(stream(ByteBuffer.allocate(12).putInt(x[0]).putInt(x[1]).putInt(x[2]).array())));
    }
    @Test public void touchWireMatchesUpstream32ByteLayout() throws Exception {
        DataInputStream d=stream(Wire.touch(0,0,100,200,720,1280));
        assertEquals(2,d.readUnsignedByte()); assertEquals(0,d.readUnsignedByte()); assertEquals(0,d.readLong());
        assertEquals(100,d.readInt()); assertEquals(200,d.readInt()); assertEquals(720,d.readUnsignedShort()); assertEquals(1280,d.readUnsignedShort());
        assertEquals(65535,d.readUnsignedShort()); assertEquals(0,d.readInt()); assertEquals(0,d.readInt()); assertEquals(-1,d.read());
        d=stream(Wire.touch(1,0,-10,9000,720,1280)); d.skipBytes(10);
        assertEquals(0,d.readInt()); assertEquals(1279,d.readInt()); d.skipBytes(4); assertEquals(0,d.readUnsignedShort());
    }
    @Test public void keyWireMatchesUpstream14ByteLayout() throws Exception {
        DataInputStream d=stream(Wire.key(1,3)); assertEquals(0,d.readUnsignedByte()); assertEquals(1,d.readUnsignedByte());
        assertEquals(3,d.readInt()); assertEquals(0,d.readInt()); assertEquals(0,d.readInt()); assertEquals(-1,d.read());
    }
    @Test public void deviceAndCodecHeaders() throws Exception {
        byte[] name=new byte[64]; System.arraycopy("Magic5".getBytes("UTF-8"),0,name,0,6);
        assertEquals("Magic5",Wire.readDeviceName(stream(name)));
        assertEquals("video/avc",Wire.readCodec(stream(new byte[]{0x68,0x32,0x36,0x34})));
        assertEquals("video/hevc",Wire.readCodec(stream(new byte[]{0x68,0x32,0x36,0x35})));
        assertThrows(IOException.class,()->Wire.readCodec(stream(new byte[]{0,0,0,1})));
    }
    @Test public void mutualAuthenticationRoundTripAndWrongCode() throws Exception {
        for(boolean wrong:new boolean[]{false,true}) {
            ExecutorService worker=Executors.newSingleThreadExecutor();
            try(ServerSocket server=new ServerSocket(0,1,InetAddress.getLoopbackAddress())) {
                Future<Wire.Hello> result=worker.submit(()->{ try(Socket s=server.accept()) { s.setSoTimeout(3000); return Wire.serverAuth(s.getInputStream(),s.getOutputStream(),"0123456789ABCDEF"); } });
                byte[] session=Wire.random(16);
                try(Socket s=new Socket(InetAddress.getLoopbackAddress(),server.getLocalPort())) {
                    s.setSoTimeout(3000);
                    if(wrong) assertThrows(IOException.class,()->Wire.clientAuth(s.getInputStream(),s.getOutputStream(),"FEDCBA9876543210",Wire.VIDEO,session));
                    else { Wire.clientAuth(s.getInputStream(),s.getOutputStream(),"0123 4567 89ab cdef",Wire.VIDEO,session);
                        Wire.Hello h=result.get(4,TimeUnit.SECONDS); assertEquals(Wire.VIDEO,h.channel); assertArrayEquals(session,h.session); }
                }
                if(wrong) assertThrows(ExecutionException.class,()->result.get(4,TimeUnit.SECONDS));
            } finally { worker.shutdownNow(); }
        }
    }
    @Test public void tokenNormalization() {
        assertEquals("0123456789ABCDEF",Wire.token("0123-4567-89ab-cdef"));
        assertEquals(16,Wire.newToken().length());
        assertThrows(IllegalArgumentException.class,()->Wire.token("123456"));
    }
}
