@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class)

package dev.maxxing.github.app.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.maxxing.github.model.DiffLine
import dev.maxxing.github.model.DiffLineKind
import dev.maxxing.github.ui.components.DiffLineRow
import dev.maxxing.github.ui.theme.GraphiteContrast
import dev.maxxing.github.ui.theme.MaxxingTheme
import org.jetbrains.skia.Bitmap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraphiteDiffLineRenderTest {
    @Test
    fun selectedDiffKeepsItsBorderAndInlineBandWhenWrappedOrScrolled() {
        for (dark in listOf(false, true)) {
            for (kind in listOf(DiffLineKind.ADDED, DiffLineKind.REMOVED)) {
                val scrolledHeight = renderSelectedLine(dark, kind, wrap = false)
                val wrappedHeight = renderSelectedLine(dark, kind, wrap = true)
                assertTrue(wrappedHeight > scrolledHeight, "Expected wrapping for $kind (dark=$dark)")
            }
        }
    }

    private fun renderSelectedLine(dark: Boolean, kind: DiffLineKind, wrap: Boolean): Int {
        val width = 360
        val scroll = ScrollState(initial = 48)
        var rowHeight = 0
        var borderColor = 0
        var bandColor = 0
        val scene = ImageComposeScene(width = width, height = 300, density = Density(2f)) {
            MaxxingTheme(useDynamicColor = false, darkThemeOverride = dark) {
                val colors = GraphiteContrast.diffLineColors(
                    MaterialTheme.colorScheme, kind, selected = true, graphitePalette = true
                )
                borderColor = checkNotNull(colors.selectionBorder).toArgb()
                bandColor = checkNotNull(colors.inlineSpanBackground).toArgb()
                Box(if (wrap) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().horizontalScroll(scroll)) {
                    DiffLineRow(
                        line = DiffLine(kind, "changed value; ".repeat(5), 123, 124),
                        wrapLines = wrap,
                        inlineHighlightRanges = listOf(0..6),
                        selected = true,
                        modifier = (if (wrap) Modifier.fillMaxWidth() else Modifier.width(440.dp))
                            .onGloballyPositioned { rowHeight = it.size.height }
                    )
                }
            }
        }
        try {
            scene.render().close()
            scene.render().use { image ->
                assertTrue(rowHeight in 1..image.height)
                if (!wrap) assertTrue(scroll.value in 1..scroll.maxValue)
                Bitmap.makeFromImage(image).use { bitmap ->
                    val state = "$kind (dark=$dark, wrap=$wrap)"
                    assertEquals(borderColor, bitmap.getColor(width / 2, 0), "$state top border")
                    assertEquals(borderColor, bitmap.getColor(width / 2, rowHeight - 1), "$state bottom border")
                    assertTrue(
                        (4 until rowHeight - 4).any { y ->
                            (4 until width - 4).any { x -> bitmap.getColor(x, y) == bandColor }
                        },
                        "$state highlighted span band was not rendered"
                    )
                }
            }
        } finally {
            scene.close()
        }
        return rowHeight
    }
}
