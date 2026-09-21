package com.duta.movie.data.remote

import android.util.Log
import com.duta.movie.model.Recommendation
import com.duta.movie.model.Video
import com.duta.movie.util.SupabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecommendationService @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "RecommendationService"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private fun getCurrentIsoTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    private fun getDaysAgoIsoTimestamp(days: Int): String {
        val calendar = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.add(java.util.Calendar.DAY_OF_YEAR, -days)
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(calendar.time)
    }

    suspend fun getRecommendations(): Result<List<Recommendation>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) {
            return@withContext Result.success(emptyList())
        }

        try {
            val sevenDaysAgo = getDaysAgoIsoTimestamp(7)
            val encodedTimestamp = URLEncoder.encode(sevenDaysAgo, "UTF-8")
            val url = "${SupabaseConfig.PROJECT_URL.trimEnd('/')}/rest/v1/recommendations?updated_at=gte.$encodedTimestamp&order=updated_at.desc&limit=100"

            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SupabaseConfig.ANON_KEY)
                .addHeader("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    Log.e(TAG, "Failed to fetch recommendations: HTTP ${response.code} $errorBody")
                    return@withContext Result.failure(Exception("HTTP ${response.code}: $errorBody"))
                }

                val responseBody = response.body?.string() ?: "[]"
                val list = json.decodeFromString<List<Recommendation>>(responseBody)

                // Client-side 7-day safeguard
                val sevenDaysMillis = 7L * 24 * 60 * 60 * 1000
                val now = System.currentTimeMillis()
                val dateParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val freshList = list.filter { rec ->
                    val timestamp = rec.updatedAt ?: rec.createdAt
                    if (timestamp.isNullOrBlank()) true
                    else {
                        try {
                            val clean = timestamp.take(19)
                            val parsedDate = dateParser.parse(clean)
                            parsedDate != null && (now - parsedDate.time) <= sevenDaysMillis
                        } catch (_: Exception) {
                            true
                        }
                    }
                }

                Result.success(freshList)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching recommendations", e)
            Result.failure(e)
        }
    }

    suspend fun getRecommendationForVideo(videoId: String): Result<Recommendation?> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) {
            return@withContext Result.success(null)
        }

        try {
            val encodedVideoId = URLEncoder.encode(videoId, "UTF-8")
            val url = "${SupabaseConfig.PROJECT_URL.trimEnd('/')}/rest/v1/recommendations?video_id=eq.$encodedVideoId&limit=1"

            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SupabaseConfig.ANON_KEY)
                .addHeader("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    Log.e(TAG, "Failed to fetch recommendation for $videoId: HTTP ${response.code} $errorBody")
                    return@withContext Result.failure(Exception("HTTP ${response.code}: $errorBody"))
                }

                val responseBody = response.body?.string() ?: "[]"
                val list = json.decodeFromString<List<Recommendation>>(responseBody)
                Result.success(list.firstOrNull())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching recommendation for $videoId", e)
            Result.failure(e)
        }
    }

    suspend fun toggleRecommendation(
        video: Video,
        isRecommending: Boolean,
        knownExistingCount: Int? = null
    ): Result<Recommendation> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) {
            return@withContext Result.failure(Exception("Supabase is not configured"))
        }

        try {
            val encodedVideoId = URLEncoder.encode(video.id, "UTF-8")
            val nowIso = getCurrentIsoTimestamp()

            // Fast-path: If count is already known, execute the write directly without prior GET
            if (knownExistingCount != null) {
                if (!isRecommending && knownExistingCount <= 1) {
                    val deleteUrl = "${SupabaseConfig.PROJECT_URL.trimEnd('/')}/rest/v1/recommendations?video_id=eq.$encodedVideoId"
                    val deleteRequest = Request.Builder()
                        .url(deleteUrl)
                        .addHeader("apikey", SupabaseConfig.ANON_KEY)
                        .addHeader("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
                        .delete()
                        .build()
                    try {
                        okHttpClient.newCall(deleteRequest).execute().close()
                    } catch (e: Exception) {
                        Log.e(TAG, "Fast delete failed for ${video.id}", e)
                    }
                    return@withContext Result.success(
                        Recommendation(
                            videoId = video.id,
                            title = video.title,
                            thumbnailUrl = video.thumbnailUrl,
                            videoUrl = video.videoUrl,
                            quality = video.quality,
                            recommendCount = 0
                        )
                    )
                }

                if (isRecommending && knownExistingCount == 0) {
                    val url = "${SupabaseConfig.PROJECT_URL.trimEnd('/')}/rest/v1/recommendations"
                    val jsonPayload = kotlinx.serialization.json.buildJsonObject {
                        put("video_id", kotlinx.serialization.json.JsonPrimitive(video.id))
                        put("title", kotlinx.serialization.json.JsonPrimitive(video.title))
                        put("thumbnail_url", kotlinx.serialization.json.JsonPrimitive(video.thumbnailUrl))
                        put("video_url", kotlinx.serialization.json.JsonPrimitive(video.videoUrl))
                        put("quality", kotlinx.serialization.json.JsonPrimitive(video.quality))
                        put("recommend_count", kotlinx.serialization.json.JsonPrimitive(1))
                        put("updated_at", kotlinx.serialization.json.JsonPrimitive(nowIso))
                    }
                    val request = Request.Builder()
                        .url(url)
                        .addHeader("apikey", SupabaseConfig.ANON_KEY)
                        .addHeader("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=representation")
                        .post(jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .build()

                    val response = okHttpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string() ?: "[]"
                        response.close()
                        val list = json.decodeFromString<List<Recommendation>>(responseBody)
                        val inserted = list.firstOrNull() ?: Recommendation(
                            videoId = video.id,
                            title = video.title,
                            thumbnailUrl = video.thumbnailUrl,
                            videoUrl = video.videoUrl,
                            quality = video.quality,
                            recommendCount = 1
                        )
                        return@withContext Result.success(inserted)
                    }
                    response.close()
                } else if (knownExistingCount > 0) {
                    val newCount = if (isRecommending) knownExistingCount + 1 else (knownExistingCount - 1).coerceAtLeast(0)
                    val url = "${SupabaseConfig.PROJECT_URL.trimEnd('/')}/rest/v1/recommendations?video_id=eq.$encodedVideoId"
                    val jsonPayload = kotlinx.serialization.json.buildJsonObject {
                        put("recommend_count", kotlinx.serialization.json.JsonPrimitive(newCount))
                        put("updated_at", kotlinx.serialization.json.JsonPrimitive(nowIso))
                        if (video.title.isNotBlank()) put("title", kotlinx.serialization.json.JsonPrimitive(video.title))
                        if (video.thumbnailUrl.isNotBlank()) put("thumbnail_url", kotlinx.serialization.json.JsonPrimitive(video.thumbnailUrl))
                        if (video.quality.isNotBlank()) put("quality", kotlinx.serialization.json.JsonPrimitive(video.quality))
                    }
                    val request = Request.Builder()
                        .url(url)
                        .addHeader("apikey", SupabaseConfig.ANON_KEY)
                        .addHeader("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=representation")
                        .patch(jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .build()

                    val response = okHttpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string() ?: "[]"
                        response.close()
                        val list = json.decodeFromString<List<Recommendation>>(responseBody)
                        val updated = list.firstOrNull() ?: Recommendation(
                            videoId = video.id,
                            title = video.title,
                            thumbnailUrl = video.thumbnailUrl,
                            videoUrl = video.videoUrl,
                            quality = video.quality,
                            recommendCount = newCount
                        )
                        return@withContext Result.success(updated)
                    }
                    response.close()
                }
            }

            // Fallback: standard check and update if fast path was skipped or encountered conflict
            val existing = getRecommendationForVideo(video.id).getOrNull()

            if (existing == null) {
                if (!isRecommending) {
                    return@withContext Result.success(
                        Recommendation(
                            videoId = video.id,
                            title = video.title,
                            thumbnailUrl = video.thumbnailUrl,
                            videoUrl = video.videoUrl,
                            quality = video.quality,
                            recommendCount = 0
                        )
                    )
                }

                // Insert new recommendation with count 1
                val url = "${SupabaseConfig.PROJECT_URL.trimEnd('/')}/rest/v1/recommendations"
                val jsonPayload = kotlinx.serialization.json.buildJsonObject {
                    put("video_id", kotlinx.serialization.json.JsonPrimitive(video.id))
                    put("title", kotlinx.serialization.json.JsonPrimitive(video.title))
                    put("thumbnail_url", kotlinx.serialization.json.JsonPrimitive(video.thumbnailUrl))
                    put("video_url", kotlinx.serialization.json.JsonPrimitive(video.videoUrl))
                    put("quality", kotlinx.serialization.json.JsonPrimitive(video.quality))
                    put("recommend_count", kotlinx.serialization.json.JsonPrimitive(1))
                    put("updated_at", kotlinx.serialization.json.JsonPrimitive(nowIso))
                }
                val jsonBody = jsonPayload.toString()

                val request = Request.Builder()
                    .url(url)
                    .addHeader("apikey", SupabaseConfig.ANON_KEY)
                    .addHeader("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=representation")
                    .post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: ""
                        Log.e(TAG, "Failed to insert recommendation: HTTP ${response.code} $errorBody")
                        return@withContext Result.failure(Exception("HTTP ${response.code}: $errorBody"))
                    }

                    val responseBody = response.body?.string() ?: "[]"
                    val list = json.decodeFromString<List<Recommendation>>(responseBody)
                    val inserted = list.firstOrNull() ?: Recommendation(
                        videoId = video.id,
                        title = video.title,
                        thumbnailUrl = video.thumbnailUrl,
                        videoUrl = video.videoUrl,
                        quality = video.quality,
                        recommendCount = 1
                    )
                    Result.success(inserted)
                }
            } else {
                val newCount = if (isRecommending) existing.recommendCount + 1 else (existing.recommendCount - 1).coerceAtLeast(0)
                if (!isRecommending && newCount <= 0) {
                    val deleteUrl = "${SupabaseConfig.PROJECT_URL.trimEnd('/')}/rest/v1/recommendations?video_id=eq.$encodedVideoId"
                    val deleteRequest = Request.Builder()
                        .url(deleteUrl)
                        .addHeader("apikey", SupabaseConfig.ANON_KEY)
                        .addHeader("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
                        .delete()
                        .build()
                    try {
                        okHttpClient.newCall(deleteRequest).execute().close()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to delete recommendation for ${video.id}", e)
                    }
                    return@withContext Result.success(existing.copy(recommendCount = 0))
                }

                val url = "${SupabaseConfig.PROJECT_URL.trimEnd('/')}/rest/v1/recommendations?video_id=eq.$encodedVideoId"
                val jsonPayload = kotlinx.serialization.json.buildJsonObject {
                    put("recommend_count", kotlinx.serialization.json.JsonPrimitive(newCount))
                    put("updated_at", kotlinx.serialization.json.JsonPrimitive(nowIso))
                    if (video.title.isNotBlank()) put("title", kotlinx.serialization.json.JsonPrimitive(video.title))
                    if (video.thumbnailUrl.isNotBlank()) put("thumbnail_url", kotlinx.serialization.json.JsonPrimitive(video.thumbnailUrl))
                    if (video.quality.isNotBlank()) put("quality", kotlinx.serialization.json.JsonPrimitive(video.quality))
                }
                val jsonBody = jsonPayload.toString()

                val request = Request.Builder()
                    .url(url)
                    .addHeader("apikey", SupabaseConfig.ANON_KEY)
                    .addHeader("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=representation")
                    .patch(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: ""
                        Log.e(TAG, "Failed to update recommendation: HTTP ${response.code} $errorBody")
                        return@withContext Result.failure(Exception("HTTP ${response.code}: $errorBody"))
                    }

                    val responseBody = response.body?.string() ?: "[]"
                    val list = json.decodeFromString<List<Recommendation>>(responseBody)
                    val updated = list.firstOrNull() ?: existing.copy(recommendCount = newCount)
                    Result.success(updated)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling recommendation for ${video.id}", e)
            Result.failure(e)
        }
    }
}
