package com.duta.movie.provider.engine

import android.util.Log
import com.duta.movie.data.local.InstalledProviderEntity
import com.duta.movie.model.Video
import com.duta.movie.model.VideoServer
import com.duta.movie.provider.core.MediaProvider
import com.duta.movie.provider.model.ProviderMediaType
import com.duta.movie.util.VideoExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class TemplateProvider(
    private val entity: InstalledProviderEntity
) : MediaProvider {

    companion object {
        private const val TAG = "TemplateProvider"
    }

    override val id: String = entity.id
    override val name: String = entity.name
    override val displayName: String = entity.displayName
    override val version: Int = entity.version
    override val versionName: String = entity.versionName
    override val iconUrl: String = entity.iconUrl
    override val mediaType: ProviderMediaType = try {
        ProviderMediaType.valueOf(entity.mediaType)
    } catch (_: Exception) {
        ProviderMediaType.MULTI
    }
    override val isEnabled: Boolean = entity.isEnabled

    private val baseUrls: List<String> by lazy {
        try {
            val json = Json { ignoreUnknownKeys = true }
            val element = json.parseToJsonElement(entity.baseUrlsJson)
            element.jsonArray.mapNotNull { it.jsonPrimitive.content }
        } catch (_: Exception) {
            emptyList()
        }
    }

    init {
        // Apply remote base URLs if configured in Supabase
        applyConfiguredBaseUrls()
    }

    private fun applyConfiguredBaseUrls() {
        if (baseUrls.isEmpty()) return
        val primary = baseUrls.firstOrNull() ?: return
        when (entity.templateType) {
            "PENCURI" -> {
                VideoExtractor.updatePencuriBaseUrl(primary)
            }
            "DUTAFILM" -> {
                val webUrl = baseUrls.firstOrNull { it.startsWith("http") && !it.contains("159.89.249.45") } ?: primary
                if (webUrl.startsWith("http") && !webUrl.contains("159.89.249.45")) {
                    VideoExtractor.setDutaFilmWebBaseUrl(webUrl)
                }
            }
            "WORDPRESS_MUVIPRO" -> {
                if (primary.startsWith("http")) {
                    VideoExtractor.setBullerswoodBaseUrl(primary)
                }
            }
        }
    }

    override suspend fun search(query: String, page: Int): List<Video> = withContext(Dispatchers.IO) {
        if (!isEnabled) return@withContext emptyList()
        try {
            val list = when (entity.templateType) {
                "PENCURI" -> {
                    VideoExtractor.searchVideos(query, page, 25)
                }
                "DUTAFILM" -> {
                    VideoExtractor.searchDutaFilmWeb(query, page, 25)
                }
                "WORDPRESS_MUVIPRO" -> {
                    VideoExtractor.searchBullerswood(query, page, 25)
                }
                "GENERIC_HTML" -> {
                    if (id.contains("pramlee", ignoreCase = true)) {
                        val all = VideoExtractor.fetchArchivePramleeVideos()
                        all.filter { it.title.contains(query, ignoreCase = true) }
                    } else {
                        VideoExtractor.searchVideos(query, page, 25)
                    }
                }
                else -> {
                    VideoExtractor.searchVideos(query, page, 25)
                }
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Search error on provider $name: ${e.message}")
            emptyList()
        }
    }

    override suspend fun fetchSection(path: String, page: Int, count: Int): List<Video> = withContext(Dispatchers.IO) {
        if (!isEnabled) return@withContext emptyList()
        try {
            val list = when (entity.templateType) {
                "DUTAFILM" -> {
                    VideoExtractor.fetchDutaFilmWebVideos(path, page, count)
                }
                "WORDPRESS_MUVIPRO" -> {
                    VideoExtractor.fetchBullerswoodVideos(path, page, count)
                }
                "GENERIC_HTML" -> {
                    if (id.contains("pramlee", ignoreCase = true)) {
                        val all = VideoExtractor.sortVideosByNewestRelease(VideoExtractor.fetchArchivePramleeVideos())
                        val itemsPerPage = 15
                        val start = (page - 1) * itemsPerPage
                        if (start >= all.size) emptyList() else all.drop(start).take(count)
                    } else {
                        VideoExtractor.fetchVideosBySection(path, page, count)
                    }
                }
                else -> {
                    VideoExtractor.fetchVideosBySection(path, page, count)
                }
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Section fetch error on provider $name for $path: ${e.message}")
            emptyList()
        }
    }

    override suspend fun fetchVideoDetail(video: Video): Video? = withContext(Dispatchers.IO) {
        try {
            VideoExtractor.fetchVideoDetails(video.videoUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Detail fetch error on provider $name: ${e.message}")
            null
        }
    }

    override suspend fun fetchServers(video: Video): List<VideoServer> = withContext(Dispatchers.IO) {
        try {
            if (video.servers.isNotEmpty()) return@withContext video.servers
            val detailed = VideoExtractor.fetchVideoDetails(video.videoUrl)
            detailed?.servers ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Servers fetch error on provider $name: ${e.message}")
            emptyList()
        }
    }
}
