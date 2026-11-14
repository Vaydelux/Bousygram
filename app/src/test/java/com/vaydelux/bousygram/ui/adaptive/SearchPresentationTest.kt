package com.vaydelux.bousygram.ui.adaptive

import org.junit.Assert.*
import org.junit.Test

class SearchPresentationTest {
    @Test fun noMatchesOnlyAfterCompletedSearch() {
        assertEquals(SearchPanel.PROGRESS, SearchPresentation.Searching("Goblin").panel())
        assertEquals(SearchPanel.NO_MATCHES, SearchPresentation.Results("Goblin", 0).panel())
        assertEquals(SearchPanel.RESULTS, SearchPresentation.Results("Goblin", 17).panel())
    }

    @Test fun errorsDoNotAppearAsEmptyResults() {
        assertEquals(SearchPanel.ERROR, SearchPresentation.Failed("Goblin", "offline").panel())
    }

    @Test fun compactLayoutPreservesResultsPosition() {
        val policy = AdaptiveUi.resolve(393, 873, false).searchLayout()
        assertTrue(policy.keepResultsBelowInput)
        assertTrue(policy.useCompactActionRow)
        assertFalse(policy.showExpandedHistoryByDefault)
    }

    @Test fun clearingInputAndHistoryAreDistinctActions() {
        assertNotEquals(SearchAction.CLEAR_QUERY, SearchAction.CLEAR_HISTORY)
    }
}
