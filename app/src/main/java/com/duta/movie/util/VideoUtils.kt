package com.duta.movie.util

import com.duta.movie.model.Video
import java.util.Locale

object VideoUtils {
    
    /**
     * Generates an aggressive unique fingerprint for a video to prevent duplicates
     * even if IDs or URLs differ.
     */
    fun getFingerprint(video: Video): String {
        // 1. Try to find a product code (e.g., SSIS-558) in the title
        val codeRegex = Regex("""(?i)([a-z]{2,6}-?\d{3,6})""")
        val codeMatch = codeRegex.find(video.title)
        if (codeMatch != null) {
            return "code_${codeMatch.value.uppercase().replace("-", "")}"
        }

        // 2. Normalize title (remove resolution, "reducing", durations, etc)
        val normalizedTitle = video.title.lowercase(Locale.ROOT)
            .replace(Regex("""(?i)reducing|fhd|hd|4k|720p|1080p|vhs|censored|uncensored|mosaic|fullhd|highquality|compressed|bluray|remastered"""), "")
            .replace(Regex("""\d{1,2}:\d{2}(?::\d{2})?"""), "") // Remove durations
            .replace(Regex("""[^a-z0-9]"""), "") // Remove non-alphanumeric
            .trim()
        
        if (normalizedTitle.length > 5) {
            return "title_$normalizedTitle"
        }

        // 3. Fallback to Thumbnail path (aggressive)
        if (video.thumbnailUrl.isNotEmpty()) {
            val cleanThumb = video.thumbnailUrl.split("?").first().removeSuffix("/")
            val segments = cleanThumb.split("/")
            val filename = segments.lastOrNull()?.lowercase() ?: ""
            
            // If the filename is generic, use the parent directory name too
            if (filename.contains("thumb") || filename.contains("poster") || filename.contains("preview") || filename.contains("default")) {
                val parent = segments.getOrNull(segments.size - 2)
                if (parent != null) {
                    return "thumb_${parent}_$filename"
                }
            }
            
            if (filename.length > 3) {
                return "thumb_$filename"
            }
        }

        return "id_${video.id}"
    }

    fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
        }
    }

    private val optimizedImageCache = java.util.concurrent.ConcurrentHashMap<String, String>(2048)
    private val optimizedBackdropCache = java.util.concurrent.ConcurrentHashMap<String, String>(1024)

    private val REGEX_TMDB_PATH = Regex("""/t/p/(?:w\d+|original)/""")
    private val REGEX_SCALED = Regex("""[-_]scaled(\.(?:jpg|png|jpeg|webp|gif|bmp))""", RegexOption.IGNORE_CASE)
    private val REGEX_DIMENSIONS = Regex("""-\d+x\d+(\.(?:jpg|png|jpeg|webp|gif|bmp))""", RegexOption.IGNORE_CASE)
    private val REGEX_DAILYMOTION_X = Regex("""/x\d+""")
    private val REGEX_GOOGLE_S_PARAM = Regex("""=s\d+([-c])?""")
    private val REGEX_GOOGLE_S_PATH = Regex("""/s\d+(-c)?/""")
    private val REGEX_WH = Regex("""/w\d+-h\d+/""")
    private val REGEX_SY = Regex("""_SY\d+_""")
    private val REGEX_SX = Regex("""_SX\d+_""")
    private val REGEX_SCALE_TO_WIDTH = Regex("""/scale_to_width/\d+/""")
    private val REGEX_RESIZE_PARAM_Q = Regex("""\?resize=\d+,\d+""")
    private val REGEX_RESIZE_PARAM_A = Regex("""&resize=\d+,\d+""")
    private val REGEX_FIT_PARAM_Q = Regex("""\?fit=\d+,\d+""")
    private val REGEX_FIT_PARAM_A = Regex("""&fit=\d+,\d+""")
    private val REGEX_W_H_PARAM_Q = Regex("""\?w=\d+&h=\d+""")
    private val REGEX_W_H_PARAM_A = Regex("""&w=\d+&h=\d+""")
    private val REGEX_W_PARAM_Q = Regex("""\?w=\d+""")
    private val REGEX_W_PARAM_A = Regex("""&w=\d+""")
    private val REGEX_H_PARAM_Q = Regex("""\?h=\d+""")
    private val REGEX_H_PARAM_A = Regex("""&h=\d+""")
    private val REGEX_CROP_PARAM_Q = Regex("""\?crop=\d+""")
    private val REGEX_CROP_PARAM_A = Regex("""&crop=\d+""")
    private val REGEX_THUMB = Regex("""(?<!__ia)_thumb(\.(?:jpg|png|jpeg|webp))""", RegexOption.IGNORE_CASE)
    private val REGEX_WP_CDN = Regex("""i\d\.wp\.com""")

    fun getOriginalImage(url: String): String {
        if (url.isEmpty()) return ""
        if (url.contains("dmcdn.net")) {
            // Dailymotion CDN: x720 is the highest standard resolution for video thumbnails.
            // Suffix /x1080 or /x480 returns 404 from Dailymotion CloudFront CDN.
            return REGEX_DAILYMOTION_X.replace(url, "/x720")
        }
        return url
            .let { REGEX_TMDB_PATH.replace(it, "/t/p/original/") }
            .let { REGEX_SCALED.replace(it, "$1") }
            .let { REGEX_DIMENSIONS.replace(it, "$1") }
            .let { REGEX_DAILYMOTION_X.replace(it, "/x1080") }
            .let { REGEX_GOOGLE_S_PARAM.replace(it, "=s1200") }
            .let { REGEX_GOOGLE_S_PATH.replace(it, "/s1200/") }
            .let { REGEX_WH.replace(it, "/w1200-h675/") }
            .let { REGEX_SY.replace(it, "_SY1200_") }
            .let { REGEX_SX.replace(it, "_SX1200_") }
            .let { REGEX_SCALE_TO_WIDTH.replace(it, "/scale_to_width/1200/") }
            .let { REGEX_RESIZE_PARAM_Q.replace(it, "") }
            .let { REGEX_RESIZE_PARAM_A.replace(it, "") }
            .let { REGEX_FIT_PARAM_Q.replace(it, "") }
            .let { REGEX_FIT_PARAM_A.replace(it, "") }
            .let { REGEX_W_H_PARAM_Q.replace(it, "") }
            .let { REGEX_W_H_PARAM_A.replace(it, "") }
            .let { REGEX_W_PARAM_Q.replace(it, "") }
            .let { REGEX_W_PARAM_A.replace(it, "") }
            .let { REGEX_H_PARAM_Q.replace(it, "") }
            .let { REGEX_H_PARAM_A.replace(it, "") }
            .let { REGEX_CROP_PARAM_Q.replace(it, "") }
            .let { REGEX_CROP_PARAM_A.replace(it, "") }
            .let { REGEX_THUMB.replace(it, "$1") }
            .replace("quality=70", "quality=100")
            .replace("?quality=low", "?quality=high")
            .replace("?&", "?")
            .removeSuffix("?")
    }

    /**
     * Optimizes poster resolution with lightweight CDN dimensions (342px - 400px width).
     * Unified across TV and Mobile so disk/memory cache is 100% shared and loads instantly.
     */
    fun getOptimizedImage(url: String, isTV: Boolean = false, isLowRam: Boolean = false): String {
        if (url.isEmpty()) return ""
        optimizedImageCache[url]?.let { return it }

        val tmdbSize = "w342"
        val wpWidth = "360"
        val googleSize = "s400"
        val dmSize = "x720"
        val imdbSy = "_SY500_"
        val imdbSx = "_SX360_"

        val result = when {
            // TMDB Fast-Path & Optimization (covers 80%+ of movies)
            url.contains("image.tmdb.org/t/p/") -> {
                if (url.contains("/t/p/$tmdbSize/")) url
                else REGEX_TMDB_PATH.replace(url, "/t/p/$tmdbSize/")
            }
            // Google/Blogger Fast-Path & Optimization
            url.contains("bp.blogspot.com") || url.contains("googleusercontent.com") || url.contains("ggpht.com") -> {
                if (url.contains("=$googleSize") || url.contains("/$googleSize/")) url
                else REGEX_GOOGLE_S_PARAM.replace(REGEX_GOOGLE_S_PATH.replace(url, "/$googleSize/"), "=$googleSize")
            }
            // Dailymotion Fast-Path & Optimization
            url.contains("dmcdn.net") -> {
                if (url.contains("/$dmSize")) url
                else REGEX_DAILYMOTION_X.replace(url, "/$dmSize")
            }
            // Bilibili CDN optimization (crops horizontal video thumbnail to vertical 2:3 movie poster)
            url.contains("hdslb.com") -> {
                val biliSuffix = "@240w_360h_1c.webp"
                if (url.endsWith(biliSuffix)) url
                else if (url.contains("@")) url.substringBefore("@") + biliSuffix
                else url + biliSuffix
            }
            // IMDb / Amazon optimization
            url.contains("media-amazon.com") || url.contains("ia.media-imdb.com") -> {
                val orig = getOriginalImage(url)
                orig.replace("_SY1200_", imdbSy).replace("_SX1200_", imdbSx)
            }
            // WordPress/Jetpack/Photon resizing (i0.wp.com, i1.wp.com, i2.wp.com, i3.wp.com, etc.)
            url.contains(REGEX_WP_CDN) || url.contains(".wp.com") -> {
                if (url.contains("w=$wpWidth")) url
                else {
                    val orig = getOriginalImage(url)
                    if (orig.contains("?")) "$orig&w=$wpWidth" else "$orig?w=$wpWidth"
                }
            }
            else -> url
        }

        if (optimizedImageCache.size > 4000) optimizedImageCache.clear()
        optimizedImageCache[url] = result
        return result
    }

    /**
     * Context-aware overload for poster resolution.
     */
    fun getOptimizedImage(url: String, isTV: Boolean, context: android.content.Context?): String =
        getOptimizedImage(url, isTV, context?.let { isLowRamDevice(it) } ?: false)

    /**
     * Optimizes wide hero banner / backdrop images (16:9 widescreen).
     * Backdrops need higher width (1280px - 1920px) on TV to avoid blurriness.
     */
    fun getOptimizedBackdrop(url: String, isTV: Boolean, isLowRam: Boolean = false): String {
        if (url.isEmpty()) return ""
        val cacheKey = "${isTV}_${isLowRam}_$url"
        optimizedBackdropCache[cacheKey]?.let { return it }

        val tmdbBackdrop = when {
            isTV && isLowRam -> "w780"
            isTV -> "w1280"
            else -> "w780"
        }
        val wpBackdropWidth = when {
            isTV && isLowRam -> "960"
            isTV -> "1280"
            else -> "720"
        }
        val googleBackdrop = when {
            isTV && isLowRam -> "s1280"
            isTV -> "s1920"
            else -> "s1024"
        }

        val result = when {
            // TMDB backdrop optimization
            url.contains("image.tmdb.org/t/p/") -> {
                if (url.contains("/t/p/$tmdbBackdrop/")) url
                else REGEX_TMDB_PATH.replace(url, "/t/p/$tmdbBackdrop/")
            }
            // Google/Blogger backdrop
            url.contains("bp.blogspot.com") || url.contains("googleusercontent.com") || url.contains("ggpht.com") -> {
                if (url.contains("=$googleBackdrop") || url.contains("/$googleBackdrop/")) url
                else REGEX_GOOGLE_S_PARAM.replace(REGEX_GOOGLE_S_PATH.replace(url, "/$googleBackdrop/"), "=$googleBackdrop")
            }
            // Dailymotion backdrop
            url.contains("dmcdn.net") -> {
                if (url.contains("/x720")) url
                else REGEX_DAILYMOTION_X.replace(url, "/x720")
            }
            // IMDb / Amazon backdrop
            url.contains("media-amazon.com") || url.contains("ia.media-imdb.com") -> {
                val orig = getOriginalImage(url)
                orig.replace("_SY1200_", if (isTV) "_SX1920_" else "_SX1280_")
                    .replace("_SX1200_", if (isTV) "_SX1920_" else "_SX1280_")
            }
            // WordPress/Jetpack/Photon backdrop
            url.contains(REGEX_WP_CDN) || url.contains(".wp.com") -> {
                if (url.contains("w=$wpBackdropWidth")) url
                else {
                    val orig = getOriginalImage(url)
                    if (orig.contains("?")) "$orig&w=$wpBackdropWidth" else "$orig?w=$wpBackdropWidth"
                }
            }
            else -> url
        }

        if (optimizedBackdropCache.size > 2000) optimizedBackdropCache.clear()
        optimizedBackdropCache[cacheKey] = result
        return result
    }

    /**
     * Context-aware overload for backdrop resolution.
     */
    fun getOptimizedBackdrop(url: String, isTV: Boolean, context: android.content.Context?): String =
        getOptimizedBackdrop(url, isTV, context?.let { isLowRamDevice(it) } ?: false)

    /**
     * Smart RAM detection for adaptive optimizations.
     */
    fun isLowRamDevice(context: android.content.Context): Boolean {
        val activityManager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        // Devices with less than 2GB total RAM or explicitly marked as low-ram by OS
        return memInfo.totalMem < 2 * 1024 * 1024 * 1024L || activityManager.isLowRamDevice
    }

    /**
     * Gets available heap in MB for dynamic cache scaling.
     */
    fun getMemoryClass(context: android.content.Context): Int {
        val activityManager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        return activityManager.memoryClass
    }
}
