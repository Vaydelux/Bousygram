package com.vaydelux.bousygram.core.cache

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamOnlyReclaimPolicyTest {
    @Test fun onlyNormalStoppedDebugSessionsScheduleReclaim() {
        assertTrue(shouldScheduleStreamOnlyReclaim(true, 25, true, false))
        assertFalse(shouldScheduleStreamOnlyReclaim(false, 25, true, false))
        assertFalse(shouldScheduleStreamOnlyReclaim(true, 0, true, false))
        assertFalse(shouldScheduleStreamOnlyReclaim(true, 25, false, false))
        assertFalse(shouldScheduleStreamOnlyReclaim(true, 25, true, true))
    }

    @Test fun retryNativeDownloadOrTransientMissingStatusWithoutDeletingActiveMedia() {
        fun decision(
            pinned: Boolean = false,
            playback: Boolean = false,
            other: Boolean = false,
            resolved: Boolean = true,
            bytes: Long = 4_096L,
            path: String? = "/tdlib/data",
            downloading: Boolean = false
        ) = decideStreamOnlyReclaim(pinned, playback, other, resolved, bytes, path, downloading)

        org.junit.Assert.assertEquals(StreamOnlyReclaimDecision.DELETE, decision())
        org.junit.Assert.assertEquals(StreamOnlyReclaimDecision.STOP, decision(pinned = true))
        org.junit.Assert.assertEquals(StreamOnlyReclaimDecision.STOP, decision(playback = true))
        org.junit.Assert.assertEquals(StreamOnlyReclaimDecision.RETRY, decision(other = true))
        org.junit.Assert.assertEquals(StreamOnlyReclaimDecision.RETRY, decision(resolved = false))
        org.junit.Assert.assertEquals(StreamOnlyReclaimDecision.RETRY, decision(downloading = true))
        org.junit.Assert.assertEquals(StreamOnlyReclaimDecision.STOP, decision(bytes = 0L))
        org.junit.Assert.assertEquals(StreamOnlyReclaimDecision.STOP, decision(path = null))
    }

    @Test fun nativeFilesMustBeDownloadedAndIdleBeforeDeletion() {
        assertTrue(isStreamOnlyReclaimCandidate(1_024, "/tdlib/media", false, false))
        assertFalse(isStreamOnlyReclaimCandidate(0, "/tdlib/media", false, false))
        assertFalse(isStreamOnlyReclaimCandidate(1_024, null, false, false))
        assertFalse(isStreamOnlyReclaimCandidate(1_024, "", false, false))
        assertFalse(isStreamOnlyReclaimCandidate(1_024, "/tdlib/media", true, false))
        assertFalse(isStreamOnlyReclaimCandidate(1_024, "/tdlib/media", false, true))
    }
}
