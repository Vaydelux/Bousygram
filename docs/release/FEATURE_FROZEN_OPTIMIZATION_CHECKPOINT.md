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

## Additional account and timestamp hardening
- Capture the account before dispatch for all three thumbnail paths; atomically publish memory frames with account invalidation and propagate cancellation. Clamp preview timestamps before conversion to microseconds.
- A separate Kotlin 1.9 JVM harness executed 14 assertions against transcribed decoder replacement and quota policy logic using fake engine/owner objects and real temporary files. It verified failing diagnostics, repeated cleanup errors, LRU ordering, protected files, and linked-directory exclusion. This is not an Android build or execution of the repository JUnit suite.

## Batch 02 — catalog, indexing, and startup efficiency (2026-11-25)
Candidate extends the same temporary PR #233. No new dependencies, providers, UI features or playback engines.
- Telegram main/archive source scans stop after three non-growing short prefixes, preserving partial sources and allowing retry. Duplicate chat IDs are filtered before metadata requests. Existing folder pagination and retry semantics remain.
- Extras metadata: fixed 16 request lock stripes coalesce successful identical cache misses; cached responses bypass AniList rate token consumption. TTL/cooldown use elapsed realtime; expired entries are removed and request keys count toward the cache budget. HTTP transport limits active requests to three and rejects non-progressing streams. No weakened URL or redirect policy.
- WebView applies initial paused state when attached below STARTED and skips lifecycle calls for known dead renderers.
- Video index serializes writes and converts 25 items per transaction instead of eagerly duplicating the entire library; preserves cancellation and account ownership. Pending submissions may still queue; no claim of a bounded total backlog.
- Thumbnail/seek memory caches initialize on actual use, account invalidation and OS trim avoid initializing unused caches, and bitmap budgets account for allocation size. ARM32 seek snapshots use low-memory limits.

### Retained test evidence for this batch
Local Kotlin/JVM 1.9.0 (JRE 21): 18/18 standalone checks executed on verbatim policy functions ChatDirectoryProgress, catalogCacheEntryKiB, catalogDeadline, covering stalls/growth/oscillation, negative inputs, rounding, key accounting, and overflow. Not repository Gradle/JUnit or Android integration evidence. New repository regression tests authored for stalled directory, cache arithmetic and zero-progress input stream; not run here. Direct Git checkout blocked by DNS, Android SDK and Gradle unavailable. No APK or real device metrics. Prior isolated results are historical and are not included in this batch's count.

### Next qualification
Run full local qualifier and repository tests before merge; specifically exercise TDLib auth/2,000-item groups, rapid search/cancellation and cache coalescing, native/WebView background/resume and ARM32 memory, then Compose phone/TV viewport and focus matrix. Continue M7 native APK inventory/security audit only after baseline measurement. Do not disable mandatory release gates.

Additional execution evidence: 6/6 checks passed in a separate local JVM harness running the transcribed ExtrasHttpTransport with fake HttpURLConnection objects, the installed Kotlin coroutine runtime, and a test-only URL-policy stub. Verified three-worker cap, queued requests owning no socket, all six requests completing, oversized response rejection, and zero-progress read rejection. The actual URL security policy, production coroutine version and Android integration were not tested by that harness. Added an equivalent repository JUnit concurrency fixture, pending Gradle execution. Current batch: 24/24 isolated assertions executed (18 pure policy + 6 fake transport); not a full build or runtime performance qualification.

## Batch 03 — bounded UI state and local release tooling
- Telegram and Extras share first-seen-order/latest-payload bounded merge. Only retained media payloads remain referenced by the merge map; evicted identities still scale with unique inputs for exact deduplication. No changes to cursor semantics or UI design.
- TV focus restoration retries up to three frames before one navigation fallback. Touch devices restore the scroll anchor without forcing input focus. Failed requests are not reported as successful.
- Local qualification now accepts a clean checkout of an exact candidate SHA with `--candidate <full SHA>`, preserving the default main-only mode and all remote publication rules. Separate candidate artifact directory, local-only build marker, final tracked-source check and native inventory report.
- Signed release verifier now requires all retained native engines and C++ runtime per ABI, rejects multiple signers and mismatched literal package/version metadata, and refuses to delete existing artifact directories.
- APK inventory reports total APK bytes, compressed and uncompressed native bytes by ABI/library, and rejects missing libraries, unknown ABIs, duplicate entries and empty native libraries. It is not proof of ELF compatibility, signature validity, or runtime safety.
- Existing R8/JNI rules, baseline profile generator, dependencies and all engines retained. No size reduction asserted without a measured candidate inventory and release regression tests.

### Locally executed evidence (this session only)
- Kotlin/JVM 1.9.0 / JRE 21: 806 checks passed in standalone transcribed-policy harness (400 seeded overlap cases, two assertions per case; six focus checks). Not a Compose instrumentation test or full repository build.
- Python: six synthetic ZIP inventory unit tests passed using copied inventory implementation and fixture test source.
- Bash: seven local Git qualification-ref cases passed against temporary real Git repositories.
- Android SDK and Gradle not available; Git remote DNS resolution failed. All candidate Android integration, screenshot evidence, native performance and release-device gates remain outstanding. No debug APK built.

### Local candidate qualification command
With JDK 21, Android SDK, native dependencies, and required local configuration on a clean candidate checkout:

```bash
bash tools/ci/qualify_local_debug.sh --candidate "$(git rev-parse HEAD)"
```

Retain the exact commit, qualifier logs, SHA256SUMS, NATIVE_INVENTORY.json, and device/UI evidence. Test the full candidate before merge; use the existing main-only release policy afterwards. Do not publish local candidate builds as CI-attested artifacts.

Additional release-verifier evidence: seven synthetic APK cases passed locally using the transcribed Bash verifier and mock apksigner/zipalign/aapt2, with real ZIP archives. Cases covered valid matrix, missing LibVLC, multiple signers, literal package mismatch, literal version mismatch, nonempty output directory preservation, and symlink output refusal. No real signature or Android alignment was validated. All three new packaging/ref fixture suites are now included in local qualification. R8 rules remain intentionally unchanged pending real release-build analysis.
