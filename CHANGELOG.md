# Changelog

All notable changes to the Duta Movie application are documented in this file.

## [2.1.4 Cloud 7] - 2026-10-22

> **YouTube Web Audio Equalizer (Sony Mega Bass) & Background Silent Sync Release**: Introduces an in-engine real-time Web Audio DSP Equalizer for YouTube featuring a dedicated Sony Mega Bass acoustic replica (+9.5dB punch, -2.5dB de-mud, dynamics compression) and audiophile music presets with instant A/B switching and full Android TV D-Pad remote optimization. Silently manages cloud profile synchronization and hardens multi-language subtitle fetching and translation.

### Added
- **YouTube Web Audio DSP Equalizer**:
  - Direct integration into YouTube HTML5 video stream using Web Audio API (`AudioContext`, BiquadFilterNodes, DynamicsCompressor, and GainNode).
  - **Sony Mega Bass Replica**: Faithfully replicates the classic Sony Walkman acoustic curve with +9.5dB sub-bass boost @ 60Hz, +6.0dB bass shelf @ 120Hz, -2.5dB midrange clarity cut @ 600Hz, +1.0dB vocal presence @ 3kHz, +4.5dB airy treble @ 10kHz, coupled with a fast-acting dynamics compressor (-16dB threshold, 5:1 ratio, 3ms attack) to prevent speaker clipping or distortion.
  - **Dedicated Music & Speech Presets**: Added **Music Hi-Fi (Balanced)**, **Bass Boost (Max)**, **Vocal Clarity**, and **Normal (Flat Bypass)**.
  - **Real-Time A/B Switching**: Interactive dialog lets users switch presets on-the-fly while listening without any audio clicks or pops (50ms exponential smoothing).
  - **Header Quick Access**: Added glowing red Equalizer icon in the YouTube header and an entry in the More Options menu.
  - **Android TV Remote Optimization**: Preset cards feature high-contrast D-pad focus outlines (`BorderStroke 1.5.dp, Color.White`), subtle card zoom (`1.02x`), enlarged typography for 10-foot viewing, and remote back button dismissal.
  - **OLED Black Screen Badge**: Active audio preset pill displayed during darkened AMOLED listening mode.

### Fixed & Improved
- **Silent Background Cloud Sync**:
  - Removed disruptive toast notification popups on automatic periodic profile and watch history syncing.
  - Synchronization runs completely silently in the background while maintaining full error logging for diagnostics.
- **Subtitle & Translation Engine**:
  - Verified and stabilized multi-source subtitle retrieval and auto-translation flows.
- **In-App Help & User Manual Synchronization**:
  - Added comprehensive `[YOUTUBE & EQ]` interactive manual card covering the Web Audio DSP Equalizer, Sony Mega Bass Walkman replica, music presets, TV remote controls, and OLED screen dimming.
  - Updated Cloud Profile & Sync manual with silent synchronization behavior.

## [2.1.3 Cloud 7] - 2026-10-19

> **Android TV Navigation & Expanded Live TV Lineup Release**: Completely fixes Android TV D-Pad horizontal category scrolling and eliminates accidental jumps into the sidebar menu. Expands the Malaysian Live TV lineup to 17 official broadcast channels with dynamic HMAC stream resolution and real-time EPG now-playing guides.

### Added
- **Expanded Malaysian Live TV Lineup (17 Official Channels)**:
  - Added **8TV (八度空间)**: Malaysia's #1 Chinese commercial television channel by Media Prima with official 1080p FHD linear broadcast stream and backup mirrors.
  - Added **TV9**: Media Prima family and local entertainment channel featuring drama series, kids cartoons, and telemovies.
  - Added **TVS (TV Sarawak)**: Borneo's flagship regional broadcast with news, local documentaries, and cultural arts.
  - Added **Astro Awani**: Malaysia's leading 24-hour news network.
  - Added **Makkah Live (Saudi Quran TV)**: Official 24/7 direct broadcast from Masjidil Haram, Makkah.
  - **Real-Time EPG Guides**: Integrated now-playing subtitle labels on TV channel cards displaying current programs.
- **Dynamic Multi-Channel Stream Token Engine**:
  - Unified tokenized HMAC pre-resolution and redirect caching across all Media Prima (TV3, 8TV, TV9) and Tenbyte CDN channels (Alhijrah, Bernama, CNA, TVS).

