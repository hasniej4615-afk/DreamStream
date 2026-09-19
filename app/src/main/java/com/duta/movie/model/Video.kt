package com.duta.movie.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class VideoServer(
    val name: String,
    val url: String
)

@Serializable
@Immutable
data class Episode(
    val id: String,
    val name: String,
    val url: String,
    val date: String = "",
    val season: String = ""
)

@Immutable
data class Video(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val backdropUrl: String = "",
    val videoUrl: String,
    val duration: String,
    val views: String = "",
    val date: String = "",
    val quality: String = "",
    val season: String = "",
    val imdbId: String? = null,
    val altTitle: String? = null,
    val description: String = "",
    val previewUrl: String = "",
    val actresses: List<String> = emptyList(),
    val actressPaths: Map<String, String> = emptyMap(), // Name to Path mapping
    val actressImages: Map<String, String> = emptyMap(), // Name to Image mapping
    val servers: List<VideoServer> = emptyList(),
    val episodes: List<Episode> = emptyList(),
    val isSeries: Boolean? = null // null = unknown, true = series, false = movie
)

@Immutable
data class ActressProfile(
    val name: String,
    val image: String,
    val bio: String,
    val metadata: List<String> = emptyList()
)
