package com.duta.movie.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.duta.movie.R
import com.duta.movie.util.DeviceUtils
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
    "googlesyndication.com",
    "google-analytics.com",
    "www.google-analytics.com",
    "play.google.com/log"
)

private val BLOCKED_AD_PATHS = listOf(
    "/pagead/",
    "/api/stats/ads",
    "/api/stats/atr",
    "/ptracking",
    "/aclk"
)

/**
 * Enhanced JavaScript to block, hide, and auto-skip YouTube ads,
 * while styling web content to seamlessly integrate with our native YouTube chrome.
 */
private const val YOUTUBE_AD_NUKER_SCRIPT = """
(function() {
    try {
        if (window.__dutaYtNukerInitialized) return;
        window.__dutaYtNukerInitialized = true;

        // 1. Inject aggressive CSS to hide ad containers, promo banners, and duplicate web header
        if (!document.getElementById('duta-yt-antiad-style')) {
            var style = document.createElement('style');
            style.id = 'duta-yt-antiad-style';
            style.textContent = `
                /* Hide YouTube Web mobile top header so our native Compose header acts as authentic UI */
                ytm-mobile-topbar-renderer,
                header.mobile-topbar-header,
                .mobile-topbar-header,
                #header-bar {
                    display: none !important;
                }

                /* Block & suppress ads, promos, and nag dialogs */
                .video-ads, .ytp-ad-module, .ytp-ad-overlay-container,
                .ytp-ad-message-container, .ytp-ad-player-overlay,
                .ytp-ad-text, .ytp-ad-preview-container,
                .ytp-ad-action-interstitial, .ytp-ad-image-overlay,
                .ytp-ad-button-icon, .ytp-ad-skip-button-slot,
                ytm-promoted-sparkles-web-renderer,
                ytm-promoted-sparkles-text-search-web-renderer,
                ytm-statement-banner-renderer,
                ytm-mealbar-promo-renderer,
                ytm-upsell-dialog-renderer,
                .upsell-dialog-renderer,
                ytm-open-app-button-renderer,
                .mobile-topbar-header-sign-in-button,
                ytm-pivot-bar-item-renderer:has([aria-label*="Create"]),
                .ad-container, #player-ads, .player-ads {
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

        function skipAndFastForwardAds() {
            try {
                // Auto-click any visible skip buttons
                var skipSelectors = [
                    '.ytp-ad-skip-button',
                    '.ytp-ad-skip-button-modern',
                    '.videoAdUiSkipButton',
                    '.ytp-skip-ad-button',
                    '.ytp-ad-overlay-close-button',
                    '[class*="skip-button"]',
                    'button[aria-label*="Skip"]',
                    'button[id^="skip-button"]'
                ];
                for (var i = 0; i < skipSelectors.length; i++) {
                    var btns = document.querySelectorAll(skipSelectors[i]);
                    for (var j = 0; j < btns.length; j++) {
                        try { btns[j].click(); } catch(e) {}
                    }
                }

                // Close upsell / install app dialogs
                var closeBtns = document.querySelectorAll(
                    'ytm-mealbar-promo-renderer button, ytm-upsell-dialog-renderer button, [aria-label*="Dismiss"], [aria-label*="No thanks"]'
                );
                for (var k = 0; k < closeBtns.length; k++) {
                    try { closeBtns[k].click(); } catch(e) {}
                }

                // Detect video player ad mode and fast-forward at 16x speed
                var player = document.querySelector('.html5-video-player');
                var isAdActive = (player && (player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting'))) ||
                                 document.querySelector('.ad-showing, .ad-interrupting, .ytp-ad-player-overlay');
                var video = document.querySelector('video');

                if (isAdActive && video) {
                    video.muted = true;
                    video.playbackRate = 16.0;
                    if (video.duration && !isNaN(video.duration) && isFinite(video.duration)) {
                        video.currentTime = video.duration;
                    }
                } else if (video && video.playbackRate > 2.0) {
                    video.playbackRate = 1.0;
                    video.muted = false;
                }
            } catch(err) {}
        }

        // Run continuous check on an interval
        setInterval(skipAndFastForwardAds, 300);

        // Run immediate check on DOM changes
        var observer = new MutationObserver(function() {
            skipAndFastForwardAds();
        });
        observer.observe(document.documentElement, {
            childList: true,
            subtree: true,
            attributes: true,
            attributeFilter: ['class']
        });
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
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val isRealTV = remember { DeviceUtils.isTvDevice(context) }
    
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(YOUTUBE_BASE_URL) }
    var pageTitle by remember { mutableStateOf("YouTube") }
    var isLoading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    
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

    // Back handling: Exit fullscreen -> Collapse search -> Go back in webView -> Pop screen
    BackHandler {
        if (customView != null) {
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── AUTHENTIC YOUTUBE HEADER & FILTER BAR ──────────────────────
            if (customView == null) {
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
                                // ── ACTIVE SEARCH BAR (MIMICKING ORIGINAL YOUTUBE) ──
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(if (isRealTV) 48.dp else 42.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Back from search button
                                    HeaderIconButton(
                                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        isTV = isRealTV,
                                        onClick = { isSearchActive = false }
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Search Text Input Pill
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

                                    // Voice Search in Search Bar
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
                                    // YouTube Authentic Logo
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable {
                                            selectedCategoryTitle = "All"
                                            webViewInstance?.loadUrl(YOUTUBE_BASE_URL)
                                        }
                                    ) {
                                        // Official YouTube Play Badge
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
                                    }

                                    // Right Action Icons (History Nav, Voice Search, Search)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
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

                                        Spacer(modifier = Modifier.width(4.dp))

                                        // Search trigger
                                        HeaderIconButton(
                                            icon = Icons.Default.Search,
                                            contentDescription = "Search",
                                            isTV = isRealTV,
                                            onClick = {
                                                isSearchActive = true
                                            }
                                        )

                                        Spacer(modifier = Modifier.width(4.dp))

                                        // Voice Search Button
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
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(if (isTV) 40.dp else 36.dp)
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
            tint = if (enabled) Color.White else Color.Gray.copy(alpha = 0.4f),
            modifier = Modifier.size(if (isTV) 22.dp else 20.dp)
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
            .size(if (isTV) 40.dp else 36.dp)
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

/**
 * Pill chip mimicking YouTube's exact dark mode category pills:
 * - Selected: Pure white background (#FFFFFF) with jet black text (#0F0F0F)
 * - Unselected: Charcoal background (#272727) with light grey text (#F1F1F1)
 * - On TV focus: 1.08x scale + glowing red border
 */
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

            // TV Spatial Navigation via safe reflection (allows remote D-pad to traverse links and video cards)
            if (isRealTV) {
                try {
                    val m = this.javaClass.getMethod(
                        "setSupportSpatialNavigation",
                        Boolean::class.javaPrimitiveType
                    )
                    m.invoke(this, true)
                } catch (_: Exception) {}
            }

            // Set clean Chrome mobile User-Agent for modern responsive UI without WebView branding
            val defaultUa = userAgentString
            userAgentString = if (defaultUa.contains("wv")) {
                defaultUa.replace("; wv", "")
            } else {
                defaultUa
            }
        }

        isFocusable = true
        isFocusableInTouchMode = true

        // TV Remote key events dispatcher
        setOnKeyListener { v, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> {
                        evaluateJavascript(
                            "(function(){ var v = document.querySelector('video'); if (v) { if (v.paused) v.play(); else v.pause(); } })();",
                            null
                        )
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        evaluateJavascript(
                            "(function(){ var v = document.querySelector('video'); if (v) v.currentTime += 10; })();",
                            null
                        )
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_MEDIA_REWIND -> {
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

                // 1. Block known ad network hosts
                for (adHost in BLOCKED_AD_HOSTS) {
                    if (low.contains(adHost)) {
                        return WebResourceResponse(
                            "text/plain",
                            "UTF-8",
                            ByteArrayInputStream(ByteArray(0))
                        )
                    }
                }

                // 2. Block known ad URL paths
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
                // Inject nuker script immediately on page start
                view?.evaluateJavascript(YOUTUBE_AD_NUKER_SCRIPT, null)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                onLoadingChanged(false)
                onHistoryChanged(view?.canGoBack() == true, view?.canGoForward() == true)
                // Inject nuker script on page finish
                view?.evaluateJavascript(YOUTUBE_AD_NUKER_SCRIPT, null)
            }

            override fun onLoadResource(view: WebView?, url: String?) {
                super.onLoadResource(view, url)
                // Continuous injection to guarantee 0ms ad elimination
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
