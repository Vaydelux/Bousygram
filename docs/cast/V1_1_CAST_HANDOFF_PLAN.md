# Bousygram v1.1 — bounded cast and handoff milestone

Baseline: accepted v1.0.0 debug prerelease, main 37ce0ae406a0bb09cd6f77dc62a209d2fcd6a37c. Retain UNSLOP and all existing media engines. No cloud/VPS/hosted TDLib service. Temporary feature branch until qualified; do not replace the accepted tag.

## Implemented foundation
- MediaForgeCastPolicy produces in-memory HLS/DASH/MP4 receiver load plans with explicit play intent, VOD resume or live default position, qualified-host policy, no credential query or fragments, stable per-plan text track IDs, and selected VTT/TTML captions. An empty host set denies all; do not automatically trust a WebView-discovered host. The Default Media Receiver fetches its own resources: sender-side checks do not enforce receiver DNS, child manifest/segment, redirect, encryption or CORS policies. Require authorized, receiver-qualified media origins before enabling direct casting.
- SrtWebVtt is a strict bounded UTF-8 conversion utility, not a subtitle HTTP server. It preserves timestamps and plain text, rejects malformed input and does not transcode video. SRT selection must fail closed until the converted VTT can be delivered via a secure receiver-readable resource.
- TelegramTvHandoff contains only chat/message reference, position and Play intent; resolves a TV-local file ID after a paired peer is confirmed. It does not treat a phone-local TDLib file ID as globally meaningful. The pairing flag is a contract precondition, NOT authentication. Production wiring must use authenticated pairing plus explicit receiver acceptance and bind lookup and player launch to the same account generation.
- Tests cover format mapping, rejected origins, selected tracks, SRT parsing/budgets and receiver-local reference resolution. No UI, manifest, dependency, network listener, existing Cast controller or Telegram playback changes.

## Finite remaining gates
C1: create isolated Extras Cast sender session and lifecycle ownership, device discovery/permission, explicit user Cast action, validated MediaInfo/MediaTrack mapping, load acknowledgement and errors. Do not use Telegram's auto-load-on-connected observer for Extras. No arbitrary receiver URL loads; qualify chosen media origins including manifest children/redirects and CORS, or use the native guarded receiver path.
C2: wire user-imported SRT and discovered tracks, bounded temporary VTT delivery with authenticated short-lived scoped capability and receiver-compatible secure transport, Off/language switching, style where supported, cleanup; never leak credentials or forward WebView cookies.
C3: remote state, play/pause/seek/volume, ownership-safe disconnect and resume; avoid duplicate playback or wrong-source takeover.
C4: Bousygram TV receiver: select Cast Connect configuration or authenticated local pairing, confirm receiver/account authorization; use existing native Media3 guarded transport for Extras, and TV-local TDLib for Telegram. Handle replay, timeout, account switching, unpaired senders and peer identity. Do not expose arbitrary Intent URL or unauthenticated LAN listener.
C5: actual phone/TV and Chromecast trials with authorized test media, subtitles, VOD/live, network loss, screen off, storage budget, and denied/expired sessions. Production gates remain unchanged. No APK publication or merge until scoped tests pass.

Current state: foundation only; not yet usable as a Cast feature, no Cast SDK session or pairing network implemented. No hosting or new third-party dependencies introduced.

## SDK mapping increment
ExtrasCastRequestAdapter constructs official MediaInfo, MediaTrack and MediaLoadRequestData objects without loading anything or sharing Telegram's automatic session takeover. Selected external VTT/TTML tracks and explicit Off map to active track IDs, VOD resume is preserved, live leaves the default position untouched, and Pause intent survives request construction. Two Android instrumented tests are added to the existing emulator smoke runner. No new dependency, no receiver discovery or actual remote load. Requires fresh emulator verification. C1 remains incomplete until an isolated session is integrated, with explicit ownership and receiver acknowledgement.

