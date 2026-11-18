package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.data.local.DocumentDao
import com.example.data.local.DocumentEntity
import com.example.data.model.StorageStats
import com.example.engine.PdfEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class DocumentRepository(
    private val context: Context,
    private val documentDao: DocumentDao,
    private val pdfEngine: PdfEngine
) {
    val docsDir: File by lazy { File(context.filesDir, "pinkscan_docs").apply { mkdirs() } }
    val thumbsDir: File by lazy { File(context.filesDir, "pinkscan_thumbs").apply { mkdirs() } }
    val scansDir: File by lazy { File(context.filesDir, "pinkscan_scans").apply { mkdirs() } }
    val exportsDir: File by lazy { File(context.filesDir, "pinkscan_exports").apply { mkdirs() } }

    fun observeDocuments(): Flow<List<DocumentEntity>> = documentDao.observeAllDocuments()

    suspend fun getDocument(id: String): DocumentEntity? = documentDao.getById(id)

    suspend fun registerPdfFile(
        file: File,
        title: String,
        sourceType: String
    ): DocumentEntity = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val sanitizedTitle = sanitizeTitle(title)
        val destFile = File(docsDir, "${id}.pdf")
        if (file.absolutePath != destFile.absolutePath) {
            file.copyTo(destFile, overwrite = true)
        }
        val pageCount = pdfEngine.getPageCount(destFile)
        val thumbFile = File(thumbsDir, "${id}_thumb.png")
        generateThumbnail(destFile, thumbFile)
        val now = System.currentTimeMillis()
        val entity = DocumentEntity(
            id = id,
            title = sanitizedTitle,
            filePath = destFile.absolutePath,
            thumbnailPath = if (thumbFile.exists()) thumbFile.absolutePath else null,
            pageCount = pageCount.coerceAtLeast(1),
            fileSizeBytes = destFile.length(),
            createdAt = now,
            updatedAt = now,
            isFavorite = false,
            sourceType = sourceType
        )
        documentDao.insert(entity)
        entity
    }

    suspend fun updateExistingPdf(
        documentId: String,
        newPdfFile: File,
        newTitle: String? = null
    ): DocumentEntity? = withContext(Dispatchers.IO) {
        val existing = documentDao.getById(documentId) ?: return@withContext null
        val destFile = File(existing.filePath)
        if (newPdfFile.absolutePath != destFile.absolutePath) {
            newPdfFile.copyTo(destFile, overwrite = true)
        }
        val pageCount = pdfEngine.getPageCount(destFile)
        val thumbFile = existing.thumbnailPath?.let { File(it) } ?: File(thumbsDir, "${existing.id}_thumb.png")
        generateThumbnail(destFile, thumbFile)
        val updated = existing.copy(
            title = newTitle?.let(::sanitizeTitle) ?: existing.title,
            thumbnailPath = if (thumbFile.exists()) thumbFile.absolutePath else null,
            pageCount = pageCount.coerceAtLeast(1),
            fileSizeBytes = destFile.length(),
            updatedAt = System.currentTimeMillis()
        )
        documentDao.update(updated)
        updated
    }

    suspend fun importExternalPdf(uri: Uri): DocumentEntity = withContext(Dispatchers.IO) {
        val displayName = resolveDisplayName(uri) ?: "Imported_Document"
        val tempFile = File(context.cacheDir, "import_${UUID.randomUUID()}.pdf")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        } ?: error("Could not read selected PDF file.")
        val pageCount = pdfEngine.getPageCount(tempFile)
        if (pageCount <= 0) {
            tempFile.delete()
            error("The selected file is not a valid readable PDF.")
        }
        val entity = registerPdfFile(tempFile, displayName.removeSuffix(".pdf"), "IMPORT")
        tempFile.delete()
        entity
    }

    suspend fun toggleFavorite(id: String, current: Boolean) {
        documentDao.setFavorite(id, !current, System.currentTimeMillis())
    }

    suspend fun renameDocument(id: String, newTitle: String) {
        val clean = sanitizeTitle(newTitle)
        if (clean.isNotEmpty()) {
            documentDao.rename(id, clean, System.currentTimeMillis())
        }
    }

    suspend fun deleteDocument(doc: DocumentEntity) = withContext(Dispatchers.IO) {
        runCatching { File(doc.filePath).delete() }
        doc.thumbnailPath?.let { runCatching { File(it).delete() } }
        documentDao.deleteById(doc.id)
    }

    suspend fun clearAllDocuments() = withContext(Dispatchers.IO) {
        documentDao.deleteAll()
        docsDir.listFiles()?.forEach { it.delete() }
        thumbsDir.listFiles()?.forEach { it.delete() }
        scansDir.listFiles()?.forEach { it.delete() }
        exportsDir.listFiles()?.forEach { it.delete() }
    }

    suspend fun copyPdfToUri(doc: DocumentEntity, destinationUri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val src = File(doc.filePath)
            context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                src.inputStream().use { input -> input.copyTo(out) }
            } ?: return@runCatching false
            true
        }.getOrDefault(false)
    }

    fun getShareUri(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun createSharePdfIntent(doc: DocumentEntity): Intent {
        val file = File(doc.filePath)
        val uri = getShareUri(file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "${doc.title}.pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    suspend fun calculateStorageStats(docsCount: Int): StorageStats = withContext(Dispatchers.IO) {
        val pdfBytes = docsDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val cacheBytes = thumbsDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } +
            scansDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } +
            exportsDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } +
            context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        StorageStats(
            documentCount = docsCount,
            pdfBytes = pdfBytes,
            cacheBytes = cacheBytes
        )
    }

    private fun generateThumbnail(pdfFile: File, thumbFile: File) {
        runCatching {
            val bmp = pdfEngine.renderPageBitmap(pdfFile, 0, targetWidth = 420)
            if (bmp != null) {
                FileOutputStream(thumbFile).use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 90, out)
                }
                bmp.recycle()
            }
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else null
        }
    }

    private fun sanitizeTitle(raw: String): String {
        return raw.trim()
            .removeSuffix(".pdf")
            .removeSuffix(".PDF")
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .ifBlank { "PinkScan_Document" }
    }
}
