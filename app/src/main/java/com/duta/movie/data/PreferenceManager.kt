package com.duta.movie.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "movie_prefs")

@Singleton
class PreferenceManager @Inject constructor(@ApplicationContext private val context: Context) {

    companion object {
        private val MY_LIST_KEY = stringSetPreferencesKey("my_list")
        private val RECENTLY_WATCHED_KEY = stringPreferencesKey("recently_watched")
        private val DEFAULT_SUBTITLE_LANGUAGE_KEY = stringPreferencesKey("default_subtitle_lang")
        private val AUTO_SUBTITLE_ENABLED_KEY = booleanPreferencesKey("auto_subtitle_enabled")
        private val SEARCH_HISTORY_KEY = stringPreferencesKey("search_history")
        private val BASE_URL_KEY = stringPreferencesKey("base_url")
        private val PENCURI_BASE_URL_KEY = stringPreferencesKey("pencuri_base_url")
        private val BLACKLISTED_MIRRORS_KEY = stringSetPreferencesKey("blacklisted_mirrors")
        private val SOURCE_WEIGHTS_KEY = stringPreferencesKey("source_weights")
        private val SOURCE_PENALTIES_KEY = stringPreferencesKey("source_penalties")
        private val UI_SAFE_AREA_PADDING_KEY = intPreferencesKey("ui_safe_area_padding")
        private val UI_HERO_HEIGHT_OFFSET_KEY = intPreferencesKey("ui_hero_height_offset")
        private val UI_SCALE_FACTOR_KEY = floatPreferencesKey("ui_scale_factor")
        private val UI_THUMBNAIL_SCALE_FACTOR_KEY = floatPreferencesKey("ui_thumbnail_scale_factor")
        private val ENABLED_CATEGORIES_KEY = stringSetPreferencesKey("enabled_categories_v2")
        private val DISCOVERED_CATEGORIES_KEY = stringPreferencesKey("discovered_categories")
        private val FORCE_WEBVIEW_HOSTS_KEY = stringSetPreferencesKey("force_webview_hosts")
        private val HOST_PLAYER_FAILURES_KEY = stringPreferencesKey("host_player_failures")
        private val DEBUG_MODE_ENABLED_KEY = booleanPreferencesKey("debug_mode_enabled")
        private val MOBILE_LANDSCAPE_ENABLED_KEY = booleanPreferencesKey("mobile_landscape_enabled")
        private val IS_INSTALL_REGISTERED_KEY = booleanPreferencesKey("is_install_registered_v1")
        private val LAST_KNOWN_INSTALL_COUNT_KEY = intPreferencesKey("last_known_install_count")
        private val USER_NICKNAME_KEY = stringPreferencesKey("user_nickname")

        val DEFAULT_ENABLED_CATEGORIES = setOf(
            "/",
            "/movie/",
            "/serial-tv-terbaru/",
            "/country/malaysia/",
            "/category/p-ramlee/"
        ).map { com.duta.movie.util.VideoExtractor.normalizePath(it) }.toSet()
    }

    val activeBaseUrl: Flow<String> = context.dataStore.data.map { prefs ->
        val raw = prefs[BASE_URL_KEY] ?: "https://algarvebuzz.com"
        val low = raw.lowercase()
        val isPoisoned = low.contains("katherineschoolphone") ||
                low.contains("voe") ||
                low.contains("johnfullwonder") ||
                low.contains("rebeccasciencestreet") ||
                low.contains("cloudwindow") ||
                low.contains("player") ||
                low.contains("stream") ||
                low.contains("duta.media") ||
                low.contains("dutamovie21.now") ||
                low.contains("kepalabergetar") ||
                low.contains("pencuri") ||
                low.contains("archive.org") ||
                !low.startsWith("http")
        if (isPoisoned) "https://algarvebuzz.com" else {
            try {
                val uri = android.net.Uri.parse(raw)
                "${uri.scheme}://${uri.host}"
            } catch (_: Exception) { "https://algarvebuzz.com" }
        }
    }

