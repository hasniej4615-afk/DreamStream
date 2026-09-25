package com.duta.movie.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.duta.movie.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.duta.movie.model.Comment
import com.duta.movie.util.SupabaseConfig
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import com.duta.movie.util.VideoUtils

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.ExperimentalLayoutApi

import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.lerp
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import android.net.Uri
import android.util.Log
import android.widget.Toast
import kotlin.OptIn
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.duta.movie.util.NetworkConfig
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun TrailerPlayer(
    videoUrl: String, 
    pageUrl: String,
    modifier: Modifier = Modifier, 
    muted: Boolean = true,
    onReady: () -> Unit = {},
    onError: () -> Unit = {},
) {
    Log.d("TrailerPlayer", "TrailerPlayer ENTER | URL: $videoUrl")
    val context = LocalContext.current
    
    val isYouTube = remember(videoUrl) { videoUrl.contains("youtube.com") || videoUrl.contains("youtu.be") }
    val isVimeo = remember(videoUrl) { videoUrl.contains("vimeo.com") }
    
    val isProbablyVideo = remember(videoUrl, isYouTube, isVimeo) {
        if (isYouTube || isVimeo) return@remember true
        val lower = videoUrl.lowercase()
        (lower.contains(".mp4") || lower.contains(".m3u8") || lower.contains(".mkv") || lower.contains(".webm")) &&
        !lower.endsWith(".jpg") && !lower.endsWith(".png") && !lower.endsWith(".webp")
    }

    if (!isProbablyVideo) {
        LaunchedEffect(Unit) { onError() }
        return
    }

    if (isYouTube || isVimeo) {
        val embedUrl = remember(videoUrl, isYouTube, isVimeo, muted) {
            when {
                isYouTube -> {
                    val id = when {
                        videoUrl.contains("v=") -> videoUrl.substringAfter("v=").substringBefore("&").substringBefore("?")
                        videoUrl.contains("be/") -> videoUrl.substringAfter("be/").substringBefore("?").substringBefore("&")
                        videoUrl.contains("embed/") -> videoUrl.substringAfter("embed/").substringBefore("?").substringBefore("&")
                        videoUrl.contains("watch/") -> videoUrl.substringAfter("watch/").substringBefore("?").substringBefore("&")
                        videoUrl.length in 10..12 -> videoUrl // Raw ID
                        else -> null
                    }
                    if (id != null) "https://www.youtube-nocookie.com/embed/$id?autoplay=1&mute=${if (muted) 1 else 0}&controls=1&loop=1&playlist=$id&rel=0&modestbranding=1&enablejsapi=1" else null
                }
                isVimeo -> {
                    val id = videoUrl.trimEnd('/').substringAfterLast("/")
                    if (id.all { it.isDigit() }) "https://player.vimeo.com/video/$id?autoplay=1&muted=${if (muted) 1 else 0}&loop=1" else null
                }
                else -> null
            }
        }
        
        Log.d("TrailerPlayer", "YouTube/Vimeo branch | Video URL: $videoUrl | Embed: $embedUrl")
        
        if (embedUrl != null) {
            AndroidView(
                factory = { ctx ->
                    android.webkit.WebView(ctx).apply {
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(android.graphics.Color.BLACK)
                        settings.javaScriptEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.setSupportZoom(false)
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
                        
                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                                Log.d("TrailerPlayer", "JS Console [${consoleMessage?.messageLevel()}]: ${consoleMessage?.message()} (${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()})")
                                return true
                            }
                        }

                        webViewClient = object : android.webkit.WebViewClient() {
                            override fun onPageStarted(view: android.webkit.WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                Log.d("TrailerPlayer", "Embed load started: $url")
                            }

                            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                Log.d("TrailerPlayer", "Embed load finished: $url")
                                postDelayed({ onReady() }, 1200)
                            }
                            
                            override fun onReceivedError(view: android.webkit.WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                                if (request?.isForMainFrame == true) {
                                    Log.e("TrailerPlayer", "Embed error: ${error?.description}")
                                    onError()
                                }
                            }

                            override fun shouldOverrideUrlLoading(view: android.webkit.WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                val url = request?.url?.toString() ?: ""
                                if ((url.contains("youtube.com") || url.contains("vimeo.com")) && !url.contains("embed") && !url.contains("player.vimeo")) {
                                     Log.d("TrailerPlayer", "Blocking redirect to: $url")
                                     return true
                                }
                                return false
                            }
                        }
                        loadUrl(embedUrl, mapOf("Referer" to "${com.duta.movie.util.VideoExtractor.getBaseUrl()}/"))
                    }
                },
                modifier = modifier,
                onRelease = { it.destroy() },
                update = { webView ->
                    if (webView.url != embedUrl && (webView.url == null || (!webView.url!!.contains("youtube.com") && !webView.url!!.contains("vimeo.com")))) {
                        Log.d("TrailerPlayer", "Embed URL changed, reloading: $embedUrl")
                        webView.loadUrl(embedUrl!!, mapOf("Referer" to "${com.duta.movie.util.VideoExtractor.getBaseUrl()}/"))
                    }
                }
            )
        } else {
            LaunchedEffect(Unit) { onError() }
        }
    } else {
        Log.d("TrailerPlayer", "Native (ExoPlayer) branch | Video URL: $videoUrl")
        val baseDomains = mutableListOf("seoulschool.org", "ladyriderswear.com", "itoshii-movie.com")
        val currentBase = com.duta.movie.util.VideoExtractor.getBaseUrl().substringAfter("://")
        if (currentBase.isNotEmpty() && !baseDomains.contains(currentBase)) {
            baseDomains.add(0, currentBase)
        }

        val exoPlayer = remember(videoUrl) {
            val okHttpFactory = OkHttpDataSource.Factory(NetworkConfig.okHttpClient)
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                .setDefaultRequestProperties(
                    mapOf(
                        "Referer" to pageUrl,
                        "Origin" to com.duta.movie.util.VideoExtractor.getBaseUrl()
                    )
                )
            
            val mediaSourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(okHttpFactory)

            ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .build().apply {
                    setMediaItem(MediaItem.fromUri(videoUrl))
                    
                    var retryCount = 0
                    val triedUrls = mutableSetOf(videoUrl)
                    
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_READY) play()
                        }

                        override fun onRenderedFirstFrame() {
                            onReady()
                        }
                        
                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            if (retryCount >= 3) {
                                onError()
                                return
                            }

                            val segments = pageUrl.split("?").first().split("/")
                            val stableId = segments.findLast { (it.isNotEmpty() && it.all { c -> c.isDigit() } && it.length >= 3) }
                            
                            if (stableId != null) {
                                val idGroup = (stableId.toIntOrNull() ?: 0) / 1000 * 1000
                                val strategies = mutableListOf<String>()
                                for (domain in baseDomains) {
                                    strategies.add("https://$domain/preview/$stableId.mp4")
                                    strategies.add("https://$domain/contents/videos_screenshots/$idGroup/$stableId/preview.mp4")
                                }

                                for (newUrl in strategies) {
                                    if (!triedUrls.contains(newUrl)) {
                                        triedUrls.add(newUrl)
                                        retryCount++
                                        setMediaItem(MediaItem.fromUri(newUrl))
                                        prepare()
                                        play()
                                        return
                                    }
                                }
                            }
                            onError()
                        }
                    })

                    volume = if (muted) 0f else 1f
                    prepare()
                    playWhenReady = true
                    repeatMode = Player.REPEAT_MODE_ALL
                }
        }

        DisposableEffect(exoPlayer) {
            onDispose { exoPlayer.release() }
        }

        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            },
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoDetailScreen(
    videoId: String,
    onBackClick: () -> Unit,
    onPlayClick: (String, String?) -> Unit,
    onActressClick: (String, String) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: VideoViewModel = viewModel(),
    windowSizeClass: WindowSizeClass
) {
    val video by viewModel.videoMetadata.collectAsStateWithLifecycle()
    SideEffect {
        Log.d("VideoDetailScreen", "Recomposed | Video: ${video?.title} | Trailer URL: ${video?.previewUrl}")
    }

    val context = LocalContext.current

    // Safety Check: Prevent direct access to adult content via deep link or history
    

    val myList by viewModel.myList.collectAsStateWithLifecycle()
    val isAddedToMyList = myList.contains(videoId)
    val error by viewModel.error.collectAsStateWithLifecycle()
    val isPlayerActive by viewModel.isPlayerActive.collectAsStateWithLifecycle()

    val isExpanded = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
    val isMedium = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Medium
    val isTV = isExpanded || isMedium
    
    val playButtonFocusRequester = remember { FocusRequester() }

    var showTrailer by remember(videoId) { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(error) {
        error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        }
    }

    androidx.compose.runtime.LaunchedEffect(videoId) {
        viewModel.loadFullDetails(videoId)
    }

    // Delayed auto-focus to prevent jank during transition
    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            delay(1000)
            try { playButtonFocusRequester.requestFocus() } catch(_: Exception) {}
        }
    }

    androidx.activity.compose.BackHandler {
        onBackClick()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        val currentVideo = video
        if (currentVideo == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.Red)
            }
        } else {
            var precacheJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

            // ARCHIVE.ORG INSTANT PRE-WARM: Pre-cache header and moov atom in background while user reads synopsis
            LaunchedEffect(currentVideo.id) {
                val streamUrl = currentVideo.videoUrl.takeIf { it.contains("archive.org") }
                    ?: currentVideo.servers.firstOrNull { it.url.contains("archive.org") }?.url
                if (!streamUrl.isNullOrEmpty()) {
                    val fastUrl = com.duta.movie.util.VideoExtractor.optimizeArchiveUrl(streamUrl)
                    precacheJob = coroutineContext[kotlinx.coroutines.Job]
                    com.duta.movie.util.PlayerCacheManager.precacheStream(context, fastUrl)
                }
            }

            DisposableEffect(Unit) {
                onDispose {
                    precacheJob?.cancel()
                }
            }

            val safePlayClick: (String, String?) -> Unit = { id, ep ->
                precacheJob?.cancel()
                onPlayClick(id, ep)
            }

            if (isExpanded) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1.2f).fillMaxHeight()) {
                        VideoDetailMedia(currentVideo, isPlayerActive, context, showTrailer, isTV = true)
                        DetailGradients()
                        DetailNavigation(onBackClick, onSettingsClick, isAddedToMyList) { viewModel.toggleMyList(currentVideo.id) }
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp)
                    ) {
                        VideoDetailInfo(currentVideo, safePlayClick, onActressClick, onTrailerClick = { showTrailer = true }, playFocusRequester = playButtonFocusRequester, viewModel = viewModel)
                        Spacer(modifier = Modifier.height(100.dp))
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Box(modifier = Modifier.fillMaxWidth().height(420.dp)) {
                        VideoDetailMedia(currentVideo, isPlayerActive, context, showTrailer, isTV = isTV)
                        DetailGradients()
                        DetailNavigation(onBackClick, onSettingsClick, isAddedToMyList) { viewModel.toggleMyList(currentVideo.id) }
                    }
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp).fillMaxWidth()) {
                        VideoDetailInfo(currentVideo, safePlayClick, onActressClick, onTrailerClick = { showTrailer = true }, playFocusRequester = playButtonFocusRequester, viewModel = viewModel)
                        Spacer(modifier = Modifier.height(120.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun VideoDetailMedia(video: com.duta.movie.model.Video, isPlayerActive: Boolean, context: android.content.Context, showTrailer: Boolean, isTV: Boolean = false) {
    val isPreviewVideo = video.previewUrl.let { com.duta.movie.util.VideoExtractor.isDirectVideoUrl(it) || it.contains("youtube.com") || it.contains("youtu.be") }
    
    if (video.previewUrl.isNotEmpty() && isPreviewVideo && !isPlayerActive && showTrailer) {
        var isPlayerReady by remember(video.id, video.previewUrl) { mutableStateOf(false) }
        var hasPlayerError by remember(video.id, video.previewUrl) { mutableStateOf(false) }
        
        Box(modifier = Modifier.fillMaxSize()) {
            TrailerPlayer(
                videoUrl = video.previewUrl,
                pageUrl = video.videoUrl,
                modifier = Modifier.fillMaxSize(),
                muted = false,
                onReady = { isPlayerReady = true },
                onError = { hasPlayerError = true }
            )
            if (!isPlayerReady) {
                DetailStaticImage(video.backdropUrl.ifEmpty { video.thumbnailUrl }, video.thumbnailUrl.ifEmpty { video.backdropUrl }, context, isTV)
                if (!hasPlayerError) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.Red, modifier = Modifier.size(48.dp))
                    }
                }
            }
        }
    } else {
        DetailStaticImage(video.backdropUrl.ifEmpty { video.thumbnailUrl }, video.thumbnailUrl.ifEmpty { video.backdropUrl }, context, isTV)
    }
}

