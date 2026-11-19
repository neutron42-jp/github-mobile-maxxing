package dev.maxxing.github.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/**
 * iOS has no public API to read wallpaper-derived colors, so this returns
 * `null` and `MaxxingTheme` falls back to the MaterialKolor seed presets
 * (same behavior as Linux Desktop).
 */
@Composable
actual fun platformDynamicColorScheme(darkTheme: Boolean): ColorScheme? = null
