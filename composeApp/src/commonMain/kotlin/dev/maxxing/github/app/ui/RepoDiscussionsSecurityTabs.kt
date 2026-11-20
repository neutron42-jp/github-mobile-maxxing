package dev.maxxing.github.app.ui

import dev.maxxing.github.ui.i18n.appStrings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Flame
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Send
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.X
import dev.maxxing.github.auth.openUrlInBrowser
import dev.maxxing.github.model.DependabotAlertSummary
import dev.maxxing.github.model.DiscussionCategory
import dev.maxxing.github.model.DiscussionComment
import dev.maxxing.github.model.DiscussionDetail
import dev.maxxing.github.model.DiscussionListData
import dev.maxxing.github.model.DiscussionSummary
import dev.maxxing.github.model.RepoSecurityOverview
import dev.maxxing.github.model.SecurityAdvisorySummary
import dev.maxxing.github.ui.components.ExpressiveStatusPill
import dev.maxxing.github.ui.components.GitHubMarkdown
import dev.maxxing.github.ui.components.MaxxingFullScreenEditorScaffold
import dev.maxxing.github.ui.components.MaxxingLoadingState
import dev.maxxing.github.ui.components.UserAvatar
import dev.maxxing.github.ui.components.expressiveAnimateContentSize

@Composable
internal fun RepoDiscussionsTabContent(
    data: DiscussionListData?,
    selectedCategoryId: String?,
    onSelectCategory: (String?) -> Unit,
    isLoading: Boolean,
    errorMsg: String?,
    canParticipate: Boolean = true,
    onRefresh: () -> Unit,
    onOpenCreateDiscussion: () -> Unit,
    onOpenDiscussion: (DiscussionSummary) -> Unit,
    onToggleUpvote: (DiscussionSummary) -> Unit
) {
    val categories = data?.categories.orEmpty()
    val discussions = data?.discussions

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (discussions != null) appStrings().discussionsCount(discussions.size) else appStrings().discussionsHeader,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            if (canParticipate && categories.isNotEmpty() && !data?.repositoryId.isNullOrBlank()) {
                OutlinedButton(
                    onClick = onOpenCreateDiscussion,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Lucide.Plus, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(appStrings().newAction, maxLines = 1, softWrap = false)
                }
            }
            IconButton(onClick = onRefresh) {
                Icon(Lucide.RefreshCw, contentDescription = appStrings().refreshDiscussionsDesc)
            }
        }

        if (categories.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item(key = "cat_all") {
                    FilterChip(
                        selected = selectedCategoryId == null,
                        onClick = { onSelectCategory(null) },
                        label = { Text(appStrings().discussionCategoryAll, maxLines = 1, softWrap = false) }
                    )
                }
                items(categories, key = { it.id }) { cat ->
                    FilterChip(
                        selected = selectedCategoryId == cat.id,
                        onClick = { onSelectCategory(cat.id) },
                        label = { Text(cat.name, maxLines = 1, softWrap = false) }
                    )
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = isLoading && discussions != null,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                isLoading && discussions == null -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        MaxxingLoadingState(message = appStrings().discussionsLoading)
                    }
                }
                errorMsg != null && discussions.isNullOrEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(errorMsg, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(10.dp))
                        FilledTonalButton(onClick = onRefresh) { Text(appStrings().retry) }
                    }
                }
                discussions.isNullOrEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Lucide.MessageSquare,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = if (categories.isEmpty()) appStrings().discussionsDisabledOrEmpty else appStrings().discussionsEmpty,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = appStrings().discussionsHint,
                            style = MaterialTheme.typography.bodySmall,
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
                        items(items = discussions, key = { it.id }, contentType = { "discussion" }) { discussion ->
                            DiscussionCard(
                                discussion = discussion,
                                onClick = { onOpenDiscussion(discussion) },
                                onToggleUpvote = { onToggleUpvote(discussion) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiscussionCard(
    discussion: DiscussionSummary,
    onClick: () -> Unit,
    onToggleUpvote: () -> Unit
) {
    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = discussion.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = discussion.category.name,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                        if (discussion.isAnswered) {
                            ExpressiveStatusPill(
                                label = appStrings().discussionAnswered,
                                icon = Lucide.CircleCheck,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        if (discussion.isClosed) {
                            ExpressiveStatusPill(
                                label = appStrings().statusClosed,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                FilterChip(
                    selected = discussion.viewerHasUpvoted,
                    onClick = onToggleUpvote,
                    leadingIcon = { Icon(Lucide.Flame, contentDescription = appStrings().discussionUpvoteDesc, modifier = Modifier.size(14.dp)) },
                    label = { Text("${discussion.upvoteCount}") }
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UserAvatar(
                    avatarUrl = discussion.authorAvatarUrl,
                    login = discussion.authorLogin,
                    sizeDp = 18
                )
                Text(
                    text = "@${discussion.authorLogin} · #${discussion.number}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Lucide.MessageSquare,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${discussion.commentsCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun DiscussionDetailView(
    detail: DiscussionDetail?,
    isLoading: Boolean,
    commentBusy: Boolean,
    errorMsg: String?,
    commentError: String? = null,
    canParticipate: Boolean = true,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggleUpvote: (DiscussionSummary) -> Unit,
    onSubmitComment: (body: String, replyToId: String?, onSuccess: () -> Unit) -> Unit
) {
    var commentText by remember { mutableStateOf("") }
    var replyTarget by remember { mutableStateOf<DiscussionComment?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Lucide.ArrowLeft, contentDescription = appStrings().backToDiscussionsDesc)
            }
            Text(
                text = detail?.summary?.let { "#${it.number} ${it.title}" } ?: appStrings().subjectDiscussion,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            detail?.summary?.htmlUrl?.takeIf { it.isNotBlank() }?.let { url ->
                IconButton(onClick = { openUrlInBrowser(url) }) {
                    Icon(Lucide.ExternalLink, contentDescription = appStrings().openInBrowser)
                }
            }
            IconButton(onClick = onRefresh) {
                Icon(Lucide.RefreshCw, contentDescription = appStrings().refreshDiscussionDesc)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        when {
            isLoading && detail == null -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    MaxxingLoadingState(message = appStrings().discussionLoading)
                }
            }
            errorMsg != null && detail == null -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(errorMsg, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(10.dp))
                    FilledTonalButton(onClick = onRefresh) { Text(appStrings().retry) }
                }
            }
            detail != null -> {
                val summary = detail.summary
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "header") {
                        ElevatedCard(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(summary.title, style = MaterialTheme.typography.titleMedium)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    UserAvatar(summary.authorAvatarUrl, summary.authorLogin, sizeDp = 22)
                                    Text(
                                        text = appStrings().discussionAuthorInCategory(summary.authorLogin, summary.category.name),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterChip(
                                        selected = summary.viewerHasUpvoted,
                                        onClick = { onToggleUpvote(summary) },
                                        leadingIcon = { Icon(Lucide.Flame, contentDescription = appStrings().discussionUpvoteDesc, modifier = Modifier.size(14.dp)) },
                                        label = { Text("${summary.upvoteCount}") }
                                    )
                                }
                                if (summary.body.isNotBlank()) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    GitHubMarkdown(content = summary.body, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }

                    item(key = "comments_header") {
                        Text(
                            text = appStrings().discussionRepliesCount(detail.comments.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    items(items = detail.comments, key = { it.id }) { comment ->
                        ElevatedCard(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = if (comment.isAnswer) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainer
                                }
                            ),
                            modifier = Modifier.fillMaxWidth().expressiveAnimateContentSize()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    UserAvatar(comment.authorAvatarUrl, comment.authorLogin, sizeDp = 20)
                                    Text(
                                        text = "@${comment.authorLogin}",
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (comment.isAnswer) {
                                        ExpressiveStatusPill(
                                            label = appStrings().discussionAnswer,
                                            icon = Lucide.CircleCheck,
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    if (canParticipate) {
                                        TextButton(onClick = { replyTarget = comment }) {
                                            Text(appStrings().discussionReplyAction)
                                        }
                                    }
                                }
                                GitHubMarkdown(content = comment.body, modifier = Modifier.fillMaxWidth())
                                if (comment.replies.isNotEmpty()) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    Column(
                                        modifier = Modifier.padding(start = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        comment.replies.forEach { reply ->
                                            Row(
                                                verticalAlignment = Alignment.Top,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                UserAvatar(reply.authorAvatarUrl, reply.authorLogin, sizeDp = 16)
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "@${reply.authorLogin}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    GitHubMarkdown(content = reply.body, modifier = Modifier.fillMaxWidth())
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (canParticipate) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (!commentError.isNullOrBlank()) {
                            Text(
                                text = commentError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        if (replyTarget != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = appStrings().discussionReplyingTo(replyTarget!!.authorLogin),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    enabled = !commentBusy,
                                    onClick = { replyTarget = null },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Lucide.X, contentDescription = appStrings().cancelReplyDesc, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = commentText,
                                onValueChange = { commentText = it },
                                enabled = !commentBusy,
                                placeholder = { Text(if (replyTarget != null) appStrings().discussionWriteReply else appStrings().discussionAddComment) },
                                modifier = Modifier.weight(1f),
                                maxLines = 4
                            )
                            FilledTonalButton(
                                enabled = commentText.trim().isNotBlank() && !commentBusy,
                                onClick = {
                                    val textToSend = commentText.trim()
                                    val targetId = replyTarget?.id
                                    onSubmitComment(textToSend, targetId) {
                                        commentText = ""
                                        replyTarget = null
                                    }
                                }
                            ) {
                                Icon(Lucide.Send, contentDescription = appStrings().sendCommentDesc, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CreateDiscussionDialog(
    categories: List<DiscussionCategory>,
    isSubmitting: Boolean,
    errorMsg: String?,
    onDismiss: () -> Unit,
    onCreate: (categoryId: String, title: String, body: String) -> Unit
) {
    var selectedCategoryId by remember(categories) { mutableStateOf(categories.firstOrNull()?.id ?: "") }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    MaxxingFullScreenEditorScaffold(
        title = appStrings().discussionNewTitle,
        saveLabel = if (isSubmitting) appStrings().creating else appStrings().create,
        canSave = selectedCategoryId.isNotBlank() && title.trim().isNotBlank(),
        isSubmitting = isSubmitting,
        errorMsg = errorMsg,
        bodyValue = body,
        onBodyChange = { body = it },
        bodyLabel = appStrings().discussionBodyLabel,
        supportsMarkdownPreview = true,
        onDismiss = onDismiss,
        onSave = { onCreate(selectedCategoryId, title.trim(), body.trim()) },
        headerContent = {
            Text(appStrings().discussionCategoryLabel, style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategoryId == cat.id,
                        onClick = { selectedCategoryId = cat.id },
                        label = { Text(cat.name) }
                    )
                }
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                enabled = !isSubmitting,
                label = { Text(appStrings().repoIssueTitleLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}

@Composable
internal fun RepoSecurityTabContent(
    overview: RepoSecurityOverview?,
    isLoading: Boolean,
    errorMsg: String?,
    onRefresh: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = appStrings().securityHeader,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRefresh) {
                Icon(Lucide.RefreshCw, contentDescription = appStrings().refreshSecurityDesc)
            }
        }

        PullToRefreshBox(
            isRefreshing = isLoading && overview != null,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                isLoading && overview == null -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        MaxxingLoadingState(message = appStrings().securityChecking)
                    }
                }
                errorMsg != null && overview == null -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(errorMsg, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(10.dp))
                        FilledTonalButton(onClick = onRefresh) { Text(appStrings().retry) }
                    }
                }
                overview != null -> {
                    val alertsNotice = appStrings().dependabotAlertsNoticeText(overview)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        alertsNotice?.let { notice ->
                            item(key = "notice") {
                                Surface(
                                    shape = MaterialTheme.shapes.large,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            Lucide.ShieldCheck,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = notice,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        if (overview.alerts.isNotEmpty()) {
                            item(key = "alerts_header") {
                                Text(
                                    text = appStrings().securityDependabotCount(overview.alerts.size),
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            items(overview.alerts, key = { "alert_${it.number}" }) { alert ->
                                DependabotAlertCard(alert)
                            }
                        }

                        if (overview.advisories.isNotEmpty()) {
                            item(key = "advisories_header") {
                                Text(
                                    text = appStrings().securityAdvisoriesCount(overview.advisories.size),
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            items(overview.advisories, key = { "adv_${it.ghsaId}" }) { advisory ->
                                SecurityAdvisoryCard(advisory)
                            }
                        }

                        if (overview.alerts.isEmpty() && overview.advisories.isEmpty() && overview.alertsAccessNotice == null) {
                            item(key = "all_clear") {
                                ElevatedCard(
                                    shape = MaterialTheme.shapes.large,
                                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Lucide.ShieldCheck,
                                            contentDescription = null,
                                            modifier = Modifier.size(36.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(appStrings().securityAllClearTitle, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            appStrings().securityAllClearDesc,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DependabotAlertCard(alert: DependabotAlertSummary) {
    val (bgColor, fgColor) = severityColors(alert.severity)
    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = alert.htmlUrl.isNotBlank()) { openUrlInBrowser(alert.htmlUrl) }
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = alert.summary,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                ExpressiveStatusPill(
                    label = appStrings().formatSeverityLabel(alert.severity),
                    containerColor = bgColor,
                    contentColor = fgColor
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                    Text(
                        text = buildString {
                            append(alert.packageName)
                            if (alert.ecosystem.isNotBlank()) append(" (${alert.ecosystem})")
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                if (alert.vulnerableVersionRange.isNotBlank()) {
                    Text(
                        text = appStrings().securityAffected(alert.vulnerableVersionRange),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                alert.patchedVersion?.takeIf { it.isNotBlank() }?.let { fixed ->
                    Text(
                        text = appStrings().securityFix(fixed),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun SecurityAdvisoryCard(advisory: SecurityAdvisorySummary) {
    val (bgColor, fgColor) = severityColors(advisory.severity)
    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = advisory.htmlUrl.isNotBlank()) { openUrlInBrowser(advisory.htmlUrl) }
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = advisory.ghsaId,
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                ExpressiveStatusPill(
                    label = appStrings().formatSeverityLabel(advisory.severity),
                    containerColor = bgColor,
                    contentColor = fgColor
                )
            }
            Text(advisory.summary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun severityColors(severity: String) = when (severity.lowercase()) {
    "critical", "high" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    "medium", "moderate" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
}
