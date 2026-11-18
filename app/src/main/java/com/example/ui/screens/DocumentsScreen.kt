package com.example.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.DocumentEntity
import com.example.data.model.SortOption
import com.example.ui.components.DocumentRowCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.theme.PinkDeep
import com.example.ui.theme.PinkLight
import com.example.ui.theme.PinkPrimary

@Composable
fun DocumentsScreen(
    documents: List<DocumentEntity>,
    onImportPdf: () -> Unit,
    onScanNow: () -> Unit,
    onOpenDocument: (DocumentEntity) -> Unit,
    onEditDocument: (DocumentEntity) -> Unit,
    onToggleFavorite: (DocumentEntity) -> Unit,
    onRenameDocument: (DocumentEntity) -> Unit,
    onShareDocument: (DocumentEntity) -> Unit,
    onDownloadDocument: (DocumentEntity) -> Unit,
    onPrintDocument: (DocumentEntity) -> Unit,
    onInfoDocument: (DocumentEntity) -> Unit,
    onDeleteDocument: (DocumentEntity) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var sortOption by remember { mutableStateOf(SortOption.DATE_DESC) }
    var sortMenuOpen by remember { mutableStateOf(false) }

    val filteredDocs = remember(documents, searchQuery, favoritesOnly, sortOption) {
        val base = documents.filter { doc ->
            val matchesQuery = searchQuery.isBlank() ||
                doc.title.contains(searchQuery.trim(), ignoreCase = true) ||
                doc.sourceType.contains(searchQuery.trim(), ignoreCase = true)
            val matchesFav = !favoritesOnly || doc.isFavorite
            matchesQuery && matchesFav
        }
        when (sortOption) {
            SortOption.DATE_DESC -> base.sortedByDescending { it.updatedAt }
            SortOption.DATE_ASC -> base.sortedBy { it.updatedAt }
            SortOption.NAME_ASC -> base.sortedBy { it.title.lowercase() }
            SortOption.NAME_DESC -> base.sortedByDescending { it.title.lowercase() }
            SortOption.SIZE_DESC -> base.sortedByDescending { it.fileSizeBytes }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "All Documents",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${documents.size} local PDF${if (documents.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = onImportPdf,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("docs_import_btn")
                ) {
                    Icon(Icons.Filled.FileOpen, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Import")
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search PDFs by name...") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Close, "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PinkPrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.fillMaxWidth().testTag("docs_search_input")
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !favoritesOnly,
                    onClick = { favoritesOnly = false },
                    label = { Text("All (${documents.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PinkLight,
                        selectedLabelColor = PinkDeep
                    )
                )
                FilterChip(
                    selected = favoritesOnly,
                    onClick = { favoritesOnly = true },
                    leadingIcon = { Icon(Icons.Filled.Favorite, null) },
                    label = { Text("Favorites (${documents.count { it.isFavorite }})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PinkLight,
                        selectedLabelColor = PinkDeep,
                        selectedLeadingIconColor = PinkDeep
                    )
                )
                Spacer(Modifier.weight(1f))
                Box {
                    OutlinedButton(
                        onClick = { sortMenuOpen = true },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("docs_sort_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Sort, null)
                        Spacer(Modifier.width(4.dp))
                        Text(sortOption.label, style = MaterialTheme.typography.labelMedium)
                    }
                    DropdownMenu(
                        expanded = sortMenuOpen,
                        onDismissRequest = { sortMenuOpen = false }
                    ) {
                        SortOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    sortOption = option
                                    sortMenuOpen = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (filteredDocs.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Filled.PictureAsPdf,
                    title = if (documents.isEmpty()) "Your Library is Empty" else "No Matching PDFs",
                    subtitle = if (documents.isEmpty()) {
                        "Scan a document, convert photos, or import an existing PDF to store and manage it here."
                    } else {
                        "Try clearing your search query or switching out of Favorites."
                    },
                    actionLabel = if (documents.isEmpty()) "Scan Document" else null,
                    onAction = if (documents.isEmpty()) onScanNow else null,
                    modifier = Modifier.padding(top = 12.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredDocs, key = { it.id }) { doc ->
                        DocumentRowCard(
                            document = doc,
                            onOpen = { onOpenDocument(doc) },
                            onEdit = { onEditDocument(doc) },
                            onToggleFavorite = { onToggleFavorite(doc) },
                            onRename = { onRenameDocument(doc) },
                            onShare = { onShareDocument(doc) },
                            onDownload = { onDownloadDocument(doc) },
                            onPrint = { onPrintDocument(doc) },
                            onInfo = { onInfoDocument(doc) },
                            onDelete = { onDeleteDocument(doc) }
                        )
                    }
                }
            }
        }
    }
}
