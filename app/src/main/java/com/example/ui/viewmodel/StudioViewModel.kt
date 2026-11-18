package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.local.DocumentEntity
import com.example.data.model.AppLanguage
import com.example.data.model.CompressionLevel
import com.example.data.model.ImageFilter
import com.example.data.model.NormalizedPoint
import com.example.data.model.PageNumberConfig
import com.example.data.model.PdfCreationOptions
import com.example.data.model.ScannedPageItem
import com.example.data.model.WatermarkConfig
import com.example.data.repository.DocumentRepository
import com.example.engine.ImageProcessor
import com.example.engine.OcrEngine
import com.example.engine.PdfEngine
import com.example.engine.PdfToolsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class StudioViewModel(
    private val context: Context,
    private val repository: DocumentRepository,
    private val imageProcessor: ImageProcessor,
    private val pdfEngine: PdfEngine,
    private val pdfToolsEngine: PdfToolsEngine,
    private val ocrEngine: OcrEngine
) : ViewModel() {

    // Batch pages for Camera Scanner & Gallery-to-PDF
    private val _pages = MutableStateFlow<List<ScannedPageItem>>(emptyList())
    val pages: StateFlow<List<ScannedPageItem>> = _pages.asStateFlow()

    private val _activeEditPageId = MutableStateFlow<String?>(null)
    val activeEditPageId: StateFlow<String?> = _activeEditPageId.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _statusText = MutableStateFlow("")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    // OCR state
    private val _ocrText = MutableStateFlow("")
    val ocrText: StateFlow<String> = _ocrText.asStateFlow()

    private val _ocrSourceLabel = MutableStateFlow("")
    val ocrSourceLabel: StateFlow<String> = _ocrSourceLabel.asStateFlow()

    private val _ocrLastBitmap = MutableStateFlow<Bitmap?>(null)
    private val _ocrLastPdfFile = MutableStateFlow<File?>(null)

    private val _ocrError = MutableStateFlow<String?>(null)
    val ocrError: StateFlow<String?> = _ocrError.asStateFlow()

    private val _ocrLanguage = MutableStateFlow(AppLanguage.ENGLISH)
    val ocrLanguage: StateFlow<AppLanguage> = _ocrLanguage.asStateFlow()

    fun clearBatch() {
        _pages.value = emptyList()
        _activeEditPageId.value = null
    }

    fun selectActivePage(pageId: String?) {
        _activeEditPageId.value = pageId
    }

    fun addCapturedFile(file: File, autoDetect: Boolean = true, onAdded: (ScannedPageItem) -> Unit = {}) {
        viewModelScope.launch {
            _isProcessing.value = true
            _statusText.value = "Detecting document bounds..."
            runCatching {
                imageProcessor.createInitialPageItem(file, autoDetect)
            }.onSuccess { item ->
                _pages.value = _pages.value + item
                _activeEditPageId.value = item.id
                _isProcessing.value = false
                onAdded(item)
            }.onFailure {
                _isProcessing.value = false
            }
        }
    }

    fun addGalleryUris(uris: List<Uri>, autoDetect: Boolean = false) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isProcessing.value = true
            _progress.value = 0f
            _statusText.value = "Importing ${uris.size} image(s)..."
            val added = mutableListOf<ScannedPageItem>()
            uris.forEachIndexed { idx, uri ->
                runCatching {
                    val src = imageProcessor.copyUriToScanFile(uri)
                    imageProcessor.createInitialPageItem(src, autoDetect)
                }.onSuccess { item ->
                    added.add(item)
                }
                _progress.value = (idx + 1).toFloat() / uris.size
            }
            _pages.value = _pages.value + added
            if (_activeEditPageId.value == null && added.isNotEmpty()) {
                _activeEditPageId.value = added.first().id
            }
            _isProcessing.value = false
        }
    }

    fun removePage(pageId: String) {
        _pages.value = _pages.value.filterNot { it.id == pageId }
        if (_activeEditPageId.value == pageId) {
            _activeEditPageId.value = _pages.value.firstOrNull()?.id
        }
    }

    fun replacePageWithRetake(pageId: String) {
        removePage(pageId)
    }

    fun movePage(fromIndex: Int, toIndex: Int) {
        val current = _pages.value.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices || fromIndex == toIndex) return
        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)
        _pages.value = current
    }

    fun updatePageEdits(
        pageId: String,
        corners: List<NormalizedPoint>? = null,
        rotationDelta: Int = 0,
        filter: ImageFilter? = null,
        brightness: Float? = null,
        contrast: Float? = null,
        onDone: () -> Unit = {}
    ) {
        val existing = _pages.value.find { it.id == pageId } ?: return
        val updatedMeta = existing.copy(
            corners = corners ?: existing.corners,
            rotationDegrees = (existing.rotationDegrees + rotationDelta + 360) % 360,
            filter = filter ?: existing.filter,
            brightness = brightness ?: existing.brightness,
            contrast = contrast ?: existing.contrast
        )
        viewModelScope.launch {
            _isProcessing.value = true
            _statusText.value = "Enhancing page..."
            val reprocessed = imageProcessor.reprocessPage(updatedMeta)
            _pages.value = _pages.value.map { if (it.id == pageId) reprocessed else it }
            _isProcessing.value = false
            onDone()
        }
    }

    fun generatePdfFromBatch(
        options: PdfCreationOptions,
        sourceType: String,
        onCreated: (DocumentEntity) -> Unit,
        onError: (String) -> Unit
    ) {
        val batch = _pages.value
        if (batch.isEmpty()) {
            onError("Add at least one image first.")
            return
        }
        viewModelScope.launch {
            _isProcessing.value = true
            _progress.value = 0f
            _statusText.value = "Building PDF (${batch.size} page${if (batch.size == 1) "" else "s"})..."
            runCatching {
                val pdfFile = pdfEngine.createPdfFromImages(
                    imagePaths = batch.map { it.processedPath },
                    options = options,
                    onProgress = { _progress.value = it }
                )
                val doc = repository.registerPdfFile(pdfFile, options.fileName, sourceType)
                pdfFile.delete()
                doc
            }.onSuccess { doc ->
                _isProcessing.value = false
                onCreated(doc)
            }.onFailure { err ->
                _isProcessing.value = false
                onError(err.message ?: "Failed to generate PDF.")
            }
        }
    }

    fun saveScannedImagesToUriList(page: ScannedPageItem, destUri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val src = File(page.processedPath)
                    context.contentResolver.openOutputStream(destUri)?.use { out ->
                        src.inputStream().use { it.copyTo(out) }
                    } ?: return@runCatching false
                    true
                }.getOrDefault(false)
            }
            onResult(ok)
        }
    }

    // ---------- OCR ----------
    fun setOcrLanguage(lang: AppLanguage) {
        _ocrLanguage.value = lang
    }

    fun updateOcrText(newText: String) {
        _ocrText.value = newText
    }

    fun runOcrOnUri(uri: Uri) {
        viewModelScope.launch {
            _isProcessing.value = true
            _ocrError.value = null
            _progress.value = 0.25f
            _statusText.value = "Recognizing text on device..."
            _ocrSourceLabel.value = "Gallery Image"
            runCatching {
                val bmp = imageProcessor.decodeSampledBitmapFromUri(uri, maxDim = 2000)
                    ?: error("Could not decode image.")
                _ocrLastBitmap.value = bmp
                _ocrLastPdfFile.value = null
                _progress.value = 0.65f
                ocrEngine.recognizeBitmap(bmp)
            }.onSuccess { text ->
                _progress.value = 1f
                _isProcessing.value = false
                _ocrText.value = text
                if (text.isBlank()) {
                    _ocrError.value = "No readable text detected in this image."
                }
            }.onFailure { err ->
                _isProcessing.value = false
                _ocrError.value = err.message ?: "OCR failed."
            }
        }
    }

    fun runOcrOnScanFile(file: File, label: String = "Scanned Page") {
        viewModelScope.launch {
            _isProcessing.value = true
            _ocrError.value = null
            _progress.value = 0.3f
            _statusText.value = "Extracting text from $label..."
            _ocrSourceLabel.value = label
            runCatching {
                val bmp = imageProcessor.decodeSampledBitmapFromFile(file, maxDim = 2000)
                    ?: error("Could not load scan image.")
                _ocrLastBitmap.value = bmp
                _ocrLastPdfFile.value = null
                _progress.value = 0.7f
                ocrEngine.recognizeBitmap(bmp)
            }.onSuccess { text ->
                _progress.value = 1f
                _isProcessing.value = false
                _ocrText.value = text
                if (text.isBlank()) {
                    _ocrError.value = "No readable text found on this page."
                }
            }.onFailure { err ->
                _isProcessing.value = false
                _ocrError.value = err.message ?: "OCR failed."
            }
        }
    }

    fun runTextExtractionOnPdf(doc: DocumentEntity) {
        viewModelScope.launch {
            _isProcessing.value = true
            _ocrError.value = null
            _progress.value = 0.15f
            _statusText.value = "Extracting PDF text..."
            _ocrSourceLabel.value = "${doc.title}.pdf"
            val pdfFile = File(doc.filePath)
            _ocrLastPdfFile.value = pdfFile
            _ocrLastBitmap.value = null
            runCatching {
                val embedded = pdfToolsEngine.extractTextFromPdf(pdfFile)
                if (embedded.isNotBlank()) {
                    _progress.value = 1f
                    embedded
                } else {
                    _statusText.value = "Running on-device OCR on scanned PDF pages..."
                    ocrEngine.recognizePdfPages(pdfFile) { p -> _progress.value = p }
                }
            }.onSuccess { text ->
                _isProcessing.value = false
                _ocrText.value = text
                if (text.isBlank()) {
                    _ocrError.value = "No extractable text or recognizable Latin text found in this PDF."
                }
            }.onFailure { err ->
                _isProcessing.value = false
                _ocrError.value = err.message ?: "Text extraction failed."
            }
        }
    }

    fun rerunLastOcr() {
        val bmp = _ocrLastBitmap.value
        val pdf = _ocrLastPdfFile.value
        when {
            bmp != null -> {
                viewModelScope.launch {
                    _isProcessing.value = true
                    _ocrError.value = null
                    _statusText.value = "Re-running OCR..."
                    runCatching { ocrEngine.recognizeBitmap(bmp) }
                        .onSuccess { text ->
                            _isProcessing.value = false
                            _ocrText.value = text
                            if (text.isBlank()) _ocrError.value = "No text detected."
                        }
                        .onFailure {
                            _isProcessing.value = false
                            _ocrError.value = it.message ?: "OCR failed."
                        }
                }
            }
            pdf != null && pdf.exists() -> {
                viewModelScope.launch {
                    _isProcessing.value = true
                    _ocrError.value = null
                    _statusText.value = "Re-running PDF OCR..."
                    runCatching {
                        ocrEngine.recognizePdfPages(pdf) { _progress.value = it }
                    }.onSuccess { text ->
                        _isProcessing.value = false
                        _ocrText.value = text
                    }.onFailure {
                        _isProcessing.value = false
                        _ocrError.value = it.message ?: "OCR failed."
                    }
                }
            }
        }
    }

    fun exportOcrTextToPdf(
        title: String,
        onCreated: (DocumentEntity) -> Unit,
        onError: (String) -> Unit
    ) {
        val text = _ocrText.value
        if (text.isBlank()) {
            onError("Extract or enter some text before exporting.")
            return
        }
        viewModelScope.launch {
            _isProcessing.value = true
            _statusText.value = "Exporting text to PDF..."
            runCatching {
                val pdfFile = pdfToolsEngine.createPdfFromText(title, text)
                val doc = repository.registerPdfFile(pdfFile, title, "OCR")
                pdfFile.delete()
                doc
            }.onSuccess {
                _isProcessing.value = false
                onCreated(it)
            }.onFailure {
                _isProcessing.value = false
                onError(it.message ?: "Export failed.")
            }
        }
    }

    // ---------- PDF TOOLS ----------
    fun mergePdfs(
        docs: List<DocumentEntity>,
        outputTitle: String,
        onCreated: (DocumentEntity) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isProcessing.value = true
            _statusText.value = "Merging ${docs.size} PDFs..."
            runCatching {
                val out = pdfToolsEngine.mergePdfs(docs.map { File(it.filePath) })
                val saved = repository.registerPdfFile(out, outputTitle, "MERGED")
                out.delete()
                saved
            }.onSuccess {
                _isProcessing.value = false
                onCreated(it)
            }.onFailure {
                _isProcessing.value = false
                onError(it.message ?: "Merge failed.")
            }
        }
    }

    fun splitPdfRange(
        doc: DocumentEntity,
        startPage: Int,
        endPage: Int,
        outputTitle: String,
        onCreated: (DocumentEntity) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isProcessing.value = true
            _statusText.value = "Splitting pages $startPage–$endPage..."
            runCatching {
                val out = pdfToolsEngine.splitPdfByRange(File(doc.filePath), startPage, endPage)
                val saved = repository.registerPdfFile(out, outputTitle, "SPLIT")
                out.delete()
                saved
            }.onSuccess {
                _isProcessing.value = false
                onCreated(it)
            }.onFailure {
                _isProcessing.value = false
                onError(it.message ?: "Split failed.")
            }
        }
    }

    fun splitPdfAllPages(
        doc: DocumentEntity,
        onCompleted: (List<DocumentEntity>) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isProcessing.value = true
            _statusText.value = "Splitting every page into separate PDFs..."
            runCatching {
                val files = pdfToolsEngine.splitPdfEveryPage(File(doc.filePath))
                files.mapIndexed { idx, file ->
                    val saved = repository.registerPdfFile(file, "${doc.title}_Page_${idx + 1}", "SPLIT")
                    file.delete()
                    saved
                }
            }.onSuccess {
                _isProcessing.value = false
                onCompleted(it)
            }.onFailure {
                _isProcessing.value = false
                onError(it.message ?: "Split failed.")
            }
        }
    }

    fun compressPdf(
        doc: DocumentEntity,
        level: CompressionLevel,
        outputTitle: String,
        onCreated: (DocumentEntity, Long) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isProcessing.value = true
            _progress.value = 0f
            _statusText.value = "Compressing PDF..."
            runCatching {
                val out = pdfToolsEngine.compressPdf(File(doc.filePath), level) { _progress.value = it }
                val saved = repository.registerPdfFile(out, outputTitle, "COMPRESSED")
                out.delete()
                saved
            }.onSuccess { saved ->
                _isProcessing.value = false
                onCreated(saved, doc.fileSizeBytes)
            }.onFailure {
                _isProcessing.value = false
                onError(it.message ?: "Compression failed.")
            }
        }
    }

    fun exportPdfToImagesAndZip(
        doc: DocumentEntity,
        onReady: (imageFiles: List<File>, zipFile: File) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isProcessing.value = true
            _progress.value = 0f
            _statusText.value = "Rendering PDF pages to PNG..."
            runCatching {
                val imgs = pdfToolsEngine.pdfToImages(File(doc.filePath)) { _progress.value = it }
                val zip = pdfToolsEngine.createZipFromFiles(imgs, doc.title)
                imgs to zip
            }.onSuccess { (imgs, zip) ->
                _isProcessing.value = false
                onReady(imgs, zip)
            }.onFailure {
                _isProcessing.value = false
                onError(it.message ?: "Export to images failed.")
            }
        }
    }

    fun rotatePdf(
        doc: DocumentEntity,
        rotations: Map<Int, Int>,
        outputTitle: String,
        onCreated: (DocumentEntity) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isProcessing.value = true
            _statusText.value = "Rotating PDF pages..."
            runCatching {
                val out = pdfToolsEngine.rotatePdfPages(File(doc.filePath), rotations)
                val saved = repository.registerPdfFile(out, outputTitle, "EDITED")
                out.delete()
                saved
            }.onSuccess {
                _isProcessing.value = false
                onCreated(it)
            }.onFailure {
                _isProcessing.value = false
                onError(it.message ?: "Rotate failed.")
            }
        }
    }

    fun deletePdfPages(
        doc: DocumentEntity,
        indicesToDelete: Set<Int>,
        outputTitle: String,
        onCreated: (DocumentEntity) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isProcessing.value = true
            _statusText.value = "Removing selected pages..."
            runCatching {
                val out = pdfToolsEngine.deletePdfPages(File(doc.filePath), indicesToDelete)
                val saved = repository.registerPdfFile(out, outputTitle, "EDITED")
                out.delete()
                saved
            }.onSuccess {
                _isProcessing.value = false
                onCreated(it)
            }.onFailure {
                _isProcessing.value = false
                onError(it.message ?: "Delete pages failed.")
            }
        }
    }

    fun addWatermark(
        doc: DocumentEntity,
        config: WatermarkConfig,
        outputTitle: String,
        onCreated: (DocumentEntity) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isProcessing.value = true
            _statusText.value = "Applying watermark..."
            runCatching {
                val out = pdfToolsEngine.addWatermark(File(doc.filePath), config)
                val saved = repository.registerPdfFile(out, outputTitle, "WATERMARK")
                out.delete()
                saved
            }.onSuccess {
                _isProcessing.value = false
                onCreated(it)
            }.onFailure {
                _isProcessing.value = false
                onError(it.message ?: "Watermark failed.")
            }
        }
    }

    fun addPageNumbers(
        doc: DocumentEntity,
        config: PageNumberConfig,
        outputTitle: String,
        onCreated: (DocumentEntity) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isProcessing.value = true
            _statusText.value = "Adding page numbers..."
            runCatching {
                val out = pdfToolsEngine.addPageNumbers(File(doc.filePath), config)
                val saved = repository.registerPdfFile(out, outputTitle, "EDITED")
                out.delete()
                saved
            }.onSuccess {
                _isProcessing.value = false
                onCreated(it)
            }.onFailure {
                _isProcessing.value = false
                onError(it.message ?: "Page numbering failed.")
            }
        }
    }

    fun convertWordOrTxtToPdf(
        uri: Uri,
        onCreated: (DocumentEntity) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isProcessing.value = true
            _statusText.value = "Converting document to PDF..."
            runCatching {
                val name = resolveFileName(uri) ?: "Document.docx"
                val out = pdfToolsEngine.convertDocxOrTextUriToPdf(uri, name)
                val saved = repository.registerPdfFile(out, name.substringBeforeLast('.'), "WORD")
                out.delete()
                saved
            }.onSuccess {
                _isProcessing.value = false
                onCreated(it)
            }.onFailure {
                _isProcessing.value = false
                onError(it.message ?: "Document conversion failed.")
            }
        }
    }

    fun copyFileToUri(srcFile: File, destUri: Uri, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(destUri)?.use { out ->
                        srcFile.inputStream().use { it.copyTo(out) }
                    } ?: return@runCatching false
                    true
                }.getOrDefault(false)
            }
            onDone(ok)
        }
    }

    private fun resolveFileName(uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) c.getString(idx) else null
            } else null
        }
    }

    class Factory(private val container: AppContainer, private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StudioViewModel(
                context = context,
                repository = container.documentRepository,
                imageProcessor = container.imageProcessor,
                pdfEngine = container.pdfEngine,
                pdfToolsEngine = container.pdfToolsEngine,
                ocrEngine = container.ocrEngine
            ) as T
        }
    }
}