## Isolated sender integration increment
- Added screen-owned ExtrasCastSender, lease tests and debug-only qualified host configuration. Explicit route selection then explicit Cast video, never automatically adopting an existing/resumed route or replacing known foreign media. SDK load result plus matching receiver content identity are required before remote commands. A bounded 20-second load confirmation deadline, stale callback rejection and ownership-safe teardown are implemented. Remote Play/Pause, relative VOD seek, volume, text track Off/selection (preserving non-text active IDs) are exposed in the opt-in panel. Phone playback pauses before load, remains paused on failure/disconnect, and returns to last reported VOD position when available. Local view is hidden during remote playback to avoid duplicate controls. No auto-resume after a failed or interrupted transfer. Connection loss may leave playback running on the TV; the UI instructs the user to check it.
- Default builds have an empty EXTRAS_CAST_QUALIFIED_HOSTS list; the controls are not visible. For controlled hardware qualification only, build debug with -PextrasCastQualifiedHosts=media.example.org,subs.example.org (replace examples with authorized, independently qualified exact origins). No wildcard, query-bearing URL, arbitrary WebView URL or credential forwarding. Default receiver's downstream requests still cannot inherit guarded sender transport; the allowlist is an explicit trust decision, not network proof. No production enablement.
- Current scope is mobile Media3 native Extras playback. LibVLC/WebView remain unchanged. SRT conversion is not yet wired to receiver-readable secure delivery. Native TV pairing/receiver and Telegram account-bound resolution still pending. First foundation commit pair 6db05b2 passed Enterprise, UI and all 128 emulator tests including SDK adapter tests. New sender/UI requires fresh verification and actual Cast hardware.

## Pairing and bounded caption import increment
- TV-issued high-entropy one-time pairing window (AES-256-GCM authenticated fixed-size reference, random 96-bit nonce, 128-bit invitation ID, 120-second monotonic expiry, 8 attempts, replay rejection and secret cleanup). The offer copy is intended for a QR exchanged in person; QR UI and LAN transport are not yet wired. This is single-use authenticated encryption based on possession of the offer secret, not persistent device identity or a completed pairing feature. Explicit TV approval remains mandatory. Do not expose key material in URLs/logs or use low-entropy PINs as raw keys.
- Receiver account handoff adapter resolves with the existing TV-local TDLib gateway, captures account generation before lookup, and permits one short pending-state publication only while that same account remains current. No hosted service, local-file-ID transfer, or modification of existing Telegram player. The actual receiver UI/launch must retain the same generation check; no exported unauthenticated launch.
- CastSubtitleImporter reads SRT from a user-provided stream or a caption belonging to inspected media. Bounded 512 KiB input, guarded HTTPS DNS, no redirects, 15s call deadline, cancellable read, rejects HTML and non-progressing streams. Conversion returns bytes only. Chromecast delivery still requires receiver-trusted HTTPS or an approved custom receiver; do not use public upload or insecure cleartext LAN as a shortcut. For Bousygram native TV a subsequent authenticated transport can deliver captions directly. File picker and native subtitle attachment not yet wired.
- Added focused JVM tests; fresh Android/Gradle qualification required. Keep existing sender opt-in and accepted v1.0.0 intact.

## Pre-merge review — 2026-11-25
Latest candidate review found a load deadline incorrectly checked after connection availability, active route loss not releasing local ownership, possible seek addition overflow, missing route-widget setup failure handling, buffering represented as a Play command, and implicit foreground resume risk while casting. Fixed these in the Extras-only sender/control/panel path and added pure seek/subtitle selection fixtures. No change to existing Telegram playback or production Cast flags. The previous 6db05b2 foundation passed all required checks; the subsequent f62443c run was superseded, and beccc301 two emulator jobs failed resolving the unchanged Kotlin 2.3.21 plugin before application compilation. Do not downgrade Kotlin or claim those jobs tested the app. Require exact corrected head build, unit and emulator results. Normal debug APKs still hide Extras Cast with no qualified origin configured; a controlled build with -PextrasCastQualifiedHosts=<qualified-origin-list> is needed. QR/network transport and converted-SRT receiver delivery are not complete. Do not call a merge an end-to-end usable casting release.

## Telegram phone-local proxy qualification increment
Existing debug-only MP4 Cast already uses phone TDLib -> session-scoped Ktor HTTP on a private Wi-Fi/Ethernet address -> Google Default Media Receiver, without VPS or hosted TDLib. Improved in-flight capability rechecks before and after each scheduler read, permission checks, revocation-before-drain, monotonic TTL and correct busy response without the media Content-Length. Existing loopback streaming uses the default continuation predicate. Scope Telegram Cast listener to the mobile player, refuse adoption of existing routes, isolate it from Extras and preserve local Play/Pause on transfer. This is still cleartext LAN HTTP, not encrypted; production gate stays disabled. No claim of zero legal/ban risk; only cast permitted content, respect Telegram content protection and API terms. Physical Chromecast qualification still needed. Standard receiver MP4 H.264/AAC first; no automatic remux/transcode. QR/native handoff and secure SRT delivery remain pending. Previous 24e92ec all three automated checks passed; new candidate requires exact-head verification.

