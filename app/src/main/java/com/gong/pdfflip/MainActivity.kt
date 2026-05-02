package com.gong.pdfflip

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
    // Selected PDF URI
    var selectedUri by remember { mutableStateOf<Uri?>(null) }

    when (currentScreen) {
        Screen.Library -> {
            LibraryScreen(
                onBookClick = { uri ->
                    selectedUri = uri
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
