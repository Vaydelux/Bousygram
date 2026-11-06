# Audio Output Lifecycle — Phase 5

## Motivation

Media3 may reuse an audio session ID after stop/prepare on some devices. A
persisted effect selection does not mean an effect is still active on a stopped
or disposed decoder. Bousygram must keep user intent separate from observable
local audio-effect state.

## Policy

- When ExoPlayer stops, release per-session Android Equalizer and
  DynamicsProcessing, and discard the old media item.
- For enabled effects, show **Pending** rather than **Applied** until output is
  ready again. Disabled effects remain **Bypassed**.
- On audio session change, only apply saved processing when an item is loaded.
- When the next media item reaches READY with a valid session ID, retry effects
  still Pending even if Media3 did not issue a new session-change callback.
- Do not continually retry Unsupported effects on each READY notification.
- Do not modify Telegram source bytes, playback timing, or player preferences.

## Test coverage

The pure lifecycle policy has JVM tests for pending/bypassed states,
missing session IDs, partial effects, already-applied effects, unsupported
effects, and disabled effects.

The emulator smoke suite also invokes `AudioOutputLifecycleInstrumentedTest` to
check stopped-state status, repeated stop/release and post-release rejection. It
does not load a video or confirm an audible audio session.

The JVM and emulator tests do not prove that OEM sound routes work. Physical test
matrix: speaker, wired, Bluetooth, HDMI/passthrough, ARM32 and ARM64;
stop/start, next episode, seek, engine switch, and long-session soak.
