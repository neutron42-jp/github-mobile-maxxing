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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.FileDiff
import com.composables.icons.lucide.Flag
import com.composables.icons.lucide.GitCommitHorizontal
import com.composables.icons.lucide.GitMerge
import com.composables.icons.lucide.GitPullRequest
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.MessageSquarePlus
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Send
import com.composables.icons.lucide.Tag
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Users
import com.composables.icons.lucide.X
import dev.maxxing.github.app.AppContainer
import dev.maxxing.github.app.IssuePullTarget
import dev.maxxing.github.auth.openUrlInBrowser
import dev.maxxing.github.model.CheckRunSummary
import dev.maxxing.github.model.IssueOrPullDetail
import dev.maxxing.github.model.DiffLine
import dev.maxxing.github.model.DiffLineKind
import dev.maxxing.github.model.IssueOrPullKind
import dev.maxxing.github.model.IssueOrPullState
import dev.maxxing.github.model.LabelTag
import dev.maxxing.github.model.MergeMethod
import dev.maxxing.github.model.MilestoneSummary
import dev.maxxing.github.model.PullFileChange
import dev.maxxing.github.model.ReactionSummary
import dev.maxxing.github.model.ReviewEvent
import dev.maxxing.github.model.TimelineEvent
import dev.maxxing.github.model.UserSummary
import dev.maxxing.github.ui.components.ExpressiveStatusPill
import dev.maxxing.github.ui.components.GitHubLabelChip
import dev.maxxing.github.ui.components.GitHubMarkdown
import dev.maxxing.github.ui.components.MaxxingDropdownSelector
import dev.maxxing.github.ui.components.MaxxingFloatingToolbar
import dev.maxxing.github.ui.components.MaxxingFullScreenDiffViewer
import dev.maxxing.github.ui.components.MaxxingFullScreenEditorScaffold
import dev.maxxing.github.ui.components.MaxxingLoadingState
import dev.maxxing.github.ui.components.MaxxingSplitActionButton
import dev.maxxing.github.ui.components.MaxxingWavyProgress
import dev.maxxing.github.ui.components.RepoLabelsPickerDialog
import dev.maxxing.github.ui.components.SplitActionOption
import dev.maxxing.github.ui.components.UserAvatar
import dev.maxxing.github.ui.components.diffFileLazyItems
import kotlinx.coroutines.launch

private enum class DetailTab(val label: String) {
    CONVERSATION("Chat"),
    CHECKS("Checks"),
    FILES("Files")
}

