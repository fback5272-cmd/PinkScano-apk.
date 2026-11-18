package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import com.example.data.model.ImageFilter
import com.example.data.model.NormalizedPoint
import com.example.data.model.ScannedPageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class ImageProcessor(private val context: Context) {

    private val workDir: File by lazy { File(context.filesDir, "pinkscan_scans").apply { mkdirs() } }

    suspend fun copyUriToScanFile(uri: Uri): File = withContext(Dispatchers.IO) {
        val outFile = File(workDir, "src_${UUID.randomUUID()}.jpg")
        val bmp = decodeSampledBitmapFromUri(uri, maxDim = 2200)
            ?: error("Unable to decode selected image.")
        FileOutputStream(outFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        bmp.recycle()
        outFile
    }

    suspend fun createInitialPageItem(sourceFile: File, autoDetect: Boolean = true): ScannedPageItem =
        withContext(Dispatchers.IO) {
            val bmp = decodeSampledBitmapFromFile(sourceFile, maxDim = 1800)
                ?: error("Failed to load captured image.")
            val detectedCorners = if (autoDetect) detectDocumentCorners(bmp) else ScannedPageItem.defaultCorners()
            val processedFile = File(workDir, "proc_${UUID.randomUUID()}.jpg")
            FileOutputStream(processedFile).use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            bmp.recycle()
            ScannedPageItem(
                originalPath = sourceFile.absolutePath,
                processedPath = processedFile.absolutePath,
                corners = detectedCorners
            )
        }

    suspend fun reprocessPage(item: ScannedPageItem): ScannedPageItem = withContext(Dispatchers.IO) {
        val original = decodeSampledBitmapFromFile(File(item.originalPath), maxDim = 2000)
            ?: return@withContext item

        val warped = applyPerspectiveCrop(original, item.corners)
        if (warped !== original) original.recycle()

        val rotated = if (item.rotationDegrees % 360 != 0) {
            val m = Matrix().apply { postRotate(item.rotationDegrees.toFloat()) }
            Bitmap.createBitmap(warped, 0, 0, warped.width, warped.height, m, true).also {
                if (it !== warped) warped.recycle()
            }
        } else {
            warped
        }

        val filtered = applyFilterAndAdjustments(
            source = rotated,
            filter = item.filter,
            brightness = item.brightness,
            contrast = item.contrast
        )
        if (filtered !== rotated) rotated.recycle()

        val outFile = File(workDir, "proc_${item.id}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(outFile).use { out ->
            filtered.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        filtered.recycle()

        val oldProc = File(item.processedPath)
        if (oldProc.exists() && oldProc.absolutePath != item.originalPath) {
            oldProc.delete()
        }

        item.copy(processedPath = outFile.absolutePath)
    }

    fun decodeSampledBitmapFromFile(file: File, maxDim: Int = 1800): Bitmap? {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDim, maxDim)
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bmp = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        return applyExifOrientation(bmp, orientation)
    }

    fun decodeSampledBitmapFromUri(uri: Uri, maxDim: Int = 2000): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDim, maxDim)
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null

        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        return applyExifOrientation(bmp, orientation)
    }

    /**
     * Heuristic document edge detector: downscales to ~160px, checks luminance gradient peaks
     * around the outer bands to estimate document quadrilateral corners, falling back cleanly
     * to a standard inset frame if contrast is uniform.
     */
    fun detectDocumentCorners(bitmap: Bitmap): List<NormalizedPoint> {
        val w = 160
        val h = ((bitmap.height.toFloat() / bitmap.width.coerceAtLeast(1)) * w).toInt().coerceIn(80, 240)
        val small = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        small.recycle()

        fun lum(x: Int, y: Int): Float {
            val c = pixels[y * w + x]
            return 0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)
        }

        // Scan inward from edges along center strips to find strong contrast transitions
        var leftEdge = (w * 0.06f).toInt()
        var rightEdge = (w * 0.94f).toInt()
        var topEdge = (h * 0.06f).toInt()
        var bottomEdge = (h * 0.94f).toInt()

        val yMid = h / 2
        val xMid = w / 2
        val threshold = 28f

        for (x in 4 until w / 3) {
            if (kotlin.math.abs(lum(x + 2, yMid) - lum(x - 2, yMid)) > threshold) {
                leftEdge = x; break
            }
        }
        for (x in (w - 5) downTo (w * 2 / 3)) {
            if (kotlin.math.abs(lum(x - 2, yMid) - lum(x + 2, yMid)) > threshold) {
                rightEdge = x; break
            }
        }
        for (y in 4 until h / 3) {
            if (kotlin.math.abs(lum(xMid, y + 2) - lum(xMid, y - 2)) > threshold) {
                topEdge = y; break
            }
        }
        for (y in (h - 5) downTo (h * 2 / 3)) {
            if (kotlin.math.abs(lum(xMid, y - 2) - lum(xMid, y + 2)) > threshold) {
                bottomEdge = y; break
            }
        }

        val l = (leftEdge.toFloat() / w).coerceIn(0.02f, 0.35f)
        val r = (rightEdge.toFloat() / w).coerceIn(0.65f, 0.98f)
        val t = (topEdge.toFloat() / h).coerceIn(0.02f, 0.35f)
        val b = (bottomEdge.toFloat() / h).coerceIn(0.65f, 0.98f)

        return listOf(
            NormalizedPoint(l, t),
            NormalizedPoint(r, t),
            NormalizedPoint(r, b),
            NormalizedPoint(l, b)
        )
    }

    fun applyPerspectiveCrop(source: Bitmap, corners: List<NormalizedPoint>): Bitmap {
        if (corners.size != 4) return source
        val isFullFrame = corners[0].x <= 0.01f && corners[0].y <= 0.01f &&
            corners[2].x >= 0.99f && corners[2].y >= 0.99f
        if (isFullFrame) return source

        val w = source.width.toFloat()
        val h = source.height.toFloat()
        val tl = corners[0]
        val tr = corners[1]
        val br = corners[2]
        val bl = corners[3]

        val srcPts = floatArrayOf(
            tl.x * w, tl.y * h,
            tr.x * w, tr.y * h,
            br.x * w, br.y * h,
            bl.x * w, bl.y * h
        )

        val topW = hypot((tr.x - tl.x) * w, (tr.y - tl.y) * h)
        val bottomW = hypot((br.x - bl.x) * w, (br.y - bl.y) * h)
        val leftH = hypot((bl.x - tl.x) * w, (bl.y - tl.y) * h)
        val rightH = hypot((br.x - tr.x) * w, (br.y - tr.y) * h)

        val outW = max(topW, bottomW).toInt().coerceIn(64, 2400)
        val outH = max(leftH, rightH).toInt().coerceIn(64, 3200)

        val dstPts = floatArrayOf(
            0f, 0f,
            outW.toFloat(), 0f,
            outW.toFloat(), outH.toFloat(),
            0f, outH.toFloat()
        )

        val matrix = Matrix()
        val ok = matrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)
        if (!ok) return source

        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, matrix, paint)
        return output
    }

    fun applyFilterAndAdjustments(
        source: Bitmap,
        filter: ImageFilter,
        brightness: Float,
        contrast: Float
    ): Bitmap {
        val needsMatrix = filter != ImageFilter.ORIGINAL || brightness != 0f || contrast != 1f
        if (!needsMatrix) return source

        val effectiveContrast = when (filter) {
            ImageFilter.AUTO_ENHANCE -> (contrast * 1.28f).coerceAtMost(2.0f)
            ImageFilter.BLACK_WHITE -> (contrast * 1.95f).coerceAtMost(2.8f)
            else -> contrast
        }
        val effectiveBrightness = when (filter) {
            ImageFilter.AUTO_ENHANCE -> brightness + 12f
            ImageFilter.BLACK_WHITE -> brightness + 18f
            else -> brightness
        }

        val cm = ColorMatrix()
        if (filter == ImageFilter.GRAYSCALE || filter == ImageFilter.BLACK_WHITE) {
            val gray = ColorMatrix().apply { setSaturation(0f) }
            cm.postConcat(gray)
        } else if (filter == ImageFilter.AUTO_ENHANCE) {
            val sat = ColorMatrix().apply { setSaturation(1.18f) }
            cm.postConcat(sat)
        }

        val scale = effectiveContrast
        val translate = (-0.5f * scale + 0.5f) * 255f + effectiveBrightness
        val bcMatrix = ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        cm.postConcat(bcMatrix)

        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)

        if (filter == ImageFilter.BLACK_WHITE) {
            // Crisp document binarization pass
            val w = result.width
            val h = result.height
            val pixels = IntArray(w * h)
            result.getPixels(pixels, 0, w, 0, 0, w, h)
            for (i in pixels.indices) {
                val c = pixels[i]
                val l = (Color.red(c) * 0.3f + Color.green(c) * 0.59f + Color.blue(c) * 0.11f)
                pixels[i] = if (l > 142f) Color.WHITE else Color.rgb(24, 18, 26)
            }
            result.setPixels(pixels, 0, w, 0, 0, w, h)
        }
        return result
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
            if (it !== bitmap) bitmap.recycle()
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqW: Int, reqH: Int): Int {
        var inSampleSize = 1
        if (height > reqH || width > reqW) {
            val halfH = height / 2
            val halfW = width / 2
            while (halfH / inSampleSize >= reqH && halfW / inSampleSize >= reqW) {
                inSampleSize *= 2
            }
        }
        return min(max(inSampleSize, 1), 16)
    }
}
