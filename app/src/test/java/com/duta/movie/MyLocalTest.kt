
package com.duta.movie
import org.junit.Test
import com.duta.movie.util.VideoExtractor
import com.duta.movie.util.NetworkConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import com.duta.movie.ui.VideoViewModel

class MyLocalTest {
    @Test
    fun testNormalize() {
        val path = "/https:/ww44.pencurimovie.baby/country/malaysia/"
        val norm = VideoExtractor.normalizePath(path)
        println("Norm: " + norm)
    }

    @Test
    fun testFetchKudrat() {
        kotlinx.coroutines.runBlocking {
            val details = VideoExtractor.fetchVideoDetails("https://ww44.pencurimovie.baby/series/kudrat-1968-high-council-2026/")
            println("Kudrat details: title=${details?.title}, isSeries=${details?.isSeries}, episodes=${details?.episodes?.size}, servers=${details?.servers?.size}")
            details?.episodes?.forEach { println(" - Episode: ${it.name} | url=${it.url} | season=${it.season}") }
            details?.servers?.forEach { println(" - Server: ${it.name} | url=${it.url}") }

            assertTrue("Title should not be Unknown", details?.title != "Unknown" && details?.title?.contains("Kudrat") == true)
            assertTrue("Should be identified as series", details?.isSeries == true)
            assertTrue("Episodes should not be empty", (details?.episodes?.size ?: 0) >= 2)
            assertTrue("Servers should not be empty", (details?.servers?.size ?: 0) > 0)

            val epDetails = VideoExtractor.fetchVideoDetails("https://ww44.pencurimovie.baby/episode/kudrat-1968-high-council-season-1-episode-1")
            println("Ep1 details: title=${epDetails?.title}, isSeries=${epDetails?.isSeries}, episodes=${epDetails?.episodes?.size}, servers=${epDetails?.servers?.size}")
            epDetails?.servers?.forEach { println(" - Ep1 Server: ${it.name} | url=${it.url}") }
            assertTrue("Ep1 Title should not be Unknown", epDetails?.title != "Unknown")
            assertTrue("Ep1 Servers should not be empty", (epDetails?.servers?.size ?: 0) > 0)
        }
    }

    @Test
    fun testParkedDomainRejection() {
        val initialBase = VideoExtractor.getBaseUrl()
        VideoExtractor.updateBaseUrl("https://dutamovie.com", force = true)
        assertEquals("dutamovie.com must be blocked", initialBase, VideoExtractor.getBaseUrl())

        VideoExtractor.updateBaseUrl("http://ww38.dutamovie.com", force = true)
        assertEquals("ww38 must be blocked", initialBase, VideoExtractor.getBaseUrl())

        VideoExtractor.updateBaseUrl("https://algarvebuzz.com", force = true)
        assertEquals("algarvebuzz must be blocked", initialBase, VideoExtractor.getBaseUrl())

        VideoExtractor.updateBaseUrl("https://digitalpapercuts.com", force = true)
        assertEquals("digitalpapercuts must be blocked", initialBase, VideoExtractor.getBaseUrl())
    }

    @Test
    fun testPencuriCategories() {
        kotlinx.coroutines.runBlocking {
            val testPaths = listOf("/", "/category/movie/", "/series/", "/category/tv/", "/category/drama/", "/tv/", "/drama/", "/genre/action/")
            val pencuriBase = VideoExtractor.getPencuriBaseUrl()
            for (p in testPaths) {
                val url = "$pencuriBase$p"
                val html = VideoExtractor.fetchHtml(url)
                if (html != null) {
                    val videos = VideoExtractor.scrapeVideosFromHtml(html, url)
                    println("Pencuri path $p ($url) -> Scraped ${videos.size} videos")
                } else {
                    println("Pencuri path $p ($url) -> HTML is NULL")
                }
            }
        }
    }

