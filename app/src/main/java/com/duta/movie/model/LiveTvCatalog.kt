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
            thumbnailUrl = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/malaysia/tv-alhijrah-my.png",
            backdropUrl = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/malaysia/tv-alhijrah-my.png",
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
            thumbnailUrl = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/malaysia/bernama-tv-my.png",
            backdropUrl = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/malaysia/bernama-tv-my.png",
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
            thumbnailUrl = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/singapore/cna-sg.png",
            backdropUrl = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/singapore/cna-sg.png",
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
        ),
        Video(
            id = "live_8tv",
            title = "8TV (八度空间)",
            thumbnailUrl = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/malaysia/8tv-my.png",
            backdropUrl = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/malaysia/8tv-my.png",
            videoUrl = "https://api.dani-dev.co.za/v2/stream/tonton/live/8tv",
            duration = "LIVE",
            quality = "1080p FHD",
            description = "Saluran televisyen swasta berbahasa Cina nombor satu di Malaysia oleh Media Prima. Siaran langsung drama Hong Kong TVB, Berita Mandarin 8TV, dan program hiburan terhebat.",
            servers = listOf(
                VideoServer("Tonton HLS 1080p (Official Broadcast)", "https://api.dani-dev.co.za/v2/stream/tonton/live/8tv"),
                VideoServer("Tonton HLS (Direct Mirror)", "https://api.dani-dev.co.za/v1/stream/tonton/live/8tv")
            ),
            isSeries = false
        ),
        Video(
            id = "live_tv9",
            title = "TV9",
            thumbnailUrl = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/malaysia/tv9-my.png",
            backdropUrl = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/malaysia/tv9-my.png",
            videoUrl = "https://api.dani-dev.co.za/v2/stream/tonton/live/tv9",
            duration = "LIVE",
            quality = "1080p FHD",
            description = "Saluran keluarga dan hiburan santai oleh Media Prima. Drama Melayu tempatan, telefilem menarik, program kanak-kanak dan animasi kegemaran ramai.",
            servers = listOf(
                VideoServer("Tonton HLS 1080p (Official Broadcast)", "https://api.dani-dev.co.za/v2/stream/tonton/live/tv9"),
                VideoServer("Tonton HLS (Direct Mirror)", "https://api.dani-dev.co.za/v1/stream/tonton/live/tv9")
            ),
            isSeries = false
        ),
        Video(
            id = "live_tvs",
            title = "TVS (TV Sarawak)",
            thumbnailUrl = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/malaysia/tvs-my.png",
            backdropUrl = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/malaysia/tvs-my.png",
            videoUrl = "https://cuwf8jayq1.tenbytecdn.com/live/tvs/playlist.m3u8",
            duration = "LIVE",
            quality = "1080p FHD",
            description = "Saluran televisyen wilayah Sarawak dan Borneo menyajikan liputan berita tempatan, kepelbagaian seni budaya etnik, dokumentari alam semula jadi, dan hiburan serantau.",
            servers = listOf(
                VideoServer("MYTV Tenbyte (1080p)", "https://cuwf8jayq1.tenbytecdn.com/live/tvs/playlist.m3u8")
            ),
            isSeries = false
        ),
        Video(
            id = "live_astro_awani",
            title = "Astro Awani",
            thumbnailUrl = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/malaysia/astro-awani-my.png",
            backdropUrl = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/malaysia/astro-awani-my.png",
            videoUrl = "https://www.youtube.com/astroawani/live",
            duration = "LIVE",
            quality = "1080p FHD",
            description = "Saluran berita 24 jam nombor satu Malaysia oleh Astro. Menyajikan laporan berita pantas, analisis ekonomi mendalam, wawancara eksklusif dan siaran langsung peristiwa penting negara.",
            servers = listOf(
                VideoServer("Astro Awani 24/7 (Official Live)", "https://www.youtube.com/astroawani/live")
            ),
            isSeries = false
        ),
        Video(
            id = "live_makkah",
            title = "Makkah Live (Quran TV)",
            thumbnailUrl = "https://i.imgur.com/XdEPTqX.png",
            backdropUrl = "https://i.imgur.com/XdEPTqX.png",
            videoUrl = "https://www.youtube.com/@QuranChannelLive/live",
            duration = "LIVE",
            quality = "1080p FHD",
            description = "Saluran siaran langsung rasmi 24 jam dari Masjidil Haram, Makkah oleh Kementerian Penerangan Arab Saudi. Siaran langsung solat fardu lima waktu berjemaah, tawaf di Kaabah, serta bacaan ayat-ayat suci Al-Quran.",
            servers = listOf(
                VideoServer("Saudi Quran TV 24/7 (Official Live)", "https://www.youtube.com/@QuranChannelLive/live")
            ),
            isSeries = false
        )
    )

    private val dynamicChannelMap = mapOf(
        "live_tv_alhijrah" to "e725be7c-615d-42bd-9399-fef5cf386ecb",
        "live_bernamatv" to "1a41a91b-64c1-4feb-a119-76ebd88fed4a",
        "live_cna" to "10792dcd-a26e-441f-95ec-c9c5ba965205"
    )

    private val dynamicStreamCache = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Long>>()

    fun isDynamicChannel(channelId: String): Boolean {
        return channelId in listOf("live_tv3", "live_8tv", "live_tv9", "live_astro_awani", "live_makkah") || 
               dynamicChannelMap.containsKey(channelId)
    }

    /**
     * Resolves fresh tokenized 1080p playback URLs on demand for dynamic Live TV channels.
     */
    suspend fun resolveDynamicStream(channelId: String): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = dynamicStreamCache[channelId]
        if (cached != null && (now - cached.second) < 12 * 60 * 1000L && cached.first.isNotBlank()) {
            return@withContext cached.first
        }

        // 1. Tonton Media Prima Channels (TV3, 8TV, TV9)
        if (channelId in listOf("live_tv3", "live_8tv", "live_tv9")) {
            val slug = when (channelId) {
                "live_8tv" -> "8tv"
                "live_tv9" -> "tv9"
                else -> "tv3"
            }
            val primaryEndpoints = listOf(
                "https://api.dani-dev.co.za/v2/stream/tonton/live/$slug",
                "https://api.dani-dev.co.za/v1/stream/tonton/live/$slug",
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
                                dynamicStreamCache[channelId] = Pair(finalUrl, now)
                                android.util.Log.i("LiveTvCatalog", "Successfully resolved $channelId to Tonton HLS: $finalUrl")
                                return@withContext finalUrl
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("LiveTvCatalog", "Failed to resolve $channelId from $endpoint: ${e.message}")
                }
            }

            // Fallback to official YouTube Live for Tonton channels
            val ytFallback = when (channelId) {
                "live_8tv" -> "https://www.youtube.com/@8TVbadukongjian/live"
                "live_tv9" -> "https://www.youtube.com/@TV9MALAYSIAofficial/live"
                else -> "https://www.youtube.com/@BuletinTV3/live"
            }
            val resolvedYt = resolveYouTubeLiveToWatchUrl(ytFallback)
            if (resolvedYt != null) {
                dynamicStreamCache[channelId] = Pair(resolvedYt, now)
                return@withContext resolvedYt
            }
            return@withContext "https://api.dani-dev.co.za/v2/stream/tonton/live/$slug"
        }

        // 2. YouTube Live Channels (Astro Awani, Makkah Live)
        if (channelId in listOf("live_astro_awani", "live_makkah")) {
            val ytUrl = when (channelId) {
                "live_astro_awani" -> "https://www.youtube.com/astroawani/live"
                else -> "https://www.youtube.com/@QuranChannelLive/live"
            }
            val resolvedYt = resolveYouTubeLiveToWatchUrl(ytUrl)
            if (resolvedYt != null) {
                dynamicStreamCache[channelId] = Pair(resolvedYt, now)
                return@withContext resolvedYt
            }
            return@withContext ytUrl
        }

        // 3. Tenbyte / MYTV Channels (Alhijrah, Bernama, CNA)
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
                            dynamicStreamCache[channelId] = Pair(playbackUrl, now)
                            return@withContext playbackUrl
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        null
    }

    private fun resolveYouTubeLiveToWatchUrl(liveUrl: String): String? {
        try {
            val client = com.duta.movie.util.NetworkConfig.okHttpClient
            val request = okhttp3.Request.Builder()
                .url(liveUrl)
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
                        return "https://www.youtube.com/watch?v=$videoId"
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("LiveTvCatalog", "Failed to resolve YouTube live $liveUrl: ${e.message}")
        }
        return null
    }

    fun isLiveVideo(id: String): Boolean {
        return id.startsWith("live_") || channels.any { it.id == id }
    }

    fun getChannelById(id: String): Video? {
        return channels.find { it.id == id }
    }
}
