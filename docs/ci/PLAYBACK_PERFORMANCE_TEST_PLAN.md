# Playback performance and recovery qualification plan

Bousygram targets Android TV (32-bit and 64-bit ARM), phones, and tablets. This
document is a **procedure and acceptance checklist**, not evidence that any
device has been tested or that a debug APK is production-ready.

## Test against exact APK bytes

1. Check out the tested Git commit and download **one exact release asset** from
   the same commit's GitHub debug prerelease (or the production-signed candidate
   for actual production qualification). Record the build commit, APK SHA-256,
   ABI, Android version, device model, decoder, and available RAM.
2. Install it without clearing app data unless the scenario explicitly needs
   a clean install. Debug certificates can differ between CI runs; if Android
   refuses an update, back up your app data or use a dedicated test device.
3. Log in to a test Telegram account. Keep a private list of reproducible test
   fixtures, including containers, video/audio codec, bitrate, duration, size,
   and whether the bytes were already cached. **Do not publish Telegram file
   IDs, chat IDs, tokens, media, account details, raw logcat or device serials.**

## Repeatable scenario matrix

Each row should be exercised for MP4/H.264, MKV (with both head and tail cue
seeks when present), and MPEG-TS. Include at least one audio-only asset.

| Scenario | What to record |
| --- | --- |
| Cold open, uncached on Wi-Fi | Tap-to-first-*rendered*-frame and first-byte latency |
| Warm open, fully cached | Tap-to-first-frame; should not require new network bytes |
| Seek 10 s forward/back and long-distance | Exact requested/actual position, p50/p95 seek latency, extra TDLib bytes |
| Variable-bitrate MKV/TS | Distinct metadata/cue windows; no false EOF or speculative window thrash |
| Manual pause during network stall | Explicit pause must remain paused after recovery |
| Wi-Fi disconnect/reconnect and mobile handoff | Total rebuffer time, retry count, no indefinite Preparing state |
| Force stop, restart, resume | Resume correctness, including rewinding and seeking to Start Over |
| Decoder fallback (ExoPlayer to mpv) | Surface, audio, subtitle continuity and memory peak |
| 60-minute uninterrupted viewing | Memory and app stability, native crashes/ANRs, cache quota |
| Browse/scroll with D-pad during thumbnails | UI frame jank and focus-loss regressions |

Use three runs per case per **device/ABI** when possible. Keep network conditions
comparable (same fixture and location/network, or simulated consistent bandwidth)
and report median and p95 only when there are enough data points to be meaningful.
Never replace measured values with idealized targets.

## Capture anonymized Android memory and UI jank

With Android Platform Tools (`adb`) and PowerShell 5.1+ available, connect one
authorized device and start playback yourself. From the repository root run:

```powershell
.\tools\qualification\Capture-BousygramPlaybackPerf.ps1 -SampleSeconds 60 -OutputDirectory .\perf-evidence
```

If multiple Android devices are connected, pass `-DeviceSerial` explicitly.
The script checks that Bousygram is installed and running, resets only its
`dumpsys gfxinfo` counters, samples initial/final `TOTAL PSS`, and writes a
small local JSON file with model, Android version, ABI, UI frame counts and
duration. No screenshots, private content, logcat, credentials or serials are
written. Output fields can be null if Android does not expose that counter.

**Interpretation:** `gfxinfo` tracks application UI rendering, not hardware
video-decoder dropped frames. It cannot prove smooth or even active video
playback. Collect app playback telemetry (first-frame, rebuffer and seek
latencies, decoder dropped frames) separately from Bousygram's diagnostic
interface and anonymize before sharing. The script intentionally reports
`hardwarePlaybackVerified: false` and `productionQualified: false`.

## Regression gates and release decision

- Gate on Android Enterprise Checks (Kotlin compilation, lint, unit tests,
  ABI/JNI compatibility and exact APK hashes) and the emulator smoke suite.
- Repeat cache-specific tests for read-ahead upper bounds, account-logout
  revocation, timestamp-derived speculative seeks, and backward/zero resume.
- **Performance is not guaranteed** by automated success. Compare actual
  p50/p95 first-frame times, rebuffer ratio, seek latency and native-memory
  peaks against a recorded pre-change baseline. Investigate regressions before
  claiming a performance gain. For high variance, repeat runs rather than
  setting an arbitrary pass threshold.
- The protected [physical-device qualification record](RELEASE_DEVICE_QUALIFICATION_RECORD.md)
  remains mandatory for a production-signed release. Debug APKs are for tests
  and never satisfy that gate.
