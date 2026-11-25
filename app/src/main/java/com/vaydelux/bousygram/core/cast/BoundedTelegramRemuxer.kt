package com.vaydelux.bousygram.core.cast

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.SystemClock
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.Semaphore

/**
 * Qualification-only, finite whole-file MP4 remux. No decoder/encoder, no
 * network, no TDLib download, no player integration. Never call on the UI thread.
 * The caller must supply a completed, authorized local file and a private
 * scratch directory, and close Ready.output when done.
 */
internal object BoundedTelegramRemuxer {
    const val MAX_INPUT_BYTES = 16L * 1024 * 1024
    const val MAX_OUTPUT_BYTES = 20L * 1024 * 1024
    const val MAX_SAMPLE_BYTES = 2 * 1024 * 1024
    const val MAX_DURATION_US = 60_000_000L
    private const val MAX_SAMPLES = 20_000
    private const val MIN_FREE_BYTES = 500L * 1024 * 1024
    private const val DEADLINE_MS = 20_000L
    private val permit = Semaphore(1)

    enum class Failure {
        BUSY, PLATFORM_UNAVAILABLE, CANCELLED, INPUT_UNAVAILABLE, INPUT_TOO_LARGE, STORAGE_PRESSURE,
        UNSUPPORTED_TRACKS, UNSUPPORTED_CODEC, UNSUPPORTED_SAMPLE,
        TIME_LIMIT, DURATION_LIMIT, OUTPUT_LIMIT, CONVERSION_FAILED, CLEANUP_FAILED
    }

    sealed interface Result {
        data class Rejected(val reason: Failure) : Result
        class Ready(val output: Output) : Result
    }

    /** Holds the single scratch allocation until explicitly closed. */
    class Output internal constructor(val file: File) : AutoCloseable {
        private var closed = false
        @Synchronized override fun close() {
            if (closed) return
            if (file.exists() && !file.delete()) {
                // Do not admit more output when the previous artifact cannot be reclaimed.
                throw IllegalStateException("Cast scratch cleanup failed")
            }
            closed = true
            permit.release()
        }
    }

    fun convert(input: File, scratch: File, cancelled: () -> Boolean = { false }): Result {
        if (Build.VERSION.SDK_INT < 28) return Result.Rejected(Failure.PLATFORM_UNAVAILABLE)
        if (!permit.tryAcquire()) return Result.Rejected(Failure.BUSY)
        var target: File? = null
        var published = false
        var outcome: Result = Result.Rejected(Failure.CONVERSION_FAILED)
        try {
            outcome = runConversion(input, scratch, cancelled) { target = it }
            published = outcome is Result.Ready
        } catch (_: Exception) {
            outcome = Result.Rejected(Failure.CONVERSION_FAILED)
        } finally {
            if (!published) {
                val cleaned = target?.let { !it.exists() || it.delete() } ?: true
                if (cleaned) permit.release()
                else outcome = Result.Rejected(Failure.CLEANUP_FAILED)
            }
        }
        return outcome
    }