## Receiver failure handling and conversion decision
- Candidate 4523eee passed media/Cast and responsive UI instrumentation but enterprise lint rejected a strong static CastSession reference (StaticFieldLeak). Changed to weak ownership, not lint suppression.
- Telegram direct Cast now explicitly admits MP4 candidates only; MKV and TS have distinct unavailable messages (remuxer not implemented). Unknown codecs remain unknown: successful LOAD request is not proof of decoding. Require acknowledged load and matching content identity, then playing/paused status; initial buffering remains pending with a 25-second deadline. Receiver idle/error, rejected SDK commands, route setup errors, missing LAN permission or file size are reported without dumping media URLs. Failure returns to local playback paused, retaining last known position. No automatic conversion retry or misleading success claim.
- Feasibility: use existing TDLib scheduler and private-interface proxy as input, never bind 0.0.0.0 or make the full cache public. A dedicated remux pipeline is not exposed by bundled libmpv/LibVLC alone. FFmpegKit upstream retired; require audited native build/license/ABI qualification or limited platform remux implementation. Direct first; only remux confirmed compatible codecs; audio conversion optional and separately bounded; full video encode opt-in only after thermal/throughput evidence. HLS VOD seeking needs timestamp-to-segment mapping, not output-byte-to-original-byte equivalence. Rolling segments and input cache must share actual reclaimable storage budget; do not use unbounded -hls_list_size 0. Receiver capability detection is device-specific (custom receiver canDisplayType); no blanket HEVC/AC3 rejection. Default receiver transport/CORS and protected content checks must be qualified; never bypass provider protections. No MKV/TS remux, codec probe or secure subtitle publishing implemented in this increment.

## Bounded Android platform remux qualification prototype
Implemented BoundedTelegramRemuxer using MediaExtractor + MediaMuxer (API 28+), with no new dependency or re-encoding. Accept only completed local files <=16 MiB, <=60 seconds, one H.264 video and zero/one AAC audio track, no silent discard of alternative tracks. Reject encrypted/partial samples, oversized samples, out-of-order timestamps, missing initial keyframe, malformed media and insufficient storage. One active scratch artifact at a time, 2 MiB sample buffer, 16 MiB copied payload ceiling and 20 MiB final output check; file must be explicitly closed and deleted. 500 MiB free-space floor plus output reservation. 20-second cooperative deadline checked between native calls, not a guaranteed interrupt of blocked native I/O. Cleanup failure prevents further admissions until process restart; nonempty scratch directory refuses new work. Native muxer metadata overhead is checked after finalization, not a hard filesystem quota. No actual shared 200 MiB budget accounting yet. This is a qualification-only utility, not a live remux engine and not yet called by the player or proxy.
Added Android instrumentation to require successful baseline MP4 copy with 144 video samples and nonempty audio, exercise MKV/TS with explicit supported/rejected results, single-artifact ownership, cancellation, oversize and cleanup. Rejection on MKV/TS is logged as unavailable, not counted as a qualified conversion. Emulator and physical receiver results remain pending. Next: actual platform results, compare sample counts/timestamps/audio, implement account-bound scratch lifecycle + authorized ranged artifact serving only for proven compatible files, then design a separately bounded rolling HLS pipeline for long or incomplete Telegram files. No full-video transcode, wildcard LAN listener, host service, feature enablement or production gate change.

## Remux output conservation gate
35023ab Enterprise and responsive UI passed; media suite produced an MP4 but read back 143 of 144 expected video samples. Exact origin of the missing sample was not established. Do not relax the frame count. Android MediaMuxer documents a zero-size EOS buffer with the desired end timestamp for final sample duration; added per-track EOS using the last measured positive timestamp delta (estimated, not exact duration metadata). Added mandatory output re-extraction and source-vs-output track count, first/last relative timestamps (2ms tolerance), initial video sync and bounded sample checks before Ready. Output verification failure rejects and cleans up; no runtime Cast integration. This verifies sample counts and track endpoints, not compressed payload equality, A/V decode quality or receiver playback. Split positive MP4 and MKV/TS instrumentation cases so results are independent. MKV/TS explicit unavailable results do not qualify those formats. Retain 144 fixture frames and compare source/output audio and video sample counts. Next: exact-head Android results, investigate any continued sample discrepancy without lowering acceptance criteria; do not serve outputs until qualified. No additional hosting or transcoding.

