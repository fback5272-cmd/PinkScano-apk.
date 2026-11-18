package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.rememberAsyncImagePainter
import com.example.data.model.ScannedPageItem
import com.example.data.model.ScannerMode
import com.example.ui.theme.PinkLight
import com.example.ui.theme.PinkPrimary
import com.example.ui.theme.PureWhite
import java.io.File
import java.util.UUID

@Composable
fun ScannerScreen(
    scannedPages: List<ScannedPageItem>,
    onBack: () -> Unit,
    onCapturedFile: (File, ScannerMode) -> Unit,
    onPickedGalleryImages: (List<android.net.Uri>) -> Unit,
    onReviewBatch: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris ->
        if (uris.isNotEmpty()) {
            onPickedGalleryImages(uris)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var selectedMode by remember { mutableStateOf(ScannerMode.DOCUMENTS) }
    var useFrontCamera by remember { mutableStateOf(false) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var hasFlashUnit by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    val previewView = remember { PreviewView(context) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(hasCameraPermission, useFrontCamera, flashMode) {
        if (!hasCameraPermission) return@DisposableEffect onDispose {}
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        val listener = Runnable {
            runCatching {
                val provider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setFlashMode(flashMode)
                    .build()
                val selector = if (useFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                provider.unbindAll()
                val cam = provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                boundCamera = cam
                hasFlashUnit = cam.cameraInfo.hasFlashUnit()
                imageCapture = capture
                cameraError = null
            }.onFailure { err ->
                Log.e("PinkScanCamera", "Camera bind error", err)
                cameraError = "Camera preview unavailable on this device (${err.localizedMessage ?: "hardware error"}). You can still import photos from Gallery."
            }
        }
        cameraProviderFuture.addListener(listener, executor)
        onDispose {
            runCatching { cameraProviderFuture.get().unbindAll() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        if (hasCameraPermission && cameraError == null) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
            ScannerGuideOverlay(mode = selectedMode, modifier = Modifier.fillMaxSize())
        } else {
            // Permission rationale or graceful camera fallback
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(PinkPrimary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = null,
                        tint = PinkPrimary,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    text = if (!hasCameraPermission) "Camera Access Needed" else "Camera Unavailable",
                    style = MaterialTheme.typography.headlineSmall,
                    color = PureWhite,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = cameraError
                        ?: "PinkScan needs camera permission to scan physical documents, receipts, IDs, and books on-device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PureWhite.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(22.dp))
                if (!hasCameraPermission) {
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.testTag("grant_camera_btn")
                    ) {
                        Text("Allow Camera")
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Button(
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PureWhite.copy(alpha = 0.16f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Filled.PhotoLibrary, null, tint = PureWhite)
                    Spacer(Modifier.width(8.dp))
                    Text("Import from Gallery", color = PureWhite)
                }
            }
        }

        // Top Bar Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("scanner_back_btn")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = PureWhite)
            }

            Text(
                text = selectedMode.guideRatio,
                style = MaterialTheme.typography.titleSmall,
                color = PureWhite,
                fontWeight = FontWeight.SemiBold
            )

            Row {
                if (hasFlashUnit) {
                    IconButton(
                        onClick = {
                            flashMode = when (flashMode) {
                                ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                                ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                                else -> ImageCapture.FLASH_MODE_OFF
                            }
                        },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("scanner_flash_btn")
                    ) {
                        Icon(
                            imageVector = when (flashMode) {
                                ImageCapture.FLASH_MODE_ON -> Icons.Filled.FlashOn
                                ImageCapture.FLASH_MODE_AUTO -> Icons.Filled.FlashAuto
                                else -> Icons.Filled.FlashOff
                            },
                            contentDescription = "Toggle Flash",
                            tint = if (flashMode == ImageCapture.FLASH_MODE_ON) PinkPrimary else PureWhite
                        )
                    }
                }
                IconButton(
                    onClick = { useFrontCamera = !useFrontCamera },
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("scanner_flip_btn")
                ) {
                    Icon(Icons.Filled.FlipCameraAndroid, "Switch camera", tint = PureWhite)
                }
            }
        }

        // Bottom Controls Dock
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(top = 10.dp, bottom = 16.dp)
        ) {
            // Mode selector
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ScannerMode.entries) { mode ->
                    val selected = mode == selectedMode
                    Surface(
                        onClick = { selectedMode = mode },
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) PinkPrimary else PureWhite.copy(alpha = 0.14f),
                        modifier = Modifier.testTag("scanner_mode_${mode.name}")
                    ) {
                        Text(
                            text = mode.label,
                            color = PureWhite,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Gallery button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(PureWhite.copy(alpha = 0.18f))
                            .testTag("scanner_gallery_btn")
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, "Import photos", tint = PureWhite)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Gallery", color = PureWhite, style = MaterialTheme.typography.labelSmall)
                }

                // Shutter Button
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .border(4.dp, PureWhite, CircleShape)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(PinkPrimary)
                        .testTag("scanner_capture_btn")
                        .clickable(enabled = hasCameraPermission && imageCapture != null) {
                            val capture = imageCapture ?: return@clickable
                            val scansDir = File(context.filesDir, "pinkscan_scans").apply { mkdirs() }
                            val photoFile = File(scansDir, "cam_${UUID.randomUUID()}.jpg")
                            val outputOpts = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                            capture.takePicture(
                                outputOpts,
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        onCapturedFile(photoFile, selectedMode)
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        Log.e("PinkScanCamera", "Capture failed", exception)
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.CameraAlt, "Capture document", tint = PureWhite, modifier = Modifier.size(30.dp))
                }

                // Batch Done / Review Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val lastThumb = scannedPages.lastOrNull()?.processedPath?.let { File(it) }
                    BadgedBox(
                        badge = {
                            if (scannedPages.isNotEmpty()) {
                                Badge(containerColor = PinkPrimary) {
                                    Text("${scannedPages.size}")
                                }
                            }
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (scannedPages.isNotEmpty()) PinkLight else PureWhite.copy(alpha = 0.14f))
                                .clickable(enabled = scannedPages.isNotEmpty(), onClick = onReviewBatch)
                                .testTag("scanner_done_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (lastThumb != null && lastThumb.exists()) {
                                Image(
                                    painter = rememberAsyncImagePainter(lastThumb),
                                    contentDescription = "Review scans",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Filled.Check, "Review scans", tint = PureWhite.copy(alpha = 0.5f))
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (scannedPages.isEmpty()) "0 Pages" else "Next",
                        color = PureWhite,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun ScannerGuideOverlay(mode: ScannerMode, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val marginX = size.width * 0.10f
        val availW = size.width - marginX * 2
        val targetH = (availW / mode.aspectHint).coerceAtMost(size.height * 0.58f)
        val targetW = (targetH * mode.aspectHint).coerceAtMost(availW)
        val left = (size.width - targetW) / 2f
        val top = (size.height - targetH) / 2f - size.height * 0.04f
        val right = left + targetW
        val bottom = top + targetH
        val corner = 44f

        // Subtle boundary box
        drawRect(
            color = PinkPrimary.copy(alpha = 0.25f),
            topLeft = Offset(left, top),
            size = androidx.compose.ui.geometry.Size(targetW, targetH),
            style = Stroke(width = 2f)
        )

        val brackets = Path().apply {
            moveTo(left, top + corner); lineTo(left, top); lineTo(left + corner, top)
            moveTo(right - corner, top); lineTo(right, top); lineTo(right, top + corner)
            moveTo(right, bottom - corner); lineTo(right, bottom); lineTo(right - corner, bottom)
            moveTo(left + corner, bottom); lineTo(left, bottom); lineTo(left, bottom - corner)
        }
        drawPath(
            path = brackets,
            color = PinkPrimary,
            style = Stroke(width = 8f, cap = StrokeCap.Round)
        )
    }
}
