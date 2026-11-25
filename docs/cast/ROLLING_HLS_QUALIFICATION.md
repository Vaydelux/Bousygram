# Rolling HLS qualification plan

This is a separate debug-only development track. No new playback route or
playlist is exposed by the storage foundation. Existing Telegram local playback,
small-clip remux, direct MP4 Cast, and production security gates stay unchanged.

## R1 — bounded segment storage (implemented, tests pending)
- Hard local limits: 32 MiB, six resident segments including retired leased
  segments, maximum 8 MiB per segment, maximum 12 seconds per segment.
  Aim for 4–6 seconds at valid random-access boundaries, not arbitrary cuts.
- Input must be a finite, independently verified segment. Storage copies
  through a 64 KiB buffer and checks declared length, contiguous sequence and
  presentation timeline. It cannot establish media decodability.
- Consumer acknowledgement explicitly retires segments. No automatic eviction
  of unread data. Active leases count against the budget until closed.
  Account/route revocation denies further reads; close and lease release reclaim.
- Shared admission checks measured app cache + TDLib cache + requested bytes
  against 200 MiB, and available filesystem space against a 500 MiB floor.
  Caller must provide fresh measurements including this scratch directory once.
  Recheck after staging. No independent 32 MiB allowance on top of 200 MiB.
  This is a policy and injectable boundary, not a global atomic cache reservation;
  competing TDLib/Coil writes can race the snapshot. No production wiring yet.
- Directory must be private, exclusively owned and empty. Crash-orphan cleanup,
  actual Android admission wiring and concurrent reservation coordination are R2.
  IO is synchronous/cooperative; no arbitrary network input or deadline guarantee.

## R2 — verified native producer and cache coordination
Inventory bundled native ABIs, licenses and exposed APIs. libmpv/libVLC playback
does not establish access to a supported incremental demux/mux API. Qualify an
audited producer for H.264/AAC first, using finite incremental source reads,
keyframe-aware boundaries, bounded staging, timestamps and sample conservation.
Prefer fragmented MP4 HLS with a verified init segment if supported. If a native
library is required, review source, license, reproducible ABI build and security
before adding it. Do not use Android's known lossy TS extractor as fallback.
A host FFmpeg fixture can establish reference expectations but not Android runtime
compatibility. Integrate storage with the existing account scope and scheduler,
including cancellation, orphan recovery, low disk and competing cache writes.

## R3 — authenticated delivery and seek semantics
Expose only verified segments through existing LAN authorization. Choose receiver
compatible secure transport before production. A sliding live playlist is not a
full-length VOD seek implementation. For full VOD provide an indexed timeline,
bounded on-demand regeneration around requested timestamps, explicit init/discontinuity
semantics, and client retention/retry rules. Never map generated offsets to TDLib
source offsets. Verify manifest rules, GET/HEAD, segment integrity, account switch,
expiry, disconnect, retries and restart. No public upload or hosted TDLib.

## R4 — device qualification and release
Test actual Chromecast and Android TV with authorized full-length MKV/TS,
long playback, seek both directions, network interruption, cancellation, low storage
and cache reclamation. Measure sustained disk usage and thermal behavior. Retain
the accepted stable release until exact-head regression and hardware evidence.

## Host reference experiment — 2026-11-25
FFmpeg 7.1.5 host test: synthetic 72-second 320x180 H.264/AAC MKV and TS generated from the same samples, converted without video re-encoding to fMP4 HLS, 12 segments each. Both retained 1,728 video packets and 3,376 audio packets and decoded with -xerror. Largest segment: MKV 540,241 bytes; TS 540,016 bytes. TS initially failed because AAC carried ADTS headers; explicit aac_adtstoasc bitstream filtering resolved it. This is evidence for a native producer design, not Android runtime, exact timestamps, device decode, rolling eviction, full-length seeking or receiver qualification. Host test intentionally writes a complete temporary 72-second reference playlist, not production rolling storage. Reproduce with bash tools/ci/qualify_rolling_hls_reference.sh; FFmpeg with libx264, ffprobe and Python 3 required on host.

