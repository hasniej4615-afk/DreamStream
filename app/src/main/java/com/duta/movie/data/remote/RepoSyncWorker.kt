package com.duta.movie.data.remote

import android.content.Context
import android.util.Log
import androidx.work.*
import com.duta.movie.provider.core.ProviderManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RepoSyncEntryPoint {
    fun providerManager(): ProviderManager
}

/**
 * Background WorkManager worker for periodic auto-sync of extensions and repositories.
 * Runs on network connectivity to keep domain mirrors and provider manifests up to date.
 */
class RepoSyncWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "RepoSyncWorker"
        const val UNIQUE_WORK_NAME = "DreamStreamRepoAutoSync"

        fun schedulePeriodicSync(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val syncRequest = PeriodicWorkRequestBuilder<RepoSyncWorker>(6, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    syncRequest
                )
                Log.d(TAG, "Scheduled periodic repo auto-sync (6h interval, network connected)")
            } catch (e: Exception) {
                Log.w(TAG, "Notice: could not schedule periodic repo auto-sync: ${e.message}")
            }
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Executing background auto-sync of repositories and providers")
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                RepoSyncEntryPoint::class.java
            )
            val syncResult = entryPoint.providerManager().syncAllRepositories()
            if (syncResult.isSuccess) {
                Log.d(TAG, "Background auto-sync succeeded: ${syncResult.getOrNull()}")
                Result.success()
            } else {
                Log.w(TAG, "Background auto-sync failed, retrying later: ${syncResult.exceptionOrNull()?.message}")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.w(TAG, "RepoSyncWorker error: ${e.message}")
            Result.retry()
        }
    }
}
