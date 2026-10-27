# Telegram Video Streaming Optimization, ProGuard Hardening & Production Cleanup

Enhance Bousygram's video streaming pipeline using a hybrid Ktor local HTTP proxy that serves Media3 ExoPlayer and libmpv with instant-start chunked buffering, establish a comprehensive production ProGuard/R8 configuration specifically targeting TDLib and Media3 reflection/JNI signatures, and verify that the data layer strictly consumes live Telegram backend data.

## User Review & Critical Decisions

> [!IMPORTANT]
> **Summary of Confirmed Choices & Architectural Directives:**
> - **Streaming Architecture**: Implement the **Hybrid Ktor Local Proxy with Media3 ExoPlayer and libmpv**. The embedded Ktor server intercepts HTTP byte-range requests (`Range: bytes=X-Y`), schedules chunked reads through TDLib's sequential file pipeline (`downloadFile` / `readFilePart`), and feeds low-latency video streams to both ExoPlayer (standard streams) and libmpv (complex MKV/multi-audio tracks).
> - **Production Data Hygiene**: Verified that the entire repository layer (`SavedMessagesRepository`, `ContinueWatchingRepository`, `TmdbRepository`, `VideoIndex`) operates exclusively on authentic TDLib MTProto data and local Room index tables with zero mock repositories. UI design previews (`TvHybridPreviewHost`, `TvPreviewMocks`) remain isolated behind `BuildConfig.DEBUG` flags for layout inspection without polluting production releases.
> - **ProGuard / R8 Hardening**: Expand `proguard-rules.pro` to explicitly protect TDLib JNI field reflection (preventing field strip/rename which causes JNI `NoSuchFieldError`), Media3 dynamic codec and renderer instantiation (`DefaultRenderersFactory`, `DefaultExtractorsFactory`), and Ktor ServiceLoader registration.

---

## 1. Overview & Core Concept

- **What It Does**: Upgrades Bousygram's video playback architecture to match top-tier Telegram streaming clients (like Echogram). An in-process Ktor loopback HTTP server receives range-based video stream requests from Jetpack Media3 ExoPlayer and libmpv, translates seek points and read-ahead buffers into TDLib sequential file download priorities, and streams media in real time without waiting for full downloads.
- **Target Audience / Devices**:
  - **Android TV (10-ft)**: Google TV, Android TV sticks (Chromecast, Fire TV, Xiaomi TV Stick), Nvidia Shield.
  - **Mobile & Tablet**: Android smartphones and tablets with adaptive portrait and landscape orientations.
- **Key Value**: Instant playback start, smooth seeking with HTTP range requests, minimal disk footprint via chunked streaming, and crash-free production APKs with full R8 minification.

---

## 2. User Experience & Visual Design

- **Streaming UX & Instant Playback**:
  - Instant playback start (< 500ms initial buffer) when selecting any video from Saved Messages or channels.
  - Real-time seek scrubbing with dynamic buffering feedback; forward and backward jumps request precise byte ranges through the local proxy.
  - Fallback engine routing: ExoPlayer for standard MP4/H.264/HEVC/WebM; seamless delegation to libmpv for 10-bit HDR, SSA/ASS complex styled subtitles, and DTS/AC3 multi-audio streams.
- **Player Interface & Controls**:
  - 10-ft TV layout with D-pad navigation, play/pause toggles, seek bar with chapter/preview thumbnails, audio track switcher, and subtitle track picker.
  - Subtle buffer indicators: discrete progress bar showing cached percentage ahead of playhead.
- **Clean Production States**:
  - Zero placeholder or fake sample media displayed in production; all items originate from authenticated Telegram chats.
  - Polished empty states (`NoSearchResultsEmptyState`) when searches yield no matches.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Hybrid Local HTTP Proxy vs Custom ExoPlayer DataSource**
  - *Chosen Approach*: Hybrid Ktor Local HTTP Proxy (`http://127.0.0.1:<port>/video?file_id=<id>`).
  - *Why*: Allows both **Media3 ExoPlayer** and **libmpv** to consume the exact same TDLib stream source. A custom ExoPlayer `DataSource` is tightly coupled to Media3 and cannot feed libmpv, breaking multi-engine playback.
  - *Alternatives Considered*: Custom ExoPlayer `DataSource.Factory` was evaluated; while it eliminates HTTP socket overhead, it lacks cross-engine compatibility needed for advanced video containers.
