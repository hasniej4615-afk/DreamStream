package com.duta.movie.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object AvatarManager {
    private const val TAG = "AvatarManager"
    private const val AVATAR_FILE_NAME = "profile_avatar.jpg"
    private const val MAX_DIMENSION = 512

    fun getAvatarFile(context: Context): File {
        return File(context.filesDir, AVATAR_FILE_NAME)
    }

    fun saveAvatarFromUri(context: Context, sourceUri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (originalBitmap == null) return null

            val scaledBitmap = scaleBitmapToMaxDimension(originalBitmap, MAX_DIMENSION)
            val destinationFile = getAvatarFile(context)
            val outputStream = FileOutputStream(destinationFile)
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            outputStream.flush()
            outputStream.close()
            if (scaledBitmap != originalBitmap) {
                originalBitmap.recycle()
            }
            scaledBitmap.recycle()

            destinationFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save avatar from uri: $sourceUri", e)
            null
        }
    }

    fun deleteAvatar(context: Context): Boolean {
        return try {
            val file = getAvatarFile(context)
            if (file.exists()) file.delete() else true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete avatar", e)
            false
        }
    }

    fun getAvatarBase64(context: Context): String? {
        return try {
            val file = getAvatarFile(context)
            if (!file.exists()) return null
            val bytes = file.readBytes()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encode avatar to base64", e)
            null
        }
    }

    fun saveAvatarFromBase64(context: Context, base64Str: String): String? {
        return try {
            if (base64Str.isBlank()) return null
            val bytes = Base64.decode(base64Str, Base64.DEFAULT)
            val file = getAvatarFile(context)
            file.writeBytes(bytes)
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save avatar from base64", e)
            null
        }
    }

    private fun scaleBitmapToMaxDimension(bm: Bitmap, maxDim: Int): Bitmap {
        val width = bm.width
        val height = bm.height
        if (width <= maxDim && height <= maxDim) return bm
        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (ratio > 1) {
            newWidth = maxDim
            newHeight = (maxDim / ratio).toInt()
        } else {
            newHeight = maxDim
            newWidth = (maxDim * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(bm, maxOf(1, newWidth), maxOf(1, newHeight), true)
    }
}
