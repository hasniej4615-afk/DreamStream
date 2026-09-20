package com.duta.movie.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class CastSubtitleServerTest {

    @After
    fun tearDown() {
        CastSubtitleServer.stop()
    }

    @Test
    fun testServerServesWebVttWithCors() {
        val vtt = "WEBVTT\n\n1\n00:00:01.000 --> 00:00:02.000\nHello Chromecast\n\n"
        val urlString = CastSubtitleServer.setSubtitle(vtt)
        assertNotNull(urlString)
        assertTrue(urlString!!.contains("sub_"))
        assertTrue(urlString.endsWith(".vtt"))

        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 3000
        conn.readTimeout = 3000

        val responseCode = conn.responseCode
        assertEquals(200, responseCode)
        assertEquals("text/vtt; charset=utf-8", conn.contentType)
        assertEquals("*", conn.getHeaderField("Access-Control-Allow-Origin"))

        val body = conn.inputStream.bufferedReader().readText()
        assertEquals(vtt, body)
        conn.disconnect()
    }

    @Test
    fun testServerHandlesOptionsCorsPreflight() {
        val vtt = "WEBVTT\n\n"
        val urlString = CastSubtitleServer.setSubtitle(vtt)
        assertNotNull(urlString)

        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "OPTIONS"
        conn.connectTimeout = 3000
        conn.readTimeout = 3000

        val responseCode = conn.responseCode
        assertEquals(204, responseCode)
        assertEquals("*", conn.getHeaderField("Access-Control-Allow-Origin"))
        assertTrue(conn.getHeaderField("Access-Control-Allow-Methods")!!.contains("GET"))
        conn.disconnect()
    }

    @Test
    fun testServerReturns404ForUnknownPath() {
        CastSubtitleServer.start()
        val urlString = CastSubtitleServer.getSubtitleUrl() ?: run {
            CastSubtitleServer.setSubtitle("WEBVTT\n\n")
            CastSubtitleServer.getSubtitleUrl()!!
        }
        val baseUrl = urlString.substringBeforeLast("/")
        val badUrl = URL("$baseUrl/unknown_file.txt")

        val conn = badUrl.openConnection() as HttpURLConnection
        conn.connectTimeout = 3000
        conn.readTimeout = 3000

        val responseCode = conn.responseCode
        assertEquals(404, responseCode)
        conn.disconnect()
    }
}
