# Bousygram playback device-qualification checklist

> This document is a test protocol, **not** evidence that a device has passed.
> CI compilation, JVM tests, APK verification and emulator navigation alone do not
> establish real authenticated Telegram playback or stable release readiness.

## Builds and provenance

- Use only APKs from a **successful Android Enterprise Checks** run whose SHA matches
  the tested commit on `main` (or the exact PR branch under qualification).
- Verify `SHA256SUMS.txt` before installation. Record APK ABI and package signature.
- Production release signing and repeatable preview signing require the owner's
  signing configuration; don't commit secrets or replace release-gating evidence.
- Do not assume debug prereleases at older tags contain newer merged commits.

## Device matrix

Record each row separately, including Android version, ABI, hardware decoder,
network type, source file ID (redacted in shared logs), outcome and issue link.

| Device class | ABI | Essential qualification |
| --- | --- | --- |
| Android TV, older / low RAM | armeabi-v7a | D-pad focus, video seek, mpv fallback, memory pressure and restart |
| Android TV, modern | arm64-v8a | MKV random-range seek, subtitles, video surface recreation and recovery |
| Android phone | arm64-v8a | Touch playback, background/foreground lifecycle and audio-only |
| Android tablet | arm64-v8a | Adaptive chrome, picker scrolling/search and playback handoff |

## Required real-device scenarios

1. **Authenticated cold launch:** sign into Telegram, open Saved Messages and
   a group/channel, start MP4/MKV without fully downloading the media.
2. **Partial MKV / Cues:** seek backward 10 seconds, forward 10 seconds,
   jump near the end, then to the beginning. Verify correct bytes/ranges,
   no indefinite 'Preparing' state, no permanently corrupt media cache.
3. **Audio-only:** play MP3/FLAC/Opus without any selected video track.
   Verify audio starts, loading closes, no black-screen fallback and no
   45-second false timeout. Pause/resume from headset/media keys.
4. **MPV fallback:** exercise a supported file whose codec is unsupported
   by MediaCodec; verify single fallback, playback, subtitles and seeking.
5. **Force-stop / reopen:** play, stop the app process, reopen, select the
   same message, verify fresh TDLib identity resolution and playback.
6. **Connectivity loss:** disconnect and restore Wi-Fi during stream start,
   while watching, and while seeking to a not-yet-cached byte range.
7. **Account boundaries:** log out or switch accounts while a TDLib
   download, GetMessage/GetFile lookup or ReadFilePart request is pending.
   Confirm old request results do not appear under the new account.
8. **Chat catalog stability:** scroll to a mid-list source while thousands
   of chats populate; confirm scroll and focus are retained. Open All Chats,
   filter Channels, and continue loading; confirm the dialog does not collapse.
9. **Playback lifecycle:** rotate phone/tablet, switch to TV task,
   background and resume, seek after surface recreation, and play a second item.
10. **Resource usage:** repeat cold start, seeks, logout and relogin
    20 times; look for native-client growth, pending TDLib handlers,
    leaked sockets, ANRs, crashes or increasing memory use.

## Additional release-blocking hardware outcomes

The protected qualification record now requires these four scenarios to be reviewed as
**passed** on the signed candidate, in addition to the existing release matrix:

- `pauseDuringBuffer`: Media3, libmpv and LibVLC respect deliberate Pause while the stream
  buffers, seeks into uncached ranges or recovers from disconnected Wi-Fi.
- `nativeEngineSwitch`: repeated transitions among all three engines retain expected
  audible output, real video frames, timeline progress, subtitle operation and surface cleanup.
- `tvDpadSubtitleFocus`: a physical TV remote can enter/select/exit track and subtitle
  controls in Dark, Light and High Contrast without losing focus or trapping Back.
- `arm32MemoryStability`: repeated native playback and 30–60-minute viewing on a real
  32-bit TV have reviewed PSS, native heap and process continuity with no unexplained
  sustained memory growth, crashes or ANRs.

These fields are **not** automatically populated or certified by CI.

## Windows / ADB evidence capture

