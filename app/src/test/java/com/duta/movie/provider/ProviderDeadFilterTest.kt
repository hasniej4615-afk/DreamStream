package com.duta.movie.provider

import com.duta.movie.data.remote.RepoService
import com.duta.movie.util.VideoExtractor
import org.junit.Assert.*
import org.junit.Test

class ProviderDeadFilterTest {

    @Test
    fun testDeadDomainBlacklist() {
        assertTrue(RepoService.isDeadOrBlacklisted("https://tv7.idlix.asia"))
        assertTrue(RepoService.isDeadOrBlacklisted("https://dramaid.tv"))
        assertTrue(RepoService.isDeadOrBlacklisted("https://dramaid.nl"))
        assertTrue(RepoService.isDeadOrBlacklisted("https://nodrakor.id"))
        assertTrue(RepoService.isDeadOrBlacklisted("https://rebahin.vip"))
        assertTrue(RepoService.isDeadOrBlacklisted("https://ww1.anoboy.app"))
        assertTrue(RepoService.isDeadOrBlacklisted("http://mantenimiento.win"))
        assertTrue(RepoService.isDeadOrBlacklisted("https://animeindo.xyz"))

        // Legitimate and active domains must NOT be blacklisted
        assertFalse(RepoService.isDeadOrBlacklisted("https://v5.pusatfilm21info.com"))
        assertFalse(RepoService.isDeadOrBlacklisted("https://ww44.pencurimovie.baby"))
        assertFalse(RepoService.isDeadOrBlacklisted("https://df31.mantab.men"))
        assertFalse(RepoService.isDeadOrBlacklisted("https://bullerswood.org"))
        assertFalse(RepoService.isDeadOrBlacklisted("https://archive.org"))
    }

    @Test
    fun testPusatfilmWordPressMuviProUrlBuilder() {
        val base = "https://v5.pusatfilm21info.com"

        val kdramaUrl = VideoExtractor.buildWordPressMuviProCategoryUrl(base, "drama-korea", 1)
        assertEquals("https://v5.pusatfilm21info.com/drama-korea/", kdramaUrl)

        val kdramaPage2 = VideoExtractor.buildWordPressMuviProCategoryUrl(base, "drama-korea", 2)
        assertEquals("https://v5.pusatfilm21info.com/drama-korea/page/2/", kdramaPage2)

        val cdramaUrl = VideoExtractor.buildWordPressMuviProCategoryUrl(base, "drama-china", 1)
        assertEquals("https://v5.pusatfilm21info.com/drama-china/", cdramaUrl)

        val seriesUrl = VideoExtractor.buildWordPressMuviProCategoryUrl(base, "series-terbaru", 1)
        assertEquals("https://v5.pusatfilm21info.com/series-terbaru/", seriesUrl)

        val movieUrl = VideoExtractor.buildWordPressMuviProCategoryUrl(base, "film-terbaru", 1)
        assertEquals("https://v5.pusatfilm21info.com/film-terbaru/", movieUrl)
    }

    @Test
    fun testPusatfilmIdExtraction() {
        val movieUrl = "https://v5.pusatfilm21info.com/dashavatar-2025/"
        val id = VideoExtractor.extractStableId(movieUrl)
        assertEquals("pf_dashavatar-2025", id)
        assertEquals("dashavatar-2025", VideoExtractor.extractCleanSlug(movieUrl))
    }

    @Test
    fun testProviderSourceIdentification() {
        assertTrue(VideoExtractor.isPusatfilm(videoUrl = "https://v5.pusatfilm21info.com/tv/avatar/"))
        assertTrue(VideoExtractor.isBullerswood(videoUrl = "https://bullerswood.org/movie/avatar/"))
        assertTrue(VideoExtractor.isPencuriMovie(videoUrl = "https://ww44.pencurimovie.baby/avatar/"))
        assertTrue(VideoExtractor.isDutaFilmWeb(videoUrl = "https://df31.mantab.men/watch/avatar.html"))
    }
}
