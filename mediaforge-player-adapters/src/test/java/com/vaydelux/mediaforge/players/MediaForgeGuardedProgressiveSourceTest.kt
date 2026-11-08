package com.vaydelux.mediaforge.players

import com.vaydelux.mediaforge.core.StreamCandidate
import com.vaydelux.mediaforge.core.StreamFormat
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import okio.Timeout
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM contract tests: no remote provider or FUSE device is accessed.
 * Android filesystem/range playback needs emulator and physical qualification.
 */
class MediaForgeGuardedProgressiveSourceTest {
    @Test fun parsesOnlyExplicitStableByteRanges() {
        assertEquals(Triple(0L, 0L, 12345L), parseContentRange("bytes 0-0/12345"))
        assertEquals(Triple(200L, 399L, 500L), parseContentRange("bytes 200-399/500"))
        assertNull(parseContentRange("bytes */500"))
        assertNull(parseContentRange("bytes 0-0/*"))
        assertNull(parseContentRange("unexpected"))
        assertNull(parseContentRange(null))
        assertNull(parseContentRange("bytes 1-1/99999999999999999999999"))
    }

    @Test fun cancellationDuringOpeningStopsPendingNetworkProbe() = runBlocking {
        val call = PendingStartupCall()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            readCancellableMediaResponse(call) { it.code }
        }
        assertTrue(call.wasEnqueued)
        assertFalse(call.isCanceled())
        job.cancelAndJoin()
        assertTrue(call.isCanceled())
    }

    private class PendingStartupCall : Call {
        private val req = Request.Builder().url("https://example.org/probe").build()
        private val cancelled = AtomicBoolean(false)
        var wasEnqueued = false
            private set

        override fun request(): Request = req
        override fun execute(): Response = throw AssertionError("Must not execute synchronously")
        override fun enqueue(responseCallback: Callback) { wasEnqueued = true }
        override fun cancel() { cancelled.set(true) }
        override fun isExecuted(): Boolean = wasEnqueued
        override fun isCanceled(): Boolean = cancelled.get()
        override fun timeout(): Timeout = Timeout()
        public override fun clone(): Call = PendingStartupCall()
    }

    @Test fun stopCancelsRegisteredReadExactlyOnce() {
        val gate = MediaForgeReadCancellationGate()
        var cancels = 0
        assertTrue(gate.register { cancels++ })
        assertFalse(gate.isStopped)
        gate.stop()
        gate.stop()
        assertEquals(1, cancels)
        assertTrue(gate.isStopped)
    }

    @Test fun closeBeforeRegistrationPreventsNewNetworkRequest() {
        val gate = MediaForgeReadCancellationGate()
        gate.stop() // close races with a scheduled request
        var cancels = 0
        assertFalse(gate.register { cancels++ })
        assertEquals(0, cancels)
        assertTrue(gate.isStopped)
    }

    @Test fun finishedReadDoesNotCancelAndCannotRearmAfterClose() {
        val gate = MediaForgeReadCancellationGate()
        var cancels = 0
        assertTrue(gate.register { cancels++ })
        gate.finish()
        gate.stop()
        assertEquals(0, cancels)
        assertFalse(gate.register { cancels++ })
    }

    @Test fun cancellationGateAcceptsSequentialRequestsBeforeStopOnly() {
        val gate = MediaForgeReadCancellationGate()
        var cancels = 0
        assertTrue(gate.register { cancels++ })
        gate.finish()
        assertTrue(gate.register { cancels += 10 })
        gate.stop()
        assertEquals(10, cancels) // only the latest pending read
    }

    @Test fun headerAndTailWindowsAreReusedWithoutAnotherDownload() {
        val cache = MediaForgeRangeWindowCache()
        val head = ByteArray(MEDIAFORGE_RANGE_WINDOW_BYTES.toInt()) { 1 }
        val tail = ByteArray(MEDIAFORGE_RANGE_WINDOW_BYTES.toInt()) { 2 }
        val tailStart = MEDIAFORGE_RANGE_WINDOW_BYTES * 50
        cache.put(0L, head)
        cache.put(tailStart, tail)
        assertTrue(cache.get(0L) === head)
        assertTrue(cache.get(tailStart) === tail)
        assertEquals(2, cache.retainedWindows)
        assertEquals(512 * 1024, cache.retainedBytes)
    }

    @Test fun thirdWindowEvictsLeastRecentlyUsedRatherThanRecentHead() {
        val cache = MediaForgeRangeWindowCache()
        val size = MEDIAFORGE_RANGE_WINDOW_BYTES
        cache.put(0L, byteArrayOf(1))
        cache.put(size, byteArrayOf(2))
        assertEquals(1.toByte(), cache.get(0L)!![0]) // Head is most recent.
        cache.put(size * 2, byteArrayOf(3))
        assertNull(cache.get(size))
        assertEquals(1.toByte(), cache.get(0L)!![0])
        assertEquals(3.toByte(), cache.get(size * 2)!![0])
        assertEquals(2, cache.retainedWindows)
        assertEquals(2, cache.retainedBytes)
    }

    @Test fun manySeekWindowsRemainBoundedTo512Kib() {
        val cache = MediaForgeRangeWindowCache()
        val windowBytes = MEDIAFORGE_RANGE_WINDOW_BYTES
        repeat(100) { i ->
            cache.put(i * windowBytes, ByteArray(windowBytes.toInt()))
            assertTrue(cache.retainedWindows <= MEDIAFORGE_MAX_CACHED_WINDOWS)
            assertTrue(cache.retainedBytes <= 512 * 1024)
        }
        assertNull(cache.get(0L))
        assertTrue(cache.get(99 * windowBytes) != null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun cacheRejectsOversizedUnboundedResponse() {
        MediaForgeRangeWindowCache().put(
            0L, ByteArray(MEDIAFORGE_RANGE_WINDOW_BYTES.toInt() + 1)
        )
    }

    @Test fun alignedWindowsAvoidOverlappingRequestsAcrossSmallSeeks() {
        val size = MEDIAFORGE_RANGE_WINDOW_BYTES
        assertEquals(0L, alignedRangeWindowStart(0L))
        assertEquals(0L, alignedRangeWindowStart(4096L))
        assertEquals(0L, alignedRangeWindowStart(size - 1L))
        assertEquals(size, alignedRangeWindowStart(size))
        assertEquals(size, alignedRangeWindowStart(size + 128L))
        assertEquals(size * 2L, alignedRangeWindowStart(size * 2L + 10L))
        // Very large offsets must not overflow when computing an aligned start.
        assertTrue(alignedRangeWindowStart(Long.MAX_VALUE) <= Long.MAX_VALUE)
    }

    @Test fun rangeFileDescriptorRejectsEncodingAndIncorrectDeclaredLengths() {
        assertTrue(isIdentityByteRangeResponse(null, null, 262_144))
        assertTrue(isIdentityByteRangeResponse("identity", "262144", 262_144))
        assertTrue(isIdentityByteRangeResponse("IDENTITY", " 1 ", 1))
        assertFalse(isIdentityByteRangeResponse("gzip", "262144", 262_144))
        assertFalse(isIdentityByteRangeResponse("br", null, 262_144))
        assertFalse(isIdentityByteRangeResponse("zstd", null, 262_144))
        assertFalse(isIdentityByteRangeResponse("identity, gzip", null, 262_144))
        assertFalse(isIdentityByteRangeResponse("", null, 262_144))
        assertFalse(isIdentityByteRangeResponse("identity", "262143", 262_144))
        assertFalse(isIdentityByteRangeResponse(null, "262145", 262_144))
        assertFalse(isIdentityByteRangeResponse(null, "invalid", 1))
        assertFalse(isIdentityByteRangeResponse(null, "-1", 1))
        assertFalse(isIdentityByteRangeResponse(null, null, 0))
    }

    @Test fun strongEtagsOnlyAndRangeIdentity() {
        assertEquals("\"media-v1\"", parseStrongEtag("\"media-v1\""))
        assertNull(parseStrongEtag("\"with spaces\""))
        assertNull(parseStrongEtag("W/\"media-v1\""))
        assertNull(parseStrongEtag("\"bad\"quoted\""))
        assertEquals("\"\"", parseStrongEtag("\"\""))
        assertNull(parseStrongEtag("media-v1"))
        assertNull(parseStrongEtag("\"bad\r\nheader\""))
        assertNull(parseStrongEtag(null))
        val identity = RemoteRangeIdentity(12_345L, parseStrongEtag("\"media-v1\""))
        assertEquals(12_345L, identity.length)
        assertEquals("\"media-v1\"", identity.strongEtag)
    }

    @Test fun byteRangeResponsesNeverMixMediaVersions() {
        val identity = RemoteRangeIdentity(1024L, "\"stable-v1\"")
        assertTrue(isConsistentRangeResponse(identity, 0L, 255L, "bytes 0-255/1024", "\"stable-v1\""))
        // A 206 may omit its ETag while still respecting the If-Range request.
        assertTrue(isConsistentRangeResponse(identity, 0L, 255L, "bytes 0-255/1024", null))
        assertFalse(isConsistentRangeResponse(identity, 0L, 255L, "bytes 0-255/1024", "\"changed-v2\""))
        assertFalse(isConsistentRangeResponse(identity, 0L, 255L, "bytes 1-256/1024", "\"stable-v1\""))
        assertFalse(isConsistentRangeResponse(identity, 0L, 255L, "bytes 0-255/2048", "\"stable-v1\""))
        assertFalse(isConsistentRangeResponse(identity, 0L, 255L, null, "\"stable-v1\""))
        assertFalse(isConsistentRangeResponse(identity, 0L, 255L, "bytes 0-255/1024", "W/\"stable-v1\""))
        val noTag = RemoteRangeIdentity(1024L, null)
        assertTrue(isConsistentRangeResponse(noTag, 0L, 255L, "bytes 0-255/1024", null))
    }

    @Test fun guardedLibVlcSourceOnlyAcceptsProgressiveFormats() {
        val url = "https://video.example.org/fixture"
        fun candidate(format: StreamFormat) = StreamCandidate(url, format, "video.example.org")
        assertTrue(MediaForgeGuardedProgressiveSource.supported(candidate(StreamFormat.MP4)))
        assertTrue(MediaForgeGuardedProgressiveSource.supported(candidate(StreamFormat.WEBM)))
        assertFalse(MediaForgeGuardedProgressiveSource.supported(candidate(StreamFormat.HLS)))
        assertFalse(MediaForgeGuardedProgressiveSource.supported(candidate(StreamFormat.DASH)))
        assertFalse(MediaForgeGuardedProgressiveSource.supported(candidate(StreamFormat.MPEG_TS)))
    }
}
