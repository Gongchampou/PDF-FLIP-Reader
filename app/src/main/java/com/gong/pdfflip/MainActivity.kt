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
            
            // Load theme on startup
            LaunchedEffect(Unit) {
                val themeFile = java.io.File(filesDir, "app_theme.txt")
                if (themeFile.exists()) {
                    themeIndex = themeFile.readText().toIntOrNull() ?: 2
                }
            }

            PDFFlipTheme(themeIndex = themeIndex) {
                MainAppNavigation(
                    currentTheme = themeIndex,
                    onThemeChange = { themeIndex = it }
                )
            }
        }
    }
}

@Composable
fun MainAppNavigation(currentTheme: Int, onThemeChange: (Int) -> Unit) {
    // Current screen state
    var currentScreen by remember { mutableStateOf(Screen.Library) }
    // Selected PDF URI and page
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var startPage by remember { mutableIntStateOf(0) }

    when (currentScreen) {
        Screen.Library -> {
            LibraryScreen(
                onBookClick = { uri, page ->
                    selectedUri = uri
                    startPage = page
                    currentScreen = Screen.Reader
                },
                onSettingsClick = {
                    currentScreen = Screen.Settings
                }
            )
        }
        Screen.Reader -> {
            selectedUri?.let { uri ->
                ReaderScreen(
                    uri = uri, 
                    initialPage = startPage,
                    onBack = { currentScreen = Screen.Library }
                )
            }
        }
        Screen.Settings -> {
            SettingsScreen(
                onBack = { currentScreen = Screen.Library },
                onThemeChanged = onThemeChange
            )
        }
    }
}
