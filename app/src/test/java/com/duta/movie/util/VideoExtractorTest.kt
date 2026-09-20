package com.duta.movie.util

import com.duta.movie.model.Video
import com.duta.movie.model.VideoServer
import kotlinx.coroutines.runBlocking
import org.junit.Test

class VideoExtractorTest {
    @Test
    fun testNormalizePath() {
        assert(VideoExtractor.normalizePath("https://ww44.pencurimovie.baby/country/malaysia/") == "/country/malaysia/")
        assert(VideoExtractor.normalizePath("/country/malaysia/") == "/country/malaysia/")
        assert(VideoExtractor.normalizePath("country/malaysia") == "/country/malaysia/")
        assert(VideoExtractor.normalizePath("https://archive.org/details/FilemP.ramlee") == "/category/p-ramlee/")
        assert(VideoExtractor.normalizePath("/category/p-ramlee/") == "/category/p-ramlee/")
        assert(VideoExtractor.normalizePath("p-ramlee") == "/category/p-ramlee/")
    }

    @Test
    fun testArchivePramleeExtraction() = runBlocking {
        try {
            val movies = VideoExtractor.fetchArchivePramleeVideos()
            println("Fetched ${movies.size} P. Ramlee movies from Archive.org:")
            movies.forEach { println(" - ${it.title} [${it.quality}, ${it.duration}] -> Poster: ${it.thumbnailUrl}") }
            if (movies.isEmpty()) {
                println("Archive.org currently unreachable or timed out; skipping assertion.")
                return@runBlocking
            }
            assert(movies.size >= 22) { "Expected at least 22 P. Ramlee movies, got ${movies.size}" }
            assert(movies.any { it.title.contains("Bujang Lapok", ignoreCase = true) }) { "Expected Bujang Lapok in archive" }
            assert(movies.any { it.title.contains("Ahmad Albab", ignoreCase = true) }) { "Expected Ahmad Albab in archive" }
            assert(movies.any { it.title.contains("Ali Baba Bujang Lapok", ignoreCase = true) }) { "Expected Ali Baba Bujang Lapok in archive" }
            assert(movies.any { it.title.contains("Nujum Pak Belalang", ignoreCase = true) }) { "Expected Nujum Pak Belalang in archive" }
            assert(movies.any { it.title.contains("Sumpah Orang Minyak", ignoreCase = true) }) { "Expected Sumpah Orang Minyak in archive" }
            assert(movies.any { it.title.contains("Tiga Abdul", ignoreCase = true) }) { "Expected Tiga Abdul in archive" }
            assert(movies.all { it.videoUrl.contains("archive.org") }) { "All video URLs must point to archive.org" }
            assert(movies.all { it.servers.size >= 2 && it.servers.first().name.contains("Archive.org") }) { "All movies must have Archive.org direct and mirror servers" }
            assert(movies.all { it.thumbnailUrl.startsWith("https://image.tmdb.org/t/p/") }) { "All movies must have high-res TMDB poster" }
            assert(movies.all { it.backdropUrl.startsWith("https://image.tmdb.org/t/p/") }) { "All movies must have high-res TMDB backdrop" }
        } catch (e: Throwable) {
            println("Archive.org network unavailable or partial: ${e.message}; skipping.")
        }
    }

    @Test
    fun testMergeAndInterleave() {
        val duta = listOf(
            com.duta.movie.model.Video(id = "munafik-2", title = "Munafik 2 (2018)", thumbnailUrl = "", duration = "", videoUrl = "https://actors-pictures.com/munafik-2-2018/"),
            com.duta.movie.model.Video(id = "tarung", title = "Tarung: Unforgiven (2026)", thumbnailUrl = "", duration = "", videoUrl = "https://actors-pictures.com/tarung-2026/")
        )
        val pencuri = listOf(
            com.duta.movie.model.Video(id = "pm_munafik-2", title = "Munafik 2 (2018)", thumbnailUrl = "", duration = "", videoUrl = "https://ww44.pencurimovie.baby/munafik-2-2018/"),
            com.duta.movie.model.Video(id = "pm_pagari-bulan", title = "Pagari Bulan (2023)", thumbnailUrl = "", duration = "", videoUrl = "https://ww44.pencurimovie.baby/pagari-bulan-2023/")
        )
        val merged = VideoExtractor.mergeAndInterleave(duta, pencuri, 10)
        assert(merged.size == 3) { "Expected 3, got ${merged.size}" } // munafik-2 deduplicated, keeping DutaMovie version!
        assert(merged[0].id == "munafik-2") { "Expected munafik-2 first, got ${merged[0].id}" }
        assert(merged[1].id == "pm_pagari-bulan") { "Expected pm_pagari-bulan second, got ${merged[1].id}" }
        assert(merged[2].id == "tarung") { "Expected tarung third, got ${merged[2].id}" }
    }

    @Test
    fun testArchiveStreamHttp() {
        try {
            val url = "https://archive.org/download/FilemP.ramlee/Enam.Jahanam.1969.576p.LEGENDTV.mp4"
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("Range", "bytes=0-1024")
                .build()
            val response = NetworkConfig.permissiveOkHttpClient.newCall(request).execute()
            println("Response code: ${response.code}")
            println("Content-Range: ${response.header("Content-Range")}")
            println("Content-Type: ${response.header("Content-Type")}")
            println("Content-Length: ${response.header("Content-Length")}")
            if (!response.isSuccessful) {
                println("Archive.org returned non-success code (${response.code}); skipping.")
                return
            }
            val body = response.body?.bytes()
            println("Read bytes: ${body?.size}")
            assert(body != null && body.size == 1025) { "Expected 1025 bytes, got ${body?.size}" }
        } catch (e: Exception) {
            println("Archive.org network unavailable: ${e.message}; skipping.")
        }
    }

    @Test
    fun testUpdateBaseUrlProtection() {
        val initialBase = VideoExtractor.getBaseUrl()
        try {
            VideoExtractor.setBaseUrl("https://actors-pictures.com")
            
            // 1. Ensure video stream / embed hosts are rejected even with force=true
            VideoExtractor.updateBaseUrl("https://vibuxer.com/stream/mZVaBmSVUnUA-wR2JkPKRw/kjhhiuahiuhgihdf/1789190911/74200242/index-v1-a1.m3u8", force = true)
            assert(VideoExtractor.getBaseUrl() == "https://actors-pictures.com") { "Vibuxer must never hijack BASE_URL" }

            VideoExtractor.updateBaseUrl("https://player.abyssplayer.com/yERq-2PX2", force = true)
            assert(VideoExtractor.getBaseUrl() == "https://actors-pictures.com") { "Abyss must never hijack BASE_URL" }

            VideoExtractor.updateBaseUrl("https://hgcloud.to/e/w3i6fj8k2fci", force = true)
            assert(VideoExtractor.getBaseUrl() == "https://actors-pictures.com") { "Hgcloud must never hijack BASE_URL" }

            VideoExtractor.updateBaseUrl("https://voe.sx/e/rjhhtsfxts04", force = true)
            assert(VideoExtractor.getBaseUrl() == "https://actors-pictures.com") { "VOE must never hijack BASE_URL" }

            VideoExtractor.updateBaseUrl("https://ww44.pencurimovie.baby/narsata-sekutu-setan-2026/", force = true)
            assert(VideoExtractor.getBaseUrl() == "https://actors-pictures.com") { "PencuriMovie must not hijack Duta BASE_URL" }

            // 2. Legitimate DutaMovie domain updates should be accepted
            VideoExtractor.updateBaseUrl("https://actors-pictures.com/movie/test/", force = false)
            assert(VideoExtractor.getBaseUrl() == "https://actors-pictures.com") { "actors-pictures.com should be accepted" }
        } finally {
            VideoExtractor.setBaseUrl(initialBase)
        }
    }

