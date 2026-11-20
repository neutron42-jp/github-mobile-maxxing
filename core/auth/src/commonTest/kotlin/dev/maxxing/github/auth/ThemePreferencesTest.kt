package dev.maxxing.github.auth

import dev.maxxing.github.model.AppLanguage
import dev.maxxing.github.model.FontSelection
import dev.maxxing.github.model.FontSourceKind
import dev.maxxing.github.model.MaxxingThemeDefaults
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemePreferencesTest {
    // The settings the Android and Desktop token stores use.
    private val json = TokenStoreJson

    @Test
    fun newInstallDefaultsToGraphiteWithItsExplicitSelectionFlag() {
        val prefs = ThemePreferences()

        assertEquals(MaxxingThemeDefaults.GRAPHITE_SEED_COLOR_HEX, prefs.seedColorHex)
        assertTrue(prefs.monochromePresetSelected)
        assertTrue(prefs.useDynamicColor)
    }

    @Test
    fun newRecordsAlwaysPersistSeedAndSelectionFlag() {
        // encodeDefaults is off in TokenStoreJson; @EncodeDefault(ALWAYS) must override it so a
        // later load can tell this record apart from a legacy record that omitted the seed.
        val encoded = json.encodeToString(ThemePreferences())
        val obj = json.parseToJsonElement(encoded).jsonObject
        assertEquals(
            MaxxingThemeDefaults.GRAPHITE_SEED_COLOR_HEX.toString(),
            (obj["seedColorHex"] as JsonPrimitive).content
        )
        assertEquals("true", (obj["monochromePresetSelected"] as JsonPrimitive).content)

        val customGray = ThemePreferences(
            seedColorHex = MaxxingThemeDefaults.GRAPHITE_SEED_COLOR_HEX,
            monochromePresetSelected = false
        )
        val customObj = json.parseToJsonElement(json.encodeToString(customGray)).jsonObject
        assertEquals("false", (customObj["monochromePresetSelected"] as JsonPrimitive).content)
    }

    @Test
    fun legacyRecordWithOmittedSeedFollowsGraphiteDefaultAndKeepsOtherFields() {
        val legacyJson = """{"useDynamicColor":false,"darkModeOverride":true,"language":"JAPANESE"}"""
        val decoded = decodeThemePreferences(legacyJson)

        assertEquals(MaxxingThemeDefaults.GRAPHITE_SEED_COLOR_HEX, decoded.seedColorHex)
        assertTrue(decoded.monochromePresetSelected)
        assertFalse(decoded.useDynamicColor)
        assertEquals(true, decoded.darkModeOverride)
        assertEquals(AppLanguage.JAPANESE, decoded.language)
    }

    @Test
    fun legacyEmptyObjectFollowsGraphiteDefault() {
        assertEquals(ThemePreferences(), decodeThemePreferences("{}"))
    }

    @Test
    fun legacyExplicitIrisSeedIsPreservedAndStaysExpressive() {
        val decoded = decodeThemePreferences(
            """{"seedColorHex":${MaxxingThemeDefaults.IRIS_SEED_COLOR_HEX},"useDynamicColor":false}"""
        )

        assertEquals(MaxxingThemeDefaults.IRIS_SEED_COLOR_HEX, decoded.seedColorHex)
        assertFalse(decoded.monochromePresetSelected)
        assertFalse(decoded.useDynamicColor)
    }

    @Test
    fun legacyExplicitPresetSeedIsPreservedAndStaysExpressive() {
        // Teal from the pre-Graphite preset list.
        val decoded = decodeThemePreferences("""{"seedColorHex":${0xFF2F7D7A}}""")

        assertEquals(0xFF2F7D7A, decoded.seedColorHex)
        assertFalse(decoded.monochromePresetSelected)
    }

    @Test
    fun legacyCustomColorThatEqualsGraphiteStaysCustomAndExpressive() {
        val decoded = decodeThemePreferences(
            """{"seedColorHex":${MaxxingThemeDefaults.GRAPHITE_SEED_COLOR_HEX},"useDynamicColor":false}"""
        )

        assertEquals(MaxxingThemeDefaults.GRAPHITE_SEED_COLOR_HEX, decoded.seedColorHex)
        assertFalse(decoded.monochromePresetSelected)
        assertFalse(decoded.useDynamicColor)
    }

    @Test
    fun newGraphiteRecordRoundTripsTheExplicitSelection() {
        val prefs = ThemePreferences(
            seedColorHex = MaxxingThemeDefaults.GRAPHITE_SEED_COLOR_HEX,
            monochromePresetSelected = true
        )
        val decoded = decodeThemePreferences(json.encodeToString(prefs))

        assertEquals(prefs.seedColorHex, decoded.seedColorHex)
        assertTrue(decoded.monochromePresetSelected)
    }

    @Test
    fun newCustomGrayRecordRoundTripsWithoutBecomingGraphite() {
        val prefs = ThemePreferences(
            seedColorHex = MaxxingThemeDefaults.GRAPHITE_SEED_COLOR_HEX,
            monochromePresetSelected = false,
            useDynamicColor = false
        )
        val decoded = decodeThemePreferences(json.encodeToString(prefs))

        assertEquals(prefs.seedColorHex, decoded.seedColorHex)
        assertFalse(decoded.monochromePresetSelected)
        assertFalse(decoded.useDynamicColor)
    }

    @Test
    fun legacyJsonWithoutFontOrLanguageFallsBackToDefaults() {
        val legacyJson = """{"seedColorHex":16734003,"useDynamicColor":false,"darkModeOverride":true}"""
        val decoded = decodeThemePreferences(legacyJson)

        assertEquals(16734003L, decoded.seedColorHex)
        assertFalse(decoded.monochromePresetSelected)
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
        val decoded = decodeThemePreferences(encoded)

        assertEquals(systemPrimary, decoded.primaryFont)
        assertEquals(customFallback, decoded.fallbackFont)
        assertEquals(AppLanguage.JAPANESE, decoded.language)
    }

    @Test
    fun unknownFontKindFallsBackToDefaultWithoutLosingOtherPreferences() {
        val newerJson = """{"seedColorHex":16734003,"useDynamicColor":false,""" +
            """"primaryFont":{"kind":"SOME_FUTURE_KIND","value":"x","displayName":"X"},""" +
            """"fallbackFont":{"kind":"SERIF_CJK_MAGIC"}}"""
        val decoded = decodeThemePreferences(newerJson)

        assertEquals(16734003L, decoded.seedColorHex)
        assertFalse(decoded.monochromePresetSelected)
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
        val decoded = decodeThemePreferences(newerJson)

        assertEquals(16734003L, decoded.seedColorHex)
        assertFalse(decoded.monochromePresetSelected)
        assertEquals(false, decoded.useDynamicColor)
        assertEquals(true, decoded.darkModeOverride)
        assertEquals(customPrimary, decoded.primaryFont)
        assertEquals(FontSelection.SystemDefault, decoded.fallbackFont)
        assertEquals(AppLanguage.DEFAULT, decoded.language)
    }

    @Test
    fun corruptOrMissingValuesFallBackToTheNewDefault() {
        assertEquals(ThemePreferences(), decodeThemePreferences("{bad json"))
        assertEquals(ThemePreferences(), decodeThemePreferences("not json"))
        assertEquals(ThemePreferences(), decodeThemePreferences("[]"))
        assertEquals(ThemePreferences(), decodeThemePreferences("null"))
    }

    @Test
    fun newerRecordWithSelectionFlagKeepsExplicitCustomSelection() {
        val newerJson = """{"seedColorHex":${MaxxingThemeDefaults.GRAPHITE_SEED_COLOR_HEX},""" +
            """"monochromePresetSelected":false,"useDynamicColor":false}"""

        val decoded = decodeThemePreferences(newerJson)
        assertEquals(MaxxingThemeDefaults.GRAPHITE_SEED_COLOR_HEX, decoded.seedColorHex)
        assertFalse(decoded.monochromePresetSelected)
    }
}
