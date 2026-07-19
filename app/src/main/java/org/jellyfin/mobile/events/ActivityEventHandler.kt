package org.jellyfin.mobile.events

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.jellyfin.mobile.MainActivity
import org.jellyfin.mobile.R
import org.jellyfin.mobile.bridge.JavascriptCallback
import org.jellyfin.mobile.downloads.DownloadManager
import org.jellyfin.mobile.downloads.DownloadsFragment
import org.jellyfin.mobile.player.ui.PlayerFragment
import org.jellyfin.mobile.player.ui.PlayerFullscreenHelper
import org.jellyfin.mobile.settings.SettingsFragment
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.extensions.addFragment
import org.jellyfin.mobile.utils.requestDownload
import org.jellyfin.mobile.webapp.WebappFunctionChannel
import org.koin.android.ext.android.get
import timber.log.Timber

class ActivityEventHandler(
    private val webappFunctionChannel: WebappFunctionChannel,
) {
    private val eventsFlow = MutableSharedFlow<ActivityEvent>(
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )

    fun MainActivity.subscribe() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.CREATED) {
                eventsFlow.collect { event ->
                    handleEvent(event)
                }
            }
        }
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun MainActivity.handleEvent(event: ActivityEvent) {
        when (event) {
            is ActivityEvent.ChangeFullscreen -> {
                val fullscreenHelper = PlayerFullscreenHelper(window)
                if (event.isFullscreen) {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    fullscreenHelper.enableFullscreen()
                    window.setBackgroundDrawable(null)
                } else {
                    // Reset screen orientation
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    fullscreenHelper.disableFullscreen()
                    // Reset window background color
                    window.setBackgroundDrawableResource(R.color.theme_background)
                }
            }
            is ActivityEvent.LaunchNativePlayer -> {
                val args = Bundle().apply {
                    putParcelable(Constants.EXTRA_MEDIA_PLAY_OPTIONS, event.playOptions)
                }
                supportFragmentManager.addFragment<PlayerFragment>(args)
            }
            is ActivityEvent.OpenUrl -> {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, event.uri.toUri())
                    if (event.grantReadPermission) intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    Timber.e("openIntent: %s", e.message)
                }
            }
            is ActivityEvent.DownloadItems -> {
                lifecycleScope.launch {
                    with(event) { requestDownload(itemIds) }
                }
            }
            is ActivityEvent.ConfirmDownloadDeletion -> {
                fun refreshDownloadButton() {
                    webappFunctionChannel.call(
                        """
                        window.dispatchEvent(new CustomEvent('zadflix-download-state-changed', {
                            detail: { itemId: '${event.itemId}' }
                        }));
                        """.trimIndent(),
                    )
                }

                AlertDialog.Builder(this).apply {
                    setTitle(R.string.zadflix_download_delete_title)
                    setMessage(getString(R.string.zadflix_download_delete_message, event.displayName))
                    setPositiveButton(R.string.zadflix_download_delete_confirm) { _, _ ->
                        lifecycleScope.launch {
                            get<DownloadManager>().delete(event.downloadId, deleteFiles = true)
                            refreshDownloadButton()
                        }
                    }
                    setNegativeButton(R.string.download_cancel) { _, _ -> refreshDownloadButton() }
                    setOnCancelListener { refreshDownloadButton() }
                }.show()
            }
            ActivityEvent.OpenDownloads -> {
                supportFragmentManager.addFragment<DownloadsFragment>()
            }
            is ActivityEvent.CastMessage -> {
                val action = event.action
                chromecast.execute(
                    action,
                    event.args,
                    object : JavascriptCallback() {
                        override fun callback(keep: Boolean, err: String?, result: String?) {
                            webappFunctionChannel.call(
                                """window.NativeShell.castCallback("$action", $keep, $err, $result);""",
                            )
                        }
                    },
                )
            }
            ActivityEvent.RequestBluetoothPermission -> {
                lifecycleScope.launch {
                    bluetoothPermissionHelper.requestBluetoothPermissionIfNecessary()
                }
            }
            ActivityEvent.OpenSettings -> {
                supportFragmentManager.addFragment<SettingsFragment>()
            }
            ActivityEvent.SelectServer -> {
                mainViewModel.resetServer()
            }
            ActivityEvent.ExitApp -> {
                if (serviceBinder?.isPlaying == true) {
                    moveTaskToBack(false)
                } else {
                    finish()
                }
            }
        }
    }

    fun emit(event: ActivityEvent): Boolean = eventsFlow.tryEmit(event)
}