## Verified-artifact delivery preparation
55bf63e Enterprise/UI passed. Media suite ran 132 tests, 131 passed, one TS fixture failed: output and Android source counts matched, but both returned only 143 of the 144 expected video samples. MP4 positive control passed; MKV case passed but its prior test allowed explicit rejection and the archived instrumentation log does not capture logcat outcome. Do not claim MKV conversion qualified yet. The platform remuxer now admits only MP4 ftyp/EBML headers, explicitly rejects TS, and requires a positive MKV fixture with all 144 video samples plus source/output audio count conservation. Raw TS remains playable through existing native local engines; future HLS packing requires a different verified demux path. No relaxed sample-count assertion.
Added CastRemuxArtifact for verified output + captured account + source file ID, bounded 64 KiB reads, exact generated-file byte ranges, per-chunk authorization and account checks, and deletion on close. Existing debug-only LAN server has a separate internal startRemuxed entry point and serves authorized GET/HEAD through the existing session URL policy; does not modify TDLib source size/scheduler with output offsets. Explicit expected-owner grant issuance rejects account changes. Caller retains artifact on startup failure; server owns successful start and closes on stop. Failed cleanup prevents new starts. Added Android artifact range/revocation/cleanup test and stale-owner unit test. No actual UI caller yet: before wiring, capture account before TDLib resolution, verify source provenance/completion and message protection, budget shared storage, handle cancellation and native blocking calls, and run actual LAN/receiver tests. No production flag change, no hosting, no automatic transcoding. Existing Telegram/Extras playback remains unchanged except prior scoped Cast changes. Latest exact-head verification pending.

## Debug player wiring
173c571 passed all three required workflows and 133/133 media instrumentation tests, including strict MP4/MKV conservation, explicit TS rejection and artifact range/account revocation. Wired TelegramCastPreparation to mobile player: capture existing playback owner, verify GetMessage/GetChat/GetFile identity and saving permission, reject protected/secret/self-destruct content, resolve completed small MKV only in canonical TDLib files root, require conservative cache admission (all app cache + TDLib bytes <= 180 MiB before up to 20 MiB output), do not initiate downloads. Scratch in account cache. Cancellation checks between native calls, revoke matching URL on abandoned preparation, expected owner checked at server startup, generated output has independent size/duration, queue changes disconnect rather than automatically replace another source. Explicit messages for size, codec, storage, timeout, cleanup and missing source. Direct MP4 now also checks message identity/protection before LAN exposure. This remains debug-only, 16 MiB/60s completed clips, cleartext private LAN, no actual Chromecast hardware verification. Cache accounting is a conservative snapshot not a hard concurrent quota. Fresh exact-head build required, including bundled TDLib field compatibility. Full-length MKV, TS rolling HLS, secure SRT and native TV pairing remain pending. No production enablement or new dependencies.

## Queue ownership hardening — 2026-11-25
8723ca1 passed Enterprise, responsive UI and media/Cast (133/133 emulator tests). Pin the full chat/message/file identity before any asynchronous preparation. A different or missing selection disconnects instead of preparing another video on the existing route; repeated identical selections do not restart remux. Recheck the live queue index and full identity immediately before LOAD, and do not apply the previous Cast position to a different message. Added JVM selection-policy fixtures. New exact-head Android qualification and physical receiver trials remain required. No production enablement, new dependencies, storage-budget expansion or full-length conversion.

## Rolling HLS R1 — storage foundation
3aee77f passed all three exact-head workflows. Added private rolling segment storage, bounded streaming copies, explicit retirement, reader pinning, revocation checks, and shared-cache admission policy. See [rolling HLS qualification](ROLLING_HLS_QUALIFICATION.md). Native demux/mux, actual Android cache coordination, playlists, full-length seeking and Cast integration remain pending. No production flag or stable playback changes.

## Rolling HLS R2a — producer boundaries
8df61a4 passed all three workflows. Added bounded observational TDLib input, AAC-LC ADTS normalization, and incremental fMP4 framing with seven JVM regression cases. No actual native muxer/demuxer or rolling HLS delivery yet. See [qualification plan](ROLLING_HLS_QUALIFICATION.md). Keep production and stable playback unchanged.

