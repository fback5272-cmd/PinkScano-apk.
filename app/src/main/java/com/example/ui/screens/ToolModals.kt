package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.DocumentEntity
import com.example.data.model.CompressionLevel
import com.example.data.model.EditorColorSwatches
import com.example.data.model.PageNumberConfig
import com.example.data.model.PageNumberFormat
import com.example.data.model.PageNumberPosition
import com.example.data.model.PdfToolType
import com.example.data.model.WatermarkConfig
import com.example.data.model.WatermarkPosition
import com.example.ui.components.formatBytes
import com.example.ui.theme.PinkDeep
import com.example.ui.theme.PinkLight
import com.example.ui.theme.PinkPrimary
import java.io.File

@Composable
fun SinglePdfPickerDialog(
    title: String,
    documents: List<DocumentEntity>,
    onImportExternal: () -> Unit,
    onSelect: (DocumentEntity) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onImportExternal,
                    modifier = Modifier.fillMaxWidth().testTag("tool_import_pdf_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.FileOpen, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Import PDF from Device")
                }
                if (documents.isEmpty()) {
                    Text(
                        text = "No saved PDFs yet. Import a PDF or create one first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(documents, key = { _, d -> d.id }) { _, doc ->
                            Surface(
                                onClick = { onSelect(doc) },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.PictureAsPdf, null, tint = PinkPrimary)
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${doc.title}.pdf", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                        Text(
                                            "${doc.pageCount} pg • ${formatBytes(doc.fileSizeBytes)}",
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
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun MergePdfsDialog(
    documents: List<DocumentEntity>,
    onImportExternal: () -> Unit,
    onMerge: (List<DocumentEntity>, String) -> Unit,
    onDismiss: () -> Unit
) {
    val selectedList = remember { mutableStateListOf<DocumentEntity>() }
    var outputTitle by remember { mutableStateOf("Merged_${System.currentTimeMillis().toString().takeLast(4)}") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Merge PDFs (${selectedList.size})", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = outputTitle,
                    onValueChange = { outputTitle = it },
                    label = { Text("Output Filename") },
                    suffix = { Text(".pdf") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = onImportExternal,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.FileOpen, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Import Another PDF")
                }

                if (documents.isEmpty()) {
                    Text("Import or create at least 2 PDFs to merge.", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("Select & order PDFs:", style = MaterialTheme.typography.labelMedium)
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(documents, key = { _, d -> d.id }) { _, doc ->
                            val orderIdx = selectedList.indexOfFirst { it.id == doc.id }
                            val checked = orderIdx >= 0
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (checked) PinkLight.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, if (checked) PinkPrimary else MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (checked) selectedList.removeAll { it.id == doc.id }
                                    else selectedList.add(doc)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = {
                                            if (checked) selectedList.removeAll { it.id == doc.id }
                                            else selectedList.add(doc)
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = PinkPrimary)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${doc.title}.pdf", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                        Text("${doc.pageCount} pg", style = MaterialTheme.typography.labelSmall)
                                    }
                                    if (checked) {
                                        Text("#${orderIdx + 1}", fontWeight = FontWeight.Bold, color = PinkDeep)
                                        if (orderIdx > 0) {
                                            IconButton(onClick = {
                                                val item = selectedList.removeAt(orderIdx)
                                                selectedList.add(orderIdx - 1, item)
                                            }) {
                                                Icon(Icons.Filled.ArrowUpward, "Move up", modifier = Modifier.size(18.dp))
                                            }
                                        }
                                        if (orderIdx < selectedList.lastIndex) {
                                            IconButton(onClick = {
                                                val item = selectedList.removeAt(orderIdx)
                                                selectedList.add(orderIdx + 1, item)
                                            }) {
                                                Icon(Icons.Filled.ArrowDownward, "Move down", modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = selectedList.size >= 2 && outputTitle.isNotBlank(),
                onClick = { onMerge(selectedList.toList(), outputTitle.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary),
                modifier = Modifier.testTag("confirm_merge_btn")
            ) {
                Text("Merge (${selectedList.size})")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun SplitPdfDialog(
    doc: DocumentEntity,
    onSplitRange: (Int, Int, String) -> Unit,
    onSplitAllPages: () -> Unit,
    onDismiss: () -> Unit
) {
    var splitEveryPage by remember { mutableStateOf(false) }
    var startText by remember { mutableStateOf("1") }
    var endText by remember { mutableStateOf(doc.pageCount.toString()) }
    var title by remember { mutableStateOf("${doc.title}_split") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Split PDF (${doc.pageCount} pages)", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !splitEveryPage,
                        onClick = { splitEveryPage = false },
                        label = { Text("Page Range") }
                    )
                    FilterChip(
                        selected = splitEveryPage,
                        onClick = { splitEveryPage = true },
                        label = { Text("Every Page") }
                    )
                }
                if (!splitEveryPage) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = startText,
                            onValueChange = { startText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("From") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = endText,
                            onValueChange = { endText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("To (max ${doc.pageCount})") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Output Name") },
                        suffix = { Text(".pdf") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = "Creates ${doc.pageCount} separate 1-page PDF files in your library.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = splitEveryPage || (title.isNotBlank() && (startText.toIntOrNull() ?: 0) >= 1),
                onClick = {
                    if (splitEveryPage) {
                        onSplitAllPages()
                    } else {
                        val s = (startText.toIntOrNull() ?: 1).coerceIn(1, doc.pageCount)
                        val e = (endText.toIntOrNull() ?: doc.pageCount).coerceIn(s, doc.pageCount)
                        onSplitRange(s, e, title.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
            ) {
                Text("Split PDF")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun CompressPdfDialog(
    doc: DocumentEntity,
    onCompress: (CompressionLevel, String) -> Unit,
    onDismiss: () -> Unit
) {
    var level by remember { mutableStateOf(CompressionLevel.MEDIUM) }
    var title by remember { mutableStateOf("${doc.title}_compressed") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compress PDF", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Current size: ${formatBytes(doc.fileSizeBytes)}", style = MaterialTheme.typography.bodySmall)
                CompressionLevel.entries.forEach { opt ->
                    FilterChip(
                        selected = level == opt,
                        onClick = { level = opt },
                        label = { Text(opt.label) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PinkLight,
                            selectedLabelColor = PinkDeep
                        )
                    )
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Output Name") },
                    suffix = { Text(".pdf") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank(),
                onClick = { onCompress(level, title.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
            ) {
                Text("Compress")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RotateOrDeletePagesDialog(
    doc: DocumentEntity,
    isDeleteMode: Boolean,
    onApplyRotate: (Map<Int, Int>, String) -> Unit,
    onApplyDelete: (Set<Int>, String) -> Unit,
    onDismiss: () -> Unit
) {
    val rotations = remember { mutableStateMapOf<Int, Int>() }
    val deleteSet = remember { mutableStateListOf<Int>() }
    var title by remember { mutableStateOf("${doc.title}_${if (isDeleteMode) "trimmed" else "rotated"}") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isDeleteMode) "Delete PDF Pages" else "Rotate PDF Pages", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (isDeleteMode) "Tap pages to mark for removal (keep at least 1):" else "Tap pages to rotate +90° clockwise:",
                    style = MaterialTheme.typography.bodySmall
                )
                if (!isDeleteMode) {
                    OutlinedButton(
                        onClick = {
                            for (i in 0 until doc.pageCount) {
                                rotations[i] = ((rotations[i] ?: 0) + 90) % 360
                            }
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Rotate All +90°")
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (i in 0 until doc.pageCount) {
                        val isMarked = if (isDeleteMode) deleteSet.contains(i) else (rotations[i] ?: 0) != 0
                        FilterChip(
                            selected = isMarked,
                            onClick = {
                                if (isDeleteMode) {
                                    if (deleteSet.contains(i)) deleteSet.remove(i)
                                    else if (deleteSet.size < doc.pageCount - 1) deleteSet.add(i)
                                } else {
                                    rotations[i] = ((rotations[i] ?: 0) + 90) % 360
                                }
                            },
                            label = {
                                Text(
                                    if (isDeleteMode) "P${i + 1}"
                                    else "P${i + 1} (${rotations[i] ?: 0}°)"
                                )
                            }
                        )
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Output Name") },
                    suffix = { Text(".pdf") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val enabled = title.isNotBlank() && if (isDeleteMode) deleteSet.isNotEmpty() else rotations.values.any { it != 0 }
            Button(
                enabled = enabled,
                onClick = {
                    if (isDeleteMode) onApplyDelete(deleteSet.toSet(), title.trim())
                    else onApplyRotate(rotations.toMap(), title.trim())
                },
                colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
            ) {
                Text(if (isDeleteMode) "Delete Selected" else "Save Rotated")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun WatermarkDialog(
    doc: DocumentEntity,
    onApply: (WatermarkConfig, String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("CONFIDENTIAL") }
    var fontSize by remember { mutableFloatStateOf(40f) }
    var opacity by remember { mutableFloatStateOf(0.28f) }
    var colorIdx by remember { mutableIntStateOf(0) }
    var position by remember { mutableStateOf(WatermarkPosition.DIAGONAL_CENTER) }
    var title by remember { mutableStateOf("${doc.title}_watermarked") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Watermark", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Watermark Text") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Size: ${fontSize.toInt()} pt", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = fontSize,
                    onValueChange = { fontSize = it },
                    valueRange = 18f..72f,
                    colors = SliderDefaults.colors(thumbColor = PinkPrimary, activeTrackColor = PinkPrimary)
                )
                Text("Opacity: ${(opacity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = opacity,
                    onValueChange = { opacity = it },
                    valueRange = 0.08f..0.85f,
                    colors = SliderDefaults.colors(thumbColor = PinkPrimary, activeTrackColor = PinkPrimary)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Color:", style = MaterialTheme.typography.labelMedium)
                    EditorColorSwatches.forEachIndexed { idx, col ->
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(col)
                                .border(
                                    width = if (idx == colorIdx) 2.5.dp else 1.dp,
                                    color = if (idx == colorIdx) PinkPrimary else Color.LightGray,
                                    shape = CircleShape
                                )
                                .clickable { colorIdx = idx }
                        )
                    }
                }
                Text("Position", style = MaterialTheme.typography.labelMedium)
                WatermarkPosition.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { pos ->
                            FilterChip(
                                selected = position == pos,
                                onClick = { position = pos },
                                label = { Text(pos.label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Output Name") },
                    suffix = { Text(".pdf") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = text.isNotBlank() && title.isNotBlank(),
                onClick = {
                    onApply(
                        WatermarkConfig(
                            text = text.trim(),
                            fontSizeSp = fontSize,
                            colorArgb = EditorColorSwatches[colorIdx].toArgb(),
                            opacity = opacity,
                            position = position
                        ),
                        title.trim()
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
            ) {
                Text("Save Watermark")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun PageNumbersDialog(
    doc: DocumentEntity,
    onApply: (PageNumberConfig, String) -> Unit,
    onDismiss: () -> Unit
) {
    var format by remember { mutableStateOf(PageNumberFormat.PAGE_OF_TOTAL) }
    var position by remember { mutableStateOf(PageNumberPosition.BOTTOM_CENTER) }
    var fontSize by remember { mutableFloatStateOf(11f) }
    var title by remember { mutableStateOf("${doc.title}_numbered") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Page Numbers", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Format", style = MaterialTheme.typography.labelMedium)
                PageNumberFormat.entries.forEach { fmt ->
                    FilterChip(
                        selected = format == fmt,
                        onClick = { format = fmt },
                        label = { Text(fmt.label) }
                    )
                }
                Text("Position", style = MaterialTheme.typography.labelMedium)
                PageNumberPosition.entries.forEach { pos ->
                    FilterChip(
                        selected = position == pos,
                        onClick = { position = pos },
                        label = { Text(pos.label) }
                    )
                }
                Text("Font Size: ${fontSize.toInt()} pt", style = MaterialTheme.typography.labelMedium)
                Slider(value = fontSize, onValueChange = { fontSize = it }, valueRange = 8f..18f)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Output Name") },
                    suffix = { Text(".pdf") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank(),
                onClick = {
                    onApply(
                        PageNumberConfig(format = format, position = position, fontSizePt = fontSize),
                        title.trim()
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
            ) {
                Text("Apply")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun PdfToImagesResultDialog(
    imageFiles: List<File>,
    zipFile: File,
    onSaveZipToUri: (File, android.net.Uri) -> Unit,
    onShareZip: (File) -> Unit,
    onDismiss: () -> Unit
) {
    val saveZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) onSaveZipToUri(zipFile, uri)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exported ${imageFiles.size} PNG Pages", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = PinkPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("All ${imageFiles.size} pages rendered cleanly and packed into ${zipFile.name}.")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { saveZipLauncher.launch(zipFile.name) },
                colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
            ) {
                Text("Save ZIP")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onShareZip(zipFile) }) { Text("Share ZIP") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}
