# Repository audit and current release evidence

Audit date: 2026-10-28

## Source of truth

- Audited base commit: `b7fce6ba9b0c0f680658622a233360ae88af468e` (`main`, subject `updated`).
- Working branch: `release/enterprise-hardening`.
- The worktree contains the changes listed below and is not committed. Results marked **worktree** apply to those exact uncommitted files, not to the base SHA or a future release commit.
- Pre-existing untracked path `build-tools/tdlib/src/` was preserved and not edited.
- Local verification ran on Windows with JDK 21 and Gradle 9.4.1. Android SDK processing warned that the installed tools encountered SDK XML v4 while supporting through v3. No Android devices were attached.

## Reconciled project configuration

| Area | Checked-in implementation | Qualification |
|---|---|---|
| Android | compile SDK 37, target SDK 37, min SDK 26; application ID `com.vaydelux.bousygram`, version `1.0` / code `10` | API 37 is Android 17. `ACCESS_LOCAL_NETWORK` is declared and Cast UI requests it before receiver discovery. Runtime grant, denial, revocation, and reconnection have not been tested on API 37 hardware. |
| Build | AGP 9.1.1, Kotlin 2.3.21, Gradle wrapper 9.4.1 | Local JDK 21 used for this worktree run. |
| Telegram | JNI `org.drinkless.tdlib.Client.create`; bundled `libtdjni.so` for ARM32 and ARM64 | No active TDJSON transport. ABI task checks ELF class/machine, load alignment, JNI markers, and metadata commit consistency; it does not establish binary provenance or runtime correctness. |
| Streaming | Media3/ExoPlayer uses Ktor/Netty `TelegramProxyManager`; MPV and local handoff use `LocalStreamingServer`; both use the TDLib chunk scheduler | End-to-end device playback is unverified. Byte-range tests are JVM tests. |
| Cache | Dynamic eviction target is `min(2 GiB, 15% of available internal app-data space, managed ceiling when present)` | It is an eviction target and not a hard cap on TDLib writes. There is no minimum quota floor. Pinned and active media must remain protected by cleanup policy. |
| Cast | LAN HTTP carries private media in the current implementation. `PRIVATE_MEDIA_CAST_ENABLED` is false for release/hardened-release and true for debug. | Do not enable production private-media Cast without receiver-trusted TLS and physical receiver validation. Bearer URLs authorize access but do not encrypt media. |
| Diagnostics | `EngineLabActivity` is exported in the manifest but uses a build placeholder; release variants set it disabled, debug sets it enabled | Debug and merged production manifests must be inspected for each release build. |
| CI | `.github/workflows/android-enterprise.yml` contains PR/main/release checks, optional signed hardened build, and optional Firebase Test Lab jobs | No CI run URL or server-side run report is available in this checkout. A configured job is not evidence that it ran. |

## Corrected issues in this worktree

1. Replaced all three `android-actions/setup-android@v3` references with upstream `v4`. The v4 release notes state that it fixes the removed `tools` package failure. The required Gradle workflow now also compiles debug Kotlin, assembles the instrumentation APK, runs the private-media Cast release gate, and uploads JUnit/lint reports even when a prior task fails.
2. Removed recursive cache preference fallback. Unmanaged preferences return the 2 GiB ceiling; `CacheManager` calculates the quota once from available bytes and the managed ceiling. The space sample now uses app internal data storage, the filesystem containing `filesDir/tdlib_files`. Removed the 256 MiB minimum behavior because it could exceed the storage budget. This remains a cleanup target, not a hard limit on TDLib writes.
3. Removed a long startup wait from speculative tail prefetch. `openPlayback` now schedules the tail request without waiting up to 60 seconds; an actual later range read retains the normal wait path. The previous scheduler tests simulated tail availability only after joining `openPlayback`, causing two 75-second test cases. Tests now model the initial two-megabyte head being available and verify tail range behavior separately.
4. Replaced stale cache tests that asserted the legacy 20%/5 GiB constants with assertions against the active 15%/2 GiB quota policy.
5. Added `verifyPrivateMediaCastReleaseGate`, which checks the generated release flags, merged release/hardened manifests, and fail-closed checks in the Cast session controller, LAN server, service, and player UI. Release APK packaging depends on this task.
6. Removed bearer-bearing local stream URLs and request URLs from logs, removed Telegram IDs from structured playback diagnostics, and stopped including raw exception messages in diagnostics. External Crashlytics reflection now requires `CRASH_REPORTING_ENABLED`; the flag is false for all build types. `verifySensitiveLogRedaction` checks these source boundaries and is a release packaging dependency.
7. Marked `RELEASE_STATUS.md` historical claims as superseded and updated `SYSTEM_INFORMATION.md` to identify the audited base and worktree evidence boundary.
8. Strengthened `verifyTdlibAbiCoverage` to validate ELF class/endian/machine, program-header integrity, expected `PT_LOAD` alignment, JNI markers, and consistency between the resolved TDLib commit and build metadata. This remains a structural check, not reproducible provenance verification.

