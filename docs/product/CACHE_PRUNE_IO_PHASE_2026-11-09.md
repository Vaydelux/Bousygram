# TDLib cache cleanup — reduced filesystem I/O

Context: Bousygram Android TV, especially devices with slow 8 GB eMMC.

## Verified source-level inefficiency

Previously, `TelegramCachePruner.pruneCacheNow` requested
`CacheManager.storageStats` **before** and **after** cleanup, despite using
the first result only to distinguish two success messages. Each stats call
walked the TDLib media directory once directly and again as part of
`filesDir`. The cache eviction pass also measured that directory.

This meant up to **five TDLib directory walks** for one manual safe prune.

## Changes

- The Settings pruner takes one **post-cleanup** stats snapshot and reports
  an accurate above-quota or safe-success status.
- The `filesDir` usage scanner excludes `tdlib_files` in the same traversal
  instead of walking it and subtracting the total later.
- Directory walking is iterative rather than recursive, and ignores symbolic
  links, so slow or malformed directory trees cannot cause recursion loops.
- For a normal manual prune, the TDLib media directory is therefore scanned
  once for quota pressure and once for the final storage reading — **two
  complete TDLib walks instead of five**. The algorithm still traverses
  other app-owned directories to compute their displayed sizes.
- A privacy-preserving `TelegramCachePruner` logcat line reports
  `durationMs`, `deletedFiles`, `freedBytes`, `remainingBytes`, and
  `quotaBytes`. It contains no account identifiers, filenames, or paths.

## Physical-device qualification

On ARM32 and ARM64, run the same safe prune with an idle player and with a
video playing. Filter `adb logcat -s TelegramCachePruner:I` to capture the
duration and reclaimed bytes. Record device free space, video stalls, disk I/O,
thermal state, and PSS before/after. Compare same content and cache population.

No actual physical-device timing or storage gain is claimed here.
Pinned/offline media, active video, byte-range leases and download policy
are unchanged. The 200 MiB number remains a **disposable media-cache
budget**, not a total app size guarantee.
