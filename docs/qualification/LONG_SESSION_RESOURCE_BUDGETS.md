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
