package com.vaydelux.bousygram.app

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vaydelux.bousygram.BuildConfig
import com.vaydelux.bousygram.data.store.EnterprisePreferences
import com.vaydelux.bousygram.domain.model.AuthStep
import com.vaydelux.bousygram.presentation.auth.AuthScreen
import com.vaydelux.bousygram.presentation.auth.vm.AuthViewModel
import com.vaydelux.bousygram.presentation.media.BrowseScreen
import com.vaydelux.bousygram.presentation.media.FavoritesScreen
import com.vaydelux.bousygram.presentation.media.PlaybackRequest
import com.vaydelux.bousygram.presentation.media.PlayerScreen
import com.vaydelux.bousygram.presentation.media.playableVideos
import com.vaydelux.bousygram.presentation.components.BousygramAdaptiveShell
import com.vaydelux.bousygram.presentation.components.ShellDestination
import com.vaydelux.bousygram.presentation.navigation.AppScreen
import com.vaydelux.bousygram.presentation.navigation.SearchFocusState
import com.vaydelux.bousygram.presentation.navigation.SearchFocusStateSaver
import com.vaydelux.bousygram.presentation.navigation.unwrapNonPlayerScreen
import com.vaydelux.bousygram.presentation.preview.TvHybridPreviewHost
import com.vaydelux.bousygram.presentation.search.SearchViewModel
import com.vaydelux.bousygram.presentation.settings.SettingsScreen
import com.vaydelux.bousygram.presentation.search.TvVideoSearchScreen
import com.vaydelux.bousygram.presentation.about.AboutScreen
import com.vaydelux.bousygram.presentation.theme.BousygramTheme
import com.vaydelux.bousygram.presentation.theme.BousygramThemePreset

