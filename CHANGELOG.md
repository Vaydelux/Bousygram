# Changelog

## Media-loading performance pass (2026-10-22)

- Added progressive TDLib video search so the UI renders merged partial results as each filter completes rather than waiting for the slowest query.
- Removed the overlapping `PhotoAndVideo` query from the video-only library and introduced a bounded query plan, including an expanded candidate limit for videos stored as documents.
- Removed focus-triggered video-byte warmup; ExoPlayer's existing stream preparation now remains the point at which selected video bytes are requested.
- Coalesced thumbnail requests, limited thumbnail concurrency to three, lowered TDLib download priority, added a timeout, and retained thumbnail-only background work.
- Prioritized the first media page ahead of the full Telegram source catalog, with a bounded wait so accounts with no indexed videos do not indefinitely block source loading.
- Added automatic metadata pagination near the end of the Library and compact grids while retaining the manual Load More control.
- Reduced card rendering overhead with remembered image requests, lightweight placeholders, and disabled repeated image crossfades; retained the existing player/ExoPlayer path.
- Prevented a recoverable error from one media filter from displaying an error banner over usable results from other filters.
- Added regression checks and documented the metadata-first loading behavior and device-verification requirements in `MEDIA_LOADING_OPTIMIZATION.md`.

### Verification note (2026-10-22)

- Source picker revision checks: 6 passed.
- Media loading performance contract checks: 10 passed.
- Static XML/source/workflow checks passed; Kotlin CLI helper checks were explicitly skipped in this restricted run, and no Android SDK/Gradle build or device test was performed here.

## 1.0

- Stabilized Telegram playback with session-aware cache-ahead and retry diagnostics.
- Stopped active Telegram cache/download work when playback exits or the app backgrounds.
- Kept partial media cache for faster resume and safer long-form playback.
- Added Settings playback diagnostics export and copy actions.
- Added Settings storage manager with app cache, TDLib media cache, cache limit, and manual prune.
- Added configurable TDLib cache limits: 1 GB, 2 GB, 5 GB, and 10 GB.
- Improved Continue Watching behavior by including the last-played loaded item.
- Added release-ready Gradle versioning and versioned APK artifact names.


## Review and hardening pass (2026-10-20)

- Updated browse and search playback preparation to surface the actual stream-preparation error.
- Disabled Android Auto Backup by default so local Telegram session and database data are not copied to device backups unintentionally.
- Replaced raw deletion of TDLib-owned files with tracked-ID eviction via TDLib `GetFile` / `DeleteFile` confirmations. Pinned files, active playback, precache jobs, and active byte-chunk requests are protected. The cleanup status now compares against actual directory size, so it does not falsely claim the cache is within limits when untracked data remains.
- Connected the configurable cache limit to cleanup and made the Fast Start / Balanced / Stable Network choices influence cache-ahead behavior.
- Encrypted Telegram API credentials with AES-GCM using an Android Keystore-held key, with a one-time migration path for older plaintext preferences.
- Added a persistent Keystore-wrapped 32-byte key for newly created TDLib databases. An existing database without a recorded key intentionally retains its legacy empty-key configuration during upgrade.
- Added short-lived, high-entropy capability tokens scoped to individual file IDs or playlist requests for the Ktor and fallback streaming endpoints.
- Strictly reject malformed, multi-range, and unsatisfiable HTTP byte ranges with HTTP 416 responses.
- Bounded the legacy loopback HTTP parser (request-line/header size, number of headers, socket timeout, GET-only, and duplicate Range rejection).
- Made Home a full-width cinematic dashboard, added a dedicated Library route with source selection and a responsive poster grid, and wired a real cross-source Favorites screen.
- Improved poster sizing for adaptive grids and redesigned the player’s track picker with focused Subtitles/Audio choices, an explicit Off selection, and a close action.
- Made D-pad arrows navigate visible player HUD controls; arrow-key seeking remains available while the HUD is hidden.
- Preserved the correct return destination across Search, Favorites, Settings, Library, and Player navigation.
- Updated the Android TV navigation smoke test to target the real dashboard preview and assert the main dashboard/favorites controls instead of opening a static placeholder.
- Updated CI to install the configured Android compile SDK, resolve versioned APK names before Firebase Test Lab, and require/check the exact release signing certificate on push builds. Pull requests retain debug/unit checks without needing production signing secrets.

