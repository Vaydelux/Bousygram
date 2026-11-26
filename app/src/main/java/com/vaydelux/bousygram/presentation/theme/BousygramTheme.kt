package com.vaydelux.bousygram.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.ExperimentalTextApi
import com.vaydelux.bousygram.R
import com.vaydelux.bousygram.presentation.adaptive.AdaptiveMetrics
import com.vaydelux.bousygram.presentation.adaptive.BousygramDisplayTier
import com.vaydelux.bousygram.presentation.adaptive.DeviceProfile
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import com.vaydelux.bousygram.presentation.adaptive.resolveDisplaySpec
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Unified dark-first visual theme designed for OLED displays and TV viewing distances. */
enum class BousygramThemePreset(
    val id: String,
    val displayName: String,
    val description: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val outline: Color
) {
    DARK(
        "dark", "Midnight", "Deep navy, luminous glacier-blue and subtle amber",
        Color(0xFF8AE8FA), Color(0xFFABC4FF), Color(0xFFFFD7A1),
        Color(0xFF070D18), Color(0xFF121C2B), Color(0xFF223249), Color(0xFF8399B1)
    ),
    AURORA(
        "aurora", "Aurora", "Electric violet over deep indigo with a cool teal accent",
        Color(0xFFB3A2FF), Color(0xFF82F2E4), Color(0xFFFFD9AC),
        Color(0xFF0B0A1A), Color(0xFF18152D), Color(0xFF30284F), Color(0xFF988BC6)
    ),
    OBSIDIAN(
        "obsidian", "Obsidian", "Quiet graphite, silver and glacier-blue accents",
        Color(0xFFDDE3EF), Color(0xFF8DE8FF), Color(0xFFF1C997),
        Color(0xFF090C12), Color(0xFF151B24), Color(0xFF283140), Color(0xFF9FAAB9)
    ),
    EVERGREEN(
        "evergreen", "Evergreen", "Deep forest green, mint and moonlight",
        Color(0xFF91F2C6), Color(0xFFA6D8FF), Color(0xFFF0D28E),
        Color(0xFF061712), Color(0xFF0D241C), Color(0xFF23483A), Color(0xFF95AD9F)
    ),
    EMBER(
        "ember", "Ember", "Warm peach and rose over rich burgundy",
        Color(0xFFFFB695), Color(0xFFFFD6A3), Color(0xFFFF95BF),
        Color(0xFF190D13), Color(0xFF27171C), Color(0xFF533039), Color(0xFFC1A0A8)
    ),
    HIGH_CONTRAST(
        "high_contrast", "High contrast", "Bright focus indicators and enhanced text contrast",
        Color(0xFFFFE66A), Color(0xFF7CE7FF), Color(0xFFFFB5EB),
        Color(0xFF060708), Color(0xFF0F1215), Color(0xFF1E2429), Color(0xFFE1E7EF)
    ),
    LIGHT(
        "light", "Daylight", "Bright ice-blue surfaces with deep accessible ink",
        Color(0xFF075B83), Color(0xFF19548B), Color(0xFF805016),
        Color(0xFFF5FAFF), Color(0xFFFFFFFF), Color(0xFFE4F0F8), Color(0xFF70849A)
    ),
    OCEAN(
        "ocean", "Deep Ocean", "Marine blue with pale cyan and periwinkle highlights",
        Color(0xFF85DCF3), Color(0xFFA9B6FF), Color(0xFFFFD59E),
        Color(0xFF041923), Color(0xFF0B2734), Color(0xFF1C4151), Color(0xFF88A7B4)
    ),
    PLUM(
        "plum", "Plum", "Mulberry surfaces with soft lavender and rose accents",
        Color(0xFFF2ABD8), Color(0xFFC9B2FF), Color(0xFFB4E2DB),
        Color(0xFF180F1D), Color(0xFF281830), Color(0xFF4C3453), Color(0xFFBDA6BD)
    ),
    IVORY(
        "ivory", "Ivory", "Warm light surfaces with earthy ink and muted accents",
        Color(0xFF79523F), Color(0xFF665181), Color(0xFF735D34),
        Color(0xFFFBF6EE), Color(0xFFFFFDF8), Color(0xFFEEE2D4), Color(0xFF85786B)
    ),
    SAKURA(
        "sakura", "Sakura Blossom", "Cutesy pastel cherry-blossom pink with warm rose ink",
        Color(0xFFA62359), Color(0xFF6A3A82), Color(0xFF8A4E10),
        Color(0xFFFFF6F9), Color(0xFFFFFFFF), Color(0xFFFBE8F0), Color(0xFFC593A8)
    ),
    MATCHA(
        "matcha", "Matcha Cream", "Cozy pastel sage & creamy oat milk palette",
        Color(0xFF2B6640), Color(0xFF874150), Color(0xFF7D5211),
        Color(0xFFF5FAF1), Color(0xFFFCFDF9), Color(0xFFE6F0E0), Color(0xFF93A896)
    ),
    COTTON_CANDY(
        "cotton_candy", "Dreamy Pastel", "Kawaii lavender-sky twilight with candy pink highlights",
        Color(0xFFFFA8DC), Color(0xFF8CE4FF), Color(0xFFFFE09E),
        Color(0xFF131024), Color(0xFF1D1834), Color(0xFF342B58), Color(0xFFA799D1)
    ),
    MATERIAL_YOU(
        "material_you", "Material Tonal", "Expressive Material 3 indigo-sage dynamic tonal palette",
        Color(0xFF2D53A8), Color(0xFF216558), Color(0xFF8A4A1C),
        Color(0xFFF6F8FE), Color(0xFFFFFFFF), Color(0xFFE6ECF8), Color(0xFF8C98B0)
    ),
    CUPERTINO(
        "cupertino", "iOS Frost", "Airy iOS 27 frosted mist with crisp cobalt blue accents",
        Color(0xFF005EC4), Color(0xFF4C3FB5), Color(0xFF8C5100),
        Color(0xFFF3F6FC), Color(0xFFFFFFFF), Color(0xFFE5ECF6), Color(0xFF8E9BB0)
    ),
    MIDNIGHT_GLASS(
        "midnight_glass", "iOS Obsidian", "Deep obsidian spatial glass with luminous Halo Cyan & mint",
        Color(0xFF5CE1FA), Color(0xFF6CE8B8), Color(0xFFFFD27D),
        Color(0xFF060910), Color(0xFF101622), Color(0xFF202A3C), Color(0xFF8896AE)
    );

    val isLightPreset: Boolean
        get() = this == LIGHT || this == IVORY || this == SAKURA ||
            this == MATCHA || this == MATERIAL_YOU || this == CUPERTINO

    companion object {
        val MIDNIGHT = DARK
        fun fromId(id: String?): BousygramThemePreset =
            entries.firstOrNull { it.id == id || (id == "midnight" && it == DARK) } ?: DARK
    }
}

