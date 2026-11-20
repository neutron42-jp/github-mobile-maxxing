package dev.maxxing.github.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformCapabilitiesDesktopTest {
    @Test
    fun desktopHidesWallpaperControlsAndKeepsOtherCapabilities() {
        assertFalse(platformCapabilities.usesIosCopy)
        assertFalse(platformCapabilities.supportsDynamicColor)
        assertFalse(platformCapabilities.supportsDynamicColorToggle)
        assertTrue(platformCapabilities.supportsCustomFonts)
        assertFalse(platformCapabilities.avoidsKeyboardOnSignIn)
        assertTrue(platformCapabilities.supportsOsNotifications)
        assertTrue(platformCapabilities.supportsInAppUpdates)
    }
}
