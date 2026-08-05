package com.hdfc.docupload.util

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.DecimalFormat
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log10
import kotlin.math.pow

@Singleton
class FileUtils @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** Directory where local copies of documents live (survives process death). */
    private val documentsDir: File
        get() = File(context.filesDir, "documents").apply { if (!exists()) mkdirs() }

    fun queryDisplayName(uri: Uri): String {
        var name = "document"
        val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && it.moveToFirst()) name = it.getString(index)
        }
        return name
    }

    fun querySize(uri: Uri): Long {
        var size = 0L
        val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val index = it.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && it.moveToFirst()) size = it.getLong(index)
        }
        return size
    }

    fun mimeType(uri: Uri): String =
        context.contentResolver.getType(uri) ?: "application/octet-stream"

    /** Copies external content (gallery/pdf) into app-private storage. */
    fun copyToInternal(uri: Uri, targetName: String): File {
        val target = File(documentsDir, "${System.currentTimeMillis()}_$targetName")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }

    fun newImageFile(prefix: String = "scan"): File =
        File(documentsDir, "${prefix}_${System.currentTimeMillis()}.jpg")

    fun uriForFile(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun delete(uriString: String) {
        runCatching {
            val uri = Uri.parse(uriString)
            uri.path?.let { path ->
                val file = File(path)
                if (file.exists() && file.absolutePath.startsWith(context.filesDir.absolutePath)) {
                    file.delete()
                }
            }
        }
    }

    companion object {
        fun readableSize(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
                .coerceIn(0, units.size - 1)
            return DecimalFormat("#,##0.#").format(
                bytes / 1024.0.pow(digitGroups.toDouble())
            ) + " " + units[digitGroups]
        }
    }
}
