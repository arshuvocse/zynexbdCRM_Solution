package com.zynexbd.crmsolution.models

import com.google.gson.annotations.SerializedName

data class AttendanceResponse(
    @SerializedName(value = "id", alternate = ["attendanceId"]) val id: Long = 0,
    val userId: Int = 0,
    @SerializedName(value = "userName", alternate = ["fullName", "username"]) val userName: String? = null,
    val type: String? = null, // "In" or "Out"
    @SerializedName(value = "timestamp", alternate = ["recordedAtUtc", "createdAt"]) val timestamp: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isWithinGeofence: Boolean = false,
    val selfieUrl: String? = null,
    val status: String? = "On Time",
    val shiftName: String? = "General Shift"
)

data class EmployeeMonthlyAttendanceSummary(
    val userId: Int = 0,
    val fullName: String = "",
    val username: String = "",
    val role: String = "",
    val shiftName: String = "General Shift",
    val year: Int = 0,
    val month: Int = 0,
    val totalWorkingDays: Int = 0,
    val presentDays: Int = 0,
    val onTimeDays: Int = 0,
    val lateDays: Int = 0,
    val earlyOutDays: Int = 0,
    val approvedLeaveDays: Int = 0,
    val absentDays: Int = 0,
    val attendancePercentage: Double = 0.0,
    val totalPresenceTime: String = "00:00"
)

data class TodayAttendanceStatusResponse(
    val hasPunchedIn: Boolean = false,
    val punchInTime: String? = null,
    val punchInStatus: String? = null,
    val punchInSelfieUrl: String? = null,

    val hasPunchedOut: Boolean = false,
    val punchOutTime: String? = null,
    val punchOutStatus: String? = null,
    val punchOutSelfieUrl: String? = null,

    val shiftName: String? = "General Shift"
)
