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
import androidx.compose.ui.res.stringResource
import com.duta.movie.R
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.duta.movie.model.Episode
import com.duta.movie.model.Video
import com.duta.movie.model.VideoServer
import com.duta.movie.util.CastSubtitleServer
import com.duta.movie.util.DutaCastMediaItemConverter
import com.duta.movie.util.NetworkConfig
import com.duta.movie.util.SubtitleParser
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

fun parseDurationToMillis(raw: String?): Long {
    if (raw.isNullOrBlank() || raw.contains("??") || raw == "0") return 0L
    try {
        val text = raw.lowercase().trim()
        val hourRegex = Regex("""(\d+)\s*(?:h|hr|hour|hours|jam)\s*(?:(\d+)\s*(?:m|min|minute|minutes|menit)?)?""")
        val hourMatch = hourRegex.find(text)
        if (hourMatch != null) {
            val hours = hourMatch.groupValues[1].toLongOrNull() ?: 0L
            val mins = hourMatch.groupValues.getOrNull(2)?.toLongOrNull() ?: 0L
            return (hours * 3600L + mins * 60L) * 1000L
        }
        val minRegex = Regex("""(\d+)\s*(?:m|min|minute|minutes|menit)""")
        val minMatch = minRegex.find(text)
        if (minMatch != null) {
            val mins = minMatch.groupValues[1].toLongOrNull() ?: 0L
            return mins * 60L * 1000L
        }
        if (text.contains(":")) {
            val parts = text.split(":")
            if (parts.size == 3) {
                val h = parts[0].trim().toLongOrNull() ?: 0L
                val m = parts[1].trim().toLongOrNull() ?: 0L
                val s = parts[2].trim().toLongOrNull() ?: 0L
                return (h * 3600L + m * 60L + s) * 1000L
            } else if (parts.size == 2) {
                val m = parts[0].trim().toLongOrNull() ?: 0L
                val s = parts[1].trim().toLongOrNull() ?: 0L
                return (m * 60L + s) * 1000L
            }
        }
        val rawNum = text.filter { it.isDigit() }.toLongOrNull()
        if (rawNum != null && rawNum in 1..600) {
            return rawNum * 60L * 1000L
        }
    } catch (_: Exception) {}
    return 0L
}

enum class GestureType { BRIGHTNESS, VOLUME }

