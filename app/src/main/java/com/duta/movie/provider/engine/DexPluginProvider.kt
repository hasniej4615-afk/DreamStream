package com.duta.movie.provider.engine

import android.content.Context
import android.util.Log
import com.duta.movie.data.local.InstalledProviderEntity
import com.duta.movie.model.Video
import com.duta.movie.model.VideoServer
import com.duta.movie.provider.core.MediaProvider
import com.duta.movie.provider.model.ProviderMediaType
import dalvik.system.DexClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * CloudStream-compatible dynamic .dex plugin loader.
 * Allows advanced community developers to compile and distribute Kotlin/Java
 * providers packaged as .dex files hosted in Supabase Storage.
 */
class DexPluginProvider(
    private val context: Context,
    private val entity: InstalledProviderEntity
) : MediaProvider {

    companion object {
        private const val TAG = "DexPluginProvider"
    }

    override val id: String = entity.id
    override val name: String = entity.name
    override val displayName: String = entity.displayName
    override val version: Int = entity.version
    override val versionName: String = entity.versionName
    override val iconUrl: String = entity.iconUrl
    override val mediaType: ProviderMediaType = try {
        ProviderMediaType.valueOf(entity.mediaType)
    } catch (_: Exception) {
        ProviderMediaType.MULTI
    }
    override val isEnabled: Boolean = entity.isEnabled

    private var dynamicInstance: MediaProvider? = null

    init {
        loadDexInstance()
    }

    private fun loadDexInstance() {
        if (entity.pluginUrl.isBlank()) return
        try {
            val pluginFile = File(context.filesDir, "plugins/${entity.id}.dex")
            if (!pluginFile.exists()) return

            // Ensure read-only for Android 14+ W^X security compliance
            pluginFile.setReadOnly()

            val classLoader = DexClassLoader(
                pluginFile.absolutePath,
                context.codeCacheDir.absolutePath,
                null,
                context.classLoader
            )

            // Look for provider class matching id or standard name
            val candidateClassNames = listOf(
                entity.name.replace(" ", "") + "Provider",
                entity.id.substringAfterLast('.').replaceFirstChar { it.uppercase() } + "Provider",
                entity.name.replace(" ", ""),
                "MainPlugin"
            )
            var instance: Any? = null
            for (candidate in candidateClassNames) {
                try {
                    val loadedClass = classLoader.loadClass(candidate)
                    instance = loadedClass.getDeclaredConstructor().newInstance()
                    break
                } catch (_: ClassNotFoundException) {}
            }
            if (instance is MediaProvider) {
                dynamicInstance = instance
                Log.i(TAG, "Successfully dynamically loaded .dex plugin: ${entity.name}")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Could not initialize dynamic .dex plugin ${entity.name}: ${e.message}")
        }
    }

    override suspend fun search(query: String, page: Int): List<Video> = withContext(Dispatchers.IO) {
        if (!isEnabled) return@withContext emptyList()
        try {
            val list = dynamicInstance?.search(query, page) ?: emptyList()
            list.map {
                it.copy(
                    id = if (it.id.startsWith("${entity.id}_")) it.id else "${entity.id}_${it.id}"
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Dynamic search failed for $name: ${e.message}")
            emptyList()
        }
    }

    override suspend fun fetchSection(path: String, page: Int, count: Int): List<Video> = withContext(Dispatchers.IO) {
        if (!isEnabled) return@withContext emptyList()
        try {
            val list = dynamicInstance?.fetchSection(path, page, count) ?: emptyList()
            list.map {
                it.copy(
                    id = if (it.id.startsWith("${entity.id}_")) it.id else "${entity.id}_${it.id}"
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Dynamic fetchSection failed for $name: ${e.message}")
            emptyList()
        }
    }

    override suspend fun fetchVideoDetail(video: Video): Video? = withContext(Dispatchers.IO) {
        try {
            dynamicInstance?.fetchVideoDetail(video)
        } catch (e: Exception) {
            Log.w(TAG, "Dynamic fetchVideoDetail failed for $name: ${e.message}")
            null
        }
    }

    override suspend fun fetchServers(video: Video): List<VideoServer> = withContext(Dispatchers.IO) {
        try {
            dynamicInstance?.fetchServers(video) ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Dynamic fetchServers failed for $name: ${e.message}")
            emptyList()
        }
    }
}
