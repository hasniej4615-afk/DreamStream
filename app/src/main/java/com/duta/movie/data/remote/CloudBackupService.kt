package com.duta.movie.data.remote

import android.util.Log
import com.duta.movie.util.SupabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Service for backing up and restoring user profiles and settings to/from Supabase Cloud.
 * Uses a unique 6-digit PIN code for instant 1-tap restore across devices (especially Android TV).
 */
@Singleton
class CloudBackupService @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "CloudBackupService"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    /**
     * Generates a random 6-digit numeric PIN code (100000 to 999999).
     */
    fun generatePin(): String {
        return Random.nextInt(100000, 1000000).toString()
    }

    /**
     * Uploads the backup JSON to Supabase with the specified 6-digit PIN code.
     * Uses UPSERT (on_conflict=pin_code) so users can update their existing cloud backup.
     */
    suspend fun uploadBackup(pinCode: String, username: String, backupJson: String): Result<String> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) {
            return@withContext Result.failure(Exception("Supabase is not configured."))
        }

        val cleanPin = pinCode.filter { it.isDigit() }
        if (cleanPin.length != 6) {
            return@withContext Result.failure(Exception("PIN must be exactly 6 digits."))
        }

        try {
            val url = "${SupabaseConfig.PROJECT_URL.trimEnd('/')}/rest/v1/user_backups?on_conflict=pin_code"

            val payload = JSONObject().apply {
                put("pin_code", cleanPin)
                put("username", username.trim())
                // Store as jsonb object if valid json, otherwise string
                try {
                    put("backup_data", JSONObject(backupJson))
                } catch (_: Exception) {
                    put("backup_data", backupJson)
                }
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
                    Log.e(TAG, "Failed to upload backup: HTTP ${response.code} $errorBody")
                    return@withContext Result.failure(Exception("HTTP ${response.code}: $errorBody"))
                }
                Log.d(TAG, "Successfully uploaded backup with PIN: $cleanPin")
                Result.success(cleanPin)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading backup with PIN $cleanPin", e)
            Result.failure(e)
        }
    }

    /**
     * Fetches the backup JSON from Supabase using the 6-digit PIN code.
     */
    suspend fun fetchBackup(pinCode: String): Result<String> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) {
            return@withContext Result.failure(Exception("Supabase is not configured."))
        }

        val cleanPin = pinCode.filter { it.isDigit() }
        if (cleanPin.length != 6) {
            return@withContext Result.failure(Exception("Please enter a valid 6-digit PIN."))
        }

        try {
            val url = "${SupabaseConfig.PROJECT_URL.trimEnd('/')}/rest/v1/user_backups?pin_code=eq.$cleanPin&select=backup_data"

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
                    Log.e(TAG, "Failed to fetch backup: HTTP ${response.code} $errorBody")
                    return@withContext Result.failure(Exception("HTTP ${response.code}: $errorBody"))
                }

                val responseBody = response.body?.string() ?: "[]"
                val jsonArray = JSONArray(responseBody)
                if (jsonArray.length() == 0) {
                    return@withContext Result.failure(Exception("No backup found for PIN $cleanPin."))
                }

                val record = jsonArray.getJSONObject(0)
                val backupDataObj = record.opt("backup_data")
                val backupJsonStr = backupDataObj?.toString() ?: ""

                if (backupJsonStr.isBlank()) {
                    return@withContext Result.failure(Exception("Backup data is empty."))
                }

                Result.success(backupJsonStr)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching backup for PIN $cleanPin", e)
            Result.failure(e)
        }
    }
}
