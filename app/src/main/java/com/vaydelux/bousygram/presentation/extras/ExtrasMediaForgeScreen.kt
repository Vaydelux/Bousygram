package com.vaydelux.bousygram.presentation.extras

import android.view.KeyEvent as AndroidKeyEvent
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.vaydelux.mediaforge.core.CaptionCandidate
import com.vaydelux.mediaforge.core.InspectedMedia
import com.vaydelux.mediaforge.core.InspectionResult
import com.vaydelux.mediaforge.core.MediaForgeDiscovery
import com.vaydelux.mediaforge.core.MediaForgeResolver
import com.vaydelux.mediaforge.core.MediaSnapshot
import com.vaydelux.mediaforge.core.StreamCandidate
import com.vaydelux.mediaforge.players.MediaForgeMedia3
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
    var pointerX by remember(request.url) { mutableIntStateOf(70) }
    var pointerY by remember(request.url) { mutableIntStateOf(70) }
    var areaWidth by remember { mutableIntStateOf(1) }
    var areaHeight by remember { mutableIntStateOf(1) }
    var selectedCaption by remember(request.url) { mutableStateOf<CaptionCandidate?>(null) }
    var approved by remember(request.url) { mutableStateOf<InspectedMedia?>(null) }
    var nativeMedia by remember(request.url) { mutableStateOf<InspectedMedia?>(null) }
    var inspectionJob by remember { mutableStateOf<Job?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Play the embedded media to discover available sources.") }
    val cursorFocus = remember { FocusRequester() }
    val bridge = remember(request.url) {
        MediaForgeWebBridge(
            onMediaResource = { url ->
                if (discovery.observe(url)) snapshot = discovery.snapshot()
            },
            onNavigationBlocked = {
                status = "Blocked a navigation outside the selected embed."
            }
        )
    }

    BackHandler { if (nativeMedia != null) nativeMedia = null else onBack() }

    if (nativeMedia != null) {
        ExtrasMediaForgeNativePlayer(
            media = nativeMedia!!,
            caption = selectedCaption,
            title = request.title,
            onBack = { nativeMedia = null }
        )
        return
    }

    LaunchedEffect(cursorEnabled) {
        if (cursorEnabled) {
            cursorFocus.requestFocus()
        } else {
            webView?.requestFocus()
        }
    }

    DisposableEffect(bridge) {
        onDispose {
            inspectionJob?.cancel()
            bridge.close()
            webView?.apply {
                stopLoading()
                loadUrl("about:blank")
                onPause()
                removeAllViews()
                destroy()
            }
            webView = null
            discovery.clear()
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onBack, modifier = Modifier.testTag("extras-embed-back")) {
                Text("Back")
            }
            Text(request.title, modifier = Modifier.weight(1f), maxLines = 1)
            if (isAndroidTv) {
                OutlinedButton(
                    onClick = { cursorEnabled = !cursorEnabled },
                    modifier = Modifier.testTag("extras-virtual-mouse")
                ) { Text(if (cursorEnabled) "Cursor ON" else "Cursor OFF") }
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).background(Color.Black)
                .onSizeChanged { size ->
                    areaWidth = size.width.coerceAtLeast(1)
                    areaHeight = size.height.coerceAtLeast(1)
                }
        ) {
            AndroidView(
                factory = { ctx ->
                    bridge.create(ctx, request.url).also { webView = it }
                },
                modifier = Modifier.fillMaxSize()
            )
            if (cursorEnabled && isAndroidTv) {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            val jump = if (event.nativeKeyEvent.repeatCount > 4) 34 else 18
                            when (event.nativeKeyEvent.keyCode) {
                                AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                                    pointerY = (pointerY - jump).coerceAtLeast(0); true
                                }
                                AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                                    pointerY = (pointerY + jump).coerceAtMost(areaHeight - 1); true
                                }
                                AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                                    pointerX = (pointerX - jump).coerceAtLeast(0); true
                                }
                                AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    pointerX = (pointerX + jump).coerceAtMost(areaWidth - 1); true
                                }
                                AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER -> {
                                    webView?.let {
                                        bridge.click(it, pointerX.toFloat(), pointerY.toFloat())
                                    }
                                    true
                                }
                                else -> false
                            }
                        }
                        .focusRequester(cursorFocus)
                        .focusable()
                        .testTag("extras-virtual-cursor")
                ) {
                    Box(
                        Modifier.offset {
                            IntOffset(
                                pointerX.coerceIn(0, areaWidth - 1),
                                pointerY.coerceIn(0, areaHeight - 1)
                            )
                        }.size(18.dp).background(Color.Cyan, CircleShape)
                    )
                }
            }
        }

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
                // Cap the on-screen list independently from the bounded registry.
                snapshot.streams.takeLast(6).forEach { candidate ->
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            inspectionJob?.cancel()
                            approved = null
                            selectedCaption = null
                            busy = true
                            status = "Checking source availability…"
                            inspectionJob = scope.launch {
                                try {
                                    when (val result = resolver.inspect(candidate, snapshot.captions)) {
                                        is InspectionResult.Playable -> {
                                            approved = result.media
                                            status = "Native playback may be available. Select Play natively."
                                        }
                                        is InspectionResult.Unavailable -> {
                                            status = result.reason
                                        }
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("extras-stream-candidate")
                    ) {
                        Text("${candidate.format} · ${candidate.host}", maxLines = 1)
                    }
                }
            }
            if (busy) CircularProgressIndicator(Modifier.size(22.dp))
            approved?.let { media ->
                media.captions.take(6).forEach { caption ->
                    OutlinedButton(onClick = {
                        selectedCaption = if (selectedCaption?.url == caption.url) null else caption
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "${if (selectedCaption?.url == caption.url) "✓ " else ""}Subtitles: ${caption.format} · ${caption.host}",
                            maxLines = 1
                        )
                    }
                }
                Button(
                    modifier = Modifier.fillMaxWidth().testTag("extras-play-native"),
                    onClick = { nativeMedia = media }
                ) { Text("Play natively") }
            }
        }
    }
}

@Composable
private fun ExtrasMediaForgeNativePlayer(
    media: InspectedMedia,
    caption: CaptionCandidate?,
    title: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val player = remember(media.stream.url) { MediaForgeMedia3.newPlayer(context) }
    DisposableEffect(player, media, caption) {
        player.setMediaItem(MediaForgeMedia3.mediaItem(media, caption))
        player.prepare()
        player.playWhenReady = true
        onDispose {
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
            Text(title, color = Color.White, maxLines = 1)
        }
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = true
                    this.player = player
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
    }
}
