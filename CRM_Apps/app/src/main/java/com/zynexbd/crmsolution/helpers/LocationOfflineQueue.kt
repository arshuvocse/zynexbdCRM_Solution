package com.zynexbd.crmsolution.helpers

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.zynexbd.crmsolution.models.LocationPingRequest
import com.zynexbd.crmsolution.utils.Constants

/**
 * Small SQLite-backed FIFO queue that persists location pings which
 * failed to upload (no connectivity / server error). The foreground
 * service flushes this queue on every tick before sending a fresh ping.
 */
class LocationOfflineQueue(context: Context) :
    SQLiteOpenHelper(context.applicationContext, Constants.QUEUE_DB_NAME, null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS queued_pings (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                accuracy REAL,
                speed REAL,
                bearing REAL,
                recorded_at_utc TEXT NOT NULL,
                attempts INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS queued_pings")
        onCreate(db)
    }

    fun enqueue(ping: LocationPingRequest) {
        val values = ContentValues().apply {
            put("latitude", ping.latitude)
            put("longitude", ping.longitude)
            put("accuracy", ping.accuracy)
            put("speed", ping.speed)
            put("bearing", ping.bearing)
            put("recorded_at_utc", ping.recordedAt)
        }
        writableDatabase.insert("queued_pings", null, values)
    }

    data class QueuedPing(val id: Long, val ping: LocationPingRequest, val attempts: Int)

    fun peekBatch(limit: Int = 20): List<QueuedPing> {
        val result = mutableListOf<QueuedPing>()
        readableDatabase.rawQuery(
            "SELECT id, latitude, longitude, accuracy, speed, bearing, recorded_at_utc, attempts " +
                "FROM queued_pings ORDER BY id ASC LIMIT ?",
            arrayOf(limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(
                    QueuedPing(
                        id = cursor.getLong(0),
                        ping = LocationPingRequest(
                            latitude = cursor.getDouble(1),
                            longitude = cursor.getDouble(2),
                            accuracy = if (cursor.isNull(3)) null else cursor.getDouble(3),
                            speed = if (cursor.isNull(4)) null else cursor.getDouble(4),
                            bearing = if (cursor.isNull(5)) null else cursor.getDouble(5),
                            recordedAt = cursor.getString(6)
                        ),
                        attempts = cursor.getInt(7)
                    )
                )
            }
        }
        return result
    }

    fun remove(id: Long) {
        writableDatabase.delete("queued_pings", "id = ?", arrayOf(id.toString()))
    }

    fun incrementAttempts(id: Long) {
        writableDatabase.execSQL("UPDATE queued_pings SET attempts = attempts + 1 WHERE id = ?", arrayOf(id))
    }

    fun dropIfExceeded(id: Long, maxAttempts: Int) {
        writableDatabase.delete("queued_pings", "id = ? AND attempts >= ?", arrayOf(id.toString(), maxAttempts.toString()))
    }
}
