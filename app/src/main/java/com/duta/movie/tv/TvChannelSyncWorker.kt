package com.duta.movie.tv

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.room.Room
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.ChannelLogoUtils
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.duta.movie.R
import com.duta.movie.data.local.MovieDatabase
import com.duta.movie.data.local.toDomain
import com.duta.movie.model.Video
import com.duta.movie.util.DeviceUtils
import com.duta.movie.util.VideoExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TvChannelSyncWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "TvChannelSyncWorker"
        const val CHANNEL_NAME = "DreamStream"
        const val INTERNAL_PROVIDER_ID = "dreamstream_featured"

        /**
         * Checks whether the device supports Android TV Preview Channels.
         * Works across official Android TV, Google TV, and uncertified AOSP TV boxes.
         */
        fun isTvChannelSupported(context: Context): Boolean {
            if (DeviceUtils.isTvDevice(context)) return true
            return try {
                val cursor = context.contentResolver.query(
                    TvContractCompat.Channels.CONTENT_URI,
                    arrayOf(TvContractCompat.Channels._ID),
                    null, null, null
                )
                val supported = cursor != null
                cursor?.close()
                supported
            } catch (e: Exception) {
                Log.w(TAG, "TvProvider not available on this device: ${e.message}")
                false
            }
        }

        fun findExistingChannelId(context: Context): Long {
            try {
                val cursor = context.contentResolver.query(
                    TvContractCompat.Channels.CONTENT_URI,
                    arrayOf(
                        TvContractCompat.Channels._ID,
                        TvContractCompat.Channels.COLUMN_DISPLAY_NAME,
                        TvContractCompat.Channels.COLUMN_INTERNAL_PROVIDER_ID
                    ),
                    null, null, null
                )
                cursor?.use {
                    while (it.moveToNext()) {
                        val idIndex = it.getColumnIndex(TvContractCompat.Channels._ID)
                        val nameIndex = it.getColumnIndex(TvContractCompat.Channels.COLUMN_DISPLAY_NAME)
                        val providerIdIndex = it.getColumnIndex(TvContractCompat.Channels.COLUMN_INTERNAL_PROVIDER_ID)
                        if (idIndex != -1) {
                            val name = if (nameIndex != -1) it.getString(nameIndex) else ""
                            val providerId = if (providerIdIndex != -1) it.getString(providerIdIndex) else ""
                            if (providerId == INTERNAL_PROVIDER_ID || name == CHANNEL_NAME || name == "Featured Movies") {
                                return it.getLong(idIndex)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error finding existing channel", e)
            }
            return -1L
        }

        fun deleteAllChannels(context: Context) {
            try {
                val cursor = context.contentResolver.query(
                    TvContractCompat.Channels.CONTENT_URI,
                    arrayOf(TvContractCompat.Channels._ID),
                    null, null, null
                )
                var count = 0
                cursor?.use {
                    while (it.moveToNext()) {
                        val id = it.getLong(0)
                        val uri = TvContractCompat.buildChannelUri(id)
                        context.contentResolver.delete(uri, null, null)
                        count++
                    }
                }
                Log.i(TAG, "Aggressively deleted $count old channels")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete channels", e)
            }
        }

        fun createChannel(context: Context): Long {
            Log.d(TAG, "Creating new TV channel: $CHANNEL_NAME")
            val channel = Channel.Builder()
                .setType(TvContractCompat.Channels.TYPE_PREVIEW)
                .setDisplayName(CHANNEL_NAME)
                .setInternalProviderId(INTERNAL_PROVIDER_ID)
                .setAppLinkIntentUri(Uri.parse("duta://video/home"))
                .build()

            val channelUri = try {
                context.contentResolver.insert(
                    TvContractCompat.Channels.CONTENT_URI,
                    channel.toContentValues()
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to insert channel into TvProvider", e)
                null
            }

            val channelId = channelUri?.let { ContentUris.parseId(it) } ?: -1L

            if (channelId != -1L) {
                setChannelLogo(context, channelId)
                try {
                    TvContractCompat.requestChannelBrowsable(context, channelId)
                } catch (e: Exception) {
                    Log.w(TAG, "requestChannelBrowsable failed: ${e.message}")
                }
                Log.d(TAG, "Channel created with ID: $channelId")
            }

            return channelId
        }

        fun setChannelLogo(context: Context, channelId: Long) {
            try {
                val drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
                if (drawable != null) {
                    val bitmap = Bitmap.createBitmap(
                        drawable.intrinsicWidth.takeIf { it > 0 } ?: 192,
                        drawable.intrinsicHeight.takeIf { it > 0 } ?: 192,
                        Bitmap.Config.ARGB_8888
                    )
                    val canvas = Canvas(bitmap)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)

                    ChannelLogoUtils.storeChannelLogo(context, channelId, bitmap)
                    Log.d(TAG, "Channel logo set successfully for channel $channelId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set channel logo", e)
            }
        }

        /**
         * Multi-tier movie fetcher:
         * 1. Network scraper for /movie/ (15 items)
         * 2. Room database for cached /movie/ or recent items
         * 3. Built-in Malaysian classics (P. Ramlee etc.)
         * Guaranteed non-empty!
         */
        suspend fun fetchSyncMovies(context: Context): List<Video> = withContext(Dispatchers.IO) {
            // Tier 1: Try network scraper
            try {
                val networkMovies = VideoExtractor.fetchVideosBySection("/movie/", 1, 15)
                if (networkMovies.isNotEmpty()) {
                    Log.d(TAG, "Fetched ${networkMovies.size} movies from network for TV channel")
                    return@withContext networkMovies
                }
            } catch (e: Exception) {
                Log.w(TAG, "Network fetch failed for TV channel: ${e.message}")
            }

            // Tier 2: Try Room database cache
            try {
                val db = Room.databaseBuilder(
                    context.applicationContext,
                    MovieDatabase::class.java,
                    "movie_database"
                ).fallbackToDestructiveMigration(true).build()

                val cached = db.videoDao().getCachedVideosByCategory("/movie/")
                    .ifEmpty { db.videoDao().getCachedVideosByCategory("/") }
                    .ifEmpty { db.videoDao().getLatestVideos(15) }

                db.close()

                if (cached.isNotEmpty()) {
                    Log.d(TAG, "Found ${cached.size} cached movies in Room DB for TV channel")
                    return@withContext cached.map { it.toDomain() }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Room DB fetch failed for TV channel: ${e.message}")
            }

            // Tier 3: Guaranteed fallback classics
            try {
                val classics = VideoExtractor.fetchArchivePramleeVideos()
                if (classics.isNotEmpty()) {
                    Log.d(TAG, "Using ${classics.size} fallback classic movies for TV channel")
                    return@withContext classics.take(15)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Classics fetch failed: ${e.message}")
            }

            emptyList()
        }

        /**
         * Clears old programs and populates channel with rich movie cards:
         * 16:9 poster/backdrop artwork, title, synopsis, deep links, and sorting weights.
         */
        fun populateProgramsForChannel(context: Context, channelId: Long, movies: List<Video>): Int {
            if (movies.isEmpty() || channelId == -1L) return 0

            // Step 1: Wipe all old/placeholder programs (including any dummy cards)
            try {
                val programsUri = TvContractCompat.buildPreviewProgramsUriForChannel(channelId)
                val deleted = context.contentResolver.delete(programsUri, null, null)
                Log.d(TAG, "Cleared $deleted old programs from TV channel $channelId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear old programs", e)
            }

            var addedCount = 0
            movies.forEachIndexed { index, video ->
                if (video.title.isBlank()) return@forEachIndexed
                try {
                    val intentUri = Uri.parse("duta://video/${video.id}")
                    val desc = video.description.takeIf { it.isNotBlank() && !it.contains("pixel.gif") }
                        ?: "Watch ${video.title} on DreamStream"

                    val backdrop = video.backdropUrl.takeIf { it.isNotBlank() && !it.contains("pixel.gif") }
                    val thumb = video.thumbnailUrl.takeIf { it.isNotBlank() && !it.contains("pixel.gif") }
                    val rawPrimary = backdrop ?: thumb ?: ""
                    val rawSecondary = thumb ?: backdrop ?: ""
                    val primaryArt = com.duta.movie.util.VideoUtils.getOptimizedBackdrop(rawPrimary, isTV = true, context = context)
                    val secondaryArt = com.duta.movie.util.VideoUtils.getOptimizedImage(rawSecondary, isTV = true, context = context)

                    val builder = PreviewProgram.Builder()
                        .setChannelId(channelId)
                        .setTitle(video.title)
                        .setDescription(desc)
                        .setIntentUri(intentUri)
                        .setType(TvContractCompat.PreviewPrograms.TYPE_MOVIE)
                        .setInternalProviderId(video.id)
                        .setWeight(movies.size - index)
                        .setLive(false)

                    if (primaryArt.isNotBlank()) {
                        builder.setPosterArtUri(Uri.parse(primaryArt))
                        builder.setThumbnailUri(Uri.parse(secondaryArt.ifBlank { primaryArt }))
                        builder.setPosterArtAspectRatio(TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_16_9)
                    }

                    val inserted = context.contentResolver.insert(
                        TvContractCompat.PreviewPrograms.CONTENT_URI,
                        builder.build().toContentValues()
                    )
                    if (inserted != null) {
                        addedCount++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to add program: ${video.title}", e)
                }
            }
            Log.d(TAG, "Successfully populated $addedCount/${movies.size} programs for channel $channelId")
            return addedCount
        }

        /**
         * Synchronizes TV channel immediately.
         * Safe to call from UI, Activity launch, or background worker.
         */
        suspend fun syncChannelDirectly(context: Context, moviesInput: List<Video>? = null): Boolean = withContext(Dispatchers.IO) {
            if (!isTvChannelSupported(context)) {
                Log.i(TAG, "Device does not support TV channels. Skipping sync.")
                return@withContext false
            }

            try {
                var channelId = findExistingChannelId(context)
                if (channelId == -1L) {
                    Log.d(TAG, "No channel found. Creating new channel.")
                    deleteAllChannels(context)
                    channelId = createChannel(context)
                } else {
                    // Refresh logo and browsable status
                    setChannelLogo(context, channelId)
                    try {
                        TvContractCompat.requestChannelBrowsable(context, channelId)
                    } catch (_: Exception) {}
                }

                if (channelId == -1L) {
                    Log.e(TAG, "Failed to find or create TV channel")
                    return@withContext false
                }

                val movies = if (!moviesInput.isNullOrEmpty()) {
                    moviesInput
                } else {
                    fetchSyncMovies(context)
                }

                if (movies.isEmpty()) {
                    Log.w(TAG, "No movies available for TV channel sync")
                    return@withContext false
                }

                val added = populateProgramsForChannel(context, channelId, movies)
                Log.i(TAG, "TV Channel sync succeeded with $added movies")
                added > 0
            } catch (e: Exception) {
                Log.e(TAG, "Error in syncChannelDirectly", e)
                false
            }
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!isTvChannelSupported(context)) {
            Log.i(TAG, "Device is not TV channel capable. Skipping TV Channel Sync.")
            return@withContext Result.success()
        }
        try {
            val success = syncChannelDirectly(context)
            if (success) Result.success() else Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "Error in TvChannelSyncWorker doWork", e)
            Result.retry()
        }
    }
}
