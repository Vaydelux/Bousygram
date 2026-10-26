# Bousygram implementation checklist

Updated: 2026-10-27

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
- [x] Verify the release signing gate with a configured production keystore and all three resulting hardened APKs.

## Phase 2 — Security and data integrity

- [x] Protect loopback stream URLs with scoped high-entropy capability tokens and explicit revocation.
- [x] Stop evicting valid tokens arbitrarily when the registry reaches capacity.
- [x] Record TDLib database-key mode in preferences and a separate no-backup marker; fail closed for a missing encrypted key or conflicting markers.
- [x] Make plaintext API credential migration fail closed if encrypted persistence fails.
- [x] Harden browse privacy mode so it masks poster art, durations, source details and file-size metadata on Home/library/favorites/search cards; hide recent search history and source/query details in relevant search states.
- [~] Verify Android Keystore and encrypted credential migration on supported Android versions, including upgrade and key-loss recovery.
- [ ] Complete real-device review of all stream endpoints and exported/logging paths.

## Phase 3 — Playback and cache reliability

- [x] Generate queue stream URLs lazily rather than minting capabilities for inactive queue entries.
- [x] Revoke local stream/subtitle capabilities when they are no longer needed.
- [x] Debounce focus-triggered warm-up during rapid TV navigation.
- [x] Make the loopback server stop/start lifecycle restart-safe at source level.
- [x] Record cache hits, cache misses, reused bytes, seek latency, dropped frames, and memory pressure in local playback telemetry.
- [x] Change hidden D-pad Left/Right to preview-only seeking; OK commits once and Back cancels.
- [~] Validate local stream startup, seeking, retries, lifecycle pause/resume, queue switching, subtitles and audio tracks on devices.
- [~] Verify cache eviction behavior while pinned downloads and active playback are running.

## Phase 4 — UI and TV navigation

- [x] Add the cinematic Home, dedicated Library/source selection, cross-source Favorites, and redesigned playback/track-selection surfaces in earlier source revisions.
- [x] Add separate source paths for real-app and dashboard-preview instrumentation tests.
- [x] Make privacy mode suppress poster loads, duration/file-size metadata, Home hero artwork/source details, and recent search-history chips.
- [x] Remove PiP declaration, automatic entry, lifecycle exceptions, and player action.
- [x] Present media directly in the fixed library grid instead of inferred series cards; retain old metadata only for data compatibility.
- [x] Restore library viewport and focused media independently per Telegram source/topic when returning from playback.
- [ ] Run UI instrumentation on an ARM64 phone and Android TV device.
- [ ] Verify focus visibility, Back behavior, focus restoration, loading/error/empty states, accessibility and adaptive layouts with a real remote.

## Phase 5 — End-to-end release validation

- [x] Run pure Kotlin range/token/URL/database-key policy tests.
- [ ] Run controlled Telegram-account integration tests and real CDN playback.
- [ ] Complete crash-free long-session, background/resume and large-library tests.
- [x] Produce and verify signed hardened release APKs for `armeabi-v7a`, `arm64-v8a`, and universal packaging.

## Phase 6 — Product expansion

- [ ] Only after release validation: expand photo/audio/document browsing, richer offline management, and further library/search improvements.

## Current local verification

- Workflow YAML parses successfully; protected release and Firebase jobs are gated by secret availability.
- Full Gradle verification passed: `:app:verifyTdlibAbiCoverage`, `:app:verifyTvHardeningContract`, 227/227 debug unit tests, `:app:lintDebug`, and `:app:assembleDebug`.
- `:app:assembleHardenedRelease` passed with R8/resource shrinking and configuration-cache storage. All three generated APKs passed `apksigner` v2 verification with the same certificate digest.
- Physical-device playback, controlled Telegram-account integration, and long-session testing remain pending.