## Rolling HLS R2b — continuous producer reference
Pinned Media3 1.10.1 fragmented writer omits tfdt and uses absolute offsets; do not adopt for HLS. Host FFmpeg pipe-to-pipe remux qualified MKV and TS across 12 independently decoded fragments with strict packet and timing gates; delayed moov required to preserve timeline. Added reproducible finite host qualification. Android native build and custom AVIO integration remain pending; no new dependency or playback change. See [qualification plan](ROLLING_HLS_QUALIFICATION.md).

## Rolling HLS R2c — isolated native qualification candidate
Added custom-AVIO JNI remux source, signed upstream source build recipe and ARM32/ARM64 qualification workflow. Fixed previous Enterprise documentation whitespace failure. Native compile, runtime, media conservation, licensing and security review pending. No app packaging or production changes.

## Rolling HLS R2d — JNI runtime gate
b769557 all four workflows passed including signed source ARM64/ARM32 native cross-compilation. Added source digest pin, exact ELF alignment validation, x86-64 Android ART runtime harness and independent media/timestamp verification. Fresh qualification pending; no APK integration or publication. See [qualification plan](ROLLING_HLS_QUALIFICATION.md).

## Rolling HLS R2e — runtime preflight and debug callback adapter
73f6e2e three app workflows passed; three native ABIs compiled, but runtime harness blocked by obsolete Java 8 target warning. Fixed target, canonical debug-only JNI declaration, bounded TDLib callback adapter and six tests. No live download ownership or production wiring. Fresh gates pending.

## Rolling HLS R2f — native runtime repair
Android runtime reached JNI at e3ed843 but TS malformed AAC and process exit 137 blocked qualification; seven negative cases passed. Explicit TS ADTS-to-ASC filter with stable configuration and one-to-one packet checks added; isolated runtime cases and failure diagnostics added without removing recovery or media gates. Enterprise dependency download HTTP 429 also pending. No app integration or production changes.

## Rolling HLS R2g — producer download ownership policy
Implemented isolated account-bound exclusive lease/window state machine, 2 MiB request limit, shared admission and uncertain-command quarantine with seven JVM regressions. No live commands or existing scheduler behavior changes. Next shared command lock/gateway fencing and runtime integration; native repair verification remains pending.

## Rolling HLS R2h — owner-bound window command driver
0ccc831 passed all four workflows; native Android x86-64 MKV/TS sample and timestamp conservation qualified. Added account-explicit debug TDLib gateway, mutex-injected bounded window command driver and six fake-gateway tests. Still no live caller; shared scheduler mutation fencing and safe cache integration remain pending.

## Rolling HLS R2i — shared gateway mutation fence
5ca21c9 all four gates passed. Real TDLib gateway start/cancel and offline pin commands now share account-fenced striped serialization; debug producer gateway requires a claim. Five regression cases added. No live producer binding: remaining raw mutation audit, lifetime/teardown and cache integration outstanding.

## Rolling HLS R2j — global producer lifecycle
Added single process-wide lifetime, debug shared-claim binding, exact mutex owner tokens and stop reconciliation after takeover; four lifecycle tests. Pre-cache cancellation, deletion/eviction, confirmed native teardown, actual rolling output and live integration remain pending.

## Rolling HLS R2k — deletion and pre-cache fence
Owner-explicit pre-cache cancel and non-waiting tracked cache deletion fence added with bounded deadline and three regression tests. No live producer or APK changes; raw logout teardown remains separate.

## Rolling HLS R2l — finite private output/store qualification
Native callback staging, independent-verifier interface, fMP4 parsing and rolling segment feed integrated in debug qualification path with six tests. No full-length streaming or real semantic verifier yet; no live receiver or native loading. Next actual semantic media verification and real JNI-to-store execution, then streaming backpressure and cleanup qualification.

## Rolling HLS R2m — sample-table verification
Independent strict fMP4 sample layout, decode continuity and H.264 IDR boundary gate implemented with three synthetic regressions. Real output acceptance and source/codec/presentation semantics remain qualification gates.

## Rolling HLS R2n — direct output streaming
Bounded synchronous JNI output assembler and private verified rolling store path implemented without converted-file staging. Six regressions. Finite native limits unchanged; real JNI/store fixture qualification and full-length integration remain pending.

## Rolling HLS R2o — real native fixture gate
Mandatory decoded-content conservation and exact Kotlin sample verifier/rolling store replay of actual Android output added to native workflow. Local FFmpeg reference hashes match for both formats; latest CI gate pending. No live conversion or physical-device certification.

