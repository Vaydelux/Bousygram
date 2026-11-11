# Experimental foreground Telegram byte-range windows

**Status:** DEBUG-ONLY PROTOTYPE — disabled by default, NOT production qualified.
**Release blocker:** [#142](https://github.com/Vaydelux/Bousygram/issues/142).
**Baseline:** Bousygram `main` at `682db330193d73f91e80656d8fbbd7f1843a9ed8` (follow-on implementation is a PR).

## Problem

The UI reports an eviction-target disposable-media budget (200 MiB total, 160 MiB
for TDLib media). That budget is **not** a hard bound on active Telegram
downloads. The established foreground scheduler calls TDLib `DownloadFile`
with `limit=0` and keeps the active file lease protected from cleanup.
A large MKV/TS stream can therefore consume far more disk than the cache target.

## Implemented debug-only experiment

A user explicitly builds an experimental **debug** APK with:

```bash
./gradlew :app:assembleDebug -PboundedTelegramStreamingExperiment=true
```

Or in Windows PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug -PboundedTelegramStreamingExperiment=true
```

The flag defaults to `false` in all variants; `release` and
`hardenedRelease` always compile the experiment **off**, even if a Gradle
property is passed. Normal CI/debug prereleases remain in the existing
open-ended mode; do not assume their APKs contain the experiment.

When enabled, **foreground** TDLib `DownloadFile` calls use positive byte
limits. The budget is **16 MiB** when at least 500 MiB is free, **8 MiB**
at 250–500 MiB free, and **4 MiB** when free space is critical or unknown.
Each request is clamped to known EOF. Actual foreground reads advance the
window within 1 MiB of its end; distant MKV Cues, MP4 moov and extractor
seeks reposition to the precise requested offset. Reopening a DataSource
inside a valid window does not restart its native download. The fast-stall
recovery reissues the current bounded window, not an unlimited request.

The separate explicit offline/pinned full-file download workflow remains
unmodified. Neither the experimental policy nor the scheduler directly
deletes TDLib disk files, evicts pinned files, or clears the active playhead.

## What this does NOT guarantee

**Finite individual native requests are NOT a hard cumulative disk quota.**
TDLib may retain bytes downloaded by previous windows. Multiple episodes
or a long viewing session can still consume significant flash storage.
The feature is a controlled stepping stone to measuring bounded-window
behavior, not proof that issue #142 is resolved.

No virtualized sparse file, segmented rolling cache, native TDLib disk
compaction or guaranteed byte-level deletion is implemented. Do not delete
TDLib temporary files with `File.delete`; file leases and native reference
lifetimes must remain authoritative. Do not enable on production just because
JVM or x86_64 emulator tests pass.

## Automated qualification

- JVM policy tests cover normal/low/critical/unknown free-space budgets,
  overflow/EOF boundaries, near-window renewals, distant back/forward
  seeks and avoidance of repeated restarts on ordinary DataSource reopens.
- Scheduler fake-gateway tests verify startup uses a **positive** download
  limit, a near-EOF extractor request repositions to a finite tail range,
  a repeat open does not discard the current window, and a failed
  startup does not leave a phantom window.
- Existing default open-ended behavior is preserved and remains tested.
- Exact PR head must pass Android Enterprise (Kotlin/lint/unit/ABI),
  Android Emulator Cast Smoke, and any applicable responsive UI checks.

## Required physical acceptance before any rollout

1. On the same **8 GB ARM32 TV**, run a 60-minute and multi-episode session
   with the current normal debug APK as baseline; record sanitized device
   `/data` available bytes, app data footprint, PSS/native heap, startup,
   rebuffer and user-visible playback behavior.
2. Repeat the **same** media sources, network conditions and seek schedule
   with an explicitly opt-in experimental debug APK; record APK checksum,
   exact commit SHA, ABI, Android OS and playback engine.
3. Include repeated .mkv/.ts/.mp4 seeks to head, uncached midpoints and near
   EOF; verify subtitle selection, D-pad Back, Pause while buffering, audio
   focus loss, player background/foreground, logout/relogin and network loss.
4. Repeat at least one sustained run on ARM64 TV and a phone, with LibVLC,
   libmpv and Media3 selected independently. Observe window thrashing,
   decoder retries, blank video, seek reset to 0:00 and unexpected autoplay.
5. Test low storage (<500 MiB and <250 MiB), without clearing active
   content, losing continue-watching records, or damaging offline pins.
6. Reject the experiment if stalled playback, repeated redownloads,
   cumulative storage growth, native crashes, or account-crossing reads
   exceed reviewed baseline thresholds. Document thresholds in the
   qualification record **before** sign-off.
7. Capture evidence using existing `tools/qualification` scripts. Redact
   Telegram account identifiers, URLs, message IDs, filenames, credentials,
   and raw media.

Only after physical acceptance and a separate explicit rollout decision
should another PR consider enabling the mode by default. Keep the
production-signing and branch-protection gates separate.
