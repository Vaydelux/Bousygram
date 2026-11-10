package com.vaydelux.mediaforge.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class MediaForgeSessionTest {
    private val embed = "https://video.example.org/embed/movie/42"
    private val first = "https://cdn.example.org/movie/master.m3u8"
    private val second = "https://media.example.org/movie/master.m3u8"

    private fun sdk(
        inspect: suspend (StreamCandidate, List<CaptionCandidate>) -> InspectionResult
    ) = MediaForgeSdk(MediaForgeInspector(inspect))

    private fun approved(candidate: StreamCandidate) = InspectionResult.Playable(
        InspectedMedia(candidate, emptyList(), 206)
    )

    private fun rejectsRequest(action: () -> Unit) {
        try {
            action()
            fail("Expected invalid SDK request to be rejected")
        } catch (_: IllegalArgumentException) {
            // Invalid requests must never create a browser/media session.
        }
    }

    @Test fun rejectsUnsafeEmbedAndUnboundedResourceBudgets() {
        rejectsRequest { MediaForgeSessionRequest("http://video.example.org/embed/movie/42") }
        rejectsRequest { MediaForgeSessionRequest("https://127.0.0.1/embed") }
        rejectsRequest { MediaForgeSessionRequest(embed, maxStreams = 0) }
        rejectsRequest { MediaForgeSessionRequest(embed, maxCaptions = 101) }
        rejectsRequest { MediaForgeDiscovery(maxStreams = 0) }
        rejectsRequest { MediaForgeDiscovery(maxCaptions = 101) }
    }

    @Test fun opaqueIdsAreStableDuringObservationAndNeverReusedAfterClear() {
        val session = sdk { stream, _ -> approved(stream) }
            .openSession(MediaForgeSessionRequest(embed, maxStreams = 2))
        assertTrue(session.observe(first))
        val initial = session.snapshot().streams.single()
        assertFalse(initial.id.contains("https"))
        assertFalse(initial.id.contains("example"))
        assertFalse(session.observe(first))
        assertEquals(initial.id, session.snapshot().streams.single().id)
        assertTrue(session.observe(second))
        assertTrue(session.observe("https://third.example.org/other.mpd"))
        assertEquals(2, session.snapshot().streams.size)
        assertFalse(session.snapshot().streams.any { it.id == initial.id })
        session.clear()
        assertTrue(session.snapshot().streams.isEmpty())
        assertTrue(session.observe(first))
        assertNotEquals(initial.id, session.snapshot().streams.single().id)
        session.close()
        assertEquals(MediaForgeSessionState.Closed, session.state.value)
        assertFalse(session.observe(first))
        assertTrue(session.snapshot().streams.isEmpty())
        session.close()
    }

    @Test fun inspectionAcceptsOnlyAnIdIssuedByTheCurrentSession() = runBlocking {
        val calls = AtomicInteger(0)
        val session = sdk { stream, _ ->
            calls.incrementAndGet()
            approved(stream)
        }.openSession(MediaForgeSessionRequest(embed))
        assertEquals(
            MediaForgeSessionInspection.Rejected(MediaForgeInspectionFailure.NOT_OBSERVED),
            session.inspect(first)
        )
        assertTrue(session.observe(first))
        val id = session.snapshot().streams.single().id
        assertTrue(session.inspect(id) is MediaForgeSessionInspection.Verified)
        assertEquals(1, calls.get())
        session.clear()
        assertEquals(
            MediaForgeSessionInspection.Rejected(MediaForgeInspectionFailure.NOT_OBSERVED),
            session.inspect(id)
        )
        assertEquals(1, calls.get())
        session.close()
        assertEquals(
            MediaForgeSessionInspection.Rejected(MediaForgeInspectionFailure.SESSION_CLOSED),
            session.inspect(id)
        )
    }

    @Test fun signedCandidateIdsAreIsolatedAcrossIndependentSessions() = runBlocking {
        val sdk = sdk { stream, _ -> approved(stream) }
        val a = sdk.openSession(MediaForgeSessionRequest(embed))
        val b = sdk.openSession(MediaForgeSessionRequest(embed))
        a.observe(first)
        b.observe(second)
        val aId = a.snapshot().streams.single().id
        // A different session must not be able to inspect A's selected media.
        // IDs can have the same counter in different sessions, so use a
        // session-specific opaque prefix rather than relying on the URL.
        val bId = b.snapshot().streams.single().id
        assertNotEquals(aId, bId)
        assertEquals(
            MediaForgeSessionInspection.Rejected(MediaForgeInspectionFailure.NOT_OBSERVED),
            b.inspect(aId)
        )
        assertTrue(b.inspect(bId) is MediaForgeSessionInspection.Verified)
        a.close()
        assertTrue(b.inspect(bId) is MediaForgeSessionInspection.Verified)
        b.close()
    }

    @Test fun clearCancelsActiveProbeAndPreventsStaleApproval() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val session = sdk { _, _ ->
            started.complete(Unit)
            awaitCancellation()
        }.openSession(MediaForgeSessionRequest(embed))
        session.observe(first)
        val id = session.snapshot().streams.single().id
        val probe = async { session.inspect(id) }
        withTimeout(5000) { started.await() }
        session.clear()
        assertEquals(
            MediaForgeSessionInspection.Rejected(MediaForgeInspectionFailure.SUPERSEDED),
            withTimeout(5000) { probe.await() }
        )
        session.close()
    }

    @Test fun closeCancelsActiveProbeAndDoesNotExposeUrls() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val session = sdk { _, _ ->
            started.complete(Unit)
            awaitCancellation()
        }.openSession(MediaForgeSessionRequest(embed))
        session.observe(first)
        val probe = async { session.inspect(session.snapshot().streams.single().id) }
        withTimeout(5000) { started.await() }
        session.close()
        assertEquals(
            MediaForgeSessionInspection.Rejected(MediaForgeInspectionFailure.SESSION_CLOSED),
            withTimeout(5000) { probe.await() }
        )
        assertEquals(MediaForgeSessionState.Closed, session.state.value)
    }

    @Test fun laterProbeSupersedesAnEarlierProbe() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val calls = AtomicInteger(0)
        val session = sdk { stream, _ ->
            if (calls.incrementAndGet() == 1) {
                started.complete(Unit)
                awaitCancellation()
            }
            approved(stream)
        }.openSession(MediaForgeSessionRequest(embed))
        session.observe(first)
        session.observe(second)
        val choices = session.snapshot().streams
        val firstProbe = async { session.inspect(choices[0].id) }
        withTimeout(5000) { started.await() }
        assertTrue(withTimeout(5000) {
            session.inspect(choices[1].id)
        } is MediaForgeSessionInspection.Verified)
        assertEquals(
            MediaForgeSessionInspection.Rejected(MediaForgeInspectionFailure.SUPERSEDED),
            withTimeout(5000) { firstProbe.await() }
        )
        session.close()
    }

    @Test fun inspectorCannotReturnNonHttpsOrSpoofedHostAsVerified() = runBlocking {
        val session = sdk { stream, _ ->
            approved(stream.copy(url = "http://10.0.0.1/movie.m3u8", host = "cdn.example.org"))
        }.openSession(MediaForgeSessionRequest(embed))
        session.observe(first)
        assertEquals(
            MediaForgeSessionInspection.Rejected(MediaForgeInspectionFailure.UNSAFE_RESULT),
            session.inspect(session.snapshot().streams.single().id)
        )
        session.close()
    }
}
