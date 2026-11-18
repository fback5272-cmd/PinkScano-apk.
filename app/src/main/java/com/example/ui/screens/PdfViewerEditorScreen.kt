package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.data.local.DocumentEntity
import com.example.data.model.EditablePageState
import com.example.data.model.EditorColorSwatches
import com.example.data.model.NormalizedCropRect
import com.example.data.model.NormalizedPoint
import com.example.data.model.PdfImageOverlay
import com.example.data.model.PdfPenStroke
import com.example.data.model.PdfTextAnnotation
import com.example.data.repository.DocumentRepository
import com.example.engine.ImageProcessor
import com.example.engine.PdfEngine
import com.example.ui.components.ProcessingOverlay
import com.example.ui.components.SignaturePadDialog
import com.example.ui.theme.PinkDeep
import com.example.ui.theme.PinkLight
import com.example.ui.theme.PinkPrimary
import com.example.ui.theme.PureWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.roundToInt

private enum class EditorTool(val label: String) {
    VIEW("View"),
    PEN("Pen"),
    HIGHLIGHT("Highlight"),
    TEXT("Text"),
    SIGN("Sign"),
    IMAGE("Image"),
    ERASER("Eraser"),
    CROP("Crop")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerEditorScreen(
    document: DocumentEntity,
    initialEditMode: Boolean,
    initialSignMode: Boolean,
    savedSignaturePath: String?,
    pdfEngine: PdfEngine,
    imageProcessor: ImageProcessor,
    repository: DocumentRepository,
    onSaved: (DocumentEntity) -> Unit,
    onShare: (DocumentEntity) -> Unit,
    onPrint: (DocumentEntity) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sourceFile = remember(document.filePath) { File(document.filePath) }

    var isEditMode by remember { mutableStateOf(initialEditMode || initialSignMode) }
    var activeTool by remember { mutableStateOf(if (initialSignMode) EditorTool.SIGN else if (initialEditMode) EditorTool.PEN else EditorTool.VIEW) }
    var selectedColorIdx by remember { mutableIntStateOf(0) }
    var strokeThickness by remember { mutableFloatStateOf(0.008f) }

    val pageStates = remember(document.id) {
        mutableStateListOf<EditablePageState>().apply {
            repeat(document.pageCount.coerceAtLeast(1)) { add(EditablePageState(originalPageIndex = it)) }
        }
    }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var renderedPageBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val strokes = remember { mutableStateListOf<PdfPenStroke>() }
    val textAnnotations = remember { mutableStateListOf<PdfTextAnnotation>() }
    val imageOverlays = remember { mutableStateListOf<PdfImageOverlay>() }
    var activeDrawPoints by remember { mutableStateOf<List<NormalizedPoint>>(emptyList()) }

    // Zoom/Pan state in Reader Mode
    var zoomScale by remember(currentPageIndex, isEditMode) { mutableFloatStateOf(1f) }
    var panOffset by remember(currentPageIndex, isEditMode) { mutableStateOf(Offset.Zero) }

    var pageBoxSize by remember { mutableStateOf(IntSize(600, 840)) }
    var showAddTextDialog by remember { mutableStateOf(false) }
    var pendingTextPos by remember { mutableStateOf(NormalizedPoint(0.25f, 0.35f)) }
    var showSignaturePad by remember { mutableStateOf(initialSignMode) }
    var showInfoTooltip by remember { mutableStateOf(false) }
    var showSaveChoiceDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf("") }

    // Crop rect draft for active page
    var cropDraft by remember(currentPageIndex) {
        mutableStateOf(
            pageStates.getOrNull(currentPageIndex)?.cropRect ?: NormalizedCropRect(0.04f, 0.04f, 0.96f, 0.96f)
        )
    }

