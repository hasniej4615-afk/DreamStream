package com.duta.movie.util

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lightweight in-app micro HTTP server for streaming WebVTT subtitles
 * over the local Wi-Fi network directly to Google Cast receivers (Chromecast / Android TV).
 *
 * Designed with zero external dependencies using standard ServerSocket.
 */
object CastSubtitleServer {
    private const val TAG = "CastSubtitleServer"

    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null
    private val isRunning = AtomicBoolean(false)

    @Volatile
    private var vttContent: String = ""

    @Volatile
    private var currentVersionTag: String = ""

    @Volatile
    private var serverPort: Int = 0

    /**
     * Starts the server if not already running.
     * Binds to port 0 (ephemeral port assigned by OS).
     */
    @Synchronized
    fun start(): Boolean {
        if (isRunning.get() && serverSocket?.isClosed == false) {
            return true
        }

        return try {
            val socket = ServerSocket(0)
            serverSocket = socket
            serverPort = socket.localPort
            isRunning.set(true)

            val thread = Thread({
                Log.i(TAG, "Cast subtitle server started on port $serverPort")
                while (isRunning.get() && !socket.isClosed) {
                    try {
                        val client = socket.accept()
                        // Handle request in a separate worker thread
                        Thread({
                            handleClient(client)
                        }, "CastSubWorker-${client.port}").start()
                    } catch (e: Exception) {
                        if (isRunning.get()) {
                            Log.e(TAG, "Error accepting client connection: ${e.message}")
                        }
                    }
                }
                Log.i(TAG, "Cast subtitle server loop exited")
            }, "CastSubtitleServerThread")

            serverThread = thread
            thread.isDaemon = true
            thread.start()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Cast subtitle server: ${e.message}", e)
            false
        }
    }

    /**
     * Stops the server and releases socket resources.
     */
    @Synchronized
    fun stop() {
        isRunning.set(false)
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        serverThread?.interrupt()
        serverThread = null
        vttContent = ""
        currentVersionTag = ""
        serverPort = 0
        Log.i(TAG, "Cast subtitle server stopped")
    }

    /**
     * Sets the active WebVTT content and returns the full local LAN URL
     * for Chromecast to consume, or null if server could not start / no IP.
     */
    fun setSubtitle(vtt: String): String? {
        if (!start()) return null
        val localIp = getLocalIpAddress() ?: return null

        vttContent = vtt
        currentVersionTag = System.currentTimeMillis().toString()

        val url = "http://$localIp:$serverPort/sub_${currentVersionTag}.vtt"
        Log.i(TAG, "Updated Cast subtitle URL: $url (${vtt.length} chars)")
        return url
    }

    /**
     * Clears the current subtitle content.
     */
    fun clear() {
        vttContent = ""
        currentVersionTag = ""
    }

    /**
     * Returns the current local HTTP URL if a subtitle is active and server is running.
     */
    fun getSubtitleUrl(): String? {
        if (!isRunning.get() || vttContent.isBlank()) return null
        val localIp = getLocalIpAddress() ?: return null
        return "http://$localIp:$serverPort/sub_${currentVersionTag}.vtt"
    }

    private fun handleClient(client: Socket) {
        try {
            client.soTimeout = 5000
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val out: OutputStream = client.getOutputStream()

            val requestLine = reader.readLine() ?: return
            Log.i(TAG, "Incoming client request from ${client.inetAddress.hostAddress}: $requestLine")

            // Drain remaining headers
            while (true) {
                val headerLine = reader.readLine() ?: break
                if (headerLine.isEmpty()) break
            }

            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val path = parts[1]

            // Handle CORS preflight OPTIONS request
            if (method == "OPTIONS") {
                val response = "HTTP/1.1 204 No Content\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Access-Control-Allow-Methods: GET, OPTIONS, HEAD\r\n" +
                        "Access-Control-Allow-Headers: *\r\n" +
                        "Access-Control-Max-Age: 86400\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n\r\n"
                out.write(response.toByteArray(Charsets.UTF_8))
                out.flush()
                return
            }

            // Handle GET / HEAD for WebVTT
            if ((method == "GET" || method == "HEAD") && path.contains(".vtt")) {
                val currentVtt = vttContent
                if (currentVtt.isNotBlank()) {
                    val bytes = currentVtt.toByteArray(Charsets.UTF_8)
                    val header = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/vtt; charset=utf-8\r\n" +
                            "Content-Length: ${bytes.size}\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Access-Control-Allow-Methods: GET, OPTIONS, HEAD\r\n" +
                            "Access-Control-Allow-Headers: *\r\n" +
                            "Cache-Control: no-cache, no-store, must-revalidate\r\n" +
                            "Connection: close\r\n\r\n"

                    out.write(header.toByteArray(Charsets.UTF_8))
                    if (method == "GET") {
                        out.write(bytes)
                    }
                    out.flush()
                    Log.i(TAG, "Served ${bytes.size} bytes of WebVTT to ${client.inetAddress.hostAddress}")
                    return
                }
            }

            // 404 for anything else
            val notFound = "HTTP/1.1 404 Not Found\r\n" +
                    "Content-Type: text/plain\r\n" +
                    "Content-Length: 9\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Connection: close\r\n\r\nNot Found"
            out.write(notFound.toByteArray(Charsets.UTF_8))
            out.flush()
        } catch (e: Exception) {
            Log.d(TAG, "Socket handler finished: ${e.message}")
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    /**
     * Resolves the primary local IPv4 address on the active Wi-Fi interface.
     */
    fun getLocalIpAddress(): String? {
        return try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            // First look specifically for wlan / eth interfaces
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val name = intf.name.lowercase()
                if (name.contains("wlan") || name.contains("eth") || name.contains("wls") || name.contains("en")) {
                    for (addr in Collections.list(intf.inetAddresses)) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            return addr.hostAddress
                        }
                    }
                }
            }
            // Fallback to any non-loopback IPv4 interface
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                for (addr in Collections.list(intf.inetAddresses)) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}
