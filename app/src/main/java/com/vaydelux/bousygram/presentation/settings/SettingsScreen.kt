package com.vaydelux.bousygram.presentation.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.BuildConfig
import com.vaydelux.bousygram.core.tdlib.cache.TelegramCachePruner
import com.vaydelux.bousygram.core.tdlib.cache.TelegramCacheStorageStats
import com.vaydelux.bousygram.data.store.EnterprisePreferences
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import com.vaydelux.bousygram.presentation.components.BousygramBackdropGallery
import com.vaydelux.bousygram.presentation.components.BousygramBrandMark
import com.vaydelux.bousygram.presentation.components.BousygramButton
import com.vaydelux.bousygram.presentation.components.BousygramChip
import com.vaydelux.bousygram.presentation.components.BousygramDesignStyleGallery
import com.vaydelux.bousygram.presentation.components.BousygramIcon
import com.vaydelux.bousygram.presentation.components.BousygramIconRef
import com.vaydelux.bousygram.presentation.components.BousygramIcons
import com.vaydelux.bousygram.presentation.components.BousygramPageHeading
import com.vaydelux.bousygram.presentation.components.BousygramQuickLookBar
import com.vaydelux.bousygram.presentation.components.BousygramScaffold
import com.vaydelux.bousygram.presentation.components.BousygramThemeGallery
import com.vaydelux.bousygram.presentation.components.EnterprisePanel
import com.vaydelux.bousygram.presentation.theme.BousygramBackdropPattern
import com.vaydelux.bousygram.presentation.theme.BousygramDesignStyle
import com.vaydelux.bousygram.presentation.theme.BousygramThemePreset
import com.vaydelux.bousygram.presentation.theme.LocalBousygramDisplaySpec
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings Screen strictly constrained to:
 * 1. Privacy mode (blur posters)
 * 2. Subtitle language
 * 3. Clear cache
 * 4. Export diagnostics
 * 5. Log out
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    themePreset: BousygramThemePreset = BousygramThemePreset.DARK,
    onThemePresetChange: (BousygramThemePreset) -> Unit = {},
    designStyle: BousygramDesignStyle = BousygramDesignStyle.CINEMA_HALO,
    onDesignStyleChange: (BousygramDesignStyle) -> Unit = {},
    backdropPattern: BousygramBackdropPattern = BousygramBackdropPattern.AMBIENT_HALO,
    onBackdropPatternChange: (BousygramBackdropPattern) -> Unit = {},
    modifier: Modifier = Modifier,
    onLogout: () -> Unit = {},
    onOpenAbout: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val adaptive = rememberAdaptiveMetrics()
    val clipboard = remember { context.getSystemService(ClipboardManager::class.java) }
    val tdLibCacheDir = remember(context) { File(context.filesDir, "tdlib_files") }

    var safeMode by remember { mutableStateOf(EnterprisePreferences.safeMode()) }
    var subtitleLang by remember { mutableStateOf(EnterprisePreferences.subtitleLanguage()) }
    var playerEngine by remember { mutableStateOf(EnterprisePreferences.selectedPlayerEngine()) }
    val audioProfile by EnterprisePreferences.audioProcessingProfileFlow.collectAsState()
    var videoDebugHud by remember { mutableStateOf(EnterprisePreferences.videoDebugHudEnabled()) }
    var streamOnlyPreview by remember { mutableStateOf(EnterprisePreferences.streamOnlyPreviewEnabled()) }
    var surfaceType by remember { mutableStateOf(EnterprisePreferences.surfaceTypePreference()) }
    var mpvHwDecMode by remember { mutableStateOf(EnterprisePreferences.mpvHwDecMode()) }
    var storageRefresh by remember { mutableIntStateOf(0) }
    var clearCacheStatus by remember { mutableStateOf<String?>(null) }
    var isClearingCache by remember { mutableStateOf(false) }
    var isAdvancedExpanded by remember { mutableStateOf(false) }

    val storageStats by produceState(
        initialValue = TelegramCacheStorageStats(
            appCacheBytes = 0L,
            appFilesBytes = 0L,
            tdLibCacheBytes = 0L,
            cacheLimitBytes = TelegramCachePruner.calculateDynamicCacheLimit(context)
        ),
        context,
        storageRefresh
    ) {
        value = withContext(Dispatchers.IO) {
            TelegramCachePruner.storageStats(context, tdLibCacheDir)
        }
    }
    // The filesDir remainder is not automatically evictable cache.
    // These sampled locations exclude databases, shared preferences and APKs.
    val cacheSize = storageStats.measuredCacheBytes

    val diagnosticsText = remember(safeMode, subtitleLang, playerEngine, audioProfile, cacheSize) {
        EnterprisePreferences.diagnosticsText(cacheSize, BuildConfig.VERSION_NAME)
    }

    val subtitleLanguages = listOf(
        "en" to "English",
        "es" to "Spanish",
        "fr" to "French",
        "de" to "German",
        "ja" to "Japanese",
        "ko" to "Korean",
        "ar" to "Arabic",
        "off" to "Off"
    )

    // A wide but short landscape window must retain the compact settings header.
    val displaySpec = LocalBousygramDisplaySpec.current
    val showSidebar = !adaptive.isCompact &&
        adaptive.effectiveWidthDp >= 900.dp && adaptive.effectiveHeightDp >= 520.dp
    val contentGutter = displaySpec.horizontalPadding
    BousygramScaffold(modifier) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = displaySpec.maxReadingWidth)
                    .padding(horizontal = contentGutter, vertical = if (adaptive.isCompact) 14.dp else 22.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                if (showSidebar) {
                    Column(
                        modifier = Modifier.width(280.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        BousygramPageHeading(
                            eyebrow = "",
                            title = "Settings",
                            subtitle = "Appearance, playback and privacy controls.",
                            compact = false,
                            icon = BousygramIcons.Settings
                        )
                        Spacer(Modifier.height(8.dp))
                        BousygramButton(
                            text = "Back",
                            onClick = onBack,
                            leadingIcon = BousygramIcons.Back,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.weight(1f))
                        BousygramButton(
                            text = "About & TMDB Notice",
                            onClick = onOpenAbout,
                            leadingIcon = BousygramIcons.Info,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (!showSidebar) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BousygramBrandMark(size = 36.dp)
                                Text(
                                    text = "Settings",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                BousygramButton(
                                    text = "Back",
                                    onClick = onBack,
                                    leadingIcon = BousygramIcons.Back
                                )
                            }
                        }
                    }

                    // 1. Appearance Studio (Quick Looks, Design Style, Backdrop Pattern, 16 Themes)
                    item {
                        EnterprisePanel(Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                SettingsSectionTitle(
                                    icon = BousygramIcons.Wand,
                                    title = "Quick Looks"
                                )
                                Text(
                                    "Apply a preset pairing of theme, design style, and background.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                BousygramQuickLookBar(
                                    activeTheme = themePreset,
                                    activeStyle = designStyle,
                                    activePattern = backdropPattern,
                                    onApplyLook = { look ->
                                        // Save the complete look before updating Compose state.
                                        EnterprisePreferences.setVisualAppearance(
                                            look.preset.id, look.style.id, look.pattern.id
                                        )
                                        onThemePresetChange(look.preset)
                                        onDesignStyleChange(look.style)
                                        onBackdropPatternChange(look.pattern)
                                    }
                                )
                            }
                        }
                    }

                    item {
                        EnterprisePanel(Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                SettingsSectionTitle(
                                    icon = BousygramIcons.Layers,
                                    title = "UI Design Style"
                                )
                                Text(
                                    "Choose component shape and surface treatment.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                BousygramDesignStyleGallery(
                                    selected = designStyle,
                                    onSelect = { style ->
                                        onDesignStyleChange(style)
                                        EnterprisePreferences.setDesignStyleId(style.id)
                                    }
                                )
                            }
                        }
                    }

                    item {
                        EnterprisePanel(Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                SettingsSectionTitle(
                                    icon = BousygramIcons.GridPattern,
                                    title = "Background Pattern"
                                )
                                Text(
                                    "Vector backdrop pattern applied across app screens.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                BousygramBackdropGallery(
                                    selected = backdropPattern,
                                    onSelect = { pattern ->
                                        onBackdropPatternChange(pattern)
                                        EnterprisePreferences.setBackdropPatternId(pattern.id)
                                    }
                                )
                            }
                        }
                    }

                    item {
                        EnterprisePanel(Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                SettingsSectionTitle(
                                    icon = BousygramIcons.Theme,
                                    title = "Theme"
                                )
                                Text(
                                    "Choose a color palette, including Daylight and High Contrast.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                BousygramThemeGallery(
                                    selected = themePreset,
                                    onSelect = { preset ->
                                        onThemePresetChange(preset)
                                        EnterprisePreferences.setVisualThemeId(preset.id)
                                    }
                                )
                            }
                        }
                    }

                    // 2. Playback Engine (default Auto)
                    item {
                        EnterprisePanel(Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        SettingsSectionTitle(
                                            icon = BousygramIcons.Decoder,
                                            title = "Playback engine"
                                        )
                                        Text(
                                            "Auto uses ExoPlayer/libmpv. Select LibVLC for a third MKV/TS decoder option; it may use more memory on older TVs.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                                ) {
                                    EnterprisePreferences.supportedPlayerEngines().forEach { engine ->
                                        val isSelected = playerEngine == engine
                                        val chipLabel = when (engine) {
                                            com.vaydelux.bousygram.data.store.SelectedPlayerEngine.AUTO -> "Auto (Default)"
                                            com.vaydelux.bousygram.data.store.SelectedPlayerEngine.ExoPlayer -> "ExoPlayer"
                                            com.vaydelux.bousygram.data.store.SelectedPlayerEngine.MPV -> "libmpv"
                                            com.vaydelux.bousygram.data.store.SelectedPlayerEngine.LIBVLC -> "LibVLC"
                                        }
                                        BousygramChip(
                                            text = chipLabel,
                                            selected = isSelected,
                                            onClick = {
                                                playerEngine = engine
                                                EnterprisePreferences.setSelectedPlayerEngine(engine)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Smart Volume remains; tonal equalizer controls have been retired.
                    item { AudioSettingsPanel(isTv = adaptive.isTv) }

                    // 3. Subtitle Language
                    item {
                        EnterprisePanel(Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                SettingsSectionTitle(
                                    icon = BousygramIcons.Subtitles,
                                    title = "Subtitle language"
                                )
                                Text(
                                    "Preferred language for automatic subtitle track selection during video playback.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    androidx.compose.foundation.lazy.LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(subtitleLanguages.size) { idx ->
                                            val (code, label) = subtitleLanguages[idx]
                                            val selected = subtitleLang.equals(code, ignoreCase = true)
                                            BousygramChip(
                                                text = label,
                                                selected = selected,
                                                onClick = {
                                                    subtitleLang = code
                                                    EnterprisePreferences.setSubtitleLanguage(code)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. Clear Cache (no cache slider)
                    item {
                        EnterprisePanel(Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                SettingsActionLayout(
                                    compact = adaptive.isCompact,
                                    details = {
                                        SettingsSectionTitle(
                                            icon = BousygramIcons.Storage,
                                            title = "Clear cache"
                                        )
                                        Text(
                                            "Cache directories: ${formatBytes(cacheSize)}. " +
                                                "Telegram media target: ${formatBytes(storageStats.cacheLimitBytes)} " +
                                                "(up to 160 MiB; temporary streaming may exceed this target).",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            "TDLib media folder: ${formatBytes(storageStats.tdLibCacheBytes)} · " +
                                                "App cache folder: ${formatBytes(storageStats.appCacheBytes)} · " +
                                                "Other persistent app files: ${formatBytes(storageStats.appFilesBytes)}.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    },
                                    action = {
                                        BousygramButton(
                                            text = if (isClearingCache) "Pruning…" else "Prune Safe Cache",
                                            leadingIcon = BousygramIcons.Trash,
                                            tooltip = "Evict temporary cached media while keeping pinned files",
                                            modifier = if (adaptive.isCompact) Modifier.fillMaxWidth() else Modifier,
                                            onClick = {
                                                if (!isClearingCache) {
                                                    isClearingCache = true
                                                    scope.launch {
                                                        clearCacheStatus = null
                                                        try {
                                                            val result = withContext(Dispatchers.IO) {
                                                                TelegramCachePruner.pruneCacheNow(context, tdLibCacheDir)
                                                            }
                                                            clearCacheStatus =
                                                                "Pruned ${formatBytes(result.freedBytes)} " +
                                                                    "(${result.deletedFiles} tracked files). ${result.note}"
                                                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                                            throw cancelled
                                                        } catch (failure: Exception) {
                                                            android.util.Log.w(
                                                                "BousygramSettings", "Safe cache pruning failed", failure
                                                            )
                                                            clearCacheStatus =
                                                                "Cache pruning could not complete. " +
                                                                    "Pinned and active media were not intentionally removed."
                                                        } finally {
                                                            isClearingCache = false
                                                            storageRefresh++
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    }
                                )
                                Text(
                                    text = "Measured directories: ${formatBytes(storageStats.totalBytes)}. " +
                                        "This includes TDLib databases in app files, but not the installed APK, " +
                                        "all databases or WebView site storage. " +
                                        "Offline pins and active streams can exceed the media cache target. " +
                                        "Resume positions remain saved after safe video-byte eviction.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (clearCacheStatus != null) {
                                    Text(
                                        text = clearCacheStatus.orEmpty(),
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    // 5. Advanced Section (holds diagnostics, privacy mode, account, etc.)
                    item {
                        EnterprisePanel(Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isAdvancedExpanded = !isAdvancedExpanded },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        SettingsSectionTitle(
                                            icon = BousygramIcons.Options,
                                            title = "Advanced"
                                        )
                                        Text(
                                            if (isAdvancedExpanded) "Tap to collapse advanced options" else "Tap to show diagnostics, privacy mode, and account controls",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    BousygramIcon(
                                        icon = if (isAdvancedExpanded) BousygramIcons.ChevronUp else BousygramIcons.ChevronDown,
                                        contentDescription = if (isAdvancedExpanded) "Collapse advanced options" else "Expand advanced options",
                                        tint = MaterialTheme.colorScheme.primary,
                                        size = 20.dp
                                    )
                                }

                                if (isAdvancedExpanded) {
                                    Spacer(Modifier.height(4.dp))

                                    // Privacy Mode
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                BousygramIcon(
                                                    icon = BousygramIcons.Shield,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    size = 18.dp
                                                )
                                                Text(
                                                    "Privacy mode",
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                            Text(
                                                "Blur media posters and thumbnails to protect viewing privacy.",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        Switch(
                                            checked = safeMode,
                                            onCheckedChange = {
                                                safeMode = it
                                                EnterprisePreferences.setSafeMode(it)
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                            )
                                        )
                                    }

                                    Spacer(Modifier.height(12.dp))

                                    if (BuildConfig.DEBUG) {
                                        // Developer Tools Section
                                        Text(
                                            "Developer Tools (In-App Video Debug)",
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )

                                        // Debug-only streaming experiment. Never overrides production behavior.
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                                Text(
                                                    "Stream Only (Experimental)",
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    "Uses finite Telegram download windows and shorter player buffers. " +
                                                        "Reclaims temporary video bytes 30 seconds after exit when safe; " +
                                                        "offline downloads and Continue Watching are preserved. " +
                                                        "Applies to the next playback, and is not yet production-qualified.",
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                            Switch(
                                                checked = streamOnlyPreview || BuildConfig.BOUNDED_FOREGROUND_STREAMING_EXPERIMENT,
                                                enabled = !BuildConfig.BOUNDED_FOREGROUND_STREAMING_EXPERIMENT,
                                                onCheckedChange = {
                                                    streamOnlyPreview = it
                                                    EnterprisePreferences.setStreamOnlyPreviewEnabled(it)
                                                },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                                )
                                            )
                                        }

                                        // Live Video Debug HUD Toggle
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                                Text(
                                                    "Live Video Debug HUD",
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    "Displays 10-ft readable real-time overlay in the player (Engine, Surface, Decoders, Drop counters, Overlays). Toggle with remote Yellow or Info key.",
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                            Switch(
                                                checked = videoDebugHud,
                                                onCheckedChange = {
                                                    videoDebugHud = it
                                                    EnterprisePreferences.setVideoDebugHudEnabled(it)
                                                },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                                )
                                            )
                                        }

                                        // Playback engine is already configured above.
                                        // Avoid duplicate controls and D-pad focus stops.

                                        // Surface Type Switcher
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Text(
                                                "Surface Presentation Mode",
                                                color = MaterialTheme.colorScheme.onSurface,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                "Switch between SurfaceView (hardware overlay) and TextureView (composited view)",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                listOf("auto" to "Auto", "surfaceview" to "SurfaceView", "textureview" to "TextureView").forEach { (type, label) ->
                                                    BousygramChip(
                                                        text = label,
                                                        selected = surfaceType == type,
                                                        onClick = {
                                                            surfaceType = type
                                                            EnterprisePreferences.setSurfaceTypePreference(type)
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        // MPV HWDEC / VO Mode Switcher
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Text(
                                                "MPV Hardware Decoder & VO Mode",
                                                color = MaterialTheme.colorScheme.onSurface,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                "mediacodec-copy works reliably on Mali-G52 GPU. mediacodec is direct embed.",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                listOf(
                                                    "mediacodec-copy" to "vo=gpu (mediacodec-copy)",
                                                    "mediacodec" to "vo=mediacodec_embed",
                                                    "no" to "vo=gpu (Software)"
                                                ).forEach { (mode, label) ->
                                                    BousygramChip(
                                                        text = label,
                                                        selected = mpvHwDecMode == mode,
                                                        onClick = {
                                                            mpvHwDecMode = mode
                                                            EnterprisePreferences.setMpvHwDecMode(mode)
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        // Open Engine Lab Button
                                        SettingsActionLayout(
                                            compact = adaptive.isCompact,
                                            details = {
                                                Text(
                                                    "Engine Lab Diagnostic Activity",
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    "Live engine comparison with surface and buffer telemetry.",
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            },
                                            action = {
                                                BousygramButton(
                                                    text = "Open Lab",
                                                    leadingIcon = BousygramIcons.Decoder,
                                                    tooltip = "Launch Engine Lab diagnostics",
                                                    modifier = if (adaptive.isCompact) Modifier.fillMaxWidth() else Modifier,
                                                    onClick = {
                                                        context.startActivity(Intent(context, com.vaydelux.bousygram.presentation.debug.EngineLabActivity::class.java))
                                                    }
                                                )
                                            }
                                        )

                                        Spacer(Modifier.height(8.dp))

                                        // Export Diagnostics
                                        SettingsActionLayout(
                                            compact = adaptive.isCompact,
                                            details = {
                                                Text(
                                                    "Diagnostics & Health",
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    "Export streaming telemetry, player buffer stats, and session logs.",
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            },
                                            action = {
                                                BousygramButton(
                                                    text = "Export",
                                                    leadingIcon = BousygramIcons.Diagnostics,
                                                    tooltip = "Copy and share diagnostics report",
                                                    modifier = if (adaptive.isCompact) Modifier.fillMaxWidth() else Modifier,
                                                    onClick = {
                                                        val copied = runCatching {
                                                            clipboard?.setPrimaryClip(
                                                                ClipData.newPlainText("Bousygram Diagnostics", diagnosticsText)
                                                            )
                                                            clipboard != null
                                                        }.getOrDefault(false)
                                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                            putExtra(Intent.EXTRA_TEXT, diagnosticsText)
                                                            type = "text/plain"
                                                        }
                                                        val chooser = Intent.createChooser(sendIntent, "Export diagnostics")
                                                        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        val shared = runCatching {
                                                            context.startActivity(chooser)
                                                            true
                                                        }.getOrDefault(false)
                                                        Toast.makeText(
                                                            context,
                                                            when {
                                                                shared -> "Diagnostics ready to share"
                                                                copied -> "Diagnostics copied; no sharing app is available"
                                                                else -> "No clipboard or sharing app is available"
                                                            },
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                )
                                            }
                                        )

                                        Spacer(Modifier.height(8.dp))

                                    }

                                    // About
                                    SettingsActionLayout(
                                        compact = adaptive.isCompact,
                                        details = {
                                            Text(
                                                "About & TMDB Notice",
                                                color = MaterialTheme.colorScheme.onSurface,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                "Metadata and poster attribution under TMDB terms.",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        },
                                        action = {
                                            BousygramButton(
                                                text = "View",
                                                leadingIcon = BousygramIcons.Info,
                                                tooltip = "Open About & TMDB Attribution",
                                                modifier = if (adaptive.isCompact) Modifier.fillMaxWidth() else Modifier,
                                                onClick = onOpenAbout
                                            )
                                        }
                                    )

                                    Spacer(Modifier.height(8.dp))

                                    // Log out
                                    SettingsActionLayout(
                                        compact = adaptive.isCompact,
                                        details = {
                                            Text(
                                                "Log out",
                                                color = MaterialTheme.colorScheme.error,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                "Sign out of Telegram account and reset authenticated sessions.",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        },
                                        action = {
                                            BousygramButton(
                                                text = "Log Out",
                                                leadingIcon = BousygramIcons.Logout,
                                                tooltip = "Sign out of Telegram session",
                                                modifier = if (adaptive.isCompact) Modifier.fillMaxWidth() else Modifier,
                                                onClick = onLogout
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (!showSidebar) {
                        item {
                            BousygramButton(
                                text = "About & TMDB Attribution",
                                onClick = onOpenAbout,
                                leadingIcon = BousygramIcons.Info,
                                tooltip = "Open About & TMDB Attribution",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsActionLayout(
    compact: Boolean,
    details: @Composable () -> Unit,
    action: @Composable () -> Unit
) {
    if (compact) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                details()
            }
            action()
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                details()
            }
            action()
        }
    }
}

@Composable
private fun SettingsSectionTitle(
    icon: BousygramIconRef,
    title: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BousygramIcon(
            icon = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            size = 20.dp
        )
        Text(
            title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.1f GB".format(mb / 1024.0) else "%.1f MB".format(mb)
}
