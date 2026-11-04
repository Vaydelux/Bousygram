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
