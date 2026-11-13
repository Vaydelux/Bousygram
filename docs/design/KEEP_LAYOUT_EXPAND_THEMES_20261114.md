# Bousygram — Preserve Existing UI, Expand Themes (Decision 2026-11-14)

## Decision

**Keep** the existing Bousygram layout, navigation, player chrome, theme preset colors and functionality. **Use selected ideas** from the uploaded brand-neutral design documents to improve native presentation incrementally; do not copy their proposed full-screen layouts as if they were verified screenshots.

The user confirmed that the current layout and seven original palettes were already good. Changing those by default has greater regression risk than benefit. The three documents explicitly identify hero/sidebar/grid geometry as proposals, not measured reference facts.

## Current release scope: UI-only theme expansion

- Original Midnight, Aurora, Obsidian, Evergreen, Ember, High Contrast and Daylight are preserved; all previous `visualThemeId` strings continue to resolve.
- Add **Deep Ocean** (dark marine/cyan), **Plum** (dark mulberry/rose), and **Ivory** (warm light).
- Preserve the Settings picker layout and D-pad semantics; append the three themes at the end rather than reordering existing choices.
- In each picker card, show actual prospective on-background text ("Aa") alongside primary/secondary/tertiary swatches. The real theme role is used rather than borrowing the selected theme's text color.
- Map new colors through existing `BousygramThemePreset` and `schemeFor` only. No duplicate runtime theme state or network-dependent dynamic wallpaper palettes.
- Continue to use the semantic `StreamingVisualTokens` design foundation from PR #187; it automatically supports every added theme.
- Keep font family, spacing, focus ring, layout direction, TV controls and motion policy unchanged.

## Decisions on the reference documents

| Proposal in the docs | Decision | Reason |
| --- | --- | --- |
| Semantic surfaces, typography roles, focus/selected/loading states | Adopt selectively | Reuses existing Material 3 infrastructure |
| Shared native UI primitives | Keep and evolve | Already integrated in PR #187 |
| Adaptive grids and spacing | Retain current geometry | The installed layout is working and has device-specific tests |
| Large featured hero on Telegram browse | Do not apply | Displaces folders/chat and source discovery |
| TV sidebar replacing existing top navigation | Do not apply | Changes D-pad graph and visibility, unverified benefit |
| Web Home/Search/Genres/Watchlist routing for Telegram | Do not copy | Telegram sources are not genre catalogs |
| Extras full catalog and detail refactor | Defer | Higher risk to provider paging/focus; review screenshots first |
| Player chrome rewrite | Reject in this phase | No need to touch seeking, engine, Cast, subtitle or cursor behavior |
| Brand logo, colors, fonts from reference | Do not copy | Existing brand and palette remain independent |

## Automated qualification

1. `ThemePresetTest`: every original ID stays in place, new IDs round-trip, and all palette semantic foreground/background pairs achieve at least 4.5:1 contrast.
2. `StreamingVisualTokensTest`: all presets map to the same design roles with sufficient contrast, no duplicate theme engine.
3. `BousygramThemeGalleryInstrumentedTest`: selecting each preset retains the native selected state.
4. `UniformPremiumDesignInstrumentedTest`: screenshots for every theme, including the new three.
5. `tools/ci/run_explorer_ui_smoke.sh`: collect all preset screenshots as CI artifacts; fail when missing.
6. All existing Enterprise, responsive UI and Cast/media emulator checks. Physical TV and phone appearance remains manual QA, not claimed by CI.

## Future incremental refinements (separate PRs)

- **First:** improve actual loading/error/empty states using existing components and theme roles, without moving buttons.
- **Second:** audit theme contrast on Extras details, subtitle/popup overlays, and shorter Android TV viewports.
- **Third:** polish typography truncation and poster fallback visuals, measuring against existing screenshots. Never add new catalogue requests solely to decorate hero cards.
- **Fourth:** consider alternate page layouts only if real screenshots and user feedback identify a specific usability problem; gate behind an opt-in preview.

Each step requires its own focused tests and before/after screenshots. Never change content restrictions, authentication, TDLib, caches, API endpoints, provider routing, Cast, audio focus, seek behavior, subtitle timing, virtual mouse or navigation destinations as part of presentation work.

**Rollback:** If the theme expansion is unsatisfactory, choose one of the unchanged original presets in Settings, or revert this isolated UI/theme PR. No database migration is needed; only existing saved theme IDs are used.
