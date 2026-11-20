package dev.maxxing.github.model

import kotlinx.serialization.Serializable

/**
 * Theme seed values shared by persistence (`core/auth`) and color generation (`core/ui`).
 *
 * [DEFAULT_SEED_COLOR_HEX] is the seed used when no preferences have been saved yet. It is also
 * the Graphite preset's seed, which is generated with the MaterialKolor Monochrome style.
 */
object MaxxingThemeDefaults {
    /** Opaque neutral middle gray seed behind the Graphite preset. */
    const val GRAPHITE_SEED_COLOR_HEX: Long = 0xFF606060L

    /** The pre-Graphite default seed (Iris). Kept for reading legacy records. */
    const val IRIS_SEED_COLOR_HEX: Long = 0xFF6750A4L

    /** Default seed for a new install and for legacy records that omitted a seed. */
    const val DEFAULT_SEED_COLOR_HEX: Long = GRAPHITE_SEED_COLOR_HEX
}

@Serializable
enum class FontSourceKind {
    SYSTEM_DEFAULT,
    GENERIC_SANS,
    GENERIC_SERIF,
    GENERIC_MONOSPACE,
    SYSTEM_FAMILY,
    CUSTOM_FILE
}

@Serializable
data class FontSelection(
    val kind: FontSourceKind = FontSourceKind.SYSTEM_DEFAULT,
    val value: String = "",
    val displayName: String = ""
) {
    companion object {
        val SystemDefault = FontSelection(FontSourceKind.SYSTEM_DEFAULT)
        val SansSerif = FontSelection(FontSourceKind.GENERIC_SANS, displayName = "Sans-serif")
        val Serif = FontSelection(FontSourceKind.GENERIC_SERIF, displayName = "Serif")
        val Monospace = FontSelection(FontSourceKind.GENERIC_MONOSPACE, displayName = "Monospace")
    }
}

data class CustomFontFile(
    val id: String,
    val displayName: String,
    val filePath: String
)

/**
 * Supported UI display languages. [code] names the column of core/ui/i18n/strings.csv that holds the texts;
 * the entry name is what the settings store, so keep it when renaming.
 */
@Serializable
enum class AppLanguage(val code: String) {
    DEFAULT("en"),
    JAPANESE("ja")
}
