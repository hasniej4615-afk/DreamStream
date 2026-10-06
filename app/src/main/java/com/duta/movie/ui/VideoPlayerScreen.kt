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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.duta.movie.audio.VoiceEnhancerManager
import com.duta.movie.audio.VoiceEnhancerMode
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

fun isSeriesPlayback(video: com.duta.movie.model.Video?, url: String?): Boolean {
    val isEpisodeUrl = url?.let { 
        (it.contains("/eps/") || it.contains("/episode/") || it.contains("-episode-") || 
         it.contains("/episod/") || it.contains("-episod-") || it.contains("-epi-") || 
         it.contains("/ep-") || it.contains("epid=")) && 
        !it.contains("player=") && !it.contains("mirror=") 
    } ?: false
    val isExplicitSeries = video?.videoUrl?.let { it.contains("/series/") || it.contains("/tv/") || it.contains("/serial-tv/") } ?: false
    val hasEpisodesOrSeriesFlag = video?.isSeries == true || (video?.episodes?.isNotEmpty() == true)
    return isExplicitSeries || hasEpisodesOrSeriesFlag || isEpisodeUrl
}

fun sanitizeAbyssHtml(rawHtml: String, nukerScript: String): String {
    var html = rawHtml
    if (html.isEmpty()) return html

    // 1. Defeat anti-framing / anti-direct redirect
    html = html.replace(Regex("""if\s*\(\s*(?:window\.)?top\.location\s*===\s*(?:window\.)?self\.location"""), "if(false && top.location === self.location")
    html = html.replace(Regex("""if\s*\(\s*(?:window\.)?top\.location\s*==\s*(?:window\.)?self\.location"""), "if(false && top.location == self.location")
    html = html.replace(Regex("""if\s*\(\s*(?:window\.)?top\.location\s*!==\s*(?:window\.)?self\.location"""), "if(true || top.location !== self.location")
    html = html.replace(Regex("""if\s*\(\s*(?:window\.)?top\.location\s*!=\s*(?:window\.)?self\.location"""), "if(true || top.location != self.location")
    html = html.replace(Regex("""if\s*\(\s*(?:window\.)?top\s*===\s*(?:window\.)?self"""), "if(false && top === self")
    html = html.replace(Regex("""if\s*\(\s*(?:window\.)?top\s*==\s*(?:window\.)?self"""), "if(false && top == self")
    html = html.replace(Regex("""if\s*\(\s*(?:window\.)?top\s*!==\s*(?:window\.)?self"""), "if(true || top !== self")
    html = html.replace(Regex("""if\s*\(\s*(?:window\.)?top\s*!=\s*(?:window\.)?self"""), "if(true || top != self")
    html = html.replace(Regex("""(?:window\.)?(?:top\.)?location(?:\.href)?\s*=\s*['"]https?://[^'"]*abyss\.to/?['"]"""), "/* blocked redirect */;")
    html = html.replace(Regex("""(?:window\.)?(?:top\.)?location\.replace\s*\(\s*['"]https?://[^'"]*abyss\.to/?['"]\s*\)"""), "/* blocked redirect */;")
    html = html.replace(Regex("""(?:window\.)?(?:top\.)?location(?:\.href)?\s*=\s*['"]https?://[^'"]*bond\.to/?['"]"""), "/* blocked redirect */;")
    html = html.replace(Regex("""(?:window\.)?(?:top\.)?location\.replace\s*\(\s*['"]https?://[^'"]*bond\.to/?['"]\s*\)"""), "/* blocked redirect */;")
    html = html.replace(Regex("""(?:window\.)?(?:top\.)?location(?:\.href)?\s*=\s*['"]https?://[^'"]*pusatfilm[^'"]*['"]"""), "/* blocked redirect */;")
    html = html.replace(Regex("""(?:window\.)?(?:top\.)?location\.replace\s*\(\s*['"]https?://[^'"]*pusatfilm[^'"]*['"]\s*\)"""), "/* blocked redirect */;")
    
    // 2. Eradicate overlay and playback elements from HTML
    html = Pattern.compile("<div[^>]*id=[\"']overlay[\"'].*?</div>\\s*</div>", Pattern.DOTALL).matcher(html).replaceAll("")
    html = Pattern.compile("<div[^>]*id=[\"']playback[\"'].*?</div>", Pattern.DOTALL).matcher(html).replaceAll("")

    // 3. Eradicate SVG play button triangles completely from HTML
    html = Pattern.compile("<svg[^>]*viewBox=[\"']0 0 24 24[\"'][^>]*>.*?</svg>", Pattern.DOTALL).matcher(html).replaceAll("")
    html = Pattern.compile("<svg[^>]*viewBox=[\"']0 0 240 240[\"'][^>]*>.*?</svg>", Pattern.DOTALL).matcher(html).replaceAll("")
    html = Pattern.compile("<svg[^>]*class=[\"'][^\"']*jw-svg-icon-play[^\"']*[\"'][^>]*>.*?</svg>", Pattern.DOTALL).matcher(html).replaceAll("")
    
    // 4. Neutralize CSS styles within the page
    html = html.replace("width: 50%; height: 50%;", "width: 0 !important; height: 0 !important; display: none !important;")
    html = html.replace("z-index: 100000;", "z-index: -99999; display: none !important;")
    html = html.replace("#playback svg { width: 50%; height: 50%; fill: #fff; }", "#playback svg, #overlay, #playback { display: none !important; opacity: 0 !important; visibility: hidden !important; width: 0 !important; height: 0 !important; }")
    html = html.replace("id=\"overlay\"", "id=\"overlay\" style=\"display:none!important;opacity:0!important;visibility:hidden!important;pointer-events:none!important;width:0!important;height:0!important;z-index:-99999!important;\"")
    html = html.replace("id=\"playback\"", "id=\"playback\" style=\"display:none!important;opacity:0!important;visibility:hidden!important;pointer-events:none!important;width:0!important;height:0!important;z-index:-99999!important;\"")
    
    // 5. Neutralize Abyss popup click handlers so ads are blocked and overlay is gone
    html = html.replace("if(overlay) {try {overlay.onclick = ra;overlay.ontouchend = ra;} catch (error) {overlay.remove();}}", "if(overlay) { try { overlay.remove(); } catch(e){} }")
    html = html.replace("overlay.onclick = ra;", "if(overlay)overlay.remove();")
    html = html.replace("overlay.ontouchend = ra;", "if(overlay)overlay.remove();")

    // 6. Inject CSS in <head>
    val css = "<style>html, body, .stage, .wrap, #player { background: #000 !important; background-color: #000 !important; } #overlay, #playback, #overlay *, #playback *, div#overlay, div#playback, .jw-display-icon-display, .jw-display-icon-container, .jw-display-icon-idle, .jw-icon-display, .jw-display, .jw-svg-icon-play, .jw-flag-fullscreen .jw-display-icon-display, .jw-button-color, .vjs-big-play-button, .vjs-big-play-button-mobile, .play-button, #play-button, .play-btn, #play-btn, .big-play, .big-play-btn, .big-play-button, .large-play-button, .ytp-large-play-button, .play-overlay, #videoInfo, .video-info, [id*=\"videoInfo\"], [class*=\"video-info\"], .video-info-title, .video-info-hint, .video-info-close, svg, svg[viewBox=\"0 0 24 24\"], svg[viewBox=\"0 0 240 240\"], #playback svg, #overlay svg { display: none !important; opacity: 0 !important; visibility: hidden !important; pointer-events: none !important; width: 0 !important; height: 0 !important; max-width: 0 !important; max-height: 0 !important; z-index: -99999 !important; }</style>"
    html = if (html.contains("</head>")) html.replace("</head>", "$css</head>") else css + html
    
    // 7. Inject immediate DOM killer script and Nuker script directly into player frame
    val remover = "<script>(function(){ var ensureBlack = function() { try { if (document.documentElement) { document.documentElement.style.setProperty('background', '#000', 'important'); document.documentElement.style.setProperty('background-color', '#000', 'important'); } if (document.body) { document.body.style.setProperty('background', '#000', 'important'); document.body.style.setProperty('background-color', '#000', 'important'); } } catch(e){} }; ensureBlack(); var kill = function(){ ensureBlack(); var o=document.getElementById('overlay'); if(o){ o.style.display='none'; try{o.remove();}catch(e){} } var p=document.getElementById('playback'); if(p){ p.style.display='none'; try{p.remove();}catch(e){} } var bads=document.querySelectorAll('#overlay, #playback, div#overlay, div#playback, #videoInfo, .video-info, [id*=\"videoInfo\"], [class*=\"video-info\"], .jw-display-icon-display, .jw-display-icon-container, .jw-display-icon-idle, .jw-icon-display, .jw-display, .jw-svg-icon-play, .vjs-big-play-button, .play-button, #play-button, .play-btn, .big-play-button, svg, svg[viewBox=\"0 0 24 24\"], svg[viewBox=\"0 0 240 240\"]'); for(var i=0;i<bads.length;i++){ bads[i].style.setProperty('display', 'none', 'important'); bads[i].style.setProperty('opacity', '0', 'important'); bads[i].style.setProperty('visibility', 'hidden', 'important'); bads[i].style.setProperty('pointer-events', 'none', 'important'); bads[i].style.setProperty('width', '0', 'important'); bads[i].style.setProperty('height', '0', 'important'); try{bads[i].remove();}catch(e){} } if(typeof window.closeVideoInfo==='function'){ try{window.closeVideoInfo();}catch(e){} } }; kill(); setInterval(kill, 100); })();</script><script type=\"text/javascript\">$nukerScript</script>"
    html = if (html.contains("</body>")) html.replace("</body>", "$remover</body>") else html + remover
    
    return html
}

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
    val rawVideo: Video? by viewModel.videoMetadata.collectAsStateWithLifecycle()
    val cleanVideoId = remember(videoId) { com.duta.movie.util.VideoExtractor.stripSourcePrefix(videoId) }
    val video = remember(rawVideo, videoId) {
        val isMatch: (Video?) -> Boolean = { v ->
            v != null && (v.id == videoId || (cleanVideoId.length >= 3 && com.duta.movie.util.VideoExtractor.stripSourcePrefix(v.id) == cleanVideoId))
        }
        rawVideo?.takeIf(isMatch) ?: viewModel.getVideo(videoId)?.takeIf(isMatch)
    }
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
    val rawCurrentEpisode by viewModel.currentEpisode.collectAsStateWithLifecycle()
    val currentEpisode = remember(rawCurrentEpisode, video, videoId) {
        rawCurrentEpisode?.takeIf { ep ->
            video == null || video.episodes.isEmpty() || video.episodes.any { it.url == ep.url || it.id == ep.id }
        }
    }
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
    val voiceEnhancerMode by viewModel.voiceEnhancerMode.collectAsStateWithLifecycle()
    val voiceEnhancerManager = remember { VoiceEnhancerManager() }

    LaunchedEffect(voiceEnhancerMode) {
        voiceEnhancerManager.setMode(VoiceEnhancerMode.fromId(voiceEnhancerMode))
    }
    
    val subFirstItemFocusRequester = remember { FocusRequester() }
    val localSubtitlePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.loadLocalSubtitle(uri) { success, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                if (success) {
                    showSubtitleDialog = false
                }
            }
        }
    }
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

    val isLive = remember(videoId, video?.duration) {
        com.duta.movie.model.LiveTvCatalog.isLiveVideo(videoId) || video?.duration == "LIVE"
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

    LaunchedEffect(videoId, video?.title, currentEpisode?.url) { video?.title?.let { viewModel.fetchSubtitles(it, isTV = (video?.isSeries == true && video?.episodes?.isNotEmpty() == true) || currentEpisode != null) } }

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

    DisposableEffect(exoPlayer, voiceEnhancerManager) {
        val analyticsListener = object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onAudioSessionIdChanged(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                audioSessionId: Int
            ) {
                if (audioSessionId > 0) {
                    voiceEnhancerManager.attachSession(audioSessionId)
                }
            }
        }
        exoPlayer.addAnalyticsListener(analyticsListener)
        try {
            val sid = exoPlayer.audioSessionId
            if (sid > 0) {
                voiceEnhancerManager.attachSession(sid)
            }
        } catch (_: Throwable) {}

        onDispose {
            try {
                exoPlayer.removeAnalyticsListener(analyticsListener)
            } catch (_: Throwable) {}
            voiceEnhancerManager.release()
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
                            if (isSeriesPlayback(video, betterServer)) {
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
    var lastSeekTimeMs by remember { mutableLongStateOf(0L) }

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
                val isAltPartner = extractedUrl?.let { com.duta.movie.util.VideoExtractor.isAlternativePartnerHost(it) } ?: false
                val isShortFakeDuration = useWebView && !isCasting && !isAltPartner && duration in 1..59_999L && (currentPos >= duration - 2000L || stallCount >= 3)
                if (isShortFakeDuration) {
                    Log.w("VideoPlayer", "Stall Guard: Suspiciously short duration ($duration ms, ended/stuck at $currentPos ms) detected on WebView. Rotating mirror.")
                    if (!isCasting) {
                        extractedUrl?.let { viewModel.notifyPlaybackFailure(it) }
                        viewModel.resolveNextServer(videoId, viewModel.currentServerUrl.value)
                    }
                    break
                }

                val isProgressing = currentPos > lastPos && lastPos != -1L
                val isStalled = if (useWebView && !isCasting) {
                    // OWL'S EYE: WebView embeds have their own dedicated Active Playback Stall Watchdog below
                    // with seek-grace and JS-protected tolerance. Do not rotate WebView here.
                    false
                } else {
                    val isSeekRecent = (System.currentTimeMillis() - lastSeekTimeMs) < 20_000L
                    !isSeekRecent && isPlayingOrBuffering && currentPos == lastPos && lastPos != -1L
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
                    if (!isLive && (isPlayingOrBuffering || isProgressing) && (duration > 0 || duration == androidx.media3.common.C.TIME_UNSET)) {
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
        
        val isRealSeries = isSeriesPlayback(video, serverUrl)
        if (isRealSeries) {
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
            val maxStuck = if (isPm) 18 else 14 // Fast-failover: embeds start within 3-7s, no need to stall for 30s
            
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
    // Detects when playback was running, but subsequent decode or CDN starvation freezes playback.
    // JS-protected streams (vidhide/fujihide/tnmr/morencius) get a longer leash because their session-bound
    // CDN tokens cannot be re-acquired, and temporary buffering stalls are normal.
    // Seek-aware: when position jumps (user scrubbed forward/back) or user initiates a seek,
    // grant a generous grace period for the CDN to buffer at the new position before counting stall time.
    LaunchedEffect(useWebView, isVideoReady, extractedUrl) {
        if (useWebView && isVideoReady && extractedUrl != null) {
            val embedLow = (extractedUrl ?: "").lowercase()
            val isJsProtected = embedLow.contains("vidhide") || embedLow.contains("fujihide") ||
                                embedLow.contains("tnmr.org") || embedLow.contains("morencius") ||
                                embedLow.contains("drakor") || embedLow.contains("playerp2p") ||
                                embedLow.contains("embed4me") || embedLow.contains("abyss") ||
                                embedLow.contains("bond")
            // Alternative partner embeds (Bilibili, YouTube, Dailymotion) can momentarily lose audio
            // focus or pause for buffering without it being a true stall — give them the same extended
            // threshold as JS-protected streams, and a resume grace when they pause unexpectedly.
            val isAltPartnerEmbed = extractedUrl?.let {
                com.duta.movie.util.VideoExtractor.isAlternativePartnerHost(it)
            } ?: false
            val stallThreshold = if (isJsProtected || isAltPartnerEmbed) 25 else 15
            val seekGracePeriod = if (isJsProtected) 20 else 10 // seconds of grace after a seek
            var lastPos = -1L
            var stallSeconds = 0
            var postSeekCooldown = 0 // counts down after a seek is detected
            var lastSeekObserved = 0L
            var unexpectedPauseSeconds = 0

            while (isVideoReady && !isFinishing) {
                delay(1000)
                if (webPlayerState.value.isPlaying && !userInitiatedPause) {
                    unexpectedPauseSeconds = 0
                    val currentPos = webPlayerState.value.position
                    val userSeekOccurred = lastSeekTimeMs > lastSeekObserved
                    val isPositionJump = lastPos >= 0L && currentPos > 0L && (currentPos < lastPos || kotlin.math.abs(currentPos - lastPos) > 2000L)

                    if (userSeekOccurred || isPositionJump) {
                        lastSeekObserved = System.currentTimeMillis()
                        Log.d("VideoPlayerScreen", "Owl's Eye: Seek detected (${lastPos}ms -> ${currentPos}ms). Granting ${seekGracePeriod}s buffer grace.")
                        postSeekCooldown = seekGracePeriod
                        stallSeconds = 0
                        lastPos = currentPos
                    } else if (postSeekCooldown > 0) {
                        // Actively within post-seek/pause grace period: countdown without counting stall
                        postSeekCooldown--
                        stallSeconds = 0
                        lastPos = currentPos
                    } else if (currentPos >= 0L && currentPos == lastPos) {
                        stallSeconds++
                        val effectiveThreshold = if (currentPos <= 1000L) {
                            if (isJsProtected) (if (isTV) 28 else 22) else (if (isTV) 18 else 14)
                        } else {
                            stallThreshold
                        }
                        if (stallSeconds >= effectiveThreshold) {
                            val hostType = when {
                                isJsProtected -> "JS-protected"
                                isAltPartnerEmbed -> "partner-embed"
                                else -> "standard"
                            }
                            Log.w("VideoPlayerScreen", "Owl's Eye: WebView playback stall detected [$hostType] (frozen at ${currentPos}ms for ${stallSeconds}s). Failing over...")
                            val failingUrl = extractedUrl ?: currentServerUrlFromVm ?: ""
                            if (currentPos > 2000) {
                                pendingRotationResumePosition = currentPos
                                pendingRotationContentKey = activeContentKey
                            }
                            if (failingUrl.isNotEmpty()) {
                                if (isJsProtected) {
                                    // Soft-fail only: do NOT permanently kill JS-protected mirrors
                                    // on a stall. The stream may recover on retry, and nuking the
                                    // only viable mirror leaves the user with nothing.
                                    viewModel.notifyPlaybackFailure(failingUrl)
                                } else {
                                    viewModel.notifyMirrorDead(failingUrl)
                                    viewModel.notifyPlaybackFailure(failingUrl)
                                }
                            }
                            viewModel.resolveNextServer(videoId, failingUrl, force = true)
                            break
                        }
                    } else {
                        // Advancing normally
                        lastPos = currentPos
                        stallSeconds = 0
                    }
                } else {
                    // Player is not playing (buffering pause, audio-focus interruption, etc.)
                    stallSeconds = 0
                    if (!userInitiatedPause) {
                        unexpectedPauseSeconds++
                        val pauseTimeout = if (isAltPartnerEmbed) 15 else 25
                        if (unexpectedPauseSeconds == 1 && isAltPartnerEmbed) {
                            Log.d("VideoPlayerScreen", "Owl's Eye: Partner embed paused unexpectedly (audio-focus loss?). Granting ${pauseTimeout}s resume grace.")
                        }
                        if (unexpectedPauseSeconds >= pauseTimeout) {
                            val hostType = if (isAltPartnerEmbed) "partner-embed" else "standard"
                            Log.w("VideoPlayerScreen", "Owl's Eye: WebView $hostType paused unexpectedly and failed to resume after ${unexpectedPauseSeconds}s. Failing over...")
                            val failingUrl = extractedUrl ?: currentServerUrlFromVm ?: ""
                            if (failingUrl.isNotEmpty()) {
                                if (isJsProtected) {
                                    viewModel.notifyPlaybackFailure(failingUrl)
                                } else {
                                    viewModel.notifyMirrorDead(failingUrl)
                                    viewModel.notifyPlaybackFailure(failingUrl)
                                }
                            }
                            viewModel.resolveNextServer(videoId, failingUrl, force = true)
                            break
                        }
                    } else {
                        unexpectedPauseSeconds = 0
                        if (!isAltPartnerEmbed) postSeekCooldown = 0
                    }
                }
            }
        }
    }

    var lastCastSubUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(extractedUrl, selectedSubtitle, currentPlayer, subtitleCues, subtitleOffset, isCasting) {
        val url = extractedUrl ?: return@LaunchedEffect
        val sub = selectedSubtitle
        if (!useWebView || isCasting) {
            val defaultRef = com.duta.movie.util.VideoExtractor.getDefaultRefererForVideoId(videoId)
            val ref = lastReferer
            val effectiveReferer = if (!ref.isNullOrEmpty() && !ref.contains("157.245.199.231")) ref else defaultRef
            
            // Sync headers to OkHttp client
            lastCookies?.let { NetworkConfig.injectCookies(url, it) }
            NetworkConfig.updateSessionReferer(url, effectiveReferer)
            
            // For Google Cast: serve WebVTT subtitles via local LAN CastSubtitleServer
            val castSubUrl = if (isCasting && sub != null && subtitleCues.isNotEmpty()) {
                val vtt = SubtitleParser.toWebVtt(subtitleCues, -subtitleOffset)
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
                    if (lowUrl.contains(".m3u8") || lowUrl.contains(".txt") || lowUrl.contains("/hls/")) {
                        // application/x-mpegURL is the standard for HLS on both Media3 and Cast
                        setMimeType(MimeTypes.APPLICATION_M3U8)
                    } else if (lowUrl.contains(".mp4") || lowUrl.contains("get_video") || lowUrl.contains("/download")) {
                        setMimeType(MimeTypes.VIDEO_MP4)
                    } else if (lowUrl.contains(".mkv")) {
                        setMimeType(MimeTypes.VIDEO_MATROSKA)
                    } else if (lowUrl.contains(".webm")) {
                        setMimeType(MimeTypes.VIDEO_WEBM)
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
                            val effectiveSubUri = when {
                                s.localUri != null -> s.localUri
                                s.url.startsWith("file://") -> s.url
                                s.url.lowercase().let { it.endsWith(".srt") || it.endsWith(".vtt") || it.endsWith(".ass") || it.endsWith(".ssa") } -> s.url
                                else -> null
                            }
                            if (effectiveSubUri != null) {
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
                            } else emptyList()
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
                                url.lowercase().contains("/hls/")
                    
                    if (isHls) {
                        val hlsOkHttpFactory = OkHttpDataSource.Factory(NetworkConfig.permissiveOkHttpClient).setUserAgent(NetworkConfig.SHARED_USER_AGENT)
                        val hlsCacheFactory = com.duta.movie.util.PlayerCacheManager.getCacheDataSourceFactory(context, hlsOkHttpFactory)
                        val hlsMediaSource = HlsMediaSource.Factory(hlsCacheFactory)
                            .setAllowChunklessPreparation(true)
                            .createMediaSource(mediaItem)
                        
                        exoPlayer.setMediaSource(hlsMediaSource, /* resetPosition = */ true)
                    } else {
                        exoPlayer.setMediaItem(mediaItem, /* resetPosition = */ true)
                    }
                    
                    isVideoReady = false
                    isFinishing = false
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
                    lastSeekTimeMs = System.currentTimeMillis()
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

                val isResolvingActive = isResolvingState || viewModel.isResolving.value
                if (state == Player.STATE_IDLE && !useWebView && !isCasting && extractedUrl != null && !isVideoReady && !isFinishing && !isResolvingActive) {
                    idleJob?.cancel()
                    idleJob = scope.launch {
                        delay(4000)
                        val stillResolving = isResolvingState || viewModel.isResolving.value
                        if (currentPlayer.playbackState == Player.STATE_IDLE && !useWebView && !isCasting && !isVideoReady && !isFinishing && !stillResolving) {
                            Log.w("VideoPlayerScreen", "ExoPlayer stranded in STATE_IDLE for 4s. Media failed to load. Rotating to next server...")
                            isFinishing = true
                            extractedUrl?.let { viewModel.notifyPlaybackFailure(it) }
                            val effectiveServer = viewModel.currentServerUrl.value ?: currentServerUrlFromVm ?: currentServerUrl.value
                            viewModel.resolveNextServer(videoId, effectiveServer, force = true)
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
                        val isArchiveStream = extractedUrl?.contains("archive.org") == true
                        val baseTimeout = if (isUserSeeking) 50000L else if (isTV || isArchiveStream) 50000L else 45000L
                        val startupTimeout = if (isTV) 15000L else 12000L
                        val maxTimeout = 75000L // Maximum allowance if progress is actively being made
                        val startTime = System.currentTimeMillis()
                        var lastBufferedPos = currentPlayer.bufferedPosition
                        var lastProgressTime = startTime

                        while (isActive && currentPlayer.playbackState == Player.STATE_BUFFERING && !isFinishing) {
                            delay(3000L)
                            val now = System.currentTimeMillis()
                            val currentBufferedPos = currentPlayer.bufferedPosition
                            val currentBufferedDuration = currentPlayer.totalBufferedDuration

                            // If data has been buffered or buffer position is advancing, mark progress
                            if (currentBufferedDuration > 0 || currentBufferedPos > lastBufferedPos) {
                                lastProgressTime = now
                                lastBufferedPos = currentBufferedPos
                            }

                            val timeSinceLastProgress = now - lastProgressTime
                            val totalTimeElapsed = now - startTime

                            // For initial connection where player hasn't started and has 0 bytes buffered, failover faster
                            val isInitialStartup = currentPlayer.currentPosition <= 1000L && currentBufferedDuration == 0L && !isUserSeeking
                            val effectiveTimeout = if (isInitialStartup) startupTimeout else baseTimeout

                            // If we have had no progress for effectiveTimeout, or overall stuck beyond maxTimeout:
                            if ((timeSinceLastProgress >= effectiveTimeout) || (totalTimeElapsed >= maxTimeout)) {
                                if (currentPlayer.playbackState == Player.STATE_BUFFERING && !isFinishing) {
                                    Log.w("VideoPlayerScreen", "Stuck in BUFFERING for ${totalTimeElapsed / 1000}s (no progress for ${timeSinceLastProgress / 1000}s, bufferedDuration=${currentBufferedDuration}ms, isInitialStartup=$isInitialStartup). Stream appears dead. Rotating.")
                                    if (currentPlayer.currentPosition > 2000) {
                                        pendingRotationResumePosition = currentPlayer.currentPosition
                                        pendingRotationContentKey = activeContentKey
                                    }
                                    isFinishing = true
                                    extractedUrl?.let { viewModel.notifyPlaybackFailure(it) }
                                    viewModel.resolveNextServer(videoId, viewModel.currentServerUrl.value)
                                }
                                break
                            }
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

                idleJob?.cancel()
                idleJob = null
                val effectiveCurrentServer = viewModel.currentServerUrl.value ?: currentServerUrlFromVm ?: currentServerUrl.value

                if (isLive) {
                    if (autoRetryCount < 5) {
                        autoRetryCount++
                        Log.i("VideoPlayerScreen", "Live TV stream retry ($autoRetryCount) after error: ${error.errorCode} | $message")
                        playerErrorMessage = "Connecting to live stream (Attempt $autoRetryCount)..."
                        scope.launch {
                            delay(2000)
                            viewModel.playMovie(currentVideoId.value, effectiveCurrentServer, forceReset = true)
                        }
                    } else {
                        playerErrorMessage = "Live TV broadcast currently unavailable. Please try again later."
                    }
                    return
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
                    val failingUrl = extractedUrl ?: effectiveCurrentServer ?: ""
                    if (failingUrl.isNotEmpty()) {
                        viewModel.notifyMirrorDead(failingUrl)
                    }
                    scope.launch {
                        viewModel.resolveNextServer(currentVideoId.value, effectiveCurrentServer, force = true)
                    }
                } else if (is403) {
                    val failingUrl = extractedUrl ?: effectiveCurrentServer ?: ""
                    val currentServ = effectiveCurrentServer ?: ""
                    val isJsHoster = com.duta.movie.util.VideoExtractor.isJsOnlyHost(currentServ) || 
                                     currentServ.contains("vidhide", ignoreCase = true) || 
                                     currentServ.contains("fujihide", ignoreCase = true) ||
                                     failingUrl.contains("tnmr.org", ignoreCase = true)

                    if (isJsHoster && !isUserForcingWebView) {
                        Log.w("VideoPlayerScreen", "Owl's Eye: 403 on direct stream for JS hoster ($currentServ). Falling back to shielded WebView player.")
                        isUserForcingWebView = true
                        viewModel.updateExtractedUrl(currentVideoId.value, currentServ)
                    } else {
                        Log.w("VideoPlayerScreen", "Owl's Eye: Detected 403 Forbidden on $failingUrl. Purging dead stream & rotating...")
                        if (failingUrl.isNotEmpty()) {
                            viewModel.notifyMirrorDead(failingUrl)
                            viewModel.purgeServerFromVideo(currentVideoId.value, failingUrl)
                        }
                        scope.launch {
                            delay(600)
                            viewModel.resolveNextServer(currentVideoId.value, effectiveCurrentServer, force = true)
                        }
                    }
                } else if (isSourceError && autoRetryCount == 0) {
                    autoRetryCount++
                    Log.i("VideoPlayerScreen", "Owl's Eye: ExoPlayer failed with Source Error. Flagging host for learning...")
                    val failingUrl = extractedUrl ?: effectiveCurrentServer ?: ""
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
                        viewModel.resolveNextServer(currentVideoId.value, effectiveCurrentServer, force = true)
                    }
                } else if (autoRetryCount < 3 && isNetworkError) {
                    autoRetryCount++
                    Log.i("VideoPlayerScreen", "Retrying same server (Attempt $autoRetryCount) due to network error: ${error.errorCode}")
                    scope.launch { 
                        delay(2000)
                        if (isSeriesPlayback(video, effectiveCurrentServer)) {
                            viewModel.playTVSeries(currentVideoId.value, effectiveCurrentServer, forceReset = false)
                        } else {
                            viewModel.playMovie(currentVideoId.value, effectiveCurrentServer, forceReset = false)
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
        webPlayerState = webPlayerState,
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
        onSubtitleClick = { showSubtitleDialog = true; viewModel.fetchSubtitles(video?.title ?: "", isTV = (video?.isSeries == true && video?.episodes?.isNotEmpty() == true) || currentEpisode != null) },
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
                    .clearVideoSizeConstraints()
                    .setOverrideForType(TrackSelectionOverride(g, t.trackIndex))
                    .build()
                selectedQualityGroupIndex = t.groupIndex
            }
        },
        onNextEpisodeClick = { viewModel.resolveNextEpisode(videoId) },
        onRetryClick = { 
            playerErrorMessage = null
            if (isSeriesPlayback(video, serverUrl)) {
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
                        if (isSeriesPlayback(video, newServer)) {
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
            if (isSeriesPlayback(video, serverUrl)) {
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
                0 -> if (isAdvancing && !userInitiatedPause) true else false
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
            if (!isVideoReady) {
                Log.w("VideoPlayer", "Gate Stuck callback triggered for $currentUrl (original: $originalUrl)")
                viewModel.notifyGateStuck(currentUrl, originalUrl, videoId)
            } else {
                Log.d("VideoPlayer", "Ignored Gate Stuck callback because video is already ready and playing (isVideoReady=true)")
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
        videoId = videoId,
        onUserPauseChange = { userInitiatedPause = it },
        onUserSeek = { lastSeekTimeMs = System.currentTimeMillis() },
        fallbackDurationMs = fallbackDurationMs,
        voiceEnhancerMode = voiceEnhancerMode,
        onVoiceEnhancerSelect = { viewModel.setVoiceEnhancerMode(it) }
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
                if (isSeriesPlayback(video, url)) {
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
                var subSearchText by remember { mutableStateOf("") }
                var isSearchFocused by remember { mutableStateOf(false) }
                var isSearchBtnFocused by remember { mutableStateOf(false) }
                var isOpenLocalFocused by remember { mutableStateOf(false) }
                var selectedLanguageFilter by remember { mutableStateOf("All") }

                fun getSubtitleCategory(sub: com.duta.movie.model.Subtitle): String {
                    val norm = com.duta.movie.util.SubtitleExtractor.normalizeLanguage(sub.language)
                    val normLabel = com.duta.movie.util.SubtitleExtractor.normalizeLanguage(sub.label)
                    return when {
                        norm == "Indonesian" || normLabel == "Indonesian" ||
                        sub.language.contains("indo", ignoreCase = true) || sub.label.contains("indo", ignoreCase = true) ||
                        sub.language.contains("bahasa", ignoreCase = true) -> "Indonesian"
                        norm == "English" || normLabel == "English" ||
                        sub.language.contains("eng", ignoreCase = true) || sub.label.contains("eng", ignoreCase = true) -> "English"
                        norm == "Malay" || normLabel == "Malay" ||
                        sub.language.contains("malay", ignoreCase = true) || sub.language.contains("melayu", ignoreCase = true) ||
                        sub.label.contains("malay", ignoreCase = true) || sub.label.contains("melayu", ignoreCase = true) -> "Malay"
                        else -> "Other"
                    }
                }

                val countAll = subtitles.size
                val countIndo = remember(subtitles) { subtitles.count { getSubtitleCategory(it) == "Indonesian" } }
                val countEng = remember(subtitles) { subtitles.count { getSubtitleCategory(it) == "English" } }
                val countMalay = remember(subtitles) { subtitles.count { getSubtitleCategory(it) == "Malay" } }
                val countOther = remember(subtitles) { subtitles.count { getSubtitleCategory(it) == "Other" } }

                val chipAllLabel = stringResource(R.string.filter_all)
                val chipIndoLabel = stringResource(R.string.filter_indonesian)
                val chipEngLabel = stringResource(R.string.filter_english)
                val chipMalayLabel = stringResource(R.string.filter_malay)
                val chipOtherLabel = stringResource(R.string.filter_other)

                val filterChips = remember(subtitles, chipAllLabel, chipIndoLabel, chipEngLabel, chipMalayLabel, chipOtherLabel) {
                    listOf(
                        Triple("All", chipAllLabel, countAll),
                        Triple("Indonesian", chipIndoLabel, countIndo),
                        Triple("English", chipEngLabel, countEng),
                        Triple("Malay", chipMalayLabel, countMalay),
                        Triple("Other", chipOtherLabel, countOther)
                    )
                }

                val filteredSubs = remember(subtitles, subSearchText, selectedLanguageFilter) {
                    val listByLang = when (selectedLanguageFilter) {
                        "Indonesian" -> subtitles.filter { getSubtitleCategory(it) == "Indonesian" }
                        "English" -> subtitles.filter { getSubtitleCategory(it) == "English" }
                        "Malay" -> subtitles.filter { getSubtitleCategory(it) == "Malay" }
                        "Other" -> subtitles.filter { getSubtitleCategory(it) == "Other" }
                        else -> subtitles
                    }
                    val q = subSearchText.trim().lowercase()
                    if (q.isEmpty()) listByLang
                    else {
                        val qNorm = q.replace(".", " ").replace("-", " ")
                        listByLang.filter { sub ->
                            val labelNorm = sub.label.lowercase().replace(".", " ").replace("-", " ")
                            labelNorm.contains(qNorm) || sub.language.lowercase().contains(q)
                        }
                    }
                }

                val performSearch = {
                    val targetQuery = subSearchText.ifBlank { viewModel.currentSubtitleSearchTitle }
                    if (targetQuery.isNotBlank()) {
                        viewModel.searchSubtitles(targetQuery)
                    }
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    // Search bar row with input and action button
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = subSearchText,
                            onValueChange = { subSearchText = it },
                            placeholder = { 
                                Text(
                                    viewModel.currentSubtitleSearchTitle.ifBlank { stringResource(R.string.search_subtitles) }, 
                                    color = Color.Gray, 
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                ) 
                            },
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray, modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                if (isSubtitleLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = Color.Red,
                                        strokeWidth = 2.dp
                                    )
                                } else if (subSearchText.isNotEmpty()) {
                                    IconButton(onClick = { subSearchText = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.Red,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedContainerColor = Color(0xFF262626),
                                unfocusedContainerColor = Color(0xFF202020)
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { if (!isSubtitleLoading) performSearch() }),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .onFocusChanged { isSearchFocused = it.isFocused }
                                .border(if (isSearchFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { if (!isSubtitleLoading) performSearch() },
                            enabled = !isSubtitleLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSearchBtnFocused) Color.White else Color.Red,
                                contentColor = if (isSearchBtnFocused) Color.Black else Color.White,
                                disabledContainerColor = if (isSearchBtnFocused) Color.White.copy(alpha = 0.8f) else Color.Red.copy(alpha = 0.6f),
                                disabledContentColor = if (isSearchBtnFocused) Color.Black else Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(50.dp)
                                .onFocusChanged { isSearchBtnFocused = it.isFocused }
                                .focusable()
                                .border(if (isSearchBtnFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(8.dp))
                        ) {
                            if (isSubtitleLoading) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = if (isSearchBtnFocused) Color.Black else Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.searching), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            } else {
                                Text(stringResource(R.string.search), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }

                    // Open Local Subtitle Button
                    OutlinedButton(
                        onClick = {
                            try {
                                localSubtitlePickerLauncher.launch(arrayOf("*/*"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Cannot open file picker: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .padding(bottom = 8.dp)
                            .onFocusChanged { isOpenLocalFocused = it.isFocused }
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                    (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                                     keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                                     keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                                    try {
                                        localSubtitlePickerLauncher.launch(arrayOf("*/*"))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Cannot open file picker: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                    true
                                } else false
                            }
                            .focusable()
                            .border(if (isOpenLocalFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(8.dp)),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isOpenLocalFocused) Color.White.copy(alpha = 0.25f) else Color(0xFF262626),
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, if (isOpenLocalFocused) Color.White else Color.White.copy(alpha = 0.2f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = if (isOpenLocalFocused) Color.White else Color(0xFFFF5252),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.open_local_subtitle),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }

                    // Language Filter Chips
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(filterChips, key = { it.first }) { (key, label, count) ->
                            var isChipFocused by remember { mutableStateOf(false) }
                            val isSelected = selectedLanguageFilter == key
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = when {
                                    isChipFocused -> Color.White
                                    isSelected -> Color.Red
                                    else -> Color(0xFF262626)
                                },
                                contentColor = when {
                                    isChipFocused -> Color.Black
                                    isSelected -> Color.White
                                    else -> Color.LightGray
                                },
                                border = BorderStroke(
                                    width = if (isChipFocused) 2.dp else if (isSelected) 1.dp else 0.5.dp,
                                    color = if (isChipFocused) Color.White else if (isSelected) Color.Red else Color.White.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier
                                    .onFocusChanged { isChipFocused = it.isFocused }
                                    .onKeyEvent { keyEvent ->
                                        if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                            (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                                             keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                                             keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                                            selectedLanguageFilter = key
                                            true
                                        } else false
                                    }
                                    .clickable { selectedLanguageFilter = key }
                                    .focusable()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected || isChipFocused) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (count > 0) {
                                        Spacer(Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = when {
                                                isChipFocused -> Color.Black.copy(alpha = 0.15f)
                                                isSelected -> Color.White.copy(alpha = 0.25f)
                                                else -> Color.White.copy(alpha = 0.1f)
                                            }
                                        ) {
                                            Text(
                                                text = "$count",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                color = when {
                                                    isChipFocused -> Color.Black
                                                    isSelected -> Color.White
                                                    else -> Color.LightGray
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (isSubtitleLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .height(3.dp),
                            color = Color.Red,
                            trackColor = Color.White.copy(alpha = 0.1f)
                        )
                    } else {
                        Spacer(Modifier.height(3.dp))
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
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
                                    .onKeyEvent { keyEvent ->
                                        if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                            (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                                             keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                                             keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                                            viewModel.selectSubtitle(null)
                                            showSubtitleDialog = false
                                            true
                                        } else false
                                    }
                                    .clickable { viewModel.selectSubtitle(null); showSubtitleDialog = false }
                                    .focusable()
                                    .border(if (isFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(8.dp)),
                                colors = ListItemDefaults.colors(
                                    containerColor = if (isFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent
                                )
                            )
                        }
                        items(filteredSubs, key = { it.url }) { sub ->
                            var isFocused by remember { mutableStateOf(false) }
                            ListItem(
                                headlineContent = { Text(sub.label, color = Color.White) },
                                supportingContent = { Text(sub.language, color = Color.Gray, fontSize = 12.sp) },
                                leadingContent = { RadioButton(selected = selectedSubtitle?.url == sub.url, onClick = null, modifier = Modifier.focusable(false)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { isFocused = it.isFocused }
                                    .onKeyEvent { keyEvent ->
                                        if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                            (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                                             keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                                             keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                                            viewModel.selectSubtitle(sub)
                                            showSubtitleDialog = false
                                            true
                                        } else false
                                    }
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
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp), 
                                     contentAlignment = Alignment.Center
                                ) { 
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = Color.Red,
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.5.dp
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            stringResource(R.string.searching_subtitles),
                                            color = Color.LightGray,
                                            fontSize = 12.sp
                                        )
                                    }
                                } 
                            } 
                        } else if (filteredSubs.isEmpty() && subtitles.isNotEmpty()) {
                            item(key = "no_filter_matches") {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    Text("No matching subtitles found for this filter.\nPress Search to query online or load from device.", color = Color.Gray, fontSize = 12.sp, textAlign = TextAlign.Center)
                                }
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
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                                 keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                                 keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                                showSubtitleDialog = false
                                true
                            } else false
                        }
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
        
        val currentPlaybackMs = if (useWebView && !isCasting) {
            webPlayerState.value.position
        } else if (isCasting && castPlayer != null) {
            castPlayer.currentPosition
        } else {
            currentPlayer.currentPosition
        }

        // Active or nearest subtitle cue
        val activeCue = subtitleCues.firstOrNull { cue ->
            cue.startTimeMs <= (currentPlaybackMs + subtitleOffset) && (currentPlaybackMs + subtitleOffset) <= cue.endTimeMs
        }
        val nearestCue = activeCue ?: subtitleCues.minByOrNull { cue ->
            Math.abs(cue.startTimeMs - (currentPlaybackMs + subtitleOffset))
        }

        // First non-ad dialogue cue for intro alignment
        val firstDialogueCue = remember(subtitleCues) {
            subtitleCues.firstOrNull { cue ->
                val txt = cue.text.lowercase()
                !txt.contains("subscene") && !txt.contains("opensubtitles") &&
                !txt.contains("subdl") && !txt.contains("translated") &&
                !txt.contains("credit") && !txt.contains("www.") &&
                !txt.contains(".com") && !txt.contains(".org") &&
                !txt.contains("sync") && !txt.contains("duta")
            }
        }

        val syncScrollState = rememberScrollState()

        AlertDialog(
            onDismissRequest = { showSyncDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Sync, contentDescription = null, tint = Color.Red, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.subtitle_sync), color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF1E1E1E),
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(syncScrollState)
                ) {
                    Text(
                        text = "${if (subtitleOffset >= 0) "+" else ""}${subtitleOffset / 1000.0}s", 
                        color = Color.White, 
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.current_playback_time, SubtitleParser.formatVttTimestamp(currentPlaybackMs).substringBefore(".")), 
                        color = Color.Gray, 
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(12.dp))

                    // 1-Click Auto Snap Section
                    if (nearestCue != null) {
                        Surface(
                            color = Color(0xFF2A2A2A),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = stringResource(R.string.nearest_dialogue_label),
                                    color = Color.LightGray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "\"${nearestCue.text.replace("\n", " ").trim()}\"",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(8.dp))
                                var isSnapFocused by remember { mutableStateOf(false) }
                                Button(
                                    onClick = {
                                        val newOffset = nearestCue.startTimeMs - currentPlaybackMs
                                        viewModel.setSubtitleOffset(newOffset)
                                        val sign = if (newOffset >= 0) "+" else ""
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.subtitle_synced_toast, "$sign${newOffset / 1000.0}s"),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSnapFocused) Color.White else Color(0xFFE50914)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(syncFirstButtonFocusRequester)
                                        .onFocusChanged { isSnapFocused = it.isFocused }
                                        .onKeyEvent { keyEvent ->
                                            if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                                (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                                                 keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                                                 keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                                                val newOffset = nearestCue.startTimeMs - currentPlaybackMs
                                                viewModel.setSubtitleOffset(newOffset)
                                                val sign = if (newOffset >= 0) "+" else ""
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.subtitle_synced_toast, "$sign${newOffset / 1000.0}s"),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                true
                                            } else false
                                        }
                                        .focusable()
                                        .scale(if (isSnapFocused) 1.04f else 1f)
                                ) {
                                    Text(
                                        stringResource(R.string.snap_line_now),
                                        color = if (isSnapFocused) Color.Black else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }

                    // Stepper Adjustment Buttons (Standard: ±0.5s, Coarse: ±2.0s)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    ) {
                        listOf(
                            Triple("-2.0s", -2000L, R.string.minus_2_0s),
                            Triple("-0.5s", -500L, R.string.minus_0_5s),
                            Triple("+0.5s", 500L, R.string.plus_0_5s),
                            Triple("+2.0s", 2000L, R.string.plus_2_0s)
                        ).forEach { (label, delta, strRes) ->
                            var isStepFocused by remember { mutableStateOf(false) }
                            Button(
                                onClick = { viewModel.adjustSubtitleOffset(delta) },
                                colors = ButtonDefaults.buttonColors(containerColor = if (isStepFocused) Color.White else Color(0xFF333333)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .onFocusChanged { isStepFocused = it.isFocused }
                                    .onKeyEvent { keyEvent ->
                                        if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                            (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                                             keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                                             keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                                            viewModel.adjustSubtitleOffset(delta)
                                            true
                                        } else false
                                    }
                                    .focusable()
                                    .scale(if (isStepFocused) 1.08f else 1f)
                            ) {
                                Text(stringResource(strRes), color = if (isStepFocused) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // Fine Tuning (±0.1s) and Reset
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    ) {
                        var isMinusFineFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = { viewModel.adjustSubtitleOffset(-100) },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isMinusFineFocused) Color.White else Color(0xFF2C2C2C)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { isMinusFineFocused = it.isFocused }
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                        (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                                         keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                                         keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                                        viewModel.adjustSubtitleOffset(-100)
                                        true
                                    } else false
                                }
                                .focusable()
                                .scale(if (isMinusFineFocused) 1.08f else 1f)
                        ) {
                            Text(stringResource(R.string.minus_0_1s), color = if (isMinusFineFocused) Color.Black else Color.White, fontSize = 12.sp)
                        }

                        var isResetFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = { viewModel.resetSubtitleOffset() },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isResetFocused) Color.White else Color(0xFF444444)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .onFocusChanged { isResetFocused = it.isFocused }
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                        (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                                         keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                                         keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                                        viewModel.resetSubtitleOffset()
                                        true
                                    } else false
                                }
                                .focusable()
                                .scale(if (isResetFocused) 1.08f else 1f)
                        ) {
                            Text(stringResource(R.string.reset_sync), color = if (isResetFocused) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        var isPlusFineFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = { viewModel.adjustSubtitleOffset(100) },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isPlusFineFocused) Color.White else Color(0xFF2C2C2C)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { isPlusFineFocused = it.isFocused }
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                        (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                                         keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                                         keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                                        viewModel.adjustSubtitleOffset(100)
                                        true
                                    } else false
                                }
                                .focusable()
                                .scale(if (isPlusFineFocused) 1.08f else 1f)
                        ) {
                            Text(stringResource(R.string.plus_0_1s), color = if (isPlusFineFocused) Color.Black else Color.White, fontSize = 12.sp)
                        }
                    }

                    // Intro Bumper & First Dialogue Alignment
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        var isIntroMinusFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = { viewModel.adjustSubtitleOffset(-10000) },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isIntroMinusFocused) Color.White else Color(0xFF252525)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { isIntroMinusFocused = it.isFocused }
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                        (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                                         keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                                         keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                                        viewModel.adjustSubtitleOffset(-10000)
                                        true
                                    } else false
                                }
                                .focusable()
                                .scale(if (isIntroMinusFocused) 1.08f else 1f)
                        ) {
                            Text(stringResource(R.string.minus_10s), color = if (isIntroMinusFocused) Color.Black else Color.LightGray, fontSize = 11.sp)
                        }

                        if (firstDialogueCue != null) {
                            var isFirstDiagFocused by remember { mutableStateOf(false) }
                            Button(
                                onClick = {
                                    val newOffset = firstDialogueCue.startTimeMs - currentPlaybackMs
                                    viewModel.setSubtitleOffset(newOffset)
                                    val sign = if (newOffset >= 0) "+" else ""
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.subtitle_synced_toast, "$sign${newOffset / 1000.0}s"),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = if (isFirstDiagFocused) Color.White else Color(0xFF333333)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .onFocusChanged { isFirstDiagFocused = it.isFocused }
                                    .onKeyEvent { keyEvent ->
                                        if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                            (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                                             keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                                             keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                                            val newOffset = firstDialogueCue.startTimeMs - currentPlaybackMs
                                            viewModel.setSubtitleOffset(newOffset)
                                            val sign = if (newOffset >= 0) "+" else ""
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.subtitle_synced_toast, "$sign${newOffset / 1000.0}s"),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            true
                                        } else false
                                    }
                                    .focusable()
                                    .scale(if (isFirstDiagFocused) 1.08f else 1f)
                            ) {
                                Text(stringResource(R.string.align_first_dialogue), color = if (isFirstDiagFocused) Color.Black else Color.White, fontSize = 11.sp, maxLines = 1)
                            }
                        }

                        var isIntroPlusFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = { viewModel.adjustSubtitleOffset(10000) },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isIntroPlusFocused) Color.White else Color(0xFF252525)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { isIntroPlusFocused = it.isFocused }
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                        (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                                         keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                                         keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                                        viewModel.adjustSubtitleOffset(10000)
                                        true
                                    } else false
                                }
                                .focusable()
                                .scale(if (isIntroPlusFocused) 1.08f else 1f)
                        ) {
                            Text(stringResource(R.string.plus_10s), color = if (isIntroPlusFocused) Color.Black else Color.LightGray, fontSize = 11.sp)
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
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                                 keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                                 keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                                showSyncDialog = false
                                true
                            } else false
                        }
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
                    itemsIndexed(episodes, key = { index, ep -> ep.url.ifEmpty { "${ep.name}_$index" } }) { index, ep ->
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
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                        (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                                         keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                                         keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                                        viewModel.playTVSeries(videoId, ep.url, targetEpisode = ep)
                                        showEpisodeDialog = false
                                        true
                                    } else false
                                }
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
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                                 keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                                 keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                                showEpisodeDialog = false
                                true
                            } else false
                        }
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
    webPlayerState: State<WebPlayerState>,
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
    videoId: String = "",
    onUserPauseChange: (Boolean) -> Unit = {},
    onUserSeek: () -> Unit = {},
    fallbackDurationMs: Long = 0L,
    voiceEnhancerMode: Int = 0,
    onVoiceEnhancerSelect: ((Int) -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isLive = remember(videoId, video?.duration) {
        com.duta.movie.model.LiveTvCatalog.isLiveVideo(videoId) || video?.duration == "LIVE"
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
    val currentWebState by rememberUpdatedState(webPlayerState.value)
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
                onPlayerStateChange(1, currentWebState.position / 1000f, currentWebState.duration / 1000f)
                val script = """
                    (function() {
                        var broadcast = function(win) {
                            try {
                                win.postMessage({ type: 'duta:play' }, '*');
                                win.postMessage(JSON.stringify({ type: 'duta:play' }), '*');
                                win.postMessage(JSON.stringify({ event: 'command', func: 'playVideo', args: [] }), '*');
                                win.postMessage(JSON.stringify({ method: 'play' }), '*');
                                win.postMessage(JSON.stringify({ type: 'play' }), '*');
                            } catch(e) {}
                            try {
                                for (var j = 0; j < win.frames.length; j++) {
                                    broadcast(win.frames[j]);
                                }
                            } catch(e) {}
                        };
                        broadcast(window);
                        if (window.playerBridge && typeof window.playerBridge.play === 'function') {
                            try { window.playerBridge.play(); } catch(e) {}
                        }
                        var v = document.querySelector('video');
                        if (v) { v.play().catch(function(){}); }
                        if (window.jwplayer && typeof window.jwplayer === 'function') {
                            try { window.jwplayer().play(); } catch(e) {}
                        }
                        if (window.videojs) {
                            try {
                                var players = window.videojs.getPlayers ? window.videojs.getPlayers() : {};
                                for (var p in players) { if (players[p] && players[p].play) players[p].play(); }
                            } catch(e) {}
                        }
                    })();
                """.trimIndent()
                safeEvaluateJavascript(webViewRef.value, script)
                webListeners.forEach {
                    it.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                    it.onIsPlayingChanged(true)
                    it.onPlaybackStateChanged(Player.STATE_READY)
                }
            }
            override fun pause() {
                onUserPauseChange(true)
                onPlayerStateChange(2, currentWebState.position / 1000f, currentWebState.duration / 1000f)
                val script = """
                    (function() {
                        var broadcast = function(win) {
                            try {
                                win.postMessage({ type: 'duta:pause' }, '*');
                                win.postMessage(JSON.stringify({ type: 'duta:pause' }), '*');
                                win.postMessage(JSON.stringify({ event: 'command', func: 'pauseVideo', args: [] }), '*');
                                win.postMessage(JSON.stringify({ method: 'pause' }), '*');
                                win.postMessage(JSON.stringify({ type: 'pause' }), '*');
                            } catch(e) {}
                            try {
                                for (var j = 0; j < win.frames.length; j++) {
                                    broadcast(win.frames[j]);
                                }
                            } catch(e) {}
                        };
                        broadcast(window);
                        if (window.playerBridge && typeof window.playerBridge.pause === 'function') {
                            try { window.playerBridge.pause(); } catch(e) {}
                        }
                        var v = document.querySelector('video');
                        if (v) { v.pause(); }
                        if (window.jwplayer && typeof window.jwplayer === 'function') {
                            try { window.jwplayer().pause(); } catch(e) {}
                        }
                        if (window.videojs) {
                            try {
                                var players = window.videojs.getPlayers ? window.videojs.getPlayers() : {};
                                for (var p in players) { if (players[p] && players[p].pause) players[p].pause(); }
                            } catch(e) {}
                        }
                    })();
                """.trimIndent()
                safeEvaluateJavascript(webViewRef.value, script)
                webListeners.forEach {
                    it.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                    it.onIsPlayingChanged(false)
                }
            }
            override fun seekTo(positionMs: Long) {
                onUserSeek()
                val time = positionMs / 1000.0
                onPlayerStateChange(if (currentWebState.isPlaying) 1 else 2, time.toFloat(), currentWebState.duration / 1000f)
                val script = """
                    (function() {
                        var t = $time;
                        var broadcast = function(win) {
                            try {
                                win.postMessage({ type: 'duta:seek', time: t }, '*');
                                win.postMessage(JSON.stringify({ type: 'duta:seek', time: t }), '*');
                                win.postMessage(JSON.stringify({ event: 'command', func: 'seekTo', args: [t, true] }), '*');
                                win.postMessage(JSON.stringify({ method: 'seek', value: t }), '*');
                                win.postMessage(JSON.stringify({ type: 'seek', value: t }), '*');
                                win.postMessage({ type: 'player:seek', time: t }, '*');
                            } catch(e) {}
                            try {
                                for (var j = 0; j < win.frames.length; j++) {
                                    broadcast(win.frames[j]);
                                }
                            } catch(e) {}
                        };
                        broadcast(window);
                        if (window.playerBridge && typeof window.playerBridge.seek === 'function') {
                            try { window.playerBridge.seek(t); } catch(e) {}
                        }
                        var v = document.querySelector('video');
                        if (v) { v.currentTime = t; }
                        if (window.jwplayer && typeof window.jwplayer === 'function') {
                            try { window.jwplayer().seek(t); } catch(e) {}
                        }
                        if (window.videojs) {
                            try {
                                var players = window.videojs.getPlayers ? window.videojs.getPlayers() : {};
                                for (var p in players) { if (players[p] && players[p].currentTime) players[p].currentTime(t); }
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

    LaunchedEffect(webPlayerState.value.isPlaying, isVideoReady) {
        val state = if (isVideoReady) Player.STATE_READY else Player.STATE_BUFFERING
        webListeners.forEach { listener ->
            listener.onPlaybackStateChanged(state)
            listener.onPlayWhenReadyChanged(webPlayerState.value.isPlaying, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            listener.onIsPlayingChanged(webPlayerState.value.isPlaying)
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
            onUserSeek()
            currentPlayer.seekTo(newPos)
            pendingSeekOffset = 0L
            baseSeekPosition = -1L
        } else {
            baseSeekPosition = -1L
        }
    }
    
    val handleSeek = { offsetMs: Long ->
        onUserSeek()
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
                        onUserSeek()
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
            val currentOnVisibilityToggle by rememberUpdatedState(onVisibilityToggle)
            val currentHandleSeek by rememberUpdatedState(handleSeek)
            val currentIsVideoReady by rememberUpdatedState(isVideoReady)
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
                    videoId = videoId,
                    onVisibilityToggle = onVisibilityToggle,
                    onSeek = handleSeek,
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

                        val gestureDetector = android.view.GestureDetector(ctx, object : android.view.GestureDetector.SimpleOnGestureListener() {
                            override fun onSingleTapConfirmed(e: android.view.MotionEvent): Boolean {
                                if (currentIsVideoReady) {
                                    currentOnVisibilityToggle()
                                    return true
                                }
                                return false
                            }
                            override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                                if (currentIsVideoReady) {
                                    val isRight = e.x > width / 2
                                    currentHandleSeek(if (isRight) 10000L else -10000L)
                                    return true
                                }
                                return false
                            }
                        })
                        setOnTouchListener { _, event ->
                            gestureDetector.onTouchEvent(event)
                            true
                        }
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
                val subFontSize = if (isTV) 28.sp else if (isLandscape) 22.sp else 17.sp
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(bottom = bottomPadding, start = 24.dp, end = 24.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onVisibilityToggle() },
                        contentAlignment = Alignment.Center
                    ) {
                        // Background black stroke outline for high readability against bright scenes without any black background box
                        Text(
                            text = subtitleText,
                            color = Color.Black,
                            fontSize = subFontSize,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            style = TextStyle(
                                drawStyle = Stroke(
                                    width = if (isTV) 5f else 3.5f,
                                    join = StrokeJoin.Round
                                )
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                        // Foreground clean pure white subtitle text
                        Text(
                            text = subtitleText,
                            color = Color.White,
                            fontSize = subFontSize,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            style = TextStyle(
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.8f),
                                    offset = Offset(1.5f, 1.5f),
                                    blurRadius = 3f
                                )
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
                isLive = isLive,
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
                isTV = isTV,
                voiceEnhancerMode = voiceEnhancerMode,
                onVoiceEnhancerSelect = onVoiceEnhancerSelect
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
    videoId: String = "",
    onVisibilityToggle: (() -> Unit)? = null,
    onSeek: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val currentOnVisibilityToggle by rememberUpdatedState(onVisibilityToggle)
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentIsVideoReady by rememberUpdatedState(isVideoReady)
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
                    setBackgroundColor(android.graphics.Color.BLACK)

                    val gestureDetector = android.view.GestureDetector(ctx, object : android.view.GestureDetector.SimpleOnGestureListener() {
                        override fun onSingleTapConfirmed(e: android.view.MotionEvent): Boolean {
                            if (currentIsVideoReady) {
                                currentOnVisibilityToggle?.invoke()
                                return true
                            }
                            return false
                        }
                        override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                            if (currentIsVideoReady && currentOnSeek != null) {
                                val w = this@apply.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
                                val isRight = e.x > w / 2
                                currentOnSeek?.invoke(if (isRight) 10000L else -10000L)
                                return true
                            }
                            return false
                        }
                    })
                    setOnTouchListener { _, event ->
                        gestureDetector.onTouchEvent(event)
                        false
                    }
                
                // TV FOCUS FIX: Block DPAD focus traversal into WebView children,
                // but keep the WebView itself focusable so JS click() events and
                // touch dispatching still work (needed for JWPlayer in VidHide).
                val isTVDevice = isTV || com.duta.movie.util.DeviceUtils.isTvDevice(ctx)
                if (isTVDevice) {
                    descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    // Dispatch a synthetic touch so JWPlayer's gesture detector initializes
                    post {
                        val x = if (width > 0) width / 2f else resources.displayMetrics.widthPixels / 2f
                        val y = if (height > 0) height / 2f else resources.displayMetrics.heightPixels / 2f
                        val e = android.view.MotionEvent.obtain(
                            android.os.SystemClock.uptimeMillis(),
                            android.os.SystemClock.uptimeMillis(),
                            android.view.MotionEvent.ACTION_DOWN, x, y, 0
                        )
                        dispatchTouchEvent(e)
                        val eUp = android.view.MotionEvent.obtain(
                            android.os.SystemClock.uptimeMillis(),
                            android.os.SystemClock.uptimeMillis(),
                            android.view.MotionEvent.ACTION_UP, x, y, 0
                        )
                        dispatchTouchEvent(eUp)
                        e.recycle()
                        eUp.recycle()
                    }
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
                        low.contains("vidhide") || low.contains("fujihide") || low.contains("tnmr.org") ||
                        forceWebViewHosts.any { low.contains(it) }
                    )
                    userAgentString = if (isStrict || (isDailymotion && isTVDevice)) NetworkConfig.MOBILE_USER_AGENT else NetworkConfig.SHARED_USER_AGENT
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
                    fun isVideoReady(): Boolean = isVideoReady
                    @android.webkit.JavascriptInterface
                    fun notifyVideoPlaying() {
                        if (isVideoReady) return
                        onPlaybackSuccess(url)
                        this@apply.post {
                            safeEvaluateJavascript(this@apply, """
                                try {
                                    window.successNotified = true;
                                    window.videoFound = true;
                                    var cleanDoc = function(doc) {
                                        if (!doc) return;
                                        try {
                                            if (doc.documentElement) {
                                                doc.documentElement.style.setProperty('background', '#000', 'important');
                                                doc.documentElement.style.setProperty('background-color', '#000', 'important');
                                            }
                                            if (doc.body) {
                                                doc.body.style.setProperty('background', '#000', 'important');
                                                doc.body.style.setProperty('background-color', '#000', 'important');
                                                doc.body.classList.add('video-active', 'video-playing');
                                            }
                                            var bgEls = doc.querySelectorAll('.stage, .wrap, #player, #content, .container, main, .jwplayer, .jw-wrapper, .jw-media, div#player, div.player, .player, .index-container, .mv-content, .play-content, .left-content, #ploader, .vid-content, .vid-play-overlay, [id^="pjs_"]');
                                            for (var bgi = 0; bgi < bgEls.length; bgi++) {
                                                bgEls[bgi].style.setProperty('background', '#000', 'important');
                                                bgEls[bgi].style.setProperty('background-color', '#000', 'important');
                                                bgEls[bgi].style.setProperty('border', 'none', 'important');
                                                bgEls[bgi].style.setProperty('box-shadow', 'none', 'important');
                                            }
                                            var webJunk = doc.querySelectorAll('.breadcrumb, .breadcrumbs, [class*="breadcrumb"], [id*="breadcrumb"], .server_list, #server_lists, .server-list, #servers, #server_list, .server-options, .btn-svx, .episode, [class*="server_list"], [class*="server-list"], .lightswitch, .light-switch, [class*="lightswitch"], .vid-episodes, #episode_lists, .episode-list, .list-episode, .episodes-list, [class*="vid-episodes"], [class*="episode-list"], .vid-details, .vid-details-left, .vid-details-right, .vid-seo-keywords, [class*="vid-details"], .movie-details, .movie-info, .film-details, .film-info, .synopsis, .deskripsi, [class*="movie-info"], [class*="movie-details"], .right-content, .related-label, .related-mv, .related-movies, .related-posts, [class*="related-"], .catalogue, .footer, .footer-content, .footer-copy, .footer-1, .footer-2, .footer-3, .index-search, .index-filter, .index-menu, .logo, .notice, .marquee, .menu-content, .menu, .sub, .socmed, .fb-share-button, .overlay, .filter-content, .filter-box, .divider, .site-header, .site-footer');
                                            for (var wj = 0; wj < webJunk.length; wj++) {
                                                webJunk[wj].style.setProperty('display', 'none', 'important');
                                                webJunk[wj].style.setProperty('opacity', '0', 'important');
                                                webJunk[wj].style.setProperty('visibility', 'hidden', 'important');
                                                webJunk[wj].style.setProperty('height', '0', 'important');
                                                webJunk[wj].style.setProperty('pointer-events', 'none', 'important');
                                            }
                                            var bads = doc.querySelectorAll('#overlay, #playback, div#overlay, div#playback, #videoInfo, .video-info, [id*="videoInfo"], [class*="video-info"], .jw-display-icon-display, .jw-display-icon-container, .jw-display-icon-idle, .jw-icon-display, .jw-display, .jw-svg-icon-play, .vjs-big-play-button, .play-button, #play-button, .play-btn, .big-play-button, svg, svg[viewBox="0 0 24 24"], svg[viewBox="0 0 240 240"]');
                                            for (var i = 0; i < bads.length; i++) {
                                                bads[i].style.setProperty('display', 'none', 'important');
                                                bads[i].style.setProperty('opacity', '0', 'important');
                                                bads[i].style.setProperty('visibility', 'hidden', 'important');
                                                bads[i].style.setProperty('pointer-events', 'none', 'important');
                                                bads[i].style.setProperty('width', '0', 'important');
                                                bads[i].style.setProperty('height', '0', 'important');
                                                try { bads[i].remove(); } catch(e){}
                                            }
                                            if (typeof doc.defaultView !== 'undefined' && typeof doc.defaultView.closeVideoInfo === 'function') {
                                                try { doc.defaultView.closeVideoInfo(); } catch(e){}
                                            }
                                        } catch(e){}
                                    };
                                    cleanDoc(document);
                                    var iframes = document.querySelectorAll('iframe');
                                    for (var j = 0; j < iframes.length; j++) {
                                        try {
                                            cleanDoc(iframes[j].contentDocument || (iframes[j].contentWindow && iframes[j].contentWindow.document));
                                        } catch(e){}
                                    }
                                } catch(e){}
                            """.trimIndent())
                        }
                    }
                    @android.webkit.JavascriptInterface
                    fun notifyGateStuck(currentUrl: String) {
                        if (isVideoReady) {
                            Log.d("VideoPlayerNuker", "Ignored notifyGateStuck for $currentUrl because isVideoReady is true")
                            return
                        }
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
                        val isVidhideStream = low.contains("vidhide") || low.contains("fujihide") || low.contains("tnmr.org") ||
                                              url.lowercase().contains("vidhide") || url.lowercase().contains("fujihide")
                        val isAsiastream = low.contains("asiastream") || url.lowercase().contains("asiastream")
                        val isProtected = (low.contains("playmogo") || 
                                          low.contains("digitalidentity") || low.contains("sunrisevalleycreative") ||
                                          low.contains("johnfullwonder") || low.contains("voe") ||
                                          low.contains("platformdocumentation") || low.contains("hgcloud") || low.contains("hglink") ||
                                          isVidhideStream || isAsiastream ||
                                          com.duta.movie.util.VideoExtractor.isJsOnlyHost(u) ||
                                          com.duta.movie.util.VideoExtractor.isJsOnlyHost(url)) &&
                                          !low.contains("cloudwindow") &&
                                          (!low.contains(".m3u8") || isVidhideStream || isAsiastream) &&
                                          (!low.contains(".txt") || isAsiastream) &&
                                          !low.contains(".mp4") && !low.contains(".mkv") && !low.contains(".webm")
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
                            val query = request?.url?.query?.lowercase() ?: ""

                            if (statusCode == 403 && (host.contains("abyss") || host.contains("bond") || host.contains("playsobat") || host.contains("mantab") || host.contains("drakor") || host.contains("cloudflare") || host.contains("challenges.cloudflare"))) {
                                Log.d("VideoPlayerWebView", "HTTP 403 Cloudflare challenge received for $failingUrl -- allowing WebView to execute challenge")
                                return
                            }
                            val isTargetEmbed = request?.isForMainFrame == true || 
                                                (lowFail.endsWith(".m3u8") || lowFail.endsWith(".mpd")) ||
                                                (path.contains("/api/v1/video") && statusCode in listOf(404, 410)) ||
                                                (host.contains("abysscdn.com") && query.contains("v=") && statusCode in listOf(404, 410))
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
                                    low.contains("bullerswood") || low.contains("scphi") || low.contains("grisham") || low.contains("lk21") || low.contains("mantab") || low.contains("kepala-bergetar") ||
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
                        val isRootDomain = (low.endsWith(".org/") || low.endsWith(".com/") || low.endsWith(".link/") || low.endsWith(".xyz/") || low.endsWith(".to/") || low.endsWith(".to")) &&
                                          low.split("/").size <= 4 
                        val isAbyssLanding = low == "https://abyss.to" || low == "https://abyss.to/" || low == "https://bond.to" || low == "https://bond.to/" ||
                                             low.startsWith("https://abyss.to/?") || low.startsWith("https://bond.to/?")
                        
                        val isMainFrame = r.isForMainFrame
                        if (isMainFrame && (!isSafe || isLandingPage || isRootDomain || isAbyssLanding)) {
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
                    override fun onPageStarted(v: android.webkit.WebView?, u: String?, favicon: android.graphics.Bitmap?) {
                        super.onPageStarted(v, u, favicon)
                        safeEvaluateJavascript(v, "try{document.documentElement.style.setProperty('background','#000','important');document.documentElement.style.setProperty('background-color','#000','important');if(document.body){document.body.style.setProperty('background','#000','important');document.body.style.setProperty('background-color','#000','important');}}catch(e){}")
                    }
                    override fun onPageFinished(v: android.webkit.WebView?, u: String?) {
                        if (v?.getTag(R.id.is_destroyed) == true) return
                        Log.d("VideoPlayerSniffer", "Injecting Nuker SCRIPT via onPageFinished")
                        safeEvaluateJavascript(v, nukerScript)
                        val removePopupScript = """
                            (function() {
                                try {
                                    var ensureBlack = function() {
                                        try {
                                            if (document.documentElement) {
                                                document.documentElement.style.setProperty('background', '#000', 'important');
                                                document.documentElement.style.setProperty('background-color', '#000', 'important');
                                            }
                                            if (document.body) {
                                                document.body.style.setProperty('background', '#000', 'important');
                                                document.body.style.setProperty('background-color', '#000', 'important');
                                            }
                                        } catch(e) {}
                                    };
                                    ensureBlack();
                                    var kill = function() {
                                        var cleanDoc = function(doc) {
                                            if (!doc) return;
                                            try {
                                                if (doc.documentElement) {
                                                    doc.documentElement.style.setProperty('background', '#000', 'important');
                                                    doc.documentElement.style.setProperty('background-color', '#000', 'important');
                                                }
                                                if (doc.body) {
                                                    doc.body.style.setProperty('background', '#000', 'important');
                                                    doc.body.style.setProperty('background-color', '#000', 'important');
                                                }
                                                var o = doc.getElementById('overlay');
                                                if (o) { o.style.display = 'none'; try { o.remove(); } catch(e){} }
                                                var p = doc.getElementById('playback');
                                                if (p) { p.style.display = 'none'; try { p.remove(); } catch(e){} }
                                                var bgEls = doc.querySelectorAll('.stage, .wrap, #player, #content, .container, main, .jwplayer, .jw-wrapper, .jw-media, div#player, div.player, .player, .index-container, .mv-content, .play-content, .left-content, #ploader, .vid-content, .vid-play-overlay, [id^="pjs_"]');
                                                for (var bgi = 0; bgi < bgEls.length; bgi++) {
                                                    bgEls[bgi].style.setProperty('background', '#000', 'important');
                                                    bgEls[bgi].style.setProperty('background-color', '#000', 'important');
                                                    bgEls[bgi].style.setProperty('border', 'none', 'important');
                                                    bgEls[bgi].style.setProperty('box-shadow', 'none', 'important');
                                                }
                                                var webJunk = doc.querySelectorAll('.breadcrumb, .breadcrumbs, [class*="breadcrumb"], [id*="breadcrumb"], .server_list, #server_lists, .server-list, #servers, #server_list, .server-options, .btn-svx, .episode, [class*="server_list"], [class*="server-list"], .lightswitch, .light-switch, [class*="lightswitch"], .vid-episodes, #episode_lists, .episode-list, .list-episode, .episodes-list, [class*="vid-episodes"], [class*="episode-list"], .vid-details, .vid-details-left, .vid-details-right, .vid-seo-keywords, [class*="vid-details"], .movie-details, .movie-info, .film-details, .film-info, .synopsis, .deskripsi, [class*="movie-info"], [class*="movie-details"], .right-content, .related-label, .related-mv, .related-movies, .related-posts, [class*="related-"], .catalogue, .footer, .footer-content, .footer-copy, .footer-1, .footer-2, .footer-3, .index-search, .index-filter, .index-menu, .logo, .notice, .marquee, .menu-content, .menu, .sub, .socmed, .fb-share-button, .overlay, .filter-content, .filter-box, .divider, .site-header, .site-footer');
                                                for (var wj = 0; wj < webJunk.length; wj++) {
                                                    webJunk[wj].style.setProperty('display', 'none', 'important');
                                                    webJunk[wj].style.setProperty('opacity', '0', 'important');
                                                    webJunk[wj].style.setProperty('visibility', 'hidden', 'important');
                                                    webJunk[wj].style.setProperty('height', '0', 'important');
                                                    webJunk[wj].style.setProperty('pointer-events', 'none', 'important');
                                                }
                                                var bads = doc.querySelectorAll('#overlay, #playback, div#overlay, div#playback, #videoInfo, .video-info, [id*="videoInfo"], [class*="video-info"], .jw-display-icon-display, .jw-display-icon-container, .jw-display-icon-idle, .jw-icon-display, .jw-display, .jw-svg-icon-play, .vjs-big-play-button, .play-button, #play-button, .play-btn, .big-play-button, svg, svg[viewBox="0 0 24 24"], svg[viewBox="0 0 240 240"]');
                                                for (var i = 0; i < bads.length; i++) {
                                                    bads[i].style.setProperty('display', 'none', 'important');
                                                    bads[i].style.setProperty('opacity', '0', 'important');
                                                    bads[i].style.setProperty('visibility', 'hidden', 'important');
                                                    bads[i].style.setProperty('pointer-events', 'none', 'important');
                                                    bads[i].style.setProperty('width', '0', 'important');
                                                    bads[i].style.setProperty('height', '0', 'important');
                                                    try { bads[i].remove(); } catch(e){}
                                                }
                                                if (typeof doc.defaultView !== 'undefined' && typeof doc.defaultView.closeVideoInfo === 'function') {
                                                    try { doc.defaultView.closeVideoInfo(); } catch(e){}
                                                }
                                            } catch(e){}
                                        };
                                        cleanDoc(document);
                                        var iframes = document.querySelectorAll('iframe');
                                        for (var j = 0; j < iframes.length; j++) {
                                            try {
                                                cleanDoc(iframes[j].contentDocument || (iframes[j].contentWindow && iframes[j].contentWindow.document));
                                            } catch(e){}
                                        }
                                    };
                                    kill();
                                    setInterval(kill, 150);
                                } catch(e) {}
                            })();
                        """.trimIndent()
                        safeEvaluateJavascript(v, removePopupScript)
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

                        val isVidhideDomain = low.contains("vidhide") || low.contains("fujihide") || low.contains("tnmr.org")
                        if (isVidhideDomain) {
                            // Only block actual ad popup networks, never block legitimate player assets, video chunks, or steganographic key poster images
                            if (low.contains("popads") || low.contains("onclickads") || low.contains("adsterra") || low.contains("exoclick") || low.contains("propellerads")) {
                                return android.webkit.WebResourceResponse("text/plain", "UTF-8", null)
                            }
                            // CORS-BYPASS: VidHide's JS player makes XHR from vidhide.org to s1.vidhide.org/api/ and /api-config/
                            // The server doesn't send Access-Control-Allow-Origin, so WebView blocks the response.
                            // Proxy these API calls through OkHttp and return with permissive CORS headers.
                            val isCorsSensitiveApi = (low.contains("/api/") || low.contains("/api-config/")) &&
                                                     (low.contains("s1.vidhide") || low.contains("s2.vidhide") || low.contains("s3.vidhide") ||
                                                      low.contains("s1.fujihide") || low.contains("s2.fujihide") || low.contains("s3.fujihide"))
                            if (isCorsSensitiveApi) {
                                try {
                                    val reqBuilder = okhttp3.Request.Builder().url(u)
                                    val ua = r.requestHeaders["User-Agent"] ?: view.settings.userAgentString ?: com.duta.movie.util.NetworkConfig.MOBILE_USER_AGENT
                                    reqBuilder.header("User-Agent", ua)
                                    // Forward all original request headers (Referer, Origin, etc.)
                                    for ((k, v) in r.requestHeaders) {
                                        if (!k.equals("User-Agent", ignoreCase = true) &&
                                            !k.equals("Accept-Encoding", ignoreCase = true) &&
                                            !k.equals("Host", ignoreCase = true)) {
                                            reqBuilder.header(k, v)
                                        }
                                    }
                                    // Forward cookies
                                    val cookieManager = android.webkit.CookieManager.getInstance()
                                    val cookies = cookieManager.getCookie(u)
                                    if (!cookies.isNullOrEmpty()) {
                                        reqBuilder.header("Cookie", cookies)
                                    }
                                    val call = com.duta.movie.util.NetworkConfig.permissiveOkHttpClient.newCall(reqBuilder.build())
                                    val resp = call.execute()
                                    val body = resp.body
                                    if (body != null) {
                                        val contentType = body.contentType()
                                        val mimeType = contentType?.let { "${it.type}/${it.subtype}" } ?: "application/json"
                                        val charset = contentType?.charset()?.name() ?: "UTF-8"
                                        // Store any Set-Cookie from the response
                                        resp.headers("Set-Cookie").forEach { cookie ->
                                            cookieManager.setCookie(u, cookie)
                                        }
                                        val responseHeaders = mutableMapOf<String, String>()
                                        responseHeaders["Access-Control-Allow-Origin"] = "*"
                                        responseHeaders["Access-Control-Allow-Headers"] = "*"
                                        responseHeaders["Access-Control-Allow-Methods"] = "GET, POST, OPTIONS"
                                        Log.d("VideoPlayerTurbo", "CORS-proxied VidHide API (${resp.code}): $u")
                                        return android.webkit.WebResourceResponse(mimeType, charset, resp.code, resp.message.ifEmpty { "OK" }, responseHeaders, body.byteStream())
                                    }
                                } catch (e: Exception) {
                                    Log.w("VideoPlayerTurbo", "CORS-proxy failed for VidHide API: ${e.message}")
                                }
                            }
                            return null // Never block legitimate Vidhide player assets, video chunks, or steganographic key poster images
                        }

                        // Intercept KotakAjaib / PusatFilm wrapper pages to eradicate anti-hotlink redirect
                        val isKotakPage = (low.contains("kotakajaib.me") || low.contains("kotakfiles") || low.contains("kotak")) &&
                                          !low.contains(".js") && !low.contains(".css") && !low.contains(".jpg") && 
                                          !low.contains(".png") && !low.contains(".mp4") && !low.contains(".m3u8")
                        if (isKotakPage) {
                            try {
                                val reqBuilder = okhttp3.Request.Builder().url(u)
                                val ua = r.requestHeaders["User-Agent"] ?: view.settings.userAgentString ?: com.duta.movie.util.NetworkConfig.MOBILE_USER_AGENT
                                reqBuilder.header("User-Agent", ua)
                                val defaultRef = com.duta.movie.util.VideoExtractor.getDefaultRefererForVideoId(videoId)
                                reqBuilder.header("Referer", defaultRef)
                                val call = com.duta.movie.util.NetworkConfig.permissiveOkHttpClient.newCall(reqBuilder.build())
                                val resp = call.execute()
                                resp.use { response ->
                                    if (response.isSuccessful) {
                                        var rawHtml = response.body?.string() ?: ""
                                        if (rawHtml.isNotEmpty()) {
                                            // Neutralize anti-hotlink redirect
                                            rawHtml = rawHtml.replace(Regex("""(?:window\.)?(?:top\.)?location(?:\.href)?\s*=\s*['"]https?://[^'"]*pusatfilm[^'"]*['"]"""), "/* blocked redirect */;")
                                            rawHtml = rawHtml.replace("window.location.href = \"https://pusatfilm21.pw\"", "/* blocked redirect */;")
                                            rawHtml = rawHtml.replace("window.location != window.parent.location", "false")
                                            Log.d("VideoPlayerTurbo", "Neutralized KotakAjaib anti-hotlink redirect for: $u")
                                            return android.webkit.WebResourceResponse("text/html", "UTF-8", java.io.ByteArrayInputStream(rawHtml.toByteArray(Charsets.UTF_8)))
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w("VideoPlayerTurbo", "Failed to intercept KotakAjaib wrapper page: ${e.message}")
                            }
                        }

                        // Intercept Abyss/Bond/Playsobat Player HTML pages to eradicate the big SVG play button overlay, videoInfo popup & anti-framing redirect
                        val isAbyssPlayerPage = (low.contains("abyssplayer.com") || low.contains("bondplayer.com") || 
                                                 low.contains("abyss.to") || low.contains("bond.to") ||
                                                 low.contains("playsobat.xyz") || low.contains("abysscdn.com") ||
                                                 low.contains("bondcdn.com") || low.contains("hydrax")) &&
                                                !low.contains(".js") && !low.contains(".css") && !low.contains(".jpg") && 
                                                !low.contains(".png") && !low.contains(".m3u8") && !low.contains(".mp4") && 
                                                !low.contains(".mkv") && !low.contains(".webm") && 
                                                !low.contains(".ts") && !low.contains(".ico") && !low.contains("/cdn-cgi/")
                        if (isAbyssPlayerPage) {
                            try {
                                val reqBuilder = okhttp3.Request.Builder().url(u)
                                val ua = r.requestHeaders["User-Agent"] ?: view.settings.userAgentString ?: com.duta.movie.util.NetworkConfig.MOBILE_USER_AGENT
                                reqBuilder.header("User-Agent", ua)
                                val defaultRef = com.duta.movie.util.VideoExtractor.getDefaultRefererForVideoId(videoId)
                                val referer = r.requestHeaders["Referer"] ?: (if (!lastReferer.isNullOrEmpty() && !lastReferer.contains("157.245.199.231")) lastReferer else defaultRef)
                                reqBuilder.header("Referer", referer)
                                val cookieManager = android.webkit.CookieManager.getInstance()
                                val cookies = cookieManager.getCookie(u)
                                if (!cookies.isNullOrEmpty()) {
                                    reqBuilder.header("Cookie", cookies)
                                }
                                for ((k, v) in r.requestHeaders) {
                                    if (!k.equals("User-Agent", ignoreCase = true) && 
                                        !k.equals("Referer", ignoreCase = true) && 
                                        !k.equals("Cookie", ignoreCase = true) &&
                                        !k.equals("Accept-Encoding", ignoreCase = true) &&
                                        !k.equals("Host", ignoreCase = true) &&
                                        !k.equals("Range", ignoreCase = true)) {
                                        reqBuilder.header(k, v)
                                    }
                                }
                                val call = com.duta.movie.util.NetworkConfig.permissiveOkHttpClient.newCall(reqBuilder.build())
                                val resp = call.execute()
                                resp.use { response ->
                                    if (response.isSuccessful) {
                                        val rawHtml = response.body?.string() ?: ""
                                        if (rawHtml.isNotEmpty() && (rawHtml.contains("SoTrym") || rawHtml.contains("playback") || rawHtml.contains("overlay") || rawHtml.contains("jwplayer") || rawHtml.contains("eval(function(p,a,c,k,e,d)"))) {
                                            val sanitized = sanitizeAbyssHtml(rawHtml, nukerScript)
                                            Log.d("VideoPlayerTurbo", "Neutralized Abyss/Bond/Playsobat player page overlay & redirect: $u")
                                            return android.webkit.WebResourceResponse("text/html", "UTF-8", java.io.ByteArrayInputStream(sanitized.toByteArray(Charsets.UTF_8)))
                                        }
                                        // Got 200 but not a real player page (challenge page served as 200, or empty) -- let WebView handle
                                        Log.d("VideoPlayerTurbo", "Abyss 200 but not a player page (len=${rawHtml.length}), delegating to WebView: $u")
                                    } else {
                                        Log.d("VideoPlayerTurbo", "Abyss returned HTTP ${response.code} (Cloudflare?), delegating to WebView: $u")
                                        if (response.code in listOf(404, 410) && 
                                            (low.contains("abysscdn.com") || low.contains("abyssplayer.com") || low.contains("playsobat.xyz"))) {
                                            view.post {
                                                val activeUrl = view.getTag(R.id.active_url) as? String ?: url
                                                Log.e("VideoPlayerTurbo", "Abyss endpoint returned fatal HTTP ${response.code} ($u), failing fast: $activeUrl")
                                                onMirrorDead(activeUrl)
                                            }
                                        }
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
                            // Never block images from the embed host itself — they are legitimate player assets
                            // (poster images, steganographic key images, player UI elements)
                            val isEmbedHostImage = (low.contains(".png") || low.contains(".jpg") || low.contains(".jpeg") || low.contains(".gif") || low.contains(".svg") || low.contains(".ico")) &&
                                (low.contains("playerp2p") || low.contains("abysscdn") || low.contains("abyssplayer") ||
                                 low.contains("bondcdn") || low.contains("bondplayer") || low.contains("playsobat") ||
                                 low.contains("embed4me") || low.contains("indostream") || low.contains("vidhide") ||
                                 low.contains("fujihide") || low.contains("zeus") || low.contains("klik") ||
                                 low.contains("pyrox") || low.contains("embedpyrox"))
                            if (!isEmbedHostImage && !low.contains("master.m3u8") && !low.contains("index.m3u8") && !low.contains("/amt/") && !low.contains(".amt") && !low.contains("amt1.pro") && !low.contains("amt2.pro")) {
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
                        
                        val isVidhideStream = low.contains("vidhide") || low.contains("fujihide") || low.contains("tnmr.org") ||
                                              url.lowercase().contains("vidhide") || url.lowercase().contains("fujihide")
                        val isAsiastream = low.contains("asiastream") || url.lowercase().contains("asiastream")
                        val isProtectedStream = (low.contains("playmogo") || 
                                                low.contains("digitalidentity") || low.contains("sunrisevalleycreative") ||
                                                low.contains("johnfullwonder") || low.contains("voe") ||
                                                low.contains("platformdocumentation") || low.contains("hgcloud") || low.contains("hglink") ||
                                                low.contains("hanerix") || low.contains("vibuxer") || low.contains("audinifer") ||
                                                isVidhideStream || isAsiastream ||
                                                com.duta.movie.util.VideoExtractor.isJsOnlyHost(u) ||
                                                com.duta.movie.util.VideoExtractor.isJsOnlyHost(url)) &&
                                                !low.contains("cloudwindow") &&
                                                (!low.contains(".m3u8") || isVidhideStream || isAsiastream) &&
                                                (!low.contains(".txt") || isAsiastream) &&
                                                !low.contains(".mp4") && !low.contains(".mkv") && !low.contains(".webm")

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
             if (view.getTag(R.id.active_url) != url || isNewEpisode) {
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
                     low.contains("vidhide") || low.contains("fujihide") || low.contains("tnmr.org") ||
                     forceWebViewHosts.any { low.contains(it) }
                 )
                 val isTVDevice = isTV || com.duta.movie.util.DeviceUtils.isTvDevice(view.context)
                 view.settings.userAgentString = if (isStrict || (isDailymotion && isTVDevice)) NetworkConfig.MOBILE_USER_AGENT else NetworkConfig.SHARED_USER_AGENT
             }
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
              } else if (url.contains("abyssplayer.com") || url.contains("bondplayer.com") || url.contains("abyss.to") || url.contains("bond.to") || url.contains("abysscdn.com") || url.contains("bondcdn.com") || url.contains("playsobat.xyz") || url.contains("hydrax")) {
                  if (view.getTag(R.id.active_url) != url || isNewEpisode) {
                      view.setTag(R.id.active_content_key, activeContentKey)
                      view.setTag(R.id.active_url, url)
                      val defaultRef = com.duta.movie.util.VideoExtractor.getDefaultRefererForVideoId(videoId)
                      val referer = if (!lastReferer.isNullOrEmpty() && !lastReferer.contains("157.245.199.231")) lastReferer else defaultRef
                      val ua = view.settings.userAgentString ?: com.duta.movie.util.NetworkConfig.MOBILE_USER_AGENT
                      scope.launch(Dispatchers.IO) {
                          try {
                              val req = okhttp3.Request.Builder()
                                  .url(url)
                                  .header("User-Agent", ua)
                                  .header("Referer", referer)
                                  .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                                  .header("Accept-Language", "en-US,en;q=0.9")
                                  .build()
                              val resp = com.duta.movie.util.NetworkConfig.permissiveOkHttpClient.newCall(req).execute()
                              val rawHtml = resp.use { if (it.isSuccessful) it.body?.string() else null }
                              if (!rawHtml.isNullOrEmpty() && (rawHtml.contains("SoTrym") || rawHtml.contains("playback") || rawHtml.contains("overlay") || rawHtml.contains("jwplayer") || rawHtml.contains("eval(function(p,a,c,k,e,d)"))) {
                                  val sanitized = sanitizeAbyssHtml(rawHtml, nukerScript)
                                  withContext(Dispatchers.Main) {
                                      if (view.getTag(R.id.active_url) == url) {
                                          Log.d("VideoPlayerTurbo", "Directly loading pre-sanitized Abyss page into WebView: $url")
                                          view.loadDataWithBaseURL(url, sanitized, "text/html", "UTF-8", null)
                                      }
                                  }
                                  return@launch
                              }
                          } catch (e: Exception) {
                              Log.w("VideoPlayerTurbo", "Direct Abyss pre-fetch failed: ${e.message}")
                          }
                          // Fallback when pre-fetch fails or returns challenge:
                          // Load via a pitch-black iframe wrapper with base URL set to playerOrigin.
                          // An iframe wrapper is CRITICAL because Abyss has native JS:
                          // if(top.location == self.location && !/^(.+?)\.abyss\.to$/.test(document.location.hostname)) { window.location = "https://abyss.to"; }
                          // Loading in an iframe ensures top.location !== self.location, preventing the anti-framing redirect to white abyss.to!
                          // Using playerOrigin makes the wrapper same-origin with the iframe, granting full DOM access to purge the play icon.
                          withContext(Dispatchers.Main) {
                              if (view.getTag(R.id.active_url) == url) {
                                  val playerOrigin = try {
                                      val u = android.net.Uri.parse(url)
                                      "${u.scheme}://${u.host}"
                                  } catch (e: Exception) {
                                      referer
                                  }
                                  Log.d("VideoPlayerTurbo", "Loading Abyss in black same-origin iframe wrapper (origin: $playerOrigin): $url")
                                  val iframeHtml = """
                                      <!DOCTYPE html>
                                      <html style="background:#000!important;background-color:#000!important;">
                                      <head>
                                          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                                          <style>
                                              html, body { margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; background: #000 !important; background-color: #000 !important; }
                                              iframe { border: none; width: 100%; height: 100%; display: block; background: #000 !important; background-color: #000 !important; }
                                          </style>
                                      </head>
                                      <body style="background:#000!important;background-color:#000!important;">
                                          <iframe id="playerFrame" src="$url" allow="autoplay *; fullscreen *; encrypted-media *; picture-in-picture *" allowfullscreen style="background:#000!important;background-color:#000!important;"></iframe>
                                          <script>
                                          (function() {
                                              var cleanIframe = function() {
                                                  try {
                                                      var f = document.getElementById('playerFrame');
                                                      if (!f) return;

                                                      // Expose AndroidPlayer interface into child iframe window
                                                      try {
                                                          if (window.AndroidPlayer && f.contentWindow && !f.contentWindow.AndroidPlayer) {
                                                              f.contentWindow.AndroidPlayer = window.AndroidPlayer;
                                                          }
                                                      } catch(e){}

                                                      var doc = f.contentDocument || (f.contentWindow && f.contentWindow.document);
                                                      if (!doc) return;
                                                      if (doc.documentElement) {
                                                          doc.documentElement.style.setProperty('background', '#000', 'important');
                                                          doc.documentElement.style.setProperty('background-color', '#000', 'important');
                                                      }
                                                      if (doc.body) {
                                                          doc.body.style.setProperty('background', '#000', 'important');
                                                          doc.body.style.setProperty('background-color', '#000', 'important');
                                                          doc.body.classList.add('video-active', 'video-playing');
                                                      }
                                                      var bgEls = doc.querySelectorAll('.stage, .wrap, #player, #content, .container, main, .jwplayer, .jw-wrapper, .jw-media, div#player, div.player, .player');
                                                      for (var bgi = 0; bgi < bgEls.length; bgi++) {
                                                          bgEls[bgi].style.setProperty('background', '#000', 'important');
                                                          bgEls[bgi].style.setProperty('background-color', '#000', 'important');
                                                      }
                                                      if (!doc.getElementById('abyss-cleaner-style')) {
                                                          var s = doc.createElement('style');
                                                          s.id = 'abyss-cleaner-style';
                                                          s.textContent = 'html, body, .stage, .wrap, #player, .jwplayer, .jw-wrapper, .jw-media, div#player, div.player, .player { background: #000 !important; background-color: #000 !important; } #overlay, #playback, #overlay *, #playback *, div#overlay, div#playback, .jw-display-icon-display, .jw-display-icon-container, .jw-display-icon-idle, .jw-icon-display, .jw-display, .jw-svg-icon-play, .jw-flag-fullscreen .jw-display-icon-display, .jw-button-color, .vjs-big-play-button, .vjs-big-play-button-mobile, .play-button, #play-button, .play-btn, #play-btn, .big-play, .big-play-btn, .big-play-button, .large-play-button, .ytp-large-play-button, .play-overlay, #videoInfo, .video-info, [id*="videoInfo"], [class*="video-info"], .video-info-title, .video-info-hint, .video-info-close, svg, svg[viewBox="0 0 24 24"], svg[viewBox="0 0 240 240"], #playback svg, #overlay svg { display: none !important; opacity: 0 !important; visibility: hidden !important; pointer-events: none !important; width: 0 !important; height: 0 !important; max-width: 0 !important; max-height: 0 !important; z-index: -99999 !important; }';
                                                          if (doc.head) doc.head.appendChild(s);
                                                          else if (doc.body) doc.body.appendChild(s);
                                                      }
                                                      var bads = doc.querySelectorAll('#overlay, #playback, div#overlay, div#playback, #videoInfo, .video-info, [id*="videoInfo"], [class*="video-info"], .jw-display-icon-display, .jw-display-icon-container, .jw-display-icon-idle, .jw-icon-display, .jw-display, .jw-svg-icon-play, .vjs-big-play-button, .play-button, #play-button, .play-btn, .big-play-button, svg, svg[viewBox="0 0 24 24"], svg[viewBox="0 0 240 240"]');
                                                      for (var i = 0; i < bads.length; i++) {
                                                          bads[i].style.setProperty('display', 'none', 'important');
                                                          bads[i].style.setProperty('opacity', '0', 'important');
                                                          bads[i].style.setProperty('visibility', 'hidden', 'important');
                                                          bads[i].style.setProperty('pointer-events', 'none', 'important');
                                                          bads[i].style.setProperty('width', '0', 'important');
                                                          bads[i].style.setProperty('height', '0', 'important');
                                                          try { bads[i].remove(); } catch(e) {}
                                                      }
                                                      if (typeof f.contentWindow.closeVideoInfo === 'function') {
                                                          try { f.contentWindow.closeVideoInfo(); } catch(e) {}
                                                      }

                                                      // Direct hook for JWPlayer inside child iframe
                                                      try {
                                                          if (f.contentWindow && typeof f.contentWindow.jwplayer === 'function') {
                                                              var jw = f.contentWindow.jwplayer();
                                                              if (jw) {
                                                                  if (typeof jw.getState === 'function') {
                                                                      var st = jw.getState();
                                                                      if (st !== 'playing' && st !== 'buffering') {
                                                                          try {
                                                                              var playPromise = jw.play();
                                                                              if (playPromise && typeof playPromise.catch === 'function') {
                                                                                  playPromise.catch(function(err) {
                                                                                      try {
                                                                                          if (typeof jw.setMute === 'function') jw.setMute(true);
                                                                                          var p2 = jw.play();
                                                                                          if (p2 && typeof p2.catch === 'function') p2.catch(function(){});
                                                                                      } catch(e){}
                                                                                  });
                                                                              }
                                                                          } catch(e) {
                                                                              try {
                                                                                  if (typeof jw.setMute === 'function') jw.setMute(true);
                                                                                  var p2 = jw.play();
                                                                                  if (p2 && typeof p2.catch === 'function') p2.catch(function(){});
                                                                              } catch(err){}
                                                                          }
                                                                      }
                                                                      if (st === 'playing' || (jw.getPosition && jw.getPosition() > 0.3)) {
                                                                          if (window.AndroidPlayer && window.AndroidPlayer.notifyVideoPlaying) {
                                                                              window.AndroidPlayer.notifyVideoPlaying();
                                                                          }
                                                                          if (window.AndroidPlayer && window.AndroidPlayer.onPlayerState) {
                                                                              window.AndroidPlayer.onPlayerState(1, jw.getPosition ? jw.getPosition() : 0.1, jw.getDuration ? jw.getDuration() : 0);
                                                                          }
                                                                      }
                                                                  }
                                                                  if (typeof jw.on === 'function' && !f.contentWindow._jwDutaHooked) {
                                                                      f.contentWindow._jwDutaHooked = true;
                                                                      var notifySuccess = function() {
                                                                          if (!window.successNotified && window.AndroidPlayer && window.AndroidPlayer.notifyVideoPlaying) {
                                                                              window.successNotified = true;
                                                                              window.AndroidPlayer.notifyVideoPlaying();
                                                                          }
                                                                      };
                                                                      // Autoplay policy and HTML5 error auto-recovery
                                                                      jw.on('warning', function(warn) {
                                                                          var code = (warn && warn.code) ? warn.code : 0;
                                                                          if (code === 324003 || code === 305000) {
                                                                              try {
                                                                                  if (typeof jw.setMute === 'function') jw.setMute(true);
                                                                                  var p = jw.play();
                                                                                  if (p && typeof p.catch === 'function') p.catch(function(){});
                                                                              } catch(e){}
                                                                          }
                                                                      });
                                                                      jw.on('error', function(err) {
                                                                          var code = (err && err.code) ? err.code : 0;
                                                                          if (code === 102630 || code === 324003) {
                                                                              try {
                                                                                  if (typeof jw.setMute === 'function') jw.setMute(true);
                                                                                  var p = jw.play();
                                                                                  if (p && typeof p.catch === 'function') p.catch(function(){});
                                                                              } catch(e){}
                                                                          }
                                                                      });
                                                                      jw.on('play', function() {
                                                                          notifySuccess();
                                                                          if (window.AndroidPlayer && window.AndroidPlayer.onPlayerState) {
                                                                              window.AndroidPlayer.onPlayerState(1, jw.getPosition ? jw.getPosition() : 0.1, jw.getDuration ? jw.getDuration() : 0);
                                                                          }
                                                                      });
                                                                      jw.on('firstFrame', notifySuccess);
                                                                      jw.on('time', function(e) {
                                                                          if (e && e.currentTime > 0.3) {
                                                                              notifySuccess();
                                                                              var curSec = Math.floor(e.currentTime);
                                                                              if (curSec !== f._lastJwSec) {
                                                                                  f._lastJwSec = curSec;
                                                                                  if (window.AndroidPlayer && window.AndroidPlayer.onPlayerState) {
                                                                                      window.AndroidPlayer.onPlayerState(1, e.currentTime, e.duration || (jw.getDuration ? jw.getDuration() : 0));
                                                                                  }
                                                                              }
                                                                          }
                                                                      });
                                                                      jw.on('pause', function() {
                                                                          if (window.AndroidPlayer && window.AndroidPlayer.onPlayerState) {
                                                                              window.AndroidPlayer.onPlayerState(2, jw.getPosition ? jw.getPosition() : 0.1, jw.getDuration ? jw.getDuration() : 0);
                                                                          }
                                                                      });
                                                                  }
                                                                  if (!f.contentWindow._unmuteHooked && doc) {
                                                                      f.contentWindow._unmuteHooked = true;
                                                                      var unmuteAll = function() {
                                                                          try {
                                                                              if (typeof jw.setMute === 'function') jw.setMute(false);
                                                                              var allVids = doc.querySelectorAll('video, audio');
                                                                              for (var vi = 0; vi < allVids.length; vi++) {
                                                                                  allVids[vi].muted = false;
                                                                              }
                                                                          } catch(e){}
                                                                      };
                                                                      doc.addEventListener('click', unmuteAll, { once: true, passive: true });
                                                                      doc.addEventListener('touchstart', unmuteAll, { once: true, passive: true });
                                                                  }
                                                              }
                                                          }
                                                      } catch(e){}

                                                      // Direct hook for video/audio elements inside child iframe
                                                      try {
                                                          var vids = doc.querySelectorAll('video, audio');
                                                          for (var vi = 0; vi < vids.length; vi++) {
                                                              var vid = vids[vi];
                                                              if (vid) {
                                                                  if (!vid._cleanHooked) {
                                                                      vid._cleanHooked = true;
                                                                      vid.addEventListener('play', function() {
                                                                          if (window.AndroidPlayer && window.AndroidPlayer.notifyVideoPlaying) {
                                                                              window.AndroidPlayer.notifyVideoPlaying();
                                                                          }
                                                                          if (window.AndroidPlayer && window.AndroidPlayer.onPlayerState) {
                                                                              window.AndroidPlayer.onPlayerState(1, this.currentTime || 0.1, this.duration || 0);
                                                                          }
                                                                      });
                                                                      vid.addEventListener('timeupdate', function() {
                                                                          if (this.currentTime > 0.3) {
                                                                              if (!window.successNotified && window.AndroidPlayer && window.AndroidPlayer.notifyVideoPlaying) {
                                                                                  window.successNotified = true;
                                                                                  window.AndroidPlayer.notifyVideoPlaying();
                                                                              }
                                                                              var curSec = Math.floor(this.currentTime);
                                                                              if (curSec !== this._lastSec) {
                                                                                  this._lastSec = curSec;
                                                                                  if (window.AndroidPlayer && window.AndroidPlayer.onPlayerState) {
                                                                                      window.AndroidPlayer.onPlayerState(1, this.currentTime, this.duration || 0);
                                                                                  }
                                                                              }
                                                                          }
                                                                      });
                                                                      vid.addEventListener('pause', function() {
                                                                          if (window.AndroidPlayer && window.AndroidPlayer.onPlayerState) {
                                                                              window.AndroidPlayer.onPlayerState(2, this.currentTime || 0.1, this.duration || 0);
                                                                          }
                                                                      });
                                                                  }
                                                                  if (vid.paused) {
                                                                      try {
                                                                          var p = vid.play();
                                                                          if (p && typeof p.catch === 'function') {
                                                                              p.catch(function(err) {
                                                                                  try {
                                                                                      vid.muted = true;
                                                                                      var p2 = vid.play();
                                                                                      if (p2 && typeof p2.catch === 'function') p2.catch(function(){});
                                                                                  } catch(e){}
                                                                              });
                                                                          }
                                                                      } catch(e) {
                                                                          try {
                                                                              vid.muted = true;
                                                                              var p2 = vid.play();
                                                                              if (p2 && typeof p2.catch === 'function') p2.catch(function(){});
                                                                          } catch(err){}
                                                                      }
                                                                  }
                                                                  if (!window.successNotified && !vid.paused && (vid.currentTime > 0.3 || vid.readyState >= 2)) {
                                                                      if (window.AndroidPlayer && window.AndroidPlayer.notifyVideoPlaying) {
                                                                          window.successNotified = true;
                                                                          window.AndroidPlayer.notifyVideoPlaying();
                                                                      }
                                                                  }
                                                              }
                                                          }
                                                      } catch(e){}

                                                  } catch(e) {}
                                              };
                                              var f = document.getElementById('playerFrame');
                                              if (f) {
                                                  f.addEventListener('load', cleanIframe);
                                              }
                                                                                            cleanIframe();
                                              setInterval(cleanIframe, 100);
                                          })();
                                          </script>
                                      </body>
                                      </html>
                                  """.trimIndent()
                                  view.loadDataWithBaseURL(playerOrigin, iframeHtml, "text/html", "UTF-8", null)
                              }
                          }
                      }
                  }
              } else if (view.url != url || isNewEpisode) {
                 // OWL'S EYE: Sticky Referer Lockdown (v6.8)
                 // Hard-coding the referer to the primary site root ensures gateways don't redirect to home.
                 val defaultRef = com.duta.movie.util.VideoExtractor.getDefaultRefererForVideoId(videoId)
                 val referer = if (!lastReferer.isNullOrEmpty() && !lastReferer.contains("157.245.199.231")) lastReferer else defaultRef
                 
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
                .distinctBy { 
                    if (it.name.startsWith("MovieBox", ignoreCase = true)) it.name.trim() 
                    else "${it.name.trim()}_${it.url.substringBefore('?')}"
                }
                .sortedWith(
                    compareBy<com.duta.movie.model.VideoServer> { s ->
                        if (com.duta.movie.util.VideoExtractor.isAlternativePartnerServer(s.name, s.url)) 1 else 0
                    }.thenByDescending { s ->
                        com.duta.movie.util.VideoExtractor.getProviderPriority(s.name, s.url)
                    }.thenBy { s -> s.name }
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(if (isTV) 6.dp else 2.dp)) {
                itemsIndexed(cleanServers, key = { index, server -> "${server.name}_${server.url}_$index" }) { index, server ->
                    val isSelected = server.url == currentServerUrl
                    var isFocused by remember { mutableStateOf(false) }
                    val lowU = server.url.lowercase()
                    val lowN = server.name.lowercase()
                    val platformBadge = remember(server.url, server.name) {
                        when {
                            lowU.contains("hakunaymatata") || lowU.contains("aoneroom") || lowN.contains("moviebox") -> "MovieBox (Direct)" to Color(0xFFFFD700)
                            lowU.contains("hglink") || lowN.contains("hglink") -> "HGLink (Top)" to Color(0xFF00E676)
                            lowU.contains("hgcloud") || lowN.contains("hgcloud") -> "HgCloud" to Color(0xFF00B0FF)
                            lowU.contains("indostream") || lowN.contains("indostream") || com.duta.movie.util.VideoExtractor.isIndoStreamAmt(server.url) -> "IndoStream" to Color(0xFFFF9100)
                            lowU.contains("voe") || lowN.contains("voe") -> "VOE" to Color(0xFFE040FB)
                            lowU.contains("vidhide") || lowN.contains("vidhide") -> "VidHide" to Color(0xFFFF5252)
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
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                    (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                                     keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                                     keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                                    onServerSelect(server.url, forceWebView)
                                    true
                                } else false
                            }
                            .clickable { onServerSelect(server.url, forceWebView) }
                            .focusable()
                            .border(if (isFocused) BorderStroke(if (isTV) 3.dp else 2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(8.dp)),
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
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                            (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                             keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                             keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                            onDismiss()
                            true
                        } else false
                    }
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