private fun safeEvaluateJavascript(view: android.webkit.WebView?, script: String) {
    if (view == null || view.getTag(R.id.is_destroyed) == true) return
    try {
        view.evaluateJavascript(script, null)
    } catch (e: Throwable) {
        Log.w("VideoPlayerWebView", "Safe evaluateJavascript caught: ${e.message}")
    }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    videoId: String,
    serverUrl: String? = null,
    viewModel: VideoViewModel = hiltViewModel(),
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val isTV = remember { 
        com.duta.movie.util.DeviceUtils.isTvDevice(context)
    }
    
    // Ensure orientation and system bars are reset when leaving the player
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val isMobileLandscapeEnabled = viewModel.isMobileLandscapeEnabled.value
        onDispose {
            activity?.let { act ->
                act.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if (!isTV) {
                    act.requestedOrientation = if (isMobileLandscapeEnabled) {
                        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                } else {
                    act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }
                val controller = WindowCompat.getInsetsController(act.window, act.window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val scope = rememberCoroutineScope()
    val video: Video? by viewModel.videoMetadata.collectAsStateWithLifecycle()
    val fallbackDurationMs = remember(video?.duration) { parseDurationToMillis(video?.duration) }
    val extractedUrl: String? by viewModel.extractedUrl.collectAsStateWithLifecycle()
    val isLoading: Boolean by viewModel.isLoading.collectAsStateWithLifecycle()
    val error: String? by viewModel.error.collectAsStateWithLifecycle()
    val lastReferer: String? by viewModel.lastReferer.collectAsStateWithLifecycle()
    val lastCookies: String? by viewModel.lastCookies.collectAsStateWithLifecycle()
    val forceWebViewHosts by viewModel.forceWebViewHosts.collectAsStateWithLifecycle()
    val isResolvingState by viewModel.isResolving.collectAsStateWithLifecycle()
    val resolutionProgress by viewModel.resolutionProgress.collectAsStateWithLifecycle()
    val resolutionLog by viewModel.resolutionLog.collectAsStateWithLifecycle()
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
    var showEpisodeDialog by remember { mutableStateOf(false) }
    var autoRetryCount by remember(videoId, serverUrl) { mutableIntStateOf(0) }
    
    // Safety Check: Prevent playback of restricted content in Safe Mode
    

    var showControls by remember { mutableStateOf(true) }
    var isAnyControlFocused by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val controlsFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    
    var showServerDialog by remember { mutableStateOf(false) }
    var showResumeDialog by remember { mutableStateOf(false) }
    var showResolutionLog by remember { mutableStateOf(false) }
    var playerErrorMessage by remember { mutableStateOf<String?>(null) }
    
    var pendingRotationResumePosition by remember { mutableStateOf(-1L) }
    var pendingRotationContentKey by remember { mutableStateOf<String?>(null) }
    
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
    val subtitleCues by viewModel.subtitleCues.collectAsStateWithLifecycle()
    
    val subFirstItemFocusRequester = remember { FocusRequester() }
    LaunchedEffect(showSubtitleDialog) {
        if (showSubtitleDialog) {
            delay(300)
            try { subFirstItemFocusRequester.requestFocus() } catch(_: Exception) { kotlinx.coroutines.delay(300); try { subFirstItemFocusRequester.requestFocus() } catch(_: Exception) {} }
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
            if (!isTV) {
                activity.requestedOrientation = if (viewModel.isMobileLandscapeEnabled.value) {
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            } else {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    var isUserForcingWebView by remember { mutableStateOf(false) }

    val useWebView = remember(extractedUrl, isUserForcingWebView) {
        if (extractedUrl == null) return@remember false
        if (isUserForcingWebView) return@remember true
        val url = extractedUrl!!

        val isDirect = com.duta.movie.util.VideoExtractor.isDirectVideoUrl(url)
        // OWL'S EYE: If it's a direct stream URL (m3u8/mp4), ALWAYS use ExoPlayer
        // regardless of the host, as WebView cannot play raw manifests.
        if (isDirect) return@remember false

        val isJsOnly = com.duta.movie.util.VideoExtractor.isJsOnlyHost(url)
        // If it's a JS-Only host or web embed (VOE, HGCloud, YouTube, Bilibili, Dailymotion, etc.), ALWAYS force WebView!
        if (isJsOnly || url.contains("youtube") || url.contains("youtu.be") || 
            url.contains("bilibili.com") || url.contains("bilibili.tv") ||
            url.contains("dailymotion.com") || url.contains("dai.ly")) return@remember true
        
        true // Default to WebView for anything else (security first)
    }


    val isPencuri = remember(videoId, video?.videoUrl, extractedUrl) {
        com.duta.movie.util.VideoExtractor.isPencuriMovie(videoId = videoId, videoUrl = video?.videoUrl, streamUrl = extractedUrl)
    }

    val isYouTube = remember(extractedUrl) {
        extractedUrl?.let { it.contains("youtube") || it.contains("youtu.be") } ?: false
    }

    val isBilibili = remember(extractedUrl) {
        extractedUrl?.let { it.contains("bilibili.com") || it.contains("bilibili.tv") } ?: false
    }

    val isDailymotion = remember(extractedUrl) {
        extractedUrl?.let { it.contains("dailymotion.com") || it.contains("dai.ly") } ?: false
    }

    val nukerScript = remember(isPencuri, isYouTube, isBilibili, isDailymotion) {
        when {
            isYouTube -> com.duta.movie.util.Nuker.getYoutubeScript()
            isBilibili -> com.duta.movie.util.Nuker.getBilibiliScript()
            isDailymotion -> com.duta.movie.util.Nuker.getDailymotionScript()
            isPencuri -> com.duta.movie.util.Nuker.getPencuriScript(400)
            else -> com.duta.movie.util.Nuker.getScript(300)
        }
    }

    fun injectNuker(view: android.webkit.WebView?) {
        safeEvaluateJavascript(view, nukerScript)
    }

    LaunchedEffect(subtitleError) {
        subtitleError?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); viewModel.clearSubtitleError() }
    }

    var isFinishing by remember { mutableStateOf(false) }

    LaunchedEffect(videoId, video?.title) { video?.title?.let { viewModel.fetchSubtitles(it) } }

    val isAnyDialogOpen = showServerDialog || showSubtitleDialog || showSyncDialog || showEpisodeDialog || showResumeDialog
    LaunchedEffect(showControls, isVideoReady, isAnyDialogOpen, lastInteractionTime) { 
        if (showControls && isVideoReady && !isAnyDialogOpen) { 
            delay(if (isTV) 12000L else 8000L)
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
        val cacheDataSourceFactory = com.duta.movie.util.PlayerCacheManager.getCacheDataSourceFactory(context, okHttpDataSourceFactory)
        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, cacheDataSourceFactory)
        
        // Manual MediaSource creation to ensure HLS is ALWAYS recognized
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)

        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
                val selectors = androidx.media3.exoplayer.mediacodec.MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
                if (mimeType.contains("avc") || mimeType.contains("h264")) {
                    // Prioritize hardware decoders first for GPU-accelerated playback; setEnableDecoderFallback(true) handles device-specific crashes automatically
                    selectors.sortedByDescending { it.hardwareAccelerated }
                } else {
                    selectors
                }
            }
        val isLowRam = com.duta.movie.util.VideoUtils.isLowRamDevice(context)
        val isTVDevice = isTV
        // TV boxes on WiFi need a larger, stable buffer with low-RAM OOM safeguards
        val minBufferMs = if (isTVDevice) (if (isLowRam) 90_000 else 120_000) else 50_000      // TV: 1.5-2 min, Phone: 50s
        val maxBufferMs = if (isTVDevice) (if (isLowRam) 180_000 else 300_000) else 150_000     // TV: 3-5 min, Phone: 2.5 min
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ minBufferMs, 
                /* maxBufferMs = */ maxBufferMs, 
                /* bufferForPlaybackMs = */ 800, 
                /* bufferForPlaybackAfterRebufferMs = */ if (isTVDevice) 3_000 else 2_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        ExoPlayer.Builder(context, renderersFactory).setMediaSourceFactory(mediaSourceFactory).setLoadControl(loadControl).setSeekForwardIncrementMs(10_000).setSeekBackIncrementMs(10_000).build().apply {
            videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
            setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            val trackBuilder = trackSelectionParameters.buildUpon().setPreferredTextLanguage("id").setSelectUndeterminedTextLanguage(true)
            // Auto-prefer 720p on TV to reduce bandwidth demand over WiFi
            if (isTVDevice) {
                trackBuilder.setMaxVideoSize(1280, 720)
            }
            trackSelectionParameters = trackBuilder.build()
        }
    }

    // OWL'S EYE: Content Identity Lock (Autoplay Fix)
    // Ensures that when switching episodes, all states are fully reset to prevent "stuck loading".
    val activeContentKey = remember(videoId, currentEpisode?.url) { "$videoId-${currentEpisode?.url}" }
    val lastKnownContentKey = remember { mutableStateOf(activeContentKey) }

    LaunchedEffect(activeContentKey) {
        if (activeContentKey != lastKnownContentKey.value) {
            Log.i("VideoPlayer", "Episode/Content Transition: $activeContentKey")
            isVideoReady = false
            isRevealed = false
            playerErrorMessage = null
            
            // CRITICAL FIX: Wipe any pending resume data from the PREVIOUS episode
            pendingRotationResumePosition = -1L
            pendingRotationContentKey = null
            
            lastKnownContentKey.value = activeContentKey
            
            // Force player to clear its state for the new media item
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }
    }

    LaunchedEffect(isVideoReady) {
        if (isVideoReady && video?.isSeries == true) {
            val thresholdMs = 600_000L // 10 mins
            while (true) {
                delay(5000)
                val duration = exoPlayer.duration
                val position = exoPlayer.currentPosition
                if (duration > 0 && (duration - position) < thresholdMs) {
                    viewModel.prefetchNextEpisode(videoId)
                    break
                }
            }
        }
    }

    LaunchedEffect(isVideoReady) { 
        if (isVideoReady) {
            // OWL'S EYE: Reset finishing flag on successful load
            isFinishing = false
            
            // OWL'S EYE: Auto-Resume after mirror rotation
            // Only apply if the content key (episode) matches the one we captured the position from.
            if (pendingRotationResumePosition > 0 && activeContentKey == pendingRotationContentKey) {
                Log.i("VideoPlayer", "Auto-resuming to $pendingRotationResumePosition for $activeContentKey")
                exoPlayer.seekTo(pendingRotationResumePosition)
                pendingRotationResumePosition = -1L
                pendingRotationContentKey = null
                viewModel.setSuppressResume(true)
            } else if (pendingRotationResumePosition > 0) {
                Log.w("VideoPlayer", "Discarding stale resume position for different episode.")
                pendingRotationResumePosition = -1L
                pendingRotationContentKey = null
            }

            // OWL'S EYE: Only show resume dialog if we have meaningful progress (> 5s)
            // AND we haven't already suppressed it for this session.
            if (savedProgress > 5000L && !shouldSuppressResume) { 
                showResumeDialog = true 
            }
            // Reset suppression for future manual reloads
            viewModel.setSuppressResume(false)
            
            // Force play for TV hardware that might ignore playWhenReady
            if (!exoPlayer.isPlaying) exoPlayer.play()
        } 
    }

    val castPlayer: CastPlayer? = remember {
        try {
            val castContext = CastContext.getSharedInstance(context)
            CastPlayer(castContext, DutaCastMediaItemConverter())
        } catch (e: Exception) {
            Log.e("VideoPlayerScreen", "CastPlayer init error", e)
            null
        }
    }

    var isCasting by remember { mutableStateOf(castPlayer?.isCastSessionAvailable ?: false) }
    var currentCastSubUrl by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(castPlayer) {
        castPlayer?.setSessionAvailabilityListener(object : SessionAvailabilityListener {
            override fun onCastSessionAvailable() {
                // AUTO-OPTIMIZE: If current server is strict (CORS), switch to Cast-friendly server FIRST
                // to avoid race condition where the broken URL loads on TV before the better one resolves
                var needsServerSwitch = false
                extractedUrl?.let { url ->
                    if (url.contains("hanerix") || url.contains("audinifer") || url.contains("hgcloud") || url.contains("masuk")) {
                        viewModel.findCastFriendlyServer(videoId)?.let { betterServer ->
                            needsServerSwitch = true
                            Log.i("VideoPlayerScreen", "Cast: Switching to Cast-Friendly server before enabling cast: $betterServer")
                            val isEpisodeUrl = betterServer.let { (it.contains("/eps/") || it.contains("/episode/") || it.contains("-episode-") || it.contains("/episod/") || it.contains("-episod-") || it.contains("-epi-") || it.contains("/ep-")) && !it.contains("player=") && !it.contains("mirror=") }
                            if (video?.isSeries == true || isEpisodeUrl) {
                                viewModel.playTVSeries(videoId, betterServer, forceReset = false)
                            } else {
                                viewModel.playMovie(videoId, betterServer, forceReset = false)
                            }
                        }
                    }
                }
                // Set isCasting = true — if server switch was triggered, the new URL 
                // will arrive via extractedUrl change which re-triggers LaunchedEffect
                isCasting = true
            }
            override fun onCastSessionUnavailable() {
                // Capture TV position before switching back to local playback
                val castPos = try { castPlayer?.currentPosition ?: 0L } catch (_: Exception) { 0L }
                isCasting = false
                currentCastSubUrl = null
                CastSubtitleServer.stop()
                // Resume local playback from where the TV left off
                if (castPos > 0) {
                    exoPlayer.seekTo(castPos)
                    exoPlayer.playWhenReady = true
                    Log.i("VideoPlayerScreen", "Cast disconnected: Resuming local playback at ${castPos}ms")
                }
            }
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

    // Explicitly sync active subtitle track on Cast RemoteMediaClient
    LaunchedEffect(isCasting, currentCastSubUrl) {
        if (isCasting) {
            try {
                val castContext = CastContext.getSharedInstance(context)
                val session = castContext.sessionManager.currentCastSession
                val rmc = session?.remoteMediaClient
                if (rmc != null) {
                    if (currentCastSubUrl != null) {
                        rmc.setActiveMediaTracks(longArrayOf(1L))
                        Log.i("VideoPlayerScreen", "Cast: Explicitly set active media tracks to [1]")
                    } else {
                        rmc.setActiveMediaTracks(longArrayOf())
                        Log.i("VideoPlayerScreen", "Cast: Deactivated all media tracks")
                    }
                }
            } catch (e: Exception) {
                Log.d("VideoPlayerScreen", "Cast: Error setting active media tracks: ${e.message}")
            }
        }
    }

    // Auto-activate subtitle track on Google Cast status updates
    DisposableEffect(castPlayer, isCasting, currentCastSubUrl) {
        val session = try {
            CastContext.getSharedInstance(context).sessionManager.currentCastSession
        } catch (_: Exception) {
            null
        }
        val rmc = session?.remoteMediaClient
        val callback = object : com.google.android.gms.cast.framework.media.RemoteMediaClient.Callback() {
            override fun onStatusUpdated() {
                if (isCasting && currentCastSubUrl != null) {
                    val status = rmc?.mediaStatus ?: return
                    val tracks = status.mediaInfo?.mediaTracks
                    if (!tracks.isNullOrEmpty()) {
                        val active = status.activeTrackIds
                        if (active == null || !active.contains(1L)) {
                            Log.i("VideoPlayerScreen", "Cast: Auto-activating track 1 on status updated")
                            rmc.setActiveMediaTracks(longArrayOf(1L))
                        }
                    }
                }
            }
        }
        rmc?.registerCallback(callback)
        onDispose {
            rmc?.unregisterCallback(callback)
        }
    }

    val currentPlayer = remember(isCasting, useWebView) {
        if (isCasting && castPlayer != null) castPlayer else exoPlayer
    }

    // Real-time subtitle synchronization for all playback engines (ExoPlayer, WebView, Cast)
    LaunchedEffect(subtitleCues, subtitleOffset, isVideoReady, useWebView, isCasting, currentPlayer) {
        if (subtitleCues.isNotEmpty()) {
            while (true) {
                val basePos = if (useWebView && !isCasting) {
                    webPlayerState.value.position
                } else if (isCasting && castPlayer != null) {
                    castPlayer.currentPosition
                } else {
                    currentPlayer.currentPosition
                }
                val effectivePos = basePos + subtitleOffset
                val activeCues = subtitleCues.filter { cue ->
                    cue.startTimeMs <= effectivePos && effectivePos <= cue.endTimeMs
                }
                val newCues = activeCues.map { it.toMedia3Cue() }
                if (currentCues != newCues) {
                    currentCues = newCues
                }
                delay(100L)
            }
        } else {
            if (selectedSubtitle == null && currentCues.isNotEmpty()) {
                currentCues = emptyList()
            }
        }
    }

    var userInitiatedPause by remember { mutableStateOf(false) }

    // OWL'S EYE: Stall Guard & Progress Saver
    LaunchedEffect(isVideoReady, useWebView, isCasting) {
        if (isVideoReady) {
            userInitiatedPause = false
            var lastPos = -1L
            var stallCount = 0
            val isTrending = video?.title?.contains("Agent Kim", ignoreCase = true) == true || 
                             video?.title?.contains("Manager Kim", ignoreCase = true) == true
            
            while (true) {
                // High-demand items (Agent Kim) get faster stall detection
                val checkDelay = if (isTrending) 3000L else if (isTV) 6000L else 4000L
                delay(checkDelay) 
                val activePlayer = if (isCasting && castPlayer != null) castPlayer else exoPlayer
                val currentPos = if (useWebView && !isCasting) webPlayerState.value.position else activePlayer.currentPosition
                val isPlayingOrBuffering = if (useWebView && !isCasting) {
                    webPlayerState.value.isPlaying
                } else {
                    activePlayer.isPlaying || (activePlayer.playWhenReady && activePlayer.playbackState == androidx.media3.common.Player.STATE_BUFFERING)
                }
                val duration = if (useWebView && !isCasting) webPlayerState.value.duration else activePlayer.duration

                // Reject suspiciously short videos (< 60s for full movie or episode) as fake/dead bumper clips
                val isShortFakeDuration = useWebView && !isCasting && duration in 1..59_999L
                if (isShortFakeDuration) {
                    Log.w("VideoPlayer", "Stall Guard: Suspiciously short duration ($duration ms) detected on WebView. Rotating mirror.")
                    if (!isCasting) {
                        extractedUrl?.let { viewModel.notifyPlaybackFailure(it) }
                        viewModel.resolveNextServer(videoId, viewModel.currentServerUrl.value)
                    }
                    break
                }

                val isProgressing = currentPos > lastPos && lastPos != -1L
                val isStalled = if (useWebView && !isCasting) {
                    !userInitiatedPause && !isProgressing && (!isPlayingOrBuffering || (currentPos == lastPos && lastPos != -1L))
                } else {
                    isPlayingOrBuffering && currentPos == lastPos && lastPos != -1L
                }

                if (isStalled) {
                    stallCount++
                    val maxStallCycles = if (isTrending) 3 else 4 // 9s for trending vs 16s for normal
                    if (stallCount >= maxStallCycles) {
                        Log.w("VideoPlayer", "Stall Guard: Content stuck at $currentPos (playing=$isPlayingOrBuffering, progressing=$isProgressing). Rotating mirror.")
                        if (currentPos > 2000) {
                            pendingRotationResumePosition = currentPos
                            pendingRotationContentKey = activeContentKey
                        }
                        if (!isCasting) {
                            extractedUrl?.let { viewModel.notifyPlaybackFailure(it) }
                            viewModel.resolveNextServer(videoId, viewModel.currentServerUrl.value)
                        }
                        break
                    }
                } else {
                    stallCount = 0
                    if ((isPlayingOrBuffering || isProgressing) && (duration > 0 || duration == androidx.media3.common.C.TIME_UNSET)) {
                        viewModel.saveVideoProgress(videoId, currentPos, duration, currentEpUrl)
                    }
                }
                lastPos = currentPos
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
        
        val isEpisodeUrl = serverUrl?.let { (it.contains("/eps/") || it.contains("/episode/") || it.contains("-episode-") || it.contains("/episod/") || it.contains("-episod-") || it.contains("-epi-") || it.contains("/ep-")) && !it.contains("player=") && !it.contains("mirror=") } ?: false
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
                
                // Only ignore if it's the SAME host AND the SAME content key
                if (isSameHost && activeContentKey == lastKnownContentKey.value) {
                    return@LaunchedEffect
                }
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
                if (pendingRotationResumePosition == -1L && exoPlayer.currentPosition > 2000) {
                    pendingRotationResumePosition = exoPlayer.currentPosition
                    pendingRotationContentKey = activeContentKey
                }
                isVideoReady = false
                webPlayerState.value = WebPlayerState()
                isFinishing = false // CRITICAL: Allow error/timeout handlers to work on the new stream
                playerErrorMessage = null
            }
        }
    }

    // Secondary listener for internal server rotation
    var lastObservedServerUrl by remember(videoId, serverUrl, currentEpisode?.url) { mutableStateOf(serverUrl) }
    LaunchedEffect(currentServerUrlFromVm) {
        val newServer = currentServerUrlFromVm
        if (newServer != null) {
            if (lastObservedServerUrl != null && newServer != lastObservedServerUrl) {
                Log.i("VideoPlayerScreen", "Internal server rotation detected: from $lastObservedServerUrl to $newServer")
                if (pendingRotationResumePosition == -1L && exoPlayer.currentPosition > 2000) {
                    pendingRotationResumePosition = exoPlayer.currentPosition
                    pendingRotationContentKey = activeContentKey
                }
                // Reset player state but DON'T call resolveVideoUrl as it was already called by rotation logic
                isVideoReady = false
                webPlayerState.value = WebPlayerState()
                isFinishing = false // Allow error/timeout handlers to work on the new stream
                playerErrorMessage = null
            }
            lastObservedServerUrl = newServer
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

    // OWL'S EYE 6.4: Pure Blackout Handshake (WebView Embeds Only)
    // Ensures the screen stays 100% black until the web player is verified playing.
    // Native ExoPlayer has its own dedicated STATE_BUFFERING watchdog (lines 882-896) and onPlayerError listeners.
    LaunchedEffect(useWebView, extractedUrl, isVideoReady) {
        if (useWebView && !isVideoReady && extractedUrl != null) {
            var stuckCount = 0
            val isPm = videoId?.startsWith("pm_") == true
            val maxStuck = if (isPm) 25 else 30 // WebView embeds need warmup time for JS & ad nuking
            
            while(!isVideoReady && stuckCount < maxStuck) {
                webViewRef.value?.let { wv -> injectNuker(wv) }
                if (webPlayerState.value.isPlaying && webPlayerState.value.position > 300L) {
                    Log.i("VideoPlayer", "Handshake: WebView state isPlaying=true with progress (${webPlayerState.value.position}ms). Setting isVideoReady=true.")
                    isVideoReady = true
                    break
                }
                if (stuckCount >= 8 && !isRevealed) {
                    val isGateway = extractedUrl?.let { url -> 
                        url.contains("/eps/") || url.contains("/episode/") || 
                        !com.duta.movie.util.VideoExtractor.isProbablyVideoHost(url)
                    } ?: false
                    
                    if (!isGateway && isUserForcingWebView) {
                        Log.i("VideoPlayer", "Emergency Hatch: Forcing reveal after 8s blackout because user forced WebView.")
                        isRevealed = true
                    }
                }
                
                stuckCount++
                delay(1000)
            }
            
            // OWL'S EYE: Anti-Freeze Rotation for WebView embeds
            if (!isVideoReady && !isFinishing) {
                Log.w("VideoPlayer", "Mirror Timeout reached on WebView embed. Auto-Rotating...")
                scope.launch {
                    val failingUrl = extractedUrl ?: currentServerUrlFromVm
                    failingUrl?.let { viewModel.notifyPlaybackFailure(it) }
                    viewModel.resolveNextServer(videoId, failingUrl, force = true)
                }
            }
        }
    }

    // OWL'S EYE: Active Playback Stall Watchdog (WebView Embeds Only)
    // Detects when playback was running, but subsequent decode or CDN starvation freezes playback for > 5s
    LaunchedEffect(useWebView, isVideoReady, extractedUrl) {
        if (useWebView && isVideoReady && extractedUrl != null) {
            var lastPos = -1L
            var stallSeconds = 0
            while (isVideoReady && !isFinishing) {
                delay(1000)
                if (webPlayerState.value.isPlaying && !userInitiatedPause) {
                    val currentPos = webPlayerState.value.position
                    if (currentPos > 0L && currentPos == lastPos) {
                        stallSeconds++
                        if (stallSeconds >= 6) {
                            Log.w("VideoPlayerScreen", "Owl's Eye: WebView playback stall detected (frozen at ${currentPos}ms for ${stallSeconds}s). Failing over...")
                            val failingUrl = extractedUrl ?: currentServerUrlFromVm ?: ""
                            if (failingUrl.isNotEmpty()) {
                                viewModel.notifyMirrorDead(failingUrl)
                                viewModel.notifyPlaybackFailure(failingUrl)
                            }
                            viewModel.resolveNextServer(videoId, failingUrl, force = true)
                            break
                        }
                    } else {
                        lastPos = currentPos
                        stallSeconds = 0
                    }
                } else {
                    stallSeconds = 0
                }
            }
        }
    }

    var lastCastSubUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(extractedUrl, selectedSubtitle, currentPlayer, subtitleCues, subtitleOffset, isCasting) {
        val url = extractedUrl ?: return@LaunchedEffect
        val sub = selectedSubtitle
        if (!useWebView || isCasting) {
            val effectiveReferer = lastReferer ?: "${com.duta.movie.util.VideoExtractor.getBaseUrl()}/"
            
            // Sync headers to OkHttp client
            lastCookies?.let { NetworkConfig.injectCookies(url, it) }
            NetworkConfig.updateSessionReferer(url, effectiveReferer)
            
            // For Google Cast: serve WebVTT subtitles via local LAN CastSubtitleServer
            val castSubUrl = if (isCasting && sub != null && subtitleCues.isNotEmpty()) {
                val vtt = SubtitleParser.toWebVtt(subtitleCues, subtitleOffset)
                CastSubtitleServer.setSubtitle(vtt)
            } else {
                if (isCasting && sub == null) CastSubtitleServer.clear()
                null
            }
            currentCastSubUrl = castSubUrl

            val mediaItem = MediaItem.Builder()
                .setUri(url)
                .setMediaId(effectiveProgressId)
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
                    // Only use explicit path/extension indicators, NOT TLDs
                    if (lowUrl.contains(".m3u8") || lowUrl.contains(".txt") || 
                        lowUrl.contains("/hls/") || lowUrl.contains("/stream/")) {
                        // application/x-mpegURL is the standard for HLS on both Media3 and Cast
                        setMimeType(MimeTypes.APPLICATION_M3U8)
                    } else if (lowUrl.contains(".mp4")) {
                        setMimeType(MimeTypes.VIDEO_MP4)
                    } else if (lowUrl.contains(".mkv")) {
                        setMimeType(MimeTypes.VIDEO_MATROSKA)
                    } else if (lowUrl.contains(".webm")) {
                        setMimeType(MimeTypes.VIDEO_WEBM)
                    } else {
                        setMimeType(MimeTypes.APPLICATION_M3U8)
                    }
                }
                .setSubtitleConfigurations(
                    if (isCasting) {
                        if (castSubUrl != null && sub != null) {
                            listOf(
                                MediaItem.SubtitleConfiguration.Builder(castSubUrl.toUri())
                                    .setMimeType(MimeTypes.TEXT_VTT)
                                    .setLanguage(currentPreferredLang.value)
                                    .setLabel(sub.label)
                                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_FORCED)
                                    .build()
                            )
                        } else emptyList()
                    } else {
                        sub?.let { s ->
                            val effectiveSubUri = if (s.localUri != null) s.localUri else s.url
                            val mimeType = if (effectiveSubUri.contains(".vtt")) MimeTypes.TEXT_VTT 
                                          else if (effectiveSubUri.contains(".ass") || effectiveSubUri.contains(".ssa")) MimeTypes.TEXT_SSA 
                                          else MimeTypes.APPLICATION_SUBRIP
                            listOf(
                                MediaItem.SubtitleConfiguration.Builder(effectiveSubUri.toUri())
                                    .setMimeType(mimeType)
                                    .setLanguage(currentPreferredLang.value)
                                    .setLabel(s.label)
                                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_FORCED)
                                    .build()
                            )
                        } ?: emptyList()
                    }
                )
                .build()

            if (isCasting && castPlayer != null) {
                // Only transfer position if ExoPlayer was actually playing THIS specific URL
                val exoCurrentUri = exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString()
                // Check if castPlayer already has an active session playing (re-entry scenario)
                val castCurrentPos = if (castPlayer.isPlaying || castPlayer.currentPosition > 0) castPlayer.currentPosition else 0L
                val currentPos = if (exoCurrentUri == url && (exoPlayer.isPlaying || exoPlayer.currentPosition > 0)) {
                    exoPlayer.currentPosition
                } else {
                    castCurrentPos // Use TV's current position if available, otherwise 0L
                }
                exoPlayer.pause()
                
                // Don't reload if castPlayer is already playing this exact media and subtitle hasn't changed
                if (castPlayer.currentMediaItem?.mediaId == effectiveProgressId && castCurrentPos > 0 && lastCastSubUrl == castSubUrl) {
                    Log.i("VideoPlayerScreen", "Cast: Already playing this media at ${castCurrentPos}ms, skipping reload")
                } else {
                    lastCastSubUrl = castSubUrl
                    castPlayer.setMediaItem(mediaItem, currentPos)
                    castPlayer.prepare()
                    castPlayer.play()
                }
            } else {
                // FOR LOCAL PLAYBACK: Only load and prepare when stream URL actually changes
                if (currentUrl.value != url) {
                    exoPlayer.stop()
                    exoPlayer.clearMediaItems()
                    val isHls = url.lowercase().contains(".m3u8") || url.lowercase().contains(".txt") || 
                                url.lowercase().contains("/hls/") || url.lowercase().contains("/stream/")
                    
                    if (isHls) {
                        val hlsOkHttpFactory = OkHttpDataSource.Factory(NetworkConfig.permissiveOkHttpClient).setUserAgent(NetworkConfig.SHARED_USER_AGENT)
                        val hlsCacheFactory = com.duta.movie.util.PlayerCacheManager.getCacheDataSourceFactory(context, hlsOkHttpFactory)
                        val hlsMediaSource = HlsMediaSource.Factory(hlsCacheFactory).createMediaSource(mediaItem)
                        
                        exoPlayer.setMediaSource(hlsMediaSource, /* resetPosition = */ true)
                    } else {
                        exoPlayer.setMediaItem(mediaItem, /* resetPosition = */ true)
                    }
                    
                    isVideoReady = false
                    currentUrl.value = url
                    currentSub.value = sub?.url
                    currentPlayer.prepare()
                    currentPlayer.playWhenReady = true
                } else {
                    currentSub.value = sub?.url
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
            CastSubtitleServer.stop()
        }
    }

    // Pause playback when screen is locked or activity goes to background (without PiP)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    // Only pause if NOT in PiP mode — PiP should keep playing
                    val activity = context.findActivity()
                    val inPip = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        activity?.isInPictureInPictureMode == true
                    } else false
                    if (!inPip && !isCasting) {
                        exoPlayer.playWhenReady = false
                    }
                }
                androidx.lifecycle.Lifecycle.Event.ON_START -> {
                    if (!isCasting) {
                        exoPlayer.playWhenReady = true
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(currentPlayer, useWebView, isCasting) {
        var stutterCount = 0
        var lastStutterTime = 0L
        var isUserSeeking = false
        var bufferJob: kotlinx.coroutines.Job? = null
        var idleJob: kotlinx.coroutines.Job? = null

        val listener = object : Player.Listener {
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
                    isUserSeeking = true
                }
            }

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

                if (state != Player.STATE_BUFFERING) {
                    bufferJob?.cancel()
                    bufferJob = null
                }

                if (state != Player.STATE_IDLE) {
                    idleJob?.cancel()
                    idleJob = null
                }

                if (state == Player.STATE_IDLE && !useWebView && !isCasting && extractedUrl != null && !isVideoReady && !isFinishing) {
                    idleJob?.cancel()
                    idleJob = scope.launch {
                        delay(2500)
                        if (currentPlayer.playbackState == Player.STATE_IDLE && !useWebView && !isCasting && !isVideoReady && !isFinishing) {
                            Log.w("VideoPlayerScreen", "ExoPlayer stranded in STATE_IDLE for 2.5s. Media failed to load. Rotating to next server...")
                            isFinishing = true
                            extractedUrl?.let { viewModel.notifyPlaybackFailure(it) }
                            viewModel.resolveNextServer(videoId, viewModel.currentServerUrl.value, force = true)
                        }
                    }
                }

                if (state == Player.STATE_BUFFERING) {
                    // OWL'S EYE: Let ExoPlayer handle normal rebuffering internally.
                    // ExoPlayer automatically reconnects and resumes on network hiccups.
                    // Only intervene as a last resort for truly stuck/dead streams.
                    // Real failures (403, timeout, etc.) are caught by onPlayerError instead.
                    
                    bufferJob?.cancel()
                    bufferJob = scope.launch {
                        // Last-resort watchdog: only rotate if stuck for a very long time
                        val isArchiveStream = extractedUrl?.contains("archive.org") == true
                        val timeout = if (isUserSeeking) 45000L else if (isTV || isArchiveStream) 45000L else 30000L
                        delay(timeout)
                        if (currentPlayer.playbackState == Player.STATE_BUFFERING && !isFinishing) {
                            Log.w("VideoPlayerScreen", "Stuck in BUFFERING for ${timeout/1000}s. Stream appears dead. Rotating.")
                            if (currentPlayer.currentPosition > 2000) {
                                pendingRotationResumePosition = currentPlayer.currentPosition
                                pendingRotationContentKey = activeContentKey
                            }
                            isFinishing = true
                            extractedUrl?.let { viewModel.notifyPlaybackFailure(it) }
                            viewModel.resolveNextServer(videoId, viewModel.currentServerUrl.value)
                        }
                    }
                }

                if (state == Player.STATE_READY) {
                    isUserSeeking = false
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
                        viewModel.resolveNextServer(videoId, viewModel.currentServerUrl.value)
                    }
                }
                if (state == Player.STATE_ENDED) {
                    if (isFinishing) {
                        Log.d("VideoPlayerScreen", "ENDED reached but isFinishing=true. Ignoring autoplay.")
                        return
                    }
                    
                    val playedDuration = currentPlayer.currentPosition
                    val totalDuration = currentPlayer.duration
                    
                    // OWL'S EYE: Healthy Duration Guard
                    // A real episode is rarely < 10 mins (600s). A real movie is rarely < 40 mins (2400s).
                    // If it ends before this, it's a "False End" (Broken Stream/Ad Loop).
                    val minHealthyDuration = if (video?.isSeries == true) 600_000L else 1_800_000L
                    
                    // CRITICAL: Fail-safe for premature "Ended" signals.
                    val isNaturalEnd = totalDuration > minHealthyDuration && playedDuration > (totalDuration * 0.9)
                    
                    if (!isNaturalEnd && autoRetryCount < 3) {
                         Log.w("VideoPlayerScreen", "Playback ended suspiciously ($playedDuration/$totalDuration). Likely a server drop. Rotating...")
                         if (playedDuration > 2000) {
                             pendingRotationResumePosition = playedDuration
                             pendingRotationContentKey = activeContentKey
                         }
                         isFinishing = true
                         scope.launch { 
                             delay(1000)
                             viewModel.resolveNextServer(videoId, extractedUrl) 
                         }
                         return
                    }

                    // If it is a natural end, clear any pending rotations so the next episode starts fresh
                    pendingRotationResumePosition = -1L
                    pendingRotationContentKey = null

                    // OWL'S EYE: Mark as finished in DB to prevent stale resume data
                    viewModel.saveVideoProgress(videoId, 0L, totalDuration, currentEpUrl)

                    isFinishing = true
                    Log.d("VideoPlayerScreen", "Playback ended normally. Triggering autoplay.")
                    
                    scope.launch {
                        // Small "breathe" delay for TV hardware to clear buffers
                        delay(1200) 
                        val hasNext = viewModel.resolveNextEpisode(videoId)
                        if (!hasNext) {
                            Log.d("VideoPlayerScreen", "Kicked out: No next episode for movie/series end.")
                            onBackClick()
                        } else {
                            isFinishing = false
                        }
                    }
                }
            }
            override fun onRenderedFirstFrame() { isVideoReady = true }
            override fun onPlayerError(error: PlaybackException) {
                if (isFinishing) return
                
                val message = error.message ?: ""
                val cause = error.cause?.message ?: ""
                Log.e("VideoPlayerScreen", "Player Error: $message | Cause: $cause | URL: ${extractedUrl?.take(60)}")
                
                if (currentPlayer.currentPosition > 2000) {
                    pendingRotationResumePosition = currentPlayer.currentPosition
                    pendingRotationContentKey = activeContentKey
                }

                // Don't blacklist mirrors due to cast errors — cast failures are usually
                // CORS/header issues on the Chromecast receiver, not actual mirror problems
                if (!isCasting) {
                    extractedUrl?.let { viewModel.notifyPlaybackFailure(it) }
                }
                
                val isNetworkError = message.contains("403") || message.contains("404") || message.contains("502") || 
                                     message.contains("Unable to connect") || message.contains("Connection timeout") || 
                                     message.contains("Source error") || message.contains("Response code") ||
                                     cause.contains("403") || cause.contains("502") || cause.contains("timeout") ||
                                     error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                                     error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED

                val is404 = message.contains("404") || cause.contains("404") || error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
                val is403 = (message.contains("403") || cause.contains("403")) && autoRetryCount == 0
                val isSourceError = message.contains("Source error") || message.contains("Response code: 4")

                if (is404) {
                    Log.w("VideoPlayerScreen", "Owl's Eye: 404 Not Found on ExoPlayer. Marking mirror dead & fast-rotating...")
                    val failingUrl = extractedUrl ?: currentServerUrl.value ?: ""
                    if (failingUrl.isNotEmpty()) {
                        viewModel.notifyMirrorDead(failingUrl)
                    }
                    scope.launch {
                        viewModel.resolveNextServer(currentVideoId.value, currentServerUrl.value, force = true)
                    }
                } else if (is403) {
                    val failingUrl = extractedUrl ?: currentServerUrl.value ?: ""
                    Log.w("VideoPlayerScreen", "Owl's Eye: Detected 403 Forbidden on $failingUrl. Purging dead stream & rotating...")
                    if (failingUrl.isNotEmpty()) {
                        viewModel.notifyMirrorDead(failingUrl)
                        viewModel.purgeServerFromVideo(currentVideoId.value, failingUrl)
                    }
                    scope.launch {
                        delay(600)
                        viewModel.resolveNextServer(currentVideoId.value, currentServerUrl.value, force = true)
                    }
                } else if (isSourceError && autoRetryCount == 0) {
                    autoRetryCount++
                    Log.i("VideoPlayerScreen", "Owl's Eye: ExoPlayer failed with Source Error. Flagging host for learning...")
                    val failingUrl = extractedUrl ?: currentServerUrl.value ?: ""
                    if (failingUrl.isNotEmpty()) {
                        viewModel.notifyMirrorDead(failingUrl)
                        viewModel.purgeServerFromVideo(currentVideoId.value, failingUrl)
                    }
                    val host = try { android.net.Uri.parse(extractedUrl).host } catch(_: Exception) { null }
                    if (host != null) {
                         viewModel.recordExoPlayerFailure(host)
                    }
                    scope.launch {
                        delay(600)
                        viewModel.resolveNextServer(currentVideoId.value, currentServerUrl.value, force = true)
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
                        viewModel.resolveNextServer(currentVideoId.value, currentServerUrl.value, force = true)
                    }
                }
            }
            override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) { if (subtitleCues.isEmpty()) currentCues = cueGroup.cues }
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
    LaunchedEffect(isResolvingState, isVideoReady, extractedUrl) {
        if (!isResolvingState && !isVideoReady) {
            delay(8000)
            val isGateway = extractedUrl?.let { url -> 
                url.contains("/eps/") || url.contains("/episode/") || 
                !com.duta.movie.util.VideoExtractor.isProbablyVideoHost(url)
            } ?: false
            if (!isGateway) {
                showManualPlay = true
                if (isUserForcingWebView) {
                    isRevealed = true // Reveal error pages or interactive mirrors
                }
            }
        } else {
            showManualPlay = false
        }
    }

    VideoPlayerContent(
        nukerScript = nukerScript,
        extractedUrl = extractedUrl,
        isLoading = isLoading,
        error = error,
        playerErrorMessage = playerErrorMessage,
        isTransitionComplete = isTransitionComplete,
        useWebView = useWebView,
        isCasting = isCasting,
        isVideoReady = isVideoReady,
        currentCues = currentCues,
        showControls = showControls,
        isInPip = isInPip,
        isLandscape = isLandscape,
        isFullscreen = isFullscreen,
        showManualPlay = showManualPlay,
        video = video,
        exoPlayer = exoPlayer,
        castPlayer = castPlayer,
        webPlayerState = webPlayerState.value,
        webViewRef = webViewRef,
        qualityTracks = qualityTracks,
        selectedQualityGroupIndex = selectedQualityGroupIndex,
        seekFeedback = seekFeedback,
        currentEpisode = currentEpisode,
        playPauseFocusRequester = playPauseFocusRequester,
        controlsFocusRequester = controlsFocusRequester,
        onBackClick = onBackClick,
        onVisibilityToggle = { if (!isInPip) showControls = !showControls },
        onFullscreenToggle = { isFullscreen = !isFullscreen },
        onServerListClick = { showServerDialog = true },
        onSubtitleClick = { showSubtitleDialog = true; viewModel.fetchSubtitles(video?.title ?: "") },
        onSyncClick = { showSyncDialog = true },
        onPipClick = { (context.findActivity() as? com.duta.movie.MainActivity)?.enterPipMode() },
        onEpisodeListClick = { showEpisodeDialog = true },
        onInteraction = { lastInteractionTime = System.currentTimeMillis() },
        onControlFocusChange = { 
            isAnyControlFocused = it
            lastInteractionTime = System.currentTimeMillis()
        },
        onQualitySelect = { t ->
            // Quality track overrides only work on local ExoPlayer, not CastPlayer
            if (!isCasting) {
                val g = exoPlayer.currentTracks.groups[t.groupIndex].mediaTrackGroup
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                    .setOverrideForType(TrackSelectionOverride(g, t.trackIndex))
                    .build()
                selectedQualityGroupIndex = t.groupIndex
            }
        },
        onNextEpisodeClick = { viewModel.resolveNextEpisode(videoId) },
        onRetryClick = { 
            playerErrorMessage = null
            val isEpisodeUrl = serverUrl?.let { (it.contains("/eps/") || it.contains("/episode/") || it.contains("-episode-") || it.contains("/ep-")) && !it.contains("player=") && !it.contains("mirror=") } ?: false
            if (video?.isSeries == true || isEpisodeUrl) {
                viewModel.playTVSeries(videoId, serverUrl, forceReset = true)
            } else {
                viewModel.playMovie(videoId, serverUrl, forceReset = true)
            }
        },
        onFindAlternativesClick = {
            playerErrorMessage = null
            Toast.makeText(context, context.getString(R.string.searching_other_sources), Toast.LENGTH_SHORT).show()
            viewModel.searchAlternativeSources(videoId) { count ->
                if (count > 0) {
                    Toast.makeText(context, context.getString(R.string.sources_found, count), Toast.LENGTH_LONG).show()
                    val updatedVideo = viewModel.videoMetadata.value
                    val newServer = updatedVideo?.servers?.lastOrNull()?.url ?: updatedVideo?.servers?.firstOrNull()?.url
                    if (newServer != null) {
                        if (video?.isSeries == true) {
                            viewModel.playTVSeries(videoId, newServer, forceReset = false, isRotation = false)
                        } else {
                            viewModel.playMovie(videoId, newServer, forceReset = false, isRotation = false)
                        }
                    }
                } else {
                    Toast.makeText(context, context.getString(R.string.no_other_sources_found), Toast.LENGTH_SHORT).show()
                }
            }
        },
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
        onNextServerClick = { 
            isVideoReady = false
            webPlayerState.value = WebPlayerState()
            viewModel.resolveNextServer(videoId, null, force = true) 
        },
        onPlayerStateChange = { state, time, duration ->
            val prev = webPlayerState.value
            val pos = if (time >= 0f) (time * 1000).toLong() else prev.position
            val dur = if (duration > 0f) (duration * 1000).toLong() else prev.duration
            val isAdvancing = pos > prev.position && prev.position >= 0L
            val finalPlaying = when (state) {
                1 -> true
                2 -> false
                else -> if (isAdvancing && !userInitiatedPause) true else prev.isPlaying
            }
            webPlayerState.value = WebPlayerState(
                isPlaying = finalPlaying,
                position = pos,
                duration = dur
            )
            if ((state == 1 || isAdvancing) && time > 0.3f && useWebView && !isVideoReady) {
                Log.i("VideoPlayerScreen", "WebView playback confirmed playing (time=$time). Setting isVideoReady = true")
                isVideoReady = true
                extractedUrl?.let { viewModel.notifyPlaybackSuccess(it) }
            }
        },
        onStreamFound = { url, ref, cookies ->
            val low = url.lowercase()
            val isCurrentDirectEmbed = (extractedUrl ?: "").let { 
                it.contains("youtube") || it.contains("youtu.be") || 
                it.contains("bilibili.com") || it.contains("bilibili.tv") ||
                it.contains("dailymotion.com") || it.contains("dai.ly")
            }
            val isFakeStream = low.contains("tiktokcdn.com") || low.contains("ad-site") || 
                               low.contains("image?lk3s=") || low.contains("/hlsmod/")
            if (isCurrentDirectEmbed || isFakeStream || low.contains("analytics") || low.contains("google-analytics") || low.contains("collect") || low.contains("pixel") ||
                low.contains("notification") || low.contains("bonus-stars") || low.contains("bakestubborn") || low.contains("gambling") || low.contains("promo") ||
                low.contains("pagead") || low.contains("googleads") || low.contains("doubleclick") || low.contains("/aclk") || low.contains("imasdk")) {
                Log.w("VideoPlayer", "Blocked ad/tracking/DirectEmbed/FakeStream URL from onStreamFound: $url")
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
                    low.contains("/amt/") || low.contains(".amt") || low.contains("amt1.pro") || low.contains("amt2.pro") || low.contains("abyss") || 
                    low.contains("bond") || low.contains("playstream") ||
                    low.contains("veev") || low.contains("iplayer") ||
                    low.contains("morencius")
                }

                // GOLD STANDARD: If URL contains the movie ID, it's 100% verified.
                val isSlugVerified = sourceUrl.contains(videoId, ignoreCase = true)

                // GATEWAY GUARD: For interactive mirrors, wait for verified sniffer activity
                val isInteractive = extractedUrl?.let { u ->
                    val low = u.lowercase()
                    low.contains("abyss") || low.contains("playstream") || low.contains("veev") || low.contains("dood")
                } ?: false

                if (isInteractive && !useWebView) {
                     // If we are on an interactive mirror but NOT in WebView mode, 
                     // it's a direct stream found by racing - trust the slug.
                     isSlugVerified || winnerHost == targetHost
                } else {
                     winnerHost != null && (winnerHost == targetHost || isVipDomain || isSlugVerified || sourceUrl == extractedUrl)
                }
            } catch(_: Exception) { sourceUrl == extractedUrl }

            if (isLegitMatch) {
                Log.i("VideoPlayerScreen", "MATCH VERIFIED: Declaring isVideoReady = true for $sourceUrl")
                viewModel.notifyPlaybackSuccess(sourceUrl)
                isVideoReady = true 
            } else {
                Log.d("VideoPlayerScreen", "Ignored success from foreign source: $sourceUrl")
            }
        },
        onGateStuck = { currentUrl, originalUrl ->
            if (!isVideoReady || useWebView) {
                Log.w("VideoPlayer", "Gate Stuck callback triggered for $currentUrl (original: $originalUrl)")
                viewModel.notifyGateStuck(currentUrl, originalUrl, videoId)
            } else {
                Log.d("VideoPlayer", "Ignored Gate Stuck callback because native player is already active")
            }
        },
        onMirrorDead = { url -> viewModel.notifyMirrorDead(url); viewModel.resolveNextServer(videoId, url, force = true) },
        onSeekFeedback = { feedback -> seekFeedback = feedback },
        lastReferer = lastReferer,
        lastCookies = lastCookies,
        forceWebViewHosts = forceWebViewHosts,
        isResolving = isResolvingState,
        resolutionProgress = resolutionProgress,
        isRevealed = isRevealed,
        onLogClick = { showResolutionLog = true },
        isTV = isTV,
        activeContentKey = activeContentKey,
        onUserPauseChange = { userInitiatedPause = it },
        fallbackDurationMs = fallbackDurationMs
    )

    if (showResolutionLog) {
        AlertDialog(
            onDismissRequest = { showResolutionLog = false },
            title = { Text(stringResource(R.string.resolution_log), color = Color.White) },
            containerColor = Color(0xFF1A1A1A),
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    resolutionLog.forEach { logText ->
                        item {
                            Text(
                                text = logText,
                                color = if (logText.contains("Success") || logText.contains("Winner")) Color.Green 
                                        else if (logText.contains("Error") || logText.contains("Failed")) Color.Red 
                                        else Color.LightGray,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showResolutionLog = false }) {
                    Text(stringResource(R.string.close), color = Color.Red)
                }
            }
        )
    }

    if (showResumeDialog) {
        val resumeFocusRequester = remember { FocusRequester() }
        var interactionKey by remember { mutableIntStateOf(0) }
        
        LaunchedEffect(Unit) {
            delay(300)
            try { resumeFocusRequester.requestFocus() } catch(_: Exception) {}
        }
        
        // Auto-close after 10 seconds of idleness
        LaunchedEffect(showResumeDialog, interactionKey) {
            delay(10000)
            showResumeDialog = false
        }

        AlertDialog(
            onDismissRequest = { showResumeDialog = false },
            title = { Text(stringResource(R.string.resume_playback), color = Color.White) },
            text = { Text(stringResource(R.string.would_you_like_to_resume_from_), color = Color.Gray) },
            containerColor = Color(0xFF1A1A1A),
            confirmButton = {
                var isResumeFocused by remember { mutableStateOf(false) }
                Button(
                    onClick = { 
                        if (useWebView) {
                            val seekScript = "(function(){ var t = ${savedProgress / 1000.0}; if(window.playerBridge && typeof window.playerBridge.seek==='function'){ try{window.playerBridge.seek(t); return;}catch(e){} } var v=document.querySelector('video'); if(v){ v.currentTime=t; } var ifrs=document.querySelectorAll('iframe'); for(var i=0;i<ifrs.length;i++){ try{ ifrs[i].contentWindow.postMessage(JSON.stringify({event:'command',func:'seekTo',args:[t,true]}),'*'); ifrs[i].contentWindow.postMessage(JSON.stringify({method:'seek',value:t}),'*'); ifrs[i].contentWindow.postMessage(JSON.stringify({type:'seek',value:t}),'*'); }catch(e){} } })();"
                            safeEvaluateJavascript(webViewRef.value, seekScript)
                            pendingRotationResumePosition = savedProgress
                            pendingRotationContentKey = activeContentKey
                        } else {
                            exoPlayer.seekTo(savedProgress)
                        }
                        showResumeDialog = false 
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isResumeFocused) Color.White else Color.Red),
                    modifier = Modifier
                        .focusRequester(resumeFocusRequester)
                        .onFocusChanged { 
                            isResumeFocused = it.isFocused 
                            if (it.isFocused) interactionKey++ 
                        }
                        .focusable()
                        .scale(if (isResumeFocused) 1.1f else 1f)
                ) { 
                    Text(stringResource(R.string.resume), color = if (isResumeFocused) Color.Black else Color.White, fontWeight = FontWeight.Bold) 
                }
            },
            dismissButton = {
                var isStartFocused by remember { mutableStateOf(false) }
                TextButton(
                    onClick = { showResumeDialog = false },
                    modifier = Modifier
                        .onFocusChanged { 
                            isStartFocused = it.isFocused 
                            if (it.isFocused) interactionKey++ 
                        }
                        .focusable()
                        .scale(if (isStartFocused) 1.1f else 1f)
                ) { 
                    Text(stringResource(R.string.start_over), color = if (isStartFocused) Color.White else Color.Gray) 
                }
            }
        )
    }

    if (showServerDialog) {
        ServerSelectionDialog(
            servers = video?.servers ?: emptyList(), 
            currentServerUrl = serverUrl ?: "", 
            isTV = isTV,
            onServerSelect = { url, force -> 
                showServerDialog = false
                isUserForcingWebView = force
                val isEpisodeUrl = url.let { (it.contains("/eps/") || it.contains("/episode/") || it.contains("-episode-") || it.contains("/ep-")) && !it.contains("player=") && !it.contains("mirror=") }
                if (video?.isSeries == true || isEpisodeUrl) {
                    viewModel.playTVSeries(videoId, url)
                } else {
                    viewModel.playMovie(videoId, url)
                }
            }, 
            onDismiss = { showServerDialog = false }
        )
    }
    
    if (showSubtitleDialog) {
        AlertDialog(
            onDismissRequest = { showSubtitleDialog = false }, 
            title = { Text(stringResource(R.string.subtitles), color = Color.White) }, 
            containerColor = Color(0xFF1A1A1A),
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                    state = rememberLazyListState()
                ) {
                    item(key = "none") {
                        var isFocused by remember { mutableStateOf(false) }
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.none_embedded), color = Color.White) },
                            leadingContent = { RadioButton(selected = selectedSubtitle == null, onClick = null, modifier = Modifier.focusable(false)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(subFirstItemFocusRequester)
                                .onFocusChanged { isFocused = it.isFocused }
                                .clickable { viewModel.selectSubtitle(null); showSubtitleDialog = false }
                                .focusable()
                                .border(if (isFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(8.dp)),
                            colors = ListItemDefaults.colors(
                                containerColor = if (isFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent
                            )
                        )
                    }
                    items(subtitles, key = { it.url }) { sub ->
                        var isFocused by remember { mutableStateOf(false) }
                        ListItem(
                            headlineContent = { Text(sub.label, color = Color.White) },
                            supportingContent = { Text(sub.language, color = Color.Gray, fontSize = 12.sp) },
                            leadingContent = { RadioButton(selected = selectedSubtitle?.url == sub.url, onClick = null, modifier = Modifier.focusable(false)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isFocused = it.isFocused }
                                .clickable { viewModel.selectSubtitle(sub); showSubtitleDialog = false }
                                .focusable()
                                .border(if (isFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(8.dp)),
                            colors = ListItemDefaults.colors(
                                containerColor = if (isFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent
                            )
                        )
                    }
                    if (isSubtitleLoading) { 
                        item(key = "loading") { 
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { 
                                CircularProgressIndicator(color = Color.Red) 
                            } 
                        } 
                    }
                }
            },
            confirmButton = { 
                var isCloseFocused by remember { mutableStateOf(false) }
                TextButton(
                    onClick = { showSubtitleDialog = false },
                    modifier = Modifier
                        .onFocusChanged { isCloseFocused = it.isFocused }
                        .focusable()
                        .background(if (isCloseFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent, RoundedCornerShape(8.dp))
                        .border(if (isCloseFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(8.dp))
                ) { Text(stringResource(R.string.close), color = if (isCloseFocused) Color.White else Color.Red) } 
            }
        )
    }
    
    if (showSyncDialog) {
        val syncFirstButtonFocusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            delay(300)
            try { syncFirstButtonFocusRequester.requestFocus() } catch(_: Exception) {}
        }
        
        AlertDialog(
            onDismissRequest = { showSyncDialog = false },
            title = { Text(stringResource(R.string.subtitle_sync), color = Color.White) },
            containerColor = Color(0xFF1A1A1A),
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.adjust_subtitle_timing), color = Color.Gray, fontSize = 14.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "${if (subtitleOffset >= 0) "+" else ""}${subtitleOffset / 1000.0}s", 
                        color = Color.White, 
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        var isMinusFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = { viewModel.adjustSubtitleOffset(-500) },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isMinusFocused) Color.White else Color(0xFF333333)),
                            modifier = Modifier
                                .focusRequester(syncFirstButtonFocusRequester)
                                .onFocusChanged { isMinusFocused = it.isFocused }
                                .focusable()
                                .scale(if (isMinusFocused) 1.1f else 1f)
                        ) { 
                            Text(stringResource(R.string.minus_0_5s), color = if (isMinusFocused) Color.Black else Color.White) 
                        }
                        
                        var isPlusFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = { viewModel.adjustSubtitleOffset(500) },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isPlusFocused) Color.White else Color(0xFF333333)),
                            modifier = Modifier
                                .onFocusChanged { isPlusFocused = it.isFocused }
                                .focusable()
                                .scale(if (isPlusFocused) 1.1f else 1f)
                        ) { 
                            Text(stringResource(R.string.minus_0_5s), color = if (isPlusFocused) Color.Black else Color.White) 
                        }
                    }
                }
            },
            confirmButton = { 
                var isDoneFocused by remember { mutableStateOf(false) }
                TextButton(
                    onClick = { showSyncDialog = false },
                    modifier = Modifier
                        .onFocusChanged { isDoneFocused = it.isFocused }
                        .focusable()
                        .background(if (isDoneFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent, RoundedCornerShape(8.dp))
                        .border(if (isDoneFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(8.dp))
                ) {
                    Text(stringResource(R.string.done), color = if (isDoneFocused) Color.White else Color.Red, fontWeight = FontWeight.Bold) 
                } 
            }
        )
    }

    if (showEpisodeDialog && video != null) {
        val firstEpFocusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            delay(300)
            try { firstEpFocusRequester.requestFocus() } catch(_: Exception) {}
        }
        
        val episodes = video!!.episodes.filter { ep ->
            val low = ep.name.lowercase()
            !low.contains("lihat semua") && !low.contains("see all") && 
            !low.contains("episode list") && !low.contains("daftar episode") &&
            !low.contains("next") && !low.contains("prev") && !low.contains("halaman")
        }

        AlertDialog(
            onDismissRequest = { showEpisodeDialog = false },
            title = { Text(stringResource(R.string.select_episode), color = Color.White) },
            containerColor = Color(0xFF1A1A1A),
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                ) {
                    itemsIndexed(episodes) { index, ep ->
                        val isSelected = currentEpisode?.url == ep.url
                        var isFocused by remember { mutableStateOf(false) }
                        val displayName = remember(ep.name, video?.title, video?.videoUrl) {
                            com.duta.movie.util.VideoExtractor.cleanEpisodeTitle(ep.name, video?.title ?: "", video?.videoUrl ?: "")
                        }
                        ListItem(
                            headlineContent = { 
                                Text(
                                    displayName, 
                                    color = if (isSelected) Color.Red else Color.White,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ) 
                            },
                            supportingContent = { if (ep.season.isNotEmpty()) Text(ep.season, color = Color.Gray, fontSize = 12.sp) },
                            leadingContent = { 
                                Icon(
                                    Icons.Default.PlayCircle, 
                                    null, 
                                    tint = if (isSelected) Color.Red else Color.Gray,
                                    modifier = Modifier.size(24.dp)
                                ) 
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (index == 0) Modifier.focusRequester(firstEpFocusRequester) else Modifier)
                                .onFocusChanged { isFocused = it.isFocused }
                                .clickable { 
                                    viewModel.playTVSeries(videoId, ep.url, targetEpisode = ep)
                                    showEpisodeDialog = false 
                                }
                                .focusable()
                                .border(if (isFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(8.dp)),
                            colors = ListItemDefaults.colors(
                                containerColor = if (isFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent
                            )
                        )
                    }
                }
            },
            confirmButton = {
                var isCloseFocused by remember { mutableStateOf(false) }
                TextButton(
                    onClick = { showEpisodeDialog = false },
                    modifier = Modifier
                        .onFocusChanged { isCloseFocused = it.isFocused }
                        .focusable()
                        .background(if (isCloseFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent, RoundedCornerShape(8.dp))
                        .border(if (isCloseFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(8.dp))
                ) { Text(stringResource(R.string.close), color = if (isCloseFocused) Color.White else Color.Red) }
            }
        )
    }
}

