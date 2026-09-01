package com.zynexbd.crmsolution.models

import com.google.gson.annotations.SerializedName

data class ExecutiveSummaryResponse(
    @SerializedName("totalUsers") val totalUsers: Int = 0,
    @SerializedName("activeUsers") val activeUsers: Int = 0,
    @SerializedName("onlineTrackingUsers") val onlineTrackingUsers: Int = 0,
    @SerializedName("todayPunchInCount") val todayPunchInCount: Int = 0,
    @SerializedName("todayAbsentCount") val todayAbsentCount: Int = 0,
    @SerializedName("todayLateCount") val todayLateCount: Int = 0,
    @SerializedName("pendingLeaveRequestsCount") val pendingLeaveRequestsCount: Int = 0,
    @SerializedName("pendingCustomerFollowUpsCount") val pendingCustomerFollowUpsCount: Int = 0,
    @SerializedName("gpsDisabledUsersCount") val gpsDisabledUsersCount: Int = 0,
    @SerializedName("attentionItems") val attentionItems: List<AttentionItem> = emptyList(),
    @SerializedName("systemHealth") val systemHealth: SystemHealthInfo? = null
)

data class AttentionItem(
    @SerializedName("id") val id: String = "",
    @SerializedName("type") val type: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("userId") val userId: Int? = null,
    @SerializedName("userName") val userName: String? = null,
    @SerializedName("severity") val severity: String = "Medium",
    @SerializedName("actionType") val actionType: String = "View",
    @SerializedName("timestamp") val timestamp: String = ""
)

data class SystemHealthInfo(
    @SerializedName("dbStatus") val dbStatus: String = "Healthy",
    @SerializedName("signalRStatus") val signalRStatus: String = "Connected",
    @SerializedName("activeSignalRConnections") val activeSignalRConnections: Int = 0,
    @SerializedName("lastCheckedAt") val lastCheckedAt: String = ""
)

data class BulkLeaveRequest(
    @SerializedName("applicationIds") val applicationIds: List<Int>,
    @SerializedName("comment") val comment: String? = null
)

data class BulkLeaveResponse(
    @SerializedName("approvedCount") val approvedCount: Int = 0,
    @SerializedName("failedCount") val failedCount: Int = 0,
    @SerializedName("approvedIds") val approvedIds: List<Int> = emptyList()
)
