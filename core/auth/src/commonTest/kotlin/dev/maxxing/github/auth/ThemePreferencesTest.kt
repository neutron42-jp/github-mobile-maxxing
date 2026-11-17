package dev.maxxing.github.auth

import dev.maxxing.github.model.FontPreset
import dev.maxxing.github.model.FontSelection
import dev.maxxing.github.model.FontSourceKind
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ThemePreferencesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun legacyJsonWithoutFontFallsBackToDefaults() {
        val legacyJson = """{"seedColorHex":16734003,"useDynamicColor":false,"darkModeOverride":true}"""
        val decoded = json.decodeFromString<ThemePreferences>(legacyJson)

        assertEquals(16734003L, decoded.seedColorHex)
        assertEquals(false, decoded.useDynamicColor)
        assertEquals(true, decoded.darkModeOverride)
        assertEquals(FontSelection.SystemDefault, decoded.primaryFont)
        assertEquals(FontSelection.SystemDefault, decoded.fallbackFont)
    }

    @Test
    fun layeredFontsRoundTripThroughJson() {
        val customFallback = FontSelection(
            kind = FontSourceKind.CUSTOM_FILE,
            value = "/tmp/custom.ttf",
            displayName = "CustomJP"
        )
        val prefs = ThemePreferences(
            primaryFont = FontSelection.BundledUbuntu,
            fallbackFont = customFallback
        )
        val encoded = json.encodeToString(prefs)
        val decoded = json.decodeFromString<ThemePreferences>(encoded)

        assertEquals(FontSelection.BundledUbuntu, decoded.primaryFont)
        assertEquals(customFallback, decoded.fallbackFont)
    }

    @Test
    fun fontPresetMatchesUbuntuOverKaiseiOpti() {
        assertEquals(
            FontPreset.UBUNTU_OVER_KAISEI_OPTI,
            FontPreset.match(FontSelection.BundledUbuntu, FontSelection.BundledKaiseiOpti)
        )
        assertEquals(
            null,
            FontPreset.match(FontSelection.SansSerif, FontSelection.BundledKaiseiOpti)
        )
    }
}
