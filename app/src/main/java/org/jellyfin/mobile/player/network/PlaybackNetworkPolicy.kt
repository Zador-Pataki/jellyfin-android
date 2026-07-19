package org.jellyfin.mobile.player.network

import android.content.Context
import android.net.ConnectivityManager
import org.jellyfin.mobile.app.AppPreferences
import org.jellyfin.mobile.settings.MeteredPlaybackQuality
import org.jellyfin.mobile.utils.Constants

data class PlaybackNetworkDecision(
    val maxStreamingBitrate: Int?,
    val preferExtraLargeBuffer: Boolean,
)

class PlaybackNetworkPolicy(
    context: Context,
    private val appPreferences: AppPreferences,
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun currentDecision(): PlaybackNetworkDecision = decide(
        isMetered = connectivityManager.isActiveNetworkMetered,
        meteredPlaybackQuality = appPreferences.meteredPlaybackQuality,
    )

    fun effectiveBufferPreference(): String = resolveBufferPreference(
        selectedPreference = appPreferences.exoPlayerNetworkBuffer,
        preferExtraLargeBuffer = currentDecision().preferExtraLargeBuffer,
    )

    companion object {
        internal fun decide(
            isMetered: Boolean,
            meteredPlaybackQuality: MeteredPlaybackQuality,
        ): PlaybackNetworkDecision = when {
            !isMetered -> PlaybackNetworkDecision(
                maxStreamingBitrate = null,
                preferExtraLargeBuffer = false,
            )
            else -> PlaybackNetworkDecision(
                maxStreamingBitrate = meteredPlaybackQuality.maxStreamingBitrate,
                preferExtraLargeBuffer = true,
            )
        }

        internal fun resolveBufferPreference(
            selectedPreference: String,
            preferExtraLargeBuffer: Boolean,
        ): String = when {
            selectedPreference != Constants.NETWORK_BUFFER_AUTO -> selectedPreference
            preferExtraLargeBuffer -> Constants.NETWORK_BUFFER_EXTRA_LARGE
            else -> Constants.NETWORK_BUFFER_AUTO
        }
    }
}
