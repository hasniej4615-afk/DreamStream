package com.duta.movie.data

import android.util.Log
import com.duta.movie.data.local.VideoDao
import com.duta.movie.data.local.VideoEntity
import com.duta.movie.data.local.toDomain
import com.duta.movie.data.local.toEntity
import com.duta.movie.model.Subtitle
import com.duta.movie.model.Video
import com.duta.movie.util.VideoExtractor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoRepository @Inject constructor(
    private val videoDao: VideoDao,
    private val preferenceManager: PreferenceManager,
    @param:ApplicationContext private val context: Context
) {
    private val videoCache = ConcurrentHashMap<String, Video>()
    private val backgroundLoadingIds = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    var isPlaybackActive: () -> Boolean = { false }

    val recentlyWatchedVideos: Flow<List<Video>> = videoDao.getRecentlyWatched().map { it.map { e -> e.toDomain() } }
    val favoriteVideos: Flow<List<Video>> = videoDao.getFavoriteVideos().map { it.map { e -> e.toDomain() } }
    val myList: Flow<Set<String>> = favoriteVideos.map { it.map { v -> v.id }.toSet() }
    val searchHistory: Flow<List<String>> = preferenceManager.searchHistory
    val defaultSubtitleLanguage: Flow<String> = preferenceManager.defaultSubtitleLanguage
    val isAutoSubtitleEnabled: Flow<Boolean> = preferenceManager.isAutoSubtitleEnabled

    fun clearAllCaches() {
        videoCache.clear()
        backgroundLoadingIds.clear()
    }

    suspend fun clearSearchHistory() { preferenceManager.clearSearchHistory() }
    suspend fun clearRecentlyWatched() { videoDao.clearAllLastWatched() }
    suspend fun addSearchQuery(query: String) { preferenceManager.addSearchQuery(query) }

    suspend fun fetchAllActresses(): List<Map<String, String>> = VideoExtractor.fetchAllActresses()
    suspend fun fetchActressProfile(path: String): Map<String, String> = VideoExtractor.fetchActressProfile(path)

    suspend fun saveVideoProgress(videoId: String, position: Long) {
        videoDao.updateLastWatched(videoId, System.currentTimeMillis())
        preferenceManager.saveVideoProgress(videoId, position)
    }

    suspend fun saveVideoDuration(videoId: String, duration: Long) {
        preferenceManager.saveVideoDuration(videoId, duration)
    }

    suspend fun setSafeModeEnabled(enabled: Boolean) {
        preferenceManager.setSafeModeEnabled(enabled)
    }

    suspend fun setSafeModePin(pin: String) {
        preferenceManager.setSafeModePin(pin)
    }

    fun verifyPin(input: String, pin: String): Boolean {
        return preferenceManager.verifyPin(input, pin)
    }

    suspend fun setDefaultSubtitleLanguage(language: String) {
        preferenceManager.setDefaultSubtitleLanguage(language)
    }

    suspend fun setAutoSubtitleEnabled(enabled: Boolean) {
        preferenceManager.setAutoSubtitleEnabled(enabled)
    }

    suspend fun toggleMyList(videoId: String) {
        val video = videoDao.getVideoById(videoId)
        if (video != null) {
            videoDao.updateFavoriteStatus(videoId, !video.isFavorite)
        }
    }

    suspend fun addToRecentlyWatched(videoId: String) {
        videoDao.updateLastWatched(videoId, System.currentTimeMillis())
    }

    fun getVideoProgress(videoId: String): Flow<Long> = preferenceManager.getVideoProgress(videoId)
    fun getVideoDuration(videoId: String): Flow<Long> = preferenceManager.getVideoDuration(videoId)
    fun getWatchedEpisodes(videoId: String): Flow<Set<String>> = preferenceManager.getWatchedEpisodes(videoId)

    fun getCachedVideo(id: String): Video? = videoCache[id]
    
    suspend fun getVideo(id: String): Video? = videoCache[id] ?: videoDao.getVideoById(id)?.toDomain()?.also { videoCache[id] = it }
    
    suspend fun updateVideoInDb(video: Video) {
        // Smart RAM Guard: Prevent repository cache from bloating
        val memoryClass = com.duta.movie.util.VideoUtils.getMemoryClass(context)
        val maxItems = if (memoryClass <= 128) 200 else if (memoryClass <= 256) 400 else 1000
        
        if (videoCache.size > maxItems) {
            Log.w("VideoRepository", "Purging video cache due to size (${videoCache.size})")
            videoCache.clear()
        }
        videoCache[video.id] = video
        withContext(Dispatchers.IO) {
            videoDao.insertOrUpdateVideos(listOf(video.toEntity()))
        }
    }

    suspend fun getCachedVideosByCategory(cat: String): List<Video> {
        val cached = videoDao.getCachedVideosByCategory(cat).map { it.toDomain().also { v -> videoCache[v.id] = v } }
        val needsHealing = cached.filter { !VideoExtractor.isValidImageUrl(it.thumbnailUrl) && !VideoExtractor.isValidImageUrl(it.backdropUrl) }
        if (needsHealing.isNotEmpty() && !isPlaybackActive()) {
            CoroutineScope(Dispatchers.IO).launch {
                needsHealing.take(15).forEach { item ->
                    try {
                        val poster = VideoExtractor.findPosterForTitle(item.title, item.date)
                        if (VideoExtractor.isValidImageUrl(poster)) {
                            val healed = item.copy(thumbnailUrl = poster, backdropUrl = poster)
                            updateVideoInDb(healed)
                        }
                    } catch (_: Exception) {}
                }
            }
        }
        return VideoExtractor.sortVideosByNewestRelease(cached)
    }

    suspend fun fetchVideosBySection(cat: String, page: Int, count: Int): List<Video> = coroutineScope {
        val results = VideoExtractor.fetchVideosBySection(cat, page, count)
        val dbItems = videoDao.getVideosByIds(results.map { it.id }).associateBy { it.id }
        val videos = VideoExtractor.sortVideosByNewestRelease(
            results.map { mergeVideos(it, dbItems[it.id]?.toDomain()).also { v -> videoCache[v.id] = v } }
        )
        if (page == 1) videoDao.updateCategoryCache(cat, videos.map { it.toEntity() })
        else videoDao.insertOrUpdateVideos(videos.map { it.toEntity() })
        videos
    }

    suspend fun searchVideos(query: String, page: Int, count: Int, categoryPath: String? = null): List<Video> = coroutineScope {
        val results = VideoExtractor.searchVideos(query, page, count, categoryPath)
        val dbItems = videoDao.getVideosByIds(results.map { it.id }).associateBy { it.id }
        val videos = results.map { mergeVideos(it, dbItems[it.id]?.toDomain()).also { v -> videoCache[v.id] = v } }
        videoDao.insertOrUpdateVideos(videos.map { it.toEntity() })
        videos
    }

    suspend fun insertOrUpdateVideos(videos: List<Video>) = withContext(Dispatchers.IO) {
        if (videos.isEmpty()) return@withContext
        videos.forEach { videoCache[it.id] = it }
        videoDao.insertOrUpdateVideos(videos.map { it.toEntity() })
    }

    suspend fun getCachedOrDbVideo(videoId: String): Video? = withContext(Dispatchers.IO) {
        videoCache[videoId] ?: videoDao.getVideoById(videoId)?.toDomain()
    }

    suspend fun fetchVideoDetails(videoId: String): Video? = withContext(Dispatchers.IO) {
        try {
            val video = videoCache[videoId] ?: videoDao.getVideoById(videoId)?.toDomain()
            if (video == null) {
                if (videoId.startsWith("yt_") || videoId.startsWith("bili_") || videoId.startsWith("dm_")) {
                    return@withContext null
                }
                if (videoId.startsWith("ia_pramlee")) {
                    val classic = VideoExtractor.fetchArchivePramleeVideos().find { it.id == videoId }
                    if (classic != null) {
                        videoDao.insertOrUpdateVideos(listOf(classic.toEntity()))
                        videoCache[videoId] = classic
                        return@withContext classic
                    }
                    return@withContext null
                }

                val fallbackUrl = VideoExtractor.resolveVideoUrl(videoId)
                if (fallbackUrl.isNotBlank()) {
                    val fetched = VideoExtractor.fetchVideoDetails(fallbackUrl)
                    if (fetched != null) {
                        var toSave = fetched.copy(id = videoId)
                        if (toSave.thumbnailUrl.isEmpty()) {
                            val fallbackPoster = VideoExtractor.findPosterForTitle(toSave.title, toSave.date)
                            if (fallbackPoster.isNotEmpty()) {
                                toSave = toSave.copy(
                                    thumbnailUrl = fallbackPoster,
                                    backdropUrl = toSave.backdropUrl.ifEmpty { fallbackPoster }
                                )
                            }
                        }
                        videoDao.insertOrUpdateVideos(listOf(toSave.toEntity()))
                        videoCache[videoId] = toSave
                        return@withContext toSave
                    }
                }
                return@withContext null
            }
            
            if (videoId.startsWith("yt_") || videoId.startsWith("bili_") || videoId.startsWith("dm_")) {
                videoCache[videoId] = video
                return@withContext video
            }

            if (videoId.startsWith("ia_pramlee")) {
                val classic = VideoExtractor.fetchArchivePramleeVideos().find { it.id == videoId }
                if (classic != null) {
                    val merged = mergeVideos(classic, video)
                    videoDao.insertOrUpdateVideos(listOf(merged.toEntity()))
                    videoCache[videoId] = merged
                    return@withContext merged
                }
            }
            
            val targetUrl = VideoExtractor.resolveVideoUrl(videoId, video.videoUrl)
            val updated = if (targetUrl.isNotBlank()) VideoExtractor.fetchVideoDetails(targetUrl) else null
            if (updated != null) {
                var merged = mergeVideos(updated, video)
                if (merged.servers.isEmpty() && merged.isSeries != true) {
                    val healed = VideoExtractor.healVideoFromAlternativeSources(merged)
                    if (healed != null && healed.servers.isNotEmpty()) {
                        merged = mergeVideos(healed, merged)
                    }
                }
                if (merged.thumbnailUrl.isEmpty()) {
                    val fallbackPoster = VideoExtractor.findPosterForTitle(merged.title, merged.date)
                    if (fallbackPoster.isNotEmpty()) {
                        merged = merged.copy(
                            thumbnailUrl = fallbackPoster,
                            backdropUrl = merged.backdropUrl.ifEmpty { fallbackPoster }
                        )
                    }
                }
                videoDao.insertOrUpdateVideos(listOf(merged.toEntity()))
                videoCache[videoId] = merged
                return@withContext merged
            } else {
                val healed = VideoExtractor.healVideoFromAlternativeSources(video)
                if (healed != null) {
                    var merged = mergeVideos(healed, video)
                    if (merged.thumbnailUrl.isEmpty()) {
                        val fallbackPoster = VideoExtractor.findPosterForTitle(merged.title, merged.date)
                        if (fallbackPoster.isNotEmpty()) {
                            merged = merged.copy(
                                thumbnailUrl = fallbackPoster,
                                backdropUrl = merged.backdropUrl.ifEmpty { fallbackPoster }
                            )
                        }
                    }
                    videoDao.insertOrUpdateVideos(listOf(merged.toEntity()))
                    videoCache[videoId] = merged
                    return@withContext merged
                } else if (video.thumbnailUrl.isEmpty()) {
                    val fallbackPoster = VideoExtractor.findPosterForTitle(video.title, video.date)
                    if (fallbackPoster.isNotEmpty()) {
                        val updatedVideo = video.copy(
                            thumbnailUrl = fallbackPoster,
                            backdropUrl = video.backdropUrl.ifEmpty { fallbackPoster }
                        )
                        videoDao.insertOrUpdateVideos(listOf(updatedVideo.toEntity()))
                        videoCache[videoId] = updatedVideo
                        return@withContext updatedVideo
                    }
                }
            }
        } catch (_: Exception) {}
        null
    }

    suspend fun loadBackgroundDetails(videos: List<Video>, onUpdate: (List<Video>) -> Unit) = coroutineScope {
        val toUpdate = videos.filter { 
            !it.id.startsWith("yt_") && !it.id.startsWith("bili_") && !it.id.startsWith("dm_") &&
            (it.description.isEmpty() || it.servers.isEmpty()) && backgroundLoadingIds.add(it.id) 
        }
        if (toUpdate.isEmpty()) return@coroutineScope
        
        val batch = mutableListOf<Video>()
        val semaphore = Semaphore(3) // Process 3 at a time for speed
        
        toUpdate.forEach { v ->
            if (!isActive || isPlaybackActive()) return@coroutineScope
            launch(Dispatchers.IO) {
                if (isPlaybackActive() || !isActive) return@launch
                semaphore.withPermit {
                    if (isPlaybackActive() || !isActive) return@withPermit
                    try {
                        val updated = VideoExtractor.fetchVideoDetails(VideoExtractor.migrateUrlToBase(v.videoUrl))
                        if (updated != null) {
                            val merged = mergeVideos(updated, v)
                            videoDao.insertOrUpdateVideos(listOf(merged.toEntity()))
                            synchronized(batch) {
                                batch.add(merged)
                                if (batch.size >= 5) {
                                    val toNotify = batch.toList()
                                    batch.clear()
                                    launch(Dispatchers.Main) { onUpdate(toNotify) }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
                delay(500) // Small rate limit
            }
        }
        
        // Final flush for remaining items in batch
        launch(Dispatchers.IO) {
            // Wait for all detail tasks to finish joining, then flush
            toUpdate.forEach { _ -> /* wait implicitly */ }
            delay(5000) // Rough buffer
            synchronized(batch) {
                if (batch.isNotEmpty()) {
                    val toNotify = batch.toList()
                    batch.clear()
                    launch(Dispatchers.Main) { onUpdate(toNotify) }
                }
            }
        }
    }

    suspend fun fetchCategories(): List<Map<String, String>> = VideoExtractor.fetchCategories()

    private fun mergeVideos(new: Video, old: Video?): Video {
        if (old == null) return new.copy(title = VideoExtractor.cleanTitle(new.title))
        
        val isPramlee = new.id.startsWith("ia_pramlee")
        val isOldThumbValid = VideoExtractor.isValidImageUrl(old.thumbnailUrl)
        val isNewThumbValid = VideoExtractor.isValidImageUrl(new.thumbnailUrl)
        val isNewThumbBetter = isNewThumbValid && (
            isPramlee ||
            !isOldThumbValid || 
            (new.thumbnailUrl.contains("tmdb.org") && !old.thumbnailUrl.contains("tmdb.org")) ||
            (!new.thumbnailUrl.contains("resize=") && old.thumbnailUrl.contains("resize="))
        )
        val resolvedThumbnail = when {
            isNewThumbBetter -> new.thumbnailUrl
            isOldThumbValid -> old.thumbnailUrl
            isNewThumbValid -> new.thumbnailUrl
            VideoExtractor.isValidImageUrl(new.backdropUrl) -> new.backdropUrl
            VideoExtractor.isValidImageUrl(old.backdropUrl) -> old.backdropUrl
            else -> ""
        }

        val isOldBackdropValid = VideoExtractor.isValidImageUrl(old.backdropUrl)
        val isNewBackdropValid = VideoExtractor.isValidImageUrl(new.backdropUrl)
        val isNewBackdropBetter = isNewBackdropValid && (
            isPramlee ||
            !isOldBackdropValid ||
            (new.backdropUrl.contains("tmdb.org") && !old.backdropUrl.contains("tmdb.org")) ||
            (!new.backdropUrl.contains("resize=") && old.backdropUrl.contains("resize="))
        )
        val resolvedBackdrop = when {
            isNewBackdropBetter -> new.backdropUrl
            isOldBackdropValid -> old.backdropUrl
            isNewBackdropValid -> new.backdropUrl
            resolvedThumbnail.isNotEmpty() -> resolvedThumbnail
            else -> ""
        }
        
        val cleanedNewTitle = VideoExtractor.cleanTitle(new.title)
        val cleanedOldTitle = VideoExtractor.cleanTitle(old.title)

        val targetSeasonNum = Regex("""(?i)\b(?:season|s)[-_ ]?(\d+)\b""").find(new.videoUrl)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("""(?i)\b(?:season|s)\s*(\d+)\b""").find(new.title)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("""(?i)\b(?:season|s)[-_ ]?(\d+)\b""").find(old.videoUrl)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("""(?i)\b(?:season|s)\s*(\d+)\b""").find(old.title)?.groupValues?.get(1)?.toIntOrNull()

        val oldEpisodesValid = if (targetSeasonNum != null && old.episodes.isNotEmpty()) {
            old.episodes.none { ep ->
                val epSeasonNum = Regex("""(?i)\b(?:season|s)[-_ ]?(\d+)\b""").find(ep.season.ifEmpty { ep.url })?.groupValues?.get(1)?.toIntOrNull()
                epSeasonNum != null && epSeasonNum != targetSeasonNum
            }
        } else true

        val resolvedEpisodes = when {
            new.episodes.isNotEmpty() -> new.episodes
            new.isSeries == false -> emptyList()
            oldEpisodesValid -> old.episodes
            else -> emptyList()
        }
        val resolvedIsSeries = when {
            resolvedEpisodes.isNotEmpty() -> true
            new.isSeries != null -> new.isSeries
            else -> old.isSeries
        }
        val resolvedSeason = when {
            new.season.isNotEmpty() -> new.season
            else -> old.season
        }

        return old.copy(
            title = if (cleanedNewTitle.length > cleanedOldTitle.length) cleanedNewTitle else cleanedOldTitle,
            videoUrl = if (isPramlee && new.videoUrl.isNotEmpty()) new.videoUrl else old.videoUrl,
            thumbnailUrl = if (resolvedThumbnail.isNotEmpty()) resolvedThumbnail else resolvedBackdrop,
            backdropUrl = if (resolvedBackdrop.isNotEmpty()) resolvedBackdrop else resolvedThumbnail,
            actresses = (new.actresses + old.actresses).distinct().filter { it.isNotEmpty() },
            actressPaths = (old.actressPaths + new.actressPaths),
            actressImages = (old.actressImages + new.actressImages),
            duration = if (old.duration == "??:??" || old.duration.isEmpty()) new.duration else old.duration,
            date = old.date.ifEmpty { new.date },
            quality = old.quality.ifEmpty { new.quality },
            season = resolvedSeason,
            description = if (isPramlee && new.description.isNotEmpty()) new.description else if (new.description.length > old.description.length) new.description else old.description,
            previewUrl = if (new.previewUrl.isNotEmpty()) new.previewUrl else old.previewUrl,
            servers = (if (isPramlee) new.servers else (new.servers + old.servers).distinctBy { it.url.trimEnd('/') }).filter {
                val lowU = it.url.lowercase()
                val lowN = it.name.lowercase()
                !lowU.contains("google.com") && !lowU.contains("pagead") && !lowU.contains("/aclk") &&
                !lowN.contains("google.com") && !lowN.contains("pagead")
            }.sortedByDescending { VideoExtractor.getProviderPriority(it.name, it.url) },
            episodes = resolvedEpisodes,
            isSeries = resolvedIsSeries,
            imdbId = if (new.imdbId?.isNotEmpty() == true) new.imdbId else old.imdbId
        )
    }
}
