package com.zynexbd.crmsolution.models

import com.google.gson.annotations.SerializedName

data class User(
    @SerializedName(value = "id", alternate = ["userId"]) val id: Int = 0,
    @SerializedName(value = "name", alternate = ["fullName"]) val name: String = "",
    val username: String = "",
    val role: String = "User",
    val isActive: Boolean = true,
    val createdAt: String? = null,
    val phoneNumber: String? = null,
    val officeLocationId: Int? = null,
    val officeLocationName: String? = null,
    val createdByAdminId: Int? = null,
    val maxUserLimit: Int? = null,
    val boundDeviceId: String? = null,
    val deviceModel: String? = null,
    val assignedOfficeLocationIds: List<Int>? = null,
    val assignedOfficeLocationNames: List<String>? = null,
    val companyId: Int? = null,
    val companyName: String? = null
)

data class AdminUserQuota(
    val maxUserLimit: Int = 10,
    val usedUserCount: Int = 0,
    val remainingUserCount: Int = 10,
    val isLimitReached: Boolean = false
)

data class CreateUserRequest(
    @SerializedName(value = "fullName", alternate = ["name"]) val name: String,
    val username: String,
    val password: String,
    val role: String,
    val phoneNumber: String? = null,
    val officeLocationId: Int? = null,
    val maxUserLimit: Int? = null,
    val assignedOfficeLocationIds: List<Int>? = null
)

data class UpdateUserRequest(
    @SerializedName(value = "fullName", alternate = ["name"]) val name: String,
    val role: String,
    val isActive: Boolean,
    val phoneNumber: String? = null,
    val officeLocationId: Int? = null,
    val maxUserLimit: Int? = null,
    val assignedOfficeLocationIds: List<Int>? = null
)

data class ResetPasswordRequest(
    @SerializedName(value = "newPassword", alternate = ["password"]) val newPassword: String
)
