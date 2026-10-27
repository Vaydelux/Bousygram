# Streaming Engine

## Cache-first contract

Every reader supplies a file ID, byte offset, bounded length, priority, purpose, and consumer lease. The scheduler first verifies availability with TDLib prefix metadata, then reads the retained local file or `readFilePart`. Missing bytes cause one coordinated TDLib window per file. Exact and containing in-flight ranges are shared rather than downloaded twice.

`updateFile` events wake suspended readers. No playback path busy-waits for partial-file growth. ExoPlayer performs one cancellable, deadline-bounded scheduler read; both loopback HTTP implementations use the same scheduler contract.

## Lease and cancellation rules

- Consumers are `ExoPlayer`, `LocalHttp`, `Prefetch`, or `Offline`.
- Closing one consumer releases only its lease.
- TDLib cancellation is issued only after the final transient lease is gone and the file is not pinned.
- Seek requests cancel/deprioritize obsolete prefetch/ranges and open one new foreground window.
- Focus prefetch is low priority and cannot cancel an active playback consumer.

## HTTP behavior

- No `Range` header: `200 OK`, full `Content-Length`, streamed in bounded chunks.
- Valid single range, including open-ended and suffix: `206 Partial Content` with accurate `Content-Range`.
- Invalid or unsatisfiable range: `416` and `Content-Range: bytes */size`.
- Disconnects release the request lease and propagate cancellation without affecting other consumers.

The direct TDLib DataSource remains the ExoPlayer path. Loopback HTTP is retained because mpv requires a compatible URL source.

## Buffering

Media3 selects low-RAM, standard, or high-memory bounded profiles from `ActivityManager`. Scheduler forward windows also account for estimated bitrate, throughput, network type, and file duration. These are caps, not a promise to download an entire file.
