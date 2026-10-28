# Streaming architecture

## Current paths

Telegram media uses native TDLib through `org.drinkless.tdlib.Client` and `TdLibTelegramFileGateway`. The `TelegramChunkScheduler` is the shared coordinator for direct Media3 reads (`TdLibDataSource`), loopback HTTP reads (`LocalStreamingServer` and `TelegramProxyManager`), focus prefetch, and cache ahead. mpv receives scoped tokenized loopback URLs. The direct Media3 path avoids the HTTP socket. ExoPlayer remains the primary engine and mpv is available for URL based playback and compatibility cases.

## Ownership

The TDLib client dispatches `UpdateFile`; the scheduler caches file state, suspends readers until the needed range is available, and reads from disk or `ReadFilePart`. It limits active per-file download windows to one, tracks active consumers through leases, and allows foreground reads to outrank background work. `CacheManager` separately schedules whole-file offline downloads and prunes inactive files. Playback sessions own foreground leases and player lifetime.

## Native transport

This checkout packages `libtdjni.so` for ARM32 and ARM64. It does not package `libtdjson.so` or x86_64 TDLib. The Java client has fail-closed fallback behavior when native loading fails. Do not treat JSON ABI functions as present in this build. The TDJSON migration remains a separate remote branch.

## HLS status

No segmented HLS pipeline is present. The former HLS shaped endpoint returned ordinary progressive stream URLs and is removed. Playback uses progressive streaming with byte ranges. Resolution variants continue to be represented as alternative Telegram files in the model, but this app path does not advertise them as HLS.
