# Storage limits and cleanup: current behavior

## Audited configuration

`CacheManager` currently defines a 2 GiB maximum, 256 MiB minimum, 15% of available free space, 32 MiB forward window, and 8 MiB next-episode head preload. The quota calculation reads `StatFs(cacheDir)`, takes 15% of available bytes, then clamps the result to `[256 MiB, min(2 GiB, user preference)]`. The minimum clamp can exceed free space and the 15% calculation is not based on total device capacity. The TDLib payload location is `filesDir/tdlib_files`; no same-volume check exists.

`TelegramCachePruner` still publishes legacy constants of 5 GiB and 20% (also 256 MiB minimum), but its runtime methods delegate to `CacheManager`. Tests currently assert these legacy values, so they describe neither the active quota policy nor physical behavior.

## Cleanup behavior

App cleanup gathers access timestamps and pinned IDs, requests TDLib file status, and considers at most 256 tracked files per sweep. It tries watched media first, then oldest eligible media if tracked downloaded size exceeds the calculated quota. It preserves explicitly pinned files, active playback, continue-watching, last-played, active downloads, current scheduler chunks, priming/cache-ahead and focus prefetch. Deletion is requested with TDLib `DeleteFile`, not by deleting paths from TDLib's private directory.

The sweep has no inventory of every TDLib file and cannot reclaim individual ranges. Its reported freed bytes are based on TDLib's logical `downloadedSize` after successful delete responses; physical freed bytes are not verified. Native `OptimizeStorage` runs separately and may remove TDLib files according to its own policy, but current code does not reconcile it with actual free-space reserve or verify a physical target. Offline pinning uses an unlimited `DownloadFile` request and persists protection until unpin.

## Findings that block a bounded-storage claim

1. The quota is a cleanup target, not a hard cap on writes or downloads.
2. Range request size and speculative read-ahead do not bound cumulative retained bytes across seeks.
3. Free-space measurement and payload storage may be on different volumes.
4. The floor can mandate a cache allowance larger than remaining storage.
5. Untracked TDLib files and database/app data are not covered by the tracked candidate total.
6. Active files cannot safely be deleted; when safe candidates are insufficient, cleanup leaves storage over target.
7. No device measurements have been collected to establish physical allocation, cleanup delay, or TDLib range retention.

## Policy direction

Separate temporary streaming budget, offline media budget, metadata/database footprint, and minimum free-space reserve. Base decisions on free space of the actual payload filesystem and user/device constraints; allow the temporary budget to become zero on low-storage hardware. Suspend speculative and background requests before deleting anything, cancel nonessential pending work, run TDLib-aware cleanup only on unleased/unpinned files, then remeasure. Preserve active playback and offline pins. If required bytes cannot be made available safely, stop with a recoverable storage error.

Do not implement partial-range deletion by manipulating files beneath TDLib's managed directory. The pinned API exposes whole-file `DeleteFile` and file-level `OptimizeStorage`, not arbitrary range removal. Any future bounded design must establish byte ownership and reclaimability experimentally or through an explicit supported API.

## Empirical baseline

| Measurement | Result |
|---|---|
| Installed device free-space samples | Not collected |
| Physical `tdlib_files` growth during long sequential playback | Not measured |
| Retained ranges after distant seeks/cancellation | Not measured |
| Physical bytes reclaimed after TDLib deletion/optimizer | Not measured |
| Low-storage Android TV playback behavior | Not tested in this audit |

These are open validation items, not passing results. See [STREAMING_CACHE_ARCHITECTURE.md](STREAMING_CACHE_ARCHITECTURE.md) for the code path and required evidence.
