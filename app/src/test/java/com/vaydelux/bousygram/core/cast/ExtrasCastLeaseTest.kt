package com.vaydelux.bousygram.core.cast

import org.junit.Assert.*
import org.junit.Test

class ExtrasCastLeaseTest {
    @Test fun rejectsExistingAndUnsolicitedSessions() {
        val gate = ExtrasCastLease()
        assertFalse(gate.claim("one", false, false))
        assertFalse(gate.claim("one", true, true))
        assertTrue(gate.claim("one", true, false))
        assertFalse(gate.claim("two", true, false))
        assertNull(gate.beginLoad("two", "https://example.org/video.m3u8"))
    }

    @Test fun controlsRequireAckAndExactReceiverContent() {
        val gate = ExtrasCastLease()
        val url = "https://example.org/video.m3u8"
        assertTrue(gate.claim("one", true, false))
        val token = gate.beginLoad("one", url)!!
        assertNull(gate.beginLoad("one", url))
        assertFalse(gate.controls("one", url))
        assertFalse(gate.acknowledge(token, "two", true))
        assertTrue(gate.acknowledge(token, "one", true))
        assertTrue(gate.controls("one", url))
        assertFalse(gate.controls("two", url))
        assertFalse(gate.controls("one", "https://example.org/other.mp4"))
        assertFalse(gate.mayEnd("one", "https://example.org/other.mp4"))
        assertTrue(gate.mayEnd("one", url))
    }

    @Test fun lateCallbacksCannotReacquireRevokedLease() {
        val gate = ExtrasCastLease()
        val url = "https://example.org/video.mp4"
        gate.claim("one", true, false)
        val stale = gate.beginLoad("one", url)!!
        gate.revoke()
        gate.claim("one", true, false)
        val fresh = gate.beginLoad("one", url)!!
        assertFalse(gate.acknowledge(stale, "one", true))
        assertFalse(gate.controls("one", url))
        assertTrue(gate.acknowledge(fresh, "one", true))
        assertTrue(gate.controls("one", url))
        gate.revoke()
        assertFalse(gate.mayEnd("one", url))
    }

    @Test fun failedLoadCannotAuthorizeCommands() {
        val gate = ExtrasCastLease()
        gate.claim("one", true, false)
        val token = gate.beginLoad("one", "https://example.org/video.mp4")!!
        assertFalse(gate.acknowledge(token, "one", false))
        assertFalse(gate.owns("one"))
    }
}
