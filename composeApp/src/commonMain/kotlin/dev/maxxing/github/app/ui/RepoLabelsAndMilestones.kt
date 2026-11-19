package dev.maxxing.github.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.Flag
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Tag
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.X
import dev.maxxing.github.model.LabelTag
import dev.maxxing.github.model.MilestoneSummary
import dev.maxxing.github.ui.components.GitHubLabelChip
import dev.maxxing.github.ui.components.MaxxingLoadingState
import dev.maxxing.github.ui.components.MaxxingWavyProgress
import dev.maxxing.github.ui.i18n.appStrings

internal enum class RepoIssuesSection {
    ISSUES,
    LABELS,
    MILESTONES
}

internal data class MilestoneDueParseResult(
    val isoValue: String?,
    val isValid: Boolean
)

private val HEX_COLOR_REGEX = Regex("^[0-9A-Fa-f]{6}$")
private val DATE_ONLY_REGEX = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")
private val ISO_DATE_PREFIX_REGEX = Regex("^(\\d{4}-\\d{2}-\\d{2})(?:T.*)?$")

internal val PRESET_LABEL_COLORS = listOf(
    "d73a4a",
    "0075ca",
    "cfd3d7",
    "a2eeef",
    "7057ff",
    "008672",
    "e4e669",
    "d876e3",
    "fbca04",
    "b60205"
)

internal fun isValidHexColor(raw: String): Boolean {
    val clean = raw.trim().removePrefix("#")
    return HEX_COLOR_REGEX.matches(clean)
}

internal fun normalizeHexColor(raw: String, fallback: String = "6E40C9"): String {
    val clean = raw.trim().removePrefix("#")
    return if (HEX_COLOR_REGEX.matches(clean)) clean.lowercase() else fallback
}

internal fun formatMilestoneDueDate(dueOn: String?): String? {
    val trimmed = dueOn?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return ISO_DATE_PREFIX_REGEX.find(trimmed)?.groupValues?.get(1) ?: trimmed
}

internal fun parseMilestoneDueInput(rawDate: String): MilestoneDueParseResult {
    val trimmed = rawDate.trim()
    if (trimmed.isEmpty()) return MilestoneDueParseResult(isoValue = null, isValid = true)
    val match = DATE_ONLY_REGEX.matchEntire(trimmed)
        ?: return MilestoneDueParseResult(isoValue = null, isValid = false)
    val month = match.groupValues[2].toIntOrNull() ?: return MilestoneDueParseResult(null, false)
    val day = match.groupValues[3].toIntOrNull() ?: return MilestoneDueParseResult(null, false)
    if (month !in 1..12 || day !in 1..31) {
        return MilestoneDueParseResult(isoValue = null, isValid = false)
    }
    return MilestoneDueParseResult(isoValue = "${trimmed}T23:59:59Z", isValid = true)
}

internal fun computeMilestoneProgress(milestone: MilestoneSummary): Pair<Int, Int> {
    val open = milestone.openIssues.coerceAtLeast(0)
    val closed = milestone.closedIssues.coerceAtLeast(0)
    val total = open + closed
    val percent = if (total == 0) 0 else ((closed * 100) / total).coerceIn(0, 100)
    return percent to total
}

internal fun sortMilestonesForManagement(milestones: List<MilestoneSummary>): List<MilestoneSummary> =
    milestones.sortedWith(
        compareBy<MilestoneSummary>(
            { !it.state.equals("open", ignoreCase = true) },
            { it.dueOn.isNullOrBlank() },
            { it.dueOn.orEmpty() },
            { -it.number }
        )
    )

