package com.duta.movie.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed interface DownloadState {
    object Idle : DownloadState
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : DownloadState
    data class ReadyToInstall(val file: File) : DownloadState
    data class Error(val message: String) : DownloadState
}

object UpdateDownloader {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun downloadApk(
        context: Context,
        apkUrl: String,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val updateDir = File(context.cacheDir, "updates")
            if (!updateDir.exists()) updateDir.mkdirs()
            val apkFile = File(updateDir, "DM-update.apk")
            if (apkFile.exists()) apkFile.delete()

            val request = Request.Builder()
                .url(apkUrl)
                .header("User-Agent", "Mozilla/5.0 (Android) DutaMovie/Updater")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }

                val body = response.body ?: return@withContext Result.failure(Exception("Empty response body"))
                val totalBytes = body.contentLength()
                var downloadedBytes = 0L

                body.byteStream().use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var lastReportTime = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            val now = System.currentTimeMillis()
                            if (now - lastReportTime > 100 || downloadedBytes == totalBytes) {
                                lastReportTime = now
                                val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else -1f
                                withContext(Dispatchers.Main) {
                                    onProgress(progress, downloadedBytes, totalBytes)
                                }
                            }
                        }
                        output.flush()
                    }
                }

                if (apkFile.length() == 0L) {
                    return@withContext Result.failure(Exception("Downloaded file is empty"))
                }
            }

            Result.success(apkFile)
        } catch (e: Exception) {
            Log.e("UpdateDownloader", "Download failed", e)
            Result.failure(e)
        }
    }

    fun installApk(context: Context, apkFile: File) {
        try {
            if (!apkFile.exists() || apkFile.length() == 0L) {
                Toast.makeText(context, "Update file not found", Toast.LENGTH_SHORT).show()
                return
            }

            // Check if app has permission to install unknown apps (Android 8.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    Toast.makeText(context, "Please allow installing updates for this app, then click Install again.", Toast.LENGTH_LONG).show()
                    return
                }
            }

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("UpdateDownloader", "Install failed", e)
            Toast.makeText(context, "Failed to launch installer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / (1024 * 1024))
            bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes.toDouble() / 1024)
            bytes > 0 -> "$bytes B"
            else -> "0 B"
        }
    }
}
