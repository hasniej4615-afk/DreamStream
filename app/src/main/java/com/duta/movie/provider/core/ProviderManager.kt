package com.duta.movie.provider.core

import android.content.Context
import android.util.Log
import com.duta.movie.data.local.InstalledProviderEntity
import com.duta.movie.data.local.InstalledRepoEntity
import com.duta.movie.data.local.RepoDao
import com.duta.movie.data.remote.RepoService
import com.duta.movie.model.Video
import com.duta.movie.model.VideoServer
import com.duta.movie.provider.engine.DexPluginProvider
import com.duta.movie.provider.engine.TemplateProvider
import com.duta.movie.provider.model.ProviderEngineType
import com.duta.movie.provider.model.ProviderMediaType
import com.duta.movie.provider.model.RemoteProviderManifest
import com.duta.movie.provider.model.RemoteRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Request

@Singleton
class ProviderManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repoDao: RepoDao,
    private val repoService: RepoService
) {
    companion object {
        private const val TAG = "ProviderManager"
        const val OFFICIAL_REPO_ID = "dreamstream-official"
        val CORE_PROVIDER_IDS = setOf(
            "com.duta.provider.pencurimovie",
            "com.duta.provider.dutafilm",
            "com.duta.provider.lk21",
            "com.duta.provider.pramlee"
        )
    }

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val installedRepos: Flow<List<InstalledRepoEntity>> = repoDao.getAllRepos()
    val installedProviders: Flow<List<InstalledProviderEntity>> = repoDao.getAllProviders()
    val enabledProviders: Flow<List<InstalledProviderEntity>> = repoDao.getEnabledProviders()

    private val _availableOnlineProviders = MutableStateFlow<List<RemoteProviderManifest>>(emptyList())
    val availableOnlineProviders: StateFlow<List<RemoteProviderManifest>> = _availableOnlineProviders.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val activeProviderInstances = ConcurrentHashMap<String, MediaProvider>()
    private val cachedOnlineProviders = ConcurrentHashMap<String, List<RemoteProviderManifest>>()

    init {
        coroutineScope.launch {
            seedDefaultProvidersIfEmpty()
            loadCachedOnlineProvidersFromDisk()
            initializeActiveProviders()
            syncAllRepositories()
        }
    }

    private fun recomputeOnlineProviders() {
        val all = cachedOnlineProviders.values.flatten().distinctBy { it.id }
        _availableOnlineProviders.value = all
        saveCachedOnlineProvidersToDisk()
    }

    private fun loadCachedOnlineProvidersFromDisk() {
        try {
            val file = File(context.filesDir, "repo_cache/online_manifests.json")
            if (file.exists()) {
                val jsonStr = file.readText()
                val list = Json.decodeFromString<List<RemoteProviderManifest>>(jsonStr)
                val grouped = list.groupBy { it.repoId }
                cachedOnlineProviders.clear()
                cachedOnlineProviders.putAll(grouped)
                _availableOnlineProviders.value = list
            }
        } catch (e: Exception) {
            Log.w(TAG, "Notice: could not load cached online manifests: ${e.message}")
        }
    }

    private fun saveCachedOnlineProvidersToDisk() {
        try {
            val dir = File(context.filesDir, "repo_cache").apply { mkdirs() }
            val file = File(dir, "online_manifests.json")
            val list = cachedOnlineProviders.values.flatten().distinctBy { it.id }
            val jsonStr = Json.encodeToString(list)
            file.writeText(jsonStr)
        } catch (e: Exception) {
            Log.w(TAG, "Notice: could not save online manifests to cache: ${e.message}")
        }
    }

    /**
     * Seeds the official repository and core providers on fresh install
     */
    private suspend fun seedDefaultProvidersIfEmpty() {
        val existingRepos = repoDao.getRepoById(OFFICIAL_REPO_ID)
        if (existingRepos == null) {
            val officialRepo = InstalledRepoEntity(
                id = OFFICIAL_REPO_ID,
                name = "DreamStream Official Repo",
                description = "Curated high-speed streaming sources for Movies, Series, and Asian Dramas.",
                author = "DreamStream Team",
                iconUrl = "https://raw.githubusercontent.com/hasniej4615-afk/DreamStream/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png",
                isOfficial = true
            )
            repoDao.insertOrUpdateRepo(officialRepo)
        }

        val existingProviders = repoDao.getEnabledProvidersSync()
        if (existingProviders.isEmpty()) {
            val defaultProviders = listOf(
                InstalledProviderEntity(
                    id = "com.duta.provider.pencurimovie",
                    repoId = OFFICIAL_REPO_ID,
                    name = "PencuriMovie",
                    displayName = "PencuriMovie (Malay & Cinema)",
                    description = "High-speed Malay, Asian, and Hollywood movies with direct HLS streaming.",
                    author = "DreamStream Team",
                    version = 1,
                    versionName = "1.0.0",
                    mediaType = "MULTI",
                    engineType = "TEMPLATE",
                    templateType = "PENCURI",
                    baseUrlsJson = "[\"https://ww44.pencurimovie.baby\", \"https://pencurimovie.baby\", \"https://pencurimovie.xyz\"]",
                    configJson = "{\"searchPath\": \"/?s=\", \"isSeriesSupported\": true}",
                    isEnabled = true,
                    priorityOrder = 1
                ),
                InstalledProviderEntity(
                    id = "com.duta.provider.dutafilm",
                    repoId = OFFICIAL_REPO_ID,
                    name = "DutaFilm",
                    displayName = "DutaFilm (Movies & TV Series)",
                    description = "Comprehensive movie and TV series library with multi-resolution streaming.",
                    author = "DreamStream Team",
                    version = 1,
                    versionName = "1.0.0",
                    mediaType = "MULTI",
                    engineType = "TEMPLATE",
                    templateType = "DUTAFILM",
                    baseUrlsJson = "[\"http://159.89.249.45\", \"https://df31.mantab.men\", \"https://df32.mantab.men\"]",
                    configJson = "{\"searchPath\": \"/search/\", \"isSeriesSupported\": true}",
                    isEnabled = true,
                    priorityOrder = 2
                ),
                InstalledProviderEntity(
                    id = "com.duta.provider.lk21",
                    repoId = OFFICIAL_REPO_ID,
                    name = "LK21 Bullerswood",
                    displayName = "LK21 / LayarKaca21",
                    description = "Extensive collection of Indonesian and international cinema and dramas.",
                    author = "DreamStream Team",
                    version = 1,
                    versionName = "1.0.0",
                    mediaType = "MOVIE",
                    engineType = "TEMPLATE",
                    templateType = "WORDPRESS_MUVIPRO",
                    baseUrlsJson = "[\"https://bullerswood.org\", \"https://scphi.org\", \"https://grishamfarms.org\"]",
                    configJson = "{\"searchPath\": \"/?s=\"}",
                    isEnabled = true,
                    priorityOrder = 3
                ),
                InstalledProviderEntity(
                    id = "com.duta.provider.pramlee",
                    repoId = OFFICIAL_REPO_ID,
                    name = "P-Ramlee Archive",
                    displayName = "Koleksi Filem P. Ramlee",
                    description = "Heritage collection of classic Tan Sri P. Ramlee masterpieces and Shaw Brothers classics.",
                    author = "DreamStream Heritage",
                    version = 1,
                    versionName = "1.0.0",
                    mediaType = "MOVIE",
                    engineType = "TEMPLATE",
                    templateType = "GENERIC_HTML",
                    baseUrlsJson = "[\"https://archive.org\"]",
                    configJson = "{\"archiveCollection\": \"FilemP.ramlee\"}",
                    isEnabled = true,
                    priorityOrder = 4
                )
            )
            repoDao.insertOrUpdateProviders(defaultProviders)
        }
    }

    /**
     * Instantiates provider engines for all enabled providers
     */
    private suspend fun initializeActiveProviders() {
        val providers = repoDao.getEnabledProvidersSync()
        activeProviderInstances.clear()
        for (p in providers) {
            val instance: MediaProvider = when (p.engineType) {
                "DEX" -> DexPluginProvider(context, p)
                else -> TemplateProvider(p)
            }
            activeProviderInstances[p.id] = instance
        }
    }

    /**
     * Syncs all repositories: Supabase official catalog + all installed custom repositories
     */
    suspend fun syncAllRepositories(): Result<String> = withContext(Dispatchers.IO) {
        _isSyncing.value = true
        try {
            // 1. Fetch remote Supabase repositories & providers
            if (com.duta.movie.util.SupabaseConfig.isConfigured) {
                val reposResult = repoService.fetchRepositories()
                reposResult.onSuccess { remoteRepos ->
                    for (r in remoteRepos) {
                        val existing = repoDao.getRepoById(r.id)
                        val entity = InstalledRepoEntity(
                            id = r.id,
                            name = r.name,
                            description = r.description,
                            author = r.author,
                            iconUrl = r.iconUrl,
                            url = r.url,
                            isOfficial = r.isOfficial,
                            lastSyncedAt = System.currentTimeMillis()
                        )
                        repoDao.insertOrUpdateRepo(entity)
                    }
                }

                val providersResult = repoService.fetchProviders()
                providersResult.onSuccess { remoteProviders ->
                    cachedOnlineProviders[OFFICIAL_REPO_ID] = remoteProviders

                    // Auto-update remote configs for installed providers
                    for (rp in remoteProviders) {
                        val local = repoDao.getProviderById(rp.id)
                        if (local != null) {
                            val baseUrlsStr = Json.encodeToString(rp.baseUrls)
                            val configStr = rp.config.toString()
                            val updated = local.copy(
                                version = rp.version,
                                versionName = rp.versionName,
                                displayName = rp.displayName,
                                description = rp.description,
                                baseUrlsJson = baseUrlsStr,
                                configJson = configStr,
                                pluginUrl = rp.pluginUrl,
                                status = rp.status.name,
                                updatedAt = System.currentTimeMillis()
                            )
                            repoDao.insertOrUpdateProvider(updated)
                        }
                    }
                }
            }

            // 2. Fetch and sync all installed custom repositories
            val customRepos = repoDao.getCustomReposSync()
            for (cRepo in customRepos) {
                if (cRepo.url.isNotBlank()) {
                    val extResult = repoService.fetchExternalRepo(cRepo.url)
                    extResult.onSuccess { (updatedRepo, manifests) ->
                        repoDao.insertOrUpdateRepo(
                            cRepo.copy(
                                name = updatedRepo.name.ifBlank { cRepo.name },
                                description = updatedRepo.description.ifBlank { cRepo.description },
                                iconUrl = updatedRepo.iconUrl.ifBlank { cRepo.iconUrl },
                                lastSyncedAt = System.currentTimeMillis()
                            )
                        )
                        cachedOnlineProviders[cRepo.id] = manifests

                        // Auto-update any installed provider from this repo if new version
                        for (manifest in manifests) {
                            val local = repoDao.getProviderById(manifest.id)
                            if (local != null && manifest.version > local.version) {
                                val updated = local.copy(
                                    version = manifest.version,
                                    versionName = manifest.versionName,
                                    displayName = manifest.displayName,
                                    description = manifest.description,
                                    pluginUrl = manifest.pluginUrl,
                                    updatedAt = System.currentTimeMillis()
                                )
                                repoDao.insertOrUpdateProvider(updated)
                            }
                        }
                    }
                }
            }

            recomputeOnlineProviders()
            initializeActiveProviders()
            Result.success("Sync completed (${_availableOnlineProviders.value.size} extensions available)")
        } catch (e: Exception) {
            Log.w(TAG, "Sync notice: ${e.message}")
            Result.failure(e)
        } finally {
            _isSyncing.value = false
        }
    }

    suspend fun syncWithSupabaseSilent() = syncAllRepositories()

    /**
     * Parallel Combined Search across all enabled providers
     */
    suspend fun searchAllEnabled(query: String, page: Int = 1): List<Video> = coroutineScope {
        val providers = activeProviderInstances.values.filter { it.isEnabled }
        if (providers.isEmpty()) return@coroutineScope emptyList()

        val deferredList = providers.map { provider ->
            async(Dispatchers.IO) {
                try {
                    provider.search(query, page)
                } catch (e: Exception) {
                    Log.w(TAG, "Search failed for ${provider.name}: ${e.message}")
                    emptyList()
                }
            }
        }

        val allResults = deferredList.awaitAll().flatten()

        // Deduplicate by clean title and id
        val seen = mutableSetOf<String>()
        val distinctResults = mutableListOf<Video>()
        for (v in allResults) {
            val key = v.title.trim().lowercase()
            if (seen.add(key) && seen.add(v.id)) {
                distinctResults.add(v)
            }
        }
        distinctResults
    }

    /**
     * Fetches category items from all enabled custom (non-core) providers.
     * Merged into Home categories alongside official sources.
     */
    suspend fun fetchSectionAllCustom(categoryPath: String, page: Int = 1, count: Int = 30): List<Video> = coroutineScope {
        val customProviders = activeProviderInstances.values.filter { provider ->
            provider.isEnabled && provider.id !in CORE_PROVIDER_IDS
        }
        if (customProviders.isEmpty()) return@coroutineScope emptyList()

        val isSeries = categoryPath.contains("series", ignoreCase = true) || categoryPath.contains("tv", ignoreCase = true)
        val isMovie = categoryPath == "/" || categoryPath == "/movies/" || categoryPath.contains("movie", ignoreCase = true)

        val deferredList: List<Deferred<List<Video>>> = customProviders.mapNotNull { provider ->
            // Filter by media type if provider specializes strictly in Movies or Series
            if (isSeries && provider.mediaType == ProviderMediaType.MOVIE) return@mapNotNull null
            if (isMovie && provider.mediaType == ProviderMediaType.SERIES) return@mapNotNull null

            async(Dispatchers.IO) {
                try {
                    withTimeoutOrNull(5000L) {
                        provider.fetchSection(categoryPath, page, count)
                    } ?: emptyList()
                } catch (e: Exception) {
                    Log.w(TAG, "Section fetch failed for custom provider ${provider.name}: ${e.message}")
                    emptyList()
                }
            }
        }

        val allResults: List<Video> = deferredList.awaitAll().flatten()
        if (allResults.isEmpty()) return@coroutineScope emptyList()

        // Clean and deduplicate
        val seen = mutableSetOf<String>()
        val distinctResults = mutableListOf<Video>()
        for (v in allResults) {
            val key = v.title.trim().lowercase()
            if (seen.add(key) && seen.add(v.id)) {
                distinctResults.add(v)
            }
        }
        distinctResults
    }

    /**
     * Resolves details for a video using active custom or preferred providers
     */
    suspend fun fetchVideoDetail(video: Video): Video? = withContext(Dispatchers.IO) {
        val preferredProvider = activeProviderInstances.values.firstOrNull { p ->
            video.id.startsWith("${p.id}_") || video.videoUrl.contains(p.id, ignoreCase = true)
        }
        if (preferredProvider != null) {
            try {
                val detailed = preferredProvider.fetchVideoDetail(video)
                if (detailed != null) return@withContext detailed
            } catch (_: Exception) {}
        }

        for (provider in activeProviderInstances.values) {
            if (provider == preferredProvider) continue
            try {
                val detailed = provider.fetchVideoDetail(video)
                if (detailed != null) return@withContext detailed
            } catch (_: Exception) {}
        }
        null
    }

    /**
     * Resolves streaming servers for a video via active providers
     */
    suspend fun fetchServers(video: Video): List<VideoServer> = withContext(Dispatchers.IO) {
        val preferredProvider = activeProviderInstances.values.firstOrNull { p ->
            video.id.startsWith("${p.id}_") || video.videoUrl.contains(p.id, ignoreCase = true)
        }
        if (preferredProvider != null) {
            try {
                val servers = preferredProvider.fetchServers(video)
                if (servers.isNotEmpty()) return@withContext servers
            } catch (_: Exception) {}
        }

        for (provider in activeProviderInstances.values) {
            if (provider == preferredProvider) continue
            try {
                val servers = provider.fetchServers(video)
                if (servers.isNotEmpty()) return@withContext servers
            } catch (_: Exception) {}
        }
        emptyList()
    }

    /**
     * Toggles provider enabled status
     */
    suspend fun toggleProvider(id: String, isEnabled: Boolean) = withContext(Dispatchers.IO) {
        repoDao.setProviderEnabled(id, isEnabled)
        initializeActiveProviders()
    }

    private fun downloadPluginFile(id: String, url: String) {
        if (url.isBlank()) return
        try {
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (DreamStream/2.0)")
                .get()
                .build()
            repoService.okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return
                val bodyStream = resp.body?.byteStream() ?: return
                val pluginDir = File(context.filesDir, "plugins").apply { mkdirs() }
                val targetDex = File(pluginDir, "$id.dex")

                if (url.endsWith(".cs3", ignoreCase = true) || url.endsWith(".zip", ignoreCase = true)) {
                    ZipInputStream(bodyStream).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            if (entry.name == "classes.dex") {
                                targetDex.outputStream().use { fos -> zis.copyTo(fos) }
                                targetDex.setReadOnly()
                                break
                            }
                            entry = zis.nextEntry
                        }
                    }
                } else {
                    targetDex.outputStream().use { fos -> bodyStream.copyTo(fos) }
                    targetDex.setReadOnly()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Notice: could not pre-cache plugin dex for $id: ${e.message}")
        }
    }

    /**
     * Installs a provider from a remote manifest
     */
    suspend fun installProvider(manifest: RemoteProviderManifest) = withContext(Dispatchers.IO) {
        if (manifest.engineType == ProviderEngineType.DEX && manifest.pluginUrl.isNotBlank()) {
            downloadPluginFile(manifest.id, manifest.pluginUrl)
        }

        val entity = InstalledProviderEntity(
            id = manifest.id,
            repoId = manifest.repoId,
            name = manifest.name,
            displayName = manifest.displayName,
            description = manifest.description,
            author = manifest.author,
            version = manifest.version,
            versionName = manifest.versionName,
            iconUrl = manifest.iconUrl,
            mediaType = manifest.mediaType.name,
            engineType = manifest.engineType.name,
            templateType = manifest.templateType.name,
            baseUrlsJson = Json.encodeToString(manifest.baseUrls),
            configJson = manifest.config.toString(),
            pluginUrl = manifest.pluginUrl,
            status = manifest.status.name,
            isEnabled = true,
            priorityOrder = 10
        )
        repoDao.insertOrUpdateProvider(entity)
        initializeActiveProviders()
    }

    /**
     * Uninstalls a provider (protected: dreamstream core providers cannot be deleted)
     */
    suspend fun uninstallProvider(id: String): Result<String> = withContext(Dispatchers.IO) {
        val provider = repoDao.getProviderById(id)
        if (provider == null) {
            return@withContext Result.failure(Exception("Provider not found."))
        }
        if (provider.repoId == OFFICIAL_REPO_ID) {
            return@withContext Result.failure(Exception("DreamStream official providers cannot be deleted. You can toggle them off instead."))
        }

        repoDao.deleteProviderById(id)
        activeProviderInstances.remove(id)
        try {
            File(context.filesDir, "plugins/$id.dex").delete()
        } catch (_: Exception) {}

        Result.success("Provider '${provider.displayName}' uninstalled.")
    }

    /**
     * Deletes a custom repository and all its associated providers
     * (protected: dreamstream official repo cannot be deleted)
     */
    suspend fun deleteRepository(repoId: String): Result<String> = withContext(Dispatchers.IO) {
        if (repoId == OFFICIAL_REPO_ID) {
            return@withContext Result.failure(Exception("Cannot delete the official DreamStream repository."))
        }
        val repo = repoDao.getRepoById(repoId)
        if (repo == null) {
            return@withContext Result.failure(Exception("Repository not found."))
        }
        if (repo.isOfficial) {
            return@withContext Result.failure(Exception("Cannot delete official repositories."))
        }

        // 1. Delete all providers belonging to this repo
        val providers = repoDao.getProvidersByRepo(repoId)
        for (p in providers) {
            activeProviderInstances.remove(p.id)
            try {
                File(context.filesDir, "plugins/${p.id}.dex").delete()
            } catch (_: Exception) {}
        }
        repoDao.deleteProvidersByRepo(repoId)

        // 2. Delete the repository entity
        repoDao.deleteRepoById(repoId)

        // 3. Remove from cached online providers
        cachedOnlineProviders.remove(repoId)
        recomputeOnlineProviders()

        Result.success("Repository '${repo.name}' and its extensions deleted.")
    }

    /**
     * Adds a custom repository via URL or shortcode (e.g. CloudStream repo URL)
     */
    suspend fun addCustomRepository(url: String): Result<String> = withContext(Dispatchers.IO) {
        _isSyncing.value = true
        try {
            val result = repoService.fetchExternalRepo(url)
            result.mapCatching { (repo, manifests) ->
                val entity = InstalledRepoEntity(
                    id = repo.id,
                    name = repo.name,
                    description = repo.description,
                    author = repo.author,
                    iconUrl = repo.iconUrl,
                    url = repo.url,
                    isOfficial = false,
                    lastSyncedAt = System.currentTimeMillis()
                )
                repoDao.insertOrUpdateRepo(entity)
                cachedOnlineProviders[repo.id] = manifests
                recomputeOnlineProviders()
                "${repo.name} (${manifests.size} extensions found)"
            }
        } finally {
            _isSyncing.value = false
        }
    }
}
