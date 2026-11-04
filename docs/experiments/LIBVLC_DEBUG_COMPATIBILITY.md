# LibVLC experiment — debug-only compatibility engine

Status: **opt-in integration, NOT a production-ready replacement**.

This experiment uses official VideoLAN `org.videolan.android:libvlc-all:3.6.5`
and remains absent from ordinary Bousygram APKs. It is intentionally not
part of Auto selection or automatic fallback. The selected preference is
ignored and replaced with Auto when the experiment is not compiled in.

## Opt-in compilation

```bash
./gradlew :app:assembleDebug -PenableExperimentalLibVlc=true -PdevAbi=arm64-v8a
./gradlew :app:testDebugUnitTest -PenableExperimentalLibVlc=true -PdevAbi=arm64-v8a
```

For ARM32 repeat with `-PdevAbi=armeabi-v7a`. Do not build release,
hardenedRelease, or publish regular prereleases with this property. The
source and dependency are injected only when this property is true and
the requested tasks are debug-only.

## Safety and acceptance

- User explicitly chooses **LibVLC (Lab)** in Settings on this opt-in APK.
- ExoPlayer and libmpv remain default and operational even if LibVLC init fails.
- VLC only receives a capability-bearing localhost HTTP URL from the same
  account-scoped `LocalStreamingServer` used by libmpv.
- Preserve native video surface, callbacks, pause, decoder stop and release
  boundaries when switching engines or logging out.
- No remote redirect or public network URL is allowed for private streams.
- A reported seekable flag is not evidence of successful time positioning.
  The VLC engine updates UI time from actual native events.
- Embedded text/audio track handling and subtitle appearance require tests on
  the actual VLC API and device; do not claim parity with Media3/libmpv.
- Compare MP4, indexed/unindexed MPEG-TS, MKV/ASS on physical ARM32 TV and
  ARM64 phone with identical Telegram files and network conditions.
- Monitor ABI packaging, APK size, PSS, native decoder crashes and LGPL
  redistribution obligations before making this a production feature.
