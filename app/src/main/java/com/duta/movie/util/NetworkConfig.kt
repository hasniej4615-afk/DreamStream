package com.duta.movie.util

import androidx.core.net.toUri
import android.util.Log
import okhttp3.ConnectionSpec
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object NetworkConfig {
    private const val TAG = "NetworkConfig"

    const val SHARED_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36"
    
    const val MOBILE_USER_AGENT = "Mozilla/5.0 (Linux; Android 13; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Mobile Safari/537.36"

    const val TV_USER_AGENT = "Mozilla/5.0 (Web0S; Linux/SmartTV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36"

    val networkTimeoutMs: Long = 30000L

    private val cookiesMap = mutableMapOf<String, List<Cookie>>()
    val sessionReferers = mutableMapOf<String, String>()

    /**
     * Owl's Eye: Header Profile System
     * Specialized profiles for difficult mirrors
     */
    enum class HeaderProfile {
        STANDARD, MOBILE, DESKTOP, AGGRESSIVE_SPOOF
    }

    fun getHeaderProfile(url: String): HeaderProfile {
        val lowUrl = url.lowercase()
        return when {
            lowUrl.contains("bestcdn") || lowUrl.contains("morencius") || lowUrl.contains("dhcplay") ||
            lowUrl.contains("ryderjet") || lowUrl.contains("faststream") || lowUrl.contains("iplayer") ||
            lowUrl.contains("asiastream") || lowUrl.contains("asiatik") -> HeaderProfile.AGGRESSIVE_SPOOF
            lowUrl.contains("hgcloud") || lowUrl.contains("hanerix") || lowUrl.contains("vibuxer") ||
            lowUrl.contains("audinifer") || lowUrl.contains("haneri") || lowUrl.contains("abyss") || 
            lowUrl.contains("bond") || lowUrl.contains("iamcdn") || lowUrl.contains("katakatamutiara") -> HeaderProfile.MOBILE
            else -> HeaderProfile.STANDARD
        }
    }

    fun getUserAgent(profile: HeaderProfile): String = when (profile) {
        HeaderProfile.MOBILE -> MOBILE_USER_AGENT
        HeaderProfile.DESKTOP -> SHARED_USER_AGENT
        HeaderProfile.AGGRESSIVE_SPOOF -> MOBILE_USER_AGENT // Switch to Mobile for aggressive spoofing
        HeaderProfile.STANDARD -> SHARED_USER_AGENT
    }

    private fun pruneSessionCaches() {
        synchronized(sessionReferers) {
            if (sessionReferers.size > 500) {
                val keys = sessionReferers.keys.asSequence().take(100).toList()
                keys.forEach { sessionReferers.remove(it) }
            }
        }
        synchronized(cookiesMap) {
            if (cookiesMap.size > 500) {
                val keys = cookiesMap.keys.asSequence().take(100).toList()
                keys.forEach { cookiesMap.remove(it) }
            }
        }
    }

    fun getSessionReferer(host: String): String? = synchronized(sessionReferers) { sessionReferers[host] }

    val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, responseList: List<Cookie>) {
            synchronized(cookiesMap) {
                val current = cookiesMap[url.host]?.toMutableList() ?: mutableListOf()
                responseList.forEach { newCookie ->
                    current.removeAll { it.name == newCookie.name }
                    current.add(newCookie)
                }
                cookiesMap[url.host] = current
            }
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val domain = url.host
            return synchronized(cookiesMap) {
                cookiesMap.filter { (host, _) -> 
                    domain == host || domain.endsWith(".$host") || host.endsWith(".$domain")
                }.values.flatten().distinctBy { it.name }
            }
        }
    }

    fun injectCookies(url: String, cookieString: String?) {
        if (cookieString.isNullOrEmpty()) return
        pruneSessionCaches()
        try {
            val httpUrl = url.toHttpUrl()
            val host = httpUrl.host
            
            val hostAliases = mutableSetOf(host)
            // Add common subdomains and related mirrors for cookie sharing
            if (host.contains("masukestin") || host.contains("masukin") || host.contains("dhcplay") || host.contains("audinifer") || host.contains("distributedcomputing") || host.contains("morencius") || host.contains("bestcdn") || host.contains("katakatamutiara") || host.contains("scphi") || host.contains("grisham") || host.contains("bullerswood") || host.contains("mantab") || host.contains("dfm2u") || host.contains("kepala-bergetar") || host.contains("kepalabergetar")) {
                hostAliases.addAll(listOf("masukestin.com", "masukestin.net", "masukin.vip", "masuk.link", "masukin.lol", "masukin.top", "dhcplay.com", "audinifer.com", "audinifer.lol", "audinifer.top", "distributedcomputing.space", "morencius.com", "bestcdn.me", "bestcdn.pro", "katakatamutiara.com", "scphi.org", "grishamfarms.org", "bullerswood.org", "tv12.lk21official.cc", "dfm2u.org", "kepalabergetar9.net", "kepala-bergetar.com"))
            }
            if (host.contains("vibuxer") || host.contains("hanerix") || host.contains("duvidun") || host.contains("fujihide") || host.contains("wellnessspace") || host.contains("harmonix") || host.contains("veev")) {
                hostAliases.addAll(listOf("vibuxer.com", "vibuxer.net", "hanerix.com", "hanerix.net", "duvidun.com", "duvidun.net", "fujihide.com", "fujihide.net", "wellnessspace.shop", "wellnessspace.online", "harmonixinnovationlab.store", "harmonixinnovationlab.com", "veev.to"))
            }
            if (host.contains("hglink") || host.contains("hgcloud") || host.contains("hgcdn") || host.contains("hglcdn")) {
                hostAliases.addAll(listOf("hglink.to", "hgcloud.to", "hgcdn.com", "hglink.icu", "hgcloud.online", "hgcdn.xyz", "hglcdn.com"))
            }
            if (host.contains("indostream") || host.contains("amt") || host.contains("iplayer") || host.contains("zeus") || host.contains("klik") || host.contains("vingaming")) {
                hostAliases.addAll(listOf("indostream.lol", "indostream.pw", "indostream.icu", "amt1.pro", "amt2.pro", "amt3.pro", "amt4.pro", "iplayerhls.com", "iplayerhls.org", "zeus88.lol", "klikzeus.lol", "klik.top", "vingaming.pro", "zeusplayer.com"))
            }
            
            // Dynamic Mirror Pattern Capture
            if (host.contains("iplayer") || host.contains("indostream") || 
                host.contains("masuk") || host.contains("vibuxer") || host.contains("haneri") || host.contains("audinifer") || 
                host.contains("ryder") || host.contains("ghb") || host.contains("tma2x") ||
                host.endsWith(".shop") || host.endsWith(".online") || host.endsWith(".sbs") || host.endsWith(".xyz") || host.endsWith(".lol")) {
                hostAliases.add(host)
            }

            if (host.contains("ryder") || host.contains("ghb") || host.contains("tma2x")) {
                hostAliases.addAll(listOf("ryderjet.com", "rydercdn.com", "ghbrisk.com", "ghb.icu", "tma2x.pro"))
            }

            cookieString.split(";").forEach {
                val part = it.trim()
                if (part.isNotEmpty()) {
                    val cookiePair = part.split("=", limit = 2)
                    if (cookiePair.size == 2) {
                        val name = cookiePair[0].trim()
                        val value = cookiePair[1].trim()
                        
                        synchronized(cookiesMap) {
                            hostAliases.forEach { alias ->
                                val current = cookiesMap[alias]?.toMutableList() ?: mutableListOf()
                                current.removeAll { c -> c.name == name }
                                // Use a more permissive cookie builder
                                val cookie = Cookie.Builder()
                                    .name(name)
                                    .value(value)
                                    .domain(alias)
                                    .path("/")
                                    .build()
                                current.add(cookie)
                                cookiesMap[alias] = current
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {}
    }

    fun updateSessionReferer(url: String, referer: String) {
        pruneSessionCaches()
        try {
            val host = url.toHttpUrl().host.lowercase()
            synchronized(sessionReferers) {
                sessionReferers[host] = referer
                
                // If this is a known gateway/mirror group, don't broadcast referer to ALL mirrors
                // unless they share the same TLD or are in a known "un-strict" group.
                val isStrictGroup = host.contains("hanerix") || host.contains("vibuxer") || 
                                   host.contains("audinifer") || host.contains("masukestin") ||
                                   host.contains("distributedcomputing") || host.contains("wellnessspace") ||
                                   host.contains("ryder") || host.contains("hgcdn")
                
                if (!isStrictGroup) {
                    val commonCdnHosts = listOf("hgcdn.com", "hglcdn.com", "meadowpath.com", "wellnessspace.shop", "latestmoviereview.com", "abyss.to", "abysscdn.com", "bond.to", "bondcdn.com", "dhcplay.com", "morencius.com", "scphi.org", "grishamfarms.org", "bullerswood.org", "tv12.lk21official.cc", "mantab.men", "dfm2u.org", "kepalabergetar9.net", "kepala-bergetar.com", "indostream.lol", "vidplay.xyz", "distributedcomputing.space", "seoulschool.org", "ladyriderswear.com", "itoshii-movie.com", "eddieoneverything.com", "dutamovie.com", "iplayerhls.com", "amt1.pro", "amt2.pro", "zeus88.lol", "klikzeus.lol", "katakatamutiara.com")
                    commonCdnHosts.forEach { if (host.contains(it.split(".").first())) sessionReferers[it] = referer }
                }
                
                // Dynamic Mirror Referer Capture
                if (host.contains("cloudwindow")) {
                    sessionReferers["cloudwindow-route.com"] = referer
                    sessionReferers[host] = referer
                }

                if (host.contains("dailymotion") || host.contains("cdndirector") || host.contains("dmcdn")) {
                    sessionReferers["cdndirector.dailymotion.com"] = "https://www.dailymotion.com/"
                    sessionReferers["dmcdn.net"] = "https://www.dailymotion.com/"
                    sessionReferers[host] = "https://www.dailymotion.com/"
                }

                if (host.contains("luluvdo") || host.contains("lulustream") || host.contains("tnmr.org") || host.contains("lulucdn")) {
                    sessionReferers["tnmr.org"] = "https://luluvdo.com/"
                    sessionReferers["cdn-tnmr.org"] = "https://luluvdo.com/"
                    sessionReferers[host] = "https://luluvdo.com/"
                }

                if (host.contains("asiastream") || host.contains("asiatik")) {
                    sessionReferers["watch.asiastream.cc"] = "https://watch.asiastream.cc/"
                    sessionReferers["asiastream.cc"] = "https://watch.asiastream.cc/"
                    sessionReferers["asiatik01.site"] = "https://watch.asiastream.cc/"
                    sessionReferers[host] = "https://watch.asiastream.cc/"
                }

                if (host.contains("iplayer") || host.contains("indostream") || host.contains("morencius") || host.contains("bestcdn") || host.contains("latestmoviereview") || host.contains("wellnessspace") || host.contains("meadowpath") ||
                    host.contains("amt") || host.contains("masuk") || host.contains("vibuxer") || host.contains("haneri") || 
                    host.contains("audinifer") || host.contains("ryder") || host.contains("ghb") || host.contains("tma2x") ||
                    host.contains("playerp2p") || host.contains("p2p") || host.contains("breakplan") ||
                    host.endsWith(".shop") || host.endsWith(".online") || host.endsWith(".sbs") || host.endsWith(".xyz") || host.endsWith(".lol")) {
                    sessionReferers[host] = referer
                }
            }
        } catch (e: Exception) {}
    }

    fun clearSessionData() {
        synchronized(cookiesMap) { cookiesMap.clear() }
        synchronized(sessionReferers) { sessionReferers.clear() }
    }

    fun getCookieHeader(url: String): String? {
        return try {
            val httpUrl = url.toHttpUrl()
            val cookiesForUrl = cookieJar.loadForRequest(httpUrl)
            if (cookiesForUrl.isEmpty()) null
            else cookiesForUrl.joinToString("; ") { "${it.name}=${it.value}" }
        } catch (e: Exception) {
            null
        }
    }

    private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
    })

    private val permissiveSslContext = SSLContext.getInstance("TLS").apply {
        init(null, trustAllCerts, java.security.SecureRandom())
    }

    @Volatile private var dohFailCount = 0
    @Volatile private var dohSuspendedUntil = 0L

    private val dnsCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<InetAddress>>>()
    private const val DNS_CACHE_TTL_MS = 60 * 60 * 1000L // 1 hour DNS in-memory cache for ultra-fast image loading

    fun clearDnsCache() {
        dnsCache.clear()
    }

    fun resetDohCircuitBreaker() {
        dohFailCount = 0
        dohSuspendedUntil = 0L
        dnsCache.clear()
        Log.d(TAG, "DoH Circuit Breaker reset. Probing DoH on new network.")
    }

    private val bootstrapClient = OkHttpClient.Builder()
        .connectTimeout(1, TimeUnit.SECONDS)
        .readTimeout(1, TimeUnit.SECONDS)
        .writeTimeout(1, TimeUnit.SECONDS)
        .callTimeout(1500, TimeUnit.MILLISECONDS)
        .sslSocketFactory(permissiveSslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
        .hostnameVerifier { _, _ -> true }
        .build()

    private fun createDnsOverHttps(url: String, bootstrapIps: List<String>): Dns {
        return DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url(url.toHttpUrl())
            .bootstrapDnsHosts(bootstrapIps.map { InetAddress.getByName(it) })
            .build()
    }

    private val cloudflareDns by lazy { createDnsOverHttps("https://1.1.1.1/dns-query", listOf("1.1.1.1", "1.0.0.1")) }
    private val googleDns by lazy { createDnsOverHttps("https://8.8.8.8/dns-query", listOf("8.8.8.8", "8.8.4.4")) }
    private val quad9Dns by lazy { createDnsOverHttps("https://dns.quad9.net/dns-query", listOf("9.9.9.9", "149.112.112.112")) }

    private fun isValidPublicIp(address: InetAddress): Boolean {
        val hostAddress = address.hostAddress ?: return false
        if (hostAddress == "0.0.0.0" || hostAddress == "127.0.0.1" || hostAddress == "::1") return false
        if (address.isAnyLocalAddress || address.isLoopbackAddress) return false
        // Known ISP censorship sinkholes (Indonesia Internet Sehat / U-Zone)
        if (hostAddress.startsWith("180.250.") || hostAddress == "36.86.63.185" || hostAddress.startsWith("118.97.115.")) return false
        return true
    }

    private val multiDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val now = System.currentTimeMillis()
            // 0. Ultra-fast In-Memory DNS Cache (0ms latency for repeating image CDN hosts like image.tmdb.org)
            val cached = dnsCache[hostname]
            if (cached != null && now < cached.first) {
                return cached.second
            }

            val isDohSuspended = now < dohSuspendedUntil
            var resolvedIps: List<InetAddress> = emptyList()

            if (!isDohSuspended) {
                // 1. Prioritize Cloudflare DoH (Encrypted over port 443, immune to ISP port 53 DNS interception)
                try {
                    val cf = cloudflareDns.lookup(hostname).filter { isValidPublicIp(it) }
                    if (cf.isNotEmpty()) {
                        dohFailCount = 0
                        resolvedIps = cf
                    }
                } catch (_: Exception) {}

                // 2. Fallback to Google DoH (Encrypted over port 443)
                if (resolvedIps.isEmpty()) {
                    try {
                        val gg = googleDns.lookup(hostname).filter { isValidPublicIp(it) }
                        if (gg.isNotEmpty()) {
                            dohFailCount = 0
                            resolvedIps = gg
                        }
                    } catch (_: Exception) {}
                }

                // 3. Fallback to Quad9 DoH (Encrypted over port 443)
                if (resolvedIps.isEmpty()) {
                    try {
                        val q9 = quad9Dns.lookup(hostname).filter { isValidPublicIp(it) }
                        if (q9.isNotEmpty()) {
                            dohFailCount = 0
                            resolvedIps = q9
                        }
                    } catch (_: Exception) {}
                }

                if (resolvedIps.isEmpty()) {
                    dohFailCount++
                    if (dohFailCount >= 1) {
                        dohSuspendedUntil = now + (15 * 60 * 1000L) // Suspend DoH for 15 minutes on this network
                        Log.w(TAG, "DoH unreachable/blocked by office network firewall. Suspending DoH for 15m; routing via System DNS.")
                    }
                }
            }

            // 4. System DNS fallback (enterprise/office network native resolver, VPN, or corporate DHCP DNS)
            if (resolvedIps.isEmpty()) {
                resolvedIps = try {
                    val sys = Dns.SYSTEM.lookup(hostname).filter { isValidPublicIp(it) }
                    if (sys.isNotEmpty()) sys else emptyList()
                } catch (_: Exception) {
                    emptyList()
                }
            }

            if (resolvedIps.isNotEmpty()) {
                dnsCache[hostname] = Pair(now + DNS_CACHE_TTL_MS, resolvedIps)
            }
            return resolvedIps
        }
    }

    val imageOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dispatcher(okhttp3.Dispatcher().apply {
                maxRequests = 256
                maxRequestsPerHost = 64
            })
            .connectionPool(okhttp3.ConnectionPool(64, 5, TimeUnit.MINUTES))
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .dns(multiDns)
            .sslSocketFactory(permissiveSslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .protocols(listOf(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1)) // HTTP/2 multiplexing for Cloudflare/Google CDNs
            .addInterceptor { chain ->
                chain.withConnectTimeout(6000, TimeUnit.MILLISECONDS)
                     .withReadTimeout(8000, TimeUnit.MILLISECONDS)
                     .proceed(chain.request())
            }
            .addInterceptor { chain ->
                val request = chain.request()
                val host = request.url.host.lowercase()
                val builder = request.newBuilder()
                    .header("User-Agent", SHARED_USER_AGENT)
                
                // Smart Referer for Images:
                if (host.contains("blogspot.com") || host.contains("googleusercontent.com")) {
                    builder.header("Referer", "https://$host/")
                } else if (host.contains("media-amazon.com") || host.contains("imdb.com")) {
                    builder.header("Referer", "https://www.imdb.com/")
                } else if (host.contains("hdslb.com") || host.contains("bilibili.com")) {
                    builder.header("Referer", "https://www.bilibili.com/")
                } else if (host.contains("dmcdn.net") || host.contains("dailymotion.com")) {
                    builder.header("Referer", "https://www.dailymotion.com/")
                } else if (host.contains("tmdb.org")) {
                    builder.removeHeader("Referer")
                } else if (host.contains("load.my.id") || host.contains("mantab.men")) {
                    builder.header("Referer", "${VideoExtractor.getDutaFilmWebBaseUrl()}/")
                } else if (host.contains("bullerswood.org")) {
                    builder.header("Referer", "https://bullerswood.org/")
                } else {
                    builder.header("Referer", "https://$host/")
                }
                
                var response: okhttp3.Response? = null
                var attempt = 0
                val maxRetry = 1
                
                while (attempt <= maxRetry) {
                    try {
                        response = chain.proceed(builder.build())
                        if (response.isSuccessful) return@addInterceptor response
                        
                        val code = response.code
                        // If it's a 403/404, don't retry, just return the response
                        if (code == 403 || code == 404) {
                            if (code == 403) Log.w(TAG, "Image Load 403: ${request.url}")
                            return@addInterceptor response
                        }
                        
                        response.close()
                    } catch (e: Exception) {
                        // Broad cancellation check to avoid logging expected behavior
                        val msg = e.message ?: ""
                        val isCanceled = (e is java.io.IOException) && (
                            msg.contains("Canceled", ignoreCase = true) || 
                            msg.contains("interrupted", ignoreCase = true) ||
                            msg.contains("Socket closed", ignoreCase = true) ||
                            msg.contains("stream was reset", ignoreCase = true)
                        )
                        
                        if (isCanceled) throw e

                        if (attempt >= maxRetry) {
                            Log.e(TAG, "Image Load Error after $attempt retries: ${request.url} | $msg")
                            throw e
                        }
                    }
                    attempt++
                }
                
                val finalResponse = response ?: chain.proceed(request)
                if (!finalResponse.isSuccessful && finalResponse.code != 404) {
                    Log.w(TAG, "Image Load Failed (Final): ${request.url} | Code: ${finalResponse.code}")
                }
                finalResponse
            }
            .build()
    }

    // MAIN CLIENT: Strict by default for Primary Domain and Security sensitive tasks
    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dispatcher(okhttp3.Dispatcher().apply {
                maxRequests = 128
                maxRequestsPerHost = 15
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .dns(multiDns)
            .cookieJar(cookieJar)
            .sslSocketFactory(permissiveSslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT))
            .addInterceptor { chain ->
                val clientConnect = chain.connectTimeoutMillis()
                // If caller explicitly configured a faster timeout (< 15s), respect it and do not override with 30s!
                if (clientConnect in 1..15000) {
                    chain.proceed(chain.request())
                } else {
                    val timeout = networkTimeoutMs.toInt()
                    chain.withConnectTimeout(timeout, TimeUnit.MILLISECONDS)
                         .withReadTimeout(timeout, TimeUnit.MILLISECONDS)
                         .proceed(chain.request())
                }
            }
            .addInterceptor { chain ->
                val request = chain.request()
                val host = request.url.host.lowercase()
                val urlString = request.url.toString()
                val savedReferer = synchronized(sessionReferers) { 
                    sessionReferers[host] ?: if (host.contains("cloudwindow")) sessionReferers["cloudwindow-route.com"]
                    else if (host.contains("dailymotion") || host.contains("cdndirector") || host.contains("dmcdn")) "https://www.dailymotion.com/"
                    else if (host.contains("vidhide") || host.contains("fujihide")) "https://vidhide.org/"
                    else if (host.contains("tnmr.org")) {
                        val sess = sessionReferers[host]
                        if (sess?.contains("vidhide") == true || sess?.contains("fujihide") == true) "https://vidhide.org/"
                        else "https://luluvdo.com/"
                    }
                    else if (host.contains("luluvdo") || host.contains("lulustream") || host.contains("lulucdn")) "https://luluvdo.com/"
                    else if (host.contains("asiastream") || host.contains("asiatik")) sessionReferers["watch.asiastream.cc"] ?: "https://watch.asiastream.cc/"
                    else null 
                }
                
                val requestBuilder = request.newBuilder()
                
                // OWL'S EYE: Intelligent Header Management
                val profile = getHeaderProfile(urlString)
                val ua = getUserAgent(profile)
                
                requestBuilder.header("User-Agent", ua)
                
                // Dynamic Origin Logic
                val currentReferer = request.header("Referer") ?: savedReferer
                val originFromReferer = if (!currentReferer.isNullOrEmpty() && currentReferer.startsWith("http")) {
                    try {
                        val uri = currentReferer.toUri()
                        "${uri.scheme}://${uri.host}"
                    } catch(_: Exception) { null }
                } else null
                
                val finalOrigin = originFromReferer ?: when {
                    host.contains("hgcdn") || host.contains("hgcloud") || host.contains("hanerix") || host.contains("katakatamutiara") -> "https://hgcloud.to"
                    host.contains("abyss") || host.contains("iamcdn") -> "https://abyss.to"
                    host.contains("indostream") || host.contains("morencius") -> "https://iplayerhls.com"
                    host.contains("dailymotion") || host.contains("cdndirector") || host.contains("dmcdn") -> "https://www.dailymotion.com"
                    host.contains("vidhide") || host.contains("fujihide") -> "https://vidhide.org"
                    host.contains("tnmr.org") -> if (currentReferer?.contains("vidhide") == true || currentReferer?.contains("fujihide") == true) "https://vidhide.org" else "https://luluvdo.com"
                    host.contains("luluvdo") || host.contains("lulustream") || host.contains("lulucdn") -> "https://luluvdo.com"
                    host.contains("asiastream") || host.contains("asiatik") -> "https://watch.asiastream.cc"
                    else -> "https://${host}"
                }
                
                if (host.contains("cloudwindow")) {
                    requestBuilder.header("Referer", "https://johnfullwonder.com/")
                    requestBuilder.header("Origin", "https://johnfullwonder.com")
                } else if (host.contains("dailymotion") || host.contains("cdndirector") || host.contains("dmcdn")) {
                    requestBuilder.header("Referer", "https://www.dailymotion.com/")
                    requestBuilder.header("Origin", "https://www.dailymotion.com")
                } else if (host.contains("vidhide") || host.contains("fujihide")) {
                    requestBuilder.header("Referer", currentReferer ?: "https://vidhide.org/")
                    requestBuilder.header("Origin", originFromReferer ?: "https://vidhide.org")
                } else if (host.contains("tnmr.org")) {
                    val isVidhide = currentReferer?.contains("vidhide") == true || currentReferer?.contains("fujihide") == true ||
                                    savedReferer?.contains("vidhide") == true || savedReferer?.contains("fujihide") == true
                    if (isVidhide) {
                        requestBuilder.header("Referer", currentReferer ?: "https://vidhide.org/")
                        requestBuilder.header("Origin", originFromReferer ?: "https://vidhide.org")
                    } else {
                        requestBuilder.header("Referer", currentReferer ?: "https://luluvdo.com/")
                        requestBuilder.header("Origin", originFromReferer ?: "https://luluvdo.com")
                    }
                } else if (host.contains("luluvdo") || host.contains("lulustream") || host.contains("lulucdn")) {
                    requestBuilder.header("Referer", currentReferer ?: "https://luluvdo.com/")
                    requestBuilder.header("Origin", originFromReferer ?: "https://luluvdo.com")
                } else if (host.contains("asiastream") || host.contains("asiatik")) {
                    requestBuilder.header("Referer", currentReferer ?: "https://watch.asiastream.cc/")
                    requestBuilder.header("Origin", "https://watch.asiastream.cc")
                } else {
                    requestBuilder.header("Origin", finalOrigin)
                    if (currentReferer != null) {
                        requestBuilder.header("Referer", currentReferer)
                    } else if (request.header("Referer") == null) {
                        requestBuilder.header("Referer", "https://${host}/")
                    }
                }

                val isHtmlPage = request.header("Accept")?.contains("text/html") == true ||
                                 (!urlString.contains(".m3u8") && !urlString.contains(".mp4") && !urlString.contains(".ts") && !urlString.contains(".mkv") && !urlString.contains(".webm") && (urlString.contains("abyss") || urlString.contains("bond") || urlString.contains("playsobat") || urlString.contains("hydrax")))
                if (isHtmlPage) {
                    requestBuilder.removeHeader("Origin")
                    if (currentReferer != null) {
                        requestBuilder.header("Referer", currentReferer)
                    }
                }

                // SEC FETCH HEADERS (Anti-Bot Bypassing)
                if (profile == HeaderProfile.AGGRESSIVE_SPOOF || profile == HeaderProfile.MOBILE) {
                    val isMobile = profile == HeaderProfile.MOBILE || profile == HeaderProfile.AGGRESSIVE_SPOOF
                    requestBuilder.header("Sec-Ch-Ua", if (isMobile) "\"Not A(Brand\";v=\"8\", \"Chromium\";v=\"132\", \"Android WebView\";v=\"132\"" else "\"Not A(Brand\";v=\"8\", \"Chromium\";v=\"132\", \"Google Chrome\";v=\"132\"")
                    requestBuilder.header("Sec-Ch-Ua-Mobile", if (isMobile) "?1" else "?0")
                    requestBuilder.header("Sec-Ch-Ua-Platform", if (isMobile) "\"Android\"" else "\"Windows\"")
                    if (isHtmlPage) {
                        requestBuilder.header("Sec-Fetch-Dest", "document")
                        requestBuilder.header("Sec-Fetch-Mode", "navigate")
                        requestBuilder.header("Sec-Fetch-Site", "cross-site")
                        requestBuilder.header("Sec-Fetch-User", "?1")
                        requestBuilder.header("Upgrade-Insecure-Requests", "1")
                    } else {
                        requestBuilder.header("Sec-Fetch-Dest", if (urlString.contains(".m3u8")) "empty" else "video")
                        requestBuilder.header("Sec-Fetch-Mode", if (urlString.contains(".m3u8")) "cors" else "no-cors")
                        requestBuilder.header("Sec-Fetch-Site", "cross-site")
                    }
                }
                
                val finalReq = requestBuilder.build()
                
                // Log outgoing headers for debugging 403s in logcat
                val isDirectStream = urlString.contains(".m3u8") || urlString.contains(".mp4")
                if (profile != HeaderProfile.STANDARD && isDirectStream) {
                    Log.d(TAG, "Outgoing Mirror Request: $urlString")
                    Log.d(TAG, "Headers: ${finalReq.headers}")
                }
                
                chain.proceed(finalReq)
            }
            .build()
    }

    // PERMISSIVE CLIENT: Only for unreliable mirrors
    val permissiveOkHttpClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .sslSocketFactory(permissiveSslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    // FAST CLIENT: Swift 5s connect timeout for mirror probing and direct stream extraction
    val fastOkHttpClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .writeTimeout(6, TimeUnit.SECONDS)
            .callTimeout(7, TimeUnit.SECONDS)
            .build()
    }

    // HTML CLIENT: Generous timeouts for large (200KB-500KB) Cloudflare HTML pages
    val htmlOkHttpClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(18, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}

