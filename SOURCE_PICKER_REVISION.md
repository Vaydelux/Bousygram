# Bousygram — Source Picker and Video Library Revision

Date: 2026-10-22

## Scope

This revision intentionally focuses on source selection and browse-screen presentation. Existing Telegram authentication, player construction, streaming/cache internals, and playback navigation were not redesigned.

## What's changed

- Added a horizontal source-tab row to the Home dashboard, Library, and compact layout.
- Kept **Saved Messages** as a dedicated first tab.
- Added an **All chats** dialog with text search and filters for All, Channels, Groups, Private chats (including secret chats), and Archived.
- The source index now loads Telegram's main and archived chat lists, continuing `LoadChats` batches until TDLib reports the list is exhausted.
- Included private chats, basic groups, supergroups (classified as either groups or channels), secret chats, and a fallback category for other chat types. The picker no longer excludes a chat merely because the first media probe did not find a video.
- Chat details are requested in bounded batches rather than launching a request for every chat simultaneously.
- The Library uses a wide poster grid with horizontal source tabs instead of a fixed left chat rail.
- The grid remains **video-only**: it still uses the existing `playableVideos(...)` filter, and photos or ordinary messages are not displayed as playable items.
- The existing progressive video recognition now additionally recognizes `.fmp4` and `.ogg`, including the corresponding MIME fallback mappings.
- Normalized `gradlew` and the mock-test runner to Unix line endings and executable permissions so the scripts can be run in Linux environments.

## Format scope clarification

This revision does not change the playback source architecture. Progressive video containers already recognized by Bousygram continue to use the existing Media3 file-proxy path, with `.fmp4` and `.ogg` added in this pass. HLS, DASH, SmoothStreaming, and RTSP/RTP are streaming source types rather than simple progressive file extensions; reliable playback of manifests and their segment requests needs dedicated source/segment routing. This revision does not claim to add that transport functionality.

## Verification performed

- Kotlin streaming/security helper suite: **27/27 passed**.
- Source-picker contract tests: **6/6 passed**.
- Static project checks and workflow YAML / embedded Bash syntax checks passed.
- Kotlin parser checks passed for the modified app source files.
- `SavedMessagesChats.kt` type-checked against the bundled TDLib API with a small repository/client stub.
- Progressive format helper tests passed with compile-time Media3 API stubs.
- ZIP contents will be integrity-checked after packaging.

## Not verified here

The full Android/Compose Gradle build and APK installation were not run. The Gradle wrapper requires Gradle 9.4.1 and could not resolve `services.gradle.org` in this environment. A complete Android Studio build and on-device run are still required to verify Compose API/type integration, real Telegram account data, TV D-pad navigation, and actual playback.
