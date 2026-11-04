# Bousygram Extras — implementation and release gates

Updated: 2026-11-04

## Product boundary

Extras is **native, metadata-only discovery** on Android TV, phone and tablet.
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
- TMDB Trending is a provider endpoint, not a synonym for Discover popularity.
- Discover-only filters are never sent to TMDB Trending; unsupported filters on
  search/trending apply to received pages locally, which may yield a sparse page.
- Network requests use I/O dispatchers and finite connection/read timeouts.
- Coil handles poster/image decoding; test image cache and bitmap memory under
  ARM32 pressure before increasing poster sizes or prefetching.

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

## Unverified / separate work

- No real-device ARM32 TV, ARM64 TV, phone or tablet qualification is claimed.
- No persistent disk metadata cache or offline completeness guarantee is made.
- No arbitrary WebView/iframe integration, DRM circumvention, third-party
  player injection, direct TMDB/AniList playback, or fuzzy cross-provider ID mapping.
- Deferred: provider test doubles, end-to-end instrumentation for Extras,
  official logo audit, controlled TMDB key distribution and measured cold-start
  memory profiling when opening Extras without Telegram.
