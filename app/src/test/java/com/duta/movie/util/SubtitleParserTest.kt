package com.duta.movie.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleParserTest {

    @Test
    fun testParseStandardSrt() {
        val srtContent = """
            1
            00:01:20,000 --> 00:01:23,500
            <i>Hello</i>, welcome to &quot;DutaMovie&quot;!
            This is line 2.

            2
            00:01:24,100 --> 00:01:26,000
            Enjoy your stream &amp; popcorn.
        """.trimIndent()

        val cues = SubtitleParser.parseContent(srtContent)
        assertEquals(2, cues.size)

        // Cue 1
        assertEquals(80000L, cues[0].startTimeMs)
        assertEquals(83500L, cues[0].endTimeMs)
        assertEquals("Hello, welcome to \"DutaMovie\"!\nThis is line 2.", cues[0].text)

        // Cue 2
        assertEquals(84100L, cues[1].startTimeMs)
        assertEquals(86000L, cues[1].endTimeMs)
        assertEquals("Enjoy your stream & popcorn.", cues[1].text)
    }

    @Test
    fun testParseWebVttWithSettings() {
        val vttContent = """
            WEBVTT

            NOTE This is a comment

            00:00.500 --> 00:03.000 line:80% align:center
            Intro dialogue with dot ms.

            01:15.250 --> 01:18.750
            Two-part minute timestamp.
        """.trimIndent()

        val cues = SubtitleParser.parseContent(vttContent)
        assertEquals(2, cues.size)

        assertEquals(500L, cues[0].startTimeMs)
        assertEquals(3000L, cues[0].endTimeMs)
        assertEquals("Intro dialogue with dot ms.", cues[0].text)

        assertEquals(75250L, cues[1].startTimeMs)
        assertEquals(78750L, cues[1].endTimeMs)
        assertEquals("Two-part minute timestamp.", cues[1].text)
    }

    @Test
    fun testParseAssDialogue() {
        val assContent = """
            [Script Info]
            Title: Example
            
            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:01:10.50,0:01:14.20,Default,,0,0,0,,{\an8}Top subtitle line\NSecond line
            Dialogue: 0,0:01:15.00,0:01:18.00,Default,,0,0,0,,Standard ASS text
        """.trimIndent()

        val cues = SubtitleParser.parseContent(assContent)
        assertEquals(2, cues.size)

        assertEquals(70500L, cues[0].startTimeMs)
        assertEquals(74200L, cues[0].endTimeMs)
        assertEquals("Top subtitle line\nSecond line", cues[0].text)

        assertEquals(75000L, cues[1].startTimeMs)
        assertEquals(78000L, cues[1].endTimeMs)
        assertEquals("Standard ASS text", cues[1].text)
    }

    @Test
    fun testTimestampParsing() {
        assertEquals(80123L, SubtitleParser.parseTimestamp("00:01:20.123"))
        assertEquals(80123L, SubtitleParser.parseTimestamp("00:01:20,123"))
        assertEquals(80000L, SubtitleParser.parseTimestamp("01:20.000"))
        assertEquals(5500L, SubtitleParser.parseTimestamp("00:00:05.5"))
    }

    @Test
    fun testEmptyOrInvalidContent() {
        assertTrue(SubtitleParser.parseContent("").isEmpty())
        assertTrue(SubtitleParser.parseContent("   \n\r  ").isEmpty())
        assertTrue(SubtitleParser.parseContent("<html><body>Not a subtitle</body></html>").isEmpty())
    }

    @Test
    fun testNormalizeLanguage() {
        assertEquals("Indonesian", SubtitleExtractor.normalizeLanguage("id"))
        assertEquals("Indonesian", SubtitleExtractor.normalizeLanguage("Indonesian"))
        assertEquals("Indonesian", SubtitleExtractor.normalizeLanguage("Bahasa Indonesia"))
        assertEquals("Malay", SubtitleExtractor.normalizeLanguage("ms"))
        assertEquals("Malay", SubtitleExtractor.normalizeLanguage("Malay"))
        assertEquals("English", SubtitleExtractor.normalizeLanguage("en"))
        assertEquals("English", SubtitleExtractor.normalizeLanguage("English"))
    }

    @Test
    fun testBomAndMojibakeSanitization() {
        val srtWithBom = "\uFEFF1\n00:00:55,542 --> 00:00:56,542\nIni pelikā¦\n"
        val cues = SubtitleParser.parseContent(srtWithBom)
        assertEquals(1, cues.size)
        assertEquals(55542L, cues[0].startTimeMs)
        assertEquals(56542L, cues[0].endTimeMs)
        assertEquals("Ini pelik...", cues[0].text)
    }
}
