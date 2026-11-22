# Telegram large-group performance — stabilization checkpoint

Date: 2026-11-22. Feature freeze maintained. PR scope: no playback/cache/Extras changes.

## Evidence and diagnosis

- User reported that channels/groups with ~2,000 media can load slowly, sometimes require repeated retries immediately after authentication.
- The previous release fixed bounded transient retries, partial group metadata and a 50-dialog preview → 160-dialog directory expansion race (PR #229). All required post-merge checks passed.
- Current source inspection finds another high-confidence bottleneck: `ForumTopicLoader` previously followed every `GetForumTopics` cursor automatically until exhaustion. A 2,000-topic forum could initiate 21 sequential requests just by opening its folder; the UI only needs the first visible topic page.
- No authenticated 2,000-video real-device latency benchmark was available in this environment. A 2,000-topic unit fixture proves request and memory bounds, **not** an actual performance improvement or video playability.

## Implemented P1 hardening

1. **On-demand topic pagination.** Start with 40 topics; load 100 more only when the user chooses **Load more forum topics**. Retain each real Telegram cursor, stable topic-ID keys, previously loaded pages, sorting, and account-scoped cancellation.
2. **Page error recovery.** When a later page fails, keep its predecessor's topics visible and retry from the same cursor; never restart an entire giant topic list just to recover one failed page. Stop repeated server cursors safely.
3. **Topic UI state.** Expose `hasMoreTopics` and `isLoadingMoreTopics` separately. The first page is considered display-ready without falsely caching an incomplete list as a complete snapshot. The remaining topics continue only on demand.
4. **Timeout policy.** A user-requested page retains the existing bounded topic timeout and, on failure, surfaces a refresh action rather than an endless spinner.
5. **Privacy-safe measurements.** Debug-only Android log lines record fixed stage enums, elapsed milliseconds and bounded item counts for: first source row, final source scan, first topic page, subsequent topic page and first media card (warm cache, local index or live TDLib). No chat IDs, names, filenames, URLs, raw errors, credentials or personal messages, and no upload or new persistent telemetry.
6. **Synthetic tests.** Validate a 2,000-topic group does not fetch more than the first page automatically; cursor continuation, navigation-order stability, late callback cancellation, failed page resume and exhausted cursor behavior; validate log format avoids private identifiers.

## Acceptance and safety gates

- Enterprise Gradle/JUnit/lint + adaptive phone/TV emulator smoke + Cast/media emulator on the exact PR commit, before merge.
- No changes to the approved Telegram media playback engine, rolling cache, Extras WebView, TMDB/AniList IDs, or APK packaging.
- On a real signed-in phone/Android TV, compare cold and warm first source, first topic, first playable media (three runs each) and count errors before/after. Do not interpret a quick first-topic page as proof that 2,000 videos are indexed.
- Preserve previous rows if a new page fails, focus on the same topic after append, Back navigation, and safe cancellation when switching accounts.
- Only after measurements, decide whether to optimize `SearchChatMessages`, limit thumbnail concurrency further or instrument TDLib transport. More retries alone can increase latency and should not be added blindly.

## How to collect evidence without ADB

The existing debug APK can be tested manually: report the stage where a spinner or error appears, whether a partial list remains visible, and the WebView/Telegram status message displayed. Engineering can inspect Android debug logs on a qualification device or CI emulator; the app does not upload logs.
Future enhancement (not in this PR): an optional in-app redacted diagnostic panel with consent and copy/export controls for people unable to use ADB.

## PR release rule

Merge only on all three passing automated workflows. New production claims require real-device sign-in testing; existing APK pre-releases remain available until a separately qualified debug APK is generated from `main`.
