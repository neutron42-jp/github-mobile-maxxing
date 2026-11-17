package dev.maxxing.github.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.maxxing.github.app.resources.Res
import dev.maxxing.github.app.resources.app_icon
import dev.maxxing.github.model.AppBranding
import dev.maxxing.github.model.AppLanguage
import dev.maxxing.github.ui.i18n.stringsFor
import org.jetbrains.compose.resources.painterResource
import java.awt.Dimension

fun main() = application {
    val windowState = rememberWindowState(width = 1280.dp, height = 840.dp)
    var language by remember { mutableStateOf(AppLanguage.DEFAULT) }
    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = stringsFor(language).windowTitle(AppBranding.APP_NAME),
        icon = painterResource(Res.drawable.app_icon)
    ) {
        window.minimumSize = Dimension(420, 600)
        GitHubMaxxingApp(onLanguageChanged = { language = it })
    }
}
