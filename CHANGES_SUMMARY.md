# DMStreaM: Changes Summary

## 🦅 v2.0: Raven's Revenge
Released: Sep 15, 2026

### 🚀 Official Release Highlights (Promo Poster)
- **Restored TV Series & Anime Episodes**: Full universal episode resolution and playback.
- **Multi-Season Grouping & Auto-Sort**: Automatic season classification and episode sequence sorting.
- **Universal Episode Extraction Engine**: Deep extraction for modern layout structures (`.gmr-listseries`).
- **Multi-Provider Mirror Auto-Healing**: Discover and resolve fresh video mirrors automatically.
- **Instant Dead Stream Failover**: Sub-2s dead stream detection with rapid mirror recovery.

---

## 👁️ v1.6: The Playback Stability Update
Released: Aug 17, 2026

### 🚀 Key Improvements
- **Refined Autoplay Engine**: Optimized transition delays and state resets for 100% reliable marathons on TV hardware.
- **Enhanced Stall Guard**: Smarter detection of connection drops to trigger mirror rotation without user input.
- **Improved TV Navigation**: Larger UI feedback elements for better visibility from distance.

---

## 👁️ v1.5.1: The Self-Healing Update
Released: Aug 17, 2026

### 🚀 Key Improvements
- **Autonomous Domain Discovery**: App now uses HTML "DNA" fingerprinting to learn new site domains instantly (e.g., automatically detected `katakatamutiara.com`).
- **Healthy Duration Guard**: Prevents TV series from jumping to the next episode if a connection drop occurs.
- **Position Pinning**: Ensures emergency resume data is only applied to the correct episode/movie, preventing time-leaks.

---

## 🏗️ v1.5: The Stability & RAM Update
Released: Aug 15, 2026

### 🧠 Smart Memory Detection
- **Adaptive Performance**: App probes hardware at startup to optimize for 1GB vs. 8GB+ RAM devices.
- **Dynamic Caching**: Image and video buffers scale based on available heap to prevent OOM crashes on TV boxes.
- **Metadata RAM Guard**: Auto-purges large browsing caches once hardware limits (300-1500 items) are reached.

### 📺 TV Mode Enhancements
- **Episode Hard Reset**: Force-clears WebView memory during autoplay transitions to prevent TV hardware hangs.
- **Prominent Navigation**: Enlarged Seek Feedback (75% bigger) and a dedicated "Next" button for DPAD users.
- **Orientation Control**: Locked mobile to Portrait while allowing Sensor-Landscape for video viewing.

### 🛠️ Bug Fixes
- **Resume Dialog Suppression**: Automatic transitions no longer stop to ask "Would you like to resume?".
- **Fixed Loading Hangs**: Unified resolution job management prevents "zombie" background processes.

---

## 📂 Technical Changes

### Modified Files
1. **VideoExtractor.kt**: Integrated DNA-fingerprinting legitimacy filters.
2. **VideoViewModel.kt**: Added Job tracking and autonomous domain learning.
3. **VideoPlayerScreen.kt**: Implemented Content Identity Lock and Healthy Duration Guard.
4. **NetworkConfig.kt**: Updated header profiles for new domain clusters.
5. **PreferenceManager.kt**: Added Smart RAM tiering defaults.
6. **MainActivity.kt**: Implemented dynamic orientation locking.

### Validation
- ✅ Compilation Successful
- ✅ Release R8 Minification Verified
- ✅ Autoplay transition stress-test passed (TV & Mobile)
- ✅ RAM usage verified stable on 1GB simulated device
