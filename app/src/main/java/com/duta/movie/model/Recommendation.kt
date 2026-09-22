package com.duta.movie.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class Recommendation(
    val id: Long? = null,
    @SerialName("video_id") val videoId: String,
    val title: String,
    @SerialName("thumbnail_url") val thumbnailUrl: String = "",
    @SerialName("video_url") val videoUrl: String = "",
    val quality: String = "",
    @SerialName("recommend_count") val recommendCount: Int = 1,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

fun Recommendation.toVideo(): Video = Video(
    id = videoId,
    title = title,
    thumbnailUrl = thumbnailUrl,
    videoUrl = com.duta.movie.util.VideoExtractor.resolveVideoUrl(videoId, videoUrl),
    quality = quality,
    duration = "",
    views = recommendCount.toString()
)
