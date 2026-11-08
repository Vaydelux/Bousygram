# Extras guarded LibVLC — physical TV measurements (experimental)

**Status:** diagnostic instrumentation available on the draft Extras PR. This is **not** physical-device certification. Native Extras LibVLC is enabled only in debug builds; neither its UI nor these measurements enable production third-party native playback.

## What is recorded

Open an explicitly selected Extras title, choose **LibVLC (MP4/WebM debug)**, and select **Stats** at the top of the guarded native player. The panel is **off by default**, stays local to that session, and exposes no media URL, host, query string, cookie, HTTP headers, Telegram account, or title in the counter object.

| UI measurement | Exact source and meaning | Does **not** prove |
| --- | --- | --- |
| Startup → native Playing | Android monotonic elapsed time between LibVLC `play()` intent and first `MediaPlayer.Event.Playing` | First rendered frame; also excludes initial HTTPS byte-range probe and optional subtitle fetch |
| Seek → reported position | Time from a successfully submitted native seek command until the reported timeline reaches the target after meaningful position movement; includes the last, mean and slowest acknowledged samples | Rendered-video recovery, decoded-frame smoothness or audio synchronization |
| HTTP windows | Number of real HTTP 256-KiB range fetches attempted for the guarded FD | All network requests (preflight 0–0 range, subtitles, and other app traffic are excluded) |
| Failed/canceled | Fetches ending in an `IOException`, including canceled operations | Permanent media failure; error status needs separate verification |
| Cache hits / rate | Reused buffered windows compared with window requests | Cache effectiveness for all formats, or disk/image cache behavior |
| Downloaded bytes | Sum of fully validated successful guarded-range windows | Complete network traffic, including HTTP metadata or failed partial downloads |
| Window cache | Currently retained, decoded response bytes; hard maximum **512 KiB** (two windows of 256 KiB) | Total decoder memory footprint |
| Longest HTTP fetch | Longest successful window request duration in milliseconds | Median seek time, WAN latency or frame delivery |
| Process PSS snapshot | Android `Debug.getPss()`, captured when opening Stats or selecting **Sample PSS** | LibVLC-only memory, native-heap allocation, a leak, or memory impact of this PR alone |

The seek timer expires after 15 seconds; rejected, no-change, superseded and timed-out seeks never inflate successful-sample averages. The panel uses O(1) counters and is intentionally not a continuous raw telemetry export. PSS sampling is opt-in, dispatched off the UI thread, and does not run on each 750-ms playback-state tick.

## Hardware qualification procedure

1. On each physical **armeabi-v7a** and **arm64-v8a** Android TV, install the debug APK built from the **same exact Git SHA**. Record APK checksum, OS/device RAM, network type, date, authorized fixture type and whether its range responses include a strong ETag. Use only permitted, non-sensitive fixture media.
2. Open the same progressive MP4 and WebM fixture on both devices. Record a cold-open native Playing observation; do not call it first frame. **Visually** confirm that video actually renders and audio is audible.
3. Choose Stats, then play continuously for 5 minutes. Capture the starting and ending request totals, successful windows, downloaded KiB, cache-hit percentage and PSS. Refresh PSS with **Sample PSS**, as that number is not periodically sampled.
4. Execute **20 repeated seeks**, alternating near the head and the tail of the file, with both short ±10s and long timeline jumps. Record the last reported-position acknowledgment, resolved-versus-expired seeks where available, visible recovery, subtitle continuity and native errors. Compare a controlled cold run to a warm run of the **same** fixture on the **same** device.
5. Switch between native Extras LibVLC, native Media3 and WebView **at least 50 times**. Include Pause-before-buffering-completes, background/foreground, external subtitle change, repeated focus loss, and the case where the source rejects range requests. Confirm deliberate Pause never resumes by itself; inspect for a lingering audio session or an unclosed FD.
6. For the long-session ARM32 memory gate, use the existing private resource sampler with at least 30–60 minutes and matched before/after cohorts as described in [PHYSICAL_DEVICE_PRIORITY_GATE.md](PHYSICAL_DEVICE_PRIORITY_GATE.md). Sample native and whole-process PSS separately; compare medians, peaks, process restarts and growth under the same workload. A single PSS increase after opening a video is not a leak.
7. Repeat on ARM64, with no change in test fixture, build SHA or connection class. If the device codec or decoder behavior differs, record that difference before interpreting timing comparisons.

## Recording results

Privately record the following **numbers only** for each run, with a source identifier that is not a URL: SHA and APK checksum; ABI/OS/device RAM; codec/container; test stage (cold/warm/head-tail/continuous); window HTTP requests; cache hits; downloaded KiB; longest successful window fetch; startup-to-native-Playing (if observed); last position acknowledgment (if observed); PSS at start/end; native error/ANR/LMK count; and **human-attested** visible-frame/audio/seek pass/fail.

Keep HTTP stream URLs, auth headers, query tokens, account identifiers, raw logcat, media titles and proprietary fixture names **out of public GitHub issues and artifacts**. Do not collect or upload an unredacted network trace from a private provider.

## Release exit conditions

- Exact-source GitHub Enterprise checks, unit tests, emulator instrumentation, TDLib generation and LibVLC ABI gate are all green.
- Operator-observed real video and audio, seeking, subtitles, lifecycle transitions and user-pause intent work on both physical ABIs.
- Repeated same-device before/after runs show no unexplained sustained PSS growth, native crash, stuck buffers or FD/network leakage.
- Measurements distinguish **native callbacks**, **timeline movement**, and **actually rendered frames**. No provider success, throughput improvement or memory reduction is inferred solely from automated tests or these debug counters.
- Production Extras LibVLC stays disabled until the app's guarded-transport and physical-qualification gates have been explicitly reviewed.

See also [PLAYBACK_RECOVERY_OBSERVABILITY.md](PLAYBACK_RECOVERY_OBSERVABILITY.md) for Telegram-engine telemetry definitions and [LONG_SESSION_RESOURCE_BUDGETS.md](LONG_SESSION_RESOURCE_BUDGETS.md) for ARM32 cache and catalog budgets.