/** Design-system semantic containers; hue variants never change component rules. */
private data class BousygramDarkLayers(
    val primaryContainer: Color,
    val secondaryContainer: Color,
    val tertiaryContainer: Color,
    val surfaceLow: Color,
    val surfaceBase: Color,
    val surfaceHigh: Color,
    val surfaceHighest: Color,
    val outlineVariant: Color
)

private fun darkLayers(preset: BousygramThemePreset): BousygramDarkLayers = when (preset) {
    BousygramThemePreset.AURORA -> BousygramDarkLayers(
        Color(0xFF34275C), Color(0xFF173C3D), Color(0xFF46321F),
        Color(0xFF130F27), Color(0xFF211B39), Color(0xFF2D264B),
        Color(0xFF372D5A), Color(0xFF5B527B)
    )
    BousygramThemePreset.OBSIDIAN -> BousygramDarkLayers(
        Color(0xFF293543), Color(0xFF173A45), Color(0xFF443020),
        Color(0xFF101722), Color(0xFF19222C), Color(0xFF25303B),
        Color(0xFF30404D), Color(0xFF4B5967)
    )
    BousygramThemePreset.EVERGREEN -> BousygramDarkLayers(
        Color(0xFF173C2B), Color(0xFF153643), Color(0xFF433921),
        Color(0xFF0A2018), Color(0xFF0F291F), Color(0xFF193A2B),
        Color(0xFF234A37), Color(0xFF557363)
    )
    BousygramThemePreset.EMBER -> BousygramDarkLayers(
        Color(0xFF5C3029), Color(0xFF563728), Color(0xFF59273E),
        Color(0xFF211218), Color(0xFF30191F), Color(0xFF44262F),
        Color(0xFF53313B), Color(0xFF77525A)
    )
    BousygramThemePreset.OCEAN -> BousygramDarkLayers(
        Color(0xFF123B4D), Color(0xFF293A63), Color(0xFF483722),
        Color(0xFF09202B), Color(0xFF102F3D), Color(0xFF214454),
        Color(0xFF2C5161), Color(0xFF66889A)
    )
    BousygramThemePreset.PLUM -> BousygramDarkLayers(
        Color(0xFF42253F), Color(0xFF3D315B), Color(0xFF253C3B),
        Color(0xFF1F1425), Color(0xFF301C39), Color(0xFF412C47),
        Color(0xFF54365B), Color(0xFF82638B)
    )
    BousygramThemePreset.COTTON_CANDY -> BousygramDarkLayers(
        Color(0xFF4A2546), Color(0xFF1A3D52), Color(0xFF4A381D),
        Color(0xFF18142C), Color(0xFF231D3E), Color(0xFF2E264F),
        Color(0xFF3A3161), Color(0xFF7264A0)
    )
    BousygramThemePreset.MIDNIGHT_GLASS -> BousygramDarkLayers(
        Color(0xFF0E3A4D), Color(0xFF113C30), Color(0xFF473318),
        Color(0xFF0B1019), Color(0xFF121926), Color(0xFF1A2334),
        Color(0xFF243045), Color(0xFF4E5E77)
    )
    else -> BousygramDarkLayers(
        Color(0xFF153C4B), Color(0xFF233A56), Color(0xFF46321F),
        Color(0xFF0D1825), Color(0xFF142131), Color(0xFF1B2C40),
        Color(0xFF23364B), Color(0xFF44596D)
    )
}

