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

    @Test
    fun testMiniFFSubSyncCrossCorrelationAccurateOffset() {
        val cues = listOf(
            ParsedSubtitleCue(10000L, 12500L, "Are you ready?"),
            ParsedSubtitleCue(14000L, 17000L, "We have to leave right now!"),
            ParsedSubtitleCue(18500L, 21000L, "Watch out behind you!")
        )

        // True video dialogue delayed by 9,500ms (e.g. streaming site intro bumper)
        // Offset = T_sub - T_audio = -9500ms
        val speechSegments = listOf(
            SubtitleSyncEngine.SpeechSegment(19500L, 22000L), // 2.5s
            SubtitleSyncEngine.SpeechSegment(23500L, 26500L), // 3.0s
            SubtitleSyncEngine.SpeechSegment(28000L, 30500L)  // 2.5s
        )

        val detectedOffset = SubtitleSyncEngine.evaluateCrossCorrelationAutoSync(
            speechSegments = speechSegments,
            dialogueCues = cues
        )

        assertNotNull("Cross-correlation should produce confident offset", detectedOffset)
        assertEquals(-9500L, detectedOffset)
    }

    @Test
    fun testMiniFFSubSyncNoiseImmunity() {
        val cues = listOf(
            ParsedSubtitleCue(12000L, 14500L, "Hello, can you hear me?"),
            ParsedSubtitleCue(16000L, 19000L, "Yes, I am right here.")
        )

        // Speech segments include a loud intro fanfare/sound effect burst at 1,500ms (not dialogue)
        // followed by actual dialogue shifted by +3,000ms
        // Offset = T_sub - T_audio = +3000ms (audio at 9000ms, cue at 12000ms)
        val speechSegments = listOf(
            SubtitleSyncEngine.SpeechSegment(1500L, 2100L),   // 600ms fanfare noise
            SubtitleSyncEngine.SpeechSegment(9000L, 11500L),  // 2.5s true dialogue 1
            SubtitleSyncEngine.SpeechSegment(13000L, 16000L)  // 3.0s true dialogue 2
        )

        val detectedOffset = SubtitleSyncEngine.evaluateCrossCorrelationAutoSync(
            speechSegments = speechSegments,
            dialogueCues = cues
        )

        assertNotNull("Cross-correlation should reject fanfare and detect true dialogue offset", detectedOffset)
        assertEquals(3000L, detectedOffset)
    }

    @Test
    fun testMiniFFSubSyncInsufficientSpeechReturnsNull() {
        val cues = listOf(
            ParsedSubtitleCue(10000L, 12500L, "Quick word")
        )

        // Only 500ms of speech (below 2000ms minimum threshold)
        val speechSegments = listOf(
            SubtitleSyncEngine.SpeechSegment(10000L, 10500L)
        )

        val detectedOffset = SubtitleSyncEngine.evaluateCrossCorrelationAutoSync(
            speechSegments = speechSegments,
            dialogueCues = cues
        )

        assertNull("Should return null when speech duration is insufficient", detectedOffset)
    }

    @Test
    fun testSignatureYearExtraction() {
        val sig1 = SubtitleSyncEngine.extractSignature(
            title = "The Wrecking Crew 2026",
            quality = "1080p",
            serverUrl = "https://df31.mantab.men/watch/the-wrecking-crew-2026-u2vr.html"
        )
        assertEquals("2026", sig1.targetYear)

        val sig2 = SubtitleSyncEngine.extractSignature(
            title = "dfw_the-wrecking-crew-2026-u2vr",
            quality = "1080p"
        )
        assertEquals("2026", sig2.targetYear)

        val sig3 = SubtitleSyncEngine.extractSignature(
            title = "The Mummy",
            year = "2026"
        )
        assertEquals("2026", sig3.targetYear)
    }

    @Test
    fun testYearMismatchDisqualifiesSubtitleAndPreventsAutoSelect() {
        val sig2026 = SubtitleSyncEngine.extractSignature(
            title = "The Wrecking Crew 2026",
            quality = "1080p",
            serverUrl = "https://df31.mantab.men/watch/the-wrecking-crew-2026-u2vr.html"
        )

        val sub1968 = Subtitle(
            label = "[SubCat] The.Wrecking.Crew.1968.1080p.WEBRip.x264-RARBG (Indonesian)",
            url = "https://subtitlecat.com/subs/123/The.Wrecking.Crew.1968.srt",
            language = "Indonesian"
        )

        // 1. Fitness score should be heavily negative (-10000)
        val score1968 = SubtitleSyncEngine.scoreSubtitle(sub1968, sig2026)
        assertTrue("Score for 1968 subtitle on 2026 movie must be negative", score1968 < 0)
        assertEquals(-10000, score1968)

        // 2. isMatchedOrEmbedded MUST be false (no blue matched badge)
        val isMatched = SubtitleSyncEngine.isMatchedOrEmbedded(sub1968, sig2026)
        assertTrue("Mismatched release year must NOT be marked matched", !isMatched)

        // 3. findBestSubtitleMatch must NOT select the 1968 subtitle
        val bestMatch = SubtitleSyncEngine.findBestSubtitleMatch(
            subtitles = listOf(sub1968),
            targetLanguage = "Indonesian",
            signature = sig2026
        )
        assertNull("findBestSubtitleMatch must return null when all candidate subtitles have mismatched years", bestMatch)
    }

    @Test
    fun testYearMatchPromotesSubtitleAndEnablesAutoSelect() {
        val sig2026 = SubtitleSyncEngine.extractSignature(
            title = "The Wrecking Crew 2026",
            quality = "1080p",
            serverUrl = "https://df31.mantab.men/watch/the-wrecking-crew-2026-u2vr.html"
        )

        val sub2026 = Subtitle(
            label = "[OpenSubtitles] The.Wrecking.Crew.2026.1080p.WEBRip.x264-RARBG (Indonesian)",
            url = "https://opensubtitles.org/subs/456/The.Wrecking.Crew.2026.srt",
            language = "Indonesian"
        )

        // 1. Fitness score should be positive and include year bonus
        val score2026 = SubtitleSyncEngine.scoreSubtitle(sub2026, sig2026)
        assertTrue("Score for 2026 subtitle on 2026 movie must be high", score2026 >= 200)

        // 2. isMatchedOrEmbedded should be true
        val isMatched = SubtitleSyncEngine.isMatchedOrEmbedded(sub2026, sig2026)
        assertTrue("Matching release year should be marked matched", isMatched)

        // 3. findBestSubtitleMatch must select the 2026 subtitle
        val bestMatch = SubtitleSyncEngine.findBestSubtitleMatch(
            subtitles = listOf(sub2026),
            targetLanguage = "Indonesian",
            signature = sig2026
        )
        assertNotNull("findBestSubtitleMatch must select the 2026 subtitle", bestMatch)
        assertEquals(sub2026.url, bestMatch?.url)
    }

    @Test
    fun testSubCatNonEnglishDemotedAndSkippedWhenVerifiedEnglishExists() {
        val sig2026 = SubtitleSyncEngine.extractSignature(
            title = "The Wrecking Crew 2026",
            quality = "1080p",
            serverUrl = "https://df31.mantab.men/watch/the-wrecking-crew-2026-u2vr.html"
        )

        // SubCat machine translated Indonesian subtitle
        val subCatId = Subtitle(
            label = "[SubCat] The Wrecking Crew - Fratelli demolitori (2026) 2160p (Indonesian)",
            url = "https://subtitlecat.com/subs/1445/The%20Wrecking%20Crew-id.srt",
            language = "Indonesian"
        )

        // Official English release from OpenSubtitles
        val verifiedEnglish = Subtitle(
            label = "[OpenSubtitles] The Wrecking Crew 2026 1080p WEB-DL",
            url = "https://api.opensubtitles.com/en.srt",
            language = "English"
        )

        // 1. SubCat non-English should have demoted score compared to verified provider
        val subCatScore = SubtitleSyncEngine.scoreSubtitle(subCatId, sig2026)
        val engScore = SubtitleSyncEngine.scoreSubtitle(verifiedEnglish, sig2026)
        assertTrue("Verified English release must score much higher than unverified SubCat machine translation", engScore > subCatScore)

        // 2. findBestSubtitleMatch for Indonesian must return null when only SubCat Indonesian exists and verified English exists,
        // allowing AI translation fallback to translate the verified English release!
        val bestMatch = SubtitleSyncEngine.findBestSubtitleMatch(
            subtitles = listOf(subCatId, verifiedEnglish),
            targetLanguage = "Indonesian",
            signature = sig2026
        )
        assertNull("findBestSubtitleMatch must reject SubCat scrape in favor of AI translating verified English", bestMatch)
    }

    @Test
    fun testConfirmedMismatchDetectedWhenSpeechDoesNotAlign() {
        // Cues from a completely different movie ("Lure" cues with 20s+ gaps)
        val wrongMovieCues = listOf(
            ParsedSubtitleCue(5000L, 7000L, "Lucas"),
            ParsedSubtitleCue(28000L, 30000L, "Lucas, ayo."),
            ParsedSubtitleCue(55000L, 57000L, "Ingrid telah menunggu begitu lama")
        )

        // True video dialogue from "The Wrecking Crew" occurring with tight dialogue rhythm (intervals ~6-8s)
        // Total speech duration = 2.5s + 3.0s + 3.0s = 8.5s (>= 8000ms threshold)
        val speechSegments = listOf(
            SubtitleSyncEngine.SpeechSegment(2000L, 4500L),   // 2.5s
            SubtitleSyncEngine.SpeechSegment(9000L, 12000L),  // 3.0s
            SubtitleSyncEngine.SpeechSegment(17000L, 20000L)  // 3.0s
        )

        val result = SubtitleSyncEngine.evaluateCrossCorrelationDetailed(
            speechSegments = speechSegments,
            dialogueCues = wrongMovieCues
        )

        assertNull("Mismatched movie subtitles must not produce a confident offset", result.offsetMs)
        assertTrue("Cross-correlation must confirm subtitle mismatch when 8s+ speech fails to correlate", result.isConfirmedMismatch)
    }
}
