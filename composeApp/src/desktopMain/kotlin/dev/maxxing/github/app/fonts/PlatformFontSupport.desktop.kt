package dev.maxxing.github.app.fonts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font as DesktopFont
import dev.maxxing.github.model.CustomFontFile
import dev.maxxing.github.model.FontSelection
import dev.maxxing.github.model.FontSourceKind
import dev.maxxing.github.ui.i18n.appStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle as SkiaFontStyle
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private val SUPPORTED_FONT_EXTENSIONS = setOf("ttf", "otf", "ttc")

/** Weights declared for variable font files; Typography uses 400/500/600/700. */
private val DECLARED_WEIGHTS: List<Int> = (100..900 step 100).toList()

actual object PlatformFontManager {
    private val defaultFontsDir: File by lazy {
        val xdgData = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
            ?: "${System.getProperty("user.home")}/.local/share"
        File(xdgData, "github-mobile-maxxing/fonts").apply { mkdirs() }
    }

    /**
     * Family names from Skia's font manager, the same one FontFamily(name) is matched against when
     * text is drawn. (AWT also lists logical fonts such as "Dialog" that Skia does not know.) Names
     * Skia lists but cannot match, such as bitmap-only families, are left out.
     */
    actual fun listSystemFontFamilies(): List<String> = runCatching {
        val fontMgr = FontMgr.default
        (0 until fontMgr.familiesCount)
            .map { fontMgr.getFamilyName(it).trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .filter { name -> runCatching { fontMgr.matchFamilyStyle(name, SkiaFontStyle.NORMAL) != null }.getOrDefault(false) }
            .sorted()
    }.getOrDefault(emptyList())

    actual fun listImportedFonts(): List<CustomFontFile> = listImportedFonts(defaultFontsDir)

    /** The composite resolver chains the fallback after the primary font glyph by glyph. */
    actual val supportsLayeredFallback: Boolean = true

    internal fun listImportedFonts(fontsDir: File): List<CustomFontFile> {
        val files = fontsDir.listFiles { file ->
            file.isFile && file.extension.lowercase() in SUPPORTED_FONT_EXTENSIONS
        } ?: return emptyList()
        return files.sortedBy { it.name.lowercase() }.map { file ->
            CustomFontFile(
                id = file.absolutePath,
                displayName = file.nameWithoutExtension,
                filePath = file.absolutePath
            )
        }
    }

    actual fun deleteImportedFont(filePath: String): Boolean = deleteImportedFont(filePath, defaultFontsDir)

    internal fun deleteImportedFont(filePath: String, fontsDir: File): Boolean {
        val target = File(filePath)
        return runCatching {
            val deleted = target.isFile && isDirectChildOf(target, fontsDir) && target.delete()
            if (deleted) retiredImportPaths += target.canonicalPath
            deleted
        }.getOrDefault(false)
    }

    /**
     * Paths of fonts deleted in this process. Compose's font cache registers a file font once per
     * path and never drops it, so a new file at a deleted path would keep rendering as the old one
     * until restart. Such paths are therefore not reused for a later import.
     */
    private val retiredImportPaths: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * Checks that [source] is a font Skia can load, copies it into [fontsDir] and checks the copy.
     * Returns null, leaving nothing behind, if either check fails.
     */
    internal fun importFile(source: File, fontsDir: File = defaultFontsDir): CustomFontFile? {
        if (!source.exists() || !source.isFile) return null
        val ext = source.extension.lowercase()
        if (ext !in SUPPORTED_FONT_EXTENSIONS) return null
        if (source.length() > MAX_IMPORTED_FONT_BYTES || !hasFontSignature(source) || !isLoadableFontFile(source)) return null
        val safeBase = sanitizeFontFileBase(source.nameWithoutExtension)
        fontsDir.mkdirs()
        val dest = importFileNameCandidates(safeBase, ext)
            .map { File(fontsDir, it) }
            .first { !it.exists() && it.canonicalPath !in retiredImportPaths }
        val copied = runCatching { source.copyTo(dest, overwrite = false) }.isSuccess
        if (!copied || !isLoadableFontFile(dest)) {
            dest.delete()
            return null
        }
        return CustomFontFile(
            id = dest.absolutePath,
            displayName = dest.nameWithoutExtension,
            filePath = dest.absolutePath
        )
    }

    internal fun isLoadableFontFile(file: File): Boolean = loadSkiaTypeface(file) != null

    private fun hasFontSignature(file: File): Boolean = runCatching {
        val header = ByteArray(FONT_SIGNATURE_SIZE)
        file.inputStream().use { input -> input.readNBytes(header, 0, header.size) } == header.size &&
            hasFontFileSignature(header)
    }.getOrDefault(false)
}

private fun isDirectChildOf(target: File, dir: File): Boolean =
    target.canonicalFile.parentFile == dir.canonicalFile

private fun loadSkiaTypeface(file: File): org.jetbrains.skia.Typeface? {
    if (!file.isFile || !file.canRead()) return null
    return try {
        FontMgr.default.makeFromFile(file.path)
    } catch (e: Exception) {
        null
    }
}

private sealed interface ChooserResult {
    data object Cancelled : ChooserResult
    data object Failed : ChooserResult
    data class Selected(val file: File) : ChooserResult
}

private fun showFontFileChooser(title: String, filterLabel: String): ChooserResult = try {
    val chooser = JFileChooser().apply {
        dialogTitle = title
        fileFilter = FileNameExtensionFilter(filterLabel, "ttf", "otf", "ttc")
        isAcceptAllFileFilterUsed = false
        isFileHidingEnabled = false
    }
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile?.let { ChooserResult.Selected(it) } ?: ChooserResult.Failed
    } else {
        ChooserResult.Cancelled
    }
} catch (e: Exception) {
    ChooserResult.Failed
}