## Rolling HLS R2p — native AAC clock integrity
Native decoded PCM hash regression blocks long-duration enablement. Candidate strict LC sample-clock repair, host C test and normalized AAC payload diagnostics added; all strict gates retained. Real Android result pending.

## Rolling HLS R2q — owned sequential source
Bounded native window renewal, pending-ticket recovery and one-shot source worker integrated into qualification coordinator. Five tests. No live TDLib or duration-cap increase until real media integrity and storage qualification pass.

## Rolling HLS R2r — AAC clock diagnostics
Address inherited TS quantization with bounded 1ms tolerance and exact rejection logging. MKV status 6 still requires Android evidence. Strict integrity gates retained.

## Rolling HLS R2s — long-duration host reference and retained-source pressure
Six-minute independent host rolling reference CI plus explicit storage-pressure reconciliation and regression. Native full-length profile and safe source reclamation not enabled; no live route.

## Rolling HLS R2t — controlled receiver delivery contract
Authenticated private playlist/init/segment lease and explicit control acknowledgement policy implemented. No network route or standard Cast receiver support; transport, RFC retention and native qualification remain gates.

## Rolling HLS R2u — transport-neutral dispatcher
Strict request routing, private headers, bounded body transfer and sticky route revocation implemented. Four tests; no network registration. Hosted six-minute reference emitted both success markers before workflow cancellation. Native integrity unverified.

## Rolling HLS R2v — isolated extended native qualification
Separate synthetic harness JNI profile with bounded 64 MiB/180s limits, GOP envelope, late cancellation, VmHWM telemetry and exact long-media integrity gate conditional on finite gate success. Existing app bridge unchanged; native repair and extended execution pending.

## Rolling HLS R2w — AAC priming semantics
Preserve one declared initial AAC frame for decode history while excluding it from presentation using an exact edit-list anchor; independently verify declared Matroska CodecDelay and exact presented PCM. Candidate repair pending Android result; all other media integrity gates retained.

## Rolling HLS R2x — controlled receiver integration qualification
Connect verified streaming output to private session/dispatcher with separate trusted consumption assertion, two-segment retention and sticky revocation. Three fake-verifier integration tests; no actual player or transport.

## Rolling HLS R2y — final consumption and local priming evidence
Automatic verified-init handoff and final segment consumption under arbitrary callback boundaries. Exact-blob local C arithmetic tests and independent source metadata inspection passed; Android output and Kotlin integration pending.

## Rolling HLS R2z — native integrity closure and authenticated receiver replay
All exact-head workflows passed, including six-minute Android JNI integrity, late cancellation and measured process memory. Added ephemeral mTLS loopback actual native media replay with peer pins and byte-consumption acknowledgement. New execution pending; actual player, pairing, LAN TLS and source reclamation remain gates.

## Rolling HLS R3a — source ownership closure
Mismatched start responses retain quarantine; failed/cancelled reads revoke the lease permanently and wipe returned bytes. Added regression cases. Native partial source reclamation and live source integration remain pending.

## Rolling HLS R3b — trusted pairing and Media3 playback gate
Ephemeral certificate pin enrollment and independent forward-only playback receipt policy, plus real Media3 synthetic fMP4 fragment instrumentation. Five unit tests and one decoder test; no live receiver or pairing UI. Fresh verification pending.

## Rolling HLS R3c — actual playback interval
Require trusted observed playback progress on verified source timeline, not just first frame/ENDED; reject seeks and protect pending pairing pins. Local two-fragment decode passed, Android receipt qualification pending.

## Rolling HLS R3d — finite one-player HLS receiver
Debug Media3 local HLS DataSource and single-player completed-window qualification, explicit playback/release retirement, pause/resume, and Android Keystore session identity. Actual instrumentation pending; live LAN, continuous rolling retirement, QR UI and source reclamation remain gates.

## Rolling HLS R3e — QR enrollment and release-before-retirement
Fixed-format invitation QR codec, Android bitmap/Keystore round-trip qualification and successful-player-release/one-shot retirement gate. No actual pairing screen or authenticated remote playback yet.

## Rolling HLS R3f — actual Android TLS receiver qualification
Bind paired Keystore identities to TLS1.3 loopback data delivery, inject transport into real Media3 HLS receiver and exercise playback and release before retirement. No LAN or remote ACK; finite completed-window only. Exact-head checks pending.

