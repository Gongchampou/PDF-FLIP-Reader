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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
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

// Updated Book class to support categories/tags, colors and original source location
data class Book(
    val name: String, 
    val uri: Uri, 
    val lastAccessed: Long = System.currentTimeMillis(),
    val tag: String? = null,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val sourceUri: String? = null, // Store original file location as a string
    val folderPath: String = "/",
    val cardColor: String? = null
)

data class Folder(
    val name: String,
    val path: String, // Full path like "/Work/Invoices"
    val parentPath: String, // Parent folder path, e.g., "/Work"
    val color: String? = null
)

enum class ImportStatus { Success, Duplicate, Error }
data class ImportResult(val name: String, val status: ImportStatus)

val tagColorOptions = listOf(
    "#E91E63", // Pink
    "#9C27B0", // Purple
    "#673AB7", // Deep Purple
    "#3F51B5", // Indigo
    "#2196F3", // Blue
    "#03A9F4", // Light Blue
    "#009688", // Teal
    "#4CAF50", // Green
    "#FF9800", // Orange
    "#FF5722", // Deep Orange
    "#795548", // Brown
    "#607D8B"  // Blue Grey
)

// Helper to determine text contrast color based on background luminance
fun getContrastColor(backgroundColor: Color): Color {
    val luminance = (0.299 * backgroundColor.red + 0.587 * backgroundColor.green + 0.114 * backgroundColor.blue)
    return if (luminance > 0.5) Color.Black else Color.White
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    initialPath: String = "/",
    onPathChange: (String) -> Unit = {},
    onBookClick: (Book) -> Unit, 
    onSettingsClick: () -> Unit, 
    titleFontSizeIndex: Int = 1
) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    val libraryFolders = remember { mutableStateListOf<Folder>() }
    var currentPath by remember(initialPath) { mutableStateOf(initialPath) }

    fun updatePath(newPath: String) {
        currentPath = newPath
        onPathChange(newPath)
    }

    BackHandler(enabled = drawerState.isOpen || currentPath != "/") {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else if (currentPath != "/") {
            val parent = libraryFolders.find { it.path == currentPath }?.parentPath ?: "/"
            updatePath(parent)
        }
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
    var showAssignFolderTagDialog by remember { mutableStateOf<Folder?>(null) }

    // Deletion states
    var bookToDelete by remember { mutableStateOf<Book?>(null) }
    var folderToDelete by remember { mutableStateOf<Folder?>(null) }
    var deleteConfirmCode by remember { mutableStateOf("") }
    var userInputCode by remember { mutableStateOf("") }

    // Folder states
    var showCreateFolderDialog by remember { mutableStateOf(false) }

    // Import Summary states
    val importResults = remember { mutableStateListOf<ImportResult>() }
    var showImportSummary by remember { mutableStateOf(false) }

    // Clear Recent Data states
    var showClearRecentDialog by remember { mutableStateOf(false) }

    // Map of Tag Name to its Color object
    val tagToColorMap = remember(availableTags.toList()) {
        availableTags.associate { tagString ->
            val parts = tagString.split("|")
            val name = parts[0]
            val color = try {
                if (parts.size > 1) Color(android.graphics.Color.parseColor(parts[1]))
                else getTagColor(name)
            } catch (e: Exception) {
                getTagColor(name)
            }
            name to color
        }
    }

    val libraryBooks = remember { mutableStateListOf<Book>() }
    val recentBooks = remember { mutableStateListOf<Book>() }

    // Save/Load metadata (tags, folders and source URIs)
    fun saveLibraryMetadata() {
        scope.launch(Dispatchers.IO) {
            val metadataFile = File(context.filesDir, "library_tags.txt")
            val tagsFile = File(context.filesDir, "available_tags.txt")
            val foldersFile = File(context.filesDir, "library_folders.txt")
            
            metadataFile.writeText(libraryBooks.joinToString("\n") { 
                val pathId = if (it.folderPath == "/") it.name else "${it.folderPath}/${it.name}"
                "$pathId|${it.tag ?: ""}|${it.sourceUri ?: ""}|${it.folderPath}|${it.cardColor ?: ""}" 
            })
            tagsFile.writeText(availableTags.joinToString("\n"))
            foldersFile.writeText(libraryFolders.joinToString("\n") { "${it.name}|${it.path}|${it.parentPath}|${it.color ?: ""}" })
        }
    }

    // Logic to scan for books (moved to a function for re-use)
    fun scanLibrary() {
        scope.launch(Dispatchers.IO) {
            val loadedBooks = mutableListOf<Book>()
            
            // Ensure the base directory exists in Documents
            try {
                val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                val baseDir = File(documentsDir, "PDF Flip")
                if (!baseDir.exists()) {
                    baseDir.mkdirs()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            // 1. Load from MediaStore (Documents/PDF Flip folder)
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Files.getContentUri("external")
            }

            // Include pending files
            val queryUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.setIncludePending(collection)
            } else {
                collection
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

            context.contentResolver.query(queryUri, projection, selection, selectionArgs, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)
                
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val relPath = cursor.getString(pathColumn) ?: ""
                    
                    // Extract the folder part after "Documents/PDF Flip"
                    // Documents/PDF Flip/Folder/ -> /Folder
                    val cleanPath = when {
                        relPath.contains("Documents/PDF Flip/") -> {
                            val suffix = relPath.substringAfter("Documents/PDF Flip/")
                            if (suffix.isEmpty()) "/" else "/" + suffix.trim('/')
                        }
                        else -> "/"
                    }
                    
                    val contentUri = Uri.withAppendedPath(collection, id.toString())
                    loadedBooks.add(Book(name, contentUri, sourceUri = contentUri.toString(), folderPath = cleanPath))
                }
            }

            // Load tags and folder mapping
            val tagMap = mutableMapOf<String, String>()
            val colorMap = mutableMapOf<String, String>()
            val progressMap = mutableMapOf<String, Triple<Int, Int, Long>>()
            
            val metadataFile = File(context.filesDir, "library_tags.txt")
            if (metadataFile.exists()) {
                metadataFile.readLines().forEach { line ->
                    val parts = line.split("|")
                    // Format: UniqueId|Tag|SourceUri|FolderPath|Color
                    // UniqueId is pathId (folderPath + name)
                    if (parts.size >= 5) {
                        val pathId = parts[0]
                        tagMap[pathId] = parts[1]
                        colorMap[pathId] = parts[4]
                    } else if (parts.size == 2) { // Backward compatibility
                        tagMap[parts[0]] = parts[1]
                    }
                }
            }

            val recentFile = File(context.filesDir, "recent_data.txt")
            if (recentFile.exists()) {
                recentFile.readLines().forEach { line ->
                    val parts = line.split("|")
                    if (parts.size >= 4) {
                        val pathId = parts[0]
                        val time = parts[1].toLongOrNull() ?: 0L
                        val page = parts[2].toIntOrNull() ?: 0
                        val total = parts[3].toIntOrNull() ?: 0
                        progressMap[pathId] = Triple(page, total, time)
                    }
                }
            }

            // Update loaded books with metadata
            val finalBooks = loadedBooks.map { book ->
                val pathId = if (book.folderPath == "/") book.name else "${book.folderPath}/${book.name}"
                val progress = progressMap[pathId]
                
                book.copy(
                    tag = tagMap[pathId] ?: tagMap[book.name], // Try pathId first, then name for old tags
                    cardColor = colorMap[pathId] ?: colorMap[book.name],
                    currentPage = progress?.first ?: 0,
                    totalPages = progress?.second ?: 0,
                    lastAccessed = progress?.third ?: 0L
                )
            }
            
            // Load available tags list
            val tagsFile = File(context.filesDir, "available_tags.txt")
            val loadedTags = if (tagsFile.exists()) tagsFile.readLines().filter { it.isNotBlank() } else emptyList()

            // Load folders
            val foldersFile = File(context.filesDir, "library_folders.txt")
            val loadedFolders = mutableListOf<Folder>()
            if (foldersFile.exists()) {
                foldersFile.readLines().forEach { line ->
                    val parts = line.split("|")
                    if (parts.size >= 3) {
                        loadedFolders.add(Folder(parts[0], parts[1], parts[2], if (parts.size >= 4) parts[3] else null))
                    }
                }
            }

            withContext(Dispatchers.Main) {
                libraryBooks.clear()
                libraryBooks.addAll(finalBooks)
                availableTags.clear()
                availableTags.addAll(loadedTags)
                libraryFolders.clear()
                libraryFolders.addAll(loadedFolders)
                
                // Refresh recent list based on time (Only show books actually opened)
                recentBooks.clear()
                recentBooks.addAll(
                    finalBooks
                        .filter { it.lastAccessed > 0 }
                        .sortedByDescending { it.lastAccessed }
                        .take(40)
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        scanLibrary()
    }

    // Auto-refresh when returning to app (important for detecting newly printed PDFs)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                scanLibrary()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun updateRecent(book: Book, page: Int = 0, total: Int = 0) {
        val currentTime = System.currentTimeMillis()
        val updatedBook = book.copy(lastAccessed = currentTime, currentPage = page, totalPages = if (total > 0) total else book.totalPages)
        
        // Use folderPath + fileName for unique identification
        val pathId = if (book.folderPath == "/") book.name else "${book.folderPath}/${book.name}"
        
        // Remove existing entry for this unique book path to avoid duplicates
        recentBooks.removeAll { 
            val otherPathId = if (it.folderPath == "/") it.name else "${it.folderPath}/${it.name}"
            otherPathId == pathId 
        }
        recentBooks.add(0, updatedBook)
        if (recentBooks.size > 40) recentBooks.removeRange(40, recentBooks.size)
        
        scope.launch(Dispatchers.IO) {
            val recentFile = File(context.filesDir, "recent_data.txt")
            val lines = if (recentFile.exists()) recentFile.readLines().toMutableList() else mutableListOf()
            
            // Remove old entry from file too
            val existingIndex = lines.indexOfFirst { it.startsWith("$pathId|") }
            if (existingIndex != -1) {
                lines.removeAt(existingIndex)
            }
            
            // Add fresh entry at top
            lines.add(0, "$pathId|$currentTime|$page|${if (total > 0) total else book.totalPages}")
            
            // Save up to 1000 items in the data file for long-term memory
            recentFile.writeText(lines.take(1000).joinToString("\n"))
        }
    }

    fun deleteBook(book: Book) {
        scope.launch(Dispatchers.IO) {
            try {
                // Delete from public storage (PDF Flip folder)
                context.contentResolver.delete(book.uri, null, null)
                
                withContext(Dispatchers.Main) {
                    val pathId = if (book.folderPath == "/") book.name else "${book.folderPath}/${book.name}"
                    libraryBooks.remove(book)
                    recentBooks.removeAll { 
                        val otherPathId = if (it.folderPath == "/") it.name else "${it.folderPath}/${it.name}"
                        otherPathId == pathId 
                    }
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

    fun deleteFolder(folder: Folder) {
        scope.launch(Dispatchers.IO) {
            try {
                val targetPath = folder.path
                
                // 1. Delete all books in this folder and subfolders from physical storage
                val booksInFolder = libraryBooks.filter { it.folderPath.startsWith(targetPath) }
                booksInFolder.forEach { book ->
                    try {
                        context.contentResolver.delete(book.uri, null, null)
                    } catch (e: Exception) { e.printStackTrace() }
                }

                // 2. Delete the physical directory from filesystem
                val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                val baseDir = File(documentsDir, "PDF Flip")
                val folderDir = File(baseDir, folder.path.substring(1)) // Remove leading /
                if (folderDir.exists()) {
                    folderDir.deleteRecursively()
                }

                withContext(Dispatchers.Main) {
                    // 3. Remove from UI lists
                    libraryBooks.removeAll { it.folderPath.startsWith(targetPath) }
                    libraryFolders.removeAll { it.path.startsWith(targetPath) }
                    
                    // Also cleanup recent list
                    recentBooks.removeAll { it.folderPath.startsWith(targetPath) }
                    
                    saveLibraryMetadata()
                    Toast.makeText(context, "Folder \"${folder.name}\" and its contents deleted", Toast.LENGTH_SHORT).show()
                    folderToDelete = null
                    userInputCode = ""
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Folder delete failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun renameBook(book: Book, newName: String) {
        val sanitizedNewName = if (newName.lowercase().endsWith(".pdf")) newName else "$newName.pdf"
        
        scope.launch(Dispatchers.IO) {
            try {
                // 1. Rename on disk via MediaStore
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, sanitizedNewName)
                }
                context.contentResolver.update(book.uri, values, null, null)

                withContext(Dispatchers.Main) {
                    val oldPathId = if (book.folderPath == "/") book.name else "${book.folderPath}/${book.name}"
                    val newPathId = if (book.folderPath == "/") sanitizedNewName else "${book.folderPath}/$sanitizedNewName"
                    
                    // 2. Update libraryBooks
                    val bookIndex = libraryBooks.indexOfFirst { it.name == book.name && it.folderPath == book.folderPath }
                    if (bookIndex != -1) {
                        libraryBooks[bookIndex] = libraryBooks[bookIndex].copy(name = sanitizedNewName)
                    }

                    // 3. Update recentBooks
                    val recentIndex = recentBooks.indexOfFirst { 
                        val pId = if (it.folderPath == "/") it.name else "${it.folderPath}/${it.name}"
                        pId == oldPathId 
                    }
                    if (recentIndex != -1) {
                        recentBooks[recentIndex] = recentBooks[recentIndex].copy(name = sanitizedNewName)
                    }

                    // 4. Migrate metadata file logic
                    scope.launch(Dispatchers.IO) {
                        // Update recent_data.txt
                        val recentFile = File(context.filesDir, "recent_data.txt")
                        if (recentFile.exists()) {
                            val lines = recentFile.readLines().map { line ->
                                if (line.startsWith("$oldPathId|")) {
                                    val parts = line.split("|").toMutableList()
                                    parts[0] = newPathId
                                    parts.joinToString("|")
                                } else line
                            }
                            recentFile.writeText(lines.joinToString("\n"))
                        }
                        
                        // library_tags.txt update is handled by saveLibraryMetadata() call
                        withContext(Dispatchers.Main) {
                            saveLibraryMetadata()
                            Toast.makeText(context, "Renamed to $sanitizedNewName", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Rename failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun renameFolder(folder: Folder, newName: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val oldPath = folder.path
                val newPath = if (folder.parentPath == "/") "/$newName" else "${folder.parentPath}/$newName"
                
                // 1. Rename physical folder
                val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                val baseDir = File(documentsDir, "PDF Flip")
                val oldFolderDir = File(baseDir, oldPath.substring(1))
                val newFolderDir = File(baseDir, newPath.substring(1))
                
                if (oldFolderDir.exists()) {
                    val success = oldFolderDir.renameTo(newFolderDir)
                    if (!success) throw Exception("Physical rename failed")
                }

                withContext(Dispatchers.Main) {
                    // 2. Update libraryFolders
                    val folderIndex = libraryFolders.indexOfFirst { it.path == oldPath }
                    if (folderIndex != -1) {
                        libraryFolders[folderIndex] = libraryFolders[folderIndex].copy(name = newName, path = newPath)
                    }
                    
                    // 3. Update sub-folders
                    val allSubFolders = libraryFolders.filter { it.path.startsWith("$oldPath/") }
                    allSubFolders.forEach { sub ->
                        val idx = libraryFolders.indexOfFirst { it.path == sub.path }
                        if (idx != -1) {
                            val suffix = sub.path.removePrefix(oldPath)
                            val parentSuffix = sub.parentPath.removePrefix(oldPath)
                            libraryFolders[idx] = libraryFolders[idx].copy(
                                path = newPath + suffix,
                                parentPath = newPath + parentSuffix
                            )
                        }
                    }

                    // 4. Update books
                    val booksToMigrate = libraryBooks.filter { it.folderPath.startsWith(oldPath) }
                    booksToMigrate.forEach { book ->
                        val idx = libraryBooks.indexOfFirst { it.uri == book.uri }
                        if (idx != -1) {
                            val suffix = book.folderPath.removePrefix(oldPath)
                            libraryBooks[idx] = libraryBooks[idx].copy(folderPath = newPath + suffix)
                        }
                    }
                    
                    // 5. Update recentBooks list in memory
                    recentBooks.forEachIndexed { i, book ->
                        if (book.folderPath.startsWith(oldPath)) {
                            val suffix = book.folderPath.removePrefix(oldPath)
                            recentBooks[i] = book.copy(folderPath = newPath + suffix)
                        }
                    }

                    // 6. Update Recent Activity and Metadata Files
                    scope.launch(Dispatchers.IO) {
                        // Update recent_data.txt
                        val recentFile = File(context.filesDir, "recent_data.txt")
                        if (recentFile.exists()) {
                            val lines = recentFile.readLines().map { line ->
                                if (line.startsWith("$oldPath/")) {
                                    line.replaceFirst(oldPath, newPath)
                                } else line
                            }
                            recentFile.writeText(lines.joinToString("\n"))
                        }
                        
                        withContext(Dispatchers.Main) {
                            saveLibraryMetadata()
                            Toast.makeText(context, "Folder renamed to $newName", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Folder rename failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Advanced Filtering Logic (Search + Tags)
    val filteredBooks = libraryBooks.filter { book ->
        val matchesSearch = book.name.contains(searchQuery, ignoreCase = true)
        val matchesTag = selectedFilterTag == null || book.tag == selectedFilterTag
        val matchesPath = if (searchQuery.isNotEmpty()) true else book.folderPath == currentPath
        matchesSearch && matchesTag && matchesPath
    }
    
    val filteredFolders = libraryFolders.filter { folder ->
        val matchesSearch = folder.name.contains(searchQuery, ignoreCase = true)
        val matchesPath = if (searchQuery.isNotEmpty()) true else folder.parentPath == currentPath
        matchesSearch && matchesPath && selectedFilterTag == null
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
                            
                            // 1. FAST CHECK: Is it already on our UI list? (Case-Insensitive)
                            if (libraryBooks.any { it.name.equals(fileName, ignoreCase = true) }) {
                                importResults.add(ImportResult(fileName, ImportStatus.Duplicate))
                                return@forEach
                            }

                            // 2. IRONCLAD DISK CHECK
                            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
                            } else {
                                MediaStore.Files.getContentUri("external")
                            }
                            
                            val queryUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                MediaStore.setIncludePending(collection)
                            } else {
                                collection
                            }

                            val projection = arrayOf(MediaStore.Files.FileColumns.DISPLAY_NAME)
                            val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                "LOWER(${MediaStore.Files.FileColumns.DISPLAY_NAME}) = LOWER(?) AND LOWER(${MediaStore.Files.FileColumns.RELATIVE_PATH}) LIKE LOWER(?)"
                            } else {
                                "LOWER(${MediaStore.Files.FileColumns.DATA}) LIKE LOWER(?)"
                            }
                            
                            val folderSearchPath = if (currentPath == "/") "Documents/PDF Flip/" else "Documents/PDF Flip${currentPath}/"
                            
                            val selectionArgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                arrayOf(fileName, "%${folderSearchPath}%")
                            } else {
                                val legacyPath = folderSearchPath.replace("Documents/", "")
                                arrayOf("%/$legacyPath$fileName")
                            }

                            val existsOnDisk = context.contentResolver.query(queryUri, projection, selection, selectionArgs, null)?.use { 
                                it.count > 0 
                            } ?: false

                            if (existsOnDisk) {
                                importResults.add(ImportResult(fileName, ImportStatus.Duplicate))
                                return@forEach
                            }

                            // 3. SAFE IMPORT
                            val relativeStoragePath = if (currentPath == "/") {
                                "Documents/PDF Flip"
                            } else {
                                // Ensure the path starts with Documents/PDF Flip and currentPath (which starts with /)
                                "Documents/PDF Flip${currentPath}"
                            }

                            val values = ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativeStoragePath)
                                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                                }
                            }
                            
                            val destinationUri = context.contentResolver.insert(collection, values)
                            
                            destinationUri?.let { destUri ->
                                val actualName = getFileName(context, destUri) ?: fileName
                                if (!actualName.equals(fileName, ignoreCase = true)) {
                                    context.contentResolver.delete(destUri, null, null)
                                    importResults.add(ImportResult(fileName, ImportStatus.Duplicate))
                                    return@forEach
                                }

                                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                                    context.contentResolver.openOutputStream(destUri)?.use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    val updateValues = ContentValues().apply {
                                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                                    }
                                    context.contentResolver.update(destUri, updateValues, null, null)
                                }

                                val newBook = Book(
                                    name = fileName, 
                                    uri = destUri,
                                    sourceUri = destUri.toString(),
                                    folderPath = currentPath
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

    // DIALOG: Create New Folder
    if (showCreateFolderDialog) {
        var newFolderName by remember { mutableStateOf("") }
        var selectedFolderColor by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Create New Folder") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text("Folder Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Select Folder Color:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(tagColorOptions) { colorHex ->
                            val isSelected = selectedFolderColor == colorHex
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(colorHex)))
                                    .border(2.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                                    .clickable { selectedFolderColor = colorHex }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newFolderName.isNotBlank()) {
                        val path = if (currentPath == "/") "/$newFolderName" else "$currentPath/$newFolderName"
                        if (libraryFolders.none { it.path == path }) {
                            try {
                                val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                                val baseDir = File(documentsDir, "PDF Flip")
                                val targetDir = if (currentPath == "/") {
                                    File(baseDir, newFolderName)
                                } else {
                                    val relativePath = currentPath.substring(1)
                                    File(baseDir, "$relativePath/$newFolderName")
                                }
                                if (!targetDir.exists()) {
                                    targetDir.mkdirs()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }

                            libraryFolders.add(Folder(newFolderName, path, currentPath, selectedFolderColor))
                            saveLibraryMetadata()
                        }
                    }
                    showCreateFolderDialog = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancel") } }
        )
    }

    // DIALOG: Create New Tag
    if (showCreateTagDialog) {
        var newTagName by remember { mutableStateOf("") }
        var selectedColorHex by remember { mutableStateOf(tagColorOptions[0]) }
        AlertDialog(
            onDismissRequest = { showCreateTagDialog = false },
            title = { Text("Create New Category") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newTagName,
                        onValueChange = { newTagName = it },
                        label = { Text("Category Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Select Color:", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(tagColorOptions) { hex ->
                            val color = Color(android.graphics.Color.parseColor(hex))
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable { selectedColorHex = hex }
                                    .border(
                                        width = if (selectedColorHex == hex) 3.dp else 0.dp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newTagName.isNotBlank()) {
                        val exists = availableTags.any { it.split("|")[0].equals(newTagName, ignoreCase = true) }
                        if (!exists) {
                            availableTags.add("$newTagName|$selectedColorHex")
                            saveLibraryMetadata()
                        }
                    }
                    showCreateTagDialog = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreateTagDialog = false }) { Text("Cancel") } }
        )
    }

    // DIALOG: Customize Book (Tag & Color)
    if (showAssignTagDialog != null) {
        var editName by remember(showAssignTagDialog) { mutableStateOf(showAssignTagDialog!!.name.substringBeforeLast(".")) }
        var selectedTag by remember(showAssignTagDialog) { mutableStateOf(showAssignTagDialog!!.tag) }
        var selectedCardColor by remember(showAssignTagDialog) { mutableStateOf(showAssignTagDialog!!.cardColor) }

        AlertDialog(
            onDismissRequest = { showAssignTagDialog = null },
            title = { Text("Customize Book") },
            text = {
                Column {
                    Text("Rename File:", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    BasicTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (editName.isEmpty()) Text("Enter name...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 14.sp)
                                innerTextField()
                            }
                        }
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Card Color:", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            val defaultColor = MaterialTheme.colorScheme.surfaceVariant
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(defaultColor)
                                    .clickable { selectedCardColor = null }
                                    .border(
                                        width = if (selectedCardColor == null) 2.dp else 0.dp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectedCardColor == null) Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                            }
                        }
                        items(tagColorOptions) { hex ->
                            val color = Color(android.graphics.Color.parseColor(hex))
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable { selectedCardColor = hex }
                                    .border(
                                        width = if (selectedCardColor == hex) 2.dp else 0.dp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectedCardColor == hex) Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp), tint = Color.White)
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text("Category:", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))

                    if (availableTags.isEmpty()) {
                        Text("No categories created yet.", color = Color.Gray, fontSize = 12.sp)
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 180.dp)) {
                            items(availableTags) { tagString ->
                                val parts = tagString.split("|")
                                val tagName = parts[0]
                                val tagColor = tagToColorMap[tagName] ?: getTagColor(tagName)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedTag = tagName }
                                        .padding(vertical = 8.dp)
                                ) {
                                    RadioButton(selected = selectedTag == tagName, onClick = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(tagName, color = tagColor, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                    TextButton(onClick = { selectedTag = null }) { Text("Clear Category", color = Color.Red) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val book = showAssignTagDialog!!
                    
                    // 1. Handle Rename if needed
                    if (editName.isNotBlank() && editName != book.name.substringBeforeLast(".")) {
                        renameBook(book, editName)
                    }
                    
                    // 2. Handle Color and Tag updates
                    val updatedBook = book.copy(tag = selectedTag, cardColor = selectedCardColor)
                    val index = libraryBooks.indexOfFirst { it.name == book.name && it.folderPath == book.folderPath }
                    if (index != -1) {
                        libraryBooks[index] = updatedBook
                        saveLibraryMetadata()
                    }
                    
                    showAssignTagDialog = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAssignTagDialog = null }) { Text("Cancel") } }
        )
    }

    // DIALOG: Customize Folder
    if (showAssignFolderTagDialog != null) {
        var editName by remember(showAssignFolderTagDialog) { mutableStateOf(showAssignFolderTagDialog!!.name) }
        var selectedCardColor by remember(showAssignFolderTagDialog) { mutableStateOf(showAssignFolderTagDialog!!.color) }

        AlertDialog(
            onDismissRequest = { showAssignFolderTagDialog = null },
            title = { Text("Customize Folder") },
            text = {
                Column {
                    Text("Rename Folder:", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    BasicTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (editName.isEmpty()) Text("Enter name...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 14.sp)
                                innerTextField()
                            }
                        }
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Folder Color:", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            val defaultColor = MaterialTheme.colorScheme.surfaceVariant
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(defaultColor)
                                    .clickable { selectedCardColor = null }
                                    .border(
                                        width = if (selectedCardColor == null) 2.dp else 0.dp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectedCardColor == null) Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                            }
                        }
                        items(tagColorOptions) { hex ->
                            val color = Color(android.graphics.Color.parseColor(hex))
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable { selectedCardColor = hex }
                                    .border(
                                        width = if (selectedCardColor == hex) 2.dp else 0.dp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectedCardColor == hex) Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp), tint = Color.White)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val folder = showAssignFolderTagDialog!!
                    
                    // 1. Handle Rename if needed
                    if (editName.isNotBlank() && editName != folder.name) {
                        renameFolder(folder, editName)
                    }
                    
                    // 2. Handle Color updates
                    val updatedFolder = folder.copy(color = selectedCardColor)
                    val index = libraryFolders.indexOfFirst { it.path == folder.path }
                    if (index != -1) {
                        libraryFolders[index] = updatedFolder
                        saveLibraryMetadata()
                    }
                    
                    showAssignFolderTagDialog = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAssignFolderTagDialog = null }) { Text("Cancel") } }
        )
    }

    // DIALOG: Confirm Delete
    if (bookToDelete != null || folderToDelete != null) {
        val itemName = bookToDelete?.name?.substringBeforeLast(".") ?: folderToDelete?.name ?: ""
        AlertDialog(
            onDismissRequest = { bookToDelete = null; folderToDelete = null; userInputCode = "" },
            title = { Text("Confirm Delete", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("\"$itemName\"? This action cannot be undone.")
                    if (folderToDelete != null) {
                        Text("This will also delete everything inside the folder.", color = Color.Red, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("To confirm, please type the code below:", fontSize = 12.sp, color = Color.Gray)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).background(Color.LightGray.copy(alpha = 0.2f), RoundedCornerShape(4.dp)).padding(8.dp)
                    ) {
                        Text(text = deleteConfirmCode, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, letterSpacing = 4.sp)
                        IconButton(onClick = { 
                            clipboardManager.setText(AnnotatedString(deleteConfirmCode))
                            Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(20.dp)) }
                    }
                    OutlinedTextField(value = userInputCode, onValueChange = { userInputCode = it }, placeholder = { Text("Paste or type code here") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(8.dp))
                }
            },
            confirmButton = {
                Button(
                    onClick = { 
                        if (bookToDelete != null) { deleteBook(bookToDelete!!) } 
                        else if (folderToDelete != null) {
                            deleteFolder(folderToDelete!!)
                        }
                    },
                    enabled = userInputCode == deleteConfirmCode,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("Delete Permanently", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { bookToDelete = null; folderToDelete = null; userInputCode = "" }) { Text("Cancel") } }
        )
    }

    // DIALOG: Import Summary
    if (showImportSummary) {
        AlertDialog(
            onDismissRequest = { showImportSummary = false },
            title = { Text("Import Summary", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    val duplicateCount = importResults.count { it.status == ImportStatus.Duplicate }
                    Text("Results for ${importResults.size} files:", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(importResults) { result ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = when(result.status) {
                                        ImportStatus.Success -> Icons.Default.CheckCircle
                                        ImportStatus.Duplicate -> Icons.Default.Error
                                        else -> Icons.Default.Cancel
                                    },
                                    contentDescription = null,
                                    tint = if (result.status == ImportStatus.Success) Color(0xFF2E7D32) else Color(0xFFC62828),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(text = result.name, color = if (result.status == ImportStatus.Success) Color(0xFF2E7D32) else Color(0xFFC62828), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text(text = result.status.name, fontSize = 11.sp, color = if (result.status == ImportStatus.Success) Color(0xFF2E7D32).copy(alpha = 0.8f) else Color(0xFFC62828).copy(alpha = 0.8f))
                            }
                        }
                    }
                    if (duplicateCount > 0) {
                        Spacer(Modifier.height(12.dp))
                        Text("Note: Duplicates were found and skipped.", fontSize = 12.sp, color = Color.Gray, lineHeight = 16.sp)
                    }
                }
            },
            confirmButton = { Button(onClick = { showImportSummary = false }) { Text("Got it") } }
        )
    }

    // DIALOG: Clear Recent Activity
    if (showClearRecentDialog) {
        AlertDialog(
            onDismissRequest = { showClearRecentDialog = false },
            title = { Text("Reset Progress?", fontWeight = FontWeight.Bold) },
            text = { Text("This will clear your reading progress across all books. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            val recentFile = File(context.filesDir, "recent_data.txt")
                            if (recentFile.exists()) recentFile.delete()
                            
                            withContext(Dispatchers.Main) {
                                // Important: Instantly wipe state in current session
                                val resetBooks = libraryBooks.map { it.copy(currentPage = 0, totalPages = 0, lastAccessed = 0L) }
                                libraryBooks.clear()
                                libraryBooks.addAll(resetBooks)
                                
                                recentBooks.clear()
                                showClearRecentDialog = false
                                Toast.makeText(context, "All progress cleared", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("Clear All Progress", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { showClearRecentDialog = false }) { Text("Cancel") } }
        )
    }
    //recent activity area:
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.background,
                modifier = Modifier.width(300.dp)
            ) {
                Spacer(Modifier.height(8.dp))//this can be reduces, if we want the spaccer of the top tpo reduce.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recent Activity", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    if (recentBooks.isNotEmpty()) {
                        IconButton(onClick = { showClearRecentDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, "Clear Recent", tint = Color.Red.copy(alpha = 0.7f))
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
                if (recentBooks.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No recent activity yet", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(recentBooks) { book ->
                            Column(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    updateRecent(book, book.currentPage, book.totalPages)
                                    scope.launch { drawerState.close() }
                                    // Set path to book's folder before opening
                                    updatePath(book.folderPath)
                                    onBookClick(book)
                                }.padding(horizontal = 16.dp, vertical = 2.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(text = book.name.substringBeforeLast("."), maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 16.sp)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    val dateStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(book.lastAccessed))
                                    Text(text = dateStr, fontSize = 10.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f), lineHeight = 10.sp)
                                    if (book.totalPages > 0) {
                                        val percent = ((book.currentPage + 1).toFloat() / book.totalPages * 100).toInt()
                                        Text(text = "Page ${book.currentPage + 1}/${book.totalPages} ($percent%)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), lineHeight = 10.sp)
                                    }
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f), thickness = 0.5.dp)
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
                        if (currentPath == "/") {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.onBackground)
                            }
                        } else {
                            IconButton(onClick = { 
                                val parent = libraryFolders.find { it.path == currentPath }?.parentPath ?: "/"
                                updatePath(parent)
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onBackground)
                            }
                        }
                    },
                    title = {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onBackground, fontSize = 12.sp),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                Row(modifier = Modifier.fillMaxWidth().height(38.dp).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), CircleShape).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                        if (searchQuery.isEmpty()) Text("Search...", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f), fontSize = 12.sp)
                                        innerTextField()
                                    }
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Clear, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        )
                    },
                    actions = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                            if (currentPath != "/") {
                                IconButton(onClick = { scope.launch { drawerState.open() } }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
                                }
                            }
                            IconButton(onClick = { filePickerLauncher.launch(arrayOf("application/pdf")) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { isGridView = !isGridView }, modifier = Modifier.size(36.dp)) {
                                Icon(if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView, null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = onSettingsClick, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (currentPath == "/") {
                        LazyRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, contentPadding = PaddingValues(start = 16.dp, end = 8.dp)) {
                            item {
                                FilterChip(
                                    selected = selectedFilterTag == null,
                                    onClick = { selectedFilterTag = null },
                                    label = { Text("All") },
                                    colors = FilterChipDefaults.filterChipColors(selectedLabelColor = MaterialTheme.colorScheme.onBackground, labelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                                )
                            }
                            items(availableTags) { tagString ->
                                val tagName = tagString.split("|")[0]
                                val tagColor = tagToColorMap[tagName] ?: getTagColor(tagName)
                                FilterChip(
                                    selected = selectedFilterTag == tagName,
                                    onClick = { selectedFilterTag = if (selectedFilterTag == tagName) null else tagName },
                                    label = { Text(tagName) },
                                    colors = FilterChipDefaults.filterChipColors(selectedLabelColor = Color.White, selectedContainerColor = tagColor, labelColor = tagColor.copy(alpha = 0.8f)),
                                    border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selectedFilterTag == tagName, borderColor = tagColor.copy(alpha = 0.5f))
                                )
                            }
                        }
                    } else {
                        // Display the folder path so you know exactly where you are inside sub-folders
                        Text(
                            text = currentPath.removePrefix("/"),
                            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(modifier = Modifier.padding(end = 4.dp), horizontalArrangement = Arrangement.spacedBy((-4).dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showCreateFolderDialog = true }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.CreateNewFolder, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
                        }
                        if (currentPath == "/") {
                            IconButton(onClick = { showCreateTagDialog = true }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.AddCircle, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                if (libraryBooks.isEmpty()) {
                    Column(modifier = Modifier.fillMaxSize().padding(bottom = 64.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text("Your Library is Empty", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        Text("Start your journey by importing a PDF file.", textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    }
                } else if (filteredBooks.isEmpty() && (searchQuery.isNotEmpty() || selectedFilterTag != null)) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No books found", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    }
                } else {
                    if (isGridView) {
                        LazyVerticalGrid(columns = GridCells.Fixed(4), contentPadding = PaddingValues(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            gridItems(filteredFolders) { folder ->
                                val itemCount = libraryFolders.count { it.parentPath == folder.path } + libraryBooks.count { it.folderPath == folder.path }
                                FolderGridItem(
                                    folder = folder,
                                    itemCount = itemCount,
                                    onClick = { updatePath(folder.path) }, 
                                    onDelete = { folderToDelete = folder; deleteConfirmCode = (1000 + Random.nextInt(9000)).toString() },
                                    onTagClick = { showAssignFolderTagDialog = folder }
                                )
                            }
                            gridItems(filteredBooks) { book ->
                                BookGridItem(book, tagColor = tagToColorMap[book.tag] ?: Color.Gray, fontSize = gridFontSize, lineHeight = gridLineHeight, onClick = { updateRecent(book, book.currentPage, book.totalPages); onBookClick(book) }, onDelete = { bookToDelete = book; deleteConfirmCode = (1000 + Random.nextInt(9000)).toString() }, onTagClick = { showAssignTagDialog = book })
                            }
                        }
                    } else {
                        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(filteredFolders) { folder ->
                                val itemCount = libraryFolders.count { it.parentPath == folder.path } + libraryBooks.count { it.folderPath == folder.path }
                                FolderListItem(
                                    folder = folder,
                                    itemCount = itemCount,
                                    onClick = { updatePath(folder.path) }, 
                                    onDelete = { folderToDelete = folder; deleteConfirmCode = (1000 + Random.nextInt(9000)).toString() },
                                    onTagClick = { showAssignFolderTagDialog = folder }
                                )
                            }
                            items(filteredBooks) { book ->
                                BookListItem(book, tagColor = tagToColorMap[book.tag] ?: Color.Gray, fontSize = listFontSize, lineHeight = listLineHeight, onClick = { updateRecent(book, book.currentPage, book.totalPages); onBookClick(book) }, onDelete = { bookToDelete = book; deleteConfirmCode = (1000 + Random.nextInt(9000)).toString() }, onTagClick = { showAssignTagDialog = book })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FolderGridItem(folder: Folder, itemCount: Int, onClick: () -> Unit, onDelete: () -> Unit, onTagClick: () -> Unit) {
    val defaultColor = MaterialTheme.colorScheme.surfaceVariant
    val bgColor = remember(folder.color, defaultColor) {
        try { if (folder.color != null) Color(android.graphics.Color.parseColor(folder.color)) else defaultColor }
        catch (e: Exception) { defaultColor }
    }
    val contentColor = remember(bgColor, folder.color) {
        if (folder.color != null) getContrastColor(bgColor) else null
    }

    Card(
        modifier = Modifier.fillMaxWidth().aspectRatio(0.7f).clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Folder,
                    null,
                    modifier = Modifier.size(48.dp),
                    tint = contentColor?.copy(alpha = 0.7f) ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = folder.name,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            // Total Item Count (Number Only) at the bottom-most left corner
            Text(
                text = itemCount.toString(),
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                color = (contentColor ?: MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
            )

            Row(modifier = Modifier.fillMaxWidth().padding(2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onTagClick, modifier = Modifier.size(24.dp).padding(4.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Label, null, modifier = Modifier.size(16.dp), tint = (contentColor ?: MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.6f))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp).padding(4.dp)) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp), tint = (contentColor ?: MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
fun FolderListItem(folder: Folder, itemCount: Int, onClick: () -> Unit, onDelete: () -> Unit, onTagClick: () -> Unit) {
    val folderColor = remember(folder.color) {
        try { if (folder.color != null) Color(android.graphics.Color.parseColor(folder.color)) else null }
        catch (e: Exception) { null }
    }
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Folder,
            null,
            modifier = Modifier.size(40.dp).padding(4.dp),
            tint = folderColor ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = folder.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(text = itemCount.toString(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
        IconButton(onClick = onTagClick) { Icon(Icons.AutoMirrored.Filled.Label, null, tint = folderColor?.copy(alpha = 0.6f) ?: MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = Color.Red.copy(alpha = 0.6f)) }
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
                        val b = Bitmap.createBitmap(page.width / 4, page.height / 4, Bitmap.Config.ARGB_8888)
                        page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap = b
                        page.close()
                    }
                    renderer.close()
                    pfd.close()
                }
            } catch (e: Exception) { e.printStackTrace() } finally { isLoading = false }
        }
    }
    Box(modifier = modifier.background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.05f))) {
        if (bitmap != null) Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else if (!isLoading) Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(24.dp).align(Alignment.Center), tint = Color.Red.copy(alpha = 0.3f))
    }
}

@Composable
fun BookGridItem(book: Book, tagColor: Color, fontSize: androidx.compose.ui.unit.TextUnit, lineHeight: androidx.compose.ui.unit.TextUnit, onClick: () -> Unit, onDelete: () -> Unit, onTagClick: () -> Unit) {
    val defaultColor = MaterialTheme.colorScheme.surfaceVariant
    val bgColor = remember(book.cardColor, defaultColor) {
        try { if (book.cardColor != null) Color(android.graphics.Color.parseColor(book.cardColor)) else defaultColor }
        catch (e: Exception) { defaultColor }
    }
    val contentColor = remember(bgColor, book.cardColor) {
        if (book.cardColor != null) getContrastColor(bgColor) else null
    }

    Card(modifier = Modifier.fillMaxWidth().aspectRatio(0.7f).clickable { onClick() }, shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = bgColor), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                BookCover(uri = book.uri, modifier = Modifier.fillMaxWidth().weight(1.3f).clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)))
                Column(modifier = Modifier.fillMaxWidth().weight(0.7f).padding(horizontal = 4.dp, vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(text = book.name.substringBeforeLast("."), fontSize = fontSize, fontWeight = FontWeight.Bold, color = contentColor ?: MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = lineHeight)
                    if (book.totalPages > 0) {
                        val progress = (book.currentPage + 1).toFloat() / book.totalPages
                        val percent = (progress * 100).toInt()
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.weight(1f).height(2.dp).clip(CircleShape), color = contentColor?.copy(alpha = 0.7f) ?: MaterialTheme.colorScheme.primary, trackColor = contentColor?.copy(alpha = 0.1f) ?: MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                            Text(text = "$percent%", fontSize = 6.sp, fontWeight = FontWeight.Bold, color = contentColor?.copy(alpha = 0.6f) ?: MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onTagClick, modifier = Modifier.size(18.dp).background(Color.Black.copy(alpha = 0.2f), CircleShape)) { Icon(Icons.AutoMirrored.Filled.Label, null, tint = if (book.tag != null) tagColor else Color.White.copy(alpha = 0.6f), modifier = Modifier.size(10.dp)) }
                IconButton(onClick = onDelete, modifier = Modifier.size(18.dp).background(Color.Black.copy(alpha = 0.2f), CircleShape)) { Icon(Icons.Default.Delete, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(10.dp)) }
            }
        }
    }
}

@Composable
fun BookListItem(book: Book, tagColor: Color, fontSize: androidx.compose.ui.unit.TextUnit, lineHeight: androidx.compose.ui.unit.TextUnit, onClick: () -> Unit, onDelete: () -> Unit, onTagClick: () -> Unit) {
    val defaultColor = MaterialTheme.colorScheme.surfaceVariant
    val bgColor = remember(book.cardColor, defaultColor) {
        try { if (book.cardColor != null) Color(android.graphics.Color.parseColor(book.cardColor)) else defaultColor }
        catch (e: Exception) { defaultColor }
    }
    val contentColor = remember(bgColor, book.cardColor) {
        if (book.cardColor != null) getContrastColor(bgColor) else null
    }

    Card(modifier = Modifier.fillMaxWidth().height(60.dp).clickable { onClick() }, shape = RoundedCornerShape(6.dp), colors = CardDefaults.cardColors(containerColor = bgColor), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(uri = book.uri, modifier = Modifier.size(45.dp).clip(RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = book.name.substringBeforeLast("."), fontSize = fontSize, fontWeight = FontWeight.Bold, color = contentColor ?: MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, lineHeight = lineHeight)
                if (book.totalPages > 0) {
                    val percent = ((book.currentPage + 1).toFloat() / book.totalPages * 100).toInt()
                    Text(text = "Page ${book.currentPage + 1}/${book.totalPages} ($percent%)", fontSize = 10.sp, color = contentColor?.copy(alpha = 0.6f) ?: MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }
            IconButton(onClick = onTagClick, modifier = Modifier.size(32.dp)) { Icon(Icons.AutoMirrored.Filled.Label, null, tint = if (book.tag != null) tagColor else contentColor?.copy(alpha = 0.6f) ?: MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(16.dp)) }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Delete, null, tint = contentColor?.copy(alpha = 0.6f) ?: Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(16.dp)) }
        }
    }
}
