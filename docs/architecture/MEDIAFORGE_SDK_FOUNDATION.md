# MediaForge SDK foundation — session and security contracts

Status: Phase 1 incremental Android SDK foundation. Native player ownership remains in Bousygram Extras and no public AAR is published.

## Public API

**MediaForgeSdk.openSession(MediaForgeSessionRequest(embedUrl))** opens an independent, disposable **MediaForgeSession**.

- **observe(url)** records a recognized HTTPS video/subtitle URL seen in the user-visible WebView; resources are bounded.
- **snapshot()** and **state** expose opaque, session-specific IDs for stream candidates and a StateFlow for lifecycle observation. Raw stream bearer URLs are never placed in the source-picker state; observed subtitle URLs remain transient in memory because the subtitle picker uses them.
- **inspect(candidateId)** permits only IDs currently observed in the session; starting a new inspection cancels the previous one.
- **clear()** retires all IDs and cancels inspection without closing the session.
- **close()** idempotently retires sources, marks the session closed and cancels inspection.

**MediaForgeSessionInspection** returns Verified or Rejected with a typed MediaForgeInspectionFailure. It never forwards a raw provider error message. Verified means *bounded preflight passed*, not that all media frames will decode or the provider authorizes redistribution.

The default SDK inspector wraps MediaForgeResolver, which already has validated HTTPS networking. Test hosts can inject MediaForgeInspector without any real network call.

## Ownership

| Layer | Ownership |
| --- | --- |
| mediaforge-core | Reusable session API, discovery, source validation, bounded inspection |
| mediaforge-webbridge | Untrusted WebView, D-pad input, navigation and renderer-loss handling |
| mediaforge-player-adapters | Guarded Media3 and experimental LibVLC progressive adapter |
| Bousygram Extras | UI, explicit native engine selection and player lifecycle |
| Telegram / TDLib | Completely independent media and account architecture |

The SDK must never inherit Telegram account data, login credentials, WebView cookies, private media caches, or the Telegram player's resource lifecycle.

## Security guarantees and limitations

- An explicit user-selected HTTPS embed is required. Collector budgets must be between 1 and 100 resources per class.
- Unobserved URLs cannot be inspected directly, and candidate IDs from one session cannot be used in another session.
- Source URLs with signed query parameters are retained in memory only; stream list UI exposes ID, format and host.
- The default network inspector uses guarded DNS, HTTPS redirects, response checks and bounded reads.
- User-controlled text from provider/network errors is not surfaced as typed SDK errors.
- WebView URL filtering does **not** amount to socket-level DNS pinning. The WebView security boundary is separate from Media3's guarded native HTTP transport.
- Arbitrary third-party LibVLC network URLs remain disabled in release; current experimental progressive LibVLC uses guarded local file descriptors.
- The SDK does not bypass DRM, authenticate on behalf of providers, steal credentials or grant rights to copyrighted media.

## Lifecycle and cancellation

The active inspection is cancelled when the session clears, closes or starts a newer inspection. Old candidate IDs cannot be reused after a clear. A stale inspection cannot produce a valid Verified result.

Caller-initiated coroutine cancellation is propagated, rather than converted into a misleading source failure. The Extras UI also retains its own generation guard and inspection-job cancellation.

## Tests

MediaForgeSessionTest covers: request/budget validation; bounded source discovery; opaque ID stability/isolation; clear/close and cancelled probes; superseding newer inspections; unobserved source rejection; rejection of unsafe inspector results.

Existing MediaForge WebBridge, Media3, LibVLC, Android Enterprise and emulator tests remain required. No real-device qualification is claimed.

## Later phases (not implemented here)

1. Move the native player ownership and adapter lifecycle into a shared player contract.
2. Improve the WebView provider-origin strategy and supply-chain controls.
3. Add per-session network limits and redacted telemetry.
4. Publish a separately versioned mediaforge-api/AAR after testing binary compatibility and an independent sample host.

## Phase 2 — common transport and navigation policy (November 2026)

- All native MediaForge probe and Media3 requests now inherit a common *per-network-hop* interceptor. It rejects non-HTTPS and credential-bearing Cookie, Authorization, Proxy-Authorization, X-Api-Key and X-Telegram-Token headers before network transmission; Range/If-Range and signed query URLs remain supported.
- WebView main-frame provider/title/episode navigation rules are centralized in a pure core policy with JVM regression tests; the existing WebBridge calls that policy. The original user-selected provider host and fixed episode remain locked.
- The WebView interception hook blocks file:, content:, intent:, ftp: and unknown resource schemes it actually encounters. Browser-local about:, data: and blob: behavior is preserved; WebView file/content access settings remain disabled.
- These defenses do **not** guarantee that WebView enforces DNS pinning or intercepts every HTTP redirect or blob/javascript resource. Chromium does not call shouldInterceptRequest for every scheme and redirects may be invisible to that callback. The browser remains untrusted and separate from guarded native media requests.
- Tests are deliberately offline: no third-party streaming website, browser cookies, secrets or real Telegram accounts are needed.

