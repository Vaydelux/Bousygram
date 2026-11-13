package com.vaydelux.bousygram.ui.adaptive

/**
 * Presentation-only search state. The existing search service remains authoritative;
 * map its events to these states rather than creating another search implementation.
 */
sealed interface SearchPresentation {
    data object Idle : SearchPresentation
    data class Searching(val query: String) : SearchPresentation
    data class Results(val query: String, val resultCount: Int) : SearchPresentation {
        init { require(resultCount >= 0) }
    }
    data class Failed(val query: String, val reason: String) : SearchPresentation
}

enum class SearchPanel { PROMPT, PROGRESS, RESULTS, NO_MATCHES, ERROR }

fun SearchPresentation.panel(): SearchPanel = when (this) {
    SearchPresentation.Idle -> SearchPanel.PROMPT
    is SearchPresentation.Searching -> SearchPanel.PROGRESS
    is SearchPresentation.Results -> if (resultCount == 0) SearchPanel.NO_MATCHES else SearchPanel.RESULTS
    is SearchPresentation.Failed -> SearchPanel.ERROR
}

/** Search history deletion must never be conflated with clearing the text input. */
enum class SearchAction { SUBMIT, CLEAR_QUERY, CLEAR_HISTORY, RETRY }

data class SearchLayoutPolicy(
    val showVoiceAsTrailingAction: Boolean,
    val showExpandedHistoryByDefault: Boolean,
    val keepResultsBelowInput: Boolean,
    val useCompactActionRow: Boolean,
)

fun AdaptiveUiPolicy.searchLayout(): SearchLayoutPolicy = SearchLayoutPolicy(
    showVoiceAsTrailingAction = true,
    showExpandedHistoryByDefault = searchHistoryInitiallyExpanded,
    keepResultsBelowInput = true,
    useCompactActionRow = compactHeader || windowClass == WindowClass.COMPACT,
)
