package com.duta.movie.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "category_cache",
    primaryKeys = ["categoryPath", "videoId"],
    foreignKeys = [
        ForeignKey(
            entity = VideoEntity::class,
            parentColumns = ["id"],
            childColumns = ["videoId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["videoId"])]
)
data class CategoryCacheEntity(
    val categoryPath: String,
    val videoId: String,
    val position: Int
)
