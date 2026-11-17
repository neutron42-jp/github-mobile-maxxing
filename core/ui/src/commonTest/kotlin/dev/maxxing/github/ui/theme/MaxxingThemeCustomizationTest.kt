package dev.maxxing.github.ui.theme

import androidx.compose.ui.text.font.FontFamily
import dev.maxxing.github.model.FontPreset
import dev.maxxing.github.model.FontSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class MaxxingThemeCustomizationTest {
    @Test
    fun buildMaxxingTypographyAppliesOverrideExceptMonospaceLabelSmall() {
        assertSame(MaxxingTypography, buildMaxxingTypography(null))

        val customFamily = FontFamily.SansSerif
        val updated = buildMaxxingTypography(customFamily)

        assertEquals(customFamily, updated.displayLarge.fontFamily)
        assertEquals(customFamily, updated.headlineMedium.fontFamily)
        assertEquals(customFamily, updated.titleLarge.fontFamily)
        assertEquals(customFamily, updated.bodyLarge.fontFamily)
        assertEquals(customFamily, updated.bodySmall.fontFamily)
        assertEquals(customFamily, updated.labelLarge.fontFamily)
        assertEquals(customFamily, updated.labelMedium.fontFamily)
        assertEquals(FontFamily.Monospace, updated.labelSmall.fontFamily)
    }

    @Test
    fun fontLabelsResolvePresetsAndBundledOptions() {
        assertEquals("Ubuntu + Kaisei Opti", FontPreset.labelFor(FontPreset.UBUNTU_OVER_KAISEI_OPTI))
        assertEquals("Custom", FontPreset.labelFor(null))
        assertEquals("Ubuntu", FontSelection.BundledUbuntu.displayLabel())
        assertEquals("Kaisei Opti", FontSelection.BundledKaiseiOpti.displayLabel())
    }
}