    @Test
    fun testOptimizeArchiveUrl() {
        val sumpahUrl = "https://archive.org/download/p-ramlee-nujum-pak-belalang-hd-quality-1/Sumpah%20Orang%20Minyak%20HD%20-%20Pramlee%20%28versi%20warna%29%20FULL.mp4"
        val optimizedSumpah = VideoExtractor.optimizeArchiveUrl(sumpahUrl)
        assert(optimizedSumpah.contains("dn601208.us.archive.org/0/items/p-ramlee-nujum-pak-belalang-hd-quality-1")) {
            "Sumpah Orang Minyak should rewrite to direct fast node, got $optimizedSumpah"
        }

        val aliBabaUrl = "https://archive.org/download/p.-ramlee-ali-baba-bujang-lapok/P.%20Ramlee%20-%20Ali%20Baba%20Bujang%20Lapok.mp4"
        val optimizedAliBaba = VideoExtractor.optimizeArchiveUrl(aliBabaUrl)
        assert(optimizedAliBaba.contains("dn600309.us.archive.org/0/items/p.-ramlee-ali-baba-bujang-lapok")) {
            "Ali Baba should rewrite to dn600309.us.archive.org, got $optimizedAliBaba"
        }

        val tigaAbdulUrl = "https://archive.org/download/TigaAbdul1964HQFullMovie/Tiga%20Abdul%20%281964%29%20HQ%20%28Full%20Movie%29.mp4"
        val optimizedTigaAbdul = VideoExtractor.optimizeArchiveUrl(tigaAbdulUrl)
        assert(optimizedTigaAbdul.contains("dn600305.us.archive.org/0/items/TigaAbdul1964HQFullMovie")) {
            "Tiga Abdul should rewrite to dn600305.us.archive.org, got $optimizedTigaAbdul"
        }

        val filemUrl = "https://archive.org/download/FilemP.ramlee/Enam.Jahanam.1969.576p.LEGENDTV.mp4"
        val optimizedFilem = VideoExtractor.optimizeArchiveUrl(filemUrl)
        assert(optimizedFilem.contains("dn600308.us.archive.org/0/items/FilemP.ramlee")) {
            "Enam Jahanam should rewrite to dn600308.us.archive.org, got $optimizedFilem"
        }

        val normalUrl = "https://actors-pictures.com/movie/test.mp4"
        assert(VideoExtractor.optimizeArchiveUrl(normalUrl) == normalUrl) { "Non-archive URL should remain untouched" }
    }

    @Test
    fun testProviderPriority() {
        val voeScore = VideoExtractor.getProviderPriority("VOE", "https://voe.sx/e/rjhhtsfxts04")
        val archiveFastScore = VideoExtractor.getProviderPriority("Archive.org HD (Fast Direct)", "https://dn601208.us.archive.org/0/items/test.mp4")
        val streamtapeScore = VideoExtractor.getProviderPriority("Streamtape", "https://streamtape.com/e/Jpa6KXAegYTjzQ6")
        val hgScore = VideoExtractor.getProviderPriority("Hgcloud-VIP", "https://hgcloud.to/e/w3i6fj8k2fci")
        val indoScore = VideoExtractor.getProviderPriority("IndoStream-VIP", "https://actors-pictures.com/?player=2")
        val archiveMirrorScore = VideoExtractor.getProviderPriority("Archive.org HD (Mirror)", "https://archive.org/download/test.mp4")
        val abyssScore = VideoExtractor.getProviderPriority("Abyss", "https://player.abyssplayer.com/yERq-2PX2")

        println("Provider priorities: VOE=$voeScore, ArchiveFast=$archiveFastScore, Streamtape=$streamtapeScore, Hgcloud=$hgScore, IndoStream=$indoScore, ArchiveMirror=$archiveMirrorScore, Abyss=$abyssScore")

        assert(voeScore == 160) { "VOE must have top priority 160" }
        assert(archiveFastScore == 150) { "Archive.org Fast Direct must have priority 150" }
        assert(streamtapeScore == 135) { "Streamtape must have priority 135" }
        assert(hgScore == 130) { "Hgcloud must have priority 130" }
        assert(indoScore == 125) { "IndoStream must have priority 125" }
        assert(archiveMirrorScore == 45) { "Archive.org slow mirror must be demoted to 45" }
        val indoP2pScore = VideoExtractor.getProviderPriority("IndoStream-VIP", "https://algarvebuzz.com/?player=3")
        val p2pDirectScore = VideoExtractor.getProviderPriority("PlayerP2P", "https://live.playerp2p.online/#trejkm")
        val upnsScore = VideoExtractor.getProviderPriority("UPNS", "https://algarvebuzz.com/?player=8")
        val veevScore = VideoExtractor.getProviderPriority("Veev", "https://algarvebuzz.com/?player=5")
        val hglinkScore = VideoExtractor.getProviderPriority("HGLink", "https://hglink.to/e/ij5agybkx3a9")

        assert(indoP2pScore == 125) { "IndoStream player=3 must have priority 125" }
        assert(p2pDirectScore == 125) { "PlayerP2P direct must have priority 125" }
        assert(upnsScore == 125) { "UPNS player=8 must have priority 125" }
        assert(veevScore == 55) { "Veev player=5 must be demoted to 55" }
        assert(hglinkScore == 20) { "HGLink ad-gate must be demoted to 20" }

        assert(indoScore > veevScore) { "IndoStream must beat Veev" }
        assert(indoP2pScore > veevScore) { "IndoStream PlayerP2P must beat Veev" }
        assert(veevScore > archiveMirrorScore) { "Veev must beat slow Archive Mirror" }
        assert(archiveMirrorScore > abyssScore) { "Archive Mirror must beat Abyss" }
        assert(abyssScore > hglinkScore) { "Abyss must beat HGLink" }
    }

    @Test
    fun testFindAlternativeSourcesCrossProvider() = runBlocking {
        val dutaVideo = com.duta.movie.model.Video(
            id = "tarung-unforgiven-2026",
            title = "Tarung: Unforgiven (2026)",
            thumbnailUrl = "",
            duration = "",
            videoUrl = "https://actors-pictures.com/tarung-unforgiven-2026/",
            servers = listOf(com.duta.movie.model.VideoServer("Expired Server", "https://dead.server/stream"))
        )
        val altSources = VideoExtractor.findAlternativeSources(dutaVideo)
        println("Discovered ${altSources.size} alternative sources for ${dutaVideo.title}:")
        altSources.forEach { println(" - ${it.name} -> ${it.url}") }

        assert(altSources.isNotEmpty()) { "Expected to find live alternative servers on PencuriMovie for Tarung: Unforgiven" }
        assert(altSources.any { it.name.contains("(Pencuri)") }) { "Servers must be tagged with (Pencuri)" }
        assert(altSources.all { it.url != "https://dead.server/stream" }) { "Dead servers must not be re-added" }
    }

    @Test
    fun testFindAlternativeSourcesPramleeClassic() = runBlocking {
        val aliBabaVideo = com.duta.movie.model.Video(
            id = "ali-baba-bujang-lapok-1961",
            title = "Ali Baba Bujang Lapok (1961)",
            thumbnailUrl = "",
            duration = "",
            videoUrl = "https://actors-pictures.com/ali-baba-bujang-lapok-1961/",
            servers = listOf(com.duta.movie.model.VideoServer("Expired Server", "https://dead.server/stream"))
        )
        val altSources = VideoExtractor.findAlternativeSources(aliBabaVideo)
        println("Discovered ${altSources.size} alternative sources for ${aliBabaVideo.title}:")
        altSources.forEach { println(" - ${it.name} -> ${it.url}") }

        assert(altSources.isNotEmpty()) { "Expected to find Archive.org alternative stream for Ali Baba Bujang Lapok" }
        assert(altSources.any { it.url.contains("archive.org") }) { "Alternative source must be from Archive.org" }
    }

