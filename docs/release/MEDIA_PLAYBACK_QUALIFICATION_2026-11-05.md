# Bousygram – Telegram Media playback qualification

Status: **implementation committed, hardware verification outstanding**.
This document is a verification plan, not evidence that every device and codec works.

## Source and build gates

- Commit source to `main` only after source review and focused regression tests.
- Required Android Enterprise CI: Kotlin compilation, JVM tests, lint, ABI-matrix APK checks.
- Android Emulator Cast Smoke on `main`: actual app cold launch, D-pad,
  media chrome, caption API smoke, navigation and authenticated-free previews.
- Debug APKs published by the exact-source prerelease workflow only if build
  and ABI verification succeed. Never substitute an older APK.
- Physical device signing, codec support, and Telegram credentials remain a
  separate qualification step.

## Required device-and-media matrix

| Surface | Files | Scenarios |
| --- | --- | --- |
| ARM32 Android TV | MP4, MKV with Cues at head/tail, TS with/without PCR, WebM | Cold play, 20 seek-back/forward actions, pause during buffer, resume, repeat playback |
| ARM64 Android TV | Above plus E-AC-3, Opus and ASS/SSA tracks | Track switching, renderer fallback, D-pad preview and subtitle size/presets |
| ARM64 phone | TS/MKV/MP4 plus SRT and embedded text | Touch scrub, orientation changes, TextureView/surface switching, subtitles |
| Tablet | All available sample files | Large layout, subtitle focus, history and account switching |

### Playback and memory assertions

1. Newly selected playable video starts automatically. After manual Pause, no
   decoder, buffer, lifecycle or reconnect event auto-resumes until Play is pressed.
2. Backward/forward seeks never fabricate native timestamps; missing seek map
   produces a clear message, not a jump to 0:00.
3. Reconnect and fallback use the pending seek target ahead of an old high-water
   progress marker. Native position must confirm the final result.
4. MPV/Media3 switches must remount a fresh video surface without leaking a
   TextureView `Surface` or text-cue observer.
5. TS artwork may be extracted from a contiguous cached prefix only; never
   read sparse ranges as media. The fallback remains a format tile if no frame
   can be decoded; it must not trigger a full download just for thumbnails.
6. Frame previews can be generated when media is completely cached locally.
   During streaming the overlay shows a truthful time label and explains that
   preview frames are unavailable.
7. Subtitle White, Yellow, Black and 75–200% sizing persist across app restarts.
   Bitmap subtitle tracks and burned-in captions cannot be recolored.
8. Measure total PSS, first-frame latency, decoder dropped frames, seek recovery,
   retry frequency, and playback stall ratio with production-like test clips
   and repeated 30–60 minute sessions. Record device/ABI/Android version and
   compare identical signed APK builds.

## Evidence needed before claiming hardware ready

- Complete green exact-head CI and emulator smoke.
- At least one physical ARM32 TV and ARM64 phone test with private test media.
- Pass/fail recordings for MKV seek, TS seek (seekable and unseekable variants),
  restart after force-stop, interrupted network and subtitle rendering.
- Failure traces with private media names/account IDs redacted; do not upload
  Telegram media, secrets, or raw logcat to public CI artifacts.

No claim of zero defects is possible from static inspection or emulator CI.
