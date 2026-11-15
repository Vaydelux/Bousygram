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
- Normal stop and playlist track transitions schedule a **30-second** delayed, account-checked
  targeted TDLib `DeleteFile`. Transient native-download status or GetFile
  timeouts receive at most two additional attempts, spaced 60 seconds apart.
  Before deletion, the worker rechecks TDLib's download-active flag,
  playback owners, active range leases, speculative jobs and explicit offline pins.
  New playback cancels pending reclaim for the currently selected episode,
  not unrelated queued episodes. Recovery stops do not schedule deletion.
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
boundary.

### Physical TV aggregate storage observations (Windows PowerShell)

Install an opt-in debug APK, connect and authorize an ARM32/ARM64 Android TV
through ADB. In Developer Tools enable Stream Only, then use these sample runs:

```powershell
.\tools\qualification\Capture-BousygramStreamOnlyStorage.ps1 -Phase baseline -Samples 2 -SampleSeconds 5
# Start an authorized test video in the app, then run:
.\tools\qualification\Capture-BousygramStreamOnlyStorage.ps1 -Phase playback -Samples 20 -SampleSeconds 30
# Stop playback, wait at least 30 seconds, then run:
.\tools\qualification\Capture-BousygramStreamOnlyStorage.ps1 -Phase after-exit -Samples 12 -SampleSeconds 15
```

The sampler requires `adb` and Android's `run-as` for a debuggable APK.
It records only TDLib directory aggregate size and optional device-wide free
space, never Telegram messages, media paths, filenames, URLs, raw logs or
serials. It fails if the private aggregate cannot be read. Existing pinned
downloads, unrelated files and TDLib databases contribute to the aggregate,
so a fall after exit is useful evidence but not a proof of exactly how many
video bytes were deleted. The named phase and source commit are user claims,
not independently verified by the script.

Capture logs/telemetry and use the non-experimental build if a seek or stall
regression occurs. Do **not** ship this as production default based only on
unit tests or Gradle compilation.
