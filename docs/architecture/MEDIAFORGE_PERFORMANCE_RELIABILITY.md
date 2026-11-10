# MediaForge Phase 4 — bounded inspection and playback transport

This milestone is a resource-safety hardening slice, not proof of physical ARM32/ARM64 performance.

## Budgets and ownership

| Resource | Limit | Scope |
| --- | --- | --- |
| Observed streams | 20 by default, 1–100 accepted | Individual MediaForgeSession |
| Observed captions | 30 by default, 1–100 accepted | Individual MediaForgeSession |
| Concurrent source inspections | **1** | Individual MediaForgeSession |
| Inspection deadline | **40 seconds** default, configurable 1–120 seconds | Inspection including waiting for permit |
| Guarded HTTP dispatcher | **8 concurrent calls** total, **4 per host** | Shared native MediaForge OkHttp dispatcher |
| Native idle connection pool | **4 connections**, 3-minute keep-alive | Shared native MediaForge OkHttp pool |
| Manifest / media probe body | **65,536 bytes** maximum read | Each inspection HTTP response |
| Probe redirects | **4 redirects, 5 request attempts maximum** | Each inspected candidate |
| Guarded LibVLC range cache | **2 × 256 KiB = 512 KiB** | Each progressive source, unchanged |

**Why:** Source switching cancels the preceding inspection, but a short interval before cancellation finishes can otherwise allow overlapping DNS, TLS, and body reads. A coroutine semaphore prevents two inspectors from holding a session permit concurrently. The timeout applies to both permit acquisition and the source probe. Other sessions share a globally bounded OkHttp dispatcher.

The default settings protect low-memory Android TV sessions while allowing ordinary media-player concurrency. They are not benchmarked optimums. Do not increase these values without measuring buffering and latency on both ARM32 and ARM64.

## Cancellation through the response body

Previously, the resolver's coroutine cancellation hook was attached only while waiting for HTTP response **headers**. If headers arrived but a manifest or media body stalled, cancelling the session did not necessarily cancel its HTTP socket immediately.

\`readCancellableProbeResponse\` now owns the OkHttp response and completes its bounded read inside the async callback. The cancellation hook remains active until the read finishes. Session close, source replacement and timeout cancel the socket; a late response is closed without yielding a verified source. Streaming probe bodies are capped at 64 KiB. Binary MP4/WebM/TS probes no longer allocate unnecessary UTF-8 strings.

The separate \`mediaforge-player-adapters\` guarded progressive source already implements cancellable byte-range reads; its existing 512-KiB two-window cache and FD teardown are unchanged.

## Redacted, in-memory diagnostics

\`MediaForgeSession.diagnostics()\` returns a fixed-size \`MediaForgeSessionDiagnostics\` snapshot:

- Number of currently observed stream and caption candidates
- Inspections started, verified, rejected, cancelled and timed out
- Whether the most recent inspection is still active
- Last rejection as a fixed enum value, **not** a provider-controlled message

These diagnostics do not contain stream URLs, request headers, signed query parameters, Telegram credentials, account identifiers, manifest bodies, subtitle names or WebView cookies. The SDK does **not** send, persist, or log them. Existing LibVLC range counters remain confined to the current session.

These are count/diagnostic indicators, not proof of decoded frames, actual seek recovery, or guaranteed media compatibility.

## Test coverage

\`MediaForgeSessionTest\` checks request budget validation, source-switch cancellation, one concurrent inspector, timeouts, idempotent close and the absence of signed URLs in diagnostics.

\`MediaForgeProbeReliabilityTest\` uses simulated OkHttp calls, including a response whose body stalls after HTTP 200 headers. Both before-headers and after-headers cancellation must cancel the socket and let the worker exit.

\`MediaForgeDiscoveryTest\` validates the 8/4 network-dispatcher settings. The existing Android Enterprise, emulator, TDLib and LibVLC ABI checks remain required.

## Remaining production qualification

- Physical ARM32 and ARM64 TVs: prolonged playback, memory high-water mark, process death and WebView renderer recovery
- Real HLS/DASH providers: buffering latency, manifest redirects, CDNs, subtitle tracks and network interruptions
- Verify performance under concurrent native requests on poor connections, including compatibility with the bounded dispatcher
- Production release gate and signed APK identity; CI and emulator results cannot replace physical-device qualification

Telegram playback, its caches and Continue Watching are independent of this SDK.
