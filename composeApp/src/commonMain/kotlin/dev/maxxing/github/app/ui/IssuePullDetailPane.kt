package dev.maxxing.github.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
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
import com.composables.icons.lucide.Search
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
import dev.maxxing.github.model.WorkflowJobSummary
import dev.maxxing.github.model.WorkflowRunSummary
import dev.maxxing.github.network.GitHubApiException
import dev.maxxing.github.ui.components.ExpressiveStatusPill
import dev.maxxing.github.ui.components.GitHubLabelChip
import dev.maxxing.github.ui.components.GitHubMarkdown
import dev.maxxing.github.ui.components.MaxxingDropdownSelector
import dev.maxxing.github.ui.components.MaxxingFloatingToolbar
import dev.maxxing.github.ui.components.MaxxingFloatingToolbarDefaults
import dev.maxxing.github.ui.components.MaxxingFullScreenDiffViewer
import dev.maxxing.github.ui.components.MaxxingFullScreenEditorScaffold
import dev.maxxing.github.ui.components.MaxxingLoadingState
import dev.maxxing.github.ui.components.MaxxingPredictiveOverlayHost
import dev.maxxing.github.ui.components.MaxxingSplitActionButton
import dev.maxxing.github.ui.components.MaxxingWavyProgress
import dev.maxxing.github.ui.components.RepoLabelsPickerDialog
import dev.maxxing.github.ui.components.SplitActionOption
import dev.maxxing.github.ui.components.UserAvatar
import dev.maxxing.github.ui.components.diffFileLazyItems
import dev.maxxing.github.ui.i18n.AppStrings
import dev.maxxing.github.ui.i18n.appStrings
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CHECKS_POLL_INTERVAL_MS = 30_000L

// Stop polling a PR after ~30 minutes of foreground polling in total (e.g. runs stuck waiting for a runner).
private const val CHECKS_POLL_MAX_COUNT = 60

/**
 * PRs with at most this many changed files open with every diff expanded; larger ones start
 * collapsed and get a file-name filter at the top of the Files tab.
 */
internal const val PULL_FILES_COMPACT_MAX_COUNT = 6

/**
 * Narrows a PR's changed files by a case-insensitive substring match on the file name or,
 * for renames, the previous file name. A blank [query] keeps every file.
 */
internal fun filterPullFiles(files: List<PullFileChange>, query: String): List<PullFileChange> {
    val needle = query.trim()
    if (needle.isEmpty()) return files
    return files.filter { file ->
        file.filename.contains(needle, ignoreCase = true) ||
            file.previousFilename?.contains(needle, ignoreCase = true) == true
    }
}

/**
 * Ref used to list a PR's check runs on the base repository. The head commit SHA
 * also resolves for PRs from forks, whose head branch does not exist in the base repo.
 */
internal fun resolveChecksRef(headRefOid: String?, headRefName: String?): String? =
    headRefOid?.takeIf { it.isNotBlank() } ?: headRefName?.takeIf { it.isNotBlank() }

internal fun hasPendingCheckRuns(runs: List<CheckRunSummary>?): Boolean =
    runs.orEmpty().any { it.status != "completed" }

internal data class ActionsRunTarget(
    val owner: String,
    val repo: String,
    val runId: Long,
    val jobId: Long? = null
)

private val ACTIONS_RUN_URL_REGEX = Regex(
    """^https?://[^/]+/([^/]+)/([^/]+)/actions/runs/(\d+)(?:/jobs?/(\d+))?"""
)

internal fun parseActionsRunUrl(detailsUrl: String): ActionsRunTarget? {
    val clean = detailsUrl.trim().substringBefore('?').substringBefore('#')
    val match = ACTIONS_RUN_URL_REGEX.find(clean) ?: return null
    val owner = match.groupValues[1]
    val repo = match.groupValues[2]
    val runId = match.groupValues[3].toLongOrNull() ?: return null
    val jobId = match.groupValues[4].takeIf { it.isNotBlank() }?.toLongOrNull()
    if (owner.isBlank() || repo.isBlank()) return null
    return ActionsRunTarget(owner = owner, repo = repo, runId = runId, jobId = jobId)
}

internal fun resolvePullMergeBlockReason(
    detail: IssueOrPullDetail,
    checkRuns: List<CheckRunSummary>?,
    strings: AppStrings
): String? {
    val mergeState = detail.mergeStateStatus?.uppercase()
    if (detail.mergeable.equals("CONFLICTING", ignoreCase = true) || mergeState == "DIRTY") {
        return strings.issueMergeBlockedConflicts
    }
    val hasPendingChecks = hasPendingCheckRuns(checkRuns) ||
        detail.checkRollup?.state?.uppercase() in setOf("PENDING", "EXPECTED")
    // Allow UNSTABLE / CLEAN / HAS_HOOKS if checks are non-required, unless individual checks are still pending
    if (hasPendingChecks && mergeState !in setOf("CLEAN", "HAS_HOOKS")) {
        return strings.issueMergeBlockedPendingChecks
    }
    if (mergeState == "BEHIND") {
        return strings.issueMergeBlockedBehind
    }
    if (mergeState == "BLOCKED") {
        val reviewDecision = detail.reviewDecision?.uppercase()
        return if (reviewDecision == "REVIEW_REQUIRED" || reviewDecision == "CHANGES_REQUESTED") {
            strings.issueMergeBlockedReview
        } else {
            strings.issueMergeBlockedPolicy
        }
    }
    return null
}

internal fun formatPullMergeFailureMessage(error: Throwable, strings: AppStrings): String {
    if (error is GitHubApiException && (error.statusCode == 405 || error.statusCode == 409)) {
        val msg = error.message.orEmpty()
        return when {
            msg.contains("conflict", ignoreCase = true) ||
                msg.contains("mergeable", ignoreCase = true) -> strings.issueMergeBlockedConflicts
            msg.contains("status check", ignoreCase = true) ||
                msg.contains("required check", ignoreCase = true) ||
                msg.contains("in progress", ignoreCase = true) ||
                msg.contains("pending", ignoreCase = true) -> strings.issueMergeBlockedPendingChecks
            msg.contains("review", ignoreCase = true) -> strings.issueMergeBlockedReview
            msg.contains("out-of-date", ignoreCase = true) ||
                msg.contains("behind", ignoreCase = true) -> strings.issueMergeBlockedBehind
            msg.contains("protected branch", ignoreCase = true) ||
                msg.contains("rule", ignoreCase = true) -> strings.issueMergeBlockedPolicy
            else -> strings.errorMessage(error, strings.issueMergeFailed)
        }
    }
    return strings.errorMessage(error, strings.issueMergeFailed)
}

