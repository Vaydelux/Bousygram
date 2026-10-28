package com.vaydelux.bousygram.core.cast

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CastAccessRegistryTest {
    @After fun tearDown() = CastAccessRegistry.revokeAll()

    @Test
    fun capabilityIsRandomAndScopedToItsMediaResource() {
        val first = CastAccessRegistry.issue(41)
        val second = CastAccessRegistry.issue(42)
        assertNotEquals(first.resourceId, second.resourceId)
        assertNotEquals(first.token, second.token)
        assertEquals(41, CastAccessRegistry.authorize(first.resourceId, first.token))
        assertNull(CastAccessRegistry.authorize(first.resourceId, second.token))
        assertNull(CastAccessRegistry.authorize(second.resourceId, first.token))
    }

    @Test
    fun revokedCapabilityCannotBeReused() {
        val grant = CastAccessRegistry.issue(7)
        assertTrue(CastAccessRegistry.authorize(grant.resourceId, grant.token) == 7)
        CastAccessRegistry.revoke(grant.resourceId)
        assertNull(CastAccessRegistry.authorize(grant.resourceId, grant.token))
    }

    @Test
    fun malformedAndOversizedCredentialsAreRejected() {
        val grant = CastAccessRegistry.issue(19)
        assertNull(CastAccessRegistry.authorize(grant.resourceId, null))
        assertNull(CastAccessRegistry.authorize(grant.resourceId, ""))
        assertNull(CastAccessRegistry.authorize(grant.resourceId, "x".repeat(65)))
        assertNull(CastAccessRegistry.authorize("x".repeat(65), grant.token))
    }
}
