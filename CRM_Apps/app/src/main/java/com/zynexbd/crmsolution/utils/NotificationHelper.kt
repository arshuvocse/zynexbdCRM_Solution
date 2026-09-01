package com.zynexbd.crmsolution.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.zynexbd.crmsolution.R
import com.zynexbd.crmsolution.activities.AdminOverviewDashboardActivity
import com.zynexbd.crmsolution.activities.LoginActivity
import com.zynexbd.crmsolution.activities.UserHomeActivity
import java.util.Collections

object NotificationHelper {

    private const val TAG = "NotificationHelper"
    private const val CHANNEL_ID = "zynex_tracking_heads_up_alerts"
    private const val CHANNEL_NAME = "Smart Workforce Heads-Up Alerts"
    const val PREFS_NOTIF = "zynex_notif_prefs"
    const val KEY_SHOWN_IDS = "shown_notif_ids"

    private val inMemoryShownIds = Collections.synchronizedSet(LinkedHashSet<String>())

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority popup alerts and notifications for Admin and User"
                enableLights(true)
                lightColor = Color.BLUE
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 200, 250)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setSound(soundUri, null)
            }
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Checks whether a notification with the given key was already displayed,
     * and if not, atomically marks it as displayed.
     * Returns true if it was already displayed (duplicate), false if it is new.
     */
    @Synchronized
    fun checkAndMarkDuplicate(context: Context, uniqueKey: String): Boolean {
        if (uniqueKey.isBlank()) return false

        // 1. In-memory check
        if (inMemoryShownIds.contains(uniqueKey)) {
            return true
        }

        // 2. SharedPreferences check
        val prefs = context.getSharedPreferences(PREFS_NOTIF, Context.MODE_PRIVATE)
        val shownSet = prefs.getStringSet(KEY_SHOWN_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (shownSet.contains(uniqueKey)) {
            inMemoryShownIds.add(uniqueKey)
            return true
        }

        // 3. Mark as shown in both caches
        inMemoryShownIds.add(uniqueKey)
        shownSet.add(uniqueKey)

        // Prune if set grows too large (keep newest 1000 items)
        val toSave = if (shownSet.size > 1200) {
            shownSet.toList().takeLast(1000).toSet()
        } else {
            shownSet
        }
        prefs.edit().putStringSet(KEY_SHOWN_IDS, toSave).apply()

        return false
    }

    fun isAlreadyShown(context: Context, uniqueKey: String): Boolean {
        if (uniqueKey.isBlank()) return false
        if (inMemoryShownIds.contains(uniqueKey)) return true
        val prefs = context.getSharedPreferences(PREFS_NOTIF, Context.MODE_PRIVATE)
        val shownSet = prefs.getStringSet(KEY_SHOWN_IDS, emptySet()) ?: emptySet()
        return shownSet.contains(uniqueKey)
    }

    fun sendNotification(
        context: Context,
        title: String,
        message: String,
        notificationId: Int = 0,
        uniqueDeduplicationKey: String? = null
    ): Boolean {
        // Construct deterministic deduplication key
        val deduplicationKey = when {
            !uniqueDeduplicationKey.isNullOrBlank() -> uniqueDeduplicationKey
            notificationId > 0 -> "id_$notificationId"
            else -> "content_${title.trim()}_${message.trim()}"
        }

        // Deduplication gate: abort if already displayed
        if (checkAndMarkDuplicate(context, deduplicationKey)) {
            android.util.Log.d(TAG, "Suppressed duplicate notification: key=$deduplicationKey, title=$title")
            return false
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel(context)

        val session = SessionManager(context)
        val targetIntent = when {
            !session.isLoggedIn() -> Intent(context, LoginActivity::class.java)
            session.isAdmin() -> Intent(context, AdminOverviewDashboardActivity::class.java)
            else -> Intent(context, UserHomeActivity::class.java)
        }.apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val effectiveId = if (notificationId > 0) {
            notificationId
        } else {
            (Math.abs(deduplicationKey.hashCode()) % 90000) + 1000
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            effectiveId,
            targetIntent,
            flags
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pulse)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 250, 200, 250))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(effectiveId, notification)
        return true
    }
}
