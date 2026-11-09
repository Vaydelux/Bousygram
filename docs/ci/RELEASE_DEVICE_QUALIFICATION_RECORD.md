# Protected physical-device qualification record

**Release decision: BLOCKED until actual testing is complete.** A green JVM build, emulator run, debug APK, or unverified JSON flag is **not** hardware qualification.

The protected GitHub environment `release-qualification` requires `RELEASE_DEVICE_QUALIFICATION_JSON` **only after** the actual *production-signed candidate APKs* have been tested. Copy the exact tag, full source SHA, and SHA-256 hashes from the signed candidate artifacts; never fill a record for a debug build or an APK with a different signing certificate.

The following example shows the **schema**. All `passed` values are placeholders, **not evidence that any device was tested**.

```json
{
  "status": "passed",
  "tag": "v1.2.3",
  "commit": "1111111111111111111111111111111111111111",
  "nativeTdlibRuntime": "passed",
  "reviewerApproval": true,
  "openP0P1": 0,
  "evidenceUrl": "https://example.org/actual-reviewed-release-evidence",
  "apkSha256": {
    "arm32": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
    "arm64": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
    "universal": "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
  },
  "deviceResults": {
    "arm32Tv": {
      "status": "passed", "abi": "armeabi-v7a",
      "nativeTdlibRuntime": "passed",
      "apkSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
      "model": "Actual ARM32 TV model", "androidVersion": "Actual OS version",
      "iterations": 3, "evidenceUrl": "https://example.org/evidence/arm32-tv"
    },
    "arm64Tv": {
      "status": "passed", "abi": "arm64-v8a",
      "nativeTdlibRuntime": "passed",
      "apkSha256": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
      "model": "Actual ARM64 TV model", "androidVersion": "Actual OS version",
      "iterations": 3, "evidenceUrl": "https://example.org/evidence/arm64-tv"
    },
    "arm64Phone": {
      "status": "passed", "abi": "arm64-v8a",
      "nativeTdlibRuntime": "passed",
      "apkSha256": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
      "model": "Actual ARM64 phone model", "androidVersion": "Actual OS version",
      "iterations": 3, "evidenceUrl": "https://example.org/evidence/arm64-phone"
    },
    "arm64Tablet": {
      "status": "passed", "abi": "arm64-v8a",
      "nativeTdlibRuntime": "passed",
      "apkSha256": "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
      "model": "Actual ARM64 tablet model", "androidVersion": "Actual OS version",
      "iterations": 3, "evidenceUrl": "https://example.org/evidence/arm64-tablet"
    }
  },
  "scenarioResults": {
    "authenticatedStartup": "passed",
    "mkvTsSeek": "passed",
    "audioOnly": "passed",
    "mpvFallback": "passed",
    "forceStopReopen": "passed",
    "networkRecovery": "passed",
    "accountIsolation": "passed",
    "catalogNavigation": "passed",
    "lifecycle": "passed",
    "longSession": "passed",
    "pauseDuringBuffer": "passed",
    "nativeEngineSwitch": "passed",
    "tvDpadSubtitleFocus": "passed",
    "arm32MemoryStability": "passed"
  }
}
```

For reproducible performance observations during manual testing, use the
[playback performance test plan](PLAYBACK_PERFORMANCE_TEST_PLAN.md).
That developer tool produces *observations only*, not release approval, and
never changes the protected production-qualification requirements.

## What each reviewer must verify

- **Hardware execution:** test with actual Telegram authentication and real files. Verify TDLib native initialization on every supported ABI. Use the full scenario checklist in [PLAYBACK_DEVICE_QUALIFICATION.md](../release/PLAYBACK_DEVICE_QUALIFICATION.md). Each device must have at least three recorded playback iterations; the long-session scenario must include sustained viewing and restart/recovery checks.
- **Four additional real-device acceptance gates:** `pauseDuringBuffer` checks manual Pause across startup, seeking, and network interruption on all three engines; `nativeEngineSwitch` checks visible/audible output, lifecycle, subtitles and seek after repeated switches; `tvDpadSubtitleFocus` checks remote focus visibility and subtitle controls in all themes; `arm32MemoryStability` requires reviewed repeated-playback PSS, crash/ANR and process-continuity evidence. **PSS counters alone do not establish working playback.**
- **Candidate binding:** the ARM32 TV uses the *signed* `arm32` APK, ARM64 TV and phone use the *signed* `arm64` APK, and ARM64 tablet uses the *signed* `universal` APK. These are exact artifact hash bindings, not package-name matches.
- **Evidence:** reviewer-accessible HTTPS links for the overall report and each device, including actual OS/model/ABI, tested video formats, elapsed results, crash/ANR status, media codec/decoder, network conditions, p50/p95 start/seek/buffer measurements, and issues. Do not place Telegram identifiers, bearer tokens, private video, account phone numbers, raw device serial numbers, or unsanitized logcat in public evidence.
- **Failure policy:** any missing device, incomplete scenario, unresolved P0/P1 issue, mismatch to the signed artifacts, or failed runtime check means `status` must **not** be `passed`. Open issues with reproductions, fix them and retest the **new exact signed APK hash**.
- **Governance:** configure required reviewers and restrict deployment to approved branches for `production-signing`, `release-qualification`, `release-draft`, and `release`. A `reviewerApproval: true` JSON value does not replace a real environment approval.

The validator `tools/ci/qualify_release_manifest.sh` checks the fields, version/tag/commit, signed APK SHA-256 hashes, supported device classes, native runtime checks, and scenario outcomes. It cannot determine whether the claimed device evidence is genuine—that is the protected reviewer’s responsibility. Negative fixtures in `tools/ci/test_release_manifest.sh` must stay green.

## Release steps

1. Confirm `main` protection/rulesets and successful exact-commit enterprise CI.
2. Configure protected `production-signing` secrets listed in [RELEASE_CONFIGURATION.md](RELEASE_CONFIGURATION.md).
3. Run **Actions → Release Bousygram APKs → auto**; it creates/reuses an immutable tag and starts protected signed-build production release.
4. Download the exact **signed** three-APK candidate and its hashes. Run all real-device checks; capture anonymized evidence and reviewer approval.
5. Populate the protected `RELEASE_DEVICE_QUALIFICATION_JSON` from the actual results/hashes, then re-run or approve the protected device-qualification job as appropriate.
6. Approve draft creation and then publication separately. Only after a non-prerelease release is published and verified may the build be described as production-qualified.
