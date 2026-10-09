package com.duta.movie.util

import com.duta.movie.model.Subtitle
import android.util.Log
import java.util.Locale

/**
 * Intelligent Subtitle Synchronization & Auto-Matching Engine.
 *
 * Solves streaming subtitle desynchronization by:
 * 1. Extracting the video stream's encode signature (resolution, source, platform, release group).
 * 2. Multi-factor scoring subtitle candidates from all providers to select the exact release match.
 * 3. Prioritizing embedded stream tracks (hardware PTS timestamp synchronization).
 * 4. Detecting known provider pre-roll bumpers (LK21, Rebahin, etc.).
 * 5. Inheriting calibrated offsets across all episodes of a TV series.
 */
object SubtitleSyncEngine {

    data class StreamSignature(
        val quality: String = "",       // e.g. "1080p", "720p", "4k"
        val source: String = "",        // e.g. "WEB-DL", "WEBRip", "BluRay", "HDTV", "CAM"
        val platform: String = "",      // e.g. "AMZN", "NF", "ATVP", "DSNP", "HMAX"
        val releaseGroup: String = "",  // e.g. "YTS", "RARBG", "PSA", "GalaxyRG", "FLUX"
        val serverHost: String = ""     // e.g. "rebahin", "lk21", "pencuri", "streamtape"
    )

    /**
     * Extracts encode signature from title, quality string, server URL, and stream URL.
     */
    fun extractSignature(
        title: String,
        quality: String? = null,
        serverUrl: String? = null,
        streamUrl: String? = null
    ): StreamSignature {
        val combined = "$title ${quality ?: ""} ${serverUrl ?: ""} ${streamUrl ?: ""}".lowercase()

        // 1. Resolution
        val detectedQuality = when {
            combined.contains("2160p") || combined.contains("4k") || combined.contains("uhd") -> "2160p"
            combined.contains("1080p") || combined.contains("fhd") -> "1080p"
            combined.contains("720p") || combined.contains("hd") -> "720p"
            combined.contains("480p") || combined.contains("sd") -> "480p"
            else -> quality?.trim() ?: ""
        }

        // 2. Source Type
        val detectedSource = when {
            Regex("""(?i)\b(?:cam|hd-?cam|hdcam|telesync|hd-?ts|hdts|predvd)\b""").containsMatchIn(combined) -> "CAM"
            Regex("""(?i)\b(?:web-?dl|webdl)\b""").containsMatchIn(combined) -> "WEB-DL"
            Regex("""(?i)\b(?:web-?rip|webrip)\b""").containsMatchIn(combined) -> "WEBRip"
            Regex("""(?i)\b(?:blu-?ray|bluray|bdrip|brrip)\b""").containsMatchIn(combined) -> "BluRay"
            Regex("""(?i)\b(?:hdtv)\b""").containsMatchIn(combined) -> "HDTV"
            Regex("""(?i)\b(?:hd-?rip|hdrip|dvdrip)\b""").containsMatchIn(combined) -> "HDRip"
            detectedQuality == "1080p" || detectedQuality == "2160p" -> "WEB-DL" // Default web standard
            else -> ""
        }

        // 3. Platform
        val detectedPlatform = when {
            Regex("""(?i)\b(?:amzn|amazon)\b""").containsMatchIn(combined) -> "AMZN"
            Regex("""(?i)\b(?:nf|netflix)\b""").containsMatchIn(combined) -> "NF"
            Regex("""(?i)\b(?:atvp|apple)\b""").containsMatchIn(combined) -> "ATVP"
            Regex("""(?i)\b(?:dsnp|disney)\b""").containsMatchIn(combined) -> "DSNP"
            Regex("""(?i)\b(?:hmax|hbomax|max)\b""").containsMatchIn(combined) -> "HMAX"
            Regex("""(?i)\b(?:hulu)\b""").containsMatchIn(combined) -> "HULU"
            Regex("""(?i)\b(?:iqiyi)\b""").containsMatchIn(combined) -> "iQIYI"
            Regex("""(?i)\b(?:wetv)\b""").containsMatchIn(combined) -> "WeTV"
            else -> ""
        }

        // 4. Release Group / Encoder
        val knownGroups = listOf("yts", "yify", "rarbg", "galaxyrg", "psa", "pahe", "sparks", "flux", "ntb", "evo", "qman")
        val detectedGroup = knownGroups.find { Regex("""(?i)\b$it\b""").containsMatchIn(combined) }?.uppercase() ?: ""

        // 5. Host identifier
        val detectedHost = when {
            combined.contains("rebahin") -> "rebahin"
            combined.contains("lk21") || combined.contains("layarkaca21") -> "lk21"
            combined.contains("pencuri") -> "pencuri"
            combined.contains("indoxxi") -> "indoxxi"
            combined.contains("bioskopkeren") -> "bioskopkeren"
            combined.contains("moviebox") || combined.contains("vskit") -> "moviebox"
            else -> ""
        }

        return StreamSignature(
            quality = detectedQuality,
            source = detectedSource,
            platform = detectedPlatform,
            releaseGroup = detectedGroup,
            serverHost = detectedHost
        )
    }

