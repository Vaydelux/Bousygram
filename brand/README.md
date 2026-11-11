# Bousygram — approved brand identity

The Bousygram brand uses the **orange Chow Chow**, **cyan-to-blue circular halo**, and **white Telegram-style paper plane** from the user-approved artwork. Always use this mascot as the source for app identity; never substitute the former TV/play/signal motif.

## Canonical assets

| Use | File | Notes |
|---|---|---|
| Android adaptive foreground | `app/src/main/res/drawable-nodpi/ic_bousygram_mascot.webp` | Approved Chow + plane, cropped for icon legibility |
| Android adaptive background | `app/src/main/res/drawable/ic_bousygram_adaptive_bg.xml` | Deep midnight navy |
| Android monochrome | `app/src/main/res/drawable/ic_bousygram_mark.xml` | Simplified one-color Chow + plane for Android themed icons |
| Android pre-26 launcher | `app/src/main/res/mipmap-anydpi/ic_launcher.xml` | Uses the **same** mascot WebP; no stale density PNG duplicates |
| Android API 26+ launcher | `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | Same foreground, background and monochrome |
| GitHub README | `brand/bousygram-mascot.webp` | Same artwork, not an unrelated stock image |
| In-app TV, tablet and phone headers | `BousygramBrandMark` | Uses the exact approved mascot drawable; never a generic play icon |
| Browser favicon | `brand/favicon.ico` | Multi-size ICO (16 and 32 px) derived from the approved mascot |

Android's `AndroidManifest.xml` points to `@mipmap/ic_launcher`, which chooses the appropriate launcher implementation by API level. The app's TV banner is a separate wide asset; it must be visually reviewed on real launchers rather than stretched from the square icon.

## Why launchers can still look slightly different

The full-color mascot source is identical between the launcher and in-app
headers. The favicon is a *downscaled* 16/32px derivative. Android launchers
apply platform-specific adaptive-circle or squircle masks, and the Android TV
launcher displays a separately composed **16:9 banner**. Consequently, the
source design is unified, but the platform may crop it differently. Do not
stretch a square favicon into a TV banner or pretend the different aspect
ratios are pixel-identical.

`tools/ci/verify_brand_identity.sh` enforces source equality between the
README and Android mascot, verifies the launcher resource references,
requires the canonical in-app component, and validates multi-size ICO frames.
The verification does not claim pixel-identical cross-platform cropping;
that requires the screenshot/device checklist in
`docs/ci/UI_VISUAL_QUALIFICATION.md`.

## Design tokens

`BousygramTheme.kt` defines seven semantic presets: Midnight, Aurora, Obsidian, Evergreen, Ember, Daylight and High Contrast. `BousygramUiTokens.kt` owns shared focus, spacing, corner, motion, and brand-accent constants.

- **Navy:** #061426 — deep background
- **Cyan:** #37D7F2 — identity and interactive accent in dark themes
- **Blue:** #2289F9 — halo/accent secondary
- **Gold:** #FFC67D — warm mascot accent
- **Light ink and surfaces:** semantic Material 3 `onSurface` / `surface`, rather than hardcoded white-on-white or black-on-black pairs

**Accessibility requirement:** each text foreground must use its corresponding opaque semantic surface `on*` color (contrast ratio at least 4.5:1 for ordinary text); tests in `ThemePresetTest.kt` enforce the primary pairs. On video, put labels on an opaque-enough Material surface/scrim so text stays readable. Keep the D-pad focus ring visible in all themes.

## Resolution policy

A real ICO is a *small multi-resolution icon*, not an 8K image. Retain a high-resolution source for artwork and marketing output; generate 16/32 px favicon renditions, 108 dp adaptive resources, and a separately composed 16:9 Android TV banner. Upscaling a source to 8K does **not** add native image detail. Never package an 8K icon in the APK solely for launcher rendering: it wastes storage and memory.

## Verification

- Confirm both pre-26 and modern adaptive launchers show the same Chow identity.
- Check Android 13+ themed/monochrome icon legibility.
- Confirm icon padding/cropping and the TV banner on ARM32 and ARM64 launchers.
- Check Daylight, Midnight blue, and High contrast text/background contrast and D-pad focus.
- Run `:app:lintDebug`, unit tests and the Android ABI matrix before publishing a new debug build.

Icons, banners, and theme changes must not modify media fetching, Telegram credentials, playback files, or login flows.
