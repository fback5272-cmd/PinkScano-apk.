package com.example.data.model

import androidx.compose.ui.graphics.Color
import java.util.UUID

enum class ThemeMode(val label: String) {
    LIGHT("Light"),
    DARK("Dark"),
    SYSTEM("System")
}

enum class AppLanguage(val label: String, val code: String) {
    ENGLISH("English", "en"),
    BANGLA("বাংলা (Bangla)", "bn")
}

enum class PageSizeOption(val label: String, val widthPt: Float, val heightPt: Float) {
    A4("A4", 595f, 842f),
    LETTER("Letter", 612f, 792f),
    ORIGINAL("Original Size", 0f, 0f)
}

enum class PageOrientation(val label: String) {
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape")
}

enum class PageMargin(val label: String, val points: Float) {
    NONE("None", 0f),
    NARROW("Narrow", 18f),
    NORMAL("Normal", 36f)
}

enum class ImageFilter(val label: String) {
    ORIGINAL("Original"),
    AUTO_ENHANCE("Auto Enhance"),
    GRAYSCALE("Grayscale"),
    BLACK_WHITE("B&W Doc")
}

enum class ScannerMode(val label: String, val aspectHint: Float, val guideRatio: String) {
    DOCUMENTS("Documents", 0.707f, "A4 Document"),
    ID_CARD("ID Card", 1.586f, "Card Frame"),
    PASSPORT("Passport", 0.714f, "Passport Page"),
    RECEIPTS("Receipts", 0.48f, "Receipt Strip"),
    BOOKS("Books", 0.78f, "Book Page")
}

enum class SortOption(val label: String) {
    DATE_DESC("Newest First"),
    DATE_ASC("Oldest First"),
    NAME_ASC("Name A–Z"),
    NAME_DESC("Name Z–A"),
    SIZE_DESC("Largest First")
}

enum class WatermarkPosition(val label: String) {
    DIAGONAL_CENTER("Diagonal Center"),
    CENTER("Center"),
    TOP("Top"),
    BOTTOM("Bottom"),
    TILED("Tiled Grid")
}

enum class PageNumberFormat(val label: String) {
    NUMBER_ONLY("1, 2, 3"),
    PAGE_OF_TOTAL("Page 1 of N"),
    SLASH_TOTAL("1 / N")
}

enum class PageNumberPosition(val label: String) {
    BOTTOM_CENTER("Bottom Center"),
    BOTTOM_RIGHT("Bottom Right"),
    TOP_RIGHT("Top Right")
}

enum class CompressionLevel(val label: String, val dpi: Int, val jpegQuality: Int) {
    LOW("High Quality (150 DPI)", 150, 82),
    MEDIUM("Balanced (110 DPI)", 110, 65),
    HIGH("Max Compression (80 DPI)", 80, 45)
}

enum class PdfToolType(
    val title: String,
    val subtitle: String,
    val category: String
) {
    IMAGES_TO_PDF("Images to PDF", "Convert photos into a PDF", "Create"),
    MERGE_PDFS("Merge PDFs", "Combine multiple PDFs", "Organize"),
    SPLIT_PDF("Split PDF", "Extract pages into new PDFs", "Organize"),
    COMPRESS_PDF("Compress PDF", "Reduce PDF file size", "Optimize"),
    PDF_TO_IMAGES("PDF to Images", "Export pages as PNG or ZIP", "Convert"),
    ROTATE_PAGES("Rotate Pages", "Rotate 90°, 180°, or 270°", "Organize"),
    DELETE_PAGES("Delete Pages", "Remove unwanted pages", "Organize"),
    ADD_WATERMARK("Add Watermark", "Stamp custom text on pages", "Protect"),
    ADD_PAGE_NUMBERS("Page Numbers", "Number pages automatically", "Edit"),
    EXTRACT_TEXT("Extract Text", "PDF text & ML Kit OCR", "Convert"),
    SIGN_PDF("Sign PDF", "Draw & place your signature", "Edit"),
    WORD_TO_PDF("Word to PDF", "Convert DOCX or TXT to PDF", "Convert"),
    PDF_INFO("PDF Info", "View size, pages & metadata", "Inspect"),
    PRINT_PDF("Print PDF", "Send directly to printer", "Share")
}

