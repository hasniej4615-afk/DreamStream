package com.duta.movie.util

import android.util.Log
import androidx.core.net.toUri
import com.duta.movie.model.Episode
import com.duta.movie.model.Video
import com.duta.movie.model.VideoServer
import com.duta.movie.model.Subtitle
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.regex.Pattern
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.suspendCancellableCoroutine
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * HIGH-PERFORMANCE VIDEO EXTRACTION ENGINE
 * Restored and Optimized based on Proven Build logic
 */
object VideoExtractor {
    private const val TAG = "!!!VideoExtractor!!!"
    private var USER_AGENT = NetworkConfig.SHARED_USER_AGENT
    private var BASE_URL = "https://algarvebuzz.com"

    fun setUserAgent(ua: String) {
        USER_AGENT = ua
    }

    private var onDomainLearned: ((String) -> Unit)? = null
    private val fetchSemaphore = Semaphore(8)
    private val scrapeSemaphore = Semaphore(4)

    fun setOnDomainLearned(listener: (String) -> Unit) {
        onDomainLearned = listener
    }

    private var PENCURI_BASE_URL = "https://ww44.pencurimovie.baby"
    private var onPencuriDomainLearned: ((String) -> Unit)? = null

    val PENCURI_FALLBACKS = listOf(
        "https://ww44.pencurimovie.baby",
        "https://pencurimovie.baby",
        "https://pencurimovie.my",
        "https://ww45.pencurimovie.baby",
        "https://ww43.pencurimovie.baby"
    )

    fun setOnPencuriDomainLearned(listener: (String) -> Unit) {
        onPencuriDomainLearned = listener
    }

    fun getPencuriBaseUrl(): String = PENCURI_BASE_URL

    fun setPencuriBaseUrl(newUrl: String) {
        PENCURI_BASE_URL = newUrl
    }

    fun updatePencuriBaseUrl(newUrl: String) {
        if (newUrl.startsWith("http")) {
            val host = try { android.net.Uri.parse(newUrl).host?.lowercase() } catch(_: Exception) { null }
                ?: try { java.net.URI(newUrl).host?.lowercase() } catch(_: Exception) { null }
            val scheme = try { android.net.Uri.parse(newUrl).scheme } catch(_: Exception) { null }
                ?: try { java.net.URI(newUrl).scheme } catch(_: Exception) { null } ?: "https"
            if (host != null && (host.contains("pencurimovie") || host.contains("pencurifilm") || host.contains("pencurivideo"))) {
                val root = "$scheme://$host"
                if (root != PENCURI_BASE_URL && root.length > 10 && root.length < 100) {
                    PENCURI_BASE_URL = root
                    Log.i(TAG, "LEARNED: Active PencuriMovie domain updated to: $PENCURI_BASE_URL")
                    onPencuriDomainLearned?.invoke(PENCURI_BASE_URL)
                }
            }
        }
    }

    suspend fun probePencuriDomain(): String? = withContext(Dispatchers.IO) {
        Log.i(TAG, "SCOUTING: Starting adaptive PencuriMovie domain discovery...")
        val remotePencuri = try { UpdateChecker.fetchRemotePencuriMirrors() } catch(_: Exception) { null }
        val allFallbacks = (remotePencuri ?: emptyList()) + PENCURI_FALLBACKS
        for (candidate in allFallbacks) {
            try {
                val testUrl = "$candidate/country/malaysia/"
                val request = Request.Builder().url(testUrl).header("User-Agent", USER_AGENT).build()
                NetworkConfig.fastOkHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val finalUrl = response.request.url.toString()
                        val html = response.body?.string() ?: ""
                        if (html.contains("pencurimovie") || html.contains("class=\"gmr-") || html.contains("content=\"video.movie\"") || html.contains("id=\"movies\"")) {
                            val root = "${response.request.url.scheme}://${response.request.url.host}"
                            Log.i(TAG, "SCOUTING: Live PencuriMovie domain confirmed: $root (via $finalUrl)")
                            updatePencuriBaseUrl(root)
                            return@withContext root
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Pencuri probe failed for $candidate: ${e.message}")
            }
        }
        null
    }

    fun setBaseUrl(newUrl: String) {
        BASE_URL = newUrl
    }

    fun updateBaseUrl(newUrl: String, force: Boolean = false) {
        if (newUrl.startsWith("http")) {
            val host = try { android.net.Uri.parse(newUrl).host?.lowercase() } catch(_: Exception) { null }
                ?: try { java.net.URI(newUrl).host?.lowercase() } catch(_: Exception) { null }
            val scheme = try { android.net.Uri.parse(newUrl).scheme } catch(_: Exception) { null }
                ?: try { java.net.URI(newUrl).scheme } catch(_: Exception) { null } ?: "https"
            if (!host.isNullOrEmpty()) {
                
                // Explicitly block known "poison" domains, VIDEO HOSTS, and GATEWAYS from hijacking BASE_URL
                val isBlockedHost = host.contains("duta.media") || host.contains("dutamovie21.now") ||
                    host.contains("test-videos.co.uk") || host.contains("sample-videos.com") || 
                    host.contains("bigbuckbunny") || host.contains("bitmovin.com") || 
                    host.contains("yandex.ru") || host.contains("google.com") ||
                    host.contains("ketik.live") || host.contains("klikzeus") || host.contains("vingaming") ||
                    host.contains("zeus88") || host.contains("klik.top") || host.contains("googletagmanager") ||
                    host.contains("archive.org") || host.contains("kepalabergetar") ||
                    host.contains("youtube") || host.contains("youtu.be") || host.contains("bilibili") || host.contains("dailymotion") ||
                    host.contains("google") || host.contains("player") || 
                    host.contains("stream") || host.contains("mirror") || host.contains("cdn") ||
                    host.contains("pencurimovie") || host.contains("pencurivideo") || host.contains("pencurifilm") ||
                    // Video hosts, stream endpoints, and embed players
                    host.contains("vibuxer") || host.contains("hanerix") || host.contains("hgcloud") ||
                    host.contains("hglink") || host.contains("hgcdn") || host.contains("voe") ||
                    host.contains("johnfullwonder") || host.contains("cloudwindow") || host.contains("abyss") ||
                    host.contains("bond") || host.contains("iamcdn") || host.contains("dsvplay") ||
                    host.contains("playmogo") || host.contains("vidhide") || host.contains("vidsrc") ||
                    host.contains("dood") || host.contains("streamtape") || host.contains("tapecontent") ||
                    host.contains("listeamed") || host.contains("indostream") || host.contains("amt") ||
                    host.contains("iplayer") || host.contains("masuk") || host.contains("audinifer") ||
                    host.contains("duvidun") || host.contains("fujihide") || host.contains("rebeccasciencestreet") ||
                    host.contains("katherineschoolphone") ||
                    host.contains("turboviplay") || host.contains("streamwish") || host.contains("filelions") ||
                    host.contains("mixdrop") || host.contains("streamhub") || host.contains("upstream") ||
                    host.contains("vidspeeds") || host.contains("dropload") || host.contains("hexload") ||
                    host.contains("userload") || host.contains("supervideo") || host.contains("vidmoly") ||
                    host.contains("senvid") || host.contains("luluvdo") || host.contains("emturbovid") ||
                    host.contains("streamhide") || host.contains("hubcloud") || host.contains("fastdl") ||
                    host.contains("gdflix") || host.contains("driveleech") || host.contains("short.ink") ||
                    host.contains("hstream") || host.contains("veev") || host.contains("swhoi")
                if (isBlockedHost) return

                val isValidDomain = host.contains(".") && !host.endsWith(".local") && !host.endsWith(".arpa")
                if (force || isValidDomain) {
                    val root = "$scheme://$host"
                    if (root != BASE_URL && root.length > 10 && root.length < 100) {
                        BASE_URL = root
                        Log.i(TAG, "LEARNED: Active domain updated to: $BASE_URL (Force=$force)")
                        onDomainLearned?.invoke(BASE_URL)
                    }
                }
            }
        }
    }

    fun getBaseUrl(): String = BASE_URL

    fun normalizePath(path: String): String {
        if (path.isEmpty() || path == "/") return "/"
        if (path.contains("country/viet-nam", ignoreCase = true) || path.contains("country/vietnam", ignoreCase = true)) return "/country/viet-nam/"
        if (path.contains("country/malaysia", ignoreCase = true)) return "/country/malaysia/"
        if (path.contains("p-ramlee", ignoreCase = true) || path.contains("FilemP.ramlee", ignoreCase = true)) return "/category/p-ramlee/"
        if (path.matches(Regex("""^(.*/)?sci-fi/?$""", RegexOption.IGNORE_CASE))) return "/science-fiction/"
        if (path.startsWith("http")) return path.trim()
        
        // Repair corrupted external URLs saved by older versions (e.g. /https:/example.com -> https://example.com)
        val repaired = path.trim().removePrefix("/")
        if (repaired.startsWith("https:") || repaired.startsWith("http:")) {
            return repaired.replaceFirst(":/", "://").replace(Regex("(?<=://)/{1,}"), "")
        }
        
        var p = path.trim()
        if (!p.startsWith("/")) p = "/$p"
        if (!p.endsWith("/")) p = "$p/"
        
        // Handle double slashes gracefully but preserve scheme `://` if it somehow slipped in
        return p.replace(Regex("(?<!http:|https:)//"), "/")
    }

    fun migrateUrlToBase(url: String): String {
        if (!url.startsWith("http")) return url
        val uri = try { android.net.Uri.parse(url) } catch(_: Exception) { null } ?: return url
        val host = uri.host?.lowercase() ?: return url
        
        if (host.contains("archive.org")) return url
        if (host.contains("kepalabergetar")) return url
        
        // Auto-heal for PencuriMovie standalone catalog
        if (host.contains("pencurimovie") || host.contains("pencurifilm") || host.contains("pencurivideo")) {
            val currentPencuri = getPencuriBaseUrl()
            val pencuriUri = try { android.net.Uri.parse(currentPencuri) } catch(_: Exception) { null }
            if (pencuriUri != null && pencuriUri.host != null && !host.equals(pencuriUri.host, ignoreCase = true)) {
                val pathWithQuery = url.substringAfter(host)
                val migrated = "$currentPencuri$pathWithQuery"
                Log.d(TAG, "PENCURI AUTO-HEAL MIGRATION: $url -> $migrated")
                return migrated
            }
            return url
        }

        var currentBase = getBaseUrl()
        val lowBase = currentBase.lowercase()
        if (lowBase.contains("katherineschoolphone") || lowBase.contains("voe") || lowBase.contains("player") || !lowBase.startsWith("http")) {
            currentBase = "https://algarvebuzz.com"
            setBaseUrl(currentBase)
        }
        val currentBaseUri = try { android.net.Uri.parse(currentBase) } catch(_: Exception) { null }
        if (currentBaseUri != null && currentBaseUri.host != host) {
            val pathWithQuery = url.substringAfter(host)
            val migrated = "$currentBase$pathWithQuery"
            Log.d(TAG, "AUTO-HEAL MIGRATION: $url -> $migrated")
            return migrated
        }
        return url
    }

    /**
     * Identifies if a video or server belongs to Malaysia (PencuriMovie) standalone category source.
     */
    fun isPencuriMovie(videoId: String? = null, videoUrl: String? = null, streamUrl: String? = null): Boolean {
        if (videoId?.startsWith("pm_") == true) return true
        if (videoUrl?.contains("pencurimovie", ignoreCase = true) == true) return true
        if (streamUrl?.contains("pencurimovie", ignoreCase = true) == true) return true
        return false
    }

    /**
     * Identifies if a video or server belongs to Malaysia (Kepala Bergetar) drama source.
     */
    fun isKepalaBergetar(videoId: String? = null, videoUrl: String? = null, streamUrl: String? = null): Boolean {
        if (videoId?.startsWith("kb_") == true) return true
        if (videoUrl?.contains("kepalabergetar", ignoreCase = true) == true) return true
        if (streamUrl?.contains("kepalabergetar", ignoreCase = true) == true) return true
        return false
    }

    private fun isLegitSite(html: String): Boolean {
        if (html.length < 500) return false
        val low = html.lowercase()
        if (low.contains("content delivery network (cdn) & video cloud") || low.contains("<title>voe")) return false
        return low.contains("doo_player_ajax") || low.contains("muvipro-listepisode") || 
               low.contains("dutamovie21") || low.contains("itoshii-movie") ||
               low.contains("dutamovie") || low.contains("indostream") ||
               low.contains("amt") || low.contains("zeus") || low.contains("eddieoneverything") ||
               low.contains("gmr-watch-button") || low.contains("gmr-movie-on") ||
               low.contains("dooplay") || low.contains("dbmovies") ||
               low.contains("muvipro") || low.contains("algarvebuzz") ||
               low.contains("pencurimovie") || low.contains("pencurifilm") ||
               // Generic structural signatures and newly verified domains
               low.contains("katakatamutiara") || low.contains("ohionewsnow") ||
               low.contains("og:type\" content=\"video.movie\"") ||
               low.contains("class=\"gmr-movie-") || low.contains("id=\"movies\"") ||
               low.contains("href=\"/movie/") || low.contains("href=\"/episode/") ||
               low.contains("href=\"/series/")
    }

    suspend fun probeForNewDomain(): String? = withContext(Dispatchers.IO) {
        Log.i(TAG, "SCOUTING: Starting adaptive domain discovery...")
        
        // PRIMARY SCOUT: Dedicated gateway
        val gateways = listOf("https://duta.media/", "https://dutamovie21.now/")
        val deferredGateways = gateways.map { gateway ->
            async {
                try {
                    val request = Request.Builder().url(gateway).header("User-Agent", USER_AGENT).build()
                    NetworkConfig.okHttpClient.newCall(request).execute().use { response ->
                        val finalUrl = response.request.url.toString()
                        val html = response.body?.string() ?: ""
                        
                        // TRUST THE GATEWAY: If our dedicated gateway redirects us somewhere, accept it.
                        if (response.isSuccessful && !finalUrl.contains("duta.media") && !finalUrl.contains("dutamovie21.now")) {
                            Log.i(TAG, "Gateway redirected directly to new domain: $finalUrl")
                            return@async "${response.request.url.scheme}://${response.request.url.host}"
                        }
                        
                        val doc = Jsoup.parse(html, gateway)
                        val links = doc.select("a[href], iframe[src]")
                        for (link in links) {
                            val href = link.attr("abs:href").ifEmpty { link.attr("abs:src") }
                            if (href.isNotEmpty()) {
                                val root = try { 
                                    val u = android.net.Uri.parse(href)
                                    "${u.scheme}://${u.host}"
                                } catch(_: Exception) { null }
                                
                                if (root != null && !root.contains("facebook") && !root.contains("twitter") && !root.contains("google") && !root.contains("duta.media") && !root.contains("dutamovie21.now")) {
                                    if (pingAndVerify(root)) {
                                        return@async root
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "SCOUTING: Gateway probe failed for $gateway: ${e.message}")
                }
                null
            }
        }
        val primaryResult = deferredGateways.firstNotNullOfOrNull { it.await() }
        if (primaryResult != null) {
            updateBaseUrl(primaryResult, force = true)
            return@withContext primaryResult
        }

        // TIER 2 SCOUT: Remote Over-The-Air (OTA) Config from GitHub Gist
        try {
            val remoteCandidates = UpdateChecker.fetchRemoteMirrors()
            if (!remoteCandidates.isNullOrEmpty()) {
                Log.i(TAG, "SCOUTING: Probing ${remoteCandidates.size} remote mirrors from OTA config...")
                val deferredRemote = remoteCandidates.map { url ->
                    async {
                        if (pingAndVerify(url)) url else null
                    }
                }
                val remoteResult = deferredRemote.firstNotNullOfOrNull { it.await() }
                if (remoteResult != null) {
                    updateBaseUrl(remoteResult, force = true)
                    return@withContext remoteResult
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "SCOUTING: OTA mirror check failed: ${e.message}")
        }

        // TIER 3 SCOUT: Known fallback candidate patterns
        val candidates = listOf(
            "https://algarvebuzz.com",
            "https://ohionewsnow.com",
            "https://billofrightsforum.org",
            "https://eddieoneverything.com", 
            "https://bokinshop.com", 
            "https://itoshii-movie.com",
            "https://dutamovie.com",
            "https://katakatamutiara.com"
        )
        val deferredProbes = candidates.map { url ->
            async {
                if (pingAndVerify(url)) url else null
            }
        }
        val result = deferredProbes.firstNotNullOfOrNull { it.await() }
        result?.let { updateBaseUrl(it, force = true) }
        result
    }

    suspend fun pingAndVerify(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            NetworkConfig.fastOkHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val finalHost = response.request.url.host.lowercase()
                    if (finalHost.contains("voe") || finalHost.contains("katherineschoolphone") || 
                        finalHost.contains("player") || finalHost.contains("stream")) {
                        return@withContext false
                    }
                    val html = response.body?.string() ?: ""
                    isLegitSite(html)
                } else false
            }
        } catch (_: Exception) { false }
    }

    suspend fun fetchHtml(url: String, referer: String? = null): String? = withContext(Dispatchers.IO) {
        fetchSemaphore.withPermit {
            for (i in 0..1) {
                try {
                    val tryUrl = if (i == 1 && url.startsWith("https://")) url.replace("https://", "http://") else url
                    val encodedUrl = tryUrl.replace(" ", "%20").replace("[", "%5B").replace("]", "%5D")
                    val actualReferer = referer ?: if (url.startsWith("http")) {
                        try { val u = java.net.URL(url); "${u.protocol}://${u.host}/" } catch (_: Exception) { "$BASE_URL/" }
                    } else "$BASE_URL/"
                    
                    val request = Request.Builder().url(encodedUrl)
                        .header("User-Agent", USER_AGENT)
                        .header("Referer", actualReferer)
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                        .header("Cache-Control", "no-cache")
                        .header("Sec-Ch-Ua", "\"Not A(Brand\";v=\"8\", \"Chromium\";v=\"132\", \"Google Chrome\";v=\"132\"")
                        .header("Sec-Fetch-Dest", "document")
                        .header("Sec-Fetch-Mode", "navigate")
                        .header("Sec-Fetch-Site", "cross-site")
                        .build()

                    val response = suspendCancellableCoroutine { continuation ->
                        val call = NetworkConfig.fastOkHttpClient.newCall(request)
                        continuation.invokeOnCancellation { call.cancel() }
                        call.enqueue(object : okhttp3.Callback {
                            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) { continuation.resumeWith(Result.failure(e)) }
                            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) { continuation.resumeWith(Result.success(response)) }
                        })
                    }
                    response.use { resp ->
                        if (resp.code == 404 || resp.code == 410) {
                            Log.w(TAG, "fetchHtml: Dead mirror ($url returned ${resp.code})")
                            return@withPermit null
                        }
                        val finalUrl = resp.request.url.toString()
                        val body = if (resp.isSuccessful) resp.body?.string() else null
                        
                        val finalHost = try { resp.request.url.host.lowercase() } catch(_: Exception) { "" }
                        if (finalHost.contains("pencurimovie") || finalHost.contains("pencurifilm") || finalHost.contains("pencurivideo")) {
                            if (!body.isNullOrEmpty() && (body.contains("pencurimovie") || body.contains("pencurifilm") || body.contains("player_nav") || body.contains("class=\"gmr-") || body.contains("content=\"video.movie\""))) {
                                val scheme = try { resp.request.url.scheme } catch(_: Exception) { "https" }
                                val rootUrl = "$scheme://$finalHost"
                                Log.d(TAG, "Dynamic Learning: PencuriMovie site verified. Updating base to: $rootUrl")
                                updatePencuriBaseUrl(rootUrl)
                            }
                        } else if (finalHost.contains("kepalabergetar") || finalHost.contains("archive.org") || 
                                   finalHost.contains("youtube") || finalHost.contains("youtu.be") || 
                                   finalHost.contains("bilibili") || finalHost.contains("dailymotion")) {
                            // Dedicated external providers - never learn as DutaMovie BASE_URL
                        } else {
                            // OWL'S EYE: Active learning from the current response (never force-hijack from random fetches)
                            if (!body.isNullOrEmpty() && isLegitSite(body)) {
                                val scheme = try { resp.request.url.scheme } catch(_: Exception) { "https" }
                                val rootUrl = "$scheme://$finalHost"
                                Log.d(TAG, "Dynamic Learning: Site verified. Proposing base: $rootUrl")
                                updateBaseUrl(rootUrl, force = true)
                            }
                        }

                        if (!body.isNullOrEmpty()) return@withPermit body
                    }
                } catch (e: Exception) {
                    if (e is java.net.SocketTimeoutException || e is java.net.ConnectException || e is java.io.InterruptedIOException) {
                        Log.w(TAG, "fetchHtml timeout on $url: ${e.message}")
                        break // Do not retry unreachable / timed out hosts
                    }
                }
                // TV Mode optimization: Reduce retry delay for faster instant load
                delay(100)
            }
            null
        }
    }

    fun sanitizeUrl(url: String, baseUrl: String): String {
        var u = url.trim().replace("\\/", "/").replace("\\&", "&").replace("&amp;", "&").replace("&quot;", "\"")
        
        val lastHttp = u.lastIndexOf("http")
        if (lastHttp > 0) {
            val extracted = u.substring(lastHttp)
            val stopChars = charArrayOf('\"', '\'', ';', ' ', ')', '(', ']', '[', '>', '<', '|', '{', '}', ',')
            val junkIdx = extracted.indexOfAny(stopChars)
            u = if (junkIdx != -1) extracted.substring(0, junkIdx) else extracted
        }

        if (u.startsWith("//")) u = "https:$u"
        else if (u.startsWith("/") && !u.startsWith("//")) {
            val root = try { 
                val uri = android.net.Uri.parse(baseUrl)
                if (uri.host != null) "${uri.scheme}://${uri.host}" else BASE_URL
            } catch (_: Exception) { BASE_URL }
            u = "$root$u"
        }
        
        u = u.trimEnd('.', ':', ';', '!', '?', '#', ' ')

        if (u.contains("abyss") && (u.contains("/f/") || u.contains("/v/"))) {
             u = u.replace("/f/", "/e/").replace("/v/", "/e/")
        } else if (u.contains("abyssplayer.com") && !u.contains("/e/") && u.substringAfter("com/").isNotEmpty() && !u.substringAfter("com/").startsWith("?")) {
             u = u.replace("com/", "com/e/")
        }
        else if (u.contains("dood") && (u.contains("/d/") || u.contains("/f/"))) {
             u = u.replace("/d/", "/e/").replace("/f/", "/e/")
        }
        else if (u.contains("listeamed") && u.contains("/d/")) u = u.replace("/d/", "/v/")
        else if (u.contains("swhoi") && u.contains("/f/")) u = u.replace("/f/", "/e/")
        else if (u.contains("playstream") && (u.contains("/v/") || (u.substringAfter("video/").isNotEmpty() && !u.contains("/e/")))) {
             u = u.replace("/v/", "/e/")
             if (u.contains("video/") && !u.contains("video/e/")) u = u.replace("video/", "video/e/")
        }
        else if (u.contains("hgcloud") || u.contains("hglink") || u.contains("vibuxer") || 
                 u.contains("hanerix") || u.contains("clouds.to") || u.contains("masukestin") || 
                 u.contains("audinifer") || u.contains("distributedcomputing") || u.contains("harmonixinnovationlab") ||
                 u.contains("dhcplay") || u.contains("ryder") || u.contains("abysscdn") || u.contains("morencius") ||
                 u.contains("bestcdn") || u.contains("veev")) {
            if (u.contains("/f/")) u = u.replace("/f/", "/e/")
            else if (u.contains("/v/")) u = u.replace("/v/", "/e/")
            else if (u.contains("/d/")) u = u.replace("/d/", "/e/")
        }
        
        if (u.contains("’")) u = u.replace("’", "'")
        if (u.contains(" ")) u = u.replace(" ", "%20")

        return u
    }

    data class ExtractionResult(
        val videoUrl: String,
        val posterUrl: String? = null,
        val imdbId: String? = null,
        val subtitles: List<Subtitle> = emptyList(),
        val referer: String? = null,
        val cookies: String? = null,
        val sourceMirrorUrl: String? = null
    )

    suspend fun extractStreamtape(pageUrl: String, referer: String? = null): String? = withContext(Dispatchers.IO) {
        try {
            val html = fetchHtml(pageUrl, referer ?: pageUrl) ?: return@withContext null
            val host = try { java.net.URI(pageUrl).host } catch (_: Exception) { null } ?: "streamtape.com"

            // Strategy 1: Find query inside script assignments to robotlink / botlink / ideoolink
            // Streamtape constructs the real stream link in Javascript:
            // document.getElementById('robotlink').innerHTML = ... ('..._video?id=...&token=...').substring(...)
            val scriptRegex = Regex("""document\.getElementById\(['"](?:robotlink|botlink|ideoolink)['"]\)\.innerHTML\s*=\s*(.+?);""")
            val scriptAssignments = scriptRegex.findAll(html).map { it.groupValues[1] }.toList()
            for (assignment in scriptAssignments) {
                val queryMatch = Regex("""['"]([^'"]*?\?(id=[^'"]+))['"]""").find(assignment)
                if (queryMatch != null) {
                    val query = queryMatch.groupValues[2]
                    if (query.contains("token=") && query.contains("expires=")) {
                        val token = query.substringAfter("token=").substringBefore('&')
                        if (!token.endsWith("cde") && !token.endsWith("xyza")) {
                            val streamParam = if (!query.contains("stream=")) "&stream=1" else ""
                            val directUrl = "https://$host/get_video?$query$streamParam"
                            Log.i(TAG, "Streamtape direct extracted (script assignment): $directUrl")
                            return@withContext directUrl
                        }
                    }
                }
            }

            // Strategy 2: Scan all queries containing id, expires, and token, rejecting honeypot decoys
            // Honeypot tokens end in 'cde' or 'xyza' and return text/html error pages rather than video.
            val queryRegex = Regex("""\?(id=[a-zA-Z0-9_-]+&expires=\d+&ip=[^&'"]+&token=([a-zA-Z0-9_-]+))""")
            val candidateQueries = queryRegex.findAll(html)
                .filter { match ->
                    val token = match.groupValues[2]
                    !token.endsWith("cde") && !token.endsWith("xyza")
                }
                .map { it.groupValues[1] }
                .toList()

            val bestQuery = candidateQueries.lastOrNull() ?: candidateQueries.firstOrNull()
            if (bestQuery != null) {
                val streamParam = if (!bestQuery.contains("stream=")) "&stream=1" else ""
                val directUrl = "https://$host/get_video?$bestQuery$streamParam"
                Log.i(TAG, "Streamtape direct extracted (honeypot filter): $directUrl")
                return@withContext directUrl
            }

            // Strategy 3: Fallback to any matched query if no clean candidate was found
            val fallbackMatches = Regex("""(?:get_video|_video)\?([a-zA-Z0-9_=&~-]+)""").findAll(html).toList()
            val fallbackValid = fallbackMatches.map { it.groupValues[1] }.filter { it.contains("id=") && it.contains("token=") && it.contains("expires=") }
            val fallbackQuery = fallbackValid.lastOrNull() ?: fallbackValid.firstOrNull()
            if (fallbackQuery != null) {
                val streamParam = if (!fallbackQuery.contains("stream=")) "&stream=1" else ""
                val directUrl = "https://$host/get_video?$fallbackQuery$streamParam"
                Log.i(TAG, "Streamtape direct extracted (fallback): $directUrl")
                return@withContext directUrl
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error extracting streamtape: ${e.message}")
        }
        null
    }

    /**
     * OWL'S EYE: Dedicated Direct Extractor for VOE / Johnfullwonder (Malaysia PencuriMovie & Mirrors)
     * Reverses VOE's obfuscation pipeline to extract the direct master.m3u8 stream.
     */
    suspend fun extractVoeStream(pageUrl: String, referer: String? = null): ExtractionResult? = withContext(Dispatchers.IO) {
        try {
            var currentUrl = pageUrl
            var html = fetchHtml(currentUrl, referer ?: currentUrl) ?: return@withContext null

            // Detect JS redirect (e.g. voe.sx -> johnfullwonder.com)
            val redirectMatch = Regex("""window\.location(?:\.href|\.replace)\s*=?\s*\(?['"]([^'"]+)['"]\)?|\.location\.href\s*=\s*['"]([^'"]+)['"]""").find(html)
            val redirectTarget = redirectMatch?.groupValues?.get(1)?.ifEmpty { redirectMatch.groupValues.getOrNull(2) }
            if (!redirectTarget.isNullOrEmpty() && redirectTarget.startsWith("http")) {
                currentUrl = redirectTarget
                html = fetchHtml(currentUrl, pageUrl) ?: return@withContext null
            }

            // Also check meta refresh redirect
            val metaRedirect = Regex("""<meta[^>]*http-equiv=["']refresh["'][^>]*content=["'][^"']*url=([^"']+)["']""", RegexOption.IGNORE_CASE).find(html)
            val metaTarget = metaRedirect?.groupValues?.get(1)
            if (!metaTarget.isNullOrEmpty() && metaTarget.startsWith("http")) {
                currentUrl = metaTarget
                html = fetchHtml(currentUrl, pageUrl) ?: return@withContext null
            }

            // Find JSON script containing the obfuscated array
            val jsonScriptRegex = Regex("""<script[^>]*type=["']application/json["'][^>]*>\s*(\[[^<]+\])\s*</script>""")
            val jsonScriptMatches = jsonScriptRegex.findAll(html)
            var extractedStream: String? = null

            for (match in jsonScriptMatches) {
                try {
                    val rawArrayStr = match.groupValues[1]
                    val jsonArray = JSONArray(rawArrayStr)
                    if (jsonArray.length() == 0) continue
                    val rawPayload = jsonArray.getString(0)
                    if (rawPayload.length < 20) continue

                    // 1. ROT13
                    val rot13 = buildString {
                        for (ch in rawPayload) {
                            when (ch) {
                                in 'A'..'Z' -> append(((ch - 'A' + 13) % 26 + 'A'.code).toChar())
                                in 'a'..'z' -> append(((ch - 'a' + 13) % 26 + 'a'.code).toChar())
                                else -> append(ch)
                            }
                        }
                    }

                    // 2. Strip VOE obfuscation markers
                    val markers = listOf("@$", "^^", "~@", "%?", "*~", "!!", "#&", "_")
                    var cleaned = rot13
                    for (m in markers) {
                        cleaned = cleaned.replace(m, "")
                    }

                    // 3. Base64 decode (Latin1)
                    val decoded1 = Base64.decode(cleaned, Base64.DEFAULT)
                    val latin1Str = String(decoded1, Charsets.ISO_8859_1)

                    // 4. Shift character code - 3
                    val shifted = buildString {
                        for (ch in latin1Str) {
                            append((ch.code - 3).toChar())
                        }
                    }

                    // 5. Reverse string
                    val reversed = shifted.reversed()

                    // 6. Base64 decode (UTF-8)
                    val decoded2 = Base64.decode(reversed, Base64.DEFAULT)
                    val finalJsonStr = String(decoded2, Charsets.UTF_8)

                    // 7. Parse VOE config JSON
                    val config = JSONObject(finalJsonStr)
                    val source = config.optString("source").takeIf { it.isNotEmpty() && it.startsWith("http") }
                    val directAccessUrl = config.optString("direct_access_url").takeIf { it.isNotEmpty() && it.startsWith("http") }

                    extractedStream = source ?: directAccessUrl
                    if (extractedStream != null) break
                } catch (e: Exception) {
                    Log.d(TAG, "VOE payload decode attempt error: ${e.message}")
                }
            }

            // Fallback: Check for inline master.m3u8 or hls variables
            if (extractedStream == null) {
                val inlineM3u8 = Regex("""['"]?(?:source|file|hls)['"]?\s*[:=]\s*['"](https?://[^'"]*?master\.m3u8[^'"]*)['"]""").find(html)
                if (inlineM3u8 != null) {
                    extractedStream = inlineM3u8.groupValues[1]
                }
            }

            if (extractedStream != null) {
                Log.i(TAG, "VOE direct stream extracted: $extractedStream (from $currentUrl)")
                return@withContext ExtractionResult(videoUrl = extractedStream, referer = currentUrl)
            }
        } catch (e: Exception) {
            Log.w(TAG, "VOE extraction failed for $pageUrl: ${e.message}")
        }
        null
    }

    /**
     * Unpacks Dean Edwards packed JavaScript: eval(function(p,a,c,k,e,d)...)
     */
    fun unpackDeanEdwards(script: String): String {
        val pattern = Regex("""eval\(function\(p,a,c,k,e,d\)\{.*?\}\('(.*?)',(\d+),(\d+),'(.*?)'\.split\('\|'\)""", setOf(RegexOption.DOT_MATCHES_ALL))
        val match = pattern.find(script) ?: return script
        val payload = match.groupValues[1]
        val a = match.groupValues[2].toIntOrNull() ?: return script
        val c = match.groupValues[3].toIntOrNull() ?: return script
        val syms = match.groupValues[4].split('|')

        fun baseN(num: Int, radix: Int): String {
            val chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
            if (num == 0) return "0"
            var n = num
            val sb = StringBuilder()
            while (n > 0) {
                sb.append(chars[n % radix])
                n /= radix
            }
            return sb.reverse().toString()
        }

        val lookup = HashMap<String, String>(c)
        for (i in 0 until c) {
            val key = baseN(i, a)
            lookup[key] = if (i < syms.size && syms[i].isNotEmpty()) syms[i] else key
        }

        return Regex("""\b\w+\b""").replace(payload) { m ->
            lookup[m.value] ?: m.value
        }
    }

    /**
     * Dedicated direct extractor for Vidhide / Morencius embeds.
     */
    suspend fun extractVidhideStream(pageUrl: String, referer: String? = null): ExtractionResult? = withContext(Dispatchers.IO) {
        try {
            val html = fetchHtml(pageUrl, referer ?: pageUrl) ?: return@withContext null
            val unpacked = unpackDeanEdwards(html)

            val mStream = Regex("""["'](/stream/[^"']+\.m3u8[^"']*)["']""").find(unpacked)
                ?: Regex("""["'](https?://[^"']+/stream/[^"']+\.m3u8[^"']*)["']""").find(unpacked)
                ?: Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""").find(unpacked)

            if (mStream != null) {
                var streamUrl = mStream.groupValues[1]
                if (streamUrl.startsWith("/")) {
                    val host = try { android.net.Uri.parse(pageUrl).host } catch(_: Exception) { "morencius.com" }
                    val scheme = try { android.net.Uri.parse(pageUrl).scheme ?: "https" } catch(_: Exception) { "https" }
                    streamUrl = "$scheme://$host$streamUrl"
                }
                Log.i(TAG, "Vidhide/Morencius direct stream extracted: $streamUrl (from $pageUrl)")
                return@withContext ExtractionResult(videoUrl = streamUrl, referer = pageUrl)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vidhide extraction failed for $pageUrl: ${e.message}")
        }
        null
    }

    /**
     * Dedicated direct extractor for LuluStream / LuluVDO embeds.
     * Unpacks Dean Edwards obfuscated JavaScript to extract direct master.m3u8 stream.
     */
    suspend fun extractLuluStream(pageUrl: String, referer: String? = null): ExtractionResult? = withContext(Dispatchers.IO) {
        try {
            val html = fetchHtml(pageUrl, referer ?: pageUrl) ?: return@withContext null
            val unpacked = unpackDeanEdwards(html)

            val mStream = Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""").find(unpacked)
                ?: Regex("""file:\s*["']([^"']+\.m3u8[^"']*)["']""").find(unpacked)

            if (mStream != null) {
                val streamUrl = mStream.groupValues[1]
                Log.i(TAG, "LuluStream direct m3u8 extracted: $streamUrl (from $pageUrl)")
                return@withContext ExtractionResult(videoUrl = streamUrl, referer = pageUrl)
            }
        } catch (e: Exception) {
            Log.w(TAG, "LuluStream extraction failed for $pageUrl: ${e.message}")
        }
        null
    }

    suspend fun extractVideoUrl(pageUrl: String, depth: Int = 0, referer: String? = null, visited: MutableSet<String> = mutableSetOf()): ExtractionResult? = withContext(Dispatchers.IO) {
        if (depth > 3 || !visited.add(pageUrl) || !pageUrl.startsWith("http") || pageUrl.lowercase().contains("listeamed")) return@withContext null
        if (isDirectVideoUrl(pageUrl)) {
            Log.i(TAG, "extractVideoUrl short-circuit: Direct video URL provided: $pageUrl")
            return@withContext ExtractionResult(pageUrl)
        }
        val actualReferer = referer ?: pageUrl

        // OWL'S EYE: Dedicated Direct Extractor for Streamtape
        if (pageUrl.contains("streamtape.com") || pageUrl.contains("streamtape.to") || pageUrl.contains("streamtape.net")) {
            val direct = extractStreamtape(pageUrl, actualReferer)
            if (direct != null) {
                Log.i(TAG, "Streamtape direct extracted: $direct")
                return@withContext ExtractionResult(direct)
            }
            Log.w(TAG, "Streamtape direct extraction failed or 404 for: $pageUrl")
            return@withContext null
        }

        // OWL'S EYE: Dedicated Direct Extractor for VOE / Johnfullwonder (Malaysia PencuriMovie & Mirrors)
        val lowPage = pageUrl.lowercase()
        if (lowPage.contains("voe.sx") || lowPage.contains("voe") || lowPage.contains("johnfullwonder") || lowPage.contains("cloudwindow")) {
            val voeResult = extractVoeStream(pageUrl, actualReferer)
            if (voeResult != null) {
                Log.i(TAG, "VOE direct extracted: ${voeResult.videoUrl}")
                return@withContext voeResult
            }
            Log.w(TAG, "VOE direct extraction failed or 404 for: $pageUrl")
            return@withContext null
        }

        // OWL'S EYE: Dedicated Direct Extractor for Morencius / Vidhide
        if (lowPage.contains("morencius") || lowPage.contains("vidhide")) {
            val vidhideResult = extractVidhideStream(pageUrl, actualReferer)
            if (vidhideResult != null) {
                Log.i(TAG, "Vidhide/Morencius direct extracted: ${vidhideResult.videoUrl}")
                return@withContext vidhideResult
            }
        }
        
        // OWL'S EYE: Dedicated Direct Extractor for Dailymotion (ExoPlayer Native Playback)
        if (lowPage.contains("dailymotion.com") || lowPage.contains("dai.ly")) {
            val dmResult = extractDailymotionStream(pageUrl)
            if (dmResult != null) {
                Log.i(TAG, "Dailymotion direct HLS extracted: ${dmResult.videoUrl}")
                return@withContext dmResult
            }
            Log.w(TAG, "Dailymotion direct extraction failed or blocked for $pageUrl, falling back to embed URL")
            return@withContext ExtractionResult(pageUrl)
        }

        // OWL'S EYE: Dedicated Direct Extractor for Streamwish / Embedwish / Strwish / Wishonly (Kepala Bergetar & Web Mirrors)
        if (lowPage.contains("streamwish") || lowPage.contains("embedwish") || lowPage.contains("strwish") || 
            lowPage.contains("wishembed") || lowPage.contains("wishonly") || lowPage.contains("wishfast")) {
            val html = fetchHtml(pageUrl, actualReferer)
            if (html != null) {
                val lowHtml = html.lowercase()
                if (lowHtml.contains("no longer available") || lowHtml.contains("expired or has been deleted") ||
                    lowHtml.contains("file was deleted") || lowHtml.contains("video not found") ||
                    lowHtml.contains("file not found")) {
                    Log.w(TAG, "Streamwish/Wishonly file expired/deleted for: $pageUrl")
                    return@withContext null
                }
                val unpacked = unpackDeanEdwards(html)
                val mStream = Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""").find(unpacked)
                    ?: Regex("""file:\s*["']([^"']+\.m3u8[^"']*)["']""").find(unpacked)
                if (mStream != null) {
                    val streamUrl = mStream.groupValues[1]
                    Log.i(TAG, "Streamwish direct m3u8 extracted: $streamUrl")
                    return@withContext ExtractionResult(streamUrl)
                }
            }
            return@withContext null
        }

        // OWL'S EYE: Dedicated Direct Extractor for LuluStream / LuluVDO (Algarvebuzz & Web Mirrors)
        if (lowPage.contains("luluvdo") || lowPage.contains("lulustream")) {
            val luluResult = extractLuluStream(pageUrl, actualReferer)
            if (luluResult != null) {
                Log.i(TAG, "LuluStream direct extracted: ${luluResult.videoUrl}")
                return@withContext luluResult
            }
            Log.w(TAG, "LuluStream direct extraction failed for: $pageUrl, falling back to embed URL")
            return@withContext ExtractionResult(pageUrl)
        }

        // Efficiency: Return immediately if the host is known to require JS/WebView
        if (isJsOnlyHost(pageUrl)) {
            val lowHost = pageUrl.lowercase()
            if (lowHost.contains("waaw") || lowHost.contains("netu") || lowHost.contains("hqq")) {
                // Fast-probe for deleted/purged file on waaw/netu/hqq
                val probeHtml = fetchHtml(pageUrl, actualReferer)
                if (probeHtml != null && (
                    probeHtml.contains("can't find the file", ignoreCase = true) ||
                    probeHtml.contains("cant find the file", ignoreCase = true) ||
                    probeHtml.contains("deleted by the owner", ignoreCase = true) ||
                    probeHtml.contains("copyright violation", ignoreCase = true) ||
                    probeHtml.contains("cant give you what you looking for", ignoreCase = true)
                )) {
                    Log.w(TAG, "waaw/hqq video hoster confirmed file is deleted/purged: $pageUrl")
                    return@withContext null
                }
            } else if (lowHost.contains("hgcloud") || lowHost.contains("hanerix") || lowHost.contains("hglink") || lowHost.contains("vibuxer") || lowHost.contains("audinifer")) {
                // Fast-probe for unprocessed/converting or deleted files on Hgcloud / Hanerix / Hglink
                val probeTarget = if (lowHost.contains("hgcloud.to/e/")) pageUrl.replace("hgcloud.to/e/", "hanerix.com/e/")
                                  else if (lowHost.contains("hglink.to/e/")) pageUrl.replace("hglink.to/e/", "hanerix.com/e/")
                                  else pageUrl
                val probeHtml = fetchHtml(probeTarget, actualReferer)
                if (probeHtml != null && (
                    probeHtml.contains("video is processing", ignoreCase = true) ||
                    probeHtml.contains("conversion stage", ignoreCase = true) ||
                    probeHtml.contains("pending in queue", ignoreCase = true) ||
                    probeHtml.contains("is being converted", ignoreCase = true) ||
                    probeHtml.contains("video is converting", ignoreCase = true) ||
                    probeHtml.contains("over_player_msg", ignoreCase = true) ||
                    probeHtml.contains("can't find the file", ignoreCase = true) ||
                    probeHtml.contains("cant find the file", ignoreCase = true) ||
                    probeHtml.contains("deleted by the owner", ignoreCase = true) ||
                    probeHtml.contains("copyright violation", ignoreCase = true) ||
                    probeHtml.contains("file not found", ignoreCase = true)
                )) {
                    Log.w(TAG, "Hgcloud/Hanerix video hoster confirmed file is unconverted/processing or deleted: $pageUrl")
                    return@withContext null
                }
            }
            Log.d(TAG, "Extraction: Host is JS-Only, returning as-is: $pageUrl")
            return@withContext ExtractionResult(pageUrl)
        }
        val html = fetchHtml(pageUrl, actualReferer) ?: return@withContext null
        
        // 0. Handle JSON response (Common for AJAX)
        if (html.trim().startsWith("{") && html.trim().endsWith("}")) {
            try {
                val json = org.json.JSONObject(html)
                val embed = json.optString("embed_url").ifEmpty { json.optString("url") }.ifEmpty { json.optString("src") }.ifEmpty { json.optString("html") }.ifEmpty { json.optString("data") }
                if (embed.isNotEmpty()) {
                    if (embed.contains("<iframe")) {
                        val m = Regex("src=[\"']([^\"']+)[\"']").find(embed)
                        val target = m?.groupValues?.get(1) ?: embed
                        val sanitized = sanitizeUrl(target, pageUrl)
                        return@withContext extractVideoUrl(sanitized, depth + 1, pageUrl, visited)
                    }
                    val sanitized = sanitizeUrl(embed, pageUrl)
                    if (sanitized.startsWith("http")) return@withContext extractVideoUrl(sanitized, depth + 1, pageUrl, visited)
                }
            } catch (_: Exception) {}
        }

        // 1. Meta Tags (Early Tier)
        val doc = Jsoup.parse(html, pageUrl)
        val metaUrl = doc.select("meta[property=\"og:video:url\"], meta[property=\"og:video:secure_url\"], meta[name=\"twitter:player\"], meta[property=\"og:video\"]").firstOrNull()?.attr("content") ?: ""
        if (metaUrl.isNotEmpty() && isProbablyVideoHost(metaUrl)) {
            val sanitized = sanitizeUrl(metaUrl, pageUrl)
            val lowSanitized = sanitized.lowercase()
            if (lowSanitized.contains("voe.sx") || lowSanitized.contains("voe") || lowSanitized.contains("johnfullwonder")) {
                val voeResult = extractVoeStream(sanitized, pageUrl)
                if (voeResult != null) return@withContext voeResult
            }
            if (isDirectVideoUrl(sanitized)) return@withContext ExtractionResult(sanitized)
            if (isJsOnlyHost(sanitized)) return@withContext ExtractionResult(sanitized)
        }

        // 2. Data Attributes scan (Common for Muvipro/DooPlay buttons)
        doc.select("[data-url], [data-link], [data-src], [data-file], [data-embed]").forEach { el ->
            val data = el.attr("data-url").ifEmpty { el.attr("data-link") }.ifEmpty { el.attr("data-src") }.ifEmpty { el.attr("data-file") }.ifEmpty { el.attr("data-embed") }
            if (data.isNotEmpty() && !data.contains("ads") && isProbablyVideoHost(data)) {
                val sanitized = sanitizeUrl(data, pageUrl)
                val lowSanitized = sanitized.lowercase()
                if (lowSanitized.contains("voe.sx") || lowSanitized.contains("voe") || lowSanitized.contains("johnfullwonder")) {
                    val voeResult = extractVoeStream(sanitized, pageUrl)
                    if (voeResult != null) return@withContext voeResult
                }
                if (lowSanitized.contains("luluvdo") || lowSanitized.contains("lulustream")) {
                    val luluResult = extractLuluStream(sanitized, pageUrl)
                    if (luluResult != null) return@withContext luluResult
                }
                if (isDirectVideoUrl(sanitized)) return@withContext ExtractionResult(sanitized)
                if (isJsOnlyHost(sanitized)) return@withContext ExtractionResult(sanitized)
            }
        }

        // 3. Check for direct video links in raw HTML (Regex tier)
        val directMatch = Regex("""["'](https?://[^"']+\.(?:m3u8|mp4|mkv|webm|ts|mpd|txt)[^"']*)["']""").find(html)
        if (directMatch != null) {
            val url = sanitizeUrl(directMatch.groupValues[1], pageUrl)
            if (isDirectVideoUrl(url)) return@withContext ExtractionResult(url)
        }

        // 4. Parallel Iframe Probing (Turbo Boost)
        val iframes = doc.select("iframe")
        if (iframes.isNotEmpty()) {
            val results = coroutineScope {
                iframes.map { iframe ->
                    async {
                        val src = iframe.attr("abs:src").ifEmpty { iframe.attr("abs:data-src") }.ifEmpty { iframe.attr("src") }
                        if (src.isNotEmpty() && !src.contains("ads") && isProbablyVideoHost(src)) {
                            val sanitized = sanitizeUrl(src, pageUrl)
                            val lowSanitized = sanitized.lowercase()
                            if (lowSanitized.contains("voe.sx") || lowSanitized.contains("voe") || lowSanitized.contains("johnfullwonder")) {
                                val voeResult = extractVoeStream(sanitized, pageUrl)
                                if (voeResult != null) return@async voeResult
                            }
                            if (lowSanitized.contains("luluvdo") || lowSanitized.contains("lulustream")) {
                                val luluResult = extractLuluStream(sanitized, pageUrl)
                                if (luluResult != null) return@async luluResult
                            }
                            if (isDirectVideoUrl(sanitized)) return@async ExtractionResult(sanitized)
                            if (isJsOnlyHost(sanitized)) return@async ExtractionResult(sanitized)
                            
                            return@async extractVideoUrl(sanitized, depth + 1, pageUrl, visited)
                        }
                        null
                    }
                }.awaitAll().filterNotNull()
            }
            results.firstOrNull()?.let { return@withContext it }
        }

        // 5. Scan Scripts for URLs (Deep Tier)
        val scriptCandidates = mutableListOf<String>()
        doc.select("script").forEach { script ->
            val data = script.data()
            if (data.contains("m3u8") || data.contains("mp4") || data.contains("google") || data.contains("hgcloud") || data.contains("abyss") || data.contains("amt") || data.contains("setup") || data.contains("Player(")) {
                
                // VEEV/DIRECT Sniffing (Owl's Eye Special)
                if (data.contains("link") && data.contains("sources")) {
                    Regex("""["']?(?:file|url|src|link)["']?\s*[:=]\s*["'](https?://[^"']+\.(?:m3u8|mp4)[^"']*)["']""").findAll(data).forEach {
                        scriptCandidates.add(sanitizeUrl(it.groupValues[1], pageUrl))
                    }
                }

                // OWL'S EYE: Enhanced Regex for Embed4Me/DM21 pattern
                Regex("""id:\s*["']([^"']+)["'],\s*file:\s*["']([^"']+)["']""").findAll(data).forEach { 
                    scriptCandidates.add(sanitizeUrl(it.groupValues[2], pageUrl))
                }

                Regex("""["']?(?:file|src|url|source|link|hls)["']?\s*[:=]\s*["'](https?://[^"']+)["']""").findAll(data).forEach { 
                    val url = sanitizeUrl(it.groupValues[1], pageUrl)
                    if (!isStaticResource(url)) {
                        scriptCandidates.add(url)
                    }
                }
                
                // Specifically look for JWPlayer setups
                if (data.contains("jwplayer") && data.contains("setup")) {
                     Regex("""sources:\s*\[\s*\{\s*file:\s*["']([^"']+)["']""").findAll(data).forEach {
                         scriptCandidates.add(sanitizeUrl(it.groupValues[1], pageUrl))
                     }
                }
            }
        }
        
        if (scriptCandidates.isNotEmpty()) {
            val sorted = scriptCandidates.distinct().sortedByDescending { it.length }
            val bestDirect = sorted.firstOrNull { isDirectVideoUrl(it) }
            if (bestDirect != null) return@withContext ExtractionResult(bestDirect)
            val bestJs = sorted.firstOrNull { isJsOnlyHost(it) }
            if (bestJs != null) return@withContext ExtractionResult(bestJs)
            return@withContext ExtractionResult(sorted.first())
        }
        
        // 6. Check for direct Video tag (Native Tier)
        val videoTag = doc.select("video source").firstOrNull() ?: doc.select("video").firstOrNull()
        videoTag?.let {
            val src = it.attr("abs:src")
            if (src.isNotEmpty() && isDirectVideoUrl(src)) return@withContext ExtractionResult(src)
        }

        null
    }

