# Extras web embed server catalogs

Source lists: [zoku-tv TMDB catalog](https://github.com/Vaydelux/zoku-tv/blob/e094faeaa76376c2161dc3e66c7d033c3cf04374/lib/embed.ts)
and [zoku-tv AniList catalog](https://github.com/Vaydelux/zoku-tv/blob/e094faeaa76376c2161dc3e66c7d033c3cf04374/lib/anime-embed.ts)
(revision `e094fae`).

- TMDB: 50 source entries from upstream, including CineSrc already present in Bousygram, plus
  two retained Bousygram-specific servers (XXIKU and EmbedHeaven): **52 registered, 51 selectable**.
  TMDB movie and series IDs are never substituted with AniList or IMDb IDs.
- AniList: 5 source entries plus Bousygram XXIKU and EmbedHeaven: **7 total**, separate server picker. Selected episodes use the AniList media
  ID, season 1, and a one-based episode number. AniList media IDs commonly refer to a
  specific season; do not assume one ID provides every sequel season.
- Existing TMDB season 0 (specials) remains valid. Anime episode controls are one-based.
- XXIKU canonical examples supplied for integration: movie `/embed/movie/603` (TMDB 603),
  TV `/embed/tv/1399/1/1` (TMDB 1399, season 1, episode 1), and anime
  `/embed/anime/16498/1` (AniList 16498, episode 1), all at
  `https://player.xxiku.com`. The legacy undocumented `best`, `auto`, and
  `style` query parameters were removed to match those exact URLs.
  The anime URL includes the episode but **not a season**, as AniList media IDs
  already identify the anime entry; the API's season argument is validated but
  intentionally not rendered in this server's URL.
- EmbedHeaven uses `https://player.embedheaven.st` with five supplied patterns:
  `/movie/{tmdb_id}`, `/tv/{tmdb_id}/{season}/{episode}`,
  `/anime/{anilist_id}/{episode}`, `/live/{channel_slug}`, and
  legacy movie fallback `/embed?id={id}&type=movie`. The undocumented
  `autoplay=0` parameter has been removed from the movie/TV URLs.
  The legacy movie fallback is an explicit control on EmbedHeaven movie pages,
  not an automatic provider redirect.
- Live TV is a manual channel-slug entry in Extras (for example, `bbc-one-gb`),
  not a TMDB/AniList catalog tab or an auto-populated channel list. Channel
  slugs must be 1–80 lowercase ASCII letters, digits, or hyphen-separated
  words. Live streams use the existing isolated WebView and guarded native
  handoff; upstream availability and playback support remain unverified.
- Both catalogs use a user-selected HTTPS WebView page. Switching servers disposes the
  previous MediaForge session and invalidates discovered streams, captions and checks.
- `SUPEREMBED_VIP` is registered but **not selectable**. Its upstream
  `/se_player.php` is relative and depends on a separately hosted/configured PHP
  player. No trusted absolute HTTPS endpoint is supplied; never invent one or use
  the `getsuperembed.link` metadata domain as a player URL.
- Upstream's `embed-su` domain metadata points at `embed.su`, but the configured
  movie/TV template targets `embed.filmu.in`. The code deliberately preserves the
  upstream template; this discrepancy must be assessed before distribution.
- Cinezo templates may not include episode selectors, even when a selected episode
  is passed to the URL builder. Provider behavior is external.

**Safety/availability:** Catalog presence means only that a URL template is configured.
It does not verify uptime, embed support, playback success, safety, licensing or user
entitlement. Native stream handoff requires explicit user choice and existing guarded
preflight. Web embeds are never used for Telegram playback, and no DRM bypass or
automatic extraction is implemented.

Validation: `./gradlew :app:testDebugUnitTest :app:compileDebugKotlin`, followed by
Android TV/phone WebView, D-pad, season 0, AniList episode selection and real provider
smoke checks. Do not treat unit tests as live service verification.
