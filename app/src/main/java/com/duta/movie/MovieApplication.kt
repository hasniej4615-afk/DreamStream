package com.duta.movie

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class MovieApplication : Application(), ImageLoaderFactory {
    
    @Inject
    lateinit var imageLoader: Lazy<ImageLoader>

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        Log.i("!!!APP_START!!!", "DMStreaM Version ${com.duta.movie.BuildConfig.VERSION_NAME}")
        try {
            val cm = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            cm?.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    com.duta.movie.util.NetworkConfig.resetDohCircuitBreaker()
                }
            })
        } catch (_: Exception) {}
        GlobalScope.launch(Dispatchers.IO) {
            try {
                com.duta.movie.util.SubtitleExtractor.init(this@MovieApplication)
                com.duta.movie.data.remote.RepoSyncWorker.schedulePeriodicSync(this@MovieApplication)
            } catch (_: Exception) {}
        }
    }

    override fun newImageLoader(): ImageLoader = imageLoader.get()
}
