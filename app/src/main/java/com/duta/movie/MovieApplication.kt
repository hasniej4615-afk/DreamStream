package com.duta.movie

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class MovieApplication : Application(), ImageLoaderFactory {
    
    @Inject
    lateinit var imageLoader: ImageLoader

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        com.duta.movie.util.SubtitleExtractor.init(this)
        Log.i("!!!APP_START!!!", "DMStreaM Version 2.0.4 - Raven's Revenge")
        try {
            val cm = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            cm?.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    com.duta.movie.util.NetworkConfig.resetDohCircuitBreaker()
                }
            })
        } catch (_: Exception) {}

        // Pre-warm Android System WebView to avoid main thread freeze during player launch
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                android.webkit.WebView(this).destroy()
                Log.i("MovieApplication", "WebView pre-warmed successfully")
            } catch (e: Exception) {
                Log.w("MovieApplication", "WebView pre-warm skipped: ${e.message}")
            }
        }, 1500)
    }

    override fun newImageLoader(): ImageLoader = imageLoader
}