    @Test
    fun testPencuriMovieLiveEndpointsAndBlockedDomains() {
        val initialBase = VideoExtractor.getBaseUrl()
        VideoExtractor.updateBaseUrl("https://204.3.234.75", force = true)
        assertEquals("204.3.234.75 must be blocked", initialBase, VideoExtractor.getBaseUrl())

        VideoExtractor.updateBaseUrl("https://tv5.rebahinxxi.auction", force = true)
        assertEquals("rebahinxxi must be blocked", initialBase, VideoExtractor.getBaseUrl())

        kotlinx.coroutines.runBlocking {
            println("1. Fetching Section '/'...")
            val homeVideos = VideoExtractor.fetchVideosBySection("/", 1, 10)
            println("Home count: ${homeVideos.size}")
            assertTrue("Home count should not be empty", homeVideos.isNotEmpty())
            homeVideos.take(3).forEach { println(" - Home item: ${it.title} | ${it.videoUrl}") }

            println("2. Fetching Section '/movies/'...")
            val movies = VideoExtractor.fetchVideosBySection("/movies/", 1, 10)
            println("Movies count: ${movies.size}")
            assertTrue("Movies count should not be empty", movies.isNotEmpty())
            movies.take(3).forEach { println(" - Movie item: ${it.title} | ${it.videoUrl}") }

            println("3. Fetching Section '/series/'...")
            val series = VideoExtractor.fetchVideosBySection("/series/", 1, 10)
            println("Series count: ${series.size}")
            assertTrue("Series count should not be empty", series.isNotEmpty())
            series.take(3).forEach { println(" - Series item: ${it.title} | ${it.videoUrl}") }

            if (movies.isNotEmpty()) {
                val sampleUrl = movies.first().videoUrl
                println("4. Fetching Movie Details for Sample ($sampleUrl)...")
                val sampleDetail = VideoExtractor.fetchVideoDetails(sampleUrl)
                println("Sample Detail: title=${sampleDetail?.title}, servers=${sampleDetail?.servers?.size}")
                sampleDetail?.servers?.forEach { println("   Server: ${it.name} -> ${it.url}") }
                assertTrue("Sample title should not be empty", !sampleDetail?.title.isNullOrEmpty())
            }

            val rawTitle = "Love Me (2024) REBAHIN BIOSKOPKEREN Sub Indo - tv5.rebahinxxi.auction"
            val cleaned = VideoExtractor.cleanTitle(rawTitle)
            println("RawTitle: '$rawTitle' -> Cleaned: '$cleaned'")
            assertEquals("Love Me (2024)", cleaned)

            val altQueries = VideoExtractor.buildAlternativeSearchQueries(rawTitle)
            println("Alt queries for '$rawTitle': $altQueries")
            assertTrue("Alt queries must contain 'Love Me'", altQueries.contains("Love Me"))

            val isVideoHost = VideoExtractor.isProbablyVideoHost("https://watch.asiastream.cc/watch?v=W90HJUZR")
            assertTrue("watch.asiastream.cc must be recognized as video host", isVideoHost)

            val serverName = VideoExtractor.identifyMirrorName("Server 1", "https://watch.asiastream.cc/watch?v=W90HJUZR")
            println("Identified AsiaStream server name: $serverName")
            assertEquals("AsiaStream", serverName)

            // Test PencuriMovie search for "Love Me"
            val pencuriSearchHtml = VideoExtractor.fetchHtml("https://ww44.pencurimovie.baby/?s=Love+Me")
            if (pencuriSearchHtml != null) {
                val foundVideos = VideoExtractor.scrapeVideosFromHtml(pencuriSearchHtml, "https://ww44.pencurimovie.baby/")
                println("Pencuri search for 'Love Me': found ${foundVideos.size} videos")
                foundVideos.take(3).forEach { println("   Found: ${it.title} -> ${it.videoUrl}") }
            }
        }
    }

