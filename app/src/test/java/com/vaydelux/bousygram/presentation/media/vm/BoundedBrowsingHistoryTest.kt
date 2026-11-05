package com.vaydelux.bousygram.presentation.media.vm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BoundedBrowsingHistoryTest {
    @Test fun oldestUnvisitedFolderEvictsAfterHundredsOfSources() {
        val store = BoundedBrowsingHistory<String, Int>(32)
        for (i in 1..500) {
            store["source-$i"] = i
            assertTrue(store.size() <= 32)
        }
        assertNull(store["source-1"])
        assertEquals(469, store["source-469"])
        assertEquals(500, store["source-500"])
    }

    @Test fun mostRecentlyUsedFocusSurvivesEviction() {
        val store = BoundedBrowsingHistory<String, Int>(3)
        store["A"] = 1
        store["B"] = 2
        store["C"] = 3
        assertEquals(1, store["A"]) // revisit
        store["D"] = 4
        assertNull(store["B"])
        assertEquals(1, store["A"])
        assertEquals(4, store["D"])
    }

    @Test fun atomicViewportAndFocusUpdatesKeepExistingFields() {
        val store = BoundedBrowsingHistory<String, BrowseRestoration>(3)
        store.compute("saved") { _, previous ->
            (previous ?: BrowseRestoration()).copy(firstVisibleItemIndex = 80)
        }
        store.compute("saved") { _, previous ->
            (previous ?: BrowseRestoration()).copy(focusedMediaKey = "11:20")
        }
        assertEquals(80, store["saved"]?.firstVisibleItemIndex)
        assertEquals("11:20", store["saved"]?.focusedMediaKey)
    }

    @Test fun pressureTrimKeepsMostRecentAndZeroTrimReleasesAll() {
        val store = BoundedBrowsingHistory<String, Int>(4)
        (1..4).forEach { store["key-$it"] = it }
        store["key-1"]
        store.trimToSize(2)
        assertEquals(2, store.size())
        assertEquals(1, store["key-1"])
        assertEquals(4, store["key-4"])
        store.trimToSize(0)
        assertEquals(0, store.size())
    }

    @Test fun concurrentUpdatesNeverExceedBudget() {
        val store = BoundedBrowsingHistory<Int, Int>(24)
        val start = CountDownLatch(1)
        val finish = CountDownLatch(4)
        repeat(4) { worker ->
            Thread {
                start.await()
                repeat(300) { index ->
                    val key = worker * 300 + index
                    store.compute(key) { _, old -> (old ?: 0) + 1 }
                }
                finish.countDown()
            }.start()
        }
        start.countDown()
        assertTrue(finish.await(10, TimeUnit.SECONDS))
        assertTrue(store.size() <= 24)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroCapacity() {
        BoundedBrowsingHistory<String, Int>(0)
    }
}
