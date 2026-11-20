# Bousygram Extras — Zoku TV feature parity roadmap

Status: proposed backlog, not implemented; inspected 2026-11-20.
Sources: [Zoku TV](https://github.com/Vaydelux/zoku-tv) at `0ce6fbefd6653510087aa3fd85a263d8ac5eca97`, especially:
- `components/ContinueWatchingRail.tsx`, `lib/continue-watching.ts`, `components/watch/ResumePrompt.tsx`
- `components/EpisodeThumbnailWithProgress.tsx`, `lib/episode-progress.ts`
- `components/watchlist/LibraryCollectionsAndBackup.tsx`, `lib/custom-collections.ts`
- `components/anime/FranchiseTimelineSection.tsx`, `lib/anilist-seasons.ts`
- `components/TrailerTheaterModal.tsx`, `components/VoiceSearchButton.tsx`

**What is already implemented in Bousygram:** native TMDB/AniList discovery, Android TV D-pad navigation, season/episode metadata, a YouTube trailer route, cast/reviews, responsive cinematic detail screens (merged PR #218), Telegram favorites, and native/WebView Extras playback routes. Do not copy or duplicate these.

## Recommended phases

### E1 — Extras-only Continue Watching and playback progress (highest value)
Implement a local-first, bounded Room store for *Extras* history using keys such as:
`(provider, mediaType, providerId, seasonNumber?, episodeNumber?)`.
Never use Telegram chat IDs or account keys. Start with user-controlled resume and a
Continue Watching rail on the Extras home. Only persist actual playback position
and duration from **instrumented native Media3/LibVLC** sessions. WebView
embeds may not expose trustworthy progress; show no invented percentages or
resume prompts for those sources.

Controls: Resume / Start over / Remove from queue / Clear history.
Debounce progress writes; avoid excessive disk writes. Cap record count and
thumbnails, expire old unneeded entries, prevent cross-provider ID collisions.

Acceptance: reopen the same playable source and restore position within a small
tolerance; preserve explicit Pause, confirm never resumes after EOF/error,
different provider IDs are isolated, and the queue remains bounded on ARM32.
No background polling or huge persistent video caches.

### E2 — Extras watchlist and local custom collections
Add separate Movies/TV/Anime watchlist with categories (Planning, Watching,
Completed, On hold, Dropped) and optional custom collections, inspired by
Zoku's `CollectionPickerButton` and `LibraryCollectionsAndBackup`.
Keep this independent from Telegram favorites and avoid requiring login.
Offer versioned JSON export/import with schema validation, bounded item count,
merge/replace preview and duplicate-key handling. Do not import credentials,
cookies, stream URLs or Telegram auth into a backup.

Acceptance: add/remove works offline; survives restart; same numeric ID in TMDB
Movie versus TV or AniList never overwrites another entry; large backup rejects
safely, malformed JSON leaves existing data intact.

### E3 — Anime franchise guide (high perceived value, medium complexity)
Use verified AniList relations to build a navigable franchise timeline with
Main Story / Side Stories & Movies, entry metadata and watched indicators.
Graph relations are **not necessarily canonical viewing order**. Never claim
that a guessed graph walk is the official watch sequence. Mark uncertain
connections and allow a plain chronological grouping without claiming authority.

Acceptance: cyclic relations do not hang, missing IDs cannot crash, long
franchises remain bounded, each card opens an AniList-backed detail correctly,
and D-pad Back restores the prior selected item.

### E4 — Progress badges on episode cards (requires E1)
Use Zoku's 16:9 EpisodeThumbnailWithProgress as a design reference. Display
completed or progress state only when actual playback events exist. Respect
theme variants, high contrast, reduced motion and accessibility labels.
Emphasize one native Play action per episode, no autoplay on focus.

### E5 — Trailer gallery, then optional voice search
Where TMDB/AniList *actually* returns multiple official clips, add a D-pad
friendly clip picker/modal with focus restoration and deterministic Back.
Keep Android TV YouTube intents and phone/tablet safe WebView behavior.

For voice search, use Android-supported system speech recognition when present,
without requiring unnecessary continuous microphone permissions. Treat lack
of a recognition provider as a normal fallback to text search.

### E6 — Playback next-episode UX (guarded, opt-in)
Zoku's AutoNextOverlay suggests a next-episode prompt, but Bousygram's existing
design goal is to avoid accidental Next. Recommend **manual Next** first, or
an explicitly enabled countdown with visible Cancel; never advance a title
because focus moved or a WebView displayed an unreliable ended event.

## Sequencing and decision gates

- Start with **E1**; E4 and E3 become more valuable when watched state exists.
- E2 can run independently after the E1 local data model is agreed.
- Keep native UI in Jetpack Compose, multi-device policy based on actual dp,
  never physical TV resolution. Check 320×568, 844×390, 768×1024, 960×540
  TV, 1280×720 TV and 1920×1080 logical-dp plus 1.5–2x fonts.
- No new Supabase/Firebase backend is needed for the *initial* local-first
  version; cloud sync is a separate privacy/auth/availability decision.
- Preserve Telegram core isolation, users' explicit Play/Pause decisions,
  limited caching and the existing 16-theme palette.
- Validation gates: Kotlin unit policy/state tests, Android Compose focus tests,
  ARM32/ARM64 build, simulator plus physical Google TV (especially D-pad repeat
  and lifecycle). A design doc or isolated Kotlin compile is not an APK QA pass.

## Branch hygiene note

As of the roadmap merge, PR #217 and #218 have been merged to `main`.
Old branch refs are not automatically deleted by the ChatGPT GitHub connector.
`scripts/Keep-Main-Only.ps1` offers an explicit operator-run remote cleanup
with a dry-run default. It deliberately never deletes `main`.
