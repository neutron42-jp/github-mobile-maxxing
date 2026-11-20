package dev.maxxing.github.app

import dev.maxxing.github.app.notifications.IosPushFeature

actual val platformCapabilities: PlatformCapabilities = PlatformCapabilities(
    usesIosCopy = true,
    supportsDynamicColor = false,
    supportsDynamicColorToggle = false,
    supportsCustomFonts = false,
    avoidsKeyboardOnSignIn = true,
    supportsOsNotifications = IosPushFeature.isApnsEnabled,
    supportsInAppUpdates = false,
)
