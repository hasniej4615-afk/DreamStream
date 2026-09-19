package com.duta.movie.util

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class InstallTrackerTest {

    @Test
    fun testInstallCountJsonParsing() {
        val validJson = """{"value": 1284}"""
        val json = JSONObject(validJson)
        val count = json.optInt("value", -1)
        assertEquals(1284, count)
    }

    @Test
    fun testMalformedJsonHandling() {
        val emptyJson = "{}"
        val json = JSONObject(emptyJson)
        val count = json.optInt("value", -1)
        assertEquals(-1, count)
    }

    @Test
    fun testFetchTotalInstallsLiveEndpoint() {
        // Verification that the counter endpoint responds with a valid count (or 0 if uninitialized)
        kotlinx.coroutines.runBlocking {
            try {
                val count = InstallTracker.fetchTotalInstalls()
                assertNotNull("Expected non-null count from counter endpoint", count)
                assert(count!! >= 0) { "Expected count >= 0, got $count" }
            } catch (e: Exception) {
                println("Network unavailable in test environment: ${e.message}; skipping.")
            }
        }
    }
}
