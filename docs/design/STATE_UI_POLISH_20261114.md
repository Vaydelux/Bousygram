# Bousygram — UI-only state polish

## Decision and scope

Keep the existing layout, palette values, adaptive breakpoints, and focus graph. The three brand-neutral design documents offer a useful **presentation consistency** target, not evidence that an all-new screen arrangement works better.

This PR improves loading, empty, recoverable-error and detail feedback **in place**. It does not change user state machines or reorder navigation/actions.

## What changes

| Existing surface | UI refinement | Behavior explicitly preserved |
| --- | --- | --- |
| Shared `EmptyStateView` in Telegram/Search/Favorites | Semantic heading emphasis and stable identifiers for title/message/action | Same text, sizing, positions, callbacks, and focus |
| Favorites partial-data warning / legacy-import error | Accessible semantic warning or error surface with theme-aware foreground and background | Import retries, refresh, data and page state unchanged |
| Extras catalog fetch error | Themed recoverable inline error banner | Same retry callback; grid memory/pagination unchanged |
| Extras catalog zero results | Consistent concise centered heading and explanation | Same Clear and Next page actions |
| Extras missing TMDB configuration | Shared centered typography | Same Browse Anime action; independent AniList |
| Extras detail load failure | Consistent error title, body and Retry | Same back path and retry counter |
| Extras episode loading/failure | Small themed spinner/label and inline error banner | Same selected season, episode results and retry action |
| Extras poster/detail artwork loading error | Theme surface placeholder rather than transparent blank | Same Coil request size/caching/network behavior |
| Extras detail metadata | Explicit `onSurface` / `onSurfaceVariant` colors | Same TMDB/AniList values and episode/player actions |
| Extras skeletons | Static surface container and border roles; loading accessibility state | Same poster geometry, no animation or extra decoding |

## Component contract

`BousygramInlineStatus` and `BousygramCenteredStatus` use existing Material 3 color roles. Tone is presentation only (neutral, warning, error). Buttons remain owned by callers via `trailingAction` or adjacent native controls. The components do not execute I/O, control the player, manage focus, navigate, store settings or retry automatically.

Do not treat a missing poster as unavailable playback. A background placeholder only reflects *artwork loading*, not a media/provider status.

## Qualification

- All ten themes: text must be readable against error/warning/normal state backgrounds; no default Android black text.
- Native D-pad/OK: Retry and Clear remain reachable. Back stays controlled by the existing screen.
- Compact 320dp phone and short Android TV: catalog grid still dominates viewport; inline notices never own focus; loading skeletons do not run continuous animation.
- No network calls in presentation component tests, and no change to provider, content policies, Telegram, cache, seek, player engine, Cast or WebView cursor.
- Enterprise + responsive UI + full Cast/media emulator workflows required on the **exact PR commit**. Physical TV and phone visual review remains separate from emulator proof.

## Rollback

The PR is an isolated presentation change. Reverting it restores prior messaging without any migration of Telegram, Extras or saved theme data.
