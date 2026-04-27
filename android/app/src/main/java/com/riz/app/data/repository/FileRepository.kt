package com.riz.app.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FileRepository(
    private val context: Context,
) {
    private val resultsDir: File by lazy {
        File(context.cacheDir, "riz_results").apply {
            if (!exists()) mkdirs()
        }
    }

    suspend fun getFileInfo(uri: Uri): Pair<String, Long>? =
        withContext(Dispatchers.IO) {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    val name = if (nameIndex != -1) cursor.getString(nameIndex) else "unknown"
                    val size = if (sizeIndex != -1) cursor.getLong(sizeIndex) else 0L
                    name to size
                } else {
                    null
                }
            }
        }

    suspend fun readFile(uri: Uri): ByteArray =
        withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
        }

    suspend fun writeResultFile(
        name: String,
        bytes: ByteArray,
    ): File =
        withContext(Dispatchers.IO) {
            val file = File(resultsDir, name)
            file.writeBytes(bytes)
            file
        }

    suspend fun clearCache() =
        withContext(Dispatchers.IO) {
            resultsDir.listFiles()?.forEach { it.delete() }
        }

    suspend fun copyFileToUri(
        sourceFile: File,
        destinationUri: Uri,
    ) = withContext(Dispatchers.IO) {
        if (!sourceFile.exists()) {
            throw java.io.FileNotFoundException("Source file missing from cache")
        }
        context.contentResolver.openOutputStream(destinationUri)?.use { output ->
            sourceFile.inputStream().use { input ->
                input.copyTo(output)
            }
        }
    }

    suspend fun saveAllFilesToTreeUri(
        files: List<File>,
        treeUri: Uri,
    ) = withContext(Dispatchers.IO) {
        val documentTree =
            androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)
                ?: error("Cannot access directory")

        for (file in files) {
            val docFile =
                documentTree.createFile("application/octet-stream", file.name)
                    ?: error("Cannot create file ${file.name}")

            context.contentResolver.openOutputStream(docFile.uri)?.use { output ->
                file.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: error("Cannot open output stream for ${file.name}")
        }
    }

    fun getCacheDir(): File = resultsDir
}
