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
