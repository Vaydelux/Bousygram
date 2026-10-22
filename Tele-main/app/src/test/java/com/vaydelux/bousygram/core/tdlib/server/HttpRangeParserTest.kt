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
    fun clampsEndButDoesNotClampInvalidStart() {
        val valid = (HttpRangeParser.parse("bytes=90-999", 100) as HttpRangeParser.Result.Valid).range
        assertEquals(99L, valid.endInclusive)
        assertTrue(HttpRangeParser.parse("bytes=100-", 100) is HttpRangeParser.Result.Unsatisfiable)
    }

    @Test
    fun rejectsMalformedMultiAndReversedRanges() {
        listOf("bytes=", "bytes=abc-def", "bytes=10-2", "bytes=1-2,4-5", "bytes=-0", "items=0-2")
            .forEach { header ->
                assertTrue("Expected rejection for $header", HttpRangeParser.parse(header, 100) is HttpRangeParser.Result.Unsatisfiable)
            }
    }

    @Test
    fun rejectsZeroSizedFiles() {
        assertTrue(HttpRangeParser.parse(null, 0) is HttpRangeParser.Result.Unsatisfiable)
    }
}
