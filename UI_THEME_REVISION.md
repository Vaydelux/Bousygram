# Bousygram UI and theme revision

## Design assessment

The previous interface already had a dark, media-first foundation and a cinematic Home hero, but styling was inconsistent: several screens hard-coded different near-black colors, source tabs used their own accent colors, poster cards used tighter corners, and focus styling did not always follow the active accent. The player also used a separate fixed color for track selection.

This revision keeps the existing screen flow, TDLib loading, source selection, playback, and streaming behavior. It concentrates on presentation consistency and user-selectable themes.

## Theme presets

The selected preset is saved in `EnterprisePreferences` and applied at the app root. Changing a preset in Settings updates the current composition immediately.

- **Midnight** — indigo and mint accents on a deep blue-black base.
- **Aurora** — emerald and fresh green accents on a deep green base.
- **Ocean** — electric blue and cyan accents on a deep navy base.
- **Sunset** — coral, amber, and rose accents on a deep plum base.

Theme IDs are stable (`midnight`, `aurora`, `ocean`, `sunset`) and included in settings JSON export/import. Unknown imported IDs fall back to the current theme, preventing invalid values from breaking theme selection.

## UI changes

- The top-level gradient uses the active Material color scheme.
- Focusable surfaces use consistent 16 dp corners, a visible focus border, a restrained scale-up, and accent-colored elevation.
- Poster cards have larger rounded surfaces, theme-aware placeholders, duration/badge pills, and a bottom artwork scrim for title legibility.
- Chat source tabs and the All Chats dialog use the selected theme instead of fixed navy/blue fills.
- Compact media grids, Favorites, authentication inputs, and the Home hero use theme-aware surfaces and typography colors.
- The player track-selection panel follows the selected theme. The actual video canvas remains black on purpose so content is not tinted by the app palette.
- Settings contains theme preview cards showing three accent swatches, title, description, and selection indicator.

## Validation

`tools/mock-tests/test_theme_redesign.py` statically verifies the four presets, live switching, persistence, settings import/export, and use of dynamic theme colors in shared components and the player overlay.

The offline mock/static runner passes when run as:

```bash
SKIP_KOTLIN_MOCK=1 bash tools/mock-tests/run_all.sh
```

A full Android/Compose build and device visual review are still required. The Gradle wrapper in this environment could not download Gradle because `services.gradle.org` did not resolve. Therefore this source revision is not represented as a successfully compiled APK.
