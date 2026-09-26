package com.duta.movie.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RepoDao {
    @Query("SELECT * FROM installed_repos ORDER BY isOfficial DESC, name ASC")
    fun getAllRepos(): Flow<List<InstalledRepoEntity>>

    @Query("SELECT * FROM installed_repos WHERE id = :id")
    suspend fun getRepoById(id: String): InstalledRepoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateRepo(repo: InstalledRepoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateRepos(repos: List<InstalledRepoEntity>)

    @Query("DELETE FROM installed_repos WHERE id = :id")
    suspend fun deleteRepoById(id: String)

    @Query("SELECT * FROM installed_providers ORDER BY priorityOrder ASC, displayName ASC")
    fun getAllProviders(): Flow<List<InstalledProviderEntity>>

    @Query("SELECT * FROM installed_providers WHERE isEnabled = 1 ORDER BY priorityOrder ASC")
    fun getEnabledProviders(): Flow<List<InstalledProviderEntity>>

    @Query("SELECT * FROM installed_providers WHERE isEnabled = 1 ORDER BY priorityOrder ASC")
    suspend fun getEnabledProvidersSync(): List<InstalledProviderEntity>

    @Query("SELECT * FROM installed_providers WHERE id = :id")
    suspend fun getProviderById(id: String): InstalledProviderEntity?

    @Query("SELECT * FROM installed_providers WHERE repoId = :repoId")
    suspend fun getProvidersByRepo(repoId: String): List<InstalledProviderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProvider(provider: InstalledProviderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProviders(providers: List<InstalledProviderEntity>)

    @Query("UPDATE installed_providers SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun setProviderEnabled(id: String, isEnabled: Boolean)

    @Query("UPDATE installed_providers SET priorityOrder = :priority WHERE id = :id")
    suspend fun setProviderPriority(id: String, priority: Int)

    @Query("DELETE FROM installed_providers WHERE id = :id")
    suspend fun deleteProviderById(id: String)

    @Query("DELETE FROM installed_providers WHERE repoId = :repoId")
    suspend fun deleteProvidersByRepo(repoId: String)
}
