package com.gong.pdfflip

/**
 * MAIN ACTIVITY - The "Brain" of the App
 * This file is the starting point of the application.
 * It handles:
 * 1. Global Navigation (switching between Library, Reader, and Settings).
 * 2. Theme Management (Loading and applying the global color scheme).
 * 3. App-wide state (like which PDF is currently open).
 */

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.gong.pdfflip.components.StoragePermissionDialog
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
            val context = LocalContext.current
            var themeIndex by remember { mutableIntStateOf(2) }
            var pageModeIndex by remember { mutableIntStateOf(0) } // 0: Flip, 1: Scroll
            var flipStyleIndex by remember { mutableIntStateOf(0) } // 0: Normal, 1: Natural
            var scrollStyleIndex by remember { mutableIntStateOf(0) } // 0: Page, 1: Smooth
            var titleFontSizeIndex by remember { mutableIntStateOf(1) } // 0: Small, 1: Medium, 2: Large
            var showTimer by remember { mutableStateOf(false) }

            // Permission States
            var showPermissionDialog by remember { mutableStateOf(false) }
            val sharedPrefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
            val hasSeenDisclosure = remember { mutableStateOf(sharedPrefs.getBoolean("has_seen_disclosure", false)) }

            val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // On Android 13+, we use specific media permissions or just the file picker
                // For this app, we check for READ_MEDIA_IMAGES/VIDEO if needed, but for Documents
                // the system file picker is preferred. We'll check READ_EXTERNAL_STORAGE for safety on <13.
                Manifest.permission.READ_EXTERNAL_STORAGE 
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (isGranted) {
                    sharedPrefs.edit().putBoolean("has_seen_disclosure", true).apply()
                }
                showPermissionDialog = false
            }
            
            // Load settings on startup
            LaunchedEffect(Unit) {
                // Check if we need to show the disclosure
                val isPermissionGranted = ContextCompat.checkSelfPermission(context, storagePermission) == PackageManager.PERMISSION_GRANTED
                if (!isPermissionGranted && !hasSeenDisclosure.value) {
                    showPermissionDialog = true
                }

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
                    if (parts.size >= 5) {
                        showTimer = parts[4] == "1"
                    }
                }
            }

            PDFFlipTheme(themeIndex = themeIndex) {
                if (showPermissionDialog) {
                    StoragePermissionDialog(
                        onConfirm = {
                            permissionLauncher.launch(storagePermission)
                        },
                        onDismiss = {
                            showPermissionDialog = false
                            // Optional: Record that they saw it but dismissed
                            sharedPrefs.edit().putBoolean("has_seen_disclosure", true).apply()
                        }
                    )
                }

                MainAppNavigation(
                    currentTheme = themeIndex,
                    onThemeChange = { themeIndex = it },
                    pageModeIndex = pageModeIndex,
                    flipStyleIndex = flipStyleIndex,
                    scrollStyleIndex = scrollStyleIndex,
                    titleFontSizeIndex = titleFontSizeIndex,
                    showTimer = showTimer,
                    onPrefsChange = { mode, flip, scroll, font, timer ->
                        pageModeIndex = mode
                        flipStyleIndex = flip
                        scrollStyleIndex = scroll
                        titleFontSizeIndex = font
                        showTimer = timer
                        // Save to storage
                        val prefsFile = java.io.File(filesDir, "reader_prefs.txt")
                        prefsFile.writeText("$mode|$flip|$scroll|$font|${if (timer) "1" else "0"}")
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
    showTimer: Boolean,
    onPrefsChange: (Int, Int, Int, Int, Boolean) -> Unit
) {
    // Current screen state
    var currentScreen by remember { mutableStateOf(Screen.Library) }
    // Selected PDF URI and page
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedBookName by remember { mutableStateOf<String?>(null) }
    var startPage by remember { mutableIntStateOf(0) }
    var sourceUriStr by remember { mutableStateOf<String?>(null) }
    var currentPath by rememberSaveable { mutableStateOf("/") }

    // Intercept back button to return to Library from Reader or Settings
    BackHandler(enabled = currentScreen != Screen.Library) {
        currentScreen = Screen.Library
    }

    when (currentScreen) {
        Screen.Library -> {
            LibraryScreen(
                initialPath = currentPath,
                onPathChange = { currentPath = it },
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
                        onPrefsChange(newMode, flipStyleIndex, scrollStyleIndex, titleFontSizeIndex, showTimer)
                        // Save immediately
                        // (We'll handle save logic in a helper or passed lambda)
                    },
                    showTimer = showTimer
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
                showTimer = showTimer,
                onPrefsChanged = onPrefsChange
            )
        }
    }
}
