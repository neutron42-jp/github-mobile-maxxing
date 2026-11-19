package dev.maxxing.github.app.fonts

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import dev.maxxing.github.model.CustomFontFile
import dev.maxxing.github.model.FontSelection

/**
 * iOS has no custom-font support yet: the system font is always used and the "Typography & Fonts"
 * settings card is hidden (`PlatformCapabilities.supportsCustomFonts = false`). Stored font
 * preferences (e.g. synced from another platform) are ignored here.
 */
actual object PlatformFontManager {
    actual fun listSystemFontFamilies(): List<String> = emptyList()

    actual fun listImportedFonts(): List<CustomFontFile> = emptyList()

    actual fun deleteImportedFont(filePath: String): Boolean = false

    actual val supportsLayeredFallback: Boolean = false
}

@Composable
actual fun rememberFontFileImporter(onImported: (CustomFontFile?) -> Unit): () -> Unit = { onImported(null) }

@Composable
actual fun ProvideAppTypographyFont(
    primary: FontSelection,
    fallback: FontSelection,
    content: @Composable (FontFamily?) -> Unit
) {
    content(null)
}
