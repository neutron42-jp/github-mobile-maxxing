package dev.maxxing.github.app.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Activity
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Building2
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.FileCode
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.Link2
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MapPin
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.UserCheck
import com.composables.icons.lucide.Users
import dev.maxxing.github.app.AppContainer
import dev.maxxing.github.app.IssuePullTarget
import dev.maxxing.github.auth.openUrlInBrowser
import dev.maxxing.github.model.ActivityEvent
import dev.maxxing.github.model.ActivityEventKind
import dev.maxxing.github.model.ContributionCalendar
import dev.maxxing.github.model.OrganizationSummary
import dev.maxxing.github.model.RepoSummary
import dev.maxxing.github.model.UserProfile
import dev.maxxing.github.model.UserSummary
import dev.maxxing.github.ui.components.MaxxingFilledIcons
import dev.maxxing.github.ui.components.ExpressiveStatusPill
import dev.maxxing.github.ui.components.FollowActionButton
import dev.maxxing.github.ui.components.MaxxingLoadingState
import dev.maxxing.github.ui.components.MaxxingWavyProgress
import dev.maxxing.github.ui.components.UserAvatar
import dev.maxxing.github.ui.i18n.AppStrings
import dev.maxxing.github.ui.i18n.appStrings
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal enum class ProfileTab {
    REPOS,
    STARRED,
    ACTIVITY,
    FOLLOWERS,
    FOLLOWING;

    fun localizedLabel(strings: AppStrings): String = when (this) {
        REPOS -> strings.profileTabRepos
        STARRED -> strings.profileTabStarred
        ACTIVITY -> strings.profileTabActivity
        FOLLOWERS -> strings.profileTabFollowers
        FOLLOWING -> strings.profileTabFollowing
    }
}

