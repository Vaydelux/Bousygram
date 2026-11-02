# Bousygram Extras — Future Architecture RFC

**Status: proposal only; no implementation authorized.**

## Vision

A separate Movies, TV Shows and Anime experience with optional TMDB and AniList metadata, and a future security-reviewed embedded browser/player with an optional TV virtual mouse. Telegram Media must remain fully functional even when Extras is disabled, unavailable or broken.

## Explicit current non-goals

- No TMDB or AniList provider clients, dual catalog code, API secrets, WebView/iframe, virtual mouse, ad blocker or third-party player
- No changes to TDLib, private Telegram auth, local stream gateway, core media-byte cache, release signing or permissions
- No unsandboxed third-party page execution
- The **existing** Telegram metadata enrichment references TMDB; this is legacy behavior to audit separately, not a new Extras feature

## Proposed future dependency boundary

The root mode shell owns route and theme contracts only. TelegramMediaGraph owns existing account/session, TDLib, private catalog, media bytes and native player. A future independently approved ExtrasGraph would own Extras-specific preferences, catalog ViewModel, provider adapters, provenance and artwork cache. An optional EmbeddedContent module would own WebView security policies, cookie and download handling and fallback virtual mouse input.

Provider records must carry explicit source identity:
- TMDB media type and provider ID, film/TV series, season and episode identity
- AniList anime ID and available episode metadata
- Cross-provider mappings are sourced and reviewable; never invent shared IDs or treat a fuzzy title match as an authenticated identity
- Extras playback history and provider keys must never reuse Telegram chat/message IDs or private session data

API keys requiring secrecy must not be placed in distributable Android source. Review each provider's current terms, quotas and accepted client patterns before planning backend access.

## WebView / iframe security boundary

A future WebView loading arbitrary HTML is untrusted remote code. Removing sandboxing and blocking popup ads is not a security policy. A future implementation should default to controlled, allowlisted HTTPS origins; deny unexpected redirects, unsupported intent schemes, popup windows, untrusted downloads and unsafe JavaScript bridges. Only permit a target-blank action when origin and navigation policy explicitly approves it.

Cookies, third-party storage, permissions, private URLs and local Telegram data must remain isolated. Review licenses, DRM, content rights and provider rules. An ad blocker can only be an imperfect UX filter, not a security or authorization guarantee.

TV input priorities would be: modal dialog > virtual mouse overlay > browser focus/history > Extras catalog > mode chooser. Remote Back must never unintentionally terminate Telegram Media or the app.

## Future readiness criteria

Before implementing Extras, obtain a separately approved ADR/RFC, source and rights review, key management and quota design, WebView threat model, account/cookie storage policy, TV D-pad and virtual-mouse accessibility criteria, load/performance limits, and provider failure/offline fallback behavior.

The current v2 implementation is limited to a responsive static maintenance page. **This document does not authorize or implement Extras.**
