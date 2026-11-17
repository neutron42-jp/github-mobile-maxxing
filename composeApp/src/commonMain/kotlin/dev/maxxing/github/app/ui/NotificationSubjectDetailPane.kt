package dev.maxxing.github.app.ui

import dev.maxxing.github.ui.i18n.appStrings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Activity
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.FileCode
import com.composables.icons.lucide.GitCommitHorizontal
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Tag
import dev.maxxing.github.app.AppContainer
import dev.maxxing.github.auth.openUrlInBrowser
import dev.maxxing.github.model.CheckRunSummary
import dev.maxxing.github.model.CommitDetail
import dev.maxxing.github.model.NotificationSubjectType
import dev.maxxing.github.model.NotificationThread
import dev.maxxing.github.model.ReleaseSummary
import dev.maxxing.github.model.WorkflowJobSummary
import dev.maxxing.github.model.WorkflowRunSummary
import dev.maxxing.github.ui.components.ExpressiveStatusPill
import dev.maxxing.github.ui.components.GitHubMarkdown
import dev.maxxing.github.ui.components.MaxxingLoadingState
import dev.maxxing.github.ui.components.MaxxingWavyProgress
import kotlinx.coroutines.launch

@Composable
internal fun NotificationSubjectDetailPane(
    thread: NotificationThread,
    container: AppContainer,
    showBackButton: Boolean,
    onBack: () -> Unit,
    onMessage: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val strings = appStrings()

    var isLoading by remember(thread.id) { mutableStateOf(false) }
    var errorMsg by remember(thread.id) { mutableStateOf<String?>(null) }
    var checkRuns by remember(thread.id) { mutableStateOf<List<CheckRunSummary>>(emptyList()) }
    var workflowRuns by remember(thread.id) {
        mutableStateOf(
            if (thread.subjectType == NotificationSubjectType.CHECK_SUITE) {
                container.repository.peekWorkflowRuns(thread.repoOwner, thread.repoName).orEmpty()
            } else emptyList()
        )
    }
    var openedRun by remember(thread.id) { mutableStateOf<WorkflowRunSummary?>(null) }
    var openedRunJobs by remember(thread.id) { mutableStateOf<List<WorkflowJobSummary>?>(null) }
    var jobsLoading by remember(thread.id) { mutableStateOf(false) }
    var runActionBusy by remember(thread.id) { mutableStateOf(false) }
    var openedLogJob by remember(thread.id) { mutableStateOf<WorkflowJobSummary?>(null) }
    var openedJobLogs by remember(thread.id) { mutableStateOf<String?>(null) }
    var logsLoading by remember(thread.id) { mutableStateOf(false) }

    var commitDetail by remember(thread.id) { mutableStateOf<CommitDetail?>(null) }
    var releases by remember(thread.id) { mutableStateOf<List<ReleaseSummary>>(emptyList()) }

    fun loadDetails(forceRefresh: Boolean = false) {
        scope.launch {
            isLoading = true
            errorMsg = null
            when (thread.subjectType) {
                NotificationSubjectType.CHECK_SUITE -> {
                    val suiteId = thread.checkSuiteId
                    if (suiteId != null) {
                        container.repository.getCheckRunsForCheckSuite(
                            thread.repoOwner,
                            thread.repoName,
                            suiteId,
                            forceRefresh = forceRefresh
                        ).onSuccess { checkRuns = it }
                    }
                    container.repository.getWorkflowRuns(
                        thread.repoOwner,
                        thread.repoName,
                        forceRefresh = forceRefresh
                    ).onSuccess {
                        workflowRuns = it
                    }.onFailure { err ->
                        if (checkRuns.isEmpty() && workflowRuns.isEmpty()) {
                            errorMsg = strings.errorMessage(err, strings.failedLoadWorkflowRuns)
                        }
                    }
                }
                NotificationSubjectType.COMMIT -> {
                    val sha = thread.commitSha
                    if (sha != null) {
                        container.repository.getCommitDetail(
                            thread.repoOwner,
                            thread.repoName,
                            sha,
                            forceRefresh = forceRefresh
                        ).onSuccess { commitDetail = it }
                            .onFailure { errorMsg = strings.errorMessage(it, strings.failedLoadCommit) }
                    }
                }
                NotificationSubjectType.RELEASE -> {
                    container.repository.getReleases(
                        thread.repoOwner,
                        thread.repoName,
                        forceRefresh = forceRefresh
                    ).onSuccess { releases = it }
                        .onFailure { errorMsg = strings.errorMessage(it, strings.failedLoadReleases) }
                }
                else -> Unit
            }
            isLoading = false
        }
    }

    fun openRun(run: WorkflowRunSummary, forceRefresh: Boolean = false) {
        openedRun = run
        openedLogJob = null
        openedJobLogs = null
        scope.launch {
            jobsLoading = true
            container.repository.getWorkflowRunJobs(
                thread.repoOwner,
                thread.repoName,
                run.id,
                forceRefresh = forceRefresh
            ).onSuccess { openedRunJobs = it }
                .onFailure { onMessage(strings.errorMessage(it, strings.failedLoadJobs)) }
            jobsLoading = false
        }
    }

    fun openJobLogs(job: WorkflowJobSummary) {
        openedLogJob = job
        openedJobLogs = null
        scope.launch {
            logsLoading = true
            container.repository.getJobLogs(thread.repoOwner, thread.repoName, job.id)
                .onSuccess { openedJobLogs = it }
                .onFailure { onMessage(strings.errorMessage(it, strings.failedLoadLogs)) }
            logsLoading = false
        }
    }

    LaunchedEffect(thread.id) {
        loadDetails(forceRefresh = false)
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showBackButton) {
                IconButton(
                    onClick = {
                        when {
                            openedLogJob != null -> openedLogJob = null
                            openedRun != null -> openedRun = null
                            else -> onBack()
                        }
                    }
                ) {
                    Icon(Lucide.ArrowLeft, contentDescription = strings.back)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = thread.repoFullName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = strings.notificationSubjectLabel(thread.subjectType),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { openUrlInBrowser(thread.htmlFallbackUrl) }) {
                Icon(Lucide.ExternalLink, contentDescription = strings.openInBrowser, modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = {
                    val run = openedRun
                    if (run != null) openRun(run, forceRefresh = true) else loadDetails(forceRefresh = true)
                }
            ) {
                Icon(Lucide.RefreshCw, contentDescription = strings.refresh, modifier = Modifier.size(18.dp))
            }
        }

        if (openedLogJob != null) {
            WorkflowLogViewer(
                job = openedLogJob!!,
                logs = openedJobLogs,
                isLoading = logsLoading,
                onBack = { openedLogJob = null }
            )
            return@Column
        }

        if (openedRun != null) {
            val run = openedRun!!
            WorkflowRunDetailView(
                run = run,
                jobs = openedRunJobs,
                isLoading = jobsLoading,
                actionBusy = runActionBusy,
                canManageRuns = true,
                onBack = { openedRun = null },
                onRefresh = { openRun(run, forceRefresh = true) },
                onRerun = { failedOnly ->
                    scope.launch {
                        runActionBusy = true
                        container.repository.rerunWorkflow(thread.repoOwner, thread.repoName, run.id, failedOnly)
                            .onSuccess {
                                onMessage(if (failedOnly) strings.notifRerunningFailed else strings.notifRerunningWorkflow)
                                openRun(run, forceRefresh = true)
                            }
                            .onFailure { onMessage(strings.errorMessage(it, strings.failedRerunWorkflow)) }
                        runActionBusy = false
                    }
                },
                onCancel = {
                    scope.launch {
                        runActionBusy = true
                        container.repository.cancelWorkflowRun(thread.repoOwner, thread.repoName, run.id)
                            .onSuccess {
                                onMessage(strings.notifCancelRequested)
                                openRun(run, forceRefresh = true)
                            }
                            .onFailure { onMessage(strings.errorMessage(it, strings.failedCancelRun)) }
                        runActionBusy = false
                    }
                },
                onOpenLogs = { job -> openJobLogs(job) }
            )
            return@Column
        }

        if (isLoading && checkRuns.isEmpty() && workflowRuns.isEmpty() && commitDetail == null && releases.isEmpty()) {
            MaxxingWavyProgress()
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "thread_header") {
                ElevatedCard(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = subjectTypeIcon(thread.subjectType),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            ExpressiveStatusPill(
                                label = strings.notificationSubjectLabel(thread.subjectType),
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            ExpressiveStatusPill(
                                label = strings.notificationReasonLabel(thread.reason),
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Text(
                            text = thread.subjectTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (thread.updatedAt.isNotBlank()) {
                            Text(
                                text = strings.notifUpdatedAt(thread.updatedAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(onClick = { openUrlInBrowser(thread.htmlFallbackUrl) }) {
                            Icon(Lucide.ExternalLink, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.notifOpenOnGitHub)
                        }
                    }
                }
            }

            if (isLoading) {
                item(key = "loading_inline") {
                    MaxxingLoadingState(message = strings.notifLoadingDetails(strings.notificationSubjectLabel(thread.subjectType)))
                }
            } else if (errorMsg != null) {
                item(key = "error_inline") {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(errorMsg!!, color = MaterialTheme.colorScheme.onErrorContainer)
                            OutlinedButton(onClick = { loadDetails(forceRefresh = true) }) {
                                Text(strings.retry)
                            }
                        }
                    }
                }
            }

            when (thread.subjectType) {
                NotificationSubjectType.CHECK_SUITE -> {
                    if (checkRuns.isNotEmpty()) {
                        item(key = "check_runs_title") {
                            Text(strings.notifCheckRunsTitle, style = MaterialTheme.typography.titleMedium)
                        }
                        items(items = checkRuns, key = { "cr_${it.id}" }) { cr ->
                            val (icon, tint) = actionStatusVisual(cr.status, cr.conclusion)
                            ElevatedCard(
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !cr.detailsUrl.isNullOrBlank()) {
                                        cr.detailsUrl?.let { openUrlInBrowser(it) }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(icon, contentDescription = strings.formatCheckStatusText(cr.conclusion ?: cr.status), tint = tint, modifier = Modifier.size(18.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(cr.name, style = MaterialTheme.typography.titleSmall)
                                        val desc = listOfNotNull(cr.appName, strings.formatCheckStatusText(cr.conclusion ?: cr.status)).joinToString(" • ")
                                        if (desc.isNotBlank()) {
                                            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    if (!cr.detailsUrl.isNullOrBlank()) {
                                        Icon(Lucide.ExternalLink, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    if (workflowRuns.isNotEmpty()) {
                        item(key = "wf_runs_title") {
                            Text(strings.notifWorkflowRunsTitle, style = MaterialTheme.typography.titleMedium)
                        }
                        items(items = workflowRuns.take(15), key = { "wf_${it.id}" }) { run ->
                            WorkflowRunSummaryCard(run = run, onClick = { openRun(run) })
                        }
                    }
                }

                NotificationSubjectType.COMMIT -> {
                    commitDetail?.let { detail ->
                        item(key = "commit_summary") {
                            ElevatedCard(
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(detail.summary.headline, style = MaterialTheme.typography.titleSmall)
                                    if (detail.summary.body.isNotBlank()) {
                                        Text(
                                            text = detail.summary.body,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "${detail.summary.authorName} • ${detail.summary.shortSha} • +${detail.additions} / -${detail.deletions}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        items(items = detail.files, key = { it.filename }) { file ->
                            ElevatedCard(
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Lucide.FileCode, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text(
                                        text = file.filename,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "+${file.additions} -${file.deletions}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = Color(0xFF16A34A)
                                    )
                                }
                            }
                        }
                    }
                }

                NotificationSubjectType.RELEASE -> {
                    items(items = releases.take(5), key = { it.id }) { rel ->
                        ElevatedCard(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ExpressiveStatusPill(
                                        label = rel.tagName,
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(rel.name.ifBlank { rel.tagName }, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                }
                                if (rel.body.isNotBlank()) {
                                    GitHubMarkdown(content = rel.body)
                                }
                            }
                        }
                    }
                }

                else -> Unit
            }
        }
    }
}

private fun subjectTypeIcon(type: NotificationSubjectType) = when (type) {
    NotificationSubjectType.CHECK_SUITE -> Lucide.Activity
    NotificationSubjectType.COMMIT -> Lucide.GitCommitHorizontal
    NotificationSubjectType.RELEASE -> Lucide.Tag
    NotificationSubjectType.DISCUSSION -> Lucide.MessageSquare
    else -> Lucide.Activity
}
