package dev.maxxing.github.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Guards the "zero behaviour change on Desktop" requirement: everything stays at its default. */
class PlatformCapabilitiesDesktopTest {
    @Test
    fun desktopKeepsTheExistingBehaviour() {
        assertFalse(platformCapabilities.usesIosCopy)
        assertTrue(platformCapabilities.supportsDynamicColorToggle)
        assertTrue(platformCapabilities.supportsCustomFonts)
        assertFalse(platformCapabilities.avoidsKeyboardOnSignIn)
        assertTrue(platformCapabilities.supportsOsNotifications)
        assertTrue(platformCapabilities.supportsInAppUpdates)
    }
}
