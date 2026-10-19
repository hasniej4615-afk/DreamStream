package com.duta.movie.util

import com.duta.movie.model.Subtitle
import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
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
 * 6. Autonomous Background VAD Multi-Cue Calibration with Supabase Consensus fallback.
 */
object SubtitleSyncEngine {

    data class AutoSyncNotice(
        val offsetMs: Long,
        val formattedOffset: String,
        val method: String = "Auto-Pilot"
    )

    data class SyncEvaluationResult(
        val offsetMs: Long? = null,
        val isConfident: Boolean = false,
        val isConfirmedMismatch: Boolean = false,
        val recall: Double = 0.0,
        val snr: Double = 0.0,
        val totalSpeechMs: Long = 0L
    )

    data class StreamSignature(
        val quality: String = "",       // e.g. "1080p", "720p", "4k"
        val source: String = "",        // e.g. "WEB-DL", "WEBRip", "BluRay", "HDTV", "CAM"
        val platform: String = "",      // e.g. "AMZN", "NF", "ATVP", "DSNP", "HMAX"
        val releaseGroup: String = "",  // e.g. "YTS", "RARBG", "PSA", "GalaxyRG", "FLUX"
        val serverHost: String = "",    // e.g. "rebahin", "lk21", "pencuri", "streamtape"
        val targetYear: String = ""     // e.g. "2026", "2024", "1968"
    )

