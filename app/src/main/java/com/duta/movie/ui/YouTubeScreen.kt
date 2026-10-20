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
    "/youtubei/v1/player/ad_break",
    "/api/stats/qoe?*adformat*",
    "/ptracking?",
    "/get_midroll_info"
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
                .ytp-ad-button-icon,
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

        if (typeof window.__dutaAutoScrollEnabled === 'undefined') {
            window.__dutaAutoScrollEnabled = false;
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

        // Checks if the active Short/Reel is an Ad/Sponsored video
        function isShortsAdActive() {
            // 1. Sponsored or Ad badge aria labels
            var sponsoredBadges = document.querySelectorAll(
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

            // 2. Badge texts containing "Sponsored", "Ad", or "Promoted"
            var badgeTexts = document.querySelectorAll(
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

            // 3. Ad tracking links or interstitial action buttons (targeted ad markers)
            var ctaButtons = document.querySelectorAll(
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

            // 4. Video overlays or ad containers
            if (document.querySelector('.ad-showing, .ad-interrupting, .ytp-ad-player-overlay') !== null) {
                return true;
            }

            // 5. Container attributes
            var activeReel = document.querySelector('ytm-shorts-carousel-video-renderer[is-active], ytm-reel-item-renderer[is-active], ytd-reel-video-renderer[is-active]');
            if (activeReel) {
                if (activeReel.hasAttribute('is-ad') || activeReel.classList.contains('ad-showing')) {
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
            if (document.querySelector('.ad-showing, .ad-interrupting') !== null) {
                return true;
            }
            var skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .videoAdUiSkipButton, button[aria-label*="Skip ad" i]');
            if (skipBtn && (skipBtn.offsetParent !== null || skipBtn.getClientRects().length > 0)) {
                return true;
            }
            return false;
        }

        function hookShortsVideo(v) {
            if (!v || v.__dutaHooked) return;
            v.__dutaHooked = true;

            function onPlaybackCheck() {
                var isShorts = location.href.indexOf('/shorts') !== -1 ||
                               location.href.indexOf('/reel') !== -1 ||
                               location.href.indexOf('/reels') !== -1;
                if (!isShorts) return;

                // 1. Ad Short bypass
                if (isShortsAdActive()) {
                    var nowAd = Date.now();
                    if (!window.__lastShortsAdSkip || nowAd - window.__lastShortsAdSkip > 1000) {
                        window.__lastShortsAdSkip = nowAd;
                        var advAd = advanceToNextShort();
                        if (advAd && window.DutaYouTubeBridge && typeof window.DutaYouTubeBridge.onShortAdBypassed === 'function') {
                            window.DutaYouTubeBridge.onShortAdBypassed();
                        }
                    }
                    return;
                }

                // 2. Normal Auto-scroll
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

            v.addEventListener('timeupdate', onPlaybackCheck);
            v.addEventListener('ended', onPlaybackCheck);
        }

        var wasAdActive = false;

        function processAdsAndPlayback() {
            try {
                checkVideoMetadata();

                var player = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
                var video = document.querySelector('video');
                if (video) {
                    hookShortsVideo(video);
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
                            if (isFinite(sv.duration) && sv.duration > 0) {
                                sv.currentTime = sv.duration;
                            }
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

                        // 3. Mute & fast-forward ad to end (16x rate + currentTime = duration)
                        if (video) {
                            if (!video.muted) {
                                video.muted = true;
                                window.__dutaMutedByAd = true;
                            }
                            if (isFinite(video.duration) && video.duration > 0 && video.currentTime < video.duration - 0.1) {
                                video.currentTime = video.duration;
                            }
                            video.playbackRate = 16.0;
                        }
                    } else {
                        if (wasAdActive) {
                            wasAdActive = false;
                            if (window.DutaYouTubeBridge && typeof window.DutaYouTubeBridge.onAdStateChanged === 'function') {
                                window.DutaYouTubeBridge.onAdStateChanged(false);
                            }
                        }

                        // Restore sound & speed if it was muted/throttled during an ad
                        if (video) {
                            if (window.__dutaMutedByAd) {
                                video.muted = false;
                                window.__dutaMutedByAd = false;
                            }
                            if (video.playbackRate > 2.0) {
                                video.playbackRate = window.__dutaTargetSpeed || 1.0;
                            }
                        }
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

                // ── D. NOTIFY PLAYBACK STATE ──
                if (video && window.DutaYouTubeBridge) {
                    var isPlaying = !video.paused && !video.ended && video.readyState > 2;
                    window.DutaYouTubeBridge.onPlaybackState(isPlaying, video.currentTime || 0, video.duration || 0);
                }

                // ── E. DISMISS PROMO / UPSELL DIALOGS ──
                var dismissBtns = document.querySelectorAll(
                    'ytm-mealbar-promo-renderer button, ytm-upsell-dialog-renderer button, [aria-label*="Dismiss" i], [aria-label*="No thanks" i]'
                );
                for (var k = 0; k < dismissBtns.length; k++) {
                    try { dismissBtns[k].click(); } catch(e){}
                }

                // ── F. NORMAL SHORTS AUTO-SCROLL (WHEN VIDEO FINISHES NATURALLY) ──
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
            } catch(err) {}
        }

        if (!window.__dutaWatchdogStarted) {
            window.__dutaWatchdogStarted = true;
            setInterval(processAdsAndPlayback, 300);
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
    var isLoading by remember { mutableStateOf(false) }
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
    var isManualFullScreenLandscape by remember { mutableStateOf(false) }
    val isFullScreenActive = customView != null || isManualFullScreenLandscape
    val isMobileLandscapeEnabled by viewModel.isMobileLandscapeEnabled.collectAsStateWithLifecycle()
    val isShortsAutoScrollEnabled by viewModel.isShortsAutoScrollEnabled.collectAsStateWithLifecycle()

    LaunchedEffect(isShortsAutoScrollEnabled, webViewInstance) {
        webViewInstance?.evaluateJavascript(
            "if (window.setDutaAutoScrollEnabled) window.setDutaAutoScrollEnabled($isShortsAutoScrollEnabled);",
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
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

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
                sponsorToastMessage = "⚡ Auto-scrolled to next Short"
            }

            @JavascriptInterface
            fun onShortAdBypassed() {
                sponsorToastMessage = "⚡ Bypassed Ad Short"
            }

            @JavascriptInterface
            fun onAdStateChanged(isAdPlaying: Boolean) {
                if (isAdPlaying) {
                    sponsorToastMessage = "⚡ Auto-skipping Ad..."
                }
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
        if (isBlackScreenMode) {
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
            // ── AUTHENTIC YOUTUBE HEADER & FILTER BAR (Hidden in PiP & Fullscreen Landscape) ──
            if (customView == null && !isInPip && !isBlackScreenMode && !isManualFullScreenLandscape) {
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
                                        onClick = {
                                            isSearchActive = false
                                            keyboardController?.hide()
                                            focusManager.clearFocus()
                                        }
                                    )

                                    Spacer(modifier = Modifier.width(6.dp))

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
                                                    .focusRequester(searchFocusRequester),
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
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .weight(1f, fill = false)
                                            .horizontalScroll(rememberScrollState())
                                    ) {
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

                                        // 5. Auto-Scroll Toggle Button (Shorts & Reels)
                                        HeaderIconButton(
                                            icon = Icons.Default.Autorenew,
                                            contentDescription = if (isShortsAutoScrollEnabled) "Auto-Scroll: ON" else "Auto-Scroll: OFF",
                                            isTV = isRealTV,
                                            tint = if (isShortsAutoScrollEnabled) Color(0xFFFF0000) else Color.White,
                                            onClick = {
                                                viewModel.toggleShortsAutoScroll()
                                                sponsorToastMessage = if (!isShortsAutoScrollEnabled) {
                                                    "⚡ Auto-Scroll ON"
                                                } else {
                                                    "Auto-Scroll OFF"
                                                }
                                            }
                                        )

                                        Spacer(modifier = Modifier.width(2.dp))

                                        // 6. Fullscreen Landscape Button (Mobile mode)
                                        if (!isRealTV) {
                                            HeaderIconButton(
                                                icon = if (isFullScreenActive) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                                contentDescription = if (isFullScreenActive) "Exit Fullscreen" else "Fullscreen Landscape",
                                                isTV = false,
                                                tint = if (isFullScreenActive) Color.Red else Color.White,
                                                onClick = {
                                                    if (isFullScreenActive) {
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
                                                    } else {
                                                        isManualFullScreenLandscape = true
                                                        webViewInstance?.evaluateJavascript(
                                                            """(function(){
                                                                var p = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
                                                                var v = document.querySelector('video');
                                                                if (p && typeof p.toggleFullscreen === 'function') {
                                                                    p.toggleFullscreen();
                                                                } else if (v && v.requestFullscreen) {
                                                                    v.requestFullscreen();
                                                                } else if (v && v.webkitRequestFullscreen) {
                                                                    v.webkitRequestFullscreen();
                                                                } else {
                                                                    var btn = document.querySelector('.ytp-fullscreen-button, button[aria-label*="full screen" i]');
                                                                    if (btn) btn.click();
                                                                }
                                                            })();""",
                                                            null
                                                        )
                                                    }
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                        }

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

                        // ── CHIPS: Categories or Quick Search Suggestions ──
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isSearchActive) {
                                items(searchSuggestions) { term ->
                                    YouTubePillChip(
                                        title = "🔍 $term",
                                        isSelected = false,
                                        isTV = isRealTV,
                                        onClick = {
                                            executeSearch(term)
                                        }
                                    )
                                }
                            } else {
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
                            isAutoScrollSupplier = { viewModel.isShortsAutoScrollEnabled.value },
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
                        wv.onResume()
                        if (wv.url.isNullOrBlank() || wv.url == "about:blank") {
                            wv.loadUrl(currentUrl)
                        }
                        wv.invalidate()
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
                            factory = { ctx ->
                                FrameLayout(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                }
                            },
                            update = { fl ->
                                fl.removeAllViews()
                                customView?.let { cv ->
                                    (cv.parent as? ViewGroup)?.removeView(cv)
                                    fl.addView(
                                        cv,
                                        ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                    )
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
                                sponsorToastMessage = if (!isShortsAutoScrollEnabled) {
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
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
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
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .scale(if (isFocused) 1.08f else 1.0f)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color(0xFFFF0000) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .focusable()
            .onFocusChanged { isFocused = it.isFocused },
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

private fun createBlockedAdResponse(): WebResourceResponse {
    return WebResourceResponse(
        "text/plain",
        "UTF-8",
        204,
        "No Content",
        mapOf(
            "Access-Control-Allow-Origin" to "*",
            "Access-Control-Allow-Methods" to "GET, POST, OPTIONS, HEAD",
            "Access-Control-Allow-Headers" to "*",
            "Access-Control-Max-Age" to "86400"
        ),
        ByteArrayInputStream(ByteArray(0))
    )
}

@SuppressLint("SetJavaScriptEnabled")
private fun createYouTubeWebView(
    context: Context,
    isRealTV: Boolean,
    jsBridge: Any,
    isAutoScrollSupplier: () -> Boolean = { false },
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
            databaseEnabled = true
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

        setLayerType(View.LAYER_TYPE_HARDWARE, null)

        CookieManager.getInstance().let { cm ->
            cm.setAcceptCookie(true)
            cm.setAcceptThirdPartyCookies(this, true)
        }

        isFocusable = true
        isFocusableInTouchMode = true

        // Register Duta bridge for metadata, SponsorBlock notifications, and PiP state
        addJavascriptInterface(jsBridge, "DutaYouTubeBridge")

        // TV Remote key events dispatcher (Media controls & Remote number seeking)
        setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when {
                    keyCode == KeyEvent.KEYCODE_SEARCH -> {
                        onOpenSearch()
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

                // 1. Intercept and block ad endpoints (e.g. ad_break, /pagead/, /api/stats/ads)
                for (adPath in BLOCKED_AD_PATHS) {
                    if (low.contains(adPath)) {
                        return createBlockedAdResponse()
                    }
                }

                // 2. Block known ad network hosts
                for (adHost in BLOCKED_AD_HOSTS) {
                    if (low.contains(adHost)) {
                        return createBlockedAdResponse()
                    }
                }

                // 3. Never intercept core YouTube streaming or player metadata
                if (low.contains("googlevideo.com") || low.contains("/youtubei/") || low.contains("/videoplayback")) {
                    return super.shouldInterceptRequest(view, request)
                }

                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                onLoadingChanged(true)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                onLoadingChanged(false)
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
