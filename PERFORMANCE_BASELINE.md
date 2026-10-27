# Performance Baseline

Measured on 2026-10-27 from commit `e4b7174` on the local Windows build host. No physical Android TV was attached, so playback latency, decoder, frame-drop, memory-pressure, and codec claims remain unmeasured.

## Build baseline

| Gate | Result |
| --- | --- |
| Debug + release + ABI + TV contract | Passed in 19m52s |
| Debug lint | Passed in 8m22s, but the former baseline hid two errors and one hint |
| Unit tests | 243 executed; one order-dependent coroutine/JNI leakage failure |

Before this hardening pass, HTTP partial-file delivery polled status and slept, `TdLibDataSource` retried with blocking sleeps, and one disconnect could cancel all work for a file. Large full HTTP requests were incorrectly converted to an 8 MiB `206` response.

## Measurement protocol

Use the same device, media file, account, network profile, and cache state for before/after runs. Capture cold/warm TTFB, TTFF, rebuffer count/duration, seek latency, Telegram bytes requested, local bytes served, deduplicated requests, superseded windows, decoder, dropped frames, initialization/release time, and memory state. Run at least five samples and report median plus worst case.

Physical validation is required on representative `armeabi-v7a` low-memory TV and `arm64-v8a` TV hardware before publishing device-level performance claims.

## Post-change host results

The complete unit suite is deterministic (246/246 passing in 3m21s), strict lint passes without suppression, and the final combined lint/ABI/TV-hardening/debug/release gate completes in 20m31s. These are correctness/build measurements only; playback performance remains pending physical-device measurement.