### Fixed & Hardened
- **Android TV D-Pad Horizontal Category Scrolling**:
  - Eliminated continuous index watchdog (`snapshotFlow { listState.firstVisibleItemIndex }`) that was snapping category rows back to index 0 on D-Pad Right.
  - Users can now smoothly and continuously navigate horizontally across all cards in any category row without interruption.
- **TV Sidebar Menu Focus Lock**:
  - Stabilized `LazyRow` item keys (`key = { _, video -> video.id }`), preventing Composable destruction and dropped-focus fallbacks to the navigation rail while navigating.
  - Hardened `LazyRow.focusProperties`: D-Pad Left is strictly trapped inside the row and only permitted to exit to the sidebar menu when the user is explicitly on **Card 0** at the beginning of the row (`focusedItemIndex == 0 && listState.firstVisibleItemIndex == 0`).
  - Restored natural 2D vertical grid navigation (`enter = { FocusRequester.Default }`), preserving horizontal column alignment across rows without yanking focus back to Card 0.
- **Browsing State & Focus Theft Protection**:
  - Cold-startup alignment only triggers once prior to user interaction. Row positions and focus are completely immune to resets during thumbnail preloading or pagination.

---

## [2.1.2 Cloud 7] - 2026-10-18

> **Major Feature & Subtitle Auto-Pilot 2.0 Release**: Upgrades TV3 Malaysia to the official Media Prima Tonton 1080p linear broadcast stream with dynamic HMAC session tokens, overhauls the Subtitle Engine with Auto-Pilot 2.0 (unverified scraper demotion, AI translation preference, and real-time speech mismatch auto-recovery), stabilizes Android TV category row focus, and introduces high-resolution transparent Live TV channel branding.

### Added
- **Official Tonton TV3 1080p Live Broadcast**:
  - Direct integration with official Media Prima Tonton CloudFront HLS master manifest stream (up to 1080p FHD at 5.7 Mbps, DRM-free unencrypted MPEG-TS chunks).
  - Broadcasts actual linear television programming (Drama Akasia, Buletin Utama, local entertainment, and live sports), replacing news-only feeds.
  - Multi-server redundancy: Server 1 (`Tonton HLS 1080p Official Broadcast`), Server 2 (`Tonton HLS Direct Mirror`), and Server 3 (`Buletin TV3 24/7 News Backup`).
- **Smart Subtitle Auto-Pilot 2.0 & AI Fallback**:
  - **Provider Scoring & Quality Gate**: SubtitleCat bot machine-translations heavily demoted (`-80` score penalty) while verified provider releases (`[OpenSubtitles]`, `[SubSource]`, `[SubDL]`, `[Subscene]`) receive a `+30` priority bonus.
  - **AI Translation Preference**: If the only candidate in the user's target language is an unverified bot scrape, the engine automatically skips it in favor of AI-translating verified official English releases using Gemini AI.
  - **Automatic Preference Cleansing**: Disqualifies and purges obsolete or mislabeled SubtitleCat entries previously saved in local preferences.
  - **Autonomous Runtime Content Mismatch Auto-Recovery**: Detects wrong movie subtitles during playback via VAD cross-correlation (`speech >= 8s`, `recall < 38%`). Automatically blacklists the bad file, purges saved offsets, and auto-recovers by translating verified English releases with a HUD notice (`⚡ Subtitles Auto-Synced (Auto-Recovered)`).
  - **Discrete Sliding-Window Cross-Correlation (Mini-FFSubSync)**: 50ms time-binned discrete dot-product alignment between VAD speech activity and dialogue cue masks, providing mathematical noise immunity against intro fanfare, logos, sound effects, and silence.
- **High-Resolution Transparent Channel Branding**:
  - Added clean transparent logos and custom brand gradients for **TV3**, **Bernama TV**, **TV Alhijrah**, and **CNA**.

### Improved & Hardened
- **Dynamic HLS Session Pre-Resolution**:
  - Implemented automatic tokenized redirect following in `VideoViewModel` to pre-resolve dynamic HMAC API endpoints into the base CloudFront master playlist, ensuring subvariant `.ts` chunks load seamlessly in ExoPlayer.
- **Server Selection & Active Server Synchronization**:
  - Synchronized ViewModel server state with player server selection dialog, ensuring the currently playing broadcast server is accurately highlighted and switchable in real time.
