package dev.maxxing.github.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformCapabilitiesIosTest {
    @Test
    fun iosUsesItsOwnCopyHidesDynamicColorToggleAndAvoidsTheKeyboard() {
        assertTrue(platformCapabilities.usesIosCopy)
        assertFalse(platformCapabilities.supportsDynamicColor)
        assertFalse(platformCapabilities.supportsDynamicColorToggle)
        assertFalse(platformCapabilities.supportsCustomFonts)
        assertTrue(platformCapabilities.avoidsKeyboardOnSignIn)
        assertFalse(platformCapabilities.supportsOsNotifications)
        assertFalse(platformCapabilities.supportsInAppUpdates)
    }
}