    val addImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val file = imageProcessor.copyUriToScanFile(uri)
                imageOverlays.add(
                    PdfImageOverlay(
                        pageIndex = currentPageIndex,
                        imagePath = file.absolutePath,
                        xRatio = 0.5f,
                        yRatio = 0.5f,
                        widthRatio = 0.34f,
                        heightRatio = 0.22f,
                        isSignature = false
                    )
                )
            }
        }
    }

    // Re-render current page whenever page index or originalPageIndex/rotation changes
    val currentState = pageStates.getOrNull(currentPageIndex)
    LaunchedEffect(currentPageIndex, currentState?.originalPageIndex, currentState?.rotationDelta, document.filePath) {
        val st = pageStates.getOrNull(currentPageIndex) ?: return@LaunchedEffect
        val bmp = withContext(Dispatchers.IO) {
            if (st.originalPageIndex < 0) {
                Bitmap.createBitmap(800, 1130, Bitmap.Config.ARGB_8888).apply {
                    android.graphics.Canvas(this).drawColor(android.graphics.Color.WHITE)
                }
            } else {
                val base = pdfEngine.renderPageBitmap(sourceFile, st.originalPageIndex, targetWidth = 1200)
                if (base != null && st.rotationDelta % 360 != 0) {
                    val m = android.graphics.Matrix().apply { postRotate(st.rotationDelta.toFloat()) }
                    Bitmap.createBitmap(base, 0, 0, base.width, base.height, m, true).also {
                        if (it !== base) base.recycle()
                    }
                } else {
                    base
                }
            }
        }
        renderedPageBitmap = bmp
    }

    fun performSave(overwriteExisting: Boolean, customName: String) {
        scope.launch {
            isSaving = true
            saveMessage = "Saving edited PDF..."
            runCatching {
                val outFile = pdfEngine.applyEditsToPdf(
                    sourcePdf = sourceFile,
                    pageStates = pageStates.toList(),
                    strokes = strokes.toList(),
                    texts = textAnnotations.toList(),
                    images = imageOverlays.toList()
                )
                val saved = if (overwriteExisting) {
                    repository.updateExistingPdf(document.id, outFile) ?: document
                } else {
                    repository.registerPdfFile(outFile, customName, "EDITED")
                }
                outFile.delete()
                saved
            }.onSuccess { savedDoc ->
                isSaving = false
                onSaved(savedDoc)
            }.onFailure {
                isSaving = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "${document.title}.pdf",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "Page ${currentPageIndex + 1} of ${pageStates.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("viewer_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showInfoTooltip = true }) {
                        Icon(Icons.Filled.Info, "Editor capabilities")
                    }
                    IconButton(
                        onClick = {
                            isEditMode = !isEditMode
                            activeTool = if (isEditMode) EditorTool.PEN else EditorTool.VIEW
                        },
                        modifier = Modifier.testTag("viewer_toggle_edit_btn")
                    ) {
                        Icon(
                            imageVector = if (isEditMode) Icons.Filled.Visibility else Icons.Filled.Edit,
                            contentDescription = if (isEditMode) "Reader mode" else "Edit PDF",
                            tint = if (isEditMode) PinkPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (isEditMode) {
                        IconButton(
                            onClick = { showSaveChoiceDialog = true },
                            modifier = Modifier.testTag("editor_save_btn")
                        ) {
                            Icon(Icons.Filled.Save, "Save edited PDF", tint = PinkPrimary)
                        }
                    } else {
                        IconButton(onClick = { onShare(document) }, modifier = Modifier.testTag("viewer_share_btn")) {
                            Icon(Icons.Filled.Share, "Share")
                        }
                        IconButton(onClick = { onPrint(document) }, modifier = Modifier.testTag("viewer_print_btn")) {
                            Icon(Icons.Filled.Print, "Print")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Main Page Viewport
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF231B21))
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = renderedPageBitmap
                    if (bmp != null) {
                        val aspect = bmp.width.toFloat() / bmp.height.coerceAtLeast(1)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(aspect.coerceIn(0.45f, 1.6f))
                                .clip(RoundedCornerShape(6.dp))
                                .background(PureWhite)
                                .graphicsLayer(
                                    scaleX = if (!isEditMode) zoomScale else 1f,
                                    scaleY = if (!isEditMode) zoomScale else 1f,
                                    translationX = if (!isEditMode) panOffset.x else 0f,
                                    translationY = if (!isEditMode) panOffset.y else 0f
                                )
                                .onSizeChanged { pageBoxSize = it }
                                .pointerInput(isEditMode, activeTool, currentPageIndex, selectedColorIdx, strokeThickness) {
                                    if (!isEditMode || activeTool == EditorTool.VIEW) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            val newZoom = (zoomScale * zoom).coerceIn(1f, 4f)
                                            zoomScale = newZoom
                                            panOffset = if (newZoom > 1f) panOffset + pan else Offset.Zero
                                        }
                                    } else if (activeTool == EditorTool.PEN || activeTool == EditorTool.HIGHLIGHT) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                val w = pageBoxSize.width.toFloat().coerceAtLeast(1f)
                                                val h = pageBoxSize.height.toFloat().coerceAtLeast(1f)
                                                activeDrawPoints = listOf(NormalizedPoint(offset.x / w, offset.y / h))
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                val w = pageBoxSize.width.toFloat().coerceAtLeast(1f)
                                                val h = pageBoxSize.height.toFloat().coerceAtLeast(1f)
                                                val pt = NormalizedPoint(
                                                    (change.position.x / w).coerceIn(0f, 1f),
                                                    (change.position.y / h).coerceIn(0f, 1f)
                                                )
                                                activeDrawPoints = activeDrawPoints + pt
                                            },
                                            onDragEnd = {
                                                if (activeDrawPoints.size >= 2) {
                                                    val color = EditorColorSwatches[selectedColorIdx].toArgb()
                                                    val isHL = activeTool == EditorTool.HIGHLIGHT
                                                    strokes.add(
                                                        PdfPenStroke(
                                                            pageIndex = currentPageIndex,
                                                            points = activeDrawPoints,
                                                            colorArgb = color,
                                                            strokeWidthRatio = if (isHL) strokeThickness * 3.2f else strokeThickness,
                                                            isHighlighter = isHL
                                                        )
                                                    )
                                                }
                                                activeDrawPoints = emptyList()
                                            },
                                            onDragCancel = { activeDrawPoints = emptyList() }
                                        )
                                    } else if (activeTool == EditorTool.TEXT) {
                                        detectTapGestures { offset ->
                                            val w = pageBoxSize.width.toFloat().coerceAtLeast(1f)
                                            val h = pageBoxSize.height.toFloat().coerceAtLeast(1f)
                                            pendingTextPos = NormalizedPoint(
                                                (offset.x / w).coerceIn(0.05f, 0.85f),
                                                (offset.y / h).coerceIn(0.05f, 0.9f)
                                            )
                                            showAddTextDialog = true
                                        }
                                    } else if (activeTool == EditorTool.ERASER) {
                                        detectTapGestures { offset ->
                                            val w = pageBoxSize.width.toFloat().coerceAtLeast(1f)
                                            val h = pageBoxSize.height.toFloat().coerceAtLeast(1f)
                                            val nx = offset.x / w
                                            val ny = offset.y / h
                                            val hitStroke = strokes.lastOrNull { s ->
                                                s.pageIndex == currentPageIndex &&
                                                    s.points.any { p -> hypot(p.x - nx, p.y - ny) < 0.06f }
                                            }
                                            if (hitStroke != null) strokes.remove(hitStroke)
                                        }
                                    }
                                }
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "PDF Page ${currentPageIndex + 1}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.FillBounds
                            )

                            // Pen & Highlight Canvas
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val w = size.width
                                val h = size.height
                                val pageStrokes = strokes.filter { it.pageIndex == currentPageIndex }
                                pageStrokes.forEach { s ->
                                    if (s.points.size >= 2) {
                                        val path = Path().apply {
                                            moveTo(s.points.first().x * w, s.points.first().y * h)
                                            for (i in 1 until s.points.size) {
                                                lineTo(s.points[i].x * w, s.points[i].y * h)
                                            }
                                        }
                                        val col = Color(s.colorArgb).copy(alpha = if (s.isHighlighter) 0.35f else 1f)
                                        drawPath(
                                            path = path,
                                            color = col,
                                            style = Stroke(
                                                width = (s.strokeWidthRatio * w).coerceAtLeast(2f),
                                                cap = StrokeCap.Round,
                                                join = StrokeJoin.Round
                                            )
                                        )
                                    }
                                }
                                if (activeDrawPoints.size >= 2) {
                                    val path = Path().apply {
                                        moveTo(activeDrawPoints.first().x * w, activeDrawPoints.first().y * h)
                                        for (i in 1 until activeDrawPoints.size) {
                                            lineTo(activeDrawPoints[i].x * w, activeDrawPoints[i].y * h)
                                        }
                                    }
                                    val isHL = activeTool == EditorTool.HIGHLIGHT
                                    val col = EditorColorSwatches[selectedColorIdx].copy(alpha = if (isHL) 0.35f else 1f)
                                    drawPath(
                                        path = path,
                                        color = col,
                                        style = Stroke(
                                            width = ((if (isHL) strokeThickness * 3.2f else strokeThickness) * w).coerceAtLeast(2f),
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }

                                // Crop overlay preview if CROP tool active
                                if (isEditMode && activeTool == EditorTool.CROP) {
                                    val left = cropDraft.left * w
                                    val top = cropDraft.top * h
                                    val right = cropDraft.right * w
                                    val bottom = cropDraft.bottom * h
                                    drawRect(
                                        color = PinkPrimary,
                                        topLeft = Offset(left, top),
                                        size = Size(right - left, bottom - top),
                                        style = Stroke(width = 4f)
                                    )
                                }
                            }

                            // Draggable Text Annotations
                            textAnnotations.filter { it.pageIndex == currentPageIndex }.forEach { txt ->
                                val w = pageBoxSize.width.toFloat().coerceAtLeast(1f)
                                val h = pageBoxSize.height.toFloat().coerceAtLeast(1f)
                                Text(
                                    text = txt.text,
                                    color = Color(txt.colorArgb),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .offset {
                                            IntOffset((txt.xRatio * w).roundToInt(), (txt.yRatio * h).roundToInt())
                                        }
                                        .background(PureWhite.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                                        .border(
                                            width = if (isEditMode) 1.dp else 0.dp,
                                            color = if (isEditMode) PinkPrimary.copy(alpha = 0.6f) else Color.Transparent,
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                        .pointerInput(txt.id, isEditMode) {
                                            if (!isEditMode) return@pointerInput
                                            detectDragGestures { change, drag ->
                                                change.consume()
                                                val idx = textAnnotations.indexOfFirst { it.id == txt.id }
                                                if (idx >= 0) {
                                                    val cur = textAnnotations[idx]
                                                    textAnnotations[idx] = cur.copy(
                                                        xRatio = (cur.xRatio + drag.x / w).coerceIn(0.02f, 0.88f),
                                                        yRatio = (cur.yRatio + drag.y / h).coerceIn(0.02f, 0.92f)
                                                    )
                                                }
                                            }
                                        }
                                        .clickable(enabled = isEditMode && activeTool == EditorTool.ERASER) {
                                            textAnnotations.removeAll { it.id == txt.id }
                                        }
                                )
                            }

                            // Draggable Image / Signature Overlays
                            imageOverlays.filter { it.pageIndex == currentPageIndex }.forEach { img ->
                                val w = pageBoxSize.width.toFloat().coerceAtLeast(1f)
                                val h = pageBoxSize.height.toFloat().coerceAtLeast(1f)
                                val boxW = (img.widthRatio * w).roundToInt().coerceAtLeast(40)
                                val boxH = (img.heightRatio * h).roundToInt().coerceAtLeast(30)
                                Image(
                                    painter = rememberAsyncImagePainter(File(img.imagePath)),
                                    contentDescription = if (img.isSignature) "Signature" else "Placed image",
                                    modifier = Modifier
                                        .offset {
                                            IntOffset(
                                                (img.xRatio * w - boxW / 2f).roundToInt(),
                                                (img.yRatio * h - boxH / 2f).roundToInt()
                                            )
                                        }
                                        .size(
                                            width = (boxW / context.resources.displayMetrics.density).dp,
                                            height = (boxH / context.resources.displayMetrics.density).dp
                                        )
                                        .border(
                                            width = if (isEditMode) 1.dp else 0.dp,
                                            color = if (isEditMode) PinkPrimary else Color.Transparent,
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .pointerInput(img.id, isEditMode) {
                                            if (!isEditMode) return@pointerInput
                                            detectDragGestures { change, drag ->
                                                change.consume()
                                                val idx = imageOverlays.indexOfFirst { it.id == img.id }
                                                if (idx >= 0) {
                                                    val cur = imageOverlays[idx]
                                                    imageOverlays[idx] = cur.copy(
                                                        xRatio = (cur.xRatio + drag.x / w).coerceIn(0.08f, 0.92f),
                                                        yRatio = (cur.yRatio + drag.y / h).coerceIn(0.08f, 0.92f)
                                                    )
                                                }
                                            }
                                        }
                                        .clickable(enabled = isEditMode && activeTool == EditorTool.ERASER) {
                                            imageOverlays.removeAll { it.id == img.id }
                                        },
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }
                }

                // Page Navigation Bar
                Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            enabled = currentPageIndex > 0,
                            onClick = { currentPageIndex-- }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous page")
                        }
                        Text(
                            text = "Page ${currentPageIndex + 1} / ${pageStates.size}" +
                                if (!isEditMode && zoomScale > 1.05f) "  (${String.format("%.1f", zoomScale)}x)" else "",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        IconButton(
                            enabled = currentPageIndex < pageStates.lastIndex,
                            onClick = { currentPageIndex++ }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next page")
                        }
                    }
                }

                // Editor Controls Panel
                if (isEditMode) {
                    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            // Tool Selector Row
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(EditorTool.entries) { _, tool ->
                                    val selected = activeTool == tool
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            activeTool = tool
                                            when (tool) {
                                                EditorTool.SIGN -> showSignaturePad = true
                                                EditorTool.IMAGE -> addImagePicker.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                                EditorTool.TEXT -> showAddTextDialog = true
                                                else -> Unit
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = when (tool) {
                                                    EditorTool.VIEW -> Icons.Filled.Visibility
                                                    EditorTool.PEN -> Icons.Filled.Brush
                                                    EditorTool.HIGHLIGHT -> Icons.Filled.BorderColor
                                                    EditorTool.TEXT -> Icons.Filled.TextFields
                                                    EditorTool.SIGN -> Icons.Filled.Draw
                                                    EditorTool.IMAGE -> Icons.Filled.AddPhotoAlternate
                                                    EditorTool.ERASER -> Icons.Filled.AutoFixNormal
                                                    EditorTool.CROP -> Icons.Filled.Crop
                                                },
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = { Text(tool.label) }
                                    )
                                }
                            }

                            // Color & Thickness when Pen/Highlight/Text active
                            if (activeTool == EditorTool.PEN || activeTool == EditorTool.HIGHLIGHT || activeTool == EditorTool.TEXT) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    EditorColorSwatches.forEachIndexed { idx, col ->
                                        Box(
                                            modifier = Modifier
                                                .padding(end = 8.dp)
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(col)
                                                .border(
                                                    width = if (idx == selectedColorIdx) 2.5.dp else 1.dp,
                                                    color = if (idx == selectedColorIdx) PinkPrimary else Color.LightGray,
                                                    shape = CircleShape
                                                )
                                                .clickable { selectedColorIdx = idx }
                                        )
                                    }
                                    if (activeTool != EditorTool.TEXT) {
                                        Slider(
                                            value = strokeThickness,
                                            onValueChange = { strokeThickness = it },
                                            valueRange = 0.003f..0.022f,
                                            colors = SliderDefaults.colors(thumbColor = PinkPrimary, activeTrackColor = PinkPrimary),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    IconButton(onClick = {
                                        val last = strokes.lastOrNull { it.pageIndex == currentPageIndex }
                                        if (last != null) strokes.remove(last)
                                    }) {
                                        Icon(Icons.AutoMirrored.Filled.Undo, "Undo stroke")
                                    }
                                }
                            }

                            // Crop adjustment sliders when CROP tool active
                            if (activeTool == EditorTool.CROP) {
                                Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Inset Margin", style = MaterialTheme.typography.labelSmall)
                                        Slider(
                                            value = cropDraft.left,
                                            onValueChange = { v ->
                                                cropDraft = NormalizedCropRect(v, v, 1f - v, 1f - v)
                                            },
                                            valueRange = 0f..0.28f,
                                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                                        )
                                        Button(
                                            onClick = {
                                                pageStates[currentPageIndex] = pageStates[currentPageIndex].copy(cropRect = cropDraft)
                                                activeTool = EditorTool.VIEW
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Text("Set Crop")
                                        }
                                    }
                                }
                            }

                            // Page Operations Row: Rotate, Blank Page, Reorder, Delete
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    OutlinedButton(
                                        onClick = {
                                            val st = pageStates[currentPageIndex]
                                            pageStates[currentPageIndex] = st.copy(rotationDelta = (st.rotationDelta + 90) % 360)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.RotateRight, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Rotate", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                item {
                                    OutlinedButton(
                                        onClick = {
                                            val insertAt = currentPageIndex + 1
                                            pageStates.add(insertAt, EditablePageState(originalPageIndex = -1))
                                            currentPageIndex = insertAt
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Filled.NoteAdd, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("+ Blank Page", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                item {
                                    OutlinedButton(
                                        enabled = currentPageIndex < pageStates.lastIndex,
                                        onClick = {
                                            val next = currentPageIndex + 1
                                            val moving = pageStates.removeAt(currentPageIndex)
                                            pageStates.add(next, moving)
                                            currentPageIndex = next
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Filled.SwapHoriz, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Move Right", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                item {
                                    OutlinedButton(
                                        enabled = pageStates.size > 1,
                                        onClick = {
                                            pageStates.removeAt(currentPageIndex)
                                            if (currentPageIndex >= pageStates.size) {
                                                currentPageIndex = pageStates.lastIndex
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Delete Pg", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            ProcessingOverlay(visible = isSaving, message = saveMessage)
        }
    }

    if (showAddTextDialog) {
        var input by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddTextDialog = false },
            title = { Text("Add Text Annotation", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Text to place on page") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    enabled = input.isNotBlank(),
                    onClick = {
                        textAnnotations.add(
                            PdfTextAnnotation(
                                pageIndex = currentPageIndex,
                                text = input.trim(),
                                xRatio = pendingTextPos.x,
                                yRatio = pendingTextPos.y,
                                colorArgb = EditorColorSwatches[selectedColorIdx].toArgb()
                            )
                        )
                        showAddTextDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
                ) {
                    Text("Place Text")
                }
            },
            dismissButton = { TextButton(onClick = { showAddTextDialog = false }) { Text("Cancel") } }
        )
    }

    var showDrawFreshSignature by remember { mutableStateOf(false) }

    if (showSignaturePad) {
        val hasSaved = savedSignaturePath != null && File(savedSignaturePath).exists()
        if (hasSaved && !showDrawFreshSignature) {
            AlertDialog(
                onDismissRequest = { showSignaturePad = false },
                title = { Text("Add Signature", fontWeight = FontWeight.Bold) },
                text = { Text("Use your saved signature or draw a new one for this PDF?") },
                confirmButton = {
                    Button(
                        onClick = {
                            imageOverlays.add(
                                PdfImageOverlay(
                                    pageIndex = currentPageIndex,
                                    imagePath = savedSignaturePath!!,
                                    xRatio = 0.5f,
                                    yRatio = 0.75f,
                                    widthRatio = 0.34f,
                                    heightRatio = 0.14f,
                                    isSignature = true
                                )
                            )
                            showSignaturePad = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
                    ) {
                        Text("Use Saved")
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { showDrawFreshSignature = true }) {
                            Text("Draw New")
                        }
                        TextButton(onClick = { showSignaturePad = false }) {
                            Text("Cancel")
                        }
                    }
                }
            )
        } else {
            SignaturePadDialog(
                onDismiss = {
                    showDrawFreshSignature = false
                    showSignaturePad = false
                },
                onConfirm = { bmp ->
                    val dir = File(context.cacheDir, "sig_temp").apply { mkdirs() }
                    val sigFile = File(dir, "sig_${UUID.randomUUID()}.png")
                    FileOutputStream(sigFile).use { out ->
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    bmp.recycle()
                    imageOverlays.add(
                        PdfImageOverlay(
                            pageIndex = currentPageIndex,
                            imagePath = sigFile.absolutePath,
                            xRatio = 0.5f,
                            yRatio = 0.72f,
                            widthRatio = 0.36f,
                            heightRatio = 0.15f,
                            isSignature = true
                        )
                    )
                    showDrawFreshSignature = false
                    showSignaturePad = false
                }
            )
        }
    }

    if (showInfoTooltip) {
        AlertDialog(
            onDismissRequest = { showInfoTooltip = false },
            title = { Text("On-Device PDF Editing", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "PinkScan natively supports adding text, highlights, freehand pen drawings, signatures, images, page rotation, deletion, reordering, inserting blank pages, and page cropping—all saved into a real PDF.\n\nNote: Modifying pre-existing embedded text glyphs inside external PDFs is not supported on-device; use Text or Eraser/Highlight overlays instead."
                )
            },
            confirmButton = { TextButton(onClick = { showInfoTooltip = false }) { Text("Got it") } }
        )
    }

    if (showSaveChoiceDialog) {
        var newName by remember { mutableStateOf("${document.title}_edited") }
        AlertDialog(
            onDismissRequest = { showSaveChoiceDialog = false },
            title = { Text("Save Edited PDF", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Save changes as a new PDF or overwrite the original document?")
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("New Copy Name") },
                        suffix = { Text(".pdf") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        showSaveChoiceDialog = false
                        performSave(overwriteExisting = false, customName = newName.trim())
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
                ) {
                    Text("Save as New")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSaveChoiceDialog = false
                    performSave(overwriteExisting = true, customName = document.title)
                }) {
                    Text("Overwrite Original")
                }
            }
        )
    }
}
