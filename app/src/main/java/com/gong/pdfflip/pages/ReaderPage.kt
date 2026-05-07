package com.gong.pdfflip.pages

/**
 * READER PAGE - The "Reading Room"
 * This is the core viewing experience of the app.
 * Key Features:
 * 1. PDF Rendering: Uses Android's PdfRenderer for high-performance viewing.
 * 2. Flex Zoom: Bouncy, elastic zoom and pan with a dedicated toggle.
 * 3. Eye Protection: Multi-mode backgrounds (like Sepia) and content filtering.
 * 4. Drawing & Annotations: Sketch directly on PDF pages with undo/redo/save.
 * 5. Text-to-Speech: "Read Aloud" function with intelligent text cleaning.
 */

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

// Data class to store our strokes for saving (Points are normalized 0f..1f)
data class DrawingStroke(val points: List<Offset>, val color: Color = Color.Red)

enum class PageMode {
    HorizontalFlip,
    VerticalScroll
}

enum class SpeechState {
    Stopped,
    Playing,
    Paused
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    uri: Uri, 
    fileName: String,
    sourceUriStr: String? = null,
    initialPage: Int = 0, 
    initialPageModeIndex: Int = 0,
    flipStyleIndex: Int = 0,
    scrollStyleIndex: Int = 0,
    onBack: () -> Unit,
    onPageModeToggle: (Int) -> Unit,
    showTimer: Boolean = false,
    showAiTool: Boolean = false,
    isAiCollapsed: Boolean = false,
    aiApiKey: String = "",
    aiModel: String = "gemini-1.5-flash"
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // --- STATES ---
    var pageCount by remember { mutableIntStateOf(0) }
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var isFullScreen by remember { mutableStateOf(false) }
    var isEyeProtectionActive by remember { mutableStateOf(true) } 
    var showContents by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var isBookmarked by remember { mutableStateOf(false) }
    var isDrawingMode by remember { mutableStateOf(false) }
    var isAiMode by remember { mutableStateOf(false) }
    var aiExplanation by remember { mutableStateOf<String?>(null) }
    var isAiAnalyzing by remember { mutableStateOf(false) }

