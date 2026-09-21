package com.duta.movie.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Comment(
    val id: Long = 0L,
    @SerialName("video_id")
    val videoId: String,
    @SerialName("user_name")
    val userName: String,
    val comment: String,
    @SerialName("created_at")
    val createdAt: String = ""
)
