# Native player loading and controls

Bousygram's Android Compose player draws inspiration from Vidstack's clear display/loading state separation without bundling Vidstack's browser/React runtime.

- Opening a media item renders a quiet shimmer skeleton; the native video surface is still created and continues preparing behind it.
- Reconnecting and rebuffering are **non-modal**, delayed (350 ms) status banners. Quick cache hits do not flash a spinner or cover the video. A playback error hides all loading overlays so the existing Retry action stays visible.
- Player controls use a dark translucent dock with clearly separated transport and secondary track/decoder/settings controls. The native timeline retains buffered progress, thumbnail preview, D-pad seek and 44 dp hit target.
- Favorites reuses the established responsive BrowseGridShimmerSkeleton while initially loading. Empty and error states remain unchanged.
- No new media engine, network polling, autoplay, unknown IDs, or extra downloads are introduced for these visuals. Android TV input mapping, Cast policy, session cleanup, and signed release gates remain unchanged.

## Acceptance tests

`PlayerLoadingPhaseTest` covers priority and error behavior. Existing PlayerInputController and core engine tests ensure transport/seek semantics. Android emulator tests cover baseline launch and navigation but **cannot verify authenticated Telegram playback or buffering UI under live network failures**. On physical phone and TV, verify opening > 350 ms, fast cache hit, rebuffer, disconnect/reconnect, a fatal playback error, focus navigation, seeking, and TalkBack announcements with real media.