- **Decision 2: Comprehensive ProGuard/R8 Rules for JNI & Reflection**
  - *Chosen Approach*: Explicitly preserve all `org.drinkless.tdlib.TdApi$*` classes, constructors, and fields, along with Media3 `DefaultRenderersFactory` and codec discovery mechanisms.
  - *Why*: TDLib's native C++ library locates Java fields directly via JNI (`GetFieldID`, `SetObjectField`, `NewObject`). R8 minification stripping or renaming these fields causes fatal `NoSuchFieldError` crashes on release APKs. Media3 dynamically instantiates decoders and renderers via reflection.
- **Decision 3: Scope of Mock Data Removal**
  - *Chosen Approach*: Remove all mock repositories and synthetic seeds from `data/repository`, keeping only standalone `@Preview` helper mocks strictly fenced inside `BuildConfig.DEBUG` preview composables.
  - *Why*: Satisfies production release requirements while preserving Compose tooling previews during development.

---

## 4. Technical Architecture & Data Strategy

### Streaming Pipeline Architecture

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                            Bousygram Video Player                            │
│         ┌─────────────────────────┐       ┌────────────────────────┐         │
│         │   Jetpack Media3 ExoPlayer │       │      libmpv Engine     │         │
│         └────────────┬────────────┘       └───────────┬────────────┘         │
└──────────────────────┼────────────────────────────────┼──────────────────────┘
                       │ HTTP Range GET Request          │ HTTP Range GET Request
                       │ http://127.0.0.1:PORT/stream   │ http://127.0.0.1:PORT/stream
                       ▼                                ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                       Ktor Local HTTP Streaming Server                       │
│  - HttpRangeParser: Parses Range: bytes=start-end headers                     │
│  - StreamAccessRegistry: Manages active client connections and timeouts     │
│  - TelegramFileGateway: Resolves fileId, mime-type, total content length     │
└──────────────────────────────────────┬───────────────────────────────────────┘
                                       │ Sequential Chunk Requests
                                       ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                    TelegramChunkScheduler & Pre-Cache Engine                 │
│  - Coordinates downloadFile / readFilePart with priority                     │
│  - Low-storage streaming: evicts distant played chunks, prioritizes readhead │
│  - Zero-disk overflow via LRU chunk buffering                                │
└──────────────────────────────────────┬───────────────────────────────────────┘
                                       │ TDLib Client Queries
                                       ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                         TDLib Native Engine (MTProto)                        │
│  - JNI Bridges: protected by hardened ProGuard keeps                         │
│  - Direct Telegram MTProto connection to DC file servers                     │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Planned Enhancements

1. **`app/proguard-rules.pro`**:
   - Comprehensive rules for `org.drinkless.tdlib.**`: Keep all classes, fields, public constructors, and native methods (`-keepclassmembers class org.drinkless.tdlib.TdApi$* { <fields>; public <init>(...); }`).
   - Comprehensive rules for `androidx.media3.**`: Keep codec reflection, `RenderersFactory`, `ExtractorsFactory`, `MediaCodecSelector`, audio processors, and format classes.
   - Comprehensive rules for `io.ktor.server.**` and `io.netty.**`: Ensure loopback server reflection, ServiceLoader discovery, and pipeline classes remain intact.
2. **`app/src/main/java/com/vaydelux/bousygram/core/tdlib/server/LocalStreamingServer.kt` & `TelegramChunkScheduler.kt`**:
   - Verify and tune HTTP range request handling, chunk streaming response pipeline, connection keep-alives, and fast seek response headers (`Accept-Ranges: bytes`, `Content-Range: bytes X-Y/Z`).
   - Ensure chunk scheduler allocates priority to the current playback head and cancels stale download requests when seeking forward or backward.
3. **Repository & Data Layer Audit**:
   - Verify that all production repository implementations (`SavedMessagesRepository.kt`, etc.) strictly query authentic Telegram channels and saved messages.
   - Guard preview mocks in `TvPreviewMocks.kt` so they are excluded or unreferenced in production paths.
4. **Verification**:
   - Run `compile_applet` and the unit test suite (`gradle :app:testDebugUnitTest`) to ensure zero regressions across streaming and ProGuard rules.
