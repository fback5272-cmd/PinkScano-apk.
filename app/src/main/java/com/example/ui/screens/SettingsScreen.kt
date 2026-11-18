package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.data.local.UserPreferences
import com.example.data.model.AppLanguage
import com.example.data.model.PageSizeOption
import com.example.data.model.StorageStats
import com.example.data.model.ThemeMode
import com.example.ui.components.DeleteConfirmDialog
import com.example.ui.components.PinkScanLogo
import com.example.ui.components.SignaturePadDialog
import com.example.ui.components.formatBytes
import com.example.ui.theme.PinkDeep
import com.example.ui.theme.PinkLight
import com.example.ui.theme.PinkPrimary
import com.example.ui.theme.PureWhite
import java.io.File
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    storageStats: StorageStats,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetDefaultPageSize: (PageSizeOption) -> Unit,
    onSetDefaultQuality: (Int) -> Unit,
    onSetDefaultFilePrefix: (String) -> Unit,
    onSaveSignature: (Bitmap) -> Unit,
    onClearSignature: () -> Unit,
    onClearAllDocuments: () -> Unit,
    onReplayOnboarding: () -> Unit
) {
    var showPrefixDialog by remember { mutableStateOf(false) }
    var showSignaturePad by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Appearance Card
            item {
                SettingsCard(title = "Appearance & Theme", icon = Icons.Filled.DarkMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Dark Mode", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("Switch between Pink & White or Dark Rose", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = preferences.themeMode == ThemeMode.DARK,
                            onCheckedChange = { checked ->
                                onSetThemeMode(if (checked) ThemeMode.DARK else ThemeMode.LIGHT)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = PureWhite, checkedTrackColor = PinkPrimary),
                            modifier = Modifier.testTag("settings_dark_mode_switch")
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = preferences.themeMode == mode,
                                onClick = { onSetThemeMode(mode) },
                                label = { Text(mode.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PinkLight,
                                    selectedLabelColor = PinkDeep
                                )
                            )
                        }
                    }
                }
            }

            // Language Card
            item {
                SettingsCard(title = "Language & OCR Script", icon = Icons.Filled.Language) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppLanguage.entries.forEach { lang ->
                            FilterChip(
                                selected = preferences.language == lang,
                                onClick = { onSetLanguage(lang) },
                                label = { Text(lang.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PinkLight,
                                    selectedLabelColor = PinkDeep
                                )
                            )
                        }
                    }
                }
            }

            // PDF Defaults Card
            item {
                SettingsCard(title = "PDF Creation Defaults", icon = Icons.Filled.PictureAsPdf) {
                    Text("Default Page Size", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PageSizeOption.entries.forEach { size ->
                            FilterChip(
                                selected = preferences.defaultPageSize == size,
                                onClick = { onSetDefaultPageSize(size) },
                                label = { Text(size.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PinkLight,
                                    selectedLabelColor = PinkDeep
                                )
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text("Default Image Quality: ${preferences.defaultImageQuality}%", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = preferences.defaultImageQuality.toFloat(),
                        onValueChange = { onSetDefaultQuality(it.roundToInt()) },
                        valueRange = 40f..100f,
                        colors = SliderDefaults.colors(thumbColor = PinkPrimary, activeTrackColor = PinkPrimary)
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showPrefixDialog = true }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Default File Name Prefix", style = MaterialTheme.typography.titleSmall)
                            Text(preferences.defaultFilePrefix, style = MaterialTheme.typography.bodySmall, color = PinkDeep)
                        }
                        Text("Edit", color = PinkPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Saved Signature Card
            item {
                SettingsCard(title = "Saved Signature", icon = Icons.Filled.Draw) {
                    val sigFile = preferences.savedSignaturePath?.let { File(it) }
                    if (sigFile != null && sigFile.exists()) {
                        Image(
                            painter = rememberAsyncImagePainter(sigFile),
                            contentDescription = "Saved signature",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(86.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(PureWhite),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { showSignaturePad = true }, modifier = Modifier.weight(1f)) {
                                Text("Redraw")
                            }
                            OutlinedButton(onClick = onClearSignature, modifier = Modifier.weight(1f)) {
                                Text("Remove", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    } else {
                        Text(
                            text = "Save a reusable signature for fast 1-tap PDF signing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { showSignaturePad = true },
                            colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Create Saved Signature")
                        }
                    }
                }
            }

            // Storage Management Card
            item {
                SettingsCard(title = "Storage Information", icon = Icons.Filled.Storage) {
                    Text(
                        text = "${storageStats.documentCount} saved PDFs (${formatBytes(storageStats.pdfBytes)}) • Cache ${formatBytes(storageStats.cacheBytes)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        enabled = storageStats.documentCount > 0 || storageStats.totalBytes > 0L,
                        onClick = { showClearConfirm = true },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("settings_clear_storage_btn")
                    ) {
                        Icon(Icons.Filled.DeleteForever, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text("Clear Local Documents", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Privacy & About Card
            item {
                SettingsCard(title = "Privacy & About PinkScan", icon = Icons.Filled.Info) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PinkScanLogo(size = 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("PinkScan v1.0", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Scan • Create • Edit • Share", style = MaterialTheme.typography.bodySmall, color = PinkDeep)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showPrivacyDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Lock, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Privacy")
                        }
                        OutlinedButton(
                            onClick = onReplayOnboarding,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Replay, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Tour")
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (showPrefixDialog) {
        var prefix by remember { mutableStateOf(preferences.defaultFilePrefix) }
        AlertDialog(
            onDismissRequest = { showPrefixDialog = false },
            title = { Text("Default File Prefix", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = prefix,
                    onValueChange = { prefix = it },
                    singleLine = true,
                    label = { Text("Prefix") }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSetDefaultFilePrefix(prefix)
                        showPrefixDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
                ) {
                    Text("Save")
                }
            },
            dismissButton = { TextButton(onClick = { showPrefixDialog = false }) { Text("Cancel") } }
        )
    }

    if (showSignaturePad) {
        SignaturePadDialog(
            onDismiss = { showSignaturePad = false },
            onConfirm = { bmp ->
                onSaveSignature(bmp)
                showSignaturePad = false
            }
        )
    }

    if (showClearConfirm) {
        DeleteConfirmDialog(
            title = "Clear All Local Documents?",
            message = "This permanently deletes all saved PDFs and thumbnails from PinkScan on this device. Exported copies in Downloads remain untouched.",
            onDismiss = { showClearConfirm = false },
            onConfirm = {
                showClearConfirm = false
                onClearAllDocuments()
            }
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Offline Privacy Guarantee", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "PinkScan processes every camera scan, gallery image, OCR text extraction, signature, watermark, and PDF edit 100% locally on your device. No API keys are used, and no private documents are ever uploaded to external servers."
                )
            },
            confirmButton = { TextButton(onClick = { showPrivacyDialog = false }) { Text("OK") } }
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = PinkPrimary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
