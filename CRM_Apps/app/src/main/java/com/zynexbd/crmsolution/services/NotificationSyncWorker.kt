package com.zynexbd.crmsolution.services

import android.content.Context
import android.util.Log
import androidx.work.*
import com.zynexbd.crmsolution.network.RetrofitClient
import com.zynexbd.crmsolution.utils.NotificationHelper
import com.zynexbd.crmsolution.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Background WorkManager worker that periodically checks for new unread
 * notifications and pops them up as high-priority heads-up notifications,
 * even when the app is in background or completely closed.
 */
class NotificationSyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "NotifSyncWorker"
        private const val WORK_NAME = "zynex_notification_sync_work"
        private const val PREFS_NOTIF = "zynex_notif_prefs"
        private const val KEY_SHOWN_IDS = "shown_notif_ids"

        /**
         * Enqueues unique periodic background notification sync.
         */
        fun enqueuePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<NotificationSyncWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }

        /**
         * Triggers an immediate one-time sync check.
         */
        fun enqueueOneTimeSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<NotificationSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val session = SessionManager(context)
        if (!session.isLoggedIn()) {
            return@withContext Result.success()
        }

        try {
            val api = RetrofitClient.getApiService(context)
            val response = api.getNotifications(unreadOnly = true, take = 15, companyId = session.getCompanyId())

            if (response.isSuccessful && response.body() != null) {
                val notifications = response.body()!!
                // Filter out already seen notifications
                val unshownNotifications = notifications.filter { notif ->
                    val key = if (notif.notificationId > 0) "id_${notif.notificationId}" else "content_${notif.title.trim()}_${notif.message.trim()}"
                    !NotificationHelper.isAlreadyShown(context, key)
                }

                // Show only newly arrived unshown notifications (max 2 at a time)
                for (notif in unshownNotifications.take(2)) {
                    val key = if (notif.notificationId > 0) "id_${notif.notificationId}" else "content_${notif.title.trim()}_${notif.message.trim()}"
                    NotificationHelper.sendNotification(
                        context = context,
                        title = notif.title,
                        message = notif.message,
                        notificationId = notif.notificationId,
                        uniqueDeduplicationKey = key
                    )
                    // Permanently mark as read in database
                    if (notif.notificationId > 0) {
                        try { api.markNotificationAsRead(notif.notificationId) } catch (_: Exception) {}
                    }
                }

                // Mark remaining old items in local deduplication store so they never reappear
                for (notif in notifications) {
                    val key = if (notif.notificationId > 0) "id_${notif.notificationId}" else "content_${notif.title.trim()}_${notif.message.trim()}"
                    NotificationHelper.checkAndMarkDuplicate(context, key)
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Notification background sync error: ${e.message}")
            Result.retry()
        }
    }
}
