# Bousygram — reclaim inactive Telegram media without losing Continue Watching

**Date:** 2026-11-15  
**Device report:** Approximately 1.3 GB shown as Bousygram storage/cache on a physical Android TV (not independently measured in CI).  
**Branch:** `fix/tdlib-resume-cache-storage-20261115`

## Historical plan and existing limits

The established disposable-media budget is **200 MiB**: **160 MiB TDLib
media**, **24 MiB Coil posters**, **16 MiB generated thumbnails**.
These are eviction targets, not a cap on APK installation, TDLib
databases, protected downloads, WebView site data, active playback, or
cumulative downloaded byte ranges.

In `docs/storage/STREAMING_CACHE_ARCHITECTURE.md` and
`docs/experiments/BOUNDED_TELEGRAM_FOREGROUND_STREAMING.md`, the app
explicitly distinguishes on-device resume metadata from downloaded
streaming payloads. The foreground scheduler still normally uses TDLib's
`DownloadFile(..., limit=0)`, which can accumulate far beyond 160 MiB
while playing one large video. The debug-only 16/8/4 MiB foreground
window prototype is not production-qualified; even finite requests do
not guarantee cumulative TDLib disk eviction.

The device's 1.3 GB has **not** been attributed to a specific directory.
Android Storage totals may include TDLib databases, installed APK/native
libraries, WebView data, and app caches outside the managed TDLib target.

## This targeted correction

1. **Do not treat Continue Watching as an offline media pin.** The
   resume timestamp, chat/message identity and video metadata remain
   persisted in the existing Room/preferences path. If the old temporary
   file is removed, the next playback downloads needed TDLib ranges.
2. **Do not treat last-played as indefinite video-byte protection.**
   It is still protected if genuinely active/leased, explicitly pinned,
   being downloaded or primed, or focus-prefetched.
3. **Preserve the two-minute recent-access grace**, even for watched
   videos. After a playback session stops, schedule a best-effort
   account-scoped cleanup after that grace, in addition to the existing
   immediate cleanup request. Changing accounts cancels that job.
4. **Improve manual cleanup for large tracked histories.** One explicit
   Settings prune may inspect up to three separate rotating TDLib ID
   pages, stopping early if the quota is met or repeated pages produce
   no deletions. Automatic playback-triggered sweeps remain single-pass
   to avoid flash I/O and TDLib request contention.
5. **Show truthful storage buckets** in Settings: TDLib media directory,
   Android cache directory, and other app files, separately. Explain
   the difference between an eviction target and real physical usage.
   After pruning, report when protected or untracked bytes remain.

### Deliberately not changed

- No deletion of TDLib-managed files by direct filesystem operations.
- No native `OptimizeStorage` sweep that could override explicit
  offline pins: it cannot promise per-file exclusion.
- No eviction of an actively playing, downloaded or leased file.
- No deletion of Telegram channels, saved messages, Room resume history,
  user favorites, or subtitle preferences.
- No unreviewed change to the live Media3, libmpv or libVLC buffer sizes.
- No enabling of the bounded-foreground debug prototype in production,
  and no claim that Android total storage is hard-capped at 200 MiB.

## Qualification

### Automated regression tests
- Resume metadata no longer prevents temporary payload eviction.
- Explicit offline pin, active playback, active chunk/lease, ongoing
  download, cache-ahead and focus prefetch remain protected.
- Manual multi-page cleanup stays capped and stops after consecutive
  nonproductive pages.
- The existing TDLib cache, quota, playback-controller and Android
  emulator suites must all pass at the PR's final commit.

### Physical-TV measurement still required
1. Record Bousygram Settings' **TDLib media folder**, **app cache
   folder**, and **other app files** before cleanup. Distinguish this
   from Android OS App Storage (which includes more categories).
2. Play a large MKV and seek to far-apart ranges; record storage
   growth during playback, after Back, after two minutes, after a
   manual Safe Prune, and after app restart.
3. Check Continue Watching restores the correct timestamp and
   downloads missing bytes without losing channel/topic navigation.
4. Verify a pinned offline video remains available after cleanup
   and a second concurrent player/CAST lease remains unaffected.
5. Repeat on ARM32 and ARM64 under low-storage conditions, measuring
   rebuffering, seek recovery and available flash.
6. For a true rolling disk bound, separately build and compare the
   debug-only bounded-foreground experiment from the original design.
   Do not promote it without a measured 60-minute/multi-episode soak.

**Decision:** This PR fixes avoidable stale-media retention. It cannot
guarantee that a currently playing 1.3 GB video or every untracked TDLib
file is immediately reclaimed, because TDLib exposes whole-file
deletion and cumulative range retention is not bounded by its request
window. Preserve privacy when collecting device evidence.
