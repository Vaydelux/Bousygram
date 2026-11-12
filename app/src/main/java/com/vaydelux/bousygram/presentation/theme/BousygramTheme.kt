package com.vaydelux.bousygram.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.ExperimentalTextApi
import com.vaydelux.bousygram.R
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
    );

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
        letterSpacing = (-1.6).sp
    ),
    displayMedium = TextStyle(
        fontFamily = HeadingFont,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        lineHeight = 52.sp,
        letterSpacing = (-1.2).sp
    ),
    displaySmall = TextStyle(
        fontFamily = HeadingFont,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.7).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = HeadingFont,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = HeadingFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.2).sp
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

private val BousygramShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun BousygramTheme(
    preset: BousygramThemePreset = BousygramThemePreset.MIDNIGHT,
    content: @Composable () -> Unit
) {
    val scheme = remember(preset) { schemeFor(preset) }
    MaterialTheme(
        colorScheme = scheme,
        typography = BousygramTypography,
        shapes = BousygramShapes,
        content = content
    )
}
