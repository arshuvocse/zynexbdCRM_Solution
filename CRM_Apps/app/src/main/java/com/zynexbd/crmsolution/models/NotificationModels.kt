package com.zynexbd.crmsolution.models

import com.google.gson.annotations.SerializedName

data class NotificationItem(
    @SerializedName("notificationId") val notificationId: Int = 0,
    @SerializedName("userId") val userId: Int? = null,
    @SerializedName("companyId") val companyId: Int? = null,
    @SerializedName("targetRole") val targetRole: String = "All",
    @SerializedName("title") val title: String = "",
    @SerializedName("message") val message: String = "",
    @SerializedName("type") val type: String = "General",
    @SerializedName("referenceId") val referenceId: String? = null,
    @SerializedName("isRead") val isRead: Boolean = false,
    @SerializedName("createdAtUtc") val createdAtUtc: String = ""
)

data class UnreadNotificationCount(
    @SerializedName("unreadCount") val unreadCount: Int = 0
)

data class SendNotificationRequest(
    @SerializedName("userId") val userId: Int? = null,
    @SerializedName("companyId") val companyId: Int? = null,
    @SerializedName("targetRole") val targetRole: String = "All",
    @SerializedName("title") val title: String,
    @SerializedName("message") val message: String,
    @SerializedName("type") val type: String = "General",
    @SerializedName("referenceId") val referenceId: String? = null
)
