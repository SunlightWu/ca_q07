# Q07 手机镜像 · 可上车测试的初版

手机：荣耀 Magic5 / Android 16。车机：已运行参考项目的 Q07 / Android 11。

本目录是新建的独立工程，没有修改 `D:\test\q07cam-main` 中的参考项目。车机测试版包名为 `com.changan.acscenter`，不使用原项目的 `de.dsa.ford.otaflash`。

## 直接测试

构建完成后的文件放在 `dist/`：

| 文件 | 用途 |
|---|---|
| `q07-mirror-phone.apk` | 安装到荣耀手机 |
| `q07-mirror-car-q07.apk` | Q07 专用测试包，安装前检查包名是否空闲 |
| `q07-mirror-car-standard.apk` | 普通 Android 手机/平板接收端，用于与车机解耦排查 |
| `TESTING.md` | 按顺序操作的实车测试指南 |

先读 **[测试指南](docs/TESTING.md)**。该版本是设备验证用原型，不代表荣耀 Magic5 + Q07 组合已实测通过。

## 已实现

- 手机使用 Shizuku `UserService`，以 shell 身份启动官方 scrcpy 5.0.1；不依赖已废弃的 `newProcess()`。
- 官方服务端随 APK 打包，无需运行时下载；固定版本、SHA-256 校验。
- 手机本机 `TCP ↔ LocalSocket` 转发，复用官方采集、编码和输入注入。
- 16 位连接码，随机挑战 HMAC 双向认证；视频/控制通道绑定同一会话和对端 IP。
- 手机界面显示局域网地址、Shizuku/服务状态、服务端日志，并提供坐标网格。
- 车机网络诊断、应用内预览、可拖动/缩放的悬浮窗。
- H.264/H.265 MediaCodec 解码、scrcpy 5 的 session/旋转处理。
- 单指点击/滑动、返回/主页、黑边排除和坐标转换；控制发送不阻塞 UI。
- 断连后重试、停止入口、资源释放和连接诊断复制。
- 独立协议与坐标单元测试。

## 目录

```text
phone/    手机 App、Shizuku shell bridge、官方 server 资产
car/      车机 App、MediaCodec、悬浮窗、应用内预览
shared/   纯 Java 协议解析、认证、控制编码、坐标映射和测试
tools/    构建、安全检查后安装、模拟器冒烟测试辅助
docs/     架构、测试步骤与验证记录
vendor/   上游版本元数据、协议源码参考、许可证
dist/     交付 APK、测试指南、校验值（构建生成）
```

## 构建

需要 JDK 17+、Android SDK Platform 35；首次构建需要联网下载 Gradle/AGP/Shizuku/JUnit 依赖。

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\build.ps1
# 缓存齐全后离线构建
powershell -ExecutionPolicy Bypass -File .\tools\build.ps1 -Offline
# 其他电脑可明确指定路径
powershell -ExecutionPolicy Bypass -File .\tools\build.ps1 -JavaHome 'C:\path\to\jdk' -SdkRoot 'C:\path\to\Android\Sdk'
```

Android Studio 也可以直接打开此目录。先运行脚本一次，生成本地签名密钥与 `local.properties`。

`.tools/debug.keystore` 是本工程的开发签名密钥，请保留，后续更新 APK 要使用同一密钥。不要用此测试密钥做公开发布。

## 当前边界

- Shizuku 激活和授权需要用户完成；非 root 手机重启后需重新启动 Shizuku。
- 第一版无音频、多指、自动发现、开机自动投屏、副驾显示、远程剪贴板或远程文字输入。
- 目前采用手填手机 IP + 连接码，IP 变化需更新；两种热点方向都可测试。
- 自动重连在 TCP 断开被检测到后执行。系统长时间保留失效连接时，可用“重连”按钮或停止再启动。
- 连接认证不是视频加密。只在自己的有密码热点中使用；不做互联网端口映射。
- 悬浮窗只保证普通应用窗口层级，不保证覆盖系统受保护界面；受保护手机画面可能黑屏。
- Android 16 荣耀系统的注入权限、后台限制、热点路由、Q07 实际解码延迟都需要真机验证。

设计和协议见 [ARCHITECTURE.md](docs/ARCHITECTURE.md)，本地验证结果见 [VALIDATION.md](docs/VALIDATION.md)。
