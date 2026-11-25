package com.vaydelux.bousygram.core.media

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingLockPolicyTest {
    @Test fun failedWifiReleaseStillAttemptsWakeRelease() {
        val calls = mutableListOf<String>()
        runIndependentStreamingLockActions(
            first = { calls += "wifi"; error("vendor failure") },
            second = { calls += "wake" },
            onFailure = { calls += "reported" }
        )
        assertEquals(listOf("wifi", "reported", "wake"), calls)
    }

    @Test fun failedDiagnosticsCannotPreventOtherRelease() {
        var released = 0
        runIndependentStreamingLockActions(
            first = { error("vendor failure") },
            second = { released++ },
            onFailure = { error("logger failure") }
        )
        assertEquals(1, released)
    }

    @Test fun bothActionsRunOnSuccess() {
        var calls = 0
        runIndependentStreamingLockActions({ calls++ }, { calls++ }) { error("unexpected") }
        assertEquals(2, calls)
    }
}
