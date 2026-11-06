# Bousygram — Phase 7 Physical Audio Performance Qualification

This opt-in instrumentation test measures ExoPlayer's CPU time and memory
during **actual decoding of a synthetic MP4/AAC stream** while switching EQ
and Smart Volume 30 times. This is not an idle-only native-player stress test.

## Prerequisites

- Physical ARM32/ARM64 Android phone or television with ADB access.
- Bousygram debug APK and matching `androidTest` APK for the same commit.
- FFmpeg on the host for deterministic, wholly synthetic media creation.
- ADB connected to the target device. No Telegram login is necessary.

## Run locally

```bash
bash tools/ci/generate_playback_fixtures.sh ci-artifacts/decoder-fixtures
( cd ci-artifacts/decoder-fixtures && sha256sum --check SHA256SUMS.txt )
adb install -r ci-artifacts/debug/Bousygram-debug.apk
adb install -r ci-artifacts/debug/Bousygram-debug-androidTest.apk
adb shell mkdir -p /sdcard/Android/data/com.vaydelux.bousygram/files/decoder-fixtures
adb push ci-artifacts/decoder-fixtures/seek-control.mp4 /sdcard/Android/data/com.vaydelux.bousygram/files/decoder-fixtures/seek-control.mp4
adb shell am instrument -w -r \
  -e class com.vaydelux.bousygram.core.media.AudioPlayingPhysicalPerformanceTest \
  -e physicalAudioPlayingPerf true \
  com.vaydelux.bousygram.test/androidx.test.runner.AndroidJUnitRunner
adb logcat -d -s BousyAudioPerf:I

# Run a second, comparable pass with audio processing Off throughout:
adb shell am instrument -w -r \
  -e class com.vaydelux.bousygram.core.media.AudioPlayingPhysicalPerformanceTest \
  -e physicalAudioPlayingPerf true -e audioPerfBaseline true \
  com.vaydelux.bousygram.test/androidx.test.runner.AndroidJUnitRunner
adb logcat -d -s BousyAudioPerf:I
```

The APK locations above assume the existing CI-normalized debug artifacts
were downloaded to `ci-artifacts/debug`. Use their actual paths if different.

## Measurements

The test reports the process bitness, CPU milliseconds, total/native PSS
in KiB every five profile changes, the p95 and max cost (ms) of applying a
profile, and how many changes reported a native Applied status. Use the
`audioPerfBaseline` argument to measure an equivalent 30-cycle profile loop
with Original/Off selected, then compare differences against the effects-on run
on the same firmware and thermal state. It asserts
that a real Media3 session exists and its timeline advances.

It does **not** prove audible output, calibrate LUFS, measure end-to-end
audio latency, certify clipping-free sound, or verify Bluetooth/HDMI routing.
Process CPU also includes other app work; report comparative baselines with
the same firmware, media, volume, thermal conditions and test duration.

## Qualification

Run baseline (Original/Off) and effects-on device sessions on ARM32 and
ARM64. Record CPU%, PSS trend, stalls, AV drift and observed sound quality.
Repeat with actual Bluetooth, HDMI, speakers, stereo and surround content,
using real-device acoustic analysis where available. Never call the device
certified from this test alone. The test does not log media URLs, device
serials, audio device IDs, Telegram accounts or file contents.
