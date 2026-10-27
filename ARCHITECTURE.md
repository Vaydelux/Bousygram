# Bousygram Android Architecture

## Runtime flow

Telegram messages are mapped to `MediaItem` records and exposed by the browse/search ViewModels. Selecting a card creates a playback session through `PlaybackLaunchCoordinator` and `PlaybackSessionController`. `PlaybackCore` owns exactly one `PlayerEngine`; `PlayerEngineFactory` selects Media3 ExoPlayer by default and mpv only for a classified format/decoder fallback.

ExoPlayer reads `tdlib://` media through `TdLibDataSource`. mpv and compatibility clients use an authenticated loopback HTTP URL. Both paths acquire a `TelegramRangeLease` and read through `TelegramChunkScheduler`, which is the only playback component allowed to coordinate TDLib byte windows.

## Ownership and lifecycle

- `PlaybackCore`: foreground engine, rendering surface, audio/player lifecycle.
- `PlaybackSessionController`: active queue/session and file protection.
- `TelegramChunkScheduler`: per-file range serialization, cached-range checks, priorities, leases, cancellation, and `updateFile` wakeups.
- `CacheManager`: retention, pinning, quotas, and active-file protection.
- `StreamAccessRegistry`: scoped expiring HTTP capabilities; the server binds only to loopback.

Android TV uses a centralized player input state and stable focus keys. Left/Right enter or adjust seek preview and never select the next/previous item. Browse state is organized by real Telegram sources and topic IDs; series metadata remains storage-compatible but does not drive topic presentation.

## Security boundaries

Telegram credentials and the TDLib database key remain Keystore protected. Streaming URLs contain short-lived scoped capabilities, are accepted only on loopback, and are revoked with their playback session. Telemetry is local and records numeric performance/lifecycle data only—never credentials, message text, private paths, or tokens. Android backup and device-transfer extraction are disabled for all app-private domains.

## Compatibility

Release packaging requires non-empty `libtdjni.so` for `arm64-v8a` and `armeabi-v7a`. Media3 hardware decoding is preferred; mpv is not used as a response to ordinary network timeouts. The Baseline Profile Gradle plugin currently requires `android.newDsl=false`; remove that bridge after upgrading the plugin to an AGP 9-compatible release.
