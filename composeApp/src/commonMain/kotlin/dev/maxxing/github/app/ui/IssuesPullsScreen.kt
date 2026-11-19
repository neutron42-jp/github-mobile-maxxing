package dev.maxxing.github.app.ui

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.GitMerge
import com.composables.icons.lucide.GitPullRequest
import com.composables.icons.lucide.Layers
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.RefreshCw
import dev.maxxing.github.app.AppContainer
import dev.maxxing.github.app.IssuePullTarget
import dev.maxxing.github.app.issuePullTargetFromPaneKey
import dev.maxxing.github.app.toPaneKey
import dev.maxxing.github.model.IssueFilterPreset
import dev.maxxing.github.model.IssueOrPullKind
import dev.maxxing.github.model.IssueOrPullState
import dev.maxxing.github.model.IssueOrPullSummary
import dev.maxxing.github.model.IssueStateFilter
import dev.maxxing.github.ui.components.ExpressiveStatusPill
import dev.maxxing.github.ui.components.GitHubLabelChip
import dev.maxxing.github.ui.components.MaxxingDropdownSelector
import dev.maxxing.github.ui.components.MaxxingIconLabelTab
import dev.maxxing.github.ui.components.MaxxingLoadingState
import dev.maxxing.github.ui.components.MaxxingPredictiveBackContainer
import dev.maxxing.github.ui.components.MaxxingTopHeaderDefaults
import dev.maxxing.github.ui.components.MaxxingWavyProgress
import dev.maxxing.github.ui.components.UserAvatar
import dev.maxxing.github.ui.components.expressiveAnimateContentSize
import dev.maxxing.github.ui.i18n.appStrings
import kotlinx.coroutines.launch

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3AdaptiveApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun IssuesPullsScreen(
    container: AppContainer,
    isTabActive: Boolean = true,
    onMessage: (String) -> Unit
) {
    // String content keys keep the navigator saveable into the Android Bundle; IssuePullTarget is not.
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    // Targets opened in this composition, so the detail pane keeps hintTitle. After the state is
    // restored (e.g. process recreation) the key is decoded instead, without a hint title.
    val openedTargets = remember { mutableMapOf<String, IssuePullTarget>() }
    val scope = rememberCoroutineScope()
    val strings = appStrings()
    val profile by container.viewerProfile.collectAsState()
    val session by container.sessionManager.session.collectAsState()
    val issueStateRevision by container.repository.issueStateRevision.collectAsState()
    val login = profile?.login ?: session?.userLogin ?: ""

    var preset by remember { mutableStateOf(IssueFilterPreset.CREATED) }
    var stateFilter by remember { mutableStateOf(IssueStateFilter.OPEN) }
    var items by remember {
        mutableStateOf(
            if (login.isNotBlank()) {
                container.repository.peekMyIssuesAndPulls(preset, login, stateFilter).orEmpty()
            } else emptyList()
        )
    }
    var isLoading by remember { mutableStateOf(items.isEmpty() && login.isNotBlank()) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var requestSeq by remember { mutableStateOf(0) }

    fun loadItems(forceRefresh: Boolean = false) {
        if (login.isBlank()) return
        val reqPreset = preset
        val reqStateFilter = stateFilter
        val seq = ++requestSeq
        val cached = container.repository.peekMyIssuesAndPulls(reqPreset, login, reqStateFilter)
        if (cached != null) {
            items = cached
            isLoading = false
        } else {
            items = emptyList()
            isLoading = true
        }
        isRefreshing = forceRefresh
        errorMsg = null

        scope.launch {
            container.repository.getMyIssuesAndPulls(reqPreset, login, reqStateFilter, forceRefresh = forceRefresh)
                .onSuccess { loaded ->
                    if (seq != requestSeq) return@onSuccess
                    items = loaded
                    isLoading = false
                    isRefreshing = false
                    container.prefetchIssuePullTargets(
                        loaded.take(3).map {
                            IssuePullTarget(it.repoOwner, it.repoName, it.number, it.title, kindHint = it.kind)
                        }
                    )
                }
                .onFailure {
                    if (seq != requestSeq) return@onFailure
                    val msg = strings.errorMessage(it, strings.failedLoadIssuesAndPrs)
                    errorMsg = msg
                    isLoading = false
                    isRefreshing = false
                    if (items.isNotEmpty() || forceRefresh) {
                        onMessage(msg)
                    }
                }
        }

        // Prefetch sibling filter presets for instant tab switching without affecting active UI state
        IssueFilterPreset.entries.forEach { otherPreset ->
            if (otherPreset != reqPreset &&
                container.repository.peekMyIssuesAndPulls(otherPreset, login, reqStateFilter) == null
            ) {
                scope.launch {
                    container.repository.getMyIssuesAndPulls(otherPreset, login, reqStateFilter, forceRefresh = false)
                }
            }
        }
    }

    val headerTracker = remember { RepoHeaderCollapseTracker() }
    val headerNestedScroll = remember(headerTracker) { headerTracker.toNestedScrollConnection() }
    val headerVerticalPadding by animateDpAsState(
        if (headerTracker.isCollapsed) MaxxingTopHeaderDefaults.CollapsedVerticalPadding
        else MaxxingTopHeaderDefaults.ExpandedVerticalPadding
    )

    LaunchedEffect(preset, stateFilter, login) {
        headerTracker.expand()
        loadItems(forceRefresh = false)
    }

    LaunchedEffect(issueStateRevision) {
        if (login.isNotBlank() && issueStateRevision > 0L) {
            container.repository.peekMyIssuesAndPulls(preset, login, stateFilter)?.let { updated ->
                items = updated
            }
        }
    }

    MaxxingPredictiveBackContainer(
        enabled = isTabActive && navigator.canNavigateBack(),
        onBack = { scope.launch { navigator.navigateBack() } },
        modifier = Modifier.fillMaxSize()
    ) {
        ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            AnimatedPane {
                Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                    Column(modifier = Modifier.padding(horizontal = MaxxingTopHeaderDefaults.HorizontalPadding, vertical = headerVerticalPadding)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = MaxxingTopHeaderDefaults.TitleRowMinHeight),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = strings.issuesAndPrsTitle,
                                style = if (headerTracker.isCollapsed) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            MaxxingDropdownSelector(
                                options = IssueFilterPreset.entries,
                                selected = preset,
                                onSelect = { preset = it },
                                labelProvider = { strings.issueFilterPresetLabel(it) }
                            )
                            IconButton(onClick = { loadItems(forceRefresh = true) }) {
                                Icon(Lucide.RefreshCw, contentDescription = strings.refresh, modifier = Modifier.size(18.dp))
                            }
                        }

                        MaxxingCollapsibleSection(visible = !headerTracker.isCollapsed) {
                            Column {
                                Spacer(Modifier.height(MaxxingTopHeaderDefaults.TabBarTopSpacing))
                                PrimaryTabRow(
                                    selectedTabIndex = IssueStateFilter.entries.indexOf(stateFilter).coerceAtLeast(0),
                                    containerColor = Color.Transparent
                                ) {
                                    IssueStateFilter.entries.forEach { sf ->
                                        val icon = when (sf) {
                                            IssueStateFilter.OPEN -> Lucide.CircleDot
                                            IssueStateFilter.CLOSED -> Lucide.CircleCheck
                                            IssueStateFilter.ALL -> Lucide.Layers
                                        }
                                        MaxxingIconLabelTab(
                                            selected = stateFilter == sf,
                                            onClick = { stateFilter = sf },
                                            icon = icon,
                                            label = strings.issueStateFilterLabel(sf)
                                        )
                                    }
                                }
                            }
                        }

                        if ((isLoading || isRefreshing) && items.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            MaxxingWavyProgress()
                        }
                    }

                    PullToRefreshBox(
                        isRefreshing = isRefreshing && items.isNotEmpty(),
                        onRefresh = { loadItems(forceRefresh = true) },
                        modifier = Modifier.fillMaxSize().nestedScroll(headerNestedScroll)
                    ) {
                        when {
                            isLoading && items.isEmpty() -> {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    MaxxingLoadingState(message = strings.fetchingIssuesAndPrs)
                                }
                            }

                            errorMsg != null && items.isEmpty() -> {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
                                    Spacer(Modifier.height(10.dp))
                                    FilledTonalButton(onClick = { loadItems(forceRefresh = true) }) {
                                        Text(strings.retry)
                                    }
                                }
                            }

                            items.isEmpty() -> {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Lucide.GitPullRequest,
                                        contentDescription = null,
                                        modifier = Modifier.size(52.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        text = strings.noIssuesOrPrsTitle(stateFilter),
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = strings.noIssuesOrPrsBody(stateFilter, preset),
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
                                    contentType = { "issue_pull_summary" }
                                ) { summary ->
                                    val summaryTarget = IssuePullTarget(
                                        owner = summary.repoOwner,
                                        repo = summary.repoName,
                                        number = summary.number,
                                        hintTitle = summary.title,
                                        kindHint = summary.kind
                                    )
                                    val summaryKey = summaryTarget.toPaneKey()
                                    val isSelected = navigator.currentDestination?.contentKey == summaryKey
                                    IssuePullSummaryCard(
                                        item = summary,
                                        selected = isSelected,
                                        onClick = {
                                            openedTargets[summaryKey] = summaryTarget
                                            scope.launch {
                                                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, summaryKey)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    }
                }
            }
        },
        detailPane = {
            AnimatedPane {
                val targetKey = navigator.currentDestination?.contentKey
                val target = remember(targetKey) {
                    targetKey?.let { openedTargets[it] ?: issuePullTargetFromPaneKey(it) }
                }
                LaunchedEffect(targetKey, target) {
                    // A key that cannot be decoded has nothing to show; return to the list.
                    if (targetKey != null && target == null && navigator.canNavigateBack()) {
                        navigator.navigateBack()
                    }
                }
                if (target != null) {
                    IssuePullDetailPane(
                        target = target,
                        container = container,
                        showBackButton = navigator.canNavigateBack(),
                        onBack = { scope.launch { navigator.navigateBack() } },
                        onMessage = onMessage
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = strings.selectIssueOrPr,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IssuePullSummaryCard(
    item: IssueOrPullSummary,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val strings = appStrings()
    val stateColor = when {
        item.isDraft -> MaterialTheme.colorScheme.onSurfaceVariant
        item.state == IssueOrPullState.OPEN -> Color(0xFF16A34A)
        item.state == IssueOrPullState.MERGED -> Color(0xFF7C3AED)
        else -> MaterialTheme.colorScheme.error
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when {
                        item.isDraft -> Lucide.Pencil
                        item.kind == IssueOrPullKind.PULL_REQUEST && item.state == IssueOrPullState.MERGED -> Lucide.GitMerge
                        item.kind == IssueOrPullKind.PULL_REQUEST -> Lucide.GitPullRequest
                        item.state == IssueOrPullState.CLOSED -> Lucide.CircleCheck
                        else -> Lucide.CircleDot
                    },
                    contentDescription = null,
                    tint = stateColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${item.repoFullName} #${item.number}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                item.checkRollup?.let { rollup ->
                    val ciColor = when (rollup.state) {
                        "SUCCESS" -> Color(0xFF16A34A)
                        "FAILURE", "ERROR" -> MaterialTheme.colorScheme.error
                        else -> Color(0xFFD97706)
                    }
                    ExpressiveStatusPill(
                        label = strings.formatCheckRollupState(rollup.state),
                        containerColor = ciColor.copy(alpha = 0.16f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (item.labels.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item.labels.forEach { GitHubLabelChip(it.name, it.colorHex) }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(item.author.avatarUrl, item.author.login, sizeDp = 20)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "@${item.author.login}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                if (item.commentCount > 0) {
                    Icon(
                        Lucide.MessageSquare,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = item.commentCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
