package dev.maxxing.github.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.BellOff
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Languages
import com.composables.icons.lucide.Link2
import com.composables.icons.lucide.LogOut
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.Shield
import com.composables.icons.lucide.SlidersHorizontal
import com.composables.icons.lucide.Type
import dev.maxxing.github.app.AppContainer
import dev.maxxing.github.app.MaxxingBuildConfig
import dev.maxxing.github.app.SettingsSection
import dev.maxxing.github.app.fonts.PlatformFontManager
import dev.maxxing.github.app.notifications.PlatformNotifier
import dev.maxxing.github.app.resources.Res
import dev.maxxing.github.app.resources.app_icon
import dev.maxxing.github.auth.GitHubLinkHandlingState
import dev.maxxing.github.auth.NotificationPreferences
import dev.maxxing.github.auth.ThemePreferences
import dev.maxxing.github.auth.inspectGitHubLinkHandling
import dev.maxxing.github.auth.openOfficialGitHubAppLinkSettings
import dev.maxxing.github.auth.openThisAppLinkSettings
import dev.maxxing.github.auth.openUrlInBrowser
import dev.maxxing.github.model.AppBranding
import dev.maxxing.github.model.AppLanguage
import dev.maxxing.github.model.AuthSession
import dev.maxxing.github.model.FontSelection
import dev.maxxing.github.model.FontSourceKind
import dev.maxxing.github.model.RateLimitInfo
import dev.maxxing.github.model.ViewerProfile
import dev.maxxing.github.ui.components.MaxxingColorPickerDialog
import dev.maxxing.github.ui.components.MaxxingDropdownSelector
import dev.maxxing.github.ui.components.MaxxingPredictiveOverlayHost
import dev.maxxing.github.ui.components.MaxxingWavyProgress
import dev.maxxing.github.ui.components.UserAvatar
import dev.maxxing.github.ui.components.formatSeedColorHex
import dev.maxxing.github.ui.i18n.AppStrings
import dev.maxxing.github.ui.i18n.appStrings
import dev.maxxing.github.ui.theme.MaxxingSeedPresets
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

private enum class FontPickerTarget {
    PRIMARY,
    FALLBACK
}

private enum class DarkModeOption(val value: Boolean?) {
    SYSTEM(null),
    LIGHT(false),
    DARK(true);

    fun label(strings: AppStrings): String = when (this) {
        SYSTEM -> strings.appearanceSystem
        LIGHT -> strings.appearanceLight
        DARK -> strings.appearanceDark
    }
}

private enum class PollIntervalOption(val minutes: Int) {
    ONE(1),
    FIVE(5),
    FIFTEEN(15);

    fun label(strings: AppStrings): String = strings.pollIntervalMinutes(minutes)
}

internal data class SettingsCategoryItem(
    val section: SettingsSection,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val isWarning: Boolean = false
)

