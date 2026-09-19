# DMStreaM v1.6: Owl's Eye Technical Documentation

## 👁️ Overview
**Owl's Eye** is the advanced monitoring, extraction, and self-healing engine powering DMStreaM. It is designed to ensure 99% playback success even when host sites use aggressive anti-bot measures, complex JavaScript gates, or low-quality mirrors.

---

## 🛠️ Core Systems

### 1. Smart Mirror Watchdog (v5.0 - v5.9)
The Watchdog monitors the critical "Handshake" period between selecting a video and the first frame appearing.
- **Handshake Monitoring**: Detects "Permanent Black Screen" during WebView initialization.
- **TV Optimization (v1.5)**: Automatically relaxes timeouts (up to 120s) and implements a **Hard Reset** for WebViews during episode transitions to clear memory on limited TV hardware.
- **Auto-Rotation**: If the handshake fails to produce a playable stream within the deadline, Owl's Eye automatically flags the mirror as "Dead" and rotates to the next available server.
- **Content Identity Lock (v1.5.1)**: Assigns a unique key to each episode, ensuring all player states are wiped clean during transitions to prevent "Loading Hangs."

### 2. Stall Guard & Progress Saver
Monitors active playback to ensure continuity.
- **Movement Verification**: Checks video position every 3-4 seconds. If the position remains frozen while the "Playing" state is true, it triggers a stall alert.
- **Trending Fast-Track**: High-demand content (e.g., Agent Kim) gets faster stall detection (3s cycles) to minimize user frustration.
- **Self-Healing**: If a stall is confirmed (2 consecutive cycles), the mirror is rotated immediately.

### 3. Healthy Duration & Zombie Detection
Identifies "fake" streams and prevents premature autoplay jumps.
- **Healthy Duration Guard (v1.5.1)**: Realizes that TV episodes are rarely < 10 mins. If a stream "Ends" before this, Owl's Eye flags it as a server drop and rotates mirrors instead of jumping to the next episode.
- **Duration Analysis**: If a "Full Movie" reports a duration of < 5 minutes (300s), Owl's Eye identifies it as a "Zombie" (usually an ad loop).

### 4. Nuker Ad-Blocker Engine
A multi-layered script injection system for WebView playback.
- **Immediate Neutralization**: Blocks ad-scripts before they can spawn pop-ups.
- **Nuclear Winter CSS**: Forcefully hides all non-video elements on a page.
- **Focus Guard**: Prevents WebView from stealing remote control focus on Android TV.

### 5. Header Profile System (HPS)
Bypasses 403 Forbidden and Regional blocks.
- **UA Rotation**: Cycles between Desktop and Mobile User-Agents based on the mirror's "Strictness" level.
- **Referer Spoofing**: Intelligently maintains site-specific referers to ensure CDNs authorize the stream request.

### 6. Extraction Tiers (GOD MODE)
The engine prioritizes stream quality and stability:
1. **Tier 1 (HgCloud/VIP)**: Ultra-stable, high-bandwidth.
2. **Tier 2 (IndoStream)**: Best for local Indonesian connectivity.
3. **Tier 3 (Abyss/Bond)**: Cleanest sources, often supports direct ExoPlayer playback.
4. **Self-Healing Discovery (v1.5.1)**: Scans HTML "DNA" to automatically learn and trust new site domains (e.g., `katakatamutiara.com`) instantly upon detection.

### 7. Smart RAM Detection (v1.5)
The engine scales its aggressiveness based on device hardware:
- **Low RAM (1GB-2GB)**: Caps video buffer at 40s and reduces image cache to 15% to prevent OOM crashes.
- **Normal RAM**: Unlocks 90s buffers and high-res poster caching for premium devices.
- **Auto-Purge**: Metadata caches are automatically cleared once they exceed device-specific item limits (300-1500 items).

---

## 🚦 Troubleshooting Logs
Monitor these tags in Logcat for Owl's Eye status:
- `OwlEyeMonitoring`: General watchdog status.
- `VideoPlayerSniffer`: Status of direct stream extraction.
- `VideoPlayerTurbo`: Network-level ad-blocking logs.
- `VideoPlayerWebView`: JS console and redirect loop detection.

---

## 📈 Version History
- **v1.0**: Basic extraction.
- **v1.2**: Added mirror rotation.
- **v1.3**: Added Owl's Eye Watchdog and Stall Guard.
- **v1.5**: **Autoplay Fix & RAM Optimizations** (Smart detection, Hard Resets).
- **v1.5.1**: **Domain Discovery & Position Pinning** (Self-healing DNA discovery, Healthy Duration Guard).
- **v1.6**: **Autoplay Transition Stability & Mirror Refinement**.

---
*Owl's Eye: Seeing through the noise so you can watch in peace.*
