# Detailed Change Log

## File 1: app/src/main/AndroidManifest.xml

### Before:
```xml
<application
    android:allowBackup="true"
    android:dataExtractionRules="@xml/data_extraction_rules"
    android:fullBackupContent="@xml/backup_rules"
    android:icon="@mipmap/ic_launcher"
    android:label="@string/app_name"
    android:roundIcon="@mipmap/ic_launcher_round"
    android:supportsRtl="true"
    android:theme="@style/Theme.Javvy">
```

### After:
```xml
<application
    android:allowBackup="true"
    android:dataExtractionRules="@xml/data_extraction_rules"
    android:fullBackupContent="@xml/backup_rules"
    android:icon="@mipmap/ic_launcher"
    android:label="@string/app_name"
    android:roundIcon="@mipmap/ic_launcher_round"
    android:supportsRtl="true"
    android:theme="@style/Theme.Javvy"
    android:enableOnBackInvokedCallback="true">
```

**Change**: Added `android:enableOnBackInvokedCallback="true"` to fix back navigation on Android 13+.

---

## File 2: app/src/main/java/com/ikeike/javvy/util/VideoExtractor.kt

### Key Improvements:

**1. Enhanced Pattern Objects (Lines 64-67):**
- Added support for `.mkv`, `.webm`, `.mov`, `.flv` formats
- Added JSON-style URL pattern: `(?:url|src|source|src_alt|video_url|urls)` 
- Improved Base64 pattern with case-insensitive flag

**2. New Video Element Detection (Lines 117-127):**
```kotlin
// Check video/source elements directly
doc.select("video, source").forEach { elem ->
    val src = elem.attr("src").ifEmpty { elem.attr("data-src") }
    if (src.isNotEmpty() && src.startsWith("http")) {
        val url = sanitizeUrl(src)
        if (url.isNotEmpty()) {
            foundUrls.add(url)
            Log.d(TAG, "Found from video element: $url")
        }
    }
}
```

**3. Enhanced JSON Pattern Matching:**
```kotlin
val jsonM = jsonUrlPattern.matcher(content)
while (jsonM.find()) {
    val group = jsonM.group(1)
    if (group != null) {
        val url = sanitizeUrl(group)
        if (url.isNotEmpty()) {
            foundUrls.add(url)
            Log.d(TAG, "Found JSON URL: $url")
        }
    }
}
```

**4. Improved Iframe Tunneling:**
- Added filtering for `disqus` ads
- Multiple extraction methods within iframes:
  - Raw video patterns
  - JSON patterns
  - Base64-encoded URLs
  - Direct video/source elements
- Better error handling and logging

**5. Better URL Filtering:**
- Added exclusions: `pixel`, `static.doubleclick`, `googleads`
- Deduplication and filtering of non-video URLs

**6. Enhanced Priority Tiers:**
```kotlin
// Tier 1: HLS Master Manifest (best quality)
// Tier 2: Full MP4
// Tier 2.5: High-bitrate formats (MKV, WebM, MOV)
// Tier 3: Fallback HLS
// Tier 3.5: TS Transport Streams
// Tier 4: Any remaining candidate
```

**7. Comprehensive Logging:**
Every extraction step logs detailed information for debugging.

---

## File 3: app/src/main/java/com/ikeike/javvy/util/NetworkConfig.kt

### Before:
```kotlin
val okHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .dns(multiDns)
        .cookieJar(cookieJar)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
}
```

### After:
```kotlin
val okHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .dns(multiDns)
        .cookieJar(cookieJar)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()
}
```

**Changes:**
- `connectTimeout`: 20s → 30s
- `readTimeout`: 20s → 30s
- `writeTimeout`: 20s → 30s
- Added `.callTimeout(60, TimeUnit.SECONDS)` for overall request deadline

---

## File 4: app/src/main/java/com/ikeike/javvy/ui/VideoViewModel.kt

### Before:
```kotlin
fun resolveVideoUrl(videoId: String) {
    viewModelScope.launch {
        val video = _videos.value.find { it.id == videoId }
        video?.let {
            _isLoading.value = true
            _error.value = null
            _extractedUrl.value = null
            try {
                val directUrl = VideoExtractor.extractVideoUrl(it.videoUrl)
                if (directUrl != null) {
                    _extractedUrl.value = directUrl
                } else {
                    _error.value = "Failed to extract direct video link."
                }
            } catch (e: Exception) {
                _error.value = "Extraction error: ${e.message}"
                Log.e("VideoViewModel", "Extraction Error", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
```

### After:
```kotlin
fun resolveVideoUrl(videoId: String) {
    viewModelScope.launch {
        val video = _videos.value.find { it.id == videoId }
        video?.let {
            _isLoading.value = true
            _error.value = null
            _extractedUrl.value = null
            try {
                Log.i("VideoViewModel", "Starting extraction for: ${it.videoUrl}")
                val directUrl = VideoExtractor.extractVideoUrl(it.videoUrl)
                if (directUrl != null) {
                    _extractedUrl.value = directUrl
                    Log.i("VideoViewModel", "Successfully extracted URL: $directUrl")
                } else {
                    _error.value = "Failed to extract direct video link. The site structure may have changed or the video may be unavailable."
                    Log.e("VideoViewModel", "Extraction returned null for: ${it.videoUrl}")
                }
            } catch (e: SocketTimeoutException) {
                _error.value = "Timeout during video extraction. Please check your connection and try again."
                Log.e("VideoViewModel", "Socket Timeout during extraction", e)
            } catch (e: UnknownHostException) {
                _error.value = "DNS Error: Unable to resolve video source. Check your internet connection."
                Log.e("VideoViewModel", "DNS Error during extraction", e)
            } catch (e: Exception) {
                _error.value = "Extraction error: ${e.message ?: "Unknown error"}"
                Log.e("VideoViewModel", "Extraction Error", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
```

**Changes:**
- Added logging when extraction starts
- Added logging on successful extraction
- Added specific handling for `SocketTimeoutException`
- Added specific handling for `UnknownHostException`
- Improved user-facing error messages
- Better logging for all error conditions

---

## Summary of Lines Changed

| File | Lines Changed | Type |
|------|------|------|
| AndroidManifest.xml | 1 attribute added | XML configuration |
| VideoExtractor.kt | ~130 lines | Enhanced extraction logic |
| NetworkConfig.kt | 4 timeout values | Network tuning |
| VideoViewModel.kt | ~25 lines | Error handling & logging |

---

## Backward Compatibility

✅ All changes are backward compatible:
- No API changes
- No dependency changes
- No breaking changes to existing code
- Graceful fallback handling for improved features

---

## Testing Checklist

- [ ] Build compiles without critical errors
- [ ] App launches successfully
- [ ] Video list loads on home screen
- [ ] Clicking a video initiates extraction
- [ ] Back button works smoothly on Android 13+
- [ ] Error messages display properly on extraction failures
- [ ] Videos play once extraction succeeds
- [ ] Network timeouts handled gracefully

