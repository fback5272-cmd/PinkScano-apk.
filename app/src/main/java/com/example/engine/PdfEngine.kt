package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import com.example.data.model.EditablePageState
import com.example.data.model.PageOrientation
import com.example.data.model.PageSizeOption
import com.example.data.model.PdfCreationOptions
import com.example.data.model.PdfImageOverlay
import com.example.data.model.PdfMetadataInfo
import com.example.data.model.PdfPenStroke
import com.example.data.model.PdfTextAnnotation
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.min

class PdfEngine(private val context: Context) {

    private val workDir: File by lazy { File(context.cacheDir, "pdf_work").apply { mkdirs() } }

    fun getPageCount(pdfFile: File): Int {
        if (!pdfFile.exists() || pdfFile.length() == 0L) return 0
        return runCatching {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer -> renderer.pageCount }
            }
        }.getOrElse {
            runCatching { PDDocument.load(pdfFile).use { it.numberOfPages } }.getOrDefault(0)
        }
    }

    suspend fun getPdfInfo(pdfFile: File, displayName: String, createdAt: Long): PdfMetadataInfo =
        withContext(Dispatchers.IO) {
            PDDocument.load(pdfFile).use { doc ->
                val count = doc.numberOfPages
                val firstBox = if (count > 0) doc.getPage(0).mediaBox else PDRectangle.A4
                val widthMm = (firstBox.width / 72f * 25.4f).toInt()
                val heightMm = (firstBox.height / 72f * 25.4f).toInt()
                val info = doc.documentInformation
                PdfMetadataInfo(
                    fileName = displayName,
                    pageCount = count,
                    fileSizeBytes = pdfFile.length(),
                    pageDimensionsLabel = "$widthMm × $heightMm mm (${firstBox.width.toInt()} × ${firstBox.height.toInt()} pt)",
                    pdfVersion = "PDF ${doc.version}",
                    title = info?.title?.takeIf { it.isNotBlank() } ?: displayName,
                    author = info?.author?.takeIf { it.isNotBlank() } ?: "PinkScan User",
                    creator = info?.creator?.takeIf { it.isNotBlank() } ?: "PinkScan PDF Maker",
                    createdAt = createdAt
                )
            }
        }

    fun renderPageBitmap(pdfFile: File, pageIndex: Int, targetWidth: Int = 1080): Bitmap? {
        if (!pdfFile.exists()) return null
        return runCatching {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (pageIndex < 0 || pageIndex >= renderer.pageCount) return null
                    renderer.openPage(pageIndex).use { page ->
                        val ratio = targetWidth.toFloat() / page.width.coerceAtLeast(1)
                        val w = targetWidth.coerceAtLeast(100)
                        val h = (page.height * ratio).toInt().coerceAtLeast(100)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bmp)
                        canvas.drawColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }
            }
        }.getOrNull()
    }

    suspend fun createPdfFromImages(
        imagePaths: List<String>,
        options: PdfCreationOptions,
        onProgress: (Float) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        val outFile = File(workDir, "gen_${UUID.randomUUID()}.pdf")
        PDDocument().use { doc ->
            doc.documentInformation.creator = "PinkScan"
            doc.documentInformation.title = options.fileName

            val total = imagePaths.size.coerceAtLeast(1)
            imagePaths.forEachIndexed { index, path ->
                val bmp = decodeBitmapForPdf(File(path)) ?: return@forEachIndexed
                val (pageW, pageH) = resolvePageDimensions(bmp.width, bmp.height, options)
                val page = PDPage(PDRectangle(pageW, pageH))
                doc.addPage(page)

                val margin = options.margin.points.coerceAtMost(min(pageW, pageH) * 0.25f)
                val availW = (pageW - margin * 2).coerceAtLeast(20f)
                val availH = (pageH - margin * 2).coerceAtLeast(20f)

                val scale = min(availW / bmp.width.toFloat(), availH / bmp.height.toFloat())
                val drawW = bmp.width * scale
                val drawH = bmp.height * scale
                val drawX = (pageW - drawW) / 2f
                val drawY = (pageH - drawH) / 2f

                val baos = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, options.imageQuality.coerceIn(35, 100), baos)
                bmp.recycle()

                val pdImage = JPEGFactory.createFromStream(doc, ByteArrayInputStream(baos.toByteArray()))
                PDPageContentStream(doc, page).use { stream ->
                    stream.drawImage(pdImage, drawX, drawY, drawW, drawH)
                }
                onProgress((index + 1).toFloat() / total)
            }
            doc.save(outFile)
        }
        outFile
    }

    suspend fun applyEditsToPdf(
        sourcePdf: File,
        pageStates: List<EditablePageState>,
        strokes: List<PdfPenStroke>,
        texts: List<PdfTextAnnotation>,
        images: List<PdfImageOverlay>,
        onProgress: (Float) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        val outFile = File(workDir, "edited_${UUID.randomUUID()}.pdf")
        PDDocument.load(sourcePdf).use { srcDoc ->
            PDDocument().use { outDoc ->
                outDoc.documentInformation.creator = "PinkScan PDF Editor"
                val total = pageStates.size.coerceAtLeast(1)
                pageStates.forEachIndexed { newIdx, state ->
                    val page: PDPage = if (state.originalPageIndex in 0 until srcDoc.numberOfPages) {
                        val imported = outDoc.importPage(srcDoc.getPage(state.originalPageIndex))
                        if (state.rotationDelta % 360 != 0) {
                            imported.rotation = (imported.rotation + state.rotationDelta) % 360
                        }
                        state.cropRect?.let { cr ->
                            val box = imported.cropBox ?: imported.mediaBox
                            val newLeft = box.lowerLeftX + box.width * cr.left.coerceIn(0f, 0.9f)
                            val newBottom = box.lowerLeftY + box.height * (1f - cr.bottom.coerceIn(0.1f, 1f))
                            val newW = (box.width * (cr.right - cr.left)).coerceAtLeast(40f)
                            val newH = (box.height * (cr.bottom - cr.top)).coerceAtLeast(40f)
                            imported.cropBox = PDRectangle(newLeft, newBottom, newW, newH)
                        }
                        imported
                    } else {
                        PDPage(PDRectangle.A4)
                    }
                    if (state.originalPageIndex < 0) {
                        outDoc.addPage(page)
                    }

                    val media = page.cropBox ?: page.mediaBox
                    val pW = media.width
                    val pH = media.height
                    val baseX = media.lowerLeftX
                    val baseY = media.lowerLeftY

                    val pageStrokes = strokes.filter { it.pageIndex == newIdx }
                    val pageTexts = texts.filter { it.pageIndex == newIdx }
                    val pageImages = images.filter { it.pageIndex == newIdx }

                    if (pageStrokes.isNotEmpty() || pageTexts.isNotEmpty() || pageImages.isNotEmpty()) {
                        PDPageContentStream(
                            outDoc,
                            page,
                            PDPageContentStream.AppendMode.APPEND,
                            true,
                            true
                        ).use { cs ->
                            // 1. Draw Pen & Highlighter Strokes
                            pageStrokes.forEach { stroke ->
                                if (stroke.points.size >= 2) {
                                    cs.saveGraphicsState()
                                    if (stroke.isHighlighter) {
                                        val gs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 0.35f; strokingAlphaConstant = 0.35f }
                                        cs.setGraphicsStateParameters(gs)
                                    }
                                    val r = Color.red(stroke.colorArgb)
                                    val g = Color.green(stroke.colorArgb)
                                    val b = Color.blue(stroke.colorArgb)
                                    cs.setStrokingColor(r, g, b)
                                    cs.setLineWidth((stroke.strokeWidthRatio * pW).coerceIn(1f, 36f))
                                    cs.setLineCapStyle(1)
                                    cs.setLineJoinStyle(1)

                                    val first = stroke.points.first()
                                    cs.moveTo(baseX + first.x * pW, baseY + (1f - first.y) * pH)
                                    for (i in 1 until stroke.points.size) {
                                        val pt = stroke.points[i]
                                        cs.lineTo(baseX + pt.x * pW, baseY + (1f - pt.y) * pH)
                                    }
                                    cs.stroke()
                                    cs.restoreGraphicsState()
                                }
                            }

                            // 2. Draw Placed Images & Signatures
                            pageImages.forEach { imgOverlay ->
                                val imgFile = File(imgOverlay.imagePath)
                                if (imgFile.exists()) {
                                    val bmp = BitmapFactory.decodeFile(imgFile.absolutePath)
                                    if (bmp != null) {
                                        val pdImg = LosslessFactory.createFromImage(outDoc, bmp)
                                        bmp.recycle()
                                        val drawW = imgOverlay.widthRatio * pW
                                        val drawH = imgOverlay.heightRatio * pH
                                        val drawX = baseX + imgOverlay.xRatio * pW - drawW / 2f
                                        val drawY = baseY + (1f - imgOverlay.yRatio) * pH - drawH / 2f
                                        cs.drawImage(pdImg, drawX, drawY, drawW, drawH)
                                    }
                                }
                            }

                            // 3. Draw Text Annotations
                            pageTexts.forEach { txt ->
                                val fontPt = (txt.fontSizeRatio * pH).coerceIn(8f, 72f)
                                val safeText = toWinAnsiSafe(txt.text)
                                val drawX = baseX + txt.xRatio * pW
                                val drawY = baseY + (1f - txt.yRatio) * pH
                                cs.beginText()
                                cs.setFont(PDType1Font.HELVETICA_BOLD, fontPt)
                                cs.setNonStrokingColor(
                                    Color.red(txt.colorArgb),
                                    Color.green(txt.colorArgb),
                                    Color.blue(txt.colorArgb)
                                )
                                cs.newLineAtOffset(drawX, drawY)
                                cs.showText(safeText)
                                cs.endText()
                            }
                        }
                    }
                    onProgress((newIdx + 1).toFloat() / total)
                }
                outDoc.save(outFile)
            }
        }
        outFile
    }

    fun printPdf(pdfFile: File, jobName: String) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder("$jobName.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(getPageCount(pdfFile).coerceAtLeast(1))
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback
            ) {
                runCatching {
                    FileInputStream(pdfFile).use { input ->
                        FileOutputStream(destination.fileDescriptor).use { output ->
                            input.copyTo(output)
                        }
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                }.onFailure {
                    callback.onWriteFailed(it.message)
                }
            }
        }
        printManager.print("PinkScan - $jobName", adapter, PrintAttributes.Builder().build())
    }

    private fun resolvePageDimensions(
        bmpW: Int,
        bmpH: Int,
        options: PdfCreationOptions
    ): Pair<Float, Float> {
        if (options.pageSize == PageSizeOption.ORIGINAL) {
            val aspect = bmpH.toFloat() / bmpW.coerceAtLeast(1)
            val w = 595f
            val h = (w * aspect).coerceIn(200f, 1600f)
            return if (options.orientation == PageOrientation.LANDSCAPE && h > w) h to w else w to h
        }
        val baseW = options.pageSize.widthPt
        val baseH = options.pageSize.heightPt
        return if (options.orientation == PageOrientation.LANDSCAPE) baseH to baseW else baseW to baseH
    }

    private fun decodeBitmapForPdf(file: File): Bitmap? {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 2200 || bounds.outHeight / sample > 2200) {
            sample *= 2
        }
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    companion object {
        fun toWinAnsiSafe(input: String): String {
            return buildString {
                for (ch in input) {
                    if (ch.code in 32..126 || ch.code in 160..255) append(ch)
                    else if (ch == '\t') append("    ")
                    else append('?')
                }
            }
        }
    }
}
