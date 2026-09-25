package com.duta.movie.util

object Nuker {
    fun getScript(intervalMs: Int = 600): String {
        return """
            (function() {
                try {
                    if (window.nukerRunning) return;
                    window.nukerRunning = true;

                    var log = function(msg) {
                        if (window.AndroidPlayer && window.AndroidPlayer.log) {
                            window.AndroidPlayer.log("Nuker v7.4: " + msg);
                        }
                    };

                    window.nukerAttempts = 0;
                    window.nukerStartTime = Date.now();
                    window.videoFound = false;
                    window.successNotified = false;
                    window.gateNotified = false;
                    window.clickedRegistry = [];
                    
                    (function neutralize() {
                        var safeSet = function(obj, prop, val) {
                            try {
                                Object.defineProperty(obj, prop, { value: val, writable: false, configurable: true });
                            } catch(e) { try { obj[prop] = val; } catch(err) {} }
                        };
                        var noop = function() { return null; };
                        safeSet(window, 'ga', noop); safeSet(window, 'fbq', noop); safeSet(window, 'gtag', noop);
                        safeSet(window, 'ym', noop); safeSet(window, 'histats', { start: noop });
                        window._pop = noop; window.pop_show = noop; window.pop_count = 0;
                        window.ad_show = noop; window.ad_count = 0; window.open_pop = noop;
                        window.show_popup = noop; window._top = window; window.open = noop;
                    })();

                    var ensureBlackBackground = function() {
                        try {
                            if (document.documentElement) {
                                document.documentElement.style.setProperty('background', '#000', 'important');
                                document.documentElement.style.setProperty('background-color', '#000', 'important');
                            }
                            if (document.body) {
                                document.body.style.setProperty('background', '#000', 'important');
                                document.body.style.setProperty('background-color', '#000', 'important');
                            }
                            var fms = document.querySelectorAll('iframe');
                            for (var fi = 0; fi < fms.length; fi++) {
                                try {
                                    var fDoc = fms[fi].contentDocument || (fms[fi].contentWindow && fms[fi].contentWindow.document);
                                    if (fDoc) {
                                        if (fDoc.documentElement) {
                                            fDoc.documentElement.style.setProperty('background', '#000', 'important');
                                            fDoc.documentElement.style.setProperty('background-color', '#000', 'important');
                                        }
                                        if (fDoc.body) {
                                            fDoc.body.style.setProperty('background', '#000', 'important');
                                            fDoc.body.style.setProperty('background-color', '#000', 'important');
                                        }
                                    }
                                } catch(e) {}
                            }
                        } catch(e) {}
                    };
                    ensureBlackBackground();

                    var isLandingPageGate = function() {
                        try {
                            if (window.location.href.toLowerCase().indexOf('login') !== -1 || 
                                window.location.href.toLowerCase().indexOf('register') !== -1) return true;

                            // Check for common login/gate inputs
                            var inputs = document.querySelectorAll('input[type="password"], input[type="email"], input[name*="user"], input[name*="pass"]');
                            if (inputs.length >= 2) return true;

                            // Standard Ad Gates (NEVER include real video player elements here!)
                            var sel = '.fujihide-play, .click-gate, .player-click-gate, img[src*="dm21"], img[src*="no_video"], img[src*="deleted"], .vjs-error-display, .vjs-error, .get-started, #btn-login';
                            var gateEl = document.querySelector(sel);
                            if (gateEl) return true;
                            
                            var bodyText = ((document.body && (document.body.innerText || document.body.textContent)) || "").toLowerCase();
                            if (bodyText.indexOf('path not found') !== -1 || 
                                bodyText.indexOf('404 not found') !== -1 ||
                                bodyText.indexOf('502 bad gateway') !== -1 ||
                                bodyText.indexOf('welcome back') !== -1 ||
                                bodyText.indexOf('please login') !== -1 ||
                                bodyText.indexOf('sign in to') !== -1 ||
                                bodyText.indexOf('member login') !== -1 ||
                                bodyText.indexOf('create account') !== -1 ||
                                bodyText.indexOf('no longer available') !== -1 ||
                                bodyText.indexOf('expired or has been deleted') !== -1 ||
                                bodyText.indexOf('file was deleted') !== -1 ||
                                bodyText.indexOf('the file you are looking for does not exist') !== -1 ||
                                bodyText.indexOf('file not found') !== -1 ||
                                bodyText.indexOf("can't find the file") !== -1 ||
                                bodyText.indexOf("cant find the file") !== -1 ||
                                bodyText.indexOf("deleted by the owner") !== -1 ||
                                bodyText.indexOf("copyright violation") !== -1 ||
                                bodyText.indexOf("cant give you what you looking for") !== -1 ||
                                bodyText.indexOf("can't give you what you looking for") !== -1 ||
                                bodyText.indexOf("this video is not available") !== -1 ||
                                bodyText.indexOf("video not found or deleted") !== -1 ||
                                bodyText.indexOf("video is not ready yet") !== -1 ||
                                bodyText.indexOf("cookieindex is not defined") !== -1 ||
                                bodyText.indexOf('video not found') !== -1 ||
                                bodyText.indexOf('video was deleted') !== -1 ||
                                bodyText.indexOf('media could not be loaded') !== -1 ||
                                bodyText.indexOf('format is not supported') !== -1 ||
                                bodyText.indexOf('server or network failed') !== -1 ||
                                bodyText.indexOf('file has been removed') !== -1 ||
                                bodyText.indexOf('not a robot') !== -1 ||
                                bodyText.indexOf('i am not a robot') !== -1 ||
                                bodyText.indexOf("i'm not a robot") !== -1 ||
                                bodyText.indexOf('verify you are human') !== -1 ||
                                bodyText.indexOf('click allow') !== -1 ||
                                bodyText.indexOf('press allow') !== -1 ||
                                bodyText.indexOf('searchresultsworld') !== -1 ||
                                bodyText.indexOf('welcome to the world') !== -1 ||
                                bodyText.indexOf('welcome to the abyss') !== -1) return true;

                            var btns = document.querySelectorAll('button, a, .button, h1, h2, h3, p, span, div');
                            for(var i=0; i < Math.min(btns.length, 120); i++) {
                                var b = btns[i];
                                if (!b) continue;
                                var t = (b.innerText || b.textContent || "").toLowerCase();
                                if (t.indexOf('get started') !== -1 ||
                                    t.indexOf('welcome back') !== -1 || t.indexOf('sign in') !== -1 || t.indexOf('login') !== -1 ||
                                    t.indexOf('register') !== -1 || t.indexOf('sign up') !== -1 || 
                                    t.indexOf('verify you are human') !== -1 ||
                                    t.indexOf('not a robot') !== -1 || t.indexOf('i am not a robot') !== -1 ||
                                    t.indexOf("i'm not a robot") !== -1 || t.indexOf('click allow') !== -1 ||
                                    t.indexOf('press allow') !== -1) return true;
                            }
                        } catch(e) {}
                        return false;
                    };

                    window.isGateActive = isLandingPageGate;

                    if (!window.playerBridge) {
                        window.playerBridge = {
                            heartbeat: function() {
                                if (window.AndroidPlayer) {
                                    var v = this.findVideo();
                                    var status = v ? (v.isProxy ? "Proxy Active" : ("Video Found (" + v.readyState + ")")) : "Video NOT Found";
                                    var gateInfo = "No Gate";
                                    if (!window.successNotified && window.isGateActive && window.isGateActive()) {
                                        var bodyText = ((document.body && (document.body.innerText || document.body.textContent)) || "").toLowerCase();
                                        var inputs = document.querySelectorAll('input[type="password"], input[type="email"]').length;
                                        gateInfo = "Gate Found (Inputs=" + inputs + ", Text=" + (bodyText.length > 20 ? bodyText.substring(0, 20) : bodyText) + ")";
                                    }
                                    log("Heartbeat: " + status + " | " + gateInfo + " | At: " + window.nukerAttempts);
                                }
                            },
                            findVideo: function() {
                                if (window._cachedVideo && window._cachedVideo.isConnected && !window._cachedVideo.isProxy) {
                                    return window._cachedVideo;
                                }
                                var find = function(root) {
                                    if (!root) return null;
                                    try {
                                        var v = root.querySelector('video');
                                        if (v) { window._cachedVideo = v; return v; }
                                        var iframes = root.querySelectorAll('iframe, embed, object');
                                        for (var i = 0; i < iframes.length; i++) {
                                            try {
                                                var d = iframes[i].contentDocument || iframes[i].contentWindow.document;
                                                if (d) { var found = find(d); if (found) { window._cachedVideo = found; return found; } }
                                            } catch(e) {
                                                var src = (iframes[i].src || "").toLowerCase();
                                                log("Checking iframe: " + src);
                                                if (src.indexOf('player') !== -1 || src.indexOf('embed') !== -1 || src.indexOf('abyss') !== -1 || 
                                                    src.indexOf('voe') !== -1 || src.indexOf('playstream') !== -1 || src.indexOf('swhoi') !== -1 ||
                                                    src.indexOf('veev') !== -1 || src.indexOf('iplayer') !== -1 || src.indexOf('mirror') !== -1 ||
                                                    src.indexOf('wishonly') !== -1 || src.indexOf('streamwish') !== -1 || src.indexOf('wishembed') !== -1 || src.indexOf('strwish') !== -1 ||
                                                    src.indexOf('vidhide') !== -1 || src.indexOf('luluvdo') !== -1 || src.indexOf('lulustream') !== -1 ||
                                                    src.indexOf('ghbrisk') !== -1 || src.indexOf('ohio') !== -1 || src.indexOf('hglink') !== -1 || src.indexOf('hgcloud') !== -1 ||
                                                    src.indexOf('audinifer') !== -1 || src.indexOf('vibuxer') !== -1 || src.indexOf('hanerix') !== -1 || src.indexOf('indostream') !== -1 || src.indexOf('amt') !== -1 ||
                                                    src.indexOf('youtube') !== -1 || src.indexOf('youtu.be') !== -1) {
                                                    return { isProxy: true, paused: false, readyState: 1, currentTime: 0.1, duration: 0 };
                                                }
                                            }
                                        }
                                    } catch(e) {}
                                    return null;
                                };
                                return find(document);
                            },
                            broadcastToFrames: function(msg) {
                                try {
                                    var ifrs = document.querySelectorAll('iframe');
                                    for (var i = 0; i < ifrs.length; i++) {
                                        try {
                                            ifrs[i].contentWindow.postMessage(msg, '*');
                                            ifrs[i].contentWindow.postMessage(JSON.stringify(msg), '*');
                                        } catch(e){}
                                    }
                                } catch(err){}
                            },
                            reportState: function(isPlaying, time, duration) {
                                try {
                                    if (window.AndroidPlayer && window.AndroidPlayer.onPlayerState) {
                                        window.AndroidPlayer.onPlayerState(isPlaying ? 1 : 0, time, duration);
                                    }
                                } catch(e){}
                                try {
                                    if (window.parent && window.parent !== window) {
                                        window.parent.postMessage({
                                            type: 'duta:state',
                                            isPlaying: isPlaying,
                                            time: time,
                                            duration: duration
                                        }, '*');
                                    }
                                } catch(e){}
                            },
                            play: function() {
                                try {
                                    var v = this.findVideo();
                                    if (v && !v.isProxy && v.play) v.play().catch(function(){});
                                    if (window.jwplayer && typeof window.jwplayer === 'function') {
                                        try { window.jwplayer().play(); } catch(e){}
                                    }
                                    if (window.videojs) {
                                        try {
                                            var players = window.videojs.getPlayers ? window.videojs.getPlayers() : {};
                                            for (var p in players) { if (players[p] && players[p].play) players[p].play(); }
                                        } catch(e){}
                                    }
                                    this.broadcastToFrames({ type: 'duta:play', method: 'play', event: 'command', func: 'playVideo', args: [] });
                                } catch(e){}
                            },
                            pause: function() {
                                try {
                                    var v = this.findVideo();
                                    if (v && !v.isProxy && v.pause) v.pause();
                                    if (window.jwplayer && typeof window.jwplayer === 'function') {
                                        try { window.jwplayer().pause(); } catch(e){}
                                    }
                                    if (window.videojs) {
                                        try {
                                            var players = window.videojs.getPlayers ? window.videojs.getPlayers() : {};
                                            for (var p in players) { if (players[p] && players[p].pause) players[p].pause(); }
                                        } catch(e){}
                                    }
                                    this.broadcastToFrames({ type: 'duta:pause', method: 'pause', event: 'command', func: 'pauseVideo', args: [] });
                                } catch(e){}
                            },
                            seek: function(t) {
                                try {
                                    var v = this.findVideo();
                                    if (v && !v.isProxy) v.currentTime = t;
                                    if (window.jwplayer && typeof window.jwplayer === 'function') {
                                        try { window.jwplayer().seek(t); } catch(e){}
                                    }
                                    if (window.videojs) {
                                        try {
                                            var players = window.videojs.getPlayers ? window.videojs.getPlayers() : {};
                                            for (var p in players) { if (players[p] && players[p].currentTime) players[p].currentTime(t); }
                                        } catch(e){}
                                    }
                                    this.broadcastToFrames({ type: 'duta:seek', time: t, value: t, method: 'seek', event: 'command', func: 'seekTo', args: [t, true] });
                                    if (v && !v.isProxy) {
                                        this.reportState(1, t, v.duration || 0);
                                    }
                                } catch(e){}
                            },
                            sync: function() {
                                try {
                                    var v = this.findVideo();
                                    if(v && window.AndroidPlayer) {
                                        var hasGate = window.successNotified ? false : isLandingPageGate();
                                        var timeActive = Date.now() - window.nukerStartTime;
                                        
                                        if (!v.isProxy) {
                                            window.videoFound = true;
                                            if (!v._dutaHooked) {
                                                v._dutaHooked = true;
                                                var self = this;
                                                v.addEventListener('timeupdate', function() {
                                                    self.reportState(!v.paused, v.currentTime, v.duration || 0);
                                                });
                                                v.addEventListener('play', function() {
                                                    self.reportState(true, v.currentTime, v.duration || 0);
                                                });
                                                v.addEventListener('pause', function() {
                                                    self.reportState(false, v.currentTime, v.duration || 0);
                                                });
                                            }
                                        }

                                        // Auto-start JWPlayer / VideoJS if available
                                        if (window.jwplayer && typeof window.jwplayer === 'function') {
                                            try {
                                                var jw = window.jwplayer();
                                                if (jw && typeof jw.getState === 'function') {
                                                    var jwState = jw.getState();
                                                    if (jwState !== 'playing' && jwState !== 'buffering') {
                                                        jw.play();
                                                    }
                                                    if (!window.jwHooked && typeof jw.on === 'function') {
                                                        window.jwHooked = true;
                                                        var self = this;
                                                        jw.on('play', function() {
                                                            window.videoFound = true;
                                                            if (document.body) {
                                                                document.body.classList.add('video-active', 'video-playing');
                                                            }
                                                            try {
                                                                var pOverlays = document.querySelectorAll('.jw-display-icon-display, .jw-display-icon-container, .jw-display-icon-idle, .jw-icon-display, .jw-display, .jw-svg-icon-play, .vjs-big-play-button, .play-button, #play-button, .play-btn, .big-play-button, #playback, #overlay, div#playback, div#overlay, svg[viewBox="0 0 24 24"], svg[viewBox="0 0 240 240"]');
                                                                for (var pi = 0; pi < pOverlays.length; pi++) {
                                                                    pOverlays[pi].style.setProperty('display', 'none', 'important');
                                                                    pOverlays[pi].style.setProperty('opacity', '0', 'important');
                                                                    pOverlays[pi].style.setProperty('visibility', 'hidden', 'important');
                                                                    pOverlays[pi].style.setProperty('pointer-events', 'none', 'important');
                                                                    pOverlays[pi].style.setProperty('width', '0', 'important');
                                                                    pOverlays[pi].style.setProperty('height', '0', 'important');
                                                                }
                                                            } catch(e){}
                                                            if (!window.successNotified && window.AndroidPlayer) {
                                                                window.successNotified = true;
                                                                log("JWPlayer onPlay event fired");
                                                                window.AndroidPlayer.notifyVideoPlaying();
                                                            }
                                                            self.reportState(true, jw.getPosition ? jw.getPosition() : 0.1, jw.getDuration ? jw.getDuration() : 0);
                                                        });
                                                        jw.on('firstFrame', function() {
                                                            window.videoFound = true;
                                                            if (document.body) {
                                                                document.body.classList.add('video-active', 'video-playing');
                                                            }
                                                            try {
                                                                var pOverlays = document.querySelectorAll('.jw-display-icon-display, .jw-display-icon-container, .jw-display-icon-idle, .jw-icon-display, .jw-display, .jw-svg-icon-play, .vjs-big-play-button, .play-button, #play-button, .play-btn, .big-play-button, #playback, #overlay, div#playback, div#overlay, svg[viewBox="0 0 24 24"], svg[viewBox="0 0 240 240"]');
                                                                for (var pi = 0; pi < pOverlays.length; pi++) {
                                                                    pOverlays[pi].style.setProperty('display', 'none', 'important');
                                                                    pOverlays[pi].style.setProperty('opacity', '0', 'important');
                                                                    pOverlays[pi].style.setProperty('visibility', 'hidden', 'important');
                                                                    pOverlays[pi].style.setProperty('pointer-events', 'none', 'important');
                                                                    pOverlays[pi].style.setProperty('width', '0', 'important');
                                                                    pOverlays[pi].style.setProperty('height', '0', 'important');
                                                                }
                                                            } catch(e){}
                                                            if (!window.successNotified && window.AndroidPlayer) {
                                                                window.successNotified = true;
                                                                log("JWPlayer onFirstFrame event fired");
                                                                window.AndroidPlayer.notifyVideoPlaying();
                                                            }
                                                        });
                                                        jw.on('pause', function() {
                                                            self.reportState(false, jw.getPosition ? jw.getPosition() : 0.1, jw.getDuration ? jw.getDuration() : 0);
                                                        });
                                                        jw.on('seek', function(e) {
                                                            // User scrubbed the seek bar: report not-playing so stall guard resets
                                                            self.reportState(false, e.offset || (jw.getPosition ? jw.getPosition() : 0), jw.getDuration ? jw.getDuration() : 0);
                                                        });
                                                        jw.on('buffer', function() {
                                                            // CDN buffering after seek or mid-stream: report not-playing
                                                            self.reportState(false, jw.getPosition ? jw.getPosition() : 0, jw.getDuration ? jw.getDuration() : 0);
                                                        });
                                                        jw.on('time', function(e) {
                                                            if (e && e.currentTime > 0.3) {
                                                                window.videoFound = true;
                                                                if (!window.successNotified && window.AndroidPlayer) {
                                                                    window.successNotified = true;
                                                                    window.AndroidPlayer.notifyVideoPlaying();
                                                                }
                                                                self.reportState(true, e.currentTime || 0.1, e.duration || (jw.getDuration ? jw.getDuration() : 0));
                                                            }
                                                        });
                                                    }
                                                    if (jwState === 'playing') {
                                                        window.videoFound = true;
                                                        var jwPos = jw.getPosition ? jw.getPosition() : 0.1;
                                                        var jwDur = jw.getDuration ? jw.getDuration() : 0;
                                                        if (jwPos > 0.3 && !window.successNotified && window.AndroidPlayer) {
                                                            window.successNotified = true;
                                                            log("JWPlayer playing state - notifying success");
                                                            window.AndroidPlayer.notifyVideoPlaying();
                                                        }
                                                        this.reportState(true, jwPos, jwDur);
                                                    }
                                                }
                                            } catch(e){}
                                        }
                                        if (window.videojs && typeof window.videojs.getPlayers === 'function') {
                                            try {
                                                var vjsPlayers = window.videojs.getPlayers();
                                                for (var p in vjsPlayers) {
                                                    if (vjsPlayers[p] && vjsPlayers[p].paused && vjsPlayers[p].paused()) {
                                                        vjsPlayers[p].play();
                                                    }
                                                }
                                            } catch(e){}
                                        }

                                        if (!v.isProxy && v.paused && !v.ended) { 
                                            v.play().catch(function() { 
                                                if (window.nukerAttempts % 2 === 0) {
                                                    v.click(); 
                                                    try {
                                                        var rect = v.getBoundingClientRect();
                                                        var centerEl = document.elementFromPoint(rect.left + rect.width/2, rect.top + rect.height/2);
                                                        if (centerEl && centerEl !== v) centerEl.click();
                                                    } catch(e) {}
                                                }
                                            }); 
                                        }
                                        
                                        var isShortClip = !v.isProxy && v.duration > 0 && v.duration < 45;
                                        if (isShortClip && !window.gateNotified) {
                                            window.gateNotified = true;
                                            log("Short video placeholder detected (duration=" + v.duration + "s). Triggering auto-rotation.");
                                            if (window.AndroidPlayer && window.AndroidPlayer.notifyGateStuck) {
                                                window.AndroidPlayer.notifyGateStuck(window.location.href);
                                            }
                                            return;
                                        }
                                        var isStrictPlaying = !v.isProxy && (v.currentTime > 0.5 && !hasGate && !isShortClip);
                                        
                                        if (isStrictPlaying && !window.successNotified) {
                                            window.successNotified = true;
                                            log("Handshake Success - Playback Verified");
                                            window.AndroidPlayer.notifyVideoPlaying();
                                        }
                                        if (!v.isProxy) {
                                            this.reportState(v.paused ? 0 : 1, v.currentTime || 0.1, v.duration || 0);
                                            if (!v.paused || v.currentTime > 0.1 || isStrictPlaying || window.successNotified) {
                                                if (document.body && !document.body.classList.contains('video-active')) {
                                                    document.body.classList.add('video-active', 'video-playing');
                                                }
                                                try {
                                                    var badPOverlays = document.querySelectorAll('.jw-display-icon-display, .jw-display-icon-container, .jw-display-icon-idle, .jw-icon-display, .jw-display, .jw-svg-icon-play, .vjs-big-play-button, .play-button, #play-button, .play-btn, .big-play-button, #playback, #overlay, div#playback, div#overlay, svg[viewBox="0 0 24 24"], svg[viewBox="0 0 240 240"]');
                                                    for (var bi = 0; bi < badPOverlays.length; bi++) {
                                                        badPOverlays[bi].style.setProperty('display', 'none', 'important');
                                                        badPOverlays[bi].style.setProperty('opacity', '0', 'important');
                                                        badPOverlays[bi].style.setProperty('visibility', 'hidden', 'important');
                                                        badPOverlays[bi].style.setProperty('pointer-events', 'none', 'important');
                                                        badPOverlays[bi].style.setProperty('width', '0', 'important');
                                                        badPOverlays[bi].style.setProperty('height', '0', 'important');
                                                    }
                                                } catch(e){}
                                            }
                                        }
                                    }
                                } catch(e) {}
                            }
                        };
                        setInterval(function() { window.playerBridge.sync(); }, 1200);
                        setInterval(function() { window.playerBridge.heartbeat(); }, 5000);
                    }

                    if (!window.dutaMsgHooked) {
                        window.dutaMsgHooked = true;
                        window.addEventListener('message', function(event) {
                            try {
                                var data = event.data;
                                if (typeof data === 'string') {
                                    try { data = JSON.parse(data); } catch(e){}
                                }
                                if (!data || typeof data !== 'object') return;

                                if (data.type === 'duta:state') {
                                    window.videoFound = true;
                                    if (data.time > 0.3 && !window.successNotified && window.AndroidPlayer) {
                                        window.successNotified = true;
                                        window.AndroidPlayer.notifyVideoPlaying();
                                    }
                                    if (window.AndroidPlayer && window.AndroidPlayer.onPlayerState) {
                                        window.AndroidPlayer.onPlayerState(data.isPlaying ? 1 : 0, data.time, data.duration);
                                    }
                                    return;
                                }

                                if (data.type === 'duta:success') {
                                    window.videoFound = true;
                                    if (!window.successNotified && window.AndroidPlayer) {
                                        window.successNotified = true;
                                        window.AndroidPlayer.notifyVideoPlaying();
                                    }
                                    return;
                                }

                                var cmd = data.type || data.method || data.action || (data.func ? data.func : '');
                                if (cmd === 'play' || cmd === 'playVideo' || cmd === 'duta:play') {
                                    if (window.jwplayer && typeof window.jwplayer === 'function') {
                                        try { window.jwplayer().play(); } catch(e){}
                                    }
                                    var vid = document.querySelector('video');
                                    if (vid && vid.play) { vid.play().catch(function(){}); }
                                    if (window.videojs) {
                                        try {
                                            var players = window.videojs.getPlayers ? window.videojs.getPlayers() : {};
                                            for (var p in players) { if (players[p] && players[p].play) players[p].play(); }
                                        } catch(e){}
                                    }
                                } else if (cmd === 'pause' || cmd === 'pauseVideo' || cmd === 'duta:pause') {
                                    if (window.jwplayer && typeof window.jwplayer === 'function') {
                                        try { window.jwplayer().pause(); } catch(e){}
                                    }
                                    var vid = document.querySelector('video');
                                    if (vid && vid.pause) { vid.pause(); }
                                    if (window.videojs) {
                                        try {
                                            var players = window.videojs.getPlayers ? window.videojs.getPlayers() : {};
                                            for (var p in players) { if (players[p] && players[p].pause) players[p].pause(); }
                                        } catch(e){}
                                    }
                                } else if (cmd === 'seek' || cmd === 'seekTo' || cmd === 'duta:seek' || cmd === 'player:seek') {
                                    var targetTime = (typeof data.value !== 'undefined') ? data.value : 
                                                     ((typeof data.time !== 'undefined') ? data.time : 
                                                     ((data.args && data.args.length > 0) ? data.args[0] : 0));
                                    targetTime = parseFloat(targetTime);
                                    if (!isNaN(targetTime)) {
                                        if (window.jwplayer && typeof window.jwplayer === 'function') {
                                            try { window.jwplayer().seek(targetTime); } catch(e){}
                                        }
                                        var vid = document.querySelector('video');
                                        if (vid) { vid.currentTime = targetTime; }
                                        if (window.videojs) {
                                            try {
                                                var players = window.videojs.getPlayers ? window.videojs.getPlayers() : {};
                                                for (var p in players) { if (players[p] && players[p].currentTime) players[p].currentTime(targetTime); }
                                            } catch(e){}
                                        }
                                    }
                                }
                            } catch(err){}
                        });
                    }

                    var unhidePlayerAncestors = function() {
                        try {
                            var targets = document.querySelectorAll('video, .jwplayer, .video-js, iframe');
                            for (var t = 0; t < targets.length; t++) {
                                var el = targets[t];
                                if (el.tagName === 'IFRAME') {
                                    var isEmbed = false;
                                    try {
                                        var src = (el.src || "").toLowerCase();
                                        if (src.indexOf('player') !== -1 || src.indexOf('embed') !== -1 || src.indexOf('voe') !== -1 || 
                                            src.indexOf('stream') !== -1 || src.indexOf('hgcloud') !== -1 || src.indexOf('hglink') !== -1 ||
                                            src.indexOf('swhoi') !== -1 || src.indexOf('ohio') !== -1 || src.indexOf('veev') !== -1 ||
                                            src.indexOf('youtube') !== -1 || src.indexOf('youtu.be') !== -1) {
                                            isEmbed = true;
                                        }
                                    } catch(e) {}
                                    if (!isEmbed) continue;
                                    el.classList.add('nuker-active-frame');
                                }
                                el.style.setProperty('opacity', '1', 'important');
                                el.style.setProperty('visibility', 'visible', 'important');
                                if (el.style.display === 'none') el.style.setProperty('display', 'block', 'important');

                                var p = el.parentElement;
                                while (p && p !== document.body && p !== document.documentElement) {
                                    p.classList.add('nuker-video-container');
                                    p.style.setProperty('opacity', '1', 'important');
                                    p.style.setProperty('visibility', 'visible', 'important');
                                    if (p.style.display === 'none') p.style.setProperty('display', 'block', 'important');
                                    p = p.parentElement;
                                }
                            }
                        } catch(e) {}
                    };

                    (function applyStyles() {
                        try {
                            if (!window.nukerStyle) {
                                window.nukerStyle = document.createElement('style');
                                var target = document.head || document.documentElement;
                                if (target) {
                                    target.appendChild(window.nukerStyle);
                                    window.nukerStyle.innerHTML = `
                                        body, html { 
                                            background: #000000 !important; 
                                            margin: 0 !important; 
                                            padding: 0 !important; 
                                            overflow: hidden !important; 
                                            width: 100% !important; 
                                            height: 100% !important; 
                                        }
                                        header, footer, nav, aside, 
                                        .header, .footer, .navbar, .sidebar, .sidebar-wrapper,
                                        .comments, .comment-box, #comments, #disqus_thread,
                                        .related, .related-posts, .muvipro-player-tabs, .player-tabs, .gmr-player-nav,
                                        .ads, .ad-box, .ads-box, .ad-wrapper, .banner, .announcement,
                                        ins.adsbygoogle, div[id*="google_ads"], div[id*="aswift"],
                                        iframe[src*="google"], iframe[src*="doubleclick"],
                                        .gmr-trailer-popup, .trailer-box, .trailer-wrapper,
                                        .ad-container, #ad-container, #player-ads, .player-ads,
                                        #overlay, #playback, #overlay *, #playback *,
                                        div#overlay, div#playback,
                                        div[class*="playerp2p-ad"], div[class*="ad-overlay"], div[id*="ad-overlay"],
                                        div[class*="pop-under"], div[class*="floating-ad"],
                                        .jw-preview, .vjs-poster,
                                        #videoInfo, .video-info, [id*="videoInfo"], [class*="video-info"],
                                        .video-info-title, .video-info-hint, .video-info-close,
                                        div#videoInfo, div.video-info,
                                        #sidebar, #header, #footer {
                                            display: none !important;
                                            opacity: 0 !important; 
                                            visibility: hidden !important; 
                                            pointer-events: none !important;
                                            height: 0 !important;
                                            max-height: 0 !important;
                                            overflow: hidden !important;
                                            z-index: -99999 !important;
                                        }
                                        video { 
                                            display: block !important; 
                                            visibility: visible !important; 
                                            opacity: 1 !important;
                                            position: fixed !important; 
                                            top: 0 !important; 
                                            left: 0 !important;
                                            width: 100vw !important; 
                                            height: 100vh !important; 
                                            max-width: 100vw !important;
                                            max-height: 100vh !important;
                                            z-index: 999990 !important;
                                            object-fit: contain !important;
                                            background: transparent !important;
                                        }
                                        .jwplayer, .video-js, .nuker-video-container {
                                            display: block !important;
                                            visibility: visible !important;
                                            opacity: 1 !important;
                                        }
                                        .jwplayer, .video-js {
                                            position: fixed !important; 
                                            top: 0 !important; 
                                            left: 0 !important;
                                            width: 100vw !important; 
                                            height: 100vh !important; 
                                            max-width: 100vw !important;
                                            max-height: 100vh !important;
                                            z-index: 999990 !important;
                                        }
                                        iframe.nuker-active-frame {
                                            position: fixed !important;
                                            top: 0 !important;
                                            left: 0 !important;
                                            width: 100vw !important;
                                            height: 100vh !important;
                                            z-index: 999980 !important;
                                            display: block !important;
                                            visibility: visible !important;
                                            opacity: 1 !important;
                                            border: none !important;
                                        }
                                        .jwplayer *, .video-js * {
                                            visibility: visible;
                                        }
                                        /* Big play buttons and overlays must NEVER be visible - unconditional */
                                        .jw-display-icon-display,
                                        .jw-display-icon-container,
                                        .jw-display-icon-idle,
                                        .jw-display,
                                        .jw-icon-display,
                                        .jw-svg-icon-play,
                                        .jw-flag-fullscreen .jw-display-icon-display,
                                        .vjs-big-play-button,
                                        .vjs-big-play-button-mobile,
                                        .play-button,
                                        #play-button,
                                        .play-btn,
                                        #play-btn,
                                        .big-play,
                                        .big-play-btn,
                                        .big-play-button,
                                        .large-play-button,
                                        .ytp-large-play-button,
                                        .play-overlay,
                                        #overlay,
                                        #playback,
                                        #overlay *,
                                        #playback *,
                                        div#overlay,
                                        div#playback,
                                        svg[viewBox="0 0 24 24"],
                                        svg[viewBox="0 0 240 240"] {
                                            display: none !important;
                                            opacity: 0 !important;
                                            visibility: hidden !important;
                                            pointer-events: none !important;
                                            width: 0 !important;
                                            height: 0 !important;
                                            max-width: 0 !important;
                                            max-height: 0 !important;
                                            z-index: -99999 !important;
                                        }
                                    `;
                                }
                            }
                            if (window.videoFound && document.body) {
                                document.body.classList.add('video-active');
                                unhidePlayerAncestors();
                            }
                        } catch(err) {}
                    })();

                    var runNuker = function() {
                        if (window.successNotified) return;
                        if (window.AndroidPlayer && window.AndroidPlayer.isVideoReady && window.AndroidPlayer.isVideoReady()) {
                            window.successNotified = true;
                            return;
                        }
                        window.nukerAttempts++; 
                        var elapsedMs = Date.now() - window.nukerStartTime;

                        var bodyText = ((document.body && document.body.innerText) || "").toLowerCase();

                        // v7.2+ Mirror-level 404/502/Login/Dead - Throttled Watchdog (Fast 1.0s for dead/expired mirrors)
                        if (elapsedMs > 1000) {
                             var isCritical = bodyText.indexOf('path not found') !== -1 || 
                                              bodyText.indexOf('404 not found') !== -1 ||
                                              bodyText.indexOf('502 bad gateway') !== -1 ||
                                              bodyText.indexOf('no longer available') !== -1 ||
                                              bodyText.indexOf('file is no longer available') !== -1 ||
                                              bodyText.indexOf('video is no longer available') !== -1 ||
                                              bodyText.indexOf('expired or has been deleted') !== -1 ||
                                              bodyText.indexOf('file was deleted') !== -1 ||
                                              bodyText.indexOf('has been deleted') !== -1 ||
                                              bodyText.indexOf('file deleted') !== -1 ||
                                              bodyText.indexOf('video deleted') !== -1 ||
                                              bodyText.indexOf('video not found') !== -1 ||
                                              bodyText.indexOf('media not found') !== -1 ||
                                              bodyText.indexOf('file expired') !== -1 ||
                                              bodyText.indexOf('the file you are looking for does not exist') !== -1 ||
                                              bodyText.indexOf('file not found') !== -1 ||
                                              bodyText.indexOf("can't find the file") !== -1 ||
                                              bodyText.indexOf("cant find the file") !== -1 ||
                                              bodyText.indexOf("deleted by the owner") !== -1 ||
                                              bodyText.indexOf("copyright violation") !== -1 ||
                                              bodyText.indexOf("cant give you what you looking for") !== -1 ||
                                              bodyText.indexOf("can't give you what you looking for") !== -1 ||
                                              bodyText.indexOf("this video is not available") !== -1 ||
                                              bodyText.indexOf("video not found or deleted") !== -1 ||
                                              bodyText.indexOf("video is not ready yet") !== -1 ||
                                              bodyText.indexOf("video is processing") !== -1 ||
                                              bodyText.indexOf("conversion stage") !== -1 ||
                                              bodyText.indexOf("pending in queue") !== -1 ||
                                              bodyText.indexOf("is being converted") !== -1 ||
                                              bodyText.indexOf("video is converting") !== -1 ||
                                              bodyText.indexOf("conversion queue") !== -1 ||
                                              bodyText.indexOf("cookieindex is not defined") !== -1 ||
                                              bodyText.indexOf('not a robot') !== -1 ||
                                              bodyText.indexOf('verify you are human') !== -1 ||
                                              bodyText.indexOf('searchresultsworld') !== -1 ||
                                              bodyText.indexOf('welcome to the world') !== -1 ||
                                              bodyText.indexOf('welcome to the abyss') !== -1 ||
                                              (elapsedMs > 6000 && (
                                                  (bodyText.indexOf('login') !== -1 && bodyText.length < 3000) ||
                                                  (bodyText.indexOf('sign in') !== -1 && bodyText.length < 3000) ||
                                                  bodyText.indexOf('welcome back') !== -1 ||
                                                  window.location.href.toLowerCase().indexOf('login') !== -1
                                              ));
                                              
                             if (isCritical) {
                                 if (!window.gateNotified) {
                                     if (window.AndroidPlayer && window.AndroidPlayer.notifyGateStuck) {
                                         window.AndroidPlayer.notifyGateStuck(window.location.href);
                                         window.gateNotified = true;
                                         log("Mirror critical block detected: Dead/Expired/Gate/Processing (Elapsed: " + Math.round(elapsedMs/1000) + "s)");
                                     }
                                 }
                             }
                        }

                        // v7.1 Gate Stuck & Dead Air Watchdog (8s for confirmed gates, 12s for dead air)
                        if (window.AndroidPlayer && window.AndroidPlayer.isVideoReady && window.AndroidPlayer.isVideoReady()) {
                            window.successNotified = true;
                            return;
                        }
                        var hasGate = isLandingPageGate();
                        var v = window.playerBridge ? window.playerBridge.findVideo() : null;
                        var hasRealVideo = window.videoFound && v && !v.isProxy;
                        if (hasRealVideo) {
                            if (!window.videoDetectedTime) {
                                window.videoDetectedTime = Date.now();
                            }
                        }
                        var videoAgeMs = window.videoDetectedTime ? (Date.now() - window.videoDetectedTime) : 0;
                        var isJwBuffering = false;
                        try {
                            if (window.jwplayer && typeof window.jwplayer === 'function') {
                                var jw = window.jwplayer();
                                if (jw && typeof jw.getState === 'function') {
                                    var st = jw.getState();
                                    if (st === 'buffering' || st === 'playing') isJwBuffering = true;
                                }
                            }
                        } catch(e){}

                        var isActivelyLoading = hasRealVideo && (videoAgeMs < 15000 || v.networkState === 2 || isJwBuffering);
                        var hasEmbedFrames = document.querySelectorAll('iframe[src*="player"], iframe[src*="embed"], iframe[src*="abyss"], iframe[src*="sobat"], iframe[src*="mogo"], iframe[src*="stream"], iframe[src*="vidhide"], iframe[src*="fujihide"]').length > 0;
                        var isDeadAir = !hasRealVideo && !hasEmbedFrames && elapsedMs > 12000;
                        var isStuckVideo = hasRealVideo && !isActivelyLoading && v.readyState === 0 && v.currentTime === 0 && !v.seeking;

                        var gateTimeoutReached = (hasGate && elapsedMs > 8000) || isDeadAir || isStuckVideo;
                        if (gateTimeoutReached && !window.successNotified) {
                             if (hasGate || isDeadAir || isStuckVideo) {
                                 if (!window.gateNotified) {
                                     if (window.AndroidPlayer && window.AndroidPlayer.notifyGateStuck) {
                                         window.AndroidPlayer.notifyGateStuck(window.location.href);
                                         window.gateNotified = true;
                                         log((hasGate ? "Gate stuck (Landing Page Gate)" : (isStuckVideo ? "Stuck Video" : "Dead Air detected")) + " after " + Math.round(elapsedMs/1000) + "s");
                                     }
                                 }
                             }
                        }

                        if (window.nukerAttempts < 150) {
                              // 1. Text-based Hunter
                              var all = document.querySelectorAll('button, a, .button, [role="button"]');
                              for(var i=0; i<Math.min(all.length, 80); i++) {
                                  var el = all[i];
                                  if (!el) continue;
                                  // CRITICAL: NEVER click navigation links or anything inside nav / server tabs
                                  if (el.tagName === 'A') {
                                      var href = (el.getAttribute('href') || "").trim();
                                      if (href && href !== '#' && href.indexOf('javascript:') !== 0) continue;
                                  }
                                  if (el.closest && el.closest('.muvipro-player-tabs, .player-tabs, .gmr-player-nav, .server-list, nav, header, footer')) continue;
                                  var txt = (el.innerText || el.textContent || "").toLowerCase();
                                  if (txt.indexOf('informasi video') !== -1 || txt.indexOf('ganti player pada') !== -1 || txt.indexOf('klik pesan ini untuk menutup') !== -1) {
                                      try { el.style.display = 'none'; el.remove(); } catch(e){}
                                      continue;
                                  }
                                  if (txt.indexOf('trailer') !== -1) continue;
                                  if (txt.indexOf('play video') !== -1 || 
                                      txt.indexOf('continue to') !== -1 || txt.indexOf('watch now') !== -1 ||
                                      txt.indexOf('close') !== -1 || txt.indexOf('dismiss') !== -1 ||
                                      txt.indexOf('verify') !== -1 || txt.indexOf('human') !== -1 ||
                                      txt.indexOf('proceed') !== -1 || txt.indexOf('click here') !== -1) {
                                      if (window.clickedRegistry.indexOf(el) === -1) {
                                          try { 
                                              el.click(); 
                                              window.clickedRegistry.push(el); 
                                              var idOrClass = el.id ? ("#" + el.id) : (el.className ? ("." + el.className.split(' ').join('.')) : "unknown");
                                              log("Clicked text trigger [" + idOrClass + "]: " + txt); 
                                          } catch(e){}
                                      }
                                  }
                              }

                               // 2. Class-based Hunter (STRICTLY for video play buttons and close overlays - NEVER server tabs)
                               var sel = '.vjs-big-play-button, .play-button, #play-button, .jw-display-icon-display, .ytp-large-play-button, .close-button, .close, .btn-close, .modal-close, .idks-close, .play-overlay';
                               var elements = document.querySelectorAll(sel);
                               for(var j=0; j<elements.length; j++) {
                                   var target = elements[j];
                                   if (target && window.clickedRegistry.indexOf(target) === -1) {
                                       // CRITICAL: NEVER click navigation links or anything inside nav / server tabs
                                       if (target.tagName === 'A') {
                                           var tHrefVal = (target.getAttribute('href') || "").trim();
                                           if (tHrefVal && tHrefVal !== '#' && tHrefVal.indexOf('javascript:') !== 0) continue;
                                       }
                                       if (target.closest && target.closest('.muvipro-player-tabs, .player-tabs, .gmr-player-nav, .server-list, nav, header, footer')) continue;
                                       var cName = (target.className || "").toString().toLowerCase();
                                       var tText = (target.innerText || target.textContent || "").toLowerCase();
                                       var tHref = (target.getAttribute('href') || "").toLowerCase();
                                       if (cName.indexOf('trailer') !== -1 || tText.indexOf('trailer') !== -1 || tHref.indexOf('trailer') !== -1 || tHref.indexOf('youtube') !== -1) continue;
                                       if (target.classList && (target.classList.contains('jw-display-icon-next') || target.classList.contains('jw-display-icon-rewind'))) continue;
                                       try { target.click(); window.clickedRegistry.push(target); log("Clicked class trigger: " + target.className); } catch(e){}
                                   }
                               }
                              // 3. Floating Ad/Overlay Killer
                              var overlays = ['#overlay', '#playback', 'div#overlay', 'div#playback', '#videoInfo', '.video-info', '[id*="videoInfo"]', '[class*="video-info"]', '.video-info-title', '.video-info-hint', '.video-info-close', '.ad-overlay', '.ads-overlay', '.pop-overlay', '.banner-overlay', '.float-overlay', '.modal-backdrop', 'div[class*="ad-overlay"]', 'div[id*="ad-overlay"]', 'img[src*=\"dm21\"]', '.dm21', '#dm21', '.ads-box', '.ad-wrapper', 'img[src*=\"idks\"]', 'div[id*=\"idks\"]', '.idks', 'ins.adsbygoogle', 'div[id*=\"aswift\"]', 'div[id*=\"google_ads\"]', '.ad-container', '#ad-container', '#player-ads', '.player-ads', 'div[class*=\"playerp2p-ad\"]', 'div[class*=\"pop-under\"]', 'div[class*=\"floating-ad\"]'];
                              overlays.forEach(function(s) {
                                  try {
                                      var elements = document.querySelectorAll(s);
                                      for (var k = 0; k < elements.length; k++) {
                                          var elAd = elements[k];
                                          if (!elAd) continue;
                                          // CRITICAL: NEVER kill or hide any element belonging to a video player!
                                          var isPlayerElement = false;
                                          try {
                                              if (elAd.closest && (elAd.closest('.jwplayer') || elAd.closest('.video-js') || elAd.closest('video') || elAd.closest('.jw-reset'))) {
                                                  isPlayerElement = true;
                                              }
                                              var cName = (elAd.className || "").toString();
                                              var elId = (elAd.id || "").toString();
                                              if (cName.indexOf('jw-') !== -1 || cName.indexOf('vjs-') !== -1 || elId.indexOf('jw-') !== -1 || elId.indexOf('vjs-') !== -1) {
                                                  isPlayerElement = true;
                                              }
                                          } catch(e) {}
                                          if (isPlayerElement) continue;

                                          log("Found potential overlay: " + s);
                                          if (elAd.tagName === 'IMG' && elAd.parentElement) {
                                               var closeBtn = elAd.parentElement.querySelector('a, button, div[class*=\"close\"], span[class*=\"close\"]');
                                               if (closeBtn) closeBtn.click();
                                          }
                                          log("Killed/Hid overlay element: " + s);
                                          elAd.style.display = 'none'; elAd.style.opacity = '0'; elAd.style.pointerEvents = 'none';
                                      }
                                  } catch(e){} 
                              });
                              try {
                                  var killBadInDoc = function(d) {
                                      if (!d) return;
                                      try {
                                          if (typeof d.defaultView !== 'undefined' && typeof d.defaultView.closeVideoInfo === 'function') {
                                              try { d.defaultView.closeVideoInfo(); } catch(e){}
                                          }
                                          var badOverlays = d.querySelectorAll('#overlay, #playback, div#overlay, div#playback, .jw-display-icon-display, .jw-display-icon-container, .jw-display-icon-idle, .jw-icon-display, .jw-display, .jw-svg-icon-play, .vjs-big-play-button, .play-button, #play-button, .play-btn, .big-play-button, #videoInfo, .video-info, [id*="videoInfo"], [class*="video-info"], svg[viewBox="0 0 24 24"], svg[viewBox="0 0 240 240"]');
                                          for (var bo = 0; bo < badOverlays.length; bo++) {
                                              badOverlays[bo].style.setProperty('display', 'none', 'important');
                                              badOverlays[bo].style.setProperty('opacity', '0', 'important');
                                              badOverlays[bo].style.setProperty('visibility', 'hidden', 'important');
                                              badOverlays[bo].style.setProperty('pointer-events', 'none', 'important');
                                              badOverlays[bo].style.setProperty('width', '0', 'important');
                                              badOverlays[bo].style.setProperty('height', '0', 'important');
                                              try { badOverlays[bo].remove(); } catch(e){}
                                          }
                                      } catch(e){}
                                  };
                                  killBadInDoc(document);
                                  var frames = document.querySelectorAll('iframe');
                                  for (var fIdx = 0; fIdx < frames.length; fIdx++) {
                                      try {
                                          var fDoc = frames[fIdx].contentDocument || (frames[fIdx].contentWindow && frames[fIdx].contentWindow.document);
                                          killBadInDoc(fDoc);
                                      } catch(e){}
                                  }
                              } catch(e) {}
                              
                              // 4. Center Clicker
                              if (!window.successNotified && window.nukerAttempts % 10 === 0) {
                                  var cx = window.innerWidth / 2; var cy = window.innerHeight / 2;
                                  var e = new MouseEvent('click', { 'view': window, 'bubbles': true, 'cancelable': true, 'clientX': cx, 'clientY': cy });
                                  var elAt = document.elementFromPoint(cx, cy);
                                  if (elAt && window.clickedRegistry.indexOf(elAt) === -1) { 
                                      try { elAt.dispatchEvent(e); window.clickedRegistry.push(elAt); } catch(e){}
                                  }
                              }
                         }
                         if (window.videoFound && document.body) {
                             document.body.classList.add('video-active');
                             unhidePlayerAncestors();
                         }
                         ensureBlackBackground();
                     };
                     setInterval(runNuker, $intervalMs);
                 } catch(e) {}
             })();
        """
    }