## Rolling HLS R3g — Android TLS compatibility repair
137/138 prior Android instrumentation tests passed; only Android encrypted playback handshake failed. Dedicated ephemeral Keystore raw ECDSA support, signing/transport preflight, negative pin gate, diagnostics and bounded deadlines added. Actual repair unverified; no live LAN or feature expansion until exact-head integration passes.

## Rolling HLS R3h — authenticated control and sustained finite playback
Versioned TLS GET/ACK transport, release-bound encrypted retirement callback, separate control capability and negative checks. Add 36s six-fragment finite receiver gate, keeping previous short gates. No live eviction or LAN; actual execution pending.

## Rolling HLS R3i–R3k — live retention, actual files and paced publication
Consolidate tested retention planner, scratch-file store and paced producer with verified-input adapter and explicit ENDLIST. Prior exact-head 139 Android tests passed. New host pacing test and regression groups passed; actual native-media verifier replay gate added, fresh qualification pending. No live player route, LAN listener or TDLib source reclamation.

## Rolling HLS R3l — live Media3 receiver candidate
Bounded retry ranges locally qualified; debug-only real-time synthetic live HLS DataSource and 72s Media3 test implemented, pending Android execution. Candidate detached while prior PR gates run. No LAN or TDLib changes.

## Rolling HLS R3m — live timing repair and integration gate
Native TS live replay exact-duration rejection prompted RFC-rounded target policy with unchanged verified timing and hard cap. Local seven timing/range/pacing regression groups passed. R3l live receiver candidate consolidated for actual Android qualification. Native root cause and live playback still require fresh execution.

## Rolling HLS R3n — encrypted live receiver
Prior 140/140 instrumentation and 144 native live replay units passed. Add paired TLS1.3 live loopback with bounded range retries, dynamic Media3 regression and revocation cleanup. New execution pending; separate-device LAN, pairing UI and source reclamation remain gates.

## R3o — debug TV receiver lab and orphan credential cleanup
Actual synthetic QR/consent UI, debug launcher entry, off-main identity operations, expiry and stop revocation, exact-namespace unowned Keystore sweep and instrumentation. Detached pending existing encrypted live gate. No remote phone pairing, no LAN or TDLib media.

## R3p — consolidated receiver enrollment qualification
Integrate debug TV launcher, explicit consent, credential orphan cleanup and real QR-pinned TLS enrollment with certificate possession proof. Prior 141/141 Android tests passed. New host enrollment negative and approval gates passed locally against equivalent dependencies; actual Android integration pending. No LAN, real remote receiver or source reclamation.

## Batch A1 — two-device foreground LAN qualification
Fix all observed manifest/trust-manager lint issues; introduce QR endpoint/certificate, permission-aware private network selection, signed enrollment, remote consent, mutually authenticated synthetic transfer, cancellation and host/Android regression gates. LAN UI and physical devices still need qualification; actual live media transport not connected. Keep main unchanged.

## Batch A2 — approved live LAN synthetic media
Split authenticated server/client endpoints, mTLS control offer, paced synthetic sender, real Media3 receiver with play/pause and terminal receipt, bounded deadlines, regression and debug-only Fragment lint dependency fix. New code not yet qualified. Actual physical devices, discovery, automatic reconnect and TDLib integration remain pending.

## Batch A3 — physical debug QA gate
Prior live private-interface emulator 147/147 passed; Enterprise only UsableSpace lint failure. Physical free-block admission via StatFs, exact-head/base gated uncredentialed ARM-compatible QA artifact (PR234 only, one-day retention), no production merge. Await complete fresh checks and actual artifact before physical-device qualification.

## Batch A4 — exact-merge QA packaging repair
686aff6 native 45758059816, Cast 45758059827 (147/147), UI 45758059820 (68/68) passed. Enterprise 45758059830 passed Gradle/lint/unit and debug assembly; packaging guard failed because PR metadata reported stale base 37ce0ae, while all five jobs checked out cffae9c with actual base 91167aff. Derive base from checkout parent and compare actual main, feature and merge refs. Require each companion job of latest exact-head run attempt to prove identical checkout via dynamic source-identity step and success, including both native jobs. Read-only pull request permission; no feature or main changes. Local jq predicate positive and three negative fixtures passed (missing jobs, mismatched merge, failed job). Fresh workflow and one-day artifact still unverified. No production publication.