- **Android TV Category Row D-Pad Stability**:
  - Implemented dynamic front-anchoring for category rows, preventing sudden scroll jumps and focus loss when asynchronous thumbnail preloading finishes.
  - Category rows maintain stable scroll position and restore exact card focus when navigating back from video detail screens.
- **ExoPlayer Audio Processor Thread Safety**:
  - Resolved thread access violations in `VadAudioProcessor` with a sample-accurate PCM clock and structured coroutine scoping.
- **Automated Regression Test Suite**:
  - Expanded `AutoSubtitleTest.kt` with comprehensive unit tests for subtitle scoring, scraper rejection, cross-correlation alignment, and content mismatch recovery.

---

## [2.1.1 Cloud 7] - 2026-10-14

> **Feature & Hardening Release**: Introduces TV3 Malaysia Live streaming with official high-res branding and resilient YouTube Live fallback, hardware-level Voice Activity Detection (VAD) audio processing for real-time speech synchronization, Dual Subtitle display with customizable mask box, auto-pilot subtitle calibration, and interactive Supabase RLS broadcast management.

### Added
- **TV3 Malaysia Live Streaming & Resilient Fallback**:
  - Integrated official high-resolution transparent TV3 Malaysia channel branding.
  - Multi-server resilience pairing direct YouTube Live embedding alongside low-latency HLS streams with automated PlaybackStuck recovery.
  - Live TV architecture isolation ensuring television channels bypass pirate media extractors and subtitle scrapers.
- **Hardware-Level Voice Activity Detection (VAD) Audio Processor**:
  - Integrated custom ExoPlayer audio processor capturing real-time PCM audio buffers to identify speech boundaries and vocal energy onset.
  - Real-time millisecond dialogue synchronization linking speech detection to subtitle cues for automatic timing adjustment.
- **Dual Subtitle Display & Subtitle Mask Box**:
  - Stacked dual subtitle rendering (warm golden-yellow secondary subtitle stacked directly above crisp white primary subtitle).
  - Configurable Subtitle Mask Box designed to cleanly obscure baked-in hardcoded subtitles when reading translated subtitles.
- **Intelligent Subtitle Signature Matching & Auto-Pilot**:
  - `SubtitleSyncEngine` extracts stream signatures (quality, platform, source release group) to automatically select and lock matching subtitles to 0ms native sync without timing drift.
  - Auto-pilot timing calibration and per-title offset persistence across playback sessions.
- **Interactive Broadcast Admin & Announcement System**:
  - Broadcast management dialog featuring priority broadcast creation, multi-level styling, and active announcement displays.
  - Supabase RLS compatibility fix ensuring announcements deactivate cleanly via upsert before deletion.

### Improved & Hardened
- **AI Subtitle Translator Rate-Limit Bypass**:
  - Replaced throttled web endpoints with dedicated mobile client endpoints, eliminating HTTP 429 rate limits during large batch translations.
  - Intelligent multi-tier fallback pipeline guaranteeing subtitle availability even during provider outages.
- **Player Gesture & HUD Stability**:
  - Refined single-tap HUD toggling, seek gesture responsiveness, and background state synchronization.

---

## [2.1.0b Cloud 7] - 2026-10-10

> **Fix & Optimization Release**: Fixes category row browsing scroll position resets when exiting video details and provides seamless TV mode D-Pad focus restoration.

### Added
- **SHORT TV Search Filter Chip**:
  - Integrated a dedicated filter chip in search results allowing instant isolation and browsing of short-form dramas and micro-series.
  - Full Android TV D-Pad focus chaining and keyboard navigation across filter chips.
- **Universal AI Subtitle Translator**:
  - Expanded subtitle translation engine to translate from any source language (Chinese, Korean, Japanese, Thai, Arabic, Spanish, etc.) into Malay, Indonesian, or English.
  - Multi-tier fallback pipeline with intelligent chunk batching and failover.
- **Cloud Backup & Restore (6-Digit PIN)**:
  - Seamless backup of user profile, custom photo avatar, bookmarks, and watch history to cloud storage with quick 6-digit PIN restore across devices.
- **Comprehensive Subtitle Sync Manual**:
  - In-app interactive manual and settings guidance covering 1-click dialogue snapping ("Snap Line Now"), first-line intro alignment ("Align 1st Line"), micro-stepping (±0.1s to ±10s), and TV remote navigation.
