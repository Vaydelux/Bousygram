# ADR-001: Telegram Core versus Extras Boundary

**Status:** Proposed, 2026-10-31. **Scope:** v2 planning. **Decision owner:** Product/repo maintainer.

## Context
Bousygram is currently a TDLib-backed local-first Android app. Proposed future TMDB/AniList catalogs, third-party embeds and virtual-mouse control bring different services, state, privacy and security risks. Loading future Extras dependencies alongside Telegram Media could destabilize auth, playback and startup.

## Decision
- Add a root shell owning only startup, mode choice, theme tokens and route contracts.
- Keep one existing Telegram session, catalog and PlaybackCore in TelegramMediaGraph; no duplicate TDLib initialization.
- ExtrasGraph **only** contains an offline maintenance placeholder in this roadmap. It owns no TMDB/AniList APIs, WebViews, embeds or virtual mouse code.
- No mutable shared global media catalog between product modes; later approved interoperability must use explicit versioned interfaces and per-domain adapters.
- Browser/iframe third-party content is untrusted: any future design requires allowed-origin, cookie, JS bridge, popup, download, navigation, DRM/rights and key management review. “Remove sandbox” is not a security plan.

## Alternatives rejected
1. Add future TMDB and AniList code directly to current Telegram ViewModels — creates data/permission and lifecycle coupling.
2. Rewrite the entire app into a multi-module project immediately — unnecessary risk before feature boundaries are proven.

## Consequences and acceptance
Slightly more route structure now; future integrations remain quarantined. Test Telegram login/logout, startup offline, Back from maintenance, deep links and process restart; assert no Extras service initialization or network request. Never merge an Extras implementation under this ADR without a new explicit product decision.
