package com.vaydelux.bousygram.presentation.media

import android.app.Activity
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import com.vaydelux.bousygram.core.media.diagnostics.BlackScreenDetector
import com.vaydelux.bousygram.core.media.diagnostics.BlackScreenRecoveryAction
import com.vaydelux.bousygram.core.media.diagnostics.CapabilityProber
import androidx.compose.ui.platform.LocalConfiguration
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import com.vaydelux.bousygram.core.media.SeekThumbnailManager
import com.vaydelux.bousygram.core.media.TdLibDataSource
import com.vaydelux.bousygram.core.media.PlaybackFileRecovery
import com.vaydelux.bousygram.core.cast.CastLanStreamingServer
import com.vaydelux.bousygram.core.cast.CastPlaybackStatus
import com.vaydelux.bousygram.core.cast.CastSessionController
import com.vaydelux.bousygram.core.cast.CastRouteButtonFactory
import com.vaydelux.bousygram.BuildConfig
import com.vaydelux.bousygram.data.store.SessionHealthRecord
import com.vaydelux.bousygram.data.store.StreamHealthTelemetry
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.key
import com.vaydelux.bousygram.core.media.PlaybackState
import kotlin.coroutines.cancellation.CancellationException
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import com.vaydelux.bousygram.core.media.EnginePlaybackState
import com.vaydelux.bousygram.core.media.hasConfirmedAudioOutput
import com.vaydelux.bousygram.core.media.hasInitialMediaOutput
import com.vaydelux.bousygram.core.media.hasConfirmedVideoProgress
import com.vaydelux.bousygram.core.media.ExoPlayerEngine
import com.vaydelux.bousygram.core.media.MediaDurationResolver
import com.vaydelux.bousygram.core.media.shouldFailPlaybackStartup
import com.vaydelux.bousygram.core.media.ContainerHeaderParser
import com.vaydelux.bousygram.core.media.MpegTsDurationParser
import com.vaydelux.bousygram.core.tdlib.server.TelegramChunkScheduler
import com.vaydelux.bousygram.core.tdlib.server.TelegramChunkRequest
import com.vaydelux.bousygram.core.tdlib.server.TelegramChunkResult
import com.vaydelux.bousygram.core.tdlib.server.TelegramChunkPurpose
import com.vaydelux.bousygram.core.media.MediaTrackInfo
import com.vaydelux.bousygram.core.media.MpvPlayerEngine
import com.vaydelux.bousygram.core.media.PlaybackCacheStopReason
import com.vaydelux.bousygram.core.media.PlaybackSession
import com.vaydelux.bousygram.core.media.PlaybackSessionController
import com.vaydelux.bousygram.core.media.PlaybackEngineOwner
import com.vaydelux.bousygram.core.media.PlaybackQueueCoordinator
import com.vaydelux.bousygram.core.media.PlaybackEngineSwitchCoordinator
import com.vaydelux.bousygram.core.media.PlayerEngine
import com.vaydelux.bousygram.core.media.PlayerEngineFactory
import com.vaydelux.bousygram.core.media.StreamingSessionManager
import com.vaydelux.bousygram.core.cache.CacheManager
import com.vaydelux.bousygram.core.media.VideoFormatSupport
import com.vaydelux.bousygram.data.store.SelectedPlayerEngine
import com.vaydelux.bousygram.core.network.AppNetworkState
import com.vaydelux.bousygram.core.network.NetworkConnectivityObserver
import com.vaydelux.bousygram.core.media.UniversalMediaSessionBridge
import com.vaydelux.bousygram.core.tv.WatchNextManager
import com.vaydelux.bousygram.core.observability.PlaybackTelemetryRecord
import com.vaydelux.bousygram.core.observability.PlaybackTelemetryRecorder
import com.vaydelux.bousygram.core.observability.PlaybackErrorMapper
import com.vaydelux.bousygram.core.observability.PlaybackLogContext
import com.vaydelux.bousygram.core.observability.StructuredPlaybackLogger
import com.vaydelux.bousygram.data.store.EnterprisePreferences
import com.vaydelux.bousygram.data.store.PlaybackDiagnostics
import com.vaydelux.bousygram.data.store.StreamMetrics
import com.vaydelux.bousygram.core.tdlib.server.LocalStreamingServer
import com.vaydelux.bousygram.core.tdlib.server.StreamAccessRegistry
import com.vaydelux.bousygram.core.tdlib.server.PlaybackCacheState
import com.vaydelux.bousygram.core.tdlib.server.TelegramPreCacheEngine
import com.vaydelux.bousygram.core.tdlib.utils.PlaybackProgressTracker
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import com.vaydelux.bousygram.presentation.components.BousygramButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val SEEK_INCREMENT_MS = 10_000L
private const val BUFFERING_STALL_MS = 22_000L
private const val MAX_AUTO_RETRIES = 5
private const val CACHE_AHEAD_REFRESH_MS = 10_000L

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    request: PlaybackRequest,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val castState by CastSessionController.state.collectAsStateWithLifecycle()
    val isAndroidTv = remember(context) {
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
    }
    var hasLocalNetworkPermission by remember(context) {
        mutableStateOf(Build.VERSION.SDK_INT < 37 || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_LOCAL_NETWORK
        ) == PackageManager.PERMISSION_GRANTED)
    }
    val localNetworkPermissionRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasLocalNetworkPermission = granted }
    val adaptive = rememberAdaptiveMetrics()
    // Both Leanback capability and TV UI mode are accepted for OEM Android TV devices.
    val tvChrome = isAndroidTv || adaptive.isTv
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val compactHud = adaptive.isCompact
    val queue = remember(request) {
        val seeded = request.queue.ifEmpty { listOf(queueItemFromRequest(request)) }
        (if (queueIndexForRequest(seeded, request.chatId, request.messageId, request.fileId) >= 0) {
            seeded
        } else {
            listOf(queueItemFromRequest(request)) + seeded
        }).toMutableStateList()
    }
    val initialIndex = remember(request, queue) {
        queueIndexForRequest(queue, request.chatId, request.messageId, request.fileId).coerceAtLeast(0)
    }

    val playbackSession = remember(request) {
        PlaybackSessionController.start(
            queue = queue,
            startIndex = initialIndex,
            startPositionMs = 0L,
            onCancelDownloads = { prevFileId ->
                if (prevFileId > 0) {
                    TelegramPreCacheEngine.stopCaching(prevFileId, cancelTdLibDownload = true)
                    com.vaydelux.bousygram.core.tdlib.server.TelegramChunkScheduler.cancelPrefetch(prevFileId)
                    StreamAccessRegistry.revokeFile(prevFileId)
                }
            }
        )
    }
    val queueIndex by playbackSession.currentIndex.collectAsState()
    val sessionState by playbackSession.state.collectAsState()
    var hudVisible by remember { mutableStateOf(true) }
    var hudInteractionTick by remember { mutableIntStateOf(0) }
    var seekScrubbing by remember { mutableStateOf(false) }
    val playerInputController = remember { PlayerInputController() }
    var autoNextCardVisible by remember { mutableStateOf(false) }
    var autoNextCountdown by remember { mutableIntStateOf(5) }
    val playNowFocusRequester = remember { FocusRequester() }
    val seekBarFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    val optionsFocusRequester = remember { FocusRequester() }
    val firstOptionFocusRequester = remember { FocusRequester() }
    val retryFocusRequester = remember { FocusRequester() }
    var optionsPanelVisible by remember { mutableStateOf(false) }
    var tracksPanelVisible by remember { mutableStateOf(false) }
    var episodesDrawerVisible by remember { mutableStateOf(false) }
    var trayFocusedIndex by remember { mutableIntStateOf(0) }
    val trayFirstFocus = remember { FocusRequester() }
    var showBrightnessHud by remember { mutableStateOf(false) }
    var showVolumeHud by remember { mutableStateOf(false) }
    val currentActivity = context as? Activity
    var gestureBrightness by remember {
        mutableFloatStateOf(currentActivity?.window?.attributes?.screenBrightness?.takeIf { it in 0.01f..1f } ?: 0.5f)
    }
    val currentAudioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    var gestureVolume by remember {
        mutableIntStateOf(currentAudioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 5)
    }
    var title by remember(request, queueIndex) {
        val item = queue.getOrElse(queueIndex) { queue.first() }
        mutableStateOf<String>(displayTitle(item))
    }
    var fileName by remember(request, queueIndex) {
        mutableStateOf(queue.getOrElse(queueIndex) { queue.first() }.fileName)
    }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(EnterprisePreferences.preferredSpeed()) }
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(false) }
    var isOpening by remember { mutableStateOf(true) }
    var isReconnecting by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var reloadNonce by remember { mutableIntStateOf(0) }
    var retryCount by remember { mutableIntStateOf(0) }
    var rebufferCount by remember { mutableIntStateOf(0) }
    var lastSavedPosition by remember { mutableLongStateOf(0L) }
    var lastGoodPosition by remember { mutableLongStateOf(0L) }
    var lastAheadPrefetchPosition by remember { mutableLongStateOf(0L) }
    var fileSizeBytes by remember { mutableLongStateOf(0L) }
    var playerStartedAtMs by remember { mutableLongStateOf(0L) }
    var firstFrameMs by remember { mutableLongStateOf(0L) }
    var firstAudioReadyMs by remember { mutableLongStateOf(0L) }
    var firstProgressReadyMs by remember { mutableLongStateOf(0L) }
    var timeToFirstByteMs by remember { mutableLongStateOf(0L) }
    var totalRebufferMs by remember { mutableLongStateOf(0L) }
    var rebufferStartMs by remember { mutableLongStateOf(0L) }
    var playbackSessionId by remember { mutableStateOf("pb_${System.currentTimeMillis()}_$queueIndex") }
    var playbackCommitted by remember { mutableStateOf(false) }
    var decoderName by remember { mutableStateOf("Unknown") }
    var droppedFrames by remember { mutableIntStateOf(0) }
    var cacheState by remember { mutableStateOf(PlaybackCacheState.Preparing) }
    var activeLocalStreamUrl by remember { mutableStateOf<String?>(null) }
    var pendingResumeMs by remember { mutableLongStateOf(0L) }
    var seekPreviewPositionMs by remember { mutableLongStateOf(-1L) }
    var pendingSeekTargetMs by remember { mutableLongStateOf(-1L) }
    var pendingSeekStartedAtMs by remember { mutableLongStateOf(0L) }
    var hasFallenBackToMpv by remember(playbackSessionId) { mutableStateOf(false) }
    var lastHandledEndedTrackIndex by remember { mutableIntStateOf(-1) }
    val playerSessionStartMs = remember { System.currentTimeMillis() }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var userSelectedResizeMode by remember { mutableStateOf<Int?>(null) }

    val effectiveResizeMode = when {
        isLandscape && userSelectedResizeMode == null -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        else -> userSelectedResizeMode ?: AspectRatioFrameLayout.RESIZE_MODE_FIT
    }

    var seekFeedbackText by remember { mutableStateOf<String?>(null) }
    var seekFeedbackSide by remember { mutableIntStateOf(0) }
    var toastFeedbackText by remember { mutableStateOf<String?>(null) }
    var showResumeRestartOption by remember { mutableStateOf(false) }

    fun formatResizeMode(mode: Int): String = when (mode) {
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Fill (Zoom)"
        AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch (Fill)"
        AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Fit (Letterbox)"
        else -> "Fit"
    }

    LaunchedEffect(seekFeedbackText, seekPreviewPositionMs) {
        if (seekFeedbackText != null && seekPreviewPositionMs < 0L) {
            kotlinx.coroutines.delay(850)
            seekFeedbackText = null
        }
    }

    LaunchedEffect(toastFeedbackText) {
        if (toastFeedbackText != null) {
            kotlinx.coroutines.delay(1200)
            toastFeedbackText = null
        }
    }

    fun recordMetrics() {
        PlaybackDiagnostics.recordPlayerMetrics(
            StreamMetrics(
                firstFrameMs = firstFrameMs,
                retryCount = retryCount,
                rebufferCount = rebufferCount,
                decoderName = decoderName,
                droppedFrames = droppedFrames,
                bufferMode = EnterprisePreferences.bufferMode()
            )
        )
    }

    fun applyPlayerDurationMs(candidateMs: Long, engine: PlayerEngine) {
        val item = queue.getOrNull(queueIndex) ?: return
        val trusted = MediaDurationResolver.playerDurationIfTrusted(
            fileId = item.fileId,
            durationMs = candidateMs,
            totalSize = fileSizeBytes,
            hintSeconds = item.durationSeconds,
            seekableContainerDuration = engine.seekableContainerDuration
        )
        if (trusted > duration) duration = trusted
    }

    val initialProbe = remember(queue, initialIndex) {
        val firstItem = queue.getOrNull(initialIndex) ?: queue.firstOrNull()
        if (firstItem != null) {
            CapabilityProber.probe(
                context = context,
                mimeType = firstItem.mimeType,
                fileName = firstItem.fileName,
                title = firstItem.title
            )
        } else null
    }

    val prefSurface = remember { EnterprisePreferences.surfaceTypePreference() }
    var useTextureView by remember {
        mutableStateOf(
            when (prefSurface) {
                "textureview" -> true
                "surfaceview" -> false
                else -> initialProbe?.useTextureView ?: false
            }
        )
    }
    var surfaceDiagnostic by remember { mutableStateOf(SurfaceDiagnosticState()) }
    var debugHudVisible by remember { mutableStateOf(EnterprisePreferences.videoDebugHudEnabled()) }
    val blackScreenDetector = remember { BlackScreenDetector() }

    // One lifecycle owner releases each engine exactly once, including fallback switches,
    // UI exit, and synchronous account invalidation.
    val engineOwner = remember(playbackSession) {
        PlaybackEngineOwner(
            session = playbackSession,
            initialEngine = PlayerEngineFactory.create(
                context = context,
                preferredEngine = initialProbe?.recommendedEngine ?: EnterprisePreferences.selectedPlayerEngine(),
                preferredSpeed = speed
            )
        )
    }
    var playerEngine by engineOwner
    val engineSwitcher = remember(engineOwner, context) {
        PlaybackEngineSwitchCoordinator(engineOwner) { selection, selectedSpeed ->
            PlayerEngineFactory.create(context, selection, selectedSpeed)
        }
    }
    fun requestEngineSwitch(target: SelectedPlayerEngine, feedback: String) {
        val resume = maxOf(position, lastGoodPosition).coerceAtLeast(0L)
        engineSwitcher.switchTo(target, speed).fold(
            onSuccess = {
                lastGoodPosition = resume
                pendingResumeMs = resume
                toastFeedbackText = feedback
                hudVisible = true
                reloadNonce++
            },
            onFailure = { failure ->
                Log.e("PlayerScreen", "Engine switch failed", failure)
                toastFeedbackText = "Unable to switch player. Try again."
                hudVisible = true
            }
        )
    }
    fun togglePlayerEngine() {
        val target = if (playerEngine is ExoPlayerEngine) SelectedPlayerEngine.MPV
                     else SelectedPlayerEngine.ExoPlayer
        requestEngineSwitch(target, "Switching to ${target.name}…")
    }

    val audioFocusController = remember(context) { PlayerAudioFocusController(context) }
    val streamingSessionManager = remember(context) { StreamingSessionManager(context) }
    var castWasConnected by remember { mutableStateOf(false) }
    var lastCastPositionMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(context) {
        if (BuildConfig.PRIVATE_MEDIA_CAST_ENABLED) CastSessionController.initialize(context)
    }

    LaunchedEffect(castState.connected, queueIndex) {
        if (!castState.connected) {
            if (castWasConnected) {
                castWasConnected = false
                playerEngine.seekTo(lastCastPositionMs.coerceAtLeast(0L))
                playerEngine.play()
                isPlaying = true
            }
            return@LaunchedEffect
        }
        castWasConnected = true
        if (!hasLocalNetworkPermission) {
            CastSessionController.endSession()
            return@LaunchedEffect
        }
        val current = queue.getOrNull(queueIndex) ?: return@LaunchedEffect
        val name = current.fileName.orEmpty()
        val mime = VideoFormatSupport.httpContentType(current.mimeType, name, current.title)
        if (!mime.equals("video/mp4", ignoreCase = true) &&
            !name.endsWith(".mp4", ignoreCase = true) && !name.endsWith(".m4v", ignoreCase = true)
        ) {
            android.widget.Toast.makeText(context, "Cast currently supports MP4 media only", android.widget.Toast.LENGTH_LONG).show()
            CastSessionController.endSession()
            return@LaunchedEffect
        }
        val size = current.fileSizeBytes.takeIf { it > 0L } ?: withContext(Dispatchers.IO) {
            com.vaydelux.bousygram.core.tdlib.server.LocalStreamingServer.resolveFileSizeWithin(current.fileId, 1_000L)
        }
        val mediaType = if (mime.equals("application/octet-stream", ignoreCase = true)) "video/mp4" else mime
        val lanUrl = withContext(Dispatchers.IO) {
            CastLanStreamingServer.start(context, current.fileId, size, mediaType)
        }
        if (lanUrl == null || !CastSessionController.loadMedia(
                url = lanUrl,
                title = current.title,
                contentType = mediaType,
                durationMs = duration,
                positionMs = playerEngine.currentPositionMs
            )
        ) {
            android.widget.Toast.makeText(context, "Could not start Cast streaming on this network", android.widget.Toast.LENGTH_LONG).show()
            CastSessionController.endSession()
        }
    }

    LaunchedEffect(castState.connected, castState.positionMs, castState.playbackStatus) {
        if (!castState.connected) return@LaunchedEffect
        lastCastPositionMs = castState.positionMs
        if (castState.durationMs > 0L) duration = castState.durationMs
        position = castState.positionMs
        isPlaying = castState.playbackStatus == CastPlaybackStatus.Playing
        if (castState.playbackStatus == CastPlaybackStatus.Playing) playerEngine.pause()
    }

    fun performTrackedSeek(targetMs: Long) {
        pendingSeekTargetMs = targetMs.coerceAtLeast(0L)
        pendingSeekStartedAtMs = android.os.SystemClock.elapsedRealtime()
        seekPreviewPositionMs = -1L
        playerEngine.seekTo(pendingSeekTargetMs)
    }

    fun commitPlaybackTelemetry(fatalError: String? = null) {
        if (playbackCommitted) return
        playbackCommitted = true
        val current = queue.getOrNull(queueIndex)
        if (rebufferStartMs > 0L) {
            totalRebufferMs += (System.currentTimeMillis() - rebufferStartMs).coerceAtLeast(0L)
            rebufferStartMs = 0L
        }
        val engineName = playerEngine.engineName
        val vDec = playerEngine.videoDecoderName.ifBlank { "Unknown" }
        val aDec = playerEngine.audioDecoderName.ifBlank { "Unknown" }
        val container = VideoFormatSupport.resolveContainer(fileName, current?.mimeType, title)
        val size = if (fileSizeBytes > 0L) fileSizeBytes else (current?.fileSizeBytes ?: 0L)
        val ttfb = when {
            timeToFirstByteMs > 0L -> timeToFirstByteMs
            firstFrameMs > 0L -> firstFrameMs
            else -> 0L
        }
        val fallbackReason = PlaybackTelemetryRecorder.getFallbackReason(playbackSessionId)
        val failure = fatalError ?: playbackError
        val fid = current?.fileId ?: 0
        val cachedAtStart = PlaybackTelemetryRecorder.consumeFullyCachedAtStart(fid)
        val headerTime = PlaybackTelemetryRecorder.consumeHeaderChunkTime(fid)
        val tailTime = PlaybackTelemetryRecorder.consumeTailChunkTime(fid)
        val throughput = PlaybackTelemetryRecorder.consumeThroughput(fid)
            ?: com.vaydelux.bousygram.core.tdlib.server.TelegramChunkScheduler.getAverageThroughput().takeIf { it > 0 }
        val cacheTelemetry = PlaybackTelemetryRecorder.consumeCacheTelemetry(fid)
        val seekTelemetry = PlaybackTelemetryRecorder.consumeSeekTelemetry(fid)
        val streamingTelemetry = PlaybackTelemetryRecorder.consumeStreamingTelemetry(fid)
        val memoryPressure = runCatching {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            val info = android.app.ActivityManager.MemoryInfo()
            manager?.getMemoryInfo(info)
            if (manager == null) null else if (info.lowMemory) {
                "low:${info.availMem / (1024L * 1024L)}MB"
            } else {
                "normal:${info.availMem / (1024L * 1024L)}MB"
            }
        }.getOrNull()

        PlaybackTelemetryRecorder.record(
            PlaybackTelemetryRecord(
                sessionId = playbackSessionId,
                timeToFirstByteMs = ttfb,
                timeToFirstFrameMs = firstFrameMs,
                rebufferCount = rebufferCount,
                totalRebufferMs = totalRebufferMs,
                engine = engineName,
                videoDecoderName = vDec,
                audioDecoderName = aDec,
                container = container,
                fileSize = size,
                failureReason = failure,
                fallbackReason = fallbackReason,
                isFullyCachedAtStart = cachedAtStart,
                timeToHeaderChunkMs = headerTime,
                timeToTailChunkMs = tailTime,
                averageThroughputBytesPerSec = throughput,
                bufferConfig = playerEngine.bufferConfigDescription.takeIf { it.isNotBlank() },
                cacheHitCount = cacheTelemetry.hitCount,
                cacheMissCount = cacheTelemetry.missCount,
                reusedCacheBytes = cacheTelemetry.reusedBytes,
                seekCount = seekTelemetry.count,
                averageSeekLatencyMs = seekTelemetry.averageLatencyMs,
                droppedFrameCount = maxOf(droppedFrames, playerEngine.droppedBufferCount),
                memoryPressure = memoryPressure,
                telegramBytesRequested = streamingTelemetry.telegramBytesRequested,
                localBytesServed = streamingTelemetry.localBytesServed,
                deduplicatedRequestCount = streamingTelemetry.deduplicatedRequestCount,
                supersededWindowCount = streamingTelemetry.supersededWindowCount,
                cancellationReason = streamingTelemetry.cancellationReason
            )
        )
    }

    val videoDimensions by playerEngine.videoDimensions.collectAsState()

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            val win = (context as? Activity)?.window
            if (win != null) {
                val insetsController = WindowCompat.getInsetsController(win, win.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Crash-Safe Proxy Supervision: hot-swap stream URL if Ktor proxy rebinds to a fresh ephemeral port
    LaunchedEffect(playerEngine) {
        com.vaydelux.bousygram.core.tdlib.server.TelegramProxyManager.proxyBaseUrlFlow.collect { newBaseUrl ->
            val currentUrl = activeLocalStreamUrl ?: return@collect
            val oldPrefix = currentUrl.substringBefore('?')
            val newPrefix = newBaseUrl
            if (oldPrefix != newPrefix && oldPrefix.startsWith("http://127.0.0.1:") && newPrefix.startsWith("http://127.0.0.1:")) {
                val query = currentUrl.substringAfter('?', "")
                val hotSwappedUrl = "$newPrefix?$query"
                Log.i("PlayerScreen", "Rebinding the active local stream after proxy restart")
                val pos = playerEngine.currentPositionMs
                activeLocalStreamUrl = hotSwappedUrl
                playerEngine.prepare(hotSwappedUrl)
                if (pos > 0L) playerEngine.seekTo(pos)
                if (isPlaying) playerEngine.play()
            }
        }
    }

    // Token Rotation on Network Change: refresh stream token on Wi-Fi -> Cellular handoff
    LaunchedEffect(Unit) {
        var previousWasCellular = com.vaydelux.bousygram.core.network.NetworkConnectivityObserver.isCellular
        com.vaydelux.bousygram.core.network.NetworkConnectivityObserver.networkState.collect { netState ->
            val isCell = netState.isCellular
            if (!previousWasCellular && isCell) {
                val current = queue.getOrNull(queueIndex)
                if (current != null && activeLocalStreamUrl != null) {
                    val newToken = com.vaydelux.bousygram.core.tdlib.server.StreamAccessRegistry.issueFile(current.fileId)
                    val newUrl = com.vaydelux.bousygram.core.tdlib.server.TelegramProxyManager.getStreamUrl(
                        fileId = current.fileId,
                        size = fileSizeBytes,
                        title = current.title,
                        mimeType = current.mimeType,
                        fileName = current.fileName,
                        token = newToken
                    )
                    Log.i("PlayerScreen", "Rotated the active stream capability after network change")
                    activeLocalStreamUrl = newUrl
                    val pos = playerEngine.currentPositionMs
                    playerEngine.prepare(newUrl)
                    if (pos > 0L) playerEngine.seekTo(pos)
                    if (isPlaying) playerEngine.play()
                }
            }
            previousWasCellular = isCell
        }
    }

    // Seek preview snapshot generation
    LaunchedEffect(queueIndex, duration, activeLocalStreamUrl) {
        val item = queue.getOrNull(queueIndex)
        val url = activeLocalStreamUrl
        if (item != null && duration > 0L && !url.isNullOrBlank()) {
            val key = "${item.chatId}:${item.messageId}"
            SeekThumbnailManager.generateSnapshots(key, url, duration)
        }
    }

    fun stopCurrentCache(reason: PlaybackCacheStopReason) {
        when (reason) {
            // Track transitions keep the same session alive. PlaybackSession.next/previous/
            // goToIndex already cancels the old file through onCancelDownloads.
            PlaybackCacheStopReason.TrackSwitch -> Unit
            PlaybackCacheStopReason.LifecycleStop -> {
                // Backgrounding can pause prefetch without destroying the current episode.
                queue.getOrNull(queueIndex)?.let {
                    TelegramPreCacheEngine.stopCaching(it.fileId, cancelTdLibDownload = false)
                }
            }
            else -> PlaybackSessionController.stop(playbackSession, reason)
        }
    }

    fun revokeLocalPlaybackCapabilities() {
        StreamAccessRegistry.revokeFromUrl(activeLocalStreamUrl)
        activeLocalStreamUrl = null
        StreamAccessRegistry.revokeFromUrl(request.subtitleUrl)
    }

    fun exitPlayback() {
        // Record the position before the engine is closed, not from a disposed player.
        runCatching { commitPlaybackTelemetry() }
            .onFailure { Log.w("BousygramPlayer", "Unable to record exit metrics", it) }
        queue.getOrNull(queueIndex)?.let { item ->
            runCatching {
                val last = playerEngine.currentPositionMs
                if (last > 0L) {
                    PlaybackProgressTracker.saveMessagePosition(
                        item.chatId, item.messageId, last.toInt(), duration,
                        item.fileId, item.title
                    )
                }
            }.onFailure { Log.w("BousygramPlayer", "Unable to persist exit position", it) }
        }
        PlaybackSessionController.setVideoPlaying(false)
        revokeLocalPlaybackCapabilities()
        // Session stop invokes PlaybackEngineOwner.close(), which is idempotent.
        stopCurrentCache(PlaybackCacheStopReason.Back)
        streamingSessionManager.releaseLocks()
        audioFocusController.abandon()
        onExit()
    }

    // Keep every queue operation on the same guarded path, without stopping the session.
    val queueCoordinator = remember(playbackSession) { PlaybackQueueCoordinator(playbackSession) }
    fun moveToTrack(index: Int): Boolean = queueCoordinator.moveTo(
        index = index,
        onLeaveCurrent = { old ->
            lastHandledEndedTrackIndex = playbackSession.currentIndex.value
            StreamAccessRegistry.revokeFromUrl(activeLocalStreamUrl)
            activeLocalStreamUrl = null
            StreamAccessRegistry.revokeFile(old.fileId)
            val lastPosition = playerEngine.currentPositionMs
            if (lastPosition > 0L) {
                runCatching {
                    PlaybackProgressTracker.saveMessagePosition(
                        old.chatId, old.messageId, lastPosition.toInt(),
                        duration, old.fileId, old.title
                    )
                }.onFailure { Log.w("PlayerScreen", "Could not persist episode progress", it) }
            }
        },
        onResetAndStop = {
            reloadNonce = 0
            retryCount = 0
            pendingResumeMs = 0L
            seekPreviewPositionMs = -1L
            pendingSeekTargetMs = -1L
            pendingSeekStartedAtMs = 0L
            isReconnecting = false
            firstFrameMs = 0L
            isOpening = true
            isBuffering = true
            isPlaying = false
            position = 0L
            duration = 0L
            playbackError = null
            playerEngine.stop()
            playerEngine.detachSurface()
        }
    )
    fun goToTrack(newIndex: Int) { moveToTrack(newIndex) }
    fun skipNext(): Boolean = moveToTrack(playbackSession.currentIndex.value + 1)
    fun skipPrevious(): Boolean = moveToTrack(playbackSession.currentIndex.value - 1)

    val mediaSessionBridge = remember(playerEngine) {
        UniversalMediaSessionBridge(
            context = context,
            playerEngine = playerEngine,
            onSeekIncrement = { deltaMs ->
                val target = relativePlayerSeekTarget(playerEngine.currentPositionMs, deltaMs, duration)
                performTrackedSeek(target)
                if (duration > 0L) queue.getOrNull(queueIndex)?.let {
                    CacheManager.onSeek(it.fileId, target, fileSizeBytes, duration)
                }
                position = target
            },
            onSkipNext = {
                skipNext()
            },
            onSkipPrevious = {
                skipPrevious()
            }
        )
    }

    // Release the old MediaSession before its player engine is replaced; release again
    // on composition disposal is safe because registerEngineResource returns a once-only closer.
    DisposableEffect(engineOwner, playerEngine, mediaSessionBridge) {
        val closeBridge = engineOwner.registerEngineResource(playerEngine) { mediaSessionBridge.release() }
        onDispose { closeBridge() }
    }

    LaunchedEffect(isPlaying, position, speed, title, duration) {
        PlaybackSessionController.setVideoPlaying(isPlaying)
        mediaSessionBridge.updateState(
            isPlaying = isPlaying,
            positionMs = position,
            speed = speed,
            title = title,
            durationMs = duration
        )
    }

    LaunchedEffect(playerEngine, queueIndex) {
        fun recordInitialOutput(audioOnly: Boolean) {
            if (hasInitialMediaOutput(firstFrameMs, firstAudioReadyMs, firstProgressReadyMs) ||
                playerStartedAtMs <= 0L
            ) return
            val effectiveStartMs = playerStartedAtMs
            val elapsedMs = (System.currentTimeMillis() - effectiveStartMs).coerceAtLeast(1L)
            if (audioOnly) firstAudioReadyMs = elapsedMs else firstFrameMs = elapsedMs
            isOpening = false
            isReconnecting = false
            isBuffering = false
            playbackError = null
            decoderName = playerEngine.activeDecoder
            if (timeToFirstByteMs == 0L) {
                val fid = queue.getOrNull(queueIndex)?.fileId ?: 0
                val ttfb = PlaybackTelemetryRecorder.consumeFirstByteElapsed(fid, effectiveStartMs)
                timeToFirstByteMs = if (ttfb > 0L) ttfb else elapsedMs
            }
            recordMetrics()
            val current = queue.getOrNull(queueIndex)
            if (current != null && fileSizeBytes > 0L) {
                TelegramPreCacheEngine.cacheFullVideoOnWifi(context, current.fileId, fileSizeBytes)
            }
            // Audio-only playback has no rendered video frame: never pollute video-frame
            // telemetry with audio readiness, but still mark the session as playing.
            if (!audioOnly) PlaybackSessionController.recordFirstFrame(playbackSession, firstFrameMs)
            if (current != null) playbackSession.onPlaying(current)
        }

        fun declaredAudioOnly(): Boolean {
            val active = queue.getOrNull(queueIndex) ?: return false
            val mime = active.mimeType.orEmpty()
            val name = active.fileName.orEmpty().lowercase(java.util.Locale.ROOT)
            return mime.startsWith("audio/", ignoreCase = true) ||
                name.endsWith(".mp3") || name.endsWith(".m4a") || name.endsWith(".flac") ||
                name.endsWith(".opus") || name.endsWith(".ogg") || name.endsWith(".wav")
        }

        fun confirmAudioOutput() {
            if (!isOpening || hasInitialMediaOutput(firstFrameMs, firstAudioReadyMs, firstProgressReadyMs)) return
            if (hasConfirmedAudioOutput(
                    playerEngine.playbackState.value, playerEngine.tracks.value,
                    playerEngine.isPlaying, playerEngine.playWhenReady, declaredAudioOnly()
                )
            ) recordInitialOutput(audioOnly = true)
        }

        // Video first-frame callbacks and audio readiness are different output signals.
        // Track changes can arrive before or after the player reaches Ready.
        launch {
            while (true) {
                delay(300L)
                confirmAudioOutput()
            }
        }

        // ExoPlayer reports an actual video output event; readiness alone can leave a black screen.
        // libmpv has no equivalent reliable callback in this wrapper, so Ready is its fallback.
        playerEngine.firstFrameRendered?.let { frames ->
            launch {
                frames.collect { rendered ->
                    if (rendered && (isOpening || (playbackError != null && playerEngine.isPlaying))) {
                        recordInitialOutput(audioOnly = false)
                    }
                }
            }
        }
        playerEngine.playbackState.collect { state ->
            when (state) {
                is EnginePlaybackState.Buffering -> {
                    isBuffering = true
                    if (hasInitialMediaOutput(firstFrameMs, firstAudioReadyMs, firstProgressReadyMs)) {
                        rebufferCount++
                        if (rebufferStartMs == 0L) {
                            rebufferStartMs = System.currentTimeMillis()
                        }
                        recordMetrics()
                    }
                }
                is EnginePlaybackState.Ready -> {
                    isBuffering = false
                    // Keep the loading banner until ExoPlayer renders a real frame.
                    confirmAudioOutput()
                    if (playerEngine.firstFrameRendered == null && !declaredAudioOnly() &&
                        !hasInitialMediaOutput(firstFrameMs, firstAudioReadyMs, firstProgressReadyMs)
                    ) {
                        // Existing libmpv fallback has no rendered-frame callback.
                        recordInitialOutput(audioOnly = false)
                    }
                    if (hasInitialMediaOutput(firstFrameMs, firstAudioReadyMs, firstProgressReadyMs)) {
                        isOpening = false
                        isReconnecting = false
                    }
                    playbackError = null
                    if (rebufferStartMs > 0L) {
                        totalRebufferMs += (System.currentTimeMillis() - rebufferStartMs).coerceAtLeast(0L)
                        rebufferStartMs = 0L
                    }
                    if (playerEngine.durationMs > 0L) {
                        applyPlayerDurationMs(playerEngine.durationMs, playerEngine)
                    }
                    // Preserve autoplay intent across asynchronous MKV demux/decoder readiness.
                    // An explicit user pause clears playWhenReady and is never overridden.
                    if (playerEngine.playWhenReady && !playerEngine.isPlaying) playerEngine.play()
                    isPlaying = playerEngine.isPlaying
                    if (isPlaying) audioFocusController.request(playerEngine)

                    val effectiveStartMs = if (playerStartedAtMs > 0L) playerStartedAtMs else playerSessionStartMs
                    confirmAudioOutput()

                    val resume = pendingResumeMs
                    if (resume > 0L) {
                        pendingResumeMs = 0L
                        playerEngine.seekTo(resume)
                        if (resume > 15_000L) {
                            showResumeRestartOption = true
                        }
                    }
                }
                is EnginePlaybackState.Ended -> {
                    isBuffering = false
                    isPlaying = false
                    val current = queue.getOrNull(queueIndex) ?: return@collect
                    if (lastHandledEndedTrackIndex == queueIndex) {
                        Log.d("PlayerScreen", "Ignoring duplicate Ended event for index $queueIndex")
                        return@collect
                    }
                    // Auto-Next Guard: Ensure video reached actual conclusion before advancing
                    val reachedEnd = duration > 5_000L && playerEngine.currentPositionMs >= (duration - 5_000L)
                    if (reachedEnd) {
                        lastHandledEndedTrackIndex = queueIndex
                        commitPlaybackTelemetry(fatalError = null)
                        PlaybackProgressTracker.clearMessagePosition(current.chatId, current.messageId)
                        scope.launch {
                            WatchNextManager.removeFromWatchNext(
                                context,
                                "bousy_${current.chatId}_${current.messageId}_${current.fileId}"
                            )
                        }
                        if (playbackSession.hasNext) {
                            autoNextCountdown = 5
                            autoNextCardVisible = true
                            playerInputController.isAutoNextActive = true
                        } else {
                            playbackSession.onEnded(current)
                            exitPlayback()
                        }
                    } else {
                        Log.w("PlayerScreen", "Premature EOF detected at ${playerEngine.currentPositionMs}ms of ${duration}ms; guarding against premature track skip")
                        isReconnecting = true
                        isBuffering = true
                        reloadNonce++
                    }
                }
                is EnginePlaybackState.Error -> {
                    isBuffering = false
                    val message = state.message
                    EnterprisePreferences.setLastError(message)
                    PlaybackDiagnostics.recordStreamError(message)
                    val item = queue.getOrNull(queueIndex)
                    val retryPosition = maxOf(playerEngine.currentPositionMs, lastGoodPosition).coerceAtLeast(0L)
                    lastGoodPosition = retryPosition
                    hudVisible = true
                    val playbackEx = (state.cause as? PlaybackException)
                        ?: PlaybackException(state.message, state.cause, PlaybackException.ERROR_CODE_UNSPECIFIED)
                    // Keep diagnostics for transient decoder/demux failures, but do not
                    // mark the playback session terminal before MPV fallback or retry.
                    PlaybackSessionController.recordError(playbackSession, playbackEx, retryCount)

                    // Automatic fallback to libmpv on decoder, container, or unsupported-format errors.
                    // Resumes at the same position, falls back strictly once per session, and records reason in telemetry.
                    val isFallbackCandidate = VideoFormatSupport.isExoPlayerFallbackCandidate(state.cause, state.message)
                    if (playerEngine is ExoPlayerEngine && !hasFallenBackToMpv && isFallbackCandidate) {
                        hasFallenBackToMpv = true
                        val fallbackReason = VideoFormatSupport.classifyFallbackReason(state.cause, state.message)
                        Log.w("PlayerScreen", "ExoPlayer error ($message). Executing automatic fallback to libmpv at $retryPosition ms. Reason: $fallbackReason")

                        PlaybackTelemetryRecorder.recordFallback(playbackSessionId, fallbackReason)
                        PlaybackDiagnostics.recordStreamError("FallbackToMpv: $fallbackReason")

                        engineSwitcher.fallback {
                            PlayerEngineFactory.createFallback(context, playerEngine, speed)
                        }.fold(
                            onSuccess = { (_, pos) ->
                                pendingResumeMs = if (pos > 0L) pos else retryPosition
                                lastGoodPosition = pendingResumeMs
                                retryCount = 1
                                isReconnecting = true
                                isBuffering = true
                                playbackError = null
                                hudVisible = false
                                reloadNonce++
                            },
                            onFailure = { failure ->
                                // A missing/failed native decoder must not crash the Compose UI.
                                // Keep the player open and offer its existing retry/exit actions.
                                Log.e("PlayerScreen", "Automatic playback fallback failed", failure)
                                isReconnecting = false
                                isBuffering = false
                                playbackError = "Unable to initialize a compatible video player. Try again."
                                playbackSession.onError(playbackError!!, failure, item)
                                commitPlaybackTelemetry(fatalError = playbackError)
                            }
                        )
                    } else if (item != null && retryCount < MAX_AUTO_RETRIES) {
                        retryCount++
                        recordMetrics()
                        isReconnecting = true
                        isBuffering = true
                        playbackError = null
                        scope.launch {
                            // The prepare effect re-resolves and applies the refreshed TDLib fileId.
                            delay(maxOf(autoRetryDelayMs(retryCount), 2_000L))
                            if (queue.getOrNull(queueIndex)?.fileId == item.fileId && playbackError == null) {
                                reloadNonce++
                            }
                        }
                    } else {
                        isReconnecting = false
                        playbackError = message
                        playbackSession.onError(message, state.cause, item)
                        commitPlaybackTelemetry(fatalError = message)
                    }
                }
                is EnginePlaybackState.Idle -> Unit
            }
        }
    }

    LaunchedEffect(playerEngine) {
        NetworkConnectivityObserver.networkState.collect { net ->
            playerEngine.updateNetworkState(net.isCellular)
        }
    }

    // Session-level resources must not be torn down when ExoPlayer swaps to mpv.
    // Updated state gives the stable observer the newest engine and queue position.
    val activeLifecycleEngine by rememberUpdatedState(playerEngine)
    val activeLifecycleQueueIndex by rememberUpdatedState(queueIndex)
    val activeLifecycleDuration by rememberUpdatedState(duration)
    val activeLifecycleIsPlaying by rememberUpdatedState(isPlaying)
    DisposableEffect(lifecycleOwner, playbackSession, streamingSessionManager) {
        streamingSessionManager.acquireLocks()
        val activity = context as? androidx.activity.ComponentActivity
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> streamingSessionManager.acquireLocks()
                Lifecycle.Event.ON_PAUSE -> {
                    if (activeLifecycleEngine.isPlaying) {
                        activeLifecycleEngine.pause()
                    }
                    queue.getOrNull(activeLifecycleQueueIndex)?.let { current ->
                        if (activeLifecycleEngine.currentPositionMs > 0) {
                            PlaybackProgressTracker.saveMessagePosition(
                                current.chatId,
                                current.messageId,
                                activeLifecycleEngine.currentPositionMs.toInt(),
                                activeLifecycleDuration,
                                current.fileId,
                                current.title
                            )
                        }
                    }
                }
                Lifecycle.Event.ON_RESUME -> if (activeLifecycleIsPlaying) activeLifecycleEngine.play()
                Lifecycle.Event.ON_STOP -> {
                    stopCurrentCache(PlaybackCacheStopReason.LifecycleStop)
                    streamingSessionManager.releaseLocks()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Explicit Back/logout may already have disposed the player engine.
            if (!playbackSession.isStopped()) {
                runCatching { commitPlaybackTelemetry() }
                    .onFailure { Log.w("BousygramPlayer", "Unable to record disposal metrics", it) }
                val current = queue.getOrNull(activeLifecycleQueueIndex)
            val totalPlayTime = System.currentTimeMillis() - playerSessionStartMs
            if (current != null && totalPlayTime > 1_000L) {
                val rebufferTime = rebufferCount * 1_500L
                val rebufferRatio = if (totalPlayTime > 0) (rebufferTime.toFloat() / totalPlayTime.toFloat()).coerceIn(0f, 1f) else 0f
                StreamHealthTelemetry.recordSession(
                    SessionHealthRecord(
                        sessionId = "sess_${System.currentTimeMillis()}",
                        fileId = current.fileId,
                        ttfbMs = firstFrameMs.takeIf { it > 0 } ?: 0L,
                        stallCount = rebufferCount,
                        totalPlayTimeMs = totalPlayTime,
                        totalRebufferTimeMs = rebufferTime,
                        rebufferRatio = rebufferRatio,
                        networkType = if (com.vaydelux.bousygram.core.network.NetworkConnectivityObserver.isCellular) "Cellular" else "WiFi",
                        decoderName = decoderName,
                        engine = if (activeLifecycleEngine is ExoPlayerEngine) "ExoPlayer" else "libmpv",
                        bufferConfig = activeLifecycleEngine.bufferConfigDescription.takeIf { it.isNotBlank() }
                    )
                )
            }
            if (current != null && activeLifecycleEngine.currentPositionMs > 0) {
                PlaybackProgressTracker.saveMessagePosition(
                    current.chatId,
                    current.messageId,
                    activeLifecycleEngine.currentPositionMs.toInt(),
                    activeLifecycleDuration,
                    current.fileId,
                    current.title
                )
                scope.launch {
                    WatchNextManager.updateProgress(
                        context = context,
                        chatId = current.chatId,
                        messageId = current.messageId,
                        fileId = current.fileId,
                        title = current.title,
                        currentPositionMs = activeLifecycleEngine.currentPositionMs,
                        durationMs = activeLifecycleDuration,
                        thumbnailPath = null
                    )
                }
            }
            }
            // Controller invokes the engine owner's once-only shutdown; do not release
            // the same engine a second time or mark the session stopped on fallback.
            PlaybackSessionController.setVideoPlaying(false)
            revokeLocalPlaybackCapabilities()
            stopCurrentCache(PlaybackCacheStopReason.Dispose)
            streamingSessionManager.releaseLocks()
            audioFocusController.abandon()
            com.vaydelux.bousygram.core.tdlib.cache.TelegramCachePruner.pruneCacheIfNecessary(
                context = context,
                tdLibCacheDir = java.io.File(context.filesDir, "tdlib_files")
            )
        }
    }

    LaunchedEffect(request, queueIndex) {
        if (!playbackCommitted && playerStartedAtMs > 0L) {
            commitPlaybackTelemetry()
        }
        playbackSessionId = "pb_${System.currentTimeMillis()}_$queueIndex"
        playbackCommitted = false
        hasFallenBackToMpv = false
        timeToFirstByteMs = 0L
        totalRebufferMs = 0L
        rebufferStartMs = 0L
        retryCount = 0
        rebufferCount = 0
        firstFrameMs = 0L
        firstAudioReadyMs = 0L
        firstProgressReadyMs = 0L
        droppedFrames = 0
        decoderName = "Unknown"
        lastGoodPosition = 0L
        lastAheadPrefetchPosition = 0L
        cacheState = PlaybackCacheState.Preparing
        isReconnecting = false
        playbackError = null
        recordMetrics()
    }

    LaunchedEffect(request, queueIndex, reloadNonce) {
        // A retry or track change replaces the old URL; revoke its capability first.
        // Lifecycle backgrounding does not run this effect, so a paused player can resume.
        firstAudioReadyMs = 0L
        firstProgressReadyMs = 0L
        firstFrameMs = 0L
        playerStartedAtMs = 0L
        StreamAccessRegistry.revokeFromUrl(activeLocalStreamUrl)
        activeLocalStreamUrl = null
        val selected = queue[queueIndex]
        val owner = com.vaydelux.bousygram.core.account.AccountSession.current()
        if (owner == null) {
            playbackError = "Telegram session is not ready. Return to the library and try again."
            isOpening = false
            isReconnecting = false
            isBuffering = false
            hudVisible = true
            return@LaunchedEffect
        }
        // Resolve against the actual message before using any persisted file ID. A
        // TDLib exception or timeout must fall back safely, not hang this effect.
        val refreshed = withContext(Dispatchers.IO) {
            PlaybackFileRecovery(
                com.vaydelux.bousygram.core.tdlib.server.TelegramFileGatewayRegistry.current
            ).refresh(selected.chatId, selected.messageId, selected.fileId)
        }
        if (!com.vaydelux.bousygram.core.account.AccountSession.isCurrent(owner)) {
            playbackError = "Telegram session changed. Return to the library and try again."
            isOpening = false
            isReconnecting = false
            isBuffering = false
            hudVisible = true
            return@LaunchedEffect
        }
        if (refreshed != null) {
            com.vaydelux.bousygram.core.tdlib.server.TelegramChunkScheduler.onFileUpdated(refreshed)
            val newSize = refreshed.size.takeIf { it > 0L }
                ?: refreshed.expectedSize.takeIf { it > 0L }
                ?: selected.fileSizeBytes
            if (refreshed.fileId != selected.fileId) {
                val rebound = PlaybackSessionController.rebindActiveFile(
                    playbackSession, selected.fileId, refreshed.fileId
                )
                if (!rebound) {
                    playbackError = "Playback session changed. Return to the library and try again."
                    isOpening = false
                    isReconnecting = false
                    isBuffering = false
                    hudVisible = true
                    return@LaunchedEffect
                }
                Log.i("PlayerScreen", "Refreshed Telegram file identity after reopening")
            }
            if (refreshed.fileId != selected.fileId || newSize != selected.fileSizeBytes) {
                queue[queueIndex] = selected.copy(
                    fileId = refreshed.fileId, fileSizeBytes = newSize
                )
            }
        }
        val item = queue[queueIndex]
        com.vaydelux.bousygram.core.tdlib.server.TelegramChunkScheduler
            .noteFileContext(item.fileId, item.chatId, item.messageId)
        // Reset before stream preparation so scheduler preflight metrics (including the
        // fully-cached-at-start verdict) are retained for this playback attempt.
        PlaybackTelemetryRecorder.clearFirstByte(item.fileId)
        title = displayTitle(item)
        fileName = item.fileName
        playbackError = null
        isOpening = true
        isReconnecting = retryCount > 0
        isBuffering = true
        duration = 0L
        MediaDurationResolver.cachedDurationMs(item.fileId)
            .takeIf { it > 0L }
            ?.let { duration = it }
            ?: item.durationSeconds.takeIf { it > 0 }?.let { duration = it * 1000L }
        lastSavedPosition = 0L
        val videoMime = VideoFormatSupport.exoMimeType(item.mimeType, item.fileName, item.title)
        val isExo = playerEngine is ExoPlayerEngine
        val streamUrl = if (isExo) {
            val resolvedSize = item.fileSizeBytes.takeIf { it > 0L }
                ?: withContext(Dispatchers.IO) { LocalStreamingServer.resolveFileSizeWithin(item.fileId, 750L) }
            fileSizeBytes = resolvedSize
            cacheState = TelegramPreCacheEngine.cacheState(item.fileId)
            activeLocalStreamUrl = null
            TdLibDataSource.buildUri(item.fileId, resolvedSize, item.fileName, videoMime).toString()
        } else {
            // MpvPlayerEngine path uses LocalStreamingServer with per-file capability token
            val alreadyPreparedUrl = request.initialStreamUrl?.takeIf {
                queueIndex == initialIndex && reloadNonce == 0 &&
                    item.fileId == request.fileId &&
                    it.startsWith("http://127.0.0.1")
            }
            val preparation = if (alreadyPreparedUrl != null) {
                null
            } else {
                withContext(Dispatchers.IO) {
                    LocalStreamingServer.prepareStream(
                        fileId = item.fileId,
                        declaredSize = item.fileSizeBytes,
                        title = item.title,
                        mimeType = item.mimeType,
                        fileName = item.fileName,
                        chatId = item.chatId,
                        messageId = item.messageId
                    )
                }
            }
            if (alreadyPreparedUrl == null && preparation == null) {
                val message = "Stream not ready. The file may still be downloading in Telegram."
                playbackSession.onError(message, null, item)
                playbackError = message
                PlaybackDiagnostics.recordStreamError(message)
                commitPlaybackTelemetry(fatalError = message)
                isOpening = false
                isReconnecting = false
                isBuffering = false
                hudVisible = true
                return@LaunchedEffect
            }
            if (preparation != null) {
                fileSizeBytes = preparation.resolvedSize
                cacheState = TelegramPreCacheEngine.cacheState(item.fileId)
            } else {
                val sizeFromPreparedUrl = alreadyPreparedUrl
                    ?.substringAfter("size=", "")
                    ?.substringBefore('&')
                    ?.toLongOrNull()
                fileSizeBytes = item.fileSizeBytes.takeIf { it > 0L }
                    ?: sizeFromPreparedUrl?.takeIf { it > 0L }
                    ?: 0L
                cacheState = TelegramPreCacheEngine.cacheState(item.fileId)
            }
            val resolvedUrl = alreadyPreparedUrl ?: preparation!!.url
            activeLocalStreamUrl = resolvedUrl.takeIf { it.startsWith("http://127.0.0.1") }
            resolvedUrl
        }
        val resumePosition = lastGoodPosition
            .takeIf { it > 0L }
            ?: PlaybackProgressTracker.getMessagePosition(item.chatId, item.messageId, duration).toLong()
        playbackSession.onPreparing(item)
        playbackSession.currentPositionMs = resumePosition
        playerEngine.stop()

        firstFrameMs = 0L
        firstAudioReadyMs = 0L
        firstProgressReadyMs = 0L
        playerStartedAtMs = System.currentTimeMillis()
        pendingResumeMs = resumePosition
        playerEngine.prepare(streamUrl)
        playerEngine.setPlaybackSpeed(speed)
        playerEngine.play()
        isPlaying = true
    }

    LaunchedEffect(Unit) {
        if (hudVisible) {
            runCatching { playPauseFocusRequester.requestFocus() }
        } else {
            runCatching { focusRequester.requestFocus() }
        }
    }

    // Recover real duration from already-cached container bytes without delaying
    // video playback or requesting a large new TS tail download. MKV Segment Info
    // normally lives at the head; MPEG-TS can be estimated from actual PCRs only
    // when both head and tail data are already available. Never guess from file size.
    LaunchedEffect(request, queueIndex, reloadNonce) {
        delay(1_500L)
        val owner = com.vaydelux.bousygram.core.account.AccountSession.current() ?: return@LaunchedEffect
        val current = queue.getOrNull(queueIndex) ?: return@LaunchedEffect
        val container = VideoFormatSupport.resolveContainer(current.fileName, current.mimeType, current.title)
        val mkv = container == "mkv" || container == "webm"
        val ts = container == "ts" || container == "mts" || container == "m2ts" ||
            VideoFormatSupport.isTransportStream(current.fileName, current.mimeType)
        if (!mkv && !ts) return@LaunchedEffect
        repeat(12) {
            if (!com.vaydelux.bousygram.core.account.AccountSession.isCurrent(owner) ||
                duration > 0L || playbackError != null) return@LaunchedEffect
            val head = kotlinx.coroutines.withTimeoutOrNull(1_500L) {
                withContext(Dispatchers.IO) {
                    TelegramChunkScheduler.fetchChunk(
                        TelegramChunkRequest(
                            current.fileId, 0L, 256 * 1024, 16, TelegramChunkPurpose.Metadata
                        )
                    )
                }
            }
            val headBytes = (head as? TelegramChunkResult.Ready)?.bytes
            val parsedDuration = if (mkv) {
                headBytes?.let(ContainerHeaderParser::parseMkvDurationMs)
            } else {
                val totalSize = fileSizeBytes.takeIf { it > 256 * 1024L }
                    ?: current.fileSizeBytes.takeIf { it > 256 * 1024L } ?: 0L
                val tail = if (totalSize > 0L && headBytes != null) {
                    kotlinx.coroutines.withTimeoutOrNull(1_500L) {
                        withContext(Dispatchers.IO) {
                            TelegramChunkScheduler.fetchChunk(
                                TelegramChunkRequest(
                                    current.fileId, totalSize - 256 * 1024L,
                                    256 * 1024, 16, TelegramChunkPurpose.Metadata
                                )
                            )
                        }
                    }
                } else null
                val tailBytes = (tail as? TelegramChunkResult.Ready)?.bytes
                if (headBytes != null && tailBytes != null) {
                    MpegTsDurationParser.durationMs(headBytes, tailBytes)
                } else null
            }
            if (parsedDuration != null &&
                com.vaydelux.bousygram.core.account.AccountSession.isCurrent(owner) &&
                queue.getOrNull(queueIndex)?.fileId == current.fileId
            ) {
                MediaDurationResolver.rememberTrustedDuration(
                    current.fileId, parsedDuration, MediaDurationResolver.DurationSource.Stream
                )
                if (parsedDuration > duration) duration = parsedDuration
                return@LaunchedEffect
            }
            delay(2_000L)
        }
    }

    // A stalled Matroska startup may be a decoder/extractor incompatibility,
    // not a network outage. Try the independently implemented libmpv path once,
    // before the final 45s watchdog reports a fatal startup error.
    LaunchedEffect(request, queueIndex, reloadNonce) {
        while (playerStartedAtMs <= 0L && playbackError == null) delay(250L)
        if (playerStartedAtMs <= 0L || playbackError != null) return@LaunchedEffect
        val startedAt = playerStartedAtMs
        val trackedEngine = playerEngine
        while (playbackError == null && playerEngine === trackedEngine &&
            !hasInitialMediaOutput(firstFrameMs, firstAudioReadyMs, firstProgressReadyMs) &&
            System.currentTimeMillis() - startedAt < 20_000L
        ) delay(500L)
        val current = queue.getOrNull(queueIndex) ?: return@LaunchedEffect
        val container = VideoFormatSupport.resolveContainer(
            current.fileName, current.mimeType, current.title
        )
        val needsCompatibility = container in setOf("mkv", "webm", "ts", "mts", "m2ts") ||
            VideoFormatSupport.isTransportStream(current.fileName, current.mimeType)
        if (needsCompatibility && playerEngine === trackedEngine &&
            playerEngine is ExoPlayerEngine && !hasFallenBackToMpv &&
            !hasInitialMediaOutput(firstFrameMs, firstAudioReadyMs, firstProgressReadyMs) && playbackError == null
        ) {
            hasFallenBackToMpv = true
            PlaybackDiagnostics.recordStreamError(
                "ExoPlayer progressive container startup stalled; attempting libmpv fallback"
            )
            requestEngineSwitch(
                SelectedPlayerEngine.MPV,
                "Video startup stalled. Trying compatibility player…"
            )
        }
    }

    // Do not leave users trapped in Preparing / Reconnecting indefinitely after
    // an app restart or stale native Telegram file state. This timer is restarted
    // for each media item and explicit retry, but never by buffering UI flickers.
    LaunchedEffect(request, queueIndex, reloadNonce) {
        // Start counting when the engine actually begins preparing, not while TDLib
        // is still resolving a message and its cached file metadata.
        while (playerStartedAtMs <= 0L && playbackError == null) delay(250L)
        val trackedEngine = playerEngine
        val trackedStartMs = playerStartedAtMs
        while (playerEngine === trackedEngine && playerStartedAtMs == trackedStartMs &&
            playbackError == null && !hasInitialMediaOutput(firstFrameMs, firstAudioReadyMs, firstProgressReadyMs) &&
            System.currentTimeMillis() - trackedStartMs < 45_000L
        ) delay(500L)
        if (shouldFailPlaybackStartup(
                sameEngine = playerEngine === trackedEngine,
                sameStartTimestamp = playerStartedAtMs == trackedStartMs,
                hasInitialOutput = hasInitialMediaOutput(firstFrameMs, firstAudioReadyMs, firstProgressReadyMs),
                hasPlaybackError = playbackError != null,
                startedAtMs = playerStartedAtMs
            )
        ) {
            PlaybackDiagnostics.recordStreamError("Playback startup exceeded 45s without video frame or audio readiness")
            playerEngine.stop()
            isOpening = false
            isReconnecting = false
            isBuffering = false
            playbackError = "Telegram media could not start. Check your connection, then Retry. If it continues, reopen the library."
            hudVisible = true
        }
    }

    LaunchedEffect(hudVisible, tracksPanelVisible, episodesDrawerVisible, optionsPanelVisible, autoNextCardVisible) {
        playerInputController.isHudVisible = hudVisible
        playerInputController.activeMenu = when {
            tracksPanelVisible -> PlayerMenuType.TRACKS
            episodesDrawerVisible -> PlayerMenuType.EPISODES
            optionsPanelVisible -> PlayerMenuType.OPTIONS
            else -> PlayerMenuType.NONE
        }
        playerInputController.isAutoNextActive = autoNextCardVisible
    }

    // A track transition or playback failure must not strand Retry behind Options.
    LaunchedEffect(queueIndex) {
        optionsPanelVisible = false
    }
    LaunchedEffect(playbackError) {
        if (playbackError != null) {
            optionsPanelVisible = false
            hudVisible = true
            playerInputController.activeMenu = PlayerMenuType.NONE
            delay(80L)
            runCatching { retryFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(autoNextCardVisible) {
        if (autoNextCardVisible) {
            autoNextCountdown = 5
            playerInputController.isAutoNextActive = true
            delay(150L)
            runCatching { playNowFocusRequester.requestFocus() }
            while (autoNextCountdown > 0) {
                delay(1000L)
                autoNextCountdown--
            }
            if (autoNextCardVisible) {
                autoNextCardVisible = false
                playerInputController.isAutoNextActive = false
                if (!skipNext()) {
                    toastFeedbackText = "No next video"
                    hudVisible = true
                }
            }
        }
    }

    val menuOpen = tracksPanelVisible || episodesDrawerVisible || optionsPanelVisible
    LaunchedEffect(hudVisible, isPlaying, isBuffering, playbackError, menuOpen, seekScrubbing, autoNextCardVisible, queueIndex, hudInteractionTick) {
        if (shouldAutoHidePlayerHud(hudVisible, isPlaying, isBuffering, playbackError != null, menuOpen, seekScrubbing, autoNextCardVisible)) {
            delay(4_000L)
            // State keys cancel the timer on interaction, scrub, buffering, or menu changes.
            if (shouldAutoHidePlayerHud(hudVisible, isPlaying, isBuffering, playbackError != null, menuOpen, seekScrubbing, autoNextCardVisible)) {
                hudVisible = false
                playerInputController.isHudVisible = false
            }
        }
    }

    LaunchedEffect(playerEngine, queueIndex) {
        var naturalProgressAnchorMs = -1L
        var progressAnchorTickMs = 0L
        while (true) {
            val observedPosition = playerEngine.currentPositionMs.coerceAtLeast(0L)
            val nowMs = android.os.SystemClock.elapsedRealtime()
            val playingVideo = playerEngine.isPlaying &&
                playerEngine.videoDimensions.value.let { it.first > 0 && it.second > 0 }
            if (!playingVideo) {
                naturalProgressAnchorMs = -1L
                progressAnchorTickMs = 0L
            } else if (naturalProgressAnchorMs < 0L ||
                observedPosition < naturalProgressAnchorMs ||
                observedPosition - naturalProgressAnchorMs > 15_000L
            ) {
                // Ignore discontinuities / seeks: a first position is not proof of progress.
                naturalProgressAnchorMs = observedPosition
                progressAnchorTickMs = nowMs
            } else if (progressAnchorTickMs > 0L && nowMs - progressAnchorTickMs >= 1_500L &&
                hasConfirmedVideoProgress(
                    naturalProgressAnchorMs, observedPosition, true, true
                ) && playerStartedAtMs > 0L && firstProgressReadyMs == 0L && firstFrameMs == 0L
            ) {
                firstProgressReadyMs = (System.currentTimeMillis() - playerStartedAtMs).coerceAtLeast(1L)
                isOpening = false
                isReconnecting = false
                isBuffering = false
                // The current engine demonstrably plays frames. Remove a stale error
                // from the old opening attempt, but don't invent a rendered-frame sample.
                playbackError = null
            }
            position = observedPosition
            if (pendingSeekTargetMs >= 0L && pendingSeekStartedAtMs > 0L) {
                val elapsed = (android.os.SystemClock.elapsedRealtime() - pendingSeekStartedAtMs).coerceAtLeast(0L)
                val reachedTarget = kotlin.math.abs(observedPosition - pendingSeekTargetMs) <= 5_000L
                if ((!isBuffering && reachedTarget) || elapsed >= 30_000L) {
                    val fid = queue.getOrNull(queueIndex)?.fileId ?: 0
                    PlaybackTelemetryRecorder.recordSeekLatency(fid, elapsed)
                    pendingSeekTargetMs = -1L
                    pendingSeekStartedAtMs = 0L
                }
            }
            applyPlayerDurationMs(playerEngine.durationMs, playerEngine)
            if (timeToFirstByteMs == 0L && playerStartedAtMs > 0L) {
                val fid = queue.getOrNull(queueIndex)?.fileId ?: 0
                val ttfb = PlaybackTelemetryRecorder.consumeFirstByteElapsed(fid, playerStartedAtMs)
                if (ttfb > 0L) {
                    timeToFirstByteMs = ttfb
                } else if (playerEngine.bufferedPositionMs > 0L) {
                    timeToFirstByteMs = (System.currentTimeMillis() - playerStartedAtMs).coerceAtLeast(1L)
                }
            }
            val enginePlaying = playerEngine.isPlaying
            val enginePlayWhenReady = playerEngine.playWhenReady
            if (enginePlaying || !enginePlayWhenReady || playerEngine.playbackState.value is EnginePlaybackState.Ended) {
                isPlaying = enginePlaying
            }
            val current = queue[queueIndex]
            cacheState = TelegramPreCacheEngine.cacheState(current.fileId)
            if (position > lastGoodPosition && playerEngine.playbackState.value is EnginePlaybackState.Ready) {
                lastGoodPosition = position
            }
            PlaybackSessionController.recordProgress(playbackSession, position, fileSizeBytes, duration)
            if (position > 0L &&
                duration > 0L &&
                fileSizeBytes > 0L &&
                position - lastAheadPrefetchPosition >= CACHE_AHEAD_REFRESH_MS
            ) {
                PlaybackSessionController.cacheAhead(playbackSession, fileSizeBytes, position, duration)
                lastAheadPrefetchPosition = position
            }
            val nextItem = queue.getOrNull(queueIndex + 1)
            CacheManager.onPlaybackProgress(
                currentFileId = current.fileId,
                currentPositionMs = position,
                durationMs = duration,
                nextItemFileId = nextItem?.fileId,
                nextItemTotalSize = nextItem?.fileSizeBytes ?: 0L
            )
            if (position > 0 && position - lastSavedPosition >= 5_000L) {
                PlaybackProgressTracker.saveMessagePosition(
                    current.chatId,
                    current.messageId,
                    position.toInt(),
                    duration,
                    current.fileId,
                    current.title
                )
                lastSavedPosition = position
            }

            val currentItem = queue.getOrNull(queueIndex)
            // Black-screen recovery is meaningless for a track with no video output.
            val recoveryAction = if (firstAudioReadyMs > 0L || firstProgressReadyMs > 0L) {
                BlackScreenRecoveryAction.NONE
            } else blackScreenDetector.evaluate(
                context = context,
                currentEngine = if (playerEngine is ExoPlayerEngine) SelectedPlayerEngine.ExoPlayer else SelectedPlayerEngine.MPV,
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                hasFirstFrame = firstFrameMs > 0L,
                currentPositionMs = position,
                videoWidth = videoDimensions.first,
                videoHeight = videoDimensions.second,
                decoderName = decoderName,
                mediaKey = "${currentItem?.chatId}:${currentItem?.messageId}"
            )
            when (recoveryAction) {
                BlackScreenRecoveryAction.SWITCH_TO_TEXTURE_VIEW -> {
                    useTextureView = true
                    toastFeedbackText = "Optimizing display surface…"
                    hudVisible = true
                }
                BlackScreenRecoveryAction.SWITCH_TO_MPV_HW,
                BlackScreenRecoveryAction.SWITCH_TO_MPV_SW -> {
                    requestEngineSwitch(SelectedPlayerEngine.MPV, "Switching player engine for video compatibility…")
                }
                else -> {}
            }

            delay(500)
        }
    }

    LaunchedEffect(showBrightnessHud, gestureBrightness) {
        if (showBrightnessHud) {
            delay(1500)
            showBrightnessHud = false
        }
    }

    LaunchedEffect(showVolumeHud, gestureVolume) {
        if (showVolumeHud) {
            delay(1500)
            showVolumeHud = false
        }
    }

    // Back handling is routed through playerInputController below, unifying remote back,
    // predictive back, and BackHandler.

    val onSkipNextAction: () -> Boolean = { skipNext() }
    val onSkipPreviousAction: () -> Boolean = { skipPrevious() }

    // Rebind callbacks when the engine is replaced, so remote keys never use a released engine.
    val playerInputActions = remember(queue, queueIndex, duration, fileSizeBytes, playerEngine) {
        object : PlayerInputActions {
            override fun showHud(target: PlayerFocusTarget) {
                hudVisible = true
                hudInteractionTick++
                scope.launch {
                    delay(50L)
                    when (target) {
                        PlayerFocusTarget.SEEK_BAR -> runCatching { seekBarFocusRequester.requestFocus() }
                        PlayerFocusTarget.BUTTON_PLAY_PAUSE -> runCatching { playPauseFocusRequester.requestFocus() }
                        PlayerFocusTarget.BUTTON_OPTIONS -> runCatching { optionsFocusRequester.requestFocus() }
                        PlayerFocusTarget.BUTTON_RETRY -> runCatching { retryFocusRequester.requestFocus() }
                        PlayerFocusTarget.AUTO_NEXT_PLAY_NOW -> runCatching { playNowFocusRequester.requestFocus() }
                        else -> runCatching { playPauseFocusRequester.requestFocus() }
                    }
                }
            }

            override fun hideHud() {
                hudVisible = false
                hudInteractionTick++
                runCatching { focusRequester.requestFocus() }
            }

            override fun openMenu(menu: PlayerMenuType) {
                when (menu) {
                    PlayerMenuType.TRACKS -> tracksPanelVisible = true
                    PlayerMenuType.EPISODES -> episodesDrawerVisible = true
                    PlayerMenuType.OPTIONS -> optionsPanelVisible = true
                    else -> {}
                }
                hudVisible = true
                hudInteractionTick++
            }

            override fun closeMenu() {
                tracksPanelVisible = false
                episodesDrawerVisible = false
                optionsPanelVisible = false
                hudInteractionTick++
            }

            override fun togglePlayPause() {
                hudInteractionTick++
                if (castState.connected) {
                    if (castState.playbackStatus == CastPlaybackStatus.Playing) CastSessionController.pause()
                    else CastSessionController.play()
                } else if (playerEngine.isPlaying) playerEngine.pause() else playerEngine.play()
                hudVisible = true
            }

            override fun play() {
                hudInteractionTick++
                if (castState.connected) CastSessionController.play()
                else if (!playerEngine.isPlaying) playerEngine.play()
                hudVisible = true
            }

            override fun pause() {
                hudInteractionTick++
                if (castState.connected) CastSessionController.pause()
                else if (playerEngine.isPlaying) playerEngine.pause()
                hudVisible = true
            }

            override fun seekRelative(seconds: Int) {
                hudInteractionTick++
                val knownDuration = playerSeekDurationMs(duration, castState.durationMs, castState.connected)
                val base = if (castState.connected) castState.positionMs else playerEngine.currentPositionMs
                val newPos = relativePlayerSeekTarget(base, seconds * 1000L, knownDuration)
                if (castState.connected) CastSessionController.seekTo(newPos) else {
                    performTrackedSeek(newPos)
                    if (knownDuration > 0L) queue.getOrNull(queueIndex)?.let {
                        CacheManager.onSeek(it.fileId, newPos, fileSizeBytes, knownDuration)
                    }
                }
                seekFeedbackText = if (seconds > 0) "+${seconds}s" else "${seconds}s"
                seekFeedbackSide = if (seconds > 0) 1 else -1
            }

            override fun previewSeekRelative(seconds: Int) {
                hudInteractionTick++
                val knownDuration = playerSeekDurationMs(duration, castState.durationMs, castState.connected)
                val base = seekPreviewPositionMs.takeIf { it >= 0L }
                    ?: if (castState.connected) castState.positionMs else playerEngine.currentPositionMs
                seekPreviewPositionMs = relativePlayerSeekTarget(base, seconds * 1000L, knownDuration)
                seekFeedbackText = "Seek ${formatMs(seekPreviewPositionMs)} · OK to confirm · Back to cancel" +
                    if (knownDuration <= 0L) " · Duration scanning" else ""
                seekFeedbackSide = if (seconds > 0) 1 else -1
            }

            override fun confirmSeekPreview() {
                val target = seekPreviewPositionMs
                val knownDuration = playerSeekDurationMs(duration, castState.durationMs, castState.connected)
                if (target < 0L) return
                seekPreviewPositionMs = -1L
                playerInputController.resetSeekPreviewState()
                val safeTarget = relativePlayerSeekTarget(target, 0L, knownDuration)
                if (castState.connected) CastSessionController.seekTo(safeTarget) else {
                    performTrackedSeek(safeTarget)
                    if (knownDuration > 0L) queue.getOrNull(queueIndex)?.let {
                        CacheManager.onSeek(it.fileId, safeTarget, fileSizeBytes, knownDuration)
                    }
                }
                seekFeedbackText = "Seeking to ${formatMs(safeTarget)}"
            }

            override fun cancelSeekPreview() {
                seekPreviewPositionMs = -1L
                playerInputController.resetSeekPreviewState()
                seekFeedbackText = null
            }

            override fun skipNext() {
                hudInteractionTick++
                if (!onSkipNextAction()) {
                    toastFeedbackText = "No next video"
                    hudVisible = true
                }
            }

            override fun skipPrevious() {
                hudInteractionTick++
                onSkipPreviousAction()
            }

            override fun exitPlayer() {
                exitPlayback()
            }

            override fun cancelAutoNext() {
                autoNextCardVisible = false
                playerInputController.isAutoNextActive = false
                playerEngine.pause()
                hudVisible = true
                hudInteractionTick++
            }

            override fun playNextNow() {
                autoNextCardVisible = false
                playerInputController.isAutoNextActive = false
                if (!onSkipNextAction()) {
                    toastFeedbackText = "No next video"
                    hudVisible = true
                }
            }
        }
    }

    BackHandler(enabled = true) {
        playerInputController.handleBack(playerInputActions)
    }

    PredictiveBackHandler(enabled = true) { progress ->
        try {
            progress.collect { /* handle predictive progress */ }
            playerInputController.handleBack(playerInputActions)
        } catch (_: CancellationException) {
            // gesture cancelled by user
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(queueIndex, duration, playerEngine) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        val max = playerSeekDurationMs(duration, castState.durationMs, castState.connected)
                        if (max <= 0L || playbackError != null) {
                            toastFeedbackText = "Seeking is available when playback is ready"
                        } else {
                            val delta = if (offset.x < size.width / 2f) -SEEK_INCREMENT_MS else SEEK_INCREMENT_MS
                            val base = if (castState.connected) castState.positionMs else playerEngine.currentPositionMs
                            val newPos = (base + delta).coerceIn(0L, max)
                            if (castState.connected) CastSessionController.seekTo(newPos) else {
                                performTrackedSeek(newPos)
                                queue.getOrNull(queueIndex)?.let { current ->
                                    CacheManager.onSeek(current.fileId, newPos, fileSizeBytes, max)
                                }
                            }
                            seekFeedbackText = if (delta < 0) "-10s" else "+10s"
                            seekFeedbackSide = if (delta < 0) -1 else 1
                        }
                        hudVisible = true
                        hudInteractionTick++
                    },
                    onTap = {
                        if (optionsPanelVisible) {
                            optionsPanelVisible = false
                            playerInputController.activeMenu = PlayerMenuType.NONE
                        } else if (playbackError != null) {
                            // Keep the visible Retry action accessible on failed playback.
                            hudVisible = true
                        } else {
                            hudVisible = !hudVisible
                        }
                        hudInteractionTick++
                    }
                )
            }
            .pointerInput(Unit) {
                var isLeft = false
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        isLeft = offset.x < size.width / 2f
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        if (isLeft) {
                            if (currentActivity != null) {
                                val current = currentActivity.window.attributes.screenBrightness.let { if (it < 0f) 0.5f else it }
                                val next = (current - (dragAmount / size.height.toFloat())).coerceIn(0.01f, 1.0f)
                                val lp = currentActivity.window.attributes
                                lp.screenBrightness = next
                                currentActivity.window.attributes = lp
                                gestureBrightness = next
                                showBrightnessHud = true
                                hudInteractionTick++
                            }
                        } else {
                            if (currentAudioManager != null) {
                                val maxVol = currentAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                val current = currentAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                // Some audio routes have zero volume steps; avoid divide-by-zero gestures.
                                val delta = if (maxVol > 0 && size.height > 0) {
                                    (-dragAmount / (size.height / (maxVol * 1.5f))).toInt()
                                } else 0
                                if (delta != 0) {
                                    val next = (current + delta).coerceIn(0, maxVol)
                                    currentAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
                                    gestureVolume = next
                                    showVolumeHud = true
                                    hudInteractionTick++
                                }
                            }
                        }
                    }
                )
            }
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                // Refresh the idle timer even when a focused child consumes D-pad navigation.
                if (tvChrome && event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent.KEYCODE_DPAD_RIGHT,
                        KeyEvent.KEYCODE_DPAD_UP,
                        KeyEvent.KEYCODE_DPAD_DOWN,
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER -> hudInteractionTick++
                    }
                }
                false
            }
            .onKeyEvent { event ->
                val handled = playerInputController.handleKeyEvent(event, playerInputActions)
                if (handled) {
                    hudInteractionTick++
                    return@onKeyEvent true
                }
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_PROG_GREEN -> {
                        // Compose receives both DOWN and UP; changing aspect on both
                        // makes one remote press cycle twice and appear unreliable.
                        if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) {
                            false
                        } else {
                            val nextMode = when (effectiveResizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                else -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            }
                            userSelectedResizeMode = nextMode
                            toastFeedbackText = "Aspect: " + formatResizeMode(nextMode)
                            hudVisible = true
                            hudInteractionTick++
                            true
                        }
                    }
                    KeyEvent.KEYCODE_PROG_YELLOW,
                    KeyEvent.KEYCODE_D -> {
                        if (!BuildConfig.DEBUG) {
                            false
                        } else {
                            if (event.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                                val next = !debugHudVisible
                                debugHudVisible = next
                                EnterprisePreferences.setVideoDebugHudEnabled(next)
                                toastFeedbackText = if (next) "Video Debug HUD: ON" else "Video Debug HUD: OFF"
                            }
                            true
                        }
                    }
                    else -> false
                }
            }
    ) {
        key(queueIndex, useTextureView) {
            VideoSurfaceView(
                engine = playerEngine,
                resizeMode = effectiveResizeMode,
                useTextureView = useTextureView,
                onSurfaceDiagnostic = { diag ->
                    surfaceDiagnostic = diag
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        val loadingPhase = playerLoadingPhaseForSession(
            castConnected = castState.connected,
            castBuffering = castState.playbackStatus == CastPlaybackStatus.Buffering,
            opening = isOpening,
            reconnecting = isReconnecting,
            buffering = isBuffering,
            error = playbackError
        )
        if (loadingPhase == PlayerLoadingPhase.OPENING) {
            PlayerOpeningSkeleton(compact = compactHud)
        }
        if (hudVisible) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(if (compactHud) 270.dp else 340.dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.54f))
                        )
                    )
            )
        }
        if (hudVisible) {
            PlayerHud(
                state = PlayerHudState(
                    title = title,
                    fileName = fileName,
                    positionMs = if (castState.connected) castState.positionMs else position,
                    durationMs = playerSeekDurationMs(duration, castState.durationMs, castState.connected),
                    durationTrusted = (playerSeekDurationMs(duration, castState.durationMs, castState.connected)) > 0L,
                    speed = speed,
                    isPlaying = if (castState.connected) castState.playbackStatus == CastPlaybackStatus.Playing else isPlaying,
                    playWhenReady = if (castState.connected) {
                        castState.playbackStatus == CastPlaybackStatus.Playing ||
                            castState.playbackStatus == CastPlaybackStatus.Buffering
                    } else playerEngine.playWhenReady,
                    isBuffering = if (castState.connected) castState.playbackStatus == CastPlaybackStatus.Buffering else isBuffering,
                    isOpening = !castState.connected && isOpening,
                    isReconnecting = !castState.connected && isReconnecting,
                    retryCount = if (castState.connected) 0 else retryCount,
                    error = playbackError,
                    trackLabel = "${queueIndex + 1} / ${queue.size}",
                    cacheStateLabel = cacheState.playerLabel(),
                    bufferedPositionMs = if (castState.connected) 0L else playerEngine.bufferedPositionMs,
                    resizeMode = effectiveResizeMode,
                    mediaKey = "${queue.getOrNull(queueIndex)?.chatId}:${queue.getOrNull(queueIndex)?.messageId}"
                ),
                compact = compactHud,
                isTv = tvChrome,
                optionsVisible = optionsPanelVisible,
                onScrubActiveChanged = { active ->
                    seekScrubbing = active
                    hudInteractionTick++
                },
                onOptionsToggle = {
                    hudInteractionTick++
                    val openingOptions = !optionsPanelVisible
                    if (openingOptions) {
                        tracksPanelVisible = false
                        episodesDrawerVisible = false
                    }
                    optionsPanelVisible = openingOptions
                    playerInputController.activeMenu =
                        if (openingOptions) PlayerMenuType.OPTIONS else PlayerMenuType.NONE
                },
                onOptionsClose = {
                    hudInteractionTick++
                    optionsPanelVisible = false
                    playerInputController.activeMenu = PlayerMenuType.NONE
                    if (tvChrome) scope.launch {
                        delay(50L)
                        runCatching { optionsFocusRequester.requestFocus() }
                    }
                },
                onExit = ::exitPlayback,
                onStartOver = {
                    hudInteractionTick++
                    if (castState.connected) CastSessionController.seekTo(0L)
                    else performTrackedSeek(0L)
                    showResumeRestartOption = false
                    toastFeedbackText = "Restarted from beginning"
                },
                onRetry = {
                    hudInteractionTick++
                    retryCount = 0
                    isReconnecting = true
                    lastGoodPosition = maxOf(lastGoodPosition, playerEngine.currentPositionMs).coerceAtLeast(0L)
                    playbackError = null
                    reloadNonce++
                },
                onPlayPause = {
                    hudInteractionTick++
                    if (castState.connected) {
                        if (castState.playbackStatus == CastPlaybackStatus.Playing) CastSessionController.pause()
                        else CastSessionController.play()
                    } else if (playerEngine.isPlaying) playerEngine.pause() else playerEngine.play()
                },
                onSeekBy = { delta ->
                    hudInteractionTick++
                    val knownDuration = playerSeekDurationMs(duration, castState.durationMs, castState.connected)
                    val basePosition = if (castState.connected) castState.positionMs else playerEngine.currentPositionMs
                    val newPos = relativePlayerSeekTarget(basePosition, delta, knownDuration)
                    if (castState.connected) CastSessionController.seekTo(newPos) else {
                        performTrackedSeek(newPos)
                        if (knownDuration > 0L) queue.getOrNull(queueIndex)?.let { current ->
                            CacheManager.onSeek(current.fileId, newPos, fileSizeBytes, knownDuration)
                        }
                    }
                },
                onSeekTo = { target ->
                    hudInteractionTick++
                    val max = playerSeekDurationMs(duration, castState.durationMs, castState.connected)
                    if (max <= 0L) {
                        toastFeedbackText = "Seeking is available when video duration is ready"
                    } else {
                        val newPos = target.coerceIn(0L, max)
                        if (castState.connected) CastSessionController.seekTo(newPos) else {
                            performTrackedSeek(newPos)
                            queue.getOrNull(queueIndex)?.let { current ->
                                CacheManager.onSeek(current.fileId, newPos, fileSizeBytes, max)
                            }
                        }
                    }
                },
                onSkipPrevious = {
                    hudInteractionTick++
                    skipPrevious()
                },
                onSkipNext = {
                    hudInteractionTick++
                    if (!skipNext()) {
                        toastFeedbackText = "No next video"
                        hudVisible = true
                    }
                },
                onTracks = {
                    hudInteractionTick++
                    optionsPanelVisible = false
                    episodesDrawerVisible = false
                    tracksPanelVisible = true
                    playerInputController.activeMenu = PlayerMenuType.TRACKS
                    hudVisible = true
                },
                onToggleResizeMode = {
                    hudInteractionTick++
                    val nextMode = when (effectiveResizeMode) {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        else -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                    userSelectedResizeMode = nextMode
                    toastFeedbackText = "Aspect: " + formatResizeMode(nextMode)
                    hudVisible = true
                },
                onSpeed = {
                    hudInteractionTick++
                    speed = nextSpeed(speed)
                    playerEngine.setPlaybackSpeed(speed)
                    EnterprisePreferences.setPreferredSpeed(speed)
                    toastFeedbackText = "Speed: ${speed}x"
                    hudVisible = true
                },
                onEpisodes = if (queue.size > 1) { {
                    hudInteractionTick++
                    optionsPanelVisible = false
                    tracksPanelVisible = false
                    episodesDrawerVisible = !episodesDrawerVisible
                    playerInputController.activeMenu =
                        if (episodesDrawerVisible) PlayerMenuType.EPISODES else PlayerMenuType.NONE
                } } else null,
                onSwitchEngine = {
                    optionsPanelVisible = false
                    playerInputController.activeMenu = PlayerMenuType.NONE
                    togglePlayerEngine()
                },
                playPauseFocusRequester = playPauseFocusRequester,
                optionsFocusRequester = optionsFocusRequester,
                firstOptionFocusRequester = firstOptionFocusRequester,
                seekBarFocusRequester = seekBarFocusRequester,
                retryFocusRequester = retryFocusRequester,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        if (tracksPanelVisible) {
            TrackPickerOverlay(
                playerEngine = playerEngine,
                compact = compactHud,
                onClose = { tracksPanelVisible = false },
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }

        // A small, non-modal status banner replaces the large centered spinner.
        // Leave Retry, seek controls, Cast, and Android TV focus untouched.
        PlayerLoadingBanner(
            phase = loadingPhase,
            compact = compactHud,
            retryCount = retryCount,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = if (compactHud) 70.dp else 26.dp)
        )

        if (BuildConfig.PRIVATE_MEDIA_CAST_ENABLED && !isAndroidTv && castState.supported) {
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (Build.VERSION.SDK_INT >= 37 && !hasLocalNetworkPermission) {
                    Surface(
                        modifier = Modifier.size(48.dp).clickable {
                            localNetworkPermissionRequest.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("Cast", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                } else {
                    AndroidView(
                        modifier = Modifier.size(48.dp),
                        factory = { viewContext ->
                            CastRouteButtonFactory.create(viewContext).also { button ->
                                com.google.android.gms.cast.framework.CastButtonFactory.setUpMediaRouteButton(
                                    viewContext.applicationContext,
                                    button
                                )
                                button.contentDescription = "Choose a Cast device"
                            }
                        }
                    )
                }
                if (castState.connected && !castState.deviceName.isNullOrBlank()) {
                    Text(
                        "Casting to ${castState.deviceName}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        // 5-Second Auto-Next Countdown Card Overlay
        AnimatedVisibility(
            visible = autoNextCardVisible,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 36.dp, bottom = 36.dp)
        ) {
            val nextEpisodeItem = queue.getOrNull(queueIndex + 1)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                tonalElevation = 12.dp,
                modifier = Modifier.widthIn(min = 280.dp, max = 360.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$autoNextCountdown",
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Next video in ${autoNextCountdown}s",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (nextEpisodeItem != null) {
                        Text(
                            text = nextEpisodeItem.title,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BousygramButton(
                            text = "Cancel",
                            onClick = {
                                autoNextCardVisible = false
                                playerInputController.isAutoNextActive = false
                                playerEngine.pause()
                                hudVisible = true
                                hudInteractionTick++
                            }
                        )
                        BousygramButton(
                            text = "Play Now ▶",
                            onClick = {
                                autoNextCardVisible = false
                                playerInputController.isAutoNextActive = false
                                if (!skipNext()) {
                                    toastFeedbackText = "No next video"
                                    hudVisible = true
                                }
                            },
                            selected = true,
                            modifier = Modifier.focusRequester(playNowFocusRequester)
                        )
                    }
                }
            }
        }

        // Same-session queue tray. The queue originated from the current browsing
        // scope; additionally exclude other chats to avoid cross-source leakage.
        AnimatedVisibility(
            visible = episodesDrawerVisible,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(bottom = if (compactHud) 110.dp else 135.dp)
        ) {
            val currentChat = queue.getOrNull(queueIndex)?.chatId
            val relatedQueue = queue.mapIndexedNotNull { idx, item ->
                if (currentChat != null && item.chatId == currentChat) idx to item else null
            }
            val trayColumns = if (compactHud) 2 else 4
            LaunchedEffect(episodesDrawerVisible, queueIndex) {
                if (episodesDrawerVisible && relatedQueue.isNotEmpty()) {
                    trayFocusedIndex = 0
                    delay(90L)
                    runCatching { trayFirstFocus.requestFocus() }
                }
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(if (compactHud) 0.96f else 0.82f)
                    .padding(horizontal = 10.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "More from this source",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium
                        )
                        BousygramButton("Close", {
                            episodesDrawerVisible = false
                            playerInputController.activeMenu = PlayerMenuType.NONE
                        })
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(trayColumns),
                        modifier = Modifier.fillMaxWidth()
                            .heightIn(max = if (compactHud) 210.dp else 265.dp)
                            .onPreviewKeyEvent { event ->
                                val native = event.nativeKeyEvent
                                if (native.action == KeyEvent.ACTION_DOWN &&
                                    native.keyCode == KeyEvent.KEYCODE_DPAD_UP &&
                                    trayFocusedIndex < trayColumns
                                ) {
                                    episodesDrawerVisible = false
                                    playerInputController.activeMenu = PlayerMenuType.NONE
                                    hudVisible = true
                                    scope.launch {
                                        delay(50L)
                                        runCatching { playPauseFocusRequester.requestFocus() }
                                    }
                                    true
                                } else false
                            },
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        gridItemsIndexed(relatedQueue, key = { _, pair -> pair.first }) { localIndex, pair ->
                            val (idx, item) = pair
                            val current = idx == queueIndex
                            val shape = RoundedCornerShape(12.dp)
                            var focused by remember(item.chatId, item.messageId) { mutableStateOf(false) }
                            Surface(
                                shape = shape,
                                color = if (current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                border = androidx.compose.foundation.BorderStroke(
                                    if (focused) 3.dp else if (current) 2.dp else 1.dp,
                                    if (focused || current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier.fillMaxWidth()
                                    .then(if (localIndex == 0) Modifier.focusRequester(trayFirstFocus) else Modifier)
                                    .onFocusChanged {
                                        focused = it.isFocused
                                        if (it.isFocused) trayFocusedIndex = localIndex
                                    }
                                    .clickable {
                                        episodesDrawerVisible = false
                                        playerInputController.activeMenu = PlayerMenuType.NONE
                                        if (!current) goToTrack(idx)
                                    }
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(
                                        item.title,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        if (current) "▶ Playing" else formatMs(item.durationSeconds * 1000L),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Gesture Brightness HUD Pill (Left)
        AnimatedVisibility(
            visible = showBrightnessHud,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 28.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("☀️", fontSize = 22.sp)
                    Text(
                        "${(gestureBrightness * 100).toInt()}%",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Gesture Volume HUD Pill (Right)
        AnimatedVisibility(
            visible = showVolumeHud,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 28.dp)
        ) {
            val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
            val maxVol = remember(audioManager) { audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15 }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🔊", fontSize = 22.sp)
                    Text(
                        "${(gestureVolume * 100 / maxVol.coerceAtLeast(1))}%",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Animated Seek Feedback Overlay
        AnimatedVisibility(
            visible = seekFeedbackText != null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(if (seekFeedbackSide < 0) Alignment.CenterStart else Alignment.CenterEnd)
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 48.dp)
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = seekFeedbackText.orEmpty(),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Mode Toast Overlay (Aspect ratio / Speed change)
        AnimatedVisibility(
            visible = toastFeedbackText != null,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 36.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.70f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 20.dp, vertical = 9.dp)
            ) {
                Text(
                    text = toastFeedbackText.orEmpty(),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Smart Resume Restart Prompt
        AnimatedVisibility(
            visible = showResumeRestartOption && hudVisible && !tvChrome,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Surface(
                modifier = Modifier
                    .padding(top = 28.dp, end = 28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        performTrackedSeek(0L)
                        showResumeRestartOption = false
                        toastFeedbackText = "Restarted from beginning"
                    },
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "↺ Start Over",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (BuildConfig.DEBUG && debugHudVisible) {
            val display = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                runCatching { context.display }.getOrNull()
            } else {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager)?.defaultDisplay
            }
            val displayHz = display?.refreshRate?.toInt() ?: 60
            val isHdr = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                display?.isHdr == true
            } else false

            val visibleOverlaysList = buildList {
                if (hudVisible) add("HUD")
                if (tracksPanelVisible) add("Tracks")
                if (episodesDrawerVisible) add("Queue")
                if (isBuffering && playbackError == null) add("Buffering")
                if (autoNextCardVisible) add("AutoNext")
                if (showBrightnessHud) add("Brightness")
                if (showVolumeHud) add("Volume")
                if (seekFeedbackText != null) add("SeekFeedback")
                if (toastFeedbackText != null) add("Toast")
                if (showResumeRestartOption) add("StartOver")
            }

            VideoDebugHud(
                engineName = playerEngine.engineName,
                decoderName = playerEngine.videoDecoderName.ifBlank { "Unknown" },
                surfaceType = surfaceDiagnostic.surfaceType,
                surfaceValid = surfaceDiagnostic.isValid,
                surfaceWidth = surfaceDiagnostic.width,
                surfaceHeight = surfaceDiagnostic.height,
                resizeModeDesc = formatResizeMode(effectiveResizeMode),
                videoWidth = videoDimensions.first,
                videoHeight = videoDimensions.second,
                displayHz = displayHz,
                hdrMode = if (isHdr) "HDR" else "SDR",
                renderedBufferCount = playerEngine.renderedOutputBufferCount,
                droppedBufferCount = playerEngine.droppedBufferCount,
                skippedBufferCount = playerEngine.skippedOutputBufferCount,
                inputBufferCount = playerEngine.inputBufferCount,
                isVideoTrackSelected = playerEngine.isVideoTrackSelected,
                disabledTracksDesc = playerEngine.disabledTrackTypesDesc,
                timeSincePrepareMs = if (playerStartedAtMs > 0L) (System.currentTimeMillis() - playerStartedAtMs).coerceAtLeast(0L) else 0L,
                timeToFirstFrameMs = firstFrameMs,
                visibleOverlays = if (visibleOverlaysList.isEmpty()) "None (Video Visible)" else visibleOverlaysList.joinToString(", "),
                onSwitchEngine = {
                    togglePlayerEngine()
                },
                onSwitchSurface = {
                    val next = !useTextureView
                    useTextureView = next
                    EnterprisePreferences.setSurfaceTypePreference(if (next) "textureview" else "surfaceview")
                    toastFeedbackText = if (next) "Surface: TextureView" else "Surface: SurfaceView"
                },
                onSwitchMpvMode = {
                    val currentMode = EnterprisePreferences.mpvHwDecMode()
                    val nextMode = when (currentMode) {
                        "mediacodec-copy" -> "mediacodec"
                        "mediacodec" -> "no"
                        else -> "mediacodec-copy"
                    }
                    EnterprisePreferences.setMpvHwDecMode(nextMode)
                    (playerEngine as? MpvPlayerEngine)?.setHwDecAndVoMode(nextMode)
                    toastFeedbackText = "MPV mode: $nextMode"
                },
                onClose = {
                    debugHudVisible = false
                    EnterprisePreferences.setVideoDebugHudEnabled(false)
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 24.dp, start = 24.dp)
            )
        }
    }
}

@Composable
private fun VideoDebugHud(
    engineName: String,
    decoderName: String,
    surfaceType: String,
    surfaceValid: Boolean,
    surfaceWidth: Int,
    surfaceHeight: Int,
    resizeModeDesc: String,
    videoWidth: Int,
    videoHeight: Int,
    displayHz: Int,
    hdrMode: String,
    renderedBufferCount: Int,
    droppedBufferCount: Int,
    skippedBufferCount: Int,
    inputBufferCount: Int,
    isVideoTrackSelected: Boolean,
    disabledTracksDesc: String,
    timeSincePrepareMs: Long,
    timeToFirstFrameMs: Long,
    visibleOverlays: String,
    onSwitchEngine: () -> Unit,
    onSwitchSurface: () -> Unit,
    onSwitchMpvMode: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .widthIn(min = 400.dp, max = 560.dp)
            .clip(RoundedCornerShape(12.dp)),
        color = Color(0xEE0B121C),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF00E5FF)),
        shadowElevation = 16.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "VIDEO DEBUG HUD (Remote: Yellow/Info)",
                    color = Color(0xFF00E5FF),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = "✕",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onClose)
                        .padding(horizontal = 6.dp)
                )
            }
            Text(
                text = "Engine: $engineName | Decoder: $decoderName",
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
            Text(
                text = "Surface: $surfaceType (valid=$surfaceValid) ${surfaceWidth}x${surfaceHeight} | $resizeModeDesc",
                color = Color(0xFF76FF03),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
            Text(
                text = "Video: ${videoWidth}x${videoHeight} | Display: ${displayHz}Hz ($hdrMode)",
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
            Text(
                text = "Buffers: rendered=$renderedBufferCount dropped=$droppedBufferCount skip=$skippedBufferCount in=$inputBufferCount",
                color = Color(0xFFFFD700),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
            Text(
                text = "Track: videoSelected=$isVideoTrackSelected | disabled=[$disabledTracksDesc]",
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
            Text(
                text = "Time: prep->now ${timeSincePrepareMs}ms | firstFrame: ${timeToFirstFrameMs}ms",
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
            Text(
                text = "Overlays: $visibleOverlays",
                color = if (visibleOverlays.startsWith("None")) Color(0xFF69F0AE) else Color(0xFFFF8A80),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BousygramButton(
                    text = "Switch Engine",
                    onClick = onSwitchEngine,
                    modifier = Modifier.weight(1f)
                )
                BousygramButton(
                    text = "Switch Surface",
                    onClick = onSwitchSurface,
                    modifier = Modifier.weight(1f)
                )
                BousygramButton(
                    text = "MPV Mode",
                    onClick = onSwitchMpvMode,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun TrackPickerOverlay(
    playerEngine: PlayerEngine,
    compact: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tracks by playerEngine.tracks.collectAsStateWithLifecycle()
    var selectedType by remember { mutableStateOf(MediaTrackInfo.TrackType.SUBTITLE) }
    val firstChoiceFocus = remember { FocusRequester() }
    val visibleTracks = tracks.filter { it.type == selectedType }
    val hasSelectedTrack = visibleTracks.any { it.isSelected }

    LaunchedEffect(selectedType) {
        runCatching { firstChoiceFocus.requestFocus() }
    }

    Surface(
        modifier = modifier
            .padding(if (compact) 12.dp else 28.dp)
            .widthIn(max = 420.dp)
            .fillMaxWidth(if (compact) 0.94f else 0.42f)
            .focusGroup(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 20.dp
    ) {
        Column(
            Modifier
                .heightIn(max = if (compact) 420.dp else 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (selectedType) {
                        MediaTrackInfo.TrackType.SUBTITLE -> "Subtitles"
                        MediaTrackInfo.TrackType.AUDIO -> "Audio"
                        MediaTrackInfo.TrackType.VIDEO -> "Video Streams"
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                BousygramButton("Close", onClose)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BousygramButton(
                    "CC · Subtitles",
                    { selectedType = MediaTrackInfo.TrackType.SUBTITLE },
                    selected = selectedType == MediaTrackInfo.TrackType.SUBTITLE
                )
                BousygramButton(
                    "♫ Audio",
                    { selectedType = MediaTrackInfo.TrackType.AUDIO },
                    selected = selectedType == MediaTrackInfo.TrackType.AUDIO
                )
                val videoTracks = tracks.filter { it.type == MediaTrackInfo.TrackType.VIDEO }
                if (videoTracks.size > 1) {
                    BousygramButton(
                        "⚙ Streams",
                        { selectedType = MediaTrackInfo.TrackType.VIDEO },
                        selected = selectedType == MediaTrackInfo.TrackType.VIDEO
                    )
                }
            }

            if (selectedType == MediaTrackInfo.TrackType.SUBTITLE) {
                TrackChoice(
                    label = "Off",
                    selected = !hasSelectedTrack,
                    modifier = Modifier.focusRequester(firstChoiceFocus),
                    onSelect = {
                        playerEngine.deselectTrack(MediaTrackInfo.TrackType.SUBTITLE)
                    }
                )
            } else if (visibleTracks.isEmpty()) {
                Text(
                    if (selectedType == MediaTrackInfo.TrackType.AUDIO)
                        "No alternate audio tracks are available."
                    else
                        "No subtitle tracks are available for this video.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            visibleTracks.forEachIndexed { index, track ->
                TrackChoice(
                    label = track.name,
                    selected = track.isSelected,
                    modifier = if (index == 0 && selectedType == MediaTrackInfo.TrackType.AUDIO) {
                        Modifier.focusRequester(firstChoiceFocus)
                    } else Modifier,
                    onSelect = { playerEngine.selectTrack(track) }
                )
            }
        }
    }
}

@Composable
private fun TrackChoice(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onSelect)
            .focusable(),
        color = when {
            selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
            focused -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.surface
        },
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (selected) Text("✓", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
@OptIn(UnstableApi::class)
private fun PlayerHud(
    state: PlayerHudState,
    compact: Boolean,
    isTv: Boolean,
    optionsVisible: Boolean,
    onScrubActiveChanged: (Boolean) -> Unit,
    onOptionsToggle: () -> Unit,
    onOptionsClose: () -> Unit,
    onExit: () -> Unit,
    onStartOver: () -> Unit,
    onRetry: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onTracks: () -> Unit,
    onToggleResizeMode: () -> Unit,
    onSpeed: () -> Unit,
    onEpisodes: (() -> Unit)? = null,
    onSwitchEngine: (() -> Unit)? = null,
    playPauseFocusRequester: FocusRequester,
    optionsFocusRequester: FocusRequester,
    firstOptionFocusRequester: FocusRequester,
    seekBarFocusRequester: FocusRequester? = null,
    retryFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    val chromeLayout = playerChromeLayout(isTv, compact)
    LaunchedEffect(optionsVisible, isTv) {
        if (optionsVisible && isTv) {
            delay(80L)
            runCatching { firstOptionFocusRequester.requestFocus() }
        }
    }
    val edgePadding = if (isTv) 14.dp else if (compact) 12.dp else 32.dp
    val innerPadding = if (isTv) 12.dp else if (compact) 14.dp else 22.dp
    val maxHudHeight = playerHudHeightLimit(
        LocalConfiguration.current.screenHeightDp.takeIf { it > 0 } ?: 720,
        compact
    )
    val isPlayingOrPending = (state.playWhenReady && state.error == null) || state.isPlaying
    val statusText = when {
        state.error != null -> "Playback error"
        state.isReconnecting -> "Retrying download"
        state.isOpening -> "Downloading"
        state.isBuffering -> "Buffering"
        isPlayingOrPending -> "Playing"
        else -> "Paused"
    }
    val hudShape = RoundedCornerShape(if (compact) 18.dp else 26.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(edgePadding)
            .heightIn(max = maxHudHeight)
            .border(1.dp, Color(0x555A7998), hudShape),
        color = Color(0xF0111A28),
        contentColor = Color.White,
        shape = hudShape,
        shadowElevation = 14.dp
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.title,
                        style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!state.fileName.isNullOrBlank() && state.fileName != state.title) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = state.fileName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (isTv && state.durationTrusted && state.durationMs > 0L) {
                    BousygramButton("↺ Start Over", onStartOver)
                }
                BousygramButton(if (isTv) "‹ Back" else "✕ Close", onExit)
            }
            if (!isTv) {
            Spacer(Modifier.height(6.dp))
            // Separate context and timing rows instead of truncating cache details
            // when TV font scaling or phone accessibility text size is enabled.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PlayerStatusChip(
                    label = statusText,
                    isError = state.error != null,
                    isBusy = state.isOpening || state.isBuffering || state.isReconnecting
                )
                Text(
                    listOfNotNull(
                        state.trackLabel,
                        state.retryCount.takeIf { it > 0 }?.let { "retry $it/$MAX_AUTO_RETRIES" }
                    ).joinToString(" · "),
                    style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(5.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    state.cacheStateLabel.orEmpty(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${formatMs(state.positionMs)} / ${durationText(state)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    "${speedLabel(state.speed)}x",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                state.error
                    ?: if (state.isOpening || state.isReconnecting) "Fetching this video from Telegram. Playback starts as soon as the first bytes arrive."
                    else if (compact) "Double-tap left/right to seek ±10s · drag timeline to scrub"
                    else "D-pad navigate controls · OK select · Back hide controls · (Hidden HUD: ◀ ▶ seek preview · OK confirm)",
                style = MaterialTheme.typography.bodySmall,
                color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (state.error != null) 5 else if (compact) 2 else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = if (state.error != null) Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                } else Modifier
            )
            Spacer(Modifier.height(if (compact) 10.dp else 15.dp))
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(statusText, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    Text(
                        formatMs(state.positionMs) + " / " + durationText(state),
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
            PlayerSeekBar(
                mediaKey = state.mediaKey,
                positionMs = state.positionMs,
                bufferedPositionMs = state.bufferedPositionMs,
                durationMs = state.durationMs,
                enabled = state.durationTrusted && state.durationMs > 0L,
                onSeekTo = onSeekTo,
                onScrubActiveChanged = onScrubActiveChanged,
                modifier = if (seekBarFocusRequester != null) Modifier.focusRequester(seekBarFocusRequester) else Modifier
            )
            Spacer(Modifier.height(10.dp))
            // Primary transport actions remain on a single focus row for Android TV.
            // Compact phones have four large touch targets; less-frequent actions
            // are grouped behind an explicit Options button instead of tiny icons.
            PlayerTransportControls(
                layout = chromeLayout,
                isPlayingOrPending = isPlayingOrPending,
                optionsExpanded = optionsVisible,
                onPrevious = onSkipPrevious,
                onSeekBack = { onSeekBy(-SEEK_INCREMENT_MS) },
                onPlayPause = onPlayPause,
                onSeekForward = { onSeekBy(SEEK_INCREMENT_MS) },
                onNext = onSkipNext,
                onOptions = onOptionsToggle,
                playPauseFocusRequester = playPauseFocusRequester,
                optionsFocusRequester = optionsFocusRequester
            )
            AnimatedVisibility(
                visible = optionsVisible,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val aspectLabel = when (state.resizeMode) {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom"
                    AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch"
                    else -> "Fit"
                }
                PlayerOptionsPanel(
                    layout = chromeLayout,
                    onTracks = onTracks,
                    onSpeed = onSpeed,
                    speedLabel = "${speedLabel(state.speed)}x",
                    onAspect = onToggleResizeMode,
                    aspectLabel = aspectLabel,
                    onEpisodes = onEpisodes,
                    onSwitchEngine = onSwitchEngine,
                    onPrevious = onSkipPrevious,
                    onNext = onSkipNext,
                    onSeekBack30 = { onSeekBy(-30_000L) },
                    onSeekForward30 = { onSeekBy(30_000L) },
                    onClose = onOptionsClose,
                    firstOptionFocusRequester = firstOptionFocusRequester
                )
            }
            if (state.error != null) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BousygramButton(
                        text = "Retry",
                        onClick = onRetry,
                        selected = true,
                        modifier = if (retryFocusRequester != null) Modifier.focusRequester(retryFocusRequester) else Modifier
                    )
                    BousygramButton("Skip", onSkipNext)
                }
            }
        }
    }
}

private fun durationText(state: PlayerHudState): String =
    if (state.durationTrusted && state.durationMs > 0L) formatMs(state.durationMs) else "Unknown"

@Composable
private fun PlayerSeekBar(
    mediaKey: String = "",
    positionMs: Long,
    bufferedPositionMs: Long,
    durationMs: Long,
    enabled: Boolean,
    onSeekTo: (Long) -> Unit,
    onScrubActiveChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragging by remember(mediaKey) { mutableStateOf(false) }
    var dragPosition by remember(mediaKey) { mutableLongStateOf(positionMs) }
    LaunchedEffect(mediaKey, enabled) {
        // A track change or unknown duration must release any prior scrub lock.
        dragging = false
        onScrubActiveChanged(false)
    }
    var barFocused by remember { mutableStateOf(false) }
    val shown = if (dragging) dragPosition else positionMs
    val fraction = if (enabled && durationMs > 0L) (shown.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    val buffered = if (enabled && durationMs > 0L) {
        (bufferedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val previewBitmap = remember(mediaKey, shown, durationMs) {
        if (mediaKey.isNotBlank() && durationMs > 0L) {
            SeekThumbnailManager.getSnapshotAt(mediaKey, shown, durationMs)
        } else {
            null
        }
    }
    Column(Modifier.fillMaxWidth()) {
        if ((dragging || barFocused) && enabled) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${formatMs(shown)} / ${formatMs(durationMs)}",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                if (previewBitmap != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.90f),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    ) {
                        Image(
                            bitmap = previewBitmap.asImageBitmap(),
                            contentDescription = "Seek frame preview",
                            modifier = Modifier.size(width = 84.dp, height = 48.dp),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                }
            }
        }
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = if (barFocused) 1.5.dp else 0.dp,
                    color = if (barFocused) MaterialTheme.colorScheme.secondary else Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 6.dp)
                .onFocusChanged { barFocused = it.isFocused }
                .focusable()
                .onKeyEvent { event ->
                    if (!enabled || event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                    val repeat = event.nativeKeyEvent.repeatCount
                    val step = when {
                        repeat >= 8 -> 60_000L
                        repeat >= 3 -> 30_000L
                        else -> SEEK_INCREMENT_MS
                    }
                    when (event.key) {
                        Key.DirectionLeft -> {
                            onSeekTo((shown - step).coerceAtLeast(0L))
                            true
                        }
                        Key.DirectionRight -> {
                            onSeekTo((shown + step).coerceAtMost(durationMs))
                            true
                        }
                        else -> false
                    }
                }
        ) {
            LinearProgressIndicator(
                progress = { buffered },
                modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.Center),
                color = MaterialTheme.colorScheme.outline,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Slider(
                value = fraction,
                onValueChange = { value ->
                    if (!enabled) return@Slider
                    val target = playerScrubTarget(value, durationMs) ?: return@Slider
                    if (!dragging) {
                        dragging = true
                        onScrubActiveChanged(true)
                    }
                    dragPosition = target
                },
                onValueChangeFinished = {
                    if (enabled && dragging) onSeekTo(dragPosition)
                    dragging = false
                    onScrubActiveChanged(false)
                },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().align(Alignment.Center),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.Transparent
                )
            )
        }
        if (dragging && enabled && previewBitmap == null) {
            Text(
                "${formatMs(dragPosition)} / ${formatMs(durationMs)}",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

private fun PlaybackCacheState.playerLabel(): String =
    when (this) {
        PlaybackCacheState.Preparing -> "Preparing cache"
        PlaybackCacheState.Priming -> "Priming stream"
        PlaybackCacheState.Streaming -> "Streaming"
        PlaybackCacheState.Reconnecting -> "Reconnecting cache"
        PlaybackCacheState.CachingAhead -> "Caching ahead"
        PlaybackCacheState.Cached -> "Cached"
        PlaybackCacheState.Failed -> "Cache waiting"
    }

private fun displayTitle(item: com.vaydelux.bousygram.domain.model.MediaItem): String {
    val directTitle = item.title.takeIf { it.isNotBlank() }
        ?: item.fileName?.takeIf { it.isNotBlank() }
        ?: item.tmdbTitle?.takeIf { it.isNotBlank() }
        ?: "Untitled media"
    return displayTitle(directTitle)
}

private fun displayTitle(item: PlaybackQueueItem): String {
    val directTitle = item.title.takeIf { it.isNotBlank() }
        ?: item.fileName?.takeIf { it.isNotBlank() }
        ?: "Untitled media"
    return displayTitle(directTitle)
}

private fun displayTitle(raw: String): String {
    val line = raw.lineSequence().firstOrNull().orEmpty().trim()
    return if (line.length <= 96) line else line.take(93) + "…"
}

private fun playbackErrorMessage(error: PlaybackException): String {
    val detail = error.message?.takeIf { it.isNotBlank() }
    val isSourceDetail = detail.equals("Source error", ignoreCase = true)
    val normalizedDetail = when {
        isSourceDetail ->
            "Telegram is still caching this range. Bousygram will reconnect automatically."
        detail != null -> detail
        else -> null
    }
    val summary = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED ->
            "Telegram stream read failed"
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
            "Telegram is still sending this part"
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            "Network error while reading the stream"
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
            "Stream file not found on device"
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ->
            if (isSourceDetail) "Telegram stream data is not ready yet" else "Unsupported or corrupted video format"
        else -> "Playback failed"
    }
    return if (normalizedDetail != null) "$summary: $normalizedDetail" else summary
}

private fun isRecoverablePlaybackError(error: PlaybackException): Boolean =
    when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> true
        else -> false
    }

private fun autoRetryDelayMs(retryCount: Int): Long = when (retryCount) {
    1 -> 500L
    2 -> 1_500L
    3 -> 3_000L
    4 -> 5_000L
    else -> 8_000L
}

@OptIn(UnstableApi::class)
private class TelegramStreamLoadErrorPolicy : DefaultLoadErrorHandlingPolicy(MAX_AUTO_RETRIES + 1) {
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        return when (loadErrorInfo.errorCount) {
            1 -> 500L
            2 -> 1_500L
            3 -> 3_000L
            4 -> 5_000L
            5, 6 -> 8_000L
            else -> C.TIME_UNSET
        }
    }

    override fun getMinimumLoadableRetryCount(dataType: Int): Int = MAX_AUTO_RETRIES + 1
}

private fun nextSpeed(current: Float): Float {
    val speeds = floatArrayOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    val index = speeds.indexOfFirst { it == current }.takeIf { it >= 0 } ?: 1
    return speeds[(index + 1) % speeds.size]
}

private fun speedLabel(value: Float): String =
    if (value == 1.0f) "1.0" else "%.2f".format(value).trimEnd('0').trimEnd('.')

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

private class PlayerAudioFocusController(context: Context) {
    private val audioManager: AudioManager? = context.getSystemService(AudioManager::class.java)
    private var focusRequest: AudioFocusRequest? = null
    private var currentEngine: PlayerEngine? = null
    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        val engine = currentEngine ?: return@OnAudioFocusChangeListener
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> engine.pause()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> engine.pause()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> Unit
            AudioManager.AUDIOFOCUS_GAIN -> Unit
        }
    }

    fun request(engine: PlayerEngine) {
        val audioManager = audioManager ?: return
        currentEngine = engine
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build()
                )
                .setOnAudioFocusChangeListener(listener)
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    fun abandon() {
        val audioManager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(listener)
        }
        focusRequest = null
        currentEngine = null
    }
}
