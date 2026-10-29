package com.vaydelux.bousygram.core.media

import com.vaydelux.bousygram.presentation.media.PlaybackQueueItem
import org.junit.Assert.*
import org.junit.Test

class PlaybackQueueCoordinatorTest {
    private fun create() = PlaybackSessionController.start(queue = (1..3).map {
        PlaybackQueueItem(chatId = 10L, messageId = it.toLong(), title = "Episode $it", fileId = 200 + it)
    })

    @Test fun rejectedAndConcurrentMovesCannotDestroyPlayback() {
        val session = create()
        val navigator = PlaybackQueueCoordinator(session)
        val events = mutableListOf<String>()
        val leave: (PlaybackQueueItem) -> Unit = { events += "leave:${it.fileId}" }
        val reset: () -> Unit = { events += "reset" }
        assertFalse(navigator.moveTo(-1, leave, reset))
        assertFalse(navigator.moveTo(0, leave, reset))
        assertFalse(navigator.moveTo(3, leave, reset))
        assertTrue(events.isEmpty())
        assertTrue(navigator.moveTo(1, leave, reset))
        assertFalse(navigator.moveTo(2, leave, reset))
        assertEquals(listOf("leave:201","reset"), events)
        session.finishTransition()
        assertTrue(navigator.moveTo(2, leave, reset))
        session.finishTransition()
        assertTrue(navigator.moveTo(0, leave, reset))
        assertEquals(listOf("leave:201","reset","leave:202","reset","leave:203","reset"), events)
        assertFalse(session.isStopped())
        PlaybackSessionController.stop(session, PlaybackCacheStopReason.Back)
    }

    @Test fun staleNavigationAfterExitDoesNotInvokeCleanup() {
        val session = create()
        val navigator = PlaybackQueueCoordinator(session)
        PlaybackSessionController.stop(session, PlaybackCacheStopReason.Back)
        var touched = false
        assertFalse(navigator.moveTo(1, { touched = true }, { touched = true }))
        assertFalse(touched)
    }
}
