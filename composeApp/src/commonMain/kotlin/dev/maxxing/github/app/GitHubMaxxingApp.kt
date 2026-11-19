package dev.maxxing.github.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.composables.icons.lucide.FolderGit2
import com.composables.icons.lucide.GitPullRequest
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Inbox
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Settings
import dev.maxxing.github.app.fonts.ProvideAppTypographyFont
import dev.maxxing.github.app.notifications.PlatformNotifier
import dev.maxxing.github.app.ui.GistsScreen
import dev.maxxing.github.app.ui.HomeScreen
import dev.maxxing.github.app.ui.IssuePullDetailPane
import dev.maxxing.github.app.ui.IssuesPullsScreen
import dev.maxxing.github.app.ui.NotificationsScreen
import dev.maxxing.github.app.ui.RepoDetailScreen
import dev.maxxing.github.app.ui.ReposScreen
import dev.maxxing.github.app.ui.SettingsScreen
import dev.maxxing.github.app.ui.SignInScreen
import dev.maxxing.github.app.ui.UserProfileScreen
import dev.maxxing.github.auth.openUrlInBrowser
import dev.maxxing.github.model.AppBranding
import dev.maxxing.github.model.AppLanguage
import dev.maxxing.github.model.RepoSummary
import dev.maxxing.github.network.createGitHubImageHttpClient
import dev.maxxing.github.ui.components.MaxxingLoadingState
import dev.maxxing.github.ui.components.MaxxingPredictiveBackContainer
import dev.maxxing.github.ui.components.MaxxingPredictiveOverlayHost
import dev.maxxing.github.ui.i18n.AppStrings
import dev.maxxing.github.ui.i18n.appStrings
import dev.maxxing.github.ui.theme.MaxxingTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private enum class AppDestination(
    val shell: ShellDestination,
    val icon: ImageVector
) {
    HOME(ShellDestination.HOME, Lucide.House),
    INBOX(ShellDestination.INBOX, Lucide.Inbox),
    ISSUES_PRS(ShellDestination.ISSUES_PRS, Lucide.GitPullRequest),
    REPOS(ShellDestination.REPOS, Lucide.FolderGit2),
    SETTINGS(ShellDestination.SETTINGS, Lucide.Settings);

    fun label(strings: AppStrings): String = when (this) {
        HOME -> strings.navHome
        INBOX -> strings.navInbox
        ISSUES_PRS -> strings.navIssues
        REPOS -> strings.navRepos
        SETTINGS -> strings.navSettings
    }
}

@Composable
fun GitHubMaxxingApp(
    deepLinkUrl: String? = null,
    onConsumeDeepLink: () -> Unit = {},
    onDarkThemeChanged: (Boolean) -> Unit = {},
    onLanguageChanged: (AppLanguage) -> Unit = {}
) {
    val container = remember { AppContainer() }

    setSingletonImageLoaderFactory { context ->
        val imageHttpClient = createGitHubImageHttpClient {
            container.sessionManager.session.value?.accessToken
        }
        ImageLoader.Builder(context)
            .components {
                add(SvgDecoder.Factory())
                add(KtorNetworkFetcherFactory(httpClient = { imageHttpClient }))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.20)
                    .build()
            }
            .crossfade(false)
            .build()
    }
    val isInitialized by container.sessionManager.isInitialized.collectAsState()
    val session by container.sessionManager.session.collectAsState()
    val themePrefs by container.sessionManager.themePreferences.collectAsState()
    val notifPrefs by container.sessionManager.notificationPreferences.collectAsState()

    val viewerProfile by container.viewerProfile.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val appScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        PlatformNotifier.ensureInitialized()
        container.sessionManager.initialize()
    }

    LaunchedEffect(session) {
        if (session != null) {
            val loaded = container.refreshViewerProfile()
            container.warmUpWorkspace(loaded?.login ?: session?.userLogin)
        }
    }

    LaunchedEffect(isInitialized, session, notifPrefs.osNotificationsEnabled) {
        // Wait for the stored session/prefs to load; otherwise the initial null session
        // would cancel the periodic background work on every cold start.
        if (!isInitialized) return@LaunchedEffect
        PlatformNotifier.syncBackgroundSchedule(
            enabled = session != null && notifPrefs.osNotificationsEnabled
        )
    }

    // Immediate sync whenever app returns to foreground (ON_RESUME)
    DisposableEffect(lifecycleOwner, session) {
        if (session == null) return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                appScope.launch {
                    container.syncNotifications()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Foreground real-time notification watcher across Android & Linux Desktop
    LaunchedEffect(session, notifPrefs) {
        if (session == null) return@LaunchedEffect
        while (isActive) {
            container.syncNotifications()
            val intervalMs = (notifPrefs.pollIntervalMinutes.coerceAtLeast(1)) * 60_000L
            delay(intervalMs)
        }
    }

    val resolvedDarkTheme = themePrefs.darkModeOverride ?: isSystemInDarkTheme()
    LaunchedEffect(resolvedDarkTheme) {
        onDarkThemeChanged(resolvedDarkTheme)
    }

    LaunchedEffect(themePrefs.language) {
        onLanguageChanged(themePrefs.language)
        PlatformNotifier.updateLanguage(themePrefs.language)
    }

    ProvideAppTypographyFont(
        primary = themePrefs.primaryFont,
        fallback = themePrefs.fallbackFont
    ) { fontFamilyOverride ->
        MaxxingTheme(
            seedColorHex = themePrefs.seedColorHex,
            useDynamicColor = themePrefs.useDynamicColor,
            darkThemeOverride = themePrefs.darkModeOverride,
            fontFamilyOverride = fontFamilyOverride,
            language = themePrefs.language
        ) {
            val strings = appStrings()
            when {
                !isInitialized -> {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            MaxxingLoadingState(message = strings.startingApp(AppBranding.APP_NAME))
                        }
                    }
                }

                session == null -> {
                    SignInScreen(
                        sessionManager = container.sessionManager,
                        onSignedIn = { /* session StateFlow transitions automatically */ }
                    )
                }

                else -> {
                    MainAuthenticatedShell(
                        container = container,
                        deepLinkUrl = deepLinkUrl,
                        onConsumeDeepLink = onConsumeDeepLink
                    )
                }
            }
        }
    }
}

