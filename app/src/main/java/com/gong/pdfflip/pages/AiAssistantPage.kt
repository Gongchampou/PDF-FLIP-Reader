package com.gong.pdfflip.pages

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.gong.pdfflip.components.NeumorphicCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AI ASSISTANT PAGE - Logic & UI Components
 * This file handles the interaction with Google Gemini AI.
 */

@Composable
fun AiCanvas(modifier: Modifier, onSelectionComplete: (List<Offset>) -> Unit) {
    val currentPoints = remember { mutableStateListOf<Offset>() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    Canvas(modifier = modifier
        .onSizeChanged { canvasSize = it }
        .pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { offset -> currentPoints.clear(); currentPoints.add(offset) },
                onDrag = { change, _ -> currentPoints.add(change.position) },
                onDragEnd = {
                    if (canvasSize.width > 0 && canvasSize.height > 0) {
                        val normalized = currentPoints.map { Offset(it.x / canvasSize.width, it.y / canvasSize.height) }
                        onSelectionComplete(normalized)
                    }
                }
            )
        }
    ) {
        if (currentPoints.isNotEmpty()) {
            val path = Path().apply {
                moveTo(currentPoints.first().x, currentPoints.first().y)
                currentPoints.forEach { lineTo(it.x, it.y) }
                close() 
            }
            drawPath(
                path, 
                color = Color(0xFF6200EE), 
                style = Stroke(width = 6f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
            )
            drawPath(path, color = Color(0xFF6200EE).copy(alpha = 0.1f))
        }
    }
}

@Composable
fun AiExplanationOverlay(
    explanation: String?,
    isAnalyzing: Boolean,
    onDismiss: () -> Unit,
    currentTextColor: Color = Color.Black
) {
    if (isAnalyzing) {
        Dialog(onDismissRequest = {}) {
            NeumorphicCard {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(8.dp))
                Text("AI is analyzing...", color = currentTextColor)
            }
        }
    }

    explanation?.let { text ->
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            NeumorphicCard(
                modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.6f),
                backgroundColor = Color.White.copy(alpha = 0.8f)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("AI Explanation", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF6200EE))
                    Spacer(Modifier.height(12.dp))
                    Box(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        Text(text, color = Color.Black, fontSize = 16.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.End),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

/**
 * Main function to handle AI logic.
 */
fun performAiAnalysis(
    apiKey: String,
    pageBitmap: Bitmap,
    circlePath: List<Offset>,
    onStart: () -> Unit,
    onResult: (String) -> Unit,
    onError: (String) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    if (circlePath.isEmpty() || apiKey.isBlank()) return
    
    onStart()
    
    scope.launch(Dispatchers.IO) {
        try {
            // Find bounding box
            val minX = circlePath.minOf { it.x }.coerceIn(0f, 1f)
            val minY = circlePath.minOf { it.y }.coerceIn(0f, 1f)
            val maxX = circlePath.maxOf { it.x }.coerceIn(0f, 1f)
            val maxY = circlePath.maxOf { it.y }.coerceIn(0f, 1f)
            
            val left = (minX * pageBitmap.width).toInt()
            val top = (minY * pageBitmap.height).toInt()
            val width = ((maxX - minX) * pageBitmap.width).toInt().coerceAtLeast(1)
            val height = ((maxY - minY) * pageBitmap.height).toInt().coerceAtLeast(1)
            
            val croppedBitmap = Bitmap.createBitmap(pageBitmap, left, top, width, height)
            
            val model = GenerativeModel(modelName = "gemini-2.5-flash", apiKey = apiKey)
            val inputContent = content {
                image(croppedBitmap)
                text("Analyzing the content")
            }
            
            val response = model.generateContent(inputContent)
            
            withContext(Dispatchers.Main) {
                val responseText = response.text
                if (responseText != null) {
                    onResult(responseText)
                } else {
                    onError("AI returned an empty response. Please try again.")
                }
                croppedBitmap.recycle()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onError("Error: ${e.localizedMessage ?: "Unknown error occurred"}")
            }
        }
    }
}
