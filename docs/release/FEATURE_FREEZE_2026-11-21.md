# Bousygram Feature Freeze — November 21, 2026

## Decision

Bousygram is in **stabilization / no-new-features** mode. The goal is a trustworthy Android debug build, controlled regression repair, and verified behavior on phones, tablets, and Android TVs. A green compile or GitHub Actions run is evidence of a successful build, not physical-device quality approval.

This freeze is about *scope*, not a claim that the app is complete or free of bugs. It does not authorize a production release.

## Allowed work

- Fix reproducible UI, layout, D-pad/focus, playback, seeking, subtitles, account/session, offline cache, or accessibility defects.
- Fix security vulnerabilities, dependency incompatibilities, data-loss or privacy risks, and resource leaks.
- Improve automated tests, crash diagnostics, release checks, APK provenance, dependency pinning, or documentation.
- Remove or disable demonstrably broken Extras sources after verification; do not silently introduce new providers.
- Adjust performance only with measured evidence, especially for low-storage Android TV devices.

## Frozen work (requires a separate, explicit decision to unfreeze)

- New streaming providers, catalogs, services, authentication methods, and playback engines.
- New navigation surfaces, design systems, themes, or unrelated UI redesigns.
- Feature additions to Telegram Media, which is an approved/stable subsystem. Only fix a proven P0/P1 regression with a focused test.
- Broad architectural rewrites or deployment work unrelated to a release blocker.

## Regression gates

1. **Build identity:** the APK uses the expected app ID, version, signing mode, and supported ABIs. Verify native TDLib, libmpv, and LibVLC content rather than trusting filenames.
2. **Three debug variants:** ARM32, ARM64 and universal; independently verify signatures, package metadata, ABI inventory and SHA-256 hashes. Publish as immutable debug prereleases, not production releases.
3. **CI:** pass Kotlin compile, unit tests, lint, native binding compatibility, sensitive-log checks and version policy. Run emulator UI/playback checks where configured; do not represent unavailable tests as passed.
4. **Responsive Extras:** verify actual portrait and landscape UI at small-phone, large-font, tablet and TV viewport sizes; verify focus order, filters, results, Favorites, theme contrast and modal dismissal. Policy-only JVM tests are not sufficient.
5. **Playback and storage:** verify MP4, MKV and MPEG-TS start/seek/pause/resume, subtitles and cast-control overlaps. For the 8 GB TV class, monitor the rolling storage budget and reclaim behavior rather than assuming safety from a successful build.
6. **Telegram isolation:** run `tools/ci/verify_telegram_local_only.sh`; avoid changes to approved Telegram metadata, navigation or playback behavior without a reproducible regression.
7. **Real devices:** obtain ARM32/ARM64 TV and phone/tablet evidence. Do not call a debug APK production-qualified based only on CI.

## Release workflow policy

- **Dated Debug APK:** a manual, `main`-only route for dated ARM32, ARM64 and universal *test* APKs. It reuses `tools/ci/build_debug_preview.sh` and `tools/ci/verify_debug_preview_publication.sh` instead of maintaining a separate ABI packager.
- **Bousygram Debug APK Prereleases:** the existing deeper qualification path, including emulator instrumentation. Use it when emulator evidence is required.
- **Production release:** remains governed by `android-release.yml`, protected signing, release-device evidence and explicit approvals. Neither debug workflow bypasses these gates.
- All release jobs must match the exact current `main` source commit; do not replace release assets or retag existing published builds.
- Debug APKs may embed an extractable TMDB v3 key for Extras. No workflow should silently publish a trusted debug build with a missing or placeholder key.

## Current known qualifications

- The Extras layout policy has JVM coverage for 320dp portrait, compact landscape, increased font scale and Android TV, but real viewports/D-pad behavior require device testing.
- Native build, signing, and release packaging may be verified by CI; physical Android TV and extended playback qualification remain separate.
- Open tracking issues include bounded Telegram streaming on 8 GB TVs (#142), device-qualified stable release (#38), and optional Cast expansion (#220). Feature expansion is deferred during this freeze.

## Exit criteria

An explicit unfreeze decision only after critical regressions are resolved or documented, debug APKs have verified provenance, and representative hardware playback + responsive UI results have been recorded. Resume feature planning separately; do not infer unfreeze from a green workflow.
