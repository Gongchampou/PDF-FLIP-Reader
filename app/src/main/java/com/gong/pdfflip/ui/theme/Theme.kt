package com.gong.pdfflip.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun PDFFlipTheme(
    themeIndex: Int = 2, // Default to Read-Mode
    content: @Composable () -> Unit
) {
    val themeOptions = listOf(
        // 0: Light Mode
        lightColorScheme(
            background = Color(0xFFFFFFFF),
            onBackground = Color(0xFF000000),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF000000),
            surfaceVariant = Color(0xFFF0F0F0),
            onSurfaceVariant = Color(0xFF333333)
        ),
        // 1: Dark Mode
        darkColorScheme(
            background = Color(0xFF121212),
            onBackground = Color(0xFFEEEEEE),
            surface = Color(0xFF1E1E1E),
            onSurface = Color(0xFFEEEEEE),
            surfaceVariant = Color(0xFF2C2C2C),
            onSurfaceVariant = Color(0xFFCCCCCC)
        ),
        // 2: Read-Mode (Sepia)
        lightColorScheme(
            background = Color(0xFFF4ECD8),
            onBackground = Color(0xFF5B4636),
            surface = Color(0xFFFDF7E7),
            onSurface = Color(0xFF5B4636),
            surfaceVariant = Color(0xFFE8DECA),
            onSurfaceVariant = Color(0xFF4A392C)
        ),
        // 3: Ocean Blue
        lightColorScheme(
            background = Color(0xFFE0F7FA),
            onBackground = Color(0xFF006064),
            surface = Color(0xFFF0FBFF),
            onSurface = Color(0xFF006064),
            surfaceVariant = Color(0xFFB2EBF2),
            onSurfaceVariant = Color(0xFF004D40)
        )
    )

    val colorScheme = themeOptions.getOrElse(themeIndex) { themeOptions[2] }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
