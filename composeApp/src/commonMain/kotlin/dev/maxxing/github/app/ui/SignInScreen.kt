package dev.maxxing.github.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.HardDrive
import com.composables.icons.lucide.Lucide
import dev.maxxing.github.app.resources.Res
import dev.maxxing.github.app.resources.app_icon
import dev.maxxing.github.app.platformCapabilities
import dev.maxxing.github.auth.SessionManager
import dev.maxxing.github.model.AppBranding
import dev.maxxing.github.model.ProviderType
import dev.maxxing.github.ui.components.MaxxingConnectedButtonGroup
import dev.maxxing.github.ui.components.expressiveAnimateContentSize
import dev.maxxing.github.ui.i18n.appStrings
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SignInScreen(
    sessionManager: SessionManager,
    onSignedIn: () -> Unit
) {
    var selectedProvider by remember { mutableStateOf(ProviderType.GITHUB) }
    val strings = appStrings()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // Only where the platform asks for it (iOS and Android), shrink the form to the
                // space above the keyboard so input fields stay reachable.
                // Desktop keeps its existing layout.
                .then(if (platformCapabilities.avoidsKeyboardOnSignIn) Modifier.imePadding() else Modifier)
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

                    SignInWordmark()
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (platformCapabilities.usesIosCopy) strings.signInSubtitleIos else strings.signInSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(20.dp))

                    MaxxingConnectedButtonGroup(
                        options = ProviderType.entries,
                        selected = selectedProvider,
                        onSelect = { selectedProvider = it },
                        labelProvider = { type ->
                            when (type) {
                                ProviderType.GITHUB -> strings.providerGitHub
                                ProviderType.FORGEJO_GITEA -> strings.providerOthers
                                ProviderType.LOCAL -> strings.providerLocal
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(18.dp))

                    when (selectedProvider) {
                        ProviderType.GITHUB -> GitHubDeviceFlowAuthBlock(
                            sessionManager = sessionManager,
                            onAuthenticated = { onSignedIn() },
                            onMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                        )

                        ProviderType.FORGEJO_GITEA -> ForgejoPatAuthBlock(
                            sessionManager = sessionManager,
                            onAuthenticated = { onSignedIn() },
                            onMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                        )

                        ProviderType.LOCAL -> LocalHostAuthBlock(
                            sessionManager = sessionManager,
                            onAuthenticated = { onSignedIn() },
                            onMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                        )
                    }
                }
            }
        }
    }
}

/**
 * The wordmark with a zero-width break opportunity (U+200B) after each hyphen.
 *
 * `github-mobile-maxxing` is a single unbreakable token for the text layout, so it used to
 * break in the middle of "maxxing" and strand the final "g" on its own line when the card
 * is narrower than the wordmark. The zero-width spaces keep the rendered wordmark
 * identical while giving the line breaker the hyphen boundaries as preferred wrap points.
 */
internal val SignInWordmarkText: String = AppBranding.APP_NAME.replace("-", "-\u200B")

/** Smallest size the wordmark shrinks to before it wraps at a hyphen instead. */
internal val SignInWordmarkMinFontSize = 24.sp

private fun TextStyle.wordmarkAt(fontSize: TextUnit): TextStyle {
    val ratio = if (this.fontSize.value > 0f) fontSize.value / this.fontSize.value else 1f
    return if (lineHeight.isSp) {
        copy(fontSize = fontSize, lineHeight = (lineHeight.value * ratio).sp)
    } else {
        copy(fontSize = fontSize)
    }
}

/**
 * Product wordmark shown at the top of the sign-in card.
 *
 * On narrow layouts the wordmark shrinks just enough to stay on one line, down to
 * [SignInWordmarkMinFontSize]. If even that size cannot fit (very narrow screens or enlarged
 * system text), it wraps at the hyphens instead of breaking mid-word, and wrapped lines are
 * centred so the mark still reads as one balanced unit. Wider screens keep the unchanged
 * `displaySmall` layout.
 *
 * [onTextLayout] is only used by layout regression tests to observe the resolved line box.
 */
@Composable
internal fun SignInWordmark(
    modifier: Modifier = Modifier,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null
) {
    val baseStyle = MaterialTheme.typography.displaySmall
    val minStyle = baseStyle.wordmarkAt(SignInWordmarkMinFontSize)
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val availableWidthPx = if (maxWidth.value.isFinite()) {
            with(density) { maxWidth.roundToPx() }
        } else {
            Int.MAX_VALUE
        }
        val widthAtMinSize = measurer.measure(
            text = AnnotatedString(AppBranding.APP_NAME),
            style = minStyle,
            maxLines = 1,
            softWrap = false
        ).size.width
        if (widthAtMinSize <= availableWidthPx) {
            Text(
                text = AppBranding.APP_NAME,
                style = baseStyle,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = SignInWordmarkMinFontSize,
                    maxFontSize = baseStyle.fontSize,
                    stepSize = 0.5.sp
                ),
                onTextLayout = onTextLayout
            )
        } else {
            Text(
                text = SignInWordmarkText,
                style = minStyle,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                onTextLayout = onTextLayout
            )
        }
    }
}
