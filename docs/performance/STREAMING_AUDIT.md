# Streaming audit

## Scope and checkout state

Audit performed against the checked out `main` branch on 2026-10-28. The worktree already contains user changes to `tools/tdlib/build_tdlib_android_arm64.ps1` and untracked native build inputs and outputs. Those files were preserved. The remote `feat/tdjson-transport-compat` branch is present; no migration branch was merged or modified.

The application uses the generated Java `org.drinkless.tdlib.Client` JNI API and loads `tdjni`. This is not the JSON ABI. `libtdjni.so` files are present for `arm64-v8a` and `armeabi-v7a`; no x86_64 binary is present. The submitted build metadata identifies TDLib commit `4c6377aa7386c39217878a9a56d581922ad37ca4`, NDK r27d, and Android API 23. This is workspace metadata, not independently reproducible build provenance. ELF headers identify AArch64 ELF64 and ARM ELF32 shared objects; their dynamic dependencies are Android system libraries and both export `JNI_OnLoad`. The Java client declares the matching native client methods and uses JNI registration. Runtime loading and authentication were not exercised on physical devices.

## Component ownership and flow

- `TdLibClient` owns the process client and dispatches `UpdateFile` through a handler registry.
- `TdLibTelegramFileGateway` translates scheduler operations to TDLib `DownloadFile`, `GetFileDownloadedPrefixSize`, `GetFile`, `ReadFilePart`, and `CancelDownloadFile` requests.
- `TelegramChunkScheduler` owns range coordination, per-file concurrency (one download permit), download windows, in-flight reads, playback leases, prefetch cancellation, cache reads, and file update wakeups.
- `TdLibDataSource` is the direct Media3 read path. `LocalStreamingServer` and `TelegramProxyManager` provide token authorized loopback HTTP for URL based playback and use the same scheduler.
- `TelegramPreCacheEngine` and `FocusPrefetchManager` schedule speculative reads through the scheduler. `CacheManager` also has a full-file `DownloadFile` call for offline caching, outside playback range scheduling.
- `PlayerBufferConfig`, `ExoPlayerEngine`, `MpvPlayerEngine`, and `PlaybackSessionController` own player buffers, engine lifetimes, and foreground session leases.

Playback flow: player selection resolves file metadata; a playback session is created; `openPlayback` schedules initial bytes; Media3 reads through `TdLibDataSource` or mpv reads an authenticated loopback URL; scheduler reads retained disk bytes or TDLib partial-file data and suspends on `UpdateFile`; after reads/leases close, unused work can be canceled. The HTTP server writes sequential byte ranges and handles client disconnects by notifying the scheduler.

## Verified findings

1. The scheduler already has event driven `UpdateFile` notifications, cache first disk/`ReadFilePart` reads, bounded one-per-file download concurrency, prioritized playback versus prefetch, leases, and cancellation. Changes to these areas need concurrency evidence before they are justified.
2. `openPlayback` accepted an `isMp4` argument but previously fetched the tail for all uncached files regardless of that argument. It also did not inspect the head for fast start MP4 metadata. This caused speculative tail traffic for containers that do not need an MP4 tail probe and for MP4s whose `moov` is already at the beginning. The implementation now limits the tail probe to MP4 and skips it when a head probe finds `moov`.
3. `TelegramProxyManager` previously exposed an HLS master playlist whose variants were ordinary `/stream` URLs; `/hls/segment` was only a progressive byte-range alias. This was not segmented HLS. Those routes and URL factory were removed. Playback now keeps the progressive authenticated stream path.
4. TDLib request ownership is mostly centralized in the gateway and scheduler, but `CacheManager` intentionally retains a direct full-file download path for offline caching. It should not be folded into range streaming without a separate offline-cache review.
5. The Android TV buffer configuration already uses `ActivityManager` memory class and `isLowRamDevice`; current settings have bounded profiles. No device benchmark data is available in this checkout.

## Baseline and limitations

No representative device, Telegram account session, controlled network, or repeatable media corpus was available for this audit. Startup latency, first frame, seek recovery, memory, CPU, and network overhead therefore have no measured baseline. The repository has telemetry and unit tests, but this audit does not claim physical-device results. ABI package presence is not equivalent to successful loading or TDLib authentication.

## Risks and next work

- Confirm native source/build provenance and execute auth/download smoke tests on ARM32 and ARM64 devices before release.
- Add controlled scheduler tests around overlapping reads, rapid seek supersession, and operation cancellation; existing unit coverage should be examined alongside changes.
- Benchmark cold and cached playback, tail-moov MP4, MKV, constrained bandwidth, and low-memory TV hardware before further tuning.
- Verify loopback binding and token revocation on real mpv consumers. Do not reintroduce HLS until a real segmented pipeline and consumer test exist.
