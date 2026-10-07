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
        assertEquals(8, channels.size)

        for (ch in channels) {
            assertTrue("Channel ID must start with live_", ch.id.startsWith("live_"))
            assertTrue("Channel title must not be empty", ch.title.isNotBlank())
            assertTrue("Channel stream URL must be non-empty HLS", ch.videoUrl.contains(".m3u8"))
            assertEquals("Channel duration must be LIVE", "LIVE", ch.duration)
            assertTrue("Servers list must contain stream", ch.servers.isNotEmpty())
            assertTrue("Thumbnail must be set", ch.thumbnailUrl.isNotBlank())
        }
    }

    @Test
    fun testLiveTvCatalogIdentification() {
        assertTrue(LiveTvCatalog.isLiveVideo("live_tv1"))
        assertTrue(LiveTvCatalog.isLiveVideo("live_tv2"))
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
        assertEquals(8, section.size)

        val searchResult = provider.search("TV1", 1)
        assertEquals(1, searchResult.size)
        assertEquals("TV1", searchResult.first().title)

        val detail = provider.fetchVideoDetail(section.first())
        assertNotNull(detail)
        assertEquals("TV1", detail?.title)
    }
}