@Composable
fun SettingsScreen(
    container: AppContainer,
    activeSection: SettingsSection? = null,
    onSelectSection: (SettingsSection?) -> Unit = {},
    onOpenUser: (String) -> Unit = {},
    onSignedOut: () -> Unit,
    onMessage: (String) -> Unit
) {
    val session by container.sessionManager.session.collectAsState()
    val profile by container.viewerProfile.collectAsState()
    val rateLimit by container.repository.rateLimit.collectAsState()
    val themePrefs by container.sessionManager.themePreferences.collectAsState()
    val notifPrefs by container.sessionManager.notificationPreferences.collectAsState()
    val savedClientId by container.sessionManager.clientId.collectAsState()

    var clientIdField by remember(savedClientId) { mutableStateOf(savedClientId) }
    var showCustomColorPicker by remember { mutableStateOf(false) }
    // Saved at root scope, so the picker (and its document launcher) comes back immediately after
    // Android recreates the process while the system file picker is open.
    var fontPickerTarget by rememberSaveable { mutableStateOf<FontPickerTarget?>(null) }
    var linkHandlingState by remember { mutableStateOf(inspectGitHubLinkHandling()) }
    var osNotificationsBlocked by remember { mutableStateOf(PlatformNotifier.areOsNotificationsBlocked()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val strings = appStrings()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                linkHandlingState = inspectGitHubLinkHandling()
                osNotificationsBlocked = PlatformNotifier.areOsNotificationsBlocked()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val categoryItems = remember(themePrefs, notifPrefs, osNotificationsBlocked, linkHandlingState, strings) {
        listOf(
            SettingsCategoryItem(
                section = SettingsSection.APPEARANCE,
                title = strings.settingsCategoryAppearance,
                subtitle = formatAppearanceSummary(themePrefs, strings),
                icon = Lucide.Palette
            ),
            SettingsCategoryItem(
                section = SettingsSection.NOTIFICATIONS,
                title = strings.settingsCategoryNotifications,
                subtitle = formatNotificationsSummary(notifPrefs, osNotificationsBlocked, strings),
                icon = if (notifPrefs.osNotificationsEnabled) Lucide.Bell else Lucide.BellOff,
                isWarning = notifPrefs.osNotificationsEnabled && osNotificationsBlocked
            ),
            SettingsCategoryItem(
                section = SettingsSection.GENERAL,
                title = strings.settingsCategoryGeneral,
                subtitle = formatGeneralSummary(themePrefs.language, linkHandlingState, strings),
                icon = Lucide.SlidersHorizontal
            ),
            SettingsCategoryItem(
                section = SettingsSection.APP_INFO,
                title = strings.settingsCategoryAppInfo,
                subtitle = formatAppBuildFooter(
                    versionName = MaxxingBuildConfig.APP_VERSION_NAME,
                    commitSha = MaxxingBuildConfig.APP_COMMIT_SHA
                ),
                icon = Lucide.Info
            )
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SettingsHubContent(
            session = session,
            profile = profile,
            categoryItems = categoryItems,
            onOpenUser = onOpenUser,
            onSignOut = {
                scope.launch {
                    container.sessionManager.signOut()
                    container.clearCachedProfile()
                    onSignedOut()
                }
            },
            onSelectSection = { section -> onSelectSection(section) }
        )

        MaxxingPredictiveOverlayHost(
            target = activeSection,
            onBack = { onSelectSection(null) },
            contentKey = { it }
        ) { section ->
            when (section) {
                SettingsSection.APPEARANCE -> AppearanceSettingsPage(
                    themePrefs = themePrefs,
                    onBack = { onSelectSection(null) },
                    onUpdateTheme = { updated ->
                        scope.launch {
                            container.sessionManager.updateThemePreferences(updated)
                        }
                    },
                    onOpenCustomColorPicker = { showCustomColorPicker = true },
                    onOpenFontPicker = { target -> fontPickerTarget = target }
                )

                SettingsSection.NOTIFICATIONS -> NotificationsSettingsPage(
                    notifPrefs = notifPrefs,
                    osNotificationsBlocked = osNotificationsBlocked,
                    onBack = { onSelectSection(null) },
                    onUpdateNotifPrefs = { updated ->
                        scope.launch {
                            container.sessionManager.updateNotificationPreferences(updated)
                        }
                    },
                    onMessage = onMessage
                )

                SettingsSection.GENERAL -> GeneralSettingsPage(
                    language = themePrefs.language,
                    linkHandlingState = linkHandlingState,
                    clientIdField = clientIdField,
                    onBack = { onSelectSection(null) },
                    onSelectLanguage = { lang ->
                        scope.launch {
                            container.sessionManager.updateThemePreferences(
                                themePrefs.copy(language = lang)
                            )
                        }
                    },
                    onClientIdFieldChange = { clientIdField = it },
                    onSaveClientId = {
                        scope.launch {
                            container.sessionManager.updateClientId(clientIdField)
                            onMessage(strings.savedClientId)
                        }
                    },
                    onMessage = onMessage
                )

                SettingsSection.APP_INFO -> AppInfoSettingsPage(
                    rateLimit = rateLimit,
                    onBack = { onSelectSection(null) },
                    onMessage = onMessage
                )
            }
        }
    }

    fontPickerTarget?.let { target ->
        val isPrimary = target == FontPickerTarget.PRIMARY
        FontPickerDialog(
            title = if (isPrimary) strings.selectPrimaryFontTitle else strings.selectFallbackFontTitle,
            current = if (isPrimary) themePrefs.primaryFont else themePrefs.fallbackFont,
            inUseFilePaths = listOf(themePrefs.primaryFont, themePrefs.fallbackFont)
                .filter { it.kind == FontSourceKind.CUSTOM_FILE }
                .map { it.value }
                .toSet(),
            // Font updates start from the latest saved preferences, not the ones captured at
            // composition, so quick successive changes do not undo each other.
            onSelect = { selection ->
                scope.launch {
                    val latest = container.sessionManager.themePreferences.value
                    container.sessionManager.updateThemePreferences(
                        if (isPrimary) latest.copy(primaryFont = selection)
                        else latest.copy(fallbackFont = selection)
                    )
                }
                fontPickerTarget = null
            },
            onDismiss = { fontPickerTarget = null },
            onImported = { imported ->
                if (imported != null) {
                    val selection = FontSelection(
                        kind = FontSourceKind.CUSTOM_FILE,
                        value = imported.filePath,
                        displayName = imported.displayName
                    )
                    scope.launch {
                        val latest = container.sessionManager.themePreferences.value
                        container.sessionManager.updateThemePreferences(
                            if (isPrimary) latest.copy(primaryFont = selection)
                            else latest.copy(fallbackFont = selection)
                        )
                    }
                    onMessage(strings.importedFontMessage(imported.displayName))
                    fontPickerTarget = null
                } else {
                    onMessage(strings.failedImportFont)
                }
            },
            onDeletedImported = { deletedPath ->
                scope.launch {
                    val latest = container.sessionManager.themePreferences.value
                    var next = latest
                    if (next.primaryFont.kind == FontSourceKind.CUSTOM_FILE && next.primaryFont.value == deletedPath) {
                        next = next.copy(primaryFont = FontSelection.SystemDefault)
                    }
                    if (next.fallbackFont.kind == FontSourceKind.CUSTOM_FILE && next.fallbackFont.value == deletedPath) {
                        next = next.copy(fallbackFont = FontSelection.SystemDefault)
                    }
                    if (next != latest) {
                        container.sessionManager.updateThemePreferences(next)
                    }
                }
            }
        )
    }

    if (showCustomColorPicker) {
        MaxxingColorPickerDialog(
            initialColorHex = themePrefs.seedColorHex,
            onDismiss = { showCustomColorPicker = false },
            onSelectColor = { chosenHex ->
                showCustomColorPicker = false
                scope.launch {
                    container.sessionManager.updateThemePreferences(
                        themePrefs.copy(
                            seedColorHex = chosenHex,
                            useDynamicColor = false
                        )
                    )
                }
            }
        )
    }
}

@Composable
private fun SettingsHubContent(
    session: AuthSession?,
    profile: ViewerProfile?,
    categoryItems: List<SettingsCategoryItem>,
    onOpenUser: (String) -> Unit,
    onSignOut: () -> Unit,
    onSelectSection: (SettingsSection) -> Unit
) {
    val strings = appStrings()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = strings.navSettings,
                style = MaterialTheme.typography.headlineMedium
            )

            ElevatedCard(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                // The sign-out button sits below the profile so the name column keeps the full width
                // (a long button label next to it squeezed names into 1–2 characters per line).
                Column(modifier = Modifier.padding(20.dp)) {
                    val loginTarget = profile?.login ?: session?.userLogin
                    val profileClickable = !loginTarget.isNullOrBlank()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable(enabled = profileClickable) {
                                if (!loginTarget.isNullOrBlank()) onOpenUser(loginTarget)
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatar(
                            avatarUrl = profile?.avatarUrl.orEmpty(),
                            login = loginTarget ?: "?",
                            sizeDp = 56
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile?.name ?: "@${loginTarget ?: strings.githubUserFallback}",
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "@${loginTarget ?: strings.unknownUser} • ${session?.authMethod?.let { strings.authMethodLabel(it) } ?: ""}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!profile?.bio.isNullOrBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = profile.bio.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (profileClickable) {
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                imageVector = Lucide.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onSignOut,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Lucide.LogOut, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.signOut)
                    }
                }
            }

            SettingsHubCategoryGroup(
                items = categoryItems,
                onSelect = onSelectSection
            )
        }
    }
}

