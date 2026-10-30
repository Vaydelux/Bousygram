# ADR-003: Compact Player and One D-pad Input Owner

**Status:** Proposed, 2026-10-31.

## Context
TV player buttons and overlays currently occupy too much screen space; Start Over and Back/Close behavior need regression tests. A large transport row with ±10s duplicates remote functionality and makes focus harder.

## Decision
- Compact native player chrome, not a web/Netflix control clone: Back, Start Over, title/status, honest timeline/current/total duration, play/pause, options. A separate settings/track panel may exist off the primary row.
- Existing PlayerInputController retains authority for directional seek and preview. Left/Right on video adjust preview, OK commits, Back cancels; no automatic media skip. D-pad Down opens same-source related-media tray; Up from its first row returns to player.
- Explicit priority ordering: blocking dialog > settings > tray > seek preview > HUD > surface. A key is consumed by only the highest-priority owning layer.
- Start Over is a distinct action that seeks to zero on current session and never dispatches Back/activity finish. Back closes current overlay first, then exits the player route, then navigates as defined by the app shell.
- Unknown duration, non-seekable streams and missing preview frames are displayed truthfully. Optional actual frame preview must not create unbounded downloads or synthetic imagery.

## Alternatives rejected
Giant primary ±10s transport row; UI widgets each independently intercepting D-pad; unbounded queue switches by Left/Right; fake progress for unknown-duration files.

## Consequences and acceptance
Requires state-machine tests and physical TV validation for long press, OK/Back preview, Start Over and tray focus. Preserve existing Media3/mpv fallback and playback lifecycle until separately approved. No blocking transient overlay covering active video on cache hits.
