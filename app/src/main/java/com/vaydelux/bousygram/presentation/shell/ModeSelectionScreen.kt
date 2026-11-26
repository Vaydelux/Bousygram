package com.vaydelux.bousygram.presentation.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import com.vaydelux.bousygram.presentation.components.BousygramBrandMark
import com.vaydelux.bousygram.presentation.components.BousygramButton
import com.vaydelux.bousygram.presentation.components.BousygramDynamicIsland
import com.vaydelux.bousygram.presentation.components.BousygramGlassLevel
import com.vaydelux.bousygram.presentation.components.BousygramIcon
import com.vaydelux.bousygram.presentation.components.BousygramIconRef
import com.vaydelux.bousygram.presentation.components.BousygramIcons
import com.vaydelux.bousygram.presentation.components.DynamicIslandAction
import com.vaydelux.bousygram.presentation.components.DynamicIslandActivityState
import com.vaydelux.bousygram.presentation.components.DynamicIslandTone
import com.vaydelux.bousygram.presentation.components.EnterprisePanel
import com.vaydelux.bousygram.presentation.components.PremiumGridBackdrop
import com.vaydelux.bousygram.presentation.components.liquidGlassSheen
import com.vaydelux.bousygram.presentation.components.resolveGlassMaterial
import com.vaydelux.bousygram.presentation.theme.BousygramUiTokens
import com.vaydelux.bousygram.presentation.theme.LocalBousygramHaloColors
import com.vaydelux.bousygram.presentation.theme.LocalBousygramReduceTransparency
import com.vaydelux.bousygram.presentation.theme.LocalBousygramStyleSpec
import com.vaydelux.bousygram.presentation.theme.rememberBousygramMotionPolicy
import com.vaydelux.bousygram.presentation.theme.BousygramThemePreset
import com.vaydelux.bousygram.presentation.theme.BousygramDesignStyle
import com.vaydelux.bousygram.presentation.theme.BousygramBackdropPattern

/** Route boundary only. Neither screen creates a Telegram or Extras API client. */
enum class BousygramMode { SELECT, TELEGRAM, EXTRAS_MAINTENANCE }

