package com.vaydelux.bousygram.presentation.extras

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.cast.framework.CastButtonFactory
import com.vaydelux.bousygram.BuildConfig
import com.vaydelux.bousygram.core.cast.CastRouteButtonFactory
import com.vaydelux.bousygram.core.cast.ExtrasCastPhase
import com.vaydelux.bousygram.core.cast.ExtrasCastSender
import com.vaydelux.mediaforge.core.MediaForgeCastPlan
import kotlinx.coroutines.delay

/**
 * Opt-in mobile Extras sender controls. Only shown for a qualified source.
 * No automatic cast on connection; the user explicitly starts the video.
 */
@Composable
internal fun ExtrasCastPanel(
    sender: ExtrasCastSender,
    ready: Boolean,
    buildPlan: () -> MediaForgeCastPlan?,
    onHoldLocal: () -> Unit,
    onReturnLocal: (Long?) -> Unit,
    visible: Boolean = true
) {
    if (!BuildConfig.DEBUG || BuildConfig.EXTRAS_CAST_QUALIFIED_HOSTS.isBlank()) return
    val context = LocalContext.current
    val state by sender.state.collectAsStateWithLifecycle()
    var held by remember(sender) { mutableStateOf(false) }
    var localMessage by remember(sender) { mutableStateOf<String?>(null) }
    var routeFailed by remember(sender) { mutableStateOf(false) }
    LaunchedEffect(routeFailed) {
        if (routeFailed) {
            sender.routeSetupFailed()
            routeFailed = false
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) sender.chooseReceiver(context)
        else localMessage = "Local network permission is required for TV discovery."
    }
    DisposableEffect(sender) {
        onDispose { sender.close() }
    }
    LaunchedEffect(sender, state.phase) {
        if (state.phase == ExtrasCastPhase.LOADING || state.phase == ExtrasCastPhase.ACTIVE) {
            while (true) {
                sender.refresh()
                delay(1000)
            }
        } else if (held) {
            held = false
            onReturnLocal(state.positionMs.takeUnless { state.live })
        }
    }
    // Lifecycle, ownership and return-to-local effects remain mounted when
    // local HUD disappears; only the idle visual controls are suppressed.
    if (!visible && !com.vaydelux.bousygram.core.cast.ExtrasCastControlPolicy.keepCastControlsVisible(state.phase)) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val message = localMessage ?: state.message
        if (message.isNotBlank()) {
            Text(message, style = MaterialTheme.typography.labelMedium,
                color = if (localMessage != null || state.phase == ExtrasCastPhase.FAILED)
                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (state.phase) {
                ExtrasCastPhase.IDLE, ExtrasCastPhase.FAILED, ExtrasCastPhase.DISCONNECTED -> {
                    OutlinedButton(
                        onClick = {
                            localMessage = null
                            if (Build.VERSION.SDK_INT >= 37 && ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.ACCESS_LOCAL_NETWORK
                                ) != PackageManager.PERMISSION_GRANTED) {
                                permission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                            } else sender.chooseReceiver(context)
                        },
                        modifier = Modifier.testTag("extras-cast-connect")
                    ) { Text("Connect TV") }
                }
                ExtrasCastPhase.CHOOSING -> {
                    AndroidView(
                        modifier = Modifier.size(48.dp).testTag("extras-cast-route"),
                        factory = { ctx ->
                            try {
                                CastRouteButtonFactory.create(ctx).also {
                                    CastButtonFactory.setUpMediaRouteButton(ctx.applicationContext, it)
                                    it.contentDescription = "Choose a Cast device"
                                }
                            } catch (_: Exception) {
                                routeFailed = true
                                View(ctx)
                            }
                        }
                    )
                    OutlinedButton(onClick = sender::cancelChoice) { Text("Cancel") }
                }
                ExtrasCastPhase.CONNECTED -> {
                    OutlinedButton(
                        enabled = ready,
                        modifier = Modifier.testTag("extras-cast-load"),
                        onClick = {
                            localMessage = null
                            val plan = buildPlan()
                            if (plan == null) {
                                localMessage = "This stream or selected subtitle is not qualified for Cast."
                            } else {
                                onHoldLocal()
                                held = true
                                if (!sender.load(plan)) {
                                    held = false
                                    onReturnLocal(null)
                                    localMessage = "Cast load could not start."
                                }
                            }
                        }
                    ) { Text("Cast video") }
                    OutlinedButton(onClick = sender::disconnect) { Text("Disconnect") }
                }
                ExtrasCastPhase.LOADING -> OutlinedButton(onClick = sender::disconnect) { Text("Cancel Cast") }
                ExtrasCastPhase.ACTIVE -> {
                    OutlinedButton(onClick = { if (state.playing || state.buffering) sender.pause() else sender.play() }) {
                        Text(if (state.playing || state.buffering) "Pause" else "Play")
                    }
                    if (!state.live && state.durationMs > 0L) {
                        OutlinedButton(onClick = { sender.seekBy(-10_000L) }) { Text("−10s") }
                        OutlinedButton(onClick = { sender.seekBy(10_000L) }) { Text("+10s") }
                    }
                    OutlinedButton(onClick = { sender.changeVolume(-0.1) }) { Text("Volume −") }
                    OutlinedButton(onClick = { sender.changeVolume(0.1) }) { Text("Volume +") }
                    OutlinedButton(onClick = sender::disconnect) { Text("Disconnect") }
                }
            }
        }
        if (state.phase == ExtrasCastPhase.ACTIVE && state.captions.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { sender.selectSubtitle(null) }) {
                    Text(if (state.activeCaptionIds.isEmpty()) "✓ Off" else "Off")
                }
                state.captions.forEach { track ->
                    OutlinedButton(onClick = { sender.selectSubtitle(track.id) }) {
                        Text((if (track.id in state.activeCaptionIds) "✓ " else "") + track.label)
                    }
                }
            }
        }
    }
}