    /**
     * Dedicated Owl's Eye Standalone Player Method for Malaysia (PencuriMovie).
     * Tailored specifically for VOE / johnfullwonder, IndoStream, HGLink, and clean web embeds.
     * Prevents aggressive ad-hunting and random clicking, keeps video frames fully transparent
     * and visible, hides web controls in favor of native Compose controls, and hooks directly
     * into the JWPlayer and HTML5 video APIs.
     */
    fun getPencuriScript(intervalMs: Int = 400): String {
        return """
            (function() {
                try {
                    if (window.pmNukerRunning) return;
                    window.pmNukerRunning = true;

                    var log = function(msg) {
                        if (window.AndroidPlayer && window.AndroidPlayer.log) {
                            window.AndroidPlayer.log("Owl's Eye PM: " + msg);
                        }
                    };

                    window.nukerAttempts = 0;
                    window.nukerStartTime = Date.now();
                    window.videoFound = false;
                    window.successNotified = false;
                    window.gateNotified = false;
                    window.clickedRegistry = [];

                    // Popup & redirect neutralization
                    (function neutralize() {
                        var safeSet = function(obj, prop, val) {
                            try {
                                Object.defineProperty(obj, prop, { value: val, writable: false, configurable: true });
                            } catch(e) { try { obj[prop] = val; } catch(err) {} }
                        };
                        var noop = function() { return null; };
                        safeSet(window, 'ga', noop); safeSet(window, 'fbq', noop); safeSet(window, 'gtag', noop);
                        safeSet(window, 'ym', noop); safeSet(window, 'histats', { start: noop });
                        window._pop = noop; window.pop_show = noop; window.pop_count = 0;
                        window.ad_show = noop; window.ad_count = 0; window.open_pop = noop;
                        window.show_popup = noop; window._top = window; window.open = noop;
                    })();

                    var ensureBlackBackground = function() {
                        try {
                            if (document.documentElement) {
                                document.documentElement.style.setProperty('background', '#000', 'important');
                                document.documentElement.style.setProperty('background-color', '#000', 'important');
                            }
                            if (document.body) {
                                document.body.style.setProperty('background', '#000', 'important');
                                document.body.style.setProperty('background-color', '#000', 'important');
                            }
                            var fms = document.querySelectorAll('iframe');
                            for (var fi = 0; fi < fms.length; fi++) {
                                try {
                                    var fDoc = fms[fi].contentDocument || (fms[fi].contentWindow && fms[fi].contentWindow.document);
                                    if (fDoc) {
                                        if (fDoc.documentElement) {
                                            fDoc.documentElement.style.setProperty('background', '#000', 'important');
                                            fDoc.documentElement.style.setProperty('background-color', '#000', 'important');
                                        }
                                        if (fDoc.body) {
                                            fDoc.body.style.setProperty('background', '#000', 'important');
                                            fDoc.body.style.setProperty('background-color', '#000', 'important');
                                        }
                                    }
                                } catch(e) {}
                            }
                        } catch(e) {}
                    };
                    ensureBlackBackground();

                                        var isLandingPageGate = function() {
                        try {
                            if (window.location.href.toLowerCase().indexOf('login') !== -1 || 
                                window.location.href.toLowerCase().indexOf('register') !== -1) return true;

                            var inputs = document.querySelectorAll('input[type="password"], input[type="email"], input[name*="user"], input[name*="pass"]');
                            if (inputs.length >= 2) return true;

                            var sel = '.fujihide-play, .click-gate, .player-click-gate, img[src*="dm21"], img[src*="no_video"], img[src*="deleted"], .vjs-error-display, .vjs-error, .get-started, #btn-login';
                            var gateEl = document.querySelector(sel);
                            if (gateEl) return true;
                            
                            var bodyText = ((document.body && (document.body.innerText || document.body.textContent)) || "").toLowerCase();
                            if (bodyText.indexOf('path not found') !== -1 || 
                                bodyText.indexOf('404 not found') !== -1 ||
                                bodyText.indexOf('502 bad gateway') !== -1 ||
                                bodyText.indexOf('welcome back') !== -1 ||
                                bodyText.indexOf('please login') !== -1 ||
                                bodyText.indexOf('sign in to') !== -1 ||
                                bodyText.indexOf('member login') !== -1 ||
                                bodyText.indexOf('create account') !== -1 ||
                                bodyText.indexOf('no longer available') !== -1 ||
                                bodyText.indexOf('expired or has been deleted') !== -1 ||
                                bodyText.indexOf('file was deleted') !== -1 ||
                                bodyText.indexOf('the file you are looking for does not exist') !== -1 ||
                                bodyText.indexOf('file not found') !== -1 ||
                                bodyText.indexOf('video not found') !== -1 ||
                                bodyText.indexOf('video was deleted') !== -1 ||
                                bodyText.indexOf('media could not be loaded') !== -1 ||
                                bodyText.indexOf('format is not supported') !== -1 ||
                                bodyText.indexOf('server or network failed') !== -1 ||
                                bodyText.indexOf('file has been removed') !== -1 ||
                                bodyText.indexOf('not a robot') !== -1 ||
                                bodyText.indexOf('i am not a robot') !== -1 ||
                                bodyText.indexOf("i'm not a robot") !== -1 ||
                                bodyText.indexOf('verify you are human') !== -1 ||
                                bodyText.indexOf('click allow') !== -1 ||
                                bodyText.indexOf('press allow') !== -1 ||
                                bodyText.indexOf('searchresultsworld') !== -1) return true;

                            var btns = document.querySelectorAll('button, a, .button, h1, h2, h3, p, span, div');
                            for(var i=0; i < Math.min(btns.length, 120); i++) {
                                var b = btns[i];
                                if (!b) continue;
                                var t = (b.innerText || b.textContent || "").toLowerCase();
                                if (t.indexOf('get started') !== -1 ||
                                    t.indexOf('welcome back') !== -1 || t.indexOf('sign in') !== -1 || t.indexOf('login') !== -1 ||
                                    t.indexOf('register') !== -1 || t.indexOf('sign up') !== -1 || 
                                    t.indexOf('verify you are human') !== -1 ||
                                    t.indexOf('not a robot') !== -1 || t.indexOf('i am not a robot') !== -1 ||
                                    t.indexOf("i'm not a robot") !== -1 || t.indexOf('click allow') !== -1 ||
                                    t.indexOf('press allow') !== -1) return true;
                            }
                        } catch(e) {}
                        return false;
                    };

                    var checkDeadInside = function(root) {
                        if (!root) return false;
                        try {
                            if (root.querySelector('img[src*="no_video"], img[src*="deleted"], img[src*="error"], svg.no-video, .no-video')) return true;
                            var errBox = root.querySelector('.vjs-error-display, .vjs-error');
                            if (errBox && (errBox.offsetParent !== null || (errBox.innerText && errBox.innerText.trim().length > 0))) return true;
                            var v = root.querySelector('video');
                            if (v && v.error) return true;
                            var iframes = root.querySelectorAll('iframe');
                            for (var i = 0; i < iframes.length; i++) {
                                try {
                                    var d = iframes[i].contentDocument || iframes[i].contentWindow.document;
                                    if (d && checkDeadInside(d)) return true;
                                } catch(e){}
                            }
                        } catch(e){}
                        return false;
                    };

                    var killPopups = function() {
                        if (window.successNotified) return;
                        try {
                            var rogueFrames = document.querySelectorAll('iframe:not(.nuker-active-frame)');
                            for (var rf = 0; rf < rogueFrames.length; rf++) {
                                var frame = rogueFrames[rf];
                                var fSrc = (frame.src || "").toLowerCase();
                                var isPlayerFrame = fSrc.indexOf('player') !== -1 || fSrc.indexOf('embed') !== -1 || 
                                                    fSrc.indexOf('voe') !== -1 || fSrc.indexOf('stream') !== -1 || 
                                                    fSrc.indexOf('dood') !== -1 || fSrc.indexOf('playmogo') !== -1 ||
                                                    fSrc.indexOf('abyss') !== -1 || fSrc.indexOf('bond') !== -1;
                                if (!isPlayerFrame && frame.parentNode) {
                                    frame.parentNode.removeChild(frame);
                                }
                            }
                            var robotBadElements = document.querySelectorAll('div, p, span, h1, h2, h3, h4, a, button, section, aside');
                            for (var rbe = 0; rbe < robotBadElements.length; rbe++) {
                                var elem = robotBadElements[rbe];
                                if (!elem || elem.tagName === 'VIDEO' || elem.classList.contains('video-js') || elem.classList.contains('jwplayer')) continue;
                                var txt = (elem.innerText || elem.textContent || "").toLowerCase();
                                if (txt.indexOf('not a robot') !== -1 || txt.indexOf('verify you are human') !== -1 ||
                                    txt.indexOf('click allow') !== -1 || txt.indexOf('press allow') !== -1 ||
                                    txt.indexOf('enable notification') !== -1 ||
                                    txt.indexOf('informasi video') !== -1 || txt.indexOf('ganti player pada') !== -1 ||
                                    txt.indexOf('klik pesan ini untuk menutup') !== -1) {
                                    var container = elem.closest('div') || elem;
                                    if (container && container !== document.body && container !== document.documentElement && container.parentNode) {
                                        container.style.setProperty('display', 'none', 'important');
                                        container.parentNode.removeChild(container);
                                    }
                                }
                            }
                            var badPms = document.querySelectorAll('#overlay, #playback, div#overlay, div#playback, #videoInfo, .video-info, [id*="videoInfo"], [class*="video-info"]');
                            for (var bi = 0; bi < badPms.length; bi++) {
                                badPms[bi].remove();
                            }
                            if (typeof window.closeVideoInfo === 'function') {
                                try { window.closeVideoInfo(); } catch(e) {}
                            }
                        } catch(e) {}
                    };

                    // Player Bridge for Native Compose Controls
                    if (!window.playerBridge) {
                        window.playerBridge = {
                            heartbeat: function() {
                                if (window.AndroidPlayer) {
                                    var v = this.findVideo();
                                    var status = v ? (v.isProxy ? "Proxy Active" : ("Video Found (" + v.readyState + ")")) : "Video NOT Found";
                                    log("PM Heartbeat: " + status + " | At: " + window.nukerAttempts);
                                    var isBlank = window.location.href === "about:blank" || !document.body || document.body.children.length === 0;
                                    var maxAttempts = isBlank ? 6 : 20;
                                    if (status === "Video NOT Found" && window.nukerAttempts >= maxAttempts && !window.gateNotified && !window.successNotified) {
                                        if (window.AndroidPlayer && window.AndroidPlayer.isVideoReady && window.AndroidPlayer.isVideoReady()) {
                                            window.successNotified = true;
                                            return;
                                        }
                                        log("PM Heartbeat Watchdog: Video NOT Found after " + window.nukerAttempts + " attempts (isBlank=" + isBlank + "). Triggering rotation.");
                                        window.gateNotified = true;
                                        if (window.AndroidPlayer.notifyGateStuck) {
                                            window.AndroidPlayer.notifyGateStuck(window.location.href);
                                        }
                                    }
                                }
                            },
                            findVideo: function() {
                                if (window._cachedVideo && window._cachedVideo.isConnected && !window._cachedVideo.isProxy) {
                                    return window._cachedVideo;
                                }
                                var find = function(root) {
                                    if (!root) return null;
                                    try {
                                        var v = root.querySelector('video');
                                        if (v) { window._cachedVideo = v; return v; }
                                        var iframes = root.querySelectorAll('iframe, embed, object');
                                        for (var i = 0; i < iframes.length; i++) {
                                            try {
                                                var d = iframes[i].contentDocument || iframes[i].contentWindow.document;
                                                if (d) { var found = find(d); if (found) { window._cachedVideo = found; return found; } }
                                            } catch(e) {
                                                var src = (iframes[i].src || "").toLowerCase();
                                                if (src.indexOf('player') !== -1 || src.indexOf('embed') !== -1 || src.indexOf('voe') !== -1 || 
                                                    src.indexOf('swhoi') !== -1 || src.indexOf('veev') !== -1 || src.indexOf('hglink') !== -1 ||
                                                    src.indexOf('hgcloud') !== -1 || src.indexOf('audinifer') !== -1 || src.indexOf('indostream') !== -1 ||
                                                    src.indexOf('youtube') !== -1 || src.indexOf('youtu.be') !== -1) {
                                                    return { isProxy: true, paused: false, readyState: 1, currentTime: 0.1, duration: 0 };
                                                }
                                            }
                                        }
                                    } catch(e) {}
                                    return null;
                                };
                                return find(document);
                            },
                            broadcastToFrames: function(msg) {
                                try {
                                    var ifrs = document.querySelectorAll('iframe');
                                    for (var i = 0; i < ifrs.length; i++) {
                                        try {
                                            ifrs[i].contentWindow.postMessage(msg, '*');
                                            ifrs[i].contentWindow.postMessage(JSON.stringify(msg), '*');
                                        } catch(e){}
                                    }
                                } catch(err){}
                            },
                            reportState: function(isPlaying, time, duration) {
                                try {
                                    if (window.AndroidPlayer && window.AndroidPlayer.onPlayerState) {
                                        window.AndroidPlayer.onPlayerState(isPlaying ? 1 : 0, time, duration);
                                    }
                                } catch(e){}
                                try {
                                    if (window.parent && window.parent !== window) {
                                        window.parent.postMessage({
                                            type: 'duta:state',
                                            isPlaying: isPlaying,
                                            time: time,
                                            duration: duration
                                        }, '*');
                                    }
                                } catch(e){}
                            },
                            play: function() {
                                try {
                                    var v = this.findVideo();
                                    if (v && !v.isProxy && v.play) v.play().catch(function(){});
                                    if (window.jwplayer && typeof window.jwplayer === 'function') {
                                        try { window.jwplayer().play(); } catch(e){}
                                    }
                                    if (window.videojs) {
                                        try {
                                            var players = window.videojs.getPlayers ? window.videojs.getPlayers() : {};
                                            for (var p in players) { if (players[p] && players[p].play) players[p].play(); }
                                        } catch(e){}
                                    }
                                    this.broadcastToFrames({ type: 'duta:play', method: 'play', event: 'command', func: 'playVideo', args: [] });
                                } catch(e){}
                            },
                            pause: function() {
                                try {
                                    var v = this.findVideo();
                                    if (v && !v.isProxy && v.pause) v.pause();
                                    if (window.jwplayer && typeof window.jwplayer === 'function') {
                                        try { window.jwplayer().pause(); } catch(e){}
                                    }
                                    if (window.videojs) {
                                        try {
                                            var players = window.videojs.getPlayers ? window.videojs.getPlayers() : {};
                                            for (var p in players) { if (players[p] && players[p].pause) players[p].pause(); }
                                        } catch(e){}
                                    }
                                    this.broadcastToFrames({ type: 'duta:pause', method: 'pause', event: 'command', func: 'pauseVideo', args: [] });
                                } catch(e){}
                            },
                            seek: function(t) {
                                try {
                                    var v = this.findVideo();
                                    if (v && !v.isProxy) v.currentTime = t;
                                    if (window.jwplayer && typeof window.jwplayer === 'function') {
                                        try { window.jwplayer().seek(t); } catch(e){}
                                    }
                                    if (window.videojs) {
                                        try {
                                            var players = window.videojs.getPlayers ? window.videojs.getPlayers() : {};
                                            for (var p in players) { if (players[p] && players[p].currentTime) players[p].currentTime(t); }
                                        } catch(e){}
                                    }
                                    this.broadcastToFrames({ type: 'duta:seek', time: t, value: t, method: 'seek', event: 'command', func: 'seekTo', args: [t, true] });
                                    if (v && !v.isProxy) {
                                        this.reportState(1, t, v.duration || 0);
                                    }
                                } catch(e){}
                            },
                            sync: function() {
                                try {
                                    var v = this.findVideo();
                                    if (v && window.AndroidPlayer) {
                                        window.videoFound = true;

                                        // 1. Direct JWPlayer API hook
                                        if (window.jwplayer && typeof window.jwplayer === 'function') {
                                            try {
                                                var jw = window.jwplayer();
                                                if (jw && typeof jw.getState === 'function') {
                                                    var jwState = jw.getState();
                                                    if (jwState !== 'playing' && jwState !== 'buffering') {
                                                        jw.play();
                                                    }
                                                    if (!window.jwHooked && typeof jw.on === 'function') {
                                                        window.jwHooked = true;
                                                        var self = this;
                                                        jw.on('play', function() {
                                                            window.videoFound = true;
                                                            if (document.body) {
                                                                document.body.classList.add('video-active', 'video-playing');
                                                            }
                                                            try {
                                                                var pOverlays = document.querySelectorAll('.jw-display-icon-display, .jw-display-icon-container, .jw-display-icon-idle, .jw-icon-display, .jw-display, .jw-svg-icon-play, .vjs-big-play-button, .play-button, #play-button, .play-btn, .big-play-button, #playback, #overlay, div#playback, div#overlay, svg[viewBox="0 0 24 24"], svg[viewBox="0 0 240 240"]');
                                                                for (var pi = 0; pi < pOverlays.length; pi++) {
                                                                    pOverlays[pi].style.setProperty('display', 'none', 'important');
                                                                    pOverlays[pi].style.setProperty('opacity', '0', 'important');
                                                                    pOverlays[pi].style.setProperty('visibility', 'hidden', 'important');
                                                                    pOverlays[pi].style.setProperty('pointer-events', 'none', 'important');
                                                                    pOverlays[pi].style.setProperty('width', '0', 'important');
                                                                    pOverlays[pi].style.setProperty('height', '0', 'important');
                                                                }
                                                            } catch(e){}
                                                            if (!window.successNotified && window.AndroidPlayer) {
                                                                window.successNotified = true;
                                                                log("JWPlayer onPlay fired");
                                                                window.AndroidPlayer.notifyVideoPlaying();
                                                            }
                                                            self.reportState(true, jw.getPosition ? jw.getPosition() : 0.1, jw.getDuration ? jw.getDuration() : 0);
                                                        });
                                                        jw.on('pause', function() {
                                                            self.reportState(false, jw.getPosition ? jw.getPosition() : 0.1, jw.getDuration ? jw.getDuration() : 0);
                                                        });
                                                        jw.on('time', function(e) {
                                                            if (e && e.currentTime > 0.3) {
                                                                window.videoFound = true;
                                                                if (document.body) {
                                                                    document.body.classList.add('video-active', 'video-playing');
                                                                }
                                                                if (!window.successNotified && window.AndroidPlayer) {
                                                                    window.successNotified = true;
                                                                    window.AndroidPlayer.notifyVideoPlaying();
                                                                }
                                                                self.reportState(true, e.currentTime || 0.1, e.duration || (jw.getDuration ? jw.getDuration() : 0));
                                                            }
                                                        });
                                                    }
                                                    if (jwState === 'playing') {
                                                        var jwPos = jw.getPosition ? jw.getPosition() : 0.1;
                                                        var jwDur = jw.getDuration ? jw.getDuration() : 0;
                                                        if (jwPos > 0.3 && !window.successNotified && window.AndroidPlayer) {
                                                            window.successNotified = true;
                                                            log("JWPlayer playing - notifying success");
                                                            window.AndroidPlayer.notifyVideoPlaying();
                                                        }
                                                        this.reportState(true, jwPos, jwDur);
                                                    }
                                                }
                                            } catch(e){}
                                        }

                                        // 2. Direct HTML5 Video element hook
                                        if (!v.isProxy) {
                                            if (!v._dutaHooked) {
                                                v._dutaHooked = true;
                                                var self = this;
                                                v.addEventListener('timeupdate', function() {
                                                    self.reportState(!v.paused, v.currentTime, v.duration || 0);
                                                });
                                                v.addEventListener('play', function() {
                                                    self.reportState(true, v.currentTime, v.duration || 0);
                                                });
                                                v.addEventListener('pause', function() {
                                                    self.reportState(false, v.currentTime, v.duration || 0);
                                                });
                                            }
                                            var isDead = false;
                                            if (!window.successNotified) {
                                                isDead = checkDeadInside(document) || isLandingPageGate();
                                            } else if (v.error) {
                                                isDead = true;
                                            }
                                            var hasDeadImage = !window.successNotified && !!document.querySelector('img[src*="no_video"], img[src*="deleted"], svg.no-video, .no-video');
                                            var isShortClip = !window.successNotified && ((v.duration > 0 && v.duration < 45) || (window.jwplayer && typeof window.jwplayer === 'function' && window.jwplayer().getDuration && window.jwplayer().getDuration() > 0 && window.jwplayer().getDuration() < 45));
                                            
                                            if ((isDead || hasDeadImage || isShortClip) && !window.gateNotified) {
                                                window.gateNotified = true;
                                                log("Dead or short video clip detected (dead=" + isDead + ", deadImg=" + hasDeadImage + ", isShort=" + isShortClip + ", dur=" + v.duration + "). Auto-rotating...");
                                                if (window.AndroidPlayer && window.AndroidPlayer.notifyGateStuck) {
                                                    window.AndroidPlayer.notifyGateStuck(window.location.href);
                                                }
                                                return;
                                            }

                                            if (!isDead && !hasDeadImage && !isShortClip && v.paused && !v.ended) {
                                                v.play().catch(function() {
                                                    var playBtn = document.querySelector('.jw-display-icon-display, .vjs-big-play-button, .play-button');
                                                    if (playBtn) playBtn.click();
                                                });
                                            }
                                            if (!isDead && !hasDeadImage && !isShortClip && v.currentTime > 0.5 && !window.successNotified) {
                                                window.successNotified = true;
                                                log("HTML5 Video playing - notifying success");
                                                window.AndroidPlayer.notifyVideoPlaying();
                                            }
                                            this.reportState(v.paused ? 0 : 1, v.currentTime || 0.1, v.duration || 0);
                                            if (!v.paused || v.currentTime > 0.1 || window.successNotified) {
                                                if (document.body && !document.body.classList.contains('video-active')) {
                                                    document.body.classList.add('video-active', 'video-playing');
                                                }
                                                try {
                                                    var badPOverlays = document.querySelectorAll('.jw-display-icon-display, .jw-display-icon-container, .jw-display-icon-idle, .jw-icon-display, .jw-display, .jw-svg-icon-play, .vjs-big-play-button, .play-button, #play-button, .play-btn, .big-play-button, #playback, #overlay, div#playback, div#overlay, svg[viewBox="0 0 24 24"], svg[viewBox="0 0 240 240"]');
                                                    for (var bi = 0; bi < badPOverlays.length; bi++) {
                                                        badPOverlays[bi].style.setProperty('display', 'none', 'important');
                                                        badPOverlays[bi].style.setProperty('opacity', '0', 'important');
                                                        badPOverlays[bi].style.setProperty('visibility', 'hidden', 'important');
                                                        badPOverlays[bi].style.setProperty('pointer-events', 'none', 'important');
                                                        badPOverlays[bi].style.setProperty('width', '0', 'important');
                                                        badPOverlays[bi].style.setProperty('height', '0', 'important');
                                                    }
                                                } catch(e){}
                                            }
                                        }
                                    }
                                } catch(e){}
                            }
                        };
                        setInterval(function() { window.playerBridge.sync(); }, 1000);
                        setInterval(function() { window.playerBridge.heartbeat(); }, 5000);
                    }

                    if (!window.pmDutaMsgHooked) {
                        window.pmDutaMsgHooked = true;
                        window.addEventListener('message', function(event) {
                            try {
                                var data = event.data;
                                if (typeof data === 'string') {
                                    try { data = JSON.parse(data); } catch(e){}
                                }
                                if (!data || typeof data !== 'object') return;

                                if (data.type === 'duta:state') {
                                    window.videoFound = true;
                                    if (data.time > 0.3 && !window.successNotified && window.AndroidPlayer) {
                                        window.successNotified = true;
                                        window.AndroidPlayer.notifyVideoPlaying();
                                    }
                                    if (window.AndroidPlayer && window.AndroidPlayer.onPlayerState) {
                                        window.AndroidPlayer.onPlayerState(data.isPlaying ? 1 : 0, data.time, data.duration);
                                    }
                                    return;
                                }

                                if (data.type === 'duta:success') {
                                    window.videoFound = true;
                                    if (!window.successNotified && window.AndroidPlayer) {
                                        window.successNotified = true;
                                        window.AndroidPlayer.notifyVideoPlaying();
                                    }
                                    return;
                                }

                                var cmd = data.type || data.method || data.action || (data.func ? data.func : '');
                                if (cmd === 'play' || cmd === 'playVideo' || cmd === 'duta:play') {
                                    if (window.jwplayer && typeof window.jwplayer === 'function') {
                                        try { window.jwplayer().play(); } catch(e){}
                                    }
                                    var vid = document.querySelector('video');
                                    if (vid && vid.play) { vid.play().catch(function(){}); }
                                    if (window.videojs) {
                                        try {
                                            var players = window.videojs.getPlayers ? window.videojs.getPlayers() : {};
                                            for (var p in players) { if (players[p] && players[p].play) players[p].play(); }
                                        } catch(e){}
                                    }
                                } else if (cmd === 'pause' || cmd === 'pauseVideo' || cmd === 'duta:pause') {
                                    if (window.jwplayer && typeof window.jwplayer === 'function') {
                                        try { window.jwplayer().pause(); } catch(e){}
                                    }
                                    var vid = document.querySelector('video');
                                    if (vid && vid.pause) { vid.pause(); }
                                    if (window.videojs) {
                                        try {
                                            var players = window.videojs.getPlayers ? window.videojs.getPlayers() : {};
                                            for (var p in players) { if (players[p] && players[p].pause) players[p].pause(); }
                                        } catch(e){}
                                    }
                                } else if (cmd === 'seek' || cmd === 'seekTo' || cmd === 'duta:seek' || cmd === 'player:seek') {
                                    var targetTime = (typeof data.value !== 'undefined') ? data.value : 
                                                     ((typeof data.time !== 'undefined') ? data.time : 
                                                     ((data.args && data.args.length > 0) ? data.args[0] : 0));
                                    targetTime = parseFloat(targetTime);
                                    if (!isNaN(targetTime)) {
                                        if (window.jwplayer && typeof window.jwplayer === 'function') {
                                            try { window.jwplayer().seek(targetTime); } catch(e){}
                                        }
                                        var vid = document.querySelector('video');
                                        if (vid) { vid.currentTime = targetTime; }
                                        if (window.videojs) {
                                            try {
                                                var players = window.videojs.getPlayers ? window.videojs.getPlayers() : {};
                                                for (var p in players) { if (players[p] && players[p].currentTime) players[p].currentTime(targetTime); }
                                            } catch(e){}
                                        }
                                    }
                                }
                            } catch(err){}
                        });
                    }

                    // Apply dedicated clean transparent styles
                    (function applyPmStyles() {
                        try {
                            if (!window.pmStyle) {
                                window.pmStyle = document.createElement('style');
                                var target = document.head || document.documentElement;
                                if (target) {
                                    target.appendChild(window.pmStyle);
                                    window.pmStyle.innerHTML = `
                                        body, html {
                                            background: #000000 !important;
                                            margin: 0 !important;
                                            padding: 0 !important;
                                            overflow: hidden !important;
                                            width: 100% !important;
                                            height: 100% !important;
                                        }
                                        video {
                                            display: block !important;
                                            visibility: visible !important;
                                            opacity: 1 !important;
                                            position: fixed !important;
                                            top: 0 !important;
                                            left: 0 !important;
                                            width: 100vw !important;
                                            height: 100vh !important;
                                            max-width: 100vw !important;
                                            max-height: 100vh !important;
                                            z-index: 10 !important;
                                            object-fit: contain !important;
                                            background: transparent !important;
                                        }
                                        .jwplayer, .video-js {
                                            position: fixed !important;
                                            top: 0 !important;
                                            left: 0 !important;
                                            width: 100vw !important;
                                            height: 100vh !important;
                                            max-width: 100vw !important;
                                            max-height: 100vh !important;
                                            z-index: 10 !important;
                                            background: transparent !important;
                                        }
                                        .jw-media, .jw-wrapper {
                                            background: transparent !important;
                                        }
                                        /* Hide web player control bar so native Compose controls are the sole UI */
                                        .jw-controls {
                                            opacity: 0 !important;
                                            pointer-events: none !important;
                                        }
                                        /* Hide non-player junk */
                                        .guestMode, #jw-loader, .modal-backdrop, .jw-preview, .vjs-poster {
                                            display: none !important;
                                            opacity: 0 !important;
                                            visibility: hidden !important;
                                        }
                                        /* Big play buttons and overlays must NEVER be visible - unconditional */
                                        .jw-display-icon-display,
                                        .jw-display-icon-container,
                                        .jw-display-icon-idle,
                                        .jw-display,
                                        .jw-icon-display,
                                        .jw-svg-icon-play,
                                        .jw-flag-fullscreen .jw-display-icon-display,
                                        .vjs-big-play-button,
                                        .vjs-big-play-button-mobile,
                                        .play-button,
                                        #play-button,
                                        .play-btn,
                                        #play-btn,
                                        .big-play,
                                        .big-play-btn,
                                        .big-play-button,
                                        .large-play-button,
                                        .ytp-large-play-button,
                                        .play-overlay,
                                        #overlay,
                                        #playback,
                                        #overlay *,
                                        #playback *,
                                        div#overlay,
                                        div#playback,
                                        svg[viewBox="0 0 24 24"],
                                        svg[viewBox="0 0 240 240"] {
                                            display: none !important;
                                            opacity: 0 !important;
                                            visibility: hidden !important;
                                            pointer-events: none !important;
                                            width: 0 !important;
                                            height: 0 !important;
                                            max-width: 0 !important;
                                            max-height: 0 !important;
                                            z-index: -99999 !important;
                                        }
                                        /* Aggressively eliminate ad iframes, popups, overlays, and robot verification prompts */
                                        iframe:not(.nuker-active-frame),
                                        #overlay, #playback, #overlay *, #playback *,
                                        div#overlay, div#playback,
                                        #videoInfo, .video-info, [id*="videoInfo"], [class*="video-info"],
                                        .video-info-title, .video-info-hint, .video-info-close,
                                        div#videoInfo, div.video-info,
                                        div[class*="captcha"], div[id*="captcha"],
                                        div[class*="robot"], div[id*="robot"],
                                        div[class*="verify"], div[id*="verify"],
                                        div[class*="overlay"]:not(.vjs-error-display):not(.jw-controls),
                                        div[id*="overlay"],
                                        div[id*="playback"],
                                        div[class*="popup"], div[id*="popup"],
                                        div[class*="modal"]:not(.vjs-modal-dialog), div[id*="modal"],
                                        .ad, .ads, .advertisement {
                                            display: none !important;
                                            opacity: 0 !important;
                                            pointer-events: none !important;
                                            visibility: hidden !important;
                                            z-index: -9999 !important;
                                        }
                                    `;
                                }
                            }
                        } catch(e){}
                    })();

                    var runPmNuker = function() {
                        if (window.successNotified) return;
                        window.nukerAttempts++;
                        var elapsedMs = Date.now() - window.nukerStartTime;

                        // Sweep and kill rogue ad iframes and fake captchas
                        killPopups();

                        // Dead mirror check
                        var bodyText = ((document.body && (document.body.innerText || document.body.textContent)) || "").toLowerCase();
                        if (elapsedMs > 1500) {
                            var isDeadInside = checkDeadInside(document);
                            var isDeadText = bodyText.indexOf('path not found') !== -1 ||
                                             bodyText.indexOf('404 not found') !== -1 ||
                                             bodyText.indexOf('502 bad gateway') !== -1 ||
                                             bodyText.indexOf('file was deleted') !== -1 ||
                                             bodyText.indexOf('the file you are looking for does not exist') !== -1 ||
                                             bodyText.indexOf('file not found') !== -1 ||
                                             bodyText.indexOf('video not found') !== -1 ||
                                             bodyText.indexOf('video was deleted') !== -1 ||
                                             bodyText.indexOf('expired or has been deleted') !== -1 ||
                                             bodyText.indexOf('media could not be loaded') !== -1 ||
                                             bodyText.indexOf('format is not supported') !== -1 ||
                                             bodyText.indexOf('server or network failed') !== -1 ||
                                             bodyText.indexOf('video is processing') !== -1 ||
                                             bodyText.indexOf('conversion stage') !== -1 ||
                                             bodyText.indexOf('pending in queue') !== -1 ||
                                             bodyText.indexOf('is being converted') !== -1 ||
                                             bodyText.indexOf('video is converting') !== -1 ||
                                             bodyText.indexOf('this video is not available') !== -1 ||
                                             bodyText.indexOf('video not found or deleted') !== -1 ||
                                             bodyText.indexOf('video is not ready yet') !== -1 ||
                                             bodyText.indexOf('no longer available') !== -1;
                            if ((isDeadInside || isDeadText) && !window.gateNotified) {
                                window.gateNotified = true;
                                log("Dead mirror / player error detected! (inside=" + isDeadInside + ", text=" + isDeadText + "). Auto-rotating...");
                                if (window.AndroidPlayer && window.AndroidPlayer.notifyGateStuck) {
                                    window.AndroidPlayer.notifyGateStuck(window.location.href);
                                }
                                return;
                            }
                        }

                        // Dead Air Watchdog: If no video is found or playing after 8 seconds, auto-rotate!
                        if (!window.videoFound && !window.successNotified && elapsedMs > 8000 && !window.gateNotified) {
                            if (window.AndroidPlayer && window.AndroidPlayer.isVideoReady && window.AndroidPlayer.isVideoReady()) {
                                window.successNotified = true;
                                return;
                            }
                            window.gateNotified = true;
                            log("Dead Air Watchdog (8s) fired: Video NOT found/playing. Triggering gate stuck auto-rotation.");
                            if (window.AndroidPlayer && window.AndroidPlayer.notifyGateStuck) {
                                window.AndroidPlayer.notifyGateStuck(window.location.href);
                            }
                            return;
                        }

                        // Click play button if not playing yet (ONLY main display play icon, NEVER next/rewind)
                        if (window.nukerAttempts < 40 && !window.successNotified) {
                            var playBtns = document.querySelectorAll('.jw-display-icon-display, .vjs-big-play-button, .play-button');
                            for (var i = 0; i < playBtns.length; i++) {
                                var btn = playBtns[i];
                                if (btn && window.clickedRegistry.indexOf(btn) === -1) {
                                    if (btn.classList && (btn.classList.contains('jw-display-icon-next') || btn.classList.contains('jw-display-icon-rewind'))) continue;
                                    try {
                                        btn.click();
                                        window.clickedRegistry.push(btn);
                                        log("Clicked PM play button: " + btn.className);
                                    } catch(e){}
                                }
                            }
                        }
                        ensureBlackBackground();
                    };

                    setInterval(runPmNuker, $intervalMs);
                } catch(e){}
            })();
        """
    }

