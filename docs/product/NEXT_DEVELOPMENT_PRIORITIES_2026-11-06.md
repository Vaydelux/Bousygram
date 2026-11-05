# Bousygram — main-only development priorities

Updated: 2026-11-06 (Asia/Manila).
Source baseline: PR #100, squash-merged as `d45916a`.
Status: **device qualification is pending**; passing JVM, x86 emulator and ABI build gates do not demonstrate authenticated Telegram recovery or stable native decoding on real TVs.

## Repository workflow

- `main` is the single active development branch. Future changes should be committed directly to `main` at the user's request; preserve atomic commits and independent rollback points.
- Retain GitHub Actions' build, lint, unit, emulator and ABI gates on every relevant `main` commit. A direct push is *not* proof of release-readiness: inspect the resulting CI runs and revert immediately if a required gate fails.
- Branch cleanup uses `.github/workflows/archive-prune-branches-once.yml` with the explicit `[archive-prune-branches]` marker. It saves branch tips as `archive/branch-cleanup/<run-id>/...` **tags** and deletes only non-`main` branch refs after verifying each tag points to the original commit. These archive tags are not live development branches.
- Direct commits do not restrict other collaborators from creating branches. Keep the cleanup opt-in rather than deleting other people's work on every push.

## Next milestones, in dependency order

| Order | Delivery focus | Required implementation or evidence | Exit gate |
| --- | --- | --- | --- |
| **P0.1** | Real TDLib reconnect/restart | Run authenticated startup, network-loss while downloading and while seeking, process restart, app force-stop/reopen, account revoke and account switch against physical devices. Capture connection/auth state transitions and private account isolation evidence without tokens or URLs. | No permanent Preparing spinner, no cross-account callback or byte leak, no normal-network-loss logout, playback resumes when intended. |
| **P0.2** | ARM32 long-session memory | Use the physical PSS sampler to establish cold/warm cache curves on an `armeabi-v7a` low-RAM TV. Repeat MKV/TS/MP4 playback, seeks, source navigation and 30–60 minute sessions. Attribute native heap vs Java/graphics pressure before changing cache limits. | Stable process identity; no repeatable runaway memory growth, OOM/LMK or degradation under comparable trials. Investigate trend before setting numerical budgets. |
| **P0.3** | Native engine stability on both ABIs | Run 30 opt-in native init/release cycles plus at least 50 real-media ExoPlayer/MPV/LibVLC swaps on ARM32/ARM64. Include surface recreation, manual Pause across buffering, subtitles, seeking, foreground/background and codec errors. | Real video and audio; no crash/ANR, orphan surface or loss of user pause intention. `LibVLC` remains explicit opt-in until accepted. |
| **P1.1** | Measurable playback recovery | Add privacy-safe engine-specific first-frame, seek-confirmation, native decoder/drop, stall and fallback counters; validate MKV tail Cues and indexed/non-indexed TS without invented seek success or resetting to 0:00. | Reproducible per-file before/after diagnostic captures; no indefinite waits and no false auto-resume after user Pause. |
| **P1.2** | Resource budgets and long-session browsing | Based on ARM32 data, refine thumbnail, status, catalog and image cache bounds; test `onTrimMemory`, account logout, multi-hour scrolling and repeat playback. | No growth from inactive sources; active playback retains necessary media ranges and UI focus. |
| **P2.1** | Automated hardware-evidence comparison | Add fixture-tested parsing/comparison of sanitized multi-cycle PSS histories and gated optional device-lab runs. Keep human-authenticated scenario checks independent from machine measurements. | Reports explicitly distinguish observed, failed, missing, skipped and operator-verified statuses. |
| **P2.2** | Candidate release and user-facing polish | Verify D-pad/touch, subtitle styling, non-seekable media explanations, restart experience, theme/accessibility and exact-SHA release APK identity. | Green required CI plus signed candidate and independently attested ARM32 + ARM64 device matrix. |

## Stop conditions

Stop releases on stale-account data exposure, lost manual-pause intent, crash/ANR, memory runaway on physical ARM32, unsupported decoder/surface state being reported as successful, missing qualified APK identity, or failing required CI. Do not claim a bug-free release without real-device evidence.

## Where to start

The first delivery is **P0.1**: physical TDLib account-reconnect tests. Use `docs/qualification/PHYSICAL_DEVICE_PRIORITY_GATE.md` and `tools/qualification/Capture-BousygramPhysicalSoak.ps1` from merged PR #100, then follow **P0.2** and **P0.3**. Keep the three results as separate test categories.

## P1.2 source implementation (November 6, 2026)

- Bounded access-order TV focus/viewport history, including concurrent-safe updates.
- Bounded incoming Telegram new-message list while preserving deep-page scroll and cursor.
- Per-category Android memory trim policy with reclaimable offscreen catalog metadata.
- Lazily configured Coil 2.7 decoded-image cache with a smaller ARM32 budget and logout/critical-pressure cleanup.
- Tests for LRU history, high-volume incoming media, trimmed memory levels and image-cache policy.
- [Resource policy and physical validation plan](../qualification/LONG_SESSION_RESOURCE_BUDGETS.md).

The source milestone does not replace the ARM32/ARM64 physical-device qualification gate.
