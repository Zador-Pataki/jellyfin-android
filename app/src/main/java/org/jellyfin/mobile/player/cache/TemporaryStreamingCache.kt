package org.jellyfin.mobile.player.cache

import android.content.Context
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import timber.log.Timber
import java.io.File

/**
 * Owns the bounded, disposable cache used only for remote media playback.
 *
 * The cache lives in Android's cache directory so the operating system may reclaim it when
 * storage is low. Data left behind by an abruptly terminated process is removed before the next
 * streaming session; active sessions are kept below [MAX_CACHE_BYTES] by LRU eviction.
 */
class TemporaryStreamingCache(
    context: Context,
    databaseProvider: DatabaseProvider,
) {
    val cache: Cache

    init {
        val cacheDirectory = File(context.cacheDir, CACHE_DIRECTORY_NAME)

        // Android does not guarantee lifecycle callbacks when a process is killed. Clearing the
        // previous process' cache here makes cleanup deterministic the next time streaming starts.
        runCatching {
            if (cacheDirectory.exists()) {
                SimpleCache.delete(cacheDirectory, databaseProvider)
            }
        }.onFailure { error ->
            Timber.w(error, "Unable to clear the previous temporary streaming cache")
        }

        cache = SimpleCache(
            cacheDirectory,
            LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
            databaseProvider,
        )
    }

    companion object {
        const val MAX_CACHE_BYTES = 512L * 1024L * 1024L
        private const val CACHE_DIRECTORY_NAME = "streaming-media"
    }
}
