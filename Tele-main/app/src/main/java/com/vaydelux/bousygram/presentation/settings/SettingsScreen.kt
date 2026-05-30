package com.vaydelux.bousygram.presentation.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.BuildConfig
import com.vaydelux.bousygram.data.store.BufferMode
import com.vaydelux.bousygram.data.store.EnterprisePreferences
import com.vaydelux.bousygram.data.store.PlaybackDiagnostics
import com.vaydelux.bousygram.data.store.PlaybackDiagnosticsSnapshot
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import com.vaydelux.bousygram.presentation.components.BousygramButton
import com.vaydelux.bousygram.presentation.components.BousygramChip
import com.vaydelux.bousygram.presentation.components.BousygramScaffold
import com.vaydelux.bousygram.presentation.components.EnterprisePanel
import com.vaydelux.bousygram.presentation.components.FeatureRow
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val adaptive = rememberAdaptiveMetrics()
    val clipboard = remember {
        context.getSystemService(ClipboardManager::class.java)
    }
    var safeMode by remember { mutableStateOf(EnterprisePreferences.safeMode()) }
    var hideWatched by remember { mutableStateOf(EnterprisePreferences.hideWatched()) }
    var autoPlayNext by remember { mutableStateOf(EnterprisePreferences.autoPlayNext()) }
    var subtitlesDefault by remember { mutableStateOf(EnterprisePreferences.subtitlesDefault()) }
    var speed by remember { mutableFloatStateOf(EnterprisePreferences.preferredSpeed()) }
    var bufferMode by remember { mutableStateOf(EnterprisePreferences.bufferMode()) }
    val playbackDiagnostics = remember { PlaybackDiagnostics.snapshot() }
    val cacheSize by produceState(initialValue = 0L, context) {
        value = withContext(Dispatchers.IO) {
            directorySize(context.cacheDir) + directorySize(context.filesDir)
        }
    }
    val diagnostics = remember(
        safeMode,
        hideWatched,
        autoPlayNext,
        subtitlesDefault,
        speed,
        bufferMode,
        cacheSize
    ) {
        EnterprisePreferences.diagnosticsText(cacheSize, BuildConfig.VERSION_NAME)
    }

    BousygramScaffold(modifier) {
        if (adaptive.isCompact) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    SettingsActions(
                        diagnostics = diagnostics,
                        clipboard = clipboard,
                        onBack = onBack
                    )
                }
                settingsItems(
                    safeMode = safeMode,
                    hideWatched = hideWatched,
                    autoPlayNext = autoPlayNext,
                    subtitlesDefault = subtitlesDefault,
                    speed = speed,
                    bufferMode = bufferMode,
                    cacheSize = cacheSize,
                    playbackDiagnostics = playbackDiagnostics,
                    diagnostics = diagnostics,
                    onSafeMode = {
                        safeMode = it
                        EnterprisePreferences.setSafeMode(it)
                    },
                    onHideWatched = {
                        hideWatched = it
                        EnterprisePreferences.setHideWatched(it)
                    },
                    onAutoPlayNext = {
                        autoPlayNext = it
                        EnterprisePreferences.setAutoPlayNext(it)
                    },
                    onSubtitlesDefault = {
                        subtitlesDefault = it
                        EnterprisePreferences.setSubtitlesDefault(it)
                    },
                    onSpeed = {
                        speed = it
                        EnterprisePreferences.setPreferredSpeed(it)
                    },
                    onBufferMode = {
                        bufferMode = it
                        EnterprisePreferences.setBufferMode(it)
                    }
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                SettingsActions(
                    diagnostics = diagnostics,
                    clipboard = clipboard,
                    onBack = onBack,
                    modifier = Modifier.width(280.dp)
                )
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .focusGroup(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    settingsItems(
                        safeMode = safeMode,
                        hideWatched = hideWatched,
                        autoPlayNext = autoPlayNext,
                        subtitlesDefault = subtitlesDefault,
                        speed = speed,
                        bufferMode = bufferMode,
                        cacheSize = cacheSize,
                        playbackDiagnostics = playbackDiagnostics,
                        diagnostics = diagnostics,
                        onSafeMode = {
                            safeMode = it
                            EnterprisePreferences.setSafeMode(it)
                        },
                        onHideWatched = {
                            hideWatched = it
                            EnterprisePreferences.setHideWatched(it)
                        },
                        onAutoPlayNext = {
                            autoPlayNext = it
                            EnterprisePreferences.setAutoPlayNext(it)
                        },
                        onSubtitlesDefault = {
                            subtitlesDefault = it
                            EnterprisePreferences.setSubtitlesDefault(it)
                        },
                        onSpeed = {
                            speed = it
                            EnterprisePreferences.setPreferredSpeed(it)
                        },
                        onBufferMode = {
                            bufferMode = it
                            EnterprisePreferences.setBufferMode(it)
                        }
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.settingsItems(
    safeMode: Boolean,
    hideWatched: Boolean,
    autoPlayNext: Boolean,
    subtitlesDefault: Boolean,
    speed: Float,
    bufferMode: BufferMode,
    cacheSize: Long,
    playbackDiagnostics: PlaybackDiagnosticsSnapshot,
    diagnostics: String,
    onSafeMode: (Boolean) -> Unit,
    onHideWatched: (Boolean) -> Unit,
    onAutoPlayNext: (Boolean) -> Unit,
    onSubtitlesDefault: (Boolean) -> Unit,
    onSpeed: (Float) -> Unit,
    onBufferMode: (BufferMode) -> Unit
) {
                item {
                    Section("Playback Defaults") {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { value ->
                                BousygramChip(
                                    text = "${value}x",
                                    selected = speed == value,
                                    onClick = {
                                        onSpeed(value)
                                    }
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        FeatureRow("Auto-play next", if (autoPlayNext) "On" else "Off", {
                            onAutoPlayNext(!autoPlayNext)
                        })
                        FeatureRow("Subtitles by default", if (subtitlesDefault) "On" else "Off", {
                            onSubtitlesDefault(!subtitlesDefault)
                        })
                    }
                }
                item {
                    Section("Stream Quality") {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            BufferMode.entries.forEach { mode ->
                                BousygramChip(
                                    text = mode.label,
                                    selected = bufferMode == mode,
                                    onClick = {
                                        onBufferMode(mode)
                                    }
                                )
                            }
                        }
                    }
                }
                item {
                    Section("Library Controls") {
                        FeatureRow("Safe mode", if (safeMode) "Private titles" else "Show titles", {
                            onSafeMode(!safeMode)
                        })
                        FeatureRow("Continue only", if (hideWatched) "On" else "Off", {
                            onHideWatched(!hideWatched)
                        })
                        FeatureRow("Favorites", "${EnterprisePreferences.favoriteKeys().size} saved", {})
                        FeatureRow("Pinned sources", "${EnterprisePreferences.pinnedChatIds().size} pinned", {})
                    }
                }
                item {
                    Section("Storage Manager") {
                        FeatureRow("Local app/cache footprint", formatBytes(cacheSize), {})
                        FeatureRow("Background full-video cache", "Disabled", {})
                        FeatureRow("Offline pins", "Managed per video", {})
                    }
                }
                item {
                    Section("Playback Diagnostics") {
                        FeatureRow("First frame", "${playbackDiagnostics.firstFrameMs} ms", {})
                        FeatureRow("Retries / rebuffers", "${playbackDiagnostics.retryCount} / ${playbackDiagnostics.rebufferCount}", {})
                        FeatureRow("Decoder", playbackDiagnostics.decoderName, {})
                        FeatureRow("Dropped frames", playbackDiagnostics.droppedFrames.toString(), {})
                        FeatureRow("Active chunks", playbackDiagnostics.activeChunkCount.toString(), {})
                        FeatureRow("Last stalled byte", playbackDiagnostics.lastStalledOffset.toString(), {})
                        FeatureRow("Proxy status", playbackDiagnostics.proxyStatus, {})
                        FeatureRow("Read source", playbackDiagnostics.readSource, {})
                    }
                }
                item {
                    Section("Diagnostics") {
                        Text(
                            diagnostics,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
}

@Composable
private fun SettingsActions(
    diagnostics: String,
    clipboard: ClipboardManager?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Settings",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Playback, cache, diagnostics, and lightweight enterprise preferences.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(8.dp))
        BousygramButton("Back", onBack, modifier = Modifier.fillMaxWidth())
        BousygramButton(
            "Export diagnostics",
            onClick = {
                runCatching {
                    val file = File(context.cacheDir, "bousygram-diagnostics.txt")
                    file.writeText(diagnostics)
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, diagnostics)
                    val chooser = Intent.createChooser(send, "Export diagnostics")
                    val canHandle = chooser.resolveActivity(context.packageManager) != null
                    if (canHandle) {
                        context.startActivity(chooser)
                    } else {
                        EnterprisePreferences.setLastError("No app available to export diagnostics")
                    }
                }.onFailure { error ->
                    EnterprisePreferences.setLastError(error.message ?: "Diagnostics export failed")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        BousygramButton(
            "Copy settings JSON",
            onClick = {
                runCatching {
                    val manager = clipboard
                    if (manager == null) {
                        EnterprisePreferences.setLastError("Clipboard service unavailable")
                        return@runCatching
                    }
                    manager.setPrimaryClip(
                        ClipData.newPlainText("Bousygram settings", EnterprisePreferences.exportSettingsJson())
                    )
                }.onFailure { error ->
                    EnterprisePreferences.setLastError(error.message ?: "Settings export failed")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        BousygramButton(
            "Import clipboard JSON",
            onClick = {
                val text = clipboard
                    ?.primaryClip
                    ?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)
                    ?.coerceToText(context)
                    ?.toString()
                    .orEmpty()
                EnterprisePreferences.importSettingsJson(text)
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun Section(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    EnterprisePanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

private fun directorySize(file: File?): Long {
    if (file == null || !file.exists()) return 0L
    if (file.isFile) return file.length()
    return file.listFiles()?.sumOf { directorySize(it) } ?: 0L
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.1f GB".format(mb / 1024.0) else "%.1f MB".format(mb)
}
