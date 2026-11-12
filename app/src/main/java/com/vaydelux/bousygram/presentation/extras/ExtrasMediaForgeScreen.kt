package com.vaydelux.bousygram.presentation.extras

import android.view.KeyEvent as AndroidKeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.ui.PlayerView
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vaydelux.mediaforge.core.CaptionCandidate
import com.vaydelux.mediaforge.core.InspectedMedia
import com.vaydelux.mediaforge.core.MediaForgeInspectionFailure
import com.vaydelux.mediaforge.core.MediaForgeSdk
import com.vaydelux.mediaforge.core.MediaForgeSessionInspection
import com.vaydelux.mediaforge.core.MediaForgeSessionRequest
import com.vaydelux.mediaforge.players.MediaForgeMedia3
import com.vaydelux.mediaforge.players.MediaForgeMedia3Controls
import com.vaydelux.mediaforge.players.MediaForgeGuardedProgressiveSource
import com.vaydelux.mediaforge.webbridge.MediaForgeWebBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A title-specific embed chosen explicitly by the Extras user. This is not a
 * TMDB stream and is never used by Telegram Media.
 */
internal data class ExtrasEmbedRequest(
    val url: String,
    val title: String,
    val tmdbId: Int = 0,
    val season: Int? = null,
    val episode: Int? = null,
    val server: ExtrasEmbedServer = ExtrasEmbedServer.CINESRC,
    val anilistId: Int = 0,
    val animeServer: ExtrasAnimeEmbedServer = ExtrasAnimeEmbedServer.EMBED_HEAVEN,
    val legacyEmbedHeaven: Boolean = false
) {
    init {
        require((tmdbId > 0) != (anilistId > 0)) {
            "Expected exactly one TMDB or AniList identity"
        }
        if (anilistId > 0) {
            require((season ?: 1) > 0 && (episode ?: 0) > 0) {
                "Anime requires a positive episode number"
            }
        }
        if (legacyEmbedHeaven) {
            require(tmdbId > 0 && season == null && episode == null &&
                server == ExtrasEmbedServer.EMBED_HEAVEN &&
                url == ExtrasEmbedHeavenRoutes.legacyMovie(tmdbId)) {
                "Legacy EmbedHeaven only supports TMDB movies"
            }
        }
    }

    fun forServer(next: ExtrasEmbedServer): ExtrasEmbedRequest {
        require(tmdbId > 0) { "TMDB server selection requires a TMDB title" }
        return copy(url = next.urlFor(tmdbId, season, episode), server = next, legacyEmbedHeaven = false)
    }

    fun forServer(next: ExtrasAnimeEmbedServer): ExtrasEmbedRequest {
        require(anilistId > 0) { "Anime server selection requires an AniList title" }
        return copy(url = next.urlFor(anilistId, season ?: 1, episode ?: 1), animeServer = next)
    }

    fun forLegacyEmbedHeaven(): ExtrasEmbedRequest {
        require(tmdbId > 0 && season == null && episode == null) {
            "Legacy EmbedHeaven route supports movies only"
        }
        return copy(
            url = ExtrasEmbedHeavenRoutes.legacyMovie(tmdbId),
            server = ExtrasEmbedServer.EMBED_HEAVEN,
            legacyEmbedHeaven = true
        )
    }

    companion object {
        fun movie(tmdbId: Int, title: String): ExtrasEmbedRequest =
            ExtrasEmbedRequest(ExtrasEmbedServer.CINESRC.urlFor(tmdbId), title, tmdbId)

        fun episode(tmdbId: Int, season: Int, episode: Int, title: String): ExtrasEmbedRequest =
            ExtrasEmbedRequest(
                ExtrasEmbedServer.CINESRC.urlFor(tmdbId, season, episode),
                "$title · S$season E$episode", tmdbId, season, episode
            )

        fun anime(anilistId: Int, episode: Int, title: String, season: Int = 1): ExtrasEmbedRequest {
            // The default provider only identifies an episode, not season 2+.
            // Prefer a provider with an explicit season route rather than
            // silently loading the wrong season or throwing in Compose.
            val source = if (season == 1) ExtrasAnimeEmbedServer.EMBED_HEAVEN
                else ExtrasAnimeEmbedServer.FILMU
            return ExtrasEmbedRequest(
                url = source.urlFor(anilistId, season, episode),
                title = if (season == 1) "$title · E$episode" else "$title · S$season E$episode",
                anilistId = anilistId, season = season, episode = episode,
                animeServer = source
            )
        }
    }
}

/**
 * Opt-in, isolated WebView playback. Only visible resource URLs are collected.
 * The user decides whether to continue in WebView or launch native playback.
 */
@Composable
internal fun ExtrasMediaForgeScreen(
    request: ExtrasEmbedRequest,
    onBack: () -> Unit,
    isAndroidTv: Boolean
) {
    var selectedServer by remember(request.tmdbId, request.anilistId, request.season, request.episode) {
        mutableStateOf(request.server)
    }
    var selectedAnimeServer by remember(request.tmdbId, request.anilistId, request.season, request.episode) {
        mutableStateOf(request.animeServer)
    }
    var useLegacyEmbedHeaven by remember(request.tmdbId, request.season, request.episode) {
        mutableStateOf(request.legacyEmbedHeaven)
    }
    // Mirror movie/TV playback: enter the shared player immediately. A native
    // chooser remains available for manual recovery when an external anime
    // provider fails (including silent/blank upstream pages).
    var animeServerConfirmed by remember(request.anilistId, request.season, request.episode) {
        mutableStateOf(true)
    }
    val selectedRequest = remember(request, selectedServer, selectedAnimeServer, useLegacyEmbedHeaven) {
        when {
            request.anilistId > 0 -> request.forServer(selectedAnimeServer)
            selectedServer == ExtrasEmbedServer.EMBED_HEAVEN &&
                request.season == null && useLegacyEmbedHeaven -> request.forLegacyEmbedHeaven()
            else -> request.forServer(selectedServer)
        }
    }
    // Each provider owns a distinct untrusted browsing session. Disposing the
    // keyed subtree destroys the old WebView, native engine, detected URLs,
    // pending inspections, and TV pointer before a replacement can be loaded.
    if (request.anilistId > 0 && !animeServerConfirmed) {
        ExtrasAnimeSourceChooser(
            request = request,
            selected = selectedAnimeServer,
            isTv = isAndroidTv,
            onSelect = { selectedAnimeServer = it },
            onOpen = { animeServerConfirmed = true },
            onBack = onBack
        )
    } else {
        key(selectedRequest.url) {
            ExtrasMediaForgePlayerScreen(
                request = selectedRequest,
                onBack = onBack,
                isAndroidTv = isAndroidTv,
                selectedServer = selectedServer,
                onServerSelected = { selectedServer = it; useLegacyEmbedHeaven = false },
                onLegacyEmbedHeavenToggle = { useLegacyEmbedHeaven = it },
                selectedAnimeServer = selectedAnimeServer,
                onAnimeServerSelected = { selectedAnimeServer = it },
                onChooseAnimeServer = { animeServerConfirmed = false }
            )
        }
    }
}

