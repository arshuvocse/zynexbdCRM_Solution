package com.zynexbd.crmsolution.models

import com.google.gson.annotations.SerializedName

data class MonthlyPerformanceReportResponse(
    @SerializedName("year") val year: Int,
    @SerializedName("month") val month: Int,
    @SerializedName("monthName") val monthName: String,
    @SerializedName("totalVisits") val totalVisits: Int,
    @SerializedName("totalFollowUps") val totalFollowUps: Int,
    @SerializedName("completedFollowUps") val completedFollowUps: Int,
    @SerializedName("pendingFollowUps") val pendingFollowUps: Int,
    @SerializedName("totalCustomersAdded") val totalCustomersAdded: Int,
    @SerializedName("employees") val employees: List<EmployeePerformanceItem>
)

data class EmployeePerformanceItem(
    @SerializedName("userId") val userId: Int = 0,
    @SerializedName("fullName") val fullName: String = "",
    @SerializedName("username") val username: String = "",
    @SerializedName("role") val role: String = "User",
    @SerializedName("isActive") val isActive: Boolean = true,
    @SerializedName("totalCustomersAdded") val totalCustomersAdded: Int = 0,
    @SerializedName("totalVisits") val totalVisits: Int = 0,
    @SerializedName("totalFollowUps") val totalFollowUps: Int = 0,
    @SerializedName("completedFollowUps") val completedFollowUps: Int = 0,
    @SerializedName("pendingFollowUps") val pendingFollowUps: Int = 0,
    @SerializedName("customers") val customers: List<ReportCustomerItem> = emptyList(),
    @SerializedName("visits") val visits: List<ReportVisitItem> = emptyList(),
    @SerializedName("followUps") val followUps: List<ReportFollowUpItem> = emptyList()
)

data class ReportCustomerItem(
    @SerializedName("customerId") val customerId: Int,
    @SerializedName("name") val name: String,
    @SerializedName("mobile") val mobile: String,
    @SerializedName("address") val address: String,
    @SerializedName("createdDate") val createdDate: String?,
    @SerializedName("remarks") val remarks: String?
)

data class ReportVisitItem(
    @SerializedName("visitId") val visitId: Long,
    @SerializedName("customerId") val customerId: Int,
    @SerializedName("customerName") val customerName: String,
    @SerializedName("customerMobile") val customerMobile: String,
    @SerializedName("customerAddress") val customerAddress: String,
    @SerializedName("visitDate") val visitDate: String,
    @SerializedName("remarks") val remarks: String?,
    @SerializedName("visitStatus") val visitStatus: String,
    @SerializedName("shopPhotoPath") val shopPhotoPath: String?
)

data class ReportFollowUpItem(
    @SerializedName("visitId") val visitId: Long,
    @SerializedName("customerId") val customerId: Int,
    @SerializedName("customerName") val customerName: String,
    @SerializedName("customerMobile") val customerMobile: String,
    @SerializedName("followUpDate") val followUpDate: String?,
    @SerializedName("isCompleted") val isCompleted: Boolean,
    @SerializedName("remarks") val remarks: String?
)
