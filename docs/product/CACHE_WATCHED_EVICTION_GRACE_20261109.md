# Bousygram cache review — protect recent watched videos

## Behavior corrected

Before this change, automatic safe cache cleanup always queued all safe
watched/completed videos in its inspected window for eviction, **including when
the media cache was below the configured 160 MiB maximum or lower dynamic
device quota**. That also bypassed the two-minute recent-access guard used by
the normal LRU pass. This could force immediate re-downloads after a user
finished an episode, adding network traffic, flash writes and loading time.

## Updated policy

- If full TDLib directory + tracked sample indicates cache is **at or below
  the current dynamic quota**, the cleanup exits without deleting media.
- If cache is above quota, watched and completed videos are *preferred*
  for eviction but only after the **same two-minute recent-access grace**
  applied to every other candidate. Never delete more than the estimated
  quota pressure requires.
- Untracked timestamps remain old/evictable by existing LRU policy, but
  future timestamps are treated as recent so clock adjustments don't cause
  immediate deletion.
- Existing protected-current-video, offline pin, continue-watching,
  downloading/lease/chunk, prefetch, and source-account safeguards remain.
- The 200 MiB figure remains a disposable media-cache budget, not a
  total installed-app storage guarantee.

## CI and device checks

JVM tests cover watched recent access, grace-boundary behavior, future
clock timestamps, no-pressure preservation, and low-storage zero quota.
Exact-head Enterprise lint/JVM/debug APK ABI tests, emulator smoke, and
TDLib generation are required before merge.

Physical ARM32/ARM64 device tests should compare completed-video replay
startup, cache size, cleanup counts, storage pressure and playback stalls.
No performance improvement is asserted without actual device evidence.
