# Native playback engine owner: exception-safe handoff

## Review finding

`PlaybackEngineOwner.value` previously performed old MediaSession bridge
closure, native decoder detach/stop/release, **then** installed the newly
constructed player. On a vendor native-teardown error, the setter threw after
partially releasing the old decoder. The switch coordinator then treated
the new decoder as unadopted and destroyed it, leaving the owner referencing
a potentially destroyed player. This can strand playback on ARM32 TVs.

## Change

1. Close the retired MediaSession bridge first (and record any exception).
2. Publish the newly constructed decoder as the owned engine.
3. Attempt all old-decoder cleanup actions; log any failure *without
   reverting the owner* to the retired decoder.
4. On final `close()`, complete unregister, bridge closure and native
   teardown even when an earlier step fails; rethrow the primary failure with
   later errors suppressed, after native cleanup has been attempted.
5. Preserve single-session engine ownership, on-player-thread serialization,
   and idempotent close behavior. New engines are still disposed by the
   coordinator when adoption is genuinely refused after logout.

Unit tests cover old decoder release exceptions, media session bridge close
exceptions, replacement ownership and exact-once cleanup. Existing tests
for concurrent ownership and account disposal remain enabled.

Physical 32-bit/64-bit Android TV testing must still verify real LibVLC and
libmpv native faults, rapid switching, playback restart, audio session cleanup,
and heap/PSS behavior. CI checks alone cannot prove vendor JNI stability.
