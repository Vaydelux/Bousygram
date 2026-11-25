package com.vaydelux.bousygram.core.cast

import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RollingHlsSegmentStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun segment(seq: Long, size: Long = 4L) =
        RollingHlsSegmentStore.Segment(seq, seq * 5_000_000L, 5_000_000L, size)
    private fun bytes(size: Int = 4) = ByteArrayInputStream(ByteArray(size) { it.toByte() })

    @Test fun fullWindowBackpressuresUntilConsumerRetires() {
        val dir = temporary.newFolder()
        RollingHlsSegmentStore(dir, { true }, { true }, capacityBytes = 8, capacitySegments = 2).use { s ->
            assertEquals(RollingHlsSegmentStore.PublishResult.STORED, s.publish(segment(0), bytes()))
            assertEquals(RollingHlsSegmentStore.PublishResult.STORED, s.publish(segment(1), bytes()))
            assertEquals(RollingHlsSegmentStore.PublishResult.BACKPRESSURE, s.publish(segment(2), bytes()))
            assertEquals(8L, s.retainedBytes())
            s.retireBefore(1)
            assertNull(s.open(0))
            assertEquals(RollingHlsSegmentStore.PublishResult.STORED, s.publish(segment(2), bytes()))
            assertEquals(listOf(1L, 2L), s.snapshot().map { it.sequence })
        }
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test fun leasedRetiredBytesRemainChargedUntilClose() {
        val dir = temporary.newFolder()
        RollingHlsSegmentStore(dir, { true }, { true }, capacityBytes = 4).use { s ->
            s.publish(segment(0), bytes())
            val lease = s.open(0)!!
            assertNull(s.open(0))
            s.retireBefore(1)
            assertNull(s.open(0))
            assertEquals(4L, s.retainedBytes())
            assertEquals(RollingHlsSegmentStore.PublishResult.BACKPRESSURE, s.publish(segment(1), bytes()))
            val output = ByteArray(4)
            assertEquals(4, lease.read(output, 0, 4))
            assertArrayEquals(byteArrayOf(0, 1, 2, 3), output)
            assertEquals(-1, lease.read(output, 0, 4))
            lease.close()
            assertEquals(0L, s.retainedBytes())
            assertEquals(RollingHlsSegmentStore.PublishResult.STORED, s.publish(segment(1), bytes()))
        }
    }

    @Test fun rejectsWrongTimelineShortLongAndOversizeWithoutAdvancing() {
        val dir = temporary.newFolder()
        RollingHlsSegmentStore(dir, { true }, { true }).use { s ->
            val invalid = RollingHlsSegmentStore.PublishResult.INVALID
            assertEquals(invalid, s.publish(segment(1), bytes()))
            assertEquals(invalid, s.publish(segment(0).copy(startUs = 1), bytes()))
            assertEquals(invalid, s.publish(segment(0).copy(durationUs = 0), bytes()))
            assertEquals(invalid, s.publish(segment(0, RollingHlsBudget.MAX_SEGMENT_BYTES + 1), bytes()))
            assertEquals(invalid, s.publish(segment(0), bytes(3)))
            assertEquals(invalid, s.publish(segment(0), bytes(5)))
            assertTrue(s.snapshot().isEmpty())
            assertEquals(0, dir.listFiles()!!.size)
            assertEquals(RollingHlsSegmentStore.PublishResult.STORED, s.publish(segment(0), bytes()))
        }
    }

    @Test fun rechecksSharedBudgetAfterStagingAndDeletesDeniedCopy() {
        val dir = temporary.newFolder()
        val checks = mutableListOf<Long>()
        RollingHlsSegmentStore(dir, { true }, { checks += it; it > 0 }).use { s ->
            assertEquals(RollingHlsSegmentStore.PublishResult.BACKPRESSURE, s.publish(segment(0), bytes()))
            assertEquals(listOf(4L, 0L), checks)
            assertEquals(0L, s.retainedBytes())
            assertEquals(0, dir.listFiles()!!.size)
        }
    }

    @Test fun closeRevokesLeaseAndDefersReclamationUntilReaderCloses() {
        val dir = temporary.newFolder()
        val s = RollingHlsSegmentStore(dir, { true }, { true })
        s.publish(segment(0), bytes())
        val lease = s.open(0)!!
        s.close()
        assertNull(s.open(0))
        assertTrue(s.snapshot().isEmpty())
        assertEquals(4L, s.retainedBytes())
        assertThrows(IOException::class.java) { lease.read(ByteArray(4), 0, 4) }
        lease.close()
        assertEquals(0L, s.retainedBytes())
        assertEquals(0, dir.listFiles()!!.size)
        assertEquals(RollingHlsSegmentStore.PublishResult.DENIED, s.publish(segment(1), bytes()))
        s.close()
    }

    @Test fun accountRevocationRejectsNewReadsAndWrites() {
        var current = true
        val dir = temporary.newFolder()
        RollingHlsSegmentStore(dir, { current }, { true }).use { s ->
            s.publish(segment(0), bytes())
            s.open(0)!!.use { lease ->
                current = false
                assertNull(s.open(0))
                assertTrue(s.snapshot().isEmpty())
                assertThrows(IOException::class.java) { lease.read(ByteArray(4), 0, 4) }
                assertEquals(RollingHlsSegmentStore.PublishResult.DENIED, s.publish(segment(1), bytes()))
            }
        }
    }

    @Test fun refusesNonemptyScratchDirectory() {
        val dir = temporary.newFolder()
        java.io.File(dir, "orphan").writeText("old")
        assertThrows(IllegalArgumentException::class.java) { RollingHlsSegmentStore(dir, { true }, { true }) }
    }

    @Test fun sharedBudgetUsesOverflowSafeAdmissionAndFreeSpaceFloor() {
        val mib = 1024L * 1024L
        val free = 600 * mib
        assertTrue(RollingHlsBudget.admits(24 * mib, 168 * mib, free, 8 * mib))
        assertFalse(RollingHlsBudget.admits(24 * mib, 169 * mib, free, 8 * mib))
        assertFalse(RollingHlsBudget.admits(0, 0, 501 * mib, 2 * mib))
        assertFalse(RollingHlsBudget.admits(Long.MAX_VALUE, Long.MAX_VALUE, free, 0))
        assertFalse(RollingHlsBudget.admits(-1, 0, free, 1))
        assertTrue(RollingHlsBudget.admits(200 * mib, 0, 500 * mib, 0))
    }
}
