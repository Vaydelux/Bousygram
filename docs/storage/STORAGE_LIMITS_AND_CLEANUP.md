# Storage limits and cleanup — active policy and evidence

The previous 2 GiB / 256 MiB floor audit is historical (see Git history).
For the current source, consult [long-session budgets](../qualification/LONG_SESSION_RESOURCE_BUDGETS.md)
and `TvStorageBudget.kt`, `CacheManager.kt` and `StoragePressurePolicy.kt`.

- Disposable media target: 200 MiB (160 MiB TDLib, 24 MiB poster, 16 MiB generated previews).
- TDLib target is at most 15% of available app-data storage and respects a lower managed ceiling. No minimum floor.
- Optional read-ahead is limited to 32 MiB per request and the measured headroom above 500 MiB. Unknown space rejects optional work.
- The cached StatFs snapshot and concurrent writers prevent this policy from guaranteeing a physical free-space reserve.
- Foreground reads, pinned offline files, account databases, installed binaries and WebView data are not covered by the disposable-cache target.
- Whole-file TDLib APIs own deletion. Never unlink live files or attempt arbitrary range reclamation.
- Physical free-space samples and matched playback/seek measurements are required to prove improvements.

See the [physical device gate](../qualification/PHYSICAL_DEVICE_PRIORITY_GATE.md) and [feature freeze](../release/FEATURE_FREEZE_2026-11-21.md).
