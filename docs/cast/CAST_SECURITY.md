# Cast security review

## Implemented controls

- The Cast server starts only after an active Cast session and is bound to one detected private Wi-Fi/Ethernet IPv4 address, not a wildcard address.
- Each media resource gets an opaque random identifier and independent 256-bit random bearer token. The grant is scoped to one Telegram file and compared in constant time.
- Only GET and HEAD routes exist. Raw file IDs and local paths are absent from the URL. At most one body stream is active.
- Session end revokes the resource and stops the Ktor engine. Tokens are also bounded by a 12-hour expiry and an in-memory grant cap.
- The existing loopback tokens are not reused for LAN delivery.
- External Cast payloads now require a current authorized Telegram session and a positive chat/message reference whose TDLib message resolves to the supplied file. Payload path, version, IDs, position, and title length are validated before playback.
- The app does not log Cast media URLs or bearer tokens.
- Each capability now expires after two hours; starting a new Cast stream stops the previous server and rotates the resource ID and token.
- `PRIVATE_MEDIA_CAST_ENABLED` is true only in the debug build. Release and hardened-release variants fail closed in both the player UI and LAN server.

## Remaining security work

The debug media URL still uses HTTP. An observer on the same LAN may capture the URL and replay its bearer capability until revocation or expiry; the bearer token is authorization, not encryption. Production private-media casting remains disabled.

TLS was evaluated at the architecture level. A self-signed certificate, custom globally trusted CA, disabled certificate verification, and global cleartext exception are rejected. Receiver-trusted TLS would require the endpoint hostname to match a certificate chained to a CA trusted by the receiver, plus a receiver-compatible way to resolve/reach that endpoint on the LAN. A phone-generated ephemeral certificate does not establish those properties. The app has no trusted hostname/certificate provisioning path, and no physical Chromecast was available to verify receiver behavior. Therefore no TLS implementation was selected and no claim of encrypted media delivery is made. A cloud relay is not used.

Rate/slow-client defenses are basic: one active GET is allowed, but there is no separately verified request-rate limiter, network transition callback, or device security test. The two-hour expiration is not renewable; long sessions must fail closed and be reauthorized. Permission revocation, Wi-Fi changes, logout, receiver requests after expiry, and process termination still require device testing.
