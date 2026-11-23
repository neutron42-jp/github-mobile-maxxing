package dev.maxxing.github.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * The Json settings the platform token stores read and write saved data with.
 *
 * coerceInputValues: a value this version does not know (such as a font kind added by a newer
 * version) falls back to the field default instead of resetting all saved preferences.
 */
internal val TokenStoreJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

internal const val KEY_THEME_SEED_COLOR_HEX = "seedColorHex"
internal const val KEY_THEME_MONOCHROME_PRESET_SELECTED = "monochromePresetSelected"

/**
 * Turns an arbitrary account ID (for example `forgejo:codeberg.org:42`) into a safe,
 * collision-resistant filename / Keychain account key.
 */
internal fun sanitizeAccountStorageKey(accountId: String): String {
    val sb = StringBuilder(accountId.length + 12)
    for (ch in accountId) {
        if (ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '_' || ch == '-') {
            sb.append(ch)
        } else {
            sb.append('_')
            sb.append(ch.code.toString(16))
        }
    }
    return sb.toString().ifEmpty { "default" }
}

/**
 * Decodes a saved [ThemePreferences] value from any platform store.
 *
 * This version always writes both [KEY_THEME_SEED_COLOR_HEX] and
 * [KEY_THEME_MONOCHROME_PRESET_SELECTED] (see the `@EncodeDefault` annotations), so a record
 * without the selection flag predates Graphite:
 *
 * - A legacy record with an explicit seed keeps that seed and is Expressive. This includes a
 *   custom color that happens to equal Graphite's gray.
 * - A legacy record with no seed (including `{}`) follows the new Graphite default while retaining
 *   every other field.
 * - A missing or corrupt record falls back to [ThemePreferences].
 *
 * A record that does carry the flag is decoded as written, including a custom Graphite-gray color
 * saved with the flag false.
 */
internal fun decodeThemePreferences(raw: String): ThemePreferences {
    val element = runCatching { TokenStoreJson.parseToJsonElement(raw) }.getOrNull()
        ?: return ThemePreferences()
    val obj = element as? JsonObject ?: return ThemePreferences()

    val normalized = when {
        KEY_THEME_MONOCHROME_PRESET_SELECTED in obj -> obj
        KEY_THEME_SEED_COLOR_HEX in obj -> JsonObject(
            obj + (KEY_THEME_MONOCHROME_PRESET_SELECTED to JsonPrimitive(false))
        )
        else -> obj
    }
    return runCatching { TokenStoreJson.decodeFromJsonElement<ThemePreferences>(normalized) }
        .getOrDefault(ThemePreferences())
}
