package com.vaydelux.bousygram.core.observability

/**
 * Per-playback-session recovery outcome counts. No file IDs, URLs, account
 * identities, decoder errors, or media names are stored here.
 *
 * A seek is counted as successful only after the decoder confirms its target.
 * Rejected, timed-out and superseded requests are different outcomes and must
 * never inflate average successful seek latency.
 */
internal class PlaybackRecoveryCounters {
    data class Snapshot(
        val rejectedSeekCount: Int,
        val timedOutSeekCount: Int,
        val supersededSeekCount: Int,
        val unresolvedSeekCount: Int,
        val fallbackAttemptCount: Int,
        val fallbackSuccessCount: Int
    )

    private var seekPending = false
    private var rejectedSeekCount = 0
    private var timedOutSeekCount = 0
    private var supersededSeekCount = 0
    private var fallbackAttemptCount = 0
    private var fallbackSuccessCount = 0
    private var fallbackPending = 0

    private fun increment(value: Int): Int = if (value < Int.MAX_VALUE) value + 1 else value

    @Synchronized fun seekRequested() {
        if (seekPending) supersededSeekCount = increment(supersededSeekCount)
        seekPending = true
    }

    @Synchronized fun seekRejected() {
        if (seekPending) supersededSeekCount = increment(supersededSeekCount)
        seekPending = false
        rejectedSeekCount = increment(rejectedSeekCount)
    }

    /** Return true only for a claimed decoder-confirmed seek. */
    @Synchronized fun seekCompleted(confirmed: Boolean): Boolean {
        if (!seekPending) return false
        seekPending = false
        if (!confirmed) timedOutSeekCount = increment(timedOutSeekCount)
        return confirmed
    }

    /** Pending seek invalidated by reprepare, engine switch or stream teardown. */
    @Synchronized fun abandonSeek() {
        if (seekPending) {
            seekPending = false
            supersededSeekCount = increment(supersededSeekCount)
        }
    }

    @Synchronized fun fallbackAttempted() {
        fallbackAttemptCount = increment(fallbackAttemptCount)
        fallbackPending = increment(fallbackPending)
    }

    /** A late or duplicated success must not count twice. */
    @Synchronized fun fallbackCompleted(success: Boolean) {
        if (fallbackPending == 0) return
        fallbackPending--
        if (success) fallbackSuccessCount = increment(fallbackSuccessCount)
    }

    @Synchronized fun snapshot(): Snapshot = Snapshot(
        rejectedSeekCount = rejectedSeekCount,
        timedOutSeekCount = timedOutSeekCount,
        supersededSeekCount = supersededSeekCount,
        unresolvedSeekCount = if (seekPending) 1 else 0,
        fallbackAttemptCount = fallbackAttemptCount,
        fallbackSuccessCount = fallbackSuccessCount
    )
}
