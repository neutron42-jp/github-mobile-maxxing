package dev.maxxing.github.model

import kotlinx.serialization.Serializable

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

/** Supported UI display languages. */
@Serializable
enum class AppLanguage {
    DEFAULT,
    JAPANESE
}
