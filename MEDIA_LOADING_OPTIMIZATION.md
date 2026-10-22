# Media loading optimization — 2026-10-22

## Goal

Make the video library feel responsive without changing Bousygram's existing Telegram login, streaming endpoints, or ExoPlayer player path. The library remains video-only; source tabs can still select Saved Messages, private chats, groups, supergroups, and channels.

## What changed

- **Progressive TDLib search:** video, document/video-file, video-note, and animation searches run concurrently. Each completed filter publishes a partial merged list immediately instead of waiting for the slowest filter. Updates are sequence-guarded so a slow callback cannot replace a newer snapshot.
- **Less duplicate query work:** removed the `PhotoAndVideo` filter because it overlaps video search and adds photos that the video-only grid drops. One page is requested from the video-oriented filters, with a larger but bounded candidate page for Documents because some video files are stored as documents. Each TDLib request is capped at 100 results.
- **Metadata before bytes:** listing a video only fetches TDLib message/file metadata. Focusing a card no longer warms up/downloads the first 2 MiB of the video. The existing ExoPlayer/stream preparation flow is still used after a user selects a video.
- **Prioritized initial media results:** the all-chat source catalog is scheduled after media discovery starts and waits for the initial media load for up to three seconds before starting its broad scan. This prevents a large account's chat enumeration from continuously taking priority over the first video page.
- **Bounded thumbnail downloads:** thumbnail work is coalesced by file ID, limited to three concurrent downloads, uses low TDLib priority, and times out after 12 seconds. A thumbnail failure should not block video metadata or player startup.
- **Faster card rendering:** repeated grid image requests use `AsyncImage`, remembered requests on the compact grid, dark placeholders, and no image crossfade. The existing cinematic hero transition remains, but its underlying image requests do not crossfade.
- **Automatic pagination:** Library and compact grid start loading the next TDLib metadata page when the user gets within six items of the end. A manual Load More button remains as a fallback. Video bytes are not downloaded to populate the next page.
- **Error clarity:** a failed individual search filter no longer leaves an error banner visible when another filter successfully returned playable videos.
- **Removed unused per-repository scheduler:** the repository no longer creates an idle single-thread scheduled executor that was not used by current request paths.

## Loading behavior

1. Select a source (Saved Messages, group, channel, or private chat).
2. Bousygram requests metadata using TDLib's specialized search filters.
3. As filters return, the grid renders available video items immediately; it does not wait for every filter to finish.
4. Poster thumbnails are downloaded separately at low priority. Missing thumbnails use a lightweight placeholder.
5. While scrolling, Bousygram requests additional metadata pages from TDLib before the user reaches the end of the grid.
6. Only after the user selects a video does the existing stream-preparation and ExoPlayer path become active.

This intentionally does **not** download every video file in a Telegram account upfront. That would create long waits, high mobile-data usage, and substantial storage pressure. All available video metadata is enumerated progressively as the user browses each selected source. Very large libraries are paginated rather than loaded in one giant in-memory request.

## Offline verification

Run from the project root:

```bash
python3 server/fastlink-proxy/run_mock_tests.py
python3 tools/mock-tests/test_chat_source_picker_revision.py
python3 tools/mock-tests/test_media_loading_performance.py
```

For the combined checks, run:

```bash
SKIP_KOTLIN_MOCK=1 bash tools/mock-tests/run_all.sh
```

To include the standalone Kotlin helper suite, omit `SKIP_KOTLIN_MOCK=1` and ensure `kotlinc` is installed. This does not replace Android Gradle, Compose, JUnit, or device testing.

## Required device verification

- Build and install with Android Studio.
- Check Saved Messages and channel/group tabs on a real account with both small and large sources.
- Verify partial results appear while the remaining TDLib searches are pending.
- Scroll repeatedly and confirm new pages appear without duplicates, skips, or a stuck spinner.
- Confirm rapid D-pad focus changes no longer trigger video-byte warmup.
- Select a video and confirm ExoPlayer still starts through the existing stream preparation path.
- Test large/slow network libraries, unavailable thumbnails, empty sources, and one or more search-filter errors.

## Verification boundary

The static/mock suites can validate source contracts and proxy behavior, but they do not demonstrate actual TDLib latency, full Kotlin/Compose compilation, or device playback. A successful Android Gradle build and real-device test are still required before release.
