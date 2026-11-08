package com.vaydelux.bousygram.presentation.extras

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtrasPagingWindowTest {
    private fun item(id: Int, type: ExtrasType = ExtrasType.ANIME): ExtrasItem =
        ExtrasItem(id = id, type = type, title = "Fixture $id")

    @Test fun restoredTabIndicesAlwaysResolveToOneVisibleCatalog() {
        assertEquals(ExtrasType.MOVIES.ordinal, ExtrasPagingWindow.activeTabIndex(-42))
        assertEquals(ExtrasType.TV.ordinal, ExtrasPagingWindow.activeTabIndex(ExtrasType.TV.ordinal))
        assertEquals(ExtrasType.ANIME.ordinal, ExtrasPagingWindow.activeTabIndex(Int.MAX_VALUE))
        assertEquals(ExtrasType.MOVIES.ordinal, ExtrasPagingWindow.activeTabIndex(Int.MIN_VALUE))
        // At all times exactly one current catalog is protected from dormant trimming.
        for (saved in listOf(-100, 0, 1, 2, 42, Int.MAX_VALUE)) {
            val current = ExtrasPagingWindow.activeTabIndex(saved)
            assertEquals(1, ExtrasType.entries.count { it.ordinal == current })
            assertEquals(2, ExtrasType.entries.count { it.ordinal != current })
        }
    }

    @Test fun memoryTrimAlwaysProtectsTheCurrentlyVisibleTab() {
        for (savedActive in listOf(-1, 0, 1, 2, 99, Int.MAX_VALUE)) {
            val active = ExtrasPagingWindow.activeTabIndex(savedActive)
            for (tab in ExtrasType.entries) {
                val shouldTrim = ExtrasPagingWindow.shouldReleaseDormantPosters(
                    savedActive, tab
                )
                assertEquals(
                    "Incorrect trim target for active $savedActive and candidate $tab",
                    tab.ordinal != active, shouldTrim
                )
            }
        }
    }

    @Test fun repeatedDormantTrimIsIdentityNoOpAfterReleasingPosters() {
        val original = TabState(
            query = "Remember my search",
            results = listOf(item(1), item(2)),
            page = 3,
            droppedResults = 21
        )
        val first = original.releaseDormantPosters()
        assertTrue(first.results.isEmpty())
        assertEquals("Remember my search", first.query)
        assertTrue(
            "Repeated OS pressure events should not allocate a redundant tab snapshot",
            first === first.releaseDormantPosters()
        )
        val empty = TabState()
        assertTrue("Fresh default tab requires no trim allocation",
            empty === empty.releaseDormantPosters())
    }

    @Test fun arm32WindowNeverRetainsMoreThan120UniquePostersDuringLongSession() {
        val limit = ExtrasPagingWindow.itemLimit(lowMemory = true)
        assertEquals(120, limit)
        var retained = emptyList<ExtrasItem>()
        var dropped = 0
        // 100 pages of 20 synthetic items = 2,000 catalog rows, 0 network calls.
        for (page in 0 until 100) {
            val next = (page * 20 + 1..page * 20 + 20).map { item(it) }
            val result = ExtrasPagingWindow.merge(retained, next, limit)
            retained = result.items
            dropped += result.removedFromFront
            assertTrue("Unbounded page $page", retained.size <= limit)
            assertEquals(retained.size, retained.distinctBy { it.type to it.id }.size)
        }
        assertEquals(120, retained.size)
        assertEquals(1_880, dropped)
        assertEquals(1_881, retained.first().id)
        assertEquals(2_000, retained.last().id)
    }

    @Test fun standardDeviceWindowStaysAt240AcrossHundredsOfPages() {
        val limit = ExtrasPagingWindow.itemLimit(lowMemory = false)
        assertEquals(240, limit)
        var retained = emptyList<ExtrasItem>()
        for (page in 0 until 300) {
            val next = (page * 20 + 1..page * 20 + 20).map { item(it) }
            retained = ExtrasPagingWindow.merge(retained, next, limit).items
            assertTrue(retained.size <= limit)
        }
        assertEquals(240, retained.size)
        assertEquals(5_761, retained.first().id)
        assertEquals(6_000, retained.last().id)
    }

    @Test fun unexpectedlyLargeProviderPageCannotExceedTheMemoryBudget() {
        val oversizedPage = (1..1_000).map { item(it) }
        val result = ExtrasPagingWindow.merge(
            existing = emptyList(),
            incoming = oversizedPage,
            maximumItems = ExtrasPagingWindow.LOW_RAM_MAX_ITEMS
        )
        assertEquals(120, result.items.size)
        assertEquals(880, result.removedFromFront)
        assertEquals(881, result.items.first().id)
        assertEquals(1_000, result.items.last().id)
    }

    @Test fun duplicatesDoNotInflateTheWindowOrDropActiveKeys() {
        val existing = (1..120).map { item(it) }
        val next = (115..134).map { item(it) }
        val result = ExtrasPagingWindow.merge(existing, next, 120)
        assertEquals(120, result.items.size)
        assertEquals(14, result.removedFromFront)
        assertEquals(15, result.items.first().id)
        assertEquals(134, result.items.last().id)
        assertTrue(result.items.any { it.id == 115 })
    }

    @Test fun providerIdNeverCrossContaminatesDeduplication() {
        val result = ExtrasPagingWindow.merge(
            listOf(item(14, ExtrasType.MOVIES)),
            listOf(item(14, ExtrasType.TV), item(14, ExtrasType.ANIME)),
            maximumItems = 10
        )
        assertEquals(3, result.items.size)
        assertEquals(0, result.removedFromFront)
        assertEquals(3, result.items.map { it.type }.toSet().size)
    }

    @Test fun emptyPageKeepsExistingAndIsSafeToRewind() {
        val current = (1..60).map { item(it) }
        val result = ExtrasPagingWindow.merge(current, emptyList(), 120)
        assertEquals(current, result.items)
        assertEquals(0, result.removedFromFront)
        val fresh = ExtrasPagingWindow.merge(emptyList(), (1..20).map { item(it) }, 120)
        assertEquals(20, fresh.items.size)
        assertEquals(1, fresh.items.first().id)
    }

    @Test fun incomingDuplicateReusesStablePositionWithUpdatedMetadata() {
        val old = item(7)
        val changed = old.copy(title = "Updated")
        val result = ExtrasPagingWindow.merge(listOf(item(6), old), listOf(changed), 10)
        assertEquals(listOf(6, 7), result.items.map { it.id })
        assertEquals("Updated", result.items.last().title)
        assertFalse(result.items.size > 10)
    }

    @Test fun dormantArm32TabReleasesCachedPostersWithoutLosingSearchSettings() {
        val old = TabState(
            query = "favorite series",
            results = (1..120).map(::item),
            page = 9,
            droppedResults = 75,
            loading = true,
            error = "Previous fetch failed"
        )
        val trimmed = old.releaseDormantPosters()
        assertEquals("favorite series", trimmed.query)
        assertEquals(old.filters, trimmed.filters)
        assertTrue(trimmed.results.isEmpty())
        assertEquals(0, trimmed.page)
        assertEquals(0, trimmed.droppedResults)
        assertTrue(trimmed.hasMore)
        assertFalse(trimmed.loading)
        assertEquals(null, trimmed.error)
        assertEquals(120, old.results.size) // Existing snapshot is immutable.
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidMaximumIsRejected() {
        ExtrasPagingWindow.merge(emptyList(), listOf(item(1)), 0)
    }
}
