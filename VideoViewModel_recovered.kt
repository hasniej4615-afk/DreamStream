package com.duta.movie.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import com.duta.movie.data.PreferenceManager
import com.duta.movie.data.VideoRepository
import com.duta.movie.model.ActressProfile
import com.duta.movie.model.Episode
import com.duta.movie.model.Video
import com.duta.movie.model.VideoServer
import com.duta.movie.model.Subtitle
import com.duta.movie.util.SubtitleExtractor
import com.duta.movie.util.VideoExtractor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@HiltViewModel
class VideoViewModel @Inject constructor(
    private val videoRepository: VideoRepository,
    private val imageLoader: ImageLoader,
    private val preferenceManager: PreferenceManager,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _featuredVideos = MutableStateFlow<List<Video>>(emptyList())
    val featuredVideos: StateFlow<List<Video>> = _featuredVideos.asStateFlow()

    private val _latestMovies = MutableStateFlow<List<Video>>(emptyList())
    val latestMovies: StateFlow<List<Video>> = _latestMovies.asStateFlow()

    private val _latestTVSeries = MutableStateFlow<List<Video>>(emptyList())
    val latestTVSeries: StateFlow<List<Video>> = _latestTVSeries.asStateFlow()

    private val _headlinerVideo = MutableStateFlow<Video?>(null)
    val headlinerVideo: StateFlow<Video?> = _headlinerVideo.asStateFlow()

    private val _resultVideos = MutableStateFlow<List<Video>>(emptyList())
    val resultVideos: StateFlow<List<Video>> = _resultVideos.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isMoviesLoading = MutableStateFlow(false)
    val isMoviesLoading: StateFlow<Boolean> = _isMoviesLoading.asStateFlow()

    private val _isSeriesLoading = MutableStateFlow(false)
    val isSeriesLoading: StateFlow<Boolean> = _isSeriesLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isPlayerActive = MutableStateFlow(false)
    val isPlayerActive: StateFlow<Boolean> = _isPlayerActive.asStateFlow()

    private val _videoMetadata = MutableStateFlow<Video?>(null)
    val videoMetadata: StateFlow<Video?> = _videoMetadata.asStateFlow()

    private val _resolvedUrl = MutableStateFlow<String?>(null)
    val resolvedUrl: StateFlow<String?> = _resolvedUrl.asStateFlow()
    val extractedUrl: StateFlow<String?> = _resolvedUrl.asStateFlow()

    private val _isResolving = MutableStateFlow(false)
    val isResolving: StateFlow<Boolean> = _isResolving.asStateFlow()

    private val _resolutionProgress = MutableStateFlow<String?>(null)
    val resolutionProgress: StateFlow<String?> = _resolutionProgress.asStateFlow()

    private val _resolutionLog = MutableStateFlow<List<String>>(emptyList())
    val resolutionLog: StateFlow<List<String>> = _resolutionLog.asStateFlow()

    private val _metadataTrigger = MutableStateFlow(0)
    val metadataTrigger: StateFlow<Int> = _metadataTrigger.asStateFlow()

    private val _categories = MutableStateFlow<List<Map<String, String>>>(emptyList())
    val allCategories: StateFlow<List<Map<String, String>>> = _categories.asStateFlow()

    private val _categoryVideos = MutableStateFlow<Map<String, List<Video>>>(emptyMap())
    private val _categoryLoading = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val categoryLoading: StateFlow<Map<String, Boolean>> = _categoryLoading.asStateFlow()

    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

    private val _isEndReached = MutableStateFlow(false)
    val isEndReached: StateFlow<Boolean> = _isEndReached.asStateFlow()

    private val _selectedActressProfile = MutableStateFlow<ActressProfile?>(null)
    val selectedActressProfile: StateFlow<ActressProfile?> = _selectedActressProfile.asStateFlow()
    private val _isActressProfileLoading = MutableStateFlow(false)
    val isActressProfileLoading: StateFlow<Boolean> = _isActressProfileLoading.asStateFlow()

    private val _subtitles = MutableStateFlow<List<Subtitle>>(emptyList())
    val subtitles: StateFlow<List<Subtitle>> = _subtitles.asStateFlow()
    private val _selectedSubtitle = MutableStateFlow<Subtitle?>(null)
    val selectedSubtitle: StateFlow<Subtitle?> = _selectedSubtitle.asStateFlow()
    private val _isSubtitleLoading = MutableStateFlow(false)
    val isSubtitleLoading: StateFlow<Boolean> = _isSubtitleLoading.asStateFlow()
    private val _subtitleError = MutableStateFlow<String?>(null)
    val subtitleError: StateFlow<String?> = _subtitleError.asStateFlow()

    private val _currentServerUrl = MutableStateFlow<String?>(null)
    val currentServerUrl: StateFlow<String?> = _currentServerUrl.asStateFlow()

    private val _currentEpisode = MutableStateFlow<Episode?>(null)
    val currentEpisode: StateFlow<Episode?> = _currentEpisode.asStateFlow()

    private val _shouldSuppressResume = MutableStateFlow(false)
    val shouldSuppressResume: StateFlow<Boolean> = _shouldSuppressResume.asStateFlow()

    private val _lastReferer = MutableStateFlow<String?>(null)
    val lastReferer: StateFlow<String?> = _lastReferer.asStateFlow()
    private val _lastCookies = MutableStateFlow<String?>(null)
    val lastCookies: StateFlow<String?> = _lastCookies.asStateFlow()

    private val _subtitleOffset = MutableStateFlow(0L)
    val subtitleOffset: StateFlow<Long> = _subtitleOffset.asStateFlow()

    val metadataCache = ConcurrentHashMap<String, Video>()
    val filterResultCache = ConcurrentHashMap<String, Boolean>()
    private val moviePath = VideoExtractor.normalizePath("/movie/")
    private val seriesPath = VideoExtractor.normalizePath("/serial-tv-terbaru/")
    private val PRIORITY_PATHS = listOf(moviePath, seriesPath, "/network/netflix/", "/network/disney/").map { VideoExtractor.normalizePath(it) }
    
    private var moviesPage = 1
    private var seriesPage = 1
    val deadMirrors = mutableSetOf<String>()
    val categoryPages = ConcurrentHashMap<String, Int>()

    private var fetchJob: Job? = null
    private var loadMoreJob: Job? = null
    private var searchJob: Job? = null
    private var backgroundDetailsJob: Job? = null
    private var currentPage = 1
    private var rotationCount = 0
    private var isRotationLocked = false
    private var prefetchJob: Job? = null
    private var lastSubtitleSearchTitle: String? = null
    private var subSearchJob: Job? = null

    val enabledCategoryPaths: StateFlow<Set<String>> = preferenceManager.enabledCategoryPaths.stateIn(viewModelScope, SharingStarted.Eagerly, PreferenceManager.DEFAULT_ENABLED_CATEGORIES)
    val myList: StateFlow<Set<String>> = videoRepository.myList.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val defaultSubtitleLanguage: StateFlow<String> = videoRepository.defaultSubtitleLanguage.stateIn(viewModelScope, SharingStarted.Eagerly, "Indonesian")
    val isAutoSubtitleEnabled: StateFlow<Boolean> = videoRepository.isAutoSubtitleEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val isSafeModeEnabled: StateFlow<Boolean> = videoRepository.isSafeModeEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val safeModePin: StateFlow<String> = videoRepository.safeModePin.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val activeBaseUrl: StateFlow<String> = preferenceManager.activeBaseUrl.onEach { url ->
        if (url.isNotEmpty()) VideoExtractor.setBaseUrl(url)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, VideoExtractor.getBaseUrl())
    val forceWebViewHosts: StateFlow<Set<String>> = preferenceManager.forceWebViewHosts.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val uiSafeAreaPadding: StateFlow<Int> = preferenceManager.uiSafeAreaPadding.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val uiHeroHeightOffset: StateFlow<Int> = preferenceManager.uiHeroHeightOffset.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val uiScaleFactor: StateFlow<Float> = preferenceManager.uiScaleFactor.stateIn(viewModelScope, SharingStarted.Eagerly, 1.0f)
    val uiThumbnailScaleFactor: StateFlow<Float> = preferenceManager.uiThumbnailScaleFactor.stateIn(viewModelScope, SharingStarted.Eagerly, 1.0f)

    val sourceWeights: StateFlow<Map<String, Int>> = preferenceManager.sourceWeights.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private fun sortAndNormalizeCategories(list: List<Map<String, String>>): List<Map<String, String>> {
        return list.map { 
            it.toMutableMap().apply { put("path", VideoExtractor.normalizePath(get("path") ?: "")) } 
        }.distinctBy { it["path"] }.sortedWith { a, b ->
            val pathA = a["path"] ?: ""
            val pathB = b["path"] ?: ""
            val pA = PRIORITY_PATHS.indexOf(pathA)
            val pB = PRIORITY_PATHS.indexOf(pathB)
            when {
                pA != -1 && pB != -1 -> pA.compareTo(pB)
                pA != -1 -> -1
                pB != -1 -> 1
                else -> (a["name"] ?: "").compareTo(b["name"] ?: "", ignoreCase = true)
            }
        }
    }

    init {
        val defaultCategories = listOf(
            mapOf("name" to "Movies", "path" to moviePath),
            mapOf("name" to "TV Series", "path" to seriesPath),
            mapOf("name" to "Netflix", "path" to "/network/netflix/"),
            mapOf("name" to "Disney+", "path" to "/network/disney/"),
            mapOf("name" to "Apple TV+", "path" to "/network/apple-tv/"),
            mapOf("name" to "HBO", "path" to "/network/hbo/"),
            mapOf("name" to "Action", "path" to "/genre/action/"),
            mapOf("name" to "Horror", "path" to "/genre/horror/"),
            mapOf("name" to "Sci-Fi", "path" to "/genre/sci-fi/"),
            mapOf("name" to "Animation", "path" to "/genre/animation/"),
            mapOf("name" to "Indonesia", "path" to "/country/indonesia/"),
            mapOf("name" to "Korea", "path" to "/country/korea/"),
            mapOf("name" to "Thailand", "path" to "/country/thailand/")
        )
        
        // Immediate initialization with sorted defaults
        _categories.value = sortAndNormalizeCategories(defaultCategories)
        
        viewModelScope.launch {
            preferenceManager.discoveredCategories.collect { list ->
                val merged = sortAndNormalizeCategories(defaultCategories + list)
                _categories.value = merged
            }
        }

        VideoExtractor.setOnDomainLearned { newUrl ->
            viewModelScope.launch {
                preferenceManager.setActiveBaseUrl(newUrl)
                VideoExtractor.setBaseUrl(newUrl)
            }
        }
        
        loadData()
        
        // STARTUP SCRUB: Remove the content provider base URL domain from forceWebViewHosts
        // if it was incorrectly added by previous buggy sessions. This auto-heals stale DataStore.
        viewModelScope.launch(Dispatchers.IO) {
            preferenceManager.removeFromForceWebViewHosts(VideoExtractor.getBaseUrl())
        }
        
        viewModelScope.launch {
            videoRepository.recentlyWatchedVideos.collect { list ->
                if (list.isNotEmpty()) updateMetadataCache(list, triggerBackground = false)
            }
        }
        
        viewModelScope.launch {
            videoRepository.favoriteVideos.collect { list ->
                if (list.isNotEmpty()) updateMetadataCache(list, triggerBackground = false)
            }
        }































































































































































                    )
                    val updated = existing.copy(
                        title = updatedCleanedTitle,
                        thumbnailUrl = if (video.thumbnailUrl.isNotEmpty()) video.thumbnailUrl else existing.thumbnailUrl,
                        actresses = (video.actresses + existing.actresses).distinct().filter { it.isNotEmpty() },
                        actressPaths = (video.actressPaths + existing.actressPaths),
                        date = video.date.ifEmpty { existing.date },
                        quality = video.quality.ifEmpty { existing.quality },
                        description = if (video.description.length > existing.description.length) video.description else existing.description,
                        previewUrl = if (video.previewUrl.isNotEmpty()) video.previewUrl else existing.previewUrl,
                        backdropUrl = if (video.backdropUrl.isNotEmpty()) video.backdropUrl else existing.backdropUrl,
                        duration = if (video.duration != "??:??" && video.duration.isNotEmpty()) video.duration else existing.duration,
                        servers = if (video.servers.size >= existing.servers.size) video.servers else existing.servers,
                        episodes = if (video.episodes.size >= existing.episodes.size) video.episodes else existing.episodes,
                        isSeries = if (video.isSeries != null) video.isSeries else existing.isSeries
                    )
                    if (updated != existing) metadataCache[video.id] = updated
                }
            }
            withContext(Dispatchers.Main) { _metadataTrigger.update { it + 1 } }
            if (triggerBackground) loadBackgroundDetails(videos)
        }
    }

    suspend fun searchVideos(query: String, categoryPath: String? = null) {
        if (query.isBlank()) return
        searchJob?.cancel(); searchJob = viewModelScope.launch { _isLoading.value = true; _resultVideos.value = emptyList(); try { val results = videoRepository.searchVideos(query, page = 1, count = 100, categoryPath = categoryPath); if (results.isNotEmpty()) { updateMetadataCache(results); _resultVideos.value = results } else _error.value = "No results found for '$query'" } catch (e: Exception) { if (e !is CancellationException) _error.value = "Search failed: ${e.message}" } finally { _isLoading.value = false } }
    }

    fun loadFullDetails(videoId: String) { 
        if (_videoMetadata.value?.id != videoId) {
            _videoMetadata.value = getVideo(videoId) 
        }

        viewModelScope.launch { 
            _isLoading.value = true
            try { 
                val video = getVideo(videoId)
                if (video != null) { 
                    val detailed = videoRepository.fetchVideoDetails(video.id)
                    if (detailed != null) { 

















































    fun notifyPlaybackFailure(url: String) {
        val host = try { android.net.Uri.parse(url).host?.lowercase() } catch(_: Exception) { null }
        
        val activeBaseHost = try { android.net.Uri.parse(VideoExtractor.getBaseUrl()).host?.lowercase() } catch(_: Exception) { null }
        if (host == activeBaseHost) return // Don't blacklist the provider site itself
        
        _videoMetadata.value?.let { v ->
            if (v.title.contains("Agent Kim", ignoreCase = true) || v.title.contains("Manager Kim", ignoreCase = true)) {
                Log.e("OwlEyeMonitoring", "FAILURE DETECTED: Agent Kim playback failed on $host | URL: ${url.take(100)}")
            }
        }

        host?.let { 
            deadMirrors.add(it)
            viewModelScope.launch(Dispatchers.IO) { 
                preferenceManager.decrementSourceWeight(it)
                preferenceManager.recordHostPlayerFailure(it)
            } 
        }
    }

    private val domainClusters = listOf(
        setOf("abyss.to", "abysscdn.com", "play.abyssplayer.com"),
        setOf("bond.to", "bondcdn.com", "play.bondplayer.com"),
        setOf("veev.to", "veev.io")
    )

    private fun blacklistHost(host: String?) {
        if (host == null || host.isEmpty()) return
        deadMirrors.add(host)
        
        // OWL'S EYE: Aliased Blacklisting
        // If we blacklist one domain in a cluster, blacklist them all.
        // Also add all cluster aliases to forceWebViewHosts so partial resets don't bring them back.
        val clusterAliases = domainClusters.find { it.contains(host) }
        clusterAliases?.forEach { deadMirrors.add(it) }

        viewModelScope.launch(Dispatchers.IO) { 
            preferenceManager.decrementSourceWeight(host)
            val hitThreshold = preferenceManager.recordHostPlayerFailure(host)
            // If this host crossed the threshold, permanently block ALL cluster aliases too
            if (hitThreshold) {
                clusterAliases?.forEach { alias ->
                    preferenceManager.addForceWebViewHost(alias)
                }
            }
        }
    }

    fun notifyGateStuck(url: String) {
        if (isRotationLocked) {
        val mirrorHost = try { mirrorUrl?.let { android.net.Uri.parse(it).host?.lowercase() } } catch(_: Exception) { null }
        blacklistHost(mirrorHost)

        addResolutionLog("Gate stuck on $url. Rotating mirrors...")
        val video = _videoMetadata.value
        resolveNextServer(video?.id ?: "", url)
    }

    fun notifyMirrorDead(url: String) {
        val stuckHost = try { android.net.Uri.parse(url).host?.lowercase() } catch(_: Exception) { null }
        blacklistHost(stuckHost)
        
        val mirrorUrl = _currentServerUrl.value
        val mirrorHost = try { mirrorUrl?.let { android.net.Uri.parse(it).host?.lowercase() } } catch(_: Exception) { null }
        blacklistHost(mirrorHost)
    }

    fun checkConnection() {
        val current = VideoExtractor.getBaseUrl()
        viewModelScope.launch(Dispatchers.IO) {
        viewModelScope.launch(Dispatchers.IO) {
            val discovered = com.duta.movie.util.VideoExtractor.probeForNewDomain()
            withContext(Dispatchers.Main) {
                if (discovered != null && discovered != current) {
                    android.widget.Toast.makeText(context, "New domain found: $discovered", android.widget.Toast.LENGTH_SHORT).show()
                    fetchHomeData(force = true)
                } else if (discovered != null) {
                    android.widget.Toast.makeText(context, "Current domain is up to date", android.widget.Toast.LENGTH_SHORT).show()
                    fetchHomeData(force = true)
                } else {



























































        if (forceReset) rotationCount = 0
        startPlaybackResolution(videoId, serverUrl, forceReset = forceReset, targetEpisode = null)
    }

    fun playTVSeries(videoId: String, episodeUrl: String? = null, forceReset: Boolean = false, targetEpisode: Episode? = null, isRotation: Boolean = false) {
        Log.i("VideoViewModel", "playTVSeries called for $videoId | force: $forceReset | ep: ${targetEpisode?.name} | rotation: $isRotation")
        if (_isResolving.value && !forceReset && !isRotation) {
            Log.d("VideoViewModel", "playTVSeries ignored: already resolving")
            return
        }
            return
        }
        if (forceReset) rotationCount = 0
        startPlaybackResolution(videoId, serverUrl, forceReset = forceReset, targetEpisode = null)
    }

    fun playTVSeries(videoId: String, episodeUrl: String? = null, forceReset: Boolean = false, targetEpisode: Episode? = null, isRotation: Boolean = false) {
        Log.i("VideoViewModel", "playTVSeries called for $videoId | force: $forceReset | ep: ${targetEpisode?.name} | rotation: $isRotation")
        if (_isResolving.value && !forceReset && !isRotation) {
            Log.d("VideoViewModel", "playTVSeries ignored: already resolving")
            return
        }
        if (forceReset) rotationCount = 0
        
        if (_videoMetadata.value?.id != videoId && !isRotation) {
            _currentEpisode.value = null
        }
        
        _isResolving.value = true
        _error.value = null
        // Only clear log on fresh start or force reset, not on internal rotation
        if (forceReset || !isRotation) clearResolutionLog()
        
        if (targetEpisode != null) {
            _currentEpisode.value = targetEpisode
        }
        
        viewModelScope.launch {
            try {
                var video = getVideo(videoId)
                
                if (video == null || (video!!.isSeries == true && (video!!.servers.isEmpty() || video!!.episodes.isEmpty()))) {
                    videoRepository.fetchVideoDetails(videoId)?.let { detailed -> 
                        metadataCache[videoId] = detailed
                        video = detailed
                    }
                }
                
                if (video == null) {
                    _error.value = "Failed to load video metadata for $videoId."
                    _isResolving.value = false
                    return@launch
                }

                withContext(Dispatchers.Main) { _videoMetadata.value = video }
                
                val isEpisodeUrl = episodeUrl?.let { (it.contains("/eps/") || it.contains("/episode/") || it.contains("-episode-") || it.contains("/ep-")) && !it.contains("player=") && !it.contains("mirror=") } ?: false
                
                // SYNC AGAIN: After metadata fetch, re-check for matching episode
                if (video?.isSeries == true && (isEpisodeUrl || targetEpisode != null)) {
                    val filteredEps = video!!.episodes.filter { ep ->
                        val low = ep.name.lowercase()
                        !low.contains("lihat semua") && !low.contains("see all") && 
                        !low.contains("episode list") && !low.contains("daftar episode")
                    }
                    val primaryTargetUrl = if (isEpisodeUrl) episodeUrl!! else targetEpisode!!.url
                    val primarySlug = VideoExtractor.extractStableId(primaryTargetUrl)
                    val matchingEp = filteredEps.find { 
                        val epSlug = VideoExtractor.extractStableId(it.url)
                        epSlug == primarySlug || (primaryTargetUrl.contains(epSlug) && epSlug.length > 5) || (it.url.contains(primarySlug) && primarySlug.length > 5)
                    }
                    if (matchingEp != null) _currentEpisode.value = matchingEp
                }

                val episodePageUrl = if (isEpisodeUrl) episodeUrl 
                                     else if (targetEpisode != null) targetEpisode.url
                                     else if (video?.isSeries == true) _currentEpisode.value?.url 
                                     else null
                
                val recoveryUrl = episodePageUrl ?: (episodeUrl?.substringBefore('?') ?: "${VideoExtractor.getBaseUrl()}/tv/$videoId/").trimEnd('/') + "/"
                
                if (video != null && video!!.isSeries == true && (video!!.servers.isEmpty() || video!!.episodes.isEmpty() || isEpisodeUrl || targetEpisode != null)) {
                    addResolutionLog("Scoping metadata for: ${episodePageUrl ?: video!!.title}")
                    
                    var fetched = VideoExtractor.fetchVideoDetails(recoveryUrl)
                    
                    // OWL'S EYE: Series Recovery Logic
                    if ((fetched == null || fetched.episodes.isEmpty()) && isEpisodeUrl) {
                        val parentPath = recoveryUrl.substringBefore("/eps/").substringBefore("/episode/")
                        val parentUrl = if (parentPath.contains("/tv/")) parentPath else "$parentPath/tv/$videoId/"
                        addResolutionLog("Episode list missing. Attempting parent recovery: $parentUrl")
                        VideoExtractor.fetchVideoDetails(parentUrl)?.let { parentFetched ->
                            fetched = if (fetched == null) parentFetched else fetched!!.copy(episodes = parentFetched.episodes)
                        }
                    }

                    fetched?.let { f ->
                        // Merging episodes while preserving original count if higher
                        val mergedEps = (f.episodes + video!!.episodes).distinctBy { it.url }.sortedBy { it.name.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
                        
                        // OWL'S EYE: Strict Episode Isolation
                        val updatedServers = if (isEpisodeUrl || targetEpisode != null) f.servers else (video!!.servers + f.servers).distinctBy { it.url }

                        video = video!!.copy(
                            servers = updatedServers, 
                            episodes = mergedEps
                        )
                        metadataCache[videoId] = video!!.copy(servers = emptyList()) 
                        withContext(Dispatchers.Main) { _videoMetadata.value = video }
                        
                        if (isEpisodeUrl || targetEpisode != null) {
                            val primaryTargetUrl = if (isEpisodeUrl) episodeUrl!! else targetEpisode!!.url
                            val primarySlug = VideoExtractor.extractStableId(primaryTargetUrl)
                            val matchingEp = mergedEps.find { 
                                val epSlug = VideoExtractor.extractStableId(it.url)
                                epSlug == primarySlug || (primaryTargetUrl.contains(epSlug) && epSlug.length > 5) || (it.url.contains(primarySlug) && primarySlug.length > 5)
                            }
                            if (matchingEp != null) _currentEpisode.value = matchingEp
                        }
                    }
                }

                // Now call core resolution
                startPlaybackResolution(videoId, episodeUrl, forceReset = forceReset, targetEpisode = _currentEpisode.value)
                
            } catch (e: Exception) {
                Log.e("VideoViewModel", "TV Series Resolution Failed", e)
                _error.value = "TV Series Resolution Failed: ${e.message}"
                _isResolving.value = false
            }
        }
    }

    private fun startPlaybackResolution(videoId: String, serverUrl: String?, forceReset: Boolean, targetEpisode: Episode?) {
        Log.i("VideoViewModel", "startPlaybackResolution started for $videoId | serverUrl: ${serverUrl?.take(40)}")
        if (forceReset) {
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            android.webkit.CookieManager.getInstance().flush()
            deadMirrors.clear()
            rotationCount = 0
            com.duta.movie.util.NetworkConfig.clearSessionData()
            _resolvedUrl.value = null 
        }
        _isResolving.value = true; _error.value = null
        clearResolutionLog()
        
        if (targetEpisode != null) {
            _currentEpisode.value = targetEpisode
        }
        
        viewModelScope.launch {
            try {
                var video = getVideo(videoId) ?: run {
                    videoRepository.fetchVideoDetails(videoId)?.let { detailed -> 
                        metadataCache[videoId] = detailed
                    }
                    getVideo(videoId)
                }
                
                if (video == null) {
                    _error.value = "Failed to load video metadata."; _isResolving.value = false; return@launch
                }

                withContext(Dispatchers.Main) { _videoMetadata.value = video }

                // Owl's Eye Stability Logic: UA Rotation and Deep Reset
                val trendingContent = video!!.title.contains("Agent Kim", ignoreCase = true) || 
                                     video!!.title.contains("Manager Kim", ignoreCase = true)

                if (rotationCount > 4) {
                    val nextUA = when (rotationCount % 3) {
                        0 -> com.duta.movie.util.NetworkConfig.MOBILE_USER_AGENT
                        1 -> com.duta.movie.util.NetworkConfig.TV_USER_AGENT
                        else -> com.duta.movie.util.NetworkConfig.SHARED_USER_AGENT
                    }
                    addResolutionLog("High rotation count ($rotationCount). Shifting User-Agent strategy...")
                    com.duta.movie.util.VideoExtractor.setUserAgent(nextUA)
                }

                if (trendingContent && rotationCount > 12) {
                    addResolutionLog("EMERGENCY: Multiple Agent Kim mirror failures. Performing Deep Reset...")
                    android.webkit.CookieManager.getInstance().removeAllCookies(null)
                    com.duta.movie.util.NetworkConfig.clearSessionData()
                    delay(500)
                }

                val isEpisodeUrl = serverUrl?.let { (it.contains("/eps/") || it.contains("/episode/") || it.contains("-episode-") || it.contains("/ep-")) && !it.contains("player=") && !it.contains("mirror=") } ?: false
                
                val sortedServers = video!!.servers
                    .filter { !VideoExtractor.isDirectVideoUrl(it.url) || !video!!.isSeries!! } 
                    .sortedByDescending { s -> 
                        val host = try { android.net.Uri.parse(s.url).host?.lowercase() ?: "" } catch(_: Exception) { "" }
                        val weight = sourceWeights.value[host] ?: 0
                        VideoExtractor.getProviderPriority(s.name, s.url) + weight
                    }

                // CRITICAL FIX: If we have a specific episode URL, it MUST be the primary mirror.
                val mirrorToResolve = if (isEpisodeUrl) serverUrl!!
                                     else if (serverUrl != null) serverUrl
                                     else sortedServers.firstOrNull()?.url ?: video!!.videoUrl
                
                val episodePageUrl = if (isEpisodeUrl) serverUrl 
                                     else if (targetEpisode != null) targetEpisode.url
                                     else if (video?.isSeries == true) _currentEpisode.value?.url 
                                     else null

                val primaryUrl = episodePageUrl ?: mirrorToResolve
                
                if (trendingContent) {
                    Log.i("OwlEyeMonitoring", "TRACKING TRENDING SERIES: ${video!!.title} | Ep: ${_currentEpisode.value?.name} | Mirror: $mirrorToResolve")
                    addResolutionLog("Enforcing High-Demand resolution for Agent Kim...")
                }

                _currentServerUrl.value = mirrorToResolve

                if (video!!.isSeries == true && _currentEpisode.value == null) {
                    val filteredEps = video!!.episodes.filter { ep ->
                        val low = ep.name.lowercase()
                        !low.contains("lihat semua") && !low.contains("see all") && 
                        !low.contains("episode list") && !low.contains("daftar episode")
                    }
                }

                if (VideoExtractor.isDirectVideoUrl(mirrorToResolve)) {
                    addResolutionLog("Direct Stream Identified. Instant Handshake engaged.")
                    withContext(Dispatchers.Main) { 
                        _resolvedUrl.value = mirrorToResolve
                        // OWL'S EYE: State Lock - Keep isResolving=true until playback success
                        // to prevent multiple overlapping resolution calls.
                    }; return@launch
                }

                val mirrorsFromMetadata = sortedServers.map { it.url }.toSet()
                val primarySlug = VideoExtractor.extractStableId(primaryUrl)
                var topMirrors = (listOf(mirrorToResolve, primaryUrl) + sortedServers.map { it.url })
                    .distinct()
                    .filter { url -> 
                        val host = try { android.net.Uri.parse(url).host?.lowercase() } catch(_: Exception) { null }
                        val isSameEp = VideoExtractor.extractStableId(url) == primarySlug || 
                                       url.contains(primarySlug) || 
                                       mirrorsFromMetadata.contains(url)

                        // IRON RULE: The content provider base URL domain is ALWAYS allowed.
                        // This overrides any DataStore state — prevents async timing bugs where the
                        // startup scrub hasn't propagated yet when this filter runs.
                        val baseHost = try { android.net.Uri.parse(VideoExtractor.getBaseUrl()).host?.lowercase() } catch(_: Exception) { null }
                        if (host != null && host == baseHost) return@filter (isSameEp || !video!!.isSeries!!)

                        // Expand permanent blocks to include ALL cluster aliases of blocked hosts
                        val permanentBlocks = forceWebViewHosts.value
                        val expandedBlocks = permanentBlocks + domainClusters
                            .filter { cluster -> cluster.any { permanentBlocks.contains(it) } }
                            .flatten()
                        (host == null || (!deadMirrors.contains(host) && !expandedBlocks.contains(host))) && (isSameEp || !video!!.isSeries!!)
                    }
                    .take(if (trendingContent) 12 else 6)
                
                val skipDirectRace = trendingContent && mirrorsFromMetadata.isEmpty() && !VideoExtractor.isDirectVideoUrl(mirrorToResolve)
                
                val isEpisodePage = mirrorToResolve.let { 
                    (it.contains("/eps/") || it.contains("/episode/") || it.contains("-episode-")) && !it.contains("player=")
                }

                if (topMirrors.isEmpty() || skipDirectRace) {
                    if (skipDirectRace && isEpisodePage) {
                        // Episode page URL with no metadata mirrors — synthesize player variants.
                        // The ?player=N structure is consistent on ohionewsnow episode pages.
                        // HGCloud first (4,5,7), then IndoStream (2,3,6,8).
                        val base = mirrorToResolve.trimEnd('/')
                        val baseHost = try { android.net.Uri.parse(VideoExtractor.getBaseUrl()).host?.lowercase() } catch(_: Exception) { null }
                        val urlHost = try { android.net.Uri.parse(base).host?.lowercase() } catch(_: Exception) { null }
                        if (urlHost == baseHost) {
                            topMirrors = listOf(4, 5, 7, 1, 2, 3, 6, 8).map { "$base/?player=$it" }
                            addResolutionLog("Episode page detected. Synthesizing player variants for race...")
                        } else {
                            addResolutionLog("No mirrors found in metadata. Fast-tracking to WebView Shield...")
                            topMirrors = listOf(mirrorToResolve)
                        }
                    } else {
                        if (skipDirectRace) addResolutionLog("No mirrors found in metadata. Fast-tracking to WebView Shield...")
                        topMirrors = listOf(mirrorToResolve)
                    }
                }

                val actuallySkipRace = topMirrors.size == 1 && skipDirectRace && !isEpisodePage

                addResolutionLog("RACING TOP ${topMirrors.size} VIP MIRRORS (God Mode)...")
                _resolutionProgress.value = "Establishing Instant Connection..."
                val startTime = System.currentTimeMillis()

                val winner = if (actuallySkipRace) null else withContext(Dispatchers.IO) {
                    val resultChannel = kotlinx.coroutines.channels.Channel<VideoExtractor.ExtractionResult?>(topMirrors.size)
                    val jobs = topMirrors.mapIndexed { idx, url ->
                        launch {
                            try {
                                val result = VideoExtractor.extractVideoUrl(url, referer = primaryUrl)
                                if (result != null) {
                                    val winUrl = result.videoUrl
                                    
                                    // Reject static resources
                                    if (VideoExtractor.isStaticResource(winUrl)) {
                                        return@launch
                                    }
                                    
                                    // Reject results from the content provider's own domain
                                    // (these are episode pages, not video streams)
                                    val winHost = try { android.net.Uri.parse(winUrl).host?.lowercase() } catch(_: Exception) { null }
                                    val baseHost = try { android.net.Uri.parse(VideoExtractor.getBaseUrl()).host?.lowercase() } catch(_: Exception) { null }
                                    if (winHost != null && (winHost == baseHost || 
                                        primaryUrl.contains(winHost) || winUrl.contains("/eps/") || winUrl.contains("/episode/"))) {
                                        Log.d("VideoViewModel", "Race: Rejected content page URL: ${winUrl.take(50)}")
                                        return@launch
                                    }

                                    val isDirect = VideoExtractor.isDirectVideoUrl(winUrl)
                                    val isJs = VideoExtractor.isJsOnlyHost(winUrl)
                                    
                                    if (isDirect || isJs) {
                                         // Enforce tiered priority: lower idx = higher priority.
                                         // Give lower priority mirrors a handicap delay so higher priority mirrors
                                         // have a chance to win if they are only slightly slower.
                                         val handicapMs = idx * 250L
                                         if (handicapMs > 0) kotlinx.coroutines.delay(handicapMs)
                                         resultChannel.send(result)
                                    }
                                }
                            } catch (e: Exception) { 
                                if (e !is CancellationException) {
                                    Log.w("VideoViewModel", "Mirror race failure for $url: ${e.message}")
                                } else {
                                    Log.v("VideoViewModel", "Mirror race job cancelled for $url")
                                }
                            }
                        }
                    }
                    
                    val firstWinner = withTimeoutOrNull<VideoExtractor.ExtractionResult?>(if (trendingContent) 6000 else 8000) {
                        resultChannel.receive()
                    }
                    jobs.forEach { it.cancel() } 
                    firstWinner
                }

                val resolutionTime = System.currentTimeMillis() - startTime

                if (winner != null) {
                    addResolutionLog("VIP WINNER FOUND in ${resolutionTime}ms: ${winner.videoUrl.take(30)}...")
                    withContext(Dispatchers.Main) {
                        _resolvedUrl.value = winner.videoUrl
                        _lastReferer.value = winner.referer; _lastCookies.value = winner.cookies
                        _resolutionProgress.value = null
                        _isResolving.value = false 
                    }
                } else {
                    addResolutionLog("Direct resolution failed after ${resolutionTime}ms. Engaging WebView Shield.")
                    withContext(Dispatchers.Main) {
                        _lastReferer.value = primaryUrl
                        _resolvedUrl.value = mirrorToResolve
                        _resolutionProgress.value = null
                        _isResolving.value = false 
                    }
                }
            } catch (e: Exception) { _error.value = "Resolution Failed: ${e.message}"; _isResolving.value = false }
        }
    }

    fun updateExtractedUrl(videoId: String, url: String?, referer: String? = null, cookies: String? = null) {
        var finalUrl = url
        // OWL'S EYE: Base64 Decoder for wrapped streams (common in Agent Kim mirrors)
        if (url != null && (!url.startsWith("http") || url.contains(" ") || url.length > 200)) {
            try {
                val potentialBase64 = if (url.contains("=")) url.substringAfterLast("=") else url
                if (potentialBase64.length > 20 && !potentialBase64.contains("/")) {
                    val decoded = String(android.util.Base64.decode(potentialBase64, android.util.Base64.DEFAULT))
                    if (decoded.startsWith("http")) {
                        Log.i("VideoViewModel", "Owl's Eye: Successfully decoded Base64 stream: $decoded")
                        finalUrl = decoded
                    }
                }
            } catch(_: Exception) {}
        }
            try { val more = videoRepository.fetchVideosBySection(category, page = page + 1, count = 150); if (more.isNotEmpty()) { updateMetadataCache(more); _categoryVideos.update { current -> val existing = current[category] ?: emptyList(); val ids = existing.map { it.id }.toSet(); current + (category to (existing + more.filter { it.id !in ids })) }; categoryPages[category] = page + (more.size / 20) } } finally { _categoryLoading.update { it + (category to false) } }
        }
    }

    fun resolveNextServer(videoId: String, currentServerUrl: String?) {
        if (isRotationLocked) {
            Log.d("VideoViewModel", "resolveNextServer ignored: Rotation Locked")
            return
        }
        isRotationLocked = true
        
        viewModelScope.launch {
            try {
                val video = _videoMetadata.value?.takeIf { it.id == videoId } ?: getVideo(videoId) ?: return@launch
                rotationCount++
                
                val isTrending = video.title.contains("Agent Kim", ignoreCase = true) || video.title.contains("Manager Kim", ignoreCase = true)
                
                val maxRotation = if (isTrending) 30 else 8
                if (rotationCount > maxRotation) {
                    _error.value = "All mirrors for ${video.title} are currently under heavy load. Please try again in 5 minutes or use a different server."
                    _isResolving.value = false
                    return@launch
                }
    
                // Important: Use the servers from the current metadata state if available
                val serversToUse = if (video.servers.isNotEmpty()) video.servers else {
                     val epUrl = _currentEpisode.value?.url ?: currentServerUrl
                     if (epUrl != null) {
                        addResolutionLog("No mirrors in state. Retrying episode page resolution.")
                        playTVSeries(videoId, epUrl, forceReset = false)
                     }
                     return@launch
                }
    
                val sortedServers = serversToUse.filter { s ->
                    val host = try { android.net.Uri.parse(s.url).host?.lowercase() ?: "" } catch(_: Exception) { "" }
                    !deadMirrors.contains(host)
                }.sortedByDescending { s -> 
                    val host = try { android.net.Uri.parse(s.url).host?.lowercase() ?: "" } catch(_: Exception) { "" }
                    val weight = sourceWeights.value[host] ?: 0
                    val mirrorStability = if (s.url.contains("indostream") || s.url.contains("amt")) 20 else 0
                    VideoExtractor.getProviderPriority(s.name, s.url) + weight + mirrorStability
                }
                
                if (sortedServers.isEmpty()) {
                    if (rotationCount < 20) {
                        addResolutionLog("No fresh mirrors available. Retrying after partial reset...")
                        // Only clear non-critical dead mirrors first? 
                        // For now, let's just clear and let maxRotation handle the hard stop.
                        deadMirrors.clear()
                        _isResolving.value = false
                        if (video.isSeries == true) {
                            playTVSeries(videoId, serversToUse.firstOrNull()?.url, forceReset = false, isRotation = true)
                        } else {
                            playMovie(videoId, serversToUse.firstOrNull()?.url, forceReset = false, isRotation = true)
                        }
                    } else {
                        _error.value = "All servers are currently restricted or down. Please try another source or try again later."
                        _isResolving.value = false
                    }
                    return@launch
                }

                val activeUrl = currentServerUrl ?: _currentServerUrl.value
                val currentIndex = sortedServers.indexOfFirst { it.url == activeUrl }
                val nextIndex = if (currentIndex == -1 || currentIndex == sortedServers.size - 1) 0 else currentIndex + 1
                
                val nextServer = sortedServers[nextIndex]
                Log.i("VideoViewModel", "Mirror Rotation: Shifting to ${nextServer.name} (${nextServer.url.take(30)}...)")
                addResolutionLog("Mirror Rotation: Shifting to ${nextServer.name}...")
                
                if (video.isSeries == true) {
                    playTVSeries(videoId, nextServer.url, forceReset = false, isRotation = true)
                } else {
                    playMovie(videoId, nextServer.url, forceReset = false, isRotation = true)
                }
                
                val rotationDelay = if (isTrending) 10000L else 5000L

    fun loadMoreForCategoryRow(category: String) {
        if (_categoryLoading.value[category] == true) return
        viewModelScope.launch {
            val page = categoryPages[category] ?: 1; _categoryLoading.update { it + (category to true) }
            try { val more = videoRepository.fetchVideosBySection(category, page = page + 1, count = 150); if (more.isNotEmpty()) { updateMetadataCache(more); _categoryVideos.update { current -> val existing = current[category] ?: emptyList(); val ids = existing.map { it.id }.toSet(); current + (category to (existing + more.filter { it.id !in ids })) }; categoryPages[category] = page + (more.size / 20) } } finally { _categoryLoading.update { it + (category to false) } }
        }
    }

    fun resolveNextServer(videoId: String, currentServerUrl: String?) {
        if (isRotationLocked) {
            Log.d("VideoViewModel", "resolveNextServer ignored: Rotation Locked")
            return
        }
        isRotationLocked = true
        
        viewModelScope.launch {
            try {
                val video = _videoMetadata.value?.takeIf { it.id == videoId } ?: getVideo(videoId) ?: return@launch
                rotationCount++
                
                val isTrending = video.title.contains("Agent Kim", ignoreCase = true) || video.title.contains("Manager Kim", ignoreCase = true)
                
                val maxRotation = if (isTrending) 30 else 8
                if (rotationCount > maxRotation) {
                    _error.value = "All mirrors for ${video.title} are currently under heavy load. Please try again in 5 minutes or use a different server."
                    _isResolving.value = false
                    return@launch
                }
    
                // Important: Use the servers from the current metadata state if available
                val serversToUse = if (video.servers.isNotEmpty()) video.servers else {
                     val epUrl = _currentEpisode.value?.url ?: currentServerUrl
                     if (epUrl != null) {
                        addResolutionLog("No mirrors in state. Retrying episode page resolution.")
                        playTVSeries(videoId, epUrl, forceReset = false)
                     }
                     return@launch
                }
    
                }
    
                // Important: Use the servers from the current metadata state if available
                val serversToUse = if (video.servers.isNotEmpty()) video.servers else {
                     val currentEp = _currentEpisode.value
                     val epUrl = currentEp?.url ?: currentServerUrl
                     if (epUrl != null) {
                        addResolutionLog("No mirrors in state. Retrying episode page resolution.")
                        // Pass the current episode to ensure we stay on the right episode (not series page)
                        playTVSeries(videoId, epUrl, forceReset = false, targetEpisode = currentEp)
                     }
                     return@launch
                }
    
                val sortedServers = serversToUse.filter { s ->
                    val host = try { android.net.Uri.parse(s.url).host?.lowercase() ?: "" } catch(_: Exception) { "" }
                    !deadMirrors.contains(host)
                }.sortedByDescending { s -> 
                    val host = try { android.net.Uri.parse(s.url).host?.lowercase() ?: "" } catch(_: Exception) { "" }
                    val weight = sourceWeights.value[host] ?: 0
                    val mirrorStability = if (s.url.contains("indostream") || s.url.contains("amt")) 20 else 0
                    VideoExtractor.getProviderPriority(s.name, s.url) + weight + mirrorStability
                }
                
                if (sortedServers.isEmpty()) {
                    if (rotationCount < 20) {
                        addResolutionLog("No fresh mirrors available. Retrying after partial reset...")
                        // Partial reset: clear dead mirrors BUT keep permanently gate-blocked
                        // hosts AND their cluster aliases so they don't re-enter the race
                        val permanentlyBlocked = forceWebViewHosts.value
                        val expandedPermanent = permanentlyBlocked + domainClusters
                            .filter { cluster -> cluster.any { permanentlyBlocked.contains(it) } }
                            .flatten()
                        deadMirrors.removeAll { host -> !expandedPermanent.contains(host) }
                        _isResolving.value = false
                        if (video.isSeries == true) {
                            playTVSeries(videoId, serversToUse.firstOrNull()?.url, forceReset = false, isRotation = true)
                        } else {
                            playMovie(videoId, serversToUse.firstOrNull()?.url, forceReset = false, isRotation = true)
                        }
                    } else {
                        _error.value = "All servers are currently restricted or down. Please try another source or try again later."
                        _isResolving.value = false
                    }
                    return@launch
                }

                val activeUrl = currentServerUrl ?: _currentServerUrl.value
                val currentIndex = sortedServers.indexOfFirst { it.url == activeUrl }
                val nextIndex = if (currentIndex == -1 || currentIndex == sortedServers.size - 1) 0 else currentIndex + 1
                
                val nextServer = sortedServers[nextIndex]
                Log.i("VideoViewModel", "Mirror Rotation: Shifting to ${nextServer.name} (${nextServer.url.take(30)}...)")
                addResolutionLog("Mirror Rotation: Shifting to ${nextServer.name}...")
                
                if (video.isSeries == true) {
                    playTVSeries(videoId, nextServer.url, forceReset = false, isRotation = true)
                } else {
                    playMovie(videoId, nextServer.url, forceReset = false, isRotation = true)
                }
                
            val low = ep.name.lowercase()
            !low.contains("lihat semua") && !low.contains("see all") && 
            !low.contains("episode list") && !low.contains("daftar episode") &&
            !low.contains("next") && !low.contains("prev") && !low.contains("halaman")
        }
        
        val currentEp = _currentEpisode.value
        val currentIdx = if (currentEp != null) {
            val idx = episodes.indexOfFirst { it.url == currentEp.url || it.id == currentEp.id }
            if (idx == -1) {
            val activeUrl = _currentServerUrl.value ?: ""
            val activeSlug = VideoExtractor.extractStableId(activeUrl)
            episodes.indexOfFirst { it.url == activeUrl || VideoExtractor.extractStableId(it.url) == activeSlug || it.url.contains(activeSlug) }
        }
        
        Log.d("VideoViewModel", "Next Episode Engine: FoundIdx=$currentIdx, TotalEps=${episodes.size}, ActiveEp=${currentEp?.name}")

        if (currentIdx != -1 && currentIdx < episodes.size - 1) {
            val nextEp = episodes[currentIdx + 1]
            Log.i("VideoViewModel", "Owl's Eye: Progressing to Next Episode: ${nextEp.name}")
            playTVSeries(videoId, nextEp.url, forceReset = true, targetEpisode = nextEp)
            return true
        }
        
        if (currentIdx == -1) {
             Log.w("VideoViewModel", "Episode position lost. Resetting search via slug.")
        }
