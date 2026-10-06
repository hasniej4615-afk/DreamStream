package com.duta.movie.util

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import org.junit.Test
import org.junit.Assert.assertTrue

class SubtitleExtractorTest {
    @Test
    fun testSubtitleExtraction() = runBlocking {
        val title = "Blood Brothers: Dragon's Embers 2025"
        var done = false
        SubtitleExtractor.searchAndGetSubtitles(title, null, GlobalScope, { subs ->
            println("Found " + subs.size + " subtitles for " + title)
            subs.take(5).forEach { 
                println(it.language + ": " + it.label + " - " + it.url)
            }
        }, {
            done = true
        })
        
        var count = 0
        while(!done && count < 100) { delay(100); count++ }
    }

    @Test
    fun testUnforgivenSubtitleClean() {
        val baseTitle = "Tarung: Unforgiven (2026)".replace(Regex("""(?i)\bS\d+E\d+\b|\bEpisode\s*\d+\b|\bSeason\s*\d+\b"""), "").trim()
        val cleanBase = baseTitle.replace(Regex("""(?i)\b(?:reducing|fhd|hd|4k|720p|1080p|bluray|web-?dl|webrip|amzn|nf|dovi|hdr|10bit|hdtv|x264|x265|proper|internal|dual-?audio|hindi|dubbed|subbed)\b"""), " ")
                             .replace(Regex("""[._()&:"!?,;+]"""), " ")
                             .replace('’', '\'')
                             .replace(Regex("""\s+"""), " ")
                             .trim()
        assert(cleanBase == "Tarung Unforgiven 2026") {
            "Expected 'Tarung Unforgiven 2026', got '$cleanBase' (must not cut 'nf' out of Unforgiven)"
        }
    }

    @Test
    fun testSubSourceDownloadRegex() {
        val sampleHtml = """
            <a href="https://api.subsource.net/v1/subtitle/download/2c7bea97d549128df7d2fb8c97830304a29f55ab254e8d37d516dcf1bd999a4f" download="" class="btn">Download</a>
        """.trimIndent()
        val regex = Regex("""https?://(?:api\.)?subsource\.net/(?:api/)?v1/subtitle/download/[a-zA-Z0-9_-]+""")
        val match = regex.find(sampleHtml)?.value
        org.junit.Assert.assertNotNull(match)
        org.junit.Assert.assertEquals(
            "https://api.subsource.net/v1/subtitle/download/2c7bea97d549128df7d2fb8c97830304a29f55ab254e8d37d516dcf1bd999a4f",
            match
        )
    }

    @Test
    fun testLanguageNormalization() {
        org.junit.Assert.assertEquals("Indonesian", SubtitleExtractor.normalizeLanguage("id"))
        org.junit.Assert.assertEquals("Indonesian", SubtitleExtractor.normalizeLanguage("indo"))
        org.junit.Assert.assertEquals("Indonesian", SubtitleExtractor.normalizeLanguage("indonesian"))
        org.junit.Assert.assertEquals("Indonesian", SubtitleExtractor.normalizeLanguage("bahasa indonesia"))
        org.junit.Assert.assertEquals("English", SubtitleExtractor.normalizeLanguage("en"))
        org.junit.Assert.assertEquals("English", SubtitleExtractor.normalizeLanguage("english"))
        org.junit.Assert.assertEquals("Malay", SubtitleExtractor.normalizeLanguage("ms"))
        org.junit.Assert.assertEquals("Malay", SubtitleExtractor.normalizeLanguage("malay"))
    }
}
