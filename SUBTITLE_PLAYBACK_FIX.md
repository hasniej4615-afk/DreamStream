# Subtitle Playback Fix - Comprehensive Solutions

## Issues Identified & Fixed

### 1. **File URI Handling Issue**
**Problem**: `file://` URLs were not being properly converted to Android-compatible file URIs, causing ExoPlayer to fail loading local cached subtitle files.

**Solution**:
- Modified `VideoPlayerScreen.kt` to convert `file://` URLs using `android.net.Uri.fromFile()` for better compatibility
- Added proper validation before attempting to use file paths
- Skip subtitle if file doesn't exist instead of passing invalid URIs to ExoPlayer

**Files Modified**: `VideoPlayerScreen.kt` (lines 245-279)

**Code Change**:
```kotlin
if (subUrl.startsWith("file://")) {
    val path = subUrl.substringAfter("file://")
    val file = java.io.File(path)
    if (!file.exists()) {
        Log.e("VideoPlayer", "Subtitle file does not exist at: $path - subtitle will not be loaded")
        return@let  // Skip this subtitle if file doesn't exist
    } else {
        Log.d("VideoPlayer", "Subtitle file verified: $path (${file.length()} bytes)")
        // Use proper Android file URI for better compatibility
        subUrl = android.net.Uri.fromFile(file).toString()
    }
}
```

---

### 2. **Data URI MIME Type Issue**
**Problem**: When subtitle caching failed, the app fell back to Base64 data URIs without proper MIME types, causing ExoPlayer's subtitle parser to fail.

**Solution**:
- Enhanced `SubtitleExtractor.saveToCache()` to properly detect and apply the correct MIME type for data URIs
- Added fallback MIME type detection based on filename extension (.srt, .vtt, .ass)
- Improved error logging when caching fails

**Files Modified**: `SubtitleExtractor.kt` (lines 172-197)

**Code Change**:
```kotlin
private fun saveToCache(fileName: String, bytes: ByteArray): String? {
    val dir = cacheDir ?: return run {
        // Fallback: Use data URI with proper MIME type detection
        val mimeType = when {
            fileName.lowercase().endsWith(".vtt") -> "text/vtt"
            fileName.lowercase().endsWith(".ass") || fileName.lowercase().endsWith(".ssa") -> "text/x-ssa"
            else -> "application/x-subrip"
        }
        Log.d(TAG, "Using data URI fallback with MIME: $mimeType")
        "data:$mimeType;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
    }
    // ... rest of implementation with fallback to data URI if file write fails
}
```

---

### 3. **Subtitle Download & Extraction Failure Logging**
**Problem**: When subtitle downloads failed or extraction failed, there was insufficient logging to diagnose the issue.

**Solution**:
- Added comprehensive logging throughout the `downloadAndExtractSubtitle()` function
- Log downloaded file size and content type
- Log GZIP decompression attempts
- Log ZIP extraction progress
- Provide detailed error messages on content validation failure

**Files Modified**: `SubtitleExtractor.kt` (lines 81-170)

---

### 4. **Subtitle Content Validation Too Strict**
**Problem**: The `isValidSubtitleContent()` function had a 50-byte minimum which was causing valid but small subtitle files to be rejected.

**Solution**:
- Reduced minimum file size from 50 bytes to 20 bytes
- Enhanced validation to explicitly reject HTML pages and error JSON responses
- Added detailed logging of validation results with content preview on failure
- Improved handling of different character encodings (UTF-16, UTF-8, Windows-1252, ISO-8859-1)

**Files Modified**: `SubtitleExtractor.kt` (lines 199-239)

**Code Change**:
```kotlin
private fun isValidSubtitleContent(bytes: ByteArray): Boolean {
    if (bytes.size < 20) {  // Reduced from 50
        Log.d(TAG, "isValidSubtitleContent: File too small (${bytes.size} bytes)")
        return false
    }
    // ... enhanced validation logic with better error detection
    
    // Reject API error responses
    if (lowerContent.contains("\"error\"") || lowerContent.contains("\"status\"") && lowerContent.contains("\"message\"")) {
        Log.d(TAG, "isValidSubtitleContent: Detected error response")
        return false
    }
}
```

---

### 5. **Subtitle Resolution URL Validation**
**Problem**: When `resolveSubtitleUrl()` returned null, there was no logging to understand why.

**Solution**:
- Enhanced `resolveSubtitleUrl()` with proper error handling and logging
- Added checks for empty URLs
- Log failed download attempts with specific error messages
- Distinguish between data URIs, file URIs, and HTTP URLs

**Files Modified**: `SubtitleExtractor.kt` (lines 50-79)

---

### 6. **Subtitle Selection Error Handling**
**Problem**: When users selected a subtitle that couldn't be resolved, error messages were vague.

**Solution**:
- Improved error messages in `VideoViewModel.selectSubtitle()` to be more informative
- Added logging of resolution attempts for debugging
- Distinguish between resolution failures and network issues
- Clear selected subtitle if resolution fails

**Files Modified**: `VideoViewModel.kt` (lines 954-994)

