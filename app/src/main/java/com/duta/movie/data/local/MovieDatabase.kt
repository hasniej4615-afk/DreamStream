package com.duta.movie.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        VideoEntity::class,
        CategoryCacheEntity::class,
        InstalledRepoEntity::class,
        InstalledProviderEntity::class
    ],
    version = 11,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class MovieDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
    abstract fun repoDao(): RepoDao
}
