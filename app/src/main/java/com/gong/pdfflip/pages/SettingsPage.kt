package com.gong.pdfflip.pages

/**
 * SETTINGS PAGE - The "Control Center"
 * This page handles all app-wide configurations.
 * Key Features:
 * 1. Theme Management: Choose between Light, Dark, Read (Sepia), and Ocean schemes.
 * 2. Category Management: Rename or delete tags with professional confirmation dialogs.
 * 3. App Info: Detailed version and description of PDF Flip.
 */
/** Reminder Version Update Number when every time update
 * Code Line = 246 : val currentVersion = "1.0.1"
 * Code Line = 257 : val latestVersion = "1.0.1" // update version: Simulated result
 * even in build.gradle.kts (:app)
   - Line 18 = versionCode = 2 (increase the no. eg 3,4,5,6,7,.....)
   - Line 19 = versionName = "1.0.1"( increase the number )
 */

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gong.pdfflip.ui.theme.ReadModeBackground
import com.gong.pdfflip.ui.theme.ReadModeText
import com.gong.pdfflip.ui.theme.getTagColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit, 
    onThemeChanged: (Int) -> Unit,
    pageModeIndex: Int,
    flipStyleIndex: Int,
    scrollStyleIndex: Int,
    titleFontSizeIndex: Int,
    onPrefsChanged: (Int, Int, Int, Int) -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    // Preference saving helper
    fun savePrefs(mode: Int, flip: Int, scroll: Int, font: Int) {
        onPrefsChanged(mode, flip, scroll, font)
        scope.launch(Dispatchers.IO) {
            val prefsFile = File(context.filesDir, "reader_prefs.txt")
            prefsFile.writeText("$mode|$flip|$scroll|$font")
        }
    }
    
    // Theme Options
    val themeOptions = listOf(
        "Light" to (Color(0xFFFFFFFF) to Color(0xFF000000)),
        "Dark" to (Color(0xFF121212) to Color(0xFFEEEEEE)),
        "Read" to (ReadModeBackground to ReadModeText),
        "Ocean" to (Color(0xFFE0F7FA) to Color(0xFF006064))
    )
    
    // States for Theme
    var selectedThemeIndex by remember { mutableIntStateOf(2) } // Default to Sepia
    var isThemeDropdownExpanded by remember { mutableStateOf(false) }
    
    val currentBgColor = MaterialTheme.colorScheme.background
    val currentTextColor = MaterialTheme.colorScheme.onBackground

    // States for Tag Management
    val availableTags = remember { mutableStateListOf<String>() }
    
    // Deletion states
    var tagToDelete by remember { mutableStateOf<String?>(null) }
    var userInputTagName by remember { mutableStateOf("") }
    
    // Load data
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            // Load tags
            val tagsFile = File(context.filesDir, "available_tags.txt")
            if (tagsFile.exists()) {
                val loadedTags = tagsFile.readLines().filter { it.isNotBlank() }
                withContext(Dispatchers.Main) {
                    availableTags.addAll(loadedTags)
                }
            }
            
            // Load theme preference
            val themeFile = File(context.filesDir, "app_theme.txt")
            if (themeFile.exists()) {
                val savedIndex = themeFile.readText().toIntOrNull() ?: 2
                withContext(Dispatchers.Main) {
                    selectedThemeIndex = savedIndex.coerceIn(0, themeOptions.size - 1)
                }
            }
        }
    }

    // Function to save theme
    fun saveTheme(index: Int) {
        selectedThemeIndex = index
        onThemeChanged(index) // Notify global theme change
        scope.launch(Dispatchers.IO) {
            val themeFile = File(context.filesDir, "app_theme.txt")
            themeFile.writeText(index.toString())
        }
    }

    // Function to delete tag and update storage
    fun deleteTag(tag: String) {
        availableTags.remove(tag)
        scope.launch(Dispatchers.IO) {
            val tagsFile = File(context.filesDir, "available_tags.txt")
            tagsFile.writeText(availableTags.joinToString("\n"))
            
            // Also clear this tag from any books using it
            val metadataFile = File(context.filesDir, "library_tags.txt")
            if (metadataFile.exists()) {
                val lines = metadataFile.readLines().map { line ->
                    val parts = line.split("|")
                    if (parts.size >= 2 && parts[1] == tag) "${parts[0]}|" else line
                }
                metadataFile.writeText(lines.joinToString("\n"))
            }
            
            withContext(Dispatchers.Main) {
                tagToDelete = null
                userInputTagName = ""
                Toast.makeText(context, "Category deleted permanently", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- GITHUB STYLE DELETE DIALOG ---
    if (tagToDelete != null) {
        AlertDialog(
            onDismissRequest = { tagToDelete = null; userInputTagName = "" },
            title = { Text("Confirm", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Delete \"${tagToDelete}\" will remove it from all books tags.")

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(Color.LightGray.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = tagToDelete!!,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )
                        IconButton(
                            onClick = { 
                                clipboardManager.setText(AnnotatedString(tagToDelete!!))
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy, 
                                contentDescription = "Copy", 
                                modifier = Modifier.size(16.dp),
                                tint = Color.Gray
                            )
                        }
                    }
                    
                    OutlinedTextField(
                        value = userInputTagName,
                        onValueChange = { userInputTagName = it },
                        label = { Text("Type the tag name", fontSize = 12.sp) },
                        placeholder = { Text("Type \"${tagToDelete}\" to confirm", fontSize = 11.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 4.dp),
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            //delete funtion for tag
            confirmButton = {
                Button(
                    onClick = { deleteTag(tagToDelete!!) },
                    enabled = userInputTagName == tagToDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Delete Permanently", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { tagToDelete = null; userInputTagName = "" }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Update State
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    val currentVersion = "1.0.1" //version update no.

    fun checkForUpdates() {
        isCheckingUpdate = true
        updateMessage = null
        scope.launch(Dispatchers.IO) {
            try {
                // Example URL - Replace with your real raw version.txt link on GitHub
                // val latestVersion = URL("https://raw.githubusercontent.com/Gongchampou/PDF-FLIP-Reader/main/version.txt").readText().trim()
                
                delay(2000) // Simulate network delay for effect
                val latestVersion = "1.0.1" // update version: Simulated result

                withContext(Dispatchers.Main) {
                    isCheckingUpdate = false
                    if (latestVersion != currentVersion) {
                        updateMessage = "New version $latestVersion available!"
                        // In a real app, you'd trigger a download here
                        // uriHandler.openUri("https://github.com/Gongchampou/PDF-FLIP-Reader/releases/latest")
                    } else {
                        Toast.makeText(context, "You are on the latest version", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isCheckingUpdate = false
                    Toast.makeText(context, "Update check failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold, color = currentTextColor) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = currentTextColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = currentBgColor)
            )
        },
        containerColor = currentBgColor
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- SECTION 1: THEME SELECTION (Ultra Compact) ---
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Palette, contentDescription = null, tint = currentTextColor, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(text = "Theme", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = currentTextColor)
                    }
                    
                    // Ultra-Thin Theme Dropdown
                    ExposedDropdownMenuBox(
                        expanded = isThemeDropdownExpanded,
                        onExpandedChange = { isThemeDropdownExpanded = !isThemeDropdownExpanded }
                    ) {
                        Surface(
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                                .width(110.dp),
                            shape = RoundedCornerShape(4.dp),
                            color = currentTextColor.copy(alpha = 0.05f),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, currentTextColor.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = themeOptions[selectedThemeIndex].first,
                                    fontSize = 10.sp,
                                    color = currentTextColor,
                                    maxLines = 1
                                )
                                Icon(
                                    if (isThemeDropdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = currentTextColor.copy(alpha = 0.6f)
                                )
                            }
                        }

                        ExposedDropdownMenu(
                            expanded = isThemeDropdownExpanded,
                            onDismissRequest = { isThemeDropdownExpanded = false }
                        ) {
                            themeOptions.forEachIndexed { index, option ->
                                DropdownMenuItem(
                                    text = { Text(option.first, fontSize = 11.sp) },
                                    onClick = {
                                        saveTheme(index)
                                        isThemeDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // --- SECTION 1.5: READING EXPERIENCE (FLIP/SCROLL) ---
            item {
                Spacer(Modifier.height(4.dp))
                SettingsSectionHeader(title = "Read mode", icon = Icons.Default.MenuBook, tint = currentTextColor)
                
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 1.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp, top = 2.dp)) {
                        // Page Mode Toggle
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Default Mode", fontSize = 14.sp, color = currentTextColor)
                            Row {
                                FilterChip(
                                    selected = pageModeIndex == 0,
                                    onClick = { savePrefs(0, flipStyleIndex, scrollStyleIndex, titleFontSizeIndex) },
                                    label = { Text("Flip", fontSize = 12.sp) }
                                )
                                Spacer(Modifier.width(4.dp))
                                FilterChip(
                                    selected = pageModeIndex == 1,
                                    onClick = { savePrefs(1, flipStyleIndex, scrollStyleIndex, titleFontSizeIndex) },
                                    label = { Text("Scroll", fontSize = 12.sp) }
                                )
                            }
                        }
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = currentTextColor.copy(alpha = 0.1f))
                        
                        if (pageModeIndex == 0) {
                            // Flip Style
                            Text("Flip Transition", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = currentTextColor.copy(alpha = 0.6f))
                            Row(modifier = Modifier.padding(top = 4.dp)) {
                                FilterChip(
                                    selected = flipStyleIndex == 0,
                                    onClick = { savePrefs(pageModeIndex, 0, scrollStyleIndex, titleFontSizeIndex) },
                                    label = { Text("Normal", fontSize = 12.sp) }
                                )
                                Spacer(Modifier.width(8.dp))
                                FilterChip(
                                    selected = flipStyleIndex == 1,
                                    onClick = { savePrefs(pageModeIndex, 1, scrollStyleIndex, titleFontSizeIndex) },
                                    label = { Text("Natural Paper", fontSize = 12.sp) }
                                )
                            }
                        } else {
                            // Scroll Style
                            Text("Scroll Style", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = currentTextColor.copy(alpha = 0.6f))
                            Row(modifier = Modifier.padding(top = 4.dp)) {
                                FilterChip(
                                    selected = scrollStyleIndex == 0,
                                    onClick = { savePrefs(pageModeIndex, flipStyleIndex, 0, titleFontSizeIndex) },
                                    label = { Text("Page Snap", fontSize = 12.sp) }
                                )
                                Spacer(Modifier.width(8.dp))
                                FilterChip(
                                    selected = scrollStyleIndex == 1,
                                    onClick = { savePrefs(pageModeIndex, flipStyleIndex, 1, titleFontSizeIndex) },
                                    label = { Text("Smooth Scroll", fontSize = 12.sp) }
                                )
                            }
                        }
                    }
                }
            }

            // --- SECTION 1.6: LIBRARY TITLE (FONT SIZE) ---
            item {
                Spacer(Modifier.height(4.dp))
                SettingsSectionHeader(title = "Library Title", icon = Icons.Default.TextFields, tint = currentTextColor)
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 1.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp, top = 2.dp)) {
                        Row {
                            FilterChip(
                                selected = titleFontSizeIndex == 0,
                                onClick = { savePrefs(pageModeIndex, flipStyleIndex, scrollStyleIndex, 0) },
                                label = { Text("Small", fontSize = 12.sp) }
                            )
                            Spacer(Modifier.width(4.dp))
                            FilterChip(
                                selected = titleFontSizeIndex == 1,
                                onClick = { savePrefs(pageModeIndex, flipStyleIndex, scrollStyleIndex, 1) },
                                label = { Text("Medium", fontSize = 12.sp) }
                            )
                            Spacer(Modifier.width(4.dp))
                            FilterChip(
                                selected = titleFontSizeIndex == 2,
                                onClick = { savePrefs(pageModeIndex, flipStyleIndex, scrollStyleIndex, 2) },
                                label = { Text("Large", fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }

            // --- SECTION 2: TAG MANAGEMENT (DELETE TAGS) ---
            item {
                SettingsSectionHeader(title = "Manage (Tags)", icon = Icons.Default.Tag, tint = currentTextColor)
            }
            
            item {
                if (availableTags.isEmpty()) {
                    Text("No tag created yet.", modifier = Modifier.padding(horizontal = 16.dp), color = Color.Gray, fontSize = 14.sp)
                } else {
                    // Show tags in a tight 2-column grid
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        availableTags.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                pair.forEach { tag ->
                                    val tagColor = getTagColor(tag)
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = tagColor.copy(alpha = 0.1f)),
                                        border = androidx.compose.foundation.BorderStroke(0.5.dp, tagColor.copy(alpha = 0.3f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(start = 12.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = tag,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = tagColor,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            IconButton(
                                                onClick = { tagToDelete = tag },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Delete Tag",
                                                    tint = Color.Red.copy(alpha = 0.4f),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                if (pair.size == 1) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // --- SECTION 3: ABOUT ---
            item {
                Spacer(Modifier.height(4.dp))
                SettingsSectionHeader(title = "About", icon = Icons.Default.Info, tint = currentTextColor)
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 1.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        //version area
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Version $currentVersion", fontSize = 12.sp, color = Color.Gray)
                        }
                        
                        Spacer(Modifier.height(4.dp))

                        // Update Button
                        Button(
                            onClick = { checkForUpdates() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isCheckingUpdate,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (updateMessage != null) Color(0xFF4CAF50) else currentTextColor.copy(alpha = 0.1f),
                                contentColor = if (updateMessage != null) Color.White else currentTextColor
                            ),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            if (isCheckingUpdate) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = currentTextColor)
                                Spacer(Modifier.width(8.dp))
                                Text("Checking...", fontSize = 14.sp)
                            } else {
                                Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(updateMessage ?: "Check for Updates", fontSize = 14.sp)
                            }
                        }

                        if (updateMessage != null) {
                            Text(
                                text = "Click the button above to go to the download page.",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                            )
                        }
                    }
                }
            }

            // --- SECTION 4: LINK & UPDATES (SOCIAL LINKS) ---
            item {
                Text("Link Update:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = currentTextColor.copy(alpha = 0.6f))
                Spacer(Modifier.height(2.dp))

                // Priority Row: Play Store & Google Drive
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    //When your Apps is the Play-store you can uncomment.
                   /* item {
                        AssistChip(
                            onClick = { uriHandler.openUri("https://play.google.com/store/apps/details?id=com.gong.pdfflip") },
                            label = { Text("Play Store", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            colors = AssistChipDefaults.assistChipColors(labelColor = currentTextColor)
                        )
                    }*/
                    item {
                        AssistChip(
                            onClick = { uriHandler.openUri("https://drive.google.com/drive/folders/your_id") },
                            label = { Text("Google Drive", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            colors = AssistChipDefaults.assistChipColors(labelColor = currentTextColor)
                        )
                    }
                }
                

                // Secondary Row: GitHub & Website
                Text("My site:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = currentTextColor.copy(alpha = 0.6f))
                Spacer(Modifier.height(2.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        AssistChip(
                            onClick = { uriHandler.openUri("https://github.com/Gongchampou/PDF-FLIP-Reader") },
                            label = { Text("GitHub", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            colors = AssistChipDefaults.assistChipColors(labelColor = currentTextColor)
                        )
                    }
                    item {
                        AssistChip(
                            onClick = { uriHandler.openUri("https://gongchampou.pages.dev") },
                            label = { Text("Website", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            colors = AssistChipDefaults.assistChipColors(labelColor = currentTextColor)
                        )
                    }
                }
            }

            // --- FOOTER: DEVELOPER INFO ---
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "This is a professional document reader. Flipping the pdf to fill the book.",
                        fontSize = 11.sp,
                        color = currentTextColor.copy(alpha = 0.5f),
                        textAlign = TextAlign.Start
                    )
                    Text(
                        text = "Developer name Gongchampou (Jonah kamei)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = currentTextColor.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String, icon: ImageVector, tint: Color = MaterialTheme.colorScheme.onBackground) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = tint)
    }
}
