# Bousygram implementation and code review — 2026-11-09

## Reviewed baseline

- Repository: `Vaydelux/Bousygram`
- Original `main` commit: `f61af9f167f1f22accbf1e116f8649e4cd001fb1`
- Code inspected: LibVLC event lifecycle, PlaybackSession, Media3 audio effects,
  playback focus, queue construction, source-scoped episode pagination, memory
  limits, theme semantics, cache quota, Android Enterprise CI and debug releases.
- This is a static + repository CI review. No claims of playback performance or
  acoustic quality are made without physical ARM32/ARM64 device evidence.

## Findings addressed in this PR

### P1 — Native unexpected-pause budget expired too early

The existing two-retry limit was reset only by preparing or stopping a media
item. A movie with a successful recovery early in its runtime could therefore
hit a terminal native-pause error at a separate interruption much later.

**Correction:** A recovery-budget reset is allowed only after 15 seconds
of continuous Playing intent and at least 5 seconds of actual player timeline
progress. Timers are invalidated on another pause, changed media generation or
release. The original maximum of two retries still applies to rapid failures.

**JVM evidence:** policy tests cover no native progress, too-short intervals,
normal recovery, pause, missing media, nonplaying output and unused budget.

### P1 — Huge launch queues retained more metadata than the player showed

The player capped its *rendered* episode queue, but `PlaybackRequest.queue`
still retained the full launch catalog. On storage- and RAM-constrained TVs,
that duplicates potentially hundreds or thousands of queue metadata items
during an extended session.

**Correction:** Launch requests now deduplicate chat/message identities and
bound their initial queue to 256 entries, centered around the selected item.
The existing player-side 256/512 limit remains an upper bound; devices can
append additional metadata on demand via the existing scoped pager. This is a
conservative common launch cap, even on ARM64; more entries can load later.

**JVM evidence:** >700-entry initial queue with duplicated messages remains
bounded and keeps the clicked video.

### P1 — File-ID-only queue fallback could select the wrong chat

When a message ID could not be found, `queueIndexForRequest` previously
selected the first matching file ID, without also checking chat ID. Identical
Telegram files can legitimately appear in multiple chats.

**Correction:** Match the original chat before accepting a file-ID fallback;
otherwise the selected item is inserted into the bounded request queue.

**JVM evidence:** duplicate file across two chat IDs with a missing message
ID never selects the other chat's entry.

### P1 — A selected message could reuse stale catalog file metadata

An asynchronous TDLib file-ID refresh or an edited message may update the
clicked `MediaItem` while the launch catalogue still includes a matching
chat/message with an earlier file ID. The original queue construction selected
that stale entry by message ID, even when the `PlaybackRequest` had the new
file ID.

**Correction:** The clicked item's current file ID, filename and display title
replace the matching queued entry before the bounded queue is handed to the
player. The change does not alter unrelated chat/message entries.

**JVM evidence:** A stale TS queue entry cannot replace an updated MKV
selection for the same Telegram message.

## Reviewed and intentionally unchanged

- A `null` selected **forum** topic represents *All Media*, not necessarily
  the General topic. The pagination guard correctly refuses to infer a General
  topic from that state; loosening it could mix forum topics. Explicit General
  topic IDs are normalized safely by `normalizedForumTopicId`.
- TDLib progressive pagination is source-scoped and already limits active
  catalogue pages. No attempt to prefetch entire channel histories.
- The storage quota protects tracked media/cache paths; it is **not** proof
  that the entire application footprint, downloaded APK, TDLib database and
  retained non-media files total under 200 MB.
- Native and CPU performance measurements must come from the existing
  opt-in physical-device test and baseline comparator, not from unit tests.

## Remaining qualification work

1. On physical ARM32 and ARM64 televisions: 30+ pause/resume/rebuffer/seek
   cycles per engine, observe whether native audio actually progresses for
   15 seconds before the retry budget renews.
2. Test LibVLC subtitles, video scaling and output route changes with MKV,
   MP4 and transport streams; verify real D-pad focus and long-duration seek.
3. Confirm no network access tokens, Telegram data, untrusted links or raw
   account identifiers leak to diagnostics, external intents or saved state.
4. Sample 60-minute PSS/Java heap/native heap/storage trends under real
   playback; record disk remaining and native decoder resource release.
5. Validate the exact PR head using Android Enterprise (lint/JVM/ABI APKs),
   Android Emulator Cast/media smoke and TDLib generation before merge.
6. Post-merge, verify combined `main` CI and generated debug artifact
   provenance before distributing an APK.

The outstanding device checks are real gates, not work declared complete
because compilation passes.
