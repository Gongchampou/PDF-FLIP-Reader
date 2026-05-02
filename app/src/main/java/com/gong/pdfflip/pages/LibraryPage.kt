package com.gong.pdfflip.pages

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

// Updated Book class to support categories/tags
data class Book(
    val name: String, 
    val uri: Uri, 
    val lastAccessed: Long = System.currentTimeMillis(),
    val tag: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onBookClick: (Uri) -> Unit, onSettingsClick: () -> Unit) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    
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

    val libraryBooks = remember { mutableStateListOf<Book>() }
    val recentBooks = remember { mutableStateListOf<Book>() }

    // Save/Load metadata (tags)
    fun saveLibraryMetadata() {
        scope.launch(Dispatchers.IO) {
            val metadataFile = File(context.filesDir, "library_tags.txt")
            val tagsFile = File(context.filesDir, "available_tags.txt")
            
            metadataFile.writeText(libraryBooks.joinToString("\n") { "${it.name}|${it.tag ?: ""}" })
            tagsFile.writeText(availableTags.joinToString("\n"))
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val filesDir = context.filesDir
            val allFiles = filesDir.listFiles { file -> file.extension.lowercase() == "pdf" } ?: emptyArray()
            
            // Load tags mapping
            val tagMap = mutableMapOf<String, String>()
            val metadataFile = File(context.filesDir, "library_tags.txt")
            if (metadataFile.exists()) {
                metadataFile.readLines().forEach { line ->
                    val parts = line.split("|")
                    if (parts.size >= 2 && parts[1].isNotBlank()) tagMap[parts[0]] = parts[1]
                }
            }

            // Load available tags list
            val tagsFile = File(context.filesDir, "available_tags.txt")
            val loadedTags = if (tagsFile.exists()) tagsFile.readLines().filter { it.isNotBlank() } else emptyList()

            val loadedLibrary = allFiles.map { Book(it.name, Uri.fromFile(it), tag = tagMap[it.name]) }
            
            val recentFile = File(context.filesDir, "recent_data.txt")
            val loadedRecent = mutableListOf<Book>()
            if (recentFile.exists()) {
                recentFile.readLines().forEach { line ->
                    val parts = line.split("|")
                    if (parts.size == 2) {
                        val name = parts[0]
                        val time = parts[1].toLongOrNull() ?: System.currentTimeMillis()
                        val file = File(context.filesDir, name)
                        if (file.exists()) {
                            loadedRecent.add(Book(file.name, Uri.fromFile(file), time, tag = tagMap[file.name]))
                        }
                    }
                }
                loadedRecent.sortByDescending { it.lastAccessed }
            }
            
            withContext(Dispatchers.Main) {
                libraryBooks.clear()
                libraryBooks.addAll(loadedLibrary)
                recentBooks.clear()
                recentBooks.addAll(loadedRecent)
                availableTags.clear()
                availableTags.addAll(loadedTags)
            }
        }
    }

    fun updateRecent(book: Book) {
        val currentTime = System.currentTimeMillis()
        val updatedBook = book.copy(lastAccessed = currentTime)
        recentBooks.removeAll { it.name == book.name }
        recentBooks.add(0, updatedBook)
        if (recentBooks.size > 10) recentBooks.removeRange(10, recentBooks.size)
        
        val dataToSave = recentBooks.toList()
        scope.launch(Dispatchers.IO) {
            val recentFile = File(context.filesDir, "recent_data.txt")
            recentFile.writeText(dataToSave.joinToString("\n") { "${it.name}|${it.lastAccessed}" })
        }
    }

    fun deleteBook(book: Book) {
        scope.launch(Dispatchers.IO) {
            val file = File(context.filesDir, book.name)
            if (file.exists()) file.delete()
            withContext(Dispatchers.Main) {
                libraryBooks.remove(book)
                recentBooks.removeAll { it.name == book.name }
                saveLibraryMetadata()
                Toast.makeText(context, "${book.name.substringBeforeLast(".")} deleted", Toast.LENGTH_SHORT).show()
                bookToDelete = null
                userInputCode = ""
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
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? -> 
            uri?.let { sourceUri ->
                scope.launch {
                    try {
                        val fileName = getFileName(context, sourceUri) ?: "Document_${System.currentTimeMillis()}.pdf"
                        val destinationFile = File(context.filesDir, fileName)
                        context.contentResolver.openInputStream(sourceUri)?.use { input ->
                            FileOutputStream(destinationFile).use { output -> input.copyTo(output) }
                        }
                        val newBook = Book(fileName, Uri.fromFile(destinationFile))
                        if (libraryBooks.none { it.name == fileName }) libraryBooks.add(newBook)
                        updateRecent(newBook)
                        onBookClick(newBook.uri)
                    } catch (e: Exception) { e.printStackTrace() }
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
                    Text("Are you sure you want to delete \"${bookToDelete!!.name.substringBeforeLast(".")}\"? This action cannot be undone.")
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
                                        updateRecent(book)
                                        scope.launch { drawerState.close() }
                                        onBookClick(book.uri)
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
                                val dateStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(book.lastAccessed))
                                Text(
                                    text = dateStr,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                    lineHeight = 10.sp
                                )
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
                            FilterChip(
                                selected = selectedFilterTag == tag,
                                onClick = { selectedFilterTag = if (selectedFilterTag == tag) null else tag },
                                label = { Text(tag) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedLabelColor = MaterialTheme.colorScheme.onBackground,
                                    labelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
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
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filteredBooks) { book ->
                                BookGridItem(book, 
                                    onClick = { 
                                        updateRecent(book)
                                        onBookClick(book.uri) 
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
                                    onClick = { 
                                        updateRecent(book)
                                        onBookClick(book.uri) 
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
fun BookGridItem(book: Book, onClick: () -> Unit, onDelete: () -> Unit, onTagClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.7f)
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Actions (Top)
            Row(
                modifier = Modifier.fillMaxWidth().padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onTagClick, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Default.Label,
                        contentDescription = "Tag", 
                        tint = if (book.tag != null) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                }
            }
            
            Column(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.PictureAsPdf, 
                    contentDescription = null, 
                    modifier = Modifier.size(32.dp),
                    tint = Color.Red.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = book.name.substringBeforeLast("."),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun BookListItem(book: Book, onClick: () -> Unit, onDelete: () -> Unit, onTagClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.PictureAsPdf, 
                contentDescription = null, 
                modifier = Modifier.size(32.dp),
                tint = Color.Red.copy(alpha = 0.6f)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.name.substringBeforeLast("."),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onTagClick) {
                Icon(Icons.Default.Label, contentDescription = "Tag", tint = if (book.tag != null) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.6f))
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
