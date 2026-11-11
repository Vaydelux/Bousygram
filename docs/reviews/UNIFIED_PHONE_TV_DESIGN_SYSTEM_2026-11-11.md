# Bousygram — Uniform Android / Android TV Design-System Review

Date: 2026-11-11 · Scope: ARM32 and ARM64 builds, TV firmware, phone/tablet/TV navigation and shared visual components.

## Diagnosis (confirmed by source inspection)
1. `BousygramTheme` and `BousygramUiTokens` existed, but they were not *enforced* as the sole visual styling contract.
2. The previous premium backdrop was shown on the mode selector, Extras and settings scaffold, while the most-used `TelegramExplorerDashboard` drew an **opaque, flat Material background** over its whole page.
3. The Telegram section tabs were independently implemented as styled OutlinedButtons; Extras used Material FilterChips; the folder and poster cards used multiple independent corner sizes, focus borders, animation durations and backgrounds. This caused design drift.
4. Favorites still used an opaque Scaffold and independent 142dp/224dp poster sizing and grid gaps.
5. `rememberAdaptiveMetrics()` detected TV solely through `UiModeManager.currentModeType`. Firmware that fails to expose the TV UI mode can be identified through Android's leanback/television feature flags. **ABI bitness is not a design-mode selector.**
6. An older `TvHybridHomeDashboard` implementation is still in source, but the actual Home/Library navigation routes through `BrowseScreen` to `TelegramExplorerDashboard`. Do not redesign or delete the legacy implementation as part of this focused fix.

## Design-system contract now enforced
- Palette, typography and contrast: semantic `MaterialTheme.colorScheme` roles via a single `BousygramTheme` preset.
- App ambient surface: `PremiumGridBackdrop`, implemented with Compose `drawWithCache`, at most 128 strokes per dimension. No JavaScript/WebView background, no infinite animation, bitmap blur, or shader retention on ARM32.
- Shared header: `BousygramPageHeading` with identical identity stripe, hierarchy and accessible colors across screens.
- Shared tabs: `BousygramSectionTab` used by Telegram and Extras; selected semantics, D-pad focus state, outlines and color roles are identical. TV increases only minimum control size and content padding.
- Shared cards: `TvFocusSurface` now owns Telegram folder, Telegram poster, and Extras poster focus/card surface treatment. Phone touch and TV D-pad use the same visual language.
- Responsive sizing: `bousygramResponsiveTokens(DeviceProfile)` centralizes page margins, grid gaps, minimum card dimensions, folder card heights and hit targets for TV/tablet/phone.
- Mode-selector focus, TV focus and poster focus share the same duration and scale tokens and respect Android's animator-disabled setting.
- App pages: Telegram Home/Library, Extras, Favorites, Settings and mode selection share the premium backdrop. Video playback surfaces stay black for correct picture contrast.
- High Contrast mode intentionally suppresses decorative backdrop layers; normal and Aurora themes share structure without changing text contrast roles.

## Logic review
- `DeviceProfile.Tv` wins when either UI mode or package features indicate a TV, including misreporting budget Android TV firmware; actual phones/tablets use shortest-side and orientation classification.
- All ARM32 and ARM64 variants are assembled from the same Kotlin/Compose code and resource set; Gradle's ABI settings only select native binary packages. The app never reads supported ABIs to choose a UI.
- No Telegram account, cache, playback engine, player-seek state, MediaForge session, or Cast ownership is changed by this visual pass.
- Existing stable semantics/test tags for library tabs and Extras navigation must remain functional across focus, touch and D-pad input.
- Larger poster titles use two bounded lines rather than an unconstrained auto-expanding layout.
- UI theme presets are stored **per app installation**; automatic cross-device synchronization of the selected preset is not implemented.

## Process gates
- Compile Kotlin + lint, unit tests, Android Enterprise checks, emulator Cast/D-pad/phone navigation, and Android debug build qualification on the exact branch HEAD before merge.
- Keep one `main` branch after the merge; do not delete tags or prior debug releases.
- Device follow-up remains essential: compare 32-bit/64-bit TV and phone screenshots with the same selected theme; check TV 720p, 1080p and 4K, reduced-motion, high-contrast and 30-minute memory/overdraw.
- A model-based UI screenshot is not evidence of actual device rendering: final confirmation requires the affected Android TV hardware and its specific firmware settings.

## Known remaining risks
- A firmware that reports neither TV UI mode nor Leanback/television features can still be misclassified; a future explicit user-selectable device-mode override would solve this exceptional case.
- Third-party WebView embeds are not reskinned because those are externally rendered documents. Only the native app controls can use Bousygram's theme.
- Older alternative/unused UI implementations may still carry historical styling; screen inventory and removal should be handled as a separate, qualified refactor.
