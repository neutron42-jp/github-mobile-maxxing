package dev.maxxing.github.app

actual val platformCapabilities: PlatformCapabilities = PlatformCapabilities(
    // This build has no Desktop wallpaper-color integration (DesktopDynamicTheme always returns
    // null), so neither the wallpaper palette nor its switch is exposed.
    supportsDynamicColor = false,
    supportsDynamicColorToggle = false,
)
