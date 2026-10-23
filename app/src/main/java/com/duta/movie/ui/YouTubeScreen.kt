package com.duta.movie.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duta.movie.LocalPipMode
import com.duta.movie.util.DeviceUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    "pubads.g.doubleclick.net",
    "www.googleadservices.com",
    "adformat.googleapis.com",
    "ade.googlesyndication.com",
    "ads.youtube.com"
)

private val BLOCKED_AD_PATHS = listOf(
    "/pagead/",
    "/api/stats/ads",
    "/ptracking?",
    "/youtubei/v1/player/ad_break",
    "/player/ad_break"
)

/**
 * Robust YouTube Ad-Nuker, SponsorBlock, and Return YouTube Dislike (RYD) script.
 * Safely removes ad banners, auto-skips video ads, and integrates with Duta bridge
 * without interfering with YouTube's core video decryption or media pipeline.
 */
private const val YOUTUBE_AD_NUKER_SCRIPT_PART1 = """
(function() {
    try {
        // 1. Inject CSS for ad suppression, TV focus styling, and YouTube dark mode
        if (!document.getElementById('duta-yt-style')) {
            var style = document.createElement('style');
            style.id = 'duta-yt-style';
            style.textContent = `
                /* Hide YouTube Web mobile top header & duplicate bottom bar */
                ytm-mobile-topbar-renderer,
                header.mobile-topbar-header,
                .mobile-topbar-header,
                #header-bar,
                ytm-pivot-bar-renderer,
                .pivot-bar,
                ytm-pivot-bar-item-renderer,
                ytm-feed-filter-chip-bar-renderer,
                .feed-filter-chip-bar-renderer,
                ytm-shorts-header-renderer,
                .shorts-header-renderer,
                ytm-open-app-button-renderer,
                .mobile-topbar-header-sign-in-button,
                ytm-mealbar-promo-renderer,
                ytm-upsell-dialog-renderer,
                .upsell-dialog-renderer {
                    display: none !important;
                }

                /* Block & suppress ad banners, sponsored video cards, and promo dialogs */
                .video-ads, .ytp-ad-overlay-container,
                .ytp-ad-message-container,
                .ytp-ad-text, .ytp-ad-preview-container,
                .ytp-ad-action-interstitial, .ytp-ad-image-overlay,
                .ytp-ad-button-icon,
                .ytp-ad-hover-text-container, .ytp-ad-info-dialog-background,
                ytm-promoted-sparkles-web-renderer,
                ytm-promoted-sparkles-text-search-web-renderer,
                ytm-statement-banner-renderer,
                ytm-compact-promoted-video-renderer,
                ytm-promoted-video-renderer,
                ytm-in-feed-ad-layout-renderer,
                ytm-brand-video-singleton-renderer,
                ytm-ad-slot-renderer,
                ad-slot-renderer {
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
                    overflow-x: hidden !important;
                }

                /* Keep video player unaffected by focus outlines or transforms */
                video:focus, #movie_player:focus, .html5-video-player:focus, .html5-main-video:focus {
                    outline: none !important;
                    box-shadow: none !important;
                    transform: none !important;
                }
                .html5-video-player, .html5-video-container {
                    background-color: #0F0F0F !important;
                }

                /* Real YouTube TV 3-4 Column Grid for Feed & Search */
                @media screen and (min-width: 600px) {
                    /* Eliminate duplicate/empty header placeholder */
                    .rich-grid-renderer-header,
                    .rich-grid-sticky-header {
                        display: none !important;
                    }
                    ytm-rich-grid-renderer {
                        display: block !important;
                        padding: 0 !important;
                        margin: 0 !important;
                        width: 100% !important;
                    }
                    ytm-section-list-renderer,
                    .rich-grid-renderer-contents,
                    .section-list-renderer-contents,
                    lazy-list {
                        display: grid !important;
                        grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)) !important;
                        gap: 16px !important;
                        padding: 12px 16px 80px 16px !important;
                        box-sizing: border-box !important;
                        width: 100% !important;
                    }

                    /* Suppress empty/ad container slots in grid so they never leave blank gaps */
                    ytm-rich-item-renderer:has(ad-slot-renderer),
                    ytm-rich-item-renderer:has(ytm-promoted-video-renderer),
                    ytm-rich-item-renderer:has(ytm-promoted-sparkles-web-renderer),
                    ytm-rich-item-renderer:has(ytm-compact-promoted-video-renderer),
                    ytm-rich-item-renderer:empty {
                        display: none !important;
                    }

                    /* Multi-column shelves (Shorts shelf, community posts) span full width */
                    ytm-rich-section-renderer {
                        grid-column: 1 / -1 !important;
                        width: 100% !important;
                        margin: 12px 0 !important;
                    }
                    ytm-reel-shelf-renderer,
                    ytm-shorts-carousel {
                        width: 100% !important;
                        display: block !important;
                    }

                    ytm-video-with-context-renderer,
                    ytm-compact-video-renderer,
                    ytm-rich-item-renderer {
                        display: flex !important;
                        flex-direction: column !important;
                        margin: 0 !important;
                        padding: 8px !important;
                        background-color: #1A1A1A !important;
                        border-radius: 12px !important;
                        transition: transform 0.2s cubic-bezier(0.2, 0, 0, 1), box-shadow 0.2s ease, outline 0.15s ease !important;
                        box-sizing: border-box !important;
                        cursor: pointer !important;
                        position: relative !important;
                    }

                    /* Thumbnail styling */
                    ytm-media-item .media-item-thumbnail-container,
                    .video-thumbnail-container-compact,
                    .video-thumbnail-container-large,
                    .video-thumbnail-container-vertical {
                        border-radius: 10px !important;
                        overflow: hidden !important;
                        width: 100% !important;
                        aspect-ratio: 16 / 9 !important;
                        background-color: #212121 !important;
                    }

                    /* TV D-Pad Focused Highlight */
                    .duta-tv-focused,
                    ytm-video-with-context-renderer.duta-tv-focused,
                    ytm-compact-video-renderer.duta-tv-focused,
                    ytm-rich-item-renderer.duta-tv-focused,
                    ytm-reel-item-renderer.duta-tv-focused,
                    ytm-shorts-carousel-video-renderer.duta-tv-focused {
                        outline: 3px solid #FF0000 !important;
                        outline-offset: 3px !important;
                        box-shadow: 0 0 24px rgba(255, 0, 0, 0.75), 0 8px 20px rgba(0,0,0,0.85) !important;
                        transform: scale(1.05) !important;
                        z-index: 10 !important;
                        background-color: #262626 !important;
                    }

                    /* Real YouTube TV Shorts Experience */
                    ytm-shorts,
                    ytm-shorts-carousel,
                    .shorts-carousel-container {
                        background-color: #0F0F0F !important;
                        height: 100vh !important;
                        width: 100% !important;
                        overflow: hidden !important;
                    }

                    ytm-shorts-player-container,
                    ytm-reel-item-renderer,
                    ytm-shorts-carousel-video-renderer,
                    .shorts-player-container {
                        display: flex !important;
                        align-items: center !important;
                        justify-content: center !important;
                        width: 100% !important;
                        height: 100% !important;
                        background-color: #0F0F0F !important;
                        position: relative !important;
                    }

                    /* Keep entire vertical 9:16 video visible ONLY for Shorts: NEVER CROP TOP OR BOTTOM */
                    ytm-shorts video,
                    ytm-shorts-player-container video,
                    ytm-reel-item-renderer video,
                    ytm-shorts-carousel-video-renderer video,
                    .shorts-player-container video {
                        position: relative !important;
                        left: 0 !important;
                        right: 0 !important;
                        top: 0 !important;
                        bottom: 0 !important;
                        margin: 0 auto !important;
                        max-width: calc(100vh * 9 / 16) !important;
                        width: auto !important;
                        height: 100% !important;
                        max-height: 100vh !important;
                        object-fit: contain !important;
                        border-radius: 14px !important;
                        box-shadow: 0 10px 40px rgba(0, 0, 0, 0.9) !important;
                    }

                    /* Feed & Search: Move mini-player offscreen with zero size so it never blocks cards, without destroying the rendering surface */
                    html.duta-tv-mode-feed #player-container-id,
                    html.duta-tv-mode-feed #player.sticky-player,
                    html.duta-tv-mode-search #player-container-id,
                    html.duta-tv-mode-search #player.sticky-player {
                        position: absolute !important;
                        top: -9999px !important;
                        left: -9999px !important;
                        width: 1px !important;
                        height: 1px !important;
                        opacity: 0 !important;
                        pointer-events: none !important;
                    }

                    /* Authentic 16:9 TV Player for standard Watch videos: ALWAYS VISIBLE, 100% width/height */
                    html.duta-tv-mode-watch #player-container-id,
                    html.duta-tv-mode-watch #player,
                    html.duta-tv-mode-watch .player-container,
                    html.duta-tv-mode-watch .html5-video-player,
                    html.duta-tv-mode-watch .html5-video-container,
                    ytm-watch,
                    ytm-watch #player-container-id,
                    ytm-watch #player,
                    ytm-watch .html5-video-player {
                        display: block !important;
                        visibility: visible !important;
                        opacity: 1 !important;
                        width: 100% !important;
                        height: 100% !important;
                        max-height: 100vh !important;
                        position: relative !important;
                        top: 0 !important;
                        left: 0 !important;
                        z-index: 10 !important;
                        background-color: #000000 !important;
                    }
                    html.duta-tv-mode-watch .html5-video-container,
                    ytm-watch .html5-video-container {
                        width: 100% !important;
                        height: 100% !important;
                    }
                    html.duta-tv-mode-watch video,
                    html.duta-tv-mode-watch .video-stream,
                    html.duta-tv-mode-watch .html5-main-video,
                    ytm-watch video {
                        display: block !important;
                        visibility: visible !important;
                        opacity: 1 !important;
                        position: relative !important;
                        top: 0 !important;
                        left: 0 !important;
                        right: 0 !important;
                        width: 100% !important;
                        height: 100% !important;
                        max-width: 100% !important;
                        max-height: 100% !important;
                        object-fit: contain !important;
                        margin: 0 !important;
                        border-radius: 0 !important;
                        box-shadow: none !important;
                        z-index: 11 !important;
                        background-color: transparent !important;
                    }

                    /* Real YouTube TV Shorts: ALWAYS VISIBLE, centered 9:16 vertical player */
                    html.duta-tv-mode-shorts #player-container-id,
                    html.duta-tv-mode-shorts #player,
                    html.duta-tv-mode-shorts .player-container,
                    html.duta-tv-mode-shorts ytm-shorts,
                    html.duta-tv-mode-shorts ytm-shorts-player-container,
                    html.duta-tv-mode-shorts .shorts-player-container,
                    html.duta-tv-mode-shorts #player-shorts-container {
                        display: flex !important;
                        visibility: visible !important;
                        opacity: 1 !important;
                        align-items: center !important;
                        justify-content: center !important;
                        width: 100% !important;
                        height: 100% !important;
                        background-color: #000000 !important;
                        position: relative !important;
                        top: 0 !important;
                        left: 0 !important;
                        z-index: 10 !important;
                    }
                    html.duta-tv-mode-shorts video,
                    html.duta-tv-mode-shorts .video-stream,
                    html.duta-tv-mode-shorts .html5-main-video,
                    html.duta-tv-mode-shorts .shorts-video-player video {
                        display: block !important;
                        visibility: visible !important;
                        opacity: 1 !important;
                        position: relative !important;
                        top: 0 !important;
                        left: 0 !important;
                        right: 0 !important;
                        bottom: 0 !important;
                        margin: 0 auto !important;
                        max-width: calc(100vh * 9 / 16) !important;
                        width: auto !important;
                        height: 100% !important;
                        max-height: 100vh !important;
                        object-fit: contain !important;
                        border-radius: 12px !important;
                        box-shadow: 0 10px 40px rgba(0, 0, 0, 0.9) !important;
                        z-index: 11 !important;
                        background-color: transparent !important;
                    }

                    /* Subtitles & Captions: Centered over 9:16 video card */
                    #ytp-caption-window-container,
                    .ytp-caption-window-container {
                        position: absolute !important;
                        top: 0 !important;
                        left: 50% !important;
                        transform: translateX(-50%) !important;
                        width: min(calc(100vh * 9 / 16), 100vw) !important;
                        height: 100% !important;
                        pointer-events: none !important;
                    }
                    .caption-window {
                        left: 50% !important;
                        transform: translateX(-50%) !important;
                    }

                    /* Overlay & Metadata: Centered and anchored around the 9:16 video card */
                    yt-reel-player-overlay-view-model {
                        pointer-events: none !important;
                    }
                    .ytReelPlayerOverlayViewModelMetadataContainer {
                        pointer-events: auto !important;
                        max-width: calc(100vh * 9 / 16 + 180px) !important;
                        margin: 0 auto !important;
                        box-sizing: border-box !important;
                    }
                    .ytReelPlayerOverlayViewModelMetadataContainerMetapanel {
                        max-width: calc(100vh * 9 / 16 - 60px) !important;
                        text-shadow: 0 1px 4px rgba(0, 0, 0, 0.85) !important;
                    }

                    /* Channel info and title: anchored at bottom of 9:16 card */
                    .shorts-video-metadata-container,
                    ytm-shorts-video-metadata-renderer,
                    .reel-player-overlay-actions,
                    .ytm-shorts-video-metadata-renderer {
                        position: absolute !important;
                        left: 50% !important;
                        transform: translateX(-50%) !important;
                        width: min(calc(100vh * 9 / 16 - 32px), 520px) !important;
                        bottom: 24px !important;
                        z-index: 25 !important;
                    }

                    /* Action buttons: anchored directly right of the video card */
                    .shorts-player-action-buttons,
                    ytm-shorts-action-button-renderer,
                    .reel-player-action-buttons {
                        position: absolute !important;
                        left: calc(50% + (100vh * 9 / 32) + 20px) !important;
                        right: auto !important;
                        bottom: 48px !important;
                        z-index: 25 !important;
                        display: flex !important;
                        flex-direction: column !important;
                        gap: 16px !important;
                    }
                }
            `;
            (document.head || document.documentElement).appendChild(style);
        }
"""

