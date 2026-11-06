# Bousygram — Live Audio Equalizer

## What this does

The **Audio equalizer** adjusts the tone of locally decoded Telegram media **while it is playing**. Video downloads, TDLib chunks, seek maps, subtitles and the original file bytes are **not** altered or re-encoded.

The default **Original** disables processing. Choose a profile in **Settings → Audio equalizer**, or during playback via **Tracks → Audio → Sound profiles**. A new choice is saved on this device and reapplied on the next video or engine switch.

| Preset | Intended listening profile |
| --- | --- |
| Original | Unmodified audio; EQ disabled |
| Cinema | Warm lows, dialogue-adjacent presence, slightly brighter highs |
| Dialogue | Reduces rumble while raising voice intelligibility bands |
| Music | General-purpose music listening |
| Deep Bass | Low-frequency emphasis |
| Night | Less low-end boom, higher voice presence |
| Vocal Focus | Foreground voice tone |
| Rock | Guitar and drum presence |
| Pop | Bright vocal/music balance |
| Jazz | Natural warmth with light high-frequency detail |
| Classical | Subtle high-frequency definition |
| Gaming | Effects and spatial cues |
| Live | Concert/stage feel |
| Podcast | Spoken-word emphasis |

These are **conservative tonal equalizers** rather than loudness normalization, AI voice isolation, dynamic-range compression, or a limiter. **Night** does not guarantee consistent volume or protect against loud peaks.

## Playback engine behavior

- **ExoPlayer (Media3):** Android `audiofx.Equalizer` attached **only to the active player audio session**. Recreates the effect on session changes and releases it on player teardown. Does not use deprecated global session 0. Audio device/OEM support and digital passthrough may bypass EQ.
- **libmpv:** A named, four-band FFmpeg `lavfi` filter with conservative preamp headroom. Removal/replacement touches only Bousygram's filter. Native filter availability varies across ABI builds; unsupported filters leave playback usable.
- **LibVLC:** Native `MediaPlayer.Equalizer` with logarithmically mapped output bands and preamp headroom. Falls back safely if the native equalizer is unavailable.
- **Cast:** Cast output is decoded on the receiver, not this device. The device-local EQ does **not** modify receiver audio.

No new media network permissions or third-party sound libraries are introduced. The EQ state is a device-global preference, not tied to a Telegram account or identifiable message.

## Qualification

Automated checks: preset bounds and frequency interpolation, Kotlin compile/lint, unit tests, and debug APK ABI matrix.

Physical testing still required: real Android TV and phone audio output on ExoPlayer, libmpv and LibVLC; rapid preset switching; manual pause, buffering and seek; Bluetooth/HDMI/speaker switching; stereo and multichannel content; repeated video/engine switches; ARM32 native PSS and audio CPU usage. Confirm **Original** restores unprocessed sound and no audio effect survives a disposed player. Confirm no equalization claim on digital passthrough or Cast.

No claim of production-grade studio EQ or identical tonal response across vendor audio routes is made by green CI alone.
