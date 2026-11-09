# Experimental bounded Telegram playback and persistent Continue Watching

**Status:** Debug-only, **OFF by default**. This is not physical-device
qualification and not a strict 200 MiB disk limit. Enable under
**Settings → Clear cache → Experimental bounded Telegram buffering** for one
test video at a time on small-storage Android TVs.

## Problem and constraints

The stable TDLib player requests `DownloadFile(offset, limit=0)` for the
current movie. TDLib can continue downloading to EOF until playback exits.
The 200 MiB managed media-cache budget cannot reclaim an active movie's
downloaded bytes while that movie is still leased by LibVLC/Media3/mpv.

**Continue Watching already stores progress independently** using
account-specific `continue_watching.json` (AtomicFile) plus account-scoped
message-position preferences. Those records are under persistent app files,
not the TDLib media-download tree. Removing a TDLib media file must never
remove its Telegram message, channel, group, database or resume position.

## Experimental mechanism

- On a new player session, with a known source file size, an explicitly
  enabled **debug** preference selects bounded mode. Each `DownloadFile`
  request has a **positive** finite limit (never `0`).
- Movie duration and file size estimate the next ~120 seconds of media,
  but only to select how many **bytes** to request. The request is anchored
  at the *actual extractor byte offset*, never a guessed timestamp/byte
  position. Variable bitrate (VBR), sparse MP4 metadata, TS discontinuities,
  and MKV cues can make any time→offset mapping inaccurate.
- Unknown duration: 16 MiB request. Normal cap: 32 MiB; less than
  500 MiB of free internal space (or unknown free bytes): 8 MiB. Minimum
  eligible request is bounded by the actual bytes remaining before EOF.
- When the extractor reaches the last ~25% of the current window, its next
  unresolved byte read requests a new finite foreground window.
  True distant seeks still cancel stale in-flight reads and request the
  exact new byte offset, including near-EOF metadata. The stable unlimited
  path remains untouched when the experiment is disabled.
- On playback close/switch, cancellation acknowledgement schedules a
  protected, account-safe `TdApi.DeleteFile` of the closed *video bytes*
  only. The worker verifies the file is no longer downloading, has no
  active playback/reader lease, no active scheduler chunks, no prefetch,
  and is not manually pinned for offline use. It retries briefly rather
  than deleting busy media. Continue Watching metadata is untouched.
- Ordinary over-quota eviction no longer treats a Continue Watching entry
  itself as a media-download pin; active playback, offline pins and other
  live-resource safeguards still apply. The previous last-played grace
  remains for normal streaming; the explicit experiment exit cleanup does
  not rely on that exemption.

## Important limitation

TDLib does **not** promise that canceling/replacing a bounded download
deletes previously downloaded file ranges. It may retain earlier ranges
in the same native file until the entire file can be removed, or until a
later native cleanup. Consequently this is **bounded transfer requests**,
not a strict sliding disk ring or a guaranteed one-minute-behind,
one-minute-ahead local data set. File-size, media bitrate and cache hits
will affect the actual playable seconds represented by 8–32 MiB.

The 200 MiB target only applies to disposable media-cache allocations.
The installed APK/native libraries, database and explicitly saved offline
downloads are not part of that budget. There is no guarantee that total
app storage stays below 200 MiB.

## Physical Android TV qualification before enabling by default

1. Verify exact-commit ARM32 and ARM64 APK hashes. Enable the debug toggle,
   restart the video and keep a second device using the stable path as baseline.
2. Play an authenticated 1–2-hour MKV and TS video for 60 minutes. Check
   first frame, subtitles, audio, pause/play, ±10s seeks, rapid back/forth,
   EOF metadata, network loss and recovery, plus engine switches.
3. Use PR #137's `Capture-BousygramPhysicalSoak.ps1` for private-data-safe
   PSS/heap and device-wide `/data` free-space observations; also measure
   app-specific storage separately. Device-wide free-space deltas do not
   prove Bousygram alone consumed or reclaimed bytes.
4. Close a partially watched video. Confirm Continue Watching still shows
   the position and resume triggers a fresh stream, while the old TDLib
   media bytes can be reclaimed when unpinned and unused.
5. Return to saved channels, groups and topics. Verify catalog and
   auth/database metadata remain intact. Repeat after 10+ video switches,
   and with storage under 500 MiB free.
6. If native seek stalls, playback loops or persistent disk growth appears,
   disable the experiment in Settings. Production defaults remain stable.

This feature deliberately **does not** remove cache files using raw
filesystem operations and does not truncate an in-use TDLib file.
