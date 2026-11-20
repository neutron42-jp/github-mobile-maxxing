@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class)

package dev.maxxing.github.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.maxxing.github.model.AppBranding
import dev.maxxing.github.ui.theme.MaxxingTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The sign-in wordmark used to break inside
 * "github-mobile-maxxing", leaving a lone trailing "g" on a second line on narrow Android
 * devices (reported on 1080x2400 @ 420 dpi, i.e. ~411 dp wide -> 315 dp of card content).
 */
class SignInWordmarkLayoutTest {

    /**
     * Card content widths: screen width - 2 * 20 dp outer padding - 2 * 28 dp card padding,
     * capped by the card's 520 dp max width (520 - 56 = 464 dp).
     *
     * 315 dp is the reported 411 dp device, 264 dp a 360 dp phone and 224 dp a 320 dp phone.
     */
    private val narrowContentWidthsDp = listOf(315, 264, 224)
    private val wideContentWidthDp = 464
    private val fontScales = listOf(1f, 1.3f, 2f)

    private data class Rendered(
        val lineCount: Int,
        val lines: List<String>,
        val text: String,
        val width: Float,
        val firstBaseline: Float,
        val didOverflowWidth: Boolean
    )

    @Test
    fun wordmarkFitsOnOneLineAtTheReportedDeviceWidth() {
        val rendered = renderWordmark(contentWidthDp = 315, fontScale = 1f)
        assertEquals(
            1,
            rendered.lineCount,
            "The wordmark must fit on one line at the reported 315 dp of card content (was $rendered)"
        )
        assertFalse(rendered.didOverflowWidth, "The one-line wordmark must not be clipped (was $rendered)")
        assertTrue(
            rendered.width <= 315.5f,
            "The one-line wordmark must stay inside the card content width (was $rendered)"
        )
        assertEquals(
            AppBranding.APP_NAME,
            rendered.text,
            "The common one-line layout must keep the exact brand string"
        )
    }

    @Test
    fun wordmarkNeverStrandsASingleCharacterOnNarrowLayouts() {
        for (fontScale in fontScales) {
            for (contentWidthDp in narrowContentWidthsDp) {
                val rendered = renderWordmark(contentWidthDp, fontScale)
                assertFalse(
                    rendered.didOverflowWidth,
                    "Wordmark must not be clipped at ${contentWidthDp}dp / fontScale $fontScale (was $rendered)"
                )
                assertTrue(
                    rendered.width <= contentWidthDp + 0.5f,
                    "Wordmark width ${rendered.width} exceeds ${contentWidthDp}dp (was $rendered)"
                )
                for ((index, line) in rendered.lines.withIndex()) {
                    assertTrue(
                        line.length > 1,
                        "Line ${index + 1} of the wordmark is stranded at ${contentWidthDp}dp / " +
                            "fontScale $fontScale: '$line' (was $rendered)"
                    )
                }
            }
        }
    }

    @Test
    fun wordmarkOnlyWrapsAtHyphenBoundariesAndKeepsTheFullBrandText() {
        for (fontScale in fontScales) {
            for (contentWidthDp in narrowContentWidthsDp) {
                val rendered = renderWordmark(contentWidthDp, fontScale)
                rendered.lines.forEachIndexed { index, line ->
                    if (index < rendered.lines.lastIndex) {
                        assertTrue(
                            line.endsWith("-"),
                            "Wordmark broke away from a hyphen at ${contentWidthDp}dp / fontScale " +
                                "$fontScale: '$line' (was $rendered)"
                        )
                    }
                }
                assertEquals(
                    AppBranding.APP_NAME,
                    rendered.lines.joinToString(""),
                    "The complete brand wordmark must stay visible and accessible (was $rendered)"
                )
            }
        }
    }

    @Test
    fun wordmarkWrapsInsteadOfShrinkingBelowTheReadableFloorOnVeryNarrowCards() {
        val rendered = renderWordmark(contentWidthDp = 224, fontScale = 1f)
        assertTrue(
            rendered.lineCount > 1,
            "A 224 dp card cannot fit the 24 sp wordmark on one line, so it must wrap at the " +
                "hyphens rather than shrink further (was $rendered)"
        )
    }

    @Test
    fun wordmarkKeepsTheNormalDisplaySmallLayoutOnWideScreens() {
        val wide = renderPlainDisplaySmallText(wideContentWidthDp)
        val rendered = renderWordmark(wideContentWidthDp, fontScale = 1f)
        assertEquals(1, rendered.lineCount, "Wide-screen wordmark must stay on one line")
        assertEquals(
            wide.width,
            rendered.width,
            0.5f,
            "Wide-screen wordmark must use the unchanged displaySmall layout"
        )
        assertTrue(
            abs(wide.firstBaseline - rendered.firstBaseline) <= 0.5f,
            "Wide-screen wordmark baseline must match displaySmall (was $rendered)"
        )
        assertEquals(AppBranding.APP_NAME, rendered.text)
    }

    @Test
    fun wordmarkRetainsTheFullBrandTextForAccessibility() {
        // 315 dp resolves to the one-line layout, 224 dp to the hyphen-wrapped layout.
        for (contentWidthDp in listOf(315, 224)) {
            val rendered = renderWordmark(contentWidthDp, fontScale = 1f)
            assertEquals(
                AppBranding.APP_NAME,
                rendered.text.replace("\u200B", ""),
                "Only zero-width break opportunities may be added to the announced brand text"
            )
        }
        assertTrue(
            SignInWordmarkText.contains("-\u200B"),
            "Hyphens must carry the zero-width break opportunity that prevents the orphaned 'g'"
        )
    }

    private fun renderWordmark(contentWidthDp: Int, fontScale: Float): Rendered =
        render(contentWidthDp, fontScale) {
            SignInWordmark(onTextLayout = it)
        }

    private fun renderPlainDisplaySmallText(contentWidthDp: Int): Rendered =
        render(contentWidthDp, fontScale = 1f) {
            Text(
                text = AppBranding.APP_NAME,
                style = MaterialTheme.typography.displaySmall,
                onTextLayout = it
            )
        }

    private fun render(
        contentWidthDp: Int,
        fontScale: Float,
        content: @Composable (onTextLayout: (TextLayoutResult) -> Unit) -> Unit
    ): Rendered {
        var layout: TextLayoutResult? = null
        val scene = ImageComposeScene(
            width = contentWidthDp + 8,
            height = 260,
            density = Density(density = 1f, fontScale = fontScale)
        ) {
            MaxxingTheme(
                useDynamicColor = false,
                darkThemeOverride = false
            ) {
                Box(modifier = Modifier.width(contentWidthDp.dp)) {
                    content { layout = it }
                }
            }
        }
        try {
            scene.render()
        } finally {
            scene.close()
        }
        val result = assertNotNull(layout, "Text should be laid out at ${contentWidthDp}dp")
        val rawText = result.layoutInput.text.text
        val lines = (0 until result.lineCount).map { index ->
            rawText
                .substring(result.getLineStart(index), result.getLineEnd(index))
                .replace("\u200B", "")
                .trim()
        }
        return Rendered(
            lineCount = result.lineCount,
            lines = lines,
            text = rawText,
            width = result.size.width.toFloat(),
            firstBaseline = result.firstBaseline,
            didOverflowWidth = result.didOverflowWidth
        )
    }
}