    fun getYoutubeScript(): String {
        return """
            (function() {
                try {
                    if (window.ytBridgeInit) return;
                    window.ytBridgeInit = true;

                    // Support Trusted Types CSP by registering default policy if available
                    if (window.trustedTypes && window.trustedTypes.createPolicy && !window.__ytPolicyCreated) {
                        try {
                            window.__ytPolicyCreated = true;
                            window.trustedTypes.createPolicy('default', {
                                createHTML: function(string) { return string; },
                                createScript: function(string) { return string; },
                                createScriptURL: function(string) { return string; }
                            });
                        } catch(e) {}
                    }

                    // Inject CSS rules to hide ad elements and YouTube default chrome overlays immediately
                    if (!document.getElementById('duta-yt-style')) {
                        try {
                            var style = document.createElement('style');
                            style.id = 'duta-yt-style';
                            var css = `
                                html, body {
                                    width: 100% !important;
                                    height: 100% !important;
                                    overflow: hidden !important;
                                    background: #000 !important;
                                    margin: 0 !important;
                                    padding: 0 !important;
                                }
                                .video-ads, .ytp-ad-module, .ytp-ad-overlay-container,
                                .ytp-ad-message-container, .ytp-ad-player-overlay,
                                .ytp-ad-text, .ytp-ad-preview-container,
                                .ytp-ad-action-interstitial, .ytp-ad-image-overlay {
                                    display: none !important;
                                    visibility: hidden !important;
                                    opacity: 0 !important;
                                    pointer-events: none !important;
                                }
                                .ytp-chrome-top, .ytp-chrome-bottom, .ytp-pause-overlay {
                                    display: none !important;
                                }
                            `;
                            style.appendChild(document.createTextNode(css));
                            (document.head || document.documentElement).appendChild(style);
                        } catch(e) {}
                    }

                    var lastState = -1;
                    var lastTime = -1;
                    var lastDur = -1;

                    function reportState() {
                        var v = document.querySelector('video');
                        if (!v) return;
                        var st = v.paused ? 0 : 1;
                        var cur = v.currentTime || 0;
                        var dur = v.duration || 0;
                        if (Math.abs(cur - lastTime) > 0.2 || st !== lastState || Math.abs(dur - lastDur) > 0.5) {
                            lastState = st;
                            lastTime = cur;
                            lastDur = dur;
                            if (window.AndroidPlayer && window.AndroidPlayer.onPlayerState) {
                                window.AndroidPlayer.onPlayerState(st, cur, dur);
                            }
                        }
                    }

                    window.playerBridge = {
                        play: function() {
                            var v = document.querySelector('video');
                            if (v && v.paused) v.play().catch(function(){});
                            reportState();
                        },
                        pause: function() {
                            var v = document.querySelector('video');
                            if (v && !v.paused) v.pause();
                            reportState();
                        },
                        seek: function(t) {
                            var v = document.querySelector('video');
                            if (v) v.currentTime = t;
                            reportState();
                        },
                        findVideo: function() {
                            return document.querySelector('video');
                        }
                    };

                    function processAds() {
                        try {
                            var v = document.querySelector('video');
                            if (v && !v._subSyncAttached) {
                                v._subSyncAttached = true;
                                v.addEventListener('timeupdate', function() { if (!isAd) reportState(); });
                                v.addEventListener('play', reportState);
                                v.addEventListener('pause', reportState);
                                v.addEventListener('seeking', reportState);
                                v.addEventListener('seeked', reportState);
                            }

                            // 1. Auto-click any skip buttons if available
                            var skipBtns = document.querySelectorAll(
                                '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .videoAdUiSkipButton, .ytp-ad-overlay-close-button, .ytp-ad-skip-button-slot, .ytp-ad-skip-button-container, [class*="skip-button"], [id*="skip-button"]'
                            );
                            for (var i = 0; i < skipBtns.length; i++) {
                                try { skipBtns[i].click(); } catch(e){}
                            }

                            // 2. Click large play button if paused initially
                            var bigPlay = document.querySelector('.ytp-large-play-button');
                            if (bigPlay && bigPlay.offsetParent !== null && !window.initialAutoplayDone) {
                                try { bigPlay.click(); } catch(e){}
                            }

                            // 3. Fast-forward & mute any active ad
                            var player = document.querySelector('.html5-video-player');
                            var isAd = (player && (player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting'))) ||
                                       document.querySelector('.ad-showing, .ad-interrupting, .ytp-ad-player-overlay, .video-ads .html5-main-video');
                            if (isAd && v) {
                                v.muted = true;
                                v.playbackRate = 16.0;
                                if (v.duration && !isNaN(v.duration) && isFinite(v.duration)) {
                                    v.currentTime = v.duration;
                                }
                            } else if (v && v.playbackRate > 2.0) {
                                // Restore speed and unmute once ad finishes
                                v.playbackRate = 1.0;
                                v.muted = false;
                            }

                            // 4. Initial autoplay trigger
                            if (v && v.paused && !window.initialAutoplayDone && (window.nukerAttempts || 0) < 15) {
                                window.nukerAttempts = (window.nukerAttempts || 0) + 1;
                                v.play().then(function() {
                                    window.initialAutoplayDone = true;
                                }).catch(function(){});
                            }

                            // 5. Notify playback started (only for real content, never for ads)
                            if (v && v.currentTime > 0.3 && !window.successNotified && !isAd) {
                                window.successNotified = true;
                                if (window.AndroidPlayer && window.AndroidPlayer.notifyVideoPlaying) {
                                    window.AndroidPlayer.notifyVideoPlaying();
                                }
                            }

                            if (!isAd) {
                                reportState();
                            }
                        } catch(e) {}
                    }

                    // MutationObserver for instant 0ms ad detection
                    try {
                        var observer = new MutationObserver(function() {
                            processAds();
                        });
                        observer.observe(document.documentElement, {
                            childList: true,
                            subtree: true,
                            attributes: true,
                            attributeFilter: ['class']
                        });
                    } catch(e) {}

                    // Polling interval as backup
                    setInterval(processAds, 200);

                } catch(e) {}
            })();
        """.trimIndent()
    }

