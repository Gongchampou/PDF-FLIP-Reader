package com.gong.pdfflip.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Read Mode / Book Colors
val ReadModeBackground = Color(0xFFF4ECD8)
val ReadModeText = Color(0xFF5B4636)
val PaperWhite = Color(0xFFF9F7F2)

// Dynamic Tag Colors
fun getTagColor(tag: String?): Color {
    if (tag.isNullOrBlank()) return Color.Gray
    val colors = listOf(
        Color(0xFFE91E63), // Pink
        Color(0xFF9C27B0), // Purple
        Color(0xFF673AB7), // Deep Purple
        Color(0xFF3F51B5), // Indigo
        Color(0xFF2196F3), // Blue
        Color(0xFF03A9F4), // Light Blue
        Color(0xFF009688), // Teal
        Color(0xFF4CAF50), // Green
        Color(0xFFFF9800), // Orange
        Color(0xFF795548)  // Brown
    )
    val index = Math.abs(tag.hashCode()) % colors.size
    return colors[index]
}
