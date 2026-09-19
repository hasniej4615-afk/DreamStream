
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