For **each real device**, use the signed candidate APK from the protected `signed-build` job (not a debug prerelease). On Windows, connect and authorize USB debugging, install Android platform-tools so `adb` is on PATH, then run:

```powershell
.\tools\qualification\Capture-BousygramDevice.ps1 -ApkPath ".\release-artifacts\Bousygram-vX.Y.Z-hardenedRelease-arm64-v8a.apk" -Install
```

For an older 32-bit TV use the `armeabi-v7a` signed APK; test the universal signed APK on the tablet. The PowerShell script records only the candidate SHA-256, OS/model/ABI and initial **not-run** test statuses. It does **not** collect private Telegram media, logcat, account identifiers, passwords or device serials. It deliberately does not mark any test passed.

Record at least three real playback runs per device, plus a sustained-viewing session, real Telegram authentication, full MKV/TS and network-recovery outcomes. Verify installed version, signed candidate hash, codec, audio/first-frame behavior, decoder, device memory and log evidence privately. Sanitized reviewer-accessible HTTPS evidence is needed before approval.

The signed-artifact proof must match all required entries in [the protected device qualification record](../ci/RELEASE_DEVICE_QUALIFICATION_RECORD.md). Do not supply fabricated results just to satisfy the validator.

## Evidence required for each case

Record: commit SHA, CI run URL, APK checksum, device build fingerprint or model,
Android version, ABI, steps, pass/fail, anonymized diagnostic excerpt, and
whether software or hardware decode was used. Preserve logs privately and
strip Telegram credentials, bearer tokens and chat/message identifiers before
sharing. File reproducible failures as GitHub issues.

## Release gate

Do not mark the build production-qualified until the complete target-device
matrix is signed off, all P0/P1 failures are resolved, and the protected release
workflow verifies signing identity and device-qualification evidence.


## November 8 follow-up: playback intent and ARM32 memory acceptance

This checklist is **not** evidence of a successful device run. Use the exact
commit and APK SHA-256 from a successful Enterprise / ABI CI run.

1. **Pause while buffering (all three Telegram engines):** select Media3,
   libmpv and LibVLC individually. Pause during first-load buffer, seek buffer
   and a Wi-Fi stall. Confirm the UI remains **Paused**, no late native
   Playing event starts video, and only a deliberate Play restarts decoding.
2. **Play while buffering:** press Play once before the first frame. The UI may
   show Preparing/Buffering but must not falsely show Playing. Verify that
   initial playback, a subsequent seek and recovery from a brief network
   interruption resume without a second Play command.
3. **ARM32 memory and sustained playback:** on a real armeabi-v7a TV, record
   p50/p95 startup, rebuffer time, ANRs and Java/native/PSS memory during 30–60
   minutes of repeated MP4/MKV/TS playback. Media3 now caps this profile at
   a 60-second forward buffer and 12-second back buffer; compare against
   prior exact-build memory and buffering baselines, not inferred MB savings.
4. **Extras tab retention:** on ARM32, browse multiple pages across Movies,
   TV and Anime, then switch repeatedly; confirm each inactive tab discards
   posters, restores its query/filters and successfully re-fetches page one.
   Exercise Android moderate/critical memory trim events. Verify D-pad focus,
   no empty-grid dead ends and no crashes after WebView renderer termination.
5. **Extras player:** reopen the subtitle picker after choosing a subtitle;
   D-pad focus must land on that selected track (or Off when none is selected).
   Simulate a Media3 source failure and use **Retry playback**. From the
   debug-only guarded LibVLC source, exercise **Try Media3** both after
   preparation failure and during playback. Verify single audio-focus owner,
   bounded retry attempts, and no leaked WebView or native file descriptor.

Keep the draft MediaForge PR unmerged until its exact-head Enterprise tests,
emulator instrumentation, LibVLC ARM32/ARM64 packaging, and TDLib generation
checks pass. These checks are necessary but **not sufficient**: a real ARM32 TV,
ARM64 TV, phone and tablet must still be tested with authorized media sources
and sanitized evidence. Any regressions require a fresh commit and retest of
the new APK hash. No debug artifact may be substituted for the signed
production candidate during final release qualification.
