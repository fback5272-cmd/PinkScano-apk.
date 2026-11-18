package com.example.ui.navigation

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.AppContainer
import com.example.data.local.DocumentEntity
import com.example.data.model.PdfMetadataInfo
import com.example.data.model.PdfToolType
import com.example.data.model.ThemeMode
import com.example.ui.components.DeleteConfirmDialog
import com.example.ui.components.PdfMetadataDialog
import com.example.ui.components.PdfResultBottomSheet
import com.example.ui.components.PinkScanLogo
import com.example.ui.components.ProcessingOverlay
import com.example.ui.components.RenameDocumentDialog
import com.example.ui.components.formatBytes
import com.example.ui.screens.CompressPdfDialog
import com.example.ui.screens.DocumentsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ImagesToPdfScreen
import com.example.ui.screens.MergePdfsDialog
import com.example.ui.screens.OcrScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PageNumbersDialog
import com.example.ui.screens.PdfToImagesResultDialog
import com.example.ui.screens.PdfViewerEditorScreen
import com.example.ui.screens.RotateOrDeletePagesDialog
import com.example.ui.screens.ScanReviewScreen
import com.example.ui.screens.ScannerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SinglePdfPickerDialog
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.SplitPdfDialog
import com.example.ui.screens.ToolsScreen
import com.example.ui.screens.WatermarkDialog
import com.example.ui.theme.PinkDeep
import com.example.ui.theme.PinkLight
import com.example.ui.theme.PinkPrimary
import com.example.ui.theme.PinkScanTheme
import com.example.ui.viewmodel.PinkScanViewModel
import com.example.ui.viewmodel.StudioViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File

@Serializable object RouteSplash
@Serializable object RouteOnboarding
@Serializable object RouteHome
@Serializable object RouteDocuments
@Serializable object RouteTools
@Serializable object RouteSettings
@Serializable object RouteScanner
@Serializable object RouteScanReview
@Serializable object RouteImagesToPdf
@Serializable object RouteOcr
@Serializable data class RoutePdfViewer(
    val documentId: String,
    val editMode: Boolean = false,
    val signMode: Boolean = false
)

private data class BottomNavTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: Any
)

