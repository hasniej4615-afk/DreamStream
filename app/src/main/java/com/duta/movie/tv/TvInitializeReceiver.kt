package com.duta.movie.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class TvInitializeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.d("TvInitializeReceiver", "Received intent: ${intent?.action}")
        
        when (intent?.action) {
            "android.media.tv.action.INITIALIZE_PROGRAMS",
            Intent.ACTION_BOOT_COMPLETED -> {
                val syncRequest = OneTimeWorkRequestBuilder<TvChannelSyncWorker>().build()
                WorkManager.getInstance(context).enqueue(syncRequest)
            }
            "android.media.tv.action.PREVIEW_PROGRAM_BROWSABLE_DISABLED" -> {
                val programId = intent.getLongExtra("android.media.tv.extra.PREVIEW_PROGRAM_ID", -1)
                Log.d("TvInitializeReceiver", "Preview program disabled: $programId")
            }
            "android.media.tv.action.WATCH_NEXT_PROGRAM_BROWSABLE_DISABLED" -> {
                val programId = intent.getLongExtra("android.media.tv.extra.WATCH_NEXT_PROGRAM_ID", -1)
                Log.d("TvInitializeReceiver", "Watch next program disabled: $programId")
            }
        }
    }
}
