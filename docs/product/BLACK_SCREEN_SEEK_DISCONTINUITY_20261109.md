# Playback stability: black-screen watchdog seek discontinuity

## Failure mode
The black-screen watchdog previously counted all `currentPositionMs`
increases as real audio advancement. A forward seek in a progressive MKV or
MPEG-TS stream could jump time by minutes before a new video frame appears,
causing an unnecessary surface or decoder switch at the 3.5 second watchdog
threshold. The default watchdog timer used wall-clock time, which can shift
with network time synchronization on Android TVs. Timestamp zero also
collided with an unarmed sentinel value.

## Fix
- Use `SystemClock.elapsedRealtime()` for the watchdog's production clock.
- Treat large forward/backward native position discontinuities as seek events
  and re-arm the observation window, preserving the staged recovery budget.
- Accept normal 1x–3x progress with jitter; do not classify small backward
  timestamp corrections as seeks.
- Use a nullable start timestamp so devices with a zero-based monotonic clock
  arm immediately.
- Retain normal recovery for a genuinely blank video with sustained real
  audio progress and no rendered video frame.
- Keep user pause/buffering reset behavior and engine selection unchanged.

## Qualification
Run exact-head JVM/Android lint/APK matrix, TDLib Java generation, and
Android emulator media/Cast smoke. Manual tests on 32-bit and 64-bit TVs:
MKV/TS seeking while video output is black or pending, repeated seek/rebuffer,
1x and 3x playback speed, subtitles after seek, audio progress while output
is genuinely black, and lifecycle pause/resume. Log false fallback counts,
successful first frames and retained decoder memory.

Physical-device behavior is not yet measured and automated CI cannot prove
full codec/driver compatibility.
