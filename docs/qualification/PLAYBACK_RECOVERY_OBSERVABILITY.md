# P1.1 — Playback recovery observability

**Status:** source instrumentation and JVM regression coverage added on `main`.
**Not a physical-hardware qualification:** live Telegram reconnection, real ARM32
memory trends and native first-frame output still require device evidence.

## Why these metrics changed

Earlier local telemetry could mistake libmpv/LibVLC `Ready` for a rendered first
video frame, count timed-out seeks toward average successful seek latency, and
replace missing byte arrival observations with inferred timing. This made
before/after comparisons misleading.

## Meanings and provenance

| Field | Meaning | Important limitation |
| --- | --- | --- |
| `timeToFirstByteMs` | A captured first-byte notification relative to stream preparation | `0` means not observed; not estimated from first frame or native buffer position |
| `timeToFirstFrameMs` | Time until an engine's authoritative rendered-video-frame callback | `0` means not verified. Native engines currently lack this reliable callback |
| `firstOutputEvidence` | `rendered_video`, `audio_playback_inferred`, `native_ready_unverified`, or `unobserved` | `native_ready_unverified` must never be interpreted as pixels rendered |
| `rebufferCount`, `totalRebufferMs` | Stall episodes after initial output, measured on Android monotonic elapsed clock | User Pause stops stall accumulation, network/cache conditions must be comparable |
| `seekCount`, `averageSeekLatencyMs` | Only decoder-confirmed seek samples and their mean latency | Does **not** include rejected, timed-out, replaced or unresolved seeks |
| `seekRejectedCount` | Seeks refused when no usable native seek map is exposed | Particularly relevant to unindexed MPEG-TS |
| `seekTimedOutCount` | Accepted seeks that the decoder never confirmed within the seek policy window | Not a successful seek |
| `seekSupersededCount` | Accepted seeks displaced by another seek, reprepare or unsupported request | Does not by itself mean the decoder failed |
| `seekUnresolvedCount` | One accepted seek was still pending when telemetry was committed | Not a completed successful seek |
| `fallbackAttemptCount`, `fallbackSuccessCount` | Number of engine-fallback attempts and successful handoffs | A successful handoff is **not** evidence of a rendered frame or successful stream |
| `droppedFrameCount` | Renderer-reported drops where available | Native player implementations may expose incomplete counters |

The existing recorder retains a local bounded **50-session** FIFO. New counters
are per playback session and store no Telegram account IDs, chat IDs, file
paths, stream URLs, titles or capability tokens. Exported legacy error strings
may still contain device-specific information, so inspect diagnostic exports
before sharing outside the device.

## Verification scenarios

1. Indexed MP4/MKV: play, wait for actual output, seek twice, and verify confirmed
   seek latency and reasonable timeline progress.
2. Unindexed TS: attempt an unsupported seek; the rejection count should change,
   while confirmed-seek count and latency stay unchanged.
3. Buffered network playback: allow a temporary stall, then recover. Confirm
   buffering time increases while user Pause does **not** count as network stall.
4. Trigger a seek timeout: ensure the timeout count grows and no successful seek
   sample is fabricated. Repeat rapid seeks and verify superseded counts.
5. Decoder fallback: trigger one fallback and verify attempts vs successful engine
   adoption. Check the **actual engine** in Engine Lab after fallback.
6. On libmpv/LibVLC, ensure no verified video TTFF is emitted solely from
   `Ready`, audio clock progress or decoder metadata. Verify pixels visually.
7. Repeat on ARM32 and ARM64 for at least 30–60 minutes using the existing
   physical-device qualification protocol.

## Validation gates and next work

Run Android Enterprise checks, JVM unit tests, emulator instrumentation and
native ABI packaging checks for the exact source commit. Use the private
physical-device protocol in
[PHYSICAL_DEVICE_PRIORITY_GATE.md](PHYSICAL_DEVICE_PRIORITY_GATE.md)
to establish ARM32/ARM64 runtime evidence. Only then set device-specific
performance budgets for P1.2 cache and memory tuning.

**Known limits:** P1.1 does not introduce an MPV/LibVLC rendered-frame callback
or claim decoder quality/performance improvements. Its purpose is to distinguish
measured success, failed recovery and missing evidence before tuning playback.