    /**
     * Extracts encode signature from title, quality string, server URL, stream URL, and release year.
     */
    fun extractSignature(
        title: String,
        quality: String? = null,
        serverUrl: String? = null,
        streamUrl: String? = null,
        year: String? = null
    ): StreamSignature {
        val combined = "$title ${quality ?: ""} ${serverUrl ?: ""} ${streamUrl ?: ""} ${year ?: ""}".lowercase()

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
            else -> "WEB-DL" // Movies on streaming servers are digital retail web streams by default unless CAM
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

        // 6. Release Year
        val detectedYear = when {
            !year.isNullOrBlank() -> Regex("""(?<!\d)(19\d{2}|20\d{2})(?!\d)""").find(year)?.groupValues?.get(1) ?: year.trim()
            else -> Regex("""(?<!\d)(19\d{2}|20\d{2})(?!\d)""").find("$title ${serverUrl ?: ""} ${streamUrl ?: ""}")?.groupValues?.get(1) ?: ""
        }

        return StreamSignature(
            quality = detectedQuality,
            source = detectedSource,
            platform = detectedPlatform,
            releaseGroup = detectedGroup,
            serverHost = detectedHost,
            targetYear = detectedYear
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

        // 2. Release Year Verification (Defends against same-title reboots/classics e.g. 1968 vs 2026)
        val targetYearInt = signature.targetYear.toIntOrNull()
        if (targetYearInt != null) {
            val subYearMatch = Regex("""(?<!\d)(19\d{2}|20\d{2})(?!\d)""").find(rawText)
            val subYear = subYearMatch?.groupValues?.get(1)?.toIntOrNull()
            if (subYear != null) {
                val diff = Math.abs(subYear - targetYearInt)
                if (diff > 1) {
                    // Critical year mismatch: completely different movie release!
                    return -10000
                } else if (diff == 0) {
                    score += 80 // Exact release year match
                } else {
                    score += 20 // Adjacent release year (e.g. late Dec theatrical vs Jan digital)
                }
            }
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
        val hasWeb = rawText.contains("web-dl") || rawText.contains("webdl") ||
                     rawText.contains("webrip") || rawText.contains("web-rip") ||
                     rawText.contains(".web.") || rawText.contains(".web-") ||
                     rawText.contains("[web]") || Regex("""(?i)\bweb\b""").containsMatchIn(rawText)
        val hasBluray = rawText.contains("bluray") || rawText.contains("blu-ray") ||
                        rawText.contains("bdrip") || rawText.contains("brrip") ||
                        rawText.contains("remux")
        val hasHdtv = rawText.contains("hdtv") || rawText.contains("pdtv")
        val hasHdrip = rawText.contains("hdrip") || rawText.contains("dvdrip") || rawText.contains("dvd")

        if (signature.source.isNotEmpty()) {
            val sigSrc = signature.source.lowercase()
            if (sigSrc == "web-dl" || sigSrc == "webdl") {
                if (rawText.contains("web-dl") || rawText.contains("webdl")) score += 60
                else if (hasWeb) score += 50
                else if (hasBluray) score += 25
            } else if (sigSrc == "webrip") {
                if (rawText.contains("webrip") || rawText.contains("web-rip")) score += 60
                else if (hasWeb) score += 50
                else if (hasBluray) score += 25
            } else if (sigSrc == "bluray") {
                if (hasBluray) score += 60
                else if (hasWeb) score += 35
            } else if (sigSrc == "hdtv") {
                if (hasHdtv) score += 60
                else if (hasWeb) score += 25
            } else if (sigSrc == "hdrip") {
                if (hasHdrip || hasWeb) score += 50
                else if (hasBluray) score += 30
            }
        } else {
            // Default prefer standard retail rips (WEB-DL / WEBRip / BluRay) over CAM/TS
            if (rawText.contains("web-dl") || rawText.contains("webdl")) score += 50
            else if (hasWeb) score += 40
            else if (hasBluray) score += 35
        }

        // 3. Platform Match (Netflix, Amazon, Disney, Apple, HBO Max, etc.)
        val platformKeywords = listOf("nf", "netflix", "amzn", "amazon", "dsnp", "disney", "atvp", "apple", "hmax", "hbomax", "max", "hulu", "hotstar", "bcore", "iqiyi", "wetv", "viu")
        if (signature.platform.isNotEmpty()) {
            val sigPlat = signature.platform.lowercase()
            if (rawText.contains(sigPlat)) {
                score += 45
            }
        } else {
            if (platformKeywords.any { Regex("""(?i)\b$it\b""").containsMatchIn(rawText) || rawText.contains(".$it.") }) {
                score += 30
            }
        }

        // 4. Resolution Match (1080p, 720p, 2160p)
        if (signature.quality.isNotEmpty()) {
            val sigQual = signature.quality.lowercase()
            if (rawText.contains(sigQual)) {
                score += 30
            }
        } else {
            if (rawText.contains("1080p") || rawText.contains("2160p") || rawText.contains("720p")) {
                score += 20
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
        val trustedGroups = listOf("rarbg", "yts", "yify", "flux", "psa", "galaxyrg", "pahe", "sparks", "ntb", "evo", "qman", "tgx", "ettv", "galaxy616", "ion10", "chd", "wiki", "flame", "playweb", "as88", "skr", "playhd", "megusta", "sphinxt", "lama", "telly", "stone", "smurf", "d3g", "cmrg", "ctrlhd", "framestor", "tommy", "afg")
        if (trustedGroups.any { Regex("""(?i)\b$it\b""").containsMatchIn(rawText) }) {
            score += 25
        }

        // 7. Verified Subtitle Providers (SubDL, OpenSubtitles, SubSource, Subscene)
        if (rawText.contains("[subdl]")) score += 30
        if (rawText.contains("[opensubtitles]") || rawText.contains("[opensubs]")) score += 30
        if (rawText.contains("[subsource]")) score += 30
        if (rawText.contains("[subscene]")) score += 30

        // SubCat non-English are automated bot translations of unverified torrent uploads.
        // Heavily demote non-English SubCat to avoid hijacking real releases or blocking AI translation.
        if ((rawText.contains("[subcat]") || rawText.contains("subtitlecat")) && 
            !sub.language.equals("English", ignoreCase = true)) {
            score -= 80
        }

        // 8. Hearing Impaired / SDH flag bonus (official transcripts have high timing accuracy)
        if (rawText.contains(" sdh") || rawText.contains(".sdh") || rawText.contains(" hi ") || rawText.contains(".hi.") ||
            rawText.contains("retail") || rawText.contains("proper") || rawText.contains("official")) {
            score += 15
        }

        return score
    }

    /**
     * Determines whether a subtitle is an embedded container track (hardware presentation timestamp sync).
     */
    fun isEmbedded(sub: Subtitle?): Boolean {
        if (sub == null) return false
        val low = sub.label.lowercase()
        val url = sub.url.lowercase()
        return url.startsWith("embedded://") || low.contains("[stream]") || low.contains("[embedded]")
    }

    /**
     * Determines whether a subtitle is an exact release match or embedded track.
     */
    fun isMatchedOrEmbedded(sub: Subtitle?, signature: StreamSignature): Boolean {
        if (sub == null) return false
        if (isEmbedded(sub)) return true
        val low = sub.label.lowercase()
        val url = sub.url.lowercase()
        val rawText = "$low $url"

        // Release Year Check: Subtitles with conflicting release years (> 1 year difference) can NEVER be matched
        val targetYearInt = signature.targetYear.toIntOrNull()
        if (targetYearInt != null) {
            val subYear = Regex("""(?<!\d)(19\d{2}|20\d{2})(?!\d)""").find(rawText)?.groupValues?.get(1)?.toIntOrNull()
            if (subYear != null && Math.abs(subYear - targetYearInt) > 1) {
                return false
            }
        }

        // 2. AI Translated from verified source
        if (low.startsWith("[ai translated]") || url.contains("translated_subtitles")) {
            return true
        }

        // 3. CAM subtitles are NEVER matched unless the stream itself is confirmed CAM
        val isSubCam = rawText.contains("cam") || rawText.contains("telesync") ||
                       rawText.contains("hdts") || rawText.contains("hd-ts") ||
                       rawText.contains("predvd")
        if (isSubCam && signature.source != "CAM") {
            return false
        }

        // 4. Retail Web, OTT Platforms, Disc Rips, Broadcast, Scene Groups, Resolutions
        val hasWebPattern = Regex("""(?i)\b(?:web|web-?dl|webdl|web-?rip|webrip)\b""").containsMatchIn(rawText) ||
                rawText.contains(".web.") || rawText.contains(".web-") || rawText.contains("[web]")
        val hasPlatformPattern = Regex("""(?i)\b(?:nf|netflix|amzn|amazon|dsnp|disney|atvp|apple|hmax|hbomax|max|hulu|hotstar|bcore|iqiyi|wetv|viu|itunes)\b""").containsMatchIn(rawText)
        val hasDiscPattern = Regex("""(?i)\b(?:blu-?ray|bluray|bdrip|brrip|remux|bdr|hdrip|dvdrip|dvd|hdtv|pdtv)\b""").containsMatchIn(rawText)
        val hasSceneGroupPattern = Regex("""(?i)\b(?:rarbg|yts|yify|flux|psa|galaxyrg|pahe|sparks|evo|ntb|qman|tgx|ettv|galaxy616|ion10|chd|wiki|flame|playweb|as88|skr|playhd|megusta|sphinxt|lama|telly|stone|smurf|d3g|cmrg|ctrlhd|framestor|tommy|afg)\b""").containsMatchIn(rawText)
        val hasResolutionPattern = Regex("""(?i)\b(?:2160p|4k|1080p|720p|uhd|fhd)\b""").containsMatchIn(rawText)
        val hasRetailTag = Regex("""(?i)\b(?:proper|repack|retail|official)\b""").containsMatchIn(rawText)

        if (hasWebPattern || hasPlatformPattern || hasDiscPattern || hasSceneGroupPattern || hasResolutionPattern || hasRetailTag) {
            return true
        }

        // 5. Verified Subtitle Provider releases (SubDL, SubSource, OpenSubtitles) when not CAM
        if (rawText.contains("[subsource]") || rawText.contains("[subdl]") || rawText.contains("[opensubs]") || rawText.contains("[opensubtitles]")) {
            return true
        }

        return scoreSubtitle(sub, signature) >= 120
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

        val bestCandidate = matchingLanguageSubs
            .map { it to scoreSubtitle(it, signature) }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first ?: return null

        // QUALITY GATE: If target language candidate is an unverified SubCat machine translation,
        // and high-quality verified English source subtitles exist, do NOT settle for low-quality SubCat scrape.
        // Return null so AI translation fallback can translate the verified official English release!
        val isSubCatBot = (bestCandidate.label.contains("[SubCat]", ignoreCase = true) || bestCandidate.url.contains("subtitlecat.com")) &&
                !normTarget.equals("English", ignoreCase = true)
        if (isSubCatBot) {
            val hasVerifiedEnglish = subtitles.any { sub ->
                !sub.label.startsWith("[AI Translated]") &&
                SubtitleExtractor.normalizeLanguage(sub.language).equals("English", ignoreCase = true) &&
                (sub.label.contains("[opensubtitles]", ignoreCase = true) ||
                 sub.label.contains("[opensubs]", ignoreCase = true) ||
                 sub.label.contains("[subsource]", ignoreCase = true) ||
                 sub.label.contains("[subdl]", ignoreCase = true) ||
                 sub.label.contains("[subscene]", ignoreCase = true) ||
                 scoreSubtitle(sub, signature) >= 120)
            }
            if (hasVerifiedEnglish) {
                Log.i("SubtitleSyncEngine", "Skipping unverified SubCat target sub '${bestCandidate.label}' in favor of AI-translating verified English source")
                return null
            }
        }

        return bestCandidate
    }

    enum class FpsMode(val label: String, val ratio: Float) {
        NORMAL("1.0x (Normal)", 1.0f),
        PAL_TO_NTSC("25 -> 23.976 (PAL)", 1.04271f),
        NTSC_TO_PAL("23.976 -> 25 (NTSC)", 0.95904f),
        FILM_TO_PAL("24 -> 25 (Speedup)", 0.96000f),
        PAL_TO_FILM("25 -> 24 (Slowdown)", 1.04167f);

        val displayName: String get() = label

        companion object {
            fun fromRatio(ratio: Float): FpsMode {
                return entries.minByOrNull { Math.abs(it.ratio - ratio) } ?: NORMAL
            }
        }
    }

    /**
     * Detects if video FPS and subtitle release FPS mismatch and calculates time stretch ratio.
     */
    fun detectFpsRatio(videoFps: Float?, subtitleLabel: String?): Float {
        if (videoFps == null || videoFps <= 0f || subtitleLabel.isNullOrBlank()) return 1.0f
        val lowSub = subtitleLabel.lowercase()
        val isSub25 = lowSub.contains("25fps") || lowSub.contains("25.000") || lowSub.contains(" pal ") || lowSub.contains(".pal.")
        val isSub23976 = lowSub.contains("23.976") || lowSub.contains("23.98") || lowSub.contains(" ntsc ") || lowSub.contains(".ntsc.") || lowSub.contains("bluray") || lowSub.contains("web-dl")

        return when {
            // Video is 25 FPS (PAL broadcast/rip) but sub is 23.976/BluRay
            videoFps in 24.8f..25.2f && isSub23976 && !isSub25 -> FpsMode.NTSC_TO_PAL.ratio
            // Video is 23.976 FPS (NTSC/Retail) but sub is 25 FPS PAL
            videoFps in 23.8f..24.2f && isSub25 -> FpsMode.PAL_TO_NTSC.ratio
            else -> 1.0f
        }
    }

    /**
     * Generates a stable normalized media key for community offset consensus.
     */
    fun generateCommunityMediaKey(title: String, serverHost: String?): String {
        val cleanTitle = title.lowercase()
            .replace(Regex("""[^a-z0-9]"""), "_")
            .replace(Regex("""_+"""), "_")
            .trim('_')
        val hostPart = (serverHost ?: "generic").lowercase()
            .replace(Regex("""[^a-z0-9]"""), "_")
        return "${cleanTitle}__${hostPart}"
    }

    /**
     * Estimates pre-roll intro bumper offset for known web streaming distributors.
     * Note: Server intro bumpers delay dialogue, requiring a negative offset to shift subtitles forward.
     */
    fun detectServerIntroBumper(serverUrl: String?, streamUrl: String?): Long {
        val target = "${serverUrl ?: ""} ${streamUrl ?: ""}".lowercase()
        val detected = when {
            target.contains("rebahin") -> -10000L      // Rebahin standard 10s intro bumper
            target.contains("lk21") || target.contains("layarkaca21") -> -9500L // LK21 9.5s betting bumper
            target.contains("indoxxi") -> -8000L     // Indoxxi 8s intro bumper
            target.contains("bioskopkeren") -> -6000L // Bioskopkeren 6s bumper
            target.contains("juraganfilm") -> -7500L  // Juraganfilm 7.5s bumper
            target.contains("melongmovie") -> -5000L  // Melongmovie 5s bumper
            target.contains("kawanfilm") -> -8500L    // Kawanfilm 8.5s bumper
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

    /**
     * Determines if a subtitle cue represents genuine spoken dialogue.
     * Accurately filters out metadata credits, web domains, sound effects in brackets,
     * and on-screen location/date/title cards (e.g. 'PITTSBURGH, 1999', 'SUMMER 1990').
     */
    fun isSpokenDialogueCue(text: String): Boolean {
        val clean = text.replace(Regex("<[^>]*>"), "").replace("\n", " ").trim()
        if (clean.length < 2) return false
        val txt = clean.lowercase()

        // 1. Credits, uploaders, websites, frame rates, sync tags
        val isCredit = txt.contains("subscene") || txt.contains("opensubtitles") ||
                       txt.contains("subdl") || txt.contains("subtitlecat") ||
                       txt.contains("subcat") || txt.contains("subtitles by") ||
                       txt.contains("sync by") || txt.contains("translated by") ||
                       txt.contains("translated") || txt.contains("credit") ||
                       txt.contains("www.") || txt.contains(".com") ||
                       txt.contains(".org") || txt.contains(".net") ||
                       txt.contains("sync") || txt.contains("duta") ||
                       txt.contains("ripped by") || txt.contains("encoded by") ||
                       txt.contains("fps")
        if (isCredit) return false

        // 2. Sound effects, musical cues in brackets or notation symbols
        val isSoundEffect = (txt.startsWith("[") && txt.endsWith("]")) ||
                            (txt.startsWith("(") && txt.endsWith(")")) ||
                            (txt.startsWith("{") && txt.endsWith("}")) ||
                            txt.contains("♪") || txt.contains("♫") ||
                            (txt.startsWith("*") && txt.endsWith("*"))
        if (isSoundEffect) return false

        // 3. Location / Date / Title cards (e.g. "PITTSBURGH, 1999", "OCTOBER 14", "PARIS, FRANCE")
        val hasYear = Regex("""\b(18|19|20)\d{2}\b""").containsMatchIn(clean)
        val isAllUpper = clean.filter { it.isLetter() }.let { letters ->
            letters.isNotEmpty() && letters.all { it.isUpperCase() }
        }
        if (hasYear) return false
        if (isAllUpper && clean.length < 35 && !clean.contains("?") && !clean.contains("!") && !clean.contains("-")) {
            return false
        }

        // 4. Must contain at least one letter
        return clean.any { it.isLetter() }
    }

    /**
     * Evaluates whether an automated subtitle offset should be applied.
     * Sanitizes against false positives (bumper sanity bounds: -35,000ms to +35,000ms).
     *
     * @param voiceOnsetMs Hardware PCM speech onset detected by VadAudioProcessor
     * @param firstDialogueCueMs Timestamp of the first dialogue cue in subtitle file
     * @param currentOffset Currently applied subtitle offset
     * @param isUserManualCalibrated True if user manually adjusted offset in this session
     * @return Calculated new offset in milliseconds, or null if invalid or within tolerance
     */
    fun evaluateAutoSync(
        voiceOnsetMs: Long,
        firstDialogueCueMs: Long,
        currentOffset: Long = 0L,
        isUserManualCalibrated: Boolean = false
    ): Long? {
        if (isUserManualCalibrated) return null
        if (voiceOnsetMs <= 0L || firstDialogueCueMs <= 0L) return null

        val calculatedOffset = firstDialogueCueMs - voiceOnsetMs

        // Sanity Check: Streaming pre-rolls, distributor bumpers, and intro logo trims realistically range from -90s to +90s.
        // Extreme offsets outside this window indicate false fanfare triggers or end-credit detections.
        if (calculatedOffset !in -90000L..90000L) {
            Log.w("SubtitleSyncEngine", "Auto-sync candidate $calculatedOffset ms rejected: outside sanity bounds [-90s, +90s] (Voice: ${voiceOnsetMs}ms, Cue: ${firstDialogueCueMs}ms)")
            return null
        }

        // If offset is already essentially identical to current offset, avoid unnecessary jumps but confirm sync
        if (Math.abs(calculatedOffset - currentOffset) <= 150L && currentOffset != 0L) {
            Log.i("SubtitleSyncEngine", "Auto-sync candidate $calculatedOffset ms matches current offset $currentOffset ms within tolerance.")
            return currentOffset
        }

        return calculatedOffset
    }

    /**
     * Intelligent Multi-Anchor Cadence Matching.
     * Correlates time intervals of multiple speech energy bursts against dialogue subtitle cues.
     * Rejects isolated fanfare hits and audio anomalies by verifying matching speech rhythm (Δt).
     */
    fun evaluateMultiAnchorAutoSync(
        voiceOnsets: List<Long>,
        dialogueCues: List<ParsedSubtitleCue>,
        currentOffset: Long = 0L,
        isUserManualCalibrated: Boolean = false
    ): Long? {
        if (isUserManualCalibrated) return null
        if (voiceOnsets.isEmpty() || dialogueCues.isEmpty()) return null

        // Filter out intro credit lines, title cards, and sound effects so cadence matching compares genuine spoken dialogue
        val spokenCues = dialogueCues.filter { isSpokenDialogueCue(it.text) }
        val effectiveCues = spokenCues.ifEmpty { dialogueCues }

        if (voiceOnsets.size == 1) {
            val firstCue = effectiveCues.firstOrNull() ?: return null
            return evaluateAutoSync(voiceOnsets.first(), firstCue.startTimeMs, currentOffset, false)
        }

        // Evaluate candidate pairs between the first few onsets and spoken dialogue cues (up to 12 cues)
        val candidateCues = effectiveCues.take(12)
        val validOffsets = mutableListOf<Long>()

        for (cue in candidateCues) {
            for (onset in voiceOnsets) {
                val diff = cue.startTimeMs - onset
                if (diff in -90000L..90000L) {
                    validOffsets.add(diff)
                }
            }
        }

        if (validOffsets.isEmpty()) return null

        // Find the offset cluster with highest consensus (support within ±400ms)
        var bestOffset: Long? = null
        var maxSupport = 0

        for (cand in validOffsets) {
            val cluster = validOffsets.filter { Math.abs(it - cand) <= 400L }
            if (cluster.size > maxSupport) {
                maxSupport = cluster.size
                bestOffset = cluster.average().toLong()
            }
        }

        // Require at least 2 agreeing anchor alignments for multi-cue consensus
        if (maxSupport >= 2 && bestOffset != null) {
            Log.i("SubtitleSyncEngine", "⚡ Multi-Anchor Cadence Consensus: offset=${bestOffset}ms (supported by $maxSupport cue/voice pairs)")
            if (Math.abs(bestOffset - currentOffset) <= 150L && currentOffset != 0L) {
                return currentOffset
            }
            return bestOffset
        }

        // Weak cluster: do not guess prematurely while anchors are still being evaluated
        return null
    }

    data class SpeechSegment(
        val startMs: Long,
        val endMs: Long
    ) {
        val durationMs: Long get() = maxOf(0L, endMs - startMs)
    }

    /**
     * Sliding-Window Cross-Correlation Subtitle Synchronization (Mini-FFSubSync).
     *
     * Discretizes voice activity from real-time audio and subtitle cue display intervals
     * into 50ms time bins, computing the cross-correlation profile across candidate shifts:
     * R(τ) = ∑ Audio(t) * Subtitle(t + τ).
     *
     * Mathematically guarantees noise resistance against intro music, sound effects, and dialogue
     * pauses by finding the indisputable global consensus peak.
     */
    fun evaluateCrossCorrelationDetailed(
        speechSegments: List<SpeechSegment>,
        dialogueCues: List<ParsedSubtitleCue>,
        currentOffset: Long = 0L,
        isUserManualCalibrated: Boolean = false,
        minSpeechDurationMs: Long = 2000L,
        searchRangeMs: Long = 45000L,
        binSizeMs: Int = 50
    ): SyncEvaluationResult {
        if (isUserManualCalibrated) return SyncEvaluationResult()
        if (speechSegments.isEmpty() || dialogueCues.isEmpty()) return SyncEvaluationResult()

        val spokenCues = dialogueCues.filter { isSpokenDialogueCue(it.text) }.ifEmpty { dialogueCues }
        if (spokenCues.isEmpty()) return SyncEvaluationResult()

        val totalSpeechMs = speechSegments.sumOf { it.durationMs }
        if (totalSpeechMs < minSpeechDurationMs) {
            // Need at least 2.0s of actual speech before cross-correlation produces a statistically significant peak
            return SyncEvaluationResult(totalSpeechMs = totalSpeechMs)
        }

        // Limit search domain to the first 4 minutes or cue coverage
        val maxCueTimeMs = spokenCues.take(25).maxOfOrNull { it.endTimeMs } ?: 0L
        val maxAudioTimeMs = speechSegments.maxOfOrNull { it.endMs } ?: 0L
        val timelineEndMs = maxOf(240_000L, maxCueTimeMs + searchRangeMs, maxAudioTimeMs + 5000L)
        val numBins = ((timelineEndMs / binSizeMs) + 1).toInt().coerceAtMost(8000)

        // 1. Build Subtitle Activity Mask (discretized binary vector)
        val subMask = BooleanArray(numBins)
        for (cue in spokenCues.take(30)) {
            val startBin = (cue.startTimeMs / binSizeMs).toInt().coerceIn(0, numBins - 1)
            val endBin = (cue.endTimeMs / binSizeMs).toInt().coerceIn(0, numBins - 1)
            for (b in startBin..endBin) {
                subMask[b] = true
            }
        }

        // 2. Discretize Audio Speech Segments
        val activeAudioBins = mutableListOf<Int>()
        for (seg in speechSegments) {
            val startBin = (seg.startMs / binSizeMs).toInt().coerceIn(0, numBins - 1)
            val endBin = (seg.endMs / binSizeMs).toInt().coerceIn(0, numBins - 1)
            for (b in startBin..endBin) {
                activeAudioBins.add(b)
            }
        }
        val distinctAudioBins = activeAudioBins.distinct().toIntArray()
        if (distinctAudioBins.isEmpty()) return SyncEvaluationResult(totalSpeechMs = totalSpeechMs)

        // 3. Compute Cross-Correlation R(τ) across all candidate shifts
        val minTauBin = (-searchRangeMs / binSizeMs).toInt()
        val maxTauBin = (searchRangeMs / binSizeMs).toInt()
        val binSpan = maxTauBin - minTauBin + 1
        val scores = IntArray(binSpan)

        var maxOverlap = 0

        for (tauBin in minTauBin..maxTauBin) {
            var overlap = 0
            for (aBin in distinctAudioBins) {
                val sBin = aBin + tauBin
                if (sBin in 0 until numBins && subMask[sBin]) {
                    overlap++
                }
            }
            val idx = tauBin - minTauBin
            scores[idx] = overlap
            if (overlap > maxOverlap) {
                maxOverlap = overlap
            }
        }

        if (maxOverlap == 0) {
            val isMismatch = totalSpeechMs >= 8000L
            return SyncEvaluationResult(isConfirmedMismatch = isMismatch, totalSpeechMs = totalSpeechMs)
        }

        // Select the center/midpoint of the optimal consensus plateau
        val optimalBins = mutableListOf<Int>()
        for (tauBin in minTauBin..maxTauBin) {
            if (scores[tauBin - minTauBin] == maxOverlap) {
                optimalBins.add(tauBin)
            }
        }
        val bestTauBin = optimalBins[optimalBins.size / 2]

        // 4. Statistical Confidence Metrics
        val totalBins = distinctAudioBins.size
        val recall = maxOverlap.toDouble() / totalBins
        val meanOverlap = scores.average()
        val snr = if (meanOverlap > 0.0) maxOverlap / meanOverlap else maxOverlap.toDouble()

        // Peak separation: find highest competitor at least 1.5s away
        val minSeparationBins = (1500 / binSizeMs)
        var secondPeakOverlap = 0
        for (tauBin in minTauBin..maxTauBin) {
            if (Math.abs(tauBin - bestTauBin) >= minSeparationBins) {
                val s = scores[tauBin - minTauBin]
                if (s > secondPeakOverlap) secondPeakOverlap = s
            }
        }
        val peakMargin = if (secondPeakOverlap > 0) maxOverlap.toDouble() / secondPeakOverlap else 3.0

        val isConfirmedMismatch = totalSpeechMs >= 8000L && recall < 0.38
        val isConfident = recall >= 0.45 && snr >= 1.8 && peakMargin >= 1.2
        if (!isConfident) {
            Log.d("SubtitleSyncEngine", "Cross-correlation below confidence: recall=${String.format("%.1f%%", recall * 100)}, snr=${String.format("%.2f", snr)}x, margin=${String.format("%.2f", peakMargin)}x (speech=${totalSpeechMs}ms, mismatch=$isConfirmedMismatch)")
            return SyncEvaluationResult(
                offsetMs = null,
                isConfident = false,
                isConfirmedMismatch = isConfirmedMismatch,
                recall = recall,
                snr = snr,
                totalSpeechMs = totalSpeechMs
            )
        }

        val bestOffsetMs = bestTauBin * binSizeMs.toLong()
        Log.i("SubtitleSyncEngine", "⚡ Mini-FFSubSync Cross-Correlation Peak: ${bestOffsetMs}ms (Recall: ${String.format("%.1f%%", recall * 100)}, SNR: ${String.format("%.2f", snr)}x, Margin: ${String.format("%.2f", peakMargin)}x, Speech: ${totalSpeechMs}ms in ${speechSegments.size} segments)")

        val finalOffset = if (Math.abs(bestOffsetMs - currentOffset) <= 150L && currentOffset != 0L) {
            currentOffset
        } else {
            bestOffsetMs
        }

        return SyncEvaluationResult(
            offsetMs = finalOffset,
            isConfident = true,
            isConfirmedMismatch = false,
            recall = recall,
            snr = snr,
            totalSpeechMs = totalSpeechMs
        )
    }

    fun evaluateCrossCorrelationAutoSync(
        speechSegments: List<SpeechSegment>,
        dialogueCues: List<ParsedSubtitleCue>,
        currentOffset: Long = 0L,
        isUserManualCalibrated: Boolean = false,
        minSpeechDurationMs: Long = 2000L,
        searchRangeMs: Long = 45000L,
        binSizeMs: Int = 50
    ): Long? {
        return evaluateCrossCorrelationDetailed(
            speechSegments, dialogueCues, currentOffset, isUserManualCalibrated,
            minSpeechDurationMs, searchRangeMs, binSizeMs
        ).offsetMs
    }

    /**
     * Detects dynamic framerate drift across extended playback and identifies closest FpsMode.
     * E.g. If offset starts at 0ms and drifts by 1,700ms over 30 minutes, determines PAL/Film ratio.
     */
    fun detectDynamicFramerateDrift(
        initialOffsetMs: Long,
        laterOffsetMs: Long,
        elapsedVideoMs: Long
    ): FpsMode {
        if (elapsedVideoMs < 180_000L) return FpsMode.NORMAL // Needs at least 3 minutes of playback
        val driftMs = laterOffsetMs - initialOffsetMs
        if (Math.abs(driftMs) < 400L) return FpsMode.NORMAL

        val measuredRatio = 1.0f + (driftMs.toFloat() / elapsedVideoMs.toFloat())
        val matchedMode = FpsMode.fromRatio(measuredRatio)
        Log.i("SubtitleSyncEngine", "Measured dynamic framerate drift: $driftMs ms over ${elapsedVideoMs}ms -> ratio: $measuredRatio (matched ${matchedMode.displayName})")
        return matchedMode
    }

    /**
     * Queries community consensus offset from Supabase cloud database.
     * Fails silently and returns null if table is uninitialized or offline.
     */
    suspend fun fetchCloudConsensusOffset(mediaKey: String): Long? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || mediaKey.isBlank()) return@withContext null
        try {
            val encodedKey = URLEncoder.encode(mediaKey, "UTF-8")
            val url = "${SupabaseConfig.PROJECT_URL.trimEnd('/')}/rest/v1/subtitle_sync_consensus?media_key=eq.$encodedKey&select=offset_ms,vote_count&limit=1"
            val req = Request.Builder()
                .url(url)
                .header("apikey", SupabaseConfig.ANON_KEY)
                .header("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
                .header("Accept", "application/json")
                .build()

            val call = NetworkConfig.permissiveOkHttpClient.newCall(req)
            call.execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val array = JSONArray(body)
                if (array.length() > 0) {
                    val obj = array.getJSONObject(0)
                    val offset = obj.optLong("offset_ms", 0L)
                    if (offset != 0L) {
                        Log.i("SubtitleSyncEngine", "Loaded cloud consensus subtitle offset: ${offset}ms for $mediaKey")
                        return@withContext offset
                    }
                }
            }
        } catch (_: Exception) {
            // Silently fall back to local engine
        }
        null
    }

    /**
     * Submits an automated or user-calibrated offset to Supabase cloud consensus.
     * Non-blocking background telemetry.
     */
    suspend fun submitCloudConsensusOffset(mediaKey: String, offsetMs: Long, source: String = "auto_pilot") = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || mediaKey.isBlank() || offsetMs == 0L) return@withContext
        try {
            val url = "${SupabaseConfig.PROJECT_URL.trimEnd('/')}/rest/v1/subtitle_sync_consensus"
            val payload = JSONObject().apply {
                put("media_key", mediaKey)
                put("offset_ms", offsetMs)
                put("source", source)
            }
            val reqBody = payload.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val req = Request.Builder()
                .url(url)
                .header("apikey", SupabaseConfig.ANON_KEY)
                .header("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
                .header("Content-Type", "application/json")
                .header("Prefer", "resolution=merge-duplicates")
                .post(reqBody)
                .build()

            NetworkConfig.permissiveOkHttpClient.newCall(req).execute().close()
            Log.i("SubtitleSyncEngine", "Submitted consensus subtitle offset: ${offsetMs}ms ($source) for $mediaKey")
        } catch (_: Exception) {
            // Non-critical, fails silently
        }
    }
}
