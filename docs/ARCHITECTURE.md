# Bousygram System Architecture

## 1. System Overview

**Bousygram** (`com.vaydelux.bousygram`) is an enterprise-grade Android video streaming client designed for Android TV (Leanback / Jetpack Compose TV) and mobile/tablet devices. It bridges Telegram (TDLib) cloud storage, TMDB metadata grouping, loopback progressive streaming, and dual media engines (Google Media3 ExoPlayer and libmpv).

### Performance Target & Hardware Baseline
- **Primary Target Device**: Xiaomi TV A Pro 55 2026 / Google TV
- **CPU**: Quad-core ARM Cortex-A55 @ 1.5 GHz
- **GPU**: ARM Mali-G52 MC1 (low fragment throughput, sensitive to shadow blurs)
- **RAM**: 2 GB LPDDR4 (free memory ~350–450 MB under Android 11 TV OS)
- **Flash Storage**: 8 GB eMMC 5.1 (free user storage ~3.0–3.8 GB)
- **ABI**: 32-bit `armeabi-v7a` system environment

---

## 2. Architectural Pillars

```
+-----------------------------------------------------------------------------------+
|                            Presentation Layer                                     |
|  - Jetpack Compose TV + Adaptive Phone/Tablet UI                                  |
|  - PlayerInputController (Centralized TV Remote D-Pad Navigation)                 |
|  - TvFocusSurface (3.5dp High-Contrast Focus Borders, Zero Blur Passes)           |
+-----------------------------------------------------------------------------------+
                                    |
                                    v
+-----------------------------------------------------------------------------------+
|                           Core Media Subsystem                                    |
|  - PlaybackCore & PlaybackStateMachine (Serialized Lifecycle, Single Session)     |
|  - CapabilityProber (Codec & Container Pre-flight Routing)                       |
|  - BlackScreenDetector (Audio Watchdog, Automated Tiered Recovery)               |
|  - EngineVerdictStore (Persistent Hardware Decoder Database)                      |
|  - VideoSurfaceView (Dynamic SurfaceView <-> TextureView Compositing)            |
+-----------------------------------------------------------------------------------+
        |                                                   |
        v                                                   v
+--------------------------+                     +----------------------------------+
|      Player Engines      |                     |       Storage & Cache Layer      |
|  - ExoPlayerEngine       |                     |  - CacheManager (Unified Quota)  |
|  - MpvPlayerEngine       |                     |  - 32 MB Sliding Window on Seek  |
|  - Decoder Fallback      |                     |  - 80% Next-Episode Preload      |
+--------------------------+                     |  - ContinueWatchingRepository    |
        |                                        +----------------------------------+
        v                                                   |
+-----------------------------------------------------------+-----------------------+
|                             Local Streaming Layer                                 |
|  - LocalStreamingServer (Ktor/Netty Loopback HTTP Server)                         |
|  - StreamAccessRegistry (Single-Use Capability Tokens)                            |
|  - TelegramChunkScheduler (Demand-Driven Range Fetching)                          |
|  - TdLibClient (C++ TDLib Native JNI Core: armeabi-v7a / arm64-v8a)              |
+-----------------------------------------------------------------------------------+
```

---

## 3. Playback Core & State Machine (Fixing P-C)

### Single Active Playback Session
Only **one** playback session is allowed to exist in memory across the entire application lifecycle. If a new session begins while an existing session is running or winding down:
1. The previous session is immediately transitioned to `PlaybackState.Ending`.
2. Active background chunk downloads are cancelled.
3. Capability tokens for loopback streams are revoked in `StreamAccessRegistry`.
4. Surface attachments are detached.
5. The player engine instance (`ExoPlayer` or `MPVLib`) is safely destroyed on its dedicated worker thread.
6. The new session enters `PlaybackState.Preparing`.

### State Lifecycle
```
[ Idle ] 
   │
   ▼
[ Preparing ] ──(Probe Capabilities & Token Issue)──► [ Buffering ]
                                                             │
                                                             ▼
                                                    [ Playing / Paused ]
                                                             │
                                                             ▼
                                                         [ Ending ]
                                                             │
                                                             ▼
                                                        [ Released ]
```

---

## 4. Centralized TV Remote Input & Focus (Fixing P-A, P-B)

All TV remote key events in `PlayerScreen` route through `PlayerInputController`. Individual widgets and composables do not intercept root D-pad keys.

| Remote Key | Controls Hidden | Controls Visible | Audio/Subtitle Menu Open | Next-Episode Card Active |
| :--- | :--- | :--- | :--- | :--- |
| **D-Pad Center / OK** | Reveal HUD & show play/pause | Toggle Play/Pause | Select focused track | Trigger "Play Now" immediately |
| **D-Pad Up** | Reveal HUD & focus Seek Bar | Move focus up to Top Bar | Move selection up | No-op (maintain card focus) |
| **D-Pad Down** | Reveal HUD & focus Bottom Controls | Move focus down to Bottom Bar | Move selection down | No-op (maintain card focus) |
| **D-Pad Left** | Seek backward 10s & reveal HUD | Seek backward 10s | Dismiss menu | Focus "Play Now ▶" |
| **D-Pad Right** | Seek forward 10s & reveal HUD | Seek forward 10s | Dismiss menu | Focus "Cancel" |
| **Back Button** | Exit player | Hide controls HUD | Close menu, restore focus | Cancel card, pause video, show HUD |
| **Play/Pause Key** | Toggle Play/Pause | Toggle Play/Pause | Toggle Play/Pause | Toggle Play/Pause |

