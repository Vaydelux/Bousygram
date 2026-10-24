package com.vaydelux.bousygram.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.vaydelux.bousygram.presentation.media.PlaybackRequest

/**
 * Single-top adaptive router managing screens, backstack, and one-shot playback resume requests.
 * Prevents recursive player relaunch loops by consuming resume signals once.
 */
class BousygramRouter(
    initialScreen: AppScreen = AppScreen.Home
) {
    var currentScreen by mutableStateOf(initialScreen)
        private set

    var resumeRequested by mutableStateOf(false)
        private set

    fun requestResume() {
        currentScreen = AppScreen.Home
        resumeRequested = true
    }

    fun consumeResume(): Boolean {
        val wasRequested = resumeRequested
        resumeRequested = false
        return wasRequested
    }

    fun navigateTo(screen: AppScreen) {
        if (currentScreen == screen) return
        currentScreen = screen
    }

    fun openPlayer(request: PlaybackRequest, returnTo: AppScreen = currentScreen) {
        currentScreen = AppScreen.Player(request = request, returnTo = returnTo)
    }

    fun popBack(): Boolean {
        val screen = currentScreen
        if (screen is AppScreen.Home) return false
        currentScreen = when (screen) {
            is AppScreen.Player -> screen.returnTo
            is AppScreen.Search -> screen.returnTo
            AppScreen.Library -> AppScreen.Home
            is AppScreen.Favorites -> screen.returnTo
            is AppScreen.Settings -> screen.returnTo
            is AppScreen.About -> screen.returnTo
            AppScreen.Home -> AppScreen.Home
        }
        return true
    }
}

@Composable
fun rememberBousygramRouter(initialScreen: AppScreen = AppScreen.Home): BousygramRouter {
    return remember { BousygramRouter(initialScreen) }
}