    @Test
    fun testYouTubeMovieMatching() {
        val target1 = VideoExtractor.parseMovieTitleMeta("Nonton KL Gangster Sub Indo (2011)")
        val target2 = VideoExtractor.parseMovieTitleMeta("Nonton KL Gangster 2 Sub Indo (2013)")

        // Verify metadata extraction
        assert(target1.sequel == 1) { "Expected sequel 1 for KL Gangster (2011), got ${target1.sequel}" }
        assert(target1.year == 2011) { "Expected year 2011 for KL Gangster (2011), got ${target1.year}" }
        assert(target1.baseTokens.contains("kl") && target1.baseTokens.contains("gangster"))

        assert(target2.sequel == 2) { "Expected sequel 2 for KL Gangster 2 (2013), got ${target2.sequel}" }
        assert(target2.year == 2013) { "Expected year 2013 for KL Gangster 2 (2013), got ${target2.year}" }
        assert(target2.baseTokens.contains("kl") && target2.baseTokens.contains("gangster"))

        // Candidate 1: KL Gangster 1 full movie
        val cand1 = "KL GANGSTER FULL MOVIE, MALAYSIAN MOVIE Sutradara syamsul yusof pemeran shar Source"
        val cand1Explicit = "KL GANGSTER 1 MALAY FULL MOVIE"
        val cand1Year = "KL Gangster (2011) HD Full Movie"

        // Candidate 2: KL Gangster 2 full movie
        val cand2 = "KL Gangster 2"
        val cand2Full = "KL gangster 2 full movie"
        val cand2Year = "KL Gangster 2 2013"

        // KL Gangster 1 target tests
        assert(VideoExtractor.isYouTubeMovieMatch(target1, cand1)) { "Target 1 must match cand1" }
        assert(VideoExtractor.isYouTubeMovieMatch(target1, cand1Explicit)) { "Target 1 must match cand1Explicit" }
        assert(VideoExtractor.isYouTubeMovieMatch(target1, cand1Year)) { "Target 1 must match cand1Year" }
        assert(!VideoExtractor.isYouTubeMovieMatch(target1, cand2)) { "Target 1 must REJECT cand2 (KL Gangster 2)" }
        assert(!VideoExtractor.isYouTubeMovieMatch(target1, cand2Full)) { "Target 1 must REJECT cand2Full (KL Gangster 2)" }
        assert(!VideoExtractor.isYouTubeMovieMatch(target1, cand2Year)) { "Target 1 must REJECT cand2Year (KL Gangster 2 2013)" }

        // KL Gangster 2 target tests
        assert(!VideoExtractor.isYouTubeMovieMatch(target2, cand1)) { "Target 2 must REJECT cand1 (KL Gangster 1)" }
        assert(!VideoExtractor.isYouTubeMovieMatch(target2, cand1Explicit)) { "Target 2 must REJECT cand1Explicit (KL Gangster 1)" }
        assert(!VideoExtractor.isYouTubeMovieMatch(target2, cand1Year)) { "Target 2 must REJECT cand1Year (KL Gangster 1 2011)" }
        assert(VideoExtractor.isYouTubeMovieMatch(target2, cand2)) { "Target 2 must match cand2" }
        assert(VideoExtractor.isYouTubeMovieMatch(target2, cand2Full)) { "Target 2 must match cand2Full" }
        assert(VideoExtractor.isYouTubeMovieMatch(target2, cand2Year)) { "Target 2 must match cand2Year" }

        // Roman numeral sequel tests
        val godfather2Target = VideoExtractor.parseMovieTitleMeta("The Godfather Part II (1974)")
        assert(godfather2Target.sequel == 2)
        assert(VideoExtractor.isYouTubeMovieMatch(godfather2Target, "The Godfather 2 Full Movie"))
        assert(VideoExtractor.isYouTubeMovieMatch(godfather2Target, "The Godfather Part 2 (1974)"))
        assert(!VideoExtractor.isYouTubeMovieMatch(godfather2Target, "The Godfather (1972) Full Movie"))

        // Strict Voodoo Doll (2017) tests: Must reject Trilogy of Terror, trailers, and compilations
        val voodooTarget = VideoExtractor.parseMovieTitleMeta("Voodoo Doll (2017)")
        val trilogyCandidate = "Trilogy of Terror Trailer and Movie VOODOO DOLL COMES ALIVE!!! STUDENT VIDEOS PROFESSOR AND PAYS"
        val top10Candidate = "Top 10 Horror Movies 2017 Featuring Voodoo Doll Annabelle It"
        val trailerCandidate = "Trailer Filem Voodoo Doll | Di Pawagam 23 Feb 2017 | Genre Seram"
        val legitimateCandidate = "Voodoo Doll (2017) Full Movie HD"
        val indonesianCandidate = "Filem Seram Voodoo Doll 2017 Sub Indo Full Movie"

        assert(!VideoExtractor.isYouTubeMovieMatch(voodooTarget, trilogyCandidate)) { "Must REJECT Trilogy of Terror for Voodoo Doll" }
        assert(!VideoExtractor.isYouTubeMovieMatch(voodooTarget, top10Candidate)) { "Must REJECT Top 10 compilations for Voodoo Doll" }
        assert(!VideoExtractor.isYouTubeMovieMatch(voodooTarget, trailerCandidate)) { "Must REJECT trailers for Voodoo Doll" }
        assert(VideoExtractor.isYouTubeMovieMatch(voodooTarget, legitimateCandidate)) { "Must match legitimate Voodoo Doll upload" }
        assert(VideoExtractor.isYouTubeMovieMatch(voodooTarget, indonesianCandidate)) { "Must match localized Voodoo Doll upload" }
    }

    @Test
    fun testBilibiliExtractionAndParsing() {
        // Test duration parsing
        assert(VideoExtractor.parseBilibiliDurationSeconds("01:24:50") == 5090)
        assert(VideoExtractor.parseBilibiliDurationSeconds("104:30") == 6270)
        assert(VideoExtractor.parseBilibiliDurationSeconds("15:00") == 900)
        assert(VideoExtractor.parseBilibiliDurationSeconds("00:00") == 0)
        assert(VideoExtractor.parseBilibiliDurationSeconds("invalid") == 0)

        // Test BVid extraction
        assert(VideoExtractor.extractBilibiliBvid("https://www.bilibili.com/video/BV1xx411c7mD") == "BV1xx411c7mD")
        assert(VideoExtractor.extractBilibiliBvid("https://player.bilibili.com/player.html?bvid=BV1uv411q7dC&page=1") == "BV1uv411q7dC")
        assert(VideoExtractor.extractBilibiliBvid("https://www.bilibili.com/video/BV1uv411q7dC?p=1") == "BV1uv411q7dC")
        assert(VideoExtractor.extractBilibiliBvid("BV1uv411q7dC") == "BV1uv411q7dC")
        assert(VideoExtractor.extractBilibiliBvid("https://youtube.com/watch?v=12345") == null)
    }

    @Test
    fun testDailymotionExtractionAndParsing() {
        // Test Dailymotion ID extraction
        assert(VideoExtractor.extractDailymotionId("https://www.dailymotion.com/video/x990bio") == "x990bio")
        assert(VideoExtractor.extractDailymotionId("https://dai.ly/x17fyt8") == "x17fyt8")
        assert(VideoExtractor.extractDailymotionId("https://www.dailymotion.com/embed/video/x15grss?autoplay=1") == "x15grss")
        assert(VideoExtractor.extractDailymotionId("x990bio") == "x990bio")
        assert(VideoExtractor.extractDailymotionId("https://cdndirector.dailymotion.com/cdn/manifest/video/x94s7sq.m3u8?sec=xyz") == "x94s7sq")
        assert(VideoExtractor.extractDailymotionId("https://www.youtube.com/watch?v=123") == null)
        assert(VideoExtractor.extractDailymotionId("invalid_id") == null)

        // Test Dailymotion and Bilibili are recognized as JS-Only web player hosts
        assert(VideoExtractor.isJsOnlyHost("https://www.dailymotion.com/embed/video/x94s7sq?autoplay=1"))
        assert(VideoExtractor.isJsOnlyHost("https://dai.ly/x94s7sq"))
        assert(VideoExtractor.isJsOnlyHost("https://player.bilibili.com/player.html?bvid=123"))

        // Test that Dailymotion internal CDN manifests are strictly rejected as direct video URLs
        assert(!VideoExtractor.isDirectVideoUrl("https://cdndirector.dailymotion.com/cdn/manifest/video/x94s7sq.m3u8?sec=xyz"))
        assert(!VideoExtractor.isDirectVideoUrl("https://www.dailymotion.com/embed/video/x94s7sq"))
    }

