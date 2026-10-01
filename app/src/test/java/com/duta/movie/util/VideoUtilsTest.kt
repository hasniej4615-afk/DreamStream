package com.duta.movie.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoUtilsTest {

    @Test
    fun testTmdbPosterUpscalingForTv() {
        val rawTmdb = "https://image.tmdb.org/t/p/w185/v9ZtOlIJ3HS49UMtS4eli3WMenT.jpg"
        
        // Unified lightweight poster optimization (w342) ensures 100% cache sharing across TV and Mobile
        val tvPoster = VideoUtils.getOptimizedImage(rawTmdb, isTV = true, isLowRam = false)
        assertEquals("https://image.tmdb.org/t/p/w342/v9ZtOlIJ3HS49UMtS4eli3WMenT.jpg", tvPoster)

        val lowRamTvPoster = VideoUtils.getOptimizedImage(rawTmdb, isTV = true, isLowRam = true)
        assertEquals("https://image.tmdb.org/t/p/w342/v9ZtOlIJ3HS49UMtS4eli3WMenT.jpg", lowRamTvPoster)

        val mobilePoster = VideoUtils.getOptimizedImage(rawTmdb, isTV = false, isLowRam = false)
        assertEquals("https://image.tmdb.org/t/p/w342/v9ZtOlIJ3HS49UMtS4eli3WMenT.jpg", mobilePoster)
    }

    @Test
    fun testTmdbBackdropUpscalingForTv() {
        val rawTmdb = "https://image.tmdb.org/t/p/w185/backdrop123.jpg"

        // TV backdrop should be widescreen w1280
        val tvBackdrop = VideoUtils.getOptimizedBackdrop(rawTmdb, isTV = true, isLowRam = false)
        assertEquals("https://image.tmdb.org/t/p/w1280/backdrop123.jpg", tvBackdrop)

        // Low-RAM TV backdrop should be w780
        val lowRamTvBackdrop = VideoUtils.getOptimizedBackdrop(rawTmdb, isTV = true, isLowRam = true)
        assertEquals("https://image.tmdb.org/t/p/w780/backdrop123.jpg", lowRamTvBackdrop)
    }

    @Test
    fun testWordPressJetpackCdnResizing() {
        val rawWp = "https://i0.wp.com/algarvebuzz.com/wp-content/uploads/2026/09/poster-152x228.webp?resize=152,228"

        // Poster requests lightweight w=360
        val tvPoster = VideoUtils.getOptimizedImage(rawWp, isTV = true, isLowRam = false)
        assertEquals("https://i0.wp.com/algarvebuzz.com/wp-content/uploads/2026/09/poster.webp?w=360", tvPoster)

        // TV backdrop should request w=1280
        val tvBackdrop = VideoUtils.getOptimizedBackdrop(rawWp, isTV = true, isLowRam = false)
        assertEquals("https://i0.wp.com/algarvebuzz.com/wp-content/uploads/2026/09/poster.webp?w=1280", tvBackdrop)
    }

    @Test
    fun testWordPressHyphenatedScaledStripping() {
        val scaledUrl = "https://algarvebuzz.com/wp-content/uploads/2026/09/unexpected-loves-2026-scaled.webp"
        val original = VideoUtils.getOriginalImage(scaledUrl)
        assertEquals("https://algarvebuzz.com/wp-content/uploads/2026/09/unexpected-loves-2026.webp", original)
    }

    @Test
    fun testImdbPosterAndBackdropResolution() {
        val imdbUrl = "https://m.media-amazon.com/images/M/MV5BMjA1234_V1_SY1200_CR0,0,675,1000_AL_.jpg"

        val tvPoster = VideoUtils.getOptimizedImage(imdbUrl, isTV = true, isLowRam = false)
        assertTrue("Expected _SY500_ in $tvPoster", tvPoster.contains("_SY500_"))

        val tvBackdrop = VideoUtils.getOptimizedBackdrop(imdbUrl, isTV = true, isLowRam = false)
        assertTrue("Expected _SX1920_ in $tvBackdrop", tvBackdrop.contains("_SX1920_"))
    }

    @Test
    fun testGoogleBloggerResolution() {
        val googleUrl = "https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEj/s1600/poster.jpg"

        val tvPoster = VideoUtils.getOptimizedImage(googleUrl, isTV = true, isLowRam = false)
        assertTrue("Expected =s400 or /s400/ in $tvPoster", tvPoster.contains("=s400") || tvPoster.contains("/s400/"))

        val tvBackdrop = VideoUtils.getOptimizedBackdrop(googleUrl, isTV = true, isLowRam = false)
        assertTrue("Expected =s1920 or /s1920/ in $tvBackdrop", tvBackdrop.contains("=s1920") || tvBackdrop.contains("/s1920/"))
    }

    @Test
    fun testDailymotionResolution() {
        val dmUrl = "https://s1.dmcdn.net/v/XYZ/x240"

        val tvPoster = VideoUtils.getOptimizedImage(dmUrl, isTV = true, isLowRam = false)
        assertEquals("https://s1.dmcdn.net/v/XYZ/x720", tvPoster)

        val mobilePoster = VideoUtils.getOptimizedImage(dmUrl, isTV = false, isLowRam = false)
        assertEquals("https://s1.dmcdn.net/v/XYZ/x720", mobilePoster)

        val tvBackdrop = VideoUtils.getOptimizedBackdrop(dmUrl, isTV = true, isLowRam = false)
        assertEquals("https://s1.dmcdn.net/v/XYZ/x720", tvBackdrop)
    }
}
