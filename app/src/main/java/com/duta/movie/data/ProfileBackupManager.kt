package com.duta.movie.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ProfileBackupData(
    val version: Int = 1,
    val appVersion: String = "2.0.9 Cloud 7",
    val exportedAt: Long = System.currentTimeMillis(),
    val username: String = "",
    val avatarBase64: String? = null,
    val defaultSubtitleLanguage: String = "Indonesian",
    val autoSubtitleEnabled: Boolean = true,
    val voiceEnhancerMode: Int = 0,
    val mobileLandscapeEnabled: Boolean = false,
    val debugModeEnabled: Boolean = false,
    val uiSafeAreaPadding: Int = 0,
    val uiHeroHeightOffset: Int = 0,
    val uiScaleFactor: Float = 1.0f,
    val uiThumbnailScaleFactor: Float = 1.0f,
    val enabledCategories: Set<String> = emptySet(),
    val myList: Set<String> = emptySet(),
    val recentlyWatched: List<String> = emptyList(),
    val videoProgress: Map<String, Long> = emptyMap(),
    val videoDuration: Map<String, Long> = emptyMap()
)

object ProfileBackupManager {
    private const val TAG = "ProfileBackupManager"
    const val CURRENT_VERSION = 1

    fun toJson(data: ProfileBackupData): String {
        val root = JSONObject()
        root.put("version", data.version)
        root.put("app_version", data.appVersion)
        root.put("exported_at", data.exportedAt)

        val profile = JSONObject()
        profile.put("username", data.username)
        if (!data.avatarBase64.isNullOrBlank()) {
            profile.put("avatar_base64", data.avatarBase64)
        }
        root.put("profile", profile)

        val settings = JSONObject()
        settings.put("default_subtitle_language", data.defaultSubtitleLanguage)
        settings.put("auto_subtitle_enabled", data.autoSubtitleEnabled)
        settings.put("voice_enhancer_mode", data.voiceEnhancerMode)
        settings.put("mobile_landscape_enabled", data.mobileLandscapeEnabled)
        settings.put("debug_mode_enabled", data.debugModeEnabled)
        settings.put("ui_safe_area_padding", data.uiSafeAreaPadding)
        settings.put("ui_hero_height_offset", data.uiHeroHeightOffset)
        settings.put("ui_scale_factor", data.uiScaleFactor.toDouble())
        settings.put("ui_thumbnail_scale_factor", data.uiThumbnailScaleFactor.toDouble())

        val categoriesArr = JSONArray()
        data.enabledCategories.forEach { categoriesArr.put(it) }
        settings.put("enabled_categories", categoriesArr)
        root.put("settings", settings)

        val userData = JSONObject()
        val myListArr = JSONArray()
        data.myList.forEach { myListArr.put(it) }
        userData.put("my_list", myListArr)

        val recentArr = JSONArray()
        data.recentlyWatched.forEach { recentArr.put(it) }
        userData.put("recently_watched", recentArr)

        val progressObj = JSONObject()
        data.videoProgress.forEach { (k, v) -> progressObj.put(k, v) }
        userData.put("video_progress", progressObj)

        val durationObj = JSONObject()
        data.videoDuration.forEach { (k, v) -> durationObj.put(k, v) }
        userData.put("video_duration", durationObj)

        root.put("user_data", userData)

        return root.toString(2)
    }