@Composable
fun BousygramModeSelection(
    onTelegram: () -> Unit,
    onExtras: () -> Unit,
    themePreset: BousygramThemePreset = BousygramThemePreset.DARK,
    designStyle: BousygramDesignStyle = BousygramDesignStyle.CINEMA_HALO,
    backdropPattern: BousygramBackdropPattern = BousygramBackdropPattern.AMBIENT_HALO,
    onThemePresetChange: (BousygramThemePreset) -> Unit = {},
    onDesignStyleChange: (BousygramDesignStyle) -> Unit = {},
    onBackdropPatternChange: (BousygramBackdropPattern) -> Unit = {},
    onOpenCastLab: (() -> Unit)? = null
) {
    val metrics = rememberAdaptiveMetrics()
    var appearanceOpen by remember { mutableStateOf(false) }
    if (appearanceOpen) {
        StartupAppearanceDialog(
            themePreset = themePreset,
            designStyle = designStyle,
            backdropPattern = backdropPattern,
            onThemePresetChange = onThemePresetChange,
            onDesignStyleChange = onDesignStyleChange,
            onBackdropPatternChange = onBackdropPatternChange,
            onDismiss = { appearanceOpen = false }
        )
    }
    val compact = metrics.isTv || metrics.widthDp > metrics.heightDp ||
        metrics.heightDp < 620.dp
    val horizontalCards = compact && metrics.widthDp >= 690.dp
    PremiumGridBackdrop {
        Box(
            // Expose the existing mode-card tags to real-activity UIAutomator tests.
            // No visible text, layout, styling or focus behavior changes.
            Modifier.fillMaxSize()
                .semantics { testTagsAsResourceId = true }
                .windowInsetsPadding(WindowInsets.safeDrawing).padding(
                horizontal = if (metrics.isTv) 32.dp else 18.dp,
                vertical = if (compact) 8.dp else 22.dp
            ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.widthIn(max = if (horizontalCards) 1020.dp else 760.dp)
                    .fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                // One gap between major groups; the hero and cards own their
                // smaller internal gaps instead of inheriting this large gap.
                verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 20.dp)
            ) {
                if (LocalBousygramStyleSpec.current.isLiquidGlass) {
                    // Top-center iOS 27/28 Dynamic Island status capsule (linear D-pad top stop)
                    BousygramDynamicIsland(
                        compact = compact && metrics.widthDp < 600.dp,
                        state = DynamicIslandActivityState(
                            title = "Bousygram Ready",
                            badge = if (designStyle == BousygramDesignStyle.IPHONE_GLASS) "iOS 27" else designStyle.displayName,
                            tone = DynamicIslandTone.ONLINE,
                            liveHeadline = "Bousygram Workspace",
                            liveDetail = "Choose Telegram Media or Movies, TV Shows & Anime, or customize Liquid Glass.",
                            statusTag = "Workspace Ready",
                            actions = listOf(
                                DynamicIslandAction("telegram", "Telegram Media", BousygramIcons.Telegram, true, onTelegram),
                                DynamicIslandAction("extras", "Explore Extras", BousygramIcons.Anime, false, onExtras),
                                DynamicIslandAction("appearance", "Appearance Studio", BousygramIcons.Theme, false) {
                                    appearanceOpen = true
                                }
                            )
                        )
                    )
                }
                if (compact) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        BousygramBrandMark(size = 56.dp, squircleFrame = true)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "Bousygram",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Your screen. Your library.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().testTag("startup-hero"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BousygramBrandMark(size = 82.dp, squircleFrame = true)
                        Text(
                            "Bousygram",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Your screen. Your library.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                BousygramButton(
                    text = "Change appearance",
                    onClick = { appearanceOpen = true },
                    leadingIcon = BousygramIcons.Theme,
                    modifier = Modifier.widthIn(min = 232.dp).testTag("startup-change-appearance")
                )
                if (horizontalCards) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ModeOption(
                            title = "Telegram Media",
                            subtitle = "Saved videos, channels and group media",
                            onClick = onTelegram,
                            primary = true,
                            modeTag = "mode-telegram",
                            icon = BousygramIcons.Telegram,
                            modifier = Modifier.weight(1f)
                        )
                        ModeOption(
                            title = "Movies, TV Shows & Anime",
                            subtitle = "Movies, TV shows and anime catalogs",
                            onClick = onExtras,
                            primary = false,
                            modeTag = "mode-extras",
                            icon = BousygramIcons.Anime,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().testTag("startup-mode-cards"),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                    ModeOption(
                        title = "Telegram Media",
                        subtitle = "Saved videos, channels and group media",
                        onClick = onTelegram,
                        primary = true,
                        modeTag = "mode-telegram",
                        icon = BousygramIcons.Telegram
                    )
                    ModeOption(
                        title = "Movies, TV Shows & Anime",
                        subtitle = "Movies, TV shows and anime catalogs",
                        onClick = onExtras,
                        primary = false,
                        modeTag = "mode-extras",
                        icon = BousygramIcons.Anime
                    )
                    }
                }
                if (onOpenCastLab != null) {
                    BousygramButton(
                        text = "Cast devices (Preview)",
                        onClick = onOpenCastLab,
                        modifier = Modifier.widthIn(min = 232.dp).testTag("startup-cast-lab")
                    )
                }
                if (!compact) {
                    Text(
                        "TMDB and AniList provide metadata. External video playback is separate from Telegram.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeOption(
    title: String,
    subtitle: String,
    primary: Boolean,
    modeTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: BousygramIconRef = if (primary) BousygramIcons.Telegram else BousygramIcons.Anime
) {
    var focused by remember { mutableStateOf(false) }
    val motion = rememberBousygramMotionPolicy()
    val isShortWindow = rememberAdaptiveMetrics().isShortWindow
    // Keep both mode cards the same text-row height even when one title wraps.
    // Font scaling can still expand the cards instead of clipping the content.
    val subtitleLines = 2
    val halo = LocalBousygramHaloColors.current
    val styleSpec = LocalBousygramStyleSpec.current
    val reduceTransparency = LocalBousygramReduceTransparency.current
    val focusScale by animateFloatAsState(
        targetValue = if (focused && motion.animateFocusScale) BousygramUiTokens.focusScale else 1f,
        animationSpec = tween(
            durationMillis = motion.duration(BousygramUiTokens.focusDurationMs),
            easing = FastOutSlowInEasing
        ),
        label = "mode-focus"
    )
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(styleSpec.panelCorner)
    val glassMaterial = resolveGlassMaterial(
        level = BousygramGlassLevel.PANEL,
        colors = colors,
        halo = halo,
        reduceTransparency = reduceTransparency,
        lowRam = motion.lowRam,
        accentTinted = primary && styleSpec.isLiquidGlass
    )
    val borderWidth = when {
        focused -> BousygramUiTokens.tvFocusOutline
        halo.isHighContrast -> 1.5.dp
        styleSpec.isLiquidGlass -> glassMaterial.borderWidth
        else -> 0.dp
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(modeTag)
            .graphicsLayer { scaleX = focusScale; scaleY = focusScale }
            .then(
                if (borderWidth > 0.dp) {
                    if (styleSpec.isLiquidGlass && !focused && !halo.isHighContrast) {
                        Modifier.border(
                            width = borderWidth,
                            brush = Brush.verticalGradient(listOf(glassMaterial.rimTop, glassMaterial.rimBottom)),
                            shape = shape
                        )
                    } else {
                        Modifier.border(
                            width = borderWidth,
                            color = if (focused) colors.primary else colors.outline,
                            shape = shape
                        )
                    }
                } else Modifier
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick),
        shape = shape,
        color = when {
            focused -> colors.surfaceContainerHighest
            styleSpec.isLiquidGlass -> glassMaterial.fill
            else -> colors.surfaceContainer
        },
        tonalElevation = if (styleSpec.isLiquidGlass) 0.dp else if (focused) 4.dp else 1.dp,
        shadowElevation = if (styleSpec.isLiquidGlass && !motion.lowRam && !halo.isHighContrast) {
            if (focused) 8.dp else glassMaterial.shadowElevation
        } else 0.dp
    ) {
        Row(
            Modifier.fillMaxWidth()
                .liquidGlassSheen(
                    enabled = styleSpec.isLiquidGlass && glassMaterial.showSpecularSheen && !focused,
                    topColor = glassMaterial.specularTop,
                    coverageFraction = 0.34f
                )
                .heightIn(min = if (isShortWindow) 112.dp else 140.dp)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (primary) colors.primaryContainer else colors.secondaryContainer
            ) {
                Box(modifier = Modifier.size(54.dp), contentAlignment = Alignment.Center) {
                    BousygramIcon(
                        icon = icon,
                        contentDescription = null,
                        tint = if (primary) colors.onPrimaryContainer else colors.onSecondaryContainer,
                        size = 24.dp
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    title, style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface, fontWeight = FontWeight.SemiBold,
                    minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitle, style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    minLines = subtitleLines, maxLines = subtitleLines,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BousygramIcon(
                icon = BousygramIcons.ChevronRight,
                contentDescription = null,
                tint = if (focused) colors.primary else colors.onSurfaceVariant,
                size = 22.dp
            )
        }
    }
}

/** Static offline placeholder: NO WebView, TMDB, AniList, network call, or virtual-mouse dependency. */
@Composable
fun ExtrasMaintenanceScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    PremiumGridBackdrop {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp), contentAlignment = Alignment.Center) {
            EnterprisePanel(
                modifier = Modifier.widthIn(max = 580.dp).fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    BousygramBrandMark(size = 72.dp)
                    Text(
                        "More to Explore",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "Movies, TV shows and anime are available through Extras. External source availability varies.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "Extras uses separate web sessions and does not access your Telegram account or playback cache.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    BousygramButton(
                        text = "Back to Mode Selection",
                        onClick = onBack,
                        selected = true,
                        leadingIcon = BousygramIcons.Back,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
}

/** Real Telegram authorization readiness, styled in the minimalist Kakei/Portfolio glass splash language. */
@Composable
fun TelegramStartupLoadingScreen(connectionStatus: String?) {
    val motion = rememberBousygramMotionPolicy()
    val colors = MaterialTheme.colorScheme
    PremiumGridBackdrop {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp), contentAlignment = Alignment.Center) {
            EnterprisePanel(
                modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    BousygramBrandMark(size = 70.dp, squircleFrame = true)
                    Text(
                        "Preparing Telegram Media",
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.onSurface,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    if (motion.animateIndeterminateProgress) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth(0.78f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(BousygramUiTokens.pillCorner)),
                            color = colors.primary,
                            trackColor = colors.surfaceVariant.copy(alpha = 0.65f)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.78f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(BousygramUiTokens.pillCorner))
                                .background(
                                    Brush.horizontalGradient(listOf(colors.primary, colors.secondary))
                                )
                        )
                    }
                    Text(
                        connectionStatus?.takeIf { it.isNotBlank() } ?: "Connecting securely to your Telegram session…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
