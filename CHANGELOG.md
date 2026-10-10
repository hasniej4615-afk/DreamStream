# Changelog

All notable changes to the Duta Movie application are documented in this file.

## [2.1.0 Cloud 7] - 2026-10-10

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
- **Category Row Startup Positioning**:
  - Guaranteed clean startup alignment ensuring every movie and series category row displays from the very first title on app launch.
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
