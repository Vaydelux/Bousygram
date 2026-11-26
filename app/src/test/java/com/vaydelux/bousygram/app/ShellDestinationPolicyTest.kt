package com.vaydelux.bousygram.app

import com.vaydelux.bousygram.presentation.components.ShellDestination
import com.vaydelux.bousygram.presentation.navigation.AppScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ShellDestinationPolicyTest {
    @Test
    fun repeatedDockSelectionsDoNotGrowBackHistory() {
        val destinations = listOf(ShellDestination.Search, ShellDestination.Me,
            ShellDestination.Favorites, ShellDestination.About)
        for (destination in destinations) {
            val first = selectShellDestination(AppScreen.Home, destination)
            var current = first
            repeat(100) { current = selectShellDestination(current, destination) }
            assertSame(first, current)
        }
    }

    @Test
    fun aliasesDoNotNestSettingsOrLibrary() {
        val settings = selectShellDestination(AppScreen.Library, ShellDestination.Me)
        assertSame(settings, selectShellDestination(settings, ShellDestination.Settings))
        assertEquals(AppScreen.Library, (settings as AppScreen.Settings).returnTo)
        for (destination in listOf(ShellDestination.Library, ShellDestination.Sources, ShellDestination.Offline)) {
            assertSame(AppScreen.Library, selectShellDestination(settings, destination))
        }
    }

    @Test
    fun switchingTopLevelDestinationsDoesNotWrapTransientScreens() {
        val settings = selectShellDestination(AppScreen.Home, ShellDestination.Settings)
        val search = selectShellDestination(settings, ShellDestination.Search) as AppScreen.Search
        assertEquals(AppScreen.Home, search.returnTo)
        val favorites = selectShellDestination(search, ShellDestination.Favorites) as AppScreen.Favorites
        assertEquals(AppScreen.Home, favorites.returnTo)
    }

    @Test
    fun selectingSearchPreservesItsExistingScopeAndQuery() {
        val scoped = AppScreen.Search(123L, "Saved", AppScreen.Library, initialQuery = "sample")
        assertSame(scoped, selectShellDestination(scoped, ShellDestination.Search))
    }
}
