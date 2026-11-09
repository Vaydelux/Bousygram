# Bousygram code, security, UI and theme review — 2026-11-10

Baseline: `main` commit `6929975e40e6bcb5b71d4a86c1bb7c0b37a8a269`.

This is a targeted, source-based hardening pass, **not** an exhaustive repository-wide
audit or physical Android TV certification. Inspected the repository's source tree,
main app activity/navigation, AndroidManifest, three theme presets/tokens/tests,
Home and shared navigation components, Telegram playback track/subtitle UI,
Extras catalog and MediaForge WebView/virtual cursor, Cast entry and account-bound
local media authorization, plus CI workflow design.

## Findings fixed in this PR

| Severity | Area | Observed issue | Change |
| --- | --- | --- | --- |
| P1 | TV track/subtitle dialog | Hardcoded navy background, cyan focus border and white text ignored selected Light / High Contrast schemes | Use semantic surface, container and matching `on*` roles for focused/selected/idle states |
| P1 | Extras subtitle dialog | Hardcoded navy/white/cyan styling likewise ignored theme | Same semantic roles, preserve large TV targets and focus behavior |
| P1 | EmbedHeaven legacy navigation | Main-frame query changes on `/embed?id=550&type=movie` were allowed to change the chosen title/type while retaining the same path | Pin legacy `id` and `type`; reject duplicate identity parameters, keep provider `server` selector query allowed |
| P2 | Home unread/topic count badges | 9sp and 10sp badge text, 16dp unread indicator on TV | Increase count text to 12sp and unread circle to 26dp |
| P2 | Extras navigation | Mode chooser claimed Extras was metadata-only, despite optional web players and Live TV | Clarify provider ownership and isolation from Telegram playback |
| P2 | Live TV input | Paste could grow an invalid channel slug without a UI length bound | Bound editing to 80 characters in addition to existing slug validator |
| P1 | Instrumentation regression | Mode-selection test used an obsolete Extras label and caused a genuine emulator smoke failure (1/57 tests) | Use stable button tags to keep UI checks independent of marketing copy |
| P1 | Title-bound embed navigation | Generic `/embed/` redirects could switch TMDB IDs or episode paths | Pin explicit `/embed/movie/...`, `/embed/tv/...`, and `/embed/anime/...` paths and selected TV episode query parameters |
| P2 | Recent-search controls | Remove-search target was only a few pixels of clickable padding, with muted small text | 48dp accessible hit target with action label, larger count text and semantic foreground |
| P2 | Common TV buttons | Selected label text could have weak contrast on translucent fills | Use `onSurface` and 48dp minimum control height |

Regression coverage: theme role contrast in all presets and instrumentation checks
for legacy embed's same-title server parameter, blocked cross-title/content-type
navigation, duplicate IDs and foreign hosts. Note that `assembleDebugAndroidTest`
compiles instrumentation tests but **does not run them on physical hardware**.

## Confirmed existing guardrails

- Android backup and cleartext traffic are disabled in the manifest.
- Extras WebView disables local file/content access and third-party cookies,
  requires HTTPS for network URLs, and handles renderer loss without invisible retry loops.
- Extras browser/native sessions are separate from Telegram's authenticated playback path.
- Local streaming capability URLs are account-scoped and can be revoked; no unauthenticated
  file ID should be served merely because a URL was constructed.
- UI supports Dark, Light and High Contrast presets; core semantic contrast
  tests were already present before this change.
- TV D-pad virtual cursor has bounded position, repeat-based acceleration, and
  a dedicated fullscreen pathway.

## Remaining review items (not fixed in this PR)

1. **P1 — Real-device qualification:** Run remote-control focus traversal,
   WebView fullscreen and cursor interactions, track/subtitle picker contrast,
   low-RAM ARM32 soak and MPV/Media3/LibVLC seek/pause transitions on hardware.
   CI compilation and unit tests alone cannot establish these behaviors.
2. **P1 — Provider/navigation compatibility:** MediaForge allows same-host
   `/embed/` path-to-path navigation for *unrecognized* provider path shapes.
   Known title-bound `/embed/movie/{id}`, `/embed/tv/{id}` and
   `/embed/anime/{id}` paths and the season/episode query keys are now locked.
   Investigate provider-specific allowlists for remaining unrecognized routes
   without breaking legitimate embedded server selectors.
3. **P1 — Credential handling:** Verify TMDB integration and key-management model:
   secrets embedded in *any client APK* can be extracted, even when GitHub CI hides
   the secret. If the key must remain confidential, use a server-side proxy and
   rate limiting; public app keys should be treated as public and restricted.
4. **P2 — Design-system adoption:** TV overlays and decorative logo glyphs
   elsewhere still contain fixed colors or text sizes. Audit the remaining
   `PlayerScreen`, `EnterpriseComponents` and `TvHybridHomeDashboard`
   surfaces under all three themes at high font scale.
5. **P2 — Accessibility/localization:** Catalog and player strings are still
   frequently inline. Move user-facing copy into resources gradually and test
   TalkBack descriptions, large fonts and 720p TV-safe content margins.
6. **P2 — Long-session memory/energy:** Compare activity and cached bitmap
   memory before and after extended browsing; do not optimize blindly around
   absent physical profiling evidence.

## Validation required

- GitHub CI: `Android Enterprise Checks`, `LibVLC Integrated ABI Gate`,
  `TDLib Java API generation`.
- Android Enterprise invokes `:app:compileDebugKotlin`, `:app:testDebugUnitTest`,
  `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, and related checks.
- Emulator: rerun the mode-selection instrumentation test against its stable
  `mode-extras` and `mode-telegram` tags (the initial label-dependent test
  failed in run 42662105259; this must pass before merging).
- Manual: themes (Dark/Light/High Contrast), 720p and 4K TV focus,
  Extras subtitle choices, Legacy EmbedHeaven movie switching, WebView safe
  navigation, system Back, RAM usage and autoplay/seek reliability.

Do **not** interpret green CI as external-provider availability, content license
verification or physical-device qualification.
