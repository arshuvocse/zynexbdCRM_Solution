package com.zynexbd.crmsolution.models

import com.google.gson.annotations.SerializedName

data class SubscriptionStatus(
    @SerializedName("adminId") val adminId: Int = 0,
    @SerializedName("adminName") val adminName: String = "",
    @SerializedName("adminUsername") val adminUsername: String = "",
    @SerializedName("adminPhone") val adminPhone: String? = null,
    @SerializedName("paymentDueDate") val paymentDueDate: String? = null,
    @SerializedName("daysRemaining") val daysRemaining: Int = 30,
    @SerializedName("isExpired") val isExpired: Boolean = false,
    @SerializedName("isWarningPeriod") val isWarningPeriod: Boolean = false,
    @SerializedName("statusText") val statusText: String = ""
)

data class UpdatePaymentDueDateRequest(
    val adminId: Int,
    val newDueDate: String
)

data class SubscriptionPlan(
    @SerializedName("planId") val planId: Int = 0,
    @SerializedName("planCode") val planCode: String = "",
    @SerializedName("tierName") val tierName: String = "Regular",
    @SerializedName("title") val title: String = "",
    @SerializedName("titleBn") val titleBn: String = "",
    @SerializedName("durationMonths") val durationMonths: Int = 1,
    @SerializedName("price") val price: Double = 0.0,
    @SerializedName("originalPrice") val originalPrice: Double = 0.0,
    @SerializedName("discountPercent") val discountPercent: Int = 0,
    @SerializedName("discountText") val discountText: String? = null,
    @SerializedName("badgeText") val badgeText: String? = null,
    @SerializedName("badgeTextBn") val badgeTextBn: String? = null,
    @SerializedName("featuresJson") val featuresJson: String = "[]",
    @SerializedName("isActive") val isActive: Boolean = true,
    @SerializedName("displayOrder") val displayOrder: Int = 0
) {
    fun getFeaturesList(): List<String> {
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
            com.google.gson.Gson().fromJson(featuresJson, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}