private const val YOUTUBE_AD_NUKER_SCRIPT_PART2 = """

        // ── SMOOTH POSTER OVERLAY ENGINE (ELIMINATES ALL BLACK SCREEN VOIDS) ──
        function updatePosterOverlay(forceShow, nextVid) {
            try {
                var player = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
                if (!player) return;

                var ov = document.getElementById('duta-poster-overlay');
                if (!ov) {
                    ov = document.createElement('div');
                    ov.id = 'duta-poster-overlay';
                    ov.style.cssText = 'position:absolute;top:0;left:0;width:100%;height:100%;background-color:#0F0F0F;background-position:center;background-size:cover;background-repeat:no-repeat;z-index:15;pointer-events:none;transition:opacity 0.25s ease-out;opacity:0;';

                    var sp = document.createElement('div');
                    sp.style.cssText = 'position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);width:40px;height:40px;border:3px solid rgba(255,255,255,0.25);border-top-color:#FF0000;border-radius:50%;animation:dutaSpin 0.8s linear infinite;';
                    ov.appendChild(sp);

                    if (!document.getElementById('duta-spin-kf')) {
                        var kf = document.createElement('style');
                        kf.id = 'duta-spin-kf';
                        kf.textContent = '@keyframes dutaSpin { to { transform: translate(-50%,-50%) rotate(360deg); } }';
                        (document.head || document.documentElement).appendChild(kf);
                    }
                    player.appendChild(ov);
                }

                var match = location.href.match(/[?&]v=([^&#]+)/);
                var vId = nextVid || (match ? match[1] : null);
                var isWatch = location.href.indexOf('/watch') !== -1;
                var video = document.querySelector('video');

                if (!isWatch && !nextVid) {
                    ov.style.opacity = '0';
                    return;
                }

                if (vId) {
                    var currentBg = ov.getAttribute('data-vid');
                    if (currentBg !== vId) {
                        ov.setAttribute('data-vid', vId);
                        ov.style.backgroundImage = 'url("https://i.ytimg.com/vi/' + vId + '/hqdefault.jpg")';
                        ov.style.opacity = '1';
                    }
                }

                var isPlaying = video && !video.paused && video.currentTime > 0.05 && video.readyState >= 3;
                if (isPlaying && !forceShow) {
                    ov.style.opacity = '0';
                } else if (forceShow || !video || video.readyState < 2) {
                    ov.style.opacity = '1';
                }
            } catch(e) {}
        }

        // Catch clicks on any video link to pre-render poster immediately (0ms latency, zero black screen)
        try {
            if (!window.__dutaPosterClickHooked) {
                window.__dutaPosterClickHooked = true;
                document.addEventListener('click', function(e) {
                    var target = e.target;
                    var a = target ? target.closest('a[href*="/watch?v="]') : null;
                    if (a && a.href) {
                        var m = a.href.match(/[?&]v=([^&#]+)/);
                        if (m) {
                            window.__dutaUserPaused = false;
                            updatePosterOverlay(true, m[1]);
                        }
                    }
                }, true);
            }
        } catch(e) {}

        // Deep clean ad placements, scheduled slots, and ad break heartbeats from YouTube player metadata
        function cleanAdData(data) {
            if (!data || typeof data !== 'object') return data;
            try {
                delete data.adBreakHeartbeatParams;
                delete data.adBreakResponses;
                delete data.adPlacements;
                delete data.playerAds;
                delete data.adSlots;
                delete data.adBreaks;
                delete data.adPlacementConfig;
                delete data.adTagUrl;
                delete data.adParams;
                delete data.adSignalsInfo;
                delete data.adBreakServiceRenderer;
                delete data.instreamAdPlayerOverlayRenderer;
                delete data.adPlaybackContext;
                for (var k in data) {
                    if (data[k] && typeof data[k] === 'object') {
                        cleanAdData(data[k]);
                    }
                }
            } catch(e) {}
            return data;
        }

        // 1. Intercept and clean window.ytInitialPlayerResponse
        try {
            var _ytInitialPlayerResponse = window.ytInitialPlayerResponse;
            if (_ytInitialPlayerResponse) {
                cleanAdData(_ytInitialPlayerResponse);
            }
            if (!window.__dutaYtInitialHooked) {
                window.__dutaYtInitialHooked = true;
                Object.defineProperty(window, 'ytInitialPlayerResponse', {
                    get: function() { return _ytInitialPlayerResponse; },
                    set: function(val) { _ytInitialPlayerResponse = cleanAdData(val); },
                    configurable: true
                });
            }
        } catch(e) {}

        // 2. Intercept JSON.parse for YouTube player responses
        try {
            if (!window.__dutaJsonParseHooked) {
                window.__dutaJsonParseHooked = true;
                var origParse = JSON.parse;
                JSON.parse = function(text, reviver) {
                    var res = origParse.apply(this, arguments);
                    if (res && typeof res === 'object') {
                        if (res.adBreakHeartbeatParams || res.adBreakResponses || res.adPlacements || res.playerAds || res.adSlots) {
                            cleanAdData(res);
                        }
                    }
                    return res;
                };
            }
        } catch(e) {}

        // 3. Intercept fetch for ALL /youtubei/v1/ endpoints (player, get_watch, next, browse) and ad_break
        try {
            if (!window.__dutaFetchHooked && window.fetch) {
                window.__dutaFetchHooked = true;
                var origFetch = window.fetch;
                window.fetch = function(url, options) {
                    var urlStr = (typeof url === 'string') ? url : (url && url.url ? url.url : '');
                    if (urlStr.indexOf('ad_break') !== -1) {
                        return Promise.resolve(new Response(JSON.stringify({}), {
                            status: 200,
                            headers: { 'Content-Type': 'application/json' }
                        }));
                    }
                    var p = origFetch.apply(this, arguments);
                    if (urlStr.indexOf('/youtubei/v1/') !== -1) {
                        return p.then(function(response) {
                            try {
                                var clone = response.clone();
                                return clone.json().then(function(data) {
                                    cleanAdData(data);
                                    return new Response(JSON.stringify(data), {
                                        status: response.status,
                                        statusText: response.statusText,
                                        headers: response.headers
                                    });
                                }).catch(function() {
                                    return response;
                                });
                            } catch(e) {
                                return response;
                            }
                        });
                    }
                    return p;
                };
            }
        } catch(e) {}

        // 4. Intercept XMLHttpRequest for all YouTube InnerTube endpoints
        try {
            if (!window.__dutaXhrHooked && window.XMLHttpRequest) {
                window.__dutaXhrHooked = true;
                var origXhrOpen = XMLHttpRequest.prototype.open;
                var origXhrSend = XMLHttpRequest.prototype.send;
                XMLHttpRequest.prototype.open = function(method, url) {
                    this.__dutaUrl = (typeof url === 'string') ? url : '';
                    return origXhrOpen.apply(this, arguments);
                };
                XMLHttpRequest.prototype.send = function() {
                    var url = this.__dutaUrl || '';
                    if (url.indexOf('ad_break') !== -1) {
                        var mockJson = JSON.stringify({});
                        Object.defineProperty(this, 'responseText', { value: mockJson, configurable: true });
                        Object.defineProperty(this, 'response', { value: mockJson, configurable: true });
                        Object.defineProperty(this, 'status', { value: 200, configurable: true });
                        Object.defineProperty(this, 'readyState', { value: 4, configurable: true });
                        var selfMock = this;
                        setTimeout(function() {
                            if (typeof selfMock.onreadystatechange === 'function') selfMock.onreadystatechange();
                            if (typeof selfMock.onload === 'function') selfMock.onload();
                        }, 0);
                        return;
                    }
                    if (url.indexOf('/youtubei/v1/') !== -1) {
                        var self = this;
                        this.addEventListener('readystatechange', function() {
                            if (self.readyState === 4 && self.status === 200) {
                                try {
                                    if (self.responseType === '' || self.responseType === 'text') {
                                        var parsed = JSON.parse(self.responseText);
                                        cleanAdData(parsed);
                                        var modified = JSON.stringify(parsed);
                                        Object.defineProperty(self, 'responseText', { value: modified, configurable: true });
                                        Object.defineProperty(self, 'response', { value: modified, configurable: true });
                                    } else if (self.responseType === 'json' && self.response) {
                                        cleanAdData(self.response);
                                    }
                                } catch(e) {}
                            }
                        });
                    }
                    return origXhrSend.apply(this, arguments);
                };
            }
        } catch(e) {}

        if (typeof window.__dutaAutoScrollEnabled === 'undefined') {
            window.__dutaAutoScrollEnabled = true;
        }
        window.setDutaAutoScrollEnabled = function(enabled) {
            window.__dutaAutoScrollEnabled = !!enabled;
        };

        function isAutoScrollActive() {
            if (window.DutaYouTubeBridge && typeof window.DutaYouTubeBridge.isAutoScrollEnabled === 'function') {
                try {
                    return window.DutaYouTubeBridge.isAutoScrollEnabled();
                } catch(e) {}
            }
            return !!window.__dutaAutoScrollEnabled;
        }

        // Prevent YouTube from forcing loop = true on HTMLMediaElement when auto-scroll is on
        try {
            var protoLoopDesc = Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, 'loop');
            if (!window.__dutaLoopOverridden && protoLoopDesc) {
                window.__dutaLoopOverridden = true;
                Object.defineProperty(HTMLMediaElement.prototype, 'loop', {
                    get: function() {
                        var isShorts = location.href.indexOf('/shorts') !== -1 || location.href.indexOf('/reel') !== -1;
                        if (isShorts && isAutoScrollActive()) return false;
                        return protoLoopDesc.get ? protoLoopDesc.get.call(this) : false;
                    },
                    set: function(val) {
                        var isShorts = location.href.indexOf('/shorts') !== -1 || location.href.indexOf('/reel') !== -1;
                        if (isShorts && isAutoScrollActive()) {
                            if (protoLoopDesc.set) protoLoopDesc.set.call(this, false);
                            return false;
                        }
                        if (protoLoopDesc.set) protoLoopDesc.set.call(this, val);
                    },
                    configurable: true
                });
            }
        } catch(e) {}

        var currentTrackedVideoId = null;
        var sponsorSegments = [];

        function formatCount(num) {
            if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M';
            if (num >= 1000) return (num / 1000).toFixed(1) + 'K';
            return num.toString();
        }

        function checkVideoMetadata() {
            if (window.DutaYouTubeBridge && typeof window.DutaYouTubeBridge.onUrlChanged === 'function') {
                if (window.__lastReportedUrl !== location.href) {
                    window.__lastReportedUrl = location.href;
                    window.DutaYouTubeBridge.onUrlChanged(location.href);
                }
            }
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

        function advanceToNextShort() {
            var advanced = false;
            // 1. YouTube Mobile Web carousel accessibility button
            var nextBtn = document.querySelector('button.ytShortsCarouselShortsA11yNavButton[aria-label="Next video"], button[aria-label="Next video"], [aria-label*="next video" i]');
            if (nextBtn) {
                try { nextBtn.click(); advanced = true; } catch(e){}
            }

            // 2. Desktop or secondary Shorts buttons
            if (!advanced) {
                var deskBtn = document.querySelector('#navigation-button-down button, ytd-shorts #down-button button, [aria-label*="Next video" i]');
                if (deskBtn) {
                    try { deskBtn.click(); advanced = true; } catch(e){}
                }
            }

            // 3. Dispatch keyboard ArrowDown
            if (!advanced) {
                try {
                    var kDown = new KeyboardEvent('keydown', { key: 'ArrowDown', code: 'ArrowDown', keyCode: 40, which: 40, bubbles: true, cancelable: true });
                    (document.activeElement || window).dispatchEvent(kDown);
                    window.dispatchEvent(kDown);
                    advanced = true;
                } catch(e){}
            }

            // 4. Smooth scroll fallback
            if (!advanced) {
                try {
                    window.scrollBy({ top: window.innerHeight, behavior: 'smooth' });
                    advanced = true;
                } catch(e){}
            }

            return advanced;
        }

        function goToPreviousShort() {
            var backed = false;
            // 1. YouTube Mobile Web carousel accessibility button
            var prevBtn = document.querySelector('button.ytShortsCarouselShortsA11yNavButton[aria-label="Previous video"], button[aria-label="Previous video"], [aria-label*="previous video" i]');
            if (prevBtn) {
                try { prevBtn.click(); backed = true; } catch(e){}
            }

            // 2. Desktop or secondary Shorts buttons
            if (!backed) {
                var deskUpBtn = document.querySelector('#navigation-button-up button, ytd-shorts #up-button button, [aria-label*="Previous video" i]');
                if (deskUpBtn) {
                    try { deskUpBtn.click(); backed = true; } catch(e){}
                }
            }

            // 3. Dispatch keyboard ArrowUp
            if (!backed) {
                try {
                    var kUp = new KeyboardEvent('keydown', { key: 'ArrowUp', code: 'ArrowUp', keyCode: 38, which: 38, bubbles: true, cancelable: true });
                    (document.activeElement || window).dispatchEvent(kUp);
                    window.dispatchEvent(kUp);
                    backed = true;
                } catch(e){}
            }

            // 4. Smooth scroll fallback
            if (!backed) {
                try {
                    window.scrollBy({ top: -window.innerHeight, behavior: 'smooth' });
                    backed = true;
                } catch(e){}
            }

            return backed;
        }

        window.advanceToNextShort = advanceToNextShort;
        window.goToPreviousShort = goToPreviousShort;

        // Checks if the active Short/Reel is an Ad/Sponsored video
        function isShortsAdActive() {
            var activeReel = document.querySelector(
                'ytm-shorts-carousel-video-renderer[is-active], ytm-reel-item-renderer[is-active], ytd-reel-video-renderer[is-active], [is-active]'
            );
            var scope = activeReel || document;

            // 1. Sponsored or Ad badge aria labels inside active reel
            var sponsoredBadges = scope.querySelectorAll(
                '[aria-label="Sponsored" i], [aria-label*="Sponsored" i], ' +
                '[aria-label*="Ad •" i], [aria-label*="Ad:" i], ' +
                '.badge-shape-wiz--ad, ytm-ad-badge, .ytm-ad-badge, ' +
                'ytm-badge-supported-renderer [aria-label*="Sponsored" i], ' +
                '.ytp-ad-badge, .ytp-ad-simple-attributed-string'
            );
            for (var i = 0; i < sponsoredBadges.length; i++) {
                var b = sponsoredBadges[i];
                if (b.offsetParent !== null || b.getClientRects().length > 0) {
                    return true;
                }
            }

            // 2. Badge texts containing "Sponsored", "Ad", or "Promoted" inside active reel
            var badgeTexts = scope.querySelectorAll(
                '.yt-spec-badge-shape__text, ytm-badge-and-byline-renderer span, ' +
                '.shorts-video-metadata-container span, .ytm-reel-item-renderer span, ' +
                '.yt-badge-shape span'
            );
            for (var j = 0; j < badgeTexts.length; j++) {
                var txt = (badgeTexts[j].textContent || '').trim().toLowerCase();
                if (txt === 'sponsored' || txt === 'ad' || txt === 'promoted') {
                    if (badgeTexts[j].offsetParent !== null || badgeTexts[j].getClientRects().length > 0) {
                        return true;
                    }
                }
            }

            // 3. Ad tracking links or interstitial action buttons inside active reel
            var ctaButtons = scope.querySelectorAll(
                '.ytp-ad-action-interstitial, .ytp-ad-action-interstitial-action-button, ' +
                'a[href*="googleadservices.com"], a[href*="doubleclick.net"], ' +
                'button[aria-label*="Visit advertiser" i], button[aria-label*="Open advertiser" i]'
            );
            for (var k = 0; k < ctaButtons.length; k++) {
                var cta = ctaButtons[k];
                if (cta.offsetParent !== null || cta.getClientRects().length > 0) {
                    return true;
                }
            }

            // 4. Video overlays or ad containers ONLY if active reel or player actually has .ad-showing
            if (activeReel) {
                if (activeReel.hasAttribute('is-ad') || activeReel.classList.contains('ad-showing') || activeReel.classList.contains('ad-interrupting')) {
                    return true;
                }
            }

            return false;
        }

        // Checks if a standard video ad is playing
        function isRegularVideoAdActive() {
            var player = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
            if (player && (player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting'))) {
                return true;
            }
            var skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .videoAdUiSkipButton');
            if (skipBtn && (skipBtn.offsetParent !== null || skipBtn.getClientRects().length > 0)) {
                return true;
            }
            return false;
        }

        function hookVideoElement(v) {
            if (!v || v.__dutaHooked) return;
            v.__dutaHooked = true;

            function notifyPlayback(playing) {
                if (window.DutaYouTubeBridge && typeof window.DutaYouTubeBridge.onPlaybackState === 'function') {
                    window.DutaYouTubeBridge.onPlaybackState(playing, v.currentTime || 0, v.duration || 0);
                }
            }

            v.addEventListener('play', function() {
                window.__dutaUserPaused = false;
                notifyPlayback(true);
            });
            v.addEventListener('pause', function() { notifyPlayback(false); });
            v.addEventListener('playing', function() {
                window.__dutaUserPaused = false;
                notifyPlayback(true);
                updatePosterOverlay(false);
            });
            v.addEventListener('ended', function() { notifyPlayback(false); });
            v.addEventListener('loadeddata', function() {
                if (v.readyState >= 3) updatePosterOverlay(false);
            });
            v.addEventListener('emptied', function() {
                updatePosterOverlay(true);
            });
            v.addEventListener('loadstart', function() {
                updatePosterOverlay(true);
            });
            v.addEventListener('waiting', function() {
                if (v.readyState < 2) updatePosterOverlay(true);
            });

            function onShortsPlaybackCheck() {
                var isShorts = location.href.indexOf('/shorts') !== -1 ||
                               location.href.indexOf('/reel') !== -1 ||
                               location.href.indexOf('/reels') !== -1;
                if (!isShorts) return;

                // Normal Auto-scroll
                if (isAutoScrollActive()) {
                    if (v.loop) v.loop = false;
                    var now = Date.now();
                    var isEnded = v.ended || (v.duration > 0 && v.currentTime >= v.duration - 0.35);
                    if (!isEnded && v.duration > 0 && v.__dutaLastTime !== undefined) {
                        if (v.currentTime < v.__dutaLastTime && v.__dutaLastTime > v.duration * 0.8) {
                            isEnded = true;
                        }
                    }
                    v.__dutaLastTime = v.currentTime;

                    if (isEnded && (!window.__lastShortsAdvance || now - window.__lastShortsAdvance > 1500)) {
                        window.__lastShortsAdvance = now;
                        var adv = advanceToNextShort();
                        if (adv && window.DutaYouTubeBridge && typeof window.DutaYouTubeBridge.onShortAutoScrolled === 'function') {
                            window.DutaYouTubeBridge.onShortAutoScrolled(location.href);
                        }
                    }
                }
            }

            v.addEventListener('timeupdate', function() {
                if (v.currentTime > 0.05 && v.readyState >= 3) {
                    updatePosterOverlay(false);
                }
                if (isRegularVideoAdActive()) {
                    processAdsAndPlayback();
                }
                onShortsPlaybackCheck();
            });
            v.addEventListener('loadedmetadata', function() {
                if (isRegularVideoAdActive()) {
                    processAdsAndPlayback();
                }
            });
        }

        var wasAdActive = false;
        var lastTvFocusCheck = 0;

        function ensureTvFocusableElements() {
            var now = Date.now();
            if (now - lastTvFocusCheck < 3000) return;
            lastTvFocusCheck = now;

            var items = document.querySelectorAll(
                'ytm-video-with-context-renderer, ytm-compact-video-renderer, ' +
                'ytm-shorts-carousel-video-renderer, ytm-reel-item-renderer, ' +
                'ytm-chip-cloud-chip-renderer, ytm-channel-renderer'
            );
            for (var i = 0; i < items.length; i++) {
                var el = items[i];
                if (!el.hasAttribute('tabindex')) {
                    el.setAttribute('tabindex', '0');
                }
            }
        }

        function updateDutaTvPageMode() {
            try {
                var path = location.pathname;
                var isWatch = path.indexOf('/watch') !== -1;
                var isShorts = path.indexOf('/shorts') !== -1 || path.indexOf('/reel') !== -1;
                var isSearch = path.indexOf('/results') !== -1;
                var isFeed = !isWatch && !isShorts && !isSearch;

                var root = document.documentElement;
                if (root) {
                    root.classList.toggle('duta-tv-mode-watch', isWatch);
                    root.classList.toggle('duta-tv-mode-shorts', isShorts);
                    root.classList.toggle('duta-tv-mode-search', isSearch);
                    root.classList.toggle('duta-tv-mode-feed', isFeed);
                }

                var pc = document.getElementById('player-container-id');
                var p = document.getElementById('player');
                var v = document.querySelector('video');

                if (isWatch || isShorts) {
                    if (pc) {
                        pc.style.removeProperty('display');
                        pc.style.removeProperty('top');
                        pc.style.removeProperty('left');
                        pc.style.removeProperty('width');
                        pc.style.removeProperty('height');
                        pc.style.removeProperty('opacity');
                        pc.style.removeProperty('pointer-events');
                        pc.style.setProperty('display', 'block', 'important');
                        pc.style.setProperty('visibility', 'visible', 'important');
                        pc.style.setProperty('opacity', '1', 'important');
                    }
                    if (p) {
                        p.style.removeProperty('display');
                        p.style.removeProperty('top');
                        p.style.removeProperty('left');
                        p.style.setProperty('display', 'block', 'important');
                        p.style.setProperty('visibility', 'visible', 'important');
                    }
                    if (v) {
                        v.style.removeProperty('display');
                        v.style.setProperty('display', 'block', 'important');
                        v.style.setProperty('visibility', 'visible', 'important');
                        v.style.setProperty('opacity', '1', 'important');
                        v.style.setProperty('left', '0', 'important');
                        v.style.setProperty('right', '0', 'important');
                        if (v.paused && !isRegularVideoAdActive()) {
                            try { v.play(); } catch(e){}
                        }
                    }
                } else {
                    if (pc && !pc.classList.contains('duta-playing')) {
                        pc.style.removeProperty('display');
                        pc.style.setProperty('position', 'absolute', 'important');
                        pc.style.setProperty('top', '-9999px', 'important');
                        pc.style.setProperty('left', '-9999px', 'important');
                        pc.style.setProperty('width', '1px', 'important');
                        pc.style.setProperty('height', '1px', 'important');
                        pc.style.setProperty('opacity', '0', 'important');
                        pc.style.setProperty('pointer-events', 'none', 'important');
                    }
                    var vFeed = document.querySelector('video');
                    if (vFeed && !vFeed.paused) {
                        try { vFeed.pause(); } catch(e){}
                    }
                }
            } catch(e) {}
        }
        window.updateDutaTvPageMode = updateDutaTvPageMode;

        function processAdsAndPlayback() {
            try {
                updateDutaTvPageMode();
                ensureTvFocusableElements();
                checkVideoMetadata();

                var player = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
                if (player && !player.__dutaPlayerHooked) {
                    player.__dutaPlayerHooked = true;
                    if (typeof player.getPlayerResponse === 'function') {
                        var origGetPR = player.getPlayerResponse;
                        player.getPlayerResponse = function() {
                            var r = origGetPR.apply(this, arguments);
                            return cleanAdData(r);
                        };
                    }
                }
                var video = document.querySelector('video');
                if (video) {
                    hookVideoElement(video);
                }

                var isShortsOrReels = location.href.indexOf('/shorts') !== -1 ||
                                      location.href.indexOf('/reel') !== -1 ||
                                      location.href.indexOf('/reels') !== -1 ||
                                      (document.body && (document.body.classList.contains('page-shorts') || document.body.classList.contains('shorts-carousel')));

                // ── A. SHORT VIDEO AD BYPASS (ALWAYS BYPASSES ADS IN SHORTS TO NEXT REAL VIDEO) ──
                if (isShortsOrReels) {
                    var isShortAd = isShortsAdActive();
                    if (isShortAd) {
                        var sv = video || document.querySelector('video');
                        if (sv) {
                            sv.muted = true;
                            window.__dutaMutedByAd = true;
                        }
                        var now = Date.now();
                        if (!window.__lastShortsAdSkip || now - window.__lastShortsAdSkip > 1000) {
                            window.__lastShortsAdSkip = now;
                            var advanced = advanceToNextShort();
                            if (advanced && window.DutaYouTubeBridge && typeof window.DutaYouTubeBridge.onShortAdBypassed === 'function') {
                                window.DutaYouTubeBridge.onShortAdBypassed();
                            }
                        }
                        return; // Ad short is being bypassed, skip remaining tick work
                    }
                }

                // ── B. REGULAR VIDEO AD SKIPPER ──
                if (!isShortsOrReels) {
                    var adCurrentlyActive = isRegularVideoAdActive();
                    if (adCurrentlyActive) {
                        if (!wasAdActive) {
                            wasAdActive = true;
                            if (window.DutaYouTubeBridge && typeof window.DutaYouTubeBridge.onAdStateChanged === 'function') {
                                window.DutaYouTubeBridge.onAdStateChanged(true);
                            }
                        }

                        // 1. Try native player.skipAd
                        if (player && typeof player.skipAd === 'function') {
                            try { player.skipAd(); } catch(e){}
                        }

                        // 2. Click any visible skip button
                        var skipBtns = document.querySelectorAll(
                            '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .videoAdUiSkipButton, ' +
                            '.ytp-skip-ad-button, .ytp-ad-overlay-close-button, [class*="skip-button"], ' +
                            'button[aria-label*="Skip ad" i], button[aria-label*="Skip" i], button[id^="skip-button"], .ytm-skip-ad-button, ' +
                            '.ytp-ad-skip-button-slot, button.ytp-ad-skip-button-text'
                        );
                        for (var i = 0; i < skipBtns.length; i++) {
                            try { skipBtns[i].click(); } catch(e){}
                        }

                        // 3. Mute and jump ad to end immediately without buffering stall
                        if (video) {
                            if (!video.muted) {
                                video.muted = true;
                                window.__dutaMutedByAd = true;
                            }
                            if (isFinite(video.duration) && video.duration > 0) {
                                try {
                                    video.currentTime = video.duration;
                                } catch(e){}
                            }
                        }

                        // Schedule immediate tight re-check to restore video display in milliseconds
                        setTimeout(processAdsAndPlayback, 50);
                    } else {
                        if (wasAdActive) {
                            wasAdActive = false;
                            if (window.DutaYouTubeBridge && typeof window.DutaYouTubeBridge.onAdStateChanged === 'function') {
                                window.DutaYouTubeBridge.onAdStateChanged(false);
                            }
                        }

                        // Restore sound & visibility for the real video immediately
                        if (video) {
                            video.style.opacity = '1';
                            if (window.__dutaMutedByAd) {
                                video.muted = false;
                                window.__dutaMutedByAd = false;
                            }
                            if (video.playbackRate !== (window.__dutaTargetSpeed || 1.0)) {
                                video.playbackRate = window.__dutaTargetSpeed || 1.0;
                            }
                        }
                    }

                    // Auto-start video when opening watch page if cued and paused
                    if (location.href.indexOf('/watch') !== -1 && window.__lastAutoPlayedUrl !== location.href) {
                        var p = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
                        if (p && typeof p.playVideo === 'function') {
                            var st = typeof p.getPlayerState === 'function' ? p.getPlayerState() : -1;
                            if ((st === -1 || st === 2 || st === 5) && !adCurrentlyActive) {
                                try { p.playVideo(); } catch(e){}
                            }
                        }
                        var bigPlay = document.querySelector('.ytp-large-play-button, button.slim-video-action-bar-button, button[aria-label="Play"]');
                        if (bigPlay && (bigPlay.offsetParent !== null || bigPlay.getClientRects().length > 0)) {
                            try { bigPlay.click(); } catch(e){}
                        }
                        if (video && video.paused && !adCurrentlyActive) {
                            try { video.play(); } catch(e){}
                        }
                        var unmuteBtn = document.querySelector('.ytp-unmute-inner, .ytp-unmute, [aria-label*="unmute" i], [aria-label*="suara" i]');
                        if (unmuteBtn && (unmuteBtn.offsetParent !== null || unmuteBtn.getClientRects().length > 0)) {
                            try { unmuteBtn.click(); } catch(e){}
                        }
                        if (video && video.muted && !window.__dutaMutedByAd) {
                            video.muted = false;
                        }
                        if (video && !video.paused) {
                            window.__lastAutoPlayedUrl = location.href;
                        }
                        updatePosterOverlay(false);
                    }
                }

                // ── C. SPONSORBLOCK (FOR REGULAR VIDEOS) ──
                if (!isShortsOrReels && video && sponsorSegments.length > 0) {
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

                // ── D. DISMISS PROMO / UPSELL DIALOGS ──
                var dismissBtns = document.querySelectorAll(
                    'ytm-mealbar-promo-renderer button, ytm-upsell-dialog-renderer button, [aria-label*="Dismiss" i], [aria-label*="No thanks" i]'
                );
                for (var k = 0; k < dismissBtns.length; k++) {
                    try { dismissBtns[k].click(); } catch(e){}
                }

                // ── E. NORMAL SHORTS AUTO-SCROLL (WHEN VIDEO FINISHES NATURALLY) ──
                if (isShortsOrReels && isAutoScrollActive()) {
                    var sv2 = video || document.querySelector('video');
                    if (sv2) {
                        if (sv2.loop) {
                            sv2.loop = false;
                        }
                        var now2 = Date.now();
                        var isEnded = sv2.ended || (sv2.duration > 0 && sv2.currentTime >= sv2.duration - 0.35);
                        if (!isEnded && sv2.duration > 0 && window.__lastShortsTime !== undefined) {
                            if (sv2.currentTime < window.__lastShortsTime && window.__lastShortsTime > sv2.duration * 0.8) {
                                isEnded = true;
                            }
                        }
                        window.__lastShortsTime = sv2.currentTime;

                        if (isEnded && (!window.__lastShortsAdvance || now2 - window.__lastShortsAdvance > 1500)) {
                            window.__lastShortsAdvance = now2;
                            var advanced2 = advanceToNextShort();
                            if (advanced2 && window.DutaYouTubeBridge && typeof window.DutaYouTubeBridge.onShortAutoScrolled === 'function') {
                                window.DutaYouTubeBridge.onShortAutoScrolled(location.href);
                            }
                        }
                    }
                }

                // ── F. ATTACH DUTA WEB AUDIO DSP ENGINE TO ACTIVE VIDEO ──
                if (window.DutaAudioEngine) {
                    var curVideo = video || document.querySelector('video');
                    if (curVideo) {
                        window.DutaAudioEngine.attachVideo(curVideo);
                    }
                }
            } catch(err) {}
        }

        window.processDutaAds = processAdsAndPlayback;

        // ── 4. DUTA WEB AUDIO DSP ENGINE (SONY MEGA BASS & EQUALIZER PRESETS) ──
        if (!window.DutaAudioEngine) {
            window.DutaAudioEngine = (function() {
                var audioCtx = null;
                var subBass = null;
                var bassShelf = null;
                var midCut = null;
                var presence = null;
                var trebleShelf = null;
                var compressor = null;
                var outputGain = null;
                var activePreset = 'NORMAL';
                var isInitialized = false;

                var PRESET_PARAMS = {
                    'SONY_MEGA_BASS': {
                        subBass: 9.5,
                        bassShelf: 6.0,
                        midCut: -2.5,
                        presence: 1.0,
                        trebleShelf: 4.5,
                        gain: 0.95,
                        compThreshold: -16.0,
                        compRatio: 5.0
                    },
                    'MUSIC_HIFI': {
                        subBass: 4.5,
                        bassShelf: 3.5,
                        midCut: 0.0,
                        presence: 2.5,
                        trebleShelf: 4.0,
                        gain: 1.0,
                        compThreshold: -18.0,
                        compRatio: 3.0
                    },
                    'BASS_BOOST_MAX': {
                        subBass: 12.0,
                        bassShelf: 8.0,
                        midCut: -3.5,
                        presence: 0.0,
                        trebleShelf: 2.0,
                        gain: 0.88,
                        compThreshold: -14.0,
                        compRatio: 8.0
                    },
                    'VOCAL_CLARITY': {
                        subBass: -6.0,
                        bassShelf: -3.0,
                        midCut: 1.0,
                        presence: 6.5,
                        trebleShelf: 3.0,
                        gain: 1.05,
                        compThreshold: -20.0,
                        compRatio: 4.0
                    },
                    'NORMAL': {
                        subBass: 0.0,
                        bassShelf: 0.0,
                        midCut: 0.0,
                        presence: 0.0,
                        trebleShelf: 0.0,
                        gain: 1.0,
                        compThreshold: 0.0,
                        compRatio: 1.0
                    }
                };

                function initContext() {
                    if (isInitialized && audioCtx) return;
                    try {
                        var AudioContextClass = window.AudioContext || window.webkitAudioContext;
                        if (!AudioContextClass) return;
                        audioCtx = new AudioContextClass();

                        subBass = audioCtx.createBiquadFilter();
                        subBass.type = 'peaking';
                        subBass.frequency.value = 60;
                        subBass.Q.value = 1.4;

                        bassShelf = audioCtx.createBiquadFilter();
                        bassShelf.type = 'lowshelf';
                        bassShelf.frequency.value = 120;

                        midCut = audioCtx.createBiquadFilter();
                        midCut.type = 'peaking';
                        midCut.frequency.value = 600;
                        midCut.Q.value = 1.0;

                        presence = audioCtx.createBiquadFilter();
                        presence.type = 'peaking';
                        presence.frequency.value = 3000;
                        presence.Q.value = 1.0;

                        trebleShelf = audioCtx.createBiquadFilter();
                        trebleShelf.type = 'highshelf';
                        trebleShelf.frequency.value = 10000;

                        compressor = audioCtx.createDynamicsCompressor();
                        compressor.attack.value = 0.003;
                        compressor.release.value = 0.25;
                        compressor.knee.value = 12;

                        outputGain = audioCtx.createGain();

                        subBass.connect(bassShelf);
                        bassShelf.connect(midCut);
                        midCut.connect(presence);
                        presence.connect(trebleShelf);
                        trebleShelf.connect(compressor);
                        compressor.connect(outputGain);
                        outputGain.connect(audioCtx.destination);

                        isInitialized = true;
                        applyPreset(activePreset, true);
                    } catch(e) {}
                }

                function smoothSet(param, targetVal, immediate) {
                    if (!param) return;
                    try {
                        if (immediate || !audioCtx || typeof audioCtx.currentTime !== 'number') {
                            param.value = targetVal;
                        } else {
                            param.setTargetAtTime(targetVal, audioCtx.currentTime, 0.05);
                        }
                    } catch(e) {
                        try { param.value = targetVal; } catch(err){}
                    }
                }

                function applyPreset(presetName, immediate) {
                    activePreset = presetName;
                    if (!isInitialized) return;
                    var cfg = PRESET_PARAMS[presetName] || PRESET_PARAMS['NORMAL'];
                    smoothSet(subBass.gain, cfg.subBass, immediate);
                    smoothSet(bassShelf.gain, cfg.bassShelf, immediate);
                    smoothSet(midCut.gain, cfg.midCut, immediate);
                    smoothSet(presence.gain, cfg.presence, immediate);
                    smoothSet(trebleShelf.gain, cfg.trebleShelf, immediate);
                    smoothSet(outputGain.gain, cfg.gain, immediate);
                    smoothSet(compressor.threshold, cfg.compThreshold, immediate);
                    smoothSet(compressor.ratio, cfg.compRatio, immediate);
                }

                function resumeContext() {
                    if (audioCtx && audioCtx.state === 'suspended') {
                        audioCtx.resume().catch(function(){});
                    }
                }

                function attachVideo(v) {
                    if (!v) return;
                    if (activePreset === 'NORMAL' || window.__dutaIsTV) return;
                    initContext();
                    resumeContext();
                    if (v.__dutaAudioHooked) return;
                    v.__dutaAudioHooked = true;

                    try {
                        var source = audioCtx.createMediaElementSource(v);
                        source.connect(subBass);
                    } catch(e) {}

                    v.addEventListener('play', resumeContext);
                    v.addEventListener('playing', resumeContext);
                    v.addEventListener('timeupdate', resumeContext, { once: true });
                }

                ['click', 'touchstart', 'pointerdown', 'keydown'].forEach(function(evt) {
                    document.addEventListener(evt, resumeContext, { passive: true, capture: true });
                });

                return {
                    init: initContext,
                    setPreset: function(name) {
                        activePreset = name;
                        if (activePreset === 'NORMAL' || window.__dutaIsTV) {
                            return;
                        }
                        initContext();
                        resumeContext();
                        applyPreset(name, false);
                        var v = document.querySelector('video');
                        if (v && !v.__dutaAudioHooked) {
                            attachVideo(v);
                        }
                    },
                    getPreset: function() {
                        return activePreset;
                    },
                    attachVideo: attachVideo,
                    resume: resumeContext
                };
            })();
        }

        // Instant observer for player class changes (e.g. ad-showing added/removed)
        try {
            if (!window.__dutaPlayerObserver) {
                var pTarget = document.getElementById('movie_player') || document.querySelector('.html5-video-player') || document.body;
                window.__dutaPlayerObserver = new MutationObserver(function() {
                    processAdsAndPlayback();
                });
                window.__dutaPlayerObserver.observe(pTarget, { attributes: true, attributeFilter: ['class'], subtree: false });
            }
        } catch(e){}

        if (!window.__dutaWatchdogStarted) {
            window.__dutaWatchdogStarted = true;
            setInterval(processAdsAndPlayback, 250);
        }
        processAdsAndPlayback();

        // ── 5. REAL YOUTUBE FOR TV SPATIAL NAVIGATION ENGINE (dutaTvNav) ──
        if (!window.dutaTvNav) {
            window.dutaTvNav = (function() {
                var currentIndex = 0;

                function getItems() {
                    var isWatch = location.href.indexOf('/watch') !== -1;
                    var selector = isWatch
                        ? 'ytm-video-with-context-renderer, ytm-compact-video-renderer, ytm-item-section-renderer ytm-video-with-context-renderer, a.compact-media-item-image'
                        : 'ytm-video-with-context-renderer, ytm-compact-video-renderer, ytm-rich-item-renderer, ytm-reel-item-renderer, ytm-shorts-carousel-video-renderer';
                    
                    var all = Array.prototype.slice.call(document.querySelectorAll(selector));
                    return all.filter(function(el) {
                        var rect = el.getBoundingClientRect();
                        return rect.width > 20 && rect.height > 20 && el.offsetParent !== null;
                    });
                }

                function clearAllFocus() {
                    var prev = document.querySelectorAll('.duta-tv-focused');
                    for (var i = 0; i < prev.length; i++) {
                        prev[i].classList.remove('duta-tv-focused');
                    }
                }

                function focusItem(index) {
                    var items = getItems();
                    if (items.length === 0) return;
                    if (index < 0) index = 0;
                    if (index >= items.length) index = items.length - 1;
                    currentIndex = index;

                    clearAllFocus();
                    var target = items[currentIndex];
                    if (target) {
                        target.classList.add('duta-tv-focused');
                        target.setAttribute('tabindex', '0');
                        try { target.focus({ preventScroll: true }); } catch(e){}
                        target.scrollIntoView({ behavior: 'smooth', block: 'center', inline: 'nearest' });
                    }
                }

                function getColumnsCount() {
                    var items = getItems();
                    if (items.length <= 1) return 1;
                    var firstTop = items[0].getBoundingClientRect().top;
                    var cols = 0;
                    for (var i = 0; i < items.length && i < 10; i++) {
                        var top = items[i].getBoundingClientRect().top;
                        if (Math.abs(top - firstTop) < 30) {
                            cols++;
                        } else {
                            break;
                        }
                    }
                    return Math.max(1, cols);
                }

                function navigate(direction) {
                    var items = getItems();
                    if (items.length === 0) {
                        if (direction === 'UP') {
                            if (window.DutaYouTubeBridge && window.DutaYouTubeBridge.onFocusHeader) {
                                window.DutaYouTubeBridge.onFocusHeader();
                            }
                        } else if (direction === 'LEFT') {
                            if (window.DutaYouTubeBridge && window.DutaYouTubeBridge.onFocusSidebar) {
                                window.DutaYouTubeBridge.onFocusSidebar();
                            }
                        }
                        return;
                    }

                    var cols = getColumnsCount();
                    var step = Math.max(1, cols);

                    switch (direction) {
                        case 'DOWN':
                            if (currentIndex + step < items.length) {
                                focusItem(currentIndex + step);
                            } else if (currentIndex < items.length - 1) {
                                focusItem(currentIndex + 1);
                            } else {
                                window.scrollBy({ top: 400, behavior: 'smooth' });
                                setTimeout(function() {
                                    var newItems = getItems();
                                    if (newItems.length > items.length) {
                                        focusItem(items.length);
                                    }
                                }, 300);
                            }
                            break;

                        case 'UP':
                            if (currentIndex - step >= 0) {
                                focusItem(currentIndex - step);
                            } else if (currentIndex > 0) {
                                focusItem(0);
                            } else {
                                clearAllFocus();
                                window.scrollTo({ top: 0, behavior: 'smooth' });
                                if (window.DutaYouTubeBridge && window.DutaYouTubeBridge.onFocusHeader) {
                                    window.DutaYouTubeBridge.onFocusHeader();
                                }
                            }
                            break;

                        case 'LEFT':
                            if (currentIndex % cols === 0 || currentIndex === 0) {
                                if (window.DutaYouTubeBridge && window.DutaYouTubeBridge.onFocusSidebar) {
                                    window.DutaYouTubeBridge.onFocusSidebar();
                                }
                            } else if (currentIndex > 0) {
                                focusItem(currentIndex - 1);
                            }
                            break;

                        case 'RIGHT':
                            if (currentIndex + 1 < items.length) {
                                focusItem(currentIndex + 1);
                            }
                            break;

                        case 'SELECT':
                            var target = items[currentIndex];
                            if (target) {
                                var link = target.querySelector('a[href*="/watch"], a[href*="/shorts"], a');
                                if (link && link.href) {
                                    try {
                                        var evt = new MouseEvent('click', { bubbles: true, cancelable: true, view: window });
                                        link.dispatchEvent(evt);
                                    } catch(e){}
                                    location.href = link.href;
                                } else if (target.getAttribute('href')) {
                                    location.href = target.getAttribute('href');
                                } else {
                                    try {
                                        var evt2 = new MouseEvent('click', { bubbles: true, cancelable: true, view: window });
                                        target.dispatchEvent(evt2);
                                    } catch(e){}
                                    target.click();
                                }
                            }
                            break;
                    }
                }

                function focusFirst() {
                    currentIndex = 0;
                    focusItem(0);
                }

                return {
                    navigate: navigate,
                    focusFirst: focusFirst,
                    focusItem: focusItem
                };
            })();
        }    } catch(e) {}
})();
"""