@Composable
fun DetailStaticImage(url: String, fallbackUrl: String? = null, context: android.content.Context, isTV: Boolean = false) {
    var currentUrl by remember(url) { mutableStateOf(url) }
    val detailRequest = ImageRequest.Builder(context)
        .data(VideoUtils.getOptimizedBackdrop(currentUrl, isTV, context))
        .crossfade(true)
        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
        .listener(
            onError = { _, _ ->
                if (!fallbackUrl.isNullOrEmpty() && currentUrl != fallbackUrl) {
                    currentUrl = fallbackUrl
                }
            }
        )
        .build()

    val errorPlaceholder = rememberVectorPainter(Icons.Default.Warning)

    AsyncImage(
        model = detailRequest,
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
        error = errorPlaceholder,
        placeholder = errorPlaceholder
    )
}

@Composable
fun DetailGradients() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.5f), Color.Black)
                )
            )
    )
}

@Composable
fun DetailNavigation(onBackClick: () -> Unit, onSettingsClick: () -> Unit, isAddedToMyList: Boolean, onToggleMyList: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        var isBackFocused by remember { mutableStateOf(false) }
        val backScale by animateFloatAsState(if (isBackFocused) 1.2f else 1f)
        Surface(
            shape = CircleShape,
            color = if (isBackFocused) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.5f),
            modifier = Modifier
                .size(40.dp)
                .graphicsLayer(scaleX = backScale, scaleY = backScale)
                .onFocusChanged { isBackFocused = it.isFocused }
                .border(if (isBackFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), CircleShape)
                .clickable { onBackClick() }
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = Color.White) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            var isMyListFocused by remember { mutableStateOf(false) }
            val myListScale by animateFloatAsState(if (isMyListFocused) 1.2f else 1f)
            Surface(
                shape = CircleShape,
                color = if (isMyListFocused) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.5f),
                modifier = Modifier
                    .size(40.dp)
                    .graphicsLayer(scaleX = myListScale, scaleY = myListScale)
                    .onFocusChanged { isMyListFocused = it.isFocused }
                    .border(if (isMyListFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), CircleShape)
                    .clickable { onToggleMyList() }
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(if (isAddedToMyList) Icons.Default.Check else Icons.Default.Add, contentDescription = stringResource(R.string.my_list), tint = if (isAddedToMyList) Color.Red else Color.White) }
            }
            var isSettingsFocused by remember { mutableStateOf(false) }
            val settingsScale by animateFloatAsState(if (isSettingsFocused) 1.2f else 1f)
            Surface(
                shape = CircleShape,
                color = if (isSettingsFocused) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.5f),
                modifier = Modifier
                    .size(40.dp)
                    .graphicsLayer(scaleX = settingsScale, scaleY = settingsScale)
                    .onFocusChanged { isSettingsFocused = it.isFocused }
                    .border(if (isSettingsFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), CircleShape)
                    .clickable { onSettingsClick() }
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings), tint = Color.White) }
            }

            // Cast Button in Detail Screen
            var isCastFocused by remember { mutableStateOf(false) }
            val castScale by animateFloatAsState(if (isCastFocused) 1.2f else 1f)
            var mediaRouteButton by remember { mutableStateOf<MediaRouteButton?>(null) }
            
            Surface(
                onClick = { mediaRouteButton?.performClick() },
                shape = CircleShape,
                color = if (isCastFocused) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.5f),
                modifier = Modifier
                    .size(40.dp)
                    .graphicsLayer(scaleX = castScale, scaleY = castScale)
                    .onFocusChanged { isCastFocused = it.isFocused }
                    .border(if (isCastFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), CircleShape)
                    .focusable()
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AndroidView(
                        factory = { context ->
                            MediaRouteButton(context).apply {
                                CastButtonFactory.setUpMediaRouteButton(context, this)
                                mediaRouteButton = this
                            }
                        },
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoDetailInfo(
    video: com.duta.movie.model.Video, 
    onPlayClick: (String, String?) -> Unit, 
    onActressClick: (String, String) -> Unit,
    onTrailerClick: () -> Unit,
    playFocusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    viewModel: VideoViewModel
) {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        val isLikelySeries = remember(video.videoUrl, video.isSeries, video.episodes) {
            if (video.episodes.isNotEmpty()) true
            else if (video.isSeries == true) true
            else if (video.isSeries == false) false
            else {
                val lowUrl = video.videoUrl.lowercase()
                lowUrl.contains("/series/") || lowUrl.contains("/serial-tv/") || lowUrl.contains("/tv/") || lowUrl.contains("/serial-tv-terbaru/") ||
                lowUrl.contains("/eps/") || lowUrl.contains("/episode/") || lowUrl.contains("-episode-") ||
                lowUrl.contains("/episod/") || lowUrl.contains("-episod-") || lowUrl.contains("-epi-")
            }
        }

        val currentOrFirstEpisode = remember(video.videoUrl, video.id, video.episodes) {
            if (video.episodes.isEmpty()) null
            else {
                val targetSlug = com.duta.movie.util.VideoExtractor.extractCleanSlug(video.videoUrl)
                val idSlug = com.duta.movie.util.VideoExtractor.stripSourcePrefix(video.id)
                video.episodes.find { ep ->
                    val epSlug = com.duta.movie.util.VideoExtractor.extractCleanSlug(ep.url)
                    epSlug == targetSlug || epSlug == idSlug ||
                    (targetSlug.length > 5 && ep.url.contains(targetSlug)) ||
                    (idSlug.length > 5 && ep.url.contains(idSlug)) ||
                    (epSlug.length > 5 && video.videoUrl.contains(epSlug))
                } ?: video.episodes.firstOrNull()
            }
        }

        Text(text = video.title, color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
        
        Row(
            modifier = Modifier.padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (video.quality.isNotEmpty()) {
                Surface(color = Color.Red, shape = RoundedCornerShape(4.dp)) {
                    Text(video.quality, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            InfoItem(Icons.Default.Schedule, video.duration)
            InfoItem(Icons.Default.Event, video.date)
        }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                var isPlayFocused by remember { mutableStateOf(false) }
                val playScale by animateFloatAsState(if (isPlayFocused) 1.1f else 1f)
                Button(
                    onClick = { 
                        val targetEpUrl = if (isLikelySeries) currentOrFirstEpisode?.url else null
                        onPlayClick(video.id, targetEpUrl) 
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .then(if (playFocusRequester != null) Modifier.focusRequester(playFocusRequester) else Modifier)
                        .graphicsLayer(scaleX = playScale, scaleY = playScale)
                        .onFocusChanged { isPlayFocused = it.isFocused }
                        .shadow(if (isPlayFocused) 15.dp else 0.dp, RoundedCornerShape(8.dp), spotColor = Color.White)
                        .border(if (isPlayFocused) BorderStroke(3.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(8.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.play), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                
                // trailer button
                val hasTrailer = video.previewUrl.isNotEmpty()
                var isTrailerFocused by remember { mutableStateOf(false) }
                val trailerScale by animateFloatAsState(if (isTrailerFocused) 1.1f else 1f)
                OutlinedButton(
                    onClick = { if (hasTrailer) onTrailerClick() },
                    enabled = hasTrailer,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .graphicsLayer(scaleX = trailerScale, scaleY = trailerScale)
                        .onFocusChanged { isTrailerFocused = it.isFocused }
                        .shadow(if (isTrailerFocused && hasTrailer) 15.dp else 0.dp, RoundedCornerShape(8.dp), spotColor = Color.Red)
                        .border(
                            if (isTrailerFocused && hasTrailer) BorderStroke(3.dp, Color.Red) 
                            else if (hasTrailer) BorderStroke(1.dp, Color.White)
                            else BorderStroke(1.dp, Color.DarkGray), 
                            RoundedCornerShape(8.dp)
                        ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (hasTrailer) Color.White else Color.Gray,
                        disabledContentColor = Color.Gray
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Movie, contentDescription = null, tint = if (hasTrailer) Color.White else Color.Gray)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (hasTrailer) "Trailer" else "No Trailer", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            var isRecommendFocused by remember { mutableStateOf(false) }
            val recommendScale by animateFloatAsState(if (isRecommendFocused) 1.08f else 1f)
            val isRecommended by viewModel.isRecommended(video.id).collectAsStateWithLifecycle(false)
            val recommendCount by viewModel.getRecommendCount(video.id).collectAsStateWithLifecycle(0)

            OutlinedButton(
                onClick = { viewModel.toggleRecommendation(video) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .graphicsLayer(scaleX = recommendScale, scaleY = recommendScale)
                    .onFocusChanged { isRecommendFocused = it.isFocused }
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                            (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                             keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                             keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                            viewModel.toggleRecommendation(video)
                            true
                        } else false
                    }
                    .shadow(
                        elevation = if (isRecommendFocused) 20.dp else 0.dp,
                        shape = RoundedCornerShape(8.dp),
                        spotColor = Color.Red,
                        ambientColor = Color.White
                    )
                    .border(
                        if (isRecommendFocused) BorderStroke(3.5.dp, Color.White)
                        else if (isRecommended) BorderStroke(1.5.dp, Color.Red)
                        else BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                        RoundedCornerShape(8.dp)
                    ),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isRecommendFocused) {
                        if (isRecommended) Color.Red.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.18f)
                    } else if (isRecommended) {
                        Color.Red.copy(alpha = 0.22f)
                    } else {
                        Color.Transparent
                    },
                    contentColor = if (isRecommendFocused) Color.White else if (isRecommended) Color.Red else Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = if (isRecommended) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                    contentDescription = null,
                    tint = if (isRecommendFocused) Color.White else if (isRecommended) Color.Red else Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                val labelText = if (recommendCount > 0) {
                    stringResource(R.string.pakcik_rekomen_count, recommendCount)
                } else {
                    stringResource(R.string.pakcik_rekomen)
                }
                Text(
                    text = labelText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (isRecommendFocused) Color.White else if (isRecommended) Color.Red else Color.White
                )
            }

        Spacer(modifier = Modifier.height(24.dp))
        
        // Premium Synopsis Layout
        var isExpanded by remember { mutableStateOf(false) }
        val synopsis = video.description.ifEmpty { stringResource(R.string.no_synopsis) }
        
        Text(
            text = stringResource(R.string.synopsis), 
            color = Color.White, 
            style = MaterialTheme.typography.titleMedium, 
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        var isSynopsisFocused by remember { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isSynopsisFocused = it.isFocused }
                .border(if (isSynopsisFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(8.dp))
                .clickable { isExpanded = !isExpanded }
                .padding(if (isSynopsisFocused) 8.dp else 4.dp)
        ) {
            Text(
                text = synopsis,
                color = if (isSynopsisFocused) Color.White else Color.Gray.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 22.sp,
                maxLines = if (isExpanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis
            )
            
            if (synopsis.length > 200) {
                Text(
                    text = if (isExpanded) stringResource(R.string.hide) else stringResource(R.string.more),
                    color = Color.Red,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        val isDetailLoading by viewModel.isDetailLoading.collectAsStateWithLifecycle()
        val isSearchingAlternatives by viewModel.isSearchingAlternatives.collectAsStateWithLifecycle()
        val localContext = LocalContext.current
        val activeEpName = remember(currentOrFirstEpisode, video.title, video.videoUrl) {
            val raw = currentOrFirstEpisode?.name ?: "Episode 1"
            com.duta.movie.util.VideoExtractor.cleanEpisodeTitle(raw, video.title, video.videoUrl)
        }
        val headerText = if (isLikelySeries) "Servers ($activeEpName)" else "Servers"

        Spacer(modifier = Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = headerText, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (video.servers.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(color = Color.DarkGray, shape = CircleShape) {
                        Text(text = video.servers.size.toString(), color = Color.LightGray, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            var isAltFocused by remember { mutableStateOf(false) }
            Button(
                onClick = {
                    if (!isSearchingAlternatives) {
                        Toast.makeText(localContext, localContext.getString(R.string.searching_other_sources), Toast.LENGTH_SHORT).show()
                        viewModel.searchAlternativeSources(video.id) { foundCount ->
                            if (foundCount > 0) {
                                Toast.makeText(localContext, localContext.getString(R.string.sources_found, foundCount), Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(localContext, localContext.getString(R.string.no_other_sources_found), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                enabled = !isSearchingAlternatives,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAltFocused) Color.White else Color(0xFF2A2A2A),
                    contentColor = if (isAltFocused) Color.Black else Color.White,
                    disabledContainerColor = Color(0xFF1E1E1E),
                    disabledContentColor = Color.Gray
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .onFocusChanged { isAltFocused = it.isFocused }
                    .border(if (isAltFocused) BorderStroke(2.dp, Color.Red) else BorderStroke(1.dp, Color.DarkGray), RoundedCornerShape(8.dp))
            ) {
                if (isSearchingAlternatives) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.Red)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.searching_other_sources), fontSize = 12.sp)
                } else {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.find_other_sources), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (video.servers.isNotEmpty()) {
            val prioritizedServers = video.servers.asSequence()
                .filter { !it.url.lowercase().contains("layarkaca") }
                .sortedWith(compareByDescending<com.duta.movie.model.VideoServer> {
                    com.duta.movie.util.VideoExtractor.getProviderPriority(it.name, it.url)
                }.thenBy { it.name })
                .toList()

            FlowRow(
                modifier = Modifier.fillMaxWidth(), 
                horizontalArrangement = Arrangement.spacedBy(10.dp), 
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                prioritizedServers.asSequence().filter {
                    val low = it.url.lowercase()
                    // Allow main site mirrors (Indostream/HgCloud proxies) but block specific spam domains
                    !low.contains("ladyriderswear") && !low.contains("chiptaylor") && !low.contains("seoulschool")
                }.forEach { server ->
                    var isServerFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = { onPlayClick(video.id, server.url) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isServerFocused) Color.White else Color(0xFF1A1A1A),
                            contentColor = if (isServerFocused) Color.Black else Color.White
                        ),
                        modifier = Modifier
                            .onFocusChanged { isServerFocused = it.isFocused }
                            .border(if (isServerFocused) BorderStroke(3.dp, Color.Red) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(8.dp)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(server.name, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else if (isDetailLoading) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(110.dp, 130.dp, 95.dp, 120.dp).forEach { width ->
                    Box(
                        modifier = Modifier
                            .width(width)
                            .height(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .shimmerEffect()
                    )
                }
            }
        }

        val isMovie = remember(video.videoUrl, video.description, video.episodes, video.isSeries) { 
            // If we have episodes, it's definitely a series and episodes should be displayed
            if (video.episodes.isNotEmpty()) return@remember false
            if (video.isSeries == true) return@remember false
            if (video.isSeries == false) return@remember true

            val lowUrl = video.videoUrl.lowercase()
            if (lowUrl.contains("/movie/") || lowUrl.contains("/film/")) return@remember true
            if (lowUrl.contains("/eps/") || lowUrl.contains("/episode/") || lowUrl.contains("-episode-") ||
                lowUrl.contains("/episod/") || lowUrl.contains("-episod-") || lowUrl.contains("-epi-")) return@remember false

            // If we reach here and there are no episodes, default to true so the user at least gets the Play button
            true
        }
        if (video.episodes.isNotEmpty() && !isMovie) {
            val groupedEpisodes = remember(video.episodes) {
                video.episodes.groupBy { it.season.ifEmpty { video.season.ifEmpty { "Season 1" } } }
                    .toSortedMap(compareBy { 
                        it.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0 
                    })
            }
            val seasons = remember(groupedEpisodes) { groupedEpisodes.keys.toList() }
            var currentSeason by remember(seasons) { mutableStateOf(seasons.firstOrNull() ?: "") }

            DetailSectionHeader("Episodes", video.episodes.size.toString())
            
            if (seasons.size > 1) {
                ScrollableTabRow(
                    selectedTabIndex = seasons.indexOf(currentSeason).coerceAtLeast(0),
                    containerColor = Color.Transparent,
                    contentColor = Color.Red,
                    edgePadding = 0.dp,
                    divider = {},
                    indicator = { tabPositions ->
                        if (seasons.indexOf(currentSeason) < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[seasons.indexOf(currentSeason)]),
                                color = Color.Red
                            )
                        }
                    },
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    seasons.forEach { season ->
                        var isTabFocused by remember { mutableStateOf(false) }
                        Tab(
                            selected = currentSeason == season,
                            onClick = { currentSeason = season },
                            modifier = Modifier
                                .onFocusChanged { 
                                     isTabFocused = it.isFocused 
                                     if (it.isFocused) currentSeason = season
                                }
                                .padding(horizontal = 4.dp),
                            text = {
                                Text(
                                    text = season,
                                    color = if (currentSeason == season || isTabFocused) Color.White else Color.Gray,
                                    fontWeight = if (currentSeason == season) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }

            val watchedEpisodes by viewModel.getWatchedEpisodes(video.id).collectAsState(initial = emptySet())
            val displaySeason = if (currentSeason.isNotEmpty() && currentSeason in seasons) currentSeason else (seasons.firstOrNull() ?: "")

            groupedEpisodes[displaySeason]?.sortedWith(compareBy { ep -> 
                Regex("""(?i)\b(?:episod[e]?|eps|ep)\s*(\d+)\b""").find(ep.name)?.groupValues?.get(1)?.toIntOrNull()
                    ?: Regex("""(?i)[-_](?:episod[e]?|eps|ep)[-_](\d+)""").find(ep.url)?.groupValues?.get(1)?.toIntOrNull()
                    ?: ep.name.filter { c -> c.isDigit() }.toIntOrNull() ?: 0
            })?.forEachIndexed { index, episode ->
                val progressId = viewModel.getEpisodeProgressId(video.id, episode.url)
                val cleanEp = remember(episode, video.title, video.videoUrl) {
                    val cleaned = com.duta.movie.util.VideoExtractor.cleanEpisodeTitle(episode.name, video.title, video.videoUrl)
                    if (cleaned != episode.name) episode.copy(name = cleaned) else episode
                }
                EpisodeRow(
                    index = index + 1, 
                    episode = cleanEp, 
                    isWatched = watchedEpisodes.contains(progressId),
                    onClick = { onPlayClick(video.id, episode.url) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        } else if (video.episodes.isEmpty() && isDetailLoading && isLikelySeries) {
            DetailSectionHeader("Episodes")
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(4) {
                    Surface(
                        color = Color(0xFF111111),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(28.dp)
                                    .height(20.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .shimmerEffect()
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.55f)
                                        .height(16.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .shimmerEffect()
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.3f)
                                        .height(12.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .shimmerEffect()
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .shimmerEffect()
                            )
                        }
                    }
                }
            }
        }

        if (video.actresses.isNotEmpty()) {
            DetailSectionHeader("Cast")
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                video.actresses.forEach { actressName ->
                    val path = video.actressPaths[actressName]
                    val imageUrl = video.actressImages[actressName]
                    var isCastFocused by remember { mutableStateOf(false) }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(80.dp)
                            .onFocusChanged { isCastFocused = it.hasFocus }
                            .graphicsLayer {
                                scaleX = if (isCastFocused) 1.2f else 1f
                                scaleY = if (isCastFocused) 1.2f else 1f
                            }
                            .border(if (isCastFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), CircleShape)
                            .clickable { 
                                if (path != null) onActressClick(path, actressName) 
                                else onActressClick("/?s=${actressName.replace(" ", "+")}", actressName)
                            }
                            .focusable()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(if (isCastFocused) Color.White.copy(alpha = 0.2f) else Color.DarkGray),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!imageUrl.isNullOrEmpty()) {
                                val actressPlaceholder = rememberVectorPainter(Icons.Default.Person)
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(imageUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = actressName,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                    error = actressPlaceholder,
                                    placeholder = actressPlaceholder
                                )
                            } else {
                                Text(actressName.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(
                            text = actressName,
                            color = if (isCastFocused) Color.White else Color.Gray,
                            fontSize = 11.sp,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        val context = LocalContext.current
        val isRealTV = remember { com.duta.movie.util.DeviceUtils.isTvDevice(context) }
        CommentSection(
            videoId = video.id,
            viewModel = viewModel,
            isRealTV = isRealTV
        )

        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
fun CommentSection(
    videoId: String,
    viewModel: VideoViewModel,
    isRealTV: Boolean
) {
    val comments by viewModel.comments.collectAsStateWithLifecycle()
    val isCommentsLoading by viewModel.isCommentsLoading.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmittingComment.collectAsStateWithLifecycle()
    val savedNickname by viewModel.userNickname.collectAsStateWithLifecycle()
    val myCommentIds by viewModel.myCommentIds.collectAsStateWithLifecycle()

    var nickname by remember { mutableStateOf("") }
    var commentText by remember { mutableStateOf("") }
    val context = LocalContext.current

    val nicknameFocusRequester = remember { FocusRequester() }
    val commentFocusRequester = remember { FocusRequester() }
    val postBtnFocusRequester = remember { FocusRequester() }

    var isNicknameFocused by remember { mutableStateOf(false) }
    var isCommentFocused by remember { mutableStateOf(false) }

    var editingComment by remember { mutableStateOf<Comment?>(null) }
    var deletingComment by remember { mutableStateOf<Comment?>(null) }
    var isEditSubmitting by remember { mutableStateOf(false) }
    var isDeleteSubmitting by remember { mutableStateOf(false) }

    LaunchedEffect(savedNickname) {
        if (nickname.isEmpty() && savedNickname.isNotEmpty()) {
            nickname = savedNickname
        }
    }

    DetailSectionHeader(
        title = "Comments",
        badge = if (comments.isNotEmpty()) "${comments.size}" else null
    )

    Surface(
        color = Color(0xFF161616),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Join the Discussion",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = nickname,
                onValueChange = { if (it.length <= 30) nickname = it },
                label = { Text("Your Nickname", fontSize = 12.sp) },
                placeholder = { Text("e.g. MovieBuff", color = Color.Gray, fontSize = 12.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.Red,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedLabelColor = Color.Red,
                    unfocusedLabelColor = Color.Gray
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { commentFocusRequester.requestFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(nicknameFocusRequester)
                    .onFocusChanged { isNicknameFocused = it.isFocused }
                    .then(
                        if (isRealTV && isNicknameFocused) {
                            Modifier.border(2.dp, Color.White, RoundedCornerShape(4.dp))
                        } else Modifier
                    )
                    .padding(bottom = 8.dp)
            )

            OutlinedTextField(
                value = commentText,
                onValueChange = { if (it.length <= 500) commentText = it },
                label = { Text("Write a comment...", fontSize = 12.sp) },
                placeholder = { Text("What did you think of this title?", color = Color.Gray, fontSize = 12.sp) },
                minLines = 2,
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.Red,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedLabelColor = Color.Red,
                    unfocusedLabelColor = Color.Gray
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (commentText.isNotBlank() && nickname.isNotBlank() && !isSubmitting) {
                        viewModel.submitComment(videoId, nickname, commentText) { success, err ->
                            if (success) {
                                commentText = ""
                            } else {
                                Toast.makeText(context, err ?: "Failed to post comment", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(commentFocusRequester)
                    .onFocusChanged { isCommentFocused = it.isFocused }
                    .then(
                        if (isRealTV && isCommentFocused) {
                            Modifier.border(2.dp, Color.White, RoundedCornerShape(4.dp))
                        } else Modifier
                    )
                    .padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${commentText.length}/500",
                    color = Color.Gray,
                    fontSize = 11.sp
                )

                var isPostBtnFocused by remember { mutableStateOf(false) }
                val canSubmit = commentText.isNotBlank() && nickname.isNotBlank() && !isSubmitting

                Button(
                    onClick = {
                        if (canSubmit) {
                            viewModel.submitComment(videoId, nickname, commentText) { success, err ->
                                if (success) {
                                    commentText = ""
                                } else {
                                    Toast.makeText(context, err ?: "Failed to post comment", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else if (nickname.isBlank()) {
                            Toast.makeText(context, "Please enter your nickname first", Toast.LENGTH_SHORT).show()
                        } else if (commentText.isBlank()) {
                            Toast.makeText(context, "Please type a comment before posting", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isSubmitting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (canSubmit) Color.Red else Color.Red.copy(alpha = 0.4f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .focusRequester(postBtnFocusRequester)
                        .onFocusChanged { isPostBtnFocused = it.isFocused }
                        .graphicsLayer {
                            scaleX = if (isPostBtnFocused) 1.05f else 1f
                            scaleY = if (isPostBtnFocused) 1.05f else 1f
                        }
                        .border(
                            border = if (isPostBtnFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent),
                            shape = RoundedCornerShape(8.dp)
                        )
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Posting...", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Post Comment", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    if (!SupabaseConfig.isConfigured) {
        Surface(
            color = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFF333333)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CloudQueue,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Cloud Sync Ready",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Add your Supabase Project URL and Anon Key in SupabaseConfig.kt to connect comments across all users.",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }

    if (isCommentsLoading && comments.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.Red, modifier = Modifier.size(28.dp))
        }
    } else if (comments.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.ChatBubbleOutline,
                    contentDescription = null,
                    tint = Color.Gray.copy(alpha = 0.5f),
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No comments yet. Be the first to comment!",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
        }
    } else {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            comments.forEach { comment ->
                val isMyComment = comment.id.toString() in myCommentIds
                CommentItem(
                    comment = comment,
                    isRealTV = isRealTV,
                    isMyComment = isMyComment,
                    onEditClick = { editingComment = comment },
                    onDeleteClick = { deletingComment = comment }
                )
            }
        }
    }

    // Edit Comment Dialog
    editingComment?.let { target ->
        var editedText by remember(target.id) { mutableStateOf(target.comment) }
        val editInputFocusRequester = remember { FocusRequester() }
        val editSaveFocusRequester = remember { FocusRequester() }
        val editCancelFocusRequester = remember { FocusRequester() }
        var isEditInputFocused by remember { mutableStateOf(false) }
        var isEditSaveFocused by remember { mutableStateOf(false) }
        var isEditCancelFocused by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            delay(150)
            try {
                editInputFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }

        AlertDialog(
            onDismissRequest = { if (!isEditSubmitting) editingComment = null },
            title = {
                Text("Edit Comment", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = editedText,
                        onValueChange = { if (it.length <= 500) editedText = it },
                        placeholder = { Text("Edit your comment...", color = Color.Gray) },
                        minLines = 3,
                        maxLines = 5,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.Red,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedLabelColor = Color.Red,
                            unfocusedLabelColor = Color.Gray
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(editInputFocusRequester)
                            .onFocusChanged { isEditInputFocused = it.isFocused }
                            .then(
                                if (isRealTV && isEditInputFocused) {
                                    Modifier.border(2.dp, Color.White, RoundedCornerShape(4.dp))
                                } else Modifier
                            )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${editedText.length}/500",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editedText.isNotBlank() && !isEditSubmitting) {
                            isEditSubmitting = true
                            viewModel.editComment(target.id, editedText) { success, err ->
                                isEditSubmitting = false
                                if (success) {
                                    editingComment = null
                                } else {
                                    Toast.makeText(context, err ?: "Failed to edit comment. Please verify Supabase update policy.", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    enabled = !isEditSubmitting && editedText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .focusRequester(editSaveFocusRequester)
                        .onFocusChanged { isEditSaveFocused = it.isFocused }
                        .graphicsLayer {
                            scaleX = if (isEditSaveFocused) 1.05f else 1f
                            scaleY = if (isEditSaveFocused) 1.05f else 1f
                        }
                        .border(
                            border = if (isEditSaveFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent),
                            shape = RoundedCornerShape(8.dp)
                        )
                ) {
                    if (isEditSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { editingComment = null },
                    enabled = !isEditSubmitting,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .focusRequester(editCancelFocusRequester)
                        .onFocusChanged { isEditCancelFocused = it.isFocused }
                        .graphicsLayer {
                            scaleX = if (isEditCancelFocused) 1.05f else 1f
                            scaleY = if (isEditCancelFocused) 1.05f else 1f
                        }
                        .border(
                            border = if (isEditCancelFocused) BorderStroke(1.5.dp, Color.White.copy(alpha = 0.8f)) else BorderStroke(0.dp, Color.Transparent),
                            shape = RoundedCornerShape(8.dp)
                        )
                ) {
                    Text("Cancel", color = if (isEditCancelFocused) Color.White else Color.Gray)
                }
            },
            containerColor = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(12.dp)
        )
    }

    // Delete Confirmation Dialog
    deletingComment?.let { target ->
        val deleteCancelFocusRequester = remember { FocusRequester() }
        val deleteConfirmFocusRequester = remember { FocusRequester() }
        var isDeleteCancelFocused by remember { mutableStateOf(false) }
        var isDeleteConfirmFocused by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            delay(150)
            try {
                deleteCancelFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }

        AlertDialog(
            onDismissRequest = { if (!isDeleteSubmitting) deletingComment = null },
            title = {
                Text("Delete Comment", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Are you sure you want to delete this comment? This action cannot be undone.", color = Color.White.copy(alpha = 0.85f))
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!isDeleteSubmitting) {
                            isDeleteSubmitting = true
                            viewModel.deleteComment(target.id) { success, err ->
                                isDeleteSubmitting = false
                                if (success) {
                                    deletingComment = null
                                } else {
                                    Toast.makeText(context, err ?: "Failed to delete comment. Please verify Supabase delete policy.", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    enabled = !isDeleteSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .focusRequester(deleteConfirmFocusRequester)
                        .onFocusChanged { isDeleteConfirmFocused = it.isFocused }
                        .graphicsLayer {
                            scaleX = if (isDeleteConfirmFocused) 1.05f else 1f
                            scaleY = if (isDeleteConfirmFocused) 1.05f else 1f
                        }
                        .border(
                            border = if (isDeleteConfirmFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent),
                            shape = RoundedCornerShape(8.dp)
                        )
                ) {
                    if (isDeleteSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deletingComment = null },
                    enabled = !isDeleteSubmitting,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .focusRequester(deleteCancelFocusRequester)
                        .onFocusChanged { isDeleteCancelFocused = it.isFocused }
                        .graphicsLayer {
                            scaleX = if (isDeleteCancelFocused) 1.05f else 1f
                            scaleY = if (isDeleteCancelFocused) 1.05f else 1f
                        }
                        .border(
                            border = if (isDeleteCancelFocused) BorderStroke(1.5.dp, Color.White.copy(alpha = 0.8f)) else BorderStroke(0.dp, Color.Transparent),
                            shape = RoundedCornerShape(8.dp)
                        )
                ) {
                    Text("Cancel", color = if (isDeleteCancelFocused) Color.White else Color.Gray)
                }
            },
            containerColor = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
fun CommentItem(
    comment: Comment, 
    isRealTV: Boolean,
    isMyComment: Boolean = false,
    onEditClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {}
) {
    var isFocused by remember { mutableStateOf(false) }
    val avatarColor = remember(comment.userName) {
        val colors = listOf(
            Color(0xFFE50914), // Netflix Red
            Color(0xFF1E88E5), // Blue
            Color(0xFF43A047), // Green
            Color(0xFFFB8C00), // Orange
            Color(0xFF8E24AA), // Purple
            Color(0xFF00ACC1), // Cyan
            Color(0xFF3949AB)  // Indigo
        )
        val hash = kotlin.math.abs(comment.userName.hashCode())
        colors[hash % colors.size]
    }

    val initial = remember(comment.userName) {
        comment.userName.trim().take(1).uppercase().ifEmpty { "U" }
    }

    val timeFormatted = remember(comment.createdAt) {
        formatCommentTime(comment.createdAt)
    }

    Surface(
        color = if (isFocused) Color(0xFF222222) else Color(0xFF121212),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            if (isRealTV && isFocused) 2.dp else 1.dp,
            if (isRealTV && isFocused) Color.White else Color.White.copy(alpha = 0.06f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .graphicsLayer {
                scaleX = if (isRealTV && isFocused) 1.01f else 1f
                scaleY = if (isRealTV && isFocused) 1.01f else 1f
            }
            .then(if (isRealTV) Modifier.focusable() else Modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(avatarColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = comment.userName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        if (isMyComment) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = Color.Red.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "YOU",
                                    color = Color.Red,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = timeFormatted,
                            color = Color.Gray,
                            fontSize = 11.sp
                        )

                        if (isMyComment) {
                            Spacer(modifier = Modifier.width(8.dp))

                            var isEditFocused by remember { mutableStateOf(false) }
                            IconButton(
                                onClick = onEditClick,
                                modifier = Modifier
                                    .size(28.dp)
                                    .onFocusChanged { isEditFocused = it.isFocused }
                                    .border(
                                        border = if (isEditFocused) BorderStroke(1.5.dp, Color.White) else BorderStroke(0.dp, Color.Transparent),
                                        shape = CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Comment",
                                    tint = if (isEditFocused) Color.White else Color.Gray,
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            var isDeleteFocused by remember { mutableStateOf(false) }
                            IconButton(
                                onClick = onDeleteClick,
                                modifier = Modifier
                                    .size(28.dp)
                                    .onFocusChanged { isDeleteFocused = it.isFocused }
                                    .border(
                                        border = if (isDeleteFocused) BorderStroke(1.5.dp, Color.Red) else BorderStroke(0.dp, Color.Transparent),
                                        shape = CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Comment",
                                    tint = if (isDeleteFocused) Color.Red else Color.Gray,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = comment.comment,
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

fun formatCommentTime(isoString: String): String {
    if (isoString.isBlank()) return "Just now"
    return try {
        val clean = isoString.replace("Z", "+0000").replace(Regex("""\.\d+"""), "")
        val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", java.util.Locale.US)
        val date = format.parse(clean) ?: return isoString.take(10)
        val diffMs = System.currentTimeMillis() - date.time
        val minutes = diffMs / (60 * 1000)
        val hours = minutes / 60
        val days = hours / 24
        when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days < 30 -> "${days}d ago"
            else -> "${days / 30}mo ago"
        }
    } catch (e: Exception) {
        isoString.take(10)
    }
}

@Composable
fun InfoItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    if (text.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = text, color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun DetailSectionHeader(title: String, badge: String? = null) {
    Spacer(modifier = Modifier.height(32.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (badge != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Surface(color = Color.DarkGray, shape = CircleShape) {
                Text(text = badge, color = Color.LightGray, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
fun EpisodeRow(index: Int, episode: com.duta.movie.model.Episode, isWatched: Boolean = false, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.03f else 1f)
    Surface(
        color = if (isFocused) Color(0xFF222222) else Color(0xFF111111),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .onFocusChanged { isFocused = it.isFocused }
            .border(border = if (isFocused) BorderStroke(3.dp, Color.Red) else BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), shape = RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .focusable(),
        border = null
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = index.toString().padStart(2, '0'), color = if (isWatched) Color.Red.copy(alpha = 0.5f) else Color.Red, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, modifier = Modifier.width(32.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (episode.season.isNotEmpty()) {
                    Text(text = episode.season, color = if (isWatched) Color.Red.copy(alpha = 0.5f) else Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 2.dp))
                }
                Text(text = episode.name, color = if (isWatched) Color.Gray else Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (episode.date.isNotEmpty()) {
                    Text(text = episode.date, color = Color.Gray.copy(alpha = if (isWatched) 0.5f else 1f), fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
            if (isWatched) {
                Icon(Icons.Default.CheckCircle, contentDescription = stringResource(R.string.watched), tint = Color.Green.copy(alpha = 0.6f), modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(12.dp))
            }
            Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color.White.copy(alpha = if (isWatched) 0.3f else 0.7f), modifier = Modifier.size(28.dp))
        }
    }
}
