package dev.maxxing.github.app.fonts

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.Font as AndroidGraphicsFont
import android.graphics.fonts.FontFamily as AndroidGraphicsFontFamily
import android.graphics.fonts.FontStyle as AndroidGraphicsFontStyle
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import dev.maxxing.github.app.resources.Res
import dev.maxxing.github.auth.AndroidAuthContextHolder
import dev.maxxing.github.model.CustomFontFile
import dev.maxxing.github.model.FontSelection
import dev.maxxing.github.model.FontSourceKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

actual object PlatformFontManager {
    private val bundledBytesCache = ConcurrentHashMap<String, ByteArray>()

    private fun customFontsDir(context: Context): File =
        File(context.filesDir, "custom_fonts").apply { mkdirs() }

    actual fun listSystemFontFamilies(): List<String> = runCatching {
        val fontDirs = listOf(File("/system/fonts"), File("/product/fonts"))
        val names = linkedSetOf<String>()
        for (dir in fontDirs) {
            if (!dir.isDirectory || !dir.canRead()) continue
            dir.listFiles()?.forEach { f ->
                if (f.isFile && (f.extension.equals("ttf", true) || f.extension.equals("otf", true))) {
                    val base = f.nameWithoutExtension
                        .removeSuffix("-Regular")
                        .removeSuffix("-Bold")
                        .removeSuffix("-Medium")
                        .removeSuffix("-Italic")
                        .removeSuffix("-BoldItalic")
                    if (base.isNotBlank()) names += base
                }
            }
        }
        names.sorted()
    }.getOrDefault(emptyList())

    actual fun listImportedFonts(): List<CustomFontFile> {
        val context = runCatching { AndroidAuthContextHolder.requireContext() }.getOrNull() ?: return emptyList()
        val files = customFontsDir(context).listFiles { file ->
            file.isFile && (file.extension.equals("ttf", true) || file.extension.equals("otf", true))
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
        val context = runCatching { AndroidAuthContextHolder.requireContext() }.getOrNull() ?: return false
        val target = File(filePath)
        val dir = customFontsDir(context)
        return runCatching {
            target.exists() && target.canonicalPath.startsWith(dir.canonicalPath) && target.delete()
        }.getOrDefault(false)
    }

    internal fun importFromUri(context: Context, uri: Uri): CustomFontFile? {
        val resolver = context.contentResolver
        val rawName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "custom_font.ttf"

        val ext = rawName.substringAfterLast('.', "ttf").lowercase().let {
            if (it == "otf" || it == "ttf") it else "ttf"
        }
        val safeBase = rawName.substringBeforeLast('.')
            .replace(Regex("""[^\p{L}\p{N}_-]+"""), "_")
            .trim('_')
            .ifBlank { "custom_font" }

        val dir = customFontsDir(context)
        var dest = File(dir, "$safeBase.$ext")
        var counter = 1
        while (dest.exists()) {
            dest = File(dir, "${safeBase}_$counter.$ext")
            counter++
        }

        val copied = resolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
            true
        } ?: false
        if (!copied) return null

        return CustomFontFile(
            id = dest.absolutePath,
            displayName = dest.nameWithoutExtension,
            filePath = dest.absolutePath
        )
    }

    internal suspend fun ensureBundledBytesLoaded(selection: FontSelection) {
        val paths = bundledPathsFor(selection)
        for (path in paths) {
            if (!bundledBytesCache.containsKey(path)) {
                runCatching { Res.readBytes(path) }.getOrNull()?.let { bytes ->
                    bundledBytesCache[path] = bytes
                }
            }
        }
    }

    internal fun getCachedBytes(path: String): ByteArray? = bundledBytesCache[path]

    internal fun bundledPathsFor(selection: FontSelection): List<String> = when (selection.kind) {
        FontSourceKind.BUNDLED_UBUNTU -> listOf(
            "font/ubuntu_regular.ttf",
            "font/ubuntu_medium.ttf",
            "font/ubuntu_bold.ttf"
        )
        FontSourceKind.BUNDLED_KAISEI_OPTI -> listOf(
            "font/kaisei_opti_regular.ttf",
            "font/kaisei_opti_medium.ttf",
            "font/kaisei_opti_bold.ttf"
        )
        else -> emptyList()
    }

    internal fun resolveSystemFontFile(familyBase: String, weight: Int): File? {
        if (familyBase.isBlank()) return null
        val dirs = listOf(File("/system/fonts"), File("/product/fonts"))
        val suffixOrder = when {
            weight >= 650 -> listOf("-Bold", "-Medium", "-Regular", "")
            weight >= 450 -> listOf("-Medium", "-Regular", "-Bold", "")
            else -> listOf("-Regular", "", "-Medium", "-Bold")
        }
        for (dir in dirs) {
            if (!dir.isDirectory) continue
            for (suffix in suffixOrder) {
                for (ext in listOf("ttf", "otf")) {
                    val candidate = File(dir, "$familyBase$suffix.$ext")
                    if (candidate.isFile && candidate.canRead()) return candidate
                }
            }
        }
        return null
    }
}

