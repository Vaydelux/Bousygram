package com.vaydelux.bousygram.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vaydelux.bousygram.BuildConfig
import com.vaydelux.bousygram.domain.model.AuthStep
import com.vaydelux.bousygram.presentation.auth.AuthScreen
import com.vaydelux.bousygram.presentation.auth.vm.AuthViewModel
import com.vaydelux.bousygram.presentation.media.BrowseScreen
import com.vaydelux.bousygram.presentation.media.FavoritesScreen
import com.vaydelux.bousygram.presentation.media.PlaybackRequest
import com.vaydelux.bousygram.presentation.media.PlayerScreen
import com.vaydelux.bousygram.presentation.media.buildPlaybackRequest
import com.vaydelux.bousygram.presentation.media.playableVideos
import com.vaydelux.bousygram.presentation.navigation.AppScreen
import com.vaydelux.bousygram.presentation.navigation.SearchFocusState
import com.vaydelux.bousygram.presentation.navigation.SearchFocusStateSaver
import com.vaydelux.bousygram.presentation.preview.TvHybridPreviewHost
import com.vaydelux.bousygram.presentation.search.SearchViewModel
import com.vaydelux.bousygram.presentation.settings.SettingsScreen
import com.vaydelux.bousygram.presentation.search.TvVideoSearchScreen
import com.vaydelux.bousygram.presentation.theme.BousygramTheme

class MainActivity : ComponentActivity() {
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BousygramTheme {
                if (BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_UI_PREVIEW, false)) {
                    TvHybridPreviewHost()
                } else {
                    BousygramAppRoot(authViewModel = authViewModel)
                }
            }
        }
    }

    companion object {
        const val EXTRA_UI_PREVIEW = "ui_preview"
    }
}

@Composable
private fun BousygramAppRoot(authViewModel: AuthViewModel) {
    val authState by authViewModel.uiState.collectAsState()
    var appScreen by remember { mutableStateOf<AppScreen>(AppScreen.Home) }
    var searchFocus by rememberSaveable(stateSaver = SearchFocusStateSaver) {
        mutableStateOf(SearchFocusState())
    }

    BackHandler(enabled = authState.step == AuthStep.Authorized && appScreen !is AppScreen.Home) {
        appScreen = when (val screen = appScreen) {
            is AppScreen.Player -> screen.returnTo
            is AppScreen.Search -> screen.returnTo
            AppScreen.Library -> AppScreen.Home
            is AppScreen.Favorites -> screen.returnTo
            is AppScreen.Settings -> screen.returnTo
            else -> AppScreen.Home
        }
    }

    val openPlayer: (PlaybackRequest, AppScreen) -> Unit = { request, returnTo ->
        appScreen = AppScreen.Player(request, returnTo)
    }

    when {
        authState.step != AuthStep.Authorized -> AuthScreen(viewModel = authViewModel)
        else -> when (val screen = appScreen) {
            is AppScreen.Player -> PlayerScreen(
                request = screen.request,
                onExit = { appScreen = screen.returnTo }
            )
            is AppScreen.Search -> SearchRoute(
                screen = screen,
                searchFocus = searchFocus,
                onSearchFocusChange = { searchFocus = it },
                onPlay = { request, returnTo -> openPlayer(request, returnTo) },
                onStreamError = { message ->
                    Toast.makeText(BousygramApp.instance, message, Toast.LENGTH_LONG).show()
                }
            )
            AppScreen.Library -> BrowseScreen(
                onPlay = { request -> openPlayer(request, AppScreen.Library) },
                onOpenSearch = { chatId, chatTitle -> appScreen = AppScreen.Search(chatId, chatTitle, AppScreen.Library) },
                onOpenLibrary = { appScreen = AppScreen.Library },
                onOpenHome = { appScreen = AppScreen.Home },
                onOpenFavorites = { appScreen = AppScreen.Favorites(AppScreen.Library) },
                onOpenSettings = { appScreen = AppScreen.Settings(AppScreen.Library) },
                onStreamError = { message ->
                    Toast.makeText(BousygramApp.instance, message, Toast.LENGTH_LONG).show()
                },
                libraryMode = true
            )
            is AppScreen.Favorites -> FavoritesScreen(
                onBack = { appScreen = screen.returnTo },
                onOpenSearch = { appScreen = AppScreen.Search(returnTo = screen) },
                onPlay = { request -> openPlayer(request, screen) }
            )
            is AppScreen.Settings -> SettingsScreen(
                onBack = { appScreen = screen.returnTo }
            )
            AppScreen.Home -> BrowseScreen(
                onPlay = { request -> openPlayer(request, AppScreen.Home) },
                onOpenSearch = { chatId, chatTitle ->
                    appScreen = AppScreen.Search(chatId, chatTitle, AppScreen.Home)
                },
                onOpenLibrary = { appScreen = AppScreen.Library },
                onOpenHome = { appScreen = AppScreen.Home },
                onOpenSettings = {
                    appScreen = AppScreen.Settings(AppScreen.Home)
                },
                onOpenFavorites = { appScreen = AppScreen.Favorites(AppScreen.Home) },
                onStreamError = { message ->
                    Toast.makeText(BousygramApp.instance, message, Toast.LENGTH_LONG).show()
                }
            )
        }
    }
}

@Composable
private fun SearchRoute(
    screen: AppScreen.Search,
    searchFocus: SearchFocusState,
    onSearchFocusChange: (SearchFocusState) -> Unit,
    onPlay: (PlaybackRequest, AppScreen) -> Unit,
    onStreamError: (String) -> Unit,
    searchViewModel: SearchViewModel = viewModel()
) {
    TvVideoSearchScreen(
        viewModel = searchViewModel,
        inChatId = screen.inChatId,
        inChatTitle = screen.inChatTitle,
        restoreFocusTarget = searchFocus.target,
        restoreGridIndex = searchFocus.gridIndex,
        onFocusStateChanged = { target, index ->
            onSearchFocusChange(com.vaydelux.bousygram.presentation.navigation.SearchFocusState(target, index))
        },
        onPlayRequest = { item, queueItems, onResult ->
            searchViewModel.requestPlay(item, playableVideos(queueItems), onResult)
        },
        onPlaybackReady = { request ->
            onPlay(request, screen)
        },
        onStreamError = onStreamError
    )
}
