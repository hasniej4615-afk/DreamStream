package com.duta.movie.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.duta.movie.R
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TileMode
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.duta.movie.util.VideoUtils
import com.duta.movie.model.Video
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.focus.focusRestorer
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun Modifier.shimmerEffect(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translation"
    )

    return this.background(
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFF1A1A1A),
                Color(0xFF2C2C2C),
                Color(0xFF1A1A1A),
            ),
            start = Offset.Zero,
            end = Offset(x = translateAnim, y = translateAnim)
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoListScreen(
    viewModel: VideoViewModel = hiltViewModel(),
    windowSizeClass: WindowSizeClass,
    onVideoClick: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val lastCompletedSearchQuery by viewModel.lastCompletedSearchQuery.collectAsStateWithLifecycle()
    val categoryVideos by viewModel.categoryVideos.collectAsStateWithLifecycle()
    val categoryLoading by viewModel.categoryLoading.collectAsStateWithLifecycle()
    val metadataTrigger by viewModel.metadataTrigger.collectAsStateWithLifecycle()
    
    val latestMovies by viewModel.latestMovies.collectAsStateWithLifecycle()
    val latestTVSeries by viewModel.latestTVSeries.collectAsStateWithLifecycle()
    val featuredVideos by viewModel.featuredVideos.collectAsStateWithLifecycle()
    val myList by viewModel.myList.collectAsStateWithLifecycle()
    val recentlyWatchedVideos by viewModel.recentlyWatchedVideos.collectAsStateWithLifecycle()
    val pakcikRekomenVideos by viewModel.pakcikRekomenVideos.collectAsStateWithLifecycle()
    val isPakcikRekomenLoading by viewModel.isPakcikRekomenLoading.collectAsStateWithLifecycle()

    val isExpanded = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
    val isMedium = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Medium
    val isLargeLayout = isExpanded || isMedium
    
    val uiHeroHeightOffset by viewModel.uiHeroHeightOffset.collectAsStateWithLifecycle()
    val uiThumbnailScaleFactor by viewModel.uiThumbnailScaleFactor.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val isRealTV = remember { com.duta.movie.util.DeviceUtils.isTvDevice(context) }
    // Immersive mode is for TVs and rotated wide-screen phones
    val isImmersiveMode = isLargeLayout || isRealTV
    val isLandscapeMobile = isLargeLayout && !isRealTV

    val columns = if (isLargeLayout) {
        if (uiThumbnailScaleFactor > 1.3f) 4 else if (uiThumbnailScaleFactor > 1.15f) 5 else if (uiThumbnailScaleFactor > 0.8f) 6 else 8
    } else 2
    
    val heroHeight = when {
        isRealTV -> (260 + uiHeroHeightOffset).dp 
        isLandscapeMobile -> 200.dp
        else -> 440.dp
    }

    val isSearchActive by viewModel.isSearchActive.collectAsStateWithLifecycle()
    val searchFilter by viewModel.searchFilter.collectAsStateWithLifecycle()
    val searchSort by viewModel.searchSort.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val firstItemFocusRequester = remember { FocusRequester() }
    val firstCategoryFocusRequester = remember { FocusRequester() }
    val clickedItemFocusRequester = remember { FocusRequester() }
    var lastClickedVideoId by rememberSaveable { mutableStateOf<String?>(null) }
    var lastFocusedSearchVideoId by rememberSaveable { mutableStateOf<String?>(null) }
    var previousCompletedSearchQuery by rememberSaveable { mutableStateOf<String?>(null) }
    val searchGridState = rememberLazyGridState()
    val homeLazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var initialHomeFocusRequested by rememberSaveable { mutableStateOf(false) }
    
    LaunchedEffect(isLoading, lastCompletedSearchQuery, selectedCategory, isSearchActive) {
        if (isImmersiveMode) {
            try {
                if (isSearchActive && !isLoading && videos.isNotEmpty()) {
                    delay(300)
                    if (lastClickedVideoId != null && videos.any { it.id == lastClickedVideoId }) {
                        clickedItemFocusRequester.requestFocus()
                    } else if (lastFocusedSearchVideoId != null && videos.any { it.id == lastFocusedSearchVideoId }) {
                        // User is actively browsing or has already focused a video, do not jump back to index 0
                    } else {
                        firstItemFocusRequester.requestFocus()
                    }
                } else if (!isSearchActive && !initialHomeFocusRequested && viewModel.lastFocusedHomeVideoId == null && !isLoading && videos.isNotEmpty()) {
                    delay(300)
                    if (viewModel.lastFocusedHomeVideoId == null) {
                        firstCategoryFocusRequester.requestFocus()
                        initialHomeFocusRequested = true
                    }
                }
            } catch(_: Exception) {}
        }
    }

    LaunchedEffect(lastCompletedSearchQuery) {
        if (lastCompletedSearchQuery != previousCompletedSearchQuery) {
            previousCompletedSearchQuery = lastCompletedSearchQuery
            lastFocusedSearchVideoId = null
            lastClickedVideoId = null
        }
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            lastFocusedSearchVideoId = null
            lastClickedVideoId = null
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.fetchPakcikRekomenVideos(silent = true)
                viewModel.startPakcikRekomenAutoSync()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) { 
        viewModel.fetchPakcikRekomenVideos()
        viewModel.startPakcikRekomenAutoSync()
        if (!isSearchActive && searchQuery.isBlank() && selectedCategory != null) {
            viewModel.selectCategory(null) 
        }
        if (isImmersiveMode && !isSearchActive && (viewModel.pendingRestoreVideoId != null || viewModel.lastFocusedHomeVideoId != null)) {
            val targetRow = viewModel.lastFocusedCategoryRowIndex
            if (targetRow > 0) {
                try {
                    homeLazyListState.scrollToItem(targetRow)
                } catch (_: Exception) {}
            }
        }
    }

    if (isSearchActive) {
        androidx.activity.compose.BackHandler {
            viewModel.setSearchActive(false)
            viewModel.onSearchQueryChange("")
        }
    }

    var showExitDialog by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = !isSearchActive && selectedCategory == null) {
        showExitDialog = true
    }

    if (showExitDialog) {
        val cancelFocusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            delay(300)
            try { cancelFocusRequester.requestFocus() } catch(_: Exception) {}
        }
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = Color.Red)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.exit_app), color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            text = { Text(stringResource(R.string.are_you_sure_you_want_to_close), color = Color.Gray) },
            containerColor = Color(0xFF1A1A1A),
            confirmButton = {
                var isExitFocused by remember { mutableStateOf(false) }
                Button(
                    onClick = { (context as? android.app.Activity)?.finish() },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isExitFocused) Color.White else Color.Red),
                    modifier = Modifier
                        .onFocusChanged { isExitFocused = it.isFocused }
                        .scale(if (isExitFocused) 1.1f else 1f)
                        .focusable()
                ) {
                    Text(stringResource(R.string.exit), color = if (isExitFocused) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                var isCancelFocused by remember { mutableStateOf(false) }
                TextButton(
                    onClick = { showExitDialog = false },
                    modifier = Modifier
                        .focusRequester(cancelFocusRequester)
                        .onFocusChanged { isCancelFocused = it.isFocused }
                        .scale(if (isCancelFocused) 1.1f else 1f)
                        .focusable()
                ) {
                    Text(stringResource(R.string.cancel), color = if (isCancelFocused) Color.White else Color.Gray)
                }
            }
        )
    }
    
    val errorPlaceholder = rememberVectorPainter(Icons.Default.Warning)
    val pullState = rememberPullToRefreshState()
    
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    // OWL'S EYE: The "Sweet Spot" scroll offset.
    // In scrollToItem, a positive offset scrolls the item UP.
    // Since we start with large top contentPadding, offset 0 is already below the hero.
    val comfortOffsetPx = 0 

    var isAnyRowFocused by remember { mutableStateOf(false) }
    val heroAlpha by animateFloatAsState(if (isAnyRowFocused && isImmersiveMode) 0.5f else 1f, label = "hero_dim")

    var focusedVideo by remember { mutableStateOf<Video?>(null) }
    var debouncedHeroVideo by remember { mutableStateOf<Video?>(null) }
    
    var randomMobileHero by remember { mutableStateOf<Video?>(null) }
    
    LaunchedEffect(isRealTV) {
        if (!isRealTV) {
            while (true) {
                // Wait for some data to load initially
                if (randomMobileHero == null) kotlinx.coroutines.delay(1000) else kotlinx.coroutines.delay(10000)
                
                val currentCategoryVideos = viewModel.categoryVideos.value
                val pool = currentCategoryVideos.values.flatten()
                    .distinctBy { it.id }
                    .filter { it.thumbnailUrl.isNotBlank() || it.backdropUrl.isNotBlank() }
                
                if (pool.isNotEmpty() && focusedVideo == null) {
                    randomMobileHero = pool.random()
                }
            }
        }
    }
    
    LaunchedEffect(focusedVideo) {
        if (focusedVideo == null) return@LaunchedEffect
        kotlinx.coroutines.delay(if (isImmersiveMode) 200L else 1000L)
        debouncedHeroVideo = focusedVideo
        if (isImmersiveMode) {
            viewModel.prefetchVideoDetails(focusedVideo!!)
        }
    }

    val featuredHeroVideo = remember(debouncedHeroVideo, featuredVideos, latestMovies, latestTVSeries, randomMobileHero, metadataTrigger) {
        val base = debouncedHeroVideo ?: run {
            if (!isRealTV && randomMobileHero != null) {
                randomMobileHero
            } else if (!isRealTV && latestMovies.isNotEmpty()) {
                latestMovies.first()
            } else {
                featuredVideos.firstOrNull() ?: latestMovies.firstOrNull()
            }
        }
        base?.let { viewModel.applyMetadata(it) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            PullToRefreshBox(
                isRefreshing = isLoading && (videos.isNotEmpty() || latestMovies.isNotEmpty() || latestTVSeries.isNotEmpty()),
                onRefresh = { viewModel.refresh() },
                state = pullState,
                modifier = Modifier.fillMaxSize(),
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = pullState,
                        isRefreshing = isLoading && (videos.isNotEmpty() || latestMovies.isNotEmpty() || latestTVSeries.isNotEmpty()),
                        modifier = Modifier.align(Alignment.TopCenter),
                        containerColor = Color.Black.copy(alpha = 0.7f),
                        color = Color.Red
                    )
                }
            ) {
                val noData = videos.isEmpty() && latestMovies.isEmpty() && latestTVSeries.isEmpty() && featuredVideos.isEmpty()
                
                if (noData && error != null && !isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                            Icon(Icons.Default.CloudOff, null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                            Spacer(Modifier.height(16.dp))
                            Text(text = error!!, color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Spacer(Modifier.height(24.dp))
                            var isRetryFocused by remember { mutableStateOf(false) }
                            Button(
                                onClick = { viewModel.refresh() },
                                colors = ButtonDefaults.buttonColors(containerColor = if (isRetryFocused) Color.White else Color.Red),
                                modifier = Modifier.onFocusChanged { isRetryFocused = it.isFocused }.focusable()
                            ) {
                                Text(stringResource(R.string.retry), color = if (isRetryFocused) Color.Black else Color.White)
                            }
                        }
                    }
                } else if (isSearchActive) {
                    val trimmedQuery = searchQuery.trim()
                    if (trimmedQuery.isEmpty() || trimmedQuery.length < 2) {
                        SearchLandingView(
                            recentSearches = recentSearches,
                            onSearchClick = { tag ->
                                viewModel.onSearchQueryChange(tag)
                                scope.launch { viewModel.searchVideos(tag) }
                            }
                        )
                    } else if (isSearching || (isLoading && videos.isEmpty())) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = if (isImmersiveMode) 115.dp else 140.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(color = Color.Red, strokeWidth = 3.dp)
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = "Searching for \"$trimmedQuery\"...",
                                    color = Color.LightGray,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    } else if (videos.isEmpty() && !isSearching && !isLoading && lastCompletedSearchQuery.equals(trimmedQuery, ignoreCase = true)) {
                        SearchEmptyState(
                            query = searchQuery,
                            onClearClick = { viewModel.clearSearchQuery() }
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = if (isImmersiveMode) 115.dp else 140.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Found ${videos.size} titles",
                                    color = Color.Gray,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${searchFilter.label} • ${searchSort.label}",
                                    color = Color.Red.copy(alpha = 0.85f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columns),
                                state = searchGridState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp, start = 16.dp, end = 16.dp)
                            ) {
                                itemsIndexed(items = videos, key = { _, video -> video.id }) { index, video ->
                                    val itemModifier = Modifier.padding(4.dp).then(
                                        if (video.id == lastClickedVideoId) {
                                            Modifier.focusRequester(clickedItemFocusRequester)
                                        } else if (index == 0) {
                                            Modifier.focusRequester(firstItemFocusRequester)
                                        } else Modifier
                                    )
                                    NetflixThumbnail(
                                        video = video,
                                        modifier = itemModifier,
                                        height = if (isImmersiveMode) (225 * uiThumbnailScaleFactor).dp else 150.dp,
                                        showTitle = !isImmersiveMode,
                                        isTV = isImmersiveMode,
                                        isRealTV = isImmersiveMode,
                                        onFocus = { 
                                            focusedVideo = video
                                            if (isSearchActive) {
                                                lastFocusedSearchVideoId = video.id
                                            }
                                        },
                                        onClick = { 
                                            lastClickedVideoId = video.id
                                            onVideoClick(video.id) 
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else if (selectedCategory != null) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = if (isImmersiveMode) 48.dp else 84.dp, bottom = 80.dp, start = 16.dp, end = 16.dp)
                    ) {
                        itemsIndexed(items = videos) { index, video ->
                            NetflixThumbnail(
                                video = video,
                                modifier = Modifier.padding(4.dp).then(if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier),
                                height = if (isImmersiveMode) (225 * uiThumbnailScaleFactor).dp else 150.dp,
                                showTitle = !isImmersiveMode,
                                isTV = isImmersiveMode,
                                isRealTV = isImmersiveMode,
                                onFocus = { focusedVideo = video },
                                onClick = { onVideoClick(video.id) }
                            )
                        }
                    }
                } else if (isImmersiveMode) {
                    // ── IMMERSIVE CINEMATIC LAYOUT (TV & Landscape Mobile) ─────────────
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (featuredHeroVideo != null) {
                            androidx.compose.animation.Crossfade(
                                targetState = featuredHeroVideo,
                                animationSpec = tween(600),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clipToBounds()
                                    .zIndex(1f), // Draw headliner background below list
                                label = "hero_bg_crossfade"
                            ) { video ->
                                Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = heroAlpha }) {
                                    FeaturedHero(
                                        video = video,
                                        isAddedToMyList = myList.contains(video.id),
                                        onMyListClick = { viewModel.toggleMyList(video.id) },
                                        isLargeLayout = true,
                                        isRealTV = isImmersiveMode,
                                        downFocusRequester = firstCategoryFocusRequester,
                                        onClick = { onVideoClick(video.id) }
                                    )
                                }
                            }
                        }

                        LazyColumn(
                            state = homeLazyListState, 
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = if (isRealTV) 330.dp else 240.dp) // Precise clipping boundary below Watch button
                                .zIndex(10f)
                                .onFocusChanged { isAnyRowFocused = it.hasFocus },
                                contentPadding = PaddingValues(top = 20.dp, bottom = 40.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                if (recentlyWatchedVideos.isNotEmpty()) {
                                    item(key = "section_continue_watching") {
                                        val scope = rememberCoroutineScope()
                                        var isRowFocused by remember { mutableStateOf(false) }
                                        val rowAlpha by animateFloatAsState(if (isRowFocused) 1f else 0.65f)
                                        
                                        Column(modifier = Modifier
                                            .onFocusChanged { isRowFocused = it.hasFocus }
                                        ) {
                                            ListSectionHeader(
                                                "Continue Watching", 
                                                isLargeLayout = true, 
                                                isRealTV = isImmersiveMode,
                                                isFocused = isRowFocused
                                            )
                                             HorizontalVideoRow(
                                                 videos = recentlyWatchedVideos,
                                                 isLoading = false,
                                                 errorPlaceholder = errorPlaceholder,
                                                 onVideoClick = onVideoClick,
                                                 isTV = true,
                                                 isRealTV = isImmersiveMode,
                                                 viewModel = viewModel,
                                                 rowIndex = 0,
                                                 onVideoFocus = { video -> 
                                                     focusedVideo = video 
                                                 },
                                                 thumbnailScale = uiThumbnailScaleFactor,
                                                 firstItemFocusRequester = firstCategoryFocusRequester,
                                                 modifier = Modifier.graphicsLayer { alpha = rowAlpha }
                                             )
                                         }
                                     }
                                 }

                                 if (pakcikRekomenVideos.isNotEmpty()) {
                                     item(key = "section_pakcik_rekomen") {
                                      var isRowFocused by remember { mutableStateOf(false) }
                                      val rowAlpha by animateFloatAsState(if (isRowFocused) 1f else 0.65f)
                                      
                                      Column(modifier = Modifier
                                          .onFocusChanged { isRowFocused = it.hasFocus }
                                      ) {
                                          ListSectionHeader(
                                              stringResource(R.string.pakcik_rekomen), 
                                              isLargeLayout = true, 
                                              isRealTV = isImmersiveMode,
                                              isFocused = isRowFocused
                                          )
                                          HorizontalVideoRow(
                                              videos = pakcikRekomenVideos,
                                              isLoading = isPakcikRekomenLoading,
                                              errorPlaceholder = errorPlaceholder,
                                              onVideoClick = onVideoClick,
                                              isTV = true,
                                              isRealTV = isImmersiveMode,
                                              viewModel = viewModel,
                                              rowIndex = if (recentlyWatchedVideos.isNotEmpty()) 1 else 0,
                                              isPakcikRekomen = true,
                                              onVideoFocus = { video -> 
                                                  focusedVideo = video 
                                              },
                                              thumbnailScale = uiThumbnailScaleFactor,
                                              firstItemFocusRequester = if (recentlyWatchedVideos.isEmpty()) firstCategoryFocusRequester else null,
                                              modifier = Modifier.graphicsLayer { alpha = rowAlpha }
                                          )
                                      }
                                  }
                              }

                              itemsIndexed(categories, key = { _, cat -> cat["path"] ?: cat["name"] ?: "" }) { index, category ->
                                  val name = translateCategoryName(category["name"] ?: "")
                                  val path = category["path"] ?: ""
                                  if (name.isNotEmpty() && path.isNotEmpty()) {
                                      val rowVideos = categoryVideos[path] ?: emptyList()
                                      val isRowLoading = categoryLoading[path] ?: false
                                      LaunchedEffect(path) { 
                                          if (rowVideos.isEmpty()) {
                                              viewModel.fetchVideosForCategoryRow(path)
                                          }
                                      }

                                      if (rowVideos.isNotEmpty() || isRowLoading) {
                                          val scope = rememberCoroutineScope()
                                          var isRowFocused by remember { mutableStateOf(false) }
                                          val rowAlpha by animateFloatAsState(if (isRowFocused) 1f else 0.65f)
                                          val baseRowIndex = (if (recentlyWatchedVideos.isNotEmpty()) 1 else 0) + (if (pakcikRekomenVideos.isNotEmpty()) 1 else 0)
                                          val actualRowIndex = baseRowIndex + index
                                          val isFirstRow = actualRowIndex == 0
                                          
                                          Column(modifier = Modifier
                                              .onFocusChanged { 
                                                  isRowFocused = it.hasFocus 
                                                  if (it.hasFocus) {
                                                      if (rowVideos.isEmpty() && !isRowLoading) {
                                                          viewModel.fetchVideosForCategoryRow(path)
                                                      }
                                                  }
                                              }
                                          ) {
                                             ListSectionHeader(
                                                 name, 
                                                 isLargeLayout = true, 
                                                 isRealTV = isImmersiveMode,
                                                 isFocused = isRowFocused
                                             )
                                             HorizontalVideoRow(
                                                 videos = rowVideos,
                                                 isLoading = isRowLoading || rowVideos.isEmpty(), // Show shimmer if empty
                                                 errorPlaceholder = errorPlaceholder,
                                                 onVideoClick = onVideoClick,
                                                 isTV = true,
                                                 isRealTV = isImmersiveMode,
                                                 viewModel = viewModel,
                                                 rowIndex = actualRowIndex,
                                                 onVideoFocus = { video -> 
                                                     focusedVideo = video 
                                                     val nextIdx = index + 1
                                                     if (nextIdx < categories.size) {
                                                         categories[nextIdx]["path"]?.let { nextPath ->
                                                             viewModel.fetchVideosForCategoryRow(nextPath)
                                                         }
                                                     }
                                                 },
                                                 thumbnailScale = uiThumbnailScaleFactor,
                                                 categoryPath = path,
                                                 firstItemFocusRequester = if (isFirstRow) firstCategoryFocusRequester else null,
                                                 modifier = Modifier.graphicsLayer { alpha = rowAlpha }
                                             )
                                         }
                                     }
                                 }
                             }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 1. FROZEN HEADLINER
                        if (featuredHeroVideo != null) {
                            androidx.compose.animation.Crossfade(
                                targetState = featuredHeroVideo,
                                animationSpec = tween(400),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(heroHeight) // Full height for stability
                                    .clipToBounds()
                                    .zIndex(10f),
                                label = "hero_mobile_crossfade"
                            ) { video ->
                                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                                    FeaturedHero(
                                        video = video,
                                        isAddedToMyList = myList.contains(video.id),
                                        onMyListClick = { viewModel.toggleMyList(video.id) },
                                        isLargeLayout = isLargeLayout,
                                        isRealTV = false,
                                        onClick = { onVideoClick(video.id) }
                                    )
                                }
                            }
                        }

                        // 2. SCROLLABLE CATEGORIES
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = heroHeight - 40.dp) // Start LazyColumn 40dp higher to overlap gradient
                                .zIndex(5f),
                            contentPadding = PaddingValues(top = 40.dp, bottom = 120.dp), // Push first item down so it starts exactly at heroHeight
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (recentlyWatchedVideos.isNotEmpty()) {
                            item { 
                                Spacer(Modifier.height(12.dp))
                                ListSectionHeader("Continue Watching", isLargeLayout = false, isRealTV = false) 
                            }
                                item {
                                    HorizontalVideoRow(
                                        videos = recentlyWatchedVideos,
                                        isLoading = false,
                                        errorPlaceholder = errorPlaceholder,
                                        onVideoClick = onVideoClick,
                                        isTV = isLargeLayout,
                                        isRealTV = false,
                                        viewModel = viewModel,
                                        onVideoFocus = { video -> focusedVideo = video },
                                        thumbnailScale = uiThumbnailScaleFactor
                                    )
                                }
                            }

                            if (pakcikRekomenVideos.isNotEmpty()) {
                                item {
                                    Spacer(Modifier.height(12.dp))
                                    ListSectionHeader(stringResource(R.string.pakcik_rekomen), isLargeLayout = false, isRealTV = false)
                                }
                                item {
                                    HorizontalVideoRow(
                                        videos = pakcikRekomenVideos,
                                        isLoading = isPakcikRekomenLoading,
                                        errorPlaceholder = errorPlaceholder,
                                        onVideoClick = onVideoClick,
                                        isTV = isLargeLayout,
                                        isRealTV = false,
                                        viewModel = viewModel,
                                        isPakcikRekomen = true,
                                        onVideoFocus = { video -> focusedVideo = video },
                                        thumbnailScale = uiThumbnailScaleFactor
                                    )
                                }
                            }

                            if (isLoading && categories.isEmpty()) {
                                items(3) {
                                    ListSectionHeader("Loading...", isLargeLayout = false, isRealTV = false)
                                    Box(Modifier.fillMaxWidth().height(230.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp)).shimmerEffect())
                                }
                            }

                            itemsIndexed(categories, key = { _, cat -> cat["path"] ?: cat["name"] ?: "" }) { _, category ->
                                val name = translateCategoryName(category["name"] ?: "")
                                val path = category["path"] ?: ""
                                if (name.isNotEmpty() && path.isNotEmpty()) {
                                    val rowVideos = categoryVideos[path] ?: emptyList()
                                    val isRowLoading = categoryLoading[path] ?: false
                                    
                                    LaunchedEffect(path) { if (rowVideos.isEmpty()) viewModel.fetchVideosForCategoryRow(path) }

                                    if (rowVideos.isNotEmpty() || isRowLoading) {
                                        ListSectionHeader(name, isLargeLayout = false, isRealTV = false)
                                        HorizontalVideoRow(
                                            videos = rowVideos,
                                            isLoading = isRowLoading,
                                            errorPlaceholder = errorPlaceholder,
                                            onVideoClick = onVideoClick,
                                            isTV = isLargeLayout,
                                            isRealTV = false,
                                            viewModel = viewModel,
                                            onVideoFocus = { video -> focusedVideo = video },
                                            thumbnailScale = uiThumbnailScaleFactor,
                                            categoryPath = path
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (!isImmersiveMode || isSearchActive) {
                    Box(modifier = Modifier.zIndex(100f)) {
                        NetflixTopBar(
                            isSearchActive = isSearchActive,
                            searchQuery = searchQuery,
                            onSearchQueryChange = { viewModel.onSearchQueryChange(it) },
                            onSearchAction = { scope.launch { viewModel.searchVideos(searchQuery) } },
                            onSearchActiveChange = { viewModel.setSearchActive(it) },
                            onClearSearch = { viewModel.clearSearchQuery() },
                            searchFilter = searchFilter,
                            onSearchFilterChange = { viewModel.setSearchFilter(it) },
                            searchSort = searchSort,
                            onSearchSortChange = { viewModel.setSearchSort(it) },
                            onSettingsClick = onSettingsClick
                        )
                    }
                }
            }
        }
        
        if (isLoading && (videos.isNotEmpty() || latestMovies.isNotEmpty() || latestTVSeries.isNotEmpty())) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.TopCenter).zIndex(100f),
                color = Color.Red,
                trackColor = Color.Transparent
            )
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun HorizontalVideoRow(
    videos: List<Video>,
    isLoading: Boolean,
    errorPlaceholder: Painter,
    onVideoClick: (String) -> Unit,
    isTV: Boolean = false,
    isRealTV: Boolean = false,
    viewModel: VideoViewModel? = null,
    rowIndex: Int = 0,
    onVideoFocus: (Video) -> Unit = {},
    thumbnailScale: Float = 1.0f,
    categoryPath: String? = null,
    firstItemFocusRequester: FocusRequester? = null,
    isPakcikRekomen: Boolean = false,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var hasFocus by remember { mutableStateOf(false) }
    val rowFirstItemFocusRequester = remember { FocusRequester() }
    var shimmerHadFocus by remember { mutableStateOf(false) }

    val shouldLoadMore by remember(videos, isLoading) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItemsCount = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            !isLoading && totalItemsCount > 0 && lastVisibleItemIndex >= totalItemsCount - 5
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && categoryPath != null && viewModel != null) {
            viewModel.loadMoreForCategoryRow(categoryPath)
        }
    }

    LaunchedEffect(videos.isNotEmpty()) {
        if (videos.isNotEmpty() && shimmerHadFocus) {
            shimmerHadFocus = false
            delay(50)
            try {
                rowFirstItemFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = if (isRealTV) 48.dp else 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .focusRestorer { rowFirstItemFocusRequester }
            .focusGroup()
            .fillMaxWidth()
            .height(((if (isRealTV) 240 else if (isTV) 260 else 340) * thumbnailScale).dp)
            .onFocusChanged { hasFocus = it.hasFocus }
    ) {
        if (isLoading && videos.isEmpty()) {
            items(5) {
                var isShimmerFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .width((if (isRealTV) 140 * thumbnailScale else if (isTV) 130 * thumbnailScale else 165f).dp)
                        .height((if (isRealTV) 210 * thumbnailScale else if (isTV) 195 * thumbnailScale else 245f).dp)
                        .onFocusChanged { 
                            isShimmerFocused = it.isFocused 
                            if (it.isFocused) shimmerHadFocus = true
                        }
                        .then(
                            if (isShimmerFocused && (isRealTV || isTV))
                                Modifier.border(2.dp, Color.White, RoundedCornerShape(12.dp)).scale(1.05f)
                            else Modifier
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .shimmerEffect()
                        .focusable()
                )
            }
        } else {
            itemsIndexed(videos, key = { _, video -> video.id }) { index, video ->
                val progressFlow = remember(video.id) { viewModel?.getVideoProgress(video.id) ?: kotlinx.coroutines.flow.flowOf(0L) }
                val durationFlow = remember(video.id) { viewModel?.getVideoDuration(video.id) ?: kotlinx.coroutines.flow.flowOf(0L) }
                val progress by progressFlow.collectAsState(0L)
                val duration by durationFlow.collectAsState(0L)
                
                var itemFocused by remember { mutableStateOf(false) }

                val isTargetRestorationItem = isRealTV && viewModel != null && video.id == viewModel.pendingRestoreVideoId
                val itemFocusRequester = remember { FocusRequester() }

                LaunchedEffect(isTargetRestorationItem) {
                    if (isTargetRestorationItem) {
                        try {
                            listState.scrollToItem(index)
                        } catch (_: Exception) {}
                        delay(100)
                        try {
                            itemFocusRequester.requestFocus()
                        } catch (_: Exception) {}
                        viewModel.pendingRestoreVideoId = null
                    }
                }

                val effectiveFocusRequester = when {
                    isTargetRestorationItem -> itemFocusRequester
                    index == 0 -> firstItemFocusRequester ?: rowFirstItemFocusRequester
                    else -> null
                }

                NetflixThumbnail(
                    video = video,
                    width = if (isRealTV) (140 * thumbnailScale).dp else if (isTV) (130 * thumbnailScale).dp else 165.dp, 
                    height = if (isRealTV) (210 * thumbnailScale).dp else if (isTV) (195 * thumbnailScale).dp else 245.dp,
                    showTitle = true, // Always show titles on mobile as per photo reference
                    progress = progress,
                    duration = duration,
                    errorPlaceholder = errorPlaceholder,
                    isTV = isTV,
                    isRealTV = isRealTV,
                    isPakcikRekomen = isPakcikRekomen,
                    onFocus = { 
                        itemFocused = true
                        viewModel?.lastFocusedHomeVideoId = video.id
                        viewModel?.lastFocusedCategoryRowIndex = rowIndex
                        onVideoFocus(video)
                        if (isRealTV) {
                            scope.launch { listState.animateScrollToItem(index) }
                        }
                    },
                    modifier = Modifier
                        .zIndex(if (itemFocused) 10f else 1f)
                        .onFocusChanged { itemFocused = it.isFocused }
                        .then(if (effectiveFocusRequester != null) Modifier.focusRequester(effectiveFocusRequester) else Modifier)
                        .focusProperties {
                            if (rowIndex == 0 && isRealTV) {
                                up = FocusRequester.Cancel
                            }
                        }
                ) {
                    viewModel?.lastFocusedHomeVideoId = video.id
                    viewModel?.lastFocusedCategoryRowIndex = rowIndex
                    viewModel?.pendingRestoreVideoId = video.id
                    onVideoClick(video.id)
                }
            }
            if (isLoading) {
                item {
                    Box(Modifier.width(100.dp).height(150.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.Red, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun NetflixThumbnail(
    video: Video,
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp? = null,
    height: androidx.compose.ui.unit.Dp = 180.dp,
    showTitle: Boolean = false,
    errorPlaceholder: Painter? = null,
    progress: Long = 0L,
    duration: Long = 0L,
    isTV: Boolean = false,
    isRealTV: Boolean = false,
    isPakcikRekomen: Boolean = false,
    onFocus: () -> Unit = {},
    onClick: () -> Unit
) {
    val baseModifier = if (width != null) modifier.width(width) else modifier
    var isFocused by remember { mutableStateOf(false) }
    
    // TACTILE TAP ANIMATION
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
       val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.96f
            isFocused && isRealTV -> 1.25f // Elite scale for TV
            isFocused -> 1.12f // Tactile scale for Mobile
            else -> 1f
        }, 
        animationSpec = tween(if (isPressed) 100 else 250, easing = LinearOutSlowInEasing),
        label = "scale"
    )
    
    Column(
        modifier = baseModifier
            .onFocusChanged { 
                isFocused = it.isFocused 
                if (it.isFocused) onFocus()
            }
            .graphicsLayer { 
                scaleX = scale
                scaleY = scale
                clip = false 
            }
            .clip(RoundedCornerShape(if (isRealTV) 16.dp else 12.dp))
            .zIndex(if (isFocused) 50f else 1f)
            .clickable(
                interactionSource = interactionSource,
                indication = null, // Custom scale is our indication
                onClick = onClick
            )
    ) {
        Card(
            shape = RoundedCornerShape(if (isRealTV) 16.dp else 12.dp), // Premium Matched
            border = BorderStroke(
                width = if (isFocused && isRealTV) 3.5.dp else if (isFocused) 2.5.dp else 1.dp, 
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.1f)
            ),
            modifier = Modifier
                .height(height)
                .graphicsLayer(clip = false)
                .shadow(
                    elevation = if (isFocused && isRealTV) 40.dp else if (isFocused) 20.dp else 0.dp,
                    shape = RoundedCornerShape(if (isRealTV) 16.dp else 12.dp),
                    ambientColor = if (isFocused) Color.White else Color.Transparent,
                    spotColor = if (isFocused) Color.White else Color.Transparent
                ),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val context = LocalContext.current
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(VideoUtils.getOptimizedImage(video.thumbnailUrl, isTV, context))
                        .size(if (isTV) coil.size.Size(500, 750) else coil.size.Size(240, 360))
                        .precision(coil.size.Precision.INEXACT)
                        .crossfade(150)
                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .build(),
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    placeholder = errorPlaceholder,
                    error = errorPlaceholder
                )

                if (isRealTV) {
                    // PREMIUM GLASS REFLECTION (OnStream Detail - TV ONLY)
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.35f)
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.White.copy(alpha = 0.15f),
                                1.0f to Color.Transparent
                            )
                        )
                    )
                }
                
                // Fallback title for TV if image is missing or bad
                if (isTV && video.thumbnailUrl.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = video.title,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                if (duration > 0 && progress > 0) {
                    val percent = (progress.toFloat() / duration).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .height(4.dp)
                            .align(Alignment.BottomStart)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(percent)
                                .fillMaxHeight()
                                .background(Color.Red, RoundedCornerShape(2.dp))
                        )
                    }
                }

                if (isPakcikRekomen) {
                    val isNarrowCard = (width != null && width < 120.dp) || (isRealTV && (width ?: 165.dp) < 120.dp)
                    Surface(
                        color = Color.Red,
                        shape = RoundedCornerShape(bottomEnd = 6.dp),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Text(
                            text = if (isNarrowCard) "Rekomen" else stringResource(R.string.pakcik_rekomen),
                            color = Color.White,
                            fontSize = if (isNarrowCard) 9.sp else 11.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(
                                horizontal = if (isNarrowCard) 4.dp else 7.dp, 
                                vertical = if (isNarrowCard) 2.dp else 3.dp
                            )
                        )
                    }
                } else if (video.quality.isNotEmpty()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.94f),
                        shape = RoundedCornerShape(bottomEnd = 4.dp),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Text(
                            text = video.quality.uppercase(),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }

                if (isPakcikRekomen) {
                    val recCount = (video.views.toIntOrNull() ?: 1).coerceAtLeast(1)
                    val isNarrowCard = (width != null && width < 120.dp) || (isRealTV && (width ?: 165.dp) < 120.dp)
                    Surface(
                        color = Color.Black.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(bottomStart = 4.dp),
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(
                                horizontal = if (isNarrowCard) 4.dp else 6.dp, 
                                vertical = if (isNarrowCard) 2.dp else 3.dp
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.ThumbUp,
                                contentDescription = null,
                                tint = Color.Red,
                                modifier = Modifier.size(if (isNarrowCard) 9.dp else 11.dp)
                            )
                            Spacer(modifier = Modifier.width(if (isNarrowCard) 2.dp else 4.dp))
                            Text(
                                text = "$recCount",
                                color = Color.White,
                                fontSize = if (isNarrowCard) 9.sp else 11.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                } else {
                    val cleanRating = remember(video.views) {
                        val num = Regex("""\d+(?:\.\d+)?""").find(video.views)?.value
                        if (num != null && (num.toDoubleOrNull() ?: 0.0) > 0.0) num else ""
                    }
                    if (cleanRating.isNotEmpty()) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(bottomStart = 4.dp),
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFFFC107),
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = cleanRating,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }
            }
        }
        
        if (showTitle) {
            val displayTitle = remember(video.title) {
                if (isRealTV) { // Gated to TV only
                    video.title.replace(Regex("\\s*\\(\\d{4}\\)"), "")
                               .replace(Regex("\\s*\\[\\d{4}\\]"), "")
                               .replace(Regex("\\s*\\d{4}$"), "")
                               .trim()
                } else video.title
            }
            Text(
                text = displayTitle,
                color = Color.White,
                fontSize = 13.sp, // High visibility
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                lineHeight = 18.sp,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp, bottom = 12.dp)
            )
        }
    }
}

