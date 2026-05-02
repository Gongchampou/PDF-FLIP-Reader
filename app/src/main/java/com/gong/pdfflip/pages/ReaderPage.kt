package com.gong.pdfflip.pages

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

// Data class to store our strokes for saving
data class DrawingStroke(val points: List<Offset>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(uri: Uri, onBack: () -> Unit) {
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
    var isSpeaking by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    
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
    
    // Drawing State: Page Index -> List of Strokes
    val pageStrokes = remember { mutableStateMapOf<Int, SnapshotStateList<DrawingStroke>>() }
    // Redo State: Page Index -> List of Redo Strokes
    val redoStrokes = remember { mutableStateMapOf<Int, SnapshotStateList<DrawingStroke>>() }

    // TTS Engine
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    LaunchedEffect(Unit) {
        PDFBoxResourceLoader.init(context) 
        val newTts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setLanguage(Locale.US)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { 
                        scope.launch(Dispatchers.Main) { isSpeaking = true }
                    }
                    override fun onDone(utteranceId: String?) { 
                        scope.launch(Dispatchers.Main) { isSpeaking = false }
                    }
                    override fun onError(utteranceId: String?) { 
                        scope.launch(Dispatchers.Main) { isSpeaking = false }
                    }
                })
            }
        }
        tts = newTts
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
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    val pagerState = rememberPagerState(pageCount = { pageCount })

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
                            Text(uri.lastPathSegment ?: "Reading", fontSize = 16.sp, color = currentTextColor, fontWeight = FontWeight.Bold)
                            if (pageCount > 0) Text("Page ${pagerState.currentPage + 1} of $pageCount", fontSize = 12.sp, color = currentTextColor.copy(alpha = 0.7f))
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
                        
                        ReaderToolIcon(Icons.Default.Fullscreen, "Full Screen", currentTextColor) { isFullScreen = true }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = currentBgColor)
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(visible = !isFullScreen, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                BottomAppBar(containerColor = currentBgColor) {
                    ReaderToolIcon(if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, "Bookmark", currentTextColor) { isBookmarked = !isBookmarked }
                    ReaderToolIcon(Icons.AutoMirrored.Filled.NoteAdd, "Notes", currentTextColor) { showNoteDialog = true }
                    
                    ReaderToolIcon(if (isSpeaking) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp, "Read Aloud", currentTextColor) {
                        if (isSpeaking) {
                            tts?.stop()
                            isSpeaking = false
                        } else {
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
                                                val naturalText = pageText
                                                    .replace(Regex("(?<=[a-z])-\\n(?=[a-z])"), "") 
                                                    .replace(Regex("\\n"), " ") 
                                                    .replace(Regex("\\s+"), " ") 
                                                    .trim()

                                                val params = Bundle()
                                                params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "ReaderTTS")
                                                tts?.setPitch(1.0f)
                                                tts?.setSpeechRate(0.95f)
                                                
                                                val result = tts?.speak(naturalText, TextToSpeech.QUEUE_FLUSH, params, "ReaderTTS")
                                                if (result == TextToSpeech.ERROR) {
                                                    Toast.makeText(context, "Voice engine error", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                Toast.makeText(context, "This page seems to be an image (No text found)", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }

                    // --- DRAW & SAVE TOOLS ---
                    ReaderToolIcon(if (isDrawingMode) Icons.Default.EditOff else Icons.Default.Edit, "Draw", currentTextColor) { isDrawingMode = !isDrawingMode }
                    
                    if (isDrawingMode) {
                        // Undo
                        ReaderToolIcon(
                            icon = Icons.AutoMirrored.Filled.Undo, 
                            description = "Undo", 
                            tint = if ((pageStrokes[pagerState.currentPage]?.size ?: 0) > 0) currentTextColor else currentTextColor.copy(alpha = 0.3f)
                        ) {
                            val currentList = pageStrokes[pagerState.currentPage]
                            if (!currentList.isNullOrEmpty()) {
                                val lastStroke = currentList.removeAt(currentList.size - 1)
                                redoStrokes.getOrPut(pagerState.currentPage) { mutableStateListOf() }.add(lastStroke)
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
                                pageStrokes.getOrPut(pagerState.currentPage) { mutableStateListOf() }.add(lastRedo)
                            }
                        }

                        // Save Permanent
                        ReaderToolIcon(Icons.Default.Check, "Save Permanent", Color.Green) {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    withContext(Dispatchers.Main) { Toast.makeText(context, "Saving...", Toast.LENGTH_SHORT).show() }
                                    pdfRenderer?.close()
                                    pdfRenderer = null
                                    val inputStream = context.contentResolver.openInputStream(uri)
                                    val document = PDDocument.load(inputStream)
                                    document.setAllSecurityToBeRemoved(true) 
                                    
                                    pageStrokes.forEach { (pageIdx, strokes) ->
                                        val page = document.getPage(pageIdx)
                                        val contentStream = PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)
                                        contentStream.setStrokingColor(255, 0, 0)
                                        contentStream.setLineWidth(3f)
                                        strokes.forEach { stroke ->
                                            if (stroke.points.isNotEmpty()) {
                                                val first = stroke.points.first()
                                                contentStream.moveTo(first.x / 2, page.mediaBox.height - (first.y / 2))
                                                stroke.points.forEach { pt ->
                                                    contentStream.lineTo(pt.x / 2, page.mediaBox.height - (pt.y / 2))
                                                }
                                                contentStream.stroke()
                                            }
                                        }
                                        contentStream.close()
                                    }

                                    val outputStream = context.contentResolver.openOutputStream(uri, "wt")
                                    if (outputStream != null) {
                                        document.save(outputStream)
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
                                            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                                            pfd?.let { pdfRenderer = PdfRenderer(it) }
                                        }
                                    }
                                }
                            }
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
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.padding(if (isFullScreen) PaddingValues(0.dp) else innerPadding).fillMaxSize(),
                    userScrollEnabled = !isDrawingMode && !isZoomMode && targetScale == 1f
                ) { pageIndex ->
                    val strokes = pageStrokes.getOrPut(pageIndex) { mutableStateListOf() }
                    val redos = redoStrokes.getOrPut(pageIndex) { mutableStateListOf() }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(isDrawingMode, isZoomMode, targetScale) {
                                if (!isDrawingMode && isZoomMode) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        targetScale = (targetScale * zoom).coerceIn(1f, 3.5f)
                                        if (targetScale > 1f) {
                                            targetOffset += pan
                                        } else {
                                            targetOffset = Offset.Zero
                                        }
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
                        PdfPageItem(pdfRenderer, pageIndex, isFullScreen, isEyeProtectionActive)
                        if (isDrawingMode) {
                            DrawingCanvas(strokes, redos, Modifier.fillMaxSize())
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = currentTextColor) }
            }

            if (isFullScreen) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopEnd) {
                    FilledIconButton(onClick = { isFullScreen = false }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f))) {
                        Icon(Icons.Default.FullscreenExit, "Exit", tint = Color.White)
                    }
                }
            }
        }
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
    
    if (showSearch) {
        var pageInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSearch = false },
            title = { Text("Enter Page No.") },
            text = { OutlinedTextField(value = pageInput, onValueChange = { if (it.all { c -> c.isDigit() }) pageInput = it }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = {
                val p = pageInput.toIntOrNull()
                if (p != null && p in 1..pageCount) { scope.launch { pagerState.scrollToPage(p-1) }; showSearch = false }
            }) { Text("Go") } }
        )
    }

    DisposableEffect(Unit) { onDispose { pdfRenderer?.close(); tts?.stop(); tts?.shutdown() } }
}

