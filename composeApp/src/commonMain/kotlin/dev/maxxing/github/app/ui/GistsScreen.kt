package dev.maxxing.github.app.ui

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.FileCode
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Star
import com.composables.icons.lucide.Trash2
import dev.maxxing.github.app.AppContainer
import dev.maxxing.github.auth.openUrlInBrowser
import dev.maxxing.github.model.AuthMethod
import dev.maxxing.github.model.GistDetail
import dev.maxxing.github.model.GistSummary
import dev.maxxing.github.ui.components.CodeViewer
import dev.maxxing.github.ui.components.ExpressiveStatusPill
import dev.maxxing.github.ui.components.GitHubMarkdown
import dev.maxxing.github.ui.components.MaxxingConnectedButtonGroup
import dev.maxxing.github.ui.components.MaxxingFilledIcons
import dev.maxxing.github.ui.components.MaxxingFullScreenEditorScaffold
import dev.maxxing.github.ui.components.MaxxingLoadingState
import dev.maxxing.github.ui.components.MaxxingPredictiveOverlayHost
import dev.maxxing.github.ui.components.MaxxingWavyProgress
import dev.maxxing.github.ui.components.StarActionButton
import dev.maxxing.github.ui.components.UserAvatar
import dev.maxxing.github.ui.i18n.AppStrings
import dev.maxxing.github.ui.i18n.appStrings
import kotlinx.coroutines.launch

internal enum class GistsTab {
    MINE,
    STARRED;

