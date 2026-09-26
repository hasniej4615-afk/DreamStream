package com.duta.movie.data.remote

import android.util.Log
import com.duta.movie.provider.model.*
import com.duta.movie.util.SupabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RepoService @Inject constructor(
    val okHttpClient: OkHttpClient
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
     * Resolves shortcodes and user-typed URLs to candidate raw JSON URLs
     */
    fun resolveRepoCandidates(input: String): List<String> {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return emptyList()

        // 1. Known shortcodes
        when (trimmed.lowercase()) {
            "cspr", "official" -> return listOf("https://raw.githubusercontent.com/recloudstream/extensions/master/repo.json")
            "megarepo", "mega" -> return listOf("https://raw.githubusercontent.com/self-similarity/MegaRepo/builds/repo.json")
            "indostream", "indo" -> return listOf("https://raw.githubusercontent.com/TeKuma25/IndoStream/builds/repo.json")
            "storm", "stormunblessed" -> return listOf("https://raw.githubusercontent.com/redblacker8/storm-ext/refs/heads/builds/repo.json")
            "cinephile" -> return listOf("https://raw.githubusercontent.com/rockhero1234/cinephile/refs/heads/builds/repo.json")
            "phisher" -> return listOf("https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/refs/heads/builds/repo.json")
        }

        // 2. GitHub Web URL (e.g. https://github.com/owner/repo)
        val githubRegex = Regex("""^https?://github\.com/([^/]+)/([^/]+)/?(?:tree/([^/]+))?$""")
        val githubMatch = githubRegex.find(trimmed)
        if (githubMatch != null) {
            val owner = githubMatch.groupValues[1]
            val repo = githubMatch.groupValues[2]
            val branch = githubMatch.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }
            return if (branch != null) {
                listOf("https://raw.githubusercontent.com/$owner/$repo/$branch/repo.json")
            } else {
                listOf(
                    "https://raw.githubusercontent.com/$owner/$repo/builds/repo.json",
                    "https://raw.githubusercontent.com/$owner/$repo/master/repo.json",
                    "https://raw.githubusercontent.com/$owner/$repo/main/repo.json"
                )
            }
        }

        // 3. Ensure schema
        val withSchema = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            "https://$trimmed"
        } else {
            trimmed
        }

        // 4. If URL ends with .json, use it directly
        if (withSchema.endsWith(".json")) {
            return listOf(withSchema)
        }

        // 5. Append repo.json candidates
        val cleanUrl = withSchema.trimEnd('/')
        return listOf(
            "$cleanUrl/repo.json",
            cleanUrl
        )
    }

    /**
     * Resolves external custom repository manifest (e.g. CloudStream repo.json URL)
     * and fetches all provider manifests from its plugin lists.
     */
    suspend fun fetchExternalRepo(rawUrl: String): Result<Pair<RemoteRepository, List<RemoteProviderManifest>>> = withContext(Dispatchers.IO) {
        val candidates = resolveRepoCandidates(rawUrl)
        if (candidates.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Invalid repository URL or shortcode: $rawUrl"))
        }

        var lastException: Exception? = null

        for (targetUrl in candidates) {
            try {
                val request = Request.Builder()
                    .url(targetUrl)
                    .addHeader("User-Agent", "Mozilla/5.0 (DreamStream/2.0)")
                    .get()
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        lastException = Exception("HTTP ${response.code} from $targetUrl")
                        return@use
                    }

                    val body = response.body?.string()?.trim() ?: ""
                    if (body.isBlank()) {
                        lastException = Exception("Empty response body from $targetUrl")
                        return@use
                    }

                    // Case A: JSON Array (direct plugins.json list)
                    if (body.startsWith("[")) {
                        try {
                            val plugins = json.decodeFromString<List<CloudStreamPlugin>>(body)
                            val cleanHost = targetUrl.substringAfter("://").substringBefore("/").replace("[^a-zA-Z0-9]".toRegex(), "-")
                            val repoId = "cs-" + (cleanHost.take(16) + "-" + Math.abs(targetUrl.hashCode()).toString(16)).take(24)
                            val repo = RemoteRepository(
                                id = repoId,
                                name = "Plugin Repository ($cleanHost)",
                                description = "External plugins list",
                                url = targetUrl,
                                isOfficial = false
                            )

                            val manifests = plugins.map { plugin ->
                                val cleanId = "${repo.id}.${plugin.internalName ?: plugin.name}".lowercase().replace("[^a-z0-9._-]".toRegex(), "")
                                RemoteProviderManifest(
                                    id = cleanId,
                                    repoId = repo.id,
                                    name = plugin.name,
                                    displayName = plugin.name,
                                    description = plugin.description ?: "",
                                    author = plugin.authors?.joinToString(", ") ?: "",
                                    version = plugin.version,
                                    versionName = "${plugin.version}.0",
                                    iconUrl = (plugin.iconUrl ?: "").replace("%size%", "128"),
                                    mediaType = when {
                                        plugin.tvTypes?.any { it.contains("Anime", ignoreCase = true) } == true -> ProviderMediaType.ANIME
                                        plugin.tvTypes?.any { it.contains("Series", ignoreCase = true) || it.contains("Tv", ignoreCase = true) } == true -> ProviderMediaType.SERIES
                                        plugin.tvTypes?.any { it.contains("Movie", ignoreCase = true) } == true -> ProviderMediaType.MOVIE
                                        else -> ProviderMediaType.MULTI
                                    },
                                    engineType = ProviderEngineType.DEX,
                                    templateType = TemplateType.GENERIC_HTML,
                                    baseUrls = emptyList(),
                                    config = buildJsonObject {},
                                    pluginUrl = plugin.url,
                                    status = ProviderStatus.ACTIVE,
                                    isEnabledDefault = false
                                )
                            }
                            return@withContext Result.success(Pair(repo, manifests))
                        } catch (e: Exception) {
                            lastException = e
                        }
                    }

                    // Case B: JSON Object (Standard CloudStream repo.json manifest)
                    if (body.startsWith("{")) {
                        try {
                            val manifest = json.decodeFromString<CloudStreamRepoManifest>(body)
                            val cleanName = manifest.name.lowercase().replace("[^a-z0-9]".toRegex(), "-").trim('-').take(20)
                            val repoId = if (cleanName.isNotBlank()) "cs-$cleanName" else "cs-" + Math.abs(targetUrl.hashCode()).toString(16)
                            val repo = RemoteRepository(
                                id = repoId,
                                name = manifest.name.ifBlank { "Custom Repository" },
                                description = manifest.description,
                                iconUrl = manifest.iconUrl,
                                url = targetUrl,
                                isOfficial = false
                            )

                            val allManifests = mutableListOf<RemoteProviderManifest>()
                            val seenIds = mutableSetOf<String>()

                            // Fetch each plugin list
                            for (pUrl in manifest.pluginLists) {
                                val resolvedPluginUrl = if (pUrl.startsWith("http://") || pUrl.startsWith("https://")) {
                                    pUrl
                                } else {
                                    val lastSlash = targetUrl.lastIndexOf('/')
                                    if (lastSlash != -1) {
                                        targetUrl.substring(0, lastSlash + 1) + pUrl.trimStart('/')
                                    } else {
                                        pUrl
                                    }
                                }

                                try {
                                    val pReq = Request.Builder()
                                        .url(resolvedPluginUrl)
                                        .addHeader("User-Agent", "Mozilla/5.0 (DreamStream/2.0)")
                                        .get()
                                        .build()

                                    okHttpClient.newCall(pReq).execute().use { pResp ->
                                        if (pResp.isSuccessful) {
                                            val pBody = pResp.body?.string()?.trim() ?: ""
                                            if (pBody.startsWith("[")) {
                                                val plugins = json.decodeFromString<List<CloudStreamPlugin>>(pBody)
                                                for (plugin in plugins) {
                                                    val cleanId = "${repo.id}.${plugin.internalName ?: plugin.name}".lowercase().replace("[^a-z0-9._-]".toRegex(), "")
                                                    if (seenIds.add(cleanId)) {
                                                        allManifests.add(
                                                            RemoteProviderManifest(
                                                                id = cleanId,
                                                                repoId = repo.id,
                                                                name = plugin.name,
                                                                displayName = plugin.name,
                                                                description = plugin.description ?: "",
                                                                author = plugin.authors?.joinToString(", ") ?: "",
                                                                version = plugin.version,
                                                                versionName = "${plugin.version}.0",
                                                                iconUrl = (plugin.iconUrl ?: "").replace("%size%", "128"),
                                                                mediaType = when {
                                                                    plugin.tvTypes?.any { it.contains("Anime", ignoreCase = true) } == true -> ProviderMediaType.ANIME
                                                                    plugin.tvTypes?.any { it.contains("Series", ignoreCase = true) || it.contains("Tv", ignoreCase = true) } == true -> ProviderMediaType.SERIES
                                                                    plugin.tvTypes?.any { it.contains("Movie", ignoreCase = true) } == true -> ProviderMediaType.MOVIE
                                                                    else -> ProviderMediaType.MULTI
                                                                },
                                                                engineType = ProviderEngineType.DEX,
                                                                templateType = TemplateType.GENERIC_HTML,
                                                                baseUrls = emptyList(),
                                                                config = buildJsonObject {},
                                                                pluginUrl = plugin.url,
                                                                status = ProviderStatus.ACTIVE,
                                                                isEnabledDefault = false
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } catch (pe: Exception) {
                                    Log.w(TAG, "Notice: could not load plugin list $resolvedPluginUrl: ${pe.message}")
                                }
                            }

                            return@withContext Result.success(Pair(repo, allManifests))
                        } catch (e: Exception) {
                            lastException = e
                        }
                    }
                }
            } catch (e: Exception) {
                lastException = e
            }
        }

        Log.e(TAG, "Failed to resolve external repo for $rawUrl: ${lastException?.message}")
        Result.failure(lastException ?: Exception("Could not connect to repository"))
    }
}
