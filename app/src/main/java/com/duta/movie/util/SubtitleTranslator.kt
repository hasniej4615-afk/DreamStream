package com.duta.movie.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.net.URLEncoder

/**
 * Intelligent Subtitle Translation Service.
 * Provides real-time translation of foreign subtitles (e.g. English -> Indonesian/Malay)
 * with multi-line batching, newline preservation, and persistent local VTT caching.
 */
object SubtitleTranslator {
    private const val TAG = "SubtitleTranslator"

    fun getLanguageCode(lang: String): String {
        val low = lang.lowercase().trim()
        return when {
            low.contains("indonesi") || low == "id" || low == "ind" -> "id"
            low.contains("malay") || low.contains("melayu") || low == "ms" || low == "msa" -> "ms"
            low.contains("english") || low == "en" || low == "eng" -> "en"
            low.contains("japan") || low == "ja" || low == "jpn" -> "ja"
            low.contains("korean") || low == "ko" || low == "kor" -> "ko"
            low.contains("chinese") || low == "zh" || low == "chi" -> "zh-CN"
            low.contains("thai") || low == "th" || low == "tha" -> "th"
            low.contains("arabic") || low == "ar" || low == "ara" -> "ar"
            low.contains("spanish") || low == "es" || low == "spa" -> "es"
            low.contains("french") || low == "fr" || low == "fre" -> "fr"
            low.contains("german") || low == "de" || low == "ger" -> "de"
            else -> "id"
        }
    }

    fun getLanguageDisplayName(codeOrName: String): String {
        val code = getLanguageCode(codeOrName)
        return when (code) {
            "id" -> "Indonesian"
            "ms" -> "Malay"
            "en" -> "English"
            "ja" -> "Japanese"
            "ko" -> "Korean"
            "zh-CN" -> "Chinese"
            "th" -> "Thai"
            "ar" -> "Arabic"
            "es" -> "Spanish"
            "fr" -> "French"
            "de" -> "German"
            else -> code.uppercase()
        }
    }

    suspend fun translateCues(
        context: Context,
        cues: List<ParsedSubtitleCue>,
        title: String,
        targetLang: String = "id",
        onProgress: ((Int, Int) -> Unit)? = null
    ): Pair<File, List<ParsedSubtitleCue>>? = withContext(Dispatchers.IO) {
        if (cues.isEmpty()) return@withContext null
        val targetCode = getLanguageCode(targetLang)
        val cleanTitle = title.replace(Regex("""[^a-zA-Z0-9]"""), "_").ifBlank { "video" }.take(30)
        val cueHash = (cues.size.toString() + "_" + cues.take(5).joinToString("") { it.text } + "_" + cues.takeLast(5).joinToString("") { it.text }).hashCode().let { Integer.toHexString(it) }
        val cacheDir = File(context.cacheDir, "subtitles")
        cacheDir.mkdirs()
        val cachedFile = File(cacheDir, "translated_${cleanTitle}_${cueHash}_${targetCode}.vtt")

        if (cachedFile.exists() && cachedFile.length() > 0L) {
            val parsed = SubtitleParser.parse(cachedFile)
            if (parsed.isNotEmpty()) {
                Log.i(TAG, "Loaded cached translation: ${cachedFile.absolutePath} (${parsed.size} cues)")
                return@withContext Pair(cachedFile, parsed)
            }
        }

        val translatedCues = mutableListOf<ParsedSubtitleCue>()
        val batchSize = 25
        val chunks = cues.chunked(batchSize)
        val total = chunks.size

        for ((index, chunk) in chunks.withIndex()) {
            val translatedBatch = translateBatch(chunk, targetCode)
            translatedCues.addAll(translatedBatch)
            onProgress?.invoke(index + 1, total)
        }

        if (translatedCues.isEmpty()) return@withContext null

        try {
            val vttContent = SubtitleParser.toWebVtt(translatedCues)
            cachedFile.writeText(vttContent, Charsets.UTF_8)
            Log.i(TAG, "Translation completed and cached: ${cachedFile.absolutePath} (${translatedCues.size} cues)")
            Pair(cachedFile, translatedCues)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache translated subtitle", e)
            Pair(cachedFile, translatedCues)
        }
    }

    private fun translateBatch(
        chunk: List<ParsedSubtitleCue>,
        targetCode: String
    ): List<ParsedSubtitleCue> {
        val delimiter = " [|||] "
        val joinedText = chunk.joinToString(delimiter) { it.text.replace("\n", " <br> ") }
        
        try {
            val translatedRaw = translateText(joinedText, targetCode)
            if (translatedRaw != null) {
                // Regex matches standard [|||], full-width 【|||】 or ［|||］, RTL ]|||[, and fullwidth pipes ｜
                val splitRegex = Regex("""\s*(?:\[\s*[|｜]{1,3}\s*\]|【\s*[|｜]{1,3}\s*】|［\s*[|｜]{1,3}\s*］|\]\s*[|｜]{1,3}\s*\[)\s*""")
                val parts = translatedRaw.split(splitRegex)
                if (parts.size == chunk.size) {
                    return chunk.mapIndexed { idx, cue ->
                        val restoredText = restoreFormatting(parts[idx])
                        cue.copy(text = restoredText)
                    }
                } else if (parts.size > 1) {
                    Log.w(TAG, "Batch split count mismatch (got ${parts.size}, expected ${chunk.size}), aligning parts")
                    return chunk.mapIndexed { idx, cue ->
                        val text = if (idx < parts.size) restoreFormatting(parts[idx]) else (translateText(cue.text, targetCode) ?: cue.text)
                        cue.copy(text = text)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Batch translation failed, falling back to individual cue translation", e)
        }

        // Fallback: translate line by line with slight throttle to avoid 429
        return chunk.map { cue ->
            try { Thread.sleep(25) } catch (_: Exception) {}
            val singleTrans = translateText(cue.text, targetCode) ?: cue.text
            cue.copy(text = restoreFormatting(singleTrans))
        }
    }

    private fun restoreFormatting(raw: String): String {
        return raw.replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(" --NL-- ", "\n")
            .replace("--NL--", "\n")
            .trim()
    }

    fun translateText(text: String, targetCode: String): String? {
        if (text.isBlank()) return text
        try {
            val encodedQ = URLEncoder.encode(text, "UTF-8")
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$targetCode&dt=t&q=$encodedQ"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", NetworkConfig.SHARED_USER_AGENT)
                .get()
                .build()

            NetworkConfig.permissiveOkHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyStr = response.body?.string() ?: return null
                val jsonArray = JSONArray(bodyStr)
                val sentences = jsonArray.optJSONArray(0) ?: return null
                val sb = StringBuilder()
                for (i in 0 until sentences.length()) {
                    val item = sentences.optJSONArray(i)
                    if (item != null) {
                        sb.append(item.optString(0))
                    }
                }
                return sb.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error translating text via Google Translate", e)
            return null
        }
    }
}