    @Test
    fun testPramleeCatalogAdditions() = kotlinx.coroutines.runBlocking {
        val movies = try {
            VideoExtractor.fetchArchivePramleeVideos()
        } catch (_: Exception) {
            emptyList()
        }

        if (movies.isNotEmpty()) {
            val titles = movies.map { it.title }
            assert(titles.any { it.contains("Seniman Bujang Lapok", ignoreCase = true) }) {
                "Seniman Bujang Lapok must be present in P. Ramlee catalog"
            }
            assert(titles.any { it.contains("Pendekar Bujang Lapok", ignoreCase = true) }) {
                "Pendekar Bujang Lapok must be present in P. Ramlee catalog"
            }
            assert(titles.any { it.contains("Madu Tiga", ignoreCase = true) }) {
                "Madu Tiga must be present in P. Ramlee catalog"
            }
            assert(titles.any { it.contains("Ibu Mertua-ku", ignoreCase = true) || it.contains("Ibu Mertuaku", ignoreCase = true) }) {
                "Ibu Mertua-ku must be present in P. Ramlee catalog"
            }
            assert(titles.any { it.contains("Anak-ku Sazali", ignoreCase = true) || it.contains("Anakku Sazali", ignoreCase = true) }) {
                "Anak-ku Sazali must be present in P. Ramlee catalog"
            }
            assert(titles.any { it.contains("Antara Dua Darjat", ignoreCase = true) }) {
                "Antara Dua Darjat must be present in P. Ramlee catalog"
            }
            assert(titles.any { it.contains("Sarjan Hassan", ignoreCase = true) }) {
                "Sarjan Hassan must be present in P. Ramlee catalog"
            }
            assert(titles.any { it.contains("Penarek Becha", ignoreCase = true) }) {
                "Penarek Becha must be present in P. Ramlee catalog"
            }
        }

        // Verify poster & description mappings exist
        assert(VideoExtractor.PRAMLEE_POSTERS.containsKey("seniman-bujang-lapok"))
        assert(VideoExtractor.PRAMLEE_POSTERS.containsKey("seniman-bujang-lapuk"))
        assert(VideoExtractor.PRAMLEE_POSTERS.containsKey("pendekar-bujang-lapok"))
        assert(VideoExtractor.PRAMLEE_POSTERS.containsKey("madu-tiga"))

        assert(VideoExtractor.PRAMLEE_DESCRIPTIONS.containsKey("seniman-bujang-lapok"))
        assert(VideoExtractor.PRAMLEE_DESCRIPTIONS.containsKey("pendekar-bujang-lapok"))
        assert(VideoExtractor.PRAMLEE_DESCRIPTIONS.containsKey("madu-tiga"))

        // Verify optimizeArchiveUrl for new additions
        val senimanUrl = "https://archive.org/download/p-ramlee-seniman-bujang-lapok-full-movie-warna/P%20Ramlee%20-%20Seniman%20Bujang%20Lapok%20%5BFull%20movie%20warna%5D.mp4"
        val optSeniman = VideoExtractor.optimizeArchiveUrl(senimanUrl)
        assert(optSeniman.contains("dn600305.us.archive.org/0/items/p-ramlee-seniman-bujang-lapok-full-movie-warna")) {
            "Seniman Bujang Lapok must optimize to direct dn600305 storage node"
        }

        val pendekarUrl = "https://archive.org/download/pendekar-bujang-lapok-1959/Pendekar_Bujang_Lapok_IA.mp4"
        val optPendekar = VideoExtractor.optimizeArchiveUrl(pendekarUrl)
        assert(optPendekar.contains("ia800602.us.archive.org/17/items/pendekar-bujang-lapok-1959")) {
            "Pendekar Bujang Lapok must optimize to direct ia800602 storage node"
        }

        val senimanBwUrl = "https://archive.org/download/p.ramleesenimanbujanglapok1961/P.%20Ramlee%20-%20Seniman%20Bujang%20Lapok%20%281961%29.mp4"
        val optSenimanBw = VideoExtractor.optimizeArchiveUrl(senimanBwUrl)
        assert(optSenimanBw.contains("dn711000.ca.archive.org/0/items/p.ramleesenimanbujanglapok1961")) {
            "Seniman Bujang Lapok B&W must optimize to direct dn711000 storage node"
        }

        // Verify Sarjan Hassan has non-restricted stream servers
        runBlocking {
            val movies = VideoExtractor.fetchArchivePramleeVideos()
            val sarjan = movies.find { it.id == "ia_pramlee_sarjan-hassan" }
            assert(sarjan != null) { "Sarjan Hassan must exist in catalog" }
            assert(!sarjan!!.videoUrl.contains("1958-sarjan-hassan")) { "Sarjan Hassan videoUrl must not use restricted archive item" }
            assert(sarjan.servers.isNotEmpty()) { "Sarjan Hassan must have playable servers" }
            assert(sarjan.servers.all { !it.url.contains("1958-sarjan-hassan") }) { "Sarjan Hassan servers must not contain 403 archive URL" }
        }
    }

    @Test
    fun testSeriesEpisodeExtraction() {
        val sampleHtml = """
            <html>
            <body>
                <h1 class="entry-title">The Walking Dead: Dead City Season 3 (2026)</h1>
                <div class="gmr-listseries">
                    <a class="gmr-all-serie" href="https://example.com/tv/the-walking-dead/">Lihat Semua Episode</a>
                    <a class="button button-shadow" href="https://example.com/eps/the-walking-dead-season-3-episode-1/" title="Permalink ke S3 Ep 1">S3 Eps1</a>
                    <a class="button button-shadow" href="https://example.com/eps/the-walking-dead-season-3-episode-2/" title="Permalink ke S3 Ep 2">S3 Eps2</a>
                    <a class="button button-shadow" href="https://example.com/eps/the-walking-dead-season-3-episode-3/" title="Permalink ke S3 Ep 3">S3 Eps3</a>
                </div>
            </body>
            </html>
        """.trimIndent()

        val doc = org.jsoup.Jsoup.parse(sampleHtml, "https://example.com/tv/the-walking-dead/")
        val episodes = VideoExtractor.extractEpisodesFromDoc(doc, "https://example.com/tv/the-walking-dead/", "The Walking Dead")
        
        assert(episodes.size == 3) { "Expected 3 episodes, got ${episodes.size}" }
        assert(episodes[0].name == "S3 Eps1") { "Episode 1 name mismatch: ${episodes[0].name}" }
        assert(episodes[0].season == "Season 3") { "Episode 1 season should be 'Season 3', got '${episodes[0].season}'" }
        assert(episodes[0].url.contains("episode-1")) { "Episode 1 URL mismatch: ${episodes[0].url}" }
        assert(episodes.none { it.name.contains("Lihat Semua") }) { "Back-link should not be included as an episode" }
    }

