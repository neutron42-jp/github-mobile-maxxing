package dev.maxxing.github.app

import android.os.Build

/**
 * Android applies the sign-in IME inset (S34).
 *
 * `MainActivity` enables edge-to-edge, so `adjustResize` alone does not shrink the window and
 * `WindowInsets.ime` must be applied by Compose. Enabling the same capability as iOS reuses the
 * existing `imePadding()` + bring-into-view path, keeping the custom Client ID field, its
 * supporting text and the reset control scrollable above the keyboard.
 *
 * S24's system bars, S61's wordmark layout and S70's build-defined Client ID default stay as-is.
 */
actual val platformCapabilities: PlatformCapabilities = PlatformCapabilities(
    // Material You wallpaper colors are available from Android 12 (API 31).
    supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    supportsDynamicColorToggle = true,
    avoidsKeyboardOnSignIn = true,
)
