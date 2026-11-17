package dev.maxxing.github.app.fonts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.maxxing.github.app.resources.Res
import dev.maxxing.github.app.resources.kaisei_opti_bold
import dev.maxxing.github.app.resources.kaisei_opti_medium
import dev.maxxing.github.app.resources.kaisei_opti_regular
import dev.maxxing.github.app.resources.ubuntu_bold
import dev.maxxing.github.app.resources.ubuntu_medium
import dev.maxxing.github.app.resources.ubuntu_regular
import dev.maxxing.github.model.CustomFontFile
import dev.maxxing.github.model.FontSelection
import org.jetbrains.compose.resources.Font

expect object PlatformFontManager {
    fun listSystemFontFamilies(): List<String>
    fun listImportedFonts(): List<CustomFontFile>
    fun deleteImportedFont(filePath: String): Boolean
}

@Composable
expect fun rememberFontFileImporter(onImported: (CustomFontFile?) -> Unit): () -> Unit

@Composable
expect fun ProvideAppTypographyFont(
    primary: FontSelection,
    fallback: FontSelection,
    content: @Composable (FontFamily?) -> Unit
)

@Composable
internal fun rememberBundledUbuntuFontFamily(): FontFamily {
    val regular = Font(Res.font.ubuntu_regular, weight = FontWeight.Normal)
    val medium = Font(Res.font.ubuntu_medium, weight = FontWeight.Medium)
    val bold = Font(Res.font.ubuntu_bold, weight = FontWeight.Bold)
    return remember(regular, medium, bold) {
        FontFamily(regular, medium, bold)
    }
}

@Composable
internal fun rememberBundledKaiseiOptiFontFamily(): FontFamily {
    val regular = Font(Res.font.kaisei_opti_regular, weight = FontWeight.Normal)
    val medium = Font(Res.font.kaisei_opti_medium, weight = FontWeight.Medium)
    val bold = Font(Res.font.kaisei_opti_bold, weight = FontWeight.Bold)
    return remember(regular, medium, bold) {
        FontFamily(regular, medium, bold)
    }
}
