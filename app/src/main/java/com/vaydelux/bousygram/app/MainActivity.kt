package com.vaydelux.bousygram.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import com.vaydelux.bousygram.presentation.media.canPageOlderEpisodes
import com.vaydelux.bousygram.presentation.media.isMatchingEpisodeSource
import com.vaydelux.bousygram.presentation.media.vm.MediaViewModel
import com.vaydelux.bousygram.presentation.components.BousygramAdaptiveShell
import com.vaydelux.bousygram.presentation.components.GlassBottomBar
import com.vaydelux.bousygram.presentation.components.ShellDestination
import com.vaydelux.bousygram.presentation.navigation.CloudFavoritesNavigation
import com.vaydelux.bousygram.presentation.navigation.AppScreen
import com.vaydelux.bousygram.presentation.navigation.SearchFocusState
import com.vaydelux.bousygram.presentation.navigation.SearchFocusStateSaver
import com.vaydelux.bousygram.presentation.navigation.unwrapNonPlayerScreen
import com.vaydelux.bousygram.presentation.preview.TvHybridPreviewHost
import com.vaydelux.bousygram.presentation.design.preview.StreamingDesignGallery
import com.vaydelux.bousygram.presentation.search.SearchViewModel
import com.vaydelux.bousygram.presentation.settings.SettingsScreen
import com.vaydelux.bousygram.presentation.search.TvVideoSearchScreen
import com.vaydelux.bousygram.presentation.about.AboutScreen
import com.vaydelux.bousygram.core.cast.CastReceiverManager
import com.vaydelux.bousygram.core.account.AccountSession
import com.vaydelux.bousygram.core.tdlib.server.LocalStreamingServer
import com.vaydelux.bousygram.presentation.theme.BousygramAppearanceController
import com.vaydelux.bousygram.presentation.theme.BousygramBackdropPattern
import com.vaydelux.bousygram.presentation.theme.BousygramDesignStyle
import com.vaydelux.bousygram.presentation.theme.BousygramTheme
import com.vaydelux.bousygram.presentation.theme.BousygramThemePreset
import com.vaydelux.bousygram.presentation.theme.rememberBousygramMotionPolicy
import com.vaydelux.bousygram.presentation.adaptive.DeviceProfile
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import com.vaydelux.bousygram.presentation.shell.BousygramMode
import com.vaydelux.bousygram.presentation.shell.BousygramModeSelection
import com.vaydelux.bousygram.presentation.extras.ExtrasCatalogScreen
import com.vaydelux.bousygram.presentation.shell.TelegramStartupLoadingScreen

class MainActivity : ComponentActivity() {
    // Activity may be reused for real and debug-preview launch intents. Keep the
    // route in observable state rather than reading a non-observable intent in Compose.
    private var previewRequested by mutableStateOf(false)
    private var designPreviewRequested by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        previewRequested = BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_UI_PREVIEW, false)
        designPreviewRequested = BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_DESIGN_PREVIEW, false)
        handleIncomingIntent(intent)
        setContent {
            var themePreset by remember {
                mutableStateOf(BousygramThemePreset.fromId(EnterprisePreferences.visualThemeId()))
            }
            var designStyle by remember {
                mutableStateOf(BousygramDesignStyle.fromId(EnterprisePreferences.designStyleId()))
            }
            var backdropPattern by remember {
                mutableStateOf(BousygramBackdropPattern.fromId(EnterprisePreferences.backdropPatternId()))
            }
            var reduceTransparency by remember {
                mutableStateOf(EnterprisePreferences.reduceTransparency())
            }
            val view = LocalView.current
            val isLightTheme = themePreset.isLightPreset
            SideEffect {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = isLightTheme
                controller.isAppearanceLightNavigationBars = isLightTheme
            }
            val appearanceController = remember(themePreset, designStyle, backdropPattern, reduceTransparency) {
                BousygramAppearanceController(
                    themePreset = themePreset,
                    designStyle = designStyle,
                    backdropPattern = backdropPattern,
                    reduceTransparency = reduceTransparency,
                    onThemePresetChange = { preset ->
                        EnterprisePreferences.setVisualThemeId(preset.id)
                        themePreset = preset
                    },
                    onDesignStyleChange = { style ->
                        EnterprisePreferences.setDesignStyleId(style.id)
                        designStyle = style
                    },
                    onBackdropPatternChange = { pattern ->
                        EnterprisePreferences.setBackdropPatternId(pattern.id)
                        backdropPattern = pattern
                    },
                    onReduceTransparencyChange = { enabled ->
                        EnterprisePreferences.setReduceTransparency(enabled)
                        reduceTransparency = enabled
                    }
                )
            }
            BousygramTheme(
                preset = themePreset,
                designStyle = designStyle,
                backdropPattern = backdropPattern,
                reduceTransparency = reduceTransparency,
                appearanceController = appearanceController
            ) {
                if (designPreviewRequested) {
                    StreamingDesignGallery(onExit = { designPreviewRequested = false })
                } else if (previewRequested) {
                    TvHybridPreviewHost()
                } else {
                    BousygramAppRoot(
                        themePreset = themePreset,
                        onThemePresetChange = appearanceController.onThemePresetChange,
                        designStyle = designStyle,
                        onDesignStyleChange = appearanceController.onDesignStyleChange,
                        backdropPattern = backdropPattern,
                        onBackdropPatternChange = appearanceController.onBackdropPatternChange
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        previewRequested = BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_UI_PREVIEW, false)
        designPreviewRequested = BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_DESIGN_PREVIEW, false)
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
        const val EXTRA_DESIGN_PREVIEW = "design_preview"
    }
}

