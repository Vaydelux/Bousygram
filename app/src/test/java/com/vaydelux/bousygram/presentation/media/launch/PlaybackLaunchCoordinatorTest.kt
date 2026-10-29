package com.vaydelux.bousygram.presentation.media.launch

import com.vaydelux.bousygram.data.store.SelectedPlayerEngine
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.MediaType
import com.vaydelux.bousygram.presentation.media.queueIndexForRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackLaunchCoordinatorTest {

    private fun sampleItem(
        messageId: Long = 100L,
        fileId: Int? = 555,
        title: String = "Test Video"
    ) = MediaItem(
        chatId = 1234L,
        messageId = messageId,
        date = 1700000000,
        type = MediaType.Video,
        title = title,
        fileId = fileId,
        durationSeconds = 120,
        fileSizeBytes = 50_000_000L,
        fileName = "video.mp4",
        mimeType = "video/mp4"
    )

    @Test
    fun fastPreparation_emitsOpenWithPreparedTrueAndUrl() = runTest {
        var clockMs = 1_000L
        val coordinator = PlaybackLaunchCoordinator(
            preflight = StreamPreflight { _, _ -> "http://127.0.0.1:8080/stream" },
            preflightBudgetMs = 500L,
            indicatorDelayMs = 120L,
            duplicateWindowMs = 600L,
            clock = { clockMs }
        )

        var outcome: LaunchOutcome? = null
        val item = sampleItem()

        coordinator.launch(
            scope = this,
            item = item,
            queue = listOf(item),
            anchor = LaunchAnchor("home", "sec1", PlaybackLaunchCoordinator.mediaKey(item))
        ) { outcome = it }

        advanceTimeBy(50)

        assertTrue(outcome is LaunchOutcome.Open)
        val open = outcome as LaunchOutcome.Open
        assertTrue(open.prepared)
        assertEquals("http://127.0.0.1:8080/stream", open.request.initialStreamUrl)
        assertEquals(item.fileId, open.request.fileId)
    }

    @Test
    fun slowPreparationExceedingDeadline_emitsOpenWithPreparedFalseAndNullUrl() = runTest {
        var clockMs = 1_000L
        val coordinator = PlaybackLaunchCoordinator(
            preflight = StreamPreflight { _, _ ->
                delay(1000L) // Slow preflight exceeding 500ms budget
                "http://127.0.0.1:8080/stream"
            },
            preflightBudgetMs = 500L,
            indicatorDelayMs = 120L,
            duplicateWindowMs = 600L,
            clock = { clockMs }
        )

        var outcome: LaunchOutcome? = null
        val item = sampleItem()

        coordinator.launch(
            scope = this,
            item = item,
            queue = listOf(item)
        ) { outcome = it }

        advanceTimeBy(200) // Indicator appears at 120ms
        val state = coordinator.state.value
        assertTrue(state.isPending && state.showIndicator)

        advanceTimeBy(350) // Total 550ms, past budget

        assertTrue(outcome is LaunchOutcome.Open)
        val open = outcome as LaunchOutcome.Open
        assertFalse("Past budget must be marked unprepared", open.prepared)
        assertNull("Past budget must have null initialStreamUrl", open.request.initialStreamUrl)
    }

    @Test
    fun transientFailure_emitsOpenWithPreparedFalse() = runTest {
        val coordinator = PlaybackLaunchCoordinator(
            preflight = StreamPreflight { _, _ -> throw IOException("Network connection dropped") },
            preflightBudgetMs = 500L
        )

        var outcome: LaunchOutcome? = null
        val item = sampleItem()

        coordinator.launch(
            scope = this,
            item = item,
            queue = listOf(item)
        ) { outcome = it }

        advanceTimeBy(50)

        assertTrue(outcome is LaunchOutcome.Open)
        val open = outcome as LaunchOutcome.Open
        assertFalse(open.prepared)
        assertNull(open.request.initialStreamUrl)
    }

    @Test
    fun preferenceWriteFailureDoesNotStopVideoNavigation() = runTest {
        val coordinator = PlaybackLaunchCoordinator(
            preflight = StreamPreflight { _, _ -> null },
            onBeforeOpen = { throw IllegalStateException("Storage temporarily unavailable") }
        )
        val item = sampleItem()
        var outcome: LaunchOutcome? = null
        coordinator.launch(this, item, listOf(item)) { outcome = it }
        advanceTimeBy(50)
        assertTrue(outcome is LaunchOutcome.Open)
        assertFalse(coordinator.state.value.isPending)
    }

    @Test
    fun missingFileId_rejected() = runTest {
        val coordinator = PlaybackLaunchCoordinator(
            preflight = StreamPreflight { _, _ -> "http://stream" }
        )

        var outcome: LaunchOutcome? = null
        val item = sampleItem(fileId = null)

        coordinator.launch(
            scope = this,
            item = item,
            queue = listOf(item)
        ) { outcome = it }

        advanceTimeBy(10)

        assertTrue(outcome is LaunchOutcome.Rejected)
    }

    @Test
    fun duplicateLaunch_whilePending_emitsDuplicate() = runTest {
        val coordinator = PlaybackLaunchCoordinator(
            preflight = StreamPreflight { _, _ ->
                delay(400L)
                "http://stream"
            }
        )

        val item = sampleItem()
        var outcome1: LaunchOutcome? = null
        var outcome2: LaunchOutcome? = null

        coordinator.launch(this, item, listOf(item)) { outcome1 = it }
        advanceTimeBy(100)

        // Second press while still pending
        coordinator.launch(this, item, listOf(item)) { outcome2 = it }

        assertEquals(LaunchOutcome.Duplicate, outcome2)
    }

    @Test
    fun duplicateLaunch_withinGracePeriod_emitsDuplicate() = runTest {
        var clockMs = 1_000L
        val coordinator = PlaybackLaunchCoordinator(
            preflight = StreamPreflight { _, _ -> "http://stream" },
            duplicateWindowMs = 600L,
            clock = { clockMs }
        )

        val item = sampleItem()
        var outcome1: LaunchOutcome? = null
        var outcome2: LaunchOutcome? = null

        coordinator.launch(this, item, listOf(item)) { outcome1 = it }
        advanceTimeBy(10)
        assertTrue(outcome1 is LaunchOutcome.Open)

        // Advance 200ms (within 600ms grace window)
        clockMs += 200L
        coordinator.launch(this, item, listOf(item)) { outcome2 = it }

        assertEquals(LaunchOutcome.Duplicate, outcome2)

        // Advance past 600ms grace window
        clockMs += 500L
        var outcome3: LaunchOutcome? = null
        coordinator.launch(this, item, listOf(item)) { outcome3 = it }
        advanceTimeBy(10)
        assertTrue(outcome3 is LaunchOutcome.Open)
    }

    @Test
    fun cancel_duringPreflight_emitsCancelled() = runTest {
        val coordinator = PlaybackLaunchCoordinator(
            preflight = StreamPreflight { _, _ ->
                delay(400L)
                "http://stream"
            }
        )

        val item = sampleItem()
        var outcome: LaunchOutcome? = null

        coordinator.launch(this, item, listOf(item)) { outcome = it }
        advanceTimeBy(100)

        coordinator.cancel()
        advanceTimeBy(10)

        assertEquals(LaunchOutcome.Cancelled, outcome)
        assertFalse(coordinator.state.value.isPending)
    }

    @Test
    fun telegramStreamPreflight_exoEngineSkipsUrl() = runTest {
        var warmUpCalled = false
        val preflight = TelegramStreamPreflight(
            engine = { SelectedPlayerEngine.ExoPlayer },
            warmUp = { _, _ -> warmUpCalled = true }
        )
        val item = sampleItem()

        val url = preflight.prepare(item, deadlineAtMs = System.currentTimeMillis() + 500L)
        assertNull("ExoPlayer engine must not mint a loopback stream URL", url)
        assertTrue("ExoPlayer preflight kicks warmup", warmUpCalled)
    }

    @Test
    fun queuePreservesSelectedMessageIdentity() = runTest {
        val coordinator = PlaybackLaunchCoordinator(
            preflight = StreamPreflight { _, _ -> "http://stream" }
        )

        val item1 = sampleItem(messageId = 1L, title = "Ep 1")
        val item2 = sampleItem(messageId = 2L, title = "Ep 2")
        val item3 = sampleItem(messageId = 3L, title = "Ep 3")
        val queue = listOf(item1, item2, item3)

        var outcome: LaunchOutcome? = null
        coordinator.launch(this, item2, queue) { outcome = it }
        advanceTimeBy(10)

        val open = outcome as LaunchOutcome.Open
        val idx = queueIndexForRequest(
            open.request.queue,
            open.request.chatId,
            open.request.messageId,
            open.request.fileId
        )
        assertEquals(1, idx)
        assertEquals(2L, open.request.messageId)
        assertEquals(3, open.request.queue.size)
    }
}