**Code Change**:
```kotlin
fun selectSubtitle(subtitle: Subtitle?) {
    // ... setup code ...
    viewModelScope.launch {
        _isSubtitleLoading.value = true
        try {
            if (needsResolution) {
                Log.d("VideoViewModel", "Resolving subtitle URL for: ${subtitle.label}")
                val resolvedUrl = SubtitleExtractor.resolveSubtitleUrl(subtitle.url)
                if (resolvedUrl != null && resolvedUrl.isNotEmpty()) {
                    _selectedSubtitle.value = subtitle.copy(url = resolvedUrl)
                    Log.d("VideoViewModel", "Resolved subtitle URL successfully: $resolvedUrl")
                } else {
                    Log.e("VideoViewModel", "Could not resolve subtitle URL...")
                    _subtitleError.value = "Subtitle link is broken or unavailable. Try another subtitle."
                }
            }
        } catch (e: Exception) {
            Log.e("VideoViewModel", "Error selecting subtitle: ${e.message}", e)
            _subtitleError.value = "Failed to load subtitle. Check your connection and try again."
        }
    }
}
```

---

### 7. **Auto-Subtitle Selection Robustness**
**Problem**: When auto-selecting subtitles in the preferred language, if the first matching subtitle couldn't be resolved, the entire feature would fail silently.

**Solution**:
- Enhanced `fetchSubtitles()` to try multiple subtitle matches (up to 10) before giving up
- Log each resolution attempt for debugging
- Distinguish between "no matches found" and "matches found but couldn't resolve them"
- Improved fallback behavior

**Files Modified**: `VideoViewModel.kt` (lines 879-934)

**Code Change**:
```kotlin
// Try resolving top matches until one works
var resolved = false
val maxRetries = 10
for (match in matchingResults.take(maxRetries)) {
    try {
        Log.d("VideoViewModel", "Auto-selection: Trying ${match.label}...")
        val resolvedUrl = SubtitleExtractor.resolveSubtitleUrl(match.url)
        if (resolvedUrl != null && resolvedUrl.isNotEmpty()) {
            Log.d("VideoViewModel", "Auto-selection SUCCESS: Resolved ${match.label}")
            _selectedSubtitle.value = match.copy(url = resolvedUrl)
            resolved = true
            break
        } else {
            Log.w("VideoViewModel", "Auto-selection: Resolution returned null for ${match.label}")
        }
    } catch (e: Exception) {
        Log.w("VideoViewModel", "Auto-selection: Exception resolving ${match.label}: ${e.message}")
    }
}

if (!resolved) {
    Log.w("VideoViewModel", "Auto-selection: Could not resolve any subtitle for $preferredLang")
}
```

---

### 8. **Track Selection Parameters**
**Problem**: ExoPlayer's track selection parameters weren't being properly configured to ensure subtitle tracks are enabled.

**Solution**:
- Explicitly set track type enabling for text tracks: `setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)`
- Added logging of track selection parameter updates for debugging

**Files Modified**: `VideoPlayerScreen.kt` (lines 283-291)

---

## Testing Recommendations

### Test Case 1: Basic Subtitle Playback
1. Load a video title
2. Wait for subtitle search to complete
3. Select a subtitle from the list
4. Verify subtitle displays on the video player
5. Check logcat for successful subtitle loading messages

### Test Case 2: File Caching
1. Select a subtitle that requires download (should show as loading)
2. Wait for caching to complete
3. Stop and restart video playback
4. Verify subtitle loads from cache (should be instant)
5. Check logcat for cache file path messages

### Test Case 3: Auto-Subtitle Selection
1. Enable auto-subtitle in settings
2. Set default language to Indonesian
3. Load a video
4. Verify subtitle automatically loads without user selection
5. Check logcat for auto-selection attempt messages

### Test Case 4: Error Handling
1. Test with network disabled - should show appropriate error message
2. Test with corrupted subtitle file - should skip to next provider
3. Test with provider that returns error JSON - should handle gracefully
4. All cases should log detailed error information in logcat

### Test Case 5: Track Selection
1. Play a video with subtitle
2. Check Android logcat with filter "VideoPlayer"
3. Should see message: "Tracks changed: X text tracks. Selected: [language]"
4. If empty, verify: "Track Type TEXT is not disabled"

---

## Files Modified Summary

| File | Lines | Changes |
|------|-------|---------|
| `VideoPlayerScreen.kt` | 245-291 | File URI handling, track selection logging |
| `SubtitleExtractor.kt` | 50-239 | Resolution, download, validation, caching enhancements |
| `VideoViewModel.kt` | 879-994 | Auto-selection robustness, subtitle selection error handling |

---

## Logging Tags for Debugging

When troubleshooting subtitle issues, filter logcat for these tags:

- **`SubtitleExtractor`** - Subtitle downloading, extraction, validation
- **`VideoPlayer`** - Player-level subtitle configuration and track selection
- **`VideoViewModel`** - Subtitle search, selection, and auto-selection logic

---

## Key Improvements

✅ **Robustness**: Multiple fallback mechanisms and error handling
✅ **Debuggability**: Comprehensive logging throughout the subtitle pipeline
✅ **Compatibility**: Proper handling of file:// and data: URIs
✅ **Resilience**: Auto-selection tries multiple providers and candidates
✅ **User Experience**: Clear error messages when subtitles fail to load

---

**Date**: May 18, 2026
**Status**: Implementation Complete - Awaiting Build & Testing

