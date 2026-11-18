package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.data.model.CompressionLevel
import com.example.data.model.PageNumberConfig
import com.example.data.model.PageNumberFormat
import com.example.data.model.PageNumberPosition
import com.example.data.model.WatermarkConfig
import com.example.data.model.WatermarkPosition
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class PdfToolsEngine(private val context: Context) {

    private val workDir: File by lazy { File(context.cacheDir, "pdf_tools").apply { mkdirs() } }
    private val exportsDir: File by lazy { File(context.filesDir, "pinkscan_exports").apply { mkdirs() } }

    suspend fun mergePdfs(sourceFiles: List<File>): File = withContext(Dispatchers.IO) {
        require(sourceFiles.size >= 2) { "Select at least 2 PDF files to merge." }
        val outFile = File(workDir, "merged_${UUID.randomUUID()}.pdf")
        val merger = PDFMergerUtility()
        merger.destinationFileName = outFile.absolutePath
        sourceFiles.forEach { merger.addSource(it) }
        merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
        outFile
    }

    suspend fun splitPdfByRange(
        sourceFile: File,
        startPageOneBased: Int,
        endPageOneBased: Int
    ): File = withContext(Dispatchers.IO) {
        val outFile = File(workDir, "split_${UUID.randomUUID()}.pdf")
        PDDocument.load(sourceFile).use { src ->
            val max = src.numberOfPages
            val s = startPageOneBased.coerceIn(1, max)
            val e = endPageOneBased.coerceIn(s, max)
            PDDocument().use { out ->
                for (i in (s - 1) until e) {
                    out.importPage(src.getPage(i))
                }
                out.save(outFile)
            }
        }
        outFile
    }

    suspend fun splitPdfEveryPage(sourceFile: File): List<File> = withContext(Dispatchers.IO) {
        val results = mutableListOf<File>()
        PDDocument.load(sourceFile).use { src ->
            for (i in 0 until src.numberOfPages) {
                val outFile = File(workDir, "split_p${i + 1}_${UUID.randomUUID()}.pdf")
                PDDocument().use { out ->
                    out.importPage(src.getPage(i))
                    out.save(outFile)
                }
                results.add(outFile)
            }
        }
        results
    }

    suspend fun compressPdf(
        sourceFile: File,
        level: CompressionLevel,
        onProgress: (Float) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        val outFile = File(workDir, "compressed_${UUID.randomUUID()}.pdf")
        ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                PDDocument().use { outDoc ->
                    val total = renderer.pageCount.coerceAtLeast(1)
                    val targetWidth = ((level.dpi / 72f) * 595f).toInt().coerceIn(480, 1400)
                    for (i in 0 until total) {
                        renderer.openPage(i).use { rPage ->
                            val ratio = targetWidth.toFloat() / rPage.width.coerceAtLeast(1)
                            val bmpW = targetWidth
                            val bmpH = (rPage.height * ratio).toInt().coerceAtLeast(100)
                            val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                            Canvas(bmp).drawColor(Color.WHITE)
                            rPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            val baos = ByteArrayOutputStream()
                            bmp.compress(Bitmap.CompressFormat.JPEG, level.jpegQuality, baos)
                            bmp.recycle()

                            val pW = 595f
                            val pH = pW * (bmpH.toFloat() / bmpW.coerceAtLeast(1))
                            val pdPage = PDPage(PDRectangle(pW, pH))
                            outDoc.addPage(pdPage)
                            val pdImg = JPEGFactory.createFromStream(outDoc, ByteArrayInputStream(baos.toByteArray()))
                            PDPageContentStream(outDoc, pdPage).use { cs ->
                                cs.drawImage(pdImg, 0f, 0f, pW, pH)
                            }
                        }
                        onProgress((i + 1).toFloat() / total)
                    }
                    outDoc.save(outFile)
                }
            }
        }
        outFile
    }

    suspend fun pdfToImages(
        sourceFile: File,
        onProgress: (Float) -> Unit = {}
    ): List<File> = withContext(Dispatchers.IO) {
        val outFiles = mutableListOf<File>()
        ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val total = renderer.pageCount.coerceAtLeast(1)
                for (i in 0 until total) {
                    renderer.openPage(i).use { page ->
                        val targetW = 1400
                        val ratio = targetW.toFloat() / page.width.coerceAtLeast(1)
                        val h = (page.height * ratio).toInt().coerceAtLeast(100)
                        val bmp = Bitmap.createBitmap(targetW, h, Bitmap.Config.ARGB_8888)
                        Canvas(bmp).drawColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val imgFile = File(exportsDir, "page_${i + 1}_${UUID.randomUUID().toString().take(6)}.png")
                        FileOutputStream(imgFile).use { out ->
                            bmp.compress(Bitmap.CompressFormat.PNG, 95, out)
                        }
                        bmp.recycle()
                        outFiles.add(imgFile)
                    }
                    onProgress((i + 1).toFloat() / total)
                }
            }
        }
        outFiles
    }

    suspend fun createZipFromFiles(files: List<File>, zipName: String): File = withContext(Dispatchers.IO) {
        val zipFile = File(exportsDir, "${zipName}_${UUID.randomUUID().toString().take(6)}.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            files.forEachIndexed { idx, file ->
                zos.putNextEntry(ZipEntry("page_${idx + 1}.png"))
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        zipFile
    }

    suspend fun rotatePdfPages(
        sourceFile: File,
        pageRotations: Map<Int, Int> // 0-based index -> added degrees
    ): File = withContext(Dispatchers.IO) {
        val outFile = File(workDir, "rotated_${UUID.randomUUID()}.pdf")
        PDDocument.load(sourceFile).use { doc ->
            for (i in 0 until doc.numberOfPages) {
                val addDeg = pageRotations[i] ?: 0
                if (addDeg != 0) {
                    val page = doc.getPage(i)
                    page.rotation = (page.rotation + addDeg) % 360
                }
            }
            doc.save(outFile)
        }
        outFile
    }

    suspend fun deletePdfPages(
        sourceFile: File,
        indicesToDelete: Set<Int>
    ): File = withContext(Dispatchers.IO) {
        val outFile = File(workDir, "trimmed_${UUID.randomUUID()}.pdf")
        PDDocument.load(sourceFile).use { doc ->
            val sortedDesc = indicesToDelete.filter { it in 0 until doc.numberOfPages }.sortedDescending()
            require(sortedDesc.size < doc.numberOfPages) { "A PDF must keep at least 1 page." }
            sortedDesc.forEach { doc.removePage(it) }
            doc.save(outFile)
        }
        outFile
    }

    suspend fun addWatermark(
        sourceFile: File,
        config: WatermarkConfig
    ): File = withContext(Dispatchers.IO) {
        require(config.text.isNotBlank()) { "Watermark text cannot be empty." }
        val outFile = File(workDir, "watermarked_${UUID.randomUUID()}.pdf")
        val safeText = PdfEngine.toWinAnsiSafe(config.text.trim())
        PDDocument.load(sourceFile).use { doc ->
            val font = PDType1Font.HELVETICA_BOLD
            val r = Color.red(config.colorArgb)
            val g = Color.green(config.colorArgb)
            val b = Color.blue(config.colorArgb)

            for (i in 0 until doc.numberOfPages) {
                val page = doc.getPage(i)
                val box = page.cropBox ?: page.mediaBox
                val pW = box.width
                val pH = box.height
                val textWidth = font.getStringWidth(safeText) / 1000f * config.fontSizeSp

                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    val gs = PDExtendedGraphicsState().apply {
                        nonStrokingAlphaConstant = config.opacity.coerceIn(0.05f, 1f)
                    }
                    cs.setGraphicsStateParameters(gs)
                    cs.setNonStrokingColor(r, g, b)
                    cs.setFont(font, config.fontSizeSp)

                    when (config.position) {
                        WatermarkPosition.DIAGONAL_CENTER -> {
                            cs.beginText()
                            val cx = box.lowerLeftX + pW / 2f
                            val cy = box.lowerLeftY + pH / 2f
                            val matrix = Matrix.getRotateInstance(Math.toRadians(38.0), cx - textWidth * 0.35f, cy - textWidth * 0.25f)
                            cs.setTextMatrix(matrix)
                            cs.showText(safeText)
                            cs.endText()
                        }
                        WatermarkPosition.CENTER -> {
                            cs.beginText()
                            cs.newLineAtOffset(box.lowerLeftX + (pW - textWidth) / 2f, box.lowerLeftY + pH / 2f)
                            cs.showText(safeText)
                            cs.endText()
                        }
                        WatermarkPosition.TOP -> {
                            cs.beginText()
                            cs.newLineAtOffset(box.lowerLeftX + (pW - textWidth) / 2f, box.lowerLeftY + pH - 64f)
                            cs.showText(safeText)
                            cs.endText()
                        }
                        WatermarkPosition.BOTTOM -> {
                            cs.beginText()
                            cs.newLineAtOffset(box.lowerLeftX + (pW - textWidth) / 2f, box.lowerLeftY + 42f)
                            cs.showText(safeText)
                            cs.endText()
                        }
                        WatermarkPosition.TILED -> {
                            val rows = listOf(0.25f, 0.5f, 0.75f)
                            val cols = listOf(0.22f, 0.65f)
                            rows.forEach { ry ->
                                cols.forEach { cx ->
                                    cs.beginText()
                                    val m = Matrix.getRotateInstance(
                                        Math.toRadians(30.0),
                                        box.lowerLeftX + pW * cx - textWidth * 0.25f,
                                        box.lowerLeftY + pH * ry
                                    )
                                    cs.setTextMatrix(m)
                                    cs.showText(safeText)
                                    cs.endText()
                                }
                            }
                        }
                    }
                }
            }
            doc.save(outFile)
        }
        outFile
    }

    suspend fun addPageNumbers(
        sourceFile: File,
        config: PageNumberConfig
    ): File = withContext(Dispatchers.IO) {
        val outFile = File(workDir, "numbered_${UUID.randomUUID()}.pdf")
        PDDocument.load(sourceFile).use { doc ->
            val total = doc.numberOfPages
            val font = PDType1Font.HELVETICA
            val r = Color.red(config.colorArgb)
            val g = Color.green(config.colorArgb)
            val b = Color.blue(config.colorArgb)

            for (i in 0 until total) {
                val page = doc.getPage(i)
                val box = page.cropBox ?: page.mediaBox
                val label = when (config.format) {
                    PageNumberFormat.NUMBER_ONLY -> "${i + 1}"
                    PageNumberFormat.PAGE_OF_TOTAL -> "Page ${i + 1} of $total"
                    PageNumberFormat.SLASH_TOTAL -> "${i + 1} / $total"
                }
                val textWidth = font.getStringWidth(label) / 1000f * config.fontSizePt
                val x = when (config.position) {
                    PageNumberPosition.BOTTOM_CENTER -> box.lowerLeftX + (box.width - textWidth) / 2f
                    PageNumberPosition.BOTTOM_RIGHT, PageNumberPosition.TOP_RIGHT -> box.lowerLeftX + box.width - textWidth - 28f
                }
                val y = when (config.position) {
                    PageNumberPosition.BOTTOM_CENTER, PageNumberPosition.BOTTOM_RIGHT -> box.lowerLeftY + 22f
                    PageNumberPosition.TOP_RIGHT -> box.lowerLeftY + box.height - 30f
                }
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.beginText()
                    cs.setFont(font, config.fontSizePt)
                    cs.setNonStrokingColor(r, g, b)
                    cs.newLineAtOffset(x, y)
                    cs.showText(label)
                    cs.endText()
                }
            }
            doc.save(outFile)
        }
        outFile
    }

    suspend fun extractTextFromPdf(sourceFile: File): String = withContext(Dispatchers.IO) {
        PDDocument.load(sourceFile).use { doc ->
            val stripper = PDFTextStripper()
            stripper.getText(doc).trim()
        }
    }

    suspend fun createPdfFromText(title: String, bodyText: String): File = withContext(Dispatchers.IO) {
        val outFile = File(workDir, "textpdf_${UUID.randomUUID()}.pdf")
        renderPlainTextToPdf(title, bodyText, outFile)
        outFile
    }

    suspend fun convertDocxOrTextUriToPdf(uri: Uri, fileName: String): File = withContext(Dispatchers.IO) {
        val extractedText = context.contentResolver.openInputStream(uri)?.use { input ->
            if (fileName.endsWith(".docx", ignoreCase = true)) {
                extractTextFromDocx(input)
            } else {
                input.bufferedReader().readText()
            }
        } ?: error("Unable to open selected document.")

        require(extractedText.isNotBlank()) { "The selected document contains no readable text." }
        val outFile = File(workDir, "wordpdf_${UUID.randomUUID()}.pdf")
        renderPlainTextToPdf(fileName.substringBeforeLast('.'), extractedText, outFile)
        outFile
    }

    private fun extractTextFromDocx(inputStream: InputStream): String {
        val sb = StringBuilder()
        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val xmlBytes = zis.readBytes()
                    val factory = XmlPullParserFactory.newInstance()
                    val parser = factory.newPullParser()
                    parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")
                    var eventType = parser.eventType
                    var inParagraph = false
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        when (eventType) {
                            XmlPullParser.START_TAG -> {
                                if (parser.name == "p" || parser.name.endsWith(":p")) {
                                    inParagraph = true
                                } else if ((parser.name == "t" || parser.name.endsWith(":t")) && inParagraph) {
                                    sb.append(parser.nextText())
                                } else if (parser.name == "br" || parser.name.endsWith(":br")) {
                                    sb.append('\n')
                                }
                            }
                            XmlPullParser.END_TAG -> {
                                if (parser.name == "p" || parser.name.endsWith(":p")) {
                                    sb.append("\n\n")
                                    inParagraph = false
                                }
                            }
                        }
                        eventType = parser.next()
                    }
                    break
                }
                entry = zis.nextEntry
            }
        }
        return sb.toString().trim()
    }

    /**
     * Renders arbitrary Unicode text (including English & Bangla) cleanly across multi-page A4 PDF
     * using Android StaticLayout into crisp page bitmaps when non-WinAnsi characters exist,
     * or fast vector Type1 text for pure Latin text.
     */
    private fun renderPlainTextToPdf(title: String, bodyText: String, outFile: File) {
        val hasUnicode = bodyText.any { it.code > 255 } || title.any { it.code > 255 }
        PDDocument().use { doc ->
            doc.documentInformation.title = title
            doc.documentInformation.creator = "PinkScan"

            if (!hasUnicode) {
                val font = PDType1Font.HELVETICA
                val bold = PDType1Font.HELVETICA_BOLD
                val pageW = PDRectangle.A4.width
                val pageH = PDRectangle.A4.height
                val margin = 48f
                val maxWidth = pageW - margin * 2
                val lineHeight = 16f
                val lines = wrapLines(bodyText, font, 11f, maxWidth)

                var idx = 0
                var pageNum = 0
                while (idx < lines.size || pageNum == 0) {
                    val page = PDPage(PDRectangle.A4)
                    doc.addPage(page)
                    PDPageContentStream(doc, page).use { cs ->
                        var y = pageH - margin
                        if (pageNum == 0 && title.isNotBlank()) {
                            cs.beginText()
                            cs.setFont(bold, 16f)
                            cs.setNonStrokingColor(219, 39, 119)
                            cs.newLineAtOffset(margin, y)
                            cs.showText(PdfEngine.toWinAnsiSafe(title))
                            cs.endText()
                            y -= 28f
                        }
                        cs.beginText()
                        cs.setFont(font, 11f)
                        cs.setNonStrokingColor(24, 18, 26)
                        while (idx < lines.size && y > margin + lineHeight) {
                            cs.newLineAtOffset(margin, y)
                            cs.showText(lines[idx])
                            cs.newLineAtOffset(-margin, -y)
                            y -= lineHeight
                            idx++
                        }
                        cs.endText()
                    }
                    pageNum++
                }
            } else {
                val pageW = 1240
                val pageH = 1754
                val margin = 96
                val contentW = pageW - margin * 2
                val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(24, 18, 26)
                    textSize = 28f
                }
                val fullText = if (title.isNotBlank()) "$title\n\n$bodyText" else bodyText
                val staticLayout = StaticLayout.Builder
                    .obtain(fullText, 0, fullText.length, textPaint, contentW)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(8f, 1.15f)
                    .build()

                var currentLine = 0
                val maxContentH = pageH - margin * 2
                while (currentLine < staticLayout.lineCount) {
                    val startTop = staticLayout.getLineTop(currentLine)
                    var endLine = currentLine
                    while (endLine < staticLayout.lineCount &&
                        staticLayout.getLineBottom(endLine) - startTop <= maxContentH
                    ) {
                        endLine++
                    }
                    if (endLine == currentLine) endLine++

                    val bmp = Bitmap.createBitmap(pageW, pageH, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    canvas.drawColor(Color.WHITE)
                    canvas.save()
                    canvas.translate(margin.toFloat(), (margin - startTop).toFloat())
                    val clipBottom = staticLayout.getLineBottom(endLine - 1)
                    canvas.clipRect(0, startTop, contentW, clipBottom)
                    staticLayout.draw(canvas)
                    canvas.restore()

                    val pdPage = PDPage(PDRectangle.A4)
                    doc.addPage(pdPage)
                    val pdImg = LosslessFactory.createFromImage(doc, bmp)
                    bmp.recycle()
                    PDPageContentStream(doc, pdPage).use { cs ->
                        cs.drawImage(pdImg, 0f, 0f, PDRectangle.A4.width, PDRectangle.A4.height)
                    }
                    currentLine = endLine
                }
            }
            doc.save(outFile)
        }
    }

    private fun wrapLines(text: String, font: PDType1Font, fontSize: Float, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        text.replace("\r\n", "\n").split("\n").forEach { rawPara ->
            val para = PdfEngine.toWinAnsiSafe(rawPara)
            if (para.isBlank()) {
                result.add("")
            } else {
                val words = para.split(" ")
                var current = StringBuilder()
                for (w in words) {
                    val candidate = if (current.isEmpty()) w else "$current $w"
                    val width = runCatching { font.getStringWidth(candidate) / 1000f * fontSize }.getOrDefault(0f)
                    if (width > maxWidth && current.isNotEmpty()) {
                        result.add(current.toString())
                        current = StringBuilder(w)
                    } else {
                        current = StringBuilder(candidate)
                    }
                }
                if (current.isNotEmpty()) result.add(current.toString())
            }
        }
        return result
    }
}
