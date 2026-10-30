# Bousygram v2 — Phase Contract, Reviews and Handoff

Scope and ordered goals: [APP_GOAL.md](APP_GOAL.md) and [ROADMAP.md](ROADMAP.md). Design contracts: [ARCHITECTURE_V2.md](docs/product/ARCHITECTURE_V2.md). Copy/paste coding-agent prompts: [docs/agent/PHASE_PROMPTS.md](docs/agent/PHASE_PROMPTS.md).

## Universal phase entry rule

1. Fetch the **latest main SHA** and open PRs; reconcile with source files, workflow runs and prior review reports. Do not claim inherited green status.
2. Read this plan, architecture ADRs, CI and relevant code. Run baseline checks before editing.
3. Record entry criteria, files touched, explicit non-goals and measurable acceptance criteria.
4. Work in a named branch with small commits, review each diff immediately, and test locally or in CI.
5. Create a PR. Required checks must pass for the PR HEAD before merge. Explicitly record anything not tested on physical devices.
6. Add **docs/reviews/PHASE-N-REVIEW.md** (or a scoped PR review) with a truthful status: **PASS**, **BLOCKED**, or **NEEDS_DEVICE_QA**. No claiming “done” without the listed evidence. Stop at the current phase until user approval.

## Mandatory per-commit review

For **every** commit capture: SHA, goal, files, API/state migration changes, regression risks, security/privacy concerns, observed tests, CI links when available, compatibility with ARMv7/ARM64/touch, rollback action and unresolved findings. Never review only the final diff.

## Phase acceptance details

### Phase 0 — Baseline and repair gate
- Inspect open PR #41; fix its observed Kotlin compile failure before any merge, and run emulator focus tests. Establish a known green SHA. Capture reproducible Start Over/Back and reload failures.
- Audit current controllers, source/topic mapping, view models, caches, existing native libs and device test coverage.
- Output baseline report, open issues/risks, prioritized test matrix. **Exit:** CI-green baseline or explicit documented blocker; no v2 UI changes.

### Phase 1 — Mode shell only
- Introduce first-start branded splash and stable mode selection. Telegram route must reuse original auth/session, not duplicate TDLib. Extras route is a static maintenance screen only.
- Persist user mode choice only if UX requires; always allow switching without leaking auth and without auto-launching future extras services. Back from maintenance returns to selection; Back from Telegram route follows current navigation policy.
- **Exit:** auth and logout unchanged, orientation and process restore tested, no Extras network initialization.

### Phase 2 — Hierarchical grid explorer
- Define sealed folder/leaf destinations and stable IDs: Saved, Channel(chatId), Group(chatId), Topic(chatId, topicId), Media(chatId, messageId). Never invent folders from title heuristics.
- Saved opens media; Channels opens actual channel grid then media; Groups opens group grid then topic grid for forum groups or media for non-forum groups. Scope pagination and search to actual source/topic IDs.
- TV uses lazy adaptive grid + focus restoration; tablet optional master/detail; phone a stacked explorer + back breadcrumb.
- **Exit:** no topic bleed, correct Back path and media focus, empty/denied/deleted states tested.

### Phase 3 — Cache and navigation state
- Account-scoped in-memory snapshot first, persistent metadata index only if verified safe; stale-while-revalidate with in-flight request coalescing and clear invalidation rules.
- Distinguish source metadata/thumbnail cache from TDLib media-byte cache and offline pins. Do not delete active files; do not cache secrets or credential-bearing URLs.
- **Exit:** same Home navigation returns immediately from cache; no unnecessary full-screen spinner, low-memory recovery and logout-switch tests pass.

### Phase 4 — Adaptive TV/tablet/mobile
- Design token system by window size and input mode, bounded dimensions and optional accessible scaling. Focus ring must remain visible beyond card bounds without clipping.
- Run keyboard/remote action traversal, TalkBack/readable labels, font scaling, large text, phone touch and tablet orientation tests.
- **Exit:** no focus traps, 1 OK equals one activation, no half-screen control strip.

### Phase 5A — Player control correctness
- Repair Start Over semantics and Back/Close priority using the existing player state machine. Separate controller-layer event handling from UI draw state.
- Compact HUD with Back, Start Over, progress/current/total time, play/pause and optional settings. Retain truthful duration and seekability; no false values for unknown-duration TS/live streams.
- **Exit:** Start Over seeks to zero exactly once; Back exits the correct overlay only; no accidental app close; no regression for MKV/TS.

### Phase 5B — D-pad seek and related-media tray
- Left/Right adjusts seek preview without changing item. OK commits, Back cancels; repeat acceleration bounded by duration and actual seekability.
- Down opens same-source related-media overlay grid, Back closes tray, Up from first row returns to player. Queue retains chat/topic permission and source identity.
- Real frame preview only when an actual decoded/cached frame is available; otherwise timestamp-only. No fabricated thumbnails, heavy prefetch or hidden downloads.
- **Exit:** remote, seek, resume and exit matrix proven on TV hardware; playback continues behind overlays.

### Phase 6 — Loading/motion refinement
- Splash has explicit, cancellable readiness steps; cached content appears before refresh. Compose skeletons only for genuine first load; fast hits avoid loading flash.
- Focus animations ~120–220ms as a starting budget, reduced-motion fallback and frame-time measurements on low-end TV.
- **Exit:** fast path has no blocking reload; animation never steals input.

### Phase 7 — Vector brand system
- Brand mark inspired by existing Bousygram identity but original, scalable and themeable; create an authored SVG master, monochrome and high-contrast variants.
- Android vector/raster outputs from source for launcher/adaptive icons, and favicon only for future web surfaces; check resource usage on TV/phone.
- **Exit:** consistent legible logo in all themes/sizes, no accidental licensed design copies.

### Phase 8 — Release hardening
- Run full source/CI tests, emulator instrumentation and documented **real account** manual tests on ARMv7 TV, ARM64 TV, tablet and phone.
- Measure launch, Home re-entry, seek-to-first-frame, buffer recovery, frame drops, memory and storage. Keep native binary provenance and signing gates intact.
- **Exit:** attach device evidence, list known failures and release or block honestly.

### Phase 9 — Extras future-readiness review only
- Validate routing and dependency isolation. Draft future API adapter contracts and WebView threat model; do not add TMDB/AniList SDKs, API keys, embeds or virtual-mouse code.
- **Exit:** roadmap/ADRs ready for a separately approved later project; Telegram media app unchanged.

## Do-not-change defaults

Do not start next phase automatically, force-merge failing CI, fabricate tests, replace the player engine, add unrequested network dependencies, bypass Telegram permissions, conflate title with source ID, or remove production release security gates. If blocked, open a focused issue/PR, attach root cause and stop.
