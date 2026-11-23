package dev.maxxing.github.app.ui

import androidx.compose.runtime.Composable

@Composable
expect fun QrCodeScannerDialog(
    onCodeScanned: (String) -> Unit,
    onDismiss: () -> Unit
)
