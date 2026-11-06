package com.vaydelux.bousygram.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class FocusCoordinatorTest {
    @Test fun missingAndUnattachedFocusTargetsCannotReportSuccess() {
        val focus = FocusCoordinator()
        assertFalse(focus.requestFocus("unknown"))
        focus.getOrCreateRequester("unattached")
        assertFalse(focus.requestFocus("unattached"))
    }

    @Test fun focusRequesterRemainsStableAndScreenHistoryCanBeCleared() {
        val focus = FocusCoordinator()
        val requester = focus.getOrCreateRequester("library-card")
        assertSame(requester, focus.getOrCreateRequester("library-card"))
        focus.recordFocus("library", "card-8")
        assertEquals("card-8", focus.getLastFocusedKey("library", "first"))
        focus.clearScreen("library")
        assertEquals("first", focus.getLastFocusedKey("library", "first"))
    }

    @Test fun restorationHistoryEvictsTheOldestUnvisitedScreen() {
        val focus = FocusCoordinator()
        repeat(64) { focus.recordFocus("screen-$it", "card-$it") }
        assertEquals("card-0", focus.getLastFocusedKey("screen-0", "missing"))
        focus.recordFocus("screen-64", "card-64")
        assertEquals("missing", focus.getLastFocusedKey("screen-1", "missing"))
        assertEquals("card-0", focus.getLastFocusedKey("screen-0", "missing"))
        assertEquals(64, focus.rememberedScreenCount())
    }

    @Test fun concurrentFocusUpdatesStayWithinTheMemoryBudget() {
        val focus = FocusCoordinator()
        val start = CountDownLatch(1)
        val done = CountDownLatch(4)
        repeat(4) { worker ->
            Thread {
                start.await()
                repeat(250) { index ->
                    val key = "s-$worker-$index"
                    focus.recordFocus(key, "item")
                    focus.getLastFocusedKey(key, "fallback")
                }
                done.countDown()
            }.start()
        }
        start.countDown()
        assertTrue(done.await(10, TimeUnit.SECONDS))
        assertTrue(focus.rememberedScreenCount() <= 64)
    }
}
