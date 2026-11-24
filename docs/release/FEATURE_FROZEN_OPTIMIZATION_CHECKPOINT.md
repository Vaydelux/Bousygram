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

## Batch 04 — integration risk and account-bound resource hardening
- Scheduler reset already cleared file namespaces; no claim of a previously uncleared map. Snapshot and clear registered prefetch jobs under their registration lock, cancel outside it, and clear lease maps under their lock. Guard new prefetch registration with captured account generation; recheck after chunk await and guard initial/final state publication. Duplicate releases no longer emit telemetry. This does not prove all gateway operations are safe across account changes.
- Register only the initially admitted storage-pressure range rather than the original requested extent. Recheck sampled headroom per chunk; cap progress by the requested count and exclusive end. Saved-tail lookup validates the Long offset before converting to an Int index. The sampled cache reserve is not a physical hard limit.
- Account invalidation attempts all handlers even if native teardown throws an Error, retains the cause, and continues to fail closed. Database cleanup attempts each handle and retains failed ones for retry. Video index acquisition checks account identity atomically with instance creation; queued background indexing is cancelled before account database closure. No index data dropped on queue saturation; unbounded pending submissions during long playback remain a separate risk.

### Verified execution evidence (current session)
Environment: OpenJDK 21.0.12.1, kotlinc-jvm 1.9.0, bundled coroutines JVM jar. Direct git ls-remote failed DNS resolution; no Gradle, adb or Android SDK executables available. Two actual local JVM runs on transcribed AccountSession and extracted range helpers passed 2,023 then 2,032 assertions. Latest output:

```
PASS 2032 assertions: account cleanup/activation barrier, prefetch arithmetic, and saved-tail slices
```

The last run includes 2,000 assertions from 1,000 seeded arithmetic cases near Long.MAX_VALUE; remaining checks cover cleanup failures, same-generation admission, stale callbacks, concurrent invalidation and saved-tail bounds. Do not add the earlier 2,023 count to this total. An optional SDK-free harness is now stored under tools/ci/test_account_prefetch_smoke.sh to compile the actual repository policy files using a locally installed Kotlin coroutine jar; the newly committed script has not been run from a full checkout here. Existing/new JUnit tests remain unexecuted. No integration or performance qualification implied.

### Merge-blocking next action
Stop widening the uncompiled candidate: run the full exact-SHA Android qualifier, native/Room integration and UI/emulator checks, and real-device playback gates. Verify coroutine gateway operations at logout/relogin, player owner handoff timeouts (already-running main-thread actions are not cancellable), 2,000+ item catalog, background index backlog, and ARM32 long-session cache/RAM behavior. Defer R8 or binary removal until actual APK measurements. No APK or production release approved.

## Batch 05 — exact-source qualification and confirmed fixture repairs
- Reproduced a real Python SyntaxError in the candidate's native inventory test (Git blob f6999e34b6eb2a490166a07bdea0e5a1ead064). Remove the stray closing brace; fixed blob 7d05738176575c6bd77f6a105de93e6b74bc2722. Earlier transcribed passing fixtures did not detect this. Do not rely on those earlier results for repository qualification.
- Local ref admission now fails closed when git status cannot be read. Signed-release verifier quotes the aapt2 executable path; regression tests include SDK/output directories with spaces. No weakened production gate.
- Add source-only qualifier and independent catalog/layout smoke suite. Both require the local Kotlin coroutine jar and operate without Android SDK, network, GitHub Actions, or APK generation.

### Executed evidence, not a full checkout or Android build
Direct Git remote DNS still unavailable; JDK 21.0.12.1 and Kotlin 1.9.0 with bundled coroutine jar available, no Android SDK/Gradle/ADB. Files copied individually and their Git blob SHA-1 matched against the candidate tree; this is stronger than prior transcribed-policy evidence. Executed original four-file account/prefetch harness (2,032 assertions); nine production catalog/layout policy source files plus newly authored fixture/script (2,018 assertions across 400 overlap cases and 120 viewport/font/device configurations); corrected Python inventory test (six tests); corrected Git policy (eight cases); corrected signed APK verifier (eight mock SDK cases). All passed. The two Kotlin suites cover distinct fixture sets, total 4,050 assertions; the other 22 cases are shell/Python fixtures. No full Android compile, real signer, real APK, Compose rendering, performance or native integration evidence. The source-only umbrella script must be exercised on a complete checkout; individual commands ran in a partial copied tree. No actual device metrics.

### Reproduction
Set KOTLIN_COROUTINES_JAR to a readable local kotlinx-coroutines-core-jvm.jar. On a clean checkout of the exact candidate, run:

```bash
bash tools/ci/qualify_source_only.sh --candidate "$(git rev-parse HEAD)"
```

Then run the existing Android qualifier on the same SHA with SDK/native dependencies and required local configuration. Do not interpret source-only PASS as a release approval. No further app code expansion until actual Android compilation and regression evidence are available.