@Composable
actual fun rememberFontFileImporter(onImported: (CustomFontFile?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            onImported(null)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val imported = withContext(Dispatchers.IO) {
                runCatching { PlatformFontManager.importFromUri(context, uri) }.getOrNull()
            }
            onImported(imported)
        }
    }
    return remember(launcher) {
        {
            launcher.launch(
                arrayOf(
                    "font/ttf",
                    "font/otf",
                    "application/x-font-ttf",
                    "application/x-font-opentype",
                    "application/octet-stream"
                )
            )
        }
    }
}

private class LayeredAndroidFont(
    private val primary: FontSelection,
    private val fallback: FontSelection,
    override val weight: FontWeight,
    override val style: FontStyle = FontStyle.Normal
) : AndroidFont(
    loadingStrategy = FontLoadingStrategy.Blocking,
    typefaceLoader = LayeredTypefaceLoader,
    variationSettings = FontVariation.Settings()
) {
    val cacheKey: String = "${primary.kind}:${primary.value}|${fallback.kind}:${fallback.value}|${weight.weight}"

    fun buildTypeface(context: Context): Typeface {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            buildApi29Typeface(context)?.let { return it }
        }
        return buildLegacyTypeface(context)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun buildApi29Typeface(context: Context): Typeface? {
        val primaryFam = buildAndroidFontFamily(primary)
        val fallbackFam = if (fallback.kind != FontSourceKind.SYSTEM_DEFAULT) {
            buildAndroidFontFamily(fallback)
        } else null

        val builder = when {
            primaryFam != null -> Typeface.CustomFallbackBuilder(primaryFam)
            fallbackFam != null -> Typeface.CustomFallbackBuilder(fallbackFam)
            else -> return null
        }

        if (primaryFam != null && fallbackFam != null) {
            builder.addCustomFallback(fallbackFam)
        }
        if (primary.kind == FontSourceKind.SYSTEM_DEFAULT ||
            primary.kind == FontSourceKind.GENERIC_SANS ||
            primary.kind == FontSourceKind.GENERIC_SERIF ||
            primary.kind == FontSourceKind.GENERIC_MONOSPACE
        ) {
            val systemFallback = when (primary.kind) {
                FontSourceKind.GENERIC_SERIF -> "serif"
                FontSourceKind.GENERIC_MONOSPACE -> "monospace"
                else -> "sans-serif"
            }
            builder.setSystemFallback(systemFallback)
        }

        builder.setStyle(
            AndroidGraphicsFontStyle(
                weight.weight,
                AndroidGraphicsFontStyle.FONT_SLANT_UPRIGHT
            )
        )
        return runCatching { builder.build() }.getOrNull()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun buildAndroidFontFamily(selection: FontSelection): AndroidGraphicsFontFamily? {
        val fonts = when (selection.kind) {
            FontSourceKind.BUNDLED_UBUNTU -> buildBundledFonts(
                regularPath = "font/ubuntu_regular.ttf",
                mediumPath = "font/ubuntu_medium.ttf",
                boldPath = "font/ubuntu_bold.ttf"
            )
            FontSourceKind.BUNDLED_KAISEI_OPTI -> buildBundledFonts(
                regularPath = "font/kaisei_opti_regular.ttf",
                mediumPath = "font/kaisei_opti_medium.ttf",
                boldPath = "font/kaisei_opti_bold.ttf"
            )
            FontSourceKind.CUSTOM_FILE -> {
                val file = File(selection.value)
                if (file.isFile && file.canRead()) {
                    runCatching { listOf(AndroidGraphicsFont.Builder(file).build()) }.getOrNull().orEmpty()
                } else emptyList()
            }
            FontSourceKind.SYSTEM_FAMILY -> {
                val file = PlatformFontManager.resolveSystemFontFile(selection.value, weight.weight)
                if (file != null) {
                    runCatching { listOf(AndroidGraphicsFont.Builder(file).build()) }.getOrNull().orEmpty()
                } else emptyList()
            }
            else -> emptyList()
        }
        if (fonts.isEmpty()) return null
        val famBuilder = AndroidGraphicsFontFamily.Builder(fonts.first())
        for (i in 1 until fonts.size) {
            famBuilder.addFont(fonts[i])
        }
        return runCatching { famBuilder.build() }.getOrNull()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun buildBundledFonts(
        regularPath: String,
        mediumPath: String,
        boldPath: String
    ): List<AndroidGraphicsFont> {
        val specs = listOf(
            regularPath to FontWeight.Normal.weight,
            mediumPath to FontWeight.Medium.weight,
            boldPath to FontWeight.Bold.weight
        )
        return specs.mapNotNull { (path, w) ->
            val bytes = PlatformFontManager.getCachedBytes(path) ?: return@mapNotNull null
            val buffer = ByteBuffer.allocateDirect(bytes.size).apply {
                put(bytes)
                flip()
            }
            runCatching {
                AndroidGraphicsFont.Builder(buffer)
                    .setWeight(w)
                    .setSlant(AndroidGraphicsFontStyle.FONT_SLANT_UPRIGHT)
                    .build()
            }.getOrNull()
        }
    }

    private fun buildLegacyTypeface(context: Context): Typeface {
        val base = when (primary.kind) {
            FontSourceKind.BUNDLED_UBUNTU -> {
                val path = when {
                    weight.weight >= 650 -> "font/ubuntu_bold.ttf"
                    weight.weight >= 450 -> "font/ubuntu_medium.ttf"
                    else -> "font/ubuntu_regular.ttf"
                }
                legacyBundledTypeface(context, path)
            }
            FontSourceKind.BUNDLED_KAISEI_OPTI -> {
                val path = when {
                    weight.weight >= 650 -> "font/kaisei_opti_bold.ttf"
                    weight.weight >= 450 -> "font/kaisei_opti_medium.ttf"
                    else -> "font/kaisei_opti_regular.ttf"
                }
                legacyBundledTypeface(context, path)
            }
            FontSourceKind.CUSTOM_FILE -> {
                val f = File(primary.value)
                if (f.isFile) runCatching { Typeface.createFromFile(f) }.getOrNull() else null
            }
            FontSourceKind.SYSTEM_FAMILY -> {
                val f = PlatformFontManager.resolveSystemFontFile(primary.value, weight.weight)
                if (f != null) runCatching { Typeface.createFromFile(f) }.getOrNull()
                else Typeface.create(primary.value, Typeface.NORMAL)
            }
            FontSourceKind.GENERIC_SERIF -> Typeface.SERIF
            FontSourceKind.GENERIC_MONOSPACE -> Typeface.MONOSPACE
            else -> Typeface.DEFAULT
        } ?: Typeface.DEFAULT
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(base, weight.weight, false)
        } else {
            val style = if (weight.weight >= FontWeight.Bold.weight) Typeface.BOLD else Typeface.NORMAL
            Typeface.create(base, style)
        }
    }

    private fun legacyBundledTypeface(context: Context, path: String): Typeface? {
        val bytes = PlatformFontManager.getCachedBytes(path) ?: return null
        val dir = File(context.cacheDir, "bundled_fonts").apply { mkdirs() }
        val file = File(dir, path.substringAfterLast('/'))
        if (!file.exists() || file.length() != bytes.size.toLong()) {
            runCatching { file.writeBytes(bytes) }.getOrNull() ?: return null
        }
        return runCatching { Typeface.createFromFile(file) }.getOrNull()
    }
}

private object LayeredTypefaceLoader : AndroidFont.TypefaceLoader {
    private val cache = ConcurrentHashMap<String, Typeface>()

    override fun loadBlocking(context: Context, font: AndroidFont): Typeface? {
        val layered = font as? LayeredAndroidFont ?: return null
        return cache.getOrPut(layered.cacheKey) {
            layered.buildTypeface(context)
        }
    }

    override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface? =
        withContext(Dispatchers.IO) { loadBlocking(context, font) }
}

@Composable
actual fun ProvideAppTypographyFont(
    primary: FontSelection,
    fallback: FontSelection,
    content: @Composable (FontFamily?) -> Unit
) {
    val ubuntu = rememberBundledUbuntuFontFamily()
    val kaiseiOpti = rememberBundledKaiseiOptiFontFamily()

    val isSimpleSingle = fallback.kind == FontSourceKind.SYSTEM_DEFAULT &&
        primary.kind in setOf(
            FontSourceKind.SYSTEM_DEFAULT,
            FontSourceKind.BUNDLED_UBUNTU,
            FontSourceKind.BUNDLED_KAISEI_OPTI,
            FontSourceKind.GENERIC_SANS,
            FontSourceKind.GENERIC_SERIF,
            FontSourceKind.GENERIC_MONOSPACE
        )

    if (isSimpleSingle) {
        val simpleFamily = when (primary.kind) {
            FontSourceKind.SYSTEM_DEFAULT -> null
            FontSourceKind.BUNDLED_UBUNTU -> ubuntu
            FontSourceKind.BUNDLED_KAISEI_OPTI -> kaiseiOpti
            FontSourceKind.GENERIC_SANS -> FontFamily.SansSerif
            FontSourceKind.GENERIC_SERIF -> FontFamily.Serif
            FontSourceKind.GENERIC_MONOSPACE -> FontFamily.Monospace
            else -> null
        }
        content(simpleFamily)
        return
    }

    val resolvedLayered by produceState<FontFamily?>(initialValue = null, primary, fallback) {
        withContext(Dispatchers.IO) {
            PlatformFontManager.ensureBundledBytesLoaded(primary)
            PlatformFontManager.ensureBundledBytesLoaded(fallback)
        }
        value = FontFamily(
            LayeredAndroidFont(primary, fallback, FontWeight.Normal),
            LayeredAndroidFont(primary, fallback, FontWeight.Medium),
            LayeredAndroidFont(primary, fallback, FontWeight.Bold)
        )
    }

    content(resolvedLayered)
}
