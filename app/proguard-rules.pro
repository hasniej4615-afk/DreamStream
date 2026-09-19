# Jsoup
-keep class org.jsoup.** { *; }

# OkHttp
-keepattributes Signature
-keepattributes AnnotationDefault
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep class com.duta.movie.data.local.** { *; }
-keep class androidx.room.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class com.duta.movie.** { *; }
-keep interface com.duta.movie.** { *; }
-keep class * extends androidx.lifecycle.ViewModel
-keep class com.duta.movie.ui.VideoViewModel { *; }
-keep class com.duta.movie.MovieApplication { *; }
-keep class * implements hilt.internal.GeneratedComponent
-keep class * implements dagger.hilt.internal.GeneratedComponent
-keep class * implements dagger.hilt.internal.GeneratedComponentManager
-keep class * implements dagger.hilt.internal.UnsafeCasts
-keep class dagger.hilt.android.internal.managers.** { *; }
-keep class dagger.hilt.android.internal.modules.** { *; }
-keep class dagger.hilt.android.internal.lifecycle.** { *; }
-keep class com.duta.movie.Hilt_* { *; }
-keep class com.duta.movie.*_HiltModules* { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class *
-keep @dagger.hilt.EntryPoints class *

# Kotlin Serialization
-keepattributes *Annotation*, EnclosingMethod, InnerClasses, Signature
-keepclassmembers class ** {
    @kotlinx.serialization.SerialName <fields>;
}
-keep class kotlinx.serialization.json.** { *; }
-keep,allowobfuscation,allowoptimization class com.duta.movie.model.** { *; }
-keep class com.duta.movie.model.** { *; }
-keep interface kotlinx.serialization.KSerializer { *; }
-keep class kotlinx.serialization.internal.** { *; }

# Media3 & Native Libraries
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keep class com.google.android.exoplayer2.** { *; }
-dontwarn com.google.android.exoplayer2.**

# Google Cast & Services
-keep class com.google.android.gms.cast.framework.** { *; }
-dontwarn com.google.android.gms.cast.framework.**
-keep class com.google.android.gms.common.** { *; }
-dontwarn com.google.android.gms.common.**
-keep class com.google.android.gms.dynamic.** { *; }
-keep class com.google.android.gms.internal.** { *; }

# Room & SQLite
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep class com.duta.movie.data.local.** { *; }
-keep class androidx.room.** { *; }
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# Javascript Interface (Crucial for Smart Player)
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}
-dontwarn kotlinx.coroutines.**

# Android TV specific
-keep class androidx.leanback.** { *; }
-dontwarn androidx.leanback.**

# Android support and lifecycle
-keep class androidx.lifecycle.** { *; }
-dontwarn androidx.lifecycle.**
-keep class androidx.activity.** { *; }
-keep class androidx.fragment.** { *; }
-keep class androidx.core.** { *; }
-dontwarn androidx.core.**

# Coil
-keep class coil.** { *; }
-dontwarn coil.**

# Media3
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Google Cast
-keep class com.google.android.gms.cast.framework.** { *; }
-dontwarn com.google.android.gms.cast.framework.**

# Javascript Interface (Crucial for Smart Player)
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}
-dontwarn kotlinx.coroutines.**

# Android TV specific
-keep class androidx.leanback.** { *; }
-dontwarn androidx.leanback.**

# Android support and lifecycle
-keep class androidx.lifecycle.** { *; }
-dontwarn androidx.lifecycle.**
-keep class androidx.activity.** { *; }
-keep class androidx.fragment.** { *; }

# Coil
-keep class coil.** { *; }
-dontwarn coil.**

# WorkManager
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.InputMerger { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
-dontwarn androidx.work.**
