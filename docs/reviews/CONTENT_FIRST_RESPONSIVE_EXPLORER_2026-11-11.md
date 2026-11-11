# Bousygram — Content-first responsive Library

**Date:** 2026-11-11
**Scope:** Telegram Home/Library browse screen, short Android TVs, Android portrait and landscape.
**Architectural principle:** **Media first.** The selected source is contextual information, not an independent dashboard.

## Original failure (verified in source)
The earlier `TelegramExplorerDashboard` placed four independent regions **above** the media grid: a large branded heading, horizontal action buttons, section tabs and a large, outlined collection-information card with its own breadcrumb, title, description, spinner, status pills and topic actions. These regions had independent padding and row heights. A low-resolution TV could therefore show almost no media, even though the grid was implemented and the theme was visually uniform. The previous premium theme pass did not address this vertical-budget problem.

## New information hierarchy

1. **Toolbar (one row):** `Your library`, one-line selected source/file count, Back when applicable, Search when space allows, and a D-pad/touch-friendly More menu. On genuinely wide displays the primary actions appear inline. Overflow keeps Continue, Search, Favorites, Settings and Refresh reachable without creating another permanent row.
2. **Section tabs (one row):** Saved messages / Channels / Groups. Semantic selection and existing test tags preserved. Labels shorten on narrow devices; the tab row scrolls horizontally when needed.
3. **Forum-specific actions only:** Browse group media and Retry topics appear as a third row only while inside a forum topic listing.
4. **The remaining viewport:** `LazyVerticalGrid` (media, channels or topics) is the weighted primary content, with a small bottom padding. Media playback/cache and folder-focus restoration logic are unchanged.

There is no opaque duplicate collection panel and no extra descriptive paragraph above the grid.

## Adaptation
- Short Android TV (~720p / 400–540dp high): compact header; source and count shown in one line; TV controls keep D-pad targets; two-line media titles in dense cards but duplicate metadata is dropped below the image, preserving screenreader semantics.
- 1080p or 4K TV: same design language, wider toolbar can expose more actions. Normal media-card metadata fits where height permits.
- Phone landscape: two-row header and one-line poster title in very short windows; full accessible metadata; media grid takes priority.
- Phone portrait: compact title, Search/More, scrollable tabs; longer media metadata remains visible, and the grid expands to the rest of the page.
- Small screens with Back: Search moves into More if necessary instead of narrowing source names into unreadable slivers.

## Review and invariants
- Section switching, search, Back navigation, D-pad focus restoration, forum browse-all, topic retry and Telegram media loading still call their original callbacks.
- Movie/TV/Anime Extras and WebView paths are deliberately not altered.
- No new continuous animations, shadows, network images, permissions or native ABI branches.
- The selected folder is displayed in the header, including privacy mode.
- Unused content-dependent metadata is hidden **visually** only on short-window poster cards; `contentDescription` retains the title, subtitle and file-size information.
- Keep the four-tab? **No**: only the existing three authentic Telegram sections remain. Do not add a fourth synthetic catalog page.

## Verification gates
- JVM tests: short TV and phone landscape header-density/card-density policy.
- Android Compose instrumentation: 540dp TV toolbar must occupy at most 160dp before forum controls; phone forum Browse/Back/menu actions remain accessible.
- Integrated Library Compose test: the grid slot must receive at least 58% of the available page height in its normal empty-data test fixture.
- Existing Android Enterprise, TV/D-pad/Cast emulator, and debug prerelease ABI verification required before merge and publication.
- Real-device acceptance is still outstanding: 32-bit Android TV at 720p, TV 1080p, Android portrait and landscape, large-font accessibility, focus and 30-minute browsing/scrolling. Emulator assertions cannot prove every vendor firmware layout.

## Rollback
The change is isolated from playback logic and can be reverted by reverting the squash commit of the associated PR; previous debug APK releases remain available.
