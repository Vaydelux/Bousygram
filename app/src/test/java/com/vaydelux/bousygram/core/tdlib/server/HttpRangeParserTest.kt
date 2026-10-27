package com.vaydelux.bousygram.core.tdlib.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpRangeParserTest {
    @Test
    fun absentRangeSelectsWholeFile() {
        val range = (HttpRangeParser.parse(null, 100) as HttpRangeParser.Result.Valid).range
        assertEquals(0L, range.start)
        assertEquals(99L, range.endInclusive)
        assertFalse(range.isPartial)
    }

    @Test
    fun supportsClosedOpenEndedAndSuffixRanges() {
        val closed = (HttpRangeParser.parse("bytes=10-19", 100) as HttpRangeParser.Result.Valid).range
        assertEquals(10L, closed.start)
        assertEquals(19L, closed.endInclusive)

        val open = (HttpRangeParser.parse("bytes=90-", 100) as HttpRangeParser.Result.Valid).range
        assertEquals(90L, open.start)
        assertEquals(99L, open.endInclusive)

        val suffix = (HttpRangeParser.parse("bytes=-10", 100) as HttpRangeParser.Result.Valid).range
        assertEquals(90L, suffix.start)
        assertEquals(99L, suffix.endInclusive)
        assertTrue(suffix.isPartial)
    }

    @Test
    fun supportsWhitespaceAndFirstOfMultiRange() {
        val spaced = (HttpRangeParser.parse("bytes= 10 - 25 ", 100) as HttpRangeParser.Result.Valid).range
        assertEquals(10L, spaced.start)
        assertEquals(25L, spaced.endInclusive)

        val multi = (HttpRangeParser.parse("bytes=1-2, 4-5", 100) as HttpRangeParser.Result.Valid).range
        assertEquals(1L, multi.start)
        assertEquals(2L, multi.endInclusive)
    }

    @Test
    fun clampsEndButDoesNotClampInvalidStart() {
        val valid = (HttpRangeParser.parse("bytes=90-999", 100) as HttpRangeParser.Result.Valid).range
        assertEquals(99L, valid.endInclusive)
        assertTrue(HttpRangeParser.parse("bytes=100-", 100) is HttpRangeParser.Result.Unsatisfiable)
    }

    @Test
    fun rejectsMalformedAndReversedRanges() {
        listOf("bytes=", "bytes=abc-def", "bytes=10-2", "bytes=-0", "items=0-2")
            .forEach { header ->
                assertTrue("Expected rejection for $header", HttpRangeParser.parse(header, 100) is HttpRangeParser.Result.Unsatisfiable)
            }
    }

    @Test
    fun rejectsZeroSizedFiles() {
        assertTrue(HttpRangeParser.parse(null, 0) is HttpRangeParser.Result.Unsatisfiable)
    }

    @Test
    fun streamsLargeFileCompletelyByDefaultAndSupportsOptionalClamping() {
        val largeFileSize = 500L * 1024L * 1024L // 500 MiB
        val absentLarge = (HttpRangeParser.parse(null, largeFileSize) as HttpRangeParser.Result.Valid).range
        assertEquals(0L, absentLarge.start)
        assertEquals(largeFileSize - 1L, absentLarge.endInclusive)
        assertFalse(absentLarge.isPartial)

        val openLarge = (HttpRangeParser.parse("bytes=0-", largeFileSize) as HttpRangeParser.Result.Valid).range
        assertEquals(0L, openLarge.start)
        assertEquals(largeFileSize - 1L, openLarge.endInclusive)
        assertTrue(openLarge.isPartial)

        val midLarge = (HttpRangeParser.parse("bytes=10485760-115343360", largeFileSize, clampOversizedRange = true) as HttpRangeParser.Result.Valid).range
        assertEquals(10485760L, midLarge.start)
        assertEquals(10485760L + HttpRangeParser.MAX_STREAM_RANGE_BYTES - 1L, midLarge.endInclusive)
        assertTrue(midLarge.isPartial)
    }
}