@Composable
fun FeaturedHero(
    video: Video,
    isAddedToMyList: Boolean,
    onMyListClick: () -> Unit,
    isLargeLayout: Boolean = false,
    isRealTV: Boolean = false,
    downFocusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    val heroImage = video.backdropUrl.ifEmpty { video.thumbnailUrl }
    
    Box(modifier = Modifier.fillMaxSize().then(if (!isRealTV) Modifier.clickable(onClick = onClick) else Modifier)) {
        val context = LocalContext.current
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(VideoUtils.getOptimizedBackdrop(heroImage, isRealTV, context))
                .crossfade(500)
                .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            placeholder = rememberVectorPainter(Icons.Default.Movie),
            error = rememberVectorPainter(Icons.Default.Warning)
        )
        Box(modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0.0f to Color.Black.copy(alpha = 0.5f), // Top Vignette for Icons
                0.15f to Color.Transparent,
                0.4f to Color.Transparent,
                0.75f to Color.Black.copy(alpha = 0.92f),
                1.0f to Color.Black
            )
        ))
        if (isRealTV) {
            Box(modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0.0f to Color.Black,
                    0.35f to Color.Black.copy(alpha = 0.98f),
                    0.55f to Color.Black.copy(alpha = 0.4f),
                    1.0f to Color.Transparent
                )
            )) }
            
            
        
        Row(
            modifier = Modifier
                .align(if (isLargeLayout) Alignment.TopStart else Alignment.BottomStart)
                .padding(
                    start = if (isRealTV) 48.dp else 24.dp, 
                    top = if (isRealTV) 120.dp else if (isLargeLayout) 32.dp else 0.dp,
                    bottom = if (isLargeLayout) 0.dp else 24.dp, 
                    end = 24.dp
                )
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                // OWL'S EYE: TV Title Cleaning (Strip year locally for Dashboard)
                val displayTitle = remember(video.title) {
                    if (isRealTV) {
                        video.title.replace(Regex("\\s*\\(\\d{4}\\)"), "")
                                   .replace(Regex("\\s*\\[\\d{4}\\]"), "")
                                   .replace(Regex("\\s*\\d{4}$"), "")
                                   .trim()
                    } else video.title
                }

                Text(
                    text = displayTitle, 
                    color = Color.White, 
                    style = if(isRealTV) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineMedium, 
                    fontWeight = FontWeight.Black,
                    lineHeight = if(isRealTV) 34.sp else 34.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                // METADATA BADGES (Gated cluster style for TV)
                Row(
                    modifier = Modifier.padding(vertical = if (isRealTV) 8.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (video.views.isNotEmpty()) { // Rating Badge
                        Surface(
                            color = Color(0xFFF5C518).copy(alpha = 0.15f), // Gold Tint
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, Color(0xFFF5C518))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
                                Icon(Icons.Default.Star, null, tint = Color(0xFFF5C518), modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(text = video.views, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                    if (video.date.isNotEmpty()) {
                        Text(
                            text = video.date,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (video.quality.isNotEmpty()) {
                        Surface(
                            color = Color.Red,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = video.quality.uppercase(),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (video.duration.isNotEmpty()) {
                        Text(
                            text = video.duration,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (video.description.isNotEmpty()) {
                    Text(
                        text = video.description, 
                        color = Color.White.copy(alpha = 0.75f), 
                        maxLines = if (isRealTV) 3 else if (isLargeLayout) 3 else 2, 
                        fontSize = if (isRealTV) 14.sp else 13.sp, 
                        lineHeight = if (isRealTV) 20.sp else 18.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = if (isRealTV) 12.dp else 16.dp)
                    )
                }
                if (!isRealTV) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        var isPlayFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = onClick, 
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPlayFocused) Color.Red else Color.White, 
                                contentColor = if (isPlayFocused) Color.White else Color.Black
                            ), 
                            shape = RoundedCornerShape(10.dp), 
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = if (isRealTV) 10.dp else 10.dp),
                            modifier = Modifier
                                .onFocusChanged { isPlayFocused = it.isFocused }
                                .border(if (isPlayFocused) BorderStroke(3.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(10.dp))
                                .scale(if (isPlayFocused) 1.15f else 1f)
                                .then(if (downFocusRequester != null) Modifier.focusProperties { down = downFocusRequester } else Modifier)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.watch_now), fontWeight = FontWeight.Black, fontSize = 16.sp)
                        }
                        var isMyListFocused by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = onMyListClick,
                            modifier = Modifier
                                .onFocusChanged { isMyListFocused = it.isFocused }
                                .background(if (isMyListFocused) Color.White else Color.White.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                .border(if (isMyListFocused) BorderStroke(3.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(10.dp))
                                .size(if (isRealTV) 46.dp else 46.dp)
                                .scale(if (isMyListFocused) 1.15f else 1f)
                                .then(if (downFocusRequester != null) Modifier.focusProperties { down = downFocusRequester } else Modifier)
                        ) {
                            Icon(
                                if (isAddedToMyList) Icons.Default.Check else Icons.Default.Add, 
                                contentDescription = stringResource(R.string.add_to_list), 
                                tint = if (isMyListFocused) Color.Black else if (isAddedToMyList) Color.Red else Color.White
                            )
                        }
                    }
                }
            }
            
            if (isRealTV) {
                 val errorPlaceholder = rememberVectorPainter(Icons.Default.Warning)
                 val context = LocalContext.current
                  AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(VideoUtils.getOptimizedImage(video.thumbnailUrl, isRealTV, context))
                        .size(coil.size.Size(300, 450))
                        .precision(coil.size.Precision.INEXACT)
                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .width(100.dp) // Compact size for TV Dashboard
                        .height(150.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .shadow(
                            elevation = 30.dp,
                            shape = RoundedCornerShape(12.dp),
                            ambientColor = Color.White.copy(alpha = 0.2f),
                            spotColor = Color.White.copy(alpha = 0.2f)
                        ),
                    contentScale = ContentScale.Crop,
                    placeholder = errorPlaceholder,
                    error = errorPlaceholder
                )
            }
        }
    }
}

@Composable
fun NetflixTopBar(
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchAction: () -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onClearSearch: () -> Unit,
    searchFilter: SearchFilter,
    onSearchFilterChange: (SearchFilter) -> Unit,
    searchSort: SearchSort,
    onSearchSortChange: (SearchSort) -> Unit,
    onSettingsClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSearchActive) Color.Black else Color.Transparent
            )
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    ) {
        if (!isSearchActive) {
            // GLASS BLUR LAYER (Netflix Style)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .blur(30.dp) // Deep premium blur
                    .background(Color.Black.copy(alpha = 0.15f))
            )
        }
        if (isSearchActive) {
            val searchFocusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { try { searchFocusRequester.requestFocus() } catch(_: Exception) {} }
            val keyboardController = LocalSoftwareKeyboardController.current
            val focusManager = LocalFocusManager.current
            val performSearch = {
                keyboardController?.hide()
                focusManager.clearFocus()
                onSearchAction()
            }
            
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    var isBackFocused by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { onSearchActiveChange(false) },
                        modifier = Modifier
                            .onFocusChanged { isBackFocused = it.isFocused }
                            .background(if (isBackFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent, CircleShape)
                            .border(if (isBackFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                    TextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier.weight(1f).focusRequester(searchFocusRequester).focusable(),
                        placeholder = { Text(stringResource(R.string.search_movies_actors), color = Color.Gray) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Red,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = Color.Red,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { performSearch() })
                    )
                    if (searchQuery.isNotEmpty()) {
                        var isClearFocused by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = onClearSearch,
                            modifier = Modifier
                                .onFocusChanged { isClearFocused = it.isFocused }
                                .background(if (isClearFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent, CircleShape)
                                .border(if (isClearFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), CircleShape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search", tint = Color.LightGray)
                        }
                    }
                    var isSearchActionFocused by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = performSearch,
                        modifier = Modifier
                            .onFocusChanged { isSearchActionFocused = it.isFocused }
                            .background(if (isSearchActionFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent, CircleShape)
                            .border(if (isSearchActionFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), CircleShape)
                    ) {
                        Icon(Icons.Default.Search, null, tint = Color.White)
                    }
                }

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SearchFilter.values().forEach { filter ->
                        SearchFilterChip(
                            label = filter.label,
                            isSelected = searchFilter == filter,
                            onClick = { onSearchFilterChange(filter) }
                        )
                    }

                    Spacer(Modifier.width(4.dp))
                    Box(modifier = Modifier.height(16.dp).width(1.dp).background(Color.DarkGray))
                    Spacer(Modifier.width(4.dp))

                    SearchSort.values().forEach { sort ->
                        SearchFilterChip(
                            label = sort.label,
                            isSelected = searchSort == sort,
                            onClick = { onSearchSortChange(sort) },
                            isSortChip = true
                        )
                    }
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.dmstream), color = Color.Red, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, letterSpacing = (-0.5).sp)
                Spacer(Modifier.weight(1f))
                var isSearchFocused by remember { mutableStateOf(false) }
                IconButton(
                    onClick = { onSearchActiveChange(true) },
                    modifier = Modifier
                        .onFocusChanged { isSearchFocused = it.isFocused }
                        .background(if (isSearchFocused) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f), CircleShape)
                        .border(if (isSearchFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), CircleShape)
                ) { Icon(Icons.Default.Search, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
                Spacer(Modifier.width(8.dp))
                var isSettingsFocused by remember { mutableStateOf(false) }
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .onFocusChanged { isSettingsFocused = it.isFocused }
                        .background(if (isSettingsFocused) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f), CircleShape)
                        .border(if (isSettingsFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), CircleShape)
                ) { Icon(Icons.Default.Settings, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
            }
        }
    }
}

