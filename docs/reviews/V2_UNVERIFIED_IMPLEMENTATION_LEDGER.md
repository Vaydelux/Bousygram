# Bousygram v2 — Implementation-First Ledger

**Status:** SOURCE CHANGES ONLY — UNVERIFIED. The owner requested that lint, compilation, emulator, hardware tests, debugging, and release qualification be deferred until after the implementation sequence. This file is not a certification of working behavior.

**Branch:** feature/v2-unverified-implementation-20261031
**Starting main SHA:** 00bc9a01f9fa4d0e9406c13036036fd8be5a3f42
**Product contract:** APP_GOAL.md, PHASE_PLAN.md, docs/product/ARCHITECTURE_V2.md, docs/agent/PHASE_PROMPTS.md

## Commit sequence / phase ownership

| Phase | Commits | Implemented source slice | Status |
|---|---|---|---|
| P0 | Focus PR #41 is separate | Pre-existing D-pad accessibility/compiler fixes are not yet adopted into this branch; no CI checks are being inspected | NOT CLOSED |
| P1 | 7edc1f5, e4bb34c | Responsive mode chooser, Telegram route, static offline Extras maintenance route | CODE WRITTEN — UNVERIFIED |
| P2 | 09e1c7c, be5a500, 2fb6e3d, 67064f6, c043d8a | Three-tab Saved/Channels/Groups grid, real forum topic drilldown, activity-viewmodel explorer position and Continue | CODE WRITTEN — UNVERIFIED |
| P3 | ed0b55e, 33674b5, 7412ee5 | Preserve warm results during metadata refresh, bounded account-generation catalog snapshots, clear account UI state on logout | CODE WRITTEN — UNVERIFIED |
| P4 | 5cc20ad, 2012601, f658450 | Single focus target on shared media cards, no duplicate oversized Home shell, compact focus animation respecting system motion settings | CODE WRITTEN — UNVERIFIED |
| P5A | 82457cb, c1c3ded | Remove giant TV ±10s/next/previous primary buttons, compact HUD, independent Start Over action | CODE WRITTEN — UNVERIFIED |
| P5B | ea4e187, 23dcd27 | D-pad Down opens queue tray, translucent vertical grid, Up-to-player first-row behavior; existing seek controller retained | CODE WRITTEN — UNVERIFIED |
| P6 | 1062403, 29b09fc, f658450 | Branded loading keyed to actual Telegram authorization and lightweight focus transition | CODE WRITTEN — UNVERIFIED |
| P7 | c17c7de, 8525c7d, ff0b3d6, c020619, 875ee1c, c0373f4 | Themeable original SVG, Android vector, Android 26+ adaptive icon, startup logo, asset notes | PARTIAL — PNG/ICO exports not done |
| P8 | none | No compile/lint/tests/emulator/device qualification has been run on this branch, by user request | DEFERRED |
| P9 | planning PR #42 merged earlier | Future TMDB/AniList, iframe/WebView and virtual-mouse architecture remains planning only; no Extras integration code | PLAN ONLY |

## Known intentional boundaries

- Existing TDLib JNI, native libraries, Media3/mpv, stream capability security, release signing and device qualification are unchanged.
- The underlying BousygramApp process still initializes existing Telegram infrastructure at process start; the new Extras placeholder itself does not create clients or access Telegram state. Full process-lifecycle isolation requires a later separately reviewed migration.
- Queue records do not include a forum topic ID. The tray filters the existing launch queue by chat; complete independent topic-scope enforcement is deferred until queue provenance carries immutable topic identity.
- The physical Android TV focus bug fix in PR #41 remains on a separate branch. This implementation branch includes a shared card focus fix, not every change from that PR.
- Legacy launcher PNGs are preserved; Android adaptive-icon XML has been added for API 26+, not yet packaging-tested.
- No new API keys, third-party libraries, WebViews, iframes, TMDB or AniList integrations have been added.
- A successful commit does not imply the Kotlin app compiles. Any defects discovered later are to be resolved during the requested final validation pass.

## Deferred final verification sequence (not started)

1. First compile the combined branch, fix Kotlin/API/resource errors and rebase/conflict issues against main and PR #41.
2. Run lint, JVM/unit, full debug APK and AndroidTest assembly, native TDLib/ABI and security gates.
3. Execute emulator focus/Back/tray tests with real key injection and account-safe mocks.
4. Test real Telegram account Saved/Channels/forum/non-forum Groups; replay MKV/TS/MP4 on ARMv7 TV and ARM64 devices, tablet, phone.
5. Measure catalog warm-return times, storage growth, focus/scroll, media seek time, first rendered frame, and motion performance.
6. Resolve bugs, review **every** source commit, produce a review packet, and only then consider a merge/debug prerelease.

**Do not merge this branch, publish a new APK, or mark any phase PASS before that requested final review.**
