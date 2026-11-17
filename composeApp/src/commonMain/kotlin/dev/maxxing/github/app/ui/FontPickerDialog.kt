package dev.maxxing.github.app.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
internal fun FontPickerDialog(
    title: String,
    current: FontSelection,
    onSelect: (FontSelection) -> Unit,
    onDismiss: () -> Unit,
    onImported: (CustomFontFile?) -> Unit,
    onDeletedImported: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var importedFonts by remember { mutableStateOf(PlatformFontManager.listImportedFonts()) }
    val systemFonts = remember { PlatformFontManager.listSystemFontFamilies() }
    val launchImport = rememberFontFileImporter { imported ->
        importedFonts = PlatformFontManager.listImportedFonts()
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
                    Text("Import font file (.ttf / .otf)")
                }

                if (systemFonts.isNotEmpty()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search system fonts...") },
                        leadingIcon = { Icon(Lucide.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Lucide.X, contentDescription = "Clear", modifier = Modifier.size(15.dp))
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
                            label = "System Default",
                            selected = current == FontSelection.SystemDefault,
                            onClick = { onSelect(FontSelection.SystemDefault) }
                        )
                    }

                    item(key = "hdr_bundled") {
                        FontGroupHeader("Bundled Open-Source Fonts")
                    }
                    item(key = "bundled_ubuntu") {
                        FontChoiceRow(
                            label = "Ubuntu (UFL)",
                            selected = current.kind == FontSourceKind.BUNDLED_UBUNTU,
                            onClick = { onSelect(FontSelection.BundledUbuntu) }
                        )
                    }
                    item(key = "bundled_kaisei") {
                        FontChoiceRow(
                            label = "Kaisei Opti (OFL)",
                            selected = current.kind == FontSourceKind.BUNDLED_KAISEI_OPTI,
                            onClick = { onSelect(FontSelection.BundledKaiseiOpti) }
                        )
                    }

                    item(key = "hdr_generic") {
                        FontGroupHeader("Generic Families")
                    }
                    item(key = "generic_sans") {
                        FontChoiceRow(
                            label = "Sans-serif",
                            selected = current.kind == FontSourceKind.GENERIC_SANS,
                            onClick = { onSelect(FontSelection.SansSerif) }
                        )
                    }
                    item(key = "generic_serif") {
                        FontChoiceRow(
                            label = "Serif",
                            selected = current.kind == FontSourceKind.GENERIC_SERIF,
                            onClick = { onSelect(FontSelection.Serif) }
                        )
                    }
                    item(key = "generic_mono") {
                        FontChoiceRow(
                            label = "Monospace",
                            selected = current.kind == FontSourceKind.GENERIC_MONOSPACE,
                            onClick = { onSelect(FontSelection.Monospace) }
                        )
                    }

                    if (importedFonts.isNotEmpty()) {
                        item(key = "hdr_imported") {
                            FontGroupHeader("Imported Font Files")
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
                                onDelete = {
                                    if (PlatformFontManager.deleteImportedFont(custom.filePath)) {
                                        importedFonts = PlatformFontManager.listImportedFonts()
                                        onDeletedImported(custom.filePath)
                                    }
                                }
                            )
                        }
                    }

                    if (systemFonts.isNotEmpty()) {
                        item(key = "hdr_system") {
                            FontGroupHeader("System Fonts")
                        }
                        if (filteredSystemFonts.isEmpty()) {
                            item(key = "no_sys_match") {
                                Text(
                                    text = "No matching system fonts",
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
                Text("Close")
            }
        }
    )
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

@Composable
private fun FontChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f)
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
                        contentDescription = "Delete imported font",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            if (selected) {
                Icon(
                    Lucide.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