    fun getYoutubeHtml(videoId: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    html, body { width: 100vw; height: 100vh; min-width: 100%; min-height: 100%; background: #000; overflow: hidden; }
                    iframe { width: 100vw; height: 100vh; border: 0; position: fixed; top: 0; left: 0; right: 0; bottom: 0; z-index: 999; }
                </style>
            </head>
            <body>
                <iframe id="ytPlayer"
                        src="https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&controls=0&playsinline=1&enablejsapi=1&rel=0&iv_load_policy=3&fs=0&origin=https://www.google.com"
                        allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                        referrerpolicy="strict-origin-when-cross-origin"
                        allowfullscreen>
                </iframe>
                <script>
                    function callPlayer(func, args) {
                        var iframe = document.getElementById('ytPlayer');
                        if (iframe && iframe.contentWindow) {
                            iframe.contentWindow.postMessage(JSON.stringify({
                                'event': 'command',
                                'func': func,
                                'args': args || []
                            }), '*');
                        }
                    }

                    var lastYtState = 0;
                    var lastCurTime = 0;
                    var lastDuration = 0;
                    var ytSuccessNotified = false;

                    function reportState() {
                        if (window.AndroidPlayer && window.AndroidPlayer.onPlayerState) {
                            window.AndroidPlayer.onPlayerState(lastYtState, lastCurTime, lastDuration);
                        }
                    }

                    window.playerBridge = {
                        play: function() { 
                            lastYtState = 1;
                            callPlayer('playVideo'); 
                            reportState();
                        },
                        pause: function() { 
                            lastYtState = 0;
                            callPlayer('pauseVideo'); 
                            reportState();
                        },
                        seek: function(t) { 
                            lastCurTime = t;
                            callPlayer('seekTo', [t, true]); 
                            reportState();
                        },
                        findVideo: function() { return document.querySelector('video'); }
                    };

                    window.addEventListener('message', function(event) {
                        try {
                            var data = typeof event.data === 'string' ? JSON.parse(event.data) : event.data;
                            if (!data) return;
                            if (data.event === 'onReady') {
                                callPlayer('playVideo');
                            }
                            if (data.event === 'onStateChange') {
                                var st = (data.info !== undefined) ? data.info : data.data;
                                if (st === 1 || st === 3) {
                                    lastYtState = 1;
                                } else if (st === 2 || st === 0) {
                                    lastYtState = 0;
                                }
                                reportState();
                            }
                            if (data.event === 'infoDelivery' && data.info) {
                                var info = data.info;
                                if (typeof info.duration === 'number' && info.duration > 0) {
                                    lastDuration = info.duration;
                                }
                                if (typeof info.currentTime === 'number') {
                                    if (info.currentTime > lastCurTime + 0.05) {
                                        lastYtState = 1;
                                    }
                                    lastCurTime = info.currentTime;
                                }
                                if (typeof info.playerState !== 'undefined') {
                                    if (info.playerState === 1 || info.playerState === 3) {
                                        lastYtState = 1;
                                    } else if (info.playerState === 2 || info.playerState === 0) {
                                        lastYtState = 0;
                                    }
                                }
                                if (lastCurTime > 0.3 && !ytSuccessNotified) {
                                    ytSuccessNotified = true;
                                    if (window.AndroidPlayer && window.AndroidPlayer.notifyVideoPlaying) {
                                        window.AndroidPlayer.notifyVideoPlaying();
                                    }
                                }
                                reportState();
                            }
                            if (data.event === 'onError') {
                                var errCode = data.info;
                                console.warn('YouTube Player Error:', errCode);
                                if (errCode === 100 || errCode === 101 || errCode === 150) {
                                    if (window.AndroidPlayer && window.AndroidPlayer.notifyMirrorDead) {
                                        window.AndroidPlayer.notifyMirrorDead();
                                    }
                                }
                            }
                        } catch(e) {}
                    });

                    // Handshake: listen to events and direct video check
                    setInterval(function() {
                        try {
                            var iframe = document.getElementById('ytPlayer');
                            if (iframe && iframe.contentWindow) {
                                iframe.contentWindow.postMessage(JSON.stringify({ 'event': 'listening' }), '*');
                            }
                            if (lastCurTime > 0.3) {
                                reportState();
                            }
                            var v = document.querySelector('video');
                            if (v) {
                                if (v.currentTime > 0.3 && !ytSuccessNotified) {
                                    ytSuccessNotified = true;
                                    if (window.AndroidPlayer && window.AndroidPlayer.notifyVideoPlaying) {
                                        window.AndroidPlayer.notifyVideoPlaying();
                                    }
                                }
                                if (window.AndroidPlayer && window.AndroidPlayer.onPlayerState) {
                                    window.AndroidPlayer.onPlayerState(v.paused ? 0 : 1, v.currentTime || 0, v.duration || 0);
                                }
                            }
                        } catch(e) {}
                    }, 1000);
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Dedicated Bilibili HTML5 Player Bridge script.
     * Hides danmaku overlays and promo cards, bridges playback controls,
     * auto-plays, and detects geo-restrictions to trigger immediate auto-rotation.
     */
    fun getBilibiliScript(): String {
        return """
            (function() {
                try {
                    if (window.biliBridgeInit) return;
                    window.biliBridgeInit = true;

                    // Inject CSS rules to hide danmaku, app promos, and ending recommendation overlays
                    if (!document.getElementById('duta-bili-style')) {
                        try {
                            var style = document.createElement('style');
                            style.id = 'duta-bili-style';
                            var css = `
                                html, body {
                                    width: 100% !important;
                                    height: 100% !important;
                                    overflow: hidden !important;
                                    background: #000 !important;
                                    margin: 0 !important;
                                    padding: 0 !important;
                                }
                                .bpx-player-dm-wrap, .bpx-player-cmd-dm-inside,
                                .bpx-player-toast, .bpx-player-ending-related,
                                .bpx-player-popup-wrap, .bilibili-player-video-danmaku,
                                .bpx-player-dialog-wrap, .bpx-player-control-mask {
                                    display: none !important;
                                    visibility: hidden !important;
                                    opacity: 0 !important;
                                    pointer-events: none !important;
                                }
                            `;
                            style.appendChild(document.createTextNode(css));
                            (document.head || document.documentElement || document.body).appendChild(style);
                        } catch(e) {}
                    }

                    var lastState = -1;
                    var lastTime = -1;
                    var lastDur = -1;

                    function reportState() {
                        var v = document.querySelector('video');
                        if (!v) return;
                        var st = v.paused ? 0 : 1;
                        var cur = v.currentTime || 0;
                        var dur = v.duration || 0;
                        if (Math.abs(cur - lastTime) > 0.2 || st !== lastState || Math.abs(dur - lastDur) > 0.5) {
                            lastState = st;
                            lastTime = cur;
                            lastDur = dur;
                            if (window.AndroidPlayer && window.AndroidPlayer.onPlayerState) {
                                window.AndroidPlayer.onPlayerState(st, cur, dur);
                            }
                        }
                    }

                    window.playerBridge = {
                        play: function() {
                            var v = document.querySelector('video');
                            if (v && v.paused) v.play().catch(function(){});
                            reportState();
                        },
                        pause: function() {
                            var v = document.querySelector('video');
                            if (v && !v.paused) v.pause();
                            reportState();
                        },
                        seek: function(t) {
                            var v = document.querySelector('video');
                            if (v) v.currentTime = t;
                            reportState();
                        },
                        findVideo: function() {
                            return document.querySelector('video');
                        }
                    };

                    function checkBilibiliPlayer() {
                        try {
                            // Geo-restriction detection fail-safe:
                            // If video is region-locked, notify Android player to rotate mirrors immediately
                            var bodyText = ((document.body && (document.body.innerText || document.body.textContent)) || "").toLowerCase();
                            var isGeoBlocked = bodyText.indexOf('地区无法观看') !== -1 ||
                                               bodyText.indexOf('not available in your location') !== -1 ||
                                               bodyText.indexOf('not available in your region') !== -1 ||
                                               bodyText.indexOf('根据版权方要求') !== -1;
                            if (isGeoBlocked && !window.biliDeadNotified) {
                                window.biliDeadNotified = true;
                                if (window.AndroidPlayer && window.AndroidPlayer.notifyMirrorDead) {
                                    window.AndroidPlayer.notifyMirrorDead();
                                }
                                return;
                            }

                            var v = document.querySelector('video');
                            if (v && !v._biliSubSyncAttached) {
                                v._biliSubSyncAttached = true;
                                v.addEventListener('timeupdate', reportState);
                                v.addEventListener('play', reportState);
                                v.addEventListener('pause', reportState);
                                v.addEventListener('seeking', reportState);
                                v.addEventListener('seeked', reportState);
                            }
                            // Auto-click play button if paused initially (supports desktop & mobile player UI)
                            var playBtn = document.querySelector('.bpx-player-ctrl-play, .bilibili-player-video-btn-start, .m-video-player-btn-play, .btn-play, .play-icon, .icon-play, .video-play-btn');
                            if (playBtn && v && v.paused && !window.initialAutoplayDone) {
                                try { playBtn.click(); window.initialAutoplayDone = true; } catch(e) {}
                            }
                            if (v && v.paused && !window.initialAutoplayDone && (window.biliAttempts || 0) < 10) {
                                window.biliAttempts = (window.biliAttempts || 0) + 1;
                                v.play().then(function() { window.initialAutoplayDone = true; }).catch(function(){});
                            }

                            // Active playback stall detector (5s without position advancement while playing)
                            if (v && !v.paused && v.currentTime > 0.5) {
                                if (v.currentTime === window.biliLastObservedTime) {
                                    window.biliStallDuration = (window.biliStallDuration || 0) + 300;
                                    if (window.biliStallDuration > 5000 && !window.biliStallNotified) {
                                        window.biliStallNotified = true;
                                        if (window.AndroidPlayer && window.AndroidPlayer.notifyMirrorDead) {
                                            window.AndroidPlayer.notifyMirrorDead();
                                        }
                                    }
                                } else {
                                    window.biliLastObservedTime = v.currentTime;
                                    window.biliStallDuration = 0;
                                }
                            }

                            // Playback success handshake
                            if (v && v.currentTime > 0.3 && !window.successNotified) {
                                window.successNotified = true;
                                if (window.AndroidPlayer && window.AndroidPlayer.notifyVideoPlaying) {
                                    window.AndroidPlayer.notifyVideoPlaying();
                                }
                            }

                            reportState();
                        } catch(e) {}
                    }

                    setInterval(checkBilibiliPlayer, 300);
                } catch(e) {}
            })();
        """.trimIndent()
    }

    /**
     * Specialized ad-bypass and player automation script for Dailymotion embed players.
     * Bypasses pre-roll/mid-roll ads, hides consent & watermark overlays, and connects HTML5 controls.
     */
    fun getDailymotionScript(): String {
        return """
            (function() {
                try {
                    // Inject CSS to wipe out Dailymotion overlays, cookie consent, watermarks, and ad banners
                    var style = document.createElement('style');
                    style.innerHTML = `
                        #onetrust-consent-sdk, .consent-banner, .dm-consent,
                        .np_ad, .ad-container, .ad-badge, [class*="ad_"], [class*="ad-"],
                        .dmp_AdBreakIndicator, .dmp_AdPlaying, .dmp_AdCountdown,
                        .dmp_Watermark, .dmp_Logo, .dmp_EndScreen, .dmp_UpNext,
                        .dmp_AdLinear, .dmp_AdNonLinear, .dmp_AdSlot,
                        [aria-label*="advertisement" i], [aria-label*="advertising" i] {
                            display: none !important;
                            opacity: 0 !important;
                            pointer-events: none !important;
                            visibility: hidden !important;
                        }
                        video {
                            object-fit: contain !important;
                        }
                    `;
                    (document.head || document.documentElement).appendChild(style);

                    function findDmVideo() {
                        var isAdShowing = document.querySelector('.dmp_AdPlaying, .np_ad, [class*="ad-showing"], [class*="is-ad"]') !== null;
                        var videos = document.querySelectorAll('video');
                        for (var k = 0; k < videos.length; k++) {
                            if (videos[k].duration > 90 || !isAdShowing) {
                                return videos[k];
                            }
                        }
                        return videos.length > 0 ? videos[0] : null;
                    }

                    // Hook HTML5 Player controls for ExoPlayer forwarding
                    window.playerBridge = {
                        play: function() {
                            var v = findDmVideo();
                            if (v) {
                                v.muted = false;
                                v.play().catch(function(){});
                                setTimeout(function() { reportState(true); }, 80);
                            }
                        },
                        pause: function() {
                            var v = findDmVideo();
                            if (v) {
                                v.pause();
                                setTimeout(function() { reportState(true); }, 80);
                            }
                        },
                        seek: function(time) {
                            var v = findDmVideo();
                            if (v) {
                                v.currentTime = time;
                                setTimeout(function() { reportState(true); }, 80);
                            }
                            if (window.AndroidPlayer && window.AndroidPlayer.onPlayerState) {
                                window.AndroidPlayer.onPlayerState(1, time, (v && v.duration) || 0);
                            }
                        },
                        findVideo: function() {
                            return findDmVideo();
                        },
                        sync: function() {
                            reportState(false);
                        },
                        heartbeat: function() {
                            reportState(false);
                        }
                    };

                    var lastReportedTime = -1;
                    var lastReportedState = -1;
                    var lastReportTimestamp = 0;
                    function reportState(force) {
                        var v = findDmVideo();
                        if (v && window.AndroidPlayer && window.AndroidPlayer.onPlayerState) {
                            var isPlaying = !v.paused && !v.ended && v.readyState > 2;
                            var state = isPlaying ? 1 : (v.paused ? 2 : 0);
                            var cur = v.currentTime || 0;
                            var dur = v.duration || 0;
                            var now = Date.now();
                            var curSec = Math.floor(cur);
                            // TV CPU OPTIMIZATION: Throttle reporting to at most once every 800ms unless state changed or forced
                            if (force || state !== lastReportedState || (curSec !== lastReportedTime && (now - lastReportTimestamp) >= 800)) {
                                lastReportedState = state;
                                lastReportedTime = curSec;
                                lastReportTimestamp = now;
                                window.AndroidPlayer.onPlayerState(state, cur, dur);
                            }
                        }
                    }

                    function checkDailymotionPlayer() {
                        try {
                            // 1. Dead video watchdog
                            var bodyText = ((document.body && (document.body.innerText || document.body.textContent)) || "").toLowerCase();
                            var isDead = bodyText.indexOf('video has been removed') !== -1 ||
                                         bodyText.indexOf('video is private') !== -1 ||
                                         bodyText.indexOf('content rejected') !== -1 ||
                                         bodyText.indexOf('not available in your country') !== -1;
                            if (isDead && !window.dmDeadNotified) {
                                window.dmDeadNotified = true;
                                if (window.AndroidPlayer && window.AndroidPlayer.notifyMirrorDead) {
                                    window.AndroidPlayer.notifyMirrorDead();
                                }
                                return;
                            }

                            // 2. Automated Ad Skip & Fast Forward
                            var skipButtons = document.querySelectorAll(
                                'button[aria-label*="skip" i], .ad-skip, .skip-button, [data-testid*="skip"], .dmp_AdSkipButton, .dmp_SkipButton'
                            );
                            for (var i = 0; i < skipButtons.length; i++) {
                                try { skipButtons[i].click(); } catch(e) {}
                            }

                            var isAdShowing = document.querySelector('.dmp_AdPlaying, .np_ad') !== null;
                            var videos = document.querySelectorAll('video');
                            for (var j = 0; j < videos.length; j++) {
                                var vid = videos[j];
                                // NEVER skip or alter main content video (duration > 90). Only target explicit short ad videos
                                var isShortAd = vid.duration > 0 && vid.duration <= 90 && (isAdShowing || (vid.closest && vid.closest('.dmp_AdPlaying, .np_ad, [class*="ad-container"]')));
                                if (isShortAd) {
                                    try {
                                        vid.muted = true;
                                        vid.playbackRate = 16.0;
                                        if (isFinite(vid.duration)) {
                                            vid.currentTime = vid.duration - 0.1;
                                        }
                                    } catch(e) {}
                                }
                            }

                            // 3. Find primary content video
                            var mainVideo = null;
                            for (var k = 0; k < videos.length; k++) {
                                if (videos[k].duration > 90 || !isAdShowing) {
                                    mainVideo = videos[k];
                                    break;
                                }
                            }
                            if (!mainVideo && videos.length > 0) mainVideo = videos[0];

                            // 4. Auto-play trigger & Subtitle Sync Listeners
                            if (mainVideo) {
                                if (!mainVideo._dmSubSyncAttached) {
                                    mainVideo._dmSubSyncAttached = true;
                                    mainVideo.addEventListener('timeupdate', function() { reportState(false); });
                                    mainVideo.addEventListener('play', function() { reportState(true); });
                                    mainVideo.addEventListener('pause', function() { reportState(true); });
                                    mainVideo.addEventListener('seeking', function() { reportState(true); });
                                    mainVideo.addEventListener('seeked', function() { reportState(true); });
                                }
                                if (mainVideo.paused && !window.initialAutoplayDone) {
                                    var playBtn = document.querySelector('button[aria-label="Play"], .play-button, .dmp_PlayButton');
                                    if (playBtn) { try { playBtn.click(); } catch(e) {} }
                                    try {
                                        mainVideo.muted = false;
                                        mainVideo.play().then(function() { window.initialAutoplayDone = true; }).catch(function(){});
                                    } catch(e) {}
                                }

                                // 5. Playback success handshake
                                if (mainVideo.currentTime > 0.5 && !isAdShowing && !window.dmSuccessNotified) {
                                    window.dmSuccessNotified = true;
                                    if (window.AndroidPlayer && window.AndroidPlayer.notifyVideoPlaying) {
                                        window.AndroidPlayer.notifyVideoPlaying();
                                    }
                                }
                            }

                            reportState(false);
                        } catch(e) {}
                    }

                    setInterval(checkDailymotionPlayer, 1000);
                } catch(e) {}
            })();
        """.trimIndent()
    }
}
