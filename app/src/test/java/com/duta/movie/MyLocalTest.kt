
package com.duta.movie
import org.junit.Test
import com.duta.movie.util.VideoExtractor
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
    fun testDutaMovieDomains() {
        kotlinx.coroutines.runBlocking {
            VideoExtractor.setBaseUrl("https://204.3.234.75")

            println("1. Fetching Section '/'...")
            val homeVideos = VideoExtractor.fetchVideosBySection("/", 1, 10)
            println("Home count: ${homeVideos.size}")
            homeVideos.take(3).forEach { println(" - Home item: ${it.title} | ${it.videoUrl}") }

            println("2. Fetching Section '/movie/'...")
            val movies = VideoExtractor.fetchVideosBySection("/movie/", 1, 10)
            println("Movies count: ${movies.size}")
            movies.take(3).forEach { println(" - Movie item: ${it.title} | ${it.videoUrl}") }

            println("3. Fetching Section '/serial-tv-terbaru/'...")
            val series = VideoExtractor.fetchVideosBySection("/serial-tv-terbaru/", 1, 10)
            println("Series count: ${series.size}")
            series.take(3).forEach { println(" - Series item: ${it.title} | ${it.videoUrl}") }

            println("4. Fetching Movie Details...")
            val firstMovie = movies.firstOrNull() ?: homeVideos.firstOrNull()
            if (firstMovie != null) {
                val movieDetail = VideoExtractor.fetchVideoDetails(firstMovie.videoUrl)
                println("Detail for ${firstMovie.title}: servers=${movieDetail?.servers?.size}, isSeries=${movieDetail?.isSeries}")
                movieDetail?.servers?.forEach { println("   Server: ${it.name} -> ${it.url}") }
            }

            println("5. Fetching Series Details...")
            val realSeries = series.find { it.videoUrl.contains("/tv/") || it.title.contains("Line of Fire") || it.title.contains("Season") } ?: series.firstOrNull()
            if (realSeries != null) {
                val seriesDetail = VideoExtractor.fetchVideoDetails(realSeries.videoUrl)
                println("Detail for ${realSeries.title}: isSeries=${seriesDetail?.isSeries}, episodes=${seriesDetail?.episodes?.size}, servers=${seriesDetail?.servers?.size}")
                seriesDetail?.episodes?.take(5)?.forEach { println("   Ep: ${it.name} -> ${it.url}") }
                seriesDetail?.servers?.forEach { println("   Server: ${it.name} -> ${it.url}") }
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
        assertEquals(6, genreSciFi)
    }

    @Test
    fun testSciFiNormalization() {
        assertEquals("/science-fiction/", VideoExtractor.normalizePath("/sci-fi/"))
        assertEquals("/science-fiction/", VideoExtractor.normalizePath("sci-fi"))
        assertEquals("/science-fiction/", VideoExtractor.normalizePath("/genre/sci-fi/"))
        assertEquals("/science-fiction/", VideoExtractor.normalizePath("https://ohionewsnow.com/sci-fi/"))
        assertEquals("/science-fiction/", VideoExtractor.normalizePath("/science-fiction/"))
    }
}

