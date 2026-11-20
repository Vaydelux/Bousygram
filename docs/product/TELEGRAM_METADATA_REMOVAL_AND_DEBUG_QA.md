# Bousygram — Telegram external metadata removal and Debug QA

Date: 2026-11-20
Scope: **Telegram Media only**. Extras and its own TMDB/AniList movie,
TV and anime discovery **must remain unchanged**.

## Desired Telegram experience

- Original Telegram file name, caption, thumbnail, topic, chat and episode
  ordering remain the source of truth.
- Existing local filename parsing to infer title/year/season/episode continues
  to run without an external provider.
- No TMDB title lookup, automatic matching, Identify / Correct metadata
  button, override-dialog, cache download, third-party poster preference,
  or Telegram metadata consent setting.
- Original Telegram TDLib playback fileId, chat/message identity and existing
  Media3/LibVLC engines remain untouched.
- No private Telegram title, account ID, filename or video bytes sent to TMDB
  by browsing, seeking, focusing or starting playback.
- Extras **continues** to use its independent TMDB/AniList catalog and poster
  source. The Settings About screen must retain relevant TMDB attribution.
- Existing source-specific Cast security restrictions remain unchanged.

## Implementation / rollback details

- Restore five Telegram-specific Compose/ViewModel components to the proven,
  local-only version from commit `d5e9d15e`, with card thumbnail preference
  explicitly pinned to the Telegram source rather than TMDB.
- Remove manual metadata correction UI, account-scoped correction persistence,
  TMDB-specific Telegram repository and corresponding obsolete tests.
- Remove the Telegram metadata search opt-in setting and preference API.
- Telegram series grouping and search no longer consult legacy TMDB titles.
- Do **not** rename or remove existing nullable TMDB fields from the
  `MediaItem` serialization shape in this release. They remain for older
  persisted object compatibility but are no longer populated or consumed by
  Telegram browsing, player, card selection or title grouping.
- Do **not** erase user app data, downloaded Telegram files, or other account
  resources as part of this feature removal. Old unused metadata cache files
  can be addressed later by a user-controlled storage cleanup.
- Introduce `tools/ci/verify_telegram_local_only.sh` and a JVM regression
  test confirming that stale legacy TMDB titles cannot alter Telegram series
  naming or search results.

## Required debug APK release gates

1. On the exact current main commit, run Android Enterprise Checks including
   Kotlin compilation, unit tests, lint, native TDLib/ABI validation, and
   the Telegram-only removal guard.
2. Android Explorer Responsive UI Smoke on emulator (phone/TV D-pad focus).
3. Android Emulator Cast Smoke including native media/pause/seek/navigation.
4. Guarded QA Debug workflow: source SHA must match main; all three exact-SHA
   push workflows must have completed successfully.
5. Build debug-signed ARM32 (armeabi-v7a), ARM64 (arm64-v8a), and universal
   APKs; verify signing, SHA-256 manifest, and immutable GitHub prerelease.
6. Hardware smoke remains separate. No claims of physical Android TV
   qualification or production signing from emulator CI alone.

## Focused manual verification

- Telegram Saved Messages, channels and topics: browse/search/play videos,
  retain original filename and Telegram poster, and preserve D-pad focus.
- Check video card action menu has no Identify/Correct metadata action.
- Check Settings Advanced has no Telegram TMDB opt-in switch.
- Series episodes use locally inferred title/season/episode. Videos without
  poster keep their original fallback/thumbnail sampling behavior.
- Extras Movies, TV and Anime still show metadata, artwork and relevant
  details without regression.
- On a low-storage 8 GB ARM32 TV, monitor storage and memory through extended
  playback; a debug APK itself is not evidence of that qualification.
