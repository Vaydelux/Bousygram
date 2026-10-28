# Performance baseline

## Measurement status

No baseline numbers are recorded because no physical Android TV/phone, authenticated Telegram session, representative video set, or controlled network was available during this work. Do not interpret code constants or synthetic unit fixtures as device performance measurements.

## Metrics to collect

Use existing playback telemetry for selection-to-first-frame, first usable byte, seek-to-resume, rebuffer time, cache/local bytes, throughput, and fallback reason. Collect PSS/native heap and CPU with Android tooling on device. Record downloaded bytes before first frame and unused bytes after playback for network overhead.

## Required comparison set

Measure cold and fully cached MP4, fast-start MP4, tail-moov MP4, large MKV, high latency and constrained bandwidth, rapid seeks, reconnects, app background/foreground, repeated switching, and long playback. Include ARM32 low-memory TV and ARM64 TV, and report p50/p95 from repeated runs. Keep device model, Android version, TDLib build, video size/codec, network conditions, and sample count with each result.
