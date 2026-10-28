# Cast architecture audit

Audit and implementation work began on 2026-10-28 in branch `feat/google-cast-native-tdlib`. Existing unstaged and untracked workspace changes were carried onto the branch; none were discarded.

## Confirmed current architecture

- `core/cast/CastReceiverManager.kt` is an intent/deep-link payload handoff into Bousygram's own player. It was not a Google Cast sender SDK integration.
- Telegram media is retrieved by native TDLib through `TelegramFileGateway` and `TelegramChunkScheduler`. The scheduler feeds direct Media3 and the loopback URL streaming paths.
- `TelegramProxyManager` Ktor binds to `127.0.0.1`; its URL cannot be fetched from a Chromecast. `LocalStreamingServer` is a second existing server implementation. Both use scheduler reads.
- The loopback access registry issues random per-file capabilities, but is designed for local playback and is not reused for the new LAN endpoint.
- The manifest disables global cleartext and allows it only for localhost/127.0.0.1. It has `INTERNET` and `ACCESS_NETWORK_STATE`, targets API 37, and has no local network runtime permission before this work.
- Google Play Services Cast sender artifacts were absent before this work.
- `MainActivity` is exported for launcher use and also accepted external Cast action/deep-link intents. Those payloads previously drove a file ID into stream preparation without validating it against a message in the current Telegram session.
- Native TDLib in this checkout is the `libtdjni.so` Java/JNI transport, with ARM32 and ARM64 files present. This Cast work does not alter TDLib transport.

## Implemented direction

The sender uses Google's Cast Application Framework with the Default Media Web Receiver. Cast control remains in `CastSessionController`; the receiver gets a media URL from a dedicated Ktor server bound to an active private Wi-Fi/Ethernet IPv4 address. That server uses an opaque media resource ID plus a random session capability and calls the existing scheduler-backed progressive byte streamer. It does not expose raw Telegram file IDs in the URL. Only one GET stream can run at a time. Session teardown revokes the capability and stops the server.

The mobile player shows the official MediaRouteButton; Android TV UI does not show it. Android API 37 local network permission is requested before presenting receiver selection. Current loading is limited to MP4 MIME/name hints. Telegram codecs are not inspected, so format support is not proven solely by that guard.

## Open risks and validation gaps

- No Chromecast or Cast-enabled Android TV was available. Default Media Web Receiver acceptance of this exact private HTTP URL, byte-range behavior, remote status, and seeking remain unverified.
- The Cast media URL is HTTP with a bearer capability. Binding to one private interface and short session lifetime narrows exposure, but does not provide transport confidentiality against an observer on that LAN. This security review must be resolved before production release.
- A Wi-Fi/Ethernet address change currently invalidates the active URL; automatic session rebind/receiver reload is not implemented.
- Screen-off/background operation and process-death recovery are not device verified. A dedicated foreground service has not yet been established as necessary or implemented.
- Session media is one active file; queue handoff and richer media metadata are not part of this milestone.
- Codec support cannot be inferred from `.mp4`; Cast receiver-specific H.264/AAC support needs a real receiver test or verified media metadata.
- Android 17 permission behavior is code-gated but has not been tested on an API 37 device.

The current endpoint is a reviewable implementation milestone, not a production security or device acceptance claim.
