# Bousygram Extras — Zoku-inspired native Android detail layout

Reference implementation: `Vaydelux/zoku-tv` at commit
`0ce6fbefd6653510087aa3fd85a263d8ac5eca97`:
- `app/title/[type]/[id]/page.tsx` (movie and TV detail profile)
- `app/anime/[id]/page.tsx` (anime detail profile)
- `app/discover/page.tsx` (media exploration)

This adapts the **information hierarchy, not branding, web CSS or playback**.
Bousygram remains native Android Jetpack Compose, with its existing TDLib,
Media3, LibVLC, WebView/source chooser, TMDB and AniList responsibilities.

## Detail information hierarchy

1. Image-only cinematic backdrop with a theme-aware gradient and provider
   label; the movie title is not rendered twice.
2. Native title profile (single title heading, year, score, genres and facts).
   Do not invent age certifications or runtimes unavailable from the provider.
3. Readable overview with an automatically detected overflow and a 48–56dp
   minimum-touch-height **Read full description / Show less** action.
4. Playback-first action: Movie/Anime Play; TV first season selector, then
   episodes. This preserves TV focus entry established in PR #215.
5. TV season/episode cards with 16:9 stills, metadata and separate Play
   buttons. Each virtualized LazyColumn row contains one or two cards.
6. Secondary sections: YouTube trailer, regional watch availability, official
   episode links, cast/characters (small circular portraits), reviews with
   **measured overflow** and native Read full review controls, and related
   media. No original video engines or network routing changes.

## dp-based layout matrix

| Environment | Width / height (logical dp) | Profile | Artwork and reading width |
| --- | --- | --- | --- |
| Small phone | 320 x 568 | stacked | 12dp gutter, 154dp banner, 4-line synopsis |
| Phone | 360 x 740 | stacked | 16dp gutter, 154dp banner |
| Landscape phone | 844 x 390 | compact stacked | 112dp banner, 3-line synopsis |
| Tablet | 768 x 1024 | two-column profile | 24dp gutter, 192dp banner |
| Short TV | 960 x 540 | two-column profile | 32dp gutter, 192dp banner, 56dp controls |
| TV | 1280 x 720 | two-column profile / two episode cards | bounded 1160dp content width |
| Wide logical TV canvas | 1920 x 1080 | two-column profile / two episode cards | 380dp side gutters, maximum 1160dp reading area |

`dp` represents the actual Android Compose window size, **not panel pixels**.
A 4K TV is commonly a much smaller window in dp. Folded, multi-window and
large-font layouts (including TVs) reflow by effective available width and
font scale rather than a hard-coded device model. TV focus targets remain
56dp even when its two-column title profile must become stacked. Use `ExtrasDetailLayoutPolicy.resolve` for
configuration decisions and actual `BoxWithConstraints.maxWidth` for card
content measurement.

The existing 16-theme palette and style tokens are retained. All actions
use minimum 48dp touch sizes or 56dp TV sizes, native Compose focus rings and
TV `FocusRequester`. Cast/metadata rows do not add dead D-pad stops.

## Verification gate

- JVM: `ExtrasDetailLayoutPolicyTest` covers 320/360/768/844/960/1280/1920dp
  widths, TV versus landscape phone, 1.8x fonts and invalid dimensions.
- Offline Compose: `ExtrasZokuNativeDetailInstrumentedTest` covers measured
  synopsis expansion, one visible title, and episode playback callback.
- Existing: `ExtrasDetailNavigationInstrumentedTest` retains Play/season
  navigation and episode callbacks without service credentials.
- Required physical QA: 960 x 540dp TV, 1280 x 720dp TV, ARM32 Google TV
  remote D-pad, 320dp small phone, landscape phone, tablet and 1.5–2x font
  sizes; capture dark/light/high-contrast evidence.

Until Android Gradle build, emulator and real TV verification pass, this is
a candidate UI migration, not a production-qualified APK.
