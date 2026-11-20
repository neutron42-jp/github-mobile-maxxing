package dev.maxxing.github.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.scheme.DynamicScheme
import dev.maxxing.github.model.DiffLineKind
import dev.maxxing.github.model.MaxxingThemeDefaults
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GraphiteContrastTest {

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

    private fun graphite(dark: Boolean): ColorScheme = generatedScheme(
        MaxxingThemeDefaults.GRAPHITE_SEED_COLOR_HEX,
        dark = dark,
        style = PaletteStyle.Monochrome
    )

    private fun iris(dark: Boolean): ColorScheme = generatedScheme(
        MaxxingThemeDefaults.IRIS_SEED_COLOR_HEX,
        dark = dark,
        style = PaletteStyle.Expressive
    )

    private fun assertColor(expected: Color, actual: Color, label: String) {
        assertTrue(
            abs(expected.red - actual.red) < 0.002f &&
                abs(expected.green - actual.green) < 0.002f &&
                abs(expected.blue - actual.blue) < 0.002f &&
                abs(expected.alpha - actual.alpha) < 0.002f,
            "$label: expected ${expected.value.toString(16)}, got ${actual.value.toString(16)}"
        )
    }

    /** Foreground is composited over the background first, matching how Compose draws the layer. */
    private fun contrast(foreground: Color, background: Color): Float {
        val drawn = foreground.compositeOver(background).luminance()
        val base = background.luminance()
        val lighter = maxOf(drawn, base)
        val darker = minOf(drawn, base)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private fun assertContrastAtLeast(
        minimum: Float,
        foreground: Color,
        background: Color,
        label: String
    ) {
        val actual = contrast(foreground, background)
        assertTrue(
            actual >= minimum,
            "$label: expected at least $minimum:1, got $actual:1"
        )
    }

    @Test
    fun graphiteFileRowUsesHighestWithPrimarySelectionBorder() {
        for (dark in listOf(false, true)) {
            val scheme = graphite(dark)
            val mode = if (dark) "dark" else "light"

            val selected = GraphiteContrast.reviewFileRowColors(scheme, true, selected = true)
            val selectedBorder = assertNotNull(selected.border)
            assertColor(scheme.surfaceContainerHighest, selected.container, "$mode selected row")
            assertColor(scheme.primary, selectedBorder, "$mode selected row border")
            assertContrastAtLeast(4.5f, scheme.onSurface, selected.container, "$mode selected filename")
            assertContrastAtLeast(4.5f, scheme.onSurfaceVariant, selected.container, "$mode selected path")
            assertContrastAtLeast(3f, selectedBorder, selected.container, "$mode selected border")

            val unselected = GraphiteContrast.reviewFileRowColors(scheme, true, selected = false)
            assertColor(scheme.surfaceContainerLow, unselected.container, "$mode unselected row")
            assertNull(unselected.border, "$mode unselected row must not have a border")

            val legacy = GraphiteContrast.reviewFileRowColors(scheme, false, selected = true)
            assertColor(
                scheme.primaryContainer.copy(alpha = 0.6f),
                legacy.container,
                "$mode legacy selected row"
            )
            assertNull(legacy.border, "$mode legacy selection must keep the old fill")
        }
    }

    @Test
    fun graphiteCompletionCardUsesHighAndReadableCheck() {
        for (dark in listOf(false, true)) {
            val scheme = graphite(dark)
            val mode = if (dark) "dark" else "light"
            val container = GraphiteContrast.reviewCompletionCardContainer(scheme, graphitePalette = true)
            assertColor(scheme.surfaceContainerHigh, container, "$mode completion card")
            assertContrastAtLeast(4.5f, scheme.onSurface, container, "$mode completion title")
            assertContrastAtLeast(4.5f, scheme.onSurfaceVariant, container, "$mode completion body")

            val checkCircle = GraphiteContrast.LegacySuccess.copy(alpha = 0.18f).compositeOver(container)
            val check = GraphiteContrast.statusAccentColor(scheme, graphitePalette = true)
            assertContrastAtLeast(3f, check, checkCircle, "$mode completion check")

            val legacy = GraphiteContrast.reviewCompletionCardContainer(scheme, graphitePalette = false)
            assertColor(
                scheme.primaryContainer.copy(alpha = 0.72f),
                legacy,
                "$mode legacy completion card"
            )
        }
    }

    @Test
    fun graphiteStartCardUsesHighAndPrimaryProgress() {
        for (dark in listOf(false, true)) {
            val scheme = graphite(dark)
            val mode = if (dark) "dark" else "light"
            val container = GraphiteContrast.reviewStartCardContainer(scheme, graphitePalette = true)
            assertColor(scheme.surfaceContainerHigh, container, "$mode start card")
            assertContrastAtLeast(4.5f, scheme.onSurface, container, "$mode start title")
            assertContrastAtLeast(4.5f, scheme.onSurfaceVariant, container, "$mode start body")
            assertContrastAtLeast(4.5f, scheme.primary, container, "$mode start progress")

            val legacy = GraphiteContrast.reviewStartCardContainer(scheme, graphitePalette = false)
            assertColor(
                scheme.primaryContainer.copy(alpha = 0.55f),
                legacy,
                "$mode legacy start card"
            )
        }
    }

    @Test
    fun graphiteBranchChipUsesHighestWithExplicitOnSurface() {
        for (dark in listOf(false, true)) {
            val scheme = graphite(dark)
            val mode = if (dark) "dark" else "light"
            val chip = GraphiteContrast.branchChipColors(scheme, graphitePalette = true)
            val chipContent = assertNotNull(chip.content)
            assertColor(scheme.surfaceContainerHighest, chip.container, "$mode branch chip")
            assertColor(scheme.onSurface, chipContent, "$mode branch chip content")
            assertContrastAtLeast(4.5f, chipContent, chip.container, "$mode branch chip text")

            val legacy = GraphiteContrast.branchChipColors(scheme, graphitePalette = false)
            assertColor(
                scheme.primaryContainer.copy(alpha = 0.6f),
                legacy.container,
                "$mode legacy branch chip"
            )
            assertNull(legacy.content, "$mode legacy branch chip keeps its inherited content color")
        }
    }

    @Test
    fun graphiteStateAccentsAndAdditionsMeetThresholds() {
        for (dark in listOf(false, true)) {
            val scheme = graphite(dark)
            val mode = if (dark) "dark" else "light"
            val high = scheme.surfaceContainerHigh
            val success = GraphiteContrast.statusAccentColor(scheme, graphitePalette = true)
            val warning = GraphiteContrast.statusAccentColor(scheme, graphitePalette = true, isWarning = true)

            // State icons and the row selection border are non-text markers.
            assertContrastAtLeast(3f, success, scheme.surfaceContainerHighest, "$mode viewed icon")
            assertContrastAtLeast(3f, warning, scheme.surfaceContainerHighest, "$mode changed icon")
            assertContrastAtLeast(3f, success, scheme.surfaceContainerLow, "$mode viewed icon on Low")
            assertContrastAtLeast(3f, warning, scheme.surfaceContainerLow, "$mode changed icon on Low")

            // Badge labels and the additions counter are text.
            val viewedBackground = GraphiteContrast.LegacySuccess.copy(alpha = 0.2f).compositeOver(high)
            val changedBackground = GraphiteContrast.LegacyWarning.copy(alpha = 0.2f).compositeOver(high)
            val resolvedBackground = GraphiteContrast.LegacySuccess.copy(alpha = 0.16f).compositeOver(high)
            assertContrastAtLeast(4.5f, success, viewedBackground, "$mode viewed label")
            assertContrastAtLeast(4.5f, warning, changedBackground, "$mode changed label")
            assertContrastAtLeast(4.5f, success, resolvedBackground, "$mode resolved label")
            assertContrastAtLeast(4.5f, success, high, "$mode additions counter")

            assertEquals(
                GraphiteContrast.LegacySuccess,
                GraphiteContrast.statusAccentColor(scheme, graphitePalette = false)
            )
            assertEquals(
                GraphiteContrast.LegacyWarning,
                GraphiteContrast.statusAccentColor(scheme, graphitePalette = false, isWarning = true)
            )
            assertEquals(
                GraphiteContrast.AddedDiffGreen,
                GraphiteContrast.additionsCounterColor(scheme, graphitePalette = false)
            )
        }
    }

    @Test
    fun graphiteDiffRowsMeetContrastAcrossKindsAndSelection() {
        for (dark in listOf(false, true)) {
            val scheme = graphite(dark)
            val mode = if (dark) "dark" else "light"
            for (kind in DiffLineKind.entries) {
                val expectedBackground = when (kind) {
                    DiffLineKind.ADDED ->
                        GraphiteContrast.AddedDiffGreen.copy(alpha = 0.14f).compositeOver(scheme.surface)
                    DiffLineKind.REMOVED ->
                        scheme.error.copy(alpha = 0.14f).compositeOver(scheme.surface)
                    DiffLineKind.META -> scheme.surfaceContainerHighest
                    DiffLineKind.CONTEXT -> scheme.surfaceContainerLow
                }
                var unselectedBackground: Color? = null
                for (selected in listOf(false, true)) {
                    val colors = GraphiteContrast.diffLineColors(
                        scheme,
                        kind,
                        selected = selected,
                        graphitePalette = true
                    )
                    val state = if (selected) "selected" else "unselected"
                    val label = "$mode $kind $state"
                    assertColor(expectedBackground, colors.background, "$label background")
                    assertEquals(1f, colors.background.alpha, 0.0001f, "$label background must be opaque")
                    assertContrastAtLeast(4.5f, colors.lineNumber, colors.background, "$label line number")
                    assertContrastAtLeast(4.5f, colors.prefix, colors.background, "$label prefix")
                    assertContrastAtLeast(4.5f, colors.content, colors.background, "$label content")

                    if (kind == DiffLineKind.ADDED || kind == DiffLineKind.REMOVED) {
                        val band = colors.inlineSpanBackground!!
                        assertContrastAtLeast(3f, band, colors.background, "$label band boundary")
                        assertContrastAtLeast(4.5f, colors.inlineSpanForeground, band, "$label band text")
                    } else {
                        assertNull(colors.inlineSpanBackground, "$label has no inline band")
                    }

                    if (selected) {
                        val selectionBorder = assertNotNull(colors.selectionBorder)
                        assertColor(scheme.primary, selectionBorder, "$label border")
                        assertContrastAtLeast(3f, selectionBorder, colors.background, "$label border")
                    } else {
                        assertNull(colors.selectionBorder, "$label has no border")
                    }

                    if (!selected) {
                        unselectedBackground = colors.background
                    } else {
                        assertColor(
                            unselectedBackground!!,
                            colors.background,
                            "$label selection keeps the row fill"
                        )
                    }
                }
            }
        }
    }

    @Test
    fun legacyDiffRowsKeepTheExistingOpacityForegroundsAndSpans() {
        for (dark in listOf(false, true)) {
            val scheme = graphite(dark)
            val mode = if (dark) "dark" else "light"

            val unselected = GraphiteContrast.diffLineColors(
                scheme,
                DiffLineKind.ADDED,
                selected = false,
                graphitePalette = false
            )
            assertColor(
                GraphiteContrast.AddedDiffGreen.copy(alpha = 0.14f),
                unselected.background,
                "$mode legacy added row"
            )
            assertColor(
                scheme.onSurfaceVariant.copy(alpha = 0.6f),
                unselected.lineNumber,
                "$mode legacy line number"
            )
            assertColor(GraphiteContrast.LegacySuccess, unselected.prefix, "$mode legacy +")
            assertColor(
                GraphiteContrast.AddedDiffGreen.copy(alpha = 0.36f),
                unselected.inlineSpanBackground!!,
                "$mode legacy added span"
            )
            assertColor(scheme.onSurface, unselected.inlineSpanForeground, "$mode legacy span text")
            assertNull(unselected.selectionBorder, "$mode legacy has no selection border")

            val selected = GraphiteContrast.diffLineColors(
                scheme,
                DiffLineKind.ADDED,
                selected = true,
                graphitePalette = false
            )
            assertColor(
                scheme.primaryContainer.copy(alpha = 0.45f),
                selected.background,
                "$mode legacy selected row"
            )
            assertNull(selected.selectionBorder, "$mode legacy selected row keeps its fill only")

            val removed = GraphiteContrast.diffLineColors(
                scheme,
                DiffLineKind.REMOVED,
                selected = false,
                graphitePalette = false
            )
            assertColor(scheme.error, removed.prefix, "$mode legacy -")
            assertColor(
                scheme.error.copy(alpha = 0.34f),
                removed.inlineSpanBackground!!,
                "$mode legacy removed span"
            )

            val meta = GraphiteContrast.diffLineColors(
                scheme,
                DiffLineKind.META,
                selected = false,
                graphitePalette = false
            )
            assertColor(scheme.surfaceContainerHighest, meta.background, "$mode legacy meta row")
            val context = GraphiteContrast.diffLineColors(
                scheme,
                DiffLineKind.CONTEXT,
                selected = false,
                graphitePalette = false
            )
            assertColor(scheme.surfaceContainerLow, context.background, "$mode legacy context row")
        }
    }

    @Test
    fun customGraphiteSeedKeepsTheLegacyExpressiveBranch() {
        // A custom #606060 seed (and a wallpaper scheme) leaves LocalGraphitePalette false.
        val customSeed = 0xFF606060L
        val style = resolveMaxxingPaletteStyle(customSeed, monochromePresetSelected = false)
        assertEquals(PaletteStyle.Expressive, style)

        val scheme = generatedScheme(customSeed, dark = false, style = style)
        val row = GraphiteContrast.diffLineColors(
            scheme,
            DiffLineKind.ADDED,
            selected = true,
            graphitePalette = false
        )
        assertColor(
            scheme.primaryContainer.copy(alpha = 0.45f),
            row.background,
            "custom seed legacy selected row"
        )
        assertNull(row.selectionBorder, "custom seed keeps the legacy selection fill")
        assertEquals(
            GraphiteContrast.LegacySuccess,
            GraphiteContrast.statusAccentColor(scheme, graphitePalette = false)
        )
    }

    @Test
    fun nonGraphitePalettesNeverUseTheGraphiteContrastBranches() {
        for (dark in listOf(false, true)) {
            val scheme = iris(dark)
            val mode = if (dark) "dark" else "light"
            for (kind in listOf(DiffLineKind.ADDED, DiffLineKind.REMOVED)) {
                val colors = GraphiteContrast.diffLineColors(
                    scheme,
                    kind,
                    selected = false,
                    graphitePalette = false
                )
                assertColor(
                    if (kind == DiffLineKind.ADDED) {
                        GraphiteContrast.AddedDiffGreen.copy(alpha = 0.14f)
                    } else {
                        scheme.error.copy(alpha = 0.14f)
                    },
                    colors.background,
                    "$mode Iris $kind row"
                )
            }
            assertEquals(
                scheme.primaryContainer.copy(alpha = 0.6f),
                GraphiteContrast.reviewFileRowColors(scheme, graphitePalette = false, selected = true).container
            )
            assertEquals(
                GraphiteContrast.LegacySuccess,
                GraphiteContrast.statusAccentColor(scheme, graphitePalette = false)
            )
        }
    }
}
