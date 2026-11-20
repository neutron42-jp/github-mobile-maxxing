package dev.maxxing.github.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.scheme.DynamicScheme
import dev.maxxing.github.model.MaxxingThemeDefaults
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MaxxingThemePaletteTest {

    private fun generatedScheme(
        seedColorHex: Long,
        dark: Boolean,
        style: PaletteStyle
    ): ColorScheme = dynamicColorScheme(
        seedColor = Color(seedColorHex),
        isDark = dark,
        style = style,
        contrastLevel = 0.0,
        specVersion = ColorSpec.SpecVersion.SPEC_2025,
        platform = DynamicScheme.Platform.PHONE
    )

    private fun assertOpaqueGrey(color: Color, label: String) {
        assertEquals(1f, color.alpha, 0.0001f, "$label must be opaque")
        assertEquals(color.red, color.green, 0.0001f, "$label must have R=G")
        assertEquals(color.red, color.blue, 0.0001f, "$label must have R=B")
    }

    private fun assertColor(expected: Long, actual: Color, label: String) {
        val expectedColor = Color(expected)
        assertTrue(
            abs(expectedColor.red - actual.red) < 0.002f &&
                abs(expectedColor.green - actual.green) < 0.002f &&
                abs(expectedColor.blue - actual.blue) < 0.002f,
            "$label: expected ${expectedColor.value.toString(16)}, got ${actual.value.toString(16)}"
        )
    }

    @Test
    fun presetsKeepTheExistingOrderAndOnlyGraphiteIsMonochrome() {
        assertEquals(
            listOf("Graphite", "Iris", "Fjord", "Teal", "Sage", "Ochre", "Terracotta", "Plum"),
            MaxxingSeedPresets.map { it.name }
        )
        assertEquals(MaxxingThemeDefaults.GRAPHITE_SEED_COLOR_HEX, MaxxingSeedPresets.first().colorHex)
        assertEquals(PaletteStyle.Monochrome, MaxxingSeedPresets.first().style)
        assertEquals(
            listOf(PaletteStyle.Expressive),
            MaxxingSeedPresets.drop(1).map { it.style }.distinct()
        )
    }

    @Test
    fun graphiteIsSelectedOnlyWithItsExplicitFlag() {
        assertEquals(
            PaletteStyle.Monochrome,
            resolveMaxxingPaletteStyle(MaxxingThemeDefaults.GRAPHITE_SEED_COLOR_HEX, monochromePresetSelected = true)
        )
        assertEquals(
            PaletteStyle.Expressive,
            resolveMaxxingPaletteStyle(MaxxingThemeDefaults.GRAPHITE_SEED_COLOR_HEX, monochromePresetSelected = false)
        )
        assertEquals(
            PaletteStyle.Expressive,
            resolveMaxxingPaletteStyle(MaxxingThemeDefaults.IRIS_SEED_COLOR_HEX, monochromePresetSelected = true)
        )
        assertEquals(
            PaletteStyle.Expressive,
            resolveMaxxingPaletteStyle(0xFF123456, monochromePresetSelected = true)
        )
    }

    @Test
    fun newDefaultResolvesToGraphiteAndRendersTheDesignGreys() {
        val style = resolveMaxxingPaletteStyle(
            MaxxingThemeDefaults.DEFAULT_SEED_COLOR_HEX,
            monochromePresetSelected = true
        )
        assertEquals(PaletteStyle.Monochrome, style)

        val light = generatedScheme(MaxxingThemeDefaults.DEFAULT_SEED_COLOR_HEX, dark = false, style = style)
        assertColor(0xFFF9F9F9, light.surface, "Graphite light surface")
        assertColor(0xFFE2E2E2, light.surfaceContainerHighest, "Graphite light surfaceContainerHighest")
        assertColor(0xFF1B1B1B, light.onSurface, "Graphite light onSurface")
        assertColor(0xFF000000, light.primary, "Graphite light primary")
        assertColor(0xFFE2E2E2, light.onPrimary, "Graphite light onPrimary")

        val dark = generatedScheme(MaxxingThemeDefaults.DEFAULT_SEED_COLOR_HEX, dark = true, style = style)
        assertColor(0xFF131313, dark.surface, "Graphite dark surface")
        assertColor(0xFF353535, dark.surfaceContainerHighest, "Graphite dark surfaceContainerHighest")
        assertColor(0xFFE2E2E2, dark.onSurface, "Graphite dark onSurface")
        assertColor(0xFFFFFFFF, dark.primary, "Graphite dark primary")
        assertColor(0xFF1B1B1B, dark.onPrimary, "Graphite dark onPrimary")

        for (scheme in listOf(light, dark)) {
            for ((label, color) in listOf(
                "surface" to scheme.surface,
                "surfaceContainerLow" to scheme.surfaceContainerLow,
                "surfaceContainerHighest" to scheme.surfaceContainerHighest,
                "onSurface" to scheme.onSurface,
                "onSurfaceVariant" to scheme.onSurfaceVariant,
                "primary" to scheme.primary,
                "primaryContainer" to scheme.primaryContainer,
                "secondary" to scheme.secondary,
                "tertiary" to scheme.tertiary
            )) {
                assertOpaqueGrey(color, label)
            }
            // The design keeps an error red: R must dominate G and B in both modes.
            assertTrue(scheme.error.red > scheme.error.green, "Graphite error should stay red")
            assertTrue(scheme.error.red > scheme.error.blue, "Graphite error should stay red")
        }
    }

    @Test
    fun legacyPresetsAndCustomColorsKeepTheExpressivePalette() {
        // The same neutral seed under Expressive is the tinted palette from the design table:
        // this is what a custom #606060 color and every pre-Graphite preset keep.
        val expressiveGraphite = generatedScheme(
            MaxxingThemeDefaults.GRAPHITE_SEED_COLOR_HEX,
            dark = false,
            style = resolveMaxxingPaletteStyle(
                MaxxingThemeDefaults.GRAPHITE_SEED_COLOR_HEX,
                monochromePresetSelected = false
            )
        )
        assertEquals(PaletteStyle.Expressive, resolveMaxxingPaletteStyle(
            MaxxingThemeDefaults.GRAPHITE_SEED_COLOR_HEX,
            monochromePresetSelected = false
        ))
        assertColor(0xFFF1FBFF, expressiveGraphite.surface, "Expressive light surface")
        assertColor(0xFF006975, expressiveGraphite.primary, "Expressive light primary")
        assertNotEquals(expressiveGraphite.surface.red, expressiveGraphite.surface.blue, 0.01f, "Expressive keeps its tint")

        val iris = generatedScheme(
            MaxxingThemeDefaults.IRIS_SEED_COLOR_HEX,
            dark = false,
            style = resolveMaxxingPaletteStyle(MaxxingThemeDefaults.IRIS_SEED_COLOR_HEX, true)
        )
        assertEquals(
            PaletteStyle.Expressive,
            resolveMaxxingPaletteStyle(MaxxingThemeDefaults.IRIS_SEED_COLOR_HEX, true)
        )
        // A pre-Graphite preset stays tinted rather than becoming a neutral Graphite scheme.
        assertTrue(
            abs(iris.surface.red - iris.surface.blue) > 0.001f ||
                abs(iris.surface.green - iris.surface.blue) > 0.001f,
            "Iris Expressive must keep its tint"
        )
    }
}
