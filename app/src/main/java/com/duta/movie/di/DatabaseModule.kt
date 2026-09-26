package com.duta.movie.di

import android.content.Context
import androidx.room.Room
import com.duta.movie.data.local.MovieDatabase
import com.duta.movie.data.local.VideoDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MovieDatabase {
        return Room.databaseBuilder(
            context,
            MovieDatabase::class.java,
            "movie_database"
        )
            .fallbackToDestructiveMigration(true)
            .build()
    }

    @Provides
    fun provideVideoDao(database: MovieDatabase): VideoDao {
        return database.videoDao()
    }

    @Provides
    fun provideRepoDao(database: MovieDatabase): com.duta.movie.data.local.RepoDao {
        return database.repoDao()
    }
}