@Composable
internal fun RepoIssuesSectionDropdownButton(
    currentSection: RepoIssuesSection,
    onSelectSection: (RepoIssuesSection) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = appStrings()
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedIconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Lucide.ChevronDown,
                contentDescription = strings.repoIssuesSectionSwitchDesc,
                modifier = Modifier.size(16.dp)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            listOf(
                Triple(RepoIssuesSection.ISSUES, strings.repoIssuesSectionIssues, Lucide.CircleDot),
                Triple(RepoIssuesSection.LABELS, strings.repoIssuesSectionLabels, Lucide.Tag),
                Triple(RepoIssuesSection.MILESTONES, strings.repoIssuesSectionMilestones, Lucide.Flag)
            ).forEach { (section, label, icon) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    trailingIcon = {
                        if (section == currentSection) {
                            Icon(
                                Lucide.Check,
                                contentDescription = strings.selectedOption,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelectSection(section)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RepoLabelsManagementContent(
    labels: List<LabelTag>?,
    isLoading: Boolean,
    errorMsg: String?,
    canManage: Boolean,
    onRefresh: () -> Unit,
    onEditLabel: (LabelTag) -> Unit,
    onDeleteLabel: (LabelTag) -> Unit
) {
    val strings = appStrings()

    if (isLoading && !labels.isNullOrEmpty()) {
        MaxxingWavyProgress(modifier = Modifier.padding(horizontal = 16.dp))
    }

    PullToRefreshBox(
        isRefreshing = isLoading && !labels.isNullOrEmpty(),
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        when {
            isLoading && labels == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    MaxxingLoadingState(message = strings.loadingLabels)
                }
            }

            errorMsg != null && labels.isNullOrEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(errorMsg, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(10.dp))
                    FilledTonalButton(onClick = onRefresh) {
                        Text(strings.retry)
                    }
                }
            }

            labels.isNullOrEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Lucide.Tag,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = strings.repoIssuesSectionLabels,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = strings.issueNoLabels,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(labels, key = { it.name }) { label ->
                        ElevatedCard(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        GitHubLabelChip(name = label.name, colorHex = label.colorHex)
                                        Text(
                                            text = "#${label.colorHex.removePrefix("#").lowercase()}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    val desc = label.description?.trim()
                                    if (!desc.isNullOrEmpty()) {
                                        Text(
                                            text = desc,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (canManage) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        IconButton(onClick = { onEditLabel(label) }) {
                                            Icon(
                                                Lucide.Pencil,
                                                contentDescription = strings.editLabelDesc,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(onClick = { onDeleteLabel(label) }) {
                                            Icon(
                                                Lucide.Trash2,
                                                contentDescription = strings.deleteLabelDesc,
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.error
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
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun RepoMilestonesManagementContent(
    milestones: List<MilestoneSummary>?,
    isLoading: Boolean,
    errorMsg: String?,
    canManage: Boolean,
    isBusy: Boolean,
    onRefresh: () -> Unit,
    onEditMilestone: (MilestoneSummary) -> Unit,
    onToggleState: (MilestoneSummary) -> Unit,
    onDeleteMilestone: (MilestoneSummary) -> Unit
) {
    val strings = appStrings()
    val sorted = remember(milestones) {
        milestones?.let { sortMilestonesForManagement(it) }
    }

    if (isLoading && !sorted.isNullOrEmpty()) {
        MaxxingWavyProgress(modifier = Modifier.padding(horizontal = 16.dp))
    }

    PullToRefreshBox(
        isRefreshing = isLoading && !sorted.isNullOrEmpty(),
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        when {
            isLoading && sorted == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    MaxxingLoadingState(message = strings.loading)
                }
            }

            errorMsg != null && sorted.isNullOrEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(errorMsg, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(10.dp))
                    FilledTonalButton(onClick = onRefresh) {
                        Text(strings.retry)
                    }
                }
            }

            sorted.isNullOrEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Lucide.Flag,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = strings.repoIssuesSectionMilestones,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = strings.repoNoMilestones,
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
                    items(sorted, key = { it.number }) { ms ->
                        val isOpen = ms.state.equals("open", ignoreCase = true)
                        val (percent, totalIssues) = computeMilestoneProgress(ms)
                        val formattedDue = formatMilestoneDueDate(ms.dueOn)

                        ElevatedCard(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = ms.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Surface(
                                        color = if (isOpen) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        shape = RoundedCornerShape(999.dp)
                                    ) {
                                        Text(
                                            text = if (isOpen) strings.statusOpen else strings.statusClosed,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isOpen) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                val desc = ms.description?.trim()
                                if (!desc.isNullOrEmpty()) {
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                LinearProgressIndicator(
                                    progress = { if (totalIssues == 0) 0f else percent / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                )

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    itemVerticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = strings.milestoneProgressSummary(percent, ms.closedIssues, totalIssues),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = strings.milestoneCountsSummary(ms.openIssues, ms.closedIssues),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (formattedDue != null) {
                                        Text(
                                            text = strings.milestoneDue(formattedDue),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                if (canManage) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            enabled = !isBusy,
                                            onClick = { onToggleState(ms) }
                                        ) {
                                            Icon(
                                                if (isOpen) Lucide.CircleCheck else Lucide.CircleDot,
                                                contentDescription = null,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                if (isOpen) strings.milestoneCloseAction else strings.milestoneReopenAction
                                            )
                                        }
                                        IconButton(
                                            enabled = !isBusy,
                                            onClick = { onEditMilestone(ms) }
                                        ) {
                                            Icon(
                                                Lucide.Pencil,
                                                contentDescription = strings.milestoneEditDesc,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            enabled = !isBusy,
                                            onClick = { onDeleteMilestone(ms) }
                                        ) {
                                            Icon(
                                                Lucide.Trash2,
                                                contentDescription = strings.milestoneDeleteDesc,
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.error
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RepoLabelEditorDialog(
    editingLabel: LabelTag?,
    isSubmitting: Boolean,
    errorMsg: String?,
    onDismiss: () -> Unit,
    onSave: (name: String, colorHex: String, description: String) -> Unit
) {
    val strings = appStrings()
    var name by remember(editingLabel) { mutableStateOf(editingLabel?.name.orEmpty()) }
    var colorHex by remember(editingLabel) { mutableStateOf(editingLabel?.colorHex ?: "6E40C9") }
    var description by remember(editingLabel) { mutableStateOf(editingLabel?.description.orEmpty()) }
    val cleanPreviewColor = normalizeHexColor(colorHex)

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Text(if (editingLabel == null) strings.labelNewTitle else strings.labelEditTitle)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GitHubLabelChip(
                    name = name.trim().ifBlank { strings.labelNameLabel },
                    colorHex = cleanPreviewColor
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    enabled = !isSubmitting,
                    label = { Text(strings.labelNameLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = colorHex,
                    onValueChange = { colorHex = it.removePrefix("#").take(6) },
                    enabled = !isSubmitting,
                    label = { Text(strings.labelColorLabel) },
                    singleLine = true,
                    isError = colorHex.isNotBlank() && !isValidHexColor(colorHex),
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PRESET_LABEL_COLORS.forEach { preset ->
                        val parsedColor = Color(("FF$preset").toLong(16))
                        val isSelected = cleanPreviewColor.equals(preset, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(parsedColor)
                                .then(
                                    if (isSelected) {
                                        Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable(enabled = !isSubmitting) { colorHex = preset }
                        )
                    }
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    enabled = !isSubmitting,
                    label = { Text(strings.labelDescriptionLabel) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!errorMsg.isNullOrBlank()) {
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && isValidHexColor(colorHex) && !isSubmitting,
                onClick = { onSave(name.trim(), normalizeHexColor(colorHex), description.trim()) }
            ) {
                Text(if (editingLabel == null) strings.create else strings.save)
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isSubmitting,
                onClick = onDismiss
            ) {
                Text(strings.cancel)
            }
        }
    )
}

@Composable
internal fun RepoMilestoneEditorDialog(
    editingMilestone: MilestoneSummary?,
    isSubmitting: Boolean,
    errorMsg: String?,
    onDismiss: () -> Unit,
    onSave: (title: String, description: String, dueOnIso: String?, clearDueOn: Boolean) -> Unit
) {
    val strings = appStrings()
    var title by remember(editingMilestone) { mutableStateOf(editingMilestone?.title.orEmpty()) }
    var description by remember(editingMilestone) { mutableStateOf(editingMilestone?.description.orEmpty()) }
    var dueDateInput by remember(editingMilestone) {
        mutableStateOf(formatMilestoneDueDate(editingMilestone?.dueOn).orEmpty())
    }
    val parsedDue = parseMilestoneDueInput(dueDateInput)

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Text(if (editingMilestone == null) strings.milestoneNewTitle else strings.milestoneEditTitle)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    enabled = !isSubmitting,
                    label = { Text(strings.milestoneTitleLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dueDateInput,
                    onValueChange = { dueDateInput = it },
                    enabled = !isSubmitting,
                    label = { Text(strings.milestoneDueDateLabel) },
                    placeholder = { Text("2026-12-31") },
                    singleLine = true,
                    isError = !parsedDue.isValid,
                    trailingIcon = {
                        if (dueDateInput.isNotEmpty() && !isSubmitting) {
                            IconButton(onClick = { dueDateInput = "" }) {
                                Icon(Lucide.X, contentDescription = strings.clear, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (!parsedDue.isValid) {
                    Text(
                        text = strings.milestoneDueDateError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    enabled = !isSubmitting,
                    label = { Text(strings.milestoneDescriptionLabel) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!errorMsg.isNullOrBlank()) {
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank() && parsedDue.isValid && !isSubmitting,
                onClick = {
                    val hadDueOn = !editingMilestone?.dueOn.isNullOrBlank()
                    val clearDueOn = editingMilestone != null && hadDueOn && parsedDue.isoValue == null
                    onSave(title.trim(), description.trim(), parsedDue.isoValue, clearDueOn)
                }
            ) {
                Text(if (editingMilestone == null) strings.create else strings.save)
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isSubmitting,
                onClick = onDismiss
            ) {
                Text(strings.cancel)
            }
        }
    )
}
