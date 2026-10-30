# Bousygram v2 Roadmap — Ordered Delivery

**Stage:** Planning, not implementation. Refer to [APP_GOAL.md](APP_GOAL.md), [PHASE_PLAN.md](PHASE_PLAN.md), and [Architecture v2](docs/product/ARCHITECTURE_V2.md).

| Phase | Focus | Exit deliverable | Mandatory gate |
|---|---|---|---|
| **0** | Baseline / blocker recovery | Build-green baseline, findings, screenshots, telemetry snapshot, ownership map and test matrix | Resolve or explicitly isolate failing PR #41; no unverified merge |
| **1** | Core/Extras shell | Branded startup → mode picker → Telegram existing auth / Extras maintenance only | Auth, logout, TV Back and app startup regression |
| **2** | Source explorer / grid | Saved, Channels and Groups; real forum topics; folder/back breadcrumbs; TV grid | No cross-chat/topic leakage; TV D-pad navigation passes |
| **3** | Catalog and state cache | Account-scoped cached catalog, stale-while-revalidate, focus/grid restoration | Re-enter Home without blocking reload; invalidation and memory tests |
| **4** | Responsive density + accessibility | TV compact/standard/accessible presets, tablet/phone layout, focus styling | No clipped ring / unreachable item; touch and TalkBack checks |
| **5A** | Player control safety | Fix Start Over/Back, compact HUD, no accidental closes; preserve playback | Media state-machine and hardware testing |
| **5B** | Seek and related-media tray | D-pad seek preview, timestamp/optional real frame, Down tray and Up return | Real file seek correctness, queue/source ownership, remote keys |
| **6** | Loading and motion | Fast, coherent splash, cached-content loading, reduced-motion fallbacks | No flashing spinner/focus loss; frame-time/performance budget |
| **7** | Theme-aware brand assets | SVG source, vector/raster icon exports, launcher assets, favicon if used | Contrast and sizes across themes/device launchers |
| **8** | Full hardening + release candidate | Regression matrix, telemetry traces, qualified debug/release artifacts | TV ARMv7/ARM64, phone/tablet QA; release signing gate intact |
| **9** | Extras readiness review only | Security boundaries, future interfaces/ADRs; roadmap handoff | No TMDB/AniList/WebView/embed implementation |

## Priority and dependency rules

- **P0**: app-exit/Back/Start Over defects, auth/session integrity, player/stream regression, inaccessible controls, and any red required CI.
- **P1**: source directory correctness, return-to-Home reload, focus/scroll restore, compact player chrome.
- **P2**: animation refinement, brand/launcher graphics, optional related-media frame preview.
- Phase 0 always precedes new implementation. Phase 1 shell and Phase 2 explorer precede account-scoped cache integration; player repairs may be separately hotfixed when P0.
- **No giant PR**: per phase, prefer one functional slice per PR and review each commit. Pause at phase boundaries. No phase auto-advance.
- Document trade-offs and any intentionally deferred behavior in the review report. New dependency/tool changes require justification.

## Known baseline risk (verified from GitHub)

[PR #41](https://github.com/Vaydelux/Bousygram/pull/41) is **not merged**. CI enterprise run [41101341571](https://github.com/Vaydelux/Bousygram/actions/runs/41101341571) and emulator run [41101341665](https://github.com/Vaydelux/Bousygram/actions/runs/41101341665) failed at Kotlin compilation: EnterpriseComponents.kt line 643 assigns Boolean to a variable resolved as ShellDestination (the selected property name is shadowed). This must be repaired and revalidated before claiming focus improvements are delivered. TDLib generation succeeded; it does not override the compile failure.

## Release discipline

Retain GitHub's debug prerelease pipeline and production signing/physical device qualification gates. Publish only a build from its exact tested SHA and include SHA256 checksums, ABI coverage, and an honest test summary. A source-level CI green status is not a physical-TV performance certification.

## Long-term Extras note

Future TMDB/AniList dual catalogs, anime metadata, WebView/iframe playback and virtual mouse are **future proposals**, not approved implementation. A dedicated design review must consider allowed sources, privacy/cookies, safe browsing, untrusted scripts, pop-up policy, rights/DRM, API key handling, lifecycle and user consent. Do not promise ad blocking or sandbox removal as a security substitute.
