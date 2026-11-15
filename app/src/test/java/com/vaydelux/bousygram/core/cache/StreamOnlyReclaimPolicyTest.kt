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

    @Test fun nativeFilesMustBeDownloadedAndIdleBeforeDeletion() {
        assertTrue(isStreamOnlyReclaimCandidate(1_024, "/tdlib/media", false, false))
        assertFalse(isStreamOnlyReclaimCandidate(0, "/tdlib/media", false, false))
        assertFalse(isStreamOnlyReclaimCandidate(1_024, null, false, false))
        assertFalse(isStreamOnlyReclaimCandidate(1_024, "", false, false))
        assertFalse(isStreamOnlyReclaimCandidate(1_024, "/tdlib/media", true, false))
        assertFalse(isStreamOnlyReclaimCandidate(1_024, "/tdlib/media", false, true))
    }
}
