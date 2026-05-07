package com.gong.pdfflip.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

import androidx.compose.ui.graphics.nativeCanvas

/**
 * A custom modifier that applies a neumorphic shadow effect.
 * Neumorphism uses two shadows: a light shadow on the top-left and a dark shadow on the bottom-right.
 */
fun Modifier.neumorphicShadow(
    cornerRadius: Dp = 16.dp,
    lightShadowColor: Color = Color.White.copy(alpha = 0.5f),
    darkShadowColor: Color = Color.Black.copy(alpha = 0.15f),
    shadowRadius: Dp = 8.dp,
    offsetX: Dp = 4.dp,
    offsetY: Dp = 4.dp
) = this.drawBehind {
    val shadowPaint = Paint().asFrameworkPaint()
    
    drawIntoCanvas { canvas ->
        // Draw Dark Shadow (Bottom-Right)
        shadowPaint.color = darkShadowColor.toArgb()
        shadowPaint.setShadowLayer(
            shadowRadius.toPx(),
            offsetX.toPx(),
            offsetY.toPx(),
            darkShadowColor.toArgb()
        )
        canvas.nativeCanvas.drawRoundRect(
            0f, 0f, size.width, size.height,
            cornerRadius.toPx(), cornerRadius.toPx(),
            shadowPaint
        )

        // Draw Light Shadow (Top-Left)
        shadowPaint.color = lightShadowColor.toArgb()
        shadowPaint.setShadowLayer(
            shadowRadius.toPx(),
            -offsetX.toPx(),
            -offsetY.toPx(),
            lightShadowColor.toArgb()
        )
        canvas.nativeCanvas.drawRoundRect(
            0f, 0f, size.width, size.height,
            cornerRadius.toPx(), cornerRadius.toPx(),
            shadowPaint
        )
    }
}

@Composable
fun NeumorphicCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.White.copy(alpha = 0.2f), // Semi-transparent as requested
    cornerRadius: Dp = 24.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .neumorphicShadow(cornerRadius = cornerRadius)
            .background(backgroundColor, RoundedCornerShape(cornerRadius))
            .padding(16.dp)
    ) {
        Column {
            content()
        }
    }
}
