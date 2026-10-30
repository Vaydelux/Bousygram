# Bousygram — Phase-by-Phase Coding-Agent Prompt Chain

**How to use:** paste [MASTER_PROMPT.md](MASTER_PROMPT.md) into Cursor/agent instructions, then copy exactly **one** phase prompt below. These prompts implement the approved roadmap **one phase at a time**. Each must end with an evidence-backed phase review and a full stop before the next phase.

## Prompt P0 — Establish a working baseline / repair blockers

**MISSION:** Establish the exact HEAD baseline, resolve or report the current CI failures, and capture a risk-controlled architecture inventory. Do not redesign UI.

1. Inspect main HEAD, all open PRs, CI/Android emulator runs, test and release history; identify any conflicting branches or uncommitted work.
2. Prioritize PR #41: Enterprise run 41101341571 and emulator run 41101341665 both failed Kotlin compilation in EnterpriseComponents.kt:643. Inspect source and explain the incorrect assignment of Boolean to ShellDestination. Fix the **root cause** in the affected PR branch only, add coverage and verify its exact new HEAD if write access allows; otherwise create a minimal corrective PR and report that PR #41 remains blocked. Do not merge red CI.
3. Inventory the owners and entry points for MainActivity shell/auth, TvHybridHomeDashboard/BrowseScreen, media ViewModel/catalog, source/topic loader, existing caches, PlayerInputController/PlayerScreen, PlaybackCore/TDLib JNI, and protected release workflows.
4. Reproduce known UX defects where device/test assets exist: invisible focus, start-over triggers close, Home reload, oversized navigation, missing/incorrect duration. Differentiate reported symptoms from confirmed root causes.
5. Capture before measurements and enumerate device matrix plus gaps; inspect existing architecture docs and highlight stale assertions.
6. Output docs/reviews/PHASE-0-REVIEW.md, commit review entries, exact CI links, baseline SHA, prioritized P0/P1 issues, and PASS/BLOCKED/NEEDS_DEVICE_QA status.

**ACCEPTANCE:** one known CI-green tested SHA or explicit blocker with minimal repro and owner; unchanged TDLib binaries/playback behavior; no v2 feature work. **STOP.**

## Prompt P1 — App shell, startup and mode selection

**MISSION:** Implement only the safe root app shell and maintenance placeholder. Existing Telegram Media remains fully functional.

1. Extract a narrow AppMode/router contract without duplicating current MainActivity BousygramAppRoot auth/AccountSession.
2. Build accessible branded startup loading banner driven by actual initialization states, with bounded feedback and a fallback/error path (do not fake progress).
3. After startup, show **Telegram Media** and **Movies / TV Shows / Anime (Coming Soon)** choices, both remote and touch friendly. Telegram route preserves current auth/deeplink/logout. Extras route displays responsive Maintenance illustration/text and Back to mode selection.
4. Extras must not initialize WebView, iframe, TMDB, AniList, virtual mouse, network client or Telegram caches. Gate the placeholder behind a simple feature boundary; no extra dependencies.
5. Test cold/warm launches, state restoration, Back navigation, auth/logout, TV remote focus and TalkBack/phone touch. Include screen previews and manual steps.

**ACCEPTANCE:** Telegram route behaves exactly as before, with no new creds/data leakage or duplicate session; Extras remains offline static. Review every commit and output phase review. **STOP.**

## Prompt P2 — Grid explorer and real Telegram directory hierarchy

**MISSION:** Replace primary TV horizontal rails with a real-source grid explorer while preserving Telegram identity and existing video discovery.

1. Inspect MediaViewModel, BrowseScreen, TvHybridHomeDashboard, SavedMessagesTopics, forum topic loader, source policies and TDLib enums.
2. Introduce typed explorer routes, stable IDs, breadcrumbs and a testable source adapter. Use 3 top-level tabs: **Saved Messages / Channels / Groups**. Never invent a category, folder, chat ID, topic ID or synthetic media.
3. Saved directly opens playable media; Channels shows joined channels → selected channel media; Groups shows groups → if forum, actual topic grid → topic media; if non-forum, direct group media. Ensure forum General behavior matches TDLib; permission-filtered or deleted items display explanatory empty states.
4. Use vertical LazyVerticalGrid or appropriate adaptive grid with focused-item safe padding, stable keys, lazy paging and **no unnecessary horizontal scrolling**. For tablet use optional two-pane; phone uses stacked explorer/breadcrumb.
5. Wire video selection through the unchanged PlaybackLaunchCoordinator. Back restores exact folder and focus; no source or topic bleed. Test archived/inaccessible sources and rapid source changes.