data class NormalizedPoint(val x: Float, val y: Float)

data class ScannedPageItem(
    val id: String = UUID.randomUUID().toString(),
    val originalPath: String,
    val processedPath: String,
    val rotationDegrees: Int = 0,
    val filter: ImageFilter = ImageFilter.ORIGINAL,
    val brightness: Float = 0f, // -50f .. 50f
    val contrast: Float = 1f,   // 0.6f .. 1.6f
    val corners: List<NormalizedPoint> = defaultCorners()
) {
    companion object {
        fun defaultCorners(): List<NormalizedPoint> = listOf(
            NormalizedPoint(0.06f, 0.06f), // Top-Left
            NormalizedPoint(0.94f, 0.06f), // Top-Right
            NormalizedPoint(0.94f, 0.94f), // Bottom-Right
            NormalizedPoint(0.06f, 0.94f)  // Bottom-Left
        )
    }
}

data class PdfCreationOptions(
    val fileName: String = "PinkScan_Document",
    val pageSize: PageSizeOption = PageSizeOption.A4,
    val orientation: PageOrientation = PageOrientation.PORTRAIT,
    val margin: PageMargin = PageMargin.NARROW,
    val imageQuality: Int = 85
)

data class WatermarkConfig(
    val text: String = "CONFIDENTIAL",
    val fontSizeSp: Float = 42f,
    val colorArgb: Int = 0xFFEC4899.toInt(),
    val opacity: Float = 0.28f,
    val position: WatermarkPosition = WatermarkPosition.DIAGONAL_CENTER
)

data class PageNumberConfig(
    val format: PageNumberFormat = PageNumberFormat.PAGE_OF_TOTAL,
    val position: PageNumberPosition = PageNumberPosition.BOTTOM_CENTER,
    val fontSizePt: Float = 11f,
    val colorArgb: Int = 0xFF18121A.toInt()
)

data class PdfMetadataInfo(
    val fileName: String,
    val pageCount: Int,
    val fileSizeBytes: Long,
    val pageDimensionsLabel: String,
    val pdfVersion: String,
    val title: String,
    val author: String,
    val creator: String,
    val createdAt: Long
)

data class NormalizedCropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
)

data class PdfPenStroke(
    val id: String = UUID.randomUUID().toString(),
    val pageIndex: Int,
    val points: List<NormalizedPoint>,
    val colorArgb: Int,
    val strokeWidthRatio: Float, // relative to page width (e.g., 0.006f)
    val isHighlighter: Boolean = false
)

data class PdfTextAnnotation(
    val id: String = UUID.randomUUID().toString(),
    val pageIndex: Int,
    val text: String,
    val xRatio: Float,
    val yRatio: Float,
    val fontSizeRatio: Float = 0.032f,
    val colorArgb: Int = 0xFF18121A.toInt()
)

data class PdfImageOverlay(
    val id: String = UUID.randomUUID().toString(),
    val pageIndex: Int,
    val imagePath: String,
    val xRatio: Float,
    val yRatio: Float,
    val widthRatio: Float = 0.28f,
    val heightRatio: Float = 0.14f,
    val isSignature: Boolean = false
)

data class EditablePageState(
    val originalPageIndex: Int, // -1 if blank inserted page
    val rotationDelta: Int = 0, // 0, 90, 180, 270
    val cropRect: NormalizedCropRect? = null
)

data class NotificationEntry(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class StorageStats(
    val documentCount: Int = 0,
    val pdfBytes: Long = 0L,
    val cacheBytes: Long = 0L
) {
    val totalBytes: Long get() = pdfBytes + cacheBytes
}

val EditorColorSwatches = listOf(
    Color(0xFFEC4899),
    Color(0xFF18121A),
    Color(0xFF2563EB),
    Color(0xFFDC2626),
    Color(0xFF16A34A),
    Color(0xFFF59E0B)
)
