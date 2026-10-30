# Bousygram — App Goal (Product Contract)

**Status:** Proposed v2 direction; planning only. **Baseline reviewed:** main acec22f2a44057c75e1e5eac09c81f94fe4bb15c, 2026-10-31. This document does not certify the current build, a physical-TV test, or completion of any phase.

## Vision

Build a **local-first, privacy-respecting Telegram media explorer** whose primary experience is fast, stable media discovery and playback on **Android TV, Android tablets, and Android phones**. Deliver a premium, calm interface with predictable D-pad and touch behavior. Make it possible to add a separate Extras mode later without introducing failures into the Telegram account, TDLib, playback, or cache subsystems.

## Must-have experiences

1. **Startup**: lightweight branded splash with real readiness states, followed by two choices: **Telegram Media** and **Extras — Movies, TV Shows & Anime (Coming Soon)**. Telegram choice opens the existing auth/session flow. Extras opens a responsive accessible maintenance screen, with Back returning to the chooser. No TMDB, AniList, iframe, WebView, virtual mouse, or streaming implementation is included in this iteration.
2. **Explorer-first Home**: three persistent top-level tabs: **Saved Messages**, **Channels**, **Groups**. Use a responsive **grid** rather than horizontally scrolling media rails as the primary content view. Persist folder, tab, scroll and focus position on Back.
3. **Accurate Telegram hierarchy**:
   - Saved Messages → playable media grid (no artificial folders).
   - Channels → actual joined channels → actual media, optionally real metadata-based filters.
   - Groups → actual joined groups → forum topics only when the Telegram group has real topics, otherwise media directly. Topic → only that topic's media. Include General where TDLib exposes it.
   - Breadcrumbs reflect real chat and topic identifiers. Never fabricate IDs, categories, folders, or inaccessible media.
4. **Fast return to Home**: render cached metadata while refreshing safely in the background. Returning from Player should not force a full-screen spinner or re-enumerate every source unnecessarily.
5. **Modern TV player**: compact transport HUD, unobtrusive Back and Start Over, reliable duration/progress, Left/Right seek preview with timestamps and OK confirm/Back cancel, and Down to show a same-source related-media tray. No accidental skip or app exit from unrelated buttons.
6. **TV scale and accessibility**: readable but not oversized controls, visible focus rings, logical traversal, TalkBack labels, overscan safety, optional larger scale and reduced-motion settings. Phone uses touch patterns; tablet uses adaptive split panes when space permits.
7. **Branding**: original theme-aware vector identity anchored in a reusable SVG source, with adaptive Android icon exports and optional web favicon exports. Retain legibility in dark, light, and high-contrast themes.
8. **Smooth, efficient feedback**: subtle focus/transition motion, skeletons only for uncached content, progressive sections, short nonblocking refresh indicators, explicit empty/error/retry states.

## Architectural invariants (never regress)

- **Telegram Media is the stable core.** Extras has a separate route, feature namespace, future data layer, configuration and security policy. It does not initialize its future services when Telegram Media starts.
- **One playback owner/session**; retain the existing Media3/TDLib and controlled mpv fallback until an evidenced change is approved. Do not change JNI binaries as part of a UI milestone.
- **Private data stays private**: no Telegram API hash, auth keys, messages, scoped stream URLs, or local paths in logs or analytics. Logout/account switch invalidates account-specific cache and UI history.
- **File identity and forum topic isolation** are source-of-truth fields; Back, seek, cache cleanup and error handling must not silently switch or delete another media item.
- **No fake readiness**: an APK build passing CI does not prove authenticated playback or physical-TV D-pad correctness; keep the release qualification gates.
- **No extras implementation before explicit authorization.** Third-party embeds, rights, pop-ups, cookies and navigation require a separate security design and legal review.

## Definition of a stable core

The feature is *done* only when accepted behavior is proven at each applicable layer: unit/integration tests; Kotlin compile; lint; debug APK and native ABI validation; on-device or emulator navigation regression; manual 32-bit/64-bit TV playback; tablet and phone smoke checks; and security/compatibility review. Attach CI links and actual results to each phase review, not merely checked boxes.

**Working documents:** [Roadmap](ROADMAP.md) · [Phase plan](PHASE_PLAN.md) · [Architecture v2](docs/product/ARCHITECTURE_V2.md) · [Agent instructions](docs/agent/README.md). Existing [runtime architecture](ARCHITECTURE.md) and [TV hardening](docs/ANDROID_TV_HARDENING.md) remain the current-code reference until superseded by verified implementation.