## R2a — producer boundaries (2026-11-25)
8df61a4 passed Enterprise, media/Cast and responsive UI workflows. Added a serialized 256 KiB account/route-bound TDLib read adapter with known-length clipping, a 5-second cooperative read timeout, explicit pending versus EOF, and stale-file-alias rejection. Uses existing observational Metadata scheduler requests: it cannot start or reposition playback downloads; producer-owned bounded download/lease protocol is still needed for missing bytes. Added strict one-frame MPEG-4 AAC-LC ADTS normalization (no CRC, mono/stereo, stable configuration) and incremental fMP4 output framing with bounded init/prefix/moof/media sizes, complete mdat, contiguous mfhd sequence, mvex and tfhd/tfdt/trun presence checks. This is structural validation, NOT a demuxer, sample-conservation proof or complete media verifier. No playlist is emitted and no new Cast source is enabled. A finite synthetic stream tests exact bytes, truncation, oversized input, gaps, revocation and ADTS limits; fresh exact-head Android workflows required.

Media3 FragmentedMp4Muxer is a candidate for the current 1.10.1 stack; its public API supports channel/stream output and interleaved encoded samples, but rejects out-of-order B-frames (https://developer.android.com/reference/androidx/media3/muxer/FragmentedMp4Muxer). Qualify the exact pinned version and fragment emission, memory, DTS/PTS, init compatibility and sample conservation before adopting. No new dependency, unsupported native symbol or platform TS extractor introduced. Next: integrate an independently qualified MKV/TS demuxer with source ownership, mux and per-fragment semantic verification; test actual host/Android streams, then wire rolling store and receiver.

### R2a host evidence
Compiled the committed parser, AAC normalizer and budget sources with host Kotlin/JDK 21; compared Git blob hashes (parser 74a75810519d6c71e318f55a61b340c4ea9e5e8f, AAC bff39204749e34ce2b1264603a28191716788b4c, budget 60e717c33e4f09ca4a2c3c88663d99320bd077b1). FFmpeg 7.1.5 generated 24-second synthetic H.264/AAC MKV and TS sources. Both HLS outputs passed exact init + four fragment byte comparisons and truncated-mdat rejection; the shared ADTS fixture passed normalization of 1,126 access units (AAC-LC 48 kHz mono) preserving raw payload bytes. Both playlists decoded without reported errors using -xerror. Host-only checks of structural framing/normalization; no Android TDLib source test or receiver qualification. Reproduce with bash tools/ci/qualify_rolling_hls_boundaries.sh (Kotlin, JDK, FFmpeg with libx264 required). The script packages the host experiment; it is not an Android native producer.

## R2b — continuous producer reference and pinned muxer decision (2026-11-25)
d084c38 passed all three exact-head workflows. Inspected Media3 1.10.1 tagged source (https://github.com/androidx/media/blob/1.10.1/libraries/muxer/src/main/java/androidx/media3/muxer/FragmentedMp4Writer.java): createTrafBoxes writes tfhd with absolute moof position and trun but no tfdt. The current HLS framing gate requires tfdt; RFC 8216 section 3.3 requires tfdt and movie-fragment-relative addressing. Do not add this exact muxer or weaken the gate for HLS. Future Media3 versions would need independent review; no playback dependency upgrade.
Ran host FFmpeg 7.1.5 experiment using synthetic 72-second 160x90 H.264/AAC MKV and TS, nonseekable input pipe and output pipe, copy only, six-second keyframes, AAC bitstream filter, default_base_moof and frag_keyframe. First attempt with empty_moov showed 21 ms MKV video relative timestamp drift and failed 2.1 ms tolerance. Adding delay_moov passed with max MKV drift 0.5 ms, TS 0 ms, initial A/V origin difference 0 ms, 864 video and 3,376 audio packets per format preserved. All 12 fragments per format independently probed and decoded with init + fragment only, tfdt and relative addressing checked, first video packet keyframe. No complete output movie written by the final pipe experiment. Simulated six-segment consumer window peak payload 1,217,061 bytes MKV and 1,219,869 bytes TS; total emitted media 2,427,401 and 2,431,893 bytes respectively. Peak excludes init, one verification copy, input, process memory. Test stores finite fixture packet arrays; not runtime memory proof. Reproducible version adds finite fixture caps, external deadline, explicit 12-fragment and packet count gates, and verifies before retaining output. The committed wrapper itself has not yet run in Android CI.
Host CLI is not an Android dependency. Native Android libavformat/libavcodec/libavutil development toolchain and reproducible ARM32/ARM64 build not available in this environment. Next concrete implementation: audited minimal pinned FFmpeg native build, custom AVIO read/seek callbacks (with producer-owned TDLib window and cancellation), output callback, enforce relative addressing and per-fragment sample/timeline checks before actual rolling-store publication. No GPL host binary is shipped; build/license/security/ABI review required. Runtime cache coordination, full-length VOD seeking, receiver delivery and hardware verification remain pending.

### Android structural gate hardening
Reject absolute base-data offsets, absent default-base-is-moof, invalid track IDs, malformed decode-time fields and missing/negative explicit track-run offsets. This is a strict structural subset, not proof of sample addressing or conservation. Synthetic negative regressions added; require fresh Android checks and real FFmpeg boundary regression. No receiver wiring.

## R2c — isolated Android native bridge candidate
Previous a79063a UI and emulator passed; Enterprise stopped on trailing whitespace in this document before app verification. Removed whitespace without disabling the gate. Added isolated JNI C remux source using custom sequential AVIO read/write, pinned signed FFmpeg 8.1.3 build recipe for ARM64/ARM32 with NDK r28c and a PR-only read-only native qualification workflow. No APK packaging or new app dependency. See tools/cast-native/README.md. Native compiler/development headers and source download unavailable locally; no native build or Android execution claimed. Candidate remains finite and requires strict output conservation, fragment and callback tests before any app integration. Source security/license review and actual ABI evidence pending.

## R2d — Android JNI runtime qualification gate
b769557 passed all four workflows. Native job 139229029834 verified FFmpeg release signature, source SHA-256 7138d28c96d9d3e3af4ee3d8cad72741f8ffb40da90c1112235dea3ecd3178a3, ARM64 ELF64/AArch64 and ARM32 ELF32/ARM with four 0x4000-aligned LOAD segments each. Pinned that observed source digest as well as the release signature. Tightened ELF alignment/congruence verification. Added x86-64 qualification-only build and separate shell-owned Android ART process using DEX harness; no production APK, credentials or library artifact publishing. Synthetic 72-second MKV/TS, deliberate 4093-byte short reads, zero/oversize read, callback exception, write refusal, cancellation, positive reentry. Host ffprobe/FFmpeg validates every standalone fragment, 864 video and 3376 audio packets per source, relative PTS/DTS and A/V offset within 2.1 ms. This is a new gate, not a recorded pass: exact-head runtime and host validation pending. ARM physical execution, native heap limits, producer-owned TDLib windows, rolling cache integration, seeking, licensing and distribution security review remain pending. No stable playback changes.

## R2e — runtime preflight repair and debug callback integration
73f6e2e passed Enterprise, Cast/media and responsive UI; native workflow 45632319849 compiled all three ABIs but JDK 21 rejected obsolete Java 8 target warnings under -Werror before DEX generation. Target Java 11, keep -Xlint:all -Werror. Move the single JNI declaration into debug-only app source and have the standalone harness compile that exact file. Added RollingHlsNativeCallbacks adapting RollingHlsSource to JNI synchronous sequential input and bounded staged output, with thread confinement, revocation, explicit Pending failure, pre/post-write admission and one-shot cleanup; six debug JVM regression cases. No native library auto-loading or call from player. Caller owns scratch and must discard on failure or unverified media. This is finite qualification (16 MiB input/20 MiB output) and observational input only, not full-length conversion. Inspected TelegramChunkScheduler: foreground reads can issue open-ended requests when stream-only mode is off; existing background fetch shares windows. Do not reuse these for an exclusive native producer until a scheduler-level ownership protocol with account-bound command fencing and cache reservation is verified. No live missing-byte fetch, receiver delivery, packaging or stable playback changes. Fresh verification pending.

## R2f — explicit AAC filtering and runtime crash attribution
At e3ed843 the three native ABIs compiled; JNI runtime reached Android. Seven negative scenarios passed, then TS write-refusal execution logged malformed AAC and the process exited 137. Cause of termination itself unproven. FFmpeg n8.1.3 movenc.c disables AUTO_BSF under empty_moov; confirmed from tagged source. Added explicit aac_adtstoasc AVBSFContext for TS, validated every ADTS packet (LC, no CRC, one raw block, mono/stereo, exact packet length and stable ASC), checked one-to-one filtered size/PTS/DTS/duration and required no buffered filter output at drain. Retains original strict media/timestamp verifier. Separate per-format and per-case ART process tests plus original same-process recovery test; capture recent logcat/memory/process diagnostics on failure, do not bypass crash. Enterprise failure was existing org.json Maven Central HTTP 429 before tests, not a source assertion; no dependency or gate changes. Fresh qualification pending. No live TDLib window or APK integration while native media integrity remains unverified.
