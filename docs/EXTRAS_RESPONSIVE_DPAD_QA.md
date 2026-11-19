# Extras responsive & D-pad quality contract

## Purpose
Prevent regressions reported in phone screenshots: oversized repeated
detail headers/backdrops in landscape, long raw reviews, ambiguous remote
focus, and tall provider lists.

## Viewport policy
Use the current Compose window bounds in **dp**, adjusted for font scale.
Never choose a layout from CPU ABI or raw screenshot pixel count.

| Viewport (dp) | Device | Detail artwork | Intro behavior |
|---|---|---|---|
| 320×568 | Compact portrait phone | Short hero | Small poster + metadata |
| 393×852 | Portrait phone | Short hero | Small poster + metadata |
| 640×360 | Small landscape phone | Omit repeated hero | 96dp poster, early Play |
| 720×400 | Landscape phone | Omit repeated hero | Early Play + compact reading |
| 915×412 | Wide landscape phone | Omit repeated hero | Collapsible reviews |
| 840×1180 | Tablet | Medium hero | Medium poster + readable body |
| 854×480 | Compact TV | Cinematic hero | Focusable primary action |
| 960×540 | Common TV dp canvas | Cinematic hero | Focusable rows and controls |
| 1280×720 | Expanded TV | Cinematic hero | Spacious browse and focus |
| 1920×1080 | Large TV canvas | Cinematic hero | No oversized body text |

A physical 4K display commonly exposes a much smaller *dp* canvas.
Respect status/navigation bars, device font scaling, and usable pane width.

## UI and interaction contracts
- Startup theme/style/backdrop selections also govern Extras and native
  playback controls. Playback progress uses a shared accessible palette.
- On short landscape phones, never let two ornamental images push the
  primary action out of the first meaningful scroll region.
- Portrait detail banners avoid repeating a large title in the hero,
  metadata, and navigation header.
- TV retains cinematic artwork at 960×540dp; phone landscape rules never
  mistakenly classify TV screens.
- Opening a Related title resets detail scroll to the top.
- Reviews and synopsis start collapsed, expand on demand, and can be collapsed.
- Cast/provider/related rows are navigable with clear D-pad focus treatment.
  Provider cards represent metadata, not direct playback links.
- Center/Enter activates real Play/season controls; don't insert dead
  clickable surfaces. Touch controls still work.
- Posters retain their full portrait aspect ratio; image decoding and
  result paging remain bounded on 32-bit/low-memory devices.
- Text must remain readable under all theme/pattern combinations.

## Verification
- JVM: ExtrasDetailLayoutTest covers portrait/landscape phones, tablets,
  854dp through 1920dp TVs, and large font scale.
- Android: ExtrasDetailResponsiveInstrumentedTest exercises compact detail,
  movie Play via D-pad, and review/synopsis expansion.
- Existing Extras search, catalog, D-pad, player, theme and navigation
  tests remain required. Explorer and Cast emulator lists include new tests.
- Physical Android TV and phone QA remains required for production release.
  Emulator checks do not prove every OEM's focus algorithm, IME or cutout
  behavior. Report window dp, device, OS and active theme with new failures.
