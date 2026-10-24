package com.vaydelux.bousygram.core.media

import com.vaydelux.bousygram.presentation.media.PlaybackQueueItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSessionStateMachineTest {

    private fun createQueue(count: Int = 3): List<PlaybackQueueItem> =
        (0 until count).map { index ->
            PlaybackQueueItem(
                chatId = 100L,
                messageId = 1000L + index,
                title = "Video Episode ${index + 1}",
                fileId = 200 + index,
                durationSeconds = 120,
                fileSizeBytes = 50_000_000L
            )
        }

    @Test
    fun initialStateIsPreparingWhenQueueNotEmpty() {
        val queue = createQueue(3)
        val session = PlaybackSession(queue = queue, initialIndex = 0)

        assertEquals(0, session.currentIndex.value)
        assertTrue(session.hasNext)
        assertFalse(session.hasPrevious)
        assertEquals(200, session.fileId)
        assertEquals(PlaybackState.Preparing(queue[0]), session.state.value)
    }

    @Test
    fun initialStateIsIdleWhenQueueEmpty() {
        val session = PlaybackSession(queue = emptyList())

        assertEquals(0, session.currentIndex.value)
        assertFalse(session.hasNext)
        assertFalse(session.hasPrevious)
        assertNull(session.currentItem)
        assertEquals(PlaybackState.Idle, session.state.value)
    }

    @Test
    fun nextAdvancesQueueCancelsPreviousDownloadsAndTransitionsToPreparing() {
        val queue = createQueue(3)
        val cancelledFileIds = mutableListOf<Int>()
        val session = PlaybackSession(
            queue = queue,
            initialIndex = 0,
            onCancelDownloads = { cancelledFileIds.add(it) }
        )

        val advanced = session.next()

        assertTrue(advanced)
        assertEquals(1, session.currentIndex.value)
        assertEquals(queue[1], session.currentItem)
        assertEquals(listOf(200), cancelledFileIds)
        assertEquals(PlaybackState.Preparing(queue[1]), session.state.value)
        assertTrue(session.hasNext)
        assertTrue(session.hasPrevious)
    }

    @Test
    fun nextAtEndOfQueueReturnsFalseAndDoesNotAdvanceOrCancel() {
        val queue = createQueue(2)
        val cancelledFileIds = mutableListOf<Int>()
        val session = PlaybackSession(
            queue = queue,
            initialIndex = 1,
            onCancelDownloads = { cancelledFileIds.add(it) }
        )

        assertFalse(session.hasNext)
        val advanced = session.next()

        assertFalse(advanced)
        assertEquals(1, session.currentIndex.value)
        assertTrue(cancelledFileIds.isEmpty())
    }

    @Test
    fun previousStepsBackCancelsPreviousDownloadsAndTransitionsToPreparing() {
        val queue = createQueue(3)
        val cancelledFileIds = mutableListOf<Int>()
        val session = PlaybackSession(
            queue = queue,
            initialIndex = 1,
            onCancelDownloads = { cancelledFileIds.add(it) }
        )

        val stepped = session.previous()

        assertTrue(stepped)
        assertEquals(0, session.currentIndex.value)
        assertEquals(queue[0], session.currentItem)
        assertEquals(listOf(201), cancelledFileIds)
        assertEquals(PlaybackState.Preparing(queue[0]), session.state.value)
        assertTrue(session.hasNext)
        assertFalse(session.hasPrevious)
    }

    @Test
    fun previousAtStartOfQueueReturnsFalse() {
        val queue = createQueue(2)
        val cancelledFileIds = mutableListOf<Int>()
        val session = PlaybackSession(
            queue = queue,
            initialIndex = 0,
            onCancelDownloads = { cancelledFileIds.add(it) }
        )

        assertFalse(session.hasPrevious)
        val stepped = session.previous()

        assertFalse(stepped)
        assertEquals(0, session.currentIndex.value)
        assertTrue(cancelledFileIds.isEmpty())
    }

    @Test
    fun goToIndexJumpsToTargetItemAndCancelsCurrentDownload() {
        val queue = createQueue(4)
        val cancelledFileIds = mutableListOf<Int>()
        val session = PlaybackSession(
            queue = queue,
            initialIndex = 0,
            onCancelDownloads = { cancelledFileIds.add(it) }
        )

        val jumped = session.goToIndex(2)

        assertTrue(jumped)
        assertEquals(2, session.currentIndex.value)
        assertEquals(queue[2], session.currentItem)
        assertEquals(listOf(200), cancelledFileIds)
        assertEquals(PlaybackState.Preparing(queue[2]), session.state.value)

        // Same index returns false without re-cancelling
        assertFalse(session.goToIndex(2))
        assertEquals(1, cancelledFileIds.size)

        // Out of bounds returns false
        assertFalse(session.goToIndex(-1))
        assertFalse(session.goToIndex(99))
        assertEquals(2, session.currentIndex.value)
    }

    @Test
    fun stateTransitionsPlayingEndedError() {
        val queue = createQueue(2)
        val session = PlaybackSession(queue = queue, initialIndex = 0)

        session.onPlaying()
        assertEquals(PlaybackState.Playing(queue[0]), session.state.value)

        session.onEnded()
        assertEquals(PlaybackState.Ended(queue[0]), session.state.value)

        val testException = IllegalStateException("Test error")
        session.onError("Playback decode failure", testException)
        val errorState = session.state.value as PlaybackState.Error
        assertEquals(queue[0], errorState.item)
        assertEquals("Playback decode failure", errorState.message)
        assertEquals(testException, errorState.cause)
    }

    @Test
    fun onAutoNextAdvancesUntilEndOfQueue() {
        val queue = createQueue(2)
        val cancelledFileIds = mutableListOf<Int>()
        val session = PlaybackSession(
            queue = queue,
            initialIndex = 0,
            onCancelDownloads = { cancelledFileIds.add(it) }
        )

        val autoNextFirst = session.onAutoNext()
        assertTrue(autoNextFirst)
        assertEquals(1, session.currentIndex.value)
        assertEquals(PlaybackState.Preparing(queue[1]), session.state.value)
        assertEquals(listOf(200), cancelledFileIds)

        val autoNextSecond = session.onAutoNext()
        assertFalse(autoNextSecond)
        assertEquals(1, session.currentIndex.value)
        assertEquals(PlaybackState.Ended(queue[1]), session.state.value)
    }

    @Test
    fun releaseCancelsActiveDownloadsAndTransitionsToIdle() {
        val queue = createQueue(3)
        val cancelledFileIds = mutableListOf<Int>()
        val session = PlaybackSession(
            queue = queue,
            initialIndex = 1,
            onCancelDownloads = { cancelledFileIds.add(it) }
        )

        session.release()

        assertTrue(session.isStopped())
        assertEquals(PlaybackState.Idle, session.state.value)
        assertEquals(listOf(201), cancelledFileIds)

        // Subsequent release call is idempotent
        session.release()
        assertEquals(1, cancelledFileIds.size)
        assertEquals(PlaybackState.Idle, session.state.value)
    }
}