- **Persistent Subtitle Calibration**:
  - Per-title timing memory automatically saves and restores sync offsets when resuming movies or series.

### Improved
- **Category Row Browsing Scroll Preservation & TV Focus Optimization**:
  - Guaranteed first-title alignment exclusively on cold app launch, while preserving scroll position seamlessly during browsing, vertical feed scrolling, and navigating back from `VideoDetailScreen`.
  - Comprehensive TV mode optimization for both `isRealTV` and `isTV` environments: exact D-Pad focus restoration to the active video card upon returning from video details, with smooth in-view scrolling and zero focus jumping.
- **Refined & Compact Profile UI**:
  - Modernized 64dp avatar with photo picker and quick delete icon, aligned username controls, and concise profile statistics (Bookmarks, History, Voice Boost).
- **Malaysian Live TV Stream Stability**:
  - Enhanced live playback engine bypassing disk caching, resolving CloudFront 403 authorization, resilient PlaylistStuck recovery, and clean TV card visuals.

### Security & Hardening
- **Network Security Configuration**:
  - Cleaned and hardened production network security config (`cleartextTrafficPermitted=false` default) with targeted domain opt-ins only where required for streaming CDNs.
- **Database & Concurrency Hardening**:
  - Room database connection pooling unified via Dagger/Hilt singleton injection in background workers.
  - Removed deprecated `GlobalScope` usage and upgraded background coroutine lifecycle to structured concurrency.

---

## [2.0.9 Cloud 7]

### Added & Improved
- **Instant MovieBox Direct CDN Streaming**: Zero-delay 0ms parallel background resolution for MovieBox high-speed 1080P/720P streams, eliminating detail screen loading lag.
- **State-Safe Mirror Preservation**: Progressive mirror injection that preserves discovered high-speed streams without being wiped out when primary webpage scraping completes.
- **Enhanced Cross-Provider Title Matching**: Intelligent year normalization and token parsing preventing false negatives on multi-source titles.
- **Refined Subtitle Display & Transparency**: Clean subtitle presentation with reduced background box opacity and crisp high-contrast white text for effortless readability.

---

## [2.0.8 Cloud 7]

### Added & Improved
- **MovieBox Multi-Stream Integration**: Full cross-provider mirror resolution fetching direct high-speed CDN MP4 streams (1080P, 720P, 480P, 360P) with golden HD badges across all titles.
- **TV Mode & D-Pad Navigation**: Complete Leanback remote control optimization featuring high-visibility 3.5dp focus rings, scale animations, and center/enter key binding across mirrors, synopsis, and navigation.
- **TV Device Safety & ExoPlayer Acceleration**: Conditioned Google Cast routing on TV hardware to eliminate uninitialized context crashes; direct hardware decoding for instant CDN playback.
- **Intelligent Movie Match & Mirror Discovery**: Strict year and token matching preventing premature loop breaks, ensuring high-speed mirrors populate reliably on detail screens.

---

## [2.0.7 Cloud 7]

### Added & Improved
- **Refined Player Touch Gestures**: Single-tap anywhere on screen to smoothly toggle playback HUD and overlay controls; double-tap left or right to seek 10s backward or forward.
- **Nuker v7.4 & Audio Auto-Mute**: Intelligent error recovery for JW Player embed streams, automatically bypassing autoplay blocks and restoring audio seamlessly upon interaction.
- **Provider Repository Optimization**: Cleanly purged defunct P-Ramlee provider from database and implemented automated blacklist filtering on app launch and repo sync.
- **Enhanced Player Controls Stability**: State-safe overlay listeners preventing UI flickering and ensuring responsive playback dismissal across both mobile and TV modes.

---

## [2.0.6 Cloud 7]

### Added & Improved
- **DUTAMOVIE21 Streaming Portal Integration**: Direct high-speed streaming portal added to official repository with zero-config database self-healing.
- **Refined Server Display (Primary Mirror First)**: Server selector displays the primary verified live mirror first, eliminating non-player buttons, ads, and CMS title leaks.
- **Automated Cross-Provider Failover**: Alternative multi-provider sources are now selectively crawled and activated only when the primary link is dead or upon request.
- **Dynamic Cloud Provider Hub**: Hybrid Supabase cloud-synced repositories with instantaneous offline fallback and real-time domain healing.
- **Upgraded Media Extractor & Player Compatibility**: Expanded player resolver whitelist including PlaySobat embeds and hardened URL migration rules.
