package com.duta.movie.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import android.util.Log
import android.util.TypedValue
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.zIndex
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import com.google.android.gms.cast.framework.CastContext
import androidx.media3.common.*
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.duta.movie.R
import com.duta.movie.model.Episode
import com.duta.movie.model.Video
import com.duta.movie.model.VideoServer
import com.duta.movie.util.NetworkConfig
import com.duta.movie.LocalPipMode
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.regex.Pattern

fun Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

data class WebPlayerState(
    val isPlaying: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L
)

enum class GestureType { BRIGHTNESS, VOLUME }

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    videoId: String,
    serverUrl: String? = null,
    viewModel: VideoViewModel = hiltViewModel(),
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val isTV = remember { context.packageManager.hasSystemFeature("android.software.leanback") }
    
    // Ensure orientation and system bars are reset when leaving the player
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        onDispose {
            activity?.let { act ->
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                val controller = WindowCompat.getInsetsController(act.window, act.window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val scope = rememberCoroutineScope()
    val video: Video? by viewModel.videoMetadata.collectAsStateWithLifecycle()
    val extractedUrl: String? by viewModel.extractedUrl.collectAsStateWithLifecycle()
    val isLoading: Boolean by viewModel.isLoading.collectAsStateWithLifecycle()
    val error: String? by viewModel.error.collectAsStateWithLifecycle()
    val lastReferer: String? by viewModel.lastReferer.collectAsStateWithLifecycle()
    val lastCookies: String? by viewModel.lastCookies.collectAsStateWithLifecycle()
    val forceWebViewHosts by viewModel.forceWebViewHosts.collectAsStateWithLifecycle()
    val isResolvingState by viewModel.isResolving.collectAsStateWithLifecycle()
    val resolutionProgress by viewModel.resolutionProgress.collectAsStateWithLifecycle()
    val resolutionLog by viewModel.resolutionLog.collectAsStateWithLifecycle()
    val isSafeModeEnabled: Boolean by viewModel.isSafeModeEnabled.collectAsStateWithLifecycle()
    val currentEpUrl by viewModel.currentServerUrl.collectAsStateWithLifecycle()
    val currentEpisode by viewModel.currentEpisode.collectAsStateWithLifecycle()
    val effectiveProgressId = remember(videoId, currentEpisode) {
        if (currentEpisode != null) {
            val slug = com.duta.movie.util.VideoExtractor.extractStableId(currentEpisode!!.url)
            "${videoId}_ep_${slug}"
        } else {
            videoId
        }
    }
    val savedProgress by viewModel.getVideoProgress(effectiveProgressId).collectAsState(initial = 0L)
    val shouldSuppressResume: Boolean by viewModel.shouldSuppressResume.collectAsStateWithLifecycle()
    
    val config = LocalConfiguration.current
    val isLandscape = config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    var isFullscreen by remember { mutableStateOf(isLandscape) }
    
    LaunchedEffect(isLandscape) {
        isFullscreen = isLandscape
    }

    var isVideoReady by remember { mutableStateOf(false) }
    var isRevealed by remember { mutableStateOf(false) }
    LaunchedEffect(isVideoReady) {
        Log.d("VideoPlayerScreen", "isVideoReady state changed to: $isVideoReady")
    }
    
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showSyncDialog by remember { mutableStateOf(false) }
    var autoRetryCount by remember(videoId, serverUrl) { mutableIntStateOf(0) }
    
    // Safety Check: Prevent playback of restricted content in Safe Mode
    LaunchedEffect(video, isSafeModeEnabled) {
        video?.let {
            if (isSafeModeEnabled && viewModel.isRestricted(it)) {
                Log.w("VideoPlayerScreen", "Kicked out: Safe Mode restriction triggered for ${it.title}")
                onBackClick()
            }
        }
    }

    var showControls by remember { mutableStateOf(true) }
    var isAnyControlFocused by remember { mutableStateOf(false) }
    val controlsFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    
    var showServerDialog by remember { mutableStateOf(false) }
    var showResumeDialog by remember { mutableStateOf(false) }
    var showResolutionLog by remember { mutableStateOf(false) }
    var playerErrorMessage by remember { mutableStateOf<String?>(null) }
    
    val webViewRef = remember { mutableStateOf<android.webkit.WebView?>(null) }
    val webPlayerState = remember { mutableStateOf(WebPlayerState()) }
    
    var qualityTracks by remember { mutableStateOf<List<VideoQualityTrack>>(emptyList()) }
    var selectedQualityGroupIndex by remember { mutableIntStateOf(-1) }
    var seekFeedback by remember { mutableStateOf<String?>(null) }
    val resolutionKey = remember(videoId, serverUrl) { "$videoId-$serverUrl" }

    val subtitles by viewModel.subtitles.collectAsStateWithLifecycle()
    val isSubtitleLoading by viewModel.isSubtitleLoading.collectAsStateWithLifecycle()
    val selectedSubtitle by viewModel.selectedSubtitle.collectAsStateWithLifecycle()
    val defaultSubtitleLanguage by viewModel.defaultSubtitleLanguage.collectAsStateWithLifecycle()
    val subtitleError by viewModel.subtitleError.collectAsStateWithLifecycle()
    val subtitleOffset by viewModel.subtitleOffset.collectAsStateWithLifecycle()
    
    val subFirstItemFocusRequester = remember { FocusRequester() }
    LaunchedEffect(showSubtitleDialog) {
        if (showSubtitleDialog) {
            delay(300)
            try { subFirstItemFocusRequester.requestFocus() } catch(_: Exception) {}
        }
    }
    
    val isInPip = LocalPipMode.current

    val preferredLanguageCode = remember(defaultSubtitleLanguage, selectedSubtitle) {
        val lang = (selectedSubtitle?.language ?: defaultSubtitleLanguage).lowercase()
        when {
            (((lang.contains("indonesia")) || lang == "id" || lang == "ind" || (lang.contains("indo")))) -> "id"
            (lang.contains("malay") || lang == "ms" || lang == "msa") -> "ms"
            else -> "en"
        }
    }

    var brightness by remember { mutableFloatStateOf(0.5f) }
    var volume by remember { mutableFloatStateOf(0.5f) }
    var gestureType by remember { mutableStateOf<GestureType?>(null) }
    var currentCues by remember { mutableStateOf<List<Cue>>(emptyList()) }
    
    // Auto-rotate and hide system bars when fullscreen is toggled
    LaunchedEffect(isFullscreen) {
        val activity = context.findActivity() ?: return@LaunchedEffect
        val window = activity.window
        val view = window.decorView
        val controller = WindowCompat.getInsetsController(window, view)

        if (isFullscreen) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    var isUserForcingWebView by remember { mutableStateOf(false) }

    val useWebView = remember(extractedUrl, isUserForcingWebView) {
        if (extractedUrl == null) return@remember true
        if (isUserForcingWebView) return@remember true
        val url = extractedUrl!!
        val isDirect = com.duta.movie.util.VideoExtractor.isDirectVideoUrl(url)
        val isJsOnly = com.duta.movie.util.VideoExtractor.isJsOnlyHost(url)
        
        // OWL'S EYE: If it's a direct stream URL (m3u8/mp4), ALWAYS use ExoPlayer
        // regardless of the host, as WebView cannot play raw manifests.
        if (isDirect) return@remember false
        
        // If it's a JS-Only gateway page, force WebView.
        if (isJsOnly) return@remember true
        
        true // Default to WebView for anything else (security first)
    }

    val nukerScript = com.duta.movie.util.Nuker.SCRIPT

    fun injectNuker(view: android.webkit.WebView?) {
        view?.evaluateJavascript(nukerScript, null)
    }

    LaunchedEffect(subtitleError) {
        subtitleError?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); viewModel.clearSubtitleError() }
    }

    var isFinishing by remember { mutableStateOf(false) }

    LaunchedEffect(videoId, video?.title) { video?.title?.let { viewModel.fetchSubtitles(it) } }

    LaunchedEffect(isVideoReady) { 
        if (isVideoReady) {
            // OWL'S EYE: Only show resume dialog if we have meaningful progress (> 5s)
            // AND we haven't already suppressed it for this session.
            if (savedProgress > 5000L && !shouldSuppressResume) { 
                showResumeDialog = true 
            }
            // Reset suppression for future manual reloads
            viewModel.setSuppressResume(false)
        } 
    }

    LaunchedEffect(showControls, isVideoReady, isAnyControlFocused) { 
        if (showControls && isVideoReady && !isAnyControlFocused) { 
            delay(8000)
            showControls = false 
        } 
    }

    LaunchedEffect(showControls) {
        if (showControls) {
            delay(100) // Small delay for AnimatedVisibility to stabilize
            try { playPauseFocusRequester.requestFocus() } catch (_: Exception) {}
        } else {
            // Re-focus the main container so it can catch DPAD keys for seeking while controls are hidden
            try { controlsFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    LaunchedEffect(seekFeedback) { if (seekFeedback != null) { delay(700); seekFeedback = null } }

    val exoPlayer: ExoPlayer = remember {
        val okHttpDataSourceFactory = OkHttpDataSource.Factory(NetworkConfig.permissiveOkHttpClient).setUserAgent(NetworkConfig.SHARED_USER_AGENT)
        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, okHttpDataSourceFactory)
        
        // Manual MediaSource creation to ensure HLS is ALWAYS recognized
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)

        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
                val selectors = androidx.media3.exoplayer.mediacodec.MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
                if (mimeType.contains("av01") || mimeType.contains("avc") || mimeType.contains("h264") || mimeType.contains("hevc") || mimeType.contains("h265")) {
                    selectors.sortedByDescending { it.hardwareAccelerated }
                } else { selectors }
            }
        val loadControl = DefaultLoadControl.Builder().setBufferDurationsMs(50_000, 120_000, 2_500, 5_000).setPrioritizeTimeOverSizeThresholds(true).build()
        ExoPlayer.Builder(context, renderersFactory).setMediaSourceFactory(mediaSourceFactory).setLoadControl(loadControl).setSeekForwardIncrementMs(10_000).setSeekBackIncrementMs(10_000).build().apply {
            videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            trackSelectionParameters = trackSelectionParameters.buildUpon().setPreferredTextLanguage("id").setSelectUndeterminedTextLanguage(true).build()
        }
    }

    val castPlayer: CastPlayer? = remember {
        try {
            val castContext = CastContext.getSharedInstance(context)
            CastPlayer(castContext)
        } catch (e: Exception) {
            Log.e("VideoPlayerScreen", "CastPlayer init error", e)
            null
        }
    }

    var isCasting by remember { mutableStateOf(false) }
    
    LaunchedEffect(castPlayer) {
        castPlayer?.setSessionAvailabilityListener(object : SessionAvailabilityListener {
            override fun onCastSessionAvailable() {
                isCasting = true
                // AUTO-OPTIMIZE: If current server is Hgcloud (strict), try to switch to a Cast-friendly server
                extractedUrl?.let { url ->
                    if (url.contains("hanerix") || url.contains("audinifer") || url.contains("hgcloud") || url.contains("masuk")) {
                        viewModel.findCastFriendlyServer(videoId)?.let { betterServer ->
                            Log.i("VideoPlayerScreen", "Switching to Cast-Friendly server: $betterServer")
                            val isEpisodeUrl = betterServer.let { (it.contains("/eps/") || it.contains("/episode/") || it.contains("-episode-") || it.contains("/ep-")) && !it.contains("player=") && !it.contains("mirror=") }
                            if (video?.isSeries == true || isEpisodeUrl) {
                                viewModel.playTVSeries(videoId, betterServer, forceReset = false)
                            } else {
                                viewModel.playMovie(videoId, betterServer, forceReset = false)
                            }
                        }
                    }
                }
            }
            override fun onCastSessionUnavailable() { isCasting = false }
        })
        castPlayer?.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e("VideoPlayerScreen", "Cast Player Error: ${error.errorCodeName}", error)
                scope.launch {
                    playerErrorMessage = "The TV failed to load this stream. Try switching to another server (e.g. Mirror-VIP) then cast again."
                }
            }
        })
    }

    val currentPlayer = remember(isCasting, useWebView) {
        if (isCasting && castPlayer != null) castPlayer else exoPlayer
    }

    // OWL'S EYE: Stall Guard & Progress Saver
    LaunchedEffect(isVideoReady, useWebView, isCasting) {
        if (isVideoReady) {
            var lastPos = -1L
            var stallCount = 0
            val isTrending = video?.title?.contains("Agent Kim", ignoreCase = true) == true || 
                             video?.title?.contains("Manager Kim", ignoreCase = true) == true
            
            while (true) {
                // High-demand items (Agent Kim) get faster stall detection (3s check vs 4s)
                delay(if (isTrending) 3000 else 4000) 
                val currentPos = if (useWebView) webPlayerState.value.position else exoPlayer.currentPosition
                val isPlaying = if (useWebView) webPlayerState.value.isPlaying else exoPlayer.isPlaying
                val duration = if (useWebView) webPlayerState.value.duration else exoPlayer.duration

                if (isPlaying && duration > 0) {
                    viewModel.saveVideoProgress(videoId, currentPos, duration, currentEpUrl)
                    
                    // Stall Detection: If position hasn't moved for 2 cycles
                    if (currentPos == lastPos && lastPos != -1L) {
                        stallCount++
                        val maxStallCycles = if (isTrending) 2 else 2 // 6s for trending vs 8s for normal
                        if (stallCount >= maxStallCycles) {
                            Log.w("VideoPlayer", "Stall Guard: Trending content stuck at $currentPos. Rotating mirror.")
                            viewModel.resolveNextServer(videoId, extractedUrl)
                            break
                        }
                    } else {
                        stallCount = 0
                    }
                    lastPos = currentPos
                }
            }
        }
    }

    val currentUrl = remember { mutableStateOf<String?>(null) }
    val currentSub = remember { mutableStateOf<String?>(null) }
    val currentPreferredLang = rememberUpdatedState(preferredLanguageCode)
    val currentSelectedSubtitle = rememberUpdatedState(selectedSubtitle)
    val currentVideoId = rememberUpdatedState(videoId)
    val currentServerUrl = rememberUpdatedState(serverUrl)

    // Resolution logic - Use a state-backed key to avoid unnecessary resets
    // We only trigger resolution if the parameters CHANGE, not on internal rotation
    val currentServerUrlFromVm by viewModel.currentServerUrl.collectAsStateWithLifecycle()
    LaunchedEffect(resolutionKey) {
        // CRITICAL: Stop ExoPlayer before starting a new resolution
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        
        // Clear WebView cookies/cache for fresh mirror sessions
        android.webkit.WebStorage.getInstance().deleteAllData()
        android.webkit.CookieManager.getInstance().removeAllCookies(null)
        android.webkit.CookieManager.getInstance().flush()
        
        val isEpisodeUrl = serverUrl?.let { (it.contains("/eps/") || it.contains("/episode/") || it.contains("-episode-") || it.contains("/ep-")) && !it.contains("player=") && !it.contains("mirror=") } ?: false
        if (video?.isSeries == true || isEpisodeUrl) {
            viewModel.playTVSeries(videoId, serverUrl, forceReset = true)
        } else {
            viewModel.playMovie(videoId, serverUrl, forceReset = true)
        }

        viewModel.addToRecentlyWatched(videoId)
        isVideoReady = false; showControls = true
        isFinishing = false
        playerErrorMessage = null
    }

    // OWL'S EYE 6.5: Next Episode Transition Watchdog
    // Only engage blackout if we aren't already playing a stable stream.
    LaunchedEffect(extractedUrl) {
        if (extractedUrl != null) {
            // OWL'S EYE: Atomic Readiness Lock
            // Once the movie is playing, ignore minor background updates from the same server.
            // This prevents the loading screen from flickering back on during playback.
            if (isVideoReady) {
                val isSameHost = currentUrl.value?.let { old ->
                    try { Uri.parse(old).host == Uri.parse(extractedUrl).host } catch(_: Exception) { false }
                } ?: false
                if (isSameHost) return@LaunchedEffect
            }

            // Reset retry count on every URL change
            autoRetryCount = 0
            
            val isInternalRedirect = currentUrl.value?.let { old ->
                try {
                    val oldHost = Uri.parse(old).host
                    val newHost = Uri.parse(extractedUrl).host
                    oldHost == newHost && oldHost != null
                } catch(_: Exception) { false }
            } ?: false

            if (!isVideoReady || !isInternalRedirect) {
                Log.i("VideoPlayerScreen", "New source URL detected. Engaging blackout guard.")
                isVideoReady = false
                playerErrorMessage = null
                // OWL'S EYE: Stop any lingering audio immediately
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
            }
        }
    }

    // Secondary listener for internal server rotation
    LaunchedEffect(currentServerUrlFromVm) {
        if (currentServerUrlFromVm != null && currentServerUrlFromVm != serverUrl) {
             Log.i("VideoPlayerScreen", "Internal server rotation detected: $currentServerUrlFromVm")
             // Reset player state but DON'T call resolveVideoUrl as it was already called by rotation logic
             isVideoReady = false
             playerErrorMessage = null
        }
    }

    LaunchedEffect(useWebView, isCasting) {
        if (useWebView) {
            exoPlayer.pause()
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        } else {
            // OWL'S EYE: Stop and clear WebView when transitioning to ExoPlayer
            webViewRef.value?.let { wv ->
                wv.stopLoading()
                wv.loadUrl("about:blank")
            }
        }
    }

    // OWL'S EYE 6.4: Pure Blackout Handshake
    // Ensures the screen stays 100% black until the video is verified playing.
    LaunchedEffect(useWebView, extractedUrl, isVideoReady) {
        if (!isVideoReady && extractedUrl != null) {
            var stuckCount = 0
            val maxStuck = 15 // 15s window
            
            while(!isVideoReady && stuckCount < maxStuck) {
                if (useWebView) {
                    webViewRef.value?.let { wv -> injectNuker(wv) }
                    // EMERGENCY HATCH: Force reveal after 8s of black screen
                    if (stuckCount >= 8 && !isRevealed) {
                        Log.i("VideoPlayer", "Emergency Hatch: Forcing reveal after 8s blackout.")
                        isRevealed = true
                    }
                }
                
                val playbackState = if (!useWebView) exoPlayer.playbackState else Player.STATE_IDLE
                if (playbackState == Player.STATE_READY || (useWebView && webPlayerState.value.isPlaying)) {
                    if (stuckCount % 2 == 0) stuckCount-- 
                }

                stuckCount++
                delay(1000)
            }
            
            // OWL'S EYE: Anti-Freeze Rotation
            // If we are still black or not ready after 15s, something is frozen - rotate server.
            if (!isVideoReady && !isFinishing) {
                Log.w("VideoPlayer", "Mirror Timeout reached. Auto-Rotating...")
                scope.launch {
                    extractedUrl?.let { viewModel.notifyPlaybackFailure(it) }
                    viewModel.resolveNextServer(videoId, currentServerUrlFromVm)
                }
            }
        }
    }

    LaunchedEffect(extractedUrl, selectedSubtitle, currentPlayer) {
        val url = extractedUrl ?: return@LaunchedEffect
        val sub = selectedSubtitle
        if (!useWebView || isCasting) {
            val effectiveReferer = lastReferer ?: "${com.duta.movie.util.VideoExtractor.getBaseUrl()}/"
            
            // Sync headers to OkHttp client
            lastCookies?.let { NetworkConfig.injectCookies(url, it) }
            NetworkConfig.updateSessionReferer(url, effectiveReferer)
            
            val mediaItem = MediaItem.Builder()
                .setUri(url)
                .setMediaId(videoId)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(video?.title ?: "Movie")
                        .setArtist(video?.actresses?.firstOrNull() ?: "Owl's Eye")
                        .setArtworkUri(video?.thumbnailUrl?.toUri())
                        .setMediaType(MediaMetadata.MEDIA_TYPE_MOVIE)
                        .build()
                )
                .setRequestMetadata(
                    MediaItem.RequestMetadata.Builder()
                        .setMediaUri(url.toUri())
                        .build()
                )
                .apply {
                    val lowUrl = url.lowercase()
                    // MimeType optimization for both local and Chromecast
                    if (lowUrl.contains(".m3u8") || lowUrl.contains(".txt") || 
                        lowUrl.contains("/hls/") || lowUrl.contains("/stream/") ||
                        lowUrl.contains(".sbs") || lowUrl.contains(".online") ||
                        lowUrl.contains(".xyz") || lowUrl.contains(".site")) {
                        // application/x-mpegURL is the standard for HLS on both Media3 and Cast
                        setMimeType(MimeTypes.APPLICATION_M3U8)
                    } else if (lowUrl.contains(".mp4")) {
                        setMimeType(MimeTypes.VIDEO_MP4)
                    }
                }
                .setSubtitleConfigurations(
                    sub?.let { s ->
                        val mimeType = if (s.url.contains(".vtt")) MimeTypes.TEXT_VTT 
                                      else if (s.url.contains(".ass") || s.url.contains(".ssa")) MimeTypes.TEXT_SSA 
                                      else MimeTypes.APPLICATION_SUBRIP
                        listOf(
                            MediaItem.SubtitleConfiguration.Builder(s.url.toUri())
                                .setMimeType(mimeType)
                                .setLanguage(currentPreferredLang.value)
                                .setLabel(s.label)
                                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_FORCED)
                        )
                    } ?: emptyList()
                )
                .build()

            if (isCasting && castPlayer != null) {
                // If we just started casting, transfer position from ExoPlayer
                val currentPos = if (exoPlayer.isPlaying || exoPlayer.currentPosition > 0) exoPlayer.currentPosition else 0L
                exoPlayer.pause()
                
                castPlayer.setMediaItem(mediaItem, currentPos)
                castPlayer.prepare()
                castPlayer.play()
            } else {
                // FOR LOCAL PLAYBACK: Use specialized HlsMediaSource to fix "Unrecognized format" errors
                // Only match explicit HLS indicators — NOT gateway domains like abyss/bond
                val isHls = url.lowercase().contains(".m3u8") || url.lowercase().contains(".txt") || 
                            url.lowercase().contains("/hls/") || url.lowercase().contains("/stream/")
                
                if (isHls) {
                    val hlsMediaSource = HlsMediaSource.Factory(
                        OkHttpDataSource.Factory(NetworkConfig.permissiveOkHttpClient).setUserAgent(NetworkConfig.SHARED_USER_AGENT)
                    ).createMediaSource(mediaItem)
                    
                    exoPlayer.setMediaSource(hlsMediaSource, /* resetPosition = */ currentUrl.value != url)
                } else {
                    exoPlayer.setMediaItem(mediaItem, /* resetPosition = */ currentUrl.value != url)
                }
                
                if (currentUrl.value != url) {
                    isVideoReady = false
                    currentUrl.value = url
                    currentSub.value = sub?.url
                    currentPlayer.prepare()
                    currentPlayer.playWhenReady = true
                } else if (currentSub.value != sub?.url) {
                    currentSub.value = sub?.url
                    currentPlayer.trackSelectionParameters = currentPlayer.trackSelectionParameters.buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        .build()
                    currentPlayer.prepare()
                }
            }
        }
    }

    }

    DisposableEffect(Unit) {
        viewModel.setPlayerActive(active = true)
        onDispose { 
            viewModel.setPlayerActive(active = false)
            exoPlayer.release()
            castPlayer?.release()
        }
    }

    DisposableEffect(currentPlayer, useWebView, isCasting) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying && (!useWebView || isCasting)) { 
                    isVideoReady = true 
                    extractedUrl?.let { viewModel.notifyPlaybackSuccess(it) }
                }
            }
            override fun onPlaybackStateChanged(state: Int) { 
                val stateName = when(state) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN"
                }
                Log.d("VideoPlayerScreen", "Playback state changed to $stateName (Ready=$isVideoReady, useWebView=$useWebView, isCasting=$isCasting)")

                if (state == Player.STATE_BUFFERING) {
                    // Watchdog for stuck buffering
                    scope.launch {
                        delay(30000) // 30s buffer timeout
                        if (currentPlayer.playbackState == Player.STATE_BUFFERING && !isFinishing) {
                            Log.w("VideoPlayerScreen", "Stuck in BUFFERING for 30s. Rotating.")
                            isFinishing = true
                            viewModel.resolveNextServer(videoId, extractedUrl)
                        }
                    }
                }

                if (state == Player.STATE_READY) {
                    // OWL'S EYE: Zombie Detection
                    // If a "Full Movie" reported duration is less than 5 minutes, it's likely an ad loop or broken mirror.
                    val duration = currentPlayer.duration
                    val isMovie = video?.isSeries == false
                    val isZombie = if (isMovie) {
                        duration in 1..300_000L
                    } else {
                        // For series, be slightly more permissive but still catch 1-3 minute ad loops
                        duration in 1..180_000L
                    }

                    if (isZombie && !extractedUrl.isNullOrEmpty() && !extractedUrl!!.contains("preview")) {
                        Log.w("VideoPlayerScreen", "Owl's Eye: Zombie Stream Detected (${duration/1000}s). Rotating...")
                        isFinishing = true
                        viewModel.notifyPlaybackFailure(extractedUrl!!)
                        viewModel.resolveNextServer(videoId, extractedUrl)
                    }
                }
                if (state == Player.STATE_ENDED) {
                    if (isFinishing) return
                    
                    // CRITICAL: Check if we actually played anything meaningful.
                    // If it ended in less than 30 seconds, it might be a broken link or ad stub.
                    val playedDuration = currentPlayer.currentPosition
                    if (playedDuration < 30000L && autoRetryCount < 3) {
                         Log.w("VideoPlayerScreen", "Playback ended suspiciously early ($playedDuration ms). Rotating server.")
                         isFinishing = true
                         scope.launch { 
                             delay(1000)
                             viewModel.resolveNextServer(videoId, extractedUrl) 
                         }
                         return
                    }

                    isFinishing = true
                    Log.d("VideoPlayerScreen", "Playback ended normally. Triggering autoplay.")
                    val hasNext = viewModel.resolveNextEpisode(videoId)
                    if (!hasNext) {
                        Log.d("VideoPlayerScreen", "Kicked out: No next episode for movie/series end.")
                        onBackClick()
                    } else {
                        isFinishing = false
                    }
                }
            }
            override fun onRenderedFirstFrame() { isVideoReady = true }
            override fun onPlayerError(error: PlaybackException) {
                val message = error.message ?: ""
                val cause = error.cause?.message ?: ""
                Log.e("VideoPlayerScreen", "Player Error: $message | Cause: $cause | URL: ${extractedUrl?.take(60)}")
                
                extractedUrl?.let { viewModel.notifyPlaybackFailure(it) }
                
                val isNetworkError = message.contains("403") || message.contains("404") || message.contains("502") || 
                                     message.contains("Unable to connect") || message.contains("Connection timeout") || 
                                     message.contains("Source error") || message.contains("Response code") ||
                                     cause.contains("403") || cause.contains("502") || cause.contains("timeout") ||
                                     error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                                     error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED

                val is403 = (message.contains("403") || cause.contains("403")) && autoRetryCount == 0
                val isSourceError = message.contains("Source error") || message.contains("Response code: 4")

                if (is403) {
                    autoRetryCount++
                    Log.i("VideoPlayerScreen", "Owl's Eye: Detected 403 Forbidden. Retrying with Header Profile rotation...")
                    scope.launch {
                        delay(1000)
                        val isEpisodeUrl = currentServerUrl.value?.let { (it.contains("/eps/") || it.contains("/episode/") || it.contains("-episode-") || it.contains("/ep-")) && !it.contains("player=") && !it.contains("mirror=") } ?: false
                        if (video?.isSeries == true || isEpisodeUrl) {
                            viewModel.playTVSeries(currentVideoId.value, currentServerUrl.value, forceReset = true)
                        } else {
                            viewModel.playMovie(currentVideoId.value, currentServerUrl.value, forceReset = true)
                        }
                    }
                } else if (isSourceError && autoRetryCount == 0) {
                    autoRetryCount++
                    Log.i("VideoPlayerScreen", "Owl's Eye: ExoPlayer failed with Source Error. Flagging host for learning...")
                    val host = try { android.net.Uri.parse(extractedUrl).host } catch(_: Exception) { null }
                    if (host != null) {
                         viewModel.recordExoPlayerFailure(host)
                    }
                    scope.launch {
                        delay(500)
                        val isEpisodeUrl = currentServerUrl.value?.let { (it.contains("/eps/") || it.contains("/episode/") || it.contains("-episode-") || it.contains("/ep-")) && !it.contains("player=") && !it.contains("mirror=") } ?: false
                        if (video?.isSeries == true || isEpisodeUrl) {
                            viewModel.playTVSeries(currentVideoId.value, currentServerUrl.value, forceReset = true)
                        } else {
                            viewModel.playMovie(currentVideoId.value, currentServerUrl.value, forceReset = true)
                        }
                    }
                } else if (autoRetryCount < 3 && isNetworkError) {
                    autoRetryCount++
                    Log.i("VideoPlayerScreen", "Retrying same server (Attempt $autoRetryCount) due to network error: ${error.errorCode}")
                    scope.launch { 
                        delay(2000)
                        val isEpisodeUrl = currentServerUrl.value?.let { (it.contains("/eps/") || it.contains("/episode/") || it.contains("-episode-") || it.contains("/ep-")) && !it.contains("player=") && !it.contains("mirror=") } ?: false
                        if (video?.isSeries == true || isEpisodeUrl) {
                            viewModel.playTVSeries(currentVideoId.value, currentServerUrl.value, forceReset = false)
                        } else {
                            viewModel.playMovie(currentVideoId.value, currentServerUrl.value, forceReset = false)
                        }
                    } 
                } else {
                    val displayError = when {
                        error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS && message.contains("403") -> "Server access denied (403). Rotating to next mirror..."
                        error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS && message.contains("404") -> "Video file not found (404). Rotating to next mirror..."
                        error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "Network connection failed. Checking other mirrors..."
                        message.contains("timeout", ignoreCase = true) || cause.contains("timeout", ignoreCase = true) -> "Server timed out. Trying a faster mirror..."
                        else -> "Playback error (Code: ${error.errorCode}). Trying next server..."
                    }
                    playerErrorMessage = displayError
                    
                    scope.launch {
                        delay(2500)
                        viewModel.resolveNextServer(currentVideoId.value, currentServerUrl.value)
                    }
                }
            }
            override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) { currentCues = cueGroup.cues }
            override fun onTracksChanged(tracks: Tracks) {
                val textTracks = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                var targetGroup: Tracks.Group? = null
                var targetTrackIndex = 0
                val sel = currentSelectedSubtitle.value
                if (sel != null) {
                    textTracks.forEach { group ->
                        for (i in 0 until group.length) { if (group.getTrackFormat(i).label == sel.label) { targetGroup = group; targetTrackIndex = i } }
                    }
                }
                if (targetGroup == null && sel != null) {
                    val targetLang = if (sel.language.lowercase().contains("indonesia")) "id" else if (sel.language.lowercase().contains("malay")) "ms" else null
                    targetGroup = textTracks.find { group -> (0 until group.length).any { i -> val format = group.getTrackFormat(i); (format.language == targetLang || format.language == currentPreferredLang.value || format.language == "id" || format.language == "ms") } }
                    targetTrackIndex = 0
                }
                if (targetGroup != null) {
                    currentPlayer.trackSelectionParameters = currentPlayer.trackSelectionParameters.buildUpon().setOverrideForType(TrackSelectionOverride(targetGroup.mediaTrackGroup, targetTrackIndex)).build()
                }
                qualityTracks = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }.flatMapIndexed { gIdx, group -> 
                    (0 until group.length).map { tIdx -> 
                        val format = group.getTrackFormat(tIdx)
                        VideoQualityTrack("${format.height}p", gIdx, tIdx) 
                    } 
                }.sortedByDescending { it.label.replace("p", "").toIntOrNull() ?: 0 }
            }
        }
        currentPlayer.addListener(listener)
        onDispose { 
            currentPlayer.removeListener(listener)
        }
    }

    var isTransitionComplete by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(500) // Wait for navigation animation to finish
        isTransitionComplete = true
    }

    var showManualPlay by remember { mutableStateOf(false) }
    LaunchedEffect(isResolvingState, isVideoReady) {
        if (!isResolvingState && !isVideoReady) {
            delay(8000)
            showManualPlay = true
            isRevealed = true // Reveal error pages or interactive mirrors
        } else {
            showManualPlay = false
        }
    }

    VideoPlayerContent(
        extractedUrl = extractedUrl,
        isLoading = isLoading,
        error = error,












































        onForceWebViewClick = { 
            isUserForcingWebView = true
            playerErrorMessage = null
            val isEpisodeUrl = serverUrl?.let { (it.contains("/eps/") || it.contains("/episode/") || it.contains("-episode-") || it.contains("/ep-")) && !it.contains("player=") && !it.contains("mirror=") } ?: false
            if (video?.isSeries == true || isEpisodeUrl) {
                viewModel.playTVSeries(videoId, serverUrl, forceReset = false)
            } else {
                viewModel.playMovie(videoId, serverUrl, forceReset = false)
            }
        },
        onNextServerClick = { viewModel.resolveNextServer(videoId, null) },
        onPlayerStateChange = { state, time, duration ->
            webPlayerState.value = WebPlayerState(state == 1, (time * 1000).toLong(), (duration * 1000).toLong())
        },
        onStreamFound = { url, ref, cookies ->
            if (url.lowercase().contains("analytics") || url.lowercase().contains("google-analytics") || url.lowercase().contains("collect") || url.lowercase().contains("pixel")) {
                Log.w("VideoPlayer", "Nuker blocked tracking URL: $url")
            } else {
                viewModel.updateExtractedUrl(videoId, url, ref, cookies)
            }
        },
        onPlaybackSuccess = { sourceUrl ->
            // OWL'S EYE: Domain-based verification to prevent handoff gaps
            val isLegitMatch = try {
                val winnerHost = Uri.parse(sourceUrl).host
                val targetHost = Uri.parse(extractedUrl ?: "").host
                
                // VIP Domain Authority DNA check
                val isVipDomain = sourceUrl.let { u ->
                    val low = u.lowercase()
                    low.contains("hgcloud") || low.contains("indostream") || 
                    low.contains("amt") || low.contains("abyss") || 
                    low.contains("bond") || low.contains("playstream") ||
                    low.contains("veev") || low.contains("iplayer") ||
                    low.contains("morencius")
                }





















































































































































































































































































































































































































































































                onServerListClick = onServerListClick, 
                onSubtitleClick = onSubtitleClick, 
                onSyncClick = onSyncClick,
                onPipClick = onPipClick,
                qualityTracks = qualityTracks, 
                selectedQualityIndex = selectedQualityGroupIndex, 
                onQualitySelect = onQualitySelect,
                onNextEpisodeClick = video?.let { v ->
                    val episodes = v.episodes.filter { ep ->
                        val low = ep.name.lowercase()
                        !low.contains("lihat semua") && !low.contains("see all") && 
                        !low.contains("episode list") && !low.contains("daftar episode") &&
                        !low.contains("next") && !low.contains("prev") && !low.contains("halaman")
                    }
                    val currentEp = currentEpisode
                    val currentIndex = if (currentEp != null) {
                        val slug = com.duta.movie.util.VideoExtractor.extractStableId(currentEp.url)
                        episodes.indexOfFirst { 
                            it.url == currentEp.url || it.id == currentEp.id || 
                            com.duta.movie.util.VideoExtractor.extractStableId(it.url) == slug ||
                            it.url.contains(slug)
                        }
                    } else -1
                    
                    if (currentIndex != -1 && currentIndex < episodes.size - 1) onNextEpisodeClick else null
                },
                playPauseFocusRequester = playPauseFocusRequester
            )
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = (isLoading || isResolving || (!isVideoReady && !isRevealed)) && error == null,
            enter = EnterTransition.None, // OWL'S EYE: Instant cover-up prevents gaps during URL swaps
            exit = fadeOut(tween(800)) // OWL'S EYE: Smooth fade-out reveal
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .zIndex(9999f), 
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = Color.Red, 
                        strokeWidth = if (isTV) 4.dp else 2.dp, 
                        modifier = Modifier.size(if (isTV) 48.dp else 32.dp).alpha(if (isRevealed) 0.3f else 1f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (isResolving) (resolutionProgress ?: "Connecting...") else "Loading...", 
                        color = Color.White.copy(alpha = if (isRevealed) 0.4f else 1f), 
                        fontSize = if (isTV) 16.sp else 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // EMERGENCY CONTROLS: Very low opacity, only after long wait
                var showEmergencyButtons by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    delay(12000)















































































































































































































































                                            onStreamFound(nested, url, null)
                                            return true
                                        }
                                    }
                                    onStreamFound(u, url, null)
                                }
                            }
                        }
                        return true
                    }
                }
                webViewClient = object : android.webkit.WebViewClient() {
                    private var redirectCount = 0
                    private var lastUrl = ""

                    override fun shouldOverrideUrlLoading(v: android.webkit.WebView, r: android.webkit.WebResourceRequest): Boolean {
                        val u = r.url.toString()
                        val low = u.lowercase()
                        val host = r.url.host?.lowercase() ?: ""
                        
                        // OWL'S EYE: Redirect Loop Prevention
                        if (u == lastUrl) {
                            redirectCount++
                            // Increased limit to allow for some site-internal retry logic
                            if (redirectCount > 8) {
                                Log.e("VideoPlayerWebView", "Redirect Loop Detected for: $u")
                                onMirrorDead(url)
                                return true
                            }
                        } else {
                            redirectCount = 0
                            lastUrl = u
                        }

                        if (low.contains("?u=") || low.contains("?link=")) {
                            val param = r.url.getQueryParameter("u") ?: r.url.getQueryParameter("link")
                            if (param != null && (param.contains(".m3u8") || param.contains(".mp4") || param.contains(".txt"))) {
                                onStreamFound(param, u, null); return true
                            }
                        }

                        // OWL'S EYE: Instant Gate Rejection
                        if (low.contains("/login") || low.contains("/register") || low.contains("/welcome") || low.contains("/signin") || low.contains("/signup")) {
                             Log.e("VideoPlayerWebView", "Gate Page Detected: $u")
                             onMirrorDead(url)
                             return true
                        }
                        
                        // Strict navigation control
                        val isSafe = u.contains("hgcloud") || u.contains("hglink") || u.contains("voe") || 
                                    u.contains("abyss") || u.contains("indostream") || 
                                    low.contains("/stream/") || low.contains("/embed/") || 
                                    low.contains("player") || low.contains("mirror") ||
                                    low.contains("amt") || low.contains("haneri") ||
                                    low.contains("morencius") || low.contains("bestcdn") ||
                                    low.contains("ryder") || low.contains("vibuxer") ||
                                    low.contains("audinifer") || low.contains("masuk") ||
                                    low.contains("billofrights") || low.contains("dutamovie") ||
                                    low.contains("pencuri") || low.contains("bokin") ||
                                    low.contains("eddie") || low.contains("seoulschool") ||
                                    low.contains("ladyriders") || low.contains("viatrix") ||
                                    low.contains("ohionewsnow") || low.contains("restaurantesabadell") ||
                                    low.contains("upns.live") || low.contains("upvideo") ||
                                    low.contains("abyssplayer") || low.contains("bondplayer") ||
                                    low.contains("pandalur") || 
                                    low.contains("iamcdn") || low.contains("bondcdn") ||
                                    low.contains("abysscdn") ||
                                    (url != null && u.contains(try { android.net.Uri.parse(url).host ?: "___" } catch(e: Exception) { "___" }))
                        
                        // Block common category/archive pages from hijacking the player
                        val isLandingPage = (low.contains("/category/") || low.contains("/genre/") || 
                                           low.contains("/tag/") || low.contains("/vivamax-sub-indo/")) &&
                                           !u.contains("?") // OWL'S EYE: Don't block query-heavy mirror pages
                        
                        // Strict domain landing page block
                        val isRootDomain = (low.endsWith(".org/") || low.endsWith(".com/") || low.endsWith(".link/") || low.endsWith(".xyz/")) &&
                                          low.split("/").size <= 4 
                        
                        val isMainFrame = r.isForMainFrame
                        if (isMainFrame && (!isSafe || isLandingPage || isRootDomain)) {
                             Log.d("VideoPlayerWebView", "Blocking unsafe mainframe navigation to: $u")
                             return true
                        }
                        
                        return isMainFrame && !isSafe
                    }
                    override fun onPageFinished(v: android.webkit.WebView?, u: String?) {
                        Log.d("VideoPlayerSniffer", "Injecting Nuker SCRIPT via onPageFinished")
                        v?.evaluateJavascript(com.duta.movie.util.Nuker.SCRIPT, null)
                    }
                    override fun shouldInterceptRequest(view: android.webkit.WebView, r: android.webkit.WebResourceRequest): android.webkit.WebResourceResponse? {
                        val u = r.url.toString()
                        val low = u.lowercase()
                        
                        // DEBUG: Log unusual traffic that might be a stream
                        if (low.contains("playlist") || low.contains("chunk") || low.contains(".m3u") || 
                            low.contains("manifest") || low.contains(".mp4") || low.contains("master") ||
                            low.contains("abysscdn") || low.contains("bond-stream") || low.contains("delivery-node")) {
                             Log.d("VideoPlayerInterception", "Candidate: $u")
                        }

                        // TURBO SPEED: Block known ad domains and trackers instantly at network level
                        if (low.contains("analytics") || low.contains("doubleclick") || low.contains("popads") || 
                            low.contains("onclickads") || low.contains("propellerads") || low.contains("exoclick") ||
                            low.contains("googlesyndication") || low.contains("histats") || low.contains("pixel.facebook") ||
                            low.contains("/collect?") || low.contains("google-analytics") || low.contains("redgarto.com") ||
                            low.contains("yandex") || low.contains("metrika") ||
                            low.contains("wantedads") || low.contains("pandalur") || low.contains("adsterra") || 
                            low.contains("a.bestcontent") || low.contains("cpmstar") || 
                            low.contains(".ico") || low.contains(".png") || low.contains(".jpg") || 
                            low.contains(".jpeg") || low.contains(".gif") || low.contains(".svg")) {
                            if (!low.contains("master.m3u8") && !low.contains("index.m3u8") && !low.contains("amt")) {
                                Log.d("VideoPlayerTurbo", "Blocked Network Tracker/Ad/Junk: $u")
                                return android.webkit.WebResourceResponse("text/plain", "UTF-8", null)
                            }
                        }

                        val isStream = (low.contains(".m3u8") || low.contains(".mp4") || low.contains(".txt") || 
                                       low.contains(".m3u") || low.contains("master.json") || low.contains("playlist") ||
                                       low.contains("abysscdn") || low.contains("bond-stream") || low.contains("upvideo.link") ||
                                       low.contains("stream") || low.contains("manifest") || low.contains("/hl/")) && 
                                       !low.contains(".js") && !low.contains(".css")
                        
                        if (isStream && !r.isForMainFrame) {
                            // Wrapper detection for nested mu parameter
                            if (low.contains(".gif") || low.contains("jwplayer")) {
                                val nested = r.url.getQueryParameter("mu") ?: r.url.getQueryParameter("file") ?: r.url.getQueryParameter("url")
                                if (nested != null && (nested.contains(".m3u8") || nested.contains(".mp4"))) {
                                    onStreamFound(nested, r.requestHeaders["Referer"] ?: url, null)
                                    return null
                                }
                            }
                            onStreamFound(u, r.requestHeaders["Referer"] ?: url, null)
                        }
                        return null
                    }
                }
            }
        }, modifier = Modifier.fillMaxSize(), update = { view ->
             if (view.url != url) {
                 // OWL'S EYE: Sticky Referer Lockdown (v6.8)
                 // Hard-coding the referer to the primary site root ensures gateways don't redirect to home.
                 val referer = lastReferer ?: "${com.duta.movie.util.VideoExtractor.getBaseUrl()}/"
                 view.loadUrl(url, mutableMapOf("Referer" to referer))
             }
        }, onRelease = { view -> webViewRef.value = null; view.destroy() })
    }
}

@Composable
fun ServerSelectionDialog(
