# Extras: episode navigation and anime metadata cross-catalog — 2026-11-21

## Decision and design constraints

A separate catalog graph enriches AniList anime titles with optional TMDB season/episode metadata. **AniList IDs remain the only anime playback IDs.** TMDB IDs are used only to retrieve title, episode, description and still metadata after a user confirms an exact mapping. TMDB series/season numbers must never silently be treated as AniList season numbers.

This milestone is an explicit exception to the earlier feature freeze, scoped to episodes and metadata. It does not modify Telegram Media, TMDB browsing, MediaForge extraction or embed server catalogs.

## User-facing implementation

- TV WebView player: **Previous**, **Sx · Ey**, **Next**, plus a season/episode chooser. Navigation is bounded to known TMDB episode counts and may cross from the last episode of one numbered season to the first episode of the next confirmed season. The current server is preserved when moving episodes.
- Anime WebView player: **Previous**, **Episode n**, **Next**, and a bounded episode chooser, based on AniList's declared episode count. A new AniList season/cour may have another ID; cross-title navigation is not guessed.
- Movies have no episode navigation.
- AniList detail page: 48-episode lazy selection windows for long-running shows, reusing the existing Play action.
- Optional **Find TMDB episode descriptions**: search up to 12 TMDB TV title candidates, explicitly select a show, one TMDB season, and the TMDB episode matching AniList Episode 1. Titles, stills and overviews are mapped into AniList episode numbering. The app continues using AniList embed routes for Play.
- The public-ID match catalog is locally persisted by AniList ID in private Android app preferences. It has no tokens, credentials or user video data and supports review/removal.
- TMDB unavailable / no key / no approved match: AniList episode counts and streaming still work. Never auto-match from fuzzy title/year similarity alone.

## Why ID matching needs a per-entry episode offset

Different sites may consider "Season 2" a new AniList title, a second TVDB season, or episodes 13–24 of TMDB Season 1. For example, a confirmed AniList E1 → TMDB S2E13 maps AniList E12 → TMDB S2E24. This cannot be derived from string similarity alone, so user review is mandatory.

The current matching catalog intentionally maps **one AniList entry to one TMDB show-season and starting episode**. It does not promise automatic cross-cour transitions or complete TMDB seasons for multiple AniList IDs. Related-series cards already expose other AniList entries, where each can be matched separately.

## Implementation surfaces

- `ExtrasEpisodeNavigation.kt`: pure sequence rules, ID pinning and offset mapping.
- `ExtrasPlayerEpisodeControls.kt`: 48-item bounded dialog, TV season selector, previous/next.
- `ExtrasAnimeTmdbMatchStore.kt`: tiny private per-entry mapping store.
- `ExtrasAnimeTmdbMatchDialog.kt`: candidate review and explicit confirmation.
- `ExtrasCatalogApi.kt`: bounded TMDB TV candidate search; existing `/tv/{id}/season/{number}` episode endpoint.
- `ExtrasCatalogScreen.kt`: mapped metadata loading, both episode players and AniList details.
- `ExtrasMediaForgeScreen.kt`: shared player navigation, remounting WebView when episode URL changes.
- Tests include source ID and cross-season restrictions, split-cour offsets, bounded lists and offline Compose navigation.

## Regression and privacy gates

1. Fetches use existing HTTPS TMDB transport, caching and API-key checks, not a second credential channel.
2. Do not make candidate search an automatic verified match. No AniList ID may be rewritten to TMDB.
3. No auto-next or provider/episode switching at end of media without a deliberate user action.
4. MediaForge's existing title/episode, host and TLS navigation security remains intact.
5. Long series must render bounded episode windows (48, max 60) with stable keys/focus.
6. TV episode navigation may only enter seasons with known positive episode counts; no fabricated availability or streaming quality claims.
7. The app's private local metadata map contains only public catalog IDs, season/offset and public title, and must be removable.
8. CI must pass Enterprise, responsive UI and media emulator on exact head before merge. Physical Android TV and phone playback are separate, as current third-party WebView compatibility is not guaranteed.
9. TMDB metadata attribution must be shown whenever TMDB episode enrichment is used.

## Next milestone (not silently included)

- Add an opt-in multi-entry mapping editor to link related AniList sequels/cours *only when each AniList ID is independently confirmed*.
- Optionally import a licensed ID crosswalk dataset after auditing provenance, copyright, retention rules and TV/episode ordering; store explicit evidence/confidence rather than pretending a mapping is definitive.
- Add offline match export/import for user-curated public metadata, only after license and privacy review.
- Add device screenshot matrix for anime episode guide, mapping dialog, short landscape and D-pad focus, then profile the impact on 8 GB Android TVs.