internal fun schemeFor(preset: BousygramThemePreset): ColorScheme {
    if (preset == BousygramThemePreset.HIGH_CONTRAST) {
        // Opaque near-black surfaces and bright, semantically paired foregrounds.
        // A dark blue container with yellow text was not consistently readable
        // over thumbnails and cinematic home backdrops.
        return darkColorScheme(
            primary = Color(0xFFFFE66A),
            onPrimary = Color(0xFF090909),
            primaryContainer = Color(0xFF282400),
            onPrimaryContainer = Color(0xFFFFF4AD),
            secondary = Color(0xFFB1F6FF),
            onSecondary = Color(0xFF061417),
            secondaryContainer = Color(0xFF09252A),
            onSecondaryContainer = Color.White,
            tertiary = Color(0xFFFFCBEA),
            onTertiary = Color(0xFF1D0716),
            tertiaryContainer = Color(0xFF36202F),
            onTertiaryContainer = Color.White,
            background = preset.background,
            onBackground = Color.White,
            surface = preset.surface,
            onSurface = Color.White,
            surfaceVariant = preset.surfaceVariant,
            onSurfaceVariant = Color(0xFFF2F4F8),
            surfaceContainerLowest = preset.background,
            surfaceContainerLow = Color(0xFF0B0D0E),
            surfaceContainer = preset.surface,
            surfaceContainerHigh = Color(0xFF181D21),
            surfaceContainerHighest = preset.surfaceVariant,
            outline = preset.outline,
            outlineVariant = Color(0xFF9EA8B2),
            error = Color(0xFFFF8799),
            onError = Color(0xFF2C0309),
            errorContainer = Color(0xFF52121B),
            onErrorContainer = Color.White
        )
    }
    if (preset == BousygramThemePreset.IVORY) {
        // A separate warm light option; Daylight and all previous presets
        // retain their exact original color values and persisted IDs.
        return lightColorScheme(
            primary = preset.primary,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE6D4C3),
            onPrimaryContainer = Color(0xFF3C2920),
            secondary = preset.secondary,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFE5D8F1),
            onSecondaryContainer = Color(0xFF38284E),
            tertiary = preset.tertiary,
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFEEE1C3),
            onTertiaryContainer = Color(0xFF423019),
            background = preset.background,
            onBackground = Color(0xFF30271F),
            surface = preset.surface,
            onSurface = Color(0xFF30271F),
            surfaceVariant = preset.surfaceVariant,
            onSurfaceVariant = Color(0xFF574940),
            surfaceContainerLowest = preset.surface,
            surfaceContainerLow = Color(0xFFFAF4EB),
            surfaceContainer = Color(0xFFF5EFE5),
            surfaceContainerHigh = Color(0xFFEEE2D4),
            surfaceContainerHighest = Color(0xFFE7DAC9),
            outline = preset.outline,
            outlineVariant = Color(0xFFCDBDAD),
            error = Color(0xFFAD2434),
            onError = Color.White,
            errorContainer = Color(0xFFFFDADD),
            onErrorContainer = Color(0xFF65121E)
        )
    }
    if (preset == BousygramThemePreset.SAKURA) {
        return lightColorScheme(
            primary = preset.primary,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFFDCE8),
            onPrimaryContainer = Color(0xFF4E0E2A),
            secondary = preset.secondary,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFF0E0FB),
            onSecondaryContainer = Color(0xFF37184A),
            tertiary = preset.tertiary,
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFFCEBD4),
            onTertiaryContainer = Color(0xFF4A2906),
            background = preset.background,
            onBackground = Color(0xFF381B27),
            surface = preset.surface,
            onSurface = Color(0xFF381B27),
            surfaceVariant = preset.surfaceVariant,
            onSurfaceVariant = Color(0xFF654150),
            surfaceContainerLowest = preset.surface,
            surfaceContainerLow = Color(0xFFFFF9FB),
            surfaceContainer = Color(0xFFFDF0F5),
            surfaceContainerHigh = Color(0xFFF8E3EC),
            surfaceContainerHighest = Color(0xFFF2D6E3),
            outline = preset.outline,
            outlineVariant = Color(0xFFE7C2D2),
            error = Color(0xFFB32037),
            onError = Color.White,
            errorContainer = Color(0xFFFFD9DE),
            onErrorContainer = Color(0xFF630E1D)
        )
    }
    if (preset == BousygramThemePreset.MATCHA) {
        return lightColorScheme(
            primary = preset.primary,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD8EEDB),
            onPrimaryContainer = Color(0xFF13351F),
            secondary = preset.secondary,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFF8DFE4),
            onSecondaryContainer = Color(0xFF471B25),
            tertiary = preset.tertiary,
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFF4E7CE),
            onTertiaryContainer = Color(0xFF402906),
            background = preset.background,
            onBackground = Color(0xFF223025),
            surface = preset.surface,
            onSurface = Color(0xFF223025),
            surfaceVariant = preset.surfaceVariant,
            onSurfaceVariant = Color(0xFF465848),
            surfaceContainerLowest = preset.surface,
            surfaceContainerLow = Color(0xFFF8FCF5),
            surfaceContainer = Color(0xFFEFF6EA),
            surfaceContainerHigh = Color(0xFFE5EEDF),
            surfaceContainerHighest = Color(0xFFDBE6D3),
            outline = preset.outline,
            outlineVariant = Color(0xFFC5D6C2),
            error = Color(0xFFAD2434),
            onError = Color.White,
            errorContainer = Color(0xFFFFDADD),
            onErrorContainer = Color(0xFF65121E)
        )
    }
    if (preset == BousygramThemePreset.MATERIAL_YOU) {
        return lightColorScheme(
            primary = preset.primary,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFDBE5FF),
            onPrimaryContainer = Color(0xFF0E265C),
            secondary = preset.secondary,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFD2F0E8),
            onSecondaryContainer = Color(0xFF0A362E),
            tertiary = preset.tertiary,
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFFBE3D1),
            onTertiaryContainer = Color(0xFF472308),
            background = preset.background,
            onBackground = Color(0xFF192030),
            surface = preset.surface,
            onSurface = Color(0xFF192030),
            surfaceVariant = preset.surfaceVariant,
            onSurfaceVariant = Color(0xFF424E66),
            surfaceContainerLowest = preset.surface,
            surfaceContainerLow = Color(0xFFF9FAFF),
            surfaceContainer = Color(0xFFEEF2FB),
            surfaceContainerHigh = Color(0xFFE4EAF7),
            surfaceContainerHighest = Color(0xFFD9E1F2),
            outline = preset.outline,
            outlineVariant = Color(0xFFC6D0E4),
            error = Color(0xFFB3261E),
            onError = Color.White,
            errorContainer = Color(0xFFF9DEDC),
            onErrorContainer = Color(0xFF410E0B)
        )
    }
    if (preset == BousygramThemePreset.CUPERTINO) {
        return lightColorScheme(
            primary = preset.primary,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD8EBFF),
            onPrimaryContainer = Color(0xFF002B5C),
            secondary = preset.secondary,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFE5E2FF),
            onSecondaryContainer = Color(0xFF231966),
            tertiary = preset.tertiary,
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFFFEBD0),
            onTertiaryContainer = Color(0xFF472700),
            background = preset.background,
            onBackground = Color(0xFF141923),
            surface = preset.surface,
            onSurface = Color(0xFF141923),
            surfaceVariant = preset.surfaceVariant,
            onSurfaceVariant = Color(0xFF434D5E),
            surfaceContainerLowest = preset.surface,
            surfaceContainerLow = Color(0xFFF7F9FD),
            surfaceContainer = Color(0xFFEEF3FA),
            surfaceContainerHigh = Color(0xFFE5ECF6),
            surfaceContainerHighest = Color(0xFFD9E3F0),
            outline = preset.outline,
            outlineVariant = Color(0xFFC2CEDF),
            error = Color(0xFFC41D2E),
            onError = Color.White,
            errorContainer = Color(0xFFFFDADC),
            onErrorContainer = Color(0xFF640D16)
        )
    }
    if (preset == BousygramThemePreset.LIGHT) {
        return lightColorScheme(
            primary = preset.primary,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD9F3FF),
            onPrimaryContainer = Color(0xFF07364B),
            secondary = preset.secondary,
            onSecondary = Color.White,
            tertiary = preset.tertiary,
            onTertiary = Color.White,
            background = preset.background,
            onBackground = Color(0xFF13273D),
            surface = preset.surface,
            onSurface = Color(0xFF13273D),
            surfaceVariant = preset.surfaceVariant,
            onSurfaceVariant = Color(0xFF344F66),
            surfaceContainer = Color(0xFFEBF4FC),
            surfaceContainerHigh = preset.surfaceVariant,
            outline = preset.outline,
            outlineVariant = Color(0xFFB8C9D8),
            error = Color(0xFFB42335),
            onError = Color.White
        )
    }
    val layers = darkLayers(preset)
    return darkColorScheme(
        primary = preset.primary,
        onPrimary = Color(0xFF07151B),
        primaryContainer = layers.primaryContainer,
        onPrimaryContainer = Color(0xFFF1FCFF),
        secondary = preset.secondary,
        onSecondary = Color(0xFF081726),
        secondaryContainer = layers.secondaryContainer,
        onSecondaryContainer = Color(0xFFF3F7FF),
        tertiary = preset.tertiary,
        onTertiary = Color(0xFF251706),
        tertiaryContainer = layers.tertiaryContainer,
        onTertiaryContainer = Color(0xFFFFF3E0),
        background = preset.background,
        onBackground = Color(0xFFF4F7FC),
        surface = preset.surface,
        onSurface = Color(0xFFF4F7FC),
        surfaceVariant = preset.surfaceVariant,
        onSurfaceVariant = Color(0xFFC2CCDA),
        surfaceContainerLowest = preset.background,
        surfaceContainerLow = layers.surfaceLow,
        surfaceContainer = layers.surfaceBase,
        surfaceContainerHigh = layers.surfaceHigh,
        surfaceContainerHighest = layers.surfaceHighest,
        outline = preset.outline,
        outlineVariant = layers.outlineVariant,
        error = Color(0xFFFF6B80),
        onError = Color(0xFF2B0000),
        errorContainer = Color(0xFF5C1824),
        onErrorContainer = Color(0xFFFFD9DF)
    )
}

