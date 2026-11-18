package com.example

import android.app.Application
import com.example.data.local.PinkScanDatabase
import com.example.data.local.SettingsDataStore
import com.example.data.repository.DocumentRepository
import com.example.engine.ImageProcessor
import com.example.engine.OcrEngine
import com.example.engine.PdfEngine
import com.example.engine.PdfToolsEngine
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class PinkScanApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        container = AppContainer(this)
    }
}

class AppContainer(application: Application) {
    private val context = application.applicationContext
    val database: PinkScanDatabase by lazy { PinkScanDatabase.getInstance(context) }
    val settingsDataStore: SettingsDataStore by lazy { SettingsDataStore(context) }
    val imageProcessor: ImageProcessor by lazy { ImageProcessor(context) }
    val pdfEngine: PdfEngine by lazy { PdfEngine(context) }
    val pdfToolsEngine: PdfToolsEngine by lazy { PdfToolsEngine(context) }
    val ocrEngine: OcrEngine by lazy { OcrEngine(context, pdfEngine) }
    val documentRepository: DocumentRepository by lazy {
        DocumentRepository(
            context = context,
            documentDao = database.documentDao(),
            pdfEngine = pdfEngine
        )
    }
}
