package dev.maxxing.github.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.composables.icons.lucide.ShieldAlert
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.HardDrive
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.LogOut
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Server
import com.composables.icons.lucide.Settings2
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.User
import com.composables.icons.lucide.Users
import com.composables.icons.lucide.X
import dev.maxxing.github.app.AppContainer
import dev.maxxing.github.app.catchingSecureStorage
import dev.maxxing.github.app.platformCapabilities
import dev.maxxing.github.app.secureStorageErrorDetail
import dev.maxxing.github.auth.SessionManager
import dev.maxxing.github.auth.openUrlInBrowser
import dev.maxxing.github.model.AppBranding
import dev.maxxing.github.model.AuthSession
import dev.maxxing.github.model.DeviceCodeState
import dev.maxxing.github.model.ProviderType
import dev.maxxing.github.model.buildForgejoTokenCreationUrl
import dev.maxxing.github.model.normalizeInstanceBaseUrl
import dev.maxxing.github.ui.components.ExpressiveStatusPill
import dev.maxxing.github.ui.components.MaxxingConnectedButtonGroup
import dev.maxxing.github.ui.components.MaxxingLoadingState
import dev.maxxing.github.ui.components.MaxxingWavyProgress
import dev.maxxing.github.ui.components.UserAvatar
import dev.maxxing.github.ui.i18n.AppStrings
import dev.maxxing.github.ui.i18n.appStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class AccountClusterLayout {
    EMPTY,
    SINGLE,
    PAIR,
    TRIANGLE,
    GRID_FOUR,
    GRID_OVERFLOW
}

internal fun resolveAccountClusterLayout(accountCount: Int): AccountClusterLayout = when {
    accountCount <= 0 -> AccountClusterLayout.EMPTY
    accountCount == 1 -> AccountClusterLayout.SINGLE
    accountCount == 2 -> AccountClusterLayout.PAIR
    accountCount == 3 -> AccountClusterLayout.TRIANGLE
    accountCount == 4 -> AccountClusterLayout.GRID_FOUR
    else -> AccountClusterLayout.GRID_OVERFLOW
}

