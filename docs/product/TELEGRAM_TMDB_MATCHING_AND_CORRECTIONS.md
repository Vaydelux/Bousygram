# Telegram Media — TMDB Matching and User Corrections

## Implemented scope (2026-11-20)

Bousygram retains native Android Jetpack Compose and its separate Telegram and
Extras player routes. This feature adds **user-controlled** identification
and corrections for Telegram video cards without changing playback routing.

### User workflow

1. Sign in to Telegram and open **Settings → Advanced → TMDB discovery for Telegram videos**.
2. Enable the account-scoped opt-in. Off by default. Without consent no Telegram
   title/filename search leaves the device.
3. In Home / Library Telegram video grid, open the existing **Media actions**
   menu on a card (mouse, touch, remote D-pad) and choose
   **Identify / correct metadata**.
4. Review the cleaned suggested title and year; edit either field, pick
   Movie or TV series, then **Find TMDB matches**.
5. The dialog shows up to 12 TMDB candidates with provider title, year and
   media type. No result is automatically selected; remakes remain distinct.
6. Choose a result. A bounded detail lookup enriches the explicit selection.
   If the detail lookup fails, the selected search result's public metadata
   may still be saved without inventing unavailable facts.
7. **Don't match this video** suppresses existing TMDB decoration for that
   message; **Restore original metadata** removes the local correction.

### Safety and correctness

- The Telegram chat/message/file IDs, source URL, filename, caption, and
  player engine are **never** submitted to TMDB. Lookup sends only the title/year
  entered by the user and the selected catalog category.
- Existing title/season/episode parsing remains local and nonblocking.
  Browsing, D-pad focus, page loading and autoplay do not trigger new TMDB
  requests. Explicit opt-in plus search-button activation is required.
- TMDB matches are **not** chosen by popularity rank. Search result selection
  is human-controlled, including remakes, uncertain years and anime.
- Corrections are persisted to private per-Telegram-account SharedPreferences,
  keyed by Telegram chat/message. Maximum 300 entries per account, no silent
  deletion of older corrections; invalid/unknown records are not overwritten.
- Account generation checks block cross-account writes after logout. Saved
  metadata cannot change Telegram fileId, chatId, messageId or original title.
- Switching off external discovery disables remote TMDB poster URLs when
  reapplying corrections. Cached title/summary are kept locally.
- Network errors, rate limits, invalid years and unavailable TMDB credentials
  produce explicit in-dialog feedback. A 429 pauses new manual searches for
  60 seconds; all search requests share a 650ms minimum interval.
- Existing 64 MiB per-account TMDB artwork budget and separate Extras catalog
  continue unchanged.

### Tests and operational qualification

- JVM: `TmdbRepositoryTest` covers deterministic search-result parsing,
  ambiguous remakes, deduplication, safe posters and the 12-candidate limit.
- Emulator instrumentation:
  `TelegramMetadataCorrectionsInstrumentedTest` verifies exact-message
  persistence, no playback-identity mutation, skip/clear, corrupt-data
  preservation, and cross-account isolation.
- Existing responsive UI smoke and Cast/media emulator tests remain required.
- Production Cast remains disabled until the separate secure transport and
  physical-receiver gate in #220 is complete.
- **A debug APK is a test artifact**, not a qualified production build.
  Publish from exact `main` only after source and tests pass, with ARM32,
  ARM64 and universal debug-signed APKs and SHA256 manifest.

### Device QA still required

320dp phone, 844×390dp landscape phone, 768dp tablet, 960×540dp TV, 1280×720dp
TV, 1.5–2x system font, remote D-pad and Back/focus restoration; user switches
Telegram account during search; offline, 401, 429, slow internet; album artwork
eviction; 8 GB ARM32 TV long-session cache/memory.

This implementation is not a claim of instant auto-identification of all
Telegram filenames: automatic matching remains deliberately conservative,
and explicit user confirmation is required to avoid incorrect title matches
and unexpected external disclosure of private video names.
