# Media-loading optimization — test report

**Revision date:** 2026-10-22  
**Result:** source/mock-tested candidate; Android app build and device playback remain unverified.

## Checks passed

- Channel/source picker revision suite: **6/6 passed**.
- Media-loading performance contract suite: **10/10 passed**.
- Python syntax compilation for test-runner modules passed.
- Project static checks passed for XML parsing, release-signing guards, ABI checks, stream URL validation, metadata-first progressive search, low-priority thumbnail work, no focus-triggered video-byte warmup, key recovery, credential migration, local-server lifecycle, privacy masking, and player stream preparation.
- GitHub workflow YAML parsing and syntax checks for all 6 embedded Bash scripts passed.
- The pure Kotlin `MediaSearchPlan.kt` helper was compiled independently and its 3 smoke assertions passed.
- ZIP archive integrity and original source/resource path retention were checked after packaging.

## Kotlin and Android build boundary

The full standalone Kotlin helper suite was skipped in the combined run because the Kotlin compiler was unusually slow in this restricted container. Only `MediaSearchPlan.kt` was compiled independently. The Android Gradle build was attempted with `:app:assembleDebug` but failed **before project compilation** while the wrapper tried to download Gradle 9.4.1. The error was `java.net.UnknownHostException: services.gradle.org`. No Android SDK/emulator is installed in this environment.

## Not yet verified

- Compose/Kotlin project compilation and Android lint.
- Gradle JUnit and instrumentation tests.
- Real TDLib query timings on the user's Telegram account.
- Real thumbnail rendering, scroll pagination and absence of duplicate/omitted items on-device.
- Actual ExoPlayer playback, seeking, subtitles, background/resume, and Android TV D-pad behavior.

## Next verification on the user's Android development environment

Run:

```powershell
.\gradlew.bat :app:verifyTdlibAbiCoverage
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

Then install the debug build, verify Saved Messages and several groups/channels, scroll until new pages load, switch sources quickly, and confirm playback still goes through the existing ExoPlayer stream-preparation path. Performance should be evaluated using actual first-item render time, time-to-thumbnail, and player startup time; mock tests cannot provide those timings.
