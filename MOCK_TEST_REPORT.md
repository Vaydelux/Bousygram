# Bousygram mock-test report — 2026-10-21

## Result summary

| Check | Result | Scope |
|---|---|---|
| Kotlin helper/policy smoke tests | PASS — 27/27 | `HttpRangeParser`, `StreamAccessRegistry`, URL validation, and pure database-key policy compiled with `kotlinc` and exercised without Android runtime |
| Workflow YAML parse | PASS | `.github/workflows/android-enterprise.yml` parsed locally |
| Embedded Bash syntax | PASS — 12 blocks | Every `run:` shell block in the workflow checked with `bash -n` |
| Full Android Gradle tests/build | BLOCKED | Gradle wrapper failed before Gradle startup because `services.gradle.org` could not be resolved; no Android SDK is installed in this environment |
| UI instrumentation / real Telegram / physical device | NOT RUN | Requires a buildable app, Android emulator/device, and Telegram credentials/network |
| Signed release APK | NOT PRODUCED | Cannot safely produce a release APK without a successful build and proper release signing credentials |

## Reproduce mock checks

For the Android pure-helper checks, use the commands in `tools/mock-tests/README.md` if a standalone `kotlinc` is installed.

## What the mock tests prove — and do not prove

The passed mock checks validate selected range-parsing and token-scope behaviors with fake upstream responses. They do not prove the Android UI launches, Compose compiles, TDLib authenticates, actual Telegram media streams correctly, encrypted storage works on-device, or an APK is ready to distribute. The Android build and device-level checks remain release blockers.

## Implementation checklist follow-up (2026-10-21)

The v4 implementation pass also:

- Added a real-app MainActivity instrumentation launch path without the debug-only preview extra, while keeping the preview dashboard test separately labeled.
- Removed silent debug-signing fallbacks from production variants and added a release-task guard.
- Required a present, non-empty ARM64 TDLib JNI library in ABI verification.
- Avoided pre-issuing local stream capabilities for every queued media item; a URL is prepared when the current item actually plays.
- Avoided revoking still-valid grants to make room under token-table pressure and added explicit local-URL token revocation.
- Added a combined offline test script at `tools/mock-tests/run_all.sh`.

The combined runner passes the Python suite (14/14), Kotlin pure-helper suite (27/27), selected XML parsing, and source-level release/ABI/lazy-queue invariants. The full Gradle invocation was attempted again in this environment and failed before Gradle started because DNS could not resolve `services.gradle.org`. The Android application and instrumentation test sources have therefore not yet been compiler-verified.

The pure Kotlin suite also covers the database-key mode policy: new databases use encrypted-key mode, legacy unmarked databases retain their empty-key mode, stored v1 key preferences are recognized, and missing/conflicting encrypted-key state fails closed. Credential-migration hardening and restart-safe local-server lifecycle changes still need Android/TDLib integration verification.


## Current Phase 1–3 source pass (2026-10-21)

Additional source changes in the current candidate:

- Database encryption mode is recorded in both preferences and a separate `noBackupFilesDir` marker. Marker disagreement, missing keys for encrypted databases, or unknown mode values fail closed. The mode-policy decision logic is included in the standalone Kotlin tests.
- API credential migration no longer returns the legacy plaintext value if secure encryption or its preference commit fails; it makes a best-effort attempt to remove that clear-text preference entry and requires re-entry.
- The local loopback server now recreates its executor after a stop and binds each accept loop to the socket/executor it was started with.
- The combined offline runner now checks the new database-key/credential/server lifecycle invariants and parses every workflow Bash block.

Current offline results: Python 14/14; Kotlin pure helpers and policy 27/27; selected XML and source invariants pass; workflow YAML and 12 Bash blocks pass syntax checks. This does not validate Android-specific code compilation or actual Keystore/server behavior on-device.


## Additional Phase 1 and UI privacy follow-up (2026-10-21)

- Moved release-signing inputs and `hasReleaseSigning` to project scope in `app/build.gradle.kts`, because the release task guards are declared outside the `android {}` block. A full Gradle configuration run is still needed to validate against the project's actual Gradle and Android Gradle Plugin versions.
- Hardened the local subtitle URL path so it fails closed rather than issuing a URL containing port 0 when the loopback server did not bind successfully.
- Expanded the source-level runner to check that privacy mode avoids poster image requests and hides duration/file-size details, masks the Home hero's source/title information, and hides recent search history and selected source/query details in relevant states.
- The new UI checks are source-level assertions plus a Kotlin parser pass; the Compose files still need compilation and visual testing on Android.

## Additional checklist implementation — privacy and subtitle endpoint (2026-10-21)

- Made browsing privacy mode mask poster art and media details on Home/library/favorites/search cards; the Home hero artwork and source name are hidden; recent searches and search empty-result query echo are suppressed while privacy mode is enabled.
- Changed the Settings label to “Privacy mode.”
- Local subtitle URL generation now fails closed if the loopback server cannot bind to a valid port.
- Expanded source-level assertions for the above behavior. The changed Compose files had a parser-only `kotlinc` check with no Kotlin syntax diagnostics; because Android/Compose dependencies were unavailable, that command was expected to fail dependency resolution and does not establish successful compilation.
- Latest offline test results remain Python 14/14 and pure Kotlin 27/27; source/XML checks and workflow YAML plus 12 Bash syntax checks pass.
- Latest Gradle verification attempt remains blocked before Gradle startup: `UnknownHostException: services.gradle.org`. No debug or release APK has been produced.