    @androidx.annotation.RequiresApi(28)
    private fun runConversion(input: File, scratch: File, cancelled: () -> Boolean,
                              allocated: (File) -> Unit): Result {
        fun reject(reason: Failure) = Result.Rejected(reason)
        if (cancelled()) return reject(Failure.CANCELLED)
        val inputSize = input.length()
        if (!input.isFile || inputSize <= 0) return reject(Failure.INPUT_UNAVAILABLE)
        if (inputSize > MAX_INPUT_BYTES) return reject(Failure.INPUT_TOO_LARGE)
        if (!scratch.isDirectory && !scratch.mkdirs()) return reject(Failure.STORAGE_PRESSURE)
        // Use a dedicated scratch directory. Do not silently delete another owner's file.
        if (scratch.listFiles()?.isNotEmpty() != false) return reject(Failure.STORAGE_PRESSURE)
        if (scratch.usableSpace < MIN_FREE_BYTES + MAX_OUTPUT_BYTES) return reject(Failure.STORAGE_PRESSURE)
        val deadline = SystemClock.elapsedRealtime() + DEADLINE_MS
        fun check(): Failure? = when {
            cancelled() -> Failure.CANCELLED
            SystemClock.elapsedRealtime() >= deadline -> Failure.TIME_LIMIT
            else -> null
        }
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            // File descriptor avoids path-based native lookup and remote URLs.
            input.inputStream().use { extractor.setDataSource(it.fd) }
            check()?.let { return reject(it) }
            if (extractor.trackCount !in 1..2) return reject(Failure.UNSUPPORTED_TRACKS)
            val formats = (0 until extractor.trackCount).map(extractor::getTrackFormat)
            val video = formats.indices.filter { formats[it].getString(MediaFormat.KEY_MIME) == "video/avc" }
            val audio = formats.indices.filter { formats[it].getString(MediaFormat.KEY_MIME) == "audio/mp4a-latm" }
            // Do not silently drop alternate audio/subtitle tracks.
            if (video.size != 1 || audio.size > 1 || video.size + audio.size != formats.size) {
                return reject(Failure.UNSUPPORTED_CODEC)
            }
            for (format in formats) {
                if (format.containsKey(MediaFormat.KEY_DURATION) &&
                    format.getLong(MediaFormat.KEY_DURATION) > MAX_DURATION_US) return reject(Failure.DURATION_LIMIT)
            }
            val file = File.createTempFile("cast-remux-", ".mp4", scratch)
            allocated(file)
            val writer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = writer
            val tracks = formats.map(writer::addTrack)
            for (index in formats.indices) extractor.selectTrack(index)
            writer.start()
            val buffer = ByteBuffer.allocateDirect(MAX_SAMPLE_BYTES)
            val info = MediaCodec.BufferInfo()
            val counts = IntArray(formats.size)
            val lastPts = LongArray(formats.size) { -1L }
            var firstPts = -1L
            var payloadBytes = 0L
            var sampleCount = 0
            while (true) {
                check()?.let { return reject(it) }
                val index = extractor.sampleTrackIndex
                if (index < 0) break
                if (index !in tracks.indices || ++sampleCount > MAX_SAMPLES) return reject(Failure.OUTPUT_LIMIT)
                val sampleSize = extractor.sampleSize
                if (sampleSize !in 1L..MAX_SAMPLE_BYTES.toLong()) return reject(Failure.UNSUPPORTED_SAMPLE)
                val flags = extractor.sampleFlags
                if (flags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED != 0 ||
                    flags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0) return reject(Failure.UNSUPPORTED_SAMPLE)
                val pts = extractor.sampleTime
                if (pts < 0L) return reject(Failure.UNSUPPORTED_SAMPLE)
                if (firstPts < 0L) firstPts = pts
                val normalized = pts - firstPts
                if (normalized < 0L || normalized < lastPts[index]) return reject(Failure.UNSUPPORTED_SAMPLE)
                if (normalized > MAX_DURATION_US) return reject(Failure.DURATION_LIMIT)
                // A complete first video sample must be independently decodable.
                if (index == video.single() && counts[index] == 0 &&
                    flags and MediaExtractor.SAMPLE_FLAG_SYNC == 0) return reject(Failure.UNSUPPORTED_SAMPLE)
                if (payloadBytes + sampleSize > MAX_INPUT_BYTES || file.length() > MAX_OUTPUT_BYTES) {
                    return reject(Failure.OUTPUT_LIMIT)
                }
                buffer.clear()
                val read = extractor.readSampleData(buffer, 0)
                if (read.toLong() != sampleSize) return reject(Failure.UNSUPPORTED_SAMPLE)
                buffer.position(0)
                buffer.limit(read)
                val outputFlags = if (flags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                    MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                info.set(0, read, normalized, outputFlags)
                writer.writeSampleData(tracks[index], buffer, info)
                counts[index]++
                lastPts[index] = normalized
                payloadBytes += read
                if (!extractor.advance()) break
            }
            check()?.let { return reject(it) }
            if (counts.any { it == 0 }) return reject(Failure.UNSUPPORTED_SAMPLE)
            writer.stop()
            writer.release()
            muxer = null
            if (file.length() !in 1L..MAX_OUTPUT_BYTES) return reject(Failure.OUTPUT_LIMIT)
            if (input.length() != inputSize) return reject(Failure.INPUT_UNAVAILABLE)
            check()?.let { return reject(it) }
            return Result.Ready(Output(file))
        } finally {
            runCatching { muxer?.release() }
            extractor.release()
        }
    }
}
