# Bousygram v2 — ISO-Aligned Engineering Review

**Date:** 2026-10-31  
**Review branch:** feature/v2-unverified-implementation-20261031  
**Draft PR:** https://github.com/Vaydelux/Bousygram/pull/44  
**Status:** Code fixes committed; **not ISO-certified**, and **not release-qualified**. GitHub's exact-head build and instrumentation checks are required. User-reported physical TV/media problems are not disproved by a source review.

## Standards interpreted correctly

- [ISO/IEC 25010:2023](https://www.iso.org/standard/78176.html) supplies a **product quality evaluation model**, not an automatic certificate when code follows guidelines. Use its nine product quality characteristics as review categories.
- [ISO 9241-210:2019](https://www.iso.org/standard/77520.html) (confirmed in 2025) informs human-centred design: capture intended users/context (TV remote, phone touch, tablet), evaluate with them, iteratively improve. Source-only edits do not establish usability.
- [WCAG 2.2](https://www.w3.org/TR/WCAG22/) is primarily for web content. Its focus-visible, focus-not-obscured, accessible labels and target-size principles are useful design references for native Android. **Do not claim WCAG 2.2 AA conformance for an untested native app.**
- Security and privacy risks are tracked against product-quality security and secure-development best practices. ISO/IEC 27001 is an organizational ISMS standard; an Android APK cannot itself be declared ISO 27001 certified by code inspection.

## Evidence-backed findings and corrections

| Priority | ISO 25010 quality aspect | Finding in implementation branch | Corrective work | Validation needed |
|---|---|---|---|---|
| P0 | Security / functional suitability | TDLib/media pagination callbacks may be delivered after account/session change | Added `AccountSession.isCurrent(owner)` to Saved/Chat/Topic load and pagination callbacks; scope thumbnail cache keys by account generation | Logged-out and switched-account callback races, no other account metadata |
| P1 | Reliability | Previously cached Home cards disappeared if a refresh emitted empty/failed data | Keep latest valid list on transient failure; bounded account-scoped snapshots with cursor and source invalidation | Home→Player→Home, offline return, transient TDLib failure |
| P1 | Performance efficiency | Opening forum source queried entire media history before user picked a topic | Open real forum topic directory first; avoid all-chat video fetch | Large forum TDLib query count and first usable grid time |
| P1 | Performance efficiency / security | Background seek-frame generation attempted eight random HTTP retrievals through private loopback stream | Limit seek-thumbnail generation to readable local file; preserve timestamp preview for streams, cache by session generation | MKV/TS streaming seek, rebuffer, memory/bandwidth, authorized preview |
| P1 | Interaction capability | Overwide mobile header and undersized labels/actions | Adaptive two-row phone toolbar; 48dp min actions; larger TV labels; compact transport row | Narrowest phone window, TalkBack, 720p TV and 4K TV |
| P1 | Interaction capability | Double-focus targets and D-pad ring hidden on TV | Ported PR #41 focus semantics into TV navigation, cards, auth, menus and seek; explicit 3dp focus border | Physical D-pad input, focus contrast and no clipping/trap |
| P1 | Functional suitability | Library and Home had different browsing patterns and horizontal overflow | Unified one Saved/Channels/Groups grid explorer; real source IDs and forum topic drilldown | Saved, channel, basic group, forum General and other topics |
| P1 | Reliability / usability | Back, Start Over and related tray navigation could conflict | Compact HUD with distinct actions, priority-controlled player keys, topic-aware related queue, return focus | On-device long play, Start Over, Back, D-pad Down/Up and unknown duration |
| P2 | Maintainability | Source-type filtering and tray scoping embedded in UI code | Extracted pure `explorerSources` and `relatedSourceQueue` functions with regression test sources | JVM unit and instrumentation tests |
| P2 | Maintainability | Unclear quality evidence / overclaiming enterprise status | Phase/commit ledger, draft PR review, measurable release gate and this matrix | Successful exact-SHA CI and independent device review |

## Usability acceptance checklist

- TV navigation: every focusable item has distinct visible outline; no duplicated focus nodes, clipping, traps, inadvertent media selection, or app termination. Full-screen remote OK/Back/Up/Down/Left/Right interactions are tested on physical TV.
- 3 source tabs (Saved/Channels/Groups) retain actual Telegram identity. Groups with forum flag show **actual** topics; non-forum groups open media directly. Empty, loading, permission, failure and retry states are distinguishable.
- Cached directory cards appear on warm return without full screen blocking; account switch/login invalidates prior private data.
- Player indicates real position/duration; unknown-duration media uses truthful unavailable indication, no invented thumbnails. Start Over seeks same item to zero, Back does not quit unexpectedly, no skip from seek D-pad.
- Phone: controls meet mobile touch sizes, row wrapping is safe, TalkBack content descriptions are meaningful, rotation and small-width split-screen remain usable.
- Tablet: grid density adapts to dp/window and focus/touch input without horizontal overflow.
- Animation is non-blocking, restrained, reduced-motion-friendly; user controls remain usable when buffering, offline, on errors or while the user changes audio/subtitle tracks.

## Required quality gates — NOT PASSED until evidenced

1. **Build and ABI:** TDLib binding compatibility; ARMv7/ARM64 library inventory; debug Kotlin compile, lint, JVM tests, debug APK packaging and AndroidTest compile with exact commit SHA.
2. **Automated UI:** real emulator runner key injection; player and explorer state transition tests; accessibility semantics; process/activity restoration tests.
3. **Authenticated real devices:** one low-resource ARMv7 Android TV, one ARM64 TV, Android phone/tablet. Saved/Channels/Forum/Group, MKV/TS/MP4, codec fallbacks, long-play resume and logout/clear cache.
4. **Security and resilience:** private stream tokens and capability revocation, credential handling, rebuffer/seek stress, storage growth/eviction, unauthorized cache access, low storage, intermittent network and app process death.
5. **Metrics:** median/p95 navigation-to-focus time, Home warm return, first rendered frame, seek-to-frame time, jank, stream rebuffer and TDLib storage size. Record device, ABI, OS, network and baseline; no fabricated numeric targets.
6. **Review and release:** exact-head CI all green, no unresolved P0/P1, tested build checksum/artifacts, signed production only with protected secrets and qualified devices. Source review alone is insufficient.

## Current restrictions / no false claims

- Draft PR #44 is **not merged**; no exact-head QA pass had been reported when this review was authored.
- Device test and baseline metrics are **NOT RUN**; a pending CI run is not a passing run.
- Android performance and codec support depend on actual ARM/Android devices and file fixtures. Unsupported/corrupt streams cannot be guaranteed playable.
- The legacy TMDB **metadata enrichment inside Telegram Media** is a separate preexisting path; future TMDB/AniList Extras and WebView are not implemented.
- This code review is a meaningful quality improvement, **not certification** to ISO/IEC 25010, ISO 9241-210, WCAG or ISO/IEC 27001.

**Release decision:** BLOCKED pending compile, CI/emulator and physical-device qualification.
