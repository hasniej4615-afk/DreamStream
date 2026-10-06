package com.duta.movie.provider.engine

import com.duta.movie.data.local.InstalledProviderEntity
import com.duta.movie.model.LiveTvCatalog
import com.duta.movie.model.Video
import com.duta.movie.model.VideoServer
import com.duta.movie.provider.core.MediaProvider
import com.duta.movie.provider.model.ProviderMediaType

/**
 * Native MediaProvider for Malaysian Live TV channels (RTM Suite & Live Broadcasts).
 * Provides instantaneous 1080p HLS direct feeds without scraping latency.
 */
class LiveTvProvider(
    private val entity: InstalledProviderEntity
) : MediaProvider {

    override val id: String = entity.id
    override val name: String = entity.name
    override val displayName: String = entity.displayName
    override val version: Int = entity.version
    override val versionName: String = entity.versionName
    override val iconUrl: String = entity.iconUrl.ifBlank { "https://raw.githubusercontent.com/hasniej4615-afk/DreamStream/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" }
    override val mediaType: ProviderMediaType = ProviderMediaType.MULTI
    override val isEnabled: Boolean = entity.isEnabled

    override suspend fun search(query: String, page: Int): List<Video> {
        if (query.isBlank()) return LiveTvCatalog.channels
        val q = query.trim().lowercase()
        return LiveTvCatalog.channels.filter { 
            it.title.lowercase().contains(q) || it.description.lowercase().contains(q) 
        }
    }

    override suspend fun fetchSection(path: String, page: Int, count: Int): List<Video> {
        val cleanPath = path.trim().lowercase()
        return if (cleanPath == "/live-tv/" || cleanPath == "live-tv" || cleanPath.contains("live")) {
            LiveTvCatalog.channels
        } else {
            emptyList()
        }
    }

    override suspend fun fetchVideoDetail(video: Video): Video? {
        return LiveTvCatalog.getChannelById(video.id) ?: video
    }

    override suspend fun fetchServers(video: Video): List<VideoServer> {
        val channel = LiveTvCatalog.getChannelById(video.id)
        return channel?.servers ?: video.servers
    }
}
