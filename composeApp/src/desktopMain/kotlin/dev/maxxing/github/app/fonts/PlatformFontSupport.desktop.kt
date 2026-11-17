@file:OptIn(ExperimentalTextApi::class, InternalComposeUiApi::class)
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package dev.maxxing.github.app.fonts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.PlatformTypefacesLoader
import androidx.compose.ui.text.font.SkiaFontLoader
import androidx.compose.ui.text.font.createPlatformFontFamilyResolver
import androidx.compose.ui.text.font.platformTypefacesLoader
import androidx.compose.ui.text.platform.Font as DesktopFileFont
import androidx.compose.ui.text.platform.FontLoadResult
import androidx.compose.ui.text.platform.SystemFont
import dev.maxxing.github.model.CustomFontFile
import dev.maxxing.github.model.FontSelection
import dev.maxxing.github.model.FontSourceKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.GraphicsEnvironment
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

actual object PlatformFontManager {
    private val fontsDir: File by lazy {
        val xdgData = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
            ?: "${System.getProperty("user.home")}/.local/share"
        File(xdgData, "github-mobile-maxxing/fonts").apply { mkdirs() }
    }

    actual fun listSystemFontFamilies(): List<String> = runCatching {
        GraphicsEnvironment.getLocalGraphicsEnvironment()
            .availableFontFamilyNames
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
    }.getOrDefault(emptyList())

    actual fun listImportedFonts(): List<CustomFontFile> {
        val files = fontsDir.listFiles { file ->
            file.isFile && (file.extension.equals("ttf", ignoreCase = true) ||
                file.extension.equals("otf", ignoreCase = true))
        } ?: return emptyList()
        return files.sortedBy { it.name.lowercase() }.map { file ->
            CustomFontFile(
                id = file.absolutePath,
                displayName = file.nameWithoutExtension,
                filePath = file.absolutePath
            )
        }
    }

    actual fun deleteImportedFont(filePath: String): Boolean {
        val target = File(filePath)
        return runCatching {
            target.exists() && target.canonicalPath.startsWith(fontsDir.canonicalPath) && target.delete()
        }.getOrDefault(false)
    }

    internal fun importFile(source: File): CustomFontFile? {
        if (!source.exists() || !source.isFile) return null
        val ext = source.extension.lowercase()
        if (ext != "ttf" && ext != "otf") return null
        val safeBase = source.nameWithoutExtension
            .replace(Regex("""[^\p{L}\p{N}_-]+"""), "_")
            .trim('_')
            .ifBlank { "custom_font" }
        var dest = File(fontsDir, "$safeBase.$ext")
        var counter = 1
        while (dest.exists()) {
            dest = File(fontsDir, "${safeBase}_$counter.$ext")
            counter++
        }
        source.copyTo(dest, overwrite = false)
        return CustomFontFile(
            id = dest.absolutePath,
            displayName = dest.nameWithoutExtension,
            filePath = dest.absolutePath
        )
    }
}

@Composable
actual fun rememberFontFileImporter(onImported: (CustomFontFile?) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    return remember(onImported) {
        {
            scope.launch {
                val imported = withContext(Dispatchers.IO) {
                    runCatching {
                        val chooser = JFileChooser().apply {
                            dialogTitle = "Select Font File (.ttf / .otf)"
                            fileFilter = FileNameExtensionFilter("Font files (*.ttf, *.otf)", "ttf", "otf")
                            isAcceptAllFileFilterUsed = false
                        }
                        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            chooser.selectedFile?.let { PlatformFontManager.importFile(it) }
                        } else {
                            null
                        }
                    }.getOrNull()
                }
                onImported(imported)
            }
        }
    }
}

@Composable
private fun resolveSingleDesktopFontFamily(selection: FontSelection): FontFamily? {
    val ubuntu = rememberBundledUbuntuFontFamily()
    val kaiseiOpti = rememberBundledKaiseiOptiFontFamily()
    return when (selection.kind) {
        FontSourceKind.SYSTEM_DEFAULT -> null
        FontSourceKind.BUNDLED_UBUNTU -> ubuntu
        FontSourceKind.BUNDLED_KAISEI_OPTI -> kaiseiOpti
        FontSourceKind.GENERIC_SANS -> FontFamily.SansSerif
        FontSourceKind.GENERIC_SERIF -> FontFamily.Serif
        FontSourceKind.GENERIC_MONOSPACE -> FontFamily.Monospace
        FontSourceKind.SYSTEM_FAMILY -> selection.value.takeIf { it.isNotBlank() }?.let { FontFamily(it) }
        FontSourceKind.CUSTOM_FILE -> remember(selection.value) {
            val file = File(selection.value)
            if (file.exists() && file.isFile) {
                FontFamily(DesktopFileFont(file, weight = FontWeight.Normal))
            } else {
                null
            }
        }
    }
}

