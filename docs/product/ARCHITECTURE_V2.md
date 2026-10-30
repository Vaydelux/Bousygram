# Bousygram v2 — Target Architecture (Non-Destructive)

**Proposed design; no refactor is approved by this document.** Current runtime truth is [ARCHITECTURE.md](../../ARCHITECTURE.md), [docs/ANDROID_TV_HARDENING.md](../ANDROID_TV_HARDENING.md), [streaming cache audit](../storage/STREAMING_CACHE_ARCHITECTURE.md) and the live code. Older ARCHITECTURE_PLAN.md contains historical/possibly stale claims; verify each claim against HEAD before reusing.

## 1. Logical component map

~~~text
Android Activity / Root Shell
  ├─ StartupBanner + ModeSelection
  ├─ TelegramMediaGraph
  │   ├─ Existing Auth/AccountSession (single owner)
  │   ├─ ExplorerNavigator + ScreenStateCache (per account)
  │   ├─ MediaCatalogRepository (TDLib-backed source-of-truth)
  │   │   ├─ Saved Messages (metadata + pages)
  │   │   ├─ Channels (real channel IDs -> media)
  │   │   └─ Groups (real group IDs -> real topics, if any -> media)
  │   ├─ Existing MediaViewModel / Browse integration (migrate gradually)
  │   ├─ PlaybackLaunchCoordinator -> PlaybackCore -> Media3/mpv fallback
  │   └─ Existing TDLib JNI + TelegramChunkScheduler + secure local stream gateway
  └─ ExtrasGraph (ONLY maintenance placeholder in v2)
      └─ No network/API/WebView/virtual mouse dependencies
~~~

**Do not rewrite existing playback and TDLib services to fit a speculative package tree.** Start with logical interfaces inside the current app module, migrate one bounded use case at a time. Split Gradle modules only after dependency direction and startup/build size justify it.

## 2. Dependency and lifecycle rules

- Shell knows **route contracts** and theme tokens, not Telegram auth internals.
- Telegram feature depends on shared UI primitives and its domain interfaces; it owns all Telegram session, chat/topic indexing, playback state, network and private media.
- Extras placeholder may depend on shell/UI tokens only. It must not depend on TelegramMediaGraph, TDLib, Telegram files, session caches, signing keys or player internals.
- No second TDLib initialization, media engine, playback session, or stream gateway. Existing native compatibility and ABI validation remain mandatory.
- Existing Android activity/process restoration and logout are authoritative; new routing must preserve them.

## 3. Explorer routes (typed, real identifiers)

~~~kotlin
sealed interface ExplorerRoute {
    data object Saved : ExplorerRoute
    data object Channels : ExplorerRoute
    data object Groups : ExplorerRoute
    data class Channel(val chatId: Long) : ExplorerRoute
    data class Group(val chatId: Long) : ExplorerRoute
    data class Topic(val chatId: Long, val topicId: Long) : ExplorerRoute
    data class Media(val chatId: Long, val messageId: Long) : ExplorerRoute
}
~~~

Use actual TDLib chat/source types and existing data-model types rather than inventing a synthetic Group/Topic ID. Source identity must include account/session key. For Groups with forum topics, show the actual topic list (including General if supplied by Telegram); for non-forum groups, open media directly. Channels normally open media directly; **do not fabricate date/playlist folders** without real metadata/explicit product approval. Honor permissions, archived chats, deleted topics, unsupported media types and inaccessible messages.

Return behavior: Player → previous exact explorer location/focus; Topic → Group; Group/Channel media → source list; source list → selected top tab; tab → shell. Back must not unintentionally quit the activity. Screen restoration stores lightweight route IDs, grid index/offset and focused stable key; it does not persist sensitive stream URLs.

## 4. State ownership and caching

| State | Single authority | Cache class | Invalidation |
|---|---|---|---|
| Authentication/account session | existing auth + AccountSession | encrypted existing TDLib storage | logout, auth revocation, account change |
| Source directory and forum topics | TDLib-backed catalog repository | account-scoped memory + optional persisted *metadata* | changed source membership, topic change, logout |
| Media metadata pages | repository, keyed by (account,chatId,topicId/filter,cursor) | bounded memory, optional local index | edits/deletes/updateFile, account change, TTL |
| Grid tab / scroll / focus | shell/explorer ViewModel or saveable state | UI snapshot | explicit navigation reset, logout |
| Thumbnails | existing image cache/TDLib metadata | bounded visual cache | file changes, logout/privacy |
| Playback progress / pins | existing tracking and repositories | existing validated persistent storage | changes via single owner |
| Video bytes | existing TDLib chunk scheduler/cache manager | **not** the catalog cache | lifecycle/TDLib retention rules |

