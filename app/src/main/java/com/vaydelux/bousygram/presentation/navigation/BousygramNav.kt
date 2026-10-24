package com.vaydelux.bousygram.presentation.navigation

import androidx.compose.runtime.saveable.Saver
import com.vaydelux.bousygram.presentation.media.PlaybackRequest

enum class SearchFocusTarget {
    SearchField,
    Grid
}

sealed class AppScreen {
    data object Home : AppScreen()
    data object Library : AppScreen()
    data class Settings(val returnTo: AppScreen = Home) : AppScreen()
    data class Favorites(val returnTo: AppScreen = Home) : AppScreen()
    data class About(val returnTo: AppScreen = Home) : AppScreen()
    data class Search(
        val inChatId: Long? = null,
        val inChatTitle: String = "",
        val returnTo: AppScreen = Home
    ) : AppScreen()
    data class Player(
        val request: PlaybackRequest,
        val returnTo: AppScreen
    ) : AppScreen()
}

data class SearchFocusState(
    val target: SearchFocusTarget = SearchFocusTarget.SearchField,
    val gridIndex: Int = 0
)

val SearchFocusStateSaver: Saver<SearchFocusState, List<Int>> = Saver(
    save = { state ->
        listOf(state.target.ordinal, state.gridIndex.coerceAtLeast(0))
    },
    restore = { saved ->
        val target = SearchFocusTarget.entries.getOrNull(saved.getOrElse(0) { 0 })
            ?: SearchFocusTarget.SearchField
        SearchFocusState(
            target = target,
            gridIndex = saved.getOrElse(1) { 0 }.coerceAtLeast(0)
        )
    }
)
