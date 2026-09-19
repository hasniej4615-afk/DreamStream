package com.duta.movie.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos WHERE isFavorite = 1")
    fun getFavoriteVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE id = :id")
    suspend fun getVideoById(id: String): VideoEntity?

    @Query("SELECT * FROM videos WHERE id IN (:ids)")
    suspend fun getVideosByIds(ids: List<String>): List<VideoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: VideoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideos(videos: List<VideoEntity>)

    @Transaction
    suspend fun insertOrUpdateVideos(videos: List<VideoEntity>) {
        videos.forEach { video ->
            val existing = getVideoById(video.id)
            if (existing != null) {
                // Hard filter for placeholders
                val isNewPlaceholder = video.thumbnailUrl.contains("data:image/") || video.thumbnailUrl.contains("/pixel.gif") || video.thumbnailUrl.contains("/loading.gif")
                val isExistingPlaceholder = existing.thumbnailUrl.contains("data:image/") || existing.thumbnailUrl.contains("/pixel.gif") || existing.thumbnailUrl.contains("/loading.gif")
                
                val betterThumb = when {
                    existing.thumbnailUrl.isNotEmpty() && !isExistingPlaceholder && (video.thumbnailUrl.isEmpty() || isNewPlaceholder) -> existing.thumbnailUrl
                    video.thumbnailUrl.isNotEmpty() && !isNewPlaceholder && (existing.thumbnailUrl.isEmpty() || isExistingPlaceholder) -> video.thumbnailUrl
                    existing.thumbnailUrl.isNotEmpty() && video.thumbnailUrl.isNotEmpty() -> {
                        if (existing.thumbnailUrl.length > video.thumbnailUrl.length + 10 && !existing.thumbnailUrl.contains("-150x")) existing.thumbnailUrl
                        else video.thumbnailUrl
                    }
                    else -> video.thumbnailUrl.ifEmpty { existing.thumbnailUrl }
                }

                val isExistingSynopsis = existing.description.length > 100 && existing.description.contains(" ")
                val isNewSynopsis = video.description.length > 100 && video.description.contains(" ")
                val betterDesc = when {
                    isExistingSynopsis && !isNewSynopsis -> existing.description
                    !isExistingSynopsis && isNewSynopsis -> video.description
                    video.description.length > existing.description.length -> video.description
                    else -> existing.description.ifEmpty { video.description }
                }

                val betterEpisodes = when {
                    video.episodes.isNotEmpty() -> video.episodes
                    video.isSeries == false -> emptyList()
                    video.description.isNotEmpty() -> {
                         val lowUrl = video.videoUrl.lowercase()
                         if (lowUrl.contains("/movie/") || lowUrl.contains("/film/")) emptyList()
                         else existing.episodes
                    }
                    else -> existing.episodes
                }

                val merged = video.copy(
                    thumbnailUrl = betterThumb,
                    description = betterDesc,
                    previewUrl = if (video.previewUrl.isEmpty()) existing.previewUrl else video.previewUrl,
                    backdropUrl = if (video.backdropUrl.isEmpty()) existing.backdropUrl else video.backdropUrl,
                    actresses = if (video.actresses.isEmpty()) existing.actresses else video.actresses,
                    actressPaths = if (video.actressPaths.isEmpty()) existing.actressPaths else video.actressPaths,
                    actressImages = if (video.actressImages.isEmpty()) existing.actressImages else video.actressImages,
                    servers = if (video.servers.isEmpty()) existing.servers else video.servers,
                    episodes = betterEpisodes,
                    season = if (video.season.isNotEmpty()) video.season else existing.season,
                    isSeries = if (betterEpisodes.isNotEmpty()) true else (video.isSeries ?: existing.isSeries),
                    isFavorite = existing.isFavorite,
                    lastWatched = if (video.lastWatched > 0) video.lastWatched else existing.lastWatched
                )
                insertVideo(merged)
            } else {
                insertVideo(video)
            }
        }
    }

    @Query("UPDATE videos SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavoriteStatus(id: String, isFavorite: Boolean)

    @Query("UPDATE videos SET lastWatched = :timestamp WHERE id = :id")
    suspend fun updateLastWatched(id: String, timestamp: Long)

    @Query("SELECT * FROM videos WHERE lastWatched > 0 ORDER BY lastWatched DESC LIMIT 20")
    fun getRecentlyWatched(): Flow<List<VideoEntity>>

    @Transaction
    @Query("SELECT v.* FROM videos v INNER JOIN category_cache c ON v.id = c.videoId WHERE c.categoryPath = :categoryPath ORDER BY c.position ASC")
    suspend fun getCachedVideosByCategory(categoryPath: String): List<VideoEntity>

    @Query("SELECT * FROM videos ORDER BY lastWatched DESC, id DESC LIMIT :limit")
    suspend fun getLatestVideos(limit: Int = 15): List<VideoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategoryCache(items: List<CategoryCacheEntity>)

    @Query("DELETE FROM category_cache WHERE categoryPath = :categoryPath")
    suspend fun clearCategoryCache(categoryPath: String)

    @Query("UPDATE videos SET lastWatched = 0")
    suspend fun clearAllLastWatched()

    @Transaction
    suspend fun updateCategoryCache(categoryPath: String, videos: List<VideoEntity>) {
        insertOrUpdateVideos(videos)
        clearCategoryCache(categoryPath)
        val cacheItems = videos.mapIndexed { index, video ->
            CategoryCacheEntity(categoryPath, video.id, index)
        }
        insertCategoryCache(cacheItems)
    }
}
