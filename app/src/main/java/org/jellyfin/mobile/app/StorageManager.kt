package org.jellyfin.mobile.app

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import org.jellyfin.mobile.R
import org.jellyfin.mobile.data.entity.DownloadFileEntity
import org.jellyfin.mobile.data.entity.DownloadFiles
import org.jellyfin.mobile.downloads.DownloadFileType
import org.jellyfin.mobile.downloads.DownloadStatus
import timber.log.Timber
import java.io.File

class StorageManager(
    private val context: Context,
) {
    private val fixedStorageDirectory: File
        get() {
            val downloadsRoot = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.filesDir.resolve(DOWNLOADS_FALLBACK_DIRECTORY)
            return downloadsRoot.resolve(context.getString(R.string.app_name_short))
        }

    fun getStorageLocation(): DocumentFile? = runCatching {
        val directory = fixedStorageDirectory
        if (!directory.exists() && !directory.mkdirs()) {
            error("Unable to create fixed download directory $directory")
        }
        if (!directory.isDirectory || !directory.canWrite()) {
            error("Fixed download directory is not writable: $directory")
        }

        DocumentFile.fromFile(directory).also(::ensureNoMedia)
    }.onFailure { error ->
        Timber.e(error, "Unable to access fixed Zadflix download directory")
    }.getOrNull()

    fun isStorageLocationAccessible(): Boolean {
        val documentFile = getStorageLocation()
        return documentFile != null && documentFile.exists() && documentFile.canWrite()
    }

    fun getFile(uri: Uri): DocumentFile? = when (uri.scheme) {
        ContentResolver.SCHEME_FILE -> uri.path?.let(::File)?.let(DocumentFile::fromFile)
        else -> DocumentFile.fromSingleUri(context, uri)
    }

    fun verify(download: DownloadFiles): Boolean {
        if (download.files.isEmpty()) return false

        return download.files.all(::verify)
    }

    fun verifyPlayback(download: DownloadFiles): Boolean = download.files
        .find { it.type == DownloadFileType.ITEM }
        ?.let(::verify)
        ?: false

    private fun verify(file: DownloadFileEntity): Boolean {
        if (file.status != DownloadStatus.DOWNLOADED || file.size <= 0) return false
        val documentFile = getFile(file.uri)
        return documentFile != null && documentFile.exists() && documentFile.length() == file.size
    }

    private fun ensureNoMedia(documentFile: DocumentFile) {
        if (documentFile.findFile(NOMEDIA_FILE) == null) {
            documentFile.createFile("", NOMEDIA_FILE)
        }
    }

    companion object {
        const val NOMEDIA_FILE = ".nomedia"
        const val DOWNLOADS_FALLBACK_DIRECTORY = "downloads"
    }
}
