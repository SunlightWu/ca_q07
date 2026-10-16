# Implementation notes

## Components

```text
Phone Activity -> foreground MirrorService -> Shizuku UserService (shell UID)
                                              |
                                              +-> extract/check official scrcpy 5.0.1 asset
                                              +-> app_process (one process per connected viewer)
                                              +-> TCP authenticated video/control -> LocalSocket bridge
Car Activity / overlay -> TCP client -> scrcpy parser -> MediaCodec -> TextureView
                         touch/key queue -> control TCP -> scrcpy Controller
```

The phone normal process never calls restricted input injection APIs. It requests Shizuku permission and binds `ShellBridge`. The user service runs shell code and starts scrcpy with `ProcessBuilder`, without `Shizuku.newProcess()` or command-string interpolation.

## Transport and server lifecycle

One port (27183), separate TCP video and control connections. A probe connection can test routing/auth without starting scrcpy. On video authentication a pending session is created; its control connection must arrive within 10 seconds with the same random session ID and IP. A newly authenticated video session replaces the previous viewer. Only one viewer is supported.

Authentication header: `u32 magic Q07M`, `u32 version 1`, `u8 channel`, `16-byte session ID`. The phone sends a random 32-byte challenge. Client/server prove knowledge of the 16-hex-character token with HMAC-SHA256 over role byte, header and challenge. Constant-time comparison is used. The control connection authenticates separately. Five-second handshake deadline, bounded payload length and control queue.

This protects against clients that do not know the token; it does not encrypt media. No public-network service is intended.

The phone server asset is extracted by the shell process from the installed APK into a unique `/data/local/tmp/q07mirror-*` directory, SHA-256 checked, made read-only, and launched with `CLASSPATH`. This avoids assuming shell can read app-private files. Stop removes only that service's own jar/directory. Abrupt OS termination can leave a small temporary directory; no global cleanup or deletion of unrelated paths occurs.

The bridge connects to the two abstract sockets in video/control order. scrcpy options explicitly disable audio, the dummy byte, automatic clipboard synchronization, power-on changes and cleanup side effects. Socket failure closes both channels and destroys the owned scrcpy process. A process watcher captures server errors.

## Pinned scrcpy 5.0.1 protocol

No dummy byte. Video begins with 64-byte device name and 4-byte codec ID (`h264`/`h265`). Following 12-byte headers have two interpretations:

- Session: MSB=1, first u32 contains session/client-resized flags, then width and height.
- Media: MSB=0, config flag bit 62, keyframe flag bit 61, PTS in low 61 bits, then payload length u32.

Session changes rebuild the decoder and update the touch coordinate space. Config payload is queued with `BUFFER_FLAG_CODEC_CONFIG`; frame PTS is preserved. Decoder input/output run off the UI thread. No arbitrary compressed-frame dropping is used because that would break frame dependencies. Decode failure reconnects with a fresh encoder session.

## Coordinates

`Fit` computes the aspect-preserving content rectangle. The TextureView is centered at that size, and the same rectangle maps touch into encoded-frame coordinates. Black bars reject touch DOWN; a drag that leaves the picture clamps to the edge and still delivers UP. Only the initially tracked finger is used. A resize/orientation/session change cancels the current gesture. The protocol includes the encoded frame dimensions; scrcpy handles the final mapping to phone display coordinates. Do not add a second unconditional 90-degree rotation.

## Network selection

Use actual Android routes that contain the destination before trying the default route; do not assume all LANs are /24 or use text IP prefixes. Tethering interfaces may not appear as `Network` objects, so an unbound socket fallback is required. Real Q07 hotspot reachability remains a hardware validation item.

## Packaging

`car:standard` uses `io.github.q07mirror.car`. `car:q07` uses `io.car07.mirror`, an independent package name unrelated to the vehicle vendor. The existing `de.dsa.ford.otaflash` app is untouched. No vehicle SOA SDK, system signatures or original keystore are used. A local test keystore signs all test builds. Install script rejects a pre-existing package unless explicitly updating and matching certificates.

The car deliberately targets API 30 for the already-tested Q07 platform, so its lint config narrowly exempts Google Play target-version policy. The phone targets API 35 and uses a `connectedDevice` foreground service with the corresponding permissions. The only exported app components are the launcher activities and the Shizuku provider protected by `INTERACT_ACROSS_USERS_FULL`.
