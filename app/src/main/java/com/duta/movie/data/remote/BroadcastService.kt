package com.duta.movie.data.remote

import android.util.Log
import com.duta.movie.model.BroadcastMessage
import com.duta.movie.util.SupabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BroadcastService @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "BroadcastService"
        private const val BROADCAST_PIN = "__GLOBAL_BROADCAST__"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    /**
     * Fetches the current global broadcast message from Supabase.
     */
    suspend fun getBroadcast(): Result<BroadcastMessage?> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) {
            return@withContext Result.failure(Exception("Supabase is not configured."))
        }

        try {
            val url = "${SupabaseConfig.PROJECT_URL.trimEnd('/')}/rest/v1/user_backups?pin_code=eq.$BROADCAST_PIN&select=backup_data,updated_at"

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
                    Log.e(TAG, "Failed to fetch broadcast: HTTP ${response.code} $errorBody")
                    return@withContext Result.failure(Exception("HTTP ${response.code}: $errorBody"))
                }

                val responseBody = response.body?.string() ?: "[]"
                val jsonArray = JSONArray(responseBody)
                if (jsonArray.length() == 0) {
                    return@withContext Result.success(null)
                }

                val record = jsonArray.getJSONObject(0)
                val backupDataObj = record.optJSONObject("backup_data")
                    ?: record.optString("backup_data").let { if (it.isNotBlank()) JSONObject(it) else null }
                    ?: return@withContext Result.success(null)

                val id = backupDataObj.optString("id", "")
                val title = backupDataObj.optString("title", "Pengumuman")
                val message = backupDataObj.optString("message", "")
                val author = backupDataObj.optString("author", "Admin")
                val type = backupDataObj.optString("type", "info")
                val timestamp = backupDataObj.optLong("timestamp", System.currentTimeMillis())
                val isActive = if (backupDataObj.has("is_active")) {
                    backupDataObj.optBoolean("is_active", true)
                } else {
                    backupDataObj.optBoolean("isActive", true)
                }

                val broadcastMessage = BroadcastMessage(
                    id = id,
                    title = title,
                    message = message,
                    author = author,
                    type = type,
                    timestamp = timestamp,
                    isActive = isActive
                )

                Result.success(broadcastMessage)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching broadcast", e)
            Result.failure(e)
        }
    }

    /**
     * Publishes a new broadcast message across all user installations.
     */
    suspend fun sendBroadcast(
        title: String,
        message: String,
        type: String = "info",
        author: String = "Admin"
    ): Result<BroadcastMessage> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) {
            return@withContext Result.failure(Exception("Supabase is not configured."))
        }

        val cleanTitle = title.trim().ifEmpty { "Pengumuman" }
        val cleanMsg = message.trim()
        if (cleanMsg.isEmpty()) {
            return@withContext Result.failure(Exception("Mesej siaran tidak boleh kosong."))
        }

        val broadcastId = "bcast_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        val now = System.currentTimeMillis()

        try {
            val url = "${SupabaseConfig.PROJECT_URL.trimEnd('/')}/rest/v1/user_backups?on_conflict=pin_code"

            val backupData = JSONObject().apply {
                put("id", broadcastId)
                put("title", cleanTitle)
                put("message", cleanMsg)
                put("author", author.trim().ifEmpty { "Admin" })
                put("type", type.lowercase())
                put("timestamp", now)
                put("is_active", true)
            }

            val payload = JSONObject().apply {
                put("pin_code", BROADCAST_PIN)
                put("username", "AdminBroadcast")
                put("backup_data", backupData)
            }

            val requestBody = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SupabaseConfig.ANON_KEY)
                .addHeader("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates,return=representation")
                .post(requestBody)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    Log.e(TAG, "Failed to send broadcast: HTTP ${response.code} $errorBody")
                    return@withContext Result.failure(Exception("HTTP ${response.code}: $errorBody"))
                }

                val broadcast = BroadcastMessage(
                    id = broadcastId,
                    title = cleanTitle,
                    message = cleanMsg,
                    author = author,
                    type = type.lowercase(),
                    timestamp = now,
                    isActive = true
                )
                Result.success(broadcast)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending broadcast", e)
            Result.failure(e)
        }
    }

    /**
     * Clears or deletes the global broadcast message.
     */
    suspend fun clearBroadcast(): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) {
            return@withContext Result.failure(Exception("Supabase is not configured."))
        }

        try {
            val url = "${SupabaseConfig.PROJECT_URL.trimEnd('/')}/rest/v1/user_backups?pin_code=eq.$BROADCAST_PIN"

            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SupabaseConfig.ANON_KEY)
                .addHeader("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
                .delete()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    Log.e(TAG, "Failed to clear broadcast: HTTP ${response.code} $errorBody")
                    return@withContext Result.failure(Exception("HTTP ${response.code}: $errorBody"))
                }
                Result.success(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing broadcast", e)
            Result.failure(e)
        }
    }
}
