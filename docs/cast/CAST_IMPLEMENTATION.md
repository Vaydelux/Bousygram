# Cast implementation

## Sender SDK

The app uses `com.google.android.gms:play-services-cast-framework:23.0.0`, the latest Android Sender release listed by Google's Cast SDK release notes at implementation time. `BousygramCastOptionsProvider` selects Google's Default Media Web Receiver, so an application registration ID is not required for this initial receiver. The official SDK owns discovery and route selection through `MediaRouteButton`; `CastSessionController` owns session state and media commands.

The controller exposes state as a Kotlin `StateFlow`, supports media load, play, pause, seek, stop, and session end, and releases callback listeners on session end. The player pauses local playback after remote playback reports Playing; on session end it resumes local playback near the last reported remote position.

## Media flow

The phone creates a session capability and starts `CastLanStreamingServer` only after a Cast session connects. The server binds to an active private IPv4 address on Wi-Fi/Ethernet, uses an ephemeral port, and serves `/cast/media/{opaqueResourceId}?token=...`. The server accepts GET and HEAD, handles a single byte range through `HttpRangeParser`, rejects invalid capability scopes, limits active media GETs to one, and delegates media delivery to `TelegramProxyManager.streamTelegramFileSequentially`, which reads through `TelegramChunkScheduler`.

Cast currently accepts MP4 MIME/name hints. It does not probe H.264/AAC stream metadata. End-to-end receiver playback must therefore remain unverified until a physical device test.

## Build and native dependencies

Cast does not alter the existing TDLib JNI transport or packaged ARM32/ARM64 native libraries. No x86_64 TDLib support is added. The Google Cast sender requires compatible Google Play services at runtime; unavailable services are represented as unsupported state and do not crash initialization.

## Known limits

The endpoint currently uses authenticated HTTP, not TLS. The capability is revoked when the Cast session ends and expires after 12 hours, but LAN traffic is not confidential. Network address changes, service foregrounding, process death, and actual Default Media Receiver playback remain device validation work.
