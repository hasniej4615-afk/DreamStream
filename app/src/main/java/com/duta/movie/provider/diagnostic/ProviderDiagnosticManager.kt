package com.duta.movie.provider.diagnostic

import android.util.Log
import com.duta.movie.data.local.InstalledProviderEntity
import com.duta.movie.util.NetworkConfig
import com.duta.movie.util.VideoExtractor
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

enum class EndpointStatus {
    ACTIVE,      // Responsive (200..299 OK)
    REDIRECTED,  // 301/302/307/308 redirect
    BLOCKED,     // 403 Forbidden / Cloudflare challenge
    DEAD,        // 404, 500, 502, connection refused, DNS failure, parked domain
    TIMEOUT      // Connection timed out
}

data class EndpointTestResult(
    val providerId: String,
    val providerName: String,
    val url: String,
    val status: EndpointStatus,
    val statusCode: Int? = null,
    val latencyMs: Long = 0L,
    val finalUrl: String? = null,
    val details: String? = null
)

data class ProviderDiagnosticSummary(
    val providerId: String,
    val providerName: String,
    val endpoints: List<EndpointTestResult>
) {
    val totalCount: Int get() = endpoints.size
    val activeCount: Int get() = endpoints.count { it.status == EndpointStatus.ACTIVE }
    val redirectedCount: Int get() = endpoints.count { it.status == EndpointStatus.REDIRECTED }
    val deadCount: Int get() = endpoints.count { it.status == EndpointStatus.DEAD }
    val timeoutCount: Int get() = endpoints.count { it.status == EndpointStatus.TIMEOUT }
    val blockedCount: Int get() = endpoints.count { it.status == EndpointStatus.BLOCKED }
}

