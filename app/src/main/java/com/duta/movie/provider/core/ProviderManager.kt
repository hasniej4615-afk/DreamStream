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
import com.duta.movie.provider.model.RemoteProviderManifest
import com.duta.movie.provider.model.RemoteRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repoDao: RepoDao,
    private val repoService: RepoService
) {
    companion object {
        private const val TAG = "ProviderManager"
        const val OFFICIAL_REPO_ID = "dreamstream-official"
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

    init {
        coroutineScope.launch {
            seedDefaultProvidersIfEmpty()
            initializeActiveProviders()
            syncWithSupabaseSilent()
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
     * Silent sync with Supabase: fetches remote updates and manifests
     */
    suspend fun syncWithSupabaseSilent() = withContext(Dispatchers.IO) {
        _isSyncing.value = true
        try {
            // 1. Fetch remote repositories
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

            // 2. Fetch remote providers
            val providersResult = repoService.fetchProviders()
            providersResult.onSuccess { remoteProviders ->
                _availableOnlineProviders.value = remoteProviders

                // Auto-update remote configs (e.g. domain changes, mirrors) for installed providers
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
                initializeActiveProviders()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Supabase sync notice: ${e.message}")
        } finally {
            _isSyncing.value = false
        }
    }

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
     * Resolves streaming servers for a video via active providers
     */
    suspend fun fetchServers(video: Video): List<VideoServer> = withContext(Dispatchers.IO) {
        for (provider in activeProviderInstances.values) {
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

    /**
     * Installs a provider from a remote manifest
     */
    suspend fun installProvider(manifest: RemoteProviderManifest) = withContext(Dispatchers.IO) {
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
     * Uninstalls a provider
     */
    suspend fun uninstallProvider(id: String) = withContext(Dispatchers.IO) {
        repoDao.deleteProviderById(id)
        activeProviderInstances.remove(id)
    }

    /**
     * Adds a custom repository via URL (e.g. CloudStream repo URL)
     */
    suspend fun addCustomRepository(url: String): Result<String> = withContext(Dispatchers.IO) {
        val result = repoService.fetchExternalRepo(url)
        result.mapCatching { (repo, _) ->
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
            repo.name
        }
    }
}