@OptIn(ExperimentalTextApi::class)
private fun bundledFamily(resource: Int) = FontFamily(
    *listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
        Font(resource, weight = weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))
    }.toTypedArray()
)

private val HeadingFont = bundledFamily(R.font.space_grotesk)
private val ReadingFont = bundledFamily(R.font.inter)

private val BousygramTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = HeadingFont,
        fontWeight = FontWeight.Bold,
        fontSize = 56.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.75).sp
    ),
    displayMedium = TextStyle(
        fontFamily = HeadingFont,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        lineHeight = 52.sp,
        letterSpacing = (-0.5).sp
    ),
    displaySmall = TextStyle(
        fontFamily = HeadingFont,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.35).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = HeadingFont,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = HeadingFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.15).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = HeadingFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontFamily = HeadingFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.25).sp
    ),
    titleMedium = TextStyle(
        fontFamily = HeadingFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.1).sp
    ),
    titleSmall = TextStyle(
        fontFamily = HeadingFont,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelLarge = TextStyle(
        fontFamily = ReadingFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = ReadingFont,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = ReadingFont,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = ReadingFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = ReadingFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp
    ),
    bodySmall = TextStyle(
        fontFamily = ReadingFont,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    )
)

/**
 * Cinema Halo extended visual tokens derived from the active preset while
 * anchoring brand identity in the approved Chow Chow + Cyan-Blue Halo palette.
 */
