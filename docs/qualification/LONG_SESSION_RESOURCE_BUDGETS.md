# P1.2 — Long-session resource budgets and Android TV memory pressure

**Implementation status:** source changes committed to `main`. **Not physical ARM32 certified.**
P1.1 source baseline: `661a0f8`; see
[playback recovery observability](PLAYBACK_RECOVERY_OBSERVABILITY.md).

## Scope and explicit allocations

| Resource | Low-RAM or ARM32 policy | Other devices | Ownership |
| --- | --- | --- | --- |
| Visible Telegram media window | 120 items | 240 items | `TelegramMediaWindow`, including live new-message updates |
| Catalog snapshots | At most 8 folder snapshots, each max 120 items | Same snapshot count and metadata cap | `MediaViewModel`; dropped under pressure |
| Recent D-pad folder/viewport restoration | 48 keys globally; 12 on moderate trim; 1 on critical | Same | Synchronized access-order LRU |
| Recent selected forum-topic focus | 64 chats; 16 on moderate trim; 1 on critical | Same | Synchronized access-order LRU |
| Telegram thumbnail bitmaps | Byte-budget based on heap, capped at 3 MiB | Capped at 8 MiB | `ThumbnailExtractor` |
| Telegram seek preview bitmaps | Up to 2 MiB | Up to 6 MiB | `ThumbnailExtractor` and `SeekThumbnailManager` independently |
| Coil decoded poster artwork | At most 8% of available app heap | 16% of available app heap | One singleton `ImageLoader`, lazily created |
| Extras catalog results | 120 retained per tab | 240 per tab | Existing bounded Extras paging window |
| Extras public HTTP JSON cache | Bounded warm metadata (LruCache) | Same | Existing ExtrasCatalogApi |

**32-bit process buffer guard (2026-11-06):** A 32-bit Android TV process
uses the compact libmpv demuxer tier (64 MiB max, 12 MiB back buffer)
even when Android reports a Java heap class above 384 MiB. A TV
already classified as low-RAM keeps the smaller 32 MiB/5 MiB tier.
The same 32-bit TV guard avoids ExoPlayer's high-memory back-buffer tier.
64-bit TV and phone profiles retain their existing behavior. This is a
source-level safety cap, **not a measured PSS reduction or physical-TV
qualification**; the matched-device soak gate below remains mandatory.

**Clarification:** budgets for independent caches are additive. Native decoder
allocations, GPU textures, TDLib native heap and Android Java heap are separate.
Percent-of-heap is not a promised physical PSS reduction.

## Behavior safeguards

- A new Telegram video arriving during a **recent** catalog view prepends once,
  deduplicates by chat/message identity and evicts the oldest retained row
  without growing the list. If an old tail is evicted, the TDLib next-page
  cursor is moved to the last *retained* message to avoid skipped pagination.
- If a user has already paged past the bounded recent window, incoming new
  messages are indexed but do **not** displace the visible older rows and D-pad
  focus. Refresh is the way back to the newest messages.
- An update for an already loaded message replaces the metadata at its existing
  row instead of creating a duplicate. Other forum topics cannot inject a card
  into the currently selected topic view.
- Browsing/focus restoration keys are bounded by access-order LRU. A discarded
  navigation anchor will use the existing safe focus-restoration fallback when
  that view is reopened.
- Memory trimming **never** resets the currently visible `MediaUiState.items`,
  explicitly pauses the player, clears authenticated Telegram identity, or
  forces an active player-engine teardown.
- Android's numeric trim callback values are **categories**, not an ascending
  severity scale: `UI_HIDDEN=20` is not more severe than
  `RUNNING_CRITICAL=15`. UI hiding alone does not trigger complete image-cache
  eviction. Running critical and complete pressure release decoded caches;
  moderate pressure trims dormant metadata and thumbnail budgets.
- Account invalidation also clears Coil's decoded cache to avoid retaining
  private artwork in the singleton image loader.

## Repeatability and validation

1. On a physical **armeabi-v7a** low-memory TV, navigate at least 100
   pages across Saved Messages, channels, groups and forum topics, with
   repeated D-pad focus changes. Revisit recent folders and confirm focus
   restoration, while older discarded history uses safe fallback focus.
2. While the user is browsing a deep page, receive new media messages in
   the same chat. Verify the current older window does not jump, duplicate or
   exceed the list limit. Return to the newest page and confirm the new item.
3. With a recent folder at its memory limit, receive more than 120 new
   messages and then select Load More. Ensure cursor continuity and no
   permanently missing rows.
4. Trigger Android's moderate and critical `onTrimMemory` paths, if your
   test harness supports it. Confirm dormant cache reduction and that the
   active playback session remains usable and retains explicit Pause intent.
5. Alternate Extras poster browsing, Telegram thumbnail extraction and MKV/TS
   playback for 30–60 minutes each. Capture app Total PSS, native heap PSS,
   decoder memory, process restarts, visible frame/audio quality and timeline
   responsiveness; use
   [physical-device sampler](PHYSICAL_DEVICE_PRIORITY_GATE.md).
6. Repeat on physical **arm64-v8a** and compare matched scenario conditions.
   Inspect private logs locally; do not upload chat IDs, media paths, tokens,
   raw logcat or authenticated URLs to public CI.

Existing emulator/JVM/ABI checks can validate syntax, rules, lifecycle and
packaging only. **Physical memory-regression thresholds remain undefined until
matched baselines are collected.** A build pass is not certification of
long-session stability.

