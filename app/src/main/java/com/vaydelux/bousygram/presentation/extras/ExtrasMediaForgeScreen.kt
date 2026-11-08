package com.vaydelux.bousygram.presentation.extras

import android.view.KeyEvent as AndroidKeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import com.vaydelux.mediaforge.core.InspectionResult
import com.vaydelux.mediaforge.core.MediaForgeDiscovery
import com.vaydelux.mediaforge.core.MediaForgeResolver
import com.vaydelux.mediaforge.core.MediaSnapshot
import com.vaydelux.mediaforge.players.MediaForgeMedia3
import com.vaydelux.mediaforge.players.MediaForgeGuardedProgressiveSource
import com.vaydelux.mediaforge.webbridge.MediaForgeWebBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A title-specific embed chosen explicitly by the Extras user. This is not a
 * TMDB stream and is never used by Telegram Media.
 */
internal data class ExtrasEmbedRequest(val url: String, val title: String) {
    companion object {
        fun movie(tmdbId: Int, title: String): ExtrasEmbedRequest {
            require(tmdbId > 0)
            return ExtrasEmbedRequest("https://cinesrc.st/embed/movie/$tmdbId", title)
        }

        fun episode(tmdbId: Int, season: Int, episode: Int, title: String): ExtrasEmbedRequest {
            require(tmdbId > 0 && season >= 0 && episode > 0)
            return ExtrasEmbedRequest(
                "https://cinesrc.st/embed/tv/$tmdbId?s=$season&e=$episode",
                "$title · S$season E$episode"
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
    val discovery = remember(request.url) { MediaForgeDiscovery() }
    val resolver = remember { MediaForgeResolver() }
    val scope = rememberCoroutineScope()
    var snapshot by remember(request.url) { mutableStateOf(MediaSnapshot(emptyList(), emptyList())) }
    var webView by remember(request.url) { mutableStateOf<WebView?>(null) }
    var cursorEnabled by remember(request.url) { mutableStateOf(false) }
    var immersiveWeb by remember(request.url) { mutableStateOf(false) }
    var htmlFullscreenView by remember(request.url) { mutableStateOf<View?>(null) }
    // The previous bridge is permanently closed when native playback disposes WebView.
    // A new generation must create a new bridge when the user returns to WebView.
    var webGeneration by remember(request.url) { mutableIntStateOf(0) }
    var pointerX by remember(request.url) { mutableIntStateOf(70) }
    var pointerY by remember(request.url) { mutableIntStateOf(70) }
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
    var status by remember { mutableStateOf("Play the embedded media to discover available sources.") }
    val bridge = remember(request.url, webGeneration) {
        MediaForgeWebBridge(
            onMediaResource = { url ->
                if (discovery.observe(url)) snapshot = discovery.snapshot()
            },
            onNavigationBlocked = {
                status = "Blocked a navigation outside the selected embed."
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
                discovery.clear()
                snapshot = discovery.snapshot()
                webView = null
                immersiveWeb = false
                cursorEnabled = false
                htmlFullscreenView = null
                status = "Embedded browser stopped. Select Reload player to try again."
            }
        )
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
        discovery.clear()
        snapshot = discovery.snapshot()
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
            discovery.clear()
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

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (!immersiveWeb) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onBack, modifier = Modifier.testTag("extras-embed-back")) {
                Text("Back")
            }
            Text(request.title, modifier = Modifier.weight(1f), maxLines = 1)
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

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
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
                    discovery.clear()
                    snapshot = discovery.snapshot()
                    status = "Reloading embedded player…"
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
                    discovery.clear()
                    snapshot = discovery.snapshot()
                    status = "Detected sources cleared."
                },
                modifier = Modifier.testTag("extras-clear-sources")
            ) { Text("Clear sources") }
        }

        }

        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).background(Color.Black)
                .onSizeChanged { size ->
                    areaWidth = size.width.coerceAtLeast(1)
                    areaHeight = size.height.coerceAtLeast(1)
                    pointerX = pointerX.coerceIn(0, areaWidth - 1)
                    pointerY = pointerY.coerceIn(0, areaHeight - 1)
                }
        ) {
            key(webGeneration) {
                AndroidView(
                    factory = { ctx ->
                        try {
                            bridge.create(ctx, request.url).also { webView = it }
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
            if (cursorEnabled && isAndroidTv) {
                ExtrasVirtualCursor(
                    target = webView,
                    widthPx = areaWidth,
                    heightPx = areaHeight,
                    xPx = pointerX,
                    yPx = pointerY,
                    onDelta = { dx, dy ->
                        pointerX = (pointerX + dx).coerceIn(0, areaWidth - 1)
                        pointerY = (pointerY + dy).coerceIn(0, areaHeight - 1)
                    },
                    onConfirm = { webView?.let { bridge.click(it, pointerX.toFloat(), pointerY.toFloat()) } },
                    isActive = htmlFullscreenView == null,
                    tag = "extras-virtual-cursor"
                )
            }
        }

        if (!immersiveWeb) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
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
                                    when (val result = resolver.inspect(candidate, snapshot.captions)) {
                                        is InspectionResult.Playable -> {
                                            if (generation == inspectionGeneration) {
                                                approved = result.media
                                                status = "Native playback may be available. Select Play natively."
                                            }
                                        }
                                        is InspectionResult.Unavailable -> {
                                            if (generation == inspectionGeneration) status = result.reason
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
                        pointerX = (pointerX + dx).coerceIn(0, fullscreenWidth - 1)
                        pointerY = (pointerY + dy).coerceIn(0, fullscreenHeight - 1)
                    },
                    onConfirm = { bridge.click(videoView, pointerX.toFloat(), pointerY.toFloat()) },
                    isActive = true,
                    tag = "extras-fullscreen-virtual-cursor"
                )
            }
        }
    }
    }
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
    tag: String
) {
    val focus = remember { FocusRequester() }
    // Explicit user input is required before any synthetic pointer action.
    LaunchedEffect(isActive, target) {
        if (isActive) runCatching { focus.requestFocus() }
    }
    Box(
        modifier = Modifier.fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (!isActive || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val repeat = event.nativeKeyEvent.repeatCount
                val baseStep = maxOf(18, minOf(widthPx, heightPx) / 55)
                val step = if (repeat > 4) baseStep * 2 else baseStep
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
            }.size(24.dp)
        ) {
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
            color = Color(0xFF0D1929),
            contentColor = Color.White,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
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
                        color = Color.White,
                        fontSize = if (isTv) 28.sp else 21.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = onDismiss) {
                        Text("Close", color = Color.White)
                    }
                }
                Text(
                    "Choose a subtitle track using the D-pad, or turn subtitles off.",
                    color = Color(0xFFD0DCEB),
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
    Surface(
        modifier = modifier.fillMaxWidth()
            .heightIn(min = if (isTv) 64.dp else 48.dp)
            .border(
                if (focused) 3.dp else 1.dp,
                if (focused) Color(0xFF78D8FF) else Color(0xFF3B526C),
                shape
            )
            .onFocusChanged { focused = it.isFocused }
            .selectable(selected = selected, onClick = onClick),
        color = if (focused) Color(0xFF175481)
            else if (selected) Color(0xFF174061) else Color(0xFF17273B),
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
                color = Color.White,
                maxLines = 2
            )
            if (selected) Text("✓", color = Color(0xFF9EF4C9), fontSize = 24.sp)
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
                    player.pause()
                }
                Lifecycle.Event.ON_START -> {
                    if (foregroundIntent.onForeground() &&
                        shouldPreserveExtrasMedia3Resume(
                            playWhenReady = true,
                            hasPlaybackError = playbackError != null,
                            reachedEnd = player.playbackState == Player.STATE_ENDED
                        )
                    ) player.play()
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
        if (initialPositionMs > 0L) player.seekTo(initialPositionMs)
        player.prepare()
        player.playWhenReady = initiallyPlayRequested &&
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        onDispose {
            foregroundIntent.cancel()
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.stop()
            player.release()
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
                            player.play()
                        } else {
                            foregroundIntent.onBackground(true)
                            player.pause()
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
