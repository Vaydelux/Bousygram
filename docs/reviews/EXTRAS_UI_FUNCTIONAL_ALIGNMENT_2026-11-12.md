# Bousygram — Extras UI / functional alignment follow-up

Date: 2026-11-12
Base: `main` at `682db330193d73f91e80656d8fbbd7f1843a9ed8`
Scope: targeted safety/behavior changes after the merged query-identity lock in PR #176.

## Findings corrected in this change

| Severity | Behavior | Correction | Regression gate |
| --- | --- | --- | --- |
| P1 | The registered Cinezo **anime** URL contained an AniList title ID but no episode ID. Picking episode 4 or 5 would load the identical URL, so the app could not honor the requested episode. | Keep the provider entry for future integration, but mark its title-only anime template **not selectable** and hide it from the native anime source picker. Reject calls to its episode URL builder. | JVM URL-identity tests and TV picker instrumentation. |
| P1 | Some anime providers omit a `{season}` segment. Passing a non-default season silently loaded the default-season URL. | Reject non-default seasons unless the chosen anime URL template includes `{season}`. | JVM test covers rejected VIDY season 2 and supported FILMU season 2. |
| P2 | Cinezo's explicit query options requested embedded PiP, Cast, and download controls, conflicting with the app's no-PiP and controlled-Cast design. | Request `pip=false`, `chromecast=false`, and `download=false` where this provider exposes such parameters. **Upstream compliance is unverified.** | JVM URL parameter test. |
| P1 | The Extras metadata HTTP transport accepted arbitrary URLs before opening the connection, even though present call sites use fixed public TMDB/AniList HTTPS endpoints. | Enforce the shared public-HTTPS, non-local-host, default-port URL policy at the transport boundary. Preserve disabled redirects, cancellation and bounded bodies. | JVM test verifies no socket opens for HTTP, localhost, private IP or custom port. |
| P1 | Responsive UI smoke used narrower pull-request path filters than push: Extras, Settings and UI resource edits could bypass this visual release gate. | Match pull-request UI paths with the complete presentation/resource/brand surfaces. | GitHub Actions responsive UI workflow is now triggered for those changes. |
| P2 | The one-time debug dispatcher for merged PR #173 remained in the workflow catalog after its branch had already been removed. | Delete the obsolete dispatcher rather than retain an unnecessary workflow with write privileges. | GitHub workflow inventory and actionlint. |

## Cross-layer alignment contract

- **Catalog identity:** TMDB movie/TV IDs never become AniList IDs, and an anime episode picker only exposes sources that encode the selected episode.
- **Navigation safety:** The selected embed's path and identity-bearing query fields remain fixed within each provider's WebView session (PR #176).
- **Isolation:** Extras WebView/native player remains separate from Telegram TDLib media playback and associated account data. Third-party providers are **not** guaranteed to load, comply with embed options, or be available.
- **Device posture:** UI themes, focus behavior, and the player's manual Pause/audio-focus semantics are not modified by this change.
- **Debug provenance:** The preceding `v1.0.0-debug.manual.8cc270f69b92` release was built **before** this change and does not include these corrections. A newly qualified debug APK is needed to test this work.

## Remaining non-automated release blockers

1. [Issue #142](https://github.com/Vaydelux/Bousygram/issues/142): the TDLib foreground download may exceed the eviction-target cache budget on an 8 GB TV; do **not** equate the Settings target with a hard download limit. Prototype and physically qualify bounded foreground byte ranges rather than deleting active media.
2. Real ARM32/ARM64 TV playback (including MKV/TS random-range seek, LibVLC/Media3/mpv transitions and subtitle D-pad focus), phone rotation/safe-area checks and 30–60-minute memory/storage evidence.
3. Production signing and signed candidate qualification under the protected release workflow. Automated CI or an emulator debug APK is not proof of these outcomes.

Release decision: **qualified debug candidate after CI passes; not a stable production lock**.
