package com.gong.pdfflip.pages

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gong.pdfflip.ui.theme.ReadModeBackground
import com.gong.pdfflip.ui.theme.ReadModeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * SETTINGS PAGE
 * This page handles all app-wide configurations:
 * 1. Theme Management (Light, Dark, Sepia, Ocean)
 * 2. Tag Management (GitHub-style delete confirmation)
 * 3. About the App information
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onThemeChanged: (Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    
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
            title = { Text("confirm", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Deleting \"${tagToDelete}\" will remove it from all books.")

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

            // --- SECTION 2: TAG MANAGEMENT (DELETE TAGS) ---
            item {
                SettingsSectionHeader(title = "Manage Categories (Tags)", icon = Icons.Default.Tag, tint = currentTextColor)
            }
            
            item {
                if (availableTags.isEmpty()) {
                    Text("No categories created yet.", modifier = Modifier.padding(horizontal = 16.dp), color = Color.Gray, fontSize = 14.sp)
                } else {
                    // Show tags in a tight 2-column grid
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        availableTags.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                pair.forEach { tag ->
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.6f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(start = 12.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = tag,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = currentTextColor,
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
                SettingsSectionHeader(title = "About", icon = Icons.Default.Info, tint = currentTextColor)
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("PDF Flip Reader", fontWeight = FontWeight.Bold, color = currentTextColor)
                        Text("Version 1.0.0", fontSize = 12.sp, color = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        Text("A professional-grade document reader designed for high performance and eye protection.", fontSize = 14.sp, color = currentTextColor.copy(alpha = 0.8f))
                    }
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
