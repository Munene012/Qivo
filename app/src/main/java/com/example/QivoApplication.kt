package com.example

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.data.AppAnalyticsService
import com.example.data.AppForegroundTracker
import com.example.data.PesapalConfig
import com.example.data.SupabaseAuthService
import com.example.data.SupabaseConfig
import com.example.data.SupabaseHttpClient
import com.example.data.UserSessionManager
import com.example.ui.theme.AppThemeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class QivoApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        try {
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                Log.e("QivoApplication", "FATAL CRASH on thread ${thread.name}: ${throwable.message}", throwable)
                defaultHandler?.uncaughtException(thread, throwable)
            }
            UserSessionManager.init(this)
            AppForegroundTracker.init(this)
            SupabaseConfig.init(this)
            PesapalConfig.init(this)
            AppAnalyticsService.init(this)
            AppThemeManager.init(this)

            // Clear persistent caches so they won't be seen on cold boot without internet
            com.example.data.AppDataCacheManager.clearChatCacheOnStartup(this)

            // Proactive periodic token refresh to ensure uninterrupted session during active use
            CoroutineScope(Dispatchers.IO).launch {
                while (true) {
                    delay(4 * 60 * 1000L) // Check every 4 minutes
                    try {
                        if (AppForegroundTracker.isAppInForeground()) {
                            val session = UserSessionManager.getSession(applicationContext)
                            if (session != null && session.userId.isNotBlank()) {
                                if (UserSessionManager.isTokenExpired(applicationContext)) {
                                    Log.d("QivoApplication", "JWT approaching expiration, auto-refreshing in background...")
                                    SupabaseAuthService.refreshSessionSync(applicationContext, forceRefresh = true)
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Throwable) {
            Log.e("QivoApplication", "Error during application init: ${e.message}", e)
        }
    }

    /**
     * Singleton production-grade ImageLoader for Coil:
     * - 25% heap memory cache with strong reference retention
     * - 100MB disk cache in app cache directory
     * - Shares the global high-throughput OkHttpClient connection pool
     * - Enables hardware accelerated bitmaps on Android GPU
     * - Enables smooth crossfade animations to eliminate UI stutter
     */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15)
                    .strongReferencesEnabled(false)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(20L * 1024 * 1024) // Capped at 20MB to prevent uncontrolled cache growth
                    .build()
            }
            .okHttpClient {
                SupabaseHttpClient.client
            }
            .crossfade(true)
            .allowHardware(true)
            .build()
    }
}
