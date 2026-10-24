# Release status: mock-tested source candidate, not a production APK

This package includes the ongoing implementation checklist pass: Phase 1 release-confidence changes, security/key-recovery and playback-token lifecycle improvements, playback/warm-up corrections, and a browsing privacy-mode upgrade. It is still a source candidate: no signed APK is included, and this environment has not successfully configured or compiled the Android project.

## Checks run in the available environment

- Fast-Link Python unit/mock suite: 14/14 passed.
- Standalone Kotlin range, token, URL-validation, and database-key policy checks: 27/27 passed.
- Python compilation passed.
- Selected Android XML resources parsed successfully.
- CI workflow YAML parsed successfully.
- Source-level checks verified that production build types no longer explicitly fall back to debug signing, signing configuration is declared at project scope, the release task guard exists, the ARM64 native ABI guard exists, queue URL creation is lazy, privacy mode suppresses poster artwork and sensitive card details, and subtitle URL creation fails closed when the loopback server does not bind.
- ZIP integrity is checked when packaging the delivery artifact.

## Blocking release checks

The Gradle wrapper stopped before Gradle startup because `services.gradle.org` could not be resolved (`UnknownHostException`). No Android SDK or emulator is installed in this environment, so the following remain unverified:

1. `:app:verifyTdlibAbiCoverage`, `:app:lintDebug`, `:app:testDebugUnitTest`, and `:app:assembleDebug` under the project's actual Gradle/Android Gradle Plugin versions.
2. Compilation of the changed Compose player and instrumentation tests.
3. Real Telegram authentication, browsing, stream startup, seeking, subtitles, retry/recovery, queue behavior, and lifecycle resume on a supported device.
4. Offline cache protection under active download/playback conditions.
5. Hardened release signing using the intended certificate and a production configuration of secrets.
6. Fast-Link proxy deployment and access control against a controlled test bot/account.
7. Verify the new privacy-mode Compose behavior in actual screenshots and accessibility inspection.
8. Confirm `app/build.gradle.kts` configures successfully with the project toolchain after moving the signing gate inputs to project scope.

## Required before publishing

1. Use Android Studio on a network-connected development machine with Android SDK API 37, then sync Gradle and run the commands above.
2. Fix every compile/lint/test failure and rerun the suite.
3. Install the debug build on an ARM64 phone and Android TV device; complete the end-to-end test matrix in `MOCK_TEST_REPORT.md`.
4. Configure a real production signing keystore and expected SHA-256 fingerprint; run `:app:assembleRelease` and verify the APK certificate.
5. Publish only the verified, signed artifact from the release pipeline.

Do not treat passing mock tests as proof that the full Android app compiles or is production-ready.


## Implementation checklist progress — 2026-10-21

- Release signing fails closed rather than using debug signing for production variants.
- ARM64 TDLib coverage is a required release gate.
- Added a redundant database-key mode marker in protected app-internal storage and explicit fail-closed policy for missing/conflicting encrypted key state.
- Legacy plaintext API credential migration no longer returns a plaintext value if encryption/persistence fails; it attempts to remove the clear-text entry and prompts re-entry.
- Made loopback stream server stop/start lifecycle restart-safe.
- Queue stream URL generation is lazy, local stream capabilities are explicitly revoked at end of use, Fast-Link URLs are HTTPS-validated, and focus-triggered warm-up is debounced.
- Offline checks: Fast-Link mock tests 14/14, standalone Kotlin helper/policy tests 27/27, workflow YAML and 12 shell snippets parsed.

Not yet verified: full Android/Compose compilation, Android unit tests, instrumented tests, device login/playback/TV navigation, Keystore operation on supported devices, and signed production APK. This remains a source candidate pending Android Studio/CI verification, not a finished release.

## Latest implementation pass (2026-10-21)

Additional source work beyond the original v3 package: production-signing input scope fix, fail-closed subtitle URL generation, broader browsing privacy mode, and static assertions for these behaviors. The UI changes pass syntax-parser screening and source assertions, but they have not been type-checked by the Android Gradle build.

Latest attempted Gradle command: `./gradlew --no-daemon :app:verifyTdlibAbiCoverage :app:lintDebug :app:testDebugUnitTest :app:assembleDebug`. It failed before Gradle startup because DNS lookup for `services.gradle.org` returned `UnknownHostException`. This is an environment blocker, not a successful or failed compile result.
