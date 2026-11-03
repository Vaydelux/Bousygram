# Cinematic video player — Compose navigation and future roadmap

The active player uses `CinematicPlayerOverlay` with `CinematicGesturePad` and
`CinematicHairline`; the underlying `VideoSurfaceView` continues to render
Media3 ExoPlayer / mpv, and PlayerScreen retains playback, TDLib session,
fallback, Cast, progress and auth ownership.

## Design contract

- The video is full-bleed beneath the status/navigation bar region. Interactive
  text/buttons use status/navigation/cutout insets; the progress line sits on the
  physical window bottom edge.
- When controls are **visible**: minimal top-left Back/title, bottom-left
  Play/Pause, bottom-right **More**, contextual audio/track/seek actions in
  the expanded menu, and a 2dp crimson timeline with a touch thumb shown
  **only while dragging**. No default PlayerView controls.
- When controls are **hidden**: no floating transport buttons, no overlay
  gradients, only a 1dp translucent white progress hairline. Android TV
  Left/Right seeks immediately ±10s; OK reveals focusable controls.
- On **phones/tablets**: a single tap of the unobstructed picture toggles
  play/pause and exposes navigation; double-tap left/right seeks ±10s.
  Brightness and volume vertical drags remain on the picture surface.
- On **TV**: Up opens timeline focus; Down exposes the More focus target.
  Once visible, focusable controls and the options list can be navigated by
  D-pad. Back dismisses dropdown before hiding controls; only Back with
  hidden controls exits playback. Dedicated transport keys remain immediate.
- Menus keep the auto-hide timer paused, have accessible control descriptions
  and do not force seek or skip on focus. Playback errors keep Retry available.

## Contextual More menu (implemented)

- Audio & subtitles (existing track picker)
- Speed cycle
- Aspect ratio cycle
- Episodes & queue (when a source queue exists)
- Previous / next item
- ±30-second seek
- Start over for streams with trusted duration
- Playback engine selection (existing ExoPlayer/mpv fallback)
- Close menu

## Future menu groups (planning only — NOT implemented)

- **Playback**: explicit rate selection; advanced picture scaling; per-device
  decoder capabilities; only expose options supported by the selected engine
- **Audio & CC**: selectable audio languages, subtitle offset/size/style and
  remembered per-device preferences, without breaking embedded track selection
- **Navigation**: chapters/bookmarks, series episodes and pinned queues, once
  trustworthy timeline metadata is available
- **Diagnostics**: optional expert-only playback stats (network throughput,
  dropped frames and audio/video sync); ensure private Telegram identifiers,
  stream URLs and tokens never leave the device
- **Cast**: a future conditional entry, enabled only after receiver TLS,
  authentication and local-network security requirements are met

When adding new menu entries, keep them behind **More** and provide a label,
accessibility description, TV focus behavior, dismiss/Back behavior, and
disabled state for unsupported engines. Do not introduce fake chapters,
quality choices or subtitle tracks. All actions must call real PlayerScreen
handlers and be covered by a Compose interaction test.

## Required device qualification

The emulator instrumentation validates structure and input callbacks. It
does **not** prove a smooth physical Android TV decoder, HDR, low-memory
performance, or accessibility with an actual remote. Test both Android TV
ABIs plus phone/tablet with MP4, MKV and TS using
[the performance test plan](../ci/PLAYBACK_PERFORMANCE_TEST_PLAN.md). Keep
production release qualification blocked until real-device evidence exists.
