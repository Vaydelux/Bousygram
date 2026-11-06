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

## Custom 5/10-band editing

Select **Custom** to edit a persistent 10-band curve from -6 dB to +6 dB.
The 5-band view edits five representative nodes of the same 10-band curve;
switching views does not discard hidden bands. `Reset Flat` resets all ten
points to 0 dB. Touch users get continuous sliders in Settings; Android TV
uses focusable minus/plus buttons in both Settings and the in-player audio
menu. Presets and the live A/B bypass remain available while a video plays.
All positive gains reserve matching preamp headroom.

## Smart Volume (separate from EQ)

Smart Volume is opt-in and does not re-encode or replace a Telegram stream:

- **Night Comfort:** moderate compression and peak limiting. This is not a
  loudness-standard measurement.
- **Dialogue Boost:** compresses louder peaks; libmpv additionally attenuates
  sub-bass. Combine with the Dialogue EQ preset for clearer voices.
- **Smart Leveling:** dynamic level control and limiting. On 64-bit libmpv
  this uses FFmpeg's history-based `dynaudnorm`; Android's Media3 engine
  uses Android `DynamicsProcessing` compression and a peak limiter instead.
  These are different algorithms and are not guaranteed to match a broadcast
  LUFS target. Neither feature performs offline EBU R128 analysis.
- **Off:** no dynamic processing.

**Compatibility:** ExoPlayer needs Android API 28+ and a supported
per-player AudioSessionId. LibVLC supports its native EQ but has no equivalent
dynamic-range processing API in this integration; the player therefore
displays **Unsupported** for Smart Volume under LibVLC. Libmpv deliberately
disables the history-intensive Smart Leveling filter on 32-bit processes;
the lighter Night/Dialogue compressor modes remain available. Unsupported
effects do not block video playback.

## Verification and device testing

The player displays independent EQ and Smart Volume states:
Bypassed, Pending, Applied, and Unsupported. `Applied` means that the
requested native API accepted and retained the effect; it is **not** an
acoustic measurement. Cast receiver audio and digital passthrough bypass
local processing.

For actual release qualification record paired A/B samples using the
same file and volume across ExoPlayer, libmpv and LibVLC. Check 2-channel,
5.1-channel, HDMI, Bluetooth, and native audio session replacement.
Use ARM32 and ARM64 televisions for 30 successive preset changes and 30
engine switches, comparing CPU%, Java heap and native/total PSS before,
after and after teardown. Track crashes, audio interruptions,
AV drift and any retained filters. Ensure Original/Off restores a dry
signal before declaring the feature production-qualified.

### Opt-in native audio lifecycle stress

`NativeEnginePhysicalSoakTest.optInPhysicalAudioEffectsStressMeasuresPssAndCpuButNotAcousticOutput`
runs only on an ARM32/ARM64 device when the instrumentation argument
`physicalNativeAudioSoak=true` is explicitly provided. It cycles through
libmpv, LibVLC and ExoPlayer 30 times, applies/bypasses custom EQ and Smart
Volume, releases resources twice to exercise idempotency, and logs total/native
PSS and process CPU time every five cycles. It does **not** include a stream,
measure audio continuity, or certify sound quality. For those acceptance
criteria, play real verified MP4/MKV/TS media and record A/B audio and PSS
traces independently.

## Phase 3 — released-engine safety

A player that has been released must reject late EQ and Smart Volume requests
rather than opening a new audio effect on a stale session. Media3 teardown is
idempotent and independently attempts listener cleanup and decoder release.
LibVLC also refuses stale audio-profile updates after disposal. On all engines,
the post-release EQ and Smart Volume status remains Bypassed.

The opt-in physical audio stress test asserts these post-release invariants for
ExoPlayer, libmpv and LibVLC. CI compilation alone cannot establish that an
OEM device's effects are audible, correctly routed, or leak-free under playback.
