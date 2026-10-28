# Cast test results

## Current status

No physical Cast receiver or API 37 Android phone was available. Discovery, first-time connection, Default Media Receiver URL acceptance, real Telegram media startup, remote controls, seeking, screen-off streaming, Wi-Fi interruption, and capability expiry have not been tested on hardware.

The Cast capability registry has unit coverage for independent random resources, exact resource/token matching, and revocation. External payload parsing has tests for exact deep-link paths, required message identity, negative positions, and title length. These unit tests do not prove LAN isolation or Cast receiver compatibility.

## Build verification

Executed on branch `feat/google-cast-native-tdlib` with the project Gradle wrapper and JDK 21:

```text
:app:verifyTdlibAbiCoverage       PASS (arm64-v8a, armeabi-v7a)
:app:verifyTvHardeningContract     PASS
:app:testDebugUnitTest             PASS (257 tests, 0 failures, 0 errors, 0 skipped)
:app:lintDebug                     PASS
:app:assembleDebug                 PASS
:app:assembleDebugAndroidTest      PASS
git diff --check                   PASS
```

One earlier full run caught two static context leak lint errors in the Cast controller. The controller was changed to avoid strongly retaining Cast session objects; the complete suite above passed after that fix. Android test APK assembly is not a device test.

## Device test matrix

Still required: API 37 phone permission grant/deny; Chromecast discovery; valid, invalid, and revoked resource requests; MP4 load with Range/HEAD; play/pause/seek; local-to-remote and remote-to-local; long playback; screen-off; Wi-Fi/IP changes; receiver loss and reconnect; unsupported codec/container; logout and process death.

## Release blocker remediation update — 2026-10-28

- Production safety: `PRIVATE_MEDIA_CAST_ENABLED` is false for `release` and `hardenedRelease`; both the player affordance and LAN endpoint are guarded. Debug remains enabled for development. This closes exposure by disabling private-media Cast in production; it does not resolve encrypted delivery.
- Capability hardening: grants remain random, per-resource, constant-time compared, and revocable at session teardown; expiry was shortened from 12 hours to two hours. No receiver-side request was available to test expiry or token rotation.
- TLS: not implemented. A trusted hostname/certificate flow is absent and receiver certificate validation could not be checked on physical Chromecast hardware. No bypasses or relay were introduced.
- Android 17: API 37 permission declaration, UI request, startup check, and per-request server check exist. No API 37 device was available; grant/deny/revoke/reconnect behavior remains unverified.
- Screen off: a `mediaPlayback` foreground service with an ongoing notification now starts with the Cast LAN server and stops with it; it uses `START_NOT_STICKY`, so process restart does not silently resurrect a stale capability. The local Compose player still pauses on `ON_PAUSE` and releases its locks on `ON_STOP`. These code paths have not been tested screen-off, under battery optimization, memory pressure, or process termination.
- Physical Chromecast: none available. Discovery, Default Media Receiver load, HTTP range/seek, controls, network interruption, and firmware compatibility remain untested. No firmware details can be reported.

This update records implementation and limitations; it is not a hardware test pass or release approval.
