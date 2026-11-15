# Stream Only — bounded Telegram playback preview

Status: experimental **debug builds only**. Release and hardenedRelease keep
the established open-ended foreground TDLib behavior until on-device qualification.

## What changes

- Developer Tools → **Stream Only (Experimental)** opt-in; default OFF.
  The Gradle debug property `-PboundedTelegramStreamingExperiment=true`
  still forces the preview on in that debug APK.
- Foreground TDLib downloads use existing finite 16 MiB / 8 MiB / 4 MiB
  windows according to storage pressure. These are **per-command** limits,
  not a guarantee that total TDLib disk usage stays below the window.
- ExoPlayer and mpv get smaller session buffers; LibVLC already keeps a short
  network cache. The actual extractor byte/range requests still own seek
  positions, especially with variable-bitrate MKV and TS containers.
- No speculative cache-ahead or next-episode preload in the preview.
  User-driven requests, subtitle handling and essential first-byte preparation
  remain available.
- Normal stop/track-switch schedules a **30-second** delayed, account-checked
  targeted TDLib `DeleteFile`. Before deletion, the worker rechecks TDLib's
  download-active flag, playback owners, active range leases, speculative
  jobs and explicit offline pins. New playback cancels pending reclaim.
  Recovery stops do not schedule deletion.
- Continue Watching checkpoints are not removed by this operation.
  Normal 160 MiB managed cache quota and the safe-prune function remain.

## Verification

Run the Kotlin unit tests (`StreamOnlyReclaimPolicyTest` and existing cache,
range-window, session tests), compile an opt-in debug APK, and compare storage,
first-frame latency, rebuffer count and seek behavior with default debug.

On actual ARM32 and ARM64 TVs check MP4, MKV with Cues near EOF, MPEG-TS,
high bitrate video, rapid seek/rewind, track switch, resume after 30 seconds,
offline-pinned videos, logout/account switch, and storage-pressure recovery.

Verify both video bytes reclaimed and Continue Watching preserved; watch for
full-file TDLib growth despite finite windows, and for stalls at the 16 MiB
boundary. Capture logs/telemetry and use the non-experimental build if a seek
or stall regression occurs. Do **not** ship this as production default based
only on unit tests or Gradle compilation.
