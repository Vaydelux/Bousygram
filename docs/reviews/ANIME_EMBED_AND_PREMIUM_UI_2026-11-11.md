# Bousygram — Anime Embed and Premium UI Review
Date: 2026-11-11 · Scope: Extras playback routing, app surfaces, accessibility, CI.

## Executive finding
Movie and TV requests already opened the shared `ExtrasMediaForgePlayerScreen` immediately. Anime instead required a separate first-visit chooser and started from the Filmu route. This inconsistency is a reproducible **code-path discrepancy**, not proof that any individual third-party host is down. An HTML provider can render HTTP 200 with a visually blank page, so native WebView error callbacks alone do not cover the reported symptom.

## Code-review findings and actions
| Priority | Finding | Change | Verification |
| --- | --- | --- | --- |
| P0 | Anime startup differed from working movie/TV process | Start Anime in shared WebView/MediaForge player directly with documented `/anime/{anilist_id}/{episode}` route | JVM route/identity assertions |
| P0 | Unsuccessful embed may show black without an HTTP error | Add conservative, non-modal manual source-switch guidance after prolonged no-observation state | JVM policy tests; manual device test outstanding |
| P1 | Source list prioritized inconsistent, unverified Anime templates | Put the canonical AniList episode routes first; leave alternatives user-selectable rather than silently auto-rotating providers | Source-template tests |
| P1 | Live TV adds an unnecessary request type and sidebar/dialog state | Remove Live TV request, manual slug dialog, route and entry descriptions | Existing Extras and mode navigation UI tests |
| P1 | Existing basic theme lacks coherent ambient surfaces | Add native `drawWithCache` precision grid and two subtle radial gradients; refine Midnight; add Aurora | Theme contrast tests, emulator smoke |
| P1 | Animated background loops can increase ARM32 pressure | No infinite animation, no GSAP/WebView shader, no bitmap/blur; short hardware-accelerated focus transform only | CI + physical ARM32 soak remains needed |
| P1 | High contrast and reduced-motion must remain usable | Skip decorative backdrop in high contrast; respect system animator setting in focus transitions | Theme tests; manual accessibility testing outstanding |

## Logic-review invariants
1. `tmdbId` and `anilistId` are mutually exclusive. Anime identity always uses AniList and positive episode numbers; TV and movies continue using TMDB IDs.
2. User-selected server changes key the player subtree by URL. Old WebView, SDK session, resource observations, native decoder and pointer are disposed before the replacement is created.
3. Native source inspection remains opt-in; no script injection, DRM bypass or transport downgrade. Preserve HTTPS-only URL policy, disabled file/content access, blocked mixed content and third-party cookies.
4. A missing network-media observation does **not** prove playback failed (blob/MSE, encrypted and provider-managed sources are opaque). Manual recovery must never auto-navigate or clear playback.
5. A provider URL being syntactically valid does not establish hosting availability, content coverage, legality or Android TV playback compatibility.

## Process-review decisions
- Build from an isolated branch; no direct non-qualified edits to `main`.
- Require exact-head Android Enterprise (Kotlin, lint, unit, TDLib/ABI) and emulator UI tests before squash merge.
- Preserve a single `main` branch afterward; prune only the merged feature branch. Do not delete GitHub release tags or debug assets.
- Do **not** label this a full physical-TV fix without at least one successful Anime playback session on the affected TV.

## Physical-device acceptance matrix (not yet executed)
- Anime: known AniList title, episode 1 and later episode, initial EmbedHeaven path, D-pad interaction, fullscreen, Back, manual alternate provider and blank-provider recovery.
- Movie/TV: prior known-good title, season and episode routing, subtitles, reload and native handoff all remain unchanged.
- UI: TV 1080p/4K overscan, phone portrait/landscape, large-font mode, High Contrast, reduced-motion setting.
- ARM32: repeated Extras tab navigation, scrolling 100+ posters, WebView renderer loss/recovery, and long playback without increasing retained sessions.

## Rollback
Revert the squash commit for this branch if real-device Anime, subtitle or TV-navigation regression is observed. Existing debug release APKs are immutable and remain available for comparison.
