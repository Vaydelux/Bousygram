# Telegram / Extras UI and Large-Group Performance Hardening

Date: 2026-11-21 · Scope: feature-frozen stabilization only.

## Observed code findings

1. Home and Library called the same `TelegramExplorerDashboard` with only title/navigation changes. A selected Library group could therefore appear on Home. Home now displays a read-only preview of the current account's Saved Messages, and Library owns Saved / Channels / Groups directories, topics and cursor pagination.
2. Source selection called `loadChatSources(CHAT_SOURCE_DIRECTORY_LIMIT)` with an upper limit of **10,000 dialogs**. Each new dialog could require `GetChat` and, for supergroups, `GetSupergroup`. The initial user action should not require that full scan.
3. The directory previously returned after the first failed detail request in a batch, even if other groups had loaded. A partial directory should remain navigable with a visible retry.
4. The existing video explorer already has progressive filter search, indexed-chat fallback, lazy grids, 120/240 in-memory metadata windows and scoped cursor paging. Preserve these contracts rather than rewriting them.
5. Extras, Telegram section tabs and shared buttons use different typography variants. Shared tab/button line heights are now normalized; compact Home/Search/Favorites titles have a common component.

These findings are based on repository implementation. The user's reported login→Groups failure is consistent with the directory request pattern, but its exact device stack trace remains unverified.

## Milestone A — bounded source directory and navigation (this hardening PR)

- Open Channels/Groups with a **160-dialog per-list request ceiling**, not 10,000. Load more in increments of **120**, only when requested by the user. Both main and archived chat lists are included; labels remain honest even when private chats occupy the scanned prefix.
- Reuse current-account `GetChat` / `GetSupergroup` metadata across expansions.
- Keep successfully resolved source entries if a detail request times out; show partial-sync status and a retry action.
- Keep existing per-source media cursor, group topic navigation, decoder, and source identity unchanged.
- Distinguish Home's Saved Messages overview from Library's real directory.
- Standardize compact page headings and control text line heights; continue to honor font scaling, theme tokens and 48dp/TV minimum targets.

## Milestone B — group/topic data latency (next stabilization pass)

- Collect structured, redacted stage timings for `GetChats`, `GetChat`, `GetSupergroup`, `GetForumTopics`, indexed video search filters, first playable card and background thumbnail fetch. Log request count and retry reason, never account IDs, message titles or credentials.
- Run a cold-login and warm-login matrix on accounts with ~2,000 videos, many groups and large forum-topic counts. Confirm first content appears before remaining directory work.
- Add request deduplication / bounded per-source metadata concurrency where profiling shows it helps. Do not duplicate native TDLib fetches or add unbounded in-flight requests.
- For topic directories with thousands of entries, introduce cursor-backed continuation and stable focus restoration instead of eager full-topic enumeration. Preserve TDLib cursor identity and cancellation.
- For empty indexed results just after login, distinguish TDLib-sync-in-progress and transient network errors from a genuinely empty source. Retry only the failed scope, and preserve existing cards and cursor; never spin forever.

## Milestone C — visual and functional acceptance

- Verify portrait widths 320/360/393/480dp, short landscape 640–960×360–480dp, tablets, 720p Android TV, 1080p/4K TV and font scale 1.0/1.3/1.6/2.0.
- Home, Library, Search, Favorites and Extras must share readable title hierarchy, centered single-line tab/button text, balanced focus rings, 48dp touch and TV targets, theme-aware colors, and non-overlapping search/filter controls.
- Home renders Saved preview without displaying a previously selected Library group's items. Library continues to show the actual selected Saved/Channels/Groups/topic directory. Search opens globally from Home and within current source from Library.
- Verify Group opening, topic Back, retry after temporary 500/timeout, 2,000-item media paging, favorites/search navigation, and D-pad focus after loading additional sources.
- Record actual device screenshots and instrumentation results. A green compiler or emulator does not establish physical TV correctness.

## Rollout and release policy

This work does not change debug APK packaging, signing, the approved Telegram playback engine, or Extras providers. Keep the feature freeze. Require Android Enterprise and appropriate emulator smoke checks on the exact PR SHA; address failures before merging. Produce a debug prerelease only after the merged source is qualified and the user requests one.

Do **not** claim a measured performance improvement until timed before/after runs on representative devices and data exist.
