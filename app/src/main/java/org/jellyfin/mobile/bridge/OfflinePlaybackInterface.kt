package org.jellyfin.mobile.bridge

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.webkit.JavascriptInterface
import org.jellyfin.mobile.app.StorageManager
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.events.ActivityEvent
import org.jellyfin.mobile.events.ActivityEventHandler
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.serializer.toUUIDOrNull

@Suppress("unused", "DEPRECATION")
class OfflinePlaybackInterface internal constructor(
    private val hasActiveNetwork: () -> Boolean,
    private val downloadDao: DownloadDao,
    private val storageManager: StorageManager,
    private val activityEventHandler: ActivityEventHandler,
) {
    constructor(
        context: Context,
        downloadDao: DownloadDao,
        storageManager: StorageManager,
        activityEventHandler: ActivityEventHandler,
    ) : this(
        hasActiveNetwork = {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            connectivityManager.allNetworks.any { network ->
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return@any false
                val hasBaseTransport = listOf(
                    NetworkCapabilities.TRANSPORT_WIFI,
                    NetworkCapabilities.TRANSPORT_CELLULAR,
                    NetworkCapabilities.TRANSPORT_ETHERNET,
                ).any(capabilities::hasTransport)
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && hasBaseTransport
            }
        },
        downloadDao = downloadDao,
        storageManager = storageManager,
        activityEventHandler = activityEventHandler,
    )

    /**
     * Handles a Jellyfin web card click when Android has no active network.
     *
     * @return true when native offline UI handled the click; false when the web app should handle it normally.
     */
    @JavascriptInterface
    fun handleItemClick(itemId: String): Boolean {
        if (!isOffline()) return false

        val downloadFiles = itemId.toUUIDOrNull()?.let(downloadDao::getDownloadWithFilesByItemId)
        val download = downloadFiles?.download?.takeIf { it.item.mediaType == MediaType.VIDEO }
        if (
            downloadFiles != null &&
            download != null &&
            storageManager.verifyPlayback(downloadFiles)
        ) {
            activityEventHandler.emit(
                ActivityEvent.LaunchNativePlayer(
                    PlayOptions(
                        ids = listOf(download.itemId),
                        mediaSourceId = download.itemId.toString(),
                        startIndex = 0,
                        startPosition = null,
                        audioStreamIndex = null,
                        subtitleStreamIndex = null,
                        playFromDownloads = true,
                    ),
                ),
            )
        } else {
            activityEventHandler.emit(ActivityEvent.OpenDownloads)
        }

        return true
    }

    fun isOffline(): Boolean = !hasActiveNetwork()
}