    /**
     * Calculates synchronization fitness score for a subtitle candidate against a stream signature.
     * Higher scores represent closer release alignment and lower risk of desync.
     */
    fun scoreSubtitle(sub: Subtitle, signature: StreamSignature): Int {
        var score = 100
        val rawText = "${sub.label} ${sub.url}".lowercase()

        // 1. Hardware / Stream / Embedded Track (Direct PTS sync: ALWAYS TOP PRIORITY)
        if (sub.url.startsWith("embedded://") ||
            sub.label.startsWith("[Stream]") ||
            sub.label.startsWith("[Embedded]") ||
            rawText.contains("[stream]") ||
            rawText.contains("[embedded]")
        ) {
            return 10000
        }

        val isSubCam = rawText.contains("cam") || rawText.contains("telesync") ||
                       rawText.contains("hdts") || rawText.contains("hd-ts") ||
                       rawText.contains("predvd")

        // If stream is HD/WEB/BluRay and subtitle is CAM, heavily demote!
        if (isSubCam && signature.source != "CAM") {
            return -500
        }

        // If stream is CAM and subtitle is CAM, promote
        if (isSubCam && signature.source == "CAM") {
            score += 80
        }

        // 2. Source Match
        if (signature.source.isNotEmpty()) {
            val sigSrc = signature.source.lowercase()
            if (sigSrc == "web-dl" || sigSrc == "webdl") {
                if (rawText.contains("web-dl") || rawText.contains("webdl")) score += 60
                else if (rawText.contains("webrip") || rawText.contains("web")) score += 40
                else if (rawText.contains("bluray") || rawText.contains("blu-ray") || rawText.contains("bdrip")) score += 20
            } else if (sigSrc == "webrip") {
                if (rawText.contains("webrip")) score += 60
                else if (rawText.contains("web-dl") || rawText.contains("webdl")) score += 45
                else if (rawText.contains("bluray") || rawText.contains("blu-ray")) score += 20
            } else if (sigSrc == "bluray") {
                if (rawText.contains("bluray") || rawText.contains("blu-ray") || rawText.contains("bdrip") || rawText.contains("brrip")) score += 60
                else if (rawText.contains("web-dl") || rawText.contains("webrip")) score += 30
            } else if (sigSrc == "hdtv") {
                if (rawText.contains("hdtv")) score += 60
                else if (rawText.contains("web-dl") || rawText.contains("webrip")) score += 20
            }
        } else {
            // Default prefer standard retail rips (WEB-DL / BluRay) over CAM/TS
            if (rawText.contains("web-dl") || rawText.contains("webdl")) score += 40
            else if (rawText.contains("webrip")) score += 30
            else if (rawText.contains("bluray") || rawText.contains("blu-ray")) score += 25
        }

        // 3. Platform Match (Netflix, Amazon, Disney, Apple)
        if (signature.platform.isNotEmpty()) {
            val sigPlat = signature.platform.lowercase()
            if (rawText.contains(sigPlat)) {
                score += 45
            }
        }

        // 4. Resolution Match (1080p, 720p, 2160p)
        if (signature.quality.isNotEmpty()) {
            val sigQual = signature.quality.lowercase()
            if (rawText.contains(sigQual)) {
                score += 30
            }
        }

        // 5. Release Group Match
        if (signature.releaseGroup.isNotEmpty()) {
            val sigGrp = signature.releaseGroup.lowercase()
            if (rawText.contains(sigGrp)) {
                score += 50
            }
        }

        // 6. Trusted Encoders / Groups
        val trustedGroups = listOf("rarbg", "yts", "yify", "flux", "psa", "galaxyrg", "pahe", "sparks", "ntb", "evo", "qman")
        if (trustedGroups.any { rawText.contains(it) }) {
            score += 20
        }

        // 7. Verified Subtitle Providers (SubDL, OpenSubtitles)
        if (rawText.contains("[subdl]")) score += 15
        if (rawText.contains("[opensubtitles]")) score += 15
        if (rawText.contains("[subsource]")) score += 10

        // 8. Hearing Impaired / SDH flag bonus (official transcripts have high timing accuracy)
        if (rawText.contains(" sdh") || rawText.contains(".sdh") || rawText.contains(" hi ") || rawText.contains(".hi.")) {
            score += 10
        }

        return score
    }