private val YOUTUBE_AD_NUKER_SCRIPT: String get() = YOUTUBE_AD_NUKER_SCRIPT_PART1 + "\n" + YOUTUBE_AD_NUKER_SCRIPT_PART2

data class YouTubeCategory(
    val title: String,
    val url: String
)

enum class YouTubeAudioPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
) {
    SONY_MEGA_BASS(
        id = "SONY_MEGA_BASS",
        title = "Sony Mega Bass",
        subtitle = "Walkman dynamic punch & de-muddled sub-bass replica",
        icon = Icons.Default.GraphicEq
    ),
    MUSIC_HIFI(
        id = "MUSIC_HIFI",
        title = "Music Hi-Fi",
        subtitle = "Balanced studio curve with crisp high-end detail",
        icon = Icons.Default.MusicNote
    ),
    BASS_BOOST_MAX(
        id = "BASS_BOOST_MAX",
        title = "Bass Boost (Max)",
        subtitle = "Maximum low-end power with dynamic limiter",
        icon = Icons.Default.Bolt
    ),
    VOCAL_CLARITY(
        id = "VOCAL_CLARITY",
        title = "Vocal Clarity",
        subtitle = "Clear speech & podcasts with low-rumble filter",
        icon = Icons.Default.Mic
    ),
    NORMAL(
        id = "NORMAL",
        title = "Normal (Flat)",
        subtitle = "Unaltered studio passthrough (bypass)",
        icon = Icons.Default.Tune
    );

    companion object {
        fun fromId(id: String): YouTubeAudioPreset {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: NORMAL
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeScreen(
    initialUrl: String? = null,
    viewModel: VideoViewModel,
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    onNavigateToSidebar: () -> Unit = {}
) {
    val context = LocalContext.current
    val isRealTV = remember { DeviceUtils.isTvDevice(context) }
    val isInPip = LocalPipMode.current
    val myList by viewModel.myList.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    val webViewFocusRequester = remember { FocusRequester() }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    val startUrl = remember { initialUrl ?: YOUTUBE_BASE_URL }
    var currentUrl by remember { mutableStateOf(startUrl) }
    var pageTitle by remember { mutableStateOf("YouTube") }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    // Active video metadata from Bridge
    var activeVideoId by remember { mutableStateOf<String?>(null) }
    var activeVideoTitle by remember { mutableStateOf("") }
    var activeLikes by remember { mutableStateOf("") }
    var activeDislikes by remember { mutableStateOf("") }
    var isVideoPlaying by remember { mutableStateOf(false) }

    // User Action Toast State (for explicit button clicks only)
    var toastMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(1500)
            toastMessage = null
        }
    }

    // Playback Speed State (1x -> 1.25x -> 1.5x -> 2x)
    var playbackSpeed by remember { mutableStateOf(1.0f) }

    // Quality Lock State (Auto, 1080p, 720p)
    var selectedQuality by remember { mutableStateOf(if (isRealTV) "1080p" else "Auto") }

    // Black Screen Mode (OLED Audio-Only Power Saver)
    var isBlackScreenMode by remember { mutableStateOf(false) }
    var isMoreMenuExpanded by remember { mutableStateOf(false) }

    // Custom full screen video view (for YouTube's native fullscreen)
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var isManualFullScreenLandscape by remember { mutableStateOf(false) }
    val isFullScreenActive = customView != null || isManualFullScreenLandscape
    val isMobileLandscapeEnabled by viewModel.isMobileLandscapeEnabled.collectAsStateWithLifecycle()
    val isShortsAutoScrollEnabled by viewModel.isShortsAutoScrollEnabled.collectAsStateWithLifecycle()
    val youtubeAudioPreset by viewModel.youtubeAudioPreset.collectAsStateWithLifecycle()
    var showAudioPresetDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isShortsAutoScrollEnabled, webViewInstance) {
        webViewInstance?.evaluateJavascript(
            "if (window.setDutaAutoScrollEnabled) window.setDutaAutoScrollEnabled($isShortsAutoScrollEnabled);",
            null
        )
    }

    LaunchedEffect(youtubeAudioPreset, webViewInstance) {
        webViewInstance?.evaluateJavascript(
            "if (window.DutaAudioEngine) window.DutaAudioEngine.setPreset('$youtubeAudioPreset');",
            null
        )
    }

    LaunchedEffect(isFullScreenActive) {
        viewModel.setYouTubeFullScreen(isFullScreenActive)
        if (!isRealTV) {
            val act = context.findActivity() ?: return@LaunchedEffect
            val window = act.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)

            if (isFullScreenActive) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                act.requestedOrientation = if (isMobileLandscapeEnabled) {
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Original YouTube Search State & Controllers
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryTitle by remember { mutableStateOf("All") }
    val searchFocusRequester = remember { FocusRequester() }
    val initialChipFocusRequester = remember { FocusRequester() }
    val searchButtonFocusRequester = remember { FocusRequester() }
    val voiceButtonFocusRequester = remember { FocusRequester() }
    val searchBackFocusRequester = remember { FocusRequester() }
    val searchSubmitFocusRequester = remember { FocusRequester() }
    val initialSuggestionFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(isRealTV) {
        if (isRealTV) {
            delay(300)
            try { initialChipFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    val executeSearch: (String) -> Unit = { query ->
        val clean = query.trim()
        if (clean.isNotBlank()) {
            searchQuery = clean
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val searchUrl = "https://m.youtube.com/results?search_query=$encoded"
            selectedCategoryTitle = ""
            currentUrl = searchUrl
            webViewInstance?.loadUrl(searchUrl)
            keyboardController?.hide()
            focusManager.clearFocus()
            isSearchActive = false
            if (isRealTV) {
                webViewInstance?.requestFocus()
                coroutineScope.launch {
                    delay(700)
                    webViewInstance?.evaluateJavascript("if (window.dutaTvNav) window.dutaTvNav.focusFirst();", null)
                    webViewFocusRequester.requestFocus()
                }
            }
        }
    }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            delay(120)
            try {
                searchFocusRequester.requestFocus()
                keyboardController?.show()
            } catch (_: Exception) {}
        } else {
            keyboardController?.hide()
            focusManager.clearFocus()
        }
    }

    // Quick Search Suggestions
    val searchSuggestions = remember {
        listOf(
            "Trending",
            "Movie Trailers",
            "Music",
            "Gaming",
            "Live Now",
            "News",
            "Podcasts",
            "Lofi Beats",
            "Animation"
        )
    }

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

    // Voice search launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                executeSearch(spoken)
            }
        }
    }

    // Bridge for metadata & SponsorBlock
    val jsBridge = remember {
        object {
            @JavascriptInterface
            fun isAutoScrollEnabled(): Boolean {
                return viewModel.isShortsAutoScrollEnabled.value
            }

            @JavascriptInterface
            fun onUrlChanged(url: String) {
                currentUrl = url
            }

            @JavascriptInterface
            fun onShortAutoScrolled(url: String) {
                currentUrl = url
            }

            @JavascriptInterface
            fun onShortAdBypassed() {
                // Silently bypass ad in background
            }

            @JavascriptInterface
            fun onAdStateChanged(isAdPlaying: Boolean) {
                // Silently skip ad in background
            }

            @JavascriptInterface
            fun onVideoDetected(videoId: String, title: String, likes: String, dislikes: String) {
                activeVideoId = videoId
                activeVideoTitle = title
                activeLikes = likes
                activeDislikes = dislikes
            }

            @JavascriptInterface
            fun onPlaybackState(isPlaying: Boolean, currentTime: Double, duration: Double) {
                if (isVideoPlaying != isPlaying) {
                    isVideoPlaying = isPlaying
                    viewModel.setYouTubePlaying(isPlaying)
                }
            }

            @JavascriptInterface
            fun onSponsorSkipped(category: String, seconds: Int) {
                // Silently skip sponsor in background
            }

            @JavascriptInterface
            fun onFocusHeader() {
                Handler(Looper.getMainLooper()).post {
                    try {
                        initialChipFocusRequester.requestFocus()
                    } catch (_: Exception) {}
                }
            }

            @JavascriptInterface
            fun onFocusSidebar() {
                Handler(Looper.getMainLooper()).post {
                    try {
                        onNavigateToSidebar()
                    } catch (_: Exception) {}
                }
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    webViewInstance?.onResume()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    webViewInstance?.onPause()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setYouTubePlaying(false)
            viewModel.setYouTubeFullScreen(false)
            if (!isRealTV) {
                val act = context.findActivity()
                if (act != null) {
                    val controller = WindowCompat.getInsetsController(act.window, act.window.decorView)
                    controller.show(WindowInsetsCompat.Type.systemBars())
                    act.requestedOrientation = if (isMobileLandscapeEnabled) {
                        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                }
            }
            if (customView != null) {
                try {
                    customViewCallback?.onCustomViewHidden()
                } catch (_: Exception) {}
                customView = null
                customViewCallback = null
            }
            isManualFullScreenLandscape = false
            isBlackScreenMode = false
        }
    }

    // Back handling: Exit Black Screen -> Exit fullscreen -> Collapse search -> Go back in webView -> Pop screen
    BackHandler {
        if (showAudioPresetDialog) {
            showAudioPresetDialog = false
        } else if (isMoreMenuExpanded) {
            isMoreMenuExpanded = false
        } else if (isBlackScreenMode) {
            isBlackScreenMode = false
        } else if (customView != null) {
            try { customViewCallback?.onCustomViewHidden() } catch (_: Exception) {}
            customView = null
            customViewCallback = null
            isManualFullScreenLandscape = false
            webViewInstance?.evaluateJavascript(
                "(function(){ if (document.exitFullscreen) document.exitFullscreen(); else if (document.webkitExitFullscreen) document.webkitExitFullscreen(); })();",
                null
            )
        } else if (isManualFullScreenLandscape) {
            isManualFullScreenLandscape = false
            webViewInstance?.evaluateJavascript(
                "(function(){ if (document.exitFullscreen) document.exitFullscreen(); else if (document.webkitExitFullscreen) document.webkitExitFullscreen(); })();",
                null
            )
        } else if (isSearchActive) {
            isSearchActive = false
            keyboardController?.hide()
            focusManager.clearFocus()
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
            // ── AUTHENTIC YOUTUBE HEADER & FILTER BAR (Hidden in PiP & Fullscreen Landscape, and in TV Shorts & Watch) ──
            val isShorts = remember(currentUrl) { currentUrl.contains("/shorts") || currentUrl.contains("/reel") }
            val isWatch = remember(currentUrl) { currentUrl.contains("/watch") }
            val hideHeaderOnTV = isRealTV && (isShorts || isWatch)
            LaunchedEffect(isShorts, isWatch) {
                if (hideHeaderOnTV) {
                    kotlinx.coroutines.delay(200)
                    webViewInstance?.requestFocus()
                    try {
                        webViewFocusRequester.requestFocus()
                    } catch (_: Exception) {}
                }
            }
            if (customView == null && !isInPip && !isBlackScreenMode && !isManualFullScreenLandscape && !hideHeaderOnTV) {
                Surface(
                    color = Color(0xFF0F0F0F),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (!isRealTV) Modifier.statusBarsPadding() else Modifier)
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
                                        .height(if (isRealTV) 50.dp else 44.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    HeaderIconButton(
                                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        isTV = isRealTV,
                                        modifier = Modifier
                                            .focusRequester(searchBackFocusRequester)
                                            .focusProperties {
                                                right = searchFocusRequester
                                                down = initialSuggestionFocusRequester
                                            }
                                            .onKeyEvent { keyEvent ->
                                                if (keyEvent.type == KeyEventType.KeyDown) {
                                                    if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                                                        initialSuggestionFocusRequester.requestFocus()
                                                        return@onKeyEvent true
                                                    } else if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                                                        searchFocusRequester.requestFocus()
                                                        return@onKeyEvent true
                                                    }
                                                }
                                                false
                                            },
                                        onClick = {
                                            isSearchActive = false
                                            keyboardController?.hide()
                                            focusManager.clearFocus()
                                            if (isRealTV) {
                                                try { searchButtonFocusRequester.requestFocus() } catch (_: Exception) {}
                                            }
                                        }
                                    )

                                    Spacer(modifier = Modifier.width(6.dp))

                                    var isSearchInputFocused by remember { mutableStateOf(false) }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(24.dp))
                                            .background(Color(0xFF272727))
                                            .border(
                                                width = if (isSearchInputFocused) 2.dp else 1.dp,
                                                color = if (isSearchInputFocused) Color(0xFFFF0000) else Color(0xFF3F3F3F),
                                                shape = RoundedCornerShape(24.dp)
                                            )
                                            .padding(horizontal = 12.dp),
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
                                                    .focusRequester(searchFocusRequester)
                                                    .onFocusChanged { isSearchInputFocused = it.isFocused }
                                                    .focusProperties {
                                                        left = searchBackFocusRequester
                                                        right = searchSubmitFocusRequester
                                                        down = initialSuggestionFocusRequester
                                                    }
                                                    .onKeyEvent { keyEvent ->
                                                        if (keyEvent.type == KeyEventType.KeyDown) {
                                                            when (keyEvent.nativeKeyEvent.keyCode) {
                                                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                                    initialSuggestionFocusRequester.requestFocus()
                                                                    return@onKeyEvent true
                                                                }
                                                                KeyEvent.KEYCODE_DPAD_LEFT -> {
                                                                    if (searchQuery.isEmpty()) {
                                                                        searchBackFocusRequester.requestFocus()
                                                                        return@onKeyEvent true
                                                                    }
                                                                }
                                                                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                                                    searchSubmitFocusRequester.requestFocus()
                                                                    return@onKeyEvent true
                                                                }
                                                            }
                                                        }
                                                        false
                                                    },
                                                singleLine = true,
                                                textStyle = TextStyle(
                                                    color = Color.White,
                                                    fontSize = if (isRealTV) 16.sp else 15.sp
                                                ),
                                                cursorBrush = SolidColor(Color.Red),
                                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                                keyboardActions = KeyboardActions(
                                                    onSearch = { executeSearch(searchQuery) }
                                                ),
                                                decorationBox = { innerTextField ->
                                                    if (searchQuery.isEmpty()) {
                                                        Text(
                                                            text = "Search YouTube...",
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
                                                        .size(24.dp)
                                                        .padding(2.dp)
                                                        .clickable { searchQuery = "" }
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    // Dedicated Search Submit Button
                                    HeaderIconButton(
                                        icon = Icons.Default.Search,
                                        contentDescription = "Search",
                                        isTV = isRealTV,
                                        modifier = Modifier
                                            .focusRequester(searchSubmitFocusRequester)
                                            .focusProperties {
                                                left = searchFocusRequester
                                                right = voiceButtonFocusRequester
                                                down = initialSuggestionFocusRequester
                                            }
                                            .onKeyEvent { keyEvent ->
                                                if (keyEvent.type == KeyEventType.KeyDown) {
                                                    if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                                                        searchFocusRequester.requestFocus()
                                                        return@onKeyEvent true
                                                    } else if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                                                        initialSuggestionFocusRequester.requestFocus()
                                                        return@onKeyEvent true
                                                    }
                                                }
                                                false
                                            },
                                        tint = if (searchQuery.isNotBlank()) Color(0xFFFF0000) else Color.White,
                                        onClick = { executeSearch(searchQuery) }
                                    )

                                    Spacer(modifier = Modifier.width(4.dp))

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
                                // ── STANDARD YOUTUBE BRANDED HEADER (Clean like Original YouTube) ──
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (canGoBack) {
                                            HeaderIconButton(
                                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "Back",
                                                isTV = isRealTV,
                                                onClick = { webViewInstance?.goBack() }
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }

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
                                        }
                                    }

                                    // Right Action Icons (Clean Search, Voice, and More Options Menu)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        HeaderIconButton(
                                            icon = Icons.Default.Search,
                                            contentDescription = "Search",
                                            isTV = isRealTV,
                                            modifier = Modifier
                                                .focusRequester(searchButtonFocusRequester)
                                                .focusProperties {
                                                    down = initialChipFocusRequester
                                                    right = voiceButtonFocusRequester
                                                }
                                                .onKeyEvent { keyEvent ->
                                                    if (keyEvent.type == KeyEventType.KeyDown) {
                                                        if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                                                            initialChipFocusRequester.requestFocus()
                                                            return@onKeyEvent true
                                                        } else if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                                                            onNavigateToSidebar()
                                                            return@onKeyEvent true
                                                        }
                                                    }
                                                    false
                                                },
                                            onClick = { isSearchActive = true }
                                        )

                                        Spacer(modifier = Modifier.width(2.dp))

                                        VoiceSearchButton(
                                            isTV = isRealTV,
                                            modifier = Modifier
                                                .focusRequester(voiceButtonFocusRequester)
                                                .focusProperties {
                                                    left = searchButtonFocusRequester
                                                    down = initialChipFocusRequester
                                                }
                                                .onKeyEvent { keyEvent ->
                                                    if (keyEvent.type == KeyEventType.KeyDown) {
                                                        if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                                                            searchButtonFocusRequester.requestFocus()
                                                            return@onKeyEvent true
                                                        } else if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                                                            initialChipFocusRequester.requestFocus()
                                                            return@onKeyEvent true
                                                        }
                                                    }
                                                    false
                                                }
                                        ) {
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

                                        Spacer(modifier = Modifier.width(2.dp))

                                        HeaderIconButton(
                                            icon = Icons.Default.GraphicEq,
                                            contentDescription = "Audio Equalizer",
                                            isTV = isRealTV,
                                            tint = if (youtubeAudioPreset != "NORMAL") Color(0xFFFF5252) else Color.White,
                                            onClick = { showAudioPresetDialog = true }
                                        )

                                        Spacer(modifier = Modifier.width(2.dp))

                                        Box {
                                            HeaderIconButton(
                                                icon = Icons.Default.MoreVert,
                                                contentDescription = "More Options",
                                                isTV = isRealTV,
                                                onClick = { isMoreMenuExpanded = true }
                                            )

                                            DropdownMenu(
                                                expanded = isMoreMenuExpanded,
                                                onDismissRequest = { isMoreMenuExpanded = false },
                                                modifier = Modifier
                                                    .background(Color(0xFF212121))
                                                    .border(1.dp, Color(0xFF383838), RoundedCornerShape(12.dp))
                                            ) {
                                                DropdownMenuItem(
                                                    text = {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text("Audio Equalizer", color = Color.White, fontSize = 14.sp)
                                                            Spacer(modifier = Modifier.width(16.dp))
                                                            Text(
                                                                YouTubeAudioPreset.fromId(youtubeAudioPreset).title,
                                                                color = Color(0xFFFF5252),
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 12.sp
                                                            )
                                                        }
                                                    },
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = Icons.Default.GraphicEq,
                                                            contentDescription = null,
                                                            tint = if (youtubeAudioPreset != "NORMAL") Color(0xFFFF5252) else Color.White
                                                        )
                                                    },
                                                    onClick = {
                                                        isMoreMenuExpanded = false
                                                        showAudioPresetDialog = true
                                                    }
                                                )

                                                DropdownMenuItem(
                                                    text = {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text("Auto-Scroll Shorts", color = Color.White, fontSize = 14.sp)
                                                            Spacer(modifier = Modifier.width(16.dp))
                                                            Text(
                                                                if (isShortsAutoScrollEnabled) "ON" else "OFF",
                                                                color = if (isShortsAutoScrollEnabled) Color(0xFFFF5252) else Color.Gray,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 12.sp
                                                            )
                                                        }
                                                    },
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = Icons.Default.Autorenew,
                                                            contentDescription = null,
                                                            tint = if (isShortsAutoScrollEnabled) Color.Red else Color.White
                                                        )
                                                    },
                                                    onClick = {
                                                        viewModel.toggleShortsAutoScroll()
                                                        toastMessage = if (!isShortsAutoScrollEnabled) "⚡ Auto-Scroll ON" else "Auto-Scroll OFF"
                                                        isMoreMenuExpanded = false
                                                    }
                                                )

                                                DropdownMenuItem(
                                                    text = {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text("Playback Speed", color = Color.White, fontSize = 14.sp)
                                                            Spacer(modifier = Modifier.width(16.dp))
                                                            Text("${playbackSpeed}x", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                        }
                                                    },
                                                    leadingIcon = {
                                                        Icon(Icons.Default.Speed, contentDescription = null, tint = Color.White)
                                                    },
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
                                                        toastMessage = "Speed: ${playbackSpeed}x"
                                                        isMoreMenuExpanded = false
                                                    }
                                                )

                                                DropdownMenuItem(
                                                    text = {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text("Quality", color = Color.White, fontSize = 14.sp)
                                                            Spacer(modifier = Modifier.width(16.dp))
                                                            Text(selectedQuality, color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                        }
                                                    },
                                                    leadingIcon = {
                                                        Icon(Icons.Default.HighQuality, contentDescription = null, tint = Color.White)
                                                    },
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
                                                        toastMessage = "Quality: $selectedQuality"
                                                        isMoreMenuExpanded = false
                                                    }
                                                )

                                                DropdownMenuItem(
                                                    text = { Text("OLED Power Saver", color = Color.White, fontSize = 14.sp) },
                                                    leadingIcon = {
                                                        Icon(Icons.Default.Bedtime, contentDescription = null, tint = Color.White)
                                                    },
                                                    onClick = {
                                                        isBlackScreenMode = true
                                                        isMoreMenuExpanded = false
                                                    }
                                                )

                                                if (activeVideoId != null) {
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                if (isFavorite) "Remove from My List" else "Save to My List",
                                                                color = Color.White,
                                                                fontSize = 14.sp
                                                            )
                                                        },
                                                        leadingIcon = {
                                                            Icon(
                                                                if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                                                contentDescription = null,
                                                                tint = if (isFavorite) Color.Red else Color.White
                                                            )
                                                        },
                                                        onClick = {
                                                            activeVideoId?.let { vId ->
                                                                viewModel.toggleYouTubeFavorite(vId, activeVideoTitle)
                                                            }
                                                            isMoreMenuExpanded = false
                                                        }
                                                    )
                                                }

                                                DropdownMenuItem(
                                                    text = { Text("Reload Page", color = Color.White, fontSize = 14.sp) },
                                                    leadingIcon = {
                                                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                                                    },
                                                    onClick = {
                                                        webViewInstance?.reload()
                                                        isMoreMenuExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ── CHIPS: Categories or Quick Search Suggestions ──
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isSearchActive) {
                                itemsIndexed(searchSuggestions) { index, term ->
                                    YouTubePillChip(
                                        title = "🔍 $term",
                                        isSelected = false,
                                        isTV = isRealTV,
                                        modifier = (if (index == 0) Modifier.focusRequester(initialSuggestionFocusRequester) else Modifier)
                                            .focusProperties {
                                                up = searchFocusRequester
                                                down = webViewFocusRequester
                                            }
                                            .onKeyEvent { keyEvent ->
                                                if (keyEvent.type == KeyEventType.KeyDown) {
                                                    if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                                                        searchFocusRequester.requestFocus()
                                                        return@onKeyEvent true
                                                    } else if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                                                        webViewInstance?.requestFocus()
                                                        webViewInstance?.evaluateJavascript("if (window.dutaTvNav) window.dutaTvNav.focusFirst();", null)
                                                        webViewFocusRequester.requestFocus()
                                                        return@onKeyEvent true
                                                    } else if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && index == 0 && isRealTV) {
                                                        searchBackFocusRequester.requestFocus()
                                                        return@onKeyEvent true
                                                    }
                                                }
                                                false
                                            },
                                        onClick = {
                                            executeSearch(term)
                                        }
                                    )
                                }
                            } else {
                                itemsIndexed(categories) { index, cat ->
                                    val isSelected = selectedCategoryTitle == cat.title
                                    YouTubePillChip(
                                        title = cat.title,
                                        isSelected = isSelected,
                                        isTV = isRealTV,
                                        modifier = (if (index == 0) Modifier.focusRequester(initialChipFocusRequester) else Modifier)
                                            .focusProperties {
                                                up = searchButtonFocusRequester
                                                down = webViewFocusRequester
                                            }
                                            .onKeyEvent { keyEvent ->
                                                if (keyEvent.type == KeyEventType.KeyDown) {
                                                    if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                                                        searchButtonFocusRequester.requestFocus()
                                                        return@onKeyEvent true
                                                    } else if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                                                        webViewInstance?.requestFocus()
                                                        webViewInstance?.evaluateJavascript("if (window.dutaTvNav) window.dutaTvNav.focusFirst();", null)
                                                        webViewFocusRequester.requestFocus()
                                                        return@onKeyEvent true
                                                    } else if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && index == 0 && isRealTV) {
                                                        onNavigateToSidebar()
                                                        return@onKeyEvent true
                                                    }
                                                }
                                                false
                                            },
                                        onClick = {
                                            selectedCategoryTitle = cat.title
                                            currentUrl = cat.url
                                            webViewInstance?.loadUrl(cat.url)
                                            if (cat.title == "Shorts" && isRealTV) {
                                                webViewInstance?.requestFocus()
                                                try {
                                                    webViewFocusRequester.requestFocus()
                                                } catch (_: Exception) {}
                                            }
                                        }
                                    )
                                }
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
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(webViewFocusRequester)
                        .focusable(),
                    factory = { ctx ->
                        createYouTubeWebView(
                            context = ctx,
                            isRealTV = isRealTV,
                            jsBridge = jsBridge,
                            onNavigateToSidebar = onNavigateToSidebar,
                            isAutoScrollSupplier = { viewModel.isShortsAutoScrollEnabled.value },
                            audioPresetSupplier = { viewModel.youtubeAudioPreset.value },
                            onTitleChanged = { pageTitle = it },
                            onLoadingChanged = { isLoading = it },
                            onHistoryChanged = { back, forward ->
                                canGoBack = back
                                canGoForward = forward
                            },
                            onUrlChanged = { currentUrl = it },
                            onShowCustomView = { v, cb ->
                                customView = v
                                customViewCallback = cb
                            },
                            onHideCustomView = {
                                customView = null
                                customViewCallback = null
                            },
                            onOpenSearch = { isSearchActive = true }
                        ).also { wv ->
                            webViewInstance = wv
                            wv.loadUrl(currentUrl)
                        }
                    },
                    update = { wv ->
                        webViewInstance = wv
                    },
                    onRelease = { wv ->
                        wv.onPause()
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

                // User Action Toast Notification
                if (toastMessage != null) {
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
                            text = toastMessage!!,
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
                            factory = { ctx ->
                                FrameLayout(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    isFocusable = true
                                    isFocusableInTouchMode = true
                                    setOnKeyListener { _, keyCode, event ->
                                        if (event.action == KeyEvent.ACTION_DOWN) {
                                            when (keyCode) {
                                                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
                                                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> {
                                                    webViewInstance?.evaluateJavascript(
                                                        "(function(){ var v = document.querySelector('video'); if (v) { if (v.paused) v.play(); else v.pause(); } })();",
                                                        null
                                                    )
                                                    true
                                                }
                                                KeyEvent.KEYCODE_MEDIA_PLAY -> {
                                                    webViewInstance?.evaluateJavascript(
                                                        "(function(){ var v = document.querySelector('video'); if (v && v.paused) v.play(); })();",
                                                        null
                                                    )
                                                    true
                                                }
                                                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                                    webViewInstance?.evaluateJavascript(
                                                        "(function(){ var v = document.querySelector('video'); if (v && !v.paused) v.pause(); })();",
                                                        null
                                                    )
                                                    true
                                                }
                                                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                                                    webViewInstance?.evaluateJavascript(
                                                        "(function(){ var v = document.querySelector('video'); if (v) v.currentTime += 10; })();",
                                                        null
                                                    )
                                                    true
                                                }
                                                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                                                    webViewInstance?.evaluateJavascript(
                                                        "(function(){ var v = document.querySelector('video'); if (v) v.currentTime = Math.max(0, v.currentTime - 10); })();",
                                                        null
                                                    )
                                                    true
                                                }
                                                in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                                                    val fraction = if (keyCode == KeyEvent.KEYCODE_0) 0.0 else (keyCode - KeyEvent.KEYCODE_0) * 0.1
                                                    webViewInstance?.evaluateJavascript(
                                                        "(function(){ var v = document.querySelector('video'); if (v && v.duration) v.currentTime = v.duration * $fraction; else if (v) v.currentTime = 0; })();",
                                                        null
                                                    )
                                                    true
                                                }
                                                else -> false
                                            }
                                        } else false
                                    }
                                }
                            },
                            update = { fl ->
                                customView?.let { cv ->
                                    if (fl.indexOfChild(cv) == -1) {
                                        fl.removeAllViews()
                                        (cv.parent as? ViewGroup)?.removeView(cv)
                                        fl.addView(
                                            cv,
                                            ViewGroup.LayoutParams(
                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                                ViewGroup.LayoutParams.MATCH_PARENT
                                            )
                                        )
                                        fl.requestFocus()
                                    }
                                }
                            },
                            onRelease = { fl ->
                                fl.removeAllViews()
                            }
                        )
                    }
                }



                // ── COMPACT SHORTS / REELS AUTO-SCROLL FLOATING BUTTON ──
                val isShortsOrReels = currentUrl.contains("/shorts") || currentUrl.contains("/reel")
                if (isShortsOrReels && !isBlackScreenMode && !isInPip && !isRealTV) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(
                                top = if (isFullScreenActive) 14.dp else 10.dp,
                                end = if (isFullScreenActive) 66.dp else 54.dp
                            )
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(
                                if (isShortsAutoScrollEnabled) Color(0xDDFF0000.toInt()) else Color.Black.copy(alpha = 0.60f)
                            )
                            .border(
                                1.dp,
                                if (isShortsAutoScrollEnabled) Color(0xFFFF5252) else Color.White.copy(alpha = 0.35f),
                                CircleShape
                            )
                            .clickable {
                                viewModel.toggleShortsAutoScroll()
                                toastMessage = if (!isShortsAutoScrollEnabled) {
                                    "⚡ Auto-Scroll ON"
                                } else {
                                    "Auto-Scroll OFF"
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Autorenew,
                            contentDescription = if (isShortsAutoScrollEnabled) "Auto-Scroll ON" else "Auto-Scroll OFF",
                            tint = if (isShortsAutoScrollEnabled) Color.White else Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // ── FLOATING EXIT FULLSCREEN BUTTON IN LANDSCAPE FULLSCREEN ──
                if (isFullScreenActive && !isRealTV) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 16.dp, end = 16.dp)
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f))
                            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                            .clickable {
                                if (customView != null) {
                                    try { customViewCallback?.onCustomViewHidden() } catch (_: Exception) {}
                                    customView = null
                                    customViewCallback = null
                                }
                                isManualFullScreenLandscape = false
                                webViewInstance?.evaluateJavascript(
                                    "(function(){ if (document.exitFullscreen) document.exitFullscreen(); else if (document.webkitExitFullscreen) document.webkitExitFullscreen(); })();",
                                    null
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FullscreenExit,
                            contentDescription = "Exit Fullscreen",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
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
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                color = Color(0xFF1E1E1E),
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, Color(0xFF333333))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = null,
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = YouTubeAudioPreset.fromId(youtubeAudioPreset).title,
                                        color = Color(0xFFCCCCCC),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAudioPresetDialog) {
        YouTubeAudioPresetDialog(
            currentPresetId = youtubeAudioPreset,
            isTV = isRealTV,
            onPresetSelect = { preset ->
                viewModel.setYouTubeAudioPreset(preset.id)
                webViewInstance?.evaluateJavascript(
                    "if (window.DutaAudioEngine) window.DutaAudioEngine.setPreset('${preset.id}');",
                    null
                )
                toastMessage = "Audio: ${preset.title}"
            },
            onDismiss = { showAudioPresetDialog = false }
        )
    }
}

