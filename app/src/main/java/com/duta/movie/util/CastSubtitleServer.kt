package com.duta.movie.util

import android.util.Log
import okhttp3.Request
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
 * and proxying anti-hotlinking video streams over the local Wi-Fi network
 * directly to Google Cast receivers (Chromecast / Android TV).
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

    @Volatile
    private var streamUrl: String? = null

    @Volatile
    private var streamReferer: String? = null

    @Volatile
    private var streamUserAgent: String? = null

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
        streamUrl = null
        streamReferer = null
        streamUserAgent = null
        serverPort = 0
        Log.i(TAG, "Cast subtitle server stopped")
    }

    /**
     * Sets the active media stream URL and anti-hotlinking headers, returning
     * a local LAN proxy URL (e.g. http://192.168.1.5:port/stream.mp4) for Chromecast.
     */
    fun setMediaStream(url: String, referer: String?, userAgent: String? = null): String? {
        if (!start()) return null
        val localIp = getLocalIpAddress() ?: return null
        streamUrl = url
        streamReferer = referer
        streamUserAgent = userAgent
        val extension = if (url.lowercase().contains(".m3u8")) "m3u8" else "mp4"
        val proxyUrl = "http://$localIp:$serverPort/stream.$extension"
        Log.i(TAG, "Configured Cast media proxy: $proxyUrl -> $url (Referer: $referer)")
        return proxyUrl
    }

    /**
     * Clears the active media stream proxy.
     */
    fun clearMediaStream() {
        streamUrl = null
        streamReferer = null
        streamUserAgent = null
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

            val headers = mutableMapOf<String, String>()
            while (true) {
                val headerLine = reader.readLine() ?: break
                if (headerLine.isEmpty()) break
                val colonIdx = headerLine.indexOf(':')
                if (colonIdx > 0) {
                    val k = headerLine.substring(0, colonIdx).trim().lowercase()
                    val v = headerLine.substring(colonIdx + 1).trim()
                    headers[k] = v
                }
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

            // Handle GET / HEAD for media streaming proxy (handles /stream.* and any relative HLS playlists/segments)
            val isMediaRequest = (method == "GET" || method == "HEAD") && !path.contains(".vtt") && streamUrl != null
            if (isMediaRequest) {
                val baseStream = streamUrl
                if (baseStream.isNullOrEmpty()) {
                    val notFound = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n"
                    out.write(notFound.toByteArray(Charsets.UTF_8))
                    out.flush()
                    return
                }

                val targetUrl = if (path.contains("/stream")) {
                    baseStream
                } else {
                    try {
                        val baseUri = java.net.URI(baseStream)
                        val relativePath = if (path.startsWith("/")) path.substring(1) else path
                        baseUri.resolve(relativePath).toString()
                    } catch (e: Exception) {
                        baseStream
                    }
                }

                client.soTimeout = 30000 // Extended timeout for media streaming
                val targetReferer = streamReferer
                val targetUa = streamUserAgent ?: NetworkConfig.SHARED_USER_AGENT
                val rangeHeader = headers["range"]

                val reqBuilder = Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", targetUa)
                if (!targetReferer.isNullOrEmpty()) {
                    reqBuilder.header("Referer", targetReferer)
                }
                if (!rangeHeader.isNullOrEmpty()) {
                    reqBuilder.header("Range", rangeHeader)
                }

                try {
                    val response = NetworkConfig.permissiveOkHttpClient.newCall(reqBuilder.build()).execute()
                    val code = response.code
                    val body = response.body
                    val rawContentType = response.header("Content-Type")
                    val contentType = when {
                        !rawContentType.isNullOrBlank() -> rawContentType
                        targetUrl.lowercase().contains(".m3u8") -> "application/vnd.apple.mpegurl"
                        targetUrl.lowercase().contains(".ts") -> "video/mp2t"
                        else -> "video/mp4"
                    }
                    val contentLength = response.header("Content-Length")
                    val contentRange = response.header("Content-Range")
                    val acceptRanges = response.header("Accept-Ranges") ?: "bytes"

                    val statusLine = if (code == 206) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n"
                    val sb = StringBuilder(statusLine)
                    sb.append("Content-Type: ").append(contentType).append("\r\n")
                    if (contentLength != null) sb.append("Content-Length: ").append(contentLength).append("\r\n")
                    if (contentRange != null) sb.append("Content-Range: ").append(contentRange).append("\r\n")
                    sb.append("Accept-Ranges: ").append(acceptRanges).append("\r\n")
                    sb.append("Access-Control-Allow-Origin: *\r\n")
                    sb.append("Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n")
                    sb.append("Access-Control-Allow-Headers: *\r\n")
                    sb.append("Connection: close\r\n\r\n")

                    out.write(sb.toString().toByteArray(Charsets.UTF_8))
                    out.flush()

                    if (method == "GET" && body != null) {
                        body.byteStream().use { inputStream ->
                            val buffer = ByteArray(64 * 1024)
                            var bytesRead: Int
                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                out.write(buffer, 0, bytesRead)
                            }
                            out.flush()
                        }
                    }
                    response.close()
                    return
                } catch (e: Exception) {
                    Log.d(TAG, "Stream proxy connection ended: ${e.message}")
                    return
                }
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
