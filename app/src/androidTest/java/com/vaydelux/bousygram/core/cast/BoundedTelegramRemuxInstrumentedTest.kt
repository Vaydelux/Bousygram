package com.vaydelux.bousygram.core.cast

import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.RandomAccessFile
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BoundedTelegramRemuxInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun fixture(name: String): File =
        File(context.getExternalFilesDir("decoder-fixtures"), name).also {
            assertTrue("Missing decoder fixture", it.isFile)
        }
    private fun scratch(): File = java.nio.file.Files.createTempDirectory(context.cacheDir.toPath(), "remux-test-").toFile()

    @Test fun baselineMp4RetainsEveryVideoSample() = qualify("seek-control.mp4", true)
    @Test fun mkvRetainsEveryVideoSample() = qualify("indexed.mkv", true)

    @Test fun tsExplicitlyUnavailableAfterExtractorSampleLoss() {
        val dir = scratch()
        try {
            assertEquals(BoundedTelegramRemuxer.Result.Rejected(BoundedTelegramRemuxer.Failure.UNSUPPORTED_CONTAINER),
                BoundedTelegramRemuxer.convert(fixture("unindexed.ts"), dir))
            assertTrue(dir.listFiles().orEmpty().isEmpty())
        } finally { dir.deleteRecursively() }
    }

    private fun qualify(name: String, mandatory: Boolean) {
        val dir = scratch()
        try {
            val result = BoundedTelegramRemuxer.convert(fixture(name), dir)
            if (result is BoundedTelegramRemuxer.Result.Rejected) {
                assertFalse("Required $name remux must succeed: ${result.reason}", mandatory)
                assertTrue("Unexpected remux failure for $name: ${result.reason}",
                    result.reason in setOf(BoundedTelegramRemuxer.Failure.UNSUPPORTED_SAMPLE,
                        BoundedTelegramRemuxer.Failure.UNSUPPORTED_CODEC,
                        BoundedTelegramRemuxer.Failure.UNSUPPORTED_TRACKS,
                        BoundedTelegramRemuxer.Failure.UNSUPPORTED_TIMELINE,
                        BoundedTelegramRemuxer.Failure.OUTPUT_VERIFICATION_FAILED))
                assertTrue(dir.listFiles().orEmpty().isEmpty())
                android.util.Log.i("CastRemuxFixtures", "$name unavailable: ${result.reason}")
                return
            }
            val ready = result as BoundedTelegramRemuxer.Result.Ready
            ready.output.use { output ->
                assertTrue(output.file.length() in 1L..BoundedTelegramRemuxer.MAX_OUTPUT_BYTES)
                val source = sampleCounts(fixture(name))
                val converted = sampleCounts(output.file)
                assertEquals("Source track sample counts must be preserved for $name", source, converted)
                assertEquals("Synthetic fixture video frame count for $name", 144, converted["video/avc"])
                val busy = BoundedTelegramRemuxer.convert(fixture(name), dir)
                assertEquals(BoundedTelegramRemuxer.Result.Rejected(BoundedTelegramRemuxer.Failure.BUSY), busy)
                android.util.Log.i("CastRemuxFixtures", "$name remuxed: $converted")
            }
            assertTrue(dir.listFiles().orEmpty().isEmpty())
        } finally { dir.deleteRecursively() }
    }

    private fun sampleCounts(file: File): Map<String, Int> {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val mimes = (0 until extractor.trackCount).map {
                checkNotNull(extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME))
            }
            assertEquals(setOf("video/avc", "audio/mp4a-latm"), mimes.toSet())
            assertEquals(2, mimes.size)
            for (index in mimes.indices) extractor.selectTrack(index)
            val counts = IntArray(mimes.size)
            while (extractor.sampleTrackIndex >= 0) {
                counts[extractor.sampleTrackIndex]++
                if (!extractor.advance()) break
            }
            assertTrue("All tracks must contain samples", counts.all { it > 0 })
            val result = mimes.indices.associate { mimes[it] to counts[it] }
            android.util.Log.i("CastRemuxFixtures", "Sample counts: $result")
            return result
        } finally { extractor.release() }
    }

    @Test fun cancellationAndSizeRejectionLeaveNoScratchOutput() {
        val dir = scratch()
        val input = File.createTempFile("oversized-", ".mkv", context.cacheDir)
        try {
            assertEquals(BoundedTelegramRemuxer.Result.Rejected(BoundedTelegramRemuxer.Failure.CANCELLED),
                BoundedTelegramRemuxer.convert(fixture("indexed.mkv"), dir) { true })
            RandomAccessFile(input, "rw").use { it.setLength(BoundedTelegramRemuxer.MAX_INPUT_BYTES + 1L) }
            assertEquals(BoundedTelegramRemuxer.Result.Rejected(BoundedTelegramRemuxer.Failure.INPUT_TOO_LARGE),
                BoundedTelegramRemuxer.convert(input, dir))
            assertTrue(dir.listFiles().orEmpty().isEmpty())
        } finally { input.delete(); dir.deleteRecursively() }
    }
}
