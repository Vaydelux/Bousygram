# Bousygram — Responsive Visual, Motion and Playback UI Review
**Date:** 12 November 2026
**Base:** `main` at `351a7f68d7408bb6c27cc21b44b99b30260afd8e`
**Scope:** Presentation, font mapping, adaptive layouts, loading banners, motion policy, emulator/UI tests, release process.

## Summary / decisions

1. Preserve the existing **Space Grotesk** and **Inter** TTF resources, bundled offline at `res/font`, and their OFL notices under `assets/licenses`. Do not download fonts at runtime or add a font dependency. Apply Space Grotesk to display, headline **and title** roles; Inter to readable labels and body copy.
2. Centralize reduced-motion and low-memory behavior with `BousygramMotionPolicy`. No decorative infinite animation when Android disables animation or `ActivityManager.isLowRamDevice` is true. Keep focus rings, content labels and progress/status text visible regardless of motion.
3. Favor opaque, semantic `MaterialTheme.colorScheme` surfaces over per-screen colors. This pass adopts theme primary for the cinematic scrubber instead of an unrelated hardcoded red. Black/white video edge-band overlays remain intentional to protect legibility over unknown video content.
4. Align shared card, tab and panel radii (12dp/10dp/16dp respectively) with common tokens; preserve large D-pad focus outlines and 56dp TV controls / 48dp touch targets. The MainActivity screen fade and all legacy dashboard focus zooms now honor the same reduced-motion/low-RAM policy. Avoid massive fullscreen transition layers.
5. Reduce redundant Mode Selection labels. Heading + one supporting description precedes the two media options; mobile and short-landscape layouts remain single-column or two-column based on measured available space.
6. Make the buffering/reconnect banner width bounded by **actual parent constraints**, with weighted title/subtitle and ellipsis. On short screens, use fewer lines and a shorter static opening skeleton. No fake buffering percent, focus-stealing surface or new media requests.
7. Keep account/Telegram and the MediaForge playback boundaries unchanged. The visual update must not turn UI previews into TDLib startup or change user pause/seek intent.

## Areas reviewed

| Area | Findings / disposition |
|---|---|
| Theme + typography | Inter and Space Grotesk already bundled; titles previously used Inter while page headings used Grotesk. Fixed role inconsistency; preserved seven palettes and contrast tests. |
| Adaptive layout | `AdaptiveMetrics` uses window bounds and TV feature detection, not CPU ABI. Phone landscape, tablet and TV use different chrome based on available height. Narrow-window status banner and heading adjustments reduce overflow risk. |
| Navigation / focus | `BousygramSectionTab`, `TvFocusSurface`, app screen transitions and legacy TV dashboard focus zooms share the reduced-motion/low-RAM policy. Focus rings remain independently visible and selection semantics are preserved. |
| Player UI + playback | `PlayerLoadingBanner` is decorative and only reports true opening, reconnecting and buffering states; `playerLoadingPhase` gives errors priority so Retry remains accessible. Cinematic scrubber uses existing seek callbacks and an on-theme progress accent. Native playback/engine state machine is intentionally unchanged. |
| Loading / overdraw | Shimmer now follows the same low-memory motion policy. Static fallback and fixed decorative backdrop avoid infinite low-RAM GPU work; player status never invents a buffer percentage. |
| Theme gallery / cards | Native Compose, responsive columns and semantic colors already shared. Shape tokens aligned for a clearer, quieter visual language. |
| Code/process | UI components remain shared, but `PlayerScreen.kt` (~4k lines) and `TvHybridHomeDashboard.kt` (~2.5k lines) are still large. Recommend future **behavior-preserving** extraction after device qualification rather than a rewrite in this design PR. |
| CI and packaging | Existing Enterprise, responsive UI emulator, media emulator and verified debug prerelease workflows remain authoritative. No secrets, app permissions, networking code, or database migrations changed. |

## Verified code-review follow-up

- A previous exact-commit Enterprise run identified a stale unit test expecting the old 8dp card corner. Updated `UniformDesignMetricsTest` to assert the actual shared 12dp card / 16dp panel / 10dp tab hierarchy. This was a test contract regression, not a renderer crash.
- Converted the main-screen fade and all five legacy dashboard focus-scale animations to use the central motion budget. These UI changes do not touch player commands or Telegram message lifecycle.
- Keep distinct actions explicit: the UI now only changes rendering and presentation; functional media operations still need physical-device verification.

## Automated acceptance

- Unit: `BousygramMotionPolicyTest`, `ThemePresetTest`, `PlayerLoadingPhaseTest`, and existing playback state/recovery tests.
- UI instrumentation: `UniformPremiumDesignInstrumentedTest` verifies Space Grotesk vs Inter role mapping and theme screenshot capture; `PlayerLoadingBannerInstrumentedTest` checks 320dp portrait and 540dp short-landscape/high-contrast bounds.
- Existing screen tests: Library/Favorites navigation, Mode Selection, theme gallery, D-pad, Extras screen, focused Player/CinematicScrubber tests.
- `tools/ci/run_explorer_ui_smoke.sh` and `tools/ci/run_emulator_smoke.sh` include the new loading-banner test.
- Inspect visual screenshot artifacts across the seven themes; no claim of pixel approval without viewing those artifacts.

## Known limitations / physical QA still required

- Android emulator x86_64 pass does **not** prove ARM32/ARM64 native decoder performance, subtitle readability at 10 feet, or 60-minute memory stability.
- For phones: test 320/360/480dp portrait, 540/720/900dp landscape, 1.0/1.5/2.0 font scales, gesture nav, rotated playback, device back, and media dialog focus.
- For TVs: test 720p low-RAM ARM32 and 1080p ARM64 with D-pad and large fonts, repeated playback and pause/seek/fallback, long titles, menu scroll, focus restoration, subtitles and audio routes.
- Playback hardening: separately validate .ts unindexed seeking, .mkv fallback and audio/video resync using real Telegram sessions and physical decoders. This design pass does not claim to solve decoder defects.
- Confirm device screenshots for every palette; if Light theme scrubber accent is insufficient against actual dark videos, tune video-specific inverse contrast without introducing a global hardcoded accent.
- No unconditional promise of zero dropped frames, instant loading, or no 32-bit memory pressure based on UI CI alone.

## Review order before merge/release

1. Android lint + JVM tests on exact PR commit.
2. Android Explorer Responsive UI smoke and visual evidence.
3. Emulator media and D-pad smoke, without changing playback behavior.
4. Enterprise full checks (including native bindings).
5. Merge only all green; verify `main` workflow commit and publish verified ARM32, ARM64 and universal debug APKs.
