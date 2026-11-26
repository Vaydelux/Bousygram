package com.vaydelux.bousygram.core.cast

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import org.junit.Assert.*
import org.junit.Test

class RollingHlsNativeCallbacksTest {
    private fun source(size: Long = 4, allowed: () -> Boolean = { true }) =
        RollingHlsSource(size, allowed) { offset, count -> ByteArray(count) { (offset + it).toByte() } }

    @Test fun exactInputAndStagedOutputAreBoundedAndOneShot() {
        val sink = ByteArrayOutputStream()
        val reservations = mutableListOf<Long>()
        val cb = RollingHlsNativeCallbacks(source(), sink, { true }, { reservations.add(it); true })
        val outcome = cb.execute(1) { kind, size, io ->
            assertEquals(1, kind)
            assertEquals(4L, size)
            val read = ByteArray(4)
            assertEquals(2, io.read(0, read, 2))
            assertEquals(2, io.read(2, read, 2))
            assertArrayEquals(byteArrayOf(2, 3, 0, 0), read)
            assertTrue(io.write(byteArrayOf(5, 6), 2))
            0
        }
        assertTrue(outcome.transferComplete)
        assertEquals(4L, outcome.inputBytes)
        assertEquals(2L, outcome.outputBytes)
        assertArrayEquals(byteArrayOf(5, 6), sink.toByteArray())
        assertEquals(listOf(2L, 0L, 0L), reservations)
        assertFalse(cb.active())
        assertThrows(IllegalStateException::class.java) { cb.execute(1) { _, _, _ -> 0 } }
    }

    @Test fun missingBytesAreNotEofOrSuccess() {
        val cb = RollingHlsNativeCallbacks(RollingHlsSource(4, { true }) { _, _ -> null },
            ByteArrayOutputStream(), { true }, { true })
        val result = cb.execute(1) { _, _, io ->
            assertEquals(-1, io.read(0, ByteArray(4), 4))
            assertFalse(io.active())
            assertFalse(io.write(byteArrayOf(1), 1))
            0
        }
        assertFalse(result.transferComplete)
        assertEquals(RollingHlsNativeCallbacks.Failure.PENDING_INPUT, result.failure)
    }

    @Test fun invalidOffsetAndOversizeReadFailClosed() {
        for ((offset, count) in listOf(1L to 1, 0L to 5, 0L to 32769)) {
            val cb = RollingHlsNativeCallbacks(source(), ByteArrayOutputStream(), { true }, { true })
            val result = cb.execute(2) { _, _, io ->
                assertEquals(-1, io.read(offset, ByteArray(count), count))
                2
            }
            assertEquals(RollingHlsNativeCallbacks.Failure.INVALID_READ, result.failure)
        }
    }

    @Test fun revocationDuringFetchCannotReturnSourceBytes() {
        var allowed = true
        val input = RollingHlsSource(4, { allowed }) { _, _ ->
            allowed = false
            byteArrayOf(7)
        }
        val cb = RollingHlsNativeCallbacks(input, ByteArrayOutputStream(), { allowed }, { true })
        val target = ByteArray(1)
        val result = cb.execute(1) { _, _, io ->
            assertEquals(-1, io.read(0, target, 1))
            assertFalse(io.write(byteArrayOf(1), 1))
            2
        }
        assertFalse(result.transferComplete)
        assertArrayEquals(byteArrayOf(0), target)
        assertEquals(0L, result.outputBytes)
    }

    @Test fun stagedWriteIsRejectedWhenBudgetChanges() {
        val sink = ByteArrayOutputStream()
        val cb = RollingHlsNativeCallbacks(source(), sink, { true }, { it > 0 })
        val result = cb.execute(1) { _, _, io ->
            assertFalse(io.write(byteArrayOf(1), 1))
            assertFalse(io.write(byteArrayOf(2), 1))
            2
        }
        assertEquals(RollingHlsNativeCallbacks.Failure.STORAGE_PRESSURE, result.failure)
        assertEquals(1L, result.outputBytes)
        assertArrayEquals(byteArrayOf(1), sink.toByteArray()) // caller must discard
    }

    @Test fun partialNativeSuccessAndSinkFailureCannotQualify() {
        val incomplete = RollingHlsNativeCallbacks(source(), ByteArrayOutputStream(), { true }, { true })
        assertFalse(incomplete.execute(1) { _, _, io -> io.write(byteArrayOf(1), 1); 0 }.transferComplete)
        val bad = object : OutputStream() {
            override fun write(b: Int) { throw IOException("synthetic failure") }
        }
        val cb = RollingHlsNativeCallbacks(source(), bad, { true }, { true })
        val result = cb.execute(2) { _, _, io -> assertFalse(io.write(byteArrayOf(1), 1)); 2 }
        assertEquals(RollingHlsNativeCallbacks.Failure.IO, result.failure)
    }
}
