# PR #157 — Anime Extras navigation + revoked-account progress cleanup

Baseline: `main` commit `21a4827030dd46e1a21b09ca885bbcc6a772da41`.
Scope: targeted fixes and regression coverage; not a full seven-phase certification.

## Confirmed defects and reproduction

1. **Anime server unavailable before page load:** `ExtrasMediaForgeScreen` immediately created an unverified default anime WebView. Some external pages can remain black or enter HTML fullscreen, covering the native server picker. Prior code did not surface top-level WebView HTTP/TLS/page load failures.
2. **Revoked-account resume positions not purged:** `PlaybackProgressTracker` persisted positions in a separate `playback_resume_positions_account_{id}` preferences file that `AccountPrivateDataPurger` did not clear/delete.
3. **Potential asynchronous write/purge race:** Continue Watching wrote through a coroutine-owned `AtomicFile` mutex. The synchronous account-invalidation handler previously cleared the in-memory state without waiting for an active write to finish.

No claim is made that an unverified third-party provider has working media or holds streaming rights.

## Implementation / architecture delta

- A native, D-pad-accessible Anime source chooser renders **before** an external WebView is created. Server selection does not trigger a page load until the user selects Open.
- A fullscreen native overlay and TV remote Menu-key escape return to server selection without keeping the provider's browsing session alive. The Back action exits fullscreen.
- MediaForge WebBridge forwards sanitized, main-frame-only HTTP/TLS/connection failure statuses. It does **not** print raw provider URLs, inspect prohibited DRM, or change native media authorization.
- Account progress preference names have one canonical helper. Revoked-account cleanup clears and deletes that account's resume preference document without altering other accounts.
- Account progress invalidation now drains the `AtomicFile` ledger behind its mutex with a bounded timeout. If persistence cannot be safely drained, invalidation fails closed rather than pretending deletion is complete.
- `ARCHITECTURE.md` now reflects the actual LibVLC new-install default and the separate Extras boundary.

## Added regression coverage

- JVM: account path mapping, invalid IDs, and deterministic write-in-progress/logout barrier.
- Android instrumentation: actual preference deletion/account isolation, Anime chooser before page load, fullscreen recovery buttons, and Menu-key escape from the virtual cursor.
- Existing TV and Telegram media suites are unchanged.

## CI / physical-device evidence

GitHub Actions required before merge: Android Enterprise Gradle and ABI gate, emulator instrumented UI tests, TDLib API generation, official LibVLC ARM32 and ARM64 packaging.

These gates prove compilation, selected behavior on emulator, unit correctness, and JNI/ABI artifact checks. They **do not** establish successful third-party Anime streaming, authenticated Telegram playback on hardware, stable player behavior after hours of use, visual design quality on a 720p TV, or production-release qualification.

## Remaining risk register

| Priority | Risk / workstream | Next evidence or change |
| --- | --- | --- |
| P1 | Third-party Anime source blank page despite successful HTTP response | Test authorized providers on ARM32/ARM64 hardware; document provider availability separately. Do not treat arbitrary HTML as a native playable stream |
| P1 | Account-specific storage beyond known current stores | Complete on-device file-tree and preference inventory across logout/restart and authorization loss |
| P1 | Playback cross-engine seek, pause, subtitles | Real-device media fixture matrix with repeated engine transitions, explicit pause and audio focus |
| P1 | Long-session ARM32 memory/storage | Repeatable physical PSS, disk usage, cache I/O and playback rebuffer soak compared with the baseline |
| P2 | Large player/Home/Extras Compose components | Extract state holders incrementally with captured behavior and golden input-navigation tests |
| P2 | Watched indicators can hold stale remembered state | Publish account-safe reactive progress snapshots; avoid per-card disk reads on each recompose |
| P2 | TV theme and sizing | Capture visual baselines for Dark, Light, High Contrast and 720p/1080p/4K with enlarged fonts |
| P2 | Extras third-party licensing / provider changes | Verify authorization and provider terms per source before any claims of production readiness |
| P2 | Historical docs and feature claims | Audit README, legacy planning files and release notes; distinguish current capabilities from proposals |

## Rollback and release

This is a focused PR. Revert it if the emulator UX, account invalidation, or privacy tests regress. Never merge before the exact-head CI gates pass. Preserve the `main`-only branch policy via the archive-first post-merge cleanup workflow. Debug artifacts are not production approval; use the normal exact-commit publisher after successful mainline gates.
