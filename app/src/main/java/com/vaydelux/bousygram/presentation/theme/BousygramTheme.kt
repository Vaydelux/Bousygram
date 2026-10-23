package com.vaydelux.bousygram.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Dark-first visual presets designed for OLED displays and TV viewing distances. */
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
    MIDNIGHT(
        "midnight", "Midnight", "Deep navy with vibrant mint and cyan highlights",
        Color(0xFF5EE5BE), Color(0xFF4AC4E2), Color(0xFFFFD98A),
        Color(0xFF0A0F1D), Color(0xFF111927), Color(0xFF16243A), Color(0xFF223249)
    ),
    AURORA(
        "aurora", "Aurora", "Emerald and aqua on deep green",
        Color(0xFF3EE8B8), Color(0xFFB6F7B0), Color(0xFFE8F7A0),
        Color(0xFF04110E), Color(0xFF0C1C18), Color(0xFF163028), Color(0xFF2C564A)
    ),
    OCEAN(
        "ocean", "Ocean", "Electric blue with calm cyan accents",
        Color(0xFF4EC4FF), Color(0xFF4AE8DA), Color(0xFFB4E4FF),
        Color(0xFF040C16), Color(0xFF0C192A), Color(0xFF162C44), Color(0xFF2E5274)
    ),
    SUNSET(
        "sunset", "Sunset", "Warm coral and amber over plum",
        Color(0xFFFF9A74), Color(0xFFFFC56E), Color(0xFFFFC0D2),
        Color(0xFF100812), Color(0xFF1E121C), Color(0xFF342032), Color(0xFF5C3C54)
    ),
    AMOLED(
        "amoled", "AMOLED Black", "True pure black with crimson highlights",
        Color(0xFFFF3366), Color(0xFFFF7597), Color(0xFFFFD166),
        Color(0xFF000000), Color(0xFF0A0A0A), Color(0xFF141414), Color(0xFF2A2A2A)
    ),
    CYBERPUNK(
        "cyberpunk", "Cyberpunk", "Neon magenta and electric cyan on obsidian",
        Color(0xFFFF007F), Color(0xFF00F0FF), Color(0xFFFFE600),
        Color(0xFF08060E), Color(0xFF130E1F), Color(0xFF201633), Color(0xFF4A326F)
    );

    companion object {
        fun fromId(id: String?): BousygramThemePreset =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: MIDNIGHT
    }
}

private fun schemeFor(preset: BousygramThemePreset): ColorScheme = darkColorScheme(
    primary = preset.primary,
    onPrimary = Color(0xFF071014),
    primaryContainer = preset.primary.copy(alpha = 0.22f),
    onPrimaryContainer = Color(0xFFF8FBFF),
    secondary = preset.secondary,
    onSecondary = Color(0xFF06120F),
    secondaryContainer = preset.secondary.copy(alpha = 0.18f),
    onSecondaryContainer = Color(0xFFF3FFFB),
    tertiary = preset.tertiary,
    onTertiary = Color(0xFF17110A),
    tertiaryContainer = preset.tertiary.copy(alpha = 0.18f),
    onTertiaryContainer = Color(0xFFFFF8EF),
    background = preset.background,
    onBackground = Color(0xFFF4F7FC),
    surface = preset.surface,
    onSurface = Color(0xFFF4F7FC),
    surfaceVariant = preset.surfaceVariant,
    onSurfaceVariant = Color(0xFFB4C0D2),
    surfaceContainerLowest = preset.background,
    surfaceContainerLow = preset.surface.copy(alpha = 0.72f),
    surfaceContainer = preset.surface,
    surfaceContainerHigh = preset.surfaceVariant.copy(alpha = 0.88f),
    surfaceContainerHighest = preset.surfaceVariant,
    outline = preset.outline,
    outlineVariant = preset.outline.copy(alpha = 0.46f),
    error = Color(0xFFFF6B80),
    onError = Color(0xFF2B0000),
    errorContainer = Color(0xFF5C1824),
    onErrorContainer = Color(0xFFFFD9DF)
)

private val BousygramTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 56.sp,
        lineHeight = 64.sp,
        letterSpacing = (-1.6).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        lineHeight = 52.sp,
        letterSpacing = (-1.2).sp
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.7).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.2).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    )
)

private val BousygramShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
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
