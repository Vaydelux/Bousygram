# Bousygram — Layout and Telegram loading hardening (2026-11-22)

## Reported evidence
- User-supplied phone screenshot on 2026-11-22 shows uneven vertical rhythm on mode-selection screen, a narrow appearance button, unequal mode-card content heights, and inconsistent text wrapping. **This is one viewport and one theme**, not proof that every Android TV/phone layout is correct or broken.
- User reports slow loading in Telegram Channels, Groups and their media, often needing 2+ manual retries after an error. No redacted TDLib error code, failed method name, signed-in account, network trace or before/after p50/p95 timing is currently available. Do not claim a verified speedup until an authenticated 2,000+ media fixture is benchmarked.

## Release-safe priorities

### P0 — Reliability before cosmetic polish (implemented in this PR)
1. Retry only transient TDLib 408/500/502/503/504/timeouts with a strictly bounded 2–3 attempts and short backoff. Never retry 401/403/404, invalid parameters, flood-wait or account rejection. All coroutine retries must respect cancellation, account generation and current navigation request.
2. Keep visible channels/groups when GetSupergroup metadata fails. Mark forum classification incomplete instead of assuming non-forum or dropping the group. Recheck the unresolved entry on refresh.
3. A background 50-chat preview must not prevent the 160-dialog initial directory scan when the user opens Channels/Groups. Queue the expansion after the preview rather than restarting all work.
4. One additional delayed auto-recovery for a failed directory sync. Preserve partial data; no infinite retry loop, no silent suppression of permanent errors. Explicit refresh retains previously expanded scan limits.
5. Apply the same bounded transient-retry policy to the TDLib media-index and forum-topic requests; increase the forum progress deadline enough to accommodate one retry.
6. Do not alter approved Telegram playback, filenames, content filters, streaming cache, or account isolation.

### P0 — Visible startup UI layout (implemented in this PR)
1. Separate logo/brand/headline/subtitle into a small-gap hero; use major-group spacing only between hero, appearance action, mode cards and footer.
2. Minimum appearance-control width, centered icon+label and stable hit target.
3. Equalized two-line mode titles and three-line standard mode descriptions (two lines on short viewports) to prevent unequal text-block heights.
4. Independent 12dp mode-card spacing and minimum row height, with existing focus treatments and theme tokens. Long text stays accessible through tooltips.
5. Maintain phone portrait, phone landscape, tablet and TV behavior; do not replace typography with hardcoded colors.

## P1 — Next measured performance iteration (not in this PR)
1. Add privacy-safe stage-duration telemetry **only** for request type, elapsed time, counts and TDLib numeric error category; no chat ID, titles, filenames, phone numbers or access tokens.
2. Measure time from authenticated TDLib state to first source row; first topic; first video card; first visible thumbnail on a representative large group (2,000+ videos). Record 3 cold, 3 warm runs for each phone/TV and network class.
3. Separate metadata and media throughput: limit concurrent GetChat/GetSupergroup, favor visible group/topic pages over offscreen thumbnail work, and deduplicate identical in-flight scans.
4. Make forum-topic paging resumable after a single failed page, preserving earlier valid topics and D-pad focus. Do not fetch every forum topic before showing the first page.
5. Treat unavailable indexed search after login as *sync pending*, not a permanently empty channel. Use bounded search → history fallback with cancel-safe cursors; benchmark before changing existing video search logic.
6. Add a simple non-sensitive diagnostics view/report showing currently loading stage, request category, and retry count for users without ADB.

## P2 — Visual unification after P0 verification
1. Audit tabs/action alignment on Home, Library, Search, Favorites, Extras, and player overlays at widths 320/360/393/600/840dp, large font scales (1.0/1.3/1.6), Android TV 720p/1080p, and both orientations when supported.
2. Adopt one control-height and label-line-height contract (existing shared Compose components) with no clipped labels; all D-pad focus rings and buttons must remain reachable.
3. Capture real emulator screenshots and review edge cases: line truncation, theme contrast, focus overlays, TV overscan, accessibility semantics and scrolling.
4. Change shared components only when a repeatable issue is visible; do not alter working Telegram Media playback controls in a styling sweep.

## Gates for merging this PR
- Android Enterprise, Android Explorer Responsive UI, Cast/media emulator smoke on exact PR head.
- New unit cases: post-login transient failures recover automatically, permanent errors do not retry, forum metadata partial failure retains group and reclassifies on refresh.
- New startup UI test: cards match height, real gap exists, Change appearance remains clickable.
- Regression: existing Telegram source list, forum topic, Saved Message, full-window, Cast/media, theme, APK signing and WebView behavior unchanged.
- Physical account with 2,000+ media is **not** present in CI. Do not claim the device error is solved until the user runs the new debug APK.

## Release approach
Keep existing dated debug APK workflow, ABI variants and approved release pipeline unchanged. Merge only after gates pass; perform new debug release from verified `main`; compare original screenshot and captured emulator evidence. If the same TDLib error recurs, collect request type, numeric code, loading stage and time-to-first-content (never private chat data) before changing lower-level TDLib.
