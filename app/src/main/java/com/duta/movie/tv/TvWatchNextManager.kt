package com.duta.movie.tv

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.duta.movie.model.Video
import com.duta.movie.util.DeviceUtils
import com.duta.movie.util.VideoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TvWatchNextManager {

    private const val TAG = "TvWatchNextManager"

    fun isTvWatchNextSupported(context: Context): Boolean {
        if (!DeviceUtils.isTvDevice(context)) return false
        return try {
            val cursor = context.contentResolver.query(
                TvContractCompat.WatchNextPrograms.CONTENT_URI,
                arrayOf(TvContractCompat.WatchNextPrograms._ID),
                null, null, null
            )
            val supported = cursor != null
            cursor?.close()
            supported
        } catch (e: Exception) {
            Log.w(TAG, "WatchNext not supported on this device: ${e.message}")
            false
        }
    }

    fun findWatchNextProgramId(context: Context, videoId: String): Long {
        try {
            val cursor = context.contentResolver.query(
                TvContractCompat.WatchNextPrograms.CONTENT_URI,
                arrayOf(
                    TvContractCompat.WatchNextPrograms._ID,
                    TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID
                ),
                "${TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID} = ?",
                arrayOf(videoId),
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val idIndex = it.getColumnIndex(TvContractCompat.WatchNextPrograms._ID)
                    if (idIndex != -1) {
                        return it.getLong(idIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error finding WatchNext program for $videoId: ${e.message}")
        }
        return -1L
    }

    suspend fun updateWatchNext(
        context: Context,
        video: Video,
        positionMs: Long,
        durationMs: Long
    ) = withContext(Dispatchers.IO) {
        if (!isTvWatchNextSupported(context)) return@withContext

        try {
            // If user finished watching (> 95%), remove from Continue Watching row
            if (durationMs > 0 && positionMs >= (durationMs * 0.95)) {
                removeWatchNext(context, video.id)
                return@withContext
            }

            // Only track if user actually watched more than 5 seconds
            if (positionMs < 5000L) {
                return@withContext
            }

            val intentUri = Uri.parse("duta://video/${video.id}")
            val desc = video.description.takeIf { it.isNotBlank() && !it.contains("pixel.gif") }
                ?: "Continue watching ${video.title} on DreamStream"

            val rawPrimary = video.backdropUrl.ifEmpty { video.thumbnailUrl }
            val rawSecondary = video.thumbnailUrl.ifEmpty { video.backdropUrl }
            val primaryArt = VideoUtils.getOptimizedBackdrop(rawPrimary, isTV = true, context = context)
            val secondaryArt = VideoUtils.getOptimizedImage(rawSecondary, isTV = true, context = context)

            val builder = WatchNextProgram.Builder()
                .setType(TvContractCompat.WatchNextPrograms.TYPE_MOVIE)
                .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                .setTitle(video.title)
                .setDescription(desc)
                .setIntentUri(intentUri)
                .setInternalProviderId(video.id)
                .setLastPlaybackPositionMillis(positionMs.toInt())
                .setDurationMillis(if (durationMs > 0) durationMs.toInt() else 0)
                .setLastEngagementTimeUtcMillis(System.currentTimeMillis())

            if (primaryArt.isNotBlank()) {
                builder.setPosterArtUri(Uri.parse(primaryArt))
                builder.setThumbnailUri(Uri.parse(secondaryArt.ifBlank { primaryArt }))
                builder.setPosterArtAspectRatio(TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_16_9)
            }

            val existingId = findWatchNextProgramId(context, video.id)
            if (existingId != -1L) {
                val programUri = TvContractCompat.buildWatchNextProgramUri(existingId)
                val rows = context.contentResolver.update(programUri, builder.build().toContentValues(), null, null)
                Log.d(TAG, "Updated WatchNext program for ${video.title} (rows=$rows)")
            } else {
                val insertedUri = context.contentResolver.insert(
                    TvContractCompat.WatchNextPrograms.CONTENT_URI,
                    builder.build().toContentValues()
                )
                Log.d(TAG, "Inserted new WatchNext program for ${video.title} (uri=$insertedUri)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update WatchNext for ${video.title}", e)
        }
    }

    suspend fun removeWatchNext(context: Context, videoId: String) = withContext(Dispatchers.IO) {
        try {
            val existingId = findWatchNextProgramId(context, videoId)
            if (existingId != -1L) {
                val uri = TvContractCompat.buildWatchNextProgramUri(existingId)
                val deleted = context.contentResolver.delete(uri, null, null)
                Log.d(TAG, "Removed WatchNext program for $videoId (deleted=$deleted)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to remove WatchNext for $videoId: ${e.message}")
        }
    }

    suspend fun clearAllWatchNext(context: Context) = withContext(Dispatchers.IO) {
        try {
            val cursor = context.contentResolver.query(
                TvContractCompat.WatchNextPrograms.CONTENT_URI,
                arrayOf(TvContractCompat.WatchNextPrograms._ID),
                null, null, null
            )
            var count = 0
            cursor?.use {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    val uri = TvContractCompat.buildWatchNextProgramUri(id)
                    context.contentResolver.delete(uri, null, null)
                    count++
                }
            }
            Log.i(TAG, "Cleared all WatchNext programs ($count removed)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear all WatchNext programs", e)
        }
    }
}
