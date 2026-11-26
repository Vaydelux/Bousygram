package com.vaydelux.bousygram.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vaydelux.bousygram.core.tdlib.utils.PlaybackProgressTracker
import com.vaydelux.bousygram.core.tdlib.utils.ThumbnailExtractor
import com.vaydelux.bousygram.core.media.VideoFormatSupport
import com.vaydelux.bousygram.core.account.AccountSession
import com.vaydelux.bousygram.core.tdlib.client.TdLibClient
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import org.drinkless.tdlib.TdApi
import com.vaydelux.bousygram.data.database.RecentSearchQuery
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.SeriesGroup
import com.vaydelux.bousygram.presentation.adaptive.DeviceProfile
import com.vaydelux.bousygram.presentation.adaptive.shouldUseSideRail
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import com.vaydelux.bousygram.presentation.theme.BousygramDesignStyle
import com.vaydelux.bousygram.presentation.theme.BousygramUiTokens
import com.vaydelux.bousygram.presentation.theme.LocalBousygramAppearanceController
import com.vaydelux.bousygram.presentation.theme.LocalBousygramDisplaySpec
import com.vaydelux.bousygram.presentation.theme.LocalBousygramHaloColors
import com.vaydelux.bousygram.presentation.theme.LocalBousygramReduceTransparency
import com.vaydelux.bousygram.presentation.theme.LocalBousygramStyleSpec
import com.vaydelux.bousygram.presentation.theme.rememberBousygramMotionPolicy
import com.vaydelux.bousygram.presentation.theme.MediaCardDensity
import com.vaydelux.bousygram.presentation.theme.mediaCardDensity
import com.vaydelux.bousygram.presentation.theme.tabular
import com.vaydelux.bousygram.presentation.media.mediaDisplayLabels
import java.io.File

enum class ShellDestination(val label: String, val glyph: String) {
    Home("Home", "H"),
    Library("Library", "L"),
    Search("Search", "Q"),
    Me("Me", "M"),
    Continue("Continue", "C"),
    Favorites("Favorites", "F"),
    Offline("Offline", "O"),
    Sources("Library", "S"),
    Settings("Me", "G"),
    About("About", "i");

    companion object {
        val navItems = listOf(Home, Library, Search, Me)
        val sidebarItems = navItems
    }
}

internal fun shellDestinationIcon(destination: ShellDestination): BousygramIconRef = when (destination) {
    ShellDestination.Home -> BousygramIcons.Home
    ShellDestination.Continue -> BousygramIcons.Continue
    ShellDestination.Favorites -> BousygramIcons.Favorites
    ShellDestination.Offline -> BousygramIcons.Offline
    ShellDestination.Library,
    ShellDestination.Sources -> BousygramIcons.Library
    ShellDestination.Search -> BousygramIcons.Search
    ShellDestination.Me -> BousygramIcons.Profile
    ShellDestination.Settings -> BousygramIcons.Settings
    ShellDestination.About -> BousygramIcons.Info
}

@Composable
fun ShellNavIcon(
    destination: ShellDestination,
    tint: Color,
    modifier: Modifier = Modifier
) {
    BousygramIcon(
        icon = shellDestinationIcon(destination),
        contentDescription = null,
        tint = tint,
        size = 20.dp,
        modifier = modifier
    )
}

@Composable
fun CenteredPlayBadge(
    modifier: Modifier = Modifier
) {
    val playBg = MaterialTheme.colorScheme.primary
    val playIcon = MaterialTheme.colorScheme.onPrimary
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(playBg),
        contentAlignment = Alignment.Center
    ) {
        BousygramIcon(
            icon = BousygramIcons.Play,
            contentDescription = null,
            tint = playIcon,
            size = 18.dp
        )
    }
}

@Composable
fun ImagePlaceholderIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    BousygramIcon(
        icon = BousygramIcons.Film,
        contentDescription = null,
        tint = tint,
        size = 26.dp,
        modifier = modifier
    )
}

@Composable
fun CastIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    BousygramIcon(
        icon = BousygramIcons.Cast,
        contentDescription = null,
        tint = tint,
        size = 22.dp,
        modifier = modifier
    )
}

@Composable
fun BousygramAdaptiveShell(
    selected: ShellDestination,
    onSelect: (ShellDestination) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSearch: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenCast: (() -> Unit)? = null,
    showResume: Boolean = false,
    onResume: () -> Unit = {},
    navFocusRequester: FocusRequester? = null,
    content: @Composable () -> Unit
) {
    val adaptive = rememberAdaptiveMetrics()

    when {
        adaptive.isTvLayout -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                TvTopNavBar(
                    selected = selected,
                    onSelect = onSelect,
                    onOpenSearch = onOpenSearch,
                    onOpenProfile = onOpenSettings,
                    showResume = showResume,
                    onResume = onResume,
                    focusRequester = navFocusRequester
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    content()
                }
            }
        }
        shouldUseSideRail(adaptive.profile, adaptive.effectiveHeightDp) -> {
            // Short landscape phones gain an entire row of media by moving
            // navigation into a slim side rail, instead of stacking both
            // the top bar and bottom navigation around a 390dp viewport.
            Row(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
            ) {
                TabletNavRail(
                    selected = selected,
                    onSelect = onSelect,
                    onOpenSearch = onOpenSearch,
                    onOpenProfile = onOpenSettings,
                    focusRequester = navFocusRequester,
                    compact = adaptive.profile == DeviceProfile.PhoneLandscape
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    content()
                }
            }
        }
        else -> {
            // Phone layout: clean frosted top bar with Dynamic Island, content, floating glass capsule dock
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
            ) {
                PhoneTopBar(
                    selected = selected,
                    showResume = showResume,
                    onResume = onResume,
                    onOpenSearch = onOpenSearch,
                    onOpenProfile = onOpenSettings,
                    onOpenCast = onOpenCast
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .consumeWindowInsets(WindowInsets.navigationBars)
                ) {
                    content()
                }
                GlassBottomBar(
                    selected = selected,
                    onSelect = onSelect,
                    modifier = Modifier.navigationBarsPadding()
                )
            }
        }
    }
}