    fun fromJson(jsonStr: String): ProfileBackupData? {
        return try {
            val root = JSONObject(jsonStr)
            val version = root.optInt("version", 1)
            val appVersion = root.optString("app_version", "2.0.9 Cloud 7")
            val exportedAt = root.optLong("exported_at", System.currentTimeMillis())

            val profile = root.optJSONObject("profile") ?: JSONObject()
            val username = profile.optString("username", "")
            val avatarBase64 = if (profile.has("avatar_base64")) profile.optString("avatar_base64") else null

            val settings = root.optJSONObject("settings") ?: JSONObject()
            val defaultSubtitleLang = settings.optString("default_subtitle_language", "Indonesian")
            val autoSubtitle = settings.optBoolean("auto_subtitle_enabled", true)
            val voiceEnhancer = settings.optInt("voice_enhancer_mode", 0)
            val mobileLandscape = settings.optBoolean("mobile_landscape_enabled", false)
            val debugMode = settings.optBoolean("debug_mode_enabled", false)
            val uiSafeArea = settings.optInt("ui_safe_area_padding", 0)
            val uiHeroOffset = settings.optInt("ui_hero_height_offset", 0)
            val uiScale = settings.optDouble("ui_scale_factor", 1.0).toFloat()
            val uiThumbScale = settings.optDouble("ui_thumbnail_scale_factor", 1.0).toFloat()

            val categoriesSet = mutableSetOf<String>()
            val catArr = settings.optJSONArray("enabled_categories")
            if (catArr != null) {
                for (i in 0 until catArr.length()) {
                    categoriesSet.add(catArr.optString(i))
                }
            }

            val userData = root.optJSONObject("user_data") ?: JSONObject()
            val myListSet = mutableSetOf<String>()
            val myListArr = userData.optJSONArray("my_list")
            if (myListArr != null) {
                for (i in 0 until myListArr.length()) {
                    myListSet.add(myListArr.optString(i))
                }
            }

            val recentList = mutableListOf<String>()
            val recentArr = userData.optJSONArray("recently_watched")
            if (recentArr != null) {
                for (i in 0 until recentArr.length()) {
                    recentList.add(recentArr.optString(i))
                }
            }

            val progressMap = mutableMapOf<String, Long>()
            val progressObj = userData.optJSONObject("video_progress")
            if (progressObj != null) {
                val keys = progressObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    progressMap[k] = progressObj.optLong(k, 0L)
                }
            }

            val durationMap = mutableMapOf<String, Long>()
            val durationObj = userData.optJSONObject("video_duration")
            if (durationObj != null) {
                val keys = durationObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    durationMap[k] = durationObj.optLong(k, 0L)
                }
            }

            ProfileBackupData(
                version = version,
                appVersion = appVersion,
                exportedAt = exportedAt,
                username = username,
                avatarBase64 = avatarBase64,
                defaultSubtitleLanguage = defaultSubtitleLang,
                autoSubtitleEnabled = autoSubtitle,
                voiceEnhancerMode = voiceEnhancer,
                mobileLandscapeEnabled = mobileLandscape,
                debugModeEnabled = debugMode,
                uiSafeAreaPadding = uiSafeArea,
                uiHeroHeightOffset = uiHeroOffset,
                uiScaleFactor = uiScale,
                uiThumbnailScaleFactor = uiThumbScale,
                enabledCategories = categoriesSet,
                myList = myListSet,
                recentlyWatched = recentList,
                videoProgress = progressMap,
                videoDuration = durationMap
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse backup JSON", e)
            null
        }
    }

    fun generateBackupFileName(): String {
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "DreamStream_Backup_$dateStr.json"
    }

    fun writeToUri(context: Context, uri: Uri, jsonStr: String): Boolean {
        return try {
            val outputStream = context.contentResolver.openOutputStream(uri) ?: return false
            outputStream.use {
                it.write(jsonStr.toByteArray(Charsets.UTF_8))
                it.flush()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write backup to uri: $uri", e)
            false
        }
    }

    fun readFromUri(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            inputStream.use {
                it.bufferedReader(Charsets.UTF_8).readText()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read backup from uri: $uri", e)
            null
        }
    }

    fun writeToDownloads(context: Context, jsonStr: String): File? {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = File(downloadsDir, generateBackupFileName())
            FileOutputStream(file).use {
                it.write(jsonStr.toByteArray(Charsets.UTF_8))
                it.flush()
            }
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write backup to Downloads directory", e)
            null
        }
    }
}
