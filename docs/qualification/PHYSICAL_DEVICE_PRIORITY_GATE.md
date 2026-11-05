# Physical-device priority gate: TDLib reconnect, ARM32 PSS, libmpv and LibVLC

**Status:** opt-in test tools added. **No physical device has been tested or certified by this change.** These results remain distinct from GitHub Actions JVM and x86_64 emulator coverage.

## Preconditions

- Use a known Git commit and an APK built from that exact commit. Record the APK SHA-256 and build variant separately; debug builds are **not** production-signed qualification.
- Install Android platform-tools (`adb`). On Windows, open PowerShell in the repository root. Use a **physical** ARM32 TV and separately an ARM64 device.
- Use a dedicated, authorized Telegram test account and media you have permission to play. **Never** pass passwords, Telegram tokens, local stream URLs, chat IDs or media filenames in public reports.
- For test-repeatability, record the device model/Android version, available RAM, APK checksum, network type, media codec/container, cache state and test date privately. Keep the clip and network comparable between rounds.

## Priority 1: real TDLib account-reconnection

Sign into the real Telegram account in Bousygram. Test each condition while playback or a file-range request is in progress: (a) offline to online during startup, (b) offline to online during playback, (c) offline to online during uncached seek, (d) force-stop/reopen without wiping app data, and (e) sign-out or account switch with outstanding work. At **each** step, inspect real authorization state, playback completion, no stale account data, and no permanent Preparing spinner. Do not interpret a normal network loss as authorization revocation.

Record sanitized PSS/process continuity while doing the actions:

```powershell
.\tools\qualification\Capture-BousygramPhysicalSoak.ps1 -Scenario tdlib-reconnect -Cycles 10 -SampleSeconds 5
```

The script **does not** modify Wi-Fi settings, force-stop the app, sign into Telegram or verify authorization for you. Perform those actions on the device, then prompt the sampler. Record actual functional pass/fail separately; PSS alone never proves reconnect.

**Source hardening since PR #100:** `TelegramAuthManager` now bounds the native `GetMe` verification to 15 seconds. An identity-attempt gate rejects callbacks from timeouts, logout, stale generations and manual retries; a TDLib `ConnectionStateReady` can trigger a fresh attempt when an identity check failed during disconnection. JVM tests exercise timeout versus callback races. **These are source/test changes, not proof of a real authenticated reconnection on hardware.**

## Priority 2: ARM32 repeated playback memory

On a physical `armeabi-v7a` TV, select an MKV, TS (with and without usable seek data) and MP4 from a fixed private test set. Repeat play -> 20 seeks -> next item -> return to library for at least 20 cycles, then run a 30–60 minute viewing session with the same test set. Include cold and warm cache runs. Monitor Android LMK, process restarts, UI responsiveness, and memory over time.

```powershell
.\tools\qualification\Capture-BousygramPhysicalSoak.ps1 -Scenario arm32-playback -Cycles 60 -SampleSeconds 60
```

Capture **Total PSS**, **Native Heap PSS** when available, peak PSS, total change and distinct process IDs. Compare a stable baseline against the same scenario after code changes; PSS can fluctuate with GC and allocator caching, so one upward sample is **not** proof of a leak. The tool stores only parsed numbers, not raw `dumpsys` output.

The sampler can optionally associate an operator-supplied APK checksum and source commit with the run. This is provenance metadata only: it **cannot** independently verify which APK is currently installed.

```powershell
.\tools\qualification\Capture-BousygramPhysicalSoak.ps1 -Scenario arm32-playback -Cycles 60 -SampleSeconds 60 -ApkPath ".\candidate.apk" -SourceCommit (git rev-parse HEAD)
```

To compare two **complete** captures with the same device, ABI, scenario, sample count and cadence, run:

```powershell
.\tools\qualification\Compare-BousygramPhysicalSoak.ps1 -Baseline ".\before.json" -Candidate ".\after.json" -MinCycles 20
```

