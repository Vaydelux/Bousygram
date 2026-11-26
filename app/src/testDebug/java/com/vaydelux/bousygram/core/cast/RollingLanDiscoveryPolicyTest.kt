package com.vaydelux.bousygram.core.cast

import org.junit.Assert.*
import org.junit.Test

class RollingLanDiscoveryPolicyTest {
    @Test fun rejectsUntrustedNamesAndBoundsResults() {
        val policy = RollingLanDiscoveryPolicy()
        assertFalse(policy.found("Private account or URL"))
        assertFalse(policy.found("Bousygram-123456789abc\nInjected"))
        assertTrue(policy.found("Bousygram-123456789abc"))
        assertFalse(policy.found("Bousygram-123456789abc"))
        repeat(15) { i -> assertTrue(policy.found("Bousygram-" + i.toString(16).padStart(12, '0'))) }
        assertEquals(16, policy.snapshot().size)
        assertFalse(policy.found("Bousygram-ffffffffffff"))
        assertTrue(policy.lost("Bousygram-123456789abc"))
        assertTrue(policy.found("Bousygram-ffffffffffff"))
        policy.clear()
        assertTrue(policy.snapshot().isEmpty())
    }

    @Test fun serviceTypeAndCollisionSuffixAreStrict() {
        assertTrue(RollingLanDiscoveryPolicy.matchesType("_bousygram._tcp"))
        assertTrue(RollingLanDiscoveryPolicy.matchesType("_bousygram._tcp."))
        assertFalse(RollingLanDiscoveryPolicy.matchesType("_http._tcp."))
        val policy = RollingLanDiscoveryPolicy()
        assertTrue(policy.found("Bousygram-123456789abc (2)"))
        assertFalse(policy.found("Bousygram-123456789abc (9999)"))
    }
}
