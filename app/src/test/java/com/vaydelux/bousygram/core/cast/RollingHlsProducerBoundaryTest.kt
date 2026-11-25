package com.vaydelux.bousygram.core.cast

import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.ByteBuffer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RollingHlsProducerBoundaryTest {
    @Test fun sourceClampsToKnownLengthAndDistinguishesPendingFromEof() = runBlocking {
        val requests = mutableListOf<Pair<Long, Int>>()
        val source = RollingHlsSource(300_000, { true }) { offset, limit ->
            requests.add(offset to limit)
            if (offset == 0L) null else ByteArray(limit)
        }
        assertEquals(RollingHlsSource.Read.Pending, source.readAt(0, Int.MAX_VALUE))
        val tail = source.readAt(299_990, 100) as RollingHlsSource.Read.Ready
        assertEquals(10, tail.bytes.size)
        assertEquals(RollingHlsSource.Read.End, source.readAt(300_000, 1))
        assertEquals(listOf(0L to 262_144, 299_990L to 10), requests)
    }

    @Test fun sourceChecksAuthorizationAfterFetchAndRejectsOversize() = runBlocking {
        var allowed = true
        val source = RollingHlsSource(100, { allowed }) { _, _ -> allowed = false; byteArrayOf(1) }
        try { source.readAt(0, 1); fail("revoked bytes returned") } catch (_: IOException) { }
        val oversize = RollingHlsSource(100, { true }) { _, _ -> ByteArray(2) }
        try { oversize.readAt(0, 1); fail("oversized read returned") } catch (_: IOException) { }
        source.close()
        try { source.readAt(0, 1); fail("closed source returned bytes") } catch (_: IOException) { }
    }

    private fun adts() = byteArrayOf(0xff.toByte(), 0xf1.toByte(), 0x4c, 0x80.toByte(),
        0x01, 0x5f, 0xfc.toByte(), 1, 2, 3)

    @Test fun aacLcAdtsNormalizesAndPreservesConfig() {
        val frame = RollingHlsAac.normalize(adts())!!
        assertEquals(RollingHlsAac.Config(3, 2), frame.config)
        assertArrayEquals(byteArrayOf(0x11, 0x90.toByte()), frame.config.audioSpecificConfig())
        assertArrayEquals(byteArrayOf(1, 2, 3), frame.payload)
        assertNull(RollingHlsAac.normalize(adts(), RollingHlsAac.Config(4, 2)))
    }

    @Test fun aacRejectsUnsupportedOrIncompleteFrames() {
        assertNull(RollingHlsAac.normalize(adts().copyOf(9)))
        assertNull(RollingHlsAac.normalize(adts() + byteArrayOf(4)))
        for ((index, value) in listOf(1 to 0xf0, 2 to 0x8c, 6 to 0xfd)) {
            assertNull(RollingHlsAac.normalize(adts().also { it[index] = value.toByte() }))
        }
    }

    private fun box(type: String, data: ByteArray = byteArrayOf()): ByteArray =
        ByteBuffer.allocate(8 + data.size).putInt(8 + data.size).put(type.toByteArray(Charsets.US_ASCII)).put(data).array()
    private fun init() = box("ftyp", ByteArray(8)) + box("moov", box("mvex"))
    private fun frag(seq: Int): ByteArray {
        val mfhd = box("mfhd", ByteBuffer.allocate(8).putInt(0).putInt(seq).array())
        val traf = box("traf", box("tfhd") + box("tfdt") + box("trun"))
        return box("styp") + box("sidx") + box("moof", mfhd + traf) + box("mdat", byteArrayOf(1, 2))
    }

    @Test fun framingReturnsExactInitAndIndependentUnits() {
        val reader = RollingFmp4Framing(ByteArrayInputStream(init() + frag(5) + frag(6))) { true }
        assertArrayEquals(init(), reader.readInit())
        val first = reader.readFragment()!!
        assertEquals(5L, first.sequence)
        assertArrayEquals(frag(5), first.bytes)
        assertArrayEquals(frag(6), reader.readFragment()!!.bytes)
        assertNull(reader.readFragment())
    }

    @Test fun framingRejectsSequenceGapTruncationAndOversizedHeader() {
        for (tail in listOf(frag(1) + frag(3), frag(1) + frag(2).copyOf(frag(2).size - 1),
            frag(1) + ByteBuffer.allocate(8).putInt(Int.MAX_VALUE).put("moof".toByteArray()).array())) {
            val reader = RollingFmp4Framing(ByteArrayInputStream(init() + tail)) { true }
            reader.readInit()
            assertEquals(1L, reader.readFragment()!!.sequence)
            assertThrows(IOException::class.java) { reader.readFragment() }
            assertThrows(IOException::class.java) { reader.readFragment() }
        }
    }

    @Test fun framingRejectsMissingDefaultsAndRevokedInput() {
        val noDefaults = RollingFmp4Framing(ByteArrayInputStream(box("ftyp", ByteArray(8)) + box("moov"))) { true }
        assertThrows(IOException::class.java) { noDefaults.readInit() }
        var allowed = true
        val reader = RollingFmp4Framing(ByteArrayInputStream(init() + frag(1))) { allowed }
        reader.readInit()
        allowed = false
        assertThrows(IOException::class.java) { reader.readFragment() }
    }
}
