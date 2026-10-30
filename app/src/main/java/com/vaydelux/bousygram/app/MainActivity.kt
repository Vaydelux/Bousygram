package com.vaydelux.bousygram.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.vaydelux.bousygram.core.cast.CastReceiverManager
import com.vaydelux.bousygram.core.account.AccountSession
import com.vaydelux.bousygram.core.tdlib.server.LocalStreamingServer
import com.vaydelux.bousygram.presentation.theme.BousygramTheme
import com.vaydelux.bousygram.presentation.theme.BousygramThemePreset
import com.vaydelux.bousygram.presentation.shell.BousygramMode
import com.vaydelux.bousygram.presentation.shell.BousygramModeSelection
import com.vaydelux.bousygram.presentation.shell.ExtrasMaintenanceScreen

class MainActivity : ComponentActivity() {
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent)
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val payload = CastReceiverManager.parseIntent(intent)
        if (payload != null) {
            CastReceiverManager.emitCastRequest(payload)
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
    val context = LocalContext.current
    val authState by authViewModel.uiState.collectAsState()
    // Mode is shell UI state only: it does not create an independent Telegram session.
    var modeName by rememberSaveable { mutableStateOf(BousygramMode.SELECT.name) }
    val mode = BousygramMode.entries.firstOrNull { it.name == modeName } ?: BousygramMode.SELECT
    var appScreen by remember { mutableStateOf<AppScreen>(AppScreen.Home) }
    var searchFocus by rememberSaveable(stateSaver = SearchFocusStateSaver) {
        mutableStateOf(SearchFocusState())
    }
    var resumeSignal by remember { mutableIntStateOf(0) }

    // The previous account's player request must never survive logout and re-login.
    // The navigation state is deliberately session-local, not persisted across identities.
    LaunchedEffect(authState.step) {
        if (authState.step != AuthStep.Authorized) {
            appScreen = AppScreen.Home
            searchFocus = SearchFocusState()
            resumeSignal = 0
            CastReceiverManager.clearPendingRequest()
        }
    }

    val pendingCast by CastReceiverManager.pendingCastRequest.collectAsState()
    LaunchedEffect(pendingCast, authState.step) {
        val payload = pendingCast ?: return@LaunchedEffect
        val owner = AccountSession.current()
        if (owner == null || authState.step != AuthStep.Authorized ||
            !CastReceiverManager.isAuthorizedTelegramMedia(payload) || !AccountSession.isCurrent(owner)) {
            CastReceiverManager.clearPendingRequest()
            android.widget.Toast.makeText(context, "This Cast link is not authorized for the current Telegram session", android.widget.Toast.LENGTH_LONG).show()
            return@LaunchedEffect
        }
        val preparation = LocalStreamingServer.prepareStream(
            fileId = payload.fileId,
            declaredSize = 0L,
            title = payload.title,
            chatId = payload.chatId,
            messageId = payload.messageId
        )
        if (!AccountSession.isCurrent(owner)) {
            CastReceiverManager.clearPendingRequest()
            return@LaunchedEffect
        }
        val req = CastReceiverManager.toPlaybackRequest(payload, preparation?.url)
        modeName = BousygramMode.TELEGRAM.name
        appScreen = AppScreen.Player(req, returnTo = AppScreen.Home)
        CastReceiverManager.clearPendingRequest()
    }

    // The Extras screen is a dead-end maintenance route: no Telegram feature UI is mounted.
    // The application process still owns existing TDLib infrastructure; separating that
    // initialization is a future, explicitly reviewed lifecycle change.
    if (mode == BousygramMode.SELECT) {
        BousygramModeSelection(
            onTelegram = { modeName = BousygramMode.TELEGRAM.name },
            onExtras = { modeName = BousygramMode.EXTRAS_MAINTENANCE.name }
        )
        return
    }
    if (mode == BousygramMode.EXTRAS_MAINTENANCE) {
        ExtrasMaintenanceScreen(onBack = { modeName = BousygramMode.SELECT.name })
        return
    }

    BackHandler(enabled = authState.step == AuthStep.Authorized && appScreen !is AppScreen.Home && appScreen !is AppScreen.Player) {
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
            PlayerScreen(
                request = screen.request,
                onExit = { appScreen = unwrapNonPlayerScreen(screen.returnTo) }
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
            if (appScreen == AppScreen.Home) {
                // Dedicated, compact explorer owns Home navigation. Do not wrap
                // a second oversized TV sidebar around the three-tab grid.
                AuthorizedContent(
                    appScreen = appScreen,
                    themePreset = themePreset,
                    onThemePresetChange = onThemePresetChange,
                    searchFocus = searchFocus,
                    onSearchFocusChange = { searchFocus = it },
                    onAppScreenChange = { appScreen = it },
                    resumeSignal = resumeSignal,
                    onResumeConsumed = { resumeSignal = 0 },
                    onBackToNavBar = {},
                    onLogout = { authViewModel.logout() }
                )
            } else {
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
                onOpenCast = {
                    android.widget.Toast.makeText(
                        context,
                        "Cast Receiver Ready (bousygram://cast/load)",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
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
            onOpenSearchWithQuery = { chatId, chatTitle, query ->
                onAppScreenChange(AppScreen.Search(chatId, chatTitle, AppScreen.Library, initialQuery = query))
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
            onOpenSearchWithQuery = { chatId, chatTitle, query ->
                onAppScreenChange(AppScreen.Search(chatId, chatTitle, AppScreen.Home, initialQuery = query))
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
        initialQuery = screen.initialQuery,
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