internal enum class ChecksViewState { LOADING, ERROR, EMPTY, LIST }

/** What the Checks tab shows. Loaded runs stay visible while a refresh is running or after it fails. */
internal fun checksViewState(loading: Boolean, error: String?, runs: List<CheckRunSummary>?): ChecksViewState =
    when {
        !runs.isNullOrEmpty() -> ChecksViewState.LIST
        loading -> ChecksViewState.LOADING
        error != null -> ChecksViewState.ERROR
        else -> ChecksViewState.EMPTY
    }

internal enum class ChecksFailureAction { SHOW_ERROR, SNACKBAR, IGNORE }

/**
 * How a failed check-runs load is reported: replace an empty list with the error state,
 * otherwise keep the shown runs and notify, except for background polls.
 */
internal fun checksFailureAction(hasRuns: Boolean, silent: Boolean): ChecksFailureAction =
    when {
        !hasRuns -> ChecksFailureAction.SHOW_ERROR
        silent -> ChecksFailureAction.IGNORE
        else -> ChecksFailureAction.SNACKBAR
    }

private enum class DetailTab {
    CONVERSATION,
    CHECKS,
    FILES
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
    val targetKey = remember(target.owner, target.repo, target.number) { issuePullIdentityKey(target) }
    var detail by remember(targetKey) {
        mutableStateOf(container.repository.peekIssueOrPullDetail(target.owner, target.repo, target.number))
    }
    var files by remember(targetKey) {
        mutableStateOf(container.repository.peekPullFiles(target.owner, target.repo, target.number).orEmpty())
    }
    var isLoading by remember(targetKey) { mutableStateOf(detail == null) }
    var filesLoading by remember(targetKey) { mutableStateOf(false) }
    var checkRuns by remember(targetKey) { mutableStateOf<List<CheckRunSummary>?>(null) }
    var checksLoading by remember(targetKey) { mutableStateOf(false) }
    var checksError by remember(targetKey) { mutableStateOf<String?>(null) }
    // Ref (head SHA or branch) that checkRuns belong to; null until the first load.
    var checksLoadedRef by remember(targetKey) { mutableStateOf<String?>(null) }
    // Only the latest check-runs request may update the state above.
    var checksRequestId by remember(targetKey) { mutableIntStateOf(0) }
    // Polls spent on this PR; the cap holds across tab switches and returns from the background.
    var checksPollsUsed by remember(targetKey) { mutableIntStateOf(0) }
    var errorMsg by remember(targetKey) { mutableStateOf<String?>(null) }
    var selectedTab by remember(targetKey) { mutableStateOf(DetailTab.CONVERSATION) }

    // Draft/editing state is keyed to targetKey (owner/repo/number, ignoring hintTitle):
    // switching to another issue/PR drops drafts, while hintTitle updates keep them intact.
    var showCommentDialog by remember(targetKey) { mutableStateOf(false) }
    var commentDraft by remember(targetKey) { mutableStateOf("") }
    var showReviewDialog by remember(targetKey) { mutableStateOf(false) }
    var reviewBody by remember(targetKey) { mutableStateOf("") }
    var reviewEvent by remember(targetKey) { mutableStateOf(ReviewEvent.APPROVE) }
    var selectedMergeMethod by remember(targetKey) { mutableStateOf(MergeMethod.SQUASH) }
    var actionBusy by remember(targetKey) { mutableStateOf(false) }

    var showLabelsDialog by remember(targetKey) { mutableStateOf(false) }
    var availableLabels by remember(targetKey) { mutableStateOf<List<LabelTag>>(emptyList()) }
    var selectedLabelNames by remember(targetKey) { mutableStateOf<Set<String>>(emptySet()) }
    var newLabelName by remember(targetKey) { mutableStateOf("") }
    var newLabelColor by remember(targetKey) { mutableStateOf("6E40C9") }

    var showAssigneesDialog by remember(targetKey) { mutableStateOf(false) }
    var availableAssignees by remember(targetKey) { mutableStateOf<List<UserSummary>>(emptyList()) }
    var selectedAssigneeLogins by remember(targetKey) { mutableStateOf<Set<String>>(emptySet()) }

    var showMilestoneDialog by remember(targetKey) { mutableStateOf(false) }
    var availableMilestones by remember(targetKey) { mutableStateOf<List<MilestoneSummary>>(emptyList()) }
    var selectedMilestoneNumber by remember(targetKey) { mutableStateOf<Int?>(null) }

    val scope = rememberCoroutineScope()
    val strings = appStrings()

    var pendingDiffCommentTarget by remember(targetKey) { mutableStateOf<Triple<String, Int?, String>?>(null) }
    var diffCommentBody by remember(targetKey) { mutableStateOf("") }
    var fullScreenDiffFile by remember(targetKey) { mutableStateOf<PullFileChange?>(null) }
    var editingComment by remember(targetKey) { mutableStateOf<TimelineEvent.CommentEvent?>(null) }
    var editingCommentBody by remember(targetKey) { mutableStateOf("") }
    var deletingComment by remember(targetKey) { mutableStateOf<TimelineEvent.CommentEvent?>(null) }
    var showReviewersDialog by remember(targetKey) { mutableStateOf(false) }
    var selectedReviewerLogins by remember(targetKey) { mutableStateOf<Set<String>>(emptySet()) }
    var customReviewerLogin by remember(targetKey) { mutableStateOf("") }

    var openedActionsTarget by remember(target) { mutableStateOf<ActionsRunTarget?>(null) }
    var openedWorkflowRun by remember(target) { mutableStateOf<WorkflowRunSummary?>(null) }
    var openedWorkflowJobs by remember(target) { mutableStateOf<List<WorkflowJobSummary>?>(null) }
    var workflowRunLoading by remember(target) { mutableStateOf(false) }
    var workflowRunError by remember(target) { mutableStateOf<String?>(null) }
    var workflowActionBusy by remember(target) { mutableStateOf(false) }
    var openedLogJob by remember(target) { mutableStateOf<WorkflowJobSummary?>(null) }
    var jobLogsText by remember(target) { mutableStateOf<String?>(null) }
    var jobLogsLoading by remember(target) { mutableStateOf(false) }