## MASVS-informed security observations

- **Storage:** `allowBackup=false` and both backup/transfer rules exclude app data. New TDLib database keys are AES-GCM wrapped by Android Keystore; a mode marker rejects inconsistent encrypted-key state. Existing databases without a marker preserve the legacy empty-key mode to avoid silently corrupting existing Telegram data. Hardware-backed Keystore storage is not guaranteed.
- **Network and sessions:** General cleartext is denied; only `127.0.0.1` and `localhost` have a network-security cleartext exception. Cast still uses HTTP on the LAN, so the production feature flag must remain false. Stream bearer capabilities are authorization, not confidentiality.
- **Components and links:** MainActivity is exported for launcher and deep-link handling. Cast handoff validation checks Telegram authorization and the current account's message/file before opening local playback, but the custom URI scheme can be claimed by another installed app. The diagnostic activity remains `exported=true` in the merged manifest but is `enabled=false` for both production variants. The Cast foreground service is not exported.
- **Telemetry and logs:** External crash reporting is currently disabled in all variants. Structured playback telemetry still records technical playback state locally; identifier fields were removed from its formatted records. Review any future telemetry enablement as a separate consent and data-minimization change.

## Native binary inventory

| ABI | File size | SHA-256 | ELF header | PT_LOAD alignment |
|---|---:|---|---|---|
| `arm64-v8a` | 34,033,264 bytes | `205BE77AB847ADF1CABEADBB2B004D7153C15839AD85E4E5E9707D1BD1679C19` | ELF64, AArch64 (machine 183) | 16,384 bytes |
| `armeabi-v7a` | 26,069,872 bytes | `2C8D3A9D2F049D3EEAF028271954FD89F9FA315426BE52BC4D1FB44E91969D7B` | ELF32, ARM (machine 40) | 4,096 bytes |

The source metadata in `tools/tdlib/BUILD_METADATA.txt` and `build-tools/tdlib/RESOLVED_COMMIT.txt` records TDLib commit `4c6377aa7386c39217878a9a56d581922ad37ca4`, NDK r27d / 27.3.13750724, Android platform 23, and OpenSSL 3.3.2. A local structural ELF parser confirmed `JNI_OnLoad`, the five `Client.java` native method names in the library strings, and `DT_NEEDED` entries `libc.so`, `libdl.so`, `liblog.so`, `libm.so`, and `libz.so` for both ABIs. This is not a full symbol/version audit. The metadata says the listed toolchain was used to build, but the checked-in ABI task does not bind the binaries to that source/toolchain, and this work did not reproduce the build. The local referenced NDK path is not available for an independent rebuild. Runtime compatibility remains unverified. The ARM64 load segments meet 16 KiB alignment; the ARM32 binary has 4 KiB alignment and is intended for 4 KiB-page 32-bit devices. Do not claim 16 KiB page qualification for ARM32.

## Worktree verification evidence

Commands run after changes:

```text
.\gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest --tests com.vaydelux.bousygram.core.cache.CacheManagerTest --rerun-tasks
.\gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest --rerun-tasks
.\gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest --tests com.vaydelux.bousygram.core.tdlib.server.TelegramChunkSchedulerTest
.\gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest
.\gradlew.bat --no-daemon --console=plain :app:verifyTdlibAbiCoverage :app:verifyTvHardeningContract :app:compileDebugKotlin :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
.\gradlew.bat --no-daemon --console=plain :app:processReleaseMainManifest :app:processHardenedReleaseMainManifest
.\gradlew.bat --no-daemon --console=plain :app:generateReleaseBuildConfig :app:generateHardenedReleaseBuildConfig
.\gradlew.bat --no-daemon --console=plain :app:verifyTdlibAbiCoverage :app:verifyTvHardeningContract :app:verifyPrivateMediaCastReleaseGate :app:verifySensitiveLogRedaction :app:compileDebugKotlin :app:lintDebug :app:testDebugUnitTest
.\gradlew.bat --no-daemon --console=plain :app:assembleDebug :app:assembleDebugAndroidTest
```

