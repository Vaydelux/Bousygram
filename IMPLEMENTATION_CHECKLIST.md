# Bousygram implementation checklist

Updated: 2026-10-21

Legend: `[x]` implemented and checked within the stated scope; `[~]` source work is present but requires Android/device validation; `[ ]` not completed.

## Phase 1 — Release confidence

- [x] Remove debug-signing fallback for production release variants.
- [x] Require a non-empty ARM64 TDLib JNI library in the ABI verification task.
- [x] Resolve release APK paths and verify the configured certificate in CI.
- [x] Add reproducible offline Python/Kotlin test runner, XML parsing, workflow YAML parsing, and embedded Bash syntax checks.
- [x] Add real-app instrumentation test source separate from dashboard preview tests.
- [x] Move signing configuration inputs to project scope so task-level signing gates can reference them.
- [x] Add static checks for the signing-configuration scope and privacy-mode behavior.
- [x] Move signing properties to project scope after source review found task guards reference them outside `android {}`.
- [x] Run Gradle verification, Android lint, JUnit, and `assembleDebug` in a connected Android SDK environment.
- [ ] Verify the release signing gate with an actual production keystore and resulting APK.

## Phase 2 — Security and data integrity

- [x] Validate Fast-Link bot URLs as HTTPS URLs without credentials, unexpected ports, fragments, or malformed URI structure.
- [x] Protect loopback stream URLs with scoped high-entropy capability tokens and explicit revocation.
- [x] Stop evicting valid tokens arbitrarily when the registry reaches capacity.
- [x] Record TDLib database-key mode in preferences and a separate no-backup marker; fail closed for a missing encrypted key or conflicting markers.
- [x] Make plaintext API credential migration fail closed if encrypted persistence fails.
- [x] Harden browse privacy mode so it masks poster art, durations, source details and file-size metadata on Home/library/favorites/search cards; hide recent search history and source/query details in relevant search states.
- [~] Verify Android Keystore and encrypted credential migration on supported Android versions, including upgrade and key-loss recovery.
- [ ] Complete real-device review of all stream endpoints and exported/logging paths.

## Phase 3 — Playback and cache reliability

- [x] Fix asynchronous Fast-Link preparation to wait for the bot response.
- [x] Generate queue stream URLs lazily rather than minting capabilities for inactive queue entries.
- [x] Revoke local stream/subtitle capabilities when they are no longer needed.
- [x] Debounce focus-triggered warm-up during rapid TV navigation.
- [x] Make the loopback server stop/start lifecycle restart-safe at source level.
- [~] Validate local/Fast-Link startup, seeking, retries, lifecycle pause/resume, queue switching, subtitles and audio tracks on devices.
- [~] Verify cache eviction behavior while pinned downloads and active playback are running.

## Phase 4 — UI and TV navigation

- [x] Add the cinematic Home, dedicated Library/source selection, cross-source Favorites, and redesigned playback/track-selection surfaces in earlier source revisions.
- [x] Add separate source paths for real-app and dashboard-preview instrumentation tests.
- [x] Make privacy mode suppress poster loads, duration/file-size metadata, Home hero artwork/source details, and recent search-history chips.
- [ ] Run UI instrumentation on an ARM64 phone and Android TV device.
- [ ] Verify focus visibility, Back behavior, focus restoration, loading/error/empty states, accessibility and adaptive layouts with a real remote.

## Phase 5 — End-to-end release validation

- [x] Mock Fast-Link routes with fake upstream responses.
- [x] Run pure Kotlin range/token/URL/database-key policy tests.
- [ ] Run controlled Telegram-account integration tests and real CDN playback.
- [ ] Complete crash-free long-session, background/resume and large-library tests.
- [ ] Produce and verify a signed hardened release APK.

## Phase 6 — Product expansion

- [ ] Only after release validation: expand photo/audio/document browsing, richer offline management, and further library/search improvements.

## Current local verification

- Fast-Link Python mock suite: 14/14 passed.
- Standalone Kotlin range/token/URL/database-key policy suite: 27/27 passed.
- Selected Android XML parsing and source invariants passed, including the signing-gate scope, subtitle URL fail-closed check, and browsing privacy-mode checks.
- Workflow YAML and all 12 embedded Bash snippets passed syntax validation.
- Full Gradle build verified: `:app:verifyTdlibAbiCoverage` passed (ARM64-v8a + armeabi-v7a), `:app:testDebugUnitTest` passed (35/35 tests), `:app:lintDebug` passed, and `:app:assembleDebug` produced `Bousygram-v1.0-debug.apk` (62.29 MB). Physical device validation and production keystore signing remain pending.
