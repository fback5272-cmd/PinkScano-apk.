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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.PdfToolType

private data class ToolVisualMeta(
    val tool: PdfToolType,
    val icon: ImageVector,
    val accent: Color
)

@Composable
fun ToolsScreen(
    onSelectTool: (PdfToolType) -> Unit
) {
    val tools = remember {
        listOf(
            ToolVisualMeta(PdfToolType.IMAGES_TO_PDF, Icons.Filled.AddPhotoAlternate, Color(0xFFEC4899)),
            ToolVisualMeta(PdfToolType.MERGE_PDFS, Icons.Filled.Merge, Color(0xFFDB2777)),
            ToolVisualMeta(PdfToolType.SPLIT_PDF, Icons.Filled.ContentCut, Color(0xFF9333EA)),
            ToolVisualMeta(PdfToolType.COMPRESS_PDF, Icons.Filled.Compress, Color(0xFF0284C7)),
            ToolVisualMeta(PdfToolType.PDF_TO_IMAGES, Icons.Filled.Image, Color(0xFF0D9488)),
            ToolVisualMeta(PdfToolType.ROTATE_PAGES, Icons.AutoMirrored.Filled.RotateRight, Color(0xFFD97706)),
            ToolVisualMeta(PdfToolType.DELETE_PAGES, Icons.Filled.DeleteSweep, Color(0xFFE11D48)),
            ToolVisualMeta(PdfToolType.ADD_WATERMARK, Icons.Filled.WaterDrop, Color(0xFFEC4899)),
            ToolVisualMeta(PdfToolType.ADD_PAGE_NUMBERS, Icons.Filled.FormatListNumbered, Color(0xFF7C3AED)),
            ToolVisualMeta(PdfToolType.EXTRACT_TEXT, Icons.Filled.TextFields, Color(0xFF2563EB)),
            ToolVisualMeta(PdfToolType.SIGN_PDF, Icons.Filled.Draw, Color(0xFFDB2777)),
            ToolVisualMeta(PdfToolType.WORD_TO_PDF, Icons.Filled.Description, Color(0xFF059669)),
            ToolVisualMeta(PdfToolType.PDF_INFO, Icons.Filled.Info, Color(0xFF64748B)),
            ToolVisualMeta(PdfToolType.PRINT_PDF, Icons.Filled.Print, Color(0xFF475569))
        )
    }

    val pairedRows = remember(tools) { tools.chunked(2) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column {
                    Text(
                        text = "PDF Tools",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "14 local tools — fast, private & offline",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(pairedRows) { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowItems.forEach { meta ->
                        ToolGridCard(
                            meta = meta,
                            onClick = { onSelectTool(meta.tool) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowItems.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun ToolGridCard(
    meta: ToolVisualMeta,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .testTag("tool_card_${meta.tool.name}")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(meta.accent.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = meta.icon,
                        contentDescription = meta.tool.title,
                        tint = meta.accent,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = meta.tool.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = meta.tool.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = meta.tool.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
