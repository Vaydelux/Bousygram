# Reactive TV watched/resume progress — targeted Compose/UI refinement

Base main commit: `6faca60c79b4e35f00a53ede762fe81d26581a93`.

## Before: stale Compose memoization

`ModernVideoCard` cached its `isWatched` and `getMessagePosition` results with `remember(chatId, messageId, durationMs)`, so returning to the same card after playback did not invalidate either value. The 12-item Continue Watching rail could update while other cards continued displaying old Resume fractions or missing Watched badges.

## After: account-safe, low-memory invalidation

- `ContinueWatchingRepository.progressRevision` is a **content-free** StateFlow that advances only on account-safe ledger publication and account invalidation. It does not publish media titles, IDs, URLs or other private data.
- The TV Home composable collects this revision **once** and passes it to the visible `ModernVideoCard` instances and the hero Resume state. No per-card Flow collector, background poll, or network request was added.
- Each visible card reads the current account's already-loaded Continue Watching ledger snapshot after revision changes. Legacy resume SharedPreferences are read only when no entry exists.
- Pure `tvCardPlaybackIndicators` policy makes completed videos show Watched with no contradictory Resume pill/bar; incomplete entries show bounded 0–100% progress; unknown durations do not invent a percentage.
- The details control now has a 48dp focusable hit target (previously 40dp), preserving the existing card design.

## Validation

JVM unit tests for progress, completed status, threshold, ledger precedence, missing duration and invalid fallback. Android Enterprise Gradle tests and emulator UI regression must pass on the **exact PR head**. No screenshot appearance, focus behavior, low-memory physical performance, or actual Telegram account hydration is certified by these JVM tests.

## Physical TV checks (not yet performed)

With an authorized Telegram account and exact-build APK:

1. Start a partly watched item, exit Back, and confirm its progress badge updates immediately without force-closing the Home screen.
2. Finish the item naturally, return to the existing rail/card, and confirm Watched appears while the Resume percentage disappears.
3. Use Start Over and seek backward; verify a confirmed new position replaces the old high-water mark, not an unverified seek command.
4. Switch accounts and ensure the previous account's badges do not appear under the new identity.
5. Navigate all eight rail variants with an ARM32 remote, cycle focus into and out of the 48dp Details button, verify no trapping or clipping.
6. Repeat with Dark/Light/High Contrast at 720p, 1080p, 4K and enlarged font scales. Capture reviewed screenshots instead of assuming appearance.
7. During a long-session Android TV ARM32 soak, compare PSS/CPU/frame timings and cache I/O using the repository's existing privacy-safe physical capture tools.

Production qualification remains **blocked** until actual ARM32/ARM64 hardware evidence, signed build hashes and reviewer approval are present.
