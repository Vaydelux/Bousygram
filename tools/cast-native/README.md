# Isolated Cast native qualification

This directory is not an app module and does not package a library or enable Cast.
The JNI bridge consumes custom sequential AVIO input and emits fragmented MP4
through synchronous 32 KiB callbacks. The first qualification is finite:
16 MiB input, 20 MiB output, 120-second timestamp range, 20,000 packets,
2 MiB per packet, cooperative 60-second wall deadline, exactly H.264 + AAC.
No arbitrary URLs or network protocols, encoders, CLI or video re-encoding.
Probe/packet limits are not a hard FFmpeg heap quota. A callback that blocks
cannot be interrupted by the native deadline. Output may be partial on failure:
the caller must discard it and verify successful output before publication.

Build on Linux x86-64 with Android NDK r28c (28.2.13676358), API 26:
```sh
bash tools/cast-native/build_android.sh "$ANDROID_NDK_ROOT" /tmp/bousygram-native-candidate
```

FFmpeg 8.1.3 is a pinned candidate from the maintained 8.1 branch, not a claim
that all current vulnerabilities are resolved. Fetch the signed upstream source;
verify the official release-key fingerprint in an isolated keyring. Record source
hash, build configuration, ABI, ELF alignment and exports. This is reproducible
build input selection, not a verified bit-for-bit reproducible binary yet.

Static FFmpeg LGPL components are for internal qualification only. Before any
APK distribution, complete security advisory review, corresponding source and
license notices, relinking compliance (or a separately reviewed dynamic-link
design), ABI and 16 KiB page-size tests. Do not upload native binaries or copy
them into jniLibs. Existing mpv/LibVLC libraries remain unchanged.

JNI contract is in NativeCastRemuxBridge.java; no app-side load or caller.
Next gate: actual build logs, Android callback/media instrumentation including
packet and timeline comparison to existing fixtures, revocation and truncated
input; then bounded output framing, producer-owned TDLib download leases and
cache integration. Random access, longer media, DTS/PTS validation and
per-fragment memory caps require further engineering. No HLS receiver exposure.
