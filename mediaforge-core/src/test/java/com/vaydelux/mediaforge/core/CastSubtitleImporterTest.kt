package com.vaydelux.mediaforge.core

import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test

class CastSubtitleImporterTest {
    @Test fun importsBoundedSrtWithoutWritingAFile() {
        val text = "1\n00:00:01,000 --> 00:00:02,000\nHello\n"
        val result = CastSubtitleImporter.fromSrtStream(ByteArrayInputStream(text.toByteArray()))
        assertEquals("WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nHello\n\n", result!!.toString(Charsets.UTF_8))
        assertNull(CastSubtitleImporter.fromSrtStream(ByteArrayInputStream(ByteArray(SrtWebVtt.MAX_INPUT_BYTES + 1))))
        assertNull(CastSubtitleImporter.fromSrtStream(ByteArrayInputStream("<html>error</html>".toByteArray())))
    }

    @Test fun nonProgressingInputDoesNotLoopForever() {
        val stalled = object : InputStream() {
            override fun read(): Int = 0
            override fun read(b: ByteArray, off: Int, len: Int): Int = 0
        }
        assertNull(CastSubtitleImporter.fromSrtStream(stalled))
    }
}
