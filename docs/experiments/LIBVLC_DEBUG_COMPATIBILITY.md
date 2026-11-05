# Bousygram — Official VideoLAN LibVLC playback option

VideoLAN LibVLC 3.6.5 is included in regular Bousygram APKs alongside Media3
ExoPlayer and libmpv. It is **manually selectable** under Settings → Playback
engine or via the player's engine switcher. Auto keeps the original
ExoPlayer/libmpv routing to avoid unexpected RAM pressure on low-memory TVs.

## Functionality

- Uses the same account-authorized, short-lived private
  `http://127.0.0.1:<port>` token URL and range scheduler as libmpv.
- Stream/decoder errors are surfaced via the shared `PlayerEngine` API.
- Track selection, actual native time/seekability, buffered state and
  Play/Pause intent are handled independently from ExoPlayer and libmpv.
- When LibVLC cannot initialize, the factory falls back to libmpv.
- Old saved `LIBVLC_EXPERIMENTAL` preferences migrate to official `LIBVLC`.
- Manual Pause is not equivalent to buffering, focus interruption or
  retry; idempotent control prevents accidental toggle resumes.
- LibVLC text-subtitle color/font-size customization does **not** yet have
  the same controls as ExoPlayer and libmpv; track selection is supported.

## Build and security gates

VideoLAN dependency: `org.videolan.android:libvlc-all:3.6.5`.
The AAR is included in normal debug/release variants. Both native media
players supply `libc++_shared.so`; packaging picks a single runtime copy
per architecture. A successful build cannot establish whether this shared
C++ runtime is stable for both decoders on physical hardware.

Use `./gradlew :app:assembleDebug :app:testDebugUnitTest` to build locally.
The Android Enterprise and LibVLC integrated ABI workflows check ARM32 and
ARM64 APKs for TDLib, libmpv, LibVLC and the C++ shared runtime.

Normal debug APKs are debug-signed test candidates, not qualified production
releases. Production signing, compliance, and device acceptance are separate.

## Device qualification

On an ARM32 Android TV and ARM64 phone, test identical Telegram files:
MPEG-TS with/without seek indexes, MKV with embedded SRT/ASS or bitmap
subtitles, MP4, WebM, codec failures, first frame, pause during buffering,
forward/backward seeks, restart, cast handoff, sign-out, network loss,
50+ engine switches, and sustained sessions tracking PSS/native crashes.

LibVLC cannot add a seek index to an arbitrary MPEG-TS file, and this
integration does **not** claim every format works on every device.

## Licensing

VideoLAN's LibVLC Android component is under LGPL-2.1. See
[third-party notices](../THIRD_PARTY_NOTICES.md). Bundled native components
must not be described as exclusively MIT-licensed.


## P0.3 lifecycle safeguards (November 6, 2026)

- LibVLC constructor failures now close the parent native runtime and preserve
  the original allocation exception with any cleanup errors suppressed.
- Events delivered after explicit Stop or release are ignored; late EndReached
  and Stopped signals during a new prepare cannot be treated as a confirmed
  new-file EOF unless native Playing was observed for that media.
- Deferred main-thread LibVLC callbacks are tagged with a media generation so
  cleanup, track refresh and pause commands cannot operate on a newer session.
- LibVLC seek targets are bounded by a known container duration, and libmpv
  ignores terminal/property events while stopped or before FILE_LOADED.
- JVM tests cover constructor cleanup, event policies and seek limits. These
  source tests **do not** replace real ARM32/ARM64 decoding or surface-soak
  evidence; video frame rendering and native heap growth remain unverified.

Qualification must still test at least 50 real Exo/MPV/LibVLC switches and
30-60 minute MKV/TS sessions on physical ARM32 and ARM64 hardware, including
pause during buffering, surface recreation, subtitles, logouts and memory PSS.