| Run | Result |
|---|---|
| Focused cache tests | Exit 0; tests executed successfully. |
| Full suite before tail-wait correction | Exit 0; 259 tests passed, but scheduler tail tests took 153.9 seconds together. Superseded as final evidence. |
| Focused scheduler suite after correction | Exit 0; 15 tests, 0 failures/errors/skips; test class time 31.8 seconds before fixture adjustment and then completed in 31 seconds with updated fixture. |
| Full suite after scheduler correction | Exit 0; JUnit XML: 259 tests, 0 failures, 0 errors, 0 skipped. The previous 153.9-second scheduler delay is removed; rerun current test duration is recorded in XML. |
| Final full suite after privacy changes | Exit 0; JUnit XML: 262 tests, 0 failures, 0 errors, 0 skipped across 43 test classes; total recorded test execution time 10.5 seconds. |
| Strengthened ABI coverage | Passed; ARM64 ELF machine 183 with 16 KiB load alignment and ARM32 machine 40 with 4 KiB alignment; JNI markers and metadata commit match validated. This is not provenance verification. |
| TV hardening contract | Passed. |
| Debug Kotlin compilation | Passed. |
| Debug lint | Passed. SDK XML v4 compatibility warning noted above. |
| Debug APK | Assembled. `app-debug.apk` contains both `lib/arm64-v8a/libtdjni.so` and `lib/armeabi-v7a/libtdjni.so`. It is debug-signed and not a production artifact. |
| Debug APK alignment/signature | `zipalign -c -P 16 4 app-debug.apk` exit 0; `apksigner verify --verbose` exit 0 (v2 signature, one debug signer). This is not a production signing check. |
| Instrumentation APK | `assembleDebugAndroidTest` completed/was up-to-date. No device was connected, so no instrumentation test was executed. |
| Release gate | Passed. Release and hardened-release generated BuildConfig values are false for private Cast, and merged manifests set `EngineLabActivity` to `enabled=false`. |
| Sensitive-log gate | Passed in the final verification run. Crash reporting remains disabled in debug/release/hardened-release generated configs. |
| Final debug artifacts | `assembleDebug` and `assembleDebugAndroidTest` exit 0. Debug app APK is 193,701,638 bytes, SHA-256 `04F3F3BB9471AEDFD4CE3E23664ABB732ACE3850DC2CEB78E555A9831B67CCD2`; it contains ARM32 and ARM64 TDLib libraries. `zipalign -c -P 16 4` and `apksigner verify --verbose` both exit 0. Signature is debug-only and not release evidence. AndroidTest APK is 628,416 bytes; it was not executed on a device. |
| `git diff --check` | Passed after the final source and documentation edits. |

Historical counts in prior system/release documents (154, 190, 212, 227, 257) are not reused as current evidence. The checked-in release status previously claimed artifacts in `dist/`; `dist/` is absent in this checkout. No signed release APKs were built or verified in this task.

## Security and release blockers

- Private Telegram media Cast uses cleartext LAN HTTP. Production variants keep the feature disabled. TLS certificate/hostname compatibility has not been qualified against physical Chromecast hardware.
- The permission flow is present for Cast discovery, but Android 17 grant/deny/revoke/reconnect outcomes remain device-unverified. The application has other local socket paths; confirm their runtime behavior against Android 17's local-network rules as part of qualification.
- No Android/TV/Chromecast hardware was connected. No Firebase Test Lab credentials/run evidence was available.
- Native source/toolchain metadata is present and binary structure was inspected, but the two binaries have not been reproducibly rebuilt or validated on Android devices. ABI checks alone are insufficient for release.
- No production signing credential was used. No release APK, matching certificate, checksum manifest, SBOM, attestation, or release publication was produced.
- Dependency locking/verification metadata and vulnerability scanning are not configured as complete release gates in this worktree. GitHub actions outside the updated SDK setup are not all pinned to immutable SHAs.
- The cache quota is not a physical storage cap. TDLib can write media outside the app's tracked cleanup model, so low-storage behavior, active/pinned protection, restart cleanup, and bounded writes require further qualification.

## Decision

**BLOCKED for release.** The local worktree test/lint/build checks pass, but mandatory device, Cast TLS, Android 17 permission, native provenance, signing, and release artifact verification remain open. No public release is authorized or created. A release workflow should be added only after these gates are implemented so that missing qualification evidence fails closed.