object ProviderDiagnosticManager {
    private const val TAG = "ProviderDiagnostics"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // Dedicated fast probe client with 3.5s timeout and no redirect following (so we can detect 301/302 redirects)
    private val probeClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3500, TimeUnit.MILLISECONDS)
            .readTimeout(3500, TimeUnit.MILLISECONDS)
            .writeTimeout(3500, TimeUnit.MILLISECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build()
    }

    /**
     * Extracts all known candidate sites, domains, and cluster IPs for a provider.
     */
    fun getCandidateEndpoints(provider: InstalledProviderEntity): List<String> {
        val urls = mutableListOf<String>()

        // 1. Parse baseUrlsJson
        try {
            val element = json.parseToJsonElement(provider.baseUrlsJson)
            if (element is JsonArray) {
                element.forEach { primitive ->
                    val raw = (primitive as? JsonPrimitive)?.content?.trim()
                    if (!raw.isNullOrBlank() && raw.startsWith("http")) {
                        urls.add(raw.trimEnd('/'))
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Add provider-specific known fallback clusters & IP mirrors
        val id = provider.id.lowercase()
        val name = (provider.name + " " + provider.displayName).lowercase()

        if (id.contains("lk21") || name.contains("layarkaca") || name.contains("lk21")) {
            urls.addAll(VideoExtractor.BULLERSWOOD_FALLBACKS.map { it.trimEnd('/') })
        }
        if (id.contains("pencuri") || name.contains("pencuri")) {
            urls.addAll(VideoExtractor.PENCURI_FALLBACKS.map { it.trimEnd('/') })
        }
        if (id.contains("dutafilm") || name.contains("dutafilm")) {
            urls.addAll(VideoExtractor.DUTAFILM_WEB_FALLBACKS.map { it.trimEnd('/') })
            urls.addAll(VideoExtractor.CLUSTER_MIRROR_URLS.map { it.trimEnd('/') })
        }
        if (id.contains("dutamovie") || name.contains("dutamovie")) {
            urls.addAll(VideoExtractor.DUTAMOVIE_FALLBACKS.map { it.trimEnd('/') })
        }

        return urls.distinct()
    }

    /**
     * Tests a single endpoint with HTTP probe and content inspection.
     */
    suspend fun testEndpoint(
        providerId: String,
        providerName: String,
        url: String
    ): EndpointTestResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", NetworkConfig.SHARED_USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Connection", "close")
                .build()

            val response = probeClient.newCall(request).execute()
            val latency = System.currentTimeMillis() - start
            val code = response.code

            response.use { resp ->
                when (code) {
                    in 200..299 -> {
                        val bodySnippet = try {
                            resp.body?.source()?.let { src ->
                                val buffer = okio.Buffer()
                                src.read(buffer, 4096)
                                buffer.readUtf8()
                            }?.lowercase() ?: ""
                        } catch (_: Exception) { "" }

                        // Check for parked/dead/domain for sale signatures
                        val isParked = bodySnippet.contains("domain is for sale") ||
                                       bodySnippet.contains("buy this domain") ||
                                       bodySnippet.contains("dan.com") ||
                                       bodySnippet.contains("sedo") ||
                                       bodySnippet.contains("parking") ||
                                       bodySnippet.contains("godaddy") ||
                                       bodySnippet.contains("abovedomains")

                        if (isParked) {
                            EndpointTestResult(
                                providerId = providerId,
                                providerName = providerName,
                                url = url,
                                status = EndpointStatus.DEAD,
                                statusCode = code,
                                latencyMs = latency,
                                details = "Parked / For Sale Domain"
                            )
                        } else {
                            EndpointTestResult(
                                providerId = providerId,
                                providerName = providerName,
                                url = url,
                                status = EndpointStatus.ACTIVE,
                                statusCode = code,
                                latencyMs = latency,
                                details = "Alive (${latency}ms)"
                            )
                        }
                    }
                    301, 302, 307, 308 -> {
                        val location = resp.header("Location") ?: "Unknown destination"
                        EndpointTestResult(
                            providerId = providerId,
                            providerName = providerName,
                            url = url,
                            status = EndpointStatus.REDIRECTED,
                            statusCode = code,
                            latencyMs = latency,
                            finalUrl = location,
                            details = "Redirects -> $location"
                        )
                    }
                    403 -> {
                        EndpointTestResult(
                            providerId = providerId,
                            providerName = providerName,
                            url = url,
                            status = EndpointStatus.BLOCKED,
                            statusCode = code,
                            latencyMs = latency,
                            details = "403 Forbidden / Cloudflare"
                        )
                    }
                    404, 410 -> {
                        EndpointTestResult(
                            providerId = providerId,
                            providerName = providerName,
                            url = url,
                            status = EndpointStatus.DEAD,
                            statusCode = code,
                            latencyMs = latency,
                            details = "$code Not Found"
                        )
                    }
                    else -> {
                        EndpointTestResult(
                            providerId = providerId,
                            providerName = providerName,
                            url = url,
                            status = EndpointStatus.DEAD,
                            statusCode = code,
                            latencyMs = latency,
                            details = "HTTP $code Error"
                        )
                    }
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            val latency = System.currentTimeMillis() - start
            EndpointTestResult(
                providerId = providerId,
                providerName = providerName,
                url = url,
                status = EndpointStatus.TIMEOUT,
                latencyMs = latency,
                details = "Timeout (> 3.5s)"
            )
        } catch (e: java.net.UnknownHostException) {
            EndpointTestResult(
                providerId = providerId,
                providerName = providerName,
                url = url,
                status = EndpointStatus.DEAD,
                details = "DNS / Host Unresolved"
            )
        } catch (e: java.net.ConnectException) {
            EndpointTestResult(
                providerId = providerId,
                providerName = providerName,
                url = url,
                status = EndpointStatus.DEAD,
                details = "Connection Refused"
            )
        } catch (e: Throwable) {
            val latency = System.currentTimeMillis() - start
            EndpointTestResult(
                providerId = providerId,
                providerName = providerName,
                url = url,
                status = EndpointStatus.DEAD,
                latencyMs = latency,
                details = e.message?.take(30) ?: "Network Error"
            )
        }
    }

    /**
     * Runs tests across all candidate endpoints concurrently with a permit limiter.
     */
    suspend fun testAllEndpoints(
        providers: List<InstalledProviderEntity>,
        concurrency: Int = 6,
        onProgress: (EndpointTestResult) -> Unit
    ): List<ProviderDiagnosticSummary> = withContext(Dispatchers.IO) {
        val semaphore = Semaphore(concurrency)
        val allTasks = mutableListOf<Deferred<EndpointTestResult>>()

        coroutineScope {
            for (provider in providers) {
                val endpoints = getCandidateEndpoints(provider)
                for (url in endpoints) {
                    val deferred = async {
                        semaphore.withPermit {
                            val res = testEndpoint(provider.id, provider.displayName, url)
                            onProgress(res)
                            res
                        }
                    }
                    allTasks.add(deferred)
                }
            }
        }

        val results = allTasks.awaitAll()

        // Group by provider
        providers.map { p ->
            val providerResults = results.filter { it.providerId == p.id }
            ProviderDiagnosticSummary(
                providerId = p.id,
                providerName = p.displayName,
                endpoints = providerResults
            )
        }
    }
}