@Composable
fun PinkScanApp(container: AppContainer) {
    val context = LocalContext.current
    val mainViewModel: PinkScanViewModel = viewModel(
        factory = PinkScanViewModel.Factory(container, context.applicationContext)
    )
    val studioViewModel: StudioViewModel = viewModel(
        factory = StudioViewModel.Factory(container, context.applicationContext)
    )

    val preferences by mainViewModel.preferences.collectAsStateWithLifecycle()
    val documents by mainViewModel.documents.collectAsStateWithLifecycle()
    val storageStats by mainViewModel.storageStats.collectAsStateWithLifecycle()
    val notifications by mainViewModel.notifications.collectAsStateWithLifecycle()
    val snackbarMessage by mainViewModel.snackbarMessage.collectAsStateWithLifecycle()
    val resultDocument by mainViewModel.resultDocument.collectAsStateWithLifecycle()
    val isBusy by mainViewModel.isBusy.collectAsStateWithLifecycle()
    val busyLabel by mainViewModel.busyLabel.collectAsStateWithLifecycle()

    val studioPages by studioViewModel.pages.collectAsStateWithLifecycle()
    val activePageId by studioViewModel.activeEditPageId.collectAsStateWithLifecycle()
    val studioBusy by studioViewModel.isProcessing.collectAsStateWithLifecycle()
    val studioProgress by studioViewModel.progress.collectAsStateWithLifecycle()
    val studioStatus by studioViewModel.statusText.collectAsStateWithLifecycle()

    val ocrText by studioViewModel.ocrText.collectAsStateWithLifecycle()
    val ocrSourceLabel by studioViewModel.ocrSourceLabel.collectAsStateWithLifecycle()
    val ocrError by studioViewModel.ocrError.collectAsStateWithLifecycle()
    val ocrLanguage by studioViewModel.ocrLanguage.collectAsStateWithLifecycle()

    val isDark = when (preferences.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    PinkScanTheme(darkTheme = isDark) {
        val navController = rememberNavController()
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        // Shared Dialog / Tool States
        var renameTarget by remember { mutableStateOf<DocumentEntity?>(null) }
        var deleteTarget by remember { mutableStateOf<DocumentEntity?>(null) }
        var infoMetadata by remember { mutableStateOf<PdfMetadataInfo?>(null) }
        var pendingDownloadDoc by remember { mutableStateOf<DocumentEntity?>(null) }

        // Tool Execution States
        var activeToolPicker by remember { mutableStateOf<PdfToolType?>(null) }
        var showMergeDialog by remember { mutableStateOf(false) }
        var splitTargetDoc by remember { mutableStateOf<DocumentEntity?>(null) }
        var compressTargetDoc by remember { mutableStateOf<DocumentEntity?>(null) }
        var rotateTargetDoc by remember { mutableStateOf<DocumentEntity?>(null) }
        var deletePagesTargetDoc by remember { mutableStateOf<DocumentEntity?>(null) }
        var watermarkTargetDoc by remember { mutableStateOf<DocumentEntity?>(null) }
        var pageNumbersTargetDoc by remember { mutableStateOf<DocumentEntity?>(null) }
        var pdfToImagesResult by remember { mutableStateOf<Pair<List<File>, File>?>(null) }
        var ocrFromCameraFlag by remember { mutableStateOf(false) }

        // Launchers
        val importPdfLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                mainViewModel.importPdf(uri) { imported ->
                    if (activeToolPicker == null && !showMergeDialog) {
                        navController.navigate(RoutePdfViewer(imported.id, editMode = false))
                    }
                }
            }
        }

        val wordToPdfLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                studioViewModel.convertWordOrTxtToPdf(
                    uri = uri,
                    onCreated = { doc -> mainViewModel.presentResultDocument(doc) },
                    onError = { err -> mainViewModel.showMessage(err) }
                )
            }
        }

        val saveCopyLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/pdf")
        ) { uri ->
            val doc = pendingDownloadDoc
            if (uri != null && doc != null) {
                mainViewModel.exportDocumentToUri(doc, uri)
            }
            pendingDownloadDoc = null
        }

        fun sharePdf(doc: DocumentEntity) {
            runCatching {
                val intent = mainViewModel.createShareIntent(doc)
                context.startActivity(Intent.createChooser(intent, "Share ${doc.title}.pdf"))
            }.onFailure {
                mainViewModel.showMessage("No share target available. Use Download to save a copy.")
            }
        }

        fun launchTool(tool: PdfToolType) {
            when (tool) {
                PdfToolType.IMAGES_TO_PDF -> {
                    studioViewModel.clearBatch()
                    navController.navigate(RouteImagesToPdf)
                }
                PdfToolType.MERGE_PDFS -> showMergeDialog = true
                PdfToolType.EXTRACT_TEXT -> navController.navigate(RouteOcr)
                PdfToolType.WORD_TO_PDF -> {
                    wordToPdfLauncher.launch(
                        arrayOf(
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "text/plain"
                        )
                    )
                }
                else -> activeToolPicker = tool
            }
        }

        LaunchedEffect(snackbarMessage) {
            snackbarMessage?.let {
                snackbarHostState.showSnackbar(it)
                mainViewModel.consumeSnackbar()
            }
        }

        val backStackEntry by navController.currentBackStackEntryAsState()
        val dest = backStackEntry?.destination
        val showPrimaryNav = dest?.let {
            it.hasRoute<RouteHome>() ||
                it.hasRoute<RouteDocuments>() ||
                it.hasRoute<RouteTools>() ||
                it.hasRoute<RouteSettings>()
        } == true

        val tabs = remember {
            listOf(
                BottomNavTab("Home", Icons.Filled.Home, Icons.Outlined.Home, RouteHome),
                BottomNavTab("Documents", Icons.Filled.Folder, Icons.Outlined.Folder, RouteDocuments),
                BottomNavTab("Tools", Icons.Filled.Build, Icons.Outlined.Build, RouteTools),
                BottomNavTab("Settings", Icons.Filled.Settings, Icons.Outlined.Settings, RouteSettings)
            )
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    if (showPrimaryNav && !useRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 4.dp
                        ) {
                            tabs.forEach { tab ->
                                val selected = when (tab.route) {
                                    RouteHome -> dest?.hasRoute<RouteHome>() == true
                                    RouteDocuments -> dest?.hasRoute<RouteDocuments>() == true
                                    RouteTools -> dest?.hasRoute<RouteTools>() == true
                                    else -> dest?.hasRoute<RouteSettings>() == true
                                }
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = PinkDeep,
                                        selectedTextColor = PinkDeep,
                                        indicatorColor = PinkLight
                                    ),
                                    modifier = Modifier.testTag("nav_${tab.label.lowercase()}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    if (showPrimaryNav && useRail) {
                        NavigationRail(
                            modifier = Modifier.fillMaxHeight(),
                            containerColor = MaterialTheme.colorScheme.surface,
                            header = {
                                PinkScanLogo(size = 40.dp, modifier = Modifier.padding(vertical = 12.dp))
                            }
                        ) {
                            Spacer(Modifier.weight(0.1f))
                            tabs.forEach { tab ->
                                val selected = when (tab.route) {
                                    RouteHome -> dest?.hasRoute<RouteHome>() == true
                                    RouteDocuments -> dest?.hasRoute<RouteDocuments>() == true
                                    RouteTools -> dest?.hasRoute<RouteTools>() == true
                                    else -> dest?.hasRoute<RouteSettings>() == true
                                }
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = PinkDeep,
                                        selectedTextColor = PinkDeep,
                                        indicatorColor = PinkLight
                                    )
                                )
                            }
                            Spacer(Modifier.weight(1f))
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        NavHost(
                            navController = navController,
                            startDestination = RouteSplash
                        ) {
                            composable<RouteSplash> {
                                SplashScreen(
                                    onFinished = {
                                        val next = if (preferences.hasCompletedOnboarding) RouteHome else RouteOnboarding
                                        navController.navigate(next) {
                                            popUpTo<RouteSplash> { inclusive = true }
                                        }
                                    }
                                )
                            }
                            composable<RouteOnboarding> {
                                OnboardingScreen(
                                    onFinish = {
                                        mainViewModel.completeOnboarding()
                                        navController.navigate(RouteHome) {
                                            popUpTo<RouteOnboarding> { inclusive = true }
                                        }
                                    }
                                )
                            }
                            composable<RouteHome> {
                                HomeScreen(
                                    documents = documents,
                                    storageStats = storageStats,
                                    notifications = notifications,
                                    onScanNow = {
                                        ocrFromCameraFlag = false
                                        studioViewModel.clearBatch()
                                        navController.navigate(RouteScanner)
                                    },
                                    onGalleryToPdf = {
                                        studioViewModel.clearBatch()
                                        navController.navigate(RouteImagesToPdf)
                                    },
                                    onImportPdf = {
                                        importPdfLauncher.launch(arrayOf("application/pdf"))
                                    },
                                    onOcrExtract = {
                                        navController.navigate(RouteOcr)
                                    },
                                    onOpenTool = { tool ->
                                        if (tool == PdfToolType.MERGE_PDFS) {
                                            navController.navigate(RouteTools)
                                        } else {
                                            launchTool(tool)
                                        }
                                    },
                                    onViewAllDocuments = { navController.navigate(RouteDocuments) },
                                    onOpenSettings = { navController.navigate(RouteSettings) },
                                    onOpenDocument = { doc ->
                                        navController.navigate(RoutePdfViewer(doc.id, editMode = false))
                                    },
                                    onEditDocument = { doc ->
                                        navController.navigate(RoutePdfViewer(doc.id, editMode = true))
                                    },
                                    onToggleFavorite = mainViewModel::toggleFavorite,
                                    onRenameDocument = { renameTarget = it },
                                    onShareDocument = ::sharePdf,
                                    onDownloadDocument = { doc ->
                                        pendingDownloadDoc = doc
                                        saveCopyLauncher.launch("${doc.title}.pdf")
                                    },
                                    onPrintDocument = mainViewModel::printDocument,
                                    onInfoDocument = { doc ->
                                        scope.launch {
                                            mainViewModel.loadPdfMetadata(doc).onSuccess { infoMetadata = it }
                                        }
                                    },
                                    onDeleteDocument = { deleteTarget = it }
                                )
                            }
                            composable<RouteDocuments> {
                                DocumentsScreen(
                                    documents = documents,
                                    onImportPdf = { importPdfLauncher.launch(arrayOf("application/pdf")) },
                                    onScanNow = {
                                        ocrFromCameraFlag = false
                                        studioViewModel.clearBatch()
                                        navController.navigate(RouteScanner)
                                    },
                                    onOpenDocument = { doc ->
                                        navController.navigate(RoutePdfViewer(doc.id, editMode = false))
                                    },
                                    onEditDocument = { doc ->
                                        navController.navigate(RoutePdfViewer(doc.id, editMode = true))
                                    },
                                    onToggleFavorite = mainViewModel::toggleFavorite,
                                    onRenameDocument = { renameTarget = it },
                                    onShareDocument = ::sharePdf,
                                    onDownloadDocument = { doc ->
                                        pendingDownloadDoc = doc
                                        saveCopyLauncher.launch("${doc.title}.pdf")
                                    },
                                    onPrintDocument = mainViewModel::printDocument,
                                    onInfoDocument = { doc ->
                                        scope.launch {
                                            mainViewModel.loadPdfMetadata(doc).onSuccess { infoMetadata = it }
                                        }
                                    },
                                    onDeleteDocument = { deleteTarget = it }
                                )
                            }
                            composable<RouteTools> {
                                ToolsScreen(onSelectTool = ::launchTool)
                            }
                            composable<RouteSettings> {
                                SettingsScreen(
                                    preferences = preferences,
                                    storageStats = storageStats,
                                    onSetThemeMode = mainViewModel::setThemeMode,
                                    onSetLanguage = mainViewModel::setLanguage,
                                    onSetDefaultPageSize = mainViewModel::setDefaultPageSize,
                                    onSetDefaultQuality = mainViewModel::setDefaultImageQuality,
                                    onSetDefaultFilePrefix = mainViewModel::setDefaultFilePrefix,
                                    onSaveSignature = mainViewModel::saveReusableSignature,
                                    onClearSignature = mainViewModel::clearReusableSignature,
                                    onClearAllDocuments = mainViewModel::clearAllDocuments,
                                    onReplayOnboarding = { navController.navigate(RouteOnboarding) }
                                )
                            }
                            composable<RouteScanner> {
                                ScannerScreen(
                                    scannedPages = studioPages,
                                    onBack = { navController.popBackStack() },
                                    onCapturedFile = { file, _ ->
                                        if (ocrFromCameraFlag) {
                                            ocrFromCameraFlag = false
                                            studioViewModel.runOcrOnScanFile(file, "Camera Capture")
                                            navController.popBackStack()
                                        } else {
                                            studioViewModel.addCapturedFile(file, autoDetect = true) {
                                                navController.navigate(RouteScanReview)
                                            }
                                        }
                                    },
                                    onPickedGalleryImages = { uris ->
                                        studioViewModel.addGalleryUris(uris, autoDetect = true)
                                        navController.navigate(RouteScanReview)
                                    },
                                    onReviewBatch = {
                                        navController.navigate(RouteScanReview)
                                    }
                                )
                            }
                            composable<RouteScanReview> {
                                ScanReviewScreen(
                                    pages = studioPages,
                                    activePageId = activePageId,
                                    defaultPrefix = preferences.defaultFilePrefix,
                                    defaultPageSize = preferences.defaultPageSize,
                                    defaultQuality = preferences.defaultImageQuality,
                                    onSelectPage = studioViewModel::selectActivePage,
                                    onAddMorePages = { navController.popBackStack() },
                                    onRetakePage = { pageId ->
                                        studioViewModel.replacePageWithRetake(pageId)
                                        navController.popBackStack()
                                    },
                                    onDeletePage = studioViewModel::removePage,
                                    onApplyEdits = { id, corners, rot, filter, bright, contrast ->
                                        studioViewModel.updatePageEdits(id, corners, rot, filter, bright, contrast)
                                    },
                                    onRunOcr = { page ->
                                        studioViewModel.runOcrOnScanFile(File(page.processedPath), "Scanned Page")
                                        navController.navigate(RouteOcr)
                                    },
                                    onSaveImageToUri = { page, uri ->
                                        studioViewModel.saveScannedImagesToUriList(page, uri) { ok ->
                                            mainViewModel.showMessage(if (ok) "Saved scanned image" else "Save failed")
                                        }
                                    },
                                    onGeneratePdf = { options ->
                                        studioViewModel.generatePdfFromBatch(
                                            options = options,
                                            sourceType = "SCAN",
                                            onCreated = { doc ->
                                                studioViewModel.clearBatch()
                                                navController.popBackStack(RouteHome, inclusive = false)
                                                mainViewModel.presentResultDocument(doc)
                                            },
                                            onError = mainViewModel::showMessage
                                        )
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable<RouteImagesToPdf> {
                                ImagesToPdfScreen(
                                    pages = studioPages,
                                    defaultPrefix = preferences.defaultFilePrefix,
                                    defaultPageSize = preferences.defaultPageSize,
                                    defaultQuality = preferences.defaultImageQuality,
                                    onAddImages = { uris -> studioViewModel.addGalleryUris(uris, autoDetect = false) },
                                    onMovePage = studioViewModel::movePage,
                                    onRotatePage = { pageId ->
                                        studioViewModel.updatePageEdits(pageId, rotationDelta = 90)
                                    },
                                    onCropEnhancePage = { pageId ->
                                        studioViewModel.selectActivePage(pageId)
                                        navController.navigate(RouteScanReview)
                                    },
                                    onRemovePage = studioViewModel::removePage,
                                    onClearAll = studioViewModel::clearBatch,
                                    onGeneratePdf = { options ->
                                        studioViewModel.generatePdfFromBatch(
                                            options = options,
                                            sourceType = "GALLERY",
                                            onCreated = { doc ->
                                                studioViewModel.clearBatch()
                                                navController.popBackStack(RouteHome, inclusive = false)
                                                mainViewModel.presentResultDocument(doc)
                                            },
                                            onError = mainViewModel::showMessage
                                        )
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable<RouteOcr> {
                                OcrScreen(
                                    ocrText = ocrText,
                                    ocrSourceLabel = ocrSourceLabel,
                                    ocrError = ocrError,
                                    ocrLanguage = ocrLanguage,
                                    isProcessing = studioBusy,
                                    progress = studioProgress,
                                    documents = documents,
                                    onSelectLanguage = studioViewModel::setOcrLanguage,
                                    onTextChange = studioViewModel::updateOcrText,
                                    onPickImageUri = studioViewModel::runOcrOnUri,
                                    onOpenCameraForOcr = {
                                        ocrFromCameraFlag = true
                                        navController.navigate(RouteScanner)
                                    },
                                    onExtractFromPdf = studioViewModel::runTextExtractionOnPdf,
                                    onRerunOcr = studioViewModel::rerunLastOcr,
                                    onExportPdf = { title ->
                                        studioViewModel.exportOcrTextToPdf(
                                            title = title,
                                            onCreated = { doc -> mainViewModel.presentResultDocument(doc) },
                                            onError = mainViewModel::showMessage
                                        )
                                    },
                                    onNotify = mainViewModel::showMessage,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable<RoutePdfViewer> { entry ->
                                val route = entry.toRoute<RoutePdfViewer>()
                                val doc = documents.find { it.id == route.documentId }
                                if (doc != null) {
                                    PdfViewerEditorScreen(
                                        document = doc,
                                        initialEditMode = route.editMode,
                                        initialSignMode = route.signMode,
                                        savedSignaturePath = preferences.savedSignaturePath,
                                        pdfEngine = container.pdfEngine,
                                        imageProcessor = container.imageProcessor,
                                        repository = container.documentRepository,
                                        onSaved = { savedDoc ->
                                            navController.popBackStack()
                                            mainViewModel.presentResultDocument(savedDoc)
                                        },
                                        onShare = ::sharePdf,
                                        onPrint = mainViewModel::printDocument,
                                        onBack = { navController.popBackStack() }
                                    )
                                } else {
                                    LaunchedEffect(Unit) { navController.popBackStack() }
                                }
                            }
                        }
                    }
                }
            }

            // Global Overlays & Sheets
            ProcessingOverlay(
                visible = isBusy || (studioBusy && dest?.hasRoute<RouteOcr>() != true),
                message = if (isBusy) busyLabel else studioStatus,
                progress = if (studioBusy && studioProgress > 0f) studioProgress else null
            )

            resultDocument?.let { doc ->
                PdfResultBottomSheet(
                    document = doc,
                    onDismiss = mainViewModel::dismissResultDocument,
                    onOpenReader = {
                        mainViewModel.dismissResultDocument()
                        navController.navigate(RoutePdfViewer(doc.id, editMode = false))
                    },
                    onOpenEditor = {
                        mainViewModel.dismissResultDocument()
                        navController.navigate(RoutePdfViewer(doc.id, editMode = true))
                    },
                    onShare = { sharePdf(doc) },
                    onSaveCopy = {
                        pendingDownloadDoc = doc
                        saveCopyLauncher.launch("${doc.title}.pdf")
                    },
                    onPrint = { mainViewModel.printDocument(doc) }
                )
            }

            renameTarget?.let { doc ->
                RenameDocumentDialog(
                    currentTitle = doc.title,
                    onDismiss = { renameTarget = null },
                    onConfirm = { newTitle ->
                        mainViewModel.renameDocument(doc, newTitle)
                        renameTarget = null
                    }
                )
            }

            deleteTarget?.let { doc ->
                DeleteConfirmDialog(
                    title = "Delete PDF?",
                    message = "Are you sure you want to delete ${doc.title}.pdf from PinkScan?",
                    onDismiss = { deleteTarget = null },
                    onConfirm = {
                        mainViewModel.deleteDocument(doc)
                        deleteTarget = null
                    }
                )
            }

            infoMetadata?.let { meta ->
                PdfMetadataDialog(info = meta, onDismiss = { infoMetadata = null })
            }

            // Single PDF Picker for Tools
            activeToolPicker?.let { tool ->
                SinglePdfPickerDialog(
                    title = "Select PDF for ${tool.title}",
                    documents = documents,
                    onImportExternal = { importPdfLauncher.launch(arrayOf("application/pdf")) },
                    onSelect = { doc ->
                        activeToolPicker = null
                        when (tool) {
                            PdfToolType.SPLIT_PDF -> splitTargetDoc = doc
                            PdfToolType.COMPRESS_PDF -> compressTargetDoc = doc
                            PdfToolType.PDF_TO_IMAGES -> {
                                studioViewModel.exportPdfToImagesAndZip(
                                    doc = doc,
                                    onReady = { imgs, zip -> pdfToImagesResult = imgs to zip },
                                    onError = mainViewModel::showMessage
                                )
                            }
                            PdfToolType.ROTATE_PAGES -> rotateTargetDoc = doc
                            PdfToolType.DELETE_PAGES -> deletePagesTargetDoc = doc
                            PdfToolType.ADD_WATERMARK -> watermarkTargetDoc = doc
                            PdfToolType.ADD_PAGE_NUMBERS -> pageNumbersTargetDoc = doc
                            PdfToolType.SIGN_PDF -> navController.navigate(RoutePdfViewer(doc.id, editMode = true, signMode = true))
                            PdfToolType.PDF_INFO -> {
                                scope.launch {
                                    mainViewModel.loadPdfMetadata(doc).onSuccess { infoMetadata = it }
                                }
                            }
                            PdfToolType.PRINT_PDF -> mainViewModel.printDocument(doc)
                            else -> Unit
                        }
                    },
                    onDismiss = { activeToolPicker = null }
                )
            }

            if (showMergeDialog) {
                MergePdfsDialog(
                    documents = documents,
                    onImportExternal = { importPdfLauncher.launch(arrayOf("application/pdf")) },
                    onMerge = { list, title ->
                        showMergeDialog = false
                        studioViewModel.mergePdfs(
                            docs = list,
                            outputTitle = title,
                            onCreated = { mainViewModel.presentResultDocument(it) },
                            onError = mainViewModel::showMessage
                        )
                    },
                    onDismiss = { showMergeDialog = false }
                )
            }

            splitTargetDoc?.let { doc ->
                SplitPdfDialog(
                    doc = doc,
                    onSplitRange = { start, end, title ->
                        splitTargetDoc = null
                        studioViewModel.splitPdfRange(
                            doc = doc,
                            startPage = start,
                            endPage = end,
                            outputTitle = title,
                            onCreated = { mainViewModel.presentResultDocument(it) },
                            onError = mainViewModel::showMessage
                        )
                    },
                    onSplitAllPages = {
                        splitTargetDoc = null
                        studioViewModel.splitPdfAllPages(
                            doc = doc,
                            onCompleted = { created ->
                                mainViewModel.showMessage("Created ${created.size} separate PDFs")
                                created.firstOrNull()?.let { mainViewModel.presentResultDocument(it) }
                            },
                            onError = mainViewModel::showMessage
                        )
                    },
                    onDismiss = { splitTargetDoc = null }
                )
            }

            compressTargetDoc?.let { doc ->
                CompressPdfDialog(
                    doc = doc,
                    onCompress = { level, title ->
                        compressTargetDoc = null
                        studioViewModel.compressPdf(
                            doc = doc,
                            level = level,
                            outputTitle = title,
                            onCreated = { saved, origBytes ->
                                mainViewModel.showMessage(
                                    "Compressed ${formatBytes(origBytes)} → ${formatBytes(saved.fileSizeBytes)}"
                                )
                                mainViewModel.presentResultDocument(saved)
                            },
                            onError = mainViewModel::showMessage
                        )
                    },
                    onDismiss = { compressTargetDoc = null }
                )
            }

            rotateTargetDoc?.let { doc ->
                RotateOrDeletePagesDialog(
                    doc = doc,
                    isDeleteMode = false,
                    onApplyRotate = { map, title ->
                        rotateTargetDoc = null
                        studioViewModel.rotatePdf(
                            doc = doc,
                            rotations = map,
                            outputTitle = title,
                            onCreated = { mainViewModel.presentResultDocument(it) },
                            onError = mainViewModel::showMessage
                        )
                    },
                    onApplyDelete = { _, _ -> },
                    onDismiss = { rotateTargetDoc = null }
                )
            }

            deletePagesTargetDoc?.let { doc ->
                RotateOrDeletePagesDialog(
                    doc = doc,
                    isDeleteMode = true,
                    onApplyRotate = { _, _ -> },
                    onApplyDelete = { set, title ->
                        deletePagesTargetDoc = null
                        studioViewModel.deletePdfPages(
                            doc = doc,
                            indicesToDelete = set,
                            outputTitle = title,
                            onCreated = { mainViewModel.presentResultDocument(it) },
                            onError = mainViewModel::showMessage
                        )
                    },
                    onDismiss = { deletePagesTargetDoc = null }
                )
            }

            watermarkTargetDoc?.let { doc ->
                WatermarkDialog(
                    doc = doc,
                    onApply = { cfg, title ->
                        watermarkTargetDoc = null
                        studioViewModel.addWatermark(
                            doc = doc,
                            config = cfg,
                            outputTitle = title,
                            onCreated = { mainViewModel.presentResultDocument(it) },
                            onError = mainViewModel::showMessage
                        )
                    },
                    onDismiss = { watermarkTargetDoc = null }
                )
            }

            pageNumbersTargetDoc?.let { doc ->
                PageNumbersDialog(
                    doc = doc,
                    onApply = { cfg, title ->
                        pageNumbersTargetDoc = null
                        studioViewModel.addPageNumbers(
                            doc = doc,
                            config = cfg,
                            outputTitle = title,
                            onCreated = { mainViewModel.presentResultDocument(it) },
                            onError = mainViewModel::showMessage
                        )
                    },
                    onDismiss = { pageNumbersTargetDoc = null }
                )
            }

            pdfToImagesResult?.let { (imgs, zip) ->
                PdfToImagesResultDialog(
                    imageFiles = imgs,
                    zipFile = zip,
                    onSaveZipToUri = { file, uri ->
                        studioViewModel.copyFileToUri(file, uri) { ok ->
                            mainViewModel.showMessage(if (ok) "Saved PNG archive (.zip)" else "Save failed")
                        }
                    },
                    onShareZip = { file ->
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Exported PNGs"))
                    },
                    onDismiss = { pdfToImagesResult = null }
                )
            }
        }
    }
}
