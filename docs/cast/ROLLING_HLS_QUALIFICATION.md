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
