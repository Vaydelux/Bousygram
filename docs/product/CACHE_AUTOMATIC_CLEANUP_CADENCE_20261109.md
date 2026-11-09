# Automatic TDLib cache cleanup — pressure-aware cadence

Source-level finding: `CacheManager.markFileAccessed()` previously
re-requested a full TDLib cleanup **on every access**, immediately after
persisting the new access timestamp. A single active playback session can
invoke this often, repeatedly walking `tdlib_files`, sorting tracked file
records and issuing bounded TDLib `GetFile` calls.

The cleanup executor already coalesces *concurrent* requests; it did not
prevent repeated sequential sweeps during a movie.

## Implementation

Keep the recency update for each file access, but throttle the expensive
automatic sweep request using a monotonic clock and account-specific marker:

| Available internal storage | Auto-sweep request cadence |
|---|---|
| Normal | At most once per 60 s |
| Low (below 500 MiB) | At most once per 15 s |
| Critical (below 250 MiB) | At most once per 5 s |

Pressure classification follows the existing `StoragePressurePolicy` and
its cached `StatFs` measurement. If the measurement is unavailable, use the
normal cadence rather than assuming critical storage from missing evidence.

This rate-limit applies **only to markFileAccessed**; manual Settings prune
and the explicit unpin cleanup entry point remain immediate. The existing
single-worker coalescing, account checks, TDLib lease/pin protection and
rotating bounded GetFile window are unchanged.

Account invalidation clears the marker. A switch to a different account
never inherits the previous account's cooldown.

## QA

Deterministic JVM tests exercise cadence boundaries, low/critical pressure,
clock-reset behavior and the shortened interval after pressure worsens.
Exact-head Android Enterprise Kotlin/JVM/lint + ABI matrix, emulator smoke
and TDLib generation are required before merge.

Actual eMMC I/O improvements and disk availability need ARM32/ARM64 hardware
measurements. This does not claim that the 200 MiB disposable-media budget
limits the total app or pinned Telegram data.
