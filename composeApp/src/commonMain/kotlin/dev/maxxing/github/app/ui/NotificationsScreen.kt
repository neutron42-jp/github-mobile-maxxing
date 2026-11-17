package dev.maxxing.github.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Activity
import com.composables.icons.lucide.BellOff
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.CheckCheck
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.GitCommitHorizontal
import com.composables.icons.lucide.GitPullRequest
import com.composables.icons.lucide.Inbox
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Tag
import com.composables.icons.lucide.UserCheck
import com.composables.icons.lucide.X
import dev.maxxing.github.app.AppContainer
import dev.maxxing.github.app.IssuePullTarget
import dev.maxxing.github.model.NotificationReason
import dev.maxxing.github.model.NotificationSubjectType
import dev.maxxing.github.model.NotificationThread
import dev.maxxing.github.ui.components.ExpressiveStatusPill
import dev.maxxing.github.ui.components.MaxxingDropdownSelector
import dev.maxxing.github.ui.components.MaxxingFloatingToolbar
import dev.maxxing.github.ui.components.MaxxingLoadingState
import dev.maxxing.github.ui.components.MaxxingPredictiveBackContainer
import dev.maxxing.github.ui.components.MaxxingTopHeaderDefaults
import dev.maxxing.github.ui.components.MaxxingWavyProgress
import dev.maxxing.github.ui.components.UserAvatar
import dev.maxxing.github.ui.components.expressiveAnimateContentSize
import dev.maxxing.github.ui.i18n.AppStrings
import dev.maxxing.github.ui.i18n.appStrings
import kotlinx.coroutines.launch

private enum class InboxFilter {
    UNREAD,
    PARTICIPATING,
    ALL;

    fun localizedLabel(strings: AppStrings): String = when (this) {
        UNREAD -> strings.inboxFilterUnread
        PARTICIPATING -> strings.inboxFilterParticipating
        ALL -> strings.inboxFilterAll
    }
}

internal enum class ReasonFilter {
    ALL,
    REVIEWS,
    MENTIONS,
    ASSIGNED,
    CI;

    fun localizedLabel(strings: AppStrings): String = when (this) {
        ALL -> strings.reasonAll
        REVIEWS -> strings.reasonReviews
        MENTIONS -> strings.reasonMentions
        ASSIGNED -> strings.reasonAssigned
        CI -> strings.reasonCi
    }
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3AdaptiveApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun NotificationsScreen(
    container: AppContainer,
    deepLinkTarget: IssuePullTarget? = null,
    onConsumeDeepLink: () -> Unit = {},
    onMessage: (String) -> Unit
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val scope = rememberCoroutineScope()
    val strings = appStrings()

    var selectedIssueTargets by remember { mutableStateOf<Map<String, IssuePullTarget>>(emptyMap()) }
    var selectedThreads by remember { mutableStateOf<Map<String, NotificationThread>>(emptyMap()) }

    fun openIssueDetail(target: IssuePullTarget) {
        val key = "issue:${target.owner}/${target.repo}#${target.number}"
        selectedIssueTargets = selectedIssueTargets + (key to target)
        scope.launch {
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, key)
        }
    }

