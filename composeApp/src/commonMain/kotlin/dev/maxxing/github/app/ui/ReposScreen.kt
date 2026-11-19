package dev.maxxing.github.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalToggleButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Activity
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ArrowLeftRight
import com.composables.icons.lucide.Bookmark
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Compass
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.File
import com.composables.icons.lucide.FileCode
import com.composables.icons.lucide.Flame
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.FolderGit2
import com.composables.icons.lucide.GitBranch
import com.composables.icons.lucide.GitCommitHorizontal
import com.composables.icons.lucide.GitFork
import com.composables.icons.lucide.GitMerge
import com.composables.icons.lucide.GitPullRequest
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Share2
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.Star
import com.composables.icons.lucide.Tag
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Users
import com.composables.icons.lucide.X
import dev.maxxing.github.app.AppContainer
import dev.maxxing.github.app.IssuePullTarget
import dev.maxxing.github.auth.openUrlInBrowser
import dev.maxxing.github.auth.shareText
import dev.maxxing.github.model.BranchCompareResult
import dev.maxxing.github.model.CodeSearchHit
import dev.maxxing.github.model.CommitDetail
import dev.maxxing.github.model.CommitSummary
import dev.maxxing.github.model.DiscussionDetail
import dev.maxxing.github.model.DiscussionListData
import dev.maxxing.github.model.FileContent
import dev.maxxing.github.model.GlobalSearchKind
import dev.maxxing.github.model.IssueOrPullKind
import dev.maxxing.github.model.IssueOrPullState
import dev.maxxing.github.model.IssueOrPullSummary
import dev.maxxing.github.model.IssueStateFilter
import dev.maxxing.github.model.LabelTag
import dev.maxxing.github.model.MilestoneSummary
import dev.maxxing.github.model.PullFileChange
import dev.maxxing.github.model.ReleaseSummary
import dev.maxxing.github.model.RepoDetail
import dev.maxxing.github.model.RepoInsightsData
import dev.maxxing.github.model.RepoParentSummary
import dev.maxxing.github.model.RepoSecurityOverview
import dev.maxxing.github.model.RepoSummary
import dev.maxxing.github.model.resolveRepoCapabilities
import dev.maxxing.github.model.resolveRepoWebUrl
import dev.maxxing.github.model.TagRef
import dev.maxxing.github.model.TreeEntry
import dev.maxxing.github.model.TreeEntryType
import dev.maxxing.github.model.UserSummary
import dev.maxxing.github.model.WorkflowJobSummary
import dev.maxxing.github.model.WorkflowRunSummary
import dev.maxxing.github.model.WorkflowSummary
import dev.maxxing.github.ui.components.CodeViewer
import dev.maxxing.github.ui.components.MaxxingIconLabelTab
import dev.maxxing.github.ui.components.ExpressiveStatusPill
import dev.maxxing.github.ui.components.GitHubLabelChip
import dev.maxxing.github.ui.components.GitHubMarkdown
import dev.maxxing.github.ui.components.GitHubMarkdownPreNormalized
import dev.maxxing.github.ui.components.MaxxingDropdownSelector
import dev.maxxing.github.ui.components.MaxxingFilledIcons
import dev.maxxing.github.ui.components.MaxxingFullScreenDiffViewer
import dev.maxxing.github.ui.components.MaxxingFullScreenEditorScaffold
import dev.maxxing.github.ui.components.MaxxingLoadingState
import dev.maxxing.github.ui.components.MaxxingPredictiveBackContainer
import dev.maxxing.github.ui.components.MaxxingPredictiveOverlayHost
import dev.maxxing.github.ui.components.MaxxingStarAmber
import dev.maxxing.github.ui.components.RepoLabelsPickerDialog
import dev.maxxing.github.ui.i18n.AppStrings
import dev.maxxing.github.ui.i18n.appStrings
import dev.maxxing.github.ui.components.MaxxingTopHeaderDefaults
import dev.maxxing.github.ui.components.MaxxingWavyProgress
import dev.maxxing.github.ui.components.StarActionButton
import dev.maxxing.github.ui.components.UserAvatar
import dev.maxxing.github.ui.components.diffFileLazyItems
import dev.maxxing.github.ui.components.expressiveAnimateContentSize
import dev.maxxing.github.ui.components.formatCompactCount
import dev.maxxing.github.ui.components.parseHexColor
import dev.maxxing.github.ui.components.rememberNormalizedMarkdownSections
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal enum class RepoListTab {
    MINE,
    STARRED,
    SEARCH
}

