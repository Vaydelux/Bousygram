# Bousygram Extras — Vyla SDK assessment and Zoryva embed integration

## Sources inspected (2026-11-15)

- Vyla: https://vyla.cc/sdk (JavaScript-driven page, not a directly usable Android integration reference)
- Vyla Stream API: https://vyla.cc/stream-api/overview/
- Vyla error / fallback guidance: https://vyla.cc/guides/error-handling/
- Vyla provider operations: https://vyla.cc/stream-api/operations/
- Vyla organization: https://github.com/vyla-entertainment (GitHub repositories archived; current project sources announced on GitLab)
- Zoryva official embed builder and routes: https://www.zoryva.me/embed
- Zoryva privacy, usage and legal terms: https://zoryva.me/privacy

The direct non-www Zoryva embed root returned HTTP 403 to the inspection
client. Its publicly indexed www documentation was readable and specifies
the canonical non-www iframe URLs below. **Live Android WebView playback
was not confirmed**, nor was content licensing or availability.

## Recommendation: Vyla SDK

**Do not add the Node.js Vyla SDK directly to the Android TV APK.** Its
architecture is useful, but its runtime and provider-resolution boundary
are not an Android Kotlin library. Vyla's *optional* self-hosted Stream API
provides an SSE layer above that SDK:

- `GET /movie?id={tmdbId}` and `GET /tv?id={tmdbId}&season={s}&episode={e}`
- `meta`, `source`, and `done` events, with progressive candidates.
- HTTP endpoints for subtitles, downloads, health and test diagnostics.
- Proxied source URLs that depend on the operator's runtime and policies.

A public or personal Bousygram integration would require a separate,
secured, rate-limited HTTPS service, working provider and usage rights,
failure/retry metrics, a properly authenticated SSE client, and source
lifecycle management. Bundling API keys in the APK does not make them
secret. The GitHub organization says its older repositories are archived
and points to GitLab; dependency activity and license compatibility
would need independent review before adoption.

**Preferred**: keep MediaForge's on-device, user-controlled workflow and
borrow safe *concepts* (per-provider adapters, verification state, explicit
fallback, cancellation, bounded timeouts, and provenance).

## Zoryva's documented integration surface

| Content | Documented direct iframe |
| --- | --- |
| Movie | `https://zoryva.me/embed/movie/{tmdb_id}` |
| TV episode | `https://zoryva.me/embed/tv/{tmdb_id}/{season}/{episode}` |
| Anime | `https://zoryva.me/embed/anime/{anilist_id}/{season}/{episode}` |

Movies/TV use TMDB numeric IDs; anime uses **AniList** numeric IDs.
IMDb IDs are not accepted. Paths with season/episode are preferred to
equivalent query parameters. The optional query parameters documented by
Zoryva include:

- `color=ff0a43` (six-digit accent; a leading `#` is accepted)
- `autoplay=false` (default true, browsers may mute)
- `startAt=120` or `t=120` (seconds, maximum 86,400)
- `exit=hide`, `cast=hide`, `pip=hide`, `fullscreen=hide`,
  `volume=hide`, `captions=hide`, `settings=hide`,
  `servers=hide`, `quality=hide` (also support `false` / `0`).

The optional host-page message is `zoryva:embed-close`; integrations
must check `event.origin === "https://zoryva.me"`. The native Android
screen already owns Back/Exit, so no unverified JavaScript bridge or
postMessage handler was added.

## What Bousygram implements

- ZORYVA is explicitly selectable in the **movie/TV** and **anime**
  Extras WebView server pickers, without changing the default provider.
- All three URL templates are taken from the documented embed builder,
  not guessed from title pages.
- The existing URL template validator rejects invalid IDs/identities;
  Zoryva `startAt` is capped to the documented 86,400 seconds.
- Anime routing preserves both season and episode across provider swaps.
- The integration is **WebView-only**: it does not automatically harvest
  media URLs for native playback or perform direct stream scraping.
- Existing WebView isolation, TV D-pad/cursor fallback, no-Telegram-credential
  policy, lifecycle cleanup, strict initial cookie/autoplay policy and
  manual browser handoff remain in place.
- No API key, proxy, Kotlin dependency, new permission, new service, or
  background polling was introduced.

### Why WebView-only?

Zoryva publishes an iframe integration, **not** a publicly documented
HLS/MP4 resolver. Its terms restrict bots/scrapers and circumventing
playback protections, ads, or paywalls. Its embed page also instructs
integrators to present only content they are authorized to show.
An iframe is a discrete third-party web session, not a guarantee that
the underlying media can be copied or natively extracted.

## Test and release gates

- Unit tests verify exact movie, TV, anime (including non-default season)
  URL shapes, bounded start time and TMDB/AniList identity separation.
- Test that switching to Zoryva selects WebView-only behavior, and
  switching away restores other providers' existing native workflow.
- Existing Android Enterprise compile, test, security checks and both
  emulator smoke workflows must pass on the PR's final commit.
- Recheck untrusted iframe behavior on an actual ARM32/ARM64 Android TV;
  a browser-readable documentation page is not evidence of playback,
  subtitle correctness, D-pad usability, uptime or lawful distribution.

**Status:** documented interface implemented; real upstream playback
availability / rights remain unverified. Do not claim Zoryva or Vyla
to be a guaranteed working streaming server.