@Composable
actual fun rememberFontFileImporter(onImported: (CustomFontFile?) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val currentOnImported by rememberUpdatedState(onImported)
    val strings by rememberUpdatedState(appStrings())
    return remember(scope) {
        {
            scope.launch {
                // Swing components must be created and shown on the EDT.
                val choice = withContext(Dispatchers.Swing) {
                    showFontFileChooser(strings.fontFileChooserTitle, strings.fontFileFilterLabel)
                }
                if (choice == ChooserResult.Cancelled) return@launch
                // Once a file is chosen, finish the import and report it even if the dialog closes.
                withContext(NonCancellable) {
                    val imported = (choice as? ChooserResult.Selected)?.let { selected ->
                        withContext(Dispatchers.IO) {
                            runCatching { PlatformFontManager.importFile(selected.file) }.getOrNull()
                        }
                    }
                    currentOnImported(imported)
                }
            }
        }
    }
}

/**
 * Builds a family for an imported font file, or returns null if Skia cannot read it.
 *
 * A static file is declared once at Normal weight. Skia paragraphs embolden a face whose own
 * weight is below 600 when 600 or more is requested, so SemiBold and Bold still look heavier. A
 * variable file with a `wght` axis gets one entry per weight, each set to that weight on the axis,
 * so it renders real weights.
 */
internal fun buildCustomFileFontFamily(file: File): FontFamily? {
    val typeface = loadSkiaTypeface(file) ?: return null
    val weightAxis = try {
        typeface.variationAxes?.firstOrNull { it.tag == "wght" }
    } catch (e: Exception) {
        null
    }
    if (weightAxis == null) {
        return FontFamily(DesktopFont(file, FontWeight.Normal, FontStyle.Normal))
    }
    return FontFamily(
        DECLARED_WEIGHTS.map { weight ->
            val axisValue = weight.toFloat().coerceIn(weightAxis.minValue, weightAxis.maxValue)
            DesktopFont(
                file = file,
                weight = FontWeight(weight),
                style = FontStyle.Normal,
                variationSettings = FontVariation.Settings(FontVariation.Setting("wght", axisValue))
            )
        }
    )
}

@OptIn(ExperimentalTextApi::class) // FontFamily(name) for installed families
internal fun desktopFontFamilyFor(selection: FontSelection): FontFamily? = when (selection.kind) {
    FontSourceKind.SYSTEM_DEFAULT -> null
    FontSourceKind.GENERIC_SANS -> FontFamily.SansSerif
    FontSourceKind.GENERIC_SERIF -> FontFamily.Serif
    FontSourceKind.GENERIC_MONOSPACE -> FontFamily.Monospace
    // The desktop FontFamily(name) declares every weight and style, so real bold faces are used.
    FontSourceKind.SYSTEM_FAMILY -> selection.value.takeIf { it.isNotBlank() }?.let { FontFamily(it) }
    // A missing or unreadable saved file resolves to null, which means the default font.
    FontSourceKind.CUSTOM_FILE -> buildCustomFileFontFamily(File(selection.value))
}

/**
 * Remembered by the selection's value: the fonts of FontFamily(name) have no equals, so a family
 * built again on every recomposition would never match and would rebuild the composite resolver.
 */
@Composable
private fun rememberDesktopFontFamily(selection: FontSelection): FontFamily? =
    remember(selection.kind, selection.value) { desktopFontFamilyFor(selection) }

@Composable
actual fun ProvideAppTypographyFont(
    primary: FontSelection,
    fallback: FontSelection,
    content: @Composable (FontFamily?) -> Unit
) {
    val primaryFamily = rememberDesktopFontFamily(primary)
    val fallbackFamily = rememberDesktopFontFamily(fallback)
    val baseResolver = LocalFontFamilyResolver.current

    val composite = remember(baseResolver, primary, fallback, primaryFamily, fallbackFamily) {
        if (fallbackFamily == null) {
            null
        } else {
            val markerIdentity = "maxxing_composite|${primary.kind}|${primary.value}|${fallback.kind}|${fallback.value}"
            createCompositeFontSetup(
                baseResolver = baseResolver,
                markerIdentity = markerIdentity,
                primary = primaryFamily ?: FontFamily.SansSerif,
                fallback = fallbackFamily
            )
        }
    }

    // If the composite cannot be set up, use the primary font alone. The fallback never replaces it.
    CompositionLocalProvider(LocalFontFamilyResolver provides (composite?.resolver ?: baseResolver)) {
        content(composite?.family ?: primaryFamily)
    }
}
