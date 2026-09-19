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
}
