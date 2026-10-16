package io.github.q07mirror.phone;
interface IBridge {
    String start(String apkPath, String token, int port, String codec, int maxSize) = 0;
    String status() = 1;
    void stop() = 2;
    void destroy() = 16777114;
}