@Composable
fun AccountAvatarCluster(
    accounts: List<AuthSession>,
    sizeDp: Int = 56,
    modifier: Modifier = Modifier
) {
    val layout = resolveAccountClusterLayout(accounts.size)
    val boxSize = sizeDp.dp
    Box(
        modifier = modifier.size(boxSize),
        contentAlignment = Alignment.Center
    ) {
        when (layout) {
            AccountClusterLayout.EMPTY -> {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(boxSize)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Lucide.Users,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size((sizeDp * 0.45f).dp)
                        )
                    }
                }
            }
            AccountClusterLayout.SINGLE -> {
                val acc = accounts.first()
                UserAvatar(
                    avatarUrl = acc.avatarUrl,
                    login = acc.userLogin ?: "?",
                    sizeDp = sizeDp
                )
            }
            AccountClusterLayout.PAIR -> {
                val itemSize = (sizeDp * 0.46f).toInt().coerceAtLeast(18)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    accounts.take(2).forEach { acc ->
                        UserAvatar(
                            avatarUrl = acc.avatarUrl,
                            login = acc.userLogin ?: "?",
                            sizeDp = itemSize
                        )
                    }
                }
            }
            AccountClusterLayout.TRIANGLE -> {
                val itemSize = (sizeDp * 0.44f).toInt().coerceAtLeast(16)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    UserAvatar(
                        avatarUrl = accounts[0].avatarUrl,
                        login = accounts[0].userLogin ?: "?",
                        sizeDp = itemSize
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        UserAvatar(
                            avatarUrl = accounts[1].avatarUrl,
                            login = accounts[1].userLogin ?: "?",
                            sizeDp = itemSize
                        )
                        UserAvatar(
                            avatarUrl = accounts[2].avatarUrl,
                            login = accounts[2].userLogin ?: "?",
                            sizeDp = itemSize
                        )
                    }
                }
            }
            AccountClusterLayout.GRID_FOUR -> {
                val itemSize = (sizeDp * 0.46f).toInt().coerceAtLeast(16)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        UserAvatar(accounts[0].avatarUrl, accounts[0].userLogin ?: "?", sizeDp = itemSize)
                        UserAvatar(accounts[1].avatarUrl, accounts[1].userLogin ?: "?", sizeDp = itemSize)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        UserAvatar(accounts[2].avatarUrl, accounts[2].userLogin ?: "?", sizeDp = itemSize)
                        UserAvatar(accounts[3].avatarUrl, accounts[3].userLogin ?: "?", sizeDp = itemSize)
                    }
                }
            }
            AccountClusterLayout.GRID_OVERFLOW -> {
                val itemSize = (sizeDp * 0.46f).toInt().coerceAtLeast(16)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        UserAvatar(accounts[0].avatarUrl, accounts[0].userLogin ?: "?", sizeDp = itemSize)
                        UserAvatar(accounts[1].avatarUrl, accounts[1].userLogin ?: "?", sizeDp = itemSize)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        UserAvatar(accounts[2].avatarUrl, accounts[2].userLogin ?: "?", sizeDp = itemSize)
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.size(itemSize.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "…",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AccountSwitcherFloatingPill(
    activeAccount: AuthSession?,
    accounts: List<AuthSession>,
    reauthRequiredIds: Set<String>,
    onSwitchAccount: (String) -> Unit,
    onOpenManage: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (activeAccount == null) return
    val strings = appStrings()
    var menuExpanded by remember { mutableStateOf(false) }
    val activeId = activeAccount.resolvedAccountId()
    val hasWarning = activeId in reauthRequiredIds

    Box(modifier = modifier) {
        Surface(
            onClick = { menuExpanded = true },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
            border = BorderStroke(
                width = 1.dp,
                color = if (hasWarning) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 220.dp)
                    .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UserAvatar(
                    avatarUrl = activeAccount.avatarUrl,
                    login = activeAccount.userLogin ?: "?",
                    sizeDp = 26
                )
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = "@${activeAccount.userLogin ?: strings.unknownUser}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (activeAccount.providerType != ProviderType.GITHUB) {
                        Text(
                            text = activeAccount.instanceHostLabel(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (hasWarning) {
                    Icon(
                        Lucide.ShieldAlert,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp)
                    )
                } else {
                    Icon(
                        Lucide.ChevronDown,
                        contentDescription = strings.switchAccountTitle,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(min = 240.dp, max = 310.dp)
        ) {
            Text(
                text = strings.switchAccountTitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Column(
                modifier = Modifier
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                accounts.forEach { acc ->
                    val id = acc.resolvedAccountId()
                    val isSelected = id == activeId
                    val needsReauth = id in reauthRequiredIds
                    DropdownMenuItem(
                        leadingIcon = {
                            UserAvatar(
                                avatarUrl = acc.avatarUrl,
                                login = acc.userLogin ?: "?",
                                sizeDp = 28
                            )
                        },
                        text = {
                            Column {
                                Text(
                                    text = "@${acc.userLogin ?: strings.unknownUser}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (needsReauth) {
                                        "${acc.instanceHostLabel()} · ${strings.accountReauthRequired}"
                                    } else {
                                        acc.instanceHostLabel()
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (needsReauth) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        trailingIcon = {
                            if (isSelected) {
                                Icon(
                                    Lucide.Check,
                                    contentDescription = strings.accountActiveBadge,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        },
                        onClick = {
                            menuExpanded = false
                            if (!isSelected) {
                                onSwitchAccount(id)
                            }
                        }
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                leadingIcon = {
                    Icon(
                        Lucide.Settings2,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                text = {
                    Text(
                        text = strings.manageAction,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = {
                    menuExpanded = false
                    onOpenManage()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ManageAccountsDialog(
    container: AppContainer,
    accounts: List<AuthSession>,
    activeAccountId: String?,
    reauthRequiredIds: Set<String>,
    initialCategory: ProviderType = ProviderType.GITHUB,
    onDismiss: () -> Unit,
    onSwitchAccount: (String) -> Unit,
    onLogoutAccount: (String) -> Unit,
    onMessage: (String) -> Unit
) {
    val strings = appStrings()
    val scope = rememberCoroutineScope()
    var selectedCategory by remember(initialCategory) { mutableStateOf(initialCategory) }
    var showAddGitHub by remember { mutableStateOf(false) }
    var showAddOthers by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 6.dp,
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .heightIn(max = 620.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.manageAccountsTitle,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Lucide.X, contentDescription = strings.closeAction, modifier = Modifier.size(18.dp))
                    }
                }

                // Connected Pill for GitHub / Others / Local
                MaxxingConnectedButtonGroup(
                    options = ProviderType.entries,
                    selected = selectedCategory,
                    onSelect = {
                        selectedCategory = it
                        showAddGitHub = false
                        showAddOthers = false
                    },
                    labelProvider = { type ->
                        when (type) {
                            ProviderType.GITHUB -> strings.providerGitHub
                            ProviderType.FORGEJO_GITEA -> strings.providerOthers
                            ProviderType.LOCAL -> strings.providerLocal
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (selectedCategory) {
                        ProviderType.GITHUB -> {
                            val githubAccounts = accounts.filter { it.providerType == ProviderType.GITHUB }
                            if (githubAccounts.isEmpty()) {
                                Text(
                                    text = strings.githubNoAccounts,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                githubAccounts.forEach { acc ->
                                    val id = acc.resolvedAccountId()
                                    AccountManageRow(
                                        session = acc,
                                        isActive = id == activeAccountId,
                                        needsReauth = id in reauthRequiredIds,
                                        onSelect = {
                                            if (id != activeAccountId) {
                                                onSwitchAccount(id)
                                            }
                                        },
                                        onLogout = { onLogoutAccount(id) }
                                    )
                                }
                            }

                            if (!showAddGitHub) {
                                FilledTonalButton(
                                    onClick = { showAddGitHub = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Lucide.Plus, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(strings.addAction)
                                }
                            } else {
                                Surface(
                                    shape = MaterialTheme.shapes.large,
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = strings.signInWithGitHubButton,
                                                style = MaterialTheme.typography.titleSmall,
                                                modifier = Modifier.weight(1f)
                                            )
                                            TextButton(onClick = { showAddGitHub = false }) {
                                                Text(strings.cancel)
                                            }
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        GitHubDeviceFlowAuthBlock(
                                            sessionManager = container.sessionManager,
                                            onAuthenticated = { newSession ->
                                                showAddGitHub = false
                                                scope.launch {
                                                    container.onExternalAccountAdded(newSession)
                                                }
                                                onMessage(strings.signInWelcome(newSession.userLogin ?: "developer"))
                                            },
                                            onMessage = onMessage
                                        )
                                    }
                                }
                            }
                        }

                        ProviderType.FORGEJO_GITEA -> {
                            val otherAccounts = accounts.filter { it.providerType == ProviderType.FORGEJO_GITEA }
                            if (otherAccounts.isEmpty()) {
                                Text(
                                    text = strings.othersNoAccounts,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                otherAccounts.forEach { acc ->
                                    val id = acc.resolvedAccountId()
                                    AccountManageRow(
                                        session = acc,
                                        isActive = id == activeAccountId,
                                        needsReauth = id in reauthRequiredIds,
                                        onSelect = {
                                            if (id != activeAccountId) {
                                                onSwitchAccount(id)
                                            }
                                        },
                                        onLogout = { onLogoutAccount(id) }
                                    )
                                }
                            }

                            if (!showAddOthers) {
                                FilledTonalButton(
                                    onClick = { showAddOthers = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Lucide.Plus, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(strings.addAction)
                                }
                            } else {
                                Surface(
                                    shape = MaterialTheme.shapes.large,
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = strings.providerOthers,
                                                style = MaterialTheme.typography.titleSmall,
                                                modifier = Modifier.weight(1f)
                                            )
                                            TextButton(onClick = { showAddOthers = false }) {
                                                Text(strings.cancel)
                                            }
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        ForgejoPatAuthBlock(
                                            sessionManager = container.sessionManager,
                                            onAuthenticated = { newSession ->
                                                showAddOthers = false
                                                scope.launch {
                                                    container.onExternalAccountAdded(newSession)
                                                }
                                                onMessage(strings.signInWelcome(newSession.userLogin ?: "user"))
                                            },
                                            onMessage = onMessage
                                        )
                                    }
                                }
                            }
                        }

                        ProviderType.LOCAL -> {
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Lucide.HardDrive,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Text(
                                        text = strings.localWipTitle,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = strings.localWipBody,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountManageRow(
    session: AuthSession,
    isActive: Boolean,
    needsReauth: Boolean,
    onSelect: () -> Unit,
    onLogout: () -> Unit
) {
    val strings = appStrings()
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = if (isActive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onSelect)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            UserAvatar(
                avatarUrl = session.avatarUrl,
                login = session.userLogin ?: "?",
                sizeDp = 36
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "@${session.userLogin ?: strings.unknownUser}",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isActive) {
                        ExpressiveStatusPill(
                            label = strings.accountActiveBadge,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Text(
                    text = if (needsReauth) {
                        "${session.instanceHostLabel()} · ${strings.accountReauthRequired}"
                    } else {
                        "${session.instanceHostLabel()} · ${strings.authMethodLabel(session.authMethod)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (needsReauth) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(
                onClick = onLogout,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(Lucide.LogOut, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(strings.logoutAction, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

@Composable
fun GitHubDeviceFlowAuthBlock(
    sessionManager: SessionManager,
    onAuthenticated: (AuthSession) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = appStrings()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val focusManager = LocalFocusManager.current

    val savedClientId = sessionManager.clientId.value
    val effectiveDefaultId = savedClientId.ifBlank { AppBranding.DEFAULT_OAUTH_CLIENT_ID }
    var clientIdInput by remember(effectiveDefaultId) { mutableStateOf(effectiveDefaultId) }
    var showCustomClientId by remember { mutableStateOf(false) }
    var deviceState by remember { mutableStateOf<DeviceCodeState>(DeviceCodeState.Idle) }
    var pollingJob by remember { mutableStateOf<Job?>(null) }
    val hasCustomClientIdOverride = clientIdInput != AppBranding.DEFAULT_OAUTH_CLIENT_ID

    Column(
        modifier = modifier.fillMaxWidth(),
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
                    Spacer(Modifier.height(12.dp))
                }

                Button(
                    onClick = {
                        val activeId = clientIdInput.trim().ifBlank { AppBranding.DEFAULT_OAUTH_CLIENT_ID }
                        pollingJob?.cancel()
                        pollingJob = scope.launch {
                            catchingSecureStorage {
                                sessionManager.startDeviceFlow(activeId).collect { next ->
                                    deviceState = next
                                    if (next is DeviceCodeState.Authenticated) {
                                        onAuthenticated(next.session)
                                    }
                                }
                            }.onFailure {
                                deviceState = DeviceCodeState.Error(
                                    strings.secureStorageSaveFailed(secureStorageErrorDetail(it))
                                )
                            }
                        }
                    },
                    enabled = clientIdInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(strings.signInWithGitHubButton)
                }

                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { showCustomClientId = !showCustomClientId }) {
                    Text(if (showCustomClientId) strings.signInHideCustomClientId else strings.signInUseCustomClientId)
                }

                AnimatedVisibility(visible = showCustomClientId) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = clientIdInput,
                            onValueChange = { clientIdInput = it },
                            label = { Text(strings.signInClientIdLabel) },
                            placeholder = { Text(AppBranding.DEFAULT_OAUTH_CLIENT_ID) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            leadingIcon = { Icon(Lucide.ShieldCheck, contentDescription = null) },
                            supportingText = {
                                Text(strings.signInClientIdDefaultSupporting(AppBranding.DEFAULT_OAUTH_CLIENT_ID))
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (hasCustomClientIdOverride) {
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
                Text(text = strings.signInEnterCode, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))

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
                            modifier = Modifier.padding(vertical = 14.dp, horizontal = 12.dp)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
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
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = {
                                runCatching { clipboard.setText(AnnotatedString(state.verificationUri)) }
                                onMessage(strings.signInCopiedValue(state.verificationUri))
                            }
                        ) {
                            Icon(Lucide.Copy, contentDescription = strings.copyUrl, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                MaxxingWavyProgress()
                Spacer(Modifier.height(8.dp))
                Text(
                    text = strings.deviceFlowWaitingText(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            runCatching { clipboard.setText(AnnotatedString(state.userCode)) }
                            onMessage(strings.signInCopiedValue(state.userCode))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Lucide.Copy, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.signInCopyCode, maxLines = 1, softWrap = false)
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                runCatching { clipboard.setText(AnnotatedString(state.userCode)) }
                                val openedByPlatform = withContext(Dispatchers.IO) {
                                    openUrlInBrowser(state.verificationUri)
                                }
                                val opened = if (openedByPlatform) true else runCatching {
                                    uriHandler.openUri(state.verificationUri)
                                    true
                                }.getOrDefault(false)
                                onMessage(
                                    if (opened) strings.signInCodeCopiedOpening(state.userCode)
                                    else strings.signInCodeCopiedManual(state.userCode, state.verificationUri)
                                )
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Lucide.ExternalLink, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.signInOpenGitHub, maxLines = 1, softWrap = false)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        pollingJob?.cancel()
                        deviceState = DeviceCodeState.Idle
                    },
                    modifier = Modifier.fillMaxWidth()
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ForgejoPatAuthBlock(
    sessionManager: SessionManager,
    onAuthenticated: (AuthSession) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = appStrings()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val focusManager = LocalFocusManager.current

    var baseUrlInput by remember { mutableStateOf(AuthSession.DEFAULT_CODEBERG_BASE_URL) }
    var patInput by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val tokenCreationUrl = remember(baseUrlInput) { buildForgejoTokenCreationUrl(baseUrlInput) }
    val normalizedUrl = remember(baseUrlInput) { normalizeInstanceBaseUrl(baseUrlInput) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (errorMessage != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterChip(
                selected = normalizedUrl == AuthSession.DEFAULT_CODEBERG_BASE_URL,
                onClick = { baseUrlInput = AuthSession.DEFAULT_CODEBERG_BASE_URL },
                leadingIcon = { Icon(Lucide.Globe, contentDescription = null, modifier = Modifier.size(14.dp)) },
                label = { Text(strings.othersCodebergPreset) }
            )
        }

        OutlinedTextField(
            value = baseUrlInput,
            onValueChange = {
                baseUrlInput = it
                errorMessage = null
            },
            label = { Text(strings.othersInstanceUrlLabel) },
            placeholder = { Text(strings.othersInstanceUrlPlaceholder) },
            leadingIcon = { Icon(Lucide.Server, contentDescription = null, modifier = Modifier.size(18.dp)) },
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = patInput,
            onValueChange = {
                patInput = it
                errorMessage = null
            },
            label = { Text(strings.othersPatLabel) },
            placeholder = { Text(strings.othersPatPlaceholder) },
            leadingIcon = { Icon(Lucide.KeyRound, contentDescription = null, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                IconButton(onClick = { showToken = !showToken }) {
                    Icon(
                        imageVector = if (showToken) Lucide.EyeOff else Lucide.Eye,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            },
            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedButton(
            onClick = {
                scope.launch {
                    val openedByPlatform = withContext(Dispatchers.IO) {
                        openUrlInBrowser(tokenCreationUrl)
                    }
                    val opened = if (openedByPlatform) true else runCatching {
                        uriHandler.openUri(tokenCreationUrl)
                        true
                    }.getOrDefault(false)
                    if (!opened) {
                        onMessage(tokenCreationUrl)
                    }
                }
            },
            enabled = normalizedUrl != null && !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Lucide.ExternalLink, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(strings.othersOpenTokenPage, maxLines = 1, softWrap = false)
        }
        Text(
            text = strings.othersOpenTokenPageDesc(tokenCreationUrl),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        if (busy) {
            MaxxingWavyProgress()
        }

        Button(
            onClick = {
                focusManager.clearFocus()
                busy = true
                errorMessage = null
                scope.launch {
                    val res = catchingSecureStorage {
                        sessionManager.signInWithForgejoPat(baseUrlInput, patInput).getOrThrow()
                    }
                    busy = false
                    res.onSuccess { session ->
                        patInput = ""
                        onAuthenticated(session)
                    }.onFailure { err ->
                        errorMessage = err.message ?: strings.unknownError
                    }
                }
            },
            enabled = !busy && normalizedUrl != null && patInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text(if (busy) strings.othersVerifying else strings.othersVerifyAndAdd)
        }
    }
}