@Composable
fun YouTubeAudioPresetDialog(
    currentPresetId: String,
    isTV: Boolean,
    onPresetSelect: (YouTubeAudioPreset) -> Unit,
    onDismiss: () -> Unit
) {
    val presets = remember { YouTubeAudioPreset.entries }
    val listState = rememberLazyListState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(Color(0xFF2A2A2A), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Audio Equalizer",
                        color = Color.White,
                        fontSize = if (isTV) 20.sp else 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Real-time Web Audio DSP Engine",
                        color = Color(0xFFAAAAAA),
                        fontSize = if (isTV) 13.sp else 12.sp
                    )
                }
            }
        },
        text = {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                items(presets) { preset ->
                    val isSelected = preset.id.equals(currentPresetId, ignoreCase = true)
                    var isFocused by remember { mutableStateOf(false) }

                    Surface(
                        onClick = { onPresetSelect(preset) },
                        shape = RoundedCornerShape(12.dp),
                        color = when {
                            isSelected -> Color(0xFF2D1E1E)
                            isFocused -> Color(0xFF333333)
                            else -> Color(0xFF222222)
                        },
                        border = BorderStroke(
                            width = if (isSelected || isFocused) 1.5.dp else 1.dp,
                            color = when {
                                isFocused -> Color.White
                                isSelected -> Color(0xFFFF5252)
                                else -> Color(0xFF383838)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isFocused = it.isFocused }
                            .scale(if (isFocused) 1.02f else 1f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        if (isSelected) Color(0x33FF5252) else Color(0xFF1E1E1E),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = preset.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) Color(0xFFFF5252) else Color.LightGray,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = preset.title,
                                        color = if (isSelected) Color.White else Color(0xFFEEEEEE),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = if (isTV) 16.sp else 15.sp
                                    )
                                    if (preset == YouTubeAudioPreset.SONY_MEGA_BASS) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = Color(0xFFFF5252),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "SONY",
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = preset.subtitle,
                                    color = if (isSelected) Color(0xFFCCCCCC) else Color(0xFF888888),
                                    fontSize = if (isTV) 13.sp else 12.sp,
                                    lineHeight = 16.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            RadioButton(
                                selected = isSelected,
                                onClick = { onPresetSelect(preset) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color(0xFFFF5252),
                                    unselectedColor = Color(0xFF666666)
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Text(
                    text = "Done",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isTV) 16.sp else 15.sp
                )
            }
        },
        containerColor = Color(0xFF1E1E1E),
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun HeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    isTV: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(if (isTV) 42.dp else 40.dp)
            .clip(CircleShape)
            .background(
                when {
                    isFocused -> Color.White.copy(alpha = 0.25f)
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
            modifier = Modifier.size(if (isTV) 22.dp else 20.dp)
        )
    }
}

@Composable
private fun HeaderChipButton(
    text: String,
    isTV: Boolean,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        color = when {
            isActive -> Color(0xFFFF0000)
            isFocused -> Color(0xFF383838)
            else -> Color(0xFF272727)
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isFocused) 2.dp else if (isActive) 1.dp else 0.dp,
                color = if (isFocused) Color.White else if (isActive) Color(0xFFFF5252) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(if (isTV) 42.dp else 40.dp)
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
            modifier = Modifier.size(if (isTV) 22.dp else 20.dp)
        )
    }
}

