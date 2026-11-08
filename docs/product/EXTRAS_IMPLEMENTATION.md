# Bousygram Extras — implementation and release gates

Updated: 2026-11-04

## Product boundary

Extras catalog remains **native metadata discovery** on Android TV, phone and tablet.

An opt-in, separately scoped MediaForge WebBridge feature now offers a third-party embedded page for Movies or individual TV episodes. It is separate from TMDB/AniList metadata, and never uses Telegram playback credentials, session objects or media gateways. See [MediaForge integration](MEDIAFORGE_EXTRAS_INTEGRATION.md). Provider content rights and availability must be verified before public distribution.
Telegram Media (TDLib, Media3/ExoPlayer, private account state, chat IDs,
streaming gateway, playback queue) is not queried or modified by Extras.

On a cold Extras-only launch, the Telegram native runtime and background
streaming infrastructure are now intentionally deferred until Telegram Media
is entered (or a pending Cast request needs authenticated Telegram handling).
The process-wide account invalidation/privacy barrier still registers at app
startup. After Telegram has started, switching back to Extras does not stop
the Telegram runtime or existing playback. See
[ADR-002](../adr/ADR-002-telegram-lazy-startup.md) for lifecycle, Cast and
release acceptance requirements.

| Tab | Provider | Search | Browse | Detail |
| --- | --- | --- | --- | --- |
| Movies | TMDB v3 | /search/movie | /discover/movie, /trending/movie/week | /movie/{id} |
| TV Shows | TMDB v3 | /search/tv | /discover/tv, /trending/tv/week | /tv/{id}, on-demand /tv/{id}/season/{n} |
| Anime | AniList public GraphQL | Media(type: ANIME, search:...) | Page(media(type: ANIME,...)) | Media(id, type: ANIME) |

TMDB details use append_to_response=credits,videos,reviews,recommendations,watch/providers
to load regional streaming/transaction provider metadata without another request.
The watch/providers append segment keeps the literal slash for compatibility.
Only a capped set of regions, beginning with the device's locale, and at most
20 offers per region are materialized to protect ARM32 memory. The detail page
shows subscription/free/rental/purchase options with country filtering and
links only to TMDB's official availability page. No stream URLs are exposed.
JustWatch attribution is mandatory when displaying this data.
Provider listings are informational and may be outdated or incomplete. Anime details include characters/Japanese voice
actors, staff, reviews, relations, trailer reference, next-airing schedule,
and AniList's optional streamingEpisodes (external HTTPS links only).

**These APIs do not provide movie/TV streams.** Trailer handling is device-specific:
Android TV opens the official YouTube TV app via `vnd.youtube:<videoId>` and
`com.google.android.youtube.tv`, falling back to the **same video's** HTTPS
YouTube URL if the TV package is absent. Phones/tablets show an optional
on-demand official YouTube HTML iframe in a short-lived WebView, with an external
YouTube fallback. The WebView uses `loadDataWithBaseURL("https://<appId>/", ...)`
to provide the required YouTube Referer, enforces HTTPS/no file access,
blocks main-frame navigations, has no JavaScript bridge, and is destroyed when
the trailer leaves the composition. TV mode never creates a WebView.

Official AniList episode links remain external HTTPS intents. No media is
played through Telegram's private streaming server or a third-party video host.
YouTube embeds must retain the provider controls, avoid unauthorized overlays,
and meet YouTube's minimum player-size / Referer / playback requirements.

## Developer configuration

The existing BuildConfig.TMDB_API_KEY comes from the project's secrets Gradle
configuration. For local builds, place TMDB_API_KEY in an ignored local.properties
(or in the supported protected build environment). The published source must
not contain a live key. AniList public metadata requires no token.

Do not treat an embedded BuildConfig API key as secret from APK recipients:
a distributed client key is extractable. Before public deployment, verify
TMDB's current permitted client/auth models, account permissions and quotas;
a controlled backend/proxy or a bring-your-own-key model may be appropriate.
Do not place Telegram credentials in the Extras provider graph.

