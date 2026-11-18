package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OcrEngine(
    private val context: Context,
    private val pdfEngine: PdfEngine
) {
    private val latinRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognizeBitmap(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        latinRecognizer.process(image)
            .addOnSuccessListener { visionText ->
                if (cont.isActive) cont.resume(visionText.text.trim())
            }
            .addOnFailureListener { e ->
                if (cont.isActive) cont.resumeWithException(e)
            }
    }

    suspend fun recognizeUri(uri: Uri): String = withContext(Dispatchers.IO) {
        val image = InputImage.fromFilePath(context, uri)
        suspendCancellableCoroutine { cont ->
            latinRecognizer.process(image)
                .addOnSuccessListener { visionText ->
                    if (cont.isActive) cont.resume(visionText.text.trim())
                }
                .addOnFailureListener { e ->
                    if (cont.isActive) cont.resumeWithException(e)
                }
        }
    }

    suspend fun recognizePdfPages(
        pdfFile: File,
        onProgress: (Float) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        val count = pdfEngine.getPageCount(pdfFile).coerceAtLeast(1)
        val sb = StringBuilder()
        for (i in 0 until count) {
            val bmp = pdfEngine.renderPageBitmap(pdfFile, i, targetWidth = 1400)
            if (bmp != null) {
                val pageText = recognizeBitmap(bmp)
                bmp.recycle()
                if (pageText.isNotBlank()) {
                    if (count > 1) sb.append("--- Page ${i + 1} ---\n")
                    sb.append(pageText).append("\n\n")
                }
            }
            onProgress((i + 1).toFloat() / count)
        }
        sb.toString().trim()
    }
}
