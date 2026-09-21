package com.duta.movie.data.remote

import android.util.Log
import com.duta.movie.model.Comment
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
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommentService @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "CommentService"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun getComments(videoId: String): Result<List<Comment>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) {
            return@withContext Result.success(emptyList())
        }

        try {
            val encodedVideoId = URLEncoder.encode(videoId, "UTF-8")
            val url = "${SupabaseConfig.PROJECT_URL.trimEnd('/')}/rest/v1/comments?video_id=eq.$encodedVideoId&order=created_at.desc&limit=100"

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
                    Log.e(TAG, "Failed to fetch comments: HTTP ${response.code} $errorBody")
                    return@withContext Result.failure(Exception("HTTP ${response.code}: $errorBody"))
                }

                val responseBody = response.body?.string() ?: "[]"
                val comments = json.decodeFromString<List<Comment>>(responseBody)
                Result.success(comments)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching comments for $videoId", e)
            Result.failure(e)
        }
    }

    suspend fun postComment(videoId: String, userName: String, commentText: String): Result<Comment> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) {
            return@withContext Result.failure(Exception("Supabase is not configured. Please set PROJECT_URL and ANON_KEY in SupabaseConfig.kt"))
        }

        try {
            val url = "${SupabaseConfig.PROJECT_URL.trimEnd('/')}/rest/v1/comments"
            val payload = mapOf(
                "video_id" to videoId,
                "user_name" to userName.trim(),
                "comment" to commentText.trim()
            )
            val jsonBody = json.encodeToString(payload)

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
                    Log.e(TAG, "Failed to post comment: HTTP ${response.code} $errorBody")
                    return@withContext Result.failure(Exception("HTTP ${response.code}: $errorBody"))
                }

                val responseBody = response.body?.string() ?: "[]"
                val list = json.decodeFromString<List<Comment>>(responseBody)
                val inserted = list.firstOrNull() ?: Comment(
                    videoId = videoId,
                    userName = userName.trim(),
                    comment = commentText.trim()
                )
                Result.success(inserted)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error posting comment for $videoId", e)
            Result.failure(e)
        }
    }

    suspend fun editComment(commentId: Long, newText: String): Result<Comment> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) {
            return@withContext Result.failure(Exception("Supabase is not configured"))
        }

        try {
            val url = "${SupabaseConfig.PROJECT_URL.trimEnd('/')}/rest/v1/comments?id=eq.$commentId"
            val payload = mapOf("comment" to newText.trim())
            val jsonBody = json.encodeToString(payload)

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
                    Log.e(TAG, "Failed to edit comment: HTTP ${response.code} $errorBody")
                    return@withContext Result.failure(Exception("HTTP ${response.code}: $errorBody"))
                }

                val responseBody = response.body?.string() ?: "[]"
                val list = json.decodeFromString<List<Comment>>(responseBody)
                val updated = list.firstOrNull() ?: return@withContext Result.failure(Exception("Comment not found or update blocked"))
                Result.success(updated)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error editing comment $commentId", e)
            Result.failure(e)
        }
    }

    suspend fun deleteComment(commentId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) {
            return@withContext Result.failure(Exception("Supabase is not configured"))
        }

        try {
            val url = "${SupabaseConfig.PROJECT_URL.trimEnd('/')}/rest/v1/comments?id=eq.$commentId"

            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SupabaseConfig.ANON_KEY)
                .addHeader("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
                .delete()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    Log.e(TAG, "Failed to delete comment: HTTP ${response.code} $errorBody")
                    return@withContext Result.failure(Exception("HTTP ${response.code}: $errorBody"))
                }
                Result.success(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting comment $commentId", e)
            Result.failure(e)
        }
    }
}