**ACCEPTANCE:** source/folder/leaf contract and regression tests; full CI green; physical TV/real Telegram hierarchy marked tested or NOT RUN. Commit reviews + phase report. **STOP.**

## Prompt P3 — Cache metadata and restore navigation

**MISSION:** Eliminate repeated Home data loading without treating streaming video bytes as catalog cache.

1. Trace source population, TDLib chat/video queries, ViewModel lifetime, Home recomposition, repository caches and thumbnail loading. Produce an observed root-cause timeline first.
2. Introduce bounded account-scoped snapshot cache keyed by account/chat/topic/filter/page cursor, with single-flight requests, generation guards, update invalidation and stale-while-revalidate. Prefer using existing Room schema when appropriate; migrations must be tested, not guessed.
3. Persist selected tab, explorer route, lazy-grid position and last focused stable media key across Player navigation; restore from cached metadata before background refresh. Deleted/denied media triggers safe fallback.
4. Do NOT cache Telegram API hash, credential-bearing URLs, stream tokens or whole video bytes. Respect offline pins, active playback, TDLib storage constraints, privacy mode and logout/account switch invalidation.
5. Add fake-clock and race tests; measure initial Home, Home-after-Player and account-switch behavior; test low-memory process death and slow/offline network.

**ACCEPTANCE:** warm return is immediately usable, no blocking full reload spinner, no duplicate source enumeration for the same valid snapshot, and no cross-account stale data; results backed by traces. Commit reviews + phase report. **STOP.**

## Prompt P4 — TV density / focus accessibility and responsive UI

**MISSION:** Reduce oversized Android TV chrome and improve focus without hiding important accessibility affordances.

1. Inspect existing focus modifiers, TvFocusSurface, focus restoration, top nav, hero, video cards, overlays and the focus fixes in PR #41 only after the baseline is green.
2. Introduce window-dp/input-aware density tokens: TV compact, standard, accessible; tablet split/compact; phone touch. Do not use inches or actual 4K pixels to directly scale Compose dp.
3. Remove clipped focus rings and duplicate focusable modifiers; keep a single focus owner, clear >=2dp contrast-aware border and visible selected vs focused distinction. Preserve overscan margins and grid edge padding.
4. Slim TV nav/header and cards without losing readability; preserve reasonable interactive targets (typically >=48dp touch and focusable TV areas) and user-selectable accessibility scaling.
5. Add Compose semantics descriptions, reduced-motion handling, screenshots/previews and D-pad movement/OK tests. Validate 720p/1080p/4K window classes and tablet/phone rotations.

**ACCEPTANCE:** every actionable item reachable, no focus traps, no half-screen nav, no hidden controls; compile, lint, emulator and device manual verification. Commit reviews + phase report. **STOP.**

## Prompt P5A — Player compact HUD and critical Start Over/Back fixes

**MISSION:** Fix reported wrong behavior before adding any related-media tray or new visual features.

1. Audit PlayerScreen, PlayerInputController, PlaybackCore/SessionController, overlay BackHandlers, zIndex/pointer interceptors, Start Over callbacks and root Activity Back/Close behavior.
2. Reproduce Start Over incorrectly closing the app (or document device-only blocker), identify the actual event/state conflict and test it. A Start Over action seeks the current session to zero exactly once; it cannot dispatch exit, delete progress unexpectedly or change the current file.
3. Introduce a compact native TV HUD: Back, title/status, Start Over, timeline/current/total duration, Play/Pause and discrete More. Remove redundant giant ±10s actions from the TV primary strip but retain touch seek gestures or relevant phone accessibility actions.
4. Preserve normal playback, MKV/TS cases, unknown duration, audio tracks, error/retry, buffering, and existing engine lifecycle.
5. Add tests for each overlay Back priority, Start Over when HUD hidden/visible, focus targets, file identity and return route. Validate on TV hardware where available.

**ACCEPTANCE:** no unrelated Close or activity exit, no accidental autoplay/skip, no misleading timeline. Separate commit reviews and phase review. **STOP.**

## Prompt P5B — D-pad preview seeking and Down related-media tray

**MISSION:** Provide modern TV remote behavior without breaking playback ownership.