@Composable
private fun MainAuthenticatedShell(
    container: AppContainer,
    deepLinkUrl: String?,
    onConsumeDeepLink: () -> Unit
) {
    var navState by rememberSaveable(stateSaver = ShellNavStateSaver) { mutableStateOf(ShellNavState()) }
    var notificationDeepLinkTarget by remember { mutableStateOf<IssuePullTarget?>(null) }
    val unreadCount by container.unreadNotificationCount.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val openUserProfile: (String) -> Unit = { targetLogin ->
        navState = navState.openUserProfile(targetLogin)
    }

    LaunchedEffect(deepLinkUrl) {
        val raw = deepLinkUrl ?: return@LaunchedEffect
        when (val parsed = parseAppDeepLink(raw)) {
            is AppDeepLink.IssueOrPull -> {
                if (parsed.fromNotification) {
                    notificationDeepLinkTarget = parsed.target
                    navState = navState.selectBottomDestination(ShellDestination.INBOX)
                } else {
                    navState = navState.copy(overlayIssue = parsed.target, overlayGistsUser = null)
                    onConsumeDeepLink()
                }
            }
            is AppDeepLink.Repository -> {
                navState = navState.copy(overlayIssue = null, overlayGistsUser = null).openRepo(
                    RepoSummary(
                        id = "link_${parsed.owner}_${parsed.repo}",
                        name = parsed.repo,
                        owner = parsed.owner,
                        fullName = "${parsed.owner}/${parsed.repo}"
                    )
                )
                onConsumeDeepLink()
            }
            is AppDeepLink.User -> {
                navState = navState.openUserProfile(parsed.login)
                onConsumeDeepLink()
            }
            is AppDeepLink.ExternalWeb -> {
                openUrlInBrowser(parsed.url)
                onConsumeDeepLink()
            }
            null -> onConsumeDeepLink()
        }
    }

    val showMessage: (String) -> Unit = { msg ->
        scope.launch {
            snackbarHostState.showSnackbar(msg)
        }
    }

    val strings = appStrings()

    NavigationSuiteScaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        navigationSuiteItems = {
            AppDestination.entries.forEach { dest ->
                val destLabel = dest.label(strings)
                item(
                    icon = {
                        if (dest == AppDestination.INBOX && unreadCount > 0) {
                            BadgedBox(
                                badge = {
                                    Badge {
                                        Text(if (unreadCount > 99) "99+" else unreadCount.toString())
                                    }
                                }
                            ) {
                                Icon(dest.icon, contentDescription = destLabel)
                            }
                        } else {
                            Icon(dest.icon, contentDescription = destLabel)
                        }
                    },
                    label = {
                        Text(
                            text = destLabel,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    selected = !navState.hasOverlay && navState.currentDestination == dest.shell,
                    onClick = { navState = navState.selectBottomDestination(dest.shell) }
                )
            }
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                MaxxingPredictiveBackContainer(
                    enabled = !navState.hasOverlay &&
                        navState.currentDestination != ShellDestination.HOME &&
                        navState.settingsSection == null,
                    onBack = { navState = navState.popBackOrReturnHome() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    when (navState.currentDestination) {
                        ShellDestination.HOME -> HomeScreen(
                            container = container,
                            unreadCount = unreadCount,
                            onNavigateInbox = { navState = navState.selectBottomDestination(ShellDestination.INBOX) },
                            onNavigateIssuesPrs = { navState = navState.selectBottomDestination(ShellDestination.ISSUES_PRS) },
                            onNavigateRepos = { navState = navState.selectBottomDestination(ShellDestination.REPOS) },
                            onOpenGists = { navState = navState.copy(overlayGistsUser = "") },
                            onOpenUser = openUserProfile,
                            onMessage = showMessage
                        )
                        ShellDestination.INBOX -> NotificationsScreen(
                            container = container,
                            deepLinkTarget = notificationDeepLinkTarget,
                            onConsumeDeepLink = {
                                notificationDeepLinkTarget = null
                                onConsumeDeepLink()
                            },
                            onMessage = showMessage
                        )
                        ShellDestination.ISSUES_PRS -> IssuesPullsScreen(
                            container = container,
                            onMessage = showMessage
                        )
                        ShellDestination.REPOS -> ReposScreen(
                            container = container,
                            onOpenUser = openUserProfile,
                            onMessage = showMessage
                        )
                        ShellDestination.SETTINGS -> SettingsScreen(
                            container = container,
                            activeSection = navState.settingsSection,
                            onSelectSection = { section ->
                                navState = if (section != null) {
                                    navState.openSettingsSection(section)
                                } else {
                                    navState.popSettingsSection()
                                }
                            },
                            onOpenUser = openUserProfile,
                            onSignedOut = { navState = navState.selectBottomDestination(ShellDestination.HOME) },
                            onMessage = showMessage
                        )
                    }
                }

                MaxxingPredictiveOverlayHost(
                    target = navState.profileBackStack.lastOrNull(),
                    onBack = { navState = navState.copy(profileBackStack = navState.profileBackStack.dropLast(1)) },
                    contentKey = { it }
                ) { login ->
                    UserProfileScreen(
                        login = login,
                        container = container,
                        onBack = { navState = navState.copy(profileBackStack = navState.profileBackStack.dropLast(1)) },
                        onOpenRepo = { navState = navState.openRepo(it) },
                        onOpenIssueOrPull = { navState = navState.copy(overlayIssue = it) },
                        onOpenUser = { nextLogin -> navState = navState.openUserProfile(nextLogin) },
                        onOpenGists = { targetUser -> navState = navState.copy(overlayGistsUser = targetUser) },
                        onMessage = showMessage
                    )
                }

                MaxxingPredictiveOverlayHost(
                    target = navState.activeRepo,
                    onBack = { navState = navState.popRepo() },
                    contentKey = { it.fullName }
                ) { repo ->
                    RepoDetailScreen(
                        repo = repo,
                        container = container,
                        onBack = { navState = navState.popRepo() },
                        onOpenIssueOrPull = { navState = navState.copy(overlayIssue = it) },
                        onOpenRepo = { navState = navState.openRepo(it) },
                        onOpenUser = openUserProfile,
                        onMessage = showMessage
                    )
                }

                MaxxingPredictiveOverlayHost(
                    target = navState.overlayIssue,
                    onBack = { navState = navState.copy(overlayIssue = null) }
                ) { target ->
                    IssuePullDetailPane(
                        target = target,
                        container = container,
                        showBackButton = true,
                        onBack = { navState = navState.copy(overlayIssue = null) },
                        onMessage = showMessage
                    )
                }

                MaxxingPredictiveOverlayHost(
                    target = navState.overlayGistsUser,
                    onBack = { navState = navState.copy(overlayGistsUser = null) },
                    contentKey = { it }
                ) { targetUser ->
                    GistsScreen(
                        container = container,
                        userLogin = targetUser.takeIf { it.isNotBlank() },
                        onBack = { navState = navState.copy(overlayGistsUser = null) },
                        onMessage = showMessage
                    )
                }
            }
        }
    }
}