## Caching / network behavior

- 350 ms search debounce; obsolete coroutine results are canceled.
- Provider-specific state, filters and grid position are preserved across tabs.
- Small in-memory JSON LRU cache (4 MB approximate) with a 5-minute freshness
  period and a 2 MB response ceiling. Android memory-trim callbacks shrink the
  cache to 1 MiB on moderate pressure or evict it entirely on critical pressure
  to reduce retained memory on ARM32 TVs.
- AniList request pacing and explicit handling of HTTP 429/Retry-After.
- AniList-specific **Format** (TV, Movie, OVA, ONA, etc.) and **Airing Status**
  (Airing, Finished, Upcoming, On hiatus, Cancelled) dropdowns use native
  GraphQL MediaFormat and MediaStatus filters and never affect TMDB searches.
- Successful provider JSON can be reused from the five-minute LRU. AniList
  GraphQL error bodies are never cached even if the server responds HTTP 200,
  so Retry is not trapped into replaying an error.
- TMDB Movies and TV Shows have a provider-specific **Original language** filter
  (including Filipino, Japanese, Korean and other common languages); Anime's
  AniList-only Format/Status filters remain independent. TMDB Discover receives
  the native `with_original_language` argument. TMDB Search and Trending apply
  this filter to the returned page using the provider's `original_language`.
- Local TMDB genre, year and minimum-rating filters use raw response values,
  not rounded display ratings. Search sorting by rating or year is confined to
  each fetched page, not a guarantee of global ranking. Related titles explicitly
  exclude provider-marked adult results and duplicate IDs.
- When changing a query or filter, only that provider's poster grid resets to
  the first item; switching between tabs keeps each provider's own scroll state.
- TMDB Trending is a provider endpoint, not a synonym for Discover popularity.
- Discover-only filters are never sent to TMDB Trending; unsupported filters on
  search/trending apply to received pages locally, which may yield a sparse page.
- Network requests use I/O dispatchers and finite connection/read timeouts.
- Coil handles poster/image decoding; test image cache and bitmap memory under
  ARM32 pressure before increasing poster sizes or prefetching.

## TMDB credentials intentionally deferred

- TMDB Movies and TV Shows show a dedicated setup-pending state instead of
  repeatedly attempting an unconfigured provider request or displaying a
  developer-facing `local.properties` instruction. The state includes a
  focusable **Browse Anime** action available by remote DPAD_CENTER.
- AniList remains independent and needs no TMDB credential. No key, placeholder
  value, or credential validation detail is shown in the catalog interface.
- The same bounded provider readiness policy is exercised by JVM tests, and
  the setup-pending screen by offline Android emulator instrumentation.
- AniList pagination stops at its supported page 200 even when a malformed
  provider response still advertises a next page, avoiding repeated terminal
  requests and unnecessary rate-limit consumption.
- TMDB credential injection is explicitly deferred until the maintainer
  configures the key and builds a new APK. This is not a live-key management
  feature and is not evidence that the TMDB endpoints were tested live.

## Autocomplete and loading UI

- Each provider's search field can reopen autocomplete on focus. Results are capped
  at five, drawn only from the current provider, and dismissible via an explicit
  **Dismiss** button or Android TV remote Back. Selecting a suggestion closes
  the popup before opening details. Back from the dismissed popup continues to
  the existing mode-selection route; search text is retained per tab.
- Search has a focusable **Clear** action. An empty filtered result offers
  **Clear search and filters**, so users can recover without a hidden menu.
- First-page catalog and title-details loading use static responsive poster and
  text placeholders instead of a single blank view. Placeholders are deliberately
  animation-free for low-memory TV hardware and require no provider credentials.
- Search suggestions have a separate, smaller Coil decode target: **64×96**
  pixels on low-memory/32-bit devices and **96×144** on standard devices. This
  does not change grid or details poster quality.
