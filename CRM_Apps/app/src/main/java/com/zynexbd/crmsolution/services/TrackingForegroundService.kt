package com.zynexbd.crmsolution.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.zynexbd.crmsolution.R
import com.zynexbd.crmsolution.activities.UserHomeActivity
import com.zynexbd.crmsolution.models.LocationPingRequest
import com.zynexbd.crmsolution.repository.LocationRepository
import com.zynexbd.crmsolution.utils.AddressHelper
import com.zynexbd.crmsolution.utils.Constants
import com.zynexbd.crmsolution.utils.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Runs for the lifetime of a logged-in User session. Started automatically
 * right after a successful login (see LoginActivity) and restarted on
 * device boot by BootReceiver if the session is still valid. Captures a
 * location fix and uploads it every LOCATION_UPDATE_INTERVAL_MS (60s),
 * queuing failed uploads for retry via LocationRepository.
 */
class TrackingForegroundService : Service() {

    companion object {
        private const val TAG = "TrackingFgService"
    }

    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var repository: LocationRepository
    private lateinit var session: SessionManager
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var locationCallback: LocationCallback? = null

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        repository = LocationRepository(applicationContext)
        session = SessionManager(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "TrackingForegroundService is temporarily disabled.")
        stopSelf()
        return START_NOT_STICKY
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    private fun startLocationUpdates() {
        if (!session.isLoggedIn() || !session.isUser()) {
            Log.w(TAG, "Not a logged-in standard user (Role: ${session.getRole()}); stopping tracking service.")
            stopSelf()
            return
        }

        // 1. Immediately fetch and upload last known location if available
        try {
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    Log.d(TAG, "Initial last known location acquired: ${loc.latitude}, ${loc.longitude}")
                    uploadLocation(loc)
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException fetching lastLocation", e)
        }

        val request = LocationRequest.Builder(Constants.LOCATION_UPDATE_INTERVAL_MS)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMinUpdateIntervalMillis(Constants.LOCATION_UPDATE_INTERVAL_MS / 2)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                Log.d(TAG, "GPS tick received: ${location.latitude}, ${location.longitude}")
                uploadLocation(location)
            }
        }

        try {
            fusedClient.requestLocationUpdates(request, locationCallback!!, mainLooper)
            Log.i(TAG, "Location updates successfully requested every ${Constants.LOCATION_UPDATE_INTERVAL_MS / 1000}s.")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException requesting location updates", e)
            stopSelf()
        }
    }

    private fun uploadLocation(location: Location) {
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val recordedTime = isoFormat.format(Date())

        serviceScope.launch(Dispatchers.IO) {
            val addressText = AddressHelper.resolveSpecificAddress(
                applicationContext,
                location.latitude,
                location.longitude
            )

            val ping = LocationPingRequest(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = if (location.hasAccuracy()) location.accuracy.toDouble() else null,
                speed = if (location.hasSpeed()) location.speed.toDouble() else null,
                bearing = if (location.hasBearing()) location.bearing.toDouble() else null,
                recordedAt = recordedTime,
                locationAddress = addressText
            )
            repository.sendPing(ping)
        }
    }

    private fun buildNotification() =
        NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Smart Workforce active")
            .setContentText("Duty tracking is in progress.")
            .setSmallIcon(R.drawable.ic_check_circle)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, UserHomeActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                "Smart Workforce",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
