package dev.maxxing.github.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import dev.maxxing.github.model.AppLanguage
import dev.maxxing.github.ui.i18n.LocalAppStrings
import dev.maxxing.github.ui.i18n.stringsFor

data class SeedPreset(
    val name: String,
    val colorHex: Long
)

val MaxxingSeedPresets = listOf(
    SeedPreset("Iris", 0xFF6750A4),
    SeedPreset("Fjord", 0xFF3B6E8C),
    SeedPreset("Teal", 0xFF2F7D7A),
    SeedPreset("Sage", 0xFF4A7C59),
    SeedPreset("Ochre", 0xFFA8763E),
    SeedPreset("Terracotta", 0xFFB85D43),
    SeedPreset("Plum", 0xFF9C4B6E)
)

@Composable
expect fun platformDynamicColorScheme(darkTheme: Boolean): ColorScheme?

val MaxxingShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

val MaxxingTypography = Typography(
    displaySmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

/**
 * Returns [MaxxingTypography] with [fontFamilyOverride] applied to every style, including the
 * Material 3 Expressive `*Emphasized` styles. `labelSmall` and `labelSmallEmphasized` are left
 * untouched so code/hash badges keep their monospace styling. With no override the original
 * [MaxxingTypography] instance is returned unchanged.
 */
fun buildMaxxingTypography(fontFamilyOverride: FontFamily? = null): Typography {
    if (fontFamilyOverride == null) return MaxxingTypography
    val base = MaxxingTypography
    fun TextStyle.withOverride(): TextStyle = copy(fontFamily = fontFamilyOverride)
    return base.copy(
        displayLarge = base.displayLarge.withOverride(),
        displayMedium = base.displayMedium.withOverride(),
        displaySmall = base.displaySmall.withOverride(),
        headlineLarge = base.headlineLarge.withOverride(),
        headlineMedium = base.headlineMedium.withOverride(),
        headlineSmall = base.headlineSmall.withOverride(),
        titleLarge = base.titleLarge.withOverride(),
        titleMedium = base.titleMedium.withOverride(),
        titleSmall = base.titleSmall.withOverride(),
        bodyLarge = base.bodyLarge.withOverride(),
        bodyMedium = base.bodyMedium.withOverride(),
        bodySmall = base.bodySmall.withOverride(),
        labelLarge = base.labelLarge.withOverride(),
        labelMedium = base.labelMedium.withOverride(),
        labelSmall = base.labelSmall, // Keep monospace styling for code/hash badges
        displayLargeEmphasized = base.displayLargeEmphasized.withOverride(),
        displayMediumEmphasized = base.displayMediumEmphasized.withOverride(),
        displaySmallEmphasized = base.displaySmallEmphasized.withOverride(),
        headlineLargeEmphasized = base.headlineLargeEmphasized.withOverride(),
        headlineMediumEmphasized = base.headlineMediumEmphasized.withOverride(),
        headlineSmallEmphasized = base.headlineSmallEmphasized.withOverride(),
        titleLargeEmphasized = base.titleLargeEmphasized.withOverride(),
        titleMediumEmphasized = base.titleMediumEmphasized.withOverride(),
        titleSmallEmphasized = base.titleSmallEmphasized.withOverride(),
        bodyLargeEmphasized = base.bodyLargeEmphasized.withOverride(),
        bodyMediumEmphasized = base.bodyMediumEmphasized.withOverride(),
        bodySmallEmphasized = base.bodySmallEmphasized.withOverride(),
        labelLargeEmphasized = base.labelLargeEmphasized.withOverride(),
        labelMediumEmphasized = base.labelMediumEmphasized.withOverride(),
        labelSmallEmphasized = base.labelSmallEmphasized // Same policy as labelSmall
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MaxxingTheme(
    seedColorHex: Long = MaxxingSeedPresets.first().colorHex,
    useDynamicColor: Boolean = true,
    darkThemeOverride: Boolean? = null,
    fontFamilyOverride: FontFamily? = null,
    language: AppLanguage = AppLanguage.DEFAULT,
    content: @Composable () -> Unit
) {
    val darkTheme = darkThemeOverride ?: isSystemInDarkTheme()
    val dynamicScheme = if (useDynamicColor) platformDynamicColorScheme(darkTheme) else null

    val colorScheme = dynamicScheme ?: rememberDynamicColorScheme(
        seedColor = Color(seedColorHex),
        isDark = darkTheme,
        style = PaletteStyle.Expressive,
        specVersion = ColorSpec.SpecVersion.SPEC_2025
    )
    val typography = remember(fontFamilyOverride) {
        buildMaxxingTypography(fontFamilyOverride)
    }
    val strings = remember(language) {
        stringsFor(language)
    }

    CompositionLocalProvider(LocalAppStrings provides strings) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = MaxxingShapes,
            typography = typography,
            content = content
        )
    }
}
