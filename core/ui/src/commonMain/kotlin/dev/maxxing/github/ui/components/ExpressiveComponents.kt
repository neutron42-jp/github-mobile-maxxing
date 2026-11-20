package dev.maxxing.github.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Tab
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Star
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.UserCheck
import com.composables.icons.lucide.UserPlus
import dev.maxxing.github.model.LabelTag
import dev.maxxing.github.ui.i18n.appStrings

/**
 * Tab with a leading icon and a one-line label. The side padding (8dp) and icon gap (4dp) are tighter than
 * the stock text tab (16dp / 6dp) so that three such tabs fit a 360dp-wide screen even with enlarged system
 * font sizes; a label that still does not fit ends with an ellipsis instead of being cut mid-character.
 */
@Composable
fun MaxxingIconLabelTab(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    Tab(selected = selected, onClick = onClick, modifier = modifier) {
        Row(
            modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MaxxingLoadingState(
    message: String = appStrings().loading,
    modifier: Modifier = Modifier,
    contained: Boolean = true
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (contained) {
            ContainedLoadingIndicator(modifier = Modifier.size(56.dp))
        } else {
            LoadingIndicator(modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MaxxingWavyProgress(
    modifier: Modifier = Modifier,
    progress: Float? = null
) {
    if (progress != null) {
        LinearWavyProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = modifier.fillMaxWidth()
        )
    } else {
        LinearWavyProgressIndicator(
            modifier = modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MaxxingFloatingToolbar(
    expanded: Boolean = true,
    vibrant: Boolean = true,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = FloatingToolbarDefaults.ContentPadding,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable RowScope.() -> Unit
) {
    HorizontalFloatingToolbar(
        expanded = expanded,
        colors = if (vibrant) {
            FloatingToolbarDefaults.vibrantFloatingToolbarColors()
        } else {
            FloatingToolbarDefaults.standardFloatingToolbarColors()
        },
        contentPadding = contentPadding,
        modifier = modifier,
        content = {
            Row(
                modifier = Modifier.horizontalScroll(
                    state = scrollState,
                    // Keep the toolbar stationary unless items genuinely overflow a tiny viewport.
                    enabled = scrollState.maxValue > 0
                ),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    )
}

object MaxxingFloatingToolbarDefaults {
    /**
     * Filled button colors for use inside a vibrant [MaxxingFloatingToolbar].
     * The default disabled colors (onSurface at 10%/38%) nearly vanish on primaryContainer,
     * so the disabled state is derived from onPrimaryContainer instead to stay readable.
     */
    @Composable
    fun vibrantButtonColors(): ButtonColors {
        val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
        return ButtonDefaults.buttonColors(
            disabledContainerColor = onContainer.copy(alpha = 0.12f),
            disabledContentColor = onContainer.copy(alpha = 0.60f)
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> MaxxingConnectedButtonGroup(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    labelProvider: (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconProvider: ((T) -> ImageVector?)? = null
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = option == selected
            val shapes = when (index) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
            ToggleButton(
                checked = isSelected,
                enabled = enabled,
                onCheckedChange = { onSelect(option) },
                shapes = shapes,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton }
            ) {
                val icon = iconProvider?.invoke(option)
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    text = labelProvider(option),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Compact pull-down selector designed to align cleanly to the right side of headers
 * so narrow viewports never truncate connected pill labels to "[icon]...".
 */
@Composable
fun <T> MaxxingDropdownSelector(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    labelProvider: (T) -> String,
    modifier: Modifier = Modifier,
    iconProvider: ((T) -> ImageVector?)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedIcon = iconProvider?.invoke(selected)
    val strings = appStrings()
    Box(modifier = modifier) {
        FilledTonalButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            if (selectedIcon != null) {
                Icon(selectedIcon, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = labelProvider(selected),
                maxLines = 1,
                softWrap = false
            )
            Spacer(Modifier.width(4.dp))
            Icon(Lucide.ChevronDown, contentDescription = strings.selectOption, modifier = Modifier.size(15.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                val optIcon = iconProvider?.invoke(option)
                DropdownMenuItem(
                    leadingIcon = optIcon?.let { icon ->
                        { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    },
                    text = { Text(labelProvider(option), maxLines = 1, softWrap = false) },
                    trailingIcon = if (isSelected) {
                        { Icon(Lucide.Check, contentDescription = strings.selectedOption, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) }
                    } else null,
                    onClick = {
                        expanded = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}

data class SplitActionOption<T>(
    val value: T,
    val label: String,
    val icon: ImageVector? = null,
    val buttonLabel: String = label
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> MaxxingSplitActionButton(
    selectedOption: SplitActionOption<T>,
    options: List<SplitActionOption<T>>,
    onPrimaryClick: (T) -> Unit,
    onSelectOption: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors()
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (menuExpanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
    )

    Box(modifier = modifier) {
        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.LeadingButton(
                    onClick = { onPrimaryClick(selectedOption.value) },
                    enabled = enabled,
                    colors = colors,
                    contentPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 4.dp, bottom = 8.dp)
                ) {
                    selectedOption.icon?.let {
                        Icon(
                            imageVector = it,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                    }
                    Text(
                        text = selectedOption.buttonLabel,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            },
            trailingButton = {
                SplitButtonDefaults.TrailingButton(
                    checked = menuExpanded,
                    onCheckedChange = { menuExpanded = it },
                    enabled = enabled,
                    colors = colors,
                    contentPadding = PaddingValues(start = 4.dp, top = 8.dp, end = 5.dp, bottom = 8.dp)
                ) {
                    Icon(
                        imageVector = Lucide.ChevronDown,
                        contentDescription = appStrings().selectOption,
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer { rotationZ = chevronRotation }
                    )
                }
            }
        )

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    leadingIcon = option.icon?.let { icon ->
                        { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    },
                    onClick = {
                        menuExpanded = false
                        onSelectOption(option.value)
                    }
                )
            }
        }
    }
}

@Composable
fun UserAvatar(
    avatarUrl: String,
    login: String,
    sizeDp: Int = 28,
    modifier: Modifier = Modifier
) {
    if (avatarUrl.isNotBlank()) {
        val context = LocalPlatformContext.current
        val sizePx = with(LocalDensity.current) { sizeDp.dp.roundToPx() }.coerceAtLeast(32)
        val request = remember(avatarUrl, sizePx, context) {
            ImageRequest.Builder(context)
                .data(avatarUrl)
                .size(sizePx, sizePx)
                .crossfade(false)
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = login,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(sizeDp.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        )
    } else {
        Box(
            modifier = modifier
                .size(sizeDp.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = login.take(1).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun GitHubLabelChip(
    name: String,
    colorHex: String,
    modifier: Modifier = Modifier
) {
    val parsedColor = remember(colorHex) { parseHexColor(colorHex) }
    val bgColor = parsedColor.copy(alpha = 0.2f)
    Surface(
        color = bgColor,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(parsedColor)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = MaterialTheme.typography.labelMedium.fontFamily,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun ExpressiveStatusPill(
    label: String,
    containerColor: Color,
    contentColor: Color,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = CircleShape,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = MaterialTheme.typography.labelMedium.fontFamily,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

val MaxxingStarAmber = Color(0xFFF59E0B)

/**
 * Icon-first Star / Starred toggle button:
 * solid golden star + warm tinted pill when starred vs hollow outline star when unstarred.
 */
@Composable
fun StarActionButton(
    isStarred: Boolean,
    count: Int? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = appStrings()
    val isLight = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val countSuffix = count?.let { " ${formatCompactCount(it)}" }.orEmpty()
    if (isStarred) {
        val container = if (isLight) Color(0xFFFEF3C7) else Color(0xFF453208)
        val content = if (isLight) Color(0xFF92400E) else Color(0xFFFCD34D)
        val starTint = if (isLight) Color(0xFFD97706) else Color(0xFFFBBF24)
        FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            border = BorderStroke(1.5.dp, starTint.copy(alpha = 0.7f)),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = container,
                contentColor = content
            ),
            modifier = modifier
        ) {
            Icon(
                imageVector = MaxxingFilledIcons.Star,
                contentDescription = strings.starred,
                tint = starTint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = "${strings.starred}$countSuffix",
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = modifier
        ) {
            Icon(
                imageVector = Lucide.Star,
                contentDescription = strings.star,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = "${strings.star}$countSuffix",
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * Icon-first Follow / Following toggle button.
 */
@Composable
fun FollowActionButton(
    isFollowing: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = appStrings()
    if (isFollowing) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            modifier = modifier
        ) {
            Icon(Lucide.UserCheck, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(strings.followingLabel, maxLines = 1, softWrap = false)
        }
    } else {
        Button(
            onClick = onClick,
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            modifier = modifier
        ) {
            Icon(Lucide.UserPlus, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(strings.follow, maxLines = 1, softWrap = false)
        }
    }
}

fun formatCompactCount(count: Int): String = when {
    count >= 1_000_000 -> "${(count / 100_000) / 10.0}M".replace(".0M", "M")
    count >= 1_000 -> "${(count / 100) / 10.0}k".replace(".0k", "k")
    else -> count.toString()
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Modifier.expressiveAnimateContentSize(): Modifier =
    this.animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec())

fun parseHexColor(rawHex: String): Color {
    val clean = rawHex.removePrefix("#").trim()
    val rgb = clean.toLongOrNull(16) ?: 0x6E40C9L
    return Color(0xFF000000L or (rgb and 0xFFFFFFL))
}

/** Shared layout metrics keeping top-level screen title bars and tab strips vertically aligned. */
object MaxxingTopHeaderDefaults {
    val HorizontalPadding = 16.dp
    val ExpandedVerticalPadding = 14.dp
    val CollapsedVerticalPadding = 6.dp
    val TitleRowMinHeight = 48.dp
    val TabBarTopSpacing = 10.dp
}

/**
 * Multi-select label picker dialog shared between new issue authoring and existing issue/PR management.
 */
@Composable
fun RepoLabelsPickerDialog(
    title: String,
    availableLabels: List<LabelTag>,
    selectedLabelNames: Set<String>,
    onToggleLabel: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmLabel: String = appStrings().save,
    isLoading: Boolean = false,
    isBusy: Boolean = false,
    canCreateLabel: Boolean = true,
    newLabelName: String = "",
    onNewLabelNameChange: (String) -> Unit = {},
    onCreateLabel: ((String) -> Unit)? = null,
    onDeleteLabel: ((LabelTag) -> Unit)? = null
) {
    val strings = appStrings()
    var pendingDeleteLabel by remember { mutableStateOf<LabelTag?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth().height(210.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isLoading && availableLabels.isEmpty()) {
                        MaxxingLoadingState(message = strings.loadingLabels)
                    } else if (availableLabels.isEmpty()) {
                        Text(
                            strings.issueNoLabels,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    availableLabels.forEach { label ->
                        val checked = label.name in selectedLabelNames
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleLabel(label.name, !checked) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked -> onToggleLabel(label.name, isChecked) }
                            )
                            Box(modifier = Modifier.weight(1f)) {
                                GitHubLabelChip(name = label.name, colorHex = label.colorHex)
                            }
                            if (canCreateLabel && onDeleteLabel != null) {
                                IconButton(
                                    enabled = !isBusy,
                                    onClick = { pendingDeleteLabel = label },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Lucide.Trash2,
                                        contentDescription = strings.deleteLabelDesc,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                if (canCreateLabel && onCreateLabel != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = newLabelName,
                            onValueChange = onNewLabelNameChange,
                            label = { Text(strings.issueNewLabel) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalIconButton(
                            enabled = newLabelName.isNotBlank() && !isBusy,
                            onClick = { onCreateLabel(newLabelName.trim()) }
                        ) {
                            Icon(Lucide.Plus, contentDescription = strings.createLabelAction)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isBusy,
                onClick = onConfirm
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )

    pendingDeleteLabel?.let { targetLabel ->
        AlertDialog(
            onDismissRequest = { if (!isBusy) pendingDeleteLabel = null },
            title = { Text(strings.labelDeleteTitle) },
            text = { Text(strings.labelDeleteConfirm(targetLabel.name)) },
            confirmButton = {
                Button(
                    enabled = !isBusy,
                    onClick = {
                        pendingDeleteLabel = null
                        onDeleteLabel?.invoke(targetLabel)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(strings.deleteAction)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isBusy,
                    onClick = { pendingDeleteLabel = null }
                ) {
                    Text(strings.cancel)
                }
            }
        )
    }
}
