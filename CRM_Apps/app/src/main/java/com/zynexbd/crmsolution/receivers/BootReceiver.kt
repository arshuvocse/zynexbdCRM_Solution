package com.zynexbd.crmsolution.receivers

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.zynexbd.crmsolution.services.NotificationSyncWorker
import com.zynexbd.crmsolution.services.TrackingForegroundService
import com.zynexbd.crmsolution.utils.SessionManager

/**
 * Restarts the tracking foreground service and background notification sync
 * worker after device reboot.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val session = SessionManager(context)

        // Reschedule background notification sync
        if (session.isLoggedIn()) {
            NotificationSyncWorker.enqueuePeriodicSync(context)
        }

        // Restart tracking service if user role and location permission granted
        if (session.isLoggedIn() && session.isUser() && hasLocationPermission(context)) {
            val serviceIntent = Intent(context, TrackingForegroundService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }

    private fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }
}
