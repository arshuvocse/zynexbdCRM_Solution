package com.zynexbd.crmsolution.models

import com.google.gson.annotations.SerializedName

data class LeaveType(
    @SerializedName(value = "id", alternate = ["leaveTypeId"]) val id: Int = 0,
    val name: String = "",
    val defaultDaysPerYear: Int = 0,
    val isActive: Boolean = true
)

data class LeaveBalance(
    @SerializedName(value = "leaveTypeId", alternate = ["id"]) val leaveTypeId: Int = 0,
    @SerializedName(value = "leaveTypeName", alternate = ["name"]) val leaveTypeName: String = "",
    val totalDays: Int = 0,
    val usedDays: Int = 0,
    val remainingDays: Int = 0
)

data class ApplyLeaveRequest(
    val leaveTypeId: Int,
    val startDate: String, // yyyy-MM-dd
    val endDate: String,   // yyyy-MM-dd
    val reason: String
)

data class LeaveReviewRequest(val comment: String?)

data class LeaveApplicationResponse(
    @SerializedName(value = "id", alternate = ["leaveApplicationId"]) val id: Int = 0,
    val userId: Int = 0,
    @SerializedName(value = "userName", alternate = ["fullName", "username"]) val userName: String? = null,
    val leaveTypeId: Int = 0,
    @SerializedName(value = "leaveTypeName", alternate = ["name"]) val leaveTypeName: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val totalDays: Int = 0,
    val reason: String? = null,
    val status: String? = null, // Pending/Approved/Rejected/Cancelled
    val appliedAt: String? = null,
    val reviewedAt: String? = null,
    val reviewComment: String? = null
)
