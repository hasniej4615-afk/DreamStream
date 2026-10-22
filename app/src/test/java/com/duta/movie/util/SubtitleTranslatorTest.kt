package com.duta.movie.util

import org.junit.Assert.*
import org.junit.Test

class SubtitleTranslatorTest {

    @Test
    fun testLanguageCodeMapping() {
        assertEquals("ms", SubtitleTranslator.getLanguageCode("Malay"))
        assertEquals("ms", SubtitleTranslator.getLanguageCode("Bahasa Melayu"))
        assertEquals("id", SubtitleTranslator.getLanguageCode("Indonesian"))
        assertEquals("id", SubtitleTranslator.getLanguageCode("Bahasa Indonesia"))
        assertEquals("en", SubtitleTranslator.getLanguageCode("English"))
        assertEquals("ja", SubtitleTranslator.getLanguageCode("Japanese"))
    }

    @Test
    fun testLanguageDisplayName() {
        assertEquals("Malay", SubtitleTranslator.getLanguageDisplayName("ms"))
        assertEquals("Indonesian", SubtitleTranslator.getLanguageDisplayName("id"))
        assertEquals("English", SubtitleTranslator.getLanguageDisplayName("en"))
    }

    @Test
    fun testTranslateTextLiveEndpoint() {
        val englishSentence = "Hello, welcome to the show."
        val translatedMalay = SubtitleTranslator.translateText(englishSentence, "ms")
        assertNotNull(translatedMalay)
        assertTrue(
            "Expected Malay translation to be non-empty and different from original",
            translatedMalay!!.isNotBlank() && !translatedMalay.equals(englishSentence, ignoreCase = true)
        )

        val translatedIndonesian = SubtitleTranslator.translateText(englishSentence, "id")
        assertNotNull(translatedIndonesian)
        assertTrue(
            "Expected Indonesian translation to be non-empty",
            translatedIndonesian!!.isNotBlank() && !translatedIndonesian.equals(englishSentence, ignoreCase = true)
        )
    }

    @Test
    fun testCinemaSubtitlePolishing() {
        val polishedMs = SubtitleTranslator.CinemaSubtitlePolisher.polish("Dia sedang tidur", "ms")
        assertNotNull(polishedMs)
        val polishedId = SubtitleTranslator.CinemaSubtitlePolisher.polish("Dia sedang tidur", "id")
        assertNotNull(polishedId)
    }
}
