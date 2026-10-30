# ADR-004: Account-scoped Catalog Cache and Stale-While-Revalidate

**Status:** Proposed, 2026-10-31.

## Context
Home and source menus are reported to repopulate on return from Player. Existing TDLib, thumbnail, pinned media and streaming caches do not automatically provide stable Compose screen state. Re-downloading metadata after each screen recreation causes loading flashes and remote focus loss.

## Decision
- Separate catalog **metadata** snapshots, UI route/focus state, thumbnail assets and TDLib **video bytes** into explicitly different owners.
- Cache keys contain session/account identity, chat ID, real topic ID, filter and pagination cursor; bounded in-memory LRU first. Introduce optional persistent Room metadata only after inspecting existing schema and migrations.
- On return, render last successful metadata snapshot immediately; revalidate only if stale or affected by an actual source/update event, coalescing in-flight requests and applying results only to the active request generation.
- Restore grid index/offset, selected tab, breadcrumb and focus using stable IDs; recover if ID is deleted.
- Invalidations cover logout/account switch, chat/topic removal, file edit/delete, relevant TDLib update and privacy-mode transitions. Never persist credentials, scoped token URLs or private key material in this cache.
- No video-byte warmup merely because a card receives D-pad focus. TDLib payload retention and quota remain governed by existing streaming/cache owner and must be measured on real storage.

## Alternatives rejected
Always refetch on navigation; globally shared cross-account catalog cache; cache whole video downloads to solve metadata reloads; force-recreate all view models on player exit.

## Consequences and acceptance
Needs lifecycle/fake clock tests, pagination deduplication, update races, low-memory tests and device metrics. No full-screen loading banner on warm Home return; stale data must not display another account's media. Set numeric TTL and memory caps based on measured usage, not guesses.
