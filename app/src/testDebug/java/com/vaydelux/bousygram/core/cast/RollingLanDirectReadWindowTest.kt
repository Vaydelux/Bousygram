package com.vaydelux.bousygram.core.cast

import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class RollingLanDirectReadWindowTest {
    @Test fun tinyReadsAndSeeksShareOneAlignedPage() {
        val page = RollingLanDirectMediaPolicy.MAX_READ_BYTES
        val size = page.toLong() * 2 + 19
        val window = RollingLanDirectReadWindow(size)
        val offsets = mutableListOf<Long>()
        val out = ByteArray(32)
        val fetch: (Long, ByteArray, Int) -> Unit = { offset, bytes, n ->
            offsets.add(offset)
            repeat(n) { bytes[it] = ((offset + it) % 251).toByte() }
        }
        repeat(1000) { i ->
            assertEquals(1, window.read(i.toLong(), out, 2, 1, { true }, fetch))
            assertEquals((i % 251).toByte(), out[2])
        }
        assertEquals(listOf(0L), offsets)
        assertEquals(2, window.read(page.toLong() - 2, out, 0, 16, { true }, fetch))
        assertEquals(16, window.read(page.toLong(), out, 0, 16, { true }, fetch))
        assertEquals(19, window.read(size - 19, out, 0, 32, { true }, fetch))
        assertEquals(listOf(0L, page.toLong(), page.toLong() * 2), offsets)
        assertEquals(1, window.read(0L, out, 0, 1, { true }, fetch))
        assertEquals(4, offsets.size)
    }
    @Test fun failedOrRevokedPagesNeverBecomeReadable() {
        val window = RollingLanDirectReadWindow(100L)
        val out = ByteArray(8)
        assertThrows(IOException::class.java) {
            window.read(0L, out, 0, 8, { true }) { _, _, _ -> throw IOException("interrupted") }
        }
        var fetched = false
        assertEquals(8, window.read(0L, out, 0, 8, { true }) { _, bytes, n ->
            fetched = true
            bytes.fill(3, 0, n)
        })
        assertTrue(fetched)
        assertArrayEquals(ByteArray(8) { 3 }, out)
        assertThrows(IOException::class.java) {
            window.read(0L, out, 0, 8, { false }) { _, _, _ -> fail("revoked fetch") }
        }
    }
}
