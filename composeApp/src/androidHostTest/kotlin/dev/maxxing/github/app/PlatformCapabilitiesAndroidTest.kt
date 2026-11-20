package dev.maxxing.github.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Locks the S34 contract: the Android sign-in screen must apply the IME inset itself because
 * the activity is edge-to-edge, while the other platform flags keep the shared defaults.
 */
class PlatformCapabilitiesAndroidTest {
    @Test
    fun androidAvoidsTheKeyboardOnSignInAndKeepsTheOtherDefaults() {
        assertFalse(platformCapabilities.usesIosCopy)
        assertTrue(platformCapabilities.supportsDynamicColorToggle)
        assertTrue(platformCapabilities.supportsCustomFonts)
        assertTrue(platformCapabilities.avoidsKeyboardOnSignIn)
        assertTrue(platformCapabilities.supportsOsNotifications)
        assertTrue(platformCapabilities.supportsInAppUpdates)
    }
}