@Composable
fun ListSectionHeader(title: String, isLargeLayout: Boolean = false, isRealTV: Boolean = false, isFocused: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (isRealTV) 48.dp else 16.dp, 
                top = if (isRealTV) 16.dp else if (isLargeLayout) 16.dp else 16.dp, 
                bottom = if (isRealTV) 4.dp else if (isLargeLayout) 8.dp else 8.dp,
                end = 16.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val contentAlpha by animateFloatAsState(if (isFocused || !isRealTV) 1f else 0.6f, label = "header_alpha")
        
        // Red indicator dash (4dp x 22dp matched to reference)
        Box(
            modifier = Modifier
                .width(if (isRealTV) 6.dp else 4.dp)
                .height(if (isRealTV) 22.dp else 22.dp)
                .graphicsLayer { alpha = contentAlpha }
                .background(Color.Red, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(if (isRealTV) 12.dp else 12.dp))

        Text(
            text = title.uppercase(), 
            color = Color.White.copy(alpha = contentAlpha), 
            style = if (isRealTV) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium, 
            fontWeight = FontWeight.Black, 
            letterSpacing = 1.sp // Professional spacing for uppercase
        )
    }
}


@Composable
fun translateCategoryName(name: String): String {
    return when(name) {
        "Newly Updated" -> stringResource(R.string.newly_updated)
        "Movies" -> stringResource(R.string.movies)
        "TV Series" -> stringResource(R.string.tv_series)
        "Box-Office" -> stringResource(R.string.box_office)
        "Action" -> stringResource(R.string.action)
        "Anime" -> stringResource(R.string.anime)
        "Animasi" -> stringResource(R.string.anime)
        "Apple TV+" -> stringResource(R.string.apple_tv)
        "Disney+" -> stringResource(R.string.disney_plus)
        "HBO" -> stringResource(R.string.hbo)
        "Comedy" -> stringResource(R.string.comedy)
        "Drama" -> stringResource(R.string.drama)
        "Horror" -> stringResource(R.string.horror)
        "Romance" -> stringResource(R.string.romance)
        "Sci-Fi" -> stringResource(R.string.sci_fi)
        "Science Fiction" -> stringResource(R.string.sci_fi)
        "Thriller" -> stringResource(R.string.thriller)
        "Adventure" -> stringResource(R.string.adventure)
        "Crime" -> stringResource(R.string.crime)
        "Fantasy" -> stringResource(R.string.fantasy)
        "Mystery" -> stringResource(R.string.mystery)
        "Indonesia" -> stringResource(R.string.indonesia)
        "Korea" -> stringResource(R.string.korea)
        "Malaysia" -> stringResource(R.string.malaysia)
        "Netflix" -> stringResource(R.string.netflix)
        "P.Ramlee" -> stringResource(R.string.p_ramlee)
        "Thailand" -> stringResource(R.string.thailand)
        "Viet Nam" -> stringResource(R.string.vietnam)
        "Vietnam" -> stringResource(R.string.vietnam)
        "Japan" -> stringResource(R.string.japan)
        "China" -> stringResource(R.string.china)
        "India" -> stringResource(R.string.india)
        "USA" -> stringResource(R.string.usa)
        "United Kingdom" -> stringResource(R.string.united_kingdom)
        else -> name
    }
}

