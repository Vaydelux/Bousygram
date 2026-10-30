# Bousygram v2 — Implementation-First Ledger

**Status:** SOURCE CHANGES ONLY — UNVERIFIED. The owner requested that lint, compilation, emulator, hardware tests, debugging, and release qualification be deferred until after the implementation sequence. This file is not a certification of working behavior.

**Branch:** feature/v2-unverified-implementation-20261031
**Starting main SHA:** 00bc9a01f9fa4d0e9406c13036036fd8be5a3f42
**Product contract:** APP_GOAL.md, PHASE_PLAN.md, docs/product/ARCHITECTURE_V2.md, docs/agent/PHASE_PROMPTS.md

## Commit sequence / phase ownership

| Phase | Commits | Implemented source slice | Status |
|---|---|---|---|
| P0 | PR #41 remains separate; e68e386, e1d6e8d, 28b1dbe, d9ecfe4, 3db22f0, b2293ee, 025539e, 7039650, 7ab4850 | Ported the relevant TV focus fixes, instrumentation source and workflow entry into v2 without merging PR #41 or running its checks | INTEGRATED SOURCE — UNVERIFIED |
| P1 | 7edc1f5, e4bb34c | Responsive mode chooser, Telegram route, static offline Extras maintenance route | CODE WRITTEN — UNVERIFIED |
| P2 | 09e1c7c, be5a500, 2fb6e3d, 803e3df, 44bf6ea, 72b5c75, 7b18f1b, 0d271cb, 694f10c, 5b8c123, d2aa2a2, f994ed5, 0eda5d3, d3d7487 | Three-tab Saved/Channels/Groups grid, real forum topic drilldown, activity-viewmodel explorer position and Continue | CODE WRITTEN — UNVERIFIED |
| P3 | ed0b55e, 33674b5, 7412ee5, 5d6194a, 09212f4, bab502e, 291562a, 5c6eb3c, 2b85042, 4100d18 | Preserve warm results during metadata refresh, bounded account-generation catalog snapshots, clear account UI state on logout | CODE WRITTEN — UNVERIFIED |
| P4 | 5cc20ad, 2012601, f658450 | Single focus target on shared media cards, no duplicate oversized Home shell, compact focus animation respecting system motion settings | CODE WRITTEN — UNVERIFIED |
| P5A | 82457cb, c1c3ded, 4e34c1f | Remove giant TV ±10s/next/previous primary buttons, compact HUD, independent Start Over action | CODE WRITTEN — UNVERIFIED |
| P5B | ea4e187, 23dcd27, 0c664ee, bced4d0, 66a1fa7, 7ddbde9, a5326a6, 13ad493, 6928f65, fc2de36 | D-pad Down opens queue tray, translucent vertical grid, Up-to-player first-row behavior; actual TDLib topic identity now flows into request/queue and tray filtering | CODE WRITTEN — UNVERIFIED |
| P6 | 1062403, 29b09fc, f658450 | Branded loading keyed to actual Telegram authorization and lightweight focus transition | CODE WRITTEN — UNVERIFIED |
| P7 | c17c7de, 8525c7d, ff0b3d6, c020619, 875ee1c, c0373f4, 3119e4f, f40dbd4 | Themeable original SVG, Android vector, Android 26+ adaptive icon, startup logo, asset notes | CODE/ASSETS WRITTEN — UNVERIFIED |
| P8 | b2293ee, 6928f65, fc2de36 | Added/updated automated regression test sources without executing them. Compile/lint/JVM/emulator/device tests and release qualification deliberately not performed on this branch | DEFERRED |
| P9 | planning PR #42 merged earlier | Future TMDB/AniList, iframe/WebView and virtual-mouse architecture remains planning only; no Extras integration code | PLAN ONLY |

## Known intentional boundaries

- Existing TDLib JNI, native libraries, Media3/mpv, stream capability security, release signing and device qualification are unchanged.
- The underlying BousygramApp process still initializes existing Telegram infrastructure at process start; the new Extras placeholder itself does not create clients or access Telegram state. Full process-lifecycle isolation requires a later separately reviewed migration.
- New playback requests carry real TDLib topic identity and the tray filters by exact chat/topic from the account-owned launch queue. Legacy requests with null topic remain limited by their original queue; authenticated on-device scoping needs final integration review.
- PR #41 remains open on its own branch, but the related auth/source/library/player focus code and D-pad test source have been selectively incorporated into this v2 branch. This does not mean PR #41 has passed CI or been merged.
- Legacy launcher PNGs have been regenerated, an Android adaptive-icon XML has been added for API 26+, and favicon.ico has been committed; no package or visual verification has occurred.
- No new API keys, third-party libraries, WebViews, iframes, TMDB or AniList integrations have been added.
- Home and Library now use one grid-first route. Empty folder/retry state, source and media focus restoration, account callback ownership, and pagination snapshots still require final scenario-based checks.
- The original Telegram media metadata enrichment path contains an existing TMDB adapter, unrelated to the unbuilt Extras catalog. Review that legacy dependency separately before declaring complete Extras runtime isolation.
- A successful commit does not imply the Kotlin app compiles. Any defects discovered later are to be resolved during the requested final validation pass.

## Deferred final verification sequence (not started)

1. First compile the combined branch, fix Kotlin/API/resource errors and rebase/conflict issues against main and PR #41.
2. Run lint, JVM/unit, full debug APK and AndroidTest assembly, native TDLib/ABI and security gates.
3. Execute emulator focus/Back/tray tests with real key injection and account-safe mocks.
4. Test real Telegram account Saved/Channels/forum/non-forum Groups; replay MKV/TS/MP4 on ARMv7 TV and ARM64 devices, tablet, phone.
5. Measure catalog warm-return times, storage growth, focus/scroll, media seek time, first rendered frame, and motion performance.
6. Resolve bugs, review **every** source commit, produce a review packet, and only then consider a merge/debug prerelease.

**Do not merge this branch, publish a new APK, or mark any phase PASS before that requested final review.**
