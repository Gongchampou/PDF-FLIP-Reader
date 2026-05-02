package com.gong.pdfflip

/**
 * MAIN ACTIVITY - The "Brain" of the App
 * This file is the starting point of the application.
 * It handles:
 * 1. Global Navigation (switching between Library, Reader, and Settings).
 * 2. Theme Management (Loading and applying the global color scheme).
 * 3. App-wide state (like which PDF is currently open).
 */

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.gong.pdfflip.pages.LibraryScreen
import com.gong.pdfflip.pages.ReaderScreen
import com.gong.pdfflip.pages.SettingsScreen
import com.gong.pdfflip.ui.theme.PDFFlipTheme

// Application Navigation State
enum class Screen {
    Library,
    Reader,
    Settings
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var themeIndex by remember { mutableIntStateOf(2) }
            var pageModeIndex by remember { mutableIntStateOf(0) } // 0: Flip, 1: Scroll
            var flipStyleIndex by remember { mutableIntStateOf(0) } // 0: Normal, 1: Natural
            var scrollStyleIndex by remember { mutableIntStateOf(0) } // 0: Page, 1: Smooth
            var titleFontSizeIndex by remember { mutableIntStateOf(1) } // 0: Small, 1: Medium, 2: Large
            
            // Load settings on startup
            LaunchedEffect(Unit) {
                val themeFile = java.io.File(filesDir, "app_theme.txt")
                if (themeFile.exists()) themeIndex = themeFile.readText().toIntOrNull() ?: 2
                
                val prefsFile = java.io.File(filesDir, "reader_prefs.txt")
                if (prefsFile.exists()) {
                    val parts = prefsFile.readText().split("|")
                    if (parts.size >= 3) {
                        pageModeIndex = parts[0].toIntOrNull() ?: 0
                        flipStyleIndex = parts[1].toIntOrNull() ?: 0
                        scrollStyleIndex = parts[2].toIntOrNull() ?: 0
                    }
                    if (parts.size >= 4) {
                        titleFontSizeIndex = parts[3].toIntOrNull() ?: 1
                    }
                }
            }

            PDFFlipTheme(themeIndex = themeIndex) {
                MainAppNavigation(
                    currentTheme = themeIndex,
                    onThemeChange = { themeIndex = it },
                    pageModeIndex = pageModeIndex,
                    flipStyleIndex = flipStyleIndex,
                    scrollStyleIndex = scrollStyleIndex,
                    titleFontSizeIndex = titleFontSizeIndex,
                    onPrefsChange = { mode, flip, scroll, font ->
                        pageModeIndex = mode
                        flipStyleIndex = flip
                        scrollStyleIndex = scroll
                        titleFontSizeIndex = font
                        // Save to storage
                        val prefsFile = java.io.File(filesDir, "reader_prefs.txt")
                        prefsFile.writeText("$mode|$flip|$scroll|$font")
                    }
                )
            }
        }
    }
}

@Composable
fun MainAppNavigation(
    currentTheme: Int, 
    onThemeChange: (Int) -> Unit,
    pageModeIndex: Int,
    flipStyleIndex: Int,
    scrollStyleIndex: Int,
    titleFontSizeIndex: Int,
    onPrefsChange: (Int, Int, Int, Int) -> Unit
) {
    // Current screen state
    var currentScreen by remember { mutableStateOf(Screen.Library) }
    // Selected PDF URI and page
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedBookName by remember { mutableStateOf<String?>(null) }
    var startPage by remember { mutableIntStateOf(0) }
    var sourceUriStr by remember { mutableStateOf<String?>(null) }

    when (currentScreen) {
        Screen.Library -> {
            LibraryScreen(
                onBookClick = { book ->
                    selectedUri = book.uri
                    selectedBookName = book.name
                    startPage = book.currentPage
                    sourceUriStr = book.sourceUri
                    currentScreen = Screen.Reader
                },
                onSettingsClick = {
                    currentScreen = Screen.Settings
                },
                titleFontSizeIndex = titleFontSizeIndex
            )
        }
        Screen.Reader -> {
            selectedUri?.let { uri ->
                ReaderScreen(
                    uri = uri, 
                    fileName = selectedBookName ?: uri.lastPathSegment ?: "Unknown",
                    sourceUriStr = sourceUriStr,
                    initialPage = startPage,
                    initialPageModeIndex = pageModeIndex,
                    flipStyleIndex = flipStyleIndex,
                    scrollStyleIndex = scrollStyleIndex,
                    onBack = { currentScreen = Screen.Library },
                    onPageModeToggle = { newMode -> 
                        onPrefsChange(newMode, flipStyleIndex, scrollStyleIndex, titleFontSizeIndex)
                        // Save immediately
                        // (We'll handle save logic in a helper or passed lambda)
                    }
                )
            }
        }
        Screen.Settings -> {
            SettingsScreen(
                onBack = { currentScreen = Screen.Library },
                onThemeChanged = onThemeChange,
                pageModeIndex = pageModeIndex,
                flipStyleIndex = flipStyleIndex,
                scrollStyleIndex = scrollStyleIndex,
                titleFontSizeIndex = titleFontSizeIndex,
                onPrefsChanged = onPrefsChange
            )
        }
    }
}