    @Test
    fun testCategoryTaxonomyAndSorting() {
        // 1. Group Classification
        assertEquals(VideoViewModel.CategoryGroup.CORE, VideoViewModel.getCategoryGroup("/", "Newly Updated"))
        assertEquals(VideoViewModel.CategoryGroup.CORE, VideoViewModel.getCategoryGroup("/movie/", "Movies"))
        assertEquals(VideoViewModel.CategoryGroup.CORE, VideoViewModel.getCategoryGroup("/serial-tv-terbaru/", "TV Series"))
        assertEquals(VideoViewModel.CategoryGroup.CORE, VideoViewModel.getCategoryGroup("/box-office/", "Box-Office"))

        assertEquals(VideoViewModel.CategoryGroup.REGIONAL, VideoViewModel.getCategoryGroup("/country/malaysia/", "Malaysia"))
        assertEquals(VideoViewModel.CategoryGroup.REGIONAL, VideoViewModel.getCategoryGroup("/category/p-ramlee/", "P.Ramlee"))
        assertEquals(VideoViewModel.CategoryGroup.REGIONAL, VideoViewModel.getCategoryGroup("/country/viet-nam/", "Viet Nam"))
        assertEquals(VideoViewModel.CategoryGroup.REGIONAL, VideoViewModel.getCategoryGroup("/country/japan/", "Japan"))

        assertEquals(VideoViewModel.CategoryGroup.STREAMING, VideoViewModel.getCategoryGroup("/network/netflix/", "Netflix"))
        assertEquals(VideoViewModel.CategoryGroup.STREAMING, VideoViewModel.getCategoryGroup("/network/apple-tv/", "Apple TV+"))
        assertEquals(VideoViewModel.CategoryGroup.STREAMING, VideoViewModel.getCategoryGroup("/network/hbo/", "HBO"))

        assertEquals(VideoViewModel.CategoryGroup.GENRE, VideoViewModel.getCategoryGroup("/action/", "Action"))
        assertEquals(VideoViewModel.CategoryGroup.GENRE, VideoViewModel.getCategoryGroup("/animasi/", "Anime"))
        assertEquals(VideoViewModel.CategoryGroup.GENRE, VideoViewModel.getCategoryGroup("/comedy/", "Comedy"))
        assertEquals(VideoViewModel.CategoryGroup.GENRE, VideoViewModel.getCategoryGroup("/horror/", "Horror"))

        assertEquals(VideoViewModel.CategoryGroup.YEAR, VideoViewModel.getCategoryGroup("/release/2024/", "2024"))

        // 2. Priority Hierarchy
        assertTrue(VideoViewModel.CategoryGroup.CORE.priority < VideoViewModel.CategoryGroup.REGIONAL.priority)
        assertTrue(VideoViewModel.CategoryGroup.REGIONAL.priority < VideoViewModel.CategoryGroup.STREAMING.priority)
        assertTrue(VideoViewModel.CategoryGroup.STREAMING.priority < VideoViewModel.CategoryGroup.GENRE.priority)
        assertTrue(VideoViewModel.CategoryGroup.GENRE.priority < VideoViewModel.CategoryGroup.YEAR.priority)

        // 3. Intra-group ordering
        val coreNewly = VideoViewModel.getIntraGroupRank(VideoViewModel.CategoryGroup.CORE, "/", "Newly Updated")
        val coreMovies = VideoViewModel.getIntraGroupRank(VideoViewModel.CategoryGroup.CORE, "/movie/", "Movies")
        val coreBoxOffice = VideoViewModel.getIntraGroupRank(VideoViewModel.CategoryGroup.CORE, "/box-office/", "Box-Office")
        assertTrue(coreNewly < coreMovies)
        assertTrue(coreMovies < coreBoxOffice)

        val streamNetflix = VideoViewModel.getIntraGroupRank(VideoViewModel.CategoryGroup.STREAMING, "/network/netflix/", "Netflix")
        val streamApple = VideoViewModel.getIntraGroupRank(VideoViewModel.CategoryGroup.STREAMING, "/network/apple-tv/", "Apple TV+")
        assertTrue(streamNetflix < streamApple)

        val genreAction = VideoViewModel.getIntraGroupRank(VideoViewModel.CategoryGroup.GENRE, "/action/", "Action")
        val genreSciFi = VideoViewModel.getIntraGroupRank(VideoViewModel.CategoryGroup.GENRE, "/science-fiction/", "Science Fiction")
        assertTrue(genreAction < genreSciFi)
        assertEquals(8, genreSciFi)
    }

