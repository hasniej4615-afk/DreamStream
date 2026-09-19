package com.duta.movie.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.duta.movie.model.Video

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey val id: String,
    val title: String,
    val thumbnailUrl: String,
    val backdropUrl: String = "",
    val videoUrl: String,
    val duration: String,
    val views: String,
    val date: String,
    val quality: String,
    val season: String = "",
    val previewUrl: String = "",
    val description: String = "",
    val imdbId: String? = null,
    val altTitle: String? = null,
    val actresses: List<String>,
    val actressPaths: Map<String, String>,
    val actressImages: Map<String, String> = emptyMap(),
    val episodes: List<com.duta.movie.model.Episode> = emptyList(),
    val servers: List<com.duta.movie.model.VideoServer> = emptyList(),
    val isFavorite: Boolean = false,
    val lastWatched: Long = 0,
    val isSeries: Boolean? = null
)

fun VideoEntity.toDomain(): Video = Video(
    id = id,
    title = title,
    thumbnailUrl = thumbnailUrl,
    backdropUrl = backdropUrl,
    videoUrl = videoUrl,
    duration = duration,
    views = views,
    date = date,
    quality = quality,
    season = season,
    previewUrl = previewUrl,
    description = description,
    imdbId = imdbId,
    altTitle = altTitle,
    actresses = actresses,
    actressPaths = actressPaths,
    actressImages = actressImages,
    servers = servers.filter { 
        val lowU = it.url.lowercase()
        val lowN = it.name.lowercase()
        !lowU.contains("google.com") && !lowU.contains("pagead") && !lowU.contains("/aclk") &&
        !lowN.contains("google.com") && !lowN.contains("pagead")
    },
    episodes = episodes,
    isSeries = isSeries
)

fun Video.toEntity(isFavorite: Boolean = false, lastWatched: Long = 0): VideoEntity = VideoEntity(
    id = id,
    title = title,
    thumbnailUrl = thumbnailUrl,
    backdropUrl = backdropUrl,
    videoUrl = videoUrl,
    duration = duration,
    views = views,
    date = date,
    quality = quality,
    season = season,
    previewUrl = previewUrl,
    description = description,
    imdbId = imdbId,
    altTitle = altTitle,
    actresses = actresses,
    actressPaths = actressPaths,
    actressImages = actressImages,
    episodes = episodes,
    servers = servers,
    isFavorite = isFavorite,
    lastWatched = lastWatched,
    isSeries = isSeries
)
