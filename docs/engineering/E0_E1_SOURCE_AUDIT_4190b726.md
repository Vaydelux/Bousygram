# E0/E1 source audit — 2026-10-28

## Audit identity and limits

- Base commit audited: `4190b726753f6ed4798d14767840f990b6c69caf` (`main`, commit message `update`).
- The checkout had one pre-existing untracked path, `build-tools/tdlib/src/`; it was left untouched.
- This report distinguishes that committed baseline from the current worktree edits. It does not treat prior commit docs, local build outputs, or GitHub Actions YAML as evidence that checks passed at this commit.
- Scope is E0 and E1. No TDLib transport migration, cache redesign, module split, or device qualification was performed.

## E0 — source-of-truth reconciliation

| Area | Checked-in implementation | Stale/contradictory documentation and correction |
|---|---|---|
| SDK/tooling | App uses compile SDK 37, target SDK 37, min SDK 26. Version catalog has AGP 9.1.1 and Kotlin 2.3.21; wrapper is Gradle 9.4.1. | `SYSTEM_INFORMATION.md` said API 37 was Android 16, AGP 9.2.1, and listed host paths/hardware from another machine. It now records API 37 as Android 17, current declared project values, and marks host-only values historical. |
| TDLib transport | `TdLibClient` calls Java `Client.create`, which dispatches to the bundled JNI binding. If native TDLib is unavailable, `TdLibFallbackEngine` returns `503 TDLIB_NATIVE_UNAVAILABLE` for data operations. | No active TDLib JSON transport was found in app code. The untracked TDLib source tree is not treated as provenance or as a transport migration. |
| Native binaries | Tracked `libtdjni.so` files exist for `arm64-v8a` and `armeabi-v7a`. SHA-256: arm64 `205BE77AB847ADF1CABEADBB2B004D7153C15839AD85E4E5E9707D1BD1679C19`; armeabi-v7a `2C8D3A9D2F049D3EEAF028271954FD89F9FA315426BE52BC4D1FB44E91969D7B`. | The verification task checks non-empty ABI files, not source revision, compiler/toolchain, reproducible build, or binary provenance. No checked-in attestation links these hashes to a source build. |
| Player routing | `PlayerScreen` runs `CapabilityProber`; the chosen engine is passed to `PlayerEngineFactory`. MPV is selected by a probe verdict or user preference; otherwise ExoPlayer is created. Runtime fallback can switch engines while preserving position. ExoPlayer uses the Ktor/Netty loopback proxy path; MPV/local handoff uses `LocalStreamingServer` (`ServerSocket`). | Docs describing one Ktor server or unconditional content-based MPV routing were incomplete. `SYSTEM_INFORMATION.md` now describes both loopback paths; `docs/ARCHITECTURE.md` cache claims were corrected. |
| Cache | Runtime target is intended as 15% of available bytes sampled on `cacheDir`, 256 MiB floor, 2 GiB max. `TelegramCachePruner` still publishes 5 GiB/20% legacy constants. The 1/2/5/10 GiB preference enum is deprecated and its setter is a no-op. TDLib data is under `filesDir/tdlib_files`; the target is tracked-file cleanup, not a physical write cap. | Old docs called this a 1–10 GiB enforced quota and described bounded cumulative caching. Those claims are removed or qualified. **Open defect:** without a managed cache override, `CacheManager.calculateDynamicQuota()` calls `EnterprisePreferences.cacheLimitBytes()`, which falls back to `TelegramCachePruner.calculateDynamicCacheLimit()` and re-enters the same quota calculation. The surrounding `runCatching` masks stack overflow by falling back to the maximum, but repeated recursion is wasteful. Fix/test this in the resource-reliability phase. |
| Test evidence | `.github/workflows/android-enterprise.yml` specifies ABI and TV hardening contracts, lint, unit tests, debug build, plus optional signed release and Firebase Test Lab jobs. | The October 24 system file says 154 tests, its quick reference says 190, and older reports say 212 and 257 without recorded commit IDs. There is no CI run artifact/log in this checkout to substantiate any count for this commit. These numbers are marked historical and are not reused as evidence. |
| Dependency/release controls | Gradle has fail-closed release signing; CI optionally verifies every hardened APK signature against a configured certificate SHA-256. Both ARM ABIs are required. | No Gradle dependency lockfiles or `gradle/verification-metadata.xml` were found. CI actions use version tags, not commit SHAs. No SBOM, dependency vulnerability scan, build attestation, or native provenance report is configured. These are E4 candidates, not implemented in this pass. |

## E1 — risk-based Android security review

This is a focused source review informed by MASVS areas, not a formal MASVS assessment or penetration test.