internal fun settingsCategoryShape(index: Int, count: Int): RoundedCornerShape {
    val outer = 24.dp
    val inner = 6.dp
    return when {
        count <= 1 -> RoundedCornerShape(outer)
        index == 0 -> RoundedCornerShape(
            topStart = outer,
            topEnd = outer,
            bottomStart = inner,
            bottomEnd = inner
        )
        index == count - 1 -> RoundedCornerShape(
            topStart = inner,
            topEnd = inner,
            bottomStart = outer,
            bottomEnd = outer
        )
        else -> RoundedCornerShape(inner)
    }
}

@Composable
internal fun SettingsHubCategoryGroup(
    items: List<SettingsCategoryItem>,
    onSelect: (SettingsSection) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items.forEachIndexed { index, item ->
            val shape = settingsCategoryShape(index, items.size)
            Surface(
                onClick = { onSelect(item.section) },
                shape = shape,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (item.isWarning) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = if (item.isWarning) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                },
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (item.isWarning) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Icon(
                        imageVector = Lucide.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSubPageScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val strings = appStrings()
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Lucide.ArrowLeft, contentDescription = strings.back)
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceSettingsPage(
    themePrefs: ThemePreferences,
    onBack: () -> Unit,
    onUpdateTheme: (ThemePreferences) -> Unit,
    onOpenCustomColorPicker: () -> Unit,
    onOpenFontPicker: (FontPickerTarget) -> Unit
) {
    val strings = appStrings()
    val supportsFallback = PlatformFontManager.supportsLayeredFallback
    // By kind: a System Default read from newer preferences may still carry a value and name.
    val hasCustomFonts = themePrefs.primaryFont.kind != FontSourceKind.SYSTEM_DEFAULT ||
        themePrefs.fallbackFont.kind != FontSourceKind.SYSTEM_DEFAULT

    SettingsSubPageScaffold(
        title = strings.settingsCategoryAppearance,
        onBack = onBack
    ) {
        // MD3E Theme customizer
        ElevatedCard(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Lucide.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(strings.themeTitle, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(16.dp))

                val currentMode = DarkModeOption.entries.first { it.value == themePrefs.darkModeOverride }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        strings.appearanceLabel,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f)
                    )
                    MaxxingDropdownSelector(
                        options = DarkModeOption.entries,
                        selected = currentMode,
                        onSelect = { opt ->
                            onUpdateTheme(themePrefs.copy(darkModeOverride = opt.value))
                        },
                        labelProvider = { it.label(strings) }
                    )
                }

                Spacer(Modifier.height(16.dp))
                val activePreset = MaxxingSeedPresets.firstOrNull { it.colorHex == themePrefs.seedColorHex }
                val isCustomColorSelected = activePreset == null
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.seedAccentLabel, style = MaterialTheme.typography.labelLarge)
                        Text(
                            text = "${activePreset?.name ?: strings.customColorLabel} · ${formatSeedColorHex(themePrefs.seedColorHex)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onOpenCustomColorPicker) {
                        Icon(Lucide.SlidersHorizontal, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.customColorLabel)
                    }
                }
                Spacer(Modifier.height(10.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MaxxingSeedPresets.forEach { preset ->
                        val selected = themePrefs.seedColorHex == preset.colorHex
                        val swatchColor = Color(preset.colorHex)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(swatchColor)
                                .then(
                                    if (selected) {
                                        Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    } else {
                                        Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                    }
                                )
                                .clickable {
                                    onUpdateTheme(
                                        themePrefs.copy(
                                            seedColorHex = preset.colorHex,
                                            useDynamicColor = false
                                        )
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    Lucide.Check,
                                    contentDescription = preset.name,
                                    tint = if (swatchColor.luminance() > 0.45f) Color.Black else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    val customBg = if (isCustomColorSelected) {
                        Color(themePrefs.seedColorHex)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(customBg)
                            .then(
                                if (isCustomColorSelected) {
                                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                } else {
                                    Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                }
                            )
                            .clickable(onClick = onOpenCustomColorPicker),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Lucide.SlidersHorizontal,
                            contentDescription = strings.pickCustomColorDesc,
                            tint = if (isCustomColorSelected) {
                                if (customBg.luminance() > 0.45f) Color.Black else Color.White
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.dynamicColorTitle, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            strings.dynamicColorDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = themePrefs.useDynamicColor,
                        onCheckedChange = { enabled ->
                            onUpdateTheme(themePrefs.copy(useDynamicColor = enabled))
                        }
                    )
                }
            }
        }

        // Typography & Font customizer
        ElevatedCard(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Lucide.Type, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(strings.fontSectionTitle, style = MaterialTheme.typography.titleMedium)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.primaryFontLabel, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            strings.primaryFontDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(onClick = { onOpenFontPicker(FontPickerTarget.PRIMARY) }) {
                        Text(
                            text = strings.fontSelectionLabel(themePrefs.primaryFont),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(Lucide.ChevronDown, contentDescription = null, modifier = Modifier.size(15.dp))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            strings.fallbackFontLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (supportsFallback) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (supportsFallback) strings.fallbackFontDesc else strings.fallbackFontRequiresAndroid10,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = { onOpenFontPicker(FontPickerTarget.FALLBACK) },
                        enabled = supportsFallback
                    ) {
                        Text(
                            text = strings.fontSelectionLabel(themePrefs.fallbackFont),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(Lucide.ChevronDown, contentDescription = null, modifier = Modifier.size(15.dp))
                    }
                }

                if (hasCustomFonts) {
                    OutlinedButton(
                        onClick = {
                            onUpdateTheme(
                                themePrefs.copy(
                                    primaryFont = FontSelection.SystemDefault,
                                    fallbackFont = FontSelection.SystemDefault
                                )
                            )
                        }
                    ) {
                        Text(strings.resetFontsToSystemDefault)
                    }
                }

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = strings.fontPreviewTitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = strings.fontPreviewPrimarySample,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = strings.fontPreviewSecondarySample,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationsSettingsPage(
    notifPrefs: NotificationPreferences,
    osNotificationsBlocked: Boolean,
    onBack: () -> Unit,
    onUpdateNotifPrefs: (NotificationPreferences) -> Unit,
    onMessage: (String) -> Unit
) {
    val strings = appStrings()

    SettingsSubPageScaffold(
        title = strings.settingsCategoryNotifications,
        onBack = onBack
    ) {
        ElevatedCard(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Lucide.Bell, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(strings.osNotificationsTitle, style = MaterialTheme.typography.titleMedium)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.enableOsAlerts, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            strings.enableOsAlertsDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = notifPrefs.osNotificationsEnabled,
                        onCheckedChange = { enabled ->
                            onUpdateNotifPrefs(notifPrefs.copy(osNotificationsEnabled = enabled))
                        }
                    )
                }

                if (notifPrefs.osNotificationsEnabled && osNotificationsBlocked) {
                    Text(
                        strings.osNotificationsBlockedWarning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    OutlinedButton(
                        onClick = {
                            if (!PlatformNotifier.openOsNotificationSettings()) {
                                onMessage(strings.failedOpenNotificationSettings)
                            }
                        }
                    ) {
                        Icon(Lucide.ExternalLink, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.openNotificationSettings)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.urgentOnly, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            strings.urgentOnlyDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = notifPrefs.urgentOnly,
                        enabled = notifPrefs.osNotificationsEnabled,
                        onCheckedChange = { urgent ->
                            onUpdateNotifPrefs(notifPrefs.copy(urgentOnly = urgent))
                        }
                    )
                }

                val currentPoll = PollIntervalOption.entries.firstOrNull {
                    it.minutes == notifPrefs.pollIntervalMinutes
                } ?: PollIntervalOption.FIVE
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        strings.foregroundSyncInterval,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f)
                    )
                    MaxxingDropdownSelector(
                        options = PollIntervalOption.entries,
                        selected = currentPoll,
                        onSelect = { opt ->
                            onUpdateNotifPrefs(notifPrefs.copy(pollIntervalMinutes = opt.minutes))
                        },
                        labelProvider = { it.label(strings) }
                    )
                }

                FilledTonalButton(
                    onClick = {
                        val sent = PlatformNotifier.showTestNotification()
                        onMessage(
                            if (sent) strings.testNotificationSent
                            else strings.testNotificationBlocked
                        )
                    }
                ) {
                    Icon(Lucide.Bell, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.sendTestNotification)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GeneralSettingsPage(
    language: AppLanguage,
    linkHandlingState: GitHubLinkHandlingState,
    clientIdField: String,
    onBack: () -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onClientIdFieldChange: (String) -> Unit,
    onSaveClientId: () -> Unit,
    onMessage: (String) -> Unit
) {
    val strings = appStrings()

    SettingsSubPageScaffold(
        title = strings.settingsCategoryGeneral,
        onBack = onBack
    ) {
        // Language selector card
        ElevatedCard(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Lucide.Languages, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(strings.languageSectionTitle, style = MaterialTheme.typography.titleMedium)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.languageLabel, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            strings.languageDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    MaxxingDropdownSelector(
                        options = AppLanguage.entries,
                        selected = language,
                        onSelect = onSelectLanguage,
                        labelProvider = { strings.languageOptionLabel(it) }
                    )
                }
            }
        }

        if (linkHandlingState.supportedOnPlatform) {
            ElevatedCard(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Lucide.Link2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(strings.linkHandlingTitle, style = MaterialTheme.typography.titleMedium)
                    }
                    val statusSummary = when {
                        linkHandlingState.thisAppHandlesGitHubLinks ->
                            strings.linkHandlingEnabledDesc
                        linkHandlingState.officialGitHubInstalled &&
                            linkHandlingState.officialGitHubHandlesLinks == true ->
                            strings.linkHandlingOfficialClaimsDesc
                        linkHandlingState.officialGitHubInstalled ->
                            strings.linkHandlingOfficialInstalledDesc
                        else ->
                            strings.linkHandlingDefaultDesc
                    }
                    Text(
                        text = statusSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (linkHandlingState.officialGitHubInstalled) {
                            OutlinedButton(
                                onClick = {
                                    if (!openOfficialGitHubAppLinkSettings()) {
                                        onMessage(strings.failedOpenGitHubMobileLinkSettings)
                                    }
                                }
                            ) {
                                Icon(Lucide.ExternalLink, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(strings.unlinkGitHubMobile)
                            }
                        }
                        FilledTonalButton(
                            onClick = {
                                if (!openThisAppLinkSettings()) {
                                    onMessage(strings.failedOpenAppLinkSettings)
                                }
                            }
                        ) {
                            Icon(Lucide.Link2, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (linkHandlingState.thisAppHandlesGitHubLinks) {
                                    strings.manageAppLinks
                                } else {
                                    strings.enableGitHubLinks
                                }
                            )
                        }
                    }
                }
            }
        }

        // OAuth Client ID config
        ElevatedCard(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Lucide.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(strings.oauthConfigTitle, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = clientIdField,
                        onValueChange = onClientIdFieldChange,
                        label = { Text(strings.oauthClientIdLabel) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalButton(onClick = onSaveClientId) {
                        Text(strings.save)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppInfoSettingsPage(
    rateLimit: RateLimitInfo,
    onBack: () -> Unit,
    onMessage: (String) -> Unit
) {
    val strings = appStrings()
    val clipboard = LocalClipboardManager.current
    val cleanVersion = MaxxingBuildConfig.APP_VERSION_NAME.trim().removePrefix("v").ifBlank { "dev" }
    val cleanCommit = MaxxingBuildConfig.APP_COMMIT_SHA.trim().ifBlank { "unknown" }
    val buildSummary = formatAppBuildFooter(
        versionName = MaxxingBuildConfig.APP_VERSION_NAME,
        commitSha = MaxxingBuildConfig.APP_COMMIT_SHA
    )

    SettingsSubPageScaffold(
        title = strings.settingsCategoryAppInfo,
        onBack = onBack
    ) {
        ElevatedCard(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(Res.drawable.app_icon),
                        contentDescription = AppBranding.APP_NAME,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(MaterialTheme.shapes.medium)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = AppBranding.APP_NAME,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = strings.signInSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider()

                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppInfoMetadataRow(
                            label = strings.appInfoVersionLabel,
                            value = "v$cleanVersion"
                        )
                        AppInfoMetadataRow(
                            label = strings.appInfoCommitLabel,
                            value = cleanCommit
                        )
                        AppInfoMetadataRow(
                            label = strings.appInfoBuildLabel,
                            value = buildSummary
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            clipboard.setText(AnnotatedString("${AppBranding.APP_NAME} $buildSummary"))
                            onMessage(strings.appInfoCopiedBuildInfo)
                        }
                    ) {
                        Icon(Lucide.Copy, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.appInfoCopyBuildInfo)
                    }
                    OutlinedButton(
                        onClick = { openUrlInBrowser(AppBranding.REPOSITORY_URL) }
                    ) {
                        Icon(Lucide.ExternalLink, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.appInfoViewRepository)
                    }
                }
            }
        }

        // Rate Limit card
        ElevatedCard(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.apiRateLimit, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${rateLimit.remaining} / ${rateLimit.limit}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(12.dp))
                val ratio = if (rateLimit.limit > 0) {
                    rateLimit.remaining.toFloat() / rateLimit.limit.toFloat()
                } else 1f
                MaxxingWavyProgress(progress = ratio)
            }
        }
    }
}

@Composable
private fun AppInfoMetadataRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

internal fun formatAppearanceSummary(
    themePrefs: ThemePreferences,
    strings: AppStrings
): String {
    val modeLabel = DarkModeOption.entries
        .first { it.value == themePrefs.darkModeOverride }
        .label(strings)
    val colorLabel = if (themePrefs.useDynamicColor) {
        strings.settingsSummaryDynamicColor
    } else {
        MaxxingSeedPresets.firstOrNull { it.colorHex == themePrefs.seedColorHex }?.name
            ?: strings.customColorLabel
    }
    val customFontLabel = when {
        themePrefs.primaryFont.kind != FontSourceKind.SYSTEM_DEFAULT ->
            strings.fontSelectionLabel(themePrefs.primaryFont)
        themePrefs.fallbackFont.kind != FontSourceKind.SYSTEM_DEFAULT ->
            strings.fontSelectionLabel(themePrefs.fallbackFont)
        else -> null
    }
    return listOfNotNull(modeLabel, colorLabel, customFontLabel).joinToString(" · ")
}

internal fun formatNotificationsSummary(
    notifPrefs: NotificationPreferences,
    osNotificationsBlocked: Boolean,
    strings: AppStrings
): String = when {
    !notifPrefs.osNotificationsEnabled -> strings.settingsSummaryNotificationsOff
    osNotificationsBlocked -> strings.settingsSummaryNotificationsBlocked
    notifPrefs.urgentOnly -> "${strings.urgentOnly} · ${strings.pollIntervalMinutes(notifPrefs.pollIntervalMinutes)}"
    else -> "${strings.settingsSummaryNotificationsOn} · ${strings.pollIntervalMinutes(notifPrefs.pollIntervalMinutes)}"
}

internal fun formatGeneralSummary(
    language: AppLanguage,
    linkHandlingState: GitHubLinkHandlingState,
    strings: AppStrings
): String {
    val langLabel = strings.languageOptionLabel(language)
    if (!linkHandlingState.supportedOnPlatform) return langLabel
    val linksLabel = if (linkHandlingState.thisAppHandlesGitHubLinks) {
        strings.settingsSummaryLinksEnabled
    } else {
        strings.settingsSummaryLinksOff
    }
    return "$langLabel · $linksLabel"
}

internal fun formatAppBuildFooter(versionName: String, commitSha: String): String {
    val cleanVersion = versionName.trim().removePrefix("v").ifBlank { "dev" }
    val cleanCommit = commitSha.trim().ifBlank { "unknown" }
    return "v$cleanVersion ($cleanCommit)"
}
