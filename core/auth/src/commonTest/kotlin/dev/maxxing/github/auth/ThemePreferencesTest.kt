package dev.maxxing.github.auth

import dev.maxxing.github.model.AppLanguage
import dev.maxxing.github.model.FontSelection
import dev.maxxing.github.model.FontSourceKind
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals

class ThemePreferencesTest {
    // The settings the Android and Desktop token stores use.
    private val json = TokenStoreJson

    @Test
    fun legacyJsonWithoutFontOrLanguageFallsBackToDefaults() {
        val legacyJson = """{"seedColorHex":16734003,"useDynamicColor":false,"darkModeOverride":true}"""
        val decoded = json.decodeFromString<ThemePreferences>(legacyJson)

        assertEquals(16734003L, decoded.seedColorHex)
        assertEquals(false, decoded.useDynamicColor)
        assertEquals(true, decoded.darkModeOverride)
        assertEquals(FontSelection.SystemDefault, decoded.primaryFont)
        assertEquals(FontSelection.SystemDefault, decoded.fallbackFont)
        assertEquals(AppLanguage.DEFAULT, decoded.language)
    }

    @Test
    fun layeredFontsAndLanguageRoundTripThroughJson() {
        val systemPrimary = FontSelection(
            kind = FontSourceKind.SYSTEM_FAMILY,
            value = "Noto Sans",
            displayName = "Noto Sans"
        )
        val customFallback = FontSelection(
            kind = FontSourceKind.CUSTOM_FILE,
            value = "/tmp/custom.ttf",
            displayName = "CustomJP"
        )
        val prefs = ThemePreferences(
            primaryFont = systemPrimary,
            fallbackFont = customFallback,
            language = AppLanguage.JAPANESE
        )
        val encoded = json.encodeToString(prefs)
        val decoded = json.decodeFromString<ThemePreferences>(encoded)

        assertEquals(systemPrimary, decoded.primaryFont)
        assertEquals(customFallback, decoded.fallbackFont)
        assertEquals(AppLanguage.JAPANESE, decoded.language)
    }

    @Test
    fun unknownFontKindFallsBackToDefaultWithoutLosingOtherPreferences() {
        val newerJson = """{"seedColorHex":16734003,"useDynamicColor":false,""" +
            """"primaryFont":{"kind":"SOME_FUTURE_KIND","value":"x","displayName":"X"},""" +
            """"fallbackFont":{"kind":"SERIF_CJK_MAGIC"}}"""
        val decoded = json.decodeFromString<ThemePreferences>(newerJson)

        assertEquals(16734003L, decoded.seedColorHex)
        assertEquals(false, decoded.useDynamicColor)
        assertEquals(FontSourceKind.SYSTEM_DEFAULT, decoded.primaryFont.kind)
        assertEquals(FontSelection.SystemDefault, decoded.fallbackFont)
    }

    @Test
    fun unknownLanguageFallsBackToDefaultWithoutLosingOtherPreferences() {
        val customPrimary = FontSelection(
            kind = FontSourceKind.SYSTEM_FAMILY,
            value = "Inter",
            displayName = "Inter"
        )
        val newerJson = """{"seedColorHex":16734003,"useDynamicColor":false,"darkModeOverride":true,""" +
            """"primaryFont":{"kind":"SYSTEM_FAMILY","value":"Inter","displayName":"Inter"},""" +
            """"language":"FUTURE_LANGUAGE"}"""
        val decoded = json.decodeFromString<ThemePreferences>(newerJson)

        assertEquals(16734003L, decoded.seedColorHex)
        assertEquals(false, decoded.useDynamicColor)
        assertEquals(true, decoded.darkModeOverride)
        assertEquals(customPrimary, decoded.primaryFont)
        assertEquals(FontSelection.SystemDefault, decoded.fallbackFont)
        assertEquals(AppLanguage.DEFAULT, decoded.language)
    }
}
