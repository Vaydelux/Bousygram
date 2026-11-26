package com.vaydelux.bousygram.presentation.extras

import org.junit.Assert.*
import org.junit.Test

class ExtrasDiscoveryPolicyTest {
    private val items = listOf(
        ExtrasItem(42, ExtrasType.ANIME, "One", rating = "9.2"),
        ExtrasItem(43, ExtrasType.ANIME, "Two", rating = "8.1"),
        ExtrasItem(44, ExtrasType.ANIME, "Three", rating = "NaN"),
        ExtrasItem(45, ExtrasType.ANIME, "Four", rating = "11")
    )
    @Test fun prefixesMatchVisibleSectionsAndIgnoreCrossCatalogIds() {
        val library = ExtrasLibrarySnapshot(saved = listOf(
            ExtrasLibraryEntry("TV", 42, "Different title"),
            ExtrasLibraryEntry("ANIME", 42, "One")
        ))
        val model = ExtrasDiscoveryPolicy.resolve(ExtrasType.ANIME, items, library, true, true)
        assertTrue(model.featured)
        assertEquals(listOf("saved", "rated"), model.rails.map { it.key })
        assertEquals(listOf(42), model.rails.first().items.map { it.id })
        assertEquals(listOf(42, 43), model.rails.last().items.map { it.id })
        assertEquals(3, model.posterIndex(0))
        assertEquals(7, model.posterIndex(4))
    }
    @Test fun compactOrFilteredCatalogHasNoPrefix() {
        listOf(false to true, true to false).forEach { (unfiltered, roomy) ->
            val model = ExtrasDiscoveryPolicy.resolve(ExtrasType.ANIME, items, null, unfiltered, roomy)
            assertEquals(0, model.prefixCount)
            assertEquals(3, model.posterIndex(3))
        }
    }
    @Test fun localRailsAreBounded() {
        val library = ExtrasLibrarySnapshot(saved = (1..30).map { ExtrasLibraryEntry("ANIME", it, "Title $it") })
        val model = ExtrasDiscoveryPolicy.resolve(ExtrasType.ANIME, items, library, true, true)
        assertEquals(8, model.rails.first().items.size)
    }
}
