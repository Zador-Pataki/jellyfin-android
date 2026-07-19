package org.jellyfin.mobile.bridge

import android.content.Context
import android.webkit.JavascriptInterface
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.jellyfin.mobile.app.StorageManager
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.data.entity.DownloadFiles
import org.jellyfin.mobile.events.ActivityEvent
import org.jellyfin.mobile.events.ActivityEventHandler
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.UUID

/**
 * Exposes account-scoped local download state to Zadflix's injected movie-card controls.
 *
 * JavaScript interfaces run on WebView's bridge thread. This allows the synchronous Room and file-system checks below
 * without blocking Android's main thread. UI work is forwarded to [ActivityEventHandler].
 */
class ZadflixDownloadBridge(
    private val context: Context,
    private val serverId: Long,
    private val userIdProvider: () -> Long?,
    private val downloadDao: DownloadDao,
    private val storageManager: StorageManager,
    private val activityEventHandler: ActivityEventHandler,
) {
    @JavascriptInterface
    fun getStates(args: String): String {
        val requestedItems = parseItemIds(args)
        if (requestedItems.isEmpty()) return EMPTY_STATES

        val userId = userIdProvider() ?: return EMPTY_STATES
        val downloads = runCatching {
            downloadDao.getDownloadsWithFiles(serverId, userId, requestedItems.values.toSet())
        }.onFailure { error ->
            Timber.e(error, "Unable to read local download states")
        }.getOrDefault(emptyList())
        val downloadsByItemId = downloads.associateBy { it.download.itemId }

        return buildJsonObject {
            requestedItems.forEach { (requestedItemId, itemId) ->
                val state = downloadsByItemId[itemId]
                    ?.takeIf(::isVerifiedLocalCopy)
                    ?.let { STATE_DOWNLOADED }
                    ?: STATE_NOT_DOWNLOADED
                put(requestedItemId, state)
            }
        }.toString()
    }

    @JavascriptInterface
    fun requestDeletion(itemId: String): Boolean {
        val parsedItemId = itemId.toUUIDOrNull() ?: return false
        val userId = userIdProvider() ?: return false
        val downloadFiles = runCatching {
            downloadDao.getDownloadsWithFiles(serverId, userId, setOf(parsedItemId)).singleOrNull()
        }.onFailure { error ->
            Timber.e(error, "Unable to find local download for deletion")
        }.getOrNull() ?: return false

        if (!isVerifiedLocalCopy(downloadFiles)) return false

        val download = downloadFiles.download
        return activityEventHandler.emit(
            ActivityEvent.ConfirmDownloadDeletion(
                downloadId = download.id,
                itemId = download.itemId,
                displayName = download.getDisplayName(context).orEmpty(),
            ),
        )
    }

    private fun isVerifiedLocalCopy(downloadFiles: DownloadFiles): Boolean = storageManager.verify(downloadFiles)

    private fun parseItemIds(args: String): Map<String, UUID> = runCatching {
        val array = Json.parseToJsonElement(args).jsonArray
        buildMap {
            repeat(minOf(array.size, MAX_STATE_ITEMS)) { index ->
                val requestedItemId = array[index].jsonPrimitive.contentOrNull ?: return@repeat
                requestedItemId.toUUIDOrNull()?.let { itemId -> put(requestedItemId, itemId) }
            }
        }
    }.onFailure { error ->
        Timber.e(error, "Unable to parse local download state request")
    }.getOrDefault(emptyMap())

    companion object {
        const val STATE_DOWNLOADED = "DOWNLOADED"
        const val STATE_NOT_DOWNLOADED = "NOT_DOWNLOADED"

        private const val EMPTY_STATES = "{}"
        private const val MAX_STATE_ITEMS = 500
    }
}