@Composable
private fun YouTubePillChip(
    title: String,
    isSelected: Boolean,
    isTV: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .scale(if (isFocused) 1.08f else 1.0f)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color(0xFFFF0000) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clip(RoundedCornerShape(8.dp)),
        color = when {
            isFocused -> Color(0xFF383838)
            isSelected -> Color(0xFFFFFFFF)
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

private fun createBlockedAdResponse(request: WebResourceRequest? = null): WebResourceResponse {
    val origin = request?.requestHeaders?.let { headers ->
        headers["Origin"] ?: headers["origin"]
    } ?: "https://m.youtube.com"

    val reqHeaders = request?.requestHeaders?.let { headers ->
        headers["Access-Control-Request-Headers"] ?: headers["access-control-request-headers"]
    } ?: "authorization, content-type, x-goog-visitor-id, x-youtube-client-name, x-youtube-client-version, range, x-origin"

    val url = request?.url?.toString()?.lowercase() ?: ""
    val isJson = url.contains("/youtubei/") || url.contains("ad_break") || url.endsWith(".json")
    val isJs = url.endsWith(".js") || url.contains(".js?")

    val mimeType = when {
        isJson -> "application/json"
        isJs -> "application/javascript"
        else -> "text/plain"
    }
    val content = when {
        isJson -> "{}".toByteArray(Charsets.UTF_8)
        isJs -> "/* blocked */".toByteArray(Charsets.UTF_8)
        else -> ByteArray(0)
    }

    val responseHeaders = mapOf(
        "Access-Control-Allow-Origin" to origin,
        "Access-Control-Allow-Credentials" to "true",
        "Access-Control-Allow-Methods" to "GET, POST, OPTIONS, HEAD, PUT",
        "Access-Control-Allow-Headers" to reqHeaders,
        "Access-Control-Max-Age" to "86400",
        "Cache-Control" to "no-cache, no-store, must-revalidate"
    )

    return WebResourceResponse(
        mimeType,
        "UTF-8",
        200,
        "OK",
        responseHeaders,
        ByteArrayInputStream(content)
    )
}

@SuppressLint("SetJavaScriptEnabled")
private fun createYouTubeWebView(
    context: Context,
    isRealTV: Boolean,
    jsBridge: Any,
    onNavigateToSidebar: () -> Unit = {},
    isAutoScrollSupplier: () -> Boolean = { false },
    audioPresetSupplier: () -> String = { "NORMAL" },
    onTitleChanged: (String) -> Unit,
    onLoadingChanged: (Boolean) -> Unit,
    onHistoryChanged: (Boolean, Boolean) -> Unit,
    onUrlChanged: (String) -> Unit,
    onShowCustomView: (View, WebChromeClient.CustomViewCallback) -> Unit,
    onHideCustomView: () -> Unit,
    onOpenSearch: () -> Unit
): WebView {
    return WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(0xFF0F0F0F.toInt())

        try {
            WebView.setWebContentsDebuggingEnabled(true)
        } catch (_: Exception) {}

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(false)
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

            // Clean Chrome Mobile User-Agent without WebView identifiers so YouTube does not restrict MSE codecs
            val defaultUa = userAgentString
            userAgentString = defaultUa
                .replace("; wv", "")
                .replace(Regex("Version/\\d+\\.\\d+\\s*"), "")
        }

        setLayerType(View.LAYER_TYPE_NONE, null)

        CookieManager.getInstance().let { cm ->
            cm.setAcceptCookie(true)
            cm.setAcceptThirdPartyCookies(this, true)
        }

        isFocusable = true
        isFocusableInTouchMode = true

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            try {
                WebViewCompat.addDocumentStartJavaScript(
                    this,
                    "window.__dutaIsTV = " + isRealTV + ";\n" + YOUTUBE_AD_NUKER_SCRIPT,
                    setOf("https://*.youtube.com", "https://youtube.com")
                )
            } catch (_: Exception) {}
        }

        // Register Duta bridge for metadata, SponsorBlock notifications, and PiP state
        addJavascriptInterface(jsBridge, "DutaYouTubeBridge")

        // TV Remote key events dispatcher (Shorts navigation, media controls, TV grid & spatial nav)
        setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                val current = url ?: ""
                val isShorts = current.contains("/shorts") || current.contains("/reel")
                val isWatch = current.contains("/watch")

                when {
                    keyCode == KeyEvent.KEYCODE_SEARCH -> {
                        onOpenSearch()
                        return@setOnKeyListener true
                    }
                    // ── SHORTS REMOTE NAVIGATION ──
                    isShorts && (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_PAGE_DOWN ||
                            keyCode == KeyEvent.KEYCODE_MEDIA_NEXT || keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) -> {
                        evaluateJavascript("if (window.advanceToNextShort) window.advanceToNextShort();", null)
                        return@setOnKeyListener true
                    }
                    isShorts && (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_PAGE_UP ||
                            keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS || keyCode == KeyEvent.KEYCODE_CHANNEL_UP) -> {
                        evaluateJavascript("if (window.goToPreviousShort) window.goToPreviousShort();", null)
                        return@setOnKeyListener true
                    }
                    isShorts && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) -> {
                        evaluateJavascript(
                            "(function(){ var v = document.querySelector('video'); if (v) { if (v.paused) v.play(); else v.pause(); } })();",
                            null
                        )
                        return@setOnKeyListener true
                    }
                    isShorts && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        evaluateJavascript(
                            "(function(){ var v = document.querySelector('video'); if (v) v.currentTime += 5; })();",
                            null
                        )
                        return@setOnKeyListener true
                    }
                    isShorts && keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> {
                        onNavigateToSidebar()
                        return@setOnKeyListener true
                    }

                    // ── WATCH PAGE REMOTE CONTROLS ──
                    isWatch && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_HEADSETHOOK) -> {
                        evaluateJavascript(
                            "(function(){ var p = document.getElementById('movie_player') || document.querySelector('.html5-video-player'); var v = document.querySelector('video'); if (p && typeof p.getPlayerState === 'function') { if (p.getPlayerState() === 1) p.pauseVideo(); else p.playVideo(); } else if (v) { if (v.paused) v.play(); else v.pause(); } })();",
                            null
                        )
                        return@setOnKeyListener true
                    }
                    isWatch && (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) -> {
                        evaluateJavascript(
                            "(function(){ var v = document.querySelector('video'); if (v) v.currentTime += 10; })();",
                            null
                        )
                        return@setOnKeyListener true
                    }
                    isWatch && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_MEDIA_REWIND) -> {
                        evaluateJavascript(
                            "(function(){ var v = document.querySelector('video'); if (v) v.currentTime = Math.max(0, v.currentTime - 10); })();",
                            null
                        )
                        return@setOnKeyListener true
                    }
                    isWatch && keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> {
                        evaluateJavascript(
                            "if (window.dutaTvNav) window.dutaTvNav.navigate('DOWN'); else window.scrollBy({ top: 300, behavior: 'smooth' });",
                            null
                        )
                        return@setOnKeyListener true
                    }
                    isWatch && keyCode == KeyEvent.KEYCODE_DPAD_UP -> {
                        evaluateJavascript(
                            "if (window.dutaTvNav) window.dutaTvNav.navigate('UP'); else window.scrollBy({ top: -300, behavior: 'smooth' });",
                            null
                        )
                        return@setOnKeyListener true
                    }

                    // ── BROWSE FEED & SEARCH TV SPATIAL NAVIGATION ──
                    isRealTV && keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> {
                        evaluateJavascript("if (window.dutaTvNav) window.dutaTvNav.navigate('DOWN');", null)
                        return@setOnKeyListener true
                    }
                    isRealTV && keyCode == KeyEvent.KEYCODE_DPAD_UP -> {
                        evaluateJavascript("if (window.dutaTvNav) window.dutaTvNav.navigate('UP');", null)
                        return@setOnKeyListener true
                    }
                    isRealTV && keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> {
                        evaluateJavascript("if (window.dutaTvNav) window.dutaTvNav.navigate('LEFT');", null)
                        return@setOnKeyListener true
                    }
                    isRealTV && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        evaluateJavascript("if (window.dutaTvNav) window.dutaTvNav.navigate('RIGHT');", null)
                        return@setOnKeyListener true
                    }
                    isRealTV && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) -> {
                        evaluateJavascript("if (window.dutaTvNav) window.dutaTvNav.navigate('SELECT');", null)
                        return@setOnKeyListener true
                    }

                    // Standard Media & Number Seek
                    keyCode == KeyEvent.KEYCODE_MEDIA_PLAY -> {
                        evaluateJavascript(
                            "(function(){ var p = document.getElementById('movie_player') || document.querySelector('.html5-video-player'); var v = document.querySelector('video'); if (p && typeof p.playVideo === 'function') p.playVideo(); else if (v && v.paused) v.play(); })();",
                            null
                        )
                        return@setOnKeyListener true
                    }
                    keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        evaluateJavascript(
                            "(function(){ var p = document.getElementById('movie_player') || document.querySelector('.html5-video-player'); var v = document.querySelector('video'); if (p && typeof p.pauseVideo === 'function') p.pauseVideo(); else if (v && !v.paused) v.pause(); })();",
                            null
                        )
                        return@setOnKeyListener true
                    }
                    keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                        val fraction = if (keyCode == KeyEvent.KEYCODE_0) 0.0 else (keyCode - KeyEvent.KEYCODE_0) * 0.1
                        evaluateJavascript(
                            "(function(){ var v = document.querySelector('video'); if (v && v.duration) v.currentTime = v.duration * $fraction; else if (v) v.currentTime = 0; })();",
                            null
                        )
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }

        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return false
                }
                return try {
                    val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                    context.startActivity(intent)
                    true
                } catch (_: Exception) {
                    true
                }
            }
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                val low = url.lowercase()

                // 1. NEVER intercept core streaming, video playback, or essential scripts
                if (low.contains("googlevideo.com") || low.contains("/videoplayback") ||
                    low.contains("/watch?") || low.contains("/shorts") || low.contains("base.js") ||
                    low.contains("desktop_polymer.js") || low.contains("player.js")
                ) {
                    return super.shouldInterceptRequest(view, request)
                }

                // 2. Intercept and mock ad endpoints
                for (adPath in BLOCKED_AD_PATHS) {
                    if (low.contains(adPath)) {
                        return createBlockedAdResponse(request)
                    }
                }

                // 3. Block known ad network hosts
                for (adHost in BLOCKED_AD_HOSTS) {
                    if (low.contains(adHost)) {
                        return createBlockedAdResponse(request)
                    }
                }

                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                onLoadingChanged(true)
                view?.evaluateJavascript("window.__dutaIsTV = $isRealTV;", null)
                view?.evaluateJavascript(YOUTUBE_AD_NUKER_SCRIPT, null)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                onLoadingChanged(false)
                view?.evaluateJavascript("window.__dutaIsTV = $isRealTV;", null)
                onHistoryChanged(view?.canGoBack() == true, view?.canGoForward() == true)
                if (!url.isNullOrBlank()) {
                    onUrlChanged(url)
                }
                view?.evaluateJavascript(YOUTUBE_AD_NUKER_SCRIPT, null)
                val autoScroll = isAutoScrollSupplier()
                view?.evaluateJavascript(
                    "if (window.setDutaAutoScrollEnabled) window.setDutaAutoScrollEnabled($autoScroll);",
                    null
                )
                val audioPreset = audioPresetSupplier()
                view?.evaluateJavascript(
                    "if (window.DutaAudioEngine) window.DutaAudioEngine.setPreset('$audioPreset');",
                    null
                )
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                if (!url.isNullOrBlank()) {
                    onUrlChanged(url)
                }
                val autoScroll = isAutoScrollSupplier()
                view?.evaluateJavascript(
                    "if (window.setDutaAutoScrollEnabled) window.setDutaAutoScrollEnabled($autoScroll);",
                    null
                )
                val audioPreset = audioPresetSupplier()
                view?.evaluateJavascript(
                    "if (window.DutaAudioEngine) window.DutaAudioEngine.setPreset('$audioPreset');",
                    null
                )
                view?.evaluateJavascript("if (window.updateDutaTvPageMode) window.updateDutaTvPageMode(); if (window.processDutaAds) window.processDutaAds();", null)
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
