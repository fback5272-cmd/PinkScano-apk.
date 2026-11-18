package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.PageMargin
import com.example.data.model.PageOrientation
import com.example.data.model.PageSizeOption
import com.example.data.model.PdfCreationOptions
import com.example.data.model.WatermarkConfig
import com.example.engine.PdfEngine
import com.example.engine.PdfToolsEngine
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    @Test
    fun `app name and tagline resources match PinkScan`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("PinkScan", context.getString(R.string.app_name))
        assertEquals("Scan • Create • Edit • Share", context.getString(R.string.app_tagline))
    }

    @Test
    fun `create pdf from images, merge, watermark, and extract text`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pdfEngine = PdfEngine(context)
        val toolsEngine = PdfToolsEngine(context)

        val img1 = File(context.cacheDir, "test_p1.jpg")
        val bmp = Bitmap.createBitmap(400, 560, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }
        FileOutputStream(img1).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()

        val createdPdf = pdfEngine.createPdfFromImages(
            imagePaths = listOf(img1.absolutePath),
            options = PdfCreationOptions(
                fileName = "TestDoc",
                pageSize = PageSizeOption.A4,
                orientation = PageOrientation.PORTRAIT,
                margin = PageMargin.NARROW,
                imageQuality = 85
            )
        )
        assertTrue(createdPdf.exists())
        assertTrue(createdPdf.length() > 500L)
        PDDocument.load(createdPdf).use { doc ->
            assertEquals(1, doc.numberOfPages)
        }

        val textPdf = toolsEngine.createPdfFromText("Hello PinkScan", "Sample invoice total 42.00")
        val extracted = toolsEngine.extractTextFromPdf(textPdf)
        assertTrue(extracted.contains("Sample invoice total 42.00"))

        val merged = toolsEngine.mergePdfs(listOf(createdPdf, textPdf))
        PDDocument.load(merged).use { doc ->
            assertEquals(2, doc.numberOfPages)
        }

        val watermarked = toolsEngine.addWatermark(merged, WatermarkConfig(text = "CONFIDENTIAL"))
        assertTrue(watermarked.exists())
        PDDocument.load(watermarked).use { doc ->
            assertEquals(2, doc.numberOfPages)
        }
    }
}