    fun isCastFriendlyUrl(url: String): Boolean {
        val low = url.lowercase()
        // Chromecast HATES wrappers (mu=...) or image-extension traps
        if (low.contains(".gif") || low.contains(".jpg") || low.contains(".png") || low.contains(".jpeg")) return false
        if (low.contains("mu=") || low.contains("file=") || low.contains("url=")) {
             // If it has a wrapper param, it's not cast-friendly even if it's a direct stream
             return false
        }
        return isDirectVideoUrl(url)
    }

    fun isStaticResource(url: String): Boolean {
        val low = url.lowercase().substringBefore("?")
        return low.endsWith(".ico") || low.endsWith(".png") || low.endsWith(".jpg") || 
               low.endsWith(".jpeg") || low.endsWith(".gif") || low.endsWith(".svg") ||
               low.endsWith(".js") || low.endsWith(".css") || low.endsWith(".woff") || 
               low.endsWith(".ttf") || low.endsWith(".json")
    }

    fun isIndoStreamAmt(url: String): Boolean {
        val low = url.lowercase()
        if (low.contains("streamtape")) return false
        return low.contains("indostream") || low.contains("amt1.pro") || low.contains("amt2.pro") || 
               low.contains("/amt/") || low.contains(".amt.") || low.contains(".amt/") || 
               low.contains("amtv") || low.contains("amfist") ||
               low.contains("playerp2p") || low.contains("embed4me") || low.contains("upns") ||
               low.contains("pm21") || low.contains("dm21") ||
               (low.contains("amt") && !low.contains("stream"))
    }

    /**
     * Identifies dynamic CDN stream URLs with session tokens or expired timestamps.
     * These URLs must never be saved permanently to video.servers or Room DB, and must
     * be purged if expired.
     */
    fun isEphemeralOrExpiredStream(url: String): Boolean {
        if (url.isBlank()) return false
        val low = url.lowercase()

        // Permanent direct stream sources
        if (low.contains("archive.org") || low.contains("youtube.com") || low.contains("youtu.be") ||
            low.contains("dailymotion.com") || low.contains("dai.ly") || low.contains("bilibili.com")) {
            return false
        }

        // 1. Check for expired epoch timestamp (10-digit number between 1.6B and 2.1B)
        val nowSec = System.currentTimeMillis() / 1000
        val epochMatches = Regex("""\b(1[6-9]\d{8})\b""").findAll(url)
        for (match in epochMatches) {
            val ts = match.groupValues[1].toLongOrNull() ?: continue
            // If the timestamp is in the past by more than 2 hours (7200 sec), it's expired!
            if (ts in 1600000000L..2100000000L && ts < (nowSec - 7200)) {
                return true
            }
        }

        // 2. Check for dynamic ephemeral CDN stream endpoints
        val isEphemeralCdn = low.contains("morencius") || low.contains("vibuxer") ||
                             low.contains("bestcdn") || low.contains("dhcplay") ||
                             low.contains("faststream") || low.contains("ryderjet") ||
                             low.contains("acek-cdn") || low.contains("streambelvona") ||
                             low.contains("delivery-node") || low.contains("stream-node") ||
                             low.contains("upvideo.link") || low.contains("upns.live")

        val isDirectStreamPath = low.contains("/stream/") || low.contains("/hls/") ||
                                 low.contains("/hls2/") || low.contains("/hls3/") ||
                                 low.contains("index-v1-a1.m3u8") || low.contains("master.m3u8") ||
                                 low.contains("?t=") || low.contains("&t=") ||
                                 low.contains("?token=") || low.contains("&token=") ||
                                 low.contains("?expires=") || low.contains("&expires=")

        return isEphemeralCdn && isDirectStreamPath
    }

    fun isDirectVideoUrl(url: String): Boolean {
        if (isStaticResource(url)) return false
        val low = url.lowercase()

        // ARCHIVE.ORG AUTHORITY: Direct media files hosted on Archive.org are authoritative direct streams
        if (low.contains("archive.org") && 
            (low.contains(".mp4") || low.contains(".mkv") || low.contains(".webm") || low.contains(".avi") || low.contains(".m4v") ||
             ((low.contains("/items/") || low.contains("/download/")) && !low.contains(".xml") && !low.contains(".sqlite") && !low.contains(".torrent") && !low.contains(".json") && !low.contains(".txt") && !low.contains(".ia.")))) {
            return true
        }

        // Immediate rejection: Known HTML embed pages are NEVER direct media streams
        val isEmbedPage = (low.contains("/e/") || low.contains("/embed/") || low.contains("/iframe/") || low.contains("/embed-") ||
                           low.contains("streamtape.com") || low.contains("streamtape.to") || low.contains("streamtape.net") ||
                           low.contains("dood") || low.contains("upstream.to") || low.contains("uptostream.com") ||
                           low.contains("mixdrop") || low.contains("filemoon") || low.contains("voe.sx") ||
                           low.contains("johnfullwonder") || low.contains("youtube.com") || low.contains("youtu.be") ||
                           low.contains("dailymotion.com") || low.contains("dai.ly") || low.contains("bilibili.com")) &&
                          !low.contains("get_video?") && !low.contains("tapecontent.net") &&
                          !low.contains(".m3u8") && !low.contains(".mp4") && !low.contains(".mkv") && !low.contains(".webm") && !low.contains("cloudwindow")
        if (isEmbedPage) return false

        // OWL'S EYE: Immediate rejection of junk files and known trackers
        if (low.contains("analytics") || low.contains("pixel") || low.contains("test-videos.co.uk") || 
            low.contains("yandex.ru") || low.contains("google.com/search") || 
            low.contains("/dmca") || low.contains("/upload") || low.contains("/contact") || 
            low.contains("/about") || low.contains("/privacy") || low.contains("/terms") || 
            low.contains("/settings") || low.contains("/register") || low.contains("/login") ||
            low.contains("ketik.live") || low.contains("klikzeus") || low.contains("vingaming") ||
            low.contains("zeus88") || low.contains("klik.top") || low.contains("googletagmanager") ||
            low.contains("histats") || low.contains("/rum") || low.contains("3lift.com") || low.contains("bidgx.com") ||
            low.contains("doubleclick") || low.contains("securepubads") || low.contains("redgarto.com") || low.contains("listeamed")) return false
        
        // Dailymotion internal CDN manifests are protected/token-bound and handled via JS player
        if (low.contains("cdndirector") || low.contains("dmcdn.net") || (low.contains("dailymotion.com") && low.contains("manifest"))) return false

        // OWL'S EYE: VIP Direct Stream Authority (DNA Matching)
        val isVipCdn = (low.contains("hgcloud") || low.contains("hglink") || low.contains("hgcdn") || 
                       low.contains("hanerix") || low.contains("vibuxer") || low.contains("dhcplay") ||
                       isIndoStreamAmt(low) || low.contains("iplayer") ||
                       low.contains("bestcdn") || low.contains("morencius") || low.contains("ryderjet") ||
                       low.contains("haneri") || low.contains("audinifer") || low.contains("vibuxer") ||
                       low.contains("masuk.link") || low.contains("masukin") ||
                       low.contains("abysscdn") || low.contains("bond-stream") || low.contains("stream-node") ||
                       low.contains("upns.live") || low.contains("upvideo.link") || low.contains("pandalur") ||
                       low.contains("iamcdn") || low.contains("bondcdn") || low.contains("abysscdn") ||
                       low.contains("playstream") || low.contains("iplayerhls") || low.contains("veev") || 
                       low.contains("vplay") || low.contains("bestcdn") || low.contains("swhoi") ||
                       low.contains("ryder") || low.contains("dhcplay") || low.contains("morencius")) &&
                       !isStaticResource(url)

        // VIP Path Validation (Gold Standard)
        val path = try { 
            android.net.Uri.parse(url).path ?: "" 
        } catch(_: Throwable) { 
            val slashIdx = url.indexOf("://")
            if (slashIdx != -1) {
                val afterScheme = url.substring(slashIdx + 3)
                val firstSlash = afterScheme.indexOf('/')
                if (firstSlash != -1) afterScheme.substring(firstSlash).substringBefore('?').substringBefore('#') else ""
            } else ""
        }
        // Reject homepages or too short paths as direct streams
        // EXCEPT for known API/stream endpoints that might have short paths
        if ((path == "" || path == "/" || path.length < 5) && !isIndoStreamAmt(low) && !low.contains("haneri")) return false 

        // If it's from a VIP CDN and has some indicators, trust it as a stream
        // CRITICAL: Gateways like Playstream/Abyss MUST be handled via JS-Only Tier
        // All hosts that serve HTML embed pages (not raw video) must be excluded here
        val isGateway = low.contains("playstream") || low.contains("abyss") || low.contains("veev") || low.contains("dood") ||
                        low.contains("hanerix") || low.contains("vibuxer") || low.contains("audinifer") ||
                        low.contains("haneri") || low.contains("masuk") || low.contains("indostream") ||
                        low.contains("swhoi") || low.contains("morencius") || low.contains("bestcdn") ||
                        low.contains("hgplayer") || low.contains("vplay") || low.contains("vhost") ||
                        low.contains("iplayerhls") || low.contains("bond")
        if (isVipCdn && !isGateway && (low.contains("?") || low.contains("token") || low.contains("key") || 
                         low.contains("expires") || low.contains("/stream/") ||
                         low.length > 50)) {
            return true
        }

        val hasVideoIndicator = (low.contains(".m3u8") || low.contains(".mp4") || low.contains(".mkv") || low.contains(".webm") || low.contains(".ts") || low.contains(".mpd") ||
                     low.contains("/hls/") || low.contains("/hls3/") || low.contains("/stream/") || low.endsWith("/hl") || low.contains("/hl?") ||
                     low.contains("/p2p/") || low.contains("/live/") || low.contains("master.m3u8") || low.contains("playlist.m3u8") ||
                     low.contains("get_video?") || low.contains("tapecontent.net") ||
                     (low.contains(".txt") && (isVipCdn || low.contains("/hls") || low.contains("master") || low.contains("index"))) ||
                     (low.contains("playlist.m3u8") && (low.contains("abyss") || low.contains("bond") || low.contains("cdn")))) &&
                     !low.contains("blank.mp4") && !low.contains("empty.mp4")
                     
        val isKnownStreamHost = low.contains("delivery-node") || low.contains("stream-node") || low.contains("vhost") || 
                                low.contains("vplay") || low.contains("bond-stream") || low.contains("abysscdn") || 
                                low.contains("ryderjet") || low.contains("bestcdn") || low.contains("upvideo") || 
                                low.contains("upns") || low.contains("playstream") || low.contains("iplayerhls") ||
                                low.contains("morencius")

        val isDirectByHeuristics = (isKnownStreamHost || (isVipCdn && (low.contains("node") || low.contains("cdn") || low.contains("stream")))) &&
               !low.endsWith(".html") && !low.endsWith(".php") && !low.contains(".html?") && !low.contains(".php?")
               
        if (hasVideoIndicator) return true
        if (isJsOnlyHost(url)) return false
        return isDirectByHeuristics
    }

