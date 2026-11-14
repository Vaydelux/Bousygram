# Unified Adaptive UI — Implementation Contract

Status: design migration baseline, not an implementation-complete claim.

## Observed defects (screenshots, 2026-11-14)
- Search keyword input, three oversized actions, history controls, and no visible results viewport.
- Library uses a different page shell from Search and Settings; mobile tab row clips.
- Library empty state says "No playable media" even when data may still be loading.
- Portrait Settings theme cards take excessive vertical space; landscape layout wastes height.
- Status bar overlaps Library header; inconsistent safe-area handling.
- Background grid competes with text and component boundaries.

## Invariants
- UI-only migration: no change to TDLib, stream gateway, caching, account scope, playback intent, engines, media indexes, or search semantics.
- Every page uses one shared scaffold, navigation, page header, content margins, typography scale, theme semantic tokens, and state presentation.
- Preserve TV D-pad focus and back behavior; no keyboard-only or hover-only interactions.
- No hardcoded foreground/background colors outside theme definitions. No default black text on dark surfaces.
- No fullscreen image or background overlay that intercepts touch or D-pad input.

## Responsive layout
Use window size classes and available dp, not physical device names or orientation alone.
- Compact (<600dp): top app bar, bottom navigation, single-column content, 16dp gutters, 48dp minimum touch targets.
- Medium (600–839dp): adaptive bottom/rail navigation based on available width and height, 20–24dp gutters, two-column media grid where usable.
- Expanded (>=840dp): navigation rail, constrained reading/content regions, responsive media grids; never stretch text fields or buttons to fill huge widths.
- TV: dedicated 10-foot density/focus behavior with overscan-safe gutters, large focused controls, predictable D-pad traversal, and no mobile bottom navigation.
- Landscape with short height: compact headers and action rows, scrollable content; search results appear immediately below search field.
- Apply WindowInsets for status/navigation bars; no content behind system bars unless explicitly edge-to-edge with inset padding.

## Component contract
Shared: AppScaffold, AdaptiveNavigation, PageHeader, SectionHeader, SearchField, PrimaryAction, SecondaryAction, FilterChip, MediaCard, MediaGrid, ThemePreviewCard, EmptyState, LoadingState, ErrorState, RetryAction.
Semantic tokens: background, surface, surfaceVariant, textPrimary, textSecondary, outline, accent, onAccent, focusRing, selectedSurface, error, success, scrim.
Spacing: 4/8/12/16/24/32dp; radii 8/12/16/24dp. Components use semantic tokens, never raw theme palette swatches.
Focus rings must be visible in all themes; text contrast target >=4.5:1 normal, >=3:1 large.

## Search screen
- Single keyword field with trailing voice action when supported.
- Search submission via IME/remote and one primary button; remove duplicated oversized Voice/Search/Clear row.
- Recent searches in collapsible horizontal chips, not a permanent competing panel.
- Results section directly under search; show query, count, progress, and media cards.
- Explicit states: idle, searching, results, no matches, offline/error, canceled.
- Clear query is not Clear History. Require confirmation for destructive history deletion.
- Results must be independently scrollable/reachable with keyboard and D-pad; maintain focus and scroll position on rotation.

## Library
- Same scaffold/header/nav as every page; no special full-screen legacy layout.
- Favorites, Saved, Channels, Groups are adaptive tabs with horizontal scrolling and selected-state semantics; never crop labels without a scroll affordance.
- Loading and no playable media are different states. Show source, loaded count, retry, and diagnostics on failure.
- Preserve list pagination, selected source, and scroll position across rotations.

## Settings and themes
- Compact grid on medium/expanded, single column on compact; cards preview actual surface/text/accent/focus colors.
- Theme selection visibly indicates active selection and updates the entire application consistently.
- Add optional background patterns: None, Fine Grid, Dots, Soft Gradient. Patterns use theme-derived low-opacity strokes, are disabled in High Contrast, and can be disabled for performance.
- Pattern overlays are decorative only; avoid expensive blur, animated shaders, and overdraw on ARM32/TV.
- Persist theme and pattern independently.

## Verification gates
- Screenshot regression: phone portrait/landscape, tablet portrait/landscape, Android TV 720p/1080p, large desktop-like window.
- Test light/dark/high contrast themes and each pattern, including long strings and font scaling.
- Assert no clipped tab, hidden results, status bar overlap, unreadable text, inaccessible focus, or invisible empty/error state.
- Test search with actual matches and zero matches; test library loading, empty, error, pagination; ensure player playback unchanged.
- Measure recomposition/scroll performance and memory on ARM32. No merge before build and device qualification.

## Delivery sequence
1. Inventory current Compose screens, themes, navigation, and layout primitives.
2. Introduce shared semantic design tokens and adaptive scaffold behind existing behavior.
3. Migrate Search and Library first, including state correctness.
4. Migrate Settings, Home, Extras, dialogs, and overlays.
5. Add optional patterns and full visual regression matrix.
6. Run builds/tests/device validation, then review PR for merge.
