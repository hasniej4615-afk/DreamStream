package com.duta.movie.model

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Built-in catalog of Malaysian Live TV channels.
 * Features official, high-definition 1080p HLS broadcasts without DRM or token expiration.
 */
object LiveTvCatalog {

    val channels: List<Video> = listOf(
        Video(
            id = "live_tv1",
            title = "TV1",
            thumbnailUrl = "https://i.imgur.com/wSyPmiY.png",
            backdropUrl = "https://i.imgur.com/wSyPmiY.png",
            videoUrl = "https://d25tgymtnqzu8s.cloudfront.net/smil:tv1/playlist.m3u8?id=1",
            duration = "LIVE",
            quality = "1080p FHD",
            description = "Saluran televisyen pertama Malaysia oleh RTM. Berita nasional terkini, siaran langsung rasmi dan acara sukan tempatan.",
            servers = listOf(VideoServer("RTM CloudFront (1080p)", "https://d25tgymtnqzu8s.cloudfront.net/smil:tv1/playlist.m3u8?id=1")),
            isSeries = false
        ),
        Video(
            id = "live_tv2",
            title = "TV2",
            thumbnailUrl = "https://i.imgur.com/LGEdeyJ.png",
            backdropUrl = "https://i.imgur.com/LGEdeyJ.png",
            videoUrl = "https://d25tgymtnqzu8s.cloudfront.net/smil:tv2/playlist.m3u8?id=2",
            duration = "LIVE",
            quality = "1080p FHD",
            description = "Saluran hiburan dan keluarga oleh RTM. Drama, filem, konsert dan pelbagai rancangan menarik multibahasa.",
            servers = listOf(VideoServer("RTM CloudFront (1080p)", "https://d25tgymtnqzu8s.cloudfront.net/smil:tv2/playlist.m3u8?id=2")),
            isSeries = false
        ),
        Video(
            id = "live_tv3",
            title = "TV3",
            thumbnailUrl = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/malaysia/tv3-my.png",
            backdropUrl = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/malaysia/tv3-my.png",
            videoUrl = "https://api.dani-dev.co.za/v2/stream/tonton/live/tv3",
            duration = "LIVE",
            quality = "1080p FHD",
            description = "Saluran televisyen swasta komersial terkemuka di Malaysia oleh Media Prima. Siaran langsung rasmi drama Akasia, hiburan, berita utama dan acara sukan tempatan.",
            servers = listOf(
                VideoServer("Tonton HLS 1080p (Official Broadcast)", "https://api.dani-dev.co.za/v2/stream/tonton/live/tv3"),
                VideoServer("Tonton HLS (Direct Mirror)", "https://api.dani-dev.co.za/v1/stream/tonton/live/tv3"),
                VideoServer("Buletin TV3 24/7 (News Backup)", "https://www.youtube.com/watch?v=HX_6UiKZw-Q")
            ),
            isSeries = false
        ),
        Video(
            id = "live_tv_alhijrah",
            title = "TV Alhijrah",
            thumbnailUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/e725be7c-615d-42bd-9399-fef5cf386ecb/logo.png",
            backdropUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/e725be7c-615d-42bd-9399-fef5cf386ecb/banner.png",
            videoUrl = "https://cuwf8jayq1.tenbytecdn.com/live/tvalhijrah/playlist.m3u8",
            duration = "LIVE",
            quality = "1080p FHD",
            description = "Saluran televisyen Islamik pertama Malaysia menyajikan program gaya hidup, bimbingan rohani, dan ilmiah berteraskan nilai murni.",
            servers = listOf(VideoServer("MYTV Tenbyte (1080p)", "https://cuwf8jayq1.tenbytecdn.com/live/tvalhijrah/playlist.m3u8")),
            isSeries = false
        ),
        Video(
            id = "live_bernamatv",
            title = "Bernama TV",
            thumbnailUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/1a41a91b-64c1-4feb-a119-76ebd88fed4a/logo.png",
            backdropUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/1a41a91b-64c1-4feb-a119-76ebd88fed4a/banner.png",
            videoUrl = "https://cuwf8jayq1.tenbytecdn.com/live/bernama/playlist.m3u8",
            duration = "LIVE",
            quality = "1080p FHD",
            description = "Saluran berita 24 jam Pertubuhan Berita Nasional Malaysia (BERNAMA). Liputan holistik ekonomi, politik, dan serantau.",
            servers = listOf(VideoServer("MYTV Tenbyte (1080p)", "https://cuwf8jayq1.tenbytecdn.com/live/bernama/playlist.m3u8")),
            isSeries = false
        ),
        Video(
            id = "live_cna",
            title = "CNA (Channel NewsAsia)",
            thumbnailUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/10792dcd-a26e-441f-95ec-c9c5ba965205/logo.png",
            backdropUrl = "https://7d8zpfv0fh.tenbytecdn.com/web-assets/images/channels/10792dcd-a26e-441f-95ec-c9c5ba965205/banner.png",
            videoUrl = "https://cuwf8jayq1.tenbytecdn.com/live/cna/playlist.m3u8",
            duration = "LIVE",
            quality = "1080p FHD",
            description = "Saluran berita antarabangsa terkemuka Asia berpangkalan di Singapura menyiarkan analisis berita serantau dan perniagaan global.",
            servers = listOf(VideoServer("MYTV Tenbyte (1080p)", "https://cuwf8jayq1.tenbytecdn.com/live/cna/playlist.m3u8")),
            isSeries = false
        ),
        Video(
            id = "live_okey",
            title = "TV Okey",
            thumbnailUrl = "https://i.imgur.com/V2jREQ5.png",
            backdropUrl = "https://i.imgur.com/V2jREQ5.png",
            videoUrl = "https://d25tgymtnqzu8s.cloudfront.net/smil:okey/playlist.m3u8?id=3",
            duration = "LIVE",
            quality = "1080p FHD",
            description = "Saluran gaya hidup, kebudayaan dan kepelbagaian etnik Malaysia terutamanya Sabah dan Sarawak oleh RTM.",
            servers = listOf(VideoServer("RTM CloudFront (1080p)", "https://d25tgymtnqzu8s.cloudfront.net/smil:okey/playlist.m3u8?id=3")),
            isSeries = false
        ),
        Video(
            id = "live_sukan_rtm",
            title = "Sukan RTM",
            thumbnailUrl = "https://i.imgur.com/1pYYSu2.png",
            backdropUrl = "https://i.imgur.com/1pYYSu2.png",
            videoUrl = "https://d25tgymtnqzu8s.cloudfront.net/smil:sukan/playlist.m3u8?id=4",
            duration = "LIVE",
            quality = "1080p FHD",
            description = "Saluran khusus sukan 24 jam menyiarkan acara sukan antarabangsa, aksi kejohanan tempatan dan berita sukan semasa.",
            servers = listOf(VideoServer("RTM CloudFront (1080p)", "https://d25tgymtnqzu8s.cloudfront.net/smil:sukan/playlist.m3u8?id=4")),
            isSeries = false
        ),
        Video(
            id = "live_berita_rtm",
            title = "Berita RTM",
            thumbnailUrl = "https://i.imgur.com/1Xzxsgl.png",
            backdropUrl = "https://i.imgur.com/1Xzxsgl.png",
            videoUrl = "https://d25tgymtnqzu8s.cloudfront.net/smil:berita/playlist.m3u8?id=5",
            duration = "LIVE",
            quality = "1080p FHD",
            description = "Saluran berita 24 jam menyajikan liputan isu semasa, analisis berita dan perkembangan terkini dalam dan luar negara.",
            servers = listOf(VideoServer("RTM CloudFront (1080p)", "https://d25tgymtnqzu8s.cloudfront.net/smil:berita/playlist.m3u8?id=5")),
            isSeries = false
        ),
        Video(
            id = "live_parlimen_rakyat",
            title = "Parlimen (Dewan Rakyat)",
            thumbnailUrl = "https://i.postimg.cc/mgrVsTMY/header.png",
            backdropUrl = "https://i.postimg.cc/mgrVsTMY/header.png",
            videoUrl = "https://d25tgymtnqzu8s.cloudfront.net/smil:rakyat/playlist.m3u8?id=7",
            duration = "LIVE",
            quality = "1080p FHD",
            description = "Siaran langsung persidangan dan perbahasan Dewan Rakyat Parlimen Malaysia secara telus.",
            servers = listOf(VideoServer("RTM CloudFront (1080p)", "https://d25tgymtnqzu8s.cloudfront.net/smil:rakyat/playlist.m3u8?id=7")),
            isSeries = false
        ),
        Video(
            id = "live_parlimen_negara",
            title = "Parlimen (Dewan Negara)",
            thumbnailUrl = "https://i.postimg.cc/mgrVsTMY/header.png",
            backdropUrl = "https://i.postimg.cc/mgrVsTMY/header.png",
            videoUrl = "https://d25tgymtnqzu8s.cloudfront.net/smil:negara/playlist.m3u8?id=8",
            duration = "LIVE",
            quality = "1080p FHD",
            description = "Siaran langsung persidangan dan perbahasan Dewan Negara Senat Parlimen Malaysia.",
            servers = listOf(VideoServer("RTM CloudFront (1080p)", "https://d25tgymtnqzu8s.cloudfront.net/smil:negara/playlist.m3u8?id=8")),
            isSeries = false
        ),
        Video(
            id = "live_rtm_asean",
            title = "RTM ASEAN",
            thumbnailUrl = "https://i.imgur.com/skAiUxg.png",
            backdropUrl = "https://i.imgur.com/skAiUxg.png",
            videoUrl = "https://d25tgymtnqzu8s.cloudfront.net/event/smil:event1/chunklist_b2596000_slENG.m3u8",
            duration = "LIVE",
            quality = "1080p FHD",
            description = "Saluran liputan serantau dan hubungan antarabangsa negara-negara komuniti ASEAN oleh RTM.",
            servers = listOf(VideoServer("RTM CloudFront (1080p)", "https://d25tgymtnqzu8s.cloudfront.net/event/smil:event1/chunklist_b2596000_slENG.m3u8")),
            isSeries = false
        )
    )

