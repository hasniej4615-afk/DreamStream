package com.duta.movie.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.duta.movie.util.VideoUtils
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass

import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import com.duta.movie.model.Video
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActressProfileScreen(
    actressPath: String,
    actressName: String,
    viewModel: VideoViewModel,
    onBackClick: () -> Unit,
    onVideoClick: (String) -> Unit,
    windowSizeClass: WindowSizeClass,
) {
    val profile by viewModel.selectedActressProfile.collectAsStateWithLifecycle()
    val isLoading by viewModel.isActressProfileLoading.collectAsStateWithLifecycle()
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val isVideosLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isEndReached by viewModel.isEndReached.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isSearchActive by viewModel.isSearchActive.collectAsStateWithLifecycle()
    val uiThumbnailScaleFactor by viewModel.uiThumbnailScaleFactor.collectAsStateWithLifecycle()

    val isExpanded = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
    val isMedium = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Medium
    val isTV = isExpanded || isMedium
    
    val columns = if (isTV) {
        if (uiThumbnailScaleFactor > 1.3f) 5 else if (uiThumbnailScaleFactor > 1.15f) 6 else if (uiThumbnailScaleFactor > 0.8f) 8 else 10
    } else 2
    
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
    
    LaunchedEffect(isVideosLoading, videos) {
        if (isTV && !isVideosLoading && videos.isNotEmpty()) {
            delay(800)
            try {
                if (lastClickedVideoId != null && videos.any { it.id == lastClickedVideoId }) {
                    clickedItemFocusRequester.requestFocus()
                } else {
                    firstItemFocusRequester.requestFocus()
                }
            } catch(_: Exception) {}
        }
    }

    var localSearchQuery by rememberSaveable(isSearchActive) { mutableStateOf(if (isSearchActive) searchQuery else "") }

    // Detection for infinite scroll
    val shouldLoadMore by remember(videos, isVideosLoading, isEndReached) {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val totalItemsCount = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            
            (videos.isNotEmpty() && !isVideosLoading && !isEndReached && 
                totalItemsCount > 0 && lastVisibleItemIndex >= totalItemsCount - 2)
        }
    }

    LaunchedEffect(actressPath) {
        if (!isSearchActive) {
            viewModel.loadActressProfile(actressPath)
            viewModel.selectCategory(actressPath) // Fetch her videos
        }
    }

    if (isSearchActive) {
        androidx.activity.compose.BackHandler {
            viewModel.setSearchActive(false)
            viewModel.onSearchQueryChange("")
            viewModel.selectCategory(actressPath)
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !isVideosLoading && !isEndReached) {
            viewModel.loadMoreVideos()
        }
    }

    val errorPlaceholder = rememberVectorPainter(Icons.Default.Warning)
    val pullState = rememberPullToRefreshState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    if (!isSearchActive) {
                        Text(actressName, fontWeight = FontWeight.Bold) 
                    } else {
                        val searchFocusRequester = remember { FocusRequester() }
                        LaunchedEffect(Unit) { searchFocusRequester.requestFocus() }
                        TextField(
                            value = localSearchQuery,
                            onValueChange = { localSearchQuery = it },
                            modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester).focusable(),
                            placeholder = { Text("Search in ${actressName}'s videos...", color = Color.Gray) },
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
                                scope.launch { viewModel.searchVideos(localSearchQuery, categoryPath = actressPath) }
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
                                                scope.launch { viewModel.searchVideos(spokenText, categoryPath = actressPath) }
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
                                viewModel.selectCategory(actressPath)
                            } else {
                                onBackClick()
                            }
                        },
                        modifier = Modifier
                            .onFocusChanged { isBackFocused = it.isFocused }
                            .border(if (isBackFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), CircleShape)
                            .focusable()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (!isSearchActive) {
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
            isRefreshing = isVideosLoading && videos.isNotEmpty(),
            onRefresh = {
                if (isSearchActive && localSearchQuery.isNotEmpty()) {
                    scope.launch { viewModel.searchVideos(localSearchQuery, categoryPath = actressPath) }
                } else {
                    viewModel.loadActressProfile(actressPath)
                    viewModel.selectCategory(actressPath)
                }
            },
            state = pullState,
            modifier = Modifier.fillMaxSize().padding(padding).background(Color.Black),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = isVideosLoading && videos.isNotEmpty(),
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = Color.Black.copy(alpha = 0.7f),
                    color = Color.Red
                )
            }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                if (isLoading) {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.Red)
                    }
                } else {
                    profile?.let { data ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Profile Image
                            if (data.image.isNotEmpty()) {
                                val context = LocalContext.current
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(VideoUtils.getOptimizedImage(data.image, isTV, context))
                                        .crossfade(true)
                                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                        .build(),
                                    contentDescription = data.name,
                                    modifier = Modifier
                                        .size(if (isTV) 100.dp else 120.dp)
                                        .clip(CircleShape)
                                        .background(Color.DarkGray),
                                    contentScale = ContentScale.Crop,
                                    error = errorPlaceholder,
                                    placeholder = errorPlaceholder
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                data.metadata.forEach { tag ->
                                    var isTagFocused by remember { mutableStateOf(false) }
                                    SuggestionChip(
                                        onClick = { },
                                        label = { Text(tag, fontSize = 12.sp) },
                                        modifier = Modifier
                                            .padding(horizontal = 4.dp)
                                            .onFocusChanged { isTagFocused = it.isFocused },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = if (isTagFocused) Color.Gray else Color.DarkGray,
                                            labelColor = Color.White
                                        ),
                                        border = if (isTagFocused) BorderStroke(2.dp, Color.White) else null
                                    )
                                }
                            }

                            if (data.bio.isNotEmpty()) {
                                Text(
                                    text = data.bio,
                                    color = Color.Gray,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    maxLines = if (isTV) 3 else 10,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Text(
                    "Videos",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
                )

                if (isVideosLoading && videos.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.Red)
                    }
                } else if (videos.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = error ?: "No videos found for this actress.",
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            var isRetryFocused by remember { mutableStateOf(false) }
                            Button(
                                onClick = { viewModel.selectCategory(actressPath) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isRetryFocused) Color.LightGray else Color.Red
                                ),
                                modifier = Modifier
                                    .onFocusChanged { isRetryFocused = it.isFocused }
                                    .border(if (isRetryFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(8.dp))
                                    .focusable()
                            ) {
                                Text(stringResource(R.string.retry), color = if (isRetryFocused) Color.Black else Color.White)
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        state = gridState,
                        contentPadding = PaddingValues(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        itemsIndexed(
                            items = videos,
                            contentType = { _, _ -> "video_thumbnail" }
                        ) { index, video ->
                            val itemModifier = Modifier
                                .padding(4.dp)
                                .then(
                                    if (video.id == lastClickedVideoId) {
                                        Modifier.focusRequester(clickedItemFocusRequester)
                                    } else if (index == 0) {
                                        Modifier.focusRequester(firstItemFocusRequester)
                                    } else Modifier
                                )
                            NetflixThumbnail(
                                video = video,
                                modifier = itemModifier,
                                showTitle = !isTV,
                                errorPlaceholder = errorPlaceholder,
                                isTV = isTV,
                                height = if (isTV) (150 * uiThumbnailScaleFactor).dp else (180 * uiThumbnailScaleFactor).dp,
                                onFocus = { focusedVideo = video }
                            ) { 
                                lastClickedVideoId = video.id
                                onVideoClick(video.id) 
                            }
                        }

                        if (isVideosLoading) {
                            item(span = { GridItemSpan(columns) }) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = Color.Red, modifier = Modifier.size(32.dp))
                                }
                            }
                        }
                    }
                }
            }
            
            // Silent Refresh Indicator (OnStream style)
            if (isVideosLoading && videos.isNotEmpty()) {
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