    /**
     * Sorts candidates in descending order of synchronization fitness score.
     */
    fun sortAndRankSubtitles(subtitles: List<Subtitle>, signature: StreamSignature): List<Subtitle> {
        return subtitles.sortedByDescending { scoreSubtitle(it, signature) }
    }

    /**
     * Finds the single best subtitle candidate for a target language using stream signature matching.
     */
    fun findBestSubtitleMatch(
        subtitles: List<Subtitle>,
        targetLanguage: String,
        signature: StreamSignature
    ): Subtitle? {
        if (subtitles.isEmpty() || targetLanguage == "Off" || targetLanguage == "None") return null
        val normTarget = SubtitleExtractor.normalizeLanguage(targetLanguage)

        val matchingLanguageSubs = subtitles.filter {
            SubtitleExtractor.normalizeLanguage(it.language).equals(normTarget, ignoreCase = true)
        }
        if (matchingLanguageSubs.isEmpty()) return null

        return matchingLanguageSubs.maxByOrNull { scoreSubtitle(it, signature) }
    }

    /**
     * Estimates pre-roll intro bumper offset for known web streaming distributors.
     */
    fun detectServerIntroBumper(serverUrl: String?, streamUrl: String?): Long {
        val target = "${serverUrl ?: ""} ${streamUrl ?: ""}".lowercase()
        val detected = when {
            target.contains("rebahin") -> 10000L      // Rebahin standard 10s intro bumper
            target.contains("lk21") || target.contains("layarkaca21") -> 9500L // LK21 9.5s betting bumper
            target.contains("indoxxi") -> 8000L     // Indoxxi 8s intro bumper
            target.contains("bioskopkeren") -> 6000L // Bioskopkeren 6s bumper
            else -> 0L
        }
        if (detected != 0L) {
            Log.i("SubtitleSyncEngine", "Detected bumper on host: offset=${detected}ms (target: $target)")
        }
        return detected
    }

    /**
     * Extracts clean base series identifier from episode videoId for offset inheritance.
     */
    fun extractSeriesBaseId(videoId: String): String {
        return videoId
            .replace(Regex("""(?i)[-_]?(?:season|s)[-_]?\d+[-_]?(?:episode|ep|e)[-_]?\d+.*$"""), "")
            .replace(Regex("""(?i)[-_]?(?:episode|ep|e)[-_]?\d+.*$"""), "")
            .replace(Regex("""(?i)[-_]?(?:season|s)[-_]?\d+.*$"""), "")
            .trimEnd('-', '_')
    }
}
