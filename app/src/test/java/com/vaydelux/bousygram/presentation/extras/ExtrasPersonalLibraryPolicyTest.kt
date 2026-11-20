package com.vaydelux.bousygram.presentation.extras

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtrasPersonalLibraryPolicyTest {
    private fun item(type: String = "MOVIES", id: Int = 7) =
        ExtrasLibraryEntry(type, id, "Reference title", "2026", "Synopsis")

    @Test fun identifiersAreProviderScoped() {
        var state = ExtrasLibrarySnapshot()
        for (type in listOf("MOVIES", "TV", "ANIME")) {
            state = ExtrasPersonalLibraryPolicy.toggleSaved(state, item(type))
        }
        assertEquals(3, state.saved.size)
        assertTrue(ExtrasPersonalLibraryPolicy.containsSaved(state, "MOVIES", 7))
        assertTrue(ExtrasPersonalLibraryPolicy.containsSaved(state, "TV", 7))
        assertTrue(ExtrasPersonalLibraryPolicy.containsSaved(state, "ANIME", 7))
        state = ExtrasPersonalLibraryPolicy.toggleSaved(state, item("MOVIES"))
        assertFalse(ExtrasPersonalLibraryPolicy.containsSaved(state, "MOVIES", 7))
        assertEquals(2, state.saved.size)
    }

    @Test fun recentIsDistinctOrderedAndNeverModifiesSaved() {
        var state = ExtrasPersonalLibraryPolicy.toggleSaved(ExtrasLibrarySnapshot(), item())
        state = ExtrasPersonalLibraryPolicy.markOpened(state, item("MOVIES", 1))
        state = ExtrasPersonalLibraryPolicy.markOpened(state, item("TV", 2))
        state = ExtrasPersonalLibraryPolicy.markOpened(state, item("MOVIES", 1))
        assertEquals(listOf("MOVIES:1", "TV:2"), state.recent.map { it.key })
        assertEquals(1, state.saved.size)
        assertTrue(ExtrasPersonalLibraryPolicy.clearRecent(state).saved.isNotEmpty())
    }

    @Test fun largeCollectionsHaveFixedBounds() {
        val state = (1..420).fold(ExtrasLibrarySnapshot()) { acc, i ->
            ExtrasPersonalLibraryPolicy.toggleSaved(acc, item("ANIME", i))
        }
        assertEquals(ExtrasPersonalLibraryPolicy.MAX_SAVED, state.saved.size)
        assertEquals("ANIME:150", state.saved.first().key)
        assertEquals("ANIME:1", state.saved.last().key)
        val visited = (1..90).fold(state) { acc, i ->
            ExtrasPersonalLibraryPolicy.markOpened(acc, item("TV", i))
        }
        assertEquals(ExtrasPersonalLibraryPolicy.MAX_RECENT, visited.recent.size)
        assertEquals("TV:90", visited.recent.first().key)
    }

    @Test fun invalidProviderAndMetadataAreRejected() {
        assertNull(ExtrasPersonalLibraryPolicy.normalize(item("TELEGRAM")))
        assertNull(ExtrasPersonalLibraryPolicy.normalize(item(id = 0)))
        assertNull(ExtrasPersonalLibraryPolicy.normalize(item().copy(title = "  ")))
        val normalized = ExtrasPersonalLibraryPolicy.normalize(
            item().copy(
                title = " X ".repeat(150),
                description = "y".repeat(2000),
                poster = "http://127.0.0.1/private"
            )
        )!!
        assertEquals(160, normalized.title.length)
        assertEquals(400, normalized.description.length)
        assertNull(normalized.poster)
    }

    @Test fun sanitizationDeduplicatesAndValidatesOrder() {
        val entry = item()
        val snapshot = ExtrasPersonalLibraryPolicy.sanitize(
            ExtrasLibrarySnapshot(
                saved = listOf(entry, entry.copy(title = "Duplicate"), item("ANIME", 22)),
                recent = listOf(item("INVALID"), entry)
            )
        )
        assertEquals(listOf("MOVIES:7", "ANIME:22"), snapshot.saved.map { it.key })
        assertEquals(listOf("MOVIES:7"), snapshot.recent.map { it.key })
    }

    @Test fun deletingSavedEntryDoesNotEraseRecent() {
        val entry = item()
        var state = ExtrasPersonalLibraryPolicy.toggleSaved(ExtrasLibrarySnapshot(), entry)
        state = ExtrasPersonalLibraryPolicy.markOpened(state, entry)
        state = ExtrasPersonalLibraryPolicy.removeSaved(state, "MOVIES", 7)
        assertTrue(state.saved.isEmpty())
        assertEquals(1, state.recent.size)
    }
}
