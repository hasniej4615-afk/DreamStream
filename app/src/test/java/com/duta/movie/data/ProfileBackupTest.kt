package com.duta.movie.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileBackupTest {

    @Test
    fun testJsonSerializationAndDeserializationRoundtrip() {
        val originalData = ProfileBackupData(
            version = 1,
            appVersion = "2.0.9 Cloud 7",
            exportedAt = 1728216000000L,
            username = "StreamMaster",
            avatarBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
            defaultSubtitleLanguage = "English",
            autoSubtitleEnabled = false,
            voiceEnhancerMode = 2,
            mobileLandscapeEnabled = true,
            debugModeEnabled = true,
            uiSafeAreaPadding = 16,
            uiHeroHeightOffset = 50,
            uiScaleFactor = 1.15f,
            uiThumbnailScaleFactor = 0.9f,
            enabledCategories = setOf("Action", "Sci-Fi", "Anime"),
            myList = setOf("movie-101", "movie-202"),
            recentlyWatched = listOf("movie-202", "movie-303"),
            videoProgress = mapOf("movie-101" to 45000L, "movie-202" to 120000L),
            videoDuration = mapOf("movie-101" to 7200000L, "movie-202" to 6000000L)
        )

        val jsonStr = ProfileBackupManager.toJson(originalData)
        assertNotNull(jsonStr)
        assertTrue(jsonStr.contains("\"username\": \"StreamMaster\""))
        assertTrue(jsonStr.contains("\"avatar_base64\""))

        val restoredData = ProfileBackupManager.fromJson(jsonStr)
        assertNotNull(restoredData)
        restoredData!!

        assertEquals(originalData.version, restoredData.version)
        assertEquals(originalData.appVersion, restoredData.appVersion)
        assertEquals(originalData.exportedAt, restoredData.exportedAt)
        assertEquals(originalData.username, restoredData.username)
        assertEquals(originalData.avatarBase64, restoredData.avatarBase64)
        assertEquals(originalData.defaultSubtitleLanguage, restoredData.defaultSubtitleLanguage)
        assertEquals(originalData.autoSubtitleEnabled, restoredData.autoSubtitleEnabled)
        assertEquals(originalData.voiceEnhancerMode, restoredData.voiceEnhancerMode)
        assertEquals(originalData.mobileLandscapeEnabled, restoredData.mobileLandscapeEnabled)
        assertEquals(originalData.debugModeEnabled, restoredData.debugModeEnabled)
        assertEquals(originalData.uiSafeAreaPadding, restoredData.uiSafeAreaPadding)
        assertEquals(originalData.uiHeroHeightOffset, restoredData.uiHeroHeightOffset)
        assertEquals(originalData.uiScaleFactor, restoredData.uiScaleFactor, 0.001f)
        assertEquals(originalData.uiThumbnailScaleFactor, restoredData.uiThumbnailScaleFactor, 0.001f)
        assertEquals(originalData.enabledCategories, restoredData.enabledCategories)
        assertEquals(originalData.myList, restoredData.myList)
        assertEquals(originalData.recentlyWatched, restoredData.recentlyWatched)
        assertEquals(originalData.videoProgress, restoredData.videoProgress)
        assertEquals(originalData.videoDuration, restoredData.videoDuration)
    }

    @Test
    fun testDeserializationWithEmptyJson() {
        val emptyJson = "{}"
        val parsed = ProfileBackupManager.fromJson(emptyJson)
        assertNotNull(parsed)
        parsed!!

        assertEquals(1, parsed.version)
        assertEquals("2.0.9 Cloud 7", parsed.appVersion)
        assertEquals("", parsed.username)
        assertNull(parsed.avatarBase64)
        assertEquals("Indonesian", parsed.defaultSubtitleLanguage)
        org.junit.Assert.assertFalse(parsed.autoSubtitleEnabled)
        assertEquals(0, parsed.voiceEnhancerMode)
        assertEquals(emptySet<String>(), parsed.enabledCategories)
        assertEquals(emptySet<String>(), parsed.myList)
        assertEquals(emptyList<String>(), parsed.recentlyWatched)
        assertEquals(emptyMap<String, Long>(), parsed.videoProgress)
    }

    @Test
    fun testCorruptedJsonReturnsNull() {
        val corruptedJson = "not valid json { [ ]"
        val result = ProfileBackupManager.fromJson(corruptedJson)
        assertNull(result)
    }

    @Test
    fun testBackupFileNameFormat() {
        val fileName = ProfileBackupManager.generateBackupFileName()
        assertTrue("Filename should start with DreamStream_Backup_", fileName.startsWith("DreamStream_Backup_"))
        assertTrue("Filename should end with .json", fileName.endsWith(".json"))
    }
}
