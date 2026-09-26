package com.duta.movie.provider.core

import com.duta.movie.model.Video
import com.duta.movie.model.VideoServer
import com.duta.movie.provider.model.ProviderMediaType

/**
 * Standard interface for all media providers (sources) in DreamStream.
 * Inspired by CloudStream's MainAPI, adapted for native Compose & Media3.
 */
interface MediaProvider {
    val id: String
    val name: String
    val displayName: String
    val version: Int
    val versionName: String
    val iconUrl: String
    val mediaType: ProviderMediaType
    val isEnabled: Boolean

    /**
     * Search movies / series on this provider
     */
    suspend fun search(query: String, page: Int = 1): List<Video>

    /**
     * Fetch videos for a specific category section or home row
     */
    suspend fun fetchSection(path: String, page: Int = 1, count: Int = 30): List<Video>

    /**
     * Resolve full details (synopsis, cast, backdrop, episodes) for a video
     */
    suspend fun fetchVideoDetail(video: Video): Video?

    /**
     * Resolve playable streaming servers / qualities for a video
     */
    suspend fun fetchServers(video: Video): List<VideoServer>
}
