package com.vaydelux.bousygram.presentation.debug

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import com.vaydelux.bousygram.core.media.EnginePlaybackState
import com.vaydelux.bousygram.core.media.PlayerEngine
import com.vaydelux.bousygram.core.media.PlayerEngineFactory
import com.vaydelux.bousygram.core.media.diagnostics.BlackScreenDetector
import com.vaydelux.bousygram.core.media.diagnostics.BlackScreenRecoveryAction
import com.vaydelux.bousygram.core.media.diagnostics.EngineVerdictStore
import com.vaydelux.bousygram.data.store.SelectedPlayerEngine
import com.vaydelux.bousygram.presentation.components.BousygramButton
import com.vaydelux.bousygram.presentation.media.VideoSurfaceView
import com.vaydelux.bousygram.presentation.theme.BousygramTheme
import kotlinx.coroutines.delay

/**
 * Diagnostic Activity for verifying video engines, surface compositing,
 * hardware decoder status, and automated black-screen recovery.
 * Reachable via:
 * adb shell am start -a com.vaydelux.bousygram.DEBUG_ENGINE_LAB
 */
@OptIn(UnstableApi::class)
class EngineLabActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialUrl = intent.getStringExtra("url") ?: intent.dataString ?: ""

        setContent {
            BousygramTheme {
                EngineLabScreen(
                    initialUrl = initialUrl,
                    onExit = { finish() }
                )
            }
        }
    }
}

@Composable
@OptIn(UnstableApi::class)
fun EngineLabScreen(
    initialUrl: String,
    onExit: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var activeEngineType by remember { mutableStateOf(SelectedPlayerEngine.ExoPlayer) }
    var useTextureView by remember { mutableStateOf(false) }
    var streamUrl by remember { mutableStateOf(initialUrl) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Initializing") }
    var diagnosticLog by remember { mutableStateOf("Engine Lab Ready") }

    val blackScreenDetector = remember { BlackScreenDetector() }

    var playerEngine by remember(activeEngineType) {
        mutableStateOf(
            PlayerEngineFactory.create(
                context = context,
                preferredEngine = activeEngineType,
                preferredSpeed = 1.0f
            )
        )
    }

    DisposableEffect(playerEngine) {
        onDispose {
            playerEngine.detachSurface()
            playerEngine.stop()
            playerEngine.release()
        }
    }

    LaunchedEffect(playerEngine, streamUrl) {
        playerEngine.stop()
        blackScreenDetector.reset()
        statusText = "Preparing ${playerEngine.engineName}"
        playerEngine.prepare(streamUrl)
        playerEngine.play()
    }

    LaunchedEffect(playerEngine) {
        playerEngine.playbackState.collect { state ->
            statusText = when (state) {
                is EnginePlaybackState.Idle -> "Idle"
                is EnginePlaybackState.Buffering -> "Buffering"
                is EnginePlaybackState.Ready -> "Playing (${playerEngine.activeDecoder})"
                is EnginePlaybackState.Ended -> "Ended"
                is EnginePlaybackState.Error -> "Error: ${state.message}"
            }
        }
    }

    LaunchedEffect(playerEngine) {
        while (true) {
            currentPositionMs = playerEngine.currentPositionMs
            durationMs = playerEngine.durationMs
            isPlaying = playerEngine.isPlaying

            val recovery = blackScreenDetector.evaluate(
                context = context,
                currentEngine = activeEngineType,
                isPlaying = isPlaying,
                isBuffering = statusText.startsWith("Buffering"),
                hasFirstFrame = currentPositionMs > 500L,
                currentPositionMs = currentPositionMs,
                decoderName = playerEngine.activeDecoder,
                mediaKey = if (initialUrl.isNotBlank()) initialUrl.hashCode().toString() else "diagnostic_stream"
            )

            if (recovery != BlackScreenRecoveryAction.NONE) {
                diagnosticLog = "Triggered: $recovery\nReason: ${blackScreenDetector.lastDiagnostic?.triggerReason}"
                when (recovery) {
                    BlackScreenRecoveryAction.SWITCH_TO_TEXTURE_VIEW -> {
                        useTextureView = true
                    }
                    BlackScreenRecoveryAction.SWITCH_TO_MPV_HW,
                    BlackScreenRecoveryAction.SWITCH_TO_MPV_SW -> {
                        activeEngineType = SelectedPlayerEngine.MPV
                    }
                    else -> {}
                }
            }

            delay(500L)
        }
    }

    // Read system memory info
    val activityManager = remember { context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager }
    val memInfo = remember(activityManager) {
        ActivityManager.MemoryInfo().also { activityManager?.getMemoryInfo(it) }
    }
    val availMb = memInfo.availMem / (1024 * 1024)
    val totalMb = memInfo.totalMem / (1024 * 1024)
    val isLowRam = activityManager?.isLowRamDevice == true

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Left: Video Surface Preview Viewport
            Box(
                modifier = Modifier
                    .weight(0.55f)
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                VideoSurfaceView(
                    engine = playerEngine,
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
                    useTextureView = useTextureView,
                    modifier = Modifier.fillMaxSize()
                )

                // Status Overlay
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.8f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(
                            text = statusText,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Pos: ${currentPositionMs / 1000}s / ${durationMs / 1000}s",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Right: Interactive Controls & Diagnostics Panel
            Column(
                modifier = Modifier
                    .weight(0.45f)
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Bousygram Engine Lab",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    BousygramButton(text = "Close", onClick = onExit)
                }

                // Diagnostics Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "DIAGNOSTICS & TELEMETRY",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text("Active Engine: ${playerEngine.engineName}", style = MaterialTheme.typography.bodySmall)
                        Text("Decoder: ${playerEngine.activeDecoder}", style = MaterialTheme.typography.bodySmall)
                        Text("Surface Mode: ${if (useTextureView) "TextureView" else "SurfaceView (Zero-Copy)"}", style = MaterialTheme.typography.bodySmall)
                        Text("Memory: ${availMb}MB free / ${totalMb}MB total (lowRam=$isLowRam)", style = MaterialTheme.typography.bodySmall)
                        Text("Device: ${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT})", style = MaterialTheme.typography.bodySmall)
                    }
                }

                // Engine Selectors
                Text("Select Engine", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BousygramButton(
                        text = "ExoPlayer (HW)",
                        selected = activeEngineType == SelectedPlayerEngine.ExoPlayer,
                        onClick = { activeEngineType = SelectedPlayerEngine.ExoPlayer }
                    )
                    BousygramButton(
                        text = "MPV",
                        selected = activeEngineType == SelectedPlayerEngine.MPV,
                        onClick = { activeEngineType = SelectedPlayerEngine.MPV }
                    )
                }

                // Surface Mode Toggle
                Text("Surface Compositor Mode", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BousygramButton(
                        text = "SurfaceView",
                        selected = !useTextureView,
                        onClick = { useTextureView = false }
                    )
                    BousygramButton(
                        text = "TextureView",
                        selected = useTextureView,
                        onClick = { useTextureView = true }
                    )
                }

                // Failure Simulation & Recovery Triggers
                Text("Fault Simulation", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BousygramButton(
                        text = "Simulate Compositor Failure",
                        onClick = { blackScreenDetector.simulateCompositorFailure() }
                    )
                    BousygramButton(
                        text = "Clear Verdicts",
                        onClick = {
                            EngineVerdictStore.clearAll(context)
                            diagnosticLog = "Verdicts cleared"
                        }
                    )
                }

                // Diagnostic Log Output
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = diagnosticLog,
                        color = Color(0xFF64FFDA),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}