@Composable
fun TabletNavRail(
    selected: ShellDestination,
    onSelect: (ShellDestination) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    compact: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    val halo = LocalBousygramHaloColors.current
    val styleSpec = LocalBousygramStyleSpec.current
    val detached = styleSpec.isLiquidGlass && !compact
    val railShape = RoundedCornerShape(styleSpec.panelCorner)
    Row(
        modifier = modifier
            .fillMaxHeight()
            .then(if (detached) Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp) else Modifier)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .then(
                    if (detached) {
                        Modifier.liquidGlassChrome(
                            shape = railShape,
                            level = BousygramGlassLevel.FLOATING_CHROME
                        )
                    } else Modifier
                )
        ) {
            NavigationRail(
                modifier = Modifier.fillMaxHeight().verticalScroll(rememberScrollState()),
                containerColor = if (detached) Color.Transparent else colors.surfaceContainerLow,
                contentColor = colors.onSurface,
                header = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(
                            top = if (compact) 4.dp else 14.dp,
                            bottom = if (compact) 2.dp else 10.dp
                        )
                    ) {
                        BousygramBrandMark(
                            size = if (compact) 28.dp else 38.dp,
                            squircleFrame = styleSpec.isLiquidGlass && !compact
                        )
                    }
                }
            ) {
                Spacer(Modifier.height(if (compact) 2.dp else 8.dp))
                ShellDestination.navItems.forEach { destination ->
                    val isSelected = when (destination) {
                        ShellDestination.Home -> selected == ShellDestination.Home
                        ShellDestination.Library -> selected == ShellDestination.Library || selected == ShellDestination.Sources
                        ShellDestination.Search -> selected == ShellDestination.Search
                        ShellDestination.Me -> selected == ShellDestination.Me || selected == ShellDestination.Settings
                        else -> destination == selected
                    }
                    var itemFocused by remember { mutableStateOf(false) }
                    val tooltipState = rememberBousygramTooltipState()
                    BousygramTooltipBox(
                        tooltip = if (destination == ShellDestination.Me) "Settings & Profile" else destination.label,
                        icon = shellDestinationIcon(destination),
                        focused = itemFocused,
                        placement = BousygramTooltipPlacement.AUTO_END,
                        state = tooltipState,
                        enabled = compact
                    ) {
                        NavigationRailItem(
                            modifier = Modifier
                                .then(if (isSelected && focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                                .onFocusChanged { itemFocused = it.isFocused },
                            selected = isSelected,
                            onClick = {
                                tooltipState.flashSelection()
                                if (destination == ShellDestination.Search) onOpenSearch() else onSelect(destination)
                            },
                            icon = {
                                ShellNavIcon(
                                    destination = destination,
                                    tint = if (isSelected || itemFocused) colors.primary else colors.onSurfaceVariant,
                                    modifier = Modifier.size(if (compact) 20.dp else 22.dp)
                                )
                            },
                            label = if (compact) null else {
                                {
                                    Text(
                                        destination.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = colors.primary,
                                selectedTextColor = colors.primary,
                                indicatorColor = colors.primaryContainer,
                                unselectedIconColor = colors.onSurfaceVariant,
                                unselectedTextColor = colors.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
        if (!detached) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(halo.glassBorder)
            )
        }
    }
}

@Composable
fun TvTopNavBar(
    selected: ShellDestination,
    onSelect: (ShellDestination) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
    showResume: Boolean = false,
    onResume: () -> Unit = {},
    focusRequester: FocusRequester? = null
) {
    val colors = MaterialTheme.colorScheme
    val halo = LocalBousygramHaloColors.current
    val spec = LocalBousygramDisplaySpec.current
    val styleSpec = LocalBousygramStyleSpec.current
    val appearance = LocalBousygramAppearanceController.current
    var appearancePopoverOpen by remember { mutableStateOf(false) }

    if (appearancePopoverOpen) {
        BousygramAppearancePopoverDialog(
            onDismiss = { appearancePopoverOpen = false },
            onOpenFullStudio = onOpenProfile
        )
    }

    val dockShape = RoundedCornerShape(
        when (styleSpec.style) {
            BousygramDesignStyle.MATERIAL_EXPRESSIVE,
            BousygramDesignStyle.COZY_KAWAII,
            BousygramDesignStyle.IPHONE_GLASS -> BousygramUiTokens.pillCorner
            BousygramDesignStyle.CINEMA_HALO -> styleSpec.cardCorner
        }
    )
    val barShape = RoundedCornerShape(if (styleSpec.isLiquidGlass) 24.dp else 0.dp)
    val outerModifier = if (styleSpec.isLiquidGlass) {
        modifier
            .fillMaxWidth()
            .padding(horizontal = spec.horizontalPadding, vertical = 10.dp)
            .liquidGlassChrome(shape = barShape, level = BousygramGlassLevel.FLOATING_CHROME)
    } else {
        modifier.fillMaxWidth().background(colors.surfaceContainerLow)
    }

    Column(modifier = outerModifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (styleSpec.isLiquidGlass) 16.dp else spec.horizontalPadding,
                    vertical = if (styleSpec.isLiquidGlass) 8.dp else 10.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Brand Halo Badge + "Bousygram" wordmark + Style-Adaptive Nav Dock
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BousygramBrandMark(size = 36.dp, squircleFrame = styleSpec.isLiquidGlass)
                    Text(
                        text = "Bousygram",
                        color = colors.onSurface,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Adaptive Top Nav Pill / Segmented Dock
                Row(
                    modifier = Modifier
                        .clip(dockShape)
                        .background(
                            if (styleSpec.segmentedTabs) colors.surfaceContainerHigh.copy(alpha = 0.72f)
                            else colors.surfaceContainer
                        )
                        .then(
                            if (styleSpec.panelBorderWidth > 0.dp || halo.isHighContrast) {
                                Modifier.border(
                                    width = if (halo.isHighContrast) 1.dp else styleSpec.panelBorderWidth,
                                    color = if (styleSpec.style == BousygramDesignStyle.COZY_KAWAII && !halo.isHighContrast) {
                                        colors.primary.copy(alpha = 0.28f)
                                    } else halo.glassBorder,
                                    shape = dockShape
                                )
                            } else Modifier
                        )
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(ShellDestination.Home, ShellDestination.Library, ShellDestination.Search).forEach { dest ->
                        val isSelected = when (dest) {
                            ShellDestination.Home -> selected == ShellDestination.Home
                            ShellDestination.Library -> selected == ShellDestination.Library || selected == ShellDestination.Sources
                            ShellDestination.Search -> selected == ShellDestination.Search
                            else -> false
                        }
                        var focused by remember { mutableStateOf(false) }
                        val interaction = remember { MutableInteractionSource() }
                        val destIcon = shellDestinationIcon(dest)
                        val tabShape = RoundedCornerShape(
                            if (styleSpec.isLiquidGlass) BousygramUiTokens.pillCorner else styleSpec.tabCorner
                        )
                        val selectedFill = if (styleSpec.segmentedTabs && !halo.isHighContrast) {
                            if (styleSpec.isLiquidGlass && !halo.isLight) colors.primaryContainer else colors.surface
                        } else {
                            colors.primaryContainer
                        }
                        val selectedContent = if (styleSpec.segmentedTabs && !halo.isHighContrast) {
                            if (styleSpec.isLiquidGlass && !halo.isLight) colors.onPrimaryContainer else colors.primary
                        } else {
                            colors.onPrimaryContainer
                        }
                        val tabBorderWidth = when {
                            focused -> BousygramUiTokens.tvFocusOutline
                            isSelected && (!styleSpec.useTonalSurfaces || halo.isHighContrast) -> 1.dp
                            else -> 0.dp
                        }
                        val tabBorderColor = when {
                            focused -> colors.primary
                            isSelected && styleSpec.segmentedTabs && !halo.isHighContrast -> colors.primary.copy(alpha = 0.45f)
                            isSelected -> halo.haloStart.copy(alpha = 0.55f)
                            else -> Color.Transparent
                        }
                        Row(
                            modifier = Modifier
                                .then(if (isSelected && focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                                .clip(tabShape)
                                .background(
                                    when {
                                        focused -> colors.primary.copy(alpha = 0.22f)
                                        isSelected -> selectedFill
                                        else -> Color.Transparent
                                    }
                                )
                                .then(
                                    if (tabBorderWidth > 0.dp) {
                                        Modifier.border(
                                            width = tabBorderWidth,
                                            color = tabBorderColor,
                                            shape = tabShape
                                        )
                                    } else Modifier
                                )
                                .semantics {
                                    contentDescription = dest.label
                                    this.selected = isSelected
                                }
                                .onFocusChanged { focused = it.isFocused }
                                .clickable(
                                    interactionSource = interaction,
                                    indication = ripple(color = colors.primary),
                                    role = Role.Button,
                                    onClick = {
                                        if (dest == ShellDestination.Search) onOpenSearch() else onSelect(dest)
                                    }
                                )
                                .heightIn(min = 48.dp)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BousygramIcon(
                                icon = destIcon,
                                contentDescription = null,
                                tint = when {
                                    isSelected -> selectedContent
                                    focused -> colors.primary
                                    else -> colors.onSurfaceVariant
                                },
                                size = spec.iconSizeAction
                            )
                            Text(
                                text = dest.label,
                                color = when {
                                    isSelected -> if (styleSpec.segmentedTabs && !halo.isHighContrast && halo.isLight) colors.onSurface else colors.onPrimaryContainer
                                    focused -> colors.onSurface
                                    else -> colors.onSurfaceVariant
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (isSelected || focused) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Center: Morphing iOS 27/28 Dynamic Island in linear TV focus chain
            BousygramDynamicIsland(
                state = DynamicIslandActivityState(
                    title = selected.label,
                    badge = if (showResume) "Resume" else null,
                    tone = if (showResume) DynamicIslandTone.ONLINE else DynamicIslandTone.ACCENT,
                    liveHeadline = "Bousygram · ${selected.label}",
                    liveDetail = if (showResume) "Continue watching where you left off or jump anywhere."
                        else "Quick navigation and iOS 27 appearance controls.",
                    statusTag = "Live Activity",
                    actions = buildList {
                        if (showResume) {
                            add(
                                DynamicIslandAction(
                                    id = "resume",
                                    label = "Continue",
                                    icon = BousygramIcons.Continue,
                                    emphasized = true,
                                    onClick = onResume
                                )
                            )
                        }
                        add(
                            DynamicIslandAction(
                                id = "search",
                                label = "Search",
                                icon = BousygramIcons.Search,
                                onClick = onOpenSearch
                            )
                        )
                        add(
                            DynamicIslandAction(
                                id = "settings",
                                label = "Settings",
                                icon = BousygramIcons.Settings,
                                onClick = onOpenProfile
                            )
                        )
                    }
                )
            )

            // Right: Appearance Popover + Profile & Settings circular glass pills
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (appearance != null) {
                    BousygramIconButton(
                        icon = BousygramIcons.Theme,
                        contentDescription = "Appearance settings",
                        tooltip = "Appearance",
                        circular = true,
                        minSize = 48.dp,
                        iconSize = spec.iconSizeAction,
                        tooltipPlacement = BousygramTooltipPlacement.AUTO_BELOW,
                        onClick = { appearancePopoverOpen = true }
                    )
                }
                val isProfileSelected = selected == ShellDestination.Me || selected == ShellDestination.Settings
                BousygramIconButton(
                    icon = BousygramIcons.Settings,
                    contentDescription = "Profile and settings",
                    tooltip = "Settings & Profile",
                    selected = isProfileSelected,
                    circular = true,
                    minSize = 48.dp,
                    iconSize = spec.iconSizeAction,
                    tooltipPlacement = BousygramTooltipPlacement.AUTO_BELOW,
                    onClick = onOpenProfile
                )
            }
        }
        if (!styleSpec.isLiquidGlass) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(halo.glassBorder)
            )
        }
    }
}

@Composable
fun PhoneTopBar(
    onOpenSearch: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
    selected: ShellDestination = ShellDestination.Home,
    showResume: Boolean = false,
    onResume: () -> Unit = {},
    onOpenCast: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val spec = LocalBousygramDisplaySpec.current
    val styleSpec = LocalBousygramStyleSpec.current
    val appearance = LocalBousygramAppearanceController.current
    var appearancePopoverOpen by remember { mutableStateOf(false) }

    if (appearancePopoverOpen) {
        BousygramAppearancePopoverDialog(
            onDismiss = { appearancePopoverOpen = false },
            onOpenFullStudio = onOpenProfile
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spec.horizontalPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BousygramBrandMark(size = 32.dp, squircleFrame = styleSpec.isLiquidGlass)
            if (spec.tier != com.vaydelux.bousygram.presentation.adaptive.BousygramDisplayTier.PhoneCompact) {
                Text(
                    text = "Bousygram",
                    color = colors.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            BousygramDynamicIsland(
                compact = true,
                state = DynamicIslandActivityState(
                    title = selected.label,
                    badge = if (showResume) "Resume" else null,
                    tone = if (showResume) DynamicIslandTone.ONLINE else DynamicIslandTone.ACCENT,
                    liveHeadline = "Bousygram · ${selected.label}",
                    liveDetail = "Quick search, resume playback, and iOS 27 Liquid Glass controls.",
                    actions = buildList {
                        if (showResume) {
                            add(DynamicIslandAction("resume", "Continue", BousygramIcons.Continue, true, onResume))
                        }
                        add(DynamicIslandAction("search", "Search", BousygramIcons.Search, onClick = onOpenSearch))
                        add(DynamicIslandAction("settings", "Settings", BousygramIcons.Settings, onClick = onOpenProfile))
                    }
                )
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (onOpenCast != null) {
                TopBarIconButton(onClick = onOpenCast, contentDescription = "Cast Receiver Info") { tint ->
                    CastIcon(tint = tint)
                }
            }
            if (appearance != null) {
                TopBarIconButton(onClick = { appearancePopoverOpen = true }, contentDescription = "Appearance") { tint ->
                    BousygramIcon(icon = BousygramIcons.Theme, contentDescription = null, tint = tint, size = 19.dp)
                }
            }
            TopBarIconButton(onClick = onOpenSearch, contentDescription = "Search") { tint ->
                ShellNavIcon(destination = ShellDestination.Search, tint = tint)
            }
            TopBarIconButton(onClick = onOpenProfile, contentDescription = "Settings & Profile") { tint ->
                BousygramIcon(icon = BousygramIcons.Settings, contentDescription = null, tint = tint, size = 20.dp)
            }
        }
    }
}

@Composable
fun TopBarIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    icon: @Composable (tint: Color) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val isTv = rememberAdaptiveMetrics().isTv
    val styleSpec = LocalBousygramStyleSpec.current
    val halo = LocalBousygramHaloColors.current
    val interaction = remember { MutableInteractionSource() }
    val tooltipState = rememberBousygramTooltipState()
    val tint = if (focused) colors.primary else colors.onSurfaceVariant
    val shape = RoundedCornerShape(styleSpec.tabCorner)
    val restBorderWidth = if (halo.isHighContrast) 1.dp else styleSpec.cardBorderWidth
    Box(
        modifier = modifier
            // Match the minimum 48dp touch/focus target across phone and TV.
            .size(48.dp)
            .clip(shape)
            .background(
                when {
                    focused -> colors.surfaceContainerHighest
                    styleSpec.useTonalSurfaces -> colors.surfaceContainerHigh
                    styleSpec.style == BousygramDesignStyle.IPHONE_GLASS -> colors.surface.copy(alpha = 0.88f)
                    else -> colors.surfaceContainerLow.copy(alpha = 0.65f)
                }
            )
            .then(
                if (focused || restBorderWidth > 0.dp) {
                    Modifier.border(
                        width = if (focused) BousygramUiTokens.tvFocusOutline else restBorderWidth,
                        color = if (focused) colors.primary else colors.outlineVariant.copy(alpha = 0.50f),
                        shape = shape
                    )
                } else Modifier
            )
            .semantics { this.contentDescription = contentDescription }
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = colors.primary),
                role = Role.Button,
                onClick = {
                    if (!isTv) tooltipState.flashSelection()
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        BousygramTooltipBox(
            tooltip = contentDescription,
            focused = focused,
            placement = BousygramTooltipPlacement.AUTO_BELOW,
            dwellMs = 0L,
            state = tooltipState
        ) {
            icon(tint)
        }
    }
}

@Composable
internal fun GlassBottomBar(
    selected: ShellDestination,
    onSelect: (ShellDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val halo = LocalBousygramHaloColors.current
    val styleSpec = LocalBousygramStyleSpec.current
    val reduceTransparency = LocalBousygramReduceTransparency.current
    val motion = rememberBousygramMotionPolicy()
    val isFloating = styleSpec.floatingBottomBar
    val barShape = when {
        styleSpec.style == BousygramDesignStyle.IPHONE_GLASS -> RoundedCornerShape(BousygramUiTokens.pillCorner)
        styleSpec.style == BousygramDesignStyle.COZY_KAWAII -> RoundedCornerShape(28.dp)
        styleSpec.useTonalSurfaces -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        else -> RoundedCornerShape(topStart = styleSpec.panelCorner, topEnd = styleSpec.panelCorner)
    }
    val glassMaterial = resolveGlassMaterial(
        level = BousygramGlassLevel.FLOATING_CHROME,
        colors = colors,
        halo = halo,
        reduceTransparency = reduceTransparency,
        lowRam = motion.lowRam
    )
    val containerColor = when {
        halo.isHighContrast -> colors.surfaceContainerHigh
        styleSpec.isLiquidGlass -> glassMaterial.fill
        styleSpec.style == BousygramDesignStyle.COZY_KAWAII -> colors.surfaceContainerHigh.copy(alpha = 0.96f)
        else -> colors.surfaceContainer
    }
    val borderColor = when {
        halo.isHighContrast -> colors.outline
        styleSpec.style == BousygramDesignStyle.COZY_KAWAII -> colors.primary.copy(alpha = 0.34f)
        styleSpec.isLiquidGlass -> glassMaterial.rimBottom
        else -> halo.glassBorder
    }
    val borderW = when {
        halo.isHighContrast -> 1.5.dp
        styleSpec.useTonalSurfaces -> 0.dp
        styleSpec.style == BousygramDesignStyle.COZY_KAWAII -> 1.5.dp
        styleSpec.isLiquidGlass -> glassMaterial.borderWidth
        else -> 0.dp
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isFloating) Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                else Modifier
            )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (borderW > 0.dp) {
                        if (styleSpec.isLiquidGlass && !halo.isHighContrast) {
                            Modifier.border(
                                width = borderW,
                                brush = Brush.verticalGradient(listOf(glassMaterial.rimTop, glassMaterial.rimBottom)),
                                shape = barShape
                            )
                        } else {
                            Modifier.border(width = borderW, color = borderColor, shape = barShape)
                        }
                    } else Modifier
                )
                .clip(barShape),
            color = containerColor,
            shape = barShape,
            tonalElevation = if (styleSpec.useTonalSurfaces) 6.dp else 0.dp,
            shadowElevation = if (isFloating && !motion.lowRam && !halo.isHighContrast) 12.dp else 0.dp
        ) {
            Box(
                modifier = Modifier.liquidGlassSheen(
                    enabled = styleSpec.isLiquidGlass && glassMaterial.showSpecularSheen,
                    topColor = glassMaterial.specularTop,
                    coverageFraction = 0.42f
                )
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets(0, 0, 0, 0)
                ) {
                    ShellDestination.navItems.forEach { destination ->
                        val isSelected = when (destination) {
                            ShellDestination.Home -> selected == ShellDestination.Home
                            ShellDestination.Library -> selected == ShellDestination.Library || selected == ShellDestination.Sources
                            ShellDestination.Search -> selected == ShellDestination.Search
                            ShellDestination.Me -> selected == ShellDestination.Me || selected == ShellDestination.Settings
                            else -> destination == selected
                        }
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { onSelect(destination) },
                            icon = {
                                ShellNavIcon(
                                    destination = destination,
                                    tint = if (isSelected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            label = {
                                Text(
                                    destination.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = colors.onPrimaryContainer,
                                selectedTextColor = colors.primary,
                                indicatorColor = colors.primaryContainer,
                                unselectedIconColor = colors.onSurfaceVariant,
                                unselectedTextColor = colors.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BousygramScaffold(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val adaptive = rememberAdaptiveMetrics()
    // Shared native backdrop covers Settings and other shell screens edge-to-edge,
    // while keeping interactive controls safely clear of system bars and cutouts on mobile.
    // Decorative pixels never consume focus or intercept D-pad input.
    PremiumGridBackdrop(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (adaptive.isTv) Modifier else Modifier.safeDrawingPadding())
        ) {
            content()
        }
    }
}

@Composable
fun TvFocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    width: Dp? = null,
    focusRequester: FocusRequester? = null,
    cornerRadius: Dp? = null,
    content: @Composable (focused: Boolean) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val halo = LocalBousygramHaloColors.current
    val styleSpec = LocalBousygramStyleSpec.current
    val reduceTransparency = LocalBousygramReduceTransparency.current
    val shape = RoundedCornerShape(cornerRadius ?: styleSpec.cardCorner)
    val interaction = remember { MutableInteractionSource() }
    // Low-RAM TVs use an immediate focus scale while preserving the prominent ring.
    val motion = rememberBousygramMotionPolicy()
    val glassMaterial = resolveGlassMaterial(
        level = BousygramGlassLevel.CARD,
        colors = colors,
        halo = halo,
        reduceTransparency = reduceTransparency,
        lowRam = motion.lowRam,
        accentTinted = selected
    )
    val scale by animateFloatAsState(
        targetValue = if (motion.animateFocusScale && focused) BousygramUiTokens.focusScale else 1f,
        animationSpec = tween(durationMillis = motion.duration(BousygramUiTokens.focusDurationMs)),
        label = "focusScale"
    )
    val restBorderColor = when {
        halo.isHighContrast -> colors.outlineVariant.copy(alpha = 0.85f)
        styleSpec.style == BousygramDesignStyle.COZY_KAWAII -> colors.primary.copy(alpha = 0.28f)
        styleSpec.isLiquidGlass -> glassMaterial.rimBottom
        else -> colors.outlineVariant.copy(alpha = 0.7f)
    }
    val border by animateColorAsState(
        animationSpec = tween(motion.duration(BousygramUiTokens.focusDurationMs)),
        targetValue = when {
            focused -> colors.primary
            selected -> colors.secondary
            else -> restBorderColor
        },
        label = "focusBorder"
    )
    val borderWidth = when {
        focused -> BousygramUiTokens.tvFocusOutline
        selected -> if (styleSpec.useTonalSurfaces && !halo.isHighContrast) 1.dp else 2.dp
        halo.isHighContrast -> 1.dp
        else -> styleSpec.cardBorderWidth
    }
    val surface = when {
        selected -> colors.primaryContainer.copy(
            alpha = if (styleSpec.isLiquidGlass && !halo.isHighContrast && !reduceTransparency) 0.90f else 1f
        )
        focused -> colors.surfaceContainerHighest
        styleSpec.isLiquidGlass && !halo.isHighContrast -> glassMaterial.fill
        styleSpec.useTonalSurfaces -> colors.surfaceContainerHigh
        else -> colors.surfaceContainerLow
    }
    Surface(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (borderWidth > 0.dp) {
                    if (styleSpec.isLiquidGlass && !focused && !selected && !halo.isHighContrast) {
                        Modifier.border(
                            width = borderWidth,
                            brush = Brush.verticalGradient(listOf(glassMaterial.rimTop, glassMaterial.rimBottom)),
                            shape = shape
                        )
                    } else {
                        Modifier.border(width = borderWidth, color = border, shape = shape)
                    }
                } else Modifier
            )
            .clip(shape)
            // Clickable owns the only focus target and the selected semantics.
            .semantics { this.selected = selected }
            .onFocusChanged { focused = it.isFocused }
            // Attach directly before the clickable focus target, not the parent Surface.
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = colors.primary),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        color = surface,
        shape = shape,
        tonalElevation = if (styleSpec.useTonalSurfaces && !selected) 2.dp else 0.dp,
        shadowElevation = if (styleSpec.isLiquidGlass && !motion.lowRam && !halo.isHighContrast) {
            if (focused) 6.dp else glassMaterial.shadowElevation
        } else 0.dp
    ) {
        Box(
            modifier = Modifier.liquidGlassSheen(
                enabled = styleSpec.isLiquidGlass && glassMaterial.showSpecularSheen && !focused,
                topColor = glassMaterial.specularTop,
                coverageFraction = 0.28f
            )
        ) {
            content(focused)
        }
    }
}

@Composable
fun BousygramButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    leadingIcon: BousygramIconRef? = null,
    tooltip: String? = null,
    tooltipPlacement: BousygramTooltipPlacement = BousygramTooltipPlacement.AUTO_BELOW
) {
    val isTv = rememberAdaptiveMetrics().isTv
    val colors = MaterialTheme.colorScheme
    val styleSpec = LocalBousygramStyleSpec.current
    val contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface
    TvFocusSurface(
        onClick = onClick,
        selected = selected,
        enabled = enabled,
        cornerRadius = styleSpec.buttonCorner,
        modifier = modifier.heightIn(
            min = if (isTv) BousygramUiTokens.tvControlHeight
                else BousygramUiTokens.touchTarget
        )
    ) { focused ->
        // Only pop tooltips for icon-only buttons; labeled controls speak for themselves.
        BousygramTooltipBox(
            tooltip = tooltip,
            icon = leadingIcon,
            focused = focused,
            placement = tooltipPlacement,
            enabled = text.isBlank()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (text.isNotEmpty()) {
                    Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                } else {
                    Arrangement.Center
                },
                modifier = Modifier.padding(
                    horizontal = if (text.isNotEmpty()) 16.dp else 12.dp,
                    vertical = 10.dp
                )
            ) {
                if (leadingIcon != null) {
                    BousygramIcon(
                        icon = leadingIcon,
                        contentDescription = null,
                        tint = if (focused && !selected) colors.primary else contentColor,
                        size = if (isTv) 19.dp else 17.dp
                    )
                }
                if (text.isNotEmpty()) {
                    Text(
                        text = text,
                        color = contentColor,
                        style = bousygramControlLabelStyle(isTv),
                        fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun BousygramChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: BousygramIconRef? = null
) {
    BousygramButton(
        text = text,
        selected = selected,
        onClick = onClick,
        leadingIcon = leadingIcon,
        modifier = modifier
    )
}

@Composable
fun BousygramTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    val styleSpec = LocalBousygramStyleSpec.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(label) },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp),
        shape = RoundedCornerShape(styleSpec.inputCorner),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f),
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

@Composable
fun rememberMiniThumbnail(chatId: Long, messageId: Long, bytes: ByteArray?): android.graphics.Bitmap? {
    val size = bytes?.size ?: 0
    return remember(chatId, messageId, size) {
        if (bytes == null || bytes.isEmpty()) {
            null
        } else {
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }
}

@Composable
fun MediaPosterCard(
    media: MediaItem,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    privacyMode: Boolean = false,
    cardWidth: Dp? = 260.dp,
    progress: Float? = null,
    onFocusChanged: (Boolean) -> Unit = {},
    playbackFocusRequester: FocusRequester? = null,
    showActions: Boolean = true,
    onSaveFavorite: ((MediaItem, (Result<Unit>) -> Unit) -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    // Screens restoring focus after pagination may supply their own requester.
    // The same requester also enables deterministic keyboard/D-pad tests.
    val localPlaybackFocus = remember { FocusRequester() }
    val playbackFocus = playbackFocusRequester ?: localPlaybackFocus
    val actionsFocus = remember { FocusRequester() }
    val adaptive = rememberAdaptiveMetrics()
    val cardDensity = mediaCardDensity(adaptive.isTv, adaptive.widthDp, adaptive.heightDp)
    val condensedCard = cardDensity != MediaCardDensity.STANDARD
    val miniThumbnail = rememberMiniThumbnail(
        media.chatId,
        media.messageId,
        if (privacyMode) null else media.miniThumbnailBytes
    )
    // TDLib thumbnail paths can become stale after native cache cleanup.
    // Treat missing local files as absent rather than permanently showing a blank Coil error.
    val preferredThumbnail = media.thumbnailPath?.takeIf { path ->
        path.startsWith("http://") || path.startsWith("https://") ||
            path.startsWith("content://") || File(path).isFile
    }
    val context = LocalContext.current
    val isTsWithoutPoster = !privacyMode && preferredThumbnail == null &&
        miniThumbnail == null && media.fileId != null &&
        VideoFormatSupport.isTransportStream(media.fileName, media.mimeType)
    val localTsFrame by produceState<android.graphics.Bitmap?>(
        initialValue = null,
        key1 = media.fileId,
        key2 = isTsWithoutPoster,
        key3 = context
    ) {
        val fileId = media.fileId
        val owner = AccountSession.current()
        if (!isTsWithoutPoster || fileId == null || owner == null) return@produceState
        // Subscribe BEFORE the initial probe: the file might complete while we
        // are querying TDLib. Only matching completion events wake this card.
        val completions = Channel<Unit>(Channel.CONFLATED)
        val lastNotifiedPrefixBucket = java.util.concurrent.atomic.AtomicLong(0L)
        val handler: (TdApi.Object?) -> Unit = { update ->
            val file = (update as? TdApi.UpdateFile)?.file
            val local = file?.local
            if (file?.id == fileId && local != null) {
                val bucket = if (local.downloadOffset == 0L) {
                    local.downloadedPrefixSize /
                        VideoFormatSupport.MIN_LOCAL_TS_FRAME_PREFIX_BYTES
                } else 0L
                // Probe again only after another 2 MiB of contiguous bytes;
                // the native metadata retriever must not run on every TDLib tick.
                val previous = lastNotifiedPrefixBucket.getAndUpdate { old ->
                    maxOf(old, bucket)
                }
                if (local.isDownloadingCompleted || (bucket > previous && bucket > 0L)) {
                    completions.trySend(Unit)
                }
            }
        }
        suspend fun extract(): android.graphics.Bitmap? = try {
            ThumbnailExtractor.getCachedTransportStreamThumbnail(context, fileId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            android.util.Log.w("MediaPosterCard", "Local TS artwork unavailable", failure)
            null
        }
        TdLibClient.addUpdateHandler(handler)
        try {
            value = extract()
            if (value == null) {
                // Event-driven instead of downloading full Telegram videos
                // for thumbnails. One initial probe and at most two prefix/completion updates.
                var attempts = 0
                for (ignored in completions) {
                    if (!AccountSession.isCurrent(owner)) break
                    value = extract()
                    if (value != null || ++attempts >= 2) break
                }
            }
        } finally {
            TdLibClient.removeUpdateHandler(handler)
            completions.close()
        }
    }
    val imageData = if (privacyMode) null else preferredThumbnail?.let { path ->
        if (path.startsWith("http")) path else File(path)
    } ?: miniThumbnail ?: localTsFrame
    val imageRequest = remember(imageData) {
        ImageRequest.Builder(context)
            .data(imageData)
            .crossfade(false)
            .size(640, 360)
            .build()
    }
    val labels = mediaDisplayLabels(media, subtitle)
    val durationMs = remember(media.trustedDurationMs, media.durationSeconds) {
        media.trustedDurationMs.takeIf { it > 0L }
            ?: media.durationSeconds.takeIf { it > 0 }?.times(1000L)
            ?: 0L
    }
    val isWatched = remember(media.chatId, media.messageId, durationMs) {
        PlaybackProgressTracker.isWatched(media.chatId, media.messageId, durationMs)
    }
    val resumeFraction = remember(progress, privacyMode, media.chatId, media.messageId, media.fileId, durationMs, isWatched) {
        if (privacyMode || isWatched) {
            null
        } else if (progress != null) {
            progress.coerceIn(0.02f, 0.98f)
        } else if (durationMs > 0L) {
            val position = PlaybackProgressTracker.getMessagePosition(media.chatId, media.messageId, durationMs)
                .takeIf { it > 0 }
                ?: media.fileId?.let { PlaybackProgressTracker.getPosition(it, durationMs) }
                ?: 0
            if (position <= 0) null else (position.toFloat() / durationMs.toFloat()).coerceIn(0.02f, 0.98f)
        } else {
            null
        }
    }
    Box(modifier = modifier.then(if (cardWidth != null) Modifier.width(cardWidth) else Modifier)) {
    TvFocusSurface(
        onClick = onClick,
        focusRequester = playbackFocus,
        modifier = Modifier.fillMaxWidth()
            .testTag("media-play-${media.chatId}:${media.messageId}")
            .focusProperties { if (showActions && AccountSession.current() != null) right = actionsFocus }
            // The actions icon overlaps the poster bounds. Spatial D-pad search can
            // skip an overlapping focus target; explicitly hand off on Right.
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight &&
                    showActions && AccountSession.current() != null) actionsFocus.requestFocus() else false
            }
            .graphicsLayer {
                if (isWatched && !focused) alpha = 0.58f
            }
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged(it.isFocused)
            }
            .semantics {
                val resumeText = if (!privacyMode && !isWatched && resumeFraction != null && resumeFraction in 0.05f..0.95f) {
                    "Resume ${(resumeFraction * 100).toInt()}%"
                } else null
                contentDescription = if (privacyMode) "Private video, details hidden"
                    else listOfNotNull(labels.title, labels.subtitle, labels.fileSizeLabel, resumeText)
                        .filter { it.isNotBlank() }.joinToString(", ")
            }
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                if (imageData != null) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = if (privacyMode) "Private video artwork" else media.title,
                        contentScale = ContentScale.Crop,
                        placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                        error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.58f))
                                )
                            )
                    )
                } else {
                    PosterFallback(privacyMode = privacyMode)
                }
                if (focused) {
                    CenteredPlayBadge()
                }
                // Resting overlay budget (<= 2): at most one corner status/format chip
                // when no progress bar is shown, or bottom-right duration + progress bar.
                if (isWatched) {
                    GlassPill(
                        text = "Watched",
                        icon = BousygramIcons.Check,
                        gold = true,
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                    )
                } else if (resumeFraction == null) {
                    val displayBadge = badge ?: when (media.type) {
                        com.vaydelux.bousygram.domain.model.MediaType.Audio -> media.docType ?: "AUDIO"
                        com.vaydelux.bousygram.domain.model.MediaType.Document -> media.docType ?: "DOC"
                        else -> if (VideoFormatSupport.isTransportStream(media.fileName, media.mimeType)) "TS" else null
                    }
                    displayBadge?.let {
                        val isFavoriteBadge = it.equals("FAVORITE", ignoreCase = true)
                        GlassPill(
                            text = it,
                            icon = if (isFavoriteBadge) BousygramIcons.Favorites else null,
                            accent = !isFavoriteBadge,
                            gold = isFavoriteBadge,
                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                        )
                    }
                }
                if (!privacyMode && labels.durationLabel.isNotBlank()) {
                    GlassPill(
                        text = labels.durationLabel,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                    )
                }
                resumeFraction?.let { fraction ->
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                    )
                }
            }
            Column(
                Modifier.padding(
                    horizontal = if (condensedCard) 10.dp else 12.dp,
                    vertical = if (condensedCard) 6.dp else 10.dp
                )
            ) {
                Text(
                    text = if (privacyMode) "Private video" else labels.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = if (cardDensity == MediaCardDensity.SHORT_TV)
                        MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                    fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = if (cardDensity == MediaCardDensity.SHORT_LANDSCAPE) 1 else 2,
                    minLines = if (cardDensity == MediaCardDensity.SHORT_LANDSCAPE) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )
                // The selected Telegram source is shown in the compact toolbar.
                // Repeating source/size below every file hides a grid row on
                // short TVs and landscape phones. TalkBack still reads details.
                if (!condensedCard) {
                    Spacer(Modifier.height(3.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (privacyMode) "Details hidden" else labels.subtitle,
                            color = if (focused) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (!privacyMode && !labels.fileSizeLabel.isNullOrBlank()) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = labels.fileSizeLabel,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.labelSmall.tabular(),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
    if (showActions) {
        MediaActionsDropdown(
            media = media, privacyMode = privacyMode, onActionFocus = onFocusChanged,
            actionFocusRequester = actionsFocus, playbackFocusRequester = playbackFocus,
            saveHandler = onSaveFavorite,
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
        )
    }
    }

}

@Composable
fun SeriesPosterCard(
    series: SeriesGroup,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    privacyMode: Boolean = false,
    cardWidth: Dp? = 260.dp,
    onFocusChanged: (Boolean) -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }
    val firstEpisode = series.episodes.firstOrNull()
    val miniThumbnail = firstEpisode?.let {
        rememberMiniThumbnail(
            it.chatId,
            it.messageId,
            if (privacyMode) null else it.miniThumbnailBytes
        )
    }
    val preferredThumbnail = firstEpisode?.thumbnailPath
    val imageData = if (privacyMode) null else preferredThumbnail?.let { path ->
        if (path.startsWith("http")) path else File(path)
    } ?: miniThumbnail
    val context = LocalContext.current
    val imageRequest = remember(imageData) {
        ImageRequest.Builder(context)
            .data(imageData)
            .crossfade(false)
            .size(640, 360)
            .build()
    }
    TvFocusSurface(
        onClick = onClick,
        modifier = modifier
            .then(if (cardWidth != null) Modifier.width(cardWidth) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged(it.isFocused)
            }
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                if (imageData != null) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = if (privacyMode) "Private series artwork" else series.title,
                        contentScale = ContentScale.Crop,
                        placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                        error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.58f))
                                )
                            )
                    )
                } else {
                    PosterFallback(privacyMode = privacyMode)
                }
                GlassPill(
                    text = "${series.episodes.size} Episodes",
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    accent = true
                )
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    text = if (privacyMode) "Private series" else series.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${series.totalSeasons} Season(s)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
internal fun GlassPill(
    text: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    gold: Boolean = false,
    icon: BousygramIconRef? = null
) {
    val colors = MaterialTheme.colorScheme
    val halo = LocalBousygramHaloColors.current
    val styleSpec = LocalBousygramStyleSpec.current
    val reduceTransparency = LocalBousygramReduceTransparency.current
    val shape = RoundedCornerShape(if (styleSpec.isLiquidGlass) BousygramUiTokens.pillCorner else 6.dp)
    val pillBg = when {
        gold -> halo.goldContainer.copy(alpha = if (reduceTransparency) 0.98f else 0.94f)
        accent -> colors.primary.copy(alpha = if (reduceTransparency) 0.98f else 0.92f)
        styleSpec.isLiquidGlass && !halo.isHighContrast -> Color(0xFF0A0F1A).copy(alpha = if (reduceTransparency) 0.96f else 0.78f)
        else -> colors.surfaceContainerHighest.copy(alpha = 0.92f)
    }
    val pillFg = when {
        gold -> halo.onGoldContainer
        accent -> colors.onPrimary
        styleSpec.isLiquidGlass && !halo.isHighContrast -> Color(0xFFF5F8FF)
        else -> colors.onSurface
    }
    val pillBorder = when {
        halo.isHighContrast -> colors.outline
        styleSpec.isLiquidGlass -> Color.White.copy(alpha = if (accent || gold) 0.28f else 0.18f)
        else -> Color.Transparent
    }
    Row(
        modifier = modifier
            .background(pillBg, shape)
            .then(
                if (pillBorder != Color.Transparent) Modifier.border(1.dp, pillBorder, shape)
                else Modifier
            )
            .padding(horizontal = if (styleSpec.isLiquidGlass) 9.dp else 7.dp, vertical = 3.5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) {
            BousygramIcon(
                icon = icon,
                contentDescription = null,
                tint = pillFg,
                size = 11.dp
            )
        }
        Text(
            text = text,
            color = pillFg,
            style = MaterialTheme.typography.labelSmall.tabular(),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun PosterFallback(privacyMode: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        BousygramIcon(
            icon = if (privacyMode) BousygramIcons.PrivacyHidden else BousygramIcons.Film,
            contentDescription = null,
            tint = colors.onSurfaceVariant.copy(alpha = 0.72f),
            size = 24.dp
        )
    }
}

@Composable
fun NoResultsIllustration(
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(colors.surfaceContainerHigh)
            .testTag("no_results_illustration"),
        contentAlignment = Alignment.Center
    ) {
        BousygramIcon(
            icon = BousygramIcons.Search,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            size = 22.dp
        )
    }
}

@Composable
fun NoSearchResultsEmptyState(
    query: String,
    modifier: Modifier = Modifier,
    title: String = "No results found",
    scopeLabel: String? = null,
    message: String? = null,
    onClearQuery: (() -> Unit)? = null,
    primaryActionText: String? = "Clear search",
    onPrimaryAction: (() -> Unit)? = onClearQuery,
    secondaryActionText: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    suggestedQueries: List<String> = emptyList(),
    suggestedSearches: List<String> = suggestedQueries,
    onSelectSuggestedQuery: ((String) -> Unit)? = null,
    onSelectSuggestion: ((String) -> Unit)? = onSelectSuggestedQuery
) {
    val colors = MaterialTheme.colorScheme
    val resolvedMessage = message ?: if (query.isNotBlank()) {
        val scopeSuffix = if (!scopeLabel.isNullOrBlank()) " in $scopeLabel" else ""
        "No videos matched \"$query\"$scopeSuffix. Try shorter keywords or switch sources."
    } else {
        "No matching media items found. Try different keywords or another source."
    }
    Box(
        modifier = modifier
            .padding(24.dp)
            .testTag("no_results_empty_state"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            NoResultsIllustration()
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                color = colors.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("no_results_title")
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = resolvedMessage,
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("no_results_message")
            )
            if ((primaryActionText != null && onPrimaryAction != null) ||
                (secondaryActionText != null && onSecondaryAction != null)
            ) {
                Spacer(Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (primaryActionText != null && onPrimaryAction != null) {
                        BousygramButton(
                            text = primaryActionText,
                            onClick = onPrimaryAction,
                            selected = true,
                            modifier = Modifier.testTag("no_results_primary_action")
                        )
                    }
                    if (secondaryActionText != null && onSecondaryAction != null) {
                        BousygramButton(
                            text = secondaryActionText,
                            onClick = onSecondaryAction,
                            selected = false,
                            modifier = Modifier.testTag("no_results_secondary_action")
                        )
                    }
                }
            }
            val filteredSuggestions = remember(suggestedSearches, query) {
                suggestedSearches
                    .filter { it.isNotBlank() && !it.equals(query.trim(), ignoreCase = true) }
                    .distinctBy { it.lowercase() }
                    .take(4)
            }
            if (filteredSuggestions.isNotEmpty() && onSelectSuggestion != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Recent searches",
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(filteredSuggestions, key = { it.lowercase() }) { suggestion ->
                        BousygramChip(
                            text = suggestion,
                            selected = false,
                            onClick = { onSelectSuggestion(suggestion) },
                            modifier = Modifier.testTag("no_results_suggestion_${suggestion.lowercase()}")
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateView(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    illustration: (@Composable () -> Unit)? = null
) {
    val shortWindow = rememberAdaptiveMetrics().isShortWindow
    Box(
        modifier = modifier.padding(if (shortWindow) 6.dp else 16.dp)
            .testTag("bousygram-empty-state"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = if (shortWindow) 12.dp else 20.dp,
                    vertical = if (shortWindow) 8.dp else 16.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (illustration != null) {
                illustration()
                Spacer(Modifier.height(10.dp))
            }
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface,
                style = if (shortWindow) MaterialTheme.typography.titleMedium
                    else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("bousygram-empty-state-title")
            )
            Spacer(Modifier.height(if (shortWindow) 4.dp else 6.dp))
            Text(
                message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                maxLines = if (shortWindow) 3 else 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("bousygram-empty-state-message")
            )
            if (actionText != null && onAction != null) {
                Spacer(Modifier.height(if (shortWindow) 8.dp else 14.dp))
                BousygramButton(
                    actionText, onAction, selected = true,
                    modifier = Modifier.testTag("bousygram-empty-state-action")
                )
            }
        }
    }
}

/** Android's Remove Animations preference sets the global animator scale to zero. */
internal fun shouldAnimateShimmer(animatorScale: Float): Boolean =
    animatorScale.isFinite() && animatorScale > 0f

@Composable
fun rememberShimmerBrush(): Brush {
    val colors = MaterialTheme.colorScheme
    val baseColor = colors.surfaceVariant.copy(alpha = 0.36f)
    val highlightColor = colors.primary.copy(alpha = 0.22f)
    val midColor = colors.surfaceVariant.copy(alpha = 0.68f)
    // The same policy governs tab/focus and shimmer animations. Never run an
    // infinite gradient transition on low-RAM Android TV or with reduced motion.
    val animationsEnabled = rememberBousygramMotionPolicy().animateShimmer
    if (!animationsEnabled) {
        // No looping transition or moving shimmer while system animations are disabled.
        return Brush.linearGradient(
            colors = listOf(baseColor, midColor, highlightColor, midColor, baseColor),
            start = Offset.Zero,
            end = Offset(480f, 150f)
        )
    }
    val transition = rememberInfiniteTransition(label = "shimmerSweep")
    val translateAnim by transition.animateFloat(
        initialValue = -400f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    return Brush.linearGradient(
        colors = listOf(baseColor, midColor, highlightColor, midColor, baseColor),
        start = Offset(translateAnim - 300f, translateAnim - 150f),
        end = Offset(translateAnim + 300f, translateAnim + 150f)
    )
}

@Composable
fun ShimmerCard(
    modifier: Modifier = Modifier,
    rounded: Dp = BousygramUiTokens.cardCorner
) {
    val shimmerBrush = rememberShimmerBrush()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(rounded))
            .background(shimmerBrush)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(rounded)
            )
    )
}

@Composable
fun MediaPosterSkeletonCard(
    modifier: Modifier = Modifier,
    cardWidth: Dp? = null
) {
    val shimmerBrush = rememberShimmerBrush()
    val colors = MaterialTheme.colorScheme
    val styleSpec = LocalBousygramStyleSpec.current
    val shape = RoundedCornerShape(styleSpec.cardCorner)
    Surface(
        modifier = modifier
            .then(if (cardWidth != null) Modifier.width(cardWidth) else Modifier)
            .border(
                width = 1.dp,
                color = if (styleSpec.isLiquidGlass) Color.White.copy(alpha = 0.14f)
                    else colors.outlineVariant.copy(alpha = 0.38f),
                shape = shape
            )
            .clip(shape)
            .testTag("media_poster_skeleton_card"),
        color = colors.surface.copy(alpha = if (styleSpec.isLiquidGlass) 0.72f else 0.78f),
        shape = shape
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(shimmerBrush)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .width(46.dp)
                        .height(18.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.background.copy(alpha = 0.55f))
                )
            }
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(shimmerBrush)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.52f)
                            .height(11.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(shimmerBrush)
                    )
                    Box(
                        modifier = Modifier
                            .width(38.dp)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(shimmerBrush)
                    )
                }
            }
        }
    }
}

@Composable
fun BrowseGridShimmerSkeleton(
    columns: GridCells,
    modifier: Modifier = Modifier,
    itemCount: Int = 9,
    spacing: Dp = 12.dp
) {
    LazyVerticalGrid(
        columns = columns,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
        contentPadding = PaddingValues(bottom = 24.dp),
        userScrollEnabled = false,
        modifier = modifier
            .fillMaxSize()
            .testTag("browse_grid_shimmer_skeleton")
    ) {
        items(itemCount) {
            MediaPosterSkeletonCard(
                cardWidth = null,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun SidebarListShimmerSkeleton(
    modifier: Modifier = Modifier,
    itemCount: Int = 6
) {
    val shimmerBrush = rememberShimmerBrush()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("sidebar_shimmer_skeleton"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(itemCount) { idx ->
            val widthFraction = when (idx % 3) {
                0 -> 0.88f
                1 -> 0.72f
                else -> 0.80f
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(widthFraction)
                        .height(13.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(shimmerBrush)
                )
            }
        }
    }
}

@Composable
fun RecentHistoryIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    BousygramIcon(
        icon = BousygramIcons.History,
        contentDescription = null,
        tint = tint,
        size = 16.dp,
        modifier = modifier
    )
}

@Composable
fun RecentSearchesSection(
    recentSearches: List<RecentSearchQuery>,
    activeQuery: String,
    onSelectQuery: (String) -> Unit,
    onClearActiveQuery: () -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
    onRemoveQuery: ((String) -> Unit)? = null,
    onOpenFullSearch: ((String) -> Unit)? = null
) {
    if (recentSearches.isEmpty() && activeQuery.isBlank()) return
    val colors = MaterialTheme.colorScheme
    val handleClearHistory = {
        onClearHistory()
        if (activeQuery.isNotBlank()) {
            onClearActiveQuery()
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("recent_searches_section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                RecentHistoryIcon(tint = colors.primary)
                Text(
                    text = "Recent Searches",
                    color = colors.onBackground,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
                if (activeQuery.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colors.primary.copy(alpha = 0.20f)
                    ) {
                        Text(
                            text = "Active: \"$activeQuery\"",
                            color = colors.primary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Text(
                        text = "• Tap a query to re-execute search",
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (recentSearches.isNotEmpty()) {
                TvFocusSurface(
                    onClick = handleClearHistory,
                    selected = false,
                    modifier = Modifier.testTag("clear_history_button")
                ) { focused ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        BousygramIcon(
                            icon = BousygramIcons.Trash,
                            contentDescription = null,
                            tint = if (focused) colors.error else colors.onSurfaceVariant,
                            size = 14.dp
                        )
                        Text(
                            text = "Clear History",
                            color = if (focused) colors.error else colors.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("recent_searches_quick_bar")
        ) {
            if (activeQuery.isNotBlank()) {
                item(key = "__clear_active_filter__") {
                    TvFocusSurface(
                        onClick = onClearActiveQuery,
                        selected = true,
                        modifier = Modifier.testTag("clear_quick_search_filter_chip")
                    ) { focused ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            BousygramIcon(
                                icon = BousygramIcons.Close,
                                contentDescription = null,
                                tint = colors.onPrimaryContainer,
                                size = 14.dp
                            )
                            Text(
                                text = "Clear \"$activeQuery\"",
                                color = colors.onPrimaryContainer,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                if (onOpenFullSearch != null) {
                    item(key = "__open_full_search__") {
                        TvFocusSurface(
                            onClick = { onOpenFullSearch(activeQuery) },
                            selected = false,
                            modifier = Modifier.testTag("open_full_search_chip")
                        ) { focused ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = "Search all Telegram for \"$activeQuery\"",
                                    color = if (focused) colors.primary else colors.secondary,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                BousygramIcon(
                                    icon = BousygramIcons.Forward,
                                    contentDescription = null,
                                    tint = if (focused) colors.primary else colors.secondary,
                                    size = 14.dp
                                )
                            }
                        }
                    }
                }
            }
            items(recentSearches, key = { it.normalizedQuery }) { entry ->
                val isSelected = activeQuery.equals(entry.query, ignoreCase = true)
                TvFocusSurface(
                    onClick = { onSelectQuery(entry.query) },
                    selected = isSelected,
                    modifier = Modifier.testTag("recent_search_chip_${entry.normalizedQuery}")
                ) { focused ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        BousygramIcon(
                            icon = if (isSelected) BousygramIcons.Refresh else BousygramIcons.Search,
                            contentDescription = null,
                            tint = if (isSelected) colors.onPrimaryContainer else if (focused) colors.primary else colors.onSurfaceVariant,
                            size = 14.dp
                        )
                        Text(
                            text = entry.query,
                            color = if (isSelected) colors.onPrimaryContainer else colors.onSurface,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected || focused) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (entry.resultCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(BousygramUiTokens.pillCorner),
                                color = if (isSelected) colors.primary.copy(alpha = 0.24f) else colors.surfaceVariant
                            ) {
                                Text(
                                    text = "${entry.resultCount}",
                                    color = if (isSelected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall.tabular(),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (onRemoveQuery != null && !isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable(
                                        role = Role.Button,
                                        onClickLabel = "Remove recent search ${entry.query}"
                                    ) { onRemoveQuery(entry.query) },
                                contentAlignment = Alignment.Center
                            ) {
                                BousygramIcon(
                                    icon = BousygramIcons.Close,
                                    contentDescription = null,
                                    tint = colors.onSurfaceVariant,
                                    size = 15.dp
                                )
                            }
                        }
                    }
                }
            }
            if (recentSearches.isNotEmpty()) {
                item(key = "__clear_recent_searches__") {
                    TvFocusSurface(
                        onClick = handleClearHistory,
                        selected = false,
                        modifier = Modifier.testTag("clear_recent_searches_button")
                    ) { focused ->
                        Text(
                            text = "Clear History",
                            color = if (focused) colors.error else colors.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecentSearchesQuickBar(
    recentSearches: List<RecentSearchQuery>,
    activeQuery: String,
    onSelectQuery: (String) -> Unit,
    onClearActiveQuery: () -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
    onRemoveQuery: ((String) -> Unit)? = null,
    onOpenFullSearch: ((String) -> Unit)? = null
) {
    RecentSearchesSection(
        recentSearches = recentSearches,
        activeQuery = activeQuery,
        onSelectQuery = onSelectQuery,
        onClearActiveQuery = onClearActiveQuery,
        onClearHistory = onClearHistory,
        modifier = modifier,
        onRemoveQuery = onRemoveQuery,
        onOpenFullSearch = onOpenFullSearch
    )
}

@Composable
fun LoadingOverlay(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        EnterprisePanel {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(text, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun EnterprisePanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val halo = LocalBousygramHaloColors.current
    val styleSpec = LocalBousygramStyleSpec.current
    val motion = rememberBousygramMotionPolicy()
    val shape = RoundedCornerShape(styleSpec.panelCorner)
    if (styleSpec.isLiquidGlass) {
        LiquidGlassSurface(
            modifier = modifier,
            level = BousygramGlassLevel.PANEL,
            shape = shape
        ) {
            content()
        }
        return
    }
    val panelColor = when {
        halo.isHighContrast -> halo.glassFill
        styleSpec.useTonalSurfaces -> colors.surfaceContainer
        styleSpec.style == BousygramDesignStyle.COZY_KAWAII -> colors.surfaceContainerLow.copy(alpha = 0.95f)
        else -> colors.surfaceContainerLow
    }
    val borderWidth = if (halo.isHighContrast) 1.5.dp else styleSpec.panelBorderWidth
    val borderColor = when {
        halo.isHighContrast -> halo.glassBorder
        styleSpec.style == BousygramDesignStyle.COZY_KAWAII -> colors.primary.copy(alpha = 0.30f)
        else -> halo.glassBorder
    }
    Surface(
        modifier = modifier.then(
            if (borderWidth > 0.dp) Modifier.border(width = borderWidth, color = borderColor, shape = shape)
            else Modifier
        ),
        color = panelColor,
        shape = shape,
        tonalElevation = styleSpec.panelTonalElevation,
        shadowElevation = if (styleSpec.useSpecularHighlight && !motion.lowRam && !halo.isHighContrast) 4.dp else 0.dp
    ) {
        content()
    }
}

@Composable
fun FeatureRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TvFocusSurface(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(12.dp))
            Text(
                value,
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(max = 220.dp)
            )
        }
    }
}
