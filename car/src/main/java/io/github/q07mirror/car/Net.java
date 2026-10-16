package io.github.q07mirror.car;

import android.content.Context;
import android.net.*;
import java.io.*;
import java.net.*;
import java.util.*;

final class Net {
    static String address(String text) {
        String v=text.trim(); String[] parts=v.split("\\.",-1);
        if(parts.length!=4) throw new IllegalArgumentException("请填写手机显示的 IPv4 地址，如 192.168.43.123");
        for(String p:parts) if(!p.matches("[0-9]{1,3}") || Integer.parseInt(p)>255) throw new IllegalArgumentException("IP 地址格式不正确");
        return v;
    }
    static Socket connect(Context ctx,String host,int port) throws IOException {
        InetAddress target=InetAddress.getByName(address(host));
        List<Network> networks=new ArrayList<>();
        ConnectivityManager cm=ctx.getSystemService(ConnectivityManager.class);
        // Prefer a real non-default route covering the target, never an IP string prefix.
        if(cm!=null) for(Network n:cm.getAllNetworks()) {
            LinkProperties lp=cm.getLinkProperties(n);
            if(lp!=null) for(RouteInfo r:lp.getRoutes()) if(!r.isDefaultRoute() && r.matches(target)) { networks.add(n); break; }
        }
        networks.add(null); // Hotspot interfaces are not always represented by a Network.
        IOException last=null;
        for(Network n:networks) {
            Socket s=new Socket();
            try { if(n!=null) n.bindSocket(s); s.setTcpNoDelay(true); s.setKeepAlive(true);
                s.connect(new InetSocketAddress(target,port),3000); s.setSoTimeout(7000); return s;
            } catch(IOException e) { last=e; try { s.close(); } catch(IOException ignored) {} }
        }
        throw new IOException("无法连接手机 "+host+":"+port+"。检查热点、IP 和手机服务。",last);
    }
}
