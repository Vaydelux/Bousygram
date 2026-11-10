# Native media-session playback intent — regression and device qualification

Baseline: `6b31173d7ee58d452e04e4610fc8dbe7cad9be38`.

## Root cause and correction

Before this change `UniversalMediaSessionBridge` called `playerEngine.pause()`, `play()` and `seekTo()` directly from the libmpv platform MediaSession callback. Those calls did not update `PlayerScreen`'s `explicitlyPaused` flag, request audio focus through the owner, or use `performTrackedSeek`'s MPEG-TS capability checks.

A late decoder Ready/rebuffer event could therefore undo a physical remote Pause. An unsupported TS seek could also bypass the guarded seek path.

- The platform session for libmpv and LibVLC now uses the same `ExternalPlayerTransportCommands` router.
- `PlayerScreen` handles the requests on its coroutine scope, rejects commands for a retired/stopped engine, records explicit user Pause, requests audio focus for Play, and performs checked absolute/incremental seeks.
- Cast commands retain their remote behavior when Cast is connected.
- Media3 ExoPlayer keeps its own session; only native-engine command ownership changes in this milestone.

## Tested in CI

- JVM policy tests verify that remote Pause updates the explicit-pause decision used by the real Ready-state recovery rule; Play clears that intent; absolute seeks cannot pass negative positions to the guarded callback.
- All existing Android Enterprise unit tests, Android emulator regressions, TDLib generation, and ABI matrix must pass for this PR.

These tests cannot prove actual HDMI-CEC input, Android TV OEM MediaSession behavior, decoded frames, subtitles, or audio focus interaction on real devices.

## Physical device checklist — status NOT RUN

Use authorized Telegram test files and an exact-commit debug APK for preview testing. Record APK SHA-256 privately. Repeat on both ARM32 and ARM64 TVs:

1. Choose libmpv, start MKV or MP4, press a physical remote Pause while buffering. Verify Pause is retained after Ready and Wi-Fi recovery.
2. Press remote Play, verify foreground audio focus and actual frame/audio progression; pause again and verify no late auto-resume.
3. On an MPEG-TS without a supported seek index, press hardware seek/fast-forward. Verify a recoverable unseekable message and no jump to 0:00.
4. On an indexed seekable file, test forward, backward and absolute seeks; verify decoded position rather than just the requested timestamp.
5. Repeat on LibVLC; validate exactly one active player and MediaSession ownership.
6. Trigger engine switch during buffering and then use remote controls; stale callbacks must not revive the retired engine.
7. Repeat with Cast connected to confirm remote Play/Pause/Seek are sent to the receiver, not a hidden local player.
8. Switch the TV app background/foreground and perform account logout; verify stale media keys cannot act on an old session.
9. Test subtitles and D-pad after media keys in Dark, Light and High Contrast modes.
10. For physical memory evidence, use existing `Capture-BousygramPhysicalSoak.ps1` and `compare_physical_cohorts.py` with matching repeat runs; do not infer playback success from PSS alone.

No physical test results, measured p50/p95 metrics or production certification are asserted by this document. Complete the protected signed-device qualification record before release.
