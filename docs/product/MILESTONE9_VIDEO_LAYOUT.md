# Milestone 9 — LibVLC aspect-ratio parity

For fresh installs, LibVLC now supports three explicitly selected sizing modes:
- **Fit** (default): `setAspectRatio(null)` and `setScale(0)`, preserving the whole image and letterboxing where needed.
- **Zoom**: get the current decoded video track, including sample aspect ratio, and scale until the viewport is covered. Edges can be cropped.
- **Stretch**: select the current view aspect ratio to fill the viewport, knowingly changing image proportions.

The original selection is retained through late video-track availability, viewport resizes,
new video surfaces and native VLC vout recreation. Reapply native layout on
`Playing` and vout notifications, without pausing or re-preparing media.

A pure Kotlin regression suite checks dimensions, sample aspect ratio, landscape,
portrait, invalid geometry, and unchanged layout no-op behavior. Device-dependent
VLC GPU/vout scaling still needs visible ARM32/ARM64 tests, especially rotated and
anamorphic content; a green JVM/emulator CI is not a substitute for visual qualification.
