package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.DocumentEntity
import com.example.data.model.NotificationEntry
import com.example.data.model.PdfToolType
import com.example.data.model.StorageStats
import com.example.ui.components.DocumentRowCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.GradientPinkButton
import com.example.ui.components.PinkScanLogo
import com.example.ui.components.formatBytes
import com.example.ui.components.formatDate
import com.example.ui.theme.PinkDeep
import com.example.ui.theme.PinkLight
import com.example.ui.theme.PinkPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    documents: List<DocumentEntity>,
    storageStats: StorageStats,
    notifications: List<NotificationEntry>,
    onScanNow: () -> Unit,
    onGalleryToPdf: () -> Unit,
    onImportPdf: () -> Unit,
    onOcrExtract: () -> Unit,
    onOpenTool: (PdfToolType) -> Unit,
    onViewAllDocuments: () -> Unit,
    onOpenSettings: () -> Unit,
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
    var showNotificationsSheet by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PinkScanLogo(size = 46.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PinkScan",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Ready to scan & organize PDFs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { showNotificationsSheet = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("home_notifications_btn")
                    ) {
                        BadgedBox(
                            badge = {
                                if (notifications.isNotEmpty()) {
                                    Badge(containerColor = PinkPrimary) {
                                        Text("${notifications.size.coerceAtMost(9)}")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Notifications,
                                contentDescription = "Recent activity",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("home_settings_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Large Gradient Pink Scan Now CTA
            item {
                GradientPinkButton(
                    text = "Scan Now",
                    subtitle = "Camera scanner with crop & enhancement",
                    icon = Icons.Filled.DocumentScanner,
                    onClick = onScanNow,
                    testTag = "home_scan_now_btn"
                )
            }

            // 4 Main Quick Actions (2x2 grid)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MainActionTile(
                            title = "Scan Document",
                            subtitle = "Multi-page cam",
                            icon = Icons.Filled.DocumentScanner,
                            tint = PinkPrimary,
                            onClick = onScanNow,
                            testTag = "action_scan_doc",
                            modifier = Modifier.weight(1f)
                        )
                        MainActionTile(
                            title = "Gallery to PDF",
                            subtitle = "Photos to PDF",
                            icon = Icons.Filled.AddPhotoAlternate,
                            tint = Color(0xFFE11D48),
                            onClick = onGalleryToPdf,
                            testTag = "action_gallery_pdf",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MainActionTile(
                            title = "Import PDF",
                            subtitle = "Open from device",
                            icon = Icons.Filled.FileOpen,
                            tint = Color(0xFF9333EA),
                            onClick = onImportPdf,
                            testTag = "action_import_pdf",
                            modifier = Modifier.weight(1f)
                        )
                        MainActionTile(
                            title = "OCR Extractor",
                            subtitle = "Image to text",
                            icon = Icons.Filled.TextFields,
                            tint = Color(0xFF0284C7),
                            onClick = onOcrExtract,
                            testTag = "action_ocr",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Quick Tools Row
            item {
                Column {
                    SectionHeader(
                        title = "Quick Tools",
                        actionText = "All 14 Tools",
                        onAction = { onOpenTool(PdfToolType.MERGE_PDFS) }
                    )
                    Spacer(Modifier.height(8.dp))
                    val quickTools = remember {
                        listOf(
                            Triple(PdfToolType.MERGE_PDFS, Icons.Filled.Merge, "Merge"),
                            Triple(PdfToolType.SPLIT_PDF, Icons.Filled.ContentCut, "Split"),
                            Triple(PdfToolType.COMPRESS_PDF, Icons.Filled.Compress, "Compress"),
                            Triple(PdfToolType.SIGN_PDF, Icons.Filled.Draw, "Sign"),
                            Triple(PdfToolType.ADD_WATERMARK, Icons.Filled.WaterDrop, "Watermark"),
                            Triple(PdfToolType.WORD_TO_PDF, Icons.Filled.Description, "Word to PDF")
                        )
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(quickTools) { (tool, icon, label) ->
                            QuickToolChip(
                                label = label,
                                icon = icon,
                                onClick = { onOpenTool(tool) },
                                testTag = "quick_tool_${tool.name}"
                            )
                        }
                    }
                }
            }

            // Storage Summary Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(PinkLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Storage, null, tint = PinkDeep, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "On-Device Storage",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${storageStats.documentCount} PDF${if (storageStats.documentCount == 1) "" else "s"} • ${formatBytes(storageStats.pdfBytes)} used",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "100% Private",
                                style = MaterialTheme.typography.labelSmall,
                                color = PinkDeep,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        val ratio = (storageStats.totalBytes.toFloat() / (100f * 1024f * 1024f)).coerceIn(0.03f, 1f)
                        LinearProgressIndicator(
                            progress = { if (storageStats.documentCount == 0) 0f else ratio },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            color = PinkPrimary,
                            trackColor = PinkLight
                        )
                    }
                }
            }

            // Recent Documents
            item {
                SectionHeader(
                    title = "Recent Documents",
                    actionText = if (documents.isNotEmpty()) "All PDFs (${documents.size})" else null,
                    onAction = onViewAllDocuments
                )
            }

            if (documents.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Filled.PictureAsPdf,
                        title = "No Documents Yet",
                        subtitle = "Tap Scan Now, convert Gallery photos, or import a PDF from your device to get started.",
                        actionLabel = "Create PDF from Photos",
                        onAction = onGalleryToPdf
                    )
                }
            } else {
                items(documents.take(5), key = { it.id }) { doc ->
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

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (showNotificationsSheet) {
        ModalBottomSheet(onDismissRequest = { showNotificationsSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    text = "Recent Activity",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                if (notifications.isEmpty()) {
                    Text(
                        text = "No recent activity yet. Your scans, exports, and edits will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    notifications.take(8).forEach { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(10.dp).clip(CircleShape).background(PinkPrimary)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(entry.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(formatDate(entry.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .testTag(testTag)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tint.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = tint, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun QuickToolChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.testTag(testTag)
    ) {
        Column(
            modifier = Modifier.width(88.dp).padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(PinkLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = label, tint = PinkDeep, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    actionText: String?,
    onAction: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        if (actionText != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionText, color = PinkDeep, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
