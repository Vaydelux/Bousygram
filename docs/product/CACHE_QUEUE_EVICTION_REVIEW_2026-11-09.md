# Cache eviction policy — active video versus queued episodes

## Reviewed defect

`CacheManager.performCacheCleanup` rejected eviction for any media ID for
which `PlaybackSessionController.isFileActive` returned true. This is intentionally
broader than the currently playing file: session startup registers **all**
queued episode IDs so streaming authorizations can resolve future selections.
A 256/512-entry queue could therefore keep many cached, no-longer-needed
episodes protected simply because the player was open.

## Correction

The cache pruner uses `isCacheProtectedPlaybackFile` rather than
`isFileActive`. The narrower policy guards the currently playing file's
original TDLib ID plus its resolved alias during file-ID replacement.

Queued video IDs remain available through `isFileActive` for their existing
stream-access capability, but passive queue membership does not imply
a cache-retention exemption. Once an episode becomes current, its original
and refreshed file ID are protected immediately, even before a DataSource
lease opens.

No removal is authorized by this check alone. The existing pruner still
prevents deletion of pinned media, continue-watching files, the protected
last-played item, active downloads, prefetch/caching activity, byte-range
leases and currently accessed chunks. The ordinary dynamic quota and
recent-access protections also remain intact.

## Tests and qualification

JVM regression tests verify that a queued episode remains stream-accessible
but is not considered the current protected playback file, while moving to
that episode immediately changes which ID is protected. Stopping the
session clears playback protection.

Android CI checks compilation, JVM tests, lint and APK ABI matrix; emulator
smoke and TDLib generation are required before merge. No on-device 8 GB TV
free-space or long-playback measurements are claimed. 200 MiB is an evictable
media-cache allocation, not a hard limit on installed app/data storage.

Manual device checks: open a channel with many loaded episodes, download
multiple older videos, play one episode through seek/buffer, run safe prune,
verify that the currently active file and any offline pins remain playable,
then open a previously evicted queued episode and verify it re-downloads safely.