    @Test
    fun testOtherCategoriesFilteredOut() {
        val testCats = listOf(
            mapOf("name" to "Movies", "path" to "/movies/"),
            mapOf("name" to "Malaysia", "path" to "/country/malaysia/"),
            mapOf("name" to "Action", "path" to "/genre/action/"),
            mapOf("name" to "2024", "path" to "/release/2024/"),
            mapOf("name" to "DMCA Notice", "path" to "/dmca/"),
            mapOf("name" to "Disclaimer", "path" to "/disclaimer/"),
            mapOf("name" to "Random Noise", "path" to "/tag/random-tag/")
        )
        val normalized = VideoViewModel.sortAndNormalizeCategories(testCats)
        assertTrue(normalized.none { it["name"] == "DMCA Notice" })
        assertTrue(normalized.none { it["name"] == "Disclaimer" })
        assertTrue(normalized.none { it["name"] == "Random Noise" })
        assertTrue(normalized.any { it["name"] == "Movies" })
        assertTrue(normalized.any { it["name"] == "Malaysia" })
        assertTrue(normalized.any { it["name"] == "Action" })
        assertTrue(normalized.any { it["name"] == "2024" })
    }

    @Test
    fun testSciFiNormalization() {
        assertEquals("/genre/science-fiction/", VideoExtractor.normalizePath("/sci-fi/"))
        assertEquals("/genre/science-fiction/", VideoExtractor.normalizePath("sci-fi"))
        assertEquals("/genre/science-fiction/", VideoExtractor.normalizePath("/genre/sci-fi/"))
        assertEquals("/genre/science-fiction/", VideoExtractor.normalizePath("https://ohionewsnow.com/sci-fi/"))
        assertEquals("/genre/science-fiction/", VideoExtractor.normalizePath("/science-fiction/"))
        assertEquals("/genre/science-fiction/", VideoExtractor.normalizePath("/genre/science-fiction/"))
    }

    @Test
    fun testGenreNormalization() {
        assertEquals("/genre/action/", VideoExtractor.normalizePath("/action/"))
        assertEquals("/genre/action/", VideoExtractor.normalizePath("action"))
        assertEquals("/genre/action/", VideoExtractor.normalizePath("/genre/action/"))
        assertEquals("/genre/comedy/", VideoExtractor.normalizePath("/comedy/"))
        assertEquals("/genre/horror/", VideoExtractor.normalizePath("/horror/"))
        assertEquals("/genre/animation/", VideoExtractor.normalizePath("/animasi/"))
        assertEquals("/genre/animation/", VideoExtractor.normalizePath("/animation/"))
    }

    @Test
    fun testPencuriNormalizationMappings() {
        assertEquals("/movies/", VideoExtractor.normalizePath("/movie/"))
        assertEquals("/movies/", VideoExtractor.normalizePath("/category/movie/"))
        assertEquals("/series/", VideoExtractor.normalizePath("/serial-tv/"))
        assertEquals("/series/", VideoExtractor.normalizePath("/serial-tv-terbaru/"))
        assertEquals("/top-imdb/", VideoExtractor.normalizePath("/box-office/"))
        assertEquals("/release-year/2026/", VideoExtractor.normalizePath("/year/2026/"))
    }

    @Test
    fun testWhitelistedHostProtection() {
        // Legitimate video hosters must be whitelisted
        assertTrue(VideoExtractor.isWhitelistedHost("playsobat.xyz"))
        assertTrue(VideoExtractor.isWhitelistedHost("https://playsobat.xyz/e/fem4zkr6s0"))
        assertTrue(VideoExtractor.isWhitelistedHost("watch.asiastream.cc"))
        assertTrue(VideoExtractor.isWhitelistedHost("asiatik01.site"))
        assertTrue(VideoExtractor.isWhitelistedHost("ww44.pencurimovie.baby"))
        assertTrue(VideoExtractor.isWhitelistedHost("pencurimovie.baby"))
        assertTrue(VideoExtractor.isWhitelistedHost("streamtape.com"))
        assertTrue(VideoExtractor.isWhitelistedHost("voe.sx"))
        assertTrue(VideoExtractor.isWhitelistedHost("hgcloud.to"))
        assertTrue(VideoExtractor.isWhitelistedHost("dailymotion.com"))

        // Known dead/gate hosts or unknown spam domains must NOT be whitelisted
        org.junit.Assert.assertFalse(VideoExtractor.isWhitelistedHost("tv5.rebahinxxi.auction"))
        org.junit.Assert.assertFalse(VideoExtractor.isWhitelistedHost("204.3.234.75"))
        org.junit.Assert.assertFalse(VideoExtractor.isWhitelistedHost("listeamed.net"))
        org.junit.Assert.assertFalse(VideoExtractor.isWhitelistedHost("ww1.listeamed.net"))
        org.junit.Assert.assertFalse(VideoExtractor.isWhitelistedHost("unknown-spam-ads.com"))
        org.junit.Assert.assertFalse(VideoExtractor.isWhitelistedHost(""))
        org.junit.Assert.assertFalse(VideoExtractor.isWhitelistedHost(null))
    }

