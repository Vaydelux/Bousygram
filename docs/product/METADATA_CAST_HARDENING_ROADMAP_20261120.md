# Bousygram — Safe Metadata Discovery and Cross-Device Casting

**Audit / implementation checkpoint:** 2026-11-20
**Target:** existing native Android Jetpack Compose project, Telegram Media and Extras.
**Status:** Phase H1 code implemented; H2–H6 are staged and must not be advertised as shipped.

## Architecture decision

Do **not** add Retrofit solely for the same TMDB API, or a second Ktor HTTP
client. This repo already has:
- \`MediaMetadataParser\` / \`SeriesMetadataParser\` for scene filenames, years,
  anime, season/episode markers.
- \`TmdbRepository\` with account-isolated TMDB metadata, TTL, image download,
  and existing UI fields on \`MediaItem\`.
- \`ExtrasCatalogApi\` for TMDB movies/TV and AniList anime metadata.
- Coil and the existing memory-aware thumbnail policy.
- Google's Cast Android sender, \`CastLanStreamingServer\` (Ktor + TDLib-backed
  Range GET/HEAD), \`CastAccessRegistry\` and session controls.

Adding duplicates raises upgrade cost and produces multiple caches, session
identities and UI loading states. **The proper next step is an enrichment
coordinator around these existing parts** and then a qualified sender/receiver
pipeline, not a library rewrite.

## H1 — Implemented now: error, cache and authorization hardening

1. \`TmdbQueryPolicy\` rejects raw paths/URLs, camera-style names, embedded
   control characters and implausible identifiers before automatic TMDB
   searches. When rejected, original Telegram video and its thumbnail remain
   usable; there is no fake match.
2. \`TmdbRepository\` distinguishes genuine no-match responses (negative-cache
   eligible) from provider failures (timeouts, 429/5xx, malformed JSON, etc.).
   **Do not put a transient failure in the seven-day negative cache.** Retry
   remains possible on a subsequent request; no infinite automatic retries.
3. \`TmdbImageCachePolicy\` bounds this repository's per-account downloaded
   poster/backdrop directory to **64 MiB and 160 files**. Evict oldest
   unprotected generated files; never wipe other files, another account,
   temporary files or the image just obtained. This is separate from Coil's
   own cache and from Telegram's video cache.
4. \`CastHandoffUrlPolicy\` and \`CastLanStreamingServer.canServeCurrentUrl\`
   require the receiver's URL to be the **current active, authorized session
   capability**, with an exact private IPv4 LAN origin and one allowed media
   path and query. Reject stale tokens, public addresses, arbitrary embed
   hosts, localhost URLs and malformed URIs without throwing.
5. Private-media Cast remains **debug-only and disabled in release**. None of
   these checks encrypt LAN traffic; a bearer token is *not* TLS. User account
   binding and capability revocation remain mandatory.

### H1 validation

Unit tests:
- \`TmdbQueryPolicyTest\`: cleaned titles, ambiguous or private filenames,
  length bounds and calendar years.
- \`TmdbImageCachePolicyTest\`: byte budget, file count, protected artwork,
  unknown files, and overflow-safe accounting.
- \`CastHandoffUrlPolicyTest\`: exact capability, private ranges, stale token,
  arbitrary or malformed destinations.

Retain existing TMDB repository and Cast access registry regression suites,
Android Enterprise compilation and release fail-closed checks, both emulator
workflows, and physical device gates.

## H1.1 — Implemented: opt-in Telegram title lookup

- Telegram TMDB enrichment now requires a **per-account, off-by-default**
  setting under Settings → Advanced → TMDB discovery for Telegram videos.
  A cleaned title and year may leave the device **only after opt-in**.
- Extras catalog searches remain separate and explicit; this control does not
  disable the dedicated TMDB/AniList Extras catalog.
- The switch does not remove previously downloaded artwork; clearing the
  local metadata cache remains a separate user decision.
- Settings require an active Telegram account to enable the switch; a missing
  app context or logged-out account must fail closed.
- Parsing filename/year/season/episode remains local even when consent is off.

## H2 — Enrichment coordinator, progressive disclosure and user override

- Extend the current per-account opt-in into optional per-library / per-folder choices; third-party metadata
  lookup. Even a cleaned movie-like title **may be personal information**;
  heuristics alone are not a privacy guarantee. Make discovery optional and
  usable offline, and surface a short in-app notice before first lookup.
- Use one \`MediaDiscoveryCoordinator\` for Telegram Media, with observable
  \`Idle → Parsing → LocalCache → Searching → Found / Ambiguous / Offline /
  NotFound / RateLimited\` states. Never block opening the real local player.
- Rate-limit TMDB requests and deduplicate same-title inflight lookups. Honor
  429 / Retry-After using bounded cooldown and user-initiated Retry; do not
  hammer TMDB on every carousel recomposition.
- Use exact/normalized title + year and media type, not just first popularity
  result. A missing year or multiple matching remakes should show a user
  selection, never silently attach the wrong artwork.
- Allow "Correct match", "Don't enrich this title", "Refresh metadata", and
  a one-item manual search. Save overrides under an account-scoped local key
  without changing the original Telegram filename or TDLib identity.
- Preserve Telegram original thumbnail, file naming and playback if TMDB is
  absent, wrong, offline, rate-limited, deleted or unconfigured.

## H3 — Native Netflix-style discovery, not a web clone

**Telegram Media:** hero/title details + existing grouped library first,
optional TMDB poster/backdrop, rating, year, synopsis, cast, seasons, episodes
and manually editable title match. Keep Telegram playback bound to the
original chat/message/file; metadata is decoration, never authorization.

**Extras:** keep existing TMDB/AniList catalog/detail components; reuse the
shared title details typography, Coil image sizing and failure components.
Do not run a duplicate TMDB network lookup for items already resolved to a
known provider ID.

Use native Compose focus, explicit Play/Details, D-pad rails, bounded
LazyRow/LazyVerticalGrid, 48dp touch targets and 56dp TV focus targets. On
960×540dp logical TV height, do not let artwork or chrome starve episodes.
Decode only requested poster/backdrop sizes; account for 32-bit memory and
1.5–2x font scaling. Reduce motion on relevant devices.

## H4 — Google Cast sender for *eligible, authorized native media*

The Cast sender SDK already exists. Do not introduce a separate mobile
companion app just to use it: Bousygram's Android phone build can be a sender.

- A remote Google Cast receiver cannot read Android localhost or a TDLib
  file ID. It must retrieve an authorized HTTP(S) resource with seekable
  \`Range\`, proper \`Content-Length\`, \`Content-Range\`, MIME, and a supported
  codec/container combination.
- Prototype with a synthetic, redistributable MP4 (verified H.264/AAC); do
  not promise every MP4, MKV, TS, HEVC, 10-bit HDR or ASS subtitle will play
  on every receiver. Container name is not a codec probe.
- Use the existing Cast UI and session. Show connecting, buffering, playing,
  paused, ended, failed, and reconnection states; remote stop must revoke
  capability and restore local playback only on user-approved transitions.
- No Cast of private Telegram media in production until receiver-compatible
  TLS and physical playback/network-security qualification are proven.
  Authenticated cleartext LAN debug casting is intentionally non-release.
- Extras native sources: only after a legally authorized, directly playable
  source and approved delivery route are confirmed. A selected iframe or
  DRM/MSE WebView is *not* a transferable native video URL. Never scrape a
  third-party provider's secrets/cookies to construct a remote URL.

## H5 — DLNA/UPnP as an optional later adapter

Do not add JUPnP/Cling to the APK yet. jUPnP is a maintained Java UPnP/DLNA
library, while the original Cling project is EOL. Evaluate jUPnP and
Android-native alternatives by Android runtime support, security posture,
maintainer activity, ABI/packaging impact and network permissions.

A DLNA control point is not a media server: many DLNA receivers require
the phone to expose the byte-range resource on the LAN. Enforce the same
active-session capability, no public bind, no unsolicited discovery,
short-lived transport leases, visible foreground notification, and session
cleanup. Discover only with explicit user intent and clear permissions.

DLNA has variable codec, subtitle and seek compatibility. Implement adapter
capability negotiation and tests before enabling. It is lower priority than
getting the existing Google Cast path verified.

## H6 — Release and performance qualification (mandatory)

1. Exact-source Enterprise: compile, unit, lint, TDLib JNI hash/ABI,
   release fail-closed, manifest permissions.
2. Android emulator: 320dp phone, 844×390dp landscape, 768dp tablet,
   960×540dp and 1280×720dp TVs, D-pad/Back, offline, cached and cold loads,
   1.5–2x font scale. Preserve prior 63 Extras UI cases.
3. Android TV/Chromecast hardware: Cast sender connects; authorized MP4
   begins; 0/near-EOF/rapid seeks and pause/reconnect work without credential
   leaks or resource loops; session ends on logout, network switch, permission
   revoke and app teardown.
4. Storage: account A cannot see account B's metadata; downloaded artwork
   remains within 64 MiB per active account (except in-flight writes); Coil
   memory and Telegram TDLib disk caches are measured separately.
5. ARM32 8 GB TV: 60-minute session, memory PSS/native heap, disk free space,
   thumbnail churn, absence of main-thread network stalls. See issue #142.
6. Production signing, hardware acceptance and release approval remain gated
   by issue #38. Do not remove production Cast fail-closed for convenience.

## Out of scope for H1

- No new Ktor/Retrofit dependency (Ktor server is already present).
- No new DLNA dependency, new network permission, API token, Cast receiver
  registration, or Chromecast claim.
- No service that exposes a private Telegram file to arbitrary devices.
- No stream remux/transcode on a low-RAM phone as an automatic fallback.
- No automatic "perfect match" claim when a movie year is missing.
- No promise of zero regressions: rely on explicit build/test/physical
  verification, not assertions about what AI should be able to predict.

## References

- https://developer.themoviedb.org/docs/search-and-query-for-details
- https://developer.themoviedb.org/docs/image-basics
- https://developer.themoviedb.org/docs/faq
- https://developers.google.com/cast/docs/android_sender/integrate
- https://developers.google.com/cast/docs/overview
- https://github.com/jupnp/jupnp