## Completion batch B1 — selected local fMP4, no physical test requested yet

User requested software completion before physical-device testing. Prior qualified 85788f7 is a synthetic QA baseline, not a final release. Three debug ABI variants were published from exact tested merge 0d99273; subsequent code needs new qualification. This batch adds explicit local clip mode in both sender and receiver, distinct protocol marker, verified sequential fragmented MP4 input (H.264/AAC strict verifier subset), 16 MiB source cap, at most 12 fragments with existing duration bounds, no full-file copy, shared measured cache admission plus physical free-block floor, first-frame/actual ENDED receiver receipt, and a short completed-window integration test alongside the original synthetic live test. Local source is opened only after approval, not from an arbitrary URL or path. No ordinary MP4 conversion, general MKV/TS conversion, actual TDLib source ownership integration, device discovery, or reconnect implemented in this batch. Provider stalls and actual hardware remain unqualified.

Remaining release gates: (1) qualify this batch and maintain synthetic regression; (2) connect safe bounded native source ownership to account-authorized Telegram and general selected local media, without changing stable playback or assuming TDLib deletion; (3) implement foreground discovery without broadcasting invitation secrets or trusting unauthenticated network advertisements; (4) implement bounded mutually authenticated reconnect with explicit revoke/account-change/network-change handling and no stale media replay; (5) integrate normal player UX, device selection, controls and truthful fallback; (6) fresh three-ABI build and all software regression checks; (7) physical phone/TV qualification, then final merge/cleanup. Do not publish a new user-facing test build solely for this intermediate batch. Stable main remains unchanged.


## Direct selected-file transport candidate

For supported seekable local media, use progressive Media3 extraction directly from a single
consent-bound TLS channel instead of requiring FFmpeg or staging an entire conversion.
Source is an explicitly selected content descriptor, regular-file only, with verified bounds
and up to 64 KiB per request (8 GiB file admission cap). No filenames or filesystem paths are
sent. No persistent disk copy. Direct mode has a 16 MiB target player buffer, not a hard process
memory limit. Tests exercise exact nonzero ranges and actual playback of synthetic MP4, MKV and
TS fixtures. The debug session still expires after 120 seconds of player time and requires
matching modes on both devices; incompatible codecs, non-seekable providers and interrupted
direct sockets fail closed. This is not arbitrary codec conversion or long-duration casting.
Keep the native LGPL packaging gate unchanged. Do not distribute a new APK until current and
candidate qualification complete; test physical devices only after the intended feature scope.


### Direct-video lifetime follow-up (candidate)
Locally selected direct video has a four-hour maximum foreground playback duration with
30 seconds of operation allowance; pairing remains 120 seconds and consent remains 30 seconds.
Direct sender idle read timeout is five minutes. A watchdog polls revocation every 250 ms
and closes registered sockets on network loss, account invalidation or expiry.
No silent re-enrollment or persistent trust. Seek controls are conditional on decoder seekability.
The shorter qualification profile remains unchanged for HLS and reference handoff.
Long-duration runtime and physical-device compatibility are not yet qualified.

### Unified casting preview entry
The main mode chooser opens the private Cast devices activity directly. Telegram and Extras
remain the original routes. One media-mode enum replaces conflicting checkboxes, defaults
to direct local video, and hides synthetic diagnostics behind Advanced diagnostics. The
Telegram player pins the chosen reference and forces Telegram mode. Both peers still select
the same mode and pair by QR/manual invitation; NSD is an untrusted availability hint.

### Direct control lifetime follow-up
The separate direct-media control connection now carries a heartbeat every five seconds,
with a 30-second sender idle read deadline and a 15-second receiver control read deadline.
Loss of control revokes data access and propagates to the playback loop. The foreground
owner stops the heartbeat before sending the terminal playback result. The protocol is
versioned; old direct-media peers are refused. Control loss requires a new invitation;
only data socket interruption within a live approved control session can recover automatically.
Instrumentation asserts heartbeat exchange alongside exact byte-range recovery and playback.

### Direct decoder liveness follow-up
Direct playback now has a separate 30-second first-frame deadline and 45-second no-progress
budget when playback is requested. User pause does not consume that budget; position changes
(including seeking) reset it. Completion still requires a rendered frame and actual ended state.
Control heartbeat shutdown treats IO failures as fatal rather than attempting to send a receipt
on an ambiguous stream. These changes require fresh Android qualification.
