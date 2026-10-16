# Pinned third-party artifacts

- scrcpy: official unmodified `scrcpy-server-v5.0.1` from <https://github.com/Genymobile/scrcpy/releases/tag/v5.0.1>.
- APK asset: `phone/src/main/assets/scrcpy-server.jar`.
- SHA-256: `764eb6f79811d5211fe9df341120882ba9994c7a61b897d7bf3fb662e53bc536`.
- The downloaded release API metadata in `scrcpy-release.json` contains the publisher-provided digest. It was compared with the binary before use. Every build checks this digest again.
- `*.java.reference` are upstream source snapshots used to verify the wire format and options; they are not compiled into the app. scrcpy is Apache-2.0; see `scrcpy-LICENSE` (also bundled inside the phone APK).
- Shizuku API/provider: Maven coordinates `dev.rikka.shizuku:api:13.1.5` and `dev.rikka.shizuku:provider:13.1.5`; see <https://github.com/RikkaApps/Shizuku-API> and `shizuku-LICENSE`.
- Gradle wrapper 8.11.1, official distribution SHA-256 pinned in `gradle/wrapper/gradle-wrapper.properties`.
- Android Gradle Plugin 8.9.2; SDK compile 35. Android 16 runs this app; screen capture compatibility comes from scrcpy, not the compile SDK number.

Never replace the server asset alone: update its digest and protocol parsing together, then run tests and real-device rotation/control checks.