    fun loadActionsOverlay(actionsTarget: ActionsRunTarget, forceRefresh: Boolean = false) {
        openedActionsTarget = actionsTarget
        workflowRunError = null
        workflowRunLoading = true
        scope.launch {
            container.repository.getWorkflowRun(
                owner = actionsTarget.owner,
                repo = actionsTarget.repo,
                runId = actionsTarget.runId,
                forceRefresh = forceRefresh
            ).onSuccess { run ->
                openedWorkflowRun = run
            }.onFailure { err ->
                workflowRunError = strings.errorMessage(err, strings.issueCheckFailedLoadRun)
            }
            container.repository.getWorkflowRunJobs(
                owner = actionsTarget.owner,
                repo = actionsTarget.repo,
                runId = actionsTarget.runId,
                forceRefresh = forceRefresh
            ).onSuccess { jobs ->
                openedWorkflowJobs = jobs
                val requestedJobId = actionsTarget.jobId
                if (requestedJobId != null && openedLogJob == null && !forceRefresh) {
                    jobs.firstOrNull { it.id == requestedJobId }?.let { matchedJob ->
                        openedLogJob = matchedJob
                        jobLogsLoading = true
                        jobLogsText = null
                        container.repository.getJobLogs(actionsTarget.owner, actionsTarget.repo, matchedJob.id)
                            .onSuccess { jobLogsText = it }
                            .onFailure { jobLogsText = strings.errorMessage(it, strings.failedLoadLogs) }
                        jobLogsLoading = false
                    }
                }
            }.onFailure { err ->
                if (workflowRunError == null) {
                    workflowRunError = strings.errorMessage(err, strings.failedLoadJobs)
                }
            }
            workflowRunLoading = false
        }
    }

    fun openWorkflowJobLogs(actionsTarget: ActionsRunTarget, job: WorkflowJobSummary) {
        openedLogJob = job
        jobLogsText = null
        jobLogsLoading = true
        scope.launch {
            container.repository.getJobLogs(actionsTarget.owner, actionsTarget.repo, job.id)
                .onSuccess { jobLogsText = it }
                .onFailure { jobLogsText = strings.errorMessage(it, strings.failedLoadLogs) }
            jobLogsLoading = false
        }
    }