class MainActivity : ComponentActivity() {
    private val authViewModel: AuthViewModel by viewModels()
    var isPlayerActive by mutableStateOf(false)
    var isInPipMode by mutableStateOf(false)
    var currentPipAspectRatio by mutableStateOf(Rational(16, 9))
    var currentPipSourceRect: android.graphics.Rect? = null

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isPlayerActive) {
            enterPipMode(currentPipAspectRatio, currentPipSourceRect)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
    }

    fun enterPipMode(
        aspectRatio: Rational = currentPipAspectRatio,
        sourceRect: android.graphics.Rect? = currentPipSourceRect
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val clamped = clampAspectRatio(aspectRatio)
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(clamped)
                    .apply {
                        if (sourceRect != null && !sourceRect.isEmpty) {
                            setSourceRectHint(sourceRect)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            setAutoEnterEnabled(true)
                            setSeamlessResizeEnabled(true)
                        }
                    }
                    .build()
                enterPictureInPictureMode(params)
            } catch (_: Exception) {
            }
        }
    }

    fun updatePipAutoEnter(
        enabled: Boolean,
        aspectRatio: Rational = currentPipAspectRatio,
        sourceRect: android.graphics.Rect? = currentPipSourceRect
    ) {
        currentPipAspectRatio = aspectRatio
        if (sourceRect != null) {
            currentPipSourceRect = sourceRect
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val clamped = clampAspectRatio(aspectRatio)
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(clamped)
                    .setAutoEnterEnabled(enabled)
                    .setSeamlessResizeEnabled(true)
                    .apply {
                        val rect = sourceRect ?: currentPipSourceRect
                        if (rect != null && !rect.isEmpty) {
                            setSourceRectHint(rect)
                        }
                    }
                    .build()
                setPictureInPictureParams(params)
            } catch (_: Exception) {
            }
        }
    }

    private fun clampAspectRatio(rational: Rational): Rational {
        val floatVal = rational.toFloat()
        return when {
            floatVal < 0.41841f -> Rational(1000, 2390)
            floatVal > 2.39f -> Rational(2390, 1000)
            else -> rational
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var themePreset by remember {
                mutableStateOf(BousygramThemePreset.fromId(EnterprisePreferences.visualThemeId()))
            }
            BousygramTheme(preset = themePreset) {
                if (BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_UI_PREVIEW, false)) {
                    TvHybridPreviewHost()
                } else {
                    BousygramAppRoot(
                        authViewModel = authViewModel,
                        themePreset = themePreset,
                        onThemePresetChange = { preset ->
                            EnterprisePreferences.setVisualThemeId(preset.id)
                            themePreset = preset
                        }
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_UI_PREVIEW = "ui_preview"
    }
}

@Composable
private fun BousygramAppRoot(
    authViewModel: AuthViewModel,
    themePreset: BousygramThemePreset,
    onThemePresetChange: (BousygramThemePreset) -> Unit
) {
    val authState by authViewModel.uiState.collectAsState()
    var appScreen by remember { mutableStateOf<AppScreen>(AppScreen.Home) }
    var searchFocus by rememberSaveable(stateSaver = SearchFocusStateSaver) {
        mutableStateOf(SearchFocusState())
    }
    var resumeSignal by remember { mutableIntStateOf(0) }

    BackHandler(enabled = authState.step == AuthStep.Authorized && appScreen !is AppScreen.Home) {
        appScreen = when (val screen = appScreen) {
            is AppScreen.Player -> unwrapNonPlayerScreen(screen.returnTo)
            is AppScreen.Search -> screen.returnTo
            AppScreen.Library -> AppScreen.Home
            is AppScreen.Favorites -> screen.returnTo
            is AppScreen.Settings -> screen.returnTo
            is AppScreen.About -> screen.returnTo
            else -> AppScreen.Home
        }
    }

    when {
        authState.step != AuthStep.Authorized -> AuthScreen(viewModel = authViewModel)
        appScreen is AppScreen.Player -> {
            val screen = appScreen as AppScreen.Player
            val activity = LocalContext.current as? MainActivity
            DisposableEffect(Unit) {
                activity?.isPlayerActive = true
                activity?.updatePipAutoEnter(enabled = true)
                onDispose {
                    activity?.isPlayerActive = false
                    activity?.updatePipAutoEnter(enabled = false)
                }
            }
            PlayerScreen(
                request = screen.request,
                isInPipMode = activity?.isInPipMode ?: false,
                onExit = { appScreen = unwrapNonPlayerScreen(screen.returnTo) },
                onEnterPip = {
                    val act = activity
                    if (act != null) {
                        act.enterPipMode(act.currentPipAspectRatio, act.currentPipSourceRect)
                    }
                }
            )
        }
        else -> {
            val destination = when (appScreen) {
                AppScreen.Library -> ShellDestination.Library
                is AppScreen.Favorites -> ShellDestination.Favorites
                is AppScreen.About -> ShellDestination.About
                is AppScreen.Search -> ShellDestination.Search
                is AppScreen.Settings -> ShellDestination.Me
                else -> ShellDestination.Home
            }
            val lastPlayed = EnterprisePreferences.lastPlayed()
            val navFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
            BousygramAdaptiveShell(
                selected = destination,
                showResume = lastPlayed != null,
                onResume = {
                    appScreen = AppScreen.Home
                    resumeSignal += 1
                },
                onOpenSearch = {
                    appScreen = AppScreen.Search(returnTo = appScreen)
                },
                onOpenSettings = {
                    appScreen = AppScreen.Settings(appScreen)
                },
                navFocusRequester = navFocusRequester,
                onSelect = { target ->
                    appScreen = when (target) {
                        ShellDestination.Home -> AppScreen.Home
                        ShellDestination.Continue -> {
                            resumeSignal += 1
                            AppScreen.Home
                        }
                        ShellDestination.Favorites -> AppScreen.Favorites(appScreen)
                        ShellDestination.Library, ShellDestination.Sources, ShellDestination.Offline -> AppScreen.Library
                        ShellDestination.Search -> AppScreen.Search(returnTo = appScreen)
                        ShellDestination.Me, ShellDestination.Settings -> AppScreen.Settings(appScreen)
                        ShellDestination.About -> AppScreen.About(appScreen)
                    }
                }
            ) {
                AuthorizedContent(
                    appScreen = appScreen,
                    themePreset = themePreset,
                    onThemePresetChange = onThemePresetChange,
                    searchFocus = searchFocus,
                    onSearchFocusChange = { searchFocus = it },
                    onAppScreenChange = { appScreen = it },
                    resumeSignal = resumeSignal,
                    onResumeConsumed = { resumeSignal = 0 },
                    onBackToNavBar = { navFocusRequester.requestFocus() },
                    onLogout = { authViewModel.logout() }
                )
            }
        }
    }
}

@Composable
private fun AuthorizedContent(
    appScreen: AppScreen,
    themePreset: BousygramThemePreset,
    onThemePresetChange: (BousygramThemePreset) -> Unit,
    searchFocus: SearchFocusState,
    onSearchFocusChange: (SearchFocusState) -> Unit,
    onAppScreenChange: (AppScreen) -> Unit,
    resumeSignal: Int,
    onResumeConsumed: () -> Unit = {},
    onBackToNavBar: () -> Unit = {},
    onLogout: () -> Unit
) {
    val openPlayer: (PlaybackRequest, AppScreen) -> Unit = { request, returnTo ->
        onAppScreenChange(AppScreen.Player(request, unwrapNonPlayerScreen(returnTo)))
    }
    when (val screen = appScreen) {
        is AppScreen.Search -> SearchRoute(
            screen = screen,
            searchFocus = searchFocus,
            onSearchFocusChange = onSearchFocusChange,
            onPlay = { request, returnTo -> openPlayer(request, returnTo) },
            onStreamError = { message ->
                Toast.makeText(BousygramApp.instance, message, Toast.LENGTH_LONG).show()
            }
        )
        AppScreen.Library -> BrowseScreen(
            onPlay = { request -> openPlayer(request, AppScreen.Library) },
            onOpenSearch = { chatId, chatTitle ->
                onAppScreenChange(AppScreen.Search(chatId, chatTitle, AppScreen.Library))
            },
            onOpenLibrary = { onAppScreenChange(AppScreen.Library) },
            onOpenFavorites = { onAppScreenChange(AppScreen.Favorites(AppScreen.Library)) },
            onOpenSettings = { onAppScreenChange(AppScreen.Settings(AppScreen.Library)) },
            onStreamError = { message ->
                Toast.makeText(BousygramApp.instance, message, Toast.LENGTH_LONG).show()
            },
            libraryMode = true,
            onBackToNavBar = onBackToNavBar
        )
        is AppScreen.Favorites -> FavoritesScreen(
            onBack = { onAppScreenChange(screen.returnTo) },
            onOpenSearch = { onAppScreenChange(AppScreen.Search(returnTo = screen)) },
            onPlay = { request -> openPlayer(request, screen) }
        )
        is AppScreen.Settings -> SettingsScreen(
            onBack = { onAppScreenChange(screen.returnTo) },
            themePreset = themePreset,
            onThemePresetChange = onThemePresetChange,
            onLogout = onLogout,
            onOpenAbout = { onAppScreenChange(AppScreen.About(screen)) }
        )
        is AppScreen.About -> AboutScreen(
            onBack = { onAppScreenChange(screen.returnTo) }
        )
        else -> BrowseScreen(
            onPlay = { request -> openPlayer(request, AppScreen.Home) },
            onOpenSearch = { chatId, chatTitle ->
                onAppScreenChange(AppScreen.Search(chatId, chatTitle, AppScreen.Home))
            },
            onOpenLibrary = { onAppScreenChange(AppScreen.Library) },
            onOpenSettings = { onAppScreenChange(AppScreen.Settings(AppScreen.Home)) },
            onOpenFavorites = { onAppScreenChange(AppScreen.Favorites(AppScreen.Home)) },
            onStreamError = { message ->
                Toast.makeText(BousygramApp.instance, message, Toast.LENGTH_LONG).show()
            },
            resumeSignal = resumeSignal,
            onResumeConsumed = onResumeConsumed,
            onBackToNavBar = onBackToNavBar
        )
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