@Composable
private fun ExtrasMediaForgePlayerScreen(
    request: ExtrasEmbedRequest,
    onBack: () -> Unit,
    isAndroidTv: Boolean,
    selectedServer: ExtrasEmbedServer,
    onServerSelected: (ExtrasEmbedServer) -> Unit,
    onLegacyEmbedHeavenToggle: (Boolean) -> Unit,
    selectedAnimeServer: ExtrasAnimeEmbedServer,
    onAnimeServerSelected: (ExtrasAnimeEmbedServer) -> Unit,
    onChooseAnimeServer: () -> Unit
) {
    // One SDK session owns each user's selected Extras embed.
    val sdk = remember { MediaForgeSdk() }
    val session = remember(request.url) {
        sdk.openSession(MediaForgeSessionRequest(embedUrl = request.url))
    }
    val scope = rememberCoroutineScope()
    var snapshot by remember(request.url) { mutableStateOf(session.snapshot()) }
    var webView by remember(request.url) { mutableStateOf<WebView?>(null) }
    var cursorEnabled by remember(request.url) { mutableStateOf(false) }
    var immersiveWeb by remember(request.url) { mutableStateOf(false) }
    // A provider may need third-party cookies or scripted autoplay to render its
    // nested player. Neither is granted until the user explicitly opts in.
    var webCompatibilityMode by remember(request.url) { mutableStateOf(false) }
    var htmlFullscreenView by remember(request.url) { mutableStateOf<View?>(null) }
    // The previous bridge is permanently closed when native playback disposes WebView.
    // A new generation must create a new bridge when the user returns to WebView.
    var webGeneration by remember(request.url) { mutableIntStateOf(0) }
    var pointerX by remember(request.url) { mutableIntStateOf(70) }
    var pointerY by remember(request.url) { mutableIntStateOf(70) }
    var cursorPositionInitialized by remember(request.url) { mutableStateOf(false) }
    var areaWidth by remember { mutableIntStateOf(1) }
    var areaHeight by remember { mutableIntStateOf(1) }
    var selectedCaption by remember(request.url) { mutableStateOf<CaptionCandidate?>(null) }
    var showSubtitlePicker by remember(request.url) { mutableStateOf(false) }
    var approved by remember(request.url) { mutableStateOf<InspectedMedia?>(null) }
    var nativeMedia by remember(request.url) { mutableStateOf<InspectedMedia?>(null) }
    var nativeEngine by remember(request.url) { mutableStateOf(ExtrasNativeEngine.MEDIA3) }
    var nativeResumePositionMs by remember(request.url) { mutableLongStateOf(0L) }
    var nativePlayRequested by remember(request.url) { mutableStateOf(true) }
    var inspectionJob by remember { mutableStateOf<Job?>(null) }
    var busy by remember { mutableStateOf(false) }
    var inspectionGeneration by remember { mutableIntStateOf(0) }
    var status by remember(request.url) {
        mutableStateOf("Loading the selected web player. If the picture stays blank, choose another server.")
    }
    var webLoadFailed by remember(request.url) { mutableStateOf(false) }
    // A blank HTML document may return HTTP 200 and never trigger onReceivedError.
    // Offer native recovery without guessing that absent network observations
    // imply playback failure (DRM/MSE pages need not expose media URLs).
    var slowEmbedHelp by remember(request.url) { mutableStateOf(false) }
    LaunchedEffect(request.url, webGeneration, webLoadFailed) {
        slowEmbedHelp = false
        if (!webLoadFailed) {
            kotlinx.coroutines.delay(15_000L)
            slowEmbedHelp = true
        }
    }
    // Keep the session alive while native playback temporarily replaces WebView.
    DisposableEffect(session) {
        onDispose { session.close() }
    }
    val bridge = remember(request.url, webGeneration) {
        MediaForgeWebBridge(
            onMediaResource = { url ->
                if (session.observe(url)) snapshot = session.snapshot()
            },
            onNavigationBlocked = {
                status = "Blocked a navigation outside the selected embed."
            },
            onPageLoadFailed = { message ->
                status = message
                webLoadFailed = true
            },
            onFullscreenRequested = { view ->
                htmlFullscreenView = view
                if (isAndroidTv) cursorEnabled = true
            },
            onFullscreenEnded = { htmlFullscreenView = null },
            onRendererGone = {
                // Android can kill Chromium on low-memory TV devices. Do not
                // reuse the dead WebView or auto-loop a crashing embed.
                inspectionGeneration += 1
                inspectionJob?.cancel()
                inspectionJob = null
                busy = false
                approved = null
                selectedCaption = null
                showSubtitlePicker = false
                session.clear()
                snapshot = session.snapshot()
                webView = null
                immersiveWeb = false
                cursorEnabled = false
                htmlFullscreenView = null
                status = "Embedded browser stopped. Select Reload player to try again."
                webLoadFailed = true
            }
        )
    }

    fun tryCompatibilityMode() {
        if (webCompatibilityMode) return
        webCompatibilityMode = true
        inspectionGeneration += 1
        inspectionJob?.cancel()
        inspectionJob = null
        busy = false
        approved = null
        selectedCaption = null
        showSubtitlePicker = false
        session.clear()
        snapshot = session.snapshot()
        status = "Compatibility mode enabled: third-party cookies and autoplay are allowed for this web player."
        webLoadFailed = false
        // New bridge + new WebView apply browser settings before loading the URL.
        webGeneration += 1
    }

    fun returnToWebView() {
        // Native handoff destroyed the old WebView and invalidated its observed
        // resource URLs. Signed provider URLs may expire or be session-scoped;
        // never keep an old native approval when the browser is recreated.
        inspectionGeneration += 1
        inspectionJob?.cancel()
        inspectionJob = null
        busy = false
        approved = null
        selectedCaption = null
        showSubtitlePicker = false
        session.clear()
        snapshot = session.snapshot()
        status = "Play the embedded media to discover fresh sources."
        webGeneration += 1
        nativeMedia = null
        nativeResumePositionMs = 0L
        nativePlayRequested = true
    }

    BackHandler {
        when {
            nativeMedia != null -> returnToWebView()
            htmlFullscreenView != null -> bridge.dismissFullscreen()
            immersiveWeb -> immersiveWeb = false
            cursorEnabled -> cursorEnabled = false
            else -> onBack()
        }
    }

    nativeMedia?.let { media ->
        when (nativeEngine) {
            ExtrasNativeEngine.MEDIA3 -> ExtrasMediaForgeNativePlayer(
                media = media,
                caption = selectedCaption,
                title = request.title,
                initialPositionMs = nativeResumePositionMs,
                initiallyPlayRequested = nativePlayRequested,
                onBack = { returnToWebView() },
                onUseLibVlc = { handoff ->
                    nativeResumePositionMs = handoff.positionMs
                    nativePlayRequested = handoff.playRequested
                    nativeEngine = ExtrasNativeEngine.LIBVLC
                }
            )
            ExtrasNativeEngine.LIBVLC -> ExtrasLibVlcPlayer(
                media = media,
                caption = selectedCaption,
                title = request.title,
                isTv = isAndroidTv,
                initialPositionMs = nativeResumePositionMs,
                initiallyPlayRequested = nativePlayRequested,
                onBack = { returnToWebView() },
                onUseMedia3 = { handoff ->
                    nativeResumePositionMs = handoff.positionMs
                    nativePlayRequested = handoff.playRequested
                    nativeEngine = ExtrasNativeEngine.MEDIA3
                },
                onChooseSubtitles = { handoff ->
                    nativeResumePositionMs = handoff.positionMs
                    nativePlayRequested = handoff.playRequested
                    showSubtitlePicker = true
                }
            )
        }
        // Keep the existing high-contrast TV subtitle dialog available while
        // LibVLC owns the player. Choosing another external caption creates a
        // fresh guarded FD and decoder; never reuse a released native surface.
        if (showSubtitlePicker) {
            ExtrasSubtitlePicker(
                captions = media.captions,
                selected = selectedCaption,
                isTv = isAndroidTv,
                onSelected = { picked ->
                    selectedCaption = picked
                    showSubtitlePicker = false
                },
                onDismiss = { showSubtitlePicker = false }
            )
        }
        return
    }

    LaunchedEffect(cursorEnabled, webView) {
        if (!cursorEnabled) webView?.requestFocus()
    }

    DisposableEffect(bridge) {
        onDispose {
            inspectionGeneration += 1
            inspectionJob?.cancel()
            bridge.close()
            session.clear()
        }
    }

    // Browser media should not continue consuming resources while the app is
    // backgrounded. WebView.onPause is best-effort for third-party players.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(webView, lifecycleOwner) {
        val activeView = webView
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> activeView?.onPause()
                Lifecycle.Event.ON_START -> activeView?.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
    // A short landscape phone or 720p TV can otherwise allocate the entire
    // viewport to the browser toolbar/footer and measure WebView at 0px.
    val shortWindow = maxHeight < 580.dp
    val headerCap = (maxHeight * 0.36f).coerceAtLeast(88.dp)
    val footerCap = (maxHeight * 0.23f).coerceAtLeast(64.dp)
    Column(
        Modifier.fillMaxSize()
            .then(if (!immersiveWeb) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier)
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (!immersiveWeb) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .then(if (shortWindow) Modifier.heightIn(max = headerCap)
                        .verticalScroll(rememberScrollState()) else Modifier)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("extras-embed-back")
                    ) { Text("Back") }
                    Text(
                        request.title,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Small phones receive a full-width source picker; TV/desktop
                // keep the picker and large D-pad targets on one row.
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth < 520.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (request.anilistId > 0) ExtrasAnimeEmbedServerDropdown(
                                server = selectedAnimeServer,
                                onSelected = onAnimeServerSelected,
                                isTv = isAndroidTv,
                                modifier = Modifier.fillMaxWidth(),
                                season = request.season ?: 1
                            ) else ExtrasEmbedServerDropdown(
                                server = selectedServer,
                                onSelected = onServerSelected,
                                isTv = isAndroidTv,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { immersiveWeb = true },
                                    modifier = Modifier.weight(1f).testTag("extras-embed-fullscreen")
                                ) { Text("Fullscreen") }
                                if (isAndroidTv) {
                                    OutlinedButton(
                                        onClick = { cursorEnabled = !cursorEnabled },
                                        modifier = Modifier.weight(1f).testTag("extras-virtual-mouse")
                                    ) { Text(if (cursorEnabled) "Cursor ON" else "Cursor OFF") }
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (request.anilistId > 0) ExtrasAnimeEmbedServerDropdown(
                                server = selectedAnimeServer,
                                onSelected = onAnimeServerSelected,
                                isTv = isAndroidTv,
                                modifier = Modifier.weight(1f),
                                season = request.season ?: 1
                            ) else ExtrasEmbedServerDropdown(
                                server = selectedServer,
                                onSelected = onServerSelected,
                                isTv = isAndroidTv,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(
                                onClick = { immersiveWeb = true },
                                modifier = Modifier.testTag("extras-embed-fullscreen")
                            ) { Text("Fullscreen") }
                            if (isAndroidTv) {
                                OutlinedButton(
                                    onClick = { cursorEnabled = !cursorEnabled },
                                    modifier = Modifier.testTag("extras-virtual-mouse")
                                ) { Text(if (cursorEnabled) "Cursor ON" else "Cursor OFF") }
                            }
                        }
                    }
                }

                Text(
                    (when {
                        request.anilistId > 0 -> "AniList #${request.anilistId} · ${selectedAnimeServer.description}"
                        else -> "TMDB #${request.tmdbId} · ${selectedServer.description}"
                    }) + " · Availability varies by provider.",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2
                )
                if (request.tmdbId > 0 && request.season == null &&
                    selectedServer == ExtrasEmbedServer.EMBED_HEAVEN
                ) {
                    OutlinedButton(
                        onClick = { onLegacyEmbedHeavenToggle(!request.legacyEmbedHeaven) },
                        modifier = Modifier.testTag("extras-embedheaven-legacy")
                    ) {
                        Text(if (request.legacyEmbedHeaven) "Use standard movie route" else "Try legacy movie route")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            inspectionGeneration += 1
                            inspectionJob?.cancel()
                            inspectionJob = null
                            busy = false
                            approved = null
                            selectedCaption = null
                            session.clear()
                            snapshot = session.snapshot()
                            status = "Reloading embedded player…"
                            webLoadFailed = false
                            if (webView != null) webView?.reload() else webGeneration += 1
                        },
                        modifier = Modifier.testTag("extras-embed-reload")
                    ) { Text("Reload player") }
                    OutlinedButton(
                        onClick = {
                            inspectionGeneration += 1
                            inspectionJob?.cancel()
                            inspectionJob = null
                            busy = false
                            approved = null
                            selectedCaption = null
                            session.clear()
                            snapshot = session.snapshot()
                            status = "Detected sources cleared."
                        },
                        modifier = Modifier.testTag("extras-clear-sources")
                    ) { Text("Clear sources") }
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).background(Color.Black)
                .onSizeChanged { size ->
                    areaWidth = size.width.coerceAtLeast(1)
                    areaHeight = size.height.coerceAtLeast(1)
                    if (!cursorPositionInitialized && areaWidth > 1 && areaHeight > 1) {
                        // Start at the video center rather than the top-left on 4K TVs.
                        pointerX = areaWidth / 2
                        pointerY = areaHeight / 2
                        cursorPositionInitialized = true
                    } else {
                        pointerX = ExtrasTvCursorPolicy.clamp(pointerX, 0, areaWidth)
                        pointerY = ExtrasTvCursorPolicy.clamp(pointerY, 0, areaHeight)
                    }
                }
        ) {
            key(request.url, webGeneration) {
                AndroidView(
                    factory = { ctx ->
                        try {
                            bridge.create(ctx, request.url, compatibilityMode = webCompatibilityMode).also { webView = it }
                        } catch (_: RuntimeException) {
                            status = ctx.getString(com.vaydelux.bousygram.R.string.extras_webview_unavailable)
                            android.widget.TextView(ctx).apply {
                                setText(com.vaydelux.bousygram.R.string.extras_webview_unavailable)
                                setTextColor(android.graphics.Color.WHITE)
                                setBackgroundColor(android.graphics.Color.BLACK)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    onRelease = { view ->
                        if (view is WebView && !bridge.rendererGone(view)) {
                            runCatching { view.stopLoading() }
                            runCatching { view.loadUrl("about:blank") }
                            runCatching { view.onPause() }
                            runCatching { view.removeAllViews() }
                            runCatching { view.destroy() }
                        }
                        if (webView === view) webView = null
                    }
                )
            }
            if (webLoadFailed) {
                Column(
                    modifier = Modifier.align(Alignment.Center)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(status, color = MaterialTheme.colorScheme.onSurface)
                    if (request.anilistId > 0) {
                        OutlinedButton(
                            onClick = onChooseAnimeServer,
                            modifier = Modifier.testTag("extras-anime-recover-server")
                        ) { Text("Choose another server") }
                    }
                    OutlinedButton(onClick = {
                        webLoadFailed = false
                        status = "Reloading embedded player…"
                        // A dead Chromium renderer has no reusable WebView or bridge.
                        if (webView != null) webView?.reload() else webGeneration += 1
                    }, modifier = Modifier.testTag("extras-embed-retry-load")) { Text("Retry page") }
                }
            }
            if (shouldOfferAnimeBlankPageHelp(
                    isAnime = request.anilistId > 0,
                    loadFailed = webLoadFailed,
                    immersive = immersiveWeb,
                    detectedVideo = snapshot.streams.isNotEmpty(),
                    longLoad = slowEmbedHelp
                )
            ) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.98f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Black screen? This web source may require cross-site cookies or autoplay.",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!webCompatibilityMode) {
                                OutlinedButton(
                                    onClick = { tryCompatibilityMode() },
                                    modifier = Modifier.weight(1f)
                                        .testTag("extras-anime-try-compatibility")
                                ) { Text("Try compatibility", maxLines = 2) }
                            }
                            OutlinedButton(
                                onClick = onChooseAnimeServer,
                                modifier = Modifier.weight(1f)
                                    .testTag("extras-anime-blank-recovery")
                            ) { Text("Choose server", maxLines = 2) }
                        }
                    }
                }
            }
            if (cursorEnabled && isAndroidTv) {
                ExtrasVirtualCursor(
                    target = webView,
                    widthPx = areaWidth,
                    heightPx = areaHeight,
                    xPx = pointerX,
                    yPx = pointerY,
                    onDelta = { dx, dy ->
                        pointerX = ExtrasTvCursorPolicy.clamp(pointerX, dx, areaWidth)
                        pointerY = ExtrasTvCursorPolicy.clamp(pointerY, dy, areaHeight)
                    },
                    onConfirm = { webView?.let { bridge.click(it, pointerX.toFloat(), pointerY.toFloat()) } },
                    isActive = htmlFullscreenView == null,
                    tag = "extras-virtual-cursor"
                )
            }
        }

        if (!immersiveWeb) {
        Column(
            Modifier.fillMaxWidth()
                .then(if (shortWindow) Modifier.heightIn(max = footerCap)
                    .verticalScroll(rememberScrollState()) else Modifier)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "WebView playback · ${snapshot.streams.size} video candidate(s) · ${snapshot.captions.size} subtitle(s)",
                style = MaterialTheme.typography.labelMedium
            )
            Text(status, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            if (snapshot.streams.isEmpty()) {
                Text(
                    "No accessible stream discovered yet. You can continue watching in WebView.",
                    style = MaterialTheme.typography.labelSmall
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(snapshot.streams.size) { index ->
                        val candidate = snapshot.streams[index]
                        OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            inspectionGeneration += 1
                            val generation = inspectionGeneration
                            inspectionJob?.cancel()
                            approved = null
                            selectedCaption = null
                            busy = true
                            status = "Checking source availability…"
                            inspectionJob = scope.launch {
                                try {
                                    when (val result = session.inspect(candidate.id)) {
                                        is MediaForgeSessionInspection.Verified -> {
                                            if (generation == inspectionGeneration) {
                                                approved = result.media
                                                status = "Native playback may be available. Select Play natively."
                                            }
                                        }
                                        is MediaForgeSessionInspection.Rejected -> {
                                            if (generation == inspectionGeneration) {
                                                status = when (result.reason) {
                                                    MediaForgeInspectionFailure.SESSION_CLOSED ->
                                                        "The media session was closed. Reload the player."
                                                    MediaForgeInspectionFailure.SUPERSEDED ->
                                                        "Source selection changed. Select a current source."
                                                    MediaForgeInspectionFailure.NOT_OBSERVED ->
                                                        "That source is no longer available. Reload sources."
                                                    MediaForgeInspectionFailure.SOURCE_UNAVAILABLE,
                                                    MediaForgeInspectionFailure.UNSAFE_RESULT ->
                                                        "Unable to verify source. Continue in WebView or try another source."
                                                }
                                            }
                                        }
                                    }
                                } catch (cancelled: CancellationException) {
                                    // Navigation, a newer choice, or browser reload
                                    // cancels this probe; it must not post stale UI.
                                    throw cancelled
                                } catch (_: Exception) {
                                    if (generation == inspectionGeneration) {
                                        approved = null
                                        // Never expose arbitrary provider exception
                                        // messages: URLs may contain credentials.
                                        status = "Could not check this source. Check your connection or select another stream."
                                    }
                                } finally {
                                    if (generation == inspectionGeneration) busy = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("extras-stream-candidate")
                    ) {
                        Text("${candidate.format} · ${candidate.host}", maxLines = 1)
                    }
                    }
                }
            }
            if (busy) CircularProgressIndicator(Modifier.size(22.dp))
            approved?.let { media ->
                val vlcAvailable = com.vaydelux.bousygram.BuildConfig.MEDIAFORGE_EXTRAS_LIBVLC_ENABLED &&
                    MediaForgeGuardedProgressiveSource.supported(media.stream)
                val launchEngine = extrasAvailableNativeEngine(
                    nativeEngine,
                    guardedVlcEnabled = com.vaydelux.bousygram.BuildConfig.MEDIAFORGE_EXTRAS_LIBVLC_ENABLED,
                    progressiveSupported = MediaForgeGuardedProgressiveSource.supported(media.stream)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExtrasNativeEngine.entries.forEach { engine ->
                        val available = engine != ExtrasNativeEngine.LIBVLC || vlcAvailable
                        FilterChip(
                            selected = launchEngine == engine,
                            enabled = available,
                            onClick = { if (available) nativeEngine = engine },
                            label = {
                                Text(if (available) engine.label else "LibVLC (MP4/WebM debug)")
                            },
                            modifier = Modifier.testTag("extras-player-" + engine.name.lowercase())
                        )
                    }
                }
                if (media.captions.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { showSubtitlePicker = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = if (isAndroidTv) 54.dp else 44.dp)
                            .testTag("extras-subtitles-open")
                    ) {
                        Text(
                            "Subtitles: " + (selectedCaption?.label ?: selectedCaption?.language ?: "Off") +
                                "  ·  ${media.captions.size} available",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = if (isAndroidTv) 19.sp else 15.sp,
                            maxLines = 2
                        )
                    }
                } else {
                    Text(
                        "No external subtitle tracks were discovered.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Button(
                    modifier = Modifier.fillMaxWidth().testTag("extras-play-native"),
                    onClick = {
                        inspectionGeneration += 1
                        inspectionJob?.cancel()
                        nativeResumePositionMs = 0L
                        nativePlayRequested = true // An explicit launch is a new Play action.
                        nativeEngine = launchEngine // A previous LibVLC choice may not support this format.
                        nativeMedia = media
                    }
                ) { Text("Play with " + launchEngine.label) }
            }
        }
        }
    }

    if (showSubtitlePicker && approved != null) {
        ExtrasSubtitlePicker(
            captions = approved!!.captions,
            selected = selectedCaption,
            isTv = isAndroidTv,
            onSelected = { caption ->
                selectedCaption = caption
                showSubtitlePicker = false
            },
            onDismiss = { showSubtitlePicker = false }
        )
    }

    // Chromium's HTML5 fullscreen surface is distinct from the ordinary
    // WebView. Keep the browser mounted; never remount the iframe on fullscreen.
    htmlFullscreenView?.let { videoView ->
        var fullscreenWidth by remember { mutableIntStateOf(1) }
        var fullscreenHeight by remember { mutableIntStateOf(1) }
        Box(
            Modifier.fillMaxSize().background(Color.Black)
                .onSizeChanged { size ->
                    fullscreenWidth = size.width.coerceAtLeast(1)
                    fullscreenHeight = size.height.coerceAtLeast(1)
                    pointerX = pointerX.coerceIn(0, fullscreenWidth - 1)
                    pointerY = pointerY.coerceIn(0, fullscreenHeight - 1)
                }
        ) {
            AndroidView(
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        (videoView.parent as? ViewGroup)?.removeView(videoView)
                        addView(
                            videoView,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { parent ->
                    if (parent is FrameLayout) parent.removeAllViews()
                }
            )
            if (cursorEnabled && isAndroidTv) {
                ExtrasVirtualCursor(
                    target = videoView,
                    widthPx = fullscreenWidth,
                    heightPx = fullscreenHeight,
                    xPx = pointerX,
                    yPx = pointerY,
                    onDelta = { dx, dy ->
                        pointerX = ExtrasTvCursorPolicy.clamp(pointerX, dx, fullscreenWidth)
                        pointerY = ExtrasTvCursorPolicy.clamp(pointerY, dy, fullscreenHeight)
                    },
                    onConfirm = { bridge.click(videoView, pointerX.toFloat(), pointerY.toFloat()) },
                    isActive = true,
                    tag = "extras-fullscreen-virtual-cursor",
                    onMenu = {
                        bridge.dismissFullscreen()
                        if (request.anilistId > 0) onChooseAnimeServer()
                    }
                )
            }
            // Always draw trusted recovery controls above the untrusted HTML surface.
            Box(Modifier.align(Alignment.TopCenter)) {
                ExtrasFullscreenRecoveryControls(
                    isAnime = request.anilistId > 0,
                    onExitFullscreen = { bridge.dismissFullscreen() },
                    onChooseAnimeServer = {
                        bridge.dismissFullscreen()
                        onChooseAnimeServer()
                    }
                )
            }
        }
    }
    }
}