    suspend fun setActiveBaseUrl(url: String) {
        val low = url.lowercase()
        if (low.contains("katherineschoolphone") || low.contains("voe") || low.contains("kepalabergetar") ||
            low.contains("pencuri") || low.contains("archive.org") || !low.startsWith("http")) return
        val cleanUrl = try {
            val uri = android.net.Uri.parse(url)
            "${uri.scheme}://${uri.host}"
        } catch (_: Exception) { url }
        context.dataStore.edit { it[BASE_URL_KEY] = cleanUrl }
    }

    val activePencuriBaseUrl: Flow<String> = context.dataStore.data.map { prefs ->
        val raw = prefs[PENCURI_BASE_URL_KEY] ?: "https://ww44.pencurimovie.baby"
        val low = raw.lowercase()
        if (low.contains("pencurimovie") || low.contains("pencurifilm") || low.contains("pencurivideo")) {
            try {
                val uri = android.net.Uri.parse(raw)
                "${uri.scheme}://${uri.host}"
            } catch (_: Exception) { "https://ww44.pencurimovie.baby" }
        } else {
            "https://ww44.pencurimovie.baby"
        }
    }

    suspend fun setActivePencuriBaseUrl(url: String) {
        val low = url.lowercase()
        if (low.contains("pencurimovie") || low.contains("pencurifilm") || low.contains("pencurivideo")) {
            val cleanUrl = try {
                val uri = android.net.Uri.parse(url)
                "${uri.scheme}://${uri.host}"
            } catch (_: Exception) { url }
            context.dataStore.edit { it[PENCURI_BASE_URL_KEY] = cleanUrl }
        }
    }

    suspend fun recordHostPlayerSuccess(host: String) {
        if (host.isBlank()) return
        val h = host.lowercase()
        context.dataStore.edit { preferences ->
            val raw = preferences[HOST_PLAYER_FAILURES_KEY] ?: ""
            val failures = raw.split("|").filter { it.contains(":") }.associate {
                val parts = it.split(":")
                parts[0] to (parts[1].toIntOrNull() ?: 0)
            }.toMutableMap()
            
            // On success, we don't just clear failures, we can also gradually remove from force-webview if it was there
            failures[h] = 0 
            preferences[HOST_PLAYER_FAILURES_KEY] = failures.entries.joinToString("|") { "${it.key}:${it.value}" }
        }
    }

    val enabledCategoryPaths: Flow<Set<String>> = context.dataStore.data
        .map { preferences ->
            val set = preferences[ENABLED_CATEGORIES_KEY] ?: DEFAULT_ENABLED_CATEGORIES
            set.map { com.duta.movie.util.VideoExtractor.normalizePath(it) }.toSet()
        }

    val discoveredCategories: Flow<List<Map<String, String>>> = context.dataStore.data.map { preferences ->
        val raw = preferences[DISCOVERED_CATEGORIES_KEY] ?: ""
        if (raw.isEmpty()) emptyList()
        else {
            raw.split("||").filter { it.contains("|") }.map {
                val parts = it.split("|")
                mapOf("name" to parts[0], "path" to com.duta.movie.util.VideoExtractor.normalizePath(parts[1]))
            }.distinctBy { it["path"] }
        }
    }

    suspend fun saveDiscoveredCategories(categories: List<Map<String, String>>) {
        context.dataStore.edit { preferences ->
            val serialized = categories.joinToString("||") { 
                val path = com.duta.movie.util.VideoExtractor.normalizePath(it["path"] ?: "")
                "${it["name"]}|$path" 
            }
            preferences[DISCOVERED_CATEGORIES_KEY] = serialized
        }
    }

