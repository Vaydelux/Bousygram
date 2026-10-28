# Buffering and seeking

`PlayerBufferConfig` selects bounded ExoPlayer and mpv buffers from Android low-RAM status, memory class, form factor, and cellular state. Scheduler request priorities put playback at 32 and prefetch at 16; per-file download concurrency is one. Playback reads trigger the scheduler's foreground window, and a file update wakes waiters without a polling loop. Seek supersession cancels scheduler work outside the active playback window where possible; already downloaded cache data remains available.

The MP4 startup path now schedules the initial head window first. When the caller identifies MP4, it inspects available head bytes for a fast-start `moov`; if found, it skips the speculative tail request. If no head `moov` is found, the tail probe remains available for end metadata. Non-MP4 files do not receive this speculative MP4 tail request. Container identification must be supplied accurately by the caller; the direct data source uses the URI filename hint.

These are code-level behavior statements, not measured latency claims. No physical-device startup or seek benchmark was run. Variable bitrate time-to-byte estimation remains player/container dependent; the scheduler should prioritize actual byte reads requested by the player.
