package dev.maxxing.github.app.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FontPickerSelectionColorsTest {

    private fun contrastRatio(a: Color, b: Color): Float {
        val high = max(a.luminance(), b.luminance())
        val low = min(a.luminance(), b.luminance())
        return (high + 0.05f) / (low + 0.05f)
    }

    private val graphiteLight = lightColorScheme(
        primary = Color(0xFF000000),
        onSurface = Color(0xFF1B1B1B),
        primaryContainer = Color(0xFF3B3B3B),
        surfaceContainerHigh = Color(0xFFE8E8E8),
        surfaceContainerHighest = Color(0xFFE2E2E2)
    )

    private val graphiteDark = darkColorScheme(
        primary = Color(0xFFFFFFFF),
        onSurface = Color(0xFFE2E2E2),
        primaryContainer = Color(0xFFD4D4D4),
        surfaceContainerHigh = Color(0xFF2B2B2B),
        surfaceContainerHighest = Color(0xFF353535)
    )

    @Test
    fun graphiteSelectedRowUsesNeutralSurfaceAndPrimaryMarkerInBothModes() {
        for (scheme in listOf(graphiteLight, graphiteDark)) {
            val colors = fontChoiceRowColors(selected = true, graphitePalette = true, scheme = scheme)
            assertEquals(scheme.surfaceContainerHighest, colors.container)
            assertEquals(scheme.onSurface, colors.content)
            assertEquals(scheme.primary, colors.border)
            assertTrue(
                contrastRatio(colors.content, colors.container) >= 4.5f,
                "Graphite selected font row text must meet 4.5:1, was " +
                    "${contrastRatio(colors.content, colors.container)}"
            )
        }
    }

    @Test
    fun graphiteUnselectedRowKeepsTheNeutralSurfaceFamily() {
        val colors = fontChoiceRowColors(selected = false, graphitePalette = true, scheme = graphiteLight)
        assertEquals(graphiteLight.surfaceContainerHigh.copy(alpha = 0.55f), colors.container)
        assertEquals(graphiteLight.onSurface, colors.content)
        assertNull(colors.border)
    }

    @Test
    fun expressiveSelectedRowKeepsTheTranslucentPrimaryContainer() {
        val colors = fontChoiceRowColors(selected = true, graphitePalette = false, scheme = graphiteLight)
        assertEquals(graphiteLight.primaryContainer.copy(alpha = 0.65f), colors.container)
        assertEquals(graphiteLight.onSurface, colors.content)
        assertNull(colors.border)
    }
}
