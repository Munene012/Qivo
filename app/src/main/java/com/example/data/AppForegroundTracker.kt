package com.example.data

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tracks whether the QIVO app is actively in the foreground (user inside the app)
 * or in the background (user outside the app).
 * 
 * Used to conditionally route notifications:
 * - When in foreground (inside app) -> Show only the top in-app notification popup
 * - When in background (outside app) -> Show the system status bar notification
 */
object AppForegroundTracker {
    private val activeActivityCount = AtomicInteger(0)
    private val _isForeground = MutableStateFlow(false)
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    fun init(application: Application) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

            override fun onActivityStarted(activity: Activity) {
                if (activeActivityCount.incrementAndGet() > 0) {
                    _isForeground.value = true
                }
            }

            override fun onActivityResumed(activity: Activity) {
                _isForeground.value = true
            }

            override fun onActivityPaused(activity: Activity) {}

            override fun onActivityStopped(activity: Activity) {
                if (activeActivityCount.decrementAndGet() <= 0) {
                    activeActivityCount.set(0)
                    _isForeground.value = false
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    fun isAppInForeground(): Boolean {
        return _isForeground.value || activeActivityCount.get() > 0
    }
}