1. Extend existing input state rather than adding competing root/child key handlers. Left/Right on video adjust timestamp preview, repeated presses accelerate with bounds; OK confirms a single seek; Back cancels.
2. Use a real thumbnail frame only when available/decoded; otherwise show timestamp preview with no false artwork. Unknown-duration/nonseekable streams must disable unsupported seek safely.
3. Down opens a translucent compact **same-source media tray** with adaptive lazy grid; retain video playing behind it. Queue cards derive from the actual current account/chat/topic and permissions; avoid invented recommendations and global cross-topic mixing.
4. First-row Up closes tray or returns focus to player HUD; Back closes tray only. Other player overlays retain explicit higher priority.
5. Test slow/fast repeated keys, OK/Back cancel, source switches, app background and return, queue transitions, 32-bit TV memory/performance.

**ACCEPTANCE:** stable key matrix, no spontaneous close/next-video, no added unnecessary media-byte prefetch and real device evidence/NOT RUN. Review all commits and phase. **STOP.**

## Prompt P6 — Loading states and restrained motion

**MISSION:** Make UI transitions look premium while prioritizing low-end TV responsiveness.

1. Inventory first-load, empty, error, retry, cached-refresh and reconnection states; stop the Home full-screen spinner on warm cached return.
2. Compose startup/banner/section skeleton only for legitimately uncached content. Make refresh nonmodal; never mask an actionable player error. Keep streaming preparation from blocking focus navigation.
3. Animate tab/focus/tray transitions with short predictable curves; initial design range 120–220ms, reduced-motion behavior, minimal blur/shadow and no event delay.
4. Measure frame-time/jank and loader flashes on low-end TV, plus phone/tablet. Remove costly motion if it regresses input.

**ACCEPTANCE:** no flickering refresh, no spinners on cache hits, animation budget evidenced. Commit and phase review. **STOP.**

## Prompt P7 — Themeable SVG brand and launcher assets

**MISSION:** Design an original Bousygram vector system inspired by the existing app's media/TV visual identity without making a raster-in-SVG fake.

1. Inspect current launcher drawables, icons, app labels, theme tokens and supplied references.
2. Propose original scalable mark (subtle TV/play/signal geometry, premium gradient and pattern layers), master SVG, single-color and contrast variants, documented palette tokens.
3. Generate Android adaptive icon foreground/background, vector/raster exports as supported. Only produce favicon.ico for an existing/future web surface as a separate export; Android launcher doesn't use favicon.ico.
4. Test theme variants, dark/light/high contrast, 24px small mark legibility, launcher masking, resource build and offline startup performance.

**ACCEPTANCE:** authored sources and reproducible exports, no third-party copied trademark mark, no breakage in existing Android resource compilation. Commit/phase reviews. **STOP.**

## Prompt P8 — Full QA/QC hardening and qualified RC

**MISSION:** Review implementation end-to-end, identify regressions and produce a truthful release-candidate decision.

1. Audit every changed file/commit from phases 0–7 against security/privacy/architecture contracts. Fix any P0/P1 blocker in small reviewable PRs.
2. Run native TDLib compatibility and ABI gates, compile/lint/JVM, debug packaging, emulator, UI regression and Android instrumentation. Capture exact SHA, output, job URLs, hashes and signing provenance.
3. Test physical ARMv7 TV and ARM64 TV with real account/source hierarchies, MKV/MP4/TS, seek, resume, logout, background, low storage, D-pad, TalkBack. Test tablet and phone landscape/portrait too. Label missing hardware tests NOT RUN.
4. Compare recorded performance: Home return, first rendered frame, seek response, jank, rebuffer, memory, TDLib stored bytes. Verify no private content in diagnostics/logs.
5. Use existing protected production-signing and qualification gates; do not ship a production release if key/device evidence is missing.

**ACCEPTANCE:** fully documented release decision PASS/BLOCKED/NEEDS_DEVICE_QA, rollback notes and real APK availability link if genuinely produced. Stop for approval. **STOP.**

## Prompt P9 — Extras architecture preparation ONLY

**MISSION:** Review future scope without implementing it.

1. Document future TMDB/AniList provider adapters (separate auth, keys, quotas, cache and catalog namespaces) and possible metadata reconciliation by stable provider IDs. Do not mix with Telegram chat IDs or account data.
2. Threat-model any future WebView iframe player/virtual mouse: untrusted origins, popup/redirects, cookie/storage isolation, JavaScript bridges, permissions, downloads, content rights and DRM; reject an unrestricted default.
3. Evaluate future dependency/module split with incremental migration costs. Maintain feature flag disabled and app shell maintenance route only.
4. Deliver an Extras RFC with go/no-go options, risks, legal/security questions and tests needed. No code integrations.

**ACCEPTANCE:** future plan approved separately, no TMDB/AniList/WebView/iframe/virtual mouse implementation or third-party calls in this phase. Review files/commits and **STOP**.
