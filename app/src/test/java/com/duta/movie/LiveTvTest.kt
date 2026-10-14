package com.duta.movie

import com.duta.movie.data.local.InstalledProviderEntity
import com.duta.movie.model.LiveTvCatalog
import com.duta.movie.provider.engine.LiveTvProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LiveTvTest {

    @Test
    fun testLiveTvCatalogChannels() {
        val channels = LiveTvCatalog.channels
        assertEquals(12, channels.size)

        for (ch in channels) {
            assertTrue("Channel ID must start with live_", ch.id.startsWith("live_"))
            assertTrue("Channel title must not be empty", ch.title.isNotBlank())
            assertTrue("Channel stream URL must be non-empty", ch.videoUrl.isNotBlank())
            assertEquals("Channel duration must be LIVE", "LIVE", ch.duration)
            assertTrue("Servers list must contain stream", ch.servers.isNotEmpty())
            assertTrue("Thumbnail must be set", ch.thumbnailUrl.isNotBlank())
        }
    }

    @Test
    fun testLiveTvCatalogIdentification() {
        assertTrue(LiveTvCatalog.isLiveVideo("live_tv1"))
        assertTrue(LiveTvCatalog.isLiveVideo("live_tv2"))
        assertTrue(LiveTvCatalog.isLiveVideo("live_tv3"))
        assertTrue(LiveTvCatalog.isLiveVideo("live_tv_alhijrah"))
        assertTrue(LiveTvCatalog.isLiveVideo("live_bernamatv"))
        assertTrue(LiveTvCatalog.isLiveVideo("live_cna"))
        assertTrue(LiveTvCatalog.isLiveVideo("live_okey"))
        assertTrue(LiveTvCatalog.isLiveVideo("live_sukan_rtm"))
        assertTrue(LiveTvCatalog.isLiveVideo("live_berita_rtm"))
        assertTrue(LiveTvCatalog.isLiveVideo("live_parlimen_rakyat"))
        assertTrue(LiveTvCatalog.isLiveVideo("live_parlimen_negara"))
        assertTrue(LiveTvCatalog.isLiveVideo("live_rtm_asean"))
        assertFalse(LiveTvCatalog.channels.any { it.id == "live_tv6" })

        assertFalse(LiveTvCatalog.isLiveVideo("movie_12345"))
        assertFalse(LiveTvCatalog.isLiveVideo("mb_9999"))
        assertFalse(LiveTvCatalog.isLiveVideo("bullerswood_abc"))

        val tv1 = LiveTvCatalog.getChannelById("live_tv1")
        assertNotNull(tv1)
        assertEquals("TV1", tv1?.title)

        val tv3 = LiveTvCatalog.getChannelById("live_tv3")
        assertNotNull(tv3)
        assertEquals("TV3", tv3?.title)
    }

    @Test
    fun testLiveTvProviderEngine() = runBlocking {
        val entity = InstalledProviderEntity(
            id = "com.duta.provider.rtmlivetv",
            repoId = "dreamstream-official",
            name = "RTM Live TV",
            displayName = "RTM Live TV",
            version = 1,
            versionName = "1.0.0",
            templateType = "LIVETV",
            isEnabled = true
        )
        val provider = LiveTvProvider(entity)

        val section = provider.fetchSection("/live-tv/", 1, 20)
        assertEquals(12, section.size)

        val searchResult = provider.search("TV1", 1)
        assertEquals(1, searchResult.size)
        assertEquals("TV1", searchResult.first().title)

        val detail = provider.fetchVideoDetail(section.first())
        assertNotNull(detail)
        assertEquals("TV1", detail?.title)
    }

    @Test
    fun testYouTubeIdExtraction() {
        val extractor = com.duta.movie.util.VideoExtractor
        assertEquals("HX_6UiKZw-Q", extractor.extractYouTubeId("https://www.youtube.com/watch?v=HX_6UiKZw-Q"))
        assertEquals("HX_6UiKZw-Q", extractor.extractYouTubeId("https://youtu.be/HX_6UiKZw-Q"))
        assertEquals("HX_6UiKZw-Q", extractor.extractYouTubeId("https://www.youtube.com/embed/HX_6UiKZw-Q"))
        assertEquals("HX_6UiKZw-Q", extractor.extractYouTubeId("https://www.youtube.com/live/HX_6UiKZw-Q"))
        assertEquals("HX_6UiKZw-Q", extractor.extractYouTubeId("HX_6UiKZw-Q"))
        assertNull(extractor.extractYouTubeId("https://www.youtube.com/@BuletinTV3/live"))
        assertNull(extractor.extractYouTubeId("https://www.youtube.com/@BuletinTV3/live/"))
        assertNull(extractor.extractYouTubeId(""))
        assertNull(extractor.extractYouTubeId("https://google.com"))
    }

    @Test
    fun testTv3StreamUrl() = runBlocking {
        val tv3 = LiveTvCatalog.getChannelById("live_tv3")
        assertNotNull(tv3)
        assertTrue("TV3 initial URL should be valid YouTube watch URL", tv3!!.videoUrl.contains("youtube.com/watch?v="))
        assertEquals("https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/malaysia/tv3-my.png", tv3.thumbnailUrl)
        assertTrue(com.duta.movie.util.VideoExtractor.isValidImageUrl(tv3.thumbnailUrl))
        
        val resolved = LiveTvCatalog.resolveDynamicStream("live_tv3")
        assertNotNull(resolved)
        assertTrue("TV3 dynamic URL should be YouTube watch URL", resolved!!.contains("youtube.com/watch?v="))
        val id = com.duta.movie.util.VideoExtractor.extractYouTubeId(resolved)
        assertNotNull("Resolved TV3 ID should be extractable", id)
        assertEquals(11, id!!.length)
    }
}