### Verification note (2026-10-20)

- The pure Kotlin `HttpRangeParser` and `StreamAccessRegistry` helpers were compiled with `kotlinc` and exercised with focused checks.
- A full Android/Compose compile, Gradle unit-test run, native TDLib integration test, and physical Android TV playback test remain unverified in this environment because Gradle 9.4.1 could not be downloaded and no Android SDK was available here. Treat this as an improved source revision, not a signed or device-validated production release.


## Mock test and CI reliability follow-up (2026-10-21)

- Added a single-command test runner so the Python mock suite can be started from any working directory.
- Added standalone Kotlin checks for HTTP range parsing and resource-scoped local stream tokens.
- Fixed the Firebase Test Lab job gate to use a repository variable instead of a secrets context in a job-level `if`, which GitHub Actions does not permit.
- Switched Firebase authentication to the supported Google GitHub Actions auth/setup-gcloud actions and limited the optional Firebase job to pushes.
- Recorded clear verification boundaries: mock tests pass, but full Android compilation, Gradle/JUnit execution, UI instrumentation, release signing, and physical device playback remain unverified in this environment.

## Implementation checklist — Phase 1 and playback-token follow-up (2026-10-21)

- Removed the debug-signing fallback from `release`; production packaging now fails with an actionable message unless a release keystore is configured.
- Strengthened the TDLib ABI verification task to require a non-empty `arm64-v8a/libtdjni.so` for modern phone/TV release targets.
- Added a persistent database-key mode marker in SharedPreferences and `noBackupFilesDir`; conflicting markers or a missing key for an encrypted database now fail closed rather than guessing.
- Changed legacy plaintext API credential migration to return the value only after the encrypted replacement is durably committed; failed migration removes the plaintext entry on a best-effort basis and requires re-entry.
- Made the local loopback HTTP server restart-safe after `stop()`, with each accept loop bound to its own socket and executor so an old listener cannot attach to a restarted server.
- Made legacy plaintext API credential migration fail closed when secure persistence fails; the app no longer returns the clear-text value to the login flow and attempts to remove the legacy preference.
- Added real-route Android instrumentation smoke tests that launch MainActivity without the debug preview extra, while retaining a separate dashboard-preview visual check.
- Changed playback queues to retain media metadata only. Stream capability URLs are now issued when an item actually becomes active instead of pre-issuing URLs for every item in the queue.
- Removed the stream registry's behavior of evicting still-valid grants when the table reaches capacity. Expired grants are purged; if the hard ceiling is reached, new grants fail closed rather than randomly breaking existing playback.
- Added local-URL capability revocation helpers and wired the player to revoke local stream/subtitle capabilities when playback is exited or a local URL is replaced. Backgrounding the app does not revoke a URL that may be needed after resume.
- Added unit coverage for queue URL laziness and capability revocation, plus a single-command offline test runner.
- Expanded pure Kotlin policy checks for encrypted database-key loss and conflicting redundant mode markers.
- Extended the offline source-sanity runner to check credential migration and local-server restart protections, alongside source-level signing, ABI, playback URL and URL-validation invariants.
- Debounced focus-triggered stream warm-up to reduce unnecessary data use during rapid TV navigation.

### Verification note (2026-10-21)

- Standalone Kotlin range/token/database-key policy checks: 27/27 passed.
- Selected Android XML, release-signing/ABI source invariants and workflow YAML parse passed.
- Full Gradle build/lint/JUnit/instrumentation remain blocked here because Gradle 9.4.1 cannot be downloaded (`services.gradle.org` DNS failure) and no Android SDK/emulator is installed. These changes have not yet been confirmed by the Android compiler or device tests.