    @Test
    fun testDutaFilmIntegration() {
        // 1. Whitelisting & Helpers
        assertTrue(VideoExtractor.isWhitelistedHost("159.89.249.45"))
        assertTrue(VideoExtractor.isWhitelistedHost("http://159.89.249.45/avatar-the-way-of-water-2022/"))
        assertTrue(VideoExtractor.isDutaFilm(videoId = "df_avatar-2022"))
        assertTrue(VideoExtractor.isDutaFilm(videoUrl = "http://159.89.249.45/avatar-the-way-of-water-2022/"))
        assertEquals("df_avatar-the-way-of-water-2022", VideoExtractor.extractStableId("http://159.89.249.45/avatar-the-way-of-water-2022/"))
        assertEquals("http://159.89.249.45/avatar-the-way-of-water-2022/", VideoExtractor.resolveVideoUrl("df_avatar-the-way-of-water-2022"))

        kotlinx.coroutines.runBlocking {
            // 2. Search
            val searchResults = VideoExtractor.searchDutaFilm("avatar", 1, 10)
            println("DutaFilm search results: ${searchResults.size}")
            searchResults.forEach { println(" - DF item: ${it.id} | ${it.title} | ${it.videoUrl}") }
            assertTrue("DutaFilm search should return results", searchResults.isNotEmpty())
            assertTrue("DF item id should start with df_", searchResults.first().id.startsWith("df_"))

            // 3. Video Details & Server parsing
            val dfVideo = VideoExtractor.fetchVideoDetails("http://159.89.249.45/avatar-the-way-of-water-2022/")
            println("DF details: title=${dfVideo?.title}, servers=${dfVideo?.servers?.size}")
            dfVideo?.servers?.forEach { println(" - DF Server: ${it.name} | ${it.url}") }
            assertTrue("DF details should not be null", dfVideo != null)
            assertTrue("DF servers should contain VidHide mirror", dfVideo?.servers?.any { it.url.contains("vidhide") } == true)

            // 4. Alternative sources cross-lookup
            val mockPencuriVideo = com.duta.movie.model.Video(
                id = "pm_avatar-the-way-of-water-2022",
                title = "Avatar: The Way of Water",
                thumbnailUrl = "",
                videoUrl = "https://ww44.pencurimovie.baby/movie/avatar-the-way-of-water-2022/",
                duration = "",
                date = "2022"
            )
            val altServers = VideoExtractor.findAlternativeSources(mockPencuriVideo)
            println("Alternative servers found for Avatar: ${altServers.size}")
            altServers.forEach { println(" - Alt Server: ${it.name} | ${it.url}") }
            assertTrue("Should discover alternative mirrors from DutaFilm/Cluster", altServers.any { it.name.contains("DutaFilm") || it.url.contains("vidhide") })
        }
    }

