package com.duta.movie.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "installed_repos")
data class InstalledRepoEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String = "",
    val author: String = "",
    val iconUrl: String = "",
    val url: String = "",
    val isOfficial: Boolean = false,
    val installedAt: Long = System.currentTimeMillis(),
    val lastSyncedAt: Long = System.currentTimeMillis()
)
