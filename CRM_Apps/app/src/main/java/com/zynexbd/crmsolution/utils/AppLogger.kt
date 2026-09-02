package com.zynexbd.crmsolution.utils

import android.util.Log

/**
 * Centralized logging utility for CRM Solution app.
 * Provides distinct emoji prefixes and structured Logcat output for easy filtering in Android Studio / ADB.
 */
object AppLogger {
    private const val TAG_PREFIX = "CRM_"

    fun d(tag: String, message: String) {
        Log.d(formatTag(tag), "🔍 $message")
    }

    fun i(tag: String, message: String) {
        Log.i(formatTag(tag), "ℹ️ $message")
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w(formatTag(tag), "⚠️ $message", throwable)
        } else {
            Log.w(formatTag(tag), "⚠️ $message")
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(formatTag(tag), "❌ $message", throwable)
        } else {
            Log.e(formatTag(tag), "❌ $message")
        }
    }

    fun v(tag: String, message: String) {
        Log.v(formatTag(tag), "💬 $message")
    }

    private fun formatTag(tag: String): String {
        return if (tag.startsWith(TAG_PREFIX)) tag else "$TAG_PREFIX$tag"
    }
}