@Immutable
data class BousygramHaloColors(
    val haloStart: Color,
    val haloEnd: Color,
    val focusRing: Color,
    val goldAccent: Color,
    val goldContainer: Color,
    val onGoldContainer: Color,
    val glassFill: Color,
    val glassBorder: Color,
    val tooltipFill: Color,
    val tooltipContent: Color,
    val tooltipBorder: Color,
    val scrimTop: Color,
    val scrimBottom: Color,
    val isHighContrast: Boolean,
    val isLight: Boolean
)

internal fun haloColorsFor(preset: BousygramThemePreset, scheme: ColorScheme): BousygramHaloColors {
    val highContrast = preset == BousygramThemePreset.HIGH_CONTRAST
    val light = preset.isLightPreset
    val brandCyan = Color(0xFF37D7F2)
    val brandBlue = Color(0xFF2289F9)
    val chowGold = Color(0xFFFFC67D)
    return when {
        highContrast -> BousygramHaloColors(
            haloStart = scheme.primary,
            haloEnd = scheme.secondary,
            focusRing = scheme.primary,
            goldAccent = scheme.primary,
            goldContainer = scheme.primaryContainer,
            onGoldContainer = scheme.onPrimaryContainer,
            glassFill = scheme.surfaceContainerHigh,
            glassBorder = scheme.outline,
            tooltipFill = scheme.surfaceContainerHighest,
            tooltipContent = scheme.onSurface,
            tooltipBorder = scheme.primary,
            scrimTop = Color(0xE6000000),
            scrimBottom = Color(0xF5000000),
            isHighContrast = true,
            isLight = false
        )
        light -> BousygramHaloColors(
            haloStart = scheme.primary,
            haloEnd = scheme.secondary,
            focusRing = scheme.primary,
            goldAccent = scheme.tertiary,
            goldContainer = if (preset == BousygramThemePreset.LIGHT) Color(0xFFFFF0D4) else scheme.tertiaryContainer,
            onGoldContainer = if (preset == BousygramThemePreset.LIGHT) Color(0xFF4A2E07) else scheme.onTertiaryContainer,
            glassFill = scheme.surface,
            glassBorder = scheme.outlineVariant.copy(alpha = 0.65f),
            tooltipFill = Color(0xFF0E2236),
            tooltipContent = Color(0xFFF5FAFF),
            tooltipBorder = brandCyan.copy(alpha = 0.65f),
            scrimTop = Color(0xB8050D18),
            scrimBottom = Color(0xDE050D18),
            isHighContrast = false,
            isLight = true
        )
        preset == BousygramThemePreset.DARK -> BousygramHaloColors(
            haloStart = brandCyan,
            haloEnd = brandBlue,
            focusRing = scheme.primary,
            goldAccent = chowGold,
            goldContainer = Color(0xFF3D2A15),
            onGoldContainer = Color(0xFFFFE4BE),
            glassFill = scheme.surfaceContainer,
            glassBorder = scheme.outlineVariant.copy(alpha = 0.55f),
            tooltipFill = scheme.surfaceContainerHighest.copy(alpha = 0.97f),
            tooltipContent = scheme.onSurface,
            tooltipBorder = scheme.outlineVariant.copy(alpha = 0.85f),
            scrimTop = Color(0xBA040A14),
            scrimBottom = Color(0xE0040A14),
            isHighContrast = false,
            isLight = false
        )
        else -> BousygramHaloColors(
            haloStart = scheme.primary,
            haloEnd = scheme.secondary,
            focusRing = scheme.primary,
            goldAccent = scheme.tertiary,
            goldContainer = scheme.tertiaryContainer,
            onGoldContainer = scheme.onTertiaryContainer,
            glassFill = scheme.surfaceContainer,
            glassBorder = scheme.outlineVariant.copy(alpha = 0.50f),
            tooltipFill = scheme.surfaceContainerHighest.copy(alpha = 0.97f),
            tooltipContent = scheme.onSurface,
            tooltipBorder = scheme.outlineVariant.copy(alpha = 0.85f),
            scrimTop = Color(0xB8050A12),
            scrimBottom = Color(0xDF050A12),
            isHighContrast = false,
            isLight = false
        )
    }
}

