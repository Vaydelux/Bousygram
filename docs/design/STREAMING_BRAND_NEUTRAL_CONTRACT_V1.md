# Streaming presentation contract v1 — native Bousygram adoption

**Scope:** Presentation only · Android (Jetpack Compose / Material 3)  
**Input documents:** `BOUSYGRAM_BRAND_NEUTRAL_DESIGN_MIGRATION_PLAN`, `STREAMING_APP_BRAND_NEUTRAL_COMPLETE_DESIGN`, `STREAMING_APP_BRAND_NEUTRAL_DESIGN_ARCHITECTURE` (2026-11-14).  
**Current phase:** Foundation and opt-in preview. Production pages remain on their existing layouts.

## Evidence boundary

The source specification verified only the reference web app's labels `Home`, `Search`, `Genres`, `Watchlist`, and `Settings`. Hero height, grid density, card geometry, navigation positioning, animation timing and interior page layout are **proposed**; they were not measured from a reliable reference screenshot.

We are sharing visual semantics, **not copying the web app's brand**, React components, CSS, or a WebView shell into Kotlin. The existing Bousygram mascot, themes, authentication, Telegram and Extras flows remain unchanged.

## One native visual source of truth

| Portable role | Current Compose binding | Web specification analogue |
| --- | --- | --- |
| background | `MaterialTheme.colorScheme.background` | `--ui-background` |
| base surface | `surfaceContainerLow` | `--ui-surface-1` |
| raised surface | `surfaceContainerHigh` | `--ui-surface-2` |
| overlay | `surfaceContainerHighest` | `--ui-surface-3` |
| primary/secondary text | `onSurface`, `onSurfaceVariant` | `--ui-text`, `--ui-text-secondary` |
| disabled text | `onSurface` at disabled opacity (nonessential labels only) | `--ui-text-subtle` |
| border / focus | `outlineVariant`, `primary` | `--ui-border`, `--ui-focus` |
| primary action / content | `primary`, `onPrimary` | `--ui-action`, `--ui-on-action` |
| selected surface / content | `primaryContainer`, `onPrimaryContainer` | `--ui-selected` |
| error | `error` | `--ui-danger` |
| progress track / fill | `surfaceVariant`, `primary` | `--ui-progress-track`, `--ui-progress-fill` |

Bindings live in `presentation/design/contract/StreamingVisualTokens.kt`. They are views over `BousygramTheme`; **do not** add a parallel palette, theme preference, branded font, or persistent presentation store.

Layout aliases call `bousygramResponsiveTokens(metrics)` from existing `BousygramUiTokens`, preserving 16dp phone gutters, 32dp TV gutters, 12dp card corners, 16dp panels, 48dp phone and 56dp TV controls, and 3dp TV focus outlines. Device class comes from `AdaptiveMetrics` and actual TV capabilities, never CPU ABI.

## Components implemented in foundation

| Component | Ownership and behavior |
| --- | --- |
| `StreamingSectionHeader` | Consistent text hierarchy, max lines and semantic roles |
| `StreamingActionButton` | Delegates to existing `BousygramButton` / `TvFocusSurface` |
| `StreamingFilterChip` | Delegates to existing `BousygramSectionTab`; separate focus and selected state |
| `StreamingArtwork` | 16:9 or 2:3 slot, no built-in network/image loader |
| `StreamingMediaCard` | Content-only model, caller supplies artwork and onClick; knows no routes/providers |
| `StreamingSkeleton` | Static layout placeholder; no continuous shimmer on ARM32 |
| `StreamingStatusPanel` | Empty/error state and optional recovery action |

Presentation primitives MUST NOT import repositories, network clients, authorization rules, TMDB/AniList, TDLib, MediaForge, player lifecycle, or application navigation. The caller is responsible for policy-filtering content **before** creating view models. The visual contract must not imply playback availability from metadata alone.

## Debug-only opt-in preview

No new production toggle is enabled. To view the isolated native Compose gallery:

```powershell
adb shell am start -n com.vaydelux.bousygram/.app.MainActivity --ez design_preview true
```

The `design_preview` extra is accepted **only** when `BuildConfig.DEBUG` is true. This view renders local fixtures, no provider calls or sign-in; its Back/Exit action returns to the normal app. No theme selection or media state is persisted by the gallery.

Current install theme applies to the gallery. For multi-theme screenshots use the `StreamingDesignGalleryInstrumentedTest`, which captures Dark, Daylight and High Contrast. Existing `ThemePresetTest` continues to validate all seven palette configurations.

## Source-of-truth / rollback

- The existing active TV shell is `TvTopNavBar`, **not** an assumed reference sidebar.
- Telegram source browsing is content-first; do not impose a large hero over chat/topic grids.
- Extras stays catalog-first and keeps existing paging memory limits, Movie/TV/Anime separation, provider and subtitle choices.
- Continue Watching is separate from Favorites. Telegram chats are not movie genres.
- WebView D-pad/cursor remains opt-in; do not intercept provider keys to emulate a new navigation model.
- ExoPlayer, libmpv, LibVLC, Cast and TDLib commands are **outside** this change.
- No new media network requests, cache allocations or continuous GPU animations in the gallery.
- For later production restyling, gate each feature-screen switch behind an explicit reversible presentation flag until visual + functional QA passes. A debug-only preview flag is **not** authorization to roll out a new production screen.
- If the foundation causes a regression, revert its presentation-only PR; no data migration or player rollback is necessary.

## Verification and remaining gates

### Automated

- `StreamingVisualTokensTest`: all seven theme schemes, progress and responsive geometry.
- `StreamingDesignGalleryInstrumentedTest`: focus/selection, local cards, navigation and semantic states.
- `run_explorer_ui_smoke.sh`: preview screenshots `brand-neutral-design-dark.png`, `brand-neutral-design-light.png`, `brand-neutral-design-high-contrast.png`.
- `run_emulator_smoke.sh`: repeats gallery instrumentation with existing Cast and playback regressions.
- Existing Enterprise lint/compile, theme contrast, TV navigation, and MediaForge suites remain required.

### Manual / not yet claimed

- Inspect actual uploaded emulator screenshots for clipped text, unexpected default black text, D-pad focus visibility and safe-area overflow.
- Android TV ARM32 at 720p, Android TV ARM64 at 1080p, phone and tablet at 1.0/1.5/2.0 font scales. No physical-device evidence is implied by a green emulator run.
- Before any production page is restyled, capture the current active screen and record a before/after review.

## Next staged PRs (do not bundle with foundation)

1. Existing shared component consolidation without modifying click/focus or state ownership.
2. Active Telegram shell / source library / favorites / search visual adaptation (content-first).
3. Extras catalog, details, episodes styling (bounded paging).
4. Both player chromes' **presentation** only; independent seek, subtitle, Cast and cursor qualification.
5. Physical device and accessibility review before wide release.

The companion web/zoku-tv design documentation is the portable source specification, **not** an instruction to change the separate zoku-tv runtime in this Android PR.
