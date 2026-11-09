# Bousygram — Engineering quality review (2026-11-09)

Scope: Android TV, Android phone/tablet, TDLib progressive media access,
LibVLC/libmpv/Media3 playback, debug builds, CI release gates and physical
resource constraints. Review applies to `main` after PR #137.

## Verified strengths from source and GitHub Actions

- Kotlin/JVM/lint, Gradle wrapper integrity, ABI packaging, TDLib binding,
  Android emulator first-launch and synthetic MKV/TS decoder fixtures are
  exercised in Android Enterprise and emulator workflows.
- Mainline debug publisher checks **both** exact-commit Android Enterprise
  (including ARM32, ARM64 and Universal APKs) and emulator smoke, verifying
  artifact digests and commit identity before publishing a prerelease.
- A separate production release pipeline requires version/tag identity,
  protected branch checks, signing, and physical qualification evidence.
- Cached/pinned video and active byte range usage have explicit retention
  guards; cleanup uses bounded TDLib requests and a monotonic deadline.
- Watchdog timeout and seek-recovery paths use monotonic event timing;
  native seek-preview extraction is serialized and bounded for ARM32.

## Fixed in this review

Previously, `CacheManager.getFilesBlocking` removed the access-tracking
record for **every** `TdApi.Error`. Temporary or rate-limited GetFile
failures could therefore strand existing cached media outside the tracked
cache sweep. The corrected policy drops records only for narrowly known
permanent missing-file IDs and otherwise keeps them for retry. JVM tests
cover invalid IDs, temporary failures, unknown error messages and rate limits.

## Open qualification gaps — do NOT claim production/device solidity

1. Emulator smoke uses x86_64 AOSP, not a real 8 GB ARM32 Android TV. It
   cannot certify TV GPU/decoder driver stability, PSS, libVLC/libmpv
   operation under native load, or 60-minute streaming reliability.
2. Firebase Test Lab instrumentation is currently reported as *not
   configured* in the mainline workflow, so Android TV hardware coverage
   cannot be presumed.
3. **Repository governance:** GitHub's branches API currently reports
   `main.protected=false` and the repository rulesets endpoint returned no
   rulesets. The dedicated protection endpoint returned permission denied for
   the connector, so enforcement details cannot be independently inspected.
   Require branch protection/rulesets with exact-commit Android Enterprise
   and emulator checks before production release approval. As currently
   observed, the production-release protection gate should reject release
   attempts. Several prior feature branches also remain in the repository;
   do not delete without confirming they are fully merged.
4. Physical 60-minute soak still requires device observation of native heap,
   total PSS, free storage, seek completion and rebuffer count. The new
   physical-soak free-space data measures the **whole /data filesystem**,
   not the app's storage consumption.
5. The configured 160 MiB TDLib media cache allocation (within the user's
   200 MiB disposable-media target) is **not** a hard limit on installed
   app binaries, pinned/offline files, SQLite databases or TDLib metadata.
6. A publicly distributed client APK necessarily exposes embedded TMDB
   credentials. Debug packaging validates the secret is configured but
   cannot make an embedded API key confidential. Use an appropriate backend
   for credentials needing true server-side secrecy.
7. Production builds require separate signed release and device-qualification
   gates. A CI-passing debug prerelease is not production approval.

## Follow-on manual validation

On physical ARM32 and ARM64:
- 60-minute repeated MP4/MKV/MPEG-TS playback at 1x and 3x, seek, buffering
  and user Pause/Play transitions across ExoPlayer, LibVLC and libmpv.
- Subtitle and audio track selection on TV D-pad, preserving focus and
  readable captions; verify no crash after resume/backgrounding.
- Cache trim while active streaming and after watched episodes, pinned-media
  preservation, low-free-space behavior and re-download after eviction.
- Native PSS/heap, decoder errors and process restarts, cache cleanup duration,
  TDLib callback latency, free storage observations and full APK hashes.
- Compare exact source/build provenance and device cohort across baseline
  and candidate. Do not use synthetic fixture success as real ARM qualification.

This review is a source/CI assessment, not an assertion that unperformed
physical-device tests have passed.
