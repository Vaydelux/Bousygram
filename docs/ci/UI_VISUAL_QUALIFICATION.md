# Bousygram — UI Visual Qualification

**Status:** enforceable code gates plus manual/device acceptance. A green build is not proof that the layout looks good.

## Why previous passes were insufficient

The prior reviews checked compile, unit tests, and a selection of navigation semantics but did not enforce the media grid's actual viewport share or review rendered image artifacts across orientations. Android TV and phone headers even displayed placeholder play badges instead of the approved brand mascot. A later emulator run failed because `/bin/sh` was invoked with Bash-only `pipefail`, while another stalled in FFmpeg setup before UI instrumentation; these infrastructure failures cannot be reclassified as successful UI tests. The earlier "premium UI" claim was stronger than the available evidence.

## UI source of truth

- All top-level headers: `BousygramBrandMark`, rendering `R.drawable.ic_bousygram_mascot`. The README and Android mascot WebP source files must be byte-identical. Browser favicon is downsampled from the approved artwork; Android adaptive launcher masking and the TV banner aspect ratio may crop differently.
- Semantic palettes: `BousygramThemePreset` and `schemeFor`; Midnight, Aurora, Obsidian, Evergreen, Ember, Daylight, and High Contrast. Every palette must meet 4.5:1 text-on-surface contrast tests. High Contrast must preserve its stronger readability.
- Controls/focus: shared `TvFocusSurface`, `BousygramSectionTab`, `BousygramThemeGallery`, and `BousygramUiTokens`. CPU ABI must never select theme or UI resource values.
- Phone landscape on short screens: compact rail, no simultaneously visible top and bottom navigation bars.
- Library: two-row content-first toolbar; weighted LazyGrid; collection summary, Back and More are reachable; metadata density adapts only to available screen height.
- Extras: compact branded toolbar, first-class search, on-demand filter row, and shorter posters on constrained heights. Provider embeds retain their own sandbox/player behavior and are not visually overwritten.
- Settings: wide sidebar only when height and width genuinely permit it, otherwise a compact full-width header and adaptive, accessible theme gallery.
- Mode selection: two choice cards side by side in TV/landscape where space permits; preserve single-column on portrait.

## Automated gates (all on exact PR head SHA)

1. **Android Enterprise:** compilation, UI/JVM tests, native compatibility and Android lint plus `tools/ci/verify_brand_identity.sh`.
2. **Android Explorer Responsive UI Smoke:** instrumented Library grid-height ratio, 540dp compact toolbar, D-pad navigation, theme selection and brand mark. Captures `telegram-library.png`, `themes-midnight.png`, and `mode-selection.png` from actual Compose on the CI emulator. Missing images fail the test. Check artifact `explorer-ui-visual-evidence-<sha>`.
3. **Android Emulator Cast Smoke:** the full integration suite including media/decoder fixtures. A cancelled or stalled run is not a pass; it must be resolved, or the limitation clearly recorded before merging.
4. **Debug prerelease:** guarded ARM32, ARM64 and universal APKs and checksums. App/source artifacts are not released merely because Java/Kotlin compiled.

## Review matrix (manual / hardware, do not claim certified until reviewed)

| Device / viewport | Acceptance evidence |
| --- | --- |
| Phone portrait 320–480dp wide | Readable tab labels; Library has majority grid area; theme cards never clip; no horizontal overflow |
| Phone landscape 660–960dp wide, 360–480dp high | Side rail fits; header+nav do not crowd media; cards show readable titles |
| Tablet portrait and landscape | Responsive card columns and theme choices; sidebar only when wide and tall enough |
| Low-resolution ARM32 TV (e.g. 1280×720 physical, variable dp) | D-pad focus stays visible; folders/posters visible immediately; two-row toolbar; no all-header screen |
| 1080p/4K ARM64 TV | No overlarge headers or low-contrast text; grid spacing responsive; all navigation reachable |
| Accessibility / reduced motion | Large text, High Contrast and no animations all usable; no clipped actionable labels |
| Branding / theme | Mascot matches in app headers, launcher and documentation; favicon derives from same source; theme chosen in Settings persists across restart |
| Long session | 30-minute scrolling/selection on ARM32 does not leak memory or indefinitely cache thumbnails |

**A screenshot artifact is evidence, not automatic pixel approval.** Compare the uploaded renders for clipping and visual hierarchy. Android CI's Pixel emulator is *not* a substitute for 32-bit TV firmware, GPU, or the Android TV home launcher. Clearly label those device qualifications as outstanding until physical testing.

## Merge/release rule

Do not merge an unresolved source error or mark visual polish 'complete' based only on Enterprise green. Require the UI-focused emulator to pass, inspect the screenshot artifact, and gate the full playback suite independently. Never invent device screenshots, timings or test results. New themes or branding changes must rerun the contrast and mascot checks. Preserve only `main` after qualified merge; preserve previous release tags and APKs.
