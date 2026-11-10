# Playback account-generation / EOF reliability milestone

Baseline: `1746da95b2e0fe728c902917f5ede09060d6c9b8`.

**Status:** source fix and automated regression coverage. **Real ARM32 / ARM64 testing has not been performed by this change.**

## Findings and root causes

1. **Old player can adopt new Telegram identity when saving progress.** `PlaybackProgressTracker.saveMessagePosition`, `getMessagePosition` and `clearMessagePosition` previously resolved `AccountSession.current()` at execution time. Delayed playback callbacks surviving logout/relogin could therefore read, write, or mark a different account's playback history as completed.
2. **Android TV launcher update can adopt the wrong account.** `WatchNextManager.updateProgress` likewise resolved the current account on the IO callback, not the account that opened the player. It could publish old Telegram titles/positions under a newly authenticated account.
3. **Compose disposal cancels its own cleanup task.** `PlayerScreen` launched Android TV Watch Next updates/removal on a screen-owned coroutine while the screen was being disposed, so the task could be canceled before a TV launcher provider request.
4. **Natural EOF can recreate resume history.** A confirmed EOF marks a video completed, but `exitPlayback` and `onDispose` could immediately save the retired decoder's final position again, reopening an unfinished entry for unknown-duration MPEG-TS.
5. **Legacy preference I/O could outlive invalidation.** The legacy resume store uses `SharedPreferences.edit { }` (asynchronous `apply()`). The previous process-lifetime account invalidation handler drained the JSON ledger but did not drain and clear these pending preference writes before revoked-account XML deletion.
6. **Emulator fixture setup failure:** the initial PR emulator run compiled the APKs, then Ubuntu 26.04 APT failed to locate `ffmpeg`. Pinning the emulator workflow to the supported `ubuntu-24.04` runner fixes this test-environment dependency rather than bypassing instrumentation.

## Small, reviewable corrections

- `PlaybackProgressTracker` now accepts an **optional explicitly pinned account snapshot** for per-message progress reads, writes and completion. Existing ordinary callers remain source-compatible, but player-owned calls supply the original verified account generation. The final preferences access uses `AccountSession.runIfCurrent` so invalidation and writes cannot race into another account.
- `PlayerScreen` passes the pinned owner to all five message-progress write points, its resume read, and natural-EOF completion. An invalidated player cannot publish to a subsequently logged-in identity.
- `WatchNextManager.updateProgress` **requires** a verified owner argument; there is no implicit `AccountSession.current()` fallback. The screen schedules the update through the manager's independent, already-existing IO scope; the queued task checks ownership again on execution and at the account-serialized launcher write.
- Natural-EOF Watch Next removal now survives Compose disposal. Shared `shouldPersistSessionExitProgress` policy prevents a confirmed completed item from being rewritten as an unfinished video during Back/disposal, without suppressing legitimate in-progress checkpoints.
- The `BousygramApp` invalidation handler now synchronously calls `PlaybackProgressTracker.invalidateAccountResources()`, which commits a clear to drain all queued legacy `apply()` writes before the account's XML is deleted. If the commit fails, `AccountSession.invalidate()` fails closed and a new account cannot activate. Normal periodic progress writes remain asynchronous to avoid ARM32 playback UI stalls.
- No changes to Telegram media-byte ownership, engine selection, decoder retry loops, login flow, Extras/MediaForge isolation or app theme.

## Tests

- Android instrumentation: synthetic accounts, real account-scoped SharedPreferences, logout drain, verified revoked XML deletion, login to a different account, delayed old-player write, current-player write and delayed old-player completion; the stale operations cannot recreate revoked files or touch the active account's progress.
- JVM: deterministic generation isolation even for a **same-account relogin**; EOF progress suppression for active versus retired episode.
- Existing Gradle Android Enterprise tests, emulator media/remote regression suite, TDLib generation and supported debug APK ABI matrix required on the exact PR SHA before merge.

## Physical qualification gate: still pending

On a real ARM32 TV and ARM64 TV, exercise normal completion, TS without duration metadata, Back/disposal, Watch Next removal and re-addition, logout during a pending video-range request or launcher update, and re-login to the same and another account. Repeat with Wi-Fi interruptions and media keys. The old account's titles, progress and launcher recommendations must never appear under the new account.

Record actual video output, stall/seek behavior and process PSS independently using `tools/qualification/Capture-BousygramPhysicalSoak.ps1` and matching repeated baseline/candidate comparisons. For release, use only the **exact signed APK hashes** with the protected `RELEASE_DEVICE_QUALIFICATION_JSON` and human review. Debug compilation or an x86_64 emulator do not establish physical ABI qualification.

## Risk register

| Risk | Priority | Gate |
| --- | --- | --- |
| Physical TV launcher content provider rejects deletion | P1 | Test OEM-specific behavior; report provider failures rather than claiming attested cleanup |
| Authentication revocation during non-UI asynchronous progress workflows | P1 | Test with authorized real accounts and latency/fault injection; inspect all external write call sites |
| Native MPEG-TS/MKV seek, recovery and subtitle accuracy | P1 | Physical ARM32/ARM64 per-engine codec and seek fixture matrix |
| Long-session memory, TTFF, rebuffer rate, UI D-pad clipping | P1/P2 | Repeated real-device baseline vs candidate measurements |
| Android TV dashboard focus and lifecycle-driven updates | P2 | Real remote navigation plus 720p/1080p/4K screenshots |

Do not mark the application production-ready until all release-blocking risks have reviewed device evidence.
