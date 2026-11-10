# Bousygram Compose Visual Consistency Audit — 11 November 2026

## Scope and implementation boundary

Presentation-only change. All changes are confined to the Android app's
`presentation/**` package, the UI instrumentation test, and this design note.
Do not change TDLib, MediaForge SDK, API requests, download/cache handling,
native players, data models, preferences or playback behavior.

## Visual language

- **Default Midnight**: graphite-navy background, tiered opaque surfaces, cyan
  primary focus, soft blue secondary, restrained amber tertiary. Opaque tokens
  keep text readable over artwork, on TVs and in optional High Contrast/Light.
- **Hierarchy**: small uppercase section kicker, one clear screen title,
  understated supporting copy; eliminate duplicated Bousygram logo headings
  when the shell already provides branding.
- **Layout**: TV 32 dp content gutters, phone 16 dp; 12–18 dp grid spacing;
  20 dp cards and 24 dp collection panels; 48 dp phone and 56 dp TV controls.
- **Navigation**: intrinsic-size section pills, explicit selection semantics,
  distinguish focused state from selected state, 3 dp D-pad focus outlines.
  Phone actions scroll horizontally rather than clipping or shrinking text.
- **Cards**: same surface/focus treatment in home and Library, consistent
  album grids and folder affordances; no additional image decoding introduced.
- **Extras**: shared headings and an opaque Anime source panel, existing
  server selection remains native Compose and the WebView remains isolated.

## Screen coverage

| Screen | Applied |
|---|---|
| Telegram Home / Library explorer | Screen heading, source pills, collection summary, folder cards, grid gutters |
| TV/tablet/phone navigation shell | Base surfaces, focus, nav hit areas |
| Shared media cards | TV focus surface and selected state |
| Favorites | Heading, actions, poster grid proportions |
| Search | Heading and consistent insets |
| Settings | Hierarchy and common insets |
| Mode selection | Hero heading, two accessible choices, readable supporting text |
| Extras catalog | Shared heading and spacing |
| Anime source selection | Shared heading, contained panel and primary action |

## Qualification

Run `:app:lintDebug :app:testDebugUnitTest :app:assembleDebug
:app:assembleDebugAndroidTest` and emulator instrumentation. The
`TelegramLibraryDesignInstrumentedTest` verifies source navigation and
selected semantics independently of Telegram credentials. Also run existing
mode-selection, Extras, focus, and theme tests.

Physical checks still required: TV 720p and 1080p with D-pad, ARM32 devices,
320–480 dp phones, large font scale, landscape, High Contrast, and Light.
A successful emulator build does not prove these physical-device appearances.

## Non-goals

No new network requests, stream-resolution changes, media/cache behaviors,
server/provider additions, or new visual asset dependencies.
