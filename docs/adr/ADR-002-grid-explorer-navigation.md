# ADR-002: Grid-first, Real-source Explorer

**Status:** Proposed, 2026-10-31.

## Context
TV horizontal carousels require repeated remote movements and can clip focus rings. Telegram has genuine Saved Messages, channel, group and forum topic semantics. Synthetic folders guessed from filenames risk wrong data and focus destinations.

## Decision
- Home shows exactly three main tabs: Saved Messages, Channels and Groups, retained across navigation and app lifecycle when safe.
- Source directories and media use adaptive **LazyVerticalGrid** / appropriate tablet master-detail and phone stacked layout; no horizontal carousel as the primary explorer.
- Directory leaves are keyed by real Telegram (account,chatId,topicId,messageId) as applicable. Do not infer topic membership or invent playlists/date folders.
- Saved opens media immediately; Channel selection opens its own media; Group selection checks actual forum flag: topics if present, direct media otherwise.
- Breadcrumbs and Back follow the real hierarchy. Store stable focus and scroll keys, not raw positions only; gracefully recover from deleted/inaccessible entries.
- Focus is visible: no duplicate clickable/focusable targets, overscan-safe border, no clipped focused scale; D-pad OK activates one item.

## Alternatives rejected
Horizontal Netflix-style rails for all content; title-derived folders; global "all media" topic filtering.

## Consequences and acceptance
The explorer will need stronger pagination/empty-state handling and account-aware navigation state. Tests cover source type discrimination, General topic handling, topic isolation, deleted chats, D-pad vertical wrap/edge and Back restoration. TV and touch should use separate density tokens, not separate media semantics.
