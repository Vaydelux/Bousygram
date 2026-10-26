# Bousygram upgrade notes (2026-10-20)

This is an improved source revision, not a device-validated release APK.

## Main changes

- Reworked the TV Home into a cinematic hero dashboard, separated Telegram source selection into Library, added a cross-source Favorites destination, and improved the subtitle/audio picker for TV remote navigation.
- Corrected stale-source refresh behavior, cross-source last-played lookup, and adaptive poster card widths.
- Added per-resource short-lived capability tokens to Android loopback stream/subtitle/playlist URLs and strict single-range parsing for Android streaming endpoints.
- Added Keystore-backed encryption for saved Telegram API credentials and a persistent Keystore-wrapped key for newly created TDLib databases. Existing databases without a recorded key intentionally remain on the legacy empty-key path so upgrades do not silently make existing local databases unreadable.
- Replaced raw filesystem deletion of TDLib-owned files with conservative tracked-ID eviction via TDLib and improved reporting when actual cache size remains over the limit.
- Updated CI to resolve versioned APK output names for Firebase Test Lab and verify the configured release certificate for hardened APKs on push builds.

## Mock tests and verification (2026-10-21)

Standalone Kotlin checks are documented in `tools/mock-tests/README.md`.

The Firebase Test Lab workflow uses repository variable `FIREBASE_TEST_LAB_ENABLED=true` plus Actions secrets `FIREBASE_SERVICE_ACCOUNT` and `FIREBASE_PROJECT_ID`. The optional job is restricted to pushes so fork pull requests do not fail for unavailable secrets.

## Android build and release

From Android Studio or a connected Android development machine, run:

```bash
./gradlew :app:verifyTdlibAbiCoverage
./gradlew :app:lintDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

For pushes to `main`, `master`, or `release/**`, the GitHub workflow requires these Actions secrets: `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`, and `RELEASE_CERT_SHA256`. The first must be a base64-encoded JKS/PKCS12 keystore; the last is the expected signing certificate's SHA-256 digest. Pull requests run the debug and unit-test workflow without requiring production signing secrets.

- The `HttpRangeParser` and Android stream-token helpers were compiled with `kotlinc` and exercised by focused checks.
- The CI workflow YAML and its multiline shell snippets pass local syntax checks.
- A full Android/Compose Gradle build, Android unit-test suite, TDLib integration, release signing, and physical Android TV playback were not run in this environment because the required Gradle distribution and Android SDK were unavailable. Validate these on a connected Android development machine before distributing an APK.

## Implementation checklist pass (2026-10-21)

This update starts the release-confidence phase and closes several playback-token lifecycle issues:

- Production `release` task uses R8 minification and requires release signing credentials (fail-closed). Debug builds remain available for local testing.
- `verifyTdlibAbiCoverage` fails if the packaged TDLib runtime is absent or if `arm64-v8a/libtdjni.so` is missing/empty.
- Instrumentation tests now include a launch path for the real app screen (without `ui_preview=true`) as well as a separate preview-only visual smoke test.
- Playback queues no longer mint an access URL/token for every queued video. A local capability URL is prepared when the item is selected; local playback capabilities are revoked when the active URL is replaced or the player exits.
- Stream token saturation no longer arbitrarily removes valid tokens that may still be in use. Expired grants are cleaned before new grants are issued; reaching the hard cap fails closed.
- Focus-triggered warm-up is debounced by 450 ms and cancelled when focus changes, avoiding rapid prefetch churn while navigating a TV poster grid.

Run all locally available, offline-safe tests from the repository root:

```bash
bash tools/mock-tests/run_all.sh
```

This runs the Python syntax checks, standalone Kotlin streaming-helper checks when `kotlinc` is available, and selected static project checks. It deliberately does not pretend to replace the Android Gradle build, lint, JUnit tests, instrumentation, or real-device validation.

### Additional release-confidence hardening (2026-10-21)

- Added a redundant TDLib database encryption-mode marker under Android's `noBackupFilesDir` as well as preferences. The marker is persisted before a key is handed to TDLib. If the encrypted key is missing or markers conflict, app startup fails safely and does not guess an empty key.
- Made plaintext API credential migration fail closed when encrypted persistence fails; the plaintext value is not returned to the auth flow and removal is attempted. Users may need to re-enter credentials if device secure storage is unavailable.
- Made the loopback streaming server restart-safe after stop/start and tied each accept loop to its own server socket/executor.
- Added pure Kotlin unit/mock checks for new, legacy, encrypted-key-loss, and conflicting-marker policy decisions.

Current locally verified results: standalone Kotlin streaming/URL/database-key helper suite 27/27; selected resource XML, release-signing/ABI invariants, workflow YAML and 12 embedded Bash snippets passed. Full Android build/instrumentation remain blocked by unavailable Gradle distribution and SDK in this environment.
