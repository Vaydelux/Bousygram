# Seek preview native memory hardening

Concurrent MediaMetadataRetriever sessions could decode duplicate frame
batches and increase native bitmap allocations. On cancelled, failed or
account-switched extractions, partially decoded frames were not explicitly
reclaimed until GC.

A coroutine Mutex now serializes native frame extraction off the main thread.
Waiting requests recheck the account-scoped LRU cache after acquiring it.
Cancellation is checked between each of eight samples, and is propagated
instead of silently converted to success. Orphan frames are recycled in finally
without recycling cached images that Compose may still display.

A saturating microsecond timestamp helper prevents huge/malformed media
durations from overflowing frame-selection time arithmetic.

The existing eight 160×90 frames and 2 MiB low-RAM LRU budget remain.
No HTTP media probing, player-seek or native decoder configuration changes.

CI qualification: JVM/lint/debug APK ABI, emulator Cast/media smoke, TDLib
generation. Physical ARM32/ARM64 measurements: peak PSS/native heap, GC
frequency, repeated D-pad seeking, account switch, and local MKV/MP4 preview
generation. No real-device memory reduction is asserted without measurements.
