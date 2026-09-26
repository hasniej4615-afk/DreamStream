package com.duta.movie.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "installed_providers")
data class InstalledProviderEntity(
    @PrimaryKey
    val id: String,
    val repoId: String,
    val name: String,
    val displayName: String,
    val description: String = "",
    val author: String = "",
    val version: Int = 1,
    val versionName: String = "1.0.0",
    val iconUrl: String = "",
    val mediaType: String = "MULTI",
    val engineType: String = "TEMPLATE",
    val templateType: String = "WORDPRESS_MUVIPRO",
    val baseUrlsJson: String = "[]",
    val configJson: String = "{}",
    val pluginUrl: String = "",
    val status: String = "ACTIVE",
    val isEnabled: Boolean = true,
    val priorityOrder: Int = 0,
    val installedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