    private val dynamicChannelMap = mapOf(
        "live_tv_alhijrah" to "e725be7c-615d-42bd-9399-fef5cf386ecb",
        "live_bernamatv" to "1a41a91b-64c1-4feb-a119-76ebd88fed4a",
        "live_cna" to "10792dcd-a26e-441f-95ec-c9c5ba965205"
    )

    @Volatile
    private var cachedTv3LiveUrl: String? = null
    @Volatile
    private var lastTv3ResolvedTime: Long = 0L

    fun isDynamicChannel(channelId: String): Boolean {
        return channelId == "live_tv3" || dynamicChannelMap.containsKey(channelId)
    }

    /**
     * Resolves fresh tokenized 1080p playback URLs on demand for dynamic Live TV channels.
     */
    suspend fun resolveDynamicStream(channelId: String): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (channelId == "live_tv3") {
            val now = System.currentTimeMillis()
            if (now - lastTv3ResolvedTime < 10 * 60 * 1000L && !cachedTv3LiveUrl.isNullOrBlank()) {
                return@withContext cachedTv3LiveUrl
            }

            // 1. Primary: Resolve official Tonton live HLS stream via dynamic endpoint
            val primaryEndpoints = listOf(
                "https://api.dani-dev.co.za/v2/stream/tonton/live/tv3",
                "https://api.dani-dev.co.za/v1/stream/tonton/live/tv3",
                "https://api.dani-dev.co.za/v2/stream/tonton/live/6420323"
            )
            for (endpoint in primaryEndpoints) {
                try {
                    val client = com.duta.movie.util.NetworkConfig.okHttpClient
                    val req = okhttp3.Request.Builder()
                        .url(endpoint)
                        .header("User-Agent", com.duta.movie.util.NetworkConfig.SHARED_USER_AGENT)
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val finalUrl = resp.request.url.toString()
                            if (finalUrl.contains(".m3u8") || finalUrl.contains("tonton")) {
                                cachedTv3LiveUrl = finalUrl
                                lastTv3ResolvedTime = now
                                android.util.Log.i("LiveTvCatalog", "Successfully resolved live_tv3 to Tonton HLS: $finalUrl")
                                return@withContext finalUrl
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("LiveTvCatalog", "Failed to resolve live_tv3 from $endpoint: ${e.message}")
                }
            }

            // 2. Resilient Backup: Fall back to Buletin TV3 live stream if HLS proxies are unreachable
            try {
                val client = com.duta.movie.util.NetworkConfig.okHttpClient
                val request = okhttp3.Request.Builder()
                    .url("https://www.youtube.com/@BuletinTV3/live")
                    .header("User-Agent", com.duta.movie.util.NetworkConfig.SHARED_USER_AGENT)
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val canonMatch = Regex("""<link\s+rel="canonical"\s+href="([^"]+)"""").find(body)
                        val canonUrl = canonMatch?.groupValues?.get(1)
                        val videoId = if (!canonUrl.isNullOrBlank() && canonUrl.contains("v=")) {
                            canonUrl.substringAfter("v=").substringBefore("&").substringBefore("?")
                        } else {
                            Regex(""""videoId":"([a-zA-Z0-9_-]{11})"""").find(body)?.groupValues?.get(1)
                        }
                        if (!videoId.isNullOrBlank() && videoId.length in 10..12) {
                            val resolved = "https://www.youtube.com/watch?v=$videoId"
                            cachedTv3LiveUrl = resolved
                            lastTv3ResolvedTime = now
                            return@withContext resolved
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("LiveTvCatalog", "Failed to resolve live_tv3 dynamic YouTube fallback: ${e.message}")
            }
            return@withContext cachedTv3LiveUrl ?: "https://api.dani-dev.co.za/v2/stream/tonton/live/tv3"
        }

        val tenbyteId = dynamicChannelMap[channelId] ?: return@withContext null
        try {
            val client = com.duta.movie.util.NetworkConfig.okHttpClient
            val jsonPayload = """{"channelId":"$tenbyteId","deviceId":"duta-livetv-client"}"""
            val body = jsonPayload.toRequestBody("application/json".toMediaTypeOrNull())
            val request = okhttp3.Request.Builder()
                .url("https://co3y6iwoio.tenbytecdn.com/api/v1/public/streaming/channel-play")
                .post(body)
                .header("Content-Type", "application/json")
                .header("User-Agent", com.duta.movie.util.NetworkConfig.SHARED_USER_AGENT)
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val respBody = response.body?.string() ?: return@withContext null
                    val json = org.json.JSONObject(respBody)
                    if (json.optBoolean("success", false)) {
                        val dataObj = json.optJSONObject("data")
                        val playbackUrl = dataObj?.optString("playbackUrl")
                        if (!playbackUrl.isNullOrBlank()) {
                            return@withContext playbackUrl
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        null
    }

    fun isLiveVideo(id: String): Boolean {
        return id.startsWith("live_") || channels.any { it.id == id }
    }

    fun getChannelById(id: String): Video? {
        return channels.find { it.id == id }
    }
}
