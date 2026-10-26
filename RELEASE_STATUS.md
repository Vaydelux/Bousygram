# Release Status: Build Verified; Device Validation Pending (v1.0)

**Last Updated**: 2026-10-27
**Build Status**: GREEN (All 227 unit tests passed, lint passed, R8 hardened release assemble succeeded)
**Target Performance Floor**: Xiaomi TV A Pro 55 2026 (Quad Cortex-A55 @ 1.5 GHz, Mali-G52 MC1 GPU, 2 GB RAM, 8 GB eMMC flash, 32-bit Android 11 OS)

---

## 1. Verified Release Deliverables (`dist/`)

The following production artifacts have been compiled, optimized with R8, verified for TDLib native coverage (`armeabi-v7a` and `arm64-v8a`), and staged in `dist/`:

| Artifact | Architecture | File Size | Target Devices |
|---|---|---|---|
| `Bousygram-v1.0-release-armeabi-v7a.apk` | 32-bit ARM | 46.6 MB | Xiaomi TV A Pro 55 2026, Fire TV Stick HD/Lite, Mi Box/Stick, 32-bit Android TVs (Performance Floor) |
| `Bousygram-v1.0-release-arm64-v8a.apk` | 64-bit ARM | 54.7 MB | Chromecast with Google TV 4K, Nvidia Shield TV, Sony Bravia, modern 64-bit Android TVs and phones |
| `Bousygram-v1.0-release-universal.apk` | Multi-ABI | 151 MB | Universal fallback containing both 32-bit and 64-bit native binaries |

---

## 2. Test & Verification Matrix

- **Unit Tests**: 227 / 227 passing (`:app:testDebugUnitTest`).
  - `PlaybackCoreLifecycleTest`: Single active session lifecycle, background pause, clean release.
  - `PlayerInputControllerTest`: Remote key handling matrix (all 12 D-pad rows verified).
  - `BlackScreenDetectorTest`: Stuck decoder detection (>3.5s), tiered recovery (`TextureView` -> `MPV HW` -> `MPV SW`).
  - `CacheManagerTest`: 2-phase ordered LRU eviction, dynamic quota bounds, active session protection.
  - `CastReceiverManagerTest`: Lightweight Cast payload serialization, deep links, intent parsing.
  - `SeriesMetadataParserTest`: TMDB and regex matching for multi-season/episode series.
- **R8 Proguard & Resource Shrinking**: Passed without warnings (`minifyReleaseWithR8`, `optimizeReleaseResources`).
- **TDLib Native Coverage**: Passed (`verifyTdlibAbiCoverage` confirmed non-empty `libtdjni.so` for `armeabi-v7a` and `arm64-v8a`).
- **TV Hardening Contract**: Passed (`verifyTvHardeningContract` rejects PiP or inferred-series UI regressions).
- **Signing**: All hardened ARM32, ARM64, and universal APKs passed APK Signature Scheme v2 verification with the same configured certificate.

Physical 32-bit/64-bit TV playback, real Telegram CDN behavior, remote focus visibility, and long-session lifecycle tests are not proven by these local build results and remain release acceptance requirements.

---

## 3. Architectural Overhaul Highlights (Phases 0 - 7)

1. **State Machine (`PlaybackCore`)**:
   - Eliminated dual concurrent playback states (`VideoSurfaceView` and `PlayerScreen`).
   - Single active playback session invariant strictly enforced across process lifecycle.
2. **D-Pad Remote Input & Auto-Next**:
   - Deterministic remote handling (`PlayerInputController`) with 50ms debouncing.
   - 5-second Auto-Next Countdown Card with default focus on "Play Now ▶". Back button cancels countdown without exiting the player.
   - Lightweight high-contrast focus rings (`TvFocusSurface`) replacing heavy GPU shadow blur passes.
3. **Playback Robustness & Diagnostics**:
   - Hardware codec pre-probing (`CapabilityProber`) prevents playing unsupported 10-bit formats on hardware decoders that crash.
   - Watchdog black-screen detector (`BlackScreenDetector`) triggers automatic tiered recovery upon stuck frames.
   - Diagnostic `EngineLabActivity` registered under `com.vaydelux.bousygram.DEBUG_ENGINE_LAB`.
4. **Smart Caching (`CacheManager`)**:
   - Dynamically bounds disk cache to `min(2 GB, 15% of free flash)` with a 256 MB safety floor.
   - Sliding read-ahead window (32 MB) and instant seek cancellation prevent bandwidth contention.
   - Next-episode 80% duration head preload (8 MB) for instant episode transitions.
5. **Continue Watching**:
   - Unified single source of truth (`ContinueWatchingRepository`) combining JSON persistence with Room entity tracking.
6. **Zero-Overhead Cast Receiver**:
   - Cast Connect intent/deep-link protocol (`com.vaydelux.bousygram.ACTION_CAST_LOAD` and `bousygram://cast/load`) without heavy Google Play Services dependencies.
