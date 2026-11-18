package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.DocumentEntity
import com.example.data.model.AppLanguage
import com.example.ui.theme.PinkDeep
import com.example.ui.theme.PinkLight
import com.example.ui.theme.PinkPrimary
import com.example.ui.theme.WarningAmber
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScreen(
    ocrText: String,
    ocrSourceLabel: String,
    ocrError: String?,
    ocrLanguage: AppLanguage,
    isProcessing: Boolean,
    progress: Float,
    documents: List<DocumentEntity>,
    onSelectLanguage: (AppLanguage) -> Unit,
    onTextChange: (String) -> Unit,
    onPickImageUri: (android.net.Uri) -> Unit,
    onOpenCameraForOcr: () -> Unit,
    onExtractFromPdf: (DocumentEntity) -> Unit,
    onRerunOcr: () -> Unit,
    onExportPdf: (String) -> Unit,
    onNotify: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var showPdfPicker by remember { mutableStateOf(false) }
    var showExportPdfDialog by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onPickImageUri(uri)
    }

    val txtSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(ocrText.toByteArray(Charsets.UTF_8))
                    }
                }.onSuccess {
                    onNotify("Saved extracted text as .txt")
                }.onFailure {
                    onNotify("Failed to save .txt file")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OCR Text Extractor", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("ocr_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (ocrSourceLabel.isNotEmpty()) {
                        IconButton(onClick = onRerunOcr, modifier = Modifier.testTag("ocr_rerun_btn")) {
                            Icon(Icons.Filled.Refresh, "Re-run OCR")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Language / Script Selector
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Script:", style = MaterialTheme.typography.labelLarge)
                    AppLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = ocrLanguage == lang,
                            onClick = { onSelectLanguage(lang) },
                            label = { Text(lang.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PinkLight,
                                selectedLabelColor = PinkDeep
                            )
                        )
                    }
                }

                if (ocrLanguage == AppLanguage.BANGLA) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.12f)),
                        border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Info, null, tint = WarningAmber)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Offline ML Kit bundles Latin (English) image OCR. Bangla Unicode text can be extracted directly from text-based PDFs via Extract PDF, while Bangla image OCR traineddata is not bundled offline.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                // Source Selection Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.weight(1f).testTag("ocr_pick_image_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Gallery")
                    }
                    OutlinedButton(
                        onClick = onOpenCameraForOcr,
                        modifier = Modifier.weight(1f).testTag("ocr_camera_btn"),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(Icons.Filled.CameraAlt, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Camera")
                    }
                    OutlinedButton(
                        enabled = documents.isNotEmpty(),
                        onClick = { showPdfPicker = true },
                        modifier = Modifier.weight(1f).testTag("ocr_pdf_btn"),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(Icons.Filled.Description, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("From PDF")
                    }
                }

                if (isProcessing) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0.1f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = PinkPrimary,
                        trackColor = PinkLight
                    )
                    Text(
                        text = "Recognizing text on-device...",
                        style = MaterialTheme.typography.bodySmall,
                        color = PinkDeep
                    )
                }

                if (ocrError != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = ocrError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                if (ocrSourceLabel.isNotEmpty()) {
                    Text(
                        text = "Source: $ocrSourceLabel",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                SelectionContainer {
                    OutlinedTextField(
                        value = ocrText,
                        onValueChange = onTextChange,
                        label = { Text("Extracted Text (Editable)") },
                        placeholder = { Text("Pick an image, scan a page, or select a PDF to extract text here...") },
                        minLines = 10,
                        maxLines = 18,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().testTag("ocr_result_input")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        enabled = ocrText.isNotBlank(),
                        onClick = {
                            clipboard.setText(AnnotatedString(ocrText))
                            onNotify("Copied all text to clipboard")
                        },
                        modifier = Modifier.weight(1f).testTag("ocr_copy_btn"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy")
                    }
                    OutlinedButton(
                        enabled = ocrText.isNotBlank(),
                        onClick = {
                            txtSaveLauncher.launch("PinkScan_OCR_${System.currentTimeMillis().toString().takeLast(4)}.txt")
                        },
                        modifier = Modifier.weight(1f).testTag("ocr_save_txt_btn"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.Download, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save TXT")
                    }
                    Button(
                        enabled = ocrText.isNotBlank(),
                        onClick = { showExportPdfDialog = true },
                        modifier = Modifier.weight(1f).testTag("ocr_export_pdf_btn"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Export PDF")
                    }
                }
            }
        }
    }

    if (showPdfPicker) {
        AlertDialog(
            onDismissRequest = { showPdfPicker = false },
            title = { Text("Extract Text from PDF", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    documents.forEach { doc ->
                        OutlinedButton(
                            onClick = {
                                showPdfPicker = false
                                onExtractFromPdf(doc)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("${doc.title}.pdf (${doc.pageCount} pg)", maxLines = 1)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPdfPicker = false }) { Text("Close") } }
        )
    }

    if (showExportPdfDialog) {
        var title by remember { mutableStateOf("OCR_Extract_${System.currentTimeMillis().toString().takeLast(4)}") }
        AlertDialog(
            onDismissRequest = { showExportPdfDialog = false },
            title = { Text("Export Text to PDF", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("PDF File Name") },
                    suffix = { Text(".pdf") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    enabled = title.isNotBlank(),
                    onClick = {
                        showExportPdfDialog = false
                        onExportPdf(title.trim())
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
                ) {
                    Text("Create PDF")
                }
            },
            dismissButton = { TextButton(onClick = { showExportPdfDialog = false }) { Text("Cancel") } }
        )
    }
}