    suspend fun toggleCategoryPath(path: String) {
        val normalized = com.duta.movie.util.VideoExtractor.normalizePath(path)
        context.dataStore.edit { preferences ->
            val currentRaw = preferences[ENABLED_CATEGORIES_KEY] ?: DEFAULT_ENABLED_CATEGORIES
            // Normalize all existing paths to ensure we don't have duplicates or mismatches
            val current = currentRaw.map { com.duta.movie.util.VideoExtractor.normalizePath(it) }.toSet()
            val newList = current.toMutableSet()
            if (newList.contains(normalized)) newList.remove(normalized) else newList.add(normalized)
            preferences[ENABLED_CATEGORIES_KEY] = newList
        }
    }

    suspend fun enableAllCategories(paths: Collection<String>) {
        val normalizedSet = paths.map { com.duta.movie.util.VideoExtractor.normalizePath(it) }.toSet()
        context.dataStore.edit { preferences ->
            preferences[ENABLED_CATEGORIES_KEY] = normalizedSet
        }
    }

    suspend fun disableAllCategories() {
        context.dataStore.edit { preferences ->
            // Keep at least root Newly Updated ("/") active so home feed is never empty
            preferences[ENABLED_CATEGORIES_KEY] = setOf("/")
        }
    }

    suspend fun resetCategoriesToDefault() {
        context.dataStore.edit { preferences ->
            preferences[ENABLED_CATEGORIES_KEY] = DEFAULT_ENABLED_CATEGORIES
        }
    }

    suspend fun resetDisplaySettings() {
        context.dataStore.edit { preferences ->
            preferences.remove(UI_SAFE_AREA_PADDING_KEY)
            preferences.remove(UI_HERO_HEIGHT_OFFSET_KEY)
            preferences.remove(UI_SCALE_FACTOR_KEY)
            preferences.remove(UI_THUMBNAIL_SCALE_FACTOR_KEY)
        }
    }

    val searchHistory: Flow<List<String>> = context.dataStore.data
        .map { it[SEARCH_HISTORY_KEY]?.split("|")?.filter { it.isNotEmpty() } ?: emptyList() }

    suspend fun addSearchQuery(query: String) {
        if (query.isBlank()) return
        context.dataStore.edit { preferences ->
            val currentRaw = preferences[SEARCH_HISTORY_KEY] ?: ""
            val currentList = currentRaw.split("|").filter { it.isNotEmpty() }.toMutableList()
            currentList.remove(query)
            currentList.add(0, query)
            preferences[SEARCH_HISTORY_KEY] = currentList.take(20).joinToString("|")
        }
    }

    suspend fun clearSearchHistory() {
        context.dataStore.edit { it.remove(SEARCH_HISTORY_KEY) }
    }


    suspend fun setSafeModeEnabled(enabled: Boolean) {
    }

    val isDebugModeEnabled: Flow<Boolean> = context.dataStore.data.map { it[DEBUG_MODE_ENABLED_KEY] ?: false }