@UnstableApi
@Composable
fun VideoPlayerContent(
    nukerScript: String,
    extractedUrl: String?,
    isLoading: Boolean,
    error: String?,
    playerErrorMessage: String?,
    isTransitionComplete: Boolean,
    useWebView: Boolean,
    isCasting: Boolean,
    isVideoReady: Boolean,
    currentCues: List<Cue>,
    showControls: Boolean,
    isInPip: Boolean,
    isLandscape: Boolean,
    isFullscreen: Boolean,
    showManualPlay: Boolean,
    video: Video?,
    exoPlayer: ExoPlayer,
    castPlayer: CastPlayer?,
    webPlayerState: WebPlayerState,
    webViewRef: MutableState<android.webkit.WebView?>,
    qualityTracks: List<VideoQualityTrack>,
    selectedQualityGroupIndex: Int,
    seekFeedback: String?,
    currentEpisode: Episode?,
    playPauseFocusRequester: FocusRequester,
    controlsFocusRequester: FocusRequester,
    onBackClick: () -> Unit,
    onVisibilityToggle: () -> Unit,
    onFullscreenToggle: () -> Unit,
    onServerListClick: () -> Unit,
    onSubtitleClick: () -> Unit,
    onSyncClick: () -> Unit,
    onPipClick: () -> Unit,
    onInteraction: () -> Unit,
    onControlFocusChange: (Boolean) -> Unit,
    onQualitySelect: (VideoQualityTrack) -> Unit,
    onNextEpisodeClick: () -> Unit,
    onEpisodeListClick: () -> Unit,
    onRetryClick: () -> Unit,
    onForceWebViewClick: () -> Unit,
    onNextServerClick: () -> Unit,
    onFindAlternativesClick: () -> Unit = {},
    onPlayerStateChange: (Int, Float, Float) -> Unit,
    onStreamFound: (String, String?, String?) -> Unit,
    onPlaybackSuccess: (String) -> Unit,
    onGateStuck: (String, String) -> Unit,
    onMirrorDead: (String) -> Unit,
    onSeekFeedback: (String?) -> Unit,
    lastReferer: String?,
    lastCookies: String?,
    forceWebViewHosts: Set<String>,
    isResolving: Boolean,
    resolutionProgress: String?,
    isRevealed: Boolean = false,
    onLogClick: () -> Unit,
    isTV: Boolean = false,
    activeContentKey: String,
    onUserPauseChange: (Boolean) -> Unit = {},
    fallbackDurationMs: Long = 0L
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isYouTube = remember(extractedUrl) {
        extractedUrl?.let { it.contains("youtube") || it.contains("youtu.be") } ?: false
    }
    val isBilibili = remember(extractedUrl) {
        extractedUrl?.let { it.contains("bilibili.com") || it.contains("bilibili.tv") } ?: false
    }
    val isDailymotion = remember(extractedUrl) {
        extractedUrl?.let { it.contains("dailymotion.com") || it.contains("dai.ly") } ?: false
    }
    val currentWebState by rememberUpdatedState(webPlayerState)
    val webListeners = remember { java.util.concurrent.CopyOnWriteArraySet<Player.Listener>() }

    val webForwardingPlayer = remember(webViewRef.value, fallbackDurationMs) {
        object : ForwardingPlayer(exoPlayer) {
            override fun getPlayWhenReady() = currentWebState.isPlaying
            override fun isPlaying() = currentWebState.isPlaying
            override fun getDuration() = if (currentWebState.duration > 0L) currentWebState.duration else fallbackDurationMs
            override fun getCurrentPosition() = currentWebState.position
            override fun getPlaybackState() = if (isVideoReady) Player.STATE_READY else Player.STATE_BUFFERING
            override fun play() {
                onUserPauseChange(false)
                val script = """
                    (function() {
                        if (window.playerBridge && typeof window.playerBridge.play === 'function') {
                            try { window.playerBridge.play(); return; } catch(e) {}
                        }
                        var v = document.querySelector('video');
                        if (v) { v.play().catch(function(){}); }
                        var ifrs = document.querySelectorAll('iframe');
                        for (var i = 0; i < ifrs.length; i++) {
                            try {
                                ifrs[i].contentWindow.postMessage(JSON.stringify({ event: 'command', func: 'playVideo', args: [] }), '*');
                                ifrs[i].contentWindow.postMessage(JSON.stringify({ method: 'play' }), '*');
                                ifrs[i].contentWindow.postMessage(JSON.stringify({ type: 'play' }), '*');
                            } catch(e) {}
                        }
                    })();
                """.trimIndent()
                safeEvaluateJavascript(webViewRef.value, script)
            }
            override fun pause() {
                onUserPauseChange(true)
                val script = """
                    (function() {
                        if (window.playerBridge && typeof window.playerBridge.pause === 'function') {
                            try { window.playerBridge.pause(); return; } catch(e) {}
                        }
                        var v = document.querySelector('video');
                        if (v) { v.pause(); }
                        var ifrs = document.querySelectorAll('iframe');
                        for (var i = 0; i < ifrs.length; i++) {
                            try {
                                ifrs[i].contentWindow.postMessage(JSON.stringify({ event: 'command', func: 'pauseVideo', args: [] }), '*');
                                ifrs[i].contentWindow.postMessage(JSON.stringify({ method: 'pause' }), '*');
                                ifrs[i].contentWindow.postMessage(JSON.stringify({ type: 'pause' }), '*');
                            } catch(e) {}
                        }
                    })();
                """.trimIndent()
                safeEvaluateJavascript(webViewRef.value, script)
            }
            override fun seekTo(positionMs: Long) {
                val time = positionMs / 1000.0
                val script = """
                    (function() {
                        var t = $time;
                        if (window.playerBridge && typeof window.playerBridge.seek === 'function') {
                            try { window.playerBridge.seek(t); return; } catch(e) {}
                        }
                        var v = document.querySelector('video');
                        if (v) { v.currentTime = t; }
                        var ifrs = document.querySelectorAll('iframe');
                        for (var i = 0; i < ifrs.length; i++) {
                            try {
                                ifrs[i].contentWindow.postMessage(JSON.stringify({ event: 'command', func: 'seekTo', args: [t, true] }), '*');
                                ifrs[i].contentWindow.postMessage(JSON.stringify({ method: 'seek', value: t }), '*');
                                ifrs[i].contentWindow.postMessage(JSON.stringify({ type: 'seek', value: t }), '*');
                                ifrs[i].contentWindow.postMessage({ type: 'player:seek', time: t }, '*');
                            } catch(e) {}
                        }
                    })();
                """.trimIndent()
                safeEvaluateJavascript(webViewRef.value, script)
                webListeners.forEach { it.onPositionDiscontinuity(Player.DISCONTINUITY_REASON_SEEK) }
            }
            override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
                seekTo(positionMs)
            }
            override fun seekBack() {
                val target = (currentPosition - 10000).coerceAtLeast(0)
                seekTo(target)
            }
            override fun seekForward() {
                val target = (currentPosition + 10000).coerceAtMost(duration)
                seekTo(target)
            }
            override fun addListener(listener: Player.Listener) {
                webListeners.add(listener)
                super.addListener(listener)
            }
            override fun removeListener(listener: Player.Listener) {
                webListeners.remove(listener)
                super.removeListener(listener)
            }
        }
    }

    LaunchedEffect(webPlayerState.isPlaying, isVideoReady) {
        val state = if (isVideoReady) Player.STATE_READY else Player.STATE_BUFFERING
        webListeners.forEach { listener ->
            listener.onPlaybackStateChanged(state)
            listener.onPlayWhenReadyChanged(webPlayerState.isPlaying, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            listener.onIsPlayingChanged(webPlayerState.isPlaying)
        }
    }

    val currentPlayer: Player = if (isCasting && castPlayer != null) {
        castPlayer
    } else if (useWebView) {
        webForwardingPlayer
    } else {
        exoPlayer
    }
    val scope = rememberCoroutineScope()
    
    var pendingSeekOffset by remember { mutableStateOf(0L) }
    var baseSeekPosition by remember { mutableStateOf(-1L) }
    
    LaunchedEffect(pendingSeekOffset) {
        if (pendingSeekOffset != 0L) {
            kotlinx.coroutines.delay(400)
            val base = if (baseSeekPosition >= 0) baseSeekPosition else currentPlayer.currentPosition
            val effectiveDur = when {
                currentPlayer.duration > 0L -> currentPlayer.duration
                fallbackDurationMs > 0L -> fallbackDurationMs
                else -> Long.MAX_VALUE
            }
            val newPos = (base + pendingSeekOffset).coerceIn(0L, effectiveDur)
            currentPlayer.seekTo(newPos)
            pendingSeekOffset = 0L
            baseSeekPosition = -1L
        } else {
            baseSeekPosition = -1L
        }
    }
    
    val handleSeek = { offsetMs: Long ->
        if (baseSeekPosition == -1L) baseSeekPosition = currentPlayer.currentPosition
        pendingSeekOffset += offsetMs
        val totalSec = Math.abs(pendingSeekOffset) / 1000L
        val dir = if (pendingSeekOffset >= 0L) "Forward" else "Back"
        val formatted = if (totalSec >= 60L) {
            val m = totalSec / 60L
            val s = totalSec % 60L
            if (s > 0L) "$dir ${m}m ${s}s" else "$dir ${m}m"
        } else {
            "$dir ${totalSec}s"
        }
        onSeekFeedback(formatted)
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)
        .onKeyEvent { keyEvent ->
            if (keyEvent.type == KeyEventType.KeyDown) {
                onInteraction()
                when (keyEvent.nativeKeyEvent.keyCode) {
                    android.view.KeyEvent.KEYCODE_BACK, android.view.KeyEvent.KEYCODE_BUTTON_B -> {
                        if (showControls) { onVisibilityToggle(); true } else false
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> {
                        currentPlayer.play()
                        true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        currentPlayer.pause()
                        true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_STOP -> {
                        currentPlayer.pause()
                        true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (currentPlayer.isPlaying) currentPlayer.pause() else currentPlayer.play()
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER, 
                    android.view.KeyEvent.KEYCODE_ENTER,
                    android.view.KeyEvent.KEYCODE_NUMPAD_ENTER,
                    android.view.KeyEvent.KEYCODE_BUTTON_A -> {
                        if (!showControls) { 
                            if (currentPlayer.isPlaying) currentPlayer.pause() else currentPlayer.play()
                            true 
                        } else false
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (!showControls) { 
                            handleSeek(-10000L)
                            true 
                        } else false
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (!showControls) { 
                            handleSeek(10000L)
                            true 
                        } else false
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, 
                    android.view.KeyEvent.KEYCODE_MEDIA_STEP_FORWARD,
                    android.view.KeyEvent.KEYCODE_BUTTON_R1 -> {
                        handleSeek(10000L)
                        true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_REWIND, 
                    android.view.KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD,
                    android.view.KeyEvent.KEYCODE_BUTTON_L1 -> {
                        handleSeek(-10000L)
                        true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        if (onNextEpisodeClick != null) {
                            onNextEpisodeClick()
                            true
                        } else false
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        currentPlayer.seekTo(0L)
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                        if (!showControls) { 
                            onVisibilityToggle()
                            true
                        } else false
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (!showControls) { 
                            if (video?.isSeries == true) {
                                onEpisodeListClick()
                            } else {
                                onVisibilityToggle()
                            }
                            true
                        } else false
                    }
                    android.view.KeyEvent.KEYCODE_MENU, android.view.KeyEvent.KEYCODE_INFO -> {
                        onVisibilityToggle()
                        true
                    }
                    else -> false
                }
            } else false
        }
        .focusable()
        .focusRequester(controlsFocusRequester)
        .pointerInput(Unit) {
            detectTapGestures(onDoubleTap = { offset ->
                val isRight = offset.x > size.width / 2
                if (isRight) handleSeek(10000L) else handleSeek(-10000L)
            }, onTap = { onVisibilityToggle() })
        }, contentAlignment = Alignment.Center) {
        
        if (extractedUrl != null && error == null && playerErrorMessage == null && isTransitionComplete) {
            val showWebView = useWebView && !isCasting
            if (showWebView) {
                VideoPlayerWebView(
                    nukerScript = nukerScript,
                    url = extractedUrl,
                    isCasting = isCasting,
                    isVideoReady = isVideoReady,
                    webViewRef = webViewRef,
                    forceWebViewHosts = forceWebViewHosts,
                    onPlayerStateChange = onPlayerStateChange,
                    onStreamFound = onStreamFound,
                    onPlaybackSuccess = onPlaybackSuccess,
                    onGateStuck = onGateStuck,
                    onMirrorDead = onMirrorDead,
                    lastReferer = lastReferer,
                    activeContentKey = activeContentKey,
                    isTV = isTV,
                    // Keep WebView fully attached at alpha 1f; Compose's AnimatedVisibility overlay cleanly covers loading
                    modifier = Modifier.fillMaxSize() 
                )
            }

            if (isCasting) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Cast, contentDescription = null, tint = Color.Red, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.casting_to_device), color = Color.White, fontWeight = FontWeight.Bold)
                        Text(video?.title ?: "", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            } else if (!useWebView) {
                AndroidView(factory = { ctx -> 
                    PlayerView(android.view.ContextThemeWrapper(ctx, R.style.TexturePlayerStyle)).apply {
                        player = exoPlayer
                        useController = false
                        try { findViewById<android.view.View>(androidx.media3.ui.R.id.exo_shutter)?.visibility = android.view.View.GONE } catch(_: Exception) {}
                        subtitleView?.visibility = android.view.View.GONE
                        keepScreenOn = true
                    } 
                }, modifier = Modifier.fillMaxSize().alpha(if (isVideoReady) 1f else 0f), update = { 
                    it.player = exoPlayer
                })
            }
        }

        if (isVideoReady && !isInPip && !isCasting && currentCues.isNotEmpty()) {
            val subtitleText = remember(currentCues) {
                currentCues.mapNotNull { it.text?.toString() }
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
            }
            if (subtitleText.isNotBlank()) {
                val bottomPadding = if (showControls) 110.dp else 48.dp
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(bottom = bottomPadding, start = 24.dp, end = 24.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Surface(
                        color = Color(0xCC000000), // High contrast 80% black pill backdrop
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onVisibilityToggle() }
                    ) {
                        Text(
                            text = subtitleText,
                            color = Color.White,
                            fontSize = if (isTV) 28.sp else if (isLandscape) 22.sp else 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            style = TextStyle(
                                shadow = Shadow(
                                    color = Color.Black,
                                    offset = Offset(2f, 2f),
                                    blurRadius = 4f
                                )
                            ),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        if (isVideoReady) {
            PlayerControls(
                player = currentPlayer, 
                isVisible = showControls && !isInPip, 
                onVisibilityToggle = onVisibilityToggle, 
                onFullscreenToggle = onFullscreenToggle, 
                isFullscreen = isFullscreen, 
                title = video?.title ?: "",
                modifier = Modifier.onFocusChanged { focusState -> onControlFocusChange(focusState.hasFocus) },
                onServerListClick = onServerListClick, 
                onSubtitleClick = onSubtitleClick, 
                onSyncClick = onSyncClick,
                onEpisodeListClick = onEpisodeListClick,
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
                        val idx = episodes.indexOfFirst { 
                            it.url == currentEp.url || it.id == currentEp.id || 
                            com.duta.movie.util.VideoExtractor.extractStableId(it.url) == slug ||
                            it.url.contains(slug)
                        }
                        if (idx == -1 && episodes.isNotEmpty()) 0 else idx
                    } else if (episodes.isNotEmpty()) 0 else -1
                    
                    if (currentIndex != -1 && currentIndex < episodes.size - 1) onNextEpisodeClick else null
                },
                onOpenExternalClick = when {
                    isYouTube -> {
                        {
                            val rawUrl = extractedUrl ?: ""
                            val ytId = com.duta.movie.util.VideoExtractor.extractYouTubeId(rawUrl)
                            if (ytId != null) {
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/watch?v=$ytId"))
                                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } catch(_: Exception) {}
                            }
                        }
                    }
                    isBilibili -> {
                        {
                            val rawUrl = extractedUrl ?: ""
                            val bvid = com.duta.movie.util.VideoExtractor.extractBilibiliBvid(rawUrl)
                            val externalUrl = if (bvid != null) "https://www.bilibili.com/video/$bvid" else rawUrl
                            try {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(externalUrl))
                                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } catch(_: Exception) {}
                        }
                    }
                    isDailymotion -> {
                        {
                            val rawUrl = extractedUrl ?: ""
                            val dmId = com.duta.movie.util.VideoExtractor.extractDailymotionId(rawUrl)
                            val externalUrl = if (dmId != null) "https://www.dailymotion.com/video/$dmId" else rawUrl
                            try {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(externalUrl))
                                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } catch(_: Exception) {}
                        }
                    }
                    else -> null
                },
                externalBadge = when {
                    isYouTube -> "YouTube"
                    isBilibili -> "Bilibili"
                    isDailymotion -> "Dailymotion"
                    else -> null
                },
                playPauseFocusRequester = playPauseFocusRequester,
                fallbackDurationMs = fallbackDurationMs,
                isTV = isTV
            )
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = !isVideoReady && !isRevealed && error == null,
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
                val isArchiveStream = extractedUrl?.contains("archive.org") == true
                var bufferSeconds by remember(extractedUrl) { mutableIntStateOf(0) }
                LaunchedEffect(extractedUrl) {
                    bufferSeconds = 0
                    while (true) {
                        delay(1000)
                        bufferSeconds++
                    }
                }

                val loadingMessage = when {
                    isResolving -> resolutionProgress ?: "Connecting..."
                    isArchiveStream && bufferSeconds < 4 -> "Menyambung Arkib Filem Klasik..."
                    isArchiveStream -> "Memuat turun indeks video Arkib (sedia sebentar)..."
                    else -> "Loading..."
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = Color.Red, 
                        strokeWidth = if (isTV) 4.dp else 2.dp, 
                        modifier = Modifier.size(if (isTV) 48.dp else 32.dp).alpha(if (isRevealed) 0.3f else 1f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        loadingMessage, 
                        color = Color.White.copy(alpha = if (isRevealed) 0.4f else 1f), 
                        fontSize = if (isTV) 16.sp else 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // EMERGENCY CONTROLS: Very low opacity, only after long wait
                var showEmergencyButtons by remember(extractedUrl) { mutableStateOf(false) }
                LaunchedEffect(extractedUrl, isArchiveStream) {
                    showEmergencyButtons = false
                    val emergencyDelay = if (isArchiveStream) 28000L else 14000L
                    delay(emergencyDelay)
                    showEmergencyButtons = true
                }
                
                if (showEmergencyButtons) {
                    Box(modifier = Modifier.fillMaxSize().padding(bottom = 64.dp), contentAlignment = Alignment.BottomCenter) {
                        var isMirrorFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = onNextServerClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isMirrorFocused) Color.White else Color(0xFF333333) // Solid Dark Gray
                            ),
                            modifier = Modifier
                                .onFocusChanged { isMirrorFocused = it.isFocused }
                                .scale(if (isMirrorFocused) 1.2f else 1f)
                        ) {
                            Text(
                                "Try Another Mirror", 
                                fontSize = if (isTV) 14.sp else 10.sp, 
                                color = if (isMirrorFocused) Color.Black else Color.White
                            )
                        }
                    }
                }
            }
        }
        
        error?.let { msg ->
            Box(modifier = Modifier.fillMaxSize().background(Color.Black).zIndex(10000f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(Icons.Default.Error, contentDescription = null, tint = Color.Red, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(msg, color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        var isBackFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = onBackClick, 
                            colors = ButtonDefaults.buttonColors(containerColor = if (isBackFocused) Color.White else Color(0xFF444444)),
                            modifier = Modifier
                                .onFocusChanged { isBackFocused = it.isFocused }
                                .scale(if (isBackFocused) 1.1f else 1f)
                        ) { Text(stringResource(R.string.back), color = if (isBackFocused) Color.Black else Color.White) }

                        var isAltFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = onFindAlternativesClick,
                            colors = ButtonDefaults.buttonColors(containerColor = if (isAltFocused) Color.White else Color.Red),
                            modifier = Modifier
                                .onFocusChanged { isAltFocused = it.isFocused }
                                .scale(if (isAltFocused) 1.1f else 1f)
                        ) { Text(stringResource(R.string.find_other_sources), color = if (isAltFocused) Color.Black else Color.White) }

                        var isRetryFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = onRetryClick, 
                            colors = ButtonDefaults.buttonColors(containerColor = if (isRetryFocused) Color.White else Color(0xFF333333)),
                            modifier = Modifier
                                .onFocusChanged { isRetryFocused = it.isFocused }
                                .scale(if (isRetryFocused) 1.1f else 1f)
                        ) { Text(stringResource(R.string.retry), color = if (isRetryFocused) Color.Black else Color.White) }
                        
                        var isWvFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = onForceWebViewClick, 
                            colors = ButtonDefaults.buttonColors(containerColor = if (isWvFocused) Color.White else Color.DarkGray),
                            modifier = Modifier
                                .onFocusChanged { isWvFocused = it.isFocused }
                                .scale(if (isWvFocused) 1.1f else 1f)
                        ) { Text(stringResource(R.string.force_webview), color = if (isWvFocused) Color.Black else Color.White) }
                        
                        var isLogFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = onLogClick, 
                            colors = ButtonDefaults.buttonColors(containerColor = if (isLogFocused) Color.White else Color.Gray),
                            modifier = Modifier
                                .onFocusChanged { isLogFocused = it.isFocused }
                                .scale(if (isLogFocused) 1.1f else 1f)
                        ) { Text(stringResource(R.string.view_log), color = if (isLogFocused) Color.Black else Color.White) }
                    }
                }
            }
        }
        
        playerErrorMessage?.let { msg ->
           Box(modifier = Modifier.fillMaxSize().background(Color.Black).zIndex(10000f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text(stringResource(R.string.player_error), color = Color.Red, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp))
                    Text(msg, color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center); Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        var isAltFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = onFindAlternativesClick,
                            colors = ButtonDefaults.buttonColors(containerColor = if (isAltFocused) Color.White else Color.Red),
                            modifier = Modifier
                                .onFocusChanged { isAltFocused = it.isFocused }
                                .scale(if (isAltFocused) 1.1f else 1f)
                        ) { Text(stringResource(R.string.find_other_sources), color = if (isAltFocused) Color.Black else Color.White) }

                        var isRetryFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = onRetryClick, 
                            colors = ButtonDefaults.buttonColors(containerColor = if (isRetryFocused) Color.White else Color.Gray),
                            modifier = Modifier
                                .onFocusChanged { isRetryFocused = it.isFocused }
                                .scale(if (isRetryFocused) 1.1f else 1f)
                        ) { Text(stringResource(R.string.retry), color = if (isRetryFocused) Color.Black else Color.White) }

                        var isWvFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = onForceWebViewClick, 
                            colors = ButtonDefaults.buttonColors(containerColor = if (isWvFocused) Color.White else Color.DarkGray),
                            modifier = Modifier
                                .onFocusChanged { isWvFocused = it.isFocused }
                                .scale(if (isWvFocused) 1.1f else 1f)
                        ) { Text(stringResource(R.string.use_webview), color = if (isWvFocused) Color.Black else Color.White) }

                        var isSwitchFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = onServerListClick, 
                            colors = ButtonDefaults.buttonColors(containerColor = if (isSwitchFocused) Color.White else Color(0xFF333333)),
                            modifier = Modifier
                                .onFocusChanged { isSwitchFocused = it.isFocused }
                                .scale(if (isSwitchFocused) 1.1f else 1f)
                        ) { Text(stringResource(R.string.switch_server), color = if (isSwitchFocused) Color.Black else Color.White) }

                        var isLogFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = onLogClick, 
                            colors = ButtonDefaults.buttonColors(containerColor = if (isLogFocused) Color.White else Color.Black),
                            modifier = Modifier
                                .onFocusChanged { isLogFocused = it.isFocused }
                                .scale(if (isLogFocused) 1.1f else 1f)
                        ) { Text(stringResource(R.string.log), color = if (isLogFocused) Color.Black else Color.White) }
                    }
                }
            }
        }
        
        if (seekFeedback != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Surface(color = Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(if (isTV) 48.dp else 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (seekFeedback.contains("Forward")) Icons.Default.FastForward else Icons.Default.FastRewind, 
                            contentDescription = null, 
                            tint = Color.White, 
                            modifier = Modifier.size(if (isTV) 84.dp else 48.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(seekFeedback, color = Color.White, fontSize = if (isTV) 32.sp else 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun VideoPlayerWebView(
    nukerScript: String,
    url: String,
    isCasting: Boolean,
    isVideoReady: Boolean,
    webViewRef: MutableState<android.webkit.WebView?>,
    forceWebViewHosts: Set<String>,
    onPlayerStateChange: (Int, Float, Float) -> Unit,
    onStreamFound: (String, String?, String?) -> Unit,
    onPlaybackSuccess: (String) -> Unit,
    onGateStuck: (String, String) -> Unit,
    onMirrorDead: (String) -> Unit,
    lastReferer: String?,
    activeContentKey: String,
    isTV: Boolean = false,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    // OWL'S EYE: Total Blackout Handshake
    Box(modifier = modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
        AndroidView(
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    webViewRef.value = this
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                
                // TV FOCUS FIX: Prevent WebView from stealing DPAD focus from Compose controls
                val isTVDevice = isTV || com.duta.movie.util.DeviceUtils.isTvDevice(ctx)
                if (isTVDevice) {
                    isFocusable = false
                    isFocusableInTouchMode = false
                }

                settings.apply {
                    javaScriptEnabled = true; domStorageEnabled = true; databaseEnabled = true
                    allowFileAccess = false; allowContentAccess = false
                    mediaPlaybackRequiresUserGesture = false; mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    setSupportMultipleWindows(true) // Required to intercept and block window.open and target="_blank"
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    setSupportZoom(false)
                    val low = url.lowercase()
                    val isYouTube = low.contains("youtube") || low.contains("youtu.be")
                    val isBilibili = low.contains("bilibili.com") || low.contains("bilibili.tv")
                    val isDailymotion = low.contains("dailymotion.com") || low.contains("dai.ly")
                    val isStrict = !isYouTube && !isBilibili && !isDailymotion && (
                        low.contains("zeus") || low.contains("klik") || low.contains("/amt/") || low.contains(".amt") || low.contains("amt1.pro") || low.contains("amt2.pro") || 
                        low.contains("playerp2p") || low.contains("abyss") || low.contains("voe") ||
                        low.contains("indostream") || low.contains("iplayer") || low.contains("masuk") ||
                        low.contains("eddie") || low.contains("bokin") || low.contains("pencuri") ||
                        low.contains("garylarge") || low.contains("huntrex") || low.contains("vibuxer") ||
                        low.contains("hanerix") || low.contains("embed4me") || low.contains("pm21") || 
                        low.contains("dm21") || low.contains("dhcplay") || low.contains("morencius") ||
                        low.contains("bestcdn") || low.contains("distributedcomputing") ||
                        low.contains("ryderjet") || low.contains("faststream") || low.contains("veev") ||
                        low.contains("dood") || low.contains("ohio") || low.contains("vplay") ||
                        low.contains("swhoi") || low.contains("bestcdn") ||
                        forceWebViewHosts.any { low.contains(it) }
                    )
                    userAgentString = if (isStrict || isBilibili || (isDailymotion && isTVDevice)) NetworkConfig.MOBILE_USER_AGENT else NetworkConfig.SHARED_USER_AGENT
                }
                
                // Native hardware compositor direct to window surface
                setLayerType(android.view.View.LAYER_TYPE_NONE, null)
                
                val cookieManager = android.webkit.CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)
                addJavascriptInterface(object : Any() {
                    @android.webkit.JavascriptInterface
                    fun log(msg: String) {
                        Log.d("VideoPlayerNuker", msg)
                    }
                    @android.webkit.JavascriptInterface
                    fun isWebViewActive(): Boolean = true
                    @android.webkit.JavascriptInterface
                    fun notifyVideoPlaying() {
                        onPlaybackSuccess(url)
                    }
                    @android.webkit.JavascriptInterface
                    fun notifyGateStuck(currentUrl: String) {
                        val activeUrl = this@apply.getTag(R.id.active_url) as? String ?: url
                        Log.e("VideoPlayerNuker", "Gate Stuck callback triggered for $currentUrl (original: $activeUrl)")
                        onGateStuck(currentUrl, activeUrl)
                    }
                    @android.webkit.JavascriptInterface fun notifyMirrorDead() { scope.launch { onMirrorDead(url) } }
                    @android.webkit.JavascriptInterface fun onFoundStream(u: String, ref: String?, cook: String?) {
                        if (u.isEmpty()) return
                        val isDirectEmbed = url.contains("youtube") || url.contains("youtu.be") || 
                                            url.contains("bilibili.com") || url.contains("bilibili.tv")
                        if (isDirectEmbed) return
                        val low = u.lowercase()
                        // OWL'S EYE: Improved Tracker and Fake Stream Suppression
                        if (low.contains("tiktokcdn.com") || low.contains("ad-site") || low.contains("image?lk3s=") || low.contains("/hlsmod/") ||
                            low.contains("analytics") || low.contains("/collect") || low.contains("pixel") || 
                            low.contains("yandex") || low.contains("metrika") || low.contains("google-analytics") ||
                            low.contains("doubleclick") || low.contains("histats") || low.contains("imasdk") || low.contains("googleapis") ||
                            low.contains("notification") || low.contains("bonus-stars") || low.contains("bakestubborn") || low.contains("gambling") ||
                            low.contains("pagead") || low.contains("googleads") || low.contains("/aclk") ||
                            low.contains(".ico") || low.contains(".png") || low.contains(".jpg") || 
                            low.contains(".jpeg") || low.contains(".gif") || low.contains(".svg")) return
                            
                        val isLegit = low.startsWith("http") && (low.contains(".m3u8") || low.contains(".mp4") || low.contains(".mkv") || low.contains(".webm") || low.contains(".txt") || low.contains("/amt/") || low.contains(".amt") || low.contains("amt1.pro") || low.contains("amt2.pro") || low.contains("haneri"))
                        val isProtected = (low.contains("playmogo") || 
                                          low.contains("digitalidentity") || low.contains("sunrisevalleycreative") ||
                                          low.contains("johnfullwonder") || low.contains("voe") ||
                                          low.contains("platformdocumentation") || low.contains("hgcloud") || low.contains("hglink") ||
                                          com.duta.movie.util.VideoExtractor.isJsOnlyHost(u) ||
                                          com.duta.movie.util.VideoExtractor.isJsOnlyHost(url)) &&
                                          !low.contains(".m3u8") && !low.contains(".mp4") && !low.contains(".mkv") && !low.contains(".webm") && !low.contains(".txt") && !low.contains("cloudwindow")
                        if (!isLegit || isProtected) return
                        scope.launch(Dispatchers.Main) { 
                             Log.i("VideoPlayerSniffer", "Sniffed Legit Stream: $u | Ref: $ref")
                             onStreamFound(u, ref ?: url, cook) 
                        }
                    }
                    @android.webkit.JavascriptInterface fun onPlayerState(state: Int, time: Float, duration: Float) {
                        scope.launch { onPlayerStateChange(state, time, duration) }
                    }
                }, "AndroidPlayer")
                
                webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun getDefaultVideoPoster(): android.graphics.Bitmap? {
                        return android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
                    }
                    override fun getVideoLoadingProgressView(): android.view.View? {
                        return android.view.View(ctx).apply { setBackgroundColor(android.graphics.Color.TRANSPARENT) }
                    }
                    override fun onPermissionRequest(request: android.webkit.PermissionRequest) {
                        val safeResources = request.resources.filter { 
                            it == android.webkit.PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID 
                        }.toTypedArray()
                        if (safeResources.isNotEmpty()) {
                            request.grant(safeResources)
                        } else {
                            request.deny()
                        }
                    }
                    override fun onCreateWindow(view: android.webkit.WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
                        Log.d("VideoPlayerWebView", "Blocked popup window creation!")
                        return false // Destroys the popup immediately
                    }
                    override fun onProgressChanged(v: android.webkit.WebView?, p: Int) { 
                        if (v?.getTag(R.id.is_destroyed) == true) return
                        if (p > 10) {
                            safeEvaluateJavascript(v, nukerScript)
                        }
                    }
                    override fun onJsAlert(v: android.webkit.WebView?, u: String?, m: String?, r: android.webkit.JsResult?): Boolean { r?.confirm(); return true }
                    override fun onJsConfirm(v: android.webkit.WebView?, u: String?, m: String?, r: android.webkit.JsResult?): Boolean { r?.confirm(); return true }
                    override fun onJsPrompt(v: android.webkit.WebView?, u: String?, m: String?, d: String?, r: android.webkit.JsPromptResult?): Boolean { r?.confirm(); return true }
                    override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                        val msg = consoleMessage?.message() ?: ""
                        val isDirectEmbed = url.contains("youtube") || url.contains("youtu.be") || 
                                            url.contains("bilibili.com") || url.contains("bilibili.tv") ||
                                            url.contains("dailymotion.com") || url.contains("dai.ly")
                        if (isDirectEmbed) return true

                        // Filter spam objects and non-essential logs from flooding Android IPC / logcat
                        val isSpam = msg.isEmpty() || 
                                     msg.startsWith("[object ") || 
                                     msg.startsWith("{\"event\":") ||
                                     msg == "null" || 
                                     msg == "undefined"
                        if (!isSpam && (msg.contains("Nuker", ignoreCase = true) || 
                                        msg.contains("PM", ignoreCase = true) || 
                                        msg.contains("player", ignoreCase = true) || 
                                        msg.contains("video", ignoreCase = true) || 
                                        msg.contains("gate", ignoreCase = true) || 
                                        msg.contains("error", ignoreCase = true) || 
                                        msg.contains("fail", ignoreCase = true) ||
                                        msg.contains("stream", ignoreCase = true) ||
                                        msg.contains("http", ignoreCase = true))) {
                            Log.d("VideoPlayerWebView", "JS Console: ${msg.take(200)}")
                        }
                        val lowMsg = msg.lowercase()
                        if (lowMsg.contains("tiktokcdn.com") || lowMsg.contains("ad-site") || lowMsg.contains("image?lk3s=") || lowMsg.contains("/hlsmod/") ||
                            lowMsg.contains("pagead") || lowMsg.contains("googleads") || lowMsg.contains("doubleclick") || lowMsg.contains("/aclk") || lowMsg.contains("analytics")) {
                            return true
                        }
                        if (msg.contains("https://") && (msg.contains(".m3u8") || msg.contains(".mp4") || msg.contains(".mkv") || msg.contains(".webm") || msg.contains(".txt") || msg.contains("/amt/") || msg.contains(".amt") || msg.contains("amt1.pro") || msg.contains("amt2.pro"))) {
                            if (msg.contains(".js") || msg.contains(".css") || msg.contains(".gif") || msg.contains(".jpg") || msg.contains(".png")) return true
                            if (!msg.contains("analytics")) {
                                val m = Pattern.compile("https?://[^\\s\"'<>|]+").matcher(msg.replace("\\/", "/"))
                                if (m.find()) {
                                    val u = m.group()
                                    val lowU = u.lowercase()
                                    if (lowU.contains("tiktokcdn.com") || lowU.contains("ad-site") || lowU.contains("image?lk3s=") || lowU.contains("/hlsmod/") ||
                                        lowU.contains("pagead") || lowU.contains("googleads") || lowU.contains("doubleclick") || lowU.contains("/aclk")) return true
                                    val isProtected = (lowU.contains("playmogo") || 
                                                      lowU.contains("digitalidentity") || lowU.contains("sunrisevalleycreative") ||
                                                      lowU.contains("johnfullwonder") || lowU.contains("voe") ||
                                                      lowU.contains("platformdocumentation") || lowU.contains("hgcloud") || lowU.contains("hglink") ||
                                                      com.duta.movie.util.VideoExtractor.isJsOnlyHost(u) ||
                                                      com.duta.movie.util.VideoExtractor.isJsOnlyHost(url)) &&
                                                      !lowU.contains(".m3u8") && !lowU.contains(".mp4") && !lowU.contains(".mkv") && !lowU.contains(".webm") && !lowU.contains(".txt") && !lowU.contains("cloudwindow")
                                    if (isProtected) return true

                                    // Deep sniff: extract nested m3u8 from query params if it's a wrapper URL
                                    if (u.contains(".gif") || u.contains("jwplayer")) {
                                        val uri = android.net.Uri.parse(u)
                                        val nested = uri.getQueryParameter("mu") ?: uri.getQueryParameter("file") ?: uri.getQueryParameter("url")
                                        if (nested != null && (nested.contains(".m3u8") || nested.contains(".mp4") || nested.contains(".mkv") || nested.contains(".webm")) && !com.duta.movie.util.VideoExtractor.isJsOnlyHost(nested)) {
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

                    override fun onReceivedSslError(view: android.webkit.WebView?, handler: android.webkit.SslErrorHandler?, error: android.net.http.SslError?) {
                        Log.w("VideoPlayerWebView", "SSL Error bypassed for video stream compatibility: ${error?.url}")
                        handler?.proceed()
                    }

                    override fun onReceivedError(view: android.webkit.WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                        if (request?.isForMainFrame == true) {
                            val activeUrl = view?.getTag(R.id.active_url) as? String ?: url
                            Log.e("VideoPlayerWebView", "Main frame network error: ${error?.description} (${error?.errorCode}) on $activeUrl")
                            onMirrorDead(activeUrl)
                        }
                    }

                    override fun onReceivedHttpError(view: android.webkit.WebView?, request: android.webkit.WebResourceRequest?, errorResponse: android.webkit.WebResourceResponse?) {
                        val statusCode = errorResponse?.statusCode ?: 0
                        val failingUrl = request?.url?.toString() ?: url
                        val lowFail = failingUrl.lowercase()

                        // Ignore trackers, analytics, beacons, and non-video subresources
                        if (lowFail.contains("analytics") || lowFail.contains("/collect") || 
                            lowFail.contains("doubleclick") || lowFail.contains("google-analytics") || 
                            lowFail.contains("histats") || lowFail.contains("metrika") || 
                            lowFail.contains("yandex") || lowFail.contains("pixel") ||
                            lowFail.contains("clarity.ms") || lowFail.contains("facebook") ||
                            lowFail.contains("adservice") || lowFail.contains("googletagmanager")) {
                            return
                        }

                        val isDeadHttp = statusCode in listOf(403, 404, 410, 429, 500, 502, 503, 504)
                        if (isDeadHttp) {
                            val isStaticAsset = lowFail.endsWith(".png") || lowFail.endsWith(".jpg") || 
                                                lowFail.endsWith(".jpeg") || lowFail.endsWith(".gif") || 
                                                lowFail.endsWith(".webp") || lowFail.endsWith(".ico") || 
                                                lowFail.endsWith(".svg") || lowFail.endsWith(".css") || 
                                                lowFail.endsWith(".js") || lowFail.endsWith(".woff") || 
                                                lowFail.endsWith(".woff2") || lowFail.endsWith(".ttf") || 
                                                lowFail.contains("/wp-content/") || lowFail.contains("/assets/")
                            if (isStaticAsset) {
                                return // Ignore static asset 404s (e.g. logo, banner, font)
                            }

                            val host = request?.url?.host?.lowercase() ?: ""
                            val path = request?.url?.path?.lowercase() ?: ""
                            val isTargetEmbed = request?.isForMainFrame == true || 
                                                host.contains("dood") || host.contains("voe") ||
                                                host.contains("indostream") || host.contains("embedo") ||
                                                host.contains("embed4me") || host.contains("playerp2p") || host.contains("upns") ||
                                                host.contains("dutamovie21.xyz") ||
                                                path.contains("/api/v1/video") || path.contains("/api/") ||
                                                path.contains("/e/") || path.contains("/embed/")
                            if (isTargetEmbed) {
                                val activeUrl = view?.getTag(R.id.active_url) as? String ?: url
                                Log.e("VideoPlayerWebView", "HTTP error $statusCode on $failingUrl (mainFrame=${request?.isForMainFrame}) -> declaring mirror dead: $activeUrl")
                                onMirrorDead(activeUrl)
                            }
                        }
                    }

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
                            if (param != null && (param.contains(".m3u8") || param.contains(".mp4") || param.contains(".mkv") || param.contains(".webm") || param.contains(".txt"))) {
                                onStreamFound(param, u, null); return true
                            }
                        }

                        // OWL'S EYE: Instant Gate Rejection
                        if (low.contains("/login") || low.contains("/register") || low.contains("/welcome") || low.contains("/signin") || low.contains("/signup")) {
                             Log.e("VideoPlayerWebView", "Gate Page Detected: $u")
                             onMirrorDead(url)
                             return true
                        }
                        
                        val currentActiveUrl = v?.getTag(R.id.active_url) as? String ?: url
                        val isSafe = u.contains("hgcloud") || u.contains("hglink") || u.contains("voe") || 
                                    u.contains("abyss") || u.contains("indostream") || u.contains("veev") ||
                                    low.contains("/stream/") || low.contains("/embed/") || low.contains("/e/") || low.contains("/v/") ||
                                    low.contains("player") || low.contains("mirror") ||
                                    low.contains("/amt/") || low.contains(".amt") || low.contains("amt1.pro") || low.contains("amt2.pro") || low.contains("haneri") ||
                                    low.contains("morencius") || low.contains("bestcdn") ||
                                    low.contains("ryder") || low.contains("vibuxer") ||
                                    low.contains("audinifer") || low.contains("masuk") ||
                                    low.contains("billofrights") || low.contains("dutamovie") ||
                                    low.contains("pencuri") || low.contains("bokin") ||
                                    low.contains("eddie") || low.contains("seoulschool") ||
                                    low.contains("ladyriders") || low.contains("viatrix") ||
                                    low.contains("ohionewsnow") || low.contains("restaurantesabadell") ||
                                    low.contains("upns.live") || low.contains("upvideo") ||
                                    low.contains("embed4me") || low.contains("playerp2p") ||
                                    low.contains("abyssplayer") || low.contains("bondplayer") ||
                                    low.contains("pandalur") || low.contains("dood") ||
                                    low.contains("playmogo") ||
                                    low.contains("iamcdn") || low.contains("bondcdn") ||
                                    low.contains("abysscdn") || low.contains("johnfullwonder") ||
                                    low.contains("streamtape") || low.contains("dsvplay") ||
                                    low.contains("youtube") || low.contains("youtu.be") || low.contains("googlevideo") ||
                                    low.contains("bilibili.com") || low.contains("bilibili.tv") || low.contains("bilivideo.com") || low.contains("hdslb.com") ||
                                    low.contains("dailymotion.com") || low.contains("dai.ly") || low.contains("dmcdn.net") ||
                                    (currentActiveUrl != null && u.contains(try { android.net.Uri.parse(currentActiveUrl).host ?: "___" } catch(e: Exception) { "___" }))
                        
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
                        
                        // OWL'S EYE: Block intents and market links explicitly, even in iframes!
                        if (low.startsWith("intent:") || low.startsWith("market:") || low.startsWith("play:")) {
                             Log.d("VideoPlayerWebView", "Blocking external app intent: $u")
                             return true
                        }
                        
                        if (!isMainFrame && !isSafe) {
                             Log.d("VideoPlayerWebView", "Blocking unsafe iframe navigation to: $u")
                             return true
                        }
                        
                        return !isSafe
                    }
                    override fun onPageFinished(v: android.webkit.WebView?, u: String?) {
                        if (v?.getTag(R.id.is_destroyed) == true) return
                        Log.d("VideoPlayerSniffer", "Injecting Nuker SCRIPT via onPageFinished")
                        safeEvaluateJavascript(v, nukerScript)
                    }
                    override fun shouldInterceptRequest(view: android.webkit.WebView, r: android.webkit.WebResourceRequest): android.webkit.WebResourceResponse? {
                        val u = r.url.toString()
                        val low = u.lowercase()
                        
                        val isYouTubeAd = low.contains("/pagead/") || low.contains("/api/stats/ads") ||
                                          low.contains("googleads") || low.contains("doubleclick.net") ||
                                          low.contains("googlesyndication.com") ||
                                          (low.contains("googlevideo.com") && (low.contains("adformat=") || low.contains("ad_type=")))
                        if (isYouTubeAd) {
                            Log.d("VideoPlayerTurbo", "Blocked YouTube Ad Network Request: $u")
                            return android.webkit.WebResourceResponse("text/plain", "UTF-8", java.io.ByteArrayInputStream(ByteArray(0)))
                        }

                        val isYouTubeDomain = low.contains("youtube.com") || low.contains("youtube-nocookie.com") || 
                                              low.contains("googlevideo.com") || low.contains("ytimg.com") || 
                                              low.contains("gstatic.com") || low.contains("google.com")
                        if (isYouTubeDomain) {
                            return null // Never block legitimate YouTube/Google assets, video chunks, or fonts
                        }

                        val isBilibiliDomain = low.contains("bilibili.com") || low.contains("bilibili.tv") || 
                                              low.contains("bilivideo.com") || low.contains("hdslb.com")
                        if (isBilibiliDomain) {
                            return null // Never block legitimate Bilibili assets, video chunks, or scripts
                        }

                        val isDmAd = low.contains("dmp.advertising.") || (low.contains("dailymotion.com") && low.contains("/ad/")) ||
                                     low.contains("dmxleo.com")
                        if (isDmAd) {
                            Log.d("VideoPlayerTurbo", "Blocked Dailymotion Ad Asset: $u")
                            return android.webkit.WebResourceResponse("text/javascript", "UTF-8", java.io.ByteArrayInputStream("console.log('DM Ad Blocked');".toByteArray()))
                        }

                        val isDailymotionDomain = low.contains("dailymotion.com") || low.contains("dai.ly") ||
                                                  low.contains("dmcdn.net")
                        if (isDailymotionDomain) {
                            return null // Never block legitimate Dailymotion player assets or video chunks
                        }

                        // Intercept Abyss/Bond Player HTML pages to eradicate the big SVG play button overlay & anti-framing redirect
                        val isAbyssPlayerPage = (low.contains("abyssplayer.com/") || low.contains("bondplayer.com/") || 
                                                 low.contains("abyss.to/") || low.contains("bond.to/")) &&
                                                !low.contains(".js") && !low.contains(".css") && !low.contains(".jpg") && 
                                                !low.contains(".png") && !low.contains(".m3u8") && !low.contains(".mp4") && 
                                                !low.contains(".mkv") && !low.contains(".webm") && 
                                                !low.contains(".ts") && !low.contains(".ico") && !low.contains("/cdn-cgi/")
                        if (isAbyssPlayerPage) {
                            try {
                                val reqBuilder = okhttp3.Request.Builder().url(u)
                                reqBuilder.header("User-Agent", com.duta.movie.util.NetworkConfig.SHARED_USER_AGENT)
                                val referer = lastReferer ?: "${com.duta.movie.util.VideoExtractor.getBaseUrl()}/"
                                reqBuilder.header("Referer", referer)
                                for ((k, v) in r.requestHeaders) {
                                    if (!k.equals("User-Agent", ignoreCase = true) && !k.equals("Referer", ignoreCase = true)) {
                                        reqBuilder.header(k, v)
                                    }
                                }
                                val resp = com.duta.movie.util.NetworkConfig.permissiveOkHttpClient.newCall(reqBuilder.build()).execute()
                                if (resp.isSuccessful) {
                                    var html = resp.body?.string() ?: ""
                                    if (html.isNotEmpty()) {
                                        // 1. Defeat anti-framing / anti-direct redirect
                                        html = html.replace("if(top.location == self.location", "if(false && top.location == self.location")
                                        
                                        // 2. Eradicate SVG play button triangle completely from HTML
                                        html = java.util.regex.Pattern.compile("<svg[^>]*viewBox=[\"']0 0 24 24[\"'][^>]*>.*?</svg>", java.util.regex.Pattern.DOTALL)
                                            .matcher(html).replaceAll("")
                                        
                                        // 3. Add inline styles to hide and neutralize #overlay and #playback
                                        html = html.replace("<div id=\"overlay\">", "<div id=\"overlay\" style=\"display:none!important;opacity:0!important;visibility:hidden!important;pointer-events:none!important;width:0!important;height:0!important;z-index:-99999!important;\">")
                                        html = html.replace("<div id=\"playback\">", "<div id=\"playback\" style=\"display:none!important;opacity:0!important;visibility:hidden!important;pointer-events:none!important;width:0!important;height:0!important;z-index:-99999!important;\">")
                                        
                                        // 4. Inject CSS in <head>
                                        val css = "<style>#overlay, #playback, #overlay *, #playback * { display: none !important; opacity: 0 !important; visibility: hidden !important; pointer-events: none !important; width: 0 !important; height: 0 !important; z-index: -99999 !important; }</style>"
                                        html = if (html.contains("</head>")) html.replace("</head>", "$css</head>") else css + html
                                        
                                        // 5. Inject immediate DOM killer script and Nuker script directly into player frame
                                        val remover = "<script>(function(){ var kill = function(){ var o=document.getElementById('overlay'); if(o){ o.style.display='none'; try{o.remove();}catch(e){} } var p=document.getElementById('playback'); if(p){ p.style.display='none'; try{p.remove();}catch(e){} } }; kill(); setInterval(kill, 200); })();</script><script type=\"text/javascript\">$nukerScript</script>"
                                        html = if (html.contains("</body>")) html.replace("</body>", "$remover</body>") else html + remover
                                        
                                        Log.d("VideoPlayerTurbo", "Neutralized Abyss/Bond player page overlay & redirect: $u")
                                        return android.webkit.WebResourceResponse("text/html", "UTF-8", java.io.ByteArrayInputStream(html.toByteArray(Charsets.UTF_8)))
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w("VideoPlayerTurbo", "Failed to intercept Abyss player page: ${e.message}")
                            }
                        }

                        // DEBUG: Log unusual traffic that might be a stream
                        if (low.contains("playlist") || low.contains("chunk") || low.contains(".m3u") || 
                            low.contains("manifest") || low.contains(".mp4") || low.contains(".mkv") || low.contains(".webm") || low.contains("master") ||
                            low.contains("abysscdn") || low.contains("bond-stream") || low.contains("delivery-node")) {
                             Log.d("VideoPlayerInterception", "Candidate: $u")
                        }

                        // Steganographic / fake chunks (e.g. TikTok CDN PNG chunks disguised as m3u8)
                        // Do not return 0-byte response so WebView players can fetch chunks, but guard ExoPlayer via !isFakeTiktokStream
                        val isFakeTiktokStream = low.contains("tiktokcdn.com") || low.contains("ad-site") || 
                                                 low.contains("image?lk3s=") || low.contains("/hlsmod/")

                        // TURBO SPEED: Block known ad domains, trackers, fake captchas and bot scams instantly at network level
                        val isScamOrFakeCaptcha = (low.contains("robot") && !low.contains("robots.txt") && !low.contains("roboto")) || 
                                                  low.contains("human-verification") || low.contains("verify-you") || 
                                                  low.contains("not-a-robot") || low.contains("press-allow") || 
                                                  low.contains("click-allow") || low.contains("tatterslisses") || 
                                                  low.contains("adsco.re") || low.contains("adscore") || 
                                                  low.contains("ukankingwithea") || low.contains("flushpersist") || 
                                                  low.contains("cloudatacdn") || low.contains("dodoimg") || 
                                                  low.contains(".cyou") || low.contains(".buzz") || low.contains(".cfd") || 
                                                  low.contains(".sbs") || low.contains(".skin") || low.contains(".quest") || low.contains(".monster")

                        if (isScamOrFakeCaptcha ||
                            low.contains("analytics") || low.contains("doubleclick") || low.contains("popads") || 
                            low.contains("onclickads") || low.contains("propellerads") || low.contains("exoclick") ||
                            low.contains("googlesyndication") || low.contains("googleads") || low.contains("pagead2") ||
                            low.contains("securepubads") || low.contains("adservice.google") || low.contains("adservice") ||
                            low.contains("huntrexus") || low.contains("quantserve") || low.contains("id5-sync") ||
                            low.contains("realizationnewestfanatic") || low.contains("flushpersist") || low.contains("copper6") ||
                            low.contains("ottadvisors") || low.contains("rlcdn") || low.contains("histats") || low.contains("pixel.facebook") ||
                            low.contains("/collect?") || low.contains("google-analytics") || low.contains("redgarto.com") ||
                            low.contains("yandex") || low.contains("metrika") || low.contains("bakestubborn") ||
                            low.contains("wantedads") || low.contains("pandalur") || low.contains("adsterra") || 
                            low.contains("a.bestcontent") || low.contains("cpmstar") || 
                            low.contains(".ico") || low.contains(".png") || low.contains(".jpg") || 
                            low.contains(".jpeg") || low.contains(".gif") || low.contains(".svg")) {
                            if (!low.contains("master.m3u8") && !low.contains("index.m3u8") && !low.contains("/amt/") && !low.contains(".amt") && !low.contains("amt1.pro") && !low.contains("amt2.pro")) {
                                Log.d("VideoPlayerTurbo", "Blocked Network Tracker/Ad/Junk: $u")
                                return android.webkit.WebResourceResponse("text/plain", "UTF-8", null)
                            }
                        }

                        val isAd = isScamOrFakeCaptcha || low.contains("notification") || low.contains("bonus-stars") || low.contains("bakestubborn") ||
                                   low.contains("gambling") || low.contains("promo") || low.contains("/ad/") || low.contains("/ads/") ||
                                   low.contains("pagead") || low.contains("/aclk")
                        if (isAd) {
                            Log.d("VideoPlayerTurbo", "Blocked Network Ad Stream: $u")
                            return android.webkit.WebResourceResponse("text/plain", "UTF-8", null)
                        }

                        val isDirectEmbed = url.contains("youtube") || url.contains("youtu.be") || 
                                            url.contains("bilibili.com") || url.contains("bilibili.tv")
                        val isStream = !isDirectEmbed && !isFakeTiktokStream && (low.contains(".m3u8") || low.contains(".mp4") || low.contains(".mkv") || low.contains(".webm") || low.contains(".txt") || 
                                       low.contains(".m3u") || low.contains("master.json") || low.contains("playlist") ||
                                       low.contains("abysscdn") || low.contains("bond-stream") || low.contains("upvideo.link") ||
                                       low.contains("/stream/") || low.contains("manifest") || low.contains("/hl/")) && 
                                       !low.contains("blank.mp4") && !low.contains("empty.mp4") && !low.contains(".js") && !low.contains(".css") && !low.contains("/api/") &&
                                       !low.contains("pagead") && !low.contains("googleads") && !low.contains("doubleclick") && !low.contains("/aclk")
                        
                        val isProtectedStream = (low.contains("playmogo") || 
                                                low.contains("digitalidentity") || low.contains("sunrisevalleycreative") ||
                                                low.contains("johnfullwonder") || low.contains("voe") ||
                                                low.contains("platformdocumentation") || low.contains("hgcloud") || low.contains("hglink") ||
                                                low.contains("hanerix") || low.contains("vibuxer") || low.contains("audinifer") ||
                                                com.duta.movie.util.VideoExtractor.isJsOnlyHost(u) ||
                                                com.duta.movie.util.VideoExtractor.isJsOnlyHost(url)) &&
                                                !low.contains(".m3u8") && !low.contains(".mp4") && !low.contains(".mkv") && !low.contains(".webm") && !low.contains(".txt") && !low.contains("cloudwindow")

                        if (isStream && !r.isForMainFrame && !isProtectedStream) {
                            val streamRef = when {
                                low.contains("cloudwindow") -> "https://johnfullwonder.com/"
                                low.contains("platformdocumentation") || low.contains("hgcloud") -> "https://hgcloud.to/"
                                low.contains("abyss") -> "https://play.abyssplayer.com/"
                                low.contains("dailymotion") || low.contains("cdndirector") || low.contains("dmcdn") -> "https://www.dailymotion.com/"
                                else -> r.requestHeaders["Referer"] ?: url
                            }
                            // Wrapper detection for nested mu parameter
                            if (low.contains(".gif") || low.contains("jwplayer")) {
                                val nested = r.url.getQueryParameter("mu") ?: r.url.getQueryParameter("file") ?: r.url.getQueryParameter("url")
                                if (nested != null && (nested.contains(".m3u8") || nested.contains(".mp4") || nested.contains(".mkv") || nested.contains(".webm")) && !com.duta.movie.util.VideoExtractor.isJsOnlyHost(nested)) {
                                    onStreamFound(nested, streamRef, null)
                                    return null
                                }
                            }
                            onStreamFound(u, streamRef, null)
                        }
                        return null
                    }
                }
            }
        }, modifier = Modifier.fillMaxSize(), update = { view ->
             if (view.getTag(R.id.is_destroyed) == true) return@AndroidView
             val isNewEpisode = view.getTag(R.id.active_content_key) != activeContentKey
              val ytId = com.duta.movie.util.VideoExtractor.extractYouTubeId(url)
              val bvid = com.duta.movie.util.VideoExtractor.extractBilibiliBvid(url)
              val dmId = com.duta.movie.util.VideoExtractor.extractDailymotionId(url)
              if (ytId != null) {
                  if (view.getTag(R.id.active_url) != url || isNewEpisode) {
                      view.setTag(R.id.active_content_key, activeContentKey)
                      view.setTag(R.id.active_url, url)
                      val embedUrl = if (url.contains("/embed/")) url else "https://www.youtube-nocookie.com/embed/$ytId?autoplay=1&controls=0&playsinline=1&enablejsapi=1&rel=0&iv_load_policy=3&fs=0"
                      view.loadUrl(embedUrl, mutableMapOf("Referer" to "https://www.google.com/"))
                  }
              } else if (bvid != null || url.contains("bilibili.com") || url.contains("bilibili.tv")) {
                  if (view.getTag(R.id.active_url) != url || isNewEpisode) {
                      view.setTag(R.id.active_content_key, activeContentKey)
                      view.setTag(R.id.active_url, url)
                      val embedUrl = if (url.contains("player.bilibili.com")) url else "https://player.bilibili.com/player.html?bvid=$bvid&page=1&as_wide=1&high_quality=1&danmaku=0"
                      view.loadUrl(embedUrl, mutableMapOf(
                          "Referer" to "https://www.bilibili.com/"
                      ))
                  }
              } else if (dmId != null || url.contains("dailymotion.com") || url.contains("dai.ly")) {
                  if (view.getTag(R.id.active_url) != url || isNewEpisode) {
                      view.setTag(R.id.active_content_key, activeContentKey)
                      view.setTag(R.id.active_url, url)
                      val embedUrl = if (dmId != null) "https://www.dailymotion.com/embed/video/$dmId?autoplay=1&mute=0&ui-logo=0&sharing-enable=0&ui-start-screen-info=0"
                                     else if (url.contains("/embed/video/")) url
                                     else url
                      view.loadUrl(embedUrl, mutableMapOf("Referer" to "https://www.dailymotion.com/"))
                  }
              } else if (url.contains("abyssplayer.com") || url.contains("bondplayer.com") || url.contains("abyss.to") || url.contains("bond.to")) {
                  if (view.getTag(R.id.active_url) != url || isNewEpisode) {
                      view.setTag(R.id.active_content_key, activeContentKey)
                      view.setTag(R.id.active_url, url)
                      val base = lastReferer ?: "${com.duta.movie.util.VideoExtractor.getBaseUrl()}/"
                      val iframeHtml = """
                          <!DOCTYPE html>
                          <html>
                          <head>
                              <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                              <style>
                                  html, body { margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; background-color: #000; }
                                  iframe { border: none; width: 100%; height: 100%; display: block; }
                              </style>
                          </head>
                          <body>
                              <iframe src="$url" allow="autoplay; fullscreen; encrypted-media; picture-in-picture" allowfullscreen></iframe>
                          </body>
                          </html>
                      """.trimIndent()
                      view.loadDataWithBaseURL(base, iframeHtml, "text/html", "UTF-8", null)
                  }
              } else if (view.url != url || isNewEpisode) {
                 // OWL'S EYE: Sticky Referer Lockdown (v6.8)
                 // Hard-coding the referer to the primary site root ensures gateways don't redirect to home.
                 val referer = lastReferer ?: "${com.duta.movie.util.VideoExtractor.getBaseUrl()}/"
                 
                 if (isTV && isNewEpisode) {
                     Log.i("VideoPlayer", "TV HARD RESET: Clearing WebView for new episode.")
                     view.stopLoading()
                     view.loadUrl("about:blank")
                 }
                 
                 view.setTag(R.id.active_content_key, activeContentKey)
                 view.setTag(R.id.active_url, url)
                 view.loadUrl(url, mutableMapOf("Referer" to referer))
             }
        }, onRelease = { view -> 
            webViewRef.value = null
            try {
                view.setTag(R.id.is_destroyed, true)
                view.stopLoading()
                view.loadUrl("about:blank")
                view.webChromeClient = null
                view.webViewClient = android.webkit.WebViewClient()
                (view.parent as? android.view.ViewGroup)?.removeView(view)
                view.post {
                    try {
                        view.destroy()
                    } catch (e: Throwable) {
                        Log.w("VideoPlayerWebView", "Deferred WebView destroy: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w("VideoPlayerWebView", "Safe WebView teardown: ${e.message}")
            }
        })
    }
}

@Composable
fun ServerSelectionDialog(
    servers: List<com.duta.movie.model.VideoServer>,
    currentServerUrl: String,
    isTV: Boolean = false,
    onServerSelect: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var forceWebView by remember { mutableStateOf(false) }
    val firstItemFocusRequester = remember { FocusRequester() }
    
    LaunchedEffect(Unit) {
        delay(300)
        try { firstItemFocusRequester.requestFocus() } catch(_: Exception) { kotlinx.coroutines.delay(300); try { firstItemFocusRequester.requestFocus() } catch(_: Exception) {} }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.select_server), 
                    color = Color.White,
                    fontSize = if (isTV) 20.sp else 16.sp,
                    fontWeight = FontWeight.Bold
                )
                var isSwitchFocused by remember { mutableStateOf(false) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .onFocusChanged { isSwitchFocused = it.isFocused }
                        .clickable { forceWebView = !forceWebView }
                        .focusable()
                        .border(if (isSwitchFocused) BorderStroke(if (isTV) 2.dp else 1.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(stringResource(R.string.force_webview), color = Color.Gray, fontSize = if (isTV) 14.sp else 12.sp)
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = forceWebView,
                        onCheckedChange = { forceWebView = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.Red),
                        modifier = Modifier.scale(if (isTV) 1.0f else 0.8f).focusable(false)
                    )
                }
            }
        },
        containerColor = Color(0xFF1A1A1A),
        text = {
            val cleanServers = remember(servers) {
                servers.filter { s ->
                    val lowU = s.url.lowercase()
                    val lowN = s.name.lowercase()
                    !lowU.contains("google.com") && !lowU.contains("pagead") && !lowU.contains("/aclk") &&
                    !lowN.contains("google.com") && !lowN.contains("pagead")
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(if (isTV) 6.dp else 2.dp)) {
                itemsIndexed(cleanServers, key = { _, server -> server.url }) { index, server ->
                    val isSelected = server.url == currentServerUrl
                    var isFocused by remember { mutableStateOf(false) }
                    val lowU = server.url.lowercase()
                    val lowN = server.name.lowercase()
                    val platformBadge = remember(server.url, server.name) {
                        when {
                            lowU.contains("youtube") || lowU.contains("youtu.be") || lowN.contains("youtube") -> "YouTube" to Color(0xFFFF0000)
                            lowU.contains("bilibili") || lowN.contains("bilibili") -> "Bilibili" to Color(0xFF00AEEC)
                            lowU.contains("dailymotion") || lowU.contains("dai.ly") || lowN.contains("dailymotion") -> "Dailymotion" to Color(0xFF0066DC)
                            lowN.contains("archive") -> "Archive" to Color(0xFFE67E22)
                            lowN.contains("stream") -> "Stream" to Color(0xFF2ECC71)
                            else -> null
                        }
                    }
                    ListItem(
                        headlineContent = { 
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = server.name,
                                    color = if (isSelected) Color.Red else Color.White,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = if (isTV) 18.sp else 15.sp,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (platformBadge != null) {
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        color = platformBadge.second.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(1.dp, platformBadge.second.copy(alpha = 0.6f))
                                    ) {
                                        Text(
                                            text = platformBadge.first,
                                            color = platformBadge.second,
                                            fontSize = if (isTV) 12.sp else 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        },
                        leadingContent = {
                            val iconColor = if (platformBadge != null) platformBadge.second else (if (isSelected) Color.Red else Color.White)
                            Icon(
                                imageVector = Icons.Default.Dns,
                                contentDescription = null,
                                tint = iconColor,
                                modifier = Modifier.size(if (isTV) 28.dp else 24.dp)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                            .onFocusChanged { isFocused = it.isFocused }
                            .clickable { onServerSelect(server.url, forceWebView) }
                            .focusable()
                            .border(if (isFocused) BorderStroke(if (isTV) 2.5.dp else 2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(8.dp)),
                        colors = ListItemDefaults.colors(
                            containerColor = if (isSelected) Color.Red.copy(alpha = 0.25f) 
                                            else if (isFocused) Color.White.copy(alpha = 0.2f)
                                            else Color.Transparent
                        )
                    )
                }
            }
        },
        confirmButton = {
            var isCloseFocused by remember { mutableStateOf(false) }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .onFocusChanged { isCloseFocused = it.isFocused }
                    .focusable()
                    .background(if (isCloseFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent, RoundedCornerShape(8.dp))
                    .border(if (isCloseFocused) BorderStroke(if (isTV) 2.5.dp else 2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(8.dp))
            ) {
                Text(
                    text = stringResource(R.string.close), 
                    color = if (isCloseFocused) Color.White else Color.Red,
                    fontSize = if (isTV) 16.sp else 14.sp
                )
            }
        }
    )
}

