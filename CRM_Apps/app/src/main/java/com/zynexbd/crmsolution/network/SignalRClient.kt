package com.zynexbd.crmsolution.network

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import com.microsoft.signalr.HubConnectionState
import com.zynexbd.crmsolution.BuildConfig
import com.zynexbd.crmsolution.models.LocationResponse
import com.zynexbd.crmsolution.models.NotificationItem
import com.zynexbd.crmsolution.utils.NotificationHelper
import com.zynexbd.crmsolution.utils.SessionManager

/**
 * Wraps the SignalR connection used for real-time live tracking
 * and instant push/heads-up notifications for Admin and User.
 */
class SignalRClient(private val context: Context) {

    companion object {
        private const val TAG = "SignalRClient"
    }

    private val session = SessionManager(context)
    private var hubConnection: HubConnection? = null

    fun connect(
        onLocationUpdated: ((LocationResponse) -> Unit)? = null,
        onNotificationReceived: ((NotificationItem) -> Unit)? = null,
        onStateChange: ((Boolean) -> Unit)? = null
    ) {
        val token = session.getToken() ?: return
        val hubUrl = "${session.getServerBaseUrl().trimEnd('/')}/hubs/location"
        com.zynexbd.crmsolution.utils.AppLogger.i(TAG, "Connecting SignalR Hub to: $hubUrl")

        val connection = HubConnectionBuilder.create(hubUrl)
            .withAccessTokenProvider(io.reactivex.rxjava3.core.Single.just(token))
            .build()

        if (onLocationUpdated != null) {
            connection.on("LocationUpdated", { payload ->
                onLocationUpdated(payload)
            }, LocationResponse::class.java)
        }

        connection.on("ReceiveNotification", { notif ->
            try {
                com.zynexbd.crmsolution.utils.AppLogger.d(TAG, "SignalR notification received: ${notif.title} - ${notif.message}")
                NotificationHelper.sendNotification(
                    context = context,
                    title = notif.title,
                    message = notif.message,
                    notificationId = notif.notificationId,
                    uniqueDeduplicationKey = if (notif.notificationId > 0) "id_${notif.notificationId}" else null
                )
                onNotificationReceived?.invoke(notif)
            } catch (e: Exception) {
                com.zynexbd.crmsolution.utils.AppLogger.e(TAG, "Error handling ReceiveNotification", e)
            }
        }, NotificationItem::class.java)

        connection.on("ForceLogout", { reason ->
            try {
                com.zynexbd.crmsolution.utils.AppLogger.w(TAG, "SignalR ForceLogout received: $reason")
                val msg = if (!reason.isNullOrBlank()) reason else "অ্যাডমিন কর্তৃক আপনার সেশন বন্ধ করা হয়েছে। পুনরায় লগইন করুন।"
                NotificationHelper.sendNotification(
                    context = context,
                    title = "⚠️ সেশন সমাপ্ত হয়েছে",
                    message = msg
                )
                session.logout(context)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    val intent = android.content.Intent(context, com.zynexbd.crmsolution.activities.LoginActivity::class.java).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    context.startActivity(intent)
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                com.zynexbd.crmsolution.utils.AppLogger.e(TAG, "Error handling ForceLogout", e)
            }
        }, String::class.java)

        connection.onClosed { onStateChange?.invoke(false) }

        connection.start().subscribe(
            {
                com.zynexbd.crmsolution.utils.AppLogger.i(TAG, "SignalR connected successfully to $hubUrl")
                onStateChange?.invoke(true)
            },
            { error ->
                com.zynexbd.crmsolution.utils.AppLogger.w(TAG, "SignalR connection failed to $hubUrl: ${error.message}", error)
                onStateChange?.invoke(false)
            }
        )

        hubConnection = connection
    }

    fun isConnected(): Boolean = hubConnection?.connectionState == HubConnectionState.CONNECTED

    fun disconnect() {
        try {
            hubConnection?.stop()
        } catch (_: Exception) {}
        hubConnection = null
    }
}