## 8 GB Android TV — 200 MiB reclaimable media-cache target

The previous Telegram media cache ceiling was **2 GiB** and could consume
hundreds of megabytes on an 8 GB TV. This policy allocates a **200 MiB
(209,715,200 bytes)** best-effort disk cache target across three explicitly
managed, disposable cache families:

| Cache family | Budget | Cleanup |
| --- | ---: | --- |
| TDLib transient media | 160 MiB | Existing account-safe LRU via `TdApi.DeleteFile`, dynamic low-storage threshold, background / periodic sweeps |
| Coil public poster images | 24 MiB | Coil `DiskCache` LRU (including existing `image_cache` files) |
| Generated Telegram thumbnails and seek previews | 16 MiB | Oldest generated PNGs recycled across account-specific cache directories, at launch and after writes |
| **Total allocation** | **200 MiB** | No forced deletion of active or user-saved media |

The TDLib target is additionally bounded to **15% of remaining available disk
space** and can become zero on a critically full device. Managed-app ceilings
can only reduce the quota; they cannot raise the 160 MiB limit. Pinned files,
active player/seek leases, continuing playback, and recently accessed files
are protected. On-disk thumbnail recycling scans **only** recognized
`cacheDir/accounts/{accountId}/video_thumbnails/thumb_*.png` and
`cacheDir/accounts/{accountId}/seek_previews/seek_*_*.png`; it never recurses
into TDLib databases or arbitrary user files. The current thumbnail being
written is protected. Work runs off the UI thread, preserving D-pad focus
and decoder responsiveness.

**Not a hard limit on all application storage.** The installed APK,
decompressed native libraries, Telegram login databases, pinned/offline
downloads, untracked old media, WebView site data, and an actively streaming
large video are **outside** this allocation. TDLib may download beyond its
eviction target while playback is using a file, and cache trimming is
asynchronous. Android's "App storage" panel may therefore show more than
200 MiB even after cleanup. Likewise, this is **disk storage**, not a
200 MiB process-RAM/PSS limit. Do not delete or overwrite live TDLib
files or database directories using raw filesystem cleanup.

### Qualification for a small-storage TV

1. Install a trusted ARM32/ARM64 debug build; record Android Settings → Apps
   → Bousygram → Storage sizes (App / Data / Cache).
2. Browse posters and generate TS/seek thumbnails, restart the app, then
   verify generated PNG and Coil disk caches stay within their allocations.
3. Stream multiple Telegram episodes, pause/seek repeatedly, and wait for
   background/periodic cleanup; verify the active video and manually pinned
   media remain playable. Cache may temporarily exceed target during use.
4. On a TV with low free storage, verify the effective TDLib quota shrinks and
   canceled next-episode prefetches do not create repeated buffering.
5. Compare physical app disk usage and RAM PSS separately for 30–60 minutes.
   No hardware certification or 200 MiB total-app guarantee is claimed.

## Streaming-first storage protection (next milestone)

**Goal:** keep the 200 MiB managed media-cache allocation useful on an
8 GB Android TV, while protecting the current movie's playback/seek traffic.

- **500 MiB free:** speculative next-episode, focused-poster and background
  head/range prefetch are disabled. Already running speculative range jobs
  stop requesting further chunks at their next chunk boundary. The player
  can still request required foreground bytes.
- **250 MiB free:** classified as critical storage pressure; no extra
  speculative work is permitted. These thresholds are based on actual
  available bytes from the TDLib app-data filesystem; the check is cached
  for eight seconds to avoid hot-path filesystem polling. If the disk
  measurement is unavailable, optional downloads fail closed.
- **Active player:** its requested stream bytes and seek handling are never
  blocked by this guard. Any speculative read-ahead request is capped at
  32 MiB on healthy storage and reduced to zero below the low-space
  threshold. This does not shrink an active decoder's memory allocations.
- **Playback exit / track switch:** the app schedules protected TDLib
  reclamation on the existing serial I/O worker. A successful TDLib
  download cancellation also prompts reclamation; the job is coalesced.
  Already pinned/offline files, live player/seek leases and
  continue-watching items are protected by existing guards.
- **Lifecycle:** a foreground return on low storage cancels focus prefetch,
  pauses other background speculation and schedules cleanup. The regular
  protected cleanup cadence is now every 10 minutes (previously 30)
  while Telegram runtime is active. The initial Telegram runtime start
  still schedules a cleanup, and app backgrounding also prunes.

**Remaining architectural limitation (important):** TDLib's active
`openPlayback` currently begins an open-ended foreground download to
avoid MKV/TS seek and Media3-reopen stalls. A very large movie can therefore
temporarily grow beyond the 160 MiB TDLib-cache *eviction target* while the
file is still in use; this milestone does not claim to convert that path into
a bounded rolling disk window. Doing that safely requires native range
handoff, exact seek/resume regression coverage, and physical ARM32/ARM64
testing to ensure no looped buffering. Never delete the in-use file just
to enforce a nominal 200 MiB quota.

**How to validate:** measure available flash before, during and after
two long MKV/TS episodes. Trigger playback switches and app-background
cleanup. Verify no new focus/next-episode prefetch occurs while free space
is under 500 MiB, active movie seeking still works, and pinned downloads
survive. Repeat physical low-storage and crash reproduction separately.
Android CI does not replace real-device low-flash playback qualification.
