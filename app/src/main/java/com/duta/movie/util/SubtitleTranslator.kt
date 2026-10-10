package com.duta.movie.util

import android.content.Context
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Intelligent Subtitle Translation Service.
 * Provides high-speed, parallel translation of foreign subtitles (e.g. English, Chinese, Korean, Japanese -> Indonesian/Malay/English)
 * with multi-line batching (60 cues/batch), official mobile client routing (client=at/it to prevent 429 rate limits),
 * binary split recovery, persistent local VTT caching with corruption detection, and in-memory LRU caching.
 */
object SubtitleTranslator {
    private const val TAG = "SubtitleTranslator"

    private const val BATCH_SIZE = 60
    private const val CONCURRENT_WORKERS = 6
    private const val MOBILE_TRANSLATE_UA = "GoogleTranslate/6.28.0.05.421483610 (Linux; U; Android 13; Pixel 7)"

    // In-memory cache for ultra-fast (0ms) re-selection of translated subtitles
    private val memoryCache = LruCache<String, Pair<File, List<ParsedSubtitleCue>>>(10)

    // Dedicated high-speed OkHttpClient with HTTP/2 connection multiplexing and keep-alive
    private val translationHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dispatcher(okhttp3.Dispatcher().apply {
                maxRequests = 64
                maxRequestsPerHost = 16
            })
            .connectionPool(okhttp3.ConnectionPool(16, 5, TimeUnit.MINUTES))
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .protocols(listOf(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1))
            .build()
    }

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
        val cacheKey = "${cleanTitle}_${cueHash}_${targetCode}"

        // 1. Check in-memory LRU cache (0ms instant return)
        synchronized(memoryCache) {
            val memCached = memoryCache.get(cacheKey)
            if (memCached != null && memCached.first.exists() && memCached.first.length() > 0L) {
                Log.i(TAG, "Loaded in-memory cached translation: $cacheKey (${memCached.second.size} cues)")
                return@withContext memCached
            }
        }

        // 2. Check persistent disk cache with corruption validation
        val cacheDir = File(context.cacheDir, "subtitles")
        cacheDir.mkdirs()
        val cachedFile = File(cacheDir, "translated_${cleanTitle}_${cueHash}_${targetCode}.vtt")

        if (cachedFile.exists() && cachedFile.length() > 0L) {
            val parsed = SubtitleParser.parse(cachedFile)
            // Integrity check: If target is not english, make sure cached file wasn't a corrupt untranslated duplicate!
            val isCorruptedCache = if (targetCode != "en" && cues.isNotEmpty() && parsed.isNotEmpty()) {
                val sampleOriginal = cues.filter { it.text.isNotBlank() }.take(5).map { it.text.trim() }
                val sampleParsed = parsed.filter { it.text.isNotBlank() }.take(5).map { it.text.trim() }
                sampleOriginal == sampleParsed
            } else false

            if (isCorruptedCache) {
                Log.w(TAG, "Detected corrupted/untranslated cached subtitle (${cachedFile.name}), deleting and re-translating")
                cachedFile.delete()
            } else if (parsed.isNotEmpty()) {
                Log.i(TAG, "Loaded cached translation from disk: ${cachedFile.absolutePath} (${parsed.size} cues)")
                val pair = Pair(cachedFile, parsed)
                synchronized(memoryCache) {
                    memoryCache.put(cacheKey, pair)
                }
                return@withContext pair
            }
        }

        // 3. High-speed parallel translation
        val chunks = cues.chunked(BATCH_SIZE)
        val total = chunks.size
        val semaphore = Semaphore(CONCURRENT_WORKERS)
        val completedCount = AtomicInteger(0)
        onProgress?.invoke(0, total)

        val orderedResults = coroutineScope {
            val deferreds = chunks.mapIndexed { index, chunk ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val translatedBatch = translateBatch(chunk, targetCode)
                        val cur = completedCount.incrementAndGet()
                        onProgress?.invoke(cur, total)
                        Pair(index, translatedBatch)
                    }
                }
            }
            deferreds.awaitAll()
        }

        val translatedCues = ArrayList<ParsedSubtitleCue>(cues.size)
        for (pair in orderedResults.sortedBy { it.first }) {
            translatedCues.addAll(pair.second)
        }

        if (translatedCues.isEmpty()) return@withContext null

        // Integrity check: Verify result is not untouched original text
        if (targetCode != "en") {
            val sampleOrig = cues.filter { it.text.isNotBlank() }.take(5).map { it.text.trim() }
            val sampleTrans = translatedCues.filter { it.text.isNotBlank() }.take(5).map { it.text.trim() }
            if (sampleOrig.isNotEmpty() && sampleOrig == sampleTrans) {
                Log.e(TAG, "Translation failed: results are identical to original text. Discarding bad result.")
                return@withContext null
            }
        }

        val resultPair = Pair(cachedFile, translatedCues)
        try {
            val vttContent = SubtitleParser.toWebVtt(translatedCues)
            cachedFile.writeText(vttContent, Charsets.UTF_8)
            Log.i(TAG, "Translation completed and cached: ${cachedFile.absolutePath} (${translatedCues.size} cues)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache translated subtitle to disk", e)
        }

        synchronized(memoryCache) {
            memoryCache.put(cacheKey, resultPair)
        }

        resultPair
    }

    private fun translateBatch(
        chunk: List<ParsedSubtitleCue>,
        targetCode: String
    ): List<ParsedSubtitleCue> {
        if (chunk.isEmpty()) return emptyList()
        if (chunk.all { it.text.isBlank() }) return chunk

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
                    Log.w(TAG, "Batch split count mismatch (got ${parts.size}, expected ${chunk.size})")
                    if (chunk.size > 4) {
                        val mid = chunk.size / 2
                        val left = translateBatch(chunk.subList(0, mid), targetCode)
                        val right = translateBatch(chunk.subList(mid, chunk.size), targetCode)
                        return left + right
                    } else {
                        return chunk.mapIndexed { idx, cue ->
                            val text = if (idx < parts.size) restoreFormatting(parts[idx]) else (translateText(cue.text, targetCode) ?: cue.text)
                            cue.copy(text = text)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Batch translation failed, attempting binary split", e)
        }

        // Binary split fallback for fast recovery without individual sleep stalls
        if (chunk.size > 4) {
            val mid = chunk.size / 2
            val left = translateBatch(chunk.subList(0, mid), targetCode)
            val right = translateBatch(chunk.subList(mid, chunk.size), targetCode)
            return left + right
        }

        // Terminal fallback for tiny chunks (<= 4 cues)
        return chunk.map { cue ->
            if (cue.text.isBlank()) cue
            else {
                val singleTrans = translateText(cue.text, targetCode)
                if (singleTrans != null && singleTrans.isNotBlank()) {
                    cue.copy(text = restoreFormatting(singleTrans))
                } else {
                    cue
                }
            }
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

        val endpoints = listOf(
            "https://translate.googleapis.com/translate_a/single?client=at&sl=auto&tl=$targetCode&dt=t",
            "https://translate.googleapis.com/translate_a/single?client=it&sl=auto&tl=$targetCode&dt=t",
            "https://translate.googleapis.com/translate_a/t?client=at&sl=auto&tl=$targetCode",
            "https://translate.google.com/translate_a/single?client=at&sl=auto&tl=$targetCode&dt=t"
        )

        for (endpoint in endpoints) {
            try {
                val formBody = FormBody.Builder()
                    .add("q", text)
                    .build()

                val request = Request.Builder()
                    .url(endpoint)
                    .header("User-Agent", MOBILE_TRANSLATE_UA)
                    .post(formBody)
                    .build()

                val result = translationHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Endpoint failed ($endpoint) with HTTP ${response.code}")
                        return@use null
                    }
                    val bodyStr = response.body?.string() ?: return@use null
                    if (bodyStr.isBlank()) return@use null

                    val jsonArray = JSONArray(bodyStr)
                    val sb = StringBuilder()
                    if (endpoint.contains("/translate_a/t?")) {
                        // Format: [["translated text", "detected_lang"]]
                        val item = jsonArray.optJSONArray(0)
                        if (item != null) {
                            sb.append(item.optString(0))
                        }
                    } else {
                        // Format: [[["translated sentence 1", "orig 1", ...], ["translated sentence 2", "orig 2", ...]]]
                        val sentences = jsonArray.optJSONArray(0)
                        if (sentences != null) {
                            for (i in 0 until sentences.length()) {
                                val item = sentences.optJSONArray(i)
                                if (item != null) {
                                    sb.append(item.optString(0))
                                }
                            }
                        }
                    }
                    sb.toString()
                }

                if (!result.isNullOrBlank()) {
                    return result
                }
            } catch (e: Exception) {
                Log.w(TAG, "Translation error on $endpoint: ${e.message}")
            }
        }
        return null
    }
}