/**
 * Responsive Typography scale tuned per device tier while strictly keeping
 * Space Grotesk on display/headline/title and Inter on label/body.
 */
internal fun bousygramTypographyFor(tier: BousygramDisplayTier): Typography = when (tier) {
    BousygramDisplayTier.PhoneCompact -> BousygramTypography.copy(
        headlineLarge = BousygramTypography.headlineLarge.copy(fontSize = 24.sp, lineHeight = 30.sp),
        headlineMedium = BousygramTypography.headlineMedium.copy(fontSize = 20.sp, lineHeight = 26.sp),
        headlineSmall = BousygramTypography.headlineSmall.copy(fontSize = 18.sp, lineHeight = 24.sp),
        titleLarge = BousygramTypography.titleLarge.copy(fontSize = 17.sp, lineHeight = 23.sp),
        titleMedium = BousygramTypography.titleMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
        titleSmall = BousygramTypography.titleSmall.copy(fontSize = 13.5.sp, lineHeight = 19.sp),
        bodyLarge = BousygramTypography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
        bodyMedium = BousygramTypography.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 19.sp),
        labelLarge = BousygramTypography.labelLarge.copy(fontSize = 13.sp, lineHeight = 18.sp),
        labelMedium = BousygramTypography.labelMedium.copy(fontSize = 11.5.sp, lineHeight = 15.sp)
    )
    BousygramDisplayTier.PhoneStandard,
    BousygramDisplayTier.PhoneLandscape -> BousygramTypography.copy(
        headlineLarge = BousygramTypography.headlineLarge.copy(fontSize = 28.sp, lineHeight = 34.sp),
        headlineMedium = BousygramTypography.headlineMedium.copy(fontSize = 24.sp, lineHeight = 30.sp),
        headlineSmall = BousygramTypography.headlineSmall.copy(fontSize = 20.sp, lineHeight = 26.sp),
        titleLarge = BousygramTypography.titleLarge.copy(fontSize = 19.sp, lineHeight = 26.sp),
        titleMedium = BousygramTypography.titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp)
    )
    BousygramDisplayTier.TabletMedium,
    BousygramDisplayTier.TabletExpanded -> BousygramTypography.copy(
        titleSmall = BousygramTypography.titleSmall.copy(fontSize = 14.5.sp, lineHeight = 20.sp),
        bodySmall = BousygramTypography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
        labelMedium = BousygramTypography.labelMedium.copy(fontSize = 12.5.sp, lineHeight = 16.sp),
        labelSmall = BousygramTypography.labelSmall.copy(fontSize = 11.5.sp, lineHeight = 15.sp)
    )
    BousygramDisplayTier.TvCompact,
    BousygramDisplayTier.TvStandard -> BousygramTypography.copy(
        titleMedium = BousygramTypography.titleMedium.copy(fontSize = 17.sp, lineHeight = 24.sp),
        titleSmall = BousygramTypography.titleSmall.copy(fontSize = 15.sp, lineHeight = 21.sp),
        bodyMedium = BousygramTypography.bodyMedium.copy(fontSize = 14.5.sp, lineHeight = 21.sp),
        bodySmall = BousygramTypography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
        labelLarge = BousygramTypography.labelLarge.copy(fontSize = 14.5.sp, lineHeight = 20.sp),
        labelMedium = BousygramTypography.labelMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
        labelSmall = BousygramTypography.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp)
    )
    BousygramDisplayTier.TvLarge -> BousygramTypography.copy(
        headlineLarge = BousygramTypography.headlineLarge.copy(fontSize = 36.sp, lineHeight = 44.sp),
        headlineMedium = BousygramTypography.headlineMedium.copy(fontSize = 30.sp, lineHeight = 38.sp),
        headlineSmall = BousygramTypography.headlineSmall.copy(fontSize = 26.sp, lineHeight = 34.sp),
        titleLarge = BousygramTypography.titleLarge.copy(fontSize = 24.sp, lineHeight = 32.sp),
        titleMedium = BousygramTypography.titleMedium.copy(fontSize = 19.sp, lineHeight = 26.sp),
        titleSmall = BousygramTypography.titleSmall.copy(fontSize = 17.sp, lineHeight = 23.sp),
        bodyLarge = BousygramTypography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
        bodyMedium = BousygramTypography.bodyMedium.copy(fontSize = 16.sp, lineHeight = 23.sp),
        bodySmall = BousygramTypography.bodySmall.copy(fontSize = 14.sp, lineHeight = 20.sp),
        labelLarge = BousygramTypography.labelLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
        labelMedium = BousygramTypography.labelMedium.copy(fontSize = 14.sp, lineHeight = 19.sp),
        labelSmall = BousygramTypography.labelSmall.copy(fontSize = 13.sp, lineHeight = 17.sp)
    )
}