    fun localizedLabel(strings: AppStrings): String = when (this) {
        MINE -> strings.gistsTabMine
        STARRED -> strings.gistsTabStarred
    }
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun GistsScreen(
    container: AppContainer,
    userLogin: String? = null,
    onBack: () -> Unit,
    onMessage: (String) -> Unit
) {
    val viewerProfile by container.viewerProfile.collectAsState()
    val session by container.sessionManager.session.collectAsState()
    val selfLogin = viewerProfile?.login ?: session?.userLogin
    val targetUser = userLogin?.takeIf { it.isNotBlank() && !it.equals(selfLogin, ignoreCase = true) }

    var activeTab by remember(targetUser) { mutableStateOf(GistsTab.MINE) }
    var gists by remember(targetUser, activeTab) {
        mutableStateOf(
            container.repository.peekGists(
                starred = targetUser == null && activeTab == GistsTab.STARRED,
                userLogin = targetUser
            )
        )
    }
    var selectedDetail by remember { mutableStateOf<GistDetail?>(null) }
    var isLoading by remember { mutableStateOf(gists == null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var requestSeq by remember { mutableStateOf(0) }

    var showEditorDialog by remember { mutableStateOf(false) }
    var editingDetail by remember { mutableStateOf<GistDetail?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var mutationBusy by remember { mutableStateOf(false) }
    var mutationError by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val strings = appStrings()

    fun copyGistLink(gist: GistSummary) {
        val shareUrl = resolveGistUrl(gist) ?: return
        clipboard.setText(AnnotatedString(shareUrl))
        onMessage(strings.copiedGistLink)
    }

    fun loadGists(forceRefresh: Boolean = false) {
        val isStarred = targetUser == null && activeTab == GistsTab.STARRED
        val seq = ++requestSeq
        val cached = container.repository.peekGists(starred = isStarred, userLogin = targetUser)
        if (cached != null) gists = cached
        if (forceRefresh) isRefreshing = true else if (cached == null) isLoading = true
        errorMsg = null
        scope.launch {
            container.repository.getGists(
                starred = isStarred,
                userLogin = targetUser,
                forceRefresh = forceRefresh
            ).onSuccess { list ->
                if (seq == requestSeq) gists = list
            }.onFailure { err ->
                if (seq == requestSeq) {
                    errorMsg = strings.errorMessage(err, strings.failedLoadGists, strings.gistScopeErrorHint)
                    if (forceRefresh) onMessage(errorMsg!!)
                }
            }
            if (seq == requestSeq) {
                isLoading = false
                isRefreshing = false
            }
        }
    }

    fun openGist(summary: GistSummary) {
        val cached = container.repository.peekGistDetail(summary.id)
        selectedDetail = cached ?: GistDetail(summary = summary, files = summary.files)
        scope.launch {
            container.repository.getGistDetail(summary.id, forceRefresh = cached == null)
                .onSuccess { detail ->
                    if (selectedDetail?.summary?.id == detail.summary.id) {
                        selectedDetail = detail
                    }
                }
                .onFailure { err -> onMessage(strings.errorMessage(err, strings.failedLoadGistDetails, strings.gistScopeErrorHint)) }
        }
    }

    LaunchedEffect(targetUser, activeTab) {
        loadGists(forceRefresh = false)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val headerTracker = remember { RepoHeaderCollapseTracker() }
        val headerNestedScroll = remember(headerTracker) { headerTracker.toNestedScrollConnection() }
        val headerVerticalPadding by animateDpAsState(if (headerTracker.isCollapsed) 4.dp else 10.dp)

        LaunchedEffect(targetUser, activeTab) {
            headerTracker.expand()
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = headerVerticalPadding)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Lucide.ArrowLeft, contentDescription = strings.back)
                        }
                        Text(
                            text = if (targetUser != null) strings.userGistsTitle(targetUser) else strings.gistsTitle,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (targetUser == null) {
                            FilledTonalButton(
                                onClick = {
                                    editingDetail = null
                                    mutationError = null
                                    showEditorDialog = true
                                }
                            ) {
                                Icon(Lucide.Plus, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(strings.newAction)
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                        IconButton(onClick = { loadGists(forceRefresh = true) }) {
                            Icon(Lucide.RefreshCw, contentDescription = strings.refresh, modifier = Modifier.size(18.dp))
                        }
                    }

                    if (targetUser == null) {
                        MaxxingCollapsibleSection(visible = !headerTracker.isCollapsed) {
                            Column {
                                Spacer(Modifier.height(6.dp))
                                MaxxingConnectedButtonGroup(
                                    options = GistsTab.entries,
                                    selected = activeTab,
                                    onSelect = { activeTab = it },
                                    labelProvider = { it.localizedLabel(strings) },
                                    iconProvider = { tab ->
                                        when (tab) {
                                            GistsTab.MINE -> Lucide.FileCode
                                            GistsTab.STARRED -> if (activeTab == GistsTab.STARRED) {
                                                MaxxingFilledIcons.Star
                                            } else {
                                                Lucide.Star
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    if (isLoading || isRefreshing) {
                        Spacer(Modifier.height(6.dp))
                        MaxxingWavyProgress()
                    }
                }
            }

            val missingGistScope = targetUser == null &&
                session?.authMethod == AuthMethod.DEVICE_FLOW &&
                session?.scope
                    ?.split(' ', ',')
                    ?.map { it.trim().lowercase() }
                    ?.contains("gist") == false
            if (missingGistScope) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = strings.missingGistScopeNotice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { loadGists(forceRefresh = true) },
                modifier = Modifier.fillMaxSize().nestedScroll(headerNestedScroll)
            ) {
                val list = gists
                when {
                    isLoading && list == null -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            MaxxingLoadingState(message = strings.loadingGists)
                        }
                    }
                    errorMsg != null && list.isNullOrEmpty() -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(10.dp))
                            FilledTonalButton(onClick = { loadGists(forceRefresh = true) }) { Text(strings.retry) }
                        }
                    }
                    list.isNullOrEmpty() -> {
                        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Lucide.FileCode, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(strings.noGistsFound, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(items = list, key = { it.id }) { summary ->
                                GistSummaryCard(
                                    gist = summary,
                                    onClick = { openGist(summary) },
                                    onCopyLink = { copyGistLink(summary) }
                                )
                            }
                        }
                    }
                }
            }
        }

        MaxxingPredictiveOverlayHost(
            target = selectedDetail,
            onBack = { selectedDetail = null },
            contentKey = { it.summary.id }
        ) { opened ->
            GistDetailView(
                detail = opened,
                isOwner = selfLogin != null && opened.summary.ownerLogin.equals(selfLogin, ignoreCase = true),
                onBack = { selectedDetail = null },
                onToggleStar = {
                    val targetStar = !opened.viewerHasStarred
                    selectedDetail = opened.copy(viewerHasStarred = targetStar)
                    scope.launch {
                        container.repository.setGistStarred(opened.summary.id, targetStar)
                            .onSuccess {
                                onMessage(if (targetStar) strings.starredGist else strings.unstarredGist)
                            }
                            .onFailure { err ->
                                selectedDetail = opened
                                onMessage(strings.errorMessage(err, strings.gistStarUpdateFailed, strings.gistScopeErrorHint))
                            }
                    }
                },
                onEdit = {
                    editingDetail = opened
                    mutationError = null
                    showEditorDialog = true
                },
                onCopyLink = { copyGistLink(opened.summary) },
                onDelete = {
                    mutationError = null
                    showDeleteConfirm = true
                }
            )
        }

        val opened = selectedDetail

        if (showEditorDialog) {
            val editing = editingDetail
            val primaryFile = editing?.files?.firstOrNull()
            val isTruncatedOrIncomplete = primaryFile != null &&
                (primaryFile.truncated || (primaryFile.size > 0L && primaryFile.content.isEmpty()))
            val selectedGist = selectedDetail?.takeIf { it.summary.id == editing?.summary?.id }
            val isFileListTruncated = editing?.summary?.filesTruncated == true ||
                selectedGist?.summary?.filesTruncated == true
            val existingFilenames = (selectedGist ?: editing)?.files?.map { it.filename }?.toSet().orEmpty()
            GistFullScreenEditor(
                isCreating = editing == null,
                initialDescription = editing?.summary?.description.orEmpty(),
                initialFilename = primaryFile?.filename ?: "snippet.txt",
                initialContent = primaryFile?.content.orEmpty(),
                initialPublic = editing?.summary?.isPublic ?: false,
                isTruncated = isTruncatedOrIncomplete,
                isFileListTruncated = isFileListTruncated,
                existingFilenames = existingFilenames,
                isSubmitting = mutationBusy,
                errorMsg = mutationError,
                onDismiss = { if (!mutationBusy) showEditorDialog = false },
                onSave = { desc, filename, content, isPublic ->
                    scope.launch {
                        mutationBusy = true
                        mutationError = null
                        val result = if (editing == null) {
                            container.repository.createGist(
                                description = desc,
                                isPublic = isPublic,
                                files = mapOf(filename to content)
                            )
                        } else {
                            val originalName = primaryFile?.filename
                            val changesFilename = !isTruncatedOrIncomplete && filename != originalName
                            val currentDetail = if (changesFilename && !isFileListTruncated) {
                                container.repository.getGistDetail(editing.summary.id, forceRefresh = true)
                            } else {
                                Result.success(editing)
                            }
                            currentDetail.fold(
                                onSuccess = { current ->
                                    if (changesFilename && selectedDetail?.summary?.id == current.summary.id) {
                                        selectedDetail = current
                                    }
                                    val currentFilenames = current.files.map { it.filename }.toSet()
                                    buildGistFileUpdates(
                                        originalName = originalName,
                                        originalContent = primaryFile?.content.orEmpty(),
                                        newFilename = filename,
                                        newContent = content,
                                        existingFilenames = currentFilenames,
                                        isTruncated = isTruncatedOrIncomplete,
                                        isFileListTruncated = isFileListTruncated || current.summary.filesTruncated
                                    ).fold(
                                        onSuccess = { updates ->
                                            container.repository.updateGist(
                                                gistId = editing.summary.id,
                                                description = desc,
                                                updatedFiles = updates
                                            )
                                        },
                                        onFailure = { err -> Result.failure(err) }
                                    )
                                },
                                onFailure = { err -> Result.failure(err) }
                            )
                        }
                        result.onSuccess { updated ->
                            mutationBusy = false
                            showEditorDialog = false
                            selectedDetail = updated
                            onMessage(if (editing == null) strings.gistCreatedMessage else strings.gistUpdatedMessage)
                            loadGists(forceRefresh = true)
                        }.onFailure { err ->
                            mutationBusy = false
                            mutationError = when (err) {
                                is DuplicateGistFilenameException -> strings.gistDuplicateFilenameError(err.filename)
                                is GistFileListIncompleteException -> strings.gistFileListTruncatedNotice
                                else -> strings.errorMessage(err, strings.gistSaveFailed, strings.gistScopeErrorHint)
                            }
                        }
                    }
                }
            )
        }

        if (showDeleteConfirm && opened != null) {
            AlertDialog(
                onDismissRequest = { if (!mutationBusy) showDeleteConfirm = false },
                title = { Text(strings.gistDeleteTitle) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(strings.gistDeleteConfirm(opened.files.firstOrNull()?.filename ?: opened.summary.id))
                        if (!mutationError.isNullOrBlank()) {
                            Text(mutationError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        enabled = !mutationBusy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        onClick = {
                            scope.launch {
                                mutationBusy = true
                                mutationError = null
                                container.repository.deleteGist(opened.summary.id)
                                    .onSuccess {
                                        mutationBusy = false
                                        showDeleteConfirm = false
                                        selectedDetail = null
                                        onMessage(strings.gistDeletedMessage)
                                        loadGists(forceRefresh = true)
                                    }
                                    .onFailure { err ->
                                        mutationBusy = false
                                        mutationError = strings.errorMessage(err, strings.gistDeleteFailed, strings.gistScopeErrorHint)
                                    }
                            }
                        }
                    ) {
                        Text(if (mutationBusy) strings.deletingAction else strings.deleteAction)
                    }
                },
                dismissButton = {
                    TextButton(enabled = !mutationBusy, onClick = { showDeleteConfirm = false }) { Text(strings.cancel) }
                }
            )
        }
    }
}

internal fun resolveGistUrl(gist: GistSummary): String? {
    val htmlUrl = gist.htmlUrl.trim()
    if (htmlUrl.isNotEmpty()) return htmlUrl
    val id = gist.id.trim()
    if (id.isEmpty()) return null
    val owner = gist.ownerLogin.trim()
    return if (owner.isNotEmpty()) {
        "https://gist.github.com/$owner/$id"
    } else {
        "https://gist.github.com/$id"
    }
}

@Composable
private fun GistSummaryCard(
    gist: GistSummary,
    onClick: () -> Unit,
    onCopyLink: () -> Unit
) {
    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (gist.ownerLogin.isNotBlank()) {
                    UserAvatar(avatarUrl = gist.ownerAvatarUrl, login = gist.ownerLogin, sizeDp = 24)
                }
                val primaryName = gist.files.firstOrNull()?.filename ?: gist.id
                Text(
                    text = primaryName,
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                ExpressiveStatusPill(
                    label = if (gist.isPublic) appStrings().gistPublicBadge else appStrings().gistSecretBadge,
                    icon = if (gist.isPublic) Lucide.Globe else Lucide.Lock,
                    containerColor = if (gist.isPublic) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    contentColor = if (gist.isPublic) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            if (gist.description.isNotBlank()) {
                Text(
                    text = gist.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = appStrings().gistFileCount(gist.files.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val lang = gist.files.firstOrNull()?.language
                if (!lang.isNullOrBlank()) {
                    Text(lang, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                if (gist.commentsCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Lucide.MessageSquare, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${gist.commentsCount}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (resolveGistUrl(gist) != null) {
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = onCopyLink,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Lucide.Copy,
                            contentDescription = appStrings().copyGistLinkDesc,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GistDetailView(
    detail: GistDetail,
    isOwner: Boolean,
    onBack: () -> Unit,
    onToggleStar: () -> Unit,
    onEdit: () -> Unit,
    onCopyLink: () -> Unit,
    onDelete: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Lucide.ArrowLeft, contentDescription = appStrings().back)
                    }
                    Text(
                        text = detail.files.firstOrNull()?.filename ?: detail.summary.id,
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    StarActionButton(isStarred = detail.viewerHasStarred, onClick = onToggleStar)
                    if (isOwner) {
                        IconButton(onClick = onEdit) {
                            Icon(Lucide.Pencil, contentDescription = appStrings().gistEditAction, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onDelete) {
                            Icon(
                                Lucide.Trash2,
                                contentDescription = appStrings().gistDeleteAction,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    val gistUrl = resolveGistUrl(detail.summary)
                    if (gistUrl != null) {
                        IconButton(onClick = onCopyLink) {
                            Icon(Lucide.Copy, contentDescription = appStrings().copyGistLinkDesc, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { openUrlInBrowser(gistUrl) }) {
                            Icon(Lucide.ExternalLink, contentDescription = appStrings().openInBrowser, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                if (detail.summary.description.isNotBlank()) {
                    Text(
                        text = detail.summary.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items = detail.files, key = { it.filename }) { file ->
                ElevatedCard(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = file.filename,
                                style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.weight(1f)
                            )
                            file.language?.takeIf { it.isNotBlank() }?.let { lang ->
                                Text(
                                    text = lang,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (file.filename.endsWith(".md", ignoreCase = true)) {
                            GitHubMarkdown(file.content)
                        } else {
                            CodeViewer(
                                code = file.content,
                                filename = file.filename,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 420.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

internal class DuplicateGistFilenameException(val filename: String) :
    IllegalArgumentException("A file named \"$filename\" already exists in this gist")

/** Raised when a rename is attempted although GitHub returned only part of the Gist's file list. */
internal class GistFileListIncompleteException :
    IllegalStateException("Cannot rename a Gist file while GitHub's file list is incomplete")

/**
 * Computes the `files` patch map for a Gist update while:
 * 1. Rejecting renames that collide with another existing file in the Gist (#86).
 * 2. Leaving files untouched when the file is truncated/incomplete or when only the description changed (#85).
 * 3. Rejecting renames when the API truncated the file list, because unreturned names cannot be checked.
 */
internal fun buildGistFileUpdates(
    originalName: String?,
    originalContent: String,
    newFilename: String,
    newContent: String,
    existingFilenames: Set<String>,
    isTruncated: Boolean,
    isFileListTruncated: Boolean = false
): Result<Map<String, String?>> {
    val cleanName = newFilename.trim()
    if (isTruncated) {
        return Result.success(emptyMap())
    }
    val renamesFile = originalName == null || cleanName != originalName
    if (isFileListTruncated && renamesFile) {
        return Result.failure(GistFileListIncompleteException())
    }
    if (originalName != null && cleanName != originalName && cleanName in existingFilenames) {
        return Result.failure(DuplicateGistFilenameException(cleanName))
    }
    if (originalName != null && cleanName == originalName && newContent == originalContent) {
        return Result.success(emptyMap())
    }
    val updates = buildMap<String, String?> {
        if (originalName != null && originalName != cleanName) {
            put(originalName, null)
        }
        put(cleanName, newContent)
    }
    return Result.success(updates)
}

/**
 * Whether the Gist editor offers a save that the update helper can apply.
 * A body truncated by the API allows a description-only save, and an incomplete file list
 * allows description/content edits but never a rename.
 */
internal fun canSaveGistEdit(
    isCreating: Boolean,
    isBodyTruncated: Boolean,
    isFileListTruncated: Boolean,
    initialDescription: String,
    description: String,
    initialFilename: String,
    filename: String,
    initialContent: String,
    content: String,
    existingFilenames: Set<String>
): Boolean {
    val descriptionChanged = description.trim() != initialDescription.trim()
    val contentChanged = content != initialContent
    val cleanName = filename.trim()
    return when {
        isBodyTruncated -> descriptionChanged
        isFileListTruncated -> cleanName == initialFilename &&
            (!contentChanged || content.isNotBlank()) && (descriptionChanged || contentChanged)
        else -> {
            val duplicateConflict = !isCreating &&
                cleanName != initialFilename &&
                cleanName in existingFilenames
            cleanName.isNotEmpty() && content.isNotBlank() && !duplicateConflict
        }
    }
}

@Composable
private fun GistFullScreenEditor(
    isCreating: Boolean,
    initialDescription: String,
    initialFilename: String,
    initialContent: String,
    initialPublic: Boolean,
    isTruncated: Boolean = false,
    isFileListTruncated: Boolean = false,
    existingFilenames: Set<String> = emptySet(),
    isSubmitting: Boolean,
    errorMsg: String?,
    onDismiss: () -> Unit,
    onSave: (description: String, filename: String, content: String, isPublic: Boolean) -> Unit
) {
    var description by remember(initialDescription) { mutableStateOf(initialDescription) }
    var filename by remember(initialFilename) { mutableStateOf(initialFilename) }
    var content by remember(initialContent) { mutableStateOf(initialContent) }
    var isPublic by remember(initialPublic) { mutableStateOf(initialPublic) }
    val renameBlocked = isTruncated || isFileListTruncated
    val editableFilename = if (renameBlocked) initialFilename else filename
    val trimmedFilename = editableFilename.trim()
    val duplicateConflict = !isCreating &&
        !renameBlocked &&
        trimmedFilename != initialFilename &&
        trimmedFilename in existingFilenames
    val effectiveError = when {
        duplicateConflict -> appStrings().gistDuplicateFilenameError(trimmedFilename)
        !errorMsg.isNullOrBlank() -> errorMsg
        isTruncated -> appStrings().gistTruncatedNotice
        isFileListTruncated -> appStrings().gistFileListTruncatedNotice
        else -> null
    }
    val canSave = canSaveGistEdit(
        isCreating = isCreating,
        isBodyTruncated = isTruncated,
        isFileListTruncated = isFileListTruncated,
        initialDescription = initialDescription,
        description = description,
        initialFilename = initialFilename,
        filename = editableFilename,
        initialContent = initialContent,
        content = content,
        existingFilenames = existingFilenames
    )

    MaxxingFullScreenEditorScaffold(
        title = if (isCreating) appStrings().gistNewTitle else appStrings().gistEditTitle,
        subtitle = editableFilename.ifBlank { "snippet.txt" },
        saveLabel = if (isSubmitting) appStrings().savingAction else if (isCreating) appStrings().create else appStrings().save,
        canSave = canSave,
        isSubmitting = isSubmitting,
        errorMsg = effectiveError,
        bodyValue = content,
        onBodyChange = { content = it },
        bodyLabel = appStrings().gistContentsLabel,
        useMonospace = true,
        supportsMarkdownPreview = trimmedFilename.endsWith(".md", ignoreCase = true),
        bodyEditable = !isTruncated,
        metadataSummary = if (isCreating) {
            null
        } else {
            buildString {
                append(editableFilename.ifBlank { "snippet.txt" })
                if (description.isNotBlank()) append(" · $description")
            }
        },
        initiallyMetadataExpanded = isCreating || isTruncated || isFileListTruncated,
        onDismiss = onDismiss,
        onSave = { onSave(description, trimmedFilename, content, isPublic) },
        headerContent = {
            OutlinedTextField(
                value = editableFilename,
                onValueChange = { filename = it },
                enabled = !isSubmitting && !renameBlocked,
                label = { Text(appStrings().gistFilenameLabel) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                enabled = !isSubmitting,
                label = { Text(appStrings().repoDescLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (isCreating) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(appStrings().gistPublicToggle, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = if (isPublic) appStrings().gistPublicDesc else appStrings().gistSecretDesc,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = isPublic, onCheckedChange = { isPublic = it }, enabled = !isSubmitting)
                }
            }
        }
    )
}
