# Bousygram — UI and Functional Release-Lock Review

Date: 2026-11-12  
Base: `main` at `a2182fc9d930bc480f9c9c483f430dab0be6646f` (post-PR #174; one branch)  
Release under review: `v1.0.0-debug.manual.c91797cfaa8b`

## Executive decision

**Conditional release candidate, not a final universal UX lock.** The prior qualified debug APK passed exact-head Enterprise, focused responsive-UI and full emulator instrumentation before merge, and the published ARM32/ARM64/universal APK packaging checks passed. This is strong *automated* confidence, not proof that every hardware/firmware configuration has good layout, reliable decoding, remote input, or third-party content availability.

Avoid another unbounded design rewrite. Freeze the visual language (canonical mascot, seven semantic palettes, focused surface, shared tab controls) and ship only targeted defects with regression tests.

## Confirmed source issues and remediation in this hardening PR

| Severity | Finding | Remediation | Verification |
| --- | --- | --- | --- |
| P1 | Extras search, filter, error, and grid states were laid out as direct Column children using `fillMaxSize()` without a single documented weighted results contract. Short TVs/landscape phones risk losing visible results as supplementary controls expand | `ColumnScope.ExtrasCatalogViewport` gives grid, loading, and no-results a single `weight(1f)` area | Android UI test with fixed 540dp height; grid must retain >=65% |
| P1 | Extras used `WindowInsets(0,0,0,0)` and mode screens ignored display safe areas; on targetSdk 37 phone edge-to-edge can place native buttons under status/navigation bars or cutouts | Use safe-drawing insets, top-bar status padding, and safe-area padding on standalone selector/startup/maintenance | Compile/lint + emulator renders; still verify different notches physically |
| P1 | Telegram forum showed `Retry topics` even during normal loading; More menu called it Retry without an error and allowed duplicate requests | Retry shown only when error exists; refresh text reflects normal state; disable refresh during in-flight loading | Focused Compose UI tests cover error and loading cases |
| P2 | Theme card showed `· Selected` suffix that clipped long palette labels | Semantically selected focus card; no title suffix | Theme gallery UI test |
| P2 | TV theme tabs, Extras poster titles, and theme previews used 11–14sp text that was marginal at viewing distance | Increase TV tab and poster titles to 16sp; theme tagline to 14sp; allow additional theme card height | UI smoke, screenshots and actual TV visual review |

## Other code paths inspected without speculative modifications

- The Telegram player has an explicit D-pad interaction controller, seek completion policy, and playback retry suppression policy intended to respect manual pause, Cast, and audio-focus loss.
- Telegram cache/pagination windows are bounded; Extras dormant poster lists are released on low-memory devices and Android memory trim.
- Extras MediaForge playback owns WebView/native engine transitions and intentionally supplies provider-switch/blank-screen recovery rather than pretending a remote embed always works.
- User preferences persist themes per installation. A selected theme is not synchronized across separate phones and TVs.
- Privacy mode in Settings is currently advertised for obscuring media posters and thumbnails. Changing source/channel name visibility or all metadata should be a separately designed privacy feature, not a silent behavior change.

These observations do **not** certify full physical device reliability. Anomaly monitoring and real-TV debugging remain necessary.

## What is intentionally **not** locked

1. **ARM32 Android TV devices:** actual 720p/1080p screenshots, focus traversal, buffered memory pressure over a 30-minute browsing/playback session and vendor firmware behavior were not tested in GitHub Actions.
2. **Portrait/landscape system UI:** large-font accessibility, navigation gesture and notch/inset behavior must be checked on physical phones; emulator assertions are an additional guard, not complete coverage.
3. **Third-party Anime/Movies/TV embeds:** providers may show a black screen or reject playback, even when routing and recovery UI tests pass. Manual server selection and safe error recovery are required.
4. **Codec cross-engine reliability:** .ts and .mkv seek, subtitles, hardware decoding and audio-focus recovery need on-device reproduction logs if issues recur. The emulator smoke suite does not cover every chipset/decoder.
5. **UI screenshot matrix:** current screenshot evidence covers selected emulator fixtures, not all pages at every width/density. Add device-matrix screenshots and baseline review before a fully stable public release.

## Lock criteria

- Exact hardening PR head: Android Enterprise Checks, Android Explorer Responsive UI Smoke, and Android Emulator Cast Smoke all succeed.
- Preserve the published visual evidence for Library, theme gallery, and mode selection. No screenshot artifacts missing.
- Verify rebuilt ABI-specific APKs and checksums from the **merged** app source, with the release tag pointing to that source.
- Physical 32-bit TV review: media grid visible immediately, no clipped labels, D-pad return path works; phone portrait/landscape safe areas and font scaling readable.
- On tests alone, label a build **qualified debug / release candidate**. Call it a **stable UI lock** only after device acceptance.

## Recommended follow-on work, not a blocker for this focused PR

- Add screenshots from actual 32-bit and 64-bit Android TV plus 320–480dp phones in both orientations.
- Add a reduced-motion/large-text test matrix and a 30-minute ARM32 soak with heap and frame pacing observations.
- Keep a small defect backlog (reproduction steps + photo/video + OS/build) instead of another universal UI rewrite.
