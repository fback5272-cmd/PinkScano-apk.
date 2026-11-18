package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.data.model.ImageFilter
import com.example.data.model.NormalizedPoint
import com.example.data.model.PageMargin
import com.example.data.model.PageOrientation
import com.example.data.model.PageSizeOption
import com.example.data.model.PdfCreationOptions
import com.example.data.model.ScannedPageItem
import com.example.ui.theme.PinkDeep
import com.example.ui.theme.PinkLight
import com.example.ui.theme.PinkPrimary
import com.example.ui.theme.PureWhite
import java.io.File
import kotlin.math.hypot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanReviewScreen(
    pages: List<ScannedPageItem>,
    activePageId: String?,
    defaultPrefix: String,
    defaultPageSize: PageSizeOption,
    defaultQuality: Int,
    onSelectPage: (String) -> Unit,
    onAddMorePages: () -> Unit,
    onRetakePage: (String) -> Unit,
    onDeletePage: (String) -> Unit,
    onApplyEdits: (
        pageId: String,
        corners: List<NormalizedPoint>?,
        rotationDelta: Int,
        filter: ImageFilter?,
        brightness: Float?,
        contrast: Float?
    ) -> Unit,
    onRunOcr: (ScannedPageItem) -> Unit,
    onSaveImageToUri: (ScannedPageItem, android.net.Uri) -> Unit,
    onGeneratePdf: (PdfCreationOptions) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val activePage = pages.find { it.id == activePageId } ?: pages.firstOrNull()
    var cropMode by remember(activePage?.id) { mutableStateOf(false) }
    var adjustPanelOpen by remember(activePage?.id) { mutableStateOf(false) }
    var showSavePdfDialog by remember { mutableStateOf(false) }

    val saveImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/jpeg")
    ) { uri ->
        if (uri != null && activePage != null) {
            onSaveImageToUri(activePage, uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (pages.isEmpty()) "Review Scans" else "Scan Review (${pages.size} pg)",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("review_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (activePage != null) {
                        IconButton(
                            onClick = { saveImageLauncher.launch("PinkScan_${System.currentTimeMillis()}.jpg") },
                            modifier = Modifier.testTag("review_save_img_btn")
                        ) {
                            Icon(Icons.Filled.Download, "Save image")
                        }
                        IconButton(
                            onClick = { onRunOcr(activePage) },
                            modifier = Modifier.testTag("review_ocr_btn")
                        ) {
                            Icon(Icons.Filled.TextFields, "Extract text OCR")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (activePage == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No pages in scan batch", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onAddMorePages,
                        colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
                    ) {
                        Text("Open Camera")
                    }
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Page Preview + Interactive 4-Corner Crop Overlay
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF151014))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (cropMode) {
                    DraggableCropOverlay(
                        imageFile = File(activePage.originalPath),
                        initialCorners = activePage.corners,
                        onApplyCrop = { newCorners ->
                            cropMode = false
                            onApplyEdits(activePage.id, newCorners, 0, null, null, null)
                        },
                        onCancel = { cropMode = false }
                    )
                } else {
                    Image(
                        painter = rememberAsyncImagePainter(File(activePage.processedPath)),
                        contentDescription = "Scanned page preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            // Optional Brightness / Contrast & Filters Drawer
            if (adjustPanelOpen && !cropMode) {
                var brightness by remember(activePage.id, activePage.brightness) {
                    mutableFloatStateOf(activePage.brightness)
                }
                var contrast by remember(activePage.id, activePage.contrast) {
                    mutableFloatStateOf(activePage.contrast)
                }
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(ImageFilter.entries) { _, filter ->
                                FilterChip(
                                    selected = activePage.filter == filter,
                                    onClick = {
                                        onApplyEdits(activePage.id, null, 0, filter, null, null)
                                    },
                                    label = { Text(filter.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PinkLight,
                                        selectedLabelColor = PinkDeep
                                    )
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Brightness", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(72.dp))
                            Slider(
                                value = brightness,
                                onValueChange = { brightness = it },
                                onValueChangeFinished = {
                                    onApplyEdits(activePage.id, null, 0, null, brightness, null)
                                },
                                valueRange = -50f..50f,
                                colors = SliderDefaults.colors(thumbColor = PinkPrimary, activeTrackColor = PinkPrimary),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Contrast", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(72.dp))
                            Slider(
                                value = contrast,
                                onValueChange = { contrast = it },
                                onValueChangeFinished = {
                                    onApplyEdits(activePage.id, null, 0, null, null, contrast)
                                },
                                valueRange = 0.6f..1.8f,
                                colors = SliderDefaults.colors(thumbColor = PinkPrimary, activeTrackColor = PinkPrimary),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Thumbnail strip if multiple pages
            if (pages.size > 1) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(pages, key = { _, p -> p.id }) { idx, item ->
                        val selected = item.id == activePage.id
                        Box(
                            modifier = Modifier
                                .size(width = 48.dp, height = 64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = if (selected) 2.5.dp else 1.dp,
                                    color = if (selected) PinkPrimary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onSelectPage(item.id) }
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(File(item.processedPath)),
                                contentDescription = "Page ${idx + 1}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Surface(
                                color = Color.Black.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp)
                            ) {
                                Text(
                                    text = "${idx + 1}",
                                    color = PureWhite,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Action Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IconTextAction("Crop", Icons.Filled.Crop, active = cropMode) {
                            cropMode = !cropMode
                        }
                        IconTextAction("Enhance", Icons.Filled.Tune, active = adjustPanelOpen) {
                            adjustPanelOpen = !adjustPanelOpen
                        }
                        IconTextAction("Left", Icons.AutoMirrored.Filled.RotateLeft) {
                            onApplyEdits(activePage.id, null, -90, null, null, null)
                        }
                        IconTextAction("Right", Icons.AutoMirrored.Filled.RotateRight) {
                            onApplyEdits(activePage.id, null, 90, null, null, null)
                        }
                        IconTextAction("Retake", Icons.Filled.Refresh) {
                            onRetakePage(activePage.id)
                        }
                        IconTextAction("Remove", Icons.Filled.Delete) {
                            onDeletePage(activePage.id)
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onAddMorePages,
                            modifier = Modifier.weight(1f).testTag("review_add_page_btn"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Filled.AddAPhoto, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add Page")
                        }
                        Button(
                            onClick = { showSavePdfDialog = true },
                            modifier = Modifier.weight(1.3f).testTag("review_save_pdf_btn"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Create PDF (${pages.size})")
                        }
                    }
                }
            }
        }
    }

    if (showSavePdfDialog) {
        var fileName by remember { mutableStateOf("${defaultPrefix}${System.currentTimeMillis().toString().takeLast(5)}") }
        var pageSize by remember { mutableStateOf(defaultPageSize) }
        var orientation by remember { mutableStateOf(PageOrientation.PORTRAIT) }
        var margin by remember { mutableStateOf(PageMargin.NARROW) }

        AlertDialog(
            onDismissRequest = { showSavePdfDialog = false },
            title = { Text("Save Scanned PDF", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = fileName,
                        onValueChange = { fileName = it },
                        label = { Text("PDF File Name") },
                        suffix = { Text(".pdf") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("scan_pdf_filename_input")
                    )
                    Text("Page Size", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PageSizeOption.entries.forEach { opt ->
                            FilterChip(
                                selected = pageSize == opt,
                                onClick = { pageSize = opt },
                                label = { Text(opt.label) }
                            )
                        }
                    }
                    Text("Orientation", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PageOrientation.entries.forEach { opt ->
                            FilterChip(
                                selected = orientation == opt,
                                onClick = { orientation = opt },
                                label = { Text(opt.label) }
                            )
                        }
                    }
                    Text("Margin", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PageMargin.entries.forEach { opt ->
                            FilterChip(
                                selected = margin == opt,
                                onClick = { margin = opt },
                                label = { Text(opt.label) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = fileName.isNotBlank(),
                    onClick = {
                        showSavePdfDialog = false
                        onGeneratePdf(
                            PdfCreationOptions(
                                fileName = fileName.trim(),
                                pageSize = pageSize,
                                orientation = orientation,
                                margin = margin,
                                imageQuality = defaultQuality
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary),
                    modifier = Modifier.testTag("confirm_save_scanned_pdf")
                ) {
                    Text("Save PDF")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSavePdfDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DraggableCropOverlay(
    imageFile: File,
    initialCorners: List<NormalizedPoint>,
    onApplyCrop: (List<NormalizedPoint>) -> Unit,
    onCancel: () -> Unit
) {
    var corners by remember(initialCorners) { mutableStateOf(initialCorners) }
    var boxSize by remember { mutableStateOf(IntSize(1, 1)) }
    var activeIndex by remember { mutableIntStateOf(-1) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { boxSize = it }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { start ->
                            val w = boxSize.width.toFloat().coerceAtLeast(1f)
                            val h = boxSize.height.toFloat().coerceAtLeast(1f)
                            activeIndex = corners.indices.minByOrNull { i ->
                                val cx = corners[i].x * w
                                val cy = corners[i].y * h
                                hypot(cx - start.x, cy - start.y)
                            } ?: -1
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (activeIndex in corners.indices) {
                                val w = boxSize.width.toFloat().coerceAtLeast(1f)
                                val h = boxSize.height.toFloat().coerceAtLeast(1f)
                                val cur = corners[activeIndex]
                                val updated = NormalizedPoint(
                                    x = (cur.x + dragAmount.x / w).coerceIn(0.01f, 0.99f),
                                    y = (cur.y + dragAmount.y / h).coerceIn(0.01f, 0.99f)
                                )
                                corners = corners.toMutableList().also { it[activeIndex] = updated }
                            }
                        },
                        onDragEnd = { activeIndex = -1 },
                        onDragCancel = { activeIndex = -1 }
                    )
                }
        ) {
            Image(
                painter = rememberAsyncImagePainter(imageFile),
                contentDescription = "Crop source",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val pts = corners.map { Offset(it.x * w, it.y * h) }
                if (pts.size == 4) {
                    val path = Path().apply {
                        moveTo(pts[0].x, pts[0].y)
                        lineTo(pts[1].x, pts[1].y)
                        lineTo(pts[2].x, pts[2].y)
                        lineTo(pts[3].x, pts[3].y)
                        close()
                    }
                    drawPath(path, color = PinkPrimary.copy(alpha = 0.16f))
                    drawPath(path, color = PinkPrimary, style = Stroke(width = 5f))
                    pts.forEach { p ->
                        drawCircle(color = PureWhite, radius = 18f, center = p)
                        drawCircle(color = PinkPrimary, radius = 12f, center = p)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("Cancel", color = PureWhite)
            }
            Button(
                onClick = { corners = ScannedPageItem.defaultCorners() },
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                modifier = Modifier.weight(1f)
            ) {
                Text("Reset")
            }
            Button(
                onClick = { onApplyCrop(corners) },
                colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary),
                modifier = Modifier.weight(1.2f)
            ) {
                Text("Apply Crop")
            }
        }
    }
}

@Composable
private fun IconTextAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) PinkPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) PinkPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
