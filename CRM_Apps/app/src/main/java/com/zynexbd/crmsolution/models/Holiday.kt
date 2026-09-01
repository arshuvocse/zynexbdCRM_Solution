package com.zynexbd.crmsolution.models

import com.google.gson.annotations.SerializedName

data class Holiday(
    @SerializedName("holidayId")
    val holidayId: Int,
    @SerializedName("name")
    val name: String,
    @SerializedName("date")
    val date: String,
    @SerializedName("year")
    val year: Int,
    @SerializedName("isRecurring")
    val isRecurring: Boolean = false,
    @SerializedName("isActive")
    val isActive: Boolean = true,
    @SerializedName("description")
    val description: String? = null
)

data class CreateOrUpdateHolidayRequest(
    val name: String,
    val date: String,
    val isRecurring: Boolean = false,
    val isActive: Boolean = true,
    val description: String? = null
)