/** Tabular numerals prevent horizontal jitter on player clocks, durations, and counts. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

val LocalBousygramHaloColors = staticCompositionLocalOf {
    haloColorsFor(BousygramThemePreset.DARK, schemeFor(BousygramThemePreset.DARK))
}

val LocalBousygramDisplaySpec = staticCompositionLocalOf {
    resolveDisplaySpec(AdaptiveMetrics(DeviceProfile.PhonePortrait, 393.dp, 852.dp))
}

/**
 * Curated iOS 27/28 Accent Tints displayed in the quick Appearance Popover.
 * Each tint maps cleanly onto WCAG-verified Dark and Light theme presets so
 * contrast, settings backup, and legacy theme contracts remain unified.
 */
enum class BousygramIosAccentTint(
    val id: String,
    val displayName: String,
    val swatchColor: Color,
    val darkPreset: BousygramThemePreset,
    val lightPreset: BousygramThemePreset
) {
    HALO_CYAN(
        id = "halo_cyan",
        displayName = "Halo Cyan",
        swatchColor = Color(0xFF5CE1FA),
        darkPreset = BousygramThemePreset.MIDNIGHT_GLASS,
        lightPreset = BousygramThemePreset.LIGHT
    ),
    COBALT_BLUE(
        id = "cobalt_blue",
        displayName = "Cobalt Blue",
        swatchColor = Color(0xFF3B82F6),
        darkPreset = BousygramThemePreset.DARK,
        lightPreset = BousygramThemePreset.CUPERTINO
    ),
    EMERALD(
        id = "emerald",
        displayName = "Emerald",
        swatchColor = Color(0xFF34D399),
        darkPreset = BousygramThemePreset.EVERGREEN,
        lightPreset = BousygramThemePreset.MATCHA
    ),
    EMBER(
        id = "ember",
        displayName = "Ember",
        swatchColor = Color(0xFFFB923C),
        darkPreset = BousygramThemePreset.EMBER,
        lightPreset = BousygramThemePreset.IVORY
    ),
    AURORA_VIOLET(
        id = "aurora_violet",
        displayName = "Aurora",
        swatchColor = Color(0xFFA78BFA),
        darkPreset = BousygramThemePreset.AURORA,
        lightPreset = BousygramThemePreset.MATERIAL_YOU
    ),
    SILVER(
        id = "silver",
        displayName = "Obsidian",
        swatchColor = Color(0xFFDDE3EF),
        darkPreset = BousygramThemePreset.OBSIDIAN,
        lightPreset = BousygramThemePreset.CUPERTINO
    );

    fun presetFor(lightMode: Boolean): BousygramThemePreset =
        if (lightMode) lightPreset else darkPreset

    companion object {
        fun fromPreset(preset: BousygramThemePreset): BousygramIosAccentTint = when (preset) {
            BousygramThemePreset.MIDNIGHT_GLASS, BousygramThemePreset.LIGHT, BousygramThemePreset.OCEAN -> HALO_CYAN
            BousygramThemePreset.DARK, BousygramThemePreset.CUPERTINO -> COBALT_BLUE
            BousygramThemePreset.EVERGREEN, BousygramThemePreset.MATCHA -> EMERALD
            BousygramThemePreset.EMBER, BousygramThemePreset.IVORY, BousygramThemePreset.SAKURA -> EMBER
            BousygramThemePreset.AURORA, BousygramThemePreset.MATERIAL_YOU,
            BousygramThemePreset.PLUM, BousygramThemePreset.COTTON_CANDY -> AURORA_VIOLET
            BousygramThemePreset.OBSIDIAN, BousygramThemePreset.HIGH_CONTRAST -> SILVER
        }
    }
}

