# Bousygram v2 — QA / QC Acceptance Matrix

**QA** here means building quality into design, risks, ownership and repeatable process; **QC** means executing verification, discovering defects and supplying proof. Both apply before release.

| Area | Automated/host checks | Required device/manual checks | Exit standard |
|---|---|---|---|
| Startup / mode picker | route restoration, auth separation, no Extras init | cold/warm start, mode toggle, Back and rotation | existing Telegram auth still works, maintenance never calls Telegram |
| Saved / Channels / Groups | source type and real chat/topic ID assertions | real multi-group and forum/non-forum account | exact hierarchy, no topic bleed, accessible empty states |
| Grid / pagination | stable keys, no duplicate pages, focus/scroll saved | TV D-pad wrap, focus ring visibility, touch scroll | all visible actions reachable, no clipped border or wrong playback |
| Home cache | repository fake clock, coalescing, account invalidation | Home → Player → Home on warm and low RAM | cached data shown without blocking reload, no cross-account reuse |
| Player lifecycle | engine ownership + close priority tests | TV replay, pause, Back, sleep/wake, relaunch | only one session, no spontaneous app termination |
| Start Over | callback invoked once, target zero, Back disjoint | D-pad and touch while HUD/tray visible | reliably seeks same media to start; no close event |
| Seek | parser/seek preview bounds, OK/Back tests | MKV/MP4/TS, known/unknown duration, seek far and back | truthful time and preview, no file skip, measured latency |
| Related-media tray | source/topic scoping, first-row Up | remote down/open, grid, up/return, Back | no cross-source leakage or focus trap |
| Accessibility | Compose semantics/labels, test tags | TalkBack, large font, remote, low contrast | meaningful labels, active focus always visible |
| Density/animation | responsive previews and state tests | 720p/1080p/4K panel, phone/tablet orientation | no oversized dock; reduced-motion usable |
| Cache / storage | active-file/pin protection, account purge | low storage, long stream, repeated far seeks | no active-file deletion; physical usage measured |
| Extras boundary | dependency/static scan, no API client in core | open maintenance offline; Back to chooser | no TMDB/AniList/WebView/virtual mouse network side effects |
| Signing / APKs | pinned TDLib ABI, APK signature/hash verification | ARMv7 and ARM64 install + real stream | tested exact SHA, no fake release-ready claim |

## Baseline/target performance metrics (targets are provisional, not measured)

Record median/p95 and device model, ABI, Android version, network conditions:
- Cold start to usable mode picker and first interactive Home content
- Home return to first cached, focusable card; number of full TDLib re-enumerations
- D-pad focus-to-rendered-outline and missed-input count
- First video frame (not merely Media3 READY), rebuffer count/duration
- Seek preview response, seek commit to first new rendered frame
- Memory pressure, app private storage, TDLib payload bytes, low-storage fallback
- Scroll frame time/jank, blur/shader cost, token scaling on slowest TV

Initial *design budgets*, not release guarantees: no blocking full-screen refresh on warm Home return; at most one owner for each network/catalog refresh; app control focus visibly responds to every navigation move; no unexpected file change from Left/Right. Numeric timing SLOs are set only after Phase 0 evidence.

## Manual test fixture matrix

- TV-A: physical low-end **ARMv7** Android TV, smaller available storage
- TV-B: physical **ARM64** TV, remote with Back/Home/OK/hold
- Tablet: portrait and landscape with touch + keyboard if supported
- Phone: compact Android touch device with screen reader / font scaling
- Source account: Saved Messages, joined channel, basic group, forum group with multiple topics, archived/muted and deleted/inaccessible samples
- Media fixture: MP4, MKV, TS, long file, missing thumbnail, unknown duration, corrupt/inaccessible file and disconnected network

No live account credentials, private message titles or token-bearing URLs may appear in shared screenshots, logs or CI artifacts. Mark cases **NOT RUN** rather than passing them by assumption.

## Mandatory commands (run only if environment is available)

~~~bash
./gradlew --no-daemon --console=plain :app:verifyTdlibAbiCoverage :app:verifyTdlibBindingCompatibility :app:verifyTvHardeningContract
./gradlew --no-daemon --console=plain :app:compileDebugKotlin :app:lintDebug :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
~~~

Use exact GitHub workflow links where local build cannot run. A command not executed is explicitly NOT RUN; never infer output. Follow existing tools/ci for ABI matrices, digests and protected release.

## Evidence packet required to merge

Commit review table; diff and relevant source pointers; test run IDs + exact SHA; sample device model/ABI/OS; reproducible manual steps; before/after metrics where applicable; screens/videos stripped of private data; unresolved failures and rollback procedure. Release is blocked by any untriaged P0 regression or red required check.