@Composable
internal fun RepoListTabRow(
    activeTab: RepoListTab,
    onSelectTab: (RepoListTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = appStrings()
    PrimaryTabRow(
        selectedTabIndex = RepoListTab.entries.indexOf(activeTab).coerceAtLeast(0),
        containerColor = Color.Transparent,
        modifier = modifier
    ) {
        RepoListTab.entries.forEach { tab ->
            val label = when (tab) {
                RepoListTab.MINE -> strings.repoTabMine
                RepoListTab.STARRED -> strings.repoTabStarred
                RepoListTab.SEARCH -> strings.repoTabExplore
            }
            val icon = when (tab) {
                RepoListTab.MINE -> Lucide.FolderGit2
                RepoListTab.STARRED -> MaxxingFilledIcons.Star
                RepoListTab.SEARCH -> Lucide.Compass
            }
            MaxxingIconLabelTab(
                selected = activeTab == tab,
                onClick = { onSelectTab(tab) },
                icon = icon,
                label = label
            )
        }
    }
}

internal enum class RepoDetailTab {
    CODE,
    ISSUES,
    PULLS,
    ACTIONS,
    RELEASES,
    DISCUSSIONS,
    SECURITY,
    INSIGHTS;

    fun localizedLabel(strings: AppStrings): String = when (this) {
        CODE -> strings.repoDetailTabCode
        ISSUES -> strings.repoDetailTabIssues
        PULLS -> strings.repoDetailTabPulls
        ACTIONS -> strings.repoDetailTabActions
        RELEASES -> strings.repoDetailTabReleases
        DISCUSSIONS -> strings.repoDetailTabDiscussions
        SECURITY -> strings.repoDetailTabSecurity
        INSIGHTS -> strings.repoDetailTabInsights
    }
}

internal fun formatLocalizedRepoDetailTabLabel(
    tab: RepoDetailTab,
    openIssuesCount: Int?,
    openPullRequestsCount: Int?,
    strings: AppStrings
): String {
    val base = tab.localizedLabel(strings)
    return when (tab) {
        RepoDetailTab.ISSUES -> openIssuesCount?.let { "$base ($it)" } ?: base
        RepoDetailTab.PULLS -> openPullRequestsCount?.let { "$base ($it)" } ?: base
        else -> base
    }
}

private val TRENDING_LANGUAGES = listOf("All", "Kotlin", "TypeScript", "Python", "Rust", "Go", "Swift")

@Composable
fun ReposScreen(
    container: AppContainer,
    isTabActive: Boolean = true,
    onOpenUser: (String) -> Unit = {},
    onMessage: (String) -> Unit
) {
    var repoStack by remember { mutableStateOf<List<Pair<RepoSummary, String?>>>(emptyList()) }
    var selectedIssueTarget by remember { mutableStateOf<IssuePullTarget?>(null) }

    fun pushRepo(repo: RepoSummary, initialFilePath: String? = null) {
        val currentTop = repoStack.lastOrNull()
        repoStack = if (currentTop != null && currentTop.first.fullName.equals(repo.fullName, ignoreCase = true) && initialFilePath == null) {
            repoStack.dropLast(1) + (repo to currentTop.second)
        } else {
            repoStack + (repo to initialFilePath)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        RepoListPane(
            container = container,
            onSelectRepo = { pushRepo(it) },
            onSelectCodeHit = { hit ->
                pushRepo(
                    repo = RepoSummary(
                        id = "code_${hit.repoFullName}",
                        name = hit.repoName,
                        owner = hit.repoOwner,
                        fullName = hit.repoFullName
                    ),
                    initialFilePath = hit.path
                )
            },
            onSelectIssuePull = { selectedIssueTarget = it },
            onSelectUser = onOpenUser,
            onMessage = onMessage
        )

        MaxxingPredictiveOverlayHost(
            target = repoStack.lastOrNull(),
            onBack = { repoStack = repoStack.dropLast(1) },
            backEnabled = isTabActive && selectedIssueTarget == null,
            contentKey = { it.first.fullName }
        ) { (repo, initialPath) ->
            RepoDetailScreen(
                repo = repo,
                container = container,
                initialFilePath = initialPath,
                backEnabled = isTabActive && selectedIssueTarget == null,
                onBack = { repoStack = repoStack.dropLast(1) },
                onOpenIssueOrPull = { selectedIssueTarget = it },
                onOpenRepo = { nextRepo -> pushRepo(nextRepo) },
                onOpenUser = onOpenUser,
                onMessage = onMessage
            )
        }

        MaxxingPredictiveOverlayHost(
            target = selectedIssueTarget,
            onBack = { selectedIssueTarget = null },
            backEnabled = isTabActive
        ) { target ->
            IssuePullDetailPane(
                target = target,
                container = container,
                showBackButton = true,
                onBack = { selectedIssueTarget = null },
                onMessage = onMessage
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepoListPane(
    container: AppContainer,
    onSelectRepo: (RepoSummary) -> Unit,
    onSelectCodeHit: (CodeSearchHit) -> Unit,
    onSelectIssuePull: (IssuePullTarget) -> Unit,
    onSelectUser: (String) -> Unit,
    onMessage: (String) -> Unit
) {
    var activeTab by remember { mutableStateOf(RepoListTab.MINE) }
    var searchKind by remember { mutableStateOf(GlobalSearchKind.REPOSITORIES) }
    var searchQuery by remember { mutableStateOf("") }
    var submittedSearchQuery by remember { mutableStateOf("") }
    var trendingLang by remember { mutableStateOf("All") }

    var repos by remember {
        mutableStateOf(container.repository.peekViewerRepositories().orEmpty())
    }
    var issueHits by remember { mutableStateOf<List<IssueOrPullSummary>>(emptyList()) }
    var codeHits by remember { mutableStateOf<List<CodeSearchHit>>(emptyList()) }
    var userHits by remember { mutableStateOf<List<UserSummary>>(emptyList()) }
    var trendingRepos by remember {
        mutableStateOf(container.repository.peekTrendingRepositories(null).orEmpty())
    }

    var isLoading by remember { mutableStateOf(repos.isEmpty()) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var requestSeq by remember { mutableStateOf(0) }
    var showCreateRepoDialog by remember { mutableStateOf(false) }
    var newRepoName by remember { mutableStateOf("") }
    var newRepoDesc by remember { mutableStateOf("") }
    var newRepoPrivate by remember { mutableStateOf(false) }
    var newRepoAutoInit by remember { mutableStateOf(true) }
    var createRepoBusy by remember { mutableStateOf(false) }
    var isSearchFocused by remember { mutableStateOf(false) }
    val strings = appStrings()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val headerTracker = remember { RepoHeaderCollapseTracker() }
    val headerNestedScroll = remember(headerTracker) {
        headerTracker.toNestedScrollConnection { !(activeTab == RepoListTab.SEARCH && isSearchFocused) }
    }
    val showExpandedHeader = !headerTracker.isCollapsed || (activeTab == RepoListTab.SEARCH && isSearchFocused)
    val headerVerticalPadding by animateDpAsState(
        if (showExpandedHeader) MaxxingTopHeaderDefaults.ExpandedVerticalPadding
        else MaxxingTopHeaderDefaults.CollapsedVerticalPadding
    )

    LaunchedEffect(activeTab, searchKind) {
        headerTracker.expand()
    }

    fun loadTrending(langLabel: String = trendingLang, forceRefresh: Boolean = false) {
        val currentSeq = ++requestSeq
        val apiLang = langLabel.takeUnless { it == "All" }
        scope.launch {
            val cached = container.repository.peekTrendingRepositories(apiLang)
            if (cached != null) {
                trendingRepos = cached
                isLoading = false
            } else {
                trendingRepos = emptyList()
                isLoading = true
            }
            if (forceRefresh) isRefreshing = true
            errorMsg = null
            val res = container.repository.getTrendingRepositories(apiLang, forceRefresh = forceRefresh)
            if (requestSeq != currentSeq || activeTab != RepoListTab.SEARCH || submittedSearchQuery.isNotBlank()) {
                return@launch
            }
            res.onSuccess {
                trendingRepos = it
                isLoading = false
                isRefreshing = false
            }.onFailure {
                errorMsg = strings.errorMessage(it, strings.failedLoadTrending)
                isLoading = false
                isRefreshing = false
            }
        }
    }

    fun loadRepos(
        forceRefresh: Boolean = false,
        targetTab: RepoListTab = activeTab,
        targetSearchKind: GlobalSearchKind = searchKind
    ) {
        val trimmedQuery = searchQuery.trim()
        if (targetTab == RepoListTab.SEARCH) {
            submittedSearchQuery = trimmedQuery
            if (trimmedQuery.isEmpty()) {
                loadTrending(trendingLang, forceRefresh = forceRefresh)
                return
            }
        }

        val currentSeq = ++requestSeq
        scope.launch {
            when (targetTab) {
                RepoListTab.MINE -> {
                    val cached = container.repository.peekViewerRepositories()
                    if (cached != null) { repos = cached; isLoading = false } else { repos = emptyList(); isLoading = true }
                }
                RepoListTab.STARRED -> {
                    val cached = container.repository.peekStarredRepositories()
                    if (cached != null) { repos = cached; isLoading = false } else { repos = emptyList(); isLoading = true }
                }
                RepoListTab.SEARCH -> when (targetSearchKind) {
                    GlobalSearchKind.REPOSITORIES -> {
                        val cached = container.repository.peekSearchRepositories(trimmedQuery)
                        if (cached != null) { repos = cached; isLoading = false } else { repos = emptyList(); isLoading = true }
                    }
                    GlobalSearchKind.ISSUES_PRS -> {
                        val cached = container.repository.peekSearchIssuesAndPullsGlobal(trimmedQuery)
                        if (cached != null) { issueHits = cached; isLoading = false } else { issueHits = emptyList(); isLoading = true }
                    }
                    GlobalSearchKind.CODE -> {
                        val cached = container.repository.peekSearchCode(trimmedQuery)
                        if (cached != null) { codeHits = cached; isLoading = false } else { codeHits = emptyList(); isLoading = true }
                    }
                    GlobalSearchKind.USERS -> {
                        val cached = container.repository.peekSearchUsers(trimmedQuery)
                        if (cached != null) { userHits = cached; isLoading = false } else { userHits = emptyList(); isLoading = true }
                    }
                }
            }
            if (forceRefresh) isRefreshing = true
            errorMsg = null

            when (targetTab) {
                RepoListTab.MINE -> container.repository.getViewerRepositories(forceRefresh = forceRefresh)
                    .also { if (requestSeq != currentSeq || activeTab != targetTab) return@launch }
                    .onSuccess { repos = it; isLoading = false; isRefreshing = false }
                    .onFailure { errorMsg = strings.errorMessage(it, strings.failedLoadRepos); isLoading = false; isRefreshing = false }
                RepoListTab.STARRED -> container.repository.getStarredRepositories(forceRefresh = forceRefresh)
                    .also { if (requestSeq != currentSeq || activeTab != targetTab) return@launch }
                    .onSuccess { repos = it; isLoading = false; isRefreshing = false }
                    .onFailure { errorMsg = strings.errorMessage(it, strings.failedLoadStarredRepos); isLoading = false; isRefreshing = false }
                RepoListTab.SEARCH -> when (targetSearchKind) {
                    GlobalSearchKind.REPOSITORIES -> container.repository.searchRepositories(trimmedQuery, forceRefresh = forceRefresh)
                        .also { if (requestSeq != currentSeq || activeTab != targetTab || searchKind != targetSearchKind) return@launch }
                        .onSuccess { repos = it; isLoading = false; isRefreshing = false }
                        .onFailure { errorMsg = strings.errorMessage(it, strings.repoSearchFailed); isLoading = false; isRefreshing = false }
                    GlobalSearchKind.ISSUES_PRS -> container.repository.searchIssuesAndPullsGlobal(trimmedQuery, forceRefresh = forceRefresh)
                        .also { if (requestSeq != currentSeq || activeTab != targetTab || searchKind != targetSearchKind) return@launch }
                        .onSuccess { issueHits = it; isLoading = false; isRefreshing = false }
                        .onFailure { errorMsg = strings.errorMessage(it, strings.issueSearchFailed); isLoading = false; isRefreshing = false }
                    GlobalSearchKind.CODE -> container.repository.searchCode(trimmedQuery, forceRefresh = forceRefresh)
                        .also { if (requestSeq != currentSeq || activeTab != targetTab || searchKind != targetSearchKind) return@launch }
                        .onSuccess { codeHits = it; isLoading = false; isRefreshing = false }
                        .onFailure { errorMsg = strings.errorMessage(it, strings.codeSearchFailed); isLoading = false; isRefreshing = false }
                    GlobalSearchKind.USERS -> container.repository.searchUsers(trimmedQuery, forceRefresh = forceRefresh)
                        .also { if (requestSeq != currentSeq || activeTab != targetTab || searchKind != targetSearchKind) return@launch }
                        .onSuccess { userHits = it; isLoading = false; isRefreshing = false }
                        .onFailure { errorMsg = strings.errorMessage(it, strings.userSearchFailed); isLoading = false; isRefreshing = false }
                }
            }
        }
    }

    LaunchedEffect(activeTab, searchKind, trendingLang) {
        if (activeTab != RepoListTab.SEARCH) {
            loadRepos(forceRefresh = false, targetTab = activeTab)
        } else if (searchQuery.isNotBlank()) {
            loadRepos(forceRefresh = false, targetTab = RepoListTab.SEARCH, targetSearchKind = searchKind)
        } else {
            submittedSearchQuery = ""
            loadTrending(trendingLang, forceRefresh = false)
        }
    }

    val hasCurrentItems = when {
        activeTab != RepoListTab.SEARCH -> repos.isNotEmpty()
        submittedSearchQuery.isBlank() -> trendingRepos.isNotEmpty()
        searchKind == GlobalSearchKind.REPOSITORIES -> repos.isNotEmpty()
        searchKind == GlobalSearchKind.ISSUES_PRS -> issueHits.isNotEmpty()
        searchKind == GlobalSearchKind.CODE -> codeHits.isNotEmpty()
        searchKind == GlobalSearchKind.USERS -> userHits.isNotEmpty()
        else -> false
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Column(modifier = Modifier.padding(horizontal = MaxxingTopHeaderDefaults.HorizontalPadding, vertical = headerVerticalPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = MaxxingTopHeaderDefaults.TitleRowMinHeight),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (activeTab == RepoListTab.SEARCH && submittedSearchQuery.isBlank()) strings.exploreTitle else strings.repositoriesTitle,
                    style = if (showExpandedHeader) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                FilledTonalButton(
                    onClick = { showCreateRepoDialog = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Lucide.Plus, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.newAction)
                }
                IconButton(onClick = { loadRepos(forceRefresh = true) }) {
                    Icon(Lucide.RefreshCw, contentDescription = strings.refresh, modifier = Modifier.size(18.dp))
                }
            }
            MaxxingCollapsibleSection(visible = showExpandedHeader) {
                Column {
                    Spacer(Modifier.height(MaxxingTopHeaderDefaults.TabBarTopSpacing))
                    RepoListTabRow(
                        activeTab = activeTab,
                        onSelectTab = { activeTab = it }
                    )
                    if (activeTab == RepoListTab.SEARCH) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = strings.searchInLabel,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f)
                            )
                            MaxxingDropdownSelector(
                                options = GlobalSearchKind.entries,
                                selected = searchKind,
                                onSelect = { searchKind = it },
                                labelProvider = { strings.searchKindLabel(it) },
                                iconProvider = {
                                    when (it) {
                                        GlobalSearchKind.REPOSITORIES -> Lucide.Folder
                                        GlobalSearchKind.ISSUES_PRS -> Lucide.CircleDot
                                        GlobalSearchKind.CODE -> Lucide.FileCode
                                        GlobalSearchKind.USERS -> Lucide.Users
                                    }
                                }
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = {
                                    Text(
                                        when (searchKind) {
                                            GlobalSearchKind.REPOSITORIES -> strings.searchReposPlaceholder
                                            GlobalSearchKind.ISSUES_PRS -> strings.searchIssuesPlaceholder
                                            GlobalSearchKind.CODE -> strings.searchCodePlaceholder
                                            GlobalSearchKind.USERS -> strings.searchUsersPlaceholder
                                        }
                                    )
                                },
                                leadingIcon = { Icon(Lucide.Search, contentDescription = null) },
                                trailingIcon = if (searchQuery.isNotEmpty()) {
                                    {
                                        IconButton(
                                            onClick = {
                                                searchQuery = ""
                                                submittedSearchQuery = ""
                                                errorMsg = null
                                                loadTrending(trendingLang, forceRefresh = false)
                                            }
                                        ) {
                                            Icon(Lucide.X, contentDescription = strings.clear, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                } else null,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        focusManager.clearFocus()
                                        if (searchQuery.isNotBlank()) {
                                            loadRepos(forceRefresh = true, targetTab = RepoListTab.SEARCH)
                                        }
                                    }
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .onFocusChanged { isSearchFocused = it.isFocused }
                                    .onPreviewKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                                            focusManager.clearFocus()
                                            if (searchQuery.isNotBlank()) {
                                                loadRepos(forceRefresh = true, targetTab = RepoListTab.SEARCH)
                                            }
                                            true
                                        } else {
                                            false
                                        }
                                    }
                            )
                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    loadRepos(forceRefresh = true, targetTab = RepoListTab.SEARCH)
                                },
                                enabled = searchQuery.isNotBlank()
                            ) {
                                Text(strings.searchAction)
                            }
                        }
                        if (submittedSearchQuery.isBlank()) {
                            Spacer(Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(TRENDING_LANGUAGES, key = { it }) { lang ->
                                    FilterChip(
                                        selected = trendingLang == lang,
                                        onClick = { trendingLang = lang },
                                        leadingIcon = if (lang == "All") {
                                            { Icon(Lucide.Flame, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                        } else null,
                                        label = { Text(if (lang == "All") strings.trendingAll else lang) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if ((isLoading || isRefreshing) && hasCurrentItems) {
                Spacer(Modifier.height(8.dp))
                MaxxingWavyProgress()
            }
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing && hasCurrentItems,
            onRefresh = { loadRepos(forceRefresh = true) },
            modifier = Modifier.fillMaxSize().nestedScroll(headerNestedScroll)
        ) {
            when {
                isLoading && !hasCurrentItems -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        MaxxingLoadingState(
                            message = if (activeTab == RepoListTab.SEARCH && submittedSearchQuery.isBlank()) {
                                strings.loadingTrending
                            } else {
                                strings.loadingResults
                            }
                        )
                    }
                }

                errorMsg != null && !hasCurrentItems -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { loadRepos(forceRefresh = true) }) {
                            Text(strings.retry)
                        }
                    }
                }

                !hasCurrentItems -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = when (activeTab) {
                                RepoListTab.SEARCH -> if (submittedSearchQuery.isBlank()) {
                                    strings.noTrendingFound
                                } else {
                                    strings.noSearchMatches(strings.searchKindLabel(searchKind), submittedSearchQuery)
                                }
                                RepoListTab.STARRED -> strings.noStarredRepos
                                RepoListTab.MINE -> strings.noReposFound
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                activeTab == RepoListTab.SEARCH && submittedSearchQuery.isBlank() -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = trendingRepos,
                            key = { "trending_${it.id}" },
                            contentType = { "repo_summary" }
                        ) { repo ->
                            RepoSummaryCard(repo = repo, onClick = { onSelectRepo(repo) })
                        }
                    }
                }

                activeTab == RepoListTab.SEARCH && searchKind == GlobalSearchKind.ISSUES_PRS -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = issueHits,
                            key = { "issue_${it.id}" },
                            contentType = { "issue_search_hit" }
                        ) { item ->
                            SearchIssuePullCard(
                                item = item,
                                onOpenUser = onSelectUser,
                                onClick = {
                                    onSelectIssuePull(
                                        IssuePullTarget(
                                            owner = item.repoOwner,
                                            repo = item.repoName,
                                            number = item.number,
                                            hintTitle = item.title
                                        )
                                    )
                                }
                            )
                        }
                    }
                }

                activeTab == RepoListTab.SEARCH && searchKind == GlobalSearchKind.CODE -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = codeHits,
                            key = { "${it.repoFullName}:${it.path}:${it.sha}" },
                            contentType = { "code_search_hit" }
                        ) { hit ->
                            CodeSearchHitCard(hit = hit, onClick = { onSelectCodeHit(hit) })
                        }
                    }
                }

                activeTab == RepoListTab.SEARCH && searchKind == GlobalSearchKind.USERS -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = userHits,
                            key = { "user_${it.login}" },
                            contentType = { "user_search_hit" }
                        ) { user ->
                            SearchUserCard(user = user, onClick = { onSelectUser(user.login) })
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = repos,
                            key = { it.id },
                            contentType = { "repo_summary" }
                        ) { repo ->
                            RepoSummaryCard(repo = repo, onClick = { onSelectRepo(repo) })
                        }
                    }
                }
            }
        }
    }

    if (showCreateRepoDialog) {
        AlertDialog(
            onDismissRequest = { if (!createRepoBusy) showCreateRepoDialog = false },
            title = { Text(strings.createRepoTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newRepoName,
                        onValueChange = { newRepoName = it },
                        label = { Text(strings.repoNameLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newRepoDesc,
                        onValueChange = { newRepoDesc = it },
                        label = { Text(strings.repoDescLabel) },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Lucide.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(strings.privateRepo, modifier = Modifier.weight(1f))
                        Switch(checked = newRepoPrivate, onCheckedChange = { newRepoPrivate = it })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Lucide.File, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(strings.initWithReadme, modifier = Modifier.weight(1f))
                        Switch(checked = newRepoAutoInit, onCheckedChange = { newRepoAutoInit = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = newRepoName.isNotBlank() && !createRepoBusy,
                    onClick = {
                        val rName = newRepoName.trim()
                        val rDesc = newRepoDesc.trim()
                        createRepoBusy = true
                        scope.launch {
                            container.repository.createRepository(rName, rDesc, newRepoPrivate, newRepoAutoInit)
                                .onSuccess { created ->
                                    createRepoBusy = false
                                    showCreateRepoDialog = false
                                    newRepoName = ""
                                    newRepoDesc = ""
                                    onMessage(strings.createdRepoMessage(created.fullName))
                                    onSelectRepo(created)
                                }
                                .onFailure {
                                    createRepoBusy = false
                                    onMessage(strings.errorMessage(it, strings.failedCreateRepo))
                                }
                        }
                    }
                ) {
                    Text(if (createRepoBusy) strings.creating else strings.create)
                }
            },
            dismissButton = {
                TextButton(enabled = !createRepoBusy, onClick = { showCreateRepoDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

@Composable
private fun SearchIssuePullCard(
    item: IssueOrPullSummary,
    onOpenUser: (String) -> Unit,
    onClick: () -> Unit
) {
    val strings = appStrings()
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = if (item.kind == IssueOrPullKind.PULL_REQUEST) Lucide.GitPullRequest else Lucide.CircleDot,
                    contentDescription = null,
                    tint = if (item.state == IssueOrPullState.OPEN) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    },
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "${item.repoFullName} #${item.number}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                ExpressiveStatusPill(
                    label = when {
                        item.isDraft -> strings.statusDraft
                        item.state == IssueOrPullState.OPEN -> strings.statusOpen
                        item.state == IssueOrPullState.MERGED -> strings.statusMerged
                        else -> strings.statusClosed
                    },
                    icon = when {
                        item.isDraft -> Lucide.Pencil
                        item.state == IssueOrPullState.MERGED -> Lucide.GitMerge
                        item.state == IssueOrPullState.CLOSED -> Lucide.CircleCheck
                        item.kind == IssueOrPullKind.PULL_REQUEST -> Lucide.GitPullRequest
                        else -> Lucide.CircleDot
                    },
                    containerColor = if (item.state == IssueOrPullState.OPEN) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    contentColor = if (item.state == IssueOrPullState.OPEN) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                )
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UserAvatar(
                    avatarUrl = item.author.avatarUrl,
                    login = item.author.login,
                    sizeDp = 20,
                    modifier = Modifier.clickable { onOpenUser(item.author.login) }
                )
                Text(
                    text = "@${item.author.login}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onOpenUser(item.author.login) }
                )
            }
        }
    }
}

@Composable
private fun CodeSearchHitCard(
    hit: CodeSearchHit,
    onClick: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Lucide.FileCode,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = hit.path,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = hit.repoFullName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            hit.fragments.firstOrNull()?.let { snippet ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = snippet.trim(),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchUserCard(
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
                Text("@${user.login}", style = MaterialTheme.typography.titleMedium)
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
            Icon(Lucide.Users, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun RepoSummaryCard(
    repo: RepoSummary,
    onClick: () -> Unit
) {
    val strings = appStrings()
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(repo.ownerAvatarUrl, repo.owner, sizeDp = 24)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = repo.fullName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (repo.isPrivate) {
                    ExpressiveStatusPill(
                        label = strings.privateBadge,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        icon = Lucide.Lock
                    )
                }
            }

            if (!repo.description.isNullOrBlank()) {
                Text(
                    text = repo.description!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                repo.language?.let { lang ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(parseHexColor(repo.languageColorHex ?: "6E40C9"))
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(lang, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (repo.viewerHasStarred) MaxxingFilledIcons.Star else Lucide.Star,
                        contentDescription = if (repo.viewerHasStarred) strings.starred else strings.stars,
                        tint = if (repo.viewerHasStarred) MaxxingStarAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = formatCompactCount(repo.stargazersCount),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (repo.viewerHasStarred) FontWeight.Bold else FontWeight.Medium,
                        color = if (repo.viewerHasStarred) MaxxingStarAmber else MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Lucide.GitFork, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(formatCompactCount(repo.forksCount), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

private enum class RepoPeopleTab {
    STARGAZERS,
    FORKS,
    CONTRIBUTORS;

    fun localizedLabel(strings: AppStrings): String = when (this) {
        STARGAZERS -> strings.repoPeopleTabStars
        FORKS -> strings.repoPeopleTabForks
        CONTRIBUTORS -> strings.repoPeopleTabContributors
    }
}

@OptIn(
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun RepoDetailScreen(
    repo: RepoSummary,
    container: AppContainer,
    initialFilePath: String? = null,
    backEnabled: Boolean = true,
    onBack: () -> Unit,
    onOpenIssueOrPull: (IssuePullTarget) -> Unit,
    onOpenRepo: (RepoSummary) -> Unit = {},
    onOpenUser: (String) -> Unit = {},
    onMessage: (String) -> Unit
) {
    val strings = appStrings()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val viewerProfile by container.viewerProfile.collectAsState()
    val pinnedRepos by container.sessionManager.pinnedRepos.collectAsState()
    val isPinned = pinnedRepos.any { it.equals(repo.fullName, ignoreCase = true) }
    var detail by remember(repo.fullName) {
        mutableStateOf(container.repository.peekRepoDetail(repo.owner, repo.name))
    }
    var currentRef by remember(repo.fullName) { mutableStateOf(detail?.summary?.defaultBranch ?: repo.defaultBranch) }
    var isStarred by remember(repo.fullName) { mutableStateOf(detail?.summary?.viewerHasStarred ?: repo.viewerHasStarred) }
    var isWatching by remember(repo.fullName) {
        mutableStateOf(container.repository.peekRepoSubscription(repo.owner, repo.name) ?: false)
    }
    var forkBusy by remember(repo.fullName) { mutableStateOf(false) }
    var activeTab by remember(repo.fullName) { mutableStateOf(RepoDetailTab.CODE) }
    var showCommitsView by remember(repo.fullName) { mutableStateOf(false) }

    var currentPath by remember(repo.fullName) { mutableStateOf("") }
    var entries by remember(repo.fullName) {
        mutableStateOf(container.repository.peekDirectoryContents(repo.owner, repo.name, "", currentRef).orEmpty())
    }
    var openedFile by remember(repo.fullName) { mutableStateOf<FileContent?>(null) }
    var readme by remember(repo.fullName) {
        mutableStateOf(container.repository.peekReadme(repo.owner, repo.name, currentRef))
    }

    var issueStateFilter by remember(repo.fullName) { mutableStateOf(IssueStateFilter.OPEN) }
    var pullStateFilter by remember(repo.fullName) { mutableStateOf(IssueStateFilter.OPEN) }
    var repoIssues by remember(repo.fullName) {
        mutableStateOf(
            container.repository.peekRepoIssuesOrPulls(repo.owner, repo.name, pullRequests = false, stateFilter = IssueStateFilter.OPEN)
        )
    }
    var repoPulls by remember(repo.fullName) {
        mutableStateOf(
            container.repository.peekRepoIssuesOrPulls(repo.owner, repo.name, pullRequests = true, stateFilter = IssueStateFilter.OPEN)
        )
    }
    var issuesLoading by remember(repo.fullName) { mutableStateOf(false) }
    var pullsLoading by remember(repo.fullName) { mutableStateOf(false) }
    var issuesError by remember(repo.fullName) { mutableStateOf<String?>(null) }
    var pullsError by remember(repo.fullName) { mutableStateOf<String?>(null) }
    // Only the latest issues/pulls request may update the state above.
    val issuesRequestGuard = remember(repo.fullName) { LatestRequestGuard() }
    val pullsRequestGuard = remember(repo.fullName) { LatestRequestGuard() }
    var issuesSection by remember(repo.fullName) { mutableStateOf(RepoIssuesSection.ISSUES) }
    var repoLabels by remember(repo.fullName) {
        mutableStateOf(container.repository.peekRepoLabels(repo.owner, repo.name))
    }
    var labelsLoading by remember(repo.fullName) { mutableStateOf(false) }
    var labelsError by remember(repo.fullName) { mutableStateOf<String?>(null) }
    var showLabelEditorDialog by remember(repo.fullName) { mutableStateOf(false) }
    var editingLabel by remember(repo.fullName) { mutableStateOf<LabelTag?>(null) }
    var labelEditorSubmitting by remember(repo.fullName) { mutableStateOf(false) }
    var labelEditorError by remember(repo.fullName) { mutableStateOf<String?>(null) }
    var deletingLabelTarget by remember(repo.fullName) { mutableStateOf<LabelTag?>(null) }
    var labelDeleteBusy by remember(repo.fullName) { mutableStateOf(false) }
    var repoMilestones by remember(repo.fullName) {
        mutableStateOf(container.repository.peekRepoMilestones(repo.owner, repo.name, state = "all"))
    }
    var milestonesLoading by remember(repo.fullName) { mutableStateOf(false) }
    var milestonesError by remember(repo.fullName) { mutableStateOf<String?>(null) }
    var milestoneActionBusy by remember(repo.fullName) { mutableStateOf(false) }
    var showMilestoneEditorDialog by remember(repo.fullName) { mutableStateOf(false) }
    var editingMilestone by remember(repo.fullName) { mutableStateOf<MilestoneSummary?>(null) }
    var milestoneEditorSubmitting by remember(repo.fullName) { mutableStateOf(false) }
    var milestoneEditorError by remember(repo.fullName) { mutableStateOf<String?>(null) }
    var deletingMilestoneTarget by remember(repo.fullName) { mutableStateOf<MilestoneSummary?>(null) }
    var milestoneDeleteBusy by remember(repo.fullName) { mutableStateOf(false) }
    var isLoading by remember(repo.fullName) { mutableStateOf(detail == null && entries.isEmpty()) }
    var showCreatePrDialog by remember(repo.fullName) { mutableStateOf(false) }
    var creatingPr by remember(repo.fullName) { mutableStateOf(false) }
    var createPrError by remember(repo.fullName) { mutableStateOf<String?>(null) }

    var showFileEditorDialog by remember(repo.fullName) { mutableStateOf(false) }
    var editingExistingFile by remember(repo.fullName) { mutableStateOf<FileContent?>(null) }
    var showDeleteFileDialog by remember(repo.fullName) { mutableStateOf(false) }
    var fileMutationBusy by remember(repo.fullName) { mutableStateOf(false) }
    var fileMutationError by remember(repo.fullName) { mutableStateOf<String?>(null) }

    var showBranchMenu by remember(repo.fullName) { mutableStateOf(false) }
    var showShareMenu by remember(repo.fullName) { mutableStateOf(false) }
    var showCreateIssueDialog by remember(repo.fullName) { mutableStateOf(false) }
    var newIssueTitle by remember(repo.fullName) { mutableStateOf("") }
    var newIssueBody by remember(repo.fullName) { mutableStateOf("") }
    var creatingIssue by remember(repo.fullName) { mutableStateOf(false) }
    var createIssueError by remember(repo.fullName) { mutableStateOf<String?>(null) }
    var showIssueLabelPicker by remember(repo.fullName) { mutableStateOf(false) }
    var availableIssueLabels by remember(repo.fullName) { mutableStateOf<List<LabelTag>>(emptyList()) }
    var issueLabelsLoading by remember(repo.fullName) { mutableStateOf(false) }
    var issueLabelActionBusy by remember(repo.fullName) { mutableStateOf(false) }
    var selectedNewIssueLabels by remember(repo.fullName) { mutableStateOf<Set<String>>(emptySet()) }
    var newIssueLabelName by remember(repo.fullName) { mutableStateOf("") }

    var showPeopleDialog by remember(repo.fullName) { mutableStateOf(false) }
    var peopleTab by remember(repo.fullName) { mutableStateOf(RepoPeopleTab.STARGAZERS) }
    var forksSort by remember(repo.fullName) { mutableStateOf("stargazers") }
    var stargazersList by remember(repo.fullName) { mutableStateOf<List<UserSummary>?>(null) }
    var forksList by remember(repo.fullName) { mutableStateOf<List<RepoSummary>?>(null) }
    var contributorsList by remember(repo.fullName) { mutableStateOf<List<UserSummary>?>(null) }
    var peopleLoading by remember(repo.fullName) { mutableStateOf(false) }
    var peopleError by remember(repo.fullName) { mutableStateOf<String?>(null) }

    var repoTags by remember(repo.fullName) { mutableStateOf<List<TagRef>>(emptyList()) }
    var repoCommits by remember(repo.fullName, currentRef) {
        mutableStateOf(container.repository.peekRepoCommits(repo.owner, repo.name, currentRef))
    }
    var commitsLoading by remember(repo.fullName, currentRef) { mutableStateOf(false) }
    var commitsError by remember(repo.fullName, currentRef) { mutableStateOf<String?>(null) }
    var commitsRequestSeq by remember(repo.fullName) { mutableStateOf(0) }
    var dirRequestSeq by remember(repo.fullName) { mutableStateOf(0) }
    var fileRequestSeq by remember(repo.fullName) { mutableStateOf(0) }
    var compareRequestSeq by remember(repo.fullName) { mutableStateOf(0) }
    var upstreamCompareRequestSeq by remember(repo.fullName) { mutableStateOf(0) }
    var upstreamCompare by remember(repo.fullName, currentRef) { mutableStateOf<BranchCompareResult?>(null) }
    var upstreamCompareLoading by remember(repo.fullName, currentRef) { mutableStateOf(false) }
    var showForkOnlyCommits by remember(repo.fullName, currentRef) { mutableStateOf(false) }

    var openedCommitSha by remember(repo.fullName) { mutableStateOf<String?>(null) }
    var openedCommitDetail by remember(repo.fullName) { mutableStateOf<CommitDetail?>(null) }
    var commitDetailLoading by remember(repo.fullName) { mutableStateOf(false) }

    var showCompareView by remember(repo.fullName) { mutableStateOf(false) }
    var compareBase by remember(repo.fullName) { mutableStateOf(repo.defaultBranch) }
    var compareHead by remember(repo.fullName) { mutableStateOf(currentRef) }
    var compareResult by remember(repo.fullName) { mutableStateOf<BranchCompareResult?>(null) }
    var compareLoading by remember(repo.fullName) { mutableStateOf(false) }
    var compareError by remember(repo.fullName) { mutableStateOf<String?>(null) }

    var workflows by remember(repo.fullName) { mutableStateOf<List<WorkflowSummary>>(emptyList()) }
    var selectedWorkflowId by remember(repo.fullName) { mutableStateOf<Long?>(null) }
    var workflowRuns by remember(repo.fullName) {
        mutableStateOf(container.repository.peekWorkflowRuns(repo.owner, repo.name))
    }
    var actionsLoading by remember(repo.fullName) { mutableStateOf(false) }
    var actionsError by remember(repo.fullName) { mutableStateOf<String?>(null) }
    // Only the latest actions request may update the workflow state.
    val actionsRequestGuard = remember(repo.fullName) { LatestRequestGuard() }
    var openedRun by remember(repo.fullName) { mutableStateOf<WorkflowRunSummary?>(null) }
    var runJobs by remember(repo.fullName) { mutableStateOf<List<WorkflowJobSummary>?>(null) }
    var jobsLoading by remember(repo.fullName) { mutableStateOf(false) }
    var openedLogJob by remember(repo.fullName) { mutableStateOf<WorkflowJobSummary?>(null) }
    var jobLogsText by remember(repo.fullName) { mutableStateOf<String?>(null) }
    var jobLogsLoading by remember(repo.fullName) { mutableStateOf(false) }
    var actionsBusy by remember(repo.fullName) { mutableStateOf(false) }
    var showDispatchDialog by remember(repo.fullName) { mutableStateOf(false) }
    var releases by remember(repo.fullName) { mutableStateOf(container.repository.peekReleases(repo.owner, repo.name)) }
    var releasesLoading by remember(repo.fullName) { mutableStateOf(false) }
    var releasesError by remember(repo.fullName) { mutableStateOf<String?>(null) }
    // Only the latest releases request may update the state above.
    val releasesRequestGuard = remember(repo.fullName) { LatestRequestGuard() }
    var showCreateReleaseDialog by remember(repo.fullName) { mutableStateOf(false) }
    var creatingRelease by remember(repo.fullName) { mutableStateOf(false) }
    var createReleaseError by remember(repo.fullName) { mutableStateOf<String?>(null) }
    var selectedDiscussionCategoryId by remember(repo.fullName) { mutableStateOf<String?>(null) }
    var discussionsData by remember(repo.fullName) {
        mutableStateOf(container.repository.peekDiscussions(repo.owner, repo.name))
    }
    var discussionsLoading by remember(repo.fullName) { mutableStateOf(false) }
    var discussionsError by remember(repo.fullName) { mutableStateOf<String?>(null) }
    var discussionsRequestSeq by remember(repo.fullName) { mutableStateOf(0) }
    var openedDiscussionNumber by remember(repo.fullName) { mutableStateOf<Int?>(null) }
    var discussionDetail by remember(repo.fullName) { mutableStateOf<DiscussionDetail?>(null) }
    var discussionDetailLoading by remember(repo.fullName) { mutableStateOf(false) }
    var discussionDetailError by remember(repo.fullName) { mutableStateOf<String?>(null) }
    var discussionCommentBusy by remember(repo.fullName) { mutableStateOf(false) }
    var showCreateDiscussionDialog by remember(repo.fullName) { mutableStateOf(false) }
    var creatingDiscussion by remember(repo.fullName) { mutableStateOf(false) }
    var createDiscussionError by remember(repo.fullName) { mutableStateOf<String?>(null) }
    var securityOverview by remember(repo.fullName) {
        mutableStateOf(container.repository.peekRepoSecurityOverview(repo.owner, repo.name))
    }
    var securityLoading by remember(repo.fullName) { mutableStateOf(false) }
    var securityError by remember(repo.fullName) { mutableStateOf<String?>(null) }
    // Only the latest security/insights requests may update the state above.
    val securityRequestGuard = remember(repo.fullName) { LatestRequestGuard() }
    var insightsData by remember(repo.fullName) {
        mutableStateOf(container.repository.peekRepoInsights(repo.owner, repo.name))
    }
    var insightsLoading by remember(repo.fullName) { mutableStateOf(false) }
    var insightsError by remember(repo.fullName) { mutableStateOf<String?>(null) }
    val insightsRequestGuard = remember(repo.fullName) { LatestRequestGuard() }
    var fullScreenDiffContext by remember(repo.fullName) { mutableStateOf<Pair<PullFileChange, List<PullFileChange>>?>(null) }

    fun loadDiscussions(categoryId: String? = selectedDiscussionCategoryId, forceRefresh: Boolean = false) {
        val reqSeq = ++discussionsRequestSeq
        scope.launch {
            val cached = container.repository.peekDiscussions(repo.owner, repo.name, categoryId)
            if (cached != null) {
                discussionsData = cached
                discussionsLoading = false
            } else {
                discussionsLoading = true
            }
            discussionsError = null
            container.repository.getDiscussions(repo.owner, repo.name, categoryId, forceRefresh = forceRefresh)
                .onSuccess {
                    if (reqSeq == discussionsRequestSeq) {
                        discussionsData = it
                        discussionsLoading = false
                    }
                }
                .onFailure {
                    if (reqSeq == discussionsRequestSeq) {
                        discussionsError = strings.errorMessage(it, strings.failedLoadDiscussions)
                        discussionsLoading = false
                    }
                }
        }
    }

    fun openDiscussion(number: Int, forceRefresh: Boolean = false) {
        openedDiscussionNumber = number
        scope.launch {
            val cached = container.repository.peekDiscussionDetail(repo.owner, repo.name, number)
            if (cached != null) {
                discussionDetail = cached
                discussionDetailLoading = false
            } else {
                discussionDetailLoading = true
            }
            discussionDetailError = null
            container.repository.getDiscussionDetail(repo.owner, repo.name, number, forceRefresh = forceRefresh)
                .onSuccess {
                    if (openedDiscussionNumber == number) {
                        discussionDetail = it
                        discussionDetailLoading = false
                    }
                }
                .onFailure {
                    if (openedDiscussionNumber == number) {
                        discussionDetailError = strings.errorMessage(it, strings.failedLoadDiscussion)
                        discussionDetailLoading = false
                    }
                }
        }
    }

    fun loadSecurityOverview(forceRefresh: Boolean = false) {
        val reqSeq = securityRequestGuard.next()
        scope.launch {
            val cached = container.repository.peekRepoSecurityOverview(repo.owner, repo.name)
            if (cached != null) {
                if (securityRequestGuard.isLatest(reqSeq)) {
                    securityOverview = cached
                    securityLoading = false
                }
            } else if (securityRequestGuard.isLatest(reqSeq)) {
                securityLoading = true
            }
            if (securityRequestGuard.isLatest(reqSeq)) {
                securityError = null
            }
            container.repository.getRepoSecurityOverview(repo.owner, repo.name, forceRefresh = forceRefresh)
                .onSuccess {
                    if (securityRequestGuard.isLatest(reqSeq)) {
                        securityOverview = it
                        securityLoading = false
                    }
                }
                .onFailure {
                    if (securityRequestGuard.isLatest(reqSeq)) {
                        securityError = strings.errorMessage(it, strings.failedLoadSecurity)
                        securityLoading = false
                    }
                }
        }
    }

    fun loadInsights(forceRefresh: Boolean = false) {
        val targetOwner = detail?.summary?.owner?.takeIf { it.isNotBlank() }
            ?: repo.owner.ifBlank { repo.fullName.substringBefore('/', "") }.trim()
        val targetRepo = detail?.summary?.name?.takeIf { it.isNotBlank() }
            ?: repo.name.ifBlank { repo.fullName.substringAfter('/', repo.name) }.trim()
        val reqSeq = insightsRequestGuard.next()
        scope.launch {
            val cached = container.repository.peekRepoInsights(targetOwner, targetRepo)
            if (cached != null) {
                if (insightsRequestGuard.isLatest(reqSeq)) {
                    insightsData = cached
                    insightsLoading = false
                }
            } else if (insightsRequestGuard.isLatest(reqSeq)) {
                insightsLoading = true
            }
            if (insightsRequestGuard.isLatest(reqSeq)) {
                insightsError = null
            }
            container.repository.getRepoInsights(targetOwner, targetRepo, forceRefresh = forceRefresh)
                .onSuccess {
                    if (insightsRequestGuard.isLatest(reqSeq)) {
                        insightsData = it
                        insightsLoading = false
                    }
                }
                .onFailure {
                    if (insightsRequestGuard.isLatest(reqSeq)) {
                        insightsError = strings.errorMessage(it, strings.failedLoadInsights)
                        insightsLoading = false
                    }
                }
        }
    }

    fun loadReleases(forceRefresh: Boolean = false) {
        val reqSeq = releasesRequestGuard.next()
        scope.launch {
            val cached = container.repository.peekReleases(repo.owner, repo.name)
            if (cached != null) {
                if (releasesRequestGuard.isLatest(reqSeq)) {
                    releases = cached
                    releasesLoading = false
                }
            } else if (releasesRequestGuard.isLatest(reqSeq)) {
                releasesLoading = true
            }
            if (releasesRequestGuard.isLatest(reqSeq)) {
                releasesError = null
            }
            container.repository.getReleases(repo.owner, repo.name, forceRefresh = forceRefresh)
                .onSuccess {
                    if (releasesRequestGuard.isLatest(reqSeq)) {
                        releases = it
                        releasesLoading = false
                    }
                }
                .onFailure {
                    if (releasesRequestGuard.isLatest(reqSeq)) {
                        releasesError = strings.errorMessage(it, strings.failedLoadReleases)
                        releasesLoading = false
                    }
                }
        }
    }

    fun loadActions(workflowId: Long? = selectedWorkflowId, forceRefresh: Boolean = false) {
        val reqSeq = actionsRequestGuard.next()
        scope.launch {
            val cached = container.repository.peekWorkflowRuns(repo.owner, repo.name, workflowId)
            if (cached != null) {
                if (actionsRequestGuard.isLatest(reqSeq)) {
                    workflowRuns = cached
                    actionsLoading = false
                }
            } else if (actionsRequestGuard.isLatest(reqSeq)) {
                actionsLoading = true
            }
            if (actionsRequestGuard.isLatest(reqSeq)) {
                actionsError = null
            }
            container.repository.getWorkflows(repo.owner, repo.name, forceRefresh = forceRefresh)
                .onSuccess { if (actionsRequestGuard.isLatest(reqSeq)) workflows = it }
            container.repository.getWorkflowRuns(repo.owner, repo.name, workflowId, forceRefresh = forceRefresh)
                .onSuccess {
                    if (actionsRequestGuard.isLatest(reqSeq)) {
                        workflowRuns = it
                        actionsLoading = false
                    }
                }
                .onFailure {
                    if (actionsRequestGuard.isLatest(reqSeq)) {
                        actionsError = strings.errorMessage(it, strings.failedLoadActions)
                        actionsLoading = false
                    }
                }
        }
    }

    fun openRun(run: WorkflowRunSummary, forceRefresh: Boolean = false) {
        openedRun = run
        openedLogJob = null
        jobLogsText = null
        scope.launch {
            jobsLoading = true
            container.repository.getWorkflowRunJobs(repo.owner, repo.name, run.id, forceRefresh = forceRefresh)
                .onSuccess { runJobs = it }
                .onFailure { onMessage(strings.errorMessage(it, strings.failedLoadJobs)) }
            jobsLoading = false
        }
    }

    fun loadJobLog(job: WorkflowJobSummary) {
        openedLogJob = job
        jobLogsText = null
        scope.launch {
            jobLogsLoading = true
            container.repository.getJobLogs(repo.owner, repo.name, job.id)
                .onSuccess { jobLogsText = it }
                .onFailure { onMessage(strings.jobLogsErrorText(it)) }
            jobLogsLoading = false
        }
    }

    fun loadCommits(ref: String, forceRefresh: Boolean = false) {
        val reqSeq = ++commitsRequestSeq
        scope.launch {
            val cached = if (forceRefresh) null else container.repository.peekRepoCommits(repo.owner, repo.name, ref)
            if (cached != null) {
                if (reqSeq == commitsRequestSeq && ref == currentRef) {
                    repoCommits = cached
                    commitsLoading = false
                }
            } else if (reqSeq == commitsRequestSeq && ref == currentRef) {
                commitsLoading = true
            }
            if (reqSeq == commitsRequestSeq && ref == currentRef) {
                commitsError = null
            }
            container.repository.getRepoCommits(repo.owner, repo.name, ref, forceRefresh = forceRefresh)
                .onSuccess { loaded ->
                    if (reqSeq == commitsRequestSeq && ref == currentRef) {
                        repoCommits = loaded
                        commitsLoading = false
                    }
                }
                .onFailure { err ->
                    if (reqSeq == commitsRequestSeq && ref == currentRef) {
                        commitsError = strings.errorMessage(err, strings.failedLoadCommits)
                        commitsLoading = false
                    }
                }
        }
    }

    fun loadUpstreamCompare(parent: RepoParentSummary, ref: String, forceRefresh: Boolean = false) {
        val baseSpec = "${parent.owner}:${parent.defaultBranch}"
        val reqSeq = ++upstreamCompareRequestSeq
        scope.launch {
            val cached = if (forceRefresh) null else container.repository.peekCompareRefs(repo.owner, repo.name, baseSpec, ref)
            if (cached != null) {
                if (reqSeq == upstreamCompareRequestSeq && ref == currentRef) {
                    upstreamCompare = cached
                    upstreamCompareLoading = false
                }
            } else if (reqSeq == upstreamCompareRequestSeq && ref == currentRef) {
                upstreamCompareLoading = true
            }
            container.repository.compareRefs(repo.owner, repo.name, baseSpec, ref, forceRefresh = forceRefresh)
                .onSuccess { cmp ->
                    if (reqSeq == upstreamCompareRequestSeq && ref == currentRef) {
                        upstreamCompare = cmp
                        upstreamCompareLoading = false
                    }
                }
                .onFailure {
                    if (reqSeq == upstreamCompareRequestSeq && ref == currentRef) {
                        upstreamCompareLoading = false
                    }
                }
        }
    }

    fun openCommit(sha: String) {
        openedCommitSha = sha
        scope.launch {
            val cached = container.repository.peekCommitDetail(repo.owner, repo.name, sha)
            if (cached != null) {
                openedCommitDetail = cached
                commitDetailLoading = false
            } else {
                openedCommitDetail = null
                commitDetailLoading = true
            }
            container.repository.getCommitDetail(repo.owner, repo.name, sha, forceRefresh = false)
                .onSuccess { if (openedCommitSha == sha) openedCommitDetail = it }
                .onFailure { onMessage(strings.errorMessage(it, strings.failedLoadCommitDiff)) }
            commitDetailLoading = false
        }
    }

    fun runCompare(base: String, head: String, forceRefresh: Boolean = false) {
        val reqSeq = ++compareRequestSeq
        compareBase = base
        compareHead = head
        scope.launch {
            compareLoading = true
            compareError = null
            container.repository.compareRefs(repo.owner, repo.name, base, head, forceRefresh = forceRefresh)
                .onSuccess { if (reqSeq == compareRequestSeq) compareResult = it }
                .onFailure { if (reqSeq == compareRequestSeq) compareError = strings.errorMessage(it, strings.compareFailed) }
            if (reqSeq == compareRequestSeq) compareLoading = false
        }
    }

    fun openPeopleTab(tab: RepoPeopleTab, forkSortOverride: String = forksSort) {
        peopleTab = tab
        if (tab == RepoPeopleTab.FORKS) {
            forksSort = forkSortOverride
        }
        showPeopleDialog = true
        peopleError = null
        val targetOwner = detail?.summary?.owner?.takeIf { it.isNotBlank() }
            ?: repo.owner.ifBlank { repo.fullName.substringBefore('/', "") }.trim()
        val targetRepo = detail?.summary?.name?.takeIf { it.isNotBlank() }
            ?: repo.name.ifBlank { repo.fullName.substringAfter('/', repo.name) }.trim()
        scope.launch {
            peopleLoading = true
            when (tab) {
                RepoPeopleTab.STARGAZERS -> container.repository.getRepoStargazers(targetOwner, targetRepo)
                    .onSuccess { stargazersList = it }
                    .onFailure { err ->
                        val msg = strings.errorMessage(err, strings.failedLoadStargazers)
                        peopleError = msg
                        onMessage(msg)
                    }
                RepoPeopleTab.FORKS -> container.repository.getRepoForks(targetOwner, targetRepo, sort = forkSortOverride)
                    .onSuccess { forksList = it }
                    .onFailure { err ->
                        val msg = strings.errorMessage(err, strings.failedLoadForks)
                        peopleError = msg
                        onMessage(msg)
                    }
                RepoPeopleTab.CONTRIBUTORS -> container.repository.getRepoContributors(targetOwner, targetRepo)
                    .onSuccess { contributorsList = it }
                    .onFailure { err ->
                        val msg = strings.errorMessage(err, strings.failedLoadContributors)
                        peopleError = msg
                        onMessage(msg)
                    }
            }
            peopleLoading = false
        }
    }

    fun loadDirectory(path: String, ref: String, forceRefresh: Boolean = false) {
        val reqSeq = ++dirRequestSeq
        scope.launch {
            openedFile = null
            currentPath = path
            val cached = if (forceRefresh) null else container.repository.peekDirectoryContents(repo.owner, repo.name, path, ref)
            if (cached != null) {
                entries = cached
                isLoading = false
            } else {
                isLoading = true
            }
            container.repository.getDirectoryContents(repo.owner, repo.name, path, ref, forceRefresh = forceRefresh)
                .onSuccess { loaded ->
                    if (reqSeq == dirRequestSeq && path == currentPath && ref == currentRef) {
                        entries = loaded
                    }
                }
                .onFailure { if (reqSeq == dirRequestSeq) onMessage(strings.errorMessage(it, strings.failedLoadDirectory)) }
            if (reqSeq == dirRequestSeq) isLoading = false
        }
    }

    fun loadFile(path: String, ref: String) {
        val reqSeq = ++fileRequestSeq
        scope.launch {
            val cached = container.repository.peekFileContent(repo.owner, repo.name, path, ref)
            if (cached != null) {
                openedFile = cached
                isLoading = false
            } else {
                isLoading = true
            }
            container.repository.getFileContent(repo.owner, repo.name, path, ref, forceRefresh = false)
                .onSuccess { loaded ->
                    if (reqSeq == fileRequestSeq && ref == currentRef) {
                        openedFile = loaded
                    }
                }
                .onFailure { if (reqSeq == fileRequestSeq) onMessage(strings.errorMessage(it, strings.failedLoadFile)) }
            if (reqSeq == fileRequestSeq) isLoading = false
        }
    }

    val issueStateRevision by container.repository.issueStateRevision.collectAsState()
    val viewerSession by container.sessionManager.session.collectAsState()
    val effectiveViewerLogin = viewerProfile?.login ?: viewerSession?.userLogin
    val capabilities = remember(repo, detail, effectiveViewerLogin) {
        resolveRepoCapabilities(repo, detail, effectiveViewerLogin)
    }

    fun loadRepoIssues(filter: IssueStateFilter, forceRefresh: Boolean = false) {
        val reqSeq = issuesRequestGuard.next()
        scope.launch {
            val cached = container.repository.peekRepoIssuesOrPulls(repo.owner, repo.name, pullRequests = false, stateFilter = filter)
            if (cached != null) {
                if (issuesRequestGuard.isLatest(reqSeq)) {
                    repoIssues = cached
                    issuesLoading = false
                }
            } else if (issuesRequestGuard.isLatest(reqSeq)) {
                repoIssues = null
                issuesLoading = true
            }
            if (issuesRequestGuard.isLatest(reqSeq)) {
                issuesError = null
            }
            container.repository.getRepoIssuesOrPulls(repo.owner, repo.name, pullRequests = false, stateFilter = filter, forceRefresh = forceRefresh)
                .onSuccess { loaded ->
                    if (issuesRequestGuard.isLatest(reqSeq)) {
                        repoIssues = loaded
                        issuesLoading = false
                        container.prefetchIssuePullTargets(
                            loaded.take(3).map { IssuePullTarget(repo.owner, repo.name, it.number, it.title) }
                        )
                    }
                }
                .onFailure {
                    if (issuesRequestGuard.isLatest(reqSeq)) {
                        issuesError = strings.errorMessage(it, strings.failedLoadIssues)
                        issuesLoading = false
                    }
                }
        }
    }

    fun loadRepoLabelsList(forceRefresh: Boolean = false) {
        scope.launch {
            val cached = if (forceRefresh) null else container.repository.peekRepoLabels(repo.owner, repo.name)
            if (cached != null) {
                repoLabels = cached
                availableIssueLabels = cached
                labelsLoading = false
            } else {
                labelsLoading = true
            }
            labelsError = null
            container.repository.getRepoLabels(repo.owner, repo.name, forceRefresh = forceRefresh)
                .onSuccess { loaded ->
                    repoLabels = loaded
                    availableIssueLabels = loaded
                    labelsLoading = false
                }
                .onFailure { err ->
                    labelsError = strings.errorMessage(err, strings.failedLoadLabels)
                    labelsLoading = false
                }
        }
    }

    fun loadRepoMilestonesList(forceRefresh: Boolean = false) {
        scope.launch {
            val cached = if (forceRefresh) null else container.repository.peekRepoMilestones(repo.owner, repo.name, state = "all")
            if (cached != null) {
                repoMilestones = cached
                milestonesLoading = false
            } else {
                milestonesLoading = true
            }
            milestonesError = null
            container.repository.getRepoMilestones(repo.owner, repo.name, state = "all", forceRefresh = forceRefresh)
                .onSuccess { loaded ->
                    repoMilestones = loaded
                    milestonesLoading = false
                }
                .onFailure { err ->
                    milestonesError = strings.errorMessage(err, strings.failedLoadMilestones)
                    milestonesLoading = false
                }
        }
    }

    fun loadRepoPulls(filter: IssueStateFilter, forceRefresh: Boolean = false) {
        val reqSeq = pullsRequestGuard.next()
        scope.launch {
            val cached = container.repository.peekRepoIssuesOrPulls(repo.owner, repo.name, pullRequests = true, stateFilter = filter)
            if (cached != null) {
                if (pullsRequestGuard.isLatest(reqSeq)) {
                    repoPulls = cached
                    pullsLoading = false
                }
            } else if (pullsRequestGuard.isLatest(reqSeq)) {
                repoPulls = null
                pullsLoading = true
            }
            if (pullsRequestGuard.isLatest(reqSeq)) {
                pullsError = null
            }
            container.repository.getRepoIssuesOrPulls(repo.owner, repo.name, pullRequests = true, stateFilter = filter, forceRefresh = forceRefresh)
                .onSuccess { loaded ->
                    if (pullsRequestGuard.isLatest(reqSeq)) {
                        repoPulls = loaded
                        pullsLoading = false
                        container.prefetchIssuePullTargets(
                            loaded.take(3).map { IssuePullTarget(repo.owner, repo.name, it.number, it.title) }
                        )
                    }
                }
                .onFailure {
                    if (pullsRequestGuard.isLatest(reqSeq)) {
                        pullsError = strings.errorMessage(it, strings.failedLoadPulls)
                        pullsLoading = false
                    }
                }
        }
    }

    val hasInnerRepoBack = (activeTab == RepoDetailTab.ACTIONS && (openedLogJob != null || openedRun != null)) ||
        (activeTab == RepoDetailTab.DISCUSSIONS && openedDiscussionNumber != null) ||
        (activeTab == RepoDetailTab.CODE && (openedCommitSha != null || showCompareView || showCommitsView || currentPath.isNotBlank())) ||
        openedFile != null
    val hasOpenEditor = showFileEditorDialog ||
        showCreateReleaseDialog ||
        showCreateDiscussionDialog ||
        showCreatePrDialog ||
        showCreateIssueDialog ||
        showLabelEditorDialog ||
        showMilestoneEditorDialog ||
        deletingLabelTarget != null ||
        deletingMilestoneTarget != null ||
        fullScreenDiffContext != null

    val effectiveParent = detail?.summary?.parent ?: repo.parent
    val isForkRepo = detail?.summary?.isFork ?: repo.isFork || effectiveParent != null

    LaunchedEffect(repo.fullName, initialFilePath) {
        isLoading = detail == null && entries.isEmpty()
        val initialParent = initialFilePath?.substringBeforeLast('/', "")?.takeIf { it.isNotBlank() } ?: ""
        currentPath = initialParent
        val initialRef = currentRef
        var resolvedRef = initialRef
        var refChanged = false
        coroutineScope {
            val detailJob = async { container.repository.getRepoDetail(repo.owner, repo.name) }
            val readmeJob = async { container.repository.getReadme(repo.owner, repo.name, initialRef) }
            val entriesJob = async { container.repository.getDirectoryContents(repo.owner, repo.name, initialParent, initialRef) }

            detailJob.await().onSuccess {
                detail = it
                isStarred = it.summary.viewerHasStarred
                val nextDefault = it.summary.defaultBranch.ifBlank { repo.defaultBranch }
                if (nextDefault != initialRef) {
                    refChanged = true
                    resolvedRef = nextDefault
                    currentRef = resolvedRef
                    compareBase = resolvedRef
                    compareHead = resolvedRef
                }
            }
            if (refChanged) {
                val updatedReadme = async { container.repository.getReadme(repo.owner, repo.name, resolvedRef) }
                val updatedEntries = async { container.repository.getDirectoryContents(repo.owner, repo.name, initialParent, resolvedRef) }
                updatedReadme.await().onSuccess { if (currentRef == resolvedRef) readme = it }
                updatedEntries.await().onSuccess { if (currentRef == resolvedRef && currentPath == initialParent) entries = it }
            } else {
                readmeJob.await().onSuccess { if (currentRef == initialRef) readme = it }
                entriesJob.await().onSuccess { if (currentRef == initialRef && currentPath == initialParent) entries = it }
            }
        }
        if (!initialFilePath.isNullOrBlank()) {
            loadFile(initialFilePath, resolvedRef)
        } else {
            isLoading = false
        }

        // Prefetch Open Issues, Open PRs, Tags, Commits & Watch state in background
        scope.launch {
            container.repository.getRepoSubscription(repo.owner, repo.name)
                .onSuccess { isWatching = it }
        }
        scope.launch {
            container.repository.getRepoTags(repo.owner, repo.name)
                .onSuccess { repoTags = it }
        }
        loadCommits(resolvedRef, forceRefresh = false)
        scope.launch {
            container.repository.getRepoIssuesOrPulls(repo.owner, repo.name, pullRequests = false, stateFilter = IssueStateFilter.OPEN)
                .onSuccess { if (issueStateFilter == IssueStateFilter.OPEN && repoIssues == null) repoIssues = it }
        }
        scope.launch {
            container.repository.getReleases(repo.owner, repo.name)
                .onSuccess { if (releases == null) releases = it }
        }
        scope.launch {
            container.repository.getRepoIssuesOrPulls(repo.owner, repo.name, pullRequests = true, stateFilter = IssueStateFilter.OPEN)
                .onSuccess { if (pullStateFilter == IssueStateFilter.OPEN && repoPulls == null) repoPulls = it }
        }
    }

    LaunchedEffect(repo.fullName, currentRef, effectiveParent?.fullName, effectiveParent?.defaultBranch) {
        val parent = effectiveParent
        if (parent != null) {
            loadUpstreamCompare(parent, currentRef, forceRefresh = false)
        }
    }

    LaunchedEffect(activeTab, currentRef, repo.fullName, showCommitsView, showCompareView, openedCommitSha) {
        if (activeTab == RepoDetailTab.CODE && showCommitsView && !showCompareView && openedCommitSha == null) {
            loadCommits(currentRef, forceRefresh = false)
        }
        if (activeTab == RepoDetailTab.ACTIONS && openedRun == null) {
            loadActions(selectedWorkflowId, forceRefresh = false)
        }
        if (activeTab == RepoDetailTab.RELEASES) {
            loadReleases(forceRefresh = false)
        }
        if (activeTab == RepoDetailTab.DISCUSSIONS && openedDiscussionNumber == null) {
            loadDiscussions(selectedDiscussionCategoryId, forceRefresh = false)
        }
        if (activeTab == RepoDetailTab.SECURITY) {
            loadSecurityOverview(forceRefresh = false)
        }
        if (activeTab == RepoDetailTab.INSIGHTS) {
            loadInsights(forceRefresh = false)
        }
    }

    LaunchedEffect(activeTab, issuesSection, issueStateFilter, repo.fullName) {
        if (activeTab == RepoDetailTab.ISSUES) {
            when (issuesSection) {
                RepoIssuesSection.ISSUES -> loadRepoIssues(issueStateFilter, forceRefresh = false)
                RepoIssuesSection.LABELS -> loadRepoLabelsList(forceRefresh = false)
                RepoIssuesSection.MILESTONES -> loadRepoMilestonesList(forceRefresh = false)
            }
        }
    }

    LaunchedEffect(activeTab, pullStateFilter, repo.fullName) {
        if (activeTab == RepoDetailTab.PULLS) {
            loadRepoPulls(pullStateFilter, forceRefresh = false)
        }
    }

    LaunchedEffect(showCreateIssueDialog, repo.fullName, capabilities.canTriage) {
        if (showCreateIssueDialog && capabilities.canTriage) {
            issueLabelsLoading = availableIssueLabels.isEmpty()
            container.repository.getRepoLabels(repo.owner, repo.name)
                .onSuccess { availableIssueLabels = it }
                .onFailure {
                    if (availableIssueLabels.isEmpty()) {
                        onMessage(strings.errorMessage(it, strings.failedLoadLabels))
                    }
                }
            issueLabelsLoading = false
        }
    }

    LaunchedEffect(issueStateRevision, repo.fullName, issueStateFilter, pullStateFilter) {
        if (issueStateRevision > 0L) {
            container.repository.peekRepoIssuesOrPulls(
                repo.owner,
                repo.name,
                pullRequests = false,
                stateFilter = issueStateFilter
            )?.let { repoIssues = it }
            container.repository.peekRepoIssuesOrPulls(
                repo.owner,
                repo.name,
                pullRequests = true,
                stateFilter = pullStateFilter
            )?.let { repoPulls = it }
            container.repository.getRepoDetail(repo.owner, repo.name, forceRefresh = true)
                .onSuccess { detail = it }
        }
    }

    val headerCollapseTracker = remember(repo.fullName) { RepoHeaderCollapseTracker() }
    val headerNestedScroll = remember(headerCollapseTracker) { headerCollapseTracker.toNestedScrollConnection() }
    val repoHeaderVerticalPadding by animateDpAsState(if (headerCollapseTracker.isCollapsed) 4.dp else 12.dp)

    Box(modifier = Modifier.fillMaxSize()) {
        MaxxingPredictiveBackContainer(
            enabled = backEnabled && hasInnerRepoBack && !hasOpenEditor,
            onBack = {
                when {
                    activeTab == RepoDetailTab.ACTIONS && openedLogJob != null -> {
                        openedLogJob = null
                        jobLogsText = null
                    }
                    activeTab == RepoDetailTab.ACTIONS && openedRun != null -> {
                        openedRun = null
                        runJobs = null
                    }
                    activeTab == RepoDetailTab.DISCUSSIONS && openedDiscussionNumber != null -> {
                        openedDiscussionNumber = null
                        discussionDetail = null
                    }
                    activeTab == RepoDetailTab.CODE && openedCommitSha != null -> {
                        openedCommitSha = null
                        openedCommitDetail = null
                    }
                    activeTab == RepoDetailTab.CODE && showCompareView -> showCompareView = false
                    activeTab == RepoDetailTab.CODE && showCommitsView -> showCommitsView = false
                    openedFile != null -> openedFile = null
                    activeTab == RepoDetailTab.CODE && currentPath.isNotBlank() -> {
                        val parent = currentPath.substringBeforeLast('/', "")
                        loadDirectory(parent, currentRef)
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        ) {
        Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = repoHeaderVerticalPadding)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Lucide.ArrowLeft, contentDescription = strings.back)
                    }
                    Text(
                        text = repo.fullName,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = repo.owner.isNotBlank()) { onOpenUser(repo.owner) }
                    )
                    IconButton(
                        onClick = {
                            scope.launch {
                                val nowPinned = container.sessionManager.togglePinnedRepo(repo.fullName)
                                onMessage(if (nowPinned) strings.repoPinnedMessage(repo.fullName) else strings.repoUnpinnedMessage(repo.fullName))
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isPinned) MaxxingFilledIcons.Bookmark else Lucide.Bookmark,
                            contentDescription = if (isPinned) strings.repoUnpinAction else strings.repoPinAction,
                            tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (capabilities.canCreateIssue) {
                        FilledTonalButton(
                            onClick = { showCreateIssueDialog = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Lucide.Plus, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.subjectIssue, maxLines = 1, softWrap = false)
                        }
                    }
                }

                if (isForkRepo) {
                    val parentTarget = effectiveParent
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .padding(start = 12.dp, bottom = 2.dp)
                            .then(
                                if (parentTarget != null) {
                                    Modifier.clickable {
                                        onOpenRepo(
                                            RepoSummary(
                                                id = "parent_${parentTarget.fullName}",
                                                name = parentTarget.name,
                                                owner = parentTarget.owner,
                                                fullName = parentTarget.fullName,
                                                defaultBranch = parentTarget.defaultBranch,
                                                ownerAvatarUrl = parentTarget.ownerAvatarUrl
                                            )
                                        )
                                    }
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        Icon(
                            Lucide.GitFork,
                            contentDescription = strings.fork,
                            tint = if (parentTarget != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (parentTarget != null) strings.repoForkedFrom(parentTarget.fullName) else strings.repoForkLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (parentTarget != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                AnimatedVisibility(
                    visible = !headerCollapseTracker.isCollapsed,
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
                ) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        // 1) MD3E Connected Social Action Group (Star · Watch · Fork · Network)
                        val isLightTheme = MaterialTheme.colorScheme.surface.luminance() > 0.5f
                        val starCount = (detail?.summary?.stargazersCount ?: repo.stargazersCount).let { base ->
                            if (isStarred == (detail?.summary?.viewerHasStarred ?: repo.viewerHasStarred)) base
                            else if (isStarred) base + 1 else (base - 1).coerceAtLeast(0)
                        }
                        val forksNum = detail?.summary?.forksCount ?: repo.forksCount
                        val starCheckedBg = if (isLightTheme) Color(0xFFFEF3C7) else Color(0xFF453208)
                        val starCheckedFg = if (isLightTheme) Color(0xFF92400E) else Color(0xFFFCD34D)
                        val starIconTint = if (isLightTheme) Color(0xFFD97706) else Color(0xFFFBBF24)
                        val actionPad = PaddingValues(horizontal = 11.dp, vertical = 6.dp)

                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                        ) {
                            ToggleButton(
                                checked = isStarred,
                                onCheckedChange = {
                                    val next = !isStarred
                                    isStarred = next
                                    scope.launch {
                                        container.repository.setStarred(repo.owner, repo.name, next)
                                            .onSuccess { onMessage(if (next) strings.repoStarredMessage(repo.fullName) else strings.repoUnstarredMessage(repo.fullName)) }
                                            .onFailure { isStarred = !next }
                                    }
                                },
                                shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                                contentPadding = actionPad,
                                colors = FilledTonalToggleButtonDefaults.colors(
                                    checkedContainerColor = starCheckedBg,
                                    checkedContentColor = starCheckedFg
                                )
                            ) {
                                Icon(
                                    imageVector = if (isStarred) MaxxingFilledIcons.Star else Lucide.Star,
                                    contentDescription = if (isStarred) strings.starred else strings.star,
                                    tint = if (isStarred) starIconTint else MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (isStarred) "${strings.starred} ${formatCompactCount(starCount)}" else "${strings.star} ${formatCompactCount(starCount)}",
                                    fontWeight = if (isStarred) FontWeight.SemiBold else FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            ToggleButton(
                                checked = isWatching,
                                onCheckedChange = {
                                    val next = !isWatching
                                    isWatching = next
                                    scope.launch {
                                        container.repository.setRepoSubscription(repo.owner, repo.name, next)
                                            .onSuccess { onMessage(if (next) strings.repoWatchingMessage(repo.fullName) else strings.repoUnwatchedMessage(repo.fullName)) }
                                            .onFailure {
                                                isWatching = !next
                                                onMessage(strings.errorMessage(it, strings.repoWatchUpdateFailed))
                                            }
                                    }
                                },
                                shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                                contentPadding = actionPad,
                                colors = FilledTonalToggleButtonDefaults.colors(
                                    checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(
                                    imageVector = if (isWatching) Lucide.Eye else Lucide.EyeOff,
                                    contentDescription = if (isWatching) strings.repoWatching else strings.repoWatch,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (isWatching) strings.repoWatching else strings.repoWatch,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            ToggleButton(
                                checked = false,
                                enabled = !forkBusy,
                                onCheckedChange = {
                                    forkBusy = true
                                    scope.launch {
                                        container.repository.forkRepository(repo.owner, repo.name)
                                            .onSuccess { forked ->
                                                forkBusy = false
                                                onMessage(strings.repoForkedMessage(forked.fullName))
                                                onOpenRepo(forked)
                                            }
                                            .onFailure {
                                                forkBusy = false
                                                onMessage(strings.errorMessage(it, strings.repoForkFailed))
                                            }
                                    }
                                },
                                shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                                contentPadding = actionPad,
                                colors = FilledTonalToggleButtonDefaults.colors()
                            ) {
                                Icon(Lucide.GitFork, contentDescription = strings.fork, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (forkBusy) strings.repoForking else "${strings.fork} ${formatCompactCount(forksNum)}",
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            ToggleButton(
                                checked = false,
                                onCheckedChange = { openPeopleTab(RepoPeopleTab.STARGAZERS) },
                                shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                                contentPadding = actionPad,
                                colors = FilledTonalToggleButtonDefaults.colors()
                            ) {
                                Icon(Lucide.Users, contentDescription = strings.repoNetwork, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(strings.repoNetwork, maxLines = 1, softWrap = false)
                            }

                            Box {
                                ToggleButton(
                                    checked = showShareMenu,
                                    onCheckedChange = { showShareMenu = true },
                                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                                    contentPadding = actionPad,
                                    colors = FilledTonalToggleButtonDefaults.colors()
                                ) {
                                    Icon(Lucide.Share2, contentDescription = strings.shareRepository, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.share, maxLines = 1, softWrap = false)
                                }
                                val repoUrl = resolveRepoWebUrl(repo.owner, repo.name)
                                DropdownMenu(
                                    expanded = showShareMenu,
                                    onDismissRequest = { showShareMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        leadingIcon = { Icon(Lucide.Copy, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        text = { Text(strings.copyUrl) },
                                        onClick = {
                                            showShareMenu = false
                                            clipboard.setText(AnnotatedString(repoUrl))
                                            onMessage(strings.signInCopiedValue(repoUrl))
                                        }
                                    )
                                    DropdownMenuItem(
                                        leadingIcon = { Icon(Lucide.Share2, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        text = { Text(strings.shareEllipsis) },
                                        onClick = {
                                            showShareMenu = false
                                            if (!shareText(repoUrl, title = repo.fullName)) {
                                                clipboard.setText(AnnotatedString(repoUrl))
                                                onMessage(strings.signInCopiedValue(repoUrl))
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        leadingIcon = { Icon(Lucide.ExternalLink, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        text = { Text(strings.openInBrowser) },
                                        onClick = {
                                            showShareMenu = false
                                            if (!openUrlInBrowser(repoUrl)) {
                                                clipboard.setText(AnnotatedString(repoUrl))
                                                onMessage(strings.signInCopiedValue(repoUrl))
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        // 2) Distinct Git Reference Field (10.dp rounded rectangle) + Compare button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showBranchMenu = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Lucide.GitBranch,
                                            contentDescription = strings.repoBranchOrTag,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = currentRef,
                                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            Lucide.ChevronDown,
                                            contentDescription = strings.repoSwitchBranchOrTag,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                                DropdownMenu(expanded = showBranchMenu, onDismissRequest = { showBranchMenu = false }) {
                                    val branches = detail?.branches.orEmpty()
                                    val selectRef: (String) -> Unit = { selected ->
                                        showBranchMenu = false
                                        currentRef = selected
                                        openedCommitSha = null
                                        loadDirectory("", selected)
                                        if (activeTab == RepoDetailTab.CODE && showCommitsView && !showCompareView) {
                                            loadCommits(selected, forceRefresh = false)
                                        }
                                        scope.launch {
                                            container.repository.getReadme(repo.owner, repo.name, selected)
                                                .onSuccess { if (currentRef == selected) readme = it }
                                        }
                                    }
                                    if (branches.isEmpty() && repoTags.isEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text(currentRef) },
                                            onClick = { showBranchMenu = false }
                                        )
                                    } else {
                                        branches.forEach { b ->
                                            DropdownMenuItem(
                                                leadingIcon = { Icon(Lucide.GitBranch, contentDescription = null, modifier = Modifier.size(15.dp)) },
                                                text = { Text(b.name) },
                                                onClick = { selectRef(b.name) }
                                            )
                                        }
                                        if (repoTags.isNotEmpty()) {
                                            HorizontalDivider()
                                            repoTags.take(20).forEach { tag ->
                                                DropdownMenuItem(
                                                    leadingIcon = { Icon(Lucide.Tag, contentDescription = null, modifier = Modifier.size(15.dp)) },
                                                    text = { Text(tag.name) },
                                                    onClick = { selectRef(tag.name) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            FilledTonalButton(
                                shape = RoundedCornerShape(10.dp),
                                onClick = {
                                    openedCommitSha = null
                                    showCommitsView = true
                                    showCompareView = true
                                    activeTab = RepoDetailTab.CODE
                                    val branches = detail?.branches.orEmpty()
                                    val defaultRef = detail?.summary?.defaultBranch ?: repo.defaultBranch
                                    val otherBranch = branches.firstOrNull { it.name != defaultRef }?.name
                                    val upstreamBase = effectiveParent?.let { "${it.owner}:${it.defaultBranch}" }
                                    val base: String
                                    val head: String
                                    if (currentRef != defaultRef) {
                                        base = defaultRef
                                        head = currentRef
                                    } else if (otherBranch != null) {
                                        base = defaultRef
                                        head = otherBranch
                                    } else if (upstreamBase != null) {
                                        base = upstreamBase
                                        head = currentRef
                                    } else {
                                        base = defaultRef
                                        head = currentRef
                                    }
                                    compareBase = base
                                    compareHead = head
                                    runCompare(base, head)
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)
                            ) {
                                Icon(Lucide.ArrowLeftRight, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(strings.repoCompare, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                // 3) Underlined Scrollable Section Tabs (distinct from action buttons above)
                val resolvedOpenIssuesCount: Int? = when {
                    detail != null -> detail!!.summary.openIssuesCount
                    repo.openPullRequestsCount != null -> repo.openIssuesCount
                    issueStateFilter == IssueStateFilter.OPEN && repoIssues != null -> repoIssues!!.size
                    else -> null
                }
                val resolvedOpenPullsCount: Int? = when {
                    detail != null -> detail!!.summary.openPullRequestsCount
                    repo.openPullRequestsCount != null -> repo.openPullRequestsCount
                    pullStateFilter == IssueStateFilter.OPEN && repoPulls != null -> repoPulls!!.size
                    else -> null
                }
                PrimaryScrollableTabRow(
                    selectedTabIndex = RepoDetailTab.entries.indexOf(activeTab),
                    containerColor = Color.Transparent,
                    edgePadding = 0.dp
                ) {
                    RepoDetailTab.entries.forEach { tab ->
                        val icon = when (tab) {
                            RepoDetailTab.CODE -> Lucide.Folder
                            RepoDetailTab.ISSUES -> Lucide.CircleDot
                            RepoDetailTab.PULLS -> Lucide.GitPullRequest
                            RepoDetailTab.ACTIONS -> Lucide.Play
                            RepoDetailTab.RELEASES -> Lucide.Tag
                            RepoDetailTab.DISCUSSIONS -> Lucide.MessageSquare
                            RepoDetailTab.SECURITY -> Lucide.ShieldCheck
                            RepoDetailTab.INSIGHTS -> Lucide.Activity
                        }
                        val selected = activeTab == tab
                        Tab(
                            selected = selected,
                            onClick = {
                                activeTab = tab
                                headerCollapseTracker.expand()
                                showCommitsView = false
                                showCompareView = false
                                openedCommitSha = null
                                if (tab != RepoDetailTab.ACTIONS) {
                                    openedRun = null
                                    openedLogJob = null
                                }
                                if (tab != RepoDetailTab.DISCUSSIONS) {
                                    openedDiscussionNumber = null
                                }
                            },
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Text(
                                        text = formatLocalizedRepoDetailTabLabel(tab, resolvedOpenIssuesCount, resolvedOpenPullsCount, strings),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        )
                    }
                }

                if (isLoading) {
                    Spacer(Modifier.height(8.dp))
                    MaxxingWavyProgress()
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .nestedScroll(headerNestedScroll)
        ) {
            when (activeTab) {
            RepoDetailTab.CODE -> {
                if (showCommitsView || showCompareView || openedCommitSha != null) {
                    val allRefs = remember(detail?.branches, repoTags, currentRef, repo.defaultBranch, effectiveParent) {
                        buildList {
                            effectiveParent?.let { add("${it.owner}:${it.defaultBranch}") }
                            addAll(detail?.branches.orEmpty().map { it.name })
                            addAll(repoTags.map { it.name })
                            add(currentRef)
                            add(repo.defaultBranch)
                        }.distinct()
                    }
                    val avatarResolver: (CommitSummary) -> String = { commit ->
                        resolveCommitAuthorAvatarUrl(
                            commit = commit,
                            repoOwner = repo.owner,
                            repoOwnerAvatarUrl = detail?.summary?.ownerAvatarUrl?.ifBlank { repo.ownerAvatarUrl } ?: repo.ownerAvatarUrl,
                            viewerLogin = viewerProfile?.login,
                            viewerName = viewerProfile?.name,
                            viewerAvatarUrl = viewerProfile?.avatarUrl
                        )
                    }
                    when {
                        openedCommitSha != null -> {
                            RepoCommitDetailView(
                                sha = openedCommitSha!!,
                                detail = openedCommitDetail,
                                isLoading = commitDetailLoading,
                                commitAvatarResolver = avatarResolver,
                                onBack = {
                                    openedCommitSha = null
                                    openedCommitDetail = null
                                },
                                onOpenParent = { parentSha -> openCommit(parentSha) },
                                onOpenUser = onOpenUser,
                                onOpenFullScreenDiff = { file, allFiles ->
                                    fullScreenDiffContext = file to allFiles
                                }
                            )
                        }
                        showCompareView -> {
                            RepoBranchCompareView(
                                availableRefs = allRefs,
                                baseRef = compareBase,
                                headRef = compareHead,
                                result = compareResult,
                                isLoading = compareLoading,
                                errorMsg = compareError,
                                commitAvatarResolver = avatarResolver,
                                onSelectBase = { base -> compareBase = base; runCompare(base, compareHead) },
                                onSelectHead = { head -> compareHead = head; runCompare(compareBase, head) },
                                onSwapRefs = {
                                    val nextBase = compareHead
                                    val nextHead = compareBase
                                    compareBase = nextBase
                                    compareHead = nextHead
                                    runCompare(nextBase, nextHead)
                                },
                                onRefresh = { runCompare(compareBase, compareHead, forceRefresh = true) },
                                onBackToCommits = { showCompareView = false },
                                onOpenCommit = { sha -> openCommit(sha) },
                                onOpenUser = onOpenUser,
                                onOpenFullScreenDiff = { file, allFiles ->
                                    fullScreenDiffContext = file to allFiles
                                }
                            )
                        }
                        else -> {
                            RepoCommitsTabContent(
                                currentRef = currentRef,
                                commits = repoCommits,
                                isLoading = commitsLoading,
                                errorMsg = commitsError,
                                parentRepo = effectiveParent,
                                upstreamCompare = upstreamCompare,
                                upstreamCompareLoading = upstreamCompareLoading,
                                forkOnlyCommits = showForkOnlyCommits,
                                commitAvatarResolver = avatarResolver,
                                onBackToCode = { showCommitsView = false },
                                onRefresh = {
                                    loadCommits(currentRef, forceRefresh = true)
                                    effectiveParent?.let { loadUpstreamCompare(it, currentRef, forceRefresh = true) }
                                },
                                onToggleForkOnly = { showForkOnlyCommits = it },
                                onOpenCompare = {
                                    showCompareView = true
                                    val targetBase = effectiveParent?.let { "${it.owner}:${it.defaultBranch}" } ?: compareBase
                                    val targetHead = if (effectiveParent != null) currentRef else compareHead
                                    compareBase = targetBase
                                    compareHead = targetHead
                                    runCompare(targetBase, targetHead)
                                },
                                onOpenCommit = { commit -> openCommit(commit.sha) },
                                onOpenUser = onOpenUser
                            )
                        }
                    }
                } else if (openedFile != null) {
                    val file = openedFile!!
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    val parent = currentPath.substringBeforeLast('/', "")
                                    openedFile = null
                                    if (parent == currentPath) loadDirectory("", currentRef)
                                    else loadDirectory(currentPath, currentRef)
                                }
                            ) {
                                Icon(Lucide.ArrowLeft, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(strings.back)
                            }
                            Text(
                                text = file.path,
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (!file.isBinary && file.sha.isNotBlank() && capabilities.canPush) {
                                IconButton(
                                    onClick = {
                                        editingExistingFile = file
                                        fileMutationError = null
                                        showFileEditorDialog = true
                                    }
                                ) {
                                    Icon(Lucide.Pencil, contentDescription = strings.repoEditFile, modifier = Modifier.size(18.dp))
                                }
                            }
                            if (file.sha.isNotBlank() && capabilities.canPush) {
                                IconButton(
                                    onClick = {
                                        fileMutationError = null
                                        showDeleteFileDialog = true
                                    }
                                ) {
                                    Icon(
                                        Lucide.Trash2,
                                        contentDescription = strings.repoDeleteFile,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                        when {
                            file.isBinary -> Text(strings.repoBinaryFile(file.size))
                            file.name.endsWith(".md", ignoreCase = true) -> {
                                val fileSections = rememberNormalizedMarkdownSections(
                                    content = file.content,
                                    repoOwner = repo.owner,
                                    repoName = repo.name,
                                    branchRef = currentRef,
                                    documentPath = file.path
                                )
                                Surface(
                                    shape = MaterialTheme.shapes.large,
                                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                    modifier = Modifier.weight(1f).fillMaxWidth()
                                ) {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        itemsIndexed(
                                            items = fileSections,
                                            key = { idx, _ -> "file_md_${file.path}_$idx" },
                                            contentType = { _, _ -> "md_section" }
                                        ) { _, section ->
                                            GitHubMarkdownPreNormalized(normalizedContent = section)
                                        }
                                    }
                                }
                            }
                            else -> {
                                CodeViewer(
                                    code = file.content,
                                    filename = file.name,
                                    modifier = Modifier.weight(1f).fillMaxWidth()
                                )
                            }
                        }
                    }
                } else {
                    val readmeSections = rememberNormalizedMarkdownSections(
                        content = if (currentPath.isBlank()) readme.orEmpty() else "",
                        repoOwner = repo.owner,
                        repoName = repo.name,
                        branchRef = currentRef,
                        documentPath = "README.md"
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Breadcrumbs + New File action
                        item(key = "breadcrumbs", contentType = "breadcrumbs") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FlowRow(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    TextButton(onClick = { loadDirectory("", currentRef) }) {
                                        Text(repo.name)
                                    }
                                    val segments = if (currentPath.isBlank()) emptyList() else currentPath.split('/')
                                    segments.forEachIndexed { idx, segment ->
                                        val targetPath = segments.take(idx + 1).joinToString("/")
                                        Text("/", modifier = Modifier.align(Alignment.CenterVertically))
                                        TextButton(onClick = { loadDirectory(targetPath, currentRef) }) {
                                            Text(segment)
                                        }
                                    }
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            openedCommitSha = null
                                            showCompareView = false
                                            showCommitsView = true
                                            loadCommits(currentRef, forceRefresh = false)
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Lucide.GitCommitHorizontal, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(strings.repoCommitsButton, maxLines = 1, softWrap = false)
                                    }
                                    if (capabilities.canPush) {
                                        FilledTonalButton(
                                            onClick = {
                                                editingExistingFile = null
                                                fileMutationError = null
                                                showFileEditorDialog = true
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Lucide.Plus, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(strings.repoNewFileButton, maxLines = 1, softWrap = false)
                                        }
                                    }
                                }
                            }
                        }

                        items(
                            items = entries,
                            key = { it.path },
                            contentType = { "tree_entry" }
                        ) { entry ->
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (entry.type == TreeEntryType.TREE) {
                                                loadDirectory(entry.path, currentRef)
                                            } else {
                                                loadFile(entry.path, currentRef)
                                            }
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (entry.type == TreeEntryType.TREE) Lucide.Folder else Lucide.File,
                                        contentDescription = null,
                                        tint = if (entry.type == TreeEntryType.TREE) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = entry.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (entry.type == TreeEntryType.BLOB && entry.size > 0) {
                                        Text(
                                            text = "${entry.size} B",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        if (readmeSections.isNotEmpty() && currentPath.isBlank()) {
                            item(key = "readme_header", contentType = "readme_header") {
                                Text(
                                    text = "README.md",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 12.dp, start = 4.dp, bottom = 2.dp)
                                )
                            }
                            itemsIndexed(
                                items = readmeSections,
                                key = { idx, _ -> "readme_${repo.fullName}_${currentRef}_$idx" },
                                contentType = { _, _ -> "md_section" }
                            ) { _, section ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    GitHubMarkdownPreNormalized(
                                        normalizedContent = section,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            RepoDetailTab.ACTIONS -> {
                when {
                    openedLogJob != null -> {
                        WorkflowLogViewer(
                            job = openedLogJob!!,
                            logs = jobLogsText,
                            isLoading = jobLogsLoading,
                            onBack = {
                                openedLogJob = null
                                jobLogsText = null
                            }
                        )
                    }
                    openedRun != null -> {
                        WorkflowRunDetailView(
                            run = openedRun!!,
                            jobs = runJobs,
                            isLoading = jobsLoading,
                            actionBusy = actionsBusy,
                            canManageRuns = capabilities.canPush,
                            onBack = {
                                openedRun = null
                                runJobs = null
                            },
                            onRefresh = { openRun(openedRun!!, forceRefresh = true) },
                            onRerun = { failedOnly ->
                                actionsBusy = true
                                scope.launch {
                                    container.repository.rerunWorkflow(repo.owner, repo.name, openedRun!!.id, failedOnly)
                                        .onSuccess {
                                            onMessage(if (failedOnly) strings.notifRerunningFailed else strings.notifRerunningWorkflow)
                                            loadActions(selectedWorkflowId, forceRefresh = true)
                                            openRun(openedRun!!, forceRefresh = true)
                                        }
                                        .onFailure { onMessage(strings.errorMessage(it, strings.actionsRerunFailedError)) }
                                    actionsBusy = false
                                }
                            },
                            onCancel = {
                                actionsBusy = true
                                scope.launch {
                                    container.repository.cancelWorkflowRun(repo.owner, repo.name, openedRun!!.id)
                                        .onSuccess {
                                            onMessage(strings.notifCancelRequested)
                                            loadActions(selectedWorkflowId, forceRefresh = true)
                                            openRun(openedRun!!, forceRefresh = true)
                                        }
                                        .onFailure { onMessage(strings.errorMessage(it, strings.actionsCancelFailedError)) }
                                    actionsBusy = false
                                }
                            },
                            onOpenLogs = { job -> loadJobLog(job) }
                        )
                    }
                    else -> {
                        RepoActionsTabContent(
                            workflows = workflows,
                            selectedWorkflowId = selectedWorkflowId,
                            runs = workflowRuns,
                            isLoading = actionsLoading,
                            errorMsg = actionsError,
                            canDispatch = capabilities.canPush,
                            onSelectWorkflow = { wfId ->
                                selectedWorkflowId = wfId
                                loadActions(wfId, forceRefresh = false)
                            },
                            onRefresh = { loadActions(selectedWorkflowId, forceRefresh = true) },
                            onOpenDispatch = { showDispatchDialog = true },
                            onOpenRun = { run -> openRun(run) }
                        )
                    }
                }
            }

            RepoDetailTab.RELEASES -> {
                RepoReleasesTabContent(
                    releases = releases,
                    tags = repoTags,
                    isLoading = releasesLoading,
                    errorMsg = releasesError,
                    canCreateRelease = capabilities.canPush,
                    onRefresh = { loadReleases(forceRefresh = true) },
                    onOpenCreateRelease = {
                        createReleaseError = null
                        showCreateReleaseDialog = true
                    },
                    onSelectTag = { tag ->
                        currentRef = tag.name
                        currentPath = ""
                        openedFile = null
                        showCommitsView = false
                        showCompareView = false
                        openedCommitSha = null
                        activeTab = RepoDetailTab.CODE
                        loadDirectory("", tag.name)
                    }
                )
            }

            RepoDetailTab.DISCUSSIONS -> {
                val activeNum = openedDiscussionNumber
                if (activeNum != null) {
                    DiscussionDetailView(
                        detail = discussionDetail,
                        isLoading = discussionDetailLoading,
                        commentBusy = discussionCommentBusy,
                        errorMsg = discussionDetailError,
                        canParticipate = capabilities.canCommentOrCreateDiscussion,
                        onBack = {
                            openedDiscussionNumber = null
                            discussionDetail = null
                        },
                        onRefresh = { openDiscussion(activeNum, forceRefresh = true) },
                        onToggleUpvote = { dSummary ->
                            scope.launch {
                                container.repository.toggleDiscussionUpvote(
                                    owner = repo.owner,
                                    repo = repo.name,
                                    subjectId = dSummary.id,
                                    currentlyUpvoted = dSummary.viewerHasUpvoted,
                                    discussionNumber = dSummary.number
                                ).onSuccess { (upvoted, count) ->
                                    discussionDetail = discussionDetail?.let { cur ->
                                        cur.copy(summary = cur.summary.copy(viewerHasUpvoted = upvoted, upvoteCount = count))
                                    }
                                    discussionsData = discussionsData?.let { cur ->
                                        cur.copy(
                                            discussions = cur.discussions.map {
                                                if (it.id == dSummary.id) it.copy(viewerHasUpvoted = upvoted, upvoteCount = count) else it
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        onSubmitComment = { commentBody, replyToId ->
                            val dId = discussionDetail?.summary?.id ?: return@DiscussionDetailView
                            scope.launch {
                                discussionCommentBusy = true
                                container.repository.addDiscussionComment(
                                    owner = repo.owner,
                                    repo = repo.name,
                                    discussionNumber = activeNum,
                                    discussionId = dId,
                                    body = commentBody,
                                    replyToId = replyToId
                                ).onSuccess {
                                    discussionCommentBusy = false
                                    openDiscussion(activeNum, forceRefresh = true)
                                }.onFailure { err ->
                                    discussionCommentBusy = false
                                    discussionDetailError = strings.errorMessage(err, strings.failedPostComment)
                                }
                            }
                        }
                    )
                } else {
                    RepoDiscussionsTabContent(
                        data = discussionsData,
                        selectedCategoryId = selectedDiscussionCategoryId,
                        onSelectCategory = { catId ->
                            selectedDiscussionCategoryId = catId
                            loadDiscussions(catId, forceRefresh = false)
                        },
                        isLoading = discussionsLoading,
                        errorMsg = discussionsError,
                        canParticipate = capabilities.canCommentOrCreateDiscussion,
                        onRefresh = { loadDiscussions(selectedDiscussionCategoryId, forceRefresh = true) },
                        onOpenCreateDiscussion = {
                            createDiscussionError = null
                            showCreateDiscussionDialog = true
                        },
                        onOpenDiscussion = { discussion -> openDiscussion(discussion.number) },
                        onToggleUpvote = { dSummary ->
                            scope.launch {
                                container.repository.toggleDiscussionUpvote(
                                    owner = repo.owner,
                                    repo = repo.name,
                                    subjectId = dSummary.id,
                                    currentlyUpvoted = dSummary.viewerHasUpvoted,
                                    discussionNumber = dSummary.number
                                ).onSuccess { (upvoted, count) ->
                                    discussionsData = discussionsData?.let { cur ->
                                        cur.copy(
                                            discussions = cur.discussions.map {
                                                if (it.id == dSummary.id) it.copy(viewerHasUpvoted = upvoted, upvoteCount = count) else it
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }

            RepoDetailTab.SECURITY -> {
                RepoSecurityTabContent(
                    overview = securityOverview,
                    isLoading = securityLoading,
                    errorMsg = securityError,
                    onRefresh = { loadSecurityOverview(forceRefresh = true) }
                )
            }

            RepoDetailTab.INSIGHTS -> {
                RepoInsightsTabContent(
                    insights = insightsData,
                    isLoading = insightsLoading,
                    errorMsg = insightsError,
                    onRefresh = { loadInsights(forceRefresh = true) },
                    onOpenUser = onOpenUser
                )
            }

            RepoDetailTab.ISSUES -> {
                RepoIssuesOrPullsTabContent(
                    isPullRequests = false,
                    stateFilter = issueStateFilter,
                    onSelectFilter = { issueStateFilter = it },
                    items = repoIssues,
                    isLoading = issuesLoading,
                    errorMsg = issuesError,
                    onRefresh = {
                        when (issuesSection) {
                            RepoIssuesSection.ISSUES -> loadRepoIssues(issueStateFilter, forceRefresh = true)
                            RepoIssuesSection.LABELS -> loadRepoLabelsList(forceRefresh = true)
                            RepoIssuesSection.MILESTONES -> loadRepoMilestonesList(forceRefresh = true)
                        }
                    },
                    onCreateNew = when (issuesSection) {
                        RepoIssuesSection.ISSUES -> if (capabilities.canCreateIssue) {
                            { showCreateIssueDialog = true }
                        } else null
                        RepoIssuesSection.LABELS -> if (capabilities.canPush) {
                            {
                                editingLabel = null
                                labelEditorError = null
                                showLabelEditorDialog = true
                            }
                        } else null
                        RepoIssuesSection.MILESTONES -> if (capabilities.canPush) {
                            {
                                editingMilestone = null
                                milestoneEditorError = null
                                showMilestoneEditorDialog = true
                            }
                        } else null
                    },
                    onOpenItem = { item ->
                        onOpenIssueOrPull(
                            IssuePullTarget(repo.owner, repo.name, item.number, item.title)
                        )
                    },
                    showStateFilters = issuesSection == RepoIssuesSection.ISSUES,
                    headerTitle = when (issuesSection) {
                        RepoIssuesSection.ISSUES -> null
                        RepoIssuesSection.LABELS -> strings.repoIssuesSectionLabels
                        RepoIssuesSection.MILESTONES -> strings.repoIssuesSectionMilestones
                    },
                    sectionPicker = {
                        RepoIssuesSectionDropdownButton(
                            currentSection = issuesSection,
                            onSelectSection = { issuesSection = it }
                        )
                    },
                    refreshDescriptionOverride = when (issuesSection) {
                        RepoIssuesSection.ISSUES -> null
                        RepoIssuesSection.LABELS -> strings.repoRefreshLabels
                        RepoIssuesSection.MILESTONES -> strings.repoRefreshMilestones
                    },
                    customBody = when (issuesSection) {
                        RepoIssuesSection.ISSUES -> null
                        RepoIssuesSection.LABELS -> {
                            {
                                RepoLabelsManagementContent(
                                    labels = repoLabels,
                                    isLoading = labelsLoading,
                                    errorMsg = labelsError,
                                    canManage = capabilities.canPush,
                                    onRefresh = { loadRepoLabelsList(forceRefresh = true) },
                                    onEditLabel = { label ->
                                        editingLabel = label
                                        labelEditorError = null
                                        showLabelEditorDialog = true
                                    },
                                    onDeleteLabel = { label -> deletingLabelTarget = label }
                                )
                            }
                        }
                        RepoIssuesSection.MILESTONES -> {
                            {
                                RepoMilestonesManagementContent(
                                    milestones = repoMilestones,
                                    isLoading = milestonesLoading,
                                    errorMsg = milestonesError,
                                    canManage = capabilities.canPush,
                                    isBusy = milestoneActionBusy,
                                    onRefresh = { loadRepoMilestonesList(forceRefresh = true) },
                                    onEditMilestone = { ms ->
                                        editingMilestone = ms
                                        milestoneEditorError = null
                                        showMilestoneEditorDialog = true
                                    },
                                    onToggleState = { ms ->
                                        val nextState = if (ms.state.equals("open", ignoreCase = true)) "closed" else "open"
                                        scope.launch {
                                            milestoneActionBusy = true
                                            container.repository.updateRepoMilestone(
                                                owner = repo.owner,
                                                repo = repo.name,
                                                number = ms.number,
                                                state = nextState
                                            ).onSuccess {
                                                onMessage(strings.milestoneUpdatedMessage(it.title))
                                                loadRepoMilestonesList(forceRefresh = true)
                                            }.onFailure {
                                                onMessage(strings.errorMessage(it, strings.failedUpdateMilestone))
                                            }
                                            milestoneActionBusy = false
                                        }
                                    },
                                    onDeleteMilestone = { ms -> deletingMilestoneTarget = ms }
                                )
                            }
                        }
                    }
                )
            }

            RepoDetailTab.PULLS -> {
                RepoIssuesOrPullsTabContent(
                    isPullRequests = true,
                    stateFilter = pullStateFilter,
                    onSelectFilter = { pullStateFilter = it },
                    items = repoPulls,
                    isLoading = pullsLoading,
                    errorMsg = pullsError,
                    onRefresh = { loadRepoPulls(pullStateFilter, forceRefresh = true) },
                    onCreateNew = if (capabilities.canPush) {
                        {
                            createPrError = null
                            showCreatePrDialog = true
                        }
                    } else null,
                    onOpenItem = { item ->
                        onOpenIssueOrPull(
                            IssuePullTarget(repo.owner, repo.name, item.number, item.title)
                        )
                    }
                )
            }
        }
        }
        }
    }

    if (showCreateReleaseDialog) {
        CreateReleaseDialog(
            defaultTargetBranch = currentRef.ifBlank { repo.defaultBranch },
            isSubmitting = creatingRelease,
            errorMsg = createReleaseError,
            onDismiss = { showCreateReleaseDialog = false },
            onCreate = { tagName, title, body, targetCommitish, isDraft, isPrerelease, generateNotes ->
                scope.launch {
                    creatingRelease = true
                    createReleaseError = null
                    container.repository.createRelease(
                        owner = repo.owner,
                        repo = repo.name,
                        tagName = tagName,
                        name = title,
                        body = body,
                        targetCommitish = targetCommitish.takeIf { it.isNotBlank() },
                        isDraft = isDraft,
                        isPrerelease = isPrerelease,
                        generateReleaseNotes = generateNotes
                    ).onSuccess {
                        creatingRelease = false
                        showCreateReleaseDialog = false
                        loadReleases(forceRefresh = true)
                        container.repository.getRepoTags(repo.owner, repo.name, forceRefresh = true)
                            .onSuccess { repoTags = it }
                    }.onFailure { err ->
                        creatingRelease = false
                        createReleaseError = strings.errorMessage(err, strings.failedCreateRelease)
                    }
                }
            }
        )
    }

    if (showCreateDiscussionDialog) {
        val discData = discussionsData
        CreateDiscussionDialog(
            categories = discData?.categories.orEmpty(),
            isSubmitting = creatingDiscussion,
            errorMsg = createDiscussionError,
            onDismiss = { showCreateDiscussionDialog = false },
            onCreate = { categoryId, title, body ->
                val repoId = discData?.repositoryId
                if (repoId.isNullOrBlank()) {
                    createDiscussionError = strings.repoMetadataNotReady
                    return@CreateDiscussionDialog
                }
                scope.launch {
                    creatingDiscussion = true
                    createDiscussionError = null
                    container.repository.createDiscussion(
                        owner = repo.owner,
                        repo = repo.name,
                        repositoryId = repoId,
                        categoryId = categoryId,
                        title = title,
                        body = body
                    ).onSuccess { created ->
                        creatingDiscussion = false
                        showCreateDiscussionDialog = false
                        loadDiscussions(selectedDiscussionCategoryId, forceRefresh = true)
                        openDiscussion(created.number, forceRefresh = true)
                    }.onFailure { err ->
                        creatingDiscussion = false
                        createDiscussionError = strings.errorMessage(err, strings.failedCreateDiscussion)
                    }
                }
            }
        )
    }

    if (showCreateIssueDialog) {
        MaxxingFullScreenEditorScaffold(
            title = strings.repoNewIssueTitle,
            subtitle = repo.fullName,
            saveLabel = if (creatingIssue) strings.creating else strings.create,
            canSave = newIssueTitle.isNotBlank() && !creatingIssue,
            isSubmitting = creatingIssue,
            errorMsg = createIssueError,
            bodyValue = newIssueBody,
            onBodyChange = { newIssueBody = it },
            bodyLabel = strings.repoDescriptionMarkdown,
            supportsMarkdownPreview = true,
            onDismiss = {
                if (!creatingIssue) {
                    showCreateIssueDialog = false
                    createIssueError = null
                }
            },
            onSave = {
                val title = newIssueTitle.trim()
                val body = newIssueBody
                val labels = if (capabilities.canTriage) selectedNewIssueLabels.toList() else emptyList()
                creatingIssue = true
                createIssueError = null
                scope.launch {
                    container.repository.createIssue(repo.owner, repo.name, title, body, labels)
                        .onSuccess { createdNumber ->
                            creatingIssue = false
                            showCreateIssueDialog = false
                            newIssueTitle = ""
                            newIssueBody = ""
                            selectedNewIssueLabels = emptySet()
                            onMessage(strings.repoCreatedIssueMessage(createdNumber))
                            onOpenIssueOrPull(IssuePullTarget(repo.owner, repo.name, createdNumber, title))
                        }
                        .onFailure { err ->
                            creatingIssue = false
                            createIssueError = strings.errorMessage(err, strings.repoCreateIssueFailed)
                        }
                }
            },
            headerContent = {
                OutlinedTextField(
                    value = newIssueTitle,
                    onValueChange = { newIssueTitle = it },
                    enabled = !creatingIssue,
                    label = { Text(strings.repoIssueTitleLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (capabilities.canTriage) {
                    val selectedLabelTags = remember(availableIssueLabels, selectedNewIssueLabels) {
                        val byName = availableIssueLabels.associateBy { it.name }
                        selectedNewIssueLabels.map { name -> byName[name] ?: LabelTag(name = name, colorHex = "6E40C9") }
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        itemVerticalAlignment = Alignment.CenterVertically
                    ) {
                        AssistChip(
                            onClick = { showIssueLabelPicker = true },
                            enabled = !creatingIssue,
                            leadingIcon = { Icon(Lucide.Tag, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            label = {
                                Text(
                                    if (selectedNewIssueLabels.isEmpty()) strings.labelsTitle
                                    else strings.issueLabelsCount(selectedNewIssueLabels.size)
                                )
                            }
                        )
                        selectedLabelTags.forEach { tag ->
                            GitHubLabelChip(
                                name = tag.name,
                                colorHex = tag.colorHex,
                                modifier = Modifier.clickable(enabled = !creatingIssue) {
                                    selectedNewIssueLabels = selectedNewIssueLabels - tag.name
                                }
                            )
                        }
                    }
                }
            }
        )

        if (showIssueLabelPicker) {
            RepoLabelsPickerDialog(
                title = strings.selectLabelsTitle,
                availableLabels = availableIssueLabels,
                selectedLabelNames = selectedNewIssueLabels,
                onToggleLabel = { labelName, isChecked ->
                    selectedNewIssueLabels = if (isChecked) {
                        selectedNewIssueLabels + labelName
                    } else {
                        selectedNewIssueLabels - labelName
                    }
                },
                onDismiss = { showIssueLabelPicker = false },
                onConfirm = { showIssueLabelPicker = false },
                confirmLabel = strings.done,
                isLoading = issueLabelsLoading,
                isBusy = issueLabelActionBusy,
                canCreateLabel = capabilities.canPush,
                newLabelName = newIssueLabelName,
                onNewLabelNameChange = { newIssueLabelName = it },
                onCreateLabel = { name ->
                    scope.launch {
                        issueLabelActionBusy = true
                        container.repository.createRepoLabel(repo.owner, repo.name, name, "6E40C9")
                            .onSuccess { created ->
                                availableIssueLabels = (availableIssueLabels + created).distinctBy { it.name }
                                selectedNewIssueLabels = selectedNewIssueLabels + created.name
                                newIssueLabelName = ""
                                onMessage(strings.createdLabelMessage(created.name))
                            }
                            .onFailure { onMessage(strings.errorMessage(it, strings.failedCreateLabel)) }
                        issueLabelActionBusy = false
                    }
                },
                onDeleteLabel = { label ->
                    scope.launch {
                        issueLabelActionBusy = true
                        container.repository.deleteRepoLabel(repo.owner, repo.name, label.name)
                            .onSuccess {
                                availableIssueLabels = availableIssueLabels.filterNot { it.name == label.name }
                                repoLabels = repoLabels?.filterNot { it.name == label.name }
                                selectedNewIssueLabels = selectedNewIssueLabels - label.name
                                onMessage(strings.labelDeletedMessage(label.name))
                            }
                            .onFailure { onMessage(strings.errorMessage(it, strings.failedDeleteLabel)) }
                        issueLabelActionBusy = false
                    }
                }
            )
        }
    }

    if (showLabelEditorDialog) {
        RepoLabelEditorDialog(
            editingLabel = editingLabel,
            isSubmitting = labelEditorSubmitting,
            errorMsg = labelEditorError,
            onDismiss = {
                showLabelEditorDialog = false
                labelEditorError = null
            },
            onSave = { name, colorHex, description ->
                scope.launch {
                    labelEditorSubmitting = true
                    labelEditorError = null
                    val targetEditing = editingLabel
                    val result = if (targetEditing == null) {
                        container.repository.createRepoLabel(
                            owner = repo.owner,
                            repo = repo.name,
                            name = name,
                            colorHex = colorHex,
                            description = description
                        )
                    } else {
                        container.repository.updateRepoLabel(
                            owner = repo.owner,
                            repo = repo.name,
                            currentName = targetEditing.name,
                            newName = name,
                            colorHex = colorHex,
                            description = description
                        )
                    }
                    result.onSuccess { saved ->
                        showLabelEditorDialog = false
                        editingLabel = null
                        onMessage(
                            if (targetEditing == null) strings.createdLabelMessage(saved.name)
                            else strings.labelUpdatedMessage(saved.name)
                        )
                        loadRepoLabelsList(forceRefresh = true)
                    }.onFailure { err ->
                        labelEditorError = strings.errorMessage(
                            err,
                            if (targetEditing == null) strings.failedCreateLabel else strings.failedUpdateLabel
                        )
                    }
                    labelEditorSubmitting = false
                }
            }
        )
    }

    deletingLabelTarget?.let { targetLabel ->
        AlertDialog(
            onDismissRequest = { if (!labelDeleteBusy) deletingLabelTarget = null },
            title = { Text(strings.labelDeleteTitle) },
            text = { Text(strings.labelDeleteConfirm(targetLabel.name)) },
            confirmButton = {
                Button(
                    enabled = !labelDeleteBusy,
                    onClick = {
                        scope.launch {
                            labelDeleteBusy = true
                            container.repository.deleteRepoLabel(repo.owner, repo.name, targetLabel.name)
                                .onSuccess {
                                    deletingLabelTarget = null
                                    availableIssueLabels = availableIssueLabels.filterNot { it.name == targetLabel.name }
                                    selectedNewIssueLabels = selectedNewIssueLabels - targetLabel.name
                                    onMessage(strings.labelDeletedMessage(targetLabel.name))
                                    loadRepoLabelsList(forceRefresh = true)
                                }
                                .onFailure { err ->
                                    deletingLabelTarget = null
                                    onMessage(strings.errorMessage(err, strings.failedDeleteLabel))
                                }
                            labelDeleteBusy = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(strings.deleteAction)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !labelDeleteBusy,
                    onClick = { deletingLabelTarget = null }
                ) {
                    Text(strings.cancel)
                }
            }
        )
    }

    if (showMilestoneEditorDialog) {
        RepoMilestoneEditorDialog(
            editingMilestone = editingMilestone,
            isSubmitting = milestoneEditorSubmitting,
            errorMsg = milestoneEditorError,
            onDismiss = {
                showMilestoneEditorDialog = false
                milestoneEditorError = null
            },
            onSave = { title, description, dueOnIso, clearDueOn ->
                scope.launch {
                    milestoneEditorSubmitting = true
                    milestoneEditorError = null
                    val targetEditing = editingMilestone
                    val result = if (targetEditing == null) {
                        container.repository.createRepoMilestone(
                            owner = repo.owner,
                            repo = repo.name,
                            title = title,
                            description = description,
                            dueOn = dueOnIso
                        )
                    } else {
                        container.repository.updateRepoMilestone(
                            owner = repo.owner,
                            repo = repo.name,
                            number = targetEditing.number,
                            title = title,
                            description = description,
                            dueOn = dueOnIso,
                            clearDueOn = clearDueOn
                        )
                    }
                    result.onSuccess { saved ->
                        showMilestoneEditorDialog = false
                        editingMilestone = null
                        onMessage(
                            if (targetEditing == null) strings.milestoneCreatedMessage(saved.title)
                            else strings.milestoneUpdatedMessage(saved.title)
                        )
                        loadRepoMilestonesList(forceRefresh = true)
                    }.onFailure { err ->
                        milestoneEditorError = strings.errorMessage(
                            err,
                            if (targetEditing == null) strings.failedCreateMilestone else strings.failedUpdateMilestone
                        )
                    }
                    milestoneEditorSubmitting = false
                }
            }
        )
    }

    deletingMilestoneTarget?.let { targetMilestone ->
        AlertDialog(
            onDismissRequest = { if (!milestoneDeleteBusy) deletingMilestoneTarget = null },
            title = { Text(strings.milestoneDeleteTitle) },
            text = { Text(strings.milestoneDeleteConfirm(targetMilestone.title)) },
            confirmButton = {
                Button(
                    enabled = !milestoneDeleteBusy,
                    onClick = {
                        scope.launch {
                            milestoneDeleteBusy = true
                            container.repository.deleteRepoMilestone(repo.owner, repo.name, targetMilestone.number)
                                .onSuccess {
                                    deletingMilestoneTarget = null
                                    onMessage(strings.milestoneDeletedMessage(targetMilestone.title))
                                    loadRepoMilestonesList(forceRefresh = true)
                                }
                                .onFailure { err ->
                                    deletingMilestoneTarget = null
                                    onMessage(strings.errorMessage(err, strings.failedDeleteMilestone))
                                }
                            milestoneDeleteBusy = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(strings.deleteAction)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !milestoneDeleteBusy,
                    onClick = { deletingMilestoneTarget = null }
                ) {
                    Text(strings.cancel)
                }
            }
        )
    }

    fullScreenDiffContext?.let { (activeFile, allFiles) ->
        MaxxingFullScreenDiffViewer(
            file = activeFile,
            files = allFiles.ifEmpty { listOf(activeFile) },
            onDismiss = { fullScreenDiffContext = null },
            onSelectFile = { next -> fullScreenDiffContext = next to allFiles },
            backEnabled = !showPeopleDialog &&
                !showCreatePrDialog &&
                !showFileEditorDialog &&
                !showDeleteFileDialog &&
                !showCreateIssueDialog &&
                !showDispatchDialog &&
                !showCreateReleaseDialog &&
                !showCreateDiscussionDialog
        )
    }

    if (showPeopleDialog) {
        AlertDialog(
            onDismissRequest = { showPeopleDialog = false },
            title = { Text(strings.repoNetworkTitle(repo.name)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryScrollableTabRow(
                        selectedTabIndex = RepoPeopleTab.entries.indexOf(peopleTab).coerceAtLeast(0),
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        edgePadding = 0.dp
                    ) {
                        RepoPeopleTab.entries.forEach { tab ->
                            Tab(
                                selected = peopleTab == tab,
                                onClick = { openPeopleTab(tab) },
                                text = { Text(tab.localizedLabel(strings), maxLines = 1, softWrap = false) }
                            )
                        }
                    }
                    if (peopleLoading) {
                        MaxxingWavyProgress()
                    }
                    peopleError?.let { err ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { openPeopleTab(peopleTab) }) {
                                Text(strings.retry)
                            }
                        }
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth().height(280.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (peopleTab) {
                            RepoPeopleTab.STARGAZERS -> {
                                val list = stargazersList.orEmpty()
                                if (!peopleLoading && peopleError == null && list.isEmpty()) {
                                    Text(strings.repoNoStargazers, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                list.forEach { user ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                showPeopleDialog = false
                                                onOpenUser(user.login)
                                            }
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        UserAvatar(user.avatarUrl, user.login, sizeDp = 28)
                                        Text("@${user.login}", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                            RepoPeopleTab.FORKS -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = forksSort == "stargazers",
                                        onClick = { openPeopleTab(RepoPeopleTab.FORKS, forkSortOverride = "stargazers") },
                                        label = { Text(strings.repoForkSortStarred) }
                                    )
                                    FilterChip(
                                        selected = forksSort == "newest",
                                        onClick = { openPeopleTab(RepoPeopleTab.FORKS, forkSortOverride = "newest") },
                                        label = { Text(strings.repoForkSortNewest) }
                                    )
                                }
                                val list = forksList.orEmpty()
                                if (!peopleLoading && peopleError == null && list.isEmpty()) {
                                    Text(strings.repoNoForks, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                list.forEach { fork ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                showPeopleDialog = false
                                                onOpenRepo(fork)
                                            }
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(Lucide.GitFork, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(fork.fullName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            val meta = buildString {
                                                append(fork.defaultBranch)
                                                if (fork.updatedAt.length >= 10) {
                                                    append(" · ${strings.repoUpdatedDate(fork.updatedAt.take(10))}")
                                                }
                                            }
                                            Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Icon(MaxxingFilledIcons.Star, contentDescription = null, tint = MaxxingStarAmber, modifier = Modifier.size(13.dp))
                                        Text(formatCompactCount(fork.stargazersCount), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            RepoPeopleTab.CONTRIBUTORS -> {
                                val list = contributorsList.orEmpty()
                                if (!peopleLoading && peopleError == null && list.isEmpty()) {
                                    Text(strings.repoNoContributors, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                list.forEach { user ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                showPeopleDialog = false
                                                onOpenUser(user.login)
                                            }
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        UserAvatar(user.avatarUrl, user.login, sizeDp = 28)
                                        Text("@${user.login}", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPeopleDialog = false }) { Text(strings.closeAction) }
            }
        )
    }

    if (showCreatePrDialog) {
        val branchNames = detail?.branches.orEmpty().map { it.name }.ifEmpty { listOf(currentRef, repo.defaultBranch).distinct() }
        val defaultBase = detail?.summary?.defaultBranch ?: repo.defaultBranch
        val defaultHead = branchNames.firstOrNull { it != defaultBase } ?: currentRef
        CreatePullRequestDialog(
            branches = branchNames,
            initialHead = defaultHead,
            initialBase = defaultBase,
            isSubmitting = creatingPr,
            errorMsg = createPrError,
            onDismiss = { showCreatePrDialog = false },
            onCreate = { title, body, head, base, isDraft ->
                scope.launch {
                    creatingPr = true
                    createPrError = null
                    container.repository.createPullRequest(
                        owner = repo.owner,
                        repo = repo.name,
                        title = title,
                        body = body,
                        head = head,
                        base = base,
                        draft = isDraft
                    ).onSuccess { createdNumber ->
                        creatingPr = false
                        showCreatePrDialog = false
                        onMessage(strings.repoCreatedPrMessage(createdNumber))
                        loadRepoPulls(pullStateFilter, forceRefresh = true)
                        onOpenIssueOrPull(IssuePullTarget(repo.owner, repo.name, createdNumber, title))
                    }.onFailure { err ->
                        creatingPr = false
                        createPrError = strings.errorMessage(err, strings.failedCreatePr)
                    }
                }
            }
        )
    }

    if (showFileEditorDialog) {
        val editing = editingExistingFile
        val defaultPath = if (editing != null) {
            editing.path
        } else if (currentPath.isBlank()) {
            ""
        } else {
            "${currentPath.trimEnd('/')}/"
        }
        RepoFileEditorDialog(
            isCreating = editing == null,
            initialPath = defaultPath,
            initialContent = editing?.content.orEmpty(),
            initialBranch = currentRef,
            isSubmitting = fileMutationBusy,
            errorMsg = fileMutationError,
            onDismiss = { if (!fileMutationBusy) showFileEditorDialog = false },
            onCommit = { targetPath, updatedContent, commitMessage, branchName ->
                scope.launch {
                    fileMutationBusy = true
                    fileMutationError = null
                    container.repository.upsertRepoFile(
                        owner = repo.owner,
                        repo = repo.name,
                        path = targetPath,
                        content = updatedContent,
                        message = commitMessage,
                        branch = branchName,
                        existingSha = editing?.sha
                    ).onSuccess { commitSha ->
                        fileMutationBusy = false
                        showFileEditorDialog = false
                        if (branchName.isNotBlank() && branchName != currentRef) {
                            currentRef = branchName
                        }
                        val targetRef = branchName.ifBlank { currentRef }
                        val short = commitSha.take(7)
                        onMessage(strings.fileCommittedMessage(targetPath, short))
                        if (editing != null) {
                            loadFile(targetPath, targetRef)
                        } else {
                            loadDirectory(currentPath, targetRef, forceRefresh = true)
                        }
                    }.onFailure { err ->
                        fileMutationBusy = false
                        fileMutationError = strings.errorMessage(err, strings.fileCommitFailed)
                    }
                }
            }
        )
    }

    if (showDeleteFileDialog && openedFile != null) {
        val fileToDelete = openedFile!!
        DeleteRepoFileDialog(
            filePath = fileToDelete.path,
            initialBranch = currentRef,
            isSubmitting = fileMutationBusy,
            errorMsg = fileMutationError,
            onDismiss = { if (!fileMutationBusy) showDeleteFileDialog = false },
            onDelete = { commitMessage, branchName ->
                scope.launch {
                    fileMutationBusy = true
                    fileMutationError = null
                    container.repository.deleteRepoFile(
                        owner = repo.owner,
                        repo = repo.name,
                        path = fileToDelete.path,
                        sha = fileToDelete.sha,
                        message = commitMessage,
                        branch = branchName
                    ).onSuccess { commitSha ->
                        fileMutationBusy = false
                        showDeleteFileDialog = false
                        openedFile = null
                        val targetRef = branchName.ifBlank { currentRef }
                        if (targetRef != currentRef) currentRef = targetRef
                        val short = commitSha.take(7)
                        onMessage(strings.fileDeletedMessage(fileToDelete.name, short))
                        loadDirectory(currentPath, targetRef, forceRefresh = true)
                    }.onFailure { err ->
                        fileMutationBusy = false
                        fileMutationError = strings.errorMessage(err, strings.fileDeleteFailed)
                    }
                }
            }
        )
    }

    if (showDispatchDialog) {
        DispatchWorkflowDialog(
            workflows = workflows.filter { it.state == "active" }.ifEmpty { workflows },
            branches = detail?.branches.orEmpty().map { it.name }.ifEmpty { listOf(currentRef) },
            defaultBranch = currentRef,
            busy = actionsBusy,
            onDismiss = { if (!actionsBusy) showDispatchDialog = false },
            onDispatch = { workflowId, ref, inputs ->
                actionsBusy = true
                scope.launch {
                    try {
                        container.repository.dispatchWorkflow(repo.owner, repo.name, workflowId, ref, inputs)
                            .onSuccess {
                                showDispatchDialog = false
                                onMessage(strings.actionsDispatchedMessage(ref))
                                loadActions(selectedWorkflowId, forceRefresh = true)
                            }
                            .onFailure { onMessage(strings.errorMessage(it, strings.actionsDispatchFailed)) }
                    } finally {
                        actionsBusy = false
                    }
                }
            }
        )
    }
    }
}


@Composable
private fun CreatePullRequestDialog(
    branches: List<String>,
    initialHead: String,
    initialBase: String,
    isSubmitting: Boolean,
    errorMsg: String?,
    onDismiss: () -> Unit,
    onCreate: (title: String, body: String, head: String, base: String, draft: Boolean) -> Unit
) {
    val strings = appStrings()
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var head by remember(initialHead) { mutableStateOf(initialHead) }
    var base by remember(initialBase) { mutableStateOf(initialBase) }
    var isDraft by remember { mutableStateOf(false) }

    MaxxingFullScreenEditorScaffold(
        title = strings.repoNewPrTitle,
        subtitle = "${head.ifBlank { "head" }} → ${base.ifBlank { "base" }}",
        saveLabel = if (isSubmitting) strings.creating else strings.repoCreatePrButton,
        canSave = title.trim().isNotBlank() && head.isNotBlank() && base.isNotBlank(),
        isSubmitting = isSubmitting,
        errorMsg = errorMsg,
        bodyValue = body,
        onBodyChange = { body = it },
        bodyLabel = strings.repoDescriptionMarkdown,
        supportsMarkdownPreview = true,
        onDismiss = onDismiss,
        onSave = { onCreate(title.trim(), body.trim(), head.trim(), base.trim(), isDraft) },
        headerContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RefSelectorDropdown(
                    label = strings.repoHeadLabel,
                    selectedRef = head,
                    options = branches,
                    onSelect = { head = it },
                    modifier = Modifier.weight(1f)
                )
                RefSelectorDropdown(
                    label = strings.repoBaseLabel,
                    selectedRef = base,
                    options = branches,
                    onSelect = { base = it },
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                enabled = !isSubmitting,
                label = { Text(strings.repoPrTitleLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(strings.repoDraftPrToggle, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = isDraft, onCheckedChange = { isDraft = it }, enabled = !isSubmitting)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepoIssuesOrPullsTabContent(
    isPullRequests: Boolean,
    stateFilter: IssueStateFilter,
    onSelectFilter: (IssueStateFilter) -> Unit,
    items: List<IssueOrPullSummary>?,
    isLoading: Boolean,
    errorMsg: String?,
    onRefresh: () -> Unit,
    onCreateNew: (() -> Unit)? = null,
    onOpenItem: (IssueOrPullSummary) -> Unit,
    showStateFilters: Boolean = true,
    headerTitle: String? = null,
    sectionPicker: (@Composable () -> Unit)? = null,
    refreshDescriptionOverride: String? = null,
    customBody: (@Composable () -> Unit)? = null
) {
    val strings = appStrings()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showStateFilters) {
                IssueStateFilter.entries.forEach { sf ->
                    FilterChip(
                        selected = stateFilter == sf,
                        onClick = { onSelectFilter(sf) },
                        label = { Text(strings.issueStateFilterLabel(sf)) }
                    )
                }
            } else if (!headerTitle.isNullOrBlank()) {
                Text(
                    text = headerTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.weight(1f))
            sectionPicker?.invoke()
            if (onCreateNew != null) {
                OutlinedButton(
                    onClick = onCreateNew,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Lucide.Plus, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.newAction, maxLines = 1, softWrap = false)
                }
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    Lucide.RefreshCw,
                    contentDescription = refreshDescriptionOverride
                        ?: if (isPullRequests) strings.repoRefreshPulls else strings.repoRefreshIssues,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (customBody != null) {
            customBody()
            return@Column
        }

        if (isLoading && !items.isNullOrEmpty()) {
            MaxxingWavyProgress(modifier = Modifier.padding(horizontal = 16.dp))
        }

        PullToRefreshBox(
            isRefreshing = isLoading && !items.isNullOrEmpty(),
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                isLoading && items == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        MaxxingLoadingState(
                            message = if (isPullRequests) strings.repoLoadingPulls else strings.repoLoadingIssues
                        )
                    }
                }

                errorMsg != null && items.isNullOrEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(errorMsg, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(10.dp))
                        FilledTonalButton(onClick = onRefresh) {
                            Text(strings.retry)
                        }
                    }
                }

                items.isNullOrEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (!isPullRequests) Lucide.CircleDot else Lucide.GitPullRequest,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = strings.repoItemsEmptyTitle(isPullRequests, stateFilter),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = strings.repoItemsEmptyBody(isPullRequests, stateFilter),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = items,
                            key = { it.id },
                            contentType = { "repo_issue_pull_summary" }
                        ) { item ->
                            IssuePullSummaryCard(item = item) {
                                onOpenItem(item)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepoCommitsTabContent(
    currentRef: String,
    commits: List<CommitSummary>?,
    isLoading: Boolean,
    errorMsg: String?,
    parentRepo: RepoParentSummary? = null,
    upstreamCompare: BranchCompareResult? = null,
    upstreamCompareLoading: Boolean = false,
    forkOnlyCommits: Boolean = false,
    commitAvatarResolver: (CommitSummary) -> String = { it.authorAvatarUrl },
    onBackToCode: () -> Unit,
    onRefresh: () -> Unit,
    onToggleForkOnly: (Boolean) -> Unit = {},
    onOpenCompare: () -> Unit,
    onOpenCommit: (CommitSummary) -> Unit,
    onOpenUser: (String) -> Unit
) {
    val strings = appStrings()
    val displayedCommits = if (forkOnlyCommits && upstreamCompare != null && upstreamCompare.aheadBy > 0) {
        upstreamCompare.commits.asReversed()
    } else {
        commits
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = onBackToCode,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(Lucide.ArrowLeft, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(strings.repoDetailTabCode, maxLines = 1, softWrap = false)
            }
            ExpressiveStatusPill(
                label = currentRef,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                icon = Lucide.GitBranch
            )
            OutlinedButton(
                onClick = onOpenCompare,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(Lucide.ArrowLeftRight, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
                Text(if (parentRepo != null) strings.repoCompareUpstream else strings.repoCompare, maxLines = 1, softWrap = false)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRefresh) {
                Icon(Lucide.RefreshCw, contentDescription = strings.refreshCommitsDesc, modifier = Modifier.size(18.dp))
            }
        }

        if (parentRepo != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val upstreamLabel = "${parentRepo.owner}:${parentRepo.defaultBranch}"
                    val statusLine = when {
                        upstreamCompareLoading && upstreamCompare == null ->
                            strings.repoCheckingDivergence(upstreamLabel)
                        upstreamCompare == null ->
                            strings.repoForkedFrom(parentRepo.fullName)
                        upstreamCompare.aheadBy == 0 && upstreamCompare.behindBy == 0 ->
                            strings.repoEvenWithUpstream(upstreamLabel, currentRef)
                        upstreamCompare.aheadBy == 0 ->
                            strings.repoZeroAheadBehind(upstreamCompare.behindBy, upstreamLabel)
                        else ->
                            strings.repoAheadBehind(upstreamCompare.aheadBy, upstreamCompare.behindBy, upstreamLabel)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Lucide.GitFork, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = statusLine,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (upstreamCompare != null && upstreamCompare.aheadBy > 0) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !forkOnlyCommits,
                                onClick = { onToggleForkOnly(false) },
                                label = { Text(strings.repoAllCommits) }
                            )
                            FilterChip(
                                selected = forkOnlyCommits,
                                onClick = { onToggleForkOnly(true) },
                                label = { Text(strings.repoForkOnlyCommits(upstreamCompare.aheadBy)) }
                            )
                        }
                    }
                }
            }
        }

        if (isLoading && !displayedCommits.isNullOrEmpty()) {
            MaxxingWavyProgress(modifier = Modifier.padding(horizontal = 16.dp))
        }

        PullToRefreshBox(
            isRefreshing = isLoading && !displayedCommits.isNullOrEmpty(),
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                isLoading && displayedCommits == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        MaxxingLoadingState(message = strings.repoLoadingCommits)
                    }
                }
                errorMsg != null && displayedCommits.isNullOrEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(errorMsg, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(10.dp))
                        FilledTonalButton(onClick = onRefresh) { Text(strings.retry) }
                    }
                }
                displayedCommits.isNullOrEmpty() -> {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(strings.repoNoCommitsFound(currentRef), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(items = displayedCommits, key = { it.sha }, contentType = { "commit_summary" }) { commit ->
                            CommitSummaryCard(
                                commit = commit,
                                resolvedAvatarUrl = commitAvatarResolver(commit),
                                onClick = { onOpenCommit(commit) },
                                onOpenUser = onOpenUser
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun resolveCommitAuthorAvatarUrl(
    commit: CommitSummary,
    repoOwner: String,
    repoOwnerAvatarUrl: String,
    viewerLogin: String?,
    viewerName: String?,
    viewerAvatarUrl: String?
): String {
    if (commit.authorAvatarUrl.isNotBlank()) return commit.authorAvatarUrl
    val loginMatchesViewer = !viewerLogin.isNullOrBlank() && (
        commit.authorLogin.equals(viewerLogin, ignoreCase = true) ||
            commit.authorName.equals(viewerLogin, ignoreCase = true) ||
            (!viewerName.isNullOrBlank() && commit.authorName.equals(viewerName, ignoreCase = true)) ||
            (commit.authorLogin == null && repoOwner.equals(viewerLogin, ignoreCase = true))
        )
    if (loginMatchesViewer) {
        viewerAvatarUrl?.takeIf { it.isNotBlank() }?.let { return it }
        repoOwnerAvatarUrl.takeIf { it.isNotBlank() }?.let { return it }
        viewerLogin?.let { return "https://avatars.githubusercontent.com/$it?size=96" }
    }
    if (commit.authorLogin == null && commit.authorName.equals(repoOwner, ignoreCase = true)) {
        if (repoOwnerAvatarUrl.isNotBlank()) return repoOwnerAvatarUrl
        return "https://avatars.githubusercontent.com/$repoOwner?size=96"
    }
    commit.authorLogin?.takeIf { it.isNotBlank() }?.let { login ->
        return "https://avatars.githubusercontent.com/$login?size=96"
    }
    return ""
}

@Composable
private fun CommitSummaryCard(
    commit: CommitSummary,
    resolvedAvatarUrl: String = commit.authorAvatarUrl,
    onClick: () -> Unit,
    onOpenUser: (String) -> Unit
) {
    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            UserAvatar(
                avatarUrl = resolvedAvatarUrl.ifBlank { commit.authorAvatarUrl },
                login = commit.authorLogin ?: commit.authorName,
                sizeDp = 34,
                modifier = Modifier.clickable(enabled = !commit.authorLogin.isNullOrBlank()) {
                    commit.authorLogin?.let(onOpenUser)
                }
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = commit.headline,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = commit.shortSha,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (commit.isVerified) {
                        Icon(
                            Lucide.ShieldCheck,
                            contentDescription = appStrings().commitVerifiedDesc,
                            tint = Color(0xFF16A34A),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = commit.authorLogin?.let { "@$it" } ?: commit.authorName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (commit.committedAt.length >= 10) {
                        Text(
                            text = commit.committedAt.take(10),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepoCommitDetailView(
    sha: String,
    detail: CommitDetail?,
    isLoading: Boolean,
    commitAvatarResolver: (CommitSummary) -> String = { it.authorAvatarUrl },
    onBack: () -> Unit,
    onOpenParent: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenFullScreenDiff: (PullFileChange, List<PullFileChange>) -> Unit = { _, _ -> }
) {
    val strings = appStrings()
    val expandedFiles = remember(sha) { mutableStateMapOf<String, Boolean>() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilledTonalButton(onClick = onBack) {
                Icon(Lucide.ArrowLeft, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text(strings.repoCommitsButton)
            }
            Text(
                text = sha.take(7),
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.primary
            )
            detail?.let { d ->
                Spacer(Modifier.weight(1f))
                Text(
                    text = strings.commitFilesChangedSummary(d.additions, d.deletions, d.changedFilesCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        when {
            isLoading && detail == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    MaxxingLoadingState(message = strings.commitLoading(sha.take(7)))
                }
            }
            detail == null -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(strings.commitDetailsUnavailable, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "commit_header") {
                        ElevatedCard(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(detail.summary.headline, style = MaterialTheme.typography.titleMedium)
                                if (detail.summary.body.isNotBlank()) {
                                    Text(
                                        text = detail.summary.body,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    UserAvatar(
                                        commitAvatarResolver(detail.summary).ifBlank { detail.summary.authorAvatarUrl },
                                        detail.summary.authorLogin ?: detail.summary.authorName,
                                        sizeDp = 24,
                                        modifier = Modifier.clickable(enabled = !detail.summary.authorLogin.isNullOrBlank()) {
                                            detail.summary.authorLogin?.let(onOpenUser)
                                        }
                                    )
                                    Text(
                                        detail.summary.authorLogin?.let { "@$it" } ?: detail.summary.authorName,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    if (detail.summary.isVerified) {
                                        ExpressiveStatusPill(
                                            label = strings.commitVerifiedBadge,
                                            containerColor = Color(0xFF16A34A).copy(alpha = 0.16f),
                                            contentColor = Color(0xFF16A34A),
                                            icon = Lucide.ShieldCheck
                                        )
                                    }
                                    Spacer(Modifier.weight(1f))
                                    if (detail.summary.committedAt.length >= 10) {
                                        Text(
                                            detail.summary.committedAt.take(10),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (detail.summary.parentShas.isNotEmpty()) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        detail.summary.parentShas.forEach { parent ->
                                            AssistChip(
                                                onClick = { onOpenParent(parent) },
                                                leadingIcon = { Icon(Lucide.GitCommitHorizontal, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                                label = { Text(strings.commitParentLabel(parent.take(7)), fontFamily = FontFamily.Monospace) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    detail.files.forEach { file ->
                        val isExpanded = expandedFiles[file.filename] ?: (detail.files.size <= 3)
                        diffFileLazyItems(
                            file = file,
                            expanded = isExpanded,
                            onToggleExpand = { expandedFiles[file.filename] = !isExpanded },
                            onOpenFullScreen = { onOpenFullScreenDiff(file, detail.files) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RepoBranchCompareView(
    availableRefs: List<String>,
    baseRef: String,
    headRef: String,
    result: BranchCompareResult?,
    isLoading: Boolean,
    errorMsg: String?,
    onSelectBase: (String) -> Unit,
    onSelectHead: (String) -> Unit,
    onSwapRefs: () -> Unit,
    onRefresh: () -> Unit,
    onBackToCommits: () -> Unit,
    onOpenCommit: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    commitAvatarResolver: (CommitSummary) -> String = { it.authorAvatarUrl },
    onOpenFullScreenDiff: (PullFileChange, List<PullFileChange>) -> Unit = { _, _ -> }
) {
    val strings = appStrings()
    val expandedFiles = remember(baseRef, headRef) { mutableStateMapOf<String, Boolean>() }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(onClick = onBackToCommits) {
                    Icon(Lucide.ArrowLeft, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.repoCommitsButton)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRefresh) {
                    Icon(Lucide.RefreshCw, contentDescription = strings.refreshCompareDesc, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RefSelectorDropdown(
                    label = strings.repoBaseLabel,
                    selectedRef = baseRef,
                    options = availableRefs,
                    onSelect = onSelectBase,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onSwapRefs) {
                    Icon(Lucide.ArrowLeftRight, contentDescription = strings.swapBaseHeadDesc, modifier = Modifier.size(16.dp))
                }
                RefSelectorDropdown(
                    label = strings.repoHeadLabel,
                    selectedRef = headRef,
                    options = availableRefs,
                    onSelect = onSelectHead,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    MaxxingLoadingState(message = strings.compareLoading(baseRef, headRef))
                }
            }
            errorMsg != null -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(errorMsg, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(10.dp))
                    FilledTonalButton(onClick = onRefresh) { Text(strings.retry) }
                }
            }
            result == null -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(strings.compareSelectRefs, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "compare_summary") {
                        ElevatedCard(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ExpressiveStatusPill(
                                        label = strings.formatCompareStatusLabel(result.status),
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        icon = Lucide.ArrowLeftRight
                                    )
                                    Text(
                                        text = strings.compareAheadBehind(result.aheadBy, result.behindBy),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                                Text(
                                    text = strings.compareSummaryCounts(result.totalCommits, result.files.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (result.commits.isNotEmpty()) {
                        item(key = "compare_commits_title") {
                            Text(strings.compareCommitsHeader(result.commits.size), style = MaterialTheme.typography.titleSmall)
                        }
                        items(items = result.commits, key = { "cmp_${it.sha}" }, contentType = { "commit_summary" }) { commit ->
                            CommitSummaryCard(
                                commit = commit,
                                resolvedAvatarUrl = commitAvatarResolver(commit),
                                onClick = { onOpenCommit(commit.sha) },
                                onOpenUser = onOpenUser
                            )
                        }
                    }

                    if (result.files.isNotEmpty()) {
                        item(key = "compare_files_title") {
                            Text(strings.compareChangedFilesHeader(result.files.size), style = MaterialTheme.typography.titleSmall)
                        }
                        result.files.forEach { file ->
                            val isExpanded = expandedFiles[file.filename] ?: (result.files.size <= 3)
                            diffFileLazyItems(
                                file = file,
                                expanded = isExpanded,
                                onToggleExpand = { expandedFiles[file.filename] = !isExpanded },
                                onOpenFullScreen = { onOpenFullScreenDiff(file, result.files) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RefSelectorDropdown(
    label: String,
    selectedRef: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("$label: $selectedRef", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Lucide.ChevronDown, contentDescription = null, modifier = Modifier.size(14.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { refName ->
                DropdownMenuItem(
                    text = { Text(refName) },
                    onClick = {
                        expanded = false
                        onSelect(refName)
                    }
                )
            }
        }
    }
}

internal enum class JobLogAction { DOWNLOAD, OPEN_IN_BROWSER, UNAVAILABLE }

/** GitHub returns 404 for the logs of a job that is still running, so only finished jobs download them. */
internal fun jobLogAction(status: String, htmlUrl: String): JobLogAction = when {
    status == "completed" -> JobLogAction.DOWNLOAD
    htmlUrl.isNotBlank() -> JobLogAction.OPEN_IN_BROWSER
    else -> JobLogAction.UNAVAILABLE
}

@Composable
internal fun actionStatusVisual(status: String, conclusion: String?): Pair<ImageVector, Color> {
    return when {
        status != "completed" -> Lucide.Clock to Color(0xFFD97706)
        conclusion == "success" -> Lucide.CircleCheck to Color(0xFF16A34A)
        conclusion == "failure" || conclusion == "timed_out" -> Lucide.X to MaterialTheme.colorScheme.error
        else -> Lucide.CircleDot to MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepoActionsTabContent(
    workflows: List<WorkflowSummary>,
    selectedWorkflowId: Long?,
    runs: List<WorkflowRunSummary>?,
    isLoading: Boolean,
    errorMsg: String?,
    canDispatch: Boolean,
    onSelectWorkflow: (Long?) -> Unit,
    onRefresh: () -> Unit,
    onOpenDispatch: () -> Unit,
    onOpenRun: (WorkflowRunSummary) -> Unit
) {
    val strings = appStrings()
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item(key = "wf_all") {
                    FilterChip(
                        selected = selectedWorkflowId == null,
                        onClick = { onSelectWorkflow(null) },
                        label = { Text(strings.actionsAllRuns) }
                    )
                }
                items(items = workflows, key = { it.id }) { wf ->
                    FilterChip(
                        selected = selectedWorkflowId == wf.id,
                        onClick = { onSelectWorkflow(wf.id) },
                        label = { Text(wf.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }
            if (workflows.isNotEmpty() && canDispatch) {
                FilledTonalButton(
                    onClick = onOpenDispatch,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Lucide.Play, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.actionsRunButton, maxLines = 1, softWrap = false)
                }
            }
            IconButton(onClick = onRefresh) {
                Icon(Lucide.RefreshCw, contentDescription = strings.actionsRefreshRuns, modifier = Modifier.size(18.dp))
            }
        }

        if (isLoading && !runs.isNullOrEmpty()) {
            MaxxingWavyProgress(modifier = Modifier.padding(horizontal = 16.dp))
        }

        PullToRefreshBox(
            isRefreshing = isLoading && !runs.isNullOrEmpty(),
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                isLoading && runs == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        MaxxingLoadingState(message = strings.actionsLoadingRuns)
                    }
                }
                errorMsg != null && runs.isNullOrEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(errorMsg, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(10.dp))
                        FilledTonalButton(onClick = onRefresh) { Text(strings.retry) }
                    }
                }
                runs.isNullOrEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Lucide.Play, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(10.dp))
                        Text(strings.actionsNoRuns, style = MaterialTheme.typography.titleMedium)
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(items = runs, key = { it.id }, contentType = { "workflow_run" }) { run ->
                            WorkflowRunSummaryCard(run = run, onClick = { onOpenRun(run) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun WorkflowRunSummaryCard(
    run: WorkflowRunSummary,
    onClick: () -> Unit
) {
    val strings = appStrings()
    val (statusIcon, statusTint) = actionStatusVisual(run.status, run.conclusion)
    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                statusIcon,
                contentDescription = strings.formatCheckStatusText(run.conclusion ?: run.status),
                tint = statusTint,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = run.displayTitle.ifBlank { run.name },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${run.name} #${run.runNumber}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (run.headBranch.isNotBlank()) {
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                            Text(
                                text = run.headBranch,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (run.event.isNotBlank()) {
                        Text(
                            text = run.event,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun WorkflowRunDetailView(
    run: WorkflowRunSummary,
    jobs: List<WorkflowJobSummary>?,
    isLoading: Boolean,
    actionBusy: Boolean,
    canManageRuns: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRerun: (failedOnly: Boolean) -> Unit,
    onCancel: () -> Unit,
    onOpenLogs: (WorkflowJobSummary) -> Unit
) {
    val strings = appStrings()
    val (statusIcon, statusTint) = actionStatusVisual(run.status, run.conclusion)
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(onClick = onBack) {
                Icon(Lucide.ArrowLeft, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(strings.actionsRunsBack)
            }
            Text("#${run.runNumber}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (run.htmlUrl.isNotBlank()) {
                IconButton(onClick = { openUrlInBrowser(run.htmlUrl) }) {
                    Icon(Lucide.ExternalLink, contentDescription = strings.openInBrowser, modifier = Modifier.size(16.dp))
                }
            }
            IconButton(onClick = onRefresh) {
                Icon(Lucide.RefreshCw, contentDescription = strings.actionsRefreshJobs, modifier = Modifier.size(18.dp))
            }
        }

        if (isLoading && jobs == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                MaxxingLoadingState(message = strings.actionsLoadingJobs)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "run_summary_header") {
                    ElevatedCard(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(statusIcon, contentDescription = null, tint = statusTint, modifier = Modifier.size(20.dp))
                                Text(run.displayTitle.ifBlank { run.name }, style = MaterialTheme.typography.titleMedium)
                            }
                            if (canManageRuns) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.horizontalScroll(rememberScrollState())
                                ) {
                                    if (run.status != "completed") {
                                        FilledTonalButton(enabled = !actionBusy, onClick = onCancel) {
                                            Icon(Lucide.X, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(strings.actionsCancelRun)
                                        }
                                    } else {
                                        FilledTonalButton(enabled = !actionBusy, onClick = { onRerun(false) }) {
                                            Icon(Lucide.RefreshCw, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(strings.actionsRerunAll)
                                        }
                                        if (run.conclusion == "failure") {
                                            OutlinedButton(enabled = !actionBusy, onClick = { onRerun(true) }) {
                                                Text(strings.actionsRerunFailed)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                items(items = jobs.orEmpty(), key = { it.id }, contentType = { "workflow_job" }) { job ->
                    val (jIcon, jTint) = actionStatusVisual(job.status, job.conclusion)
                    val logAction = jobLogAction(job.status, job.htmlUrl)
                    ElevatedCard(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(jIcon, contentDescription = null, tint = jTint, modifier = Modifier.size(18.dp))
                                Text(job.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                if (logAction == JobLogAction.OPEN_IN_BROWSER) {
                                    FilledTonalButton(
                                        onClick = { openUrlInBrowser(job.htmlUrl) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Lucide.ExternalLink, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(strings.actionsViewInBrowser)
                                    }
                                } else {
                                    FilledTonalButton(
                                        onClick = { onOpenLogs(job) },
                                        enabled = logAction == JobLogAction.DOWNLOAD,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Lucide.FileCode, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(strings.actionsLogsButton)
                                    }
                                }
                            }
                            if (logAction != JobLogAction.DOWNLOAD) {
                                Text(
                                    strings.actionsLogsAfterJobFinishes,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            job.steps.forEach { step ->
                                val (sIcon, sTint) = actionStatusVisual(step.status, step.conclusion)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Icon(sIcon, contentDescription = null, tint = sTint, modifier = Modifier.size(14.dp))
                                    Text(
                                        text = "${step.number}. ${step.name}",
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
            }
        }
    }
}

@Composable
internal fun WorkflowLogViewer(
    job: WorkflowJobSummary,
    logs: String?,
    isLoading: Boolean,
    onBack: () -> Unit
) {
    val strings = appStrings()
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(onClick = onBack) {
                Icon(Lucide.ArrowLeft, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(strings.actionsJobsBack)
            }
            Text(job.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                MaxxingLoadingState(message = strings.actionsLoadingLogs)
            }
            logs.isNullOrBlank() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(strings.actionsNoLogs, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> CodeViewer(
                code = logs,
                filename = "${job.name}.log",
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DispatchWorkflowDialog(
    workflows: List<WorkflowSummary>,
    branches: List<String>,
    defaultBranch: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onDispatch: (workflowId: Long, ref: String, inputs: Map<String, String>) -> Unit
) {
    val strings = appStrings()
    var selectedWf by remember(workflows) { mutableStateOf(workflows.firstOrNull()) }
    var ref by remember(defaultBranch) { mutableStateOf(defaultBranch) }
    var inputsRaw by remember { mutableStateOf("") }
    var showWfMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.actionsRunWorkflowTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box {
                    OutlinedButton(
                        onClick = { showWfMenu = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(selectedWf?.name ?: strings.actionsSelectWorkflow, modifier = Modifier.weight(1f))
                        Icon(Lucide.ChevronDown, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(expanded = showWfMenu, onDismissRequest = { showWfMenu = false }) {
                        workflows.forEach { wf ->
                            DropdownMenuItem(
                                text = { Text(wf.name) },
                                onClick = {
                                    selectedWf = wf
                                    showWfMenu = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = ref,
                    onValueChange = { ref = it },
                    label = { Text(strings.actionsBranchOrTagLabel(branches.firstOrNull() ?: "main")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = inputsRaw,
                    onValueChange = { inputsRaw = it },
                    label = { Text(strings.actionsInputsLabel) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && selectedWf != null && ref.isNotBlank(),
                onClick = {
                    val inputsMap = inputsRaw.lineSequence()
                        .map { it.trim() }
                        .filter { '=' in it }
                        .associate {
                            val idx = it.indexOf('=')
                            it.substring(0, idx).trim() to it.substring(idx + 1).trim()
                        }
                    selectedWf?.let { wf -> onDispatch(wf.id, ref.trim(), inputsMap) }
                }
            ) {
                Text(if (busy) strings.actionsStarting else strings.actionsRunWorkflowTitle)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}

@Composable
private fun RepoFileEditorDialog(
    isCreating: Boolean,
    initialPath: String,
    initialContent: String,
    initialBranch: String,
    isSubmitting: Boolean,
    errorMsg: String?,
    onDismiss: () -> Unit,
    onCommit: (path: String, content: String, message: String, branch: String) -> Unit
) {
    val strings = appStrings()
    var path by remember(initialPath) { mutableStateOf(initialPath) }
    var content by remember(initialContent) { mutableStateOf(initialContent) }
    var commitMessage by remember(initialPath, isCreating) {
        val fileName = initialPath.substringAfterLast('/').ifBlank { "file" }
        mutableStateOf(
            if (isCreating) strings.fileDefaultCreateCommitMsg(fileName)
            else strings.fileDefaultUpdateCommitMsg(fileName)
        )
    }
    var branch by remember(initialBranch) { mutableStateOf(initialBranch) }

    MaxxingFullScreenEditorScaffold(
        title = if (isCreating) strings.fileCreateTitle else strings.fileEditTitle,
        subtitle = path.ifBlank { initialBranch },
        saveLabel = if (isSubmitting) strings.fileCommitting else strings.fileCommitButton,
        canSave = path.trim().trimStart('/').isNotEmpty(),
        isSubmitting = isSubmitting,
        errorMsg = errorMsg,
        bodyValue = content,
        onBodyChange = { content = it },
        bodyLabel = strings.fileContentsLabel,
        useMonospace = true,
        supportsMarkdownPreview = path.trim().endsWith(".md", ignoreCase = true),
        metadataSummary = if (isCreating) {
            null
        } else {
            strings.fileMetadataSummary(
                path.ifBlank { strings.fileUntitledName },
                branch.ifBlank { initialBranch },
                commitMessage.ifBlank { strings.fileDefaultUpdateCommitMsg("file") }
            )
        },
        initiallyMetadataExpanded = isCreating,
        onDismiss = onDismiss,
        onSave = { onCommit(path.trim().trimStart('/'), content, commitMessage.trim(), branch.trim()) },
        headerContent = {
            OutlinedTextField(
                value = path,
                onValueChange = { path = it },
                enabled = isCreating && !isSubmitting,
                label = { Text(strings.filePathLabel) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = commitMessage,
                onValueChange = { commitMessage = it },
                enabled = !isSubmitting,
                label = { Text(strings.fileCommitMessageLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = branch,
                onValueChange = { branch = it },
                enabled = !isSubmitting,
                label = { Text(strings.fileBranchLabel) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}

@Composable
private fun DeleteRepoFileDialog(
    filePath: String,
    initialBranch: String,
    isSubmitting: Boolean,
    errorMsg: String?,
    onDismiss: () -> Unit,
    onDelete: (message: String, branch: String) -> Unit
) {
    val strings = appStrings()
    var commitMessage by remember(filePath) {
        mutableStateOf(strings.fileDefaultDeleteCommitMsg(filePath.substringAfterLast('/')))
    }
    var branch by remember(initialBranch) { mutableStateOf(initialBranch) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.fileDeleteTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = strings.fileDeleteConfirm(filePath, branch),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = commitMessage,
                    onValueChange = { commitMessage = it },
                    enabled = !isSubmitting,
                    label = { Text(strings.fileCommitMessageLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = branch,
                    onValueChange = { branch = it },
                    enabled = !isSubmitting,
                    label = { Text(strings.fileBranchLabel) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth()
                )
                if (!errorMsg.isNullOrBlank()) {
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                onClick = { onDelete(commitMessage.trim(), branch.trim()) }
            ) {
                Text(if (isSubmitting) strings.deletingAction else strings.deleteAction)
            }
        },
        dismissButton = {
            TextButton(enabled = !isSubmitting, onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}
