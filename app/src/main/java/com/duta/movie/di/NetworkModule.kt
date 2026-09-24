package com.duta.movie.di

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.duta.movie.util.NetworkConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import android.graphics.Bitmap

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return NetworkConfig.okHttpClient
    }

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context
    ): ImageLoader {
        val isLowRam = com.duta.movie.util.VideoUtils.isLowRamDevice(context)
        val isTv = com.duta.movie.util.DeviceUtils.isTvDevice(context)
        val imageDispatcher = Dispatchers.IO.limitedParallelism(if (isLowRam) 6 else 16)

        return ImageLoader.Builder(context)
            .okHttpClient { NetworkConfig.imageOkHttpClient }
            .dispatcher(imageDispatcher)
            .bitmapConfig(if (isLowRam && !isTv) Bitmap.Config.RGB_565 else Bitmap.Config.HARDWARE)
            .allowRgb565(true)
            .allowHardware(true)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(if (isLowRam) 0.15 else 0.25) 
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(if (isLowRam) 256 * 1024 * 1024L else 512 * 1024 * 1024L) 
                    .build()
            }
            .components {
                add(coil.decode.GifDecoder.Factory())
                add(coil.decode.SvgDecoder.Factory())
            }
            .respectCacheHeaders(false) 
            .crossfade(false)
            .build()
    }
}
