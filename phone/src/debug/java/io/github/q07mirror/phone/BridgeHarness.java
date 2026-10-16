package io.github.q07mirror.phone;

/** Emulator-only CLI harness. Requires an already authorized ADB shell; not an exported component. */
public final class BridgeHarness {
    public static void main(String[] args) throws Exception {
        if(args.length!=2) throw new IllegalArgumentException("APK path and test connection code required");
        android.os.Looper.prepareMainLooper();
        ShellBridge bridge=new ShellBridge();
        System.out.println(bridge.start(args[0],args[1],27183,"h264",1280));
        Runtime.getRuntime().addShutdownHook(new Thread(bridge::stop));
        android.os.Looper.loop();
    }
}
