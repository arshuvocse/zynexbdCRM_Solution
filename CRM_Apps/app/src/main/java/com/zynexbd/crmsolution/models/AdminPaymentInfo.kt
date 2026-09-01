package com.zynexbd.crmsolution.models

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class AdminPaymentInfo(
    val adminId: Int,
    val adminName: String,
    val adminUsername: String,
    val adminPhone: String? = null,
    val paymentDueDate: String? = null,
    val daysRemaining: Int = 30,
    val isExpired: Boolean = false,
    val isWarningPeriod: Boolean = false,
    val statusText: String = ""
) {
    val formattedDueDate: String?
        get() {
            val raw = paymentDueDate ?: return null
            return try {
                val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val date = parser.parse(raw) ?: return raw.take(10)
                SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(date)
            } catch (e: Exception) {
                raw.take(10)
            }
        }
}
