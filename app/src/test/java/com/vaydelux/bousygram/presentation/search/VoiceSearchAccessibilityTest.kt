package com.vaydelux.bousygram.presentation.search

import com.vaydelux.bousygram.presentation.navigation.SearchFocusState
import com.vaydelux.bousygram.presentation.navigation.SearchFocusStateSaver
import com.vaydelux.bousygram.presentation.navigation.SearchFocusTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSearchAccessibilityTest {

    @Test
    fun testSearchFocusTargetEnumIncludesVoiceButton() {
        val targets = SearchFocusTarget.entries
        assertTrue("SearchFocusTarget should contain VoiceButton", targets.contains(SearchFocusTarget.VoiceButton))
        assertTrue("SearchFocusTarget should contain SearchField", targets.contains(SearchFocusTarget.SearchField))
        assertTrue("SearchFocusTarget should contain Grid", targets.contains(SearchFocusTarget.Grid))
    }

    @Test
    fun testSearchFocusStateSaverPreservesVoiceButtonTarget() {
        val original = SearchFocusState(
            target = SearchFocusTarget.VoiceButton,
            gridIndex = 4
        )

        val scope = object : androidx.compose.runtime.saveable.SaverScope {
            override fun canBeSaved(value: Any): Boolean = true
        }
        val saved = with(SearchFocusStateSaver) { scope.save(original) }
        assertNotNull(saved)

        val restored = SearchFocusStateSaver.restore(saved!!)
        assertNotNull(restored)
        assertEquals(SearchFocusTarget.VoiceButton, restored!!.target)
        assertEquals(4, restored.gridIndex)
    }

    @Test
    fun testVoiceSearchPromptGeneration() {
        val globalPrompt = buildVoiceSearchPrompt(SearchMode.Global, "")
        assertEquals("Speak a video or audio title", globalPrompt)

        val inChatPrompt = buildVoiceSearchPrompt(SearchMode.InChat, "SciFi Channel")
        assertEquals("Search in SciFi Channel", inChatPrompt)

        val inChatBlankPrompt = buildVoiceSearchPrompt(SearchMode.InChat, "")
        assertEquals("Search in current source", inChatBlankPrompt)
    }

    @Test
    fun testRecognizedSpeechQuerySanitization() {
        val rawResults = arrayListOf("   Interstellar 4K   ", "Interstellar")
        val selected = rawResults.firstOrNull()?.trim()

        assertEquals("Interstellar 4K", selected)
        assertTrue(!selected.isNullOrBlank())

        val emptyResults = arrayListOf("   ", "")
        val emptySelected = emptyResults.firstOrNull()?.trim()
        assertTrue(emptySelected.isNullOrEmpty())
    }
}
