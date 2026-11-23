package dev.maxxing.github.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import dev.maxxing.github.ui.i18n.appStrings

@Composable
actual fun QrCodeScannerDialog(
    onCodeScanned: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = appStrings()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.localScanQrAction) },
        text = { Text(strings.localQrUnavailableDesktop) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.localCloseScanner)
            }
        }
    )
}
