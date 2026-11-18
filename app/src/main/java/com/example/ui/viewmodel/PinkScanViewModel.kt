package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.local.DocumentEntity
import com.example.data.local.SettingsDataStore
import com.example.data.local.UserPreferences
import com.example.data.model.AppLanguage
import com.example.data.model.NotificationEntry
import com.example.data.model.PageSizeOption
import com.example.data.model.PdfMetadataInfo
import com.example.data.model.StorageStats
import com.example.data.model.ThemeMode
import com.example.data.repository.DocumentRepository
import com.example.engine.PdfEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PinkScanViewModel(
    private val repository: DocumentRepository,
    private val settingsDataStore: SettingsDataStore,
    private val pdfEngine: PdfEngine,
    private val appContext: Context
) : ViewModel() {

    val documents: StateFlow<List<DocumentEntity>> = repository.observeDocuments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val preferences: StateFlow<UserPreferences> = settingsDataStore.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val storageStats: StateFlow<StorageStats> = documents
        .mapLatest { list -> repository.calculateStorageStats(list.size) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StorageStats())

    private val _notifications = MutableStateFlow<List<NotificationEntry>>(emptyList())
    val notifications: StateFlow<List<NotificationEntry>> = _notifications.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _resultDocument = MutableStateFlow<DocumentEntity?>(null)
    val resultDocument: StateFlow<DocumentEntity?> = _resultDocument.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _busyLabel = MutableStateFlow("")
    val busyLabel: StateFlow<String> = _busyLabel.asStateFlow()

    fun consumeSnackbar() {
        _snackbarMessage.value = null
    }

    fun showMessage(message: String) {
        _snackbarMessage.value = message
    }

    fun presentResultDocument(doc: DocumentEntity) {
        _resultDocument.value = doc
        addNotification(
            title = "PDF Ready",
            message = "${doc.title}.pdf (${doc.pageCount} page${if (doc.pageCount == 1) "" else "s"}) saved locally."
        )
    }

    fun dismissResultDocument() {
        _resultDocument.value = null
    }

    fun addNotification(title: String, message: String) {
        val entry = NotificationEntry(title = title, message = message)
        _notifications.value = (listOf(entry) + _notifications.value).take(20)
    }

    fun importPdf(uri: Uri, onImported: (DocumentEntity) -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            _busyLabel.value = "Importing PDF..."
            runCatching {
                repository.importExternalPdf(uri)
            }.onSuccess { doc ->
                _isBusy.value = false
                addNotification("PDF Imported", "${doc.title}.pdf added to your library.")
                _snackbarMessage.value = "Imported ${doc.title}.pdf"
                onImported(doc)
            }.onFailure { err ->
                _isBusy.value = false
                _snackbarMessage.value = err.message ?: "Failed to import PDF."
            }
        }
    }

    fun toggleFavorite(doc: DocumentEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(doc.id, doc.isFavorite)
        }
    }

    fun renameDocument(doc: DocumentEntity, newTitle: String) {
        viewModelScope.launch {
            repository.renameDocument(doc.id, newTitle)
            _snackbarMessage.value = "Renamed to $newTitle"
        }
    }

    fun deleteDocument(doc: DocumentEntity) {
        viewModelScope.launch {
            repository.deleteDocument(doc)
            _snackbarMessage.value = "Deleted ${doc.title}.pdf"
        }
    }

    fun clearAllDocuments() {
        viewModelScope.launch {
            _isBusy.value = true
            _busyLabel.value = "Clearing local storage..."
            repository.clearAllDocuments()
            _isBusy.value = false
            addNotification("Storage Cleared", "All local PDF documents were removed.")
            _snackbarMessage.value = "All local documents cleared."
        }
    }

    fun createShareIntent(doc: DocumentEntity): Intent {
        return repository.createSharePdfIntent(doc)
    }

    fun exportDocumentToUri(doc: DocumentEntity, destUri: Uri) {
        viewModelScope.launch {
            val ok = repository.copyPdfToUri(doc, destUri)
            _snackbarMessage.value = if (ok) {
                addNotification("PDF Saved", "Exported ${doc.title}.pdf to device storage.")
                "Saved ${doc.title}.pdf"
            } else {
                "Could not save file to selected location."
            }
        }
    }

    fun printDocument(doc: DocumentEntity) {
        val file = File(doc.filePath)
        if (file.exists()) {
            pdfEngine.printPdf(file, doc.title)
        } else {
            _snackbarMessage.value = "PDF file is missing on disk."
        }
    }

    suspend fun loadPdfMetadata(doc: DocumentEntity): Result<PdfMetadataInfo> {
        return runCatching {
            pdfEngine.getPdfInfo(File(doc.filePath), doc.title, doc.createdAt)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsDataStore.setThemeMode(mode) }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { settingsDataStore.setLanguage(language) }
    }

    fun setDefaultPageSize(size: PageSizeOption) {
        viewModelScope.launch { settingsDataStore.setDefaultPageSize(size) }
    }

    fun setDefaultImageQuality(quality: Int) {
        viewModelScope.launch { settingsDataStore.setDefaultImageQuality(quality) }
    }

    fun setDefaultFilePrefix(prefix: String) {
        viewModelScope.launch { settingsDataStore.setDefaultFilePrefix(prefix) }
    }

    fun completeOnboarding() {
        viewModelScope.launch { settingsDataStore.setOnboardingCompleted(true) }
    }

    fun saveReusableSignature(bitmap: Bitmap) {
        viewModelScope.launch {
            val sigFile = withContext(Dispatchers.IO) {
                val dir = File(appContext.filesDir, "pinkscan_signatures").apply { mkdirs() }
                val file = File(dir, "default_signature.png")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                file
            }
            settingsDataStore.setSavedSignaturePath(sigFile.absolutePath)
            _snackbarMessage.value = "Signature saved for quick signing."
        }
    }

    fun clearReusableSignature() {
        viewModelScope.launch {
            preferences.value.savedSignaturePath?.let { runCatching { File(it).delete() } }
            settingsDataStore.setSavedSignaturePath(null)
            _snackbarMessage.value = "Saved signature cleared."
        }
    }

    class Factory(private val container: AppContainer, private val appContext: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PinkScanViewModel(
                repository = container.documentRepository,
                settingsDataStore = container.settingsDataStore,
                pdfEngine = container.pdfEngine,
                appContext = appContext
            ) as T
        }
    }
}
