package com.duta.movie.data.remote

import android.util.Log
import com.duta.movie.provider.model.CloudStreamRepoManifest
import com.duta.movie.provider.model.RemoteProviderManifest
import com.duta.movie.provider.model.RemoteRepository
import com.duta.movie.util.SupabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RepoService @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "RepoService"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Fetches all registered repositories from Supabase
     */
    suspend fun fetchRepositories(): Result<List<RemoteRepository>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) {
            return@withContext Result.failure(Exception("Supabase is not configured"))
        }

        try {
            val url = "${SupabaseConfig.PROJECT_URL.trimEnd('/')}/rest/v1/repositories?order=is_official.desc,name.asc"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SupabaseConfig.ANON_KEY)
                .addHeader("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    Log.w(TAG, "Failed to fetch repositories: HTTP ${response.code} $errorBody")
                    return@withContext Result.failure(Exception("HTTP ${response.code}: $errorBody"))
                }

                val body = response.body?.string() ?: "[]"
                val repos = json.decodeFromString<List<RemoteRepository>>(body)
                Result.success(repos)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching repositories", e)
            Result.failure(e)
        }
    }

    /**
     * Fetches all providers (or filtered by repository) from Supabase
     */
    suspend fun fetchProviders(repoId: String? = null): Result<List<RemoteProviderManifest>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) {
            return@withContext Result.failure(Exception("Supabase is not configured"))
        }

        try {
            val query = if (repoId.isNullOrBlank()) {
                "order=name.asc"
            } else {
                val encodedRepoId = URLEncoder.encode(repoId, "UTF-8")
                "repo_id=eq.$encodedRepoId&order=name.asc"
            }
            val url = "${SupabaseConfig.PROJECT_URL.trimEnd('/')}/rest/v1/providers?$query"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SupabaseConfig.ANON_KEY)
                .addHeader("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    Log.w(TAG, "Failed to fetch providers: HTTP ${response.code} $errorBody")
                    return@withContext Result.failure(Exception("HTTP ${response.code}: $errorBody"))
                }

                val body = response.body?.string() ?: "[]"
                val providers = json.decodeFromString<List<RemoteProviderManifest>>(body)
                Result.success(providers)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching providers", e)
            Result.failure(e)
        }
    }

    /**
     * Resolves external custom repository manifest (e.g. CloudStream repo.json URL)
     */
    suspend fun fetchExternalRepo(url: String): Result<Pair<RemoteRepository, List<RemoteProviderManifest>>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (DreamStream/2.0)")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }
                val body = response.body?.string() ?: ""
                
                // Try parsing standard CloudStream repo manifest
                try {
                    val manifest = json.decodeFromString<CloudStreamRepoManifest>(body)
                    val repoId = "cs-" + (manifest.name.lowercase().replace("\\s+".toRegex(), "-").take(24))
                    val repo = RemoteRepository(
                        id = repoId,
                        name = manifest.name.ifBlank { "Custom Repository" },
                        description = manifest.description,
                        url = url,
                        isOfficial = false
                    )
                    Result.success(Pair(repo, emptyList()))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch external repo: $url", e)
            Result.failure(e)
        }
    }
}
