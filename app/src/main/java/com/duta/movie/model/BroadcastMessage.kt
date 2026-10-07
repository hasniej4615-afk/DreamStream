package com.duta.movie.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BroadcastMessage(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val author: String = "Admin",
    val type: String = "info", // "info", "warning", "alert"
    val timestamp: Long = 0L,
    @SerialName("is_active")
    val isActive: Boolean = true
)
