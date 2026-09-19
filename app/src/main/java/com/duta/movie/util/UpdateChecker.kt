package com.duta.movie.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class UpdateInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val telegramUrl: String,
    val changelog: String,
    val mirrors: List<String>? = null,
    val pencuriMirrors: List<String>? = null,
    val apkUrl: String? = null
)

object UpdateChecker {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    // Use the raw Gist URL provided by the user (removed commit hash for latest version)
    private const val UPDATE_URL = "https://gist.githubusercontent.com/hasniej4615-afk/2f535093cc562e77ed8f68c9e596b17c/raw/update.json"

    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(UPDATE_URL)
                .build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        return@withContext json.decodeFromString<UpdateInfo>(body)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("UpdateChecker", "Failed to check for updates", e)
        }
        return@withContext null
    }

    suspend fun fetchRemoteMirrors(): List<String>? = withContext(Dispatchers.IO) {
        try {
            checkForUpdate()?.mirrors?.filter { it.startsWith("http") }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fetchRemotePencuriMirrors(): List<String>? = withContext(Dispatchers.IO) {
        try {
            checkForUpdate()?.pencuriMirrors?.filter { it.startsWith("http") }
        } catch (_: Exception) {
            null
        }
    }

    fun openTelegram(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("UpdateChecker", "Failed to open Telegram", e)
        }
    }
}
