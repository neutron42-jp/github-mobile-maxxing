package dev.maxxing.github.app

/**
 * Platform-specific UI behaviour flags.
 *
 * The texts live in the shared i18n table (`core/ui/i18n/strings.csv`); this class only says which
 * variant to use. Each platform actual overrides the shared defaults where needed.
 */
data class PlatformCapabilities(
    /**
     * Use the iOS variant of the platform-dependent texts (sign-in subtitle, "Enable OS Alerts"
     * description, test-notification failure message) instead of the Android / Linux ones.
     */
    val usesIosCopy: Boolean = false,
    /**
     * Whether wallpaper-derived colors can actually be applied. This is separate from
     * [supportsDynamicColorToggle]: the summary must say "Dynamic Color" only when the platform
     * really uses the wallpaper, while the switch is shown wherever the choice is meaningful.
     */
    val supportsDynamicColor: Boolean = false,
    /**
     * Whether the "Android Wallpaper Dynamic Color" switch is shown. Android shows it; iOS and
     * Desktop have no wallpaper-color integration in this app, so they hide it.
     */
    val supportsDynamicColorToggle: Boolean = true,
    /** Whether the "Typography & Fonts" settings card is shown (custom font import / system font list). */
    val supportsCustomFonts: Boolean = true,
    /**
     * Whether the sign-in form must make room for the on-screen keyboard itself (shrink to the
     * space above it). iOS and Android need this; Desktop keeps its existing layout.
     */
    val avoidsKeyboardOnSignIn: Boolean = false,
    /**
     * Whether OS-level push/local notification alerts and their Settings controls are active.
     * Android and Desktop keep `true`; iOS ties this to [IosPushFeature.isApnsEnabled].
     */
    val supportsOsNotifications: Boolean = true,
    /**
     * Whether the GitHub Releases in-app update checker and App Updates settings card are active.
     * Android and Desktop keep `true`; iOS uses `false` (distributed via TestFlight / App Store).
     */
    val supportsInAppUpdates: Boolean = true,
)

expect val platformCapabilities: PlatformCapabilities
