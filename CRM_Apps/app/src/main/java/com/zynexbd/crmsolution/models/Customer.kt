package com.zynexbd.crmsolution.models

import com.google.gson.annotations.SerializedName

data class Customer(
    @SerializedName("customerId") val customerId: Int = 0,
    @SerializedName("name") val name: String,
    @SerializedName("mobile") val mobile: String,
    @SerializedName("email") val email: String? = null,
    @SerializedName("address") val address: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("remarks") val remarks: String? = null,
    @SerializedName("createdDate") val createdDate: String? = null,
    @SerializedName("isActive") val isActive: Boolean = true,
    @SerializedName("createdByUserId") val createdByUserId: Int? = null,
    @SerializedName("createdByUserName") val createdByUserName: String? = null,
    @SerializedName("lastVisitDate") val lastVisitDate: String? = null,
    @SerializedName("nextFollowUpDate") val nextFollowUpDate: String? = null
)

data class CreateCustomerRequest(
    @SerializedName("name") val name: String,
    @SerializedName("mobile") val mobile: String,
    @SerializedName("email") val email: String? = null,
    @SerializedName("address") val address: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("remarks") val remarks: String? = null
)

data class RecordVisitRequest(
    @SerializedName("customerId") val customerId: Int,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("remarks") val remarks: String? = null,
    @SerializedName("visitStatus") val visitStatus: String = "Completed",
    @SerializedName("nextFollowUpDate") val nextFollowUpDate: String? = null,
    @SerializedName("shopPhotoBase64") val shopPhotoBase64: String? = null
)

data class CustomerVisit(
    @SerializedName("visitId") val visitId: Long = 0,
    @SerializedName("customerId") val customerId: Int,
    @SerializedName("customerName") val customerName: String = "",
    @SerializedName("mobile") val mobile: String = "",
    @SerializedName("address") val address: String = "",
    @SerializedName("userId") val userId: Int = 0,
    @SerializedName("userName") val userName: String = "",
    @SerializedName("visitDate") val visitDate: String = "",
    @SerializedName("latitude") val latitude: Double = 0.0,
    @SerializedName("longitude") val longitude: Double = 0.0,
    @SerializedName("remarks") val remarks: String? = null,
    @SerializedName("visitStatus") val visitStatus: String = "Completed",
    @SerializedName("nextFollowUpDate") val nextFollowUpDate: String? = null,
    @SerializedName("shopPhotoPath") val shopPhotoPath: String? = null,
    @SerializedName("isFollowUpCompleted") val isFollowUpCompleted: Boolean = false
)

data class FollowUpItem(
    @SerializedName("visitId") val visitId: Long = 0,
    @SerializedName("customerId") val customerId: Int = 0,
    @SerializedName("customerName") val customerName: String = "",
    @SerializedName("mobile") val mobile: String = "",
    @SerializedName("address") val address: String = "",
    @SerializedName("followUpDate") val followUpDate: String? = null,
    @SerializedName("category") val category: String = "Today",
    @SerializedName("isCompleted") val isCompleted: Boolean = false,
    @SerializedName("remarks") val remarks: String? = null
)

data class FieldUserDashboardStats(
    @SerializedName("attendanceStatus") val attendanceStatus: String = "Not Punched In",
    @SerializedName("punchInTime") val punchInTime: String? = null,
    @SerializedName("punchOutTime") val punchOutTime: String? = null,
    @SerializedName("todayWorkingTime") val todayWorkingTime: String = "0h 0m",
    @SerializedName("todayVisitsCount") val todayVisitsCount: Int = 0,
    @SerializedName("pendingFollowUpsCount") val pendingFollowUpsCount: Int = 0,
    @SerializedName("gpsTrackingActive") val gpsTrackingActive: Boolean = true
)