@Composable
private fun BousygramAppRoot(
    themePreset: BousygramThemePreset,
    onThemePresetChange: (BousygramThemePreset) -> Unit,
    designStyle: BousygramDesignStyle,
    onDesignStyleChange: (BousygramDesignStyle) -> Unit,
    backdropPattern: BousygramBackdropPattern,
    onBackdropPatternChange: (BousygramBackdropPattern) -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        com.vaydelux.bousygram.data.repository.FavoritesRepository.errors.collect {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        }
    }
    // Mode is shell UI state only: it does not create an independent Telegram session.
    var modeName by rememberSaveable { mutableStateOf(BousygramMode.SELECT.name) }
    val mode = BousygramMode.entries.firstOrNull { it.name == modeName } ?: BousygramMode.SELECT
    var appScreen by remember { mutableStateOf<AppScreen>(AppScreen.Home) }
    var searchFocus by rememberSaveable(stateSaver = SearchFocusStateSaver) {
        mutableStateOf(SearchFocusState())
    }
    var resumeSignal by remember { mutableIntStateOf(0) }
    val favoritesState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()

    // Observe incoming Cast requests before selecting a mode. A genuine Cast
    // target must initialize Telegram, even if Extras or the chooser is visible.
    val pendingCast by CastReceiverManager.pendingCastRequest.collectAsState()
    val handoffInbox = com.vaydelux.bousygram.core.cast.TelegramHandoffNavigation.inbox
    val pendingHandoff by handoffInbox.state.collectAsState()
    LaunchedEffect(pendingCast, pendingHandoff) {
        val handoff = pendingHandoff
        val available = handoff != null && handoffInbox.isAvailable(handoff)
        if (handoff != null && !available) handoffInbox.discard(handoff)
        val requested = TelegramRuntimePolicy.routeForPendingCast(mode, pendingCast != null || available)
        if (requested != mode) modeName = requested.name
    }

    val application = context.applicationContext as BousygramApp
    var telegramReady by remember { mutableStateOf(application.telegramRuntimeStarted) }
    var telegramStartupError by remember { mutableStateOf<String?>(null) }
    var telegramStartupAttempt by remember { mutableIntStateOf(0) }

    // Dispatch the expensive native/server setup away from Compose's UI thread.
    // Extras and UI preview never invoke this path.
    LaunchedEffect(mode, telegramStartupAttempt) {
        if (!TelegramRuntimePolicy.needsTelegramRuntime(mode) || telegramReady) return@LaunchedEffect
        telegramStartupError = null
        try {
            withContext(Dispatchers.IO) { application.ensureTelegramRuntimeStarted() }
            telegramReady = true
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            telegramStartupError = error.message ?: "Telegram Media could not initialize."
        }
    }

    // Extras has no Telegram ViewModel, TDLib client, HTTP loopback or Netty proxy.
    // Process-wide account invalidation is still installed by BousygramApp.onCreate.
    if (mode == BousygramMode.SELECT) {
        BousygramModeSelection(
            onTelegram = { modeName = BousygramMode.TELEGRAM.name },
            onExtras = { modeName = BousygramMode.EXTRAS_MAINTENANCE.name },
            onOpenCastLab = if (BuildConfig.DEBUG) ({
                // The debug-only receiver is private to this application. Never
                // register a second launcher tile or accept external lab intents.
                val lab = Intent().setClassName(context.packageName,
                    "com.vaydelux.bousygram.presentation.debug.RollingLanLabActivity")
                try {
                    context.startActivity(lab)
                } catch (_: android.content.ActivityNotFoundException) {
                    Toast.makeText(context, "Casting preview is unavailable in this build.", Toast.LENGTH_SHORT).show()
                }
            }) else null,
            themePreset = themePreset,
            designStyle = designStyle,
            backdropPattern = backdropPattern,
            onThemePresetChange = onThemePresetChange,
            onDesignStyleChange = onDesignStyleChange,
            onBackdropPatternChange = onBackdropPatternChange
        )
        return
    }
    if (mode == BousygramMode.EXTRAS_MAINTENANCE) {
        ExtrasCatalogScreen(onExit = { modeName = BousygramMode.SELECT.name })
        return
    }
    if (!telegramReady) {
        // Physical remote Back must return to mode selection, not finish the Activity.
        BackHandler {
            CastReceiverManager.clearPendingRequest()
            handoffInbox.clear()
            modeName = BousygramMode.SELECT.name
        }
        com.vaydelux.bousygram.presentation.components.PremiumGridBackdrop {
            Box(
                Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                com.vaydelux.bousygram.presentation.components.EnterprisePanel {
                    Column(
                        modifier = Modifier.padding(horizontal = 30.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        com.vaydelux.bousygram.presentation.components.BousygramBrandMark(
                            size = 64.dp,
                            squircleFrame = true
                        )
                        if (telegramStartupError != null) {
                            Text(
                                telegramStartupError ?: "Unable to start Telegram Media",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(onClick = { telegramStartupAttempt += 1 }) { Text("Retry Telegram startup") }
                        } else {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Text(
                                "Starting Telegram Media…",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        OutlinedButton(onClick = {
                            CastReceiverManager.clearPendingRequest()
                            handoffInbox.clear()
                            modeName = BousygramMode.SELECT.name
                        }) { Text("Back to modes") }
                    }
                }
            }
        }
        return
    }

    // Created only after Telegram runtime is ready; Activity-scoped VM survives
    // navigation between Telegram and Extras without recreating an auth client.
    val authViewModel: AuthViewModel = viewModel()
    val authState by authViewModel.uiState.collectAsState()
    LaunchedEffect(Unit) {
        CloudFavoritesNavigation.openRequests.collect {
            if (AccountSession.current() != null) {
                appScreen = AppScreen.Favorites(unwrapNonPlayerScreen(appScreen))
            }
        }
    }

    // Reset navigation on auth loss. Do not discard Cast during the initial
    // loading state: saved Telegram authorization may still be restoring.
    LaunchedEffect(authState.step) {
        if (authState.step != AuthStep.Authorized) {
            appScreen = AppScreen.Home
            searchFocus = SearchFocusState()
            resumeSignal = 0
            if (authState.step != AuthStep.Loading) {
                CastReceiverManager.clearPendingRequest()
                handoffInbox.clear()
            }
        }
    }

    LaunchedEffect(pendingHandoff, authState.step) {
        val entry = pendingHandoff ?: return@LaunchedEffect
        if (authState.step == AuthStep.Loading) return@LaunchedEffect
        if (authState.step != AuthStep.Authorized || !handoffInbox.isAvailable(entry)) {
            handoffInbox.discard(entry)
            return@LaunchedEffect
        }
        val request = entry.media.request
        val localId = try {
            withContext(Dispatchers.IO) {
                com.vaydelux.bousygram.core.cast.TelegramHandoffAuthorization.resolve(entry.owner, request)
            }
        } catch (cancel: CancellationException) { throw cancel }
          catch (_: Exception) { null }
        if (localId == null || localId != entry.media.localFileId) {
            handoffInbox.discard(entry)
            return@LaunchedEffect
        }
        if (handoffInbox.take(entry) == null) return@LaunchedEffect
        AccountSession.runIfCurrent(entry.owner) {
            val payload = com.vaydelux.bousygram.core.cast.CastMediaPayload(
                request.chatId, request.messageId, localId, "Telegram video")
            val playback = CastReceiverManager.toPlaybackRequest(payload, null).copy(
                initialHandoffPositionMs = request.positionMs, initialHandoffPlay = request.playRequested,
                handoffOwner = entry.owner)
            appScreen = AppScreen.Player(playback, returnTo = AppScreen.Home)
            modeName = BousygramMode.TELEGRAM.name
        }
    }

    LaunchedEffect(pendingCast, authState.step) {
        val payload = pendingCast ?: return@LaunchedEffect
        if (authState.step == AuthStep.Loading) return@LaunchedEffect
        val owner = AccountSession.current()
        if (owner == null || authState.step != AuthStep.Authorized ||
            !CastReceiverManager.isAuthorizedTelegramMedia(payload) || !AccountSession.isCurrent(owner)
        ) {
            CastReceiverManager.clearPendingRequest()
            android.widget.Toast.makeText(
                context, "This Cast link is not authorized for the current Telegram session",
                android.widget.Toast.LENGTH_LONG
            ).show()
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

    // Auth is a child of Telegram Media mode. Back from a login/connection
    // screen must return to mode choice, not unexpectedly finish the app.
    BackHandler(enabled = authState.step != AuthStep.Authorized) {
        handoffInbox.clear()
        modeName = BousygramMode.SELECT.name
    }

    // From the Telegram Home root, Back returns to mode choice instead of
    // terminating the Activity. Inner folder/player BackHandlers take priority.
    BackHandler(enabled = authState.step == AuthStep.Authorized && appScreen == AppScreen.Home) {
        modeName = BousygramMode.SELECT.name
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
        authState.step == AuthStep.Loading -> TelegramStartupLoadingScreen(authState.connectionStatus)
        authState.step != AuthStep.Authorized -> AuthScreen(viewModel = authViewModel)
        appScreen is AppScreen.Player -> {
            val screen = appScreen as AppScreen.Player
            // The same Activity-scoped catalog VM used by BrowseScreen survives
            // navigation to the player. Never page an unrelated search/Cast source.
            val mediaViewModel: MediaViewModel = viewModel()
            val source by mediaViewModel.uiState.collectAsState()
            val matchesCurrentSource = isMatchingEpisodeSource(screen.request, source)
            val canPageSource = canPageOlderEpisodes(screen.request, source)
            PlayerScreen(
                request = screen.request,
                onExit = { appScreen = unwrapNonPlayerScreen(screen.returnTo) },
                episodeCatalog = if (matchesCurrentSource) source.items else emptyList(),
                isForumEpisodeSource = source.videoChats.any {
                    it.chatId == screen.request.chatId && it.isForum
                },
                canLoadOlderEpisodes = canPageSource &&
                    !source.isLoadingMore,
                isLoadingOlderEpisodes = matchesCurrentSource &&
                    source.isLoadingMore,
                onLoadOlderEpisodes = if (canPageSource) {
                    { mediaViewModel.loadMoreIfNeeded() }
                } else null
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
            val adaptive = rememberAdaptiveMetrics()
            // Home/Library own the compact explorer. Phone subroutes should use
            // the same single-header surface, not a second tablet navigation
            // system with a landscape side rail and a redundant top bar.
            val useRouteHeader = appScreen == AppScreen.Home ||
                appScreen == AppScreen.Library ||
                (!adaptive.isTv && adaptive.profile != DeviceProfile.Tablet)
            if (useRouteHeader) {
                val showPhoneFloatingDock = adaptive.profile == DeviceProfile.PhonePortrait &&
                    !adaptive.isShortWindow
                if (showPhoneFloatingDock) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .statusBarsPadding()
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .consumeWindowInsets(WindowInsets.navigationBars)
                        ) {
                            AuthorizedContent(
                                favoritesState = favoritesState,
                                appScreen = appScreen,
                                themePreset = themePreset,
                                onThemePresetChange = onThemePresetChange,
                                designStyle = designStyle,
                                onDesignStyleChange = onDesignStyleChange,
                                backdropPattern = backdropPattern,
                                onBackdropPatternChange = onBackdropPatternChange,
                                searchFocus = searchFocus,
                                onSearchFocusChange = { searchFocus = it },
                                onAppScreenChange = { appScreen = it },
                                resumeSignal = resumeSignal,
                                onResumeConsumed = { resumeSignal = 0 },
                                onBackToNavBar = {},
                                onLogout = { authViewModel.logout() }
                            )
                        }
                        GlassBottomBar(
                            selected = destination,
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
                            },
                            modifier = Modifier.navigationBarsPadding()
                        )
                    }
                } else {
                    // Dedicated, compact explorer owns Home navigation on TV and short landscape.
                    AuthorizedContent(
                        favoritesState = favoritesState,
                        appScreen = appScreen,
                        themePreset = themePreset,
                        onThemePresetChange = onThemePresetChange,
                        designStyle = designStyle,
                        onDesignStyleChange = onDesignStyleChange,
                        backdropPattern = backdropPattern,
                        onBackdropPatternChange = onBackdropPatternChange,
                        searchFocus = searchFocus,
                        onSearchFocusChange = { searchFocus = it },
                        onAppScreenChange = { appScreen = it },
                        resumeSignal = resumeSignal,
                        onResumeConsumed = { resumeSignal = 0 },
                        onBackToNavBar = {},
                        onLogout = { authViewModel.logout() }
                    )
                }
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
                    favoritesState = favoritesState,
                    appScreen = appScreen,
                    themePreset = themePreset,
                    onThemePresetChange = onThemePresetChange,
                    designStyle = designStyle,
                    onDesignStyleChange = onDesignStyleChange,
                    backdropPattern = backdropPattern,
                    onBackdropPatternChange = onBackdropPatternChange,
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
    favoritesState: androidx.compose.runtime.saveable.SaveableStateHolder,
    appScreen: AppScreen,
    themePreset: BousygramThemePreset,
    onThemePresetChange: (BousygramThemePreset) -> Unit,
    designStyle: BousygramDesignStyle,
    onDesignStyleChange: (BousygramDesignStyle) -> Unit,
    backdropPattern: BousygramBackdropPattern,
    onBackdropPatternChange: (BousygramBackdropPattern) -> Unit,
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
    var entered by remember(appScreen) { mutableStateOf(false) }
    LaunchedEffect(appScreen) { entered = true }
    // Match all other navigation/focus motion. Android reduced-motion setting
    // disables this fade; low-RAM devices receive a short, bounded transition.
    val motion = rememberBousygramMotionPolicy()
    val transitionMs = motion.duration(
        com.vaydelux.bousygram.presentation.theme.BousygramUiTokens.screenDurationMs
    )
    val opacity by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (entered || transitionMs == 0) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(transitionMs),
        label = "screenArrival"
    )
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().graphicsLayer { alpha = opacity }) {
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
            onOpenHome = { onAppScreenChange(AppScreen.Home) },
            onBackToNavBar = onBackToNavBar
        )
        is AppScreen.Favorites -> favoritesState.SaveableStateProvider(
            "favorites-${com.vaydelux.bousygram.core.account.AccountSession.current()?.accountId}"
        ) {
            FavoritesScreen(
                onBack = { onAppScreenChange(screen.returnTo) },
                onOpenSearch = { onAppScreenChange(AppScreen.Search(returnTo = screen)) },
                onPlay = { request -> openPlayer(request, screen) }
            )
        }
        is AppScreen.Settings -> SettingsScreen(
            onBack = { onAppScreenChange(screen.returnTo) },
            themePreset = themePreset,
            onThemePresetChange = onThemePresetChange,
            designStyle = designStyle,
            onDesignStyleChange = onDesignStyleChange,
            backdropPattern = backdropPattern,
            onBackdropPatternChange = onBackdropPatternChange,
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
