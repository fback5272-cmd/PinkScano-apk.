package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.data.model.PageMargin
import com.example.data.model.PageOrientation
import com.example.data.model.PageSizeOption
import com.example.data.model.PdfCreationOptions
import com.example.data.model.ScannedPageItem
import com.example.ui.components.EmptyStateCard
import com.example.ui.theme.PinkDeep
import com.example.ui.theme.PinkLight
import com.example.ui.theme.PinkPrimary
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagesToPdfScreen(
    pages: List<ScannedPageItem>,
    defaultPrefix: String,
    defaultPageSize: PageSizeOption,
    defaultQuality: Int,
    onAddImages: (List<android.net.Uri>) -> Unit,
    onMovePage: (Int, Int) -> Unit,
    onRotatePage: (String) -> Unit,
    onCropEnhancePage: (String) -> Unit,
    onRemovePage: (String) -> Unit,
    onClearAll: () -> Unit,
    onGeneratePdf: (PdfCreationOptions) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(30)
    ) { uris ->
        if (uris.isNotEmpty()) onAddImages(uris)
    }

    var fileName by remember { mutableStateOf("${defaultPrefix}Gallery_${System.currentTimeMillis().toString().takeLast(4)}") }
    var pageSize by remember { mutableStateOf(defaultPageSize) }
    var orientation by remember { mutableStateOf(PageOrientation.PORTRAIT) }
    var margin by remember { mutableStateOf(PageMargin.NARROW) }
    var quality by remember { mutableIntStateOf(defaultQuality) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gallery to PDF", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("g2p_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (pages.isNotEmpty()) {
                        IconButton(
                            onClick = { showSettingsSheet = true },
                            modifier = Modifier.testTag("g2p_settings_btn")
                        ) {
                            Icon(Icons.Filled.Tune, "PDF settings")
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
                    .padding(horizontal = 16.dp)
            ) {
                if (pages.isEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    EmptyStateCard(
                        icon = Icons.Filled.AddPhotoAlternate,
                        title = "Select Gallery Photos",
                        subtitle = "Choose JPG, JPEG, PNG, or WEBP images from your device to reorder, crop, enhance, and build a crisp PDF.",
                        actionLabel = "Pick Images",
                        onAction = {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${pages.size} image${if (pages.size == 1) "" else "s"} • ${pageSize.label} • ${quality}%",
                            style = MaterialTheme.typography.titleSmall,
                            color = PinkDeep,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("g2p_add_more_btn")
                            ) {
                                Icon(Icons.Filled.AddPhotoAlternate, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add More")
                            }
                            OutlinedButton(
                                onClick = onClearAll,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("Clear")
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        itemsIndexed(pages, key = { _, item -> item.id }) { idx, page ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Image(
                                        painter = rememberAsyncImagePainter(File(page.processedPath)),
                                        contentDescription = "Page ${idx + 1}",
                                        modifier = Modifier
                                            .size(width = 64.dp, height = 84.dp)
                                            .clip(RoundedCornerShape(10.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Page ${idx + 1}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Filter: ${page.filter.label} • ${page.rotationDegrees}°",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            OutlinedButton(
                                                onClick = { onRotatePage(page.id) },
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Icon(Icons.AutoMirrored.Filled.RotateRight, "Rotate", modifier = Modifier.size(16.dp))
                                            }
                                            OutlinedButton(
                                                onClick = { onCropEnhancePage(page.id) },
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Icon(Icons.Filled.Crop, null, modifier = Modifier.size(15.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Crop/Filter", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        IconButton(
                                            enabled = idx > 0,
                                            onClick = { onMovePage(idx, idx - 1) }
                                        ) {
                                            Icon(Icons.Filled.ArrowUpward, "Move up")
                                        }
                                        IconButton(
                                            enabled = idx < pages.lastIndex,
                                            onClick = { onMovePage(idx, idx + 1) }
                                        ) {
                                            Icon(Icons.Filled.ArrowDownward, "Move down")
                                        }
                                    }
                                    IconButton(onClick = { onRemovePage(page.id) }) {
                                        Icon(Icons.Filled.Close, "Remove image")
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Generate Bar
                    OutlinedTextField(
                        value = fileName,
                        onValueChange = { fileName = it },
                        label = { Text("PDF Filename") },
                        suffix = { Text(".pdf") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("g2p_filename_input")
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showSettingsSheet = true },
                            modifier = Modifier.weight(0.8f).height(52.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Filled.Tune, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Options")
                        }
                        Button(
                            enabled = fileName.isNotBlank(),
                            onClick = {
                                onGeneratePdf(
                                    PdfCreationOptions(
                                        fileName = fileName.trim(),
                                        pageSize = pageSize,
                                        orientation = orientation,
                                        margin = margin,
                                        imageQuality = quality
                                    )
                                )
                            },
                            modifier = Modifier.weight(1.4f).height(52.dp).testTag("g2p_create_pdf_btn"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Create PDF", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showSettingsSheet) {
        ModalBottomSheet(onDismissRequest = { showSettingsSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("PDF Layout & Quality", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                Text("Page Size", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PageSizeOption.entries.forEach { opt ->
                        FilterChip(
                            selected = pageSize == opt,
                            onClick = { pageSize = opt },
                            label = { Text(opt.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PinkLight,
                                selectedLabelColor = PinkDeep
                            )
                        )
                    }
                }

                Text("Orientation", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PageOrientation.entries.forEach { opt ->
                        FilterChip(
                            selected = orientation == opt,
                            onClick = { orientation = opt },
                            label = { Text(opt.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PinkLight,
                                selectedLabelColor = PinkDeep
                            )
                        )
                    }
                }

                Text("Page Margins", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PageMargin.entries.forEach { opt ->
                        FilterChip(
                            selected = margin == opt,
                            onClick = { margin = opt },
                            label = { Text(opt.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PinkLight,
                                selectedLabelColor = PinkDeep
                            )
                        )
                    }
                }

                Text("Image Compression Quality: $quality%", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = quality.toFloat(),
                    onValueChange = { quality = it.roundToInt() },
                    valueRange = 40f..100f,
                    colors = SliderDefaults.colors(thumbColor = PinkPrimary, activeTrackColor = PinkPrimary)
                )

                Button(
                    onClick = { showSettingsSheet = false },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Done")
                }
            }
        }
    }
}