internal fun formatLocalizedProfileTabLabel(
    tab: ProfileTab,
    profile: UserProfile,
    strings: AppStrings
): String = when (tab) {
    ProfileTab.REPOS -> "${tab.localizedLabel(strings)} (${profile.publicReposCount})"
    ProfileTab.FOLLOWERS -> "${tab.localizedLabel(strings)} (${profile.followersCount})"
    ProfileTab.FOLLOWING -> "${tab.localizedLabel(strings)} (${profile.followingCount})"
    ProfileTab.STARRED, ProfileTab.ACTIVITY -> tab.localizedLabel(strings)
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class
)
@Composable
fun UserProfileScreen(
    login: String,
    container: AppContainer,
    onBack: () -> Unit,
    onOpenRepo: (RepoSummary) -> Unit,
    onOpenIssueOrPull: (IssuePullTarget) -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenGists: (String) -> Unit = {},
    onMessage: (String) -> Unit
) {
    val viewerProfile by container.viewerProfile.collectAsState()
    val session by container.sessionManager.session.collectAsState()
    val selfLogin = viewerProfile?.login ?: session?.userLogin

    var profile by remember(login) { mutableStateOf(container.repository.peekUserProfile(login)) }
    var isFollowing by remember(login) { mutableStateOf(profile?.viewerIsFollowing ?: false) }
    var orgs by remember(login) { mutableStateOf<List<OrganizationSummary>>(emptyList()) }
    var calendar by remember(login) { mutableStateOf(container.repository.peekContributionCalendar(login)) }
    var activeTab by remember(login) { mutableStateOf(ProfileTab.REPOS) }

    var repos by remember(login) { mutableStateOf(container.repository.peekUserRepositories(login).orEmpty()) }
    var starred by remember(login) { mutableStateOf<List<RepoSummary>>(emptyList()) }
    var events by remember(login) { mutableStateOf<List<ActivityEvent>>(emptyList()) }
    var followers by remember(login) { mutableStateOf<List<UserSummary>>(emptyList()) }
    var following by remember(login) { mutableStateOf<List<UserSummary>>(emptyList()) }

    var isLoading by remember(login) { mutableStateOf(profile == null) }
    var tabLoading by remember(login) { mutableStateOf(false) }
    var isRefreshing by remember(login) { mutableStateOf(false) }
    var errorMsg by remember(login) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val strings = appStrings()

    fun loadProfile(forceRefresh: Boolean = false) {
        scope.launch {
            if (profile == null) isLoading = true
            if (forceRefresh) isRefreshing = true
            errorMsg = null
            coroutineScope {
                val profJob = async { container.repository.getUserProfile(login, forceRefresh = forceRefresh) }
                val orgsJob = async { container.repository.getUserOrganizations(login, forceRefresh = forceRefresh) }
                val calJob = async { container.repository.getContributionCalendar(login, forceRefresh = forceRefresh) }

                profJob.await()
                    .onSuccess { p ->
                        profile = p
                        isFollowing = p.viewerIsFollowing
                        container.repository.getUserRepositories(login, isOrganization = p.isOrganization, forceRefresh = forceRefresh)
                            .onSuccess { repos = it }
                    }
                    .onFailure { errorMsg = strings.errorMessage(it, strings.failedLoadProfile) }

                orgsJob.await().onSuccess { orgs = it }
                calJob.await().onSuccess { calendar = it }
            }
            isLoading = false
            isRefreshing = false
        }
    }

    fun loadActiveTab(tab: ProfileTab, forceRefresh: Boolean = false) {
        val currentProfile = profile ?: return
        scope.launch {
            tabLoading = true
            when (tab) {
                ProfileTab.REPOS -> container.repository.getUserRepositories(login, currentProfile.isOrganization, forceRefresh)
                    .onSuccess { repos = it }
                ProfileTab.STARRED -> container.repository.getUserStarredRepositories(login, forceRefresh)
                    .onSuccess { starred = it }
                ProfileTab.ACTIVITY -> container.repository.getUserEvents(login, forceRefresh)
                    .onSuccess { events = it }
                ProfileTab.FOLLOWERS -> container.repository.getUserFollowers(login, forceRefresh)
                    .onSuccess { followers = it }
                ProfileTab.FOLLOWING -> container.repository.getUserFollowing(login, forceRefresh)
                    .onSuccess { following = it }
            }
            tabLoading = false
        }
    }

    LaunchedEffect(login) {
        loadProfile(forceRefresh = false)
    }

    LaunchedEffect(activeTab, profile?.login) {
        if (profile != null) {
            loadActiveTab(activeTab, forceRefresh = false)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Lucide.ArrowLeft, contentDescription = strings.back)
                    }
                    Text(
                        text = "@$login",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onOpenGists(if (selfLogin.equals(login, ignoreCase = true)) "" else login) }) {
                        Icon(Lucide.FileCode, contentDescription = strings.gistsAction, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { loadProfile(forceRefresh = true) }) {
                        Icon(Lucide.RefreshCw, contentDescription = strings.refresh, modifier = Modifier.size(18.dp))
                    }
                    if (!profile?.htmlUrl.isNullOrBlank()) {
                        IconButton(onClick = { openUrlInBrowser(profile!!.htmlUrl) }) {
                            Icon(Lucide.ExternalLink, contentDescription = strings.openInBrowser, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                if (isLoading || isRefreshing || tabLoading) {
                    MaxxingWavyProgress(modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                loadProfile(forceRefresh = true)
                loadActiveTab(activeTab, forceRefresh = true)
            },
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                isLoading && profile == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        MaxxingLoadingState(message = strings.loadingUser(login))
                    }
                }

                errorMsg != null && profile == null -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { loadProfile(forceRefresh = true) }) {
                            Text(strings.retry)
                        }
                    }
                }

                profile != null -> {
                    val p = profile!!
                    val isSelf = selfLogin.equals(p.login, ignoreCase = true)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(key = "profile_header", contentType = "header") {
                            ElevatedCard(
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        UserAvatar(p.avatarUrl, p.login, sizeDp = 64)
                                        Spacer(Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text(
                                                    text = p.name ?: "@${p.login}",
                                                    style = MaterialTheme.typography.titleLarge,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (p.isOrganization) {
                                                    ExpressiveStatusPill(
                                                        label = strings.orgBadge,
                                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "@${p.login}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (!isSelf && !p.isOrganization) {
                                            FollowActionButton(
                                                isFollowing = isFollowing,
                                                onClick = {
                                                    val next = !isFollowing
                                                    isFollowing = next
                                                    scope.launch {
                                                        container.repository.setFollowingUser(p.login, next)
                                                            .onSuccess {
                                                                onMessage(if (next) strings.followedUser(p.login) else strings.unfollowedUser(p.login))
                                                            }
                                                            .onFailure {
                                                                isFollowing = !next
                                                                onMessage(strings.errorMessage(it, strings.followUpdateFailed, strings.followScopeErrorHint))
                                                            }
                                                    }
                                                }
                                            )
                                        }
                                    }

                                    if (!p.bio.isNullOrBlank()) {
                                        Text(p.bio!!, style = MaterialTheme.typography.bodyMedium)
                                    }

                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        p.company?.takeIf { it.isNotBlank() }?.let { c ->
                                            MetaRow(Lucide.Building2, c)
                                        }
                                        p.location?.takeIf { it.isNotBlank() }?.let { loc ->
                                            MetaRow(Lucide.MapPin, loc)
                                        }
                                        p.blogUrl?.takeIf { it.isNotBlank() }?.let { blog ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.clickable {
                                                    val url = if (blog.startsWith("http")) blog else "https://$blog"
                                                    openUrlInBrowser(url)
                                                }
                                            ) {
                                                Icon(Lucide.Link2, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                                Spacer(Modifier.width(4.dp))
                                                Text(blog, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (orgs.isNotEmpty()) {
                            item(key = "orgs", contentType = "orgs") {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(strings.organizationsTitle, style = MaterialTheme.typography.titleSmall)
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(orgs, key = { it.login }) { org ->
                                            ElevatedCard(
                                                shape = MaterialTheme.shapes.medium,
                                                modifier = Modifier.clickable { onOpenUser(org.login) }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    UserAvatar(org.avatarUrl, org.login, sizeDp = 24)
                                                    Text(org.login, style = MaterialTheme.typography.labelLarge)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (!p.isOrganization && calendar != null) {
                            item(key = "profile_calendar", contentType = "calendar") {
                                ContributionHeatmapCard(calendar = calendar!!)
                            }
                        }

                        item(key = "profile_tabs", contentType = "tabs") {
                            val visibleTabs = if (p.isOrganization) {
                                listOf(ProfileTab.REPOS, ProfileTab.ACTIVITY)
                            } else {
                                ProfileTab.entries
                            }
                            val currentTab = if (activeTab in visibleTabs) activeTab else ProfileTab.REPOS
                            PrimaryScrollableTabRow(
                                selectedTabIndex = visibleTabs.indexOf(currentTab).coerceAtLeast(0),
                                containerColor = Color.Transparent,
                                edgePadding = 0.dp
                            ) {
                                visibleTabs.forEach { tab ->
                                    val icon = when (tab) {
                                        ProfileTab.REPOS -> Lucide.Folder
                                        ProfileTab.STARRED -> MaxxingFilledIcons.Star
                                        ProfileTab.ACTIVITY -> Lucide.Activity
                                        ProfileTab.FOLLOWERS -> Lucide.Users
                                        ProfileTab.FOLLOWING -> Lucide.UserCheck
                                    }
                                    Tab(
                                        selected = currentTab == tab,
                                        onClick = { activeTab = tab },
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
                                                Text(formatLocalizedProfileTabLabel(tab, p, strings), maxLines = 1, softWrap = false)
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        when (activeTab) {
                            ProfileTab.REPOS -> {
                                if (repos.isEmpty()) {
                                    item(key = "empty_repos") { ProfileEmptyNotice(strings.noPublicRepos) }
                                } else {
                                    items(repos, key = { "pr_${it.fullName}" }, contentType = { "repo" }) { repo ->
                                        RepoSummaryCard(repo = repo, onClick = { onOpenRepo(repo) })
                                    }
                                }
                            }
                            ProfileTab.STARRED -> {
                                if (starred.isEmpty()) {
                                    item(key = "empty_starred") { ProfileEmptyNotice(strings.noStarredReposProfile) }
                                } else {
                                    items(starred, key = { "ps_${it.fullName}" }, contentType = { "repo" }) { repo ->
                                        RepoSummaryCard(repo = repo, onClick = { onOpenRepo(repo) })
                                    }
                                }
                            }
                            ProfileTab.ACTIVITY -> {
                                if (events.isEmpty()) {
                                    item(key = "empty_activity") { ProfileEmptyNotice(strings.noRecentPublicActivity) }
                                } else {
                                    items(events, key = { "pe_${it.id}" }, contentType = { "event" }) { ev ->
                                        ActivityEventCard(
                                            event = ev,
                                            onClick = {
                                                val num = ev.targetIssueNumber
                                                if (num != null && ev.repoOwner.isNotBlank()) {
                                                    onOpenIssueOrPull(IssuePullTarget(ev.repoOwner, ev.repoName, num, ev.detailTitle))
                                                } else if (ev.kind == ActivityEventKind.FORK && !ev.detailTitle.isNullOrBlank() && '/' in ev.detailTitle!!) {
                                                    val forkFull = ev.detailTitle!!.trim()
                                                    onOpenRepo(
                                                        RepoSummary(
                                                            id = forkFull,
                                                            name = forkFull.substringAfter('/'),
                                                            owner = forkFull.substringBefore('/'),
                                                            fullName = forkFull,
                                                            isFork = true
                                                        )
                                                    )
                                                } else if (ev.repoOwner.isNotBlank()) {
                                                    onOpenRepo(
                                                        RepoSummary(
                                                            id = ev.repoFullName,
                                                            name = ev.repoName,
                                                            owner = ev.repoOwner,
                                                            fullName = ev.repoFullName
                                                        )
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                            ProfileTab.FOLLOWERS -> {
                                if (followers.isEmpty()) {
                                    item(key = "empty_followers") { ProfileEmptyNotice(strings.noFollowersYet) }
                                } else {
                                    items(followers, key = { "fl_${it.login}" }, contentType = { "user" }) { user ->
                                        UserSummaryCard(user = user, onClick = { onOpenUser(user.login) })
                                    }
                                }
                            }
                            ProfileTab.FOLLOWING -> {
                                if (following.isEmpty()) {
                                    item(key = "empty_following") { ProfileEmptyNotice(strings.notFollowingAnyoneYet) }
                                } else {
                                    items(following, key = { "fg_${it.login}" }, contentType = { "user" }) { user ->
                                        UserSummaryCard(user = user, onClick = { onOpenUser(user.login) })
                                    }
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
fun UserSummaryCard(
    user: UserSummary,
    onClick: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            UserAvatar(user.avatarUrl, user.login, sizeDp = 40)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "@${user.login}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (user.htmlUrl.isNotBlank()) {
                    Text(
                        text = user.htmlUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun MetaRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProfileEmptyNotice(message: String) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(20.dp)
        )
    }
}