### TV Focus System
- **`TvFocusSurface`**: Uses a solid 3.5 dp high-contrast border with 1.05x scale on D-pad focus.
- **Mali-G52 Optimization**: GPU-intensive `Modifier.shadow` Gaussian blurs are eliminated. On low-end Mali-G52 MC1 GPUs, multi-pass shadow blurs cause significant frame drops; the crisp solid border eliminates overdraw while remaining clearly legible from 10 feet.
- **Overscan Margins**: Outer containers enforce a 5% margin to prevent critical controls from clipping on budget TV panels.

---

## 5. Engine Diagnostics & Black-Screen Recovery (Fixing P-D)

Budget TV systems often feature incomplete MediaCodec implementations, particularly for 10-bit H.264 (Hi10P) anime profiles, Dolby Vision Profile 5 streams, and custom container tags.

### Pre-flight Probing (`CapabilityProber`)
Before initializing the media engine, `CapabilityProber` analyzes the media:
- **10-bit AVC (Hi10P)**: Standard Android TV decoders produce green/garbled video or fail silently. Automatically routed to `MpvPlayerEngine`.
- **Dolby Vision Profile 5**: Requires proprietary DV decoders; if missing, routed to `MpvPlayerEngine` with tonemapping.
- **Cached Decisions**: Successful and failed decisions are stored in `EngineVerdictStore` to eliminate future trial-and-error delays.

### Black-Screen Watchdog (`BlackScreenDetector`)
Monitors playback status every 500ms. If audio position is advancing (`> 1500ms`) but no video frame has been rendered for $\ge 3.5\text{s}$:
1. **Tier 1 - Display Surface Fallback**: Automatically switches from `SurfaceView` to `TextureView` via `VideoSurfaceView`. This bypasses hardware video layer compositor crashes on Android TV.
2. **Tier 2 - Hardware Engine Fallback**: Switches from `ExoPlayer` to `MpvPlayerEngine` with hardware decoding (`mediacodec`), preserving playback position.
3. **Tier 3 - Software Decoding**: Falls back to `libmpv` software decoding as an absolute safety net.

---

## 6. Smart Caching & Quota Management (Fixing P-F)

Budget TVs with 8 GB flash typically offer only ~3.0–3.8 GB of usable storage after the OS and system apps. Hardcoded 5 GB cache limits risk completely filling storage, crashing the system.

### Dynamic Quota Algorithm
$$\text{Storage Quota} = \text{clamp}\Big(\text{Available Flash} \times 0.15,\; 256\text{ MB},\; 2048\text{ MB}\Big)$$

### Cache Eviction Phases
1. **Phase 1 (Watched Media)**: Media with playback progress $\ge 90\%$ is automatically marked as watched and prioritized for immediate eviction.
2. **Phase 2 (LRU Sweeping)**: If disk space remains above the dynamic quota, unwatched files are evicted in least-recently-used order.
3. **Invariants (Protected Data)**:
   - User-pinned files (`CacheManager.isPinned(fileId)`).
   - Actively playing media session.
   - Continue-watching items.
   - Files actively being downloaded.

### Sliding Read-Ahead Window (32 MB)
- Sequential prefetching is restricted to a 32 MB window forward from the playhead.
- **Seek Invalidation**: When a user seeks, `CacheManager.onSeek` instructs `TelegramChunkScheduler` and `TelegramPreCacheEngine` to cancel all in-flight chunks outside `[newOffset - 2MB, newOffset + 32MB]`. Download bandwidth immediately concentrates on the new seek point.

### Next-Episode Preload
- When video playback reaches $\ge 80\%$ duration, `CacheManager.onPlaybackProgress` automatically primes the first 8 MB head of the next queued item, ensuring instant playback without buffering on episode transitions.

---

## 7. Single Source of Truth for Resume State (Fixing P-E, P-G)

- **`ContinueWatchingRepository`**: Acts as the authoritative single source of truth for all playback progress across the app.
- **Reactive Stream**: Exposes `StateFlow<List<ContinueWatchingEntry>>` for TV and mobile home rails.
- **Auto-Eviction**: Completed items older than 30 days are automatically purged.
- **Backward Compatibility**: `PlaybackProgressTracker` delegates directly to `ContinueWatchingRepository`.

---

## 8. Verification Matrix

| Verification Target | Command / Tool | Status |
| :--- | :--- | :--- |
| **Unit Test Suite** | `.\gradlew.bat testDebugUnitTest` | **212 Tests Green** |
| **TDLib ABI Coverage** | `.\gradlew.bat :app:verifyTdlibAbiCoverage` | **armeabi-v7a & arm64-v8a Verified** |
| **R8 Minification** | `.\gradlew.bat assembleRelease -PdevAbi=armeabi-v7a` | **Success (5m 31s, 46.6 MB APK)** |
| **Android Lint** | `:app:lintVitalRelease` | **0 Errors, 0 Warnings** |
