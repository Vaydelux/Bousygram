# Extras WebView Regression Qualification — 2026-11-21

## Scope and evidence

**Reported:** TMDB metadata/catalogs load, but in-app Android WebView playback stays black across nearly all Anime sources and possibly Movies/TV. Selected sources play in the external browser, where user taps also trigger numerous advertising pop-ups. No captured provider-specific WebView stack trace or rendering measurement exists. Do not claim a particular provider's problem is fixed based on compilation or emulator fixtures.

**Relevant history:** PR #226 switched WebView to `setSupportMultipleWindows(true)` while declining child-window requests to stop ad popups from replacing the selected player. Android documents that `false` routes `window.open()` and `target=_blank` to the top-level WebView, and recommends `true` without `onCreateWindow` for popup safety. That security improvement can change provider behavior, because some player initialization uses new-window interactions. See:
- https://developer.android.com/reference/android/webkit/WebSettings#setSupportMultipleWindows(boolean)
- https://developer.android.com/develop/ui/views/layout/webapps/webview
- https://developer.android.com/reference/android/webkit/WebView#getCurrentWebViewPackage()

**Confirmed code issue:** Anime alone had a manually accessible cookie/autoplay compatibility toggle even though Movies/TV use the same WebView.

## Implemented recovery architecture (PR #227)

| Mode | Cookies (third-party) | Autoplay | Multiwindow | Allowed navigation |
|---|---|---|---|---|
| Strict (default) | Denied | Gesture required | Enabled; no child window handler | Selected host, path, title/episode identity |
| Compatibility (explicit) | Allowed for selected WebView | Allowed | Enabled; no child window handler | Same strict navigation policy |
| Legacy routing (explicit after compatibility) | Allowed for selected WebView | Allowed | Disabled (original routing) | Same strict navigation policy |

- Never enable automatic JS-created windows. No `onCreateWindow` popup host is provided.
- Never suppress TLS errors, permit mixed HTTP, local addresses, files/content access, or cross-origin top-frame redirects.
- No JS injection, DRM circumvention or native stream extraction from iframe-only providers.
- Mode changes cancel pending native inspections, clear stale stream proposals and remount the WebView/bridge so new settings are applied before provider scripts execute.
- Failed ad resources and blocked navigations are non-fatal diagnostics, not proof that video failed. Surface hints outside the video area after the existing delay.
- Display only the public WebView package/version when recovery is shown; never expose provider console text, signed URLs, cookies, device identifiers or user account details.
- Strict mode is the privacy-friendly default. Switching is local to the selected provider session and reversible.

## Validation gates

1. **Unit:** `ExtrasEmbedRecoveryPolicyTest` checks blank-recovery logic for every Extras type and prioritization of blocked navigation vs codec/iframe errors.
2. **Android WebView settings:** `MediaForgeWebBridgeInstrumentedTest` checks strict/compat/legacy modes, opt-in required for legacy, disabled automatic popups, preserved HTTPS/local-network policy, and title/episode identity pinning.
3. **Offline Compose:** `ExtrasWebPlaybackModeControlsInstrumentedTest` checks the strict → compatibility → legacy → compatibility → strict action path without the internet.
4. **Full CI:** Android Enterprise, responsive UI emulator, Cast/media emulator must pass on the same head SHA. No merge on failures.
5. **Physical devices:** Test at least one phone and Android TV in portrait/landscape when supported; check D-pad/cursor clicks, popup isolation, fullscreen, audio, pause/resume, orientation, provider switching, and Back.
6. **Provider matrix:** For a legitimately accessible test episode and licensed sample, record whether selected Anime, Movie, TV provider (a) loads its top-level page, (b) presents a Play control, (c) displays a first video frame, (d) plays audio, (e) plays for >60 sec, (f) displays unwanted ads/popups, and (g) restores on resume.
7. **Settings matrix:** Repeat strict, compatibility and legacy modes for the *same exact provider and episode*. If the external browser plays but all WebView modes fail, capture only the on-screen diagnostic category and WebView engine version; do not store source URLs or access tokens in logs.
8. **Unsupported requirements:** Report a provider's blocked iframe, unsupported DRM or unavailable codec honestly. Do not add a bypass or automatically open ad windows to force playback.

## Follow-up decision

This stabilization PR is a controlled recovery and observability fix, not a guarantee about third-party provider behavior. If 2+ unrelated providers remain black on the same device in all modes, investigate WebView engine version, device decoding capabilities and hardware-accelerated surface behavior *before* adding another provider-specific workaround. Preserve the feature freeze and leave Telegram Media unchanged.
