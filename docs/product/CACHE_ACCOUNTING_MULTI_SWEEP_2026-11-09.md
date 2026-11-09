# Bousygram — Cache accounting and rotating TDLib eviction

## Audit finding

Cache cleanup inspects at most 256 tracked TDLib file IDs on each run (including
up to 64 pinned IDs). The oldest access timestamps were always selected.
Consequently:

1. The sampled `downloadedSize` sum could be lower than the whole
   `filesDir/tdlib_files` directory size, causing an incorrect assessment that
   the media cache was below its configured target.
2. When oldest files were protected or missing, newer tracked IDs were never
   considered for eviction even across repeated sweeps.
3. The **final** `DeleteFile` eligibility recheck still used
   `isFileActive` (all queued episodes). This canceled the earlier
   PR #129 improvement, which correctly uses
   `isCacheProtectedPlaybackFile` for only the current video.

## Changes

- Use a rotating 256-ID **maximum** TDLib lookup window. Pinned IDs retain up
  to 64 inspection slots; the remaining slots advance through sorted, unpinned
  tracked IDs. The cursor is reset when the active account changes.
- Measure the full scoped `tdlib_files` directory size as a conservative
  **quota-pressure input**. The sampled tracked size is retained as a fallback
  if the directory cannot be measured or is smaller than known tracked bytes.
  A measurement includes untracked and pinned media but **does not authorize
  deleting either**. Quota pressure may remain above target for protected data.
- Retain a maximum of 256 concurrent `GetFile` lookups and a single eviction
  worker. The cleanup still deletes only explicitly tracked, checked and
  confirmed TDLib files.
- Fix the final TDLib deletion guard to use the active-media-specific
  protection, consistent with PR #129. Offline pins, ongoing downloads,
  continue watching, in-progress chunks, active range leases and background
  prefetch protection remain in effect.

The `remainingTrackedBytes` result remains a **sample estimate** of the
current inspection window. It is not used as a whole-cache metric. The
Settings storage screen uses directory measurements instead.

## Testing and limitations

JVM tests cover a 700-ID tracker with complete coverage across three
256-ID windows, wraparound, page limits, zero entries and quota pressure
when physical disk usage exceeds inspected bytes.

After PR CI passes, test on a physical ARM32 and ARM64 Android TV:
open a long channel, accumulate more than 256 tracked videos, play and seek
one item, run cleanup, verify active playback continues and pinned downloads
remain; then inspect free storage and reopen an evicted queued video to verify
a safe fresh download.

This is not a promise that the full installed application or offline media
stays under 200 MiB. Scanning the full directory consumes filesystem I/O,
and repeated cleanups should be profiled on slower flash devices. Device
measurements are still needed to confirm actual storage savings.