    @Test
    fun testClusterMirrorsIntegration() {
        // 1. Cluster configuration
        assertEquals(27, VideoExtractor.CLUSTER_MIRROR_URLS.size)
        assertTrue(VideoExtractor.isClusterSite("http://165.227.237.129/"))
        assertTrue(VideoExtractor.isClusterSite("http://178.128.166.138/"))
        assertTrue(VideoExtractor.isClusterSite("https://138.68.169.162/"))
        assertTrue(VideoExtractor.isClusterSite("165.227.237.129"))
        assertTrue(VideoExtractor.isWhitelistedHost("165.227.237.129"))
        assertTrue(VideoExtractor.isWhitelistedHost("138.68.169.162"))

        // 2. Primary Priority tier
        val vidhidePriority = VideoExtractor.getProviderPriority("VidHide", "https://vidhide.org/embed/wtc8s9")
        assertEquals(128, vidhidePriority)
        val youtubePriority = VideoExtractor.getProviderPriority("YouTube", "https://www.youtube.com/embed/123")
        assertTrue("VidHide must have higher priority than YouTube fallback", vidhidePriority > youtubePriority)

        // 3. Stable ID & Video URL resolution
        val clusterUrl = "http://165.227.237.129/avatar-the-way-of-water-2022/"
        val stableId = VideoExtractor.extractStableId(clusterUrl)
        assertEquals("df_avatar-the-way-of-water-2022", stableId)
        assertTrue(VideoExtractor.isDutaFilm(videoId = stableId))
        assertTrue(VideoExtractor.isDutaFilm(videoUrl = clusterUrl))

        kotlinx.coroutines.runBlocking {
            // 4. Cluster Search
            val clusterResults = VideoExtractor.searchClusterMirrors("Avatar", 1, 10)
            println("Cluster search results: ${clusterResults.size}")
            clusterResults.forEach { println(" - Cluster item: ${it.id} | ${it.title} | ${it.videoUrl}") }
            assertTrue("Cluster search should return items", clusterResults.isNotEmpty())

            // 5. Video Details on cluster mirror
            val details = VideoExtractor.fetchVideoDetails("http://165.227.237.129/avatar-the-way-of-water-2022/")
            println("Cluster details: title=${details?.title}, servers=${details?.servers?.size}")
            details?.servers?.forEach { println(" - Cluster server: ${it.name} -> ${it.url}") }
            assertTrue("Cluster details should be parsed", details != null)
            assertTrue("Should contain VidHide mirror", details?.servers?.any { it.url.contains("vidhide") } == true)
        }
    }

    @Test
    fun testJohnWickAlternativeSources() {
        kotlinx.coroutines.runBlocking {
            val pmDetails = VideoExtractor.fetchVideoDetails("https://ww44.pencurimovie.baby/john-wick-chapter-3-parabellum-2019/")
            println("PM Details: title='${pmDetails?.title}', isSeries=${pmDetails?.isSeries}, episodes=${pmDetails?.episodes?.size}, servers=${pmDetails?.servers?.size}")
            pmDetails?.servers?.forEach { println(" - PM Server: ${it.name} -> ${it.url}") }
            pmDetails?.episodes?.forEach { println(" - PM Episode: ${it.name} -> ${it.url}") }
            val target = pmDetails ?: com.duta.movie.model.Video(
                id = "pm_john-wick-chapter-3-parabellum-2019",
                title = "John Wick: Chapter 3 – Parabellum (2019)",
                thumbnailUrl = "",
                videoUrl = "https://ww44.pencurimovie.baby/john-wick-chapter-3-parabellum-2019/",
                duration = "",
                date = "2019"
            )
            println("=== TESTING JOHN WICK ALTERNATIVE SOURCES ===")
            val queries = VideoExtractor.buildAlternativeSearchQueries(target.title)
            println("Generated queries: $queries")

            for (base in VideoExtractor.CLUSTER_MIRROR_URLS) {
                try {
                    val url = "$base/?s=John+Wick"
                    val req = okhttp3.Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
                    NetworkConfig.fastOkHttpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val html = resp.body?.string() ?: ""
                            val doc = org.jsoup.Jsoup.parse(html, url)
                            val items = doc.select("article.item, .gmr-item, article[id^='post-']")
                            val titles = items.map { it.select(".entry-title a, h2 a").text() }.filter { it.contains("Wick", ignoreCase = true) }
                            if (titles.isNotEmpty()) {
                                println("Mirror $base found ${titles.size} Wick items: $titles")
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("Mirror $base error: ${e.message}")
                }
            }

            val altServers = VideoExtractor.findAlternativeSources(target)
            println("Total alt servers discovered: ${altServers.size}")
            altServers.forEach { println("  Alt Server: ${it.name} -> ${it.url}") }
            assertTrue("Alt servers should be found for John Wick 3", altServers.isNotEmpty())

            // Test resilience: Even if misflagged as a series with dummy episode
            val misflaggedSeriesTarget = target.copy(
                isSeries = true,
                episodes = listOf(com.duta.movie.model.Episode("ep1", "Episode 1", "${target.videoUrl}ep1", ""))
            )
            val altForMisflagged = VideoExtractor.findAlternativeSources(misflaggedSeriesTarget)
            println("Alt servers discovered for misflagged target: ${altForMisflagged.size}")
            assertTrue("Fallback mirrors must be discovered even if misflagged", altForMisflagged.isNotEmpty())
        }
    }
}


