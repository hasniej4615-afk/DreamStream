package com.duta.movie.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.speech.RecognizerIntent
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duta.movie.LocalPipMode
import com.duta.movie.util.DeviceUtils
import kotlinx.coroutines.delay
import java.io.ByteArrayInputStream
import java.net.URLEncoder

private const val TAG = "YouTubeScreen"
private const val YOUTUBE_BASE_URL = "https://m.youtube.com"

private val BLOCKED_AD_HOSTS = listOf(
    "googleads.g.doubleclick.net",
    "pagead2.googlesyndication.com",
    "adservice.google.com",
    "static.doubleclick.net",
    "doubleclick.net",
    "ad.doubleclick.net",
    "securepubads.g.doubleclick.net",
    "pubads.g.doubleclick.net"
)

private val BLOCKED_AD_PATHS = listOf(
    "/pagead/",
    "/api/stats/ads",
    "/get_midroll_info",
    "/youtubei/v1/player/ad_break"
)

/**
 * Robust YouTube Ad-Nuker, SponsorBlock, and Return YouTube Dislike (RYD) script.
 * Safely removes ad banners, auto-skips video ads, and integrates with Duta bridge
 * without interfering with YouTube's core video decryption or media pipeline.
 */
private const val YOUTUBE_AD_NUKER_SCRIPT = """
(function() {
    try {
        // 1. Inject CSS for ad suppression, TV focus styling, and YouTube dark mode
        if (!document.getElementById('duta-yt-style')) {
            var style = document.createElement('style');
            style.id = 'duta-yt-style';
            style.textContent = `
                /* Hide YouTube Web mobile top header so our native Compose header acts as authentic UI */
                ytm-mobile-topbar-renderer,
                header.mobile-topbar-header,
                .mobile-topbar-header,
                #header-bar {
                    display: none !important;
                }

                /* Block & suppress ad banners, sponsored video cards, and promo dialogs */
                .video-ads, .ytp-ad-module, .ytp-ad-overlay-container,
                .ytp-ad-message-container, .ytp-ad-player-overlay,
                .ytp-ad-text, .ytp-ad-preview-container,
                .ytp-ad-action-interstitial, .ytp-ad-image-overlay,
                .ytp-ad-button-icon, .ytp-ad-skip-button-slot,
                .ytp-ad-hover-text-container, .ytp-ad-info-dialog-background,
                ytm-promoted-sparkles-web-renderer,
                ytm-promoted-sparkles-text-search-web-renderer,
                ytm-statement-banner-renderer,
                ytm-mealbar-promo-renderer,
                ytm-upsell-dialog-renderer,
                .upsell-dialog-renderer,
                ytm-open-app-button-renderer,
                .mobile-topbar-header-sign-in-button,
                ytm-pivot-bar-item-renderer:has([aria-label*="Create"]),
                .ad-container, #player-ads, .player-ads,
                ytm-compact-promoted-video-renderer,
                ytm-promoted-video-renderer,
                ytm-in-feed-ad-layout-renderer,
                ytm-brand-video-singleton-renderer,
                ytm-ad-slot-renderer,
                ad-slot-renderer,
                ytm-item-section-renderer:has(ytm-promoted-sparkles-web-renderer),
                ytm-item-section-renderer:has(ytm-ad-slot-renderer) {
                    display: none !important;
                    visibility: hidden !important;
                    opacity: 0 !important;
                    height: 0 !important;
                    pointer-events: none !important;
                }

                /* Authentic YouTube Dark background */
                html, body, #app {
                    background-color: #0F0F0F !important;
                    color: #FFFFFF !important;
                }

                /* TV D-Pad Focus Outline for remote navigation */
                *:focus, [tabindex]:focus, a:focus, button:focus,
                ytm-video-with-context-renderer:focus,
                ytm-compact-video-renderer:focus,
                ytm-item-section-renderer:focus {
                    outline: 3px solid #FF0000 !important;
                    outline-offset: 3px !important;
                    box-shadow: 0 0 20px rgba(255, 0, 0, 0.85) !important;
                    border-radius: 12px !important;
                    transform: scale(1.02) !important;
                    transition: transform 0.15s ease, outline 0.15s ease !important;
                }
            `;
            (document.head || document.documentElement).appendChild(style);
        }

        var currentTrackedVideoId = null;
        var sponsorSegments = [];

        function formatCount(num) {
            if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M';
            if (num >= 1000) return (num / 1000).toFixed(1) + 'K';
            return num.toString();
        }

        function checkVideoMetadata() {
            var match = location.href.match(/[?&]v=([^&#]+)/) || location.href.match(/\/shorts\/([^&#/?]+)/);
            var vId = match ? match[1] : null;
            if (vId && vId !== currentTrackedVideoId) {
                currentTrackedVideoId = vId;
                sponsorSegments = [];

                // 1. Fetch SponsorBlock segments
                fetch('https://sponsor.ajay.app/api/skipSegments?videoID=' + vId + '&categories=["sponsor","selfpromo","interaction","intro","outro"]')
                    .then(function(r) { return r.json(); })
                    .then(function(data) {
                        if (Array.isArray(data)) {
                            sponsorSegments = data;
                        }
                    })
                    .catch(function(){});

                // 2. Fetch Return YouTube Dislike (RYD)
                fetch('https://returnyoutubedislikeapi.com/votes?videoId=' + vId)
                    .then(function(r) { return r.json(); })
                    .then(function(data) {
                        if (data && data.dislikes !== undefined) {
                            notifyBridge(vId, data);
                            updateDislikeButton(data);
                        }
                    })
                    .catch(function(){});

                notifyBridge(vId, null);
            }
        }

        function updateDislikeButton(data) {
            if (!data) return;
            var formatted = formatCount(data.dislikes);
            var dislikeBtn = document.querySelector('dislike-button-view-model button') ||
                               document.querySelector('.slim-video-action-bar-actions button[aria-label*="Dislike" i]') ||
                               document.querySelector('[aria-label*="Dislike" i]');
            if (dislikeBtn) {
                var label = dislikeBtn.querySelector('.yt-spec-button-shape-next__button-text-content, span');
                if (label) label.textContent = formatted;
            }
        }

        function notifyBridge(vId, ryd) {
            if (!window.DutaYouTubeBridge) return;
            var titleEl = document.querySelector('h1.slim-video-metadata-title, h1.title, ytm-slim-video-metadata-section-renderer h1');
            var title = titleEl ? titleEl.textContent.trim() : document.title.replace(' - YouTube', '');
            var likes = (ryd && ryd.likes !== undefined) ? formatCount(ryd.likes) : '';
            var dislikes = (ryd && ryd.dislikes !== undefined) ? formatCount(ryd.dislikes) : '';
            window.DutaYouTubeBridge.onVideoDetected(vId, title, likes, dislikes);
        }

        function processAdsAndPlayback() {
            try {
                checkVideoMetadata();

                var player = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
                var video = document.querySelector('video');

                // Strictly detect if an ad is actively playing
                var isAdActive = player && (player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting'));

                if (isAdActive) {
                    // 1. Try native player.skipAd
                    if (typeof player.skipAd === 'function') {
                        try { player.skipAd(); } catch(e){}
                    }

                    // 2. Fast-forward & mute the active ad
                    if (video) {
                        video.muted = true;
                        video.playbackRate = 16.0;
                        if (video.duration && isFinite(video.duration) && video.duration > 0) {
                            video.currentTime = video.duration;
                        }
                    }

                    // 3. Click any visible skip button
                    var skipBtns = document.querySelectorAll(
                        '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .videoAdUiSkipButton, ' +
                        '.ytp-skip-ad-button, .ytp-ad-overlay-close-button, [class*="skip-button"], ' +
                        'button[aria-label*="Skip" i], button[id^="skip-button"], .ytm-skip-ad-button'
                    );
                    for (var i = 0; i < skipBtns.length; i++) {
                        try { skipBtns[i].click(); } catch(e){}
                    }
                } else if (video && video.playbackRate > 2.0) {
                    // Restore regular speed and unmute for normal video playback
                    video.playbackRate = 1.0;
                    video.muted = false;
                }

                // 4. SponsorBlock auto-skip (only for regular video content)
                if (!isAdActive && video && sponsorSegments.length > 0) {
                    var cur = video.currentTime;
                    for (var s = 0; s < sponsorSegments.length; s++) {
                        var seg = sponsorSegments[s];
                        if (cur >= seg.segment[0] && cur < seg.segment[1] - 0.3) {
                            var skipSec = Math.round(seg.segment[1] - seg.segment[0]);
                            video.currentTime = seg.segment[1];
                            if (window.DutaYouTubeBridge) {
                                window.DutaYouTubeBridge.onSponsorSkipped(seg.category, skipSec);
                            }
                            break;
                        }
                    }
                }

                // 5. Notify playback state for PiP & background audio
                if (video && window.DutaYouTubeBridge) {
                    var isPlaying = !video.paused && !video.ended && video.readyState > 2;
                    window.DutaYouTubeBridge.onPlaybackState(isPlaying, video.currentTime || 0, video.duration || 0);
                }

                // 6. Dismiss promo / upsell dialogs
                var dismissBtns = document.querySelectorAll(
                    'ytm-mealbar-promo-renderer button, ytm-upsell-dialog-renderer button, [aria-label*="Dismiss" i], [aria-label*="No thanks" i]'
                );
                for (var k = 0; k < dismissBtns.length; k++) {
                    try { dismissBtns[k].click(); } catch(e){}
                }
            } catch(err) {}
        }

        if (!window.__dutaWatchdogStarted) {
            window.__dutaWatchdogStarted = true;
            setInterval(processAdsAndPlayback, 250);

            var observer = new MutationObserver(function() {
                processAdsAndPlayback();
            });
            observer.observe(document.documentElement, {
                childList: true,
                subtree: true,
                attributes: true,
                attributeFilter: ['class']
            });
        }
        processAdsAndPlayback();
    } catch(e) {}
})();
"""

