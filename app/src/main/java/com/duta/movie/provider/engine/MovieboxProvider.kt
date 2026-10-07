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

    private fun getClientToken(): String {
        val e = System.currentTimeMillis() / 1000
        val rev = e.toString().reversed()
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(rev.toByteArray())
        val hash = digest.joinToString("") { "%02x".format(it) }
        return "$e,$hash"
    }

    private fun ensureJwtToken(): String? {
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

                    list.add(
                        Video(
                            id = "mb_$sid",
                            title = title,
                            thumbnailUrl = cover,
                            videoUrl = "moviebox://$dp?id=$sid&type=$stype",
                            duration = "",
                            quality = if (rating.isNotBlank()) "★ $rating" else "HD",
                            isSeries = (stype == 2 || stype == 5 || stype == 7),
                            isShortTv = (stype == 5 || stype == 7)
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
        val list = mutableListOf<Video>()
        val seenSids = mutableSetOf<String>()

        // 7844144696607102784 = "Hot Short TV" (DramaBox Chinese micro-dramas)
        // 173752404280836544  = "Trending C-Drama" (Chinese drama series)
        val targetPage = if (page <= 3) page else (page - 3)
        val targetRankId = if (page <= 3) "7844144696607102784" else "173752404280836544"

        try {
            val url = "$API_BASE/wefeed-h5api-bff/ranking-list/content?id=$targetRankId&page=$targetPage&perPage=$count"
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
                        if (sid.isBlank() || title.isBlank() || !seenSids.add(sid)) continue

                        val cover = item.optJSONObject("cover")?.optString("url") ?: ""
                        val stype = item.optInt("subjectType", 7)
                        val rating = item.optString("imdbRatingValue", "ShortTV")
                        val releaseDate = item.optString("releaseDate", "")

                        list.add(
                            Video(
                                id = "mb_$sid",
                                title = title,
                                thumbnailUrl = cover,
                                videoUrl = "moviebox://$dp?id=$sid&type=$stype",
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
            Log.w(TAG, "Failed fetching Short TV ranking list: ${e.message}")
        }

        // Fallback: If ranking list returned empty (e.g. out of pages), try static ranking list endpoint
        if (list.isEmpty() && page == 1) {
            try {
                val fallbackUrl = "$API_BASE/wefeed-h5api-bff/ranking-list?id=7844144696607102784&page=1&perPage=$count"
                val request = Request.Builder()
                    .url(fallbackUrl)
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
                            if (sid.isBlank() || title.isBlank() || !seenSids.add(sid)) continue

                            val cover = item.optJSONObject("cover")?.optString("url") ?: ""
                            val stype = item.optInt("subjectType", 7)
                            val rating = item.optString("imdbRatingValue", "ShortTV")
                            val releaseDate = item.optString("releaseDate", "")

                            list.add(
                                Video(
                                    id = "mb_$sid",
                                    title = title,
                                    thumbnailUrl = cover,
                                    videoUrl = "moviebox://$dp?id=$sid&type=$stype",
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
                Log.w(TAG, "Failed fetching Short TV fallback: ${e.message}")
            }
        }

        return list
    }

    override suspend fun fetchVideoDetail(video: Video): Video? = withContext(Dispatchers.IO) {
        val dp = extractDetailPath(video.videoUrl)
        if (dp.isBlank()) return@withContext video
        try {
            val url = "$API_BASE/wefeed-h5api-bff/detail?detailPath=${URLEncoder.encode(dp, "UTF-8")}"
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
                if (!resp.isSuccessful) return@withContext video
                val jsonStr = resp.body?.string() ?: return@withContext video
                val root = JSONObject(jsonStr)
                val subject = root.optJSONObject("data")?.optJSONObject("subject") ?: return@withContext video
                val resource = root.optJSONObject("data")?.optJSONObject("resource")

                val title = subject.optString("title", video.title)
                val description = subject.optString("description", video.description)
                val stype = subject.optInt("subjectType", if (video.isShortTv) 7 else if (video.isSeries == true) 2 else 1)
                val sid = subject.optString("subjectId", video.id.removePrefix("mb_"))
                val backdrop = subject.optJSONObject("trailer")?.optJSONObject("cover")?.optString("url") ?: ""

                // Cast
                val staffList = subject.optJSONArray("staffList")
                val castList = mutableListOf<String>()
                if (staffList != null) {
                    for (i in 0 until staffList.length()) {
                        val staff = staffList.optJSONObject(i) ?: continue
                        val name = staff.optString("name")
                        if (name.isNotBlank()) castList.add(name)
                    }
                }

                // Episodes if TV Series or Short TV
                val isShortDrama = (stype == 5 || stype == 7 || video.isShortTv || video.videoUrl.contains("type=7") || video.videoUrl.contains("type=5"))
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
            }
        } catch (e: Exception) {
            Log.w(TAG, "Video detail error on Moviebox: ${e.message}")
            video
        }
    }

    override suspend fun fetchServers(video: Video): List<VideoServer> = withContext(Dispatchers.IO) {
        if (video.servers.isNotEmpty()) return@withContext video.servers
        val sid = extractParam(video.videoUrl, "id").ifBlank { video.id.removePrefix("mb_") }
        val dp = extractDetailPath(video.videoUrl)
        var se = extractParam(video.videoUrl, "se").toIntOrNull() ?: 0
        var ep = extractParam(video.videoUrl, "ep").toIntOrNull() ?: 0
        if ((video.isSeries == true || video.isShortTv) && se == 0 && ep == 0) {
            se = 1
            ep = 1
        }
        fetchPlayStreams(sid, dp, se, ep)
    }

    private fun fetchPlayStreams(sid: String, dp: String, se: Int, ep: Int): List<VideoServer> {
        val servers = mutableListOf<VideoServer>()
        val encodedDp = try { URLEncoder.encode(dp, "UTF-8") } catch (_: Exception) { dp }
        val isTv = (se > 0)
        val playReferer = "$SITE_BASE/videoPlayPage/$dp?type=" + (if (isTv) "/tv/detail" else "/movie/detail")

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
