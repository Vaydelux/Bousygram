# Bousygram Extras server availability audit — 2026-11-16

## Method

The configured representative movie URL was attempted for each movie provider with a public web fetcher, alongside domain searches and public documentation checks. Most JavaScript/WebView media embeds were **not accessible to the fetcher**, which is an inconclusive result — not proof of DNS expiration or failed playback. A responsive landing page is not proof that a specific episode will play. No Android/Google TV WebView runtime verification was possible here.

**Confirmed removal:** `vidsrc.xyz` is explicitly categorized as Dead in the domain roster maintained for that provider. The source catalog's relative-path-only `SUPEREMBED_VIP` is unusable without an absolute server and was not selectable. Anime `CINEZO` was registered but unusable because its route omitted the episode ID. These three registrations have been removed. Other sites remain until repeatable evidence proves failure.

Sources: [VidSrc domain roster](https://vidsrc.domains/), [CineSrc docs](https://cinesrc.st/docs), [XXIKU](https://player.xxiku.com/), [XXIKU status](https://player.xxiku.com/status), [EmbedHeaven docs](https://www.embedheaven.st/docs/), [111Movies](https://www.111movies.net/), [VidKing docs](https://www.vidking.net/).

## Configured host inventory before pruning (49 hosts)

| Host | Current evidence | Action |
|---|---|---|
| `0vidads.top` | Embed page not readable from fetcher; live status undetermined | RETAIN — not conclusively dead |
| `111movies.net` | Provider docs available at www.111movies.net | RETAIN — not conclusively dead |
| `1embed.cc` | Embed page not readable from fetcher; live status undetermined | RETAIN — not conclusively dead |
| `atlantic.st` | Embed page not readable from fetcher; live status undetermined | RETAIN — not conclusively dead |
| `cinemaos.tech` | Landing page accessible; individual stream unverified | RETAIN — not conclusively dead |
| `cinesrc.st` | Recently accessible documentation; representative embed inaccessible to fetcher | RETAIN — not conclusively dead |
| `cinextream.cc` | Embed page not readable from fetcher; live status undetermined | RETAIN — not conclusively dead |
| `embed.filmu.in` | Embed page not readable from fetcher; live status undetermined | RETAIN — not conclusively dead |
| `embed.icefy.top` | Embed page not readable from fetcher; live status undetermined | RETAIN — not conclusively dead |
| `embed.wfs.lol` | Embed page not readable from fetcher; live status undetermined | RETAIN — not conclusively dead |
| `fmov.my` | Embed page not readable from fetcher; live status undetermined | RETAIN — not conclusively dead |
| `framextv.xyz` | Embed page not readable from fetcher; live status undetermined | RETAIN — not conclusively dead |
| `mappletv.uk` | Embed page not readable from fetcher; live status undetermined | RETAIN — not conclusively dead |
| `multiembed.mov` | Embed page not readable from fetcher; live status undetermined | RETAIN — not conclusively dead |
| `nontongo.win` | Representative embed returned movie HTML | RETAIN — not conclusively dead |
| `peachify.top` | Embed page not readable from fetcher; live status undetermined | RETAIN — not conclusively dead |
| `player.autoembed.app` | Embed page not readable from fetcher; live status undetermined | RETAIN — not conclusively dead |
| `player.cinezo.live` | Embed page not readable from fetcher; live status undetermined | RETAIN — not conclusively dead |
| `player.embedheaven.st` | Provider page and current documented routes | RETAIN — not conclusively dead |
| `player.smashystream.com` | Embed page not readable from fetcher; live status undetermined | RETAIN — not conclusively dead |
| `player.vidzee.wtf` | Embed page not readable from fetcher; live status undetermined | RETAIN — not conclusively dead |
| `player.xxiku.com` | Recently accessible provider and status page | RETAIN — not conclusively dead |
| `primesrc.me` | Embed page not readable from fetcher; live status undetermined | RETAIN — not conclusively dead |
| `stellar.rip` | Embed page not readable from fetcher; live status undetermined | RETAIN — not conclusively dead |
| `streamvaultsrc.click` | Documentation indexed; uptime reports conflict | RETAIN — not conclusively dead |
| `thisiscinema.pages.dev` | Embed page not readable from fetcher; live status undetermined | RETAIN — not conclusively dead |
| `velarium.cc` | Embed page not readable from fetcher; live status undetermined | RETAIN — not conclusively dead |
| `vidapi.xyz` | Embed page not readable from fetcher; live status undetermined | RETAIN — not conclusively dead |
| `vidcore.net` | Embed page not readable from fetcher; live status undetermined | RETAIN — not conclusively dead |
| `vidfast.pro` | Embed page not readable from fetcher; live status undetermined | RETAIN — not conclusively dead |
| `vidflix.club` | Embed page not readable from fetcher; live status undetermined | RETAIN — not conclusively dead |
| `vidjoy.pro` | Embed page not readable from fetcher; live status undetermined | RETAIN — not conclusively dead |
| `vidlink.pro` | Embed page not readable from fetcher; live status undetermined | RETAIN — not conclusively dead |
| `vidplays.fun` | Embed page not readable from fetcher; live status undetermined | RETAIN — not conclusively dead |
| `vidrock.ru` | Embed page not readable from fetcher; live status undetermined | RETAIN — not conclusively dead |
| `vidsrc-embed.ru` | Provider domain list marks Use New (not Dead) | RETAIN — not conclusively dead |
| `vidsrc.to` | Contradictory uptime reports; not enough evidence to declare dead | RETAIN — not conclusively dead |
| `vidsrc.xyz` | Provider domain list explicitly lists Dead | REMOVE — retired |
| `vidsync.xyz` | Embed page not readable from fetcher; live status undetermined | RETAIN — not conclusively dead |
| `viduki.net` | Embed page not readable from fetcher; live status undetermined | RETAIN — not conclusively dead |
| `vidup.to` | Embed page not readable from fetcher; live status undetermined | RETAIN — not conclusively dead |
| `vidy.st` | Embed page not readable from fetcher; live status undetermined | RETAIN — not conclusively dead |
| `vidzen.fun` | Embed page not readable from fetcher; live status undetermined | RETAIN — not conclusively dead |
| `vixsrc.to` | Embed page not readable from fetcher; live status undetermined | RETAIN — not conclusively dead |
| `vsembed.ru` | Embed page not readable from fetcher; live status undetermined | RETAIN — not conclusively dead |
| `web.nxsha.app` | Embed page not readable from fetcher; live status undetermined | RETAIN — not conclusively dead |
| `www.2embed.cc` | Recent domain registration/certificate evidence; embed unverified | RETAIN — not conclusively dead |
| `www.vidking.net` | Provider documents selected embed route | RETAIN — not conclusively dead |
| `zoryva.me` | Embed page not readable from fetcher; live status undetermined | RETAIN — not conclusively dead |

## Caveats

Bot protection, geo restrictions, 403/429, JavaScript-only pages and transient errors cannot establish domain expiry. A complete playability judgment requires a representative movie, TV episode, and (where supported) anime episode on a device. The app continues to handle failures in its existing server picker instead of deleting functioning choices based on one test. Do not add DRM bypasses, new undocumented domains, or unsafe redirects.
