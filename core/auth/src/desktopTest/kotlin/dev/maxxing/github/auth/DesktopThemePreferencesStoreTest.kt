package dev.maxxing.github.auth

import dev.maxxing.github.model.AppLanguage
import dev.maxxing.github.model.MaxxingThemeDefaults
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopThemePreferencesStoreTest {

    private fun withStore(block: suspend (LinuxDesktopTokenStore, java.io.File) -> Unit) = runTest {
        val dir = Files.createTempDirectory("maxxing-theme-store-test").toFile()
        try {
            block(LinuxDesktopTokenStore(dir), dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun graphitePreferencesRoundTripThroughThemeJson() = withStore { store, dir ->
        val prefs = ThemePreferences()
        store.saveThemePreferences(prefs)

        val encoded = dir.resolve("theme.json").readText()
        assertTrue("seedColorHex" in encoded, "seed must always be persisted: $encoded")
        assertTrue("monochromePresetSelected" in encoded, "selection flag must always be persisted: $encoded")
        assertEquals(prefs, store.loadThemePreferences())
    }

    @Test
    fun customColorPreferencesRoundTripAndKeepExpressiveSelection() = withStore { store, _ ->
        val prefs = ThemePreferences(
            seedColorHex = MaxxingThemeDefaults.GRAPHITE_SEED_COLOR_HEX,
            monochromePresetSelected = false,
            useDynamicColor = false
        )
        store.saveThemePreferences(prefs)

        val loaded = store.loadThemePreferences()
        assertEquals(prefs.seedColorHex, loaded.seedColorHex)
        assertFalse(loaded.monochromePresetSelected)
        assertFalse(loaded.useDynamicColor)
    }

    @Test
    fun legacyRecordWithoutSeedFollowsGraphiteDefaultAndKeepsOtherFields() = withStore { store, dir ->
        dir.resolve("theme.json").writeText("""{"useDynamicColor":false,"language":"JAPANESE"}""")

        val loaded = store.loadThemePreferences()
        assertEquals(MaxxingThemeDefaults.GRAPHITE_SEED_COLOR_HEX, loaded.seedColorHex)
        assertTrue(loaded.monochromePresetSelected)
        assertFalse(loaded.useDynamicColor)
        assertEquals(AppLanguage.JAPANESE, loaded.language)
    }

    @Test
    fun legacyCustomGrayRecordStaysCustomAndExpressive() = withStore { store, dir ->
        dir.resolve("theme.json").writeText(
            """{"seedColorHex":${MaxxingThemeDefaults.GRAPHITE_SEED_COLOR_HEX},"useDynamicColor":false}"""
        )

        val loaded = store.loadThemePreferences()
        assertEquals(MaxxingThemeDefaults.GRAPHITE_SEED_COLOR_HEX, loaded.seedColorHex)
        assertFalse(loaded.monochromePresetSelected)
        assertFalse(loaded.useDynamicColor)
    }

    @Test
    fun missingOrCorruptThemeJsonFallsBackToTheNewDefault() = withStore { store, dir ->
        assertEquals(ThemePreferences(), store.loadThemePreferences())

        dir.resolve("theme.json").writeText("{bad json")
        assertEquals(ThemePreferences(), store.loadThemePreferences())
    }

    @Test
    fun unreadableThemeFileFallsBackToTheNewDefault() = withStore { store, dir ->
        Files.createDirectory(dir.resolve("theme.json").toPath())

        assertEquals(ThemePreferences(), store.loadThemePreferences())
    }
}