- The Android emulator smoke suite exercises suggestion dismissal, provider
  isolation, a DPAD_CENTER suggestion selection, and static skeleton rendering
  without TMDB API credentials or AniList network access. Device/TV hardware
  qualification remains separately required.

## TV and responsive acceptance criteria

- On Android TV, D-pad can enter search, switch tabs, operate sort/genre/year/rating,
  select suggestions and posters, open details, seasons and external links.
- On phone/tablet, Android Back first closes a visible embedded YouTube trailer
  (destroying the WebView), then navigates out of title details on the next Back.
- Back closes Credits, returns to the previous related-title details page, then
  returns to catalog with the prior grid scroll and selected-poster focus, and
  finally returns to the mode chooser.
- Search suggestions expose visible focus borders for D-pad users; the three
  catalog tabs remain horizontally scrollable on narrow screens and large fonts.
- If TV season fetching fails, a Retry button is accessible without changing
  seasons; before selection, clear guidance explains how to load episodes.
- Phone portrait/landscape and tablet UI must remain readable, touch accessible,
  and landscape-safe.
- Test with no TMDB key, no network, provider 429, missing poster, long title,
  no seasons/episodes, a title with many related items and 30-minute TV browsing.
- ARM32 and ARM64 APK compile/packaging CI is **not** a substitute for hardware
  focus tests, memory traces, actual provider search/detail trials or usability QA.

## Provider attribution and licensing

TMDB requires the approved logo and the notice:
"This product uses the TMDB API but is not endorsed or certified by TMDB."

Bousygram's About and Extras Credits display the notice and logo.
Logo geometry/gradient are transcribed from the TMDB artwork reproduced at
`logo/themoviedb.svg` in the source project
`Samuel-Benso/TMDb`, which identifies the TMDB artwork.
Official logo policy: https://www.themoviedb.org/about/logos-attribution
Before release, confirm this specific legacy logo variant remains approved
and that the conversion preserves artwork, proportions, colors, and licensing
obligations. If not, replace with a current official asset.

AniList is the source of anime data/cover images. Streaming-episode links are
provided by AniList as references to third-party legal services; their
availability and licensing vary by country, and Bousygram is not an affiliate.

## Cancellation, images and reproducible qualification

- The dedicated Extras HTTP transport disconnects `HttpURLConnection` when
  its calling coroutine is canceled. The worker checks cancellation between
  network reads, uses finite timeouts and the existing 2 MB response limit,
  and never writes a canceled response into the JSON cache. JVM tests use a
  fake connection deliberately blocked on a response, then cancel its caller
  and assert the connection is disconnected.
- Grid and detail posters use bounded Coil decode requests, selected by
  `ActivityManager.isLowRamDevice` or a 32-bit process. ARM32/low-RAM
  grids request up to 240×360 pixels and details up to 360×540; standard
  devices remain capped. This does **not** imply a measured reduction in
  total app PSS: bitmap caches, density, GPU layers and long sessions still
  require on-device profiling.
- `ExtrasCatalogTabs` and `ExtrasPosterCard` expose stable UI tags for
  Android Compose instrumentation. Offline tests send DPAD_CENTER to tabs
  and posters and can compile with the app without any TMDB credentials.
  Firebase Test Lab or a physical device must actually run them before
  claiming D-pad behavior was tested.
- Run `tools/qualification/Capture-BousygramExtrasPerf.ps1 -Stage SELECT`,
  `-Stage EXTRAS`, and `-Stage TELEGRAM` **separately** after manually
  navigating to each screen on the same device. Use repeat runs after
  force-stop and compare averages on ARM32 and ARM64. The script captures
  only aggregate PSS and frame counters and labels results `captured-only`,
  not a production qualification. No physical-device run was performed
  as part of this change.
