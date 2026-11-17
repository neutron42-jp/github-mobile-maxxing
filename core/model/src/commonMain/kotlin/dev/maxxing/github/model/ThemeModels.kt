package dev.maxxing.github.model

import kotlinx.serialization.Serializable

@Serializable
enum class FontSourceKind {
    SYSTEM_DEFAULT,
    BUNDLED_UBUNTU,
    BUNDLED_KAISEI_OPTI,
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
    fun displayLabel(): String = when (kind) {
        FontSourceKind.SYSTEM_DEFAULT -> "System Default"
        FontSourceKind.BUNDLED_UBUNTU -> "Ubuntu"
        FontSourceKind.BUNDLED_KAISEI_OPTI -> "Kaisei Opti"
        FontSourceKind.GENERIC_SANS -> "Sans-serif"
        FontSourceKind.GENERIC_SERIF -> "Serif"
        FontSourceKind.GENERIC_MONOSPACE -> "Monospace"
        FontSourceKind.SYSTEM_FAMILY -> displayName.ifBlank { value }.ifBlank { "System Default" }
        FontSourceKind.CUSTOM_FILE -> displayName.ifBlank { value.substringAfterLast('/') }
    }

    companion object {
        val SystemDefault = FontSelection(FontSourceKind.SYSTEM_DEFAULT)
        val BundledUbuntu = FontSelection(FontSourceKind.BUNDLED_UBUNTU, displayName = "Ubuntu")
        val BundledKaiseiOpti = FontSelection(FontSourceKind.BUNDLED_KAISEI_OPTI, displayName = "Kaisei Opti")
        val SansSerif = FontSelection(FontSourceKind.GENERIC_SANS, displayName = "Sans-serif")
        val Serif = FontSelection(FontSourceKind.GENERIC_SERIF, displayName = "Serif")
        val Monospace = FontSelection(FontSourceKind.GENERIC_MONOSPACE, displayName = "Monospace")
    }
}

enum class FontPreset(
    val label: String,
    val primary: FontSelection,
    val fallback: FontSelection
) {
    SYSTEM_DEFAULT("System Default", FontSelection.SystemDefault, FontSelection.SystemDefault),
    UBUNTU_OVER_KAISEI_OPTI("Ubuntu + Kaisei Opti", FontSelection.BundledUbuntu, FontSelection.BundledKaiseiOpti),
    UBUNTU("Ubuntu", FontSelection.BundledUbuntu, FontSelection.SystemDefault),
    KAISEI_OPTI("Kaisei Opti", FontSelection.BundledKaiseiOpti, FontSelection.SystemDefault);

    companion object {
        fun match(primary: FontSelection, fallback: FontSelection): FontPreset? =
            entries.firstOrNull { it.primary == primary && it.fallback == fallback }

        fun labelFor(preset: FontPreset?): String = preset?.label ?: "Custom"
    }
}

data class CustomFontFile(
    val id: String,
    val displayName: String,
    val filePath: String
)
