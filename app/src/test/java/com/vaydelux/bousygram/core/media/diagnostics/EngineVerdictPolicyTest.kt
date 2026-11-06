package com.vaydelux.bousygram.core.media.diagnostics

import com.vaydelux.bousygram.data.store.SelectedPlayerEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineVerdictPolicyTest {
    @Test fun legacyAndCurrentEnginesRoundTripWithoutDefaultingCorruptValuesToExo() {
        assertEquals(SelectedPlayerEngine.ExoPlayer, EngineVerdictPolicy.decodeEngine("ExoPlayer"))
        assertEquals(SelectedPlayerEngine.MPV, EngineVerdictPolicy.decodeEngine("MPV"))
        assertEquals(SelectedPlayerEngine.MPV, EngineVerdictPolicy.decodeEngine("MpvPlayer"))
        assertEquals(SelectedPlayerEngine.LIBVLC, EngineVerdictPolicy.decodeEngine("LIBVLC"))
        assertNull(EngineVerdictPolicy.decodeEngine("AUTO"))
        assertNull(EngineVerdictPolicy.decodeEngine("CORRUPT"))
        assertNull(EngineVerdictPolicy.decodeEngine(null))
    }

    @Test fun recordsExpireAndFutureTimestampsAreRejected() {
        val now = 2_000_000_000_000L
        assertTrue(EngineVerdictPolicy.isFresh(now, now))
        assertTrue(EngineVerdictPolicy.isFresh(now - EngineVerdictPolicy.TTL_MS + 1, now))
        assertFalse(EngineVerdictPolicy.isFresh(now - EngineVerdictPolicy.TTL_MS, now))
        assertFalse(EngineVerdictPolicy.isFresh(now + 1, now))
        assertFalse(EngineVerdictPolicy.isFresh(0, now))
    }

    @Test fun pruningKeepsNewest96AndRejectsMalformedDates() {
        val now = 2_000_000_000_000L
        val values = mutableMapOf<String, Any>()
        repeat(110) { values["media_${it}_time"] = now - it.toLong() }
        values["expired_time"] = now - EngineVerdictPolicy.TTL_MS
        values["bad_time"] = "unexpected"
        values["not_a_timestamp_engine"] = "MPV"
        val evicted = EngineVerdictPolicy.evictions(values, now)
        assertEquals(16, evicted.size)
        assertTrue("expired" in evicted)
        assertTrue("bad" in evicted)
        assertTrue("media_109" in evicted)
        assertFalse("media_0" in evicted)
        assertFalse("media_95" in evicted)
        assertTrue("media_96" in evicted)
    }

    @Test fun privatePurgeTargetsOnlyTheChosenAccount() {
        val own = "media_101_-404_88"
        val other = "media_1011_-404_88"
        for (suffix in listOf("_engine", "_texture", "_reason", "_time")) {
            assertTrue(EngineVerdictStore.isAccountPreferenceKey(101, own + suffix))
            assertFalse(EngineVerdictStore.isAccountPreferenceKey(101, other + suffix))
            assertFalse(EngineVerdictStore.isAccountPreferenceKey(101, "media_202_-404_88" + suffix))
        }
        assertFalse(EngineVerdictStore.isAccountPreferenceKey(101, own + "_extra"))
        assertFalse(EngineVerdictStore.isAccountPreferenceKey(101, "not_media_101_-404_88_engine"))
        assertEquals("media_101_", EngineVerdictStore.accountKeyPrefix(101))
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            EngineVerdictStore.accountKeyPrefix(0)
        }
    }

    @Test fun mediaKeysAreAccountScopedAndRejectMissingIdentity() {
        assertEquals("media_101_-404_88", EngineVerdictStore.buildMediaKey(101, -404, 88))
        assertEquals("media_202_-404_88", EngineVerdictStore.buildMediaKey(202, -404, 88))
        assertNull(EngineVerdictStore.buildMediaKey(null, -404, 88))
        assertNull(EngineVerdictStore.buildMediaKey(101, 0, 88))
        assertNull(EngineVerdictStore.buildMediaKey(101, -404, 0))
    }
}