@Composable
fun IssuePullDetailPane(
    target: IssuePullTarget,
    container: AppContainer,
    showBackButton: Boolean = false,
    onBack: () -> Unit = {},
    onMessage: (String) -> Unit = {}
) {
    // The detail content and its full-screen editors/viewers are emitted as siblings. Keep them in one
    // Box so the editors draw on top of the content whatever layout the caller uses: ListDetailPaneScaffold's
    // AnimatedPane lays its children out in a Column, which pushed the editors below the screen (#14).
    Box(modifier = Modifier.fillMaxSize()) {
        IssuePullDetailPaneContent(
            target = target,
            container = container,
            showBackButton = showBackButton,
            onBack = onBack,
            onMessage = onMessage
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun IssuePullDetailPaneContent(
    target: IssuePullTarget,
    container: AppContainer,
    showBackButton: Boolean,
    onBack: () -> Unit,
    onMessage: (String) -> Unit
) {
    var detail by remember(target) {
        mutableStateOf(container.repository.peekIssueOrPullDetail(target.owner, target.repo, target.number))
    }
    var files by remember(target) {
        mutableStateOf(container.repository.peekPullFiles(target.owner, target.repo, target.number).orEmpty())
    }
    var isLoading by remember(target) { mutableStateOf(detail == null) }
    var filesLoading by remember(target) { mutableStateOf(false) }
    var checkRuns by remember(target) { mutableStateOf<List<CheckRunSummary>?>(null) }
    var checksLoading by remember(target) { mutableStateOf(false) }
    var errorMsg by remember(target) { mutableStateOf<String?>(null) }
    var selectedTab by remember(target) { mutableStateOf(DetailTab.CONVERSATION) }

    var showCommentDialog by remember { mutableStateOf(false) }
    var commentDraft by remember { mutableStateOf("") }
    var showReviewDialog by remember { mutableStateOf(false) }
    var reviewBody by remember { mutableStateOf("") }
    var reviewEvent by remember { mutableStateOf(ReviewEvent.APPROVE) }
    var selectedMergeMethod by remember { mutableStateOf(MergeMethod.SQUASH) }
    var actionBusy by remember { mutableStateOf(false) }

    var showLabelsDialog by remember { mutableStateOf(false) }
    var availableLabels by remember(target) { mutableStateOf<List<LabelTag>>(emptyList()) }
    var selectedLabelNames by remember(target) { mutableStateOf<Set<String>>(emptySet()) }
    var newLabelName by remember { mutableStateOf("") }
    var newLabelColor by remember { mutableStateOf("6E40C9") }

    var showAssigneesDialog by remember { mutableStateOf(false) }
    var availableAssignees by remember(target) { mutableStateOf<List<UserSummary>>(emptyList()) }
    var selectedAssigneeLogins by remember(target) { mutableStateOf<Set<String>>(emptySet()) }

    var showMilestoneDialog by remember { mutableStateOf(false) }
    var availableMilestones by remember(target) { mutableStateOf<List<MilestoneSummary>>(emptyList()) }
    var selectedMilestoneNumber by remember(target) { mutableStateOf<Int?>(null) }

    val scope = rememberCoroutineScope()

    var pendingDiffCommentTarget by remember(target) { mutableStateOf<Triple<String, Int?, String>?>(null) }
    var diffCommentBody by remember { mutableStateOf("") }
    var fullScreenDiffFile by remember(target) { mutableStateOf<PullFileChange?>(null) }
    var editingComment by remember { mutableStateOf<TimelineEvent.CommentEvent?>(null) }
    var editingCommentBody by remember { mutableStateOf("") }
    var deletingComment by remember { mutableStateOf<TimelineEvent.CommentEvent?>(null) }
    var showReviewersDialog by remember { mutableStateOf(false) }
    var selectedReviewerLogins by remember { mutableStateOf<Set<String>>(emptySet()) }
    var customReviewerLogin by remember { mutableStateOf("") }

    fun loadDetail(forceRefresh: Boolean = false) {
        scope.launch {
            val cached = container.repository.peekIssueOrPullDetail(target.owner, target.repo, target.number)
            if (cached != null) {
                detail = cached
            } else {
                isLoading = true
            }
            errorMsg = null
            container.repository.getIssueOrPullDetail(target.owner, target.repo, target.number, forceRefresh = forceRefresh)
                .onSuccess { loaded ->
                    detail = loaded
                    isLoading = false
                    if (loaded.kind == IssueOrPullKind.PULL_REQUEST && (files.isEmpty() || forceRefresh)) {
                        filesLoading = files.isEmpty()
                        container.repository.getPullFiles(target.owner, target.repo, target.number, forceRefresh = forceRefresh)
                            .onSuccess { files = it }
                        filesLoading = false
                    }
                }
                .onFailure {
                    errorMsg = it.message ?: "Failed to load details"
                    isLoading = false
                }
        }
    }

    LaunchedEffect(target) {
        loadDetail(forceRefresh = false)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading && detail == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    MaxxingLoadingState(message = "Loading #${target.number}...")
                }
            }

            errorMsg != null && detail == null -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = errorMsg ?: "Unknown error",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (showBackButton) {
                            OutlinedButton(onClick = onBack) { Text("Back") }
                        }
                        Button(onClick = { loadDetail(forceRefresh = true) }) { Text("Retry") }
                    }
                }
            }

            detail != null -> {
                val item = detail!!
                val isPr = item.kind == IssueOrPullKind.PULL_REQUEST
                val headerTracker = remember(item.number, isPr) { RepoHeaderCollapseTracker() }
                val headerNestedScroll = remember(headerTracker) { headerTracker.toNestedScrollConnection() }
                val headerVerticalPadding by animateDpAsState(if (headerTracker.isCollapsed) 4.dp else 12.dp)

                Column(modifier = Modifier.fillMaxSize().nestedScroll(headerNestedScroll)) {
                    // Top Bar
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 0.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = headerVerticalPadding)
                        ) {
                            MaxxingCollapsibleSection(visible = !headerTracker.isCollapsed || !isPr) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (showBackButton) {
                                        IconButton(onClick = onBack) {
                                            Icon(Lucide.ArrowLeft, contentDescription = "Back")
                                        }
                                    }
                                    Text(
                                        text = "${item.repoFullName} #${item.number}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { loadDetail(forceRefresh = true) }) {
                                        Icon(Lucide.RefreshCw, contentDescription = "Refresh", modifier = Modifier.size(18.dp))
                                    }
                                    if (item.htmlUrl.isNotBlank()) {
                                        IconButton(onClick = { openUrlInBrowser(item.htmlUrl) }) {
                                            Icon(Lucide.ExternalLink, contentDescription = "Open in browser", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }

                            if (actionBusy || isLoading) {
                                MaxxingWavyProgress(modifier = Modifier.padding(vertical = 4.dp))
                            }

                            if (isPr) {
                                if (!headerTracker.isCollapsed) {
                                    Spacer(Modifier.height(6.dp))
                                }
                                val headRef = item.headRefName
                                LaunchedEffect(headRef, selectedTab) {
                                    if (!headRef.isNullOrBlank() && selectedTab == DetailTab.CHECKS && checkRuns == null) {
                                        checksLoading = true
                                        container.repository.getCheckRunsForRef(target.owner, target.repo, headRef)
                                            .onSuccess { checkRuns = it }
                                            .onFailure { onMessage(it.message ?: "Failed to load CI checks") }
                                        checksLoading = false
                                    }
                                }
                                PrimaryScrollableTabRow(
                                    selectedTabIndex = DetailTab.entries.indexOf(selectedTab).coerceAtLeast(0),
                                    containerColor = Color.Transparent,
                                    edgePadding = 0.dp
                                ) {
                                    DetailTab.entries.forEach { tab ->
                                        val label = when (tab) {
                                            DetailTab.FILES -> "${tab.label} (${item.changedFilesCount})"
                                            DetailTab.CHECKS -> checkRuns?.let { "${tab.label} (${it.size})" } ?: tab.label
                                            DetailTab.CONVERSATION -> tab.label
                                        }
                                        val icon = when (tab) {
                                            DetailTab.CONVERSATION -> Lucide.MessageSquare
                                            DetailTab.CHECKS -> Lucide.CircleCheck
                                            DetailTab.FILES -> Lucide.FileDiff
                                        }
                                        Tab(
                                            selected = selectedTab == tab,
                                            onClick = { selectedTab = tab },
                                            text = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
                                                    Text(label, maxLines = 1, softWrap = false)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (selectedTab == DetailTab.CONVERSATION || !isPr) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                DetailHeaderCard(
                                    item = item,
                                    onEditLabels = {
                                        selectedLabelNames = item.labels.map { it.name }.toSet()
                                        showLabelsDialog = true
                                        scope.launch {
                                            container.repository.getRepoLabels(target.owner, target.repo)
                                                .onSuccess { availableLabels = it }
                                                .onFailure { onMessage(it.message ?: "Failed to load labels") }
                                        }
                                    },
                                    onEditAssignees = {
                                        selectedAssigneeLogins = item.assignees.map { it.login }.toSet()
                                        showAssigneesDialog = true
                                        scope.launch {
                                            container.repository.getRepoAssignees(target.owner, target.repo)
                                                .onSuccess { availableAssignees = it }
                                                .onFailure { onMessage(it.message ?: "Failed to load assignees") }
                                        }
                                    },
                                    onEditMilestone = {
                                        selectedMilestoneNumber = item.milestone?.number
                                        showMilestoneDialog = true
                                        scope.launch {
                                            container.repository.getRepoMilestones(target.owner, target.repo)
                                                .onSuccess { availableMilestones = it }
                                                .onFailure { onMessage(it.message ?: "Failed to load milestones") }
                                        }
                                    },
                                    onRequestReviewers = {
                                        selectedReviewerLogins = item.requestedReviewers.map { it.login }.toSet()
                                        customReviewerLogin = ""
                                        showReviewersDialog = true
                                        if (availableAssignees.isEmpty()) {
                                            scope.launch {
                                                container.repository.getRepoAssignees(target.owner, target.repo)
                                                    .onSuccess { availableAssignees = it }
                                            }
                                        }
                                    },
                                    onToggleDraft = {
                                        scope.launch {
                                            actionBusy = true
                                            val nextDraft = !item.isDraft
                                            container.repository.setPullRequestDraftState(
                                                target.owner, target.repo, target.number, item.id, nextDraft
                                            ).onSuccess {
                                                onMessage(if (nextDraft) "Converted PR to draft" else "Marked PR ready for review")
                                                loadDetail(forceRefresh = true)
                                            }.onFailure {
                                                onMessage(it.message ?: "Failed to update draft state")
                                            }
                                            actionBusy = false
                                        }
                                    }
                                )
                            }

                            if (item.body.isNotBlank() || item.reactions.isNotEmpty() || item.viewerCanUpdate) {
                                item {
                                    ElevatedCard(
                                        shape = MaterialTheme.shapes.large,
                                        colors = CardDefaults.elevatedCardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                UserAvatar(item.author.avatarUrl, item.author.login, sizeDp = 24)
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    text = "@${item.author.login}",
                                                    style = MaterialTheme.typography.labelLarge
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    text = item.createdAt.take(10),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (item.body.isNotBlank()) {
                                                Spacer(Modifier.height(12.dp))
                                                GitHubMarkdown(item.body)
                                            }
                                            Spacer(Modifier.height(10.dp))
                                            ReactionsBar(
                                                reactions = item.reactions,
                                                enabled = !actionBusy,
                                                onToggleReaction = { content, currentlyReacted ->
                                                    scope.launch {
                                                        container.repository.toggleReaction(
                                                            target.owner, target.repo, target.number,
                                                            item.id, content, currentlyReacted
                                                        ).onSuccess {
                                                            loadDetail(forceRefresh = true)
                                                        }.onFailure {
                                                            onMessage(it.message ?: "Reaction failed")
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            items(
                                items = item.timeline,
                                key = { it.id },
                                contentType = { it::class }
                            ) { event ->
                                TimelineItemCard(
                                    event = event,
                                    actionBusy = actionBusy,
                                    onToggleReaction = { subjectId, content, reacted ->
                                        scope.launch {
                                            container.repository.toggleReaction(
                                                target.owner, target.repo, target.number,
                                                subjectId, content, reacted
                                            ).onSuccess {
                                                loadDetail(forceRefresh = true)
                                            }.onFailure {
                                                onMessage(it.message ?: "Reaction failed")
                                            }
                                        }
                                    },
                                    onEditComment = { c ->
                                        editingCommentBody = c.body
                                        editingComment = c
                                    },
                                    onDeleteComment = { c -> deletingComment = c }
                                )
                            }
                        }
                    } else if (selectedTab == DetailTab.CHECKS) {
                        val runs = checkRuns
                        when {
                            checksLoading && runs == null -> {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    MaxxingLoadingState(message = "Loading CI check runs...")
                                }
                            }
                            runs.isNullOrEmpty() -> {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Lucide.CircleCheck,
                                        contentDescription = null,
                                        modifier = Modifier.size(44.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Text("No CI check runs reported", style = MaterialTheme.typography.titleMedium)
                                    val headRef = item.headRefName
                                    if (!headRef.isNullOrBlank()) {
                                        Spacer(Modifier.height(10.dp))
                                        FilledTonalButton(onClick = {
                                            scope.launch {
                                                checksLoading = true
                                                container.repository.getCheckRunsForRef(
                                                    target.owner, target.repo, headRef, forceRefresh = true
                                                ).onSuccess { checkRuns = it }
                                                checksLoading = false
                                            }
                                        }) { Text("Refresh Checks") }
                                    }
                                }
                            }
                            else -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(items = runs, key = { it.id }, contentType = { "check_run" }) { cr ->
                                        val isSuccess = cr.conclusion.equals("success", ignoreCase = true)
                                        val isFailure = cr.conclusion.equals("failure", ignoreCase = true) ||
                                            cr.conclusion.equals("timed_out", ignoreCase = true)
                                        val tint = when {
                                            isSuccess -> Color(0xFF16A34A)
                                            isFailure -> MaterialTheme.colorScheme.error
                                            else -> Color(0xFFD97706)
                                        }
                                        val icon = when {
                                            isSuccess -> Lucide.CircleCheck
                                            isFailure -> Lucide.X
                                            else -> Lucide.CircleDot
                                        }
                                        ElevatedCard(
                                            shape = MaterialTheme.shapes.large,
                                            colors = CardDefaults.elevatedCardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(14.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(cr.name, style = MaterialTheme.typography.titleSmall)
                                                        cr.appName?.let { app ->
                                                            Text(
                                                                app,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                    ExpressiveStatusPill(
                                                        label = (cr.conclusion ?: cr.status).uppercase(),
                                                        containerColor = tint.copy(alpha = 0.16f),
                                                        contentColor = tint
                                                    )
                                                    if (cr.detailsUrl.isNotBlank()) {
                                                        IconButton(onClick = { openUrlInBrowser(cr.detailsUrl) }) {
                                                            Icon(Lucide.ExternalLink, contentDescription = "Open details", modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                }
                                                cr.title?.takeIf { it.isNotBlank() }?.let { title ->
                                                    Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                                }
                                                cr.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                                                    GitHubMarkdown(summary)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        val expandedFiles = remember(target, files) {
                            mutableStateMapOf<String, Boolean>().apply {
                                val defaultOpen = files.size <= 6
                                files.forEach { put(it.filename, defaultOpen) }
                            }
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (filesLoading) {
                                item(key = "files_loading", contentType = "loading") {
                                    MaxxingWavyProgress()
                                }
                            }
                            if (!filesLoading && files.isEmpty()) {
                                item(key = "files_empty", contentType = "empty") {
                                    Text(
                                        text = "No file diffs returned for this pull request.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            files.forEach { file ->
                                val isExpanded = expandedFiles[file.filename] ?: true
                                diffFileLazyItems(
                                    file = file,
                                    expanded = isExpanded,
                                    onToggleExpand = { expandedFiles[file.filename] = !isExpanded },
                                    onOpenFullScreen = { fullScreenDiffFile = file },
                                    onCommentFile = if (!item.headRefOid.isNullOrBlank()) {
                                        {
                                            diffCommentBody = ""
                                            pendingDiffCommentTarget = Triple(file.filename, null, "RIGHT")
                                        }
                                    } else null
                                )
                            }
                        }
                    }
                }

                var pendingMergeConfirmMethod by remember(target) { mutableStateOf<MergeMethod?>(null) }
                var pendingStateConfirmOpen by remember(target) { mutableStateOf<Boolean?>(null) }

                // MD3E Floating Action Toolbar at bottom
                MaxxingFloatingToolbar(
                    expanded = true,
                    vibrant = true,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = { showCommentDialog = true },
                        enabled = !actionBusy
                    ) {
                        Icon(Lucide.MessageSquarePlus, contentDescription = "Comment")
                    }

                    if (isPr && item.state == IssueOrPullState.OPEN) {
                        Spacer(Modifier.width(6.dp))
                        FilledTonalButton(
                            onClick = { showReviewDialog = true },
                            enabled = !actionBusy
                        ) {
                            Icon(Lucide.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Review")
                        }

                        if (item.viewerCanMerge) {
                            Spacer(Modifier.width(6.dp))
                            val mergeOptions = remember {
                                MergeMethod.entries.map {
                                    SplitActionOption(it, it.displayLabel, Lucide.GitMerge)
                                }
                            }
                            MaxxingSplitActionButton(
                                selectedOption = mergeOptions.first { it.value == selectedMergeMethod },
                                options = mergeOptions,
                                enabled = !actionBusy && !item.isDraft,
                                onSelectOption = { selectedMergeMethod = it },
                                onPrimaryClick = { method ->
                                    pendingMergeConfirmMethod = method
                                }
                            )
                        }
                    }

                    if (item.state != IssueOrPullState.MERGED && item.viewerCanCloseOrReopen) {
                        val willOpen = item.state == IssueOrPullState.CLOSED
                        val mergeVisible = isPr && item.state == IssueOrPullState.OPEN && item.viewerCanMerge
                        Spacer(Modifier.width(6.dp))
                        if (mergeVisible) {
                            FilledTonalIconButton(
                                onClick = { pendingStateConfirmOpen = willOpen },
                                enabled = !actionBusy
                            ) {
                                Icon(
                                    if (willOpen) Lucide.CircleDot else Lucide.X,
                                    contentDescription = if (willOpen) "Reopen" else "Close"
                                )
                            }
                        } else {
                            FilledTonalButton(
                                onClick = { pendingStateConfirmOpen = willOpen },
                                enabled = !actionBusy
                            ) {
                                Icon(
                                    if (willOpen) Lucide.CircleDot else Lucide.X,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(if (willOpen) "Reopen" else "Close")
                            }
                        }
                    }
                }

                pendingMergeConfirmMethod?.let { method ->
                    AlertDialog(
                        onDismissRequest = { pendingMergeConfirmMethod = null },
                        icon = { Icon(Lucide.GitMerge, contentDescription = null) },
                        title = { Text("Merge #${target.number}?") },
                        text = {
                            Text(
                                "Are you sure you want to merge into ${item.baseRefName ?: "base"} " +
                                    "using ${method.displayLabel}?\n\n${item.title}"
                            )
                        },
                        confirmButton = {
                            Button(
                                enabled = !actionBusy,
                                onClick = {
                                    pendingMergeConfirmMethod = null
                                    scope.launch {
                                        actionBusy = true
                                        container.repository.mergePullRequest(
                                            target.owner, target.repo, target.number, method
                                        ).onSuccess {
                                            onMessage("Merged #${target.number} (${method.apiValue})")
                                            loadDetail(forceRefresh = true)
                                        }.onFailure {
                                            onMessage(it.message ?: "Merge failed")
                                        }
                                        actionBusy = false
                                    }
                                }
                            ) {
                                Text("Merge")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingMergeConfirmMethod = null }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                pendingStateConfirmOpen?.let { willOpen ->
                    val kindLabel = if (isPr) "pull request" else "issue"
                    AlertDialog(
                        onDismissRequest = { pendingStateConfirmOpen = null },
                        icon = {
                            Icon(
                                if (willOpen) Lucide.CircleDot else Lucide.X,
                                contentDescription = null
                            )
                        },
                        title = { Text(if (willOpen) "Reopen #${target.number}?" else "Close #${target.number}?") },
                        text = {
                            Text(
                                "Are you sure you want to ${if (willOpen) "reopen" else "close"} " +
                                    "this $kindLabel?\n\n${item.title}"
                            )
                        },
                        confirmButton = {
                            Button(
                                enabled = !actionBusy,
                                colors = if (!willOpen) {
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    )
                                } else {
                                    ButtonDefaults.buttonColors()
                                },
                                onClick = {
                                    pendingStateConfirmOpen = null
                                    scope.launch {
                                        actionBusy = true
                                        container.repository.setIssueOrPullState(
                                            target.owner, target.repo, target.number, open = willOpen
                                        ).onSuccess {
                                            onMessage(
                                                if (willOpen) "Reopened #${target.number}"
                                                else "Closed #${target.number}"
                                            )
                                            loadDetail(forceRefresh = true)
                                        }.onFailure {
                                            onMessage(it.message ?: "Failed to update state")
                                        }
                                        actionBusy = false
                                    }
                                }
                            ) {
                                Text(if (willOpen) "Reopen" else "Close")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingStateConfirmOpen = null }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }
        }
    }

    fullScreenDiffFile?.let { requestedFile ->
        val activeFile = files.firstOrNull { it.filename == requestedFile.filename } ?: requestedFile
        val canInlineComment = !detail?.headRefOid.isNullOrBlank()
        val hasDialogAbove = showCommentDialog ||
            showReviewDialog ||
            pendingDiffCommentTarget != null ||
            editingComment != null ||
            deletingComment != null ||
            showLabelsDialog ||
            showAssigneesDialog ||
            showMilestoneDialog ||
            showReviewersDialog
        MaxxingFullScreenDiffViewer(
            file = activeFile,
            files = files.ifEmpty { listOf(activeFile) },
            onDismiss = { fullScreenDiffFile = null },
            onSelectFile = { next -> fullScreenDiffFile = next },
            onCommentFile = if (canInlineComment) {
                {
                    diffCommentBody = ""
                    pendingDiffCommentTarget = Triple(activeFile.filename, null, "RIGHT")
                }
            } else null,
            onCommentLine = if (canInlineComment) {
                { line: DiffLine ->
                    val side = if (line.kind == DiffLineKind.REMOVED) "LEFT" else "RIGHT"
                    val lineNum = if (side == "LEFT") line.oldLineNumber else line.newLineNumber
                    if (lineNum != null) {
                        diffCommentBody = ""
                        pendingDiffCommentTarget = Triple(activeFile.filename, lineNum, side)
                    }
                }
            } else null,
            backEnabled = !hasDialogAbove
        )
    }

    if (showCommentDialog) {
        MaxxingFullScreenEditorScaffold(
            title = "Comment on #${target.number}",
            subtitle = "${target.owner}/${target.repo}",
            saveLabel = "Post",
            canSave = commentDraft.isNotBlank(),
            isSubmitting = actionBusy,
            bodyValue = commentDraft,
            onBodyChange = { commentDraft = it },
            bodyLabel = "Markdown comment",
            supportsMarkdownPreview = true,
            onDismiss = { showCommentDialog = false },
            onSave = {
                val text = commentDraft
                showCommentDialog = false
                scope.launch {
                    actionBusy = true
                    container.repository.postComment(target.owner, target.repo, target.number, text)
                        .onSuccess {
                            commentDraft = ""
                            onMessage("Comment posted")
                            loadDetail(forceRefresh = true)
                        }
                        .onFailure { onMessage(it.message ?: "Failed to post comment") }
                    actionBusy = false
                }
            }
        )
    }

    if (showReviewDialog) {
        MaxxingFullScreenEditorScaffold(
            title = "Submit PR Review",
            subtitle = "#${target.number} · ${reviewEvent.displayLabel}",
            saveLabel = "Submit",
            canSave = reviewEvent == ReviewEvent.APPROVE || reviewBody.isNotBlank(),
            isSubmitting = actionBusy,
            bodyValue = reviewBody,
            onBodyChange = { reviewBody = it },
            bodyLabel = "Review summary (optional for Approve)",
            supportsMarkdownPreview = true,
            onDismiss = { showReviewDialog = false },
            onSave = {
                val event = reviewEvent
                val body = reviewBody
                showReviewDialog = false
                scope.launch {
                    actionBusy = true
                    container.repository.submitPullReview(target.owner, target.repo, target.number, event, body)
                        .onSuccess {
                            reviewBody = ""
                            onMessage("Review submitted (${event.displayLabel})")
                            loadDetail(forceRefresh = true)
                        }
                        .onFailure { onMessage(it.message ?: "Review submission failed") }
                    actionBusy = false
                }
            },
            headerContent = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Decision", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    MaxxingDropdownSelector(
                        options = ReviewEvent.entries,
                        selected = reviewEvent,
                        onSelect = { reviewEvent = it },
                        labelProvider = { it.displayLabel }
                    )
                }
            }
        )
    }

    if (showLabelsDialog) {
        RepoLabelsPickerDialog(
            title = "Manage Labels",
            availableLabels = availableLabels,
            selectedLabelNames = selectedLabelNames,
            onToggleLabel = { labelName, isChecked ->
                selectedLabelNames = if (isChecked) {
                    selectedLabelNames + labelName
                } else {
                    selectedLabelNames - labelName
                }
            },
            onDismiss = { showLabelsDialog = false },
            onConfirm = {
                val nextLabels = selectedLabelNames.toList()
                showLabelsDialog = false
                scope.launch {
                    actionBusy = true
                    container.repository.updateIssueOrPullMetadata(
                        owner = target.owner,
                        repo = target.repo,
                        number = target.number,
                        labels = nextLabels
                    ).onSuccess {
                        onMessage("Updated labels")
                        loadDetail(forceRefresh = true)
                    }.onFailure {
                        onMessage(it.message ?: "Failed to update labels")
                    }
                    actionBusy = false
                }
            },
            isBusy = actionBusy,
            newLabelName = newLabelName,
            onNewLabelNameChange = { newLabelName = it },
            onCreateLabel = { name ->
                scope.launch {
                    actionBusy = true
                    container.repository.createRepoLabel(target.owner, target.repo, name, newLabelColor)
                        .onSuccess { created ->
                            availableLabels = (availableLabels + created).distinctBy { it.name }
                            selectedLabelNames = selectedLabelNames + created.name
                            newLabelName = ""
                            onMessage("Created label ${created.name}")
                        }
                        .onFailure { onMessage(it.message ?: "Failed to create label") }
                    actionBusy = false
                }
            }
        )
    }

    if (showAssigneesDialog) {
        AlertDialog(
            onDismissRequest = { showAssigneesDialog = false },
            title = { Text("Manage Assignees") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().height(250.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (availableAssignees.isEmpty()) {
                        Text("No assignable users returned.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    availableAssignees.forEach { user ->
                        val checked = user.login in selectedAssigneeLogins
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedAssigneeLogins = if (checked) {
                                        selectedAssigneeLogins - user.login
                                    } else {
                                        selectedAssigneeLogins + user.login
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    selectedAssigneeLogins = if (isChecked) {
                                        selectedAssigneeLogins + user.login
                                    } else {
                                        selectedAssigneeLogins - user.login
                                    }
                                }
                            )
                            UserAvatar(user.avatarUrl, user.login, sizeDp = 24)
                            Text("@${user.login}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !actionBusy,
                    onClick = {
                        val nextAssignees = selectedAssigneeLogins.toList()
                        showAssigneesDialog = false
                        scope.launch {
                            actionBusy = true
                            container.repository.updateIssueOrPullMetadata(
                                owner = target.owner,
                                repo = target.repo,
                                number = target.number,
                                assignees = nextAssignees
                            ).onSuccess {
                                onMessage("Updated assignees")
                                loadDetail(forceRefresh = true)
                            }.onFailure {
                                onMessage(it.message ?: "Failed to update assignees")
                            }
                            actionBusy = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAssigneesDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showMilestoneDialog) {
        AlertDialog(
            onDismissRequest = { showMilestoneDialog = false },
            title = { Text("Select Milestone") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().height(230.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMilestoneNumber = null }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedMilestoneNumber == null,
                            onClick = { selectedMilestoneNumber = null }
                        )
                        Text("No milestone", style = MaterialTheme.typography.bodyMedium)
                    }
                    availableMilestones.forEach { ms ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedMilestoneNumber = ms.number }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedMilestoneNumber == ms.number,
                                onClick = { selectedMilestoneNumber = ms.number }
                            )
                            Column {
                                Text(ms.title, style = MaterialTheme.typography.bodyMedium)
                                ms.dueOn?.take(10)?.let { due ->
                                    Text("Due $due", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !actionBusy,
                    onClick = {
                        val chosen = selectedMilestoneNumber
                        showMilestoneDialog = false
                        scope.launch {
                            actionBusy = true
                            container.repository.updateIssueOrPullMetadata(
                                owner = target.owner,
                                repo = target.repo,
                                number = target.number,
                                milestoneNumber = chosen,
                                clearMilestone = chosen == null
                            ).onSuccess {
                                onMessage(if (chosen == null) "Cleared milestone" else "Updated milestone")
                                loadDetail(forceRefresh = true)
                            }.onFailure {
                                onMessage(it.message ?: "Failed to update milestone")
                            }
                            actionBusy = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMilestoneDialog = false }) { Text("Cancel") }
            }
        )
    }

    pendingDiffCommentTarget?.let { (filePath, lineNum, side) ->
        val commitSha = detail?.headRefOid
        MaxxingFullScreenEditorScaffold(
            title = if (lineNum != null) "Review Comment: ${filePath.substringAfterLast('/')}:$lineNum"
            else "File Comment: ${filePath.substringAfterLast('/')}",
            subtitle = filePath,
            saveLabel = "Post",
            canSave = diffCommentBody.trim().isNotBlank() && !commitSha.isNullOrBlank(),
            isSubmitting = actionBusy,
            bodyValue = diffCommentBody,
            onBodyChange = { diffCommentBody = it },
            bodyLabel = "Comment (Markdown)",
            supportsMarkdownPreview = true,
            onDismiss = { pendingDiffCommentTarget = null },
            onSave = {
                val bodyToPost = diffCommentBody.trim()
                val sha = commitSha ?: return@MaxxingFullScreenEditorScaffold
                pendingDiffCommentTarget = null
                scope.launch {
                    actionBusy = true
                    container.repository.createPullDiffComment(
                        owner = target.owner,
                        repo = target.repo,
                        number = target.number,
                        commitId = sha,
                        path = filePath,
                        body = bodyToPost,
                        line = lineNum,
                        side = side
                    ).onSuccess {
                        onMessage("Added inline diff comment")
                        loadDetail(forceRefresh = true)
                    }.onFailure {
                        onMessage(it.message ?: "Failed to post diff comment")
                    }
                    actionBusy = false
                }
            }
        )
    }

    editingComment?.let { c ->
        MaxxingFullScreenEditorScaffold(
            title = "Edit Comment",
            subtitle = "@${c.author.login} on #${target.number}",
            saveLabel = "Save",
            canSave = editingCommentBody.trim().isNotBlank(),
            isSubmitting = actionBusy,
            bodyValue = editingCommentBody,
            onBodyChange = { editingCommentBody = it },
            bodyLabel = "Comment body (Markdown)",
            supportsMarkdownPreview = true,
            onDismiss = { editingComment = null },
            onSave = {
                val newBody = editingCommentBody.trim()
                editingComment = null
                scope.launch {
                    actionBusy = true
                    container.repository.updateIssueComment(
                        target.owner, target.repo, target.number, c.id, newBody
                    ).onSuccess {
                        onMessage("Updated comment")
                        loadDetail(forceRefresh = true)
                    }.onFailure {
                        onMessage(it.message ?: "Failed to update comment")
                    }
                    actionBusy = false
                }
            }
        )
    }

    deletingComment?.let { c ->
        AlertDialog(
            onDismissRequest = { deletingComment = null },
            title = { Text("Delete Comment?") },
            text = { Text("Are you sure you want to delete this comment by @${c.author.login}?") },
            confirmButton = {
                Button(
                    enabled = !actionBusy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    onClick = {
                        deletingComment = null
                        scope.launch {
                            actionBusy = true
                            container.repository.deleteIssueComment(
                                target.owner, target.repo, target.number, c.id
                            ).onSuccess {
                                onMessage("Deleted comment")
                                loadDetail(forceRefresh = true)
                            }.onFailure {
                                onMessage(it.message ?: "Failed to delete comment")
                            }
                            actionBusy = false
                        }
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingComment = null }) { Text("Cancel") }
            }
        )
    }

    if (showReviewersDialog) {
        AlertDialog(
            onDismissRequest = { showReviewersDialog = false },
            title = { Text("Request Reviewers") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = customReviewerLogin,
                            onValueChange = { customReviewerLogin = it },
                            label = { Text("GitHub username") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalIconButton(
                            enabled = customReviewerLogin.trim().isNotBlank(),
                            onClick = {
                                val login = customReviewerLogin.trim().removePrefix("@")
                                if (login.isNotBlank()) {
                                    selectedReviewerLogins = selectedReviewerLogins + login
                                    customReviewerLogin = ""
                                }
                            }
                        ) {
                            Icon(Lucide.Plus, contentDescription = "Add reviewer")
                        }
                    }
                    if (availableAssignees.isNotEmpty()) {
                        Column(
                            modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            availableAssignees.forEach { user ->
                                val checked = user.login in selectedReviewerLogins
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedReviewerLogins = if (checked) {
                                                selectedReviewerLogins - user.login
                                            } else {
                                                selectedReviewerLogins + user.login
                                            }
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { isChecked ->
                                            selectedReviewerLogins = if (isChecked) {
                                                selectedReviewerLogins + user.login
                                            } else {
                                                selectedReviewerLogins - user.login
                                            }
                                        }
                                    )
                                    UserAvatar(user.avatarUrl, user.login, sizeDp = 24)
                                    Spacer(Modifier.width(8.dp))
                                    Text("@${user.login}", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = selectedReviewerLogins.isNotEmpty() && !actionBusy,
                    onClick = {
                        val reviewers = selectedReviewerLogins.toList()
                        showReviewersDialog = false
                        scope.launch {
                            actionBusy = true
                            container.repository.requestPullReviewers(
                                target.owner, target.repo, target.number, reviewers
                            ).onSuccess {
                                onMessage("Requested reviewers: ${reviewers.joinToString(", ")}")
                                loadDetail(forceRefresh = true)
                            }.onFailure {
                                onMessage(it.message ?: "Failed to request reviewers")
                            }
                            actionBusy = false
                        }
                    }
                ) { Text("Request") }
            },
            dismissButton = {
                TextButton(onClick = { showReviewersDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailHeaderCard(
    item: IssueOrPullDetail,
    onEditLabels: () -> Unit,
    onEditAssignees: () -> Unit,
    onEditMilestone: () -> Unit,
    onRequestReviewers: () -> Unit,
    onToggleDraft: () -> Unit
) {
    val (stateLabel, stateBg, stateFg) = when {
        item.isDraft -> Triple(
            "DRAFT",
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        item.state == IssueOrPullState.OPEN -> Triple(
            "OPEN",
            Color(0xFF16A34A),
            Color.White
        )
        item.state == IssueOrPullState.MERGED -> Triple(
            "MERGED",
            Color(0xFF7C3AED),
            Color.White
        )
        else -> Triple(
            "CLOSED",
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.onError
        )
    }

    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExpressiveStatusPill(
                    label = stateLabel,
                    containerColor = stateBg,
                    contentColor = stateFg,
                    icon = when {
                        item.isDraft -> Lucide.Pencil
                        item.state == IssueOrPullState.MERGED -> Lucide.GitMerge
                        item.state == IssueOrPullState.CLOSED -> Lucide.CircleCheck
                        item.kind == IssueOrPullKind.PULL_REQUEST -> Lucide.GitPullRequest
                        else -> Lucide.CircleDot
                    }
                )
                item.checkRollup?.let { rollup ->
                    val checkColor = when (rollup.state) {
                        "SUCCESS" -> Color(0xFF16A34A)
                        "FAILURE", "ERROR" -> MaterialTheme.colorScheme.error
                        else -> Color(0xFFD97706)
                    }
                    ExpressiveStatusPill(
                        label = "CI: ${rollup.state}",
                        containerColor = checkColor.copy(alpha = 0.16f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                }
                item.reviewDecision?.let { decision ->
                    ExpressiveStatusPill(
                        label = decision.replace('_', ' '),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Text(
                text = item.title,
                style = MaterialTheme.typography.headlineSmall
            )

            if (item.kind == IssueOrPullKind.PULL_REQUEST && item.headRefName != null && item.baseRefName != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "${item.headRefName} → ${item.baseRefName}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        text = "+${item.additions} / -${item.deletions}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (item.labels.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item.labels.forEach { label ->
                        GitHubLabelChip(name = label.name, colorHex = label.colorHex)
                    }
                }
            }

            if (item.assignees.isNotEmpty() || item.milestone != null) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item.assignees.forEach { user ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            UserAvatar(user.avatarUrl, user.login, sizeDp = 20)
                            Text("@${user.login}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    item.milestone?.let { ms ->
                        ExpressiveStatusPill(
                            label = ms.title,
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            icon = Lucide.Flag
                        )
                    }
                }
            }

            val canAccessPrMeta = item.kind == IssueOrPullKind.PULL_REQUEST &&
                item.state == IssueOrPullState.OPEN &&
                (item.viewerCanEditMetadata || item.viewerCanUpdate)
            if (item.viewerCanEditMetadata || canAccessPrMeta) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (item.viewerCanEditMetadata) {
                        AssistChip(
                            onClick = onEditLabels,
                            leadingIcon = { Icon(Lucide.Tag, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            label = { Text("Labels (${item.labels.size})") }
                        )
                        AssistChip(
                            onClick = onEditAssignees,
                            leadingIcon = { Icon(Lucide.Users, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            label = { Text("Assignees (${item.assignees.size})") }
                        )
                        AssistChip(
                            onClick = onEditMilestone,
                            leadingIcon = { Icon(Lucide.Flag, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            label = { Text(item.milestone?.title ?: "Milestone") }
                        )
                    }
                    if (canAccessPrMeta) {
                        AssistChip(
                            onClick = onRequestReviewers,
                            leadingIcon = { Icon(Lucide.Users, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            label = { Text("Reviewers (${item.requestedReviewers.size})") }
                        )
                        if (item.viewerCanUpdate) {
                            AssistChip(
                                onClick = onToggleDraft,
                                leadingIcon = { Icon(Lucide.Pencil, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                label = { Text(if (item.isDraft) "Ready for review" else "Convert to draft") }
                            )
                        }
                    }
                }
            }
            if (item.requestedReviewers.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Reviewers:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    item.requestedReviewers.forEach { reviewer ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            UserAvatar(reviewer.avatarUrl, reviewer.login, sizeDp = 18)
                            Text("@${reviewer.login}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

private val REACTION_EMOJIS = listOf(
    "THUMBS_UP" to "👍",
    "THUMBS_DOWN" to "👎",
    "LAUGH" to "😄",
    "HOORAY" to "🎉",
    "CONFUSED" to "😕",
    "HEART" to "❤️",
    "ROCKET" to "🚀",
    "EYES" to "👀"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReactionsBar(
    reactions: List<ReactionSummary>,
    enabled: Boolean,
    onToggleReaction: (content: String, currentlyReacted: Boolean) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    val emojiLookup = remember { REACTION_EMOJIS.toMap() }
    val reactionByContent = remember(reactions) { reactions.associateBy { it.content } }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        reactions.filter { it.count > 0 || it.viewerHasReacted }.forEach { reaction ->
            val emoji = emojiLookup[reaction.content] ?: reaction.content
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (reaction.viewerHasReacted) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                modifier = Modifier.clickable(enabled = enabled) {
                    onToggleReaction(reaction.content, reaction.viewerHasReacted)
                }
            ) {
                Text(
                    text = "$emoji ${reaction.count}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (reaction.viewerHasReacted) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        Box {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.clickable(enabled = enabled) { showPicker = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(Lucide.Plus, contentDescription = "Add reaction", modifier = Modifier.size(12.dp))
                    Text("React", style = MaterialTheme.typography.labelSmall)
                }
            }
            DropdownMenu(expanded = showPicker, onDismissRequest = { showPicker = false }) {
                REACTION_EMOJIS.forEach { (contentKey, emoji) ->
                    val alreadyReacted = reactionByContent[contentKey]?.viewerHasReacted == true
                    DropdownMenuItem(
                        text = { Text("$emoji  ${contentKey.lowercase().replace('_', ' ')}") },
                        trailingIcon = if (alreadyReacted) {
                            { Icon(Lucide.Check, contentDescription = "Reacted", modifier = Modifier.size(14.dp)) }
                        } else null,
                        onClick = {
                            showPicker = false
                            onToggleReaction(contentKey, alreadyReacted)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineItemCard(
    event: TimelineEvent,
    actionBusy: Boolean = false,
    onToggleReaction: (subjectId: String, content: String, currentlyReacted: Boolean) -> Unit = { _, _, _ -> },
    onEditComment: (TimelineEvent.CommentEvent) -> Unit = {},
    onDeleteComment: (TimelineEvent.CommentEvent) -> Unit = {}
) {
    when (event) {
        is TimelineEvent.CommentEvent -> {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(event.author.avatarUrl, event.author.login, sizeDp = 22)
                        Spacer(Modifier.width(8.dp))
                        Text("@${event.author.login}", style = MaterialTheme.typography.labelLarge)
                        val association = event.authorAssociation
                        if (!association.isNullOrBlank() && association != "NONE") {
                            Spacer(Modifier.width(8.dp))
                            ExpressiveStatusPill(
                                label = association,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        if (event.viewerCanUpdate) {
                            IconButton(
                                onClick = { onEditComment(event) },
                                enabled = !actionBusy,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Lucide.Pencil, contentDescription = "Edit comment", modifier = Modifier.size(14.dp))
                            }
                        }
                        if (event.viewerCanDelete) {
                            IconButton(
                                onClick = { onDeleteComment(event) },
                                enabled = !actionBusy,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Lucide.Trash2,
                                    contentDescription = "Delete comment",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Text(
                            text = event.createdAt.take(10),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    GitHubMarkdown(event.body)
                    Spacer(Modifier.height(8.dp))
                    ReactionsBar(
                        reactions = event.reactions,
                        enabled = !actionBusy,
                        onToggleReaction = { content, reacted ->
                            onToggleReaction(event.id, content, reacted)
                        }
                    )
                }
            }
        }

        is TimelineEvent.ReviewEventItem -> {
            val badgeColor = when (event.state) {
                "APPROVED" -> Color(0xFF16A34A)
                "CHANGES_REQUESTED" -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.secondary
            }
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = badgeColor.copy(alpha = 0.08f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(event.author.avatarUrl, event.author.login, sizeDp = 22)
                        Spacer(Modifier.width(8.dp))
                        Text("@${event.author.login}", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(8.dp))
                        ExpressiveStatusPill(
                            label = event.state.replace('_', ' '),
                            containerColor = badgeColor.copy(alpha = 0.2f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (event.body.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        GitHubMarkdown(event.body)
                    }
                }
            }
        }

        is TimelineEvent.CommitEvent -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Lucide.GitCommitHorizontal,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = event.oidShort,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = event.messageHeadline,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        is TimelineEvent.StateChangeEvent -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val actor = event.actor
                if (actor != null) {
                    UserAvatar(actor.avatarUrl, actor.login, sizeDp = 18)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = "@${actor?.login ?: "system"} ${event.actionLabel} • ${event.createdAt.take(10)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