    fun openThreadSubjectDetail(thread: NotificationThread) {
        val key = "thread:${thread.id}"
        selectedThreads = selectedThreads + (key to thread)
        scope.launch {
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, key)
        }
    }

    var filter by remember { mutableStateOf(InboxFilter.UNREAD) }
    var reasonFilter by remember { mutableStateOf(ReasonFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var isFilterInputActive by remember { mutableStateOf(false) }
    val filterFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val liveUnread by container.unreadThreads.collectAsState()
    var threads by remember {
        mutableStateOf(
            liveUnread
                ?: container.repository.peekNotifications(all = false, participating = false)
                ?: emptyList()
        )
    }
    var isLoading by remember { mutableStateOf(threads.isEmpty() && liveUnread == null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isFilterInputActive) {
        if (isFilterInputActive) {
            filterFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(deepLinkTarget) {
        if (deepLinkTarget != null) {
            openIssueDetail(deepLinkTarget)
            onConsumeDeepLink()
        }
    }

    LaunchedEffect(liveUnread, filter) {
        if (filter == InboxFilter.UNREAD && liveUnread != null) {
            threads = liveUnread!!
            isLoading = false
        }
    }

    fun prefetchVisible(loaded: List<NotificationThread>) {
        val targets = loaded.mapNotNull { thread ->
            thread.subjectNumber?.let { num ->
                IssuePullTarget(thread.repoOwner, thread.repoName, num, thread.subjectTitle)
            }
        }
        container.prefetchIssuePullTargets(targets)
    }

    var requestSeq by remember { mutableStateOf(0) }

    fun loadNotifications(forceRefresh: Boolean = true, showRefreshFeedback: Boolean = false) {
        val reqFilter = filter
        val seq = ++requestSeq
        val cached = container.repository.peekNotifications(
            all = reqFilter == InboxFilter.ALL,
            participating = reqFilter == InboxFilter.PARTICIPATING
        )
        if (cached != null) {
            threads = cached
            isLoading = false
        } else {
            threads = emptyList()
            isLoading = true
        }
        isRefreshing = forceRefresh && cached != null
        errorMsg = null

        scope.launch {
            if (reqFilter == InboxFilter.UNREAD && forceRefresh) {
                container.syncNotifications()
                    .onSuccess { check ->
                        if (seq != requestSeq) return@onSuccess
                        threads = check.allUnread
                        isLoading = false
                        isRefreshing = false
                        if (showRefreshFeedback) {
                            onMessage(
                                if (check.allUnread.isEmpty()) strings.inboxUpToDate
                                else strings.syncedUnreadCount(check.allUnread.size)
                            )
                        }
                    }
                    .onFailure {
                        if (seq != requestSeq) return@onFailure
                        val msg = strings.errorMessage(it, strings.failedLoadNotifications)
                        errorMsg = msg
                        isLoading = false
                        isRefreshing = false
                        if (threads.isNotEmpty() || showRefreshFeedback) {
                            onMessage(msg)
                        }
                    }
            } else {
                container.repository.getNotifications(
                    all = reqFilter == InboxFilter.ALL,
                    participating = reqFilter == InboxFilter.PARTICIPATING,
                    forceRefresh = forceRefresh
                ).onSuccess { loaded ->
                    if (seq != requestSeq) return@onSuccess
                    threads = loaded
                    if (reqFilter == InboxFilter.UNREAD) {
                        container.updateUnreadThreads(loaded)
                    } else {
                        container.updateUnreadCount(loaded.count { it.unread })
                    }
                    prefetchVisible(loaded)
                    isLoading = false
                    isRefreshing = false
                    if (showRefreshFeedback) {
                        onMessage(strings.syncedCount(loaded.size))
                    }
                }.onFailure {
                    if (seq != requestSeq) return@onFailure
                    val msg = strings.errorMessage(it, strings.failedLoadNotifications)
                    errorMsg = msg
                    isLoading = false
                    isRefreshing = false
                    if (threads.isNotEmpty() || showRefreshFeedback) {
                        onMessage(msg)
                    }
                }
            }
        }
    }

    LaunchedEffect(filter) {
        val cached = container.repository.peekNotifications(
            all = filter == InboxFilter.ALL,
            participating = filter == InboxFilter.PARTICIPATING
        )
        if (cached != null) {
            threads = cached
            isLoading = false
        }
        loadNotifications(forceRefresh = true)
    }

    val visibleThreads = remember(threads, reasonFilter, searchQuery) {
        threads.filter { t ->
            val reasonOk = when (reasonFilter) {
                ReasonFilter.ALL -> true
                ReasonFilter.REVIEWS -> t.reason == NotificationReason.REVIEW_REQUESTED
                ReasonFilter.MENTIONS -> t.reason == NotificationReason.MENTION || t.reason == NotificationReason.TEAM_MENTION
                ReasonFilter.ASSIGNED -> t.reason == NotificationReason.ASSIGN
                ReasonFilter.CI -> t.reason == NotificationReason.CI_ACTIVITY
            }
            val q = searchQuery.trim()
            val queryOk = q.isEmpty() ||
                t.subjectTitle.contains(q, ignoreCase = true) ||
                t.repoFullName.contains(q, ignoreCase = true)
            reasonOk && queryOk
        }
    }

    val headerTracker = remember { RepoHeaderCollapseTracker() }
    val headerNestedScroll = remember(headerTracker) {
        headerTracker.toNestedScrollConnection { !isFilterInputActive && searchQuery.isEmpty() }
    }
    val showExpandedHeader = !headerTracker.isCollapsed || isFilterInputActive || searchQuery.isNotEmpty()
    val headerVerticalPadding by animateDpAsState(
        if (showExpandedHeader) MaxxingTopHeaderDefaults.ExpandedVerticalPadding
        else MaxxingTopHeaderDefaults.CollapsedVerticalPadding
    )

    LaunchedEffect(filter, reasonFilter) {
        headerTracker.expand()
    }

    MaxxingPredictiveBackContainer(
        enabled = isFilterInputActive || navigator.canNavigateBack(),
        onBack = {
            when {
                isFilterInputActive -> {
                    focusManager.clearFocus()
                    isFilterInputActive = false
                }
                navigator.canNavigateBack() -> scope.launch { navigator.navigateBack() }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            AnimatedPane {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                        Column(modifier = Modifier.padding(horizontal = MaxxingTopHeaderDefaults.HorizontalPadding, vertical = headerVerticalPadding)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().heightIn(min = MaxxingTopHeaderDefaults.TitleRowMinHeight),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = strings.inboxTitle,
                                    style = if (showExpandedHeader) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                MaxxingDropdownSelector(
                                    options = InboxFilter.entries,
                                    selected = filter,
                                    onSelect = { filter = it },
                                    labelProvider = { it.localizedLabel(strings) }
                                )
                                IconButton(onClick = { loadNotifications(forceRefresh = true, showRefreshFeedback = true) }) {
                                    Icon(Lucide.RefreshCw, contentDescription = strings.refresh, modifier = Modifier.size(18.dp))
                                }
                            }

                            MaxxingCollapsibleSection(visible = showExpandedHeader) {
                                Column {
                                    Spacer(Modifier.height(MaxxingTopHeaderDefaults.TabBarTopSpacing))
                                    PrimaryScrollableTabRow(
                                        selectedTabIndex = ReasonFilter.entries.indexOf(reasonFilter).coerceAtLeast(0),
                                        containerColor = Color.Transparent,
                                        edgePadding = 0.dp
                                    ) {
                                        ReasonFilter.entries.forEach { rf ->
                                            val icon = when (rf) {
                                                ReasonFilter.ALL -> Lucide.Inbox
                                                ReasonFilter.REVIEWS -> Lucide.GitPullRequest
                                                ReasonFilter.MENTIONS -> Lucide.MessageSquare
                                                ReasonFilter.ASSIGNED -> Lucide.UserCheck
                                                ReasonFilter.CI -> Lucide.Activity
                                            }
                                            Tab(
                                                selected = reasonFilter == rf,
                                                onClick = { reasonFilter = rf },
                                                text = {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
                                                        Text(rf.localizedLabel(strings), maxLines = 1, softWrap = false)
                                                    }
                                                }
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(6.dp))
                                    if (!isFilterInputActive && searchQuery.isEmpty()) {
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(52.dp)
                                                .clickable { isFilterInputActive = true }
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Icon(
                                                    Lucide.Search,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = strings.filterNotificationsPlaceholder,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Surface(
                                                    shape = MaterialTheme.shapes.small,
                                                    color = MaterialTheme.colorScheme.secondaryContainer
                                                ) {
                                                    Text(
                                                        text = "${visibleThreads.size} / ${threads.size}",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        OutlinedTextField(
                                            value = searchQuery,
                                            onValueChange = { searchQuery = it },
                                            placeholder = { Text(strings.filterNotificationsPlaceholder) },
                                            leadingIcon = { Icon(Lucide.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                            suffix = {
                                                Text(
                                                    text = "${visibleThreads.size}/${threads.size}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            },
                                            trailingIcon = {
                                                IconButton(
                                                    onClick = {
                                                        searchQuery = ""
                                                        focusManager.clearFocus()
                                                        isFilterInputActive = false
                                                    }
                                                ) {
                                                    Icon(Lucide.X, contentDescription = strings.clear, modifier = Modifier.size(16.dp))
                                                }
                                            },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                            keyboardActions = KeyboardActions(
                                                onSearch = {
                                                    focusManager.clearFocus()
                                                    if (searchQuery.isEmpty()) isFilterInputActive = false
                                                }
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .focusRequester(filterFocusRequester)
                                        )
                                    }
                                }
                            }

                            if ((isLoading || isRefreshing) && threads.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                MaxxingWavyProgress()
                            }
                        }

                        PullToRefreshBox(
                            isRefreshing = isRefreshing && threads.isNotEmpty(),
                            onRefresh = { loadNotifications(forceRefresh = true, showRefreshFeedback = true) },
                            modifier = Modifier.fillMaxSize().nestedScroll(headerNestedScroll)
                        ) {
                            when {
                                isLoading && threads.isEmpty() -> {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        MaxxingLoadingState(message = strings.syncingNotifications)
                                    }
                                }

                                errorMsg != null && threads.isEmpty() -> {
                                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = errorMsg!!,
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }

                                visibleThreads.isEmpty() -> {
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            Lucide.Inbox,
                                            contentDescription = null,
                                            modifier = Modifier.size(56.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        Text(strings.inboxZeroTitle, style = MaterialTheme.typography.headlineSmall)
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = if (filter == InboxFilter.UNREAD) {
                                                strings.inboxZeroUnreadHint
                                            } else {
                                                strings.inboxZeroFilteredHint(filter.localizedLabel(strings))
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                else -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 92.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(
                                        items = visibleThreads,
                                        key = { it.id },
                                        contentType = { "notification_thread" }
                                    ) { thread ->
                                        val dismissState = rememberSwipeToDismissBoxState(
                                            confirmValueChange = { value ->
                                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                                    threads = threads.filterNot { it.id == thread.id }
                                                    container.updateUnreadCount(threads.count { it.unread })
                                                    scope.launch {
                                                        container.repository.markNotificationDone(thread.id)
                                                            .onSuccess { onMessage(strings.markedDone) }
                                                            .onFailure { loadNotifications() }
                                                    }
                                                    true
                                                } else false
                                            }
                                        )

                                        SwipeToDismissBox(
                                            state = dismissState,
                                            enableDismissFromStartToEnd = false,
                                            backgroundContent = {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(horizontal = 20.dp),
                                                    contentAlignment = Alignment.CenterEnd
                                                ) {
                                                    Icon(
                                                        Lucide.Check,
                                                        contentDescription = strings.doneAction,
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        ) {
                                            val currentKey = navigator.currentDestination?.contentKey
                                            val isSelected = when {
                                                currentKey == "thread:${thread.id}" -> true
                                                currentKey != null && currentKey.startsWith("issue:") -> {
                                                    selectedIssueTargets[currentKey]?.let {
                                                        it.owner == thread.repoOwner &&
                                                            it.repo == thread.repoName &&
                                                            it.number == thread.subjectNumber
                                                    } == true
                                                }
                                                else -> false
                                            }
                                            NotificationThreadCard(
                                                thread = thread,
                                                selected = isSelected,
                                                onClick = {
                                                    if (thread.unread) {
                                                        threads = threads.map {
                                                            if (it.id == thread.id) it.copy(unread = false) else it
                                                        }
                                                        container.updateUnreadCount(threads.count { it.unread })
                                                        scope.launch {
                                                            container.repository.markNotificationRead(thread.id)
                                                        }
                                                    }
                                                    val num = thread.subjectNumber
                                                    if (num != null && (thread.subjectType == NotificationSubjectType.ISSUE || thread.subjectType == NotificationSubjectType.PULL_REQUEST)) {
                                                        openIssueDetail(
                                                            IssuePullTarget(
                                                                owner = thread.repoOwner,
                                                                repo = thread.repoName,
                                                                number = num,
                                                                hintTitle = thread.subjectTitle
                                                            )
                                                        )
                                                    } else {
                                                        openThreadSubjectDetail(thread)
                                                    }
                                                },
                                                onMarkDone = {
                                                    threads = threads.filterNot { it.id == thread.id }
                                                    container.updateUnreadCount(threads.count { it.unread })
                                                    scope.launch {
                                                        container.repository.markNotificationDone(thread.id)
                                                            .onFailure { loadNotifications() }
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

                    MaxxingFloatingToolbar(
                        expanded = true,
                        vibrant = true,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                    ) {
                        FilledTonalIconButton(onClick = { loadNotifications(forceRefresh = true, showRefreshFeedback = true) }) {
                            Icon(Lucide.RefreshCw, contentDescription = strings.refresh)
                        }
                        Spacer(Modifier.width(6.dp))
                        FilledTonalIconButton(
                            onClick = {
                                scope.launch {
                                    container.repository.markAllNotificationsRead()
                                        .onSuccess {
                                            onMessage(strings.allNotificationsMarkedRead)
                                            loadNotifications(forceRefresh = true)
                                        }
                                        .onFailure { onMessage(strings.errorMessage(it, strings.markAllReadFailed)) }
                                }
                            }
                        ) {
                            Icon(Lucide.CheckCheck, contentDescription = strings.markAllReadAction)
                        }
                    }
                }
            }
        },
        detailPane = {
            AnimatedPane {
                val contentKey = navigator.currentDestination?.contentKey
                val issueTarget = contentKey?.let { selectedIssueTargets[it] }
                val threadTarget = contentKey?.removePrefix("thread:")?.takeIf { contentKey.startsWith("thread:") }?.let { tid ->
                    threads.firstOrNull { it.id == tid } ?: selectedThreads[contentKey]
                }
                when {
                    issueTarget != null -> {
                        IssuePullDetailPane(
                            target = issueTarget,
                            container = container,
                            showBackButton = navigator.canNavigateBack(),
                            onBack = { scope.launch { navigator.navigateBack() } },
                            onMessage = onMessage
                        )
                    }
                    threadTarget != null -> {
                        NotificationSubjectDetailPane(
                            thread = threadTarget,
                            container = container,
                            showBackButton = navigator.canNavigateBack(),
                            onBack = { scope.launch { navigator.navigateBack() } },
                            onMessage = onMessage
                        )
                    }
                    else -> {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Lucide.BellOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = strings.selectNotificationToTriage,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    )
    }
}

@Composable
private fun NotificationThreadCard(
    thread: NotificationThread,
    selected: Boolean,
    onClick: () -> Unit,
    onMarkDone: () -> Unit
) {
    val icon = when (thread.subjectType) {
        NotificationSubjectType.PULL_REQUEST -> Lucide.GitPullRequest
        NotificationSubjectType.ISSUE -> Lucide.CircleDot
        NotificationSubjectType.RELEASE -> Lucide.Tag
        NotificationSubjectType.CHECK_SUITE -> Lucide.Activity
        NotificationSubjectType.DISCUSSION -> Lucide.MessageSquare
        else -> Lucide.GitCommitHorizontal
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = when {
                selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                thread.unread -> MaterialTheme.colorScheme.surfaceContainerHigh
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            UserAvatar(
                avatarUrl = thread.repoOwnerAvatarUrl,
                login = thread.repoOwner,
                sizeDp = 34
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (thread.unread) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = androidx.compose.foundation.shape.CircleShape,
                            modifier = Modifier.size(8.dp)
                        ) {}
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (thread.unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = thread.repoFullName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    ExpressiveStatusPill(
                        label = appStrings().notificationReasonLabel(thread.reason),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = thread.subjectTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (thread.unread) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onMarkDone, modifier = Modifier.size(32.dp)) {
                Icon(
                    Lucide.Check,
                    contentDescription = appStrings().markDoneAction,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
