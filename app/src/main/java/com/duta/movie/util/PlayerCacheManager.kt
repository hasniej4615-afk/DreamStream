package com.duta.movie.util

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PlayerCacheManager {

    private var cache: SimpleCache? = null

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun getCache(context: Context): SimpleCache {
        return cache ?: synchronized(this) {
            cache ?: run {
                val cacheDir = File(context.cacheDir, "media3_cache")
                val evictor = LeastRecentlyUsedCacheEvictor(100 * 1024 * 1024) // 100 MB max cache
                val databaseProvider = StandaloneDatabaseProvider(context)
                SimpleCache(cacheDir, evictor, databaseProvider).also { cache = it }
            }
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun getCacheDataSourceFactory(
        context: Context,
        upstreamFactory: DataSource.Factory
    ): CacheDataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(getCache(context))
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /**
     * Pre-caches the header and initial segments of a stream into SimpleCache.
     * Essential for high-latency hosts (like Archive.org) where downloading the 5MB moov atom
     * takes 12-15s over cold spinning disk clusters.
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    suspend fun precacheStream(
        context: Context,
        url: String,
        bytesToPrecache: Long = 6L * 1024 * 1024
    ) = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext
        try {
            val okHttpFactory = OkHttpDataSource.Factory(NetworkConfig.permissiveOkHttpClient)
                .setUserAgent(NetworkConfig.SHARED_USER_AGENT)
            val cacheDataSource = getCacheDataSourceFactory(context, okHttpFactory).createDataSource()
            val dataSpec = DataSpec.Builder()
                .setUri(Uri.parse(url))
                .setPosition(0)
                .setLength(bytesToPrecache)
                .build()
            val cacheWriter = CacheWriter(
                cacheDataSource,
                dataSpec,
                null,
                null
            )
            Log.d("PlayerCacheManager", "Pre-caching started for: $url ($bytesToPrecache bytes)")
            cacheWriter.cache()
            Log.d("PlayerCacheManager", "Pre-caching completed for: $url")
        } catch (e: Exception) {
            Log.d("PlayerCacheManager", "Pre-caching ended/interrupted: ${e.message}")
        }
    }
}
