# Bousygram web / Kotlin-Wasm adoption plan

**Decision (November 20, 2026): Ship a browser PWA for QA now, share portable
logic later, and evaluate a Compose Multiplatform web client only after the
native/remote data boundaries are designed. Do not migrate Android wholesale.**

## What exists now (commit ae070335 and subsequent hardening)

The repository is an Android-first Jetpack Compose application with Android
TDLib/JNI, local encrypted credentials, Android Media3, libmpv/LibVLC, and an
Android MediaForge WebView bridge. These cannot be compiled directly for
Kotlin/Wasm or Kotlin/JS. The new `web/` folder is a **separate static
HTML/CSS/JS PWA**, not a full feature-parity app and not a Wasm bundle.

Current browser QA capabilities:

- Sixteen palette IDs matching `BousygramThemePreset` on Android.
- Local video playback using browser-native HTML5 codec support, themed seek
  progress, Play/Pause, fullscreen, WebVTT subtitle upload and local file picker.
- Responsive portrait/landscape/desktop presentation, offline application
  shell, keyboard/remote seeking and no media-file uploads.
- An SVG QR code linking to the planned GitHub Pages URL:
  https://vaydelux.github.io/Bousygram/
- PWA install guidance for Safari on iPhone and Chrome on Android.

The browser's selected palette is stored **only in that browser**. There is
no Android-to-web theme sync, Telegram login, catalog, or account data sync.
Playback codec support is browser-dependent; MKV/TS/AC3 and native libVLC
features are not guaranteed. Video files cannot be stored offline by the
service worker, which deliberately caches only the UI shell.

## Deployment: no paid backend

1. Repository Settings → Pages → Build and deployment → **Source: GitHub
   Actions** (requires repository owner/admin action).
2. The `Publish Bousygram web preview` workflow validates JS/JSON and
   publishes the static `web/` directory when it changes on `main`, or
   manually with `workflow_dispatch`.
3. Check that the actual Pages deployment succeeds **before** distributing
   `web/qr.svg`; it encodes `https://vaydelux.github.io/Bousygram/`.
4. On iPhone, scan the QR with Camera → open in Safari → Share → Add to Home
   Screen. Some older Safari versions may not support all modern media APIs.
5. Alternative: deploy the `web/` directory as static assets on Vercel.
   The included QR code is pinned to the GitHub Pages URL and would need
   regeneration if the public address changes.

Avoid any third-party QR generation service that receives private watch URLs.
This QR contains only the fixed public app URL.

## Android/native work to preserve

| Capability | Android now | Browser preview | Full web future |
| --- | --- | --- | --- |
| Telegram account and cloud Favorites | TDLib native | Not available | Secure web auth/API boundary required |
| Saved / Channels / Groups browsing | Android repositories | Not available | Portable domain model + platform adapter |
| Extras catalog | Native TMDB/AniList integrations | Not available | Public proxy / server-side credentials |
| Playback | Media3 / mpv / LibVLC | Browser-native local file | Separate browser player & licensed media handling |
| Theme and progress palette | Shared Compose provider | Matched public palette IDs | Generate tokens from a single source |
| Remote / D-pad | Compose focus APIs | Keyboard input | Explicit focus graph and WCAG contract |
| Private local media caching | Android account-scoped cache | UI shell only | Explicit user-consent storage design |

## Milestones

### W0 — Browser QA PWA (implemented)

Keep a static, account-free PWA. Verify 320/375/393/428px portrait, 667/844px
short landscape, 768/1024px tablet and 1280/1920px desktop/TV-like widths.
Test Safari on a physical iPhone, Android Chrome, keyboard arrows, fullscreen,
subtitle upload, 16 themes, dark/light readability and offline re-entry.
Never claim native codec parity or background downloads.

### W1 — Portable domain code (next, separate PR)

Create a Kotlin Multiplatform `shared-domain` module with **pure Kotlin**
types (media metadata, list filtering, error/result types, bookmark models,
appearance IDs and focus/navigation invariants). Keep Android libraries and
`Context`, `Uri`, JNI, `BuildConfig`, and `androidx.media3` out of `commonMain`.
Use Gradle version pinning and include JVM and JS/Wasm tests. Introduce
platform interfaces for storage, media providers, authentication and playback.

### W2 — Web API contract & security (future)

Implement explicit browser authentication, scoped sessions, rate limiting,
server-side handling of private API credentials, CORS and CSRF defenses.
Decide whether Telegram can legally and securely be accessed through a
supported browser-specific client or backend; do not expose TDLib session
databases, file capability tokens, raw chat logs, or API keys to hosted pages.
No unrestricted media proxy, SSRF fetcher, DRM bypass or arbitrary URL player.

### W3 — Kotlin/Wasm / Compose UI spike (future, optional)

Make a **separate** Compose Multiplatform web entry point and compare load
time, binary size, Safari/WasmGC compatibility, accessibility, text-input and
media interop against the existing PWA. Test a documented browser matrix
before deciding to replace web UI. Kotlin/Wasm/Compose for web is not a drop-in
build flag for this Android project. For an HTML-first product, Kotlin/JS for
shared logic plus a web-native UI may be the better maintenance trade-off.

### W4 — QR public beta (future)

Only after W2 and device qualification, enable authenticated media browsing
for approved testers and add onboarding. Track expected limitations and keep
browser previews isolated from production credentials.

## Release gates

- **Android:** compile, unit/lint, Saved/Channels/Groups/Favorites navigation
  tests on narrow phones and TV, both Telegram and LibVLC themed scrubbers,
  TV D-pad tests and 32-bit device memory testing.
- **Web:** Node built-in tests, syntax checks, static file integrity, iOS
  Safari tests, mobile accessibility (44px+ targets), keyboard, reduced motion,
  and successful Pages deployment.
- **Privacy:** zero uploaded files, no media URL leakage in QR or analytics,
  no sensitive caches, and local `blob:` URL revocation.

Useful upstream documentation:

- https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html
- https://kotlinlang.org/docs/web-overview.html
- https://kotlinlang.org/docs/multiplatform/compose-multiplatform-and-jetpack-compose.html
- https://docs.github.com/en/pages/getting-started-with-github-pages/configuring-a-publishing-source-for-your-github-pages-site
