# Cache TDLib request backpressure — November 9, 2026

## Problem

The tracked cache sweep is limited to 256 IDs, but `getFilesBlocking`
previously sent all 256 `TdApi.GetFile` requests concurrently and
`deleteFilesBlocking` could also send hundreds of asynchronous
`TdApi.DeleteFile` requests at once. On a low-memory ARM32 Android TV, this
may contend with streaming's latency-sensitive TDLib requests.

## Implementation

- Submit cache GetFile and DeleteFile requests in **sequential batches of at
  most 32 outstanding requests**.
- Use one **4-second monotonic timeout budget for the whole operation**,
  rather than allowing every batch to block for another four seconds.
- If one batch hangs, stop submitting subsequent batches, log the timeout,
  and resume inspection on a future sweep; never block streaming indefinitely.
- Count down each completed callback in a `finally` block. Ignore late
  responses from a timed-out batch and stop on an account switch.
- Recheck file protection immediately before each DeleteFile issuance.
- Keep offline pins, currently played video, active byte leases/chunks,
  prefetch and continue-watching rules unchanged.

**Important:** An already-issued asynchronous TDLib deletion cannot be
cancelled atomically when playback changes. The current-video and active-lease
checks reduce the risk, but native device behavior still needs verification.
The existing 160 MiB Telegram cache target remains an eviction goal, not a
guarantee that entire app storage stays below 200 MiB.

## Validation

Pure JVM tests check complete 256-ID coverage with an in-flight bound of
32, smaller batches, invalid parallelism and one shared deadline. Exact-head
Android Enterprise Kotlin/JVM/lint/ABI, emulator media/Cast smoke and TDLib
generation are required before merge. Physical ARM32/ARM64 devices should
measure request latency, buffering/stalls, TDLib callback backlog and cleanup
duration before and after this change; no performance improvement is claimed
without hardware data.
