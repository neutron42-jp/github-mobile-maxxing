package dev.maxxing.github.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.X
import dev.maxxing.github.app.fonts.PlatformFontManager
import dev.maxxing.github.app.fonts.rememberFontFileImporter
import dev.maxxing.github.model.CustomFontFile
import dev.maxxing.github.model.FontSelection
import dev.maxxing.github.model.FontSourceKind
import dev.maxxing.github.ui.components.MaxxingWavyProgress
import dev.maxxing.github.ui.i18n.appStrings
import dev.maxxing.github.ui.theme.LocalGraphitePalette
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun FontPickerDialog(
    title: String,
    current: FontSelection,
    /** Imported files selected as the primary or the fallback font; deleting one resets that slot. */
    inUseFilePaths: Set<String>,
    onSelect: (FontSelection) -> Unit,
    onDismiss: () -> Unit,
    onImported: (CustomFontFile?) -> Unit,
    onDeletedImported: (String) -> Unit
) {
    val strings = appStrings()
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    // Font enumeration can be slow (AWT on desktop, directory scans on Android), so it runs off the UI thread.
    var importedFontsVersion by remember { mutableIntStateOf(0) }
    val importedFonts by produceState(initialValue = emptyList<CustomFontFile>(), importedFontsVersion) {
        value = withContext(Dispatchers.IO) { PlatformFontManager.listImportedFonts() }
    }
    val loadedSystemFonts by produceState<List<String>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { PlatformFontManager.listSystemFontFamilies() }
    }
    val systemFonts = loadedSystemFonts.orEmpty()
    // Shown inside the dialog as well: a snackbar would sit behind the dialog's scrim.
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<CustomFontFile?>(null) }
    val launchImport = rememberFontFileImporter { imported ->
        importedFontsVersion++
        errorMessage = if (imported == null) strings.failedImportFont else null
        onImported(imported)
    }

    val filteredSystemFonts = remember(systemFonts, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) systemFonts
        else systemFonts.filter { it.contains(q, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = launchImport,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Lucide.Plus, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.importFontFileButton)
                }

                errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (systemFonts.isNotEmpty()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(strings.searchSystemFontsPlaceholder) },
                        leadingIcon = { Icon(Lucide.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Lucide.X, contentDescription = strings.clear, modifier = Modifier.size(15.dp))
                                }
                            }
                        } else null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item(key = "sys_default") {
                        FontChoiceRow(
                            label = strings.systemDefaultLabel,
                            selected = current.kind == FontSourceKind.SYSTEM_DEFAULT,
                            onClick = { onSelect(FontSelection.SystemDefault) }
                        )
                    }

                    if (importedFonts.isNotEmpty()) {
                        item(key = "hdr_imported") {
                            FontGroupHeader(strings.importedFontFilesHeader)
                        }
                        items(importedFonts, key = { "imp_${it.filePath}" }) { custom ->
                            val selection = FontSelection(
                                kind = FontSourceKind.CUSTOM_FILE,
                                value = custom.filePath,
                                displayName = custom.displayName
                            )
                            FontChoiceRow(
                                label = custom.displayName,
                                selected = current.kind == FontSourceKind.CUSTOM_FILE && current.value == custom.filePath,
                                onClick = { onSelect(selection) },
                                onDelete = { pendingDelete = custom }
                            )
                        }
                    }

                    item(key = "hdr_generic") {
                        FontGroupHeader(strings.genericFontFamiliesHeader)
                    }
                    item(key = "generic_sans") {
                        FontChoiceRow(
                            label = strings.fontGenericSans,
                            selected = current.kind == FontSourceKind.GENERIC_SANS,
                            onClick = { onSelect(FontSelection.SansSerif) }
                        )
                    }
                    item(key = "generic_serif") {
                        FontChoiceRow(
                            label = strings.fontGenericSerif,
                            selected = current.kind == FontSourceKind.GENERIC_SERIF,
                            onClick = { onSelect(FontSelection.Serif) }
                        )
                    }
                    item(key = "generic_mono") {
                        FontChoiceRow(
                            label = strings.fontGenericMonospace,
                            selected = current.kind == FontSourceKind.GENERIC_MONOSPACE,
                            onClick = { onSelect(FontSelection.Monospace) }
                        )
                    }

                    if (loadedSystemFonts == null) {
                        item(key = "loading_system") {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = strings.loadingSystemFonts,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                MaxxingWavyProgress()
                            }
                        }
                    } else if (systemFonts.isNotEmpty()) {
                        item(key = "hdr_system") {
                            FontGroupHeader(strings.systemFontsHeader)
                        }
                        if (filteredSystemFonts.isEmpty()) {
                            item(key = "no_sys_match") {
                                Text(
                                    text = strings.noMatchingSystemFonts,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        } else {
                            items(filteredSystemFonts, key = { "sys_$it" }) { familyName ->
                                val selection = FontSelection(
                                    kind = FontSourceKind.SYSTEM_FAMILY,
                                    value = familyName,
                                    displayName = familyName
                                )
                                FontChoiceRow(
                                    label = familyName,
                                    selected = current.kind == FontSourceKind.SYSTEM_FAMILY && current.value == familyName,
                                    onClick = { onSelect(selection) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.closeAction)
            }
        }
    )

    pendingDelete?.let { font ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(strings.deleteImportedFontTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = font.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (font.filePath in inUseFilePaths) {
                        Text(
                            text = strings.deleteSelectedFontInUseDesc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        // Once the file is deleted, the selection must be cleared even if the dialog closes.
                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                            withContext(NonCancellable) {
                                val deleted = withContext(Dispatchers.IO) {
                                    PlatformFontManager.deleteImportedFont(font.filePath)
                                }
                                importedFontsVersion++
                                if (deleted) {
                                    errorMessage = null
                                    onDeletedImported(font.filePath)
                                } else {
                                    errorMessage = strings.failedDeleteFontFile
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(strings.deleteAction)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

@Composable
private fun FontGroupHeader(title: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

internal data class FontChoiceRowColors(
    val container: Color,
    val content: Color,
    val border: Color?
)

/**
 * Colors for one font row.
 *
 * Graphite's primaryContainer is dark in light mode and light in dark mode. At 65% opacity over
 * the dialog surface (surfaceContainerHigh) the label falls to about 3.9:1 in light mode and
 * 2.2:1 in dark mode, below the 4.5:1 requirement. Graphite therefore uses an opaque neutral
 * surface family with onSurface text and marks selection with a primary border; the Expressive
 * palettes keep their existing translucent primaryContainer fill.
 */
internal fun fontChoiceRowColors(
    selected: Boolean,
    graphitePalette: Boolean,
    scheme: ColorScheme
): FontChoiceRowColors = if (selected && graphitePalette) {
    FontChoiceRowColors(
        container = scheme.surfaceContainerHighest,
        content = scheme.onSurface,
        border = scheme.primary
    )
} else {
    FontChoiceRowColors(
        container = if (selected) {
            scheme.primaryContainer.copy(alpha = 0.65f)
        } else {
            scheme.surfaceContainerHigh.copy(alpha = 0.55f)
        },
        content = scheme.onSurface,
        border = null
    )
}

@Composable
private fun FontChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val strings = appStrings()
    val colors = fontChoiceRowColors(
        selected = selected,
        graphitePalette = LocalGraphitePalette.current,
        scheme = MaterialTheme.colorScheme
    )
    Surface(
        shape = MaterialTheme.shapes.small,
        color = colors.container,
        contentColor = colors.content,
        border = colors.border?.let { BorderStroke(1.dp, it) },
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (onDelete != null) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Lucide.Trash2,
                        contentDescription = strings.deleteImportedFontDesc,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            if (selected) {
                Icon(
                    Lucide.Check,
                    contentDescription = strings.selectedOption,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
