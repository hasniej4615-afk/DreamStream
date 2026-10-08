package com.duta.movie.provider.engine

import android.net.Uri
import android.util.Log
import com.duta.movie.data.local.InstalledProviderEntity
import com.duta.movie.model.Episode
import com.duta.movie.model.Video
import com.duta.movie.model.VideoServer
import com.duta.movie.provider.core.MediaProvider
import com.duta.movie.provider.model.ProviderMediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Native MovieBox MediaProvider implementation.
 * Connects directly to the MovieBox / Aoneroom streaming backend with automatic JWT token minting.
 * Resolves direct high-speed MP4 streams (360P, 480P, 720P, 1080P).
 */
class MovieboxProvider(
    private val entity: InstalledProviderEntity,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) : MediaProvider {

    companion object {
        private const val TAG = "MovieboxProvider"
        private const val API_BASE = "https://h5-api.aoneroom.com"
        private const val SITE_BASE = "https://movieboxonline.net"
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    override val id: String = entity.id
    override val name: String = entity.name
    override val displayName: String = entity.displayName
    override val version: Int = entity.version
    override val versionName: String = entity.versionName
    override val iconUrl: String = entity.iconUrl.ifBlank { "https://i.ibb.co/q3YkdNRv/asm0d3usx.png" }
    override val mediaType: ProviderMediaType = ProviderMediaType.MULTI
    override val isEnabled: Boolean = entity.isEnabled

    private var cachedJwt: String? = null
    private var jwtExpiresAt: Long = 0L

    // In-memory cache for Short TV micro-drama catalog
    private val shortTvCache = mutableListOf<Video>()
    private val shortTvSeenIds = mutableSetOf<String>()
    private var shortTvInitialized = false
    private val shortTvLock = Any()
    @Volatile private var nextVskitCatalogPage: Int = 4

    private fun getClientToken(): String {
        val e = System.currentTimeMillis() / 1000
        val rev = e.toString().reversed()
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(rev.toByteArray())
        val hash = digest.joinToString("") { "%02x".format(it) }
        return "$e,$hash"
    }

    internal fun ensureJwtToken(): String? {
        if (!cachedJwt.isNullOrBlank() && System.currentTimeMillis() < jwtExpiresAt) {
            return cachedJwt
        }
        try {
            val url = "$API_BASE/wefeed-h5api-bff/subject/search-suggest"
            val body = JSONObject().apply {
                put("keyword", "a")
                put("perPage", 10)
            }.toString()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("X-Client-Info", "{\"timezone\":\"Africa/Lagos\"}")
                .header("X-Request-Lang", "en")
                .header("X-Client-Token", getClientToken())
                .header("Origin", SITE_BASE)
                .header("Referer", "$SITE_BASE/")
                .header("Authorization", "")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            okHttpClient.newCall(request).execute().use { resp ->
                val xUser = resp.header("x-user")
                if (!xUser.isNullOrBlank()) {
                    val userObj = JSONObject(xUser)
                    val token = userObj.optString("token")
                    if (token.isNotBlank()) {
                        cachedJwt = token
                        // 30 days safe cache
                        jwtExpiresAt = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
                        Log.i(TAG, "Successfully minted anonymous MovieBox JWT token")
                        return token
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to mint Moviebox token: ${e.message}")
        }
        return null
    }

    override suspend fun search(query: String, page: Int): List<Video> = withContext(Dispatchers.IO) {
        if (!isEnabled || query.isBlank()) return@withContext emptyList()
        val token = ensureJwtToken() ?: run {
            Log.w(TAG, "Search aborted: could not obtain JWT token")
            return@withContext emptyList()
        }
        Log.i(TAG, "Executing MovieBox search: query='$query', page=$page")
        try {
            val url = "$API_BASE/wefeed-h5api-bff/subject/search"
            val payload = JSONObject().apply {
                put("keyword", query)
                put("page", page)
                put("perPage", 0)
                put("subjectType", 0)
            }.toString()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("X-Client-Info", "{\"timezone\":\"Africa/Lagos\"}")
                .header("X-Request-Lang", "en")
                .header("Origin", SITE_BASE)
                .header("Referer", "$SITE_BASE/")
                .header("Authorization", "Bearer $token")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            okHttpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val jsonStr = resp.body?.string() ?: return@withContext emptyList()
                val root = JSONObject(jsonStr)
                val items = root.optJSONObject("data")?.optJSONArray("items") ?: JSONArray()
                val list = mutableListOf<Video>()
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val sid = item.optString("subjectId")
                    val dp = item.optString("detailPath")
                    val title = item.optString("title")
                    if (sid.isBlank() || title.isBlank()) continue

                    val cover = item.optJSONObject("cover")?.optString("url") ?: ""
                    val stype = item.optInt("subjectType", 1) // 1=movie, 2=tv
                    val rating = item.optString("imdbRatingValue", "HD")
                    val releaseDate = item.optString("releaseDate", "")
                    val durationSec = item.optInt("duration", 0)
                    val durStr = if (durationSec > 0) "${durationSec / 60}m" else ""

                    list.add(
                        Video(
                            id = "mb_$sid",
                            title = title,
                            thumbnailUrl = cover,
                            videoUrl = "moviebox://$dp?id=$sid&type=$stype",
                            duration = durStr,
                            date = releaseDate,
                            quality = if (rating.isNotBlank()) "★ $rating" else "HD",
                            isSeries = (stype == 2 || stype == 5 || stype == 7),
                            isShortTv = (stype == 5 || stype == 7)
                        )
                    )
                }
                Log.i(TAG, "MovieBox search query='$query' returned ${list.size} results")
                list
            }
        } catch (e: Exception) {
            Log.w(TAG, "Search error on Moviebox: ${e.message}")
            emptyList()
        }
    }

    override suspend fun fetchSection(path: String, page: Int, count: Int): List<Video> = withContext(Dispatchers.IO) {
        if (!isEnabled) return@withContext emptyList()
        val norm = path.trim('/')
        if (norm.startsWith("source/")) return@withContext emptyList()

        if (norm == "short-tv" || norm.contains("short")) {
            return@withContext fetchShortTvSection(page, count)
        }

        try {
            val url = "$API_BASE/wefeed-h5api-bff/subject/trending?page=$page&perPage=$count"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "application/json")
                .header("X-Client-Info", "{\"timezone\":\"Africa/Lagos\"}")
                .header("Origin", SITE_BASE)
                .header("Referer", "$SITE_BASE/")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val jsonStr = resp.body?.string() ?: return@withContext emptyList()
                val root = JSONObject(jsonStr)
                val items = root.optJSONObject("data")?.optJSONArray("subjectList")
                    ?: root.optJSONArray("data")
                    ?: JSONArray()

                val list = mutableListOf<Video>()
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val sid = item.optString("subjectId")
                    val dp = item.optString("detailPath")
                    val title = item.optString("title")
                    if (sid.isBlank() || title.isBlank()) continue

                    val cover = item.optJSONObject("cover")?.optString("url") ?: ""
                    val stype = item.optInt("subjectType", 1)
                    val rating = item.optString("imdbRatingValue", "HD")

                    val isTvCategory = norm.contains("series") || norm.contains("tv")
                    val isMovieCategory = norm.contains("movie") || norm.contains("film")

                    // Strict category isolation: Never mix TV series, Movies, or Short Dramas
                    if (isTvCategory && stype != 2) continue
                    if (isMovieCategory && stype != 1) continue
                    // Never include short micro-dramas in normal categories
                    if (stype == 5 || stype == 7) continue

                    list.add(
                        Video(
                            id = "mb_$sid",
                            title = title,
                            thumbnailUrl = cover,
                            videoUrl = "moviebox://$dp?id=$sid&type=$stype",
                            duration = "",
                            quality = if (rating.isNotBlank()) "★ $rating" else "HD",
                            isSeries = (stype == 2),
                            isShortTv = false
                        )
                    )
                }
                list
            }
        } catch (e: Exception) {
            Log.w(TAG, "Section fetch error: ${e.message}")
            emptyList()
        }
    }

    private suspend fun fetchShortTvSection(page: Int, count: Int): List<Video> {
        val token = ensureJwtToken()
        val startIndex = (page - 1) * count
        val targetEndIndex = startIndex + count

        val needsInit = synchronized(shortTvLock) { !shortTvInitialized || shortTvCache.isEmpty() }
        if (needsInit) {
            populateShortTvCache(token)
        }

        // On-demand pagination: while we don't have enough items to fulfill targetEndIndex,
        // dynamically fetch subsequent pages from the MovieBox Vskit micro-drama catalog.
        var consecutiveEmptyFetches = 0
        while (synchronized(shortTvLock) { shortTvCache.size } < targetEndIndex && nextVskitCatalogPage <= 150 && consecutiveEmptyFetches < 2) {
            val pageToFetch = synchronized(shortTvLock) { nextVskitCatalogPage++ }
            val moreItems = fetchVskitCatalog(pageToFetch, 50)
            if (moreItems.isEmpty()) {
                consecutiveEmptyFetches++
            } else {
                consecutiveEmptyFetches = 0
                synchronized(shortTvLock) {
                    for (v in moreItems) {
                        val sid = v.id.removePrefix("mb_")
                        if (shortTvSeenIds.add(sid)) {
                            shortTvCache.add(v)
                        }
                    }
                }
            }
        }

        return synchronized(shortTvLock) {
            if (startIndex >= shortTvCache.size) {
                emptyList()
            } else {
                val endIndex = minOf(targetEndIndex, shortTvCache.size)
                shortTvCache.subList(startIndex, endIndex).toList()
            }
        }
    }

    private suspend fun populateShortTvCache(token: String?) = coroutineScope {
        val rankingDeferred = async(Dispatchers.IO) { fetchShortTvRanking(1, 50, token) }
        val tabsDeferred = async(Dispatchers.IO) { fetchVskitTabs() }
        val rec1Deferred = async(Dispatchers.IO) { fetchVskitRecommend(1) }
        val rec2Deferred = async(Dispatchers.IO) { fetchVskitRecommend(2) }
        val searchDeferred = async(Dispatchers.IO) { fetchVskitEveryoneSearch() }
        val cat1Deferred = async(Dispatchers.IO) { fetchVskitCatalog(1, 50) }
        val cat2Deferred = async(Dispatchers.IO) { fetchVskitCatalog(2, 50) }
        val cat3Deferred = async(Dispatchers.IO) { fetchVskitCatalog(3, 50) }

        val allResults = listOf(
            rankingDeferred.await(),
            tabsDeferred.await(),
            rec1Deferred.await(),
            rec2Deferred.await(),
            searchDeferred.await(),
            cat1Deferred.await(),
            cat2Deferred.await(),
            cat3Deferred.await()
        )

        synchronized(shortTvLock) {
            shortTvCache.clear()
            shortTvSeenIds.clear()
            for (subList in allResults) {
                for (v in subList) {
                    val sid = v.id.removePrefix("mb_")
                    if (shortTvSeenIds.add(sid)) {
                        shortTvCache.add(v)
                    }
                }
            }
            nextVskitCatalogPage = 4
            shortTvInitialized = true
        }
        Log.i(TAG, "MovieBox Short TV catalog loaded: ${shortTvCache.size} pure micro-drama titles in cache")
    }

    private fun fetchShortTvRanking(page: Int, count: Int, token: String?): List<Video> {
        val list = mutableListOf<Video>()
        try {
            val url = "$API_BASE/wefeed-h5api-bff/ranking-list/content?id=7844144696607102784&page=$page&perPage=$count"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "application/json")
                .header("X-Client-Info", "{\"timezone\":\"Africa/Lagos\"}")
                .header("Origin", SITE_BASE)
                .header("Referer", "$SITE_BASE/")
                .apply { if (token != null) header("Authorization", "Bearer $token") }
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    val jsonStr = resp.body?.string() ?: ""
                    val root = JSONObject(jsonStr)
                    val items = root.optJSONObject("data")?.optJSONArray("subjectList") ?: JSONArray()
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        val sid = item.optString("subjectId")
                        val dp = item.optString("detailPath")
                        val title = item.optString("title")
                        val stype = item.optInt("subjectType", 7)
                        if (sid.isBlank() || title.isBlank()) continue

                        // Strict validation: Only genuine Short TV / DramaBox items (subjectType 7 or 5)
                        // Reject normal movies (1) or regular TV series (2)
                        if (stype != 7 && stype != 5) continue
                        if (dp.contains("/movie/") || dp.contains("/tv/") || dp.contains("/series/")) continue

                        val cover = item.optJSONObject("cover")?.optString("url") ?: ""
                        val rating = item.optString("imdbRatingValue", "ShortTV")
                        val releaseDate = item.optString("releaseDate", "")

                        list.add(
                            Video(
                                id = "mb_$sid",
                                title = title,
                                thumbnailUrl = cover,
                                videoUrl = "moviebox://$dp?id=$sid&type=7",
                                duration = "",
                                date = releaseDate,
                                quality = if (rating.isNotBlank() && rating != "0" && rating != "HD") "★ $rating" else "ShortTV",
                                isSeries = true,
                                isShortTv = true
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed fetching Short TV ranking: ${e.message}")
        }
        return list
    }

    private fun fetchVskitCatalog(page: Int, count: Int): List<Video> {
        val list = mutableListOf<Video>()
        try {
            val url = "$API_BASE/wefeed-h5api-bff/vskit/subject/list"
            val body = JSONObject().apply {
                put("page", page)
                put("perPage", count)
            }.toString()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Content-Type", "application/json")
                .header("X-Client-Info", "{\"timezone\":\"Africa/Lagos\"}")
                .header("Origin", "https://vskit.online")
                .header("Referer", "https://vskit.online/")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            okHttpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    val root = JSONObject(resp.body?.string() ?: "")
                    val items = root.optJSONObject("data")?.optJSONArray("items") ?: JSONArray()
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        val sid = item.optString("subjectId")
                        val title = item.optString("title")
                        val seoKey = item.optString("subjectSeoKey")
                        val epCount = item.optInt("totalEpisode", 0)
                        if (sid.isBlank() || title.isBlank()) continue
                        if (seoKey.contains("/movie/") || seoKey.contains("/tv/") || seoKey.contains("/series/")) continue

                        val cover = item.optJSONObject("cover")?.optString("url") ?: ""
                        list.add(
                            Video(
                                id = "mb_$sid",
                                title = title,
                                thumbnailUrl = cover,
                                videoUrl = "moviebox://$seoKey?id=$sid&type=7",
                                duration = if (epCount > 0) "$epCount Eps" else "",
                                date = "",
                                quality = if (epCount > 0) "$epCount Eps" else "ShortTV",
                                isSeries = true,
                                isShortTv = true
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed fetching Vskit catalog page $page: ${e.message}")
        }
        return list
    }

    private fun fetchVskitTabs(): List<Video> {
        val list = mutableListOf<Video>()
        try {
            val url = "$API_BASE/wefeed-h5api-bff/vskit/tab-operation-list"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("X-Client-Info", "{\"timezone\":\"Africa/Lagos\"}")
                .header("Origin", "https://vskit.online")
                .header("Referer", "https://vskit.online/")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    val root = JSONObject(resp.body?.string() ?: "")
                    val tabs = root.optJSONObject("data")?.optJSONArray("list") ?: JSONArray()
                    for (t in 0 until tabs.length()) {
                        val tab = tabs.optJSONObject(t) ?: continue
                        val items = tab.optJSONArray("novelItems") ?: JSONArray()
                        for (i in 0 until items.length()) {
                            val item = items.optJSONObject(i) ?: continue
                            val sid = item.optString("subjectId")
                            val title = item.optString("title")
                            val seoKey = item.optString("subjectSeoKey")
                            val epCount = item.optInt("totalEpisode", 0)
                            if (sid.isBlank() || title.isBlank()) continue
                            if (seoKey.contains("/movie/") || seoKey.contains("/tv/") || seoKey.contains("/series/")) continue

                            val cover = item.optJSONObject("cover")?.optString("url") ?: ""
                            list.add(
                                Video(
                                    id = "mb_$sid",
                                    title = title,
                                    thumbnailUrl = cover,
                                    videoUrl = "moviebox://$seoKey?id=$sid&type=7",
                                    duration = if (epCount > 0) "$epCount Eps" else "",
                                    date = "",
                                    quality = if (epCount > 0) "$epCount Eps" else "ShortTV",
                                    isSeries = true,
                                    isShortTv = true
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed fetching Vskit tabs: ${e.message}")
        }
        return list
    }

    private fun fetchVskitRecommend(page: Int): List<Video> {
        val list = mutableListOf<Video>()
        try {
            val url = "$API_BASE/wefeed-h5api-bff/vskit/recommend-list?page=$page&perPage=50"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("X-Client-Info", "{\"timezone\":\"Africa/Lagos\"}")
                .header("Origin", "https://vskit.online")
                .header("Referer", "https://vskit.online/")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    val root = JSONObject(resp.body?.string() ?: "")
                    val items = root.optJSONObject("data")?.optJSONArray("list") ?: JSONArray()
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        val sid = item.optString("subjectId")
                        val title = item.optString("title")
                        val seoKey = item.optString("subjectSeoKey")
                        val epCount = item.optInt("totalEpisode", 0)
                        if (sid.isBlank() || title.isBlank()) continue
                        if (seoKey.contains("/movie/") || seoKey.contains("/tv/") || seoKey.contains("/series/")) continue

                        val cover = item.optJSONObject("cover")?.optString("url") ?: ""
                        list.add(
                            Video(
                                id = "mb_$sid",
                                title = title,
                                thumbnailUrl = cover,
                                videoUrl = "moviebox://$seoKey?id=$sid&type=7",
                                duration = if (epCount > 0) "$epCount Eps" else "",
                                date = "",
                                quality = if (epCount > 0) "$epCount Eps" else "ShortTV",
                                isSeries = true,
                                isShortTv = true
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed fetching Vskit recommend page $page: ${e.message}")
        }
        return list
    }

    private fun fetchVskitEveryoneSearch(): List<Video> {
        val list = mutableListOf<Video>()
        try {
            val url = "$API_BASE/wefeed-h5api-bff/vskit/everyonesearch"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("X-Client-Info", "{\"timezone\":\"Africa/Lagos\"}")
                .header("Origin", "https://vskit.online")
                .header("Referer", "https://vskit.online/")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    val root = JSONObject(resp.body?.string() ?: "")
                    val items = root.optJSONObject("data")?.optJSONArray("recommendList") ?: JSONArray()
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        val sid = item.optString("subjectId")
                        val title = item.optString("title")
                        val seoKey = item.optString("subjectSeoKey")
                        val epCount = item.optInt("totalEpisode", 0)
                        if (sid.isBlank() || title.isBlank()) continue
                        if (seoKey.contains("/movie/") || seoKey.contains("/tv/") || seoKey.contains("/series/")) continue

                        val cover = item.optJSONObject("cover")?.optString("url") ?: ""
                        list.add(
                            Video(
                                id = "mb_$sid",
                                title = title,
                                thumbnailUrl = cover,
                                videoUrl = "moviebox://$seoKey?id=$sid&type=7",
                                duration = if (epCount > 0) "$epCount Eps" else "",
                                date = "",
                                quality = if (epCount > 0) "$epCount Eps" else "ShortTV",
                                isSeries = true,
                                isShortTv = true
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed fetching Vskit everyone search: ${e.message}")
        }
        return list
    }

    override suspend fun fetchVideoDetail(video: Video): Video? = withContext(Dispatchers.IO) {
        val dp = extractDetailPath(video.videoUrl)
        val sidParam = extractParam(video.videoUrl, "id").ifBlank { video.id.removePrefix("mb_") }
        if (dp.isBlank() && sidParam.isBlank()) return@withContext video
        try {
            val encodedDp = if (dp.isNotBlank()) URLEncoder.encode(dp, "UTF-8") else ""
            var subject: JSONObject? = null
            var resource: JSONObject? = null

            // 1. Try detailPath lookup first if available
            if (encodedDp.isNotBlank()) {
                try {
                    val url = "$API_BASE/wefeed-h5api-bff/detail?detailPath=$encodedDp"
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", UA)
                        .header("Accept", "application/json")
                        .header("X-Client-Info", "{\"timezone\":\"Africa/Lagos\"}")
                        .header("Origin", SITE_BASE)
                        .header("Referer", "$SITE_BASE/")
                        .get()
                        .build()

                    okHttpClient.newCall(request).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val jsonStr = resp.body?.string() ?: ""
                            val root = JSONObject(jsonStr)
                            subject = root.optJSONObject("data")?.optJSONObject("subject")
                            resource = root.optJSONObject("data")?.optJSONObject("resource")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "detailPath lookup failed for $dp: ${e.message}")
                }
            }

            // 2. Fallback to subjectId lookup if detailPath was absent or returned no subject
            if (subject == null && sidParam.isNotBlank()) {
                try {
                    val url = "$API_BASE/wefeed-h5api-bff/detail?subjectId=$sidParam"
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", UA)
                        .header("Accept", "application/json")
                        .header("X-Client-Info", "{\"timezone\":\"Africa/Lagos\"}")
                        .header("Origin", SITE_BASE)
                        .header("Referer", "$SITE_BASE/")
                        .get()
                        .build()

                    okHttpClient.newCall(request).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val jsonStr = resp.body?.string() ?: ""
                            val root = JSONObject(jsonStr)
                            subject = root.optJSONObject("data")?.optJSONObject("subject")
                            resource = root.optJSONObject("data")?.optJSONObject("resource")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "subjectId lookup failed for $sidParam: ${e.message}")
                }
            }

            val validSubject = subject ?: return@withContext video
            val title = validSubject.optString("title", video.title)
            val description = validSubject.optString("description", video.description)
            val stype = validSubject.optInt("subjectType", if (video.isShortTv) 7 else if (video.isSeries == true) 2 else 1)
            val sid = validSubject.optString("subjectId", video.id.removePrefix("mb_"))
            val backdrop = validSubject.optJSONObject("trailer")?.optJSONObject("cover")?.optString("url") ?: ""

            // Cast
            val staffList = validSubject.optJSONArray("staffList")
            val castList = mutableListOf<String>()
            if (staffList != null) {
                for (i in 0 until staffList.length()) {
                    val staff = staffList.optJSONObject(i) ?: continue
                    val name = staff.optString("name")
                    if (name.isNotBlank()) castList.add(name)
                }
            }

            // Episodes if TV Series or Short TV
            val isShortDrama = (stype == 5 || stype == 7 || video.isShortTv || video.videoUrl.contains("type=7") || video.videoUrl.contains("type=5")) && 
                    (stype != 2 && stype != 1 && !video.videoUrl.contains("type=2") && !video.videoUrl.contains("type=1"))
            val episodes = mutableListOf<Episode>()
            if (stype == 2 || isShortDrama) {
                val seasons = resource?.optJSONArray("seasons")
                if (seasons != null) {
                    for (i in 0 until seasons.length()) {
                        val seasonObj = seasons.optJSONObject(i) ?: continue
                        val se = seasonObj.optInt("se", 1)
                        val maxEp = seasonObj.optInt("maxEp", 1)
                        for (ep in 1..maxEp) {
                            episodes.add(
                                Episode(
                                    id = "mb_${sid}_s${se}_e${ep}",
                                    name = if (isShortDrama) "Episode $ep" else "Season $se Ep $ep",
                                    url = "moviebox://$dp?id=$sid&type=$stype&se=$se&ep=$ep",
                                    season = if (isShortDrama) "Episodes" else "Season $se"
                                )
                            )
                        }
                    }
                }
            }

            // Pre-resolve movie streams if it is a single movie
            val servers = if (stype == 1) {
                fetchPlayStreams(sid, dp, se = 0, ep = 0)
            } else emptyList()

            video.copy(
                title = title,
                description = description,
                backdropUrl = if (backdrop.isNotBlank()) backdrop else video.thumbnailUrl,
                actresses = castList,
                episodes = episodes,
                servers = servers,
                isSeries = (stype == 2 || isShortDrama),
                isShortTv = isShortDrama
            )
        } catch (e: Exception) {
            Log.w(TAG, "Video detail error on Moviebox: ${e.message}")
            video
        }
    }

    override suspend fun fetchServers(video: Video): List<VideoServer> = withContext(Dispatchers.IO) {
        val sid = extractParam(video.videoUrl, "id").ifBlank { video.id.removePrefix("mb_") }
        val dp = extractDetailPath(video.videoUrl)
        var se = extractParam(video.videoUrl, "se").toIntOrNull() ?: 0
        var ep = extractParam(video.videoUrl, "ep").toIntOrNull() ?: 0
        if ((video.isSeries == true || video.isShortTv) && se == 0 && ep == 0) {
            se = 1
            ep = 1
        }
        val streams = fetchPlayStreams(sid, dp, se, ep)
        if (streams.isNotEmpty()) streams else video.servers
    }

    private fun fetchPlayStreams(sid: String, dp: String, se: Int, ep: Int): List<VideoServer> {
        val servers = mutableListOf<VideoServer>()
        val playDp = if (dp.isNotBlank()) dp else sid
        val encodedDp = try { URLEncoder.encode(playDp, "UTF-8") } catch (_: Exception) { playDp }
        val isTv = (se > 0)
        val playReferer = "$SITE_BASE/videoPlayPage/$playDp?type=" + (if (isTv) "/tv/detail" else "/movie/detail")

        // 1. Try /subject/play (Full quality 1080P/720P/480P)
        try {
            val url = "$API_BASE/wefeed-h5api-bff/subject/play?subjectId=$sid&se=$se&ep=$ep&detailPath=$encodedDp"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "application/json")
                .header("X-Client-Info", "{\"timezone\":\"Africa/Lagos\"}")
                .header("Origin", SITE_BASE)
                .header("Referer", playReferer)
                .get()
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val root = JSONObject(body)
                    val streamArr = root.optJSONObject("data")?.optJSONArray("streams")
                    if (streamArr != null) {
                        for (i in 0 until streamArr.length()) {
                            val s = streamArr.optJSONObject(i) ?: continue
                            val streamUrl = s.optString("url")
                            val res = s.optString("resolutions", s.optString("resolution", "HD"))
                            val sizeBytes = s.optLong("size", 0L)
                            val sizeMb = if (sizeBytes > 0) " (${sizeBytes / 1_000_000}MB)" else ""
                            if (streamUrl.isNotBlank() && streamUrl.startsWith("http")) {
                                servers.add(VideoServer(name = "MovieBox ${res}P$sizeMb", url = streamUrl))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Play error on Moviebox: ${e.message}")
        }

        // 2. Fallback to /subject/download
        if (servers.isEmpty()) {
            try {
                val url = "$SITE_BASE/wefeed-h5api-bff/subject/download?subjectId=$sid&se=$se&ep=$ep&detailPath=$encodedDp"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", UA)
                    .header("Accept", "application/json")
                    .header("X-Client-Info", "{\"timezone\":\"Africa/Lagos\"}")
                    .header("Origin", SITE_BASE)
                    .header("Referer", playReferer)
                    .get()
                    .build()

                okHttpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        val root = JSONObject(body)
                        val dlArr = root.optJSONObject("data")?.optJSONArray("downloads")
                        if (dlArr != null) {
                            for (i in 0 until dlArr.length()) {
                                val s = dlArr.optJSONObject(i) ?: continue
                                val dlUrl = s.optString("url")
                                val res = s.optString("resolution", "HD")
                                val isVip = s.optBoolean("vipLocked", false)
                                if (!isVip && dlUrl.isNotBlank() && dlUrl.startsWith("http")) {
                                    servers.add(VideoServer(name = "MovieBox Backup ${res}P", url = dlUrl))
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Download fallback error on Moviebox: ${e.message}")
            }
        }

        return servers
    }

    private fun extractDetailPath(videoUrl: String): String {
        return if (videoUrl.contains("moviebox://")) {
            videoUrl.substringAfter("moviebox://").substringBefore('?').trim('/')
        } else {
            try {
                videoUrl.substringAfter("://").substringBefore('?').trim('/')
            } catch (_: Exception) {
                ""
            }
        }
    }

    private fun extractParam(videoUrl: String, key: String): String {
        return try {
            val queryPart = videoUrl.substringAfter('?', "")
            if (queryPart.isNotEmpty()) {
                for (p in queryPart.split('&')) {
                    if (p.startsWith("$key=")) {
                        return p.substringAfter("$key=")
                    }
                }
            }
            val uri = Uri.parse(videoUrl)
            uri.getQueryParameter(key) ?: ""
        } catch (_: Exception) {
            ""
        }
    }
}