@Composable
fun SearchFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    isSortChip: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    val baseBgColor = when {
        isSelected && isSortChip -> Color(0xFF383838)
        isSelected -> Color.Red
        else -> Color.White.copy(alpha = 0.08f)
    }
    val contentColor = when {
        isSelected -> Color.White
        else -> Color.LightGray
    }

    Box(
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .clip(RoundedCornerShape(16.dp))
            .background(if (isFocused) Color.White.copy(alpha = 0.25f) else baseBgColor)
            .border(
                BorderStroke(
                    width = if (isFocused) 2.dp else if (isSelected) 1.dp else 0.dp,
                    color = if (isFocused) Color.White else if (isSelected && isSortChip) Color.Gray else Color.Transparent
                ),
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isFocused) Color.White else contentColor,
            fontSize = 12.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchLandingView(
    recentSearches: List<String>,
    onSearchClick: (String) -> Unit
) {
    val curatedSuggestions = remember {
        listOf("P. Ramlee", "Bujang Lapok", "Pendekar Bujang Lapok", "Gerak Khas", "Polis Evo", "Spider-Man", "Marvel", "Anime", "KL Gangster", "Hantu Kak Limah")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 135.dp, start = 20.dp, end = 20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start
    ) {
        if (recentSearches.isNotEmpty()) {
            Text(
                text = "Recent Searches",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                recentSearches.forEach { query ->
                    SearchSuggestionChip(text = query, onClick = { onSearchClick(query) }, isRecent = true)
                }
            }
        }

        Text(
            text = "Popular Suggestions",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            curatedSuggestions.forEach { tag ->
                SearchSuggestionChip(text = tag, onClick = { onSearchClick(tag) }, isRecent = false)
            }
        }
    }
}

@Composable
fun SearchSuggestionChip(
    text: String,
    onClick: () -> Unit,
    isRecent: Boolean
) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .clip(RoundedCornerShape(20.dp))
            .border(
                BorderStroke(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.15f)
                ),
                RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .focusable(),
        color = if (isFocused) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isRecent) Icons.Default.History else Icons.AutoMirrored.Filled.TrendingUp,
                contentDescription = null,
                tint = if (isFocused) Color.White else Color.LightGray,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                color = if (isFocused) Color.White else Color.LightGray,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun SearchEmptyState(
    query: String,
    onClearClick: () -> Unit
) {
    var isClearFocused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.SearchOff,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "No results found for \"$query\"",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Try searching with different keywords, check the spelling, or switch the filter to 'All'.",
            color = Color.Gray,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onClearClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isClearFocused) Color.White else Color.Red
            ),
            modifier = Modifier
                .onFocusChanged { isClearFocused = it.isFocused }
                .focusable()
        ) {
            Text(
                text = "Clear Search",
                color = if (isClearFocused) Color.Black else Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

