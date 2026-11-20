package dev.maxxing.github.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ArrowRight
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.FileCode
import com.composables.icons.lucide.GitMerge
import com.composables.icons.lucide.ListChecks
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.MessageSquarePlus
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.Paperclip
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Send
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.X
import dev.maxxing.github.app.AppContainer
import dev.maxxing.github.app.IssuePullTarget
import dev.maxxing.github.app.attachments.EditorAttachmentController
import dev.maxxing.github.model.CheckRunSummary
import dev.maxxing.github.model.DiffHunk
import dev.maxxing.github.model.DiffLine
import dev.maxxing.github.model.DiffLineKind
import dev.maxxing.github.model.FileChangeStatus
import dev.maxxing.github.model.FileViewedState
import dev.maxxing.github.model.IssueOrPullDetail
import dev.maxxing.github.model.IssueOrPullState
import dev.maxxing.github.model.MergeMethod
import dev.maxxing.github.model.PullFileChange
import dev.maxxing.github.model.PullReviewComment
import dev.maxxing.github.model.PullReviewSyncState
import dev.maxxing.github.model.ReviewEvent
import dev.maxxing.github.ui.components.DiffLineRow
import dev.maxxing.github.ui.components.ExpressiveStatusPill
import dev.maxxing.github.ui.components.GitHubMarkdown
import dev.maxxing.github.ui.components.MaxxingConnectedButtonGroup
import dev.maxxing.github.ui.components.MaxxingFloatingToolbar
import dev.maxxing.github.ui.components.MaxxingFloatingToolbarDefaults
import dev.maxxing.github.ui.components.MaxxingPredictiveBackContainer
import dev.maxxing.github.ui.components.MaxxingSplitActionButton
import dev.maxxing.github.ui.components.MaxxingWavyProgress
import dev.maxxing.github.ui.components.SplitActionOption
import dev.maxxing.github.ui.components.UserAvatar
import dev.maxxing.github.ui.components.appendAttachmentMarkdown
import dev.maxxing.github.ui.components.collectWidestDiffLineCandidates
import dev.maxxing.github.ui.components.computeDiffMaxLineDigits
import dev.maxxing.github.ui.components.computeHunkWordHighlights
import dev.maxxing.github.ui.i18n.appStrings
import dev.maxxing.github.ui.theme.GraphiteContrast
import dev.maxxing.github.ui.theme.LocalGraphitePalette
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun PullReviewModeScreen(
    target: IssuePullTarget,
    detail: IssueOrPullDetail,
    files: List<PullFileChange>,
    syncState: PullReviewSyncState?,
    checkRuns: List<CheckRunSummary>?,
    container: AppContainer,
    attachmentController: EditorAttachmentController,
    selectedMergeMethod: MergeMethod,
    onSelectMergeMethod: (MergeMethod) -> Unit,
    onSyncStateUpdated: (PullReviewSyncState) -> Unit,
    onReloadDetail: () -> Unit,
    onMergeClick: (MergeMethod) -> Unit,
    onMessage: (String) -> Unit,
    onDismiss: () -> Unit,
    initialFilename: String? = null,
    allowedMergeMethods: List<MergeMethod> = MergeMethod.entries
) {
    val strings = appStrings()
    val graphitePalette = LocalGraphitePalette.current
    val scope = rememberCoroutineScope()
    val entries = remember(files) { organizeFilesForReview(files) }
    val initialIndex = remember(entries, initialFilename, syncState) {
        if (!initialFilename.isNullOrBlank()) {
            entries.indexOfFirst { it.file.filename == initialFilename }.takeIf { it >= 0 } ?: 0
        } else {
            val states = syncState?.viewedStates.orEmpty()
            findNextUnviewedFileIndex(entries, states, -1) ?: 0
        }
    }
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0)),
        pageCount = { entries.size.coerceAtLeast(1) }
    )

    var localSyncState by remember(syncState) { mutableStateOf(syncState ?: PullReviewSyncState()) }
    LaunchedEffect(syncState) {
        if (syncState != null) localSyncState = syncState
    }

    var wrapLines by remember { mutableStateOf(true) }
    var syncBusy by remember { mutableStateOf(false) }
    var actionBusy by remember { mutableStateOf(false) }
    val manuallyExpandedCollapsed = remember(target) { mutableStateMapOf<String, Boolean>() }

    var showFilesSheet by remember { mutableStateOf(false) }
    var showSubmitSheet by remember { mutableStateOf(false) }
    var showDiscardPendingConfirm by remember { mutableStateOf(false) }
    var completionBannerDismissed by remember { mutableStateOf(false) }

    var composerState by remember { mutableStateOf(ReviewCommentComposerState()) }

    var reviewEvent by remember { mutableStateOf(ReviewEvent.APPROVE) }
    var reviewSummaryBody by remember { mutableStateOf("") }

    val viewerLogin = remember(container.viewerProfile.value?.login) {
        container.viewerProfile.value?.login ?: container.sessionManager.session.value?.userLogin
    }
    val isOwnPr = viewerLogin != null && detail.author.login.equals(viewerLogin, ignoreCase = true)
    LaunchedEffect(isOwnPr) {
        if (isOwnPr && reviewEvent != ReviewEvent.COMMENT) {
            reviewEvent = ReviewEvent.COMMENT
        }
    }

    fun refreshSyncState(forceRefresh: Boolean = true) {
        scope.launch {
            syncBusy = true
            container.repository.getPullReviewSyncState(target.owner, target.repo, target.number, forceRefresh)
                .onSuccess { fresh ->
                    localSyncState = fresh
                    onSyncStateUpdated(fresh)
                }
                .onFailure { err ->
                    onMessage(strings.errorMessage(err, strings.reviewModeSyncError))
                }
            syncBusy = false
        }
    }

    LaunchedEffect(target.owner, target.repo, target.number) {
        refreshSyncState(forceRefresh = syncState == null)
    }

    val currentEntry = entries.getOrNull(pagerState.currentPage)
    val viewedCount = remember(entries, localSyncState.viewedStates) {
        countViewedFiles(entries, localSyncState.viewedStates)
    }
    val totalCount = entries.size
    val allFilesViewed = totalCount > 0 && viewedCount == totalCount
    val animatedProgress by animateFloatAsState(
        targetValue = if (totalCount > 0) viewedCount.toFloat() / totalCount.toFloat() else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()
    )
    val pendingComments = localSyncState.pendingReview?.comments.orEmpty()

    val isOpenPr = detail.state == IssueOrPullState.OPEN
    val canComment = !detail.headRefOid.isNullOrBlank() && detail.state != IssueOrPullState.MERGED
    val canMergeFromCompletion = isOpenPr &&
        !detail.isDraft &&
        detail.viewerCanMerge &&
        canConfirmPullMerge(detail)

    val toggleCurrentFileViewed: () -> Unit = {
        val entry = currentEntry
        if (entry != null && !actionBusy) {
            val path = entry.file.filename
            val currentlyViewed = localSyncState.viewedStates[path] == FileViewedState.VIEWED
            val nextViewed = !currentlyViewed
            val previousStates = localSyncState.viewedStates
            val optimisticStates = previousStates.toMutableMap().apply {
                put(path, if (nextViewed) FileViewedState.VIEWED else FileViewedState.UNVIEWED)
            }
            localSyncState = localSyncState.copy(viewedStates = optimisticStates)
            onSyncStateUpdated(localSyncState)

            if (nextViewed) {
                val nextUnviewed = findNextUnviewedFileIndex(entries, optimisticStates, pagerState.currentPage)
                if (nextUnviewed != null && nextUnviewed != pagerState.currentPage) {
                    scope.launch {
                        pagerState.animateScrollToPage(nextUnviewed)
                    }
                } else if (countViewedFiles(entries, optimisticStates) == entries.size) {
                    completionBannerDismissed = false
                }
            }

            scope.launch {
                container.repository.setPullFileViewed(
                    owner = target.owner,
                    repo = target.repo,
                    number = target.number,
                    pullRequestId = detail.id,
                    path = path,
                    viewed = nextViewed
                ).onFailure { err ->
                    localSyncState = localSyncState.copy(viewedStates = previousStates)
                    onSyncStateUpdated(localSyncState)
                    onMessage(strings.errorMessage(err, strings.reviewModeViewedError))
                }
            }
        }
    }

    fun openLineComposer(filePath: String, hunk: DiffHunk, lineIdx: Int, line: DiffLine) {
        val lineInfo = extractDiffLineNumberAndSide(line) ?: return
        val (lineNumber, side) = lineInfo
        val (_, selectedLines) = collectLineRangeForSuggestion(hunk, lineIdx, 1)
        composerState = composerState.open(
            ReviewCommentDraft(
                target = ReviewComposerTarget(
                    filePath = filePath,
                    hunk = hunk,
                    endLineIndex = lineIdx,
                    lineNumber = lineNumber,
                    side = side
                ),
                suggestedCode = selectedLines.joinToString("\n") { it.content }
            )
        )
    }

    fun openFileComposer(filePath: String) {
        composerState = composerState.open(
            ReviewCommentDraft(target = ReviewComposerTarget(filePath = filePath))
        )
    }

    fun openEditPendingComposer(comment: PullReviewComment) {
        composerState = composerState.open(
            ReviewCommentDraft(
                target = ReviewComposerTarget(
                    filePath = comment.path,
                    lineNumber = comment.line ?: comment.originalLine,
                    side = comment.diffSide ?: "RIGHT",
                    editingPendingComment = comment
                ),
                commentBody = comment.body
            )
        )
    }

    fun submitReviewNow(event: ReviewEvent, summaryBody: String) {
        scope.launch {
            actionBusy = true
            container.repository.submitPullReview(
                owner = target.owner,
                repo = target.repo,
                number = target.number,
                event = event,
                body = summaryBody,
                pendingReviewId = localSyncState.pendingReview?.id
            ).onSuccess {
                reviewSummaryBody = ""
                showSubmitSheet = false
                onMessage(strings.issueReviewSubmitted(strings.reviewEventLabel(event)))
                refreshSyncState(forceRefresh = true)
                onReloadDetail()
            }.onFailure { err ->
                onMessage(strings.errorMessage(err, strings.issueReviewFailed))
            }
            actionBusy = false
        }
    }

    val hasAnyOverlay = composerState.draft != null || showFilesSheet || showSubmitSheet || showDiscardPendingConfirm

    MaxxingPredictiveBackContainer(
        enabled = !hasAnyOverlay,
        onBack = onDismiss,
        modifier = Modifier.fillMaxSize()
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Compact one-handed top bar
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = onDismiss) {
                                    Icon(Lucide.X, contentDescription = strings.reviewModeCloseDesc)
                                }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable(enabled = entries.isNotEmpty()) { showFilesSheet = true }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = currentEntry?.file?.filename?.substringAfterLast('/') ?: strings.reviewModeTitle,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (entries.isNotEmpty()) {
                                            Icon(
                                                imageVector = Lucide.ChevronDown,
                                                contentDescription = strings.reviewModeFilesListDesc,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (currentEntry != null) {
                                        Text(
                                            text = "${pagerState.currentPage + 1}/$totalCount · +${currentEntry.file.additions}/-${currentEntry.file.deletions} · ${strings.reviewModeViewedCounter(viewedCount, totalCount)}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                TextButton(onClick = { wrapLines = !wrapLines }) {
                                    Text(
                                        text = if (wrapLines) strings.diffScrollMode else strings.diffWrapMode,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                IconButton(onClick = { showFilesSheet = true }) {
                                    Icon(
                                        imageVector = Lucide.ListChecks,
                                        contentDescription = strings.reviewModeFilesListDesc,
                                        tint = if (allFilesViewed) {
                                            GraphiteContrast.statusAccentColor(
                                                MaterialTheme.colorScheme,
                                                graphitePalette
                                            )
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                            if (syncBusy || actionBusy) {
                                MaxxingWavyProgress()
                            } else if (totalCount > 0) {
                                MaxxingWavyProgress(progress = animatedProgress)
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Celebratory MD3E Completion Banner when all files are viewed
                    AnimatedVisibility(
                        visible = allFilesViewed && !completionBannerDismissed,
                        enter = expandVertically(
                            animationSpec = MaterialTheme.motionScheme.slowSpatialSpec()
                        ) + fadeIn(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()),
                        exit = shrinkVertically(
                            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
                        ) + fadeOut()
                    ) {
                        ReviewCompletionBanner(
                            viewedCount = viewedCount,
                            totalCount = totalCount,
                            pendingCount = pendingComments.size,
                            isOpenPr = isOpenPr,
                            isOwnPr = isOwnPr,
                            canMerge = canMergeFromCompletion,
                            selectedMergeMethod = selectedMergeMethod,
                            allowedMergeMethods = allowedMergeMethods,
                            actionBusy = actionBusy,
                            onApprove = {
                                if (pendingComments.isNotEmpty() || reviewSummaryBody.isNotBlank()) {
                                    reviewEvent = ReviewEvent.APPROVE
                                    showSubmitSheet = true
                                } else {
                                    submitReviewNow(ReviewEvent.APPROVE, "")
                                }
                            },
                            onRequestChanges = {
                                reviewEvent = ReviewEvent.REQUEST_CHANGES
                                showSubmitSheet = true
                            },
                            onComment = {
                                reviewEvent = ReviewEvent.COMMENT
                                showSubmitSheet = true
                            },
                            onSelectMergeMethod = onSelectMergeMethod,
                            onMerge = onMergeClick,
                            onDismiss = { completionBannerDismissed = true }
                        )
                    }

                    if (entries.isEmpty()) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = strings.noFileDiffs,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            key = { idx -> entries[idx].file.filename }
                        ) { pageIdx ->
                            val entry = entries[pageIdx]
                            val filename = entry.file.filename
                            val viewedState = localSyncState.viewedStates[filename] ?: FileViewedState.UNVIEWED
                            val commentsByLine = remember(localSyncState, filename) {
                                collectReviewCommentsForFile(localSyncState, filename)
                            }
                            val threadsByLine = remember(localSyncState.threads, filename) {
                                localSyncState.threads
                                    .filter { it.path == filename }
                                    .groupBy { if (it.subjectType == "FILE") null else (it.line ?: it.originalLine) }
                            }
                            val isCollapsed = entry.autoCollapsed && manuallyExpandedCollapsed[filename] != true

                            PullReviewFilePage(
                                entry = entry,
                                viewedState = viewedState,
                                wrapLines = wrapLines,
                                isCollapsed = isCollapsed,
                                canComment = canComment,
                                commentsByLine = commentsByLine,
                                threadsByLine = threadsByLine,
                                onToggleExpandCollapsed = {
                                    manuallyExpandedCollapsed[filename] = isCollapsed
                                },
                                onCommentFile = if (canComment) {
                                    { openFileComposer(filename) }
                                } else null,
                                onClickLine = if (canComment) {
                                    { hunk, lineIdx, line ->
                                        openLineComposer(filename, hunk, lineIdx, line)
                                    }
                                } else null,
                                onEditPendingComment = { openEditPendingComposer(it) },
                                onDeletePendingComment = { comment ->
                                    scope.launch {
                                        actionBusy = true
                                        container.repository.deletePendingReviewComment(
                                            target.owner, target.repo, target.number, comment.id
                                        ).onSuccess {
                                            onMessage(strings.reviewModePendingDeleted)
                                            refreshSyncState(forceRefresh = true)
                                        }.onFailure { err ->
                                            onMessage(strings.errorMessage(err, strings.reviewModePendingDeleteError))
                                        }
                                        actionBusy = false
                                    }
                                }
                            )
                        }
                    }
                }

                // Bottom MD3E Floating Toolbar for one-handed review
                if (entries.isNotEmpty()) {
                    val currentFilename = currentEntry?.file?.filename
                    val isCurrentViewed = currentFilename != null &&
                        localSyncState.viewedStates[currentFilename] == FileViewedState.VIEWED
                    val hasPrev = pagerState.currentPage > 0
                    val hasNext = pagerState.currentPage < entries.lastIndex

                    ReviewModeFloatingToolbar(
                        hasPrev = hasPrev,
                        hasNext = hasNext,
                        isCurrentViewed = isCurrentViewed,
                        pendingCount = pendingComments.size,
                        allFilesViewed = allFilesViewed,
                        canComment = canComment,
                        actionBusy = actionBusy,
                        onPrev = {
                            if (hasPrev) {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                            }
                        },
                        onToggleViewedAndNext = toggleCurrentFileViewed,
                        onNext = {
                            if (hasNext) {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            }
                        },
                        onFileComment = {
                            currentFilename?.let { openFileComposer(it) }
                        },
                        onOpenSubmitReview = {
                            if (allFilesViewed && !isOwnPr) {
                                reviewEvent = ReviewEvent.APPROVE
                            }
                            showSubmitSheet = true
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 16.dp)
                    )
                }
            }
        }
    }

    // File list picker bottom sheet
    if (showFilesSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showFilesSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.reviewModeFilesListTitle,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    ExpressiveStatusPill(
                        label = strings.reviewModeViewedCounter(viewedCount, totalCount),
                        containerColor = if (allFilesViewed) {
                            Color(0xFF16A34A).copy(alpha = 0.2f)
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (entries.any { it.category != ReviewFileCategory.PRIMARY }) {
                    Text(
                        text = strings.reviewModeSmartOrderNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(360.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(entries, key = { _, e -> e.file.filename }) { idx, entry ->
                        val fname = entry.file.filename
                        val vState = localSyncState.viewedStates[fname] ?: FileViewedState.UNVIEWED
                        val filePendingCount = pendingComments.count { it.path == fname }
                        val isSelected = idx == pagerState.currentPage
                        val rowColors = GraphiteContrast.reviewFileRowColors(
                            MaterialTheme.colorScheme,
                            graphitePalette,
                            isSelected
                        )
                        val rowBorder = rowColors.border
                        val viewedStateLabel = when (vState) {
                            FileViewedState.VIEWED -> strings.reviewModeViewedBadge
                            FileViewedState.DISMISSED -> strings.reviewModeDismissedBadge
                            FileViewedState.UNVIEWED -> strings.reviewModeUnviewedBadge
                        }
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = rowColors.container,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (rowBorder != null) {
                                        Modifier.border(
                                            1.5.dp,
                                            rowBorder,
                                            MaterialTheme.shapes.medium
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable {
                                    showFilesSheet = false
                                    scope.launch { pagerState.animateScrollToPage(idx) }
                                }
                                .then(
                                    if (graphitePalette) {
                                        Modifier.semantics(mergeDescendants = true) {
                                            this.selected = isSelected
                                            stateDescription = viewedStateLabel
                                        }
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = when (vState) {
                                        FileViewedState.VIEWED -> Lucide.CircleCheck
                                        FileViewedState.DISMISSED -> Lucide.CircleDot
                                        FileViewedState.UNVIEWED -> Lucide.FileCode
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = when (vState) {
                                        FileViewedState.VIEWED -> GraphiteContrast.statusAccentColor(
                                            MaterialTheme.colorScheme,
                                            graphitePalette
                                        )
                                        FileViewedState.DISMISSED -> GraphiteContrast.statusAccentColor(
                                            MaterialTheme.colorScheme,
                                            graphitePalette,
                                            isWarning = true
                                        )
                                        FileViewedState.UNVIEWED -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = fname.substringAfterLast('/'),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (graphitePalette) MaterialTheme.colorScheme.onSurface else Color.Unspecified,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = fname,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (filePendingCount > 0) {
                                    ExpressiveStatusPill(
                                        label = strings.reviewModePendingCount(filePendingCount),
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                                Text(
                                    text = "+${entry.file.additions}/-${entry.file.deletions}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Submit Review & Pending Drafts Sheet
    if (showSubmitSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val availableEvents = remember(isOwnPr) {
            if (isOwnPr) listOf(ReviewEvent.COMMENT) else ReviewEvent.entries
        }
        val effectiveEvent = if (reviewEvent in availableEvents) reviewEvent else ReviewEvent.COMMENT
        val canSubmitReview = effectiveEvent == ReviewEvent.APPROVE ||
            reviewSummaryBody.isNotBlank() ||
            pendingComments.isNotEmpty()

        ModalBottomSheet(
            onDismissRequest = { if (!actionBusy) showSubmitSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.issueSubmitReviewTitle,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    if (pendingComments.isNotEmpty() && localSyncState.pendingReview != null) {
                        TextButton(
                            enabled = !actionBusy,
                            onClick = { showDiscardPendingConfirm = true }
                        ) {
                            Text(strings.reviewModeDiscardAction, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                if (pendingComments.isNotEmpty()) {
                    Text(
                        text = strings.reviewModePendingCommentsHeader(pendingComments.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        pendingComments.forEach { pending ->
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = buildString {
                                                append(pending.path.substringAfterLast('/'))
                                                val ln = pending.line ?: pending.originalLine
                                                if (ln != null) append(":$ln")
                                            },
                                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        IconButton(
                                            enabled = !actionBusy,
                                            onClick = {
                                                showSubmitSheet = false
                                                openEditPendingComposer(pending)
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Lucide.Pencil,
                                                contentDescription = strings.reviewModeEditCommentDesc,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        IconButton(
                                            enabled = !actionBusy,
                                            onClick = {
                                                scope.launch {
                                                    actionBusy = true
                                                    container.repository.deletePendingReviewComment(
                                                        target.owner, target.repo, target.number, pending.id
                                                    ).onSuccess {
                                                        onMessage(strings.reviewModePendingDeleted)
                                                        refreshSyncState(forceRefresh = true)
                                                    }.onFailure { err ->
                                                        onMessage(strings.errorMessage(err, strings.reviewModePendingDeleteError))
                                                    }
                                                    actionBusy = false
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Lucide.Trash2,
                                                contentDescription = strings.reviewModeDeleteCommentDesc,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = pending.body,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = strings.reviewModeNoPendingComments,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (availableEvents.size > 1) {
                    MaxxingConnectedButtonGroup(
                        options = availableEvents,
                        selected = effectiveEvent,
                        onSelect = { reviewEvent = it },
                        labelProvider = { strings.reviewEventLabel(it) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = reviewSummaryBody,
                    onValueChange = { reviewSummaryBody = it },
                    label = { Text(strings.issueReviewSummaryLabel) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    enabled = canSubmitReview && !actionBusy,
                    onClick = { submitReviewNow(effectiveEvent, reviewSummaryBody) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (actionBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(Lucide.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = if (pendingComments.isNotEmpty()) {
                            strings.reviewModeSubmitPendingLabel(pendingComments.size)
                        } else {
                            strings.reviewEventLabel(effectiveEvent)
                        }
                    )
                }
            }
        }
    }

    if (showDiscardPendingConfirm) {
        val pendingId = localSyncState.pendingReview?.id
        AlertDialog(
            onDismissRequest = { showDiscardPendingConfirm = false },
            title = { Text(strings.reviewModeDiscardPendingTitle) },
            text = { Text(strings.reviewModeDiscardPendingConfirm(pendingComments.size)) },
            confirmButton = {
                Button(
                    enabled = !actionBusy && pendingId != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    onClick = {
                        showDiscardPendingConfirm = false
                        val revId = pendingId ?: return@Button
                        scope.launch {
                            actionBusy = true
                            container.repository.discardPendingPullReview(
                                target.owner, target.repo, target.number, revId
                            ).onSuccess {
                                showSubmitSheet = false
                                onMessage(strings.reviewModePendingDiscarded)
                                refreshSyncState(forceRefresh = true)
                            }.onFailure { err ->
                                onMessage(strings.errorMessage(err, strings.reviewModePendingDiscardError))
                            }
                            actionBusy = false
                        }
                    }
                ) { Text(strings.reviewModeDiscardAction) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardPendingConfirm = false }) { Text(strings.cancel) }
            }
        )
    }

    // Mobile Comment & Suggested Change Composer Overlay
    composerState.draft?.let { activeDraft ->
        val activeTarget = activeDraft.target
        ReviewCommentComposerOverlay(
            filePath = activeTarget.filePath,
            hunk = activeTarget.hunk,
            endLineIndex = activeTarget.endLineIndex,
            lineNumber = activeTarget.lineNumber,
            side = activeTarget.side,
            isEditingPending = activeTarget.editingPendingComment != null,
            mode = activeDraft.mode,
            onModeChange = { nextMode ->
                composerState = composerState.updateDraft { draft ->
                    if (
                        nextMode == ComposerMode.SUGGESTION &&
                        draft.suggestedCode.isEmpty() &&
                        activeTarget.hunk != null &&
                        activeTarget.endLineIndex != null
                    ) {
                        val (_, lines) = collectLineRangeForSuggestion(activeTarget.hunk, activeTarget.endLineIndex, draft.lineCount)
                        draft.copy(mode = nextMode, suggestedCode = lines.joinToString("\n") { it.content })
                    } else {
                        draft.copy(mode = nextMode)
                    }
                }
            },
            commentBody = activeDraft.commentBody,
            onCommentBodyChange = { body ->
                composerState = composerState.updateDraft { it.copy(commentBody = body) }
            },
            suggestionNote = activeDraft.suggestionNote,
            onSuggestionNoteChange = { note ->
                composerState = composerState.updateDraft { it.copy(suggestionNote = note) }
            },
            suggestedCode = activeDraft.suggestedCode,
            onSuggestedCodeChange = { code ->
                composerState = composerState.updateDraft { it.copy(suggestedCode = code) }
            },
            lineCount = activeDraft.lineCount,
            onChangeLineCount = { nextCount ->
                if (activeTarget.hunk != null && activeTarget.endLineIndex != null) {
                    composerState = composerState.updateDraft { draft ->
                        val (_, lines) = collectLineRangeForSuggestion(activeTarget.hunk, activeTarget.endLineIndex, nextCount)
                        draft.copy(
                            lineCount = lines.size.coerceAtLeast(1),
                            suggestedCode = lines.joinToString("\n") { it.content }
                        )
                    }
                }
            },
            saveAsPending = activeDraft.saveAsPending,
            onSaveAsPendingChange = { saveAsPending ->
                composerState = composerState.updateDraft { it.copy(saveAsPending = saveAsPending) }
            },
            isSubmitting = actionBusy || composerState.isSubmitting,
            errorMessage = composerState.errorMessage,
            isAttachingMedia = attachmentController.isUploading,
            onAttachMedia = {
                attachmentController.attach { snippet ->
                    composerState = composerState.updateDraft { draft ->
                        if (draft.mode == ComposerMode.SUGGESTION) {
                            draft.copy(suggestionNote = appendAttachmentMarkdown(draft.suggestionNote, snippet))
                        } else {
                            draft.copy(commentBody = appendAttachmentMarkdown(draft.commentBody, snippet))
                        }
                    }
                }
            },
            onDismiss = { if (!actionBusy) composerState = composerState.cancel() },
            onSave = {
                val editing = activeTarget.editingPendingComment
                val finalBody = if (activeDraft.mode == ComposerMode.SUGGESTION) {
                    buildSuggestedChangeComment(activeDraft.suggestionNote, activeDraft.suggestedCode)
                } else {
                    activeDraft.commentBody.trim()
                }
                if (finalBody.isBlank() || actionBusy || composerState.isSubmitting) return@ReviewCommentComposerOverlay

                val sha = detail.headRefOid
                if (editing == null && !activeDraft.saveAsPending && sha.isNullOrBlank()) {
                    val message = strings.failedPostDiffComment
                    composerState = composerState.failSubmit(message)
                    onMessage(message)
                    return@ReviewCommentComposerOverlay
                }

                val startLineForMulti = if (
                    activeDraft.mode == ComposerMode.SUGGESTION &&
                    activeDraft.lineCount > 1 &&
                    activeTarget.hunk != null &&
                    activeTarget.endLineIndex != null
                ) {
                    collectLineRangeForSuggestion(activeTarget.hunk, activeTarget.endLineIndex, activeDraft.lineCount).first
                } else null

                // Keep the composer (and the typed draft) open until the request succeeds so a
                // failure can be retried with the same contents. Only an explicit cancel or a
                // successful submit clears it.
                composerState = composerState.beginSubmit()
                scope.launch {
                    actionBusy = true
                    if (editing != null) {
                        container.repository.updatePendingReviewComment(
                            target.owner, target.repo, target.number, editing.id, finalBody
                        ).onSuccess {
                            onMessage(strings.reviewModePendingUpdated)
                            composerState = composerState.succeedSubmit()
                            refreshSyncState(forceRefresh = true)
                        }.onFailure { err ->
                            val message = strings.errorMessage(err, strings.reviewModePendingSaveError)
                            composerState = composerState.failSubmit(message)
                            onMessage(message)
                        }
                    } else if (activeDraft.saveAsPending) {
                        container.repository.addPendingReviewComment(
                            owner = target.owner,
                            repo = target.repo,
                            number = target.number,
                            pullRequestId = detail.id,
                            commitOid = detail.headRefOid,
                            existingPendingReviewId = localSyncState.pendingReview?.id,
                            path = activeTarget.filePath,
                            body = finalBody,
                            line = activeTarget.lineNumber,
                            side = activeTarget.side,
                            startLine = startLineForMulti,
                            startSide = if (startLineForMulti != null) activeTarget.side else null
                        ).onSuccess {
                            onMessage(strings.reviewModePendingAdded)
                            composerState = composerState.succeedSubmit()
                            refreshSyncState(forceRefresh = true)
                        }.onFailure { err ->
                            val message = strings.errorMessage(err, strings.reviewModePendingSaveError)
                            composerState = composerState.failSubmit(message)
                            onMessage(message)
                        }
                    } else {
                        container.repository.createPullDiffComment(
                            owner = target.owner,
                            repo = target.repo,
                            number = target.number,
                            commitId = sha.orEmpty(),
                            path = activeTarget.filePath,
                            body = finalBody,
                            line = activeTarget.lineNumber,
                            side = activeTarget.side
                        ).onSuccess {
                            onMessage(strings.addedInlineDiffComment)
                            composerState = composerState.succeedSubmit()
                            refreshSyncState(forceRefresh = true)
                        }.onFailure { err ->
                            val message = strings.errorMessage(err, strings.failedPostDiffComment)
                            composerState = composerState.failSubmit(message)
                            onMessage(message)
                        }
                    }
                    actionBusy = false
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReviewModeFloatingToolbar(
    hasPrev: Boolean,
    hasNext: Boolean,
    isCurrentViewed: Boolean,
    pendingCount: Int,
    allFilesViewed: Boolean,
    canComment: Boolean,
    actionBusy: Boolean,
    onPrev: () -> Unit,
    onToggleViewedAndNext: () -> Unit,
    onNext: () -> Unit,
    onFileComment: () -> Unit,
    onOpenSubmitReview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = appStrings()
    val buttonColors = MaxxingFloatingToolbarDefaults.vibrantButtonColors()
    val pillPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)

    MaxxingFloatingToolbar(
        expanded = true,
        vibrant = true,
        contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = modifier
    ) {
        FilledTonalIconButton(
            onClick = onPrev,
            enabled = hasPrev && !actionBusy
        ) {
            Icon(Lucide.ArrowLeft, contentDescription = strings.reviewModePrevFileDesc)
        }

        Spacer(Modifier.width(4.dp))

        if (isCurrentViewed) {
            FilledTonalButton(
                onClick = onToggleViewedAndNext,
                enabled = !actionBusy,
                contentPadding = pillPadding
            ) {
                Icon(Lucide.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(strings.reviewModeViewedBadge, maxLines = 1, softWrap = false)
            }
        } else {
            Button(
                onClick = onToggleViewedAndNext,
                enabled = !actionBusy,
                colors = buttonColors,
                contentPadding = pillPadding
            ) {
                Icon(Lucide.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(strings.reviewModeMarkViewedAndNext, maxLines = 1, softWrap = false)
            }
        }

        Spacer(Modifier.width(4.dp))

        FilledTonalIconButton(
            onClick = onNext,
            enabled = hasNext && !actionBusy
        ) {
            Icon(Lucide.ArrowRight, contentDescription = strings.reviewModeNextFileDesc)
        }

        if (canComment) {
            FilledTonalIconButton(
                onClick = onFileComment,
                enabled = !actionBusy
            ) {
                Icon(Lucide.MessageSquarePlus, contentDescription = strings.commentOnFile)
            }
        }

        Spacer(Modifier.width(4.dp))

        if (allFilesViewed || pendingCount > 0) {
            Button(
                onClick = onOpenSubmitReview,
                enabled = !actionBusy,
                colors = buttonColors,
                contentPadding = pillPadding
            ) {
                BadgedBox(
                    badge = {
                        if (pendingCount > 0) {
                            Badge { Text(pendingCount.toString()) }
                        }
                    }
                ) {
                    Icon(Lucide.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(strings.reviewModeSubmitReviewButton, maxLines = 1, softWrap = false)
            }
        } else {
            FilledTonalButton(
                onClick = onOpenSubmitReview,
                enabled = !actionBusy,
                contentPadding = pillPadding
            ) {
                Icon(Lucide.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(strings.reviewModeSubmitReviewButton, maxLines = 1, softWrap = false)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewCompletionBanner(
    viewedCount: Int,
    totalCount: Int,
    pendingCount: Int,
    isOpenPr: Boolean,
    isOwnPr: Boolean,
    canMerge: Boolean,
    selectedMergeMethod: MergeMethod,
    actionBusy: Boolean,
    onApprove: () -> Unit,
    onRequestChanges: () -> Unit,
    onComment: () -> Unit,
    onSelectMergeMethod: (MergeMethod) -> Unit,
    onMerge: (MergeMethod) -> Unit,
    onDismiss: () -> Unit,
    allowedMergeMethods: List<MergeMethod> = MergeMethod.entries
) {
    val strings = appStrings()
    val graphitePalette = LocalGraphitePalette.current
    val colorScheme = MaterialTheme.colorScheme
    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = GraphiteContrast.reviewCompletionCardContainer(
                colorScheme,
                graphitePalette
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF16A34A).copy(alpha = 0.18f),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Lucide.CircleCheck,
                            contentDescription = null,
                            tint = GraphiteContrast.statusAccentColor(
                                colorScheme,
                                graphitePalette
                            ),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.reviewModeAllViewedTitle(viewedCount, totalCount),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (graphitePalette) colorScheme.onSurface else Color.Unspecified,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (pendingCount > 0) {
                            strings.reviewModeAllViewedWithPending(pendingCount)
                        } else {
                            strings.reviewModeAllViewedReady
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Lucide.X, contentDescription = strings.closeAction, modifier = Modifier.size(16.dp))
                }
            }

            if (isOpenPr) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!isOwnPr) {
                        Button(
                            onClick = onApprove,
                            enabled = !actionBusy,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Lucide.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.reviewApproveLabel, maxLines = 1, softWrap = false)
                        }
                        FilledTonalButton(
                            onClick = onRequestChanges,
                            enabled = !actionBusy,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(strings.reviewRequestChangesLabel, maxLines = 1, softWrap = false)
                        }
                    }
                    OutlinedButton(
                        onClick = onComment,
                        enabled = !actionBusy,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(strings.reviewCommentLabel, maxLines = 1, softWrap = false)
                    }
                    if (canMerge) {
                        val validMethods = allowedMergeMethods.ifEmpty { MergeMethod.entries }
                        val mergeOptions = remember(strings, validMethods) {
                            validMethods.map {
                                SplitActionOption(
                                    value = it,
                                    label = strings.mergeMethodLabel(it),
                                    icon = Lucide.GitMerge,
                                    buttonLabel = strings.mergeMethodShortLabel(it)
                                )
                            }
                        }
                        MaxxingSplitActionButton(
                            selectedOption = mergeOptions.firstOrNull { it.value == selectedMergeMethod } ?: mergeOptions.first(),
                            options = mergeOptions,
                            enabled = !actionBusy,
                            onSelectOption = onSelectMergeMethod,
                            onPrimaryClick = onMerge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PullReviewFilePage(
    entry: ReviewFileEntry,
    viewedState: FileViewedState,
    wrapLines: Boolean,
    isCollapsed: Boolean,
    canComment: Boolean,
    commentsByLine: Map<Int?, List<PullReviewComment>>,
    threadsByLine: Map<Int?, List<dev.maxxing.github.model.PullReviewThread>>,
    onToggleExpandCollapsed: () -> Unit,
    onCommentFile: (() -> Unit)?,
    onClickLine: ((DiffHunk, Int, DiffLine) -> Unit)?,
    onEditPendingComment: (PullReviewComment) -> Unit,
    onDeletePendingComment: (PullReviewComment) -> Unit
) {
    val file = entry.file
    val strings = appStrings()
    val graphitePalette = LocalGraphitePalette.current
    val colorScheme = MaterialTheme.colorScheme
    val additionsColor = GraphiteContrast.additionsCounterColor(colorScheme, graphitePalette)
    val lineDigits = remember(file) { computeDiffMaxLineDigits(file) }
    val listState = remember(file.filename) { LazyListState(firstVisibleItemIndex = 0) }
    val hScroll = remember(file.filename) { ScrollState(0) }
    val hunkWordHighlights = remember(file) { file.hunks.map { computeHunkWordHighlights(it) } }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val contentStyle = MaterialTheme.typography.bodySmall.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = MaterialTheme.typography.bodySmall.fontSize
    )
    val lineNumStyle = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
    val prefixStyle = MaterialTheme.typography.bodySmall.copy(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold
    )
    val measuredContentWidthDp = remember(file, lineDigits, contentStyle, lineNumStyle, density) {
        val sampleDigits = "0".repeat(lineDigits)
        val numWidthPx = textMeasurer.measure(sampleDigits, lineNumStyle, maxLines = 1, softWrap = false).size.width
        val prefixWidthPx = textMeasurer.measure("+", prefixStyle, maxLines = 1, softWrap = false).size.width
        val maxContentPx = collectWidestDiffLineCandidates(file, limit = 8).maxOfOrNull { text ->
            textMeasurer.measure(text, contentStyle, maxLines = 1, softWrap = false).size.width
        } ?: 0
        val maxHunkPx = file.hunks.maxOfOrNull { hunk ->
            textMeasurer.measure(hunk.header, lineNumStyle, maxLines = 1, softWrap = false).size.width
        } ?: 0
        maxOf(
            with(density) { (numWidthPx * 2 + prefixWidthPx + maxContentPx).toDp() } + 72.dp,
            with(density) { maxHunkPx.toDp() } + 48.dp
        )
    }

    val statusColor = when (file.status) {
        FileChangeStatus.ADDED -> Color(0xFF16A34A)
        FileChangeStatus.REMOVED -> MaterialTheme.colorScheme.error
        FileChangeStatus.RENAMED -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val minRowWidth = maxWidth
        val unifiedWidth = maxOf(minRowWidth, measuredContentWidthDp)
        val diffRowModifier = if (wrapLines) Modifier.fillMaxWidth() else Modifier.width(unifiedWidth)

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 116.dp)
        ) {
            item(key = "review_file_hdr_${file.filename}", contentType = "review_file_header") {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ExpressiveStatusPill(
                                label = strings.fileStatusBadge(file.status),
                                containerColor = statusColor.copy(alpha = 0.2f),
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                            if (viewedState == FileViewedState.VIEWED) {
                                ExpressiveStatusPill(
                                    label = strings.reviewModeViewedBadge,
                                    containerColor = Color(0xFF16A34A).copy(alpha = 0.2f),
                                    contentColor = GraphiteContrast.statusAccentColor(
                                        colorScheme,
                                        graphitePalette
                                    ),
                                    icon = Lucide.Check
                                )
                            } else if (viewedState == FileViewedState.DISMISSED) {
                                ExpressiveStatusPill(
                                    label = strings.reviewModeDismissedBadge,
                                    containerColor = Color(0xFFD97706).copy(alpha = 0.2f),
                                    contentColor = GraphiteContrast.statusAccentColor(
                                        colorScheme,
                                        graphitePalette,
                                        isWarning = true
                                    )
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "+${file.additions}",
                                style = MaterialTheme.typography.labelMedium,
                                color = additionsColor,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "-${file.deletions}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                            if (onCommentFile != null) {
                                IconButton(onClick = onCommentFile, modifier = Modifier.size(30.dp)) {
                                    Icon(
                                        Lucide.MessageSquarePlus,
                                        contentDescription = strings.commentOnFile,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = file.filename,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        file.previousFilename?.let { prev ->
                            Text(
                                text = strings.reviewModeRenamedFrom(prev),
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (canComment && !isCollapsed && file.hunks.isNotEmpty()) {
                            Text(
                                text = strings.reviewModeLineTapHint,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // File-level comments
            val fileComments = commentsByLine[null].orEmpty()
            if (fileComments.isNotEmpty()) {
                item(key = "review_file_comments_${file.filename}", contentType = "review_comment_block") {
                    ReviewInlineCommentBlock(
                        comments = fileComments,
                        isResolved = threadsByLine[null]?.all { it.isResolved } == true && threadsByLine[null]?.isNotEmpty() == true,
                        isOutdated = threadsByLine[null]?.any { it.isOutdated } == true,
                        onEditPending = onEditPendingComment,
                        onDeletePending = onDeletePendingComment,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            if (isCollapsed) {
                item(key = "review_collapsed_${file.filename}", contentType = "review_collapsed") {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = when {
                                    file.patch == null -> strings.reviewModeAutoCollapsedBinary
                                    entry.category == ReviewFileCategory.LOCK_OR_GENERATED -> strings.reviewModeAutoCollapsedGenerated
                                    else -> strings.reviewModeAutoCollapsedDeleted
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (file.patch != null) {
                                FilledTonalButton(onClick = onToggleExpandCollapsed) {
                                    Icon(Lucide.Eye, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(strings.reviewModeShowDiffAction)
                                }
                            }
                        }
                    }
                }
            } else if (file.hunks.isEmpty()) {
                item(key = "review_empty_${file.filename}", contentType = "review_empty") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (file.patch == null) strings.diffBinaryOrLarge else strings.diffEmptyPatch,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                if (entry.autoCollapsed) {
                    item(key = "review_collapse_btn_${file.filename}") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onToggleExpandCollapsed) {
                                Text(strings.reviewModeHideDiffAction)
                            }
                        }
                    }
                }

                file.hunks.forEachIndexed { hunkIdx, hunk ->
                    val wordHighlights = hunkWordHighlights.getOrElse(hunkIdx) { emptyMap() }
                    item(key = "rv_hunk_${file.filename}_$hunkIdx", contentType = "rv_hunk") {
                        Box(
                            modifier = if (wrapLines) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().horizontalScroll(hScroll)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                                modifier = diffRowModifier
                            ) {
                                Text(
                                    text = hunk.header,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = if (wrapLines) Int.MAX_VALUE else 1,
                                    softWrap = wrapLines,
                                    overflow = if (wrapLines) TextOverflow.Clip else TextOverflow.Visible,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    itemsIndexed(
                        items = hunk.lines,
                        key = { lineIdx, _ -> "rv_line_${file.filename}_${hunkIdx}_$lineIdx" },
                        contentType = { _, _ -> "rv_line" }
                    ) { lineIdx, line ->
                        val lineTarget = extractDiffLineNumberAndSide(line)
                        val matchingComments = remember(commentsByLine, lineTarget) {
                            if (lineTarget == null) {
                                emptyList()
                            } else {
                                val (num, side) = lineTarget
                                commentsByLine[num].orEmpty().filter { c ->
                                    c.diffSide == null || c.diffSide.equals(side, ignoreCase = true)
                                }
                            }
                        }
                        val matchingThreads = remember(threadsByLine, lineTarget) {
                            if (lineTarget == null) emptyList()
                            else {
                                val (num, side) = lineTarget
                                threadsByLine[num].orEmpty().filter { t ->
                                    t.diffSide == null || t.diffSide.equals(side, ignoreCase = true)
                                }
                            }
                        }

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = if (wrapLines) {
                                    Modifier.fillMaxWidth()
                                } else {
                                    Modifier.fillMaxWidth().horizontalScroll(hScroll)
                                }
                            ) {
                                SelectionContainer {
                                    DiffLineRow(
                                        line = line,
                                        lineDigits = lineDigits,
                                        wrapLines = wrapLines,
                                        fontSizeSp = 12.5f,
                                        lineHeightSp = 19f,
                                        verticalPaddingDp = 3f,
                                        inlineHighlightRanges = wordHighlights[lineIdx].orEmpty(),
                                        modifier = diffRowModifier,
                                        onClick = if (onClickLine != null && line.kind != DiffLineKind.META) {
                                            { onClickLine(hunk, lineIdx, line) }
                                        } else null
                                    )
                                }
                            }

                            if (matchingComments.isNotEmpty()) {
                                ReviewInlineCommentBlock(
                                    comments = matchingComments,
                                    isResolved = matchingThreads.isNotEmpty() && matchingThreads.all { it.isResolved },
                                    isOutdated = matchingThreads.any { it.isOutdated },
                                    onEditPending = onEditPendingComment,
                                    onDeletePending = onDeletePendingComment,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
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
private fun ReviewInlineCommentBlock(
    comments: List<PullReviewComment>,
    isResolved: Boolean,
    isOutdated: Boolean,
    onEditPending: (PullReviewComment) -> Unit,
    onDeletePending: (PullReviewComment) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = appStrings()
    val graphitePalette = LocalGraphitePalette.current
    val colorScheme = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isResolved || isOutdated) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isResolved) {
                        ExpressiveStatusPill(
                            label = strings.reviewModeResolvedBadge,
                            containerColor = Color(0xFF16A34A).copy(alpha = 0.16f),
                            contentColor = GraphiteContrast.statusAccentColor(
                                colorScheme,
                                graphitePalette
                            ),
                            icon = Lucide.Check
                        )
                    }
                    if (isOutdated) {
                        ExpressiveStatusPill(
                            label = strings.reviewModeOutdatedBadge,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            comments.forEachIndexed { idx, comment ->
                if (idx > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        UserAvatar(comment.author.avatarUrl, comment.author.login, sizeDp = 20)
                        Text(
                            text = "@${comment.author.login}",
                            style = MaterialTheme.typography.labelMedium
                        )
                        if (comment.isPending) {
                            ExpressiveStatusPill(
                                label = strings.reviewModePendingBadge,
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        if (comment.isPending && comment.viewerCanUpdate) {
                            IconButton(onClick = { onEditPending(comment) }, modifier = Modifier.size(26.dp)) {
                                Icon(
                                    Lucide.Pencil,
                                    contentDescription = strings.reviewModeEditCommentDesc,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        if (comment.isPending && comment.viewerCanDelete) {
                            IconButton(onClick = { onDeletePending(comment) }, modifier = Modifier.size(26.dp)) {
                                Icon(
                                    Lucide.Trash2,
                                    contentDescription = strings.reviewModeDeleteCommentDesc,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                    GitHubMarkdown(comment.body)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReviewCommentComposerOverlay(
    filePath: String,
    hunk: DiffHunk?,
    endLineIndex: Int?,
    lineNumber: Int?,
    side: String,
    isEditingPending: Boolean,
    mode: ComposerMode,
    onModeChange: (ComposerMode) -> Unit,
    commentBody: String,
    onCommentBodyChange: (String) -> Unit,
    suggestionNote: String,
    onSuggestionNoteChange: (String) -> Unit,
    suggestedCode: String,
    onSuggestedCodeChange: (String) -> Unit,
    lineCount: Int,
    onChangeLineCount: (Int) -> Unit,
    saveAsPending: Boolean,
    onSaveAsPendingChange: (Boolean) -> Unit,
    isSubmitting: Boolean,
    errorMessage: String?,
    isAttachingMedia: Boolean,
    onAttachMedia: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val strings = appStrings()
    var isPreviewMode by remember { mutableStateOf(false) }
    val supportsSuggestion = !isEditingPending && lineNumber != null && side == "RIGHT" && hunk != null && endLineIndex != null
    val canSave = if (mode == ComposerMode.SUGGESTION && supportsSuggestion) {
        suggestedCode.isNotEmpty()
    } else {
        commentBody.isNotBlank()
    }
    val saveLabel = when {
        isEditingPending -> strings.reviewModeSaveEditAction
        saveAsPending -> strings.reviewModeAddPendingAction
        else -> strings.reviewModePostSingleAction
    }

    MaxxingPredictiveBackContainer(
        enabled = true,
        onBack = { if (!isSubmitting) onDismiss() }
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxSize().imePadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(enabled = !isSubmitting, onClick = onDismiss) {
                                Icon(Lucide.X, contentDescription = strings.closeEditor)
                            }
                            Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                                Text(
                                    text = when {
                                        isEditingPending -> strings.reviewModeEditCommentTitle
                                        lineNumber != null -> strings.reviewCommentLineTitle(filePath.substringAfterLast('/'), lineNumber)
                                        else -> strings.fileCommentTitle(filePath.substringAfterLast('/'))
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = filePath,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(
                                enabled = !isSubmitting,
                                onClick = { isPreviewMode = !isPreviewMode }
                            ) {
                                Icon(
                                    if (isPreviewMode) Lucide.Pencil else Lucide.Eye,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(if (isPreviewMode) strings.writeMode else strings.previewMode, maxLines = 1, softWrap = false)
                            }
                            IconButton(
                                enabled = !isSubmitting && !isAttachingMedia && !isPreviewMode,
                                onClick = onAttachMedia
                            ) {
                                if (isAttachingMedia) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        Lucide.Paperclip,
                                        contentDescription = strings.editorAttachMedia,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Button(
                                enabled = canSave && !isSubmitting && !isAttachingMedia,
                                onClick = onSave,
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                if (isSubmitting) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(saveLabel, maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (errorMessage != null) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }

                    if (supportsSuggestion) {
                        MaxxingConnectedButtonGroup(
                            options = ComposerMode.entries,
                            selected = mode,
                            onSelect = onModeChange,
                            enabled = !isSubmitting,
                            labelProvider = { m ->
                                when (m) {
                                    ComposerMode.COMMENT -> strings.reviewModeCommentMode
                                    ComposerMode.SUGGESTION -> strings.reviewModeSuggestionMode
                                }
                            },
                            iconProvider = { m ->
                                when (m) {
                                    ComposerMode.COMMENT -> Lucide.MessageSquare
                                    ComposerMode.SUGGESTION -> Lucide.Sparkles
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (!isEditingPending) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = saveAsPending,
                                enabled = !isSubmitting,
                                onClick = { onSaveAsPendingChange(true) },
                                label = { Text(strings.reviewModeAddPendingAction) },
                                leadingIcon = if (saveAsPending) {
                                    { Icon(Lucide.Check, contentDescription = null, modifier = Modifier.size(15.dp)) }
                                } else null
                            )
                            FilterChip(
                                selected = !saveAsPending,
                                enabled = !isSubmitting,
                                onClick = { onSaveAsPendingChange(false) },
                                label = { Text(strings.reviewModePostSingleAction) },
                                leadingIcon = if (!saveAsPending) {
                                    { Icon(Lucide.Check, contentDescription = null, modifier = Modifier.size(15.dp)) }
                                } else null
                            )
                        }
                    }

                    if (isPreviewMode) {
                        val previewMarkdown = if (mode == ComposerMode.SUGGESTION && supportsSuggestion) {
                            buildSuggestedChangeComment(suggestionNote, suggestedCode)
                        } else {
                            commentBody
                        }
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(modifier = Modifier.padding(14.dp)) {
                                if (previewMarkdown.isBlank()) {
                                    Text(
                                        text = strings.nothingToPreview,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    GitHubMarkdown(previewMarkdown)
                                }
                            }
                        }
                    } else if (mode == ComposerMode.SUGGESTION && supportsSuggestion && hunk != null && endLineIndex != null && lineNumber != null) {
                        val (startLineNum, _) = remember(hunk, endLineIndex, lineCount) {
                            collectLineRangeForSuggestion(hunk, endLineIndex, lineCount)
                        }
                        val maxExpandableLines = remember(hunk, endLineIndex) {
                            collectLineRangeForSuggestion(hunk, endLineIndex, hunk.lines.size).second.size.coerceAtLeast(1)
                        }
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (lineCount > 1 && startLineNum != null && startLineNum < lineNumber) {
                                        strings.reviewModeLineRangeLabel(startLineNum, lineNumber)
                                    } else {
                                        strings.reviewModeLineSingleLabel(lineNumber)
                                    },
                                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    enabled = !isSubmitting && lineCount > 1,
                                    onClick = { onChangeLineCount(1) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Lucide.Minus, contentDescription = strings.reviewModeShrinkRange, modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    enabled = !isSubmitting && lineCount < maxExpandableLines,
                                    onClick = { onChangeLineCount(lineCount + 1) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Lucide.Plus, contentDescription = strings.reviewModeExpandRangeUp, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        OutlinedTextField(
                            value = suggestedCode,
                            onValueChange = onSuggestedCodeChange,
                            enabled = !isSubmitting,
                            label = { Text(strings.reviewModeSuggestedCodeLabel) },
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            minLines = 3,
                            maxLines = 12,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = suggestionNote,
                            onValueChange = onSuggestionNoteChange,
                            enabled = !isSubmitting,
                            label = { Text(strings.reviewModeSuggestionNoteLabel) },
                            minLines = 2,
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        OutlinedTextField(
                            value = commentBody,
                            onValueChange = onCommentBodyChange,
                            enabled = !isSubmitting,
                            label = { Text(strings.diffCommentBodyLabel) },
                            minLines = 6,
                            maxLines = 16,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