## Implementation checklist follow-up (2026-10-21)

- Fixed the Gradle Kotlin DSL scope of production signing configuration so the release-task guard can reference `hasReleaseSigning` from the project level.
- Expanded the offline static suite to guard the signing scope, ensure production build types do not use the debug certificate, and check privacy-mode masking on Home, media cards, Search, and Browse.
- Upgraded the existing “Safe mode” into an actual browsing privacy mode: poster images are not requested, duration and file-size metadata are hidden, source labels are generalized on the Home hero, Search history and source/query details are reduced, and settings now describes the behavior accurately.
- The privacy-mode change is source-level checked only; the Compose app has not been compiled because Gradle 9.4.1 remains unavailable in this environment.
- Moved the release-signing inputs to project scope after review found `hasReleaseSigning` was referenced by Gradle task guards outside the block where it was declared.
- Made subtitle URL creation fail closed when the loopback streaming server does not bind a valid port.
- Expanded privacy mode across Home, adaptive media cards, Favorites and Search: artwork is not requested while enabled, duration/file size are hidden, Home source labeling is generalized, recent search queries are hidden, and search empty-state text no longer echoes the query.
- Expanded offline static assertions for the above privacy behavior and release-signing configuration scope.

## Implementation checklist follow-up — UI privacy and subtitle reliability (2026-10-21)

- Upgraded browsing privacy mode so poster artwork is not requested when enabled; duration and file size are hidden from cards; the Home hero hides artwork and source-specific text; Search hides recent queries and avoids echoing the query in empty-result text.
- Changed the visible settings label from “Safe mode” to “Privacy mode” to describe the broader behavior.
- Made local subtitle URL generation fail closed when the loopback server cannot bind a valid port rather than returning a URL that points at port 0.
- Added reproducible source assertions for privacy behavior and subtitle-server readiness.
- Re-ran the combined offline test suite: 14/14 Python tests and 27/27 pure Kotlin checks passed; selected XML/source sanity checks, workflow YAML parsing, and 12 embedded Bash syntax checks passed.
- The Android Gradle tasks were retried, but the wrapper still fails before Gradle startup because `services.gradle.org` cannot resolve in this environment. Changed Compose files were checked for Kotlin parser/syntax errors but not fully type-checked without Android/Compose dependencies.

## Source picker and video-library UI revision (2026-10-22)

- Added horizontal source tabs to Home, Library, and compact layouts. Saved Messages remains the first, dedicated source.
- Added a searchable All Chats dialog with filters for channels, groups, private/secret chats, and archived conversations.
- Expanded the picker from only selected video-bearing channels/groups to all main-list and archived Telegram chat types. Media grids still use the existing video-only filter.
- Loads TDLib chat lists in batches until the list is exhausted, merges main and archived sources without duplicates, and fetches chat details in bounded batches to avoid a large burst of requests.
- Improved the Library visual hierarchy by moving source selection to a horizontal source-tab row, leaving more width for the poster grid.
- Added the `.fmp4` and `.ogg` filename extensions to the existing progressive-video format recognition; the player/source architecture was otherwise left unchanged.
- Playback, authentication, and cache business logic were intentionally not redesigned in this UI-focused revision.

Verification note: source-level checks and archive integrity can be run here, but the complete Android/Compose Gradle build must still be executed in Android Studio with the project's configured Android SDK and Gradle distribution.

## UI theme and visual polish

- Added persistent Midnight, Aurora, Ocean, and Sunset color presets, with immediate switching from Settings.
- Included the selected theme in settings JSON export/import and diagnostics.
- Unified the app gradient, focus surfaces, source tabs, All Chats dialog, poster cards, auth inputs, Favorites screen, and player track picker around the active Material 3 color scheme.
- Refined poster artwork with larger corner radii, theme-aware placeholders, duration/badge pills, and a lower artwork scrim; reduced TV focus scale to avoid excessive card overlap.
- Added source-level theme regression checks. Full Android compilation/device verification remains pending due to unavailable Gradle distribution download in this environment.
