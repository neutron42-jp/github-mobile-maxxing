package dev.maxxing.github.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import dev.maxxing.github.app.notifications.PlatformNotifier
import dev.maxxing.github.auth.AndroidAuthContextHolder

class MainActivity : ComponentActivity() {
    private var deepLinkUrl by mutableStateOf<String?>(null)

    // Monotonic nonce paired with [deepLinkUrl]: re-delivering the same URL via
    // onNewIntent changes the nonce, so the consumer's LaunchedEffect re-fires.
    private var deepLinkNonce by mutableStateOf(0L)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission result handled gracefully by PlatformNotifier */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val initialBarStyle = if (systemDark) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
            SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        }
        enableEdgeToEdge(
            statusBarStyle = initialBarStyle,
            navigationBarStyle = initialBarStyle
        )
        super.onCreate(savedInstanceState)
        enableMaxRefreshRate()
        AndroidAuthContextHolder.init(this)
        PlatformNotifier.ensureInitialized()
        requestNotificationPermissionIfNeeded()
        setDeepLink(extractDeepLinkUrl(intent))

        setContent {
            var isDarkTheme by mutableStateOf(systemDark)
            val view = LocalView.current

            SideEffect {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !isDarkTheme
                controller.isAppearanceLightNavigationBars = !isDarkTheme
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isStatusBarContrastEnforced = false
                    window.isNavigationBarContrastEnforced = false
                }
            }

            LaunchedEffect(isDarkTheme) {
                val barStyle = if (isDarkTheme) {
                    SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                }
                enableEdgeToEdge(
                    statusBarStyle = barStyle,
                    navigationBarStyle = barStyle
                )
            }
            GitHubMaxxingApp(
                deepLinkUrl = deepLinkUrl,
                deepLinkNonce = deepLinkNonce,
                onConsumeDeepLink = { deepLinkUrl = null },
                onDarkThemeChanged = { isDarkTheme = it }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        setDeepLink(extractDeepLinkUrl(intent))
    }

    private fun setDeepLink(url: String?) {
        if (url == null) return
        deepLinkUrl = url
        deepLinkNonce += 1L
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun enableMaxRefreshRate() {
        runCatching {
            val dm = getSystemService(DISPLAY_SERVICE) as? DisplayManager ?: return
            val targetDisplay = dm.getDisplay(Display.DEFAULT_DISPLAY) ?: return
            val maxMode = targetDisplay.supportedModes.maxByOrNull { it.refreshRate } ?: return
            val params = window.attributes
            if (params.preferredDisplayModeId != maxMode.modeId) {
                params.preferredDisplayModeId = maxMode.modeId
                window.attributes = params
            }
        }
    }

    private fun extractDeepLinkUrl(intent: Intent?): String? {
        return intent?.dataString?.takeIf { it.isNotBlank() }
    }
}