/**
 * Live root-level appearance controller provided via [LocalBousygramAppearanceController]
 * so the Dynamic Island, Appearance Popover, and Settings can toggle Dark/Light/Contrast,
 * iOS Accent Tints, and Reduce Transparency from any screen without threading callbacks.
 */
@Immutable
data class BousygramAppearanceController(
    val themePreset: BousygramThemePreset,
    val designStyle: BousygramDesignStyle,
    val backdropPattern: BousygramBackdropPattern,
    val reduceTransparency: Boolean,
    val onThemePresetChange: (BousygramThemePreset) -> Unit,
    val onDesignStyleChange: (BousygramDesignStyle) -> Unit,
    val onBackdropPatternChange: (BousygramBackdropPattern) -> Unit,
    val onReduceTransparencyChange: (Boolean) -> Unit
)

val LocalBousygramAppearanceController = staticCompositionLocalOf<BousygramAppearanceController?> {
    null
}

@Composable
fun BousygramTheme(
    preset: BousygramThemePreset = BousygramThemePreset.MIDNIGHT,
    designStyle: BousygramDesignStyle = BousygramDesignStyle.IPHONE_GLASS,
    backdropPattern: BousygramBackdropPattern = BousygramBackdropPattern.IOS_MESH,
    reduceTransparency: Boolean = false,
    appearanceController: BousygramAppearanceController? = null,
    content: @Composable () -> Unit
) {
    val scheme = remember(preset) { schemeFor(preset) }
    val halo = remember(preset, scheme) { haloColorsFor(preset, scheme) }
    val styleSpec = remember(designStyle) { styleSpecFor(designStyle) }
    val shapes = remember(styleSpec) { shapesFor(styleSpec) }
    val adaptive = rememberAdaptiveMetrics()
    val displaySpec = remember(adaptive) { resolveDisplaySpec(adaptive) }
    val typography = remember(displaySpec.tier) { bousygramTypographyFor(displaySpec.tier) }
    CompositionLocalProvider(
        LocalBousygramHaloColors provides halo,
        LocalBousygramDisplaySpec provides displaySpec,
        LocalBousygramStyleSpec provides styleSpec,
        LocalBousygramBackdropPattern provides backdropPattern,
        LocalBousygramReduceTransparency provides reduceTransparency,
        LocalBousygramAppearanceController provides appearanceController
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            shapes = shapes,
            content = content
        )
    }
}
