# Streaming cache architecture and audited baseline

## Status

Audit of the repository implementation on 2026-10-28. This is a code and pinned-TDLib-binding audit, not a device measurement. Physical storage consumption during playback, long sequential playback, and range retention have not been measured on hardware. Therefore bounded cumulative storage is **not verified**.

## Where bytes live

`TelegramAuthManager` configures TDLib's database directory as `filesDir/tdlib` and its payload directory as `filesDir/tdlib_files`. Telegram video/document payloads requested by playback are therefore TDLib-managed files, distinct from Android's `cacheDir`. The database directory holds TDLib metadata/database state; the app also has its own files and cache. `CacheManager.storageStats` recursively measures these directory trees, but it does not establish a quota over all app data or filesystem usage.

| Data | Owner/path | Current handling |
|---|---|---|
| Telegram media payloads and downloaded ranges | TDLib, `filesDir/tdlib_files` | Ranges requested through `DownloadFile`; retained bytes are TDLib-managed |
| TDLib metadata/database | TDLib, `filesDir/tdlib` | Persistent database, separate from the media cache |
| App cache | Android `cacheDir` | Includes app-managed cached content; used for quota free-space lookup |
| Offline-pinned media | TDLib payload directory, ID persisted in app preferences | Pin starts a full-file download (`offset=0, limit=0`); unpin removes protection and requests cleanup |
| Streaming buffer | No separate app-owned disk buffer identified | Scheduler reads ranges from TDLib; this is not an independent bounded cache |

## Playback path

`TelegramChunkScheduler` coordinates range jobs through `TelegramFileGateway`. The gateway calls TDLib `DownloadFile(fileId, priority, offset, limit, false)`, checks downloaded prefix size, and reads available bytes with `ReadFilePart`. `TelegramPreCacheEngine` primes 2 MiB at the head and, for larger videos, 2 MiB at the tail when playback is not busy. Cache-ahead is normally 32 MiB (capped at 64 MiB); cellular limits it to 4 MiB. Seek cancels out-of-window jobs. Full-video background caching is disabled, while explicit pinning still requests the full file.

The pinned generated `TdApi.java` documents `DownloadFile.limit` as the number of bytes to download from `offset` before that download is automatically cancelled; zero means unlimited. `GetFileDownloadedPrefixSize` measures contiguous availability from an offset, and `ReadFilePart` reads bytes already in TDLib's file cache. These APIs support demand-driven retrieval and bounded requests. They do **not** document deletion of arbitrary old ranges within a still-needed file. Cancelling a request does not establish that downloaded ranges are discarded. `DeleteFile(fileId)` deletes the TDLib-managed file as a whole, which is unsuitable while playback still needs it.

`OptimizeStorage` is a file-level storage optimizer that removes files and returns storage statistics. In the pinned binding, empty `chatIds` and `excludeChatIds` mean no chat filter/exclusion; an empty `fileTypes` means TDLib defaults. Current `CacheManager` passes video and document types, a one-hour immunity delay, and target size. This optimizer is not a per-session range eviction API and the app does not verify that its result achieves a physical disk target.

## Baseline limits and gaps

- The configured 32 MiB read-ahead is a request window, not a cumulative on-disk limit. Repeated distant seeks may leave prior ranges in the TDLib-managed file; repository code does not prove otherwise.
- Cleanup reasons over `downloadedSize` for app-tracked IDs, at most 256 access-tracked files plus 64 pinned IDs per sweep. It selects files, not byte ranges. Untracked payloads are invisible to this candidate list.
- Cleanup protects pins, active playback, continue-watching and last-played items, active downloads, scheduler chunks, prefetch and priming. If safe candidates are absent or insufficient, it can finish over the target.
- `CacheManager.calculateDynamicQuota` uses available bytes on `cacheDir`, although TDLib payloads live under `filesDir/tdlib_files`; the code does not verify both paths share a volume. It computes 15% of free space, then enforces a 256 MiB floor, so the result may exceed the intended fraction or available bytes on a low-storage device. The maximum is the lower of 2 GiB and the user's preference.
- `TelegramCachePruner` exposes stale 20%, 256 MiB and 5 GiB constants, but delegates calculation to the 15%, 256 MiB and 2 GiB `CacheManager` policy. Its tests assert the stale constants and do not exercise runtime quota or physical storage behavior.
- `storageStats` is an observation of directory sizes, not an enforcement mechanism. The current quota is not separated into temporary-streaming, offline, database and reserve budgets.
- Offline pins intentionally start full-file downloads and are protected by app cleanup. This behavior must remain distinct from temporary streaming.

## Required architectural change for a bounded policy

Treat TDLib range downloads as demand hints, not as a guarantee of bounded retained bytes. A reliable cap requires either (a) a supported TDLib policy/API demonstrated to reclaim old ranges while preserving active playback, or (b) a separately managed bounded staging store whose lifecycle and source semantics are verified. Until one is proven, monitor actual free space and payload-directory growth, stop speculative work at a safety threshold, cancel nonessential requests, and return a recoverable storage error when required playback bytes cannot be safely obtained. Never delete an active TDLib file to satisfy a quota.

Before describing storage as bounded, run long playback and repeated far-seek scenarios on target devices and record filesystem free bytes and `tdlib_files` physical bytes over time, including after cancellation, playback end, app restart and cleanup. Include offline pins, concurrent local/cast playback and low-storage TV hardware.

## Evidence and verification

- Audited sources: `CacheManager.kt`, `TelegramCachePruner.kt`, `TelegramNativeFileGc.kt`, `OfflineCacheManager.kt`, `TelegramChunkScheduler.kt`, `TelegramPreCacheEngine.kt`, `TelegramFileGateway.kt`, `TelegramAuthManager.kt`, and the checked-in generated `TdApi.java`.
- No physical storage trace or target-device run is available in this audit. No cumulative range-retention claim is made.
- The legacy cache tests include arithmetic examples and mocked LRU behavior; they do not invoke Android `StatFs`, TDLib cleanup, or device storage measurement.
