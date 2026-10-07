package com.duta.movie.data.remote

import com.duta.movie.model.BroadcastMessage
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BroadcastServiceTest {

    @Test
    fun testBroadcastMessageModel() {
        val msg = BroadcastMessage(
            id = "bcast_12345",
            title = "Sistem Dikemaskini",
            message = "Server streaming telah dipulihkan kepada kelajuan penuh.",
            author = "Admin",
            type = "info",
            timestamp = 1728320000000L,
            isActive = true
        )

        assertEquals("bcast_12345", msg.id)
        assertEquals("Sistem Dikemaskini", msg.title)
        assertEquals("Server streaming telah dipulihkan kepada kelajuan penuh.", msg.message)
        assertEquals("Admin", msg.author)
        assertEquals("info", msg.type)
        assertEquals(1728320000000L, msg.timestamp)
        assertTrue(msg.isActive)
    }

    @Test
    fun testJsonPayloadParsing() {
        val jsonStr = """
            {
                "id": "bcast_999",
                "title": "Notis Penyelenggaraan",
                "message": "Penyelenggaraan pelayan jam 12:00 AM.",
                "author": "Pentadbir",
                "type": "warning",
                "timestamp": 1728321111000,
                "is_active": true
            }
        """.trimIndent()

        val json = JSONObject(jsonStr)
        val id = json.optString("id")
        val title = json.optString("title")
        val message = json.optString("message")
        val author = json.optString("author")
        val type = json.optString("type")
        val timestamp = json.optLong("timestamp")
        val isActive = json.optBoolean("is_active", true)

        val broadcast = BroadcastMessage(
            id = id,
            title = title,
            message = message,
            author = author,
            type = type,
            timestamp = timestamp,
            isActive = isActive
        )

        assertEquals("bcast_999", broadcast.id)
        assertEquals("Notis Penyelenggaraan", broadcast.title)
        assertEquals("warning", broadcast.type)
        assertTrue(broadcast.isActive)
    }

    @Test
    fun testDeactivatedBroadcast() {
        val jsonStr = """
            {
                "id": "bcast_old",
                "title": "Tamat",
                "message": "Pengumuman lama.",
                "author": "Admin",
                "type": "info",
                "timestamp": 1728300000000,
                "is_active": false
            }
        """.trimIndent()

        val json = JSONObject(jsonStr)
        val isActive = json.optBoolean("is_active", true)
        assertFalse(isActive)
    }
}