The comparator rejects interrupted, mismatched, restarted-process and falsely certified reports. It prints early/late PSS medians, drift and peaks, but it **does not** certify that a native memory leak exists or is fixed. For a repeatability comparison of the same candidate APK, add `-RequireSameApk` and supply `-ApkPath` during both captures.

## Priority 3: native libmpv/LibVLC stability

On each ARM ABI, use the existing debug Engine Lab, then test the normal playback UI. Alternate ExoPlayer -> MPV -> LibVLC -> ExoPlayer at least 50 times with actual authorized media. Include surface detach/reattach, pause during buffering, multiple seek attempts, subtitle switching, repeated app background/foreground, and 30–60 minutes of viewing. Verify a real video frame and audible output on each engine, no unexpected auto-resume after user Pause, and no native crash/ANR.

The opt-in Android instrumentation checks **native init/stop/release only** (30 alternating cycles), not authenticated streaming:

```powershell
adb shell am instrument -w -e class com.vaydelux.bousygram.core.media.NativeEnginePhysicalSoakTest -e physicalNativeSoak true com.vaydelux.bousygram.test/androidx.test.runner.AndroidJUnitRunner
```

Install **both** the matching debug APK and AndroidTest APK for that command. This test is deliberately skipped unless `physicalNativeSoak=true`. A pass does **not** establish decode/seek/long-session stability. During hands-on media switching, capture memory separately:

```powershell
.\tools\qualification\Capture-BousygramPhysicalSoak.ps1 -Scenario native-engines -Cycles 50 -SampleSeconds 2
```

## Evidence and exit conditions

- Treat automated instrumentation, parsed PSS observations, and operator-attested media/account checks as **three different** evidence categories.
- Confirm functional reconnection for offline startup, playback, seeking and force-stop/reopen without account leakage.
- Show repeatable, controlled ARM32 memory trend without unexplained runaway growth or LMK process death; investigate trends before defining numerical release thresholds.
- Show real frames/audio/seek/subtitle/surface stability for libmpv and LibVLC on **both** physical ABIs, and collect sanitized crash/ANR reports privately when failures occur.
- Do not publish private logcat or streaming URLs in a public issue or CI artifact. Use the existing [release device qualification checklist](../release/PLAYBACK_DEVICE_QUALIFICATION.md) and protected signing/qualification gate for production approval.

The JSON generated by the script explicitly says `productionQualified=false` and keeps all manual verification fields false. A test operator, not the sampler, must sign off real-world behavior.


## Native lifecycle and live debug telemetry (P0.3)

Debug Engine Lab now samples **app Total PSS and Native Heap PSS** every two
seconds, distinguishes the **requested** engine from the actual fallback engine,
and reports native first-frame telemetry as **unavailable** where the engine
has no reliable callback. A progressing audio timestamp is not a rendered frame.
The Activity will not prepare blank or non-loopback media URLs. These process
memory readings are diagnostic snapshots; compare sustained runs with the
PowerShell sampler instead of declaring a memory leak fixed from one value.

The existing physical init/release instrumentation emits sanitized PSS samples
every five native engine cycles; no Telegram content is collected. There is
also a **separately opted-in** offscreen surface attach/detach stress check:

```powershell
adb shell am instrument -w -e class com.vaydelux.bousygram.core.media.NativeEnginePhysicalSoakTest#optInPhysicalSurfaceAttachDetachSmokeWithoutPlaybackClaims -e physicalNativeSurface true com.vaydelux.bousygram.test/androidx.test.runner.AndroidJUnitRunner
```

Offscreen surface attachment alone **does not** prove that video rendered. A
real display, MKV/TS sample, audio, subtitle and seek test with visual review
must still be performed in the normal player on physical devices. The surface
test intentionally instantiates one native engine at a time to bound temporary
ARM32 memory pressure.
