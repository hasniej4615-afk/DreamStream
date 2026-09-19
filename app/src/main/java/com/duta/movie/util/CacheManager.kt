package com.duta.movie.util

import android.content.Context
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import java.io.File
import java.text.DecimalFormat

object CacheManager {

    fun getCacheSize(context: Context): String {
        var size: Long = 0
        size += getFolderSize(context.cacheDir)
        size += getFolderSize(context.externalCacheDir)
        return formatSize(size)
    }

    private fun getFolderSize(file: File?): Long {
        if (file == null || !file.exists()) return 0
        var size: Long = 0
        if (file.isDirectory) {
            file.listFiles()?.forEach {
                size += getFolderSize(it)
            }
        } else {
            size = file.length()
        }
        return size
    }

    private fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }

    @OptIn(ExperimentalCoilApi::class)
    fun clearCache(context: Context, imageLoader: ImageLoader) {
        try {
            // Clear Coil's caches
            imageLoader.diskCache?.clear()
            imageLoader.memoryCache?.clear()

            // Clear app's cache directory contents
            context.cacheDir.listFiles()?.forEach { deleteRecursive(it) }
            context.externalCacheDir?.listFiles()?.forEach { deleteRecursive(it) }
        } catch (e: Exception) {
            Logger.e("CacheManager", "Error clearing cache", e)
        }
    }

    /**
     * Performs a smart cleanup of the cache directories.
     * It ensures the cache doesn't exceed a certain threshold by deleting oldest files first.
     */
    fun performSmartCleanup(context: Context) {
        // Target: Keep app cache under 300MB total (images + subs + temp)
        val maxCacheSize = 300 * 1024 * 1024L
        
        try {
            val totalSize = getFolderSize(context.cacheDir)
            if (totalSize > maxCacheSize) {
                // Collect all files in cache with their last modified timestamps
                val allFiles = mutableListOf<File>()
                collectFiles(context.cacheDir, allFiles)
                
                // Sort by last modified (oldest first)
                allFiles.sortBy { it.lastModified() }
                
                var currentSize = totalSize
                for (file in allFiles) {
                    if (currentSize <= maxCacheSize * 0.7) break // Clean until we reach 70% of max
                    val fileSize = file.length()
                    if (file.delete()) {
                        currentSize -= fileSize
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e("CacheManager", "Error in smart cleanup", e)
        }
    }

    private fun collectFiles(dir: File, fileList: MutableList<File>) {
        dir.listFiles()?.forEach {
            if (it.isDirectory) {
                collectFiles(it, fileList)
            } else {
                fileList.add(it)
            }
        }
    }

    private fun deleteRecursive(fileOrDirectory: File?) {
        if (fileOrDirectory == null || !fileOrDirectory.exists()) return
        if (fileOrDirectory.isDirectory) {
            fileOrDirectory.listFiles()?.forEach {
                deleteRecursive(it)
            }
        }
        fileOrDirectory.delete()
    }
}
