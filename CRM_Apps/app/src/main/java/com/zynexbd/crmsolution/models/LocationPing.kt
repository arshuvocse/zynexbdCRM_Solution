package com.zynexbd.crmsolution.models

import com.google.gson.annotations.SerializedName

data class LocationPingRequest(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double?,
    val speed: Double?,
    val bearing: Double?,
    @SerializedName(value = "recordedAt", alternate = ["recordedAtUtc"]) val recordedAt: String, // ISO-8601
    val deviceBattery: Int? = null,
    val networkType: String? = null,
    @SerializedName(value = "locationAddress", alternate = ["address"]) val locationAddress: String? = null
)

data class LocationResponse(
    val userId: Int = 0,
    @SerializedName(value = "name", alternate = ["fullName"]) val name: String? = null,
    val username: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracy: Double? = null,
    val speed: Double? = null,
    val bearing: Double? = null,
    @SerializedName(value = "recordedAt", alternate = ["recordedAtUtc"]) val recordedAt: String? = null,
    val deviceBattery: Int? = null,
    val networkType: String? = null,
    @SerializedName(value = "isOnline", alternate = ["isActive"]) val isOnline: Boolean = false,
    @SerializedName(value = "locationAddress", alternate = ["address", "locationName"]) val locationAddress: String? = null
)
