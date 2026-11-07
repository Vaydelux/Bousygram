# MediaForge Extras integration (initial implementation)

MediaForge is split into three Android library modules:

- `mediaforge-core`: bounded URL discovery and guarded stream preflight, HLS subtitle list parsing.
- `mediaforge-webbridge`: opt-in WebView resource observation and D-pad touch dispatch.
- `mediaforge-player-adapters`: standalone Media3 player item creation and native adapter extension point.

## Scope
Only Extras catalog details may launch this feature. Telegram PlaybackRequest, TDLib, PlaybackCore,
Telegram networking and account state must not be used or changed.

TMDB identifies a title, but **TMDB does not supply the media**. Third-party embeds can be unavailable,
geo-restricted, rights-restricted, non-playable or DRM-protected. No blanket provider compatibility claim.
The configured CineSrc example is a user-selected external embed, not an official TMDB integration.
Inspect licensing/provider terms before distributing a public build.

## Flow
1. User chooses movie or a TV episode on Extras details.
2. A scoped WebView opens the generated HTTPS embed URL. TV users toggle a D-pad cursor; touch users interact normally.
3. Resource URLs are observed on a best-effort basis. No JavaScript injection or hidden autoplay is attempted.
4. Discovered media candidates are shown. Selection explicitly triggers a bounded HTTPS probe.
5. On accepted preflight, the user enters a separate Media3 player. WebView navigates to about:blank to stop remote audio.
6. Back from native reopens the embed. If no direct URL is exposed, the user continues using WebView.

## Unsupported behavior
- No claim that CineSrc exposes a direct playable URL or SRT.
- Not a full Chromium network inspector: blob:/MSE, service worker or internal redirects can be invisible.
- No DRM bypass, cookie/token theft, rate-limit evasion, or circumventing provider access controls.
- No automatic media extraction from TMDB/AniList metadata alone.
- No LibVLC integration yet; the adapters module exposes a host-extensible contract.

## Validation
Run `./gradlew :mediaforge-core:testDebugUnitTest :app:testDebugUnitTest :app:compileDebugKotlin`,
then run the device emulator/TV tests and physical ARM32/ARM64 smoke suites.

## Recovery refinements

- Android lint now uses a localized string resource for WebView-unavailable fallback.
- Returning from native playback creates a new WebBridge instance, because closed resource observers must not be reused.
- Stale/canceled stream probes are generation-gated and cannot overwrite the latest source selection.
- These changes do **not** establish third-party player compatibility; encrypted, blob: and session-restricted sources may remain WebView-only.

- Added explicit **Reload player** and **Clear sources** controls in Extras; these invalidate pending source checks and clear stale candidates.
- D-pad center key-repeat is filtered to avoid duplicate pointer clicks during long presses.

- On rotation/layout changes, D-pad pointer coordinates are clamped to the current WebView bounds.
- WebView disposal is owned by the actual AndroidView instance and tolerates renderer failure.

## Native LibVLC option

The Extras picker now offers either Media3 or LibVLC. LibVLC is backed by
`ExtrasLibVlcSession` and never calls Telegram's loopback-only LibVLC engine.
It owns native resources and Android audio focus within the selected Extras
player's lifecycle, uses FIT aspect ratio, provides basic transport controls,
and attempts user-selected external subtitle attachment.

The previously planned generic `MediaForgeNativeAdapter` extension interface
remains available to other hosts; no LibVLC native dependency has been added
to `mediaforge-core`. Real-device verification is still required for LibVLC
stream playback, SRT loading, subtitle renderer, seek and engine behavior.

- Both Extras-only native engines pause upon Activity ON_STOP. They do not resume automatically when returning from background; the user can explicitly resume with the player controls.

## Embedded-player fullscreen and lifecycle

The WebBridge now supports HTML5 `WebChromeClient.onShowCustomView` / `onHideCustomView`: the browser's fullscreen video surface is displayed on top of the existing WebView, not in a new iframe. Android Back dismisses that surface before navigating away; disposal notifies Chromium. The host explicitly owns the fullscreen overlay, while the reusable WebBridge exposes callbacks.

The Extras WebView is paused/resumed on Activity `ON_STOP`/`ON_START` (best-effort only: untrusted cross-origin players may not honor WebView pause for all audio). Native engines retain their independent background-pause behavior. Neither implementation touches Telegram's media lifecycle.

### TV cursor in HTML5 fullscreen

A single reusable D-pad cursor component now targets both the normal WebView and the HTML5 fullscreen custom view. Entering HTML5 fullscreen automatically enables the TV pointer; OK synthesizes a user-initiated tap on the active video surface, and long-press directional keys accelerate pointer movement. This does not promise compatibility with all third-party players or cross-origin restrictions.

## Track metadata and low-memory resource observation

Explicit external HLS subtitle tracks now retain playlist LANGUAGE and NAME attributes (when present) in the MediaForge result. Media3's external subtitle configuration uses those values. WebVTT/SRT URLs without metadata still use a host fallback. HLS rendition playlists with their own .m3u8 tracks remain handled by the native player rather than being misidentified as standalone subtitle assets.

WebBridge limits pending main-thread resource observation callbacks to 32; excess observations are dropped without affecting page playback. This keeps callback backlogs bounded on ARM32 devices. Discovery remains best-effort, not a full Chromium network inspector.

## DNS rebinding defense for native preflight

