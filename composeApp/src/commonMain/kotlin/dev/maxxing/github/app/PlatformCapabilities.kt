package dev.maxxing.github.app

/**
 * Platform-specific UI behaviour flags.
 *
 * The texts live in the shared i18n table (`core/ui/i18n/strings.csv`); this class only says which
 * variant to use. Every default is the Android / Desktop behaviour, so their `actual`s do not set
 * anything and their screens are unchanged. iOS overrides what it needs.
 */
data class PlatformCapabilities(
    /**
     * Use the iOS variant of the platform-dependent texts (sign-in subtitle, "Enable OS Alerts"
     * description, test-notification failure message) instead of the Android / Linux ones.
     */
    val usesIosCopy: Boolean = false,
    /** Whether the "Android Wallpaper Dynamic Color" switch is shown. iOS has no public equivalent. */
    val supportsDynamicColorToggle: Boolean = true,
    /** Whether the "Typography & Fonts" settings card is shown (custom font import / system font list). */
    val supportsCustomFonts: Boolean = true,
    /**
     * Whether the sign-in form must make room for the on-screen keyboard itself (shrink to the
     * space above it). iOS needs this; Android and Desktop keep their existing layout.
     */
    val avoidsKeyboardOnSignIn: Boolean = false,
    /**
     * Whether OS-level push/local notification alerts and their Settings controls are active.
     * Android and Desktop keep `true`; iOS ties this to [IosPushFeature.isApnsEnabled].
     */
    val supportsOsNotifications: Boolean = true,
)

expect val platformCapabilities: PlatformCapabilities
