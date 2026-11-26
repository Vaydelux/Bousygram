package com.vaydelux.bousygram.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Selectable UI design languages controlling component geometry, surface
 * treatment, border weights, navigation chrome, and cinema player styling.
 */
enum class BousygramDesignStyle(
    val id: String,
    val displayName: String,
    val shortTagline: String,
    val description: String
) {
    CINEMA_HALO(
        id = "cinema_halo",
        displayName = "Cinema Halo",
        shortTagline = "Glass · Halo",
        description = "Signature Bousygram cyan-halo glass with crisp cinema framing"
    ),
    MATERIAL_EXPRESSIVE(
        id = "material_expressive",
        displayName = "Material You",
        shortTagline = "M3 · Tonal Pills",
        description = "Google Material 3 Expressive with tonal surfaces and stadium pills"
    ),
    IPHONE_GLASS(
        id = "iphone_glass",
        displayName = "iOS 27 Glass",
        shortTagline = "Liquid Glass · Island",
        description = "iOS 27/28 Liquid Glass with specular frost rims, floating docks, and Dynamic Island"
    ),
    COZY_KAWAII(
        id = "cozy_kawaii",
        displayName = "Cutesy Soft",
        shortTagline = "Bubbly · Pastel",
        description = "Playful pillowy bubbles, plump rounded cards and soft pastel outlines"
    );

    companion object {
        fun fromId(id: String?): BousygramDesignStyle =
            entries.firstOrNull { it.id == id } ?: CINEMA_HALO
    }
}

/**
 * Selectable native Compose vector/Canvas backdrop patterns. All patterns are
 * cached in drawWithCache (zero bitmaps, zero timers, low-RAM TV safe) and
 * automatically suppressed in High Contrast mode.
 */
enum class BousygramBackdropPattern(
    val id: String,
    val displayName: String,
    val shortTagline: String
) {
    AMBIENT_HALO("ambient_halo", "Cinema Halo", "Ambient glow"),
    DOT_MATRIX("dot_matrix", "Polka Dots", "Dotted pattern"),
    GEOMETRIC_GRID("geometric_grid", "Cinema Grid", "Architectural grid"),
    TOPO_WAVES("topo_waves", "Silk Waves", "Ribbon contours"),
    IOS_MESH("ios_mesh", "iOS Aurora", "Liquid aurora"),
    CUTESY_KAWAII("cutesy_kawaii", "Cutesy Stars", "Hearts & sparkles"),
    CUTESY_PAWS("cutesy_paws", "Chow Paws", "Cute paw prints");

    companion object {
        fun fromId(id: String?): BousygramBackdropPattern =
            entries.firstOrNull { it.id == id } ?: AMBIENT_HALO
    }
}

/** Resolved geometry and surface contract for the active [BousygramDesignStyle]. */
@Immutable
data class BousygramStyleSpec(
    val style: BousygramDesignStyle,
    val cardCorner: Dp,
    val panelCorner: Dp,
    val tabCorner: Dp,
    val buttonCorner: Dp,
    val inputCorner: Dp,
    val tooltipCorner: Dp,
    val panelBorderWidth: Dp,
    val cardBorderWidth: Dp,
    val panelTonalElevation: Dp,
    val floatingBottomBar: Boolean,
    val segmentedTabs: Boolean,
    val useTonalSurfaces: Boolean,
    val useSpecularHighlight: Boolean,
    val playerDockFloatingCapsule: Boolean,
    val isLiquidGlass: Boolean = false
)

internal fun styleSpecFor(style: BousygramDesignStyle): BousygramStyleSpec = when (style) {
    BousygramDesignStyle.CINEMA_HALO -> BousygramStyleSpec(
        style = style,
        cardCorner = BousygramUiTokens.cardCorner,
        panelCorner = BousygramUiTokens.panelCorner,
        tabCorner = BousygramUiTokens.tabCorner,
        buttonCorner = BousygramUiTokens.cardCorner,
        inputCorner = BousygramUiTokens.cardCorner,
        tooltipCorner = BousygramUiTokens.tooltipCorner,
        panelBorderWidth = 0.dp,
        cardBorderWidth = 0.dp,
        panelTonalElevation = 1.dp,
        floatingBottomBar = false,
        segmentedTabs = false,
        useTonalSurfaces = false,
        useSpecularHighlight = false,
        playerDockFloatingCapsule = false,
        isLiquidGlass = false
    )
    BousygramDesignStyle.MATERIAL_EXPRESSIVE -> BousygramStyleSpec(
        style = style,
        cardCorner = 18.dp,
        panelCorner = 24.dp,
        tabCorner = BousygramUiTokens.pillCorner,
        buttonCorner = BousygramUiTokens.pillCorner,
        inputCorner = 16.dp,
        tooltipCorner = 14.dp,
        panelBorderWidth = 0.dp,
        cardBorderWidth = 0.dp,
        panelTonalElevation = 4.dp,
        floatingBottomBar = false,
        segmentedTabs = false,
        useTonalSurfaces = true,
        useSpecularHighlight = false,
        playerDockFloatingCapsule = true,
        isLiquidGlass = false
    )
    BousygramDesignStyle.IPHONE_GLASS -> BousygramStyleSpec(
        style = style,
        cardCorner = 18.dp,
        panelCorner = 24.dp,
        tabCorner = 14.dp,
        buttonCorner = 16.dp,
        inputCorner = 16.dp,
        tooltipCorner = 14.dp,
        panelBorderWidth = 1.dp,
        cardBorderWidth = 1.dp,
        panelTonalElevation = 0.dp,
        floatingBottomBar = true,
        segmentedTabs = true,
        useTonalSurfaces = false,
        useSpecularHighlight = true,
        playerDockFloatingCapsule = true,
        isLiquidGlass = true
    )
    BousygramDesignStyle.COZY_KAWAII -> BousygramStyleSpec(
        style = style,
        cardCorner = 22.dp,
        panelCorner = 28.dp,
        tabCorner = BousygramUiTokens.pillCorner,
        buttonCorner = BousygramUiTokens.pillCorner,
        inputCorner = 22.dp,
        tooltipCorner = 16.dp,
        panelBorderWidth = 1.5.dp,
        cardBorderWidth = 1.5.dp,
        panelTonalElevation = 3.dp,
        floatingBottomBar = true,
        segmentedTabs = false,
        useTonalSurfaces = false,
        useSpecularHighlight = true,
        playerDockFloatingCapsule = true,
        isLiquidGlass = false
    )
}

