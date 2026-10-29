package com.vaydelux.bousygram.presentation.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSearchSessionTest {
    @Test
    fun cancellationRejectsResultsAndFallbackFromPriorRecognizer() {
        val session = VoiceSearchSession()
        val request = session.begin()!!

        session.cancel()

        assertFalse(session.isActive(request))
        assertFalse(session.complete(request))
        assertNull(session.replace(request))
    }

    @Test
    fun fallbackInvalidatesEveryCallbackFromDirectRecognizer() {
        val session = VoiceSearchSession()
        val directRequest = session.begin()!!
        val fallbackRequest = session.replace(directRequest)!!

        assertNotEquals(directRequest, fallbackRequest)
        assertFalse(session.isActive(directRequest))
        assertNull(session.replace(directRequest))
        assertFalse(session.complete(directRequest))
        assertTrue(session.isActive(fallbackRequest))
    }

    @Test
    fun aCompletedRequestCannotDeliverTheSameQueryTwice() {
        val session = VoiceSearchSession()
        val request = session.begin()!!

        assertTrue(session.complete(request))
        assertFalse(session.complete(request))
        assertFalse(session.isActive(request))
    }

    @Test
    fun freshRequestRemainsActiveAfterLateCallbackFromCanceledRequest() {
        val session = VoiceSearchSession()
        val oldRequest = session.begin()!!
        session.cancel()
        val newRequest = session.begin()!!

        assertFalse(session.complete(oldRequest))
        assertNull(session.replace(oldRequest))
        assertTrue(session.isActive(newRequest))
    }

    @Test
    fun disposalPermanentlyRejectsCallbacksAndNewRequests() {
        val session = VoiceSearchSession()
        val request = session.begin()!!
        session.dispose()

        assertFalse(session.isActive(request))
        assertNull(session.replace(request))
        assertFalse(session.complete(request))
        assertNull(session.begin())
    }

    @Test
    fun permissionCompletionCanStartOneDirectRecognitionStage() {
        val session = VoiceSearchSession()
        val permissionRequest = session.begin()!!
        val directRequest = session.replace(permissionRequest)

        assertNotNull(directRequest)
        assertNull(session.replace(permissionRequest))
        assertTrue(session.isActive(directRequest!!))
    }
}
