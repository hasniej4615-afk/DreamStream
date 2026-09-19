package com.duta.movie.tv

import android.content.Context
import android.util.Log
import androidx.tvprovider.media.tv.TvContractCompat

object TvChannelCleaner {
    fun deleteAllChannels(context: Context) {
        try {
            val cursor = context.contentResolver.query(
                TvContractCompat.Channels.CONTENT_URI,
                arrayOf(TvContractCompat.Channels._ID),
                null, null, null
            )
            var count = 0
            cursor?.use {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    val uri = TvContractCompat.buildChannelUri(id)
                    context.contentResolver.delete(uri, null, null)
                    count++
                }
            }
            Log.i("TvChannelCleaner", "Deleted $count old channels")
        } catch (e: Exception) {
            Log.e("TvChannelCleaner", "Failed to delete channels", e)
        }
    }
}
