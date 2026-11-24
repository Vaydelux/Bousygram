# Feature-frozen optimization — batch 01 checkpoint

Baseline: 0d8ab4ce323a6bd3c54e5127f21a62215810fb05. Scope M0–M3. No new user-facing functionality, engines, providers or design changes.

## Implemented source changes
- Bound speculative scheduler requests by sampled headroom above 500 MiB and 32 MiB maximum; do not label a partial BackgroundFull request Cached.
- Track inclusive byte ranges without overflow or the exclusive endpoint; snapshot cancellation targets under the registration lock and cancel outside it.
- Attempt independent wake and Wi-Fi lock acquisition/release even after a platform error; redact exception details.
- Stream directory accounting without a wide File array; normalize exclusions and ignore symbolic links.
- Reuse successfully loaded offline-pin snapshots even when empty, scoped to the authenticated account generation. Do not mark unavailable preferences loaded.
- Add focused unit regressions and reconcile obsolete storage documentation.

## Qualification status
Prior isolated test totals were not independently substantiated by retained execution logs. Do not treat those totals as qualification evidence. Android Gradle, authenticated TDLib, emulator and real ARM32/ARM64 testing remain outstanding. No APK or production approval. Cached free-space estimates are not a physical cap; no measured speedup or memory reduction is claimed.

## Next work (in order)
1. Execute existing local debug qualifier and existing scheduler/lock tests, then physical matched baseline. Verify seek cancellation during overlapping prefetch and check actual available bytes after cleanup.
2. Measure native engine allocations, repeated switch/release, manual pause during buffering, MKV/TS and long-session PSS; fix only evidenced failures.
3. Qualify catalog loading for 2,000+ media; then Extras WebView lifecycle and responsive UI matrix.
4. Audit startup and APK native inventory; verify signed release gates. Preserve current stable feature set.

## Batch 01 extension — native and thumbnail lifecycle
- Protect adopted player from failing diagnostic callbacks and ensure rejected-engine teardown continues even if cleanup rethrows the original error.
- Keep generated PNG pruning inside direct app-owned directories; snapshot file metadata once per scan rather than repeating filesystem queries during sort.
- Bind queued seek extraction to its original account generation, skip non-local paths before allocating native retriever, and publish cache data only while the captured session is current.
- Bound thumbnail decoding across existing paths to one worker on ARM32/low-RAM and two elsewhere. Recheck identity and memory cache after waiting for local permits. No new functionality.
- Qualification: source review and authored regression tests; Android SDK and hardware gates remain mandatory. Update this record with actual retained test evidence only.
