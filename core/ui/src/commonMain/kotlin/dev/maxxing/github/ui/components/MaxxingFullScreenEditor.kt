package dev.maxxing.github.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Paperclip
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.X
import dev.maxxing.github.ui.i18n.AppStrings
import dev.maxxing.github.ui.i18n.appStrings

data class EditorTextMetrics(
    val lines: Int,
    val characters: Int
)

fun measureEditorText(text: String): EditorTextMetrics {
    if (text.isEmpty()) return EditorTextMetrics(lines = 0, characters = 0)
    return EditorTextMetrics(
        lines = text.count { it == '\n' } + 1,
        characters = text.length
    )
}

fun formatEditorMetrics(text: String, strings: AppStrings): String {
    val metrics = measureEditorText(text)
    return strings.formatEditorMetricsLabel(metrics.lines, metrics.characters)
}

/**
 * Appends a markdown attachment snippet cleanly onto [currentBody], separating from
 * preceding text with a blank line when appropriate.
 */
fun appendAttachmentMarkdown(currentBody: String, snippet: String): String {
    val cleanSnippet = snippet.trim()
    if (cleanSnippet.isEmpty()) return currentBody
    if (currentBody.isBlank()) return cleanSnippet
    val separator = when {
        currentBody.endsWith("\n\n") -> ""
        currentBody.endsWith("\n") -> "\n"
        else -> "\n\n"
    }
    return "$currentBody$separator$cleanSnippet"
}

/**
 * Full-screen editor surface replacing cramped modal dialogs for file, Gist,
 * issue, PR, release, discussion, and comment editing.
 */
@Composable
fun MaxxingFullScreenEditorScaffold(
    title: String,
    saveLabel: String,
    canSave: Boolean,
    isSubmitting: Boolean,
    bodyValue: String,
    onBodyChange: (String) -> Unit,
    bodyLabel: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    errorMsg: String? = null,
    bodyPlaceholder: String? = null,
    useMonospace: Boolean = false,
    supportsMarkdownPreview: Boolean = false,
    bodyEditable: Boolean = true,
    metadataSummary: String? = null,
    initiallyMetadataExpanded: Boolean = true,
    onAttachMedia: (() -> Unit)? = null,
    isAttachingMedia: Boolean = false,
    headerContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    var metadataExpanded by remember(title, initiallyMetadataExpanded) {
        mutableStateOf(initiallyMetadataExpanded)
    }
    var isPreviewMode by remember { mutableStateOf(false) }
    val strings = appStrings()

    MaxxingPredictiveBackContainer(
        enabled = true,
        onBack = { if (!isSubmitting) onDismiss() },
        modifier = modifier
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
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!subtitle.isNullOrBlank()) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (supportsMarkdownPreview) {
                            TextButton(
                                enabled = !isSubmitting,
                                onClick = { isPreviewMode = !isPreviewMode }
                            ) {
                                Icon(
                                    imageVector = if (isPreviewMode) Lucide.Pencil else Lucide.Eye,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(if (isPreviewMode) strings.writeMode else strings.previewMode, maxLines = 1, softWrap = false)
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                        Button(
                            enabled = canSave && !isSubmitting,
                            onClick = onSave
                        ) {
                            Text(saveLabel, maxLines = 1, softWrap = false)
                        }
                    }
                    if (isSubmitting) {
                        MaxxingWavyProgress()
                    }
                }
            }

            if (!errorMsg.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            if (headerContent != null) {
                if (metadataSummary != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { metadataExpanded = !metadataExpanded }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = metadataSummary,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (metadataExpanded) Lucide.ChevronUp else Lucide.ChevronDown,
                            contentDescription = if (metadataExpanded) strings.collapseDetails else strings.expandDetails,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                AnimatedVisibility(visible = metadataSummary == null || metadataExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        content = headerContent
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            }

            if (isPreviewMode && supportsMarkdownPreview) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    if (bodyValue.isBlank()) {
                        Text(
                            text = strings.nothingToPreview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        GitHubMarkdown(bodyValue)
                    }
                }
            } else {
                OutlinedTextField(
                    value = bodyValue,
                    onValueChange = onBodyChange,
                    enabled = !isSubmitting && bodyEditable,
                    readOnly = !bodyEditable,
                    label = { Text(bodyLabel) },
                    placeholder = bodyPlaceholder?.let { { Text(it) } },
                    textStyle = if (useMonospace) {
                        MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onAttachMedia != null) {
                        TextButton(
                            enabled = !isSubmitting && !isAttachingMedia,
                            onClick = onAttachMedia
                        ) {
                            if (isAttachingMedia) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Lucide.Paperclip,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (isAttachingMedia) strings.editorAttachingMedia else strings.editorAttachMedia,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    Text(
                        text = formatEditorMetrics(bodyValue, strings),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (useMonospace) strings.monospaceCodeHint else strings.markdownSupportedHint,
                        style = MaterialTheme.typography.labelSmall,
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