MediaForge's standalone native source probe now uses OkHttp 4.12.1 with a private/reserved-address-rejecting DNS implementation. The socket connects to the checked resolved addresses (not a separate unchecked DNS lookup); ordinary TLS host/certificate verification remains enabled. Redirects are handled manually and independently checked at each hop, with bounded reads, no forwarded WebView cookies, and coroutine cancellation propagated to the HTTP call. This protects the native preflight, not third-party WebView pages or every downstream media segment request. Authorized provider allowlists and device testing are still recommended before public release.

## Guarded Media3 transport for downstream resources

Media3 now uses its own `DefaultMediaSourceFactory` backed by the SDK's
`MediaForgePlaybackTransport.client` through
`androidx.media3:media3-datasource-okhttp`. The guarded client handles
all Media3 HTTP reads, not just the initial source probe:

- HTTPS-only request/redirect checks and no HTTPS-to-HTTP redirect following
- DNS checks against private/reserved IPs for segment/CDN/subtitle destinations
- Direct connections without a system proxy that could bypass DNS filtering
- No inherited Telegram/TDLib data sources and no WebView cookies
- Native Media3 timeouts and explicit audio focus

The native LibVLC player uses VLC's own network transport and does **not**
inherit these protections. Until an equivalent allowlisted transport/proxy is
implemented for LibVLC, it remains experimental and is not qualified for
arbitrary untrusted embed URLs.

Security checks validate connection hosts, not whether a movie is licensed,
DRM-free or otherwise authorized for redistribution. CineSrc compatibility
remains unverified.

## LibVLC experimental gate and stream sanity checks

LibVLC is now **enabled only for debug builds**, through the `MEDIAFORGE_EXTRAS_LIBVLC_ENABLED`
BuildConfig flag. Release and hardenedRelease keep it disabled; native VLC does not inherit
the validated Media3 HTTP stack, and no secure VLC transport has been proven for arbitrary
third-party stream hosts. The Extras picker displays the disabled state rather than silently
relying on the native engine. This is a deliberate release safety boundary, not a claim that
debug LibVLC has equivalent network protections.

The core resolver now performs bounded container signature sanity checks on candidate MP4,
WebM and direct MPEG-TS responses, rather than accepting any HTTP 2xx (including HTML/JSON
error pages) as a native stream. This does not guarantee codec, subtitle or live-segment
compatibility and does not bypass provider authorization or DRM.

## Android emulator WebBridge tests

The existing `Android Emulator Cast Smoke` workflow now includes
`MediaForgeWebBridgeInstrumentedTest`. It runs real Android WebView initialization
against a reserved, non-routable `.invalid` URL and verifies JavaScript/domStorage
are enabled only alongside strict mixed-content rejection, file/content access
denial, disabled automatic popups, user-gesture-required media playback, and
third-party cookie rejection. It also checks that invalid HTTP, loopback, file,
and credential-bearing embed URLs are rejected before loading.

This tests Android WebView configuration and basic security only. It does
**not** qualify CineSrc, production streams, LibVLC on ARM32/ARM64, DRM, or
device-specific playback behavior.

## TV virtual mouse qualification (offline emulator)

`MediaForgeVirtualMouseInstrumentedTest` is now part of the Android emulator
smoke class list. It uses an actual Android `View` that records
`dispatchTouchEvent` invocations and confirms D-pad Right/Down movement,
one DOWN/UP tap pair on OK/Center, and bounds clamping.
The test exercises the **same `ExtrasVirtualCursor` composable** used by the
ordinary WebView and HTML5 custom fullscreen view.

This establishes Android emulator input behavior for the cursor component,
not real third-party iframe click-to-play behavior, device-specific D-pad
focus, or browser player compatibility. Continue physical ARM32/ARM64 QA.

## Guarded LibVLC progressive file-descriptor experiment

LibVLC now receives a **seekable, local Android ProxyFileDescriptor** rather than
a third-party network URL when the user selects native LibVLC. All reads for
eligible *progressive MP4/WebM* media use guarded MediaForge HTTPS range GETs
with strict 206/Content-Range validation. The device callback maintains a
bounded 256-KiB in-memory window; seeking reads a new protected range instead
of downloading the entire file. Authenticated/CDN sources that reject Range
requests are not supported and should use Media3 or WebView.

Selected SRT/VTT files are downloaded through the same guarded client into a
bounded private temporary cache file, attached to LibVLC as a local subtitle,
and deleted when the source is disposed. Native VLC never receives the remote
media or subtitle URL. No WebView cookies or Telegram tokens are forwarded.

The implementation remains **debug-only and experimental**: Android's
ProxyFileDescriptor FUSE behavior, HTTP request overhead, seeking performance,
and ARM32/ARM64 codec/device compatibility need qualification. HLS and DASH
are deliberately ineligible for LibVLC until a safe manifest/segment transport
is implemented; those formats remain supported by Media3. This is not yet
proof of production-safe LibVLC behavior for arbitrary untrusted content.

The guarded progressive source now sets a 30-second absolute HTTP call timeout, closes its Android proxy FD on setup exceptions, and has dedicated pure-JVM tests for Content-Range parsing and eligible formats. Enterprise CI runs the player-adapter JVM test task.

## Virtual pointer precision and key-event correctness
The Extras virtual mouse now applies D-pad movement as deltas against live Compose state to prevent rapid key events from overwriting each other before recomposition. OK/Center reads the latest coordinates; larger TV viewports get appropriately scaled movement and held directions accelerate. The cursor is a high-contrast white arrow with a dark outline and a hotspot at its exact pointer tip. The emulator interaction tests cover two immediate D-pad movements followed by OK and edge clamping.