    fun isJsOnlyHost(url: String): Boolean {
        if (isStaticResource(url)) return false
        val low = url.lowercase()

        // OWL'S EYE: Direct streams (.m3u8, .mp4, .mkv, .webm, .ts, .mpd, get_video) are NEVER JS-Only web pages
        if (low.contains(".m3u8") || low.contains(".mp4") || low.contains(".mkv") || low.contains(".webm") || low.contains(".ts") || low.contains(".mpd") ||
            low.contains("get_video?") || low.contains("tapecontent.net") || low.contains("/stream/")) return false

        // Dailymotion, YouTube, and Bilibili web embeds/pages are JS-Only web players (when not direct m3u8 streams)
        if (low.contains("dailymotion.com") || low.contains("dai.ly") ||
            low.contains("bilibili.com") || low.contains("bilibili.tv") ||
            low.contains("youtube.com") || low.contains("youtu.be")) return true

        // OWL'S EYE: Only force WebView for known interactive mirrors, NOT the gateway pages
        if (low.contains("player=") || low.contains("mirror=") || low.contains("action=")) return false
        
        if (low.contains("zeus") || low.contains("klik") || low.contains("vingaming") || low.contains("pingaming") || 
            low.contains("voe.sx") || low.contains("voe") || low.contains("johnfullwonder") || low.contains("cloudwindow")) return false
        
        val jsOnlyHosts = setOf(
            "youtube", "youtu.be", "dailymotion", "dai.ly", "bilibili",
            "streamtape", "hgplayer", "rebeccasciencestreet", "ryderjet", "ghbrisk", "ghb", "duvidun", "hanerix", 
            "fujihide", "dood", "embedo", "mixdrop", "filemoon", "dsvplay", 
            "latestmoviereview", "playerp2p", "upstream", "vidplay", "mycloud", "vidhide", "vidsrc", "viatrix", "breakplan", 
            "hexload", "vstream", "player.fun", "movearnpre", "movienu", "movielite",
            "embed4me", "amtv", "amfist", "highload", "vizcloud", "vplay", 
            "vhost", "dstream", "meadowpath", "wellnessspace", 
            "playstream", "iplayerhls", "veev", "harmonixinnovationlab", "bryantenunder",
            "playmogo", "digitalidentity", "sunrisevalleycreative",
            "p2p", "pm21", "go.player", "llvpn", "callistanise", "writingtoolsonline", 
            "indostream", "swhoi", "embedpyrox", "pyrox", "garylarge", 
            "huntrex", "bestcdn", "faststream", "morencius", "abyss", "bond", "iamcdn",
            "hgcloud", "hglink", "indostream", "amt", "iplayer", "haneri", "audinifer", "vibuxer", "masuk",
            "veev", "dood", "upns", "d-s.io", "swishsrv", "swish", "swishembed",
            "waaw", "netu", "hqq", "vkspeed", "luluvdo", "lulustream"
        )
        val cleanUrl = low.substringBefore("?").substringBefore("#")
        return jsOnlyHosts.any { cleanUrl.contains(it) }
    }

    fun isProbablyVideoHost(url: String): Boolean {
        val low = url.lowercase()
        val isYt = low.contains("youtube") || low.contains("youtu.be")
        if (isYt && !low.contains("/embed/")) return false
        if (low.contains("vimeo.com") || low.contains("listeamed")) return false
        if (low.contains("zeus") || low.contains("klik") || low.contains("vingaming") || low.contains("pingaming") || 
            low.contains("/dmca") || low.contains("/upload") || low.contains("/contact") || 
            low.contains("/about") || low.contains("/privacy") || low.contains("/terms") || 
            low.contains("/settings") || low.contains("/register") || low.contains("/login") ||
            low.contains("ketik.live") || low.contains("zeus88") || low.contains("klik.top") || low.contains("vingaming") || low.contains("pingaming") ||
            low.contains("googleapis.com") || low.contains("imasdk")) return false
        
        return isJsOnlyHost(low) || low.contains(".m3u8") || low.contains(".mp4") || low.contains(".mkv") || low.contains(".webm") || low.contains(".ts") || 
               low.contains("/e/") || low.contains("/v/") || low.contains("/embed/") || 
               low.contains("/stream/") || low.contains("/hls/") || low.contains("mirror") ||
               low.contains("player") || low.contains("swhoi") || low.contains("playstream") ||
               low.contains("pyrox") || low.contains("embedpyrox") || low.contains("amt") ||
               low.contains("haneri") || low.contains("audinifer") || low.contains("vibuxer") ||
               low.contains("morencius") || low.contains("bestcdn") ||
               low.contains("luluvdo") || low.contains("lulustream") || low.contains("tnmr.org") || low.contains("lulucdn") ||
               low.contains("waaw") || low.contains("netu") || low.contains("hqq") || low.contains("vkspeed")
    }

    suspend fun fetchVideosBySection(path: String, page: Int, count: Int): List<Video> = withContext(Dispatchers.IO) {
        if (path.contains("country/viet-nam", ignoreCase = true) || path.contains("country/vietnam", ignoreCase = true)) {
            return@withContext fetchVietnamUnified(page, count)
        }
        if (path.contains("country/malaysia", ignoreCase = true)) {
            return@withContext fetchMalaysiaUnified(page, count)
        }
        if (path.contains("p-ramlee", ignoreCase = true) || path.contains("FilemP.ramlee", ignoreCase = true)) {
            val all = fetchArchivePramleeVideos()
            val start = (page - 1) * count
            if (start >= all.size) return@withContext emptyList()
            return@withContext all.drop(start).take(count)
        }

        val results = mutableListOf<Video>()
        val seenIds = mutableSetOf<String>()
        var currentPage = page
        val maxPages = page + 6 // Deeper scan for genre categories that have fewer items per page
        var consecutiveEmptyPages = 0 // Track pages with zero NEW items
        
        while (results.size < count && currentPage < maxPages) {
            var url = if (path.startsWith("http")) {
                val effectivePath = migrateUrlToBase(path)
                if (currentPage > 1) "${effectivePath.removeSuffix("/")}/page/$currentPage/" else effectivePath
            } else {
                val normalizedPath = normalizePath(path).removeSuffix("/") // Remove trailing slash for page concat
                if (currentPage > 1) "$BASE_URL$normalizedPath/page/$currentPage/" else "$BASE_URL$normalizedPath/"
            }
            Log.d(TAG, "Fetching Page $currentPage for $path: $url")
            var html = fetchHtml(url)

            if (html.isNullOrEmpty() && currentPage == 1) {
                if (path.contains("pencurimovie") || path.contains("country/malaysia")) {
                    Log.w(TAG, "PencuriMovie category failed on $url. Probing fallback domains...")
                    val liveDomain = probePencuriDomain()
                    if (liveDomain != null) {
                        url = "$liveDomain/country/malaysia/"
                        Log.i(TAG, "Retrying category with live domain: $url")
                        html = fetchHtml(url)
                    }
                } else {
                    Log.w(TAG, "Category $path failed on $url. Probing for live Duta domain...")
                    val liveDomain = probeForNewDomain()
                    if (liveDomain != null && !url.startsWith(liveDomain)) {
                        val normalizedPath = normalizePath(path).removeSuffix("/")
                        url = if (currentPage > 1) "$liveDomain$normalizedPath/page/$currentPage/" else "$liveDomain$normalizedPath/"
                        Log.i(TAG, "Retrying category with live domain: $url")
                        html = fetchHtml(url)
                    }
                }
            }

            if (html == null) break
            val isSeriesCategory = path.contains("serial-tv", ignoreCase = true) || path.contains("/tv/", ignoreCase = true) || path.contains("/series/", ignoreCase = true)
            val scrapedRaw = scrapeVideosFromHtml(html, url)
            val scraped = if (isSeriesCategory) scrapedRaw.map { it.copy(isSeries = true) } else scrapedRaw
            val newItems = scraped.count { !seenIds.contains(it.id) }
            Log.d(TAG, "Page $currentPage found ${scraped.size} items ($newItems new). Total unique so far: ${results.size + newItems}")
            if (scraped.isEmpty()) break
            
            scraped.forEach { video ->
                if (seenIds.add(video.id)) {
                    results.add(video)
                }
            }
            
            // Only break if we got zero new unique items for 2 consecutive pages
            // This prevents premature exit when pages have overlapping content
            if (newItems == 0) {
                consecutiveEmptyPages++
                if (consecutiveEmptyPages >= 2) break
            } else {
                consecutiveEmptyPages = 0
            }
            
            currentPage++
        }
        Log.i(TAG, "Fetch complete for $path. Final count: ${results.size}")
        results.take(count)
    }

    /**
     * Unified Hybrid Malaysia Catalog: Concurrently fetches from both DutaMovie and PencuriMovie,
     * deduplicating by title while prioritizing DutaMovie's high-uptime streams and interleaving
     * PencuriMovie's long-tail exclusives.
     */
    private suspend fun fetchMalaysiaUnified(page: Int, count: Int): List<Video> = withContext(Dispatchers.IO) {
        val dutaDeferred = async {
            val results = mutableListOf<Video>()
            val seen = mutableSetOf<String>()
            val pagesToFetch = if (count > 40) 3 else 1
            for (offset in 0 until pagesToFetch) {
                val currPage = page + offset
                val url = if (currPage > 1) "$BASE_URL/country/malaysia/page/$currPage/" else "$BASE_URL/country/malaysia/"
                try {
                    var html = fetchHtml(url)
                    if (html.isNullOrEmpty() && currPage == 1) {
                        val liveDomain = probeForNewDomain()
                        if (liveDomain != null) {
                            val retryUrl = if (currPage > 1) "$liveDomain/country/malaysia/page/$currPage/" else "$liveDomain/country/malaysia/"
                            html = fetchHtml(retryUrl)
                        }
                    }
                    if (html == null) break
                    val scraped = scrapeVideosFromHtml(html, url)
                    if (scraped.isEmpty()) break
                    scraped.forEach { if (seen.add(it.id)) results.add(it) }
                } catch (e: Exception) {
                    Log.w(TAG, "Duta Malaysia fetch failed on p$currPage: ${e.message}")
                    break
                }
            }
            results
        }

        val pencuriDeferred = async {
            val results = mutableListOf<Video>()
            val seen = mutableSetOf<String>()
            val pagesToFetch = if (count > 40) 2 else 1
            val pencuriBase = getPencuriBaseUrl()
            for (offset in 0 until pagesToFetch) {
                val currPage = page + offset
                var url = if (currPage > 1) "$pencuriBase/country/malaysia/page/$currPage/" else "$pencuriBase/country/malaysia/"
                try {
                    var html = fetchHtml(url)
                    if (html.isNullOrEmpty() && currPage == 1) {
                        val liveDomain = probePencuriDomain()
                        if (liveDomain != null) {
                            url = "$liveDomain/country/malaysia/"
                            html = fetchHtml(url)
                        }
                    }
                    if (html == null) break
                    val scraped = scrapeVideosFromHtml(html, url)
                    if (scraped.isEmpty()) break
                    scraped.forEach { if (seen.add(it.id)) results.add(it) }
                } catch (e: Exception) {
                    Log.w(TAG, "Pencuri Malaysia fetch failed on p$currPage: ${e.message}")
                    break
                }
            }
            results
        }

        val dutaVideos = dutaDeferred.await()
        val pencuriVideos = pencuriDeferred.await()
        Log.i(TAG, "Malaysia Unified Fetch (Page $page, Count $count): Duta=${dutaVideos.size}, Pencuri=${pencuriVideos.size}")

        val totalAvailable = dutaVideos.size + pencuriVideos.size
        val merged = mergeAndInterleave(dutaVideos, pencuriVideos, totalAvailable)
        val sorted = sortVideosByNewestRelease(merged)
        sorted.take(count)
    }

    /**
     * Unified Hybrid Vietnam Catalog: Concurrently fetches from both DutaMovie (/country/viet-nam/)
     * and PencuriMovie (/country/vietnam/), deduplicating and interleaving.
     */
    private suspend fun fetchVietnamUnified(page: Int, count: Int): List<Video> = withContext(Dispatchers.IO) {
        val dutaDeferred = async {
            val results = mutableListOf<Video>()
            val seen = mutableSetOf<String>()
            val pagesToFetch = if (count > 40) 3 else 1
            for (offset in 0 until pagesToFetch) {
                val currPage = page + offset
                val url = if (currPage > 1) "$BASE_URL/country/viet-nam/page/$currPage/" else "$BASE_URL/country/viet-nam/"
                try {
                    val html = fetchHtml(url) ?: break
                    val scraped = scrapeVideosFromHtml(html, url)
                    if (scraped.isEmpty()) break
                    scraped.forEach { if (seen.add(it.id)) results.add(it) }
                } catch (e: Exception) {
                    Log.w(TAG, "Duta Vietnam fetch failed on p$currPage: ${e.message}")
                    break
                }
            }
            results
        }

        val pencuriDeferred = async {
            val results = mutableListOf<Video>()
            val seen = mutableSetOf<String>()
            val pagesToFetch = if (count > 40) 2 else 1
            val pencuriBase = getPencuriBaseUrl()
            for (offset in 0 until pagesToFetch) {
                val currPage = page + offset
                var url = if (currPage > 1) "$pencuriBase/country/vietnam/page/$currPage/" else "$pencuriBase/country/vietnam/"
                try {
                    var html = fetchHtml(url)
                    if (html.isNullOrEmpty() && currPage == 1) {
                        val liveDomain = probePencuriDomain()
                        if (liveDomain != null) {
                            url = "$liveDomain/country/vietnam/"
                            html = fetchHtml(url)
                        }
                    }
                    if (html == null) break
                    val scraped = scrapeVideosFromHtml(html, url)
                    if (scraped.isEmpty()) break
                    scraped.forEach { if (seen.add(it.id)) results.add(it) }
                } catch (e: Exception) {
                    Log.w(TAG, "Pencuri Vietnam fetch failed on p$currPage: ${e.message}")
                    break
                }
            }
            results
        }

        val dutaVideos = dutaDeferred.await()
        val pencuriVideos = pencuriDeferred.await()
        Log.i(TAG, "Vietnam Unified Fetch (Page $page, Count $count): Duta=${dutaVideos.size}, Pencuri=${pencuriVideos.size}")

        mergeAndInterleave(dutaVideos, pencuriVideos, count)
    }

    fun normalizeForDedup(title: String): String =
        title.lowercase()
            .replace(Regex("""\((?:19|20)\d{2}\)"""), "")
            .replace(Regex("[^a-z0-9]"), "")
            .trim()

