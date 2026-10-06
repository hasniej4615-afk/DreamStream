package com.duta.movie.util

import com.duta.movie.model.Subtitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSubtitleTest {

    @Test
    fun testAutoSubtitleMatchingWhenEnabled() {
        val defaultLang = "Indonesian"
        val isAutoEnabled = true
        val userDismissed = false

        val incomingSubtitles = listOf(
            Subtitle(label = "[SubCat] Movie (English)", url = "https://example.com/en.srt", language = "English"),
            Subtitle(label = "[SubCat] Movie (Indonesian)", url = "https://example.com/id.srt", language = "id"),
            Subtitle(label = "[SubCat] Movie (Malay)", url = "https://example.com/ms.srt", language = "Malay")
        )

        var selectedSubtitle: Subtitle? = null

        if (selectedSubtitle == null && !userDismissed && isAutoEnabled) {
            val normDef = SubtitleExtractor.normalizeLanguage(defaultLang)
            val match = incomingSubtitles.find {
                SubtitleExtractor.normalizeLanguage(it.language).equals(normDef, ignoreCase = true)
            }
            if (match != null) {
                selectedSubtitle = match
            }
        }

        assertNotNull("Should auto-select matching Indonesian subtitle", selectedSubtitle)
        assertEquals("https://example.com/id.srt", selectedSubtitle?.url)
        assertEquals("Indonesian", SubtitleExtractor.normalizeLanguage(selectedSubtitle?.language ?: ""))
    }

    @Test
    fun testAutoSubtitleNotSelectedWhenDisabled() {
        val defaultLang = "Indonesian"
        val isAutoEnabled = false
        val userDismissed = false

        val incomingSubtitles = listOf(
            Subtitle(label = "[SubCat] Movie (Indonesian)", url = "https://example.com/id.srt", language = "id")
        )

        var selectedSubtitle: Subtitle? = null

        if (selectedSubtitle == null && !userDismissed && isAutoEnabled) {
            val normDef = SubtitleExtractor.normalizeLanguage(defaultLang)
            val match = incomingSubtitles.find {
                SubtitleExtractor.normalizeLanguage(it.language).equals(normDef, ignoreCase = true)
            }
            if (match != null) {
                selectedSubtitle = match
            }
        }

        assertNull("Should NOT auto-select subtitle when isAutoEnabled is false", selectedSubtitle)
    }

    @Test
    fun testAutoSubtitleNotSelectedWhenUserDismissed() {
        val defaultLang = "Indonesian"
        val isAutoEnabled = true
        val userDismissed = true // User clicked 'Off' / dismissed in dialog

        val incomingSubtitles = listOf(
            Subtitle(label = "[SubCat] Movie (Indonesian)", url = "https://example.com/id.srt", language = "id")
        )

        var selectedSubtitle: Subtitle? = null

        if (selectedSubtitle == null && !userDismissed && isAutoEnabled) {
            val normDef = SubtitleExtractor.normalizeLanguage(defaultLang)
            val match = incomingSubtitles.find {
                SubtitleExtractor.normalizeLanguage(it.language).equals(normDef, ignoreCase = true)
            }
            if (match != null) {
                selectedSubtitle = match
            }
        }

        assertNull("Should NOT auto-select subtitle when user explicitly dismissed subtitles", selectedSubtitle)
    }

    @Test
    fun testAutoSubtitleLanguageVariantsNormalizeCorrectly() {
        val target = "Indonesian"
        val variants = listOf("id", "ID", "indo", "Indo", "indonesian", "Indonesian", "bahasa indonesia", "Bahasa Indonesia")

        for (variant in variants) {
            assertEquals("Variant '$variant' should normalize to 'Indonesian'", target, SubtitleExtractor.normalizeLanguage(variant))
        }

        val msVariants = listOf("ms", "msa", "malay", "Malay", "Bahasa Melayu")
        for (variant in msVariants) {
            assertEquals("Variant '$variant' should normalize to 'Malay'", "Malay", SubtitleExtractor.normalizeLanguage(variant))
        }

        val enVariants = listOf("en", "eng", "english", "English")
        for (variant in enVariants) {
            assertEquals("Variant '$variant' should normalize to 'English'", "English", SubtitleExtractor.normalizeLanguage(variant))
        }
    }

    @Test
    fun testSubtitleParsingAndCueGeneration() {
        val srtContent = """
            1
            00:01:20,000 --> 00:01:23,500
            Hello, welcome to DreamStream!

            2
            00:01:25,000 --> 00:01:28,000
            Enjoy your movie.
        """.trimIndent()

        val cues = SubtitleParser.parseContent(srtContent)
        assertEquals(2, cues.size)
        assertEquals(80000L, cues[0].startTimeMs)
        assertEquals(83500L, cues[0].endTimeMs)
        assertEquals("Hello, welcome to DreamStream!", cues[0].text)

        assertEquals(85000L, cues[1].startTimeMs)
        assertEquals(88000L, cues[1].endTimeMs)
        assertEquals("Enjoy your movie.", cues[1].text)
    }
}
