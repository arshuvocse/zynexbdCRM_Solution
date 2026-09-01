package com.zynexbd.crmsolution.repository

import android.content.Context
import com.zynexbd.crmsolution.helpers.LocationOfflineQueue
import com.zynexbd.crmsolution.models.LocationPingRequest
import com.zynexbd.crmsolution.network.ApiClient
import com.zynexbd.crmsolution.utils.Constants

/**
 * Sends location pings to the API. On failure (no connectivity, 5xx, etc.)
 * the ping is persisted to the offline queue and retried on the next tick.
 * flushQueue() is called before each new ping to drain any backlog.
 */
class LocationRepository(context: Context) {

    private val api = ApiClient.getApiService(context)
    private val queue = LocationOfflineQueue(context)

    suspend fun sendPing(ping: LocationPingRequest) {
        flushQueue()
        val sent = trySend(ping)
        if (!sent) {
            queue.enqueue(ping)
        }
    }

    private suspend fun trySend(ping: LocationPingRequest): Boolean {
        return try {
            val resp = api.sendLocationPing(ping)
            if (resp.isSuccessful) {
                android.util.Log.d("LocationRepo", "Location ping uploaded successfully: Lat=${ping.latitude}, Lng=${ping.longitude}")
                true
            } else {
                android.util.Log.e("LocationRepo", "Location ping failed: HTTP ${resp.code()} - ${resp.errorBody()?.string()}")
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("LocationRepo", "Location ping network error: ${e.message}", e)
            false
        }
    }

    suspend fun flushQueue() {
        val batch = queue.peekBatch()
        for (item in batch) {
            val ok = trySend(item.ping)
            if (ok) {
                queue.remove(item.id)
            } else {
                queue.incrementAttempts(item.id)
                queue.dropIfExceeded(item.id, Constants.MAX_RETRY_ATTEMPTS)
            }
        }
    }
}
