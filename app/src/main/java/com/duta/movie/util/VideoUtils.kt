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

    fun getOriginalImage(url: String): String {
        if (url.isEmpty()) return ""
        if (url.contains("dmcdn.net")) {
            // Dailymotion CDN: x720 is the highest standard resolution for video thumbnails.
            // Suffix /x1080 or /x480 returns 404 from Dailymotion CloudFront CDN.
            return url.replace(Regex("""/x\d+"""), "/x720")
        }
        return url
            .replace(Regex("""/t/p/(?:w\d+|original)/"""), "/t/p/original/")
            .replace(Regex("""[-_]scaled(\.(?:jpg|png|jpeg|webp|gif|bmp))""", RegexOption.IGNORE_CASE), "$1")
            .replace(Regex("""-\d+x\d+(\.(?:jpg|png|jpeg|webp|gif|bmp))""", RegexOption.IGNORE_CASE), "$1")
            .replace(Regex("""/x\d+"""), "/x1080")
            .replace(Regex("""=s\d+([-c])?"""), "=s1200")
            .replace(Regex("""/s\d+(-c)?/"""), "/s1200/")
            .replace(Regex("""/w\d+-h\d+/"""), "/w1200-h675/")
            .replace(Regex("""_SY\d+_"""), "_SY1200_")
            .replace(Regex("""_SX\d+_"""), "_SX1200_")
            .replace(Regex("""/scale_to_width/\d+/"""), "/scale_to_width/1200/")
            .replace(Regex("""\?resize=\d+,\d+"""), "")
            .replace(Regex("""&resize=\d+,\d+"""), "")
            .replace(Regex("""\?fit=\d+,\d+"""), "")
            .replace(Regex("""&fit=\d+,\d+"""), "")
            .replace(Regex("""\?w=\d+&h=\d+"""), "")
            .replace(Regex("""&w=\d+&h=\d+"""), "")
            .replace(Regex("""\?w=\d+"""), "")
            .replace(Regex("""&w=\d+"""), "")
            .replace(Regex("""\?h=\d+"""), "")
            .replace(Regex("""&h=\d+"""), "")
            .replace(Regex("""\?crop=\d+"""), "")
            .replace(Regex("""&crop=\d+"""), "")
            .replace(Regex("""(?<!__ia)_thumb(\.(?:jpg|png|jpeg|webp))""", RegexOption.IGNORE_CASE), "$1")
            .replace("quality=70", "quality=100")
            .replace("?quality=low", "?quality=high")
            .replace("?&", "?")
            .removeSuffix("?")
    }

    /**
     * Optimizes poster resolution based on device type and available hardware memory.
     * TV gets a crystal-clear high resolution (720px - 780px width) instead of blurry 400px.
     */
    fun getOptimizedImage(url: String, isTV: Boolean, isLowRam: Boolean = false): String {
        if (url.isEmpty()) return ""
        val original = getOriginalImage(url)

        val tmdbSize = when {
            isTV && !isLowRam -> "w780"
            isTV && isLowRam -> "w500"
            else -> "w342"
        }
        val wpWidth = when {
            isTV && !isLowRam -> "720"
            isTV && isLowRam -> "500"
            else -> "360"
        }
        val googleSize = when {
            isTV && !isLowRam -> "s800"
            isTV && isLowRam -> "s500"
            else -> "s400"
        }
        val dmSize = "x720"
        val imdbSy = when {
            isTV && !isLowRam -> "_SY1000_"
            isTV && isLowRam -> "_SY600_"
            else -> "_SY500_"
        }
        val imdbSx = when {
            isTV && !isLowRam -> "_SX700_"
            isTV && isLowRam -> "_SX400_"
            else -> "_SX360_"
        }

        return when {
            // TMDB optimization (PencuriMovie, TMDB mirrors)
            original.contains("image.tmdb.org/t/p/") -> {
                original.replace(Regex("""/t/p/(?:original|w\d+)/"""), "/t/p/$tmdbSize/")
            }
            // IMDb / Amazon optimization
            original.contains("media-amazon.com") || original.contains("ia.media-imdb.com") -> {
                original.replace("_SY1200_", imdbSy).replace("_SX1200_", imdbSx)
            }
            // Google/Blogger optimization
            original.contains("bp.blogspot.com") || original.contains("googleusercontent.com") || original.contains("ggpht.com") -> {
                original.replace("=s1200", "=$googleSize").replace("/s1200/", "/$googleSize/")
            }
            // Dailymotion optimization
            original.contains("dmcdn.net") -> {
                original.replace(Regex("""/x\d+"""), "/$dmSize")
            }
            // Bilibili CDN optimization (crops horizontal video thumbnail to vertical 2:3 movie poster)
            original.contains("hdslb.com") -> {
                val biliSuffix = if (isTV) "@320w_480h_1c.webp" else "@240w_360h_1c.webp"
                if (original.contains("@")) original.substringBefore("@") + biliSuffix else original + biliSuffix
            }
            // WordPress/Jetpack/Photon resizing (i0.wp.com, i1.wp.com, i2.wp.com, i3.wp.com, etc.)
            original.contains(Regex("""i\d\.wp\.com""")) || original.contains(".wp.com") -> {
                if (original.contains("?")) "$original&w=$wpWidth" else "$original?w=$wpWidth"
            }
            else -> {
                // Preserve small lightweight thumbnail from site; avoid downloading multi-megabyte master files
                if (url.isNotEmpty()) url else original
            }
        }
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
        val original = getOriginalImage(url)

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

        return when {
            // TMDB backdrop optimization
            original.contains("image.tmdb.org/t/p/") -> {
                original.replace(Regex("""/t/p/(?:original|w\d+)/"""), "/t/p/$tmdbBackdrop/")
            }
            // IMDb / Amazon backdrop
            original.contains("media-amazon.com") || original.contains("ia.media-imdb.com") -> {
                original.replace("_SY1200_", if (isTV) "_SX1920_" else "_SX1280_")
                    .replace("_SX1200_", if (isTV) "_SX1920_" else "_SX1280_")
            }
            // Google/Blogger backdrop
            original.contains("bp.blogspot.com") || original.contains("googleusercontent.com") || original.contains("ggpht.com") -> {
                original.replace("=s1200", "=$googleBackdrop").replace("/s1200/", "/$googleBackdrop/")
            }
            // Dailymotion backdrop
            original.contains("dmcdn.net") -> {
                original.replace(Regex("""/x\d+"""), "/x720")
            }
            // WordPress/Jetpack/Photon backdrop
            original.contains(Regex("""i\d\.wp\.com""")) || original.contains(".wp.com") -> {
                if (original.contains("?")) "$original&w=$wpBackdropWidth" else "$original?w=$wpBackdropWidth"
            }
            else -> original
        }
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
