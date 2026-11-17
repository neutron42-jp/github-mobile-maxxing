package dev.maxxing.github.app.ui

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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.File
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Tag
import dev.maxxing.github.auth.openUrlInBrowser
import dev.maxxing.github.model.ReleaseAsset
import dev.maxxing.github.model.ReleaseSummary
import dev.maxxing.github.model.TagRef
import dev.maxxing.github.ui.components.ExpressiveStatusPill
import dev.maxxing.github.ui.components.GitHubMarkdown
import dev.maxxing.github.ui.components.MaxxingFullScreenEditorScaffold
import dev.maxxing.github.ui.components.MaxxingLoadingState
import dev.maxxing.github.ui.components.UserAvatar
import dev.maxxing.github.ui.components.expressiveAnimateContentSize
import dev.maxxing.github.ui.components.formatCompactCount
import dev.maxxing.github.ui.i18n.appStrings

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RepoReleasesTabContent(
    releases: List<ReleaseSummary>?,
    tags: List<TagRef>,
    isLoading: Boolean,
    errorMsg: String?,
    canCreateRelease: Boolean = true,
    onRefresh: () -> Unit,
    onOpenCreateRelease: () -> Unit,
    onSelectTag: (TagRef) -> Unit
) {
    val strings = appStrings()
    val latestStableId = remember(releases) {
        releases?.firstOrNull { !it.isDraft && !it.isPrerelease }?.id
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (releases != null) strings.releasesCount(releases.size) else strings.releasesHeader,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            if (canCreateRelease) {
                OutlinedButton(
                    onClick = onOpenCreateRelease,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Lucide.Plus, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.releasesNew, maxLines = 1, softWrap = false)
                }
            }
            IconButton(onClick = onRefresh) {
                Icon(Lucide.RefreshCw, contentDescription = strings.refreshReleasesDesc)
            }
        }

        if (tags.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(tags.take(20), key = { it.name }) { tag ->
                    AssistChip(
                        onClick = { onSelectTag(tag) },
                        leadingIcon = { Icon(Lucide.Tag, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        label = {
                            Text(
                                text = tag.name,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    )
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = isLoading && releases != null,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                isLoading && releases == null -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center
                    ) {
                        MaxxingLoadingState(message = strings.releasesLoading)
                    }
                }
                errorMsg != null && releases.isNullOrEmpty() -> {
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
                releases.isNullOrEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Lucide.Tag,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(strings.releasesNoneTitle, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (tags.isNotEmpty()) {
                                strings.releasesNoneWithTags(tags.size)
                            } else {
                                strings.releasesNoneEmpty
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(items = releases, key = { it.id }, contentType = { "release" }) { release ->
                            ReleaseCard(
                                release = release,
                                isLatest = release.id == latestStableId
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
private fun ReleaseCard(
    release: ReleaseSummary,
    isLatest: Boolean
) {
    val strings = appStrings()
    var notesExpanded by remember(release.id) { mutableStateOf(isLatest) }
    var assetsExpanded by remember(release.id) { mutableStateOf(isLatest || release.assets.size <= 3) }

    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().expressiveAnimateContentSize()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = release.name.ifBlank { release.tagName },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Lucide.Tag, contentDescription = null, modifier = Modifier.size(12.dp))
                                Text(
                                    text = release.tagName,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
                                )
                            }
                        }
                        if (isLatest) {
                            ExpressiveStatusPill(
                                label = strings.releaseLatest,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        if (release.isDraft) {
                            ExpressiveStatusPill(
                                label = strings.statusDraft,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (release.isPrerelease) {
                            ExpressiveStatusPill(
                                label = strings.releasePrerelease,
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
                if (release.htmlUrl.isNotBlank()) {
                    IconButton(onClick = { openUrlInBrowser(release.htmlUrl) }) {
                        Icon(Lucide.ExternalLink, contentDescription = strings.openReleaseInBrowserDesc)
                    }
                }
            }

            if (release.authorLogin.isNotBlank() || release.publishedAt.isNotBlank() || release.createdAt.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (release.authorLogin.isNotBlank()) {
                        UserAvatar(
                            avatarUrl = release.authorAvatarUrl,
                            login = release.authorLogin,
                            sizeDp = 20
                        )
                        Text(
                            text = "@${release.authorLogin}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val dateText = release.publishedAt.ifBlank { release.createdAt }.take(10)
                    if (dateText.isNotBlank()) {
                        Text(
                            text = dateText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (release.body.isNotBlank()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { notesExpanded = !notesExpanded }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (notesExpanded) strings.releaseHideNotes else strings.releaseShowNotes,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Lucide.ChevronDown, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                }
                if (notesExpanded) {
                    GitHubMarkdown(content = release.body, modifier = Modifier.fillMaxWidth())
                }
            }

            val hasSourceDownloads = release.zipballUrl.isNotBlank() || release.tarballUrl.isNotBlank()
            if (release.assets.isNotEmpty() || hasSourceDownloads) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { assetsExpanded = !assetsExpanded }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val totalItems = release.assets.size +
                        (if (release.zipballUrl.isNotBlank()) 1 else 0) +
                        (if (release.tarballUrl.isNotBlank()) 1 else 0)
                    Text(
                        text = strings.releaseAssetsCount(totalItems),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Lucide.ChevronDown, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                if (assetsExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        release.assets.forEach { asset ->
                            ReleaseAssetRow(asset = asset)
                        }
                        if (release.zipballUrl.isNotBlank()) {
                            SourceArchiveRow(
                                label = strings.releaseSourceZip,
                                onClick = { openUrlInBrowser(release.zipballUrl) }
                            )
                        }
                        if (release.tarballUrl.isNotBlank()) {
                            SourceArchiveRow(
                                label = strings.releaseSourceTarGz,
                                onClick = { openUrlInBrowser(release.tarballUrl) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseAssetRow(asset: ReleaseAsset) {
    val strings = appStrings()
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = asset.downloadUrl.isNotBlank()) {
                openUrlInBrowser(asset.downloadUrl)
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Lucide.File, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = asset.name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatByteSize(asset.sizeBytes)} · ${strings.releaseDownloadsCount(formatCompactCount(asset.downloadCount))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Lucide.ExternalLink,
                contentDescription = strings.downloadAssetDesc,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SourceArchiveRow(label: String, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Lucide.Folder, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
            Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Icon(Lucide.ExternalLink, contentDescription = appStrings().releaseDownloadArchiveDesc, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun CreateReleaseDialog(
    defaultTargetBranch: String,
    isSubmitting: Boolean,
    errorMsg: String?,
    onDismiss: () -> Unit,
    onCreate: (
        tagName: String,
        name: String,
        body: String,
        targetCommitish: String,
        isDraft: Boolean,
        isPrerelease: Boolean,
        generateNotes: Boolean
    ) -> Unit
) {
    var tagName by remember { mutableStateOf("") }
    var releaseTitle by remember { mutableStateOf("") }
    var targetBranch by remember { mutableStateOf(defaultTargetBranch) }
    var body by remember { mutableStateOf("") }
    var isDraft by remember { mutableStateOf(false) }
    var isPrerelease by remember { mutableStateOf(false) }
    var generateNotes by remember { mutableStateOf(true) }
    val strings = appStrings()

    MaxxingFullScreenEditorScaffold(
        title = strings.releaseCreateTitle,
        subtitle = targetBranch.takeIf { it.isNotBlank() }?.let { strings.releaseTargetBranchSubtitle(it) },
        saveLabel = if (isSubmitting) strings.releasePublishing else if (isDraft) strings.releaseSaveDraft else strings.releasePublish,
        canSave = tagName.trim().isNotBlank(),
        isSubmitting = isSubmitting,
        errorMsg = errorMsg,
        bodyValue = body,
        onBodyChange = { body = it },
        bodyLabel = strings.releaseNotesLabel,
        supportsMarkdownPreview = true,
        onDismiss = onDismiss,
        onSave = {
            onCreate(
                tagName.trim(),
                releaseTitle.trim(),
                body.trim(),
                targetBranch.trim(),
                isDraft,
                isPrerelease,
                generateNotes
            )
        },
        headerContent = {
            OutlinedTextField(
                value = tagName,
                onValueChange = { tagName = it },
                enabled = !isSubmitting,
                label = { Text(strings.releaseTagLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = releaseTitle,
                onValueChange = { releaseTitle = it },
                enabled = !isSubmitting,
                label = { Text(strings.releaseTitleLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = targetBranch,
                onValueChange = { targetBranch = it },
                enabled = !isSubmitting,
                label = { Text(strings.releaseTargetLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(strings.releaseAutoNotes, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = generateNotes, onCheckedChange = { generateNotes = it }, enabled = !isSubmitting)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(strings.releasePrerelease, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = isPrerelease, onCheckedChange = { isPrerelease = it }, enabled = !isSubmitting)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(strings.releaseDraftToggle, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = isDraft, onCheckedChange = { isDraft = it }, enabled = !isSubmitting)
            }
        }
    )
}

private fun formatByteSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "${(bytes * 10 / 1024L) / 10.0} KB"
        bytes < 1024L * 1024L * 1024L -> "${(bytes * 10 / (1024L * 1024L)) / 10.0} MB"
        else -> "${(bytes * 10 / (1024L * 1024L * 1024L)) / 10.0} GB"
    }
}