data class YouTubeCategory(
    val title: String,
    val url: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeScreen(
    initialUrl: String? = null,
    viewModel: VideoViewModel,
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val isRealTV = remember { DeviceUtils.isTvDevice(context) }
    val isInPip = LocalPipMode.current
    val myList by viewModel.myList.collectAsStateWithLifecycle()

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    val startUrl = remember { initialUrl ?: YOUTUBE_BASE_URL }
    var currentUrl by remember { mutableStateOf(startUrl) }
    var pageTitle by remember { mutableStateOf("YouTube") }
    var isLoading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    // Active video metadata from Bridge
    var activeVideoId by remember { mutableStateOf<String?>(null) }
    var activeVideoTitle by remember { mutableStateOf("") }
    var activeLikes by remember { mutableStateOf("") }
    var activeDislikes by remember { mutableStateOf("") }
    var isVideoPlaying by remember { mutableStateOf(false) }

    // SponsorBlock Toast State
    var sponsorToastMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(sponsorToastMessage) {
        if (sponsorToastMessage != null) {
            delay(2800)
            sponsorToastMessage = null
        }
    }

    // Playback Speed State (1x -> 1.25x -> 1.5x -> 2x)
    var playbackSpeed by remember { mutableStateOf(1.0f) }

    // Quality Lock State (Auto, 1080p, 720p)
    var selectedQuality by remember { mutableStateOf(if (isRealTV) "1080p" else "Auto") }

    // Black Screen Mode (OLED Audio-Only Power Saver)
    var isBlackScreenMode by remember { mutableStateOf(false) }

    // Custom full screen video view (for YouTube's native fullscreen)
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    // Original YouTube Search State
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }

    // Categories matching authentic YouTube filter chips
    val categories = remember {
        listOf(
            YouTubeCategory("All", "https://m.youtube.com"),
            YouTubeCategory("Trending", "https://m.youtube.com/feed/trending"),
            YouTubeCategory("Gaming", "https://m.youtube.com/gaming"),
            YouTubeCategory("Music", "https://m.youtube.com/channel/UC-9-kyTW8ZkZNDHQJ6FgpwQ"),
            YouTubeCategory("Live", "https://m.youtube.com/channel/UC4R8FKtAEEN3VQ7fTFpjWMA"),
            YouTubeCategory("News", "https://m.youtube.com/channel/UCYfdidRxbB8Qhf0Nx7ioOYw"),
            YouTubeCategory("Podcasts", "https://m.youtube.com/podcasts"),
            YouTubeCategory("Trailers", "https://m.youtube.com/results?search_query=movie+trailers"),
            YouTubeCategory("Animation", "https://m.youtube.com/results?search_query=animation"),
            YouTubeCategory("Shorts", "https://m.youtube.com/shorts")
        )
    }
    var selectedCategoryTitle by remember { mutableStateOf("All") }

    // Voice search launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                searchQuery = spoken
                val encoded = URLEncoder.encode(spoken, "UTF-8")
                val searchUrl = "https://m.youtube.com/results?search_query=$encoded"
                selectedCategoryTitle = ""
                webViewInstance?.loadUrl(searchUrl)
                isSearchActive = false
            }
        }
    }

    // Bridge for metadata & SponsorBlock
    val jsBridge = remember {
        object {
            @JavascriptInterface
            fun onVideoDetected(videoId: String, title: String, likes: String, dislikes: String) {
                activeVideoId = videoId
                activeVideoTitle = title
                activeLikes = likes
                activeDislikes = dislikes
            }

            @JavascriptInterface
            fun onPlaybackState(isPlaying: Boolean, currentTime: Double, duration: Double) {
                isVideoPlaying = isPlaying
                viewModel.setYouTubePlaying(isPlaying)
            }

            @JavascriptInterface
            fun onSponsorSkipped(category: String, seconds: Int) {
                val label = when (category) {
                    "sponsor" -> "Sponsor"
                    "selfpromo" -> "Self-Promo"
                    "interaction" -> "Subscribe Reminder"
                    "intro" -> "Intro"
                    "outro" -> "Outro"
                    else -> "Segment"
                }
                sponsorToastMessage = "⚡ Skipped $label (${seconds}s)"
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.setYouTubePlaying(false)
        }
    }

    // Back handling: Exit Black Screen -> Exit fullscreen -> Collapse search -> Go back in webView -> Pop screen
    BackHandler {
        if (isBlackScreenMode) {
            isBlackScreenMode = false
        } else if (customView != null) {
            customViewCallback?.onCustomViewHidden()
            customView = null
        } else if (isSearchActive) {
            isSearchActive = false
        } else if (webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        } else {
            onBackClick()
        }
    }

    val isFavorite = activeVideoId != null && myList.contains("yt_$activeVideoId")

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── AUTHENTIC YOUTUBE HEADER & FILTER BAR (Hidden in PiP) ───────
            if (customView == null && !isInPip && !isBlackScreenMode) {
                Surface(
                    color = Color(0xFF0F0F0F),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (isRealTV) 16.dp else 12.dp, vertical = 6.dp)
                    ) {
                        AnimatedContent(
                            targetState = isSearchActive,
                            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                            label = "youtube_header_mode"
                        ) { searchActive ->
                            if (searchActive) {
                                // ── ACTIVE SEARCH BAR ──
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(if (isRealTV) 48.dp else 42.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    HeaderIconButton(
                                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        isTV = isRealTV,
                                        onClick = { isSearchActive = false }
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(24.dp))
                                            .background(Color(0xFF272727))
                                            .border(
                                                width = 1.dp,
                                                color = Color(0xFF3F3F3F),
                                                shape = RoundedCornerShape(24.dp)
                                            )
                                            .padding(horizontal = 14.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            BasicTextField(
                                                value = searchQuery,
                                                onValueChange = { searchQuery = it },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .focusRequester(searchFocusRequester),
                                                singleLine = true,
                                                textStyle = TextStyle(
                                                    color = Color.White,
                                                    fontSize = if (isRealTV) 16.sp else 15.sp
                                                ),
                                                cursorBrush = SolidColor(Color.Red),
                                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                                keyboardActions = KeyboardActions(
                                                    onSearch = {
                                                        if (searchQuery.isNotBlank()) {
                                                            val encoded = URLEncoder.encode(searchQuery.trim(), "UTF-8")
                                                            val url = "https://m.youtube.com/results?search_query=$encoded"
                                                            selectedCategoryTitle = ""
                                                            webViewInstance?.loadUrl(url)
                                                            isSearchActive = false
                                                        }
                                                    }
                                                ),
                                                decorationBox = { innerTextField ->
                                                    if (searchQuery.isEmpty()) {
                                                        Text(
                                                            text = "Search YouTube",
                                                            color = Color(0xFFAAAAAA),
                                                            fontSize = if (isRealTV) 16.sp else 15.sp
                                                        )
                                                    }
                                                    innerTextField()
                                                }
                                            )

                                            if (searchQuery.isNotEmpty()) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Clear",
                                                    tint = Color.LightGray,
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .clickable { searchQuery = "" }
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    VoiceSearchButton(isTV = isRealTV) {
                                        try {
                                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                putExtra(
                                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                                )
                                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Search YouTube...")
                                            }
                                            speechLauncher.launch(intent)
                                        } catch (e: Exception) {
                                            Log.w(TAG, "Speech intent error: ${e.message}")
                                        }
                                    }
                                }
                            } else {
                                // ── STANDARD YOUTUBE BRANDED HEADER ──
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable {
                                            selectedCategoryTitle = "All"
                                            webViewInstance?.loadUrl(YOUTUBE_BASE_URL)
                                        }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(width = 30.dp, height = 21.dp)
                                                .background(Color(0xFFFF0000), shape = RoundedCornerShape(6.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Canvas(modifier = Modifier.size(9.dp)) {
                                                val path = androidx.compose.ui.graphics.Path().apply {
                                                    moveTo(0f, 0f)
                                                    lineTo(size.width, size.height / 2f)
                                                    lineTo(0f, size.height)
                                                    close()
                                                }
                                                drawPath(path, color = Color.White)
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(4.dp))

                                        Text(
                                            text = "YouTube",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = if (isRealTV) 20.sp else 18.sp,
                                            letterSpacing = (-0.8).sp
                                        )

                                        // RYD Rating Badge (Return YouTube Dislike)
                                        if (activeDislikes.isNotBlank()) {
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Surface(
                                                color = Color(0xFF272727),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.padding(vertical = 2.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    if (activeLikes.isNotBlank()) {
                                                        Text("👍 $activeLikes ", fontSize = 11.sp, color = Color.LightGray)
                                                    }
                                                    Text("👎 $activeDislikes", fontSize = 11.sp, color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }

                                    // Right Action Icons (My List, Speed, Quality, Black Screen, Search, Voice)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // 1. Add to My List Bookmark Button
                                        if (activeVideoId != null) {
                                            HeaderIconButton(
                                                icon = if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                                contentDescription = if (isFavorite) "In My List" else "Add to My List",
                                                tint = if (isFavorite) Color.Red else Color.White,
                                                isTV = isRealTV,
                                                onClick = {
                                                    activeVideoId?.let { vId ->
                                                        viewModel.toggleYouTubeFavorite(vId, activeVideoTitle)
                                                    }
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                        }

                                        // 2. Playback Speed Button (1.0x -> 1.25x -> 1.5x -> 2.0x)
                                        HeaderChipButton(
                                            text = "${playbackSpeed}x",
                                            isTV = isRealTV,
                                            onClick = {
                                                playbackSpeed = when (playbackSpeed) {
                                                    1.0f -> 1.25f
                                                    1.25f -> 1.5f
                                                    1.5f -> 2.0f
                                                    else -> 1.0f
                                                }
                                                webViewInstance?.evaluateJavascript(
                                                    "(function(){ var v = document.querySelector('video'); if (v) v.playbackRate = $playbackSpeed; })();",
                                                    null
                                                )
                                            }
                                        )

                                        Spacer(modifier = Modifier.width(2.dp))

                                        // 3. Quality Lock Button (Auto -> 1080p -> 720p)
                                        HeaderChipButton(
                                            text = selectedQuality,
                                            isTV = isRealTV,
                                            onClick = {
                                                selectedQuality = when (selectedQuality) {
                                                    "Auto" -> "1080p"
                                                    "1080p" -> "720p"
                                                    else -> "Auto"
                                                }
                                                val qParam = if (selectedQuality == "1080p") "hd1080" else if (selectedQuality == "720p") "hd720" else "default"
                                                webViewInstance?.evaluateJavascript(
                                                    "(function(){ var p = document.getElementById('movie_player') || document.querySelector('.html5-video-player'); if (p && p.setPlaybackQualityRange) p.setPlaybackQualityRange('$qParam', 'highres'); })();",
                                                    null
                                                )
                                            }
                                        )

                                        Spacer(modifier = Modifier.width(2.dp))

                                        // 4. OLED / Black Screen Mode Button
                                        HeaderIconButton(
                                            icon = Icons.Default.Bedtime,
                                            contentDescription = "OLED Black Screen Mode",
                                            isTV = isRealTV,
                                            onClick = { isBlackScreenMode = true }
                                        )

                                        Spacer(modifier = Modifier.width(2.dp))

                                        if (canGoBack) {
                                            HeaderIconButton(
                                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "Back",
                                                isTV = isRealTV,
                                                onClick = { webViewInstance?.goBack() }
                                            )
                                        }

                                        if (canGoForward) {
                                            HeaderIconButton(
                                                icon = Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = "Forward",
                                                isTV = isRealTV,
                                                onClick = { webViewInstance?.goForward() }
                                            )
                                        }

                                        HeaderIconButton(
                                            icon = Icons.Default.Refresh,
                                            contentDescription = "Reload",
                                            isTV = isRealTV,
                                            onClick = { webViewInstance?.reload() }
                                        )

                                        Spacer(modifier = Modifier.width(2.dp))

                                        HeaderIconButton(
                                            icon = Icons.Default.Search,
                                            contentDescription = "Search",
                                            isTV = isRealTV,
                                            onClick = { isSearchActive = true }
                                        )

                                        Spacer(modifier = Modifier.width(2.dp))

                                        VoiceSearchButton(isTV = isRealTV) {
                                            try {
                                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                    putExtra(
                                                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                                    )
                                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Search YouTube...")
                                                }
                                                speechLauncher.launch(intent)
                                            } catch (e: Exception) {
                                                Log.w(TAG, "Speech intent error: ${e.message}")
                                                isSearchActive = true
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ── ORIGINAL YOUTUBE FILTER CHIPS (Pills) ──
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(categories) { cat ->
                                val isSelected = selectedCategoryTitle == cat.title
                                YouTubePillChip(
                                    title = cat.title,
                                    isSelected = isSelected,
                                    isTV = isRealTV,
                                    onClick = {
                                        selectedCategoryTitle = cat.title
                                        currentUrl = cat.url
                                        webViewInstance?.loadUrl(cat.url)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ── WEBVIEW CONTAINER ──────────────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        createYouTubeWebView(
                            context = ctx,
                            isRealTV = isRealTV,
                            jsBridge = jsBridge,
                            onTitleChanged = { pageTitle = it },
                            onLoadingChanged = { isLoading = it },
                            onHistoryChanged = { back, forward ->
                                canGoBack = back
                                canGoForward = forward
                            },
                            onShowCustomView = { v, cb ->
                                customView = v
                                customViewCallback = cb
                            },
                            onHideCustomView = {
                                customView = null
                                customViewCallback = null
                            }
                        ).also { wv ->
                            webViewInstance = wv
                            wv.loadUrl(currentUrl)
                        }
                    },
                    update = { _ ->
                        // Managed internally via webViewInstance
                    }
                )

                // YouTube Red Loading Progress Indicator
                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.TopCenter),
                        color = Color(0xFFFF0000),
                        trackColor = Color.Transparent
                    )
                }

                // SponsorBlock Toast Notification
                if (sponsorToastMessage != null) {
                    Surface(
                        color = Color(0xFF1E1E1E).copy(alpha = 0.95f),
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD600)),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp)
                    ) {
                        Text(
                            text = sponsorToastMessage!!,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = if (isRealTV) 15.sp else 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                // Fullscreen Custom View (YouTube native fullscreen video expanded)
                if (customView != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = {
                                FrameLayout(it).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    customView?.let { cv ->
                                        (cv.parent as? ViewGroup)?.removeView(cv)
                                        addView(cv)
                                    }
                                }
                            }
                        )
                    }
                }

                // ── OLED BLACK SCREEN / AUDIO-ONLY OVERLAY ──────────────────
                if (isBlackScreenMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .clickable { isBlackScreenMode = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Bedtime,
                                contentDescription = null,
                                tint = Color.DarkGray,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Audio-Only Mode (OLED Power Saver)",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tap screen or press remote to wake",
                                color = Color.DarkGray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    isTV: Boolean,
    enabled: Boolean = true,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(if (isTV) 38.dp else 34.dp)
            .clip(CircleShape)
            .background(
                when {
                    isFocused -> Color.White.copy(alpha = 0.2f)
                    else -> Color.Transparent
                }
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color(0xFFFF0000) else Color.Transparent,
                shape = CircleShape
            )
            .focusable(enabled)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else Color.Gray.copy(alpha = 0.4f),
            modifier = Modifier.size(if (isTV) 20.dp else 18.dp)
        )
    }
}

@Composable
private fun HeaderChipButton(
    text: String,
    isTV: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        color = if (isFocused) Color(0xFF383838) else Color(0xFF272727),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color(0xFFFF0000) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = if (isTV) 12.sp else 11.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun VoiceSearchButton(
    isTV: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(if (isTV) 38.dp else 34.dp)
            .clip(CircleShape)
            .background(
                when {
                    isFocused -> Color.White.copy(alpha = 0.25f)
                    else -> Color(0xFF272727)
                }
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color(0xFFFF0000) else Color.Transparent,
                shape = CircleShape
            )
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = "Voice Search",
            tint = if (isFocused) Color(0xFFFF0000) else Color.White,
            modifier = Modifier.size(if (isTV) 20.dp else 18.dp)
        )
    }
}

@Composable
private fun YouTubePillChip(
    title: String,
    isSelected: Boolean,
    isTV: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .scale(if (isFocused) 1.08f else 1.0f)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color(0xFFFF0000) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick),
        color = when {
            isSelected -> Color(0xFFFFFFFF)
            isFocused -> Color(0xFF383838)
            else -> Color(0xFF272727)
        },
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                color = if (isSelected) Color(0xFF0F0F0F) else Color(0xFFF1F1F1),
                fontSize = if (isTV) 14.sp else 13.sp,
                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createYouTubeWebView(
    context: Context,
    isRealTV: Boolean,
    jsBridge: Any,
    onTitleChanged: (String) -> Unit,
    onLoadingChanged: (Boolean) -> Unit,
    onHistoryChanged: (Boolean, Boolean) -> Unit,
    onShowCustomView: (View, WebChromeClient.CustomViewCallback) -> Unit,
    onHideCustomView: () -> Unit
): WebView {
    return WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(0xFF0F0F0F.toInt())

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT

            // TV Spatial Navigation via safe reflection
            if (isRealTV) {
                try {
                    val m = this.javaClass.getMethod(
                        "setSupportSpatialNavigation",
                        Boolean::class.javaPrimitiveType
                    )
                    m.invoke(this, true)
                } catch (_: Exception) {}
            }

            // Clean Chrome Mobile User-Agent without WebView identifiers
            val defaultUa = userAgentString
            userAgentString = if (defaultUa.contains("wv")) {
                defaultUa.replace("; wv", "")
            } else {
                defaultUa
            }
        }

        isFocusable = true
        isFocusableInTouchMode = true

        // Register Duta bridge for metadata, SponsorBlock notifications, and PiP state
        addJavascriptInterface(jsBridge, "DutaYouTubeBridge")

        // TV Remote key events dispatcher (Media controls & Remote number seeking)
        setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when {
                    keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                        val fraction = if (keyCode == KeyEvent.KEYCODE_0) 0.0 else (keyCode - KeyEvent.KEYCODE_0) * 0.1
                        evaluateJavascript(
                            "(function(){ var v = document.querySelector('video'); if (v && v.duration) v.currentTime = v.duration * $fraction; else if (v) v.currentTime = 0; })();",
                            null
                        )
                        return@setOnKeyListener true
                    }
                    keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_HEADSETHOOK -> {
                        evaluateJavascript(
                            "(function(){ var v = document.querySelector('video'); if (v) { if (v.paused) v.play(); else v.pause(); } })();",
                            null
                        )
                        return@setOnKeyListener true
                    }
                    keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        evaluateJavascript(
                            "(function(){ var v = document.querySelector('video'); if (v) v.currentTime += 10; })();",
                            null
                        )
                        return@setOnKeyListener true
                    }
                    keyCode == KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        evaluateJavascript(
                            "(function(){ var v = document.querySelector('video'); if (v) v.currentTime = Math.max(0, v.currentTime - 10); })();",
                            null
                        )
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }

        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                val low = url.lowercase()

                // Block known ad network hosts
                for (adHost in BLOCKED_AD_HOSTS) {
                    if (low.contains(adHost)) {
                        return WebResourceResponse(
                            "text/plain",
                            "UTF-8",
                            ByteArrayInputStream(ByteArray(0))
                        )
                    }
                }

                // Block known ad URL paths
                for (adPath in BLOCKED_AD_PATHS) {
                    if (low.contains(adPath)) {
                        return WebResourceResponse(
                            "text/plain",
                            "UTF-8",
                            ByteArrayInputStream(ByteArray(0))
                        )
                    }
                }

                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                onLoadingChanged(true)
                view?.evaluateJavascript(YOUTUBE_AD_NUKER_SCRIPT, null)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                onLoadingChanged(false)
                onHistoryChanged(view?.canGoBack() == true, view?.canGoForward() == true)
                view?.evaluateJavascript(YOUTUBE_AD_NUKER_SCRIPT, null)
            }

            override fun onLoadResource(view: WebView?, url: String?) {
                super.onLoadResource(view, url)
                view?.evaluateJavascript(YOUTUBE_AD_NUKER_SCRIPT, null)
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                if (!title.isNullOrBlank()) {
                    onTitleChanged(title)
                }
                onHistoryChanged(view?.canGoBack() == true, view?.canGoForward() == true)
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                onLoadingChanged(newProgress < 100)
                if (newProgress > 30) {
                    view?.evaluateJavascript(YOUTUBE_AD_NUKER_SCRIPT, null)
                }
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                super.onShowCustomView(view, callback)
                if (view != null && callback != null) {
                    onShowCustomView(view, callback)
                }
            }

            override fun onHideCustomView() {
                super.onHideCustomView()
                onHideCustomView()
            }
        }
    }
}