    suspend fun setDebugModeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[DEBUG_MODE_ENABLED_KEY] = enabled }
    }

    val isMobileLandscapeEnabled: Flow<Boolean> = context.dataStore.data.map { it[MOBILE_LANDSCAPE_ENABLED_KEY] ?: false }

    suspend fun setMobileLandscapeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[MOBILE_LANDSCAPE_ENABLED_KEY] = enabled }
    }


    suspend fun setSafeModePin(pin: String) {
        val hashedPin = if (pin.length == 64) pin else hashPin(pin)
    }

    private fun hashPin(pin: String): String {
        return try {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val digest = md.digest(pin.toByteArray())
            digest.fold("") { str, it -> str + "%02x".format(it) }
        } catch (e: Exception) { pin }
    }

    fun verifyPin(input: String, stored: String): Boolean = 
        if (stored.length != 64) input == stored else hashPin(input) == stored

    val isAutoSubtitleEnabled: Flow<Boolean> = context.dataStore.data.map { it[AUTO_SUBTITLE_ENABLED_KEY] ?: false }

    suspend fun setAutoSubtitleEnabled(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_SUBTITLE_ENABLED_KEY] = enabled }
    }

    val defaultSubtitleLanguage: Flow<String> = context.dataStore.data.map { it[DEFAULT_SUBTITLE_LANGUAGE_KEY] ?: "English" }

    suspend fun setDefaultSubtitleLanguage(language: String) {
        context.dataStore.edit { it[DEFAULT_SUBTITLE_LANGUAGE_KEY] = language }
    }

    val myList: Flow<Set<String>> = context.dataStore.data.map { it[MY_LIST_KEY] ?: emptySet() }

    suspend fun toggleMyList(videoId: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[MY_LIST_KEY] ?: emptySet()
            val newList = current.toMutableSet()
            if (newList.contains(videoId)) newList.remove(videoId) else newList.add(videoId)
            preferences[MY_LIST_KEY] = newList
        }
    }

    val recentlyWatched: Flow<List<String>> = context.dataStore.data
        .map { it[RECENTLY_WATCHED_KEY]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList() }

    suspend fun addRecentlyWatched(videoId: String) {
        context.dataStore.edit { preferences ->
            val currentRaw = preferences[RECENTLY_WATCHED_KEY] ?: ""
            val currentList = currentRaw.split(",").filter { it.isNotEmpty() }.toMutableList()
            currentList.remove(videoId)
            currentList.add(0, videoId)
            preferences[RECENTLY_WATCHED_KEY] = currentList.take(50).joinToString(",")
        }
    }

    suspend fun clearRecentlyWatched() {
        context.dataStore.edit { preferences ->
            preferences.remove(RECENTLY_WATCHED_KEY)
            preferences.asMap().keys.forEach { key ->
                if (key.name.startsWith("progress_") || key.name.startsWith("duration_")) preferences.remove(key)
            }
        }
    }

    suspend fun saveVideoProgress(videoId: String, position: Long) {
        context.dataStore.edit { it[longPreferencesKey("progress_$videoId")] = position }
    }

    suspend fun saveVideoDuration(videoId: String, duration: Long) {
        context.dataStore.edit { it[longPreferencesKey("duration_$videoId")] = duration }
    }

    val blacklistedMirrors: Flow<Set<String>> = context.dataStore.data.map { it[BLACKLISTED_MIRRORS_KEY] ?: emptySet() }

    val sourceWeights: Flow<Map<String, Int>> = context.dataStore.data
        .map { preferences ->
            val raw = preferences[SOURCE_WEIGHTS_KEY] ?: ""
            raw.split("|").filter { it.contains(":") }.associate {
                val parts = it.split(":")
                parts[0] to (parts[1].toIntOrNull() ?: 0)
            }
        }

    suspend fun incrementSourceWeight(host: String) {
        if (host.isBlank()) return
        val h = host.lowercase()
        context.dataStore.edit { preferences ->
            val raw = preferences[SOURCE_WEIGHTS_KEY] ?: ""
            val weights = raw.split("|").filter { it.contains(":") }.associate {
                val parts = it.split(":")
                parts[0] to (parts[1].toIntOrNull() ?: 0)
            }.toMutableMap()
            val current = weights[h] ?: 0
            val newWeight = (current + 1).coerceAtMost(100)
            weights[h] = newWeight
            preferences[SOURCE_WEIGHTS_KEY] = weights.entries.joinToString("|") { "${it.key}:${it.value}" }
        }
    }

    suspend fun decrementSourceWeight(host: String) {
        if (host.isBlank()) return
        val h = host.lowercase()
        context.dataStore.edit { preferences ->
            val rawWeights = preferences[SOURCE_WEIGHTS_KEY] ?: ""
            val weights = rawWeights.split("|").filter { it.contains(":") }.associate {
                val parts = it.split(":")
                parts[0] to (parts[1].toIntOrNull() ?: 0)
            }.toMutableMap()
            val currentWeight = weights[h] ?: 0
            val newWeight = (currentWeight - 5).coerceAtLeast(0)
            weights[h] = newWeight
            
            val rawPenalties = preferences[SOURCE_PENALTIES_KEY] ?: ""
            val penalties = rawPenalties.split("|").filter { it.contains(":") }.associate {
                val parts = it.split(":")
                parts[0] to (parts[1].toIntOrNull() ?: 0)
            }.toMutableMap()
            val nextPenalties = (penalties[h] ?: 0) + 1
            penalties[h] = nextPenalties
            
            val isResilient = h.contains("zeus") || h.contains("klik") || h.contains("indostream") || h.contains("amt")
            if (newWeight == 0 && nextPenalties >= (if (isResilient) 30 else 10)) {
                val currentBlacklist = preferences[BLACKLISTED_MIRRORS_KEY] ?: emptySet()
                preferences[BLACKLISTED_MIRRORS_KEY] = currentBlacklist + h
            }
            preferences[SOURCE_WEIGHTS_KEY] = weights.entries.joinToString("|") { "${it.key}:${it.value}" }
            preferences[SOURCE_PENALTIES_KEY] = penalties.entries.joinToString("|") { "${it.key}:${it.value}" }
        }
    }

    val forceWebViewHosts: Flow<Set<String>> = context.dataStore.data.map { it[FORCE_WEBVIEW_HOSTS_KEY] ?: emptySet() }

    suspend fun addForceWebViewHost(host: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[FORCE_WEBVIEW_HOSTS_KEY] ?: emptySet()
            preferences[FORCE_WEBVIEW_HOSTS_KEY] = current + host.lowercase()
        }
    }

    suspend fun removeFromForceWebViewHosts(urlOrHost: String) {
        if (urlOrHost.isBlank()) return
        val host = try { android.net.Uri.parse(urlOrHost).host?.lowercase() ?: urlOrHost.lowercase() } catch(_: Exception) { urlOrHost.lowercase() }
        context.dataStore.edit { preferences ->
            val current = preferences[FORCE_WEBVIEW_HOSTS_KEY] ?: emptySet()
            if (current.contains(host)) {
                preferences[FORCE_WEBVIEW_HOSTS_KEY] = current - host
                android.util.Log.i("PreferenceManager", "Startup scrub: removed $host from forceWebViewHosts")
            }
        }
    }

    suspend fun recordHostPlayerFailure(host: String): Boolean {
        if (host.isBlank()) return false
        val h = host.lowercase()
        // Never add direct video stream hosts or internal chunk CDNs to forceWebViewHosts because Android WebView CANNOT play raw manifests or raw direct video streams!
        if (h.contains("morencius") || h.contains("bestcdn") || h.contains("faststream") || 
            h.contains("iplayerhls") || h.contains("indostream.lol") || h.contains("archive.org") ||
            h.contains("platformdocumentation") || h.contains("hgcloud") || h.contains("hglink") ||
            h.endsWith(".m3u8") || h.endsWith(".mp4") || h.contains(".txt") || h.contains("tapecontent")) {
            return false
        }
        var crossedThreshold = false
        context.dataStore.edit { preferences ->
            val raw = preferences[HOST_PLAYER_FAILURES_KEY] ?: ""
            val failures = raw.split("|").filter { it.contains(":") }.associate {
                val parts = it.split(":")
                parts[0] to (parts[1].toIntOrNull() ?: 0)
            }.toMutableMap()
            
            val prev = failures[h] ?: 0
            val next = prev + 1
            failures[h] = next
            
            if (next >= 3) {
                val currentForce = preferences[FORCE_WEBVIEW_HOSTS_KEY] ?: emptySet()
                preferences[FORCE_WEBVIEW_HOSTS_KEY] = currentForce + h
                Log.e("PreferenceManager", "Owl's Eye: Host $h failed 3 times. Added to Force WebView list.")
                crossedThreshold = (prev < 3) // only true the first time the threshold is crossed
            }
            preferences[HOST_PLAYER_FAILURES_KEY] = failures.entries.joinToString("|") { "${it.key}:${it.value}" }
        }
        return crossedThreshold
    }

    suspend fun dumpDiagnostics() {
        val weights = context.dataStore.data.map { it[SOURCE_WEIGHTS_KEY] ?: "" }.first()
        Log.d("PreferenceManager", "DIAGNOSTICS - Weights: $weights")
    }

    suspend fun clearBlacklistedMirrors() {
        context.dataStore.edit { 
            it.remove(BLACKLISTED_MIRRORS_KEY)
            it.remove(SOURCE_WEIGHTS_KEY)
            it.remove(SOURCE_PENALTIES_KEY)
            it.remove(FORCE_WEBVIEW_HOSTS_KEY)
            it.remove(HOST_PLAYER_FAILURES_KEY)
        }
    }

    suspend fun addBlacklistedMirror(host: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[BLACKLISTED_MIRRORS_KEY] ?: emptySet()
            preferences[BLACKLISTED_MIRRORS_KEY] = current + host.lowercase()
        }
    }

    private fun isTV(): Boolean = com.duta.movie.util.DeviceUtils.isTvDevice(context)

    fun getVideoProgress(videoId: String): Flow<Long> = context.dataStore.data.map { it[longPreferencesKey("progress_$videoId")] ?: 0L }
    fun getVideoDuration(videoId: String): Flow<Long> = context.dataStore.data.map { it[longPreferencesKey("duration_$videoId")] ?: 0L }
    fun getWatchedEpisodes(videoId: String): Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs.asMap().keys
            .map { it.name }
            .filter { it.startsWith("duration_${videoId}_") }
            .map { it.removePrefix("duration_") }
            .toSet()
    }

    val uiSafeAreaPadding: Flow<Int> = context.dataStore.data.map { it[UI_SAFE_AREA_PADDING_KEY] ?: 0 }
    suspend fun setUiSafeAreaPadding(padding: Int) { context.dataStore.edit { it[UI_SAFE_AREA_PADDING_KEY] = padding } }
    val uiHeroHeightOffset: Flow<Int> = context.dataStore.data.map { it[UI_HERO_HEIGHT_OFFSET_KEY] ?: 0 }
    suspend fun setUiHeroHeightOffset(offset: Int) { context.dataStore.edit { it[UI_HERO_HEIGHT_OFFSET_KEY] = offset } }
    val uiScaleFactor: Flow<Float> = context.dataStore.data.map { it[UI_SCALE_FACTOR_KEY] ?: 1.0f }
    suspend fun setUiScaleFactor(scale: Float) { context.dataStore.edit { it[UI_SCALE_FACTOR_KEY] = scale } }
    val uiThumbnailScaleFactor: Flow<Float> = context.dataStore.data.map { it[UI_THUMBNAIL_SCALE_FACTOR_KEY] ?: if (isTV()) 0.60f else 1.0f }
    suspend fun setUiThumbnailScaleFactor(scale: Float) { context.dataStore.edit { it[UI_THUMBNAIL_SCALE_FACTOR_KEY] = scale } }

    val isInstallRegistered: Flow<Boolean> = context.dataStore.data.map { it[IS_INSTALL_REGISTERED_KEY] ?: false }
    suspend fun setInstallRegistered(registered: Boolean) { context.dataStore.edit { it[IS_INSTALL_REGISTERED_KEY] = registered } }

    val lastKnownInstallCount: Flow<Int> = context.dataStore.data.map { it[LAST_KNOWN_INSTALL_COUNT_KEY] ?: 0 }
    suspend fun setLastKnownInstallCount(count: Int) { context.dataStore.edit { it[LAST_KNOWN_INSTALL_COUNT_KEY] = count } }

    val userNickname: Flow<String> = context.dataStore.data.map { it[USER_NICKNAME_KEY] ?: "" }
    suspend fun setUserNickname(nickname: String) { context.dataStore.edit { it[USER_NICKNAME_KEY] = nickname.trim() } }
}
