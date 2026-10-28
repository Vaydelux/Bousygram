# HTTP and HLS correctness

The application retains loopback progressive HTTP for URL based players. Stream URLs carry per-file scoped tokens checked by `StreamAccessRegistry`; the proxy binds to `127.0.0.1`. Both HTTP paths use the shared `TelegramChunkScheduler`. The Ktor path handles GET and HEAD and supports full and range responses, including unsatisfiable ranges through `HttpRangeParser`.

The former `/adaptive-stream`, `/hls/master.m3u8`, and `/hls/segment` routes were removed. The master playlist pointed directly at progressive `/stream` resources, while the segment route delegated to the same byte-range stream handler. There were no actual media segments or media playlists, so advertising HLS was misleading. Progressive playback and its scoped authorization remain available.

No external LAN endpoint, additional HTTP server, FFmpeg dependency, or media remuxing was added. HTTP disconnect, range, and token behavior still require the repository unit suite and device/client validation to establish runtime correctness.