- A manual GitHub Actions workflow
  `.github/workflows/extras-live-provider-smoke.yml` exercises **real**
  TMDB movie/TV discovery, language filtering, detail watch providers,
  a TV season, and AniList discovery/details. It needs the protected
  repository secret `TMDB_API_KEY`; without it, the run fails explicitly.
  This live check is opt-in and is *not* claimed as part of default PR CI.
  No credentials or raw provider payloads are uploaded to artifacts.

## Long-session catalog memory window (ARM32 and ARM64)

The previous catalog appended every page indefinitely to Compose state.
TMDB can return hundreds of pages and AniList can return many dozens; three
independent tabs could therefore retain thousands of poster/title references
during a long browsing session even though the grid itself is lazily composed.

- Each provider tab now maintains a **sliding window of unique results**:
  **120 titles on 32-bit / low-RAM devices**, or **240 titles on standard
  devices**. Earlier title objects are released from Compose's retained
  result lists as new pages are appended. Duplicate provider/type IDs are
  removed without confusing identically numbered TMDB and AniList items.
- The response page counter continues advancing; a dropped page is **not**
  silently reloaded. A small notice explains why older titles disappeared,
  with a focusable **From start** button to explicitly fetch page 1 again.
  Resetting a query/filter also fetches page 1. Provider-specific tab states
  stay independent and stable poster IDs support LazyGrid scroll anchoring.
- JVM regression tests append **100–300 synthetic pages**, enforce the bounds
  after each page, exercise deduplication/rewind and provider isolation. An
  offline Compose emulator test uses DPAD_CENTER to invoke **From start**.
- This limits **Kotlin references in UI state**, not the Coil/GL bitmap cache.
  The existing bounded image decode sizes and Android-managed Coil LRU are
  unchanged. Adjusting a shared Coil cache without physical profiling risks
  extra decoding/jank or disturbing Telegram image performance.
- To capture observed **total PSS** at a chosen page count, run for example:
  `Capture-BousygramExtrasPerf.ps1 -Stage EXTRAS -CapturePoint Baseline -LoadedPages 1`
  and after manually browsing more pages,
  `Capture-BousygramExtrasPerf.ps1 -Stage EXTRAS -CapturePoint LongSession -LoadedPages 30`.
  Then pass the two actual JSON evidence paths to
  `Compare-BousygramExtrasPerf.ps1 -Baseline ... -LongSession ...`.
  Repeat using the same APK/build, device, waiting periods, and sample count
  on 32-bit and 64-bit devices.
- Those commands require **an authorized physical/emulated ADB device**;
  no device measurement was performed by this PR. The comparison is a
  descriptive memory delta, **not proof of a causal performance improvement
  or production release qualification**. Record profile artifacts as private
  evidence; the scripts deliberately avoid saving ADB serials or Telegram data.

## Unverified / separate work

- No real-device ARM32 TV, ARM64 TV, phone or tablet qualification is claimed.
- No persistent disk metadata cache or offline completeness guarantee is made.
- No arbitrary WebView/iframe integration, DRM circumvention, third-party
  player injection, direct TMDB/AniList playback, or fuzzy cross-provider ID mapping.
- Deferred: provider test doubles, end-to-end instrumentation for Extras,
  official logo audit, controlled TMDB key distribution and measured cold-start
  memory profiling when opening Extras without Telegram.

## Additional bounded-window navigation

- **Previous page** offers an explicit single-page rewind without holding all historical
  results in Compose state; the page request replaces the active window and scrolls to
  its start. The existing **From start** action remains when earlier pages have been
  discarded by the memory budget.
- Telegram card focus now falls back to a real visible entry when its old
  chat/message key has left the bounded active catalog. Neither action fabricates
  catalog data or requires provider credentials.
- Offline JVM and Compose D-pad regression cases cover fallback identity and page action.


## Supplement (MediaForge opt-in feature)

This file describes pre-MediaForge metadata behavior; the new opt-in embedded source UX is documented separately in MEDIAFORGE_EXTRAS_INTEGRATION.md.
