package com.gong.pdfflip.pages

/**
 * LIBRARY PAGE - The "Bookshelf"
 * This page allows users to manage their PDF collection.
 * Key Features:
 * 1. PDF Import: Add new PDF files from the device.
 * 2. Organization: Categorize books with colorful tags.
 * 3. Search & Filter: Quickly find books by name or category.
 * 4. Recent Activity: Shows progress and resumes reading from the last page.
 */

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gong.pdfflip.ui.theme.getTagColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

// Updated Book class to support categories/tags and original source location
data class Book(
    val name: String, 
    val uri: Uri, 
    val lastAccessed: Long = System.currentTimeMillis(),
    val tag: String? = null,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val sourceUri: String? = null // Store original file location as a string
)

enum class ImportStatus { Success, Duplicate, Error }
data class ImportResult(val name: String, val status: ImportStatus)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onBookClick: (Book) -> Unit, onSettingsClick: () -> Unit, titleFontSizeIndex: Int = 1) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    val clipboardManager = LocalClipboardManager.current
    
    // Font size scaling based on index
    val gridFontSize = when(titleFontSizeIndex) {
        0 -> 9.sp
        2 -> 13.sp
        else -> 11.sp
    }
    val listFontSize = when(titleFontSizeIndex) {
        0 -> 12.sp
        2 -> 16.sp
        else -> 14.sp
    }
    val gridLineHeight = when(titleFontSizeIndex) {
        0 -> 11.sp
        2 -> 15.sp
        else -> 13.sp
    }
    val listLineHeight = when(titleFontSizeIndex) {
        0 -> 14.sp
        2 -> 18.sp
        else -> 16.sp
    }

    var isGridView by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    // Tag and Category States
    val availableTags = remember { mutableStateListOf<String>() }
    var selectedFilterTag by remember { mutableStateOf<String?>(null) }
    var showCreateTagDialog by remember { mutableStateOf(false) }
    var showAssignTagDialog by remember { mutableStateOf<Book?>(null) }

    // Deletion states
    var bookToDelete by remember { mutableStateOf<Book?>(null) }
    var deleteConfirmCode by remember { mutableStateOf("") }
    var userInputCode by remember { mutableStateOf("") }

    // Import Summary states
    val importResults = remember { mutableStateListOf<ImportResult>() }
    var showImportSummary by remember { mutableStateOf(false) }

    val libraryBooks = remember { mutableStateListOf<Book>() }
    val recentBooks = remember { mutableStateListOf<Book>() }

    // Save/Load metadata (tags and source URIs)
    fun saveLibraryMetadata() {
        scope.launch(Dispatchers.IO) {
            val metadataFile = File(context.filesDir, "library_tags.txt")
            val tagsFile = File(context.filesDir, "available_tags.txt")
            
            metadataFile.writeText(libraryBooks.joinToString("\n") { "${it.name}|${it.tag ?: ""}|${it.sourceUri ?: ""}" })
            tagsFile.writeText(availableTags.joinToString("\n"))
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val loadedBooks = mutableListOf<Book>()
            
            // 1. Load from MediaStore (Documents/PDF Flip folder)
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Files.getContentUri("external")
            }
            
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.RELATIVE_PATH
            )
            
            val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.Files.FileColumns.MIME_TYPE} = ?"
            } else {
                "${MediaStore.Files.FileColumns.DATA} LIKE ? AND ${MediaStore.Files.FileColumns.MIME_TYPE} = ?"
            }
            
            val selectionArgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                arrayOf("%Documents/PDF Flip%", "application/pdf")
            } else {
                arrayOf("%/PDF Flip/%", "application/pdf")
            }

            context.contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val contentUri = Uri.withAppendedPath(collection, id.toString())
                    loadedBooks.add(Book(name, contentUri, sourceUri = contentUri.toString()))
                }
            }

            // Load tags and progress mapping
            val tagMap = mutableMapOf<String, String>()
            val progressMap = mutableMapOf<String, Pair<Int, Int>>()
            
            val metadataFile = File(context.filesDir, "library_tags.txt")
            if (metadataFile.exists()) {
                metadataFile.readLines().forEach { line ->
                    val parts = line.split("|")
                    if (parts.size >= 2) tagMap[parts[0]] = parts[1]
                }
            }

            val recentFile = File(context.filesDir, "recent_data.txt")
            if (recentFile.exists()) {
                recentFile.readLines().forEach { line ->
                    val parts = line.split("|")
                    if (parts.size >= 4) {
                        val name = parts[0]
                        val page = parts[2].toIntOrNull() ?: 0
                        val total = parts[3].toIntOrNull() ?: 0
                        progressMap[name] = page to total
                    }
                }
            }

            // Update loaded books with metadata
            val finalBooks = loadedBooks.map { book ->
                val progress = progressMap[book.name]
                book.copy(
                    tag = tagMap[book.name],
                    currentPage = progress?.first ?: 0,
                    totalPages = progress?.second ?: 0
                )
            }
            
            // Load available tags list
            val tagsFile = File(context.filesDir, "available_tags.txt")
            val loadedTags = if (tagsFile.exists()) tagsFile.readLines().filter { it.isNotBlank() } else emptyList()

            withContext(Dispatchers.Main) {
                libraryBooks.clear()
                libraryBooks.addAll(finalBooks)
                availableTags.clear()
                availableTags.addAll(loadedTags)
                
                // Refresh recent list based on time
                recentBooks.clear()
                recentBooks.addAll(finalBooks.sortedByDescending { it.lastAccessed }.take(10))
            }
        }
    }

    fun updateRecent(book: Book, page: Int = 0, total: Int = 0) {
        val currentTime = System.currentTimeMillis()
        val updatedBook = book.copy(lastAccessed = currentTime, currentPage = page, totalPages = if (total > 0) total else book.totalPages)
        recentBooks.removeAll { it.name == book.name }
        recentBooks.add(0, updatedBook)
        if (recentBooks.size > 10) recentBooks.removeRange(10, recentBooks.size)
        
        val dataToSave = recentBooks.toList()
        scope.launch(Dispatchers.IO) {
            val recentFile = File(context.filesDir, "recent_data.txt")
            recentFile.writeText(dataToSave.joinToString("\n") { "${it.name}|${it.lastAccessed}|${it.currentPage}|${it.totalPages}" })
        }
    }

    fun deleteBook(book: Book) {
        scope.launch(Dispatchers.IO) {
            try {
                // Delete from public storage (PDF Flip folder)
                context.contentResolver.delete(book.uri, null, null)
                
                withContext(Dispatchers.Main) {
                    libraryBooks.remove(book)
                    recentBooks.removeAll { it.name == book.name }
                    saveLibraryMetadata()
                    Toast.makeText(context, "${book.name.substringBeforeLast(".")} deleted from storage", Toast.LENGTH_SHORT).show()
                    bookToDelete = null
                    userInputCode = ""
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Delete failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Advanced Filtering Logic (Search + Tags)
    val filteredBooks = libraryBooks.filter { book ->
        val matchesSearch = book.name.contains(searchQuery, ignoreCase = true)
        val matchesTag = selectedFilterTag == null || book.tag == selectedFilterTag
        matchesSearch && matchesTag
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris: List<Uri> -> 
            if (uris.isNotEmpty()) {
                scope.launch {
                    importResults.clear()
                    var successCount = 0
                    var lastImportedBook: Book? = null

                    uris.forEach { sourceUri ->
                        try {
                            val fileName = getFileName(context, sourceUri) ?: "Document_${System.currentTimeMillis()}.pdf"
                            
                            // 1. FAST CHECK: Is it already on our UI list?
                            if (libraryBooks.any { it.name == fileName }) {
                                importResults.add(ImportResult(fileName, ImportStatus.Duplicate))
                                return@forEach
                            }

                            // 2. INTELLIGENT DISK CHECK: Does the file exist in the "PDF Flip" folder on storage?
                            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
                            } else {
                                MediaStore.Files.getContentUri("external")
                            }
                            
                            val projection = arrayOf(MediaStore.Files.FileColumns.DISPLAY_NAME)
                            val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ? AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?"
                            } else {
                                "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ? AND ${MediaStore.Files.FileColumns.DATA} LIKE ?"
                            }
                            val selectionArgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                arrayOf(fileName, "%Documents/PDF Flip%")
                            } else {
                                arrayOf(fileName, "%/PDF Flip/%")
                            }

                            val existsOnDisk = context.contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { 
                                it.count > 0 
                            } ?: false

                            if (existsOnDisk) {
                                importResults.add(ImportResult(fileName, ImportStatus.Duplicate))
                                return@forEach
                            }

                            // 3. SAFE IMPORT: Only create the file if it's truly new
                            val values = ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/PDF Flip")
                                }
                            }
                            
                            val destinationUri = context.contentResolver.insert(collection, values)
                            
                            destinationUri?.let { destUri ->
                                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                                    context.contentResolver.openOutputStream(destUri)?.use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                
                                val newBook = Book(
                                    name = fileName, 
                                    uri = destUri,
                                    sourceUri = destUri.toString()
                                )
                                
                                withContext(Dispatchers.Main) {
                                    libraryBooks.add(newBook)
                                    updateRecent(newBook, 0, 0)
                                }
                                
                                importResults.add(ImportResult(fileName, ImportStatus.Success))
                                lastImportedBook = newBook
                                successCount++
                            }
                        } catch (e: Exception) { 
                            e.printStackTrace()
                            importResults.add(ImportResult("Unknown File", ImportStatus.Error))
                        }
                    }

                    withContext(Dispatchers.Main) {
                        saveLibraryMetadata()
                        
                        // Logic:
                        // 1. If only 1 file picked and it's success -> Open it
                        // 2. Otherwise -> Show Summary Dialog
                        if (uris.size == 1 && successCount == 1 && lastImportedBook != null) {
                            onBookClick(lastImportedBook!!)
                        } else {
                            showImportSummary = true
                        }
                    }
                }
            }
        }
    )

    // DIALOG: Create New Tag
    if (showCreateTagDialog) {
        var newTagName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateTagDialog = false },
            title = { Text("Create New Category") },
            text = {
                OutlinedTextField(
                    value = newTagName,
                    onValueChange = { newTagName = it },
                    label = { Text("Category Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newTagName.isNotBlank() && !availableTags.contains(newTagName)) {
                        availableTags.add(newTagName)
                        saveLibraryMetadata()
                    }
                    showCreateTagDialog = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreateTagDialog = false }) { Text("Cancel") } }
        )
    }

    // DIALOG: Assign Tag to Book
    if (showAssignTagDialog != null) {
        AlertDialog(
            onDismissRequest = { showAssignTagDialog = null },
            title = { Text("Set Category for \"${showAssignTagDialog!!.name.substringBeforeLast(".")}\"") },
            text = {
                Column {
                    if (availableTags.isEmpty()) {
                        Text("No categories created yet. Click the + button in the library to create one.", color = Color.Gray, fontSize = 14.sp)
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                            items(availableTags) { tag ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val book = showAssignTagDialog!!
                                            val index = libraryBooks.indexOfFirst { it.name == book.name }
                                            if (index != -1) {
                                                libraryBooks[index] = book.copy(tag = tag)
                                                saveLibraryMetadata()
                                            }
                                            showAssignTagDialog = null
                                        }
                                        .padding(vertical = 12.dp)
                                ) {
                                    RadioButton(selected = showAssignTagDialog!!.tag == tag, onClick = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(tag)
                                }
                            }
                        }
                    }
                    TextButton(onClick = {
                        val book = showAssignTagDialog!!
                        val index = libraryBooks.indexOfFirst { it.name == book.name }
                        if (index != -1) {
                            libraryBooks[index] = book.copy(tag = null)
                            saveLibraryMetadata()
                        }
                        showAssignTagDialog = null
                    }) { Text("Clear Category", color = Color.Red) }
                }
            },
            confirmButton = { TextButton(onClick = { showAssignTagDialog = null }) { Text("Close") } }
        )
    }

    // GitHub-Style Confirm Delete Dialog
    if (bookToDelete != null) {
        AlertDialog(
            onDismissRequest = { bookToDelete = null; userInputCode = "" },
            title = { Text("Confirm Delete", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("\"${bookToDelete!!.name.substringBeforeLast(".")}\"? This action cannot be undone.")
                    Spacer(Modifier.height(16.dp))
                    Text("To confirm, please type the code below:", fontSize = 12.sp, color = Color.Gray)
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .background(Color.LightGray.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = deleteConfirmCode,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            letterSpacing = 4.sp
                        )
                        IconButton(onClick = { 
                            clipboardManager.setText(AnnotatedString(deleteConfirmCode))
                            Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Code", modifier = Modifier.size(20.dp))
                        }
                    }
                    
                    OutlinedTextField(
                        value = userInputCode,
                        onValueChange = { userInputCode = it },
                        placeholder = { Text("Paste or type code here") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { deleteBook(bookToDelete!!) },
                    enabled = userInputCode == deleteConfirmCode,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Delete Permanently", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { bookToDelete = null; userInputCode = "" }) {
                    Text("Cancel")
                }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.background,
                modifier = Modifier.width(300.dp)
            ) {
                Spacer(Modifier.height(48.dp))
                Text("Recent Activity", modifier = Modifier.padding(16.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
                
                if (recentBooks.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No recent activity yet", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(recentBooks) { book ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        updateRecent(book, book.currentPage, book.totalPages)
                                        scope.launch { drawerState.close() }
                                        onBookClick(book)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 2.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = book.name.substringBeforeLast("."),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    lineHeight = 16.sp
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val dateStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(book.lastAccessed))
                                    Text(
                                        text = dateStr,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                        lineHeight = 10.sp
                                    )

                                    if (book.totalPages > 0) {
                                        val percent = ((book.currentPage + 1).toFloat() / book.totalPages * 100).toInt()
                                        Text(
                                            text = "Page ${book.currentPage + 1}/${book.totalPages} ($percent%)",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                            lineHeight = 10.sp
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f),
                                thickness = 0.5.dp
                            )
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.History, contentDescription = "Recent", tint = MaterialTheme.colorScheme.onBackground)
                        }
                    },
                    title = {
                        // Custom Search Bar to prevent clipping and reduce font size
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            textStyle = LocalTextStyle.current.copy(
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 12.sp
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(38.dp)
                                        .background(
                                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f),
                                            CircleShape
                                        )
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                "Search...",
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                                fontSize = 12.sp
                                            )
                                        }
                                        innerTextField()
                                    }
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { searchQuery = "" },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Clear,
                                                contentDescription = "Clear",
                                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    },
                    actions = {
                        // Import Action
                        IconButton(onClick = { filePickerLauncher.launch(arrayOf("application/pdf")) }) {
                            Icon(Icons.Default.Add, contentDescription = "Import PDF", tint = MaterialTheme.colorScheme.onBackground)
                        }
                        // View Toggle Action
                        IconButton(onClick = { isGridView = !isGridView }) {
                            Icon(
                                if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                contentDescription = "Toggle View",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onBackground)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                // Category/Tag Selection Row (Scrollable tags with pinned Add button)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        contentPadding = PaddingValues(start = 16.dp, end = 8.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = selectedFilterTag == null,
                                onClick = { selectedFilterTag = null },
                                label = { Text("All") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedLabelColor = MaterialTheme.colorScheme.onBackground,
                                    labelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                            )
                        }
                        items(availableTags) { tag ->
                            val tagColor = getTagColor(tag)
                            FilterChip(
                                selected = selectedFilterTag == tag,
                                onClick = { selectedFilterTag = if (selectedFilterTag == tag) null else tag },
                                label = { Text(tag) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedLabelColor = Color.White,
                                    selectedContainerColor = tagColor,
                                    labelColor = tagColor.copy(alpha = 0.8f)
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selectedFilterTag == tag,
                                    borderColor = tagColor.copy(alpha = 0.5f)
                                )
                            )
                        }
                    }
                    // Pinned Add Button in the right corner
                    IconButton(
                        onClick = { showCreateTagDialog = true },
                        modifier = Modifier.padding(end = 8.dp).size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.AddCircle, 
                            contentDescription = "Create Category", 
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                        )
                    }
                }

                // Removed the old Import Row to keep the Top Bar clean

                if (libraryBooks.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(bottom = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Your Library is Empty", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        Text(
                            "Start your journey by importing a PDF file.",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                } else if (filteredBooks.isEmpty() && (searchQuery.isNotEmpty() || selectedFilterTag != null)) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No books found for this category or search", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    }
                } else {
                    if (isGridView) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4), // Increased from 3 to 4 for smaller cards
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredBooks) { book ->
                                BookGridItem(book, 
                                    fontSize = gridFontSize,
                                    lineHeight = gridLineHeight,
                                    onClick = { 
                                        updateRecent(book, book.currentPage, book.totalPages)
                                        onBookClick(book) 
                                    },
                                    onDelete = {
                                        bookToDelete = book
                                        deleteConfirmCode = (1000 + Random.nextInt(9000)).toString()
                                    },
                                    onTagClick = { showAssignTagDialog = book }
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredBooks) { book ->
                                BookListItem(book, 
                                    fontSize = listFontSize,
                                    lineHeight = listLineHeight,
                                    onClick = { 
                                        updateRecent(book, book.currentPage, book.totalPages)
                                        onBookClick(book)
                                    },
                                    onDelete = {
                                        bookToDelete = book
                                        deleteConfirmCode = (1000 + Random.nextInt(9000)).toString()
                                    },
                                    onTagClick = { showAssignTagDialog = book }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BookCover(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    val renderer = PdfRenderer(pfd)
                    if (renderer.pageCount > 0) {
                        val page = renderer.openPage(0)
                        // Create a small thumbnail for efficiency
                        val b = Bitmap.createBitmap(page.width / 4, page.height / 4, Bitmap.Config.ARGB_8888)
                        page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap = b
                        page.close()
                    }
                    renderer.close()
                    pfd.close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    Box(modifier = modifier.background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.05f))) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Cover",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else if (!isLoading) {
            Icon(
                Icons.Default.PictureAsPdf,
                contentDescription = null,
                modifier = Modifier.size(24.dp).align(Alignment.Center),
                tint = Color.Red.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
fun BookGridItem(book: Book, fontSize: androidx.compose.ui.unit.TextUnit, lineHeight: androidx.compose.ui.unit.TextUnit, onClick: () -> Unit, onDelete: () -> Unit, onTagClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.7f) // Adjusted for more vertical space for cover + title
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Cover Image (Top Part)
                BookCover(
                    uri = book.uri,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                )

                // Title Area (Bottom Part)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = book.name.substringBeforeLast("."),
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        lineHeight = lineHeight
                    )
                }

                // Progress Indicator (Pinned to Bottom)
                if (book.totalPages > 0) {
                    val progress = (book.currentPage + 1).toFloat() / book.totalPages
                    val percent = (progress * 100).toInt()
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 6.dp, end = 6.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                        )
                        Text(
                            text = "$percent%",
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Actions (Top Right/Left Overlays)
            Row(
                modifier = Modifier.fillMaxWidth().padding(2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onTagClick, 
                    modifier = Modifier
                        .size(20.dp)
                        .background(Color.Black.copy(alpha = 0.2f), CircleShape)
                ) {
                    val tagColor = getTagColor(book.tag)
                    Icon(
                        Icons.Default.Label,
                        contentDescription = "Tag", 
                        tint = if (book.tag != null) tagColor else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(12.dp)
                    )
                }
                IconButton(
                    onClick = onDelete, 
                    modifier = Modifier
                        .size(20.dp)
                        .background(Color.Black.copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

@Composable
fun BookListItem(book: Book, fontSize: androidx.compose.ui.unit.TextUnit, lineHeight: androidx.compose.ui.unit.TextUnit, onClick: () -> Unit, onDelete: () -> Unit, onTagClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp) // Slightly taller to fit thumbnail
            .clickable { onClick() },
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Small Thumbnail
            BookCover(
                uri = book.uri,
                modifier = Modifier
                    .size(45.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.name.substringBeforeLast("."),
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = lineHeight
                )
                
                // Progress Label
                if (book.totalPages > 0) {
                    val percent = ((book.currentPage + 1).toFloat() / book.totalPages * 100).toInt()
                    Text(
                        text = "Page ${book.currentPage + 1}/${book.totalPages} ($percent%)",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
            IconButton(onClick = onTagClick, modifier = Modifier.size(32.dp)) {
                val tagColor = getTagColor(book.tag)
                Icon(Icons.Default.Label, contentDescription = "Tag", tint = if (book.tag != null) tagColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
            }
        }
    }
}

fun getFileName(context: android.content.Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor.use {
            if (it != null && it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) result = it.getString(index)
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) result = result?.substring(cut + 1)
    }
    return result
}
