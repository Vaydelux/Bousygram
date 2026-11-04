# ADR-002 — Defer Telegram runtime until Telegram Media is selected

Status: Proposed, pending Android CI and physical ARM32/ARM64 qualification
Date: 2026-11-04

## Problem

The app historically initialized TDLib, account-related indexing, native file
garbage collection, the local loopback HTTP streaming server, and the Ktor Netty
proxy in `BousygramApp.onCreate()`. `MainActivity` also eagerly acquired the
Telegram authorization ViewModel before the user selected a mode. These actions
incur native/heap/threads/socket work even when a user only opens the independent
TMDB/AniList Extras catalog, especially visible on 32-bit Android TVs.

## Decision

Separate **process-wide security setup**, which must happen on every launch,
from **Telegram runtime setup**, which begins on first Telegram Media entry.

At process creation:
- Keep `AccountSession.registerInvalidationHandler` installed before any account
  or Telegram client could become active. Never move it into a per-screen lifecycle.
- Refresh managed configuration and observe process foreground/background.
- Do **not** create `AuthViewModel`, load `tdjni`, instantiate native TDLib,
  create a loopback server, run a Netty proxy, schedule Telegram GC, or register
  the Telegram network observer until Telegram Media is requested.

On first Telegram Media entry:
- Show a simple focusable startup state with Retry and Back controls.
- Invoke `BousygramApp.ensureTelegramRuntimeStarted()` on `Dispatchers.IO`.
- A synchronized, retryable process-lifetime gate initializes the original
  Telegram services once: native library -> index/network observer -> file GC
  -> TDLib logging -> local streaming server -> proxy -> cache cleanup.
- Only when the gate is ready do we create the activity-scoped
  `AuthViewModel`; the existing Telegram auth and playback screen then renders.
- If the process is already foregrounded, explicitly refresh network state,
  prefetch policy and proxy rather than relying on another `onStart`.
- The runtime is **not** stopped when switching to Extras; active Telegram
  background playback / PiP / MediaSession sockets remain intact.
- All Telegram-only process lifecycle and memory-pressure work is skipped until
  the gate has successfully initialized.

Incoming Cast intents:
- Intent parsing remains in `MainActivity`; no automatic authorization bypass.
- A pending Cast payload routes mode selection or Extras to Telegram Media.
- Wait for the initial auth restoration instead of silently discarding a
  pending Cast during `AuthStep.Loading`.
- Before a Cast becomes a playback request, preserve all existing account,
  authorization, file, chat, and generation checks. An unauthorized request is
  discarded, not played by Extras.

On startup failure:
- Do not mark the gate ready or create the auth ViewModel.
- Display a retryable startup error and allow Back to mode selection.
- A retry attempts the bootstrap under the same exactly-once lock. Initializer
  steps are mostly already idempotent; error recovery across all partial
  native initialization states requires additional device qualification.

## Required acceptance tests

1. Cold start -> Extras -> browse three catalogs -> Back: no native Telegram
   client or Netty/loopback server, zero Telegram auth session creation.
2. Cold start -> Telegram: startup banner, auth restoration/login, Home, media
   stream, seek, library and logout continue to work.
3. Extras -> Telegram -> Extras -> Telegram: no duplicated socket or auth
   client; active playback/MediaSession isn't torn down on mode changes.
4. Pending Cast deep link at launch or while Extras: routes through Telegram
   initialization, waits for auth state, applies authorization checks, and
   creates playback only if ownership is valid.
5. Log out or authorization loss: privacy invalidation still revokes all
   account-scoped resources, even after previous mode switches.
6. Offline startup, missing native ABI, repeated user Back/Retry, process
   recreation and fast mode switching do not deadlock or double-initialize.
7. ARM32 / ARM64 TV and phone/tablet: compare cold-start wall time, native
   heap/PSS, Java heap, threads and open loopback sockets across
   `SELECT`, `EXTRAS` and `TELEGRAM` before claiming measured improvement.

Unit tests cover gate idempotence, concurrency, retry behavior, mode isolation,
and Cast routing. CI covers compile, lint, unit tests, APK ABI matrix and TDLib
API generation. CI is not proof of on-device startup or PiP correctness.

## Rollback

Revert the lazy-bootstrap PR as one isolated unit to reinstate unconditional
`BousygramApp.onCreate` startup and eager Telegram ViewModel creation. No
persistent schema migration or saved-account data conversion is involved.
