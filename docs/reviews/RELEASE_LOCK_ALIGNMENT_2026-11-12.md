# Bousygram — UI & functionality release-lock alignment

Date: 2026-11-12
Status: **DEBUG CANDIDATE WORK IN PROGRESS — production release NOT locked**
Prior historical baseline: `deb4442523e6b3a982b59e2690c0d65214a99c4f`
Reviewed starting main: `682db330193d73f91e80656d8fbbd7f1843a9ed8`

This is a follow-on status record to
[UI_FUNCTIONAL_RELEASE_LOCK_REVIEW_2026-11-12.md](../reviews/UI_FUNCTIONAL_RELEASE_LOCK_REVIEW_2026-11-12.md).
It does not rewrite or claim to have completed physical-device testing.

## Hardening status by requirement

| User-facing requirement | Source / implementation | Release-gate status |
| --- | --- | --- |
| Freeze the established seven themes and mascot design | Theme architecture / responsive UI hardening already on main (#174, #175). No redesign introduced here. | Emulator responsive UI checks available. Real 720p/1080p ARM TVs, font scaling and overscan still needed. |
| Media Library bounded viewport, Back and D-pad focus | #175 merged. See [UI visual qualification](../ci/UI_VISUAL_QUALIFICATION.md). | Real TV navigation and screenshot review pending. |
| Extras search, filters and safe inset viewport | #175 merged. | Short landscape device confirmation pending. |
| Protect the selected Extras title and episode when an embed navigates | #176 **merged** (previous review's open-PR status is historical). | Automated identity regression tests in the codebase; provider availability and redirects unverified. |
| Prevent Extras providers silently dropping anime episode/season | [PR #177](https://github.com/Vaydelux/Bousygram/pull/177). | Exact-head Enterprise, responsive UI and emulator checks required before merge. |
| Keep remote embeds isolated, no unintended PiP/Cast/download claims | Existing MediaForge WebView guard; provider controls and metadata transport corrections in #177. | Upstream provider compliance not guaranteed. |
| Preserve manual Pause, correct .mkv/.ts seek and subtitle focus in Media3/libmpv/LibVLC | Native player engine and seek intent policies already on main. | **Physical ARM32/ARM64 playback evidence required.** Emulator success alone cannot qualify it. |
| Bound foreground Telegram streaming on small-flash TVs | [PR #178](https://github.com/Vaydelux/Bousygram/pull/178), **debug-only / disabled-by-default** finite native request windows. | **P0 remains open**: multi-episode cumulative data and real 8 GB TV soaks not yet proven. See [#142](https://github.com/Vaydelux/Bousygram/issues/142). |
| Limit RAM/disk on long ARM32 sessions | Existing RAM/cache policy, new finite-request opt-in alternative, and read-only physical-device measurement tools. | 60-minute ARM32/ARM64 observations and baseline comparisons pending. |
| Exact source, clean CI, known debug APK checksums | Trusted Android Enterprise / Emulator / Responsive UI workflows and debug publisher. | PR #177 and #178 must have green exact-head runs. Prior debug release does not contain these branches. |
| Production signing / stable GitHub Release | Protected signing + qualification workflow designed; [issue #38](https://github.com/Vaydelux/Bousygram/issues/38). | Production signing evidence, actual devices, reviewer approval and configured protected environments pending. |
| Enforce PR and CI checks before main merges | Workflow gates exist; repository main branch reports no ruleset / branch protection. | **Owner must add branch ruleset**; no admin modification is available through connected GitHub integration. |

## Lock rules

1. **Visual language frozen:** no new theme overhaul, navigation rewrite or provider CSS injection. Fix defects with focused tests.
2. **No unreviewed merges:** do not merge PR #177 or #178 while checks are queued, cancelled or failed. An unrelated older successful commit is not a pass for their new heads.
3. **No opportunistic cache deletion:** never delete the active TDLib file or pinned media to meet the 160/200 MiB *eviction target*. Even the experimental positive DownloadFile limit does not guarantee an aggregate cache limit.
4. **Device matrix:** real 8 GB ARM32 TV, ARM64 TV, phone and tablet; include 720p/1080p UI, D-pad/Back, subtitle dialogs, text scaling, landscape safe areas, .ts/.mkv near-EOF seeks, manual pause/recovery, and 30–60-minute storage/PSS checks.
5. **Only qualify the tested exact APK:** record source SHA, ABI, APK SHA-256 and redacted observations. Debug APKs must not be republished as production-signed stable assets.
6. **Stable publication is blocked** until #142, the required physical playback cases, source-check workflow results, owner-controlled branch protection and issue #38 signing gates are satisfied.

## Debug provenance

Previously published three-ABI debug release:
[`v1.0.0-debug.manual.8cc270f69b92`](https://github.com/Vaydelux/Bousygram/releases/tag/v1.0.0-debug.manual.8cc270f69b92).

It contains the merged #176 identity fix but **does not** contain unmerged #177 Extras alignment or #178 bounded foreground experimentation.
