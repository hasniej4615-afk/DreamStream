# Changelog

All notable changes to the Duta Movie application are documented in this file.

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
