package com.gong.pdfflip.utils

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * BackupUtils - The "Mover" 🚚
 * This utility handles packaging your entire library into a single file and unpacking it.
 */
object BackupUtils {

    private const val METADATA_DIR = "_metadata/"
    private val METADATA_FILES = listOf("available_tags.txt", "library_folders.txt", "library_tags.txt", "recent_data.txt")

    /**
     * EXPORT: Packages PDF Flip folder and app settings into a ZIP
     */
    fun exportLibrary(context: Context, outputStream: OutputStream): Boolean {
        return try {
            ZipOutputStream(outputStream).use { zos ->
                // 1. Add Metadata Files
                METADATA_FILES.forEach { fileName ->
                    val file = File(context.filesDir, fileName)
                    if (file.exists()) {
                        zos.putNextEntry(ZipEntry(METADATA_DIR + fileName))
                        file.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }

                // 2. Add PDF Files from Documents/PDF Flip
                val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                val baseDir = File(documentsDir, "PDF Flip")
                if (baseDir.exists()) {
                    baseDir.walkTopDown().filter { it.isFile && (it.extension.lowercase() == "pdf") }.forEach { file ->
                        val relativePath = file.relativeTo(baseDir).path.replace("\\", "/")
                        zos.putNextEntry(ZipEntry(relativePath))
                        file.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * IMPORT: Unpacks a backup file and restores the library
     */
    fun importLibrary(context: Context, inputStream: InputStream): Boolean {
        return try {
            ZipInputStream(inputStream).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (name.startsWith(METADATA_DIR)) {
                        // Restore Metadata
                        val fileName = name.substringAfter(METADATA_DIR)
                        if (METADATA_FILES.contains(fileName)) {
                            val targetFile = File(context.filesDir, fileName)
                            targetFile.outputStream().use { zis.copyTo(it) }
                        }
                    } else if (!entry.isDirectory && name.lowercase().endsWith(".pdf")) {
                        // Restore PDF to MediaStore
                        val fileName = name.substringAfterLast("/")
                        val internalPath = if (name.contains("/")) name.substringBeforeLast("/") else ""
                        
                        restorePdfToDisk(context, zis, fileName, internalPath)
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun restorePdfToDisk(context: Context, inputStream: InputStream, fileName: String, internalPath: String) {
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        // Determine destination folder
        val relativeStoragePath = if (internalPath.isEmpty()) {
            "Documents/PDF Flip"
        } else {
            "Documents/PDF Flip/$internalPath"
        }

        // Check if exists to avoid duplicates
        val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ? AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?"
        } else {
            "${MediaStore.Files.FileColumns.DATA} LIKE ?"
        }
        val selectionArgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(fileName, "%$relativeStoragePath%")
        } else {
            arrayOf("%/$relativeStoragePath/$fileName")
        }

        val exists = resolver.query(collection, arrayOf(MediaStore.Files.FileColumns._ID), selection, selectionArgs, null)?.use {
            it.count > 0
        } ?: false

        if (exists) return // Skip if already there

        // Insert
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeStoragePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(collection, values)
        uri?.let { destUri ->
            resolver.openOutputStream(destUri)?.use { output ->
                inputStream.copyTo(output)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val updateValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(destUri, updateValues, null, null)
            }
        }
    }
}