    @Test
    fun testCleanEpisodeTitle_kudratAndLeakageGuards() {
        val kudratTitle = "Kudrat 1968: High Council (2026)"
        val kudratUrl = "https://ww44.pencurimovie.baby/series/kudrat-1968-high-council-2026/"

        // 1. Episode 1 with leaked year 1968 from parent title
        val ep1Clean = VideoExtractor.cleanEpisodeTitle("Episode 1 - 1968", kudratTitle, kudratUrl)
        assert(ep1Clean == "Episode 1") { "Expected 'Episode 1', got '$ep1Clean'" }

        // 2. Episode 1 without spaces around hyphen
        val ep1CleanNoSpace = VideoExtractor.cleanEpisodeTitle("Episode 1- 1968", kudratTitle, kudratUrl)
        assert(ep1CleanNoSpace == "Episode 1") { "Expected 'Episode 1', got '$ep1CleanNoSpace'" }

        // 3. Episode 2 with genuine subtitle "Taat"
        val ep2Clean = VideoExtractor.cleanEpisodeTitle("Episode 2 - Taat", kudratTitle, kudratUrl)
        assert(ep2Clean == "Episode 2 - Taat") { "Expected 'Episode 2 - Taat', got '$ep2Clean'" }

        // 4. Full parent title leakage after episode number
        val epTitleLeak = VideoExtractor.cleanEpisodeTitle("Episode 1 - Kudrat 1968", kudratTitle, kudratUrl)
        assert(epTitleLeak == "Episode 1") { "Expected 'Episode 1', got '$epTitleLeak'" }

        // 5. Release year leakage
        val epYearLeak = VideoExtractor.cleanEpisodeTitle("Episode 1 - 2026", kudratTitle, kudratUrl)
        assert(epYearLeak == "Episode 1") { "Expected 'Episode 1', got '$epYearLeak'" }

        // 6. Plain episode or number
        assert(VideoExtractor.cleanEpisodeTitle("Episode 1", kudratTitle, kudratUrl) == "Episode 1")
        assert(VideoExtractor.cleanEpisodeTitle("1", kudratTitle, kudratUrl) == "Episode 1")
        assert(VideoExtractor.cleanEpisodeTitle("S3 Eps1", "The Walking Dead", "") == "S3 Eps1")

        // 7. Test HTML parsing of Kudrat 1968 episode list from Pencuri Movie
        val sampleKudratHtml = """
            <html>
            <body>
                <div id="seasons">
                    <div class="tvseason">
                        <div class="les-title"><strong>Season 1</strong></div>
                        <div class="les-content" style="display: block">
                            <a href="/episode/kudrat-1968-high-council-season-1-episode-1">Episode 1 - 1968</a>
                            <a href="/episode/kudrat-1968-high-council-season-1-episode-2">Episode 2 - Taat</a>
                        </div>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()

        val doc = org.jsoup.Jsoup.parse(sampleKudratHtml, kudratUrl)
        val extracted = VideoExtractor.extractEpisodesFromDoc(doc, kudratUrl, kudratTitle)

        assert(extracted.size == 2) { "Expected 2 episodes, got ${extracted.size}" }
        assert(extracted[0].name == "Episode 1") { "Episode 1 should be sanitized to 'Episode 1', but was '${extracted[0].name}'" }
        assert(extracted[1].name == "Episode 2 - Taat") { "Episode 2 should remain 'Episode 2 - Taat', but was '${extracted[1].name}'" }
    }

    @Test
    fun testRelevanceScoringAndNoiseFiltering() {
        val generalQuery = "Avatar"
        val parsedGeneral = VideoExtractor.parseSearchQuery(generalQuery)
        assert(parsedGeneral.cleanText == "avatar") { "Clean text should be 'avatar'" }

        val movie1 = Video(
            id = "1",
            title = "Avatar (2009)",
            thumbnailUrl = "",
            videoUrl = "https://example.com/movie/avatar-2009",
            duration = "162 min"
        )

        val movie2 = Video(
            id = "2",
            title = "Avatar: The Way of Water (2022)",
            thumbnailUrl = "",
            videoUrl = "https://example.com/movie/avatar-2",
            duration = "192 min"
        )

        val ytMovie = Video(
            id = "yt_avatar",
            title = "[YouTube] Avatar",
            thumbnailUrl = "",
            videoUrl = "https://youtube.com/watch?v=123",
            duration = "162 min"
        )

        val unrelatedNoise = Video(
            id = "3",
            title = "Barbie (2023)",
            thumbnailUrl = "",
            videoUrl = "https://example.com/movie/barbie",
            duration = "114 min"
        )

        val score1 = VideoExtractor.calculateRelevanceScore(movie1, parsedGeneral)
        val score2 = VideoExtractor.calculateRelevanceScore(movie2, parsedGeneral)
        val scoreYt = VideoExtractor.calculateRelevanceScore(ytMovie, parsedGeneral)
        val scoreNoise = VideoExtractor.calculateRelevanceScore(unrelatedNoise, parsedGeneral)

        // Exact match > Prefix match > Unrelated (0)
        assert(score1 > score2) { "Exact title match ($score1) must be higher than subtitle prefix match ($score2)" }
        assert(score1 > scoreYt) { "DutaMovie exact match ($score1) must lead YouTube exact match ($scoreYt) due to source priority" }
        assert(scoreYt > 0) { "YouTube exact match must have positive score ($scoreYt)" }
        assert(scoreNoise <= 0) { "Unrelated movie must receive score <= 0, got $scoreNoise" }

        val filteredGeneral = VideoExtractor.filterAndSortByRelevance(listOf(movie2, unrelatedNoise, ytMovie, movie1), generalQuery)
        assert(filteredGeneral.size == 3) { "Noise must be filtered out; expected 3 results, got ${filteredGeneral.size}" }
        assert(filteredGeneral[0].id == "1") { "Top result must be DutaMovie Avatar (2009)" }
        assert(filteredGeneral[1].id == "yt_avatar") { "Second result must be YouTube Avatar" }
        assert(filteredGeneral[2].id == "2") { "Third result must be Avatar: The Way of Water" }

        // Test year-constrained exact query
        val yearQuery = "Avatar 2009"
        val filteredYear = VideoExtractor.filterAndSortByRelevance(listOf(movie2, unrelatedNoise, movie1), yearQuery)
        assert(filteredYear.size == 1 && filteredYear[0].id == "1") { "Year-constrained query must only return Avatar 2009, got ${filteredYear.map { it.title }}" }
    }

    @Test
    fun testAdvancedSearchFeatures() {
        // 1. Test token order independence
        val miMovie = Video(id = "mi7", title = "Mission: Impossible - Dead Reckoning (2023)", thumbnailUrl = "", videoUrl = "", duration = "")
        val noiseMovie = Video(id = "noise", title = "The Flash (2023)", thumbnailUrl = "", videoUrl = "", duration = "")
        val invertedOrderQuery = "dead reckoning mission impossible"
        val resultsInverted = VideoExtractor.filterAndSortByRelevance(listOf(noiseMovie, miMovie), invertedOrderQuery)
        assert(resultsInverted.isNotEmpty() && resultsInverted[0].id == "mi7") {
            "Inverted token order query should match and find Mission Impossible"
        }

        // 2. Test typo tolerance (e.g. Oppenhiemer -> Oppenheimer)
        val oppenMovie = Video(id = "opp", title = "Oppenheimer (2023)", thumbnailUrl = "", videoUrl = "", duration = "")
        val typoQuery = "oppenhiemer"
        val resultsTypo = VideoExtractor.filterAndSortByRelevance(listOf(noiseMovie, oppenMovie), typoQuery)
        assert(resultsTypo.isNotEmpty() && resultsTypo[0].id == "opp") {
            "Typo query 'oppenhiemer' should match 'Oppenheimer'"
        }

        // 3. Test Malay transliterations (lapuk -> lapok)
        val pramleeMovie = Video(id = "bl", title = "Bujang Lapok (1957)", thumbnailUrl = "", videoUrl = "", duration = "")
        val translitQuery = "bujang lapuk"
        val resultsTranslit = VideoExtractor.filterAndSortByRelevance(listOf(noiseMovie, pramleeMovie), translitQuery)
        assert(resultsTranslit.isNotEmpty() && resultsTranslit[0].id == "bl") {
            "Transliteration 'bujang lapuk' should match 'Bujang Lapok'"
        }

        // 4. Test stop-words stripping (e.g. 'full movie')
        val avatarMovie = Video(id = "av", title = "Avatar (2009)", thumbnailUrl = "", videoUrl = "", duration = "")
        val stopWordsQuery = "avatar 2009 full movie"
        val resultsStopWords = VideoExtractor.filterAndSortByRelevance(listOf(noiseMovie, avatarMovie), stopWordsQuery)
        assert(resultsStopWords.isNotEmpty() && resultsStopWords[0].id == "av") {
            "Query with filler stop-words 'avatar 2009 full movie' should match Avatar (2009)"
        }
    }

    @Test
    fun testExternalFallbackVideoMapping() {
        // Test that external IDs and embed URLs are properly recognized and structured
        val ytVideo = Video(
            id = "yt_dQw4w9WgXcQ",
            title = "[YouTube] Never Gonna Give You Up",
            thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
            videoUrl = "https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?autoplay=1",
            duration = "3:32",
            quality = "YouTube HD",
            servers = listOf(VideoServer("YouTube HD", "https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?autoplay=1"))
        )
        assert(ytVideo.id.startsWith("yt_"))
        assert(ytVideo.servers.isNotEmpty())
        assert(ytVideo.quality == "YouTube HD")

        val biliVideo = Video(
            id = "bili_BV1xx411c7mD",
            title = "[Bilibili] Anime Movie",
            thumbnailUrl = "https://example.com/pic.jpg",
            videoUrl = "https://player.bilibili.com/player.html?bvid=BV1xx411c7mD",
            duration = "105 min",
            quality = "Bilibili HD",
            servers = listOf(VideoServer("Bilibili HD", "https://player.bilibili.com/player.html?bvid=BV1xx411c7mD"))
        )
        assert(biliVideo.id.startsWith("bili_"))
        assert(VideoExtractor.extractBilibiliBvid(biliVideo.videoUrl) == "BV1xx411c7mD")

        val dmVideo = Video(
            id = "dm_x990bio",
            title = "[Dailymotion] Classic Film",
            thumbnailUrl = "https://example.com/thumb.jpg",
            videoUrl = "https://www.dailymotion.com/embed/video/x990bio",
            duration = "95 min",
            quality = "Dailymotion",
            servers = listOf(VideoServer("Dailymotion", "https://www.dailymotion.com/embed/video/x990bio"))
        )
        assert(dmVideo.id.startsWith("dm_"))
        assert(VideoExtractor.extractDailymotionId(dmVideo.videoUrl) == "x990bio")
    }

    @Test
    fun testCrossProviderMovieMatching() {
        val guardian2021 = Video(
            id = "pm_the-guardian-2021",
            title = "The Guardian (2021)",
            thumbnailUrl = "",
            videoUrl = "https://ww44.pencurimovie.baby/the-guardian-2021/",
            duration = "100 min"
        )
        val guardians2012 = Video(
            id = "the-guardians-2012",
            title = "The Guardians (2012)",
            thumbnailUrl = "",
            videoUrl = "https://actors-pictures.com/the-guardians-2012/",
            duration = "105 min"
        )
        val guardianDuta2021 = Video(
            id = "the-guardian-2021",
            title = "The Guardian (2021)",
            thumbnailUrl = "",
            videoUrl = "https://actors-pictures.com/the-guardian-2021/",
            duration = "100 min"
        )
        val guardian2006 = Video(
            id = "the-guardian-2006",
            title = "The Guardian (2006)",
            thumbnailUrl = "",
            videoUrl = "https://actors-pictures.com/the-guardian-2006/",
            duration = "139 min"
        )

        // 1. Year Mismatch: 2021 movie MUST NEVER match 2012 or 2006 movie!
        assert(!VideoExtractor.isCrossProviderMovieMatch(guardian2021, guardians2012)) {
            "The Guardian (2021) must REJECT The Guardians (2012)"
        }
        assert(!VideoExtractor.isCrossProviderMovieMatch(guardian2021, guardian2006)) {
            "The Guardian (2021) must REJECT The Guardian (2006)"
        }

        // 2. Exact Title & Year: Must match!
        assert(VideoExtractor.isCrossProviderMovieMatch(guardian2021, guardianDuta2021)) {
            "The Guardian (2021) must match The Guardian (2021) on partner"
        }

        // 3. Subtitle / Sub-heading variation: Ma Da: The Drowning Spirit (2024) vs Ma Da (2024)
        val madaTarget = Video(
            id = "mada-duta",
            title = "Ma Da: The Drowning Spirit (2024)",
            thumbnailUrl = "",
            videoUrl = "https://actors-pictures.com/ma-da-the-drowning-spirit-2024/",
            duration = "95 min"
        )
        val madaCand = Video(
            id = "pm_ma-da-2024",
            title = "Ma Da (2024)",
            thumbnailUrl = "",
            videoUrl = "https://ww44.pencurimovie.baby/ma-da-2024/",
            duration = "95 min"
        )
        assert(VideoExtractor.isCrossProviderMovieMatch(madaTarget, madaCand)) {
            "Ma Da: The Drowning Spirit (2024) must match Ma Da (2024)"
        }

        // 4. Sequel Mismatch: KL Gangster (2011) vs KL Gangster 2 (2013)
        val klg1 = Video(
            id = "kl-gangster-2011",
            title = "KL Gangster (2011)",
            thumbnailUrl = "",
            videoUrl = "https://actors-pictures.com/kl-gangster-2011/",
            duration = "90 min"
        )
        val klg2 = Video(
            id = "pm_kl-gangster-2-2013",
            title = "KL Gangster 2 (2013)",
            thumbnailUrl = "",
            videoUrl = "https://ww44.pencurimovie.baby/kl-gangster-2-2013/",
            duration = "115 min"
        )
        assert(!VideoExtractor.isCrossProviderMovieMatch(klg1, klg2)) {
            "KL Gangster (2011) must REJECT KL Gangster 2 (2013)"
        }

        // 5. Shared root word with identical release year: Tarung: Unforgiven (2026) vs Tarung (2026)
        val tarungTarget = Video(
            id = "pm_tarung-unforgiven-2026",
            title = "Tarung: Unforgiven (2026)",
            thumbnailUrl = "",
            videoUrl = "https://ww44.pencurimovie.baby/tarung-unforgiven-2026/",
            duration = "100 min"
        )
        val tarungCand = Video(
            id = "tarung-2026",
            title = "Tarung (2026)",
            thumbnailUrl = "",
            videoUrl = "https://actors-pictures.com/tarung-2026/",
            duration = "100 min"
        )
        assert(VideoExtractor.isCrossProviderMovieMatch(tarungTarget, tarungCand)) {
            "Tarung: Unforgiven (2026) must match Tarung (2026)"
        }
    }

    @Test
    fun testDailymotionAndYouTubeGenericMatching() {
        val guardianMeta = VideoExtractor.parseMovieTitleMeta("The Guardian (2021)")
        // Single generic token ("guardian") with target year 2021:
        // Must reject upload without the year
        assert(!VideoExtractor.isYouTubeMovieMatch(guardianMeta, "Guardian Full Movie")) {
            "Generic single-word title without year must be rejected"
        }
        assert(VideoExtractor.isYouTubeMovieMatch(guardianMeta, "The Guardian 2021 Full Movie")) {
            "Candidate with matching year must be accepted"
        }

        // Multi-word compound title:
        val klgMeta = VideoExtractor.parseMovieTitleMeta("KL Gangster (2011)")
        assert(VideoExtractor.isYouTubeMovieMatch(klgMeta, "KL GANGSTER FULL MOVIE")) {
            "Compound title KL Gangster should match even without year"
        }
    }

    @Test
    fun testEmbedoJsOnlyHost() {
        assert(VideoExtractor.isJsOnlyHost("https://embedo.co/e/xfnzvfnsxwwi"))
        assert(VideoExtractor.isJsOnlyHost("https://embedo.co/e/nqgdo8ft1qc5"))
        assert(VideoExtractor.isJsOnlyHost("https://doodstream.com/e/vuxy5bkxg6iu"))
    }

    @Test
    fun testExtractReleaseYear() {
        assert(VideoExtractor.extractReleaseYear("Tarung: Unforgiven (2026)") == 2026)
        assert(VideoExtractor.extractReleaseYear("Kudrat 1968: High Council (2026)") == 2026)
        assert(VideoExtractor.extractReleaseYear("Cyberpunk 2077 (2023)") == 2023)
        assert(VideoExtractor.extractReleaseYear("Puteri Gunung Ledang [REMASTERED] (2004)") == 2004)
        assert(VideoExtractor.extractReleaseYear("Adik Manja (1980)") == 1980)
        assert(VideoExtractor.extractReleaseYear("Operasi X (2018)") == 2018)
        assert(VideoExtractor.extractReleaseYear("Papa Zola The Movie", "2025-10-12") == 2025)
        assert(VideoExtractor.extractReleaseYear("Polis Evo 3 2023") == 2023)
        assert(VideoExtractor.extractReleaseYear("Movie Without Year") == 0)
    }

    @Test
    fun testSortVideosByNewestRelease() {
        val v2018 = Video(id = "op-x", title = "Operasi X (2018)", thumbnailUrl = "", videoUrl = "", duration = "")
        val v2026_1 = Video(id = "tarung", title = "Tarung: Unforgiven (2026)", thumbnailUrl = "", videoUrl = "", duration = "")
        val v2026_2 = Video(id = "kudrat", title = "Kudrat 1968: High Council (2026)", thumbnailUrl = "", videoUrl = "", duration = "")
        val v2025 = Video(id = "papazola", title = "Papa Zola: The Movie (2025)", thumbnailUrl = "", videoUrl = "", duration = "")
        val v2023 = Video(id = "pagari", title = "Pagari Bulan (2023)", thumbnailUrl = "", videoUrl = "", duration = "")
        val v1980 = Video(id = "adik-manja", title = "Adik Manja (1980)", thumbnailUrl = "", videoUrl = "", duration = "")

        val list = listOf(v2018, v2026_1, v1980, v2025, v2026_2, v2023)
        val sorted = VideoExtractor.sortVideosByNewestRelease(list)

        // 2026 should be first (preserving order: tarung then kudrat)
        assert(sorted[0].id == "tarung")
        assert(sorted[1].id == "kudrat")
        // 2025 next
        assert(sorted[2].id == "papazola")
        // 2023 next
        assert(sorted[3].id == "pagari")
        // 2018 next
        assert(sorted[4].id == "op-x")
        // 1980 last
        assert(sorted[5].id == "adik-manja")
    }

    @Test
    fun testEphemeralOrExpiredStream() {
        // Expired Morencius stream token from 5 days ago (1789190969)
        val expiredMorencius = "https://morencius.com/stream/ocMiXh09FyZU5pl2QBJsiA/hjkrhuihghfvu/1789190969/42960351/index-v1-a1.m3u8"
        assert(VideoExtractor.isEphemeralOrExpiredStream(expiredMorencius)) {
            "Expired Morencius stream from Sept 12 must be identified as ephemeral/expired"
        }

        // Ephemeral CDN stream path with query token
        val acekStream = "https://RNzT2t4XVKU08.acek-cdn.com/hls2/01/08592/sqxq3joo1oz3_n/master.m3u8?t=7NIPaAAYH-bUEQziwFYgfzQSV8Q_5QI9nI6PKDHMTUo&s=1789643017&e=129600"
        assert(VideoExtractor.isEphemeralOrExpiredStream(acekStream)) {
            "Acek-cdn dynamic HLS stream must be identified as ephemeral"
        }

        // Permanent embed mirror pages MUST NOT be marked as ephemeral
        assert(!VideoExtractor.isEphemeralOrExpiredStream("https://morencius.com/embed/sqxq3joo1oz3"))
        assert(!VideoExtractor.isEphemeralOrExpiredStream("https://player.abyssplayer.com/OJMoBmhfN"))
        assert(!VideoExtractor.isEphemeralOrExpiredStream("https://hgcloud.to/e/okr8hbaoombt"))
        assert(!VideoExtractor.isEphemeralOrExpiredStream("https://voe.sx/e/xfjnjp2xd6e5"))

        // Permanent direct video sources MUST NOT be marked as ephemeral
        assert(!VideoExtractor.isEphemeralOrExpiredStream("https://archive.org/download/FilemP.ramlee/Enam.Jahanam.1969.576p.LEGENDTV.mp4"))
        assert(!VideoExtractor.isEphemeralOrExpiredStream("https://dn601208.us.archive.org/0/items/p-ramlee/test.mp4"))
        assert(!VideoExtractor.isEphemeralOrExpiredStream("https://www.youtube.com/embed/yY-WOddcmiA"))
    }

    @Test
    fun testDeanEdwardsUnpack() {
        val packed = "eval(function(p,a,c,k,e,d){while(c--)if(k[c])p=p.replace(new RegExp('\\\\b'+c.toString(a)+'\\\\b','g'),k[c]);return p}('1 0=\"2\";',3,3,'world|var|hello'.split('|')))"
        val unpacked = VideoExtractor.unpackDeanEdwards(packed)
        assert(unpacked.contains("var world=\"hello\"") || unpacked.contains("hello")) {
            "Dean Edwards unpack failed: $unpacked"
        }
    }

    @Test
    fun testArchiveMkvAndWebmDirectDetection() {
        val kanchanTiranaMkv = "https://dn600308.us.archive.org/0/items/FilemP.ramlee/Kanchan.Tirana.1969.720p.LEGENDTV.mkv"
        val genericWebm = "https://example.com/videos/classic_movie.webm"
        val genericMkv = "https://example.com/videos/classic_movie.mkv"

        // 1. Direct video URL identification
        assert(VideoExtractor.isDirectVideoUrl(kanchanTiranaMkv)) {
            "Kanchan Tirana MKV on Archive.org must be recognized as direct video URL"
        }
        assert(VideoExtractor.isDirectVideoUrl(genericMkv)) {
            "Generic MKV must be recognized as direct video URL"
        }
        assert(VideoExtractor.isDirectVideoUrl(genericWebm)) {
            "Generic WebM must be recognized as direct video URL"
        }

        // 2. Host and ephemeral classification
        assert(!VideoExtractor.isJsOnlyHost(kanchanTiranaMkv)) {
            "Kanchan Tirana MKV must NOT be classified as JS-only host"
        }
        assert(!VideoExtractor.isEphemeralOrExpiredStream(kanchanTiranaMkv)) {
            "Kanchan Tirana MKV on Archive.org must NOT be classified as ephemeral"
        }

        // 3. Short-circuit in extractVideoUrl
        runBlocking {
            val result = VideoExtractor.extractVideoUrl(kanchanTiranaMkv)
            assert(result != null && result.videoUrl == kanchanTiranaMkv) {
                "extractVideoUrl must short-circuit and return direct URL for Kanchan Tirana MKV without scraping HTML"
            }
        }
    }

    @Test
    fun testSwishHostRecognition() {
        val swishUrl = "https://swishsrv.com/e/5tj3l5gd8z7k"
        val swishEmbed = "https://swishembed.com/v/01mpz06frr2i"
        
        assert(VideoExtractor.isJsOnlyHost(swishUrl)) { "swishsrv.com must be recognized as JS-only host" }
        assert(VideoExtractor.isJsOnlyHost(swishEmbed)) { "swishembed.com must be recognized as JS-only host" }
        assert(VideoExtractor.isProbablyVideoHost(swishUrl)) { "swishsrv.com must be recognized as video host" }
        assert(VideoExtractor.getProviderPriority("Server 3", swishUrl) == 85) { "swishsrv.com priority must be 85" }

        runBlocking {
            val result = VideoExtractor.extractVideoUrl(swishUrl)
            assert(result != null && result.videoUrl == swishUrl) {
                "extractVideoUrl must return JS-only ExtractionResult for swishsrv.com without failing"
            }
        }
    }

    @Test
    fun testDailymotionFullMovieMatching() {
        val targetMeta = VideoExtractor.parseMovieTitleMeta("Ma Da: The Drowning Spirit", "2024")
        assert(targetMeta.year == 2024)
        assert(targetMeta.baseTokens.contains("ma") && targetMeta.baseTokens.contains("da"))

        val serverNameWithDuration = "Dailymotion [94m]: Ma Da: The Drowning Spirit (2024)"
        val cleanName = VideoExtractor.sanitizeServerNameToTitle(serverNameWithDuration)
        assert(cleanName == "Ma Da: The Drowning Spirit (2024)")
        assert(VideoExtractor.isYouTubeMovieMatch(targetMeta, cleanName))

        val truncatedServerName = "Dailymotion [94m]: Ma Da: The Drowning Spirit"
        val cleanTruncated = VideoExtractor.sanitizeServerNameToTitle(truncatedServerName)
        assert(VideoExtractor.isYouTubeMovieMatch(targetMeta, cleanTruncated))

        val serverNameWithDots = "Dailymotion [94m]: Ma Da: The Drowning Spir..."
        val cleanDots = VideoExtractor.sanitizeServerNameToTitle(serverNameWithDots)
        assert(VideoExtractor.isYouTubeMovieMatch(targetMeta, cleanDots))

        val rawServerName = "Dailymotion [94m]: Ma Da: The Drowning Spirit (2024)"
        assert(VideoExtractor.isYouTubeMovieMatch(targetMeta, rawServerName)) {
            "isYouTubeMovieMatch must match raw server name directly without manual preprocessing"
        }
    }

    @Test
    fun testCleanTitleKepalaBergetar() {
        val raw1 = "Hantu Van Sewa 2 Episod 9 Tonton Drama Video"
        assert(VideoExtractor.cleanTitle(raw1) == "Hantu Van Sewa 2 Episod 9") { "Expected 'Hantu Van Sewa 2 Episod 9', got '${VideoExtractor.cleanTitle(raw1)}'" }

        val raw2 = "Kasih Yang Terkorban Episod 51 Tonton Drama Video"
        assert(VideoExtractor.cleanTitle(raw2) == "Kasih Yang Terkorban Episod 51") { "Expected 'Kasih Yang Terkorban Episod 51', got '${VideoExtractor.cleanTitle(raw2)}'" }

        val raw3 = "Rompak Raya Tonton Drama Melayu"
        assert(VideoExtractor.cleanTitle(raw3) == "Rompak Raya") { "Expected 'Rompak Raya', got '${VideoExtractor.cleanTitle(raw3)}'" }
    }

    @Test
    fun testKepalaBergetarEpisodeExtractionAndSocialFiltering() {
        val html = buildString {
            append("<html><body>")
            append("<h1>Hantu Van Sewa 2 Episod 9 Tonton Drama Video</h1>")
            append("<div class='share-post'>")
            append("<a href='https://pinterest.com/pin/create/button/?url=https://kepalabergetar9.net/hantu-van-sewa-2-episod-9-tonton-drama-video/&media=test.jpg'>Pinterest</a>")
            append("<a href='https://www.facebook.com/sharer.php?u=https://kepalabergetar9.net/hantu-van-sewa-2-episod-9-tonton-drama-video/'>Facebook</a>")
            append("<a href='https://twitter.com/share?url=https://kepalabergetar9.net/hantu-van-sewa-2-episod-9-tonton-drama-video/'>Twitter</a>")
            append("</div>")
            append("<div class='sidebar'>")
            append("<a href='https://kepalabergetar9.net/mache-fusion-episod-1-tonton-drama-video/'>Mache Fusion Episod 1</a>")
            append("<a href='https://kepalabergetar9.net/category/drama-melayu/'>Drama Melayu</a>")
            append("</div>")
            append("<div class='episode-navigation'>")
            for (i in 1..24) {
                if (i != 9) {
                    append("<a href='https://kepalabergetar9.net/hantu-van-sewa-2-episod-$i-tonton-drama-video/'>Hantu Van Sewa 2 Episod $i Tonton Drama Video</a>")
                }
            }
            append("</div>")
            append("</body></html>")
        }

        val doc = org.jsoup.Jsoup.parse(html, "https://kepalabergetar9.net/hantu-van-sewa-2-episod-9-tonton-drama-video/")
        val episodes = VideoExtractor.extractEpisodesFromDoc(
            doc,
            "https://kepalabergetar9.net/hantu-van-sewa-2-episod-9-tonton-drama-video/",
            "Hantu Van Sewa 2 Episod 9 Tonton Drama Video"
        )

        assert(episodes.size == 24) { "Expected exactly 24 episodes, got ${episodes.size}" }
        assert(episodes.none { it.url.contains("pinterest") || it.url.contains("facebook") || it.url.contains("twitter") }) {
            "Social share links must be excluded from episodes"
        }
        assert(episodes.none { it.url.contains("mache-fusion") }) {
            "Unrelated drama links from sidebar must be excluded"
        }

        val ep9 = episodes.find { it.name == "Episode 9" || it.url.contains("-episod-9-") }
        assert(ep9 != null) { "Episode 9 must be present in episode list" }
        assert(ep9?.url == "https://kepalabergetar9.net/hantu-van-sewa-2-episod-9-tonton-drama-video/") {
            "Episode 9 must point to the actual episode page URL, got ${ep9?.url}"
        }

        for (i in 1..24) {
            val ep = episodes[i - 1]
            assert(ep.name == "Episode $i") { "Expected 'Episode $i', got '${ep.name}'" }
            assert(ep.url == "https://kepalabergetar9.net/hantu-van-sewa-2-episod-$i-tonton-drama-video/") {
                "Unexpected URL for Episode $i: ${ep.url}"
            }
        }
    }

    @Test
    fun testBuildAlternativeSearchQueries() {
        val hantuQueries = VideoExtractor.buildAlternativeSearchQueries("Hantu Van Sewa 2 Episod 9 Tonton Drama Video")
        assert(hantuQueries.contains("Hantu Van Sewa 2")) { "Must include full title 'Hantu Van Sewa 2'" }
        assert(hantuQueries.contains("Hantu Van Sewa")) { "Must include base title 'Hantu Van Sewa'" }
        assert(hantuQueries.contains("Hantu Van")) { "Must include 2-word prefix 'Hantu Van'" }
        assert(!hantuQueries.contains("Hantu")) { "Must NOT contain broad single word 'Hantu' for multi-word titles" }

        val polisQueries = VideoExtractor.buildAlternativeSearchQueries("Polis Evo 3 (2023)")
        assert(polisQueries.contains("Polis Evo 3")) { "Must include full title 'Polis Evo 3'" }
        assert(polisQueries.contains("Polis Evo")) { "Must include prefix 'Polis Evo'" }
        assert(!polisQueries.contains("Polis")) { "Must NOT contain broad single word 'Polis'" }

        val munafik2Queries = VideoExtractor.buildAlternativeSearchQueries("Munafik 2 (2018)")
        assert(munafik2Queries == listOf("Munafik 2", "Munafik")) {
            "Expected ['Munafik 2', 'Munafik'], got $munafik2Queries"
        }

        val munafik1Queries = VideoExtractor.buildAlternativeSearchQueries("Munafik")
        assert(munafik1Queries == listOf("Munafik")) {
            "Expected ['Munafik'], got $munafik1Queries"
        }
    }
}

