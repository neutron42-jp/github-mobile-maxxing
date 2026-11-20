package dev.maxxing.github.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer

/**
 * Loads Markdown images with Coil like [Coil3ImageTransformerImpl], but sizes them the way github.com does.
 *
 * GitHub lays README images out in CSS pixels, which match dp. Coil reports an image's size in device pixels,
 * and the renderer's default `placeholderConfig` turns those pixels straight into the placeholder size, so on a
 * 2.6x screen a 20 px badge came out about 7.7 dp tall instead of 20 dp (#15). Reporting the size multiplied by
 * the screen density makes the placeholder match the image's GitHub size. Images wider than the text column are
 * still scaled down to fit.
 *
 * Limitation of multiplatform-markdown-renderer 0.39.x: all inline images in one paragraph share a single
 * placeholder size (the most recently loaded image's), so badges of different widths in one paragraph are fitted
 * into equally sized slots.
 */
object GitHubImageTransformer : ImageTransformer {

    // The default ImageData only fills the placeholder's width, so the image kept its device-pixel height inside
    // the enlarged placeholder. Fill the whole placeholder; ContentScale.Fit keeps the aspect ratio.
    @Composable
    override fun transform(link: String): ImageData? {
        val safeLink = sanitizeMarkdownImageUri(link) ?: return null
        return Coil3ImageTransformerImpl.transform(safeLink).copy(modifier = Modifier.fillMaxSize())
    }

    @Composable
    override fun intrinsicSize(painter: Painter): Size =
        scaleToDensity(Coil3ImageTransformerImpl.intrinsicSize(painter), LocalDensity.current.density)
}

internal fun scaleToDensity(imagePixels: Size, density: Float): Size =
    if (imagePixels.isUnspecified) imagePixels else imagePixels * density
