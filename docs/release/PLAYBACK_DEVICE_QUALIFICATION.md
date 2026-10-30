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