@Composable
fun DrawingCanvas(strokes: SnapshotStateList<DrawingStroke>, redoList: SnapshotStateList<DrawingStroke>, modifier: Modifier) {
    var currentPoints = remember { mutableStateListOf<Offset>() }

    Canvas(modifier = modifier.pointerInput(Unit) {
        detectDragGestures(
            onDragStart = { offset -> 
                currentPoints.add(offset)
                redoList.clear() // Clear redo stack when new drawing starts
            },
            onDrag = { change, _ -> currentPoints.add(change.position) },
            onDragEnd = { 
                strokes.add(DrawingStroke(currentPoints.toList()))
                currentPoints.clear()
            }
        )
    }) {
        // Draw existing strokes
        strokes.forEach { stroke ->
            val path = Path().apply {
                if (stroke.points.isNotEmpty()) {
                    moveTo(stroke.points.first().x, stroke.points.first().y)
                    stroke.points.forEach { lineTo(it.x, it.y) }
                }
            }
            drawPath(path, Color.Red, style = Stroke(5f, cap = StrokeCap.Round))
        }
        
        // Draw current stroke
        if (currentPoints.isNotEmpty()) {
            val path = Path().apply {
                moveTo(currentPoints.first().x, currentPoints.first().y)
                currentPoints.forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, Color.Red, style = Stroke(5f, cap = StrokeCap.Round))
        }
    }
}

@Composable
fun PdfPageItem(renderer: PdfRenderer?, pageIndex: Int, isFullScreen: Boolean, isEyeProtection: Boolean) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(pageIndex, renderer) {
        if (renderer != null) {
            withContext(Dispatchers.IO) {
                try {
                    val page = renderer.openPage(pageIndex)
                    val b = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap = b
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
                    contentScale = ContentScale.Fit,
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
