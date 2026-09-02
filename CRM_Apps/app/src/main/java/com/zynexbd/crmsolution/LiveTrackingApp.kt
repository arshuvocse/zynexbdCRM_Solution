package com.zynexbd.crmsolution

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.zynexbd.crmsolution.services.NotificationSyncWorker
import com.zynexbd.crmsolution.utils.NotificationHelper

class LiveTrackingApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Force Light Mode so Dark Mode system settings do not alter the UI design
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        // Initialize high priority notification channel for heads-up alerts
        NotificationHelper.createNotificationChannel(this)

        // Initialize background notification sync worker
        NotificationSyncWorker.enqueuePeriodicSync(this)

        // Initialize Global Uncaught Exception Handler to capture all crashes in Logcat
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            com.zynexbd.crmsolution.utils.AppLogger.e("CRASH_HANDLER", "CRITICAL UNCAUGHT EXCEPTION in thread '${thread.name}': ${throwable.message}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        com.zynexbd.crmsolution.utils.AppLogger.i("LiveTrackingApp", "Application initialized successfully. Base URL: ${com.zynexbd.crmsolution.utils.SessionManager(this).getServerBaseUrl()}")

        // Pre-warm Google Maps SDK Renderer in background so maps open instantly without lag
        Thread {
            try {
                com.google.android.gms.maps.MapsInitializer.initialize(
                    this,
                    com.google.android.gms.maps.MapsInitializer.Renderer.LATEST,
                    null
                )
            } catch (e: Exception) {
                com.zynexbd.crmsolution.utils.AppLogger.w("LiveTrackingApp", "MapsInitializer pre-warm notice: ${e.message}")
            }
        }.start()
    }
}