| Priority | Finding | Current mitigation/state |
|---|---|---|
| High | `EngineLabActivity` was exported in production and accepts a URL from an intent, allowing an external app to invoke diagnostic playback with arbitrary input. | Fixed in the worktree: Gradle manifest placeholder enables the exported diagnostic activity only in `debug`; base, `release`, and `hardenedRelease` disable it. Release merged-manifest checks are included in this work's verification command. |
| High | The Cast LAN media endpoint uses HTTP; its bearer URL can be observed/replayed on the LAN. A capability token is authorization, not encryption. | Release and hardened-release already set `PRIVATE_MEDIA_CAST_ENABLED=false`, with a runtime server guard. Debug Cast remains HTTP and is not suitable for private-media production use. No trusted-certificate bypass, global cleartext exception, or cloud relay was added. Physical receiver TLS compatibility remains unverified. |
| High | Bundled native transport integrity is not reproducibly established by the repository. | Current JNI binaries are tracked and their hashes are recorded above, but CI checks only presence/non-empty size and has no source-to-binary attestation. Native provenance remains a release blocker. |
| Medium | Existing TDLib databases that predate the encrypted-key markers continue with an empty database key to preserve access to user data. New databases use a random key wrapped by Android Keystore; inconsistent marker/key state fails closed. | Do not force-reset legacy data. Plan a user-safe re-encryption/migration with backup/recovery tests before changing this compatibility path. |
| Medium | Loopback stream grants are random, file-scoped and expire after six hours. Tokens are carried in URL query strings. They are revoked on track/session teardown, but not universally on app background; selected external-player handoff intentionally shares a URL when enterprise policy allows it. | Keep external-player access opt-in by managed policy. Avoid logging full URLs. Review/refactor token delivery and lifetime with handoff tests in a later security pass. |
| Medium | MainActivity is exported for launcher and custom-scheme deep links. Deep-link payloads are revalidated against the authorized Telegram session and the actual chat/message/file before playback. A custom scheme is not domain-verified and may be claimed by another app. | No Telegram session credential is accepted from the deep link. Keep payload validation and add verified-link/hijack tests before relying on deep links for sensitive actions. |
| Medium | New TDLib API credentials use AES-GCM with Android Keystore; hardware-backed storage is device-dependent. Bundled API ID/hash defaults are Telegram's standard public client values, not account session tokens. Session state resides in TDLib's private database. | Backup is disabled and data-extraction rules exclude app-private domains. Never report the API hash as an account password; continue treating user-specific TDLib session/database material as sensitive. |
| Medium | Playback telemetry is stored locally and diagnostics can be exported. `CrashReporter` reflectively invokes Firebase Crashlytics if that class is present; otherwise it writes to Android logs. Some logger call sites attach file/message IDs. | No direct Crashlytics dependency was found in the version catalog, but the runtime classpath and release behavior were not resolved/tested here. Do not claim telemetry is strictly local until dependency resolution and consent/disable paths are verified. |
| Low | Manifest source has an exported BROWSABLE `bousygram://cast/load` entry and a custom action on the exported launcher activity; merged manifests may add third-party components. | The diagnostic activity is now release-disabled. Inspect merged manifests for every release variant and retain the authenticated media-resolution checks. |

## Worktree changes in this pass

- `app/build.gradle.kts`: debug-only Engine Lab manifest placeholder; release and hardened-release keep it disabled.
- `app/src/main/AndroidManifest.xml`: binds the diagnostic activity's enabled state to that placeholder.
- `SYSTEM_INFORMATION.md`: reconciled commit, SDK/toolchain, transport, cache, security, signing, and historical-evidence claims.
- `docs/ARCHITECTURE.md`: corrected stale token, cache ceiling, read-ahead and next-item preload descriptions.

## Release decision

**No release approval from this audit.** The Cast release gate is appropriately off, but native binary provenance is absent, the legacy database encryption migration remains unresolved, the cache quota has a recursive default path, telemetry runtime/consent behavior is not verified, and the full unit-test task did not complete in this worktree.

## Verification attempted on the current worktree

Command:

```text
.\gradlew.bat --no-daemon :app:verifyTdlibAbiCoverage :app:verifyTvHardeningContract :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:processReleaseMainManifest :app:processHardenedReleaseMainManifest
```

- Passed tasks in the log: `verifyTdlibAbiCoverage`, `verifyTvHardeningContract`, `compileDebugKotlin`, `lintDebug`, `assembleDebug`, `processReleaseMainManifest`, and `processHardenedReleaseMainManifest`.
- The Gradle invocation was interrupted after `testDebugUnitTest` remained running without producing updated XML reports. Therefore the overall invocation is **incomplete**, not a successful full verification.
- Inspected merged manifests: Engine Lab is enabled/exported in debug and `enabled=false` in release and hardened-release; no release artifact was signed or assembled.
- `git diff --check` passed after documentation cleanup.
- No Android device, Firebase Test Lab result, or Chromecast was available/used.
