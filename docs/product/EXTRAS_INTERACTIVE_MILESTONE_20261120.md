# Bousygram Extras — Interactive discovery & graceful recovery milestone

Implemented on `main` — 2026-11-20

This milestone extends the **native Android Compose** Extras experience and
preserves the existing Telegram / Extras separation. It does **not** create a
web UI, introduce another media player engine, or change Telegram data.

## Shipped code

### 1. My Extras: offline, local-first library

- On-device **Save to My Extras** from Movie / TV / Anime details.
- **My Extras** panel: Saved and Recently explored sections, local search,
  provider filter chips, poster grid, removal, and confirmed Clear recent.
- Movies, TV series and anime use compound provider identities (type + ID).
  Identical numeric IDs from distinct providers never overwrite each other.
- Bounded to **150 deliberate saves** and **24 recent title entries**. When full,
  adding another save fails with a recoverable explanation; it **never evicts
  someone else's older saved title**. Remove one to make room.
- Backed by private, versioned `SharedPreferences` JSON under a dedicated
  Extras namespace with IO mutex, finite bounds, and transactional commit
  result verification. Corrupt/unrecognized storage is **not overwritten**.
- The library works independently of TMDB credentials, including the dedicated
  TMDB-not-configured fallback panel.
- Recently explored means a title's **metadata page successfully loaded**.
  It is **not** a claim that video played or that a timestamp is available.

### 2. Interactive discovery

- Large-window Spotlight on appropriate TV and tablet sizes, using already
  fetched TMDB/AniList result data. It has a readable title/synopsis, Details
  and manual Next pick. It makes **no extra catalog requests or timers**.
- On landscape phones and short 960×540dp TVs, Spotlight stays hidden to
  preserve scrolling poster space, focus targets and reasonable controls.
- Focusable `My Extras` entry alongside filters, with theme-token
  foregrounds and TV-sized controls.

### 3. Loading, failures and recoverability

| State | Response |
| --- | --- |
| First catalog load | Stable, low-cost poster skeletons, no fictional percentage |
| Refresh with existing posters | Previous cards remain interactive; updating spinner |
| Catalog request fails with no titles | Full-screen understandable message + Retry |
| Catalog request fails with retained posters | Inline failure status; existing cards retained |
| TMDB API key missing | Explain configuration status, browse AniList or My Extras |
| Detail request fails | Cached title preview, Retry, Back, Save for later |
| Episode load fails | Existing explicit episode retry UI remains, with sanitized error |
| Library still loading | Loading panel, no premature empty-library claim |
| Local library unreadable | Error and Retry, original bytes untouched |
| Save limit reached | Explain full library; let user remove a saved item |
| No results / filtered empty library | Dedicated empty guidance and Browse action |

Provider errors are converted by `ExtrasFailurePolicy` into safe actionable
messages for timeouts, DNS failures, TLS, authentication/configuration, rate
limits, 5xx and invalid payloads. Raw API URLs, credentials and stack traces
are not surfaced in UI. Retry is **user-driven**, not an endless loop.

### 4. Cross-screen UX

- Native Compose and 16-theme palette preserved.
- Poster images decode with existing bounded Coil policy; no video bytes are
  persisted by My Extras.
- D-pad controls remain focusable with explicit focus outlines. Personal
  library navigation avoids stealing focus via catalog restoration.
- New large-screen Spotlight is intentionally not a never-ending auto-carousel.
- Safe/back navigation from saved titles returns to My Extras without losing
  its current local category and query.

## Code and tests

Main code:
- `presentation/extras/ExtrasPersonalLibraryPolicy.kt`
- `presentation/extras/ExtrasPersonalLibrary.kt`
- `presentation/extras/ExtrasPersonalLibraryPanel.kt`
- `presentation/extras/ExtrasFailurePolicy.kt`
- `presentation/extras/ExtrasBrowseFeedback.kt`
- `presentation/extras/ExtrasSpotlightCard.kt`
- `presentation/extras/ExtrasDetailRecoveryPanel.kt`
- `presentation/extras/ExtrasCatalogScreen.kt`

Added regression tests:
- `ExtrasPersonalLibraryPolicyTest` (JVM)
- `ExtrasFailurePolicyTest` (JVM)
- `ExtrasPersonalLibraryUiInstrumentedTest` (Android Compose)
- `ExtrasPersonalLibraryPersistenceInstrumentedTest` (Android, dedicated
  test-only preferences; never clears production library)

Verified here: the **actual committed Kotlin policy sources** for the library
and error messages, byte-for-byte checked with Git blob SHA, compile under
`kotlinc` 1.9.0. The local smoke harness passes **26 pure Kotlin assertions**.
Also inspected balanced delimiter/import structure of changed source files.

**Not verified**: full Android Gradle compile, Compose tests, Android emulator,
actual TMDB/AniList connectivity, 32-bit Google TV, or physical D-pad and font
scale behavior. These need QA before claiming a production-ready APK.
No APK was generated.

## Known next milestone: real native Continue Watching

This milestone does **not** persist playback positions or claim a Continue
Watching feature. Implement that separately by instrumenting real native
Media3/LibVLC position and duration events, distinguishing explicit Pause,
EOF and errors. WebView embeds may not expose trustworthy position; do not
fabricate a resume point for them. Add a bounded on-device progress store and
resume prompt only when the source actually supports it.

Further future UX options: franchise watch-order timeline (verified AniList
relations), additional trailer clips, episode completion markers backed by
real progress, and optional Android speech search.

## Manual QA matrix

1. 320×568dp small phone and 844×390dp landscape phone: search, save, open
   My Extras, filter, remove, back, retry, no clipped control.
2. 768×1024dp tablet: open title, Spotlight if viewport qualifies, show
   full synopsis, return focus.
3. 960×540dp and 1280×720dp Google TV canvases: verify Spotlight hidden
   on the short viewport, DPAD_CENTER/Back focus, Save/Remove buttons and
   error retries.
4. Large-font / high-contrast presets: no clipped title or unavailable
   buttons; no hardcoded black text on dark backgrounds.
5. Force offline, DNS timeout, HTTP 429 and TLS issues; retry when recovered.
6. Corrupt *test-only* library preferences; confirm reads/writes do not wipe
   previous data. At 150 saves, confirm no silent evictions.
7. Long ARM32 browsing session: inspect memory, poster bitmap sizing,
   recomposition, and cold startup without TMDB credentials.
