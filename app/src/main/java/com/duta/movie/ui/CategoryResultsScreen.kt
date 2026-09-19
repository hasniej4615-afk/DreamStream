package com.duta.movie.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import com.duta.movie.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duta.movie.util.VideoUtils
import com.duta.movie.model.Video
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass

import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Mic
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryResultsScreen(
    categoryPath: String,
    categoryName: String,
    onBackClick: () -> Unit,
    onVideoClick: (String) -> Unit,
    viewModel: VideoViewModel = hiltViewModel(),
    windowSizeClass: WindowSizeClass,
    customVideos: List<Video>? = null
) {
    val videos = customVideos ?: viewModel.videos.collectAsStateWithLifecycle().value
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val uiThumbnailScaleFactor by viewModel.uiThumbnailScaleFactor.collectAsStateWithLifecycle()
    
    val isEndReached by viewModel.isEndReached.collectAsStateWithLifecycle()
    
    val isExpanded = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
    val isMedium = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Medium
    
    val columns = when {
        isExpanded -> if (uiThumbnailScaleFactor > 1.3f) 4 else if (uiThumbnailScaleFactor > 1.15f) 5 else if (uiThumbnailScaleFactor > 0.8f) 6 else 8
        isMedium -> if (uiThumbnailScaleFactor > 1.2f) 3 else 4
        else -> 2
    }

    // TV Optimization: Safe area padding to prevent content from being cut off on old TVs (overscan)
    val screenPadding = if (isExpanded) 48.dp else 0.dp

    val isSearchActive by viewModel.isSearchActive.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    
    // TV Optimization: Focus requesters
    val firstItemFocusRequester = remember { FocusRequester() }
    val clickedItemFocusRequester = remember { FocusRequester() }
    var lastClickedVideoId by rememberSaveable { mutableStateOf<String?>(null) }

    var focusedVideo by remember { mutableStateOf<Video?>(null) }
    LaunchedEffect(focusedVideo) {
        focusedVideo?.let { viewModel.prefetchVideoDetails(it) }
    }

    var localSearchQuery by rememberSaveable(isSearchActive) { mutableStateOf(if (isSearchActive) searchQuery else "") }

    LaunchedEffect(isLoading, videos) {
        if (isExpanded && !isLoading && videos.isNotEmpty()) {
            delay(1000)
            try {
                if (lastClickedVideoId != null && videos.any { it.id == lastClickedVideoId }) {
                    clickedItemFocusRequester.requestFocus()
                } else {
                    firstItemFocusRequester.requestFocus()
                }
            } catch(_: Exception) {}
        }
    }

    // Detection for infinite scroll - More aggressive trigger for "As many as possible"
    val shouldLoadMore by remember(videos, isLoading, isEndReached, customVideos) {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val totalItemsCount = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            // Trigger load more when 15 items away from end instead of 5
            (customVideos == null && videos.isNotEmpty() && !isLoading && !isEndReached && totalItemsCount > 0 && lastVisibleItemIndex >= totalItemsCount - 15)
        }
    }

    LaunchedEffect(categoryPath, customVideos) {
        if (!isSearchActive && customVideos == null) {
            viewModel.selectCategory(categoryPath)
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.loadMoreVideos()
        }
    }

    if (isSearchActive) {
        androidx.activity.compose.BackHandler {
            viewModel.setSearchActive(false)
            viewModel.onSearchQueryChange("")
            viewModel.selectCategory(categoryPath)
        }
    }
    
    val errorPlaceholder = rememberVectorPainter(Icons.Default.Warning)
    val pullState = rememberPullToRefreshState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    if (!isSearchActive) {
                        Text(
                            categoryName, 
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        val searchFocusRequester = remember { FocusRequester() }
                        LaunchedEffect(Unit) { searchFocusRequester.requestFocus() }
                        TextField(
                            value = localSearchQuery,
                            onValueChange = { localSearchQuery = it },
                            modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester).focusable(),
                            placeholder = { Text("Search in $categoryName...", color = Color.Gray) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Red,
                                unfocusedIndicatorColor = Color.Gray,
                                cursorColor = Color.Red,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { 
                                viewModel.onSearchQueryChange(localSearchQuery)
                                scope.launch { viewModel.searchVideos(localSearchQuery, categoryPath = categoryPath) }
                            }),
                            trailingIcon = {
                                val speechLauncher = rememberLauncherForActivityResult(
                                    contract = ActivityResultContracts.StartActivityForResult(),
                                    onResult = { result ->
                                        if (result.resultCode == android.app.Activity.RESULT_OK) {
                                            val spokenText = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                                            if (!spokenText.isNullOrEmpty()) {
                                                localSearchQuery = spokenText
                                                viewModel.onSearchQueryChange(spokenText)
                                                scope.launch { viewModel.searchVideos(spokenText, categoryPath = categoryPath) }
                                            }
                                        }
                                    }
                                )
                                var isMicFocused by remember { mutableStateOf(false) }
                                IconButton(
                                    onClick = { 
                                        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        }
                                        try { speechLauncher.launch(intent) } catch (_: Exception) {}
                                    },
                                    modifier = Modifier
                                        .onFocusChanged { isMicFocused = it.isFocused }
                                        .background(if (isMicFocused) Color.White.copy(alpha = 0.15f) else Color.Transparent, CircleShape)
                                        .border(if (isMicFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), CircleShape)
                                ) { Icon(Icons.Default.Mic, contentDescription = stringResource(R.string.voice_search), tint = Color.White) }
                            }
                        )
                    }
                },
                navigationIcon = {
                    var isBackFocused by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = {
                            if (isSearchActive) {
                                viewModel.setSearchActive(false)
                                viewModel.onSearchQueryChange("")
                                viewModel.selectCategory(categoryPath)
                            } else {
                                onBackClick()
                            }
                        },
                        modifier = Modifier
                            .onFocusChanged { isBackFocused = it.isFocused }
                            .border(if (isBackFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), CircleShape)
                            .focusable()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = Color.White)
                    }
                },
                actions = {
                    if (!isSearchActive && customVideos == null) {
                        var isSearchFocused by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { viewModel.setSearchActive(true) },
                            modifier = Modifier
                                .onFocusChanged { isSearchFocused = it.isFocused }
                                .border(if (isSearchFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), CircleShape)
                                .focusable()
                        ) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search), tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isLoading && videos.isNotEmpty(),
            onRefresh = { 
                if (isSearchActive && localSearchQuery.isNotEmpty()) {
                    scope.launch { viewModel.searchVideos(localSearchQuery, categoryPath = categoryPath) }
                } else {
                    viewModel.selectCategory(categoryPath) 
                }
            },
            state = pullState,
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = screenPadding).background(Color.Black),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = isLoading && videos.isNotEmpty(),
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = Color.Black.copy(alpha = 0.7f),
                    color = Color.Red
                )
            }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoading && videos.isEmpty()) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.Red
                    )
                } else if (error != null && videos.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(text = error!!, color = Color.White, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    var isRetryFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = { 
                            if (isSearchActive && localSearchQuery.isNotEmpty()) {
                                scope.launch { viewModel.searchVideos(localSearchQuery, categoryPath = categoryPath) }
                            } else {
                                viewModel.selectCategory(categoryPath) 
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRetryFocused) Color.LightGray else Color.Red
                        ),
                        modifier = Modifier.onFocusChanged { isRetryFocused = it.isFocused }.focusable()
                    ) {
                        Text(stringResource(R.string.retry), color = if (isRetryFocused) Color.Black else Color.White)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    items(
                        items = videos,
                        contentType = { "video_thumbnail" }
                    ) { video ->
                        val isFirst = videos.firstOrNull()?.id == video.id
                        val isTV = isExpanded || isMedium
                        val itemModifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .then(
                                if (video.id == lastClickedVideoId) {
                                    Modifier.focusRequester(clickedItemFocusRequester)
                                } else if (isFirst) {
                                    Modifier.focusRequester(firstItemFocusRequester)
                                } else Modifier
                            )
                        NetflixThumbnail(
                            video = video,
                            modifier = itemModifier,
                            height = if (isExpanded) (220 * uiThumbnailScaleFactor).dp else (150 * uiThumbnailScaleFactor).dp,
                            showTitle = true,
                            errorPlaceholder = errorPlaceholder,
                            isTV = isTV,
                            onFocus = { focusedVideo = video }
                        ) { 
                            lastClickedVideoId = video.id
                            onVideoClick(video.id) 
                        }
                    }

                    if (isLoading) {
                        item(key = "loading_more", span = { GridItemSpan(columns) }) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color.Red, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }
            }
            
            // Silent Refresh Indicator (OnStream style)
            if (isLoading && videos.isNotEmpty()) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.TopCenter).zIndex(100f),
                    color = Color.Red,
                    trackColor = Color.Transparent
                )
            }
        }
    }
}
}
