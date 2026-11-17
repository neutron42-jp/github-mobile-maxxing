package dev.maxxing.github.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ShieldCheck
import dev.maxxing.github.app.resources.Res
import dev.maxxing.github.app.resources.app_icon
import dev.maxxing.github.auth.SessionManager
import dev.maxxing.github.auth.openUrlInBrowser
import dev.maxxing.github.model.AppBranding
import dev.maxxing.github.model.DeviceCodeState
import dev.maxxing.github.ui.components.MaxxingLoadingState
import dev.maxxing.github.ui.components.MaxxingWavyProgress
import dev.maxxing.github.ui.components.expressiveAnimateContentSize
import dev.maxxing.github.ui.i18n.appStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SignInScreen(
    sessionManager: SessionManager,
    onSignedIn: () -> Unit
) {
    val savedClientId by sessionManager.clientId.collectAsState()
    val effectiveDefaultId = savedClientId.ifBlank { AppBranding.DEFAULT_OAUTH_CLIENT_ID }
    var clientIdInput by remember(effectiveDefaultId) { mutableStateOf(effectiveDefaultId) }
    var showCustomClientId by remember { mutableStateOf(false) }
    var deviceState by remember { mutableStateOf<DeviceCodeState>(DeviceCodeState.Idle) }
    val strings = appStrings()

    val scope = rememberCoroutineScope()
    var pollingJob by remember { mutableStateOf<Job?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            ElevatedCard(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .expressiveAnimateContentSize()
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(Res.drawable.app_icon),
                        contentDescription = AppBranding.APP_NAME,
                        modifier = Modifier
                            .size(68.dp)
                            .clip(MaterialTheme.shapes.large)
                    )

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = AppBranding.APP_NAME,
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = strings.signInSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(24.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when (val state = deviceState) {
                            is DeviceCodeState.Idle,
                            is DeviceCodeState.Error -> {
                                if (state is DeviceCodeState.Error) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        shape = MaterialTheme.shapes.medium,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = strings.deviceFlowErrorText(state),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                    Spacer(Modifier.height(14.dp))
                                }

                                Button(
                                    onClick = {
                                        val activeId = clientIdInput.trim().ifBlank { AppBranding.DEFAULT_OAUTH_CLIENT_ID }
                                        pollingJob?.cancel()
                                        pollingJob = scope.launch {
                                            sessionManager.startDeviceFlow(activeId)
                                                .collect { next ->
                                                    deviceState = next
                                                    if (next is DeviceCodeState.Authenticated) {
                                                        onSignedIn()
                                                    }
                                                }
                                        }
                                    },
                                    enabled = clientIdInput.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth().height(52.dp)
                                ) {
                                    Text(strings.signInWithGitHubButton)
                                }

                                Spacer(Modifier.height(10.dp))

                                TextButton(onClick = { showCustomClientId = !showCustomClientId }) {
                                    Text(if (showCustomClientId) strings.signInHideCustomClientId else strings.signInUseCustomClientId)
                                }

                                AnimatedVisibility(visible = showCustomClientId) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = clientIdInput,
                                            onValueChange = { clientIdInput = it },
                                            label = { Text(strings.signInClientIdLabel) },
                                            placeholder = { Text(AppBranding.DEFAULT_OAUTH_CLIENT_ID) },
                                            singleLine = true,
                                            leadingIcon = {
                                                Icon(Lucide.ShieldCheck, contentDescription = null)
                                            },
                                            supportingText = {
                                                Text(strings.signInClientIdDefaultSupporting(AppBranding.DEFAULT_OAUTH_CLIENT_ID))
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        if (clientIdInput != AppBranding.DEFAULT_OAUTH_CLIENT_ID) {
                                            TextButton(
                                                onClick = { clientIdInput = AppBranding.DEFAULT_OAUTH_CLIENT_ID },
                                                modifier = Modifier.align(Alignment.End)
                                            ) {
                                                Text(strings.signInResetClientId)
                                            }
                                        }
                                    }
                                }
                            }

                            is DeviceCodeState.RequestingCode -> {
                                MaxxingLoadingState(message = strings.signInRequestingCode)
                            }

                            is DeviceCodeState.AwaitingUser -> {
                                Text(
                                    text = strings.signInEnterCode,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(Modifier.height(12.dp))

                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = MaterialTheme.shapes.large,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    SelectionContainer {
                                        Text(
                                            text = state.userCode,
                                            style = MaterialTheme.typography.headlineMedium.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.ExtraBold,
                                                letterSpacing = 3.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 18.dp, horizontal = 12.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(10.dp))

                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        SelectionContainer(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = state.verificationUri,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = FontFamily.Monospace
                                                ),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                runCatching {
                                                    clipboard.setText(AnnotatedString(state.verificationUri))
                                                }
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(strings.signInCopiedValue(state.verificationUri))
                                                }
                                            },
                                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                                        ) {
                                            Icon(
                                                Lucide.Copy,
                                                contentDescription = strings.copyUrl,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(14.dp))
                                MaxxingWavyProgress()
                                Spacer(Modifier.height(10.dp))

                                Text(
                                    text = strings.deviceFlowWaitingText(state),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = {
                                            runCatching {
                                                clipboard.setText(AnnotatedString(state.userCode))
                                            }
                                            scope.launch {
                                                snackbarHostState.showSnackbar(strings.signInCopiedValue(state.userCode))
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .pointerHoverIcon(PointerIcon.Hand)
                                    ) {
                                        Icon(Lucide.Copy, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(strings.signInCopyCode, maxLines = 1, softWrap = false)
                                    }

                                    Button(
                                        onClick = {
                                            scope.launch {
                                                runCatching {
                                                    clipboard.setText(AnnotatedString(state.userCode))
                                                }
                                                val openedByPlatform = withContext(Dispatchers.IO) {
                                                    openUrlInBrowser(state.verificationUri)
                                                }
                                                val opened = if (openedByPlatform) {
                                                    true
                                                } else {
                                                    runCatching {
                                                        uriHandler.openUri(state.verificationUri)
                                                        true
                                                    }.getOrDefault(false)
                                                }
                                                snackbarHostState.showSnackbar(
                                                    if (opened) {
                                                        strings.signInCodeCopiedOpening(state.userCode)
                                                    } else {
                                                        strings.signInCodeCopiedManual(state.userCode, state.verificationUri)
                                                    }
                                                )
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .pointerHoverIcon(PointerIcon.Hand)
                                    ) {
                                        Icon(Lucide.ExternalLink, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(strings.signInOpenGitHub, maxLines = 1, softWrap = false)
                                    }
                                }

                                Spacer(Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = {
                                        pollingJob?.cancel()
                                        deviceState = DeviceCodeState.Idle
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    Text(strings.cancel)
                                }
                            }

                            is DeviceCodeState.Verifying -> {
                                MaxxingLoadingState(message = strings.deviceFlowVerifyingText(state))
                            }

                            is DeviceCodeState.Authenticated -> {
                                MaxxingLoadingState(message = strings.signInWelcome(state.session.userLogin ?: "developer"))
                            }
                        }
                    }
                }
            }
        }
    }
}
