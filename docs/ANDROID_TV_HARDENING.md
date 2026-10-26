# Android TV hardening

Updated: 2026-10-27

This document records the source-level Android TV playback contract. It distinguishes behavior verified by automated checks from behavior that still requires a physical device and a real Telegram account.

## Enforced in source and CI

- The launcher activity does not declare or enter picture-in-picture mode. Player backgrounding pauses playback and stops active cache work.
- Hidden player controls use Left/Right for an accelerated seek preview. OK commits one seek and Back cancels the preview; directional keys never select the previous or next video.
- Telegram chats and real forum topics remain the browsing hierarchy. Inferred TV-series cards and series drill-down are not presented, while old metadata fields remain readable for database compatibility.
- Library browsing uses a bounded `LazyVerticalGrid` area with stable chat/message keys; headers, source selection, and topic controls stay outside the scrolling grid.
- The chunk scheduler checks TDLib-ready ranges before waiting for a download and records cache hits, cache misses, and reused byte counts.
- Playback telemetry records startup and rebuffer data plus cache reuse, seek latency, dropped frames, throughput, and memory-pressure state. Telemetry stays local.
- Both `armeabi-v7a` and `arm64-v8a` TDLib binaries are mandatory for release builds.
- Release and hardened-release builds fail when production signing is absent. CI verifies every generated hardened APK against the configured SHA-256 certificate digest.

The `:app:verifyTvHardeningContract` task protects the PiP and browsing invariants. The unit suite covers D-pad routing, seek-preview confirmation/cancellation, range behavior, cache metrics, playback state, and serialization compatibility.

## Physical-device acceptance checks

Automated JVM and emulator checks cannot prove decoder, remote, storage, or Telegram CDN behavior. Before release, run the following on representative 32-bit and 64-bit Android TVs:

1. Start a large uncached video and record time to first frame.
2. Rewind into an already played region and confirm no unnecessary Telegram range request occurs.
3. Seek into an unseen region and confirm only the missing playback window is prioritized.
4. Hold Left/Right, verify the preview accelerates, then test OK commit and Back cancel.
5. Navigate away and back; verify the selected card and grid position are restored.
6. Switch between a forum group's topics and confirm media never crosses topic boundaries.
7. Background and resume repeatedly; verify there is one playback surface and no PiP window.
8. Exercise hardware decoding, the mpv fallback, audio/subtitle selection, and low-memory conditions.
9. Test voice search with and without an on-device recognizer and confirm typed search remains available.
10. Install the signed `armeabi-v7a`, `arm64-v8a`, and universal APKs on matching devices and verify their certificate digest.

Use the local diagnostics export when comparing devices. Cache-hit/miss counts and seek latency make regressions visible without assuming that unseen remote content can seek instantly.