private class CompositePlatformTypefacesLoader(
    private val delegate: PlatformTypefacesLoader,
    private val baseResolver: FontFamily.Resolver,
    private val markerIdentity: String,
    private val primary: FontFamily,
    private val fallback: FontFamily
) : PlatformTypefacesLoader {
    override val cacheKey: Any?
        get() = delegate.cacheKey

    override val fontCollection: Any
        get() = delegate.fontCollection

    override fun loadBlocking(font: Font): Any? {
        if (font is SystemFont && font.identity == markerIdentity) {
            return resolveComposite(font.weight, font.style)
        }
        return delegate.loadBlocking(font)
    }

    override suspend fun awaitLoad(font: Font): Any? {
        if (font is SystemFont && font.identity == markerIdentity) {
            return resolveComposite(font.weight, font.style)
        }
        return delegate.awaitLoad(font)
    }

    override fun loadPlatformTypes(
        fontFamily: FontFamily,
        fontWeight: FontWeight,
        fontStyle: FontStyle
    ): Any = delegate.loadPlatformTypes(fontFamily, fontWeight, fontStyle)

    private fun resolveComposite(weight: FontWeight, style: FontStyle): FontLoadResult? {
        val p = resolveFontLoadResult(primary, weight, style)
        val f = resolveFontLoadResult(fallback, weight, style)
        return when {
            p != null && f != null -> FontLoadResult(
                typeface = p.typeface ?: f.typeface,
                aliases = (p.aliases + f.aliases).distinct()
            )
            p != null -> p
            f != null -> f
            else -> null
        }
    }

    private fun resolveFontLoadResult(
        family: FontFamily,
        weight: FontWeight,
        style: FontStyle
    ): FontLoadResult? {
        val fromResolver = baseResolver.resolve(family, weight, style, FontSynthesis.All).value as? FontLoadResult
        if (fromResolver != null) return fromResolver
        return if (family is FontListFontFamily) {
            val chosen = family.fonts.firstOrNull { it.weight == weight && it.style == style }
                ?: family.fonts.minByOrNull { kotlin.math.abs(it.weight.weight - weight.weight) }
            chosen?.let { delegate.loadBlocking(it) as? FontLoadResult }
        } else {
            delegate.loadPlatformTypes(family, weight, style) as? FontLoadResult
        }
    }
}

@Composable
actual fun ProvideAppTypographyFont(
    primary: FontSelection,
    fallback: FontSelection,
    content: @Composable (FontFamily?) -> Unit
) {
    val primaryFamily = resolveSingleDesktopFontFamily(primary)
    val fallbackFamily = resolveSingleDesktopFontFamily(fallback)

    if (fallbackFamily == null) {
        content(primaryFamily)
        return
    }

    val effectivePrimary = primaryFamily ?: FontFamily.SansSerif
    val baseResolver = LocalFontFamilyResolver.current
    val markerIdentity = remember(primary, fallback) {
        "maxxing_composite_${primary.kind}_${primary.value.hashCode()}_${fallback.kind}_${fallback.value.hashCode()}"
    }
    val markerFamily = remember(markerIdentity) { FontFamily(markerIdentity) }

    val preloaded by produceState(initialValue = false, effectivePrimary, fallbackFamily, baseResolver) {
        runCatching { baseResolver.preload(effectivePrimary) }
        runCatching { baseResolver.preload(fallbackFamily) }
        value = true
    }

    val compositeResolver = remember(baseResolver, markerIdentity, effectivePrimary, fallbackFamily, preloaded) {
        val backend = baseResolver.platformTypefacesLoader() ?: SkiaFontLoader()
        val loader = CompositePlatformTypefacesLoader(
            delegate = backend,
            baseResolver = baseResolver,
            markerIdentity = markerIdentity,
            primary = effectivePrimary,
            fallback = fallbackFamily
        )
        createPlatformFontFamilyResolver(loader)
    }

    CompositionLocalProvider(LocalFontFamilyResolver provides compositeResolver) {
        content(if (preloaded) markerFamily else effectivePrimary)
    }
}
