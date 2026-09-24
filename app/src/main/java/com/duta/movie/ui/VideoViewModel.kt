package com.duta.movie.ui
import com.duta.movie.R

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
import com.duta.movie.model.Comment
import com.duta.movie.data.remote.CommentService
import com.duta.movie.data.remote.RecommendationService
import com.duta.movie.model.Recommendation
import com.duta.movie.model.toVideo
import com.duta.movie.util.SubtitleExtractor
import com.duta.movie.util.SubtitleParser
import com.duta.movie.util.ParsedSubtitleCue
import com.duta.movie.util.VideoExtractor
import com.duta.movie.util.NetworkConfig
import java.io.File
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

enum class SearchFilter(val label: String) {
    ALL("All"),
    MOVIES("Movies"),
    SERIES("Series"),
    MALAY("Malay"),
    PRAMLEE("P. Ramlee")
}

enum class SearchSort(val label: String) {
    RELEVANCE("Best Match"),
    NEWEST("Newest"),
    RATING("Top Rated")
}

@HiltViewModel
class VideoViewModel @Inject constructor(
    private val videoRepository: VideoRepository,
    private val imageLoader: ImageLoader,
    private val preferenceManager: PreferenceManager,
    private val commentService: CommentService,
    private val recommendationService: RecommendationService,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _comments = MutableStateFlow<List<Comment>>(emptyList())
    val comments: StateFlow<List<Comment>> = _comments.asStateFlow()

    private val _isCommentsLoading = MutableStateFlow(false)
    val isCommentsLoading: StateFlow<Boolean> = _isCommentsLoading.asStateFlow()

    private val _isSubmittingComment = MutableStateFlow(false)
    val isSubmittingComment: StateFlow<Boolean> = _isSubmittingComment.asStateFlow()

    val userNickname: StateFlow<String> = preferenceManager.userNickname
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val myCommentIds: StateFlow<Set<String>> = preferenceManager.myCommentIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _pakcikRekomenVideos = MutableStateFlow<List<Video>>(emptyList())
    val pakcikRekomenVideos: StateFlow<List<Video>> = _pakcikRekomenVideos.asStateFlow()

    private val _isPakcikRekomenLoading = MutableStateFlow(false)
    val isPakcikRekomenLoading: StateFlow<Boolean> = _isPakcikRekomenLoading.asStateFlow()

    val myRecommendedVideoIds: StateFlow<Set<String>> = preferenceManager.myRecommendedVideoIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _recommendationCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val recommendationCounts: StateFlow<Map<String, Int>> = _recommendationCounts.asStateFlow()

    fun getRecommendCount(videoId: String): StateFlow<Int> {
        return recommendationCounts.map { map ->
            map[videoId] ?: (_pakcikRekomenVideos.value.find { it.id == videoId }?.views?.toIntOrNull() ?: 0)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    }

    fun isRecommended(videoId: String): StateFlow<Boolean> {
        return myRecommendedVideoIds.map { it.contains(videoId) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    }

    private var pakcikSyncJob: Job? = null

    fun fetchPakcikRekomenVideos(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent && _pakcikRekomenVideos.value.isEmpty()) {
                _isPakcikRekomenLoading.value = true
            }
            try {
                val res = recommendationService.getRecommendations()
                res.onSuccess { recs ->
                    val newVideos = recs.map { it.toVideo() }
                    val counts = recs.associate { it.videoId to it.recommendCount }
                    _recommendationCounts.update { current -> current + counts }
                    if (_pakcikRekomenVideos.value != newVideos) {
                        _pakcikRekomenVideos.value = newVideos
                    }
                    updateMetadataCache(newVideos, triggerBackground = false)
                    viewModelScope.launch(Dispatchers.IO) {
                        videoRepository.insertOrUpdateVideos(newVideos)
                    }
                }.onFailure { e ->
                    Log.e("VideoViewModel", "Failed to fetch Pakcik Rekomen videos", e)
                }
            } catch (e: Exception) {
                Log.e("VideoViewModel", "Error fetching Pakcik Rekomen videos", e)
            } finally {
                _isPakcikRekomenLoading.value = false
            }
        }
    }

    fun startPakcikRekomenAutoSync() {
        if (pakcikSyncJob?.isActive == true) return
        pakcikSyncJob = viewModelScope.launch {
            while (isActive) {
                delay(20_000L) // Silent background auto-sync every 20 seconds
                try {
                    val res = recommendationService.getRecommendations()
                    res.onSuccess { recs ->
                        val newVideos = recs.map { it.toVideo() }
                        val counts = recs.associate { it.videoId to it.recommendCount }
                        _recommendationCounts.update { current -> current + counts }
                        if (_pakcikRekomenVideos.value != newVideos) {
                            _pakcikRekomenVideos.value = newVideos
                        }
                        updateMetadataCache(newVideos, triggerBackground = false)
                        viewModelScope.launch(Dispatchers.IO) {
                            videoRepository.insertOrUpdateVideos(newVideos)
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun toggleRecommendation(video: Video) {
        val currentSet = myRecommendedVideoIds.value
        val isCurrentlyRecommended = currentSet.contains(video.id)
        val shouldRecommend = !isCurrentlyRecommended

        val currentCount = _recommendationCounts.value[video.id]
            ?: (_pakcikRekomenVideos.value.find { it.id == video.id }?.views?.toIntOrNull() ?: 0)
        val newCount = if (shouldRecommend) currentCount + 1 else (currentCount - 1).coerceAtLeast(0)
        _recommendationCounts.update { it + (video.id to newCount) }

        // Instant optimistic update of the Pakcik Rekomen row for zero-delay UI response
        val previousPakcikList = _pakcikRekomenVideos.value
        _pakcikRekomenVideos.update { current ->
            val existingIndex = current.indexOfFirst { it.id == video.id }
            if (shouldRecommend) {
                val updatedVideo = video.copy(views = newCount.toString())
                if (existingIndex >= 0) {
                    val mutable = current.toMutableList()
                    mutable[existingIndex] = updatedVideo
                    mutable
                } else {
                    listOf(updatedVideo) + current
                }
            } else {
                if (newCount <= 0) {
                    current.filterNot { it.id == video.id }
                } else {
                    val updatedVideo = video.copy(views = newCount.toString())
                    if (existingIndex >= 0) {
                        val mutable = current.toMutableList()
                        mutable[existingIndex] = updatedVideo
                        mutable
                    } else current
                }
            }
        }

        viewModelScope.launch {
            if (shouldRecommend) {
                preferenceManager.addRecommendedVideoId(video.id)
            } else {
                preferenceManager.removeRecommendedVideoId(video.id)
            }

            val res = recommendationService.toggleRecommendation(video, shouldRecommend, currentCount)
            res.onSuccess { updatedRec ->
                _recommendationCounts.update { it + (video.id to updatedRec.recommendCount) }
                // Silent background sync
                fetchPakcikRekomenVideos()
            }.onFailure { e ->
                Log.e("VideoViewModel", "Failed to sync recommendation for ${video.id}", e)
                // Revert on network failure
                _recommendationCounts.update { it + (video.id to currentCount) }
                _pakcikRekomenVideos.value = previousPakcikList
                if (shouldRecommend) {
                    preferenceManager.removeRecommendedVideoId(video.id)
                } else {
                    preferenceManager.addRecommendedVideoId(video.id)
                }
            }
        }
    }

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

    private val _searchResultsVideos = MutableStateFlow<List<Video>>(emptyList())
    val searchResultsVideos: StateFlow<List<Video>> = _searchResultsVideos.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchFilter = MutableStateFlow(SearchFilter.ALL)
    val searchFilter: StateFlow<SearchFilter> = _searchFilter.asStateFlow()

    private val _searchSort = MutableStateFlow(SearchSort.RELEVANCE)
    val searchSort: StateFlow<SearchSort> = _searchSort.asStateFlow()

    private val _recentSearches = MutableStateFlow<List<String>>(listOf("Avatar", "Spider-Man", "Batman", "John Wick", "Munafik", "Bujang Lapok"))
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    private var searchDebounceJob: Job? = null

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _lastCompletedSearchQuery = MutableStateFlow<String?>(null)
    val lastCompletedSearchQuery: StateFlow<String?> = _lastCompletedSearchQuery.asStateFlow()

    // TV D-Pad Focus Restoration across navigation
    var lastFocusedCategoryRowIndex: Int = 0
    var lastFocusedHomeVideoId: String? = null
    var pendingRestoreVideoId: String? = null

    fun setSearchFilter(filter: SearchFilter) { _searchFilter.value = filter }
    fun setSearchSort(sort: SearchSort) { _searchSort.value = sort }

    fun clearSearchQuery() {
        searchDebounceJob?.cancel()
        searchJob?.cancel()
        _searchQuery.value = ""
        _searchResultsVideos.value = emptyList()
        _isSearching.value = false
        _lastCompletedSearchQuery.value = null
        _error.value = null
    }

    fun removeRecentSearch(query: String) {
        _recentSearches.update { list -> list.filterNot { it.equals(query, ignoreCase = true) } }
    }

    fun addRecentSearch(query: String) {
        if (query.isBlank() || query.length < 2) return
        _recentSearches.update { list ->
            val clean = query.trim()
            (listOf(clean) + list.filterNot { it.equals(clean, ignoreCase = true) }).take(10)
        }
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isDetailLoading = MutableStateFlow(false)
    val isDetailLoading: StateFlow<Boolean> = _isDetailLoading.asStateFlow()

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
    var isPlaybackActive: Boolean = false
        private set

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
    private val _subtitleCues = MutableStateFlow<List<ParsedSubtitleCue>>(emptyList())
    val subtitleCues: StateFlow<List<ParsedSubtitleCue>> = _subtitleCues.asStateFlow()
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
    val episodeServersCache = ConcurrentHashMap<String, List<VideoServer>>()
    val filterResultCache = ConcurrentHashMap<String, Boolean>()
    private val moviePath = VideoExtractor.normalizePath("/movies/")
    private val seriesPath = VideoExtractor.normalizePath("/series/")

    enum class CategoryGroup(val priority: Int, val label: String, val tag: String) {
        CORE(1, "Core Catalogs", "CORE"),
        REGIONAL(2, "Regional Cinema", "REGION"),
        STREAMING(3, "Streaming Platforms", "STREAM"),
        GENRE(4, "Genres", "GENRE"),
        YEAR(5, "Release Years", "YEAR"),
        OTHER(6, "Other", "MORE")
    }

    companion object {
        val CURATED_CORE_ORDER = listOf(
            "/",
            "/movies/",
            "/series/",
            "/top-imdb/",
            "/most-viewed/"
        ).map { VideoExtractor.normalizePath(it) }

        val CURATED_REGIONAL_ORDER = listOf(
            "/country/malaysia/",
            "/category/p-ramlee/",
            "/country/vietnam/",
            "/country/viet-nam/",
            "/country/indonesia/",
            "/country/korea/",
            "/country/thailand/",
            "/country/japan/",
            "/country/china/",
            "/country/hong-kong/",
            "/country/india/",
            "/country/usa/",
            "/country/united-states/",
            "/country/united-kingdom/"
        ).map { VideoExtractor.normalizePath(it) }

        val CURATED_STREAMING_ORDER = listOf(
            "/network/netflix/",
            "/network/disney/",
            "/network/apple-tv/",
            "/network/hbo/",
            "/network/amazon/"
        ).map { VideoExtractor.normalizePath(it) }

        val CURATED_GENRE_ORDER = listOf(
            "/genre/subbed/malay-subbed/",
            "/genre/dubbed/malay/",
            "/genre/action/",
            "/genre/animation/",
            "/genre/comedy/",
            "/genre/drama/",
            "/genre/horror/",
            "/genre/romance/",
            "/genre/science-fiction/",
            "/genre/thriller/",
            "/genre/adventure/",
            "/genre/crime/",
            "/genre/fantasy/",
            "/genre/mystery/"
        ).map { VideoExtractor.normalizePath(it) }

        val PRIORITY_PATHS = (
            CURATED_CORE_ORDER +
            CURATED_REGIONAL_ORDER +
            CURATED_STREAMING_ORDER +
            CURATED_GENRE_ORDER
        ).distinct()

        fun getCategoryGroup(path: String, name: String): CategoryGroup {
            val normPath = VideoExtractor.normalizePath(path)
            val lowPath = normPath.lowercase()
            val lowName = name.trim().lowercase()

            return when {
                normPath == "/" || lowPath.contains("/movie") || lowPath.contains("/series") || lowPath.contains("/serial-tv") || lowPath.contains("/tv") || lowPath.contains("box-office") || lowPath.contains("top-imdb") || lowPath.contains("most-viewed") ||
                lowName in listOf("newly updated", "movies", "movie", "tv series", "serial tv", "series", "box-office", "top imdb", "most viewed") -> CategoryGroup.CORE

                lowPath.startsWith("/country/") || lowPath.contains("p-ramlee") ||
                lowName in listOf("malaysia", "p.ramlee", "viet nam", "vietnam", "indonesia", "korea", "thailand", "japan", "china", "hong kong", "india", "usa", "united states", "united kingdom", "uk", "australia", "canada", "france", "germany", "italy", "philippines", "spain", "taiwan", "russia", "netherlands") -> CategoryGroup.REGIONAL

                lowPath.startsWith("/network/") ||
                lowName in listOf("netflix", "disney+", "disney", "apple tv+", "apple tv", "hbo", "hbo max", "amazon prime", "amazon", "paramount+", "hulu", "peacock") -> CategoryGroup.STREAMING

                lowPath.startsWith("/genre/") || lowPath.contains("/animasi") ||
                lowName in listOf("action", "adventure", "animasi", "anime", "animation", "biography", "comedy", "crime", "documentary", "drama", "family", "fantasy", "history", "horror", "music", "mystery", "romance", "sci-fi", "science fiction", "sport", "thriller", "war", "western") -> CategoryGroup.GENRE

                lowPath.startsWith("/release-year/") || lowPath.startsWith("/release/") || lowPath.startsWith("/year/") || lowName.matches(Regex("""^(19|20)\d{2}$""")) -> CategoryGroup.YEAR

                else -> CategoryGroup.OTHER
            }
        }

        fun getIntraGroupRank(group: CategoryGroup, path: String, name: String): Int {
            val normPath = VideoExtractor.normalizePath(path)
            val lowName = name.trim().lowercase()

            return when (group) {
                CategoryGroup.CORE -> {
                    val idx = CURATED_CORE_ORDER.indexOf(normPath)
                    if (idx != -1) idx else {
                        when {
                            lowName.contains("newly") || normPath == "/" -> 0
                            lowName.contains("movie") || normPath.contains("/movie") -> 1
                            lowName.contains("series") || lowName.contains("serial") || normPath.contains("/series") -> 2
                            lowName.contains("imdb") || normPath.contains("imdb") -> 3
                            lowName.contains("viewed") || normPath.contains("viewed") -> 4
                            else -> 100
                        }
                    }
                }
                CategoryGroup.REGIONAL -> {
                    val idx = CURATED_REGIONAL_ORDER.indexOf(normPath)
                    if (idx != -1) idx else {
                        when {
                            lowName.contains("malaysia") -> 0
                            lowName.contains("p-ramlee") || lowName.contains("p.ramlee") -> 1
                            lowName.contains("viet") -> 2
                            lowName.contains("indonesia") -> 3
                            lowName.contains("korea") -> 4
                            lowName.contains("thailand") -> 5
                            lowName.contains("japan") -> 6
                            lowName.contains("china") -> 7
                            lowName.contains("hong kong") -> 8
                            lowName.contains("india") -> 9
                            lowName.contains("usa") || lowName.contains("united states") -> 10
                            lowName.contains("united kingdom") || lowName == "uk" -> 11
                            else -> 500
                        }
                    }
                }
                CategoryGroup.STREAMING -> {
                    val idx = CURATED_STREAMING_ORDER.indexOf(normPath)
                    if (idx != -1) idx else {
                        when {
                            lowName.contains("netflix") -> 0
                            lowName.contains("disney") -> 1
                            lowName.contains("apple") -> 2
                            lowName.contains("hbo") -> 3
                            lowName.contains("amazon") -> 4
                            else -> 500
                        }
                    }
                }
                CategoryGroup.GENRE -> {
                    val idx = CURATED_GENRE_ORDER.indexOf(normPath)
                    if (idx != -1) idx else {
                        when {
                            lowName == "action" -> 0
                            lowName in listOf("animasi", "anime") -> 1
                            lowName == "comedy" -> 2
                            lowName == "drama" -> 3
                            lowName == "horror" -> 4
                            lowName == "romance" -> 5
                            lowName in listOf("sci-fi", "science fiction") -> 6
                            lowName == "thriller" -> 7
                            lowName == "adventure" -> 8
                            lowName == "crime" -> 9
                            lowName == "fantasy" -> 10
                            lowName == "mystery" -> 11
                            lowName == "animation" -> 12
                            else -> 500
                        }
                    }
                }
                CategoryGroup.YEAR -> {
                    val yearDigits = Regex("""\b(19\d{2}|20\d{2})\b""").find(name)?.value
                        ?: Regex("""\b(19\d{2}|20\d{2})\b""").find(path)?.value
                    val yearNum = yearDigits?.toIntOrNull() ?: 0
                    9999 - yearNum
                }
                CategoryGroup.OTHER -> 500
            }
        }
    }
    
    private var moviesPage = 1
    private var seriesPage = 1
    val deadMirrors = mutableSetOf<String>("listeamed.net", "ww1.listeamed.net", "listeamed")
    val hardDeadMirrors = mutableSetOf<String>("listeamed.net", "ww1.listeamed.net", "listeamed") // For gates and permanent bans
    val categoryPages = ConcurrentHashMap<String, Int>()
    val discoveredAltServers = ConcurrentHashMap<String, List<com.duta.movie.model.VideoServer>>()

    fun cleanDeadMirrors() {
        val predicate: (String) -> Boolean = { item ->
            val isFullUrl = item.contains("://") || item.contains("/")
            if (isFullUrl) {
                false
            } else {
                com.duta.movie.util.VideoExtractor.isWhitelistedHost(item)
            }
        }
        deadMirrors.removeAll(predicate)
        hardDeadMirrors.removeAll(predicate)
    }

    private var fetchJob: Job? = null
    private var loadMoreJob: Job? = null
    private var searchJob: Job? = null
    private var backgroundDetailsJob: Job? = null
    private var currentPage = 1
    private var rotationCount = 0
    private var isRotationLocked = false
    var activeVideoId: String? = null
        private set
    private val exhaustedServerUrls = mutableSetOf<String>() // Track servers where ALL mirrors are blacklisted
    private var consecutiveAllBlacklistedCount = 0 // Track consecutive ALL_BLACKLISTED results
    private var prefetchJob: Job? = null
    private var lastSubtitleSearchTitle: String? = null
    private var subSearchJob: Job? = null
    private var subResolveJob: Job? = null
    private var userExplicitlyDismissedSubtitles = false
    private var resolutionJob: Job? = null
    private var hasAttemptedAltHealing = false

    private val _isSearchingAlternatives = MutableStateFlow(false)
    val isSearchingAlternatives: StateFlow<Boolean> = _isSearchingAlternatives.asStateFlow()

    val enabledCategoryPaths: StateFlow<Set<String>> = preferenceManager.enabledCategoryPaths.stateIn(viewModelScope, SharingStarted.Eagerly, PreferenceManager.DEFAULT_ENABLED_CATEGORIES)
    val myList: StateFlow<Set<String>> = videoRepository.myList.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val defaultSubtitleLanguage: StateFlow<String> = videoRepository.defaultSubtitleLanguage.stateIn(viewModelScope, SharingStarted.Eagerly, "Indonesian")
    val isAutoSubtitleEnabled: StateFlow<Boolean> = videoRepository.isAutoSubtitleEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val isDebugModeEnabled: StateFlow<Boolean> = preferenceManager.isDebugModeEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val isMobileLandscapeEnabled: StateFlow<Boolean> = preferenceManager.isMobileLandscapeEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val activeBaseUrl: StateFlow<String> = preferenceManager.activeBaseUrl.onEach { url ->
        if (url.isNotEmpty()) VideoExtractor.setBaseUrl(url)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, VideoExtractor.getBaseUrl())
    val activePencuriBaseUrl: StateFlow<String> = preferenceManager.activePencuriBaseUrl.onEach { url ->
        if (url.isNotEmpty()) VideoExtractor.setPencuriBaseUrl(url)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, VideoExtractor.getPencuriBaseUrl())
    val forceWebViewHosts: StateFlow<Set<String>> = preferenceManager.forceWebViewHosts.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val uiSafeAreaPadding: StateFlow<Int> = preferenceManager.uiSafeAreaPadding.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val uiHeroHeightOffset: StateFlow<Int> = preferenceManager.uiHeroHeightOffset.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val uiScaleFactor: StateFlow<Float> = preferenceManager.uiScaleFactor.stateIn(viewModelScope, SharingStarted.Eagerly, 1.0f)
    val uiThumbnailScaleFactor: StateFlow<Float> = preferenceManager.uiThumbnailScaleFactor.stateIn(viewModelScope, SharingStarted.Eagerly, if (isTV()) 0.60f else 1.0f)

    val sourceWeights: StateFlow<Map<String, Int>> = preferenceManager.sourceWeights.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private fun sortAndNormalizeCategories(list: List<Map<String, String>>): List<Map<String, String>> {
        return list.map { 
            it.toMutableMap().apply { 
                val origPath = get("path") ?: ""
                var p = VideoExtractor.normalizePath(origPath)
                if (p.contains("country/viet-nam", ignoreCase = true) || p.contains("country/vietnam", ignoreCase = true)) {
                    p = "/country/vietnam/"
                    put("name", "Vietnam")
                }
                if (p.contains("country/malaysia", ignoreCase = true)) {
                    p = "/country/malaysia/"
                    put("name", "Malaysia")
                }
                if (p.contains("p-ramlee", ignoreCase = true) || p.contains("FilemP.ramlee", ignoreCase = true)) {
                    p = "/category/p-ramlee/"
                    put("name", "P.Ramlee")
                }
                if (p.matches(Regex("""^(.*/)?sci-fi/?$""", RegexOption.IGNORE_CASE)) || p.contains("science-fiction", ignoreCase = true)) {
                    p = "/genre/science-fiction/"
                    put("name", "Science Fiction")
                }
                put("path", p)
            } 
        }
        .filter { 
            val name = it["name"]?.trim() ?: ""
            val path = it["path"] ?: ""
            val lowName = name.lowercase()
            val lowPath = path.lowercase()
            
            !lowName.equals("country") &&
            !lowName.equals("country #") &&
            !lowName.contains("kelas bintang") &&
            !lowName.contains("semi barat") &&
            !lowName.contains("semi jav") &&
            !lowName.contains("semi indo") &&
            !lowName.contains("semi korea") &&
            !lowName.contains("vivamax") &&
            !lowName.contains("bokep") &&
            !lowName.contains("dunia21") &&
            !lowName.contains("idlix") &&
            !lowName.contains("rebahin") &&
            !lowName.contains("lk21") &&
            !lowName.contains("dm21") &&
            !lowName.contains("iklan") &&
            !lowName.contains("film lainnya") &&
            !lowName.contains("islamic republic of") &&
            !lowPath.startsWith("/network/") &&
            !lowPath.contains("kelas-bintang") &&
            !lowPath.contains("vivamax") &&
            !lowPath.contains("bokep") &&
            !lowPath.contains("dunia21") &&
            !lowPath.contains("idlix") &&
            !lowPath.contains("rebahin") &&
            !lowPath.contains("pasang-iklan") &&
            !lowPath.equals("/sci-fi/") &&
            !(lowName == "sci-fi" && lowPath.contains("sci-fi")) &&
            name.isNotEmpty() && path.isNotEmpty() && path != "#"
        }
        .distinctBy { it["path"] }
        .distinctBy { it["name"]?.trim()?.lowercase() }
        .sortedWith { a, b ->
            val pathA = a["path"] ?: ""
            val nameA = a["name"] ?: ""
            val pathB = b["path"] ?: ""
            val nameB = b["name"] ?: ""

            val groupA = getCategoryGroup(pathA, nameA)
            val groupB = getCategoryGroup(pathB, nameB)

            if (groupA.priority != groupB.priority) {
                groupA.priority.compareTo(groupB.priority)
            } else {
                val rankA = getIntraGroupRank(groupA, pathA, nameA)
                val rankB = getIntraGroupRank(groupB, pathB, nameB)
                if (rankA != rankB) {
                    rankA.compareTo(rankB)
                } else {
                    nameA.compareTo(nameB, ignoreCase = true)
                }
            }
        }
    }

    init {
        cleanDeadMirrors()
        com.duta.movie.util.SubtitleExtractor.init(context)
        val defaultCategories = listOf(
            mapOf("name" to "Newly Updated", "path" to "/"),
            mapOf("name" to "Movies", "path" to moviePath),
            mapOf("name" to "TV Series", "path" to seriesPath),
            mapOf("name" to "Top IMDb", "path" to "/top-imdb/"),
            mapOf("name" to "Trending", "path" to "/most-viewed/"),
            mapOf("name" to "Malay Subbed", "path" to "/genre/subbed/malay-subbed/"),
            mapOf("name" to "Malay Dubbed", "path" to "/genre/dubbed/malay/"),
            mapOf("name" to "Malaysia", "path" to "/country/malaysia/"),
            mapOf("name" to "P.Ramlee", "path" to "/category/p-ramlee/"),
            mapOf("name" to "Indonesia", "path" to "/country/indonesia/"),
            mapOf("name" to "Korea", "path" to "/country/korea/"),
            mapOf("name" to "Thailand", "path" to "/country/thailand/"),
            mapOf("name" to "Vietnam", "path" to "/country/vietnam/"),
            mapOf("name" to "Japan", "path" to "/country/japan/"),
            mapOf("name" to "China", "path" to "/country/china/"),
            mapOf("name" to "Action", "path" to "/genre/action/"),
            mapOf("name" to "Animation", "path" to "/genre/animation/"),
            mapOf("name" to "Comedy", "path" to "/genre/comedy/"),
            mapOf("name" to "Drama", "path" to "/genre/drama/"),
            mapOf("name" to "Horror", "path" to "/genre/horror/"),
            mapOf("name" to "Romance", "path" to "/genre/romance/"),
            mapOf("name" to "Science Fiction", "path" to "/genre/science-fiction/"),
            mapOf("name" to "Thriller", "path" to "/genre/thriller/")
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

        VideoExtractor.setOnPencuriDomainLearned { newUrl ->
            viewModelScope.launch {
                preferenceManager.setActivePencuriBaseUrl(newUrl)
                VideoExtractor.setPencuriBaseUrl(newUrl)
            }
        }
        
        videoRepository.isPlaybackActive = { _isPlayerActive.value }
        loadData()
        
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

        viewModelScope.launch {
            val directHostsToScrub = listOf(
                "morencius.com",
                "bestcdn.me",
                "faststream.org",
                "iplayerhls.com",
                "indostream.lol",
                "archive.org"
            )
            directHostsToScrub.forEach { host ->
                preferenceManager.removeFromForceWebViewHosts(host)
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            val currentBase = VideoExtractor.getBaseUrl()
            val lowBase = currentBase.lowercase()
            if (lowBase.contains("katherineschoolphone") || lowBase.contains("voe") || lowBase.contains("204.3.234.75") || !VideoExtractor.pingAndVerify(currentBase)) {
                Log.w("VideoViewModel", "Active base domain ($currentBase) is invalid or dead. Probing fresh domain...")
                val fresh = VideoExtractor.probeForNewDomain() ?: VideoExtractor.getPencuriBaseUrl()
                VideoExtractor.setBaseUrl(fresh)
                preferenceManager.setActiveBaseUrl(fresh)
                Log.i("VideoViewModel", "Active base domain restored to: $fresh")
            }
        }
    }

    val categories: StateFlow<List<Map<String, String>>> = combine(allCategories, enabledCategoryPaths) { all, enabled ->
        all.filter { VideoExtractor.normalizePath(it["path"] ?: "") in enabled }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categoryVideos: StateFlow<Map<String, List<Video>>> = combine(_categoryVideos, _latestMovies, _latestTVSeries, _metadataTrigger) { map, latestMovies, latestSeries, _ ->
        val fullMap = map.toMutableMap()
        if (latestMovies.isNotEmpty()) fullMap[moviePath] = latestMovies
        if (latestSeries.isNotEmpty()) fullMap[seriesPath] = latestSeries
        
        fullMap.mapValues { (_, list) -> list.map { applyMetadata(it) } }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(15000), emptyMap())

    val videos: StateFlow<List<Video>> = combine(
        listOf(
            _latestMovies,
            _resultVideos,
            _selectedCategory,
            _searchQuery,
            headlinerVideo,
            _metadataTrigger,
            _searchFilter,
            _searchSort,
            _searchResultsVideos
        )
    ) { args ->
        val browse = args[0] as List<Video>
        val categoryResults = args[1] as List<Video>
        val category = args[2] as String?
        val query = args[3] as String
        val headliner = args[4] as Video?
        val filter = args[6] as SearchFilter
        val sort = args[7] as SearchSort
        val searchResults = args[8] as List<Video>

        if (query.isNotBlank()) {
            if (searchResults.isEmpty()) return@combine emptyList<Video>()
            val processed = searchResults.map { applyMetadata(it) }
            val filtered = when (filter) {
                SearchFilter.ALL -> processed
                SearchFilter.MOVIES -> processed.filter { 
                    it.isSeries != true && !it.videoUrl.contains("/series/") && !it.videoUrl.contains("/tv/") && 
                    !it.title.contains("Episod", ignoreCase = true) && !it.title.contains("Episode", ignoreCase = true)
                }
                SearchFilter.SERIES -> processed.filter { 
                    it.isSeries == true || it.videoUrl.contains("/series/") || it.videoUrl.contains("/tv/") || 
                    it.title.contains("Episod", ignoreCase = true) || it.title.contains("Episode", ignoreCase = true) ||
                    it.title.contains("Season", ignoreCase = true)
                }
                SearchFilter.MALAY -> processed.filter { 
                    it.id.startsWith("kb_") || it.id.startsWith("pm_") || it.videoUrl.contains("pencurimovie") || 
                    it.videoUrl.contains("kepalabergetar") || it.id.startsWith("ia_pramlee") || 
                    it.title.contains("Episod", ignoreCase = true)
                }
                SearchFilter.PRAMLEE -> processed.filter { 
                    it.id.startsWith("ia_pramlee") || it.title.contains("P. Ramlee", ignoreCase = true) || 
                    it.title.contains("P.Ramlee", ignoreCase = true) || it.title.contains("Bujang Lapok", ignoreCase = true)
                }
            }
            when (sort) {
                SearchSort.RELEVANCE -> filtered
                SearchSort.NEWEST -> filtered.sortedByDescending { 
                    val yearMatch = Regex("""\b(19\d{2}|20\d{2})\b""").find(it.title)?.value?.toIntOrNull()
                    val dateYear = Regex("""\b(19\d{2}|20\d{2})\b""").find(it.date)?.value?.toIntOrNull()
                    yearMatch ?: dateYear ?: 0
                }
                SearchSort.RATING -> filtered.sortedByDescending {
                    val viewsCount = it.views.filter { ch -> ch.isDigit() }.toLongOrNull() ?: 0L
                    viewsCount
                }
            }
        } else {
            val activeList = if (category != null) categoryResults else browse
            if (activeList.isEmpty()) return@combine emptyList<Video>()
            val processedList = activeList.map { applyMetadata(it) }
            processedList.sortedWith(compareByDescending<Video> { it.id == headliner?.id }.thenByDescending { it.date }.thenByDescending { it.views })
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyWatchedVideos: StateFlow<List<Video>> = combine(videoRepository.recentlyWatchedVideos, _metadataTrigger) { list, _ ->
        list.map { applyMetadata(it) }
    }.flowOn(Dispatchers.Default).onEach { list -> if (list.isNotEmpty()) updateMetadataCache(list, triggerBackground = false) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(15000), emptyList())

    val myListVideos: StateFlow<List<Video>> = combine(videoRepository.favoriteVideos, _metadataTrigger) { list, _ ->
        list.map { applyMetadata(it) }
    }.flowOn(Dispatchers.Default).onEach { list -> if (list.isNotEmpty()) updateMetadataCache(list, triggerBackground = false) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(15000), emptyList())

    private fun addResolutionLog(msg: String) {
        _resolutionLog.update { (it + msg).takeLast(20) }
        Log.d("OwlEye", msg)
    }

    private fun clearResolutionLog() { _resolutionLog.value = emptyList() }

    fun toggleCategory(path: String) { viewModelScope.launch { preferenceManager.toggleCategoryPath(path) } }

    fun enableAllCategories() {
        val allPaths = _categories.value.mapNotNull { it["path"] }
        viewModelScope.launch { preferenceManager.enableAllCategories(allPaths) }
    }

    fun disableAllCategories() {
        viewModelScope.launch { preferenceManager.disableAllCategories() }
    }

    fun resetDefaultCategories() {
        viewModelScope.launch { preferenceManager.resetCategoriesToDefault() }
    }

    fun setSearchActive(active: Boolean) {
        _isSearchActive.value = active
        if (!active) {
            clearSearchQuery()
        }
    }

    fun selectCategory(category: String?) {
        if (category == null) {
            if (_selectedCategory.value != null) {
                _selectedCategory.value = null
                _resultVideos.value = emptyList()
            }
        } else {
            fetchVideos(category)
        }
    }

    fun onSearchQueryChange(query: String) {
        if (query.lowercase() == "debug1") {
            setDebugModeEnabled(true)
            _searchQuery.value = ""
            _searchResultsVideos.value = emptyList()
            _isSearching.value = false
            _lastCompletedSearchQuery.value = null
            return
        } else if (query.lowercase() == "debug0") {
            setDebugModeEnabled(false)
            _searchQuery.value = ""
            _searchResultsVideos.value = emptyList()
            _isSearching.value = false
            _lastCompletedSearchQuery.value = null
            return
        }

        _searchQuery.value = query
        searchDebounceJob?.cancel()

        if (query.isBlank()) {
            _searchResultsVideos.value = emptyList()
            _isSearching.value = false
            _lastCompletedSearchQuery.value = null
        }
    }

    fun getVideoDuration(id: String): Flow<Long> = videoRepository.getVideoDuration(id)

    fun setUiSafeAreaPadding(padding: Int) { viewModelScope.launch { preferenceManager.setUiSafeAreaPadding(padding) } }
    fun setUiHeroHeightOffset(offset: Int) { viewModelScope.launch { preferenceManager.setUiHeroHeightOffset(offset) } }
    fun setUiScaleFactor(factor: Float) { viewModelScope.launch { preferenceManager.setUiScaleFactor(factor) } }
    fun setUiThumbnailScaleFactor(factor: Float) { viewModelScope.launch { preferenceManager.setUiThumbnailScaleFactor(factor) } }
    fun resetDisplaySettings() { viewModelScope.launch { preferenceManager.resetDisplaySettings() } }


    private val adultKeywords = setOf("bokep", "av", "jav", "porn", "adult", "sensor", "uncensored", "18+", "semi", "bar-bar", "desah")

    fun setDefaultSubtitleLanguage(lang: String) { viewModelScope.launch { preferenceManager.setDefaultSubtitleLanguage(lang) } }
    fun setAutoSubtitleEnabled(enabled: Boolean) { viewModelScope.launch { preferenceManager.setAutoSubtitleEnabled(enabled) } }
    fun setDebugModeEnabled(enabled: Boolean) { viewModelScope.launch { preferenceManager.setDebugModeEnabled(enabled) } }
    fun setMobileLandscapeEnabled(enabled: Boolean) { viewModelScope.launch { preferenceManager.setMobileLandscapeEnabled(enabled) } }

    private val _totalInstallCount = MutableStateFlow<Int?>(null)
    val totalInstallCount: StateFlow<Int?> = _totalInstallCount.asStateFlow()

    private val _isFetchingInstalls = MutableStateFlow(false)
    val isFetchingInstalls: StateFlow<Boolean> = _isFetchingInstalls.asStateFlow()

    val isDeviceInstallRegistered: StateFlow<Boolean> = preferenceManager.isInstallRegistered
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val lastKnownInstallCount: StateFlow<Int> = preferenceManager.lastKnownInstallCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun fetchTotalInstalls() {
        viewModelScope.launch {
            _isFetchingInstalls.value = true
            val count = com.duta.movie.util.InstallTracker.fetchTotalInstalls(preferenceManager)
            if (count != null) {
                _totalInstallCount.value = count
            }
            _isFetchingInstalls.value = false
        }
    }


    fun toggleMyList(videoId: String) {
        viewModelScope.launch {
            val video = _videoMetadata.value?.takeIf { it.id == videoId } ?: getVideo(videoId) ?: return@launch
            videoRepository.updateVideoInDb(video.copy())
            videoRepository.toggleMyList(videoId)
        }
    }

    fun fetchHomeData(force: Boolean = false) {
        if (_isLoading.value && !force) return
        _isLoading.value = true; _isEndReached.value = false; currentPage = 1
        
        viewModelScope.launch {
            try {
                // Fetch categories from site first
                val fetchedCategories = withContext(Dispatchers.IO) { videoRepository.fetchCategories() }
                
                if (fetchedCategories.isNotEmpty()) {
                    val current = _categories.value
                    val merged = sortAndNormalizeCategories(current + fetchedCategories)
                    
                    if (merged.size != current.size) {
                        _categories.value = merged
                        preferenceManager.saveDiscoveredCategories(merged)
                    }
                }

                // ADAPTIVE DISCOVERY: Probe if the absolute basics are missing
                val checkMovies = try { videoRepository.fetchVideosBySection(moviePath, 1, 5) } catch(_: Exception) { emptyList<Video>() }
                if (checkMovies.isEmpty()) {
                    Log.w("VideoViewModel", "Movies empty. Attempting domain discovery...")
                    VideoExtractor.probeForNewDomain()?.let { newDomain ->
                         VideoExtractor.setBaseUrl(newDomain)
                         preferenceManager.setActiveBaseUrl(newDomain)
                         try {
                             val freshCats = withContext(Dispatchers.IO) { videoRepository.fetchCategories() }
                             if (freshCats.isNotEmpty()) {
                                 val current = _categories.value
                                 val merged = sortAndNormalizeCategories(current + freshCats)
                                 if (merged.size != current.size) {
                                     _categories.value = merged
                                     preferenceManager.saveDiscoveredCategories(merged)
                                 }
                             }
                         } catch (_: Exception) {}
                    }
                }

                // FETCH ALL ENABLED ROWS: TV mode requires rows to have data to be focusable/scrollable
                val enabled = enabledCategoryPaths.value
                val fetchQueue = enabled.toList().sortedBy { path -> 
                    val i = PRIORITY_PATHS.indexOf(path)
                    if (i == -1) 999 else i 
                }
                fetchQueue.forEach { path ->
                    fetchVideosForCategoryRow(path)
                }
            } catch (e: Exception) {
                Log.e("VideoViewModel", "Home Fetch Failed: ${e.message}")
                if (e !is CancellationException) _error.value = "Failed to refresh home: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refresh() {
        val query = _searchQuery.value.trim()
        if (_isSearchActive.value && query.length >= 2) {
            viewModelScope.launch { searchVideos(query) }
            return
        }
        val category = _selectedCategory.value
        if (category == null) {
            fetchHomeData(force = true)
            fetchPakcikRekomenVideos()
        } else {
            currentPage = 1
            _resultVideos.value = emptyList()
            _isEndReached.value = false
            viewModelScope.launch { fetchVideos(category) }
        }
    }
    fun loadActressProfile(actressPath: String) {
        viewModelScope.launch {
            _isActressProfileLoading.value = true; _selectedActressProfile.value = null
            try { val data = videoRepository.fetchActressProfile(actressPath); if (data.isNotEmpty()) { val name = data["name"] ?: ""; val bio = data["bio"] ?: ""; _selectedActressProfile.value = com.duta.movie.model.ActressProfile(name = name, image = data["image"] ?: "", bio = bio, metadata = data["metadata"]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()) } else { _error.value = "Actress profile not found." } } catch (e: Exception) { if (e !is CancellationException) _error.value = "Failed to load profile: ${e.message}" } finally { _isActressProfileLoading.value = false }
        }
    }

    fun updateMetadataCache(videos: List<Video>, triggerBackground: Boolean = false) {
        if (_isPlayerActive.value) return
        
        // Smart RAM Guard: Scaling cache limit based on device heap
        val memoryClass = com.duta.movie.util.VideoUtils.getMemoryClass(context)
        val maxItems = if (memoryClass <= 128) 300 else if (memoryClass <= 256) 600 else 1500
        
        if (metadataCache.size > maxItems) {
            Log.w("OwlEyeMemory", "Cache limit reached ($maxItems). Purging metadata cache to free RAM.")
            metadataCache.clear()
        }

        viewModelScope.launch(Dispatchers.Default) {
            var hasChanges = false
            videos.forEach { video ->
                val existing = metadataCache[video.id]
                val cleanedTitle = VideoExtractor.cleanTitle(video.title)
                if (existing == null) {
                    metadataCache[video.id] = video.copy(title = cleanedTitle)
                    hasChanges = true
                } else {
                    val updatedCleanedTitle = VideoExtractor.cleanTitle(
                        if (cleanedTitle.length > VideoExtractor.cleanTitle(existing.title).length) cleanedTitle else existing.title
                    )
                    val isPramlee = video.id.startsWith("ia_pramlee")
                    val updated = existing.copy(
                        title = updatedCleanedTitle,
                        thumbnailUrl = if (isPramlee && video.thumbnailUrl.isNotEmpty()) video.thumbnailUrl
                                       else if (video.thumbnailUrl.isNotEmpty()) video.thumbnailUrl 
                                       else existing.thumbnailUrl,
                        actresses = (video.actresses + existing.actresses).distinct().filter { it.isNotEmpty() },
                        actressPaths = (video.actressPaths + existing.actressPaths),
                        date = video.date.ifEmpty { existing.date },
                        quality = video.quality.ifEmpty { existing.quality },
                        description = if (isPramlee && video.description.isNotEmpty()) video.description
                                      else if (video.description.length > existing.description.length) video.description 
                                      else existing.description,
                        previewUrl = if (video.previewUrl.isNotEmpty()) video.previewUrl else existing.previewUrl,
                        backdropUrl = if (isPramlee && video.backdropUrl.isNotEmpty()) video.backdropUrl
                                      else if (video.backdropUrl.isNotEmpty()) video.backdropUrl 
                                      else existing.backdropUrl,
                        duration = if (video.duration != "??:??" && video.duration.isNotEmpty()) video.duration else existing.duration,
                        servers = if (video.servers.size >= existing.servers.size) video.servers else existing.servers,
                        season = if (video.season.isNotEmpty()) video.season else existing.season,
                        episodes = if (video.episodes.isNotEmpty()) video.episodes else existing.episodes,
                        isSeries = if (video.episodes.isNotEmpty() || existing.episodes.isNotEmpty()) true else (video.isSeries ?: existing.isSeries)
                    )
                    if (updated != existing) {
                        metadataCache[video.id] = updated
                        hasChanges = true
                    }
                }
            }
            if (hasChanges) {
                withContext(Dispatchers.Main) { _metadataTrigger.update { it + 1 } }
            }
            if (triggerBackground) loadBackgroundDetails(videos)
        }
    }

    suspend fun searchVideos(query: String, categoryPath: String? = null) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        searchDebounceJob?.cancel()
        addRecentSearch(trimmed)

        // Prevent redundant re-scraping if already showing results for the exact same query
        if (_lastCompletedSearchQuery.value.equals(trimmed, ignoreCase = true) && _searchResultsVideos.value.isNotEmpty() && !_isSearching.value) {
            return
        }

        _isSearching.value = true
        _isLoading.value = true
        _error.value = null

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            try {
                val results = videoRepository.searchVideos(trimmed, page = 1, count = 100, categoryPath = categoryPath)
                if (results.isNotEmpty()) {
                    // Do NOT trigger heavy background HTML prefetching for 100 search items
                    updateMetadataCache(results, triggerBackground = false)
                    _searchResultsVideos.value = results
                } else {
                    _searchResultsVideos.value = emptyList()
                    _error.value = "No results found for '$trimmed'"
                }
                _lastCompletedSearchQuery.value = trimmed
            } catch (e: Exception) {
                if (e !is CancellationException) _error.value = "Search failed: ${e.message}"
            } finally {
                _isSearching.value = false
                _isLoading.value = false
            }
        }
    }

    fun loadComments(videoId: String) {
        viewModelScope.launch {
            _isCommentsLoading.value = true
            val result = commentService.getComments(videoId)
            result.onSuccess {
                _comments.value = it
            }.onFailure {
                Log.e("VideoViewModel", "Failed to load comments", it)
            }
            _isCommentsLoading.value = false
        }
    }

    fun submitComment(videoId: String, userName: String, commentText: String, onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
        val trimmedComment = commentText.trim()
        val trimmedUser = userName.trim()
        if (trimmedComment.isEmpty() || trimmedUser.isEmpty()) {
            onComplete(false, "Name and comment cannot be empty")
            return
        }
        viewModelScope.launch {
            _isSubmittingComment.value = true
            preferenceManager.setUserNickname(trimmedUser)

            val result = commentService.postComment(videoId, trimmedUser, trimmedComment)
            result.onSuccess { newComment ->
                _comments.value = listOf(newComment) + _comments.value
                preferenceManager.addMyCommentId(newComment.id)
                _isSubmittingComment.value = false
                onComplete(true, null)
            }.onFailure { e ->
                _isSubmittingComment.value = false
                onComplete(false, e.message)
            }
        }
    }

    fun editComment(commentId: Long, newText: String, onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
        val trimmed = newText.trim()
        if (trimmed.isEmpty()) {
            onComplete(false, "Comment cannot be empty")
            return
        }
        viewModelScope.launch {
            val result = commentService.editComment(commentId, trimmed)
            result.onSuccess { updated ->
                _comments.value = _comments.value.map { if (it.id == commentId) updated else it }
                onComplete(true, null)
            }.onFailure { e ->
                onComplete(false, e.message)
            }
        }
    }

    fun deleteComment(commentId: Long, onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val result = commentService.deleteComment(commentId)
            result.onSuccess {
                _comments.value = _comments.value.filter { it.id != commentId }
                preferenceManager.removeMyCommentId(commentId)
                onComplete(true, null)
            }.onFailure { e ->
                onComplete(false, e.message)
            }
        }
    }

    fun loadRecommendation(videoId: String) {
        viewModelScope.launch {
            try {
                recommendationService.getRecommendationForVideo(videoId).onSuccess { rec ->
                    if (rec != null) {
                        _recommendationCounts.update { it + (videoId to rec.recommendCount) }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun loadFullDetails(videoId: String) { 
        if (_videoMetadata.value?.id != videoId) {
            _videoMetadata.value = getVideo(videoId) 
        }

        loadComments(videoId)
        loadRecommendation(videoId)

        viewModelScope.launch { 
            _isDetailLoading.value = true
            _isLoading.value = true
            try { 
                if (videoId.startsWith("yt_") || videoId.startsWith("bili_") || videoId.startsWith("dm_")) {
                    val extVideo = getVideo(videoId)
                    if (extVideo != null) {
                        _videoMetadata.value = applyMetadata(extVideo)
                        return@launch
                    }
                }

                if (videoId.startsWith("ia_pramlee")) {
                    val freshClassic = com.duta.movie.util.VideoExtractor.fetchArchivePramleeVideos().find { it.id == videoId }
                    if (freshClassic != null) {
                        _videoMetadata.value = applyMetadata(freshClassic)
                        metadataCache[videoId] = freshClassic
                        viewModelScope.launch { videoRepository.updateVideoInDb(freshClassic) }
                    }
                }

                // Instant cache/DB check: if memory or local DB already has servers/episodes, display them immediately
                val cached = metadataCache[videoId]
                if (cached != null && (cached.servers.isNotEmpty() || cached.episodes.isNotEmpty())) {
                    _videoMetadata.value = applyMetadata(cached)
                } else {
                    val dbVideo = videoRepository.getCachedOrDbVideo(videoId)
                    if (dbVideo != null && (dbVideo.servers.isNotEmpty() || dbVideo.episodes.isNotEmpty())) {
                        _videoMetadata.value = applyMetadata(dbVideo)
                        updateMetadataCache(listOf(dbVideo), triggerBackground = false)
                    }
                }

                val video = getVideo(videoId)
                val detailed = videoRepository.fetchVideoDetails(videoId)
                if (detailed != null) { 
                    _videoMetadata.value = applyMetadata(detailed)
                    updateMetadataCache(listOf(detailed), triggerBackground = false) 
                    
                    // Asynchronously discover alternative mirrors from partner source (e.g. DutaFilm <-> PencuriMovie)
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            val altServers = com.duta.movie.util.VideoExtractor.findAlternativeSources(detailed)
                            if (altServers.isNotEmpty()) {
                                val current = _videoMetadata.value
                                if (current != null && (current.id == detailed.id || current.title.equals(detailed.title, ignoreCase = true))) {
                                    val existingServerUrls = current.servers.map { it.url.trimEnd('/') }.toSet()
                                    val newUnique = altServers.filter { !existingServerUrls.contains(it.url.trimEnd('/')) }
                                    if (newUnique.isNotEmpty()) {
                                        val combined = current.servers + newUnique
                                        val updated = current.copy(servers = combined)
                                        withContext(Dispatchers.Main) {
                                            _videoMetadata.value = applyMetadata(updated)
                                        }
                                        videoRepository.updateVideoInDb(updated)
                                        updateMetadataCache(listOf(updated), triggerBackground = false)
                                        Log.i("VideoViewModel", "Appended ${newUnique.size} alternative mirror(s) to server list for '${current.title}'")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("VideoViewModel", "Background alternative source discovery error: ${e.message}")
                        }
                    }
                } else if (video != null) {
                    _videoMetadata.value = applyMetadata(video)
                }
            } catch (e: Exception) { 
                Log.e("VideoViewModel", "Failed to load details", e) 
            } finally { 
                _isDetailLoading.value = false
                _isLoading.value = false
            }
        }
    }

    fun fetchVideos(category: String?) {
        if (category == null) return
        _selectedCategory.value = category; fetchJob?.cancel(); fetchJob = viewModelScope.launch { _isLoading.value = true; try { val results = videoRepository.fetchVideosBySection(category, page = 1, count = 250); if (results.isNotEmpty()) { updateMetadataCache(results); _resultVideos.value = results; currentPage = 6 } else _error.value = "No videos found" } catch (e: Exception) { if (e !is CancellationException) _error.value = "Failed to load: ${e.message}" } finally { _isLoading.value = false } }
    }

    fun loadMoreVideos() {
        val category = _selectedCategory.value ?: return
        if (_isLoading.value) return
        loadMoreJob?.cancel(); loadMoreJob = viewModelScope.launch { 
            _isLoading.value = true
            try { 
                val nextPage = currentPage + 1
                val moreVideos = videoRepository.fetchVideosBySection(category, page = nextPage, count = 250)
                if (moreVideos.isEmpty()) {
                    _isEndReached.value = true
                } else { 
                    updateMetadataCache(moreVideos)
                    val currentIds = _resultVideos.value.asSequence().map { it.id }.toSet()
                    val combined = _resultVideos.value + moreVideos.filter { it.id !in currentIds }
                    val sorted = if (category.contains("country/malaysia", ignoreCase = true)) {
                        VideoExtractor.sortVideosByNewestRelease(combined)
                    } else combined
                    _resultVideos.value = sorted
                    currentPage = nextPage + 5
                    loadBackgroundDetails(moreVideos) 
                } 
            } catch (e: Exception) { 
                if (e !is CancellationException) Log.e("VideoViewModel", "Error loading more", e) 
            } finally { 
                _isLoading.value = false 
            } 
        }
    }

    private fun loadBackgroundDetails(newVideos: List<Video>) {
        if (_isPlayerActive.value) return
        
        backgroundDetailsJob?.cancel()
        backgroundDetailsJob = viewModelScope.launch {
            // Wait for UI to settle. TV needs longer delay to avoid stutter during initial animation.
            delay(if (isTV()) 5000 else 2500)
            
            // Limit background detail prefetching to first 6 items on TV and 8 on mobile to avoid GC thrashing
            val maxBackground = if (isTV()) 6 else 8
            val targetVideos = newVideos.take(maxBackground)
            
            videoRepository.loadBackgroundDetails(targetVideos) { updated ->
                updateMetadataCache(updated, triggerBackground = false)
                updateListsWithMetadata(updated) 
            }
        }
    }

    private fun isTV(): Boolean {
        return com.duta.movie.util.DeviceUtils.isTvDevice(context)
    }

    fun updateListsWithMetadata(updatedVideos: List<Video>) {
        viewModelScope.launch(Dispatchers.Default) {
            val updateMap = updatedVideos.associateBy { it.id }
            _latestMovies.update { current -> current.map { updateMap[it.id] ?: it } }
            _resultVideos.update { current -> current.map { updateMap[it.id] ?: it } }
            _searchResultsVideos.update { current -> current.map { updateMap[it.id] ?: it } }
        }
    }

    fun notifyPlaybackFailure(url: String) {
        if (url.isNotEmpty()) {
            deadMirrors.add(url)
            exhaustedServerUrls.add(url)
        }
        val host = try { android.net.Uri.parse(url).host?.lowercase() ?: java.net.URI(url).host?.lowercase() } catch(_: Throwable) { null }
        if (host == null || com.duta.movie.util.VideoExtractor.isWhitelistedHost(host) || com.duta.movie.util.VideoExtractor.isWhitelistedHost(url)) return
        
        _videoMetadata.value?.let { v ->
            if (v.title.contains("Agent Kim", ignoreCase = true) || v.title.contains("Manager Kim", ignoreCase = true)) {
                Log.e("OwlEyeMonitoring", "FAILURE DETECTED: Agent Kim playback failed on $host | URL: ${url.take(100)}")
            }
        }

        blacklistHost(host, hard = true)
    }

    private fun blacklistHost(host: String?, hard: Boolean = false) {
        if (host.isNullOrBlank() || com.duta.movie.util.VideoExtractor.isWhitelistedHost(host)) return
        deadMirrors.add(host)
        if (hard) hardDeadMirrors.add(host)
        
        // OWL'S EYE: Aliased Blacklisting
        // If we blacklist one domain in a cluster, blacklist them all
        val clusters = listOf(
            setOf("abyss.to", "abysscdn.com", "play.abyssplayer.com", "player.abyssplayer.com"),
            setOf("bond.to", "bondcdn.com", "play.bondplayer.com", "player.bondplayer.com"),
            setOf("veev.to", "veev.io")
        )
        clusters.find { it.contains(host) }?.forEach { 
            deadMirrors.add(it)
            if (hard) hardDeadMirrors.add(it)
        }

        viewModelScope.launch(Dispatchers.IO) { 
            preferenceManager.decrementSourceWeight(host)
            preferenceManager.recordHostPlayerFailure(host)
        }
    }

    fun notifyGateStuck(currentUrl: String, originalUrl: String? = null, targetVideoId: String? = null) {
        if (isPlaybackActive) {
            Log.w("VideoViewModel", "Ignored notifyGateStuck for $currentUrl because playback is currently active")
            return
        }
        isRotationLocked = false

        val effectiveVideoId = targetVideoId ?: activeVideoId ?: _videoMetadata.value?.id ?: ""

        // OWL'S EYE: Cloudflare bot-challenge pages (cdn-cgi/*) are NOT real gates.
        // They are analytics/RUM beacons or bot-check redirects. The player host itself
        // is not dead — a session reset will usually bypass them. Do NOT hard-blacklist.
        val isCloudflareCdnUrl = currentUrl.contains("/cdn-cgi/", ignoreCase = true)
        if (isCloudflareCdnUrl) {
            Log.w("VideoViewModel", "Gate Stuck on Cloudflare CDN URL (soft rotate only): $currentUrl")
            // Soft-blacklist only the embed host so it's skipped this session but recovers after reset
            val stuckHost = try { android.net.Uri.parse(currentUrl).host?.lowercase() } catch(_: Exception) { null }
            blacklistHost(stuckHost, hard = false)
            addResolutionLog("Cloudflare challenge detected on $currentUrl. Soft-rotating...")
            resolveNextServer(effectiveVideoId, currentUrl, force = true)
            return
        }

        val mirrorUrl = _currentServerUrl.value
        val resolvedUrl = _resolvedUrl.value
        
        val effectiveUrl = if (currentUrl.contains("://") && !currentUrl.startsWith("about:")) currentUrl
                           else originalUrl ?: mirrorUrl ?: resolvedUrl ?: currentUrl

        val stuckHost = try { android.net.Uri.parse(effectiveUrl).host?.lowercase() } catch(_: Exception) { null }
        val originalHost = try { originalUrl?.let { android.net.Uri.parse(it).host?.lowercase() } } catch(_: Exception) { null }
        val mirrorHost = try { mirrorUrl?.let { android.net.Uri.parse(it).host?.lowercase() } } catch(_: Exception) { null }

        blacklistHost(stuckHost, hard = true)
        blacklistHost(originalHost, hard = true)
        blacklistHost(mirrorHost, hard = true)
        
        val resolvedHost = try { resolvedUrl?.let { android.net.Uri.parse(it).host?.lowercase() } } catch(_: Exception) { null }
        blacklistHost(resolvedHost, hard = false)

        // Record exhausted URLs so they aren't retried this session
        if (!originalUrl.isNullOrEmpty()) {
            exhaustedServerUrls.add(originalUrl)
            deadMirrors.add(originalUrl)
        }
        if (effectiveUrl.isNotEmpty()) {
            exhaustedServerUrls.add(effectiveUrl)
            deadMirrors.add(effectiveUrl)
        }
        mirrorUrl?.let {
            exhaustedServerUrls.add(it)
            deadMirrors.add(it)
            Log.i("VideoViewModel", "notifyGateStuck: Marked parent mirror dead: $it")
        }

        Log.i("VideoViewModel", "notifyGateStuck: effectiveUrl=$effectiveUrl, videoId=$effectiveVideoId")
        addResolutionLog("Gate stuck on $effectiveUrl. Rotating mirrors...")
        resolveNextServer(effectiveVideoId, mirrorUrl ?: effectiveUrl, force = true)
    }

    fun notifyMirrorDead(url: String) {
        isPlaybackActive = false
        isRotationLocked = false
        val stuckHost = try { android.net.Uri.parse(url).host?.lowercase() ?: java.net.URI(url).host?.lowercase() } catch(_: Throwable) { null }
        blacklistHost(stuckHost, hard = false)
        exhaustedServerUrls.add(url)
        deadMirrors.add(url)
        com.duta.movie.util.VideoExtractor.markConfirmedDead(url)
        _currentServerUrl.value?.let { parentMirror ->
            val parentHost = try { android.net.Uri.parse(parentMirror).host?.lowercase() ?: java.net.URI(parentMirror).host?.lowercase() } catch(_: Throwable) { null }
            blacklistHost(parentHost, hard = false)
            exhaustedServerUrls.add(parentMirror)
            deadMirrors.add(parentMirror)
            com.duta.movie.util.VideoExtractor.markConfirmedDead(parentMirror)
            Log.i("VideoViewModel", "notifyMirrorDead: Marked parent mirror dead: $parentMirror (child was $url)")
        }
        
        val mirrorUrl = _currentServerUrl.value
        val resolvedUrl = _resolvedUrl.value
        val resolvedHost = try { resolvedUrl?.let { android.net.Uri.parse(it).host?.lowercase() ?: java.net.URI(it).host?.lowercase() } } catch(_: Throwable) { null }
        if (resolvedHost != null) {
            blacklistHost(resolvedHost, hard = false)
        } else {
            val mirrorHost = try { mirrorUrl?.let { android.net.Uri.parse(it).host?.lowercase() ?: java.net.URI(it).host?.lowercase() } } catch(_: Throwable) { null }
            blacklistHost(mirrorHost, hard = false)
        }
    }

    fun purgeServerFromVideo(videoId: String, serverUrl: String) {
        if (videoId.isEmpty() || serverUrl.isEmpty()) return
        deadMirrors.add(serverUrl)
        hardDeadMirrors.add(serverUrl)
        val host = try { android.net.Uri.parse(serverUrl).host?.lowercase() ?: java.net.URI(serverUrl).host?.lowercase() } catch(_: Throwable) { null }
        if (host != null && !com.duta.movie.util.VideoExtractor.isWhitelistedHost(host) && com.duta.movie.util.VideoExtractor.isEphemeralOrExpiredStream(serverUrl)) {
            deadMirrors.add(host)
            hardDeadMirrors.add(host)
        }
        val video = _videoMetadata.value?.takeIf { it.id == videoId } ?: getVideo(videoId)
        if (video != null) {
            val filtered = video.servers.filter { 
                it.url != serverUrl && !com.duta.movie.util.VideoExtractor.isEphemeralOrExpiredStream(it.url) 
            }
            if (filtered.size != video.servers.size) {
                val updated = video.copy(servers = filtered)
                metadataCache[videoId] = updated
                _videoMetadata.value = updated
                viewModelScope.launch(Dispatchers.IO) {
                    videoRepository.updateVideoInDb(updated)
                }
            }
        }
    }

    fun checkConnection() {
        val current = VideoExtractor.getBaseUrl()
        viewModelScope.launch(Dispatchers.IO) {
            val discovered = com.duta.movie.util.VideoExtractor.probeForNewDomain()
            withContext(Dispatchers.Main) {
                if (discovered != null && discovered != current) {
                    android.widget.Toast.makeText(context, "New domain found: $discovered", android.widget.Toast.LENGTH_SHORT).show()
                    fetchHomeData(force = true)
                } else if (discovered != null) {
                    android.widget.Toast.makeText(context, context.getString(R.string.current_domain_up_to_date), android.widget.Toast.LENGTH_SHORT).show()
                    fetchHomeData(force = true)
                } else {
                    android.widget.Toast.makeText(context, context.getString(R.string.failed_to_discover_domain), android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun updateBaseUrlManually(url: String) {
        val cleanUrl = url.trim().trimEnd('/')
        if (cleanUrl.isNotEmpty() && cleanUrl.startsWith("http")) {
            viewModelScope.launch {
                VideoExtractor.setBaseUrl(cleanUrl)
                preferenceManager.setActiveBaseUrl(cleanUrl)
                android.widget.Toast.makeText(context, context.getString(R.string.base_url_updated), android.widget.Toast.LENGTH_SHORT).show()
                fetchHomeData(force = true)
            }
        } else {
            android.widget.Toast.makeText(context, context.getString(R.string.invalid_url_format), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun prefetchVideoDetails(video: Video) {
        if ((video.description.isNotEmpty() && video.previewUrl.isNotEmpty()) || _isPlayerActive.value) return
        prefetchJob?.cancel(); prefetchJob = viewModelScope.launch(Dispatchers.IO) { try { val cached = metadataCache[video.id]; if (cached != null && cached.description.isNotEmpty() && cached.previewUrl.isNotEmpty()) { withContext(Dispatchers.Main) { _metadataTrigger.update { it + 1 } }; return@launch }; delay(400); if (!isActive || _isPlayerActive.value) return@launch; val detailed = videoRepository.fetchVideoDetails(video.id); if (detailed != null) { updateMetadataCache(listOf(detailed), triggerBackground = false); updateListsWithMetadata(listOf(detailed)) } } catch (e: Exception) { if (e !is CancellationException) Log.e("VideoViewModel", "Prefetch failed for ${video.title}", e) } }
    }

    fun findCastFriendlyServer(videoId: String): String? {
        val video = getVideo(videoId) ?: return null
        return video.servers.firstOrNull { 
            val lowName = it.name.lowercase()
            (lowName.contains("direct") || lowName.contains("mp4") || lowName.contains("vip")) && 
            VideoExtractor.isCastFriendlyUrl(it.url)
        }?.url
    }

    fun playMovie(videoId: String, serverUrl: String? = null, forceReset: Boolean = false, isRotation: Boolean = false, clearBlacklist: Boolean = forceReset) {
        Log.i("VideoViewModel", "playMovie called for $videoId | force: $forceReset | rotation: $isRotation")
        isPlaybackActive = false
        activeVideoId = videoId
        cleanDeadMirrors()
        if (forceReset || isRotation) {
            if (isRotation) {
                _shouldSuppressResume.value = true
            }
            if (forceReset) { 
                rotationCount = 0
                exhaustedServerUrls.clear()
                consecutiveAllBlacklistedCount = 0
                _currentServerUrl.value = null
                _currentEpisode.value = null
                _subtitleCues.value = emptyList()
                subResolveJob?.cancel()
                subResolveJob = null
                userExplicitlyDismissedSubtitles = false
            }
            _resolvedUrl.value = null
        }
        backgroundDetailsJob?.cancel()
        prefetchJob?.cancel()
        
        resolutionJob?.cancel()
        resolutionJob = viewModelScope.launch {
            startMoviePlaybackResolution(videoId, serverUrl, forceReset = forceReset, isRotation = isRotation, clearBlacklist = clearBlacklist)
        }
    }

    fun playTVSeries(videoId: String, episodeUrl: String? = null, forceReset: Boolean = false, targetEpisode: Episode? = null, isRotation: Boolean = false, clearBlacklist: Boolean = forceReset) {
        Log.i("VideoViewModel", "playTVSeries called for $videoId | force: $forceReset | ep: ${targetEpisode?.name} | rotation: $isRotation")
        isPlaybackActive = false
        activeVideoId = videoId
        cleanDeadMirrors()
        if (forceReset || isRotation) {
            if (isRotation) {
                _shouldSuppressResume.value = true
            }
            if (forceReset) { 
                rotationCount = 0
                exhaustedServerUrls.clear()
                consecutiveAllBlacklistedCount = 0
                _currentServerUrl.value = null
                _currentEpisode.value = targetEpisode
                _subtitleCues.value = emptyList()
                subResolveJob?.cancel()
                subResolveJob = null
                userExplicitlyDismissedSubtitles = false
            }
            _resolvedUrl.value = null // Immediate clear for TV UI to show loading
        }
        backgroundDetailsJob?.cancel()
        prefetchJob?.cancel()
        
        resolutionJob?.cancel()
        resolutionJob = viewModelScope.launch {
            _isResolving.value = true
            _error.value = null
            // Only clear log on fresh start or force reset, not on internal rotation
            if (forceReset || !isRotation) clearResolutionLog()
            
            if (targetEpisode != null) {
                _currentEpisode.value = targetEpisode
            } else if (forceReset && !isRotation) {
                _currentEpisode.value = null
                _currentServerUrl.value = null
            }
            
            try {
                var video = getVideo(videoId)
                
                val targetSeasonNum = Regex("""(?i)\b(?:season|s)[-_ ]?(\d+)\b""").find(video?.videoUrl ?: "")?.groupValues?.get(1)?.toIntOrNull()
                    ?: Regex("""(?i)\b(?:season|s)\s*(\d+)\b""").find(video?.title ?: "")?.groupValues?.get(1)?.toIntOrNull()
                val hasMismatchedSeason = targetSeasonNum != null && video?.episodes?.any { ep ->
                    val epSeasonNum = Regex("""(?i)\b(?:season|s)[-_ ]?(\d+)\b""").find(ep.season.ifEmpty { ep.url })?.groupValues?.get(1)?.toIntOrNull()
                    epSeasonNum != null && epSeasonNum != targetSeasonNum
                } == true

                // Fetch basic metadata if missing episodes or cached episodes belong to wrong season
                if (video == null || (video!!.isSeries == true && (video!!.episodes.isEmpty() || hasMismatchedSeason))) {
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
                
                val isEpisodeUrl = episodeUrl?.let { (it.contains("/eps/") || it.contains("/episode/") || it.contains("-episode-") || it.contains("/episod/") || it.contains("-episod-") || it.contains("-epi-") || it.contains("/ep-")) && !it.contains("player=") && !it.contains("mirror=") } ?: false
                
                // Re-check for matching episode after fetch
                if (video?.isSeries == true) {
                    val curEpSeason = Regex("""(?i)\b(?:season|s)[-_ ]?(\d+)\b""").find(_currentEpisode.value?.season?.ifEmpty { _currentEpisode.value?.url ?: "" } ?: "")?.groupValues?.get(1)?.toIntOrNull()
                    if (targetSeasonNum != null && curEpSeason != null && curEpSeason != targetSeasonNum) {
                        _currentEpisode.value = null
                    }
                    val filteredEps = video!!.episodes.filter { ep ->
                        val low = ep.name.lowercase()
                        val notMeta = !low.contains("lihat semua") && !low.contains("see all") && 
                                      !low.contains("episode list") && !low.contains("daftar episode")
                        val epSeasonNum = Regex("""(?i)\b(?:season|s)[-_ ]?(\d+)\b""").find(ep.season.ifEmpty { ep.url })?.groupValues?.get(1)?.toIntOrNull()
                        val matchesSeason = targetSeasonNum == null || epSeasonNum == null || epSeasonNum == targetSeasonNum
                        notMeta && matchesSeason
                    }
                    if (isEpisodeUrl || targetEpisode != null) {
                        val primaryTargetUrl = if (isEpisodeUrl) episodeUrl!! else targetEpisode!!.url
                        val primarySlug = VideoExtractor.extractStableId(primaryTargetUrl)
                        val matchingEp = filteredEps.find { 
                            val epSlug = VideoExtractor.extractStableId(it.url)
                            epSlug == primarySlug || (primaryTargetUrl.contains(epSlug) && epSlug.length > 5) || (it.url.contains(primarySlug) && primarySlug.length > 5)
                        }
                        if (matchingEp != null) _currentEpisode.value = matchingEp
                    } else if (_currentEpisode.value == null && filteredEps.isNotEmpty()) {
                        val videoSlug = VideoExtractor.extractStableId(video!!.videoUrl).removePrefix("kb_").removePrefix("pm_")
                        val idSlug = videoId.removePrefix("kb_").removePrefix("pm_")
                        val matchingEp = filteredEps.find { ep ->
                            val epSlug = VideoExtractor.extractStableId(ep.url).removePrefix("kb_").removePrefix("pm_")
                            epSlug == videoSlug || epSlug == idSlug ||
                            (videoSlug.length > 5 && (epSlug.contains(videoSlug) || videoSlug.contains(epSlug))) ||
                            (idSlug.length > 5 && (epSlug.contains(idSlug) || idSlug.contains(epSlug)))
                        }
                        if (matchingEp != null) _currentEpisode.value = matchingEp
                    }
                }

                val episodePageUrl = if (isEpisodeUrl) episodeUrl 
                                     else if (targetEpisode != null) targetEpisode.url
                                     else if (video?.isSeries == true) _currentEpisode.value?.url 
                                     else null
                
                // OWL'S EYE: State Engine decoupled from TV Show metadata
                if (video != null && video!!.isSeries == true && episodePageUrl != null) {
                    val epSlug = VideoExtractor.extractStableId(episodePageUrl)
                    val cachedServers = episodeServersCache[epSlug]
                    
                    if (cachedServers.isNullOrEmpty() || forceReset) {
                        addResolutionLog("Scoping servers for episode...")
                        val fetched = VideoExtractor.fetchVideoDetails(episodePageUrl)
                        
                        var finalServers = fetched?.servers ?: emptyList()
                        
                        // Parent fallback if episode page fails
                        if (finalServers.isEmpty()) {
                            val parentPath = episodePageUrl.substringBefore("/eps/").substringBefore("/episode/").substringBefore("/episod/").substringBefore("-episod-").substringBefore("-episode-")
                            val parentUrl = if (parentPath.contains("/tv/") || parentPath.endsWith("/")) parentPath else "$parentPath/tv/$videoId/"
                            addResolutionLog("Episode servers missing. Attempting parent recovery: $parentUrl")
                            val parentFetched = VideoExtractor.fetchVideoDetails(parentUrl)
                            finalServers = parentFetched?.servers ?: emptyList()
                        }
                        
                        if (finalServers.isNotEmpty()) {
                            episodeServersCache[epSlug] = finalServers
                        } else {
                            addResolutionLog("Warning: No servers found for this episode.")
                        }
                    }
                }

                // Now call core resolution
                startTVSeriesPlaybackResolution(videoId, episodeUrl, forceReset = forceReset, targetEpisode = _currentEpisode.value, isRotation = isRotation, clearBlacklist = clearBlacklist)
                
            } catch (e: Exception) {
                Log.e("VideoViewModel", "TV Series Resolution Failed", e)
                _error.value = "TV Series Resolution Failed: ${e.message}"
                _isResolving.value = false
            }
        }
    }

    private suspend fun startMoviePlaybackResolution(videoId: String, serverUrl: String?, forceReset: Boolean, isRotation: Boolean = false, clearBlacklist: Boolean = forceReset) {
        Log.i("VideoViewModel", "startMoviePlaybackResolution started for $videoId | serverUrl: ${serverUrl?.take(40)} | rotation: $isRotation")
        cleanDeadMirrors()
        if (forceReset) {
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            android.webkit.CookieManager.getInstance().flush()
            if (clearBlacklist) {
                deadMirrors.clear()
                cleanDeadMirrors()
                deadMirrors.addAll(hardDeadMirrors)
            }
            rotationCount = 0
            hasAttemptedAltHealing = false
            com.duta.movie.util.NetworkConfig.clearSessionData()
            withContext(Dispatchers.Main) { _resolvedUrl.value = null }
        }
        _isResolving.value = true; _error.value = null
        clearResolutionLog()
        
        if (forceReset) {
            _currentEpisode.value = null
            _currentServerUrl.value = null
        }
        
        try {
                var video = _videoMetadata.value?.takeIf { it.id == videoId } ?: getVideo(videoId) ?: run {
                    videoRepository.fetchVideoDetails(videoId)?.let { detailed -> 
                        metadataCache[videoId] = detailed
                    }
                    getVideo(videoId)
                }

                if (videoId.startsWith("ia_pramlee")) {
                    val freshClassic = com.duta.movie.util.VideoExtractor.fetchArchivePramleeVideos().find { it.id == videoId }
                    if (freshClassic != null) {
                        video = freshClassic
                        metadataCache[videoId] = freshClassic
                    }
                }
                
                if (video == null) {
                    _error.value = "Failed to load video metadata."; _isResolving.value = false; return
                }

                // ARCHIVE.ORG FAST DIRECT: Transform any legacy/cached archive.org links to high-speed direct US nodes
                if (video.videoUrl.contains("archive.org") || video.servers.any { it.url.contains("archive.org") }) {
                    val optVideoUrl = com.duta.movie.util.VideoExtractor.optimizeArchiveUrl(video.videoUrl)
                    val optServers = video.servers.map { s ->
                        val optUrl = com.duta.movie.util.VideoExtractor.optimizeArchiveUrl(s.url)
                        if (optUrl != s.url) {
                            s.copy(url = optUrl, name = if (!s.name.contains("Fast Direct")) "${s.name} (Fast Direct)" else s.name)
                        } else s
                    }
                    if (optVideoUrl != video.videoUrl || optServers != video.servers) {
                        video = video.copy(videoUrl = optVideoUrl, servers = optServers)
                        metadataCache[videoId] = video
                    }
                }

                // SANITIZE ALTERNATIVE MIRRORS: Purge any stale/mismatched YouTube, Bilibili, or Dailymotion servers from cache/DB
                val targetMeta = com.duta.movie.util.VideoExtractor.parseMovieTitleMeta(video.title, video.date)
                val validAltServers = video.servers.filter { s ->
                    val lowUrl = s.url.lowercase()
                    val isAlt = lowUrl.contains("youtube") || lowUrl.contains("youtu.be") ||
                                lowUrl.contains("dailymotion") || lowUrl.contains("dai.ly") ||
                                lowUrl.contains("bilibili")
                    if (isAlt) {
                        val isPreVerified = discoveredAltServers[videoId]?.any { it.url == s.url } == true
                        if (isPreVerified) true else {
                            val cleanName = com.duta.movie.util.VideoExtractor.sanitizeServerNameToTitle(s.name)
                            com.duta.movie.util.VideoExtractor.isYouTubeMovieMatch(targetMeta, cleanName)
                        }
                    } else true
                }
                if (validAltServers.size != video.servers.size) {
                    video = video.copy(servers = validAltServers)
                    metadataCache[videoId] = video
                    viewModelScope.launch { videoRepository.updateVideoInDb(video) }
                }

                discoveredAltServers[videoId]?.let { alts ->
                    val validAlts = alts.filter { s ->
                        val lowUrl = s.url.lowercase()
                        val isAlt = lowUrl.contains("youtube") || lowUrl.contains("youtu.be") ||
                                    lowUrl.contains("dailymotion") || lowUrl.contains("dai.ly") ||
                                    lowUrl.contains("bilibili")
                        if (isAlt) {
                            val cleanName = com.duta.movie.util.VideoExtractor.sanitizeServerNameToTitle(s.name)
                            com.duta.movie.util.VideoExtractor.isYouTubeMovieMatch(targetMeta, cleanName)
                        } else true
                    }
                    if (validAlts.size != alts.size) {
                        if (validAlts.isEmpty()) discoveredAltServers.remove(videoId)
                        else discoveredAltServers[videoId] = validAlts
                    }
                }

                withContext(Dispatchers.Main) { 
                    if (_videoMetadata.value?.servers.isNullOrEmpty() || video!!.servers.isNotEmpty()) {
                        _videoMetadata.value = video 
                    }
                }

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
                    kotlinx.coroutines.delay(500)
                }

                val isPencuri = com.duta.movie.util.VideoExtractor.isPencuriMovie(videoId = videoId, videoUrl = video!!.videoUrl, streamUrl = serverUrl)
                val extraAlt = discoveredAltServers[videoId] ?: emptyList()
                val baseVideoServers = if (extraAlt.isNotEmpty()) {
                    val existingUrls = video!!.servers.map { it.url }.toSet()
                    video!!.servers + extraAlt.filter { it.url !in existingUrls }
                } else {
                    video!!.servers
                }

                // Cleanse expired/ephemeral direct streams from persistent video servers list
                val sanitizedServers = baseVideoServers.filter { 
                    !com.duta.movie.util.VideoExtractor.isEphemeralOrExpiredStream(it.url) &&
                    !hardDeadMirrors.contains(it.url) &&
                    !com.duta.movie.util.VideoExtractor.isConfirmedDead(it.url)
                }
                if (sanitizedServers.size != baseVideoServers.size && video != null) {
                    val cleanedVideo = video!!.copy(servers = sanitizedServers)
                    metadataCache[videoId] = cleanedVideo
                    _videoMetadata.value = cleanedVideo
                    viewModelScope.launch(Dispatchers.IO) { videoRepository.updateVideoInDb(cleanedVideo) }
                }

                val sortedServers = sanitizedServers
                    .filter {
                        val lowUrl = it.url.lowercase()
                        val isYt = lowUrl.contains("youtube") || lowUrl.contains("youtu.be")
                        val ytMatches = if (isYt && video != null) {
                            val isPreVerified = discoveredAltServers[videoId]?.any { alt -> alt.url == it.url } == true
                            if (isPreVerified) true else {
                                val targetMeta = com.duta.movie.util.VideoExtractor.parseMovieTitleMeta(video!!.title, video!!.date)
                                val cleanName = com.duta.movie.util.VideoExtractor.sanitizeServerNameToTitle(it.name)
                                com.duta.movie.util.VideoExtractor.isYouTubeMovieMatch(targetMeta, cleanName)
                            }
                        } else true
                        !lowUrl.contains("imasdk") && !lowUrl.contains("googleapis.com") && (!isYt || (lowUrl.contains("/embed/") && ytMatches) || (it.name.contains("YouTube", ignoreCase = true) && ytMatches)) && !lowUrl.contains("listeamed")
                    }
                    .sortedByDescending { s -> 
                        val host = try { android.net.Uri.parse(s.url).host?.lowercase() ?: "" } catch(_: Exception) { "" }
                        val weight = sourceWeights.value[host] ?: 0
                        val lowUrl = s.url.lowercase()
                        val lowName = s.name.lowercase()
                        val voeBonus = if (lowUrl.contains("voe") || lowUrl.contains("johnfullwonder") || lowName.contains("voe")) 100 else 0
                        val trapPenalty = if (lowUrl.contains("abyss") || lowUrl.contains("bond") || lowName.contains("abyss") || lowName.contains("bond")) -50 else 0
                        com.duta.movie.util.VideoExtractor.getProviderPriority(s.name, s.url) + weight + voeBonus + trapPenalty
                    }

                val rawMirror = serverUrl ?: sortedServers.firstOrNull()?.url ?: video!!.videoUrl
                val mirrorToResolve = com.duta.movie.util.VideoExtractor.optimizeArchiveUrl(rawMirror)
                val primaryUrl = mirrorToResolve
                
                if (trendingContent) {
                    Log.i("OwlEyeMonitoring", "TRACKING TRENDING MOVIE: ${video!!.title} | Mirror: $mirrorToResolve")
                    addResolutionLog("Enforcing High-Demand resolution for Agent Kim...")
                }

                _currentServerUrl.value = mirrorToResolve

                if (com.duta.movie.util.VideoExtractor.isDirectVideoUrl(mirrorToResolve) && 
                    !com.duta.movie.util.VideoExtractor.isEphemeralOrExpiredStream(mirrorToResolve)) {
                    addResolutionLog("Direct Stream Identified. Instant Handshake engaged.")
                    withContext(Dispatchers.Main) { 
                        _currentServerUrl.value = mirrorToResolve
                        _resolvedUrl.value = mirrorToResolve
                        _resolutionProgress.value = null
                        _isResolving.value = false
                    }; return
                }

                val lowMirrorFast = mirrorToResolve.lowercase()
                val isWebEmbedFast = lowMirrorFast.contains("dailymotion.com") || lowMirrorFast.contains("dai.ly") ||
                                     lowMirrorFast.contains("youtube.com") || lowMirrorFast.contains("youtu.be") ||
                                     lowMirrorFast.contains("bilibili.com") || lowMirrorFast.contains("bilibili.tv")
                if (isWebEmbedFast) {
                    addResolutionLog("Web Embed Identified ($mirrorToResolve). Instant Handshake engaged.")
                    withContext(Dispatchers.Main) {
                        _currentServerUrl.value = mirrorToResolve
                        _resolvedUrl.value = mirrorToResolve
                        _resolutionProgress.value = null
                        _isResolving.value = false
                    }
                    return
                }

                val directCandidates = (listOfNotNull(mirrorToResolve) + sortedServers.map { it.url })
                    .distinct()
                    .filter { url ->
                        val low = url.lowercase()
                        (low.contains("voe") || low.contains("johnfullwonder") || low.contains("cloudwindow") || low.contains("morencius") || low.contains("vidhide")) &&
                        !deadMirrors.contains(url)
                    }

                if (directCandidates.isNotEmpty()) {
                    for (candidate in directCandidates) {
                        addResolutionLog("Owl's Eye: Dedicated Direct Stream Extractor engaged on ${candidate.take(35)}...")
                        val voeExtract = com.duta.movie.util.VideoExtractor.extractVideoUrl(pageUrl = candidate, referer = primaryUrl)
                        if (voeExtract != null && com.duta.movie.util.VideoExtractor.isDirectVideoUrl(voeExtract.videoUrl)) {
                            addResolutionLog("Owl's Eye: Direct Stream Extracted successfully.")
                            withContext(Dispatchers.Main) {
                                _lastReferer.value = voeExtract.referer ?: "https://johnfullwonder.com/"
                                _lastCookies.value = voeExtract.cookies
                                _currentServerUrl.value = candidate
                                _resolvedUrl.value = voeExtract.videoUrl
                                _resolutionProgress.value = null
                                _isResolving.value = false
                            }
                            return
                        }
                    }
                }

                val mirrorsFromMetadata = sortedServers.map { it.url }.toSet()
                
                val baseList = if (mirrorsFromMetadata.isEmpty()) listOf(mirrorToResolve) else listOf(mirrorToResolve)
                
                val concurrencyLimit = if (trendingContent) 12 else 6 // Turbo

                var topMirrors = (baseList + mirrorsFromMetadata)
                    .distinct()
                    .filter { url -> 
                        val low = url.lowercase()
                        val host = try { android.net.Uri.parse(url).host?.lowercase() ?: java.net.URI(url).host?.lowercase() } catch(_: Throwable) { null }
                        val isHostWhitelisted = com.duta.movie.util.VideoExtractor.isWhitelistedHost(host)
                        !low.contains("listeamed") && (host == null || !deadMirrors.contains(host) || isHostWhitelisted) && !deadMirrors.contains(url) && !hardDeadMirrors.contains(url) && !exhaustedServerUrls.contains(url) && !com.duta.movie.util.VideoExtractor.isConfirmedDead(url)
                    }
                    .take(concurrencyLimit)
                
                val isExplicitServer = serverUrl != null && !forceReset && !isRotation
                val skipDirectRace = trendingContent && mirrorsFromMetadata.isEmpty() && !com.duta.movie.util.VideoExtractor.isDirectVideoUrl(mirrorToResolve)

                if (isExplicitServer || isRotation) {
                    // For an explicit server selection or rotation, target that specific server mirror only
                    if (mirrorToResolve != null && (deadMirrors.contains(mirrorToResolve) || hardDeadMirrors.contains(mirrorToResolve) || exhaustedServerUrls.contains(mirrorToResolve) || com.duta.movie.util.VideoExtractor.isConfirmedDead(mirrorToResolve))) {
                        topMirrors = topMirrors.filter { it != mirrorToResolve }
                    } else {
                        topMirrors = listOf(mirrorToResolve)
                    }
                } else if (topMirrors.isEmpty() || skipDirectRace) {
                    if (skipDirectRace) addResolutionLog("No mirrors found in metadata. Fast-tracking to WebView Shield...")
                    topMirrors = listOf(mirrorToResolve)
                }

                val winner = if (skipDirectRace) null else executeGodModeRace(topMirrors, primaryUrl, trendingContent)

                if (winner != null) {
                    if (winner.videoUrl == "ALL_BLACKLISTED_FAST_SKIP") {
                        addResolutionLog("All God Mode mirrors are blacklisted. Server is dead. Fast-rotating...")
                        if (mirrorToResolve != null) exhaustedServerUrls.add(mirrorToResolve)
                        consecutiveAllBlacklistedCount++
                        if (consecutiveAllBlacklistedCount >= 4) {
                            // All servers produce the same blacklisted hosts - stop the loop
                            addResolutionLog("STOP: $consecutiveAllBlacklistedCount consecutive servers all resolve to blacklisted hosts. Halting rotation.")
                            withContext(Dispatchers.Main) {
                                _error.value = "All available servers are currently down. Please try again later or choose a different title."
                                _isResolving.value = false
                            }
                            return
                        }
                        if (rotationCount >= 4) {
                            addResolutionLog("High failure rate. Clearing soft blacklist for recovery.")
                            deadMirrors.clear()
                            cleanDeadMirrors()
                            deadMirrors.addAll(hardDeadMirrors)
                        }
                        withContext(Dispatchers.Main) {
                            _error.value = "Server is dead. Fast rotating..."
                            isRotationLocked = false
                            resolveNextServer(videoId, mirrorToResolve, force = true)
                        }
                        return
                    }
                    withContext(Dispatchers.Main) {
                        consecutiveAllBlacklistedCount = 0 // Reset on success
                        _currentServerUrl.value = winner.sourceMirrorUrl ?: winner.referer ?: winner.videoUrl
                        _resolvedUrl.value = winner.videoUrl
                        _lastReferer.value = winner.referer; _lastCookies.value = winner.cookies
                        _resolutionProgress.value = null
                        _isResolving.value = false 
                    }
                } else {
                    val targetFallback = mirrorToResolve ?: topMirrors.firstOrNull()
                    val isConfirmedDead = targetFallback != null && (
                        deadMirrors.contains(targetFallback) ||
                        hardDeadMirrors.contains(targetFallback) ||
                        exhaustedServerUrls.contains(targetFallback) ||
                        com.duta.movie.util.VideoExtractor.isConfirmedDead(targetFallback)
                    )
                    val isPlayableTarget = targetFallback != null && (
                        com.duta.movie.util.VideoExtractor.isProbablyVideoHost(targetFallback) ||
                        isRotation || isExplicitServer
                    ) && !isConfirmedDead
                    if (isPlayableTarget && targetFallback != null) {
                        addResolutionLog("Falling back to WebView Shield for embed mirror: ${targetFallback.take(40)}...")
                        withContext(Dispatchers.Main) {
                            consecutiveAllBlacklistedCount = 0
                            _currentServerUrl.value = targetFallback
                            _resolvedUrl.value = targetFallback
                            _lastReferer.value = primaryUrl
                            _resolutionProgress.value = null
                            _isResolving.value = false
                        }
                    } else {
                        val playableCandidates = (listOfNotNull(targetFallback) + topMirrors + sortedServers.map { it.url }).distinct().filter { url ->
                            val isDead = deadMirrors.contains(url) || hardDeadMirrors.contains(url) || 
                                          exhaustedServerUrls.contains(url) || com.duta.movie.util.VideoExtractor.isConfirmedDead(url)
                            val isPlayable = com.duta.movie.util.VideoExtractor.isProbablyVideoHost(url) || isRotation || isExplicitServer
                            isPlayable && !isDead
                        }
                        val chosenFallback = playableCandidates.firstOrNull()
                        if (chosenFallback != null) {
                            addResolutionLog("Falling back to WebView Shield for embed mirror: ${chosenFallback.take(40)}...")
                            withContext(Dispatchers.Main) {
                                consecutiveAllBlacklistedCount = 0
                                _currentServerUrl.value = chosenFallback
                                _resolvedUrl.value = chosenFallback
                                _lastReferer.value = primaryUrl
                                _resolutionProgress.value = null
                                _isResolving.value = false
                            }
                        } else {
                            addResolutionLog("No direct stream and no viable fallback embed mirror. Rotating to next server...")
                            if (targetFallback != null) {
                                exhaustedServerUrls.add(targetFallback)
                            }
                            withContext(Dispatchers.Main) {
                                isRotationLocked = false
                                resolveNextServer(videoId, targetFallback, force = true)
                            }
                            return
                        }
                    }
                }
            } catch (e: Exception) { _error.value = "Resolution Failed: ${e.message}"; _isResolving.value = false }
    }

    private suspend fun startTVSeriesPlaybackResolution(videoId: String, serverUrl: String?, forceReset: Boolean, targetEpisode: Episode?, isRotation: Boolean = false, clearBlacklist: Boolean = forceReset) {
        Log.i("VideoViewModel", "startTVSeriesPlaybackResolution started for $videoId | serverUrl: ${serverUrl?.take(40)} | rotation: $isRotation")
        cleanDeadMirrors()
        if (forceReset) {
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            android.webkit.CookieManager.getInstance().flush()
            if (clearBlacklist) {
                deadMirrors.clear()
                cleanDeadMirrors()
                deadMirrors.addAll(hardDeadMirrors)
            }
            rotationCount = 0
            hasAttemptedAltHealing = false
            com.duta.movie.util.NetworkConfig.clearSessionData()
            withContext(Dispatchers.Main) { _resolvedUrl.value = null }
        }
        _isResolving.value = true; _error.value = null
        clearResolutionLog()
        
        if (targetEpisode != null) {
            _currentEpisode.value = targetEpisode
        } else if (forceReset) {
            _currentEpisode.value = null
            _currentServerUrl.value = null
        }
        
        try {
                var video = _videoMetadata.value?.takeIf { it.id == videoId } ?: getVideo(videoId)
                val targetSeasonNum = Regex("""(?i)\b(?:season|s)[-_ ]?(\d+)\b""").find(video?.videoUrl ?: "")?.groupValues?.get(1)?.toIntOrNull()
                    ?: Regex("""(?i)\b(?:season|s)\s*(\d+)\b""").find(video?.title ?: "")?.groupValues?.get(1)?.toIntOrNull()
                val hasMismatchedSeason = targetSeasonNum != null && video?.episodes?.any { ep ->
                    val epSeasonNum = Regex("""(?i)\b(?:season|s)[-_ ]?(\d+)\b""").find(ep.season.ifEmpty { ep.url })?.groupValues?.get(1)?.toIntOrNull()
                    epSeasonNum != null && epSeasonNum != targetSeasonNum
                } == true

                if (video == null || (video!!.isSeries == true && (video!!.episodes.isEmpty() || hasMismatchedSeason))) {
                    videoRepository.fetchVideoDetails(videoId)?.let { detailed -> 
                        metadataCache[videoId] = detailed
                        video = detailed
                    } ?: run {
                        if (video == null) video = getVideo(videoId)
                    }
                }
                
                if (video == null) {
                    _error.value = "Failed to load video metadata."; _isResolving.value = false; return
                }

                withContext(Dispatchers.Main) { 
                    if (_videoMetadata.value?.servers.isNullOrEmpty() || video!!.servers.isNotEmpty() || hasMismatchedSeason) {
                        _videoMetadata.value = video 
                    }
                }
                
                // CRITICAL FIX: Ensure _currentEpisode.value is NEVER null if we have episodes
                val curEpSeason = Regex("""(?i)\b(?:season|s)[-_ ]?(\d+)\b""").find(_currentEpisode.value?.season?.ifEmpty { _currentEpisode.value?.url ?: "" } ?: "")?.groupValues?.get(1)?.toIntOrNull()
                if (targetSeasonNum != null && curEpSeason != null && curEpSeason != targetSeasonNum) {
                    _currentEpisode.value = null
                }
                if (_currentEpisode.value == null && video!!.episodes.isNotEmpty()) {
                    val filteredEps = video!!.episodes.filter { ep ->
                        val low = ep.name.lowercase()
                        val notMeta = !low.contains("lihat semua") && !low.contains("see all") && 
                                      !low.contains("episode list") && !low.contains("daftar episode")
                        val epSeasonNum = Regex("""(?i)\b(?:season|s)[-_ ]?(\d+)\b""").find(ep.season.ifEmpty { ep.url })?.groupValues?.get(1)?.toIntOrNull()
                        val matchesSeason = targetSeasonNum == null || epSeasonNum == null || epSeasonNum == targetSeasonNum
                        notMeta && matchesSeason
                    }
                    val videoSlug = VideoExtractor.extractStableId(video!!.videoUrl).removePrefix("kb_").removePrefix("pm_")
                    val idSlug = videoId.removePrefix("kb_").removePrefix("pm_")
                    val matched = filteredEps.find { ep ->
                        val epSlug = VideoExtractor.extractStableId(ep.url).removePrefix("kb_").removePrefix("pm_")
                        epSlug == videoSlug || epSlug == idSlug ||
                        (videoSlug.length > 5 && (epSlug.contains(videoSlug) || videoSlug.contains(epSlug))) ||
                        (idSlug.length > 5 && (epSlug.contains(idSlug) || idSlug.contains(epSlug)))
                    }
                    _currentEpisode.value = matched ?: filteredEps.firstOrNull() ?: video!!.episodes.first()
                }

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
                    kotlinx.coroutines.delay(500)
                }

                val isEpisodeUrl = serverUrl?.let { (it.contains("/eps/") || it.contains("/episode/") || it.contains("-episode-") || it.contains("/episod/") || it.contains("-episod-") || it.contains("-epi-") || it.contains("/ep-")) && !it.contains("player=") && !it.contains("mirror=") } ?: false
                
                val episodePageUrl = if (isEpisodeUrl) serverUrl 
                                     else if (targetEpisode != null) targetEpisode.url
                                     else _currentEpisode.value?.url 
                                     
                val baseServers = if (episodePageUrl != null) {
                    val epSlug = com.duta.movie.util.VideoExtractor.extractStableId(episodePageUrl)
                    episodeServersCache[epSlug] ?: video!!.servers
                } else {
                    video!!.servers
                }

                val extraAlt = discoveredAltServers[videoId] ?: emptyList()
                val combinedBase = if (extraAlt.isNotEmpty()) {
                    val existingUrls = baseServers.map { it.url }.toSet()
                    baseServers + extraAlt.filter { it.url !in existingUrls }
                } else baseServers

                val sortedServers = combinedBase
                    .filter { !com.duta.movie.util.VideoExtractor.isDirectVideoUrl(it.url) }
                    .filter {
                        val lowUrl = it.url.lowercase()
                        val isYt = lowUrl.contains("youtube") || lowUrl.contains("youtu.be")
                        val ytMatches = if (isYt && video != null) {
                            val isPreVerified = discoveredAltServers[videoId]?.any { alt -> alt.url == it.url } == true
                            if (isPreVerified) true else {
                                val targetMeta = com.duta.movie.util.VideoExtractor.parseMovieTitleMeta(video!!.title, video!!.date)
                                val cleanName = com.duta.movie.util.VideoExtractor.sanitizeServerNameToTitle(it.name)
                                com.duta.movie.util.VideoExtractor.isYouTubeMovieMatch(targetMeta, cleanName)
                            }
                        } else true
                        !lowUrl.contains("imasdk") && !lowUrl.contains("googleapis.com") && (!isYt || (lowUrl.contains("/embed/") && ytMatches) || (it.name.contains("YouTube", ignoreCase = true) && ytMatches)) && !lowUrl.contains("listeamed")
                    }
                    .sortedByDescending { s -> 
                        val host = try { android.net.Uri.parse(s.url).host?.lowercase() ?: "" } catch(_: Exception) { "" }
                        val weight = sourceWeights.value[host] ?: 0
                        val lowUrl = s.url.lowercase()
                        val lowName = s.name.lowercase()
                        val voeBonus = if (lowUrl.contains("voe") || lowUrl.contains("johnfullwonder") || lowName.contains("voe")) 100 else 0
                        val trapPenalty = if (lowUrl.contains("abyss") || lowUrl.contains("bond") || lowName.contains("abyss") || lowName.contains("bond")) -50 else 0
                        com.duta.movie.util.VideoExtractor.getProviderPriority(s.name, s.url) + weight + voeBonus + trapPenalty
                    }

                val mirrorToResolve = if (isEpisodeUrl && sortedServers.isNotEmpty()) sortedServers.first().url
                                     else if (serverUrl != null && !isEpisodeUrl) serverUrl
                                     else sortedServers.firstOrNull()?.url ?: video!!.videoUrl

                val primaryUrl = episodePageUrl ?: mirrorToResolve
                
                if (trendingContent) {
                    Log.i("OwlEyeMonitoring", "TRACKING TRENDING SERIES: ${video!!.title} | Ep: ${_currentEpisode.value?.name} | Mirror: $mirrorToResolve")
                    addResolutionLog("Enforcing High-Demand resolution for Agent Kim...")
                }

                _currentServerUrl.value = mirrorToResolve

                if (_currentEpisode.value == null) {
                    val filteredEps = video!!.episodes.filter { ep ->
                        val low = ep.name.lowercase()
                        !low.contains("lihat semua") && !low.contains("see all") && 
                        !low.contains("episode list") && !low.contains("daftar episode")
                    }
                    
                    val primarySlug = com.duta.movie.util.VideoExtractor.extractStableId(primaryUrl)
                    val matchingEp = filteredEps.find { 
                        val epSlug = com.duta.movie.util.VideoExtractor.extractStableId(it.url)
                        epSlug == primarySlug || (primaryUrl.contains(epSlug) && epSlug.length > 5) || (it.url.contains(primarySlug) && primarySlug.length > 5)
                    }
                    
                    if (matchingEp != null) {
                        _currentEpisode.value = matchingEp
                    } else if (filteredEps.isNotEmpty()) {
                        _currentEpisode.value = filteredEps.first()
                    }
                }

                if (com.duta.movie.util.VideoExtractor.isDirectVideoUrl(mirrorToResolve)) {
                    addResolutionLog("Direct Stream Identified. Instant Handshake engaged.")
                    withContext(Dispatchers.Main) { 
                        _currentServerUrl.value = mirrorToResolve
                        _resolvedUrl.value = mirrorToResolve
                        _resolutionProgress.value = null
                        _isResolving.value = false
                    }; return
                }

                val lowMirror = mirrorToResolve.lowercase()
                val isWebEmbed = lowMirror.contains("dailymotion.com") || lowMirror.contains("dai.ly") ||
                                 lowMirror.contains("youtube.com") || lowMirror.contains("youtu.be") ||
                                 lowMirror.contains("bilibili.com") || lowMirror.contains("bilibili.tv")
                if (isWebEmbed) {
                    addResolutionLog("Web Embed Identified ($mirrorToResolve). Instant Handshake engaged.")
                    withContext(Dispatchers.Main) {
                        _currentServerUrl.value = mirrorToResolve
                        _resolvedUrl.value = mirrorToResolve
                        _resolutionProgress.value = null
                        _isResolving.value = false
                    }
                    return
                }

                val isPencuri = com.duta.movie.util.VideoExtractor.isPencuriMovie(videoId = videoId, videoUrl = primaryUrl, streamUrl = mirrorToResolve)
                val voeCandidates = (listOfNotNull(mirrorToResolve) + sortedServers.map { it.url })
                    .distinct()
                    .filter { url ->
                        val low = url.lowercase()
                        (low.contains("voe") || low.contains("johnfullwonder") || low.contains("cloudwindow")) &&
                        !deadMirrors.contains(url)
                    }

                if (voeCandidates.isNotEmpty()) {
                    for (candidate in voeCandidates) {
                        addResolutionLog("Owl's Eye: Dedicated Direct Extractor engaged on ${candidate.take(35)}...")
                        val voeExtract = com.duta.movie.util.VideoExtractor.extractVideoUrl(pageUrl = candidate, referer = primaryUrl)
                        if (voeExtract != null && com.duta.movie.util.VideoExtractor.isDirectVideoUrl(voeExtract.videoUrl)) {
                            addResolutionLog("Owl's Eye: Direct Stream Extracted successfully.")
                            withContext(Dispatchers.Main) {
                                _lastReferer.value = voeExtract.referer ?: primaryUrl
                                _lastCookies.value = voeExtract.cookies
                                _resolvedUrl.value = voeExtract.videoUrl
                                _resolutionProgress.value = null
                                _isResolving.value = false
                            }
                            return
                        }
                    }
                }

                val mirrorsFromMetadata = sortedServers.map { it.url }.toSet()
                val primarySlug = com.duta.movie.util.VideoExtractor.extractStableId(primaryUrl)
                
                val baseList = if (mirrorsFromMetadata.isEmpty()) listOf(mirrorToResolve, primaryUrl) else listOf(mirrorToResolve)
                
                val concurrencyLimit = if (trendingContent) 12 else 6 // Turbo

                val isSeries = video!!.isSeries == true
                var topMirrors = (baseList + mirrorsFromMetadata)
                    .distinct()
                    .filter { url -> 
                        val low = url.lowercase()
                        val host = try { android.net.Uri.parse(url).host?.lowercase() ?: java.net.URI(url).host?.lowercase() } catch(_: Throwable) { null }
                        val isVideoHost = com.duta.movie.util.VideoExtractor.isProbablyVideoHost(url)
                        val isSameEp = !isSeries || isVideoHost ||
                                       com.duta.movie.util.VideoExtractor.extractStableId(url) == primarySlug || 
                                       url.contains(primarySlug) || 
                                       primaryUrl.contains(com.duta.movie.util.VideoExtractor.extractStableId(url))
                        val isHostWhitelisted = com.duta.movie.util.VideoExtractor.isWhitelistedHost(host)
                        !low.contains("listeamed") && (host == null || !deadMirrors.contains(host) || isHostWhitelisted) && !deadMirrors.contains(url) && !hardDeadMirrors.contains(url) && !exhaustedServerUrls.contains(url) && !com.duta.movie.util.VideoExtractor.isConfirmedDead(url) && isSameEp
                    }
                    .take(concurrencyLimit)
                
                val isExplicitServer = serverUrl != null && !isEpisodeUrl && !forceReset && !isRotation
                val skipDirectRace = trendingContent && mirrorsFromMetadata.isEmpty() && !com.duta.movie.util.VideoExtractor.isDirectVideoUrl(mirrorToResolve)

                if (isExplicitServer || isRotation) {
                    // For an explicit server selection or rotation, target that specific server mirror only
                    if (mirrorToResolve != null && (deadMirrors.contains(mirrorToResolve) || hardDeadMirrors.contains(mirrorToResolve) || exhaustedServerUrls.contains(mirrorToResolve) || com.duta.movie.util.VideoExtractor.isConfirmedDead(mirrorToResolve))) {
                        topMirrors = topMirrors.filter { it != mirrorToResolve }
                    } else {
                        topMirrors = listOf(mirrorToResolve)
                    }
                } else if (topMirrors.isEmpty() || skipDirectRace) {
                    if (skipDirectRace) addResolutionLog("No mirrors found in metadata. Fast-tracking to WebView Shield...")
                    topMirrors = listOf(mirrorToResolve)
                }

                val cacheKey = "stream_cache_$primaryUrl"
                var winner = prefetchedMirrors.remove(cacheKey)
                
                if (winner == null) {
                    winner = if (skipDirectRace) null else executeGodModeRace(topMirrors, primaryUrl, trendingContent)
                } else {
                    addResolutionLog("Fast Direct: Using pre-fetched stream for instant start!")
                }

                if (winner != null) {
                    if (winner.videoUrl == "ALL_BLACKLISTED_FAST_SKIP") {
                        addResolutionLog("All God Mode mirrors are blacklisted. Server is dead. Fast-rotating...")
                        if (mirrorToResolve != null) exhaustedServerUrls.add(mirrorToResolve)
                        consecutiveAllBlacklistedCount++
                        if (consecutiveAllBlacklistedCount >= 4) {
                            addResolutionLog("STOP: $consecutiveAllBlacklistedCount consecutive servers all resolve to blacklisted hosts. Halting rotation.")
                            withContext(Dispatchers.Main) {
                                _error.value = "All available servers are currently down. Please try again later or choose a different title."
                                _isResolving.value = false
                            }
                            return
                        }
                        if (rotationCount >= 4) {
                            addResolutionLog("High failure rate. Clearing soft blacklist for recovery.")
                            deadMirrors.clear()
                            cleanDeadMirrors()
                            deadMirrors.addAll(hardDeadMirrors)
                        }
                        withContext(Dispatchers.Main) {
                            _error.value = "Server is dead. Fast rotating..."
                            isRotationLocked = false
                            resolveNextServer(videoId, mirrorToResolve, force = true)
                        }
                        return
                    }
                    withContext(Dispatchers.Main) {
                        consecutiveAllBlacklistedCount = 0 // Reset on success
                        _currentServerUrl.value = winner.sourceMirrorUrl ?: winner.referer ?: winner.videoUrl
                        _resolvedUrl.value = winner.videoUrl
                        _lastReferer.value = winner.referer; _lastCookies.value = winner.cookies
                        _resolutionProgress.value = null
                        _isResolving.value = false 
                    }
                } else {
                    val targetFallback = mirrorToResolve ?: topMirrors.firstOrNull()
                    val isConfirmedDead = targetFallback != null && (
                        deadMirrors.contains(targetFallback) ||
                        hardDeadMirrors.contains(targetFallback) ||
                        exhaustedServerUrls.contains(targetFallback) ||
                        com.duta.movie.util.VideoExtractor.isConfirmedDead(targetFallback)
                    )
                    
                    val isPlayableTarget = targetFallback != null && (
                        com.duta.movie.util.VideoExtractor.isProbablyVideoHost(targetFallback) ||
                        isRotation || isExplicitServer
                    ) && !isConfirmedDead
                    
                    if (isPlayableTarget && targetFallback != null) {
                        addResolutionLog("Falling back to WebView Shield for embed mirror: ${targetFallback.take(40)}...")
                        withContext(Dispatchers.Main) {
                            consecutiveAllBlacklistedCount = 0
                            _currentServerUrl.value = targetFallback
                            _resolvedUrl.value = targetFallback
                            _lastReferer.value = primaryUrl
                            _resolutionProgress.value = null
                            _isResolving.value = false
                        }
                    } else {
                        // Find any playable embed mirror that hasn't been exhausted or confirmed dead
                        val playableCandidates = (listOfNotNull(targetFallback) + topMirrors + sortedServers.map { it.url }).distinct().filter { url ->
                            val isDead = deadMirrors.contains(url) || hardDeadMirrors.contains(url) || 
                                          exhaustedServerUrls.contains(url) || com.duta.movie.util.VideoExtractor.isConfirmedDead(url)
                            val isPlayable = com.duta.movie.util.VideoExtractor.isProbablyVideoHost(url) || isRotation || isExplicitServer
                            isPlayable && !isDead
                        }
                        val chosenFallback = playableCandidates.firstOrNull()
                        if (chosenFallback != null) {
                            addResolutionLog("Falling back to WebView Shield for embed mirror: ${chosenFallback.take(40)}...")
                            withContext(Dispatchers.Main) {
                                consecutiveAllBlacklistedCount = 0
                                _currentServerUrl.value = chosenFallback
                                _resolvedUrl.value = chosenFallback
                                _lastReferer.value = primaryUrl
                                _resolutionProgress.value = null
                                _isResolving.value = false
                            }
                        } else {
                            addResolutionLog("No direct stream and no viable fallback embed mirror. Rotating to next server...")
                            if (targetFallback != null) {
                                exhaustedServerUrls.add(targetFallback)
                            }
                            withContext(Dispatchers.Main) {
                                isRotationLocked = false
                                resolveNextServer(videoId, targetFallback, force = true)
                            }
                            return
                        }
                    }
                }
            } catch (e: Exception) { _error.value = "Resolution Failed: ${e.message}"; _isResolving.value = false }
    }

    private suspend fun executeGodModeRace(
        topMirrors: List<String>, 
        primaryUrl: String, 
        trendingContent: Boolean
    ): com.duta.movie.util.VideoExtractor.ExtractionResult? = kotlinx.coroutines.withContext(Dispatchers.IO) {
        addResolutionLog("RACING TOP ${topMirrors.size} VIP MIRRORS (God Mode)...")
        _resolutionProgress.value = "Establishing Instant Connection..."
        val startTime = System.currentTimeMillis()

        val extractedCount = java.util.concurrent.atomic.AtomicInteger(0)
        val blacklistedCount = java.util.concurrent.atomic.AtomicInteger(0)

        val directWinnerChannel = kotlinx.coroutines.channels.Channel<com.duta.movie.util.VideoExtractor.ExtractionResult>(kotlinx.coroutines.channels.Channel.UNLIMITED)
        val jsWinnerChannel = kotlinx.coroutines.channels.Channel<com.duta.movie.util.VideoExtractor.ExtractionResult>(kotlinx.coroutines.channels.Channel.UNLIMITED)

        val jobs = topMirrors.mapIndexed { idx, url ->
            launch {
                if (deadMirrors.contains(url) || hardDeadMirrors.contains(url) || exhaustedServerUrls.contains(url) || com.duta.movie.util.VideoExtractor.isConfirmedDead(url)) {
                    return@launch
                }
                try {
                    val result = com.duta.movie.util.VideoExtractor.extractVideoUrl(url, referer = primaryUrl)
                    if (result != null) {
                        val enrichedResult = result.copy(
                            sourceMirrorUrl = url,
                            referer = result.referer ?: url
                        )
                        extractedCount.incrementAndGet()
                        val winUrl = enrichedResult.videoUrl
                        val lowWin = winUrl.lowercase()
                        val winHost = try { android.net.Uri.parse(winUrl).host?.lowercase() ?: java.net.URI(winUrl).host?.lowercase() } catch(_: Throwable) { null }
                        val isWhitelisted = com.duta.movie.util.VideoExtractor.isWhitelistedHost(winHost) || com.duta.movie.util.VideoExtractor.isWhitelistedHost(winUrl)
                        
                        if (lowWin.contains("listeamed") || deadMirrors.contains(winUrl) || hardDeadMirrors.contains(winUrl) || exhaustedServerUrls.contains(winUrl) || com.duta.movie.util.VideoExtractor.isConfirmedDead(winUrl) || (winHost != null && deadMirrors.contains(winHost) && !isWhitelisted)) {
                            blacklistedCount.incrementAndGet()
                            android.util.Log.w("VideoViewModel", "God Mode rejected $winUrl because host or url is blacklisted/dead.")
                            return@launch
                        }
                        
                        if (com.duta.movie.util.VideoExtractor.isStaticResource(winUrl)) {
                            return@launch
                        }
                        
                        val isTape = winUrl.contains("streamtape.com/get_video?") || winUrl.contains("tapecontent.net")
                        val isVoeDirect = winUrl.contains("cloudwindow") || winUrl.contains(".m3u8") || winUrl.contains(".mp4") || winUrl.contains(".mkv") || winUrl.contains(".webm")
                        val isJsOnly = com.duta.movie.util.VideoExtractor.isJsOnlyHost(winUrl)
                        val isDirect = com.duta.movie.util.VideoExtractor.isDirectVideoUrl(winUrl)
                        
                        if (isTape || isVoeDirect || isDirect) {
                            directWinnerChannel.trySend(enrichedResult)
                        } else {
                            jsWinnerChannel.trySend(enrichedResult)
                        }
                    } else {
                        if (com.duta.movie.util.VideoExtractor.isConfirmedDead(url)) {
                            deadMirrors.add(url)
                            exhaustedServerUrls.add(url)
                        }
                    }
                } catch (e: Exception) { 
                    if (e !is kotlinx.coroutines.CancellationException) {
                        Log.w("VideoViewModel", "Mirror race failure for $url: ${e.message}")
                    } else {
                        Log.v("VideoViewModel", "Mirror race job cancelled for $url")
                    }
                }
            }
        }
        
        launch {
            jobs.forEach { it.join() }
            directWinnerChannel.close()
            jsWinnerChannel.close()
        }

        val hasDirectCandidate = topMirrors.any { 
            val low = it.lowercase()
            low.contains("voe") || low.contains("johnfullwonder") || low.contains("streamtape") || 
            low.contains("cloudwindow") || low.contains("tapecontent") || low.contains("player=") || 
            low.contains("mirror=") || low.contains("ajax:") || low.contains("hgcloud") || 
            low.contains("vibuxer") || low.contains("hanerix") || low.contains("hglink") ||
            low.contains("playstream") || low.contains("embedpyrox") || low.contains("faststream") ||
            low.contains("morencius") || low.contains("vidhide") || low.contains("fujihide") ||
            low.contains("lulustream") || low.contains("luluvdo") || low.contains("dood")
        }
        val directTimeout = if (hasDirectCandidate) 6000L else if (trendingContent) 3000L else 4000L

        // Wait up to directTimeout for a direct winner
        val firstDirectWinner = kotlinx.coroutines.withTimeoutOrNull<com.duta.movie.util.VideoExtractor.ExtractionResult?>(directTimeout) {
            try {
                directWinnerChannel.receive()
            } catch (e: kotlinx.coroutines.channels.ClosedReceiveChannelException) {
                null
            }
        }

        if (firstDirectWinner != null) {
            jobs.forEach { it.cancel() } 
            val resolutionTime = System.currentTimeMillis() - startTime
            addResolutionLog("DIRECT VIP WINNER FOUND in ${resolutionTime}ms: ${firstDirectWinner.videoUrl.take(30)}...")
            return@withContext firstDirectWinner
        }

        // Direct timeout reached. Gather available JS candidates.
        val jsCandidates = mutableListOf<com.duta.movie.util.VideoExtractor.ExtractionResult>()
        var initialJs = jsWinnerChannel.tryReceive().getOrNull()
        while (initialJs != null) {
            jsCandidates.add(initialJs)
            initialJs = jsWinnerChannel.tryReceive().getOrNull()
        }

        // If no JS candidate arrived yet, wait for the first one to land
        if (jsCandidates.isEmpty()) {
            val first = kotlinx.coroutines.withTimeoutOrNull<com.duta.movie.util.VideoExtractor.ExtractionResult?>(if (trendingContent) 2000L else 3000L) {
                try {
                    jsWinnerChannel.receive()
                } catch (e: kotlinx.coroutines.channels.ClosedReceiveChannelException) {
                    null
                }
            }
            if (first != null) {
                jsCandidates.add(first)
                var more = jsWinnerChannel.tryReceive().getOrNull()
                while (more != null) {
                    jsCandidates.add(more)
                    more = jsWinnerChannel.tryReceive().getOrNull()
                }
            }
        }

        jobs.forEach { it.cancel() } 

        val resolutionTime = System.currentTimeMillis() - startTime
        
        // Select the best JS candidate based on provider priority (Hgcloud 130 > IndoStream 125 > ... > Abyss 30)
        val bestJsWinner = jsCandidates
            .filter { cand ->
                val winUrl = cand.videoUrl
                val winHost = try { android.net.Uri.parse(winUrl).host?.lowercase() ?: java.net.URI(winUrl).host?.lowercase() } catch(_: Throwable) { null }
                val isHostWhitelisted = com.duta.movie.util.VideoExtractor.isWhitelistedHost(winHost)
                !deadMirrors.contains(winUrl) && 
                !exhaustedServerUrls.contains(winUrl) && 
                !com.duta.movie.util.VideoExtractor.isConfirmedDead(winUrl) && 
                (winHost == null || !deadMirrors.contains(winHost) || isHostWhitelisted)
            }
            .maxByOrNull { cand ->
                com.duta.movie.util.VideoExtractor.getProviderPriority(cand.videoUrl, cand.videoUrl)
            }

        if (bestJsWinner != null) {
            addResolutionLog("VIP WINNER FOUND in ${resolutionTime}ms: ${bestJsWinner.videoUrl.take(30)}...")
            return@withContext bestJsWinner
        }
        
        if (extractedCount.get() > 0 && extractedCount.get() == blacklistedCount.get()) {
            addResolutionLog("All direct extractions resulted in blacklisted hosts. Fast-skipping WebView.")
            return@withContext com.duta.movie.util.VideoExtractor.ExtractionResult("ALL_BLACKLISTED_FAST_SKIP")
        }

        addResolutionLog("Direct resolution failed after ${resolutionTime}ms. Engaging WebView Shield.")
        return@withContext null
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
        
        if (finalUrl != null) {
            val low = finalUrl.lowercase()
            
            // OWL'S EYE: Hard block known ad injection iframes/scripts or locked CDN manifests from becoming a stream
            if (low.contains("tiktokcdn.com") || low.contains("ad-site") || low.contains("image?lk3s=") || low.contains("/hlsmod/") ||
                low.contains("imasdk") || low.contains("googleapis.com") || ((low.contains("youtube.com") || low.contains("youtu.be")) && !low.contains("/embed/")) || 
                low.contains("doubleclick") || low.contains("pagead") || low.contains("googleads") || low.contains("/aclk") || low.contains("google-analytics")) {
                Log.w("VideoViewModel", "Owl's Eye: Blocked ad script/manifest/fake stream from entering playback pipe: $finalUrl")
                return
            }
            
            // OWL'S EYE: Hard block non-video files from entering the playback pipe
            if (low.contains(".gif") || low.contains(".png") || low.contains(".jpg") || 
                low.contains(".jpeg") || low.contains(".ico") || low.contains(".svg")) {
                 if (!low.contains(".m3u8") && !low.contains(".mp4") && !low.contains(".mkv") && !low.contains(".webm") && !low.contains(".txt")) return
            }

            // CRITICAL: Block protected internal CDN streams and JS-Only embed hosts from overriding resolvedUrl
            val isDirectStream = low.contains(".m3u8") || low.contains(".mp4") || low.contains(".mkv") || low.contains(".webm") || low.contains(".txt") || low.contains("/stream/")
            val isProtectedStream = (low.contains("playmogo") ||
                                    low.contains("digitalidentity") || low.contains("sunrisevalleycreative") ||
                                    low.contains("johnfullwonder") || low.contains("voe") ||
                                    low.contains("platformdocumentation") || low.contains("hgcloud") || low.contains("hglink") ||
                                    com.duta.movie.util.VideoExtractor.isJsOnlyHost(finalUrl)) &&
                                    !isDirectStream && !low.contains("cloudwindow")
            if (isProtectedStream) {
                Log.w("VideoViewModel", "Protected/Internal CDN stream blocked from overriding resolvedUrl: $finalUrl")
                return
            }
        }

        _resolvedUrl.value = finalUrl
        _isResolving.value = false
        _resolutionProgress.value = null
        
        // Force a minor metadata trigger to ensure the UI re-evaluates the player type (Handoff Sync)
        _metadataTrigger.update { it + 1 }
        
        if (referer != null) _lastReferer.value = referer
        if (cookies != null) _lastCookies.value = cookies
        
        if (finalUrl != null) {
            val low = finalUrl.lowercase()
            val host = try { android.net.Uri.parse(finalUrl).host ?: "" } catch(_: Exception) { "" }
            val isAdOrBlocked = host.contains("google.com") || host.contains("googleapis.com") || host.contains("doubleclick") ||
                                low.contains("pagead") || low.contains("googleads") || low.contains("/aclk") || low.contains("analytics") ||
                                low.contains("imasdk") || ((low.contains("youtube.com") || low.contains("youtu.be")) && !low.contains("/embed/"))
            val isEphemeral = com.duta.movie.util.VideoExtractor.isEphemeralOrExpiredStream(finalUrl) ||
                             (com.duta.movie.util.VideoExtractor.isDirectVideoUrl(finalUrl) && !finalUrl.contains("archive.org"))
            if (!isAdOrBlocked && !isEphemeral) {
                val video = getVideo(videoId)
                if (video != null && video.isSeries == false) {
                    val updatedServers = video.servers.filter { 
                        val lowU = it.url.lowercase()
                        val lowN = it.name.lowercase()
                        !lowU.contains("google.com") && !lowU.contains("pagead") && !lowU.contains("/aclk") &&
                        !lowN.contains("google.com") && !lowN.contains("pagead") &&
                        !com.duta.movie.util.VideoExtractor.isEphemeralOrExpiredStream(it.url)
                    }.toMutableList()
                    val serverHost = if (host.isNotEmpty()) host else "Stream"
                    if (updatedServers.none { it.url == finalUrl }) updatedServers.add(VideoServer(serverHost, finalUrl!!))
                    val updatedVideo = video.copy(servers = updatedServers)
                    updateMetadataCache(listOf(updatedVideo), triggerBackground = false)
                    viewModelScope.launch { videoRepository.updateVideoInDb(updatedVideo) }
                } else if (video != null && video.isSeries == true) {
                    val currentServers = _videoMetadata.value?.servers?.filter { 
                        val lowU = it.url.lowercase()
                        val lowN = it.name.lowercase()
                        !lowU.contains("google.com") && !lowU.contains("pagead") && !lowU.contains("/aclk") &&
                        !lowN.contains("google.com") && !lowN.contains("pagead") &&
                        !com.duta.movie.util.VideoExtractor.isEphemeralOrExpiredStream(it.url)
                    }?.toMutableList() ?: mutableListOf()
                    val serverHost = if (host.isNotEmpty()) host else "Stream"
                    if (currentServers.none { it.url == finalUrl }) {
                        currentServers.add(VideoServer(serverHost, finalUrl!!))
                        _videoMetadata.value = _videoMetadata.value?.copy(servers = currentServers)
                    }
                }
            }
        }
    }

    fun fetchSubtitles(title: String, isTV: Boolean = false) {
        val curEp = _currentEpisode.value
        val effectiveTitle = if (curEp != null && (isTV || _videoMetadata.value?.isSeries == true)) {
            val epName = curEp.name.trim()
            val epNumMatch = Regex("""(?i)\b(?:episode|ep|e)\s*(\d+)\b""").find(epName) ?: Regex("""\b(\d+)\b""").find(epName)
            val epNum = epNumMatch?.groupValues?.get(1)?.toIntOrNull()
            val seasonMatch = Regex("""(?i)\b(?:season|s)\s*(\d+)\b""").find(curEp.season.ifEmpty { curEp.url })
            val seasonNum = seasonMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
            if (epNum != null) {
                val epTag = String.format("S%02dE%02d", seasonNum, epNum)
                "$title $epTag"
            } else {
                "$title $epName"
            }
        } else title

        if (effectiveTitle == lastSubtitleSearchTitle) return
        lastSubtitleSearchTitle = effectiveTitle
        subSearchJob?.cancel()
        subSearchJob = viewModelScope.launch(Dispatchers.IO) { 
            _isSubtitleLoading.value = true
            _subtitles.value = emptyList()
            try { 
                SubtitleExtractor.searchAndGetSubtitles(effectiveTitle, null, this, { subs -> 
                    val combined = (_subtitles.value + subs).distinctBy { it.url }
                    _subtitles.value = combined
                    
                    // Auto-select subtitle if enabled, none currently selected, and not explicitly dismissed
                    if (_selectedSubtitle.value == null && !userExplicitlyDismissedSubtitles && isAutoSubtitleEnabled.value) {
                        val defLang = defaultSubtitleLanguage.value
                        if (defLang != "Off" && defLang != "None") {
                            val normDef = SubtitleExtractor.normalizeLanguage(defLang)
                            val match = combined.find { 
                                SubtitleExtractor.normalizeLanguage(it.language).equals(normDef, ignoreCase = true)
                            }
                            if (match != null) {
                                selectSubtitle(match)
                            }
                        }
                    }
                }, { _isSubtitleLoading.value = false }) 
            } catch (e: Exception) { 
                withContext(Dispatchers.Main) { _subtitleError.value = e.message } 
            } finally { 
                _isSubtitleLoading.value = false 
            } 
        }
    }

    val currentSubtitleSearchTitle: String
        get() = lastSubtitleSearchTitle ?: ""

    fun searchSubtitles(customQuery: String) {
        val rawQ = customQuery.trim()
        if (rawQ.isBlank()) return
        val curEp = _currentEpisode.value
        val q = if (curEp != null && _videoMetadata.value?.isSeries == true && SubtitleExtractor.extractEpisodeNumber(rawQ) == null) {
            val epName = curEp.name.trim()
            val epNumMatch = Regex("""(?i)\b(?:episode|ep|e)\s*(\d+)\b""").find(epName) ?: Regex("""\b(\d+)\b""").find(epName)
            val epNum = epNumMatch?.groupValues?.get(1)?.toIntOrNull()
            val seasonMatch = Regex("""(?i)\b(?:season|s)\s*(\d+)\b""").find(curEp.season.ifEmpty { curEp.url })
            val seasonNum = seasonMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
            if (epNum != null) {
                val epTag = String.format("S%02dE%02d", seasonNum, epNum)
                "$rawQ $epTag"
            } else rawQ
        } else rawQ

        lastSubtitleSearchTitle = q
        subSearchJob?.cancel()
        subSearchJob = viewModelScope.launch(Dispatchers.IO) {
            _isSubtitleLoading.value = true
            _subtitles.value = emptyList()
            _subtitleError.value = null
            try {
                SubtitleExtractor.searchAndGetSubtitles(q, null, this, { subs ->
                    val combined = (_subtitles.value + subs).distinctBy { it.url }
                    _subtitles.value = combined
                }, { _isSubtitleLoading.value = false })
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { _subtitleError.value = e.message }
            } finally {
                _isSubtitleLoading.value = false
            }
        }
    }

    fun adjustSubtitleOffset(delta: Long) { _subtitleOffset.value += delta }

    fun selectSubtitle(subtitle: Subtitle?) {
        subResolveJob?.cancel()
        _subtitleCues.value = emptyList()
        if (subtitle == null) {
            _selectedSubtitle.value = null
            _subtitleError.value = null
            userExplicitlyDismissedSubtitles = true
            return
        }

        userExplicitlyDismissedSubtitles = false
        _selectedSubtitle.value = subtitle
        _isSubtitleLoading.value = true

        subResolveJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val localUri = if (subtitle.url.startsWith("file://")) {
                    subtitle.url
                } else {
                    SubtitleExtractor.resolveSubtitleUrl(subtitle.url, subtitle.language)
                }

                if (localUri != null && localUri.startsWith("file://")) {
                    val filePath = localUri.removePrefix("file://")
                    val file = File(filePath)
                    if (file.exists()) {
                        val cues = SubtitleParser.parse(file)
                        _subtitleCues.value = cues
                        _selectedSubtitle.value = subtitle.copy(localUri = localUri)
                        Log.i("VideoViewModel", "Subtitle resolved & parsed: ${cues.size} cues loaded from ${file.name}")
                    } else {
                        withContext(Dispatchers.Main) { _subtitleError.value = "Subtitle file not found" }
                    }
                } else {
                    withContext(Dispatchers.Main) { _subtitleError.value = "Failed to download subtitle" }
                }
            } catch (e: Exception) {
                Log.e("VideoViewModel", "Subtitle resolution error", e)
                withContext(Dispatchers.Main) { _subtitleError.value = "Subtitle error: ${e.message}" }
            } finally {
                _isSubtitleLoading.value = false
            }
        }
    }

    fun clearSubtitleError() { _subtitleError.value = null }

    fun fetchVideosForCategoryRow(category: String) {
        if (_categoryLoading.value[category] == true || _isPlayerActive.value) return
        viewModelScope.launch {
            _categoryLoading.update { it + (category to true) }
            
            // OWL'S EYE: Load from cache first for instant UI!
            try {
                val cached = withContext(Dispatchers.IO) { videoRepository.getCachedVideosByCategory(category) }
                if (cached.isNotEmpty() && !_isPlayerActive.value) {
                    val displayCached = if (category.contains("country/malaysia", ignoreCase = true)) {
                        VideoExtractor.sortVideosByNewestRelease(cached)
                    } else cached
                    _categoryVideos.update { it + (category to displayCached) }
                    if (category == moviePath) {
                        _latestMovies.value = cached
                        _headlinerVideo.value = cached.firstOrNull()
                    }
                    if (category == seriesPath) _latestTVSeries.value = cached
                }
            } catch (e: Exception) { android.util.Log.e("VideoViewModel", "Cache load failed", e) }

            try { 
                val results = withContext(Dispatchers.IO) { videoRepository.fetchVideosBySection(category, page = 1, count = 150) }
                if (results.isNotEmpty()) { 
                    if (!_isPlayerActive.value) {
                        updateMetadataCache(results, triggerBackground = false)
                        _categoryVideos.update { it + (category to results) }
                        categoryPages[category] = (results.size / 20).coerceAtLeast(1) 
                        
                        // SYNC: Update specific state flows for headliner/UI stability
                        if (category == moviePath) {
                            _latestMovies.value = results
                            _headlinerVideo.value = results.firstOrNull()
                        }
                        if (category == seriesPath) _latestTVSeries.value = results
                    }
                }
                else if (results.isEmpty() && category == moviePath) {
                    VideoExtractor.probeForNewDomain()?.let { fetchVideosForCategoryRow(category) }
                }
            } finally { _categoryLoading.update { it + (category to false) } }
        }
    }

    fun loadMoreForCategoryRow(category: String) {
        if (_categoryLoading.value[category] == true) return
        viewModelScope.launch {
            val page = categoryPages[category] ?: 1; _categoryLoading.update { it + (category to true) }
            try { 
                val more = videoRepository.fetchVideosBySection(category, page = page + 1, count = 150)
                if (more.isNotEmpty()) { 
                    updateMetadataCache(more, triggerBackground = false)
                    _categoryVideos.update { current -> 
                        val existing = current[category] ?: emptyList()
                        val ids = existing.map { it.id }.toSet()
                        val combined = existing + more.filter { it.id !in ids }
                        val sorted = if (category.contains("country/malaysia", ignoreCase = true)) {
                            VideoExtractor.sortVideosByNewestRelease(combined)
                        } else combined
                        current + (category to sorted)
                    }
                    categoryPages[category] = page + (more.size / 20) 
                } 
            } finally { 
                _categoryLoading.update { it + (category to false) } 
            }
        }
    }

    fun resolveNextServer(videoId: String, currentServerUrl: String?, force: Boolean = false) {
        if (isRotationLocked && !force) {
            Log.d("VideoViewModel", "resolveNextServer ignored: Rotation Locked")
            return
        }
        isRotationLocked = true

        val effectiveVideoId = videoId.ifEmpty { activeVideoId ?: _videoMetadata.value?.id ?: "" }
        Log.i("VideoViewModel", "resolveNextServer invoked: videoId='$videoId' (effective='$effectiveVideoId'), currentServer='$currentServerUrl'")

        viewModelScope.launch {
            try {
                var video = _videoMetadata.value?.takeIf { it.id == effectiveVideoId } ?: getVideo(effectiveVideoId)
                if (effectiveVideoId.startsWith("ia_pramlee")) {
                    val freshClassic = com.duta.movie.util.VideoExtractor.fetchArchivePramleeVideos().find { it.id == effectiveVideoId }
                    if (freshClassic != null) {
                        video = freshClassic
                        metadataCache[effectiveVideoId] = freshClassic
                        withContext(Dispatchers.Main) { _videoMetadata.value = freshClassic }
                    }
                } else if (video == null && effectiveVideoId.isNotEmpty()) {
                    Log.i("VideoViewModel", "resolveNextServer: video metadata missing, fetching details for '$effectiveVideoId'...")
                    video = videoRepository.fetchVideoDetails(effectiveVideoId)?.also { detailed ->
                        metadataCache[effectiveVideoId] = detailed
                        withContext(Dispatchers.Main) { _videoMetadata.value = detailed }
                    }
                }

                if (video == null) {
                    Log.e("VideoViewModel", "resolveNextServer: Cannot find video for ID: '$effectiveVideoId'")
                    withContext(Dispatchers.Main) {
                        _error.value = "All available video servers for this title are currently down or deleted. Please try another source or title."
                        _isResolving.value = false
                        _resolvedUrl.value = null
                    }
                    return@launch
                }

                rotationCount++
                
                val isTrending = video.title.contains("Agent Kim", ignoreCase = true) || video.title.contains("Manager Kim", ignoreCase = true)
                
                val maxRotation = if (isTrending) 20 else 10
                if (rotationCount > maxRotation) {
                    Log.w("VideoViewModel", "resolveNextServer: max rotation reached ($rotationCount > $maxRotation)")
                    withContext(Dispatchers.Main) {
                        _error.value = "We tried $rotationCount servers but all are currently unresponsive. Please try again in a few minutes or switch to a different source."
                        _isResolving.value = false
                        _resolvedUrl.value = null
                    }
                    return@launch
                }
    
                if (rotationCount >= 5 && rotationCount % 5 == 0) {
                    addResolutionLog("Multiple failures detected. Refreshing mirror blacklist...")
                    deadMirrors.clear()
                    cleanDeadMirrors()
                    deadMirrors.addAll(hardDeadMirrors)
                }
    
                // Important: Use the servers from the current episode cache if available
                val extraAlt = discoveredAltServers[effectiveVideoId] ?: emptyList()
                val rawServers = if (video.isSeries == true && _currentEpisode.value != null) {
                    val epSlug = VideoExtractor.extractStableId(_currentEpisode.value!!.url)
                    episodeServersCache[epSlug] ?: video.servers
                } else {
                    video.servers
                }
                val existingUrls = rawServers.map { it.url }.toSet()
                val serversToUse = (rawServers + extraAlt.filter { it.url !in existingUrls })
                    .filter { !com.duta.movie.util.VideoExtractor.isEphemeralOrExpiredStream(it.url) }
                    .map { s ->
                        val optUrl = VideoExtractor.optimizeArchiveUrl(s.url)
                        if (optUrl != s.url) {
                            s.copy(url = optUrl, name = if (!s.name.contains("Fast Direct")) "${s.name} (Fast Direct)" else s.name)
                        } else s
                    }
                
                if (serversToUse.isEmpty()) {
                     val epUrl = _currentEpisode.value?.url ?: currentServerUrl
                     if (video.isSeries == true && epUrl != null) {
                        addResolutionLog("No mirrors in state. Retrying episode page resolution.")
                        playTVSeries(effectiveVideoId, epUrl, forceReset = false, isRotation = true)
                     } else {
                        if (!hasAttemptedAltHealing) {
                            hasAttemptedAltHealing = true
                            addResolutionLog("No mirrors available. Searching partner aggregators for alternative sources...")
                            val altServers = VideoExtractor.findAlternativeSources(video)
                            if (altServers.isNotEmpty()) {
                                addResolutionLog("Auto-healed: Discovered ${altServers.size} fresh mirrors from partner! Resuming...")
                                discoveredAltServers[effectiveVideoId] = altServers
                                val combinedServers = (video.servers + altServers).distinctBy { it.url }
                                val updatedVideo = video.copy(servers = combinedServers)
                                metadataCache[effectiveVideoId] = updatedVideo
                                withContext(Dispatchers.Main) {
                                    _videoMetadata.value = updatedVideo
                                }
                                viewModelScope.launch { videoRepository.updateVideoInDb(updatedVideo) }
                                rotationCount = 0
                                isRotationLocked = false
                                playMovie(effectiveVideoId, altServers.first().url, forceReset = false, isRotation = false)
                                return@launch
                            }
                        }
                        Log.e("VideoViewModel", "resolveNextServer: serversToUse is empty for $effectiveVideoId")
                        withContext(Dispatchers.Main) {
                            _error.value = "No video servers found for this title. Please try another source."
                            _isResolving.value = false
                            _resolvedUrl.value = null
                        }
                     }
                     return@launch
                }
    
                val sortedServers = serversToUse.filter { s ->
                    val host = try { android.net.Uri.parse(s.url).host?.lowercase() ?: java.net.URI(s.url).host?.lowercase() ?: "" } catch(_: Throwable) { "" }
                    val lowUrl = s.url.lowercase()
                    if (lowUrl.contains("imasdk") || lowUrl.contains("googleapis.com") || lowUrl.contains("listeamed")) return@filter false
                    val isYt = lowUrl.contains("youtube") || lowUrl.contains("youtu.be")
                    val isDm = lowUrl.contains("dailymotion") || lowUrl.contains("dai.ly")
                    val isBili = lowUrl.contains("bilibili")
                    if (isYt || isDm || isBili) {
                        val isPreVerified = discoveredAltServers[effectiveVideoId]?.any { it.url == s.url } == true
                        if (!isPreVerified) {
                            val targetMeta = com.duta.movie.util.VideoExtractor.parseMovieTitleMeta(video.title, video.date)
                            val cleanName = com.duta.movie.util.VideoExtractor.sanitizeServerNameToTitle(s.name)
                            if (!com.duta.movie.util.VideoExtractor.isYouTubeMovieMatch(targetMeta, cleanName)) return@filter false
                        }
                    }
                    if (isYt && !lowUrl.contains("/embed/") && !s.name.contains("YouTube", ignoreCase = true)) return@filter false
                    val isHostWhitelisted = com.duta.movie.util.VideoExtractor.isWhitelistedHost(host)
                    (!deadMirrors.contains(host) || isHostWhitelisted) && !deadMirrors.contains(s.url) && !hardDeadMirrors.contains(s.url) && !exhaustedServerUrls.contains(s.url) && !com.duta.movie.util.VideoExtractor.isEphemeralOrExpiredStream(s.url) && !com.duta.movie.util.VideoExtractor.isConfirmedDead(s.url)
                }.sortedByDescending { s -> 
                    val host = try { android.net.Uri.parse(s.url).host?.lowercase() ?: java.net.URI(s.url).host?.lowercase() ?: "" } catch(_: Throwable) { "" }
                    val weight = sourceWeights.value[host] ?: 0
                    val mirrorStability = if (VideoExtractor.isIndoStreamAmt(s.url)) 20 else 0
                    VideoExtractor.getProviderPriority(s.name, s.url) + weight + mirrorStability
                }
                
                if (sortedServers.isEmpty()) {
                    if (!hasAttemptedAltHealing) {
                        hasAttemptedAltHealing = true
                        addResolutionLog("All mirrors failed. Searching partner aggregators for alternative sources...")
                        val altServers = VideoExtractor.findAlternativeSources(video)
                        if (altServers.isNotEmpty()) {
                            addResolutionLog("Auto-healed: Discovered ${altServers.size} fresh mirrors from partner! Resuming playback...")
                            discoveredAltServers[effectiveVideoId] = altServers
                            val combinedServers = (video.servers + altServers).distinctBy { it.url }
                            val updatedVideo = video.copy(servers = combinedServers)
                            metadataCache[effectiveVideoId] = updatedVideo
                            withContext(Dispatchers.Main) {
                                _videoMetadata.value = updatedVideo
                            }
                            viewModelScope.launch { videoRepository.updateVideoInDb(updatedVideo) }
                            rotationCount = 0
                            consecutiveAllBlacklistedCount = 0
                            isRotationLocked = false
                            if (video.isSeries == true) {
                                playTVSeries(effectiveVideoId, altServers.first().url, forceReset = false, targetEpisode = _currentEpisode.value, isRotation = false)
                            } else {
                                playMovie(effectiveVideoId, altServers.first().url, forceReset = false, isRotation = false)
                            }
                            return@launch
                        } else {
                            addResolutionLog("Cross-provider search completed: No alternative sources found.")
                        }
                    }

                    val allServersDead = serversToUse.isEmpty() || serversToUse.all { s ->
                        val host = try { android.net.Uri.parse(s.url).host?.lowercase() ?: java.net.URI(s.url).host?.lowercase() } catch(_: Throwable) { null }
                        val isHostWhitelisted = com.duta.movie.util.VideoExtractor.isWhitelistedHost(host)
                        (host != null && !isHostWhitelisted && (hardDeadMirrors.contains(host) || deadMirrors.contains(host))) || 
                        hardDeadMirrors.contains(s.url) || deadMirrors.contains(s.url) ||
                        exhaustedServerUrls.contains(s.url) ||
                        com.duta.movie.util.VideoExtractor.isConfirmedDead(s.url)
                    }
                    val isSingleServer = serversToUse.size <= 1
                    val maxTries = if (isSingleServer) 3 else serversToUse.size
                    Log.w("VideoViewModel", "sortedServers is EMPTY! allServersDead=$allServersDead, rotationCount=$rotationCount, serversCount=${serversToUse.size}")
                    if (allServersDead || rotationCount >= maxTries || rotationCount >= maxRotation) {
                        Log.e("VideoViewModel", "All servers dead or exhausted for $effectiveVideoId. Showing error UI.")
                        addResolutionLog("All available mirrors for this title have failed or are dead. Halting playback.")
                        withContext(Dispatchers.Main) {
                            _error.value = "All available video servers for this title are currently down or deleted. Please try another source or title."
                            _isResolving.value = false
                            _resolvedUrl.value = null
                        }
                        return@launch
                    }
                    addResolutionLog("No fresh mirrors available. Retrying after partial reset...")
                    exhaustedServerUrls.clear()
                    deadMirrors.clear()
                    cleanDeadMirrors()
                    deadMirrors.addAll(hardDeadMirrors)
                    _isResolving.value = false
                    if (video.isSeries == true) {
                        playTVSeries(effectiveVideoId, serversToUse.firstOrNull()?.url, forceReset = false, targetEpisode = _currentEpisode.value, isRotation = true)
                    } else {
                        playMovie(effectiveVideoId, serversToUse.firstOrNull()?.url, forceReset = false, isRotation = true)
                    }
                    return@launch
                }

                val activeUrl = (if (currentServerUrl != null && !currentServerUrl.startsWith("about:")) currentServerUrl else null) ?: _currentServerUrl.value?.takeIf { !it.startsWith("about:") }
                val activeHost = try { android.net.Uri.parse(activeUrl ?: "").host?.lowercase() } catch(_: Exception) { null }
                val parentMirror = _currentServerUrl.value
                val parentMirrorHost = try { android.net.Uri.parse(parentMirror ?: "").host?.lowercase() } catch(_: Exception) { null }
                val currentIndex = sortedServers.indexOfFirst { 
                    it.url == activeUrl || 
                    (parentMirror != null && it.url == parentMirror) ||
                    (activeHost != null && try { android.net.Uri.parse(it.url).host?.lowercase() == activeHost } catch(_: Exception) { false }) ||
                    (parentMirrorHost != null && try { android.net.Uri.parse(it.url).host?.lowercase() == parentMirrorHost } catch(_: Exception) { false })
                }
                if (activeUrl != null) exhaustedServerUrls.add(activeUrl)
                if (currentIndex != -1) exhaustedServerUrls.add(sortedServers[currentIndex].url)

                val freshServers = sortedServers.filter { 
                    !exhaustedServerUrls.contains(it.url) && 
                    !deadMirrors.contains(it.url) && 
                    !hardDeadMirrors.contains(it.url) && 
                    !com.duta.movie.util.VideoExtractor.isConfirmedDead(it.url)
                }

                val nextServer = freshServers.firstOrNull() ?: run {
                    val nextIndex = if (currentIndex == -1 || currentIndex >= sortedServers.size - 1) 0 else currentIndex + 1
                    sortedServers[nextIndex]
                }
                Log.i("VideoViewModel", "Mirror Rotation: Shifting to ${nextServer.name} (${nextServer.url.take(30)}...)")
                addResolutionLog("Mirror Rotation: Shifting to ${nextServer.name}...")
                
                if (video.isSeries == true) {
                    playTVSeries(effectiveVideoId, nextServer.url, forceReset = false, targetEpisode = _currentEpisode.value, isRotation = true)
                } else {
                    playMovie(effectiveVideoId, nextServer.url, forceReset = false, isRotation = true)
                }
                
                val rotationDelay = if (isTrending) 1500L else 800L
                delay(rotationDelay)
            } finally {
                isRotationLocked = false
            }
        }
    }

    fun searchAlternativeSources(videoId: String, onResult: ((Int) -> Unit)? = null) {
        val effectiveVideoId = videoId.ifEmpty { activeVideoId ?: _videoMetadata.value?.id ?: "" }
        if (effectiveVideoId.isEmpty() || _isSearchingAlternatives.value) return

        viewModelScope.launch {
            _isSearchingAlternatives.value = true
            try {
                val video = _videoMetadata.value?.takeIf { it.id == effectiveVideoId } ?: getVideo(effectiveVideoId) ?: run {
                    videoRepository.fetchVideoDetails(effectiveVideoId)?.also { detailed ->
                        metadataCache[effectiveVideoId] = detailed
                        withContext(Dispatchers.Main) { _videoMetadata.value = detailed }
                    }
                }
                if (video == null) {
                    withContext(Dispatchers.Main) { onResult?.invoke(0) }
                    return@launch
                }

                val healed = VideoExtractor.healVideoFromAlternativeSources(video)
                val altServers = healed?.servers ?: VideoExtractor.findAlternativeSources(video)
                if (altServers.isNotEmpty() || healed?.episodes?.isNotEmpty() == true) {
                    val combinedServers = (video.servers + altServers).distinctBy { it.url }
                    val resolvedEpisodes = if (video.episodes.isEmpty() && healed?.episodes?.isNotEmpty() == true) healed.episodes else video.episodes
                    val updatedVideo = video.copy(
                        servers = combinedServers,
                        episodes = resolvedEpisodes,
                        isSeries = if (resolvedEpisodes.isNotEmpty()) true else video.isSeries
                    )
                    metadataCache[effectiveVideoId] = updatedVideo
                    withContext(Dispatchers.Main) {
                        _videoMetadata.value = updatedVideo
                    }
                    viewModelScope.launch { videoRepository.updateVideoInDb(updatedVideo) }
                    withContext(Dispatchers.Main) { onResult?.invoke(combinedServers.size) }
                } else {
                    withContext(Dispatchers.Main) { onResult?.invoke(0) }
                }
            } catch (e: Exception) {
                Log.e("VideoViewModel", "Error searching alternative sources: ${e.message}", e)
                withContext(Dispatchers.Main) { onResult?.invoke(0) }
            } finally {
                _isSearchingAlternatives.value = false
            }
        }
    }

    fun resolveNextEpisode(videoId: String): Boolean {
        Log.i("VideoViewModel", "AUTOPLAY: Looking for next episode for $videoId")
        val video = getVideo(videoId) ?: return false
        if (video.isSeries == false || video.episodes.isEmpty()) {
            Log.w("VideoViewModel", "AUTOPLAY: Not a series or no episodes found.")
            return false
        }
        
        val episodes = video.episodes.filter { ep ->
            val low = ep.name.lowercase()
            !low.contains("lihat semua") && !low.contains("see all") && 
            !low.contains("episode list") && !low.contains("daftar episode") &&
            !low.contains("next") && !low.contains("prev") && !low.contains("halaman")
        }
        
        val currentEp = _currentEpisode.value
        val currentIdx = if (currentEp != null) {
            val idx = episodes.indexOfFirst { it.url == currentEp.url || it.id == currentEp.id }
            if (idx == -1) {
                val slug = VideoExtractor.extractStableId(currentEp.url)
                episodes.indexOfFirst { VideoExtractor.extractStableId(it.url) == slug || it.url.contains(slug) }
            } else idx
        } else {
            val activeUrl = _currentServerUrl.value ?: ""
            val activeSlug = VideoExtractor.extractStableId(activeUrl)
            episodes.indexOfFirst { it.url == activeUrl || VideoExtractor.extractStableId(it.url) == activeSlug || it.url.contains(activeSlug) }
        }
        
        Log.d("VideoViewModel", "AUTOPLAY Engine: FoundIdx=$currentIdx, TotalEps=${episodes.size}, ActiveEp=${currentEp?.name}")

        if (currentIdx != -1 && currentIdx < episodes.size - 1) {
            val nextEp = episodes[currentIdx + 1]
            Log.i("VideoViewModel", "AUTOPLAY: Progressing to Next Episode: ${nextEp.name} (${nextEp.url})")
            _shouldSuppressResume.value = true // Suppress dialog for autoplay
            playTVSeries(videoId, nextEp.url, forceReset = true, targetEpisode = nextEp, clearBlacklist = false)
            return true
        }
        
        Log.w("VideoViewModel", "AUTOPLAY: End of series or episode not found.")
        return false
    }

    private var isPrefetching = false
    private val prefetchedMirrors = ConcurrentHashMap<String, com.duta.movie.util.VideoExtractor.ExtractionResult>()

    fun prefetchNextEpisode(videoId: String) {
        if (isPrefetching) return
        val video = getVideo(videoId) ?: return
        if (video.isSeries == false || video.episodes.isEmpty()) return
        
        val episodes = video.episodes.filter { ep ->
            val low = ep.name.lowercase()
            !low.contains("lihat semua") && !low.contains("see all") && 
            !low.contains("episode list") && !low.contains("daftar episode") &&
            !low.contains("next") && !low.contains("prev") && !low.contains("halaman")
        }
        
        val currentEp = _currentEpisode.value
        val currentIdx = if (currentEp != null) {
            val idx = episodes.indexOfFirst { it.url == currentEp.url || it.id == currentEp.id }
            if (idx == -1) {
                val slug = VideoExtractor.extractStableId(currentEp.url)
                episodes.indexOfFirst { VideoExtractor.extractStableId(it.url) == slug || it.url.contains(slug) }
            } else idx
        } else -1

        if (currentIdx != -1 && currentIdx < episodes.size - 1) {
            val nextEp = episodes[currentIdx + 1]
            val cacheKey = "stream_cache_${nextEp.url}"
            if (prefetchedMirrors.containsKey(cacheKey)) return // Already prefetched
            
            Log.i("VideoViewModel", "AUTOPLAY Engine: Pre-fetching Next Episode: ${nextEp.name}")
            isPrefetching = true
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val fetched = VideoExtractor.fetchVideoDetails(nextEp.url)
                    val activeMirrors = fetched?.servers?.map { it.url }?.filter { !it.contains("youtube") && !it.contains("trailer") } ?: emptyList()
                    if (activeMirrors.isNotEmpty()) {
                        val limit = 12
                        val limitedMirrors = if (activeMirrors.size > limit) activeMirrors.subList(0, limit) else activeMirrors
                        val fastestMirror = executeGodModeRace(limitedMirrors, nextEp.url, true)
                        if (fastestMirror != null) {
                            prefetchedMirrors[cacheKey] = fastestMirror
                            Log.i("VideoViewModel", "AUTOPLAY Engine: Pre-fetched successfully")
                        }
                    }
                } catch(e: Exception) {
                    Log.e("VideoViewModel", "Failed to prefetch", e)
                } finally {
                    isPrefetching = false
                }
            }
        }
    }

    fun recordExoPlayerFailure(host: String) { viewModelScope.launch(Dispatchers.IO) { preferenceManager.recordHostPlayerFailure(host) } }

    fun clearCache() {
        videoRepository.clearAllCaches()
        metadataCache.clear()
        episodeServersCache.clear()
        filterResultCache.clear()
        _metadataTrigger.value++
    }

    fun clearRecentlyWatched() {
        viewModelScope.launch {
            videoRepository.clearRecentlyWatched()
            if (com.duta.movie.util.DeviceUtils.isTvDevice(context)) {
                try {
                    com.duta.movie.tv.TvWatchNextManager.clearAllWatchNext(context)
                    com.duta.movie.tv.TvChannelSyncWorker.syncChannelDirectly(context)
                } catch (_: Exception) {}
            }
        }
    }

    fun getVideoProgress(id: String): Flow<Long> = videoRepository.getVideoProgress(id)
    fun getWatchedEpisodes(videoId: String): Flow<Set<String>> = videoRepository.getWatchedEpisodes(videoId)
    
    fun getEpisodeProgressId(videoId: String, episodeUrl: String): String {
        val slug = com.duta.movie.util.VideoExtractor.extractStableId(episodeUrl)
        return "${videoId}_ep_${slug}"
    }
    
    fun saveVideoProgress(videoId: String, position: Long, duration: Long, episodeUrl: String? = null) {
        viewModelScope.launch {
            val progressId = if (episodeUrl != null && (episodeUrl.contains("/episode/") || episodeUrl.contains("-eps-") || episodeUrl.contains("-episode-") || episodeUrl.contains("/ep-"))) {
                getEpisodeProgressId(videoId, episodeUrl)
            } else {
                videoId
            }
            videoRepository.saveVideoProgress(progressId, position)
            videoRepository.saveVideoDuration(progressId, duration)

            // Update Android TV Home Screen Watch Next & Recommendations
            if (com.duta.movie.util.DeviceUtils.isTvDevice(context)) {
                try {
                    val video = videoRepository.getVideo(videoId)
                    if (video != null) {
                        com.duta.movie.tv.TvWatchNextManager.updateWatchNext(
                            context = context,
                            video = video,
                            positionMs = position,
                            durationMs = duration
                        )
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun addToRecentlyWatched(videoId: String) {
        viewModelScope.launch {
            videoRepository.addToRecentlyWatched(videoId)
        }
    }

    fun notifyPlaybackSuccess(url: String) {
        Log.d("VideoViewModel", "Playback Success: $url")
        isPlaybackActive = true
        _isResolving.value = false
        _error.value = null
        rotationCount = 0
        exhaustedServerUrls.clear()
        consecutiveAllBlacklistedCount = 0
        
        val host = try { android.net.Uri.parse(url).host?.lowercase() } catch(_: Exception) { null }
        
        _videoMetadata.value?.let { v ->
            if (v.title.contains("Agent Kim", ignoreCase = true) || v.title.contains("Manager Kim", ignoreCase = true)) {
                Log.i("OwlEyeMonitoring", "SUCCESS: Agent Kim playing on $host")
            }
        }

        host?.let { 
            viewModelScope.launch(Dispatchers.IO) { 
                preferenceManager.recordHostPlayerSuccess(it)
                preferenceManager.incrementSourceWeight(it)
            } 
        }
    }

    private fun loadData() { 
        fetchHomeData()
        fetchPakcikRekomenVideos()
        startPakcikRekomenAutoSync()
    }

    fun applyMetadata(video: Video): Video {
        val cached = metadataCache[video.id] ?: return video
        val baseServers = if (cached.servers.size >= video.servers.size) cached.servers else video.servers
        val extra = discoveredAltServers[video.id] ?: emptyList()
        val combinedServers = if (extra.isNotEmpty()) {
            val existingUrls = baseServers.map { it.url }.toSet()
            baseServers + extra.filter { it.url !in existingUrls }
        } else baseServers

        val targetMeta = com.duta.movie.util.VideoExtractor.parseMovieTitleMeta(video.title, video.date)
        val validServers = combinedServers.filter { s ->
            val lowUrl = s.url.lowercase()
            val isAlt = lowUrl.contains("youtube") || lowUrl.contains("youtu.be") ||
                        lowUrl.contains("dailymotion") || lowUrl.contains("dai.ly") ||
                        lowUrl.contains("bilibili")
            if (isAlt) {
                val isPreVerified = discoveredAltServers[video.id]?.any { it.url == s.url } == true
                if (isPreVerified) true else {
                    val cleanName = com.duta.movie.util.VideoExtractor.sanitizeServerNameToTitle(s.name)
                    com.duta.movie.util.VideoExtractor.isYouTubeMovieMatch(targetMeta, cleanName)
                }
            } else true
        }

        val resolvedEpisodes = if (video.episodes.isNotEmpty()) video.episodes else cached.episodes
        return video.copy(
            thumbnailUrl = if (video.thumbnailUrl.isNotEmpty()) video.thumbnailUrl else cached.thumbnailUrl,
            backdropUrl = if (video.backdropUrl.isNotEmpty()) video.backdropUrl else cached.backdropUrl,
            previewUrl = if (video.previewUrl.isNotEmpty()) video.previewUrl else cached.previewUrl,
            description = if (cached.description.length > video.description.length) cached.description else video.description,
            servers = validServers,
            episodes = resolvedEpisodes,
            season = if (video.season.isNotEmpty()) video.season else cached.season,
            isSeries = if (resolvedEpisodes.isNotEmpty()) true else (video.isSeries ?: cached.isSeries),
            actresses = (cached.actresses + video.actresses).distinct(),
            quality = if (cached.quality.isNotEmpty()) cached.quality else video.quality,
            duration = if (cached.duration.isNotEmpty() && cached.duration != "??:??") cached.duration else video.duration
        )
    }

    fun setPlayerActive(active: Boolean) { 
        _isPlayerActive.value = active
        if (active) {
            backgroundDetailsJob?.cancel()
            backgroundDetailsJob = null
            prefetchJob?.cancel()
            prefetchJob = null
        } else { 
            _resolvedUrl.value = null
            _currentServerUrl.value = null
            _subtitles.value = emptyList()
            _selectedSubtitle.value = null
            _subtitleCues.value = emptyList()
            subResolveJob?.cancel()
            subResolveJob = null
            userExplicitlyDismissedSubtitles = false
            lastSubtitleSearchTitle = null
            activeVideoId = null
        }
    }

    fun setSuppressResume(suppress: Boolean) { _shouldSuppressResume.value = suppress }

    fun getVideo(id: String): Video? {
        val base = metadataCache[id] ?: run {
            val fromLatestMovies = _latestMovies.value.find { it.id == id }
            if (fromLatestMovies != null) {
                metadataCache[id] = fromLatestMovies
                fromLatestMovies
            } else {
                val fromLatestTVSeries = _latestTVSeries.value.find { it.id == id }
                if (fromLatestTVSeries != null) {
                    metadataCache[id] = fromLatestTVSeries
                    fromLatestTVSeries
                } else {
                    val fromResults = _resultVideos.value.find { it.id == id } ?: _searchResultsVideos.value.find { it.id == id }
                    if (fromResults != null) {
                        metadataCache[id] = fromResults
                        fromResults
                    } else {
                        var found: Video? = null
                        for ((_, videos) in _categoryVideos.value) {
                            val fromCategory = videos.find { it.id == id }
                            if (fromCategory != null) {
                                metadataCache[id] = fromCategory
                                found = fromCategory
                                break
                            }
                        }
                        if (found != null) {
                            found
                        } else {
                            val fromPakcik = _pakcikRekomenVideos.value.find { it.id == id }
                            if (fromPakcik != null) {
                                metadataCache[id] = fromPakcik
                                fromPakcik
                            } else {
                                val fromMyList = myListVideos.value.find { it.id == id }
                                if (fromMyList != null) {
                                    metadataCache[id] = fromMyList
                                    fromMyList
                                } else {
                                    val fromHistory = recentlyWatchedVideos.value.find { it.id == id }
                                    if (fromHistory != null) {
                                        metadataCache[id] = fromHistory
                                        fromHistory
                                    } else null
                                }
                            }
                        }
                    }
                }
            }
        } ?: return null

        val extra = discoveredAltServers[id] ?: emptyList()
        val combinedServers = if (extra.isNotEmpty()) {
            val existingUrls = base.servers.map { it.url }.toSet()
            base.servers + extra.filter { it.url !in existingUrls }
        } else base.servers

        val targetMeta = com.duta.movie.util.VideoExtractor.parseMovieTitleMeta(base.title, base.date)
        val validServers = combinedServers.filter { s ->
            val lowUrl = s.url.lowercase()
            val isAlt = lowUrl.contains("youtube") || lowUrl.contains("youtu.be") ||
                        lowUrl.contains("dailymotion") || lowUrl.contains("dai.ly") ||
                        lowUrl.contains("bilibili")
            if (isAlt) {
                val isPreVerified = discoveredAltServers[id]?.any { it.url == s.url } == true
                if (isPreVerified) true else {
                    val cleanName = com.duta.movie.util.VideoExtractor.sanitizeServerNameToTitle(s.name)
                    com.duta.movie.util.VideoExtractor.isYouTubeMovieMatch(targetMeta, cleanName)
                }
            } else true
        }
        return base.copy(servers = validServers)
    }
}

