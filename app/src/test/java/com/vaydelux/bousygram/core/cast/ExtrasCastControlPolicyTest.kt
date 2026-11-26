package com.vaydelux.bousygram.core.cast

import org.junit.Assert.*
import org.junit.Test

class ExtrasCastControlPolicyTest {
    @Test fun seekBoundsAndOverflow() {
        assertEquals(0L, ExtrasCastControlPolicy.seekTarget(2_000L, 12_000L, -10_000L))
        assertEquals(12_000L, ExtrasCastControlPolicy.seekTarget(10_000L, 12_000L, 10_000L))
        assertEquals(Long.MAX_VALUE, ExtrasCastControlPolicy.seekTarget(Long.MAX_VALUE - 1L, Long.MAX_VALUE, Long.MAX_VALUE))
        assertEquals(0L, ExtrasCastControlPolicy.seekTarget(Long.MIN_VALUE, 12_000L, Long.MIN_VALUE))
        assertNull(ExtrasCastControlPolicy.seekTarget(0L, 0L, 100L))
    }

    @Test fun subtitleOffPreservesAudioAndVideoSelections() {
        val active = longArrayOf(2L, 3L, 10L, 10L)
        val text = setOf(10L, 11L)
        assertArrayEquals(longArrayOf(2L, 3L), ExtrasCastControlPolicy.activeTracks(active, text, null))
        assertArrayEquals(longArrayOf(2L, 3L, 11L), ExtrasCastControlPolicy.activeTracks(active, text, 11L))
        assertNull(ExtrasCastControlPolicy.activeTracks(active, text, 2L))
    }
}