Implement **stale-while-revalidate**: display cached metadata immediately with a subtle refresh indicator; coalesce duplicate requests; atomically replace snapshots only when account/source/request sequence matches. Capture emitted TDLib update events; do not turn navigation into periodic full reindexing. Treat TTLs and budgets as tunable after measurements, not unfounded guarantees. Cap memory, preserve offline pins, exclude credentials and per-session capability URLs, and clear account-scoped entries on logout or account switch.

Important: a bounded **request read-ahead** does not imply bounded physical TDLib storage. Follow the existing [storage audit](../storage/STREAMING_CACHE_ARCHITECTURE.md).

## 5. Player state and remote ownership

Keep the existing PlayerInputController, PlaybackCore, PlaybackSessionController and playback launch path as starting contracts. Create a **single prioritized overlay input state**:

1. Fatal error/retry or confirmation dialog
2. Track/decoder/settings dialog
3. Related-media tray (opened by Down)
4. Seek preview
5. Compact player HUD
6. Hidden chrome / video surface

The **same key must never be handled by both parent and focused child**. Back closes the topmost layer; after all layers are dismissed, Back exits player to the exact route (never closes the app from Start Over). Start Over invokes a validated seek-to-zero on the current session, preserves selected file/queue and cannot trigger the close handler.

With no modal overlay: Left/Right begin or adjust preview (bounded, accelerates on repeat); OK confirms it; Back cancels; Down opens same-source tray. On tray's first row, Up returns focus to the player controls. Disabled seek or unknown-duration live/TS presents truthful status rather than a fake timeline. Optional frame previews must come from real accessible media, never fabricated thumbnails or unbounded network work.

Use current source identity (account/chat/topic) for tray contents. No cross-group/topic suggestions unless explicitly supported by permission-aware logic.

## 6. Adaptive presentation

| Mode | Structure | Input | Default density |
|---|---|---|---|
| Android TV (leanback) | reduced header, three tabs, bounded full-height lazy grid, compact player HUD | remote D-pad + OK + Back | compact/standard TV tokens, visible focus ring |
| Tablet | flexible two-pane explorer or grid, restrained top/bottom chrome | touch, keyboard and remote where present | responsive to window classes not diagonal inches |
| Phone | stacked explorer with breadcrumb/back; 2–3 columns as space allows | touch, gestures and accessibility services | >=48dp touch affordances and readable labels |

Do not simply scale the phone screenshot to TV. Size by window dp, focus input and accessibility settings; never assume actual panel resolution equals Compose dp. Avoid focus clipping: separate overflow-safe focus ring from media image clip. Animate focus state modestly (initial design target 120–220ms), provide reduced-motion behavior. Track real device performance before calling it smooth.

## 7. Brand system

Source asset lives as an authored SVG with vector paths, opacity layers, gradients/mask as supported; original Bousygram TV/play motif rather than tracing existing third-party logo. Brand palette and variants are token-based. Android does **not** use favicon.ico for launcher; generate adaptive icon layers and Android vector/raster resources from SVG. Only generate favicon.ico if a web surface/site exists. Test contrast, legibility at 24/48/192px and OS launcher masking.

## 8. Extras security / explicit exclusion

Only render a maintenance placeholder. Future TMDB/AniList/API integrations require a separately approved domain adapter and key handling. Future iframe-in-WebView browsing is **high-risk untrusted content**: design an isolated origin/navigation policy, deny untrusted intent schemes and popups, control downloads, avoid exposing JavaScript bridges or private Telegram data, handle cookies and rights/DRM and document a safer policy than “no sandbox + block ads”. No embedded content or virtual mouse code in v2 core.

## 9. Test and observability boundaries

Use existing GitHub Actions for compile/lint/JVM/native ABI, emulator for D-pad navigation and Compose semantics, and real 32-bit/64-bit TV for focus/codec/seek/performance. Preserve zero-PII telemetry and tagged local measures for Home re-entry, initial cached frame, source refresh, focused item restore, seek commit, first rendered frame, and storage growth. **Do not claim a latency target has been achieved until a before/after trace exists.**

Adopt [ADR decisions](../adr/), the [QA matrix](../agent/QA_MATRIX.md), and the [phase review template](../agent/REVIEW_PROTOCOL.md) for any new implementation.
