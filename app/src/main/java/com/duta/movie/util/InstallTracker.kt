package com.duta.movie.util

import android.content.Context
import android.util.Log
import com.duta.movie.data.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object InstallTracker {
    private const val TAG = "InstallTracker"
    private const val NAMESPACE = "duta-movie-official"
    private const val KEY = "installs"
    private const val HIT_URL = "https://abacus.jasoncameron.dev/hit/$NAMESPACE/$KEY"
    private const val GET_URL = "https://abacus.jasoncameron.dev/get/$NAMESPACE/$KEY"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Registers this device's installation if not already registered.
     * Guaranteed to only increment once per device installation.
     */
    suspend fun registerInstall(context: Context, preferenceManager: PreferenceManager): Boolean = withContext(Dispatchers.IO) {
        try {
            val isRegistered = preferenceManager.isInstallRegistered.first()
            if (isRegistered) {
                Log.d(TAG, "Device already registered. Skipping install ping.")
                return@withContext true
            }

            val request = Request.Builder()
                .url(HIT_URL)
                .header("User-Agent", "DutaMovie/${com.duta.movie.BuildConfig.VERSION_NAME}")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val count = json.optInt("value", -1)
                    if (count > 0) {
                        preferenceManager.setInstallRegistered(true)
                        preferenceManager.setLastKnownInstallCount(count)
                        Log.i(TAG, "Install registered successfully! Total installs: $count")
                        return@withContext true
                    }
                }
                Log.w(TAG, "Install register response non-success: ${response.code}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register install (will retry next launch): ${e.message}")
        }
        return@withContext false
    }

    /**
     * Retrieves the current total installation count (read-only, does NOT increment).
     */
    suspend fun fetchTotalInstalls(preferenceManager: PreferenceManager? = null): Int? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GET_URL)
                .header("User-Agent", "DutaMovie/${com.duta.movie.BuildConfig.VERSION_NAME}")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code == 404) {
                    // Counter not initialized yet on server (0 installs)
                    preferenceManager?.setLastKnownInstallCount(0)
                    return@withContext 0
                }
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val count = json.optInt("value", -1)
                    if (count >= 0) {
                        preferenceManager?.setLastKnownInstallCount(count)
                        return@withContext count
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch total installs: ${e.message}")
        }
        return@withContext null
    }
}
