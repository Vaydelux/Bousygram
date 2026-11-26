package com.vaydelux.bousygram.core.cast

import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class RollingLanClipPolicyTest {
    @Test fun syntheticStillRequiresTwelveFragments() {
        val policy = RollingLanClipPolicy(true)
        repeat(12) { i ->
            assertFalse(policy.complete())
            assertTrue(policy.accept(i + 1L, i * 6_000_000L, 6_000_000))
        }
        assertTrue(policy.complete())
        assertFalse(policy.accept(13, 72_000_000, 6_000_000))
    }

    @Test fun localClipRejectsEmptyNoncontiguousAndOversizedUnits() {
        val policy = RollingLanClipPolicy(false)
        assertFalse(policy.complete())
        assertFalse(policy.accept(2, 0, 6_000_000))
        assertFalse(policy.accept(1, 1, 6_000_000))
        assertFalse(policy.accept(1, 0, 7_000_000))
        assertTrue(policy.accept(1, 0, 4_000_000))
        assertTrue(policy.complete())
        assertFalse(policy.accept(2, 3_000_000, 4_000_000))
        assertTrue(policy.accept(2, 4_000_000, 4_000_000))
    }

    @Test fun boundedInputRejectsOverflowAndRevocation() {
        val size = RollingLanClipPolicy.MAX_INPUT_BYTES.toInt()
        val input = object : java.io.InputStream() {
            private var remaining = size + 1
            override fun read(): Int = if (remaining-- > 0) 0 else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (remaining <= 0) return -1
                val count = minOf(remaining, len)
                b.fill(0, off, off + count)
                remaining -= count
                return count
            }
        }
        RollingLanClipPolicy.bounded(input) { true }.use {
            val buffer = ByteArray(8192)
            repeat(size / buffer.size) { _ -> assertEquals(buffer.size, it.read(buffer)) }
            assertThrows(IOException::class.java) { it.read() }
        }
        var allowed = true
        RollingLanClipPolicy.bounded(ByteArrayInputStream(byteArrayOf(1, 2))) { allowed }.use {
            assertEquals(1, it.read())
            allowed = false
            assertThrows(IOException::class.java) { it.read() }
        }
    }
}