internal fun shapesFor(spec: BousygramStyleSpec): Shapes = Shapes(
    extraSmall = RoundedCornerShape(spec.tooltipCorner),
    small = RoundedCornerShape(spec.tabCorner.coerceAtMost(20.dp)),
    medium = RoundedCornerShape(spec.cardCorner),
    large = RoundedCornerShape(spec.panelCorner),
    extraLarge = RoundedCornerShape((spec.panelCorner + 4.dp).coerceAtMost(32.dp))
)

/** One-tap curated aesthetic bundles pairing Theme + Design Style + Backdrop Pattern. */
@Immutable
data class BousygramCuratedLook(
    val id: String,
    val title: String,
    val subtitle: String,
    val preset: BousygramThemePreset,
    val style: BousygramDesignStyle,
    val pattern: BousygramBackdropPattern
)

val BousygramCuratedLooks: List<BousygramCuratedLook> = listOf(
    BousygramCuratedLook(
        id = "iphone_midnight",
        title = "iOS 27 Obsidian",
        subtitle = "Liquid Glass · Cyan",
        preset = BousygramThemePreset.MIDNIGHT_GLASS,
        style = BousygramDesignStyle.IPHONE_GLASS,
        pattern = BousygramBackdropPattern.IOS_MESH
    ),
    BousygramCuratedLook(
        id = "iphone_cupertino",
        title = "iOS 27 Frost",
        subtitle = "Light Glass · Cobalt",
        preset = BousygramThemePreset.CUPERTINO,
        style = BousygramDesignStyle.IPHONE_GLASS,
        pattern = BousygramBackdropPattern.IOS_MESH
    ),
    BousygramCuratedLook(
        id = "ios_emerald",
        title = "iOS Emerald",
        subtitle = "Liquid Glass · Mint",
        preset = BousygramThemePreset.EVERGREEN,
        style = BousygramDesignStyle.IPHONE_GLASS,
        pattern = BousygramBackdropPattern.IOS_MESH
    ),
    BousygramCuratedLook(
        id = "ios_ember",
        title = "iOS Ember",
        subtitle = "Liquid Glass · Warm",
        preset = BousygramThemePreset.EMBER,
        style = BousygramDesignStyle.IPHONE_GLASS,
        pattern = BousygramBackdropPattern.IOS_MESH
    ),
    BousygramCuratedLook(
        id = "cinema_halo",
        title = "Cinema Halo",
        subtitle = "Midnight · Halo",
        preset = BousygramThemePreset.DARK,
        style = BousygramDesignStyle.CINEMA_HALO,
        pattern = BousygramBackdropPattern.AMBIENT_HALO
    ),
    BousygramCuratedLook(
        id = "material_you",
        title = "Material You",
        subtitle = "Tonal · Polka Dots",
        preset = BousygramThemePreset.MATERIAL_YOU,
        style = BousygramDesignStyle.MATERIAL_EXPRESSIVE,
        pattern = BousygramBackdropPattern.DOT_MATRIX
    ),
    BousygramCuratedLook(
        id = "cutesy_sakura",
        title = "Cutesy Sakura",
        subtitle = "Pastel · Stars",
        preset = BousygramThemePreset.SAKURA,
        style = BousygramDesignStyle.COZY_KAWAII,
        pattern = BousygramBackdropPattern.CUTESY_KAWAII
    ),
    BousygramCuratedLook(
        id = "chow_paws",
        title = "Chow Paws",
        subtitle = "Dreamy · Paws",
        preset = BousygramThemePreset.COTTON_CANDY,
        style = BousygramDesignStyle.COZY_KAWAII,
        pattern = BousygramBackdropPattern.CUTESY_PAWS
    )
)

val LocalBousygramStyleSpec = staticCompositionLocalOf {
    styleSpecFor(BousygramDesignStyle.IPHONE_GLASS)
}

val LocalBousygramBackdropPattern = staticCompositionLocalOf {
    BousygramBackdropPattern.IOS_MESH
}

val LocalBousygramReduceTransparency = staticCompositionLocalOf { false }