    var selectedPenColor by remember { mutableStateOf(Color.Red) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var pageMode by remember { mutableStateOf(if (initialPageModeIndex == 0) PageMode.HorizontalFlip else PageMode.VerticalScroll) }
    
    val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(0, if (pageCount > 0) pageCount - 1 else 0), pageCount = { pageCount })
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }

    // Zoom States (Flexible/Spring Animated)
    var isZoomMode by remember { mutableStateOf(false) }
    var targetScale by remember { mutableFloatStateOf(1f) }
    var targetOffset by remember { mutableStateOf(Offset.Zero) }
    
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "ZoomScale"
    )
    val offset by animateOffsetAsState(
        targetValue = targetOffset,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "ZoomOffset"
    )

    // Drawing States
    val pageStrokes: SnapshotStateMap<Int, SnapshotStateList<DrawingStroke>> = remember { mutableStateMapOf() }
    val redoStrokes: SnapshotStateMap<Int, SnapshotStateList<DrawingStroke>> = remember { mutableStateMapOf() }
    
    // TTS States
    var speechState by remember { mutableStateOf(SpeechState.Stopped) }
    var currentSentences by remember { mutableStateOf(emptyList<String>()) }
    var currentSentenceIndex by remember { mutableIntStateOf(0) }
    var ttsSourcePage by remember { mutableIntStateOf(-1) }

    // Timer States
    var timerSeconds by remember { mutableIntStateOf(0) }
    var isTimerRunning by remember { mutableStateOf(false) }

    // Timer Effect
    LaunchedEffect(isTimerRunning) {
        if (isTimerRunning) {
            while (true) {
                delay(1000)
                timerSeconds++
            }
        }
    }

    fun formatTime(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return "%02d:%02d:%02d".format(h, m, s)
    }
    fun speakCurrentSentence(engine: TextToSpeech?, list: List<String>, index: Int) {
        if (engine != null && index in list.indices) {
            val params = Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "ReaderTTS_$index")
            engine.speak(list[index], TextToSpeech.QUEUE_FLUSH, params, "ReaderTTS_$index")
        }
    }

    fun handleSpeechStop() {
        tts?.stop()
        speechState = SpeechState.Stopped
        currentSentenceIndex = 0
    }

    fun handleSpeechToggle() {
        when (speechState) {
            SpeechState.Playing -> {
                tts?.stop()
                speechState = SpeechState.Paused
            }
            SpeechState.Paused -> {
                speakCurrentSentence(tts, currentSentences, currentSentenceIndex)
            }
            SpeechState.Stopped -> {
                scope.launch(Dispatchers.IO) {
                    try {
                        val inputStream = context.contentResolver.openInputStream(uri)
                        if (inputStream != null) {
                            val document = PDDocument.load(inputStream)
                            val stripper = PDFTextStripper()
                            stripper.startPage = pagerState.currentPage + 1
                            stripper.endPage = pagerState.currentPage + 1
                            val pageText = stripper.getText(document).trim()
                            document.close()
                            inputStream.close()
                            
                            withContext(Dispatchers.Main) {
                                if (pageText.isNotEmpty()) {
                                    val cleaned = pageText
                                        .replace(Regex("(?<=[a-z])-\\n(?=[a-z])"), "") 
                                        .replace(Regex("\\n"), " ") 
                                        .replace(Regex("\\s+"), " ") 
                                        .trim()
                                    
                                    // Split into sentences using common delimiters
                                    currentSentences = cleaned.split(Regex("(?<=[.!?])\\s+"))
                                    currentSentenceIndex = 0
                                    ttsSourcePage = pagerState.currentPage
                                    speakCurrentSentence(tts, currentSentences, 0)
                                } else {
                                    Toast.makeText(context, "No text found on this page", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }
        }
    }

    // Function to handle the actual saving logic
    fun saveEditsToPdf() {
        scope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) { Toast.makeText(context, "Saving...", Toast.LENGTH_SHORT).show() }
                
                pdfRenderer?.close()
                pdfRenderer = null
                
                val inputStream = context.contentResolver.openInputStream(uri)
                val document = PDDocument.load(inputStream)
                document.setAllSecurityToBeRemoved(true)
                
                pageStrokes.forEach { (pageIdx, strokes) ->
                    if (pageIdx < document.numberOfPages) {
                        val page = document.getPage(pageIdx)
                        val mBox = page.mediaBox
                        
                        val contentStream = PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)
                        
                        strokes.forEach { stroke ->
                            if (stroke.points.isNotEmpty()) {
                                val r = (stroke.color.red * 255).toInt()
                                val g = (stroke.color.green * 255).toInt()
                                val b = (stroke.color.blue * 255).toInt()
                                
                                contentStream.setStrokingColor(r, g, b)
                                contentStream.setLineWidth(3f)
                                
                                val first = stroke.points.first()
                                val startX = mBox.lowerLeftX + (first.x * mBox.width)
                                val startY = mBox.lowerLeftY + (mBox.height - (first.y * mBox.height))
                                
                                contentStream.moveTo(startX, startY)
                                
                                stroke.points.forEach { pt ->
                                    val nextX = mBox.lowerLeftX + (pt.x * mBox.width)
                                    val nextY = mBox.lowerLeftY + (mBox.height - (pt.y * mBox.height))
                                    contentStream.lineTo(nextX, nextY)
                                }
                                contentStream.stroke()
                            }
                        }
                        contentStream.close()
                    }
                }
                
                val outputStream = context.contentResolver.openOutputStream(uri, "rwt")
                if (outputStream != null) {
                    document.save(outputStream)
                    outputStream.flush()
                    outputStream.close()
                }
                
                document.close()
                inputStream?.close()
                
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                pfd?.let { pdfRenderer = PdfRenderer(it) }
                
                withContext(Dispatchers.Main) {
                    pageStrokes.clear()
                    redoStrokes.clear()
                    isDrawingMode = false
                    Toast.makeText(context, "Saved Successfully!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Save Failed: ${e.message}", Toast.LENGTH_LONG).show()
                    if (pdfRenderer == null) {
                        try {
                            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                            pfd?.let { pdfRenderer = PdfRenderer(it) }
                        } catch (ex: Exception) { ex.printStackTrace() }
                    }
                }
            }
        }
    }

    // TTS Engine Initialization
    LaunchedEffect(Unit) {
        PDFBoxResourceLoader.init(context) 
        val newTts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setLanguage(Locale.US)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { 
                        scope.launch(Dispatchers.Main) { speechState = SpeechState.Playing }
                    }
                    override fun onDone(utteranceId: String?) { 
                        scope.launch(Dispatchers.Main) {
                            if (speechState == SpeechState.Playing) { // Safety: Only continue if the user hasn't paused/stopped
                                if (currentSentenceIndex < currentSentences.size - 1) {
                                    currentSentenceIndex++
                                    speakCurrentSentence(tts, currentSentences, currentSentenceIndex)
                                } else {
                                    speechState = SpeechState.Stopped
                                    currentSentenceIndex = 0
                                }
                            }
                        }
                    }
                    override fun onError(utteranceId: String?) { 
                        scope.launch(Dispatchers.Main) { 
                            speechState = SpeechState.Stopped
                            Toast.makeText(context, "Voice engine error", Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            }
        }
        tts = newTts
    }

    // Reset TTS if page changes while playing
    LaunchedEffect(pagerState.currentPage) {
        if (speechState != SpeechState.Stopped && ttsSourcePage != pagerState.currentPage) {
            handleSpeechStop()
        }
    }

    // Initialize Renderer
    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                pfd?.let {
                    val renderer = PdfRenderer(it)
                    pdfRenderer = renderer
                    pageCount = renderer.pageCount
                    
                    // Jump to initial page after loading
                    if (initialPage > 0 && initialPage < renderer.pageCount) {
                        withContext(Dispatchers.Main) {
                            pagerState.scrollToPage(initialPage)
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // Save current page to local storage (No Sync - Pure Local Memory)
    LaunchedEffect(pagerState.currentPage) {
        if (pageCount > 0) {
            scope.launch(Dispatchers.IO) {
                val recentFile = java.io.File(context.filesDir, "recent_data.txt")
                val lines = if (recentFile.exists()) recentFile.readLines().toMutableList() else mutableListOf()
                
                val timestamp = System.currentTimeMillis()
                val existingIndex = lines.indexOfFirst { it.startsWith("$fileName|") }
                val newLine = "$fileName|$timestamp|${pagerState.currentPage}|$pageCount"
                
                if (existingIndex != -1) {
                    lines.removeAt(existingIndex)
                }
                lines.add(0, newLine)
                
                // Keep only top 10
                recentFile.writeText(lines.take(10).joinToString("\n"))
            }
        }
    }

    // Use Theme Colors by default, but allow Eye Protection (Sepia) override
    val themeBg = MaterialTheme.colorScheme.background
    val themeText = MaterialTheme.colorScheme.onBackground
    
    val currentBgColor = if (isEyeProtectionActive) Color(0xFFF4ECD8) else themeBg
    val currentTextColor = if (isEyeProtectionActive) Color(0xFF5B4636) else themeText

    Scaffold(
        topBar = {
            AnimatedVisibility(visible = !isFullScreen, enter = slideInVertically { -it }, exit = slideOutVertically { -it }) {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = currentTextColor) } },
                    title = {
                        Column {
                            if (showTimer) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = formatTime(timerSeconds),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = currentTextColor
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    IconButton(onClick = { isTimerRunning = !isTimerRunning }, modifier = Modifier.size(24.dp)) {
                                        Icon(
                                            if (isTimerRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "Timer Control",
                                            tint = currentTextColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(onClick = { timerSeconds = 0 }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Refresh, "Reset", tint = currentTextColor, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = { isTimerRunning = false; timerSeconds = 0 }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Stop, "Stop", tint = Color.Red, modifier = Modifier.size(18.dp))
                                    }
                                }
                            } else {
                                Text(uri.lastPathSegment ?: "Reading", fontSize = 16.sp, color = currentTextColor, fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    actions = {
                        ReaderToolIcon(
                            icon = if (isEyeProtectionActive) Icons.Default.Visibility else Icons.Default.VisibilityOff, 
                            description = "Eye Protection", 
                            tint = currentTextColor
                        ) { 
                            isEyeProtectionActive = !isEyeProtectionActive 
                        }
                        ReaderToolIcon(Icons.AutoMirrored.Filled.List, "Contents", currentTextColor) { showContents = true }
                        ReaderToolIcon(Icons.Default.Search, "Search", currentTextColor) { showSearch = true }
                        
                        ReaderToolIcon(
                            icon = if (pageMode == PageMode.HorizontalFlip) Icons.Default.SwapVert else Icons.Default.SwapHoriz, 
                            description = "Switch Page Mode", 
                            tint = currentTextColor
                        ) { 
                            pageMode = if (pageMode == PageMode.HorizontalFlip) PageMode.VerticalScroll else PageMode.HorizontalFlip 
                            onPageModeToggle(if (pageMode == PageMode.HorizontalFlip) 0 else 1)
                        }

                        ReaderToolIcon(Icons.Default.Fullscreen, "Full Screen", currentTextColor) { isFullScreen = true }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = currentBgColor)
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(visible = !isFullScreen, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                BottomAppBar(containerColor = currentBgColor) {
                    if (pageCount > 0) {
                        Text(
                            text = "Page ${pagerState.currentPage + 1} of $pageCount",
                            fontSize = 12.sp,
                            color = currentTextColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        VerticalDivider(modifier = Modifier.padding(vertical = 12.dp), color = currentTextColor.copy(alpha = 0.2f))
                    }
                    
                    ReaderToolIcon(
                        icon = when (speechState) {
                            SpeechState.Playing -> Icons.Default.Pause
                            else -> Icons.AutoMirrored.Filled.VolumeUp
                        }, 
                        description = "Read Aloud", 
                        tint = currentTextColor
                    ) {
                        handleSpeechToggle()
                    }

                    if (speechState != SpeechState.Stopped) {
                        ReaderToolIcon(Icons.Default.Stop, "Stop Aloud", Color.Red) {
                            handleSpeechStop()
                        }
                    }

                    // --- AI TOOL ---
                    if (showAiTool && !isAiCollapsed) {
                        ReaderToolIcon(
                            icon = Icons.Default.AutoAwesome, 
                            description = "AI Explain", 
                            tint = if (isAiMode) Color(0xFF6200EE) else currentTextColor
                        ) { 
                            if (aiApiKey.isBlank()) {
                                Toast.makeText(context, "Please set your Gemini API key in Settings", Toast.LENGTH_LONG).show()
                            } else {
                                isAiMode = !isAiMode
                                isDrawingMode = false // Mutually exclusive
                                isZoomMode = false
                            }
                        }
                    }

                    // --- DRAW & SAVE TOOLS ---
                    ReaderToolIcon(if (isDrawingMode) Icons.Default.EditOff else Icons.Default.Edit, "Draw", currentTextColor) { isDrawingMode = !isDrawingMode }
                    
                    if (isDrawingMode) {
                        // Pen Color Picker
                        Box {
                            Box(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(20.dp)
                                    .background(selectedPenColor, CircleShape)
                                    .clickable { showColorPicker = true }
                            )
                        }

                        // Undo
                        ReaderToolIcon(
                            icon = Icons.AutoMirrored.Filled.Undo, 
                            description = "Undo", 
                            tint = if ((pageStrokes[pagerState.currentPage]?.size ?: 0) > 0) currentTextColor else currentTextColor.copy(alpha = 0.3f)
                        ) {
                            val currentList = pageStrokes[pagerState.currentPage]
                            if (!currentList.isNullOrEmpty()) {
                                val lastStroke = currentList.removeAt(currentList.size - 1)
                                redoStrokes.getOrPut(pagerState.currentPage) { mutableStateListOf<DrawingStroke>() }.add(lastStroke)
                            }
                        }

                        // Redo
                        ReaderToolIcon(
                            icon = Icons.AutoMirrored.Filled.Redo, 
                            description = "Redo", 
                            tint = if ((redoStrokes[pagerState.currentPage]?.size ?: 0) > 0) currentTextColor else currentTextColor.copy(alpha = 0.3f)
                        ) {
                            val redoList = redoStrokes[pagerState.currentPage]
                            if (!redoList.isNullOrEmpty()) {
                                val lastRedo = redoList.removeAt(redoList.size - 1)
                                pageStrokes.getOrPut(pagerState.currentPage) { mutableStateListOf<DrawingStroke>() }.add(lastRedo)
                            }
                        }

                        // Save Permanent
                        ReaderToolIcon(Icons.Default.Check, "Save Permanent", Color.Green) {
                            saveEditsToPdf()
                        }

                        // Cancel / Discard
                        ReaderToolIcon(Icons.Default.Close, "Cancel", Color.Red) {
                            if (pageStrokes.values.any { it.isNotEmpty() }) {
                                showDiscardDialog = true
                            } else {
                                isDrawingMode = false
                            }
                        }
                    }

                    ReaderToolIcon(Icons.Default.Share, "Share", currentTextColor) {
                        val intent = Intent(Intent.ACTION_SEND).apply { type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri) }
                        context.startActivity(Intent.createChooser(intent, "Share PDF"))
                    }

                    // --- ZOOM TOOLS ---
                    VerticalDivider(modifier = Modifier.padding(vertical = 12.dp), color = currentTextColor.copy(alpha = 0.2f))
                    
                    ReaderToolIcon(if (isZoomMode) Icons.Default.ZoomIn else Icons.Default.ZoomIn, "Zoom Mode", if (isZoomMode) Color.Cyan else currentTextColor) {
                        isZoomMode = !isZoomMode
                        if (!isZoomMode) {
                            targetScale = 1f
                            targetOffset = Offset.Zero
                        }
                    }

                    if (isZoomMode) {
                        ReaderToolIcon(Icons.Default.Add, "Zoom In", currentTextColor) { 
                            targetScale = (targetScale + 0.3f).coerceAtMost(3f) 
                        }
                        ReaderToolIcon(Icons.Default.Remove, "Zoom Out", currentTextColor) { 
                            targetScale = (targetScale - 0.3f).coerceAtLeast(1f)
                            if (targetScale == 1f) targetOffset = Offset.Zero
                        }
                    }
                }
            }
        },
        containerColor = currentBgColor
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (pageCount > 0) {
                if (pageMode == PageMode.HorizontalFlip) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.padding(if (isFullScreen) PaddingValues(0.dp) else innerPadding).fillMaxSize(),
                        userScrollEnabled = !isDrawingMode && !isZoomMode && targetScale == 1f
                    ) { pageIndex: Int ->
                        // Apply "Natural Paper" transformation if selected
                        val graphicsModifier = Modifier.graphicsLayer {
                            if (flipStyleIndex == 1) { // Natural Paper Flip
                                val pageOffset = (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
                                if (pageOffset < 0) { // Page to the right
                                    cameraDistance = 12f * density
                                    rotationY = -15f * pageOffset.coerceIn(-1f, 0f)
                                }
                            }
                        }

                        Box(modifier = graphicsModifier) {
                            ReaderPageContent(
                                pdfRenderer = pdfRenderer,
                                pageIndex = pageIndex,
                                isFullScreen = isFullScreen,
                                isEyeProtectionActive = isEyeProtectionActive,
                                isDrawingMode = isDrawingMode,
                                selectedPenColor = selectedPenColor,
                                isZoomMode = isZoomMode,
                                targetScale = targetScale,
                                targetOffset = targetOffset,
                                scale = scale,
                                offset = offset,
                                pageStrokes = pageStrokes,
                                redoStrokes = redoStrokes,
                                onTransform = { zoom: Float, pan: Offset ->
                                    targetScale = (targetScale * zoom).coerceIn(1f, 3.5f)
                                    if (targetScale > 1f) targetOffset += pan else targetOffset = Offset.Zero
                                },
                                isAiMode = isAiMode,
                                onAiSelect = { bitmap, path -> 
                                    performAiAnalysis(
                                        apiKey = aiApiKey,
                                        modelName = aiModel,
                                        pageBitmap = bitmap,
                                        circlePath = path,
                                        onStart = { isAiAnalyzing = true; isAiMode = false },
                                        onResult = { aiExplanation = it; isAiAnalyzing = false },
                                        onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); isAiAnalyzing = false },
                                        scope = scope
                                    )
                                }
                            )
                        }
                    }
                } else {
                    if (scrollStyleIndex == 1) { // Smooth Flow
                        LazyColumn(
                            modifier = Modifier
                                .padding(if (isFullScreen) PaddingValues(0.dp) else innerPadding)
                                .fillMaxSize()
                        ) {
                            items(pageCount) { pageIndex ->
                                Box(modifier = Modifier
                                    .fillMaxWidth()
                                    .fillParentMaxHeight() // Occupy full screen height for each page
                                ) {
                                    ReaderPageContent(
                                        pdfRenderer = pdfRenderer,
                                        pageIndex = pageIndex,
                                        isFullScreen = isFullScreen,
                                        isEyeProtectionActive = isEyeProtectionActive,
                                        isDrawingMode = isDrawingMode,
                                        selectedPenColor = selectedPenColor,
                                        isZoomMode = isZoomMode,
                                        targetScale = targetScale,
                                        targetOffset = targetOffset,
                                        scale = scale,
                                        offset = offset,
                                        pageStrokes = pageStrokes,
                                        redoStrokes = redoStrokes,
                                        onTransform = { zoom: Float, pan: Offset ->
                                            targetScale = (targetScale * zoom).coerceIn(1f, 3.5f)
                                            if (targetScale > 1f) targetOffset += pan else targetOffset = Offset.Zero
                                        },
                                        isAiMode = isAiMode,
                                        onAiSelect = { bitmap, path -> 
                                            performAiAnalysis(
                                                apiKey = aiApiKey,
                                                modelName = aiModel,
                                                pageBitmap = bitmap,
                                                circlePath = path,
                                                onStart = { isAiAnalyzing = true; isAiMode = false },
                                                onResult = { aiExplanation = it; isAiAnalyzing = false },
                                                onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); isAiAnalyzing = false },
                                                scope = scope
                                            )
                                        }
                                    )
                                }
                                if (pageIndex < pageCount - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        color = currentTextColor.copy(alpha = 0.05f)
                                    )
                                }
                            }
                        }
                    } else { // Page Snap (Normal Vertical)
                        VerticalPager(
                            state = pagerState,
                            modifier = Modifier.padding(if (isFullScreen) PaddingValues(0.dp) else innerPadding).fillMaxSize(),
                            userScrollEnabled = !isDrawingMode && !isZoomMode && targetScale == 1f
                        ) { pageIndex: Int ->
                            ReaderPageContent(
                                pdfRenderer = pdfRenderer,
                                pageIndex = pageIndex,
                                isFullScreen = isFullScreen,
                                isEyeProtectionActive = isEyeProtectionActive,
                                isDrawingMode = isDrawingMode,
                                selectedPenColor = selectedPenColor,
                                isZoomMode = isZoomMode,
                                targetScale = targetScale,
                                targetOffset = targetOffset,
                                scale = scale,
                                offset = offset,
                                pageStrokes = pageStrokes,
                                redoStrokes = redoStrokes,
                                onTransform = { zoom: Float, pan: Offset ->
                                    targetScale = (targetScale * zoom).coerceIn(1f, 3.5f)
                                    if (targetScale > 1f) targetOffset += pan else targetOffset = Offset.Zero
                                },
                                isAiMode = isAiMode,
                                onAiSelect = { bitmap, path -> 
                                    performAiAnalysis(
                                        apiKey = aiApiKey,
                                        modelName = aiModel,
                                        pageBitmap = bitmap,
                                        circlePath = path,
                                        onStart = { isAiAnalyzing = true; isAiMode = false },
                                        onResult = { aiExplanation = it; isAiAnalyzing = false },
                                        onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); isAiAnalyzing = false },
                                        scope = scope
                                    )
                                }
                            )
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = currentTextColor) }
            }

            if (isFullScreen) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    // Top Overlay Bar in Full Screen
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (showTimer) {
                            Text(
                                text = formatTime(timerSeconds),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            IconButton(onClick = { isTimerRunning = !isTimerRunning }, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    if (isTimerRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Timer Control",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(onClick = { timerSeconds = 0 }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Refresh, "Reset", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }

                        // Drawing Toggle
                        ReaderToolIcon(if (isDrawingMode) Icons.Default.EditOff else Icons.Default.Edit, "Draw", Color.White) { 
                            isDrawingMode = !isDrawingMode 
                        }

                        if (isDrawingMode) {
                            // Pen Color Picker
                            Box(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(20.dp)
                                    .background(selectedPenColor, CircleShape)
                                    .clickable { showColorPicker = true }
                            )

                            // Undo
                            ReaderToolIcon(
                                icon = Icons.AutoMirrored.Filled.Undo, 
                                description = "Undo", 
                                tint = if ((pageStrokes[pagerState.currentPage]?.size ?: 0) > 0) Color.White else Color.White.copy(alpha = 0.3f)
                            ) {
                                val currentList = pageStrokes[pagerState.currentPage]
                                if (!currentList.isNullOrEmpty()) {
                                    val lastStroke = currentList.removeAt(currentList.size - 1)
                                    redoStrokes.getOrPut(pagerState.currentPage) { mutableStateListOf<DrawingStroke>() }.add(lastStroke)
                                }
                            }

                            // Redo
                            ReaderToolIcon(
                                icon = Icons.AutoMirrored.Filled.Redo, 
                                description = "Redo", 
                                tint = if ((redoStrokes[pagerState.currentPage]?.size ?: 0) > 0) Color.White else Color.White.copy(alpha = 0.3f)
                            ) {
                                val redoList = redoStrokes[pagerState.currentPage]
                                if (!redoList.isNullOrEmpty()) {
                                    val lastRedo = redoList.removeAt(redoList.size - 1)
                                    pageStrokes.getOrPut(pagerState.currentPage) { mutableStateListOf<DrawingStroke>() }.add(lastRedo)
                                }
                            }

                            // Save Permanent
                            ReaderToolIcon(Icons.Default.Check, "Save Permanent", Color.Green) {
                                saveEditsToPdf()
                            }

                            // Cancel / Discard
                            ReaderToolIcon(Icons.Default.Close, "Cancel", Color.Red) {
                                if (pageStrokes.values.any { it.isNotEmpty() }) {
                                    showDiscardDialog = true
                                } else {
                                    isDrawingMode = false
                                }
                            }
                        }
                        
                        // Read Aloud Toggle
                        ReaderToolIcon(
                            icon = when (speechState) {
                                SpeechState.Playing -> Icons.Default.Pause
                                else -> Icons.AutoMirrored.Filled.VolumeUp
                            }, 
                            description = "Read Aloud", 
                            tint = Color.White
                        ) {
                            handleSpeechToggle()
                        }
                        
                        if (speechState != SpeechState.Stopped) {
                            ReaderToolIcon(Icons.Default.Stop, "Stop Aloud", Color.Red) {
                                handleSpeechStop()
                            }
                        }

                        // Zoom Mode Toggle
                        ReaderToolIcon(Icons.Default.ZoomIn, "Zoom Mode", if (isZoomMode) Color.Cyan else Color.White) {
                            isZoomMode = !isZoomMode
                            if (!isZoomMode) {
                                targetScale = 1f
                                targetOffset = Offset.Zero
                            }
                        }
                        
                        // AI Tool Toggle in Full Screen
                        if (showAiTool) {
                            ReaderToolIcon(
                                icon = Icons.Default.AutoAwesome, 
                                description = "AI Explain", 
                                tint = if (isAiMode) Color.Cyan else Color.White
                            ) { 
                                if (aiApiKey.isBlank()) {
                                    Toast.makeText(context, "Please set Gemini API key in Settings", Toast.LENGTH_LONG).show()
                                } else {
                                    isAiMode = !isAiMode
                                    isDrawingMode = false
                                    isZoomMode = false
                                }
                            }
                        }
                    }

                    // Exit Full Screen Button (Top Right)
                    FilledIconButton(
                        onClick = { isFullScreen = false }, 
                        modifier = Modifier.align(Alignment.TopEnd),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.FullscreenExit, "Exit", tint = Color.White)
                    }

                    // Page Number (Bottom Most)
                    if (pageCount > 0) {
                        Text(
                            text = "Page ${pagerState.currentPage + 1} of $pageCount",
                            fontSize = 12.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // --- FLOATING AI BUBBLE ---
            if (showAiTool && isAiCollapsed && !isFullScreen) {
                var offsetX by remember { mutableFloatStateOf(0f) }
                var offsetY by remember { mutableFloatStateOf(0f) }

                Box(modifier = Modifier.fillMaxSize()) {
                    FloatingActionButton(
                        onClick = {
                            if (aiApiKey.isBlank()) {
                                Toast.makeText(context, "Please set API key in Settings", Toast.LENGTH_LONG).show()
                            } else {
                                isAiMode = !isAiMode
                                isDrawingMode = false
                                isZoomMode = false
                            }
                        },
                        modifier = Modifier
                            .offset { IntOffset(offsetX.toInt(), offsetY.toInt()) }
                            .padding(16.dp)
                            .align(Alignment.CenterEnd)
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    offsetX += dragAmount.x
                                    offsetY += dragAmount.y
                                }
                            },
                        containerColor = if (isAiMode) Color(0xFF6200EE) else currentBgColor.copy(alpha = 0.8f),
                        contentColor = if (isAiMode) Color.White else currentTextColor,
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.AutoAwesome, "AI Assistant")
                    }
                }
            }
        }
    }

    // --- COLOR PICKER DIALOG ---
    if (showColorPicker) {
        val colors = listOf(Color.Red, Color.Blue, Color.Green, Color.Black, Color.Yellow, Color.Magenta)
        AlertDialog(
            onDismissRequest = { showColorPicker = false },
            title = { Text("Select Pen Color") },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    colors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(color, CircleShape)
                                .clickable { 
                                    selectedPenColor = color
                                    showColorPicker = false
                                }
                                .let { if (selectedPenColor == color) it.background(Color.Gray.copy(alpha = 0.3f), CircleShape) else it }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showColorPicker = false }) { Text("Close") }
            }
        )
    }

    // --- DISCARD CHANGES DIALOG ---
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard Changes?") },
            text = { Text("You have unsaved drawings. Are you sure you want to discard them and exit drawing mode?") },
            confirmButton = {
                Button(
                    onClick = {
                        pageStrokes.clear()
                        redoStrokes.clear()
                        showDiscardDialog = false
                        isDrawingMode = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Discard", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Continue Editing")
                }
            }
        )
    }

    // --- OVERLAYS (CONTENTS/SEARCH) ---
    if (showContents) {
        ModalBottomSheet(onDismissRequest = { showContents = false }, containerColor = currentBgColor, contentColor = currentTextColor) {
            Column(modifier = Modifier.fillMaxHeight(0.7f).padding(16.dp)) {
                Text("Table of Contents", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn {
                    items(pageCount) { index ->
                        ListItem(
                            headlineContent = { Text("Page ${index + 1}", color = currentTextColor) },
                            modifier = Modifier.clickable { scope.launch { pagerState.animateScrollToPage(index); showContents = false } },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }
        }
    }

    AiExplanationOverlay(
        explanation = aiExplanation,
        isAnalyzing = isAiAnalyzing,
        onDismiss = { aiExplanation = null },
        currentTextColor = currentTextColor
    )
    
    if (showSearch) {
        var pageInput by remember { mutableStateOf("") }
        
        fun performJump() {
            val p = pageInput.toIntOrNull()
            if (p != null && p in 1..pageCount) {
                scope.launch {
                    pagerState.scrollToPage(p - 1) // Direct jump without animation
                }
                showSearch = false
            } else if (p != null) {
                Toast.makeText(context, "Please enter a page between 1 and $pageCount", Toast.LENGTH_SHORT).show()
            }
        }

        AlertDialog(
            onDismissRequest = { showSearch = false },
            title = { Text("Jump to Page", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Total Pages: $pageCount", fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pageInput,
                        onValueChange = { if (it.all { c -> c.isDigit() }) pageInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Example: 15") },
                        label = { Text("Enter Page Number") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(onGo = { performJump() }),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = { performJump() }) {
                    Text("Go Directly")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSearch = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    DisposableEffect(Unit) { onDispose { pdfRenderer?.close(); tts?.stop(); tts?.shutdown() } }
}

@Composable
fun DrawingCanvas(strokes: SnapshotStateList<DrawingStroke>, redoList: SnapshotStateList<DrawingStroke>, selectedColor: Color, modifier: Modifier) {
    var currentPoints = remember { mutableStateListOf<Offset>() }
    var canvasSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    Canvas(modifier = modifier
        .onSizeChanged { canvasSize = it }
        .pointerInput(selectedColor) {
            detectDragGestures(
                onDragStart = { offset -> 
                    currentPoints.add(offset)
                    redoList.clear() 
                },
                onDrag = { change, _ -> 
                    currentPoints.add(change.position) 
                },
                onDragEnd = { 
                    if (canvasSize.width > 0 && canvasSize.height > 0) {
                        val normalized = currentPoints.map { Offset(it.x / canvasSize.width, it.y / canvasSize.height) }
                        strokes.add(DrawingStroke(normalized, selectedColor))
                    }
                    currentPoints.clear()
                }
            )
        }
    ) {
        // Draw existing strokes
        strokes.forEach { stroke ->
            val path = Path().apply {
                if (stroke.points.isNotEmpty()) {
                    val start = stroke.points.first()
                    moveTo(start.x * size.width, start.y * size.height)
                    stroke.points.forEach { lineTo(it.x * size.width, it.y * size.height) }
                }
            }
            drawPath(path, stroke.color, style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        
        // Draw current stroke
        if (currentPoints.isNotEmpty()) {
            val path = Path().apply {
                moveTo(currentPoints.first().x, currentPoints.first().y)
                currentPoints.forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, selectedColor, style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
fun ReaderPageContent(
    pdfRenderer: android.graphics.pdf.PdfRenderer?,
    pageIndex: Int,
    isFullScreen: Boolean,
    isEyeProtectionActive: Boolean,
    isDrawingMode: Boolean,
    selectedPenColor: Color,
    isZoomMode: Boolean,
    targetScale: Float,
    targetOffset: Offset,
    scale: Float,
    offset: Offset,
    pageStrokes: androidx.compose.runtime.snapshots.SnapshotStateMap<Int, SnapshotStateList<DrawingStroke>>,
    redoStrokes: androidx.compose.runtime.snapshots.SnapshotStateMap<Int, SnapshotStateList<DrawingStroke>>,
    onTransform: (Float, Offset) -> Unit,
    isAiMode: Boolean,
    onAiSelect: (Bitmap, List<Offset>) -> Unit
) {
    val strokes = pageStrokes.getOrPut(pageIndex) { mutableStateListOf<DrawingStroke>() }
    val redos = redoStrokes.getOrPut(pageIndex) { mutableStateListOf<DrawingStroke>() }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(isDrawingMode, isZoomMode, targetScale, isAiMode) {
                if (!isDrawingMode && !isAiMode && isZoomMode) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        onTransform(zoom, pan)
                    }
                }
            }
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y
            )
    ) {
        PdfPageItem(pdfRenderer, pageIndex, isFullScreen, isEyeProtectionActive) { bitmap ->
            pageBitmap = bitmap
        }
        
        if (isDrawingMode) {
            DrawingCanvas(strokes, redos, selectedPenColor, Modifier.fillMaxSize())
        }

        if (isAiMode) {
            AiCanvas(Modifier.fillMaxSize()) { path ->
                pageBitmap?.let { onAiSelect(it, path) }
            }
        }
    }
}

@Composable
fun PdfPageItem(renderer: PdfRenderer?, pageIndex: Int, isFullScreen: Boolean, isEyeProtection: Boolean, onBitmapReady: (Bitmap) -> Unit = {}) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(pageIndex, renderer) {
        if (renderer != null) {
            withContext(Dispatchers.IO) {
                try {
                    val page = renderer.openPage(pageIndex)
                    val b = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap = b
                    onBitmapReady(b)
                    page.close()
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    val cardBg = if (isEyeProtection) Color(0xFFFDF7E7) else MaterialTheme.colorScheme.surfaceVariant
    
    Card(
        modifier = Modifier.fillMaxSize().padding(if (isFullScreen) 0.dp else 8.dp), 
        shape = if (isFullScreen) RoundedCornerShape(0.dp) else RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp), 
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            bitmap?.let { 
                Image(
                    bitmap = it.asImageBitmap(), 
                    contentDescription = "Page", 
                    modifier = Modifier.fillMaxSize(), 
                    contentScale = ContentScale.FillBounds,
                    colorFilter = if (isEyeProtection) ColorFilter.tint(Color(0xFFF4ECD8), BlendMode.Multiply) else null
                ) 
            } ?: CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Composable
fun ReaderToolIcon(icon: ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, description, tint = tint, modifier = Modifier.size(22.dp)) }
}