/**
 * A virtualized D-pad accessible server dialog avoids eagerly composing over
 * fifty WebView providers on memory-limited Android TVs.
 */
private data class ExtrasServerOption(
    val key: String, val label: String, val description: String,
    val group: String
)

@Composable
private fun ExtrasServerPicker(
    label: String,
    selectedKey: String,
    options: List<ExtrasServerOption>,
    onSelected: (String) -> Unit,
    isTv: Boolean,
    modifier: Modifier = Modifier,
    pickerTitle: String = "Choose a web server",
    compactOptions: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = if (isTv) 56.dp else 48.dp)
                .testTag("extras-server-dropdown")
        ) {
            Text("Server: $label  ▾", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (expanded) {
            Dialog(
                onDismissRequest = { expanded = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                val listState = rememberLazyListState()
                val selectedFocus = remember { FocusRequester() }
                var focusedKey by remember { mutableStateOf<String?>(null) }
                // Include section headers when restoring the selected option.
                val selectedPosition = remember(options, selectedKey) {
                    val selected = options.indexOfFirst { it.key == selectedKey }
                    if (selected < 0) 0
                    else selected + options.take(selected + 1).map { it.group }.distinct().size
                }
                LaunchedEffect(selectedPosition) {
                    listState.scrollToItem(selectedPosition)
                    withFrameNanos { }
                    if (!selectedFocus.requestFocus()) {
                        withFrameNanos { }
                        selectedFocus.requestFocus()
                    }
                }
                // Height must be constrained by the *actual dialog window*.
                // A fixed 460.dp list plus title/description overflows 720p TVs
                // and smaller phone landscapes. The weighted list scrolls
                // independently while header and Close stay accessible.
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize()
                        .padding(horizontal = if (isTv) 22.dp else 12.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val maxPanelHeight = (maxHeight * 0.94f)
                        .coerceAtMost(if (isTv) 650.dp else 540.dp)
                    Surface(
                        modifier = Modifier.widthIn(max = if (isTv) 680.dp else 460.dp)
                            .fillMaxWidth()
                            .heightIn(max = maxPanelHeight)
                            .testTag("extras-server-dialog"),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shadowElevation = 24.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(if (isTv) 18.dp else 14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    pickerTitle,
                                    modifier = Modifier.weight(1f),
                                    style = if (isTv) MaterialTheme.typography.titleLarge
                                        else MaterialTheme.typography.titleMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                OutlinedButton(
                                    onClick = { expanded = false },
                                    modifier = Modifier.testTag("extras-server-close")
                                ) { Text("Close") }
                            }
                            Text(
                                "${options.size} servers · Third-party availability and licensing are unverified.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxWidth()
                                    .weight(1f, fill = false)
                                    .testTag("extras-server-options"),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                options.groupBy { it.group }.forEach { (group, candidates) ->
                                    item(key = "group:$group") {
                                        Text(
                                            group,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(
                                                horizontal = 12.dp, vertical = 6.dp
                                            )
                                        )
                                    }
                                    items(candidates, key = { it.key }) { candidate ->
                                        val isSelected = candidate.key == selectedKey
                                        val isFocused = candidate.key == focusedKey
                                        val shape = RoundedCornerShape(12.dp)
                                        DropdownMenuItem(
                                            text = {
                                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Text(
                                                        (if (isSelected) "✓  " else "") + candidate.label,
                                                        fontSize = if (isTv) 19.sp else 16.sp,
                                                        color = if (isSelected)
                                                            MaterialTheme.colorScheme.onPrimaryContainer
                                                        else MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    // Anime descriptions are identical for every
                                                    // provider; repeating them crowds the picker.
                                                    if (!compactOptions) {
                                                        Text(
                                                            candidate.description,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                expanded = false
                                                if (!isSelected) onSelected(candidate.key)
                                            },
                                            modifier = Modifier
                                                .background(
                                                    if (isSelected)
                                                        MaterialTheme.colorScheme.primaryContainer
                                                    else Color.Transparent,
                                                    shape
                                                )
                                                .border(
                                                    if (isFocused) 2.dp else 1.dp,
                                                    if (isFocused) MaterialTheme.colorScheme.primary
                                                    else Color.Transparent,
                                                    shape
                                                )
                                                .heightIn(min = if (isTv) 60.dp else 48.dp)
                                                .onFocusChanged {
                                                    if (it.isFocused) focusedKey = candidate.key
                                                    else if (focusedKey == candidate.key) focusedKey = null
                                                }
                                                .then(if (isSelected)
                                                    Modifier.focusRequester(selectedFocus) else Modifier)
                                                .semantics { selected = isSelected }
                                                .testTag(candidate.key)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ExtrasEmbedServerDropdown(
    server: ExtrasEmbedServer,
    onSelected: (ExtrasEmbedServer) -> Unit,
    isTv: Boolean,
    modifier: Modifier = Modifier
) {
    val options = remember {
        val existing = setOf(ExtrasEmbedServer.CINESRC, ExtrasEmbedServer.XXIKU, ExtrasEmbedServer.EMBED_HEAVEN)
        ExtrasEmbedServer.entries.filter { it.selectable }
            .sortedBy { when { it in existing -> 0; it.featured -> 1; else -> 2 } }
            .map {
                ExtrasServerOption(
                    "extras-server-" + it.name.lowercase(), it.label, it.description,
                    when {
                        it in existing -> "Existing servers"
                        it.featured -> "New servers"
                        else -> "More servers"
                    }
                )
            }
    }
    ExtrasServerPicker(server.label, "extras-server-" + server.name.lowercase(),
        options, { key -> onSelected(ExtrasEmbedServer.entries.first { "extras-server-" + it.name.lowercase() == key }) },
        isTv, modifier)
}

@Composable
internal fun ExtrasAnimeEmbedServerDropdown(
    server: ExtrasAnimeEmbedServer,
    onSelected: (ExtrasAnimeEmbedServer) -> Unit,
    isTv: Boolean,
    modifier: Modifier = Modifier,
    season: Int = 1
) {
    val options = remember(season) {
        ExtrasAnimeEmbedServer.entries.filter { it.supportsSeason(season) }.map {
            ExtrasServerOption("extras-anime-server-" + it.name.lowercase(),
                it.label, it.description, if (it.featured) "New anime servers" else "Default anime server")
        }
    }
    ExtrasServerPicker(server.label, "extras-anime-server-" + server.name.lowercase(),
        options, { key -> onSelected(ExtrasAnimeEmbedServer.entries.first { it.supportsSeason(season) && "extras-anime-server-" + it.name.lowercase() == key }) },
        isTv, modifier, pickerTitle = "Choose anime server", compactOptions = true)
}

/**
 * Reuses the same DPAD pointer for ordinary WebView and HTML5 custom fullscreen.
 * Pointer coordinates belong to the target view, not its outer Activity.
 */
@Composable
internal fun ExtrasVirtualCursor(
    target: View?,
    widthPx: Int,
    heightPx: Int,
    xPx: Int,
    yPx: Int,
    onDelta: (Int, Int) -> Unit,
    onConfirm: () -> Unit,
    isActive: Boolean,
    tag: String,
    onMenu: (() -> Unit)? = null
) {
    val focus = remember { FocusRequester() }
    // The HTML5 fullscreen window / WebView may attach after composition.
    // Two bounded frame attempts avoid an unresponsive remote on slow TVs.
    LaunchedEffect(isActive, target) {
        if (isActive) {
            withFrameNanos { }
            if (!focus.requestFocus()) {
                withFrameNanos { }
                focus.requestFocus()
            }
        }
    }
    Box(
        modifier = Modifier.fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (!isActive || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val repeat = event.nativeKeyEvent.repeatCount
                val step = ExtrasTvCursorPolicy.step(widthPx, heightPx, repeat)
                when (event.nativeKeyEvent.keyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                        onDelta(0, -step)
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                        onDelta(0, step)
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                        onDelta(-step, 0)
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                        onDelta(step, 0)
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER -> {
                        if (repeat == 0) onConfirm()
                        true
                    }
                    AndroidKeyEvent.KEYCODE_MENU -> {
                        if (repeat == 0) onMenu?.invoke()
                        onMenu != null
                    }
                    else -> false
                }
            }
            .focusRequester(focus)
            .focusable()
            .testTag(tag)
    ) {
        Canvas(
            Modifier.offset {
                IntOffset(
                    xPx.coerceIn(0, widthPx - 1),
                    yPx.coerceIn(0, heightPx - 1)
                )
            }.size(30.dp)
        ) {
            // Larger high-contrast arrow remains legible at TV viewing distances.
            // Pointer tip is also the exact synthesized tap hotspot.
            val arrow = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width * 0.20f, size.height * 0.92f)
                lineTo(size.width * 0.43f, size.height * 0.66f)
                lineTo(size.width * 0.62f, size.height * 0.97f)
                lineTo(size.width * 0.82f, size.height * 0.86f)
                lineTo(size.width * 0.64f, size.height * 0.58f)
                lineTo(size.width * 0.96f, size.height * 0.52f)
                close()
            }
            drawPath(arrow, Color.White)
            drawPath(arrow, Color.Black, style = Stroke(width = 2.dp.toPx()))
        }
    }
}

@Composable
internal fun ExtrasSubtitlePicker(
    captions: List<CaptionCandidate>,
    selected: CaptionCandidate?,
    isTv: Boolean,
    onSelected: (CaptionCandidate?) -> Unit,
    onDismiss: () -> Unit
) {
    val selectedFocus = remember { FocusRequester() }
    val focusOff = selected == null || captions.none { it.url == selected.url }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(if (isTv) 0.78f else 0.94f)
                .widthIn(max = if (isTv) 780.dp else 480.dp)
                .testTag("extras-subtitle-dialog"),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            shadowElevation = 24.dp
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = if (isTv) 650.dp else 540.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(if (isTv) 26.dp else 18.dp),
                verticalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 9.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Subtitle tracks",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = if (isTv) 28.sp else 21.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = onDismiss) {
                        Text("Close", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Text(
                    "Choose a subtitle track using the D-pad, or turn subtitles off.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = if (isTv) 18.sp else 14.sp
                )
                SubtitlePickerChoice(
                    title = "Off · No subtitles",
                    selected = selected == null,
                    isTv = isTv,
                    modifier = if (focusOff) Modifier.focusRequester(selectedFocus) else Modifier,
                    onClick = { onSelected(null) }
                )
                captions.forEach { caption ->
                    SubtitlePickerChoice(
                        title = (caption.label ?: caption.language ?: caption.host) +
                            "  ·  ${caption.format.name}",
                        selected = selected?.url == caption.url,
                        isTv = isTv,
                        modifier = if (!focusOff && selected?.url == caption.url)
                            Modifier.focusRequester(selectedFocus) else Modifier,
                        onClick = { onSelected(caption) }
                    )
                }
            }
        }
        // Dialog content lives in a *separate Android window*. On initial
        // composition its focus targets may exist before that window has been
        // measured/attached; an immediate requestFocus() can return false
        // (or lose focus to the window's initial focus search).
        // Request after the next frame and retry once if attachment is late.
        // No timers and no unbounded focus loop on slow ARM32 TVs.
        LaunchedEffect(selected?.url, focusOff, captions.map { it.url }) {
            withFrameNanos { }
            if (!selectedFocus.requestFocus()) {
                withFrameNanos { }
                selectedFocus.requestFocus()
            }
        }
    }
}

@Composable
private fun SubtitlePickerChoice(
    title: String,
    selected: Boolean,
    isTv: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    val colors = MaterialTheme.colorScheme
    val fill = when {
        focused -> colors.primaryContainer
        selected -> colors.secondaryContainer
        else -> colors.surfaceContainerHigh
    }
    val foreground = when {
        focused -> colors.onPrimaryContainer
        selected -> colors.onSecondaryContainer
        else -> colors.onSurface
    }
    Surface(
        modifier = modifier.fillMaxWidth()
            .heightIn(min = if (isTv) 64.dp else 48.dp)
            .border(
                if (focused) 3.dp else 1.dp,
                if (focused) colors.primary else colors.outline,
                shape
            )
            .onFocusChanged { focused = it.isFocused }
            .selectable(selected = selected, onClick = onClick),
        color = fill,
        contentColor = foreground,
        shape = shape
    ) {
        Row(
            Modifier.padding(horizontal = if (isTv) 20.dp else 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                modifier = Modifier.weight(1f),
                fontSize = if (isTv) 20.sp else 15.sp,
                color = foreground,
                maxLines = 2
            )
            if (selected) Text("✓", color = foreground, fontSize = 24.sp)
        }
    }
}

@Composable
private fun ExtrasMediaForgeNativePlayer(
    media: InspectedMedia,
    caption: CaptionCandidate?,
    title: String,
    initialPositionMs: Long,
    initiallyPlayRequested: Boolean,
    onBack: () -> Unit,
    onUseLibVlc: (ExtrasPlaybackHandoff) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // A new source or subtitle selection needs a NEW player; releasing the
    // previous DisposableEffect must never leave a released ExoPlayer in remember.
    val player = remember(media, caption?.url, context, initialPositionMs, initiallyPlayRequested) {
        MediaForgeMedia3.newPlayer(context)
    }
    val controls = remember(player) { MediaForgeMedia3Controls(player) }
    val foregroundIntent = remember(player) { ExtrasForegroundPlaybackIntent() }
    // A changed caption/source creates a new player; stale errors must not bleed across it.
    var playbackError by remember(player) { mutableStateOf<String?>(null) }
    DisposableEffect(player, lifecycleOwner) {
        val initiallyForeground = lifecycleOwner.lifecycle.currentState
            .isAtLeast(Lifecycle.State.STARTED)
        foregroundIntent.onInitialAttach(initiallyPlayRequested, initiallyForeground)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    // Capture playWhenReady, not isPlaying: a buffering decoder
                    // still owns an active Play intent.
                    foregroundIntent.onBackground(
                        shouldPreserveExtrasMedia3Resume(
                            playWhenReady = player.playWhenReady,
                            hasPlaybackError = playbackError != null,
                            reachedEnd = player.playbackState == Player.STATE_ENDED
                        )
                    )
                    controls.pause()
                }
                Lifecycle.Event.ON_START -> {
                    if (foregroundIntent.onForeground() &&
                        shouldPreserveExtrasMedia3Resume(
                            playWhenReady = true,
                            hasPlaybackError = playbackError != null,
                            reachedEnd = player.playbackState == Player.STATE_ENDED
                        )
                    ) controls.play()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                foregroundIntent.cancel()
                playbackError = "Native playback failed. Return to WebView or try another source."
            }
        }
        player.addListener(listener)
        player.setMediaItem(MediaForgeMedia3.mediaItem(media, caption))
        if (initialPositionMs > 0L) controls.seekTo(initialPositionMs)
        player.prepare()
        player.playWhenReady = initiallyPlayRequested && initiallyForeground
        onDispose {
            foregroundIntent.cancel()
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.removeListener(listener)
            controls.release()
        }
    }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onBack) { Text("Back to WebView") }
            Text(title, color = Color.White, maxLines = 1, modifier = Modifier.weight(1f))
            if (com.vaydelux.bousygram.BuildConfig.MEDIAFORGE_EXTRAS_LIBVLC_ENABLED &&
                MediaForgeGuardedProgressiveSource.supported(media.stream)
            ) {
                OutlinedButton(
                    modifier = Modifier.testTag("extras-media3-use-libvlc"),
                    onClick = {
                        onUseLibVlc(extrasMedia3Handoff(
                            positionMs = player.currentPosition,
                            seekable = player.isCurrentMediaItemSeekable,
                            playWhenReady = player.playWhenReady,
                            failed = playbackError != null,
                            ended = player.playbackState == Player.STATE_ENDED
                        ))
                    }
                ) { Text("Use LibVLC") }
            }
        }
        playbackError?.let { error ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(error, color = Color(0xFFFFC7C7), modifier = Modifier.weight(1f))
                // Explicit retry rather than endlessly auto-restarting failed media.
                Button(
                    modifier = Modifier.testTag("extras-native-media3-retry"),
                    onClick = {
                        playbackError = null
                        player.prepare()
                        if (lifecycleOwner.lifecycle.currentState
                                .isAtLeast(Lifecycle.State.STARTED)
                        ) {
                            controls.play()
                        } else {
                            foregroundIntent.onBackground(true)
                            controls.pause()
                        }
                    }
                ) { Text("Retry playback") }
            }
        }
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = true
                    this.player = player
                }
            },
            update = { it.player = player },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
    }
}