    /**
     * Cross-searches alternative streaming aggregators (DutaMovie <-> PencuriMovie <-> Archive.org)
     * to find fresh live mirrors when the primary movie's mirrors are expired, 404'd, or deleted.
     */
    suspend fun findAlternativeSources(video: Video): List<VideoServer> = withContext(Dispatchers.IO) {
        val targetNorm = normalizeForDedup(video.title)
        if (targetNorm.isEmpty()) return@withContext emptyList()

        val isPm = isPencuriMovie(videoId = video.id, videoUrl = video.videoUrl)
        val isKb = isKepalaBergetar(videoId = video.id, videoUrl = video.videoUrl)
        val partners = if (isPm) listOf(getBaseUrl() to "Duta")
                       else if (isKb) listOf(getPencuriBaseUrl() to "Pencuri", getBaseUrl() to "Duta")
                       else listOf(getPencuriBaseUrl() to "Pencuri")

        Log.i(TAG, "Finding alternative sources for '${video.title}' (isPm=$isPm, isKb=$isKb)...")

        val distinctQueries = buildAlternativeSearchQueries(video.title)
        val altServers = mutableListOf<VideoServer>()
        val existingUrls = video.servers.map { it.url.trimEnd('/') }.toSet()

        coroutineScope {
            val partnerDeferreds = partners.map { (partnerDomain, partnerTag) ->
                async(Dispatchers.IO) {
                    val partnerServers = mutableListOf<VideoServer>()
                    for (q in distinctQueries) {
                        var searchResults = searchDomain(partnerDomain, q, startPage = 1, maxCount = 20)
                        if (searchResults.isEmpty() && partnerTag == "Pencuri") {
                            // If PencuriMovie returned nothing, probe active domain and retry
                            probePencuriDomain()?.let { liveDomain ->
                                searchResults = searchDomain(liveDomain, q, startPage = 1, maxCount = 20)
                            }
                        }

                        val matched = searchResults.find { isCrossProviderMovieMatch(video, it) }

                        if (matched != null) {
                            Log.i(TAG, "Alternative source matched on $partnerTag: '${matched.title}' (${matched.videoUrl})")
                            val details = fetchVideoDetails(matched.videoUrl)
                            if (details != null) {
                                val serversToExamine = if (video.isSeries == true && details.servers.isEmpty() && details.episodes.isNotEmpty()) {
                                    val epNum = Regex("""\b(?:episod[e]?|eps|ep)\s*(\d+)\b""", RegexOption.IGNORE_CASE).find(video.title)?.groupValues?.get(1)
                                        ?: Regex("""\b(?:episod[e]?|eps|ep)\s*(\d+)\b""", RegexOption.IGNORE_CASE).find(video.videoUrl)?.groupValues?.get(1)
                                    val matchedEp = if (epNum != null) {
                                        details.episodes.find { 
                                            val pNum = Regex("""\b(?:episod[e]?|eps|ep)\s*(\d+)\b""", RegexOption.IGNORE_CASE).find(it.name)?.groupValues?.get(1)
                                                ?: Regex("""\b(?:episod[e]?|eps|ep)\s*(\d+)\b""", RegexOption.IGNORE_CASE).find(it.url)?.groupValues?.get(1)
                                            pNum == epNum
                                        }
                                    } else details.episodes.firstOrNull()

                                    if (matchedEp != null) {
                                        fetchVideoDetails(matchedEp.url)?.servers ?: emptyList()
                                    } else emptyList()
                                } else {
                                    details.servers
                                }

                                if (serversToExamine.isNotEmpty()) {
                                    val newServers = serversToExamine.filter { s ->
                                        val cleanUrl = s.url.trimEnd('/')
                                        !existingUrls.contains(cleanUrl)
                                    }.map { s ->
                                        val cleanName = if (s.name.contains(partnerTag, ignoreCase = true)) s.name else "${s.name} ($partnerTag)"
                                        VideoServer(name = cleanName, url = s.url)
                                    }
                                    if (newServers.isNotEmpty()) {
                                        Log.i(TAG, "Discovered ${newServers.size} alternative mirrors from $partnerTag for '${video.title}'")
                                        partnerServers.addAll(newServers)
                                        break
                                    }
                                }
                            }
                        }
                    }
                    partnerServers
                }
            }
            val results = partnerDeferreds.awaitAll()
            results.forEach { servers ->
                altServers.addAll(servers)
            }
        }

        // Full movie fallbacks: Only applicable for standalone movies, not TV series episodes
        if (video.isSeries != true) {
            // Also check classic P. Ramlee Archive.org catalogue if applicable
            if (altServers.isEmpty() || !video.videoUrl.contains("archive.org")) {
                try {
                    val classicMatch = fetchArchivePramleeVideos().find {
                        normalizeForDedup(it.title) == targetNorm
                    }
                    if (classicMatch != null && video.servers.none { it.url.contains("archive.org") }) {
                        Log.i(TAG, "Discovered Archive.org classic mirror for '${video.title}'")
                        if (classicMatch.servers.isNotEmpty()) {
                            altServers.addAll(classicMatch.servers)
                        } else {
                            altServers.add(VideoServer(name = "Archive.org HD (Fast Direct)", url = classicMatch.videoUrl))
                        }
                    }
                } catch (_: Exception) {}
            }

            // Search YouTube Full Movie for modern Malaysian movies and titles whose filehosts are dead
            if (altServers.isEmpty() || altServers.all { s -> video.servers.any { it.url == s.url } }) {
                try {
                    val ytServers = searchYouTubeFullMovie(video.title, video.date)
                    if (ytServers.isNotEmpty()) {
                        Log.i(TAG, "Discovered ${ytServers.size} YouTube Full Movie mirrors for '${video.title}'")
                        altServers.addAll(ytServers)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "YouTube full movie search error: ${e.message}")
                }
            }

            // Search Bilibili Full Movie for anime, asian films, and full movies (ad-free & globally accessible)
            if (altServers.isEmpty() || altServers.all { s -> video.servers.any { it.url == s.url } }) {
                try {
                    val biliServers = searchBilibiliFullMovie(video.title, video.date)
                    if (biliServers.isNotEmpty()) {
                        Log.i(TAG, "Discovered ${biliServers.size} Bilibili Full Movie mirrors for '${video.title}'")
                        altServers.addAll(biliServers)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Bilibili full movie search error: ${e.message}")
                }
            }

            // Search Dailymotion Full Movie for user uploads, malay/indo films, and full movies
            if (altServers.size < 5) {
                try {
                    val dmServers = searchDailymotionFullMovie(video.title, video.date)
                    if (dmServers.isNotEmpty()) {
                        Log.i(TAG, "Discovered ${dmServers.size} Dailymotion Full Movie mirrors for '${video.title}'")
                        altServers.addAll(dmServers)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Dailymotion full movie search error: ${e.message}")
                }
            }
        }

        altServers.distinctBy { it.url }
    }

    fun buildAlternativeSearchQueries(rawTitle: String): List<String> {
        val rawClean = cleanTitle(rawTitle)
        val titleWithoutYear = rawClean
            .replace(Regex("""(?i)\b(?:episod[e]?|eps|ep)\s*\d+\b.*"""), "")
            .replace(Regex("""\((?:19|20)\d{2}\)"""), "")
            .replace(":", " ")
            .replace("-", " ")
            .replace("'", "")
            .replace("’", "")
            .replace(Regex("""\s+"""), " ")
            .trim()
        val cleanWords = titleWithoutYear.split(Regex("""\s+""")).filter { it.isNotBlank() }

        val queries = mutableListOf<String>()
        if (cleanWords.isNotEmpty()) {
            // 1. Full clean title (e.g. "Hantu Van Sewa 2")
            queries.add(cleanWords.joinToString(" "))

            // 2. Base title without sequel number (e.g. "Hantu Van Sewa" for "Hantu Van Sewa 2")
            if (cleanWords.size >= 3 && cleanWords.last().toIntOrNull() != null) {
                queries.add(cleanWords.dropLast(1).joinToString(" "))
            }

            // 3. 2-word prefix (e.g. "Hantu Van" or "Mat Kilau")
            if (cleanWords.size >= 2) {
                queries.add(cleanWords.take(2).joinToString(" "))
            }

            // 4. Single-word query ONLY if original title only has 1 or 2 words (e.g. "Munafik")
            if (cleanWords.size <= 2 && cleanWords.first().length >= 4) {
                queries.add(cleanWords.first())
            }
        }
        return queries.distinct()
    }

    data class MovieTitleMeta(
        val year: Int?,
        val sequel: Int,
        val baseTokens: List<String>
    )

    fun sanitizeServerNameToTitle(serverName: String): String {
        return serverName
            .replace(Regex("""^(?:dailymotion|youtube|bilibili|archive(?:\.org)?)\s*(?:\[\s*\d+\s*m\s*\])?\s*(?:\([^\)]*\))?\s*(?:hd)?\s*[:\-]\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\((?:full\s*movie|versi\s*warna|hitam\s*putih|fast\s*direct|mirror\s*\d*|duta|pencuri|hq\s*engsub)\)\s*""", RegexOption.IGNORE_CASE), "")
            .removeSuffix("...")
            .removeSuffix(".")
            .trim()
    }

    fun parseMovieTitleMeta(rawTitle: String, rawDate: String? = null): MovieTitleMeta {
        val sanitizedTitle = sanitizeServerNameToTitle(rawTitle)
        val yearFromTitle = Regex("""\b(19\d{2}|20\d{2})\b""").find(sanitizedTitle)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("""\b(19\d{2}|20\d{2})\b""").find(rawTitle)?.groupValues?.get(1)?.toIntOrNull()
        val yearFromDate = if (!rawDate.isNullOrEmpty()) Regex("""\b(19\d{2}|20\d{2})\b""").find(rawDate)?.groupValues?.get(1)?.toIntOrNull() else null
        val year = yearFromTitle ?: yearFromDate

        var clean = sanitizedTitle.lowercase()
            .replace(Regex("""\b(?:dailymotion|youtube|bilibili|archive)\b"""), " ")
            .replace(Regex("""\b\d+m\b"""), " ")
            .replace("permalink ke:", " ")
            .replace("nonton", " ")
            .replace(Regex("""sub\s+indo(?:nesia)?"""), " ")
            .replace(Regex("""subtitle\s+indo(?:nesia)?"""), " ")
            .replace(Regex("""dutamovie21|dutamovie|layarkaca21|lk21|itoshii"""), " ")
            .replace(Regex("""\b(?:web-?dl|web-?rip|1080p|720p|480p|360p|hdcam|cam-?rip|bluray|blu-?ray|hdrip)\b"""), " ")
            .replace(Regex("""\b(?:full\s*movie|full\s*film|lengkap|terbaru|official|phim|tonton|watch)\b"""), " ")
            .replace(Regex("""[\(\)\[\]\{\}\-_,:\.'\"\|\\\/]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        var sequel = 1
        val romanMap = mapOf(
            "ii" to 2, "iii" to 3, "iv" to 4, "v" to 5,
            "vi" to 6, "vii" to 7, "viii" to 8, "ix" to 9, "x" to 10
        )
        val sequelMatch = Regex("""\b(?:part|chapter|season|vol|volume)?\s*(2|3|4|5|6|7|8|9|ii|iii|iv|v|vi|vii|viii|ix|x)\b""").find(clean)
        if (sequelMatch != null) {
            val token = sequelMatch.groupValues[1]
            sequel = romanMap[token] ?: token.toIntOrNull() ?: 1
        }

        val stopWords = setOf(
            "the", "a", "an", "and", "or", "by", "of", "in", "on", "to", "for", "with", "from",
            "part", "chapter", "season", "vol", "volume", "movie", "film", "malay", "malaysian",
            "hd", "dubbed", "eng", "sub", "source", "director", "sutradara", "pemeran",
            "dailymotion", "youtube", "bilibili", "archive", "server", "phim", "tonton", "watch"
        )
        val tokens = clean.split(Regex("""\s+"""))
        val baseTokens = mutableListOf<String>()
        for (t in tokens) {
            if (t in stopWords) continue
            if (t.matches(Regex("""^(19\d{2}|20\d{2})$"""))) continue
            if (t in setOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "i", "ii", "iii", "iv", "v")) continue
            if (t.length >= 2) baseTokens.add(t)
        }

        return MovieTitleMeta(year = year, sequel = sequel, baseTokens = baseTokens)
    }

    fun isCrossProviderMovieMatch(target: Video, candidate: Video): Boolean {
        val targetMeta = parseMovieTitleMeta(target.title, target.date)
        val candMeta = parseMovieTitleMeta(candidate.title, candidate.date)

        // 1. Sequel matching: MUST strictly match (e.g. Part 1 vs Part 2)
        if (targetMeta.sequel != candMeta.sequel) return false

        // Extract year from title, date, OR url slug (e.g. /the-guardian-2021/)
        val targetYear = targetMeta.year ?: Regex("""\b(19\d{2}|20\d{2})\b""").find(target.videoUrl)?.groupValues?.get(1)?.toIntOrNull()
        val candYear = candMeta.year ?: Regex("""\b(19\d{2}|20\d{2})\b""").find(candidate.videoUrl)?.groupValues?.get(1)?.toIntOrNull()

        // 2. Strict Year matching:
        if (targetYear != null && candYear != null) {
            if (Math.abs(targetYear - candYear) > 1) return false
        } else if (targetYear != null && candYear == null) {
            // Target specifies a release year, but candidate has no year anywhere.
            // For short/generic titles (<= 2 base tokens), reject candidate without year
            if (targetMeta.baseTokens.size <= 2) return false
        }

        // 3. Base tokens matching
        val targetTokens = targetMeta.baseTokens
        val candTokens = candMeta.baseTokens
        if (targetTokens.isEmpty() || candTokens.isEmpty()) return false

        // Quick equality check on normalized dedup string
        val targetDedup = normalizeForDedup(target.title)
        val candDedup = normalizeForDedup(candidate.title)
        if (targetDedup == candDedup) {
            return true
        }

        // Single token titles (e.g. "Guardian") must match token exactly (not plural "Guardians")
        if (targetTokens.size == 1) {
            return candTokens.size == 1 && candTokens[0] == targetTokens[0]
        }

        // Subtitle / sub-heading prefix match (e.g. "Ma Da: The Drowning Spirit" vs "Ma Da")
        if (targetTokens.size >= 2 && candTokens.size >= 2) {
            val minLen = minOf(targetTokens.size, candTokens.size)
            if (targetTokens.take(minLen) == candTokens.take(minLen)) {
                return true
            }
        }

        // Key shared root word with identical release year (e.g. "Tarung: Unforgiven (2026)" vs "Tarung (2026)")
        if (targetTokens.isNotEmpty() && candTokens.isNotEmpty() &&
            targetTokens.first() == candTokens.first() &&
            targetTokens.first().length >= 4 &&
            targetYear != null && candYear != null && targetYear == candYear) {
            return true
        }

        // Token overlap check for multi-token titles
        val candSet = candTokens.toSet()
        val matchedCount = targetTokens.count { candSet.contains(it) }
        val requiredMatches = if (targetTokens.size <= 2) targetTokens.size else targetTokens.size - 1
        return matchedCount >= requiredMatches && Math.abs(targetTokens.size - candTokens.size) <= 1
    }

    fun isYouTubeMovieMatch(targetMeta: MovieTitleMeta, candidateTitle: String): Boolean {
        // 0. Strict reject for promotional, preview, trailer, or review clips (unless target title itself has it)
        if (!targetMeta.baseTokens.contains("trailer") &&
            candidateTitle.contains(Regex("""\b(?:trailer|teaser|review|reaction|behind\s*the\s*scenes)\b""", RegexOption.IGNORE_CASE))) {
            return false
        }

        val candMeta = parseMovieTitleMeta(candidateTitle, null)

        // 1. Sequel matching: MUST strictly match (e.g. Part 1 vs Part 2)
        if (targetMeta.sequel != candMeta.sequel) return false

        // 2. Year check: If candidate specifies a 4-digit year, it must match within +/- 1 year
        val extraTokens = candMeta.baseTokens.size - targetMeta.baseTokens.size
        if (targetMeta.year != null) {
            val y = targetMeta.year
            val candHasTargetYear = candidateTitle.contains(y.toString()) || 
                                   candidateTitle.contains((y - 1).toString()) || 
                                   candidateTitle.contains((y + 1).toString())
            if (candMeta.year != null) {
                if (Math.abs(targetMeta.year - candMeta.year) > 1) return false
            } else {
                // Target has a year, candidate does NOT specify a year.
                if (!candHasTargetYear) {
                    // For standalone single-token generic titles without sequel (e.g. "The Guardian" -> ["guardian"]),
                    // candidate title MUST contain the target year (or +/- 1) to prevent false positives
                    if (targetMeta.sequel == 1 && targetMeta.baseTokens.size == 1) return false

                    // If candidate does not start with target tokens and has extra tokens, year is required
                    val startsWithTarget = candMeta.baseTokens.take(targetMeta.baseTokens.size) == targetMeta.baseTokens
                    if (!startsWithTarget && extraTokens > 0) return false
                }
            }
        }

        // 3. Base tokens: Target base tokens must be present in candidate clean title
        if (targetMeta.baseTokens.isEmpty()) return false
        val candCleanLower = candMeta.baseTokens.toSet()
        val matchedCount = targetMeta.baseTokens.count { bt ->
            candCleanLower.any { it == bt || (it.length >= 4 && bt.length >= 4 && (it.removeSuffix("s") == bt.removeSuffix("s"))) }
        }
        val requiredMatches = if (targetMeta.baseTokens.size <= 2) targetMeta.baseTokens.size else targetMeta.baseTokens.size - 1
        if (matchedCount < requiredMatches) return false

        // 4. Excess tokens check: Prevent matching clickbait titles, compilations, or unrelated movies
        // e.g. "Trilogy of Terror ... VOODOO DOLL COMES ALIVE ... STUDENT VIDEOS PROFESSOR AND PAYS"
        if (extraTokens > 4) return false
        if (candMeta.baseTokens.isNotEmpty()) {
            val precision = matchedCount.toDouble() / candMeta.baseTokens.size.toDouble()
            if (precision < 0.25) return false
        }

        return true
    }

    /**
     * Searches YouTube for verified full-length movie uploads (>= 45 mins)
     * as an authoritative alternative source when provider filehosts are dead.
     */
    suspend fun searchYouTubeFullMovie(title: String, releaseDate: String? = null): List<VideoServer> = withContext(Dispatchers.IO) {
        val results = mutableListOf<VideoServer>()
        try {
            val targetMeta = parseMovieTitleMeta(title, releaseDate)
            val baseQuery = targetMeta.baseTokens.joinToString(" ")
            val sequelQuery = if (targetMeta.sequel > 1) "${targetMeta.sequel}" else ""
            val yearQuery = targetMeta.year?.toString() ?: ""
            val query = "$baseQuery $sequelQuery $yearQuery full movie".replace(Regex("""\s+"""), " ").trim()
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://www.youtube.com/results?search_query=$encoded"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9,ms;q=0.8")
                .build()

            val html = NetworkConfig.okHttpClient.newCall(request).execute().use { response ->
                response.body?.string()
            } ?: return@withContext emptyList()

            val regex = Regex("""var ytInitialData\s*=\s*(\{.*?\});</script>""")
            val match = regex.find(html) ?: Regex("""ytInitialData\s*=\s*(\{.*?\});</script>""").find(html)
            if (match == null) return@withContext emptyList()

            val jsonStr = match.groupValues[1]
            val root = JSONObject(jsonStr)
            val sections = root.optJSONObject("contents")
                ?.optJSONObject("twoColumnSearchResultsRenderer")
                ?.optJSONObject("primaryContents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return@withContext emptyList()

            for (s in 0 until sections.length()) {
                val section = sections.optJSONObject(s) ?: continue
                val items = section.optJSONObject("itemSectionRenderer")?.optJSONArray("contents") ?: continue
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val v = item.optJSONObject("videoRenderer") ?: continue
                    val vid = v.optString("videoId")
                    if (vid.isEmpty()) continue

                    val titleRuns = v.optJSONObject("title")?.optJSONArray("runs")
                    val vTitle = if (titleRuns != null && titleRuns.length() > 0) {
                        titleRuns.getJSONObject(0).optString("text", "")
                    } else ""

                    val durText = v.optJSONObject("lengthText")?.optString("simpleText", "") ?: ""

                    // Duration check: must be >= 45 minutes
                    val isFullMovie = if (durText.isNotEmpty()) {
                        val parts = durText.split(":")
                        if (parts.size >= 3) {
                            true // H:MM:SS is >= 1 hour
                        } else if (parts.size == 2) {
                            (parts[0].toIntOrNull() ?: 0) >= 45
                        } else false
                    } else false

                    if (!isFullMovie) continue

                    // Strict sequel, year, and title similarity check
                    if (isYouTubeMovieMatch(targetMeta, vTitle)) {
                        val shortTitle = if (vTitle.length > 80) vTitle.take(77) + "..." else vTitle
                        val embedUrl = "https://www.youtube-nocookie.com/embed/$vid?autoplay=1&controls=0&playsinline=1&rel=0&enablejsapi=1&iv_load_policy=3&fs=0"
                        results.add(VideoServer(name = "YouTube HD: $shortTitle", url = embedUrl))
                        if (results.size >= 3) break
                    }
                }
                if (results.isNotEmpty()) break
            }
        } catch (e: Exception) {
            Log.w(TAG, "YouTube full movie search failed for '$title': ${e.message}")
        }
        results
    }

    /**
     * Extracts video ID from any standard YouTube URL (watch, embed, youtu.be, or raw ID).
     */
    fun extractYouTubeId(url: String): String? {
        val clean = url.trim()
        return when {
            clean.contains("v=") -> clean.substringAfter("v=").substringBefore("&").substringBefore("?")
            clean.contains("youtu.be/") -> clean.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
            clean.contains("/embed/") -> clean.substringAfter("/embed/").substringBefore("?").substringBefore("&")
            clean.contains("/watch/") -> clean.substringAfter("/watch/").substringBefore("?").substringBefore("&")
            clean.length in 10..12 && !clean.contains("/") -> clean
            else -> null
        }
    }

    /**
     * Parses Bilibili duration strings into total seconds.
     * Supports formats: "HH:MM:SS" (e.g. "1:24:50"), "MM:SS" (e.g. "104:30" or "45:12"), or "SS".
     */
    fun parseBilibiliDurationSeconds(durationStr: String): Int {
        val parts = durationStr.split(":").mapNotNull { it.trim().toIntOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0]
            else -> 0
        }
    }

    /**
     * Extracts Bilibili BVID from standard URLs (e.g., https://www.bilibili.com/video/BV1xx411c7mD,
     * https://player.bilibili.com/player.html?bvid=BV1xx411c7mD, or raw BV string).
     */
    fun extractBilibiliBvid(url: String): String? {
        val clean = url.trim()
        return when {
            clean.contains("bvid=") -> clean.substringAfter("bvid=").substringBefore("&").substringBefore("?")
            clean.contains("/video/") -> clean.substringAfter("/video/").substringBefore("/").substringBefore("?").substringBefore("&")
            clean.startsWith("BV", ignoreCase = true) && clean.length in 10..15 -> clean
            else -> null
        }
    }

    /**
     * Searches Bilibili for full movies and anime matching the movie title and year.
     * UGC videos on Bilibili have no pre-roll/mid-roll commercial interruptions and are globally accessible.
     */
    suspend fun searchBilibiliFullMovie(title: String, releaseDate: String? = null): List<VideoServer> = withContext(Dispatchers.IO) {
        val results = mutableListOf<VideoServer>()
        try {
            val targetMeta = parseMovieTitleMeta(title, releaseDate)
            val baseQuery = targetMeta.baseTokens.joinToString(" ")
            val sequelQuery = if (targetMeta.sequel > 1) "${targetMeta.sequel}" else ""
            val yearQuery = targetMeta.year?.toString() ?: ""
            val query = "$baseQuery $sequelQuery $yearQuery full movie".replace(Regex("""\s+"""), " ").trim()
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://api.bilibili.com/x/web-interface/search/type?search_type=video&keyword=$encoded"

            val buvid = java.util.UUID.randomUUID().toString() + "infoc"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", NetworkConfig.SHARED_USER_AGENT)
                .header("Referer", "https://www.bilibili.com/")
                .header("Cookie", "buvid3=$buvid; b_nut=1700000000; CURRENT_FNVAL=4048")
                .header("X-Forwarded-For", "220.181.38.148")
                .header("Client-IP", "220.181.38.148")
                .build()

            val jsonStr = NetworkConfig.okHttpClient.newCall(request).execute().use { response ->
                response.body?.string()
            } ?: return@withContext emptyList()

            val root = JSONObject(jsonStr)
            if (root.optInt("code", -1) != 0) return@withContext emptyList()

            val items = root.optJSONObject("data")?.optJSONArray("result") ?: return@withContext emptyList()
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val bvid = item.optString("bvid")
                if (bvid.isEmpty()) continue

                // Exclude paid VIP-only content
                if (item.optInt("is_pay", 0) != 0) continue

                val rawTitle = item.optString("title", "").replace(Regex("""<[^>]+>"""), "").trim()
                val durStr = item.optString("duration", "")
                val durSecs = parseBilibiliDurationSeconds(durStr)

                // Full movie duration threshold: at least 40 minutes (2400 seconds)
                if (durSecs < 2400) continue

                // Strict sequel, year, and title matching check
                if (isYouTubeMovieMatch(targetMeta, rawTitle)) {
                    val shortTitle = if (rawTitle.length > 80) rawTitle.take(77) + "..." else rawTitle
                    val embedUrl = "https://player.bilibili.com/player.html?bvid=$bvid&page=1&as_wide=1&high_quality=1&danmaku=0"
                    results.add(VideoServer(name = "Bilibili HD: $shortTitle", url = embedUrl))
                    if (results.size >= 3) break
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bilibili full movie search failed for '$title': ${e.message}")
        }
        results
    }

    /**
     * Extracts Dailymotion video ID from standard URLs (e.g., https://www.dailymotion.com/video/x990bio,
     * https://dai.ly/x990bio, https://www.dailymotion.com/embed/video/x990bio, or raw ID).
     */
    fun extractDailymotionId(url: String): String? {
        val clean = url.trim()
        return when {
            clean.contains("/embed/video/") -> clean.substringAfter("/embed/video/").substringBefore("?").substringBefore("&").substringBefore("/").substringBefore(".")
            clean.contains("/video/") -> clean.substringAfter("/video/").substringBefore("?").substringBefore("&").substringBefore("/").substringBefore(".")
            clean.contains("dai.ly/") -> clean.substringAfter("dai.ly/").substringBefore("?").substringBefore("&").substringBefore("/").substringBefore(".")
            Regex("""^x[a-z0-9]{5,10}$""", RegexOption.IGNORE_CASE).matches(clean) -> clean
            else -> null
        }
    }

    /**
     * Extracts direct HLS (.m3u8) adaptive stream from Dailymotion's player metadata API.
     * Enables native ExoPlayer playback with zero WebView CPU/GPU overhead, eliminating stutter on TV devices.
     */
    suspend fun extractDailymotionStream(pageUrl: String): ExtractionResult? = withContext(Dispatchers.IO) {
        try {
            val dmId = extractDailymotionId(pageUrl) ?: return@withContext null
            val metaUrl = "https://www.dailymotion.com/player/metadata/video/$dmId"
            val request = okhttp3.Request.Builder()
                .url(metaUrl)
                .header("User-Agent", NetworkConfig.SHARED_USER_AGENT)
                .header("Referer", "https://www.dailymotion.com/")
                .build()
            val response = NetworkConfig.permissiveOkHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            val bodyStr = response.body?.string() ?: return@withContext null
            val json = org.json.JSONObject(bodyStr)
            if (json.has("error") && !json.isNull("error")) return@withContext null
            val qualities = json.optJSONObject("qualities") ?: return@withContext null
            var m3u8Url: String? = null
            val autoArray = qualities.optJSONArray("auto")
            if (autoArray != null && autoArray.length() > 0) {
                m3u8Url = autoArray.getJSONObject(0).optString("url")
            }
            if (m3u8Url.isNullOrEmpty()) {
                val tiers = listOf("1080", "720", "480", "380", "240")
                for (tier in tiers) {
                    val arr = qualities.optJSONArray(tier)
                    if (arr != null && arr.length() > 0) {
                        m3u8Url = arr.getJSONObject(0).optString("url")
                        if (!m3u8Url.isNullOrEmpty()) break
                    }
                }
            }
            if (!m3u8Url.isNullOrEmpty()) {
                Log.i(TAG, "Dailymotion direct HLS stream resolved: $m3u8Url (ID: $dmId)")
                return@withContext ExtractionResult(
                    videoUrl = m3u8Url,
                    referer = "https://www.dailymotion.com/"
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "extractDailymotionStream error for $pageUrl: ${e.message}")
        }
        null
    }

    /**
     * Searches Dailymotion for full movies matching the movie title and year.
     * Uses Dailymotion's open video search API with longer_than=40 filter.
     */
    suspend fun searchDailymotionFullMovie(title: String, releaseDate: String? = null): List<VideoServer> = withContext(Dispatchers.IO) {
        val results = mutableListOf<VideoServer>()
        try {
            val targetMeta = parseMovieTitleMeta(title, releaseDate)
            val baseQuery = targetMeta.baseTokens.joinToString(" ")
            val sequelQuery = if (targetMeta.sequel > 1) "${targetMeta.sequel}" else ""
            val yearQuery = targetMeta.year?.toString() ?: ""
            val query = "$baseQuery $sequelQuery $yearQuery full movie".replace(Regex("""\s+"""), " ").trim()
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://api.dailymotion.com/videos?search=$encoded&fields=id,title,duration,thumbnail_url&longer_than=40&limit=20"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", NetworkConfig.SHARED_USER_AGENT)
                .header("Referer", "https://www.dailymotion.com/")
                .build()

            val jsonStr = NetworkConfig.okHttpClient.newCall(request).execute().use { response ->
                response.body?.string()
            } ?: return@withContext emptyList()

            val root = JSONObject(jsonStr)
            val items = root.optJSONArray("list") ?: return@withContext emptyList()
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val dmId = item.optString("id")
                if (dmId.isEmpty()) continue

                val rawTitle = item.optString("title", "").trim()
                val durSecs = item.optInt("duration", 0)

                // Full movie duration threshold: at least 40 minutes (2400 seconds)
                if (durSecs < 2400) continue

                // Strict sequel, year, and title matching check
                if (isYouTubeMovieMatch(targetMeta, rawTitle)) {
                    val cleanTitle = rawTitle.replace(Regex("""<[^>]+>"""), "").trim()
                    val shortTitle = if (cleanTitle.length > 80) cleanTitle.take(77) + "..." else cleanTitle
                    val durMins = durSecs / 60
                    val embedUrl = "https://www.dailymotion.com/embed/video/$dmId?autoplay=1&mute=0&ui-logo=0&sharing-enable=0&ui-start-screen-info=0"
                    results.add(VideoServer(name = "Dailymotion [${durMins}m]: $shortTitle", url = embedUrl))
                    if (results.size >= 3) break
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Dailymotion full movie search failed for '$title': ${e.message}")
        }
        results
    }

    /**
     * Searches YouTube for full movies and returns them as Video items for search results.
     */
    suspend fun searchYouTubeAsVideos(query: String): List<Video> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Video>()
        try {
            val targetMeta = parseMovieTitleMeta(query)
            val baseQuery = targetMeta.baseTokens.joinToString(" ")
            val sequelQuery = if (targetMeta.sequel > 1) "${targetMeta.sequel}" else ""
            val yearQuery = targetMeta.year?.toString() ?: ""
            val qWithMovie = "$baseQuery $sequelQuery $yearQuery full movie".replace(Regex("""\s+"""), " ").trim()
            val encoded = java.net.URLEncoder.encode(qWithMovie, "UTF-8")
            val url = "https://www.youtube.com/results?search_query=$encoded"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9,ms;q=0.8")
                .build()

            val html = NetworkConfig.okHttpClient.newCall(request).execute().use { response ->
                response.body?.string()
            } ?: return@withContext emptyList()

            val regex = Regex("""var ytInitialData\s*=\s*(\{.*?\});</script>""")
            val match = regex.find(html) ?: Regex("""ytInitialData\s*=\s*(\{.*?\});</script>""").find(html)
            if (match == null) return@withContext emptyList()

            val jsonStr = match.groupValues[1]
            val root = JSONObject(jsonStr)
            val sections = root.optJSONObject("contents")
                ?.optJSONObject("twoColumnSearchResultsRenderer")
                ?.optJSONObject("primaryContents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return@withContext emptyList()

            for (s in 0 until sections.length()) {
                val section = sections.optJSONObject(s) ?: continue
                val items = section.optJSONObject("itemSectionRenderer")?.optJSONArray("contents") ?: continue
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val v = item.optJSONObject("videoRenderer") ?: continue
                    val vid = v.optString("videoId")
                    if (vid.isEmpty()) continue

                    val titleRuns = v.optJSONObject("title")?.optJSONArray("runs")
                    val vTitle = if (titleRuns != null && titleRuns.length() > 0) {
                        titleRuns.getJSONObject(0).optString("text", "")
                    } else ""

                    val durText = v.optJSONObject("lengthText")?.optString("simpleText", "") ?: ""

                    // Duration check: full movies must be >= 40 minutes (or H:MM:SS)
                    val isFullMovie = if (durText.isNotEmpty()) {
                        val parts = durText.split(":")
                        if (parts.size >= 3) {
                            true // H:MM:SS
                        } else if (parts.size == 2) {
                            (parts[0].toIntOrNull() ?: 0) >= 40
                        } else false
                    } else false

                    if (!isFullMovie) continue

                    if (isYouTubeMovieMatch(targetMeta, vTitle)) {
                        val cleanTitle = vTitle.replace(Regex("""(?i)\b(full\s*movie|hd\s*movie|film\s*lengkap|sub\s*indo)\b"""), "").trim()
                        val thumbUrl = "https://i.ytimg.com/vi/$vid/hqdefault.jpg"
                        val embedUrl = "https://www.youtube-nocookie.com/embed/$vid?autoplay=1&controls=0&playsinline=1&rel=0&enablejsapi=1&iv_load_policy=3&fs=0"
                        results.add(
                            Video(
                                id = "yt_$vid",
                                title = "[YouTube] $cleanTitle",
                                thumbnailUrl = thumbUrl,
                                backdropUrl = thumbUrl,
                                videoUrl = embedUrl,
                                duration = durText,
                                quality = "YouTube HD",
                                description = "Full Movie available on YouTube: $vTitle",
                                servers = listOf(VideoServer(name = "YouTube HD: $cleanTitle", url = embedUrl)),
                                isSeries = false
                            )
                        )
                        if (results.size >= 10) break
                    }
                }
                if (results.size >= 10) break
            }
        } catch (e: Exception) {
            Log.w(TAG, "YouTube fallback search failed: ${e.message}")
        }
        results
    }

    /**
     * Searches Bilibili for full movies and returns them as Video items for search results.
     */
    suspend fun searchBilibiliAsVideos(query: String): List<Video> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Video>()
        try {
            val targetMeta = parseMovieTitleMeta(query)
            val baseQuery = targetMeta.baseTokens.joinToString(" ")
            val sequelQuery = if (targetMeta.sequel > 1) "${targetMeta.sequel}" else ""
            val yearQuery = targetMeta.year?.toString() ?: ""
            val qWithMovie = "$baseQuery $sequelQuery $yearQuery full movie".replace(Regex("""\s+"""), " ").trim()
            val encoded = java.net.URLEncoder.encode(qWithMovie, "UTF-8")
            val url = "https://api.bilibili.com/x/web-interface/search/type?search_type=video&keyword=$encoded"

            val buvid = java.util.UUID.randomUUID().toString() + "infoc"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", NetworkConfig.SHARED_USER_AGENT)
                .header("Referer", "https://www.bilibili.com/")
                .header("Cookie", "buvid3=$buvid; b_nut=1700000000; CURRENT_FNVAL=4048")
                .header("X-Forwarded-For", "220.181.38.148")
                .header("Client-IP", "220.181.38.148")
                .build()

            val jsonStr = NetworkConfig.okHttpClient.newCall(request).execute().use { response ->
                response.body?.string()
            } ?: return@withContext emptyList()

            val root = JSONObject(jsonStr)
            if (root.optInt("code", -1) != 0) return@withContext emptyList()

            val items = root.optJSONObject("data")?.optJSONArray("result") ?: return@withContext emptyList()

            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val bvid = item.optString("bvid")
                if (bvid.isEmpty()) continue
                if (item.optInt("is_pay", 0) != 0) continue

                val rawTitle = item.optString("title", "").replace(Regex("""<[^>]+>"""), "").trim()
                val durStr = item.optString("duration", "")
                val durSecs = parseBilibiliDurationSeconds(durStr)

                // Must be >= 40 minutes (2400 seconds)
                if (durSecs < 2400) continue

                if (isYouTubeMovieMatch(targetMeta, rawTitle)) {
                    var pic = item.optString("pic", "")
                    if (pic.startsWith("//")) pic = "https:$pic"
                    val cleanTitle = rawTitle.replace(Regex("""(?i)\b(full\s*movie|hd\s*movie|film\s*lengkap|sub\s*indo)\b"""), "").trim()
                    val embedUrl = "https://player.bilibili.com/player.html?bvid=$bvid&page=1&as_wide=1&high_quality=1&danmaku=0"
                    val durMins = durSecs / 60
                    val posterUrl = if (pic.isNotEmpty()) {
                        if (pic.contains("@")) pic.substringBefore("@") + "@360w_540h_1c.webp" else "$pic@360w_540h_1c.webp"
                    } else ""
                    results.add(
                        Video(
                            id = "bili_$bvid",
                            title = "[Bilibili] $cleanTitle",
                            thumbnailUrl = posterUrl.ifEmpty { pic },
                            backdropUrl = pic,
                            videoUrl = embedUrl,
                            duration = "$durMins min",
                            quality = "Bilibili HD",
                            description = "Full Movie available on Bilibili: $rawTitle",
                            servers = listOf(VideoServer(name = "Bilibili HD: $cleanTitle", url = embedUrl)),
                            isSeries = false
                        )
                    )
                    if (results.size >= 10) break
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bilibili fallback search failed: ${e.message}")
        }
        results
    }

    /**
     * Searches Dailymotion for full movies and returns them as Video items for search results.
     */
    suspend fun searchDailymotionAsVideos(query: String): List<Video> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Video>()
        try {
            val targetMeta = parseMovieTitleMeta(query)
            val baseQuery = targetMeta.baseTokens.joinToString(" ")
            val sequelQuery = if (targetMeta.sequel > 1) "${targetMeta.sequel}" else ""
            val yearQuery = targetMeta.year?.toString() ?: ""
            val qWithMovie = "$baseQuery $sequelQuery $yearQuery full movie".replace(Regex("""\s+"""), " ").trim()
            val encoded = java.net.URLEncoder.encode(qWithMovie, "UTF-8")
            val url = "https://api.dailymotion.com/videos?search=$encoded&fields=id,title,duration,thumbnail_720_url,thumbnail_url,thumbnail_480_url&longer_than=40&limit=15"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", NetworkConfig.SHARED_USER_AGENT)
                .header("Referer", "https://www.dailymotion.com/")
                .build()

            val jsonStr = NetworkConfig.okHttpClient.newCall(request).execute().use { response ->
                response.body?.string()
            } ?: return@withContext emptyList()

            val root = JSONObject(jsonStr)
            val items = root.optJSONArray("list") ?: return@withContext emptyList()

            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val dmId = item.optString("id")
                if (dmId.isEmpty()) continue

                val rawTitle = item.optString("title", "").trim()
                val durSecs = item.optInt("duration", 0)
                if (durSecs < 2400) continue

                if (isYouTubeMovieMatch(targetMeta, rawTitle)) {
                    val cleanTitle = rawTitle.replace(Regex("""<[^>]+>"""), "")
                        .replace(Regex("""(?i)\b(full\s*movie|hd\s*movie|film\s*lengkap|sub\s*indo)\b"""), "").trim()
                    val thumbUrl = item.optString("thumbnail_720_url").ifEmpty {
                        item.optString("thumbnail_url").ifEmpty {
                            item.optString("thumbnail_480_url", "")
                        }
                    }
                    val durMins = durSecs / 60
                    val embedUrl = "https://www.dailymotion.com/embed/video/$dmId?autoplay=1&mute=0&ui-logo=0&sharing-enable=0&ui-start-screen-info=0"
                    results.add(
                        Video(
                            id = "dm_$dmId",
                            title = "[Dailymotion] $cleanTitle",
                            thumbnailUrl = thumbUrl,
                            backdropUrl = thumbUrl,
                            videoUrl = embedUrl,
                            duration = "$durMins min",
                            quality = "Dailymotion",
                            description = "Full Movie available on Dailymotion: $rawTitle",
                            servers = listOf(VideoServer(name = "Dailymotion: $cleanTitle", url = embedUrl)),
                            isSeries = false
                        )
                    )
                    if (results.size >= 10) break
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Dailymotion fallback search failed: ${e.message}")
        }
        results
    }

    /**
     * Concurrently searches YouTube, Bilibili, and Dailymotion when titles are not found
     * on DutaMovie or PencuriMovie.
     */
    suspend fun searchExternalFallbackVideos(query: String): List<Video> = coroutineScope {
        val ytDeferred = async { searchYouTubeAsVideos(query) }
        val biliDeferred = async { searchBilibiliAsVideos(query) }
        val dmDeferred = async { searchDailymotionAsVideos(query) }

        val yt = ytDeferred.await()
        val bili = biliDeferred.await()
        val dm = dmDeferred.await()

        val combined = mutableListOf<Video>()
        val maxLen = maxOf(yt.size, bili.size, dm.size)
        for (i in 0 until maxLen) {
            if (i < yt.size) combined.add(yt[i])
            if (i < bili.size) combined.add(bili[i])
            if (i < dm.size) combined.add(dm[i])
        }
        combined.distinctBy { it.id }
    }

    /**
     * Interleaves DutaMovie and PencuriMovie results, prioritizing DutaMovie's active streams
     * whenever titles overlap, and enriching with unique long-tail PencuriMovie items.
     */
    fun mergeAndInterleave(dutaResults: List<Video>, pencuriResults: List<Video>, count: Int): List<Video> {
        val merged = mutableListOf<Video>()
        val seenTitles = mutableSetOf<String>()
        val seenIds = mutableSetOf<String>()

        val dutaClean = mutableListOf<Video>()
        for (video in dutaResults) {
            val normTitle = normalizeForDedup(video.title)
            if (seenTitles.add(normTitle) && seenIds.add(video.id)) {
                dutaClean.add(video)
            }
        }

        val pencuriClean = mutableListOf<Video>()
        for (video in pencuriResults) {
            val normTitle = normalizeForDedup(video.title)
            if (seenTitles.add(normTitle) && seenIds.add(video.id)) {
                pencuriClean.add(video)
            }
        }

        val maxLen = maxOf(dutaClean.size, pencuriClean.size)
        for (i in 0 until maxLen) {
            if (i < dutaClean.size) merged.add(dutaClean[i])
            if (i < pencuriClean.size) merged.add(pencuriClean[i])
        }

        if (merged.isEmpty()) {
            return (dutaResults + pencuriResults).distinctBy { it.id }.take(count)
        }

        return merged.take(count)
    }

    /**
     * Extracts the 4-digit release year from a movie title or date.
     * Prioritizes bracketed release years e.g. "(2026)", then video.date, then trailing 4-digit year.
     */
    fun extractReleaseYear(title: String, date: String? = null): Int {
        val parenMatches = Regex("""[\(\[]\s*(19\d{2}|20\d{2})\s*[\)\]]""").findAll(title)
        val lastParen = parenMatches.lastOrNull()
        if (lastParen != null) {
            lastParen.groupValues[1].toIntOrNull()?.let { return it }
        }
        if (!date.isNullOrBlank()) {
            Regex("""\b(19\d{2}|20\d{2})\b""").find(date)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        }
        val allYears = Regex("""\b(19\d{2}|20\d{2})\b""").findAll(title)
        val lastYear = allYears.lastOrNull()
        if (lastYear != null) {
            lastYear.groupValues[1].toIntOrNull()?.let { return it }
        }
        return 0
    }

    /**
     * Sorts movies so that the newest titles (e.g. 2026, 2025, 2024...) are placed in front.
     * Uses Kotlin's stable sort to preserve the upload freshness order within the same release year.
     */
    fun sortVideosByNewestRelease(videos: List<Video>): List<Video> {
        return videos.sortedWith(
            compareByDescending<Video> { extractReleaseYear(it.title, it.date) }
        )
    }

    val PRAMLEE_POSTERS = mapOf(
        "abu-hassan-penchuri" to "https://image.tmdb.org/t/p/w500/lLsTOstlADKLrtfWPZTJeOeBADu.jpg",
        "ahmad-albab" to "https://image.tmdb.org/t/p/w500/ehhqSBEQiS3loFGvXXest8o6gXx.jpg",
        "bujang-lapok" to "https://image.tmdb.org/t/p/w500/hiLcooHtEwgNkjv7fOlzxzMWPdP.jpg",
        "do-re-mi" to "https://image.tmdb.org/t/p/w500/wW3VbZ5qKy4C2eSPO4oY6y8VPmu.jpg",
        "enam-jahanam" to "https://image.tmdb.org/t/p/w500/9APHudzCbMPq787LwBC5iBKeEsd.jpg",
        "hang-tuah" to "https://image.tmdb.org/t/p/w500/6x59DjnPIUf0ymsXBIlvQo0EkDW.jpg",
        "kanchan-tirana" to "https://image.tmdb.org/t/p/w500/ckadmVtQQvaW2VAE8sV61TILG89.jpg",
        "keluarga69" to "https://image.tmdb.org/t/p/w500/jpzzvWFj2IpKtyX4zjJqidZw1eO.jpg",
        "keluarga-69" to "https://image.tmdb.org/t/p/w500/jpzzvWFj2IpKtyX4zjJqidZw1eO.jpg",
        "labu-dan-labi" to "https://image.tmdb.org/t/p/w500/nwY3aU6Iq9DIf1EEwsX0Nz3Kbpo.jpg",
        "laksamana-do-re-mi" to "https://image.tmdb.org/t/p/w500/sMxKmrBkClc7PpXWrgovKoaIiUG.jpg",
        "masam-masam-manis" to "https://image.tmdb.org/t/p/w500/xbr7r8lkBWQtirXjAw0xD3oFmiN.jpg",
        "musang-berjanggut" to "https://image.tmdb.org/t/p/w500/hpUYZMwIt72L2FJM6kwwsgnJt1K.jpg",
        "nasib-do-re-mi" to "https://image.tmdb.org/t/p/w500/6tiXdCbhzQltSz41Vf4FnMEPdJ0.jpg",
        "nasib-si-labu-labi" to "https://image.tmdb.org/t/p/w500/kzupjs6c7wOtqd9TAZXRz1hM5jB.jpg",
        "putus-sudah-kasih-sayang" to "https://image.tmdb.org/t/p/w500/6ztYIbZsad860V5G2Jfl0w2FXCt.jpg",
        "putus-sudah-kaseh-sayang" to "https://image.tmdb.org/t/p/w500/6ztYIbZsad860V5G2Jfl0w2FXCt.jpg",
        "ragam-p-ramlee" to "https://image.tmdb.org/t/p/w500/kqI0rQ04pjJ0qhQhTmgQih7P2Ce.jpg",
        "semerah-padi" to "https://image.tmdb.org/t/p/w500/eztSlII8upGsAXLk0dnxx72fqhq.jpg",
        "sesudah-subuh" to "https://image.tmdb.org/t/p/w500/AuX0gFXGFEV4Pcwama3gzXJTyb7.jpg",
        "ali-baba-bujang-lapok" to "https://image.tmdb.org/t/p/w500/dYSuQvfc9RDzzvtvj3iIVditp9e.jpg",
        "alibababujanglapok" to "https://image.tmdb.org/t/p/w500/dYSuQvfc9RDzzvtvj3iIVditp9e.jpg",
        "nujum-pak-belalang" to "https://image.tmdb.org/t/p/w500/rcwroUjJfTDFVMrX8DRXB6kuuKJ.jpg",
        "nujumpakbelalang" to "https://image.tmdb.org/t/p/w500/rcwroUjJfTDFVMrX8DRXB6kuuKJ.jpg",
        "sumpah-orang-minyak" to "https://image.tmdb.org/t/p/w500/wvrMRUoR6X2AKD9LBZTQGryx6Nc.jpg",
        "sumpahorangminyak" to "https://image.tmdb.org/t/p/w500/wvrMRUoR6X2AKD9LBZTQGryx6Nc.jpg",
        "tiga-abdul" to "https://image.tmdb.org/t/p/w500/8X9YyahJBDBQ3wjTEN6HubQN6Cg.jpg",
        "tigaabdul" to "https://image.tmdb.org/t/p/w500/8X9YyahJBDBQ3wjTEN6HubQN6Cg.jpg",
        "seniman-bujang-lapok" to "https://upload.wikimedia.org/wikipedia/ms/a/a7/SenimanBujangLapok.jpg",
        "senimanbujanglapok" to "https://upload.wikimedia.org/wikipedia/ms/a/a7/SenimanBujangLapok.jpg",
        "seniman-bujang-lapuk" to "https://upload.wikimedia.org/wikipedia/ms/a/a7/SenimanBujangLapok.jpg",
        "senimanbujanglapuk" to "https://upload.wikimedia.org/wikipedia/ms/a/a7/SenimanBujangLapok.jpg",
        "pendekar-bujang-lapok" to "https://img.youtube.com/vi/1Cij_LQNVHU/hqdefault.jpg",
        "pendekarbujanglapok" to "https://img.youtube.com/vi/1Cij_LQNVHU/hqdefault.jpg",
        "pendekar-bujang-lapuk" to "https://img.youtube.com/vi/1Cij_LQNVHU/hqdefault.jpg",
        "pendekarbujanglapuk" to "https://img.youtube.com/vi/1Cij_LQNVHU/hqdefault.jpg",
        "madu-tiga" to "https://upload.wikimedia.org/wikipedia/ms/0/0d/MaduTiga.jpg",
        "madutiga" to "https://upload.wikimedia.org/wikipedia/ms/0/0d/MaduTiga.jpg",
        "ibu-mertua-ku" to "https://upload.wikimedia.org/wikipedia/ms/f/f4/PosterIbuMertuaKu.jpg",
        "ibumertuaku" to "https://upload.wikimedia.org/wikipedia/ms/f/f4/PosterIbuMertuaKu.jpg",
        "ibu-mertuaku" to "https://upload.wikimedia.org/wikipedia/ms/f/f4/PosterIbuMertuaKu.jpg",
        "anak-ku-sazali" to "https://upload.wikimedia.org/wikipedia/ms/5/5c/Anakku_sazali_01.jpg",
        "anakku-sazali" to "https://upload.wikimedia.org/wikipedia/ms/5/5c/Anakku_sazali_01.jpg",
        "anakkusazali" to "https://upload.wikimedia.org/wikipedia/ms/5/5c/Anakku_sazali_01.jpg",
        "antara-dua-darjat" to "https://upload.wikimedia.org/wikipedia/ms/9/9c/Antara_Dua_Darjat.jpg",
        "antaraduadarjat" to "https://upload.wikimedia.org/wikipedia/ms/9/9c/Antara_Dua_Darjat.jpg",
        "sarjan-hassan" to "https://upload.wikimedia.org/wikipedia/ms/e/ec/Sarjan_hassan_01.jpg",
        "sarjanhassan" to "https://upload.wikimedia.org/wikipedia/ms/e/ec/Sarjan_hassan_01.jpg",
        "penarek-becha" to "https://upload.wikimedia.org/wikipedia/ms/8/88/Filem-Penarek_Becha.jpg",
        "penarekbecha" to "https://upload.wikimedia.org/wikipedia/ms/8/88/Filem-Penarek_Becha.jpg",
        "penarik-beca" to "https://upload.wikimedia.org/wikipedia/ms/8/88/Filem-Penarek_Becha.jpg",
        "penarikbeca" to "https://upload.wikimedia.org/wikipedia/ms/8/88/Filem-Penarek_Becha.jpg"
    )

    val PRAMLEE_DESCRIPTIONS = mapOf(
        "abu-hassan-penchuri" to "Kisah dongeng klasik 1001 Malam mengisahkan pemuda miskin bernama Abu Hassan yang jatuh hati kepada Puteri Faridah dan terpaksa berhadapan dengan Wazir yang zalim.",
        "ahmad-albab" to "Kisah komedi satirikal Mashood Hakimi yang menguji tiga menantunya. Syawal, pemuda miskin dan jujur yang mengahwini Mastura, akhirnya menemui harta karun Ahmad Albab.",
        "bujang-lapok" to "Kisah lucu tiga bujang lapok—Ramli, Aziz, dan Sudin—yang menyewa bilik bersama di rumah Mak Tom dan berusaha mencari cinta serta pekerjaan di Singapura.",
        "do-re-mi" to "Kisah tiga sahabat penganggur yang menggelar diri mereka Do, Re, dan Mi yang sering terlibat dalam pelbagai helah lucu untuk mencari rezeki.",
        "enam-jahanam" to "Kisah dendam Tantari terhadap kumpulan perompak 'Enam Jahanam' yang telah merompak dan membunuh isterinya Mastura.",
        "hang-tuah" to "Kisah epik sejarah pahlawan legenda Melayu Laksamana Hang Tuah yang taat setia kepada Sultan Melaka, berhadapan dengan sahabat karibnya Hang Jebat.",
        "kanchan-tirana" to "Kisah pendekar Kanchan yang menentang kezaliman pendekar Tirana demi menegakkan keadilan dan membela rakyat tertindas.",
        "keluarga69" to "Kisah komedi kekeluargaan yang rumit apabila Osman berkahwin dengan anak kepada wanita yang berkahwin dengan bapa kandungnya sendiri.",
        "keluarga-69" to "Kisah komedi kekeluargaan yang rumit apabila Osman berkahwin dengan anak kepada wanita yang berkahwin dengan bapa kandungnya sendiri.",
        "labu-dan-labi" to "Kisah dua orang gaji setia, Labu dan Labi, yang bekerja di rumah Haji Bakhil bin Haji Lebai Kedekut dan sering berangan-angan tentang kekayaan dan cinta.",
        "laksamana-do-re-mi" to "Filem terakhir Tan Sri P. Ramlee mengisahkan tiga sahabat Do, Re, dan Mi yang memperoleh kuasa ajaib lalu dilantik menjadi menteri pertahanan negeri Pasir Berdengung.",
        "masam-masam-manis" to "Kisah Cikgu Shaari yang mengajar di sekolah pada waktu siang dan menyanyi di kelab malam pada waktu malam, lalu jatuh cinta dengan jiran bilik sewanya, Norkiah.",
        "musang-berjanggut" to "Kisah Nila Utama, anak angkat Sultan Negeri Pura Cendana, yang mencari wanita sejati untuk dijadikan isteri dengan membawa beras bercampur pasir.",
        "nasib-do-re-mi" to "Kesinambungan pengembaraan Do, Re, dan Mi yang cuba mencari pekerjaan baru dan membuka perniagaan jualan ubat serta menyelamatkan wanita yang dianiaya.",
        "nasib-si-labu-labi" to "Kesinambungan kisah Labu dan Labi yang menyertai pertandingan peragaan pakaian dan terus berhadapan dengan karenah majikan mereka, Haji Bakhil.",
        "putus-sudah-kasih-sayang" to "Kisah drama emosi mengenai pengorbanan cinta dan kekeluargaan yang diuji oleh salah faham dan tragedi.",
        "putus-sudah-kaseh-sayang" to "Kisah drama emosi mengenai pengorbanan cinta dan kekeluargaan yang diuji oleh salah faham dan tragedi.",
        "ragam-p-ramlee" to "Filem antologi warna yang memaparkan beberapa sketsa lucu dan musikal arahan serta lakonan Tan Sri P. Ramlee.",
        "semerah-padi" to "Kisah epik zaman Melayu kuno di kampung Semerah Padi mengisahkan persahabatan, hukum Islam, dan cinta antara Aduka dan Dara.",
        "sesudah-subuh" to "Drama sosial mengisahkan cabaran kehidupan moden, perbezaan generasi, dan keharmonian keluarga di Kuala Lumpur pada era 1960-an.",
        "ali-baba-bujang-lapok" to "Kisah komedi klasik Ali Baba, seorang pemuda miskin yang menjumpai gua rahsia 40 penyamun yang dipenuhi harta karun, dan abangnya Kassim Baba yang tamak.",
        "alibababujanglapok" to "Kisah komedi klasik Ali Baba, seorang pemuda miskin yang menjumpai gua rahsia 40 penyamun yang dipenuhi harta karun, dan abangnya Kassim Baba yang tamak.",
        "nujum-pak-belalang" to "Kisah Pak Belalang dan anaknya Belalang yang berpakat berpura-pura menjadi ahli nujum pintar sehingga dilantik menjadi Ahli Nujum Negara oleh Sultan Negeri Beringin Rendang.",
        "nujumpakbelalang" to "Kisah Pak Belalang dan anaknya Belalang yang berpakat berpura-pura menjadi ahli nujum pintar sehingga dilantik menjadi Ahli Nujum Negara oleh Sultan Negeri Beringin Rendang.",
        "sumpah-orang-minyak" to "Kisah Si Bongkok yang dihina kerana kecacatannya, membuat perjanjian dengan syaitan untuk menjadi kacak dan sakti namun terperangkap menjadi Orang Minyak yang mengganas.",
        "sumpahorangminyak" to "Kisah Si Bongkok yang dihina kerana kecacatannya, membuat perjanjian dengan syaitan untuk menjadi kacak dan sakti namun terperangkap menjadi Orang Minyak yang mengganas.",
        "tiga-abdul" to "Kisah tiga adik-beradik Abdul Wahab, Abdul Wahid dan Abdul Wahub. Dua abang yang tamak ditipu oleh Sadiq Segaraga, manakala Abdul Wahub yang bijak berjaya menyelamatkan harta dan mengajar mertuanya.",
        "tigaabdul" to "Kisah tiga adik-beradik Abdul Wahab, Abdul Wahid dan Abdul Wahub. Dua abang yang tamak ditipu oleh Sadiq Segaraga, manakala Abdul Wahub yang bijak berjaya menyelamatkan harta dan mengajar mertuanya.",
        "seniman-bujang-lapok" to "Kisah tiga bujang lapok—Ramli, Aziz, dan Sudin—yang mencuba nasib menjadi bintang filem di Malay Film Productions (Studio Jalan Ampas). Penuh dengan babak ikonik uji bakat 'Cobaan...' dan gelagat lucu pengarah Ahmad Nisfu.",
        "senimanbujanglapok" to "Kisah tiga bujang lapok—Ramli, Aziz, dan Sudin—yang mencuba nasib menjadi bintang filem di Malay Film Productions (Studio Jalan Ampas). Penuh dengan babak ikonik uji bakat 'Cobaan...' dan gelagat lucu pengarah Ahmad Nisfu.",
        "seniman-bujang-lapuk" to "Kisah tiga bujang lapok—Ramli, Aziz, dan Sudin—yang mencuba nasib menjadi bintang filem di Malay Film Productions (Studio Jalan Ampas). Penuh dengan babak ikonik uji bakat 'Cobaan...' dan gelagat lucu pengarah Ahmad Nisfu.",
        "senimanbujanglapuk" to "Kisah tiga bujang lapok—Ramli, Aziz, dan Sudin—yang mencuba nasib menjadi bintang filem di Malay Film Productions (Studio Jalan Ampas). Penuh dengan babak ikonik uji bakat 'Cobaan...' dan gelagat lucu pengarah Ahmad Nisfu.",
        "pendekar-bujang-lapok" to "Tiga sahabat bujang lapok merantau ke Kampung Pinang Sebatang untuk menuntut ilmu persilatan dengan Pendekar Mustar demi mempertahankan diri daripada samseng penambang. Sarat dengan babak legenda 'Alif Mim Nun Wau - Sarkas!' dan belajar membaca.",
        "pendekarbujanglapok" to "Tiga sahabat bujang lapok merantau ke Kampung Pinang Sebatang untuk menuntut ilmu persilatan dengan Pendekar Mustar demi mempertahankan diri daripada samseng penambang. Sarat dengan babak legenda 'Alif Mim Nun Wau - Sarkas!' dan belajar membaca.",
        "pendekar-bujang-lapuk" to "Tiga sahabat bujang lapok merantau ke Kampung Pinang Sebatang untuk menuntut ilmu persilatan dengan Pendekar Mustar demi mempertahankan diri daripada samseng penambang. Sarat dengan babak legenda 'Alif Mim Nun Wau - Sarkas!' dan belajar membaca.",
        "pendekarbujanglapuk" to "Tiga sahabat bujang lapok merantau ke Kampung Pinang Sebatang untuk menuntut ilmu persilatan dengan Pendekar Mustar demi mempertahankan diri daripada samseng penambang. Sarat dengan babak legenda 'Alif Mim Nun Wau - Sarkas!' dan belajar membaca.",
        "madu-tiga" to "Jamil berkahwin dengan tiga wanita berbeza—Latifah, Hasnah, dan Rohani—tanpa pengetahuan mereka berkat bantuan bapa mertuanya Pak Mansor. Situasi menjadi huru-hara apabila ketiga-tiga isteri bertemu di salon kecantikan.",
        "madutiga" to "Jamil berkahwin dengan tiga wanita berbeza—Latifah, Hasnah, dan Rohani—tanpa pengetahuan mereka berkat bantuan bapa mertuanya Pak Mansor. Situasi menjadi huru-hara apabila ketiga-tiga isteri bertemu di salon kecantikan.",
        "ibu-mertua-ku" to "Tragedi cinta antara pemuzik saksofon Kassim Selamat dan gadis kaya Sabariah yang ditentang hebat oleh ibu Sabariah, Nyonya Mansoor, sehingga membawa kepada pembohongan, kebutaan, dan pengorbanan menyayat hati.",
        "ibumertuaku" to "Tragedi cinta antara pemuzik saksofon Kassim Selamat dan gadis kaya Sabariah yang ditentang hebat oleh ibu Sabariah, Nyonya Mansoor, sehingga membawa kepada pembohongan, kebutaan, dan pengorbanan menyayat hati.",
        "ibu-mertuaku" to "Tragedi cinta antara pemuzik saksofon Kassim Selamat dan gadis kaya Sabariah yang ditentang hebat oleh ibu Sabariah, Nyonya Mansoor, sehingga membawa kepada pembohongan, kebutaan, dan pengorbanan menyayat hati.",
        "anak-ku-sazali" to "Kisah pengorbanan seorang bapa, Hassan, yang terlalu memanjakan anak tunggalnya Sazali sehingga Sazali membesar menjadi ketua samseng yang kejam. Hassan berhadapan dengan dilema moral antara kasih sayang seorang bapa dan keadilan undang-undang.",
        "anakku-sazali" to "Kisah pengorbanan seorang bapa, Hassan, yang terlalu memanjakan anak tunggalnya Sazali sehingga Sazali membesar menjadi ketua samseng yang kejam. Hassan berhadapan dengan dilema moral antara kasih sayang seorang bapa dan keadilan undang-undang.",
        "anakkusazali" to "Kisah pengorbanan seorang bapa, Hassan, yang terlalu memanjakan anak tunggalnya Sazali sehingga Sazali membesar menjadi ketua samseng yang kejam. Hassan berhadapan dengan dilema moral antara kasih sayang seorang bapa dan keadilan undang-undang.",
        "antara-dua-darjat" to "Kisah Ghazali, pemuzik kampung yang menyelamatkan Engku Zaleha, anak seorang kerabat bangsawan. Cinta mereka ditentang keras oleh keluarga Zaleha kerana perbezaan darjat yang akhirnya membawa kepada tragedi pemisahan kejam.",
        "antaraduadarjat" to "Kisah Ghazali, pemuzik kampung yang menyelamatkan Engku Zaleha, anak seorang kerabat bangsawan. Cinta mereka ditentang keras oleh keluarga Zaleha kerana perbezaan darjat yang akhirnya membawa kepada tragedi pemisahan kejam.",
        "sarjan-hassan" to "Kisah pemuda kampung Hassan yang sering diejek sebagai penakut, lalu menyertai Rejimen Askar Melayu dan berjuang dengan penuh keberanian menentang tentera Jepun demi mempertahankan tanah air.",
        "sarjanhassan" to "Kisah pemuda kampung Hassan yang sering diejek sebagai penakut, lalu menyertai Rejimen Askar Melayu dan berjuang dengan penuh keberanian menentang tentera Jepun demi mempertahankan tanah air.",
        "penarek-becha" to "Filem pertama arahan Tan Sri P. Ramlee mengisahkan Amran, seorang penarik beca miskin berhati mulia yang jatuh cinta dengan Azizah, gadis bangsawan. Hubungan mereka dicemburui oleh pemuda kaya Ghazali yang cuba merancang pelbagai tipu muslihat.",
        "penarekbecha" to "Filem pertama arahan Tan Sri P. Ramlee mengisahkan Amran, seorang penarik beca miskin berhati mulia yang jatuh cinta dengan Azizah, gadis bangsawan. Hubungan mereka dicemburui oleh pemuda kaya Ghazali yang cuba merancang pelbagai tipu muslihat.",
        "penarik-beca" to "Filem pertama arahan Tan Sri P. Ramlee mengisahkan Amran, seorang penarik beca miskin berhati mulia yang jatuh cinta dengan Azizah, gadis bangsawan. Hubungan mereka dicemburui oleh pemuda kaya Ghazali yang cuba merancang pelbagai tipu muslihat.",
        "penarikbeca" to "Filem pertama arahan Tan Sri P. Ramlee mengisahkan Amran, seorang penarik beca miskin berhati mulia yang jatuh cinta dengan Azizah, gadis bangsawan. Hubungan mereka dicemburui oleh pemuda kaya Ghazali yang cuba merancang pelbagai tipu muslihat."
    )

    @Volatile
    private var archivePramleeCache: List<Video>? = null
    private val archivePramleeLock = Any()

    /**
     * Dedicated P.Ramlee Classic Catalog: Fetches and parses 22+ high-definition (1080p/720p)
     * classic films from Internet Archive with direct MP4 streams.
     */
    suspend fun fetchArchivePramleeVideos(forceRefresh: Boolean = false): List<Video> = withContext(Dispatchers.IO) {
        if (!forceRefresh && archivePramleeCache != null) {
            return@withContext archivePramleeCache!!
        }

        // Standalone classic additions from verified high-speed Archive.org storage nodes
        val standaloneAdditions = listOf(
            Video(
                id = "ia_pramlee_ali-baba-bujang-lapok",
                title = "Ali Baba Bujang Lapok (1961)",
                thumbnailUrl = PRAMLEE_POSTERS["ali-baba-bujang-lapok"] ?: "https://image.tmdb.org/t/p/w500/dYSuQvfc9RDzzvtvj3iIVditp9e.jpg",
                backdropUrl = PRAMLEE_POSTERS["ali-baba-bujang-lapok"] ?: "https://image.tmdb.org/t/p/w500/dYSuQvfc9RDzzvtvj3iIVditp9e.jpg",
                videoUrl = "https://dn600309.us.archive.org/0/items/p.-ramlee-ali-baba-bujang-lapok/P.%20Ramlee%20-%20Ali%20Baba%20Bujang%20Lapok.mp4",
                duration = "122 min",
                views = "Classic",
                date = "1961",
                quality = "HD",
                description = PRAMLEE_DESCRIPTIONS["ali-baba-bujang-lapok"] ?: "",
                actresses = listOf("Tan Sri P. Ramlee"),
                servers = listOf(
                    VideoServer(name = "Archive.org HD (Fast Direct)", url = "https://dn600309.us.archive.org/0/items/p.-ramlee-ali-baba-bujang-lapok/P.%20Ramlee%20-%20Ali%20Baba%20Bujang%20Lapok.mp4"),
                    VideoServer(name = "Archive.org HD (Mirror)", url = "https://archive.org/download/p.-ramlee-ali-baba-bujang-lapok/P.%20Ramlee%20-%20Ali%20Baba%20Bujang%20Lapok.mp4")
                )
            ),
            Video(
                id = "ia_pramlee_nujum-pak-belalang",
                title = "Nujum Pak Belalang (1959)",
                thumbnailUrl = PRAMLEE_POSTERS["nujum-pak-belalang"] ?: "https://image.tmdb.org/t/p/w500/rcwroUjJfTDFVMrX8DRXB6kuuKJ.jpg",
                backdropUrl = PRAMLEE_POSTERS["nujum-pak-belalang"] ?: "https://image.tmdb.org/t/p/w500/rcwroUjJfTDFVMrX8DRXB6kuuKJ.jpg",
                videoUrl = "https://dn601208.us.archive.org/0/items/p-ramlee-nujum-pak-belalang-hd-quality-1/P%20Ramlee%20Nujum%20Pak%20Belalang%20HD%20Quality%20%281%29.mp4",
                duration = "111 min",
                views = "Classic",
                date = "1959",
                quality = "HD",
                description = PRAMLEE_DESCRIPTIONS["nujum-pak-belalang"] ?: "",
                actresses = listOf("Tan Sri P. Ramlee"),
                servers = listOf(
                    VideoServer(name = "Archive.org HD (Fast Direct)", url = "https://dn601208.us.archive.org/0/items/p-ramlee-nujum-pak-belalang-hd-quality-1/P%20Ramlee%20Nujum%20Pak%20Belalang%20HD%20Quality%20%281%29.mp4"),
                    VideoServer(name = "Archive.org HD (Mirror)", url = "https://archive.org/download/p-ramlee-nujum-pak-belalang-hd-quality-1/P%20Ramlee%20Nujum%20Pak%20Belalang%20HD%20Quality%20%281%29.mp4")
                )
            ),
            Video(
                id = "ia_pramlee_sumpah-orang-minyak",
                title = "Sumpah Orang Minyak (1958)",
                thumbnailUrl = PRAMLEE_POSTERS["sumpah-orang-minyak"] ?: "https://image.tmdb.org/t/p/w500/wvrMRUoR6X2AKD9LBZTQGryx6Nc.jpg",
                backdropUrl = PRAMLEE_POSTERS["sumpah-orang-minyak"] ?: "https://image.tmdb.org/t/p/w500/wvrMRUoR6X2AKD9LBZTQGryx6Nc.jpg",
                videoUrl = "https://dn601208.us.archive.org/0/items/p-ramlee-nujum-pak-belalang-hd-quality-1/Sumpah%20Orang%20Minyak%20HD%20-%20Pramlee%20%28versi%20warna%29%20FULL.mp4",
                duration = "84 min",
                views = "Classic",
                date = "1958",
                quality = "720p",
                description = PRAMLEE_DESCRIPTIONS["sumpah-orang-minyak"] ?: "",
                actresses = listOf("Tan Sri P. Ramlee"),
                servers = listOf(
                    VideoServer(name = "Archive.org HD (Fast Direct)", url = "https://dn601208.us.archive.org/0/items/p-ramlee-nujum-pak-belalang-hd-quality-1/Sumpah%20Orang%20Minyak%20HD%20-%20Pramlee%20%28versi%20warna%29%20FULL.mp4"),
                    VideoServer(name = "Archive.org HD (Mirror)", url = "https://archive.org/download/p-ramlee-nujum-pak-belalang-hd-quality-1/Sumpah%20Orang%20Minyak%20HD%20-%20Pramlee%20%28versi%20warna%29%20FULL.mp4")
                )
            ),
            Video(
                id = "ia_pramlee_tiga-abdul",
                title = "Tiga Abdul (1964)",
                thumbnailUrl = PRAMLEE_POSTERS["tiga-abdul"] ?: "https://image.tmdb.org/t/p/w500/8X9YyahJBDBQ3wjTEN6HubQN6Cg.jpg",
                backdropUrl = PRAMLEE_POSTERS["tiga-abdul"] ?: "https://image.tmdb.org/t/p/w500/8X9YyahJBDBQ3wjTEN6HubQN6Cg.jpg",
                videoUrl = "https://dn600305.us.archive.org/0/items/TigaAbdul1964HQFullMovie/Tiga%20Abdul%20%281964%29%20HQ%20%28Full%20Movie%29.mp4",
                duration = "113 min",
                views = "Classic",
                date = "1964",
                quality = "HD",
                description = PRAMLEE_DESCRIPTIONS["tiga-abdul"] ?: "",
                actresses = listOf("Tan Sri P. Ramlee"),
                servers = listOf(
                    VideoServer(name = "Archive.org HD (Fast Direct)", url = "https://dn600305.us.archive.org/0/items/TigaAbdul1964HQFullMovie/Tiga%20Abdul%20%281964%29%20HQ%20%28Full%20Movie%29.mp4"),
                    VideoServer(name = "Archive.org HD (Mirror)", url = "https://archive.org/download/TigaAbdul1964HQFullMovie/Tiga%20Abdul%20%281964%29%20HQ%20%28Full%20Movie%29.mp4")
                )
            ),
            Video(
                id = "ia_pramlee_seniman-bujang-lapok",
                title = "Seniman Bujang Lapok (1961)",
                thumbnailUrl = PRAMLEE_POSTERS["seniman-bujang-lapok"] ?: "https://img.youtube.com/vi/VwVq6IurDYg/hqdefault.jpg",
                backdropUrl = "https://img.youtube.com/vi/VwVq6IurDYg/hqdefault.jpg",
                videoUrl = "https://dn600305.us.archive.org/0/items/p-ramlee-seniman-bujang-lapok-full-movie-warna/P%20Ramlee%20-%20Seniman%20Bujang%20Lapok%20%5BFull%20movie%20warna%5D.mp4",
                duration = "120 min",
                views = "Classic",
                date = "1961",
                quality = "HD",
                description = PRAMLEE_DESCRIPTIONS["seniman-bujang-lapok"] ?: "",
                actresses = listOf("Tan Sri P. Ramlee", "Saloma", "Aziz Sattar", "S. Shamsuddin"),
                servers = listOf(
                    VideoServer(name = "Archive.org HD (Warna)", url = "https://dn600305.us.archive.org/0/items/p-ramlee-seniman-bujang-lapok-full-movie-warna/P%20Ramlee%20-%20Seniman%20Bujang%20Lapok%20%5BFull%20movie%20warna%5D.mp4"),
                    VideoServer(name = "Archive.org HD (Hitam Putih)", url = "https://archive.org/download/p.ramleesenimanbujanglapok1961/P.%20Ramlee%20-%20Seniman%20Bujang%20Lapok%20%281961%29.mp4"),
                    VideoServer(name = "YouTube HD (Full Movie)", url = "https://www.youtube-nocookie.com/embed/VwVq6IurDYg?autoplay=1&controls=0&playsinline=1&enablejsapi=1&rel=0&iv_load_policy=3&fs=0")
                )
            ),
            Video(
                id = "ia_pramlee_pendekar-bujang-lapok",
                title = "Pendekar Bujang Lapok (1959)",
                thumbnailUrl = PRAMLEE_POSTERS["pendekar-bujang-lapok"] ?: "https://img.youtube.com/vi/1Cij_LQNVHU/hqdefault.jpg",
                backdropUrl = "https://img.youtube.com/vi/1Cij_LQNVHU/hqdefault.jpg",
                videoUrl = "https://ia800602.us.archive.org/17/items/pendekar-bujang-lapok-1959/Pendekar_Bujang_Lapok_IA.mp4",
                duration = "104 min",
                views = "Classic",
                date = "1959",
                quality = "720p",
                description = PRAMLEE_DESCRIPTIONS["pendekar-bujang-lapok"] ?: "",
                actresses = listOf("Tan Sri P. Ramlee", "Aziz Sattar", "S. Shamsuddin", "Roseyatimah"),
                servers = listOf(
                    VideoServer(name = "Archive.org 720p (Direct HD)", url = "https://ia800602.us.archive.org/17/items/pendekar-bujang-lapok-1959/Pendekar_Bujang_Lapok_IA.mp4"),
                    VideoServer(name = "Archive.org 720p (Mirror)", url = "https://archive.org/download/pendekar-bujang-lapok-1959/Pendekar_Bujang_Lapok_IA.mp4"),
                    VideoServer(name = "YouTube HD (Full Movie)", url = "https://www.youtube-nocookie.com/embed/1Cij_LQNVHU?autoplay=1&controls=0&playsinline=1&enablejsapi=1&rel=0&iv_load_policy=3&fs=0"),
                    VideoServer(name = "Dailymotion HD (Mirror)", url = "https://www.dailymotion.com/embed/video/x9yfnd2?autoplay=1&mute=0&ui-logo=0&sharing-enable=0&ui-start-screen-info=0")
                )
            ),
            Video(
                id = "ia_pramlee_madu-tiga",
                title = "Madu Tiga (1964)",
                thumbnailUrl = PRAMLEE_POSTERS["madu-tiga"] ?: "https://img.youtube.com/vi/ZCfv21EfdzA/hqdefault.jpg",
                backdropUrl = "https://img.youtube.com/vi/ZCfv21EfdzA/hqdefault.jpg",
                videoUrl = "https://www.youtube-nocookie.com/embed/ZCfv21EfdzA?autoplay=1&controls=0&playsinline=1&enablejsapi=1&rel=0&iv_load_policy=3&fs=0",
                duration = "100 min",
                views = "Classic",
                date = "1964",
                quality = "HD",
                description = PRAMLEE_DESCRIPTIONS["madu-tiga"] ?: "",
                actresses = listOf("Tan Sri P. Ramlee", "Sarimah", "Jah Mahadi", "Zara Agus"),
                servers = listOf(
                    VideoServer(name = "YouTube HD (HQ EngSub)", url = "https://www.youtube-nocookie.com/embed/ZCfv21EfdzA?autoplay=1&controls=0&playsinline=1&enablejsapi=1&rel=0&iv_load_policy=3&fs=0"),
                    VideoServer(name = "YouTube HD (Versi Warna)", url = "https://www.youtube-nocookie.com/embed/GlcNq7BsCKo?autoplay=1&controls=0&playsinline=1&enablejsapi=1&rel=0&iv_load_policy=3&fs=0"),
                    VideoServer(name = "YouTube HD (Mirror)", url = "https://www.youtube-nocookie.com/embed/WJ2ieFBb_jc?autoplay=1&controls=0&playsinline=1&enablejsapi=1&rel=0&iv_load_policy=3&fs=0")
                )
            ),
            Video(
                id = "ia_pramlee_ibu-mertua-ku",
                title = "Ibu Mertua-ku (1962)",
                thumbnailUrl = PRAMLEE_POSTERS["ibu-mertua-ku"] ?: "https://img.youtube.com/vi/u2CEfblj-cU/hqdefault.jpg",
                backdropUrl = "https://img.youtube.com/vi/u2CEfblj-cU/hqdefault.jpg",
                videoUrl = "https://www.youtube-nocookie.com/embed/u2CEfblj-cU?autoplay=1&controls=0&playsinline=1&enablejsapi=1&rel=0&iv_load_policy=3&fs=0",
                duration = "115 min",
                views = "Classic",
                date = "1962",
                quality = "HD",
                description = PRAMLEE_DESCRIPTIONS["ibu-mertua-ku"] ?: "",
                actresses = listOf("Tan Sri P. Ramlee", "Sarimah", "Mak Dara", "Ahmad Mahmud"),
                servers = listOf(
                    VideoServer(name = "YouTube HD (Full Movie)", url = "https://www.youtube-nocookie.com/embed/u2CEfblj-cU?autoplay=1&controls=0&playsinline=1&enablejsapi=1&rel=0&iv_load_policy=3&fs=0"),
                    VideoServer(name = "YouTube HD (Versi Warna)", url = "https://www.youtube-nocookie.com/embed/RgVDH45tR5A?autoplay=1&controls=0&playsinline=1&enablejsapi=1&rel=0&iv_load_policy=3&fs=0"),
                    VideoServer(name = "YouTube HD (Mirror)", url = "https://www.youtube-nocookie.com/embed/0egZjfVfkbw?autoplay=1&controls=0&playsinline=1&enablejsapi=1&rel=0&iv_load_policy=3&fs=0")
                )
            ),
            Video(
                id = "ia_pramlee_anak-ku-sazali",
                title = "Anak-ku Sazali (1956)",
                thumbnailUrl = PRAMLEE_POSTERS["anak-ku-sazali"] ?: "https://img.youtube.com/vi/UTBXVBG7fqs/hqdefault.jpg",
                backdropUrl = "https://img.youtube.com/vi/UTBXVBG7fqs/hqdefault.jpg",
                videoUrl = "https://www.youtube-nocookie.com/embed/UTBXVBG7fqs?autoplay=1&controls=0&playsinline=1&enablejsapi=1&rel=0&iv_load_policy=3&fs=0",
                duration = "108 min",
                views = "Classic",
                date = "1956",
                quality = "HD",
                description = PRAMLEE_DESCRIPTIONS["anak-ku-sazali"] ?: "",
                actresses = listOf("Tan Sri P. Ramlee", "Zaiton", "Rosnani Jamil"),
                servers = listOf(
                    VideoServer(name = "YouTube HD (Full Movie)", url = "https://www.youtube-nocookie.com/embed/UTBXVBG7fqs?autoplay=1&controls=0&playsinline=1&enablejsapi=1&rel=0&iv_load_policy=3&fs=0"),
                    VideoServer(name = "YouTube 720p (Versi Warna)", url = "https://www.youtube-nocookie.com/embed/eeneWfOQnvk?autoplay=1&controls=0&playsinline=1&enablejsapi=1&rel=0&iv_load_policy=3&fs=0"),
                    VideoServer(name = "YouTube HD (Mirror)", url = "https://www.youtube-nocookie.com/embed/ZzFeVkmOTEM?autoplay=1&controls=0&playsinline=1&enablejsapi=1&rel=0&iv_load_policy=3&fs=0")
                )
            ),
            Video(
                id = "ia_pramlee_antara-dua-darjat",
                title = "Antara Dua Darjat (1960)",
                thumbnailUrl = PRAMLEE_POSTERS["antara-dua-darjat"] ?: "https://img.youtube.com/vi/_tdHjooqbAk/hqdefault.jpg",
                backdropUrl = "https://img.youtube.com/vi/_tdHjooqbAk/hqdefault.jpg",
                videoUrl = "https://www.youtube-nocookie.com/embed/_tdHjooqbAk?autoplay=1&controls=0&playsinline=1&enablejsapi=1&rel=0&iv_load_policy=3&fs=0",
                duration = "110 min",
                views = "Classic",
                date = "1960",
                quality = "HD",
                description = PRAMLEE_DESCRIPTIONS["antara-dua-darjat"] ?: "",
                actresses = listOf("Tan Sri P. Ramlee", "Saadiah", "S. Kadarisman"),
                servers = listOf(
                    VideoServer(name = "YouTube HD (Full Movie EngSub)", url = "https://www.youtube-nocookie.com/embed/_tdHjooqbAk?autoplay=1&controls=0&playsinline=1&enablejsapi=1&rel=0&iv_load_policy=3&fs=0"),
                    VideoServer(name = "YouTube HD (Versi Warna)", url = "https://www.youtube-nocookie.com/embed/kkyS0NqQPuY?autoplay=1&controls=0&playsinline=1&enablejsapi=1&rel=0&iv_load_policy=3&fs=0"),
                    VideoServer(name = "YouTube HD (Mirror)", url = "https://www.youtube-nocookie.com/embed/YdUqkI-QCXI?autoplay=1&controls=0&playsinline=1&enablejsapi=1&rel=0&iv_load_policy=3&fs=0")
                )
            ),
            Video(
                id = "ia_pramlee_sarjan-hassan",
                title = "Sarjan Hassan (1958)",
                thumbnailUrl = PRAMLEE_POSTERS["sarjan-hassan"] ?: "https://img.youtube.com/vi/syqvPWymshg/hqdefault.jpg",
                backdropUrl = "https://img.youtube.com/vi/syqvPWymshg/maxresdefault.jpg",
                videoUrl = "https://www.youtube-nocookie.com/embed/syqvPWymshg?autoplay=1&controls=0&playsinline=1&enablejsapi=1&rel=0&iv_load_policy=3&fs=0",
                duration = "109 min",
                views = "Classic",
                date = "1958",
                quality = "HD",
                description = PRAMLEE_DESCRIPTIONS["sarjan-hassan"] ?: "",
                actresses = listOf("Tan Sri P. Ramlee", "Jins Shamsuddin", "Saadiah"),
                servers = listOf(
                    VideoServer(name = "YouTube HD (Full Movie)", url = "https://www.youtube-nocookie.com/embed/syqvPWymshg?autoplay=1&controls=0&playsinline=1&enablejsapi=1&rel=0&iv_load_policy=3&fs=0"),
                    VideoServer(name = "YouTube HD (Versi Klasik)", url = "https://www.youtube-nocookie.com/embed/GyOhIB-JJqI?autoplay=1&controls=0&playsinline=1&enablejsapi=1&rel=0&iv_load_policy=3&fs=0"),
                    VideoServer(name = "Dailymotion HD (Full Movie)", url = "https://www.dailymotion.com/embed/video/x9p5kmy?autoplay=1")
                )
            ),
            Video(
                id = "ia_pramlee_penarek-becha",
                title = "Penarek Becha (1955)",
                thumbnailUrl = PRAMLEE_POSTERS["penarek-becha"] ?: "https://img.youtube.com/vi/TNcleBetr70/hqdefault.jpg",
                backdropUrl = "https://img.youtube.com/vi/TNcleBetr70/hqdefault.jpg",
                videoUrl = "https://www.youtube-nocookie.com/embed/TNcleBetr70?autoplay=1&controls=0&playsinline=1&enablejsapi=1&rel=0&iv_load_policy=3&fs=0",
                duration = "110 min",
                views = "Classic",
                date = "1955",
                quality = "HD",
                description = PRAMLEE_DESCRIPTIONS["penarek-becha"] ?: "",
                actresses = listOf("Tan Sri P. Ramlee", "Saadiah", "Udo Omar"),
                servers = listOf(
                    VideoServer(name = "YouTube HD (Full Movie)", url = "https://www.youtube-nocookie.com/embed/TNcleBetr70?autoplay=1&controls=0&playsinline=1&enablejsapi=1&rel=0&iv_load_policy=3&fs=0"),
                    VideoServer(name = "YouTube HD (Mirror 1)", url = "https://www.youtube-nocookie.com/embed/hBpXmsQ1anQ?autoplay=1&controls=0&playsinline=1&enablejsapi=1&rel=0&iv_load_policy=3&fs=0"),
                    VideoServer(name = "YouTube HD (Mirror 2)", url = "https://www.youtube-nocookie.com/embed/6QpUf15uVBo?autoplay=1&controls=0&playsinline=1&enablejsapi=1&rel=0&iv_load_policy=3&fs=0")
                )
            )
        )

        try {
            val metadataUrl = "https://archive.org/metadata/FilemP.ramlee"
            val request = Request.Builder()
                .url(metadataUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()
            val jsonStr = try {
                NetworkConfig.okHttpClient.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Archive.org metadata fetch failed: ${e.message}")
                null
            } ?: return@withContext archivePramleeCache ?: standaloneAdditions
            val jsonObj = org.json.JSONObject(jsonStr)
            val filesArray = jsonObj.optJSONArray("files") ?: return@withContext archivePramleeCache ?: standaloneAdditions
            val storageServer = jsonObj.optString("server", "dn600308.us.archive.org").ifEmpty { "dn600308.us.archive.org" }
            val storageDir = jsonObj.optString("dir", "/0/items/FilemP.ramlee").ifEmpty { "/0/items/FilemP.ramlee" }
            val fastBase = "https://$storageServer$storageDir"

            val allFileNames = mutableSetOf<String>()
            val thumbMap = mutableMapOf<String, MutableList<String>>()

            for (i in 0 until filesArray.length()) {
                val fObj = filesArray.optJSONObject(i) ?: continue
                val name = fObj.optString("name", "")
                if (name.isNotEmpty()) {
                    allFileNames.add(name)
                    if (name.contains("thumbs/") && name.endsWith(".jpg", ignoreCase = true)) {
                        val base = name.substringAfterLast('/').substringBeforeLast('_')
                        thumbMap.getOrPut(base) { mutableListOf() }.add(name)
                    }
                }
            }

            val bestThumb = mutableMapOf<String, String>()
            for ((base, list) in thumbMap) {
                if (list.isNotEmpty()) {
                    bestThumb[base] = list[list.size / 2]
                }
            }

            val movies = mutableListOf<Video>()
            val seenTitles = mutableSetOf<String>()

            for (i in 0 until filesArray.length()) {
                val fObj = filesArray.optJSONObject(i) ?: continue
                val rawName = fObj.optString("name", "")
                if (!rawName.endsWith(".mp4", ignoreCase = true) && !rawName.endsWith(".mkv", ignoreCase = true)) {
                    continue
                }
                // Skip .ia.mp4 derivatives if primary MP4 or MKV exists
                if (rawName.endsWith(".ia.mp4", ignoreCase = true)) {
                    val baseMp4 = rawName.replace(".ia.mp4", ".mp4", ignoreCase = true)
                    val baseUnderscore = rawName.replace(".", "_").replace("_ia_mp4", ".mp4", ignoreCase = true)
                    if (allFileNames.contains(baseMp4) || allFileNames.contains(baseUnderscore) || allFileNames.contains("Do_Re_Mi_1966.720p.LEGENDTV.mp4")) continue
                }

                val baseNoExt = rawName.substringBeforeLast('.')
                val spaceSeparated = baseNoExt.replace('_', ' ').replace('.', ' ')
                
                // Release Year
                val yearMatch = Regex("""\b(19\d{2})\b""").find(spaceSeparated)
                val year = yearMatch?.groupValues?.get(1) ?: ""

                // Quality
                val qual = when {
                    rawName.contains("1080p", ignoreCase = true) -> "1080p"
                    rawName.contains("720p", ignoreCase = true) -> "720p"
                    rawName.contains("576p", ignoreCase = true) -> "576p"
                    else -> "HD"
                }

                // Duration
                val lengthSec = fObj.optString("length", "")
                val duration = if (lengthSec.isNotEmpty()) {
                    val sec = lengthSec.toDoubleOrNull()?.toInt() ?: 0
                    if (sec > 0) "${sec / 60} min" else "90 min"
                } else "90 min"

                // Clean Title
                var clean = spaceSeparated.replace(Regex("(?i)\\b(?:720p|1080p|576p|legendtv|versi\\s*warna|ia|mp4|mkv)\\b"), "")
                if (year.isNotEmpty()) {
                    clean = clean.replace(year, "")
                }
                clean = clean.replace("(", "").replace(")", "").replace(Regex("""\s+"""), " ").trim()
                val fullTitle = if (year.isNotEmpty()) "$clean ($year)" else clean

                val normForDedup = clean.lowercase().replace("kaseh", "kasih").replace(Regex("[^a-z0-9]"), "")
                if (normForDedup.isEmpty() || !seenTitles.add(normForDedup)) {
                    continue
                }

                val slug = clean.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
                val videoId = "ia_pramlee_$slug"

                // Thumbnail, Poster & Video URLs
                val tFile = bestThumb[baseNoExt] ?: ""
                val archiveThumb = if (tFile.isNotEmpty()) "https://archive.org/download/FilemP.ramlee/$tFile" else ""
                val posterUrl = PRAMLEE_POSTERS[slug] 
                    ?: PRAMLEE_POSTERS[slug.replace("-", "")] 
                    ?: PRAMLEE_POSTERS[normForDedup] 
                    ?: archiveThumb
                val backdropUrl = posterUrl
                val fastVideoUrl = "$fastBase/$rawName"
                val fallbackVideoUrl = "https://archive.org/download/FilemP.ramlee/$rawName"
                val description = PRAMLEE_DESCRIPTIONS[slug] 
                    ?: PRAMLEE_DESCRIPTIONS[slug.replace("-", "")] 
                    ?: PRAMLEE_DESCRIPTIONS[normForDedup]
                    ?: "Koleksi Filem Klasik Tan Sri P. Ramlee dari Arkib Filem (Shaw Brothers / Studio Merdeka). Dihoskan di Internet Archive."

                val movie = Video(
                    id = videoId,
                    title = fullTitle,
                    thumbnailUrl = posterUrl,
                    backdropUrl = backdropUrl,
                    videoUrl = fastVideoUrl,
                    duration = duration,
                    views = "Classic",
                    date = year,
                    quality = qual,
                    description = description,
                    actresses = listOf("Tan Sri P. Ramlee"),
                    servers = listOf(
                        VideoServer(name = "Archive.org HD (Fast Direct)", url = fastVideoUrl),
                        VideoServer(name = "Archive.org HD (Mirror)", url = fallbackVideoUrl)
                    )
                )
                movies.add(movie)
            }

            for (addition in standaloneAdditions) {
                val norm = normalizeForDedup(addition.title)
                if (seenTitles.add(norm)) {
                    movies.add(addition)
                }
            }

            val sorted = movies.sortedBy { it.title }
            synchronized(archivePramleeLock) {
                archivePramleeCache = sorted
            }
            Log.i(TAG, "Loaded ${sorted.size} P. Ramlee classic movies from archive.org")
            sorted
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load P. Ramlee archive: ${e.message}", e)
            archivePramleeCache ?: standaloneAdditions
        }
    }

    fun encodeQuery(query: String): String =
        try {
            java.net.URLEncoder.encode(query, "UTF-8").replace("+", "%20")
        } catch (_: Exception) {
            query.replace(" ", "%20")
        }

    private suspend fun searchDomain(domainUrl: String, query: String, startPage: Int, maxCount: Int): List<Video> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Video>()
        val seenIds = mutableSetOf<String>()
        var currentPage = startPage
        val maxPages = startPage + 1
        val encodedQuery = encodeQuery(query)

        while (results.size < maxCount && currentPage <= maxPages) {
            val url = if (currentPage > 1) "$domainUrl/page/$currentPage/?s=$encodedQuery" else "$domainUrl/?s=$encodedQuery"
            Log.d(TAG, "Searching Domain $domainUrl Page $currentPage: $url")
            val html = fetchHtml(url) ?: break
            val scraped = scrapeVideosFromHtml(html, url)
            Log.d(TAG, "Domain $domainUrl Page $currentPage found ${scraped.size} items.")
            if (scraped.isEmpty()) break

            scraped.forEach { video ->
                if (seenIds.add(video.id)) {
                    results.add(video)
                }
            }

            if (results.size >= 15 || scraped.size < 5) break
            currentPage++
        }
        sortResultsByRelevance(results, query)
    }

    fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        if (m == 0) return n
        if (n == 0) return m
        var prev = IntArray(n + 1) { it }
        var curr = IntArray(n + 1)
        for (i in 1..m) {
            curr[0] = i
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                curr[j] = minOf(
                    curr[j - 1] + 1,
                    prev[j] + 1,
                    prev[j - 1] + cost
                )
            }
            val temp = prev
            prev = curr
            curr = temp
        }
        return prev[n]
    }

    data class CleanedQuery(
        val rawQuery: String,
        val cleanText: String,
        val year: Int?,
        val tokens: List<String> = emptyList(),
        val coreTokens: List<String> = emptyList()
    )

    private val STOP_WORDS = setOf(
        "the", "a", "an", "and", "or", "of", "in", "on", "at", "to", "for", "with",
        "movie", "film", "movies", "films", "full", "fullmovie",
        "sub", "subtitle", "indo", "malay", "subindo", "submalay",
        "season", "series", "episode", "ep", "eps", "hd", "bluray", "webdl"
    )

    fun normalizeWord(word: String): String {
        return when (word) {
            "lapuk" -> "lapok"
            "kaseh" -> "kasih"
            "spiderman" -> "spider"
            "ironman" -> "iron"
            "cerekarama" -> "telefilem"
            else -> word
        }
    }

    fun parseSearchQuery(query: String): CleanedQuery {
        val yearMatch = Regex("""\b(19\d{2}|20\d{2})\b""").find(query)
        val year = yearMatch?.groupValues?.get(1)?.toIntOrNull()
        val textWithoutYear = if (yearMatch != null) query.replace(yearMatch.value, " ") else query
        val clean = textWithoutYear.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        val allTokens = clean.split(Regex("""\s+""")).filter { it.isNotBlank() }
        val coreTokens = allTokens.filter { it !in STOP_WORDS }.map { normalizeWord(it) }
        return CleanedQuery(
            rawQuery = query.trim(),
            cleanText = clean,
            year = year,
            tokens = allTokens.map { normalizeWord(it) },
            coreTokens = if (coreTokens.isNotEmpty()) coreTokens else allTokens.map { normalizeWord(it) }
        )
    }

    fun calculateRelevanceScore(video: Video, parsedQuery: CleanedQuery): Int {
        val titleRaw = cleanTitle(video.title)
        val titleYearMatch = Regex("""\b(19\d{2}|20\d{2})\b""").find(video.title)
            ?: (if (video.date.isNotEmpty()) Regex("""\b(19\d{2}|20\d{2})\b""").find(video.date) else null)
        val titleYear = titleYearMatch?.groupValues?.get(1)?.toIntOrNull()
        val titleTextWithoutYear = if (titleYearMatch != null) titleRaw.replace(titleYearMatch.value, " ") else titleRaw
        val cleanTitleText = titleTextWithoutYear.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        
        val cleanQ = parsedQuery.cleanText
        if (cleanTitleText.isEmpty() || cleanQ.isEmpty()) return 0

        // Year constraint: if user explicitly typed a release year, candidate MUST match that year
        if (parsedQuery.year != null && titleYear != null && titleYear != parsedQuery.year) {
            return 0 // STRICT REJECTION: Year mismatch when user specified a year
        }

        val titleTokens = cleanTitleText.split(Regex("""\s+""")).filter { it.isNotBlank() }.map { normalizeWord(it) }
        val queryTokens = if (parsedQuery.coreTokens.isNotEmpty()) parsedQuery.coreTokens else parsedQuery.tokens

        val isExactSubstring = cleanTitleText.contains(cleanQ)
        val compactTitle = cleanTitleText.replace(" ", "")
        val compactQ = cleanQ.replace(" ", "")
        val isCompactMatch = compactTitle.contains(compactQ)

        // Token matching
        val matchedTokensCount = queryTokens.count { qTok ->
            titleTokens.any { tTok ->
                tTok == qTok || tTok.startsWith(qTok) || (qTok.length >= 4 && (tTok.contains(qTok) || (levenshteinDistance(qTok, tTok) <= (if (qTok.length >= 8) 2 else 1))))
            }
        }
        val allTokensMatch = queryTokens.isNotEmpty() && matchedTokensCount == queryTokens.size
        val partialTokensMatch = queryTokens.isNotEmpty() && (matchedTokensCount.toDouble() / queryTokens.size) >= 0.5

        if (!isExactSubstring && !isCompactMatch && !allTokensMatch && !partialTokensMatch) {
            return 0 // No match
        }

        var score = 0

        // Exact & Token match ranking tiers
        if (cleanTitleText == cleanQ || compactTitle == compactQ) {
            score += 10000 // Exact full title match
        } else if (cleanTitleText.startsWith(cleanQ) || compactTitle.startsWith(compactQ)) {
            score += 5000 // Exact prefix match
        } else if (isExactSubstring || isCompactMatch) {
            score += 2500 // Substring match
        } else if (allTokensMatch) {
            score += 2000 // All tokens matched in different order
        } else {
            score += (1000 * matchedTokensCount / queryTokens.size.coerceAtLeast(1)) // Partial tokens match
        }

        // Exact year match bonus
        if (parsedQuery.year != null && titleYear == parsedQuery.year) {
            score += 1000
        }

        // Length proximity bonus (prefer closer title length to query)
        val lenDiff = Math.abs(cleanTitleText.length - cleanQ.length)
        score += Math.max(0, 300 - lenDiff * 4)

        // Source priority boost for primary catalog (DutaMovie / PencuriMovie / KepalaBergetar / P.Ramlee)
        val isExternal = video.id.startsWith("yt_") || video.id.startsWith("bili_") || video.id.startsWith("dm_")
        if (!isExternal) {
            score += 500
        }

        // Quality bonus (HD, 1080p, etc.)
        val lowQuality = video.quality.lowercase()
        if (lowQuality.contains("1080") || lowQuality.contains("hd") || lowQuality.contains("bluray")) {
            score += 100
        }

        return score
    }

    fun filterAndSortByRelevance(results: List<Video>, query: String): List<Video> {
        if (results.isEmpty() || query.isBlank()) return results
        val parsedQuery = parseSearchQuery(query)

        val scored = results.map { video ->
            val score = calculateRelevanceScore(video, parsedQuery)
            Pair(video, score)
        }

        val relevant = scored.filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }

        return relevant
    }

    fun sortResultsByRelevance(results: List<Video>, query: String): List<Video> {
        val filtered = filterAndSortByRelevance(results, query)
        return filtered.ifEmpty { results }
    }

    suspend fun searchKepalaBergetar(query: String, page: Int = 1, count: Int = 30): List<Video> = withContext(Dispatchers.IO) {
        val encoded = encodeQuery(query)
        val url = if (page > 1) "https://kepalabergetar9.net/page/$page/?s=$encoded" else "https://kepalabergetar9.net/?s=$encoded"
        try {
            val html = fetchHtml(url) ?: return@withContext emptyList()
            val doc = Jsoup.parse(html, url)
            val items = doc.select("article.item-list, article.post, .post-listing article, .post-box")
            if (items.isEmpty()) return@withContext emptyList()
            
            items.mapNotNull { el ->
                val titleEl = el.selectFirst(".post-box-title a, h2 a, h3 a, .entry-title a") ?: return@mapNotNull null
                val rawTitle = titleEl.text().trim()
                val title = cleanTitle(rawTitle)
                val link = titleEl.attr("abs:href")
                if (title.length < 2 || link.isEmpty()) return@mapNotNull null
                
                val rawImg = el.selectFirst("img")?.let { imgTag ->
                    imgTag.attr("abs:data-src").ifEmpty { imgTag.attr("abs:src") }.ifEmpty { imgTag.attr("src") }
                } ?: ""
                val img = rawImg
                val badge = el.select(".archive-episode-badge, .archive-quality-badge").text().trim()
                val date = el.select(".tie-date, .post-meta").firstOrNull()?.text()?.trim() ?: ""
                
                val slug = link.trimEnd('/').substringAfterLast('/')
                Video(
                    id = "kb_$slug",
                    title = title,
                    thumbnailUrl = img,
                    videoUrl = link,
                    duration = "",
                    quality = badge.ifEmpty { "HD" },
                    date = date,
                    isSeries = link.contains("episod") || rawTitle.contains("Episod", ignoreCase = true) || rawTitle.contains("Episode", ignoreCase = true)
                )
            }.distinctBy { it.id }.take(count)
        } catch (e: Exception) {
            Log.w(TAG, "Kepala Bergetar search error for '$query': ${e.message}")
            emptyList()
        }
    }

    suspend fun searchVideos(query: String, page: Int, count: Int, categoryPath: String? = null): List<Video> = withContext(Dispatchers.IO) {
        if (categoryPath != null) {
            if (categoryPath.contains("country/malaysia", ignoreCase = true)) {
                val encodedQuery = encodeQuery(query)
                val dutaDeferred = async {
                    val url = if (page > 1) "$BASE_URL/country/malaysia/page/$page/?s=$encodedQuery" else "$BASE_URL/country/malaysia/?s=$encodedQuery"
                    fetchHtml(url)?.let { scrapeVideosFromHtml(it, url) } ?: emptyList()
                }
                val pencuriDeferred = async {
                    val pencuriBase = getPencuriBaseUrl()
                    val url = if (page > 1) "$pencuriBase/country/malaysia/page/$page/?s=$encodedQuery" else "$pencuriBase/country/malaysia/?s=$encodedQuery"
                    fetchHtml(url)?.let { scrapeVideosFromHtml(it, url) } ?: emptyList()
                }
                val kepalaDeferred = async { searchKepalaBergetar(query, page, count) }
                val duta = dutaDeferred.await()
                val pencuri = pencuriDeferred.await()
                val kepala = kepalaDeferred.await()
                val merged = mergeAndInterleave(duta, pencuri, count) + kepala
                return@withContext filterAndSortByRelevance(merged.distinctBy { it.id }, query).take(count)
            }

            if (categoryPath.contains("p-ramlee", ignoreCase = true) || categoryPath.contains("FilemP.ramlee", ignoreCase = true)) {
                val all = fetchArchivePramleeVideos()
                val qNorm = query.lowercase().replace("lapuk", "lapok").replace("kaseh", "kasih")
                val matched = all.filter { 
                    val tNorm = it.title.lowercase().replace("lapuk", "lapok").replace("kaseh", "kasih")
                    tNorm.contains(qNorm) || it.title.contains(query, ignoreCase = true)
                }
                val start = (page - 1) * count
                if (start >= matched.size) return@withContext emptyList()
                return@withContext filterAndSortByRelevance(matched.drop(start).take(count), query)
            }

            val results = mutableListOf<Video>()
            val seenIds = mutableSetOf<String>()
            var currentPage = page
            val maxPages = page + 1
            val encodedQuery = encodeQuery(query)

            while (results.size < count && currentPage <= maxPages) {
                val url = if (categoryPath.startsWith("http")) {
                    val cleanCat = categoryPath.removeSuffix("/")
                    if (currentPage > 1) "$cleanCat/page/$currentPage/?s=$encodedQuery" else "$cleanCat/?s=$encodedQuery"
                } else {
                    if (currentPage > 1) "$BASE_URL$categoryPath/page/$currentPage/?s=$encodedQuery" else "$BASE_URL$categoryPath/?s=$encodedQuery"
                }
                Log.d(TAG, "Searching Category Page $currentPage: $url")
                val html = fetchHtml(url) ?: break
                val scraped = scrapeVideosFromHtml(html, url)
                if (scraped.isEmpty()) break

                scraped.forEach { video ->
                    if (seenIds.add(video.id)) {
                        results.add(video)
                    }
                }

                if (results.size >= 20 || scraped.size < 5) break
                currentPage++
            }
            return@withContext filterAndSortByRelevance(results, query).take(count)
        }

        // GLOBAL SEARCH: Concurrently query all 6 platforms simultaneously:
        // DutaMovie, PencuriMovie, KepalaBergetar, P.Ramlee Archive, YouTube, Bilibili, and Dailymotion
        val dutaDeferred = async { searchDomain(BASE_URL, query, page, count) }
        val pencuriDeferred = async { searchDomain(getPencuriBaseUrl(), query, page, count) }
        val kepalaDeferred = async { searchKepalaBergetar(query, page, count) }
        val pramleeDeferred = async {
            try {
                val qNorm = query.lowercase().replace("lapuk", "lapok").replace("kaseh", "kasih")
                fetchArchivePramleeVideos().filter { 
                    val tNorm = it.title.lowercase().replace("lapuk", "lapok").replace("kaseh", "kasih")
                    tNorm.contains(qNorm) || it.title.contains(query, ignoreCase = true)
                }
            } catch (_: Exception) { emptyList() }
        }
        val ytDeferred = async { if (page == 1) searchYouTubeAsVideos(query) else emptyList() }
        val biliDeferred = async { if (page == 1) searchBilibiliAsVideos(query) else emptyList() }
        val dmDeferred = async { if (page == 1) searchDailymotionAsVideos(query) else emptyList() }

        var dutaResults = dutaDeferred.await()
        var pencuriResults = pencuriDeferred.await()
        val kepalaResults = kepalaDeferred.await()
        val pramleeResults = pramleeDeferred.await()
        val ytResults = ytDeferred.await()
        val biliResults = biliDeferred.await()
        val dmResults = dmDeferred.await()

        // Smart query fallback: If full multi-word query returned 0 on primary catalogs,
        // retry searching with core tokens (strip stop words / punctuation)
        val parsed = parseSearchQuery(query)
        if (dutaResults.isEmpty() && pencuriResults.isEmpty() && parsed.coreTokens.size in 1..4) {
            val coreQuery = parsed.coreTokens.joinToString(" ")
            if (!coreQuery.equals(query.trim(), ignoreCase = true)) {
                Log.d(TAG, "Smart fallback search retry on core tokens: '$coreQuery'")
                val retryDuta = async { searchDomain(BASE_URL, coreQuery, page, count) }
                val retryPencuri = async { searchDomain(getPencuriBaseUrl(), coreQuery, page, count) }
                dutaResults = retryDuta.await()
                pencuriResults = retryPencuri.await()
            }
        }

        Log.i(TAG, "Simultaneous search completed: Duta=${dutaResults.size}, Pencuri=${pencuriResults.size}, Kepala=${kepalaResults.size}, PRamlee=${pramleeResults.size}, YT=${ytResults.size}, Bili=${biliResults.size}, DM=${dmResults.size}")

        val allMerged = (dutaResults + pencuriResults + kepalaResults + pramleeResults + ytResults + biliResults + dmResults)
            .distinctBy { it.id }

        val relevantResults = filterAndSortByRelevance(allMerged, query)
        return@withContext relevantResults.take(count)
    }

    fun extractStableId(url: String): String {
        val slug = url.substringBefore('?').trimEnd('/').substringAfterLast('/')
        return if (url.contains("pencurimovie", ignoreCase = true)) "pm_$slug" else slug
    }

    fun cleanTitle(title: String): String {
        return title.replace("Permalink ke:", "", ignoreCase = true)
                    .replace("Error (", "(", ignoreCase = true)
                    .replace("Nonton ", "", ignoreCase = true)
                    .replace("Dutamovie21", "", ignoreCase = true)
                    .replace("DutaMovie", "", ignoreCase = true)
                    .replace("Itoshii", "", ignoreCase = true)
                    .replace("Layarkaca21", "", ignoreCase = true)
                    .replace("LK21", "", ignoreCase = true)
                    .replace(Regex("""(?i)\s*(?:Tonton\s+)?Drama\s+(?:Video|Melayu|Online)\s*"""), " ")
                    .replace(Regex("""(?i)\s*Tonton\s+Video\s*"""), " ")
                    .replace(Regex("""(?i)\s*Kepala\s*Bergetar\s*"""), " ")
                    .replace(Regex("""(?i)^\[(YouTube|Bilibili|Dailymotion|Archive\.org)\]\s*"""), "")
                    .replace(Regex("(?i)(?:web-dl|webdl|web-rip|webrip|1080p|720p|480p|360p|hdcam|hd-cam|camrip|cam-rip|bluray|blu-ray|hdrip|hd-rip)\\s*"), "")
                    .replace(Regex("""\s+"""), " ")
                    .trim()
    }

    fun scrapeVideosFromHtml(html: String, baseUrl: String): List<Video> {
        val doc = Jsoup.parse(html, baseUrl)
        
        // Use a more optimized selector path for high-performance parsing
        val mainContent = doc.selectFirst("#archive-content, .items, .movies-list, #main-content, .post-listing, #gmr-main-load, .archive-container, .grid-container, .list-container, main, #primary, #content, .site-content") ?: doc
        
        val items = mainContent.select(".ml-item, article.item, .post-item, .item-movie, .movie-item, .post-entry, .gmr-item, .movie-post, .item, .post, .T-Post, .movie-list-item, .post-box, article.post, article.item-list, .item-list, [id^='post-'], .grid-item")
        
        if (items.isEmpty()) return emptyList()

        return items.asSequence().mapNotNull { el ->
            // Skip sidebar/footer items quickly
            val isBadContainer = el.parents().any { 
                val cn = it.className(); val id = it.id(); val tag = it.tagName()
                cn.contains("sidebar") || id.contains("sidebar") || tag == "aside" || cn.contains("footer") || cn.contains("widget")
            }
            if (isBadContainer) return@mapNotNull null

            var titleEl = el.selectFirst(".post-box-title, .entry-title, h2, h3, .title, .title-movie")
            var rawTitle = titleEl?.text()?.trim() ?: ""
            
            if (rawTitle.isEmpty()) {
                val aEl = el.selectFirst("a[oldtitle], a[title]")
                rawTitle = aEl?.attr("oldtitle")?.takeIf { it.isNotEmpty() }
                    ?: aEl?.attr("title")?.takeIf { it.isNotEmpty() }
                    ?: aEl?.text()?.trim()
                    ?: ""
            }
            
            val title = cleanTitle(rawTitle)
            
            // Filter out junk titles or broken scrape results
            if (title.isEmpty() || title.lowercase() == "error" || title.length < 2) return@mapNotNull null

            val link = el.selectFirst(".post-box-title a, .entry-title a, h2 a, h3 a, a")?.attr("abs:href") ?: ""
            
            if (title.isNotEmpty() && link.isNotEmpty() && 
                !link.contains("/genre/") && !link.contains("/year/") && 
                !link.contains("/actor/") && !link.contains("/category/") &&
                !link.contains("/tag/") && !link.contains("/author/") &&
                !link.contains("facebook.com") && !link.contains("twitter.com")) {
                
                val rawImg = el.selectFirst("img")?.let { imgTag ->
                    val srcset = imgTag.attr("srcset").ifEmpty { imgTag.attr("data-srcset") }
                    if (srcset.isNotEmpty()) {
                        val candidates = srcset.split(",").mapNotNull { part ->
                            val tokens = part.trim().split(Regex("""\s+"""))
                            if (tokens.isNotEmpty()) {
                                val u = tokens[0]
                                val w = tokens.getOrNull(1)?.removeSuffix("w")?.toIntOrNull() ?: 0
                                Pair(w, u)
                            } else null
                        }
                        val best = candidates.filter { it.first in 240..500 }.maxByOrNull { it.first }?.second
                            ?: candidates.filter { it.first <= 600 }.maxByOrNull { it.first }?.second
                            ?: candidates.minByOrNull { it.first }?.second
                        if (!best.isNullOrEmpty()) best
                        else imgTag.attr("abs:data-src").ifEmpty { imgTag.attr("abs:src") }.ifEmpty { imgTag.attr("abs:data-original") }.ifEmpty { imgTag.attr("src") }
                    } else {
                        imgTag.attr("abs:data-src").ifEmpty { imgTag.attr("abs:src") }.ifEmpty { imgTag.attr("abs:data-original") }.ifEmpty { imgTag.attr("src") }
                    }
                } ?: ""
                val img = rawImg
                
                // OWL'S EYE: Extract quality tag (HD, HDCAM, etc) directly from list view
                val quality = el.select(".quality, .gmr-quality-item, .res, .resolution, .status").text().trim()
                val rawRating = el.select(".imdb-rating, .rating, .score, .gmr-rating-item, .post-ratings").text()
                val rating = Regex("""\d+(?:\.\d+)?""").find(rawRating)?.value ?: ""
                
                Video(id = extractStableId(link), title = title, thumbnailUrl = img, videoUrl = link, duration = "", quality = quality, views = rating)
            } else null
        }.distinctBy { it.id }.toList()
    }

    suspend fun fetchCategories(): List<Map<String, String>> = withContext(Dispatchers.IO) {
        // Aggressively try base and secondary pages for categories to be exhaustive
        val sources = listOf(
            BASE_URL, 
            "$BASE_URL/movie/", 
            "$BASE_URL/tv/", 
            "$BASE_URL/horror/", 
            "$BASE_URL/box-office/", 
            "$BASE_URL/type/movies/", 
            "$BASE_URL/type/series/", 
            "$BASE_URL/network/netflix/", 
            "$BASE_URL/network/disney/",
            "$BASE_URL/genre/",
            "$BASE_URL/country/",
            "$BASE_URL/release/",
            "$BASE_URL/popular/",
            "$BASE_URL/trending/"
        )
        val results = mutableListOf<Map<String, String>>()
        val mutex = kotlinx.coroutines.sync.Mutex()
        
        sources.map { sourceUrl ->
            async {
                try {
                    fetchHtml(sourceUrl)?.let { html ->
                        val doc = Jsoup.parse(html, sourceUrl)
                        // Expanded selectors to catch more menu types, sidebar widgets, and specialized lists
                        val pageResults = mutableListOf<Map<String, String>>()
                        doc.select(".menu-item a, .cat-item a, .category-list a, .genre-list a, #menu-main-menu a, .nav-menu a, .list-category a, .widget_categories a, .wp-block-categories-list a, .footer-menu a, .sub-menu a, .tax-index a, .tag-index a, a[href*='/genre/'], a[href*='/country/'], a[href*='/network/'], a[href*='/type/'], a[href*='/release/'], .genres a, .countries a, .networks a, .years a").forEach {
                            val name = it.text().trim()
                            val href = it.attr("href")
                            val path = try {
                                if (href.startsWith("http")) {
                                    android.net.Uri.parse(href).path ?: ""
                                } else href.substringBefore("?")
                            } catch (e: Exception) { "" }
                            
                            if (name.isNotEmpty() && path.isNotEmpty() && path != "/" && !path.contains("javascript") && !path.contains("mailto:")) {
                                val lowName = name.lowercase()
                                val normalizedPath = normalizePath(path)
                                val lowPath = normalizedPath.lowercase()
                                
                                // Advanced filtering to remove noise (social, pagination, generic terms, utility links)
                                val isNoise = lowName.contains("facebook") || lowName.contains("twitter") || 
                                              lowName.contains("instagram") || lowName.contains("youtube") ||
                                              lowName.contains("contact") || lowName.contains("dmca") || 
                                              lowName.contains("about") || lowPath.contains("login") || 
                                              lowPath.contains("register") || lowPath.contains("terms") ||
                                              lowName.contains("policy") || lowName.contains("copyright") ||
                                              lowName.contains("home") || lowPath == "/movie/" || lowPath == "/tv/" ||
                                              lowPath == "/genre/" || lowPath == "/country/" ||
                                              lowName.contains("page") || lowName.contains("next") || 
                                              lowName.contains("prev") || lowName.contains("login") ||
                                              lowName == "movie" || lowName == "movies" || 
                                              lowName == "tv series" || lowName == "series" ||
                                              lowName == "genre" || lowName.contains("iklan") || 
                                              lowName.contains("film lainnya") || lowName.all { it.isDigit() && it.toString().length < 4 } ||
                                              lowPath.contains("/iklan/") || lowPath.contains("bokep") ||
                                              lowPath.contains("semi") || lowPath.contains("dm21") ||
                                              name.length <= 1 || name.length > 40
                                
                                if (!isNoise) {
                                    pageResults.add(mapOf("name" to name, "path" to normalizedPath))
                                }
                            }
                        }
                        mutex.withLock {
                            results.addAll(pageResults)
                        }
                    }
                } catch (_: Exception) {}
            }
        }.awaitAll()
        
        // Prefer root-level genre paths (e.g., /horror/) over /genre/ paths (e.g., /genre/horror/)
        // because root paths return more items (20 vs 11) and support pagination
        val genreUpgraded = results.map { entry ->
            val path = entry["path"] ?: ""
            if (path.startsWith("/genre/")) {
                val rootPath = path.removePrefix("/genre")
                // Check if root-level version exists in results
                val rootExists = results.any { it["path"] == rootPath }
                if (rootExists) null // Drop this /genre/ entry, root version is better
                else entry.toMutableMap().apply { this["path"] = rootPath } // Upgrade to root path
            } else entry
        }.filterNotNull()

        val sortedResults = genreUpgraded.distinctBy { it["path"] }.sortedBy { it["name"] }.toMutableList()
        if (sortedResults.none { (it["path"] ?: "").contains("country/viet-nam", ignoreCase = true) || (it["path"] ?: "").contains("country/vietnam", ignoreCase = true) }) {
            sortedResults.add(mapOf("name" to "Viet Nam", "path" to "/country/viet-nam/"))
        }
        if (sortedResults.none { (it["path"] ?: "").contains("country/malaysia", ignoreCase = true) }) {
            sortedResults.add(mapOf("name" to "Malaysia", "path" to "/country/malaysia/"))
        }
        if (sortedResults.none { (it["path"] ?: "").contains("p-ramlee", ignoreCase = true) }) {
            sortedResults.add(mapOf("name" to "P.Ramlee", "path" to "/category/p-ramlee/"))
        }
        
        // Final fallback if nothing found (safety net)
        if (sortedResults.isEmpty()) {
            return@withContext listOf(
                mapOf("name" to "Update Film Terbaru", "path" to "/movie/"),
                mapOf("name" to "Serial TV Terbaru", "path" to "/serial-tv-terbaru/"),
                mapOf("name" to "Viet Nam", "path" to "/country/viet-nam/"),
                mapOf("name" to "Box-Office", "path" to "/box-office/"),
                mapOf("name" to "Anime", "path" to "/animasi/"),
                mapOf("name" to "Action", "path" to "/action/"),
                mapOf("name" to "Comedy", "path" to "/comedy/"),
                mapOf("name" to "Drama", "path" to "/drama/"),
                mapOf("name" to "Horror", "path" to "/horror/"),
                mapOf("name" to "Thriller", "path" to "/thriller/"),
                mapOf("name" to "Romance", "path" to "/romance/"),
                mapOf("name" to "Indonesia", "path" to "/country/indonesia/"),
                mapOf("name" to "Korea", "path" to "/country/korea/"),
                mapOf("name" to "Thailand", "path" to "/country/thailand/"),
                mapOf("name" to "Malaysia", "path" to "/country/malaysia/"),
                mapOf("name" to "P.Ramlee", "path" to "/category/p-ramlee/")
            )
        }

        sortedResults
    }

    fun fetchAllActresses(): List<Map<String, String>> = emptyList()
    
    private fun getHighResImage(url: String): String {
        if (url.isEmpty()) return ""
        return VideoUtils.getOriginalImage(url)
    }

    suspend fun fetchActressProfile(actressPath: String): Map<String, String> = withContext(Dispatchers.IO) {
        val fullUrl = if (actressPath.startsWith("http")) actressPath else "$BASE_URL$actressPath"
        val html = fetchHtml(fullUrl) ?: return@withContext emptyMap()
        val doc = Jsoup.parse(html, fullUrl)
        val data = mutableMapOf<String, String>()
        data["name"] = doc.select("h1, .entry-title, .name").firstOrNull()?.text() ?: ""
        data["image"] = getHighResImage(doc.select(".person-poster img, .actress-photo img, .wp-post-image").firstOrNull()?.attr("abs:src") ?: "")
        data["bio"] = doc.select(".person-bio, .entry-content p, .description p").firstOrNull()?.text() ?: ""
        data["metadata"] = doc.select(".person-info li, .actress-details li").map { it.text() }.joinToString(",")
        return@withContext data
    }

    private fun cleanDescription(desc: String): String {
        if (desc.isEmpty()) return ""
        var d = desc
        val seoPatterns = listOf(
            Regex("""(?i)Nonton\s+.*?\s+Sub\s+Indo\s+hanya\s+di\s+.*?(\.|\b)"""),
            Regex("""(?i)tempatnya\s+nonton\s+film\s+LK21,.*?\."""),
            Regex("""(?i)lengkap\s+dengan\s+subtitle\s+indonesia\."""),
            Regex("""(?i)subtitle\s+indonesia\s+lengkap\s+hanya\s+di\s+.*?(\.|\b)"""),
            Regex("""(?i)kualitas\s+video\s+terbaik\s+hanya\s+di\s+.*?(\.|\b)"""),
            Regex("""(?i)Dutamovie21"""),
            Regex("""(?i)Layarkaca21"""),
            Regex("""(?i)LK21"""),
            Regex("""(?i)Rebahin"""),
            Regex("""(?i)IndoXXI"""),
            Regex("""(?i)Indostream"""),
            Regex("""(?i)Bioskop21"""),
            Regex("""(?i)Dewamovie""")
        )
        seoPatterns.forEach { d = d.replace(it, "") }
        d = d.replace("DMStreaM", "").replace("DutaMovie", "").replace("dutamovie", "").trim()
        val low = d.lowercase()
        if (low.contains("nonton") && (low.contains("sub indo") || low.contains("subtitle")) && d.length < 250) return ""
        if (low.contains("link download") || low.contains("layarkaca")) return ""
        return d.trim()
    }

    fun identifyMirrorName(name: String, url: String): String {
        val lowUrl = url.lowercase()
        val lowName = name.lowercase()
        
        val hgHosts = setOf("hgcloud", "hglink", "hgcdn", "hanerix", "vibuxer", "dhcplay", "masukestin", "audinifer", "duvidun", "fujihide", "distributedcomputing", "harmonixinnovationlab", "ryder", "hglcdn", "movearnpre", "movienu", "movielite", "garylarge", "huntrex", "bestcdn")
        val indoHosts = setOf("indostream", "amt", "iplayer", "masuk.link", "masukin", "listeamed", "rebeccasciencestreet", "morencius", "playstream", "embed4me", "pm21", "dm21", "zeus", "klik", "playerp2p", "faststream")

        val provider = when {
            lowUrl.contains("voe") || lowUrl.contains("johnfullwonder") -> "VOE"
            lowUrl.contains("hglink") -> "HGLink"
            hgHosts.any { lowUrl.contains(it) } -> "Hgcloud-VIP"
            indoHosts.any { lowUrl.contains(it) } -> "IndoStream-VIP"
            lowUrl.contains("abyss") || lowUrl.contains("bond") || lowUrl.contains("iamcdn") -> "Abyss"
            lowUrl.contains("dood") || lowUrl.contains("ds2play") || lowUrl.contains("doodstream") -> "Doodstream"
            lowUrl.contains("streamtape") -> "Streamtape"
            lowUrl.contains("streamwish") || lowUrl.contains("embedwish") || lowUrl.contains("strwish") || lowUrl.contains("wishembed") -> "Streamwish"
            lowUrl.contains("embedo") -> "Embedo"
            lowUrl.contains("streamsilk") -> "Streamsilk"
            lowUrl.contains("waaw") || lowUrl.contains("netu") || lowUrl.contains("hqq") -> "Netu"
            lowUrl.contains("vkspeed") -> "VKSpeed"
            else -> null
        }
        if (provider != null) return provider
        
        if (lowName.contains("server") || lowName.contains("s1") || lowName.contains("s2") || lowName.contains("s3") || lowName.contains("s4") || lowName.length <= 3) {
            val provider = when {
                lowUrl.contains("player=1") || lowUrl.contains("player=4") || lowUrl.contains("player=5") || lowUrl.contains("player=7") || lowUrl.contains("hgcloud") || lowUrl.contains("hgplayer") || lowUrl.contains("dhcplay") -> "Hgcloud"
                lowUrl.contains("player=2") || lowUrl.contains("player=3") || lowUrl.contains("player=6") || lowUrl.contains("player=8") || lowUrl.contains("indostream") || isIndoStreamAmt(lowUrl) -> "IndoStream"
                else -> null
            }
            if (provider != null) {
                val cleanName = name.replace(Regex("(?i)server\\s*"), "S").replace(Regex("(?i)mirror\\s*"), "M").ifEmpty { "S1" }
                return "$provider-VIP ($cleanName)"
            }
        }
        
        if (lowName.isEmpty() || lowName == "server" || lowName == "s1") return "Server 1 (Main)"
        return name
    }

    fun isGenuineMirror(name: String, url: String): Boolean {
        val lowName = name.lowercase()
        val lowUrl = url.lowercase()
        
        // Block known related/recommendation movie paths that might be detected as mirrors
        if (lowUrl.contains("/movie/") || lowUrl.contains("/tv/") || lowUrl.contains("/horror/") || lowUrl.contains("/action/")) {
             // If it has 'player=' or 'mirror=', it's a real server button. 
             // Otherwise, if it's just a clean movie URL, it's a related movie, not a mirror.
             if (!lowUrl.contains("player=") && !lowUrl.contains("mirror=") && !lowUrl.contains("action=")) {
                  return false
             }
        }

        // Efficiency: High-level ad domain blocking
        val adDomains = listOf(
            "zeus", "klik", "vingaming", "pingaming", "chiptaylor", "ketik.live",
            "poker", "slot", "bet", "jud", "bola", "win", "88", "138", "jackpot",
            "qpon", "butynejutes", "parklogic", "embedo"
        )
        val host = try { android.net.Uri.parse(url).host?.lowercase() ?: "" } catch(_: Exception) { "" }
        if (adDomains.any { host.contains(it) || lowName.contains(it) }) return false
        
        if (lowUrl.contains("youtube") || lowUrl.contains("trailer") || lowUrl.contains("preview") || 
            lowUrl.contains("google.com") || lowUrl.contains("googleapis.com") || lowUrl.contains("imasdk")) return false
        
        if (lowName.contains("server") || lowName.contains("mirror") || lowName.contains("vip") || 
            lowName.contains("s1") || lowName.contains("s2") || lowName.contains("s3") || lowName.contains("s4")) {
            if (host.contains("parklogic") || host.contains("embedo")) return false
            return true
        }

        return lowName.contains("hgcloud") || 
               lowName.contains("indostream") ||
               lowName.contains("voe") || 
               lowName.contains("abyss") || lowName.contains("dood") || 
               lowName.contains("streamtape") || lowName.contains("playstream") ||
               lowName.contains("vidplay") || lowName.contains("mycloud") ||
               lowName.contains("vidhide") || lowName.contains("veev") ||
               lowName.contains("hexload") || lowName.contains("filemoon") ||
               lowName.contains("acefile") || lowName.contains("racaty") ||
               lowName.contains("bilibili") || lowUrl.contains("bilibili.com") ||
               lowName.contains("dailymotion") || lowUrl.contains("dailymotion.com") || lowUrl.contains("dai.ly") ||
               lowName.contains("streamwish") || lowUrl.contains("streamwish") || lowUrl.contains("embedwish") || lowUrl.contains("streamsilk") ||
               lowUrl.contains("player=") || lowUrl.startsWith("ajax:") ||
               lowUrl.contains("m3u8") || lowUrl.contains("mp4") || lowUrl.contains("/e/") || lowUrl.contains("/v/") ||
               lowUrl.contains("/amt/") || lowUrl.contains(".amt")
    }

    suspend fun fetchVideoDetails(videoUrl: String, referer: String? = null, isRecursive: Boolean = false): Video? = withContext(Dispatchers.IO) {
        if (videoUrl.contains("archive.org") || videoUrl.startsWith("ia_pramlee")) {
            val cached = archivePramleeCache?.find { 
                it.videoUrl == videoUrl || 
                it.id == videoUrl ||
                (videoUrl.contains('/') && it.videoUrl.contains(videoUrl.substringAfterLast('/'))) ||
                it.servers.any { s -> s.url == videoUrl }
            }
            if (cached != null) return@withContext cached

            val all = fetchArchivePramleeVideos()
            val found = all.find { 
                it.videoUrl == videoUrl || 
                it.id == videoUrl ||
                (videoUrl.contains('/') && it.videoUrl.contains(videoUrl.substringAfterLast('/'))) ||
                it.servers.any { s -> s.url == videoUrl }
            }
            if (found != null) return@withContext found
        }

        var effectiveUrl = migrateUrlToBase(videoUrl)
        var html = fetchHtml(effectiveUrl, referer)
        if (html == null) {
            val isPencuri = isPencuriMovie(videoUrl = videoUrl)
            if (isPencuri) {
                val liveDomain = probePencuriDomain()
                if (liveDomain != null) {
                    effectiveUrl = migrateUrlToBase(videoUrl)
                    html = fetchHtml(effectiveUrl, referer)
                }
            } else if (effectiveUrl.contains("kepalabergetar") || effectiveUrl.contains("archive.org") || 
                       effectiveUrl.contains("youtube") || effectiveUrl.contains("bilibili") || 
                       effectiveUrl.contains("dailymotion")) {
                // External provider failed or 404 - do not trigger DutaMovie domain probe
                return@withContext null
            } else {
                Log.w(TAG, "fetchVideoDetails failed on $effectiveUrl. Probing for live DutaMovie domain...")
                val liveDomain = probeForNewDomain()
                if (liveDomain != null && !effectiveUrl.startsWith(liveDomain)) {
                    effectiveUrl = migrateUrlToBase(videoUrl)
                    Log.i(TAG, "Retrying video details with live domain: $effectiveUrl")
                    html = fetchHtml(effectiveUrl, referer)
                }
            }
        }
        if (html == null) return@withContext null
        val doc = Jsoup.parse(html, effectiveUrl)
        val rawTitle = doc.select(".entry-title, h1").firstOrNull()?.text() ?: "Unknown"
        val title = cleanTitle(rawTitle)
        
        val poster = getHighResImage(doc.select("meta[property=\"og:image\"], .poster img").firstOrNull()?.let { it.attr("content").ifEmpty { it.attr("abs:src") } } ?: "")
        val backdrop = getHighResImage(doc.select(".backdrop img, #background img").firstOrNull()?.attr("abs:src") ?: "")
        
        val descriptions = doc.select(".synopsis p, .description p, .entry-content p, .desc p, .synopsis, .description, .plot, #muvipro_player_content_id p, article.item p")
        var bestDesc = ""
        for (p in descriptions) {
            val t = cleanDescription(p.text())
            if (t.length > 40) {
                bestDesc = t; break
            }
        }
        if (bestDesc.isEmpty()) {
            val metaDesc = doc.select("meta[property=\"og:description\"]").firstOrNull()?.attr("content") ?: ""
            bestDesc = cleanDescription(metaDesc)
        }
        
        val actresses = mutableListOf<String>()
        val actressPaths = mutableMapOf<String, String>()
        val actressImages = mutableMapOf<String, String>()
        doc.select(".mvp-actor-list a, .cast-item a, a[href*='/actor/'], .cast-list a").forEach { 
            val name = it.text().trim()
            if (name.isNotEmpty()) {
                actresses.add(name)
                actressPaths[name] = it.attr("href")
                it.parent()?.selectFirst("img")?.let { img -> actressImages[name] = getHighResImage(img.attr("abs:src")) }
            }
        }

        val rawServers = mutableListOf<VideoServer>()
        
        // OWL'S EYE: High-accuracy mirror detection
        val serverContainers = doc.select(".muvipro-player-tabs, .player-tabs, .gmr-player-nav, .gmr-server-wrap, #player-option-1, #player-option-2, #player-option-3, .server-list, .list-server, .source-box, .sources-list, .mirror-list, .list-server-items, .server-wrap, .player-options")
        
        val elements = if (serverContainers.isNotEmpty()) {
            serverContainers.select("a, li, span, [data-post][data-n], [data-index], .do-player-option, .server-item, .gmr-player-option, .btn-server, .source, .mirror")
        } else {
            // Fallback to broader scan if specialized containers not found
            doc.select(".do-player-option, .server-item, .source-box, a:contains(Server), [data-post][data-n], [data-index], .player-option, .gmr-player-option, .btn-server, .source-box a, .mirror-item, a[data-url], a[data-link], .server a, .mirror a")
        }

        Log.d(TAG, "Scanning servers/trailers for: $videoUrl - Found ${elements.size} potential buttons")

        // Identify the "Active" button (default server) to prevent duplication
        val activeElement = elements.find { it.hasClass("active") || it.parent()?.hasClass("active") == true || it.attr("class").contains("active") } ?: elements.firstOrNull()
        val activeServerName = activeElement?.let { 
            val t = it.text().trim()
            if (t.isNotEmpty() && t.length < 20) t else "Server 1"
        } ?: "Server 1"
        
        var previewUrl = ""
        
        // 1. Explicit Content Area Strategy (Old Build Logic)
        val contentArea = doc.select("article, #primary, .main-content, #content, .post-entry, .video-info")
            .firstOrNull { it.select(".related-movies, .recommendations, #sidebar, .sidebar").isEmpty() }
            ?: doc.select("article, #primary, .main-content, #content").firstOrNull()
            ?: doc

        val explicitTrailer = contentArea.select("a.gmr-trailer-popup, a.gmr-trailer, a.trailer, .trailer-link, .trailer, [class*=\"trailer\"], .do-player-option[data-type=\"trailer\"], [id*=\"trailer\"]")
            .firstOrNull { el ->
                val parent = el.parents()
                if (parent.`is`(".related-movies, .recommendations, .widget_related_posts, #sidebar, .sidebar, .related-posts, .related-items")) return@firstOrNull false
                
                val link = if (el.tagName() == "iframe") el.attr("abs:src") 
                           else if (el.tagName() == "a") el.attr("abs:href").ifEmpty { el.attr("data-url") }.ifEmpty { el.attr("data-link") }
                           else el.attr("data-url").ifEmpty { el.attr("data-link") }.ifEmpty { el.attr("data-src") }
                
                link.contains("youtube.com") || link.contains("youtu.be") || link.contains("vimeo.com") || link.contains(".mp4") || link.contains(".m3u8")
            }?.let {
            if (it.tagName() == "iframe") it.attr("abs:src")
            else if (it.tagName() == "a") it.attr("abs:href").ifEmpty { it.attr("data-url") }.ifEmpty { it.attr("data-link") }
            else {
                it.select("a[href*=\"youtube\"], a[href*=\"youtu.be\"]").firstOrNull()?.attr("abs:href") ?:
                it.select("iframe[src*=\"youtube\"], iframe[src*=\"youtu.be\"]").firstOrNull()?.attr("abs:src") ?:
                it.attr("data-url").ifEmpty { it.attr("data-link") }.ifEmpty { it.attr("data-src") }
            }
        }
        
        if (!explicitTrailer.isNullOrEmpty()) {
            previewUrl = sanitizeUrl(explicitTrailer, videoUrl)
            Log.d(TAG, "Acquired explicit trailer from content area: $previewUrl")
        }
        
        // 1b. Check Player Tabs (Common in Muvipro)
        if (previewUrl.isEmpty()) {
            doc.select(".muvipro-player-tabs li, .player-tabs li").forEach { tab ->
                if (tab.text().lowercase().contains("trailer")) {
                    val tabId = tab.attr("id").replace("tab-", "player-option-")
                    doc.select("#$tabId, .$tabId").firstOrNull()?.let { playerOption ->
                        val link = playerOption.attr("abs:href").ifEmpty { playerOption.attr("data-url") }.ifEmpty { playerOption.attr("data-link") }
                        if (link.contains("youtube") || link.contains("youtu.be")) {
                            previewUrl = link
                            Log.d(TAG, "Acquired trailer from tabs: $previewUrl")
                        }
                    }
                }
            }
        }

        // 2. Meta Tags (Fallback)
        if (previewUrl.isEmpty()) {
            val metaTrailer = doc.select("meta[property*='video'], meta[name*='trailer'], meta[property*='trailer'], meta[property='og:video:url']").firstOrNull()?.attr("content") ?: ""
            if (metaTrailer.contains("youtube.com") || metaTrailer.contains("youtu.be") || metaTrailer.contains("vimeo.com")) {
                previewUrl = metaTrailer
            }
        }

        // 3. Iframe/Script Scan within Content Area
        if (previewUrl.isEmpty()) {
            doc.select("iframe").forEach { iframe ->
                val src = iframe.attr("abs:src").ifEmpty { iframe.attr("abs:data-src") }
                if (src.contains("youtube.com") || src.contains("youtu.be") || src.contains("vimeo.com")) {
                    if (!src.contains("player=") && !src.contains("player-option") && !src.contains("post=")) {
                        previewUrl = src
                        return@forEach
                    }
                }
            }
        }
        
        // 3b. Search for trailer buttons/links specifically
        if (previewUrl.isEmpty()) {
            val trailerSelectors = ".trailer-link, .btn-trailer, .view-trailer, .muvipro-trailer, .muvipro-trailer-button, .trailer-content, #trailer, #tab-trailer, .play-trailer, a:contains(Trailer), a:contains(trailer), .trailer-area, .video-trailer"
            doc.select(trailerSelectors).forEach { el ->
                var link = el.attr("abs:href").ifEmpty { el.attr("data-url") }.ifEmpty { el.attr("data-link") }.ifEmpty { el.attr("href") }
                if (link.isEmpty() || (!link.contains("youtube") && !link.contains("youtu.be"))) {
                    val childLink = el.select("a[href*='youtube'], a[href*='youtu.be'], iframe[src*='youtube']").firstOrNull()
                    if (childLink != null) {
                        link = childLink.attr("abs:href").ifEmpty { childLink.attr("abs:src") }
                    }
                }
                if (link.contains("youtube.com") || link.contains("youtu.be") || link.contains("vimeo.com")) {
                    previewUrl = link
                    Log.d(TAG, "Found trailer in specific selectors: $previewUrl")
                    return@forEach
                }
            }
        }

        // 4. Global HTML Script/String Search (Aggressive)
        if (previewUrl.isEmpty()) {
            val youtubeRegex = Regex("""(?:https?:)?//(?:www\.)?(?:youtube\.com/(?:watch\?v=|embed/)|youtu\.be/)([a-zA-Z0-9_-]{11})""")
            val vimeoRegex = Regex("""(?:https?:)?//(?:www\.)?vimeo\.com/(\d+)""")
            
            doc.select("script").forEach { script ->
                val data = script.data()
                youtubeRegex.find(data)?.let { previewUrl = it.value }
                if (previewUrl.isEmpty()) vimeoRegex.find(data)?.let { previewUrl = it.value }
                
                if (previewUrl.isEmpty()) {
                    val mTrailer = Regex("""["']?(?:trailer|preview|youtube)_?(?:url|id|link)["']?\s*[:=]\s*["']([^"']+)["']""").find(data)
                    mTrailer?.let { 
                        val found = it.groupValues[1]
                        previewUrl = if (found.contains("http")) found else "https://www.youtube.com/watch?v=$found"
                    }
                }
                if (previewUrl.isNotEmpty()) return@forEach
            }
        }

        for (el in elements) {
            var link = el.attr("abs:href").ifEmpty { el.attr("data-link") }.ifEmpty { el.attr("data-url") }.ifEmpty { el.attr("abs:src") }.ifEmpty { el.attr("href") }
            if (link.isEmpty()) continue
            
            // Extract trailer if YouTube/Vimeo (if not already found)
            if (link.contains("youtube.com") || link.contains("youtu.be") || link.contains("vimeo.com")) {
                if (previewUrl.isEmpty()) {
                    previewUrl = link
                    Log.d(TAG, "Found trailer in elements: $previewUrl")
                }
                continue // Don't add youtube as a mirror server
            }

            var rawName = el.text().trim().ifEmpty { el.attr("data-name") }.ifEmpty { "Server" }
            
            val postId = el.attr("data-post").ifEmpty { el.attr("data-id") }.ifEmpty { el.attr("data-post-id") }
            val n = el.attr("data-n").ifEmpty { el.attr("data-server") }.ifEmpty { el.attr("data-index") }
            val type = el.attr("data-type").ifEmpty { "movie" }
            
            if (postId.isNotEmpty() && n.isNotEmpty()) {
                val resolved = resolveAjaxServer(videoUrl, postId, n, type, videoUrl)
                if (resolved != null) {
                    link = resolved
                    Log.d(TAG, "Resolved AJAX server: $rawName -> $link")
                } else if (link.isEmpty() || link.startsWith("#")) {
                    link = "ajax:$postId:$n:$type"
                }
            }
            
            val isSelf = link == videoUrl || link == videoUrl.removeSuffix("/") || link == "$videoUrl/"
            
            // Refinement: If it's a self-link (placeholder tab), skip adding it as a server button.
            // We will extract the actual player from the iframe inside this page later.
            if (isSelf && !link.contains("player=") && !link.contains("mirror=")) {
                continue
            }
            
            val hasPlayerParams = link.contains("player=") || link.contains("mirror=") || link.startsWith("ajax:")
            
            if (link.isNotEmpty() && !link.contains("javascript") && (!isSelf || hasPlayerParams)) {
                if (isProbablyVideoHost(link) || hasPlayerParams) {
                    if (isGenuineMirror(rawName, link)) {
                        val beautifulName = identifyMirrorName(rawName, link)
                        Log.d(TAG, "Adding server from button: $beautifulName -> $link")
                        rawServers.add(VideoServer(beautifulName, link))
                    }
                }
            }
        }

        // 2b. PencuriMovie Tabbed Players Scan (.player_nav, .idTabs, #player2)
        val pmTabs = doc.select(".player_nav li, .idTabs li")
        if (pmTabs.isNotEmpty()) {
            pmTabs.forEach { li ->
                val serverName = li.select(".les-title strong").text().trim().ifEmpty { "Server" }
                val tabHref = li.select(".les-content a").attr("href").trim().removePrefix("#")
                if (tabHref.isNotEmpty()) {
                    val tabContainer = doc.select("#$tabHref")
                    val iframe = tabContainer.select("iframe").firstOrNull()
                    val src = iframe?.attr("abs:src")?.ifEmpty { iframe.attr("abs:data-src") }?.ifEmpty { iframe.attr("data-src") }?.ifEmpty { iframe.attr("src") } ?: ""
                    if (src.isNotEmpty() && isProbablyVideoHost(src)) {
                        val beautifulName = identifyMirrorName(serverName, src)
                        Log.d(TAG, "Adding PencuriMovie server from tab $tabHref: $beautifulName -> $src")
                        rawServers.add(VideoServer(beautifulName, src))
                    }
                }
            }
        }

        // 3. Iframe/Object Scan (Real mirrors often auto-load in an iframe below the video area)
        val videoArea = doc.select(".gmr-pagi-player, .player-wrap, .video-player, #player, #player2, .movieplay, .embed-responsive, .muvipro-player-wrap")
        val iframesToScan = if (videoArea.isNotEmpty() && videoArea.select("iframe, embed").isNotEmpty()) videoArea.select("iframe, embed") else doc.select("iframe, embed")

        iframesToScan.forEach { iframe ->
            val src = iframe.attr("abs:src").ifEmpty { iframe.attr("abs:data-src") }.ifEmpty { iframe.attr("data-src") }.ifEmpty { iframe.attr("abs:data") }
            if (src.isNotEmpty() && !src.contains("ads") && isProbablyVideoHost(src)) {
                if (!src.contains("youtube") && !src.contains("youtu.be")) {
                    // Refinement: If this iframe is the auto-loaded player for the default button,
                    // use the site's original button label (e.g. "Server 1") for consistency.
                    val beautifulName = identifyMirrorName(activeServerName, src)
                    Log.d(TAG, "Adding server from iframe: $beautifulName -> $src")
                    rawServers.add(VideoServer(beautifulName, src))
                }
            }
        }

        // 3b. Kepala Bergetar & Netu/HQQ Hex-Encoded Player Div Scan
        doc.select("div[id]").forEach { div ->
            val id = div.id().trim()
            if (id.length >= 20 && id.length % 3 == 0 && id.all { it in "0123456789abcdefABCDEF" }) {
                try {
                    val sb = StringBuilder()
                    for (i in 0 until id.length step 3) {
                        val hex = id.substring(i, i + 3)
                        sb.append(hex.toInt(16).toChar())
                    }
                    val decoded = sb.toString()
                    val vidMatch = Regex("""["']?v["']?\s*:\s*["']([^"']+)["']""").find(decoded)
                    if (vidMatch != null) {
                        val vid = vidMatch.groupValues[1]
                        val netuUrl = "https://waaw.to/e/$vid"
                        Log.d(TAG, "Decoded Kepala Bergetar hex div: $vid -> $netuUrl")
                        rawServers.add(VideoServer("Netu", netuUrl))
                        rawServers.add(VideoServer("HQQ", "https://hqq.tv/player/embed_player.php?vid=$vid"))
                    }
                } catch (_: Exception) {}
            }
        }

        val isPmSource = isPencuriMovie(videoUrl = videoUrl)
        val sortedRawServers = if (isPmSource) {
            rawServers.distinctBy { it.url }.sortedWith(Comparator { a, b ->
                fun serverPriority(url: String): Int {
                    val low = url.lowercase()
                    return when {
                        low.contains("voe") || low.contains("johnfullwonder") -> 0
                        low.contains("listeamed") || low.contains("indostream") || low.contains("audinifer") -> 1
                        low.contains("hglink") || low.contains("hgcloud") -> 2
                        else -> 3
                    }
                }
                serverPriority(a.url).compareTo(serverPriority(b.url))
            })
        } else {
            rawServers.distinctBy { it.url }
        }

        val finalServers = mutableListOf<VideoServer>()
        val nameCounts = mutableMapOf<String, Int>()
        sortedRawServers.forEach { s ->
            val name = s.name
            val count = nameCounts.getOrDefault(name, 0) + 1
            nameCounts[name] = count
            val finalName = if (count > 1) "$name $count" else name
            finalServers.add(s.copy(name = finalName))
        }

        val isExplicitSeries = videoUrl.contains("/series/") || videoUrl.contains("/tv/") || videoUrl.contains("/serial-tv/")
        val hasEpisodeContainer = doc.select(".gmr-listseries, .muvipro-listepisode, .list-episode, .episodios, .eps-item, .list-eps, .list-series, .seasons, .season, [class*='listseries'], .episode-list-container, .episodes-grid, #seasons, #season, .tvseason, .les-content, [id*='season']").isNotEmpty()
        val isEpisodePage = videoUrl.contains("/eps/") || videoUrl.contains("/episode/") || videoUrl.contains("-episode-") ||
                            videoUrl.contains("/episod/") || videoUrl.contains("-episod-") || videoUrl.contains("-epi-")
        
        val rawSlug = extractStableId(videoUrl).removePrefix("pm_")
        val seriesSlug = rawSlug
            .replace(Regex("""[-_](?:episod[e]?|eps|ep)[-_]\d+.*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[-_]s(?:eason)?[-_]?\d+[-_]ep(?:isod[e]?)?[-_]?\d+.*""", RegexOption.IGNORE_CASE), "")
            .trimEnd('-')
        val cleanSlugBase = seriesSlug.replace(Regex("""[-_]s(?:eason)?[-_]?\d+""", RegexOption.IGNORE_CASE), "").lowercase()
        val cleanSlugNoYear = cleanSlugBase.replace(Regex("""[-_]?(?:\(|\[)?(?:19|20)\d{2}(?:\)|\])?$"""), "")

        // Extract episodes unconditionally for any title that may contain episodes
        var rawEpisodes = extractEpisodesFromDoc(doc, videoUrl, title)
        val targetSeasonNum = Regex("""(?i)\b(?:season|s)[-_ ]?(\d+)\b""").find(videoUrl)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("""(?i)\b(?:season|s)\s*(\d+)\b""").find(title)?.groupValues?.get(1)?.toIntOrNull()

        // Auto-Healing: If explicit series is missing episodes or page contained mismatched season episodes
        if (rawEpisodes.isEmpty() && isExplicitSeries && !isEpisodePage && !isRecursive) {
            val searchDomain = try { 
                val uri = android.net.Uri.parse(videoUrl)
                "${uri.scheme}://${uri.host}"
            } catch(_: Exception) { getBaseUrl() }
            val queryText = if (targetSeasonNum != null) {
                "${title.replace(Regex("""\s*\(\d{4}\)"""), "").replace(Regex("""(?i)\s*season\s*\d+"""), "")} Season $targetSeasonNum"
            } else {
                title.replace(Regex("""\s*\(\d{4}\)"""), "")
            }
            try {
                val searchUrl = "$searchDomain/?s=${java.net.URLEncoder.encode(queryText.trim(), "UTF-8")}"
                val searchHtml = fetchHtml(searchUrl, videoUrl)
                if (searchHtml != null) {
                    val searchDoc = org.jsoup.Jsoup.parse(searchHtml, searchDomain)
                    val healedEpisodes = mutableListOf<Episode>()
                    searchDoc.select("a").forEach { a ->
                        val href = a.attr("abs:href")
                        val low = href.lowercase()
                        if (low.contains("/eps/") || low.contains("/episode/")) {
                            val cleanEpUrl = href.substringBefore('?')
                            val matchesSlug = cleanSlugNoYear.length >= 3 && low.contains(cleanSlugNoYear)
                            val epSeason = Regex("""(?i)season-(\d+)""").find(low)?.groupValues?.get(1)?.toIntOrNull()
                            val seasonMatches = targetSeasonNum == null || epSeason == null || epSeason == targetSeasonNum
                            if (matchesSlug && seasonMatches) {
                                val epNum = Regex("""(?i)episode-(\d+)""").find(low)?.groupValues?.get(1)
                                val epName = cleanEpisodeTitle(if (epNum != null) "Episode $epNum" else a.text().trim().ifEmpty { "Episode" }, title, videoUrl)
                                val sLabel = if (targetSeasonNum != null) "Season $targetSeasonNum" else ""
                                healedEpisodes.add(Episode(id = extractStableId(cleanEpUrl), name = epName, url = cleanEpUrl, season = sLabel))
                            }
                        }
                    }
                    if (healedEpisodes.isNotEmpty()) {
                        rawEpisodes = healedEpisodes.distinctBy { it.url }.sortedWith(compareBy({ 
                            it.season.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0 
                        }, {
                            Regex("""(?i)\b(?:episod[e]?|eps|ep)\s*(\d+)\b""").find(it.name)?.groupValues?.get(1)?.toIntOrNull()
                                ?: Regex("""(?i)[-_](?:episod[e]?|eps|ep)[-_](\d+)""").find(it.url)?.groupValues?.get(1)?.toIntOrNull()
                                ?: 0
                        }))
                        Log.i(TAG, "Auto-healed ${rawEpisodes.size} episodes for series: $title (Season $targetSeasonNum)")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Episode auto-healing search failed: ${e.message}")
            }
        }

        val finalIsSeries = rawEpisodes.isNotEmpty() || isExplicitSeries || (hasEpisodeContainer && !videoUrl.contains("/movie/"))
        val episodes = if (finalIsSeries) rawEpisodes else emptyList()

        // OWL'S EYE: Speculative Probing (v5.9)
        var discoveredServers = finalServers
        if (finalIsSeries && discoveredServers.isEmpty() && episodes.isNotEmpty() && !isRecursive && !isEpisodePage) {
            val firstEp = episodes.first()
            val cleanSeriesSlug = seriesSlug.replace(Regex("""[-_]s(?:eason)?[-_]?\d+""", RegexOption.IGNORE_CASE), "")
            val isSafeProbe = cleanSeriesSlug.isEmpty() || 
                              firstEp.url.contains(cleanSeriesSlug, ignoreCase = true) || 
                              cleanSeriesSlug.contains(extractStableId(firstEp.url)) ||
                              (cleanSlugNoYear.length >= 3 && firstEp.url.contains(cleanSlugNoYear, ignoreCase = true))
            if (isSafeProbe) {
                Log.d(TAG, "Series mirrors missing. Speculative Probe engaged for: ${firstEp.name}")
                val probeResult = try {
                    withTimeoutOrNull(8000) {
                        fetchVideoDetails(firstEp.url, videoUrl, isRecursive = true)
                    }
                } catch (e: Exception) { null }

                if (probeResult != null && probeResult.servers.isNotEmpty()) {
                    discoveredServers = probeResult.servers.toMutableList()
                }
            } else {
                Log.w(TAG, "Speculative Probe skipped: target ${firstEp.url} does not match series slug $cleanSeriesSlug")
            }
        }
        
        // If we are on an episode page and found NO mirrors, try finding an iframe with a common mirror host
        if (isEpisodePage && discoveredServers.isEmpty()) {
            doc.select("iframe").forEach { iframe ->
                val src = iframe.attr("abs:src").ifEmpty { iframe.attr("abs:data-src") }.ifEmpty { iframe.attr("data-src") }.ifEmpty { iframe.attr("src") }
                if (src.isNotEmpty() && isProbablyVideoHost(src)) {
                    val beautifulName = identifyMirrorName("Server 1", src)
                    discoveredServers.add(VideoServer(beautifulName, src))
                }
            }
        }

        val rawRating = doc.select(".imdb-rating, .rating, .score, .gmr-rating-item, .post-ratings").firstOrNull()?.text() ?: ""
        val rating = Regex("""\d+(?:\.\d+)?""").find(rawRating)?.value ?: ""
        val quality = doc.select(".quality, .resolution, .res, .gmr-quality-item").firstOrNull()?.text() ?: ""
        val year = doc.select(".date, .release-date, .year").firstOrNull()?.text()?.filter { it.isDigit() }?.takeLast(4) ?: ""
        val parsedSeason = doc.select(".gmr-season, .season, meta[property='og:title']").firstOrNull()?.text()?.let {
            Regex("""(?i)Season\s+(\d+)""").find(it)?.groupValues?.get(1)?.let { s -> "Season $s" }
        } ?: Regex("""(?i)Season\s+(\d+)""").find(title)?.groupValues?.get(1)?.let { s -> "Season $s" } ?: ""

        Video(
            id = extractStableId(videoUrl), title = title, thumbnailUrl = poster, backdropUrl = backdrop,
            videoUrl = videoUrl, description = bestDesc, previewUrl = previewUrl, actresses = actresses.distinct(),
            actressPaths = actressPaths, actressImages = actressImages,
            duration = doc.select(".duration, .runtime").firstOrNull()?.text() ?: "",
            date = if (year.isNotEmpty()) year else doc.select(".date, .release-date").firstOrNull()?.text() ?: "",
            quality = quality,
            views = rating, // Re-purpose views as Rating for UI dashboard
            season = parsedSeason,
            servers = discoveredServers.sortedByDescending { getProviderPriority(it.name, it.url) },
            episodes = episodes, isSeries = finalIsSeries
        )
    }

    /**
     * Rewrites generic archive.org/download/... URLs to authoritative, high-speed direct US storage nodes.
     * Prevents archive.org from 302-redirecting requests to slow edge caches (e.g. Canadian proxy at 19 KB/s),
     * ensuring immediate fast download at 350-450+ KB/s.
     */
    fun optimizeArchiveUrl(url: String): String {
        if (!url.contains("archive.org/download/")) return url
        return when {
            url.contains("p.-ramlee-ali-baba-bujang-lapok") ->
                url.replace("archive.org/download/p.-ramlee-ali-baba-bujang-lapok", "dn600309.us.archive.org/0/items/p.-ramlee-ali-baba-bujang-lapok")
            url.contains("p-ramlee-nujum-pak-belalang-hd-quality-1") ->
                url.replace("archive.org/download/p-ramlee-nujum-pak-belalang-hd-quality-1", "dn601208.us.archive.org/0/items/p-ramlee-nujum-pak-belalang-hd-quality-1")
            url.contains("TigaAbdul1964HQFullMovie") ->
                url.replace("archive.org/download/TigaAbdul1964HQFullMovie", "dn600305.us.archive.org/0/items/TigaAbdul1964HQFullMovie")
            url.contains("p-ramlee-seniman-bujang-lapok-full-movie-warna") ->
                url.replace("archive.org/download/p-ramlee-seniman-bujang-lapok-full-movie-warna", "dn600305.us.archive.org/0/items/p-ramlee-seniman-bujang-lapok-full-movie-warna")
            url.contains("pendekar-bujang-lapok-1959") ->
                url.replace("archive.org/download/pendekar-bujang-lapok-1959", "ia800602.us.archive.org/17/items/pendekar-bujang-lapok-1959")
            url.contains("p.ramleesenimanbujanglapok1961") ->
                url.replace("archive.org/download/p.ramleesenimanbujanglapok1961", "dn711000.ca.archive.org/0/items/p.ramleesenimanbujanglapok1961")
            url.contains("FilemP.ramlee") ->
                url.replace("archive.org/download/FilemP.ramlee", "dn600308.us.archive.org/0/items/FilemP.ramlee")
            else -> url
        }
    }

    fun getProviderPriority(name: String, url: String? = null): Int {
        val lowName = name.lowercase()
        val lowUrl = url?.lowercase() ?: ""
        
        return when {
            // OWL'S EYE: Priority Tier 0 - VOE / Johnfullwonder (Direct Stream Decrypted)
            lowUrl.contains("voe.sx") || lowUrl.contains("voe") || lowUrl.contains("johnfullwonder") || lowUrl.contains("cloudwindow") || lowName.contains("voe") -> 160

            // ARCHIVE.ORG FAST DIRECT: High-speed direct US storage node (350+ KB/s)
            lowName.contains("fast direct") || lowUrl.contains(".us.archive.org") -> 150

            // OWL'S EYE: Priority Tier 1 - Streamtape (Direct MP4 Extraction Enabled)
            lowUrl.contains("streamtape") && (lowUrl.contains("get_video") || lowUrl.contains("tapecontent")) -> 140
            lowUrl.contains("streamtape") || lowName.contains("streamtape") -> 135

            // OWL'S EYE: Priority Tier 1a - LuluStream (Ultra Fast Direct HLS 720p)
            lowUrl.contains("luluvdo") || lowUrl.contains("lulustream") || lowUrl.contains("player=7") ||
            lowName.contains("lulustream") || lowName.contains("lulu") -> 138

            // OWL'S EYE: Priority Tier 1b - HgCloud (Ultra Stable)
            lowUrl.contains("hgcloud") || lowUrl.contains("hanerix") || lowUrl.contains("vibuxer") ||
            lowUrl.contains("dhcplay") || lowUrl.contains("morencius") || lowUrl.contains("bestcdn") ||
            lowUrl.contains("player=1") ||
            lowName.contains("hgcloud") || lowName.contains("hgvip") -> 130
            
            // OWL'S EYE: Priority Tier 2 - IndoStream (Local Favorite & High-Speed SEA CDN)
            lowUrl.contains("indostream") || isIndoStreamAmt(lowUrl) || lowUrl.contains("iplayer") || 
            lowUrl.contains("pm21") || lowUrl.contains("dm21") || lowUrl.contains("player=2") ||
            lowUrl.contains("player=3") || lowUrl.contains("player=6") || lowUrl.contains("player=8") ||
            lowUrl.contains("playerp2p") || lowUrl.contains("p2p") || lowUrl.contains("embed4me") || lowUrl.contains("upns") ||
            lowName.contains("indostream") || lowName.contains("indovip") || (lowName.contains("amt") && !lowName.contains("stream")) -> 125

            // OWL'S EYE: Priority Tier 2b - YouTube / Dailymotion / Bilibili Full Movie / Alternative Partner Mirror
            lowUrl.contains("youtube") || lowUrl.contains("youtu.be") || lowName.contains("youtube") -> 110
            lowUrl.contains("dailymotion") || lowUrl.contains("dai.ly") || lowName.contains("dailymotion") -> 105
            lowUrl.contains("bilibili") || lowName.contains("bilibili") -> 100

            lowName.contains("vip") || lowUrl.contains("vip") -> 90
            lowUrl.contains("archive.org/download") -> 45
            lowName.contains("direct") || lowUrl.contains(".mp4") || lowUrl.contains(".m3u8") -> 88
            lowUrl.contains("dsvplay") || lowName.contains("dsvplay") -> 85
            lowUrl.contains("swishsrv") || lowUrl.contains("swish") || lowName.contains("swish") -> 85
            lowUrl.contains("vidhide") || lowUrl.contains("vidsrc") -> 75
            lowUrl.contains("dood") -> 70
            
            // OWL'S EYE: Demoted unreliable / heavily protected hosts
            lowUrl.contains("veev") || lowName.contains("veev") || lowUrl.contains("player=5") -> 55
            lowUrl.contains("hglink") || lowName.contains("hglink") -> 20
            
            // OWL'S EYE: Demoted Trap / Ad-gate hosts (Abyss & Bond) to lowest tier
            lowUrl.contains("abyss") || lowUrl.contains("bond") || lowUrl.contains("iamcdn") ||
            lowName.contains("abyss") || lowName.contains("bond") -> 30
            
            lowUrl.contains("listeamed") || lowName.contains("listeamed") -> -100
            else -> 50
        }
    }

    fun cleanEpisodeTitle(rawName: String, parentTitle: String = "", parentUrl: String = ""): String {
        val trimmed = rawName.trim()
        if (trimmed.isEmpty()) return "Episode"

        // Pure numeric strings e.g. "1", "01"
        if (trimmed.length <= 4 && trimmed.all { it.isDigit() }) {
            return "Episode ${trimmed.toIntOrNull() ?: trimmed}"
        }

        val epNum = Regex("""(?i)\b(?:episod[e]?|eps|ep)\s*(\d+)\b""").find(trimmed)?.groupValues?.get(1)

        // Matches patterns like "Episode 1 - 1968", "Episode 1: Taat", "Episode 1 (1968)", "1 - Taat"
        val match = Regex("""(?i)^\s*(?:episod[e]?|eps|ep)?\s*(\d+)\s*(?:[-–—:|]|\()\s*(.*?)\)?$""").find(trimmed)
            ?: if (epNum != null) {
                Regex("""(?i)^\s*(?:episod[e]?|eps|ep)\s*${epNum}\s*(?:[-–—:|]|\()\s*(.*?)\)?$""").find(trimmed)
            } else null

        if (match != null) {
            val num = match.groupValues[1]
            val rawSubtitle = match.groupValues[2].trim().removeSuffix(")").trim()

            if (rawSubtitle.isEmpty()) {
                return "Episode $num"
            }

            // Clean title leakage, year collision, or boilerplate noise
            val cleanParent = parentTitle.replace(Regex("""\s*\((?:19|20)\d{2}\)"""), "").trim().lowercase()
            val lowSub = rawSubtitle.lowercase()
            val subDigits = rawSubtitle.filter { it.isDigit() }

            // 1. Year or numeric sequence that appears in title, URL, or is a standard release year (1900..2099)
            val isYearOrNumberInTitle = (subDigits.length == 4 || rawSubtitle.all { it.isDigit() }) &&
                    (cleanParent.contains(subDigits) || parentUrl.contains(subDigits) || (subDigits.toIntOrNull() ?: 0) in 1900..2099)

            // 2. Parent title leakage (e.g., "Episode 1 - Kudrat 1968", "Episode 1 - Kudrat")
            val isParentTitleLeak = (cleanParent.isNotEmpty() && lowSub.length >= 3 && cleanParent.contains(lowSub)) ||
                    (parentUrl.isNotEmpty() && lowSub.length >= 3 && parentUrl.lowercase().contains(lowSub.replace(" ", "-")))

            // 3. Common site scraping junk
            val isNoise = lowSub.contains("tonton") || lowSub.contains("drama video") ||
                    lowSub.contains("kepala bergetar") || lowSub.contains("melayu") ||
                    lowSub.contains("online") || lowSub.contains("full movie") ||
                    lowSub.contains("web-dl") || lowSub.contains("hd") || lowSub.contains("pencuri")

            if (isYearOrNumberInTitle || isParentTitleLeak || isNoise) {
                return "Episode $num"
            }

            // If subtitle has trailing year in parentheses that matches parent, strip the year
            val cleanedSub = rawSubtitle.replace(Regex("""\s*\((?:19|20)\d{2}\)$"""), "").trim()
            return if (cleanedSub.isNotEmpty()) "Episode $num - $cleanedSub" else "Episode $num"
        }

        // Noise keyword fallback
        if (epNum != null && (trimmed.contains("tonton", ignoreCase = true) || 
                              trimmed.contains("drama video", ignoreCase = true) || 
                              trimmed.contains("kepala", ignoreCase = true))) {
            return "Episode $epNum"
        }

        return trimmed.ifEmpty { if (epNum != null) "Episode $epNum" else "Episode" }
    }

    fun extractEpisodesFromDoc(doc: Document, videoUrl: String, title: String): List<Episode> {
        val episodes = mutableListOf<Episode>()
        // Prioritize dedicated episode containers; fall back to searching full document
        val container = doc.select(".gmr-listseries, .muvipro-listepisode, .list-episode, .episodios, .eps-item, .list-eps, .list-series, .seasons, .season, [class*='listseries'], .episode-list-container, .episodes-grid, #seasons, #season, .tvseason, .les-content, [id*='season']").firstOrNull()
        val searchScope = container ?: doc

        val rawSlug = extractStableId(videoUrl).removePrefix("pm_")
        val seriesSlug = rawSlug
            .replace(Regex("""[-_](?:episod[e]?|eps|ep)[-_]\d+.*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[-_]s(?:eason)?[-_]?\d+[-_]ep(?:isod[e]?)?[-_]?\d+.*""", RegexOption.IGNORE_CASE), "")
            .trimEnd('-')
        val cleanSlugBase = seriesSlug.replace(Regex("""[-_]s(?:eason)?[-_]?\d+""", RegexOption.IGNORE_CASE), "").lowercase()
        val cleanSlugNoYear = cleanSlugBase.replace(Regex("""[-_]?(?:\(|\[)?(?:19|20)\d{2}(?:\)|\])?$"""), "")
        val isContainerDedicated = container != null && !container.id().contains("related", ignoreCase = true) && !container.className().contains("related", ignoreCase = true)

        searchScope.select("a").forEach { el ->
            val epName = el.text().trim()
            val epUrl = el.attr("abs:href")
            val lowUrl = epUrl.lowercase()
            val lowName = epName.lowercase()
            val epTitle = el.attr("title").lowercase()
            val epAria = el.attr("aria-label").lowercase()
            val isServer = lowName.contains("server") || lowName.contains("mirror") || lowUrl.contains("player=") || lowUrl.contains("mirror=")
            val isBackLink = lowName.contains("lihat semua") || lowName.contains("all episode") || lowName.contains("view all") || el.hasClass("gmr-all-serie")
            val isSocial = lowUrl.contains("pinterest") || lowUrl.contains("facebook") || lowUrl.contains("twitter") || 
                           lowUrl.contains("x.com") || lowUrl.contains("whatsapp") || lowUrl.contains("telegram") || 
                           lowUrl.contains("reddit") || lowUrl.contains("linkedin") || lowUrl.contains("share")
            val isEpisodeLink = (lowUrl.contains("/eps/") || lowUrl.contains("/episode/") || lowUrl.contains("-episode-") || 
                                lowUrl.contains("/episod/") || lowUrl.contains("-episod-") || lowUrl.contains("-epi-") ||
                                lowUrl.contains("/ep-") || lowUrl.contains("/episodio/")) && 
                               !lowName.contains("next") && !lowName.contains("prev") && !lowName.contains("lanjut") && !lowName.contains("sebelum") && !lowName.contains("halaman") && !isBackLink && !isSocial

            if (epUrl.isNotEmpty() && !isServer && isEpisodeLink) {
                val cleanUrl = epUrl.substringBefore('?')
                // Validate that episode belongs to current series if container is loose or shared
                if (!isContainerDedicated && cleanSlugBase.length >= 3) {
                    val belongsToSeries = lowUrl.contains(cleanSlugBase) ||
                                          (cleanSlugNoYear.length >= 3 && lowUrl.contains(cleanSlugNoYear)) ||
                                          epTitle.contains(cleanSlugBase.replace("-", " ")) ||
                                          epAria.contains(cleanSlugBase.replace("-", " ")) ||
                                          (cleanSlugNoYear.length >= 3 && epTitle.contains(cleanSlugNoYear.replace("-", " ")))
                    if (!belongsToSeries) {
                        return@forEach
                    }
                }

                val seasonFromName = Regex("""(?i)\bS(\d+)\b""").find(epName)?.groupValues?.get(1)?.let { "Season $it" }
                val seasonFromUrl = Regex("""(?i)season-(\d+)""").find(epUrl)?.groupValues?.get(1)?.let { "Season $it" }
                val seasonFromTitle = Regex("""(?i)season\s+(\d+)""").find(epTitle)?.groupValues?.get(1)?.let { "Season $it" }
                val resolvedSeason = seasonFromName ?: seasonFromUrl ?: seasonFromTitle ?: ""

                // Multi-Season Conflict Guard: If series specifies Season X, reject episodes belonging to Season Y
                val targetSeasonNum = Regex("""(?i)\b(?:season|s)[-_ ]?(\d+)\b""").find(videoUrl)?.groupValues?.get(1)?.toIntOrNull()
                    ?: Regex("""(?i)\b(?:season|s)\s*(\d+)\b""").find(title)?.groupValues?.get(1)?.toIntOrNull()
                val epSeasonNum = Regex("""(?i)\b(?:season|s)[-_ ]?(\d+)\b""").find(resolvedSeason.ifEmpty { epUrl })?.groupValues?.get(1)?.toIntOrNull()
                if (targetSeasonNum != null && epSeasonNum != null && targetSeasonNum != epSeasonNum) {
                    return@forEach
                }

                val epNum = Regex("""(?i)\b(?:episod[e]?|eps|ep)\s*(\d+)\b""").find(epName)?.groupValues?.get(1)
                    ?: Regex("""(?i)\b(?:episod[e]?|eps|ep)\s*(\d+)\b""").find(epTitle)?.groupValues?.get(1)
                    ?: Regex("""(?i)\b(?:episod[e]?|eps|ep)\s*(\d+)\b""").find(epAria)?.groupValues?.get(1)
                    ?: Regex("""(?i)[-_](?:episod[e]?|eps|ep)[-_](\d+)""").find(lowUrl)?.groupValues?.get(1)

                val cleanEpName = cleanEpisodeTitle(epName, title, videoUrl)
                episodes.add(Episode(id = extractStableId(cleanUrl), name = cleanEpName, url = cleanUrl, season = resolvedSeason))
            }
        }

        // OWL'S EYE: If the current page itself is an episode, guarantee it is included in the episode list
        val currLow = videoUrl.lowercase()
        val isCurrEp = (currLow.contains("/eps/") || currLow.contains("/episode/") || currLow.contains("-episode-") || 
                        currLow.contains("/episod/") || currLow.contains("-episod-") || currLow.contains("-epi-") ||
                        currLow.contains("/ep-") || currLow.contains("/episodio/"))
        if (isCurrEp) {
            val cleanCurr = videoUrl.substringBefore('?')
            if (episodes.none { it.url.substringBefore('?') == cleanCurr }) {
                val currEpNum = Regex("""(?i)[-_](?:episod[e]?|eps|ep)[-_](\d+)""").find(currLow)?.groupValues?.get(1)
                    ?: Regex("""(?i)\b(?:episod[e]?|eps|ep)\s*(\d+)\b""").find(title)?.groupValues?.get(1)
                val currName = cleanEpisodeTitle(if (currEpNum != null) "Episode $currEpNum" else "Episode", title, videoUrl)
                episodes.add(Episode(id = extractStableId(cleanCurr), name = currName, url = cleanCurr, season = ""))
            }
        }

        return episodes.distinctBy { it.url }.sortedWith(compareBy({ 
            it.season.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0 
        }, {
            Regex("""(?i)\b(?:episod[e]?|eps|ep)\s*(\d+)\b""").find(it.name)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("""(?i)[-_](?:episod[e]?|eps|ep)[-_](\d+)""").find(it.url)?.groupValues?.get(1)?.toIntOrNull()
                ?: it.name.filter { c -> c.isDigit() }.toIntOrNull() ?: 0
        }))
    }

    suspend fun resolveAjaxServer(url: String, postId: String, n: String, type: String, referer: String? = null): String? = withContext(Dispatchers.IO) {
        val uri = try { android.net.Uri.parse(url) } catch(e: Exception) { null }
        val hosts = listOf("eddieoneverything.com", "seoulschool.org", "ladyriderswear.com", "itoshii-movie.com", "bokinshop.com", "b7510.com")
        val actualReferer = referer ?: url
        
        val typesToTry = when (type) {
            "movie" -> listOf("movie", "movies")
            "tv" -> listOf("tv", "episodes", "episode")
            else -> listOf(type, "movie")
        }
        
        val allHosts = (listOf(uri?.host ?: "") + hosts).distinct().filter { it.isNotEmpty() }
        val actions = listOf("muvipro_player_content", "muvipro_get_player", "muvipro_player", "doo_player_ajax", "muvipro_player_ajax", "dt_player_ajax", "halim_ajax_player", "ajax_get_link", "get_player_link", "gmr_ajax_player", "player_ajax", "get_player_ajax", "load_player_content", "load_player", "player_content", "ajax_player")

        // OWL'S EYE GOD MODE: Parallel Handshake
        // Try all hosts and all actions simultaneously to find the winner in <1s
        val result = coroutineScope {
            allHosts.flatMap { host ->
                typesToTry.flatMap { currentType ->
                    actions.map { actionName ->
                        async {
                            try {
                                val ajaxUrl = "https://$host/wp-admin/admin-ajax.php"
                                val bodyBuilder = FormBody.Builder().add("action", actionName)
                                if (actionName == "muvipro_player_content") {
                                    bodyBuilder.add("post", postId).add("nume", n).add("type", currentType)
                                } else {
                                    bodyBuilder.add("post", postId).add("n", n).add("type", currentType)
                                }
                                val body = bodyBuilder.build()
                                val request = Request.Builder().url(ajaxUrl).post(body)
                                    .header("X-Requested-With", "XMLHttpRequest")
                                    .header("Referer", actualReferer)
                                    .header("User-Agent", USER_AGENT)
                                    .build()
                                    
                                NetworkConfig.okHttpClient.newCall(request).execute().use { response ->
                                    if (response.isSuccessful) {
                                        val resp = response.body?.string() ?: ""
                                        if (resp.length > 10) {
                                            val m = Regex("src=[\"']([^\"']+)[\"']").find(resp) ?: 
                                                    Regex("[\"'](?:embed_url|url|link|src)[\"']\\s*:\\s*[\"']([^\"']+)[\"']").find(resp)
                                            val found = m?.groupValues?.get(1)?.replace("\\/", "/")
                                            if (found != null && found.startsWith("http")) found else null
                                        } else null
                                    } else null
                                }
                            } catch (_: Exception) { null }
                        }
                    }
                }
            }.firstNotNullOfOrNull { it.await() }
        }
        
        return@withContext result
    }
}
