package org.jellyfin.mobile.player.network

import org.jellyfin.mobile.settings.MeteredPlaybackQuality
import org.jellyfin.mobile.utils.Constants
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackNetworkPolicyTest {
    @Test
    fun `unmetered network preserves original quality and buffer`() {
        val decision = PlaybackNetworkPolicy.decide(
            isMetered = false,
            meteredPlaybackQuality = MeteredPlaybackQuality.DATA_SAVER,
        )

        assertNull(decision.maxStreamingBitrate)
        assertFalse(decision.preferExtraLargeBuffer)
    }

    @Test
    fun `auto caps metered playback below measured cellular goodput`() {
        val decision = PlaybackNetworkPolicy.decide(
            isMetered = true,
            meteredPlaybackQuality = MeteredPlaybackQuality.AUTO,
        )

        assertEquals(2_500_000, decision.maxStreamingBitrate)
        assertTrue(decision.preferExtraLargeBuffer)
    }

    @Test
    fun `original removes bitrate cap but keeps metered buffering`() {
        val decision = PlaybackNetworkPolicy.decide(
            isMetered = true,
            meteredPlaybackQuality = MeteredPlaybackQuality.ORIGINAL,
        )

        assertNull(decision.maxStreamingBitrate)
        assertTrue(decision.preferExtraLargeBuffer)
    }

    @Test
    fun `explicit metered quality choices map to their bitrate`() {
        assertEquals(
            1_500_000,
            PlaybackNetworkPolicy.decide(true, MeteredPlaybackQuality.DATA_SAVER).maxStreamingBitrate,
        )
        assertEquals(
            2_500_000,
            PlaybackNetworkPolicy.decide(true, MeteredPlaybackQuality.BALANCED).maxStreamingBitrate,
        )
        assertEquals(
            4_000_000,
            PlaybackNetworkPolicy.decide(true, MeteredPlaybackQuality.HIGH).maxStreamingBitrate,
        )
    }

    @Test
    fun `metered auto buffer upgrades to extra large`() {
        assertEquals(
            Constants.NETWORK_BUFFER_EXTRA_LARGE,
            PlaybackNetworkPolicy.resolveBufferPreference(
                selectedPreference = Constants.NETWORK_BUFFER_AUTO,
                preferExtraLargeBuffer = true,
            ),
        )
    }

    @Test
    fun `explicit buffer preference overrides network recommendation`() {
        assertEquals(
            Constants.NETWORK_BUFFER_LARGE,
            PlaybackNetworkPolicy.resolveBufferPreference(
                selectedPreference = Constants.NETWORK_BUFFER_LARGE,
                preferExtraLargeBuffer = true,
            ),
        )
    }
}
