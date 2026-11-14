# Extras — UI, remote navigation and catalog API review
**Scope:** Bousygram Android TV / phone Extras (Movies, TV Shows, Anime)  
**Baseline:** Merged frozen debug source `17d5b0d09c1fce202c94ccf193898877a5f2f6a1`  
**Branch:** `feat/extras-unified-focus-artwork-20261115`

## Objective and non-goals

Make Extras visually and operationally consistent with Telegram Media while
keeping its independent catalog and playback paths. This is a UI/API reliability
pass, **not** a rewrite of TDLib, Telegram playback, FFmpeg, embedded third-party
streaming providers, or the native media engines.

## Code findings and changes

| Surface | Verified problem | Change | Trade-off |
|---|---|---|---|
| Poster grid | Fixed 154/205dp height + `ContentScale.Crop` clipped full portrait key art as cell widths changed | Share a 2:3 poster aspect, use `Fit`, corner clipping and a readable missing-art placeholder | Some unusual provider ratios letterbox rather than being cropped |
| Detail posters | Independent width and height branches and low-resolution TMDB details | Full aspect ratio, TMDB w500 detail image; AniList extraLarge with large fallback | More image bytes over network when opening one detail, but bounded decoder remains |
| Details hero | TMDB backdrop / AniList banner metadata was fetched but not presented | Theme-adaptive cinematic backdrop hero with ARM32/64 decode limits and readable overlay | One extra image request per opened detail, no extra metadata API request |
| Catalog tabs | Three destinations in a LazyRow could be partially hidden on narrow phones | Three simultaneously visible, shared `BousygramSectionTab` controls | Labels may ellipsize under unusually large accessibility font scales |
| D-pad focus | Many Material3 actions offered weak/default device-dependent focus feedback | Theme-primary focus outline for search, actions, filters, seasons, paging and server controls | Focus stays on the original actionable control; no fake invisible focus targets |
| Virtual cursor | Unconventional arrow polygon had poor small-scale recognition | Conventional white pointer with dark edge, theme halo, exact top-left click hotspot | Still a virtual pointer; does not replace native D-pad focus inside a third-party WebView |
| AniList 429 | Cooldown check rejected even a *fresh cached* GraphQL result | Return cache before checking provider cooldown; keep limiter/backoff for network requests | Cached metadata can be up to its TTL old |

## API architecture and proper utilization

### TMDB
- Movie/TV tabs are independently routed to TMDB search, discover and the
  weekly trending endpoints. Filters use provider query parameters when
  available, with raw-result post-filtering for search/trending restrictions.
- Detail fetch already combines `credits,videos,reviews,recommendations,watch/providers`
  with TMDB `append_to_response`; preserve that single request instead of
  serial endpoint calls.
- Existing information from the response is displayed through poster
  metadata, cinematic backdrops, details, cast, trailers, recommendations,
  seasons, reviews and regional watch-provider listings.
- The TMDB API key bundled in an APK **is extractable**; build secrets protect
  GitHub storage but do not make on-device credentials confidential. A public
  consumer app requiring secret credentials needs a scoped backend/proxy and
  abuse/rate-limit protection. Never log the credential.

Official reference: https://developer.themoviedb.org/docs/append-to-response

### AniList
- The public GraphQL endpoint is queried with one `Page.media` collection
  and its `pageInfo.hasNextPage`, rather than depending on inaccurate
  total/last-page values.
- Rich detail query already includes descriptions, cast/voice actors,
  studios, airing information, links, community reviews and relations.
  Show this existing metadata instead of multiplying API queries.
- AniList documentation currently describes a degraded **30 requests/min**
  service limit. The existing token bucket approximately spaces sustained
  requests by 2.1 seconds (~28.6/min), with burst and 429 backoff safeguards.
- Fresh cache hits are now usable during a provider cooldown; HTTP/network
  requests still respect rate limits, cancellation, bounded payload size,
  and the error response cache guard.

Official references: https://docs.anilist.co/guide/rate-limiting
and https://docs.anilist.co/guide/graphql/pagination

### Provider isolation
TMDB IDs and AniList IDs are different namespaces. Catalog metadata is
separate from embedded player servers: neither API guarantees an authorized
playable video, working WebView, subtitles, or region-unrestricted content.
Third-party players cannot be made reliable by adding catalog API requests.

## Security, process and performance checks

1. Preserve HTTP private-host/invalid-port rejection, disabled redirects,
   cancellation, bounded JSON, and no Telegram session leakage into Extras.
2. Preserve native player/Telegram Media caching and playback code, and
   avoid new perpetual drawing animations or background polling on ARM32.
3. Keep poster and backdrop decoder sizes bounded. Never decode full-resolution
   provider art into every grid card.
4. Avoid additional TMDB/AniList requests just to duplicate already appended
   or GraphQL-selected fields. Track future provider quotas and cache expiry.
5. Maintain remote semantics and test tags; focus should remain visibly
   selectable with D-pad, not depend exclusively on hover or touch.

## Qualification plan

- Unit: poster aspect ratio and image RAM budgets; existing provider-routing,
  URL safety, caching, rate-limit and cursor-motion tests.
- Emulator UI: all three tabs visible, poster exact ratio, D-pad tab and
  poster click; playback server selection; pointer click semantics.
- Visual artifacts (local synthetic media, no external API or copyrighted
  posters): `extras-catalog-dark.png`, `extras-catalog-light.png`,
  `extras-catalog-focus.png`, `extras-detail-hero.png`,
  `extras-webview-pointer.png` alongside existing Telegram/theme evidence.
- Release gates: Android Enterprise Checks, Explorer Responsive UI Smoke,
  Android Emulator Cast Smoke, and source-diff/visual inspection **before
  merging**. A subsequent debug release must independently verify the merged
  `main` commit.
- Physical qualification remains necessary for ARM32 TV memory pressure,
  third-party WebView D-pad compatibility, subtitle windows and sustained
  native playback; passing emulator tests is not proof of these devices.

## Release boundary
Keep the feature branch independent of the previously frozen debug release.
No production release declaration until the actual code revision passes all
required checks; merge only the tested revision.