    fun loadChecks(ref: String, forceRefresh: Boolean, silent: Boolean = false) {
        if (checksLoadedRef != ref) {
            // A new head commit: drop runs that belong to the previous one.
            checkRuns = null
            checksError = null
        }
        checksLoadedRef = ref
        checksLoading = true
        checksRequestId += 1
        val requestId = checksRequestId
        scope.launch {
            val result = container.repository.getCheckRunsForRef(target.owner, target.repo, ref, forceRefresh = forceRefresh)
            // A newer request (another head commit or a later refresh) owns the state now.
            if (requestId != checksRequestId) return@launch
            result
                .onSuccess {
                    checkRuns = it
                    checksError = null
                }
                .onFailure {
                    if (it !is CancellationException) {
                        val message = strings.errorMessage(it, strings.failedLoadCiChecks)
                        when (checksFailureAction(hasRuns = !checkRuns.isNullOrEmpty(), silent = silent)) {
                            ChecksFailureAction.SHOW_ERROR -> checksError = message
                            ChecksFailureAction.SNACKBAR -> onMessage(message)
                            ChecksFailureAction.IGNORE -> Unit
                        }
                    }
                }
            checksLoading = false
        }
    }

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
                    if (forceRefresh && loaded.kind == IssueOrPullKind.PULL_REQUEST && checksLoadedRef != null) {
                        resolveChecksRef(loaded.headRefOid, loaded.headRefName)?.let {
                            // Silent: a reload triggered by an unrelated action (reaction, comment, ...)
                            // must not pop a check-runs error snackbar; an empty tab still shows the error.
                            loadChecks(it, forceRefresh = true, silent = true)
                        }
                    }
                }
                .onFailure {
                    errorMsg = strings.errorMessage(it, strings.failedLoadDetails)
                    isLoading = false
                }
        }
    }

    LaunchedEffect(targetKey) {
        loadDetail(forceRefresh = false)
    }

    // Head ref whose runs were last seen unfinished. When they all finish, reload the PR so
    // the header CI rollup and the head commit catch up with the Checks tab.
    var checksPendingRef by remember(targetKey) { mutableStateOf<String?>(null) }
    LaunchedEffect(checkRuns) {
        val runs = checkRuns ?: return@LaunchedEffect
        val ref = checksLoadedRef
        if (hasPendingCheckRuns(runs)) {
            checksPendingRef = ref
        } else if (ref != null && checksPendingRef == ref) {
            checksPendingRef = null
            loadDetail(forceRefresh = true)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading && detail == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    MaxxingLoadingState(message = strings.issueLoadingNumber(target.number))
                }
            }

            errorMsg != null && detail == null -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = errorMsg ?: strings.unknownError,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (showBackButton) {
                            OutlinedButton(onClick = onBack) { Text(strings.back) }
                        }
                        Button(onClick = { loadDetail(forceRefresh = true) }) { Text(strings.retry) }
                    }
                }
            }

            detail != null -> {
                val item = detail!!
                val isPr = item.kind == IssueOrPullKind.PULL_REQUEST
                val headerTracker = remember(item.number, isPr) { RepoHeaderCollapseTracker() }
                val headerNestedScroll = remember(headerTracker) { headerTracker.toNestedScrollConnection() }
                val headerVerticalPadding by animateDpAsState(if (headerTracker.isCollapsed) 4.dp else 12.dp)
                // Shared by the header chip and the floating toolbar.
                val toggleDraft: () -> Unit = {
                    scope.launch {
                        actionBusy = true
                        val nextDraft = !item.isDraft
                        container.repository.setPullRequestDraftState(
                            target.owner, target.repo, target.number, item.id, nextDraft
                        ).onSuccess {
                            onMessage(if (nextDraft) strings.prConvertedToDraftMessage else strings.prMarkedReadyMessage)
                            loadDetail(forceRefresh = true)
                        }.onFailure {
                            onMessage(strings.errorMessage(it, strings.failedUpdateDraftState))
                        }
                        actionBusy = false
                    }
                }

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
                                            Icon(Lucide.ArrowLeft, contentDescription = strings.back)
                                        }
                                    }
                                    Text(
                                        text = "${item.repoFullName} #${item.number}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { loadDetail(forceRefresh = true) }) {
                                        Icon(Lucide.RefreshCw, contentDescription = strings.refresh, modifier = Modifier.size(18.dp))
                                    }
                                    if (item.htmlUrl.isNotBlank()) {
                                        IconButton(onClick = { openUrlInBrowser(item.htmlUrl) }) {
                                            Icon(Lucide.ExternalLink, contentDescription = strings.openInBrowser, modifier = Modifier.size(18.dp))
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
                                val checksRef = resolveChecksRef(item.headRefOid, item.headRefName)
                                LaunchedEffect(checksRef, selectedTab) {
                                    if (checksRef != null && selectedTab == DetailTab.CHECKS && checksLoadedRef != checksRef) {
                                        loadChecks(checksRef, forceRefresh = false)
                                    }
                                }
                                val checksPending = hasPendingCheckRuns(checkRuns)
                                val lifecycle = LocalLifecycleOwner.current.lifecycle
                                LaunchedEffect(checksRef, selectedTab, checksPending, lifecycle) {
                                    if (checksRef == null || selectedTab != DetailTab.CHECKS || !checksPending) return@LaunchedEffect
                                    // Poll while CI is still running and the Checks tab is visible,
                                    // pausing while the app is in the background or minimized.
                                    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                                        while (checksPollsUsed < CHECKS_POLL_MAX_COUNT) {
                                            delay(CHECKS_POLL_INTERVAL_MS)
                                            checksPollsUsed += 1
                                            if (!checksLoading) loadChecks(checksRef, forceRefresh = true, silent = true)
                                        }
                                    }
                                }
                                PrimaryScrollableTabRow(
                                    selectedTabIndex = DetailTab.entries.indexOf(selectedTab).coerceAtLeast(0),
                                    containerColor = Color.Transparent,
                                    edgePadding = 0.dp
                                ) {
                                    DetailTab.entries.forEach { tab ->
                                        val label = when (tab) {
                                            DetailTab.FILES -> "${strings.issueDetailTabFiles} (${item.changedFilesCount})"
                                            DetailTab.CHECKS -> checkRuns?.let { "${strings.issueDetailTabChecks} (${it.size})" } ?: strings.issueDetailTabChecks
                                            DetailTab.CONVERSATION -> strings.issueDetailTabChat
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
                                                .onFailure { onMessage(strings.errorMessage(it, strings.failedLoadLabels)) }
                                        }
                                    },
                                    onEditAssignees = {
                                        selectedAssigneeLogins = item.assignees.map { it.login }.toSet()
                                        showAssigneesDialog = true
                                        scope.launch {
                                            container.repository.getRepoAssignees(target.owner, target.repo)
                                                .onSuccess { availableAssignees = it }
                                                .onFailure { onMessage(strings.errorMessage(it, strings.failedLoadAssignees)) }
                                        }
                                    },
                                    onEditMilestone = {
                                        selectedMilestoneNumber = item.milestone?.number
                                        showMilestoneDialog = true
                                        scope.launch {
                                            container.repository.getRepoMilestones(target.owner, target.repo)
                                                .onSuccess { availableMilestones = it }
                                                .onFailure { onMessage(strings.errorMessage(it, strings.failedLoadMilestones)) }
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
                                    onToggleDraft = toggleDraft
                                )
                            }

                            if (isPr && item.isDraft && item.state == IssueOrPullState.OPEN) {
                                item {
                                    DraftNoticeCard()
                                }
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
                                                            onMessage(strings.errorMessage(it, strings.reactionFailed))
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
                                                onMessage(strings.errorMessage(it, strings.reactionFailed))
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
                        val checksRef = resolveChecksRef(item.headRefOid, item.headRefName)
                        when (checksViewState(checksLoading, checksError, runs)) {
                            ChecksViewState.LOADING -> {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    MaxxingLoadingState(message = strings.issueLoadingChecks)
                                }
                            }
                            ChecksViewState.ERROR -> {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    val errorText = checksError.orEmpty()
                                    // Translated messages already start with the heading; do not show it twice.
                                    if (!errorText.startsWith(strings.failedLoadCiChecks)) {
                                        Text(strings.failedLoadCiChecks, style = MaterialTheme.typography.titleMedium)
                                        Spacer(Modifier.height(6.dp))
                                    }
                                    Text(
                                        text = errorText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    if (checksRef != null) {
                                        Spacer(Modifier.height(12.dp))
                                        Button(onClick = { loadChecks(checksRef, forceRefresh = true) }) { Text(strings.retry) }
                                    }
                                }
                            }
                            ChecksViewState.EMPTY -> {
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
                                    Text(strings.issueNoChecks, style = MaterialTheme.typography.titleMedium)
                                    if (checksRef != null) {
                                        Spacer(Modifier.height(10.dp))
                                        FilledTonalButton(
                                            enabled = !checksLoading,
                                            onClick = { loadChecks(checksRef, forceRefresh = true) }
                                        ) { Text(strings.issueRefreshChecks) }
                                    }
                                }
                            }
                            ChecksViewState.LIST -> {
                                val shownRuns = runs.orEmpty()
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    item(key = "checks_header", contentType = "checks_header") {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (shownRuns.size == 1) strings.issueCheckRunCountOne(shownRuns.size) else strings.issueCheckRunCountOther(shownRuns.size),
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (checksRef != null) {
                                                IconButton(
                                                    enabled = !checksLoading,
                                                    onClick = { loadChecks(checksRef, forceRefresh = true) }
                                                ) {
                                                    Icon(Lucide.RefreshCw, contentDescription = strings.issueRefreshChecksDesc, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                    items(items = shownRuns, key = { it.id }, contentType = { "check_run" }) { cr ->
                                        val (icon, tint) = actionStatusVisual(cr.status, cr.conclusion)
                                        val actionsTarget = remember(cr.detailsUrl) { parseActionsRunUrl(cr.detailsUrl) }
                                        ElevatedCard(
                                            shape = MaterialTheme.shapes.large,
                                            colors = CardDefaults.elevatedCardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .then(
                                                    if (actionsTarget != null) {
                                                        Modifier.clickable {
                                                            openedLogJob = null
                                                            jobLogsText = null
                                                            openedWorkflowRun = null
                                                            openedWorkflowJobs = null
                                                            loadActionsOverlay(actionsTarget, forceRefresh = false)
                                                        }
                                                    } else {
                                                        Modifier
                                                    }
                                                )
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
                                                        label = strings.formatCheckConclusionLabel(cr.conclusion ?: cr.status),
                                                        containerColor = tint.copy(alpha = 0.16f),
                                                        contentColor = tint
                                                    )
                                                    if (cr.detailsUrl.isNotBlank()) {
                                                        IconButton(
                                                            onClick = {
                                                                if (actionsTarget != null) {
                                                                    openedLogJob = null
                                                                    jobLogsText = null
                                                                    openedWorkflowRun = null
                                                                    openedWorkflowJobs = null
                                                                    loadActionsOverlay(actionsTarget, forceRefresh = false)
                                                                } else {
                                                                    openUrlInBrowser(cr.detailsUrl)
                                                                }
                                                            }
                                                        ) {
                                                            Icon(
                                                                if (actionsTarget != null) Lucide.CircleDot else Lucide.ExternalLink,
                                                                contentDescription = if (actionsTarget != null) {
                                                                    strings.issueCheckOpenRunInApp
                                                                } else {
                                                                    strings.issueCheckOpenInBrowser
                                                                },
                                                                modifier = Modifier.size(16.dp)
                                                            )
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
                        val expandedFiles = remember(targetKey, files) {
                            mutableStateMapOf<String, Boolean>().apply {
                                val defaultOpen = files.size <= PULL_FILES_COMPACT_MAX_COUNT
                                files.forEach { put(it.filename, defaultOpen) }
                            }
                        }
                        var fileFilterQuery by remember(targetKey) { mutableStateOf("") }
                        val showFileFilter = files.size > PULL_FILES_COMPACT_MAX_COUNT
                        // If a refresh shrinks the PR below the threshold the field disappears, so drop its query too.
                        val activeFileFilter = if (showFileFilter) fileFilterQuery else ""
                        val visibleFiles = remember(files, activeFileFilter) {
                            filterPullFiles(files, activeFileFilter)
                        }
                        val focusManager = LocalFocusManager.current
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (showFileFilter) {
                                item(key = "files_filter", contentType = "files_filter") {
                                    OutlinedTextField(
                                        value = fileFilterQuery,
                                        onValueChange = { fileFilterQuery = it },
                                        placeholder = { Text(strings.filterPullFilesPlaceholder) },
                                        leadingIcon = {
                                            Icon(Lucide.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                        },
                                        suffix = {
                                            Text(
                                                text = "${visibleFiles.size}/${files.size}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        trailingIcon = if (fileFilterQuery.isNotEmpty()) {
                                            {
                                                IconButton(
                                                    onClick = {
                                                        fileFilterQuery = ""
                                                        focusManager.clearFocus()
                                                    }
                                                ) {
                                                    Icon(Lucide.X, contentDescription = strings.clear, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        } else null,
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp)
                                    )
                                }
                            }
                            if (filesLoading) {
                                item(key = "files_loading", contentType = "loading") {
                                    MaxxingWavyProgress()
                                }
                            }
                            if (!filesLoading && files.isEmpty()) {
                                item(key = "files_empty", contentType = "empty") {
                                    Text(
                                        text = strings.noFileDiffs,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (!filesLoading && files.isNotEmpty() && visibleFiles.isEmpty()) {
                                item(key = "files_no_match", contentType = "empty") {
                                    Text(
                                        text = strings.noMatchingPullFiles,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            visibleFiles.forEach { file ->
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

                var pendingMergeConfirmMethod by remember(targetKey) { mutableStateOf<MergeMethod?>(null) }
                var pendingStateConfirmOpen by remember(targetKey) { mutableStateOf<Boolean?>(null) }

                val primaryAction = prPrimaryAction(
                    isPr = isPr,
                    state = item.state,
                    isDraft = item.isDraft,
                    canMerge = item.viewerCanMerge,
                    canUpdate = item.viewerCanUpdate
                )

                // MD3E Floating Action Toolbar at bottom
                IssuePullFloatingToolbar(
                    isPr = isPr,
                    state = item.state,
                    primaryAction = primaryAction,
                    canCloseOrReopen = item.viewerCanCloseOrReopen,
                    selectedMergeMethod = selectedMergeMethod,
                    actionBusy = actionBusy,
                    onCommentClick = { showCommentDialog = true },
                    onReviewClick = { showReviewDialog = true },
                    onSelectMergeMethod = { selectedMergeMethod = it },
                    onMergeClick = { pendingMergeConfirmMethod = it },
                    onReadyForReviewClick = toggleDraft,
                    onStateToggleClick = { pendingStateConfirmOpen = it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 16.dp)
                )

                pendingMergeConfirmMethod?.let { method ->
                    val mergeBlockReason = resolvePullMergeBlockReason(item, checkRuns, strings)
                    AlertDialog(
                        onDismissRequest = { pendingMergeConfirmMethod = null },
                        icon = { Icon(Lucide.GitMerge, contentDescription = null) },
                        title = { Text(strings.issueMergeConfirmTitle(target.number)) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    "${strings.mergeConfirmQuestion(item.baseRefName, method)}\n\n${item.title}"
                                )
                                if (mergeBlockReason != null) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        shape = MaterialTheme.shapes.medium,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = mergeBlockReason,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                enabled = !actionBusy && mergeBlockReason == null,
                                onClick = {
                                    pendingMergeConfirmMethod = null
                                    scope.launch {
                                        actionBusy = true
                                        container.repository.mergePullRequest(
                                            target.owner, target.repo, target.number, method
                                        ).onSuccess {
                                            onMessage(strings.issueMergedMessage(target.number, method))
                                            loadDetail(forceRefresh = true)
                                        }.onFailure { err ->
                                            onMessage(formatPullMergeFailureMessage(err, strings))
                                        }
                                        actionBusy = false
                                    }
                                }
                            ) {
                                Text(strings.issueMergeAction)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingMergeConfirmMethod = null }) {
                                Text(strings.cancel)
                            }
                        }
                    )
                }

                pendingStateConfirmOpen?.let { willOpen ->
                    AlertDialog(
                        onDismissRequest = { pendingStateConfirmOpen = null },
                        icon = {
                            Icon(
                                if (willOpen) Lucide.CircleDot else Lucide.X,
                                contentDescription = null
                            )
                        },
                        title = { Text(if (willOpen) strings.issueReopenConfirmTitle(target.number) else strings.issueCloseConfirmTitle(target.number)) },
                        text = {
                            Text("${strings.stateChangeConfirmQuestion(willOpen, isPr)}\n\n${item.title}")
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
                                                if (willOpen) strings.issueReopenedMessage(target.number)
                                                else strings.issueClosedMessage(target.number)
                                            )
                                            loadDetail(forceRefresh = true)
                                        }.onFailure {
                                            onMessage(strings.errorMessage(it, strings.issueStateUpdateFailed))
                                        }
                                        actionBusy = false
                                    }
                                }
                            ) {
                                Text(if (willOpen) strings.issueReopenAction else strings.issueCloseAction)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingStateConfirmOpen = null }) {
                                Text(strings.cancel)
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
            title = strings.issueCommentTitle(target.number),
            subtitle = "${target.owner}/${target.repo}",
            saveLabel = strings.issueCommentPost,
            canSave = commentDraft.isNotBlank(),
            isSubmitting = actionBusy,
            bodyValue = commentDraft,
            onBodyChange = { commentDraft = it },
            bodyLabel = strings.issueCommentLabel,
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
                            onMessage(strings.issueCommentPosted)
                            loadDetail(forceRefresh = true)
                        }
                        .onFailure { onMessage(strings.errorMessage(it, strings.issueCommentPostFailed)) }
                    actionBusy = false
                }
            }
        )
    }

    if (showReviewDialog) {
        MaxxingFullScreenEditorScaffold(
            title = strings.issueSubmitReviewTitle,
            subtitle = "#${target.number} · ${strings.reviewEventLabel(reviewEvent)}",
            saveLabel = strings.issueSubmitAction,
            canSave = reviewEvent == ReviewEvent.APPROVE || reviewBody.isNotBlank(),
            isSubmitting = actionBusy,
            bodyValue = reviewBody,
            onBodyChange = { reviewBody = it },
            bodyLabel = strings.issueReviewSummaryLabel,
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
                            onMessage(strings.issueReviewSubmitted(strings.reviewEventLabel(event)))
                            loadDetail(forceRefresh = true)
                        }
                        .onFailure { onMessage(strings.errorMessage(it, strings.issueReviewFailed)) }
                    actionBusy = false
                }
            },
            headerContent = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.issueDecisionLabel, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    MaxxingDropdownSelector(
                        options = ReviewEvent.entries,
                        selected = reviewEvent,
                        onSelect = { reviewEvent = it },
                        labelProvider = { strings.reviewEventLabel(it) }
                    )
                }
            }
        )
    }

    if (showLabelsDialog) {
        RepoLabelsPickerDialog(
            title = strings.issueManageLabelsTitle,
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
                        onMessage(strings.issueLabelsUpdated)
                        loadDetail(forceRefresh = true)
                    }.onFailure {
                        onMessage(strings.errorMessage(it, strings.failedUpdateLabels))
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
                            onMessage(strings.createdLabelMessage(created.name))
                        }
                        .onFailure { onMessage(strings.errorMessage(it, strings.failedCreateLabel)) }
                    actionBusy = false
                }
            },
            onDeleteLabel = { label ->
                scope.launch {
                    actionBusy = true
                    container.repository.deleteRepoLabel(target.owner, target.repo, label.name)
                        .onSuccess {
                            availableLabels = availableLabels.filterNot { it.name == label.name }
                            selectedLabelNames = selectedLabelNames - label.name
                            onMessage(strings.labelDeletedMessage(label.name))
                        }
                        .onFailure { onMessage(strings.errorMessage(it, strings.failedDeleteLabel)) }
                    actionBusy = false
                }
            }
        )
    }

    if (showAssigneesDialog) {
        AlertDialog(
            onDismissRequest = { showAssigneesDialog = false },
            title = { Text(strings.issueManageAssigneesTitle) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().height(250.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (availableAssignees.isEmpty()) {
                        Text(strings.issueNoAssignees, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                onMessage(strings.issueAssigneesUpdated)
                                loadDetail(forceRefresh = true)
                            }.onFailure {
                                onMessage(strings.errorMessage(it, strings.failedUpdateAssignees))
                            }
                            actionBusy = false
                        }
                    }
                ) {
                    Text(strings.save)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAssigneesDialog = false }) { Text(strings.cancel) }
            }
        )
    }

    if (showMilestoneDialog) {
        AlertDialog(
            onDismissRequest = { showMilestoneDialog = false },
            title = { Text(strings.issueSelectMilestoneTitle) },
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
                        Text(strings.issueNoMilestone, style = MaterialTheme.typography.bodyMedium)
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
                                    Text(strings.milestoneDue(due), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                onMessage(if (chosen == null) strings.issueMilestoneCleared else strings.issueMilestoneUpdated)
                                loadDetail(forceRefresh = true)
                            }.onFailure {
                                onMessage(strings.errorMessage(it, strings.failedUpdateMilestone))
                            }
                            actionBusy = false
                        }
                    }
                ) {
                    Text(strings.save)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMilestoneDialog = false }) { Text(strings.cancel) }
            }
        )
    }

    pendingDiffCommentTarget?.let { (filePath, lineNum, side) ->
        val commitSha = detail?.headRefOid
        MaxxingFullScreenEditorScaffold(
            title = if (lineNum != null) strings.reviewCommentLineTitle(filePath.substringAfterLast('/'), lineNum)
            else strings.fileCommentTitle(filePath.substringAfterLast('/')),
            subtitle = filePath,
            saveLabel = strings.issueCommentPost,
            canSave = diffCommentBody.trim().isNotBlank() && !commitSha.isNullOrBlank(),
            isSubmitting = actionBusy,
            bodyValue = diffCommentBody,
            onBodyChange = { diffCommentBody = it },
            bodyLabel = strings.diffCommentBodyLabel,
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
                        onMessage(strings.addedInlineDiffComment)
                        loadDetail(forceRefresh = true)
                    }.onFailure {
                        onMessage(strings.errorMessage(it, strings.failedPostDiffComment))
                    }
                    actionBusy = false
                }
            }
        )
    }

    editingComment?.let { c ->
        MaxxingFullScreenEditorScaffold(
            title = strings.issueEditCommentTitle,
            subtitle = strings.commentOnIssueSubtitle(c.author.login, target.number),
            saveLabel = strings.save,
            canSave = editingCommentBody.trim().isNotBlank(),
            isSubmitting = actionBusy,
            bodyValue = editingCommentBody,
            onBodyChange = { editingCommentBody = it },
            bodyLabel = strings.editCommentBodyLabel,
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
                        onMessage(strings.issueCommentUpdated)
                        loadDetail(forceRefresh = true)
                    }.onFailure {
                        onMessage(strings.errorMessage(it, strings.failedUpdateComment))
                    }
                    actionBusy = false
                }
            }
        )
    }

    deletingComment?.let { c ->
        AlertDialog(
            onDismissRequest = { deletingComment = null },
            title = { Text(strings.issueDeleteCommentTitle) },
            text = { Text(strings.issueDeleteCommentConfirm(c.author.login)) },
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
                                onMessage(strings.issueCommentDeleted)
                                loadDetail(forceRefresh = true)
                            }.onFailure {
                                onMessage(strings.errorMessage(it, strings.failedDeleteComment))
                            }
                            actionBusy = false
                        }
                    }
                ) { Text(strings.deleteAction) }
            },
            dismissButton = {
                TextButton(onClick = { deletingComment = null }) { Text(strings.cancel) }
            }
        )
    }

    if (showReviewersDialog) {
        AlertDialog(
            onDismissRequest = { showReviewersDialog = false },
            title = { Text(strings.issueRequestReviewersTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = customReviewerLogin,
                            onValueChange = { customReviewerLogin = it },
                            label = { Text(strings.issueUsernameLabel) },
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
                            Icon(Lucide.Plus, contentDescription = strings.addReviewerDesc)
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
                                onMessage(strings.requestedReviewersMessage(reviewers.joinToString(", ")))
                                loadDetail(forceRefresh = true)
                            }.onFailure {
                                onMessage(strings.errorMessage(it, strings.failedRequestReviewers))
                            }
                            actionBusy = false
                        }
                    }
                ) { Text(strings.issueRequestAction) }
            },
            dismissButton = {
                TextButton(onClick = { showReviewersDialog = false }) { Text(strings.cancel) }
            }
        )
    }

    MaxxingPredictiveOverlayHost(
        target = openedActionsTarget,
        onBack = {
            if (openedLogJob != null) {
                openedLogJob = null
                jobLogsText = null
            } else {
                openedActionsTarget = null
                openedWorkflowRun = null
                openedWorkflowJobs = null
                workflowRunError = null
            }
        },
        contentKey = { "${it.owner}/${it.repo}/${it.runId}" }
    ) { actionsTarget ->
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxSize().statusBarsPadding()
        ) {
            val activeLogJob = openedLogJob
            val activeRun = openedWorkflowRun
            when {
                activeLogJob != null -> {
                    WorkflowLogViewer(
                        job = activeLogJob,
                        logs = jobLogsText,
                        isLoading = jobLogsLoading,
                        onBack = {
                            openedLogJob = null
                            jobLogsText = null
                        }
                    )
                }
                activeRun != null -> {
                    WorkflowRunDetailView(
                        run = activeRun,
                        jobs = openedWorkflowJobs,
                        isLoading = workflowRunLoading,
                        actionBusy = workflowActionBusy,
                        canManageRuns = detail?.viewerCanMerge == true,
                        onBack = {
                            openedActionsTarget = null
                            openedWorkflowRun = null
                            openedWorkflowJobs = null
                            workflowRunError = null
                        },
                        onRefresh = { loadActionsOverlay(actionsTarget, forceRefresh = true) },
                        onRerun = { failedOnly ->
                            scope.launch {
                                workflowActionBusy = true
                                container.repository.rerunWorkflow(
                                    actionsTarget.owner,
                                    actionsTarget.repo,
                                    activeRun.id,
                                    failedOnly
                                ).onSuccess {
                                    onMessage(if (failedOnly) strings.notifRerunningFailed else strings.notifRerunningWorkflow)
                                    loadActionsOverlay(actionsTarget, forceRefresh = true)
                                }.onFailure { err ->
                                    onMessage(strings.errorMessage(err, strings.actionsRerunFailedError))
                                }
                                workflowActionBusy = false
                            }
                        },
                        onCancel = {
                            scope.launch {
                                workflowActionBusy = true
                                container.repository.cancelWorkflowRun(
                                    actionsTarget.owner,
                                    actionsTarget.repo,
                                    activeRun.id
                                ).onSuccess {
                                    onMessage(strings.notifCancelRequested)
                                    loadActionsOverlay(actionsTarget, forceRefresh = true)
                                }.onFailure { err ->
                                    onMessage(strings.errorMessage(err, strings.actionsCancelFailedError))
                                }
                                workflowActionBusy = false
                            }
                        },
                        onOpenLogs = { job -> openWorkflowJobLogs(actionsTarget, job) }
                    )
                }
                workflowRunLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        MaxxingLoadingState(message = strings.issueCheckLoadingRun)
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = workflowRunError ?: strings.issueCheckFailedLoadRun,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    openedActionsTarget = null
                                    workflowRunError = null
                                }
                            ) {
                                Text(strings.back)
                            }
                            FilledTonalButton(
                                onClick = { loadActionsOverlay(actionsTarget, forceRefresh = true) }
                            ) {
                                Text(strings.retry)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun IssuePullFloatingToolbar(
    isPr: Boolean,
    state: IssueOrPullState,
    primaryAction: PrPrimaryAction,
    canCloseOrReopen: Boolean,
    selectedMergeMethod: MergeMethod,
    actionBusy: Boolean,
    onCommentClick: () -> Unit,
    onReviewClick: () -> Unit,
    onSelectMergeMethod: (MergeMethod) -> Unit,
    onMergeClick: (MergeMethod) -> Unit,
    onReadyForReviewClick: () -> Unit,
    onStateToggleClick: (willOpen: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState()
) {
    val strings = appStrings()
    val toolbarButtonColors = MaxxingFloatingToolbarDefaults.vibrantButtonColors()
    val pillButtonPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
    val standaloneButtonPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    val showsPrActions = isPr && state == IssueOrPullState.OPEN
    val showsCloseOrReopen = state != IssueOrPullState.MERGED && canCloseOrReopen
    // IconButtons carry 4dp of invisible touch-target inset on each side, whereas standard Buttons do not.
    // Keep visual margins symmetric with the 12dp top/bottom padding inside the 64dp toolbar pill.
    val endsWithIconButton = when {
        showsCloseOrReopen -> primaryAction != PrPrimaryAction.NONE
        showsPrActions -> false
        else -> true
    }
    val toolbarContentPadding = if (endsWithIconButton) {
        PaddingValues(horizontal = 8.dp)
    } else {
        PaddingValues(start = 8.dp, end = 12.dp)
    }

    MaxxingFloatingToolbar(
        expanded = true,
        vibrant = true,
        contentPadding = toolbarContentPadding,
        scrollState = scrollState,
        modifier = modifier
    ) {
        FilledTonalIconButton(
            onClick = onCommentClick,
            enabled = !actionBusy
        ) {
            Icon(Lucide.MessageSquarePlus, contentDescription = strings.reviewCommentLabel)
        }

        if (isPr && state == IssueOrPullState.OPEN) {
            // IconButton already adds 4dp touch-target padding around its 40dp circle.
            FilledTonalButton(
                onClick = onReviewClick,
                enabled = !actionBusy,
                contentPadding = pillButtonPadding
            ) {
                Icon(Lucide.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(strings.issueReviewAction, maxLines = 1, softWrap = false)
            }

            when (primaryAction) {
                PrPrimaryAction.MERGE -> {
                    Spacer(Modifier.width(4.dp))
                    val mergeOptions = remember(strings) {
                        MergeMethod.entries.map {
                            SplitActionOption(
                                value = it,
                                label = strings.mergeMethodLabel(it),
                                icon = Lucide.GitMerge,
                                buttonLabel = strings.mergeMethodShortLabel(it)
                            )
                        }
                    }
                    MaxxingSplitActionButton(
                        selectedOption = mergeOptions.first { it.value == selectedMergeMethod },
                        options = mergeOptions,
                        enabled = !actionBusy,
                        colors = toolbarButtonColors,
                        onSelectOption = onSelectMergeMethod,
                        onPrimaryClick = onMergeClick
                    )
                }
                PrPrimaryAction.MARK_READY -> {
                    Spacer(Modifier.width(4.dp))
                    Button(
                        onClick = onReadyForReviewClick,
                        enabled = !actionBusy,
                        colors = toolbarButtonColors,
                        contentPadding = pillButtonPadding
                    ) {
                        Icon(Lucide.GitPullRequest, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.issueReadyForReview, maxLines = 1, softWrap = false)
                    }
                }
                PrPrimaryAction.NONE -> Unit
            }
        }

        if (state != IssueOrPullState.MERGED && canCloseOrReopen) {
            val willOpen = state == IssueOrPullState.CLOSED
            if (primaryAction != PrPrimaryAction.NONE) {
                FilledTonalIconButton(
                    onClick = { onStateToggleClick(willOpen) },
                    enabled = !actionBusy
                ) {
                    Icon(
                        if (willOpen) Lucide.CircleDot else Lucide.X,
                        contentDescription = if (willOpen) strings.issueReopenAction else strings.issueCloseAction
                    )
                }
            } else {
                Spacer(Modifier.width(4.dp))
                FilledTonalButton(
                    onClick = { onStateToggleClick(willOpen) },
                    enabled = !actionBusy,
                    contentPadding = standaloneButtonPadding
                ) {
                    Icon(
                        if (willOpen) Lucide.CircleDot else Lucide.X,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (willOpen) strings.issueReopenAction else strings.issueCloseAction,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

internal enum class PrPrimaryAction { NONE, MERGE, MARK_READY }

/**
 * Decides the primary action shown in the detail toolbar. Like GitHub web, a draft PR
 * offers "Ready for review" instead of a disabled merge button.
 */
internal fun prPrimaryAction(
    isPr: Boolean,
    state: IssueOrPullState,
    isDraft: Boolean,
    canMerge: Boolean,
    canUpdate: Boolean
): PrPrimaryAction = when {
    !isPr || state != IssueOrPullState.OPEN -> PrPrimaryAction.NONE
    isDraft -> if (canUpdate) PrPrimaryAction.MARK_READY else PrPrimaryAction.NONE
    canMerge -> PrPrimaryAction.MERGE
    else -> PrPrimaryAction.NONE
}

@Composable
private fun DraftNoticeCard() {
    val strings = appStrings()
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Lucide.Pencil, contentDescription = null, modifier = Modifier.size(18.dp))
            Column {
                Text(
                    strings.issueDraftNoticeTitle,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    strings.issueDraftNoticeBody,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
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
    val strings = appStrings()
    val stateLabel = strings.issueStateBadgeLabel(item.state, item.isDraft)
    val (stateBg, stateFg) = when {
        item.isDraft -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
        item.state == IssueOrPullState.OPEN -> Color(0xFF16A34A) to Color.White
        item.state == IssueOrPullState.MERGED -> Color(0xFF7C3AED) to Color.White
        else -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
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
                        label = strings.formatCheckRollupLabel(rollup.state),
                        containerColor = checkColor.copy(alpha = 0.16f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                }
                item.reviewDecision?.let { decision ->
                    ExpressiveStatusPill(
                        label = strings.formatReviewStateLabel(decision),
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
                            label = { Text(strings.issueLabelsCount(item.labels.size)) }
                        )
                        AssistChip(
                            onClick = onEditAssignees,
                            leadingIcon = { Icon(Lucide.Users, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            label = { Text(strings.issueAssigneesCount(item.assignees.size)) }
                        )
                        AssistChip(
                            onClick = onEditMilestone,
                            leadingIcon = { Icon(Lucide.Flag, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            label = { Text(item.milestone?.title ?: strings.issueMilestoneFallback) }
                        )
                    }
                    if (canAccessPrMeta) {
                        AssistChip(
                            onClick = onRequestReviewers,
                            leadingIcon = { Icon(Lucide.Users, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            label = { Text(strings.issueReviewersCount(item.requestedReviewers.size)) }
                        )
                        if (item.viewerCanUpdate) {
                            AssistChip(
                                onClick = onToggleDraft,
                                leadingIcon = { Icon(Lucide.Pencil, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                label = { Text(if (item.isDraft) strings.issueReadyForReview else strings.issueConvertToDraft) }
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
                        strings.reviewersHeader,
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
    val strings = appStrings()
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
                    Icon(Lucide.Plus, contentDescription = strings.addReactionDesc, modifier = Modifier.size(12.dp))
                    Text(strings.issueReactAction, style = MaterialTheme.typography.labelSmall)
                }
            }
            DropdownMenu(expanded = showPicker, onDismissRequest = { showPicker = false }) {
                REACTION_EMOJIS.forEach { (contentKey, emoji) ->
                    val alreadyReacted = reactionByContent[contentKey]?.viewerHasReacted == true
                    DropdownMenuItem(
                        text = { Text("$emoji  ${strings.formatReactionLabel(contentKey)}") },
                        trailingIcon = if (alreadyReacted) {
                            { Icon(Lucide.Check, contentDescription = strings.reactedDesc, modifier = Modifier.size(14.dp)) }
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
    val strings = appStrings()
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
                                label = strings.formatAuthorAssociationLabel(association),
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
                                Icon(Lucide.Pencil, contentDescription = strings.editCommentDesc, modifier = Modifier.size(14.dp))
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
                                    contentDescription = strings.deleteCommentDesc,
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
                            label = strings.formatReviewStateLabel(event.state),
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
                    text = "${strings.formatTimelineLine(actor?.login, event.actionLabel)} • ${event.createdAt.take(10)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
