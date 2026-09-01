package com.zynexbd.crmsolution.models

import com.google.gson.annotations.SerializedName

data class Shift(
    @SerializedName("shiftId") val shiftId: Int = 0,
    @SerializedName("shiftName") val shiftName: String,
    @SerializedName("startTime") val startTime: String,
    @SerializedName("endTime") val endTime: String,
    @SerializedName("gracePeriodMinutes") val gracePeriodMinutes: Int = 15,
    @SerializedName("isDefault") val isDefault: Boolean = false,
    @SerializedName("isActive") val isActive: Boolean = true
)

data class CreateShiftRequest(
    @SerializedName("shiftName") val shiftName: String,
    @SerializedName("startTime") val startTime: String,
    @SerializedName("endTime") val endTime: String,
    @SerializedName("gracePeriodMinutes") val gracePeriodMinutes: Int = 15,
    @SerializedName("isDefault") val isDefault: Boolean = false
)

data class UpdateShiftRequest(
    @SerializedName("shiftName") val shiftName: String,
    @SerializedName("startTime") val startTime: String,
    @SerializedName("endTime") val endTime: String,
    @SerializedName("gracePeriodMinutes") val gracePeriodMinutes: Int = 15,
    @SerializedName("isDefault") val isDefault: Boolean = false,
    @SerializedName("isActive") val isActive: Boolean = true
)
